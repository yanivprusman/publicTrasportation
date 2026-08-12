/**
 * Straight-line metres between two coordinates.
 *
 * Equirectangular rather than haversine: at the scale this app deals in — a bus, a stop,
 * a person — the error is centimetres, and every result is shown rounded to a tenth of a
 * kilometre.
 *
 * One copy. This formula was written out three times in this repo (this file's two
 * callers plus the Kotlin client), which is how three places came to agree on the maths
 * and disagree on nothing useful. Kotlin keeps its own in `util/Geo.kt` because it cannot
 * import TypeScript; everything on this side imports from here.
 */
export function metersBetween(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = (lat1 - lat2) * 111_320
  const dLon = (lon1 - lon2) * 111_320 * Math.cos((lat2 * Math.PI) / 180)
  return Math.sqrt(dLat * dLat + dLon * dLon)
}
