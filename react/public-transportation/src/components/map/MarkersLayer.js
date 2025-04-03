import React from 'react';
import { Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import { destinationIcon, centerIcon, vehicleIcon, stopIcon } from './MapMarkers';

/**
 * Displays all types of markers on the map
 */
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
  middlePoint  // Add middle point parameter
}) => {
  // Custom green icon for position marker
  const positionIcon = L.icon({
    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
    iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
  });

  // Custom purple icon for middle point marker
  const middlePointIcon = L.icon({
    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-violet.png',
    iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-violet.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
  });

  return (
    <>
      {/* User position marker */}
      <Marker position={position} icon={positionIcon}>
        <Popup>
          <strong>Starting Point:</strong> {positionAddress}<br/>
          Coordinates: {position[0].toFixed(6)}, {position[1].toFixed(6)}
        </Popup>
      </Marker>
      
      {/* Middle point marker */}
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
      
      {/* Destination marker */}
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
      
      {/* Map center marker */}
      <Marker position={mapCenter} icon={centerIcon}>
        <Popup>
          <div>
            <strong>Map Center:</strong> {Array.isArray(mapCenter) ? `${mapCenter[0]?.toFixed(6)}, ${mapCenter[1]?.toFixed(6)}` : 'Invalid coordinates'}
          </div>
        </Popup>
      </Marker>
      
      {/* Vehicle markers */}
      {vehicleMarkers.map((vehicle, index) => (
        <Marker 
          key={`vehicle-${index}`} 
          position={vehicle.position}
          icon={vehicleIcon}
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
      
      {/* Stop markers */}
      {stops.map((stop, index) => (
        <Marker 
          key={`stop-${stop.id}-${index}`}
          position={[stop.lat, stop.lon]}
          icon={stop.id === selectedStop ? centerIcon : stopIcon}
        >
          <Popup>
            <div>
              <strong>Stop:</strong> {stop.name}<br />
              <strong>ID:</strong> {stop.id}<br />
              <strong>Sequence:</strong> {stop.sequence}<br />
              {stop.direction !== undefined && (
                <><strong>Direction:</strong> {stop.direction}<br /></>
              )}
              {stop.headsign && (
                <><strong>Destination:</strong> {stop.headsign}<br /></>
              )}
              <button 
                onClick={() => handleSetStartPoint([stop.lat, stop.lon])}
                style={{
                  padding: '5px',
                  margin: '5px 0',
                  backgroundColor: '#4CAF50',
                  color: 'white',
                  border: 'none',
                  borderRadius: '3px',
                  cursor: 'pointer'
                }}
              >
                Set as starting point
              </button>
            </div>
          </Popup>
        </Marker>
      ))}
    </>
  );
};

export default MarkersLayer;
