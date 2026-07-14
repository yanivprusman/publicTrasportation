import type { Itinerary } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import styles from './ItineraryCard.module.css'

interface ItineraryCardProps {
  itinerary: Itinerary
  selected: boolean
  onClick: () => void
}

export default function ItineraryCard({ itinerary, selected, onClick }: ItineraryCardProps) {
  return (
    <div
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      data-id="select-itinerary"
      onClick={onClick}
    >
      <div className={styles.header}>
        <span className={styles.duration}>{formatDuration(itinerary.duration)}</span>
        <span className={styles.transfers}>
          {itinerary.transfers === 0 ? 'Direct' : `${itinerary.transfers} transfer${itinerary.transfers > 1 ? 's' : ''}`}
        </span>
        <span className={styles.times}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
        </span>
      </div>
      <div className={styles.legs}>
        {itinerary.legs.map((leg, i) => {
          const style = getModeStyle(leg.mode, leg.routeColor)
          return (
            <div key={i} className={styles.pill} style={{ background: style.color }}>
              <span className={styles.pillMode}>{getModeLabel(leg.mode)}</span>
              {leg.routeShortName && <span className={styles.pillRoute}>{leg.routeShortName}</span>}
              <span className={styles.pillTime}>{formatDuration(leg.duration)}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
