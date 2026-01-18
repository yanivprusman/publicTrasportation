import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';

/**
 * Component that handles map effects like fitting to bounds
 */
const MapEffect = ({ routeShape }) => {
  const map = useMap();
  const fittedRef = useRef(false);
  const routeIdRef = useRef(null);
  const processingRef = useRef(false);
  
  // Generate a unique ID for the current route to detect changes
  const currentRouteId = routeShape ? JSON.stringify(routeShape.slice(0, 2)) : null;
  
  useEffect(() => {
    // Skip if processing is in progress or if this is the same route
    if (processingRef.current || 
       (routeIdRef.current === currentRouteId && fittedRef.current)) {
      return;
    }
    
    // Only fit bounds if we have a valid route shape with points
    if (routeShape && routeShape.length > 0) {
      processingRef.current = true;
      
      try {
        // Use setTimeout to give the browser a chance to breathe
        setTimeout(() => {
          try {
            // Create a bounds object from the route shape
            const bounds = routeShape.reduce((bounds, point) => {
              bounds.extend(point);
              return bounds;
            }, L.latLngBounds(routeShape[0], routeShape[0]));
            
            // Fit the map to these bounds with some padding
            map.fitBounds(bounds, { 
              padding: [50, 50],
              maxZoom: 15, // Limit max zoom level
              animate: true
            });
            console.log("Map fitted to route bounds");
            
            // Mark that we've fitted bounds for this route
            fittedRef.current = true;
            routeIdRef.current = currentRouteId;
          } catch (e) {
            console.error("Error fitting map to route:", e);
          } finally {
            processingRef.current = false;
          }
        }, 100);
      } catch (e) {
        console.error("Error in MapEffect timeout:", e);
        processingRef.current = false;
      }
    }
  }, [map, routeShape, currentRouteId]);
  
  // Clean up function
  useEffect(() => {
    return () => {
      processingRef.current = false;
    };
  }, []);
  
  return null;
};

export default MapEffect;
