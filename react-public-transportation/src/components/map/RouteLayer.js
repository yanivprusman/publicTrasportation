import React, { useMemo } from 'react';
import { Polyline } from 'react-leaflet';
import { simplifyShape } from '../../utils/ShapeSimplifier';

/**
 * Displays route polylines on the map
 */
const RouteLayer = ({ routeShape, route }) => {
  // Memoize and simplify the route shape to improve performance
  const optimizedRouteShape = useMemo(() => {
    if (!routeShape || !Array.isArray(routeShape) || routeShape.length < 3) {
      return routeShape;
    }
    
    console.log(`Simplifying route shape with ${routeShape.length} points...`);
    
    // Determine if we need to simplify (only for large routes)
    if (routeShape.length > 300) {
      // Stronger simplification for very large routes
      const factor = routeShape.length > 1000 ? 0.00015 : 0.0001;
      const simplified = simplifyShape(routeShape, factor);
      console.log(`Simplified to ${simplified.length} points (${Math.round(simplified.length/routeShape.length*100)}%)`);
      return simplified;
    }
    
    return routeShape;
  }, [routeShape]);

  return (
    <>
      {/* Route from findRoute (blue solid line) */}
      {route && route.length > 0 && (
        <Polyline 
          positions={route} 
          pathOptions={{ 
            color: 'blue', 
            weight: 4, 
            opacity: 0.8 
          }} 
        />
      )}
      
      {/* Public transportation route shape (red dashed line) */}
      {optimizedRouteShape && optimizedRouteShape.length > 0 && (
        <Polyline 
          positions={optimizedRouteShape} 
          pathOptions={{ 
            color: 'red', 
            weight: 3, 
            opacity: 0.7,
            dashArray: '5, 5',
            lineCap: 'round'
          }} 
        />
      )}
    </>
  );
};

export default RouteLayer;
