import type { DirectAlternative } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle } from '../../utils/mode-colors'
import { useI18n } from '../../i18n'
import styles from './DirectRouteCard.module.css'

interface DirectRouteCardProps {
  alternative: DirectAlternative
}

export default function DirectRouteCard({ alternative }: DirectRouteCardProps) {
  const { t } = useI18n()
  const { mode, distance, itinerary } = alternative
  const style = getModeStyle(mode)
  const km = distance > 0 ? (distance / 1000).toFixed(distance >= 10000 ? 0 : 1) : null

  return (
    <div className={styles.card} data-id="direct-route-summary">
      <div className={styles.header}>
        <span className={styles.icon} style={{ background: style.color }} aria-hidden="true">
          {mode === 'BIKE' ? '🚴' : '🚗'}
        </span>
        <span className={styles.title}>
          {mode === 'BIKE' ? t('direct.bikeTitle') : t('direct.carTitle')}
        </span>
      </div>
      <div className={styles.stats}>
        <span className={styles.duration}>{formatDuration(itinerary.duration)}</span>
        {km && <span className={styles.distance}>{t('direct.km', { km })}</span>}
      </div>
      <div className={styles.times}>
        {t('direct.arrive', { time: formatTime(itinerary.endTime) })}
      </div>
      <div className={styles.note}>
        {mode === 'CAR' ? t('direct.carNote') : t('direct.bikeNote')}
      </div>
    </div>
  )
}
