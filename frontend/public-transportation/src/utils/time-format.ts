import { getLanguage, translate } from '../i18n'

export function formatDuration(seconds: number): string {
  const lang = getLanguage()
  // A NaN/Infinity duration (e.g. from a diff of unparseable dates) would
  // render as "NaN min" / "NaNh NaNmin". Math.max(0, NaN) is NaN, so callers
  // can't guard it away — clamp here so the value is always displayable.
  if (!Number.isFinite(seconds)) return translate(lang, 'duration.na')
  const mins = Math.round(seconds / 60)
  if (mins < 60) return translate(lang, 'duration.min', { n: mins })
  const hours = Math.floor(mins / 60)
  const remaining = mins % 60
  if (remaining === 0) return translate(lang, 'duration.h', { h: hours })
  return translate(lang, 'duration.hmin', { h: hours, m: remaining })
}

export function formatTime(isoString: string): string {
  const d = new Date(isoString)
  // Missing/unparseable times (legs default startTime/endTime to '' in the
  // /api/route transform) would print the literal "Invalid Date" in itinerary
  // cards and details, which call this directly. Show 'N/A' like MarkersLayer
  // and StationArrivals already do for absent times.
  if (isNaN(d.getTime())) return translate(getLanguage(), 'duration.na')
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
}

export function formatTimeDiff(startIso: string, endIso: string): string {
  const diffMs = new Date(endIso).getTime() - new Date(startIso).getTime()
  return formatDuration(Math.max(0, diffMs / 1000))
}

/**
 * Which day a printed time belongs to.
 *
 * Everything here prints "HH:mm" and nothing prints a date, while the Fastest
 * sort orders by trip length: a search made at night puts tomorrow's 06:35 above
 * a bus leaving in ten minutes, and the two look alike. Calendar days in the
 * browser's zone, not 24-hour blocks — a 23:50 search returning 00:10 is
 * "tomorrow" twenty minutes later, because that is how a person reads a date.
 */
function calendarDaysBetween(from: Date, to: Date): number {
  const a = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  const b = new Date(to.getFullYear(), to.getMonth(), to.getDate())
  return Math.round((b.getTime() - a.getTime()) / 86_400_000)
}

/**
 * The day badge for a departure: null for today, so the common case stays clean;
 * "Tomorrow" for the next day; a real date past that, because a search for next
 * week must not read as "tomorrow". Null for an unparsable time — an unknown day
 * is left unlabelled rather than guessed.
 */
export function departureDayLabel(isoString: string, nowMs: number): string | null {
  const d = new Date(isoString)
  if (isNaN(d.getTime())) return null
  const days = calendarDaysBetween(new Date(nowMs), d)
  if (days === 0) return null
  const lang = getLanguage()
  if (days === 1) return translate(lang, 'card.dayTomorrow')
  if (days === -1) return translate(lang, 'card.dayYesterday')
  return d.toLocaleDateString(lang === 'he' ? 'he-IL' : undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })
}

/** "+1" when a trip lands after midnight — how many days the arrival crosses. */
export function nextDayOffset(startIso: string, endIso: string): number {
  const start = new Date(startIso)
  const end = new Date(endIso)
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0
  return Math.max(0, calendarDaysBetween(start, end))
}
