import { isValidAppointmentDate } from './appointment-state.ts';
import { CARE_CENTRES, DIRECTORY_CHECKED_ON } from './care-directory-data.ts';

export type SupportContactNote = {
  id: string;
  date: string;
  person: string;
  reply: string;
  addedBy: string;
  updatedBy: string;
};

export type SupportPlace = {
  id: string;
  patientId: string;
  centreId: string;
  addedBy: string;
  notes: SupportContactNote[];
  nextStep: string;
  followUpOn: string;
  followUpDoneBy: string | null;
};

export type SupportContactDraft = Pick<SupportContactNote, 'date' | 'person' | 'reply'>;
export type RemovedSupportNote = { patientId: string; placeId: string; note: SupportContactNote; index: number };

export function orderedSupportNotes(place: SupportPlace): SupportContactNote[] {
  return [...place.notes].reverse().sort((a, b) => b.date.localeCompare(a.date));
}

export function validateSupportContactDraft(draft: SupportContactDraft, today: string): 'date' | 'person' | 'reply' | null {
  if (!isValidAppointmentDate(draft.date) || !isValidAppointmentDate(today) || draft.date > today) return 'date';
  if (draft.person.length > 100) return 'person';
  if (!draft.reply.trim() || draft.reply.length > 3000) return 'reply';
  return null;
}

export function validSupportFollowUp(nextStep: string, followUpOn: string): boolean {
  return nextStep.length <= 500 && (!followUpOn || (Boolean(nextStep.trim()) && isValidAppointmentDate(followUpOn)));
}

export function addSupportPlace(entries: SupportPlace[], patientId: string, centreId: string, author: string, id: string): SupportPlace[] {
  if (!patientId || !id || !author.trim() || !CARE_CENTRES.some((centre) => centre.id === centreId)
    || entries.some((entry) => entry.id === id || (entry.patientId === patientId && entry.centreId === centreId))) return entries;
  return [...entries, { id, patientId, centreId, addedBy: author, notes: [], nextStep: '', followUpOn: '', followUpDoneBy: null }];
}

function changePlace(entries: SupportPlace[], patientId: string, placeId: string, update: (place: SupportPlace) => SupportPlace): SupportPlace[] {
  const index = entries.findIndex((entry) => entry.patientId === patientId && entry.id === placeId);
  if (index < 0) return entries;
  const updated = update(entries[index]);
  if (updated === entries[index]) return entries;
  return entries.map((entry, position) => position === index ? updated : entry);
}

export function addSupportContactNote(entries: SupportPlace[], patientId: string, placeId: string, author: string, today: string, id: string, draft: SupportContactDraft): SupportPlace[] {
  if (!author.trim() || !id || validateSupportContactDraft(draft, today)) return entries;
  return changePlace(entries, patientId, placeId, (place) => place.notes.some((note) => note.id === id) ? place : {
    ...place, notes: [...place.notes, { ...draft, person: draft.person.trim(), id, addedBy: author, updatedBy: author }],
  });
}

export function updateSupportContactNote(entries: SupportPlace[], patientId: string, placeId: string, noteId: string, author: string, today: string, draft: SupportContactDraft): SupportPlace[] {
  if (!author.trim() || validateSupportContactDraft(draft, today)) return entries;
  return changePlace(entries, patientId, placeId, (place) => !place.notes.some((note) => note.id === noteId) ? place : {
    ...place, notes: place.notes.map((note) => note.id === noteId ? { ...note, ...draft, person: draft.person.trim(), updatedBy: author } : note),
  });
}

export function removeSupportContactNote(entries: SupportPlace[], patientId: string, placeId: string, noteId: string): SupportPlace[] {
  return changePlace(entries, patientId, placeId, (place) => !place.notes.some((note) => note.id === noteId) ? place : {
    ...place, notes: place.notes.filter((note) => note.id !== noteId),
  });
}

export function restoreSupportContactNote(entries: SupportPlace[], patientId: string, removed: RemovedSupportNote): SupportPlace[] {
  if (removed.patientId !== patientId) return entries;
  return changePlace(entries, patientId, removed.placeId, (place) => {
    if (place.notes.some((note) => note.id === removed.note.id)) return place;
    const notes = [...place.notes];
    notes.splice(Math.max(0, Math.min(removed.index, notes.length)), 0, { ...removed.note });
    return { ...place, notes };
  });
}

export function setSupportFollowUp(entries: SupportPlace[], patientId: string, placeId: string, nextStep: string, followUpOn: string): SupportPlace[] {
  if (!validSupportFollowUp(nextStep, followUpOn)) return entries;
  return changePlace(entries, patientId, placeId, (place) => place.nextStep === nextStep && place.followUpOn === followUpOn ? place : {
    ...place, nextStep, followUpOn, followUpDoneBy: null,
  });
}

export function completeSupportFollowUp(entries: SupportPlace[], patientId: string, placeId: string, author: string, complete: boolean): SupportPlace[] {
  if (!author.trim()) return entries;
  return changePlace(entries, patientId, placeId, (place) => !place.nextStep.trim() ? place : { ...place, followUpDoneBy: complete ? author : null });
}

export function removeSupportPlace(entries: SupportPlace[], patientId: string, placeId: string): SupportPlace[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== placeId);
}

export function restoreSupportPlace(entries: SupportPlace[], patientId: string, place: SupportPlace, index: number): SupportPlace[] {
  if (place.patientId !== patientId || !CARE_CENTRES.some((centre) => centre.id === place.centreId)
    || entries.some((entry) => entry.id === place.id || (entry.patientId === patientId && entry.centreId === place.centreId))) return entries;
  const restored = [...entries];
  restored.splice(Math.max(0, Math.min(index, entries.length)), 0, { ...place, notes: place.notes.map((note) => ({ ...note })) });
  return restored;
}

export function supportPlacesText(entries: SupportPlace[], patientId: string): string {
  const places = entries.filter((entry) => entry.patientId === patientId);
  return [
    `SAANTHVANA — PLACES I’VE CHECKED\nPatient ID: ${patientId}`,
    `Public directory checked: ${DIRECTORY_CHECKED_ON}. Call notes are written by the family; they do not confirm current services, medicine stock or bookings.`,
    ...places.map((place) => {
      const centre = CARE_CENTRES.find((item) => item.id === place.centreId);
      return [
        centre ? `${centre.name}\n${centre.city}, ${centre.state}\n${centre.address}\nPhone: ${centre.phone}${centre.phoneNote ? ` (${centre.phoneNote})` : ''}\nDirectory: ${centre.directoryUrl}` : 'Original listing is no longer in this directory.',
        `Saved by: ${place.addedBy}`,
        place.notes.length ? `FAMILY CALL NOTES\n${orderedSupportNotes(place).map((note) => `${note.date}${note.person ? ` — spoke to ${note.person}` : ''}\n${note.reply}\nAdded by: ${note.addedBy}${note.updatedBy !== note.addedBy ? `; edited by: ${note.updatedBy}` : ''}`).join('\n\n')}` : 'No call note added.',
        place.nextStep.trim() ? `NEXT STEP\n${place.nextStep}${place.followUpOn ? `\nDate: ${place.followUpOn}` : ''}\n${place.followUpDoneBy ? `Marked done by: ${place.followUpDoneBy}` : 'Still to do'}` : 'No follow-up added.',
      ].join('\n\n');
    }),
  ].join('\n\n');
}
