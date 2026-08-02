import { useEffect, useState, useMemo, useRef } from 'react'
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
import LineShapeLayer from './LineShapeLayer'
import NearbyStopsLayer from './NearbyStopsLayer'
import StopsLayer from './StopsLayer'
import LiveBusLayer, { type LiveBusMarkerData } from './LiveBusLayer'
import MapStyleControls from './MapStyleControls'
import { tilesFor, type MapStyle } from '../../hooks/useMapStyle'
import { useFollowLocation } from '../../hooks/useFollowLocation'
import type { LineFocusRequest } from '../../hooks/useLineExplorer'
import type { NearbyStop, StopResult } from '../../services/transport-api'
import type { Coordinates, VehicleMarker, StopInfo, Itinerary, LineShapeData } from '../../types'
import styles from './MapView.module.css'

configureDefaultLeafletIcons()

/** Two centres that describe the same spot (~0.1 m apart). */
const sameCenter = (a: Coordinates | null, b: Coordinates): boolean =>
  a !== null && Math.abs(a[0] - b[0]) < 1e-6 && Math.abs(a[1] - b[1]) < 1e-6

/**
 * Mirrors the map's centre into React state.
 *
 * This is a feedback edge — the state it writes flows back down as the map's
 * centre — so it must only report movement the *user* caused. Two guards keep
 * it from feeding itself:
 *
 *  - Popup auto-pan is not navigation. Leaflet nudges the map whenever an open
 *    popup would not fit, and react-leaflet calls `popup.update()` on every
 *    render of the popup's children. Mirroring that pan re-renders the map,
 *    which updates the popup, which pans again: an unbounded render loop that
 *    ends in "Maximum update depth exceeded". `autopanstart` marks those moves.
 *  - A centre equal to the one already published is dropped, so a moveend that
 *    lands where we already are cannot churn state with a fresh array.
 */
function TrackMapMovement({ setMapCenter }: { setMapCenter: (c: Coordinates) => void }) {
  const publishedRef = useRef<Coordinates | null>(null)
  const autoPanningRef = useRef(false)

  useMapEvents({
    autopanstart: () => {
      autoPanningRef.current = true
    },
    moveend: (event) => {
      if (autoPanningRef.current) {
        autoPanningRef.current = false
        return
      }
      const center = event.target.getCenter()
      const next: Coordinates = [center.lat, center.lng]
      if (sameCenter(publishedRef.current, next)) return
      publishedRef.current = next
      setMapCenter(next)
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
  startingPoint: Coordinates | null
  mapCenter: Coordinates
  setMapCenter: (center: Coordinates) => void
  vehicleMarkers?: VehicleMarker[]
  routeShape?: Coordinates[] | null
  stops?: StopInfo[]
  selectedStop?: string | null
  center: Coordinates
  calculateRoute: boolean
  onRouteCalculated: () => void
  onStartingPointSet: (pos: Coordinates) => void
  viaPoint?: Coordinates | null
  viaName?: string
  selectedItinerary?: Itinerary | null
  onRouteFrom: (lat: number, lon: number) => void
  onRouteTo: (lat: number, lon: number) => void
  exploredLine?: string
  lineShape?: LineShapeData | null
  hiddenLineDirections?: Record<string, boolean>
  lineFocus?: LineFocusRequest
  nearbyStops?: NearbyStop[]
  nearbyUserLocation?: Coordinates | null
  nearbyFocusSeq?: number
  onNearbyStopSelect?: (stop: NearbyStop) => void
  activeStopCode?: string | null
  onMapStopSelect?: (stop: StopResult) => void
  liveBus?: LiveBusMarkerData | null
  mapStyle: MapStyle
  onMapStyleChange: (style: MapStyle) => void
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
  calculateRoute,
  onRouteCalculated,
  onStartingPointSet,
  viaPoint = null,
  viaName = '',
  selectedItinerary = null,
  onRouteFrom,
  onRouteTo,
  exploredLine = '',
  lineShape = null,
  hiddenLineDirections = {},
  lineFocus = { direction: null, seq: 0 },
  nearbyStops = [],
  nearbyUserLocation = null,
  nearbyFocusSeq = 0,
  onNearbyStopSelect = () => {},
  activeStopCode = null,
  onMapStopSelect = () => {},
  liveBus = null,
  mapStyle,
  onMapStyleChange
}: MapViewProps) {
  const follow = useFollowLocation()
  // null until the user sets an origin — no marker, no reverse geocode.
  const [position, setPosition] = useState<Coordinates | null>(startingPoint)
  const [route, setRoute] = useState<Coordinates[] | null>(null)
  const [mapReady, setMapReady] = useState(false)
  const [mapCenter, setMapCenterLocal] = useState<Coordinates>(center || initialMapCenter || [latitude, longitude])
  const [showRoutePanel, setShowRoutePanel] = useState(false)

  useEffect(() => {
    if (setMapCenter) {
      setMapCenter(mapCenter)
    }
  }, [mapCenter, setMapCenter])

  // Sync external center changes (e.g. "Show on map" on an arrival row) into
  // the local center that actually drives the map: useState only captures the
  // prop at mount. Centers round-tripped from our own moveend handler come
  // back with the same array identity, so those setState calls bail out.
  useEffect(() => {
    setMapCenterLocal(center)
  }, [center])

  // Follow mode: every fix re-centres the map. Panning away is allowed — the
  // next fix pulls it back, which is what "follow" means; the user turns the
  // mode off to browse freely.
  useEffect(() => {
    if (follow.following && follow.position) {
      setMapCenterLocal(follow.position)
    }
  }, [follow.following, follow.position])

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

  // Keyed on the coordinates themselves, not on the array holding them: the
  // parent rebuilds these arrays on every render, and depending on identity
  // fired one reverse geocode per render — hundreds of requests a second under
  // any render loop, which is how the geocoder's quota got burned.
  const destinationKey = destination?.length === 2 ? `${destination[0]},${destination[1]}` : ''
  useEffect(() => {
    if (!destinationKey) {
      setDestinationAddress('')
      return
    }
    const [lat, lon] = destinationKey.split(',').map(Number)
    fetchAddress(lat, lon, setDestinationAddress)
  }, [destinationKey]) // eslint-disable-line react-hooks/exhaustive-deps

  const positionKey = position?.length === 2 ? `${position[0]},${position[1]}` : ''
  useEffect(() => {
    if (!positionKey) {
      setPositionAddress('')
      return
    }
    const [lat, lon] = positionKey.split(',').map(Number)
    fetchAddress(lat, lon, setPositionAddress)
  }, [positionKey]) // eslint-disable-line react-hooks/exhaustive-deps

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
      if (!position || startingPoint[0] !== position[0] || startingPoint[1] !== position[1]) {
        setPosition(startingPoint)
      }
    } else if (!startingPoint && position) {
      // Origin cleared upstream (input × button) — remove the marker too.
      setPosition(null)
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

      <MapControlPanel
        showRoutePanel={showRoutePanel}
        setShowRoutePanel={setShowRoutePanel}
        optimizedRouteShape={optimizedRouteShape}
        handleShowRoutePanel={handleShowRoutePanel}
      />

      <MapStyleControls
        mapStyle={mapStyle}
        onStyleChange={onMapStyleChange}
        following={follow.following}
        onToggleFollow={follow.toggle}
        locating={follow.locating}
      />

      <MapContainer
        center={mapCenter}
        zoom={13}
        style={{ height: '100%', width: '100%' }}
        zoomControl={false}
        whenReady={() => setMapReady(true)}
        preferCanvas={true}
      >
        {/* Keyed on the style so Leaflet tears the layer down and rebuilds it —
            it does not pick up a changed tile URL in place. */}
        <TileLayer
          key={mapStyle}
          url={tilesFor(mapStyle).url}
          attribution={tilesFor(mapStyle).attribution}
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

        <LiveBusLayer bus={liveBus} />

        <LineShapeLayer
          line={exploredLine}
          data={lineShape}
          hiddenDirections={hiddenLineDirections}
          focus={lineFocus}
        />

        <StopsLayer
          activeStopCode={activeStopCode}
          onStopSelect={onMapStopSelect}
        />

        <NearbyStopsLayer
          stops={nearbyStops}
          userLocation={nearbyUserLocation}
          focusSeq={nearbyFocusSeq}
          onStopSelect={onNearbyStopSelect}
        />

        <MarkersLayer
          position={position}
          positionAddress={positionAddress}
          destination={destination}
          destinationAddress={destinationAddress}
          viaPoint={viaPoint}
          viaName={viaName}
          mapCenter={mapCenter}
          vehicleMarkers={vehicleMarkers}
          stops={stops}
          selectedStop={selectedStop}
          handleSetStartPoint={handleSetStartPoint}
        />
      </MapContainer>
    </div>
  )
}

export default MapView
