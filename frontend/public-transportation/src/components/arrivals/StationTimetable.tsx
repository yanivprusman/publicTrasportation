'use client'

import { useEffect, useMemo, useState } from 'react'
import {
  fetchStationTimetable,
  departureMs,
  lineLabel,
  type StopTimeEntry,
} from '../../services/timetable-api'
import { useI18n } from '../../i18n'
import styles from './StationTimetable.module.css'

const MAX_ROWS = 14
/** Re-fetching the schedule on the 15s arrivals poll would be wasteful — it barely changes. */
const REFRESH_MS = 5 * 60_000

interface StationTimetableProps {
  stationCode: string
}

/**
 * Scheduled (GTFS) departure board for the selected stop, shown under the live
 * arrivals. Matches the Android StationTimetable: it keeps answering "when is
 * the next one?" at hours when the live SIRI feed reports nothing.
 */
export default function StationTimetable({ stationCode }: StationTimetableProps) {
  const { t } = useI18n()
  const [entries, setEntries] = useState<StopTimeEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [selectedLine, setSelectedLine] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  // Countdowns go stale without a ticker; a minute is enough resolution here.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(id)
  }, [])

  // Line chips belong to the stop that was showing when they were picked.
  useEffect(() => setSelectedLine(null), [stationCode])

  useEffect(() => {
    if (!stationCode) return
    let cancelled = false

    const load = async () => {
      setLoading(true)
      setFailed(false)
      try {
        const result = await fetchStationTimetable(stationCode)
        if (cancelled) return
        setEntries(result)
        setNow(Date.now())
      } catch {
        if (cancelled) return
        // A stop with no GTFS schedule match is a normal outcome, not an app
        // error — say the timetable is unavailable and show nothing else.
        setEntries([])
        setFailed(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    const id = setInterval(load, REFRESH_MS)
    return () => { cancelled = true; clearInterval(id) }
  }, [stationCode])

  // A departure a minute in the past is still useful (the bus may be at the
  // kerb); anything older is noise.
  const upcoming = useMemo(
    () => entries.filter(e => {
      const ms = departureMs(e)
      return ms !== null && ms - now >= -60_000
    }),
    [entries, now]
  )

  const lines = useMemo(() => {
    const seen = Array.from(new Set(upcoming.map(lineLabel)))
    return seen.sort((a, b) => {
      const na = Number(a)
      const nb = Number(b)
      const aNum = Number.isFinite(na)
      const bNum = Number.isFinite(nb)
      if (aNum && bNum) return na - nb
      if (aNum) return -1
      if (bNum) return 1
      return a.localeCompare(b)
    })
  }, [upcoming])

  const shown = useMemo(
    () => upcoming
      .filter(e => selectedLine === null || lineLabel(e) === selectedLine)
      .slice(0, MAX_ROWS),
    [upcoming, selectedLine]
  )

  return (
    <div className={styles.wrap} data-id="station-timetable">
      <div className={styles.header}>
        <span className={styles.icon} aria-hidden="true">🕒</span>
        <div>
          <div className={styles.title}>{t('timetable.title')}</div>
          <div className={styles.caption}>{t('timetable.caption')}</div>
        </div>
      </div>

      {loading && entries.length === 0 ? (
        <div className={styles.note}>{t('timetable.loading')}</div>
      ) : failed ? (
        <div className={styles.error}>{t('timetable.unavailable')}</div>
      ) : shown.length === 0 ? (
        <div className={styles.note}>{t('timetable.none')}</div>
      ) : (
        <>
          {lines.length > 1 && (
            <div className={styles.chips}>
              <button
                type="button"
                className={`${styles.chip} ${selectedLine === null ? styles.chipActive : ''}`}
                onClick={() => setSelectedLine(null)}
                aria-pressed={selectedLine === null}
                data-id="timetable-line-all"
              >
                {t('timetable.all')}
              </button>
              {lines.map(line => (
                <button
                  key={line}
                  type="button"
                  className={`${styles.chip} ${selectedLine === line ? styles.chipActive : ''}`}
                  onClick={() => setSelectedLine(selectedLine === line ? null : line)}
                  aria-pressed={selectedLine === line}
                  data-id="timetable-line-chip"
                >
                  {line}
                </button>
              ))}
            </div>
          )}
          <ul className={styles.rows}>
            {shown.map((entry, i) => (
              <TimetableRow key={`${lineLabel(entry)}-${departureMs(entry)}-${i}`} entry={entry} now={now} />
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function TimetableRow({ entry, now }: { entry: StopTimeEntry; now: number }) {
  const { t } = useI18n()
  const ms = departureMs(entry)
  if (ms === null) return null

  const departure = new Date(ms)
  const time = departure.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const minutes = Math.round((ms - now) / 60_000)
  const isTomorrow = departure.toDateString() !== new Date(now).toDateString()

  const countdown = isTomorrow
    ? t('timetable.tomorrow')
    : minutes <= 0
      ? t('timetable.now')
      : minutes <= 120
        ? t('arrivals.inMin', { n: minutes })
        : null

  return (
    <li className={styles.row}>
      <span className={styles.badge}>{lineLabel(entry)}</span>
      <div className={styles.rowInfo}>
        <span className={styles.headsign} dir="auto">{entry.headsign.replace(/_/g, ' ')}</span>
        {entry.agencyName && <span className={styles.agency} dir="auto">{entry.agencyName}</span>}
      </div>
      <div className={styles.rowTime}>
        <span className={styles.time}>{time}</span>
        {countdown && (
          <span className={isTomorrow ? styles.countdownMuted : styles.countdown}>{countdown}</span>
        )}
      </div>
    </li>
  )
}
