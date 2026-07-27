'use client'

import { useState } from 'react'
import { MAP_STYLES, type MapStyle } from '../../hooks/useMapStyle'
import { useI18n } from '../../i18n'
import type { TranslationKey } from '../../i18n/translations'
import styles from './MapStyleControls.module.css'

const STYLE_LABELS: Record<MapStyle, TranslationKey> = {
  dark: 'map.styleDark',
  light: 'map.styleLight',
  satellite: 'map.styleSatellite',
}

const STYLE_ICONS: Record<MapStyle, string> = {
  dark: '🌑',
  light: '🌕',
  satellite: '🛰️',
}

interface MapStyleControlsProps {
  mapStyle: MapStyle
  onStyleChange: (style: MapStyle) => void
  following: boolean
  onToggleFollow: () => void
  locating: boolean
}

/**
 * Floating map controls: basemap style (dark/light/satellite) and the
 * follow-my-location toggle. Same pair the Android map offers.
 */
export default function MapStyleControls({
  mapStyle, onStyleChange, following, onToggleFollow, locating,
}: MapStyleControlsProps) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)

  return (
    <div className={styles.wrap}>
      <button
        type="button"
        className={`${styles.btn} ${following ? styles.btnActive : ''}`}
        onClick={onToggleFollow}
        title={following ? t('map.unfollow') : t('map.follow')}
        aria-label={following ? t('map.unfollow') : t('map.follow')}
        aria-pressed={following}
        data-id="toggle-follow-location"
      >
        <span className={locating ? styles.spinner : ''} aria-hidden="true">
          {locating ? '' : following ? '◉' : '◎'}
        </span>
      </button>

      <div className={styles.layersWrap}>
        <button
          type="button"
          className={`${styles.btn} ${open ? styles.btnActive : ''}`}
          onClick={() => setOpen(o => !o)}
          title={t('map.style')}
          aria-label={t('map.style')}
          aria-expanded={open}
          data-id="toggle-map-style-menu"
        >
          <span aria-hidden="true">🗺️</span>
        </button>
        {open && (
          <div className={styles.menu} role="group" aria-label={t('map.style')}>
            {MAP_STYLES.map(style => (
              <button
                key={style}
                type="button"
                className={`${styles.menuItem} ${mapStyle === style ? styles.menuItemActive : ''}`}
                onClick={() => { onStyleChange(style); setOpen(false) }}
                aria-pressed={mapStyle === style}
                data-id={`set-map-style-${style}`}
              >
                <span aria-hidden="true">{STYLE_ICONS[style]}</span>
                {t(STYLE_LABELS[style])}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
