import { isValidAppointmentDate } from './appointment-state.ts';

export const HOME_HELP_CATEGORIES = ['Transport', 'Home nursing', 'Medicines or equipment delivery'] as const;
const LEGACY_HOME_HELP_CATEGORIES = ['Home help or nursing', 'Equipment', 'Paperwork', 'Company', 'Caregiver break'] as const;
export const HOME_HELP_STATUSES = ['To arrange', 'Arranged', 'Done'] as const;
export type HomeHelpCategory = (typeof HOME_HELP_CATEGORIES)[number] | (typeof LEGACY_HOME_HELP_CATEGORIES)[number];
export type HomeHelpStatus = (typeof HOME_HELP_STATUSES)[number];
export type HomeHelpInput = { category: HomeHelpCategory; title: string; helper: string; phone: string; date: string; note: string };
export type HomeHelpEntry = HomeHelpInput & { id: string; patientId: string; status: HomeHelpStatus; author: string; editedBy?: string };

export function validateHomeHelpInput(input: HomeHelpInput): 'category' | 'title' | 'helper' | 'phone' | 'date' | 'note' | null {
  if (![...HOME_HELP_CATEGORIES, ...LEGACY_HOME_HELP_CATEGORIES].includes(input.category)) return 'category';
  if (!input.title.trim() || input.title.trim().length > 160) return 'title';
  if (input.helper.trim().length > 80) return 'helper';
  if (input.phone.trim() && (!/^\+?[\d\s().-]{6,30}$/.test(input.phone.trim()) || input.phone.replace(/\D/g, '').length < 6 || input.phone.replace(/\D/g, '').length > 15)) return 'phone';
  if (input.date && !isValidAppointmentDate(input.date)) return 'date';
  if (input.note.trim().length > 500) return 'note';
  return null;
}

function cleanHomeHelp(input: HomeHelpInput): HomeHelpInput {
  return { category: input.category, title: input.title.trim(), helper: input.helper.trim(), phone: input.phone.trim(), date: input.date, note: input.note.trim() };
}

export function addHomeHelpEntry(entries: HomeHelpEntry[], entry: HomeHelpEntry): HomeHelpEntry[] {
  if (!entry.id || !entry.patientId || !entry.author.trim() || validateHomeHelpInput(entry) || entries.some((item) => item.id === entry.id)) return entries;
  return [...entries, { ...cleanHomeHelp(entry), id: entry.id, patientId: entry.patientId, author: entry.author.trim(), status: 'To arrange' }];
}

export function updateHomeHelpEntry(entries: HomeHelpEntry[], patientId: string, entryId: string, input: HomeHelpInput, author: string): HomeHelpEntry[] {
  if (!author.trim() || validateHomeHelpInput(input) || !entries.some((entry) => entry.patientId === patientId && entry.id === entryId)) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === entryId ? { ...entry, ...cleanHomeHelp(input), editedBy: author.trim() } : entry);
}

export function setHomeHelpStatus(entries: HomeHelpEntry[], patientId: string, entryId: string, status: HomeHelpStatus): HomeHelpEntry[] {
  if (!HOME_HELP_STATUSES.includes(status) || !entries.some((entry) => entry.patientId === patientId && entry.id === entryId)) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === entryId ? { ...entry, status } : entry);
}

export function removeHomeHelpEntry(entries: HomeHelpEntry[], patientId: string, entryId: string): HomeHelpEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== entryId);
}

export function restoreHomeHelpEntry(entries: HomeHelpEntry[], patientId: string, removed: HomeHelpEntry): HomeHelpEntry[] {
  if (!removed.id || removed.patientId !== patientId || !removed.author.trim() || validateHomeHelpInput(removed)
    || !HOME_HELP_STATUSES.includes(removed.status) || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, { ...removed }];
}

export function selectHomeHelpEntries(entries: HomeHelpEntry[], patientId: string, status: HomeHelpStatus | 'All' = 'All'): HomeHelpEntry[] {
  return entries.filter((entry) => entry.patientId === patientId && (status === 'All' || entry.status === status))
    .sort((a, b) => HOME_HELP_STATUSES.indexOf(a.status) - HOME_HELP_STATUSES.indexOf(b.status) || (a.date || '9999').localeCompare(b.date || '9999'));
}

export function homeHelpText(entries: HomeHelpEntry[], patientId: string, today: string, hindi = false): string {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const statuses = { 'To arrange': 'इंतज़ाम बाकी है', Arranged: 'इंतज़ाम हो गया', Done: 'पूरा हो गया' };
  return [
    t('Saanthvana — Help at home', 'सांत्वना — घर पर मदद'),
    `${t('Saved on', 'कॉपी की तारीख')}: ${today}`,
    t('Arrangements recorded by the family. No bookings or messages are sent by this list.', 'परिवार ने ये इंतज़ाम दर्ज किए हैं। यह सूची कोई बुकिंग या संदेश नहीं भेजती।'),
    ...selectHomeHelpEntries(entries, patientId).map((entry) => [
      `${hindi ? statuses[entry.status] : entry.status} — ${entry.title}`,
      entry.date ? `${t('Day', 'दिन')}: ${entry.date}` : '',
      entry.helper ? `${t('Helping', 'मदद करने वाले')}: ${entry.helper}` : t('No helper added yet.', 'मदद करने वाले का नाम अभी नहीं जोड़ा है।'),
      entry.phone ? `${t('Phone', 'फ़ोन')}: ${entry.phone}` : '',
      entry.note,
      `${t('Added by', 'जोड़ा')}: ${entry.author}${entry.editedBy ? `; ${t('edited by', 'बदला')}: ${entry.editedBy}` : ''}`,
    ].filter(Boolean).join('\n')),
  ].join('\n\n');
}
