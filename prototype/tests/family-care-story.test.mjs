import test from 'node:test';
import assert from 'node:assert/strict';
import { addCareStoryEntry, updateCareStoryEntry, removeCareStoryEntry, restoreCareStoryEntry, selectCareStoryEntries, exportCareStory } from '../app/family-care-story-state.ts';

const draft = { date: '2026-09-20', kind: 'Treatment change', title: ' Changed appointment plan ', details: 'Copied wording\nSecond line', changeReason: 'Reason as written in the visit note', source: ' Visit note ', sourceAuthor: 'Dr Rao' };
const entry = { ...draft, id: 'one', patientId: 'patient-a', createdBy: 'Kavya', updatedBy: 'Kavya' };
const other = { ...entry, id: 'two', patientId: 'patient-b', title: 'Private to another family' };

test('adding rejects impossible dates and duplicates, and keeps entered clinical wording without inferred values', () => {
  assert.deepEqual(addCareStoryEntry([], 'patient-a', 'Kavya', 'one', { ...draft, date: '2026-02-30' }), []);
  assert.deepEqual(addCareStoryEntry([], 'patient-a', 'Kavya', 'one', { ...draft, title: ' ' }), []);
  assert.deepEqual(addCareStoryEntry([], 'patient-a', ' ', 'one', draft), []);
  assert.deepEqual(addCareStoryEntry([entry], 'patient-b', 'Another person', 'one', draft), [entry]);
  const added = addCareStoryEntry([], 'patient-a', ' Kavya ', 'one', { ...draft, changeReason: '' });
  assert.equal(added[0].title, 'Changed appointment plan');
  assert.equal(added[0].details, draft.details);
  assert.equal(added[0].changeReason, '');
  assert.equal(added[0].source, 'Visit note');
  assert.equal(added[0].createdBy, 'Kavya');
  assert.equal(added[0].updatedBy, 'Kavya');
});

test('editing cannot change another patient or original author, and preserves supplied source wording', () => {
  const entries = [entry, other];
  assert.equal(updateCareStoryEntry(entries, 'patient-b', 'one', 'Ravi', draft), entries);
  const next = updateCareStoryEntry(entries, 'patient-a', 'one', 'Ravi', { ...draft, title: 'Updated note', sourceAuthor: 'Discharge team' });
  assert.equal(next[0].title, 'Updated note');
  assert.equal(next[0].createdBy, 'Kavya');
  assert.equal(next[0].updatedBy, 'Ravi');
  assert.equal(next[0].sourceAuthor, 'Discharge team');
  assert.equal(next[1], other);
  assert.equal(entry.title, draft.title);
});

test('remove and undo are patient scoped, retain the original entry, and do not overwrite a reused ID', () => {
  const entries = [entry, other];
  assert.deepEqual(removeCareStoryEntry(entries, 'patient-b', 'one'), entries);
  const removed = removeCareStoryEntry(entries, 'patient-a', 'one');
  assert.deepEqual(removed, [other]);
  assert.equal(restoreCareStoryEntry(removed, 'patient-b', entry), removed);
  const restored = restoreCareStoryEntry(removed, 'patient-a', entry);
  assert.equal(restored[1], entry);
  assert.equal(restoreCareStoryEntry(restored, 'patient-a', entry), restored);
  assert.deepEqual(entries, [entry, other]);
});

test('timeline filters and sorts newest first without changing storage order or leaking another patient', () => {
  const earlier = { ...entry, id: 'early', date: '2026-08-10', kind: 'Past history' };
  const entries = [earlier, other, entry];
  assert.deepEqual(selectCareStoryEntries(entries, 'patient-a').map((item) => item.id), ['one', 'early']);
  assert.deepEqual(selectCareStoryEntries(entries, 'patient-a', 'Past history'), [earlier]);
  assert.deepEqual(selectCareStoryEntries(entries, 'patient-a', 'Report'), []);
  assert.deepEqual(entries, [earlier, other, entry]);
});

test('download includes the entire selected patient story, authors and verbatim notes, without the other patient', () => {
  const earlier = { ...entry, id: 'early', date: '2026-08-10', kind: 'Past history', title: 'Earlier visit', updatedBy: 'Ravi' };
  const output = exportCareStory([earlier, other, entry], 'patient-a');
  assert.match(output, /Family-added notes/);
  assert.ok(output.indexOf('2026-09-20') < output.indexOf('2026-08-10'));
  assert.ok(output.includes(draft.details));
  assert.ok(output.includes(`What changed / reason as documented: ${draft.changeReason}`));
  assert.match(output, /Source written by: Dr Rao/);
  assert.match(output, /Added by: Kavya/);
  assert.match(output, /Last edited by: Ravi/);
  assert.ok(!output.includes(other.title));
});
