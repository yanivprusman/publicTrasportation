import { Marker, Popup } from 'react-leaflet'
import { originIcon, destinationIcon, centerIcon, createBusIcon, stopIcon } from './MapMarkers'
import { formatTime } from '../../utils/time-format'
import type { Coordinates, VehicleMarker, StopInfo } from '../../types'

// SIRI gives ExpectedArrivalTime as a raw ISO string; render a readable HH:MM
// instead of dumping the machine timestamp into the popup. Empty/unparseable
// values (the '' default from extractVehicleMarkers) show as 'N/A'.
const formatArrival = (iso: string): string => {
  if (!iso || isNaN(new Date(iso).getTime())) return 'N/A'
  return formatTime(iso)
}

const formatDistance = (meters: number): string =>
  meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${meters} m`

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
  handleSetStartPoint
}: MarkersLayerProps) => {
  return (
    <>
      <Marker position={position} icon={originIcon}>
        <Popup>
          <strong>Origin:</strong> {positionAddress}<br/>
          Coordinates: {position[0].toFixed(6)}, {position[1].toFixed(6)}
        </Popup>
      </Marker>

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
              <strong>Expected arrival:</strong> {formatArrival(vehicle.expectedArrival)}<br/>
              <strong>Distance from stop:</strong> {formatDistance(vehicle.distanceFromStop)}
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
