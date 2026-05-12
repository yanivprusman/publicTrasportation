import { NextRequest, NextResponse } from 'next/server';

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

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lat = searchParams.get('lat');
  const lon = searchParams.get('lon');

  if (!lat || !lon) {
    return NextResponse.json(
      { error: 'Missing required parameters: lat, lon' },
      { status: 400 }
    );
  }

  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he`,
      {
        signal: AbortSignal.timeout(5000),
        headers: { 'User-Agent': 'com.automatelinux.pt/1.0' }
      }
    );

    const data: NominatimResult = await response.json();
    const addr = data.address || {};
    const road = addr.road;
    const houseNumber = addr.house_number;
    const city = addr.village || addr.hamlet || addr.town || addr.city;

    const parts: string[] = [];
    if (road) {
      parts.push(houseNumber ? `${road} ${houseNumber}` : road);
    }
    if (city) parts.push(city);
    const name = parts.length > 0 ? parts.join(', ') : `${lat}, ${lon}`;

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
