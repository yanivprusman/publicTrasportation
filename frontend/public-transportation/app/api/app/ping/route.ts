import { NextRequest, NextResponse } from "next/server";
import { sendToDaemon, isUuid } from "../../../../lib/daemon";

// Anonymous install/usage ping from the native app.
//
// The body carries a client-generated install UUID and nothing else that
// identifies a person — no account, no device id, no ad id, no location. The
// daemon dedupes to one row per (install, event, day), so the client may ping
// on every launch without inflating anything.
//
// This route is public by necessity (real installs call it from the internet),
// so it validates hard and forwards a fixed argument set only.

export const dynamic = "force-dynamic";

const ALLOWED_EVENTS = new Set([
  "launch",
  "search",
  "share",
  "notice_seen",
  "notice_acknowledged",
]);

export async function POST(req: NextRequest) {
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const installId = body.installId;
  if (!isUuid(installId)) {
    return NextResponse.json(
      { error: "installId must be a UUID" },
      { status: 400 }
    );
  }

  const event = typeof body.event === "string" ? body.event : "launch";
  if (!ALLOWED_EVENTS.has(event)) {
    return NextResponse.json({ error: `Unknown event: ${event}` }, { status: 400 });
  }

  const appVersion = Number.isFinite(Number(body.appVersion))
    ? String(Math.max(0, Math.trunc(Number(body.appVersion))))
    : "0";

  // The raw Play Install Referrer string. The daemon parses `ref=<uuid>` out of
  // it and ignores anything that is not UUID-shaped, so it is safe to pass
  // through verbatim (truncated to the column width).
  const referrer =
    typeof body.referrer === "string" ? body.referrer.slice(0, 512) : "";

  const args: Record<string, string> = {
    command: "appPing",
    installId,
    app: "pt",
    platform: typeof body.platform === "string" ? body.platform.slice(0, 16) : "android",
    appVersion,
    event,
  };
  if (referrer) args.referrer = referrer;

  try {
    const raw = await sendToDaemon(args);
    // A ping must never surface daemon internals to a public caller.
    let newActiveDay = false;
    try {
      newActiveDay = JSON.parse(raw)?.newActiveDay === true;
    } catch {
      /* daemon returned a non-JSON error string */
    }
    return NextResponse.json({ ok: true, newActiveDay });
  } catch (err) {
    console.error("[app/ping] daemon error:", err);
    return NextResponse.json({ error: "Analytics unavailable" }, { status: 503 });
  }
}
