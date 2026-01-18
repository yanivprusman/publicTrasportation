import React, { createContext, useContext, useState, useCallback } from 'react';
import { fetchAddress, findRoute } from '../components/map/MapUtilities';
import { useTransport } from './TransportContext';

const MapContext = createContext();

export const useMap = () => useContext(MapContext);

export const MapProvider = ({ children }) => {
  const { mapCenter, setMapCenter, destination, setDestination } = useTransport();
  
  const [position, setPosition] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchError, setSearchError] = useState(null);
  const [positionAddress, setPositionAddress] = useState('');
  const [destinationAddress, setDestinationAddress] = useState('');
  const [route, setRoute] = useState(null);
  const [showRoutePanel, setShowRoutePanel] = useState(false);
  const [middlePoint, setMiddlePoint] = useState(null);
  
  const handleSearch = useCallback(async () => {
    if (!searchQuery.trim()) return;

    try {
      setSearchError(null);
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(searchQuery)}&format=json`
      );
      const data = await response.json();
      
      if (data.length > 0) {
        const { lat, lon } = data[0];
        setMapCenter([parseFloat(lat), parseFloat(lon)]);
      } else {
        setSearchError('Location not found.');
      }
    } catch (error) {
      setSearchError('Error fetching location. Please try again.');
    }
  }, [searchQuery, setMapCenter]);

  const handleKeyPress = useCallback((event) => {
    if (event.key === 'Enter') {
      handleSearch();
    }
  }, [handleSearch]);

  const handleSetStartPoint = useCallback(async (customPosition = null) => {
    const posToUse = customPosition || mapCenter;
    
    if (posToUse && Array.isArray(posToUse) && posToUse.length === 2) {
      setPosition([posToUse[0], posToUse[1]]);
      fetchAddress(posToUse[0], posToUse[1], setPositionAddress);
      console.log("Starting position updated to:", posToUse);
    }
  }, [mapCenter]);

  const handleSetDestinationPoint = useCallback(async () => {
    if (mapCenter && Array.isArray(mapCenter) && mapCenter.length === 2) {
      console.log("Setting destination to map center:", mapCenter);
      setDestination(mapCenter);
      fetchAddress(mapCenter[0], mapCenter[1], setDestinationAddress);
    }
  }, [mapCenter, setDestination]);

  const handleFindRoute = useCallback(async () => {
    console.log("Find route called with: position=", position, "destination=", destination);
    
    if (!destination || !destination[0] || !destination[1]) {
      console.error("Missing or invalid destination:", destination);
      setSearchError('Please set a destination first.');
      return null;
    }

    try {
      setSearchError(null);
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
  }, [position, destination]);

  const handleFindRouteClick = useCallback(async () => {
    if (destination) {
      console.log("Finding route from user position to destination");
      const routeData = await handleFindRoute();
      if (routeData) {
        setRoute(routeData);
        console.log("Setting route with", routeData.length, "points from", position, "to", destination);
      }
    } else {
      console.error("No destination set");
    }
  }, [destination, handleFindRoute, position]);

  const handleShowRoutePanel = useCallback(() => {
    setShowRoutePanel(true);
  }, []);

  const value = {
    position,
    searchQuery,
    searchError,
    positionAddress,
    destinationAddress,
    route,
    showRoutePanel,
    middlePoint,
    
    setPosition,
    setSearchQuery,
    setSearchError,
    setPositionAddress,
    setDestinationAddress,
    setRoute,
    setShowRoutePanel,
    setMiddlePoint,
    
    handleSearch,
    handleKeyPress,
    handleSetStartPoint,
    handleSetDestinationPoint,
    handleFindRoute,
    handleFindRouteClick,
    handleShowRoutePanel
  };

  return (
    <MapContext.Provider value={value}>
      {children}
    </MapContext.Provider>
  );
};
