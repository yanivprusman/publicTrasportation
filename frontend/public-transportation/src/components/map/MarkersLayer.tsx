import { Marker, Popup } from 'react-leaflet'
import { originIcon, destinationIcon, viaIcon, centerIcon, createBusIcon, stopIcon } from './MapMarkers'
import { formatTime } from '../../utils/time-format'
import { formatStopDistance } from '../../utils/distance'
import { useI18n } from '../../i18n'
import type { Coordinates, VehicleMarker, StopInfo } from '../../types'

// SIRI gives ExpectedArrivalTime as a raw ISO string; render a readable HH:MM
// instead of dumping the machine timestamp into the popup. Empty/unparseable
// values (the '' default from extractVehicleMarkers) show as 'N/A'.
const formatArrival = (iso: string): string => {
  if (!iso || isNaN(new Date(iso).getTime())) return 'N/A'
  return formatTime(iso)
}

interface MarkersLayerProps {
  position: Coordinates | null
  positionAddress: string
  destination: Coordinates | null
  destinationAddress: string
  viaPoint?: Coordinates | null
  viaName?: string
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
  viaPoint = null,
  viaName = '',
  mapCenter,
  vehicleMarkers,
  stops,
  selectedStop,
  handleSetStartPoint
}: MarkersLayerProps) => {
  const { t } = useI18n()
  return (
    <>
      {position && (
        <Marker position={position} icon={originIcon}>
          <Popup>
            <strong>{t('popup.origin')}</strong> {positionAddress}<br/>
            {t('popup.coordinates')} {position[0].toFixed(6)}, {position[1].toFixed(6)}
          </Popup>
        </Marker>
      )}

      {viaPoint && (
        <Marker position={viaPoint} icon={viaIcon}>
          <Popup>
            <div>
              <strong>{t('popup.via')}</strong> {viaName}<br/>
              <strong>{t('popup.coordinates')}</strong> {viaPoint[0].toFixed(6)}, {viaPoint[1].toFixed(6)}
            </div>
          </Popup>
        </Marker>
      )}

      {destination && (
        <Marker position={destination} icon={destinationIcon}>
          <Popup>
            <div>
              <strong>{t('popup.destination')}</strong> {destinationAddress}<br/>
              <strong>{t('popup.coordinates')}</strong> {destination[0].toFixed(6)}, {destination[1].toFixed(6)}
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
              <strong>{t('popup.line')}</strong> {vehicle.lineNumber}<br/>
              <strong>{t('popup.vehicle')}</strong> {vehicle.vehicleRef}<br/>
              <strong>{t('popup.expectedArrival')}</strong> {formatArrival(vehicle.expectedArrival)}<br/>
              <strong>{t('popup.tripTravelled')}</strong> {formatStopDistance(vehicle.tripTravelledMeters)}
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
              <strong>{t('popup.stop')}</strong> {stop.stopName}<br/>
              <strong>{t('popup.id')}</strong> {stop.stopId}<br/>
              <button onClick={() => handleSetStartPoint([stop.lat, stop.lon])}>
                {t('popup.setAsStart')}
              </button>
            </div>
          </Popup>
        </Marker>
      ))}
    </>
  )
}

export default MarkersLayer
