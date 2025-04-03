import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Polyline, useMap, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// This component will fit the map to the route bounds
const FitToBounds = ({ points }) => {
  const map = useMap();
  
  useEffect(() => {
    if (points && points.length > 0) {
      try {
        const bounds = points.reduce(
          (bounds, point) => bounds.extend([point[0], point[1]]), 
          L.latLngBounds(points[0], points[0])
        );
        map.fitBounds(bounds, { 
          padding: [50, 50],
          maxZoom: 15
        });
        console.log("Map fitted to route bounds");
      } catch (e) {
        console.error("Error fitting to bounds:", e);
      }
    }
  }, [map, points]);
  
  return null;
};

const RouteMapView = ({ routeShape }) => {
  const defaultCenter = [32.0, 35.0]; // Approximate center of Israel
  
  // Log some debugging info
  useEffect(() => {
    if (routeShape && routeShape.length > 0) {
      console.log(`Route shape received in RouteMapView: ${routeShape.length} points`);
      console.log(`First point: ${JSON.stringify(routeShape[0])}`);
      console.log(`Last point: ${JSON.stringify(routeShape[routeShape.length - 1])}`);
    }
  }, [routeShape]);

  return (
    <div className="w-full h-full" style={{ minHeight: "500px" }}>
      <MapContainer 
        center={defaultCenter} 
        zoom={8} 
        style={{ height: '100%', width: '100%' }}
        preferCanvas={true}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        
        {/* Start and end markers */}
        {routeShape && routeShape.length > 1 && (
          <>
            <Marker position={routeShape[0]}>
              <Popup>Start point</Popup>
            </Marker>
            <Marker position={routeShape[routeShape.length-1]}>
              <Popup>End point</Popup>
            </Marker>
          </>
        )}
        
        {routeShape && routeShape.length > 0 && (
          <>
            <Polyline 
              positions={routeShape}
              pathOptions={{ 
                color: 'blue',
                weight: 4,
                opacity: 0.7
              }} 
            />
            <FitToBounds points={routeShape} />
          </>
        )}
      </MapContainer>
    </div>
  );
};

export default RouteMapView;
