import type { RouteResult, GeocodeSuggestion } from '../types'

export async function searchRoute(
  from: { lat: number; lon: number },
  to: { lat: number; lon: number },
  time?: string,
  arriveBy?: boolean,
  pageCursor?: string
): Promise<RouteResult> {
  const params = new URLSearchParams({
    from: `${from.lat},${from.lon}`,
    to: `${to.lat},${to.lon}`,
  })
  if (time) params.set('time', time)
  if (arriveBy) params.set('arriveBy', 'true')
  if (pageCursor) params.set('pageCursor', pageCursor)

  const res = await fetch(`/api/route?${params}`)
  if (!res.ok) {
    // The route API reports failures as JSON ({ error, message }); surface
    // those fields instead of the raw body, which renders as a JSON blob in
    // the error UI.
    if (res.headers.get('content-type')?.includes('application/json')) {
      const body: { error?: string; message?: string } = await res.json()
      const detail = [body.error, body.message].filter(Boolean).join(': ')
      throw new Error(detail || `Route search failed (${res.status})`)
    }
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
