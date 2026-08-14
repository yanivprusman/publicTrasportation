import fs from 'fs';
import path from 'path';

/**
 * What a ride actually costs, from the operators' own fare table.
 *
 * The app used to price a journey at a flat ₪5.50 per bus leg, which put Midreshet
 * Ben-Gurion → Be'er Sheva at about ₪11 when both Moovit and Bus Nearby say ₪19.
 * A price that is wrong is worse than no price, and the Israeli feed ships the real
 * one: `fare_attributes.txt` holds 17 tiers, `fare_rules.txt` maps a (origin zone,
 * destination zone) pair to a tier — 985,573 of them.
 *
 * The table is held as three parallel typed arrays sorted by a packed key rather than
 * a Map: a Map of a million string keys costs well over 100 MB, the arrays cost ~9 MB,
 * and a binary search over them is fast enough to run per leg on every route request.
 *
 * `fare_rules` is sparse on purpose — it holds only pairs some single ride actually
 * connects. A missing pair therefore means "no ride goes from here to there", which is
 * why a leg without a rule reports null instead of a guess, and an itinerary containing
 * one reports no total at all.
 */

const GTFS_DIR = path.join(process.cwd(), '../../gtfs/israel-public-transportation');
const ATTRS_FILE = path.join(GTFS_DIR, 'fare_attributes.txt');
const RULES_FILE = path.join(GTFS_DIR, 'fare_rules.txt');

// zone ids are numeric and 7 digits or fewer, so origin * 1e7 + destination is exact
// in a double (< 2^53) and orders the pairs the same way a two-column sort would.
const KEY_SCALE = 1e7;

interface FareTable {
  keys: Float64Array;
  tiers: Uint8Array;
  prices: number[];
}

let cache: FareTable | null = null;

function load(): FareTable {
  if (cache) return cache;

  const empty: FareTable = { keys: new Float64Array(0), tiers: new Uint8Array(0), prices: [] };

  if (!fs.existsSync(ATTRS_FILE) || !fs.existsSync(RULES_FILE)) {
    console.error(`gtfs-fares: ${ATTRS_FILE} or ${RULES_FILE} missing — every leg will report no fare`);
    cache = empty;
    return cache;
  }

  // fare_id -> index into prices[]
  const tierIndex = new Map<string, number>();
  const prices: number[] = [];
  {
    const lines = fs.readFileSync(ATTRS_FILE, 'utf8').split('\n');
    const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
    const idIdx = header.indexOf('fare_id');
    const priceIdx = header.indexOf('price');
    for (let i = 1; i < lines.length; i++) {
      if (!lines[i].trim()) continue;
      const cols = lines[i].split(',');
      const id = cols[idIdx]?.trim();
      const price = parseFloat(cols[priceIdx]?.trim() || '');
      if (!id || !Number.isFinite(price)) continue;
      tierIndex.set(id, prices.length);
      prices.push(price);
    }
  }
  if (prices.length > 255) {
    console.error(`gtfs-fares: ${prices.length} fare tiers exceeds the Uint8 tier index`);
    cache = empty;
    return cache;
  }

  const raw = fs.readFileSync(RULES_FILE, 'utf8');
  const lines = raw.split('\n');
  const header = lines[0].replace(/﻿/g, '').split(',').map(c => c.trim().toLowerCase());
  const fareIdx = header.indexOf('fare_id');
  const originIdx = header.indexOf('origin_id');
  const destIdx = header.indexOf('destination_id');
  if (fareIdx === -1 || originIdx === -1 || destIdx === -1) {
    console.error('gtfs-fares: fare_rules.txt is missing fare_id/origin_id/destination_id');
    cache = empty;
    return cache;
  }

  const n = lines.length;
  const keys = new Float64Array(n);
  const tiers = new Uint8Array(n);
  let count = 0;
  for (let i = 1; i < n; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');
    const tier = tierIndex.get(cols[fareIdx]?.trim() || '');
    if (tier === undefined) continue;
    const origin = Number(cols[originIdx]?.trim());
    const dest = Number(cols[destIdx]?.trim());
    if (!Number.isFinite(origin) || !Number.isFinite(dest) || !origin || !dest) continue;
    keys[count] = origin * KEY_SCALE + dest;
    tiers[count] = tier;
    count++;
  }

  // Sort both arrays by key. Sorting an index permutation keeps the pairing without
  // materialising a million objects.
  const order = new Uint32Array(count);
  for (let i = 0; i < count; i++) order[i] = i;
  const sorted = Array.prototype.slice.call(order).sort((a: number, b: number) => keys[a] - keys[b]);
  const sortedKeys = new Float64Array(count);
  const sortedTiers = new Uint8Array(count);
  for (let i = 0; i < count; i++) {
    sortedKeys[i] = keys[sorted[i]];
    sortedTiers[i] = tiers[sorted[i]];
  }

  cache = { keys: sortedKeys, tiers: sortedTiers, prices };
  return cache;
}

/**
 * The single-ride price between two fare zones, or null when the table has no rule
 * for the pair — which means no single ride connects them.
 */
export function fareBetween(originZone: string | undefined, destZone: string | undefined): number | null {
  if (!originZone || !destZone) return null;
  const origin = Number(originZone);
  const dest = Number(destZone);
  if (!Number.isFinite(origin) || !Number.isFinite(dest) || !origin || !dest) return null;

  const { keys, tiers, prices } = load();
  const target = origin * KEY_SCALE + dest;
  let lo = 0;
  let hi = keys.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const k = keys[mid];
    if (k === target) return prices[tiers[mid]] ?? null;
    if (k < target) lo = mid + 1;
    else hi = mid - 1;
  }
  return null;
}
