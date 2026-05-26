import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';

interface Stop {
  stopCode: string;
  stopName: string;
  lat: number;
  lon: number;
}

let stopsCache: Stop[] | null = null;

function loadStops(): Stop[] {
  if (stopsCache) return stopsCache;

  const stopsFile = path.join(process.cwd(), '../../gtfs/israel-public-transportation/stops.txt');
  if (!fs.existsSync(stopsFile)) return [];

  const content = fs.readFileSync(stopsFile, 'utf8');
  const lines = content.split('\n');
  const header = lines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
  const codeIdx = header.indexOf('stop_code');
  const nameIdx = header.indexOf('stop_name');
  const latIdx = header.indexOf('stop_lat');
  const lonIdx = header.indexOf('stop_lon');
  const typeIdx = header.indexOf('location_type');

  if (codeIdx === -1 || nameIdx === -1 || latIdx === -1 || lonIdx === -1) return [];

  const stops: Stop[] = [];
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');

    // Filter to location_type=0 (actual stops, not parent stations)
    if (typeIdx !== -1 && cols[typeIdx]?.trim() !== '0') continue;

    const code = cols[codeIdx]?.trim();
    const name = cols[nameIdx]?.trim();
    const lat = parseFloat(cols[latIdx]?.trim());
    const lon = parseFloat(cols[lonIdx]?.trim());

    if (code && name && !isNaN(lat) && !isNaN(lon)) {
      stops.push({ stopCode: code, stopName: name, lat, lon });
    }
  }

  stopsCache = stops;
  return stops;
}

export async function GET(request: NextRequest) {
  const q = request.nextUrl.searchParams.get('q')?.trim() || '';
  const latParam = request.nextUrl.searchParams.get('lat');
  const lonParam = request.nextUrl.searchParams.get('lon');
  const radiusParam = request.nextUrl.searchParams.get('radius');

  const stops = loadStops();

  if (latParam && lonParam) {
    const lat = parseFloat(latParam);
    const lon = parseFloat(lonParam);
    const radius = radiusParam ? parseFloat(radiusParam) : 500;
    if (isNaN(lat) || isNaN(lon)) return NextResponse.json([]);

    const degPerMeter = 1 / 111320;
    const latDelta = radius * degPerMeter;
    const lonDelta = radius * degPerMeter / Math.cos(lat * Math.PI / 180);

    const nearby: Stop[] = [];
    for (const stop of stops) {
      if (
        Math.abs(stop.lat - lat) <= latDelta &&
        Math.abs(stop.lon - lon) <= lonDelta
      ) {
        nearby.push(stop);
        if (nearby.length >= 100) break;
      }
    }
    return NextResponse.json(nearby);
  }

  if (!q) return NextResponse.json([]);

  const qLower = q.toLowerCase();
  const results: Stop[] = [];

  for (const stop of stops) {
    if (
      stop.stopName.toLowerCase().includes(qLower) ||
      stop.stopCode.startsWith(q)
    ) {
      results.push(stop);
      if (results.length >= 8) break;
    }
  }

  return NextResponse.json(results);
}
