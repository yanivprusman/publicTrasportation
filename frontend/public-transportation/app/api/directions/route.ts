import { NextRequest, NextResponse } from 'next/server';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const start = searchParams.get('start');
  const end = searchParams.get('end');
  const apiKey = process.env.ORS_API_KEY;

  try {
    const response = await fetch(
      `https://api.openrouteservice.org/v2/directions/driving-car?api_key=${apiKey}&start=${start}&end=${end}`,
      { signal: AbortSignal.timeout(10000) }
    );

    const data = await response.json();
    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching directions:', message);
    return NextResponse.json({ error: 'Failed to fetch directions' }, { status: 500 });
  }
}
