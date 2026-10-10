import test from 'node:test';
import assert from 'node:assert/strict';
import { formatMedicineTime, medicineTimeGroups, nextMedicineTimeGroup } from '../app/care-medicine-schedule.ts';

const event = (id, title, time, change = {}) => ({
  id, title, time, patientId: 'patient-a', kind: 'Medicine', date: '2026-10-10', repeatUntil: '2026-10-12',
  instructions: `${title} instructions`, addedBy: 'Meera', ...change,
});

test('groups active medicines into ordered clickable times', () => {
  const events = [event('c', 'Third', '20:00'), event('b', 'Beta', '08:00'), event('a', 'Alpha', '08:00'), event('x', 'Other patient', '07:00', { patientId: 'patient-b' })];
  const groups = medicineTimeGroups(events, 'patient-a', '2026-10-10');
  assert.deepEqual(groups.map((group) => [group.time, group.medicines.map((medicine) => medicine.title)]), [
    ['08:00', ['Alpha', 'Beta']],
    ['20:00', ['Third']],
  ]);
  assert.equal(formatMedicineTime('08:00'), '8:00 AM');
  assert.equal(formatMedicineTime('20:15'), '8:15 PM');
});

test('finds only the nearest upcoming untaken medicine group', () => {
  const events = [event('morning', 'Morning', '08:00'), event('evening-a', 'Evening A', '20:00'), event('evening-b', 'Evening B', '20:00')];
  const checks = [{ eventId: 'evening-a', patientId: 'patient-a', date: '2026-10-10', actor: 'Meera', checkedAt: '2026-10-10T10:00:00Z' }];
  const next = nextMedicineTimeGroup(events, checks, 'patient-a', '2026-10-10', '09:00');
  assert.equal(next?.date, '2026-10-10');
  assert.equal(next?.time, '20:00');
  assert.deepEqual(next?.medicines.map((medicine) => medicine.title), ['Evening B']);
});

test('rolls to tomorrow after the last time today and respects course dates', () => {
  const events = [event('morning', 'Morning', '08:00'), event('ended', 'Ended', '07:00', { repeatUntil: '2026-10-10' })];
  const next = nextMedicineTimeGroup(events, [], 'patient-a', '2026-10-10', '22:00');
  assert.equal(next?.date, '2026-10-11');
  assert.equal(next?.time, '08:00');
  assert.deepEqual(next?.medicines.map((medicine) => medicine.title), ['Morning']);
});
