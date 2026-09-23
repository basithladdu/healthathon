import test from 'node:test';
import assert from 'node:assert/strict';
import { addFamilyTask, editFamilyTask, removeFamilyTask, restoreFamilyTask, setFamilyTaskCompleted } from '../app/family-care-state.ts';

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

test('editing changes the task details without changing patient, creator or appointment links', () => {
  const original = addFamilyTask([], task)[0];
  const tasks = [original, { ...original, id: 'other', patientId: 'patient-b' }];
  const updated = { ...original, title: ' Collect reports ', owner: ' Kavya Raghavan ', due: '2026-09-24', kind: 'Paperwork', createdBy: 'Different person', appointmentId: 'other-visit', completedBy: 'Forged completion' };
  const next = editFamilyTask(tasks, 'patient-a', updated);
  assert.equal(next[0].title, 'Collect reports');
  assert.equal(next[0].owner, 'Kavya Raghavan');
  assert.equal(next[0].kind, 'Paperwork');
  assert.equal(next[0].due, '2026-09-24');
  assert.equal(next[0].createdBy, original.createdBy);
  assert.equal(next[0].appointmentId, original.appointmentId);
  assert.equal(next[0].completedBy, null);
  assert.equal(next[1], tasks[1]);
  assert.equal(tasks[0].title, original.title);
  assert.equal(editFamilyTask(tasks, 'patient-b', updated), tasks);
  assert.equal(editFamilyTask(tasks, 'patient-a', { ...updated, patientId: 'patient-b' }), tasks);
});

test('invalid edits and duplicate appointment tasks do not change existing records', () => {
  const original = addFamilyTask([], task)[0];
  const tasks = [original, { ...original, id: 'medicine-task', kind: 'Medicines' }];
  for (const patch of [{ title: '' }, { owner: '' }, { due: '2026-02-30' }, { kind: 'Unrecognised' }, { kind: 'Medicines' }]) {
    assert.equal(editFamilyTask(tasks, 'patient-a', { ...original, ...patch }), tasks);
  }
  assert.equal(editFamilyTask(tasks, 'patient-a', { ...original }), tasks);
});

test('changing a completed task reopens it, while a no-change edit keeps its completion', () => {
  const original = { ...addFamilyTask([], task)[0], completedBy: 'Kavya' };
  const tasks = [original];
  assert.equal(editFamilyTask(tasks, 'patient-a', { ...original }), tasks);
  for (const patch of [{ title: 'Arrange another ride' }, { owner: 'Ravi' }, { due: '2026-09-26' }, { kind: 'Family support' }]) {
    assert.equal(editFamilyTask(tasks, 'patient-a', { ...original, ...patch })[0].completedBy, null);
  }
});

test('remove and undo preserve the complete record without crossing patients or duplicating it', () => {
  const original = { ...addFamilyTask([], task)[0], completedBy: 'Kavya' };
  const tasks = [original, { ...original, id: 'other', patientId: 'patient-b' }];
  assert.equal(removeFamilyTask(tasks, 'patient-b', original.id), tasks);
  const removed = removeFamilyTask(tasks, 'patient-a', original.id);
  assert.equal(removed.length, 1);
  assert.equal(restoreFamilyTask(removed, 'patient-b', original), removed);
  const restored = restoreFamilyTask(removed, 'patient-a', original);
  assert.deepEqual(restored.find((item) => item.id === original.id), original);
  assert.equal(restoreFamilyTask(restored, 'patient-a', original), restored);
  assert.deepEqual(tasks[0], original);
});

test('a completed task can be restored alongside a newer open task for that appointment', () => {
  const completed = { ...addFamilyTask([], task)[0], completedBy: 'Kavya' };
  const newer = { ...completed, id: 'newer', completedBy: null };
  const restored = restoreFamilyTask([newer], 'patient-a', completed);
  assert.equal(restored.length, 2);
  assert.equal(restored[1].completedBy, 'Kavya');
  assert.equal(restored[1].appointmentId, 'visit-1');
});
