import { useEffect, useState } from 'react';
import { useMapEvents, useMap } from 'react-leaflet';
import axios from 'axios';

export function TrackMapMovement({ setMapCenter }) {
  useMapEvents({
    moveend: (event) => {
      const map = event.target;
      const center = map.getCenter();
      setMapCenter([center.lat, center.lng]);
    },
  });
  return null;
}

export function MapContextMenu({ onSetStart, onSetDestination }) {
  const [contextMenu, setContextMenu] = useState(null);

  useMapEvents({
    contextmenu: (e) => {
      e.originalEvent.preventDefault();
      setContextMenu({
        lat: e.latlng.lat,
        lng: e.latlng.lng,
        x: e.originalEvent.clientX,
        y: e.originalEvent.clientY
      });
    },
    click: () => {
      if (contextMenu) setContextMenu(null);
    },
    dragstart: () => {
      if (contextMenu) setContextMenu(null);
    },
    zoomstart: () => {
      if (contextMenu) setContextMenu(null);
    }
  });

  if (!contextMenu) return null;

  const handleSetStart = () => {
    onSetStart([contextMenu.lat, contextMenu.lng]);
    setContextMenu(null);
  };

  const handleSetDestination = () => {
    onSetDestination([contextMenu.lat, contextMenu.lng]);
    setContextMenu(null);
  };

  return (
    <div
      className="map-context-menu"
      style={{
        position: 'fixed',
        top: contextMenu.y,
        left: contextMenu.x,
        zIndex: 10000,
        backgroundColor: 'white',
        border: '1px solid #ccc',
        borderRadius: '4px',
        boxShadow: '0 2px 10px rgba(0,0,0,0.2)',
        padding: '5px 0',
        minWidth: '150px'
      }}
    >
      <div
        onClick={handleSetStart}
        style={{
          padding: '8px 15px',
          cursor: 'pointer',
          fontSize: '14px',
          transition: 'background 0.2s'
        }}
        onMouseOver={(e) => e.target.style.backgroundColor = '#f0f0f0'}
        onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
      >
        🚩 Set as Start
      </div>
      <div
        onClick={handleSetDestination}
        style={{
          padding: '8px 15px',
          cursor: 'pointer',
          fontSize: '14px',
          transition: 'background 0.2s'
        }}
        onMouseOver={(e) => e.target.style.backgroundColor = '#f0f0f0'}
        onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
      >
        📍 Set as Destination
      </div>
      <div
        onClick={() => setContextMenu(null)}
        style={{
          padding: '8px 15px',
          borderTop: '1px solid #eee',
          cursor: 'pointer',
          fontSize: '14px',
          color: '#666'
        }}
        onMouseOver={(e) => e.target.style.backgroundColor = '#f0f0f0'}
        onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
      >
        Cancel
      </div>
    </div>
  );
}

export function UpdateMapView({ position }) {
  const map = useMap();
  useEffect(() => {
    map.setView(position, map.getZoom());
  }, [position, map]);
  return null;
}

export const simplifyAddress = (address) => {
  const parts = address.split(',').slice(0, 2);
  return parts.join(',').trim();
};

export const fetchAddress = async (lat, lon, setAddress) => {
  try {
    const response = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
    );
    const fullAddress = response.data.display_name || 'Address not found';
    setAddress(simplifyAddress(fullAddress));
  } catch (error) {
    console.error('Error fetching address:', error);
    setAddress('Error fetching address');
  }
};

export const findRoute = async (position, destination) => {
  try {
    console.log("Finding route from:", position, "to:", destination);

    // Validate input coordinates
    if (!position || !position[0] || !position[1] ||
      !destination || !destination[0] || !destination[1]) {
      console.error("Invalid coordinates for routing:", { position, destination });
      return [position, destination]; // Return direct line as fallback
    }

    // First try to get a direct route
    const response = await axios.get(
      `https://api.openrouteservice.org/v2/directions/driving-car`,
      {
        params: {
          api_key: '5b3ce3597851110001cf62489ba3c6e8d4824449975eca1f205d2eec',
          start: `${position[1]},${position[0]}`,
          end: `${destination[1]},${destination[0]}`,
        },
      }
    );

    if (response.data && response.data.features && response.data.features.length > 0) {
      const routeGeometry = response.data.features[0].geometry.coordinates;
      const formattedRoute = routeGeometry.map(([lon, lat]) => [lat, lon]);
      console.log(`Found route with ${formattedRoute.length} points`);
      return formattedRoute;
    }

    // If no route is found, create a simple straight line from position to destination
    console.log("No route found via API, creating direct line");
    return [
      [position[0], position[1]],
      [destination[0], destination[1]]
    ];
  } catch (error) {
    console.error('Error finding route:', error);
    // Return a fallback straight line route on error
    return [
      [position[0], position[1]],
      [destination[0], destination[1]]
    ];
  }
};
