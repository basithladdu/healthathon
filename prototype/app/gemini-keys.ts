// Gemini API keys for the goals-of-care path, with fallback from one key to the next. The keys stay on the server
// (Vercel -> Project -> Settings -> Environment Variables) and are never logged: logs name only the slot (GEMINI_API_KEY_2).
//
//   GEMINI_API_KEY_1 … GEMINI_API_KEY_5   all optional; only the ones that are set are used, in that order, gaps skipped
//   SAHARA_AI_API_KEY                     the older name, used while SAHARA_AI_BASE_URL is unset or points at Gemini
//   GEMINI_API_KEY                        the older name, tried last
// A key that is set twice under different names is used once. With none of them set, the GOC path reports "not configured".
//
// When a key fails with 429 (quota), 5xx, 401/403 (bad or blocked key), a 400 that says the key is invalid, a timeout or a
// network error, the next key is tried. Any other 4xx is about the request itself and stops at once: another key would not help.
type Environment = Record<string, string | undefined>;

export type GeminiKeySlot = { slot: string; key: string };
export const GEMINI_KEY_SLOTS = ['GEMINI_API_KEY_1', 'GEMINI_API_KEY_2', 'GEMINI_API_KEY_3', 'GEMINI_API_KEY_4', 'GEMINI_API_KEY_5'] as const;

/** The keys that are set, in the order they are tried. */
export function geminiKeySlots(env: Environment = process.env): GeminiKeySlot[] {
  const secret = (name: string) => env[name]?.trim() || '';
  const base = secret('SAHARA_AI_BASE_URL');
  const saharaIsGemini = !base || base.startsWith('https://generativelanguage.googleapis.com');
  const names = [...GEMINI_KEY_SLOTS, ...(saharaIsGemini ? ['SAHARA_AI_API_KEY'] : []), 'GEMINI_API_KEY'];
  const out: GeminiKeySlot[] = []; const seen = new Set<string>();
  for (const slot of names) {
    const key = secret(slot);
    if (!key || seen.has(key)) continue;
    seen.add(key); out.push({ slot, key });
  }
  return out;
}

/** A failed Gemini call. status null = network error or timeout. */
export class GeminiCallError extends Error {
  readonly status: number | null; readonly detail: string; readonly retryAfterSec?: number;
  constructor(status: number | null, detail = '', retryAfterSec?: number) { super(`gemini ${status ?? 'network'}`); this.status = status; this.detail = detail; this.retryAfterSec = retryAfterSec; }
}

/** True when the next key may succeed where this one failed. */
export function keyMayHelp(status: number | null, detail = '') {
  if (status === null || status === 429 || status >= 500 || status === 401 || status === 403) return true;
  // Google answers a bad or expired key with 400 API_KEY_INVALID, and a key whose project cannot use the API with FAILED_PRECONDITION.
  if (status === 400) return /API_KEY_INVALID|API key not valid|API key expired|FAILED_PRECONDITION|billing|PERMISSION_DENIED|RESOURCE_EXHAUSTED/iu.test(detail);
  return false;
}

// Per server instance: a key that just ran out of quota is tried after the others for a while. A speed bump, not a guarantee.
const resting = new Map<string, number>();
const REST_MS = 60_000;
export function restGeminiKey(slot: GeminiKeySlot, seconds?: number, now = Date.now()) {
  resting.set(slot.key, now + Math.min(10 * 60_000, Math.max(REST_MS, (seconds ?? 0) * 1000)));
  if (resting.size > 100) resting.clear();
}
export function geminiKeyResting(slot: GeminiKeySlot, now = Date.now()) { return (resting.get(slot.key) ?? 0) > now; }
export function resetGeminiKeyRest() { resting.clear(); }
/** The slots in order, with resting ones moved to the end (still tried, never dropped). */
export function geminiKeyOrder(slots: GeminiKeySlot[], now = Date.now()) {
  return [...slots.filter((slot) => !geminiKeyResting(slot, now)), ...slots.filter((slot) => geminiKeyResting(slot, now))];
}

export type KeyLog = (event: string, detail: { slot: string; status?: number | null }) => void;
const defaultLog: KeyLog = (event, detail) => console.warn(event, detail.slot, detail.status ?? '');

/**
 * Runs attempt() with each key in turn until one succeeds. attempt() throws GeminiCallError for a failed call; when keyMayHelp()
 * says so the next key is tried, otherwise the error is thrown at once. Any other error is thrown at once. When every key fails,
 * the last GeminiCallError is thrown. Logs only slot names and statuses.
 */
export async function withGeminiKeys<T>(slots: GeminiKeySlot[], attempt: (slot: GeminiKeySlot) => Promise<T>,
  options: { log?: KeyLog; now?: () => number } = {}): Promise<{ value: T; slot: GeminiKeySlot }> {
  const log = options.log ?? defaultLog; const now = options.now ?? Date.now;
  if (!slots.length) throw new GeminiCallError(503, 'not configured');
  let last: GeminiCallError | null = null;
  for (const slot of geminiKeyOrder(slots, now())) {
    try {
      const value = await attempt(slot);
      return { value, slot };
    } catch (error) {
      if (!(error instanceof GeminiCallError)) throw error;
      if (error.status === 429) restGeminiKey(slot, error.retryAfterSec, now());
      if (!keyMayHelp(error.status, error.detail)) throw error;
      log('gemini_key_failed', { slot: slot.slot, status: error.status });
      last = error;
    }
  }
  throw last ?? new GeminiCallError(503, 'not configured');
}
