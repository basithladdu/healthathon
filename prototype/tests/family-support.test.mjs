import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addSupportContactNote, addSupportPlace, completeSupportFollowUp, orderedSupportNotes, removeSupportContactNote, removeSupportPlace,
  restoreSupportContactNote, restoreSupportPlace, setSupportFollowUp, supportPlacesText, updateSupportContactNote,
  validateSupportContactDraft,
} from '../app/family-support-state.ts';
import { CARE_CENTRES } from '../app/care-directory-data.ts';

const today = '2026-09-21';
const author = 'Kavya';
const centreId = CARE_CENTRES[0].id;
const first = addSupportPlace([], 'patient-a', centreId, author, 'place-a');
const both = addSupportPlace(first, 'patient-b', centreId, 'Other family', 'place-b');
const draft = { date: '2026-09-20', person: 'Reception', reply: '  Please call tomorrow.\nAsk for the home care team.  ' };

test('the shortlist accepts real listings, rejects duplicates, and allows another patient their own copy', () => {
  assert.equal(first.length, 1);
  assert.equal(both.length, 2);
  assert.equal(addSupportPlace(first, 'patient-a', centreId, author, 'again'), first);
  assert.equal(addSupportPlace(first, 'patient-a', 'invented-centre', author, 'wrong'), first);
  assert.equal(addSupportPlace(first, 'patient-a', CARE_CENTRES[1].id, author, 'place-a'), first);
  assert.equal(first[0].notes.length, 0);
  assert.equal(first[0].followUpDoneBy, null);
});

test('call notes preserve exactly what the family wrote and cannot be filed under another patient', () => {
  const logged = addSupportContactNote(both, 'patient-a', 'place-a', author, today, 'call-a', draft);
  assert.equal(logged[0].notes[0].reply, draft.reply);
  assert.equal(logged[0].notes[0].addedBy, author);
  assert.equal(logged[1], both[1]);
  assert.equal(both[0].notes.length, 0);
  assert.equal(addSupportContactNote(both, 'patient-b', 'place-a', author, today, 'wrong', draft), both);
  assert.equal(addSupportContactNote(logged, 'patient-a', 'place-a', author, today, 'call-a', draft), logged);
});

test('future or invalid call dates and blank replies cannot pretend that contact happened', () => {
  assert.equal(validateSupportContactDraft({ ...draft, date: '2026-09-22' }, today), 'date');
  assert.equal(validateSupportContactDraft({ ...draft, date: '2026-02-30' }, today), 'date');
  assert.equal(validateSupportContactDraft({ ...draft, reply: ' ' }, today), 'reply');
  assert.equal(addSupportContactNote(first, 'patient-a', 'place-a', author, today, 'bad', { ...draft, date: '2026-09-22' }), first);
});

test('the latest call is chosen by contact date even when an older note is entered later', () => {
  const recent = addSupportContactNote(first, 'patient-a', 'place-a', author, today, 'recent', { ...draft, date: today });
  const older = addSupportContactNote(recent, 'patient-a', 'place-a', author, today, 'older', draft);
  assert.deepEqual(orderedSupportNotes(older[0]).map((note) => note.id), ['recent', 'older']);
  assert.deepEqual(older[0].notes.map((note) => note.id), ['recent', 'older']);
});

test('editing, removing and restoring call notes retain the author and original order', () => {
  const logged = addSupportContactNote(first, 'patient-a', 'place-a', author, today, 'call-a', draft);
  const second = addSupportContactNote(logged, 'patient-a', 'place-a', author, today, 'call-b', { ...draft, date: today });
  const edited = updateSupportContactNote(second, 'patient-a', 'place-a', 'call-a', 'Family helper', today, { ...draft, reply: 'Corrected family note.' });
  assert.equal(edited[0].notes[0].addedBy, author);
  assert.equal(edited[0].notes[0].updatedBy, 'Family helper');
  assert.equal(logged[0].notes[0].reply, draft.reply);
  assert.equal(updateSupportContactNote(second, 'patient-b', 'place-a', 'call-a', author, today, draft), second);
  const removed = removeSupportContactNote(edited, 'patient-a', 'place-a', 'call-a');
  const undo = { patientId: 'patient-a', placeId: 'place-a', note: edited[0].notes[0], index: 0 };
  assert.deepEqual(restoreSupportContactNote(removed, 'patient-a', undo), edited);
  assert.equal(restoreSupportContactNote(removed, 'patient-b', undo), removed);
  assert.equal(restoreSupportContactNote(edited, 'patient-a', undo), edited);
});

test('follow-up completion is an explicit family action and changes reopen the task', () => {
  assert.equal(completeSupportFollowUp(first, 'patient-a', 'place-a', author, true), first);
  assert.equal(setSupportFollowUp(first, 'patient-a', 'place-a', '', today), first);
  assert.equal(setSupportFollowUp(first, 'patient-a', 'place-a', 'Call', '2026-02-30'), first);
  const planned = setSupportFollowUp(both, 'patient-a', 'place-a', 'Call the home care team', '2026-09-22');
  assert.equal(planned[0].followUpDoneBy, null);
  const done = completeSupportFollowUp(planned, 'patient-a', 'place-a', author, true);
  assert.equal(done[0].followUpDoneBy, author);
  assert.equal(done[1], both[1]);
  assert.equal(completeSupportFollowUp(done, 'patient-a', 'place-a', author, false)[0].followUpDoneBy, null);
  assert.equal(setSupportFollowUp(done, 'patient-a', 'place-a', 'Bring the referral letter', '2026-09-23')[0].followUpDoneBy, null);
  assert.equal(setSupportFollowUp(done, 'patient-a', 'place-a', done[0].nextStep, done[0].followUpOn), done);
  assert.equal(completeSupportFollowUp(done, 'patient-b', 'place-a', author, false), done);
});

test('removing and restoring a place carries its call history but cannot cross patients or duplicate a centre', () => {
  const logged = addSupportContactNote(both, 'patient-a', 'place-a', author, today, 'call-a', draft);
  const removed = removeSupportPlace(logged, 'patient-a', 'place-a');
  assert.deepEqual(restoreSupportPlace(removed, 'patient-a', logged[0], 0), logged);
  assert.equal(restoreSupportPlace(removed, 'patient-b', logged[0], 0), removed);
  assert.equal(restoreSupportPlace(logged, 'patient-a', logged[0], 0), logged);
  assert.deepEqual(removeSupportPlace(logged, 'patient-b', 'place-a'), logged);
});

test('the download identifies public listings and family notes separately and excludes other patients', () => {
  const withOtherNote = addSupportContactNote(both, 'patient-b', 'place-b', 'Other family', today, 'private', { ...draft, reply: 'Other patient private reply' });
  const logged = addSupportContactNote(withOtherNote, 'patient-a', 'place-a', author, today, 'call-a', draft);
  const output = supportPlacesText(setSupportFollowUp(logged, 'patient-a', 'place-a', 'Call tomorrow', '2026-09-22'), 'patient-a');
  assert.ok(output.includes(CARE_CENTRES[0].directoryUrl));
  assert.ok(output.includes('FAMILY CALL NOTES'));
  assert.ok(output.includes(draft.reply));
  assert.ok(output.includes('Still to do'));
  assert.ok(output.includes('do not confirm current services, medicine stock or bookings'));
  assert.ok(!output.includes('Other patient private reply'));
  assert.ok(!output.includes('Other family'));
});
