import { useState, useCallback, useEffect } from 'react'

const STORAGE_KEY = 'pt-route-options'

export type TransitModeKey = 'bus' | 'train' | 'tram'

export interface TransitModeFilter {
  bus: boolean
  train: boolean
  tram: boolean
}

export interface RouteOptionsState {
  modes: TransitModeFilter
  /** Longest acceptable walk to/from the first/last stop, in minutes. */
  maxWalkMinutes: number
}

export const WALK_MINUTE_CHOICES = [5, 10, 15, 20, 30]

// 15 minutes mirrors the MOTIS server default, so "defaults" means the exact
// query the app sent before options existed.
export const DEFAULT_OPTIONS: RouteOptionsState = {
  modes: { bus: true, train: true, tram: true },
  maxWalkMinutes: 15,
}

function loadOptions(): RouteOptionsState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_OPTIONS
    const parsed = JSON.parse(raw)
    const modes: TransitModeFilter = {
      bus: parsed?.modes?.bus !== false,
      train: parsed?.modes?.train !== false,
      tram: parsed?.modes?.tram !== false,
    }
    const maxWalkMinutes = WALK_MINUTE_CHOICES.includes(parsed?.maxWalkMinutes)
      ? parsed.maxWalkMinutes
      : DEFAULT_OPTIONS.maxWalkMinutes
    // A stored state with every mode off can't produce any route — treat it
    // as corrupt and fall back to all modes on.
    if (!modes.bus && !modes.train && !modes.tram) return { ...DEFAULT_OPTIONS, maxWalkMinutes }
    return { modes, maxWalkMinutes }
  } catch {
    return DEFAULT_OPTIONS
  }
}

export function isDefaultOptions(state: RouteOptionsState): boolean {
  return (
    state.modes.bus && state.modes.train && state.modes.tram &&
    state.maxWalkMinutes === DEFAULT_OPTIONS.maxWalkMinutes
  )
}

/**
 * Converts UI state to the /api/route query contract:
 * - `modes`: app-level keys (bus,train,tram) — omitted when all modes are on,
 *   so default searches stay byte-identical to pre-options queries.
 * - `maxWalk`: minutes — omitted at the 15-minute server default.
 */
export function toRouteQueryOptions(state: RouteOptionsState): { modes?: string; maxWalk?: number } {
  const out: { modes?: string; maxWalk?: number } = {}
  const active = (['bus', 'train', 'tram'] as TransitModeKey[]).filter(k => state.modes[k])
  if (active.length < 3) out.modes = active.join(',')
  if (state.maxWalkMinutes !== DEFAULT_OPTIONS.maxWalkMinutes) out.maxWalk = state.maxWalkMinutes
  return out
}

export interface UseRouteOptionsReturn {
  options: RouteOptionsState
  /** Toggles a mode chip. Ignores the click that would turn off the last active mode. */
  toggleMode: (mode: TransitModeKey) => void
  setMaxWalkMinutes: (minutes: number) => void
  isDefault: boolean
}

export function useRouteOptions(): UseRouteOptionsReturn {
  const [options, setOptions] = useState<RouteOptionsState>(loadOptions)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(options))
    } catch {}
  }, [options])

  const toggleMode = useCallback((mode: TransitModeKey) => {
    setOptions(prev => {
      const next = { ...prev.modes, [mode]: !prev.modes[mode] }
      if (!next.bus && !next.train && !next.tram) return prev
      return { ...prev, modes: next }
    })
  }, [])

  const setMaxWalkMinutes = useCallback((minutes: number) => {
    if (!WALK_MINUTE_CHOICES.includes(minutes)) return
    setOptions(prev => ({ ...prev, maxWalkMinutes: minutes }))
  }, [])

  return { options, toggleMode, setMaxWalkMinutes, isDefault: isDefaultOptions(options) }
}
