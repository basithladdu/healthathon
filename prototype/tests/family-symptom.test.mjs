import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addSymptomEntry, updateSymptomEntry, removeSymptomEntry, restoreSymptomEntry,
  selectSymptomEntries, symptomDiaryText, validateSymptomInput,
} from '../app/family-symptom-state.ts';

const today = '2026-09-21';
const entry = { id: 'one', patientId: 'patient-a', author: 'Kavya', symptom: ' Pain ', severity: 'Mild', date: today, startedOn: '2026-09-19', note: ' At night ' };
const other = { ...entry, id: 'two', patientId: 'patient-b', author: 'Other family', symptom: 'Tiredness', note: 'Private note from another family' };

test('dates are real, cannot be future observations and onset cannot follow the note', () => {
  assert.equal(validateSymptomInput(entry, today), null);
  assert.equal(validateSymptomInput({ ...entry, date: '2026-02-30' }, today), 'date');
  assert.equal(validateSymptomInput({ ...entry, date: '2026-09-22' }, today), 'date');
  assert.equal(validateSymptomInput({ ...entry, startedOn: '2026-09-22' }, today), 'startedOn');
  assert.equal(validateSymptomInput({ ...entry, startedOn: '2026-02-30' }, today), 'startedOn');
  assert.equal(validateSymptomInput({ ...entry, date: '2024-02-29', startedOn: '' }, today), null);
  assert.equal(validateSymptomInput({ ...entry, severity: '' }, today), 'severity');
  assert.equal(validateSymptomInput({ ...entry, symptom: ' ' }, today), 'symptom');
  assert.equal(validateSymptomInput({ ...entry, note: 'a'.repeat(501) }, today), 'note');
});

test('add and edit keep identities scoped and never mutate the original note', () => {
  const entries = addSymptomEntry([other], entry, today);
  assert.equal(entries[1].symptom, 'Pain');
  assert.equal(entries[1].note, 'At night');
  assert.equal(entry.note, ' At night ');
  assert.strictEqual(addSymptomEntry(entries, entry, today), entries);
  assert.strictEqual(updateSymptomEntry(entries, 'patient-b', 'one', { ...entry, note: 'Changed' }, 'Other family', today), entries);
  const updated = updateSymptomEntry(entries, 'patient-a', 'one', { ...entry, id: 'hijacked', patientId: 'patient-b', author: 'Fake', note: ' Better now ', severity: 'Moderate' }, 'Meera', today);
  assert.equal(updated[1].note, 'Better now');
  assert.equal(updated[1].id, 'one');
  assert.equal(updated[1].patientId, 'patient-a');
  assert.equal(updated[1].author, 'Kavya');
  assert.equal(updated[1].editedBy, 'Meera');
  assert.equal(entries[1].note, 'At night');
  assert.strictEqual(updated[0], other);
});

test('remove and undo affect only the selected patient, preserve the edited note and do not duplicate it', () => {
  const edited = { ...entry, editedBy: 'Meera' };
  const entries = [edited, other];
  assert.deepEqual(removeSymptomEntry(entries, 'patient-b', 'one'), entries);
  const removed = removeSymptomEntry(entries, 'patient-a', 'one');
  assert.deepEqual(removed, [other]);
  assert.equal(entries.length, 2);
  assert.strictEqual(restoreSymptomEntry(removed, 'patient-b', edited, today), removed);
  const restored = restoreSymptomEntry(removed, 'patient-a', edited, today);
  assert.deepEqual(restored[1], edited);
  assert.notStrictEqual(restored[1], edited);
  assert.strictEqual(restoreSymptomEntry(restored, 'patient-a', edited, today), restored);
});

test('filters and export exclude other patients and use an inclusive seven-day window', () => {
  const entries = [entry, other, { ...entry, id: 'older', date: '2026-09-14', startedOn: '' }, { ...entry, id: 'week-start', date: '2026-09-15', startedOn: '' }];
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'today').length, 1);
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'week').length, 2);
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'all').length, 3);
  assert.deepEqual(selectSymptomEntries(entries, 'patient-a', today, 'all', 'Tiredness'), []);
  const copy = symptomDiaryText(entries, 'patient-a', today);
  assert.match(copy, /At night/);
  assert.match(copy, /Started: 2026-09-19/);
  assert.match(copy, /Not sent to the care team/);
  assert.doesNotMatch(copy, /Other family|Private note from another family/);
  assert.equal(entries[0], entry);
});
