import test from 'node:test';
import assert from 'node:assert/strict';
import { addOpenQuestion, addOpenQuestionReply, editOpenQuestion, exportOpenQuestions, markOpenQuestionAsked, postponeOpenQuestion, removeOpenQuestion, restoreOpenQuestion, selectOpenQuestions, setOpenQuestionClosed, setOpenQuestionNextStep } from '../app/family-open-question-state.ts';

const details = { topic: 'Who do we call between visits?', owner: '', due: '' };
const question = { id: 'one', patientId: 'patient-a', ...details, status: 'to-ask', askedOn: '', nextStep: '', createdBy: 'Kavya', updatedBy: 'Kavya', createdOn: '2026-09-21', replies: [] };
const other = { ...question, id: 'two', patientId: 'patient-b', topic: 'Another family private question' };
const reply = { id: 'reply-1', text: '  Call the number on the handout.\nBring it next time.  ', saidBy: 'Dr Asha', date: '2026-09-21' };

test('new questions and postponed questions stay unanswered and unasked until marked explicitly', () => {
  const added = addOpenQuestion([], 'patient-a', 'Kavya', '2026-09-21', 'one', details);
  assert.equal(added[0].status, 'to-ask');
  assert.equal(added[0].askedOn, '');
  assert.equal(addOpenQuestionReply(added, 'patient-a', 'one', 'Kavya', reply), added);
  const later = postponeOpenQuestion(added, 'patient-a', 'one', 'Kavya', '2026-09-24', '2026-09-21');
  assert.equal(later[0].due, '2026-09-24');
  assert.equal(later[0].status, 'to-ask');
  const asked = markOpenQuestionAsked(later, 'patient-a', 'one', 'Kavya', '2026-09-21');
  assert.equal(asked[0].status, 'waiting');
  assert.equal(asked[0].replies.length, 0);
});

test('reply, next step, close and reopen preserve exact family-written words and earlier replies', () => {
  const asked = markOpenQuestionAsked([question], 'patient-a', 'one', 'Kavya', '2026-09-21');
  const replied = addOpenQuestionReply(asked, 'patient-a', 'one', 'Ravi', reply);
  assert.equal(replied[0].status, 'replied');
  assert.equal(replied[0].replies[0].text, reply.text);
  assert.equal(replied[0].replies[0].saidBy, 'Dr Asha');
  assert.equal(replied[0].replies[0].recordedBy, 'Ravi');
  const next = setOpenQuestionNextStep(replied, 'patient-a', 'one', 'Ravi', 'Find the handout', 'Kavya', '2026-09-23');
  const closed = setOpenQuestionClosed(next, 'patient-a', 'one', 'Kavya', true);
  assert.equal(closed[0].status, 'closed');
  const reopened = setOpenQuestionClosed(closed, 'patient-a', 'one', 'Kavya', false);
  assert.equal(reopened[0].status, 'to-ask');
  assert.equal(reopened[0].askedOn, '');
  assert.deepEqual(reopened[0].replies, replied[0].replies);
  assert.equal(question.replies.length, 0);
});

test('all updates and exports stay within the selected patient', () => {
  const entries = [question, other];
  assert.equal(markOpenQuestionAsked(entries, 'patient-b', 'one', 'Ravi', '2026-09-21'), entries);
  assert.equal(editOpenQuestion(entries, 'patient-b', 'one', 'Ravi', { ...details, topic: 'Changed' }), entries);
  assert.equal(setOpenQuestionClosed(entries, 'patient-b', 'one', 'Ravi', true), entries);
  assert.equal(setOpenQuestionNextStep(entries, 'patient-b', 'one', 'Ravi', 'Do a thing', '', ''), entries);
  assert.deepEqual(removeOpenQuestion(entries, 'patient-b', 'one'), entries);
  assert.deepEqual(selectOpenQuestions(entries, 'patient-a'), [question]);
  const text = exportOpenQuestions(entries, 'patient-a');
  assert.ok(text.includes(details.topic));
  assert.ok(text.includes('No reply has been saved.'));
  assert.ok(!text.includes(other.topic));
});

test('impossible dates, past postponement and missing reply attribution cannot advance the question', () => {
  const entries = [question];
  assert.equal(addOpenQuestion(entries, 'patient-a', 'Kavya', '2026-09-21', 'new', { ...details, due: '2026-02-30' }), entries);
  assert.equal(markOpenQuestionAsked(entries, 'patient-a', 'one', 'Kavya', '2026-02-30'), entries);
  assert.equal(postponeOpenQuestion(entries, 'patient-a', 'one', 'Kavya', '2026-09-20', '2026-09-21'), entries);
  const asked = markOpenQuestionAsked(entries, 'patient-a', 'one', 'Kavya', '2026-09-21');
  assert.equal(addOpenQuestionReply(asked, 'patient-a', 'one', 'Kavya', { ...reply, saidBy: '' }), asked);
  assert.equal(addOpenQuestionReply(asked, 'patient-a', 'one', '', reply), asked);
});

test('removal undo keeps the whole question and replies without duplicating or restoring another patient', () => {
  const entries = [question, other];
  const removed = removeOpenQuestion(entries, 'patient-a', 'one');
  assert.deepEqual(removed, [other]);
  assert.equal(restoreOpenQuestion(removed, 'patient-b', question), removed);
  const restored = restoreOpenQuestion(removed, 'patient-a', question);
  assert.equal(restored[1], question);
  assert.equal(restoreOpenQuestion(restored, 'patient-a', question), restored);
  const closed = setOpenQuestionClosed([question], 'patient-a', 'one', 'Kavya', true);
  assert.equal(closed[0].replies.length, 0);
  assert.ok(exportOpenQuestions(closed, 'patient-a').includes('No reply has been saved.'));
});
