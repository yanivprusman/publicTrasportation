import { useState, useCallback, useMemo, useEffect, useRef } from 'react'
import type { GeocodeSuggestion, RouteResult, Itinerary, DirectAlternative } from '../types'
import { searchRoute, reverseGeocode, type RouteQueryOptions } from '../services/routing-api'
import { useRouteOptions, toRouteQueryOptions, isDefaultOptions, type UseRouteOptionsReturn } from './useRouteOptions'

const ROUTE_STORAGE_KEY = 'pt-saved-route'

/** 'TRANSIT' shows the itinerary list; 'BIKE'/'CAR' show that direct street route. */
export type TravelMode = 'TRANSIT' | 'BIKE' | 'CAR'

function loadSavedRoute(): { origin: GeocodeSuggestion | null; destination: GeocodeSuggestion | null; via?: GeocodeSuggestion | null } {
  try {
    const saved = localStorage.getItem(ROUTE_STORAGE_KEY)
    if (saved) return JSON.parse(saved)
  } catch {}
  return { origin: null, destination: null, via: null }
}

// Identifies an itinerary across pages: MOTIS pages can overlap at the edges,
// so merged lists are deduplicated by departure/arrival times plus the transit
// legs' line-and-stop signature.
function itineraryKey(itin: Itinerary): string {
  const legs = itin.legs
    .filter(leg => leg.mode !== 'WALK')
    .map(leg => `${leg.mode}:${leg.routeShortName || ''}:${leg.from.name}:${leg.to.name}`)
    .join('|')
  return `${itin.startTime}|${itin.endTime}|${legs}`
}

/** Address for a dropped pin; the raw coordinates are the label when the
 *  geocoder cannot name the spot, so a picked point always shows something. */
async function placeName(lat: number, lon: number): Promise<string> {
  try {
    return await reverseGeocode(lat, lon)
  } catch {
    return `${lat.toFixed(4)}, ${lon.toFixed(4)}`
  }
}

export interface UseRoutingReturn {
  origin: GeocodeSuggestion | null
  destination: GeocodeSuggestion | null
  /** Optional intermediate stop the trip must pass through. */
  via: GeocodeSuggestion | null
  setOrigin: (place: GeocodeSuggestion | null) => void
  setDestination: (place: GeocodeSuggestion | null) => void
  setVia: (place: GeocodeSuggestion | null) => void
  setOriginFromCoords: (lat: number, lon: number, name?: string) => void
  setDestinationFromCoords: (lat: number, lon: number, name?: string) => void
  /** null = "leave now": the actual time is resolved when the search runs */
  departureTime: Date | null
  setDepartureTime: (d: Date | null) => void
  arriveBy: boolean
  setArriveBy: (b: boolean) => void
  results: RouteResult | null
  selectedIndex: number
  setSelectedIndex: (i: number) => void
  /** Which way of making the trip is displayed: the transit results or a direct street route. */
  travelMode: TravelMode
  setTravelMode: (mode: TravelMode) => void
  /** Fastest bike/car routes for the current trip (empty until a search returns them). */
  alternatives: DirectAlternative[]
  loading: boolean
  error: string | null
  swapOriginDestination: () => void
  search: () => Promise<void>
  initRoute: (from: GeocodeSuggestion, to: GeocodeSuggestion, opts?: { departureTime?: Date | null; arriveBy?: boolean; via?: GeocodeSuggestion | null }) => void
  selectedItinerary: Itinerary | null
  /** Load the page of trips departing before the earliest shown one. */
  loadEarlier: () => Promise<void>
  /** Load the page of trips departing after the latest shown one. */
  loadLater: () => Promise<void>
  loadingEarlier: boolean
  loadingLater: boolean
  /** Paging failure or exhaustion message — shown next to the paging buttons without discarding results. */
  pagingNotice: string | null
  /** Transit-mode filter and max-walk preference; changes re-run the active search. */
  routeOptions: UseRouteOptionsReturn
}

export function useRouting(): UseRoutingReturn {
  const saved = loadSavedRoute()
  const [origin, setOrigin] = useState<GeocodeSuggestion | null>(saved.origin)
  const [destination, setDestination] = useState<GeocodeSuggestion | null>(saved.destination)
  const [via, setVia] = useState<GeocodeSuggestion | null>(saved.via ?? null)
  // null = "leave now". Resolving to a concrete Date only at search time keeps
  // "Now" searches current — a Date captured at mount goes stale while the tab sits open.
  const [departureTime, setDepartureTime] = useState<Date | null>(null)
  const [arriveBy, setArriveBy] = useState(false)
  const [results, setResults] = useState<RouteResult | null>(null)
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [travelMode, setTravelMode] = useState<TravelMode>('TRANSIT')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Guards against overlapping searches resolving out of order: only the
  // most recently started search may update results/error/loading.
  const searchSeqRef = useRef(0)
  const [pagingDirection, setPagingDirection] = useState<'earlier' | 'later' | null>(null)
  const [pagingNotice, setPagingNotice] = useState<string | null>(null)
  // The exact query the current results came from. Paging must replay it
  // verbatim (same resolved time — not a re-resolved "now") plus the cursor,
  // or MOTIS rejects the cursor as belonging to a different query.
  const lastQueryRef = useRef<{
    from: { lat: number; lon: number }
    to: { lat: number; lon: number }
    via: { lat: number; lon: number } | null
    time: string
    arriveBy: boolean
    options: RouteQueryOptions
  } | null>(null)
  const routeOptions = useRouteOptions()
  // doSearch is a stable callback; the ref lets it read the options that are
  // current when the search actually runs, not the ones captured at creation.
  const optionsRef = useRef(routeOptions.options)
  optionsRef.current = routeOptions.options

  // Persist origin/destination/via to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(ROUTE_STORAGE_KEY, JSON.stringify({ origin, destination, via }))
    } catch {}
  }, [origin, destination, via])

  const setOriginFromCoords = useCallback((lat: number, lon: number, name?: string) => {
    setOrigin({ name: name || `${lat.toFixed(4)}, ${lon.toFixed(4)}`, lat, lon })
    if (!name) {
      placeName(lat, lon).then(addr => setOrigin(prev => prev && prev.lat === lat ? { ...prev, name: addr } : prev))
    }
  }, [])

  const setDestinationFromCoords = useCallback((lat: number, lon: number, name?: string) => {
    setDestination({ name: name || `${lat.toFixed(4)}, ${lon.toFixed(4)}`, lat, lon })
    if (!name) {
      placeName(lat, lon).then(addr => setDestination(prev => prev && prev.lat === lat ? { ...prev, name: addr } : prev))
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
    stopAt: GeocodeSuggestion | null,
    time: Date | null,
    arrive: boolean
  ) => {
    const seq = ++searchSeqRef.current
    setLoading(true)
    setError(null)
    setResults(null)
    setSelectedIndex(0)
    setTravelMode('TRANSIT')
    setPagingDirection(null)
    setPagingNotice(null)
    const resolvedTime = (time ?? new Date()).toISOString()
    const queryOptions = toRouteQueryOptions(optionsRef.current)
    lastQueryRef.current = {
      from: { lat: from.lat, lon: from.lon },
      to: { lat: to.lat, lon: to.lon },
      via: stopAt ? { lat: stopAt.lat, lon: stopAt.lon } : null,
      time: resolvedTime,
      arriveBy: arrive,
      options: queryOptions,
    }
    try {
      const data = await searchRoute(
        { lat: from.lat, lon: from.lon },
        { lat: to.lat, lon: to.lon },
        resolvedTime,
        arrive,
        undefined,
        queryOptions,
        stopAt ? { lat: stopAt.lat, lon: stopAt.lon } : null
      )
      if (seq !== searchSeqRef.current) return
      if (!data.itineraries || data.itineraries.length === 0) {
        if (data.alternatives && data.alternatives.length > 0) {
          // No transit, but the trip is still makeable: show the fastest
          // direct street route instead of a "no routes" dead end.
          setResults(data)
          setTravelMode(data.alternatives[0].mode)
        } else {
          // Known conditions are stored as translation keys and localized at
          // render, so they follow a language switch.
          setError(
            isDefaultOptions(optionsRef.current)
              ? 'errors.noRoutes'
              : 'errors.noRoutesFiltered'
          )
        }
      } else {
        setResults(data)
      }
    } catch (err) {
      if (seq !== searchSeqRef.current) return
      setError(err instanceof Error ? err.message : 'errors.searchFailed')
    } finally {
      if (seq === searchSeqRef.current) setLoading(false)
    }
  }, [])

  const search = useCallback(async () => {
    if (!origin || !destination) {
      setError('errors.setBoth')
      return
    }
    doSearch(origin, destination, via, departureTime, arriveBy)
  }, [origin, destination, via, departureTime, arriveBy, doSearch])

  const loadPage = useCallback(async (direction: 'earlier' | 'later') => {
    const query = lastQueryRef.current
    if (!query || !results || pagingDirection) return
    const cursor = direction === 'earlier' ? results.previousPageCursor : results.nextPageCursor
    if (!cursor) return
    // Joins the same sequence as full searches: a new search started while a
    // page is in flight discards the page result, and vice versa.
    const seq = ++searchSeqRef.current
    setPagingDirection(direction)
    setPagingNotice(null)
    try {
      const data = await searchRoute(query.from, query.to, query.time, query.arriveBy, cursor, query.options, query.via)
      if (seq !== searchSeqRef.current) return
      const known = new Set(results.itineraries.map(itineraryKey))
      const fresh = data.itineraries.filter(itin => !known.has(itineraryKey(itin)))
      // Direct bike/car routes only arrive with the first page — carry the
      // existing ones through, or the comparison strip would vanish on paging.
      if (direction === 'earlier') {
        setResults({
          itineraries: [...fresh, ...results.itineraries],
          alternatives: results.alternatives,
          previousPageCursor: data.previousPageCursor,
          nextPageCursor: results.nextPageCursor,
        })
        // Keep the same itinerary selected after new ones are prepended.
        if (fresh.length > 0) setSelectedIndex(selectedIndex + fresh.length)
      } else {
        setResults({
          itineraries: [...results.itineraries, ...fresh],
          alternatives: results.alternatives,
          previousPageCursor: results.previousPageCursor,
          nextPageCursor: data.nextPageCursor,
        })
      }
      if (fresh.length === 0) {
        setPagingNotice(direction === 'earlier' ? 'errors.noEarlier' : 'errors.noLater')
      }
    } catch (err) {
      if (seq !== searchSeqRef.current) return
      setPagingNotice(err instanceof Error ? err.message : 'errors.loadMoreFailed')
    } finally {
      if (seq === searchSeqRef.current) setPagingDirection(null)
    }
  }, [results, pagingDirection, selectedIndex])

  const loadEarlier = useCallback(() => loadPage('earlier'), [loadPage])
  const loadLater = useCallback(() => loadPage('later'), [loadPage])

  // Changing an option while results (or a no-routes message) are showing
  // re-runs the search immediately, so a mode chip acts as a live filter.
  // Deliberately keyed on options alone: origin/destination/time edits must
  // still wait for an explicit Search tap.
  const optionsAtLastSearchRef = useRef(routeOptions.options)
  useEffect(() => {
    if (routeOptions.options === optionsAtLastSearchRef.current) return
    optionsAtLastSearchRef.current = routeOptions.options
    if (!lastQueryRef.current || !origin || !destination) return
    doSearch(origin, destination, via, departureTime, arriveBy)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeOptions.options])

  const initRoute = useCallback((
    from: GeocodeSuggestion,
    to: GeocodeSuggestion,
    opts?: { departureTime?: Date | null; arriveBy?: boolean; via?: GeocodeSuggestion | null }
  ) => {
    const time = opts?.departureTime ?? null
    const arrive = opts?.arriveBy ?? false
    // Callers that don't carry a via (saved routes, day-overview picks) mean a
    // plain A→B trip — clear any leftover stop so it can't alter their search.
    const stopAt = opts?.via ?? null
    setOrigin(from)
    setDestination(to)
    setVia(stopAt)
    // Sync the time picker to what this search actually uses, so a trip opened
    // from a shared link (or a saved route) shows the real settings, not stale ones.
    setDepartureTime(time)
    setArriveBy(arrive)
    doSearch(from, to, stopAt, time, arrive)
  }, [doSearch])

  const alternatives = useMemo(() => results?.alternatives ?? [], [results])

  const selectedItinerary = useMemo(() => {
    if (travelMode !== 'TRANSIT') {
      return alternatives.find(alt => alt.mode === travelMode)?.itinerary ?? null
    }
    if (!results?.itineraries?.length) return null
    return results.itineraries[selectedIndex] || null
  }, [results, selectedIndex, travelMode, alternatives])

  return {
    origin, destination, via, setOrigin, setDestination, setVia,
    setOriginFromCoords, setDestinationFromCoords,
    swapOriginDestination,
    departureTime, setDepartureTime, arriveBy, setArriveBy,
    results, selectedIndex, setSelectedIndex,
    travelMode, setTravelMode, alternatives,
    loading, error, search, initRoute, selectedItinerary,
    loadEarlier, loadLater,
    loadingEarlier: pagingDirection === 'earlier',
    loadingLater: pagingDirection === 'later',
    pagingNotice,
    routeOptions,
  }
}
