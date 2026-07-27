'use client'

import { useEffect, useState } from 'react'
import { useI18n } from '../../i18n'
import styles from './ServiceAlertBanner.module.css'

/** Israeli rush hours: morning commute and the afternoon/evening return. */
function isPeakHour(hour: number): boolean {
  return (hour >= 7 && hour <= 9) || (hour >= 16 && hour <= 19)
}

/**
 * Service status for the arrivals tab. There is no live disruption feed in the
 * MOT SIRI data the app consumes, so this reports the one thing that can be
 * known without one: whether the rider is travelling in a peak window where
 * delays are routine. Matches the Android ServiceAlertBanner.
 */
export default function ServiceAlertBanner() {
  const { t } = useI18n()
  // Rendered from the clock, so it must not run during SSR — the server's hour
  // would hydrate into a client with a different one.
  const [hour, setHour] = useState<number | null>(null)

  useEffect(() => {
    const tick = () => setHour(new Date().getHours())
    tick()
    const interval = setInterval(tick, 60_000)
    return () => clearInterval(interval)
  }, [])

  if (hour === null) return null

  const peak = isPeakHour(hour)

  return (
    <div
      className={`${styles.banner} ${peak ? styles.peak : styles.normal}`}
      role="status"
      data-id="service-alert-banner"
    >
      <span className={styles.icon} aria-hidden="true">{peak ? '⚠️' : 'ℹ️'}</span>
      <span className={styles.text}>{peak ? t('alerts.peak') : t('alerts.normal')}</span>
    </div>
  )
}
