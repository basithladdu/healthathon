// Goals-of-care analysis for the Android app: after the conversation is transcribed, Gemini reads the whole transcript once and maps
// what was said onto the emergency care plan form (the same fields, values and JSON as /api/goc-extract, GOC_EXTRACT_RULES).
// Every answer is checked before anyone sees it: only known fields and values, and only quotes that are word for word in one line
// of the transcript. A field with no such quote stays empty ("not discussed"). The doctor confirms every item; nothing is applied here.
// The checked result is saved to Supabase (public.sahara_goc_analyses) as the signed-in account, under its row-level security.
import { GEMINI_NATIVE, jsonPart } from './ai-providers.ts';
import { GeminiCallError, withGeminiKeys, type GeminiKeySlot } from './gemini-keys.ts';
import { GOC_EXTRACT_RULES } from './goc-extract-server.ts';
import { GOC_DECISION_FIELDS, GOC_FIELD_VALUES, type GocDecisionField, type GocQuote, type GocSuggestion } from './goc-extract.ts';

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);

export type GocAnalyzeLine = { startSec: number; speaker: 'DOCTOR' | 'PATIENT_PARTY' | 'UNKNOWN'; lang: string; text: string };
export type GocAnalysis = { suggestions: GocSuggestion[]; ownWords: GocQuote[]; unacceptableOutcomes: GocQuote[]; notDiscussed: GocDecisionField[] };

export const GOC_ANALYZE_MAX_LINES = 6000;
export const GOC_ANALYZE_MAX_CHARS = 400_000;
const SPEAKERS = new Set(['DOCTOR', 'PATIENT_PARTY', 'UNKNOWN']);

export class GocAnalyzeFailure extends Error {
  readonly status: number; readonly code: string;
  constructor(status: number, code: string, message: string) { super(message); this.status = status; this.code = code; }
}

/** The lines the app sent, checked. Throws GocAnalyzeFailure 400/413. */
export function gocAnalyzeLines(value: unknown): GocAnalyzeLine[] {
  if (!Array.isArray(value) || value.length === 0) throw new GocAnalyzeFailure(400, 'source_required', 'Add the conversation first.');
  if (value.length > GOC_ANALYZE_MAX_LINES) throw new GocAnalyzeFailure(413, 'too_large', 'The conversation is too long to read online.');
  let total = 0;
  const out: GocAnalyzeLine[] = [];
  for (const item of value) {
    if (!isObject(item) || typeof item.text !== 'string') throw new GocAnalyzeFailure(400, 'invalid_body', 'The conversation could not be read.');
    const text = item.text.normalize('NFC').replace(/\s+/gu, ' ').trim();
    if (!text) continue;
    total += text.length;
    const startSec = Number(item.startSec ?? 0);
    out.push({ startSec: Number.isFinite(startSec) && startSec >= 0 ? startSec : 0, speaker: SPEAKERS.has(String(item.speaker)) ? item.speaker as GocAnalyzeLine['speaker'] : 'UNKNOWN',
      lang: typeof item.lang === 'string' ? item.lang.slice(0, 12) : 'und', text });
  }
  if (!out.length) throw new GocAnalyzeFailure(400, 'source_required', 'Add the conversation first.');
  if (total > GOC_ANALYZE_MAX_CHARS) throw new GocAnalyzeFailure(413, 'too_large', 'The conversation is too long to read online.');
  return out;
}

/** What Gemini reads: numbered lines with who spoke. The speaker label is never part of a quote. */
export const gocAnalyzeInput = (lines: GocAnalyzeLine[]) => lines.map((line, index) => `${index + 1}. [${line.speaker}] ${line.text}`).join('\n');

const QUOTE = { type: 'OBJECT', required: ['line', 'quote'], propertyOrdering: ['line', 'quote'], properties: { line: { type: 'INTEGER' }, quote: { type: 'STRING' } } };
export const GOC_ANALYZE_SCHEMA = { type: 'OBJECT', required: ['suggestions', 'ownWords', 'unacceptableOutcomes'], properties: {
  suggestions: { type: 'ARRAY', items: { type: 'OBJECT', required: ['field', 'value', 'line', 'quote'], propertyOrdering: ['field', 'value', 'line', 'quote'],
    properties: { field: { type: 'STRING', enum: [...GOC_DECISION_FIELDS] }, value: { type: 'STRING' }, line: { type: 'INTEGER' }, quote: { type: 'STRING' } } } },
  ownWords: { type: 'ARRAY', items: QUOTE }, unacceptableOutcomes: { type: 'ARRAY', items: QUOTE } } };

/**
 * Checks Gemini's answer against the transcript. Anything not word for word in a line, an unknown field or value, or a quote of fewer
 * than 3 characters is dropped. who is the speaker of the quoted line ('doctor', 'patient or family' or '').
 */
export function checkedGocAnalysis(answer: unknown, lines: GocAnalyzeLine[]): GocAnalysis {
  const texts = lines.map((line) => line.text);
  const starts: number[] = []; let offset = 0;
  for (const text of texts) { starts.push(offset); offset += text.length + 1; }
  const locate = (lineNo: unknown, rawQuote: unknown): { index: number; quote: string } | null => {
    if (typeof rawQuote !== 'string') return null;
    const quote = rawQuote.normalize('NFC').replace(/\s+/gu, ' ').trim();
    if (quote.length < 3) return null;
    const given = Number.isInteger(lineNo) ? (lineNo as number) - 1 : -1;
    if (given >= 0 && given < texts.length && texts[given].includes(quote)) return { index: given, quote };
    const found = texts.findIndex((text) => text.includes(quote));
    return found >= 0 ? { index: found, quote } : null;
  };
  const who = (index: number) => lines[index].speaker === 'DOCTOR' ? 'doctor' : lines[index].speaker === 'PATIENT_PARTY' ? 'patient or family' : '';
  const value = isObject(answer) ? answer : {};
  const suggestions: GocSuggestion[] = []; const seen = new Set<string>();
  for (const item of (Array.isArray(value.suggestions) ? value.suggestions : []).slice(0, 80)) {
    if (!isObject(item) || typeof item.field !== 'string' || !(item.field in GOC_FIELD_VALUES)) continue;
    const field = item.field as GocDecisionField; const option = String(item.value ?? '');
    if (!GOC_FIELD_VALUES[field].includes(option)) continue;
    const place = locate(item.line, item.quote);
    if (!place) continue;
    const key = `${field}|${option}|${place.index}|${place.quote}`;
    if (seen.has(key)) continue;
    seen.add(key);
    suggestions.push({ field, values: [option], ruledOut: [], quote: place.quote, source: 'ai', at: starts[place.index] + texts[place.index].indexOf(place.quote), who: who(place.index) });
  }
  const quotes = (list: unknown) => {
    const out: GocQuote[] = []; const kept = new Set<string>();
    for (const item of (Array.isArray(list) ? list : []).slice(0, 40)) {
      const place = isObject(item) ? locate(item.line, item.quote) : null;
      if (place && !kept.has(place.quote)) { kept.add(place.quote); out.push({ quote: place.quote }); }
    }
    return out;
  };
  suggestions.sort((a, b) => a.at - b.at);
  const discussed = new Set(suggestions.map((s) => s.field));
  return { suggestions, ownWords: quotes(value.ownWords), unacceptableOutcomes: quotes(value.unacceptableOutcomes), notDiscussed: GOC_DECISION_FIELDS.filter((field) => !discussed.has(field)) };
}

class ModelMissing extends Error {}
class UnusableAnswer extends Error {}

function readAnswer(value: unknown, lines: GocAnalyzeLine[]): GocAnalysis {
  const answer = value as { promptFeedback?: { blockReason?: string }; candidates?: { finishReason?: string; content?: { parts?: { text?: unknown; thought?: boolean }[] } }[] } | null;
  if (answer?.promptFeedback?.blockReason) throw new GocAnalyzeFailure(502, 'gemini_rejected', 'Google Gemini declined to read this conversation.');
  const candidate = answer?.candidates?.[0];
  if (!candidate) throw new UnusableAnswer();
  if (candidate.finishReason && ['SAFETY', 'RECITATION', 'PROHIBITED_CONTENT', 'BLOCKLIST', 'SPII'].includes(candidate.finishReason)) throw new GocAnalyzeFailure(502, 'gemini_rejected', 'Google Gemini declined to read this conversation.');
  if (candidate.finishReason === 'MAX_TOKENS') throw new UnusableAnswer();
  const text = (candidate.content?.parts ?? []).filter((part) => !part.thought).map((part) => typeof part.text === 'string' ? part.text : '').join('');
  let parsed: unknown;
  try { parsed = jsonPart(text); } catch { throw new UnusableAnswer(); }
  if (!isObject(parsed) || !Array.isArray(parsed.suggestions)) throw new UnusableAnswer();
  return checkedGocAnalysis(parsed, lines);
}

export type GocAnalyzeOptions = { slots: GeminiKeySlot[]; models: string[]; send: typeof fetch; now?: () => number; budgetMs?: number; perAttemptMs?: number };

/**
 * One Gemini reading of the whole transcript. Keys are tried in order (gemini-keys.ts); on each key the models are tried in order
 * when one is not found or gives an unusable answer. Throws GocAnalyzeFailure.
 */
export async function analyzeGoc(lines: GocAnalyzeLine[], options: GocAnalyzeOptions): Promise<{ analysis: GocAnalysis; model: string; slot: string }> {
  const now = options.now ?? Date.now;
  const deadline = now() + (options.budgetMs ?? 240_000);
  const body = JSON.stringify({
    systemInstruction: { parts: [{ text: GOC_EXTRACT_RULES }] },
    contents: [{ role: 'user', parts: [{ text: `Transcript, one numbered line per speaker turn:\n${gocAnalyzeInput(lines)}` }] }],
    generationConfig: { temperature: 0, responseMimeType: 'application/json', responseSchema: GOC_ANALYZE_SCHEMA, maxOutputTokens: 16_384 },
  });
  let unusable = false;
  try {
    const result = await withGeminiKeys(options.slots, async (slot) => {
      let missing = 0;
      for (const model of options.models) {
        const left = deadline - now();
        if (left < 3000) break;
        let response: Response;
        try {
          response = await options.send(`${GEMINI_NATIVE}/models/${encodeURIComponent(model)}:generateContent`, { method: 'POST', redirect: 'error', cache: 'no-store',
            signal: AbortSignal.timeout(Math.min(options.perAttemptMs ?? 120_000, left)), headers: { 'x-goog-api-key': slot.key, 'Content-Type': 'application/json', Accept: 'application/json' }, body });
        } catch { throw new GeminiCallError(null); }
        if (!response.ok) {
          const detail = (await response.text().catch(() => '')).slice(0, 600);
          const retry = Number(response.headers.get('retry-after'));
          if (response.status === 404 || (response.status === 400 && /model/iu.test(detail) && /not found|not supported|unknown/iu.test(detail))) { missing++; continue; }
          throw new GeminiCallError(response.status, detail, Number.isFinite(retry) && retry > 0 ? retry : undefined);
        }
        try { return { analysis: readAnswer(await response.json().catch(() => null), lines), model }; }
        catch (error) { if (error instanceof UnusableAnswer) { unusable = true; continue; } throw error; }
      }
      if (unusable) throw new UnusableAnswer();
      if (missing) throw new ModelMissing();
      throw new GeminiCallError(null, 'out of time');
    });
    return { ...result.value, slot: result.slot.slot };
  } catch (error) {
    if (error instanceof GocAnalyzeFailure) throw error;
    if (error instanceof UnusableAnswer) throw new GocAnalyzeFailure(502, 'invalid_output', 'Google Gemini did not return a usable reading. The transcript is unchanged.');
    if (error instanceof ModelMissing) throw new GocAnalyzeFailure(502, 'gemini_unavailable', 'The Gemini model is not available.');
    if (error instanceof GeminiCallError) {
      if (error.status === 429) throw new GocAnalyzeFailure(429, 'provider_busy', 'Google Gemini is busy. Try again in a minute; the transcript is unchanged.');
      if (error.status === 503 && error.detail === 'not configured') throw new GocAnalyzeFailure(503, 'not_enabled', 'Gemini is not set up.');
      if (error.status !== null && error.status >= 400 && error.status < 500 && error.status !== 401 && error.status !== 403) throw new GocAnalyzeFailure(502, 'gemini_rejected', 'Google Gemini could not read this conversation.');
      throw new GocAnalyzeFailure(502, 'gemini_unavailable', 'Google Gemini could not finish. The transcript is unchanged.');
    }
    throw error;
  }
}

export async function sha256Hex(text: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export type GocSaveTarget = { url: string; anon: string; authorization: string };
export type GocAnalysisRow = {
  id: string; patient_ref: string | null; conversation_ref: string | null; transcript: GocAnalyzeLine[]; transcript_sha256: string;
  suggestions: GocSuggestion[]; own_words: GocQuote[]; unacceptable_outcomes: GocQuote[]; not_discussed: GocDecisionField[];
  model: string; transcribed_by: string; language: string; analyzed_at: string;
};

/**
 * Saves one checked analysis as the signed-in account (its own access token, the project's publishable key, row-level security).
 * Resending the same id keeps the first row. Returns false when the table is missing or the row was refused; never logs the content.
 */
export async function saveGocAnalysis(target: GocSaveTarget, row: GocAnalysisRow, send: typeof fetch): Promise<boolean> {
  const response = await send(`${target.url.replace(/\/+$/u, '')}/rest/v1/sahara_goc_analyses?on_conflict=id`, { method: 'POST', redirect: 'error', cache: 'no-store',
    signal: AbortSignal.timeout(15_000), headers: { apikey: target.anon, Authorization: target.authorization, 'Content-Type': 'application/json',
      Prefer: 'return=minimal,resolution=ignore-duplicates' }, body: JSON.stringify(row) }).catch(() => null);
  if (!response) { console.warn('goc_analysis_not_saved', 'network'); return false; }
  void response.body?.cancel().catch(() => {});
  if (!response.ok) console.warn('goc_analysis_not_saved', response.status);
  return response.ok;
}
