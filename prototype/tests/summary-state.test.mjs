import assert from 'node:assert/strict';
import test from 'node:test';
import {
  beginSummaryRevision,
  invalidateSummaryReview,
  isSummaryReadyToRelease,
  latestSummaryRelease,
  nextSummaryVersion,
  releaseSummary,
  reviseSummary,
  setSummaryFieldStatus,
  summaryReleaseByNumber,
} from '../app/summary-state.ts';

const source = [
  'The patient wants time with family before the next discussion.',
  'The patient and clinician attended the conversation.',
  'We discussed questions for review.',
  'We agreed to continue the conversation next week.',
].join(' ');

const excerpts = {
  priorities: 'wants time with family',
  participants: 'patient and clinician attended the conversation',
  topics: 'discussed questions for review',
  openQuestions: '',
  followUp: 'continue the conversation next week',
};

const fields = {
  priorities: 'The patient wants time with family before the next discussion.',
  participants: 'The patient and clinician attended the conversation.',
  topics: 'Questions for review were discussed.',
  openQuestions: 'Not stated in this conversation.',
  followUp: 'The conversation will continue next week.',
};

const statuses = {
  priorities: 'ready',
  participants: 'ready',
  topics: 'ready',
  openQuestions: 'not-stated',
  followUp: 'ready',
};

function reviewedDraft(overrides = {}) {
  return {
    patientId: 'SYNTHETIC-01',
    releases: [],
    draftSource: source,
    draftExcerpts: { ...excerpts },
    draftIsNewConversation: false,
    draftPrepared: true,
    draftFields: { ...fields },
    draftStatuses: { ...statuses },
    verificationChecks: { source: true, ambiguity: true, inference: true, reviewDate: true },
    authorisation: 'Authorised by Patient & Surrogate',
    attested: true,
    versionPublished: false,
    publishedFields: null,
    recordOnlyField: 'preserved',
    ...overrides,
  };
}

function seedV1(patientId = 'SYNTHETIC-01') {
  return {
    number: 1,
    patientId,
    fields: Object.freeze({
      priorities: 'Version 1 priority',
      participants: 'Version 1 participants',
      topics: 'Version 1 topics',
      openQuestions: 'Version 1 questions',
      followUp: 'Version 1 follow-up',
    }),
    statuses: null,
    source: null,
    excerpts: null,
    coverage: null,
    authorisation: 'Verbal acknowledgement recorded by care team',
    physician: 'Dr Seed',
    releasedAt: '2026-08-21T12:00:00+05:30',
  };
}

function prepareNextDraft(state) {
  const prepared = beginSummaryRevision(state);
  const withSource = reviseSummary(prepared, { draftSource: source });
  const withFields = reviseSummary(withSource, { draftFields: fields });
  const withExcerpts = reviseSummary(withFields, { draftExcerpts: excerpts });
  const withStatuses = reviseSummary(withExcerpts, { draftStatuses: statuses });
  return {
    ...withStatuses,
    verificationChecks: { source: true, ambiguity: true, inference: true, reviewDate: true },
    authorisation: 'Authorised by Patient & Surrogate',
    attested: true,
  };
}

test('editing text, source, excerpt, or status requires fresh review', () => {
  const original = reviewedDraft();
  const revised = reviseSummary(original, {
    draftFields: { priorities: 'Changed synthetic text' },
  });
  assert.equal(revised.patientId, original.patientId);
  assert.equal(revised.draftStatuses.priorities, 'clarify');
  assert.deepEqual(revised.verificationChecks, { source: false, ambiguity: false, inference: false, reviewDate: false });
  assert.equal(revised.authorisation, 'Pending patient review');
  assert.equal(revised.attested, false);
  assert.equal(releaseSummary(revised), revised);
  assert.equal(original.draftFields.priorities, fields.priorities);

  const sourceEdited = reviseSummary(original, { draftSource: `${source} A new detail was recorded.` });
  assert.deepEqual(sourceEdited.draftStatuses, {
    priorities: 'clarify', participants: 'clarify', topics: 'clarify', openQuestions: 'not-stated', followUp: 'clarify',
  });
  assert.deepEqual(sourceEdited.draftExcerpts, { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' });

  const excerptEdited = reviseSummary(original, { draftExcerpts: { priorities: 'patient wants time with family' } });
  assert.equal(excerptEdited.draftStatuses.priorities, 'ready');
  assert.equal(excerptEdited.attested, false);
  assert.equal(excerptEdited.verificationChecks.source, false);

  const statusEdited = setSummaryFieldStatus(original, 'topics', 'clarify');
  assert.equal(statusEdited.draftStatuses.topics, 'clarify');
  assert.equal(statusEdited.authorisation, 'Pending patient review');
});

test('a no-op edit preserves the reviewed state', () => {
  const original = reviewedDraft();
  assert.equal(reviseSummary(original, { draftFields: { ...original.draftFields } }), original);
  assert.equal(reviseSummary(original, { draftSource: original.draftSource }), original);
  assert.equal(reviseSummary(original, { draftExcerpts: { ...original.draftExcerpts } }), original);
});

test('a blank revision preserves the latest release while creating a new draft', () => {
  const initial = reviewedDraft({ releases: [seedV1()], publishedFields: seedV1().fields });
  const released = releaseSummary(initial, {
    physician: 'Dr Sujay',
    releasedAt: '2026-08-28T12:00:00+05:30',
    coverage: { priorities: 'discussed', followUp: 'discussed' },
  });
  const revised = beginSummaryRevision(released);

  assert.equal(revised.versionPublished, false);
  assert.equal(revised.draftPrepared, true);
  assert.equal(revised.draftIsNewConversation, true);
  assert.deepEqual(revised.draftFields, { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' });
  assert.deepEqual(revised.draftStatuses, { priorities: 'clarify', participants: 'clarify', topics: 'clarify', openQuestions: 'clarify', followUp: 'clarify' });
  assert.equal(revised.draftSource, '');
  assert.deepEqual(revised.draftExcerpts, { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' });
  assert.equal(latestSummaryRelease(revised).number, 2);
  assert.deepEqual(revised.publishedFields, released.publishedFields);
  assert.deepEqual(revised.releases, released.releases);
  assert.equal(beginSummaryRevision(revised), revised);
});

test('releases append V2 and V3 without changing prior snapshots', () => {
  const v1 = seedV1();
  const state = reviewedDraft({ releases: [v1], publishedFields: v1.fields });
  const v2State = releaseSummary(state, {
    physician: 'Dr Two',
    releasedAt: '2026-08-28T12:00:00+05:30',
    coverage: { priorities: 'discussed' },
  });
  const v2 = latestSummaryRelease(v2State);
  assert.equal(v2.number, 2);
  assert.equal(nextSummaryVersion(v2State), 3);

  const v3Draft = prepareNextDraft(v2State);
  const v3State = releaseSummary(v3Draft, {
    physician: 'Dr Three',
    releasedAt: '2026-09-13T12:00:00+05:30',
    coverage: { priorities: 'discussed', followUp: 'discussed' },
  });
  assert.deepEqual(v3State.releases.map((release) => release.number), [1, 2, 3]);
  assert.equal(latestSummaryRelease(v3State).number, 3);
  assert.equal(summaryReleaseByNumber(v3State, 1), v1);
  assert.equal(summaryReleaseByNumber(v3State, 2), v2);
  assert.equal(summaryReleaseByNumber(v3State, 3).physician, 'Dr Three');
  assert.equal(v1.source, null);
  assert.equal(v1.statuses, null);
  assert.equal(v1.excerpts, null);
  assert.equal(v2.source, source);
  assert.equal(v2.coverage.priorities, 'discussed');
  assert.equal(v3State.publishedFields, v3State.releases[2].fields);
});

test('release blocks missing, mismatched, and empty source excerpts', () => {
  const missingSource = reviewedDraft({ draftSource: '' });
  assert.equal(isSummaryReadyToRelease(missingSource), false);
  assert.equal(releaseSummary(missingSource), missingSource);

  const mismatched = reviewedDraft({ draftExcerpts: { ...excerpts, priorities: 'not in source' } });
  assert.equal(isSummaryReadyToRelease(mismatched), false);
  assert.equal(releaseSummary(mismatched), mismatched);

  const emptyExcerpt = reviewedDraft({ draftExcerpts: { ...excerpts, followUp: '   ' } });
  assert.equal(isSummaryReadyToRelease(emptyExcerpt), false);
  assert.equal(releaseSummary(emptyExcerpt), emptyExcerpt);
});

test('absence status uses the canonical text and cannot retain substantive text', () => {
  const absent = setSummaryFieldStatus(reviewedDraft(), 'topics', 'not-stated');
  assert.equal(absent.draftFields.topics, 'Not stated in this conversation.');
  assert.equal(absent.draftExcerpts.topics, '');

  const invalid = reviewedDraft({
    draftFields: { ...fields, topics: 'Substantive text under absence' },
    draftStatuses: { ...statuses, topics: 'not-stated' },
  });
  assert.equal(releaseSummary(invalid), invalid);
});

test('release metadata is bound to the state patient and nested snapshots are immutable', () => {
  const state = reviewedDraft({ releases: [seedV1('OTHER-PATIENT')] });
  assert.equal(latestSummaryRelease(state), null);
  assert.equal(nextSummaryVersion(state), 1);

  const released = releaseSummary(state, {
    physician: 'Dr Reviewer',
    releasedAt: '2026-09-13T12:00:00+05:30',
    coverage: { patientId: 'OTHER-PATIENT', priorities: 'discussed' },
  });
  const release = latestSummaryRelease(released);
  assert.equal(release.patientId, 'SYNTHETIC-01');
  assert.equal(release.coverage.patientId, 'OTHER-PATIENT');
  assert.equal(Object.isFrozen(release), true);
  assert.equal(Object.isFrozen(release.fields), true);
  assert.equal(Object.isFrozen(release.statuses), true);
  assert.equal(Object.isFrozen(release.excerpts), true);
  assert.equal(Object.isFrozen(release.coverage), true);
  assert.equal(Object.isFrozen(released.releases), true);
  assert.equal(released.recordOnlyField, 'preserved');
  assert.throws(() => { release.fields.priorities = 'Overwrite'; }, TypeError);
  assert.throws(() => { release.coverage.priorities = 'Overwrite'; }, TypeError);
});

test('all existing release guards remain enforced', () => {
  const original = reviewedDraft();
  for (const patch of [
    { draftPrepared: false },
    { attested: false },
    { verificationChecks: { ...original.verificationChecks, source: false } },
    { draftStatuses: { ...original.draftStatuses, followUp: 'clarify' } },
    { draftFields: { ...original.draftFields, participants: '   ' } },
    { authorisation: 'Pending patient review' },
    { authorisation: 'Declined' },
    { authorisation: '' },
    { authorisation: 'Unknown' },
  ]) {
    const state = { ...original, ...patch };
    assert.equal(releaseSummary(state), state);
  }

  assert.equal(releaseSummary(original), original);
  assert.equal(releaseSummary(original, { physician: '   ', releasedAt: '2026-09-13T12:00:00+05:30' }), original);
  assert.equal(releaseSummary(original, { physician: 'Dr Reviewer', releasedAt: 'not a timestamp' }), original);

  const released = releaseSummary(original, {
    physician: 'Dr Reviewer',
    releasedAt: '2026-09-13T12:00:00+05:30',
  });
  assert.equal(released.versionPublished, true);
  assert.equal(releaseSummary(released), released);
  assert.equal(invalidateSummaryReview(released), released);
  assert.equal(reviseSummary(released, { draftFields: { topics: 'Later edit' } }), released);
  assert.deepEqual(released.publishedFields, original.draftFields);
  assert.notEqual(released.publishedFields, original.draftFields);
  assert.throws(() => { released.publishedFields.priorities = 'Overwrite'; }, TypeError);
});
