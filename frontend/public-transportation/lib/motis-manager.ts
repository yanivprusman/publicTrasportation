import { execSync } from 'child_process';
import { writeFileSync } from 'fs';
import net from 'net';

const MOTIS_PORT = parseInt(process.env.MOTIS_PORT || '3504', 10);
const IDLE_MARKER = '/tmp/motis-last-request';
const START_COOLDOWN = 10_000;

let lastStartAttempt = 0;

function touchIdleMarker() {
  try { writeFileSync(IDLE_MARKER, String(Date.now())); } catch {}
}

function checkPort(port: number, timeoutMs = 1000): Promise<boolean> {
  return new Promise(resolve => {
    const sock = net.createConnection({ port, host: '127.0.0.1', timeout: timeoutMs });
    sock.on('connect', () => { sock.destroy(); resolve(true); });
    sock.on('error', () => { sock.destroy(); resolve(false); });
    sock.on('timeout', () => { sock.destroy(); resolve(false); });
  });
}

export async function ensureMotis(): Promise<void> {
  touchIdleMarker();

  if (await checkPort(MOTIS_PORT)) return;

  const now = Date.now();
  if (now - lastStartAttempt < START_COOLDOWN) return;
  lastStartAttempt = now;

  try { execSync('systemctl start motis', { timeout: 5000 }); } catch {}

  for (let i = 0; i < 20; i++) {
    if (await checkPort(MOTIS_PORT, 500)) return;
    await new Promise(r => setTimeout(r, 500));
  }
}
