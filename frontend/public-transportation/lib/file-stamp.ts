import fs from 'fs';

/**
 * Identity of a file's content for cache invalidation, as its mtime in ms
 * (-1 when the file is absent).
 *
 * Every GTFS file this server memoizes is REPLACED nightly by
 * motis/update-data.sh while the process keeps running — a cache guarded only
 * by "already loaded?" serves yesterday's feed forever. Yesterday's trip ids
 * grepped against today's stop_times.txt match zero rows, so the morning after
 * the first nightly swap, /api/route-stops 404'd on every line ("Couldn't load
 * the stop list") and /api/trip-shape was one feed-day away from drawing
 * nothing. Each cache stores the stamp it was built from and rebuilds when the
 * file on disk moves on; a statSync per request is microseconds.
 */
export function fileStamp(path: string): number {
  try {
    return fs.statSync(path).mtimeMs;
  } catch {
    return -1;
  }
}
