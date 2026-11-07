// The 0-10 distress scale, the same question and scale as the NCCN Distress Thermometer (Android CareFamilyToday.kt / CareDistress.kt).
// A score of 4 or more suggests distress worth talking about with the care team. Kept in this browser only.

export const DISTRESS_SCORES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
/** NCCN: 4 or more suggests distress worth talking about with the care team. */
export const DISTRESS_TALK_FROM = 4;
export const NCCN_DISTRESS_SOURCE_EN = 'Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).';
export const NCCN_DISTRESS_SOURCE_HI = 'स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।';

export type DistressBand = 'none' | 'mild' | 'moderate' | 'severe';
export function distressBand(score: number): DistressBand {
  return score <= 0 ? 'none' : score < DISTRESS_TALK_FROM ? 'mild' : score < 8 ? 'moderate' : 'severe';
}
export function distressBandText(score: number, t: (en: string, hi: string) => string): string {
  switch (distressBand(score)) {
    case 'none': return t('No distress', 'कोई परेशानी नहीं');
    case 'mild': return t('Mild', 'थोड़ी');
    case 'moderate': return t('Moderate', 'मध्यम');
    default: return t('Severe', 'बहुत ज़्यादा');
  }
}

function mix(from: number, to: number, f: number): number { return Math.round(from + (to - from) * f); }
/** Thermometer colour from calm green (0) to rust (10). */
export function distressColour(score: number): string {
  const f = Math.min(10, Math.max(0, score)) / 10;
  return `rgb(${mix(0x3e, 0xb9, f)}, ${mix(0x8a, 0x47, f)}, ${mix(0x68, 0x2c, f)})`;
}

export function encodeDistress(day: string, score: number): string {
  return `${day}|${Math.min(10, Math.max(0, Math.trunc(score)))}`;
}
/** Today's saved score, or null when nothing was saved today (yesterday's answer does not count). */
export function decodeDistress(raw: string | null | undefined, today: string): number | null {
  const parts = (raw ?? '').split('|');
  if (parts.length !== 2 || parts[0] !== today) return null;
  const score = Number(parts[1]);
  return Number.isInteger(score) && score >= 0 && score <= 10 ? score : null;
}

type Store = Pick<Storage, 'getItem' | 'setItem'>;
export function loadDistress(store: Store, key: string, today: string): number | null {
  try { return decodeDistress(store.getItem(key), today); } catch { return null; }
}
export function saveDistress(store: Store, key: string, today: string, score: number): void {
  try { store.setItem(key, encodeDistress(today, score)); } catch { /* storage blocked: the score still shows this visit */ }
}
export const CAREGIVER_CHECK_KEY = (person: string) => `sahara_caregiver_check|${person}`;
export const PATIENT_DISTRESS_KEY = (person: string) => `sahara_distress_thermometer|${person}`;
