import { NextRequest, NextResponse } from 'next/server';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

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

    // Append city name from areas to each result's name so users can see which city a place is in
    if (Array.isArray(data)) {
      for (const item of data) {
        if (item.name && Array.isArray(item.areas)) {
          const city = item.areas.find((a: { default?: boolean }) => a.default);
          if (city?.name && !item.name.includes(city.name)) {
            item.name = `${item.name}, ${city.name}`;
          }
        }
      }
    }

    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching geocode from MOTIS:', message);
    return NextResponse.json(
      { error: 'Failed to geocode', message },
      { status: 502 }
    );
  }
}
