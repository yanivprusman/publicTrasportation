import type { Itinerary } from '../types'

export type RouteSortMode = 'fastest' | 'fewerTransfers' | 'lessWalking'

export const ROUTE_SORT_MODES: RouteSortMode[] = ['fastest', 'fewerTransfers', 'lessWalking']

/** Total seconds spent on foot across an itinerary. */
export function walkDuration(itinerary: Itinerary): number {
  return itinerary.legs
    .filter(leg => leg.mode === 'WALK')
    .reduce((sum, leg) => sum + (leg.duration || 0), 0)
}

/**
 * Orders itineraries for display without losing their identity: each entry
 * keeps the index it had in the unsorted results, so selection state and the
 * map layer keep pointing at the same trip when the sort changes.
 *
 * Every comparator falls back to total duration, so ties resolve to the
 * quickest trip rather than to MOTIS's arbitrary ordering.
 */
export function sortItineraries(
  itineraries: Itinerary[],
  mode: RouteSortMode
): { itinerary: Itinerary; index: number }[] {
  const indexed = itineraries.map((itinerary, index) => ({ itinerary, index }))

  const byDuration = (a: Itinerary, b: Itinerary) => a.duration - b.duration

  return indexed.sort((a, b) => {
    switch (mode) {
      case 'fewerTransfers': {
        const diff = a.itinerary.transfers - b.itinerary.transfers
        return diff !== 0 ? diff : byDuration(a.itinerary, b.itinerary)
      }
      case 'lessWalking': {
        const diff = walkDuration(a.itinerary) - walkDuration(b.itinerary)
        return diff !== 0 ? diff : byDuration(a.itinerary, b.itinerary)
      }
      default:
        return byDuration(a.itinerary, b.itinerary)
    }
  })
}
