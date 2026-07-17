import { useCallback, useRef, useEffect, useState } from 'react'
import type { UseRoutingReturn } from '../../hooks/useRouting'
import BottomSheet, { type SheetState } from './BottomSheet'
import LocationInput from './LocationInput'
import TimePicker from './TimePicker'
import RouteOptions from './RouteOptions'
import RouteResults from './RouteResults'
import TravelModeStrip from './TravelModeStrip'
import DirectRouteCard from './DirectRouteCard'
import ItineraryDetail from './ItineraryDetail'
import SavedRoutesBar from './SavedRoutesBar'
import SavedPlacesBar from './SavedPlacesBar'
import { useSavedRoutes, type SavedRoute } from '../../hooks/useSavedRoutes'
import { useSavedPlaces } from '../../hooks/useSavedPlaces'
import { useTheme } from '../../hooks/useTheme'
import { useI18n } from '../../i18n'
import type { LiveBusState } from '../../hooks/useLiveBus'
import type { Coordinates, GeocodeSuggestion } from '../../types'
import styles from './RoutePlanner.module.css'

interface RoutePlannerProps {
  routing: UseRoutingReturn
  sheetState: SheetState
  onSheetStateChange: (s: SheetState) => void
  activeTab: 'route' | 'nearby' | 'arrivals' | 'lines'
  onTabChange: (tab: 'route' | 'nearby' | 'arrivals' | 'lines') => void
  arrivalsContent: React.ReactNode
  linesContent: React.ReactNode
  nearbyContent: React.ReactNode
  liveBus: LiveBusState
  onShowLiveBusOnMap: (position: Coordinates) => void
}

export default function RoutePlanner({
  routing, sheetState, onSheetStateChange,
  activeTab, onTabChange, arrivalsContent, linesContent, nearbyContent,
  liveBus, onShowLiveBusOnMap,
}: RoutePlannerProps) {
  const saved = useSavedRoutes()
  const places = useSavedPlaces()
  const { theme, toggleTheme } = useTheme()
  const { lang, t, tm, toggleLanguage } = useI18n()

  const handleUseAsOrigin = useCallback((place: GeocodeSuggestion) => {
    routing.setOrigin(place)
  }, [routing])

  const handleUseAsDestination = useCallback((place: GeocodeSuggestion) => {
    routing.setDestination(place)
  }, [routing])

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
      setGpsError('gps.unavailable')
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
        // Stored as translation keys so a language switch retranslates them.
        setGpsError(err.code === err.PERMISSION_DENIED ? 'gps.denied' : 'gps.failed')
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
          data-active-tab={activeTab === 'route' ? t('tabs.routes') : undefined}
        >
          {t('tabs.routes')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'nearby' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('nearby')}
          type="button"
          data-id="tab-nearby"
          data-active-tab={activeTab === 'nearby' ? t('tabs.nearby') : undefined}
        >
          {t('tabs.nearby')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'arrivals' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('arrivals')}
          type="button"
          data-id="tab-station-arrivals"
          data-active-tab={activeTab === 'arrivals' ? t('tabs.arrivals') : undefined}
        >
          {t('tabs.arrivals')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'lines' ? styles.activeTab : ''}`}
          onClick={() => onTabChange('lines')}
          type="button"
          data-id="tab-lines"
          data-active-tab={activeTab === 'lines' ? t('tabs.lines') : undefined}
        >
          {t('tabs.lines')}
        </button>
        <button
          className={styles.themeBtn}
          onClick={toggleLanguage}
          type="button"
          title={t('lang.toggleTitle')}
          aria-label={t('lang.toggleTitle')}
          data-id="toggle-language"
        >
          {lang === 'he' ? 'EN' : 'עב'}
        </button>
        <button
          className={styles.themeBtn}
          onClick={toggleTheme}
          type="button"
          title={theme === 'dark' ? t('theme.toLight') : t('theme.toDark')}
          aria-label={theme === 'dark' ? t('theme.toLight') : t('theme.toDark')}
          data-id="toggle-theme"
        >
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
      </div>

      {activeTab === 'arrivals' ? (
        <div className={styles.arrivalsWrap}>{arrivalsContent}</div>
      ) : activeTab === 'lines' ? (
        <div className={styles.arrivalsWrap}>{linesContent}</div>
      ) : activeTab === 'nearby' ? (
        <div className={styles.arrivalsWrap}>{nearbyContent}</div>
      ) : (
        <>
          {sheetState === 'collapsed' ? (
            <div className={styles.searchBar} onClick={handleCollapsedClick} data-id="open-route-planner">
              {routing.origin && routing.destination
                ? `${routing.origin.name} → ${routing.destination.name}`
                : t('planner.whereTo')}
            </div>
          ) : (
            <>
              <SavedPlacesBar
                places={places.places}
                onUseAsOrigin={handleUseAsOrigin}
                onUseAsDestination={handleUseAsDestination}
                onRemove={places.remove}
              />
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
                      label={t('planner.from')}
                      field="from"
                      value={routing.origin}
                      onChange={routing.setOrigin}
                      placeholder={t('planner.originPlaceholder')}
                      onGpsClick={handleGpsClick}
                      gpsLoading={gpsLoading}
                      isSaved={!!routing.origin && places.isSaved(routing.origin)}
                      onToggleSave={routing.origin ? () => places.toggleSave(routing.origin!) : undefined}
                    />
                    <LocationInput
                      label={t('planner.to')}
                      field="to"
                      value={routing.destination}
                      onChange={routing.setDestination}
                      placeholder={t('planner.destinationPlaceholder')}
                      isSaved={!!routing.destination && places.isSaved(routing.destination)}
                      onToggleSave={routing.destination ? () => places.toggleSave(routing.destination!) : undefined}
                    />
                  </div>
                  <button
                    className={styles.swapBtn}
                    onClick={routing.swapOriginDestination}
                    disabled={!routing.origin && !routing.destination}
                    type="button"
                    title={t('planner.swapTitle')}
                    data-id="swap-origin-destination"
                  >
                    &#8693;
                  </button>
                </div>
                {gpsError && (
                  <div className={styles.gpsError} role="alert" data-id="gps-error">
                    {tm(gpsError)}
                  </div>
                )}
                <TimePicker
                  departureTime={routing.departureTime}
                  setDepartureTime={routing.setDepartureTime}
                  arriveBy={routing.arriveBy}
                  setArriveBy={routing.setArriveBy}
                />
                <RouteOptions routeOptions={routing.routeOptions} />
                <div className={styles.searchRow}>
                  <button
                    className={styles.searchBtn}
                    onClick={handleSearch}
                    disabled={!routing.origin || !routing.destination || routing.loading}
                    type="button"
                    data-id="search-routes"
                  >
                    {routing.loading ? t('planner.searching') : t('planner.search')}
                  </button>
                  <button
                    className={`${styles.favBtn} ${isFavorited ? styles.favBtnActive : ''}`}
                    onClick={handleToggleFavorite}
                    disabled={!canFavorite}
                    type="button"
                    title={isFavorited ? t('planner.removeSavedRoute') : t('planner.saveRoute')}
                    aria-pressed={isFavorited}
                    data-id="toggle-favorite-route"
                  >
                    {isFavorited ? '★' : '☆'}
                  </button>
                </div>
              </div>

              {routing.alternatives.length > 0 && (
                <TravelModeStrip
                  transitDuration={routing.results?.itineraries.length
                    ? Math.min(...routing.results.itineraries.map(itin => itin.duration))
                    : null}
                  alternatives={routing.alternatives}
                  travelMode={routing.travelMode}
                  onSelect={routing.setTravelMode}
                />
              )}

              {routing.travelMode === 'TRANSIT' ? (
                <RouteResults
                  results={routing.results}
                  selectedIndex={routing.selectedIndex}
                  onSelect={routing.setSelectedIndex}
                  loading={routing.loading}
                  error={routing.error}
                  onRetry={handleSearch}
                  onLoadEarlier={routing.loadEarlier}
                  onLoadLater={routing.loadLater}
                  loadingEarlier={routing.loadingEarlier}
                  loadingLater={routing.loadingLater}
                  pagingNotice={routing.pagingNotice}
                />
              ) : (
                routing.alternatives
                  .filter(alt => alt.mode === routing.travelMode)
                  .map(alt => <DirectRouteCard key={alt.mode} alternative={alt} />)
              )}

              {routing.travelMode === 'TRANSIT' && routing.selectedItinerary && (
                <div ref={detailRef}>
                  <ItineraryDetail
                    itinerary={routing.selectedItinerary}
                    trip={routing.origin && routing.destination ? {
                      origin: routing.origin,
                      destination: routing.destination,
                      departureTime: routing.departureTime,
                      arriveBy: routing.arriveBy,
                    } : null}
                    liveBus={liveBus}
                    onShowLiveBusOnMap={onShowLiveBusOnMap}
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
