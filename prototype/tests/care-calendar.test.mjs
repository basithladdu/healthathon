import test from 'node:test';
import assert from 'node:assert/strict';
import { addCareEvent, calendarDays, eventFallsOn, reportFileError, setCareCheck } from '../app/care-calendar-state.ts';

const medicine = { id: 'medicine-a', patientId: 'patient-a', kind: 'Medicine', title: ' Example medicine ', instructions: ' As written in the existing prescription ', date: '2026-08-28', time: '08:00', repeatUntil: '2026-08-30', addedBy: 'Family A' };

test('medicine entries require an explicit time and prescription instructions, and valid repeat dates', () => {
  for (const change of [{ time: '' }, { time: '25:00' }, { instructions: ' ' }, { date: '2026-02-30' }, { repeatUntil: '2026-08-27' }]) {
    assert.deepEqual(addCareEvent([], { ...medicine, ...change }), []);
  }
  const items = addCareEvent([], medicine);
  assert.equal(items[0].title, 'Example medicine');
  assert.equal(items[0].instructions, 'As written in the existing prescription');
  assert.equal(eventFallsOn(items[0], '2026-08-29'), true);
  assert.equal(eventFallsOn(items[0], '2026-08-31'), false);
  assert.deepEqual(addCareEvent(items, medicine), items);
});

test('a taken mark applies only to one patient and one occurrence, and cannot pre-complete a future dose', () => {
  const items = [medicine, { ...medicine, id: 'medicine-b', patientId: 'patient-b' }];
  const stamp = '2026-08-28T09:00:00Z';
  assert.deepEqual(setCareCheck([], items, 'patient-b', 'medicine-a', '2026-08-28', 'B', stamp, '2026-08-28', true), []);
  assert.deepEqual(setCareCheck([], items, 'patient-a', 'medicine-a', '2026-08-29', 'A', stamp, '2026-08-28', true), []);
  const checks = setCareCheck([], items, 'patient-a', 'medicine-a', '2026-08-28', 'A', stamp, '2026-08-28', true);
  assert.equal(checks.length, 1);
  assert.equal(checks[0].date, '2026-08-28');
  assert.equal(checks[0].actor, 'A');
  assert.equal(setCareCheck(checks, items, 'patient-a', 'medicine-a', '2026-08-28', 'A', stamp, '2026-08-28', true).length, 1);
  assert.deepEqual(setCareCheck(checks, items, 'patient-a', 'medicine-a', '2026-08-28', 'A', stamp, '2026-08-28', false), []);
  assert.equal(checks.length, 1);
});

test('calendar weeks start Monday and preserve leap days and year boundaries', () => {
  const february = calendarDays('2028-02');
  assert.equal(february[0], '2028-01-31');
  assert.equal(february.includes('2028-02-29'), true);
  assert.equal(february.length % 7, 0);
  assert.equal(calendarDays('2027-01')[0], '2026-12-28');
  assert.deepEqual(calendarDays('2026-13'), []);
});

test('report history accepts only nonempty bounded PDFs and image files', () => {
  assert.equal(reportFileError({ type: 'application/pdf', size: 1024 }), null);
  assert.equal(reportFileError({ type: 'image/png', size: 1024 }), null);
  assert.equal(reportFileError({ type: 'text/html', size: 1024 }), 'type');
  assert.equal(reportFileError({ type: 'image/svg+xml', size: 1024 }), 'type');
  assert.equal(reportFileError({ type: 'image/jpeg', size: 0 }), 'size');
  assert.equal(reportFileError({ type: 'application/pdf', size: 11 * 1024 * 1024 }), 'size');
});
