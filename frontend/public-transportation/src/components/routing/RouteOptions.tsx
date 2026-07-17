import type { UseRouteOptionsReturn } from '../../hooks/useRouteOptions'
import { WALK_MINUTE_CHOICES, type TransitModeKey } from '../../hooks/useRouteOptions'
import styles from './RouteOptions.module.css'

const MODE_CHIPS: { key: TransitModeKey; icon: string; label: string }[] = [
  { key: 'bus', icon: '🚌', label: 'Bus' },
  { key: 'train', icon: '🚆', label: 'Train' },
  { key: 'tram', icon: '🚈', label: 'Light Rail' },
]

interface RouteOptionsProps {
  routeOptions: UseRouteOptionsReturn
}

export default function RouteOptions({ routeOptions }: RouteOptionsProps) {
  const { options, toggleMode, setMaxWalkMinutes } = routeOptions

  return (
    <div className={styles.wrapper}>
      <div className={styles.modesRow}>
        {MODE_CHIPS.map(({ key, icon, label }) => {
          const active = options.modes[key]
          return (
            <button
              key={key}
              type="button"
              className={`${styles.modeChip} ${active ? styles.modeChipActive : ''}`}
              onClick={() => toggleMode(key)}
              aria-pressed={active}
              title={active ? `Exclude ${label.toLowerCase()} routes` : `Include ${label.toLowerCase()} routes`}
              data-id={`toggle-mode-${key}`}
            >
              <span className={styles.modeIcon} aria-hidden="true">{icon}</span>
              {label}
            </button>
          )
        })}
      </div>
      <div className={styles.walkRow}>
        <span className={styles.walkLabel}>Max walk</span>
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
          <span className={styles.walkUnit}>min</span>
        </div>
      </div>
    </div>
  )
}
