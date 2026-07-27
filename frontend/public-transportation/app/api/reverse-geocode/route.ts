import { NextRequest, NextResponse } from 'next/server';
import { nominatim } from '@/lib/nominatim';

interface NominatimAddress {
  road?: string;
  house_number?: string;
  city?: string;
  town?: string;
  village?: string;
  hamlet?: string;
}

interface NominatimResult {
  display_name?: string;
  address?: NominatimAddress;
  lat?: string;
  lon?: string;
}

/**
 * Reverse geocodes repeat constantly — the map re-labels the same start and
 * destination pins on every trip, and Nominatim's anonymous quota is one
 * request per second for the whole deployment. Caching by rounded coordinate
 * (~1 m) keeps a session of map work down to a handful of upstream calls.
 */
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const CACHE_MAX_ENTRIES = 5000;
const cache = new Map<string, { name: string; at: number }>();

function cacheKey(lat: string, lon: string): string {
  return `${Number(lat).toFixed(5)},${Number(lon).toFixed(5)}`;
}

function cacheGet(key: string): string | null {
  const hit = cache.get(key);
  if (!hit) return null;
  if (Date.now() - hit.at > CACHE_TTL_MS) {
    cache.delete(key);
    return null;
  }
  return hit.name;
}

function cachePut(key: string, name: string): void {
  if (cache.size >= CACHE_MAX_ENTRIES) {
    // Map preserves insertion order, so the first key is the oldest write.
    const oldest = cache.keys().next().value;
    if (oldest !== undefined) cache.delete(oldest);
  }
  cache.set(key, { name, at: Date.now() });
}

/**
 * One upstream lookup per coordinate, even when several requests for it arrive
 * at once — the map asks for the start and destination pins together, and the
 * queue would otherwise spend a full second of its budget on a duplicate.
 */
const inFlight = new Map<string, Promise<string>>();

function resolve(key: string, lat: string, lon: string): Promise<string> {
  const pending = inFlight.get(key);
  if (pending) return pending;

  const lookup = (async () => {
    const data = await nominatim<NominatimResult>(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he`
    );
    const addr = data.address || {};
    const city = addr.village || addr.hamlet || addr.town || addr.city;

    const parts: string[] = [];
    if (addr.road) {
      parts.push(addr.house_number ? `${addr.road} ${addr.house_number}` : addr.road);
    }
    if (city) parts.push(city);
    const name = parts.length > 0 ? parts.join(', ') : `${lat}, ${lon}`;
    cachePut(key, name);
    return name;
  })().finally(() => inFlight.delete(key));

  inFlight.set(key, lookup);
  return lookup;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lat = searchParams.get('lat');
  const lon = searchParams.get('lon');

  if (!lat || !lon || !Number.isFinite(Number(lat)) || !Number.isFinite(Number(lon))) {
    return NextResponse.json(
      { error: 'Missing or invalid parameters: lat, lon' },
      { status: 400 }
    );
  }

  const key = cacheKey(lat, lon);
  const cached = cacheGet(key);
  if (cached) {
    return NextResponse.json([{ type: 'ADDRESS', name: cached, lat: Number(lat), lon: Number(lon) }]);
  }

  try {
    const name = await resolve(key, lat, lon);
    const result = [{ type: 'ADDRESS', name, lat: Number(lat), lon: Number(lon) }];
    return NextResponse.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching reverse-geocode from Nominatim:', message);
    return NextResponse.json(
      { error: 'Failed to reverse geocode', message },
      { status: 502 }
    );
  }
}
