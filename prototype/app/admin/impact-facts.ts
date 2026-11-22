// Fixed facts for /admin → Impact, taken from files in the repo (no live data). tests/admin-impact.test.mjs
// recounts them from android/app/src/main/assets/india.json and app/care-i18n.ts so they cannot drift silently.
export const DIRECTORY_FACTS = {
  /** Palliative care centres in the directory bundled with the Android app (india.json). */
  centres: 1628,
  /** States and union territories those centres are in. */
  states: 36,
  /** Centres whose source service notes say oral morphine is available ("Morphine: Available" or "Yes"). */
  morphineListed: 371,
  sources: 'Pallium India and the Indian Association of Palliative Care directories (sources dated October 2026)',
} as const;

/** Languages the app and website offer (CARE_LANGS in app/care-i18n.ts, CareLang in the Android app). */
export const SUPPORTED_LANGUAGES = ['English', 'Hindi', 'Telugu', 'Urdu', 'Kannada'] as const;

/** One number on the Impact tab. total/last30 are null when the tile is unavailable (reason in `unavailable`). */
export type ImpactTile = { key: string; group: string; label: string; total: number | null; last30: number | null; note?: string; unavailable?: string };
