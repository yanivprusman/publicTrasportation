import type { Itinerary } from '../types'

/**
 * What the journey costs, as the server computed it from the operators' fare table
 * (`fare_attributes.txt` + `fare_rules.txt` in the Israeli feed).
 *
 * This used to be a flat ₪5.50 per boarding, mirrored in the Android app. That put
 * Midreshet Ben-Gurion → Be'er Sheva at about ₪11 where both Moovit and Bus Nearby
 * quote ₪19. Returning null when the server could not price a leg is deliberate:
 * a wrong price is worse than no price, and the UI shows no badge at all.
 */
export function itineraryFare(itinerary: Itinerary): number | null {
  return typeof itinerary.fareTotal === 'number' ? itinerary.fareTotal : null
}

/** ₪17 keeps no agorot; ₪12.50 keeps both — 12.5 is a real price in this tariff. */
export function formatFare(amount: number): string {
  return amount % 1 === 0 ? `₪${amount.toFixed(0)}` : `₪${amount.toFixed(2)}`
}
