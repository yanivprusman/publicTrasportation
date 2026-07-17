import type { DirectAlternative } from '../../types'
import type { TravelMode } from '../../hooks/useRouting'
import { formatDuration } from '../../utils/time-format'
import { useI18n } from '../../i18n'
import styles from './TravelModeStrip.module.css'

interface TravelModeStripProps {
  /** Duration of the fastest transit itinerary; null when transit found nothing. */
  transitDuration: number | null
  alternatives: DirectAlternative[]
  travelMode: TravelMode
  onSelect: (mode: TravelMode) => void
}

const ALTERNATIVE_META = {
  BIKE: { icon: '🚴', titleKey: 'compare.selectBike' },
  CAR: { icon: '🚗', titleKey: 'compare.selectCar' },
} as const

export default function TravelModeStrip({
  transitDuration, alternatives, travelMode, onSelect,
}: TravelModeStripProps) {
  const { t } = useI18n()

  return (
    <div className={styles.strip} role="group" aria-label={t('compare.title')}>
      {transitDuration !== null && (
        <button
          type="button"
          className={`${styles.chip} ${travelMode === 'TRANSIT' ? styles.chipActive : ''}`}
          onClick={() => onSelect('TRANSIT')}
          aria-pressed={travelMode === 'TRANSIT'}
          title={t('compare.selectTransit')}
          data-id="travel-mode-transit"
        >
          <span className={styles.icon} aria-hidden="true">🚌</span>
          <span className={styles.label}>{t('compare.transit')}</span>
          <span className={styles.duration}>{formatDuration(transitDuration)}</span>
        </button>
      )}
      {alternatives.map(alt => {
        const meta = ALTERNATIVE_META[alt.mode]
        return (
          <button
            key={alt.mode}
            type="button"
            className={`${styles.chip} ${travelMode === alt.mode ? styles.chipActive : ''}`}
            onClick={() => onSelect(alt.mode)}
            aria-pressed={travelMode === alt.mode}
            title={t(meta.titleKey)}
            data-id={`travel-mode-${alt.mode.toLowerCase()}`}
          >
            <span className={styles.icon} aria-hidden="true">{meta.icon}</span>
            <span className={styles.label}>{t(`modes.${alt.mode}`)}</span>
            <span className={styles.duration}>{formatDuration(alt.itinerary.duration)}</span>
          </button>
        )
      })}
    </div>
  )
}
