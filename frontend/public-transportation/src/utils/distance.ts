// Typical walking pace used for "N min walk" estimates.
const WALK_METERS_PER_MINUTE = 80

export const formatStopDistance = (meters: number): string =>
  meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(1)} km`

export const walkMinutes = (meters: number): number =>
  Math.max(1, Math.ceil(meters / WALK_METERS_PER_MINUTE))
