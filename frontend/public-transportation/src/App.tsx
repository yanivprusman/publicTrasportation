import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import MapView from './components/map/MapView'
import StationArrivals from './components/data-display/StationArrivals'
import DepartureBoard from './components/data-display/DepartureBoard'
import TransportControls from './components/controls/TransportControls'
import RoutePlanner from './components/routing/RoutePlanner'
import LineExplorer from './components/lines/LineExplorer'
import NearbyStops from './components/nearby/NearbyStops'
import StationTimetable from './components/arrivals/StationTimetable'
import ServiceAlertBanner from './components/arrivals/ServiceAlertBanner'
import FavoriteStationsBar from './components/arrivals/FavoriteStationsBar'
import PricingNotice from './components/onboarding/PricingNotice'
import RegistrationScreen from './components/onboarding/RegistrationScreen'
import { useRouting } from './hooks/useRouting'
import { useLiveBus } from './hooks/useLiveBus'
import { useLineExplorer } from './hooks/useLineExplorer'
import { useNearbyStops } from './hooks/useNearbyStops'
import { useSessionState } from './hooks/useSessionState'
import { useFavorites, type FavoriteStation } from './hooks/useFavorites'
import { useMapStyle } from './hooks/useMapStyle'
import { useTheme } from './hooks/useTheme'
import { useRegistration } from './hooks/useRegistration'
import { fetchStationArrivals, extractVehicleMarkers, type NearbyStop, type StopResult } from './services/transport-api'
import { geocodeSearch } from './services/routing-api'
import { parseTripLink } from './utils/trip-link'
import type { SheetState } from './components/routing/BottomSheet'
import type { SiriData, VehicleMarker, Coordinates } from './types'
import { LanguageProvider, useI18n } from './i18n'

function AppInner() {
  const { t } = useI18n()
  const defaultStartingPoint: Coordinates = [32.0783, 34.8120]
  const defaultDestination: Coordinates = [32.0673, 34.7835]

  const [siriData, setSiriData] = useState<SiriData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [stationCode, setStationCode] = useSessionState('stationCode', '26472')

  const [showVehicleMarkers, setShowVehicleMarkers] = useSessionState('showVehicleMarkers', false)
  const [vehicleMarkers, setVehicleMarkers] = useState<VehicleMarker[]>([])

  const [mapCenter, setMapCenter] = useState<Coordinates>(defaultStartingPoint)
  const [calculateRoute, setCalculateRoute] = useState(false)

  const routing = useRouting()
  const lineExplorer = useLineExplorer()
  const nearby = useNearbyStops()
  const favorites = useFavorites()
  const { theme } = useTheme()
  const { mapStyle, setMapStyle } = useMapStyle(theme)
  const registration = useRegistration()

  // Initialize routing with defaults when no saved route exists. A URL that
  // carries a trip (shared link or debug params) supplies its own points —
  // seeding defaults then would fire reverse geocodes that can overwrite the
  // link's place names after the trip is restored.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (parseTripLink(params) || (params.get('origin') && params.get('destination'))) return
    if (!routing.origin) {
      routing.setOriginFromCoords(defaultStartingPoint[0], defaultStartingPoint[1])
    }
    if (!routing.destination) {
      routing.setDestinationFromCoords(defaultDestination[0], defaultDestination[1])
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Derive marker positions from routing state. Memoized because the map keys
  // effects off these props: a fresh array on every render makes the map treat
  // an unchanged pin as a move and re-geocode it.
  const startingPoint: Coordinates = useMemo(() => routing.origin
    ? [routing.origin.lat, routing.origin.lon]
    : defaultStartingPoint, [routing.origin])
  const destination: Coordinates = useMemo(() => routing.destination
    ? [routing.destination.lat, routing.destination.lon]
    : defaultDestination, [routing.destination])
  const viaPoint: Coordinates | null = useMemo(() => routing.via
    ? [routing.via.lat, routing.via.lon]
    : null, [routing.via])
  const [lineFilter, setLineFilter] = useSessionState('lineFilter', '')
  // Kiosk board mode survives a reload (sessionStorage) so a phone or tablet
  // propped up as a station display comes back as a board, not the planner.
  const [boardMode, setBoardMode] = useSessionState('boardMode', false)
  const [sheetState, setSheetState] = useState<SheetState>(routing.origin && routing.destination ? 'half' : 'collapsed')
  const [activeTab, setActiveTab] = useSessionState<'route' | 'nearby' | 'arrivals' | 'lines'>('activeTab', 'route')

  // Track the actual vehicle serving the selected route's next bus leg; only
  // while the Routes tab is showing, so other tabs don't keep SIRI polling.
  const liveBus = useLiveBus(routing.selectedItinerary, activeTab === 'route')

  // Rebuilt only when the tracked vehicle actually changes. A fresh object per
  // render would re-render the bus marker's popup on every unrelated state
  // change, and an open Leaflet popup re-pans the map each time it updates.
  const liveBusMarker = useMemo(() => liveBus.vehicle ? {
    vehicle: liveBus.vehicle,
    lineNumber: liveBus.lineNumber,
    stopName: liveBus.stopName,
    expectedArrival: liveBus.expectedArrival,
  } : null, [liveBus.vehicle, liveBus.lineNumber, liveBus.stopName, liveBus.expectedArrival])

  // "Show on map" on the live-bus banner: center on the bus and drop the sheet
  // to half so the map is actually visible on mobile.
  const handleShowLiveBusOnMap = useCallback((position: Coordinates) => {
    setMapCenter(position)
    setSheetState('half')
  }, [])

  // Restore a shared trip link (?from=lat,lon&to=lat,lon&fromName=...&time=...)
  // or a debug route (?origin=text&destination=text)
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)

    const sharedTrip = parseTripLink(params)
    if (sharedTrip) {
      routing.initRoute(sharedTrip.origin, sharedTrip.destination, {
        departureTime: sharedTrip.departureTime,
        arriveBy: sharedTrip.arriveBy,
        via: sharedTrip.via,
      })
      setActiveTab('route')
      setSheetState('expanded')
      // Clear the params so a later reload reflects the user's current trip,
      // not the link they arrived with.
      window.history.replaceState({}, '', window.location.pathname)
      return
    }

    const originText = params.get('origin')
    const destText = params.get('destination')
    if (!originText || !destText) return

    const resolve = async () => {
      const [originResults, destResults] = await Promise.all([
        geocodeSearch(originText),
        geocodeSearch(destText),
      ])
      if (originResults.length > 0 && destResults.length > 0) {
        routing.initRoute(originResults[0], destResults[0])
        setActiveTab('route')
        setSheetState('half')
      }
    }
    resolve()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Guards against overlapping arrivals fetches resolving out of order (poll
  // ticks + station switches): only the most recently started fetch may
  // update siriData/markers/error.
  const arrivalsSeqRef = useRef(0)

  const fetchStationData = async () => {
    const seq = ++arrivalsSeqRef.current
    setError(null)
    try {
      const data = await fetchStationArrivals(stationCode)
      if (seq !== arrivalsSeqRef.current) return
      setSiriData(data)
      const markers = extractVehicleMarkers(data)
      setVehicleMarkers(markers)
      setLastUpdated(new Date())
    } catch (err) {
      if (seq !== arrivalsSeqRef.current) return
      const message = err instanceof Error ? err.message : String(err)
      setError(t('app.connectionError', { message }))
    }
  }

  // Switching stations invalidates everything shown for the previous one:
  // clear it immediately so the panel shows "Loading..." instead of the old
  // station's arrivals (with a fresh-looking timestamp) until the fetch lands.
  const handleStationChange = useCallback((code: string) => {
    if (code === stationCode) return
    setSiriData(null)
    setVehicleMarkers([])
    setLastUpdated(null)
    setError(null)
    setStationCode(code)
  }, [stationCode, setStationCode])

  const handleRouteFrom = (lat: number, lon: number) => {
    routing.setOriginFromCoords(lat, lon)
    setActiveTab('route')
    setSheetState('half')
  }

  const handleRouteTo = (lat: number, lon: number) => {
    routing.setDestinationFromCoords(lat, lon)
    setActiveTab('route')
    setSheetState('half')
  }

  useEffect(() => {
    if (activeTab !== 'arrivals') return
    fetchStationData()
    const interval = setInterval(fetchStationData, 15000)
    return () => clearInterval(interval)
  }, [stationCode, activeTab])

  const handleVehicleSelect = useCallback((lat: number, lon: number) => {
    setMapCenter([lat, lon])
    setShowVehicleMarkers(true)
  }, [setShowVehicleMarkers])

  // Tapping a nearby stop jumps straight to its live departure board.
  const handleNearbyStopSelect = useCallback((stop: NearbyStop) => {
    handleStationChange(stop.stopCode)
    setActiveTab('arrivals')
    setMapCenter([stop.lat, stop.lon])
  }, [handleStationChange, setActiveTab])

  // Tapping a stop dot on the map opens its live departure board. Unlike the
  // nearby flow the sheet may be collapsed here, so raise it to half.
  const handleMapStopSelect = useCallback((stop: StopResult) => {
    handleStationChange(stop.stopCode)
    setActiveTab('arrivals')
    setSheetState('half')
    setMapCenter([stop.lat, stop.lon])
  }, [handleStationChange, setActiveTab])

  // Picking a favorite behaves like any other station switch, and re-centres the
  // map only once the stop's coordinates are known (the chip stores name + code).
  const handleFavoriteStationSelect = useCallback((station: FavoriteStation) => {
    handleStationChange(station.code)
  }, [handleStationChange])

  const handleToggleFavoriteStation = useCallback((name: string) => {
    if (!stationCode) return
    favorites.toggleStation({ code: stationCode, name })
  }, [stationCode, favorites])

  const arrivalsContent = (
    <>
      <FavoriteStationsBar
        stations={favorites.stations}
        activeCode={stationCode}
        onSelect={handleFavoriteStationSelect}
        onRemove={favorites.removeStation}
      />
      <TransportControls
        stationCode={stationCode}
        setStationCode={handleStationChange}
        lastUpdated={lastUpdated}
        lineFilter={lineFilter}
        setLineFilter={setLineFilter}
        showVehicleMarkers={showVehicleMarkers}
        setShowVehicleMarkers={setShowVehicleMarkers}
        onOpenBoard={() => setBoardMode(true)}
        isFavorite={favorites.isStationFavorite(stationCode)}
        onToggleFavorite={handleToggleFavoriteStation}
      />
      <ServiceAlertBanner />
      <StationArrivals
        siriData={siriData}
        error={error}
        stationCode={stationCode}
        lineFilter={lineFilter}
        onVehicleSelect={handleVehicleSelect}
      />
      <StationTimetable stationCode={stationCode} />
      {boardMode && (
        <DepartureBoard
          siriData={siriData}
          error={error}
          stationCode={stationCode}
          lineFilter={lineFilter}
          lastUpdated={lastUpdated}
          onClose={() => setBoardMode(false)}
        />
      )}
    </>
  )

  // The pricing promise is shown before registration is asked for, so nobody
  // hands over contact details without first knowing what they are being told
  // about. Both gates resolve from localStorage after mount; until then the app
  // renders normally so a returning user never sees a flash of the gate.
  if (registration.state === 'needsRegistration') {
    return (
      <>
        {!registration.noticeAcknowledged && (
          <PricingNotice onAcknowledge={registration.acknowledgeNotice} />
        )}
        <RegistrationScreen onSubmit={registration.register} />
      </>
    )
  }

  return (
    <div className="combined-app">
      <MapView
        latitude={mapCenter[0]}
        longitude={mapCenter[1]}
        mapCenter={mapCenter}
        setMapCenter={setMapCenter}
        vehicleMarkers={showVehicleMarkers ? (lineFilter.trim()
          ? vehicleMarkers.filter(m => m.lineNumber.toLowerCase() === lineFilter.trim().toLowerCase())
          : vehicleMarkers) : []}
        destination={destination}
        onDestinationSet={(coords: Coordinates) => routing.setDestinationFromCoords(coords[0], coords[1])}
        startingPoint={startingPoint}
        onStartingPointSet={(coords: Coordinates) => routing.setOriginFromCoords(coords[0], coords[1])}
        viaPoint={viaPoint}
        viaName={routing.via?.name || ''}
        center={mapCenter}
        defaultStartingPoint={defaultStartingPoint}
        defaultDestination={defaultDestination}
        calculateRoute={calculateRoute}
        onRouteCalculated={() => setCalculateRoute(false)}
        selectedItinerary={routing.selectedItinerary}
        onRouteFrom={handleRouteFrom}
        onRouteTo={handleRouteTo}
        exploredLine={lineExplorer.line}
        lineShape={lineExplorer.data}
        hiddenLineDirections={lineExplorer.hiddenDirections}
        lineFocus={lineExplorer.focus}
        nearbyStops={activeTab === 'nearby' ? nearby.stops : []}
        nearbyUserLocation={activeTab === 'nearby' ? nearby.userLocation : null}
        nearbyFocusSeq={nearby.focusSeq}
        onNearbyStopSelect={handleNearbyStopSelect}
        activeStopCode={activeTab === 'arrivals' ? stationCode : null}
        onMapStopSelect={handleMapStopSelect}
        liveBus={liveBusMarker}
        mapStyle={mapStyle}
        onMapStyleChange={setMapStyle}
      />

      <RoutePlanner
        routing={routing}
        sheetState={sheetState}
        onSheetStateChange={setSheetState}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        arrivalsContent={arrivalsContent}
        linesContent={<LineExplorer explorer={lineExplorer} favorites={favorites} />}
        nearbyContent={<NearbyStops nearby={nearby} onSelect={handleNearbyStopSelect} />}
        liveBus={liveBus}
        onShowLiveBusOnMap={handleShowLiveBusOnMap}
      />
    </div>
  )
}

export default function App() {
  return (
    <LanguageProvider>
      <AppInner />
    </LanguageProvider>
  )
}
