import test from 'node:test';
import assert from 'node:assert/strict';
import { CareMedicineAiError, medicineImageRequestItem, sanitizeMedicineAiResponse } from '../app/care-medicine-ai.ts';

test('sends image bytes in the flat contract accepted by the organizer function', () => {
  const item = medicineImageRequestItem('strip.jpg', { data: 'AQ==', mimeType: 'image/jpeg' });
  assert.deepEqual(item, { name: 'strip.jpg', data: 'AQ==', mimeType: 'image/jpeg' });
  assert.equal('inlineData' in item, false);
});

test('sanitizes the structured medicine response and computes unused strip photos', () => {
  const result = sanitizeMedicineAiResponse({
    medicines: [
      {
        medicineName: ' Amoxicillin ', strength: ' 500 mg ', form: ' capsule ', quantity: ' 15 capsules ',
        frequency: ' twice daily ', suggestedTimes: ['8:00 AM', '08:00 pm', '25:00 PM', '8:00 AM'],
        mealTiming: 'afterFood', instructions: ' Finish the prescribed course. ', confidence: 1.4,
        uncertainties: ['Quantity is faint', 'Quantity is faint'], medicineImageIndex: 1,
        prescriptionPageIndexes: [1, 0, 99, 1],
      },
      {
        medicineName: 'amoxicillin', strength: '500 MG', form: '', quantity: '', frequency: '',
        suggestedTimes: [], mealTiming: 'anytime', instructions: '', confidence: 0.2,
        uncertainties: [], medicineImageIndex: 0, prescriptionPageIndexes: [],
      },
      {
        medicineName: 'Omeprazole', strength: '20 mg', form: 'tablet', quantity: '',
        frequency: 'once daily', suggestedTimes: ['7:30 AM'], mealTiming: 'beforeFood',
        instructions: '', confidence: 0.62, uncertainties: ['Strip photo is missing'],
        medicineImageIndex: 20, prescriptionPageIndexes: [0],
      },
    ],
    unmatchedMedicineImageIndexes: [2, 200],
    warnings: ['Check the faint quantity', 'Check the faint quantity'],
  }, 2, 4);

  assert.equal(result.medicines.length, 2);
  assert.deepEqual(result.medicines[0].times, ['08:00', '20:00']);
  assert.equal(result.medicines[0].mealTiming, 'after-food');
  assert.equal(result.medicines[0].confidence, 1);
  assert.deepEqual(result.medicines[0].prescriptionPageIndexes, [1, 0]);
  assert.deepEqual(result.medicines[0].uncertainties, ['Quantity is faint']);
  assert.equal(result.medicines[1].medicineImageIndex, -1);
  assert.equal(result.medicines[1].matchQuality, 'prescription-only');
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0, 2, 3]);
  assert.deepEqual(result.warnings, ['Check the faint quantity']);
});

test('fails closed for blank, invalid, or missing model output', () => {
  for (const value of ['', '{broken', {}, { medicines: 'not a list' }]) {
    assert.throws(() => sanitizeMedicineAiResponse(value, 1, 1), CareMedicineAiError);
  }
});

test('never uses a strip photo as prescription evidence', () => {
  const result = sanitizeMedicineAiResponse({
    medicines: [{
      medicineName: 'Pulmoclear', strength: '', form: '', quantity: '', frequency: '', suggestedTimes: [],
      mealTiming: 'anytime', instructions: '', confidence: 0.3, uncertainties: ['Prescription wording is unclear'],
      medicineImageIndex: 0, prescriptionPageIndexes: [],
    }],
    unmatchedMedicineImageIndexes: [], warnings: [],
  }, 1, 1);
  assert.equal(result.medicines[0].sourceText, 'Pulmoclear');
  assert.equal(result.medicines[0].medicineImageIndex, -1);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0]);
  assert.match(result.medicines[0].uncertainties.join(' '), /prescription page/iu);
  assert.doesNotMatch(result.medicines[0].sourceText, /strip|package|ocr/iu);
});

test('keeps safe clock variants, explicit duration, and printed prescription order', () => {
  const result = sanitizeMedicineAiResponse({
    medicines: [
      {
        medicineName: 'Pulmoclear', strength: '', form: 'tablet', quantity: '1 tablet',
        frequency: 'Every 12 hours', suggestedTimes: ['8AM', '20:00', '8', '24:00'],
        mealTiming: 'anytime', instructions: 'For 5 days', durationDays: 5,
        prescriptionItemNumber: 12, confidence: 0.95, uncertainties: [],
        medicineImageIndex: 1, prescriptionPageIndexes: [0],
      },
      {
        medicineName: 'Holirose', strength: '40 mg', form: 'tablet', quantity: '1 tablet',
        frequency: 'Every 24 hours', suggestedTimes: ['9 PM'], mealTiming: 'anytime',
        instructions: '', durationDays: 0, prescriptionItemNumber: 2, confidence: 0.98,
        uncertainties: [], medicineImageIndex: 0, prescriptionPageIndexes: [1],
      },
    ],
    unmatchedMedicineImageIndexes: [], warnings: [],
  }, 2, 2);

  assert.deepEqual(result.medicines.map((item) => item.title), ['Holirose', 'Pulmoclear']);
  assert.deepEqual(result.medicines[0].times, ['21:00']);
  assert.equal(result.medicines[0].durationDays, null);
  assert.deepEqual(result.medicines[1].times, ['08:00', '20:00']);
  assert.equal(result.medicines[1].durationDays, 5);
  assert.equal(result.medicines[1].prescriptionItemNumber, 12);
});

test('allows only one medicine to claim a strip image and flags unsafe matches', () => {
  const result = sanitizeMedicineAiResponse({
    medicines: [
      {
        medicineName: 'Medicine A', strength: '5 mg', form: 'tablet', quantity: '', frequency: '',
        suggestedTimes: [], mealTiming: 'anytime', instructions: '', durationDays: 0,
        prescriptionItemNumber: 1, confidence: 0.9, uncertainties: [], medicineImageIndex: 0,
        prescriptionPageIndexes: [0],
      },
      {
        medicineName: 'Medicine B', strength: '10 mg', form: 'tablet', quantity: '', frequency: '',
        suggestedTimes: [], mealTiming: 'anytime', instructions: '', durationDays: 0,
        prescriptionItemNumber: 2, confidence: 0.9, uncertainties: [], medicineImageIndex: 0,
        prescriptionPageIndexes: [0],
      },
      {
        medicineName: 'Medicine C', strength: '', form: '', quantity: '', frequency: '',
        suggestedTimes: [], mealTiming: 'anytime', instructions: '', durationDays: 0,
        prescriptionItemNumber: 3, confidence: 0.8, uncertainties: [], medicineImageIndex: 9,
        prescriptionPageIndexes: [0],
      },
    ],
    unmatchedMedicineImageIndexes: [], warnings: [],
  }, 1, 1);

  assert.equal(result.medicines[0].medicineImageIndex, 0);
  assert.equal(result.medicines[1].medicineImageIndex, -1);
  assert.equal(result.medicines[1].confidence, 0.49);
  assert.match(result.medicines[1].uncertainties.join(' '), /also matched/iu);
  assert.equal(result.medicines[2].medicineImageIndex, -1);
  assert.match(result.medicines[2].uncertainties.join(' '), /invalid strip-photo/iu);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, []);
});

test('keeps a different package brand in manual-review quality even at high model confidence', () => {
  const result = sanitizeMedicineAiResponse({
    medicines: [{
      medicineName: 'IVABRAD', strength: '5 mg', form: 'tablet', quantity: '1 tablet',
      frequency: 'Every 12 hours', suggestedTimes: ['8AM', '8PM'], mealTiming: 'anytime',
      instructions: '', durationDays: 0, prescriptionItemNumber: 4,
      identityMatch: 'genericEquivalent', confidence: 0.94, uncertainties: [],
      medicineImageIndex: 0, prescriptionPageIndexes: [0],
    }],
    unmatchedMedicineImageIndexes: [], warnings: [],
  }, 1, 1);

  assert.equal(result.medicines[0].confidence, 0.74);
  assert.equal(result.medicines[0].matchQuality, 'possible');
  assert.match(result.medicines[0].uncertainties.join(' '), /different brand/iu);
});
