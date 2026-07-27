import type { FavoriteStation } from '../../hooks/useFavorites'
import { useI18n } from '../../i18n'
import styles from './FavoriteStationsBar.module.css'

interface FavoriteStationsBarProps {
  stations: FavoriteStation[]
  activeCode: string
  onSelect: (station: FavoriteStation) => void
  onRemove: (code: string) => void
}

/** Quick-access chips for bookmarked stops, above the station search. */
export default function FavoriteStationsBar({
  stations, activeCode, onSelect, onRemove,
}: FavoriteStationsBarProps) {
  const { t } = useI18n()

  if (stations.length === 0) return null

  return (
    <div className={styles.bar}>
      <span className={styles.label}>★ {t('fav.stations')}</span>
      <div className={styles.chips}>
        {stations.map(station => (
          <div
            key={station.code}
            className={`${styles.chip} ${station.code === activeCode ? styles.chipActive : ''}`}
          >
            <button
              type="button"
              className={styles.chipMain}
              onClick={() => onSelect(station)}
              title={t('fav.showStation', { name: station.name || station.code })}
              data-id="select-favorite-station"
            >
              {station.name || station.code}
            </button>
            <button
              type="button"
              className={styles.chipRemove}
              onClick={() => onRemove(station.code)}
              title={t('fav.removeNamed', { name: station.name || station.code })}
              aria-label={t('fav.removeNamed', { name: station.name || station.code })}
              data-id="remove-favorite-station"
            >
              &times;
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
