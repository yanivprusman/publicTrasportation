import { useState, useEffect } from 'react'
import './App.css'
import MapView from './components/map/MapView'
import StationArrivals from './components/data-display/StationArrivals'
import TransportControls from './components/controls/TransportControls'
import { fetchStationArrivals, extractVehicleMarkers, fetchLineShape } from './services/transport-api'
import type { SiriData, VehicleMarker, Coordinates } from './types'

function App() {
  const defaultStartingPoint: Coordinates = [32.0783, 34.8120]
  const defaultDestination: Coordinates = [32.0673, 34.7835]

  const [siriData, setSiriData] = useState<SiriData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stationCode, setStationCode] = useState('26472')
  const [lineNumber, setLineNumber] = useState('60')
  const [routeShape, setRouteShape] = useState<Coordinates[] | null>(null)
  const [routeDirection, setRouteDirection] = useState('0')

  const [showVehicleMarkers, setShowVehicleMarkers] = useState(false)
  const [vehicleMarkers, setVehicleMarkers] = useState<VehicleMarker[]>([])

  const [startingPoint, setStartingPoint] = useState<Coordinates>(defaultStartingPoint)
  const [destination, setDestination] = useState<Coordinates>(defaultDestination)

  const [mapCenter, setMapCenter] = useState<Coordinates>(defaultStartingPoint)
  const [calculateRoute, setCalculateRoute] = useState(false)

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

  useEffect(() => {
    fetchStationData()
  }, [stationCode])

  return (
    <div className="combined-app">
      <div className="app-header">
        <h1>Israel Public Transportation Tracker</h1>
      </div>

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

      <div className="main-content">
        <div className="map-section">
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
          />
        </div>
        <div className="data-section">
          <StationArrivals
            siriData={siriData}
            loading={loading}
            error={error}
            stationCode={stationCode}
          />
        </div>
      </div>
    </div>
  )
}

export default App
