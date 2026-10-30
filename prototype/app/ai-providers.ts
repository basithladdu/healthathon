// Server-side chain of free-tier AI services. Every key stays on the server (Vercel -> Settings -> Environment Variables);
// the phone and the web bundle never ship a model or a key. Docs checked 2026-10-29, see docs/FREE_MODELS.md.
//
// Order (first configured answers; on timeout, 429, 5xx, a bad key or an unusable answer the next one is tried):
//   custom      SAHARA_AI_BASE_URL + SAHARA_AI_API_KEY + SAHARA_AI_MODEL   (any OpenAI-compatible service)
//   gemini      GEMINI_API_KEY            https://ai.google.dev/gemini-api/docs/openai
//   groq        GROQ_API_KEY              https://console.groq.com/docs/models
//   openrouter  OPENROUTER_API_KEY        https://openrouter.ai/docs/api/reference/limits  (":free" models only)
//   huggingface HF_TOKEN                  https://huggingface.co/docs/inference-providers/en/pricing
//   cloudflare  CLOUDFLARE_ACCOUNT_ID + CLOUDFLARE_API_TOKEN  https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/
// SAHARA_AI_CHAIN="groq,gemini" reorders or limits the chain. Per-provider model overrides: GEMINI_MODEL, GROQ_MODEL,
// OPENROUTER_MODEL, HF_MODEL, CLOUDFLARE_MODEL (SAHARA_AI_MODEL still sets the Gemini model when no custom service is set).
type Environment = Record<string, string | undefined>;
type Json = Record<string, unknown>;

export type ProviderId = 'custom' | 'gemini' | 'groq' | 'openrouter' | 'huggingface' | 'cloudflare';
export const PROVIDER_ORDER: readonly ProviderId[] = ['custom', 'gemini', 'groq', 'openrouter', 'huggingface', 'cloudflare'];

export const GEMINI_OPENAI = 'https://generativelanguage.googleapis.com/v1beta/openai';
export const GEMINI_NATIVE = 'https://generativelanguage.googleapis.com/v1beta';
export const GROQ_OPENAI = 'https://api.groq.com/openai/v1';
export const OPENROUTER_OPENAI = 'https://openrouter.ai/api/v1';
export const HF_ROUTER = 'https://router.huggingface.co/v1';
export const cloudflareBase = (account: string) => `https://api.cloudflare.com/client/v4/accounts/${account}/ai/v1`;

// Current ids from each provider's model list on 2026-10-29. Override with the *_MODEL variables when a provider retires one.
export const DEFAULT_MODELS = {
  gemini: 'gemini-3.5-flash-lite',
  groq: 'llama-3.3-70b-versatile',
  groqTranscribe: 'whisper-large-v3-turbo',
  openrouter: 'google/gemma-4-31b-it:free',
  openrouterFallbacks: ['google/gemma-4-26b-a4b-it:free'],
  huggingface: 'google/gemma-4-31B-it',
  cloudflare: '@cf/google/gemma-4-26b-a4b-it',
} as const;

export type ChatProvider = {
  id: ProviderId; label: string; base: string; key: string; model: string;
  /** OpenAI response_format json_object is documented for this service. Others get the same JSON instruction in the prompt. */
  json: boolean; fallbackModels?: string[]; extra?: Json;
};
export type AudioProvider = { id: ProviderId; label: string; kind: 'gemini' | 'openai'; base: string; key: string; model: string };

const LABELS: Record<ProviderId, string> = { custom: 'the AI service', gemini: 'Google Gemini', groq: 'Groq', openrouter: 'OpenRouter', huggingface: 'Hugging Face', cloudflare: 'Cloudflare' };

function chainOrder(env: Environment): ProviderId[] {
  const raw = env.SAHARA_AI_CHAIN?.trim();
  if (!raw) return [...PROVIDER_ORDER];
  const picked = raw.split(',').map((part) => part.trim().toLowerCase()).filter((part): part is ProviderId => (PROVIDER_ORDER as readonly string[]).includes(part));
  return picked.length ? [...new Set(picked)] : [...PROVIDER_ORDER];
}

/** Configured text services, in the order they are tried. A service without its key is simply left out. */
export function chatProviders(env: Environment = process.env): ChatProvider[] {
  const secret = (name: string) => env[name]?.trim() || '';
  const custom = secret('SAHARA_AI_BASE_URL').replace(/\/+$/u, '');
  const all: Partial<Record<ProviderId, ChatProvider>> = {};
  if (custom.startsWith('https://') && secret('SAHARA_AI_MODEL')) {
    let host = ''; try { host = new URL(custom).hostname; } catch { /* invalid */ }
    if (host) all.custom = { id: 'custom', label: (secret('SAHARA_AI_PROVIDER_NAME') || host).slice(0, 40), base: custom, key: secret('SAHARA_AI_API_KEY'), model: secret('SAHARA_AI_MODEL'), json: true };
  }
  if (secret('GEMINI_API_KEY')) all.gemini = { id: 'gemini', label: LABELS.gemini, base: GEMINI_OPENAI, key: secret('GEMINI_API_KEY'),
    model: secret('GEMINI_MODEL') || (custom ? '' : secret('SAHARA_AI_MODEL')) || DEFAULT_MODELS.gemini, json: true, extra: { reasoning_effort: 'low' } };
  if (secret('GROQ_API_KEY')) all.groq = { id: 'groq', label: LABELS.groq, base: GROQ_OPENAI, key: secret('GROQ_API_KEY'), model: secret('GROQ_MODEL') || DEFAULT_MODELS.groq, json: true };
  if (secret('OPENROUTER_API_KEY')) {
    const model = secret('OPENROUTER_MODEL') || DEFAULT_MODELS.openrouter;
    // Only ":free" models are ever listed, so an OpenRouter fallback can never bill a paid model.
    if (model.endsWith(':free')) all.openrouter = { id: 'openrouter', label: LABELS.openrouter, base: OPENROUTER_OPENAI, key: secret('OPENROUTER_API_KEY'), model, json: true,
      fallbackModels: secret('OPENROUTER_MODEL') ? [] : [...DEFAULT_MODELS.openrouterFallbacks] };
  }
  if (secret('HF_TOKEN')) all.huggingface = { id: 'huggingface', label: LABELS.huggingface, base: HF_ROUTER, key: secret('HF_TOKEN'), model: secret('HF_MODEL') || DEFAULT_MODELS.huggingface, json: false };
  const account = secret('CLOUDFLARE_ACCOUNT_ID');
  if (/^[a-f0-9]{32}$/iu.test(account) && secret('CLOUDFLARE_API_TOKEN')) all.cloudflare = { id: 'cloudflare', label: LABELS.cloudflare, base: cloudflareBase(account),
    key: secret('CLOUDFLARE_API_TOKEN'), model: secret('CLOUDFLARE_MODEL') || DEFAULT_MODELS.cloudflare, json: false };
  return chainOrder(env).flatMap((id) => all[id] ? [all[id]] : []);
}

/** Speech-to-text: Gemini hears audio directly; Groq runs Whisper; a custom service needs SAHARA_AI_TRANSCRIBE_MODEL. */
export function audioProviders(env: Environment = process.env): AudioProvider[] {
  const secret = (name: string) => env[name]?.trim() || '';
  const out: AudioProvider[] = [];
  for (const chat of chatProviders(env)) {
    if (chat.id === 'custom' && secret('SAHARA_AI_TRANSCRIBE_MODEL')) out.push({ id: 'custom', label: chat.label, kind: 'openai', base: chat.base, key: chat.key, model: secret('SAHARA_AI_TRANSCRIBE_MODEL') });
    if (chat.id === 'gemini') out.push({ id: 'gemini', label: chat.label, kind: 'gemini', base: GEMINI_NATIVE, key: chat.key, model: secret('SAHARA_AI_TRANSCRIBE_MODEL') && !secret('SAHARA_AI_BASE_URL') ? secret('SAHARA_AI_TRANSCRIBE_MODEL') : chat.model });
    if (chat.id === 'groq') out.push({ id: 'groq', label: chat.label, kind: 'openai', base: GROQ_OPENAI, key: chat.key, model: secret('GROQ_TRANSCRIBE_MODEL') || DEFAULT_MODELS.groqTranscribe });
  }
  return out;
}

/** The consent line must name every service the words may be sent to, not just the first. */
export function chainLabel(providers: { label: string }[], override = '') {
  if (override.trim()) return override.trim().slice(0, 60);
  const names = [...new Set(providers.map((provider) => provider.label))];
  let label = '';
  for (const name of names) { const next = label ? `${label} / ${name}` : name; if (next.length > 60) break; label = next; }
  return label;
}

export type Attempt = { provider: ProviderId; model: string; ok: boolean; status?: number; reason?: string; ms: number };
/** Thrown when no configured service produced a usable answer. status 429 only when every service said it was busy. */
export class ChainFailure extends Error {
  readonly status: number; readonly code: string; readonly attempts: Attempt[];
  constructor(status: number, code: string, message: string, attempts: Attempt[]) { super(message); this.status = status; this.code = code; this.attempts = attempts; }
}
/** A parse error marked final stops the chain (for example a safety refusal): another model must not be asked to try again. */
export class FinalOutputError extends Error {
  readonly cause: unknown;
  constructor(cause: unknown) { super('final'); this.cause = cause; }
}

const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);
/** Reasoning models may prepend <think>…</think>; it is never part of the answer. */
export const stripThinking = (content: string) => content.replace(/<think>[\s\S]*?<\/think>/gu, '').trim();
export const jsonPart = (content: string) => { const text = stripThinking(content); return JSON.parse(text.slice(text.indexOf('{'), text.lastIndexOf('}') + 1)) as unknown; };

export type ChatRequest = { system: string; user: string; json: boolean; maxTokens?: number; temperature?: number };
export type ChainOptions = { send?: typeof fetch; perAttemptMs?: number; deadlineMs?: number; signal?: AbortSignal; now?: () => number };
export type ChainResult<T> = { value: T; provider: ChatProvider; model: string; attempts: Attempt[] };

const JSON_ONLY = '\nReply with one JSON object only, with no text before or after it.';

/**
 * Tries each configured service in order. parse() checks the answer (citations, numbers, safety); if it throws, the next service is tried,
 * and if every service fails that way the last parse error is rethrown so callers keep their own error codes.
 */
export async function runChatChain<T>(providers: ChatProvider[], request: ChatRequest, parse: (content: string) => T, options: ChainOptions = {}): Promise<ChainResult<T>> {
  const send = options.send ?? fetch; const now = options.now ?? Date.now;
  const started = now(); const deadline = started + (options.deadlineMs ?? 55_000); const perAttempt = options.perAttemptMs ?? 25_000;
  const attempts: Attempt[] = []; let parseError: unknown = null;
  for (const provider of providers) {
    const left = deadline - now();
    if (left < 1500) break;
    options.signal?.throwIfAborted();
    const begun = now();
    const record = (ok: boolean, status?: number, reason?: string) => attempts.push({ provider: provider.id, model: provider.model, ok, status, reason, ms: now() - begun });
    const timeout = AbortSignal.timeout(Math.min(perAttempt, left));
    const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
    let remote: Response;
    try {
      remote = await send(`${provider.base}/chat/completions`, { method: 'POST', redirect: 'error', signal, cache: 'no-store',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...(provider.key ? { Authorization: `Bearer ${provider.key}` } : {}) },
        body: JSON.stringify({ model: provider.model, ...(provider.fallbackModels?.length ? { models: [provider.model, ...provider.fallbackModels] } : {}),
          temperature: request.temperature ?? 0, max_tokens: request.maxTokens ?? 4096, ...(provider.extra ?? {}),
          messages: [{ role: 'system', content: request.json ? request.system + JSON_ONLY : request.system }, { role: 'user', content: request.user }],
          ...(request.json && provider.json ? { response_format: { type: 'json_object' } } : {}) }) });
    } catch (error) {
      if (options.signal?.aborted) throw error;
      record(false, undefined, timeout.aborted ? 'timeout' : 'network'); continue;
    }
    if (!remote.ok) { void remote.body?.cancel().catch(() => {}); record(false, remote.status, remote.status === 429 ? 'busy' : 'http'); continue; }
    const raw: unknown = await remote.json().catch(() => null);
    const choice = isObject(raw) && Array.isArray(raw.choices) ? raw.choices[0] : null;
    const content = isObject(choice) && isObject(choice.message) && typeof choice.message.content === 'string' ? choice.message.content : null;
    if (!isObject(choice) || choice.finish_reason === 'length' || content === null || !stripThinking(content)) { record(false, remote.status, 'incomplete'); continue; }
    try {
      const value = parse(content);
      record(true, remote.status);
      // OpenRouter reports which of its free models actually answered.
      const model = isObject(raw) && typeof raw.model === 'string' && raw.model.length <= 100 && provider.id === 'openrouter' ? raw.model : provider.model;
      return { value, provider, model, attempts };
    } catch (error) {
      if (error instanceof FinalOutputError) { record(false, remote.status, 'refused'); throw error.cause; }
      record(false, remote.status, 'unusable'); parseError = error;
    }
  }
  if (parseError) throw parseError;
  const busy = attempts.length > 0 && attempts.every((attempt) => attempt.status === 429);
  throw new ChainFailure(busy ? 429 : providers.length ? 502 : 503, busy ? 'provider_busy' : providers.length ? 'provider_unavailable' : 'not_enabled',
    busy ? 'The free AI services are busy. Try again in a minute; your original is unchanged.' : 'The AI service could not finish. Your original is unchanged.', attempts);
}

export type TranscribeResult = { text: string; provider: AudioProvider; attempts: Attempt[] };

/** Speech-to-text through the audio chain. language is an ISO 639-1 code or '' for automatic. */
export async function runTranscribeChain(providers: AudioProvider[], audio: { bytes: Uint8Array; base64: string; mime: string }, language: { code: string; name: string },
  options: ChainOptions = {}): Promise<TranscribeResult> {
  const send = options.send ?? fetch; const now = options.now ?? Date.now;
  const deadline = now() + (options.deadlineMs ?? 55_000); const perAttempt = options.perAttemptMs ?? 30_000;
  const attempts: Attempt[] = [];
  for (const provider of providers) {
    const left = deadline - now();
    if (left < 1500) break;
    const begun = now();
    const record = (ok: boolean, status?: number, reason?: string) => attempts.push({ provider: provider.id, model: provider.model, ok, status, reason, ms: now() - begun });
    const timeout = AbortSignal.timeout(Math.min(perAttempt, left));
    const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
    try {
      let text = '';
      if (provider.kind === 'gemini') {
        const remote = await send(`${provider.base}/models/${encodeURIComponent(provider.model)}:generateContent`, { method: 'POST', redirect: 'error', signal,
          headers: { 'Content-Type': 'application/json', 'x-goog-api-key': provider.key },
          body: JSON.stringify({ generationConfig: { temperature: 0 }, contents: [{ role: 'user', parts: [
            { text: `Transcribe this recording verbatim in ${language.name}, in that language's own script. Output only the words spoken. Do not translate, summarise, correct or add anything. If nothing intelligible is said, output nothing.` },
            { inline_data: { mime_type: audio.mime, data: audio.base64 } }] }] }) });
        if (!remote.ok) { void remote.body?.cancel().catch(() => {}); record(false, remote.status, remote.status === 429 ? 'busy' : 'http'); continue; }
        const value = await remote.json().catch(() => null) as { candidates?: { content?: { parts?: { text?: unknown }[] } }[] } | null;
        text = (value?.candidates?.[0]?.content?.parts ?? []).map((part) => typeof part.text === 'string' ? part.text : '').join('').trim();
      } else {
        const form = new FormData();
        form.append('file', new Blob([audio.bytes as BlobPart], { type: audio.mime }), 'turn.m4a');
        form.append('model', provider.model); form.append('response_format', 'json'); form.append('temperature', '0');
        if (language.code) form.append('language', language.code);
        const remote = await send(`${provider.base}/audio/transcriptions`, { method: 'POST', redirect: 'error', signal, body: form,
          headers: { Accept: 'application/json', ...(provider.key ? { Authorization: `Bearer ${provider.key}` } : {}) } });
        if (!remote.ok) { void remote.body?.cancel().catch(() => {}); record(false, remote.status, remote.status === 429 ? 'busy' : 'http'); continue; }
        const value: unknown = await remote.json().catch(() => null);
        text = isObject(value) && typeof value.text === 'string' ? value.text.trim() : '';
      }
      if (!text || text.length > 12_000) { record(false, 200, 'unusable'); continue; }
      record(true, 200);
      return { text, provider, attempts };
    } catch (error) {
      if (options.signal?.aborted) throw error;
      record(false, undefined, timeout.aborted ? 'timeout' : 'network');
    }
  }
  const busy = attempts.length > 0 && attempts.every((attempt) => attempt.status === 429);
  const unusable = attempts.some((attempt) => attempt.reason === 'unusable');
  throw new ChainFailure(busy ? 429 : 502, busy ? 'provider_busy' : unusable ? 'invalid_transcript' : 'provider_unavailable',
    busy ? 'The free AI services are busy. Try again in a minute; your recording is unchanged.' : unusable ? 'A usable transcript was not returned. Your recording is unchanged.' : 'The AI service could not finish. Your recording is unchanged.', attempts);
}
