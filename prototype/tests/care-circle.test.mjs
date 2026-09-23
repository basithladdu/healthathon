import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createInitialCareCircle, canFamilyOpenView, addCareCircleMember, setCareCirclePermission,
  setCareCircleMemberActive, addCareCircleUpdate, visibleCareCircleUpdates, acknowledgeCareCircleUpdate,
} from '../app/care-circle-state.ts';

const patientId = 'CANCER-20418';
const at = '2026-09-23T10:15:32.000Z';
const patient = { patientId, actor: 'Meera Raghavan', role: 'patient', at };
const family = { patientId, actor: 'Kavya Raghavan', role: 'family', at };
const doctor = { patientId, actor: 'Dr Sujay', role: 'doctor', at };
const audience = { patient: true, careTeam: false, memberIds: ['meera-circle-kavya'] };
const post = (state, input = {}) => addCareCircleUpdate(state, { ...patient, id: 'update-1', text: 'Kavya will bring the reports.', audience, ...input });

test('initial people exist for Meera only, costs start off, and no fake updates or read receipts exist', () => {
  const state = createInitialCareCircle();
  assert.equal(state.members.length, 2);
  assert.deepEqual(state.updates, []);
  assert.deepEqual(state.audit, []);
  assert.equal(canFamilyOpenView(state, patientId, 'Kavya Raghavan', 'reports'), true);
  assert.equal(canFamilyOpenView(state, patientId, 'Arun Raghavan', '/care-note/history'), true);
  assert.equal(canFamilyOpenView(state, patientId, 'Kavya Raghavan', 'costs'), false);
  assert.equal(canFamilyOpenView(state, 'another-patient', 'Kavya Raghavan', 'reports'), false);
  state.members[0].permissions.reports = false;
  assert.equal(createInitialCareCircle().members[0].permissions.reports, true);
  assert.equal(state.members[1].permissions.reports, true);
});

test('known sensitive routes follow grants and unknown/private administrative routes are denied', () => {
  const state = createInitialCareCircle();
  for (const view of ['reports', 'document-search', '/find-in-documents', 'lab-history', 'clinical-documents', 'doctor-pack', 'my-plan', 'note-history', 'calendar', 'family-tasks']) {
    assert.equal(canFamilyOpenView(state, patientId, family.actor, view), true, view);
    assert.equal(canFamilyOpenView(state, patientId, 'Stranger', view), false, view);
  }
  for (const view of ['home', '/home/', 'daily-care', 'care-circle', 'journal', 'voice-journal']) assert.equal(canFamilyOpenView(state, patientId, 'Stranger', view), true, view);
  for (const view of ['my-details', 'consent', '/doctor', 'unknown-feature', 'workspace/patients']) assert.equal(canFamilyOpenView(state, patientId, family.actor, view), false, view);
});

test('combined routes require every grant needed by their embedded source material', () => {
  const state = createInitialCareCircle();
  assert.equal(canFamilyOpenView(state, patientId, family.actor, '/medicines'), true);
  assert.equal(canFamilyOpenView(state, patientId, family.actor, 'cancer-overview'), true);
  const noCalendar = setCareCirclePermission(state, { ...patient, memberId: 'meera-circle-kavya', permission: 'calendar', allowed: false });
  assert.equal(canFamilyOpenView(noCalendar, patientId, family.actor, 'medicines'), false);
  const noReports = setCareCirclePermission(state, { ...patient, memberId: 'meera-circle-kavya', permission: 'reports', allowed: false });
  assert.equal(canFamilyOpenView(noReports, patientId, family.actor, '/medicines/'), false);
  assert.equal(canFamilyOpenView(noReports, patientId, family.actor, 'calendar'), true);
  assert.equal(canFamilyOpenView(noCalendar, patientId, family.actor, 'reports'), true);
  for (const permission of ['careNote', 'reports']) {
    const restricted = setCareCirclePermission(state, { ...patient, memberId: 'meera-circle-kavya', permission, allowed: false });
    assert.equal(canFamilyOpenView(restricted, patientId, family.actor, 'cancer-overview'), false);
    assert.equal(canFamilyOpenView(restricted, patientId, family.actor, '/my-cancer-care'), false);
  }
});

test('only patient actions can change permission, with patient isolation and a precise audit trail', () => {
  const original = createInitialCareCircle();
  const input = { memberId: 'meera-circle-kavya', permission: 'reports', allowed: false };
  assert.strictEqual(setCareCirclePermission(original, { ...family, ...input }), original);
  assert.strictEqual(setCareCirclePermission(original, { ...doctor, ...input }), original);
  assert.strictEqual(setCareCirclePermission(original, { ...patient, patientId: 'another-patient', ...input }), original);
  const updated = setCareCirclePermission(original, { ...patient, ...input });
  assert.equal(canFamilyOpenView(updated, patientId, family.actor, 'reports'), false);
  assert.equal(canFamilyOpenView(updated, patientId, family.actor, 'calendar'), true);
  assert.equal(original.members[0].permissions.reports, true);
  assert.deepEqual(updated.audit[0], {
    id: `access-changed-meera-circle-kavya-${at}-0`, patientId, actor: patient.actor, at,
    action: 'access-changed', targetId: 'meera-circle-kavya', targetName: family.actor, permission: 'reports', allowed: false,
  });
  assert.strictEqual(setCareCirclePermission(updated, { ...patient, ...input }), updated);
});

test('removing access immediately hides sensitive routes and updates, restoring preserves patient-chosen grants', () => {
  const state = post(createInitialCareCircle());
  assert.equal(visibleCareCircleUpdates(state, patientId, family.actor, 'family').length, 1);
  const revoked = setCareCircleMemberActive(state, { ...patient, memberId: 'meera-circle-kavya', active: false });
  assert.equal(canFamilyOpenView(revoked, patientId, family.actor, 'calendar'), false);
  assert.deepEqual(visibleCareCircleUpdates(revoked, patientId, family.actor, 'family'), []);
  assert.strictEqual(acknowledgeCareCircleUpdate(revoked, { ...family, updateId: 'update-1' }), revoked);
  const restored = setCareCircleMemberActive(revoked, { ...patient, memberId: 'meera-circle-kavya', active: true });
  assert.equal(canFamilyOpenView(restored, patientId, family.actor, 'reports'), true);
  assert.equal(canFamilyOpenView(restored, patientId, family.actor, 'costs'), false);
  assert.deepEqual(restored.audit.slice(-2).map((entry) => entry.action), ['member-revoked', 'member-restored']);
});

test('new people require two fields, reject duplicates/self, preserve other patients and cannot be added by family', () => {
  const state = createInitialCareCircle();
  const input = { id: 'new-person', name: ' Tara  Raghavan ', relationship: ' Sister ' };
  assert.strictEqual(addCareCircleMember(state, { ...family, ...input }), state);
  assert.strictEqual(addCareCircleMember(state, { ...patient, ...input, name: patient.actor }), state);
  assert.strictEqual(addCareCircleMember(state, { ...patient, ...input, relationship: '' }), state);
  assert.strictEqual(addCareCircleMember(state, { ...patient, ...input, name: ' kavya   Raghavan ' }), state);
  const next = addCareCircleMember(state, { ...patient, ...input });
  assert.equal(next.members[2].name, 'Tara Raghavan');
  assert.equal(next.members[2].permissions.costs, false);
  assert.strictEqual(next.members[0], state.members[0]);
  assert.equal(next.audit[0].action, 'member-added');
  const another = addCareCircleMember(next, { ...patient, patientId: 'other', id: 'other-person', name: 'Tara Raghavan', relationship: 'Friend' });
  assert.equal(another.members.length, 4);
});

test('updates are visible only to the author and selected recipients, never other patients or unselected people', () => {
  let state = post(createInitialCareCircle());
  assert.equal(visibleCareCircleUpdates(state, patientId, patient.actor, 'patient').length, 1);
  assert.equal(visibleCareCircleUpdates(state, patientId, family.actor, 'family').length, 1);
  assert.deepEqual(visibleCareCircleUpdates(state, patientId, 'Arun Raghavan', 'family'), []);
  assert.deepEqual(visibleCareCircleUpdates(state, patientId, doctor.actor, 'doctor'), []);
  assert.deepEqual(visibleCareCircleUpdates(state, 'other', patient.actor, 'patient'), []);
  state = post(state, { ...family, id: 'private-family', text: 'Question for the care team.', audience: { patient: false, careTeam: true, memberIds: [] } });
  assert.equal(visibleCareCircleUpdates(state, patientId, patient.actor, 'patient').length, 1);
  assert.equal(visibleCareCircleUpdates(state, patientId, family.actor, 'family').length, 2);
  assert.equal(visibleCareCircleUpdates(state, patientId, doctor.actor, 'doctor')[0].id, 'private-family');
});

test('audiences cannot name foreign or inactive members and only active family can add updates', () => {
  const state = createInitialCareCircle();
  assert.strictEqual(post(state, { ...family, actor: 'Stranger' }), state);
  assert.strictEqual(post(state, { audience: { ...audience, memberIds: ['not-a-member'] } }), state);
  assert.strictEqual(post(state, { text: ' ' }), state);
  assert.strictEqual(post(state, { text: 'a'.repeat(1201) }), state);
  assert.strictEqual(post(state, { at: 'not-a-date' }), state);
  const deduped = post(state, { audience: { ...audience, memberIds: ['meera-circle-kavya', 'meera-circle-kavya'] } });
  assert.deepEqual(deduped.updates[0].audience.memberIds, ['meera-circle-kavya']);
});

test('reading is recorded only after the selected person acts, once, with actor/time and no invented receipts', () => {
  const state = post(createInitialCareCircle());
  assert.deepEqual(state.updates[0].reads, []);
  assert.strictEqual(acknowledgeCareCircleUpdate(state, { ...patient, updateId: 'update-1' }), state);
  assert.strictEqual(acknowledgeCareCircleUpdate(state, { ...family, actor: 'Arun Raghavan', updateId: 'update-1' }), state);
  const read = acknowledgeCareCircleUpdate(state, { ...family, updateId: 'update-1' });
  assert.deepEqual(read.updates[0].reads, [{ actor: family.actor, role: 'family', at }]);
  assert.deepEqual(state.updates[0].reads, []);
  assert.strictEqual(acknowledgeCareCircleUpdate(read, { ...family, updateId: 'update-1' }), read);
  assert.equal(read.audit.at(-1).action, 'update-read');
  assert.equal(read.audit.at(-1).actor, family.actor);
});
