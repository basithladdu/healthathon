// Online transcription of a long goals-of-care recording with Google Gemini. The key stays here, on the server.
// When Gemini cannot finish, the client falls back to on-device Whisper, then typing.
//
// Vercel caps a request body at about 4.5 MB, so audio for Gemini never passes through this route:
//   1. start:      this route opens a Gemini Files API resumable upload with the key (sent as a header, never in the URL)
//                  and returns only the upload URL, which carries an upload id and no key. It is checked before it is returned.
//   2. upload:     the client sends the audio straight to that URL in chunks, with progress.
//   3. transcribe: this route calls generateContent on that file and returns checked lines; the file is then deleted.
//   4. delete:     the client asks for the file to be deleted when it stops early (files also expire after 48 hours).
// If the upload URL ever carried the key, the route keeps the URL to itself (encrypted inside the ticket) and relays
// chunks of under 4 MB instead ("relay" mode).
//
//
// Env, the same names as the Android function sahara-care-assist: SAHARA_AI_API_KEY (the Gemini key) and SAHARA_AI_MODEL,
// with GEMINI_API_KEY / GEMINI_MODEL as fallback names. SAHARA_AI_API_KEY is used only while SAHARA_AI_BASE_URL is unset or points at Gemini.
// Optional: GEMINI_GOC_MODEL (forces one model id), GOC_TICKET_SECRET. See docs/FREE_MODELS.md.
import { DEFAULT_MODELS, GEMINI_NATIVE, jsonPart } from './ai-providers.ts';
import { checkedGocLines, GEMINI_AUDIO_MIME, GOC_MAX_SECONDS, GOC_RELAY_MAX_BYTES, GOC_RESPONSE_SCHEMA, GOC_WINDOW_SEC, GocOutputError,
  baseMime, gocConsentLine, gocGeminiPrompt, type GocLangHint, type GocLine } from './goc-online-lines.ts';

type Environment = Record<string, string | undefined>;
type Json = Record<string, unknown>;

export const GEMINI_UPLOAD = 'https://generativelanguage.googleapis.com/upload/v1beta/files';
/** Tried in order. Model ids from the Gemini cookbook's audio quickstart (google-gemini/cookbook, checked 2026-11-04). */
export const GOC_GEMINI_MODELS = ['gemini-3.8-flash', DEFAULT_MODELS.gemini];
const MAX_UPLOAD_BYTES = 400 * 1024 * 1024;
const UPLOAD_CHUNK = 8 * 1024 * 1024;
const TICKET_HOURS = 6;
/** The route's own budget; route.ts sets maxDuration to 300 s. */
const BUDGET_MS = 280_000;
const LANGS = new Set(['en', 'hi', 'te', 'ur', 'kn', 'auto']);

export function gocTranscribeConfig(env: Environment = process.env) {
  const secret = (name: string) => env[name]?.trim() || '';
  const base = secret('SAHARA_AI_BASE_URL');
  const saharaIsGemini = !base || base.startsWith('https://generativelanguage.googleapis.com');
  const gemini = (saharaIsGemini ? secret('SAHARA_AI_API_KEY') : '') || secret('GEMINI_API_KEY');
  const configured = [saharaIsGemini ? secret('SAHARA_AI_MODEL') : '', secret('GEMINI_MODEL')].filter((model) => /^gemini-[a-z0-9.-]{1,60}$/u.test(model));
  return {
    gemini,
    geminiModels: secret('GEMINI_GOC_MODEL') ? [secret('GEMINI_GOC_MODEL')] : [...new Set([...configured, ...GOC_GEMINI_MODELS])],
    ticketSecret: secret('GOC_TICKET_SECRET') || (gemini ? `sahara-goc-ticket:${gemini}` : ''),
  };
}
type Config = ReturnType<typeof gocTranscribeConfig>;

export class GocFailure extends Error {
  readonly status: number; readonly code: string; readonly retryAfterSec?: number;
  constructor(status: number, code: string, message: string, retryAfterSec?: number) { super(message); this.status = status; this.code = code; this.retryAfterSec = retryAfterSec; }
}
const fail = (status: number, code: string, message: string, retryAfterSec?: number): never => { throw new GocFailure(status, code, message, retryAfterSec); };
const reply = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);
const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
const BUSY = 'The online transcription service is busy. Try again in a minute; the recording is unchanged.';

// ---------- tickets: a signed note of which upload this client may transcribe or delete ----------
const b64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/gu, '-').replace(/\//gu, '_').replace(/=+$/u, '');
const unb64url = (text: string) => Uint8Array.from(atob(text.replace(/-/gu, '+').replace(/_/gu, '/') + '==='.slice((text.length + 3) % 4)), (c) => c.charCodeAt(0));
async function keys(secret: string) {
  const raw = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(secret)));
  return {
    sign: await crypto.subtle.importKey('raw', raw, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']),
    seal: await crypto.subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']),
  };
}
type Ticket = { d: string; b: number; m: string; e: number; u?: string };
export async function signTicket(secret: string, ticket: Omit<Ticket, 'u'>, relayUrl?: string) {
  const k = await keys(secret);
  let u: string | undefined;
  if (relayUrl) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, k.seal, new TextEncoder().encode(relayUrl)));
    u = `${b64url(iv)}.${b64url(sealed)}`;
  }
  const body = b64url(new TextEncoder().encode(JSON.stringify({ ...ticket, ...(u ? { u } : {}) })));
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', k.sign, new TextEncoder().encode(body)));
  return `${body}.${b64url(mac)}`;
}
export async function readTicket(secret: string, value: unknown, now = Date.now()): Promise<Ticket & { relayUrl?: string }> {
  if (!secret || typeof value !== 'string' || value.length > 4096 || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u.test(value)) fail(400, 'ticket_invalid', 'Start the upload again.');
  const [body, mac] = (value as string).split('.');
  const k = await keys(secret);
  if (!await crypto.subtle.verify('HMAC', k.sign, unb64url(mac), new TextEncoder().encode(body))) fail(400, 'ticket_invalid', 'Start the upload again.');
  const ticket = JSON.parse(new TextDecoder().decode(unb64url(body))) as Ticket;
  if (ticket.e < now) fail(410, 'ticket_expired', 'This upload expired. Start again.');
  if (!ticket.u) return ticket;
  const [iv, sealed] = ticket.u.split('.');
  const relayUrl = new TextDecoder().decode(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64url(iv) }, k.seal, unb64url(sealed)));
  return { ...ticket, relayUrl };
}

/** The upload URL may go to the client only when it is Google's upload host and carries no key. */
export function uploadUrlIsSafe(url: string | null, key: string) {
  if (!url) return false;
  let parsed: URL;
  try { parsed = new URL(url); } catch { return false; }
  if (parsed.protocol !== 'https:' || parsed.hostname !== 'generativelanguage.googleapis.com' || !parsed.pathname.startsWith('/upload/')) return false;
  if (parsed.searchParams.has('key') || parsed.searchParams.has('api_key')) return false;
  return !(key && (url.includes(key) || decodeURIComponent(url).includes(key)));
}

// ---------- who is calling ----------
const ANDROID_SUPABASE = { url: 'https://kjnhtakqzboayqorrlue.supabase.co', key: 'sb_publishable_bheyh26N5MKpXNEJDQgq8w_oDRhW8Xe' };
const WEB_SUPABASE = { url: 'https://cghlkqeqiokjqdionrkc.supabase.co', key: 'sb_publishable_43mvHQiHJXVGwS71SX0GVA_GWxXBVOC' };

async function caller(request: Request, env: Environment, send: typeof fetch) {
  const authorization = request.headers.get('authorization') || '';
  const ip = (request.headers.get('x-forwarded-for') || '').split(',')[0].trim() || 'unknown';
  if (/^Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u.test(authorization) && authorization.length <= 16384) {
    for (const project of [{ url: env.SAHARA_SUPABASE_URL?.trim() || ANDROID_SUPABASE.url, key: env.SAHARA_SUPABASE_ANON_KEY?.trim() || ANDROID_SUPABASE.key }, WEB_SUPABASE]) {
      const user = await send(`${project.url.replace(/\/+$/u, '')}/auth/v1/user`, { headers: { apikey: project.key, Authorization: authorization }, signal: AbortSignal.timeout(8000), cache: 'no-store', redirect: 'error' }).catch(() => null);
      if (!user?.ok) { void user?.body?.cancel().catch(() => {}); continue; }
      const value: unknown = await user.json().catch(() => null);
      if (isObject(value) && typeof value.id === 'string' && value.is_anonymous !== true) return `user:${value.id}`;
    }
    fail(401, 'sign_in_required', 'Your sign-in expired. Sign in again; the recording is unchanged.');
  }
  // The Android app, like its other public Sahara routes (ed-card), says who it is. Rate limited by address.
  if (request.headers.get('x-sahara-client') === 'android') return `android:${ip}`;
  const origin = request.headers.get('origin'); const site = request.headers.get('sec-fetch-site');
  if (!origin || origin !== new URL(request.url).origin || (site && site !== 'same-origin')) fail(401, 'sign_in_required', 'Sign in to Sahara first.');
  return `ip:${ip}`;
}

// Per server instance: a speed bump against someone spending the free quota, not a guarantee.
const LIMITS = { start: 40, transcribe: 80, chunk: 400, delete: 200 } as const;
const recent = new Map<string, number[]>();
export function gocAllowed(who: string, action: keyof typeof LIMITS, now = Date.now()) {
  const key = `${action}:${who}`;
  const times = (recent.get(key) ?? []).filter((time) => now - time < 3_600_000);
  if (times.length >= LIMITS[action]) { recent.set(key, times); return false; }
  times.push(now); recent.set(key, times);
  if (recent.size > 20_000) recent.clear();
  return true;
}

// ---------- Gemini ----------
const retryAfter = (response: Response) => { const value = Number(response.headers.get('retry-after')); return Number.isFinite(value) && value > 0 ? value : undefined; };

async function geminiStart(config: Config, mime: string, bytes: number, origin: string | null, send: typeof fetch, pause: (ms: number) => Promise<void> = sleep) {
  const display = `sahara-goc-${crypto.randomUUID()}`;
  for (let attempt = 0; attempt < 2; attempt++) {
    let response: Response;
    try {
      response = await send(GEMINI_UPLOAD, { method: 'POST', redirect: 'error', cache: 'no-store', signal: AbortSignal.timeout(20_000),
        headers: { 'x-goog-api-key': config.gemini, 'X-Goog-Upload-Protocol': 'resumable', 'X-Goog-Upload-Command': 'start',
          'X-Goog-Upload-Header-Content-Length': String(bytes), 'X-Goog-Upload-Header-Content-Type': mime, 'Content-Type': 'application/json',
          // Google's upload server answers the browser's CORS checks for the origin that started the session.
          ...(origin ? { Origin: origin } : {}) },
        body: JSON.stringify({ file: { display_name: display } }) });
    } catch { if (attempt === 0) { await pause(1500); continue; } return fail(502, 'gemini_unavailable', 'Google Gemini could not be reached.'); }
    void response.body?.cancel().catch(() => {});
    if (response.status === 429 || response.status >= 500) {
      if (attempt === 0) { await pause(Math.min(8, retryAfter(response) ?? 2) * 1000); continue; }
      return response.status === 429 ? fail(429, 'provider_busy', BUSY, retryAfter(response)) : fail(502, 'gemini_unavailable', 'Google Gemini could not start the upload.');
    }
    if (!response.ok) return fail(502, 'gemini_rejected', 'Google Gemini refused the upload.');
    const url = response.headers.get('x-goog-upload-url');
    const granularity = Math.max(1, Number(response.headers.get('x-goog-upload-chunk-granularity')) || 262_144);
    return { display, url, granularity };
  }
  return fail(502, 'gemini_unavailable', 'Google Gemini could not start the upload.');
}

type GeminiFile = { name: string; uri: string; mimeType: string; state: string; sizeBytes: number; displayName: string };
function readFile(value: unknown): GeminiFile | null {
  const file = isObject(value) && isObject(value.file) ? value.file : value;
  if (!isObject(file) || typeof file.name !== 'string' || typeof file.uri !== 'string') return null;
  return { name: file.name, uri: file.uri, mimeType: String(file.mimeType ?? ''), state: String(file.state ?? ''), sizeBytes: Number(file.sizeBytes ?? 0), displayName: String(file.displayName ?? '') };
}

/** The ticket's file, found by name when the client could read it, otherwise by its display name. */
async function geminiFile(config: Config, ticket: Ticket, name: unknown, send: typeof fetch): Promise<GeminiFile> {
  const headers = { 'x-goog-api-key': config.gemini, Accept: 'application/json' };
  let file: GeminiFile | null = null;
  if (typeof name === 'string' && /^files\/[a-z0-9-]{1,64}$/u.test(name)) {
    const response = await send(`${GEMINI_NATIVE}/${name}`, { headers, cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(15_000) }).catch(() => null);
    if (response?.status === 429) fail(429, 'provider_busy', BUSY, retryAfter(response));
    file = response?.ok ? readFile(await response.json().catch(() => null)) : null;
  } else {
    let pageToken = '';
    for (let page = 0; page < 5 && !file; page++) {
      const response = await send(`${GEMINI_NATIVE}/files?pageSize=100${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ''}`, { headers, cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(15_000) }).catch(() => null);
      if (!response?.ok) break;
      const value = await response.json().catch(() => null) as { files?: unknown[]; nextPageToken?: string } | null;
      file = (value?.files ?? []).map(readFile).find((item) => item?.displayName === ticket.d) ?? null;
      pageToken = value?.nextPageToken ?? ''; if (!pageToken) break;
    }
  }
  if (!file || file.displayName !== ticket.d) return fail(404, 'upload_missing', 'The uploaded audio was not found. Start again.');
  if (file.sizeBytes && file.sizeBytes !== ticket.b) fail(409, 'upload_incomplete', 'The upload did not finish. Start again.');
  return file;
}

async function geminiDelete(config: Config, name: string, send: typeof fetch) {
  await send(`${GEMINI_NATIVE}/${name}`, { method: 'DELETE', headers: { 'x-goog-api-key': config.gemini }, redirect: 'error', signal: AbortSignal.timeout(10_000) })
    .then((response) => { void response.body?.cancel().catch(() => {}); }).catch(() => {});
}

export type GeminiWindowRequest = { file: { uri: string; mimeType: string }; lang: GocLangHint; offsetSec: number; overlapSec: number; durationSec: number };

/**
 * One generateContent call per window, with one retry on 429, 5xx, a timeout or an unusable answer, and the next model on "not found" or busy.
 * Throws GocFailure: 429 provider_busy, 502 gemini_unavailable / gemini_rejected / invalid_output, or 422 output_truncated (split the window).
 */
export async function geminiTranscribeWindow(config: Config, request: GeminiWindowRequest, send: typeof fetch, now: () => number = Date.now, pause: (ms: number) => Promise<void> = sleep): Promise<{ lines: GocLine[]; model: string }> {
  const deadline = now() + BUDGET_MS;
  let busy = 0; let tries = 0; let last: GocFailure | null = null;
  const body = JSON.stringify({
    systemInstruction: { parts: [{ text: gocGeminiPrompt(request.lang, request.overlapSec) }] },
    contents: [{ role: 'user', parts: [{ text: 'Transcribe this audio.' }, { file_data: { mime_type: request.file.mimeType, file_uri: request.file.uri } }] }],
    generationConfig: { responseMimeType: 'application/json', responseSchema: GOC_RESPONSE_SCHEMA, maxOutputTokens: 32_768 },
  });
  for (const model of config.geminiModels) {
    for (let attempt = 0; attempt < 2; attempt++) {
      const left = deadline - now();
      if (left < 5000) break;
      tries++;
      let response: Response;
      try {
        response = await send(`${GEMINI_NATIVE}/models/${encodeURIComponent(model)}:generateContent`, { method: 'POST', redirect: 'error', cache: 'no-store', signal: AbortSignal.timeout(Math.min(240_000, left)),
          headers: { 'x-goog-api-key': config.gemini, 'Content-Type': 'application/json', Accept: 'application/json' }, body });
      } catch {
        last = new GocFailure(502, 'gemini_unavailable', 'Google Gemini did not answer in time.');
        if (attempt === 0) { await pause(2000); continue; } break;
      }
      if (response.status === 404) { void response.body?.cancel().catch(() => {}); last = new GocFailure(502, 'gemini_unavailable', 'The Gemini model was not found.'); break; }
      if (response.status === 429 || response.status >= 500) {
        void response.body?.cancel().catch(() => {});
        if (response.status === 429) busy++;
        last = response.status === 429 ? new GocFailure(429, 'provider_busy', BUSY, retryAfter(response)) : new GocFailure(502, 'gemini_unavailable', 'Google Gemini had a problem. Try again later.');
        if (attempt === 0) { await pause(Math.min(8, retryAfter(response) ?? 2) * 1000); continue; } break;
      }
      const value = await response.json().catch(() => null) as Json | null;
      if (!response.ok) {
        const message = String((isObject(value?.error) ? (value.error as Json).message : '') ?? '');
        // A retired or unknown model: try the next one. Anything else (format, size) will not improve with a retry.
        if (/model|not found|not supported/iu.test(message) && !/mime|audio|file/iu.test(message)) { last = new GocFailure(502, 'gemini_unavailable', 'The Gemini model is not available.'); break; }
        throw new GocFailure(502, 'gemini_rejected', 'Google Gemini could not read this audio.');
      }
      try {
        return { lines: parseGeminiAnswer(value, request), model };
      } catch (error) {
        if (error instanceof GocOutputError && error.code === 'output_truncated') throw new GocFailure(422, 'output_truncated', 'This part was too long for one answer.');
        if (error instanceof GocOutputError && error.code === 'blocked') throw new GocFailure(502, 'gemini_rejected', 'Google Gemini declined to transcribe this audio.');
        last = new GocFailure(502, 'invalid_output', 'Google Gemini did not return a usable transcript.');
        if (attempt === 0) continue; break;
      }
    }
  }
  if (busy > 0 && busy === tries && last) throw last;
  throw last ?? new GocFailure(502, 'gemini_unavailable', 'Google Gemini could not finish.');
}

/** Reads a generateContent answer: candidate text → JSON → checked lines. Exported for tests. */
export function parseGeminiAnswer(value: unknown, window: { offsetSec: number; overlapSec: number; durationSec: number }): GocLine[] {
  const answer = value as { promptFeedback?: { blockReason?: string }; candidates?: { finishReason?: string; content?: { parts?: { text?: unknown; thought?: boolean }[] } }[] } | null;
  if (answer?.promptFeedback?.blockReason) throw new GocOutputError('blocked', 'blocked');
  const candidate = answer?.candidates?.[0];
  if (!candidate) throw new GocOutputError('invalid_output', 'No answer.');
  if (candidate.finishReason === 'MAX_TOKENS') throw new GocOutputError('output_truncated', 'Too long.');
  if (candidate.finishReason && ['SAFETY', 'RECITATION', 'PROHIBITED_CONTENT', 'BLOCKLIST', 'SPII'].includes(candidate.finishReason)) throw new GocOutputError('blocked', 'blocked');
  const text = (candidate.content?.parts ?? []).filter((part) => !part.thought).map((part) => typeof part.text === 'string' ? part.text : '').join('');
  let parsed: unknown;
  try { parsed = jsonPart(text); } catch { throw new GocOutputError('invalid_output', 'Not JSON.'); }
  return checkedGocLines(parsed, window);
}

// ---------- the handler ----------
function windowFrom(source: { offsetSec?: unknown; overlapSec?: unknown; durationSec?: unknown }) {
  const offsetSec = Number(source.offsetSec ?? 0); const overlapSec = Number(source.overlapSec ?? 0); const durationSec = Number(source.durationSec);
  if (!Number.isFinite(offsetSec) || offsetSec < 0 || offsetSec > GOC_MAX_SECONDS || !Number.isFinite(overlapSec) || overlapSec < 0 || overlapSec > 10
    || !Number.isFinite(durationSec) || durationSec <= 0 || durationSec > GOC_WINDOW_SEC * 2 + 10) fail(400, 'invalid_body', 'The audio part could not be read.');
  return { offsetSec, overlapSec, durationSec };
}
const langFrom = (value: unknown): GocLangHint => typeof value === 'string' && LANGS.has(value) ? value as GocLangHint : 'auto';

/** Injected transport and environment keep tests synthetic: they never call a live AI service. */
export function createGocTranscribeHandler(send: typeof fetch = fetch, env: Environment = process.env, pause: (ms: number) => Promise<void> = sleep) {
  return async (request: Request): Promise<Response> => {
    try {
      const config = gocTranscribeConfig(env);
      const gemini = Boolean(config.gemini);
      if (request.method === 'GET') return reply({ available: gemini, gemini, consentLine: gocConsentLine(gemini),
        geminiModel: gemini ? config.geminiModels[0] : '', windowSec: GOC_WINDOW_SEC, relayMaxBytes: GOC_RELAY_MAX_BYTES });
      if (request.method !== 'POST') fail(405, 'invalid_body', 'Use POST.');
      const url = new URL(request.url);
      const binary = url.searchParams.get('action');
      if (binary === 'chunk') {
        if (request.headers.get('x-sahara-consent') !== 'online-transcription') fail(400, 'consent_required', 'Agree to sending the audio first.');
        if (Number(request.headers.get('content-length') || '0') > GOC_RELAY_MAX_BYTES + 4096) fail(413, 'too_large', 'This part is too large.');
        if (!gemini) fail(503, 'not_enabled', 'Gemini transcription is not set up.');
        const who = await caller(request, env, send);
        if (!gocAllowed(who, 'chunk')) fail(429, 'attempt_unavailable', 'The hourly transcription limit was reached. Try again later.', 600);
        const audio = await request.blob();
        if (audio.size < 16 || audio.size > GOC_RELAY_MAX_BYTES) fail(413, 'too_large', 'This part is too large.');
        // Relay mode: a chunk of the Gemini upload, sent on with the URL only this server knows.
        const ticket = await readTicket(config.ticketSecret, request.headers.get('x-goc-ticket'));
        if (!ticket.relayUrl) fail(400, 'ticket_invalid', 'Start the upload again.');
        const offset = Number(request.headers.get('x-goc-offset'));
        const final = request.headers.get('x-goc-final') === '1';
        if (!Number.isInteger(offset) || offset < 0 || offset + audio.size > ticket.b || (final && offset + audio.size !== ticket.b)) fail(400, 'invalid_body', 'The upload part did not fit.');
        const forwarded = await send(ticket.relayUrl as string, { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(60_000), body: audio,
          headers: { 'X-Goog-Upload-Command': final ? 'upload, finalize' : 'upload', 'X-Goog-Upload-Offset': String(offset), 'Content-Length': String(audio.size) } }).catch(() => null);
        if (!forwarded) fail(502, 'gemini_unavailable', 'Google Gemini could not be reached.');
        const value = await (forwarded as Response).json().catch(() => null);
        if (!(forwarded as Response).ok) fail((forwarded as Response).status === 429 ? 429 : 502, (forwarded as Response).status === 429 ? 'provider_busy' : 'gemini_unavailable', 'The upload could not continue.');
        return reply({ status: (forwarded as Response).headers.get('x-goog-upload-status') || '', file: readFile(value)?.name ?? null });
      }

      if (Number(request.headers.get('content-length') || '0') > 16 * 1024) fail(413, 'too_large', 'The request is too large.');
      const payload: unknown = await request.json().catch(() => null);
      if (!isObject(payload) || typeof payload.action !== 'string') fail(400, 'invalid_body', 'The request could not be read.');
      const body = payload as Json;
      if (!['start', 'transcribe', 'delete'].includes(body.action as string)) fail(400, 'invalid_body', 'Unknown action.');
      const action = body.action as 'start' | 'transcribe' | 'delete';
      if (action !== 'delete' && body.consent !== true) fail(400, 'consent_required', 'Agree to sending the audio first.');
      if (!gemini) fail(503, 'not_enabled', 'Gemini transcription is not set up.');
      const who = await caller(request, env, send);
      if (!gocAllowed(who, action)) fail(429, 'attempt_unavailable', 'The hourly transcription limit was reached. Try again later.', 600);

      if (action === 'start') {
        const mime = baseMime(typeof body.mimeType === 'string' ? body.mimeType : '');
        const bytes = Number(body.bytes);
        if (!GEMINI_AUDIO_MIME.has(mime)) fail(415, 'invalid_audio', 'Convert the audio to WAV or AAC first.');
        if (!Number.isInteger(bytes) || bytes < 16 || bytes > MAX_UPLOAD_BYTES) fail(413, 'too_large', 'This part is too large.');
        const started = await geminiStart(config, mime, bytes, request.headers.get('origin'), send, pause);
        const ticket = { d: started.display, b: bytes, m: mime, e: Date.now() + TICKET_HOURS * 3_600_000 };
        if (uploadUrlIsSafe(started.url, config.gemini)) {
          return reply({ mode: 'direct', uploadUrl: started.url, chunkBytes: Math.max(started.granularity, Math.floor(UPLOAD_CHUNK / started.granularity) * started.granularity), ticket: await signTicket(config.ticketSecret, ticket) });
        }
        // The URL is not safe to hand out: keep it sealed in the ticket and relay chunks through this route.
        const chunkBytes = Math.floor(GOC_RELAY_MAX_BYTES / started.granularity) * started.granularity;
        if (!started.url || chunkBytes < 1) fail(502, 'gemini_unavailable', 'Google Gemini could not start the upload.');
        return reply({ mode: 'relay', chunkBytes, ticket: await signTicket(config.ticketSecret, ticket, started.url as string) });
      }

      const ticket = await readTicket(config.ticketSecret, body.ticket);
      if (action === 'delete') {
        const file = await geminiFile(config, ticket, body.file, send).catch(() => null);
        if (file) await geminiDelete(config, file.name, send);
        return reply({ deleted: Boolean(file) });
      }
      const window = windowFrom(body);
      const file = await geminiFile(config, ticket, body.file, send);
      if (file.state === 'PROCESSING') return reply({ pending: true, file: file.name, retryMs: 2000 }, 202);
      if (file.state === 'FAILED') { await geminiDelete(config, file.name, send); fail(502, 'gemini_rejected', 'Google Gemini could not process this audio.'); }
      let keep = false;
      try {
        const result = await geminiTranscribeWindow(config, { file: { uri: file.uri, mimeType: file.mimeType || ticket.m }, lang: langFrom(body.lang), ...window }, send, Date.now, pause);
        return reply({ ...result, engine: 'gemini', file: file.name });
      } catch (error) {
        // A busy answer may be retried on the same upload; anything else is final for this file.
        keep = error instanceof GocFailure && error.status === 429;
        throw error;
      } finally {
        if (!keep) await geminiDelete(config, file.name, send);
      }
    } catch (error) {
      if (error instanceof GocFailure) return reply({ code: error.code, error: error.message, ...(error.retryAfterSec ? { retryAfterSec: error.retryAfterSec } : {}) }, error.status);
      return reply({ code: 'unavailable', error: 'Online transcription could not finish. The recording is unchanged.' }, 503);
    }
  };
}
