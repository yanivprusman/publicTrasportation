import polyline from '@mapbox/polyline'

/**
 * Routing for a rider who is already on a bus.
 *
 * A plan from "where I am" treats the rider as a pedestrian: on board line 60 it told
 * them to get off at the next junction and wait for the 142 — while the 60 itself went
 * on to a stop four minutes' walk from where they were going (2026-09-22, Ramat Negev).
 * The rider names the line; everything else is inferred here:
 *
 *  - WHICH trip of that line: the one whose timetable puts it on this stretch of road
 *    now, moving the way the rider is moving. MOTIS's /api/v1/map/trips returns every
 *    trip active in a box with the real road geometry between its stops, so the fit is
 *    against the road, not the straight line between two stops.
 *  - HOW LATE it is: the gap between now and when the timetable has it where the rider
 *    is. Every stop ahead is shifted by it, so a connection it makes impossible is not
 *    offered.
 *  - WHERE to get off: every stop ahead is tried as the alighting point, onward trips
 *    are planned from each, and only the ones nothing else beats are kept. Staying on
 *    and walking from a later stop is one of them.
 */

const MOTIS_BASE = `http://localhost:${process.env.MOTIS_PORT || '3504'}`

/** GPS on a moving bus, plus a stop placed across the road from its line. */
const MAX_OFF_ROUTE_M = 300
/** Israeli buses run this late (and, less often, this early) on an ordinary day. */
const MAX_SCHEDULE_DEVIATION_MIN = 30
/**
 * What a minute of disagreement with the timetable is worth, in metres, when ranking
 * trips. A bus covers 500–1000 m a minute, so being 5 minutes off the timetable is about
 * as telling as being 250 m off the road.
 */
const METERS_PER_DEVIATION_MIN = 50
/** Further than this from the road's direction is the line's other direction. */
const MAX_HEADING_DIFF_DEG = 100
/**
 * The road's direction is read over this many metres either side of the rider. At a
 * stop the nearest piece of polyline is often a metre long and points anywhere.
 */
const DIRECTION_SPAN_M = 40
/** Half-width of the active-trips box, in degrees (~2 km). */
const SEARCH_BOX_DEG = 0.02
/** Getting off one bus and onto another at the same pole is not instant. */
const TRANSFER_BUFFER_MS = 2 * 60_000
const PLAN_CONCURRENCY = 8
/**
 * Walking that differs by less than this is the same walk. Without it, a change of bus
 * that arrives seven minutes later survived for saving one second of walking.
 */
const WALK_SLACK_S = 3 * 60

export interface MotisStop {
  name?: string
  stopId?: string
  lat?: number
  lon?: number
  arrival?: string
  departure?: string
  scheduledArrival?: string
  scheduledDeparture?: string
  dropoffType?: string
}

interface MotisSegment {
  trips?: { tripId?: string; routeShortName?: string }[]
  mode?: string
  from?: MotisStop
  to?: MotisStop
  departure?: string
  arrival?: string
  scheduledArrival?: string
  polyline?: string
}

export interface OnboardLeg {
  mode?: string
  from?: MotisStop
  to?: MotisStop
  startTime?: string
  endTime?: string
  duration?: number
  distance?: number
  routeShortName?: string
  routeColor?: string
  agencyName?: string
  legGeometry?: { points?: string; precision?: number }
  intermediateStops?: MotisStop[]
  tripId?: string
  routeId?: string
  headsign?: string
  realTime?: boolean
  scheduledStartTime?: string
  scheduledEndTime?: string
  /** The rider is already on this vehicle: there is no boarding, and nothing to pay. */
  onboard?: boolean
}

export interface OnboardItinerary {
  duration?: number
  startTime?: string
  endTime?: string
  transfers?: number
  legs?: OnboardLeg[]
}

interface PlanResult {
  itineraries?: OnboardItinerary[]
  direct?: OnboardItinerary[]
}

/** Plans onward from a place at a time; the caller owns modes and walk limits. */
export type PlanFrom = (lat: number, lon: number, timeIso: string) => Promise<PlanResult>

/** Which trip the rider is on, and where on it they are. */
export interface RiddenTrip {
  tripId: string
  line: string
  headsign: string
  /** The next stop the bus will reach — the first place the rider can get off. */
  nextStop: string
  /** Positive when the bus is behind its timetable. */
  delayMinutes: number
}

export interface LineSuggestion {
  line: string
  headsign: string
  mode: string
  nextStop: string
}

// --- Fitting a position to a road --------------------------------------------

interface Fit {
  distM: number
  /** Scheduled instant for the fitted point. */
  scheduledMs: number
  score: number
}

function toXY(lat: number, lon: number, originLat: number, originLon: number): [number, number] {
  const kx = 111_320 * Math.cos((originLat * Math.PI) / 180)
  return [(lon - originLon) * kx, (lat - originLat) * 111_320]
}

function angleDiff(a: number, b: number): number {
  const d = Math.abs(a - b) % 360
  return d > 180 ? 360 - d : d
}

/**
 * Where on one stop-to-stop stretch of road the rider is, and when the timetable has
 * the bus there. Null when the rider is not on this stretch: too far from the road,
 * travelling against it, or at a time the bus is nowhere near.
 */
function fitSegment(
  seg: MotisSegment,
  lat: number,
  lon: number,
  heading: number | null,
  nowMs: number
): Fit | null {
  const dep = Date.parse(seg.departure || '')
  const arr = Date.parse(seg.arrival || '')
  if (!Number.isFinite(dep) || !Number.isFinite(arr) || !seg.polyline) return null
  const points = polyline.decode(seg.polyline, 5).map(([pLat, pLon]) => toXY(pLat, pLon, lat, lon))
  if (points.length < 2) return null

  const lengths: number[] = []
  let total = 0
  for (let i = 1; i < points.length; i++) {
    const l = Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1])
    lengths.push(l)
    total += l
  }

  let best: { distM: number; along: number } | null = null
  let walked = 0
  for (let i = 1; i < points.length; i++) {
    const [ax, ay] = points[i - 1]
    const [bx, by] = points[i]
    const dx = bx - ax
    const dy = by - ay
    const len2 = dx * dx + dy * dy
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2))
    const distM = Math.hypot(ax + t * dx, ay + t * dy)
    if (!best || distM < best.distM) best = { distM, along: walked + t * lengths[i - 1] }
    walked += lengths[i - 1]
  }
  if (!best || best.distM > MAX_OFF_ROUTE_M) return null
  if (heading !== null) {
    const pointAt = (along: number): [number, number] => {
      let rest = Math.max(0, Math.min(total, along))
      for (let i = 1; i < points.length; i++) {
        if (rest <= lengths[i - 1] || i === points.length - 1) {
          const f = lengths[i - 1] === 0 ? 0 : Math.min(1, rest / lengths[i - 1])
          return [
            points[i - 1][0] + f * (points[i][0] - points[i - 1][0]),
            points[i - 1][1] + f * (points[i][1] - points[i - 1][1]),
          ]
        }
        rest -= lengths[i - 1]
      }
      return points[points.length - 1]
    }
    const [ax, ay] = pointAt(best.along - DIRECTION_SPAN_M)
    const [bx, by] = pointAt(best.along + DIRECTION_SPAN_M)
    // A stretch too short to have a direction says nothing about which way it goes.
    if (Math.hypot(bx - ax, by - ay) >= 15) {
      const bearing = ((Math.atan2(bx - ax, by - ay) * 180) / Math.PI + 360) % 360
      if (angleDiff(heading, bearing) > MAX_HEADING_DIFF_DEG) return null
    }
  }
  const scheduledMs = dep + (total === 0 ? 0 : best.along / total) * (arr - dep)
  const deviationMin = Math.abs(nowMs - scheduledMs) / 60_000
  if (deviationMin > MAX_SCHEDULE_DEVIATION_MIN) return null
  return { distM: best.distM, scheduledMs, score: best.distM + METERS_PER_DEVIATION_MIN * deviationMin }
}

interface TripFit {
  tripId: string
  line: string
  mode: string
  fit: Fit
  segment: MotisSegment
}

/** Every trip that plausibly carries the rider right now, best fit first. */
async function tripsCarryingRider(
  lat: number,
  lon: number,
  heading: number | null,
  nowMs: number
): Promise<TripFit[]> {
  const window = MAX_SCHEDULE_DEVIATION_MIN * 60_000
  const url = `${MOTIS_BASE}/api/v1/map/trips`
    + `?zoom=15&min=${(lat - SEARCH_BOX_DEG).toFixed(5)},${(lon - SEARCH_BOX_DEG).toFixed(5)}`
    + `&max=${(lat + SEARCH_BOX_DEG).toFixed(5)},${(lon + SEARCH_BOX_DEG).toFixed(5)}`
    + `&startTime=${new Date(nowMs - window).toISOString()}&endTime=${new Date(nowMs + window).toISOString()}`
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) })
  if (!res.ok) throw new Error(`MOTIS map/trips returned ${res.status}`)
  const segments: MotisSegment[] = await res.json()

  const best = new Map<string, TripFit>()
  for (const seg of segments) {
    const fit = fitSegment(seg, lat, lon, heading, nowMs)
    if (!fit) continue
    for (const trip of seg.trips || []) {
      if (!trip.tripId || !trip.routeShortName) continue
      const held = best.get(trip.tripId)
      if (!held || fit.score < held.fit.score) {
        best.set(trip.tripId, {
          tripId: trip.tripId,
          line: trip.routeShortName,
          mode: seg.mode || 'BUS',
          fit,
          segment: seg,
        })
      }
    }
  }
  return [...best.values()].sort((a, b) => a.fit.score - b.fit.score)
}

async function fetchTripLeg(tripId: string): Promise<OnboardLeg> {
  const res = await fetch(`${MOTIS_BASE}/api/v1/trip?tripId=${encodeURIComponent(tripId)}`, {
    signal: AbortSignal.timeout(15_000),
  })
  if (!res.ok) throw new Error(`MOTIS trip returned ${res.status}`)
  const data: OnboardItinerary = await res.json()
  const leg = data.legs?.[0]
  if (!leg) throw new Error(`MOTIS has no legs for trip ${tripId}`)
  return leg
}

// --- Suggestions ---------------------------------------------------------------

/**
 * The lines the rider could be on: running past here now and, when the phone knows its
 * heading, going the rider's way. One entry per line and direction, best fit first.
 *
 * Every one of them, not a shortlist. At a city stop two dozen lines share the pavement
 * at the same distance, so only the timetable ranks them, and a line running ten minutes
 * late came 35th — line 33 at שוק עירוני, Be'er Sheva, 2026-09-22, with six shown. The
 * app narrows the list to what the rider types; a cut made here hides the very bus they
 * are on.
 */
export async function suggestLines(
  lat: number,
  lon: number,
  heading: number | null,
  nowMs: number
): Promise<LineSuggestion[]> {
  const fits = await tripsCarryingRider(lat, lon, heading, nowMs)
  // Trips of one line that reach the same next stop from here are departures in one
  // direction; the best-fitting one stands for them. The headsign needs the trip itself.
  const byDirection = new Map<string, TripFit>()
  for (const f of fits) {
    const key = `${f.line}|${f.segment.to?.stopId ?? ''}`
    if (!byDirection.has(key)) byDirection.set(key, f)
  }
  const representatives = [...byDirection.values()]
  const legs = await limited(representatives.map(f => () => fetchTripLeg(f.tripId)), PLAN_CONCURRENCY)
  const seen = new Set<string>()
  const suggestions: LineSuggestion[] = []
  representatives.forEach((f, i) => {
    const headsign = legs[i].headsign || ''
    const key = `${f.line}|${headsign}`
    if (seen.has(key)) return
    seen.add(key)
    suggestions.push({ line: f.line, headsign, mode: f.mode, nextStop: f.segment.to?.name || '' })
  })
  return suggestions
}

// --- Planning from on board -----------------------------------------------------

function isoPlus(iso: string | undefined, ms: number): string | undefined {
  if (!iso) return iso
  const t = Date.parse(iso)
  return Number.isFinite(t) ? new Date(t + ms).toISOString() : iso
}

/** The trip's stops in order: first stop, intermediates, last stop. */
function tripStops(leg: OnboardLeg): MotisStop[] {
  return [leg.from, ...(leg.intermediateStops || []), leg.to].filter((s): s is MotisStop => !!s)
}

/**
 * The stretch of the trip's road from the rider to [stop], as a polyline in the trip's
 * own precision. Drawn only — nothing is computed from it.
 */
function geometryFromRider(leg: OnboardLeg, lat: number, lon: number, stop: MotisStop): string {
  const precision = leg.legGeometry?.precision ?? 7
  const points = polyline.decode(leg.legGeometry?.points || '', precision)
  if (points.length === 0) return ''
  const nearest = (from: number, pLat: number, pLon: number) => {
    let bestIdx = from
    let bestD = Infinity
    for (let k = from; k < points.length; k++) {
      const [x, y] = toXY(points[k][0], points[k][1], pLat, pLon)
      const d = Math.hypot(x, y)
      if (d < bestD) {
        bestD = d
        bestIdx = k
      }
    }
    return bestIdx
  }
  const start = nearest(0, lat, lon)
  const end = nearest(start, stop.lat ?? lat, stop.lon ?? lon)
  const slice: [number, number][] = [[lat, lon], ...points.slice(start + 1, end + 1)]
  return polyline.encode(slice, precision)
}

function rides(itin: OnboardItinerary): OnboardLeg[] {
  return (itin.legs || []).filter(l => l.mode && l.mode !== 'WALK')
}

function walkSeconds(itin: OnboardItinerary): number {
  return (itin.legs || []).filter(l => l.mode === 'WALK').reduce((s, l) => s + (l.duration || 0), 0)
}

/** Runs [tasks] at most [limit] at a time, keeping their order. */
async function limited<T>(tasks: (() => Promise<T>)[], limit: number): Promise<T[]> {
  const results: T[] = new Array(tasks.length)
  let next = 0
  const worker = async () => {
    while (next < tasks.length) {
      const i = next++
      results[i] = await tasks[i]()
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, tasks.length) }, worker))
  return results
}

export class NotOnLineError extends Error {}

/**
 * Journeys for a rider on [line] at (lat, lon). Throws [NotOnLineError] when no trip
 * of that line is passing here now — the rider named a line that is not here, and a
 * plan for some other trip of it would be a plan for a bus they are not on.
 */
export async function planFromOnboard(opts: {
  line: string
  lat: number
  lon: number
  heading: number | null
  nowMs: number
  planFrom: PlanFrom
}): Promise<{ itineraries: OnboardItinerary[]; riding: RiddenTrip }> {
  const { line, lat, lon, heading, nowMs, planFrom } = opts
  const fits = await tripsCarryingRider(lat, lon, heading, nowMs)
  const ours = fits.find(f => f.line === line)
  if (!ours) {
    throw new NotOnLineError(`No line ${line} is passing here now, going your way`)
  }

  const leg = await fetchTripLeg(ours.tripId)
  const stops = tripStops(leg)
  // The fitted stretch ends at the next stop. Match on the stop AND its timetabled
  // arrival: a line that loops calls at the same stop twice.
  const nextStop = ours.segment.to
  const nextArrival = ours.segment.scheduledArrival || ours.segment.arrival
  const nextIdx = stops.findIndex(s =>
    s.stopId === nextStop?.stopId && (s.scheduledArrival || s.arrival) === nextArrival
  )
  if (nextIdx < 0) throw new Error(`Stop ${nextStop?.stopId} is not on trip ${ours.tripId}`)

  const delayMs = nowMs - ours.fit.scheduledMs
  const nowIso = new Date(nowMs).toISOString()
  const alightings = stops
    .map((stop, idx) => ({ stop, idx }))
    .filter(({ stop, idx }) => idx >= nextIdx && stop.dropoffType !== 'NOT_ALLOWED')

  const rideTo = (stop: MotisStop, idx: number): OnboardLeg => {
    const arrival = isoPlus(stop.arrival, delayMs) || nowIso
    return {
      mode: leg.mode,
      from: { name: '', lat, lon },
      to: { ...stop, arrival },
      startTime: nowIso,
      endTime: arrival,
      duration: Math.max(0, Math.round((Date.parse(arrival) - nowMs) / 1000)),
      routeShortName: leg.routeShortName,
      routeColor: leg.routeColor,
      agencyName: leg.agencyName,
      headsign: leg.headsign,
      tripId: leg.tripId,
      routeId: leg.routeId,
      realTime: false,
      intermediateStops: stops.slice(nextIdx, idx).map(s => ({ ...s, arrival: isoPlus(s.arrival, delayMs) })),
      legGeometry: { points: geometryFromRider(leg, lat, lon, stop), precision: leg.legGeometry?.precision ?? 7 },
      onboard: true,
    }
  }

  const perStop = await limited(
    alightings.map(({ stop, idx }) => async () => {
      if (typeof stop.lat !== 'number' || typeof stop.lon !== 'number') return []
      const onBus = rideTo(stop, idx)
      const arriveMs = Date.parse(onBus.endTime || '')
      const plan = await planFrom(stop.lat, stop.lon, new Date(arriveMs + TRANSFER_BUFFER_MS).toISOString())
      const combined: OnboardItinerary[] = []
      // A walk needs no connection, so it starts the moment the rider is off.
      for (const walk of plan.direct || []) {
        const shift = arriveMs - Date.parse(walk.startTime || '')
        combined.push(join(onBus, (walk.legs || []).map(l => ({
          ...l,
          startTime: isoPlus(l.startTime, shift),
          endTime: isoPlus(l.endTime, shift),
        }))))
      }
      for (const onward of plan.itineraries || []) {
        // Boarding this same bus again is staying on it, which a later stop covers.
        if (rides(onward)[0]?.tripId === leg.tripId) continue
        combined.push(join(onBus, onward.legs || []))
      }
      return combined
    }),
    PLAN_CONCURRENCY
  )

  return {
    itineraries: bestJourneys(perStop.flat()),
    riding: {
      tripId: ours.tripId,
      line,
      headsign: leg.headsign || '',
      nextStop: stops[nextIdx].name || '',
      delayMinutes: Math.round(delayMs / 60_000),
    },
  }
}

function join(onBus: OnboardLeg, onward: OnboardLeg[]): OnboardItinerary {
  // MOTIS opens every plan with a walk from the query point; from a stop it is empty.
  const legs = [onBus, ...onward.filter(l => !(l.mode === 'WALK' && (l.duration || 0) === 0))]
  const end = legs[legs.length - 1].endTime || onBus.endTime
  const transitCount = legs.filter(l => l.mode && l.mode !== 'WALK').length
  return {
    startTime: onBus.startTime,
    endTime: end,
    duration: Math.round((Date.parse(end || '') - Date.parse(onBus.startTime || '')) / 1000),
    transfers: Math.max(0, transitCount - 1),
    legs,
  }
}

/**
 * One journey per way of continuing — staying on and walking, or changing to a given
 * line — at its best alighting stop; then only the journeys no other one beats on
 * arrival, changes and walking (within [WALK_SLACK_S]) all at once.
 */
function bestJourneys(all: OnboardItinerary[]): OnboardItinerary[] {
  const endMs = (i: OnboardItinerary) => Date.parse(i.endTime || '')
  const byWay = new Map<string, OnboardItinerary>()
  for (const itin of all) {
    if (!Number.isFinite(endMs(itin))) continue
    const way = rides(itin).slice(1).map(r => r.routeShortName || r.mode).join('>')
    const held = byWay.get(way)
    if (!held || endMs(itin) < endMs(held) || (endMs(itin) === endMs(held) && walkSeconds(itin) < walkSeconds(held))) {
      byWay.set(way, itin)
    }
  }
  const ways = [...byWay.values()]
  const dominated = (a: OnboardItinerary, b: OnboardItinerary) =>
    endMs(b) <= endMs(a) && (b.transfers || 0) <= (a.transfers || 0) &&
    walkSeconds(b) <= walkSeconds(a) + WALK_SLACK_S &&
    (endMs(b) < endMs(a) || (b.transfers || 0) < (a.transfers || 0) || walkSeconds(b) + WALK_SLACK_S < walkSeconds(a))
  return ways
    .filter(a => !ways.some(b => b !== a && dominated(a, b)))
    .sort((a, b) => endMs(a) - endMs(b))
}
