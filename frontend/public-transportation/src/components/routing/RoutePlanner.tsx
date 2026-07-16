import { useCallback, useRef, useEffect, useState } from 'react'
import type { UseRoutingReturn } from '../../hooks/useRouting'
import BottomSheet, { type SheetState } from './BottomSheet'
import LocationInput from './LocationInput'
import TimePicker from './TimePicker'
import RouteResults from './RouteResults'
import ItineraryDetail from './ItineraryDetail'
import SavedRoutesBar from './SavedRoutesBar'
import { useSavedRoutes, type SavedRoute } from '../../hooks/useSavedRoutes'
import { useTheme } from '../../hooks/useTheme'
import styles from './RoutePlanner.module.css'

interface RoutePlannerProps {
  routing: UseRoutingReturn
  sheetState: SheetState
  onSheetStateChange: (s: SheetState) => void
  activeTab: 'route' | 'arrivals' | 'lines'
  onTabChange: (tab: 'route' | 'arrivals' | 'lines') => void
  arrivalsContent: React.ReactNode
  linesContent: React.ReactNode
}

export default function RoutePlanner({
  routing, sheetState, onSheetStateChange,
  activeTab, onTabChange, arrivalsContent, linesContent,
}: RoutePlannerProps) {
  const saved = useSavedRoutes()
  const { theme, toggleTheme } = useTheme()

  const handleSearch = useCallback(() => {
    if (routing.origin && routing.destination) {
      saved.recordSearch(routing.origin, routing.destination)
    }
    routing.search()
    onSheetStateChange('expanded')
  }, [routing, onSheetStateChange, saved])

  const handleSelectSaved = useCallback((route: SavedRoute) => {
    saved.recordSearch(route.origin, route.destination)
    routing.initRoute(route.origin, route.destination)
    onSheetStateChange('expanded')
  }, [routing, onSheetStateChange, saved])

  const canFavorite = !!(routing.origin && routing.destination)
  const isFavorited = canFavorite && saved.isFavorite(routing.origin!, routing.destination!)

  const handleToggleFavorite = useCallback(() => {
    if (routing.origin && routing.destination) {
      saved.toggleFavorite(routing.origin, routing.destination)
    }
  }, [routing.origin, routing.destination, saved])

  const handleCollapsedClick = useCallback(() => {
    onSheetStateChange('half')
  }, [onSheetStateChange])

  const detailRef = useRef<HTMLDivElement>(null)
  const [gpsLoading, setGpsLoading] = useState(false)
  const [gpsError, setGpsError] = useState<string | null>(null)

  useEffect(() => {
    if (routing.selectedItinerary && detailRef.current) {
      detailRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [routing.selectedIndex, routing.selectedItinerary])

  // Once an origin is set (by GPS or typed), the previous GPS failure no longer
  // applies — drop the stale message so it can't contradict a valid origin.
  useEffect(() => {
    if (routing.origin) setGpsError(null)
  }, [routing.origin])

  const handleGpsClick = useCallback(() => {
    // Geolocation is the primary way to set the origin on mobile; a denied
    // permission or timeout used to just stop the spinner with no feedback,
    // leaving the user unsure whether it was working. Surface a clear message.
    if (!navigator.geolocation) {
      setGpsError('Location is not available on this device — type an origin instead.')
      return
    }
    setGpsLoading(true)
    setGpsError(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        routing.setOriginFromCoords(pos.coords.latitude, pos.coords.longitude)
        setGpsLoading(false)
      },
      (err) => {
        setGpsLoading(false)
        setGpsError(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied — enable it or type an origin.'
            : 'Could not get your location — try again or type an origin.'
        )
      },
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
        <button
          className={`${styles.tab} ${activeTab === 'lines' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('lines')}
          type="button"
          data-id="tab-lines"
          data-active-tab={activeTab === 'lines' ? 'Lines' : undefined}
        >
          Lines
        </button>
        <button
          className={styles.themeBtn}
          onClick={toggleTheme}
          type="button"
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          data-id="toggle-theme"
        >
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
      </div>

      {activeTab === 'arrivals' ? (
        <div className={styles.arrivalsWrap}>{arrivalsContent}</div>
      ) : activeTab === 'lines' ? (
        <div className={styles.arrivalsWrap}>{linesContent}</div>
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
              <SavedRoutesBar
                favorites={saved.favorites}
                recents={saved.recents}
                onSelect={handleSelectSaved}
                onRemoveFavorite={saved.removeFavorite}
                onRemoveRecent={saved.removeRecent}
              />
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
                {gpsError && (
                  <div className={styles.gpsError} role="alert" data-id="gps-error">
                    {gpsError}
                  </div>
                )}
                <TimePicker
                  departureTime={routing.departureTime}
                  setDepartureTime={routing.setDepartureTime}
                  arriveBy={routing.arriveBy}
                  setArriveBy={routing.setArriveBy}
                />
                <div className={styles.searchRow}>
                  <button
                    className={styles.searchBtn}
                    onClick={handleSearch}
                    disabled={!routing.origin || !routing.destination || routing.loading}
                    type="button"
                    data-id="search-routes"
                  >
                    {routing.loading ? 'Searching...' : 'Search Routes'}
                  </button>
                  <button
                    className={`${styles.favBtn} ${isFavorited ? styles.favBtnActive : ''}`}
                    onClick={handleToggleFavorite}
                    disabled={!canFavorite}
                    type="button"
                    title={isFavorited ? 'Remove from saved routes' : 'Save this route'}
                    aria-pressed={isFavorited}
                    data-id="toggle-favorite-route"
                  >
                    {isFavorited ? '★' : '☆'}
                  </button>
                </div>
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
                  <ItineraryDetail
                    itinerary={routing.selectedItinerary}
                    trip={routing.origin && routing.destination ? {
                      origin: routing.origin,
                      destination: routing.destination,
                      departureTime: routing.departureTime,
                      arriveBy: routing.arriveBy,
                    } : null}
                  />
                </div>
              )}
            </>
          )}
        </>
      )}
    </BottomSheet>
  )
}
