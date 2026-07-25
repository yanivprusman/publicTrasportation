import { NextRequest, NextResponse } from "next/server";
import { sendToDaemon, isUuid } from "../../../../lib/daemon";

// User registration: email + phone, linked to the anonymous install UUID.
//
// This is the app's contact channel. It exists so the promise made in the
// pricing notice — advance warning before anything is charged — can actually be
// kept for someone who has not opened the app in weeks.
//
// The daemon owns validation and normalisation (lowercased email, Israeli
// numbers canonicalised to +9725XXXXXXXX) so one person cannot become several
// accounts by typing their number a different way.

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const { installId, email, phone } = body;

  if (!isUuid(installId)) {
    return NextResponse.json({ error: "installId must be a UUID" }, { status: 400 });
  }
  if (typeof email !== "string" || !email.trim()) {
    return NextResponse.json({ error: "email is required" }, { status: 400 });
  }
  if (typeof phone !== "string" || !phone.trim()) {
    return NextResponse.json({ error: "phone is required" }, { status: 400 });
  }

  try {
    const raw = await sendToDaemon({
      command: "appRegister",
      app: "pt",
      installId,
      email: email.trim().slice(0, 320),
      phone: phone.trim().slice(0, 32),
    });

    let parsed: { ok?: boolean; userId?: number; founderSince?: string };
    try {
      parsed = JSON.parse(raw);
    } catch {
      // The daemon returns a plain-text reason for rejected input. Surface it
      // as a 400 so the user sees "invalid phone", not a generic failure.
      return NextResponse.json({ error: raw.trim() || "Registration failed" }, { status: 400 });
    }

    // userId is internal; the client only needs to know it worked and since when.
    return NextResponse.json({
      ok: parsed.ok === true,
      founderSince: parsed.founderSince ?? null,
    });
  } catch (err) {
    console.error("[app/register] daemon error:", err);
    return NextResponse.json({ error: "Registration unavailable" }, { status: 503 });
  }
}
