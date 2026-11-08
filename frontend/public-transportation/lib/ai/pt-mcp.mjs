#!/usr/bin/env node
/**
 * The PT tools the AI navigator may use, as a dependency-free MCP server (stdio,
 * newline-delimited JSON-RPC). Started by `claude` for one AI call; it reaches the
 * app's own API over HTTP, so the AI plans with exactly what the planner shows.
 *
 * env:
 *   PT_AI_BASE  the PT server, e.g. http://127.0.0.1:3003
 *   PT_AI_DIR   this conversation's directory; every trip plan_trip shows is filed in
 *               options.json under a letter id, so the app can hand the very itinerary
 *               the AI described to the journey tracker.
 */
import { existsSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";
import readline from "node:readline";

const BASE = process.env.PT_AI_BASE;
const DIR = process.env.PT_AI_DIR;
if (!BASE || !DIR) {
  process.stderr.write("pt-mcp: PT_AI_BASE and PT_AI_DIR are required\n");
  process.exit(1);
}
const OPTIONS = path.join(DIR, "options.json");
const MAX_OPTIONS = 5;
const TZ = "Asia/Jerusalem";

const TOOLS = [
  {
    name: "find_place",
    description:
      "Find a place in Israel by name (city, neighbourhood, street, landmark, station; Hebrew or English). Returns up to 5 matches with coordinates. Use it to turn the place the rider names into coordinates for plan_trip.",
    inputSchema: {
      type: "object",
      properties: { query: { type: "string", description: "The place, as the rider said it" } },
      required: ["query"],
    },
  },
  {
    name: "plan_trip",
    description:
      "Plan public-transport trips (bus, train, light rail, walking) between two points in Israel, with live delays where known. Returns up to 5 options, each with a letter id (A, B, ...). Offer the rider those options by their letter. Times are Israel local time.",
    inputSchema: {
      type: "object",
      properties: {
        from_lat: { type: "number" },
        from_lon: { type: "number" },
        to_lat: { type: "number" },
        to_lon: { type: "number" },
        time: { type: "string", description: "Optional ISO-8601 date-time with offset, e.g. 2026-11-08T17:30:00+02:00. Omit for now." },
        arrive_by: { type: "boolean", description: "true: `time` is when the rider must arrive, not leave" },
      },
      required: ["from_lat", "from_lon", "to_lat", "to_lon"],
    },
  },
];

async function getJson(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(60_000) });
  if (!res.ok) throw new Error(`${new URL(url).pathname} answered HTTP ${res.status}`);
  return res.json();
}

async function findPlace({ query }) {
  const list = await getJson(`${BASE}/api/geocode?text=${encodeURIComponent(String(query))}`);
  if (!Array.isArray(list) || list.length === 0) return `No place found for "${query}".`;
  return list
    .slice(0, 5)
    .map((p) => {
      const area = (p.areas || []).filter((a) => a.adminLevel >= 8 && a.name !== p.name).map((a) => a.name)[0];
      const city = p.type === "PLACE" && /^place_[0-8]$/.test(p.category || "");
      return (
        `${p.name}${area ? ` (${area})` : ""} — ${p.type?.toLowerCase() || "place"} at ${p.lat.toFixed(5)},${p.lon.toFixed(5)}` +
        (city ? `  [a whole town/city: its point is arbitrary — for a trip, search its central bus station instead, e.g. "תחנה מרכזית ${p.name.split(",")[0]}"]` : "")
      );
    })
    .join("\n");
}

const hm = (iso) => new Date(iso).toLocaleTimeString("en-GB", { timeZone: TZ, hour: "2-digit", minute: "2-digit" });
const mins = (a, b) => Math.round((new Date(b) - new Date(a)) / 60000);
const dur = (sec) => {
  const m = Math.round(sec / 60);
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)}h${String(m % 60).padStart(2, "0")}`;
};
const MODE = { BUS: "Bus", RAIL: "Train", TRAM: "Light rail", SUBWAY: "Metro", FERRY: "Ferry", COACH: "Bus" };

function describeLeg(leg) {
  if (leg.mode === "WALK") {
    const m = mins(leg.startTime, leg.endTime);
    return `Walk ${m} min${leg.to?.name ? ` to ${leg.to.name}` : " to the destination"}.`;
  }
  if (leg.mode === "SCOOTER" || leg.mode === "BIKE") return `Ride ${mins(leg.startTime, leg.endTime)} min${leg.to?.name ? ` to ${leg.to.name}` : ""}.`;
  const kind = MODE[leg.mode] || leg.mode;
  const stops = (leg.intermediateStops?.length ?? 0) + 1;
  let live = "";
  if (leg.realTime && leg.scheduledStartTime && leg.scheduledStartTime !== leg.startTime) {
    const d = mins(leg.scheduledStartTime, leg.startTime);
    live = d > 0 ? ` (live: ${d} min late, scheduled ${hm(leg.scheduledStartTime)})` : ` (live: ${-d} min early, scheduled ${hm(leg.scheduledStartTime)})`;
  } else if (leg.realTime) live = " (live: on time)";
  return (
    `${kind} ${leg.routeShortName ?? ""}${leg.agencyName ? ` (${leg.agencyName})` : ""}${leg.headsign ? ` toward ${leg.headsign}` : ""}: ` +
    `board at ${leg.from?.name ?? "?"}${leg.fromStopCode ? ` [stop ${leg.fromStopCode}]` : ""} at ${hm(leg.startTime)}${live}, ` +
    `get off at ${leg.to?.name ?? "?"} at ${hm(leg.endTime)} (${stops} stop${stops === 1 ? "" : "s"}).`
  );
}

function readOptions() {
  return existsSync(OPTIONS) ? JSON.parse(readFileSync(OPTIONS, "utf8")) : {};
}

function nextId(n) {
  // A..Z, then AA..AZ, ...
  let s = "";
  n += 1;
  while (n > 0) {
    const r = (n - 1) % 26;
    s = String.fromCharCode(65 + r) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

async function planTrip(a) {
  const q = new URLSearchParams({ from: `${a.from_lat},${a.from_lon}`, to: `${a.to_lat},${a.to_lon}` });
  if (a.time) q.set("time", new Date(a.time).toISOString());
  if (a.arrive_by) q.set("arriveBy", "true");
  const data = await getJson(`${BASE}/api/route?${q}`);
  const its = (data.itineraries || []).slice(0, MAX_OPTIONS);
  if (its.length === 0) return "No public-transport trip found for that time.";

  const store = readOptions();
  let n = Object.keys(store).length;
  const lines = [];
  for (const it of its) {
    const id = nextId(n++);
    store[id] = { itinerary: it, from: { lat: a.from_lat, lon: a.from_lon }, to: { lat: a.to_lat, lon: a.to_lon }, at: Date.now() };
    const rides = it.legs.filter((l) => l.mode !== "WALK").length;
    lines.push(
      `Option ${id}: leave ${hm(it.startTime)}, arrive ${hm(it.endTime)} — ${dur(it.duration)}, ` +
        `${rides === 0 ? "walking only" : it.transfers === 0 ? "no transfers" : `${it.transfers} transfer${it.transfers === 1 ? "" : "s"}`}` +
        `${it.earlierDeparture ? " (an earlier bus of a line shown)" : ""}\n` +
        it.legs.map((l) => `  - ${describeLeg(l)}`).join("\n"),
    );
  }
  const tmp = `${OPTIONS}.tmp`;
  writeFileSync(tmp, JSON.stringify(store));
  renameSync(tmp, OPTIONS);

  const car = (data.alternatives || []).find((x) => x.mode === "CAR");
  if (car?.itinerary) lines.push(`(For comparison, driving takes about ${dur(car.itinerary.duration)}.)`);
  return lines.join("\n\n");
}

const HANDLERS = { find_place: findPlace, plan_trip: planTrip };

function send(msg) {
  process.stdout.write(JSON.stringify(msg) + "\n");
}

const rl = readline.createInterface({ input: process.stdin });
rl.on("line", async (line) => {
  if (!line.trim()) return;
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return;
  }
  const { id, method, params } = msg;
  if (id === undefined) return; // notifications need no answer
  try {
    if (method === "initialize") {
      send({
        jsonrpc: "2.0",
        id,
        result: { protocolVersion: params?.protocolVersion ?? "2025-06-18", capabilities: { tools: {} }, serverInfo: { name: "pt", version: "1.0.0" } },
      });
    } else if (method === "tools/list") {
      send({ jsonrpc: "2.0", id, result: { tools: TOOLS } });
    } else if (method === "tools/call") {
      const fn = HANDLERS[params?.name];
      if (!fn) throw new Error(`unknown tool ${params?.name}`);
      try {
        const text = await fn(params.arguments || {});
        send({ jsonrpc: "2.0", id, result: { content: [{ type: "text", text }] } });
      } catch (e) {
        send({ jsonrpc: "2.0", id, result: { content: [{ type: "text", text: `Error: ${e.message}` }], isError: true } });
      }
    } else if (method === "ping") {
      send({ jsonrpc: "2.0", id, result: {} });
    } else {
      send({ jsonrpc: "2.0", id, error: { code: -32601, message: `method not found: ${method}` } });
    }
  } catch (e) {
    send({ jsonrpc: "2.0", id, error: { code: -32603, message: e.message } });
  }
});
