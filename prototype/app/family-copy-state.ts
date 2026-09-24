import { isValidAppointmentDate } from './appointment-state.ts';
import type { SummaryRelease } from './summary-state.ts';

export type CareCopyEntry = {
  id: string;
  patientId: string;
  recipient: string;
  version: number;
  givenOn: string;
  locationNote: string;
  addedBy: string;
  updatedBy: string;
};

export type CareCopyDraft = Pick<CareCopyEntry, 'recipient' | 'version' | 'givenOn' | 'locationNote'>;
export type CareCopyStatus = 'latest' | 'older' | 'unavailable';
export type CareCopyRecipient = { key: string; recipient: string; lastEntry: CareCopyEntry; history: CareCopyEntry[]; status: CareCopyStatus };

const COPY_FIELDS = [
  ['priorities', 'What matters to the patient'], ['participants', 'Who was there'], ['topics', 'What was discussed'],
  ['openQuestions', 'Still to discuss'], ['followUp', 'Next steps'],
] as const;

export function approvedPatientCopies(releases: readonly SummaryRelease[], patientId: string): SummaryRelease[] {
  return releases.filter((release) => release.patientId === patientId && Number.isInteger(release.number) && release.number > 0
    && release.physician.trim() && isValidAppointmentDate(release.releasedAt.slice(0, 10)) && Number.isFinite(Date.parse(release.releasedAt)))
    .sort((a, b) => b.number - a.number);
}

export function careCopyStatus(releases: readonly SummaryRelease[], patientId: string, version: number): CareCopyStatus {
  const copies = approvedPatientCopies(releases, patientId);
  if (!copies.some((copy) => copy.number === version)) return 'unavailable';
  return copies[0]?.number === version ? 'latest' : 'older';
}

export function validateCareCopyDraft(draft: CareCopyDraft, releases: readonly SummaryRelease[], patientId: string, today: string): 'recipient' | 'version' | 'date' | 'location' | null {
  if (!draft.recipient.trim() || draft.recipient.length > 160) return 'recipient';
  const release = approvedPatientCopies(releases, patientId).find((copy) => copy.number === draft.version);
  if (!release) return 'version';
  if (!isValidAppointmentDate(today) || !isValidAppointmentDate(draft.givenOn) || draft.givenOn > today || draft.givenOn < release.releasedAt.slice(0, 10)) return 'date';
  if (draft.locationNote.length > 1500) return 'location';
  return null;
}

export function addCareCopyEntry(entries: CareCopyEntry[], patientId: string, author: string, id: string, draft: CareCopyDraft, releases: readonly SummaryRelease[], today: string): CareCopyEntry[] {
  if (!patientId || !author.trim() || !id || entries.some((entry) => entry.id === id) || validateCareCopyDraft(draft, releases, patientId, today)) return entries;
  return [...entries, { ...draft, recipient: draft.recipient.trim(), id, patientId, addedBy: author, updatedBy: author }];
}

export function updateCareCopyEntry(entries: CareCopyEntry[], patientId: string, entryId: string, author: string, draft: CareCopyDraft, releases: readonly SummaryRelease[], today: string): CareCopyEntry[] {
  if (!author.trim() || validateCareCopyDraft(draft, releases, patientId, today)) return entries;
  const existing = entries.find((entry) => entry.patientId === patientId && entry.id === entryId);
  if (!existing) return entries;
  return entries.map((entry) => entry === existing ? { ...entry, ...draft, recipient: draft.recipient.trim(), updatedBy: author } : entry);
}

export function removeCareCopyEntry(entries: CareCopyEntry[], patientId: string, entryId: string): CareCopyEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== entryId);
}

export function restoreCareCopyEntry(entries: CareCopyEntry[], patientId: string, entry: CareCopyEntry, index: number): CareCopyEntry[] {
  if (entry.patientId !== patientId || entries.some((item) => item.id === entry.id)) return entries;
  const restored = [...entries];
  restored.splice(Math.max(0, Math.min(index, entries.length)), 0, { ...entry });
  return restored;
}

export function groupCareCopyEntries(entries: CareCopyEntry[], releases: readonly SummaryRelease[], patientId: string): CareCopyRecipient[] {
  const groups = new Map<string, CareCopyEntry[]>();
  [...entries].reverse().filter((entry) => entry.patientId === patientId).sort((a, b) => b.givenOn.localeCompare(a.givenOn)).forEach((entry) => {
    const key = entry.recipient.trim().toLocaleLowerCase();
    groups.set(key, [...(groups.get(key) ?? []), entry]);
  });
  return Array.from(groups, ([key, history]) => ({ key, recipient: history[0].recipient, lastEntry: history[0], history, status: careCopyStatus(releases, patientId, history[0].version) }));
}

export function reviewedCopyText(releases: readonly SummaryRelease[], patientId: string, patientName: string, version: number): string | null {
  const release = approvedPatientCopies(releases, patientId).find((copy) => copy.number === version);
  if (!release?.fields) return null;
  return [
    `SAATHI — DOCTOR-REVIEWED CARE NOTE\nPatient: ${patientName}\nPatient ID: ${patientId}`,
    `Version: ${release.number}\nDoctor: ${release.physician}\nReleased: ${release.releasedAt}`,
    ...COPY_FIELDS.map(([key, label]) => `${label}\n${release.fields![key]}`),
    'A record of the reviewed conversation, not a prescription or a legal directive.',
  ].join('\n\n');
}

export function careCopyLogText(entries: CareCopyEntry[], releases: readonly SummaryRelease[], patientId: string, patientName: string): string {
  const latest = approvedPatientCopies(releases, patientId)[0];
  const groups = groupCareCopyEntries(entries, releases, patientId);
  return [
    `SAATHI — FAMILY COPY LOG\nPatient: ${patientName}\nPatient ID: ${patientId}`,
    'These are family-entered handover notes. The app does not check delivery, reading or who still holds a copy. Removing an entry does not retrieve or revoke a copy.',
    latest ? `Latest approved note here: version ${latest.number}\nDoctor: ${latest.physician}\nReleased: ${latest.releasedAt}` : 'No approved note is available here.',
    ...groups.map((group) => [
      group.recipient,
      `Last recorded handover: version ${group.lastEntry.version} on ${group.lastEntry.givenOn}\n${group.status === 'latest' ? 'Matches the latest approved version here' : group.status === 'older' ? 'An older version was last recorded' : 'This version is not available here'}`,
      ...group.history.map((entry) => `Gave version ${entry.version} on ${entry.givenOn}${entry.locationNote ? `\n${entry.locationNote}` : ''}\nRecorded by: ${entry.addedBy}${entry.updatedBy !== entry.addedBy ? `; corrected by: ${entry.updatedBy}` : ''}`),
    ].join('\n\n')),
  ].join('\n\n');
}
