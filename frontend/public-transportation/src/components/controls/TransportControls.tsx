import { useState, useEffect, useRef, useCallback } from 'react'
import { searchStops, type StopResult } from '../../services/transport-api'
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
  const [stationText, setStationText] = useState('')
  const [stationName, setStationName] = useState('')
  const [suggestions, setSuggestions] = useState<StopResult[]>([])
  const [showDropdown, setShowDropdown] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout>>(null)
  const inputRef = useRef<HTMLInputElement>(null)

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

  const doSearch = useCallback((query: string) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    if (!query.trim()) {
      setSuggestions([])
      return
    }
    timerRef.current = setTimeout(async () => {
      const results = await searchStops(query)
      setSuggestions(results)
      setShowDropdown(results.length > 0)
    }, 300)
  }, [])

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setStationText(val)
    doSearch(val)
  }

  const handleSelect = (stop: StopResult) => {
    setStationCode(stop.stopCode)
    setStationName(stop.stopName)
    setStationText('')
    setSuggestions([])
    setShowDropdown(false)
  }

  const handleClear = () => {
    setStationText('')
    setSuggestions([])
    inputRef.current?.focus()
  }

  const handleBlur = () => {
    setTimeout(() => setShowDropdown(false), 200)
  }

  return (
    <div className={styles.panel}>
      <div className={styles.row}>
        <span className={styles.label}>Station</span>
        <div className={styles.stationSearch}>
          <div className={styles.stationInputRow}>
            <input
              ref={inputRef}
              className={styles.stationInput}
              value={stationText}
              onChange={handleInput}
              onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
              onBlur={handleBlur}
              placeholder={stationName ? `${stationName} (${stationCode})` : 'Search station...'}
            />
            {stationText && (
              <button className={styles.clearBtn} onClick={handleClear} type="button">&times;</button>
            )}
          </div>
          {showDropdown && suggestions.length > 0 && (
            <ul className={styles.stationDropdown}>
              {suggestions.map(s => (
                <li
                  key={s.stopCode}
                  className={styles.stationSuggestion}
                  onMouseDown={() => handleSelect(s)}
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
        />
        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={showVehicleMarkers}
            onChange={(e) => setShowVehicleMarkers(e.target.checked)}
          />
          Vehicles
        </label>
      </div>
    </div>
  )
}

export default TransportControls
