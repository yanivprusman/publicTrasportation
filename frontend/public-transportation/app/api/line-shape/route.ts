import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { execFileSync } from 'child_process';

// The one GTFS copy motis/update-data.sh maintains, and the same one MOTIS itself
// imports. This route used to read its own copy under backend/, fetched from a
// Google Storage mirror and refreshed only when the files were *missing* — so it
// sat at a five-month-old snapshot of a different feed while every other route
// moved on, and drew lines that no longer existed.
const GTFS_DATA_DIR = path.join(process.cwd(), '../../gtfs/israel-public-transportation');
const GTFS_REQUIRED_FILES = ['routes.txt', 'trips.txt', 'shapes.txt'];

async function ensureGtfsData() {
  const missing = GTFS_REQUIRED_FILES.filter(f =>
    !fs.existsSync(path.join(GTFS_DATA_DIR, f))
  );
  if (missing.length === 0) return;

  // Deliberately not downloading a replacement here. A second fetch path is how
  // this route drifted onto its own stale feed in the first place: whoever ran it
  // got data nobody else had, and nothing ever brought it forward again. There is
  // one importer, and it is the one that must run.
  throw new Error(
    `GTFS files missing from ${GTFS_DATA_DIR}: ${missing.join(', ')}. ` +
    `Run motis/update-data.sh — it is the only thing that refreshes this feed.`
  );
}

interface LineShapeEntry {
  shapes: Record<string, number[][]>;
  headsigns: Record<string, string>;
}

const shapeCache = new Map<string, { data: LineShapeEntry; time: number }>();
const SHAPE_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours

// The Android client deserializes the default response as a flat
// Map<direction, points>, so new fields can only ship behind ?meta=full.
function toResponse(entry: LineShapeEntry, withMeta: boolean) {
  if (withMeta) {
    return NextResponse.json({ directions: entry.shapes, headsigns: entry.headsigns });
  }
  return NextResponse.json(entry.shapes);
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lineNumber = (searchParams.get('line') || '60').trim();
  const withMeta = searchParams.get('meta') === 'full';

  const cached = shapeCache.get(lineNumber);
  if (cached && (Date.now() - cached.time < SHAPE_CACHE_TTL)) {
    return toResponse(cached.data, withMeta);
  }

  try {
    await ensureGtfsData();

    const routesFile = path.join(GTFS_DATA_DIR, 'routes.txt');
    const tripsFile = path.join(GTFS_DATA_DIR, 'trips.txt');
    const shapesFile = path.join(GTFS_DATA_DIR, 'shapes.txt');

    // Step 1: Find route IDs for the line number
    const routesContent = fs.readFileSync(routesFile, 'utf8');
    const routesLines = routesContent.split('\n');
    const routesHeader = routesLines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
    const routeIdIdx = routesHeader.indexOf('route_id');
    const routeShortNameIdx = routesHeader.indexOf('route_short_name');
    const routeLongNameIdx = routesHeader.indexOf('route_long_name');

    if (routeIdIdx === -1 || routeShortNameIdx === -1) {
      throw new Error('Required columns not found in routes.txt');
    }

    const routeIds: string[] = [];
    const routeLongNames: Record<string, string> = {};
    for (let i = 1; i < routesLines.length; i++) {
      if (!routesLines[i].trim()) continue;
      const cols = routesLines[i].split(',');
      if (cols[routeShortNameIdx]?.trim() === lineNumber) {
        const routeId = cols[routeIdIdx].trim();
        routeIds.push(routeId);
        if (routeLongNameIdx !== -1 && cols[routeLongNameIdx]) {
          routeLongNames[routeId] = cols[routeLongNameIdx].trim();
        }
      }
    }

    if (routeIds.length === 0) {
      throw new Error(`No routes found for line ${lineNumber}`);
    }

    // Step 2: Find shape IDs from trips
    const tripsContent = fs.readFileSync(tripsFile, 'utf8');
    const tripsLines = tripsContent.split('\n');
    const tripsHeader = tripsLines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
    const tripRouteIdIdx = tripsHeader.indexOf('route_id');
    const shapeIdIdx = tripsHeader.indexOf('shape_id');
    const directionIdIdx = tripsHeader.indexOf('direction_id');

    if (tripRouteIdIdx === -1 || shapeIdIdx === -1) {
      throw new Error('Required columns not found in trips.txt');
    }

    const routeIdSet = new Set(routeIds);
    const shapeIds: Record<string, string[]> = {};
    const headsigns: Record<string, string> = {};

    for (let i = 1; i < tripsLines.length; i++) {
      if (!tripsLines[i].trim()) continue;
      const cols = tripsLines[i].split(',');
      const tripRouteId = cols[tripRouteIdIdx]?.trim();
      if (tripRouteId && routeIdSet.has(tripRouteId)) {
        const shapeId = cols[shapeIdIdx]?.trim();
        if (shapeId) {
          const direction = (directionIdIdx !== -1 && cols[directionIdIdx]) ? cols[directionIdIdx].trim() : '0';
          if (!shapeIds[direction]) shapeIds[direction] = [];
          if (!shapeIds[direction].includes(shapeId)) {
            shapeIds[direction].push(shapeId);
          }
          if (!headsigns[direction] && routeLongNames[tripRouteId]) {
            headsigns[direction] = routeLongNames[tripRouteId];
          }
        }
      }
    }

    if (Object.keys(shapeIds).length === 0) {
      throw new Error(`No shapes found for line ${lineNumber}`);
    }

    // Step 3: Get shape points using grep for efficiency on large files
    const result: Record<string, number[][]> = {};

    for (const [direction, dirShapeIds] of Object.entries(shapeIds)) {
      const shapeId = dirShapeIds[0];
      try {
        const grepOutput = execFileSync('grep', ['-F', `${shapeId},`, shapesFile], {
          encoding: 'utf8',
          maxBuffer: 50 * 1024 * 1024,
        });

        const shapePoints: { sequence: number; lat: number; lon: number }[] = [];
        for (const line of grepOutput.split('\n')) {
          if (!line.trim()) continue;
          // grep -F matches the ID anywhere in the line — inside longer shape
          // IDs ("145874," contains "45874,") and inside coordinates
          // ("32.045874,") — which merges points from unrelated shapes into
          // this polyline. Keep only rows whose shape_id column matches exactly.
          if (!line.startsWith(`${shapeId},`)) continue;
          const cols = line.split(',');
          if (cols.length >= 4) {
            shapePoints.push({
              sequence: parseInt(cols[3], 10),
              lat: parseFloat(cols[1]),
              lon: parseFloat(cols[2]),
            });
          }
        }

        shapePoints.sort((a, b) => a.sequence - b.sequence);
        const points = shapePoints.map(p => [p.lat, p.lon]);

        if (points.length > 0) {
          result[direction] = points;
        }
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        console.error(`Error getting shape ${shapeId}:`, message);
      }
    }

    if (Object.keys(result).length === 0) {
      throw new Error(`No shape points found for line ${lineNumber}`);
    }

    const entry: LineShapeEntry = { shapes: result, headsigns };
    shapeCache.set(lineNumber, { data: entry, time: Date.now() });
    return toResponse(entry, withMeta);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching line shape:', message);
    return NextResponse.json(
      { error: message, message: 'Failed to retrieve route shape data' },
      { status: 500 }
    );
  }
}
