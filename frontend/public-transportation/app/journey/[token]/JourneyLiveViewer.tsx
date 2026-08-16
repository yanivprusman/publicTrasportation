'use client';

import dynamic from 'next/dynamic';
import { useEffect, useRef, useState } from 'react';

const JourneyLiveMap = dynamic(() => import('./JourneyLiveMap'), { ssr: false });

export type ShareState = {
  token: string;
  updatedAt: number;
  endedAt: number | null;
  position: { lat: number; lon: number } | null;
  headline: string;
  detail: string | null;
  etaIso: string;
  destinationName: string;
  legs: { mode: string; polyline: string; routeColor: string | null }[];
  progressLegIndex: number;
};

const POLL_MS = 5_000;
/** Older than this and the page says "last seen" instead of pretending. */
const FRESH_MS = 45_000;

/**
 * The page behind a shared live-journey link: where the rider is, right now,
 * for anyone they sent it to. Read-only by construction — it knows the token
 * and nothing else.
 */
export default function JourneyLiveViewer({ token }: { token: string }) {
  const [share, setShare] = useState<ShareState | null>(null);
  const [gone, setGone] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    console.log('[journey-live] effect mounted, token', token);
    let cancelled = false;
    async function poll() {
      try {
        const res = await fetch(`/api/journey-live?token=${encodeURIComponent(token)}`);
        if (cancelled) return;
        if (res.status === 404) {
          // Only a share that never loaded is "gone" — one we watched end just
          // stays on its final state instead of turning into an error page.
          setGone((wasGone) => (share === null ? true : wasGone));
          return;
        }
        if (res.ok) {
          setShare(await res.json());
          setGone(false);
        }
      } catch {
        // Network blips leave the last state on screen; the age line says how old it is.
      }
    }
    poll();
    timer.current = setInterval(() => {
      setNow(Date.now());
      poll();
    }, POLL_MS);
    return () => {
      cancelled = true;
      if (timer.current) clearInterval(timer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  if (gone && !share) {
    return (
      <Shell>
        <p style={{ fontSize: 18, opacity: 0.9 }}>This journey link has expired.</p>
        <p style={{ opacity: 0.6 }}>Shares end a while after the trip does.</p>
      </Shell>
    );
  }

  if (!share) {
    return (
      <Shell>
        <p style={{ opacity: 0.8 }}>Loading journey…</p>
      </Shell>
    );
  }

  const ageMs = now - share.updatedAt;
  const stale = share.endedAt === null && ageMs > FRESH_MS;
  const eta = share.etaIso
    ? new Date(share.etaIso).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      })
    : null;

  return (
    <div style={{ position: 'fixed', inset: 0, display: 'flex', flexDirection: 'column', background: '#0f172a' }}>
      <div style={{ padding: '12px 16px', color: '#e2e8f0', background: '#1e293b', zIndex: 1000 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 5,
              background: share.endedAt !== null ? '#94a3b8' : stale ? '#f59e0b' : '#4ade80',
              display: 'inline-block',
            }}
          />
          <span style={{ fontSize: 13, letterSpacing: 2, opacity: 0.7 }}>
            {share.endedAt !== null ? 'JOURNEY ENDED' : 'LIVE JOURNEY'}
          </span>
          {eta && share.endedAt === null && (
            <span style={{ marginInlineStart: 'auto', fontSize: 13, opacity: 0.7 }}>Arrive {eta}</span>
          )}
        </div>
        <div dir="auto" style={{ fontSize: 20, fontWeight: 700, marginTop: 4 }}>{share.headline}</div>
        {share.detail && (
          <div dir="auto" style={{ fontSize: 14, opacity: 0.75, marginTop: 2 }}>{share.detail}</div>
        )}
        {stale && (
          <div style={{ fontSize: 12, color: '#f59e0b', marginTop: 4 }}>
            Last seen {Math.round(ageMs / 60_000)} min ago
          </div>
        )}
      </div>
      <div style={{ flex: 1, minHeight: 0 }}>
        <JourneyLiveMap share={share} />
      </div>
    </div>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#0f172a',
        color: '#e2e8f0',
        fontFamily: 'system-ui, sans-serif',
        padding: 24,
        textAlign: 'center',
      }}
    >
      {children}
    </div>
  );
}
