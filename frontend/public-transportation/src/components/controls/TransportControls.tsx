import { useState, useEffect, useCallback } from 'react'
import { searchStops, type StopResult } from '../../services/transport-api'
import { useAutocomplete } from '../../hooks/useAutocomplete'
import { useI18n } from '../../i18n'
import styles from './TransportControls.module.css'

interface TransportControlsProps {
  stationCode: string
  setStationCode: (code: string) => void
  lastUpdated: Date | null
  lineFilter: string
  setLineFilter: (filter: string) => void
  showVehicleMarkers: boolean
  setShowVehicleMarkers: (show: boolean) => void
  onOpenBoard: () => void
  isFavorite: boolean
  onToggleFavorite: (name: string) => void
}

const searchFn = (query: string) => searchStops(query)

function TransportControls({
  stationCode,
  setStationCode,
  lastUpdated,
  lineFilter,
  setLineFilter,
  showVehicleMarkers,
  setShowVehicleMarkers,
  onOpenBoard,
  isFavorite,
  onToggleFavorite,
}: TransportControlsProps) {
  const { t } = useI18n()
  const [agoText, setAgoText] = useState('')
  const [stationName, setStationName] = useState('')
  const ac = useAutocomplete<StopResult>({ searchFn })

  // Resolve the station name whenever the code changes — the station can be set
  // from outside this panel (favorites bar, nearby list, a stop tapped on the
  // map), and a stale name would mislabel the favorite button and placeholder.
  useEffect(() => {
    if (!stationCode) {
      setStationName('')
      return
    }
    let cancelled = false
    searchStops(stationCode).then(results => {
      if (cancelled) return
      const match = results.find(s => s.stopCode === stationCode)
      setStationName(match ? match.stopName : '')
    })
    return () => { cancelled = true }
  }, [stationCode])

  useEffect(() => {
    if (!lastUpdated) return
    const tick = () => {
      const secs = Math.round((Date.now() - lastUpdated.getTime()) / 1000)
      setAgoText(secs < 5 ? t('arrivals.justNow') : t('arrivals.secondsAgo', { n: secs }))
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [lastUpdated])

  const selectStop = useCallback((stop: StopResult) => {
    setStationCode(stop.stopCode)
    setStationName(stop.stopName)
    ac.setText('')
    ac.handleSelect(stop)
  }, [setStationCode, ac])

  return (
    <div className={styles.panel}>
      <div className={styles.row}>
        <span className={styles.label}>{t('arrivals.station')}</span>
        <div className={styles.stationSearch}>
          <div className={styles.stationInputRow}>
            <input
              ref={ac.inputRef}
              className={styles.stationInput}
              data-id="station-search-input"
              value={ac.text}
              onChange={ac.handleInput}
              onKeyDown={(e) => ac.handleKeyDown(e, selectStop)}
              onFocus={ac.handleFocus}
              onBlur={ac.handleBlur}
              placeholder={stationName ? `${stationName} (${stationCode})` : t('arrivals.searchStation')}
            />
            {ac.text && (
              <button className={styles.clearBtn} onClick={ac.handleClear} type="button" data-id="clear-station-search">&times;</button>
            )}
          </div>
          {ac.showDropdown && ac.suggestions.length > 0 && (
            <ul className={styles.stationDropdown}>
              {ac.suggestions.map((s, i) => (
                <li
                  key={s.stopCode}
                  className={`${styles.stationSuggestion} ${i === ac.highlightIndex ? styles.highlighted : ''}`}
                  data-id="select-station-suggestion"
                  onMouseDown={() => selectStop(s)}
                >
                  <span className={styles.stopName}>{s.stopName}</span>
                  <span className={styles.stopCode}>{s.stopCode}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <button
          type="button"
          className={`${styles.favBtn} ${isFavorite ? styles.favBtnActive : ''}`}
          onClick={() => onToggleFavorite(stationName)}
          disabled={!stationCode}
          title={isFavorite ? t('fav.removeStation') : t('fav.addStation')}
          aria-label={isFavorite ? t('fav.removeStation') : t('fav.addStation')}
          aria-pressed={isFavorite}
          data-id="toggle-favorite-station"
        >
          {isFavorite ? '★' : '☆'}
        </button>
        {lastUpdated && <span className={styles.updatedAgo}>{t('arrivals.updated', { ago: agoText })}</span>}
      </div>
      <div className={styles.row}>
        <span className={styles.label}>{t('arrivals.filter')}</span>
        <input
          type="text"
          value={lineFilter}
          onChange={(e) => setLineFilter(e.target.value)}
          placeholder={t('arrivals.linePlaceholder')}
          className={styles.input}
          data-id="line-filter-input"
        />
        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={showVehicleMarkers}
            onChange={(e) => setShowVehicleMarkers(e.target.checked)}
            data-id="toggle-vehicle-markers"
          />
          {t('arrivals.vehicles')}
        </label>
      </div>
      <button
        type="button"
        className={styles.boardBtn}
        onClick={onOpenBoard}
        data-id="open-board-mode"
      >
        <span className={styles.boardBtnIcon}>▦</span>
        {t('board.open')}
      </button>
    </div>
  )
}

export default TransportControls
