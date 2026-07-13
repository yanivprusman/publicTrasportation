import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

// In-memory route cache (persists between requests in Next.js server)
const routeCache = new Map<string, { data: unknown; time: number }>();
const ROUTE_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
const ROUTE_CACHE_MAX = 500;

function getCachedRoute(key: string) {
  const entry = routeCache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.time > ROUTE_CACHE_TTL) {
    routeCache.delete(key);
    return null;
  }
  return entry.data;
}

function setCachedRoute(key: string, data: unknown) {
  if (routeCache.size >= ROUTE_CACHE_MAX) {
    const oldest = routeCache.keys().next().value;
    if (oldest) routeCache.delete(oldest);
  }
  routeCache.set(key, { data, time: Date.now() });
}

interface MotisPlace {
  name?: string;
  lat?: number;
  lon?: number;
}

interface MotisLeg {
  mode?: string;
  from?: MotisPlace;
  to?: MotisPlace;
  startTime?: string;
  endTime?: string;
  duration?: number;
  routeShortName?: string;
  routeColor?: string;
  agencyName?: string;
  legGeometry?: { points?: string };
  polyline?: string;
  intermediateStops?: MotisPlace[];
}

interface MotisItinerary {
  duration?: number;
  startTime?: string;
  endTime?: string;
  transfers?: number;
  legs?: MotisLeg[];
}

function itineraryFingerprint(itin: MotisItinerary): string {
  return (itin.legs || [])
    .filter(leg => leg.mode && leg.mode !== 'WALK')
    .map(leg => `${leg.mode}:${leg.routeShortName || ''}:${leg.from?.name || ''}:${leg.to?.name || ''}`)
    .join('|');
}

function transformMotisResponse(motisData: { itineraries?: MotisItinerary[]; direct?: MotisItinerary[] }) {
  const seen = new Set<string>();
  const allItineraries = [...(motisData.itineraries || []), ...(motisData.direct || [])];
  const itineraries = allItineraries
    .filter(itin => {
      const fp = itineraryFingerprint(itin);
      if (!fp) return true; // walk-only routes are always unique
      if (seen.has(fp)) return false;
      seen.add(fp);
      return true;
    })
    .map(itin => ({
    duration: itin.duration || 0,
    startTime: itin.startTime || '',
    endTime: itin.endTime || '',
    transfers: itin.transfers || 0,
    legs: (itin.legs || []).map(leg => {
      const transformed: Record<string, unknown> = {
        mode: leg.mode || 'WALK',
        from: {
          name: leg.from?.name || '',
          lat: leg.from?.lat || 0,
          lon: leg.from?.lon || 0,
        },
        to: {
          name: leg.to?.name || '',
          lat: leg.to?.lat || 0,
          lon: leg.to?.lon || 0,
        },
        startTime: leg.startTime || '',
        endTime: leg.endTime || '',
        duration: leg.duration || 0,
        polyline: leg.legGeometry?.points || leg.polyline || '',
      };
      if (leg.routeShortName) transformed.routeShortName = leg.routeShortName;
      if (leg.routeColor) transformed.routeColor = leg.routeColor;
      if (leg.agencyName) transformed.agencyName = leg.agencyName;
      if (leg.intermediateStops && leg.intermediateStops.length > 0) {
        transformed.intermediateStops = leg.intermediateStops.map(stop => ({
          name: stop.name || '',
          lat: stop.lat || 0,
          lon: stop.lon || 0,
        }));
      }
      return transformed;
    }),
  }));
  return { itineraries };
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const from = searchParams.get('from');
  const to = searchParams.get('to');
  const time = searchParams.get('time');
  const arriveBy = searchParams.get('arriveBy');

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

  const routeTime = time || new Date().toISOString();
  const isArriveBy = arriveBy === 'true';
  // Bucket the key's time to the minute: clients send "leave now" searches with
  // millisecond precision, which makes every key unique and the cache useless
  // for the most common search. Minute-level reuse is within the staleness the
  // 5-minute TTL already accepts. MOTIS still gets the exact requested time.
  const routeTimeMs = Date.parse(routeTime);
  const timeBucket = isNaN(routeTimeMs)
    ? routeTime
    : new Date(routeTimeMs - (routeTimeMs % 60000)).toISOString();
  const cacheKey = `${from}|${to}|${timeBucket}|${isArriveBy}`;

  const cached = getCachedRoute(cacheKey);
  if (cached) {
    return NextResponse.json(cached);
  }

  await ensureMotis();

  try {
    const params = new URLSearchParams({
      fromPlace: `${fromLat},${fromLon}`,
      toPlace: `${toLat},${toLon}`,
      time: routeTime,
      arriveBy: String(isArriveBy),
      numItineraries: '5',
    });

    const response = await fetch(`${MOTIS_BASE}/api/v1/plan?${params}`, {
      signal: AbortSignal.timeout(15000),
    });

    if (!response.ok) {
      return NextResponse.json(
        { error: 'Failed to fetch route', message: `MOTIS returned ${response.status}` },
        { status: response.status }
      );
    }

    const motisData = await response.json();
    const result = transformMotisResponse(motisData);
    setCachedRoute(cacheKey, result);
    return NextResponse.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching route from MOTIS:', message);
    return NextResponse.json(
      { error: 'Failed to fetch route', message },
      { status: 502 }
    );
  }
}
