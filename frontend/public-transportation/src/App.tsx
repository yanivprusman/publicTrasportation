import { useState, useEffect, useCallback, useRef } from 'react'
import MapView from './components/map/MapView'
import StationArrivals from './components/data-display/StationArrivals'
import TransportControls from './components/controls/TransportControls'
import RoutePlanner from './components/routing/RoutePlanner'
import LineExplorer from './components/lines/LineExplorer'
import NearbyStops from './components/nearby/NearbyStops'
import { useRouting } from './hooks/useRouting'
import { useLineExplorer } from './hooks/useLineExplorer'
import { useNearbyStops } from './hooks/useNearbyStops'
import { useSessionState } from './hooks/useSessionState'
import { fetchStationArrivals, extractVehicleMarkers, type NearbyStop } from './services/transport-api'
import { geocodeSearch } from './services/routing-api'
import { parseTripLink } from './utils/trip-link'
import type { SheetState } from './components/routing/BottomSheet'
import type { SiriData, VehicleMarker, Coordinates } from './types'

function App() {
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

  // Derive marker positions from routing state
  const startingPoint: Coordinates = routing.origin
    ? [routing.origin.lat, routing.origin.lon]
    : defaultStartingPoint
  const destination: Coordinates = routing.destination
    ? [routing.destination.lat, routing.destination.lon]
    : defaultDestination
  const [lineFilter, setLineFilter] = useSessionState('lineFilter', '')
  const [sheetState, setSheetState] = useState<SheetState>(routing.origin && routing.destination ? 'half' : 'collapsed')
  const [activeTab, setActiveTab] = useSessionState<'route' | 'nearby' | 'arrivals' | 'lines'>('activeTab', 'route')

  // Restore a shared trip link (?from=lat,lon&to=lat,lon&fromName=...&time=...)
  // or a debug route (?origin=text&destination=text)
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)

    const sharedTrip = parseTripLink(params)
    if (sharedTrip) {
      routing.initRoute(sharedTrip.origin, sharedTrip.destination, {
        departureTime: sharedTrip.departureTime,
        arriveBy: sharedTrip.arriveBy,
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
      setError(`Connection error: ${message}. Make sure the API server is running.`)
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

  const arrivalsContent = (
    <>
      <TransportControls
        stationCode={stationCode}
        setStationCode={handleStationChange}
        lastUpdated={lastUpdated}
        lineFilter={lineFilter}
        setLineFilter={setLineFilter}
        showVehicleMarkers={showVehicleMarkers}
        setShowVehicleMarkers={setShowVehicleMarkers}
      />
      <StationArrivals
        siriData={siriData}
        error={error}
        stationCode={stationCode}
        lineFilter={lineFilter}
        onVehicleSelect={handleVehicleSelect}
      />
    </>
  )

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
      />

      <RoutePlanner
        routing={routing}
        sheetState={sheetState}
        onSheetStateChange={setSheetState}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        arrivalsContent={arrivalsContent}
        linesContent={<LineExplorer explorer={lineExplorer} />}
        nearbyContent={<NearbyStops nearby={nearby} onSelect={handleNearbyStopSelect} />}
      />
    </div>
  )
}

export default App
