import { useEffect, useState } from 'react'
import type { Coordinates } from '../../types'
import type { LiveBusState } from '../../hooks/useLiveBus'
import { formatStopDistance } from '../../utils/distance'
import { formatTime } from '../../utils/time-format'
import styles from './LiveBusStatus.module.css'

interface LiveBusStatusProps {
  state: LiveBusState
  onShowOnMap: (position: Coordinates) => void
}

function etaText(expectedArrival: string | null, now: number): string | null {
  if (!expectedArrival) return null
  const ms = Date.parse(expectedArrival)
  if (Number.isNaN(ms)) return null
  const mins = Math.round((ms - now) / 60000)
  const clock = formatTime(expectedArrival)
  if (mins <= 0) return `arriving now (${clock})`
  return `arrives ${clock} · in ${mins} min`
}

export default function LiveBusStatus({ state, onShowOnMap }: LiveBusStatusProps) {
  // Keep the "in N min" text fresh between the hook's 15s polls.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 10000)
    return () => clearInterval(id)
  }, [])

  if (state.phase === 'idle') return null

  if (state.phase === 'error') {
    return (
      <div className={`${styles.card} ${styles.muted}`} role="status" data-id="live-bus-status">
        <span className={styles.offDot} aria-hidden="true" />
        <span className={styles.text}>Live bus tracking unavailable: {state.error}</span>
      </div>
    )
  }

  if (state.phase === 'locating') {
    return (
      <div className={`${styles.card} ${styles.muted}`} role="status" data-id="live-bus-status">
        <span className={styles.liveDot} aria-hidden="true" />
        <span className={styles.text}>Looking for bus {state.lineNumber} near {state.stopName}…</span>
      </div>
    )
  }

  if (state.phase === 'no-vehicle') {
    return (
      <div className={`${styles.card} ${styles.muted}`} role="status" data-id="live-bus-status">
        <span className={styles.offDot} aria-hidden="true" />
        <span className={styles.text}>
          No live bus {state.lineNumber} heading to {state.stopName} in the next hour yet.
        </span>
      </div>
    )
  }

  const eta = etaText(state.expectedArrival, now)

  return (
    <div className={styles.card} role="status" data-id="live-bus-status">
      <span className={styles.liveBadge}>
        <span className={styles.liveDot} aria-hidden="true" />
        LIVE
      </span>
      <div className={styles.body}>
        <span className={styles.headline}>
          {state.phase === 'live' && state.vehicle
            ? `Bus ${state.lineNumber} is ${formatStopDistance(state.vehicle.distanceFromStopMeters)} from ${state.stopName}`
            : `Bus ${state.lineNumber} is on its way to ${state.stopName}`}
        </span>
        <span className={styles.sub}>
          {eta ?? (state.phase === 'tracked' ? 'No arrival estimate yet' : 'Arrival estimate unavailable')}
          {state.phase === 'tracked' && ' · no GPS position yet'}
        </span>
      </div>
      {state.phase === 'live' && state.vehicle && (
        <button
          className={styles.mapBtn}
          type="button"
          onClick={() => onShowOnMap(state.vehicle!.position)}
          data-id="show-live-bus-on-map"
        >
          Show on map
        </button>
      )}
    </div>
  )
}
