import { useState, useCallback, useEffect } from 'react'

const STATIONS_KEY = 'pt-favorite-stations'
const LINES_KEY = 'pt-favorite-lines'
const MAX_FAVORITES = 20

/** A bookmarked stop, kept with its name so the chip is readable without a lookup. */
export interface FavoriteStation {
  code: string
  name: string
}

function isValidStation(s: unknown): s is FavoriteStation {
  return (
    !!s &&
    typeof s === 'object' &&
    typeof (s as FavoriteStation).code === 'string' &&
    !!(s as FavoriteStation).code &&
    typeof (s as FavoriteStation).name === 'string'
  )
}

function load<T>(key: string, valid: (v: unknown) => v is T): T[] {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(valid)
  } catch {
    return []
  }
}

function persist(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {}
}

export interface UseFavoritesReturn {
  stations: FavoriteStation[]
  lines: string[]
  isStationFavorite: (code: string) => boolean
  toggleStation: (station: FavoriteStation) => void
  removeStation: (code: string) => void
  isLineFavorite: (line: string) => boolean
  toggleLine: (line: string) => void
  removeLine: (line: string) => void
}

/**
 * Favorite stations and lines, persisted in localStorage. Mirrors the Android
 * favorites in SettingsStore so the two apps offer the same quick-access lists.
 */
export function useFavorites(): UseFavoritesReturn {
  const [stations, setStations] = useState<FavoriteStation[]>([])
  const [lines, setLines] = useState<string[]>([])

  // Read after mount so the server render and the first client render agree.
  useEffect(() => {
    setStations(load(STATIONS_KEY, isValidStation))
    setLines(load(LINES_KEY, (v): v is string => typeof v === 'string' && !!v))
  }, [])

  const isStationFavorite = useCallback(
    (code: string) => stations.some(s => s.code === code),
    [stations]
  )

  const toggleStation = useCallback((station: FavoriteStation) => {
    if (!station.code) return
    setStations(prev => {
      const exists = prev.some(s => s.code === station.code)
      const next = exists
        ? prev.filter(s => s.code !== station.code)
        : [station, ...prev].slice(0, MAX_FAVORITES)
      persist(STATIONS_KEY, next)
      return next
    })
  }, [])

  const removeStation = useCallback((code: string) => {
    setStations(prev => {
      const next = prev.filter(s => s.code !== code)
      persist(STATIONS_KEY, next)
      return next
    })
  }, [])

  const isLineFavorite = useCallback((line: string) => lines.includes(line), [lines])

  const toggleLine = useCallback((line: string) => {
    const value = line.trim()
    if (!value) return
    setLines(prev => {
      const next = prev.includes(value)
        ? prev.filter(l => l !== value)
        : [value, ...prev].slice(0, MAX_FAVORITES)
      persist(LINES_KEY, next)
      return next
    })
  }, [])

  const removeLine = useCallback((line: string) => {
    setLines(prev => {
      const next = prev.filter(l => l !== line)
      persist(LINES_KEY, next)
      return next
    })
  }, [])

  return {
    stations,
    lines,
    isStationFavorite,
    toggleStation,
    removeStation,
    isLineFavorite,
    toggleLine,
    removeLine,
  }
}
