import { NextRequest, NextResponse } from "next/server";
import { sendToDaemon } from "../../../../lib/daemon";
import { rateLimit, clientIp } from "../../../../lib/rate-limit";

// Token-gated, but an unlimited guess rate turns any secret into a matter of
// time. This bounds it to something useless for brute force while staying far
// above what a dashboard refresh needs.
const STATS_LIMIT = 60;
const STATS_WINDOW_SECONDS = 3600;

// Install/retention/referral numbers for the native app.
//
// Token-gated: this is the private side of the analytics pair. The token lives
// in .env.local (gitignored) as PT_STATS_TOKEN. With no token configured the
// route refuses to serve rather than defaulting to open — an unset secret is a
// misconfiguration, not permission.

export const dynamic = "force-dynamic";

const VALID_PERIODS = new Set(["today", "24h", "7d", "30d", "90d", "all"]);

function tokenOf(req: NextRequest): string | null {
  const auth = req.headers.get("authorization");
  if (auth?.startsWith("Bearer ")) return auth.slice(7).trim();
  return req.nextUrl.searchParams.get("token");
}

export async function GET(req: NextRequest) {
  const limit = rateLimit(`stats:${clientIp(req)}`, STATS_LIMIT, STATS_WINDOW_SECONDS);
  if (!limit.allowed) {
    return NextResponse.json(
      { error: "Too many requests" },
      { status: 429, headers: { "Retry-After": String(limit.retryAfterSeconds) } }
    );
  }

  const expected = process.env.PT_STATS_TOKEN;
  if (!expected) {
    console.error("[app/stats] PT_STATS_TOKEN is not set — refusing to serve");
    return NextResponse.json(
      { error: "Stats endpoint is not configured" },
      { status: 503 }
    );
  }
  if (tokenOf(req) !== expected) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const period = req.nextUrl.searchParams.get("period") || "30d";
  if (!VALID_PERIODS.has(period)) {
    return NextResponse.json(
      { error: `period must be one of: ${[...VALID_PERIODS].join(", ")}` },
      { status: 400 }
    );
  }

  try {
    const raw = await sendToDaemon({ command: "appStats", app: "pt", period });
    return NextResponse.json(JSON.parse(raw));
  } catch (err) {
    console.error("[app/stats] daemon error:", err);
    return NextResponse.json(
      { error: "Stats source unavailable", detail: String(err) },
      { status: 503 }
    );
  }
}
