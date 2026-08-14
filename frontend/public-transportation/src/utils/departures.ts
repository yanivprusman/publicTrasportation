import type { Itinerary } from '../types'
import { formatTime, nextDayOffset } from './time-format'

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
  // "then 23:00 · 06:40⁺¹" — after the card's day badge, this list is the last
  // place a day can hide: the next departure of the same line is very often the
  // first one of the following morning, and read bare it looks like a bus leaving
  // in eight hours today. The mark is glued to the digits, with no space for bidi
  // to break at.
  return [...new Set(times)]
    .sort()
    .slice(0, limit)
    .map(iso => formatTime(iso) + nextDayMark(nextDayOffset(ride.startTime, iso)))
}

const SUPERSCRIPT_DIGITS = ['⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹']

function nextDayMark(days: number): string {
  if (days <= 0) return ''
  return '⁺' + String(days).split('').map(d => SUPERSCRIPT_DIGITS[Number(d)] ?? d).join('')
}
