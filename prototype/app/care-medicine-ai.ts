'use client';

import type { MedicineOrganization, OrganizedMedicine } from './care-medicine-import-state';

const MAX_PRESCRIPTION_PAGES = 5;
const MAX_MEDICINE_IMAGES = 10;
const MAX_IMAGE_BYTES = 500 * 1024;
const MAX_TOTAL_BYTES = 8 * 1024 * 1024;
const MAX_IMAGE_WIDTH = 1100;
const MAX_IMAGE_HEIGHT = 1500;
export type CareMedicineAiProgress = {
  stage: 'preparing' | 'organizing';
  completed: number;
  total: number;
  fileName: string;
};

export type CareMedicineAiResult = MedicineOrganization & {
  preparedPrescriptionPages: File[];
  preparedMedicineImages: File[];
};

export class CareMedicineAiError extends Error {
  readonly code: 'configuration' | 'invalid-images' | 'invalid-response' | 'unavailable';

  constructor(code: CareMedicineAiError['code'], message: string) {
    super(message);
    this.name = 'CareMedicineAiError';
    this.code = code;
  }
}

function trimmed(value: unknown, maximum: number): string {
  return typeof value === 'string' ? value.trim().slice(0, maximum) : '';
}

function stringList(value: unknown, maximumItems: number, maximumLength: number): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim().slice(0, maximumLength)).filter(Boolean))].slice(0, maximumItems);
}

function boundedIndexes(value: unknown, count: number): number[] {
  if (!Array.isArray(value)) return [];
  const indexes = value.filter((item): item is number => typeof item === 'number' && Number.isFinite(item))
    .map((item) => Math.trunc(item)).filter((item) => item >= 0 && item < count);
  return [...new Set(indexes)];
}

function suggestedTime(value: string): string | null {
  const text = value.trim();
  const twelveHour = /^(1[0-2]|0?[1-9])(?::([0-5]\d))?\s*(AM|PM)$/iu.exec(text);
  if (twelveHour) {
    let hour = Number(twelveHour[1]) % 12;
    if (twelveHour[3].toUpperCase() === 'PM') hour += 12;
    return `${String(hour).padStart(2, '0')}:${twelveHour[2] ?? '00'}`;
  }
  const twentyFourHour = /^(2[0-3]|[01]?\d):([0-5]\d)$/u.exec(text);
  if (!twentyFourHour) return null;
  return `${String(Number(twentyFourHour[1])).padStart(2, '0')}:${twentyFourHour[2]}`;
}

function positiveInteger(value: unknown, maximum: number): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const integer = Math.trunc(value);
  return integer >= 1 && integer <= maximum ? integer : null;
}

function withUncertainty(items: string[], message: string): string[] {
  return items.includes(message) ? items : [...items, message].slice(0, 6);
}

function mealTiming(value: unknown): OrganizedMedicine['mealTiming'] {
  return value === 'beforeFood' ? 'before-food'
    : value === 'withFood' ? 'with-food'
      : value === 'afterFood' ? 'after-food'
        : 'not-stated';
}

function medicineSourceText(item: Pick<OrganizedMedicine, 'title' | 'strength' | 'form' | 'quantity' | 'frequency' | 'instructions'>): string {
  return [item.title, item.strength, item.quantity, item.form, item.frequency, item.instructions].filter(Boolean).join(' · ').slice(0, 1000);
}

/** Validates the model response before anything reaches the review UI. */
export function sanitizeMedicineAiResponse(value: unknown, prescriptionCount: number, medicineImageCount: number): MedicineOrganization {
  let decoded = value;
  if (typeof value === 'string') {
    if (!value.trim()) throw new CareMedicineAiError('invalid-response', 'The AI returned no medicine list. Try clearer photos.');
    try { decoded = JSON.parse(value); }
    catch { throw new CareMedicineAiError('invalid-response', 'The AI response was not valid. Try the photos again.'); }
  }
  if (!decoded || typeof decoded !== 'object' || !Array.isArray((decoded as { medicines?: unknown }).medicines)) {
    throw new CareMedicineAiError('invalid-response', 'No medicines could be organized from these photos.');
  }

  const root = decoded as { medicines: unknown[]; unmatchedMedicineImageIndexes?: unknown; warnings?: unknown };
  const medicines: OrganizedMedicine[] = [];
  const seen = new Set<string>();
  const claimedMedicineImages = new Set<number>();
  for (const candidate of root.medicines) {
    if (!candidate || typeof candidate !== 'object') continue;
    const raw = candidate as Record<string, unknown>;
    const title = trimmed(raw.medicineName, 120);
    const strength = trimmed(raw.strength, 80);
    if (!title) continue;
    const key = `${title.toLocaleLowerCase()}|${strength.toLocaleLowerCase()}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const numericImageIndex = typeof raw.medicineImageIndex === 'number' && Number.isFinite(raw.medicineImageIndex)
      ? Math.trunc(raw.medicineImageIndex) : -1;
    let medicineImageIndex = numericImageIndex >= 0 && numericImageIndex < medicineImageCount ? numericImageIndex : -1;
    let confidence = typeof raw.confidence === 'number' && Number.isFinite(raw.confidence)
      ? Math.max(0, Math.min(1, raw.confidence)) : 0;
    const times = [...new Set(stringList(raw.suggestedTimes, 6, 20).map(suggestedTime).filter((item): item is string => Boolean(item)))];
    const prescriptionPageIndexes = boundedIndexes(raw.prescriptionPageIndexes, prescriptionCount);
    let uncertainties = stringList(raw.uncertainties, 6, 180);
    const identityMatch = raw.identityMatch === 'exactBrand' || raw.identityMatch === 'genericEquivalent'
      || raw.identityMatch === 'uncertain' || raw.identityMatch === 'none' ? raw.identityMatch : null;
    if (identityMatch === 'genericEquivalent') {
      confidence = Math.min(confidence, 0.74);
      uncertainties = withUncertainty(uncertainties, 'The strip uses a different brand. Confirm the generic medicine and strength before saving.');
    } else if (identityMatch === 'uncertain') {
      confidence = Math.min(confidence, 0.49);
    } else if (identityMatch === 'none') {
      medicineImageIndex = -1;
    }
    if (medicineImageIndex >= 0 && claimedMedicineImages.has(medicineImageIndex)) {
      uncertainties = withUncertainty(uncertainties, `Strip photo ${medicineImageIndex + 1} was also matched to another medicine. Review this entry manually.`);
      medicineImageIndex = -1;
      confidence = Math.min(confidence, 0.49);
    }
    if (numericImageIndex >= medicineImageCount) {
      uncertainties = withUncertainty(uncertainties, 'The AI returned an invalid strip-photo match. Review this entry manually.');
    }
    if (!prescriptionPageIndexes.length) {
      uncertainties = withUncertainty(uncertainties, 'No prescription page was linked to this entry. Check it against the prescription.');
      medicineImageIndex = -1;
      confidence = Math.min(confidence, 0.49);
    }
    if (medicineImageIndex >= 0) claimedMedicineImages.add(medicineImageIndex);
    const medicine: OrganizedMedicine = {
      title,
      strength,
      form: trimmed(raw.form, 60),
      quantity: trimmed(raw.quantity, 80),
      frequency: trimmed(raw.frequency, 120),
      instructions: trimmed(raw.instructions, 500),
      times,
      mealTiming: mealTiming(raw.mealTiming),
      sourceText: '',
      medicineImageIndex,
      prescriptionPageIndexes,
      prescriptionItemNumber: positiveInteger(raw.prescriptionItemNumber, 999),
      durationDays: positiveInteger(raw.durationDays, 365),
      confidence,
      uncertainties,
      matchQuality: medicineImageIndex < 0 ? 'prescription-only' : confidence >= 0.75 ? 'matched' : 'possible',
    };
    medicine.sourceText = medicineSourceText(medicine);
    medicines.push(medicine);
  }

  medicines.sort((left, right) => {
    const leftOrder = left.prescriptionItemNumber ?? Number.MAX_SAFE_INTEGER;
    const rightOrder = right.prescriptionItemNumber ?? Number.MAX_SAFE_INTEGER;
    return leftOrder - rightOrder;
  });

  const used = new Set(medicines.filter((item) => item.medicineImageIndex >= 0).map((item) => item.medicineImageIndex));
  const unmatched = new Set(boundedIndexes(root.unmatchedMedicineImageIndexes, medicineImageCount));
  for (let index = 0; index < medicineImageCount; index += 1) if (!used.has(index)) unmatched.add(index);
  return {
    medicines,
    unmatchedMedicineImageIndexes: [...unmatched].sort((left, right) => left - right),
    warnings: stringList(root.warnings, 8, 180),
  };
}

function canvasBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Image encoding failed.')), 'image/jpeg', quality));
}

async function prepareImage(file: File): Promise<File> {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size <= 0) {
    throw new CareMedicineAiError('invalid-images', `${file.name || 'An image'} is not a supported medicine photo.`);
  }
  // Send an in-limit source image unchanged. Keeping the original
  // bytes matters for small printed strengths and handwritten directions.
  if (file.size <= MAX_IMAGE_BYTES) return file;
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  try {
    let scale = Math.min(1, MAX_IMAGE_WIDTH / bitmap.width, MAX_IMAGE_HEIGHT / bitmap.height);
    for (let sizeAttempt = 0; sizeAttempt < 5; sizeAttempt += 1) {
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(bitmap.width * scale));
      canvas.height = Math.max(1, Math.round(bitmap.height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Image canvas is unavailable.');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      for (const quality of [0.58, 0.48, 0.38, 0.30]) {
        const blob = await canvasBlob(canvas, quality);
        if (blob.size <= MAX_IMAGE_BYTES) {
          const stem = file.name.replace(/\.[^.]+$/u, '').slice(0, 180) || 'medicine-photo';
          return new File([blob], `${stem}.jpg`, { type: 'image/jpeg', lastModified: file.lastModified });
        }
      }
      scale *= 0.78;
    }
  } finally {
    bitmap.close();
  }
  throw new CareMedicineAiError('invalid-images', `${file.name} could not be reduced below 500 KB. Crop it and try again.`);
}

async function fileImageData(file: File): Promise<{ data: string; mimeType: string }> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error ?? new Error('The image could not be read.'));
    reader.readAsDataURL(file);
  });
  const comma = dataUrl.indexOf(',');
  if (comma < 0) throw new CareMedicineAiError('invalid-images', `${file.name} could not be encoded.`);
  return { data: dataUrl.slice(comma + 1), mimeType: file.type };
}

export function medicineImageRequestItem(name: string, image: { data: string; mimeType: string }) {
  return { name, data: image.data, mimeType: image.mimeType };
}

async function withSignal<T>(promise: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return promise;
  if (signal.aborted) throw new DOMException('The request was cancelled.', 'AbortError');
  return new Promise<T>((resolve, reject) => {
    const abort = () => reject(new DOMException('The request was cancelled.', 'AbortError'));
    signal.addEventListener('abort', abort, { once: true });
    promise.then(resolve, reject).finally(() => signal.removeEventListener('abort', abort));
  });
}

export async function organizeMedicineImagesWithAi(prescriptionPages: readonly File[], medicineImages: readonly File[], options: {
  signal?: AbortSignal;
  onProgress?: (progress: CareMedicineAiProgress) => void;
} = {}): Promise<CareMedicineAiResult> {
  if (!prescriptionPages.length || !medicineImages.length) {
    throw new CareMedicineAiError('invalid-images', 'Add at least one prescription page and one medicine-strip photo.');
  }
  if (prescriptionPages.length > MAX_PRESCRIPTION_PAGES || medicineImages.length > MAX_MEDICINE_IMAGES) {
    throw new CareMedicineAiError('invalid-images', 'Use up to 5 prescription pages and 10 medicine-strip photos.');
  }
  const inputs = [...prescriptionPages, ...medicineImages];
  const prepared: File[] = [];
  for (let index = 0; index < inputs.length; index += 1) {
    options.onProgress?.({ stage: 'preparing', completed: index, total: inputs.length, fileName: inputs[index].name });
    prepared.push(await withSignal(prepareImage(inputs[index]), options.signal));
  }
  const totalBytes = prepared.reduce((sum, file) => sum + file.size, 0);
  if (totalBytes > MAX_TOTAL_BYTES) throw new CareMedicineAiError('invalid-images', 'The selected images are too large together. Use fewer or tighter photos.');
  const preparedPrescriptionPages = prepared.slice(0, prescriptionPages.length);
  const preparedMedicineImages = prepared.slice(prescriptionPages.length);

  options.onProgress?.({ stage: 'organizing', completed: prepared.length, total: prepared.length, fileName: '' });

  try {
    const prescriptionPayload = await Promise.all(preparedPrescriptionPages.map(async (file) => (
      medicineImageRequestItem(file.name, await fileImageData(file))
    )));
    const medicinePayload = await Promise.all(preparedMedicineImages.map(async (file) => (
      medicineImageRequestItem(file.name, await fileImageData(file))
    )));
    const response = await fetch('/api/medicine-organizer', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ prescriptionPages: prescriptionPayload, medicineImages: medicinePayload }),
      credentials: 'same-origin',
      cache: 'no-store',
      signal: options.signal,
    });
    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const message = payload && typeof payload === 'object' && 'error' in payload && typeof payload.error === 'string'
        ? payload.error : 'The medicine organizer could not connect. Check the connection and try again.';
      throw new CareMedicineAiError(response.status === 503 ? 'configuration' : 'unavailable', message);
    }
    const organization = sanitizeMedicineAiResponse(payload, preparedPrescriptionPages.length, preparedMedicineImages.length);
    if (!organization.medicines.length) throw new CareMedicineAiError('invalid-response', 'No medicines could be organized. Try clearer prescription photos.');
    return { ...organization, preparedPrescriptionPages, preparedMedicineImages };
  } catch (reason) {
    if (reason instanceof CareMedicineAiError || (reason instanceof DOMException && reason.name === 'AbortError')) throw reason;
    throw new CareMedicineAiError('unavailable', 'The medicine organizer could not connect. Check the connection and try again.');
  }
}
