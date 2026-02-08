import polyline from '@mapbox/polyline'

export function decodePolyline(encoded: string): [number, number][] {
  return polyline.decode(encoded) as [number, number][]
}
