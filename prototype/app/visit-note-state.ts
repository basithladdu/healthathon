import { beginSummaryRevision, releaseSummary, reviseSummary, type SummaryState } from './summary-state.ts';

// Keep a pasted note verbatim. No clinical facts or preferences are inferred.
export function prepareVisitNote<T extends SummaryState>(state: T, note: string): T {
  if (!note.trim()) return state;
  const notStated = 'Not stated in this conversation.';
  const withNote = reviseSummary(beginSummaryRevision(state), {
    draftSource: note.trim(),
    draftFields: { priorities: notStated, participants: notStated, topics: note.trim(), openQuestions: notStated, followUp: notStated },
  });
  return reviseSummary(withNote, {
    draftStatuses: { priorities: 'not-stated', participants: 'not-stated', topics: 'ready', openQuestions: 'not-stated', followUp: 'not-stated' },
    draftExcerpts: { priorities: '', participants: '', topics: note.trim(), openQuestions: '', followUp: '' },
  });
}

export function approveVisitNote<T extends SummaryState>(state: T, input: {
  treatingPhysician: boolean; physician: string; permission: string; reviewed: boolean; releasedAt: string;
  signatureName?: string;
}): T {
  if (!input.treatingPhysician || !input.reviewed) return state;
  if (input.signatureName !== undefined && input.signatureName.trim().toLowerCase() !== input.physician.trim().toLowerCase()) return state;
  const candidate = {
    ...state, draftPrepared: true, authorisation: input.permission, attested: true,
    verificationChecks: { source: true, ambiguity: true, inference: true, reviewDate: true },
  };
  const released = releaseSummary(candidate, { physician: input.physician, releasedAt: input.releasedAt,
    ...(input.signatureName ? { signature: { name: input.signatureName.trim(), signedAt: input.releasedAt, method: 'typed' as const } } : {}) });
  return released === candidate ? state : released;
}
