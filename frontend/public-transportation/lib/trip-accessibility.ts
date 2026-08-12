import fs from 'fs';
import path from 'path';

/**
 * Wheelchair accessibility for a scheduled trip, from GTFS `trips.txt`.
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

// ~318k trips in the Israeli feed. Held for the life of the process: the map is
// built once (~0.5s) and every lookup after that is free. Deliberately complete
// rather than storing only the inaccessible ones — "absent means accessible"
// would turn a trip missing from the feed into a promise we cannot keep, and a
// wrong "accessible" is the one error that can strand somebody at a stop.
let accessCache: Map<string, WheelchairAccess> | null = null;

function parseFlag(raw: string | undefined): WheelchairAccess {
  switch (raw?.trim()) {
    case '1': return 'accessible';
    case '2': return 'not_accessible';
    default: return 'unknown';
  }
}

function loadTripAccess(): Map<string, WheelchairAccess> {
  if (accessCache) return accessCache;

  if (!fs.existsSync(TRIPS_FILE)) {
    // Not a fallback: with no timetable there is no accessibility answer, and
    // every trip legitimately reports "unknown". Loud, because it means
    // motis/update-data.sh did not extract trips.txt.
    console.error(`trip-accessibility: ${TRIPS_FILE} missing — every trip will report unknown`);
    accessCache = new Map();
    return accessCache;
  }

  const content = fs.readFileSync(TRIPS_FILE, 'utf8');
  const lines = content.split('\n');
  const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
  const tripIdx = header.indexOf('trip_id');
  const accessIdx = header.indexOf('wheelchair_accessible');

  const map = new Map<string, WheelchairAccess>();
  if (tripIdx === -1 || accessIdx === -1) {
    console.error('trip-accessibility: trips.txt has no trip_id / wheelchair_accessible column');
    accessCache = map;
    return map;
  }

  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');
    const tripId = cols[tripIdx]?.trim();
    if (tripId) map.set(tripId, parseFlag(cols[accessIdx]));
  }

  accessCache = map;
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
  return loadTripAccess().get(tripId) ?? 'unknown';
}
