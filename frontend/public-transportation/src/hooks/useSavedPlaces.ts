import { useState, useCallback, useEffect } from 'react'
import type { GeocodeSuggestion } from '../types'

const PLACES_KEY = 'pt-saved-places'
const MAX_PLACES = 12

/** Identifies a place by its coordinates, rounded so tiny GPS jitter still matches. */
export function placeKey(place: GeocodeSuggestion): string {
  const r = (n: number) => n.toFixed(4)
  return `${r(place.lat)},${r(place.lon)}`
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

function loadPlaces(): GeocodeSuggestion[] {
  try {
    const raw = localStorage.getItem(PLACES_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isValidPlace)
  } catch {
    return []
  }
}

function persist(places: GeocodeSuggestion[]): void {
  try {
    localStorage.setItem(PLACES_KEY, JSON.stringify(places))
  } catch {}
}

export interface UseSavedPlacesReturn {
  places: GeocodeSuggestion[]
  isSaved: (place: GeocodeSuggestion) => boolean
  toggleSave: (place: GeocodeSuggestion) => void
  remove: (place: GeocodeSuggestion) => void
}

export function useSavedPlaces(): UseSavedPlacesReturn {
  const [places, setPlaces] = useState<GeocodeSuggestion[]>([])

  // Read from storage after mount so server and first client render agree.
  useEffect(() => {
    setPlaces(loadPlaces())
  }, [])

  const isSaved = useCallback(
    (place: GeocodeSuggestion) => {
      const key = placeKey(place)
      return places.some((p) => placeKey(p) === key)
    },
    [places]
  )

  const toggleSave = useCallback((place: GeocodeSuggestion) => {
    const key = placeKey(place)
    setPlaces((prev) => {
      const exists = prev.some((p) => placeKey(p) === key)
      const next = exists
        ? prev.filter((p) => placeKey(p) !== key)
        : [place, ...prev].slice(0, MAX_PLACES)
      persist(next)
      return next
    })
  }, [])

  const remove = useCallback((place: GeocodeSuggestion) => {
    const key = placeKey(place)
    setPlaces((prev) => {
      const next = prev.filter((p) => placeKey(p) !== key)
      persist(next)
      return next
    })
  }, [])

  return { places, isSaved, toggleSave, remove }
}
