import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';
import { MODE_GROUPS, normalizeMode, PEDESTRIAN_SPEED } from '@/lib/motis-modes';
import { tripWheelchairAccess } from '@/lib/gtfs-trips';
import { stopIdentity } from '@/lib/gtfs-stops';
import { fareBetween } from '@/lib/gtfs-fares';

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
  // GTFS stop id, feed-prefixed ("israel_26635"). Present on transit endpoints only —
  // a walk leg's START/END are coordinates, not stops.
  stopId?: string;
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
  // Carries the GTFS trip id, which is the only route to an accessibility flag.
  tripId?: string;
  // GTFS route_id. SIRI's LineRef is the same number, so this is what makes a live
  // sighting match THIS direction of the line rather than the opposite one.
  routeId?: string;
  // Where this service is signed for — the only thing that tells a passenger which
  // of a line's two directions they are looking at.
  headsign?: string;
  // True when the times came from a realtime feed rather than the timetable. MOTIS
  // sets it per leg; with no GTFS-RT source wired it is false for everything, which
  // is itself the honest answer and is what the card now says.
  realTime?: boolean;
  scheduledStartTime?: string;
  scheduledEndTime?: string;
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

function itineraryFingerprint(itin: MotisItinerary): string {
  return (itin.legs || [])
    .filter(leg => leg.mode && leg.mode !== 'WALK')
    .map(leg => `${leg.mode}:${leg.routeShortName || ''}:${leg.from?.name || ''}:${leg.to?.name || ''}`)
    .join('|');
}

/**
 * MOTIS names the two ends of the query "START" and "END". Those are the pins the
 * rider dropped, not places with names, and passing the words through puts them on
 * screen verbatim — "Walk to END" in the live navigator, "END" as the last node of
 * the itinerary timeline. They are cleared here, at the one door every client comes
 * through, so no client has to know MOTIS's vocabulary; each words a nameless end
 * in its own language ("Walk to your destination").
 *
 * A named place always carries a stopId, so a real stop called END keeps its name.
 */
function placeName(p?: MotisPlace): string {
  const name = (p?.name || '').trim();
  if (!p?.stopId && (name === 'START' || name === 'END')) return '';
  return name;
}

function transformItinerary(itin: MotisItinerary) {
  const legs = (itin.legs || []).map(leg => {
      const transformed: Record<string, unknown> = {
        mode: normalizeMode(leg.mode),
        from: {
          name: placeName(leg.from),
          lat: leg.from?.lat || 0,
          lon: leg.from?.lon || 0,
        },
        to: {
          name: placeName(leg.to),
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
      if (leg.headsign) transformed.headsign = leg.headsign;
      // Street legs carry their length; Moovit and Maps both print it, and "walk
      // 370 m" answers a question "walk 4 min" does not.
      if (typeof leg.distance === 'number') transformed.distanceMeters = Math.round(leg.distance);
      // Say where the times came from rather than implying certainty we lack.
      transformed.realTime = leg.realTime === true;
      if (leg.scheduledStartTime) transformed.scheduledStartTime = leg.scheduledStartTime;
      // Only transit legs can be inaccessible; a walk leg has nothing to board.
      if (leg.mode && leg.mode !== 'WALK') {
        transformed.wheelchairAccess = tripWheelchairAccess(leg.tripId);
        // Passed through so the client can ask /api/trip-shape for this exact
        // trip's geometry. Resolving by line number instead draws another city's
        // line of the same number.
        if (leg.tripId) transformed.tripId = leg.tripId;
        if (leg.routeId) transformed.routeId = leg.routeId;

        // The stop code is what is printed on the pole and what every other app
        // shows, so it is the only stop identifier a passenger can act on.
        const boarding = stopIdentity(leg.from?.stopId);
        const alighting = stopIdentity(leg.to?.stopId);
        if (boarding?.stopCode) transformed.fromStopCode = boarding.stopCode;
        if (alighting?.stopCode) transformed.toStopCode = alighting.stopCode;

        // Null when the fare table has no rule for this pair; the itinerary then
        // reports no total rather than a partial one.
        const fare = fareBetween(boarding?.zoneId, alighting?.zoneId);
        if (fare !== null) transformed.fare = fare;
      }
      if (leg.intermediateStops && leg.intermediateStops.length > 0) {
        transformed.intermediateStops = leg.intermediateStops.map(stop => ({
          name: stop.name || '',
          lat: stop.lat || 0,
          lon: stop.lon || 0,
        }));
      }
      return transformed;
  });

  // One unpriced ride makes the whole journey unpriced. Summing the legs we can
  // price and calling it the total would understate the fare, which is the same
  // failure the flat-rate estimate made and the reason it was replaced.
  const rides = legs.filter(l => l.mode !== 'WALK');
  const priced = rides.filter(l => typeof l.fare === 'number');
  const fareTotal = rides.length > 0 && priced.length === rides.length
    ? Number(priced.reduce((sum, l) => sum + (l.fare as number), 0).toFixed(2))
    : null;

  return {
    duration: itin.duration || 0,
    startTime: itin.startTime || '',
    endTime: itin.endTime || '',
    transfers: itin.transfers || 0,
    ...(fareTotal !== null ? { fareTotal } : {}),
    legs,
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

// --- Trips with an intermediate stop ---------------------------------------
// MOTIS plans point-to-point only; a trip through a via place is built by
// planning the two halves and pairing them: each first-half arrival is matched
// with the earliest second-half connection it can catch (mirrored for
// arrive-by searches).

function isWalkOnly(itin: MotisItinerary): boolean {
  return (itin.legs || []).every(leg => !leg.mode || leg.mode === 'WALK');
}

// Walk-only itineraries are schedule-independent — MOTIS just anchors them to
// the query time — so they can be re-anchored to line up with the other half.
function shiftItinerary(itin: MotisItinerary, deltaMs: number): MotisItinerary {
  const shift = (iso?: string) => {
    if (!iso) return iso;
    const ms = Date.parse(iso);
    return isNaN(ms) ? iso : new Date(ms + deltaMs).toISOString();
  };
  return {
    ...itin,
    startTime: shift(itin.startTime),
    endTime: shift(itin.endTime),
    legs: (itin.legs || []).map(leg => ({
      ...leg,
      startTime: shift(leg.startTime),
      endTime: shift(leg.endTime),
    })),
  };
}

function combineItineraries(first: MotisItinerary, second: MotisItinerary): MotisItinerary {
  const legs = [...(first.legs || []), ...(second.legs || [])];
  const transitLegCount = legs.filter(leg => leg.mode && leg.mode !== 'WALK').length;
  return {
    startTime: first.startTime,
    endTime: second.endTime,
    duration: Math.round(
      (Date.parse(second.endTime || '') - Date.parse(first.startTime || '')) / 1000
    ),
    transfers: Math.max(0, transitLegCount - 1),
    legs,
  };
}

// One half of a via trip. Direct walk itineraries are merged into the
// candidates: walking a short half is a legitimate connection.
async function planHalf(
  fromPlace: string,
  toPlace: string,
  time: string,
  arriveBy: boolean,
  transitModes: string[] | null,
  maxWalkSeconds: number | null
): Promise<MotisItinerary[]> {
  const params = new URLSearchParams({
    fromPlace,
    toPlace,
    time,
    arriveBy: String(arriveBy),
    numItineraries: '5',
    pedestrianSpeed: PEDESTRIAN_SPEED,
  });
  if (transitModes) params.set('transitModes', transitModes.join(','));
  if (maxWalkSeconds !== null) {
    params.set('maxPreTransitTime', String(maxWalkSeconds));
    params.set('maxPostTransitTime', String(maxWalkSeconds));
  }
  const response = await fetch(`${MOTIS_BASE}/api/v1/plan?${params}`, {
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) {
    throw new Error(`MOTIS returned ${response.status}`);
  }
  const data: MotisPlanResponse = await response.json();
  return [...(data.itineraries || []), ...(data.direct || [])];
}

async function planViaTrip(
  fromPlace: string,
  viaPlace: string,
  toPlace: string,
  time: string,
  arriveBy: boolean,
  transitModes: string[] | null,
  maxWalkSeconds: number | null
): Promise<MotisItinerary[]> {
  const combined: MotisItinerary[] = [];
  if (!arriveBy) {
    const firstHalves = await planHalf(fromPlace, viaPlace, time, false, transitModes, maxWalkSeconds);
    const arrivals = firstHalves.map(itin => Date.parse(itin.endTime || '')).filter(ms => !isNaN(ms));
    if (arrivals.length === 0) return [];
    // Query the second half once, from the earliest possible arrival at the
    // via point; later first halves pick a later departure out of the same set.
    const earliestArrival = new Date(Math.min(...arrivals)).toISOString();
    const secondHalves = await planHalf(viaPlace, toPlace, earliestArrival, false, transitModes, maxWalkSeconds);
    for (const first of firstHalves) {
      const arriveVia = Date.parse(first.endTime || '');
      if (isNaN(arriveVia)) continue;
      let best: MotisItinerary | null = null;
      let bestEnd = Infinity;
      for (const second of secondHalves) {
        const candidate = isWalkOnly(second)
          ? shiftItinerary(second, arriveVia - Date.parse(second.startTime || ''))
          : second;
        const depart = Date.parse(candidate.startTime || '');
        const end = Date.parse(candidate.endTime || '');
        if (isNaN(depart) || isNaN(end) || depart < arriveVia) continue;
        if (end < bestEnd) {
          best = candidate;
          bestEnd = end;
        }
      }
      if (best) combined.push(combineItineraries(first, best));
    }
    return combined;
  }
  // Arrive-by: plan the second half backwards from the target time, then the
  // first half backwards from the latest usable via departure.
  const secondHalves = await planHalf(viaPlace, toPlace, time, true, transitModes, maxWalkSeconds);
  const departures = secondHalves.map(itin => Date.parse(itin.startTime || '')).filter(ms => !isNaN(ms));
  if (departures.length === 0) return [];
  const latestDeparture = new Date(Math.max(...departures)).toISOString();
  const firstHalves = await planHalf(fromPlace, viaPlace, latestDeparture, true, transitModes, maxWalkSeconds);
  for (const second of secondHalves) {
    const departVia = Date.parse(second.startTime || '');
    if (isNaN(departVia)) continue;
    let best: MotisItinerary | null = null;
    let bestStart = -Infinity;
    for (const first of firstHalves) {
      const candidate = isWalkOnly(first)
        ? shiftItinerary(first, departVia - Date.parse(first.endTime || ''))
        : first;
      const start = Date.parse(candidate.startTime || '');
      const end = Date.parse(candidate.endTime || '');
      if (isNaN(start) || isNaN(end) || end > departVia) continue;
      if (start > bestStart) {
        best = candidate;
        bestStart = start;
      }
    }
    if (best) combined.push(combineItineraries(best, second));
  }
  return combined;
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
  const via = searchParams.get('via');
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

  // Trips through an intermediate stop are stitched from two MOTIS queries and
  // have no paging cursors or street alternatives — handled as their own branch.
  if (via) {
    if (pageCursor) {
      return NextResponse.json(
        { error: 'Paging is not supported for trips with an intermediate stop' },
        { status: 400 }
      );
    }
    const [viaLat, viaLon] = via.split(',').map(Number);
    if (isNaN(viaLat) || isNaN(viaLon)) {
      return NextResponse.json(
        { error: 'Invalid coordinate format. Use: lat,lon' },
        { status: 400 }
      );
    }
    const viaCacheKey = `via|${from}|${via}|${to}|${timeBucket}|${isArriveBy}|${transitModes?.join(',') || ''}|${maxWalkSeconds || ''}`;
    const viaCached = getCachedRoute(viaCacheKey);
    if (viaCached) {
      return NextResponse.json(viaCached);
    }
    await ensureMotis();
    try {
      const combined = await planViaTrip(
        `${fromLat},${fromLon}`,
        `${viaLat},${viaLon}`,
        `${toLat},${toLon}`,
        routeTime,
        isArriveBy,
        transitModes,
        maxWalkSeconds
      );
      const seen = new Set<string>();
      const itineraries = combined
        .sort((a, b) =>
          (Date.parse(a.endTime || '') - Date.parse(b.endTime || '')) ||
          ((a.duration || 0) - (b.duration || 0))
        )
        .filter(itin => {
          const key = `${itineraryFingerprint(itin)}|${itin.startTime}|${itin.endTime}`;
          if (seen.has(key)) return false;
          seen.add(key);
          return true;
        })
        .slice(0, 6)
        .map(transformItinerary);
      const result = { itineraries };
      setCachedRoute(viaCacheKey, result);
      return NextResponse.json(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error('Error fetching via route from MOTIS:', message);
      return NextResponse.json(
        { error: 'Failed to fetch route', message },
        { status: 502 }
      );
    }
  }

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
      pedestrianSpeed: PEDESTRIAN_SPEED,
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
