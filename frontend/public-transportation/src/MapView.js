import { useEffect, useState, useMemo } from 'react';
import './App.css';
import { MapContainer, TileLayer } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { TrackMapMovement, UpdateMapView, fetchAddress, MapContextMenu } from './components/map/MapUtilities';
import { configureDefaultLeafletIcons } from './components/map/MapMarkers';
import MapControls from './components/map/MapControls';
import useMapHandlers from './hooks/useMapHandlers';
import MapEffect from './components/map/MapEffect';
import RouteLayer from './components/map/RouteLayer';
import MarkersLayer from './components/map/MarkersLayer';
import MapControlPanel from './components/map/MapControlPanel';

// Configure default Leaflet icons
configureDefaultLeafletIcons();

function MapView({
  latitude,
  longitude,
  destination,
  onDestinationSet,
  startingPoint,
  mapCenter: initialMapCenter,
  setMapCenter,
  vehicleMarkers = [],
  routeShape = null,
  stops = [],
  selectedStop = null,
  center,
  defaultStartingPoint,
  defaultDestination,
  calculateRoute,
  onRouteCalculated,
  onStartingPointSet
}) {
  // Use provided starting point or fallback to initial coords
  const [position, setPosition] = useState(startingPoint || defaultStartingPoint || [latitude, longitude]);
  const [route, setRoute] = useState(null);
  const [mapReady, setMapReady] = useState(false);
  const [mapCenter, setMapCenterLocal] = useState(center || initialMapCenter || [latitude, longitude]);
  const [showRoutePanel, setShowRoutePanel] = useState(false);
  // Add state for middle point
  const [middlePoint, setMiddlePoint] = useState(null);

  // Update external map center when local state changes
  useEffect(() => {
    if (setMapCenter) {
      setMapCenter(mapCenter);
    }
  }, [mapCenter, setMapCenter]);

  // Set the default destination if provided and no destination is set
  useEffect(() => {
    if (defaultDestination && (!destination || !destination[0])) {
      onDestinationSet(defaultDestination);
    }
  }, [defaultDestination, destination, onDestinationSet]);

  // Use custom hook for map handlers
  const {
    searchQuery,
    setSearchQuery,
    searchError,
    setSearchError,  // Add this line to extract setSearchError from the hook
    positionAddress,
    destinationAddress,
    setPositionAddress,
    setDestinationAddress,
    handleSearch,
    handleSetStartPoint,
    handleSetDestinationPoint,
    handleKeyPress,
    handleFindRoute
  } = useMapHandlers(position, (newPos) => {
    setPosition(newPos);
    if (onStartingPointSet) onStartingPointSet(newPos);
  }, destination, onDestinationSet, setMapCenterLocal, mapCenter);

  // When destination changes, ensure it's properly set
  useEffect(() => {
    if (destination && Array.isArray(destination) && destination.length === 2) {
      // Destination is valid
      fetchAddress(destination[0], destination[1], setDestinationAddress);
    }
  }, [destination]);

  // When position changes
  useEffect(() => {
    if (position && Array.isArray(position) && position.length === 2) {
      fetchAddress(position[0], position[1], setPositionAddress);
    }
  }, [position]);

  // Add effect to respond to calculateRoute changes
  useEffect(() => {
    if (calculateRoute && position && destination) {
      // Actually calculate the route using the position as starting point
      handleFindRouteClick();

      // Let parent know we've processed the request
      if (onRouteCalculated) {
        onRouteCalculated();
      }
    }
  }, [calculateRoute, position, destination, onRouteCalculated]);

  // When startingPoint prop changes from outside
  useEffect(() => {
    if (startingPoint && Array.isArray(startingPoint) && startingPoint.length === 2) {
      if (startingPoint[0] !== position[0] || startingPoint[1] !== position[1]) {
        setPosition(startingPoint);
      }
    }
  }, [startingPoint, position]);

  // Log routeShape when it changes to debug
  useEffect(() => {
    if (routeShape) {
      console.log("Route shape received in MapView:", routeShape.length, "points");
    }
  }, [routeShape]);

  // If we have a proper route shape, show the route panel with RouteMapView
  const handleShowRoutePanel = () => {
    if (routeShape && routeShape.length > 0) {
      setShowRoutePanel(true);
    }
  };

  // Handle clicking the "Find Route" button
  const handleFindRouteClick = async () => {
    if (destination) {
      // Make sure we're using the current user position, not map center
      console.log("Finding route from user position to destination");
      const routeData = await handleFindRoute();
      if (routeData) {
        setRoute(routeData);
        console.log("Setting route with", routeData.length, "points from", position, "to", destination);
      }
    } else {
      console.error("No destination set");
    }
  };

  // Function to calculate and set the middle point of line 60's route
  const handleShowMiddlePoint = async () => {
    try {
      setSearchError('Fetching Line 60 route data...');

      // First approach: Use route shape points if available
      if (routeShape && routeShape.length > 0) {
        const middleIndex = Math.floor(routeShape.length / 2);
        const calculatedMiddlePoint = routeShape[middleIndex];
        setMiddlePoint(calculatedMiddlePoint);

        // Log detailed information to console for debugging
        console.group("Line 60 Middle Point Information");
        console.log("Middle point coordinates:", calculatedMiddlePoint);
        console.log("Total route points:", routeShape.length);
        console.log("Middle point index:", middleIndex);
        console.log("Method used: Route shape");
        console.groupEnd();

        // Update map center to show the middle point
        setMapCenterLocal(calculatedMiddlePoint);
        return;
      }

      // Second approach: Fetch stops data for Line 60
      const response = await fetch('/api/stops-data.php?line=60');
      const data = await response.json();

      if (data.stops && data.stops.length > 0) {
        // Get the middle stop from the stops array
        const stops = data.stops;
        const middleIndex = Math.floor(stops.length / 2);
        const middleStop = stops[middleIndex];
        const calculatedMiddlePoint = [middleStop.lat, middleStop.lon];

        setMiddlePoint(calculatedMiddlePoint);

        // Log detailed information about the middle stop to console
        console.group("Line 60 Middle Stop Information");
        console.log("Middle stop:", middleStop.name);
        console.log("Stop ID:", middleStop.id);
        console.log("Coordinates:", calculatedMiddlePoint);
        console.log("Stop sequence:", middleStop.sequence);
        console.log("Position in route:", `${middleIndex + 1} of ${stops.length} stops`);
        console.log("Method used: Stops data");

        // Log all stops for detailed analysis
        console.log("All stops on route:", stops);
        console.groupEnd();

        // Update map center to show the middle stop
        setMapCenterLocal(calculatedMiddlePoint);
      } else {
        // Third approach: Hardcode known middle points for Line 60
        const hardcodedMiddlePoint = [32.0729, 34.8046]; // Middle point for Line 60 in Ramat Gan
        setMiddlePoint(hardcodedMiddlePoint);

        console.group("Line 60 Middle Point Information");
        console.log("Middle point coordinates (hardcoded):", hardcodedMiddlePoint);
        console.log("Method used: Hardcoded coordinates");
        console.log("Note: This is an approximation as API data was unavailable");
        console.groupEnd();

        // Update map center to show the hardcoded middle point
        setMapCenterLocal(hardcodedMiddlePoint);
      }
    } catch (error) {
      console.error('Error finding middle point:', error);
      setSearchError('Error finding middle point');

      // Still provide some fallback coordinates in case of error
      const fallbackPoint = [32.0729, 34.8046]; // Known approximate middle point for Line 60
      setMiddlePoint(fallbackPoint);
      setMapCenterLocal(fallbackPoint);

      setTimeout(() => setSearchError(null), 3000);
    }
  };

  // Helper function to validate coordinates are in Israel
  const isInIsrael = (lat, lon) => {
    // Rough bounding box for Israel
    return lat >= 29.5 && lat <= 33.3 && lon >= 34.2 && lon <= 35.9;
  };

  // Better middle point calculation using route distance
  const findRouteMiddlePoint = (routePoints) => {
    if (!routePoints || routePoints.length < 2) {
      setSearchError('Route has insufficient points');
      return;
    }

    // Filter out any points that are clearly outside Israel
    const validPoints = routePoints.filter(point =>
      isInIsrael(point[0], point[1])
    );

    if (validPoints.length < routePoints.length) {
      console.warn(`Filtered out ${routePoints.length - validPoints.length} points outside Israel`);
    }

    if (validPoints.length < 2) {
      setSearchError('No valid route points in Israel');
      return;
    }

    // Calculate the cumulative distances along the route
    let totalDistance = 0;
    const distances = [0]; // First point has 0 distance

    for (let i = 1; i < validPoints.length; i++) {
      const p1 = validPoints[i - 1];
      const p2 = validPoints[i];

      // Calculate the distance between consecutive points
      const d = Math.sqrt(
        Math.pow(p2[0] - p1[0], 2) +
        Math.pow(p2[1] - p1[1], 2)
      );

      totalDistance += d;
      distances.push(totalDistance);
    }

    // Find the middle distance point
    const middleDistance = totalDistance / 2;

    // Find the segment containing the middle distance
    let segmentIndex = 0;
    while (segmentIndex < distances.length - 1 && distances[segmentIndex + 1] < middleDistance) {
      segmentIndex++;
    }

    // Calculate the exact middle point using interpolation
    let calculatedMiddlePoint;

    if (segmentIndex < validPoints.length - 1) {
      const p1 = validPoints[segmentIndex];
      const p2 = validPoints[segmentIndex + 1];

      const segmentLength = distances[segmentIndex + 1] - distances[segmentIndex];
      const ratio = segmentLength ? (middleDistance - distances[segmentIndex]) / segmentLength : 0;

      calculatedMiddlePoint = [
        p1[0] + ratio * (p2[0] - p1[0]),
        p1[1] + ratio * (p2[1] - p1[1])
      ];
    } else {
      // Fallback to the middle point if calculation fails
      calculatedMiddlePoint = validPoints[Math.floor(validPoints.length / 2)];
    }

    // Verify the calculated point is in Israel
    if (!isInIsrael(calculatedMiddlePoint[0], calculatedMiddlePoint[1])) {
      console.error("Calculated middle point is outside Israel, using midpoint of array instead");
      calculatedMiddlePoint = validPoints[Math.floor(validPoints.length / 2)];
    }

    // Set the middle point and log to console
    setMiddlePoint(calculatedMiddlePoint);
    console.log("Line 60 middle point:", calculatedMiddlePoint);

    // Update map to show the middle point
    setMapCenterLocal(calculatedMiddlePoint);
  };

  // Memoize optimized route shape to avoid unnecessary recalculations
  const optimizedRouteShape = useMemo(() => {
    return routeShape;
  }, [routeShape]);

  return (
    <div style={{ height: '100vh', width: '100%' }}>
      {/* Search and controls */}
      <MapControls
        searchQuery={searchQuery}
        setSearchQuery={setSearchQuery}
        handleSearch={handleSearch}
        handleKeyPress={handleKeyPress}
        handleSetStartPoint={handleSetStartPoint}
        handleSetDestinationPoint={handleSetDestinationPoint}
        searchError={searchError}
        positionAddress={positionAddress}
        destinationAddress={destinationAddress}
      />

      {/* Middle point button for line 60 */}
      <button
        onClick={handleShowMiddlePoint}
        style={{
          position: 'absolute',
          bottom: '60px',
          left: '10px',
          zIndex: 1000,
          padding: '8px 12px',
          backgroundColor: '#9c27b0',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
        title="Show middle point of Line 60 route"
      >
        Show Line 60 Middle Point
      </button>

      {/* Route panel and controls */}
      <MapControlPanel
        showRoutePanel={showRoutePanel}
        setShowRoutePanel={setShowRoutePanel}
        optimizedRouteShape={optimizedRouteShape}
        handleShowRoutePanel={handleShowRoutePanel}
      />

      <MapContainer
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
        <MapContextMenu
          onSetStart={handleSetStartPoint}
          onSetDestination={handleSetDestinationPoint}
        />
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
          middlePoint={middlePoint}  // Pass the middle point to MarkersLayer
        />
      </MapContainer>
    </div>
  );
}

export default MapView;