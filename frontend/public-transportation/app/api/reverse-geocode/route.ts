import { NextRequest, NextResponse } from 'next/server';
import { reverseGeocode } from '@automatelinux/geo';

// The lookup — Nominatim through its rate-limit queue, cached per ~1 m and
// deduplicated while in flight — is @automatelinux/geo, shared with midreshaze.
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

  try {
    const name = await reverseGeocode(Number(lat), Number(lon));
    return NextResponse.json([{ type: 'ADDRESS', name, lat: Number(lat), lon: Number(lon) }]);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching reverse-geocode from Nominatim:', message);
    return NextResponse.json(
      { error: 'Failed to reverse geocode', message },
      { status: 502 }
    );
  }
}
