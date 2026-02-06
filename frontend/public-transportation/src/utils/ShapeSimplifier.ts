import type { Coordinates } from '../types'

/**
 * Simplifies a polyline by reducing the number of points.
 * Uses simple decimation for performance.
 */
export function simplifyShape(points: Coordinates[], tolerance = 0.0001): Coordinates[] {
  if (!points || points.length <= 2) return points

  const decimate = (pts: Coordinates[], factor = 4): Coordinates[] => {
    return pts.filter((_, idx) => idx % factor === 0 || idx === pts.length - 1)
  }

  try {
    // Simple decimation to reduce the number of points
    const decimated = points.length > 500 ? decimate(points) : points

    // For very large datasets, decimate more aggressively
    if (decimated.length > 300) {
      const adjustedFactor = Math.ceil(Math.log10(decimated.length))
      return decimate(decimated, adjustedFactor)
    }

    return decimated
  } catch (err) {
    console.error('Error simplifying route shape:', err)
    return decimate(points, 6)
  }
}
