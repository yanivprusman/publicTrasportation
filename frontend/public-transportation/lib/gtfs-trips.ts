import fs from 'fs';
import path from 'path';

/**
 * Per-trip facts read out of GTFS `trips.txt`: wheelchair access and shape id.
 *
 * The SIRI feed carries no accessibility field of any kind — not on the vehicle,
 * not on the call — so the timetable is the only source there is. This describes
 * the *scheduled service*, not the particular vehicle that turns up.
 *
 * Values are GTFS's own: 1 accessible, 2 not accessible, 0/blank unknown.
 */
export type WheelchairAccess = 'accessible' | 'not_accessible' | 'unknown';

const TRIPS_FILE = path.join(
  process.cwd(),
  '../../gtfs/israel-public-transportation/trips.txt'
);

interface TripFacts {
  access: WheelchairAccess;
  /** GTFS shape_id — the only correct way to draw the line this trip runs on. */
  shapeId: string;
}

// ~318k trips in the Israeli feed. Held for the life of the process: the map is
// built once (~0.5s) and every lookup after that is free. Deliberately complete
// rather than storing only the inaccessible ones — "absent means accessible"
// would turn a trip missing from the feed into a promise we cannot keep, and a
// wrong "accessible" is the one error that can strand somebody at a stop.
let tripCache: Map<string, TripFacts> | null = null;

function parseFlag(raw: string | undefined): WheelchairAccess {
  switch (raw?.trim()) {
    case '1': return 'accessible';
    case '2': return 'not_accessible';
    default: return 'unknown';
  }
}

function loadTrips(): Map<string, TripFacts> {
  if (tripCache) return tripCache;

  if (!fs.existsSync(TRIPS_FILE)) {
    // Not a fallback: with no timetable there is no accessibility answer, and
    // every trip legitimately reports "unknown". Loud, because it means
    // motis/update-data.sh did not extract trips.txt.
    console.error(`gtfs-trips: ${TRIPS_FILE} missing — every trip will report unknown`);
    tripCache = new Map();
    return tripCache;
  }

  const content = fs.readFileSync(TRIPS_FILE, 'utf8');
  const lines = content.split('\n');
  const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
  const tripIdx = header.indexOf('trip_id');
  const accessIdx = header.indexOf('wheelchair_accessible');
  const shapeIdx = header.indexOf('shape_id');

  const map = new Map<string, TripFacts>();
  if (tripIdx === -1 || accessIdx === -1 || shapeIdx === -1) {
    console.error('gtfs-trips: trips.txt is missing trip_id / wheelchair_accessible / shape_id');
    tripCache = map;
    return map;
  }

  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');
    const tripId = cols[tripIdx]?.trim();
    if (!tripId) continue;
    map.set(tripId, {
      access: parseFlag(cols[accessIdx]),
      shapeId: cols[shapeIdx]?.trim() ?? '',
    });
  }

  tripCache = map;
  return map;
}

/**
 * MOTIS trip ids look like `20260812_08:25_israel_11427155_120826`; the GTFS
 * `trip_id` is the last two underscore-separated parts (`11427155_120826`).
 */
export function gtfsTripId(motisTripId: string): string | null {
  const parts = motisTripId.split('_');
  if (parts.length < 2) return null;
  return parts.slice(-2).join('_');
}

export function tripWheelchairAccess(motisTripId: string | undefined): WheelchairAccess {
  if (!motisTripId) return 'unknown';
  const tripId = gtfsTripId(motisTripId);
  if (!tripId) return 'unknown';
  return loadTrips().get(tripId)?.access ?? 'unknown';
}

/**
 * The shape this trip runs on, or null when the trip is not in the feed.
 *
 * This is the whole point of going through the trip: a line *number* is not
 * unique nationwide — line 60 exists in Beer Sheva, Tel Aviv and Haifa — so
 * resolving geometry by number draws somebody else's route.
 */
export function tripShapeId(motisTripId: string | undefined): string | null {
  if (!motisTripId) return null;
  const tripId = gtfsTripId(motisTripId);
  if (!tripId) return null;
  return loadTrips().get(tripId)?.shapeId || null;
}
