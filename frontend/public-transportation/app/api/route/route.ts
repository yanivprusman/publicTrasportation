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
  // Meters traveled; MOTIS sets it on non-transit (street) legs only.
  distance?: number;
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

interface MotisPlanResponse {
  itineraries?: MotisItinerary[];
  direct?: MotisItinerary[];
  previousPageCursor?: string;
  nextPageCursor?: string;
}

// The app's TransitMode union is WALK | BUS | RAIL | TRAM | SUBWAY, and
// utils/mode-colors styles only those five. MOTIS v2 reports finer-grained
// modes (e.g. REGIONAL_RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, NIGHT_RAIL, METRO,
// COACH). Passing those through unchanged made getModeStyle fall back to the
// WALK style, so a real train/metro leg rendered as a grey dashed "walking"
// polyline and showed the raw enum (e.g. "REGIONAL_RAIL") as its pill label.
// Fold each MOTIS mode into the app's contract so styling and labels are right.
const MODE_MAP: Record<string, 'WALK' | 'BIKE' | 'CAR' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY'> = {
  WALK: 'WALK',
  BIKE: 'BIKE',
  CAR: 'CAR',
  BUS: 'BUS',
  COACH: 'BUS',
  TRAM: 'TRAM',
  SUBWAY: 'SUBWAY',
  METRO: 'SUBWAY',
  RAIL: 'RAIL',
  REGIONAL_RAIL: 'RAIL',
  REGIONAL_FAST_RAIL: 'RAIL',
  HIGHSPEED_RAIL: 'RAIL',
  LONG_DISTANCE: 'RAIL',
  NIGHT_RAIL: 'RAIL',
};

// App-level mode filter keys (sent by the client as ?modes=bus,train) mapped
// to the MOTIS transitModes enums each one covers. The groups mirror MODE_MAP
// above so filtering and rendering agree on what counts as bus/train/tram.
const MODE_GROUPS: Record<string, string[]> = {
  bus: ['BUS', 'COACH'],
  train: ['RAIL', 'REGIONAL_RAIL', 'REGIONAL_FAST_RAIL', 'HIGHSPEED_RAIL', 'LONG_DISTANCE', 'NIGHT_RAIL'],
  tram: ['TRAM', 'SUBWAY', 'METRO'],
};

function normalizeMode(mode: string | undefined): 'WALK' | 'BIKE' | 'CAR' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY' {
  if (!mode) return 'WALK';
  // Any unrecognized transit mode is still a vehicle leg, not a walk — render
  // it as BUS (solid colored line) rather than the grey dashed walk style.
  return MODE_MAP[mode] ?? 'BUS';
}

function itineraryFingerprint(itin: MotisItinerary): string {
  return (itin.legs || [])
    .filter(leg => leg.mode && leg.mode !== 'WALK')
    .map(leg => `${leg.mode}:${leg.routeShortName || ''}:${leg.from?.name || ''}:${leg.to?.name || ''}`)
    .join('|');
}

function transformItinerary(itin: MotisItinerary) {
  return {
    duration: itin.duration || 0,
    startTime: itin.startTime || '',
    endTime: itin.endTime || '',
    transfers: itin.transfers || 0,
    legs: (itin.legs || []).map(leg => {
      const transformed: Record<string, unknown> = {
        mode: normalizeMode(leg.mode),
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
  };
}

function transformMotisResponse(motisData: MotisPlanResponse, includeDirect: boolean) {
  const seen = new Set<string>();
  // Direct (walk-only) itineraries are not part of the time-paged sequence —
  // MOTIS repeats them on every page, so merge them only into the first page.
  const allItineraries = [
    ...(motisData.itineraries || []),
    ...(includeDirect ? motisData.direct || [] : []),
  ];
  const itineraries = allItineraries
    .filter(itin => {
      const fp = itineraryFingerprint(itin);
      if (!fp) return true; // walk-only routes are always unique
      if (seen.has(fp)) return false;
      seen.add(fp);
      return true;
    })
    .map(transformItinerary);
  return {
    itineraries,
    previousPageCursor: motisData.previousPageCursor,
    nextPageCursor: motisData.nextPageCursor,
  };
}

// Direct street itineraries (one per requested mode) come back unordered and
// occasionally with more than one option per mode; keep only the fastest
// bike and car route, tagged with the mode and total street distance.
function extractAlternatives(direct: MotisItinerary[]) {
  const best = new Map<'BIKE' | 'CAR', MotisItinerary>();
  for (const itin of direct) {
    const legModes = (itin.legs || []).map(leg => leg.mode);
    const mode = legModes.includes('CAR') ? 'CAR' : legModes.includes('BIKE') ? 'BIKE' : null;
    if (!mode) continue;
    const current = best.get(mode);
    if (!current || (itin.duration || 0) < (current.duration || 0)) {
      best.set(mode, itin);
    }
  }
  return [...best.entries()]
    .map(([mode, itin]) => ({
      mode,
      distance: Math.round(
        (itin.legs || []).reduce((sum, leg) => sum + (leg.distance || 0), 0)
      ),
      itinerary: transformItinerary(itin),
    }))
    .sort((a, b) => a.itinerary.duration - b.itinerary.duration);
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const from = searchParams.get('from');
  const to = searchParams.get('to');
  const time = searchParams.get('time');
  const arriveBy = searchParams.get('arriveBy');
  const pageCursor = searchParams.get('pageCursor');
  const modesParam = searchParams.get('modes');
  const maxWalkParam = searchParams.get('maxWalk');

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
  const cacheKey = `${from}|${to}|${timeBucket}|${isArriveBy}|${pageCursor || ''}|${transitModes?.join(',') || ''}|${maxWalkSeconds || ''}`;

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
    if (pageCursor) params.set('pageCursor', pageCursor);
    if (transitModes) params.set('transitModes', transitModes.join(','));
    if (maxWalkSeconds !== null) {
      params.set('maxPreTransitTime', String(maxWalkSeconds));
      params.set('maxPostTransitTime', String(maxWalkSeconds));
    }

    // Bike/car comparison routes are fetched with a second, direct-only plan
    // call rather than by adding directModes to the transit call: MOTIS uses
    // the fastest direct connection as a cut-off during transit routing, so a
    // fast car route in the same query would silently drop slower (i.e. most)
    // transit itineraries. A minimal searchWindow keeps the second call's
    // transit search — whose results are discarded — as cheap as possible.
    // Direct routes are time-independent, so paging calls skip this.
    const fetchAlternatives = async () => {
      if (pageCursor) return undefined;
      const directParams = new URLSearchParams({
        fromPlace: `${fromLat},${fromLon}`,
        toPlace: `${toLat},${toLon}`,
        time: routeTime,
        arriveBy: String(isArriveBy),
        numItineraries: '1',
        searchWindow: '60',
        directModes: 'BIKE,CAR',
        maxDirectTime: '7200',
      });
      const directResponse = await fetch(`${MOTIS_BASE}/api/v1/plan?${directParams}`, {
        signal: AbortSignal.timeout(15000),
      });
      if (!directResponse.ok) {
        throw new Error(`MOTIS direct-route request returned ${directResponse.status}`);
      }
      const directData: MotisPlanResponse = await directResponse.json();
      return extractAlternatives(directData.direct || []);
    };

    const [response, alternatives] = await Promise.all([
      fetch(`${MOTIS_BASE}/api/v1/plan?${params}`, {
        signal: AbortSignal.timeout(15000),
      }),
      fetchAlternatives(),
    ]);

    if (!response.ok) {
      return NextResponse.json(
        { error: 'Failed to fetch route', message: `MOTIS returned ${response.status}` },
        { status: response.status }
      );
    }

    const motisData = await response.json();
    const result = {
      ...transformMotisResponse(motisData, !pageCursor),
      ...(alternatives !== undefined ? { alternatives } : {}),
    };
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
