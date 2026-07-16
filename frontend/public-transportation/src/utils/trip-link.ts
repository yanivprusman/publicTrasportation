import type { GeocodeSuggestion } from '../types'

export interface SharedTrip {
  origin: GeocodeSuggestion
  destination: GeocodeSuggestion
  /** null = "leave now" — resolved when the recipient's search runs */
  departureTime: Date | null
  arriveBy: boolean
}

function parseLatLon(value: string | null): { lat: number; lon: number } | null {
  if (!value) return null
  const parts = value.split(',')
  if (parts.length !== 2) return null
  const lat = Number(parts[0])
  const lon = Number(parts[1])
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
  return { lat, lon }
}

/**
 * Encode the whole journey into a URL: exact coordinates plus display names,
 * so the recipient's app restores the trip without geocoding, and the time
 * mode ("now" is omitted so it resolves at open time, not at share time).
 */
export function buildTripLink(trip: SharedTrip): string {
  const params = new URLSearchParams()
  params.set('from', `${trip.origin.lat.toFixed(5)},${trip.origin.lon.toFixed(5)}`)
  params.set('fromName', trip.origin.name)
  params.set('to', `${trip.destination.lat.toFixed(5)},${trip.destination.lon.toFixed(5)}`)
  params.set('toName', trip.destination.name)
  if (trip.departureTime) params.set('time', trip.departureTime.toISOString())
  if (trip.arriveBy) params.set('arriveBy', '1')
  return `${window.location.origin}${window.location.pathname}?${params.toString()}`
}

export function parseTripLink(params: URLSearchParams): SharedTrip | null {
  const from = parseLatLon(params.get('from'))
  const to = parseLatLon(params.get('to'))
  if (!from || !to) return null

  const originName = params.get('fromName')?.trim() || `${from.lat.toFixed(4)}, ${from.lon.toFixed(4)}`
  const destinationName = params.get('toName')?.trim() || `${to.lat.toFixed(4)}, ${to.lon.toFixed(4)}`

  // An unparseable time means the sender's "depart at" can't be honored;
  // "leave now" is the only meaningful reading of the link at that point.
  let departureTime: Date | null = null
  const timeRaw = params.get('time')
  if (timeRaw) {
    const parsed = new Date(timeRaw)
    if (!Number.isNaN(parsed.getTime())) departureTime = parsed
  }

  return {
    origin: { name: originName, lat: from.lat, lon: from.lon },
    destination: { name: destinationName, lat: to.lat, lon: to.lon },
    departureTime,
    arriveBy: params.get('arriveBy') === '1',
  }
}
