// Shared MOTIS↔app mode vocabulary, used by every API route that talks to
// MOTIS so filtering and rendering always agree on what counts as bus/train/tram.

export type NormalizedMode = 'WALK' | 'BIKE' | 'CAR' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY';

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

export function normalizeMode(mode: string | undefined): NormalizedMode {
  if (!mode) return 'WALK';
  // Any unrecognized transit mode is still a vehicle leg, not a walk — render
  // it as BUS (solid colored line) rather than the grey dashed walk style.
  return MODE_MAP[mode] ?? 'BUS';
}

// Meters per second assumed for first/last-mile walking, sent as ?pedestrianSpeed=
// on every plan call. MOTIS's built-in default (~1.1 m/s) priced a 375 m walk at
// 7 minutes where Moovit says 4 and Maps says 3 — and since the same figure feeds
// the routing itself, the planner also skipped buses the rider could in fact
// catch. 1.5 is exactly Moovit's assumption (their 360 m = 4 min). Requires
// MOTIS >= 2.9 — a 2.8 server silently ignores the parameter.
export const PEDESTRIAN_SPEED = '1.5';
