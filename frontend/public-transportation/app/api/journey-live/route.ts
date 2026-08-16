import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'crypto';

/**
 * Live journey sharing — the phone posts where the rider is, anyone with the
 * link watches.
 *
 * The store is in-memory on purpose: a share is ephemeral by nature (it exists
 * while somebody is travelling), the phone re-posts every few seconds so a
 * restarted server repopulates within one update, and nothing here is worth a
 * database row that would outlive the trip it describes.
 */

type LatLon = { lat: number; lon: number };

type ShareLeg = {
  mode: string;
  polyline: string;
  routeColor: string | null;
};

type JourneyShare = {
  token: string;
  createdAt: number;
  updatedAt: number;
  /** Set when the journey ended; the page says so instead of going dark. */
  endedAt: number | null;
  position: LatLon | null;
  /** Localized by the sharing app — rendered verbatim, dir="auto". */
  headline: string;
  detail: string | null;
  etaIso: string;
  destinationName: string;
  legs: ShareLeg[];
  progressLegIndex: number;
};

const shares = new Map<string, JourneyShare>();
const TTL_MS = 3 * 60 * 60 * 1000;
const ENDED_LINGER_MS = 30 * 60 * 1000;
const MAX_SHARES = 500;

function prune() {
  const now = Date.now();
  for (const [token, share] of shares) {
    const keepUntil = share.endedAt !== null
      ? share.endedAt + ENDED_LINGER_MS
      : share.updatedAt + TTL_MS;
    if (now > keepUntil) shares.delete(token);
  }
}

export async function GET(request: NextRequest) {
  prune();
  const token = request.nextUrl.searchParams.get('token');
  const share = token ? shares.get(token) : undefined;
  if (!share) {
    return NextResponse.json({ error: 'unknown or expired share' }, { status: 404 });
  }
  return NextResponse.json(share);
}

export async function POST(request: NextRequest) {
  prune();
  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: 'invalid JSON' }, { status: 400 });
  }

  const token = typeof body.token === 'string' ? body.token : null;

  if (token) {
    const share = shares.get(token);
    // An expired or unknown token is refused rather than resurrected under the
    // caller's chosen name — tokens are only ever minted here.
    if (!share) return NextResponse.json({ error: 'unknown or expired share' }, { status: 404 });
    applyUpdate(share, body);
    return NextResponse.json({ token: share.token });
  }

  if (shares.size >= MAX_SHARES) {
    return NextResponse.json({ error: 'too many active shares' }, { status: 503 });
  }
  const share: JourneyShare = {
    token: randomBytes(8).toString('base64url'),
    createdAt: Date.now(),
    updatedAt: Date.now(),
    endedAt: null,
    position: null,
    headline: '',
    detail: null,
    etaIso: '',
    destinationName: '',
    legs: [],
    progressLegIndex: 0,
  };
  applyUpdate(share, body);
  shares.set(share.token, share);
  return NextResponse.json({ token: share.token });
}

function applyUpdate(share: JourneyShare, body: Record<string, unknown>) {
  share.updatedAt = Date.now();
  if (typeof body.headline === 'string') share.headline = body.headline;
  if (typeof body.detail === 'string' || body.detail === null) share.detail = body.detail as string | null;
  if (typeof body.etaIso === 'string') share.etaIso = body.etaIso;
  if (typeof body.destinationName === 'string') share.destinationName = body.destinationName;
  if (typeof body.progressLegIndex === 'number') share.progressLegIndex = body.progressLegIndex;
  if (body.position && typeof body.position === 'object') {
    const p = body.position as Record<string, unknown>;
    if (typeof p.lat === 'number' && typeof p.lon === 'number') {
      share.position = { lat: p.lat, lon: p.lon };
    }
  }
  if (Array.isArray(body.legs) && body.legs.length > 0) {
    share.legs = (body.legs as Record<string, unknown>[])
      .filter((l) => typeof l.mode === 'string' && typeof l.polyline === 'string')
      .map((l) => ({
        mode: l.mode as string,
        polyline: l.polyline as string,
        routeColor: typeof l.routeColor === 'string' ? l.routeColor : null,
      }));
  }
  if (body.ended === true) share.endedAt = Date.now();
}
