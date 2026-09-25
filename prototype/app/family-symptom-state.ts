import { isValidAppointmentDate } from './appointment-state.ts';

export const SYMPTOM_SEVERITIES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
const LEGACY_SYMPTOM_SEVERITIES = ['Mild', 'Moderate', 'Severe'] as const;
export type SymptomSeverity = (typeof SYMPTOM_SEVERITIES)[number] | (typeof LEGACY_SYMPTOM_SEVERITIES)[number];
export type SymptomInput = {
  symptom: string;
  severity: SymptomSeverity;
  date: string;
  startedOn: string;
  note: string;
};
export type SymptomEntry = SymptomInput & {
  id: string;
  patientId: string;
  author: string;
  editedBy?: string;
};
export type SymptomRange = 'all' | 'week' | 'today';
export type SymptomInputError = 'symptom' | 'severity' | 'date' | 'startedOn' | 'note';

export function validateSymptomInput(input: SymptomInput, today: string): SymptomInputError | null {
  if (!input.symptom.trim() || input.symptom.trim().length > 80) return 'symptom';
  if (typeof input.severity === 'number'
    ? !Number.isInteger(input.severity) || input.severity < 0 || input.severity > 10
    : !LEGACY_SYMPTOM_SEVERITIES.includes(input.severity)) return 'severity';
  if (!isValidAppointmentDate(today) || !isValidAppointmentDate(input.date) || input.date > today) return 'date';
  if (input.startedOn && (!isValidAppointmentDate(input.startedOn) || input.startedOn > input.date)) return 'startedOn';
  if (input.note.trim().length > 500) return 'note';
  return null;
}

function cleanInput(input: SymptomInput): SymptomInput {
  return { symptom: input.symptom.trim(), severity: input.severity, date: input.date, startedOn: input.startedOn, note: input.note.trim() };
}

export function addSymptomEntry(entries: SymptomEntry[], entry: SymptomEntry, today: string): SymptomEntry[] {
  if (!entry.id || !entry.patientId || !entry.author.trim() || validateSymptomInput(entry, today)
    || entries.some((item) => item.id === entry.id)) return entries;
  return [...entries, { ...cleanInput(entry), id: entry.id, patientId: entry.patientId, author: entry.author.trim() }];
}

export function updateSymptomEntry(entries: SymptomEntry[], patientId: string, entryId: string, input: SymptomInput, author: string, today: string): SymptomEntry[] {
  if (!author.trim() || validateSymptomInput(input, today)
    || !entries.some((entry) => entry.patientId === patientId && entry.id === entryId)) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === entryId
    ? { ...entry, ...cleanInput(input), editedBy: author.trim() }
    : entry);
}

export function removeSymptomEntry(entries: SymptomEntry[], patientId: string, entryId: string): SymptomEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== entryId);
}

export function restoreSymptomEntry(entries: SymptomEntry[], patientId: string, removed: SymptomEntry, today: string): SymptomEntry[] {
  if (removed.patientId !== patientId || !removed.id || !removed.author.trim() || validateSymptomInput(removed, today)
    || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, { ...removed }];
}

export function selectSymptomEntries(entries: SymptomEntry[], patientId: string, today: string, range: SymptomRange = 'all', symptom = ''): SymptomEntry[] {
  const start = new Date(`${today}T12:00:00Z`);
  if (range !== 'all' && !isValidAppointmentDate(today)) return [];
  if (range === 'week') start.setUTCDate(start.getUTCDate() - 6);
  const earliest = range === 'all' ? '' : start.toISOString().slice(0, 10);
  return entries.filter((entry) => entry.patientId === patientId && (!symptom || entry.symptom === symptom)
    && (range === 'all' || (entry.date >= earliest && entry.date <= today)))
    .sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id));
}

export function symptomSeverityText(value: SymptomSeverity, hindi = false): string {
  if (typeof value === 'number') return `${value}/10`;
  const labels = { Mild: 'हल्का', Moderate: 'मध्यम', Severe: 'बहुत ज़्यादा' };
  return hindi ? labels[value] : value;
}

export function symptomDiaryText(entries: SymptomEntry[], patientId: string, today: string, hindi = false): string {
  const t = (en: string, hi: string) => hindi ? hi : en;
  return [
    t('Saanthvana — How I have been feeling', 'सांत्वना — मेरी तबीयत के नोट'),
    `${t('Saved on', 'कॉपी की तारीख')}: ${today}`,
    t('Patient/family observations. Not sent to the care team.', 'मरीज़ या परिवार के लिखे नोट। देखभाल टीम को नहीं भेजे गए हैं।'),
    ...selectSymptomEntries(entries, patientId, today).map((entry) => [
      `${entry.date} — ${entry.symptom} — ${symptomSeverityText(entry.severity, hindi)}`,
      entry.startedOn ? `${t('Started', 'शुरू हुआ')}: ${entry.startedOn}` : '',
      entry.note,
      `${t('Written by', 'लिखा')}: ${entry.author}${entry.editedBy ? `; ${t('edited by', 'बदला')}: ${entry.editedBy}` : ''}`,
    ].filter(Boolean).join('\n')),
  ].join('\n\n');
}
