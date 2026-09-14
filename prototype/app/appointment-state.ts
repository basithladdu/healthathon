export type AppointmentStatus = 'Scheduled' | 'Attended' | 'Rescheduled' | 'Cancelled' | 'Missed';

export type AppointmentRecord = {
  id: string;
  hospitalId: string;
  date: string;
  time: string;
  status: AppointmentStatus;
};

export type AppointmentDateTimeError =
  | 'date-required'
  | 'time-required'
  | 'date-invalid'
  | 'time-invalid'
  | 'date-before-minimum';

export type AppointmentMutationResult<T> = {
  changed: boolean;
  appointments: T[];
};

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const CLOCK_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

function isLeapYear(year: number): boolean {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) return isLeapYear(year) ? 29 : 28;
  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

export function isValidAppointmentDate(value: string): boolean {
  const match = ISO_DATE.exec(value);
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  return year >= 1 && month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth(year, month);
}

export function isValidAppointmentTime(value: string): boolean {
  return CLOCK_TIME.test(value);
}

export function validateAppointmentDateTime(
  date: string,
  time: string,
  minimumDate: string,
): AppointmentDateTimeError | null {
  if (!date && !time) return 'date-required';
  if (!date) return 'date-required';
  if (!time) return 'time-required';
  if (!isValidAppointmentDate(date)) return 'date-invalid';
  if (!isValidAppointmentTime(time)) return 'time-invalid';
  if (!isValidAppointmentDate(minimumDate) || date < minimumDate) return 'date-before-minimum';
  return null;
}

export function canTransitionAppointmentStatus(
  from: AppointmentStatus,
  to: AppointmentStatus,
): boolean {
  if (from === to) return true;
  return from === 'Scheduled' && ['Attended', 'Rescheduled', 'Cancelled', 'Missed'].includes(to);
}

export function canMarkAppointmentAttended(
  appointment: Pick<AppointmentRecord, 'status' | 'date'>,
  today: string,
): boolean {
  return (
    appointment.status === 'Scheduled' &&
    isValidAppointmentDate(appointment.date) &&
    isValidAppointmentDate(today) &&
    appointment.date <= today
  );
}

export function nextAppointmentId(appointments: readonly Pick<AppointmentRecord, 'id'>[]): string {
  const max = appointments.reduce((highest, appointment) => {
    const number = Number.parseInt(appointment.id.replace('APT-', ''), 10);
    return Number.isFinite(number) && number > highest ? number : highest;
  }, 1000);
  return `APT-${max + 1}`;
}

export function appendScheduledAppointmentForPatient<T extends AppointmentRecord>(
  appointments: readonly T[],
  patientId: string,
  appointment: T,
): AppointmentMutationResult<T> {
  if (
    !patientId ||
    appointment.hospitalId !== patientId ||
    appointment.status !== 'Scheduled' ||
    !appointment.id ||
    appointments.some((existing) => existing.id === appointment.id)
  ) {
    return { changed: false, appointments: [...appointments] };
  }
  return { changed: true, appointments: [...appointments, appointment] };
}

export function updateAppointmentForPatient<T extends AppointmentRecord>(
  appointments: readonly T[],
  patientId: string,
  next: T,
  extra?: T,
): AppointmentMutationResult<T> {
  const targetIndexes = appointments
    .map((appointment, index) => ({ appointment, index }))
    .filter(({ appointment }) => appointment.id === next.id && appointment.hospitalId === patientId);
  const source = targetIndexes.length === 1 ? targetIndexes[0].appointment : undefined;

  if (
    !patientId ||
    !source ||
    next.hospitalId !== patientId ||
    !canTransitionAppointmentStatus(source.status, next.status) ||
    (next.status === 'Rescheduled' && !extra)
  ) {
    return { changed: false, appointments: [...appointments] };
  }

  if (extra) {
    const extraIdTaken = appointments.some((appointment) => appointment.id === extra.id);
    if (
      source.status !== 'Scheduled' ||
      next.status !== 'Rescheduled' ||
      extra.status !== 'Scheduled' ||
      extra.hospitalId !== patientId ||
      !extra.id ||
      extra.id === next.id ||
      extraIdTaken
    ) {
      return { changed: false, appointments: [...appointments] };
    }
  }

  const updated = [...appointments];
  updated[targetIndexes[0].index] = next;
  if (extra) updated.push(extra);
  return { changed: true, appointments: updated };
}
