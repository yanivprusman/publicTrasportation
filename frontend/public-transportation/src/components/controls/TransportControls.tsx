import styles from './TransportControls.module.css'

interface TransportControlsProps {
  stationCode: string
  setStationCode: (code: string) => void
  fetchStationData: () => void
  lineNumber: string
  setLineNumber: (num: string) => void
  routeDirection: string
  setRouteDirection: (dir: string) => void
  fetchLineShape: () => void
  showVehicleMarkers: boolean
  setShowVehicleMarkers: (show: boolean) => void
  handleFindRoute: () => void
}

function TransportControls({
  stationCode,
  setStationCode,
  fetchStationData,
  lineNumber,
  setLineNumber,
  routeDirection,
  setRouteDirection,
  fetchLineShape,
  showVehicleMarkers,
  setShowVehicleMarkers,
  handleFindRoute
}: TransportControlsProps) {
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
          <option value="20832">Station 20832</option>
        </select>
        <button onClick={fetchStationData} className={styles.btn}>Refresh</button>
        <label className={styles.checkbox}>
          <input
            type="checkbox"
            checked={showVehicleMarkers}
            onChange={(e) => setShowVehicleMarkers(e.target.checked)}
          />
          Vehicles
        </label>
      </div>

      <div className={styles.row}>
        <span className={styles.label}>Line</span>
        <input
          type="text"
          value={lineNumber}
          onChange={(e) => setLineNumber(e.target.value)}
          className={styles.input}
        />
        <select
          value={routeDirection}
          onChange={(e) => setRouteDirection(e.target.value)}
          className={styles.select}
          style={{ flex: 'none', width: 'auto' }}
        >
          <option value="0">Outbound</option>
          <option value="1">Inbound</option>
        </select>
        <button onClick={fetchLineShape} className={styles.btn}>Show Route</button>
        <button onClick={handleFindRoute} className={styles.btnPrimary}>Find Route</button>
      </div>
    </div>
  )
}

export default TransportControls
