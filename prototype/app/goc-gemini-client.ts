// Browser side of online transcription for a goals-of-care recording. The API keys never reach this file: it only talks to
// /api/goc-transcribe and, for Gemini, to the one-off upload URL that route hands out.
//
// Order: Google Gemini (verbatim lines with speaker roles) → when it cannot finish, the caller falls back to on-device Whisper
// (goc-transcribe-model.ts) with the lines already done (GocOnlineError.partial) → typing.
// Consent line to show first: gocOnlineEngines().consentLine, "Audio is sent to Google Gemini to transcribe."
//
// A long recording is decoded once to 16 kHz mono and sent to Gemini in 10-minute windows, each cut at a quiet moment.
import { GEMINI_AUDIO_MIME, GOC_MAX_SECONDS, GOC_MIN_WINDOW_SEC, GOC_WINDOW_SEC, baseMime,
  gocWav, mergeGocLines, planGocWindows, quietCut, type GocLangHint, type GocLine } from './goc-online-lines.ts';

export type { GocLine } from './goc-online-lines.ts';
type Json = Record<string, unknown>;
export type GocOnlineEngine = 'gemini';
export type GocOnlineResult = { lines: GocLine[]; model: string; engine: GocOnlineEngine };
export type GocOnlineOptions = { lang?: GocLangHint; onProgress?: (p: number) => void; signal?: AbortSignal };

export type GocOnlineErrorCode = 'not_enabled' | 'provider_busy' | 'offline' | 'invalid_output' | 'rejected' | 'decode' | 'too_long' | 'aborted' | 'unavailable';
/** Thrown when Gemini could not finish. `partial` keeps the lines already transcribed, so the caller can show them before falling back. */
export class GocOnlineError extends Error {
  readonly code: GocOnlineErrorCode; readonly partial: GocLine[];
  constructor(code: GocOnlineErrorCode, message: string, partial: GocLine[] = []) { super(message); this.code = code; this.partial = partial; }
}

export const GOC_ROUTE = '/api/goc-transcribe';

/** HTTP calls the pipeline makes. Injected in tests and in the dev script. */
export type GocHttp = {
  info(): Promise<Json>;
  json(body: Json, signal?: AbortSignal): Promise<{ status: number; body: Json }>;
  binary(query: Record<string, string>, data: Blob, headers: Record<string, string>, signal?: AbortSignal): Promise<{ status: number; body: Json }>;
  upload(url: string, data: Blob, headers: Record<string, string>, signal?: AbortSignal): Promise<{ status: number; uploadStatus: string; body: Json | null }>;
};

export function fetchHttp(base = '', extraHeaders: Record<string, string> = {}, send: typeof fetch = fetch): GocHttp {
  const read = async (response: Response) => ({ status: response.status, body: (await response.json().catch(() => ({}))) as Json });
  return {
    info: async () => (await send(`${base}${GOC_ROUTE}`, { cache: 'no-store', headers: extraHeaders }).then((r) => r.ok ? r.json() : {}).catch(() => ({}))) as Json,
    json: async (body, signal) => read(await send(`${base}${GOC_ROUTE}`, { method: 'POST', signal, cache: 'no-store', headers: { 'Content-Type': 'application/json', ...extraHeaders }, body: JSON.stringify(body) })),
    binary: async (query, data, headers, signal) => read(await send(`${base}${GOC_ROUTE}?${new URLSearchParams(query)}`, { method: 'POST', signal, cache: 'no-store', body: data,
      headers: { 'Content-Type': data.type || 'application/octet-stream', 'X-Sahara-Consent': 'online-transcription', ...extraHeaders, ...headers } })),
    upload: async (url, data, headers, signal) => {
      const response = await send(url, { method: 'POST', signal, body: data, headers });
      return { status: response.status, uploadStatus: response.headers.get('x-goog-upload-status') || '', body: (await response.json().catch(() => null)) as Json | null };
    },
  };
}

/** Audio the pipeline can cut into parts. */
export type GocAudioSource = {
  durationSec: number;
  /** A quiet moment at or just before `at`, to cut between words. */
  cutNear(at: number): number;
  /** Encoded audio for [start, end). `overlapSec` is how much of the previous part it repeats at its start. */
  slice(start: number, end: number): Promise<{ blob: Blob; overlapSec: number }>;
  /** The recording as it is, when it can be sent unchanged. */
  original?: Blob;
};

/** A source over 16 kHz mono samples: parts are 16-bit WAV, cut at the quietest 20 ms before each boundary. */
export function samplesSource(samples: Float32Array, original?: Blob, sampleRate = 16000): GocAudioSource {
  return {
    durationSec: samples.length / sampleRate,
    cutNear: (at) => quietCut(samples, at, sampleRate),
    slice: async (start, end) => ({ blob: new Blob([gocWav(samples.subarray(Math.round(start * sampleRate), Math.round(end * sampleRate)), sampleRate) as BlobPart], { type: 'audio/wav' }), overlapSec: 0 }),
    original,
  };
}

/** Decodes a recording to 16 kHz mono in the browser. Two hours hold about 460 MB of memory while this runs. */
export async function decodeForOnline(audio: Blob): Promise<Float32Array> {
  if (typeof OfflineAudioContext === 'undefined') throw new GocOnlineError('decode', 'This browser cannot read the recording.');
  let decoded: AudioBuffer;
  try { decoded = await new OfflineAudioContext(1, 1, 16000).decodeAudioData(await audio.arrayBuffer()); }
  catch { throw new GocOnlineError('decode', 'The recording could not be read. Try on-device transcription or type the words.'); }
  if (decoded.duration > GOC_MAX_SECONDS) throw new GocOnlineError('too_long', 'The recording is longer than 4 hours. Split it into parts.');
  if (decoded.numberOfChannels === 1) return decoded.getChannelData(0);
  const mono = new Float32Array(decoded.length);
  for (let c = 0; c < decoded.numberOfChannels; c++) { const channel = decoded.getChannelData(c); for (let i = 0; i < mono.length; i++) mono[i] += channel[i] / decoded.numberOfChannels; }
  return mono;
}

const aborted = (signal?: AbortSignal) => { if (signal?.aborted) throw new GocOnlineError('aborted', 'Stopped.'); };
const wait = (ms: number, signal?: AbortSignal) => new Promise<void>((resolve, reject) => {
  if (signal?.aborted) { reject(new GocOnlineError('aborted', 'Stopped.')); return; }
  const timer = setTimeout(() => { signal?.removeEventListener('abort', stop); resolve(); }, ms);
  const stop = () => { clearTimeout(timer); reject(new GocOnlineError('aborted', 'Stopped.')); };
  signal?.addEventListener('abort', stop, { once: true });
});

/** A failure from Gemini or the route: busy and "split this part" are handled; anything else ends the online run. */
class EngineError extends Error {
  readonly code: string; readonly retryAfterSec?: number;
  constructor(code: string, message: string, retryAfterSec?: number) { super(message); this.code = code; this.retryAfterSec = retryAfterSec; }
}
const engineError = (response: { status: number; body: Json }) => new EngineError(String(response.body.code || (response.status === 429 ? 'provider_busy' : 'unavailable')),
  String(response.body.error || 'The online service could not finish.'), typeof response.body.retryAfterSec === 'number' ? response.body.retryAfterSec : undefined);
const asLines = (value: unknown): GocLine[] => Array.isArray(value) ? value as GocLine[] : [];

export type GocPipelineDeps = { http: GocHttp; pause?: (ms: number, signal?: AbortSignal) => Promise<void> };

/** One Gemini window: start an upload, send the audio straight to Google, transcribe, and make sure the file is deleted. */
async function geminiWindow(source: GocAudioSource, window: { start: number; end: number }, whole: boolean, lang: GocLangHint, deps: Required<GocPipelineDeps>,
  progress: (fraction: number) => void, signal?: AbortSignal): Promise<{ lines: GocLine[]; model: string }> {
  const useOriginal = whole && source.original && GEMINI_AUDIO_MIME.has(baseMime(source.original.type));
  const piece = useOriginal ? { blob: new Blob([source.original as Blob], { type: baseMime((source.original as Blob).type) }), overlapSec: 0 } : await source.slice(window.start, window.end);
  const start = await deps.http.json({ action: 'start', consent: true, mimeType: piece.blob.type, bytes: piece.blob.size }, signal);
  if (start.status !== 200) throw engineError(start);
  const ticket = String(start.body.ticket); const chunk = Math.max(1, Number(start.body.chunkBytes) || 8 * 1024 * 1024);
  let file: string | null = null; let done = false;
  try {
    for (let offset = 0; offset < piece.blob.size; offset += chunk) {
      aborted(signal);
      const part = piece.blob.slice(offset, Math.min(piece.blob.size, offset + chunk));
      const final = offset + part.size >= piece.blob.size;
      let ok = false;
      for (let attempt = 0; attempt < 3 && !ok; attempt++) {
        try {
          if (start.body.mode === 'relay') {
            const sent = await deps.http.binary({ action: 'chunk' }, part, { 'X-Goc-Ticket': ticket, 'X-Goc-Offset': String(offset), 'X-Goc-Final': final ? '1' : '0' }, signal);
            ok = sent.status === 200; if (ok && typeof sent.body.file === 'string') file = sent.body.file;
          } else {
            const sent = await deps.http.upload(String(start.body.uploadUrl), part, { 'X-Goog-Upload-Command': final ? 'upload, finalize' : 'upload', 'X-Goog-Upload-Offset': String(offset) }, signal);
            ok = sent.status === 200 && (final ? sent.uploadStatus === 'final' || sent.uploadStatus === '' : true);
            const named = sent.body && typeof sent.body.file === 'object' && sent.body.file ? (sent.body.file as Json).name : null;
            if (ok && typeof named === 'string') file = named;
          }
        } catch (error) { if (signal?.aborted) throw new GocOnlineError('aborted', 'Stopped.'); void error; }
        if (!ok) await deps.pause(1000 * 2 ** attempt, signal);
      }
      if (!ok) throw new EngineError('upload_failed', 'The audio could not be uploaded to Google Gemini.');
      progress(0.4 * (offset + part.size) / piece.blob.size);
    }
    // The file may take a moment to be ready; a busy answer gets one wait on the same upload.
    let busyWaits = 0;
    for (let polls = 0; polls < 90; polls++) {
      aborted(signal);
      const answer = await deps.http.json({ action: 'transcribe', consent: true, ticket, file, lang, offsetSec: window.start, overlapSec: piece.overlapSec, durationSec: window.end - window.start }, signal);
      if (answer.status === 202) { if (typeof answer.body.file === 'string') file = answer.body.file; await deps.pause(Number(answer.body.retryMs) || 2000, signal); continue; }
      if (answer.status === 429 && busyWaits++ < 1) { await deps.pause(Math.min(30, Number(answer.body.retryAfterSec) || 10) * 1000, signal); continue; }
      if (answer.status !== 200) { if (answer.status !== 429) done = true; throw engineError(answer); }
      done = true; progress(1);
      return { lines: asLines(answer.body.lines), model: String(answer.body.model || 'gemini') };
    }
    throw new EngineError('unavailable', 'Google Gemini took too long to prepare the audio.');
  } finally {
    // The route deletes the file after a final answer; otherwise ask it to.
    if (!done) await deps.http.json({ action: 'delete', ticket, file }).catch(() => null);
  }
}

const errorCode = (code: string): GocOnlineErrorCode => code === 'provider_busy' || code === 'attempt_unavailable' ? 'provider_busy'
  : code === 'invalid_output' ? 'invalid_output' : /rejected|invalid_audio|too_large/u.test(code) ? 'rejected' : code === 'not_enabled' ? 'not_enabled' : 'unavailable';

/**
 * The whole online pipeline over any audio source, window by window. A window whose answer was too long is split in two (down to 2 minutes).
 * When Gemini cannot go on, GocOnlineError carries the finished lines so the caller can continue on the device.
 */
export async function runGocOnline(source: GocAudioSource, opts: GocOnlineOptions, deps: GocPipelineDeps): Promise<GocOnlineResult> {
  const full: Required<GocPipelineDeps> = { http: deps.http, pause: deps.pause ?? wait };
  const lang = opts.lang ?? 'auto'; const signal = opts.signal;
  if (source.durationSec > GOC_MAX_SECONDS) throw new GocOnlineError('too_long', 'The recording is longer than 4 hours. Split it into parts.');
  const queue = planGocWindows(source.durationSec, GOC_WINDOW_SEC, source.cutNear);
  const parts: GocLine[][] = []; const models = new Set<string>();
  let doneSec = 0;
  const report = (window: { start: number; end: number }) => (fraction: number) => opts.onProgress?.(Math.min(1, (doneSec + fraction * (window.end - window.start)) / Math.max(1, source.durationSec)));
  opts.onProgress?.(0);
  for (let i = 0; i < queue.length; i++) {
    const window = queue[i];
    aborted(signal);
    try {
      const result = await geminiWindow(source, window, queue.length === 1, lang, full, report(window), signal);
      parts.push(result.lines); models.add(result.model); doneSec += window.end - window.start;
    } catch (error) {
      if (error instanceof GocOnlineError) throw Object.assign(error, { partial: mergeGocLines(parts) });
      const failure = error instanceof EngineError ? error : new EngineError('offline', 'Google Gemini could not be reached.');
      if (failure.code === 'output_truncated' && window.end - window.start > GOC_MIN_WINDOW_SEC * 2) {
        const length = window.end - window.start;
        const cut = Math.max(window.start + GOC_MIN_WINDOW_SEC, Math.min(window.end - GOC_MIN_WINDOW_SEC, source.cutNear(window.start + length / 2)));
        queue.splice(i, 1, { start: window.start, end: cut }, { start: cut, end: window.end }); i--; continue;
      }
      throw new GocOnlineError(failure.code === 'offline' ? 'offline' : errorCode(failure.code), failure.message, mergeGocLines(parts));
    }
  }
  opts.onProgress?.(1);
  return { lines: mergeGocLines(parts), model: [...models].join(' + ') || 'unknown', engine: 'gemini' };
}

/** Whether the server has a Gemini key, and the consent line to show before anything is sent. */
export async function gocOnlineEngines(http: GocHttp = fetchHttp()): Promise<{ gemini: boolean; consentLine: string }> {
  const info = await http.info().catch(() => ({} as Json));
  const gemini = info.gemini === true;
  return { gemini, consentLine: gemini ? 'Audio is sent to Google Gemini to transcribe.' : '' };
}

/** True when the server has a Gemini key. False when offline or not set up. */
export async function geminiTranscriptionAvailable(): Promise<boolean> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return false;
  const engines = await gocOnlineEngines();
  return engines.gemini;
}

/**
 * Transcribes a recording online with Gemini (verbatim lines with speaker roles).
 * Throws GocOnlineError (with `partial`) when Gemini cannot finish; the caller then offers on-device Whisper, then typing. `engine` and `model` say who transcribed it.
 */
export async function transcribeGocWithGemini(audio: Blob, opts: GocOnlineOptions = {}): Promise<GocOnlineResult> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) throw new GocOnlineError('offline', 'You are offline. Use on-device transcription or type the words.');
  const http = fetchHttp();
  const engines = await gocOnlineEngines(http);
  if (!engines.gemini) throw new GocOnlineError('not_enabled', 'Online transcription is not set up. Use on-device transcription or type the words.');
  const samples = await decodeForOnline(audio);
  try {
    return await runGocOnline(samplesSource(samples, audio), opts, { http });
  } catch (error) {
    if (error instanceof GocOnlineError) throw error;
    if (opts.signal?.aborted) throw new GocOnlineError('aborted', 'Stopped.');
    throw new GocOnlineError('offline', 'The online service could not be reached. Use on-device transcription or type the words.');
  }
}
