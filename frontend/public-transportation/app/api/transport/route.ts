import { NextRequest, NextResponse } from 'next/server';
import http from 'http';
import fs from 'fs';
import path from 'path';

function httpGet(url: string, headers: Record<string, string>, timeoutMs: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const req = http.get({
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname + parsed.search,
      headers,
      timeout: timeoutMs,
    }, (res) => {
      // Collect raw bytes and decode once at the end. Concatenating Buffers as
      // strings (data += chunk) decodes each chunk independently, so a
      // multi-byte UTF-8 sequence split across a chunk boundary — every Hebrew
      // character is 2 bytes — corrupts into replacement characters. SIRI stop,
      // destination, and line names are Hebrew, so this must reassemble first.
      const chunks: Buffer[] = [];
      res.on('data', (chunk: Buffer) => { chunks.push(chunk); });
      res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Request timed out')); });
  });
}

// Cache stop_code → stop_name from GTFS stops.txt
let stopNameCache: Map<string, string> | null = null;

function loadStopNames(): Map<string, string> {
  if (stopNameCache) return stopNameCache;

  const stopsFile = path.join(process.cwd(), '../../gtfs/israel-public-transportation/stops.txt');
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
  const apiKey = process.env.MOT_API_KEY || 'YP719171';
  const station = searchParams.get('station') || '26472';
  const line = searchParams.get('line');
  const detailLevel = searchParams.get('detail') || 'calls';
  const previewInterval = searchParams.get('interval') || 'PT60M';

  try {
    const proxyPortFile = '/tmp/pt_proxy_port';

    if (!fs.existsSync(proxyPortFile)) {
      throw new Error("Proxy not running (port file not found). Run 'd startApp --app pt' to start the proxy.");
    }

    const port = fs.readFileSync(proxyPortFile, 'utf8').trim();
    let localUrl = `http://localhost:${port}/Channels/HTTPChannel/SmQuery/2.8/json?Key=${apiKey}&MonitoringRef=${station}&StopVisitDetailLevel=${detailLevel}&PreviewInterval=${previewInterval}`;
    if (line) localUrl += `&LineRef=${line}`;

    // Use http module instead of fetch — the SSH tunnel proxy doesn't
    // handle undici's (Node.js fetch) connection behavior correctly.
    const body = await httpGet(localUrl, { 'Host': 'moran.mot.gov.il:110' }, 10000);
    const data = JSON.parse(body);

    data._stopNames = resolveStopNames(data);

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
