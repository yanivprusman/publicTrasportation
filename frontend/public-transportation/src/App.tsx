import { useState, useEffect } from 'react'
import MapView from './components/map/MapView'
import StationArrivals from './components/data-display/StationArrivals'
import TransportControls from './components/controls/TransportControls'
import RoutePlanner from './components/routing/RoutePlanner'
import { useRouting } from './hooks/useRouting'
import { useSessionState } from './hooks/useSessionState'
import { fetchStationArrivals, extractVehicleMarkers, fetchLineShape } from './services/transport-api'
import type { SheetState } from './components/routing/BottomSheet'
import type { SiriData, VehicleMarker, Coordinates } from './types'

function App() {
  const defaultStartingPoint: Coordinates = [32.0783, 34.8120]
  const defaultDestination: Coordinates = [32.0673, 34.7835]

  const [siriData, setSiriData] = useState<SiriData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stationCode, setStationCode] = useSessionState('stationCode', '26472')
  const [lineNumber, setLineNumber] = useSessionState('lineNumber', '60')
  const [routeShape, setRouteShape] = useState<Coordinates[] | null>(null)
  const [routeDirection, setRouteDirection] = useSessionState('routeDirection', '0')

  const [showVehicleMarkers, setShowVehicleMarkers] = useSessionState('showVehicleMarkers', false)
  const [vehicleMarkers, setVehicleMarkers] = useState<VehicleMarker[]>([])

  const [startingPoint, setStartingPoint] = useState<Coordinates>(defaultStartingPoint)
  const [destination, setDestination] = useState<Coordinates>(defaultDestination)

  const [mapCenter, setMapCenter] = useState<Coordinates>(defaultStartingPoint)
  const [calculateRoute, setCalculateRoute] = useState(false)

  const routing = useRouting()
  const [sheetState, setSheetState] = useState<SheetState>('collapsed')
  const [activeTab, setActiveTab] = useSessionState<'route' | 'arrivals'>('activeTab', 'route')

  const fetchStationData = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await fetchStationArrivals(stationCode)
      setSiriData(data)
      const markers = extractVehicleMarkers(data)
      setVehicleMarkers(markers)
      setLoading(false)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setError(`Connection error: ${message}. Make sure the API server is running.`)
      setLoading(false)
    }
  }

  const handleFindRoute = async () => {
    if (!destination) {
      setError('Please set a destination first')
      return
    }
    setLoading(true)
    setError(null)
    try {
      setCalculateRoute(true)
      setLoading(false)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setError(`Error finding route: ${message}`)
      setLoading(false)
    }
  }

  const handleFetchLineShape = async () => {
    setLoading(true)
    setError(null)
    try {
      if (!lineNumber.trim()) {
        setError('Please enter a valid line number')
        setLoading(false)
        return
      }

      const data = await fetchLineShape(lineNumber)

      if (data[routeDirection] && Array.isArray(data[routeDirection]) && data[routeDirection].length > 0) {
        setRouteShape(data[routeDirection])
        if (data[routeDirection][0] && data[routeDirection][0].length === 2) {
          setMapCenter(data[routeDirection][0])
        }
      } else if (data['0'] && Array.isArray(data['0']) && data['0'].length > 0) {
        setRouteShape(data['0'])
        setMapCenter(data['0'][0])
        setRouteDirection('0')
      } else if (data['1'] && Array.isArray(data['1']) && data['1'].length > 0) {
        setRouteShape(data['1'])
        setMapCenter(data['1'][0])
        setRouteDirection('1')
      } else {
        throw new Error('No valid shape data found in the API response')
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setError(`Failed to load route shape: ${message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleRouteFrom = (lat: number, lon: number) => {
    routing.setOriginFromCoords(lat, lon)
    setStartingPoint([lat, lon])
    setActiveTab('route')
    setSheetState('half')
  }

  const handleRouteTo = (lat: number, lon: number) => {
    routing.setDestinationFromCoords(lat, lon)
    setDestination([lat, lon])
    setActiveTab('route')
    setSheetState('half')
  }

  useEffect(() => {
    fetchStationData()
  }, [stationCode])

  const arrivalsContent = (
    <>
      <TransportControls
        stationCode={stationCode}
        setStationCode={setStationCode}
        fetchStationData={fetchStationData}
        lineNumber={lineNumber}
        setLineNumber={setLineNumber}
        routeDirection={routeDirection}
        setRouteDirection={setRouteDirection}
        fetchLineShape={handleFetchLineShape}
        showVehicleMarkers={showVehicleMarkers}
        setShowVehicleMarkers={setShowVehicleMarkers}
        handleFindRoute={handleFindRoute}
      />
      <StationArrivals
        siriData={siriData}
        loading={loading}
        error={error}
        stationCode={stationCode}
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
        vehicleMarkers={showVehicleMarkers ? vehicleMarkers : []}
        routeShape={routeShape}
        destination={destination}
        onDestinationSet={setDestination}
        startingPoint={startingPoint}
        onStartingPointSet={setStartingPoint}
        center={mapCenter}
        defaultStartingPoint={defaultStartingPoint}
        defaultDestination={defaultDestination}
        calculateRoute={calculateRoute}
        onRouteCalculated={() => setCalculateRoute(false)}
        selectedItinerary={routing.selectedItinerary}
        onRouteFrom={handleRouteFrom}
        onRouteTo={handleRouteTo}
      />

      <RoutePlanner
        routing={routing}
        sheetState={sheetState}
        onSheetStateChange={setSheetState}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        arrivalsContent={arrivalsContent}
      />
    </div>
  )
}

export default App
