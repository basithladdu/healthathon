'use client';

export const MEDICINE_IMAGE_DATABASE_NAME = 'saanthvana-medicine-images';
export const MEDICINE_IMAGE_DATABASE_VERSION = 1;

const IMAGE_STORE = 'images';
const IMPORT_STORE = 'imports';
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

export type MedicineStoredImage = {
  id: string;
  importId: string;
  kind: 'prescription' | 'medicine';
  index: number;
  name: string;
  type: (typeof IMAGE_TYPES)[number];
  size: number;
  lastModified: number;
  blob: Blob;
};

export type MedicineImageImport = {
  id: string;
  patientId: string;
  createdAt: string;
  prescriptionImageIds: string[];
  medicineImageIds: string[];
};

export type MedicineImageImportInput = {
  id: string;
  patientId: string;
  createdAt: string;
  prescriptionImages: readonly File[];
  medicineImages: readonly File[];
};

export type StandaloneMedicineImageInput = {
  id: string;
  patientId: string;
  createdAt: string;
  file: File;
};

export type MedicineImageImportStore = {
  put: (record: MedicineImageImport, images: readonly MedicineStoredImage[]) => Promise<void>;
  getImage: (id: string) => Promise<MedicineStoredImage | null>;
  delete: (id: string) => Promise<void>;
};

let database: Promise<IDBDatabase> | undefined;

function openDatabase(): Promise<IDBDatabase> {
  if (typeof indexedDB === 'undefined') return Promise.reject(new Error('IndexedDB is not available.'));
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open(MEDICINE_IMAGE_DATABASE_NAME, MEDICINE_IMAGE_DATABASE_VERSION);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(IMAGE_STORE)) request.result.createObjectStore(IMAGE_STORE, { keyPath: 'id' });
      if (!request.result.objectStoreNames.contains(IMPORT_STORE)) request.result.createObjectStore(IMPORT_STORE, { keyPath: 'id' });
    };
    request.onsuccess = () => {
      request.result.onversionchange = () => request.result.close();
      resolve(request.result);
    };
    request.onerror = () => reject(request.error ?? new Error('The medicine image database could not be opened.'));
    request.onblocked = () => reject(new Error('The medicine image database is blocked by another tab.'));
  });
  return database;
}

function validTimestamp(value: string): boolean {
  return Boolean(value) && Number.isFinite(Date.parse(value));
}

export function medicineStoredImageId(importId: string, kind: MedicineStoredImage['kind'], index: number): string {
  if (!importId.trim() || !Number.isInteger(index) || index < 0) return '';
  return `${importId.trim()}:${kind}:${index}`;
}

function storedImage(file: File, importId: string, kind: MedicineStoredImage['kind'], index: number): MedicineStoredImage | null {
  if (!IMAGE_TYPES.includes(file.type as MedicineStoredImage['type']) || file.size <= 0 || !file.name.trim()) return null;
  return {
    id: medicineStoredImageId(importId, kind, index), importId, kind, index,
    name: file.name, type: file.type as MedicineStoredImage['type'], size: file.size,
    lastModified: file.lastModified, blob: file,
  };
}

export function makeMedicineImageImport(input: MedicineImageImportInput): {
  record: MedicineImageImport;
  images: MedicineStoredImage[];
} | null {
  const id = input.id.trim();
  const patientId = input.patientId.trim();
  if (!id || !patientId || !validTimestamp(input.createdAt)
    || (!input.prescriptionImages.length && !input.medicineImages.length)
    || input.prescriptionImages.length > 5
    || input.medicineImages.length > 10) return null;

  const prescriptionImages = input.prescriptionImages.map((file, index) => storedImage(file, id, 'prescription', index));
  const medicineImages = input.medicineImages.map((file, index) => storedImage(file, id, 'medicine', index));
  if (prescriptionImages.some((image) => !image) || medicineImages.some((image) => !image)) return null;
  const images = [...prescriptionImages, ...medicineImages] as MedicineStoredImage[];
  return {
    record: {
      id, patientId, createdAt: input.createdAt,
      prescriptionImageIds: prescriptionImages.map((image) => image!.id),
      medicineImageIds: medicineImages.map((image) => image!.id),
    },
    images,
  };
}

const browserStore: MedicineImageImportStore = {
  async put(record, images) {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction([IMPORT_STORE, IMAGE_STORE], 'readwrite');
      transaction.objectStore(IMPORT_STORE).add(record);
      const imageStore = transaction.objectStore(IMAGE_STORE);
      images.forEach((image) => imageStore.add(image));
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error ?? new Error('The medicine images could not be stored.'));
      transaction.onabort = () => reject(transaction.error ?? new Error('The medicine image save was cancelled.'));
    });
  },
  async getImage(id) {
    const db = await openDatabase();
    return new Promise<MedicineStoredImage | null>((resolve, reject) => {
      const request = db.transaction(IMAGE_STORE, 'readonly').objectStore(IMAGE_STORE).get(id);
      request.onsuccess = () => resolve((request.result as MedicineStoredImage | undefined) ?? null);
      request.onerror = () => reject(request.error ?? new Error('The medicine image could not be read.'));
    });
  },
  async delete(id) {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction([IMPORT_STORE, IMAGE_STORE], 'readwrite');
      const imports = transaction.objectStore(IMPORT_STORE);
      const images = transaction.objectStore(IMAGE_STORE);
      const request = imports.get(id);
      request.onsuccess = () => {
        const record = request.result as MedicineImageImport | undefined;
        [...(record?.prescriptionImageIds ?? []), ...(record?.medicineImageIds ?? [])]
          .forEach((image) => images.delete(image));
        imports.delete(id);
      };
      request.onerror = () => transaction.abort();
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error ?? new Error('The medicine images could not be removed.'));
      transaction.onabort = () => reject(transaction.error ?? request.error ?? new Error('The medicine image removal was cancelled.'));
    });
  },
};

export async function saveMedicineImageImport(input: MedicineImageImportInput,
  store: MedicineImageImportStore = browserStore): Promise<MedicineImageImport> {
  const value = makeMedicineImageImport(input);
  if (!value) throw new Error('The prescription or medicine images are not valid.');
  await store.put(value.record, value.images);
  return value.record;
}

export async function loadMedicineImageFile(id: string,
  store: MedicineImageImportStore = browserStore): Promise<File | null> {
  if (!id.trim()) return null;
  const image = await store.getImage(id);
  if (!image || image.id !== id || image.size !== image.blob.size
    || !IMAGE_TYPES.includes(image.type) || image.blob.type !== image.type) return null;
  return new File([image.blob], image.name, { type: image.type, lastModified: image.lastModified });
}

/** Stores a photo added from the medicine editor without duplicating it in the event record. */
export async function saveStandaloneMedicineImage(input: StandaloneMedicineImageInput,
  store: MedicineImageImportStore = browserStore): Promise<{ importId: string; imageId: string }> {
  const record = await saveMedicineImageImport({
    id: input.id,
    patientId: input.patientId,
    createdAt: input.createdAt,
    prescriptionImages: [],
    medicineImages: [input.file],
  }, store);
  const imageId = record.medicineImageIds[0];
  if (!imageId) throw new Error('The medicine photo could not be stored.');
  return { importId: record.id, imageId };
}

export function standaloneMedicineImageImportId(imageId: string): string | null {
  const match = /^(medicine-photo-[^:]+):medicine:0$/u.exec(imageId.trim());
  return match?.[1] ?? null;
}

export async function deleteMedicineImageImport(id: string,
  store: MedicineImageImportStore = browserStore): Promise<void> {
  if (id.trim()) await store.delete(id.trim());
}
