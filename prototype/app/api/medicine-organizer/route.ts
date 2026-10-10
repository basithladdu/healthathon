export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 150;

const MAX_BODY_BYTES = 12 * 1024 * 1024;
const MAX_RESPONSE_BYTES = 512 * 1024;
const BODY_TIMEOUT_MS = 15_000;
const PROVIDER_TIMEOUT_MS = 135_000;
const RATE_WINDOW_MS = 60_000;

let rateWindowStarted = Date.now();
let attempts = 0;
let inFlight = 0;

class SizeLimitError extends Error {}

function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return Response.json(body, { status, headers: {
    'Cache-Control': 'private, no-store, max-age=0',
    'X-Content-Type-Options': 'nosniff',
    Vary: 'Origin',
    ...headers,
  } });
}

function failure(status: number, code: string, error: string, headers?: Record<string, string>) {
  return json({ code, error }, status, headers);
}

function sameOrigin(request: Request) {
  const origin = request.headers.get('origin');
  const fetchSite = request.headers.get('sec-fetch-site');
  if (!origin || (fetchSite && fetchSite !== 'same-origin')) return false;
  try {
    const url = new URL(request.url);
    if (!['http:', 'https:'].includes(url.protocol)) return false;
    const host = request.headers.get('host');
    if (host && !/^(?:\[[a-fA-F0-9:]+\]|[a-zA-Z0-9.-]+)(?::\d{1,5})?$/.test(host)) return false;
    const expected = host ? new URL(`${url.protocol}//${host}`).origin : url.origin;
    return origin === expected;
  } catch { return false; }
}

function configuration() {
  const url = process.env.SAANTHVANA_SUPABASE_URL?.trim();
  const key = process.env.SAANTHVANA_SUPABASE_PUBLISHABLE_KEY?.trim()
    || process.env.SAANTHVANA_SUPABASE_ANON_KEY?.trim();
  const secret = process.env.SAANTHVANA_MEDICINE_ORGANIZER_SECRET?.trim();
  if (!url || !/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(url) || !key || !secret || secret.length < 32) return null;
  return { url, key, secret };
}

async function readBounded(body: Request['body'], maxBytes: number, signal: AbortSignal) {
  signal.throwIfAborted();
  if (!body) throw new Error('Missing body');
  const reader = body.getReader();
  const cancel = () => { void reader.cancel().catch(() => {}); };
  signal.addEventListener('abort', cancel, { once: true });
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      signal.throwIfAborted();
      const chunk = await reader.read();
      signal.throwIfAborted();
      if (chunk.done) break;
      size += chunk.value.byteLength;
      if (size > maxBytes) throw new SizeLimitError();
      chunks.push(chunk.value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    return bytes;
  } finally {
    signal.removeEventListener('abort', cancel);
    cancel();
    reader.releaseLock();
  }
}

export function GET() {
  return json({ ready: Boolean(configuration()) });
}

export async function POST(request: Request) {
  if (!sameOrigin(request)) return failure(403, 'origin_rejected', 'Open this action from the Sahara app.');
  if (request.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/json'
    || (request.headers.has('content-encoding') && request.headers.get('content-encoding') !== 'identity')) {
    return failure(415, 'unsupported_body', 'Send medicine images from the organizer.');
  }
  const contentLength = request.headers.get('content-length');
  if (contentLength && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_BODY_BYTES)) {
    return failure(413, 'images_too_large', 'The selected images are too large together. Use fewer or tighter photos.');
  }
  const config = configuration();
  if (!config) return failure(503, 'not_configured', 'Sahara AI is being connected. Add the Gemini key and try again.');

  const now = Date.now();
  if (now - rateWindowStarted >= RATE_WINDOW_MS) { rateWindowStarted = now; attempts = 0; }
  if (attempts >= 6 || inFlight >= 2) {
    const retryAfter = inFlight >= 2 ? 5 : Math.max(1, Math.ceil((RATE_WINDOW_MS - (now - rateWindowStarted)) / 1000));
    return failure(429, 'too_many_requests', 'Please wait before organizing another prescription.', { 'Retry-After': String(retryAfter) });
  }
  attempts += 1;
  inFlight += 1;

  try {
    const bodySignal = AbortSignal.any([request.signal, AbortSignal.timeout(BODY_TIMEOUT_MS)]);
    let body: Uint8Array;
    try { body = await readBounded(request.body, MAX_BODY_BYTES, bodySignal); }
    catch (error) {
      if (error instanceof SizeLimitError) return failure(413, 'images_too_large', 'The selected images are too large together. Use fewer or tighter photos.');
      return failure(bodySignal.aborted ? 408 : 400, 'invalid_body', bodySignal.aborted
        ? 'The image upload took too long. Try again.' : 'The medicine images could not be read. Try again.');
    }

    const providerSignal = AbortSignal.any([request.signal, AbortSignal.timeout(PROVIDER_TIMEOUT_MS)]);
    try {
      const response = await fetch(`${config.url}/functions/v1/saanthvana-medicine-organizer`, {
        method: 'POST',
        headers: {
          apikey: config.key,
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'X-Saanthvana-Organizer-Secret': config.secret,
        },
        body: body as BodyInit,
        cache: 'no-store',
        redirect: 'error',
        signal: providerSignal,
      });
      const responseType = response.headers.get('content-type') ?? '';
      if (!responseType.includes('application/json')) {
        void response.body?.cancel().catch(() => {});
        return failure(502, 'invalid_upstream', 'Sahara AI returned an unreadable response. Try again.');
      }
      const responseBytes = await readBounded(response.body, MAX_RESPONSE_BYTES, providerSignal);
      return new Response(responseBytes, {
        status: response.status,
        headers: {
          'Content-Type': 'application/json',
          'Cache-Control': 'private, no-store, max-age=0',
          'X-Content-Type-Options': 'nosniff',
        },
      });
    } catch {
      return failure(providerSignal.aborted ? 504 : 502, providerSignal.aborted ? 'organizer_timeout' : 'organizer_unavailable', providerSignal.aborted
        ? 'The medicine organizer took too long. Try fewer photos.' : 'The medicine organizer is temporarily unavailable. Try again.');
    }
  } finally {
    inFlight -= 1;
  }
}
