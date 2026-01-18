import React from 'react';
import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet';
import { UpdateMapView, TrackMapMovement } from './MapUtilities';
import RouteLayer from './RouteLayer';
import MarkersLayer from './MarkersLayer';
import MapEffect from './MapEffect';

/**
 * Container component for the Leaflet map
 */
const MapContainer = ({ 
  mapCenter, 
  setMapCenterLocal,
  routeShape,
  route,
  position,
  positionAddress,
  destination,
  destinationAddress,
  vehicleMarkers,
  stops,
  selectedStop,
  handleSetStartPoint,
  middlePoint,
  optimizedRouteShape,
  mapReady,
  setMapReady
}) => {
  return (
    <LeafletMapContainer 
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
      <MapEffect routeShape={optimizedRouteShape} />
      
      {/* All route polylines */}
      <RouteLayer 
        routeShape={routeShape}
        route={route}
      />
      
      {/* All map markers */}
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
    </LeafletMapContainer>
  );
};

export default MapContainer;
