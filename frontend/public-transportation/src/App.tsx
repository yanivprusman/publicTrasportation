import { useState, useEffect, useCallback } from 'react'
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
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
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
  const [lineFilter, setLineFilter] = useSessionState('lineFilter', '')
  const [sheetState, setSheetState] = useState<SheetState>('collapsed')
  const [activeTab, setActiveTab] = useSessionState<'route' | 'arrivals'>('activeTab', 'route')

  const fetchStationData = async () => {
    setError(null)
    try {
      const data = await fetchStationArrivals(stationCode)
      setSiriData(data)
      const markers = extractVehicleMarkers(data)
      setVehicleMarkers(markers)
      setLastUpdated(new Date())
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setError(`Connection error: ${message}. Make sure the API server is running.`)
    }
  }

  const handleFetchLineShape = async () => {
    setError(null)
    try {
      if (!lineNumber.trim()) {
        setError('Please enter a valid line number')
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
    if (activeTab !== 'arrivals') return
    fetchStationData()
    const interval = setInterval(fetchStationData, 15000)
    return () => clearInterval(interval)
  }, [stationCode, activeTab])

  const handleVehicleSelect = useCallback((lat: number, lon: number) => {
    setMapCenter([lat, lon])
    setShowVehicleMarkers(true)
  }, [setShowVehicleMarkers])

  const arrivalsContent = (
    <>
      <TransportControls
        stationCode={stationCode}
        setStationCode={setStationCode}
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
        lineNumber={lineNumber}
        setLineNumber={setLineNumber}
        routeDirection={routeDirection}
        setRouteDirection={setRouteDirection}
        onFetchLineShape={handleFetchLineShape}
      />
    </div>
  )
}

export default App
