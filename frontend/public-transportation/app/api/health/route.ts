import { NextResponse } from 'next/server';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

export async function GET() {
  try {
    await fetch(`${MOTIS_BASE}/api/v1/geocode?text=test`, {
      signal: AbortSignal.timeout(3000),
    });
    return NextResponse.json({ status: 'ok', motis: 'connected' });
  } catch {
    return NextResponse.json({ status: 'degraded', motis: 'unreachable' });
  }
}
