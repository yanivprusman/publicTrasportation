/**
 * Single gateway for every Nominatim call the server makes.
 *
 * Nominatim's public instance allows one request per second per application and
 * requires an identifying User-Agent; exceed either and it answers 429 with an
 * HTML page. Because the whole deployment shares one exit IP, bursts from
 * different users collide — two pins geocoded at once is already a burst.
 *
 * Every caller therefore goes through one queue that spaces upstream requests,
 * and retries once when the spacing was still not enough.
 */

const MIN_INTERVAL_MS = 1100;
const REQUEST_TIMEOUT_MS = 5000;
const USER_AGENT = 'com.automatelinux.pt/1.0';

let chain: Promise<unknown> = Promise.resolve();
let lastStartedAt = 0;

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

async function send(url: string): Promise<Response> {
  const waitFor = MIN_INTERVAL_MS - (Date.now() - lastStartedAt);
  if (waitFor > 0) await sleep(waitFor);
  lastStartedAt = Date.now();
  return fetch(url, {
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    headers: { 'User-Agent': USER_AGENT },
  });
}

/**
 * Queued Nominatim request. Resolves with the parsed JSON body; throws when the
 * upstream refuses twice or the body is not JSON, so callers report a failure
 * instead of passing an HTML error page down as data.
 */
export async function nominatim<T>(url: string): Promise<T> {
  const run = async (): Promise<T> => {
    // Backs off on 429: the public instance keeps refusing for a stretch after
    // a burst, so a single immediate retry is not always enough.
    let response = await send(url);
    for (const backoff of [MIN_INTERVAL_MS, 3000]) {
      if (response.status !== 429) break;
      await sleep(backoff);
      response = await send(url);
    }
    if (!response.ok) {
      throw new Error(`Nominatim responded ${response.status}`);
    }
    return response.json() as Promise<T>;
  };

  // Serialize against whatever is already in flight, and keep the chain alive
  // when this request fails so a rejection cannot poison later callers.
  const result = chain.then(run, run);
  chain = result.catch(() => undefined);
  return result;
}
