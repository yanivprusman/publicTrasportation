import React, { useState, useEffect } from 'react';
import './App.css';
import MapView from './MapView';
import StationArrivals from './components/data-display/StationArrivals';
import TransportControls from './components/controls/TransportControls';
import { fetchStationArrivals, extractVehicleMarkers, fetchLineShape } from './services/transport-api';

function CombinedTransportApp() {
  // Default locations:
  // אלרום רמת גן - Alrom in Ramat Gan
  const defaultStartingPoint = [32.0783, 34.8120];
  // המסגר 49 תל אביב - HaMasger 49 in Tel Aviv
  const defaultDestination = [32.0673, 34.7835];

  const [siriData, setSiriData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [stationCode, setStationCode] = useState('26472'); // Default station code
  const [lineNumber, setLineNumber] = useState('60'); // Default line number
  const [routeShape, setRouteShape] = useState(null);
  const [routeDirection, setRouteDirection] = useState('0'); // 0 = outbound, 1 = inbound

  // Add state to control vehicle markers visibility
  const [showVehicleMarkers, setShowVehicleMarkers] = useState(false);
  const [vehicleMarkers, setVehicleMarkers] = useState([]);

  // Starting and Destination points
  const [startingPoint, setStartingPoint] = useState(defaultStartingPoint);
  const [destination, setDestination] = useState(defaultDestination);

  // Map state
  const [mapCenter, setMapCenter] = useState(defaultStartingPoint); // Use default starting point as initial center
  const [stops, setStops] = useState([]);

  // Add this state variable
  const [calculateRoute, setCalculateRoute] = useState(false);

  const fetchStationData = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await fetchStationArrivals(stationCode);
      setSiriData(data);

      // Extract vehicle markers from SIRI data
      const markers = extractVehicleMarkers(data);
      setVehicleMarkers(markers);

      setLoading(false);
    } catch (err) {
      console.error('Error fetching arrivals:', err);
      setError(`Connection error: ${err.message}. Make sure the API server is running.`);
      setLoading(false);
    }
  };

  const handleFindRoute = async () => {
    if (!destination) {
      setError("Please set a destination first");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Use the actual starting point instead of map center or hardcoded default
      const startPoint = startingPoint;
      console.log("Finding route from", startPoint, "to", destination);

      // Trigger the route finding in MapView
      setCalculateRoute(true);

      setLoading(false);
    } catch (err) {
      console.error('Error finding route:', err);
      setError(`Error finding route: ${err.message}`);
      setLoading(false);
    }
  };

  const handleFetchLineShape = async () => {
    setLoading(true);
    setError(null);
    try {
      if (!lineNumber.trim()) {
        setError("Please enter a valid line number");
        setLoading(false);
        return;
      }

      console.log(`Fetching shape for line ${lineNumber} in direction ${routeDirection}`);

      try {
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
        console.error("Error fetching or processing shape data:", err);
        setError(`Failed to load route shape: ${err.message}`);
      }
    } catch (err) {
      console.error('Error in handleFetchLineShape:', err);
      setError(`Connection error: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Fetch initial data
    fetchStationData();
  }, [stationCode]);

  return (
    <div className="combined-app">
      <div className="app-header">
        <h1>Israel Public Transportation Tracker</h1>
      </div>

      <TransportControls
        stationCode={stationCode}
        setStationCode={setStationCode}
        fetchStationData={fetchStationData}
        lineNumber={lineNumber}
        setLineNumber={setLineNumber}
        routeDirection={routeDirection}
        setRouteDirection={setRouteDirection}
        fetchLineShape={handleFetchLineShape}
        showVehicleMarkers={showVehicleMarkers}
        setShowVehicleMarkers={setShowVehicleMarkers}
        handleFindRoute={handleFindRoute}
      />

      <div className="main-content">
        <div className="map-section">
          <MapView
            latitude={mapCenter[0]}
            longitude={mapCenter[1]}
            mapCenter={mapCenter}
            setMapCenter={setMapCenter}
            vehicleMarkers={showVehicleMarkers ? vehicleMarkers : []}
            routeShape={routeShape}
            stops={stops}
            onDestinationSet={setDestination}
            destination={destination}
            startingPoint={startingPoint}
            onStartingPointSet={setStartingPoint}
            center={mapCenter}
            defaultStartingPoint={defaultStartingPoint}
            defaultDestination={defaultDestination}
            calculateRoute={calculateRoute}
            onRouteCalculated={() => setCalculateRoute(false)}
          />
        </div>
        <div className="data-section">
          <StationArrivals
            siriData={siriData}
            loading={loading}
            error={error}
            stationCode={stationCode}
          />
        </div>
      </div>
    </div>
  );
}

export default CombinedTransportApp;
