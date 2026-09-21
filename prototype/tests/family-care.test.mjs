import test from 'node:test';
import assert from 'node:assert/strict';
import { addFamilyTask, setFamilyTaskCompleted } from '../app/family-care-state.ts';

const task = { id: 'one', patientId: 'patient-a', title: ' Arrange transport ', kind: 'Transport', owner: ' Family member ', due: '2026-09-20', createdBy: 'Family member', completedBy: null, appointmentId: 'visit-1' };

test('family task completion is confined to its patient and records the actor', () => {
  const tasks = [task, { ...task, id: 'two', patientId: 'patient-b' }];
  assert.deepEqual(setFamilyTaskCompleted(tasks, 'patient-b', 'one', 'Other family', true), tasks);
  const done = setFamilyTaskCompleted(tasks, 'patient-a', 'one', 'Patient A', true);
  assert.equal(done[0].completedBy, 'Patient A');
  assert.equal(done[1].completedBy, null);
  assert.equal(tasks[0].completedBy, null);
  assert.equal(setFamilyTaskCompleted(done, 'patient-a', 'one', 'Patient A', false)[0].completedBy, null);
});

test('task input rejects empty content and invalid dates without creating completed or duplicate tasks', () => {
  assert.deepEqual(addFamilyTask([], { ...task, title: ' ' }), []);
  assert.deepEqual(addFamilyTask([], { ...task, due: '2026-02-30' }), []);
  assert.deepEqual(addFamilyTask([task], { ...task, id: 'new' }), [task]);
  const added = addFamilyTask([], { ...task, completedBy: 'Forged completion' });
  assert.equal(added[0].title, 'Arrange transport');
  assert.equal(added[0].owner, 'Family member');
  assert.equal(added[0].completedBy, null);
});

test('questions can be undated and another patient can have a transport task for their visit', () => {
  const question = { ...task, id: 'question', kind: 'Question', due: '', appointmentId: undefined };
  assert.equal(addFamilyTask([], question).length, 1);
  const other = { ...task, id: 'other', patientId: 'patient-b' };
  assert.equal(addFamilyTask([task], other).length, 2);
});
