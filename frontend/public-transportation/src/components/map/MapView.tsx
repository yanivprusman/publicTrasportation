import { useEffect, useState, useMemo } from 'react'
import { MapContainer, TileLayer, useMapEvents, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { fetchAddress } from './MapUtilities'
import MapContextMenu from './MapContextMenu'
import { configureDefaultLeafletIcons } from './MapMarkers'
import MapControls from './MapControls'
import useMapHandlers from '../../hooks/useMapHandlers'
import MapEffect from './MapEffect'
import RouteLayer from './RouteLayer'
import MarkersLayer from './MarkersLayer'
import MapControlPanel from './MapControlPanel'
import MultimodalRouteLayer from './MultimodalRouteLayer'
import type { Coordinates, VehicleMarker, StopInfo, Itinerary } from '../../types'
import styles from './MapView.module.css'

configureDefaultLeafletIcons()

function TrackMapMovement({ setMapCenter }: { setMapCenter: (c: Coordinates) => void }) {
  useMapEvents({
    moveend: (event) => {
      const map = event.target
      const center = map.getCenter()
      setMapCenter([center.lat, center.lng])
    },
  })
  return null
}

function UpdateMapView({ position }: { position: Coordinates }) {
  const map = useMap()
  useEffect(() => {
    const center = map.getCenter()
    if (Math.abs(center.lat - position[0]) > 1e-6 || Math.abs(center.lng - position[1]) > 1e-6) {
      map.setView(position, map.getZoom())
    }
  }, [position, map])
  return null
}

interface MapViewProps {
  latitude: number
  longitude: number
  destination: Coordinates | null
  onDestinationSet: (dest: Coordinates) => void
  startingPoint: Coordinates
  mapCenter: Coordinates
  setMapCenter: (center: Coordinates) => void
  vehicleMarkers?: VehicleMarker[]
  routeShape?: Coordinates[] | null
  stops?: StopInfo[]
  selectedStop?: string | null
  center: Coordinates
  defaultStartingPoint: Coordinates
  defaultDestination: Coordinates
  calculateRoute: boolean
  onRouteCalculated: () => void
  onStartingPointSet: (pos: Coordinates) => void
  selectedItinerary?: Itinerary | null
  onRouteFrom: (lat: number, lon: number) => void
  onRouteTo: (lat: number, lon: number) => void
}

function MapView({
  latitude,
  longitude,
  destination,
  onDestinationSet,
  startingPoint,
  mapCenter: initialMapCenter,
  setMapCenter,
  vehicleMarkers = [],
  routeShape = null,
  stops = [],
  selectedStop = null,
  center,
  defaultStartingPoint,
  defaultDestination,
  calculateRoute,
  onRouteCalculated,
  onStartingPointSet,
  selectedItinerary = null,
  onRouteFrom,
  onRouteTo
}: MapViewProps) {
  const [position, setPosition] = useState<Coordinates>(startingPoint || defaultStartingPoint || [latitude, longitude])
  const [route, setRoute] = useState<Coordinates[] | null>(null)
  const [mapReady, setMapReady] = useState(false)
  const [mapCenter, setMapCenterLocal] = useState<Coordinates>(center || initialMapCenter || [latitude, longitude])
  const [showRoutePanel, setShowRoutePanel] = useState(false)
  const [middlePoint, setMiddlePoint] = useState<Coordinates | null>(null)

  useEffect(() => {
    if (setMapCenter) {
      setMapCenter(mapCenter)
    }
  }, [mapCenter, setMapCenter])

  useEffect(() => {
    if (defaultDestination && (!destination || !destination[0])) {
      onDestinationSet(defaultDestination)
    }
  }, [defaultDestination, destination, onDestinationSet])

  const {
    searchQuery,
    setSearchQuery,
    searchError,
    setSearchError,
    positionAddress,
    destinationAddress,
    setPositionAddress,
    setDestinationAddress,
    handleSearch,
    handleSetStartPoint,
    handleSetDestinationPoint,
    handleKeyPress,
    handleFindRoute
  } = useMapHandlers(position, (newPos) => {
    setPosition(newPos)
    if (onStartingPointSet) onStartingPointSet(newPos)
  }, destination, onDestinationSet, setMapCenterLocal, mapCenter)

  useEffect(() => {
    if (destination && Array.isArray(destination) && destination.length === 2) {
      fetchAddress(destination[0], destination[1], setDestinationAddress)
    }
  }, [destination])

  useEffect(() => {
    if (position && Array.isArray(position) && position.length === 2) {
      fetchAddress(position[0], position[1], setPositionAddress)
    }
  }, [position])

  useEffect(() => {
    if (calculateRoute && position && destination) {
      handleFindRouteClick()
      if (onRouteCalculated) {
        onRouteCalculated()
      }
    }
  }, [calculateRoute, position, destination, onRouteCalculated])

  useEffect(() => {
    if (startingPoint && Array.isArray(startingPoint) && startingPoint.length === 2) {
      if (startingPoint[0] !== position[0] || startingPoint[1] !== position[1]) {
        setPosition(startingPoint)
      }
    }
  }, [startingPoint, position])

  const handleShowRoutePanel = () => {
    if (routeShape && routeShape.length > 0) {
      setShowRoutePanel(true)
    }
  }

  const handleFindRouteClick = async () => {
    if (destination) {
      const routeData = await handleFindRoute()
      if (routeData) {
        setRoute(routeData)
      }
    }
  }

  const handleShowMiddlePoint = async () => {
    try {
      setSearchError('Fetching Line 60 route data...')

      if (routeShape && routeShape.length > 0) {
        const middleIndex = Math.floor(routeShape.length / 2)
        const calculatedMiddlePoint = routeShape[middleIndex]
        setMiddlePoint(calculatedMiddlePoint)
        setMapCenterLocal(calculatedMiddlePoint)
        setSearchError(null)
        return
      }

      // Fallback: hardcoded middle point for Line 60
      const hardcodedMiddlePoint: Coordinates = [32.0729, 34.8046]
      setMiddlePoint(hardcodedMiddlePoint)
      setMapCenterLocal(hardcodedMiddlePoint)
      setSearchError(null)
    } catch {
      const fallbackPoint: Coordinates = [32.0729, 34.8046]
      setMiddlePoint(fallbackPoint)
      setMapCenterLocal(fallbackPoint)
      setSearchError(null)
    }
  }

  const optimizedRouteShape = useMemo(() => {
    return routeShape
  }, [routeShape])

  // Suppress unused var warning - mapReady is used by whenReady callback
  void mapReady

  return (
    <div className={styles.wrapper}>
      <MapControls
        searchQuery={searchQuery}
        setSearchQuery={setSearchQuery}
        handleSearch={handleSearch}
        handleKeyPress={handleKeyPress}
        handleSetStartPoint={handleSetStartPoint}
        handleSetDestinationPoint={handleSetDestinationPoint}
        searchError={searchError}
        positionAddress={positionAddress}
        destinationAddress={destinationAddress}
      />

      <button
        onClick={handleShowMiddlePoint}
        className={styles.middlePointButton}
        title="Show middle point of Line 60 route"
        data-id="show-line-60-middle-point"
      >
        Show Line 60 Middle Point
      </button>

      <MapControlPanel
        showRoutePanel={showRoutePanel}
        setShowRoutePanel={setShowRoutePanel}
        optimizedRouteShape={optimizedRouteShape}
        handleShowRoutePanel={handleShowRoutePanel}
      />

      <MapContainer
        center={mapCenter}
        zoom={13}
        style={{ height: '100%', width: '100%' }}
        zoomControl={false}
        whenReady={() => setMapReady(true)}
        preferCanvas={true}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        <UpdateMapView position={mapCenter} />
        <TrackMapMovement setMapCenter={setMapCenterLocal} />
        <MapContextMenu
          onRouteFrom={onRouteFrom}
          onRouteTo={onRouteTo}
        />
        <MapEffect routeShape={optimizedRouteShape} />

        <RouteLayer
          routeShape={routeShape}
          route={route}
        />

        <MultimodalRouteLayer itinerary={selectedItinerary} />

        <MarkersLayer
          position={position}
          positionAddress={positionAddress}
          destination={destination}
          destinationAddress={destinationAddress}
          mapCenter={mapCenter}
          vehicleMarkers={vehicleMarkers}
          stops={stops}
          selectedStop={selectedStop}
          handleSetStartPoint={handleSetStartPoint}
          middlePoint={middlePoint}
        />
      </MapContainer>
    </div>
  )
}

export default MapView
