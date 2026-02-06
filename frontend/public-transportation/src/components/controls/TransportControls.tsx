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
    <div className="controls-panel">
      <div className={styles.stationGroup}>
        <label className={styles.label}>Station:</label>
        <select
          value={stationCode}
          onChange={(e) => setStationCode(e.target.value)}
          className={styles.select}
        >
          <option value="26472">מסוף עמידר (26472)</option>
          <option value="20832">Station 20832</option>
        </select>
        <button onClick={fetchStationData} className={styles.refreshButton}>
          Refresh Station Data
        </button>

        <label className={styles.vehicleLabel}>
          <input
            type="checkbox"
            checked={showVehicleMarkers}
            onChange={(e) => setShowVehicleMarkers(e.target.checked)}
          />
          Show Vehicles
        </label>
      </div>
      <div>
        <label className={styles.label}>Line:</label>
        <input
          type="text"
          value={lineNumber}
          onChange={(e) => setLineNumber(e.target.value)}
          className={styles.lineInput}
        />
        <label className={styles.directionLabel}>Direction:</label>
        <select
          value={routeDirection}
          onChange={(e) => setRouteDirection(e.target.value)}
          className={styles.select}
        >
          <option value="0">Outbound</option>
          <option value="1">Inbound</option>
        </select>
        <button onClick={fetchLineShape} className={styles.showRouteButton}>
          Show Route
        </button>

        <button onClick={handleFindRoute} className={styles.findRouteButton}>
          Find Route
        </button>
      </div>
    </div>
  )
}

export default TransportControls
