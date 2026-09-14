import assert from 'node:assert/strict';
import test from 'node:test';
import {
  appendScheduledAppointmentForPatient,
  canMarkAppointmentAttended,
  canTransitionAppointmentStatus,
  isValidAppointmentDate,
  isValidAppointmentTime,
  updateAppointmentForPatient,
  validateAppointmentDateTime,
} from '../app/appointment-state.ts';

function appointment(overrides = {}) {
  return {
    id: 'APT-1001',
    hospitalId: 'PATIENT-A',
    date: '2026-08-28',
    time: '10:30',
    status: 'Scheduled',
    ...overrides,
  };
}

test('appointment date and time validation rejects malformed calendar and clock values', () => {
  assert.equal(isValidAppointmentDate('2026-02-28'), true);
  assert.equal(isValidAppointmentDate('2026-02-29'), false);
  assert.equal(isValidAppointmentDate('2024-02-29'), true);
  assert.equal(isValidAppointmentDate('2026-04-31'), false);
  assert.equal(isValidAppointmentDate('2026-8-08'), false);
  assert.equal(isValidAppointmentTime('00:00'), true);
  assert.equal(isValidAppointmentTime('23:59'), true);
  assert.equal(isValidAppointmentTime('24:00'), false);
  assert.equal(isValidAppointmentTime('9:30'), false);
  assert.equal(isValidAppointmentTime('12:60'), false);

  assert.equal(validateAppointmentDateTime('', '', '2026-08-28'), 'date-required');
  assert.equal(validateAppointmentDateTime('2026-08-29', '', '2026-08-28'), 'time-required');
  assert.equal(validateAppointmentDateTime('2026-02-29', '10:30', '2026-08-28'), 'date-invalid');
  assert.equal(validateAppointmentDateTime('2026-08-29', '24:00', '2026-08-28'), 'time-invalid');
  assert.equal(validateAppointmentDateTime('2026-08-27', '10:30', '2026-08-28'), 'date-before-minimum');
  assert.equal(validateAppointmentDateTime('2026-08-29', '10:30', '2026-08-28'), null);
});

test('rescheduling retains the original appointment as history and creates one scheduled successor', () => {
  const original = appointment({ note: 'Bring family questions' });
  const next = appointment({
    id: 'APT-1002',
    date: '2026-09-04',
    time: '12:00',
  });
  const historical = {
    ...original,
    status: 'Rescheduled',
    outcome: 'Rescheduled to 04 Sep 2026 12:00',
  };

  const result = updateAppointmentForPatient([original], 'PATIENT-A', historical, next);

  assert.equal(result.changed, true);
  assert.equal(result.appointments.length, 2);
  assert.deepEqual(result.appointments[0], historical);
  assert.equal(result.appointments[0].status, 'Rescheduled');
  assert.equal(result.appointments[0].note, 'Bring family questions');
  assert.equal(result.appointments[1].id, 'APT-1002');
  assert.equal(result.appointments[1].status, 'Scheduled');
  assert.equal(result.appointments[1].hospitalId, 'PATIENT-A');
  assert.equal(original.status, 'Scheduled');
  assert.equal(
    updateAppointmentForPatient([original], 'PATIENT-A', historical).changed,
    false,
  );
});

test('appointment mutations reject cross patient records without changing either patient', () => {
  const own = appointment();
  const other = appointment({ id: 'APT-2001', hospitalId: 'PATIENT-B' });
  const appointments = [own, other];

  const update = updateAppointmentForPatient(
    appointments,
    'PATIENT-A',
    { ...other, status: 'Cancelled' },
  );
  const append = appendScheduledAppointmentForPatient(
    appointments,
    'PATIENT-A',
    { ...other, id: 'APT-2002' },
  );

  assert.equal(update.changed, false);
  assert.deepEqual(update.appointments, appointments);
  assert.equal(append.changed, false);
  assert.deepEqual(append.appointments, appointments);
  assert.equal(appointments[1].status, 'Scheduled');
});

test('terminal statuses cannot be changed into a new appointment outcome, and future visits cannot be marked attended', () => {
  for (const status of ['Attended', 'Rescheduled', 'Cancelled', 'Missed']) {
    assert.equal(canTransitionAppointmentStatus(status, 'Cancelled'), status === 'Cancelled');
  }

  const terminal = appointment({ status: 'Rescheduled' });
  const result = updateAppointmentForPatient(
    [terminal],
    'PATIENT-A',
    { ...terminal, status: 'Cancelled' },
  );
  assert.equal(result.changed, false);
  assert.equal(canMarkAppointmentAttended(appointment({ date: '2026-08-29' }), '2026-08-28'), false);
  assert.equal(canMarkAppointmentAttended(appointment({ date: '2026-08-28' }), '2026-08-28'), true);
});
