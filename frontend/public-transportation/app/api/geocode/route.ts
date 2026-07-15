import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

interface NominatimResult {
  display_name?: string;
  name?: string;
  addresstype?: string;
  lat?: string;
  lon?: string;
  type?: string;
  address?: {
    road?: string;
    house_number?: string;
    city?: string;
    town?: string;
    village?: string;
    hamlet?: string;
    neighbourhood?: string;
    [key: string]: string | undefined;
  };
}

function formatNominatimName(r: NominatimResult): string {
  const addr = r.address || {};
  const city = addr.village || addr.city || addr.town || addr.hamlet;

  if (r.name && r.addresstype && r.addresstype !== 'road' && r.addresstype !== 'house') {
    if (city && !r.name.includes(city)) return `${r.name}, ${city}`;
    return r.name;
  }

  if (addr.road) {
    const parts = [addr.house_number ? `${addr.road} ${addr.house_number}` : addr.road];
    if (city) parts.push(city);
    return parts.join(', ');
  }

  if (city && (r.display_name || '').length > 40) return city;
  return r.display_name || '';
}

async function nominatimSearch(text: string): Promise<unknown[]> {
  const response = await fetch(
    `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(text)}&format=json&accept-language=he&countrycodes=il&limit=10&addressdetails=1`,
    {
      signal: AbortSignal.timeout(5000),
      headers: { 'User-Agent': 'com.automatelinux.pt/1.0' }
    }
  );
  const results: NominatimResult[] = await response.json();
  return results.map(r => ({
    type: 'ADDRESS',
    name: formatNominatimName(r),
    lat: Number(r.lat),
    lon: Number(r.lon),
  }));
}

interface GeoResult {
  type: string;
  name: string;
  lat: number;
  lon: number;
  [key: string]: unknown;
}

function isDuplicate(a: GeoResult, b: GeoResult): boolean {
  const dlat = a.lat - b.lat;
  const dlon = a.lon - b.lon;
  return Math.sqrt(dlat * dlat + dlon * dlon) < 0.005;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const text = searchParams.get('text');

  if (!text) {
    return NextResponse.json(
      { error: 'Missing required parameter: text' },
      { status: 400 }
    );
  }

  await ensureMotis();

  const motisPromise = fetch(
    `${MOTIS_BASE}/api/v1/geocode?text=${encodeURIComponent(text)}`,
    { signal: AbortSignal.timeout(5000) }
  ).then(r => r.json()).catch(() => []);

  const nominatimPromise = nominatimSearch(text).catch(() => []);

  const [motisData, nominatimData] = await Promise.all([motisPromise, nominatimPromise]);

  const motisResults: GeoResult[] = Array.isArray(motisData) ? motisData : [];
  for (const item of motisResults) {
    if (item.name && Array.isArray(item.areas)) {
      const city = (item.areas as { name?: string; default?: boolean }[]).find(a => a.default);
      if (city?.name && !item.name.includes(city.name)) {
        item.name = `${item.name}, ${city.name}`;
      }
    }
  }

  const nominatimResults: GeoResult[] = Array.isArray(nominatimData) ? nominatimData as GeoResult[] : [];

  if (motisResults.length === 0) {
    return NextResponse.json(nominatimResults);
  }

  const uniqueNominatim = nominatimResults.filter(
    nr => !motisResults.some(mr => isDuplicate(mr, nr))
  );

  const merged = [...motisResults, ...uniqueNominatim];

  // Match case-insensitively: a query and a result name often differ in case,
  // especially for English/transliterated Israeli place names (a user typing
  // "haifa" against a "Haifa" result). A case-sensitive includes() scored those
  // as zero hits, so this relevance sort silently did nothing for any query
  // whose casing differed from the result. Lowercase both sides. Array.sort is
  // stable, so equal-hit results keep their order (MOTIS-first is preserved).
  const queryWords = text.toLowerCase().split(/\s+/).filter(w => w.length >= 2);
  if (queryWords.length > 0) {
    merged.sort((a, b) => {
      const aName = a.name.toLowerCase();
      const bName = b.name.toLowerCase();
      const aHits = queryWords.filter(w => aName.includes(w)).length;
      const bHits = queryWords.filter(w => bName.includes(w)).length;
      return bHits - aHits;
    });
  }

  return NextResponse.json(merged);
}
