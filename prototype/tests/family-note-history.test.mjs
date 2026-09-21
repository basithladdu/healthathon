import test from 'node:test';
import assert from 'node:assert/strict';
import { compareFamilyNoteVersions, exportFamilyNoteComparison, exportFamilyNoteVersion, selectFamilyNoteVersions } from '../app/family-note-history-state.ts';

const fields = { priorities: 'Time at home', participants: 'Patient and family', topics: 'Next visit', openQuestions: 'Not stated in this conversation.', followUp: 'Call on Monday\nBring the existing note' };
const release = { number: 1, patientId: 'patient-a', fields, statuses: null, source: 'The exact conversation text', excerpts: null, coverage: null, authorisation: 'Authorised by Patient & Surrogate', physician: 'Dr Asha', releasedAt: '2026-08-24T15:00:00.000Z' };
const newer = { ...release, number: 2, fields: { ...fields, followUp: 'Call on Tuesday\nBring the existing note' }, physician: 'Dr Ravi', releasedAt: '2026-08-28T15:00:00.000Z' };
const other = { ...release, number: 9, patientId: 'patient-b', source: 'Another family private note' };

test('version selection is patient scoped and newest first without mutating the stored release list', () => {
  const releases = Object.freeze([Object.freeze(release), Object.freeze(other), Object.freeze(newer)]);
  assert.deepEqual(selectFamilyNoteVersions(releases, 'patient-a').map((item) => item.number), [2, 1]);
  assert.equal(releases[0], release);
  assert.deepEqual(selectFamilyNoteVersions(releases, 'patient-c'), []);
  assert.equal(compareFamilyNoteVersions(releases, 'patient-a', 1, 9), null);
});

test('comparison reports exact wording, including whitespace, rather than inferred meaning', () => {
  const newerWhitespace = { ...newer, fields: { ...newer.fields, priorities: 'Time at home ' } };
  const comparison = compareFamilyNoteVersions([release, newerWhitespace], 'patient-a', 2, 1);
  assert.equal(comparison.earlier, release);
  assert.equal(comparison.later, newerWhitespace);
  assert.equal(comparison.mode, 'fields');
  assert.equal(comparison.rows.find((row) => row.key === 'priorities').change, 'changed');
  assert.equal(comparison.rows.find((row) => row.key === 'openQuestions').change, 'unchanged');
  assert.equal(comparison.rows.find((row) => row.key === 'followUp').later, newer.fields.followUp);
  assert.equal(comparison.rows.filter((row) => row.change === 'changed').length, 2);
  assert.equal(compareFamilyNoteVersions([release, newer], 'patient-a', 2, 2), null);
});

test('legacy versions compare saved source text and missing text never becomes an inferred change', () => {
  const sourceOnly = { ...release, fields: null };
  const comparison = compareFamilyNoteVersions([sourceOnly, newer], 'patient-a', 1, 2);
  assert.equal(comparison.mode, 'source');
  assert.equal(comparison.rows[0].change, 'unchanged');
  assert.equal(comparison.rows[0].earlier, sourceOnly.source);
  const missing = { ...sourceOnly, source: null };
  const unavailable = compareFamilyNoteVersions([missing, newer], 'patient-a', 1, 2);
  assert.equal(unavailable.rows[0].change, 'unavailable');
  assert.equal(exportFamilyNoteVersion([missing], 'patient-a', 'Meera', 1), null);
});

test('selected-note export retains version, physician, exact timestamp and multiline content without other patient data', () => {
  const text = exportFamilyNoteVersion([release, newer, other], 'patient-a', 'Meera', 1);
  assert.ok(text.includes('Version 1'));
  assert.ok(text.includes(release.physician));
  assert.ok(text.includes(release.releasedAt));
  assert.ok(text.includes(fields.followUp));
  assert.ok(text.includes(release.authorisation));
  assert.ok(!text.includes(other.source));
  assert.ok(!text.includes(newer.physician));
  assert.equal(exportFamilyNoteVersion([other], 'patient-a', 'Meera', 9), null);
});

test('comparison export preserves both originals and identifies unavailable legacy text explicitly', () => {
  const text = exportFamilyNoteComparison([release, newer, other], 'patient-a', 'Meera', 2, 1);
  assert.ok(text.includes(fields.followUp));
  assert.ok(text.includes(newer.fields.followUp));
  assert.ok(text.includes(release.releasedAt));
  assert.ok(text.includes(newer.releasedAt));
  assert.match(text, /Next steps — Changed/);
  assert.match(text, /Who was there — Unchanged/);
  assert.ok(!text.includes(other.source));
  const missing = { ...release, fields: null, source: null };
  const unavailable = exportFamilyNoteComparison([missing, newer], 'patient-a', 'Meera', 1, 2);
  assert.match(unavailable, /Cannot compare: text was not saved/);
  assert.ok(unavailable.includes(newer.source));
});
