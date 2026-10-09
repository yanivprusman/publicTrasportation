// Shared MOTIS↔app mode vocabulary, used by every API route that talks to
// MOTIS so filtering and rendering always agree on what counts as bus/train/tram.

export type NormalizedMode = 'WALK' | 'BIKE' | 'SCOOTER' | 'CAR' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY';

// The app's TransitMode union is WALK | BUS | RAIL | TRAM | SUBWAY, and
// utils/mode-colors styles only those five. MOTIS v2 reports finer-grained
// modes (e.g. REGIONAL_RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, NIGHT_RAIL, METRO,
// COACH). Passing those through unchanged made getModeStyle fall back to the
// WALK style, so a real train/metro leg rendered as a grey dashed "walking"
// polyline and showed the raw enum (e.g. "REGIONAL_RAIL") as its pill label.
// Fold each MOTIS mode into the app's contract so styling and labels are right.
export const MODE_MAP: Record<string, NormalizedMode> = {
  WALK: 'WALK',
  BIKE: 'BIKE',
  CAR: 'CAR',
  BUS: 'BUS',
  COACH: 'BUS',
  TRAM: 'TRAM',
  SUBWAY: 'SUBWAY',
  METRO: 'SUBWAY',
  RAIL: 'RAIL',
  REGIONAL_RAIL: 'RAIL',
  REGIONAL_FAST_RAIL: 'RAIL',
  HIGHSPEED_RAIL: 'RAIL',
  LONG_DISTANCE: 'RAIL',
  NIGHT_RAIL: 'RAIL',
};

// App-level mode filter keys (sent by the client as ?modes=bus,train) mapped
// to the MOTIS transitModes enums each one covers. The groups mirror MODE_MAP
// above so filtering and rendering agree on what counts as bus/train/tram.
export const MODE_GROUPS: Record<string, string[]> = {
  bus: ['BUS', 'COACH'],
  train: ['RAIL', 'REGIONAL_RAIL', 'REGIONAL_FAST_RAIL', 'HIGHSPEED_RAIL', 'LONG_DISTANCE', 'NIGHT_RAIL'],
  tram: ['TRAM', 'SUBWAY', 'METRO'],
};

export function normalizeMode(mode: string | undefined, scooter = false): NormalizedMode {
  if (!mode) return 'WALK';
  // A scooter rider's street legs come back from MOTIS as BIKE (see
  // scooterPlanParams); they are reported as what the rider is actually on.
  if (scooter && mode === 'BIKE') return 'SCOOTER';
  // Any unrecognized transit mode is still a vehicle leg, not a walk — render
  // it as BUS (solid colored line) rather than the grey dashed walk style.
  return MODE_MAP[mode] ?? 'BUS';
}

// Legs the rider moves themself — on foot, on a scooter, by bike or car — as
// opposed to a vehicle they board. Covers MOTIS's raw modes and the normalized
// ones alike, so the test reads the same before and after transformation. A
// missing mode is a walk, as in normalizeMode.
const STREET_MODES: ReadonlySet<string> = new Set(['WALK', 'BIKE', 'SCOOTER', 'CAR']);

export function isStreetMode(mode: string | undefined): boolean {
  return !mode || STREET_MODES.has(mode);
}

/**
 * Plan parameters for a rider with an e-scooter (?scooter=1).
 *
 * The scooter travels with the rider — folded on the bus, in the marked car on the
 * train — so they ride to the first stop and from the last one, and there is nothing
 * to park or return. MOTIS has no scooter profile; its bike profile (bike lanes and
 * roads) is the closest fit, and `cyclingSpeed` sets the pace to the rider's own.
 * That matters more than it sounds: the profile's default is a cyclist's ~15 km/h,
 * and a scooter that does 40 (the user's, 2026-10-09) is undervalued almost three to
 * one at that pace — a 14 km ride priced at 59 minutes that takes 22. The pace
 * decides which rides beat which buses, not only how the minutes read.
 *
 * Measured 2026-10-09 against the walk-only plan: Midreshet Ben-Gurion → Beer Sheva
 * 57 → 52 min, Florentin → Herzliya 45 → 36 min, Meitar → Tel Aviv 191 → 147 min.
 * The gain comes from stops the rider could never walk to: MOTIS treats every stop
 * within the ride cap as a place to board or get off, so it skips the meandering
 * part of a line and boards where the line is already going the rider's way.
 *
 * Two things are deliberately NOT here:
 * - `requireBikeTransport`: the Israeli GTFS carries no bikes_allowed data, so with
 *   it MOTIS finds nothing at all (measured the same day).
 * - WALK next to BIKE for the first/last mile: measured identical results — a rider
 *   with a scooter rides, and a 50 m "ride" is harmless.
 *
 * `directModes` IS set, unlike in the bike/car comparison query: MOTIS uses the
 * fastest direct connection as a cut-off, so a bus combination slower than simply
 * riding the whole way is dropped. That is the rule a scooter rider plans by — the
 * bus is for the long stretch the scooter can't do — and the ride itself comes back
 * in `direct` as an itinerary of its own. The direct search shares the ride cap, so
 * a trip too long to ride prunes nothing.
 */
export function scooterPlanParams(maxRideSeconds: number, speedKmh: number): Record<string, string> {
  return {
    preTransitModes: 'BIKE',
    postTransitModes: 'BIKE',
    directModes: 'BIKE',
    maxDirectTime: String(maxRideSeconds),
    // Metres per second, as MOTIS takes it. Supported by the 2.11.2 binary in use
    // (verified live 2026-10-09) even though its release notes never mention it.
    cyclingSpeed: (speedKmh / 3.6).toFixed(3),
  };
}

export const MIN_SCOOTER_SPEED_KMH = 10;
export const MAX_SCOOTER_SPEED_KMH = 50;
/** Israel's legal e-scooter limit — what a scooter request that names no speed plans with. */
export const DEFAULT_SCOOTER_SPEED_KMH = 25;

/**
 * Parses ?scooterSpeed= (average km/h on the road); the legal limit when absent,
 * null when the value is not a whole number in range.
 */
export function parseScooterSpeedParam(value: string | null): number | null {
  if (value === null || value === '') return DEFAULT_SCOOTER_SPEED_KMH;
  const n = Number(value);
  if (!Number.isInteger(n) || n < MIN_SCOOTER_SPEED_KMH || n > MAX_SCOOTER_SPEED_KMH) return null;
  return n;
}

/** Parses ?scooter=; null when the value is not a recognised boolean. */
export function parseScooterParam(value: string | null): boolean | null {
  if (value === null || value === '' || value === '0' || value === 'false') return false;
  if (value === '1' || value === 'true') return true;
  return null;
}

// Meters per second assumed for first/last-mile walking, sent as ?pedestrianSpeed=
// on every plan call. MOTIS's built-in default (~1.1 m/s) priced a 375 m walk at
// 7 minutes where Moovit says 4 and Maps says 3 — and since the same figure feeds
// the routing itself, the planner also skipped buses the rider could in fact
// catch. 1.5 is exactly Moovit's assumption (their 360 m = 4 min). Requires
// MOTIS >= 2.9 — a 2.8 server silently ignores the parameter.
export const PEDESTRIAN_SPEED = '1.5';

// First/last-mile walk ceiling, in seconds, for a request that names no maxWalk
// (the app's "No limit" chip). Leaving maxPre/PostTransitTime off is NOT
// unlimited: MOTIS then applies its own 15-minute default, so "No limit" was
// tighter than the "20 min" chip beside it. On 2026-09-22 a rider in Ramat Negev,
// 3.3 km from the nearest stop (צומת קיבוץ טללים), got "no routes" under
// "No limit" while MOTIS had three at a 60-minute cap. 60 is the API's own maxWalk
// ceiling; city trips measured that day (Tel Aviv, Beer Sheva, Jerusalem) return
// the identical itineraries at 15 and 60, in the same time.
export const UNCAPPED_WALK_SECONDS = 60 * 60;
