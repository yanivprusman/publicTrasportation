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
  /**
   * The scooter's average speed on the road, in km/h. The router prices every ride
   * with it, so it decides which rides beat which buses — not only how the minutes
   * read. The default is Israel's legal limit; a faster scooter says so here.
   */
  scooterSpeedKmh: number
}

export const WALK_MINUTE_CHOICES = [5, 10, 15, 20, 30]
// A scooter covers ground a walk never could — 30 minutes is about 7 km at the
// router's bike speed — and that reach is the whole point of the option, so the
// choices start where the walk ones end.
export const RIDE_MINUTE_CHOICES = [10, 20, 30, 45, 60]
export const SCOOTER_SPEED_CHOICES = [15, 20, 25, 30, 40]

// 15 minutes mirrors the MOTIS server default, so "defaults" means the exact
// query the app sent before options existed.
export const DEFAULT_OPTIONS: RouteOptionsState = {
  modes: { bus: true, train: true, tram: true },
  maxWalkMinutes: 15,
  scooter: false,
  maxRideMinutes: 30,
  scooterSpeedKmh: 25,
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
    const scooterSpeedKmh = SCOOTER_SPEED_CHOICES.includes(parsed?.scooterSpeedKmh)
      ? parsed.scooterSpeedKmh
      : DEFAULT_OPTIONS.scooterSpeedKmh
    // The row offers all or one. A stored set of two is what the old exclude-toggles
    // could save (no chip would show it), and all-off can't produce a route; both
    // open as all modes on.
    const on = [modes.bus, modes.train, modes.tram].filter(Boolean).length
    if (on !== 1) return { ...DEFAULT_OPTIONS, maxWalkMinutes, scooter, maxRideMinutes, scooterSpeedKmh }
    return { modes, maxWalkMinutes, scooter, maxRideMinutes, scooterSpeedKmh }
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
 * - `scooter` + `maxRide` + `scooterSpeed`: a scooter rider walks to no stop, so the
 *   walk cap is not sent at all (the server refuses the pair); the ride cap and the
 *   speed always are, since the server's own ceiling (60 min) is not this UI's default.
 */
export interface RouteQueryParams {
  modes?: string
  maxWalk?: number
  scooter?: boolean
  maxRide?: number
  scooterSpeed?: number
}

export function toRouteQueryOptions(state: RouteOptionsState): RouteQueryParams {
  const out: RouteQueryParams = {}
  const active = (['bus', 'train', 'tram'] as TransitModeKey[]).filter(k => state.modes[k])
  if (active.length < 3) out.modes = active.join(',')
  if (state.scooter) {
    out.scooter = true
    out.maxRide = state.maxRideMinutes
    out.scooterSpeed = state.scooterSpeedKmh
  } else if (state.maxWalkMinutes !== DEFAULT_OPTIONS.maxWalkMinutes) {
    out.maxWalk = state.maxWalkMinutes
  }
  return out
}

export interface UseRouteOptionsReturn {
  options: RouteOptionsState
  /**
   * The mode row is all, or one: 'all' turns every mode on; a mode shows that mode
   * alone, and selecting the mode that is already alone returns to all. (Three
   * exclude-toggles that all started on made the first click on Bus hide buses.)
   */
  selectMode: (mode: TransitModeKey | 'all') => void
  /** The one selected mode, or null when every mode is on. */
  onlyMode: TransitModeKey | null
  setMaxWalkMinutes: (minutes: number) => void
  toggleScooter: () => void
  setMaxRideMinutes: (minutes: number) => void
  setScooterSpeedKmh: (kmh: number) => void
  isDefault: boolean
}

export function useRouteOptions(): UseRouteOptionsReturn {
  const [options, setOptions] = useState<RouteOptionsState>(loadOptions)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(options))
    } catch {}
  }, [options])

  const selectMode = useCallback((mode: TransitModeKey | 'all') => {
    setOptions(prev => {
      const alone = onlyModeOf(prev.modes)
      if (mode === 'all' || alone === mode) return { ...prev, modes: { bus: true, train: true, tram: true } }
      return { ...prev, modes: { bus: mode === 'bus', train: mode === 'train', tram: mode === 'tram' } }
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

  const setScooterSpeedKmh = useCallback((kmh: number) => {
    if (!SCOOTER_SPEED_CHOICES.includes(kmh)) return
    setOptions(prev => ({ ...prev, scooterSpeedKmh: kmh }))
  }, [])

  return {
    options, selectMode, onlyMode: onlyModeOf(options.modes),
    setMaxWalkMinutes, toggleScooter, setMaxRideMinutes, setScooterSpeedKmh, isDefault: isDefaultOptions(options),
  }
}

export function onlyModeOf(modes: TransitModeFilter): TransitModeKey | null {
  const on = (['bus', 'train', 'tram'] as TransitModeKey[]).filter(k => modes[k])
  return on.length === 1 ? on[0] : null
}
