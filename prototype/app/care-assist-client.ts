import { assistObject, assistLanguage, careSourceHash, checkedCareSelection, translatedNumbersMatch, CARE_ASSIST_FIELDS, type CareAssistResult, type CareTranscriptResult, type CareTranslationResult } from './care-assist-contract.ts';

export class CareAssistError extends Error {
  readonly code: string;
  constructor(code: string, message: string) { super(message); this.code = code; }
}
type Options = { signal?: AbortSignal; consent: true; request?: typeof fetch };
function error(code: string, message: string): never { throw new CareAssistError(code, message); }

async function resultBody(response: Response, limit = 256 * 1024): Promise<unknown> {
  if (!response.headers.get('content-type')?.includes('application/json') || !response.body) error('invalid_response', 'The service returned an unreadable response. Your original is unchanged.');
  const reader = response.body.getReader(); const decoder = new TextDecoder('utf-8', { fatal: true });
  let bytes = 0; let text = '';
  try {
    while (true) {
      const next = await reader.read(); if (next.done) break;
      bytes += next.value.byteLength;
      if (bytes > limit) error('invalid_response', 'The returned content is too large.');
      text += decoder.decode(next.value, { stream: true });
    }
    return JSON.parse(text + decoder.decode());
  } catch (cause) {
    if (cause instanceof CareAssistError) throw cause;
    return error('invalid_response', 'The response could not be checked. Your original is unchanged.');
  } finally { void reader.cancel().catch(() => {}); reader.releaseLock(); }
}

async function post(path: string, body: Record<string, unknown>, accessToken: string, options: Options) {
  if (options.consent !== true) error('consent_required', 'Agree to online processing first.');
  if (!/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u.test(accessToken) || accessToken.length > 16384) error('sign_in_required', 'Sign in to Sahara first.');
  const signal = options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(300_000)]) : AbortSignal.timeout(300_000);
  try {
    signal.throwIfAborted();
    const remote = await (options.request || fetch)(`/api/care-assist${path}`, { method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ ...body, consent: true, requestId: crypto.randomUUID() }), signal,
      cache: 'no-store', credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer' });
    const value = await resultBody(remote); signal.throwIfAborted();
    if (!remote.ok) error(assistObject(value) && typeof value.code === 'string' ? value.code : 'unavailable',
      assistObject(value) && typeof value.error === 'string' ? value.error.slice(0, 400) : 'Online assistance could not finish. Your original is unchanged.');
    return value;
  } catch (cause) {
    if (cause instanceof CareAssistError) throw cause;
    if (signal.aborted) return error('cancelled', 'Stopped. Your original content is unchanged.');
    return error('unavailable', 'Could not connect. Your original content is unchanged; try again.');
  }
}

export async function careAssistCapabilities(signal?: AbortSignal, request: typeof fetch = fetch) {
  const remote = await request('/api/care-assist', { signal, cache: 'no-store', credentials: 'omit', redirect: 'error' });
  const value = await resultBody(remote, 16 * 1024);
  if (!remote.ok || !assistObject(value) || value.provider !== 'sarvam' || typeof value.ready !== 'boolean'
    || typeof value.audioReady !== 'boolean' || typeof value.translationReady !== 'boolean') error('unavailable', 'Online assistance is not available.');
  return { ready: value.ready, audioReady: value.audioReady, translationReady: value.translationReady };
}

export async function careAssist(source: string, accessToken: string, options: Options): Promise<CareAssistResult> {
  if (!source.trim() || source.length > 12000) error('invalid_source', 'Use a conversation up to 12,000 characters.');
  const hash = await careSourceHash(source);
  const value = await post('', { source }, accessToken, options);
  if (!assistObject(value) || value.sourceHash !== hash || value.requiresReview !== true || value.provider !== 'sarvam'
    || value.model !== 'sarvam-105b' || typeof value.generatedAt !== 'string' || !Number.isFinite(Date.parse(value.generatedAt))
    || !assistObject(value.fields) || !assistObject(value.excerpts) || !Array.isArray(value.evidence)) error('evidence_mismatch', 'The draft did not match this conversation. Nothing was filled in.');
  const evidence = value.evidence as unknown[];
  const ids = Object.fromEntries(CARE_ASSIST_FIELDS.map((field) => [field, evidence.filter((item) => assistObject(item) && item.field === field).map((item) => assistObject(item) ? item.line : null)]));
  const checked = checkedCareSelection(ids, source);
  if (JSON.stringify(checked.fields) !== JSON.stringify(value.fields) || JSON.stringify(checked.excerpts) !== JSON.stringify(value.excerpts)
    || JSON.stringify(checked.evidence) !== JSON.stringify(value.evidence)) error('evidence_mismatch', 'The draft did not match complete source turns. Nothing was filled in.');
  return { ...checked, sourceHash: hash, provider: 'sarvam', model: value.model, generatedAt: value.generatedAt, requiresReview: true };
}

export async function careTranscribe(
  file: File, details: { language: string; durationMs: number; recordingConsent: true; processingConsent: true },
  accessToken: string, options: Options,
): Promise<CareTranscriptResult> {
  if (details.recordingConsent !== true || details.processingConsent !== true) error('consent_required', 'Everyone must separately agree to recording and online processing.');
  if (file.size < 16 || file.size > 2 * 1024 * 1024 || !Number.isFinite(details.durationMs) || details.durationMs <= 0 || details.durationMs > 30000) error('audio_too_large', 'Use one speaker’s turn, up to 30 seconds and 2 MB.');
  if (details.language !== 'unknown' && !assistLanguage(details.language)) error('language_unavailable', 'Choose a supported recording language.');
  options.signal?.throwIfAborted();
  const bytes = new Uint8Array(await file.arrayBuffer()); let encoded = '';
  for (let start = 0; start < bytes.length; start += 8192) encoded += String.fromCharCode(...bytes.subarray(start, start + 8192));
  const value = await post('/transcribe', { ...details, audio: { data: btoa(encoded), mimeType: file.type.split(';')[0] } }, accessToken, options);
  if (!assistObject(value) || typeof value.transcript !== 'string' || !value.transcript.trim() || value.transcript.length > 12000
    || value.originalTranscript !== value.transcript || value.sourceHash !== await careSourceHash(value.transcript)
    || value.provider !== 'sarvam' || value.model !== 'saaras:v3' || value.requiresReview !== true
    || value.speaker !== 'Unknown' || value.speakerConfirmed !== false || value.language !== 'unknown' && !assistLanguage(value.language)) error('invalid_transcript', 'The transcript could not be checked. Your recording is unchanged.');
  return value as CareTranscriptResult;
}

export async function careTranslate(source: string,
  details: { versionId: string; sourceHash: string; sourceLanguage: string; targetLanguage: string },
  accessToken: string, options: Options,
): Promise<CareTranslationResult> {
  if (details.sourceHash !== await careSourceHash(source)) error('source_changed', 'The note changed. Choose its current version.');
  const value = await post('/translate', { source, ...details }, accessToken, options);
  if (!assistObject(value) || value.original !== source || value.sourceHash !== details.sourceHash || value.versionId !== details.versionId
    || value.sourceLanguage !== details.sourceLanguage || value.targetLanguage !== details.targetLanguage || value.provider !== 'sarvam'
    || value.model !== 'sarvam-translate:v1' || value.requiresReview !== true || typeof value.translation !== 'string' || !value.translation.trim()
    || !Array.isArray(value.segments)) error('source_changed', 'The translation did not match this note version. Use the original.');
  let position = 0; const translations: string[] = [];
  for (const segment of value.segments) {
    if (!assistObject(segment) || segment.start !== position || typeof segment.end !== 'number' || !Number.isInteger(segment.end)
      || segment.end <= position || segment.original !== source.slice(position, segment.end) || typeof segment.translation !== 'string'
      || !translatedNumbersMatch(segment.original as string, segment.translation)) error('invalid_translation', 'The translation did not cover the original note.');
    position = segment.end; translations.push(segment.translation);
  }
  if (position !== source.length || translations.join('\n') !== value.translation) error('invalid_translation', 'The translation did not cover the complete original note.');
  return value as CareTranslationResult;
}

/** Bind words to a role only after a person checks both the audio and the transcript. */
export function confirmedTranscriptTurn(original: string, reviewed: string, speaker: string, language: string, confirmed: boolean) {
  if (!confirmed || !original.trim() || !reviewed.trim() || reviewed.length > 12000 || !['Patient', 'Family / carer', 'Doctor', 'Unknown'].includes(speaker)
    || language !== 'unknown' && !assistLanguage(language)) error('review_required', 'Check the words and who spoke before adding this turn.');
  return `[Unknown · ${language} · machine original] ${original.replace(/[\r\n]+/gu, ' ')}\n[${speaker} · ${language} · human-reviewed] ${reviewed.replace(/[\r\n]+/gu, ' ')}`;
}
