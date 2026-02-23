import { useState, useEffect } from 'react'
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

  return (
    <div className={styles.panel}>
      <div className={styles.row}>
        <span className={styles.label}>Station</span>
        <select
          value={stationCode}
          onChange={(e) => setStationCode(e.target.value)}
          className={styles.select}
        >
          <option value="26472">מסוף עמידר (26472)</option>
          <option value="20001">ת. רכבת תל אביב סבידור (20001)</option>
          <option value="21256">ת. מרכזית ת&quot;א קומה 6 (21256)</option>
          <option value="23009">שוק הכרמל/אלנבי (23009)</option>
          <option value="21199">קניון רמת אביב (21199)</option>
          <option value="20304">פנחס רוזן/קהילת יאסי (20304)</option>
          <option value="20832">Station 20832</option>
        </select>
        {lastUpdated && <span className={styles.updatedAgo}>Updated {agoText}</span>}
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
