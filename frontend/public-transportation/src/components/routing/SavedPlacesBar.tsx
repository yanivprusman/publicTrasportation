import type { GeocodeSuggestion } from '../../types'
import { placeKey } from '../../hooks/useSavedPlaces'
import { useI18n } from '../../i18n'
import styles from './SavedPlacesBar.module.css'

interface SavedPlacesBarProps {
  places: GeocodeSuggestion[]
  onUseAsOrigin: (place: GeocodeSuggestion) => void
  onUseAsDestination: (place: GeocodeSuggestion) => void
  onRemove: (place: GeocodeSuggestion) => void
}

/** First meaningful token of a place name — keeps chips compact. */
function shortName(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return '?'
  const firstPart = trimmed.split(',')[0].trim()
  return firstPart || trimmed
}

export default function SavedPlacesBar({
  places,
  onUseAsOrigin,
  onUseAsDestination,
  onRemove,
}: SavedPlacesBarProps) {
  const { t } = useI18n()
  if (places.length === 0) return null

  return (
    <div className={styles.bar} data-id="saved-places-bar">
      <div className={styles.label}>{t('places.label')}</div>
      <div className={styles.chips}>
        {places.map((place) => {
          const name = shortName(place.name)
          return (
            <div className={styles.chip} key={placeKey(place)}>
              <button
                type="button"
                className={styles.chipMain}
                onClick={() => onUseAsDestination(place)}
                title={t('places.routeTo', { place: place.name })}
                data-id="use-place-destination"
              >
                <span className={styles.pin} aria-hidden="true">📍</span>
                <span className={styles.chipName}>{name}</span>
              </button>
              <button
                type="button"
                className={styles.fromBtn}
                onClick={() => onUseAsOrigin(place)}
                title={t('places.routeFrom', { place: place.name })}
                aria-label={t('places.setAsStart', { name })}
                data-id="use-place-origin"
              >
                {t('places.from')}
              </button>
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => onRemove(place)}
                title={t('places.remove')}
                aria-label={t('places.removeName', { name })}
                data-id="remove-saved-place"
              >
                &times;
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}
