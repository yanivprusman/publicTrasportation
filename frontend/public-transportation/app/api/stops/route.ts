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

// Hard cap on stops returned for a map viewport. A phone-sized viewport at the
// layer's minimum zoom stays well under this; only huge desktop viewports over
// dense metro areas can hit it, and those get the stops nearest the view
// center so truncation trims the edges rather than dropping stops arbitrarily.
const MAX_BBOX_STOPS = 900;

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

  const bboxParam = request.nextUrl.searchParams.get('bbox');
  if (bboxParam) {
    const parts = bboxParam.split(',').map(p => parseFloat(p.trim()));
    if (parts.length !== 4 || parts.some(n => isNaN(n))) {
      return NextResponse.json(
        { error: 'bbox must be "minLat,minLon,maxLat,maxLon"' },
        { status: 400 },
      );
    }
    const [minLat, minLon, maxLat, maxLon] = parts;

    // Dedupe by stop code: the map keys markers by code, and a rare duplicate
    // row in stops.txt must not produce two markers for one physical stop.
    const seen = new Set<string>();
    const inView: Stop[] = [];
    for (const stop of stops) {
      if (
        stop.lat >= minLat && stop.lat <= maxLat &&
        stop.lon >= minLon && stop.lon <= maxLon &&
        !seen.has(stop.stopCode)
      ) {
        seen.add(stop.stopCode);
        inView.push(stop);
      }
    }

    if (inView.length > MAX_BBOX_STOPS) {
      const cLat = (minLat + maxLat) / 2;
      const cLon = (minLon + maxLon) / 2;
      const lonScale = Math.cos(cLat * Math.PI / 180);
      const dist2 = (s: Stop) => {
        const dLat = s.lat - cLat;
        const dLon = (s.lon - cLon) * lonScale;
        return dLat * dLat + dLon * dLon;
      };
      inView.sort((a, b) => dist2(a) - dist2(b));
      inView.length = MAX_BBOX_STOPS;
    }

    return NextResponse.json(inView);
  }

  if (latParam && lonParam) {
    const lat = parseFloat(latParam);
    const lon = parseFloat(lonParam);
    const radius = radiusParam ? parseFloat(radiusParam) : 500;
    if (isNaN(lat) || isNaN(lon)) return NextResponse.json([]);

    const degPerMeter = 1 / 111320;
    const latDelta = radius * degPerMeter;
    const lonDelta = radius * degPerMeter / Math.cos(lat * Math.PI / 180);

    const nearby: { stopCode: string; stopName: string; lat: number; lon: number; distanceMeters: number }[] = [];
    for (const stop of stops) {
      if (
        Math.abs(stop.lat - lat) <= latDelta &&
        Math.abs(stop.lon - lon) <= lonDelta
      ) {
        const dlat = (stop.lat - lat) * 111320;
        const dlon = (stop.lon - lon) * 111320 * Math.cos(lat * Math.PI / 180);
        const dist = Math.round(Math.sqrt(dlat * dlat + dlon * dlon));
        // The box test above is a cheap pre-filter; enforce the true radius so
        // "within N meters" means the circle, not the bounding square's corners.
        if (dist <= radius) nearby.push({ ...stop, distanceMeters: dist });
      }
    }
    // Sort before capping: truncating in file order can drop the nearest stops
    // in dense areas (>100 stops in the box), which breaks nearest-stop consumers.
    nearby.sort((a, b) => a.distanceMeters - b.distanceMeters);
    return NextResponse.json(nearby.slice(0, 100));
  }

  if (!q) return NextResponse.json([]);

  const qLower = q.toLowerCase();

  // Rank matches before capping at 8, rather than taking the first 8 in
  // file order: an exact stop-code hit or a name that starts with the query
  // is far more relevant than an arbitrary mid-name substring match, but
  // truncating in file order can bury or drop those best matches entirely.
  // 0 = exact code, 1 = code prefix, 2 = name prefix, 3 = name substring.
  const ranked: { stop: Stop; rank: number }[] = [];
  for (const stop of stops) {
    const nameLower = stop.stopName.toLowerCase();
    let rank: number;
    if (stop.stopCode === q) rank = 0;
    else if (stop.stopCode.startsWith(q)) rank = 1;
    else if (nameLower.startsWith(qLower)) rank = 2;
    else if (nameLower.includes(qLower)) rank = 3;
    else continue;
    ranked.push({ stop, rank });
  }

  ranked.sort((a, b) =>
    a.rank - b.rank ||
    a.stop.stopName.length - b.stop.stopName.length ||
    a.stop.stopName.localeCompare(b.stop.stopName)
  );

  return NextResponse.json(ranked.slice(0, 8).map(r => r.stop));
}
