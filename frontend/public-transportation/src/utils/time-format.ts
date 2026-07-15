export function formatDuration(seconds: number): string {
  // A NaN/Infinity duration (e.g. from a diff of unparseable dates) would
  // render as "NaN min" / "NaNh NaNmin". Math.max(0, NaN) is NaN, so callers
  // can't guard it away — clamp here so the value is always displayable.
  if (!Number.isFinite(seconds)) return 'N/A'
  const mins = Math.round(seconds / 60)
  if (mins < 60) return `${mins} min`
  const hours = Math.floor(mins / 60)
  const remaining = mins % 60
  if (remaining === 0) return `${hours}h`
  return `${hours}h ${remaining}min`
}

export function formatTime(isoString: string): string {
  const d = new Date(isoString)
  // Missing/unparseable times (legs default startTime/endTime to '' in the
  // /api/route transform) would print the literal "Invalid Date" in itinerary
  // cards and details, which call this directly. Show 'N/A' like MarkersLayer
  // and StationArrivals already do for absent times.
  if (isNaN(d.getTime())) return 'N/A'
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
}

export function formatTimeDiff(startIso: string, endIso: string): string {
  const diffMs = new Date(endIso).getTime() - new Date(startIso).getTime()
  return formatDuration(Math.max(0, diffMs / 1000))
}
