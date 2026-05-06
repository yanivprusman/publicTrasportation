import { NextRequest, NextResponse } from 'next/server';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

interface NominatimResult {
  display_name?: string;
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
  };
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
  return results.map(r => {
    const addr = r.address || {};
    const city = addr.city || addr.town || addr.village || addr.hamlet;
    let name = r.display_name || '';
    if (addr.road) {
      const parts = [addr.house_number ? `${addr.road} ${addr.house_number}` : addr.road];
      if (city) parts.push(city);
      name = parts.join(', ');
    } else if (city && name.length > 40) {
      name = city;
    }
    return {
      type: 'ADDRESS',
      name,
      lat: Number(r.lat),
      lon: Number(r.lon),
    };
  });
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

  try {
    const response = await fetch(
      `${MOTIS_BASE}/api/v1/geocode?text=${encodeURIComponent(text)}`,
      { signal: AbortSignal.timeout(5000) }
    );

    const data = await response.json();

    if (Array.isArray(data) && data.length > 0) {
      for (const item of data) {
        if (item.name && Array.isArray(item.areas)) {
          const city = item.areas.find((a: { default?: boolean }) => a.default);
          if (city?.name && !item.name.includes(city.name)) {
            item.name = `${item.name}, ${city.name}`;
          }
        }
      }
      return NextResponse.json(data);
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('MOTIS geocode failed, falling back to Nominatim:', message);
  }

  try {
    const results = await nominatimSearch(text);
    return NextResponse.json(results);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Nominatim geocode also failed:', message);
    return NextResponse.json(
      { error: 'Failed to geocode', message },
      { status: 502 }
    );
  }
}
