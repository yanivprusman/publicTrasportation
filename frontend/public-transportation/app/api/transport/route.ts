import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { normaliseVehicles } from '../../../lib/siri-vehicles';
import { fileStamp } from '../../../lib/file-stamp';
import { fetchStopMonitoring } from '../../../lib/siri-fetch';

// Cache stop_code → stop_name from GTFS stops.txt. Rebuilt when the nightly
// update replaces the file (see lib/file-stamp.ts).
let stopNameCache: Map<string, string> | null = null;
let stopNamesStamp = -2;

function loadStopNames(): Map<string, string> {
  const stopsFile = path.join(process.cwd(), '../../gtfs/israel-public-transportation/stops.txt');
  const stamp = fileStamp(stopsFile);
  if (stopNameCache && stamp === stopNamesStamp) return stopNameCache;
  stopNamesStamp = stamp;

  if (!fs.existsSync(stopsFile)) return new Map();

  const content = fs.readFileSync(stopsFile, 'utf8');
  const lines = content.split('\n');
  const header = lines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
  const codeIdx = header.indexOf('stop_code');
  const nameIdx = header.indexOf('stop_name');

  if (codeIdx === -1 || nameIdx === -1) return new Map();

  const map = new Map<string, string>();
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const cols = lines[i].split(',');
    const code = cols[codeIdx]?.trim();
    const name = cols[nameIdx]?.trim();
    if (code && name) map.set(code, name);
  }

  stopNameCache = map;
  return map;
}

function resolveStopNames(data: Record<string, unknown>): Record<string, string> {
  const visits = (data as any)?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit;
  if (!Array.isArray(visits)) return {};

  const codes = new Set<string>();
  for (const visit of visits) {
    const ref = visit?.MonitoredVehicleJourney?.DestinationRef;
    if (ref) codes.add(String(ref));
  }

  if (codes.size === 0) return {};

  const allNames = loadStopNames();
  const result: Record<string, string> = {};
  for (const code of codes) {
    const name = allNames.get(code);
    if (name) result[code] = name;
  }
  return result;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const station = searchParams.get('station') || '26472';
  const line = searchParams.get('line');
  const detailLevel = searchParams.get('detail') || 'calls';
  const previewInterval = searchParams.get('interval') || 'PT60M';

  try {
    const data = await fetchStopMonitoring(station, {
      detail: detailLevel,
      line,
      interval: previewInterval,
    }) as Record<string, any>

    data._stopNames = resolveStopNames(data);
    // The raw SIRI tree stays in the payload — the arrivals board still reads visits from
    // it — but every client reads vehicles from here, so none of them has to decide what
    // a SIRI field means. See lib/siri-vehicles.ts for why that was worth centralising.
    data._vehicles = normaliseVehicles(data, data._stopNames);

    return NextResponse.json(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error('Error fetching transport data:', message);
    return NextResponse.json(
      { error: message, message: 'Could not fetch data from transportation API' },
      { status: 500 }
    );
  }
}
