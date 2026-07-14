import { useCallback, useRef, useEffect, useState } from 'react'
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
}

export default function RoutePlanner({
  routing, sheetState, onSheetStateChange,
  activeTab, onTabChange, arrivalsContent,
}: RoutePlannerProps) {
  const handleSearch = useCallback(() => {
    routing.search()
    onSheetStateChange('expanded')
  }, [routing, onSheetStateChange])

  const handleCollapsedClick = useCallback(() => {
    onSheetStateChange('half')
  }, [onSheetStateChange])

  const detailRef = useRef<HTMLDivElement>(null)
  const [gpsLoading, setGpsLoading] = useState(false)

  useEffect(() => {
    if (routing.selectedItinerary && detailRef.current) {
      detailRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [routing.selectedIndex, routing.selectedItinerary])

  const handleGpsClick = useCallback(() => {
    if (!navigator.geolocation) return
    setGpsLoading(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        routing.setOriginFromCoords(pos.coords.latitude, pos.coords.longitude)
        setGpsLoading(false)
      },
      () => setGpsLoading(false),
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }, [routing])

  return (
    <BottomSheet state={sheetState} onStateChange={onSheetStateChange}>
      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'route' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('route')}
          type="button"
          data-id="tab-route-planner"
          data-active-tab={activeTab === 'route' ? 'Route Planner' : undefined}
        >
          Route Planner
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'arrivals' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('arrivals')}
          type="button"
          data-id="tab-station-arrivals"
          data-active-tab={activeTab === 'arrivals' ? 'Station Arrivals' : undefined}
        >
          Station Arrivals
        </button>
      </div>

      {activeTab === 'arrivals' ? (
        <div className={styles.arrivalsWrap}>{arrivalsContent}</div>
      ) : (
        <>
          {sheetState === 'collapsed' ? (
            <div className={styles.searchBar} onClick={handleCollapsedClick} data-id="open-route-planner">
              {routing.origin && routing.destination
                ? `${routing.origin.name} → ${routing.destination.name}`
                : 'Where to?'}
            </div>
          ) : (
            <>
              <div className={styles.inputs}>
                <div className={styles.inputsRow}>
                  <div className={styles.inputsFields}>
                    <LocationInput
                      label="From"
                      value={routing.origin}
                      onChange={routing.setOrigin}
                      placeholder="Origin"
                      onGpsClick={handleGpsClick}
                      gpsLoading={gpsLoading}
                    />
                    <LocationInput
                      label="To"
                      value={routing.destination}
                      onChange={routing.setDestination}
                      placeholder="Destination"
                    />
                  </div>
                  <button
                    className={styles.swapBtn}
                    onClick={routing.swapOriginDestination}
                    disabled={!routing.origin && !routing.destination}
                    type="button"
                    title="Swap origin and destination"
                    data-id="swap-origin-destination"
                  >
                    &#8693;
                  </button>
                </div>
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
                  data-id="search-routes"
                >
                  {routing.loading ? 'Searching...' : 'Search Routes'}
                </button>
              </div>

              <RouteResults
                results={routing.results}
                selectedIndex={routing.selectedIndex}
                onSelect={routing.setSelectedIndex}
                loading={routing.loading}
                error={routing.error}
                onRetry={handleSearch}
              />

              {routing.selectedItinerary && (
                <div ref={detailRef}>
                  <ItineraryDetail itinerary={routing.selectedItinerary} />
                </div>
              )}
            </>
          )}
        </>
      )}
    </BottomSheet>
  )
}
