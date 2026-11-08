import { NextRequest, NextResponse } from 'next/server';
import { accept, aiEnabled, authorized, view } from '@/lib/ai/navigator';

/**
 * The AI navigator (lib/ai/navigator.ts). Bearer-token guarded: every message runs a
 * Claude session on the owner's account.
 *
 * POST {conversationId?, text, lat?, lon?} → {conversationId}; the answer comes later.
 * GET ?id=<conversationId> → the conversation, `busy` while the AI works, `ended` once
 * the pt domain guard closed it.
 */
export async function POST(request: NextRequest) {
  if (!aiEnabled()) return NextResponse.json({ error: 'unavailable' }, { status: 503 });
  if (!authorized(request)) return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  const b = (await request.json().catch(() => ({}))) as { conversationId?: string | null; text?: string; lat?: number; lon?: number };
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);
  const r = accept({ conversationId: b.conversationId ?? null, text: String(b.text ?? ''), lat: num(b.lat), lon: num(b.lon) });
  if ('error' in r) {
    const status = { text: 400, 'not found': 404, ended: 409, busy: 409 }[r.error];
    return NextResponse.json({ error: r.error }, { status });
  }
  return NextResponse.json({ conversationId: r.id });
}

export async function GET(request: NextRequest) {
  if (!aiEnabled()) return NextResponse.json({ error: 'unavailable' }, { status: 503 });
  if (!authorized(request)) return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  const id = request.nextUrl.searchParams.get('id') ?? '';
  if (!/^[0-9a-f-]{36}$/.test(id)) return NextResponse.json({ error: 'id' }, { status: 400 });
  const v = view(id);
  if (!v) return NextResponse.json({ error: 'not found' }, { status: 404 });
  return NextResponse.json(v);
}
