import type { Itinerary } from '../types'

// Per-boarding fares in shekels. These mirror the Android app's
// Itinerary.estimateFare() so the same trip quotes the same price on both
// platforms. Israeli fares are distance-banded in reality; this is a
// deliberately simple per-leg estimate, which is why the UI labels it "~".
const FARE_BY_MODE: Record<string, number> = {
  BUS: 5.5,
  TRAM: 5.5,
  SUBWAY: 5.5,
  RAIL: 15,
  FERRY: 25,
}

/** Estimated cost of an itinerary in shekels. Walking/bike/car legs are free. */
export function estimateFare(itinerary: Itinerary): number {
  return itinerary.legs.reduce((sum, leg) => sum + (FARE_BY_MODE[leg.mode] || 0), 0)
}
