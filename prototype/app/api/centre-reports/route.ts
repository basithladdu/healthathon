export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

const COOKIE = 'saanthvana_report_session';
const MAX_BODY = 4 * 1024 * 1024 + 24 * 1024;
const json = (error: string, status: number) => Response.json({ error }, { status, headers: { 'Cache-Control': 'no-store' } });

function sameOrigin(request: Request) {
  const site = request.headers.get('sec-fetch-site');
  if (site && site !== 'same-origin') return false;
  try {
    const url = new URL(request.url);
    if (!['http:', 'https:'].includes(url.protocol)) return false;
    // Next can reconstruct request.url with its internal bind hostname. Host is
    // the browser's actual request authority; browsers cannot override it.
    // Forwarded host/protocol headers are deliberately not origin allowlists.
    const host = request.headers.get('host');
    if (host && !/^(?:\[[a-fA-F0-9:]+\]|[a-zA-Z0-9.-]+)(?::\d{1,5})?$/.test(host)) return false;
    const expected = host ? new URL(`${url.protocol}//${host}`).origin : url.origin;
    return request.headers.get('origin') === expected;
  } catch { return false; }
}

function configuration() {
  const url = process.env.SAANTHVANA_SUPABASE_URL?.trim();
  const key = process.env.SAANTHVANA_SUPABASE_ANON_KEY?.trim();
  const secret = process.env.SAANTHVANA_REPORTS_PROXY_SECRET?.trim();
  if (!url || !/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(url) || !key || !secret || secret.length < 32) return null;
  return { url, key, secret };
}

async function readBody(request: Request): Promise<Uint8Array> {
  const length = request.headers.get('content-length');
  if (length && (!/^\d+$/.test(length) || Number(length) > MAX_BODY)) throw new RangeError();
  if (!request.body) throw new Error('Missing body');
  const reader = request.body.getReader();
  const signal = AbortSignal.any([request.signal, AbortSignal.timeout(12_000)]);
  const cancel = () => { void reader.cancel().catch(() => {}); };
  signal.addEventListener('abort', cancel, { once: true });
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      signal.throwIfAborted();
      const next = await reader.read();
      signal.throwIfAborted();
      if (next.done) break;
      size += next.value.length;
      if (size > MAX_BODY) throw new RangeError();
      chunks.push(next.value);
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

async function proxy(request: Request) {
  if (request.method !== 'GET') {
    if (!sameOrigin(request)) {
      return json('Open this action from the care app.', 403);
    }
  }
  const config = configuration();
  if (!config) return json('Shared reports are not configured yet.', 503);
  const existing = request.headers.get('cookie')?.split(';').map((part) => part.trim()).find((part) => part.startsWith(`${COOKIE}=`))?.slice(COOKIE.length + 1);
  const session = existing && /^[a-f0-9]{64}$/.test(existing) ? existing : Array.from(crypto.getRandomValues(new Uint8Array(32)), (byte) => byte.toString(16).padStart(2, '0')).join('');
  const headers: Record<string, string> = { Authorization: `Bearer ${config.key}`, apikey: config.key, 'X-Reports-Proxy-Secret': config.secret, 'X-Report-Session': session };
  // Set this only to a header that the hosting reverse proxy overwrites.
  const trustedIpHeader = process.env.SAANTHVANA_TRUSTED_IP_HEADER;
  if (trustedIpHeader) {
    const ip = request.headers.get(trustedIpHeader)?.trim();
    if (ip && /^[a-fA-F0-9:.]{3,45}$/.test(ip)) headers['X-Report-Client-IP'] = ip;
  }
  let body: Uint8Array | undefined;
  if (request.method === 'POST') {
    const contentType = request.headers.get('content-type') || '';
    if (!/^multipart\/form-data;\s*boundary=/i.test(contentType) || (request.headers.has('content-encoding') && request.headers.get('content-encoding') !== 'identity')) return json('Send the report using the report form.', 415);
    headers['Content-Type'] = contentType;
    try { body = await readBody(request); }
    catch (error) { return json(error instanceof RangeError ? 'The bill must be no larger than 4 MB.' : 'The report could not be read. Try again.', error instanceof RangeError ? 413 : 400); }
  }
  const id = new URL(request.url).searchParams.get('id');
  if (request.method === 'DELETE' && (!id || !/^[a-f0-9-]{36}$/.test(id))) return json('Choose a report to withdraw.', 400);
  try {
    const response = await fetch(`${config.url}/functions/v1/saanthvana-centre-reports${request.method === 'DELETE' ? `?id=${encodeURIComponent(id!)}` : ''}`, {
      method: request.method, headers, body: body as BodyInit | undefined, cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(50_000),
    });
    if (!response.headers.get('content-type')?.includes('application/json')) { void response.body?.cancel(); return json('Shared reports are temporarily unavailable.', 502); }
    const result = await response.text();
    if (result.length > 2_000_000) return json('Shared reports are temporarily unavailable.', 502);
    const outgoing = new Response(result, { status: response.status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
    if (session !== existing) outgoing.headers.set('Set-Cookie', `${COOKIE}=${session}; HttpOnly; SameSite=Strict; Path=/api/centre-reports; Max-Age=31536000${process.env.NODE_ENV === 'production' ? '; Secure' : ''}`);
    return outgoing;
  } catch { return json('Shared reports are temporarily unavailable. Try again.', 502); }
}

export const GET = proxy;
export const POST = proxy;
export const DELETE = proxy;
