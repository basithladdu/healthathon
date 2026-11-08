// Weight history on the patient profile, twin of CareWeightHistory in CarePatientProfile.kt: one weight per day (the latest
// entered that day wins), oldest first, with the change since the weight before it.
export type CareWeightEntry = { date: string; kg: number };

export const WEIGHT_MIN_KG = 2;
export const WEIGHT_MAX_KG = 300;

/** A valid entry: a real ISO date no later than today and a weight from 2 to 300 kg. */
export function validWeight(entry: CareWeightEntry, today: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(entry.date) && Number.isFinite(Date.parse(`${entry.date}T12:00:00Z`)) && entry.date <= today
    && Number.isFinite(entry.kg) && entry.kg >= WEIGHT_MIN_KG && entry.kg <= WEIGHT_MAX_KG;
}

/** Adds or replaces that day's weight, rounded to 0.1 kg, oldest first. An invalid entry leaves the list unchanged. */
export function addWeight(list: readonly CareWeightEntry[] | undefined, entry: CareWeightEntry, today: string): CareWeightEntry[] {
  const current = [...(list ?? [])];
  if (!validWeight(entry, today)) return current;
  const kg = Math.round(entry.kg * 10) / 10;
  return [...current.filter((item) => item.date !== entry.date), { date: entry.date, kg }].sort((a, b) => a.date.localeCompare(b.date));
}

export function removeWeight(list: readonly CareWeightEntry[] | undefined, date: string): CareWeightEntry[] {
  return (list ?? []).filter((item) => item.date !== date);
}

/** Change from the weight before the latest one; null with fewer than two weights. */
export function weightChange(list: readonly CareWeightEntry[] | undefined): number | null {
  const sorted = [...(list ?? [])].sort((a, b) => a.date.localeCompare(b.date));
  if (sorted.length < 2) return null;
  return Math.round((sorted[sorted.length - 1].kg - sorted[sorted.length - 2].kg) * 10) / 10;
}

export function weightTrend(list: readonly CareWeightEntry[] | undefined): 'up' | 'down' | 'steady' | null {
  const change = weightChange(list);
  if (change === null) return null;
  return change > 0 ? 'up' : change < 0 ? 'down' : 'steady';
}
