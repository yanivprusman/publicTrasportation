import { useState, useCallback, useRef } from 'react'
import { fetchNearbyStops, type NearbyStop } from '../services/transport-api'
import { useSessionState } from './useSessionState'
import type { Coordinates } from '../types'

export interface UseNearbyStopsReturn {
  stops: NearbyStop[]
  userLocation: Coordinates | null
  radius: number
  loading: boolean
  error: string | null
  located: boolean
  focusSeq: number
  locate: () => void
  changeRadius: (radiusMeters: number) => void
}

export function useNearbyStops(): UseNearbyStopsReturn {
  const [radius, setRadius] = useSessionState('nearbyRadius', 600)
  const [stops, setStops] = useState<NearbyStop[]>([])
  const [userLocation, setUserLocation] = useState<Coordinates | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [focusSeq, setFocusSeq] = useState(0)

  // Only the most recently started lookup (locate or radius change) may update
  // the list — a slow response must not clobber a newer one.
  const seqRef = useRef(0)

  const fetchAround = useCallback(async (location: Coordinates, radiusMeters: number) => {
    const seq = ++seqRef.current
    setLoading(true)
    setError(null)
    try {
      const result = await fetchNearbyStops(location[0], location[1], radiusMeters)
      if (seq !== seqRef.current) return
      setStops(result)
      setLoading(false)
      // Signal the map to refit around the fresh result set.
      setFocusSeq(s => s + 1)
    } catch (err) {
      if (seq !== seqRef.current) return
      setStops([])
      setError(err instanceof Error ? err.message : String(err))
      setLoading(false)
    }
  }, [])

  const locate = useCallback(() => {
    if (!navigator.geolocation) {
      // Known conditions are stored as translation keys, localized at render.
      setError('nearby.noGeo')
      return
    }
    const seq = ++seqRef.current
    setLoading(true)
    setError(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (seq !== seqRef.current) return
        const location: Coordinates = [pos.coords.latitude, pos.coords.longitude]
        setUserLocation(location)
        fetchAround(location, radius)
      },
      (err) => {
        if (seq !== seqRef.current) return
        setLoading(false)
        setError(err.code === err.PERMISSION_DENIED ? 'nearby.denied' : 'nearby.failed')
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }, [fetchAround, radius])

  const changeRadius = useCallback((radiusMeters: number) => {
    setRadius(radiusMeters)
    if (userLocation) fetchAround(userLocation, radiusMeters)
  }, [setRadius, userLocation, fetchAround])

  return {
    stops,
    userLocation,
    radius,
    loading,
    error,
    located: userLocation !== null,
    focusSeq,
    locate,
    changeRadius,
  }
}
