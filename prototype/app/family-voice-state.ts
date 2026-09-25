import { isValidAppointmentDate } from './appointment-state.ts';

export type VoiceJournalEntry = {
  id: string;
  patientId: string;
  date: string;
  title: string;
  thought: string;
  audio: File | null;
  durationSeconds: number | null;
  createdBy: string;
  updatedBy: string;
};

export type VoiceJournalDraft = Pick<VoiceJournalEntry, 'title' | 'thought' | 'audio' | 'durationSeconds'>;

export function validVoiceJournalDraft(draft: VoiceJournalDraft): boolean {
  if (draft.title.length > 160 || draft.thought.length > 6000 || (!draft.audio && !draft.thought.trim())) return false;
  if (!draft.audio) return draft.durationSeconds === null;
  return draft.audio.size > 0 && draft.audio.size <= 20 * 1024 * 1024
    && typeof draft.durationSeconds === 'number' && Number.isFinite(draft.durationSeconds)
    && draft.durationSeconds > 0 && draft.durationSeconds <= 90;
}

export function addVoiceJournalEntry(entries: VoiceJournalEntry[], patientId: string, author: string, today: string, id: string, draft: VoiceJournalDraft): VoiceJournalEntry[] {
  if (!patientId || !author.trim() || !id || !isValidAppointmentDate(today) || !validVoiceJournalDraft(draft)
    || entries.some((entry) => entry.id === id)) return entries;
  return [...entries, { ...draft, title: draft.title.trim(), id, patientId, date: today, createdBy: author, updatedBy: author }];
}

export function editVoiceJournalText(entries: VoiceJournalEntry[], patientId: string, entryId: string, author: string, title: string, thought: string): VoiceJournalEntry[] {
  if (!author.trim()) return entries;
  const current = entries.find((entry) => entry.patientId === patientId && entry.id === entryId);
  if (!current || !validVoiceJournalDraft({ ...current, title, thought })) return entries;
  return entries.map((entry) => entry === current ? { ...entry, title: title.trim(), thought, updatedBy: author } : entry);
}

export function removeVoiceJournalEntry(entries: VoiceJournalEntry[], patientId: string, entryId: string): VoiceJournalEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== entryId);
}

export function restoreVoiceJournalEntry(entries: VoiceJournalEntry[], patientId: string, entry: VoiceJournalEntry, index: number): VoiceJournalEntry[] {
  if (entry.patientId !== patientId || entries.some((item) => item.id === entry.id)) return entries;
  const restored = [...entries];
  restored.splice(Math.max(0, Math.min(index, entries.length)), 0, { ...entry });
  return restored;
}

export function selectVoiceJournalEntries(entries: VoiceJournalEntry[], patientId: string): VoiceJournalEntry[] {
  return [...entries].reverse().filter((entry) => entry.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
}

export function voiceJournalEntryText(entry: VoiceJournalEntry): string {
  return [
    `SAANTHVANA — PERSONAL JOURNAL\nPatient ID: ${entry.patientId}\nDate: ${entry.date}`,
    entry.title, entry.thought,
    entry.audio ? `Audio file: ${entry.audio.name}\nDownload the original audio separately.` : '',
    `Written by: ${entry.createdBy}${entry.updatedBy !== entry.createdBy ? `\nEdited by: ${entry.updatedBy}` : ''}`,
  ].filter(Boolean).join('\n\n');
}
