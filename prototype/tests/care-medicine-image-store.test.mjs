import test from 'node:test';
import assert from 'node:assert/strict';
import {
  deleteMedicineImageImport,
  loadMedicineImageFile,
  makeMedicineImageImport,
  medicineStoredImageId,
  saveMedicineImageImport,
  saveStandaloneMedicineImage,
  standaloneMedicineImageImportId,
} from '../app/care-medicine-image-store.ts';

const image = (name, contents = name) => new File([contents], name, {
  type: 'image/jpeg',
  lastModified: 10,
});

function memoryStore() {
  const imports = new Map();
  const images = new Map();
  return {
    imports,
    images,
    store: {
      async put(record, values) {
        if (imports.has(record.id)) throw new Error('duplicate import');
        imports.set(record.id, structuredClone(record));
        values.forEach((value) => images.set(value.id, structuredClone(value)));
      },
      async getImage(id) { return images.get(id) ?? null; },
      async delete(id) {
        const record = imports.get(id);
        [...(record?.prescriptionImageIds ?? []), ...(record?.medicineImageIds ?? [])]
          .forEach((imageId) => images.delete(imageId));
        imports.delete(id);
      },
    },
  };
}

test('creates one shared import with each prescription and strip image stored exactly once', () => {
  const value = makeMedicineImageImport({
    id: 'import-1', patientId: 'patient-a', createdAt: '2026-10-10T10:00:00Z',
    prescriptionImages: [image('rx-1.jpg'), image('rx-2.jpg')],
    medicineImages: [image('strip-1.jpg'), image('strip-2.jpg'), image('strip-3.jpg')],
  });
  assert.ok(value);
  assert.equal(value.images.length, 5);
  assert.equal(new Set(value.images.map((item) => item.id)).size, 5);
  assert.deepEqual(value.record.prescriptionImageIds, [
    'import-1:prescription:0', 'import-1:prescription:1',
  ]);
  assert.deepEqual(value.record.medicineImageIds, [
    'import-1:medicine:0', 'import-1:medicine:1', 'import-1:medicine:2',
  ]);
  assert.equal(medicineStoredImageId('import-1', 'medicine', 2), 'import-1:medicine:2');
});

test('saves, reads, and atomically removes a shared image import through the store boundary', async () => {
  const memory = memoryStore();
  const record = await saveMedicineImageImport({
    id: 'import-2', patientId: 'patient-a', createdAt: '2026-10-10T10:00:00Z',
    prescriptionImages: [image('rx.jpg', 'prescription')],
    medicineImages: [image('strip.jpg', 'medicine')],
  }, memory.store);
  assert.equal(memory.imports.size, 1);
  assert.equal(memory.images.size, 2);
  assert.equal(record.id, 'import-2');

  const restored = await loadMedicineImageFile('import-2:medicine:0', memory.store);
  assert.ok(restored instanceof File);
  assert.equal(restored.name, 'strip.jpg');
  assert.equal(await restored.text(), 'medicine');

  await deleteMedicineImageImport('import-2', memory.store);
  assert.equal(memory.imports.size, 0);
  assert.equal(memory.images.size, 0);
});

test('rejects invalid imports before calling IndexedDB', () => {
  assert.equal(makeMedicineImageImport({
    id: 'import-3', patientId: 'patient-a', createdAt: 'not-a-date',
    prescriptionImages: [image('rx.jpg')], medicineImages: [],
  }), null);
  assert.equal(makeMedicineImageImport({
    id: 'import-3', patientId: 'patient-a', createdAt: '2026-10-10T10:00:00Z',
    prescriptionImages: [], medicineImages: [],
  }), null);
});

test('stores an editor-added medicine photo once and returns its small reference', async () => {
  const memory = memoryStore();
  const saved = await saveStandaloneMedicineImage({
    id: 'medicine-photo-1234',
    patientId: 'patient-a',
    createdAt: '2026-10-10T10:00:00Z',
    file: image('new-strip.jpg', 'new medicine photo'),
  }, memory.store);

  assert.deepEqual(saved, {
    importId: 'medicine-photo-1234',
    imageId: 'medicine-photo-1234:medicine:0',
  });
  assert.equal(memory.images.size, 1);
  assert.equal(memory.imports.get(saved.importId).prescriptionImageIds.length, 0);
  assert.equal(standaloneMedicineImageImportId(saved.imageId), saved.importId);
  assert.equal(standaloneMedicineImageImportId('shared-ai-import:medicine:0'), null);
});
