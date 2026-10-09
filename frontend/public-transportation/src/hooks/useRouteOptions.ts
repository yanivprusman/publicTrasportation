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
  /**
   * The rider has an e-scooter that travels with them: they ride to the first stop
   * and from the last one, and the bus is only for the stretch the scooter can't do.
   * The server plans street legs as rides and drops bus combinations slower than
   * simply riding the whole way.
   */
  scooter: boolean
  /** Longest acceptable scooter ride to/from a stop, in minutes. Only used with scooter. */
  maxRideMinutes: number
}

export const WALK_MINUTE_CHOICES = [5, 10, 15, 20, 30]
// A scooter covers ground a walk never could — 30 minutes is about 7 km at the
// router's bike speed — and that reach is the whole point of the option, so the
// choices start where the walk ones end.
export const RIDE_MINUTE_CHOICES = [10, 20, 30, 45, 60]

// 15 minutes mirrors the MOTIS server default, so "defaults" means the exact
// query the app sent before options existed.
export const DEFAULT_OPTIONS: RouteOptionsState = {
  modes: { bus: true, train: true, tram: true },
  maxWalkMinutes: 15,
  scooter: false,
  maxRideMinutes: 30,
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
    const scooter = parsed?.scooter === true
    const maxRideMinutes = RIDE_MINUTE_CHOICES.includes(parsed?.maxRideMinutes)
      ? parsed.maxRideMinutes
      : DEFAULT_OPTIONS.maxRideMinutes
    // A stored state with every mode off can't produce any route — treat it
    // as corrupt and fall back to all modes on.
    if (!modes.bus && !modes.train && !modes.tram) return { ...DEFAULT_OPTIONS, maxWalkMinutes, scooter, maxRideMinutes }
    return { modes, maxWalkMinutes, scooter, maxRideMinutes }
  } catch {
    return DEFAULT_OPTIONS
  }
}

export function isDefaultOptions(state: RouteOptionsState): boolean {
  return (
    state.modes.bus && state.modes.train && state.modes.tram &&
    state.maxWalkMinutes === DEFAULT_OPTIONS.maxWalkMinutes &&
    !state.scooter
  )
}

/**
 * Converts UI state to the /api/route query contract:
 * - `modes`: app-level keys (bus,train,tram) — omitted when all modes are on,
 *   so default searches stay byte-identical to pre-options queries.
 * - `maxWalk`: minutes — omitted at the 15-minute server default.
 * - `scooter` + `maxRide`: a scooter rider walks to no stop, so the walk cap is
 *   not sent at all (the server refuses the pair); the ride cap always is, since
 *   the server's own ceiling (60 min) is not this UI's default.
 */
export function toRouteQueryOptions(state: RouteOptionsState): { modes?: string; maxWalk?: number; scooter?: boolean; maxRide?: number } {
  const out: { modes?: string; maxWalk?: number; scooter?: boolean; maxRide?: number } = {}
  const active = (['bus', 'train', 'tram'] as TransitModeKey[]).filter(k => state.modes[k])
  if (active.length < 3) out.modes = active.join(',')
  if (state.scooter) {
    out.scooter = true
    out.maxRide = state.maxRideMinutes
  } else if (state.maxWalkMinutes !== DEFAULT_OPTIONS.maxWalkMinutes) {
    out.maxWalk = state.maxWalkMinutes
  }
  return out
}

export interface UseRouteOptionsReturn {
  options: RouteOptionsState
  /** Toggles a mode chip. Ignores the click that would turn off the last active mode. */
  toggleMode: (mode: TransitModeKey) => void
  setMaxWalkMinutes: (minutes: number) => void
  toggleScooter: () => void
  setMaxRideMinutes: (minutes: number) => void
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

  const toggleScooter = useCallback(() => {
    setOptions(prev => ({ ...prev, scooter: !prev.scooter }))
  }, [])

  const setMaxRideMinutes = useCallback((minutes: number) => {
    if (!RIDE_MINUTE_CHOICES.includes(minutes)) return
    setOptions(prev => ({ ...prev, maxRideMinutes: minutes }))
  }, [])

  return { options, toggleMode, setMaxWalkMinutes, toggleScooter, setMaxRideMinutes, isDefault: isDefaultOptions(options) }
}
