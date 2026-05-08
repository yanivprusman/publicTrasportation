import { execSync } from 'child_process';
import { NextResponse } from 'next/server';

const MOTIS_PORT = process.env.MOTIS_PORT || '3504';
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

function getGitCommit(): string | null {
  try {
    return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim();
  } catch {
    return null;
  }
}

export async function GET() {
  const gitCommit = getGitCommit();
  try {
    await fetch(`${MOTIS_BASE}/api/v1/geocode?text=test`, {
      signal: AbortSignal.timeout(3000),
    });
    return NextResponse.json({ status: 'ok', motis: 'connected', gitCommit });
  } catch {
    return NextResponse.json({ status: 'degraded', motis: 'unreachable', gitCommit });
  }
}
