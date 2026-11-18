import polyline from '@mapbox/polyline'

/** One MOTIS street step: a stretch of one OSM way, with its own geometry. */
export interface MotisStep {
  streetName?: string
  distance?: number
  polyline?: { points?: string; precision?: number }
}

/** A named stretch of a street leg, for the map to write the name along the line. */
export interface StreetStretch {
  name: string
  /** Encoded at precision 7, like every other polyline the API returns. */
  polyline: string
  distanceMeters: number
}

const PRECISION = 7

/**
 * The street names along a walk/bike/scooter leg, one entry per run of the same street.
 *
 * Why: the route line is drawn on raster map tiles whose street names are painted into
 * the image, so the line covers the name of the very street the rider is walking on
 * (2026-11-18, Sheinkin/Nahalat Binyamin, Tel Aviv). The map cannot lift its own label
 * above the line, so the client writes these names onto the line instead.
 *
 * MOTIS splits a street into one step per OSM way, so consecutive steps of the same name
 * are merged into one stretch: one label per street, not one per block. Unnamed steps
 * (footpaths, squares, crossings) carry no label and are dropped.
 */
export function streetStretches(steps: MotisStep[] | undefined): StreetStretch[] {
  const out: StreetStretch[] = []
  let current: { name: string; points: [number, number][]; distance: number } | null = null

  const flush = () => {
    if (current && current.points.length >= 2) {
      out.push({
        name: current.name,
        polyline: polyline.encode(current.points, PRECISION),
        distanceMeters: Math.round(current.distance),
      })
    }
    current = null
  }

  for (const step of steps || []) {
    const name = (step.streetName || '').trim()
    const encoded = step.polyline?.points
    if (!name || !encoded) {
      flush()
      continue
    }
    const points = polyline.decode(encoded, step.polyline?.precision ?? PRECISION) as [number, number][]
    if (current && current.name === name) {
      // Steps share their joining node; skip it so the line has no zero-length segment.
      const last = current.points[current.points.length - 1]
      const rest = points.length && last && points[0][0] === last[0] && points[0][1] === last[1]
        ? points.slice(1) : points
      current.points.push(...rest)
      current.distance += step.distance || 0
    } else {
      flush()
      current = { name, points: [...points], distance: step.distance || 0 }
    }
  }
  flush()
  return out
}
