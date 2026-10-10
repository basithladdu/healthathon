import { eventFallsOn, type CareCheck, type CareEvent } from './care-calendar-state.ts';

export type MedicineTimeGroup = {
  date: string;
  time: string;
  medicines: CareEvent[];
};

function dateAfter(date: string, days: number): string {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

export function formatMedicineTime(time: string): string {
  const match = /^(\d{2}):(\d{2})$/.exec(time);
  if (!match) return time;
  const hour = Number(match[1]);
  const minute = match[2];
  if (hour > 23) return time;
  return `${hour % 12 || 12}:${minute} ${hour < 12 ? 'AM' : 'PM'}`;
}

export function medicineTimeGroups(events: readonly CareEvent[], patientId: string, date: string): MedicineTimeGroup[] {
  const byTime = new Map<string, CareEvent[]>();
  events
    .filter((event) => event.patientId === patientId && event.kind === 'Medicine' && eventFallsOn(event, date))
    .sort((first, second) => first.time.localeCompare(second.time) || first.title.localeCompare(second.title))
    .forEach((event) => byTime.set(event.time, [...(byTime.get(event.time) ?? []), event]));
  return [...byTime].map(([time, medicines]) => ({ date, time, medicines }));
}

export function nextMedicineTimeGroup(
  events: readonly CareEvent[],
  checks: readonly CareCheck[],
  patientId: string,
  today: string,
  currentTime: string,
  horizonDays = 180,
): MedicineTimeGroup | null {
  const safeCurrentTime = /^([01]\d|2[0-3]):[0-5]\d$/.test(currentTime) ? currentTime : '00:00';
  for (let offset = 0; offset <= horizonDays; offset += 1) {
    const date = dateAfter(today, offset);
    const groups = medicineTimeGroups(events, patientId, date);
    for (const group of groups) {
      if (offset === 0 && group.time < safeCurrentTime) continue;
      const medicines = group.medicines.filter((medicine) => !checks.some((check) => check.patientId === patientId
        && check.eventId === medicine.id && check.date === date));
      if (medicines.length) return { ...group, medicines };
    }
  }
  return null;
}
