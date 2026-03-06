import { useState, useCallback, useMemo, useEffect } from 'react'
import axios from 'axios'
import type { GeocodeSuggestion, RouteResult, Itinerary } from '../types'
import { searchRoute } from '../services/routing-api'
import { buildAddressLabel } from '../components/map/MapUtilities'

const ROUTE_STORAGE_KEY = 'pt-saved-route'

function loadSavedRoute(): { origin: GeocodeSuggestion | null; destination: GeocodeSuggestion | null } {
  try {
    const saved = localStorage.getItem(ROUTE_STORAGE_KEY)
    if (saved) return JSON.parse(saved)
  } catch {}
  return { origin: null, destination: null }
}

async function reverseGeocode(lat: number, lon: number): Promise<string> {
  try {
    const resp = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
    )
    return buildAddressLabel(resp.data)
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
  swapOriginDestination: () => void
  search: () => Promise<void>
  initRoute: (from: GeocodeSuggestion, to: GeocodeSuggestion) => void
  selectedItinerary: Itinerary | null
}

export function useRouting(): UseRoutingReturn {
  const saved = loadSavedRoute()
  const [origin, setOrigin] = useState<GeocodeSuggestion | null>(saved.origin)
  const [destination, setDestination] = useState<GeocodeSuggestion | null>(saved.destination)
  const [departureTime, setDepartureTime] = useState<Date>(new Date())
  const [arriveBy, setArriveBy] = useState(false)
  const [results, setResults] = useState<RouteResult | null>(null)
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Persist origin/destination to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(ROUTE_STORAGE_KEY, JSON.stringify({ origin, destination }))
    } catch {}
  }, [origin, destination])

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

  const swapOriginDestination = useCallback(() => {
    const prevOrigin = origin
    const prevDest = destination
    setOrigin(prevDest)
    setDestination(prevOrigin)
  }, [origin, destination])

  const doSearch = useCallback(async (
    from: GeocodeSuggestion,
    to: GeocodeSuggestion,
    time: Date,
    arrive: boolean
  ) => {
    setLoading(true)
    setError(null)
    setResults(null)
    setSelectedIndex(0)
    try {
      const data = await searchRoute(
        { lat: from.lat, lon: from.lon },
        { lat: to.lat, lon: to.lon },
        time.toISOString(),
        arrive
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
  }, [])

  const search = useCallback(async () => {
    if (!origin || !destination) {
      setError('Set both origin and destination')
      return
    }
    doSearch(origin, destination, departureTime, arriveBy)
  }, [origin, destination, departureTime, arriveBy, doSearch])

  const initRoute = useCallback((from: GeocodeSuggestion, to: GeocodeSuggestion) => {
    setOrigin(from)
    setDestination(to)
    doSearch(from, to, new Date(), false)
  }, [doSearch])

  const selectedItinerary = useMemo(() => {
    if (!results?.itineraries?.length) return null
    return results.itineraries[selectedIndex] || null
  }, [results, selectedIndex])

  return {
    origin, destination, setOrigin, setDestination,
    setOriginFromCoords, setDestinationFromCoords,
    swapOriginDestination,
    departureTime, setDepartureTime, arriveBy, setArriveBy,
    results, selectedIndex, setSelectedIndex,
    loading, error, search, initRoute, selectedItinerary,
  }
}
