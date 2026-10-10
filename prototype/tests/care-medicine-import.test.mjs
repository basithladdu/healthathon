import test from 'node:test';
import assert from 'node:assert/strict';
import {
  explicitTimes,
  mealTimingFromText,
  medicineCourseDurationDays,
  medicineCourseEndDate,
  organizeMedicineText,
  sortOrganizedMedicinesByPrescriptionOrder,
} from '../app/care-medicine-import-state.ts';

test('organizes prescription lines and matches visible medicine-strip words', () => {
  const result = organizeMedicineText([
    'Patient: Example\n1. Tab Metformin 500 mg\nTake at 08:00 AM and 08:00 PM after food\n2. Cap Omeprazole 20mg\nOnce daily before breakfast',
  ], [
    'METFORMIN TABLETS 500 mg',
    'OMEPRAZOLE CAPSULES 20 mg',
    'UNRELATED STRIP 10 mg',
  ]);

  assert.equal(result.medicines.length, 2);
  assert.equal(result.medicines[0].title, 'Metformin 500 mg');
  assert.deepEqual(result.medicines[0].times, ['08:00', '20:00']);
  assert.equal(result.medicines[0].mealTiming, 'after-food');
  assert.equal(result.medicines[0].medicineImageIndex, 0);
  assert.equal(result.medicines[1].mealTiming, 'before-food');
  assert.deepEqual(result.medicines[1].times, []);
  assert.equal(result.medicines[1].medicineImageIndex, 1);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [2]);
});

test('only explicit clock values become reminder times', () => {
  assert.deepEqual(explicitTimes('Take twice daily, morning and night.'), []);
  assert.deepEqual(explicitTimes('At 7 am, 13:30 and 08:15 PM.'), ['07:00', '13:30', '20:15']);
  assert.deepEqual(explicitTimes('Dose pattern 1-0-1.'), []);
});

test('meal timing is copied from visible wording without guessing', () => {
  assert.equal(mealTimingFromText('Take on an empty stomach'), 'before-food');
  assert.equal(mealTimingFromText('Use with dinner'), 'with-food');
  assert.equal(mealTimingFromText('Take after meals'), 'after-food');
  assert.equal(mealTimingFromText('Take once daily'), 'not-stated');
});

test('rejects OCR noise instead of presenting it as a matched prescription medicine', () => {
  const result = organizeMedicineText(['| :: 2 Ze g EEE'], ['Pulmoclear tablets']);
  assert.equal(result.medicines.length, 0);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0]);
  assert.match(result.warnings[0], /not clear enough/iu);
});

test('weak shared OCR fragments cannot create a prescription-to-strip match', () => {
  const result = organizeMedicineText(['Tab MedicineAlpha 20 mg\nTake once daily'], ['MedicineBeta 20 mg']);
  assert.equal(result.medicines[0].medicineImageIndex, -1);
  assert.deepEqual(result.unmatchedMedicineImageIndexes, [0]);
});

test('computes inclusive prescription course dates without timezone drift', () => {
  assert.equal(medicineCourseEndDate('2026-10-09', 1), '2026-10-09');
  assert.equal(medicineCourseEndDate('2026-10-09', 5), '2026-10-13');
  assert.equal(medicineCourseEndDate('2026-02-30', 5), null);
  assert.equal(medicineCourseEndDate('2026-10-09', 0), null);
  assert.equal(medicineCourseDurationDays('2026-10-09', '2026-10-13'), 5);
  assert.equal(medicineCourseDurationDays('2026-10-13', '2026-10-09'), null);
});

test('sorts medicines by printed prescription item number with unnumbered entries last', () => {
  const result = sortOrganizedMedicinesByPrescriptionOrder([
    { id: 'unknown-a', prescriptionItemNumber: null },
    { id: 'third', prescriptionItemNumber: 3 },
    { id: 'first', prescriptionItemNumber: 1 },
    { id: 'unknown-b', prescriptionItemNumber: null },
    { id: 'third-again', prescriptionItemNumber: 3 },
  ]);
  assert.deepEqual(result.map((item) => item.id), ['first', 'third', 'third-again', 'unknown-a', 'unknown-b']);
});
