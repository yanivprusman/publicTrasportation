import type { Itinerary } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import { useI18n } from '../../i18n'
import styles from './ItineraryCard.module.css'

interface ItineraryCardProps {
  itinerary: Itinerary
  selected: boolean
  onClick: () => void
}

export default function ItineraryCard({ itinerary, selected, onClick }: ItineraryCardProps) {
  const { t } = useI18n()
  const totalLegSeconds = itinerary.legs.reduce((sum, leg) => sum + (leg.duration || 0), 0)
  const barDescription = itinerary.legs
    .map(leg => {
      const what = `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''}`
      return `${what} ${formatDuration(leg.duration)}`
    })
    .join(', ')

  return (
    <div
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      data-id="select-itinerary"
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onClick={onClick}
      onKeyDown={(e) => {
        // A bare clickable div is unreachable by keyboard/AT. Activate on
        // Enter/Space like a real button; preventDefault stops Space from
        // scrolling the results list instead of selecting the itinerary.
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
    >
      <div className={styles.header}>
        <span className={styles.duration}>{formatDuration(itinerary.duration)}</span>
        <span className={styles.transfers}>
          {itinerary.transfers === 0
            ? t('card.direct')
            : itinerary.transfers === 1
              ? t('card.transfersOne')
              : t('card.transfersMany', { n: itinerary.transfers })}
        </span>
        <span className={styles.times}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
        </span>
      </div>
      {/* Leg widths are proportional to each leg's share of travel time, so the
          walk/ride balance of a route is readable at a glance */}
      <div className={styles.durationBar} role="img" aria-label={barDescription}>
        {itinerary.legs.map((leg, i) => {
          const style = getModeStyle(leg.mode, leg.routeColor)
          const isWalk = leg.mode === 'WALK'
          const share = totalLegSeconds > 0
            ? (leg.duration || 0) / totalLegSeconds
            : 1 / itinerary.legs.length
          const label = isWalk ? '\u{1F6B6}' : (leg.routeShortName || getModeLabel(leg.mode))
          const what = `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''}`
          return (
            <div
              key={i}
              className={`${styles.barSeg} ${isWalk ? styles.barSegWalk : ''}`}
              style={{
                // Floor tiny legs so every segment stays visible and labelable
                flexGrow: Math.max(share, 0.06),
                ...(isWalk ? {} : { background: style.color }),
              }}
              title={`${what} · ${formatDuration(leg.duration)}`}
            >
              <span className={styles.barLabel}>{label}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
