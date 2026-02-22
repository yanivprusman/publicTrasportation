import { NextRequest, NextResponse } from 'next/server';
import http from 'http';
import fs from 'fs';

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
      let data = '';
      res.on('data', (chunk: Buffer) => { data += chunk; });
      res.on('end', () => resolve(data));
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Request timed out')); });
  });
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const apiKey = process.env.MOT_API_KEY || 'YP719171';
  const station = searchParams.get('station') || '26472';
  const line = searchParams.get('line');
  const detailLevel = searchParams.get('detail') || 'calls';
  const previewInterval = searchParams.get('interval') || 'PT30M';

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
