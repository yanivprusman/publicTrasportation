import { useState, useCallback, useMemo } from 'react'
import axios from 'axios'
import type { GeocodeSuggestion, RouteResult, Itinerary } from '../types'
import { searchRoute } from '../services/routing-api'

async function reverseGeocode(lat: number, lon: number): Promise<string> {
  try {
    const resp = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
    )
    const name = resp.data.display_name
    if (!name) return `${lat.toFixed(4)}, ${lon.toFixed(4)}`
    // Return first two parts (street, city) for a concise label
    return name.split(',').slice(0, 2).join(',').trim()
  } catch {
    return `${lat.toFixed(4)}, ${lon.toFixed(4)}`
  }
}

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
    if (!name) {
      reverseGeocode(lat, lon).then(addr => setOrigin(prev => prev && prev.lat === lat ? { ...prev, name: addr } : prev))
    }
  }, [])

  const setDestinationFromCoords = useCallback((lat: number, lon: number, name?: string) => {
    setDestination({ name: name || `${lat.toFixed(4)}, ${lon.toFixed(4)}`, lat, lon })
    if (!name) {
      reverseGeocode(lat, lon).then(addr => setDestination(prev => prev && prev.lat === lat ? { ...prev, name: addr } : prev))
    }
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
