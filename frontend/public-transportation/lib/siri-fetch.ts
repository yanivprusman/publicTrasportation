import fs from 'fs'
import http from 'http'

const PROXY_PORT_FILE = '/tmp/pt_proxy_port'

function httpGet(url: string, headers: Record<string, string>, timeoutMs: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url)
    const req = http.get({
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname + parsed.search,
      headers,
      timeout: timeoutMs,
    }, (res) => {
      // Collect raw bytes and decode once at the end. Concatenating Buffers as strings
      // (data += chunk) decodes each chunk independently, so a multi-byte UTF-8 sequence
      // split across a chunk boundary — every Hebrew character is 2 bytes — corrupts into
      // replacement characters. SIRI stop, destination and line names are Hebrew.
      const chunks: Buffer[] = []
      res.on('data', (chunk: Buffer) => { chunks.push(chunk) })
      res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    })
    req.on('error', reject)
    req.on('timeout', () => { req.destroy(); reject(new Error('Request timed out')) })
  })
}

/**
 * One stop's SIRI StopMonitoring document, through the tunnel proxy to moran.mot.gov.il.
 *
 * Node's own `fetch` is deliberately not used: the SSH tunnel does not handle undici's
 * connection behaviour correctly, which is why this goes through `http` directly.
 *
 * Shared by `/api/transport` (one stop, for a board) and `/api/nearest-bus` (many stops,
 * walking outward) so the proxy contract — the port file, the Host header the tunnel
 * needs, the key — is written once. Two copies of it drift the moment one is fixed.
 */
export async function fetchStopMonitoring(
  station: string,
  opts: { detail?: string; line?: string | null; interval?: string; timeoutMs?: number } = {},
): Promise<Record<string, unknown>> {
  if (!fs.existsSync(PROXY_PORT_FILE)) {
    throw new Error("Proxy not running (port file not found). Run 'd startApp --app pt' to start the proxy.")
  }
  const port = fs.readFileSync(PROXY_PORT_FILE, 'utf8').trim()
  const apiKey = process.env.MOT_API_KEY || 'YP719171'
  const detail = opts.detail || 'calls'
  const interval = opts.interval || 'PT60M'

  let url = `http://localhost:${port}/Channels/HTTPChannel/SmQuery/2.8/json`
    + `?Key=${apiKey}&MonitoringRef=${station}`
    + `&StopVisitDetailLevel=${detail}&PreviewInterval=${interval}`
  if (opts.line) url += `&LineRef=${opts.line}`

  const body = await httpGet(url, { Host: 'moran.mot.gov.il:110' }, opts.timeoutMs ?? 10_000)
  return JSON.parse(body)
}
