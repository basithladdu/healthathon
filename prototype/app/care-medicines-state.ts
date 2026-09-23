import { addCareEvent, type CareEvent } from './care-calendar-state.ts';

export type MedicinePrescription = {
  reportId?: string;
  sourceName: string;
  sourceText: string;
  checkedBy: string;
  checkedAt: string;
};
export type MedicineCareEvent = CareEvent & { prescription: MedicinePrescription };
export type MedicineDraft = {
  title: string; instructions: string; time: string; date: string; lastDay: string;
  reportId: string; sourceName: string; sourceText: string; checked: boolean;
};

export function medicinePrescription(item: CareEvent): MedicinePrescription | null {
  const value = (item as Partial<MedicineCareEvent>).prescription;
  if (item.kind !== 'Medicine' || !value || typeof value.sourceName !== 'string' || typeof value.sourceText !== 'string'
    || typeof value.checkedBy !== 'string' || typeof value.checkedAt !== 'string') return null;
  return value;
}

/** A clock already written in the source; never infer a frequency or time from a dose. */
export function literalPrescriptionTime(line: string): string | null {
  const matches = [...line.matchAll(/\b(\d{1,2}):([0-5]\d)\s*(a\.?m\.?|p\.?m\.?)?(?!\w)/giu)];
  if (matches.length !== 1) return null;
  const match = matches[0];
  let hour = Number(match[1]);
  const suffix = match[3]?.replaceAll('.', '').toLowerCase();
  if (suffix) {
    if (hour < 1 || hour > 12) return null;
    hour = hour % 12 + (suffix === 'pm' ? 12 : 0);
  } else if (hour > 23) return null;
  return `${String(hour).padStart(2, '0')}:${match[2]}`;
}

export function prescriptionLines(text: string): string[] {
  return [...new Set(text.split(/\r?\n/u).map((line) => line.trim()).filter((line) => line.length > 0 && line.length <= 400))];
}

export function makeMedicineEvent(draft: MedicineDraft, context: {
  id: string; patientId: string; author: string; checkedAt: string; reportIds: readonly string[];
}, previous?: CareEvent): MedicineCareEvent | null {
  if (!draft.checked || !draft.sourceName.trim() || !draft.lastDay
    || (draft.reportId ? !context.reportIds.includes(draft.reportId) : !draft.sourceText.trim())
    || (previous && (previous.patientId !== context.patientId || previous.kind !== 'Medicine' || previous.id !== context.id))
    || !Number.isFinite(Date.parse(context.checkedAt))) return null;
  const event: MedicineCareEvent = {
    ...previous,
    id: context.id, patientId: context.patientId, kind: 'Medicine',
    title: draft.title, instructions: draft.instructions, time: draft.time, date: draft.date,
    repeatUntil: draft.lastDay === draft.date ? '' : draft.lastDay,
    addedBy: previous?.addedBy ?? context.author,
    prescription: {
      ...(draft.reportId ? { reportId: draft.reportId } : {}),
      sourceName: draft.sourceName.trim(), sourceText: draft.sourceText,
      checkedBy: context.author, checkedAt: context.checkedAt,
    },
  };
  if (!context.author.trim() || !addCareEvent([], event).length) return null;
  return { ...event, title: event.title.trim(), instructions: event.instructions.trim() };
}
