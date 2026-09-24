import { isValidAppointmentDate } from './appointment-state.ts';

export const COMFORT_FEELINGS = ['Peaceful', 'In pain', 'Tired', 'Happy'] as const;
export type ComfortFeeling = (typeof COMFORT_FEELINGS)[number];
export type ComfortEntry = { id: string; patientId: string; author: string; date: string; feeling: ComfortFeeling; createdAt: string };

export function addComfortEntry(entries: ComfortEntry[], entry: ComfortEntry): ComfortEntry[] {
  if (!entry.id.trim() || !entry.patientId.trim() || !entry.author.trim() || !isValidAppointmentDate(entry.date)
    || !COMFORT_FEELINGS.includes(entry.feeling) || !Number.isFinite(Date.parse(entry.createdAt))
    || entries.some((item) => item.id === entry.id)) return entries;
  return [...entries, { ...entry, author: entry.author.trim() }];
}

export function selectComfortEntries(entries: ComfortEntry[], patientId: string, author: string): ComfortEntry[] {
  return entries.filter((entry) => entry.patientId === patientId && entry.author === author.trim())
    .sort((a, b) => b.date.localeCompare(a.date) || Date.parse(b.createdAt) - Date.parse(a.createdAt));
}

export function latestComfortEntryToday(entries: ComfortEntry[], patientId: string, author: string, today: string): ComfortEntry | null {
  return selectComfortEntries(entries, patientId, author).find((entry) => entry.date === today) ?? null;
}

export function removeComfortEntry(entries: ComfortEntry[], patientId: string, author: string, id: string): ComfortEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.author !== author.trim() || entry.id !== id);
}

export function restoreComfortEntry(entries: ComfortEntry[], patientId: string, author: string, removed: ComfortEntry): ComfortEntry[] {
  if (removed.patientId !== patientId || removed.author !== author.trim() || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, removed];
}

export function exportComfortEntries(entries: ComfortEntry[], patientId: string, author: string): string {
  return [
    'SAATHI — MY COMFORT SPACE',
    `Patient: ${patientId}\nWritten by: ${author.trim()}`,
    'Personal check-ins. These entries were not sent to a doctor or family member.',
    ...selectComfortEntries(entries, patientId, author).map((entry) => `${entry.date} — ${entry.feeling}\nSaved at: ${entry.createdAt}\nWritten by: ${entry.author}`),
  ].join('\n\n');
}
