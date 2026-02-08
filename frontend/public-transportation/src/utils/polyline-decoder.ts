import polyline from '@mapbox/polyline'

export function decodePolyline(encoded: string): [number, number][] {
  // MOTIS v2 uses precision 7 for encoded polylines (not the default 5)
  return polyline.decode(encoded, 7) as [number, number][]
}
