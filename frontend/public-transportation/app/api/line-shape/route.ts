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
const GTFS_REQUIRED_FILES = ['routes.txt', 'trips.txt', 'shapes.txt', 'agency.txt'];
const SHAPES_PATH = path.join(GTFS_DATA_DIR, 'shapes.txt');

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

/**
 * One line of a number: line numbers repeat across Israel (there are a dozen 64s), and
 * GTFS tells them apart by route_desc's first field, the line code ("22064" is Be'er
 * Sheva–Mitzpe Ramon, "13064" Jerusalem's). Its route_ids are its directions and their
 * alternatives.
 */
interface LineVariant {
  id: string;
  /** "Be'er Sheva ↔ Mitzpe Ramon" — the two cities from route_long_name. */
  label: string;
  agency: string;
  /** Per direction_id: the most-run shape's points, and the route_long_name it runs under. */
  shapes: Record<string, number[][]>;
  headsigns: Record<string, string>;
}

const lineCache = new Map<string, { variants: LineVariant[]; time: number }>();
const LINE_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours

/** A GTFS CSV row. No field in routes/trips/agency carries a quoted comma. */
function cols(line: string): string[] {
  return line.split(',').map(c => c.trim());
}

function header(content: string): string[] {
  const first = content.slice(0, content.indexOf('\n'));
  return first.replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
}

/** "stop-city<->stop-city-1#" → "city ↔ city", or the one city for a line inside it. */
function variantLabel(longName: string): string {
  const [from, to] = longName.split('<->');
  if (!to) return longName;
  const city = (end: string) => end.substring(end.lastIndexOf('-') + 1).trim();
  const a = city(from);
  const b = city(to.replace(/-[^-]*$/, ''));
  // GTFS writes gershayim as two apostrophes (חב''ד).
  return (a === b ? a : `${a} ↔ ${b}`).replace(/''/g, '"');
}

function loadVariants(lineNumber: string): LineVariant[] {
  const routes = fs.readFileSync(path.join(GTFS_DATA_DIR, 'routes.txt'), 'utf8');
  const rh = header(routes);
  const [rId, rAgency, rShort, rLong, rDesc] =
    ['route_id', 'agency_id', 'route_short_name', 'route_long_name', 'route_desc'].map(c => rh.indexOf(c));
  if (rId === -1 || rShort === -1) throw new Error('Required columns not found in routes.txt');

  const agencies = new Map<string, string>();
  const agencyContent = fs.readFileSync(path.join(GTFS_DATA_DIR, 'agency.txt'), 'utf8');
  const ah = header(agencyContent);
  for (const line of agencyContent.split('\n').slice(1)) {
    const c = cols(line);
    if (c[ah.indexOf('agency_id')]) agencies.set(c[ah.indexOf('agency_id')], c[ah.indexOf('agency_name')] || '');
  }

  // route_id → which line (by code) it belongs to, and its long name.
  const routeLine = new Map<string, { code: string; longName: string; agencyId: string }>();
  for (const line of routes.split('\n').slice(1)) {
    const c = cols(line);
    if (c[rShort] !== lineNumber) continue;
    // A feed without route_desc still gets one line per route_id rather than none.
    const code = (rDesc !== -1 && c[rDesc]?.split('-')[0]) || c[rId];
    routeLine.set(c[rId], { code, longName: rLong !== -1 ? c[rLong] : '', agencyId: rAgency !== -1 ? c[rAgency] : '' });
  }
  if (routeLine.size === 0) throw new Error(`No routes found for line ${lineNumber}`);

  // Per line and direction: how many trips each shape carries, and each route's long name.
  const trips = fs.readFileSync(path.join(GTFS_DATA_DIR, 'trips.txt'), 'utf8');
  const th = header(trips);
  const [tRoute, tShape, tDir] = ['route_id', 'shape_id', 'direction_id'].map(c => th.indexOf(c));
  if (tRoute === -1 || tShape === -1) throw new Error('Required columns not found in trips.txt');
  const counts = new Map<string, Map<string, Map<string, number>>>(); // code → dir → shape → trips
  const shapeRoute = new Map<string, string>();
  for (const line of trips.split('\n').slice(1)) {
    const comma = line.indexOf(',');
    const route = routeLine.get(line.slice(0, comma).trim());
    if (!route) continue;
    const c = cols(line);
    const shape = c[tShape];
    if (!shape) continue;
    const dir = tDir !== -1 && c[tDir] ? c[tDir] : '0';
    const byDir = counts.get(route.code) ?? new Map();
    counts.set(route.code, byDir);
    const byShape = byDir.get(dir) ?? new Map();
    byDir.set(dir, byShape);
    byShape.set(shape, (byShape.get(shape) ?? 0) + 1);
    shapeRoute.set(shape, c[tRoute]);
  }

  // The shape most trips run on stands for its direction — a short-turn or a school
  // detour is still the line, but not the one to draw.
  const chosen = new Map<string, Record<string, string>>(); // code → dir → shape
  for (const [code, byDir] of counts) {
    const dirs: Record<string, string> = {};
    for (const [dir, byShape] of byDir) {
      dirs[dir] = [...byShape].sort((a, b) => b[1] - a[1])[0][0];
    }
    chosen.set(code, dirs);
  }
  const wanted = new Set([...chosen.values()].flatMap(d => Object.values(d)));
  if (wanted.size === 0) throw new Error(`No shapes found for line ${lineNumber}`);

  // One pass over shapes.txt (~230 MB) for every shape of every line of this number.
  const points = new Map<string, { seq: number; lat: number; lon: number }[]>();
  const output = execFileSync('grep', ['-F', ...[...wanted].flatMap(id => ['-e', `${id},`]), SHAPES_PATH], {
    encoding: 'utf8',
    maxBuffer: 200 * 1024 * 1024,
  });
  for (const line of output.split('\n')) {
    // grep -F matches anywhere on the row — inside a longer shape id or a coordinate.
    // Only an exact first column counts.
    const comma = line.indexOf(',');
    const id = line.slice(0, comma);
    if (!wanted.has(id)) continue;
    const c = line.split(',');
    if (c.length < 4) continue;
    const list = points.get(id) ?? [];
    points.set(id, list);
    list.push({ seq: parseInt(c[3], 10), lat: parseFloat(c[1]), lon: parseFloat(c[2]) });
  }

  const variants: LineVariant[] = [];
  for (const [code, dirs] of chosen) {
    const shapes: Record<string, number[][]> = {};
    const headsigns: Record<string, string> = {};
    for (const [dir, shape] of Object.entries(dirs)) {
      const pts = points.get(shape);
      if (!pts?.length) continue;
      shapes[dir] = pts.sort((a, b) => a.seq - b.seq).map(p => [p.lat, p.lon]);
      const route = routeLine.get(shapeRoute.get(shape) || '');
      if (route?.longName) headsigns[dir] = route.longName;
    }
    if (Object.keys(shapes).length === 0) continue;
    const any = [...routeLine.values()].find(r => r.code === code)!;
    const first = headsigns[Object.keys(headsigns).sort()[0]] || any.longName;
    variants.push({ id: code, label: variantLabel(first), agency: agencies.get(any.agencyId) || '', shapes, headsigns });
  }
  if (variants.length === 0) throw new Error(`No shape points found for line ${lineNumber}`);
  return variants;
}

/** Metres from (lat, lon) to the nearest point of any of the variant's shapes. */
function distanceTo(v: LineVariant, lat: number, lon: number): number {
  const kx = 111_320 * Math.cos((lat * Math.PI) / 180);
  let best = Infinity;
  for (const pts of Object.values(v.shapes)) {
    for (const [pLat, pLon] of pts) {
      const d = Math.hypot((pLon - lon) * kx, (pLat - lat) * 111_320);
      if (d < best) best = d;
    }
  }
  return Math.round(best);
}

/**
 * `GET /api/line-shape?line=64[&near=lat,lon][&variant=22064][&meta=full]`
 *
 * Draws one line of that number: [variant] when asked for, else the one nearest
 * [near], else the first. A number alone used to merge every 64 in the country into
 * two "directions" and draw whichever shape came first in trips.txt — Jerusalem's, for
 * a rider in the Negev (2026-10-12). With meta=full, `variants` lists every line of the
 * number, nearest first, so the rider can pick another.
 */
export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lineNumber = (searchParams.get('line') || '60').trim();
  const withMeta = searchParams.get('meta') === 'full';
  const variantParam = searchParams.get('variant');
  const nearParam = searchParams.get('near');
  let near: [number, number] | null = null;
  if (nearParam !== null) {
    const [lat, lon] = nearParam.split(',').map(Number);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      return NextResponse.json({ error: 'Invalid near parameter (format: lat,lon)' }, { status: 400 });
    }
    near = [lat, lon];
  }

  try {
    await ensureGtfsData();
    let cached = lineCache.get(lineNumber);
    if (!cached || Date.now() - cached.time >= LINE_CACHE_TTL) {
      cached = { variants: loadVariants(lineNumber), time: Date.now() };
      lineCache.set(lineNumber, cached);
    }
    const ranked = cached.variants.map(v => ({ v, distanceM: near ? distanceTo(v, near[0], near[1]) : null }));
    if (near) ranked.sort((a, b) => a.distanceM! - b.distanceM!);

    let shown = ranked[0].v;
    if (variantParam !== null) {
      const asked = cached.variants.find(v => v.id === variantParam);
      if (!asked) {
        return NextResponse.json({ error: `Line ${lineNumber} has no variant ${variantParam}` }, { status: 404 });
      }
      shown = asked;
    }

    // The Android client deserializes the default response as a flat
    // Map<direction, points>, so new fields can only ship behind ?meta=full.
    if (!withMeta) return NextResponse.json(shown.shapes);
    return NextResponse.json({
      directions: shown.shapes,
      headsigns: shown.headsigns,
      variant: shown.id,
      variants: ranked.map(({ v, distanceM }) => ({ id: v.id, label: v.label, agency: v.agency, distanceM })),
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching line shape:', message);
    return NextResponse.json(
      { error: message, message: 'Failed to retrieve route shape data' },
      { status: 500 }
    );
  }
}
