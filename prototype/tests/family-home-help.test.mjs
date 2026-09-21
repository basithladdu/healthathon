import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateHomeHelpInput, addHomeHelpEntry, updateHomeHelpEntry, setHomeHelpStatus,
  removeHomeHelpEntry, restoreHomeHelpEntry, selectHomeHelpEntries, homeHelpText,
} from '../app/family-home-help-state.ts';

const entry = { id: 'one', patientId: 'patient-a', author: 'Kavya', category: 'Transport', title: ' Arrange a ride ', helper: ' Asha ', phone: '+91 90000 00000', date: '2026-09-22', status: 'To arrange', note: ' At 10 am ' };
const other = { ...entry, id: 'two', patientId: 'patient-b', author: 'Other family', note: 'Another family private note' };

test('needs can be added with optional details empty and reject bad dates, contact values and unbounded text', () => {
  assert.equal(validateHomeHelpInput({ ...entry, helper: '', phone: '', date: '', note: '' }), null);
  assert.equal(validateHomeHelpInput({ ...entry, date: '2026-02-30' }), 'date');
  assert.equal(validateHomeHelpInput({ ...entry, date: '2024-02-29' }), null);
  assert.equal(validateHomeHelpInput({ ...entry, phone: 'javascript:alert(1)' }), 'phone');
  assert.equal(validateHomeHelpInput({ ...entry, category: 'Prescribe equipment' }), 'category');
  assert.equal(validateHomeHelpInput({ ...entry, title: ' ' }), 'title');
  assert.equal(validateHomeHelpInput({ ...entry, note: 'a'.repeat(501) }), 'note');
  const entries = addHomeHelpEntry([], { ...entry, status: 'Done' });
  assert.equal(entries[0].title, 'Arrange a ride');
  assert.equal(entries[0].status, 'To arrange');
  assert.equal(entries[0].helper, 'Asha');
  assert.strictEqual(addHomeHelpEntry(entries, entry), entries);
});

test('edit and complete/reopen flow preserve patient identity and other families', () => {
  const entries = [entry, other];
  assert.strictEqual(updateHomeHelpEntry(entries, 'patient-b', 'one', { ...entry, title: 'Wrong patient' }, 'Elsewhere'), entries);
  const updated = updateHomeHelpEntry(entries, 'patient-a', 'one', { ...entry, id: 'fake', patientId: 'patient-b', status: 'Done', title: ' Taxi arranged ' }, 'Meera');
  assert.equal(updated[0].title, 'Taxi arranged');
  assert.equal(updated[0].id, 'one');
  assert.equal(updated[0].patientId, 'patient-a');
  assert.equal(updated[0].status, 'To arrange');
  assert.equal(updated[0].author, 'Kavya');
  assert.equal(updated[0].editedBy, 'Meera');
  assert.strictEqual(updated[1], other);
  const arranged = setHomeHelpStatus(updated, 'patient-a', 'one', 'Arranged');
  const done = setHomeHelpStatus(arranged, 'patient-a', 'one', 'Done');
  const reopened = setHomeHelpStatus(done, 'patient-a', 'one', 'To arrange');
  assert.equal(arranged[0].status, 'Arranged');
  assert.equal(done[0].status, 'Done');
  assert.equal(reopened[0].status, 'To arrange');
  assert.equal(entries[0].title, ' Arrange a ride ');
  assert.strictEqual(setHomeHelpStatus(entries, 'patient-b', 'one', 'Done'), entries);
  assert.strictEqual(setHomeHelpStatus(entries, 'patient-a', 'one', 'Booked by hospital'), entries);
});

test('removal and repeated undo preserve exact progress without crossing patients or duplicating entries', () => {
  const done = { ...entry, status: 'Done', editedBy: 'Meera' };
  const entries = [done, other];
  assert.deepEqual(removeHomeHelpEntry(entries, 'patient-b', 'one'), entries);
  const remaining = removeHomeHelpEntry(entries, 'patient-a', 'one');
  assert.deepEqual(remaining, [other]);
  assert.strictEqual(restoreHomeHelpEntry(remaining, 'patient-b', done), remaining);
  const restored = restoreHomeHelpEntry(remaining, 'patient-a', done);
  assert.deepEqual(restored[1], done);
  assert.notStrictEqual(restored[1], done);
  assert.strictEqual(restoreHomeHelpEntry(restored, 'patient-a', done), restored);
});

test('filters and downloaded handover only contain the chosen family, including actual status and contact details', () => {
  const entries = [entry, other, { ...entry, id: 'done', status: 'Done', date: '' }];
  assert.equal(selectHomeHelpEntries(entries, 'patient-a', 'To arrange').length, 1);
  assert.equal(selectHomeHelpEntries(entries, 'patient-a', 'Done').length, 1);
  const text = homeHelpText(entries, 'patient-a', '2026-09-21');
  assert.match(text, /To arrange —  Arrange a ride/);
  assert.match(text, /Done —  Arrange a ride/);
  assert.match(text, /Phone: \+91 90000 00000/);
  assert.match(text, /No bookings or messages are sent/);
  assert.doesNotMatch(text, /Other family|Another family private note/);
});
