import type { Itinerary } from '../types'
import { formatTime } from './time-format'

/**
 * The next departures of the SAME line from the SAME stop, taken from the other
 * itineraries already on screen.
 *
 * No extra request: a result set for one journey almost always contains the same
 * line several times, so that stop's timetable is already in hand. Only later
 * departures count, and only from the identical boarding stop — the same line
 * number leaves two different poles in this country.
 */
export function laterDeparturesOf(target: Itinerary, all: Itinerary[], limit = 2): string[] {
  const ride = target.legs.find(l => l.mode !== 'WALK')
  if (!ride) return []
  const stop = ride.fromStopCode || ride.from.name
  const times = all
    .map(it => it.legs.find(l => l.mode !== 'WALK'))
    .filter((l): l is NonNullable<typeof l> => !!l)
    .filter(l => l.routeShortName === ride.routeShortName)
    .filter(l => (l.fromStopCode || l.from.name) === stop)
    .filter(l => l.startTime > ride.startTime)
    .map(l => l.startTime)
  return [...new Set(times)].sort().slice(0, limit).map(formatTime)
}
