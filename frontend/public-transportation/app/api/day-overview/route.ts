import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';
import { MODE_GROUPS, normalizeMode, PEDESTRIAN_SPEED, type NormalizedMode } from '@/lib/motis-modes';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

// Sweeping a whole day means many sequential MOTIS plan calls; bound both the
// call count and the wall time so one busy corridor can't hold a request open.
const MAX_PLAN_CALLS = 30;
const MAX_SWEEP_MS = 25 * 1000;

// A day sweep is expensive relative to a single plan call, so it earns a
// longer cache than /api/route: the set of scheduled departures for a day
// only changes when GTFS data does.
const dayCache = new Map<string, { data: unknown; time: number }>();
const DAY_CACHE_TTL = 30 * 60 * 1000; // 30 minutes
const DAY_CACHE_MAX = 50;

function getCachedDay(key: string) {
  const entry = dayCache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.time > DAY_CACHE_TTL) {
    dayCache.delete(key);
    return null;
  }
  return entry.data;
}

function setCachedDay(key: string, data: unknown) {
  if (dayCache.size >= DAY_CACHE_MAX) {
    const oldest = dayCache.keys().next().value;
    if (oldest) dayCache.delete(oldest);
  }
  dayCache.set(key, { data, time: Date.now() });
}

interface MotisLeg {
  mode?: string;
  from?: { name?: string };
  to?: { name?: string };
  routeShortName?: string;
}

interface MotisItinerary {
  duration?: number;
  startTime?: string;
  endTime?: string;
  transfers?: number;
  legs?: MotisLeg[];
}

interface MotisPlanResponse {
  itineraries?: MotisItinerary[];
  nextPageCursor?: string;
}

export interface DayDeparturePayload {
  startTime: string;
  endTime: string;
  duration: number;
  transfers: number;
  lines: { mode: NormalizedMode; name: string }[];
}

// Same identity a rider would use — departure time plus the sequence of rides.
// MOTIS pages overlap at the edges, so the sweep sees some itineraries twice.
function departureKey(itin: MotisItinerary): string {
  const legs = (itin.legs || [])
    .filter(leg => leg.mode && leg.mode !== 'WALK')
    .map(leg => `${leg.mode}:${leg.routeShortName || ''}:${leg.from?.name || ''}:${leg.to?.name || ''}`)
    .join('|');
  return `${itin.startTime}|${legs}`;
}

function toPayload(itin: MotisItinerary): DayDeparturePayload {
  return {
    startTime: itin.startTime || '',
    endTime: itin.endTime || '',
    duration: itin.duration || 0,
    transfers: itin.transfers || 0,
    lines: (itin.legs || [])
      .filter(leg => leg.mode && leg.mode !== 'WALK')
      .map(leg => ({ mode: normalizeMode(leg.mode), name: leg.routeShortName || '' })),
  };
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const from = searchParams.get('from');
  const to = searchParams.get('to');
  const start = searchParams.get('start');
  const end = searchParams.get('end');
  const modesParam = searchParams.get('modes');
  const maxWalkParam = searchParams.get('maxWalk');

  if (!from || !to) {
    return NextResponse.json(
      { error: 'Missing required parameters: from, to (format: lat,lon)' },
      { status: 400 }
    );
  }

  const [fromLat, fromLon] = from.split(',').map(Number);
  const [toLat, toLon] = to.split(',').map(Number);
  if (isNaN(fromLat) || isNaN(fromLon) || isNaN(toLat) || isNaN(toLon)) {
    return NextResponse.json(
      { error: 'Invalid coordinate format. Use: lat,lon' },
      { status: 400 }
    );
  }

  const startMs = start ? Date.parse(start) : NaN;
  const endMs = end ? Date.parse(end) : NaN;
  if (isNaN(startMs) || isNaN(endMs) || endMs <= startMs) {
    return NextResponse.json(
      { error: 'Invalid time window. Expected ISO timestamps: start < end.' },
      { status: 400 }
    );
  }
  if (endMs - startMs > 36 * 60 * 60 * 1000) {
    return NextResponse.json(
      { error: 'Time window too large. Maximum sweep is 36 hours.' },
      { status: 400 }
    );
  }

  let transitModes: string[] | null = null;
  if (modesParam !== null) {
    const keys = modesParam.split(',').map(k => k.trim()).filter(Boolean);
    const unknown = keys.filter(k => !MODE_GROUPS[k]);
    if (keys.length === 0 || unknown.length > 0) {
      return NextResponse.json(
        { error: `Invalid modes parameter. Allowed values: ${Object.keys(MODE_GROUPS).join(', ')}` },
        { status: 400 }
      );
    }
    transitModes = [...new Set(keys.flatMap(k => MODE_GROUPS[k]))];
  }

  let maxWalkSeconds: number | null = null;
  if (maxWalkParam !== null) {
    const minutes = Number(maxWalkParam);
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 60) {
      return NextResponse.json(
        { error: 'Invalid maxWalk parameter. Expected whole minutes between 1 and 60.' },
        { status: 400 }
      );
    }
    maxWalkSeconds = minutes * 60;
  }

  const cacheKey = `${from}|${to}|${new Date(startMs).toISOString()}|${new Date(endMs).toISOString()}|${transitModes?.join(',') || ''}|${maxWalkSeconds || ''}`;
  const cached = getCachedDay(cacheKey);
  if (cached) {
    return NextResponse.json(cached);
  }

  await ensureMotis();

  try {
    const seen = new Set<string>();
    const departures: DayDeparturePayload[] = [];
    const sweepStarted = Date.now();
    let cursor: string | undefined;
    let calls = 0;
    let truncated = false;

    // Walk the day page by page: each MOTIS call returns the next batch of
    // itineraries and a cursor for the window after them.
    while (calls < MAX_PLAN_CALLS) {
      if (Date.now() - sweepStarted > MAX_SWEEP_MS) {
        truncated = true;
        break;
      }
      const params = new URLSearchParams({
        fromPlace: `${fromLat},${fromLon}`,
        toPlace: `${toLat},${toLon}`,
        time: new Date(startMs).toISOString(),
        arriveBy: 'false',
        numItineraries: '10',
        pedestrianSpeed: PEDESTRIAN_SPEED,
      });
      if (cursor) params.set('pageCursor', cursor);
      if (transitModes) params.set('transitModes', transitModes.join(','));
      if (maxWalkSeconds !== null) {
        params.set('maxPreTransitTime', String(maxWalkSeconds));
        params.set('maxPostTransitTime', String(maxWalkSeconds));
      }

      const response = await fetch(`${MOTIS_BASE}/api/v1/plan?${params}`, {
        signal: AbortSignal.timeout(15000),
      });
      if (!response.ok) {
        return NextResponse.json(
          { error: 'Failed to sweep the day', message: `MOTIS returned ${response.status}` },
          { status: response.status }
        );
      }
      const data: MotisPlanResponse = await response.json();
      calls++;

      let passedEnd = false;
      for (const itin of data.itineraries || []) {
        const departMs = Date.parse(itin.startTime || '');
        if (isNaN(departMs)) continue;
        if (departMs >= endMs) {
          passedEnd = true;
          continue;
        }
        if (departMs < startMs) continue;
        // Walk-only itineraries repeat at every requested time — they are not
        // scheduled departures, so they don't belong on a departures chart.
        if (!(itin.legs || []).some(leg => leg.mode && leg.mode !== 'WALK')) continue;
        const key = departureKey(itin);
        if (seen.has(key)) continue;
        seen.add(key);
        departures.push(toPayload(itin));
      }

      if (passedEnd || !data.nextPageCursor) break;
      cursor = data.nextPageCursor;
      if (calls === MAX_PLAN_CALLS) truncated = true;
    }

    departures.sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
    const result = { departures, truncated };
    setCachedDay(cacheKey, result);
    return NextResponse.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error sweeping day overview from MOTIS:', message);
    return NextResponse.json(
      { error: 'Failed to sweep the day', message },
      { status: 502 }
    );
  }
}
