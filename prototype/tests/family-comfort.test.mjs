import test from 'node:test';
import assert from 'node:assert/strict';
import { addComfortEntry, exportComfortEntries, latestComfortEntryToday, removeComfortEntry, restoreComfortEntry, selectComfortEntries } from '../app/family-comfort-state.ts';

const entry = { id: 'one', patientId: 'patient-a', author: 'Kavya', date: '2026-09-21', feeling: 'Peaceful', createdAt: '2026-09-21T10:00:00Z' };
const later = { ...entry, id: 'two', feeling: 'Tired', createdAt: '2026-09-21T12:00:00Z' };
const otherPerson = { ...entry, id: 'other-person', author: 'Ravi', feeling: 'Happy' };
const otherPatient = { ...entry, id: 'other-patient', patientId: 'patient-b', feeling: 'In pain' };

test('current feeling comes only from the latest saved check-in for this author, patient and day', () => {
  const entries = [entry, otherPerson, otherPatient, later];
  assert.equal(latestComfortEntryToday(entries, 'patient-a', 'Kavya', '2026-09-21'), later);
  assert.equal(latestComfortEntryToday(entries, 'patient-a', 'Ravi', '2026-09-21'), otherPerson);
  assert.equal(latestComfortEntryToday(entries, 'patient-a', 'Kavya', '2026-09-22'), null);
  assert.equal(latestComfortEntryToday([], 'patient-a', 'Kavya', '2026-09-21'), null);
  assert.deepEqual(entries, [entry, otherPerson, otherPatient, later]);
});

test('a check-in needs a real date, supported feeling and capture timestamp without duplicate IDs', () => {
  assert.equal(addComfortEntry([], entry).length, 1);
  for (const invalid of [{ ...entry, date: '2026-02-30' }, { ...entry, feeling: 'Diagnosed' }, { ...entry, author: ' ' }, { ...entry, createdAt: 'not a timestamp' }]) assert.deepEqual(addComfortEntry([], invalid), []);
  const entries = [entry];
  assert.equal(addComfortEntry(entries, { ...entry, feeling: 'Happy' }), entries);
});

test('remove and undo cannot change another author or patient, and restoring recovers the prior feeling', () => {
  const entries = [entry, later, otherPerson, otherPatient];
  assert.deepEqual(removeComfortEntry(entries, 'patient-b', 'Kavya', 'two'), entries);
  assert.deepEqual(removeComfortEntry(entries, 'patient-a', 'Ravi', 'two'), entries);
  const removed = removeComfortEntry(entries, 'patient-a', 'Kavya', 'two');
  assert.equal(latestComfortEntryToday(removed, 'patient-a', 'Kavya', '2026-09-21'), entry);
  assert.equal(restoreComfortEntry(removed, 'patient-a', 'Ravi', later), removed);
  const restored = restoreComfortEntry(removed, 'patient-a', 'Kavya', later);
  assert.equal(latestComfortEntryToday(restored, 'patient-a', 'Kavya', '2026-09-21'), later);
  assert.equal(restoreComfortEntry(restored, 'patient-a', 'Kavya', later), restored);
});

test('history export keeps the self-reports and dates without other people or patients', () => {
  const entries = [entry, otherPatient, otherPerson, later];
  assert.deepEqual(selectComfortEntries(entries, 'patient-a', 'Kavya'), [later, entry]);
  const text = exportComfortEntries(entries, 'patient-a', 'Kavya');
  assert.ok(text.includes('Peaceful'));
  assert.ok(text.includes('Tired'));
  assert.ok(text.includes(entry.createdAt));
  assert.ok(!text.includes('Happy'));
  assert.ok(!text.includes('In pain'));
  assert.ok(!text.includes('Ravi'));
});
