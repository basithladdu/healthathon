import { beginSummaryRevision, releaseSummary, reviseSummary, type DraftFieldKey, type DraftFieldStatus, type SummaryRelease, type SummaryState } from './summary-state.ts';
import { CARE_NOTE_LABELS } from './doctor-conversation-state.ts';
import type { CareNoteKind } from './care-note-signing-state.ts';

const CARRIED_FIELDS: readonly DraftFieldKey[] = ['priorities', 'openQuestions', 'followUp'];

// Keep a pasted note verbatim. No clinical facts or preferences are inferred.
export function prepareVisitNote<T extends SummaryState>(state: T, note: string, options: { carryForward?: boolean } = {}): T {
  if (!note.trim()) return state;
  const notStated = 'Not stated in this conversation.';
  const previous = options.carryForward ? state.releases.filter((release) => release.patientId === state.patientId && release.signature?.method === 'typed').reduce<SummaryRelease | null>((latest, release) => !latest || release.number > latest.number ? release : latest, null) : null;
  const fields: Record<DraftFieldKey, string> = { priorities: notStated, participants: notStated, topics: note.trim(), openQuestions: notStated, followUp: notStated };
  const statuses: Record<DraftFieldKey, DraftFieldStatus> = { priorities: 'not-stated', participants: 'not-stated', topics: 'ready', openQuestions: 'not-stated', followUp: 'not-stated' };
  const excerpts: Record<DraftFieldKey, string> = { priorities: '', participants: '', topics: note.trim(), openQuestions: '', followUp: '' };
  const previousText = previous?.fields
    ? (Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => `${CARE_NOTE_LABELS[key]}\n${previous.fields![key]}`).join('\n\n')
    : previous?.source;
  const source = previous && previousText ? `Previous signed note · Version ${previous.number}\n${previousText}\n\nCurrent conversation\n${note.trim()}` : note.trim();
  if (previous?.fields && previousText) for (const key of CARRIED_FIELDS) {
    const value = previous.fields[key];
    if (!value.trim() || value === notStated) continue;
    fields[key] = value;
    statuses[key] = 'clarify';
    excerpts[key] = `${CARE_NOTE_LABELS[key]}\n${value}`;
  }
  const fresh = {
    ...beginSummaryRevision(state), draftPriorVersion: previous && previousText ? previous.number : undefined,
    draftIsNewConversation: true, authorisation: 'Pending patient review', attested: false,
    verificationChecks: { source: false, ambiguity: false, inference: false, reviewDate: false },
  } as T;
  const withNote = reviseSummary(fresh, {
    draftSource: source, draftFields: fields,
  });
  return reviseSummary(withNote, {
    draftStatuses: statuses, draftExcerpts: excerpts,
  });
}

function confirmCarriedFields<T extends SummaryState>(state: T): T {
  const previous = state.releases.find((release) => release.patientId === state.patientId && release.number === state.draftPriorVersion && release.signature?.method === 'typed');
  if (!previous?.fields || !state.draftSource.startsWith(`Previous signed note · Version ${previous.number}\n`)) return state;
  const statuses = { ...state.draftStatuses };
  for (const key of CARRIED_FIELDS) {
    const priorExcerpt = `${CARE_NOTE_LABELS[key]}\n${previous.fields[key]}`;
    if (statuses[key] === 'clarify' && state.draftFields[key] === previous.fields[key] && state.draftExcerpts[key] === priorExcerpt && state.draftSource.includes(priorExcerpt)) statuses[key] = 'ready';
  }
  return { ...state, draftStatuses: statuses } as T;
}

export function approveVisitNote<T extends SummaryState>(state: T, input: {
  treatingPhysician: boolean; physician: string; permission: string; reviewed: boolean; releasedAt: string;
  signatureName?: string; noteKind?: CareNoteKind;
}): T {
  if (!input.treatingPhysician || !input.reviewed) return state;
  if (input.signatureName !== undefined && input.signatureName.trim().toLowerCase() !== input.physician.trim().toLowerCase()) return state;
  const candidate = {
    ...confirmCarriedFields(state), draftPrepared: true, authorisation: input.permission, attested: true,
    verificationChecks: { source: true, ambiguity: true, inference: true, reviewDate: true },
  };
  const released = releaseSummary(candidate, { physician: input.physician, releasedAt: input.releasedAt, noteKind: input.noteKind,
    ...(input.signatureName ? { signature: { name: input.signatureName.trim(), signedAt: input.releasedAt, method: 'typed' as const } } : {}) });
  return released === candidate ? state : released;
}
