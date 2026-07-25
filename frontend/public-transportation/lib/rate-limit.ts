// Fixed-window rate limiting for the public, unauthenticated endpoints.
//
// These routes are reachable by anyone on the internet and write to the shared
// database, so without a limit a single client can fill app_users with junk
// registrations or flood app_events. This is the cheapest thing that closes
// that, and it runs in-process so it works identically in dev and behind nginx.
//
// Deliberate limitations, so nobody mistakes this for more than it is:
//  - Per process. If Next.js runs multiple workers the effective limit is
//    multiplied by the worker count. Still bounds abuse by orders of magnitude.
//  - Per IP. Users behind one NAT share a budget, which is why the limits are
//    set well above what a real person generates rather than at the true minimum.
//  - Resets on restart.

type Bucket = { count: number; resetAt: number };

const buckets = new Map<string, Bucket>();

// The bucket map is itself an attack surface: a spoofed or distributed source
// could otherwise grow it without bound. Cap it, and evict the entries closest
// to expiry when full.
const MAX_TRACKED_KEYS = 20_000;

function evictOldest() {
  const excess = buckets.size - MAX_TRACKED_KEYS + 1;
  if (excess <= 0) return;
  const byExpiry = [...buckets.entries()].sort((a, b) => a[1].resetAt - b[1].resetAt);
  for (let i = 0; i < excess && i < byExpiry.length; i++) {
    buckets.delete(byExpiry[i][0]);
  }
}

export type RateLimitResult = {
  allowed: boolean;
  remaining: number;
  retryAfterSeconds: number;
};

export function rateLimit(
  key: string,
  limit: number,
  windowSeconds: number
): RateLimitResult {
  const now = Date.now();
  const existing = buckets.get(key);

  if (!existing || existing.resetAt <= now) {
    if (buckets.size >= MAX_TRACKED_KEYS) evictOldest();
    buckets.set(key, { count: 1, resetAt: now + windowSeconds * 1000 });
    return { allowed: true, remaining: limit - 1, retryAfterSeconds: 0 };
  }

  existing.count += 1;
  if (existing.count > limit) {
    return {
      allowed: false,
      remaining: 0,
      retryAfterSeconds: Math.max(1, Math.ceil((existing.resetAt - now) / 1000)),
    };
  }
  return {
    allowed: true,
    remaining: limit - existing.count,
    retryAfterSeconds: 0,
  };
}

/**
 * Client IP as seen through nginx.
 *
 * X-Forwarded-For is client-controlled in general, but nginx overwrites it for
 * requests it proxies, so the FIRST entry is the real peer here. Falls back to
 * a constant when absent (direct local access in dev) — which means those
 * requests share one bucket rather than escaping the limit entirely.
 */
export function clientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) {
    const first = xff.split(",")[0]?.trim();
    if (first) return first;
  }
  return req.headers.get("x-real-ip")?.trim() || "unknown";
}
