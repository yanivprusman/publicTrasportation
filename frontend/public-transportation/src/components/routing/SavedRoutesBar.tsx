import type { SavedRoute } from '../../hooks/useSavedRoutes'
import { routeKey } from '../../hooks/useSavedRoutes'
import styles from './SavedRoutesBar.module.css'

interface SavedRoutesBarProps {
  favorites: SavedRoute[]
  recents: SavedRoute[]
  onSelect: (route: SavedRoute) => void
  onRemoveFavorite: (route: SavedRoute) => void
  onRemoveRecent: (route: SavedRoute) => void
}

/** First meaningful token of a place name — keeps chips compact. */
function shortName(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return '?'
  const firstPart = trimmed.split(',')[0].trim()
  return firstPart || trimmed
}

function RouteChip({
  route,
  favorite,
  onSelect,
  onRemove,
}: {
  route: SavedRoute
  favorite: boolean
  onSelect: (route: SavedRoute) => void
  onRemove: (route: SavedRoute) => void
}) {
  const label = `${shortName(route.origin.name)} → ${shortName(route.destination.name)}`
  const idBase = favorite ? 'favorite-route' : 'recent-route'
  return (
    <div className={`${styles.chip} ${favorite ? styles.favoriteChip : ''}`}>
      <button
        type="button"
        className={styles.chipMain}
        onClick={() => onSelect(route)}
        title={`${route.origin.name} → ${route.destination.name}`}
        data-id={`run-${idBase}`}
      >
        {favorite && <span className={styles.star} aria-hidden="true">★</span>}
        <span className={styles.chipLabel}>{label}</span>
      </button>
      <button
        type="button"
        className={styles.chipRemove}
        onClick={(e) => {
          e.stopPropagation()
          onRemove(route)
        }}
        title="Remove"
        aria-label={`Remove ${label}`}
        data-id={`remove-${idBase}`}
      >
        &times;
      </button>
    </div>
  )
}

export default function SavedRoutesBar({
  favorites,
  recents,
  onSelect,
  onRemoveFavorite,
  onRemoveRecent,
}: SavedRoutesBarProps) {
  const favoriteKeys = new Set(favorites.map((f) => routeKey(f.origin, f.destination)))
  // A route shown as a favorite should not also appear in the recents row.
  const recentsToShow = recents.filter(
    (r) => !favoriteKeys.has(routeKey(r.origin, r.destination))
  )

  if (favorites.length === 0 && recentsToShow.length === 0) return null

  return (
    <div className={styles.bar} data-id="saved-routes-bar">
      {favorites.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionLabel}>Saved</div>
          <div className={styles.chips}>
            {favorites.map((route) => (
              <RouteChip
                key={routeKey(route.origin, route.destination)}
                route={route}
                favorite
                onSelect={onSelect}
                onRemove={onRemoveFavorite}
              />
            ))}
          </div>
        </div>
      )}
      {recentsToShow.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionLabel}>Recent</div>
          <div className={styles.chips}>
            {recentsToShow.map((route) => (
              <RouteChip
                key={routeKey(route.origin, route.destination)}
                route={route}
                favorite={false}
                onSelect={onSelect}
                onRemove={onRemoveRecent}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
