import type { RouteResult, GeocodeSuggestion } from '../types'

export async function searchRoute(
  from: { lat: number; lon: number },
  to: { lat: number; lon: number },
  time?: string,
  arriveBy?: boolean
): Promise<RouteResult> {
  const params = new URLSearchParams({
    from: `${from.lat},${from.lon}`,
    to: `${to.lat},${to.lon}`,
  })
  if (time) params.set('time', time)
  if (arriveBy) params.set('arriveBy', 'true')

  const res = await fetch(`/api/route?${params}`)
  if (!res.ok) {
    const body = await res.text()
    throw new Error(body || `Route search failed (${res.status})`)
  }
  return res.json()
}

export async function geocodeSearch(text: string): Promise<GeocodeSuggestion[]> {
  if (!text.trim()) return []
  const res = await fetch(`/api/geocode?text=${encodeURIComponent(text)}`)
  if (!res.ok) return []
  return res.json()
}

export async function fetchStoptimes(stopId: string, n = 5): Promise<unknown> {
  const res = await fetch(`/api/stoptimes?stopId=${encodeURIComponent(stopId)}&n=${n}`)
  if (!res.ok) throw new Error(`Failed to fetch stoptimes (${res.status})`)
  return res.json()
}
