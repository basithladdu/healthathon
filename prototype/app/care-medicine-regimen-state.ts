'use client';

import { isValidAppointmentDate, isValidAppointmentTime } from './appointment-state.ts';
import { medicineCourseEndDate } from './care-medicine-import-state.ts';

export const MEDICINE_REGIMEN_STORAGE_KEY = 'saanthvana-care:medicine-regimens';
export const MEDICINE_REGIMEN_STATUSES = ['active', 'completed', 'removed'] as const;
export const MEDICINE_REGIMEN_MEAL_TIMINGS = ['beforeFood', 'withFood', 'afterFood', 'anytime'] as const;

export type MedicineRegimenStatus = (typeof MEDICINE_REGIMEN_STATUSES)[number];
export type MedicineRegimenMealTiming = (typeof MEDICINE_REGIMEN_MEAL_TIMINGS)[number];

export type MedicineRegimen = {
  id: string;
  patientId: string;
  sourceImportId: string;
  sourceMedicineIndex: number;
  /** Printed item/line number from the source prescription, when visible. */
  prescriptionItemNumber: number | null;
  medicineName: string;
  strength: string;
  form: string;
  quantity: string;
  frequency: string;
  /** Explicit inclusive course length reviewed by the patient, when stated. */
  durationDays: number | null;
  instructions: string;
  mealTiming: MedicineRegimenMealTiming;
  times: string[];
  startDate: string;
  /** `null` means the regimen is ongoing. */
  endDate: string | null;
  status: MedicineRegimenStatus;
  /** IndexedDB image key. The Blob is never copied into this localStorage record. */
  medicineImageId: string | null;
  /** Page positions in the single shared `sourceImportId` prescription record. */
  prescriptionPageIndexes: number[];
  confidence: number | null;
  uncertainties: string[];
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  removedAt: string | null;
  removedBy: string | null;
};

export type MedicineRegimenDraft = Pick<MedicineRegimen,
  'patientId' | 'sourceImportId' | 'sourceMedicineIndex' | 'prescriptionItemNumber' | 'medicineName' | 'strength' | 'form'
  | 'quantity' | 'frequency' | 'durationDays' | 'instructions' | 'mealTiming' | 'times' | 'startDate' | 'endDate'
  | 'medicineImageId' | 'prescriptionPageIndexes' | 'confidence' | 'uncertainties'
> & { id?: string };

export type MedicineRegimenChanges = Partial<Pick<MedicineRegimen,
  'medicineName' | 'strength' | 'form' | 'quantity' | 'frequency' | 'instructions' | 'mealTiming'
  | 'durationDays' | 'times' | 'startDate' | 'endDate' | 'medicineImageId' | 'prescriptionPageIndexes' | 'confidence' | 'uncertainties'
>>;

export type MedicineRegimenAudit = { actor: string; at: string };
export type MedicineRegimenStorage = Pick<Storage, 'getItem' | 'setItem'>;

type PersistedMedicineRegimens = { version: 2; value: unknown };
type LegacyMedicineRegimens = { version: 1; value: unknown };

function validTimestamp(value: string): boolean {
  return Boolean(value) && Number.isFinite(Date.parse(value));
}

function nullablePositiveInteger(value: unknown): value is number | null {
  return value === null || (typeof value === 'number' && Number.isSafeInteger(value) && value > 0);
}

function browserStorage(): MedicineRegimenStorage | null {
  try { return typeof window === 'undefined' ? null : window.localStorage; }
  catch { return null; }
}

function normalizedIndexes(value: unknown): number[] | null {
  if (!Array.isArray(value)) return null;
  const seen = new Set<number>();
  const indexes: number[] = [];
  for (const index of value) {
    if (!Number.isInteger(index) || Number(index) < 0 || seen.has(Number(index))) return null;
    seen.add(Number(index));
    indexes.push(Number(index));
  }
  return indexes.sort((left, right) => left - right);
}

function normalizedStrings(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) return null;
  return [...new Set(value.map((item) => item.trim()).filter(Boolean))];
}

function normalizeRegimen(value: unknown): MedicineRegimen | null {
  if (!value || typeof value !== 'object') return null;
  const item = value as Partial<MedicineRegimen>;
  const requiredStrings = [item.id, item.patientId, item.sourceImportId, item.medicineName, item.createdBy, item.updatedBy];
  const strings = [item.strength, item.form, item.quantity, item.frequency, item.instructions];
  const times = normalizedStrings(item.times);
  const uncertainties = normalizedStrings(item.uncertainties);
  const prescriptionPageIndexes = normalizedIndexes(item.prescriptionPageIndexes);
  const medicineImageId = item.medicineImageId === null
    ? null
    : typeof item.medicineImageId === 'string' && item.medicineImageId.trim() ? item.medicineImageId.trim() : undefined;
  // Records written before course duration/order provenance was added migrate safely to unknown.
  const durationDays = item.durationDays === undefined ? null : item.durationDays;
  const prescriptionItemNumber = item.prescriptionItemNumber === undefined ? null : item.prescriptionItemNumber;
  if (requiredStrings.some((entry) => typeof entry !== 'string' || !entry.trim())
    || strings.some((entry) => typeof entry !== 'string')
    || !Number.isInteger(item.sourceMedicineIndex) || Number(item.sourceMedicineIndex) < 0
    || !nullablePositiveInteger(prescriptionItemNumber) || !nullablePositiveInteger(durationDays)
    || typeof item.mealTiming !== 'string' || !MEDICINE_REGIMEN_MEAL_TIMINGS.includes(item.mealTiming as MedicineRegimenMealTiming)
    || !times || times.some((time) => !isValidAppointmentTime(time))
    || typeof item.startDate !== 'string' || !isValidAppointmentDate(item.startDate)
    || (item.endDate !== null && (typeof item.endDate !== 'string' || !isValidAppointmentDate(item.endDate) || item.endDate < item.startDate))
    || (durationDays !== null && (typeof item.endDate !== 'string' || medicineCourseEndDate(item.startDate, durationDays) !== item.endDate))
    || typeof item.status !== 'string' || !MEDICINE_REGIMEN_STATUSES.includes(item.status as MedicineRegimenStatus)
    || medicineImageId === undefined || !prescriptionPageIndexes
    || (item.confidence !== null && (typeof item.confidence !== 'number' || !Number.isFinite(item.confidence) || item.confidence < 0 || item.confidence > 1))
    || !uncertainties || typeof item.createdAt !== 'string' || !validTimestamp(item.createdAt)
    || typeof item.updatedAt !== 'string' || !validTimestamp(item.updatedAt)
    || (item.status === 'removed'
      ? typeof item.removedAt !== 'string' || !validTimestamp(item.removedAt) || typeof item.removedBy !== 'string' || !item.removedBy.trim()
      : item.removedAt !== null || item.removedBy !== null)) return null;

  return {
    id: item.id!.trim(), patientId: item.patientId!.trim(), sourceImportId: item.sourceImportId!.trim(),
    sourceMedicineIndex: Number(item.sourceMedicineIndex), prescriptionItemNumber, medicineName: item.medicineName!.trim(),
    strength: item.strength!.trim(), form: item.form!.trim(), quantity: item.quantity!.trim(),
    frequency: item.frequency!.trim(), durationDays, instructions: item.instructions!.trim(),
    mealTiming: item.mealTiming as MedicineRegimenMealTiming, times, startDate: item.startDate,
    endDate: item.endDate, status: item.status as MedicineRegimenStatus, medicineImageId,
    prescriptionPageIndexes, confidence: item.confidence, uncertainties,
    createdAt: item.createdAt, createdBy: item.createdBy!.trim(), updatedAt: item.updatedAt,
    updatedBy: item.updatedBy!.trim(), removedAt: item.removedAt ?? null,
    removedBy: typeof item.removedBy === 'string' ? item.removedBy.trim() : null,
  };
}

export function medicineRegimenId(patientId: string, sourceImportId: string, sourceMedicineIndex: number): string {
  if (!patientId.trim() || !sourceImportId.trim() || !Number.isInteger(sourceMedicineIndex) || sourceMedicineIndex < 0) return '';
  return `medicine-regimen:${encodeURIComponent(patientId.trim())}:${encodeURIComponent(sourceImportId.trim())}:${sourceMedicineIndex}`;
}

export function makeMedicineRegimen(draft: MedicineRegimenDraft, audit: MedicineRegimenAudit): MedicineRegimen | null {
  const id = draft.id?.trim() || medicineRegimenId(draft.patientId, draft.sourceImportId, draft.sourceMedicineIndex);
  return normalizeRegimen({
    ...draft, id, status: 'active', createdAt: audit.at, createdBy: audit.actor,
    updatedAt: audit.at, updatedBy: audit.actor, removedAt: null, removedBy: null,
  });
}

export function addMedicineRegimen(items: readonly MedicineRegimen[], item: MedicineRegimen): MedicineRegimen[] {
  const normalized = normalizeRegimen(item);
  if (!normalized || items.some((existing) => existing.id === normalized.id)) return items as MedicineRegimen[];
  return [...items, normalized];
}

export function reviseMedicineRegimen(items: readonly MedicineRegimen[], patientId: string, id: string,
  changes: MedicineRegimenChanges, audit: MedicineRegimenAudit): MedicineRegimen[] {
  const previous = items.find((item) => item.patientId === patientId && item.id === id);
  if (!previous || !audit.actor.trim() || !validTimestamp(audit.at)) return items as MedicineRegimen[];
  const normalized = normalizeRegimen({
    ...previous, ...changes, updatedAt: audit.at, updatedBy: audit.actor,
  });
  if (!normalized || normalized.patientId !== previous.patientId || normalized.id !== previous.id) return items as MedicineRegimen[];
  return items.map((item) => item.patientId === patientId && item.id === id ? normalized : item);
}

export function setMedicineRegimenStatus(items: readonly MedicineRegimen[], patientId: string, id: string,
  status: MedicineRegimenStatus, audit: MedicineRegimenAudit): MedicineRegimen[] {
  const previous = items.find((item) => item.patientId === patientId && item.id === id);
  if (!previous || !MEDICINE_REGIMEN_STATUSES.includes(status) || !audit.actor.trim() || !validTimestamp(audit.at)
    || previous.status === status) return items as MedicineRegimen[];
  const removed = status === 'removed';
  const normalized = normalizeRegimen({
    ...previous, status, updatedAt: audit.at, updatedBy: audit.actor,
    removedAt: removed ? audit.at : null, removedBy: removed ? audit.actor : null,
  });
  if (!normalized) return items as MedicineRegimen[];
  return items.map((item) => item.patientId === patientId && item.id === id ? normalized : item);
}

export function removeMedicineRegimen(items: readonly MedicineRegimen[], patientId: string, id: string,
  audit: MedicineRegimenAudit): MedicineRegimen[] {
  return setMedicineRegimenStatus(items, patientId, id, 'removed', audit);
}

export function medicineRegimenFallsOn(item: MedicineRegimen, date: string): boolean {
  return item.status === 'active' && isValidAppointmentDate(date) && date >= item.startDate
    && (item.endDate === null || date <= item.endDate);
}

export function patientMedicineRegimens(items: readonly MedicineRegimen[], patientId: string,
  includeRemoved = false): MedicineRegimen[] {
  return items.filter((item) => item.patientId === patientId && (includeRemoved || item.status !== 'removed'))
    .map((item) => normalizeRegimen(item)!).filter(Boolean);
}

function migrateLegacyRegimen(value: unknown): unknown {
  if (!value || typeof value !== 'object') return value;
  const legacy = value as Record<string, unknown>;
  const medicineImage = legacy.medicineImage && typeof legacy.medicineImage === 'object'
    ? legacy.medicineImage as Record<string, unknown> : null;
  const pageIndexes = Array.isArray(legacy.prescriptionPages)
    ? legacy.prescriptionPages.map((page) => page && typeof page === 'object'
      ? (page as Record<string, unknown>).pageIndex : null)
    : [];
  const text = Object.fromEntries(Object.entries(legacy)
    .filter(([key]) => key !== 'medicineImage' && key !== 'prescriptionPages'));
  return {
    ...text,
    medicineImageId: typeof medicineImage?.id === 'string' && medicineImage.id.trim() ? medicineImage.id.trim() : null,
    prescriptionPageIndexes: pageIndexes,
  };
}

export function loadMedicineRegimens(storage: MedicineRegimenStorage | null = browserStorage()): MedicineRegimen[] {
  if (!storage) return [];
  try {
    const raw = storage.getItem(MEDICINE_REGIMEN_STORAGE_KEY);
    if (!raw) return [];
    const record = JSON.parse(raw) as Partial<PersistedMedicineRegimens | LegacyMedicineRegimens>;
    if ((record.version !== 1 && record.version !== 2) || !Array.isArray(record.value)) return [];
    const values = record.version === 1 ? record.value.map(migrateLegacyRegimen) : record.value;
    const result: MedicineRegimen[] = [];
    for (const value of values) {
      const normalized = normalizeRegimen(value);
      if (!normalized || result.some((item) => item.id === normalized.id)) return [];
      result.push(normalized);
    }
    return result;
  } catch { return []; }
}

export function saveMedicineRegimens(items: readonly MedicineRegimen[], storage: MedicineRegimenStorage | null = browserStorage()): boolean {
  if (!storage) return false;
  const normalized: MedicineRegimen[] = [];
  for (const value of items) {
    const item = normalizeRegimen(value);
    if (!item || normalized.some((existing) => existing.id === item.id)) return false;
    normalized.push(item);
  }
  try {
    storage.setItem(MEDICINE_REGIMEN_STORAGE_KEY, JSON.stringify({ version: 2, value: normalized } satisfies PersistedMedicineRegimens));
    return true;
  } catch { return false; }
}
