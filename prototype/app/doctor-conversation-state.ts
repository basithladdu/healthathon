import type { DraftFieldKey } from './summary-state';
import type { CareNoteKind } from './care-note-signing-state';

export type DoctorConversationEntry = {
  id: string;
  patientId: string;
  physician: string;
  note: string;
  recordedAt: string;
  audio?: File;
  recordingAgreed: boolean;
};

export type AssistedNote = { fields: Record<DraftFieldKey, string>; excerpts: Record<DraftFieldKey, string>; suggestedKind?: CareNoteKind };

export const CARE_NOTE_LABELS: Record<DraftFieldKey, string> = {
  priorities: 'What matters', participants: 'Who was there', topics: 'What we discussed',
  openQuestions: 'Still to discuss', followUp: 'Next steps',
};
