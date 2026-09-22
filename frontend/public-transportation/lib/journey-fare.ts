import { metersBetween } from './geo';

/**
 * What a whole journey costs, from the single-ride fares of its rides.
 *
 * Rides are paid one by one, with one exception the Israeli tariff grants: a fare
 * paid for a short ride includes free transfers for 90 minutes, as long as the rider
 * stays within 15 km of where they first validated — buses and light rail only,
 * never Israel Railways. Summing every ride charged a two-bus trip across Be'er Sheva
 * ₪16 where the rider pays ₪8.
 *
 * Every public description of the tariff found on 2026-09-22 (Moovit, ynet, Maariv)
 * states the free transfer for that short band only, so beyond it each ride is priced
 * on its own: a 1 km hop onto an intercity line is two fares — 55 then 392 from
 * Midreshet Ben-Gurion is ₪8 + ₪19. Pricing that as one journey on a rule nobody
 * publishes would quote less than the rider may be charged.
 */

const FREE_TRANSFER_RADIUS_M = 15_000;
const FREE_TRANSFER_WINDOW_MS = 90 * 60_000;

export interface PricedRide {
  mode: string;
  /** The ride's single-ride price; undefined when the fare table has no rule for it. */
  fare?: number;
  from: { lat: number; lon: number };
  to: { lat: number; lon: number };
  /** ISO boarding time — when the rider validates. */
  startTime: string;
}

export interface JourneyFare {
  /** Null when any ride is unpriced: a partial sum would understate the fare. */
  total: number | null;
  /** Indexes into the rides that the first ride's fare already paid for. */
  coveredByTransfer: Set<number>;
}

export function journeyFare(rides: PricedRide[]): JourneyFare {
  const none: JourneyFare = { total: null, coveredByTransfer: new Set() };
  if (rides.length === 0) return none;
  const fares = rides.map(r => r.fare);
  if (fares.some(f => typeof f !== 'number')) return none;

  if (rides.length > 1 && withinFreeTransfer(rides)) {
    return {
      total: fares[0] as number,
      coveredByTransfer: new Set(rides.map((_, i) => i).slice(1)),
    };
  }
  const sum = (fares as number[]).reduce((a, b) => a + b, 0);
  return { total: Number(sum.toFixed(2)), coveredByTransfer: new Set() };
}

function withinFreeTransfer(rides: PricedRide[]): boolean {
  const first = rides[0];
  const validatedAt = Date.parse(first.startTime);
  if (!Number.isFinite(validatedAt)) return false;
  const nearFirst = (p: { lat: number; lon: number }) =>
    metersBetween(first.from.lat, first.from.lon, p.lat, p.lon) <= FREE_TRANSFER_RADIUS_M;
  return rides.every(ride => {
    const boardedAt = Date.parse(ride.startTime);
    return (
      ride.mode !== 'RAIL' &&
      nearFirst(ride.from) &&
      nearFirst(ride.to) &&
      Number.isFinite(boardedAt) &&
      boardedAt - validatedAt <= FREE_TRANSFER_WINDOW_MS
    );
  });
}
