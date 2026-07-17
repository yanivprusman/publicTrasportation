import RouteMapView from './RouteMapView'
import { useI18n } from '../../i18n'
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
  const { t } = useI18n()
  return (
    <>
      {(showRoutePanel || optimizedRouteShape) && (
        <div className={styles.panel}>
          <h3>{t('routePanel.title')}</h3>
          <button onClick={() => setShowRoutePanel(false)} data-id="close-route-panel">{t('routePanel.close')}</button>
          <div className={styles.panelMap}>
            <RouteMapView routeShape={optimizedRouteShape} />
          </div>
        </div>
      )}

      {!showRoutePanel && optimizedRouteShape && (
        <button onClick={handleShowRoutePanel} className={styles.showButton} data-id="show-route-panel">
          {t('routePanel.show')}
        </button>
      )}
    </>
  )
}

export default MapControlPanel
