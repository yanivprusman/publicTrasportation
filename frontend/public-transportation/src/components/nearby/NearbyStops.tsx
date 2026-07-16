import type { UseNearbyStopsReturn } from '../../hooks/useNearbyStops'
import type { NearbyStop } from '../../services/transport-api'
import { formatStopDistance, walkMinutes } from '../../utils/distance'
import styles from './NearbyStops.module.css'

const RADIUS_OPTIONS = [300, 600, 1000]

interface NearbyStopsProps {
  nearby: UseNearbyStopsReturn
  onSelect: (stop: NearbyStop) => void
}

export default function NearbyStops({ nearby, onSelect }: NearbyStopsProps) {
  return (
    <div className={styles.wrap}>
      <button
        className={styles.locateBtn}
        onClick={nearby.locate}
        disabled={nearby.loading}
        type="button"
        data-id="locate-nearby-stops"
      >
        <span className={styles.locateIcon} aria-hidden="true">📍</span>
        {nearby.loading ? 'Searching…' : nearby.located ? 'Update my location' : 'Find stops near me'}
      </button>

      <div className={styles.radiusRow}>
        <span className={styles.radiusLabel}>Within</span>
        {RADIUS_OPTIONS.map(r => (
          <button
            key={r}
            className={`${styles.radiusChip} ${r === nearby.radius ? styles.radiusChipActive : ''}`}
            onClick={() => nearby.changeRadius(r)}
            aria-pressed={r === nearby.radius}
            type="button"
            data-id="set-nearby-radius"
          >
            {r < 1000 ? `${r} m` : `${r / 1000} km`}
          </button>
        ))}
      </div>

      {nearby.error && (
        <div className={styles.error} role="alert" data-id="nearby-stops-error">
          {nearby.error}
        </div>
      )}

      {!nearby.located && !nearby.error && !nearby.loading && (
        <div className={styles.empty}>
          See every bus and train stop around you, sorted by walking distance.
          Tap a stop to open its live departure board.
        </div>
      )}

      {nearby.located && !nearby.error && !nearby.loading && nearby.stops.length === 0 && (
        <div className={styles.empty} data-id="nearby-stops-empty">
          No stops within {formatStopDistance(nearby.radius)} of you — try a wider radius.
        </div>
      )}

      {nearby.stops.length > 0 && !nearby.error && (
        <>
          <div className={styles.countLine}>
            {nearby.stops.length === 1 ? '1 stop' : `${nearby.stops.length} stops`} within {formatStopDistance(nearby.radius)} — numbered on the map
          </div>
          <div className={styles.list}>
            {nearby.stops.map((stop, i) => (
              <button
                key={stop.stopCode}
                className={styles.stopRow}
                onClick={() => onSelect(stop)}
                type="button"
                title="Show live arrivals for this stop"
                data-id="select-nearby-stop"
              >
                <span className={styles.stopIndex}>{i + 1}</span>
                <span className={styles.stopInfo}>
                  <span className={styles.stopName} dir="auto">{stop.stopName}</span>
                  <span className={styles.stopMeta}>Stop {stop.stopCode}</span>
                </span>
                <span className={styles.stopDistance}>
                  <span className={styles.distanceValue}>{formatStopDistance(stop.distanceMeters)}</span>
                  <span className={styles.walkTime}>~{walkMinutes(stop.distanceMeters)} min walk</span>
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
