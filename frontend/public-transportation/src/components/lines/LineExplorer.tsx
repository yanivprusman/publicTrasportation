import { useState } from 'react'
import type { UseLineExplorerReturn } from '../../hooks/useLineExplorer'
import type { UseFavoritesReturn } from '../../hooks/useFavorites'
import { getDirectionColor } from '../../utils/mode-colors'
import { formatHeadsign } from '../../utils/line-name'
import { useI18n } from '../../i18n'
import type { Coordinates } from '../../types'
import styles from './LineExplorer.module.css'

// Haversine length of a polyline, in km.
const shapeLengthKm = (points: Coordinates[]): number => {
  const R = 6371
  let total = 0
  for (let i = 1; i < points.length; i++) {
    const [lat1, lon1] = points[i - 1]
    const [lat2, lon2] = points[i]
    const dLat = (lat2 - lat1) * Math.PI / 180
    const dLon = (lon2 - lon1) * Math.PI / 180
    const a = Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2
    total += 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  }
  return total
}

interface LineExplorerProps {
  explorer: UseLineExplorerReturn
  favorites: UseFavoritesReturn
}

export default function LineExplorer({ explorer, favorites }: LineExplorerProps) {
  const { t } = useI18n()
  const [query, setQuery] = useState('')
  const isFavoriteLine = !!explorer.line && favorites.isLineFavorite(explorer.line)

  const submit = () => {
    if (query.trim()) explorer.explore(query)
  }

  const directions = explorer.data
    ? Object.entries(explorer.data.directions)
        .filter(([, points]) => Array.isArray(points) && points.length > 1)
        .sort(([a], [b]) => a.localeCompare(b))
    : []

  return (
    <div className={styles.wrap}>
      <div className={styles.searchRow}>
        <input
          className={styles.input}
          type="text"
          inputMode="numeric"
          value={query}
          onChange={e => setQuery(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') submit() }}
          placeholder={t('lines.placeholder')}
          data-id="line-explorer-input"
        />
        <button
          className={styles.showBtn}
          onClick={submit}
          disabled={!query.trim() || explorer.loading}
          type="button"
          data-id="explore-line"
        >
          {explorer.loading ? t('lines.loading') : t('lines.show')}
        </button>
      </div>

      {favorites.lines.length > 0 && (
        <div className={styles.favRow}>
          <span className={styles.favLabel}>★ {t('fav.lines')}</span>
          {favorites.lines.map(l => (
            <div key={l} className={`${styles.favChip} ${l === explorer.line ? styles.favChipActive : ''}`}>
              <button
                className={styles.favChipMain}
                onClick={() => { setQuery(l); explorer.explore(l) }}
                type="button"
                title={t('fav.showLine', { name: l })}
                data-id="select-favorite-line"
              >
                {l}
              </button>
              <button
                className={styles.favChipRemove}
                onClick={() => favorites.removeLine(l)}
                type="button"
                title={t('fav.removeNamed', { name: l })}
                aria-label={t('fav.removeNamed', { name: l })}
                data-id="remove-favorite-line"
              >
                &times;
              </button>
            </div>
          ))}
        </div>
      )}

      {explorer.recentLines.length > 0 && (
        <div className={styles.recentRow}>
          {explorer.recentLines.map(l => (
            <button
              key={l}
              className={`${styles.recentChip} ${l === explorer.line ? styles.recentChipActive : ''}`}
              onClick={() => { setQuery(l); explorer.explore(l) }}
              type="button"
              data-id="select-recent-line"
            >
              {l}
            </button>
          ))}
        </div>
      )}

      {explorer.error && (
        <div className={styles.error} role="alert" data-id="line-explorer-error">
          {explorer.error}
        </div>
      )}

      {explorer.data && !explorer.error && (
        <>
          <div className={styles.lineHeader}>
            <span className={styles.lineBadge} data-id="explored-line-badge">{t('lines.line', { n: explorer.line ?? '' })}</span>
            <span className={styles.lineSub}>
              {directions.length === 1 ? t('lines.oneDirection') : t('lines.directions', { n: directions.length })}
            </span>
            <button
              className={`${styles.lineFavBtn} ${isFavoriteLine ? styles.lineFavBtnActive : ''}`}
              onClick={() => explorer.line && favorites.toggleLine(explorer.line)}
              type="button"
              title={isFavoriteLine ? t('fav.removeLine') : t('fav.addLine')}
              aria-label={isFavoriteLine ? t('fav.removeLine') : t('fav.addLine')}
              aria-pressed={isFavoriteLine}
              data-id="toggle-favorite-line"
            >
              {isFavoriteLine ? '★' : '☆'}
            </button>
            <button
              className={styles.clearBtn}
              onClick={explorer.clear}
              type="button"
              title={t('lines.clearTitle')}
              data-id="clear-explored-line"
            >
              {t('lines.clear')}
            </button>
          </div>

          <div className={styles.directions}>
            {directions.map(([direction, points]) => {
              const hidden = !!explorer.hiddenDirections[direction]
              const color = getDirectionColor(direction)
              return (
                <div
                  key={direction}
                  className={`${styles.directionCard} ${hidden ? styles.directionHidden : ''}`}
                  data-id="line-direction-card"
                >
                  <span className={styles.colorDot} style={{ background: color }} />
                  <div className={styles.directionInfo}>
                    <div className={styles.headsign} dir="auto">
                      {formatHeadsign(explorer.data!.headsigns[direction], direction)}
                    </div>
                    <div className={styles.meta}>{t('lines.km', { n: shapeLengthKm(points).toFixed(1) })}</div>
                  </div>
                  <button
                    className={styles.dirBtn}
                    onClick={() => explorer.toggleDirection(direction)}
                    type="button"
                    aria-pressed={!hidden}
                    title={hidden ? t('lines.showDirTitle') : t('lines.hideDirTitle')}
                    data-id="toggle-line-direction"
                  >
                    {hidden ? t('lines.showDir') : t('lines.hideDir')}
                  </button>
                  <button
                    className={styles.dirBtn}
                    onClick={() => explorer.focusDirection(direction)}
                    disabled={hidden}
                    type="button"
                    title={t('lines.zoomTitle')}
                    data-id="focus-line-direction"
                  >
                    {t('lines.zoom')}
                  </button>
                </div>
              )
            })}
          </div>
        </>
      )}

      {!explorer.data && !explorer.error && !explorer.loading && (
        <div className={styles.empty}>
          {t('lines.empty')}
        </div>
      )}
    </div>
  )
}
