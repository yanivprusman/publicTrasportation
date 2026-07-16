import type { GeocodeSuggestion } from '../../types'
import { placeKey } from '../../hooks/useSavedPlaces'
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
  if (places.length === 0) return null

  return (
    <div className={styles.bar} data-id="saved-places-bar">
      <div className={styles.label}>Places</div>
      <div className={styles.chips}>
        {places.map((place) => {
          const name = shortName(place.name)
          return (
            <div className={styles.chip} key={placeKey(place)}>
              <button
                type="button"
                className={styles.chipMain}
                onClick={() => onUseAsDestination(place)}
                title={`Route to ${place.name}`}
                data-id="use-place-destination"
              >
                <span className={styles.pin} aria-hidden="true">📍</span>
                <span className={styles.chipName}>{name}</span>
              </button>
              <button
                type="button"
                className={styles.fromBtn}
                onClick={() => onUseAsOrigin(place)}
                title={`Route from ${place.name}`}
                aria-label={`Set ${name} as start`}
                data-id="use-place-origin"
              >
                From
              </button>
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => onRemove(place)}
                title="Remove saved place"
                aria-label={`Remove ${name}`}
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
