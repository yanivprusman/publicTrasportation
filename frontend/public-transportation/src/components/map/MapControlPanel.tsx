import RouteMapView from './RouteMapView'
import type { Coordinates } from '../../types'
import styles from './MapControlPanel.module.css'

interface MapControlPanelProps {
  showRoutePanel: boolean
  setShowRoutePanel: (show: boolean) => void
  optimizedRouteShape: Coordinates[] | null
  handleShowRoutePanel: () => void
}

const MapControlPanel = ({
  showRoutePanel,
  setShowRoutePanel,
  optimizedRouteShape,
  handleShowRoutePanel
}: MapControlPanelProps) => {
  return (
    <>
      {(showRoutePanel || optimizedRouteShape) && (
        <div className={styles.panel}>
          <h3>Route Shape View</h3>
          <button onClick={() => setShowRoutePanel(false)} data-id="close-route-panel">Close</button>
          <div className={styles.panelMap}>
            <RouteMapView routeShape={optimizedRouteShape} />
          </div>
        </div>
      )}

      {!showRoutePanel && optimizedRouteShape && (
        <button onClick={handleShowRoutePanel} className={styles.showButton} data-id="show-route-panel">
          Show Route Panel
        </button>
      )}
    </>
  )
}

export default MapControlPanel
