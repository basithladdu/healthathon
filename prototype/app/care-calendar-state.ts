import { isValidAppointmentDate, isValidAppointmentTime } from './appointment-state.ts';

export const CARE_EVENT_KINDS = ['Medicine', 'Appointment', 'Test', 'Procedure', 'Follow-up'] as const;
export type CareEventKind = (typeof CARE_EVENT_KINDS)[number];
export type CareEvent = {
  id: string; patientId: string; kind: CareEventKind; title: string;
  date: string; time: string; instructions: string; repeatUntil: string; addedBy: string;
};
export type CareCheck = { eventId: string; patientId: string; date: string; actor: string; checkedAt: string };
export type CareReport = { id: string; patientId: string; date: string; addedBy: string; file: File };

export function eventFallsOn(item: CareEvent, date: string): boolean {
  return isValidAppointmentDate(date) && (item.date === date || Boolean(item.repeatUntil && date >= item.date && date <= item.repeatUntil));
}

export function addCareEvent(items: CareEvent[], item: CareEvent): CareEvent[] {
  if (!item.id || !item.patientId || !item.title.trim() || !item.addedBy.trim()
    || !CARE_EVENT_KINDS.includes(item.kind) || !isValidAppointmentDate(item.date)
    || (item.time && !isValidAppointmentTime(item.time))
    || (item.kind === 'Medicine' && (!item.time || !item.instructions.trim()))
    || (item.repeatUntil && (!isValidAppointmentDate(item.repeatUntil) || item.repeatUntil < item.date))
    || items.some((existing) => existing.id === item.id)) return items;
  return [...items, { ...item, title: item.title.trim(), instructions: item.instructions.trim(), addedBy: item.addedBy.trim() }];
}

export function updateCareEvent(items: CareEvent[], patientId: string, updated: CareEvent): CareEvent[] {
  const previous = items.find((item) => item.id === updated.id && item.patientId === patientId);
  if (!previous || updated.patientId !== patientId) return items;
  const validated = addCareEvent([], { ...updated, addedBy: previous.addedBy })[0];
  if (!validated) return items;
  return items.map((item) => item.id === previous.id && item.patientId === patientId ? validated : item);
}

export function careChecksAfterEventUpdate(checks: CareCheck[], previous: CareEvent, updated: CareEvent): CareCheck[] {
  if (previous.id !== updated.id || previous.patientId !== updated.patientId || !addCareEvent([], updated).length) return checks;
  const wordingOrTimeChanged = previous.kind !== updated.kind || previous.title.trim() !== updated.title.trim()
    || previous.time !== updated.time || previous.instructions.trim() !== updated.instructions.trim();
  return checks.filter((check) => check.patientId !== updated.patientId || check.eventId !== updated.id
    || (!wordingOrTimeChanged && eventFallsOn(updated, check.date)));
}

export function setCareCheck(checks: CareCheck[], items: CareEvent[], patientId: string, eventId: string, date: string, actor: string, checkedAt: string, today: string, complete: boolean): CareCheck[] {
  const item = items.find((event) => event.patientId === patientId && event.id === eventId);
  if (!item || !actor.trim() || !eventFallsOn(item, date) || !isValidAppointmentDate(today) || date > today || !Number.isFinite(Date.parse(checkedAt))) return checks;
  const remaining = checks.filter((check) => !(check.patientId === patientId && check.eventId === eventId && check.date === date));
  return complete ? [...remaining, { eventId, patientId, date, actor: actor.trim(), checkedAt }] : remaining;
}

export function calendarDays(month: string): string[] {
  if (!isValidAppointmentDate(`${month}-01`)) return [];
  const first = new Date(`${month}-01T12:00:00Z`);
  const offset = (first.getUTCDay() + 6) % 7;
  const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  return Array.from({ length: Math.ceil((offset + days) / 7) * 7 }, (_, index) => {
    const date = new Date(first); date.setUTCDate(1 - offset + index);
    return date.toISOString().slice(0, 10);
  });
}

export function calendarWeekDays(selected: string): string[] {
  if (!isValidAppointmentDate(selected)) return [];
  const date = new Date(`${selected}T12:00:00Z`);
  const mondayOffset = (date.getUTCDay() + 6) % 7;
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(date);
    day.setUTCDate(date.getUTCDate() - mondayOffset + index);
    return day.toISOString().slice(0, 10);
  });
}

export function reportFileError(file: Pick<File, 'type' | 'size'>): 'type' | 'size' | null {
  if (!['application/pdf', 'image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return 'type';
  return file.size <= 0 || file.size > 10 * 1024 * 1024 ? 'size' : null;
}
