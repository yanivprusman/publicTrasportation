import { NextRequest, NextResponse } from 'next/server';
import { execFileSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { routeRepresentativeTrip } from '@/lib/gtfs-trips';
import { fileStamp } from '@/lib/file-stamp';

/**
 * The ordered stop list of one route — "line 64, and every stop it makes".
 *
 * Keyed by route_id (`?routeId=`) because that is what a live SIRI arrival
 * carries (LineRef = route_id), and in this feed a route_id already encodes the
 * direction — so the answer is the exact sequence the tracked bus will drive,
 * not a both-directions muddle the client has to pick through.
 *
 * The sequence itself lives only in stop_times.txt, which is ~470 MB — so like
 * /api/trip-shape with shapes.txt, it is never read into the process: grep pulls
 * one trip's ~40 rows in milliseconds and the joined result is cached.
 */

const GTFS_DIR = path.join(process.cwd(), '../../gtfs/israel-public-transportation');
const STOP_TIMES_FILE = path.join(GTFS_DIR, 'stop_times.txt');
const STOPS_FILE = path.join(GTFS_DIR, 'stops.txt');
const ROUTES_FILE = path.join(GTFS_DIR, 'routes.txt');

interface RouteStop {
  stopCode: string;
  name: string;
  lat: number;
  lon: number;
}

const routeStopsCache = new Map<string, { headsign: string; lineNumber: string; stops: RouteStop[] }>();
const ROUTE_STOPS_CACHE_MAX = 200;

// All caches here are rebuilt when their source file is replaced by the
// nightly update — a joined result caches ids from FOUR files, so it flushes
// when any of them moves (see lib/file-stamp.ts for the failure this ends).
let resultsStamp = -2;

function currentResultsStamp(): number {
  return (
    fileStamp(STOP_TIMES_FILE) +
    fileStamp(STOPS_FILE) +
    fileStamp(ROUTES_FILE) +
    fileStamp(path.join(GTFS_DIR, 'trips.txt'))
  );
}

// stop_id -> everything the stop list shows. stops.txt is ~5 MB / ~45k rows;
// held whole for the life of the process like the trips cache it joins against.
let stopsCache: Map<string, RouteStop> | null = null;
let stopsStamp = -2;

function loadStops(): Map<string, RouteStop> {
  const stamp = fileStamp(STOPS_FILE);
  if (stopsCache && stamp === stopsStamp) return stopsCache;
  stopsStamp = stamp;
  const map = new Map<string, RouteStop>();
  if (!fs.existsSync(STOPS_FILE)) {
    console.error(`route-stops: ${STOPS_FILE} missing — run motis/update-data.sh`);
    stopsCache = map;
    return map;
  }
  const lines = fs.readFileSync(STOPS_FILE, 'utf8').split('\n');
  const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
  const idIdx = header.indexOf('stop_id');
  const codeIdx = header.indexOf('stop_code');
  const nameIdx = header.indexOf('stop_name');
  const latIdx = header.indexOf('stop_lat');
  const lonIdx = header.indexOf('stop_lon');
  if (idIdx === -1 || nameIdx === -1 || latIdx === -1 || lonIdx === -1) {
    console.error('route-stops: stops.txt is missing stop_id / stop_name / stop_lat / stop_lon');
    stopsCache = map;
    return map;
  }
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');
    const id = cols[idIdx]?.trim();
    if (!id) continue;
    const lat = parseFloat(cols[latIdx]);
    const lon = parseFloat(cols[lonIdx]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    map.set(id, {
      stopCode: codeIdx !== -1 ? (cols[codeIdx]?.trim() ?? '') : '',
      name: cols[nameIdx]?.trim() ?? '',
      lat,
      lon,
    });
  }
  stopsCache = map;
  return map;
}

// route_id -> route_short_name, the number on the bus. routes.txt is ~1 MB.
let routeNameCache: Map<string, string> | null = null;
let routeNamesStamp = -2;

function loadRouteNames(): Map<string, string> {
  const stamp = fileStamp(ROUTES_FILE);
  if (routeNameCache && stamp === routeNamesStamp) return routeNameCache;
  routeNamesStamp = stamp;
  const map = new Map<string, string>();
  if (fs.existsSync(ROUTES_FILE)) {
    const lines = fs.readFileSync(ROUTES_FILE, 'utf8').split('\n');
    const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
    const idIdx = header.indexOf('route_id');
    const shortIdx = header.indexOf('route_short_name');
    if (idIdx !== -1 && shortIdx !== -1) {
      for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const cols = lines[i].split(',');
        const id = cols[idIdx]?.trim();
        if (id) map.set(id, cols[shortIdx]?.trim() ?? '');
      }
    }
  }
  routeNameCache = map;
  return map;
}

/** The trip's stops in driving order, joined against stops.txt. */
function loadTripStops(tripId: string): RouteStop[] {
  let output: string;
  try {
    output = execFileSync('grep', ['-F', `${tripId},`, STOP_TIMES_FILE], {
      encoding: 'utf8',
      maxBuffer: 10 * 1024 * 1024,
    });
  } catch {
    // grep exits 1 on zero matches — a trip the schedule no longer carries.
    return [];
  }

  const stops = loadStops();
  const rows: { sequence: number; stop: RouteStop }[] = [];
  for (const line of output.split('\n')) {
    // grep -F matches anywhere on the row; only an exact first column counts.
    if (!line.startsWith(`${tripId},`)) continue;
    const cols = line.split(',');
    if (cols.length < 5) continue;
    const stop = stops.get(cols[3]?.trim());
    const sequence = parseInt(cols[4], 10);
    if (!stop || !Number.isFinite(sequence)) continue;
    rows.push({ sequence, stop });
  }
  rows.sort((a, b) => a.sequence - b.sequence);
  return rows.map(r => r.stop);
}

export async function GET(request: NextRequest) {
  const routeId = request.nextUrl.searchParams.get('routeId');
  if (!routeId) {
    return NextResponse.json({ error: 'Missing required parameter: routeId' }, { status: 400 });
  }

  const stamp = currentResultsStamp();
  if (stamp !== resultsStamp) {
    routeStopsCache.clear();
    resultsStamp = stamp;
  }
  const cached = routeStopsCache.get(routeId);
  if (cached) return NextResponse.json({ routeId, ...cached });

  const trip = routeRepresentativeTrip(routeId);
  if (!trip) {
    return NextResponse.json({ error: `No trips found for route ${routeId}` }, { status: 404 });
  }

  const stops = loadTripStops(trip.tripId);
  if (stops.length === 0) {
    return NextResponse.json(
      { error: `No stop sequence found for route ${routeId} (trip ${trip.tripId})` },
      { status: 404 }
    );
  }

  const result = {
    headsign: trip.headsign,
    lineNumber: loadRouteNames().get(routeId) ?? '',
    stops,
  };
  if (routeStopsCache.size >= ROUTE_STOPS_CACHE_MAX) {
    const oldest = routeStopsCache.keys().next().value;
    if (oldest !== undefined) routeStopsCache.delete(oldest);
  }
  routeStopsCache.set(routeId, result);
  return NextResponse.json({ routeId, ...result });
}
