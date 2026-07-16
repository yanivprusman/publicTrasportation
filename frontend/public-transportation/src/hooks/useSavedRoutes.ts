import { useState, useCallback, useEffect } from 'react'
import type { GeocodeSuggestion } from '../types'

const FAVORITES_KEY = 'pt-favorite-routes'
const RECENTS_KEY = 'pt-recent-routes'
const MAX_RECENTS = 6

export interface SavedRoute {
  origin: GeocodeSuggestion
  destination: GeocodeSuggestion
}

/** Identifies a route by its endpoints, rounded so tiny GPS jitter still matches. */
export function routeKey(origin: GeocodeSuggestion, destination: GeocodeSuggestion): string {
  const r = (n: number) => n.toFixed(4)
  return `${r(origin.lat)},${r(origin.lon)}>${r(destination.lat)},${r(destination.lon)}`
}

function isValidPlace(p: unknown): p is GeocodeSuggestion {
  return (
    !!p &&
    typeof p === 'object' &&
    typeof (p as GeocodeSuggestion).lat === 'number' &&
    typeof (p as GeocodeSuggestion).lon === 'number' &&
    typeof (p as GeocodeSuggestion).name === 'string'
  )
}

function loadRoutes(key: string): SavedRoute[] {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (r): r is SavedRoute => isValidPlace(r?.origin) && isValidPlace(r?.destination)
    )
  } catch {
    return []
  }
}

function save(key: string, routes: SavedRoute[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(routes))
  } catch {}
}

export interface UseSavedRoutesReturn {
  favorites: SavedRoute[]
  recents: SavedRoute[]
  isFavorite: (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => boolean
  toggleFavorite: (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => void
  recordSearch: (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => void
  removeRecent: (route: SavedRoute) => void
  removeFavorite: (route: SavedRoute) => void
}

export function useSavedRoutes(): UseSavedRoutesReturn {
  const [favorites, setFavorites] = useState<SavedRoute[]>([])
  const [recents, setRecents] = useState<SavedRoute[]>([])

  // Read from storage after mount so server and first client render agree.
  useEffect(() => {
    setFavorites(loadRoutes(FAVORITES_KEY))
    setRecents(loadRoutes(RECENTS_KEY))
  }, [])

  const isFavorite = useCallback(
    (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => {
      const key = routeKey(origin, destination)
      return favorites.some((f) => routeKey(f.origin, f.destination) === key)
    },
    [favorites]
  )

  const toggleFavorite = useCallback(
    (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => {
      const key = routeKey(origin, destination)
      setFavorites((prev) => {
        const exists = prev.some((f) => routeKey(f.origin, f.destination) === key)
        const next = exists
          ? prev.filter((f) => routeKey(f.origin, f.destination) !== key)
          : [{ origin, destination }, ...prev]
        save(FAVORITES_KEY, next)
        return next
      })
    },
    []
  )

  const recordSearch = useCallback(
    (origin: GeocodeSuggestion, destination: GeocodeSuggestion) => {
      const key = routeKey(origin, destination)
      setRecents((prev) => {
        const next = [
          { origin, destination },
          ...prev.filter((r) => routeKey(r.origin, r.destination) !== key),
        ].slice(0, MAX_RECENTS)
        save(RECENTS_KEY, next)
        return next
      })
    },
    []
  )

  const removeRecent = useCallback((route: SavedRoute) => {
    const key = routeKey(route.origin, route.destination)
    setRecents((prev) => {
      const next = prev.filter((r) => routeKey(r.origin, r.destination) !== key)
      save(RECENTS_KEY, next)
      return next
    })
  }, [])

  const removeFavorite = useCallback((route: SavedRoute) => {
    const key = routeKey(route.origin, route.destination)
    setFavorites((prev) => {
      const next = prev.filter((f) => routeKey(f.origin, f.destination) !== key)
      save(FAVORITES_KEY, next)
      return next
    })
  }, [])

  return {
    favorites,
    recents,
    isFavorite,
    toggleFavorite,
    recordSearch,
    removeRecent,
    removeFavorite,
  }
}
