import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { fetchStationArrivals, extractVehicleMarkers, fetchLineShape } from '../services/transport-api';

const TransportContext = createContext();

export const useTransport = () => useContext(TransportContext);

export const TransportProvider = ({ children }) => {
  // Default locations with clear comments
  // אלרום רמת גן - Alrom in Ramat Gan
  const defaultStartingPoint = [32.0783, 34.8120];
  // המסגר 49 תל אביב - HaMasger 49 in Tel Aviv
  const defaultDestination = [32.0673, 34.7835]; 
  
  // State management
  const [siriData, setSiriData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [stationCode, setStationCode] = useState('26472');
  const [lineNumber, setLineNumber] = useState('60');
  const [routeShape, setRouteShape] = useState(null);
  const [routeDirection, setRouteDirection] = useState('0');
  const [showVehicleMarkers, setShowVehicleMarkers] = useState(false);
  const [vehicleMarkers, setVehicleMarkers] = useState([]);
  const [mapCenter, setMapCenter] = useState(defaultStartingPoint);
  const [stops, setStops] = useState([]);
  const [destination, setDestination] = useState(defaultDestination);
  const [calculateRoute, setCalculateRoute] = useState(false);

  const fetchStationData = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const data = await fetchStationArrivals(stationCode);
      setSiriData(data);
      
      // Extract vehicle markers from SIRI data
      const markers = extractVehicleMarkers(data);
      setVehicleMarkers(markers);
    } catch (err) {
      console.error('Error fetching arrivals:', err);
      setError(`Connection error: ${err.message}. Make sure the API server is running.`);
    } finally {
      setLoading(false);
    }
  }, [stationCode]);

  const handleFindRoute = useCallback(async () => {
    if (!destination) {
      setError("Please set a destination first");
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      console.log("Finding navigation route from", defaultStartingPoint, "to", destination);
      setCalculateRoute(true);
    } catch (err) {
      console.error('Error finding route:', err);
      setError(`Error finding route: ${err.message}`);
    } finally {
      setLoading(false);
    }
  }, [destination, defaultStartingPoint]);

  const handleFetchLineShape = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      if (!lineNumber.trim()) {
        setError("Please enter a valid line number");
        setLoading(false);
        return;
      }
      
      console.log(`Fetching shape for line ${lineNumber} in direction ${routeDirection}`);
      
      const data = await fetchLineShape(lineNumber);
      console.log("Shape data received:", data);
      
      // Check if we have data for the selected direction
      if (data[routeDirection] && Array.isArray(data[routeDirection]) && data[routeDirection].length > 0) {
        console.log(`Using direction ${routeDirection} with ${data[routeDirection].length} points`);
        setRouteShape(data[routeDirection]);
        
        // Set map center to first point of the shape
        if (data[routeDirection][0] && data[routeDirection][0].length === 2) {
          setMapCenter(data[routeDirection][0]);
        }
      } 
      // Fall back to the other direction if the selected one has no data
      else if (data['0'] && Array.isArray(data['0']) && data['0'].length > 0) {
        console.log(`Falling back to direction 0 with ${data['0'].length} points`);
        setRouteShape(data['0']);
        setMapCenter(data['0'][0]);
        // Update the direction selector to match the actual data
        setRouteDirection('0');
      }
      else if (data['1'] && Array.isArray(data['1']) && data['1'].length > 0) {
        console.log(`Falling back to direction 1 with ${data['1'].length} points`);
        setRouteShape(data['1']);
        setMapCenter(data['1'][0]);
        // Update the direction selector to match the actual data
        setRouteDirection('1');
      }
      else {
        throw new Error("No valid shape data found in the API response");
      }
    } catch (err) {
      console.error('Error fetching or processing line shape:', err);
      setError(`Failed to load route shape: ${err.message}`);
    } finally {
      setLoading(false);
    }
  }, [lineNumber, routeDirection]);

  // Fetch initial data when component mounts or station code changes
  useEffect(() => {
    fetchStationData();
  }, [fetchStationData]);

  // Memoize value to prevent unnecessary re-renders
  const value = useMemo(() => ({
    // Constants
    defaultStartingPoint,
    defaultDestination,
    
    // State
    siriData,
    loading,
    error,
    stationCode,
    lineNumber,
    routeShape,
    routeDirection,
    showVehicleMarkers,
    vehicleMarkers,
    mapCenter,
    stops,
    destination,
    calculateRoute,
    
    // Setters
    setStationCode,
    setLineNumber,
    setRouteDirection,
    setShowVehicleMarkers,
    setMapCenter,
    setDestination,
    setCalculateRoute,
    
    // Actions
    fetchStationData,
    handleFindRoute,
    handleFetchLineShape
  }), [
    // Dependencies
    siriData,
    loading,
    error,
    stationCode,
    lineNumber,
    routeShape,
    routeDirection,
    showVehicleMarkers,
    vehicleMarkers,
    mapCenter,
    stops,
    destination,
    calculateRoute,
    fetchStationData,
    handleFindRoute,
    handleFetchLineShape
  ]);

  return (
    <TransportContext.Provider value={value}>
      {children}
    </TransportContext.Provider>
  );
};
