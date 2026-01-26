import { useState } from 'react';
import axios from 'axios';
import { fetchAddress, findRoute } from '../components/map/MapUtilities';

/**
 * Hook that provides map-related handler functions
 */
const useMapHandlers = (position, setPosition, destination, onDestinationSet, setMapCenter, mapCenter) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchError, setSearchError] = useState(null);
  const [positionAddress, setPositionAddress] = useState('');
  const [destinationAddress, setDestinationAddress] = useState('');

  // Handle search input and query nominatim API
  const handleSearch = async () => {
    if (!searchQuery.trim()) return;

    try {
      setSearchError(null);
      const response = await axios.get(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(searchQuery)}&format=json`
      );
      if (response.data.length > 0) {
        const { lat, lon } = response.data[0];
        setMapCenter([parseFloat(lat), parseFloat(lon)]);
      } else {
        setSearchError('Location not found.');
      }
    } catch (error) {
      setSearchError('Error fetching location. Please try again.');
    }
  };

  // Set the starting point (defaults to current map center if no coords provided)
  const handleSetStartPoint = (coords) => {
    // If coords is a React event or not an array, use mapCenter
    const targetPoint = (Array.isArray(coords) && coords.length === 2) ? coords : mapCenter;

    if (targetPoint && Array.isArray(targetPoint) && targetPoint.length === 2) {
      setPosition([targetPoint[0], targetPoint[1]]);
      fetchAddress(targetPoint[0], targetPoint[1], setPositionAddress);
      console.log("Starting position updated to:", targetPoint);
    } else {
      console.error("Invalid point for starting point:", targetPoint);
    }
  };

  // Set the destination (defaults to current map center if no coords provided)
  const handleSetDestinationPoint = (coords) => {
    // If coords is a React event or not an array, use mapCenter
    const targetPoint = (Array.isArray(coords) && coords.length === 2) ? coords : mapCenter;

    if (targetPoint && Array.isArray(targetPoint) && targetPoint.length === 2) {
      console.log("Setting destination to:", targetPoint);
      onDestinationSet(targetPoint);
      fetchAddress(targetPoint[0], targetPoint[1], setDestinationAddress);
    } else {
      console.error("Invalid point for destination:", targetPoint);
    }
  };

  // Handle keyboard events for search
  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  // Find a route between position and destination
  const handleFindRoute = async () => {
    console.log("Find route called with: position=", position, "destination=", destination);

    if (!destination || !destination[0] || !destination[1]) {
      console.error("Missing or invalid destination:", destination);
      setSearchError('Please set a destination first.');
      return null;
    }

    try {
      setSearchError(null);
      // Explicitly use the current position, not map center
      const routeData = await findRoute(position, destination);

      if (routeData) {
        console.log("Route data received:", routeData.length, "points");
        return routeData;
      } else {
        setSearchError('No route found. Please try again with different locations.');
        return null;
      }
    } catch (error) {
      console.error("Error in findRoute:", error);
      setSearchError('Error fetching route. Please try again.');
      return null;
    }
  };

  return {
    searchQuery,
    setSearchQuery,
    searchError,
    setSearchError,  // Make sure we expose setSearchError in the return value
    positionAddress,
    destinationAddress,
    setPositionAddress,
    setDestinationAddress,
    handleSearch,
    handleSetStartPoint,
    handleSetDestinationPoint,
    handleKeyPress,
    handleFindRoute
  };
};

export default useMapHandlers;
