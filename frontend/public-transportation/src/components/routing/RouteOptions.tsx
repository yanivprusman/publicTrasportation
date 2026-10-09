import type { UseRouteOptionsReturn } from '../../hooks/useRouteOptions'
import { RIDE_MINUTE_CHOICES, SCOOTER_SPEED_CHOICES, WALK_MINUTE_CHOICES, type TransitModeKey } from '../../hooks/useRouteOptions'
import { useI18n } from '../../i18n'
import type { TranslationKey } from '../../i18n/translations'
import styles from './RouteOptions.module.css'

const MODE_CHIPS: { key: TransitModeKey; icon: string; labelKey: TranslationKey }[] = [
  { key: 'bus', icon: '🚌', labelKey: 'modes.BUS' },
  { key: 'train', icon: '🚆', labelKey: 'modes.RAIL' },
  { key: 'tram', icon: '🚈', labelKey: 'options.tramLabel' },
]

interface RouteOptionsProps {
  routeOptions: UseRouteOptionsReturn
}

export default function RouteOptions({ routeOptions }: RouteOptionsProps) {
  const { t } = useI18n()
  const { options, toggleMode, setMaxWalkMinutes, toggleScooter, setMaxRideMinutes, setScooterSpeedKmh } = routeOptions
  // With a scooter the first/last mile is ridden, not walked: the cap row keeps
  // its place but asks about the ride, with choices sized for one.
  const capChoices = options.scooter ? RIDE_MINUTE_CHOICES : WALK_MINUTE_CHOICES
  const capMinutes = options.scooter ? options.maxRideMinutes : options.maxWalkMinutes
  const setCap = options.scooter ? setMaxRideMinutes : setMaxWalkMinutes
  const capKind = options.scooter ? 'ride' : 'walk'

  return (
    <div className={styles.wrapper}>
      <div className={styles.modesRow}>
        {MODE_CHIPS.map(({ key, icon, labelKey }) => {
          const active = options.modes[key]
          const label = t(labelKey)
          return (
            <button
              key={key}
              type="button"
              className={`${styles.modeChip} ${active ? styles.modeChipActive : ''}`}
              onClick={() => toggleMode(key)}
              aria-pressed={active}
              title={active ? t('options.excludeMode', { mode: label }) : t('options.includeMode', { mode: label })}
              data-id={`toggle-mode-${key}`}
            >
              <span className={styles.modeIcon} aria-hidden="true">{icon}</span>
              {label}
            </button>
          )
        })}
      </div>
      <div className={styles.scooterRow}>
        <button
          type="button"
          className={`${styles.scooterChip} ${options.scooter ? styles.scooterChipActive : ''}`}
          onClick={toggleScooter}
          aria-pressed={options.scooter}
          title={t('options.scooterTitle')}
          data-id="toggle-scooter"
        >
          <span className={styles.modeIcon} aria-hidden="true">🛴</span>
          {t('options.scooter')}
        </button>
        {options.scooter && (
          <span className={styles.scooterNote} data-id="scooter-note">{t('options.scooterNote')}</span>
        )}
      </div>
      <div className={styles.walkRow}>
        <span className={styles.walkLabel}>{options.scooter ? t('options.maxRide') : t('options.maxWalk')}</span>
        <div className={styles.walkChoices}>
          {capChoices.map(minutes => (
            <button
              key={minutes}
              type="button"
              className={`${styles.walkBtn} ${capMinutes === minutes ? styles.walkBtnActive : ''}`}
              onClick={() => setCap(minutes)}
              aria-pressed={capMinutes === minutes}
              data-id={`set-max-${capKind}-${minutes}`}
            >
              {minutes}
            </button>
          ))}
          <span className={styles.walkUnit}>{t('options.min')}</span>
        </div>
      </div>
      {options.scooter && (
        <div className={styles.walkRow}>
          <span className={styles.walkLabel}>{t('options.scooterSpeed')}</span>
          <div className={styles.walkChoices}>
            {SCOOTER_SPEED_CHOICES.map(kmh => (
              <button
                key={kmh}
                type="button"
                className={`${styles.walkBtn} ${options.scooterSpeedKmh === kmh ? styles.walkBtnActive : ''}`}
                onClick={() => setScooterSpeedKmh(kmh)}
                aria-pressed={options.scooterSpeedKmh === kmh}
                title={t('options.scooterSpeedTitle')}
                data-id={`set-scooter-speed-${kmh}`}
              >
                {kmh}
              </button>
            ))}
            <span className={styles.walkUnit}>{t('options.kmh')}</span>
          </div>
        </div>
      )}
    </div>
  )
}
