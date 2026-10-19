import test from 'node:test';
import assert from 'node:assert/strict';
import { copiedPrescriptionPages } from '../app/care-prescription-local-state.ts';
import { findCareDocumentText } from '../app/care-document-state.ts';

test('copied prescription candidates retain page position without asserting a packaging match', () => {
  const result = copiedPrescriptionPages(['Tab Example 500 mg\nTake at 08:30 AM after food', 'Cap Fictional 20 mg\nTwice daily'], 2);
  assert.equal(result.medicines.length, 2);
  assert.deepEqual(result.medicines.map((item) => item.prescriptionPageIndexes), [[0], [1]]);
  assert.ok(result.medicines.every((item) => item.medicineImageIndex === -1 && item.matchQuality === 'prescription-only' && item.confidence === null));
  assert.deepEqual(result.medicines[0].times, ['08:30']);
  assert.deepEqual(result.medicines[1].times, []);
  assert.ok(result.medicines.every((item) => !item.quantity && !item.strength && item.durationDays === null));
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0, 1]);
});
test('blank or unrelated text does not turn packaging into a medicine or treatment', () => {
  const result = copiedPrescriptionPages(['Hospital visit\nPatient Fictional\nDiagnosis not recorded'], 3);
  assert.deepEqual(result.medicines, []);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0, 1, 2]);
  assert.throws(() => copiedPrescriptionPages([], 0));
  assert.throws(() => copiedPrescriptionPages(['x'.repeat(40_001)], 0));
});
test('OCR page passages stay exact, patient scoped and separate from retained original reading', () => {
  const report = { id: 'report', patientId: 'a', date: '2026-10-19', addedBy: 'Reviewer', file: new File(['original'], 'page.png', { type: 'image/png' }) };
  const document = { reportId: 'report', patientId: 'a', category: 'other', textSource: 'image-ocr', originalText: '[Page 1]\nAn original reading', text: '[Page 1]\nFollowup appointment 24 October\nBring the prescription', recordedBy: 'Reviewer', reviewedAt: '2026-10-19T10:00:00Z' };
  const found = findCareDocumentText('bring prescription', 'a', [report], [document]);
  assert.equal(found.length, 1);
  assert.equal(found[0].excerpts[0].page, 1);
  assert.equal(found[0].excerpts[0].text, document.text.split('\n').slice(found[0].excerpts[0].line - 1, found[0].excerpts[0].line - 1 + found[0].excerpts[0].text.split('\n').length).join('\n'));
  assert.equal(document.originalText, '[Page 1]\nAn original reading');
  assert.deepEqual(findCareDocumentText('bring prescription', 'b', [report], [document]), []);
  assert.deepEqual(findCareDocumentText('cure cancer', 'a', [report], [document]), []);
});
