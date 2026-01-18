import L from 'leaflet';

/**
 * Simplifies a polyline by reducing the number of points
 * Uses the Ramer-Douglas-Peucker algorithm
 * @param {Array} points - Array of [lat, lng] coordinates
 * @param {Number} tolerance - Higher values mean more simplification
 * @returns {Array} - Simplified array of points
 */
export function simplifyShape(points, tolerance = 0.0001) {
  if (!points || points.length <= 2) return points;
  
  // Create map instance at the beginning so it's available to all inner functions
  const dummyMap = L.map(document.createElement('div'));
  
  const rdp = (pts, start, end, epsilon) => {
    // Find the point with the maximum distance
    let dmax = 0;
    let index = 0;
    
    const line = L.polyline([pts[start], pts[end]]);
    const lineLatLngs = line.getLatLngs();
    
    for (let i = start + 1; i < end; i++) {
      const point = L.latLng(pts[i]);
      const distance = L.GeometryUtil.distanceSegment(dummyMap, point, lineLatLngs[0], lineLatLngs[1]);
      if (distance > dmax) {
        index = i;
        dmax = distance;
      }
    }
    
    // If max distance is greater than epsilon, recursively simplify
    const result = [];
    if (dmax > epsilon) {
      const res1 = rdp(pts, start, index, epsilon);
      const res2 = rdp(pts, index, end, epsilon);
      
      // Concat the results, removing the duplicate point
      result.push(...res1.slice(0, -1));
      result.push(...res2);
    } else {
      result.push(pts[start]);
      result.push(pts[end]);
    }
    
    return result;
  };
  
  // Simple decimation as fallback - keep only every nth point
  const decimate = (pts, factor = 4) => {
    return pts.filter((_, idx) => idx % factor === 0 || idx === pts.length - 1);
  };
  
  try {
    // First do simple decimation to reduce the number of points
    const decimated = points.length > 500 ? decimate(points) : points;
    
    // If we still have a lot of points, use RDP algorithm
    if (decimated.length > 300) {
      // For very large datasets, increase the tolerance
      const adjustedTolerance = tolerance * Math.log10(decimated.length);
      const simplified = rdp(decimated, 0, decimated.length - 1, adjustedTolerance);
      dummyMap.remove(); // Clean up the map
      return simplified;
    }
    
    dummyMap.remove(); // Make sure to clean up
    return decimated;
  } catch (err) {
    console.error('Error simplifying route shape:', err);
    // Clean up the map in case of error
    dummyMap.remove();
    // Fall back to simple decimation
    return decimate(points, 6);
  }
}
