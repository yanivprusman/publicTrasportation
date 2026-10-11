import polyline from '@mapbox/polyline'
import { isStreetMode } from './motis-modes'
import type { HopItinerary, HopLeg, HopStop, PlanFromStop } from './scooter-hops'

/**
 * Earlier departures of a line the results already show.
 *
 * MOTIS returns only Pareto-optimal journeys: one is dropped when another leaves
 * no earlier and arrives no later with no more changes. That other journey can be
 * on a different line, so a bus vanishes because a train ties it. On 2026-10-11
 * a search from Yavne at 16:43 showed the 369 at 17:18 (arriving 19:48) and never
 * the 369 at 16:48 (arriving 19:19) — the 17:05 train also reached 19:19, so the
 * 16:48 bus was pruned. The rider was at the stop when it pulled in; the app said
 * the next one was in 34 minutes.
 *
 * A rider shown a line wants that line's earliest bus they can still catch. So for
 * each itinerary, the first ride's line is looked up at its boarding stop for
 * earlier trips the rider can reach in time; each one is ridden to the same
 * alighting stop and a fresh plan from there finishes the journey. A candidate is
 * kept when it arrives no later than the itinerary it came from — including when it
 * arrives at the SAME time, having waited at the transfer for the same onward bus.
 * The rider standing at the stop as the earlier bus pulls in wants to know it is
 * theirs; the wait is on the card. A different line tying it is exactly the case
 * this exists for, so only a shown journey on the very same trips makes it a repeat.
 */

export interface LineStop extends HopStop {
  parentId?: string
  pickupType?: string
}

export interface LineLeg extends HopLeg {
  routeId?: string
  from?: LineStop
  to?: LineStop
  intermediateStops?: LineStop[]
  scheduledStartTime?: string
}

export interface LineItinerary extends HopItinerary {
  legs?: LineLeg[]
  /**
   * Set on every journey this module adds. It may go the same way as a shown one,
   * only on an earlier bus, so the route's one-card-per-way dedupe must not merge them.
   */
  earlierDeparture?: boolean
}

/** Departures from [stopId] from [timeIso] on, as MOTIS /api/v1/stoptimes returns them. */
export type StopTimes = (
  stopId: string,
  timeIso: string
) => Promise<{ place?: LineStop; routeId?: string; tripId?: string; cancelled?: boolean }[]>

/** A whole trip, as MOTIS /api/v1/trip returns it: one leg from its first stop to its last. */
export type TripById = (tripId: string) => Promise<LineItinerary>

/** Leaving the bus and crossing to the next one — what MOTIS itself allows for a change. */
const TRANSFER_MS = 2 * 60_000
/** Earlier trips tried per line; a third earlier bus the rider can still reach is rare. */
const MAX_TRIPS_PER_LINE = 3
const MAX_RESULTS = 3

const ms = (iso?: string) => Date.parse(iso || '')
const shift = (iso: string | undefined, delta: number) =>
  iso && Number.isFinite(ms(iso)) ? new Date(ms(iso) + delta).toISOString().replace('.000Z', 'Z') : iso

function transitLegs(itin: LineItinerary): LineLeg[] {
  return (itin.legs || []).filter(l => !isStreetMode(l.mode))
}

/** The ride sequence and its stops — what makes two journeys the same way of going. */
function way(itin: LineItinerary): string {
  return transitLegs(itin)
    .map(l => `${l.mode}:${l.routeShortName || ''}:${l.from?.name || ''}:${l.to?.name || ''}`)
    .join('|')
}

/** The polyline between the points nearest [from] and [to]. */
function geometryBetween(leg: LineLeg, from: LineStop, to: LineStop): string {
  const precision = leg.legGeometry?.precision ?? 7
  const points = polyline.decode(leg.legGeometry?.points || '', precision)
  if (points.length === 0) return ''
  const nearest = (stop: LineStop, after: number) => {
    if (typeof stop.lat !== 'number' || typeof stop.lon !== 'number') return after
    const kx = 111_320 * Math.cos((stop.lat * Math.PI) / 180)
    let best = after
    let bestD = Infinity
    for (let k = after; k < points.length; k++) {
      const [lat, lon] = points[k]
      const d = Math.hypot((lon - stop.lon) * kx, (lat - stop.lat) * 111_320)
      if (d < bestD) {
        bestD = d
        best = k
      }
    }
    return best
  }
  const a = nearest(from, 0)
  const b = nearest(to, a)
  return polyline.encode(points.slice(a, b + 1) as [number, number][], precision)
}

/** [trip]'s one leg cut to the stretch from [boardId] to [alightId]; null if it does not call at both in order. */
function ride(trip: LineItinerary, boardId: string, alightId: string): LineLeg | null {
  const leg = trip.legs?.[0]
  if (!leg) return null
  const calls = [leg.from, ...(leg.intermediateStops || []), leg.to].filter((s): s is LineStop => !!s)
  const i = calls.findIndex(s => s.stopId === boardId)
  const j = calls.findIndex((s, k) => k > i && s.stopId === alightId)
  if (i < 0 || j < 0) return null
  const from = calls[i]
  const to = calls[j]
  const startTime = from.departure || from.scheduledDeparture
  const endTime = to.arrival || to.scheduledArrival
  if (!Number.isFinite(ms(startTime)) || !Number.isFinite(ms(endTime))) return null
  return {
    ...leg,
    from,
    to,
    startTime,
    endTime,
    scheduledStartTime: from.scheduledDeparture,
    scheduledEndTime: to.scheduledArrival,
    duration: Math.round((ms(endTime) - ms(startTime)) / 1000),
    intermediateStops: calls.slice(i + 1, j),
    legGeometry: { points: geometryBetween(leg, from, to), precision: leg.legGeometry?.precision ?? 7 },
  }
}

interface Source {
  itin: LineItinerary
  rideIndex: number
  leg: LineLeg
  /** When the rider can be at the boarding stop: the search time plus the way there. */
  readyAt: number
}

/**
 * Journeys on an earlier trip of a line already shown that arrive sooner than the
 * itinerary they came from, earliest arrival first, at most [MAX_RESULTS]. Never
 * includes an original. [searchTimeIso] is when the rider sets off.
 */
export async function expandEarlierDepartures(
  itineraries: LineItinerary[],
  searchTimeIso: string,
  stopTimes: StopTimes,
  tripById: TripById,
  planFromStop: PlanFromStop
): Promise<LineItinerary[]> {
  const searchTime = ms(searchTimeIso)
  if (!Number.isFinite(searchTime)) return []

  // One lookup per line and boarding stop, against the soonest-arriving itinerary
  // that rides it — a candidate has to beat that one to be worth showing.
  const sources = new Map<string, Source>()
  for (const itin of itineraries) {
    const legs = itin.legs || []
    const rideIndex = legs.findIndex(l => !isStreetMode(l.mode))
    if (rideIndex < 0) continue
    const leg = legs[rideIndex]
    if (!leg.routeId || !leg.from?.stopId || !leg.to?.stopId || !leg.tripId) continue
    const access = ms(leg.startTime) - ms(itin.startTime)
    if (!Number.isFinite(access) || !Number.isFinite(ms(itin.endTime))) continue
    const key = `${leg.routeId}|${leg.from.stopId}|${leg.to.stopId}`
    const held = sources.get(key)
    if (!held || ms(itin.endTime) < ms(held.itin.endTime)) {
      sources.set(key, { itin, rideIndex, leg, readyAt: searchTime + access })
    }
  }

  const perSource = await Promise.all(
    [...sources.values()].map(async src => {
      const boardAt = src.leg.from!.parentId || src.leg.from!.stopId!
      let departures: Awaited<ReturnType<StopTimes>>
      try {
        departures = await stopTimes(boardAt, new Date(src.readyAt).toISOString())
      } catch (error) {
        // One line's lookup failing must not cost the search its other results.
        console.error('earlier departures lookup failed:', error instanceof Error ? error.message : String(error))
        return []
      }
      const shownDeparture = ms(src.leg.startTime)
      const trips = departures
        .filter(d => d.routeId === src.leg.routeId && d.tripId && d.tripId !== src.leg.tripId && !d.cancelled)
        .filter(d => d.place?.pickupType !== 'NOT_ALLOWED')
        .filter(d => {
          const t = ms(d.place?.departure || d.place?.scheduledDeparture)
          return t >= src.readyAt && t < shownDeparture
        })
        .slice(0, MAX_TRIPS_PER_LINE)

      const out: LineItinerary[] = []
      for (const dep of trips) {
        try {
          const leg = ride(await tripById(dep.tripId!), src.leg.from!.stopId!, src.leg.to!.stopId!)
          if (!leg) continue
          const delta = ms(leg.startTime) - shownDeparture
          const front = (src.itin.legs || []).slice(0, src.rideIndex).map(l => ({
            ...l,
            startTime: shift(l.startTime, delta),
            endTime: shift(l.endTime, delta),
          }))
          const stop = leg.to!
          const plan = await planFromStop(stop.lat!, stop.lon!, new Date(ms(leg.endTime) + TRANSFER_MS).toISOString())
          const onwards = [...(plan.itineraries || []), ...(plan.direct || [])]
          let best: LineItinerary | null = null
          for (const onward of onwards) {
            // Boarding the same bus again is staying on it, which the shown itinerary covers.
            if (transitLegs(onward)[0]?.tripId === leg.tripId) continue
            // A plan from a stop opens with a zero-length street leg; it is not a step.
            const tail = (onward.legs || []).filter((l, i) => !(i === 0 && isStreetMode(l.mode) && (l.duration || 0) === 0))
            if (tail.length === 0) continue
            const legs = [...front, leg, ...tail]
            const startTime = shift(src.itin.startTime, delta)
            const endTime = tail[tail.length - 1].endTime
            if (!Number.isFinite(ms(startTime)) || !Number.isFinite(ms(endTime))) continue
            const candidate: LineItinerary = {
              startTime,
              endTime,
              duration: Math.round((ms(endTime) - ms(startTime)) / 1000),
              transfers: Math.max(0, legs.filter(l => !isStreetMode(l.mode)).length - 1),
              legs,
              earlierDeparture: true,
            }
            if (!best || ms(candidate.endTime) < ms(best.endTime)) best = candidate
          }
          if (best && ms(best.endTime) <= ms(src.itin.endTime)) out.push(best)
        } catch (error) {
          console.error('earlier departure plan failed:', error instanceof Error ? error.message : String(error))
        }
      }
      return out
    })
  )

  // One journey per way of going: the earliest arrival, and on a tie the earliest
  // bus — the one that may be pulling in now. None the results already hold trip
  // for trip.
  const byWay = new Map<string, LineItinerary>()
  for (const c of perSource.flat()) {
    const key = way(c)
    const held = byWay.get(key)
    if (!held || ms(c.endTime) < ms(held.endTime) || (ms(c.endTime) === ms(held.endTime) && ms(c.startTime) < ms(held.startTime))) {
      byWay.set(key, c)
    }
  }
  const trips = (itin: LineItinerary) => transitLegs(itin).map(l => l.tripId || '').join('|')
  const shown = (c: LineItinerary) => itineraries.some(o => trips(o) === trips(c))
  return [...byWay.values()]
    .filter(c => !shown(c))
    .sort((a, b) => ms(a.endTime) - ms(b.endTime) || ms(a.startTime) - ms(b.startTime))
    .slice(0, MAX_RESULTS)
}
