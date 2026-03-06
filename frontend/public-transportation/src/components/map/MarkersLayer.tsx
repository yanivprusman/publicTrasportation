import { Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import { originIcon, destinationIcon, centerIcon, createBusIcon, stopIcon } from './MapMarkers'
import type { Coordinates, VehicleMarker, StopInfo } from '../../types'

interface MarkersLayerProps {
  position: Coordinates
  positionAddress: string
  destination: Coordinates | null
  destinationAddress: string
  mapCenter: Coordinates
  vehicleMarkers: VehicleMarker[]
  stops: StopInfo[]
  selectedStop?: string | null
  handleSetStartPoint: (coords: Coordinates) => void
  middlePoint: Coordinates | null
}

const MarkersLayer = ({
  position,
  positionAddress,
  destination,
  destinationAddress,
  mapCenter,
  vehicleMarkers,
  stops,
  selectedStop,
  handleSetStartPoint,
  middlePoint
}: MarkersLayerProps) => {
  const middlePointIcon = L.icon({
    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-violet.png',
    iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-violet.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
  })

  return (
    <>
      <Marker position={position} icon={originIcon}>
        <Popup>
          <strong>Origin:</strong> {positionAddress}<br/>
          Coordinates: {position[0].toFixed(6)}, {position[1].toFixed(6)}
        </Popup>
      </Marker>

      {middlePoint && (
        <Marker position={middlePoint} icon={middlePointIcon}>
          <Popup>
            <div>
              <strong>Middle Point</strong><br/>
              Coordinates: {middlePoint[0].toFixed(6)}, {middlePoint[1].toFixed(6)}
            </div>
          </Popup>
        </Marker>
      )}

      {destination && (
        <Marker position={destination} icon={destinationIcon}>
          <Popup>
            <div>
              <strong>Destination:</strong> {destinationAddress}<br/>
              <strong>Coordinates:</strong> {destination[0].toFixed(6)}, {destination[1].toFixed(6)}
            </div>
          </Popup>
        </Marker>
      )}

      {/* Map center marker hidden - was showing "Map Center: lat, lon" */}

      {vehicleMarkers.map((vehicle, index) => (
        <Marker
          key={`vehicle-${index}`}
          position={vehicle.position}
          icon={createBusIcon(vehicle.lineNumber)}
        >
          <Popup>
            <div>
              <strong>Line:</strong> {vehicle.lineNumber}<br/>
              <strong>Vehicle:</strong> {vehicle.vehicleRef}<br/>
              <strong>Expected arrival:</strong> {vehicle.expectedArrival}<br/>
              <strong>Distance from stop:</strong> {vehicle.distanceFromStop} m
            </div>
          </Popup>
        </Marker>
      ))}

      {stops.map(stop => (
        <Marker
          key={`stop-${stop.stopId}`}
          position={[stop.lat, stop.lon]}
          icon={stop.stopId === selectedStop ? centerIcon : stopIcon}
        >
          <Popup>
            <div>
              <strong>Stop:</strong> {stop.stopName}<br/>
              <strong>ID:</strong> {stop.stopId}<br/>
              <button onClick={() => handleSetStartPoint([stop.lat, stop.lon])}>
                Set as starting point
              </button>
            </div>
          </Popup>
        </Marker>
      ))}
    </>
  )
}

export default MarkersLayer
