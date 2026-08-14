import fs from 'fs';
import path from 'path';

/**
 * `stop_id` -> the two other numbers a stop has.
 *
 * MOTIS identifies a stop by GTFS `stop_id` ("israel_26635"), but nothing else does:
 * the public sign on the pole, SIRI's MonitoringRef and every other app on the phone
 * use `stop_code` (13868), and the fare table keys on `zone_id`. In this feed zone_id
 * happens to equal stop_code, but they are read separately because that is a property
 * of the Israeli feed, not of GTFS.
 */
export interface StopIdentity {
  stopCode: string;
  zoneId: string;
  name: string;
}

const STOPS_FILE = path.join(
  process.cwd(),
  '../../gtfs/israel-public-transportation/stops.txt'
);

// ~35k stops. Built once, then every lookup is free — same lifetime policy as
// gtfs-trips, and for the same reason: the file never changes under a running server.
let cache: Map<string, StopIdentity> | null = null;

function load(): Map<string, StopIdentity> {
  if (cache) return cache;

  if (!fs.existsSync(STOPS_FILE)) {
    // Loud: without it, legs lose their stop codes and the fare goes with them.
    console.error(`gtfs-stops: ${STOPS_FILE} missing — legs will carry no stop code or fare`);
    cache = new Map();
    return cache;
  }

  const lines = fs.readFileSync(STOPS_FILE, 'utf8').split('\n');
  const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
  const idIdx = header.indexOf('stop_id');
  const codeIdx = header.indexOf('stop_code');
  const nameIdx = header.indexOf('stop_name');
  const zoneIdx = header.indexOf('zone_id');

  const map = new Map<string, StopIdentity>();
  if (idIdx === -1 || codeIdx === -1) {
    console.error('gtfs-stops: stops.txt has no stop_id/stop_code column');
    cache = map;
    return cache;
  }

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.trim()) continue;
    // stop_name is quoted when it contains a comma; the columns we need sit
    // before it, so a plain split is safe for id/code and for the trailing
    // zone_id only when the name is unquoted. Rows with a quoted name are
    // parsed properly rather than skipped — they are real stops.
    const cols = line.includes('"') ? splitCsv(line) : line.split(',');
    const id = cols[idIdx]?.trim();
    if (!id) continue;
    map.set(id, {
      stopCode: cols[codeIdx]?.trim() || '',
      zoneId: zoneIdx === -1 ? '' : (cols[zoneIdx]?.trim() || ''),
      name: nameIdx === -1 ? '' : (cols[nameIdx]?.trim().replace(/^"|"$/g, '') || ''),
    });
  }

  cache = map;
  return cache;
}

function splitCsv(line: string): string[] {
  const out: string[] = [];
  let cur = '';
  let quoted = false;
  for (const ch of line) {
    if (ch === '"') quoted = !quoted;
    else if (ch === ',' && !quoted) { out.push(cur); cur = ''; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

/**
 * MOTIS prefixes every stop id with its feed name. Strips it, so callers can pass
 * what MOTIS gave them without knowing that.
 */
export function stopIdentity(motisStopId: string | undefined): StopIdentity | null {
  if (!motisStopId) return null;
  const id = motisStopId.replace(/^[a-z0-9]+_/i, '');
  return load().get(id) || null;
}
