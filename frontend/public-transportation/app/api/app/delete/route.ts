import { NextRequest, NextResponse } from "next/server";
import { sendToDaemon, isUuid } from "../../../../lib/daemon";
import { rateLimit, clientIp } from "../../../../lib/rate-limit";

// Deleting is rarer than registering, and a person who means it will not need
// more than a few attempts. The limit exists so a leaked install id cannot be
// used to hammer the daemon, not to make a real deletion hard.
const DELETE_LIMIT = 10;
const DELETE_WINDOW_SECONDS = 3600;

// Account deletion: erases the account behind an install — the user row, the
// synced state, every install linked to it, and those installs' events.
//
// Google Play requires an app that lets people register to let them delete that
// account from inside the app, so this route exists to be called by the app's
// own settings screen. The install UUID is the credential: nothing here accepts
// an email, because an unauthenticated address would let anyone erase anyone.

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const limit = rateLimit(
    `delete:${clientIp(req)}`,
    DELETE_LIMIT,
    DELETE_WINDOW_SECONDS
  );
  if (!limit.allowed) {
    return NextResponse.json(
      { error: "Too many deletion attempts. Please try again later." },
      { status: 429, headers: { "Retry-After": String(limit.retryAfterSeconds) } }
    );
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const { installId } = body;
  if (!isUuid(installId)) {
    return NextResponse.json({ error: "installId must be a UUID" }, { status: 400 });
  }

  try {
    const raw = await sendToDaemon({
      command: "appDeleteAccount",
      app: "pt",
      installId,
    });

    let parsed: { ok?: boolean; wasRegistered?: boolean };
    try {
      parsed = JSON.parse(raw);
    } catch {
      return NextResponse.json({ error: raw.trim() || "Deletion failed" }, { status: 400 });
    }

    // wasRegistered lets the app word its confirmation honestly: an install
    // that never registered still had analytics rows, and they are gone too.
    return NextResponse.json({
      ok: parsed.ok === true,
      wasRegistered: parsed.wasRegistered === true,
    });
  } catch (err) {
    console.error("[app/delete] daemon error:", err);
    return NextResponse.json({ error: "Deletion unavailable" }, { status: 503 });
  }
}
