import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addCareCopyEntry, approvedPatientCopies, careCopyLogText, careCopyStatus, groupCareCopyEntries, removeCareCopyEntry,
  restoreCareCopyEntry, reviewedCopyText, updateCareCopyEntry, validateCareCopyDraft,
} from '../app/family-copy-state.ts';

const makeRelease = (number, patientId = 'patient-a', date = '2026-09-18') => ({
  number, patientId, physician: 'Dr. Sujay H S', releasedAt: `${date}T12:20:00+05:30`,
  fields: { priorities: '  Patient’s exact words.\nKeep this spacing.  ', participants: 'Patient and family', topics: 'Reviewed discussion', openQuestions: 'Not stated in this conversation.', followUp: 'As written by the treating doctor' },
  statuses: null, source: 'Original source', excerpts: null, coverage: null, authorisation: 'Authorised by Patient & Surrogate',
});
const releases = [makeRelease(1), makeRelease(2, 'patient-a', '2026-09-20'), makeRelease(9, 'patient-b')];
const today = '2026-09-21';
const draft = { recipient: 'Family folder', version: 1, givenOn: '2026-09-19', locationNote: 'Blue folder by the door' };
const first = addCareCopyEntry([], 'patient-a', 'Kavya', 'entry-a', draft, releases, today);

test('copy status is derived from that patient’s actual approved releases', () => {
  assert.deepEqual(approvedPatientCopies(releases, 'patient-a').map((copy) => copy.number), [2, 1]);
  assert.equal(careCopyStatus(releases, 'patient-a', 1), 'older');
  assert.equal(careCopyStatus(releases, 'patient-a', 2), 'latest');
  assert.equal(careCopyStatus(releases, 'patient-a', 9), 'unavailable');
  assert.equal(careCopyStatus([], 'patient-a', 2), 'unavailable');
});

test('a handover cannot predate the note, occur in the future or name a missing version', () => {
  assert.equal(validateCareCopyDraft({ ...draft, version: 2 }, releases, 'patient-a', today), 'date');
  assert.equal(validateCareCopyDraft({ ...draft, givenOn: '2026-09-22' }, releases, 'patient-a', today), 'date');
  assert.equal(validateCareCopyDraft({ ...draft, givenOn: '2026-02-30' }, releases, 'patient-a', today), 'date');
  assert.equal(validateCareCopyDraft({ ...draft, version: 9 }, releases, 'patient-a', today), 'version');
  assert.equal(validateCareCopyDraft({ ...draft, recipient: ' ' }, releases, 'patient-a', today), 'recipient');
  assert.equal(addCareCopyEntry(first, 'patient-a', 'Kavya', 'wrong', { ...draft, version: 9 }, releases, today), first);
});

test('giving a newer copy appends history and an older entry added later cannot replace the most recent handover', () => {
  const updated = addCareCopyEntry(first, 'patient-a', 'Kavya', 'entry-new', { ...draft, recipient: ' FAMILY FOLDER ', version: 2, givenOn: today }, releases, today);
  const withOldEntry = addCareCopyEntry(updated, 'patient-a', 'Kavya', 'entry-old', { ...draft, givenOn: '2026-09-18' }, releases, today);
  const groups = groupCareCopyEntries(withOldEntry, releases, 'patient-a');
  assert.equal(groups.length, 1);
  assert.equal(groups[0].history.length, 3);
  assert.equal(groups[0].lastEntry.id, 'entry-new');
  assert.equal(groups[0].status, 'latest');
  assert.equal(first.length, 1);
});

test('downloads preserve the approved wording and do not create a handover entry', () => {
  const output = reviewedCopyText(releases, 'patient-a', 'Meera', 2);
  assert.ok(output.includes(releases[1].fields.priorities));
  assert.ok(output.includes(releases[1].fields.openQuestions));
  assert.ok(output.includes('Version: 2'));
  assert.ok(output.includes(releases[1].physician));
  assert.ok(output.includes(releases[1].releasedAt));
  assert.equal(first.length, 1);
  assert.equal(reviewedCopyText(releases, 'patient-a', 'Meera', 9), null);
  assert.equal(reviewedCopyText([{ ...releases[1], fields: null }], 'patient-a', 'Meera', 2), null);
});

test('corrections and undo preserve authors and remain within the patient’s log', () => {
  const both = addCareCopyEntry(first, 'patient-b', 'Another family', 'entry-b', { ...draft, recipient: 'Other patient private place', version: 9 }, releases, today);
  assert.equal(updateCareCopyEntry(both, 'patient-b', 'entry-a', 'Another family', { ...draft, version: 9 }, releases, today), both);
  const edited = updateCareCopyEntry(both, 'patient-a', 'entry-a', 'Family helper', { ...draft, locationNote: 'Corrected location' }, releases, today);
  assert.equal(edited[0].addedBy, 'Kavya');
  assert.equal(edited[0].updatedBy, 'Family helper');
  assert.equal(edited[1], both[1]);
  const removed = removeCareCopyEntry(edited, 'patient-a', 'entry-a');
  assert.deepEqual(restoreCareCopyEntry(removed, 'patient-a', edited[0], 0), edited);
  assert.equal(restoreCareCopyEntry(removed, 'patient-b', edited[0], 0), removed);
  assert.equal(restoreCareCopyEntry(edited, 'patient-a', edited[0], 0), edited);
  const output = careCopyLogText(both, releases, 'patient-a', 'Meera');
  assert.ok(output.includes('not check delivery, reading'));
  assert.ok(!output.includes('Other patient private place'));
  assert.ok(!output.includes('Another family'));
});

test('a newer release makes the previously current recorded copy older without changing the handover history', () => {
  const newEntry = addCareCopyEntry([], 'patient-a', 'Kavya', 'current', { ...draft, version: 2, givenOn: today }, releases, today);
  assert.equal(groupCareCopyEntries(newEntry, releases, 'patient-a')[0].status, 'latest');
  const updatedReleases = [...releases, makeRelease(3, 'patient-a', today)];
  assert.equal(groupCareCopyEntries(newEntry, updatedReleases, 'patient-a')[0].status, 'older');
  assert.equal(newEntry[0].version, 2);
  assert.equal(newEntry[0].givenOn, today);
});
