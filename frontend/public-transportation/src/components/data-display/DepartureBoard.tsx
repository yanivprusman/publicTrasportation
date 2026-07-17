import { useEffect, useMemo, useState } from 'react'
import { searchStops } from '../../services/transport-api'
import type { SiriData, MonitoredStopVisit } from '../../types'
import { useI18n } from '../../i18n'
import styles from './DepartureBoard.module.css'

interface DepartureBoardProps {
  siriData: SiriData | null
  error: string | null
  stationCode: string
  lineFilter: string
  lastUpdated: Date | null
  onClose: () => void
}

/**
 * Full-screen kiosk-style live departure board — amber-on-black like the LED
 * boards at real Israeli stations. Data arrives via the same SIRI polling the
 * Arrivals tab already runs; this component only renders it big. A 1s clock
 * tick drives both the header clock and the per-row minute countdowns.
 */
export default function DepartureBoard({
  siriData, error, stationCode, lineFilter, lastUpdated, onClose,
}: DepartureBoardProps) {
  const { lang, t } = useI18n()
  const [now, setNow] = useState(() => new Date())
  const [stationName, setStationName] = useState('')

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Resolve the station's display name from its code (same lookup
  // TransportControls uses for its placeholder).
  useEffect(() => {
    let cancelled = false
    setStationName('')
    searchStops(stationCode).then(results => {
      const match = results.find(s => s.stopCode === stationCode)
      if (!cancelled && match) setStationName(match.stopName)
    })
    return () => { cancelled = true }
  }, [stationCode])

  const visits = useMemo(() => {
    const all = siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || []
    const filter = lineFilter.trim().toLowerCase()
    const filtered = filter
      ? all.filter(v => (v.MonitoredVehicleJourney.PublishedLineName || '').toString().toLowerCase().trim() === filter)
      : all
    // Soonest first — same ordering contract as the Arrivals table. Visits
    // with a missing/unparseable time sort to the end.
    const arrivalMs = (v: MonitoredStopVisit): number => {
      const time = v.MonitoredVehicleJourney.MonitoredCall?.ExpectedArrivalTime
      const ms = time ? Date.parse(time) : NaN
      return isNaN(ms) ? Infinity : ms
    }
    return [...filtered].sort((a, b) => arrivalMs(a) - arrivalMs(b))
  }, [siriData, lineFilter])

  const locale = lang === 'he' ? 'he-IL' : 'en-GB'
  const clock = now.toLocaleTimeString(locale, {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })

  const stopNames = siriData?._stopNames || {}
  const trimmedFilter = lineFilter.trim()

  let updatedText = ''
  if (lastUpdated) {
    const secs = Math.round((now.getTime() - lastUpdated.getTime()) / 1000)
    updatedText = t('arrivals.updated', {
      ago: secs < 5 ? t('arrivals.justNow') : t('arrivals.secondsAgo', { n: secs }),
    })
  }

  let body: React.ReactNode
  if (error) {
    body = <div className={styles.message}>{error}</div>
  } else if (!siriData) {
    body = <div className={styles.message}>{t('board.loading')}</div>
  } else if (visits.length === 0) {
    body = <div className={styles.message}>{t('board.none')}</div>
  } else {
    body = (
      <div className={styles.rows}>
        {visits.map((visit, index) => {
          const journey = visit.MonitoredVehicleJourney
          const call = journey.MonitoredCall
          const rowKey = visit.ItemIdentifier || journey.VehicleRef || `row-${index}`
          const destination = stopNames[journey.DestinationRef] || journey.DestinationRef || ''

          const ms = call?.ExpectedArrivalTime ? Date.parse(call.ExpectedArrivalTime) : NaN
          const hasTime = !isNaN(ms)
          const diffMin = hasTime ? Math.round((ms - now.getTime()) / 60000) : 0
          const absTime = hasTime
            ? new Date(ms).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit', hour12: false })
            : ''

          return (
            <div className={styles.row} key={rowKey} data-id="board-row">
              <div className={styles.lineCell}>
                <span className={styles.linePill}>{journey.PublishedLineName || '—'}</span>
              </div>
              <div className={styles.destCell}>
                <span className={styles.destName}>{destination || '—'}</span>
                {call?.DistanceFromStop != null && (
                  <span className={styles.destMeta}>
                    {call.DistanceFromStop >= 1000
                      ? t('dist.km', { n: (call.DistanceFromStop / 1000).toFixed(1) })
                      : t('dist.m', { n: call.DistanceFromStop })}
                  </span>
                )}
              </div>
              <div className={styles.etaCell}>
                {!hasTime ? (
                  <span className={styles.etaNow}>—</span>
                ) : diffMin < 1 ? (
                  <span className={`${styles.etaNow} ${styles.flash}`}>{t('board.now')}</span>
                ) : diffMin <= 60 ? (
                  <>
                    <span className={styles.etaNum}>{diffMin}</span>
                    <span className={styles.etaUnit}>{t('board.minUnit')}</span>
                  </>
                ) : (
                  <span className={styles.etaAbs}>{absTime}</span>
                )}
                {hasTime && diffMin >= 1 && diffMin <= 60 && (
                  <span className={styles.etaAbsSmall}>{absTime}</span>
                )}
              </div>
            </div>
          )
        })}
      </div>
    )
  }

  return (
    <div className={styles.board} role="dialog" aria-label={t('board.open')}>
      <header className={styles.header}>
        <div className={styles.stationInfo}>
          <div className={styles.stationName}>
            {stationName || t('map.stop', { code: stationCode })}
          </div>
          <div className={styles.stationMeta}>
            <span>#{stationCode}</span>
            {trimmedFilter && (
              <span className={styles.filterChip}>{t('board.filterNote', { line: trimmedFilter })}</span>
            )}
          </div>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.liveBadge}>
            <span className={styles.liveDot} />
            {t('board.live')}
          </span>
          <span className={styles.clock}>{clock}</span>
          <button
            type="button"
            className={styles.closeBtn}
            onClick={onClose}
            title={t('board.close')}
            aria-label={t('board.close')}
            data-id="close-board-mode"
          >
            ✕
          </button>
        </div>
      </header>

      <div className={styles.columns}>
        <span className={styles.colLine}>{t('board.thLine')}</span>
        <span className={styles.colDest}>{t('board.thDest')}</span>
        <span className={styles.colEta}>{t('board.thArrival')}</span>
      </div>

      {body}

      <footer className={styles.footer}>
        <span>{updatedText}</span>
      </footer>
    </div>
  )
}
