import { useEffect, useState } from 'react'
import type { Coordinates } from '../../types'
import type { LiveBusState } from '../../hooks/useLiveBus'
import { formatStopDistance } from '../../utils/distance'
import { formatTime } from '../../utils/time-format'
import { useI18n } from '../../i18n'
import styles from './LiveBusStatus.module.css'

interface LiveBusStatusProps {
  state: LiveBusState
  onShowOnMap: (position: Coordinates) => void
}

export default function LiveBusStatus({ state, onShowOnMap }: LiveBusStatusProps) {
  const { t, tm } = useI18n()
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
        <span className={styles.text}>
          {t('liveBus.unavailable', { error: tm(state.error ?? '', { stop: state.stopName }) })}
        </span>
      </div>
    )
  }

  if (state.phase === 'locating') {
    return (
      <div className={`${styles.card} ${styles.muted}`} role="status" data-id="live-bus-status">
        <span className={styles.liveDot} aria-hidden="true" />
        <span className={styles.text}>{t('liveBus.looking', { line: state.lineNumber, stop: state.stopName })}</span>
      </div>
    )
  }

  if (state.phase === 'no-vehicle') {
    return (
      <div className={`${styles.card} ${styles.muted}`} role="status" data-id="live-bus-status">
        <span className={styles.offDot} aria-hidden="true" />
        <span className={styles.text}>
          {t('liveBus.none', { line: state.lineNumber, stop: state.stopName })}
        </span>
      </div>
    )
  }

  let eta: string | null = null
  if (state.expectedArrival) {
    const ms = Date.parse(state.expectedArrival)
    if (!Number.isNaN(ms)) {
      const mins = Math.round((ms - now) / 60000)
      const clock = formatTime(state.expectedArrival)
      eta = mins <= 0
        ? t('liveBus.arrivingNow', { time: clock })
        : t('liveBus.arrives', { time: clock, n: mins })
    }
  }

  return (
    <div className={styles.card} role="status" data-id="live-bus-status">
      <span className={styles.liveBadge}>
        <span className={styles.liveDot} aria-hidden="true" />
        {t('liveBus.live')}
      </span>
      <div className={styles.body}>
        <span className={styles.headline}>
          {state.phase === 'live' && state.vehicle
            ? t('liveBus.distanceFrom', {
                line: state.lineNumber,
                distance: formatStopDistance(state.vehicle.metersFromStop),
                stop: state.stopName,
              })
            : t('liveBus.onWay', { line: state.lineNumber, stop: state.stopName })}
        </span>
        <span className={styles.sub}>
          {eta ?? (state.phase === 'tracked' ? t('liveBus.noEstimate') : t('liveBus.estimateUnavailable'))}
          {state.phase === 'tracked' && t('liveBus.noGps')}
        </span>
      </div>
      {state.phase === 'live' && state.vehicle && (
        <button
          className={styles.mapBtn}
          type="button"
          onClick={() => onShowOnMap(state.vehicle!.position)}
          data-id="show-live-bus-on-map"
        >
          {t('liveBus.showOnMap')}
        </button>
      )}
    </div>
  )
}
