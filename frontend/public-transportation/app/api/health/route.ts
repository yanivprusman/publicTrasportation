import { execSync } from 'child_process';
import { existsSync, statSync } from 'fs';
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
    const apk = candidates.find(p => existsSync(p));
    if (!apk) return null;
    const out = execSync(`${aapt2} dump badging "${apk}"`, { encoding: 'utf-8' });
    const match = out.match(/versionName='[^']*\(([^)]+)\)'/);
    return match?.[1] ?? null;
  } catch {
    return null;
  }
}

function resolveFeedbackLibRepo(): string | null {
  try {
    const repoRoot = execSync('git rev-parse --show-toplevel', { encoding: 'utf-8' }).trim();
    const feedbackLibDirs = [
      `${repoRoot}/android/feedback-lib`,
      `${repoRoot}/feedback-lib`,
    ];
    const feedbackLibDir = feedbackLibDirs.find(d => existsSync(d));
    if (!feedbackLibDir) return null;
    const source = execSync(`findmnt -n -o SOURCE --target "${feedbackLibDir}"`, { encoding: 'utf-8' }).trim();
    const match = source.match(/\[(.+)\]/);
    if (!match) return null;
    return match[1];
  } catch {
    return null;
  }
}

function getFeedbackLibInfo(realPath: string): { commit: string; version: number } | null {
  try {
    const commit = execSync(`git -C "${realPath}" rev-parse --short HEAD`, { encoding: 'utf-8' }).trim();
    const version = parseInt(execSync(`git -C "${realPath}" rev-list --count HEAD`, { encoding: 'utf-8' }).trim(), 10);
    return { commit, version };
  } catch {
    return null;
  }
}

function isFeedbackLibNewerThanApk(realPath: string): boolean {
  try {
    const repoRoot = execSync('git rev-parse --show-toplevel', { encoding: 'utf-8' }).trim();
    const candidates = [
      `${repoRoot}/android/app/build/outputs/apk/dev/debug/app-dev-debug.apk`,
      `${repoRoot}/app/build/outputs/apk/dev/debug/app-dev-debug.apk`,
    ];
    const apk = candidates.find(p => existsSync(p));
    if (!apk) return false;
    const apkMtime = statSync(apk).mtimeMs;
    const lastCommitTime = parseInt(
      execSync(`git -C "${realPath}" log -1 --format=%ct`, { encoding: 'utf-8' }).trim(), 10
    ) * 1000;
    return lastCommitTime > apkMtime;
  } catch {
    return false;
  }
}

export async function GET() {
  const gitCommit = getGitCommit();
  const apkCommit = getApkCommit();
  const gitVersion = getCommitCount('HEAD');
  const apkVersion = apkCommit ? getCommitCount(apkCommit) : null;

  const flRealPath = resolveFeedbackLibRepo();
  const flInfo = flRealPath ? getFeedbackLibInfo(flRealPath) : null;
  const feedbackLibCommit = flInfo?.commit ?? null;
  const feedbackLibVersion = flInfo?.version ?? null;
  const feedbackLibNeedsBuild = flRealPath ? isFeedbackLibNewerThanApk(flRealPath) : false;

  const versionInfo = {
    gitCommit, apkCommit, gitVersion, apkVersion,
    feedbackLibCommit, feedbackLibVersion, feedbackLibNeedsBuild,
  };

  try {
    await fetch(`${MOTIS_BASE}/api/v1/geocode?text=test`, {
      signal: AbortSignal.timeout(3000),
    });
    return NextResponse.json({ status: 'ok', motis: 'connected', ...versionInfo });
  } catch {
    return NextResponse.json({ status: 'degraded', motis: 'unreachable', ...versionInfo });
  }
}
