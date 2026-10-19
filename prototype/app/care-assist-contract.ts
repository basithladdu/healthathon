/** Shared wire contract. Original words are never replaced by generated text. */
export const CARE_ASSIST_FIELDS = ['priorities', 'participants', 'topics', 'openQuestions', 'followUp'] as const;
export type CareAssistField = typeof CARE_ASSIST_FIELDS[number];
export const CARE_ASSIST_LANGUAGES = [
  { code: 'en-IN', label: 'English' }, { code: 'hi-IN', label: 'हिन्दी' },
  { code: 'kn-IN', label: 'ಕನ್ನಡ' }, { code: 'te-IN', label: 'తెలుగు' },
  { code: 'ta-IN', label: 'தமிழ்' }, { code: 'ml-IN', label: 'മലയാളം' },
  { code: 'mr-IN', label: 'मराठी' }, { code: 'bn-IN', label: 'বাংলা' },
  { code: 'gu-IN', label: 'ગુજરાતી' }, { code: 'pa-IN', label: 'ਪੰਜਾਬੀ' },
  { code: 'od-IN', label: 'ଓଡ଼ିଆ' }, { code: 'ur-IN', label: 'اردو' },
] as const;
export type CareAssistSpeaker = 'Patient' | 'Family / carer' | 'Doctor' | 'Unknown';
export type CareAssistTurn = { line: number; start: number; end: number; text: string; speaker: CareAssistSpeaker };
export type CareAssistEvidence = CareAssistTurn & { field: CareAssistField };
export type CareAssistResult = {
  fields: Record<CareAssistField, string>; excerpts: Record<CareAssistField, string>;
  evidence: CareAssistEvidence[]; sourceHash: string; provider: 'sarvam'; model: string;
  generatedAt: string; requiresReview: true;
};
export type CareTranscriptResult = {
  transcript: string; originalTranscript: string; sourceHash: string; language: string;
  speaker: 'Unknown'; speakerConfirmed: false; provider: 'sarvam'; model: string; requiresReview: true;
};
export type CareTranslationResult = {
  original: string; translation: string; versionId: string; sourceHash: string;
  sourceLanguage: string; targetLanguage: string; provider: 'sarvam'; model: string;
  requiresReview: true; segments: Array<{ start: number; end: number; original: string; translation: string }>;
};

export function assistObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function assistLanguage(value: unknown): value is string {
  return typeof value === 'string' && CARE_ASSIST_LANGUAGES.some((language) => language.code === value);
}

export async function careSourceHash(source: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(source));
  return [...new Uint8Array(hash)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function careAssistTurns(source: string): CareAssistTurn[] {
  let line = 0;
  return [...source.matchAll(/[^\r\n]*(?:\r?\n|$)/gu)].flatMap((match) => {
    if (!match[0]) return [];
    line += 1;
    const raw = match[0].replace(/[\r\n]+$/u, '');
    const text = raw.trim();
    if (!text) return [];
    const start = match.index + raw.indexOf(text);
    const label = /^(?:\[(Patient|Family \/ carer|Doctor) · [^\]\r\n]+\]|(Patient|Family \/ carer|Doctor):)/u.exec(text);
    return [{ line, start, end: start + text.length, text, speaker: (label?.[1] || label?.[2] || 'Unknown') as CareAssistSpeaker }];
  });
}

/** A model can choose line IDs, never rewrite, shorten, or manufacture an excerpt. */
export function checkedCareSelection(value: unknown, source: string) {
  if (!assistObject(value) || Object.keys(value).length !== CARE_ASSIST_FIELDS.length) throw new Error('evidence_mismatch');
  const turns = careAssistTurns(source);
  const fields = Object.fromEntries(CARE_ASSIST_FIELDS.map((key) => [key, ''])) as Record<CareAssistField, string>;
  const excerpts = { ...fields };
  const evidence: CareAssistEvidence[] = [];
  for (const field of CARE_ASSIST_FIELDS) {
    const ids = value[field];
    if (!Array.isArray(ids) || ids.length > 12 || ids.some((id) => !Number.isInteger(id)) || new Set(ids).size !== ids.length) throw new Error('evidence_mismatch');
    const selected = ids.map((id) => turns.find((turn) => turn.line === id));
    if (selected.some((turn) => !turn)) throw new Error('evidence_mismatch');
    const ordered = (selected as CareAssistTurn[]).sort((a, b) => a.start - b.start);
    if (field === 'priorities' && ordered.some((turn) => turn.speaker !== 'Patient')) throw new Error('patient_speaker_required');
    fields[field] = ordered.map((turn) => turn.text).join('\n');
    if (fields[field].length > 4000) throw new Error('evidence_too_large');
    if (ordered.length) excerpts[field] = source.slice(ordered[0].start, ordered[ordered.length - 1].end);
    evidence.push(...ordered.map((turn) => ({ ...turn, field })));
  }
  return { fields, excerpts, evidence };
}

/** Translation chunks keep every character and carry offsets into the unchanged original. */
export function careTranslationChunks(source: string, limit = 1800) {
  if (limit < 1 || limit > 2000) throw new Error('invalid_chunk_limit');
  const chunks: Array<{ start: number; end: number; original: string }> = [];
  let start = 0;
  while (start < source.length) {
    let end = Math.min(source.length, start + limit);
    if (end < source.length) {
      const boundary = source.lastIndexOf('\n', end - 1);
      if (boundary > start + limit / 2) end = boundary + 1;
      // Never split a UTF-16 surrogate pair.
      if (/[\uD800-\uDBFF]/u.test(source[end - 1])) end -= 1;
    }
    chunks.push({ start, end, original: source.slice(start, end) });
    start = end;
  }
  return chunks;
}

export function translatedNumbersMatch(original: string, translated: string) {
  const numbers = (text: string) => (text.match(/[0-9]+(?:[.,:/-][0-9]+)*/gu) || []).sort().join('|');
  return numbers(original) === numbers(translated);
}
