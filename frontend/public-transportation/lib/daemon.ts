import net from "net";

// Thin client for the local daemon's UNIX socket. Every peer runs a daemon, and
// the daemon owns all shared-MySQL access, so this works uniformly whether the
// backend runs on the leader or on a worker (via mysql-router).
//
// Daemon command args must be sent as STRINGS — numeric JSON values are not
// accepted by the command parser.

const SOCKET_PATH =
  process.env.AUTOMATE_LINUX_SOCKET_PATH ||
  "/run/automatelinux/automatelinux-daemon.sock";

export function sendToDaemon(
  payload: Record<string, string>,
  timeoutMs = 10000
): Promise<string> {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection(SOCKET_PATH);
    let data = "";
    let settled = false;

    const done = (val: string) => {
      if (settled) return;
      settled = true;
      sock.destroy();
      resolve(val);
    };

    sock.on("connect", () => sock.write(JSON.stringify(payload) + "\n"));
    sock.on("data", (chunk) => {
      data += chunk.toString();
      // The daemon pretty-prints JSON, which can span several chunks. Resolve
      // as soon as the buffer parses as a complete object.
      try {
        JSON.parse(data.trim());
        done(data.trim());
      } catch {
        /* keep buffering */
      }
    });
    sock.on("end", () => done(data.trim()));
    sock.on("error", (err) => {
      if (!settled) {
        settled = true;
        reject(err);
      }
    });
    setTimeout(() => {
      if (!settled) {
        settled = true;
        sock.destroy();
        reject(new Error("daemon timeout"));
      }
    }, timeoutMs);
  });
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID_RE.test(value);
}
