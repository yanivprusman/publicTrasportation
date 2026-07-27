import { useCallback, useEffect, useRef, useState } from 'react'
import type { Coordinates } from '../types'

export interface UseFollowLocationReturn {
  following: boolean
  /** True between enabling follow mode and the first fix landing. */
  locating: boolean
  /** Latest fix while following; null when off or not yet located. */
  position: Coordinates | null
  toggle: () => void
  stop: () => void
}

/**
 * Keeps the map centred on the user while enabled — the web counterpart of the
 * Android follow-my-location button.
 *
 * Uses watchPosition rather than repeated getCurrentPosition so the browser can
 * stream fixes at its own cadence, and clears the watch the moment following is
 * turned off so the GPS is not left running in the background.
 */
export function useFollowLocation(): UseFollowLocationReturn {
  const [following, setFollowing] = useState(false)
  const [locating, setLocating] = useState(false)
  const [position, setPosition] = useState<Coordinates | null>(null)
  const watchRef = useRef<number | null>(null)

  const clearWatch = useCallback(() => {
    if (watchRef.current !== null && typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchRef.current)
    }
    watchRef.current = null
  }, [])

  const stop = useCallback(() => {
    clearWatch()
    setFollowing(false)
    setLocating(false)
  }, [clearWatch])

  useEffect(() => clearWatch, [clearWatch])

  const toggle = useCallback(() => {
    if (following) {
      stop()
      return
    }
    if (typeof navigator === 'undefined' || !navigator.geolocation) return

    setFollowing(true)
    setLocating(true)
    watchRef.current = navigator.geolocation.watchPosition(
      pos => {
        setPosition([pos.coords.latitude, pos.coords.longitude])
        setLocating(false)
      },
      () => {
        // A denied or failed fix means follow mode cannot work — leave it off
        // rather than showing a permanently spinning button.
        clearWatch()
        setFollowing(false)
        setLocating(false)
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 5_000 }
    )
  }, [following, stop, clearWatch])

  return { following, locating, position, toggle, stop }
}
