/**
 * The AI navigator: a rider says where they want to go in plain words, and a
 * Claude session answers with their options, using PT's own planner as tools.
 *
 * Each conversation is ONE Claude session limited to the automateLinux `pt`
 * DOMAIN — launched through the same `claudeWithContext none,domain:pt` that
 * claudeDesk and the dashboard use, so the two cannot drift apart:
 * - no CLAUDE.md, memory, skills or other MCP servers; `claude/domains/pt/scope.md`
 *   appended to the system prompt;
 * - the domain guard as a UserPromptSubmit hook: every message is judged BEFORE the
 *   navigator sees it; an out-of-scope one is blocked and logged, the owner is
 *   emailed, and the conversation is closed for good.
 * On top of the domain: `--tools ''` (no shell, no files) and exactly one MCP server,
 * `lib/ai/pt-mcp.mjs` (find_place, plan_trip), which calls this server's own API.
 * Never add `--setting-sources ''`: it switches the guard hook off with the rest.
 *
 * Every trip plan_trip shows is filed in the conversation's options.json under a
 * letter; the navigator's answer names letters, and the app gets those exact
 * itineraries — the same JSON /api/route returns — to start the journey tracker
 * (live bus, "bus is early", board now, get off next).
 *
 * Offered exactly when PT_AI_DATA_DIR is set (a switch, not a fallback); then
 * PT_AI_TOKEN (the bearer token the app sends) and PT_AI_CLAUDE_BIN (absolute path
 * to `claude`) are required.
 */
import { execFile } from 'node:child_process';
import { randomUUID, timingSafeEqual } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const MODEL = 'sonnet';
const CALL_SECONDS = 180;
const CONTEXT = 'none,domain:pt';
const AUTOMATE_LINUX = '/opt/automateLinux';
const CLAUDE_SH = `${AUTOMATE_LINUX}/terminal/functions/claude.sh`;
const DOMAIN_GUARD = `${AUTOMATE_LINUX}/utilities/claudeDomainGuard/claudeDomainGuard.py`;
const MCP_SERVER = path.join(process.cwd(), 'lib', 'ai', 'pt-mcp.mjs');
const TZ = 'Asia/Jerusalem';
const MAX_TEXT = 1000;

// ---- config -------------------------------------------------------------------

export function aiEnabled(): boolean {
  return !!process.env.PT_AI_DATA_DIR?.trim();
}

function required(name: string): string {
  const v = process.env[name]?.trim();
  if (!v) throw new Error(`${name} is not set (the AI navigator is on because PT_AI_DATA_DIR is)`);
  return v;
}

/** The app sends `Authorization: Bearer <PT_AI_TOKEN>`: every message runs a Claude session on the owner's account. */
export function authorized(request: Request): boolean {
  const want = Buffer.from(`Bearer ${required('PT_AI_TOKEN')}`);
  const got = Buffer.from(request.headers.get('authorization') ?? '');
  return got.length === want.length && timingSafeEqual(got, want);
}

// ---- conversations ----------------------------------------------------------

export type AiMessage = {
  role: 'user' | 'ai';
  text: string;
  at: string;
  /** AI: the option letters offered, best first. */
  options?: string[];
  /** AI: the option the rider chose in words ("take B") — the app starts following it. */
  start?: string | null;
  error?: boolean;
};
type Conversation = { id: string; createdAt: string; ended: boolean; messages: AiMessage[] };
type StoredOption = { itinerary: Itinerary; at: number };
type Itinerary = { startTime: string; endTime: string; duration: number; transfers: number; legs: Leg[] };
type Leg = { mode: string; startTime: string; endTime: string; routeShortName?: string | null };

const ID = /^[0-9a-f-]{36}$/;
const busy = new Set<string>();

function convDir(id: string): string {
  if (!ID.test(id)) throw new Error('bad conversation id');
  return path.join(required('PT_AI_DATA_DIR'), id);
}

function load(id: string): Conversation | null {
  const f = path.join(convDir(id), 'conversation.json');
  return existsSync(f) ? (JSON.parse(readFileSync(f, 'utf8')) as Conversation) : null;
}

function save(c: Conversation) {
  const f = path.join(convDir(c.id), 'conversation.json');
  writeFileSync(`${f}.tmp`, JSON.stringify(c));
  renameSync(`${f}.tmp`, f);
}

function options(id: string): Record<string, StoredOption> {
  const f = path.join(convDir(id), 'options.json');
  return existsSync(f) ? JSON.parse(readFileSync(f, 'utf8')) : {};
}

/** Whether an answer is on its way. The in-memory set alone is not enough: a dev-server
 *  reload empties it while the call it tracked is still running. */
function working(c: Conversation): boolean {
  const last = c.messages.at(-1);
  return busy.has(c.id) || (last?.role === 'user' && Date.now() - new Date(last.at).getTime() < (CALL_SECONDS + 60) * 1000);
}

/** What the app shows: the conversation, and each offered option with its itinerary and a one-line summary. */
export function view(id: string) {
  const c = load(id);
  if (!c) return null;
  const opts = options(id);
  const he = c.messages.some((m) => m.role === 'user' && /[֐-׿]/.test(m.text));
  return {
    id: c.id,
    ended: c.ended,
    busy: working(c),
    messages: c.messages.map((m) => ({
      ...m,
      options: (m.options ?? []).filter((o) => opts[o]).map((o) => ({ id: o, line: summary(opts[o].itinerary, he), itinerary: opts[o].itinerary })),
    })),
  };
}

const hm = (iso: string) => new Date(iso).toLocaleTimeString('en-GB', { timeZone: TZ, hour: '2-digit', minute: '2-digit' });

function summary(it: Itinerary, he: boolean): string {
  const rides = it.legs.filter((l) => l.mode !== 'WALK' && l.routeShortName).map((l) => l.routeShortName);
  const walk = Math.round(it.legs.filter((l) => l.mode === 'WALK').reduce((s, l) => s + (new Date(l.endTime).getTime() - new Date(l.startTime).getTime()), 0) / 60000);
  const parts = [`${hm(it.startTime)} → ${hm(it.endTime)}`];
  if (rides.length) parts.push(`${he ? 'קו' : rides.length > 1 ? 'lines' : 'line'} ${rides.join(' + ')}`);
  if (walk > 0) parts.push(he ? `${walk} דק׳ הליכה` : `${walk} min walk`);
  return parts.join(' · ');
}

// ---- a message ----------------------------------------------------------------

export type Incoming = { conversationId: string | null; text: string; lat?: number; lon?: number };

/** Files the rider's message and answers it in the background. Returns the conversation id, or why not. */
export function accept(m: Incoming): { id: string } | { error: 'text' | 'not found' | 'ended' | 'busy' } {
  const text = m.text.trim();
  if (!text || text.length > MAX_TEXT) return { error: 'text' };
  let c: Conversation | null;
  if (m.conversationId) {
    c = load(m.conversationId);
    if (!c) return { error: 'not found' };
    if (c.ended) return { error: 'ended' };
    if (working(c)) return { error: 'busy' };
  } else {
    c = { id: randomUUID(), createdAt: new Date().toISOString(), ended: false, messages: [] };
    mkdirSync(convDir(c.id), { recursive: true });
  }
  const resume = c.messages.some((x) => x.role === 'ai');
  c.messages.push({ role: 'user', text, at: new Date().toISOString() });
  save(c);
  busy.add(c.id);
  const id = c.id;
  void answer(id, text, resume, m.lat, m.lon)
    .catch((e) => {
      console.error(`[ai] conversation ${id} failed:`, e);
      const cur = load(id)!;
      cur.messages.push({ role: 'ai', text: '', at: new Date().toISOString(), error: true });
      save(cur);
    })
    .finally(() => busy.delete(id));
  return { id };
}

type Answer = { reply: string; options: string[]; start: string };

async function answer(id: string, text: string, resume: boolean, lat?: number, lon?: number) {
  const where = lat != null && lon != null ? `${(await placeName(lat, lon)) ?? 'unnamed spot'} (${lat.toFixed(5)},${lon.toFixed(5)})` : 'unknown (location is off)';
  const now = new Date().toLocaleString('en-GB', { timeZone: TZ, weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  const prompt = `[Now: ${now}, Israel time. Rider's location: ${where}.]\n\n${text}`;

  const out = await run(id, resume, prompt);
  const parsed = JSON.parse(out) as { is_error?: boolean; result?: string; structured_output?: Answer };
  const c = load(id)!;
  if (!parsed.is_error && parsed.structured_output) {
    const a = parsed.structured_output;
    const known = options(id);
    c.messages.push({
      role: 'ai',
      text: a.reply.trim(),
      at: new Date().toISOString(),
      options: a.options.filter((o) => known[o]),
      start: a.start && known[a.start] ? a.start : null,
    });
    save(c);
    return;
  }
  // A blocked message comes back as a result with no answer. Ask the guard rather than
  // reading Claude's block message: its log is the record of what it ended.
  if ((await guardVerdict(id)) === 'ended') {
    c.ended = true;
    const he = /[֐-׿]/.test(text);
    c.messages.push({
      role: 'ai',
      text: he ? 'השיחה הזו הסתיימה כי חרגה מתחבורה ציבורית. אפשר להתחיל שיחה חדשה.' : 'This conversation has ended because it went beyond public transport. You can start a new one.',
      at: new Date().toISOString(),
    });
    save(c);
    return;
  }
  throw new Error(`AI call failed: ${String(parsed.result).slice(0, 300)}`);
}

/** The address at the rider's position, or null when there is none; the coordinates are what the planner uses. */
async function placeName(lat: number, lon: number): Promise<string | null> {
  const res = await fetch(`http://127.0.0.1:${process.env.PORT}/api/reverse-geocode?lat=${lat}&lon=${lon}`, { signal: AbortSignal.timeout(20_000) }); // cold: a dev compile plus a remote lookup
  if (!res.ok) throw new Error(`reverse-geocode answered HTTP ${res.status}`);
  const list = (await res.json()) as { name?: string }[];
  return list[0]?.name || null;
}

const SYSTEM_PROMPT = `You are PT, a public-transport navigator for Israel inside a phone app. A rider tells you in plain words where they want to go; you find their options with your tools and answer like a calm, precise friend.

Each message starts with the current Israel time and the rider's location (from their phone). Unless they say otherwise, they leave from there, now.

Tools: find_place turns a place name into coordinates; plan_trip plans trips and labels each option with a letter (A, B, ...). Only offer trips that plan_trip returned, by their letter — never invent lines or times. Call plan_trip again whenever the rider changes the place, the time or arrive-by.
- An option is the WHOLE trip, door to door. Its leave and arrive times are the ones on its "Option X: leave ..., arrive ..." line, and you name every line it rides. Never describe only part of an option.
- When the rider names a whole city ("Be'er Sheva", "Tel Aviv"), do not plan to the city itself (that is an arbitrary point in it): find and plan to its central bus station (or main train station if that is the obvious hub), and say so in one short line; when they name a street, a place or an address, plan to that.

"reply" — in the rider's language (Hebrew if they wrote Hebrew). PLAIN TEXT ONLY: no markdown, no asterisks, no bullets, no headings, no emoji. Short lines a person reads at a glance.
- Do NOT list the options' times and lines in "reply": beneath your reply the app shows every option you put in "options" as its own line, straight from the planner ("B · 12:21 → 13:20 · line 60 · 8 min walk"). Write only what that line cannot say: which one you recommend and why (fewest transfers, least walking, earliest arrival), a live delay or an early bus plan_trip reported, a last bus, a long walk, where the trip ends.
- No preamble, no restating the question, no sign-off. Usually one or two lines.

"options": the letters you offered in this reply, best first; [] when none.
"start": when the rider chooses an option in words ("take B", "the first one", "ok, the 60"), the letter of that option — the app then starts following the trip live and will tell them if the bus is early, when to board and when to get off; say so in one short line. Otherwise "".

If the destination is unclear, ask one short question. If you need their location and it is unknown, ask where they are.`;

const SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['reply', 'options', 'start'],
  properties: {
    reply: { type: 'string' },
    options: { type: 'array', items: { type: 'string' }, maxItems: 5 },
    start: { type: 'string' },
  },
};

function run(id: string, resume: boolean, prompt: string): Promise<string> {
  const dir = convDir(id);
  const mcp = path.join(dir, 'mcp.json');
  writeFileSync(
    mcp,
    JSON.stringify({
      mcpServers: { pt: { command: process.execPath, args: [MCP_SERVER], env: { PT_AI_BASE: `http://127.0.0.1:${process.env.PORT}`, PT_AI_DIR: dir } } },
    }),
  );
  const args = [
    '-p', '--output-format', 'json',
    '--model', MODEL,
    '--tools', '',
    '--mcp-config', mcp,
    '--allowedTools', 'mcp__pt',
    resume ? '--resume' : '--session-id', id,
    '--system-prompt', SYSTEM_PROMPT,
    '--json-schema', JSON.stringify(SCHEMA),
    prompt,
  ];
  // The service has no ~/.local/bin on PATH; claudeWithContext runs `claude` from PATH.
  const claudeBin = required('PT_AI_CLAUDE_BIN');
  const env = { ...process.env, PATH: `${path.dirname(claudeBin)}:${process.env.PATH ?? '/usr/bin:/bin'}` };
  return new Promise((resolve, reject) => {
    execFile(
      'bash',
      ['-c', 'source "$1" && cd "$2" && claudeWithContext "$3" "${@:4}"', 'bash', CLAUDE_SH, dir, CONTEXT, ...args],
      { env, timeout: CALL_SECONDS * 1000, maxBuffer: 16 * 1024 * 1024 },
      (err, stdout, stderr) => {
        if (err) return reject(new Error(`claude exited: ${err.message.slice(0, 200)} ${stderr.slice(-300)}`));
        resolve(stdout);
      },
    );
  });
}

function guardVerdict(sessionId: string): Promise<'ended' | 'live'> {
  return new Promise((resolve, reject) => {
    execFile(DOMAIN_GUARD, ['ended', sessionId], { timeout: 10_000 }, (err, stdout, stderr) => {
      const v = stdout.trim();
      if (err || (v !== 'ended' && v !== 'live')) return reject(new Error(`domain guard: ${(stderr || stdout || String(err)).trim().slice(0, 200)}`));
      resolve(v);
    });
  });
}
