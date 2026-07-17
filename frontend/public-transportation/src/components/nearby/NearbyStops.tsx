import type { UseNearbyStopsReturn } from '../../hooks/useNearbyStops'
import type { NearbyStop } from '../../services/transport-api'
import { formatStopDistance, walkMinutes } from '../../utils/distance'
import { useI18n } from '../../i18n'
import styles from './NearbyStops.module.css'

const RADIUS_OPTIONS = [300, 600, 1000]

interface NearbyStopsProps {
  nearby: UseNearbyStopsReturn
  onSelect: (stop: NearbyStop) => void
}

export default function NearbyStops({ nearby, onSelect }: NearbyStopsProps) {
  const { t, tm } = useI18n()
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
        {nearby.loading ? t('nearby.searching') : nearby.located ? t('nearby.update') : t('nearby.find')}
      </button>

      <div className={styles.radiusRow}>
        <span className={styles.radiusLabel}>{t('nearby.within')}</span>
        {RADIUS_OPTIONS.map(r => (
          <button
            key={r}
            className={`${styles.radiusChip} ${r === nearby.radius ? styles.radiusChipActive : ''}`}
            onClick={() => nearby.changeRadius(r)}
            aria-pressed={r === nearby.radius}
            type="button"
            data-id="set-nearby-radius"
          >
            {r < 1000 ? t('dist.m', { n: r }) : t('dist.km', { n: r / 1000 })}
          </button>
        ))}
      </div>

      {nearby.error && (
        <div className={styles.error} role="alert" data-id="nearby-stops-error">
          {tm(nearby.error)}
        </div>
      )}

      {!nearby.located && !nearby.error && !nearby.loading && (
        <div className={styles.empty}>
          {t('nearby.empty')}
        </div>
      )}

      {nearby.located && !nearby.error && !nearby.loading && nearby.stops.length === 0 && (
        <div className={styles.empty} data-id="nearby-stops-empty">
          {t('nearby.none', { d: formatStopDistance(nearby.radius) })}
        </div>
      )}

      {nearby.stops.length > 0 && !nearby.error && (
        <>
          <div className={styles.countLine}>
            {nearby.stops.length === 1
              ? t('nearby.countOne', { d: formatStopDistance(nearby.radius) })
              : t('nearby.countMany', { n: nearby.stops.length, d: formatStopDistance(nearby.radius) })}
          </div>
          <div className={styles.list}>
            {nearby.stops.map((stop, i) => (
              <button
                key={stop.stopCode}
                className={styles.stopRow}
                onClick={() => onSelect(stop)}
                type="button"
                title={t('nearby.showArrivals')}
                data-id="select-nearby-stop"
              >
                <span className={styles.stopIndex}>{i + 1}</span>
                <span className={styles.stopInfo}>
                  <span className={styles.stopName} dir="auto">{stop.stopName}</span>
                  <span className={styles.stopMeta}>{t('nearby.stopCode', { code: stop.stopCode })}</span>
                </span>
                <span className={styles.stopDistance}>
                  <span className={styles.distanceValue}>{formatStopDistance(stop.distanceMeters)}</span>
                  <span className={styles.walkTime}>{t('nearby.walkTime', { n: walkMinutes(stop.distanceMeters) })}</span>
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
