import { useState, useCallback, useMemo } from 'react'
import type { GeocodeSuggestion, RouteResult, Itinerary } from '../types'
import { searchRoute } from '../services/routing-api'

export interface UseRoutingReturn {
  origin: GeocodeSuggestion | null
  destination: GeocodeSuggestion | null
  setOrigin: (place: GeocodeSuggestion | null) => void
  setDestination: (place: GeocodeSuggestion | null) => void
  setOriginFromCoords: (lat: number, lon: number, name?: string) => void
  setDestinationFromCoords: (lat: number, lon: number, name?: string) => void
  departureTime: Date
  setDepartureTime: (d: Date) => void
  arriveBy: boolean
  setArriveBy: (b: boolean) => void
  results: RouteResult | null
  selectedIndex: number
  setSelectedIndex: (i: number) => void
  loading: boolean
  error: string | null
  search: () => Promise<void>
  selectedItinerary: Itinerary | null
}

export function useRouting(): UseRoutingReturn {
  const [origin, setOrigin] = useState<GeocodeSuggestion | null>(null)
  const [destination, setDestination] = useState<GeocodeSuggestion | null>(null)
  const [departureTime, setDepartureTime] = useState<Date>(new Date())
  const [arriveBy, setArriveBy] = useState(false)
  const [results, setResults] = useState<RouteResult | null>(null)
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const setOriginFromCoords = useCallback((lat: number, lon: number, name?: string) => {
    setOrigin({ name: name || `${lat.toFixed(4)}, ${lon.toFixed(4)}`, lat, lon })
  }, [])

  const setDestinationFromCoords = useCallback((lat: number, lon: number, name?: string) => {
    setDestination({ name: name || `${lat.toFixed(4)}, ${lon.toFixed(4)}`, lat, lon })
  }, [])

  const search = useCallback(async () => {
    if (!origin || !destination) {
      setError('Set both origin and destination')
      return
    }
    setLoading(true)
    setError(null)
    setResults(null)
    setSelectedIndex(0)
    try {
      const data = await searchRoute(
        { lat: origin.lat, lon: origin.lon },
        { lat: destination.lat, lon: destination.lon },
        departureTime.toISOString(),
        arriveBy
      )
      if (!data.itineraries || data.itineraries.length === 0) {
        setError('No routes found')
      } else {
        setResults(data)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Route search failed')
    } finally {
      setLoading(false)
    }
  }, [origin, destination, departureTime, arriveBy])

  const selectedItinerary = useMemo(() => {
    if (!results?.itineraries?.length) return null
    return results.itineraries[selectedIndex] || null
  }, [results, selectedIndex])

  return {
    origin, destination, setOrigin, setDestination,
    setOriginFromCoords, setDestinationFromCoords,
    departureTime, setDepartureTime, arriveBy, setArriveBy,
    results, selectedIndex, setSelectedIndex,
    loading, error, search, selectedItinerary,
  }
}
