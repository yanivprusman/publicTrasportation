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
