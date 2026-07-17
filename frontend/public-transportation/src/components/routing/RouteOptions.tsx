import type { UseRouteOptionsReturn } from '../../hooks/useRouteOptions'
import { WALK_MINUTE_CHOICES, type TransitModeKey } from '../../hooks/useRouteOptions'
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
  const { options, toggleMode, setMaxWalkMinutes } = routeOptions

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
      <div className={styles.walkRow}>
        <span className={styles.walkLabel}>{t('options.maxWalk')}</span>
        <div className={styles.walkChoices}>
          {WALK_MINUTE_CHOICES.map(minutes => (
            <button
              key={minutes}
              type="button"
              className={`${styles.walkBtn} ${options.maxWalkMinutes === minutes ? styles.walkBtnActive : ''}`}
              onClick={() => setMaxWalkMinutes(minutes)}
              aria-pressed={options.maxWalkMinutes === minutes}
              data-id={`set-max-walk-${minutes}`}
            >
              {minutes}
            </button>
          ))}
          <span className={styles.walkUnit}>{t('options.min')}</span>
        </div>
      </div>
    </div>
  )
}
