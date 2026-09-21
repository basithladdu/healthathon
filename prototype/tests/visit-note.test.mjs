import assert from 'node:assert/strict';
import test from 'node:test';
import { prepareVisitNote, approveVisitNote } from '../app/visit-note-state.ts';
import { latestSummaryRelease } from '../app/summary-state.ts';

function emptyState() {
  const blank = { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' };
  return { patientId: 'SAMPLE-01', releases: [], draftSource: '', draftExcerpts: { ...blank }, draftIsNewConversation: true, draftPrepared: false,
    draftFields: { ...blank }, draftStatuses: Object.fromEntries(Object.keys(blank).map((key) => [key, 'clarify'])),
    verificationChecks: { source: false, ambiguity: false, inference: false, reviewDate: false }, authorisation: 'Pending patient review', attested: false,
    versionPublished: false, publishedFields: null, recordOnlyField: 'preserved' };
}
const approval = { treatingPhysician: true, physician: 'Dr Sample', permission: 'Authorised by Patient & Surrogate', reviewed: true, releasedAt: '2026-09-20T10:00:00Z' };

test('an existing note is copied verbatim without inferred preferences or a lost patient scope', () => {
  const state = emptyState();
  assert.equal(prepareVisitNote(state, '   '), state);
  const next = prepareVisitNote(state, 'We will discuss the patient’s questions at the next visit.');
  assert.equal(next.draftFields.topics, next.draftSource);
  assert.equal(next.draftExcerpts.topics, next.draftSource);
  assert.equal(next.draftStatuses.priorities, 'not-stated');
  assert.equal(next.patientId, state.patientId);
  assert.equal(next.recordOnlyField, 'preserved');
  assert.equal(state.draftSource, '');
});

test('the short review still requires a doctor, explicit permission, review and valid source', () => {
  const next = prepareVisitNote(emptyState(), 'The patient asked to include her daughter in the next conversation.');
  for (const patch of [{ treatingPhysician: false }, { reviewed: false }, { permission: 'Pending patient review' }, { permission: 'Declined' }, { physician: '' }]) {
    assert.equal(approveVisitNote(next, { ...approval, ...patch }), next);
  }
  const unsupported = { ...next, draftExcerpts: { ...next.draftExcerpts, topics: 'Not present in the source' } };
  assert.equal(approveVisitNote(unsupported, approval), unsupported);
  assert.equal(next.releases.length, 0);
});

test('approval preserves older versions and changing the note requires a new review', () => {
  const first = approveVisitNote(prepareVisitNote(emptyState(), 'First visit note.'), approval);
  assert.equal(latestSummaryRelease(first).number, 1);
  assert.equal(approveVisitNote(first, approval), first);
  const revision = prepareVisitNote(first, 'Follow-up conversation.');
  assert.equal(revision.attested, false);
  assert.equal(revision.authorisation, 'Pending patient review');
  assert.equal(first.releases[0].fields.topics, 'First visit note.');
  const second = approveVisitNote(revision, approval);
  assert.equal(second.releases.length, 2);
  assert.equal(latestSummaryRelease(second).number, 2);
  assert.equal(second.releases[0], first.releases[0]);
});
