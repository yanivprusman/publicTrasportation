import { NextRequest, NextResponse } from 'next/server';
import { geocode } from '@automatelinux/geo';
import { ensureMotis } from '@/lib/motis-manager';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

// The search itself — MOTIS geocoding merged with Nominatim, relevance first and
// proximity second — is @automatelinux/geo, shared with midreshaze and brownSigns.
export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const text = searchParams.get('text');
  // Optional "near=lat,lon" viewport bias: equally relevant name matches are
  // ordered closest-first, so "דיזנגוף סנטר" from a Tel Aviv map means the Tel
  // Aviv mall, not Netanya's similarly named one (MOTIS itself ignores its
  // place-bias parameter).
  const near = searchParams.get('near');
  let nearPoint: { lat: number; lon: number } | null = null;
  if (near) {
    const [lat, lon] = near.split(',').map(Number);
    if (Number.isFinite(lat) && Number.isFinite(lon)) nearPoint = { lat, lon };
  }

  if (!text) {
    return NextResponse.json(
      { error: 'Missing required parameter: text' },
      { status: 400 }
    );
  }

  await ensureMotis();

  return NextResponse.json(await geocode(text, { motisUrl: MOTIS_BASE, near: nearPoint }));
}
