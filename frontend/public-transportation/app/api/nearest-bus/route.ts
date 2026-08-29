import { NextRequest, NextResponse } from 'next/server'
import { ensureMotis } from '@/lib/motis-manager'
import { metersBetween } from '../../../lib/geo'
import { fetchStopMonitoring } from '../../../lib/siri-fetch'
import { normaliseVehicles, NormalisedVehicle } from '../../../lib/siri-vehicles'

/**
 * Where the nearest bus that is actually reporting is — at any distance.
 *
 * The map's live layer walks the stops NEAREST the user, which answers "what is around
 * me" and cannot answer "is anything running at all". The difference is not academic: on
 * a Shabbat afternoon every stop in Gush Dan is silent while Haifa and Nazareth run
 * normally, so a search that spends its budget on consecutive Tel Aviv stops covers 330 m
 * of a country 400 km long, then reports "no live buses within 330 m" — true, useless,
 * and indistinguishable from a broken app.
 *
 * Two approaches were built and measured against a brute-force probe of every serviced
 * stop within 8 km of central Tel Aviv, and both failed, for the same reason:
 *
 *  - sampling stops on rings (one per compass sector) answered 146 km while Haifa ran 34
 *    buses at 85 km;
 *  - filtering stops by "has a scheduled departure soon" and probing the nearest of those
 *    answered 11.13 km while line 845 sat 260 m away.
 *
 * A bus appears in SIRI ONLY at the stops on its own route. Any candidate set chosen by
 * geometry — bearing, distance, density — is therefore chosen without reference to where
 * buses can possibly be, and misses them however deep it digs.
 *
 * So the candidates come from the timetable's own answer to "what is running right now":
 * MOTIS holds the country in memory on this host and `/api/v1/map/trips` returns every
 * trip active in a bounding box, with its stops, in under a tenth of a second even for a
 * box 300 km across. Probing the stops those trips actually touch found the nearest bus
 * within 80 m of the brute-force answer using a third of its upstream requests.
 *
 * This is the server's job, not the phone's: the answer is the same for everyone standing
 * in the same square kilometre, so it is computed once and cached.
 */

/**
 * Search boxes, in metres of half-width, tried in order. The first covers a city, the
 * last covers Israel from anywhere in it. Almost every call is answered by the first.
 */
const SEARCH_RADII_M = [25_000, 100_000, 400_000]

/** How far ahead a trip must be active to count as running now. */
const TRIP_WINDOW_MIN = 20

/** Upstream probes per attempt, at the nearest candidate stops not yet asked. */
const PROBE_BATCH = 16

/**
 * Total SIRI requests one walk may spend — the only genuinely costly resource here. Below
 * the 60 the phone's own live-bus poll spends every 15 seconds, and unlike that one this
 * is computed once for everyone in the square kilometre and cached.
 */
const MAX_SIRI_REQUESTS = 48

/**
 * How long an answer stands. A bus moves ~800 m in a minute, which does not change which
 * bus is nearest when the nearest is 85 km away; when it is 200 m away the map's own live
 * layer is already drawing it and nobody is reading this.
 */
const ANSWER_TTL_MS = 60_000

/** Grid the cache key onto ~1 km, so a walking user shares one answer with themselves. */
const CACHE_GRID_DECIMALS = 2

const MOTIS_BASE = `http://localhost:${process.env.MOTIS_PORT || '3504'}`

interface Answer {
  found: boolean
  vehicle: NormalisedVehicle | null
  distanceMeters: number | null
  /** Half-width of the box that produced the answer, or the last one tried. */
  searchedMeters: number
  /** Metres the caller said it had already covered; candidates inside this are skipped. */
  searchedFromMeters: number
  /** Stops touched by a trip running right now — 0 is itself the answer on a Shabbat. */
  candidateStops: number
  siriRequests: number
}

const answerCache = new Map<string, { at: number; answer: Answer }>()

interface Candidate { stopCode: string; d: number }

/**
 * Stops touched by a trip that is running right now inside the box — the free half of the
 * search, and the half that makes the paid half worth anything.
 */
async function candidatesWithin(
  lat: number, lon: number, radiusM: number, after: number,
): Promise<Candidate[]> {
  const dLat = radiusM / 111_320
  const dLon = radiusM / (111_320 * Math.cos((lat * Math.PI) / 180))
  const now = new Date()
  const end = new Date(now.getTime() + TRIP_WINDOW_MIN * 60_000)
  const url = `${MOTIS_BASE}/api/v1/map/trips`
    + `?zoom=15&min=${(lat - dLat).toFixed(5)},${(lon - dLon).toFixed(5)}`
    + `&max=${(lat + dLat).toFixed(5)},${(lon + dLon).toFixed(5)}`
    + `&startTime=${now.toISOString()}&endTime=${end.toISOString()}`

  const res = await fetch(url, { signal: AbortSignal.timeout(20_000) })
  if (!res.ok) return []
  const segments = await res.json()
  if (!Array.isArray(segments)) return []

  const nearest = new Map<string, number>()
  for (const seg of segments) {
    for (const place of [seg?.from, seg?.to]) {
      const code = place?.stopCode
      if (!code || typeof place.lat !== 'number' || typeof place.lon !== 'number') continue
      const d = metersBetween(place.lat, place.lon, lat, lon)
      // The caller has already probed everything closer than this, exhaustively and
      // better; re-asking would spend an upstream request to re-learn a known answer.
      if (d <= after) continue
      const held = nearest.get(code)
      if (held === undefined || d < held) nearest.set(code, d)
    }
  }

  return [...nearest.entries()]
    .map(([stopCode, d]) => ({ stopCode, d }))
    .sort((a, b) => a.d - b.d)
}

async function vehiclesAt(stopCode: string): Promise<NormalisedVehicle[]> {
  try {
    const data = await fetchStopMonitoring(stopCode, { timeoutMs: 8_000 })
    // Positions only. A visit with no VehicleLocation is a scheduled departure, not a bus
    // anyone can be pointed at, and naming a distance to one would name a distance to
    // something that never reported where it is.
    return normaliseVehicles(data, {}).filter(v => Number.isFinite(v.lat) && Number.isFinite(v.lon))
  } catch {
    // One unreachable stop must not decide the walk.
    return []
  }
}

export async function GET(request: NextRequest) {
  const lat = parseFloat(request.nextUrl.searchParams.get('lat') || '')
  const lon = parseFloat(request.nextUrl.searchParams.get('lon') || '')
  if (isNaN(lat) || isNaN(lon)) {
    return NextResponse.json({ error: 'lat and lon are required' }, { status: 400 })
  }
  const after = Math.max(0, parseFloat(request.nextUrl.searchParams.get('after') || '0') || 0)

  const key = `${lat.toFixed(CACHE_GRID_DECIMALS)},${lon.toFixed(CACHE_GRID_DECIMALS)}`
    + `@${Math.round(after / 250)}`
  const hit = answerCache.get(key)
  if (hit && Date.now() - hit.at < ANSWER_TTL_MS) {
    return NextResponse.json({ ...hit.answer, cached: true })
  }

  await ensureMotis()

  // Held on an object: assigned inside the loop below and read after it, which bare-let
  // control-flow narrowing types away to `never`.
  const best: { hit: { vehicle: NormalisedVehicle; d: number } | null } = { hit: null }
  let siriRequests = 0
  let candidateStops = 0
  let searchedMeters = 0
  const probed = new Set<string>()

  for (const radius of SEARCH_RADII_M) {
    if (best.hit || siriRequests >= MAX_SIRI_REQUESTS) break
    searchedMeters = radius

    let candidates: Candidate[]
    try {
      candidates = await candidatesWithin(lat, lon, radius, after)
    } catch {
      // MOTIS unreachable is not "no buses" — say nothing rather than something false.
      return NextResponse.json({ error: 'timetable service unavailable' }, { status: 503 })
    }
    candidateStops = candidates.length

    for (const c of candidates) {
      if (probed.has(c.stopCode)) continue
      if (siriRequests >= MAX_SIRI_REQUESTS) break

      const batch: Candidate[] = []
      for (const cand of candidates) {
        if (batch.length >= PROBE_BATCH || siriRequests + batch.length >= MAX_SIRI_REQUESTS) break
        if (probed.has(cand.stopCode)) continue
        probed.add(cand.stopCode)
        batch.push(cand)
      }
      if (batch.length === 0) break
      siriRequests += batch.length

      const found = (await Promise.all(batch.map(b => vehiclesAt(b.stopCode)))).flat()
      for (const v of found) {
        // A stop reports the buses heading TO it, which can be far behind it, so the
        // nearest vehicle is not the one from the nearest stop. Measure every one.
        const d = metersBetween(v.lat, v.lon, lat, lon)
        if (!best.hit || d < best.hit.d) best.hit = { vehicle: v, d }
      }
      if (best.hit) break
    }
  }

  const hit2 = best.hit
  const answer: Answer = hit2
    ? {
        found: true,
        vehicle: hit2.vehicle,
        distanceMeters: Math.round(hit2.d),
        searchedMeters,
        searchedFromMeters: after,
        candidateStops,
        siriRequests,
      }
    : {
        found: false,
        vehicle: null,
        distanceMeters: null,
        searchedMeters,
        searchedFromMeters: after,
        candidateStops,
        siriRequests,
      }

  answerCache.set(key, { at: Date.now(), answer })
  return NextResponse.json({ ...answer, cached: false })
}
