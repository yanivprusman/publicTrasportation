import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const stopId = searchParams.get('stopId');
  const n = searchParams.get('n') || '20';

  if (!stopId) {
    return NextResponse.json(
      { error: 'Missing required parameter: stopId' },
      { status: 400 }
    );
  }

  await ensureMotis();

  try {
    const response = await fetch(
      `${MOTIS_BASE}/api/v1/stoptimes?stopId=${encodeURIComponent(stopId)}&n=${n}`,
      { signal: AbortSignal.timeout(5000) }
    );

    const data = await response.json();
    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching stoptimes from MOTIS:', message);
    return NextResponse.json(
      { error: 'Failed to fetch stop times', message },
      { status: 502 }
    );
  }
}
