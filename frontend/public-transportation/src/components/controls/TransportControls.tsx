import { useState, useEffect, useCallback } from 'react'
import { searchStops, type StopResult } from '../../services/transport-api'
import { useAutocomplete } from '../../hooks/useAutocomplete'
import styles from './TransportControls.module.css'

interface TransportControlsProps {
  stationCode: string
  setStationCode: (code: string) => void
  lastUpdated: Date | null
  lineFilter: string
  setLineFilter: (filter: string) => void
  showVehicleMarkers: boolean
  setShowVehicleMarkers: (show: boolean) => void
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
}: TransportControlsProps) {
  const [agoText, setAgoText] = useState('')
  const [stationName, setStationName] = useState('')
  const ac = useAutocomplete<StopResult>({ searchFn })

  // Resolve initial station name on mount
  useEffect(() => {
    if (stationCode && !stationName) {
      searchStops(stationCode).then(results => {
        const match = results.find(s => s.stopCode === stationCode)
        if (match) setStationName(match.stopName)
      })
    }
  }, [])

  useEffect(() => {
    if (!lastUpdated) return
    const tick = () => {
      const secs = Math.round((Date.now() - lastUpdated.getTime()) / 1000)
      setAgoText(secs < 5 ? 'just now' : `${secs}s ago`)
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
        <span className={styles.label}>Station</span>
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
              placeholder={stationName ? `${stationName} (${stationCode})` : 'Search station...'}
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
        {lastUpdated && <span className={styles.updatedAgo}>Updated {agoText}</span>}
      </div>
      <div className={styles.row}>
        <span className={styles.label}>Filter</span>
        <input
          type="text"
          value={lineFilter}
          onChange={(e) => setLineFilter(e.target.value)}
          placeholder="Line #"
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
          Vehicles
        </label>
      </div>
    </div>
  )
}

export default TransportControls
