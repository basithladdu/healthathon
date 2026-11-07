import test from 'node:test';
import assert from 'node:assert/strict';
import { slotOfTime, slotOf, groupMedicines, filterMedicinesByName, medicineEntries, isAsNeededText, MEDICINE_SEARCH_ABOVE } from '../app/care-medicine-groups.ts';

const input = (id, name, times = [], asNeeded = false) => ({ id, name, times, asNeeded });

test('time of day boundaries match Android', () => {
  assert.equal(slotOfTime('04:59'), 'night');
  assert.equal(slotOfTime('05:00'), 'morning');
  assert.equal(slotOfTime('11:59'), 'morning');
  assert.equal(slotOfTime('12:00'), 'afternoon');
  assert.equal(slotOfTime('16:59'), 'afternoon');
  assert.equal(slotOfTime('17:00'), 'evening');
  assert.equal(slotOfTime('20:59'), 'evening');
  assert.equal(slotOfTime('21:00'), 'night');
  assert.equal(slotOfTime('25:00'), null);
  assert.equal(slotOfTime('xx'), null);
});

test('a medicine is listed once, under its earliest time', () => {
  const groups = groupMedicines([input('a', 'Paracetamol', ['21:00', '08:00', '14:00']), input('b', 'Senna', ['21:00'])]);
  assert.deepEqual(groups.map((g) => [g.slot, g.medicines.map((m) => m.id)]), [['morning', ['a']], ['night', ['b']]]);
  assert.equal(groups.flatMap((g) => g.medicines).length, 2);
});

test('as needed and other groups come last, in order, and empty groups are dropped', () => {
  const groups = groupMedicines([input('o', 'Weekly', []), input('s', 'SOS', [], true), input('e', 'Eve', ['18:00'])]);
  assert.deepEqual(groups.map((g) => g.slot), ['evening', 'as-needed', 'other']);
  assert.equal(slotOf(input('x', 'x', [], true)), 'as-needed');
});

test('sorted by earliest time then name inside a group', () => {
  const groups = groupMedicines([input('b', 'beta', ['08:00']), input('a', 'Alpha', ['08:00']), input('c', 'Early', ['06:30'])]);
  assert.deepEqual(groups[0].medicines.map((m) => m.id), ['c', 'a', 'b']);
});

test('search ignores case and spaces; blank returns everything', () => {
  const list = [input('a', 'Paracetamol'), input('b', 'Morphine')];
  assert.deepEqual(filterMedicinesByName(list, '  PARA ').map((m) => m.id), ['a']);
  assert.equal(filterMedicinesByName(list, '   ').length, 2);
  assert.equal(MEDICINE_SEARCH_ABOVE, 8);
});

test('dose events of one medicine merge into one entry', () => {
  const ev = (id, title, time, instructions = '1 tablet') => ({ id, title, time, instructions, patientId: 'p', kind: 'Medicine', date: '2026-10-10', repeatUntil: '', addedBy: 'x' });
  const entries = medicineEntries([ev('1', 'Paracetamol', '21:00'), ev('2', 'Paracetamol', '08:00'), ev('3', 'Tramadol', '10:00', 'SOS for pain')]);
  assert.equal(entries.length, 2);
  const para = entries.find((e) => e.name === 'Paracetamol');
  assert.deepEqual(para.times, ['08:00', '21:00']);
  assert.equal(para.events.length, 2);
  assert.equal(slotOf(entries.find((e) => e.name === 'Tramadol')), 'as-needed');
  assert.equal(isAsNeededText('take PRN'), true);
  assert.equal(isAsNeededText('after dinner'), false);
});
