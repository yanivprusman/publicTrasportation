import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

const GTFS_DATA_DIR = path.join(process.cwd(), '../../backend/israel-public-transportation');
const GTFS_URL = 'https://storage.googleapis.com/storage/v1/b/mdb-latest/o/il-ministry-of-transport-and-road-safety-gtfs-2519.zip?alt=media';
const GTFS_REQUIRED_FILES = ['routes.txt', 'trips.txt', 'shapes.txt'];

let gtfsDownloading: Promise<void> | null = null;

async function ensureGtfsData() {
  const allExist = GTFS_REQUIRED_FILES.every(f =>
    fs.existsSync(path.join(GTFS_DATA_DIR, f))
  );
  if (allExist) return;

  if (gtfsDownloading) return gtfsDownloading;

  gtfsDownloading = (async () => {
    try {
      console.log('GTFS files missing, downloading from MOT...');
      fs.mkdirSync(GTFS_DATA_DIR, { recursive: true });
      const zipPath = path.join(GTFS_DATA_DIR, 'gtfs.zip');
      execSync(`curl -fSL -o "${zipPath}" "${GTFS_URL}"`, {
        timeout: 120000,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      console.log('Extracting GTFS data...');
      execSync(`unzip -o "${zipPath}" ${GTFS_REQUIRED_FILES.join(' ')} -d "${GTFS_DATA_DIR}"`, {
        timeout: 60000,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      fs.unlinkSync(zipPath);
      console.log('GTFS data ready.');
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      console.error('Failed to download GTFS data:', message);
      throw new Error('Failed to download GTFS data. Please manually place routes.txt, trips.txt, shapes.txt in backend/israel-public-transportation/');
    } finally {
      gtfsDownloading = null;
    }
  })();

  return gtfsDownloading;
}

const shapeCache = new Map<string, { data: Record<string, number[][]>; time: number }>();
const SHAPE_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lineNumber = (searchParams.get('line') || '60').trim();

  const cached = shapeCache.get(lineNumber);
  if (cached && (Date.now() - cached.time < SHAPE_CACHE_TTL)) {
    return NextResponse.json(cached.data);
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

    if (routeIdIdx === -1 || routeShortNameIdx === -1) {
      throw new Error('Required columns not found in routes.txt');
    }

    const routeIds: string[] = [];
    for (let i = 1; i < routesLines.length; i++) {
      if (!routesLines[i].trim()) continue;
      const cols = routesLines[i].split(',');
      if (cols[routeShortNameIdx]?.trim() === lineNumber) {
        routeIds.push(cols[routeIdIdx].trim());
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

    for (let i = 1; i < tripsLines.length; i++) {
      if (!tripsLines[i].trim()) continue;
      const cols = tripsLines[i].split(',');
      if (routeIdSet.has(cols[tripRouteIdIdx]?.trim())) {
        const shapeId = cols[shapeIdIdx]?.trim();
        if (shapeId) {
          const direction = (directionIdIdx !== -1 && cols[directionIdIdx]) ? cols[directionIdIdx].trim() : '0';
          if (!shapeIds[direction]) shapeIds[direction] = [];
          if (!shapeIds[direction].includes(shapeId)) {
            shapeIds[direction].push(shapeId);
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
        const grepOutput = execSync(`grep -F "${shapeId}," "${shapesFile}"`, {
          encoding: 'utf8',
          maxBuffer: 50 * 1024 * 1024,
        });

        const shapePoints: { sequence: number; lat: number; lon: number }[] = [];
        for (const line of grepOutput.split('\n')) {
          if (!line.trim()) continue;
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

    shapeCache.set(lineNumber, { data: result, time: Date.now() });
    return NextResponse.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching line shape:', message);
    return NextResponse.json(
      { error: message, message: 'Failed to retrieve route shape data' },
      { status: 500 }
    );
  }
}
