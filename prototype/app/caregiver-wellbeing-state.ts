// The family member's own wellbeing check (ZBI-4) and private journal. Mirrors Android CaregiverWellbeingRules.kt.
// The 4-item Zarit Burden Interview screen (Bedard et al. 2001): four questions, each 0 to 4, total 0 to 16.
// 8 or more suggests high burden. A screening aid, not a diagnosis. Kept in this browser only, per person.

export type WellbeingEntry = { day: string; score: number };
export type JournalNote = { at: number; text: string };

export const ZBI_ITEM_COUNT = 4;
export const ZBI_ITEM_MAX = 4;
export const ZBI_MAX_SCORE = ZBI_ITEM_COUNT * ZBI_ITEM_MAX;
/** ZBI-4: 8 or more is high burden. */
export const ZBI_HIGH_FROM = 8;
/** A jump of this many points since the previous check is a sharp rise, even below the high line. */
export const ZBI_SHARP_RISE = 4;
export const ZBI_KEEP = 12;
export const ZBI_TREND_SHOWN = 8;
export const JOURNAL_MAX_CHARS = 2000;

export const SUPPORT_GROUP_SWAYAM = 'https://swayamfoundation.co.in/support-group/caregiver-support/';
export const SUPPORT_GROUP_SOULUP = 'https://www.soulup.in/products/caregiving-support-group';
export const MAPS_QUERY = 'psychiatrist OR counsellor near me';
export const TELE_MANAS_NUMBER = '14416';
export const MAPS_URL = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(MAPS_QUERY)}`;
export const TELE_MANAS_HREF = `tel:${TELE_MANAS_NUMBER}`;
export const ZBI_SOURCE_EN = 'Based on the 4-item Zarit Burden Interview screening (Bédard et al., The Gerontologist, 2001). A screening, not a diagnosis.';
export const ZBI_SOURCE_HI = 'ज़ारिट बर्डन इंटरव्यू की 4-प्रश्न स्क्रीनिंग पर आधारित (Bédard et al., The Gerontologist, 2001)। यह जाँच है, निदान नहीं।';

export const ZBI_ANSWERS_EN = ['Never', 'Rarely', 'Sometimes', 'Quite often', 'Nearly always'] as const;
export const ZBI_ANSWERS_HI = ['कभी नहीं', 'शायद ही कभी', 'कभी-कभी', 'अक्सर', 'लगभग हमेशा'] as const;

export function validAnswer(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= ZBI_ITEM_MAX;
}

/** Total of the four answers, or null unless all four are answered with 0 to 4. */
export function zbiScore(answers: readonly (number | null | undefined)[]): number | null {
  if (answers.length !== ZBI_ITEM_COUNT || !answers.every(validAnswer)) return null;
  return (answers as number[]).reduce((sum, value) => sum + value, 0);
}

export function isHighBurden(score: number): boolean { return score >= ZBI_HIGH_FROM; }

const byDay = (a: WellbeingEntry, b: WellbeingEntry) => a.day.localeCompare(b.day);

/** Newest entry's change from the one before it, or null with fewer than two entries. */
export function wellbeingChange(history: readonly WellbeingEntry[]): number | null {
  const sorted = [...history].sort(byDay);
  if (sorted.length < 2) return null;
  return sorted[sorted.length - 1].score - sorted[sorted.length - 2].score;
}

export function risingSharply(history: readonly WellbeingEntry[]): boolean { return (wellbeingChange(history) ?? 0) >= ZBI_SHARP_RISE; }

/** Show the "You're carrying a lot" card when the latest score is high or has risen sharply. */
export function shouldFlag(history: readonly WellbeingEntry[]): boolean {
  const latest = [...history].sort(byDay).pop();
  if (!latest) return false;
  return isHighBurden(latest.score) || risingSharply(history);
}

export type WellbeingTrend = 'rising' | 'falling' | 'steady' | 'none';
export function wellbeingTrend(history: readonly WellbeingEntry[]): WellbeingTrend {
  const change = wellbeingChange(history);
  if (change === null) return 'none';
  return change >= 2 ? 'rising' : change <= -2 ? 'falling' : 'steady';
}

/** Add a check: one entry per day (a second check the same day replaces the first), oldest first, newest 12 kept. */
export function addCheck(history: readonly WellbeingEntry[], entry: WellbeingEntry): WellbeingEntry[] {
  const score = Math.min(ZBI_MAX_SCORE, Math.max(0, Math.trunc(entry.score)));
  return [...history.filter((item) => item.day !== entry.day), { day: entry.day, score }].sort(byDay).slice(-ZBI_KEEP);
}

/** The last 8 scores, oldest first, for the small trend. */
export function recentChecks(history: readonly WellbeingEntry[]): WellbeingEntry[] { return [...history].sort(byDay).slice(-ZBI_TREND_SHOWN); }

const DAY = /^\d{4}-\d{2}-\d{2}$/;
export function parseHistory(raw: unknown): WellbeingEntry[] {
  if (!Array.isArray(raw)) return [];
  const out: WellbeingEntry[] = [];
  for (const item of raw) {
    const row = item as Partial<WellbeingEntry> | null;
    if (!row || typeof row.day !== 'string' || !DAY.test(row.day) || !Number.isInteger(row.score) || (row.score as number) < 0 || (row.score as number) > ZBI_MAX_SCORE) continue;
    out.push({ day: row.day, score: row.score as number });
  }
  const unique = new Map(out.map((entry) => [entry.day, entry]));
  return [...unique.values()].sort(byDay).slice(-ZBI_KEEP);
}

export function parseJournal(raw: unknown): JournalNote[] {
  if (!Array.isArray(raw)) return [];
  const out: JournalNote[] = [];
  for (const item of raw) {
    const row = item as Partial<JournalNote> | null;
    if (!row || !Number.isFinite(row.at) || typeof row.text !== 'string' || !row.text.trim()) continue;
    out.push({ at: row.at as number, text: row.text });
  }
  return out.sort((a, b) => b.at - a.at);
}

/** Add a note; blank text is ignored. Newest first. */
export function addNote(notes: readonly JournalNote[], at: number, text: string): JournalNote[] {
  const clean = text.trim();
  if (!clean) return [...notes];
  return [{ at, text: clean.slice(0, JOURNAL_MAX_CHARS) }, ...notes].sort((a, b) => b.at - a.at);
}

export function wellbeingStoreKey(namespace: string, person: string): string { return `${namespace}|${person}`; }

type Store = Pick<Storage, 'getItem' | 'setItem'>;
const PREFIX = 'sahara_caregiver_wellbeing';
function read(store: Store, key: string, suffix: string): unknown {
  try { return JSON.parse(store.getItem(`${PREFIX}|${key}|${suffix}`) ?? 'null'); } catch { return null; }
}
function write(store: Store, key: string, suffix: string, value: unknown): void {
  try { store.setItem(`${PREFIX}|${key}|${suffix}`, JSON.stringify(value)); } catch { /* storage full or blocked: the check still shows this visit */ }
}
export function loadHistory(store: Store, key: string): WellbeingEntry[] { return parseHistory(read(store, key, 'zbi4')); }
export function saveHistory(store: Store, key: string, history: readonly WellbeingEntry[]): void { write(store, key, 'zbi4', history); }
export function loadJournal(store: Store, key: string): JournalNote[] { return parseJournal(read(store, key, 'journal')); }
export function saveJournal(store: Store, key: string, notes: readonly JournalNote[]): void { write(store, key, 'journal', notes); }
