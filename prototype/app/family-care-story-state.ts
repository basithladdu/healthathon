import { isValidAppointmentDate } from './appointment-state.ts';
import type { CareDocumentTreatmentSource } from './care-document-treatment-state.ts';

export const CARE_STORY_KINDS = ['Visit', 'Treatment', 'Treatment change', 'Report', 'Milestone', 'Past history'] as const;
export type CareStoryKind = (typeof CARE_STORY_KINDS)[number];
export type CareStoryFilter = CareStoryKind | 'All';

export type CareStoryDraft = {
  date: string;
  kind: CareStoryKind;
  title: string;
  details: string;
  changeReason: string;
  source: string;
  sourceAuthor: string;
};

export type CareStoryEntry = CareStoryDraft & {
  id: string;
  patientId: string;
  createdBy: string;
  updatedBy: string;
  documentSource?: CareDocumentTreatmentSource;
};

function cleanDraft(draft: CareStoryDraft): CareStoryDraft | null {
  if (!isValidAppointmentDate(draft.date) || !CARE_STORY_KINDS.includes(draft.kind)) return null;
  const clean = {
    date: draft.date,
    kind: draft.kind,
    title: draft.title.trim(),
    details: draft.details.trim(),
    changeReason: draft.changeReason.trim(),
    source: draft.source.trim(),
    sourceAuthor: draft.sourceAuthor.trim(),
  };
  if (!clean.title || clean.title.length > 160 || clean.details.length > 4000
    || clean.changeReason.length > 2000 || clean.source.length > 240 || clean.sourceAuthor.length > 100) return null;
  return clean;
}

export function addCareStoryEntry(entries: CareStoryEntry[], patientId: string, author: string, id: string, draft: CareStoryDraft): CareStoryEntry[] {
  const clean = cleanDraft(draft);
  if (!clean || !patientId.trim() || !author.trim() || !id.trim() || entries.some((entry) => entry.id === id)) return entries;
  return [...entries, { ...clean, id, patientId, createdBy: author.trim(), updatedBy: author.trim() }];
}

export function updateCareStoryEntry(entries: CareStoryEntry[], patientId: string, id: string, author: string, draft: CareStoryDraft): CareStoryEntry[] {
  const clean = cleanDraft(draft);
  if (!clean || !author.trim() || !entries.some((entry) => entry.patientId === patientId && entry.id === id)) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === id ? { ...entry, ...clean, updatedBy: author.trim() } : entry);
}

export function removeCareStoryEntry(entries: CareStoryEntry[], patientId: string, id: string): CareStoryEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== id);
}

export function restoreCareStoryEntry(entries: CareStoryEntry[], patientId: string, removed: CareStoryEntry): CareStoryEntry[] {
  if (removed.patientId !== patientId || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, removed];
}

export function selectCareStoryEntries(entries: CareStoryEntry[], patientId: string, filter: CareStoryFilter = 'All'): CareStoryEntry[] {
  return entries.filter((entry) => entry.patientId === patientId && (filter === 'All' || entry.kind === filter))
    .sort((a, b) => b.date.localeCompare(a.date));
}

export function exportCareStory(entries: CareStoryEntry[], patientId: string): string {
  const story = selectCareStoryEntries(entries, patientId);
  return [
    'SAANTHVANA — YOUR CARE STORY',
    `Patient: ${patientId}`,
    'Family-added notes. Sources are recorded as entered; these notes are not a doctor-approved care note.',
    ...story.map((entry) => [
      `${entry.date} · ${entry.kind} · ${entry.title}`,
      entry.details,
      entry.documentSource ? `Quoted from the document: ${entry.documentSource.quote}\n${entry.documentSource.page ? `Page ${entry.documentSource.page} · ` : ''}Text line ${entry.documentSource.line}\nSource checked by: ${entry.documentSource.recordedBy}` : '',
      entry.changeReason ? `What changed / reason as documented: ${entry.changeReason}` : '',
      entry.source ? `Source: ${entry.source}` : '',
      entry.sourceAuthor ? `Source written by: ${entry.sourceAuthor}` : '',
      `Added by: ${entry.createdBy}`,
      entry.updatedBy !== entry.createdBy ? `Last edited by: ${entry.updatedBy}` : '',
    ].filter(Boolean).join('\n')),
  ].join('\n\n');
}
