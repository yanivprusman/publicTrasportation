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

function getCommitCount(ref: string): number | null {
  try {
    return parseInt(execSync(`git rev-list --count ${ref}`, { encoding: 'utf-8' }).trim(), 10);
  } catch {
    return null;
  }
}

function getApkCommit(): string | null {
  try {
    const aapt2 = '/home/yaniv/Android/Sdk/build-tools/35.0.1/aapt2';
    const repoRoot = execSync('git rev-parse --show-toplevel', { encoding: 'utf-8' }).trim();
    const candidates = [
      `${repoRoot}/android/app/build/outputs/apk/dev/debug/app-dev-debug.apk`,
      `${repoRoot}/app/build/outputs/apk/dev/debug/app-dev-debug.apk`,
      `${repoRoot}/android/app/build/outputs/apk/debug/app-debug.apk`,
      `${repoRoot}/app/build/outputs/apk/debug/app-debug.apk`,
    ];
    const { existsSync } = require('fs');
    const apk = candidates.find(p => existsSync(p));
    if (!apk) return null;
    const out = execSync(`${aapt2} dump badging "${apk}"`, { encoding: 'utf-8' });
    const match = out.match(/versionName='[^']*\(([^)]+)\)'/);
    return match?.[1] ?? null;
  } catch {
    return null;
  }
}

export async function GET() {
  const gitCommit = getGitCommit();
  const apkCommit = getApkCommit();
  const gitVersion = getCommitCount('HEAD');
  const apkVersion = apkCommit ? getCommitCount(apkCommit) : null;
  try {
    await fetch(`${MOTIS_BASE}/api/v1/geocode?text=test`, {
      signal: AbortSignal.timeout(3000),
    });
    return NextResponse.json({ status: 'ok', motis: 'connected', gitCommit, apkCommit, gitVersion, apkVersion });
  } catch {
    return NextResponse.json({ status: 'degraded', motis: 'unreachable', gitCommit, apkCommit, gitVersion, apkVersion });
  }
}
