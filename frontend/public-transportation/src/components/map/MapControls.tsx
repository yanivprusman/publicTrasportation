import { useI18n } from '../../i18n'
import styles from './MapControls.module.css'

interface MapControlsProps {
  searchQuery: string
  setSearchQuery: (q: string) => void
  handleSearch: () => void
  handleKeyPress: (e: React.KeyboardEvent) => void
  handleSetStartPoint: () => void
  handleSetDestinationPoint: () => void
  searchError: string | null
  positionAddress: string
  destinationAddress: string
}

const MapControls = ({
  searchQuery,
  setSearchQuery,
  handleKeyPress,
  handleSetStartPoint,
  handleSetDestinationPoint,
  searchError,
  positionAddress,
  destinationAddress
}: MapControlsProps) => {
  const { t, tm } = useI18n()
  return (
    <div className={styles.container}>
      <div>
        <input
          type="text"
          placeholder={t('mapCtl.searchPlaceholder')}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          onKeyPress={handleKeyPress}
          className={styles.searchInput}
          data-id="map-location-search"
        />
        <div className={styles.buttonRow}>
          <button onClick={handleSetStartPoint} className={styles.startButton} data-id="set-starting-point">
            {t('mapCtl.setStart')}
          </button>
          <button onClick={handleSetDestinationPoint} className={styles.destButton} data-id="set-destination-point">
            {t('mapCtl.setDest')}
          </button>
        </div>
      </div>
      {searchError && <p className={styles.error}>{tm(searchError)}</p>}
      <div className={styles.infoBox}>
        <p><strong>{t('mapCtl.start')}</strong> {positionAddress || t('mapCtl.noStart')}</p>
      </div>
      <div className={styles.infoBoxDest}>
        <p><strong>{t('popup.destination')}</strong> {destinationAddress || t('mapCtl.noDest')}</p>
      </div>
    </div>
  )
}

export default MapControls
