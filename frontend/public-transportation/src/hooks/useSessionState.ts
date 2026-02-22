import { useState, useCallback } from 'react'

/**
 * Like useState, but persists the value in sessionStorage.
 * Survives Next.js HMR/Fast Refresh without losing user selections.
 *
 * Supports string, number, boolean, and JSON-serializable types.
 */
export function useSessionState<T>(key: string, defaultValue: T): [T, (value: T | ((prev: T) => T)) => void] {
  const prefixedKey = `pt-${key}`

  const [state, setState] = useState<T>(() => {
    try {
      const stored = sessionStorage.getItem(prefixedKey)
      if (stored === null) return defaultValue
      return JSON.parse(stored) as T
    } catch {
      return defaultValue
    }
  })

  const setPersistedState = useCallback((value: T | ((prev: T) => T)) => {
    setState(prev => {
      const next = typeof value === 'function' ? (value as (prev: T) => T)(prev) : value
      try {
        sessionStorage.setItem(prefixedKey, JSON.stringify(next))
      } catch { /* storage full — degrade gracefully */ }
      return next
    })
  }, [prefixedKey])

  return [state, setPersistedState]
}
