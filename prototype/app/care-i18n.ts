// Languages offered in the header dropdown. English and Hindi strings live inline as t(en, hi);
// other languages look the English string up in their dictionary and fall back to English.
// Owner of this file: the language work (header dropdown + dictionaries).
export type CareLang = 'en' | 'hi' | 'te' | 'ur' | 'kn';

export const CARE_LANGS: Array<{ code: CareLang; label: string; dir: 'ltr' | 'rtl' }> = [
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'hi', label: 'हिन्दी', dir: 'ltr' },
  { code: 'te', label: 'తెలుగు', dir: 'ltr' },
  { code: 'ur', label: 'اردو', dir: 'rtl' },
  { code: 'kn', label: 'ಕನ್ನಡ', dir: 'ltr' },
];

const DICTIONARIES: Partial<Record<CareLang, Record<string, string>>> = {};

export function registerCareDictionary(lang: CareLang, entries: Record<string, string>) {
  DICTIONARIES[lang] = { ...(DICTIONARIES[lang] ?? {}), ...entries };
}

export function careText(lang: CareLang, en: string, hi?: string): string {
  if (lang === 'en') return en;
  if (lang === 'hi') return hi ?? DICTIONARIES.hi?.[en] ?? en;
  return DICTIONARIES[lang]?.[en] ?? en;
}

export function careDir(lang: CareLang): 'ltr' | 'rtl' {
  return CARE_LANGS.find((l) => l.code === lang)?.dir ?? 'ltr';
}
