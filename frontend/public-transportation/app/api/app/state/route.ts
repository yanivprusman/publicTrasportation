import { NextRequest, NextResponse } from "next/server";
import { sendToDaemon, isUuid } from "../../../../lib/daemon";
import { rateLimit, clientIp } from "../../../../lib/rate-limit";

// Per-account app state: favourites, saved lines, synced preferences.
//
// An install's SharedPreferences die with the app, so this is where the data a
// user would be upset to lose actually lives. The client proves which account
// it is by its install id — the daemon resolves that to the account and refuses
// anything not linked to one, so an anonymous install can neither read nor
// write.
//
// Writes carry the client's own last-edit timestamp and the daemon applies
// last-write-wins, so a phone that has been offline cannot roll back edits made
// on another device. Both verbs return the winning payload.

// Generous: a client syncs on launch and on every favourite toggle, and a user
// rapidly starring a handful of stations must not be throttled mid-session.
const STATE_LIMIT = 120;
const STATE_WINDOW_SECONDS = 3600;

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  const installId = req.nextUrl.searchParams.get("installId");
  if (!isUuid(installId)) {
    return NextResponse.json({ error: "installId must be a UUID" }, { status: 400 });
  }

  try {
    const raw = await sendToDaemon({
      command: "appGetState",
      app: "pt",
      installId,
    });
    return NextResponse.json(JSON.parse(raw));
  } catch (err) {
    console.error("[app/state] daemon error:", err);
    return NextResponse.json({ error: "State unavailable" }, { status: 503 });
  }
}

export async function POST(req: NextRequest) {
  const limit = rateLimit(`state:${clientIp(req)}`, STATE_LIMIT, STATE_WINDOW_SECONDS);
  if (!limit.allowed) {
    return NextResponse.json(
      { error: "Too many state writes. Please try again later." },
      { status: 429, headers: { "Retry-After": String(limit.retryAfterSeconds) } }
    );
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const { installId, payload, updatedAt } = body;

  if (!isUuid(installId)) {
    return NextResponse.json({ error: "installId must be a UUID" }, { status: 400 });
  }
  if (typeof payload !== "object" || payload === null) {
    return NextResponse.json({ error: "payload must be an object" }, { status: 400 });
  }
  if (typeof updatedAt !== "number" || !Number.isFinite(updatedAt) || updatedAt <= 0) {
    return NextResponse.json(
      { error: "updatedAt must be a positive epoch-millis value" },
      { status: 400 }
    );
  }

  try {
    const raw = await sendToDaemon({
      command: "appPutState",
      app: "pt",
      installId,
      // The daemon takes the payload as a JSON string and validates it there,
      // so it is re-serialised rather than forwarded as a nested object.
      payload: JSON.stringify(payload),
      updatedAt,
    });

    let parsed: { ok?: boolean; payload?: unknown; updatedAt?: number };
    try {
      parsed = JSON.parse(raw);
    } catch {
      // The daemon answers rejected input in plain text — surface its reason
      // rather than a generic failure the client cannot act on.
      return NextResponse.json({ error: raw.trim() || "State write failed" }, { status: 400 });
    }
    return NextResponse.json(parsed);
  } catch (err) {
    console.error("[app/state] daemon error:", err);
    return NextResponse.json({ error: "State unavailable" }, { status: 503 });
  }
}
