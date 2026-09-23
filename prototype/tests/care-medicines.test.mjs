import test from 'node:test';
import assert from 'node:assert/strict';
import { literalPrescriptionTime, makeMedicineEvent, medicinePrescription, prescriptionLines } from '../app/care-medicines-state.ts';
import { addCareEvent, updateCareEvent, careChecksAfterEventUpdate, setCareCheck } from '../app/care-calendar-state.ts';

const draft = { title: 'Medicine from prescription', instructions: 'Original instructions, unchanged.', time: '08:00', date: '2026-09-23', lastDay: '2026-09-25', reportId: 'rx-a', sourceName: 'Prescription.pdf', sourceText: 'Original instructions, unchanged.', checked: true };
const context = { id: 'medicine-a', patientId: 'patient-a', author: 'Kavya', checkedAt: '2026-09-23T08:00:00Z', reportIds: ['rx-a'] };

test('requires the prescription check and the current patient source', () => {
  assert.equal(makeMedicineEvent({ ...draft, checked: false }, context), null);
  assert.equal(makeMedicineEvent({ ...draft, reportId: 'other-patient-prescription' }, context), null);
  assert.equal(makeMedicineEvent({ ...draft, sourceName: '' }, context), null);
  assert.equal(makeMedicineEvent({ ...draft, reportId: '', sourceText: '' }, context), null);
  assert.equal(makeMedicineEvent(draft, { ...context, author: '' }), null);
  assert.ok(makeMedicineEvent({ ...draft, reportId: '', sourceText: 'Prescription copied exactly.' }, context));
});

test('validates time and course dates without choosing a dose or recurring period', () => {
  for (const patch of [{ time: '' }, { time: '25:00' }, { date: '2026-02-30' }, { lastDay: '2026-09-22' }, { instructions: '' }, { title: '' }]) {
    assert.equal(makeMedicineEvent({ ...draft, ...patch }, context), null);
  }
  const oneDay = makeMedicineEvent({ ...draft, lastDay: draft.date }, context);
  assert.equal(oneDay.repeatUntil, '');
  assert.equal(oneDay.instructions, draft.instructions);
  assert.equal(oneDay.time, draft.time);
});

test('prescription evidence survives the existing calendar add and update functions', () => {
  const medicine = makeMedicineEvent(draft, context);
  const events = addCareEvent([], medicine);
  assert.deepEqual(medicinePrescription(events[0]), { reportId: draft.reportId, sourceName: draft.sourceName, sourceText: draft.sourceText, checkedBy: context.author, checkedAt: context.checkedAt });
  const edited = makeMedicineEvent({ ...draft, time: '09:00' }, { ...context, author: 'Meera', checkedAt: '2026-09-23T08:30:00Z' }, events[0]);
  const updated = updateCareEvent(events, context.patientId, edited);
  assert.equal(updated[0].addedBy, 'Kavya');
  assert.equal(medicinePrescription(updated[0]).checkedBy, 'Meera');
  assert.equal(medicinePrescription(updated[0]).sourceText, draft.sourceText);
  assert.equal(makeMedicineEvent(draft, { ...context, patientId: 'patient-b' }, events[0]), null);
});

test('Taken is scoped to a scheduled date and editing instructions clears earlier checks', () => {
  const medicine = makeMedicineEvent(draft, context);
  const checks = setCareCheck([], [medicine], context.patientId, medicine.id, '2026-09-23', 'Kavya', context.checkedAt, '2026-09-23', true);
  assert.equal(checks.length, 1);
  assert.equal(setCareCheck(checks, [medicine], context.patientId, medicine.id, '2026-09-24', 'Kavya', context.checkedAt, '2026-09-23', true), checks);
  assert.deepEqual(setCareCheck(checks, [medicine], context.patientId, medicine.id, '2026-09-23', 'Kavya', context.checkedAt, '2026-09-23', false), []);
  const edited = makeMedicineEvent({ ...draft, instructions: 'Updated words from the prescription.' }, context, medicine);
  assert.deepEqual(careChecksAfterEventUpdate(checks, medicine, edited), []);
});

test('only a single explicitly written time can populate the clock field', () => {
  assert.equal(literalPrescriptionTime('Take at 08:30.'), '08:30');
  assert.equal(literalPrescriptionTime('08:30 PM as written.'), '20:30');
  assert.equal(literalPrescriptionTime('12:00 a.m.'), '00:00');
  assert.equal(literalPrescriptionTime('12:00 PM'), '12:00');
  for (const line of ['After breakfast', 'Twice daily', '08:00 and 20:00', '08:00–10:00', '25:00', '00:30 PM', '123:00']) assert.equal(literalPrescriptionTime(line), null);
});

test('line choices stay verbatim without generated medicine names or doses', () => {
  const text = '  First original line.  \nSecond original line.\n\nFirst original line.';
  assert.deepEqual(prescriptionLines(text), ['First original line.', 'Second original line.']);
  for (const line of prescriptionLines(text)) assert.ok(text.includes(line));
  assert.equal(medicinePrescription({ ...makeMedicineEvent(draft, context), kind: 'Test' }), null);
});
