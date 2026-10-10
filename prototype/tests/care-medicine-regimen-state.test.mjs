import test from 'node:test';
import assert from 'node:assert/strict';
import {
  MEDICINE_REGIMEN_STORAGE_KEY,
  addMedicineRegimen,
  loadMedicineRegimens,
  makeMedicineRegimen,
  medicineRegimenFallsOn,
  medicineRegimenId,
  patientMedicineRegimens,
  removeMedicineRegimen,
  reviseMedicineRegimen,
  saveMedicineRegimens,
  setMedicineRegimenStatus,
} from '../app/care-medicine-regimen-state.ts';

const png = (id, name = `${id}.png`) => ({
  id, name, type: 'image/png', size: 3, lastModified: 1,
  dataUrl: 'data:image/png;base64,YWJj',
});

const draft = {
  patientId: 'patient-a', sourceImportId: 'scan 42', sourceMedicineIndex: 0,
  prescriptionItemNumber: 3,
  medicineName: '  Amoxicillin  ', strength: ' 500 mg ', form: ' capsule ', quantity: ' 15 ',
  frequency: ' three times daily ', durationDays: null, instructions: '  Take exactly as prescribed. ',
  mealTiming: 'afterFood', times: ['20:00', '08:00', '20:00'], startDate: '2026-10-09', endDate: null,
  medicineImageId: 'scan 42:medicine:0',
  prescriptionPageIndexes: [2, 0],
  confidence: 0.91, uncertainties: ['Strength is faint', ' Strength is faint ', 'Quantity needs review'],
};
const created = { actor: ' Kavya ', at: '2026-10-09T10:00:00.000Z' };

test('builds a deterministic, serializable regimen with optional reminders and an ongoing end date', () => {
  assert.equal(medicineRegimenId('patient-a', 'scan 42', 0), 'medicine-regimen:patient-a:scan%2042:0');
  assert.equal(medicineRegimenId('', 'scan', 0), '');
  const item = makeMedicineRegimen(draft, created);
  assert.equal(item.id, medicineRegimenId(draft.patientId, draft.sourceImportId, 0));
  assert.equal(item.medicineName, 'Amoxicillin');
  assert.equal(item.prescriptionItemNumber, 3);
  assert.equal(item.durationDays, null);
  assert.deepEqual(item.times, ['20:00', '08:00']);
  assert.equal(item.endDate, null);
  assert.deepEqual(item.prescriptionPageIndexes, [0, 2]);
  assert.deepEqual(item.uncertainties, ['Strength is faint', 'Quantity needs review']);
  assert.equal(item.createdBy, 'Kavya');
  assert.equal(item.status, 'active');

  const withoutReminders = makeMedicineRegimen({ ...draft, sourceMedicineIndex: 1, times: [] }, created);
  assert.deepEqual(withoutReminders.times, []);
});

test('rejects unsafe or incomplete regimen data instead of silently repairing it', () => {
  for (const patch of [
    { medicineName: '' }, { sourceMedicineIndex: -1 }, { times: ['8am'] }, { startDate: '2026-02-30' },
    { endDate: '2026-10-08' }, { durationDays: 0 }, { prescriptionItemNumber: 1.5 },
    { durationDays: 5, endDate: null }, { durationDays: 5, endDate: '2026-10-12' },
    { mealTiming: 'unknown' }, { confidence: 1.01 },
    { prescriptionPageIndexes: [0, 0] },
    { medicineImageId: '' },
  ]) assert.equal(makeMedicineRegimen({ ...draft, ...patch }, created), null);
  assert.equal(makeMedicineRegimen(draft, { actor: '', at: created.at }), null);
  assert.equal(makeMedicineRegimen(draft, { actor: 'Kavya', at: 'not-a-time' }), null);
});

test('persists a reviewed printed item number and inclusive course duration', () => {
  const item = makeMedicineRegimen({
    ...draft,
    sourceMedicineIndex: 4,
    prescriptionItemNumber: 7,
    durationDays: 5,
    endDate: '2026-10-13',
  }, created);
  assert.equal(item.prescriptionItemNumber, 7);
  assert.equal(item.durationDays, 5);
  assert.equal(item.startDate, '2026-10-09');
  assert.equal(item.endDate, '2026-10-13');
});

test('add and revise are immutable, patient-scoped, and preserve provenance and creation audit', () => {
  const item = makeMedicineRegimen(draft, created);
  const added = addMedicineRegimen([], item);
  assert.notEqual(added[0], item);
  assert.notEqual(added[0].prescriptionPageIndexes, item.prescriptionPageIndexes);
  assert.equal(addMedicineRegimen(added, item), added);

  const edited = reviseMedicineRegimen(added, 'patient-a', item.id, {
    instructions: '  Updated verbatim directions. ', times: [], endDate: '2026-10-20',
  }, { actor: 'Meera', at: '2026-10-09T11:00:00Z' });
  assert.notEqual(edited, added);
  assert.equal(added[0].instructions, 'Take exactly as prescribed.');
  assert.equal(edited[0].instructions, 'Updated verbatim directions.');
  assert.deepEqual(edited[0].times, []);
  assert.equal(edited[0].createdBy, 'Kavya');
  assert.equal(edited[0].updatedBy, 'Meera');
  assert.deepEqual(edited[0].prescriptionPageIndexes, added[0].prescriptionPageIndexes);
  assert.notEqual(edited[0].prescriptionPageIndexes, added[0].prescriptionPageIndexes);
  assert.equal(reviseMedicineRegimen(added, 'patient-b', item.id, { medicineName: 'Wrong patient' }, created), added);
  assert.equal(reviseMedicineRegimen(added, 'patient-a', item.id, { times: ['25:00'] }, created), added);
});

test('completion, soft removal, restoration, date activity, and patient views are explicit', () => {
  const item = makeMedicineRegimen({ ...draft, endDate: '2026-10-11' }, created);
  const items = [item];
  assert.equal(medicineRegimenFallsOn(item, '2026-10-09'), true);
  assert.equal(medicineRegimenFallsOn(item, '2026-10-12'), false);

  const completed = setMedicineRegimenStatus(items, 'patient-a', item.id, 'completed', { actor: 'Kavya', at: '2026-10-11T20:00:00Z' });
  assert.equal(completed[0].status, 'completed');
  assert.equal(medicineRegimenFallsOn(completed[0], '2026-10-10'), false);
  const removed = removeMedicineRegimen(completed, 'patient-a', item.id, { actor: 'Meera', at: '2026-10-12T09:00:00Z' });
  assert.equal(removed[0].status, 'removed');
  assert.equal(removed[0].removedBy, 'Meera');
  assert.equal(patientMedicineRegimens(removed, 'patient-a').length, 0);
  assert.equal(patientMedicineRegimens(removed, 'patient-a', true).length, 1);

  const restored = setMedicineRegimenStatus(removed, 'patient-a', item.id, 'active', { actor: 'Kavya', at: '2026-10-12T10:00:00Z' });
  assert.equal(restored[0].status, 'active');
  assert.equal(restored[0].removedAt, null);
  assert.equal(restored[0].removedBy, null);
  assert.equal(removeMedicineRegimen(restored, 'patient-b', item.id, created), restored);
});

test('localStorage round-trips validated records and fails closed for corruption', () => {
  const data = new Map();
  const storage = {
    getItem(key) { return data.has(key) ? data.get(key) : null; },
    setItem(key, value) { data.set(key, value); },
  };
  const item = makeMedicineRegimen(draft, created);
  assert.equal(saveMedicineRegimens([item], storage), true);
  assert.ok(data.get(MEDICINE_REGIMEN_STORAGE_KEY).includes('Strength is faint'));
  const loaded = loadMedicineRegimens(storage);
  assert.deepEqual(loaded, [item]);
  assert.notEqual(loaded[0].prescriptionPageIndexes, item.prescriptionPageIndexes);

  const legacy = {
    ...item,
    medicineImage: png('strip-0'),
    prescriptionPages: [{ pageIndex: 2, image: png('page-2') }, { pageIndex: 0, image: png('page-0') }],
  };
  delete legacy.medicineImageId;
  delete legacy.prescriptionPageIndexes;
  delete legacy.durationDays;
  delete legacy.prescriptionItemNumber;
  data.set(MEDICINE_REGIMEN_STORAGE_KEY, JSON.stringify({ version: 1, value: [legacy] }));
  const migrated = loadMedicineRegimens(storage);
  assert.equal(migrated[0].durationDays, null);
  assert.equal(migrated[0].prescriptionItemNumber, null);
  assert.equal(migrated[0].medicineImageId, 'strip-0');
  assert.deepEqual(migrated[0].prescriptionPageIndexes, [0, 2]);
  assert.equal(JSON.parse(data.get(MEDICINE_REGIMEN_STORAGE_KEY)).version, 1);
  assert.equal(data.get(MEDICINE_REGIMEN_STORAGE_KEY).includes('dataUrl'), true);

  data.set(MEDICINE_REGIMEN_STORAGE_KEY, '{not json');
  assert.deepEqual(loadMedicineRegimens(storage), []);
  data.set(MEDICINE_REGIMEN_STORAGE_KEY, JSON.stringify({ version: 3, value: [item] }));
  assert.deepEqual(loadMedicineRegimens(storage), []);
  data.set(MEDICINE_REGIMEN_STORAGE_KEY, JSON.stringify({ version: 1, value: [{ ...item, times: ['noon'] }] }));
  assert.deepEqual(loadMedicineRegimens(storage), []);

  const before = data.get(MEDICINE_REGIMEN_STORAGE_KEY);
  assert.equal(saveMedicineRegimens([{ ...item, confidence: 2 }], storage), false);
  assert.equal(data.get(MEDICINE_REGIMEN_STORAGE_KEY), before);
});

test('a large medicine list keeps only compact shared image references in localStorage', () => {
  const data = new Map();
  const storage = {
    getItem(key) { return data.has(key) ? data.get(key) : null; },
    setItem(key, value) { data.set(key, value); },
  };
  const items = Array.from({ length: 15 }, (_, index) => makeMedicineRegimen({
    ...draft,
    sourceMedicineIndex: index,
    medicineName: `Medicine ${index + 1}`,
    medicineImageId: `scan 42:medicine:${index % 5}`,
  }, created));
  assert.equal(items.every(Boolean), true);
  assert.equal(saveMedicineRegimens(items, storage), true);
  const json = data.get(MEDICINE_REGIMEN_STORAGE_KEY);
  assert.ok(json.length < 50_000);
  assert.equal(json.includes('data:image'), false);
  assert.equal(json.includes('prescriptionPages'), false);
  assert.deepEqual(new Set(loadMedicineRegimens(storage).map((item) => item.sourceImportId)), new Set(['scan 42']));
});
