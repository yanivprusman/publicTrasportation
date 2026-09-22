import { NextRequest, NextResponse } from 'next/server';
import { ensureMotis } from '@/lib/motis-manager';
import { suggestLines } from '@/lib/onboard';

/**
 * Which lines the rider could be on: `GET /api/riding?at=lat,lon[&heading=deg]`.
 *
 * Suggestions only — the rider says which bus they are on, and /api/route?onLine=
 * plans from it. Lines are those whose timetable has them passing here now; with a
 * heading, only those going the rider's way.
 */
export async function GET(request: NextRequest) {
  const at = request.nextUrl.searchParams.get('at');
  const headingParam = request.nextUrl.searchParams.get('heading');
  const [lat, lon] = (at || '').split(',').map(Number);
  if (!at || !Number.isFinite(lat) || !Number.isFinite(lon)) {
    return NextResponse.json({ error: 'Missing or invalid at parameter (format: lat,lon)' }, { status: 400 });
  }
  let heading: number | null = null;
  if (headingParam !== null) {
    heading = Number(headingParam);
    if (!Number.isFinite(heading) || heading < 0 || heading >= 360) {
      return NextResponse.json(
        { error: 'Invalid heading parameter. Expected degrees from 0 (inclusive) to 360 (exclusive).' },
        { status: 400 }
      );
    }
  }
  await ensureMotis();
  try {
    return NextResponse.json({ lines: await suggestLines(lat, lon, heading, Date.now()) });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error suggesting riding lines:', message);
    return NextResponse.json({ error: 'Failed to suggest lines', message }, { status: 502 });
  }
}
