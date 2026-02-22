import { useCallback } from 'react'
import type { UseRoutingReturn } from '../../hooks/useRouting'
import BottomSheet, { type SheetState } from './BottomSheet'
import LocationInput from './LocationInput'
import TimePicker from './TimePicker'
import RouteResults from './RouteResults'
import ItineraryDetail from './ItineraryDetail'
import styles from './RoutePlanner.module.css'

interface RoutePlannerProps {
  routing: UseRoutingReturn
  sheetState: SheetState
  onSheetStateChange: (s: SheetState) => void
  activeTab: 'route' | 'arrivals'
  onTabChange: (tab: 'route' | 'arrivals') => void
  arrivalsContent: React.ReactNode
  lineNumber: string
  setLineNumber: (num: string) => void
  routeDirection: string
  setRouteDirection: (dir: string) => void
  onFetchLineShape: () => void
}

export default function RoutePlanner({
  routing, sheetState, onSheetStateChange,
  activeTab, onTabChange, arrivalsContent,
  lineNumber, setLineNumber, routeDirection, setRouteDirection, onFetchLineShape
}: RoutePlannerProps) {
  const handleSearch = useCallback(() => {
    routing.search()
    onSheetStateChange('expanded')
  }, [routing, onSheetStateChange])

  const handleCollapsedClick = useCallback(() => {
    onSheetStateChange('half')
  }, [onSheetStateChange])

  return (
    <BottomSheet state={sheetState} onStateChange={onSheetStateChange}>
      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'route' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('route')}
          type="button"
        >
          Route Planner
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'arrivals' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('arrivals')}
          type="button"
        >
          Station Arrivals
        </button>
      </div>

      {activeTab === 'arrivals' ? (
        <div className={styles.arrivalsWrap}>{arrivalsContent}</div>
      ) : (
        <>
          {sheetState === 'collapsed' ? (
            <div className={styles.searchBar} onClick={handleCollapsedClick}>
              Where to?
            </div>
          ) : (
            <>
              <div className={styles.inputs}>
                <LocationInput
                  label="From"
                  value={routing.origin}
                  onChange={routing.setOrigin}
                  placeholder="Origin"
                />
                <LocationInput
                  label="To"
                  value={routing.destination}
                  onChange={routing.setDestination}
                  placeholder="Destination"
                />
                <TimePicker
                  departureTime={routing.departureTime}
                  setDepartureTime={routing.setDepartureTime}
                  arriveBy={routing.arriveBy}
                  setArriveBy={routing.setArriveBy}
                />
                <button
                  className={styles.searchBtn}
                  onClick={handleSearch}
                  disabled={!routing.origin || !routing.destination || routing.loading}
                  type="button"
                >
                  {routing.loading ? 'Searching...' : 'Search Routes'}
                </button>
              </div>

              <div className={styles.lineShapeSection}>
                <div className={styles.lineShapeLabel}>Line Shape</div>
                <div className={styles.lineShapeRow}>
                  <input
                    type="text"
                    value={lineNumber}
                    onChange={(e) => setLineNumber(e.target.value)}
                    placeholder="Line #"
                    className={styles.lineShapeInput}
                  />
                  <select
                    value={routeDirection}
                    onChange={(e) => setRouteDirection(e.target.value)}
                    className={styles.lineShapeSelect}
                  >
                    <option value="0">Outbound</option>
                    <option value="1">Inbound</option>
                  </select>
                  <button
                    onClick={onFetchLineShape}
                    className={styles.lineShapeBtn}
                    type="button"
                  >
                    Show Route
                  </button>
                </div>
              </div>

              <RouteResults
                results={routing.results}
                selectedIndex={routing.selectedIndex}
                onSelect={routing.setSelectedIndex}
                loading={routing.loading}
                error={routing.error}
              />

              {routing.selectedItinerary && (
                <ItineraryDetail itinerary={routing.selectedItinerary} />
              )}
            </>
          )}
        </>
      )}
    </BottomSheet>
  )
}
