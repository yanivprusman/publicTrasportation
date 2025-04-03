import { useEffect } from 'react';
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
