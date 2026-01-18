import React, { useState } from 'react';

/**
 * Component for handling middle point functionality
 */
const MiddlePointHandler = ({ 
  routeShape, 
  route, 
  setMiddlePoint, 
  setMapCenterLocal,
  setSearchError 
}) => {
  // Helper function to validate coordinates are in Israel
  const isInIsrael = (lat, lon) => {
    return lat >= 29.5 && lat <= 33.3 && lon >= 34.2 && lon <= 35.9;
  };

  // Handler for middle point calculation button
  const handleShowMiddlePoint = async () => {
    try {
      // Check if we have a route or route shape
      const routePoints = route || routeShape;
      
      if (!routePoints || routePoints.length === 0) {
        console.log("No route available to find middle point");
        setSearchError('Please find a route first before showing the middle point');
        setTimeout(() => setSearchError(null), 3000);
        return;
      }
      
      findRouteMiddlePoint(routePoints);
    } catch (error) {
      console.error('Error finding middle point:', error);
      setSearchError('Error finding middle point');
      setTimeout(() => setSearchError(null), 3000);
    }
  };

  // Calculate middle point from route
  const findRouteMiddlePoint = (routePoints) => {
    if (!routePoints || routePoints.length < 2) {
      setSearchError('Route has insufficient points');
      return;
    }

    // Filter out any points that are clearly outside Israel
    const validPoints = routePoints.filter(point => 
      isInIsrael(point[0], point[1])
    );

    if (validPoints.length < 2) {
      setSearchError('No valid route points in Israel');
      return;
    }

    // Calculate the middle point
    const middleIndex = Math.floor(validPoints.length / 2);
    let calculatedMiddlePoint;
    
    // Calculate the exact middle point
    if (validPoints.length % 2 === 1) {
      // Odd number of points - take the middle one
      calculatedMiddlePoint = validPoints[middleIndex];
    } else {
      // Even number of points - average the two middle points
      const midPoint1 = validPoints[middleIndex - 1];
      const midPoint2 = validPoints[middleIndex];
      calculatedMiddlePoint = [
        (midPoint1[0] + midPoint2[0]) / 2,
        (midPoint1[1] + midPoint2[1]) / 2
      ];
    }
    
    // Set the middle point and log to console
    setMiddlePoint(calculatedMiddlePoint);
    console.log("Line 60 middle point:", calculatedMiddlePoint);

    // Update map to show the middle point
    setMapCenterLocal(calculatedMiddlePoint);
  };

  return (
    <button
      onClick={handleShowMiddlePoint}
      disabled={!route && !routeShape}
      style={{
        position: 'absolute',
        bottom: '60px',
        left: '10px',
        zIndex: 1000,
        padding: '8px 12px',
        backgroundColor: (!route && !routeShape) ? '#cccccc' : '#9c27b0',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: (!route && !routeShape) ? 'not-allowed' : 'pointer'
      }}
      title={(!route && !routeShape) ? 'Find a route first to enable this feature' : 'Show the middle point of the current route'}
    >
      Show Line 60 Middle Point
    </button>
  );
};

export default MiddlePointHandler;
