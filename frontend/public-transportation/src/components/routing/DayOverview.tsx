import { useEffect, useMemo, useState } from 'react'
import type { DayDeparture, DayOverviewResult, GeocodeSuggestion } from '../../types'
import { fetchDayOverview, type RouteQueryOptions } from '../../services/routing-api'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle } from '../../utils/mode-colors'
import { useI18n } from '../../i18n'
import styles from './DayOverview.module.css'

interface DayOverviewProps {
  origin: GeocodeSuggestion
  destination: GeocodeSuggestion
  queryOptions: RouteQueryOptions
  /** Load the trip that leaves at this ISO time into the main results view. */
  onPickDeparture: (startTimeIso: string) => void
}

// Chart geometry in viewBox units; the SVG scales to the panel width.
const VB_W = 640
const VB_H = 210
const M_LEFT = 34
const M_RIGHT = 10
const M_TOP = 12
const M_BOTTOM = 26
const PLOT_W = VB_W - M_LEFT - M_RIGHT
const PLOT_H = VB_H - M_TOP - M_BOTTOM

function minutesOfDay(iso: string, dayStartMs: number): number {
  return (Date.parse(iso) - dayStartMs) / 60000
}

export default function DayOverview({ origin, destination, queryOptions, onPickDeparture }: DayOverviewProps) {
  const { t } = useI18n()
  const [data, setData] = useState<DayOverviewResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const { modes, maxWalk } = queryOptions

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setData(null)
    setSelectedKey(null)
    // The service day: today 04:00 to 02:00 tomorrow, local time. Buses that
    // leave after midnight belong to today's chart, not tomorrow's.
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 4, 0, 0)
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 2, 0, 0)
    fetchDayOverview(
      { lat: origin.lat, lon: origin.lon },
      { lat: destination.lat, lon: destination.lon },
      start.toISOString(),
      end.toISOString(),
      { modes, maxWalk }
    )
      .then(result => {
        if (!cancelled) setData(result)
      })
      .catch(err => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [origin.lat, origin.lon, destination.lat, destination.lon, modes, maxWalk, reloadToken])

  const departures = useMemo(() => data?.departures ?? [], [data])

  const chart = useMemo(() => {
    if (departures.length === 0) return null
    const first = new Date(departures[0].startTime)
    // All x positions are minutes since the chart day's midnight, so bars
    // after midnight (25:30) continue the axis instead of wrapping to 01:30.
    const dayStart = new Date(first.getFullYear(), first.getMonth(), first.getDate(), 0, 0, 0)
    const dayStartMs = dayStart.getTime()
    const xs = departures.map(dep => minutesOfDay(dep.startTime, dayStartMs))
    const minX = Math.floor(Math.min(...xs) / 60) * 60
    const maxX = Math.max(Math.ceil(Math.max(...xs) / 60) * 60, minX + 120)
    const maxDurationMin = Math.max(...departures.map(dep => dep.duration / 60))
    const maxY = Math.max(Math.ceil(maxDurationMin / 10) * 10, 10)
    const spanHours = (maxX - minX) / 60
    const hourStep = spanHours <= 8 ? 1 : spanHours <= 16 ? 2 : 3
    const hourTicks: number[] = []
    for (let m = minX; m <= maxX; m += hourStep * 60) hourTicks.push(m)
    const yTickStep = maxY <= 30 ? 10 : maxY <= 60 ? 20 : 30
    const yTicks: number[] = []
    for (let v = yTickStep; v <= maxY; v += yTickStep) yTicks.push(v)
    const fastest = Math.min(...departures.map(dep => dep.duration))
    const barW = Math.max(3, Math.min(12, (PLOT_W / departures.length) * 0.6))
    const xFor = (minutes: number) =>
      M_LEFT + ((minutes - minX) / (maxX - minX)) * PLOT_W
    const bars = departures.map((dep, i) => {
      const h = Math.max(3, (dep.duration / 60 / maxY) * PLOT_H)
      return {
        dep,
        key: `${dep.startTime}|${i}`,
        x: xFor(xs[i]) - barW / 2,
        y: M_TOP + PLOT_H - h,
        h,
        isFastest: dep.duration === fastest,
      }
    })
    return { bars, barW, hourTicks, yTicks, maxY, minX, maxX, xFor, fastest }
  }, [departures])

  const selected: DayDeparture | null = useMemo(() => {
    if (!chart || !selectedKey) return null
    return chart.bars.find(bar => bar.key === selectedKey)?.dep ?? null
  }, [chart, selectedKey])

  const transfersLabel = (dep: DayDeparture) =>
    dep.transfers === 0
      ? t('card.direct')
      : dep.transfers === 1
        ? t('card.transfersOne')
        : t('card.transfersMany', { n: dep.transfers })

  if (loading) {
    return (
      <div className={styles.panel} data-id="day-overview">
        <div className={styles.status}>
          <div className={styles.spinner} />
          {t('day.loading')}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.panel} data-id="day-overview">
        <div className={styles.error} role="alert">
          <span>{t('day.failed')}: {error}</span>
          <button
            className={styles.retryBtn}
            onClick={() => setReloadToken(n => n + 1)}
            type="button"
            data-id="day-overview-retry"
          >
            {t('results.tryAgain')}
          </button>
        </div>
      </div>
    )
  }

  if (!chart) {
    return (
      <div className={styles.panel} data-id="day-overview">
        <div className={styles.empty}>{t('day.none')}</div>
      </div>
    )
  }

  const firstDep = departures[0]
  const lastDep = departures[departures.length - 1]

  return (
    <div className={styles.panel} data-id="day-overview">
      <div className={styles.stats}>
        <div className={styles.stat}>
          <span className={styles.statLabel}>{t('day.first')}</span>
          <span className={styles.statValue}>{formatTime(firstDep.startTime)}</span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>{t('day.last')}</span>
          <span className={styles.statValue}>{formatTime(lastDep.startTime)}</span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>{t('day.fastest')}</span>
          <span className={`${styles.statValue} ${styles.statFastest}`}>{formatDuration(chart.fastest)}</span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>{t('day.departures')}</span>
          <span className={styles.statValue}>{departures.length}</span>
        </div>
      </div>

      {/* Time flows left-to-right regardless of UI language */}
      <div className={styles.chartWrap} dir="ltr">
        <svg
          className={styles.chart}
          viewBox={`0 0 ${VB_W} ${VB_H}`}
          role="img"
          aria-label={t('day.chartLabel')}
        >
          {chart.yTicks.map(v => {
            const y = M_TOP + PLOT_H - (v / chart.maxY) * PLOT_H
            return (
              <g key={`y${v}`}>
                <line className={styles.gridLine} x1={M_LEFT} x2={VB_W - M_RIGHT} y1={y} y2={y} />
                <text className={styles.axisLabel} x={M_LEFT - 5} y={y + 3} textAnchor="end">
                  {v}
                </text>
              </g>
            )
          })}
          {chart.hourTicks.map(m => {
            const x = chart.xFor(m)
            return (
              <g key={`x${m}`}>
                <line className={styles.gridLine} x1={x} x2={x} y1={M_TOP} y2={M_TOP + PLOT_H} />
                <text className={styles.axisLabel} x={x} y={VB_H - 8} textAnchor="middle">
                  {String(Math.floor(m / 60) % 24).padStart(2, '0')}
                </text>
              </g>
            )
          })}
          <line
            className={styles.axisLine}
            x1={M_LEFT}
            x2={VB_W - M_RIGHT}
            y1={M_TOP + PLOT_H}
            y2={M_TOP + PLOT_H}
          />
          {chart.bars.map(bar => (
            <rect
              key={bar.key}
              className={[
                styles.bar,
                bar.isFastest ? styles.barFastest : '',
                bar.key === selectedKey ? styles.barSelected : '',
              ].join(' ')}
              x={bar.x}
              y={bar.y}
              width={chart.barW}
              height={bar.h}
              rx={2}
              role="button"
              tabIndex={0}
              aria-label={`${formatTime(bar.dep.startTime)} · ${formatDuration(bar.dep.duration)} · ${transfersLabel(bar.dep)}`}
              data-id="day-overview-departure"
              onClick={() => setSelectedKey(bar.key)}
              onKeyDown={e => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  setSelectedKey(bar.key)
                }
              }}
            />
          ))}
        </svg>
        <div className={styles.chartCaption}>{t('day.chartCaption')}</div>
      </div>

      {data?.truncated && (
        <div className={styles.truncatedNote}>{t('day.truncatedNote', { n: departures.length })}</div>
      )}

      {selected && (
        <div className={styles.detail} data-id="day-overview-detail">
          <div className={styles.detailInfo}>
            <span className={styles.detailTimes}>
              {formatTime(selected.startTime)} – {formatTime(selected.endTime)}
            </span>
            <span className={styles.detailMeta}>
              {formatDuration(selected.duration)} · {transfersLabel(selected)}
            </span>
            <span className={styles.detailLines}>
              {selected.lines.map((line, i) => (
                <span
                  key={i}
                  className={styles.linePill}
                  style={{ backgroundColor: getModeStyle(line.mode).color }}
                >
                  {line.name || '•'}
                </span>
              ))}
            </span>
          </div>
          <button
            className={styles.showTripBtn}
            onClick={() => onPickDeparture(selected.startTime)}
            type="button"
            data-id="day-overview-show-trip"
          >
            {t('day.show')}
          </button>
        </div>
      )}
    </div>
  )
}
