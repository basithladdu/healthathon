// Languages offered in the header dropdown. English and Hindi strings live inline as t(en, hi);
// other languages look the English string up in their dictionary and fall back to English.
// Owner of this file: the language work (header dropdown + dictionaries).
// Dictionaries live in app/i18n/<code>.ts and are loaded on demand (see loadCareDictionary).
export type CareLang = 'en' | 'hi' | 'te' | 'ur' | 'kn';

export const CARE_LANGS: Array<{ code: CareLang; label: string; english: string; dir: 'ltr' | 'rtl'; locale: string }> = [
  { code: 'en', label: 'English', english: 'English', dir: 'ltr', locale: 'en-GB' },
  { code: 'hi', label: 'हिन्दी', english: 'Hindi', dir: 'ltr', locale: 'hi-IN' },
  { code: 'te', label: 'తెలుగు', english: 'Telugu', dir: 'ltr', locale: 'te-IN' },
  { code: 'ur', label: 'اردو', english: 'Urdu', dir: 'rtl', locale: 'ur-IN' },
  { code: 'kn', label: 'ಕನ್ನಡ', english: 'Kannada', dir: 'ltr', locale: 'kn-IN' },
];

export const CARE_LANG_STORAGE_KEY = 'sahara-care-lang';

const DICTIONARIES: Partial<Record<CareLang, Record<string, string>>> = {};
// Keys with {0}, {1}… placeholders match English text that was built from a template literal.
const PATTERNS: Partial<Record<CareLang, Array<{ test: RegExp; text: string }>>> = {};
const LOOKUPS: Partial<Record<CareLang, Map<string, string>>> = {};

export function isCareLang(value: unknown): value is CareLang {
  return typeof value === 'string' && CARE_LANGS.some((item) => item.code === value);
}

/** Accepts the old `hindi` boolean as well as a language code. */
export function careLangOf(value: boolean | CareLang | undefined | null): CareLang {
  if (value === true) return 'hi';
  return isCareLang(value) ? value : 'en';
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function registerCareDictionary(lang: CareLang, entries: Record<string, string>) {
  DICTIONARIES[lang] = { ...(DICTIONARIES[lang] ?? {}), ...entries };
  const patterns = PATTERNS[lang] ?? [];
  for (const [key, text] of Object.entries(entries)) {
    if (!/\{\d+\}/.test(key) || !text) continue;
    const source = key.split(/(\{\d+\})/).map((part) => /^\{\d+\}$/.test(part) ? '([\\s\\S]*?)' : escapeRegExp(part)).join('');
    patterns.push({ test: new RegExp(`^${source}$`), text });
  }
  // Longer (more specific) patterns first.
  PATTERNS[lang] = patterns.sort((a, b) => b.test.source.length - a.test.source.length);
  LOOKUPS[lang] = new Map();
}

export function hasCareDictionary(lang: CareLang) {
  return lang === 'en' || lang === 'hi' || Boolean(DICTIONARIES[lang]);
}

function lookup(lang: CareLang, en: string): string | undefined {
  const exact = DICTIONARIES[lang]?.[en];
  if (exact) return exact;
  const patterns = PATTERNS[lang];
  if (!patterns?.length) return undefined;
  const cache = LOOKUPS[lang] ?? (LOOKUPS[lang] = new Map());
  const cached = cache.get(en);
  if (cached !== undefined) return cached || undefined;
  for (const { test, text } of patterns) {
    const match = test.exec(en);
    if (!match) continue;
    const result = text.replace(/\{(\d+)\}/g, (_, index: string) => match[Number(index) + 1] ?? '');
    cache.set(en, result);
    return result;
  }
  cache.set(en, '');
  return undefined;
}

export function careText(lang: CareLang, en: string, hi?: string): string {
  if (lang === 'en') return en;
  if (lang === 'hi') return hi ?? lookup('hi', en) ?? en;
  return lookup(lang, en) ?? en;
}

export function careDir(lang: CareLang): 'ltr' | 'rtl' {
  return CARE_LANGS.find((l) => l.code === lang)?.dir ?? 'ltr';
}

/** Locale for dates and numbers. English keeps the caller's choice (en-GB or en-IN). */
export function careLocale(lang: CareLang, english = 'en-GB'): string {
  if (lang === 'en') return english;
  return CARE_LANGS.find((l) => l.code === lang)?.locale ?? english;
}

const LOADERS: Partial<Record<CareLang, () => Promise<unknown>>> = {
  te: () => import('./i18n/te.ts'),
  ur: () => import('./i18n/ur.ts'),
  kn: () => import('./i18n/kn.ts'),
};
const LOADING: Partial<Record<CareLang, Promise<void>>> = {};

/** Loads a language dictionary once. Resolves immediately for English and Hindi. */
export function loadCareDictionary(lang: CareLang): Promise<void> {
  if (hasCareDictionary(lang)) return Promise.resolve();
  const loader = LOADERS[lang];
  if (!loader) return Promise.resolve();
  return LOADING[lang] ?? (LOADING[lang] = loader().then(() => undefined, () => { delete LOADING[lang]; }));
}

/** Reads the saved language: localStorage first, then the old per-tab viewer flag. */
export function readSavedCareLang(): CareLang | null {
  try {
    const saved = localStorage.getItem(CARE_LANG_STORAGE_KEY) ?? sessionStorage.getItem(CARE_LANG_STORAGE_KEY);
    if (isCareLang(saved)) return saved;
  } catch { /* Storage can be unavailable. */ }
  try {
    const viewer = JSON.parse(sessionStorage.getItem('saanthvana-care-viewer') ?? 'null');
    if (viewer && isCareLang(viewer.lang)) return viewer.lang;
    if (viewer && viewer.hindi === true) return 'hi';
  } catch { /* Storage can be unavailable. */ }
  return null;
}

export function saveCareLang(lang: CareLang) {
  try { localStorage.setItem(CARE_LANG_STORAGE_KEY, lang); } catch { /* Storage can be unavailable. */ }
  try { sessionStorage.setItem(CARE_LANG_STORAGE_KEY, lang); } catch { /* Storage can be unavailable. */ }
}

/** Sets <html lang> and <html dir>. */
export function applyCareLangToDocument(lang: CareLang) {
  if (typeof document === 'undefined') return;
  document.documentElement.lang = lang;
  document.documentElement.dir = careDir(lang);
}
