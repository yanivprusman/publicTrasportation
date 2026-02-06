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
  return (
    <div className={styles.container}>
      <div>
        <input
          type="text"
          placeholder="Search for a location"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          onKeyPress={handleKeyPress}
          className={styles.searchInput}
        />
        <div className={styles.buttonRow}>
          <button onClick={handleSetStartPoint} className={styles.startButton}>
            Set Starting Point
          </button>
          <button onClick={handleSetDestinationPoint} className={styles.destButton}>
            Set Destination
          </button>
        </div>
      </div>
      {searchError && <p className={styles.error}>{searchError}</p>}
      <div className={styles.infoBox}>
        <p><strong>Start:</strong> {positionAddress || 'Loading...'}</p>
      </div>
      <div className={styles.infoBoxDest}>
        <p><strong>Destination:</strong> {destinationAddress || 'No destination set'}</p>
      </div>
    </div>
  )
}

export default MapControls
