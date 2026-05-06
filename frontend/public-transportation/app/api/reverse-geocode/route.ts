import { NextRequest, NextResponse } from 'next/server';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

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
      `${MOTIS_BASE}/api/v1/reverse-geocode?place=${lat},${lon}`,
      { signal: AbortSignal.timeout(5000) }
    );

    const data = await response.json();

    if (Array.isArray(data)) {
      for (const item of data) {
        if (item.name && Array.isArray(item.areas)) {
          const city = item.areas.find((a: { default?: boolean }) => a.default);
          if (city?.name && !item.name.includes(city.name)) {
            item.name = `${item.name}, ${city.name}`;
          }
        }
      }
      // Sort ADDRESS results first — POIs like "birding site" are less useful for routing
      data.sort((a: { type?: string }, b: { type?: string }) => {
        if (a.type === 'ADDRESS' && b.type !== 'ADDRESS') return -1;
        if (a.type !== 'ADDRESS' && b.type === 'ADDRESS') return 1;
        return 0;
      });
    }

    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching reverse-geocode from MOTIS:', message);
    return NextResponse.json(
      { error: 'Failed to reverse geocode', message },
      { status: 502 }
    );
  }
}
