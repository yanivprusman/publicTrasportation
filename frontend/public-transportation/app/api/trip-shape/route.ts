import { NextRequest, NextResponse } from 'next/server';
import { execFileSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { routeShapeId, tripShapeId } from '@/lib/gtfs-trips';
import { fileStamp } from '@/lib/file-stamp';

/**
 * The geometry of one trip, or of one route in one direction.
 *
 * This exists because /api/line-shape resolves by `route_short_name`, which is
 * not unique in Israel — asking it for "60" while riding the Negev's line 60
 * returns Tel Aviv's and Haifa's, ~200 km away.
 *
 * Two callers, two keys, both exact:
 *   ?tripId=   a planned itinerary leg, which carries a MOTIS trip id
 *   ?routeId=  a live SIRI arrival, whose LineRef is the route_id
 */

const SHAPES_FILE = path.join(
  process.cwd(),
  '../../gtfs/israel-public-transportation/shapes.txt'
);

// shapes.txt is ~230 MB, so it is never read into the process. grep pulls the
// few thousand rows for one shape in milliseconds; the result is small and is
// cached, because a trip's geometry cannot change while the feed is unchanged.
const shapeCache = new Map<string, number[][]>();
const SHAPE_CACHE_MAX = 200;
// Flushed when the nightly update replaces shapes.txt — shape ids are per-feed,
// and a stale cache would keep answering with geometry the feed no longer has
// (see lib/file-stamp.ts).
let shapesStamp = -2;

function loadShape(shapeId: string): number[][] {
  const stamp = fileStamp(SHAPES_FILE);
  if (stamp !== shapesStamp) {
    shapeCache.clear();
    shapesStamp = stamp;
  }
  const cached = shapeCache.get(shapeId);
  if (cached) return cached;

  const output = execFileSync('grep', ['-F', `${shapeId},`, SHAPES_FILE], {
    encoding: 'utf8',
    maxBuffer: 50 * 1024 * 1024,
  });

  const rows: { sequence: number; lat: number; lon: number }[] = [];
  for (const line of output.split('\n')) {
    if (!line.trim()) continue;
    // grep -F matches anywhere on the row: shape id "1558" also hits "155881,"
    // and even a coordinate like "34.1558,". Only an exact first column counts.
    if (!line.startsWith(`${shapeId},`)) continue;
    const cols = line.split(',');
    if (cols.length < 4) continue;
    rows.push({
      lat: parseFloat(cols[1]),
      lon: parseFloat(cols[2]),
      sequence: parseInt(cols[3], 10),
    });
  }

  rows.sort((a, b) => a.sequence - b.sequence);
  const points = rows
    .filter(r => Number.isFinite(r.lat) && Number.isFinite(r.lon))
    .map(r => [r.lat, r.lon]);

  if (shapeCache.size >= SHAPE_CACHE_MAX) {
    const oldest = shapeCache.keys().next().value;
    if (oldest) shapeCache.delete(oldest);
  }
  shapeCache.set(shapeId, points);
  return points;
}

export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const tripId = params.get('tripId');
  const routeId = params.get('routeId');
  if (!tripId && !routeId) {
    return NextResponse.json(
      { error: 'Missing required parameter: tripId or routeId' },
      { status: 400 }
    );
  }

  if (!fs.existsSync(SHAPES_FILE)) {
    return NextResponse.json(
      { error: `shapes.txt not found — run motis/update-data.sh to extract it` },
      { status: 503 }
    );
  }

  const shapeId = tripId ? tripShapeId(tripId) : routeShapeId(routeId!);
  if (!shapeId) {
    const what = tripId ? `trip ${tripId}` : `route ${routeId}`;
    return NextResponse.json({ error: `No shape for ${what}` }, { status: 404 });
  }

  try {
    const points = loadShape(shapeId);
    if (points.length === 0) {
      return NextResponse.json(
        { error: `Shape ${shapeId} has no points — shapes.txt may be older than trips.txt` },
        { status: 404 }
      );
    }
    return NextResponse.json({ shapeId, points });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('trip-shape failed:', message);
    return NextResponse.json({ error: 'Failed to read shape', message }, { status: 500 });
  }
}
