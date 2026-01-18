import { useState, useEffect, useMemo } from 'react';
import { fetchLineShape } from '../services/gtfs-service';

/**
 * Custom hook for handling routes and route-related operations
 */
const useRouteHandler = (position, destination, handleFindRoute, propRouteShape) => {
  const [route, setRoute] = useState(null);
  const [lineShape, setLineShape] = useState(null);
  const [routeShape, setRouteShape] = useState(null);
  const [showRoutePanel, setShowRoutePanel] = useState(false);

  // Fetch line shape when component mounts
  useEffect(() => {
    const fetchShape = async () => {
      try {
        const shapeData = await fetchLineShape('60');
        const firstShape = Object.values(shapeData)[0]; // Use the first shape
        setLineShape(firstShape);
        console.log('Line 60 shape data:', shapeData);
      } catch (error) {
        console.error('Failed to fetch line shape:', error);
      }
    };
    fetchShape();
  }, []);

  // Add proper initialization of routeShape
  useEffect(() => {
    // Set route shape from props when available
    if (propRouteShape && propRouteShape.length > 0) {
      setRouteShape(propRouteShape);
    }
  }, [propRouteShape]);

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

  // If we have a proper route shape, show the route panel with RouteMapView
  const handleShowRoutePanel = () => {
    if (routeShape && routeShape.length > 0) {
      setShowRoutePanel(true);
    }
  };

  // Memoize optimized route shape
  const optimizedRouteShape = useMemo(() => {
    return routeShape;
  }, [routeShape]);

  return {
    route,
    setRoute,
    lineShape,
    routeShape,
    setRouteShape,
    showRoutePanel,
    setShowRoutePanel,
    handleFindRouteClick,
    handleShowRoutePanel,
    optimizedRouteShape
  };
};

export default useRouteHandler;
