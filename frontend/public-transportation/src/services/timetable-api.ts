import { searchStops } from './transport-api'
import { geocodeSearch } from './routing-api'

/** One scheduled departure from the GTFS timetable, as MOTIS reports it. */
export interface StopTimeEntry {
  place: { departure?: string | null; scheduledDeparture?: string | null }
  mode: string
  realTime: boolean
  headsign: string
  routeShortName: string
  displayName: string
  agencyName: string
}

interface StoptimesResponse {
  stopTimes?: StopTimeEntry[]
}

/** How far a geocoded schedule stop may sit from the GTFS stop before we reject the match. */
const MAX_MATCH_METERS = 250

/** Rail routes often carry no short name; fall back so the badge never reads "?". */
export function lineLabel(entry: StopTimeEntry): string {
  return entry.routeShortName || entry.displayName || entry.mode
}

/** Equirectangular approximation — accurate enough at the scale of one stop. */
function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = (lat1 - lat2) * 111_320
  const dLon = (lon1 - lon2) * 111_320 * Math.cos((lat2 * Math.PI) / 180)
  return Math.sqrt(dLat * dLat + dLon * dLon)
}

/** Departure instant of an entry, or null when neither timestamp parses. */
export function departureMs(entry: StopTimeEntry): number | null {
  const iso = entry.place?.departure || entry.place?.scheduledDeparture
  if (!iso) return null
  const ms = Date.parse(iso)
  return Number.isFinite(ms) ? ms : null
}

/**
 * Scheduled departures for a GTFS stop code.
 *
 * The SIRI feed the arrivals table uses only reports vehicles that are actually
 * running, so it goes empty at night and on Shabbat. This reads the timetable
 * instead, which keeps answering "when is the next one?".
 *
 * /api/stoptimes keys on MOTIS place ids, not GTFS stop codes, so the code is
 * resolved in three hops: code → name+coords via /api/stops, name → nearest
 * same-named MOTIS STOP via /api/geocode, then that id → departures.
 */
export async function fetchStationTimetable(stopCode: string, n = 30): Promise<StopTimeEntry[]> {
  const stops = await searchStops(stopCode)
  const stop = stops.find(s => s.stopCode === stopCode)
  if (!stop) throw new Error(`Stop ${stopCode} not found in GTFS`)

  const candidates = (await geocodeSearch(stop.stopName))
    .filter(g => g.type === 'STOP' && !!g.id)
    .map(g => ({ g, d: distanceMeters(g.lat, g.lon, stop.lat, stop.lon) }))
    .sort((a, b) => a.d - b.d)

  const best = candidates[0]
  // A same-named stop on the other side of the country is not this stop.
  if (!best || best.d > MAX_MATCH_METERS) {
    throw new Error(`No schedule stop matches ${stop.stopName}`)
  }

  const res = await fetch(
    `/api/stoptimes?stopId=${encodeURIComponent(best.g.id!)}&n=${n}`
  )
  if (!res.ok) throw new Error(`Failed to fetch stoptimes (${res.status})`)
  const data: StoptimesResponse = await res.json()
  return Array.isArray(data.stopTimes) ? data.stopTimes : []
}
