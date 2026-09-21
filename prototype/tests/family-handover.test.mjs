import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addHandoverContact, buildHandoverPack, DEFAULT_HANDOVER_SELECTION, handoverFileName,
  removeHandoverContact, restoreHandoverContact, updateHandoverContact,
} from '../app/family-handover-state.ts';

const note = { version: 3, physician: 'Dr. Sujay H S', releasedAt: '2026-09-20T13:15:00+05:30', fields: [{ label: 'What matters', value: '  The patient said:\n“I want to be at home.”  ' }] };
const contact = { id: 'contact-a', patientId: 'patient-a', name: 'Kavya', relationship: 'Daughter', phone: '+91 98765 43210' };
const otherContact = { ...contact, id: 'contact-b', patientId: 'patient-b', name: 'Another patient’s relative' };
const input = { patientId: 'patient-a', patientName: 'Meera', author: 'Family member', note, calendarText: 'Private family checklist\nBring prescription', reportNames: ['Scan.pdf'], contacts: [contact, otherContact], selection: { ...DEFAULT_HANDOVER_SELECTION } };

test('the default pack contains only the reviewed note, preserving its wording and version', () => {
  const result = buildHandoverPack(input);
  assert.ok(result.includes(note.fields[0].value));
  assert.ok(result.includes(`Version: ${note.version}`));
  assert.ok(result.includes(`Doctor: ${note.physician}`));
  assert.ok(result.includes(note.releasedAt));
  assert.ok(!result.includes('Private family checklist'));
  assert.ok(!result.includes('Scan.pdf'));
  assert.ok(!result.includes('Kavya'));
  assert.ok(!result.includes('FAMILY-ADDED INFORMATION'));
});

test('family selections remain clearly separate and never include another patient’s contacts', () => {
  const result = buildHandoverPack({ ...input, selection: { note: true, checklist: true, reports: true, contacts: true } });
  assert.ok(result.includes('FAMILY-ADDED INFORMATION'));
  assert.ok(result.includes('Not part of the doctor-reviewed note.'));
  assert.ok(result.includes(input.calendarText));
  assert.ok(result.includes('FILES ARE NOT ATTACHED'));
  assert.ok(result.includes('Scan.pdf'));
  assert.ok(result.includes('Kavya'));
  assert.ok(!result.includes(otherContact.name));
  assert.deepEqual(note.fields, [{ label: 'What matters', value: '  The patient said:\n“I want to be at home.”  ' }]);
});

test('turning off a section removes it, and an empty selection cannot produce a misleading pack', () => {
  const result = buildHandoverPack({ ...input, selection: { note: false, checklist: true, reports: false, contacts: false } });
  assert.ok(result.includes(input.calendarText));
  assert.ok(!result.includes('DOCTOR-REVIEWED CARE NOTE'));
  assert.ok(!result.includes(note.physician));
  assert.equal(buildHandoverPack({ ...input, note: null }), null);
  assert.equal(buildHandoverPack({ ...input, selection: { note: false, checklist: false, reports: false, contacts: false } }), null);
  assert.equal(buildHandoverPack({ ...input, patientId: '' }), null);
});

test('a later reviewed note replaces the export contents and file version without mutating the old note', () => {
  const later = { ...note, version: 4, physician: 'Dr. Kutty', fields: [{ label: 'Visit note', value: 'New conversation\nExact reviewed wording.' }] };
  const result = buildHandoverPack({ ...input, note: later });
  assert.ok(result.includes('Version: 4'));
  assert.ok(result.includes('Doctor: Dr. Kutty'));
  assert.ok(result.includes(later.fields[0].value));
  assert.ok(!result.includes(note.fields[0].value));
  assert.equal(note.version, 3);
  assert.equal(handoverFileName('patient-a', 4), 'saanthvana-patient-a-note-v4-care-pack.txt');
  assert.equal(handoverFileName('patient-a', null), 'saanthvana-patient-a-care-pack.txt');
});

test('contact creation and editing reject cross-patient changes and leave other patients untouched', () => {
  const contacts = [contact, otherContact];
  assert.deepEqual(addHandoverContact(contacts, 'patient-a', { ...otherContact, id: 'new' }), contacts);
  assert.deepEqual(addHandoverContact(contacts, 'patient-a', contact), contacts);
  assert.deepEqual(addHandoverContact(contacts, 'patient-a', { ...contact, id: 'empty', name: '  ' }), contacts);
  const added = addHandoverContact(contacts, 'patient-a', { ...contact, id: 'new', name: ' Transport helper ' });
  assert.equal(added[2].name, 'Transport helper');
  assert.equal(contacts.length, 2);
  assert.deepEqual(updateHandoverContact(contacts, 'patient-b', { ...contact, name: 'Wrong family' }), contacts);
  assert.deepEqual(updateHandoverContact(contacts, 'patient-a', { ...contact, id: otherContact.id, name: 'Wrong family' }), contacts);
  const updated = updateHandoverContact(contacts, 'patient-a', { ...contact, phone: ' 12345 ' });
  assert.equal(updated[0].phone, '12345');
  assert.equal(updated[1], otherContact);
  assert.equal(contact.phone, '+91 98765 43210');
});

test('remove and undo are patient-scoped, preserve order and do not restore duplicate contacts', () => {
  const contacts = [contact, otherContact];
  assert.deepEqual(removeHandoverContact(contacts, 'patient-b', contact.id), contacts);
  const removed = removeHandoverContact(contacts, 'patient-a', contact.id);
  assert.deepEqual(removed, [otherContact]);
  assert.deepEqual(restoreHandoverContact(removed, 'patient-a', contact, 0), contacts);
  assert.deepEqual(restoreHandoverContact(removed, 'patient-b', contact, 0), removed);
  assert.deepEqual(restoreHandoverContact(contacts, 'patient-a', contact, 0), contacts);
});
