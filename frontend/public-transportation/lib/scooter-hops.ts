import polyline from '@mapbox/polyline'
import { isStreetMode } from './motis-modes'

/**
 * Mid-journey scooter hops: get off a bus early and ride.
 *
 * MOTIS lets a scooter rider ride only at the two ends of a trip. Between two
 * vehicles it knows nothing but its table of short footpaths, so it will never
 * say "leave the 60 at the city entrance, ride to the train". For a rider whose
 * rule is "the bus only for the long stretch" that is the case that matters most:
 * a line that meanders through town before reaching the station costs them
 * fifteen minutes the scooter could skip.
 *
 * So the app does it, the way the on-board planner (lib/onboard.ts) already does
 * for a rider sitting on a bus: for each ride in the best itineraries, every stop
 * along it is a place the rider could step off, and a plan from that stop at the
 * bus's arrival time — in scooter mode, so its first leg is a ride — says where
 * that leads. The front of the original itinerary plus the shortened ride plus
 * that plan is a candidate, kept when it beats the itinerary it came from and no
 * original beats it. One hop per journey: the plan from the stop is itself a
 * scooter-mode plan, so it rides at its own two ends but not in its own middle.
 */

export interface HopStop {
  name?: string
  lat?: number
  lon?: number
  stopId?: string
  arrival?: string
  departure?: string
  scheduledArrival?: string
  scheduledDeparture?: string
  dropoffType?: string
}

export interface HopLeg {
  mode?: string
  from?: HopStop
  to?: HopStop
  startTime?: string
  endTime?: string
  scheduledEndTime?: string
  duration?: number
  distance?: number
  routeShortName?: string
  tripId?: string
  legGeometry?: { points?: string; precision?: number }
  intermediateStops?: HopStop[]
}

export interface HopItinerary {
  duration?: number
  startTime?: string
  endTime?: string
  transfers?: number
  legs?: HopLeg[]
}

/** A scooter-mode plan from a stop to the trip's destination, leaving at [timeIso]. */
export type PlanFromStop = (
  lat: number,
  lon: number,
  timeIso: string
) => Promise<{ itineraries?: HopItinerary[]; direct?: HopItinerary[] }>

/** Stepping off, unfolding, pushing off. */
const UNFOLD_MS = 60_000
/** Leaving a bus that reaches its stop in under this gains nothing worth a plan call. */
const MIN_REMAINING_MS = 4 * 60_000
/** A hop has to buy real time over the itinerary it was cut from. */
const MIN_GAIN_MS = 3 * 60_000
/** Only the best itineraries are worth cutting; the rest are already slower. */
const TOP_ITINERARIES = 3
/** Ceiling on MOTIS calls per search: ~60 ms each, eight at a time. */
const MAX_PLANS = 40
const CONCURRENCY = 8
const MAX_HOPS = 4

const ms = (iso?: string) => Date.parse(iso || '')

interface Cut {
  itin: HopItinerary
  legIndex: number
  /** The stop to step off at — one along the ride, or its own alighting stop. */
  stop: HopStop
  /** Index into the ride's intermediateStops, or -1 for the alighting stop. */
  stopIndex: number
}

function transitLegs(itin: HopItinerary): HopLeg[] {
  return (itin.legs || []).filter(l => !isStreetMode(l.mode))
}

/** Every place the rider could step off, on the rides of the best itineraries. */
function cuts(itineraries: HopItinerary[]): Cut[] {
  const out: Cut[] = []
  const best = itineraries
    .filter(i => Number.isFinite(ms(i.endTime)))
    .sort((a, b) => ms(a.endTime) - ms(b.endTime))
    .slice(0, TOP_ITINERARIES)
  for (const itin of best) {
    const legs = itin.legs || []
    const lastRide = legs.map((l, i) => (isStreetMode(l.mode) ? -1 : i)).reduce((a, b) => Math.max(a, b), -1)
    legs.forEach((leg, legIndex) => {
      if (isStreetMode(leg.mode)) return
      const legEnd = ms(leg.endTime)
      ;(leg.intermediateStops || []).forEach((stop, stopIndex) => {
        if (stop.dropoffType === 'NOT_ALLOWED') return
        if (typeof stop.lat !== 'number' || typeof stop.lon !== 'number') return
        const arrival = ms(stop.arrival || stop.scheduledArrival)
        if (!Number.isFinite(arrival) || !Number.isFinite(legEnd)) return
        if (legEnd - arrival < MIN_REMAINING_MS) return
        out.push({ itin, legIndex, stop, stopIndex })
      })
      // Riding away from the stop the bus was leaving the rider at anyway — in
      // place of the walk MOTIS put there — needs no shortening, only a plan.
      if (legIndex < lastRide && leg.to && typeof leg.to.lat === 'number' && typeof leg.to.lon === 'number') {
        out.push({ itin, legIndex, stop: leg.to, stopIndex: -1 })
      }
    })
  }
  if (out.length <= MAX_PLANS) return out
  // Too many stops to try them all: keep every k-th, so a long ride is sampled
  // along its whole length rather than only at its start.
  const stride = Math.ceil(out.length / MAX_PLANS)
  return out.filter((_, i) => i % stride === 0)
}

/** The ride's geometry from its boarding stop to [stop], where the rider gets off. */
function geometryTo(leg: HopLeg, stop: HopStop): string {
  const precision = leg.legGeometry?.precision ?? 7
  const points = polyline.decode(leg.legGeometry?.points || '', precision)
  if (points.length === 0 || typeof stop.lat !== 'number' || typeof stop.lon !== 'number') return ''
  const kx = 111_320 * Math.cos((stop.lat * Math.PI) / 180)
  let bestIdx = 0
  let bestD = Infinity
  points.forEach(([lat, lon], k) => {
    const d = Math.hypot((lon - stop.lon!) * kx, (lat - stop.lat!) * 111_320)
    if (d < bestD) {
      bestD = d
      bestIdx = k
    }
  })
  return polyline.encode(points.slice(0, bestIdx + 1) as [number, number][], precision)
}

/** The ride as the rider takes it: boarding as planned, off at [cut.stop]. */
function shortened(leg: HopLeg, cut: Cut): HopLeg {
  if (cut.stopIndex < 0) return leg
  const endTime = cut.stop.arrival || cut.stop.scheduledArrival
  const duration = Math.max(0, Math.round((ms(endTime) - ms(leg.startTime)) / 1000))
  const { distance: _distance, ...rest } = leg
  return {
    ...rest,
    to: cut.stop,
    endTime,
    scheduledEndTime: cut.stop.scheduledArrival ?? leg.scheduledEndTime,
    duration,
    intermediateStops: (leg.intermediateStops || []).slice(0, cut.stopIndex),
    legGeometry: { points: geometryTo(leg, cut.stop), precision: leg.legGeometry?.precision ?? 7 },
  }
}

function stitch(cut: Cut, onward: HopItinerary): HopItinerary | null {
  const front = (cut.itin.legs || []).slice(0, cut.legIndex)
  const ride = shortened((cut.itin.legs || [])[cut.legIndex], cut)
  // A plan from a stop opens with a zero-length street leg; it is not a step.
  const tail = (onward.legs || []).filter((l, i) => !(i === 0 && isStreetMode(l.mode) && (l.duration || 0) === 0))
  if (tail.length === 0) return null
  const legs = [...front, ride, ...tail]
  const startTime = cut.itin.startTime
  const endTime = tail[tail.length - 1].endTime
  if (!Number.isFinite(ms(startTime)) || !Number.isFinite(ms(endTime))) return null
  return {
    startTime,
    endTime,
    duration: Math.round((ms(endTime) - ms(startTime)) / 1000),
    transfers: Math.max(0, legs.filter(l => !isStreetMode(l.mode)).length - 1),
    legs,
  }
}

/** The ride sequence, which is what makes two journeys the same way of going. */
function way(itin: HopItinerary): string {
  return transitLegs(itin).map(l => `${l.mode}:${l.routeShortName || ''}`).join('>')
}

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

/**
 * Journeys with one mid-trip scooter hop that beat the itineraries they were cut
 * from, earliest arrival first, at most [MAX_HOPS]. Never includes an original.
 */
export async function expandScooterHops(
  itineraries: HopItinerary[],
  planFromStop: PlanFromStop
): Promise<HopItinerary[]> {
  const all = cuts(itineraries)
  if (all.length === 0) return []
  const perCut = await limited(
    all.map(cut => async () => {
      const leaveAt = ms(cut.stop.arrival || cut.stop.scheduledArrival) + UNFOLD_MS
      let plan: Awaited<ReturnType<PlanFromStop>>
      try {
        plan = await planFromStop(cut.stop.lat!, cut.stop.lon!, new Date(leaveAt).toISOString())
      } catch (error) {
        // One stop's plan failing must not cost the search its other results; the
        // hop it would have found simply is not offered.
        console.error('scooter hop plan failed:', error instanceof Error ? error.message : String(error))
        return []
      }
      const ride = (cut.itin.legs || [])[cut.legIndex]
      const out: HopItinerary[] = []
      for (const onward of [...(plan.itineraries || []), ...(plan.direct || [])]) {
        // Boarding the same bus again is staying on it, which a later stop covers.
        if (transitLegs(onward)[0]?.tripId && transitLegs(onward)[0]?.tripId === ride.tripId) continue
        const stitched = stitch(cut, onward)
        if (!stitched) continue
        if (ms(stitched.endTime) > ms(cut.itin.endTime) - MIN_GAIN_MS) continue
        out.push(stitched)
      }
      return out
    }),
    CONCURRENCY
  )
  // One journey per way of going, the earliest; then only those no original beats
  // on both arrival and changes.
  const byWay = new Map<string, HopItinerary>()
  for (const hop of perCut.flat()) {
    const key = way(hop)
    const held = byWay.get(key)
    if (!held || ms(hop.endTime) < ms(held.endTime)) byWay.set(key, hop)
  }
  const beaten = (hop: HopItinerary) =>
    itineraries.some(o => ms(o.endTime) <= ms(hop.endTime) && (o.transfers || 0) <= (hop.transfers || 0))
  return [...byWay.values()]
    .filter(hop => !beaten(hop))
    .sort((a, b) => ms(a.endTime) - ms(b.endTime))
    .slice(0, MAX_HOPS)
}
