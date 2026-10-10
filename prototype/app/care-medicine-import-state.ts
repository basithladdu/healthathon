export const MEDICINE_MEAL_TIMINGS = ['not-stated', 'before-food', 'with-food', 'after-food'] as const;
export type MedicineMealTiming = (typeof MEDICINE_MEAL_TIMINGS)[number];

export type OrganizedMedicine = {
  title: string;
  strength: string;
  form: string;
  quantity: string;
  frequency: string;
  /** Explicit course length printed on the prescription. `null` means it was not stated safely. */
  durationDays: number | null;
  /** Printed prescription line/item number. `null` means no reliable number was visible. */
  prescriptionItemNumber: number | null;
  instructions: string;
  times: string[];
  mealTiming: MedicineMealTiming;
  sourceText: string;
  medicineImageIndex: number;
  prescriptionPageIndexes: number[];
  confidence: number | null;
  uncertainties: string[];
  matchQuality: 'matched' | 'possible' | 'prescription-only' | 'manual';
};

export type MedicineOrganization = {
  medicines: OrganizedMedicine[];
  unmatchedMedicineImageIndexes: number[];
  warnings: string[];
};

function validIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/u.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function safePositiveInteger(value: number | null): value is number {
  return Number.isSafeInteger(value) && Number(value) > 0;
}

/** Computes an inclusive last day: a one-day course ends on its first day. */
export function medicineCourseEndDate(startDate: string, durationDays: number | null): string | null {
  if (!validIsoDate(startDate) || !safePositiveInteger(durationDays)) return null;
  const end = new Date(`${startDate}T00:00:00.000Z`);
  end.setUTCDate(end.getUTCDate() + durationDays - 1);
  return Number.isFinite(end.getTime()) ? end.toISOString().slice(0, 10) : null;
}

/** Returns the inclusive day count between two valid dates. */
export function medicineCourseDurationDays(startDate: string, endDate: string): number | null {
  if (!validIsoDate(startDate) || !validIsoDate(endDate) || endDate < startDate) return null;
  const start = Date.parse(`${startDate}T00:00:00.000Z`);
  const end = Date.parse(`${endDate}T00:00:00.000Z`);
  const days = Math.round((end - start) / 86_400_000) + 1;
  return Number.isSafeInteger(days) && days > 0 ? days : null;
}

/** Keeps printed prescription order while leaving unnumbered/manual entries at the end. */
export function sortOrganizedMedicinesByPrescriptionOrder<T extends Pick<OrganizedMedicine, 'prescriptionItemNumber'>>(
  medicines: readonly T[],
): T[] {
  return medicines.map((medicine, index) => ({ medicine, index })).sort((left, right) => {
    const leftNumber = safePositiveInteger(left.medicine.prescriptionItemNumber) ? left.medicine.prescriptionItemNumber : null;
    const rightNumber = safePositiveInteger(right.medicine.prescriptionItemNumber) ? right.medicine.prescriptionItemNumber : null;
    if (leftNumber === null && rightNumber === null) return left.index - right.index;
    if (leftNumber === null) return 1;
    if (rightNumber === null) return -1;
    return leftNumber - rightNumber || left.index - right.index;
  }).map(({ medicine }) => medicine);
}

export function medicineMealTimingLabel(value: MedicineMealTiming, hindi = false): string {
  if (hindi) return value === 'before-food' ? 'खाने से पहले'
    : value === 'with-food' ? 'खाने के साथ'
      : value === 'after-food' ? 'खाने के बाद'
        : 'कभी भी / नहीं बताया';
  return value === 'before-food' ? 'Before food'
    : value === 'with-food' ? 'With food'
      : value === 'after-food' ? 'After food'
        : 'Anytime / not stated';
}

const MEDICINE_MARKER = /\b(?:tab(?:let)?s?|cap(?:sule)?s?|syr(?:up)?|inj(?:ection)?|cream|ointment|drops?|mg|mcg|µg|ml|units?|iu)\b/iu;
const INSTRUCTION_MARKER = /\b(?:take|use|apply|once|twice|thrice|daily|morning|afternoon|evening|night|bedtime|food|meal|breakfast|lunch|dinner|empty stomach|od|bd|bid|tds|tid|qid|sos|prn)\b/iu;
const NON_MEDICINE_MARKER = /\b(?:patient|hospital|clinic|address|phone|mobile|diagnosis|doctor|registration|invoice|bill|total|date of birth|age|sex)\b/iu;
const STOP_WORDS = new Set(['tablet', 'tablets', 'capsule', 'capsules', 'medicine', 'medicines', 'take', 'with', 'after', 'before', 'food', 'meal', 'daily', 'once', 'twice', 'morning', 'night', 'dose', 'prescription']);

function cleanLines(text: string): string[] {
  return text.split(/\r?\n/u).map((line) => line.replace(/\s+/gu, ' ').trim()).filter((line) => line.length >= 3 && line.length <= 500);
}

function looksLikeMedicine(line: string): boolean {
  if (NON_MEDICINE_MARKER.test(line) || !/[a-z]{3}/iu.test(line)) return false;
  if (/^(?:rx|℞)\s*$/iu.test(line)) return false;
  if (!credibleMedicineName(medicineTitle(line))) return false;
  return MEDICINE_MARKER.test(line)
    || /^(?:\d+[.)-]?\s*)?(?:tab|cap|syp|syr|inj|cream|ointment|drops?)\b/iu.test(line)
    || (/\b\d+(?:\.\d+)?\s*(?:mg|mcg|µg|g|ml|iu)\b/iu.test(line) && line.length <= 220);
}

function looksLikeInstruction(line: string): boolean {
  return INSTRUCTION_MARKER.test(line) || /\b\d\s*[-–]\s*\d\s*[-–]\s*\d\b/u.test(line) || explicitTimes(line).length > 0;
}

function medicineTitle(line: string): string {
  const cleaned = line
    .replace(/^(?:\s*(?:rx|℞)\s*)?/iu, '')
    .replace(/^\s*\d+[.)-]?\s*/u, '')
    .replace(/^\s*(?:tab(?:let)?s?|cap(?:sule)?s?|syp|syr(?:up)?|inj(?:ection)?|cream|ointment|drops?)\.?\s+/iu, '')
    .trim();
  const instructionAt = cleaned.search(/\b(?:take|use|apply|once|twice|thrice|daily|morning|afternoon|evening|night|bedtime|before|after|with|od|bd|bid|tds|tid|qid|sos|prn)\b|\b\d\s*[-–]\s*\d\s*[-–]\s*\d\b/iu);
  const title = (instructionAt > 2 ? cleaned.slice(0, instructionAt) : cleaned).replace(/[,:;\-–]+$/u, '').trim();
  return title.slice(0, 160);
}

function credibleWord(word: string): boolean {
  const letters = word.toLowerCase().replace(/[^a-z]/gu, '');
  return letters.length >= 4 && new Set(letters).size >= 3 && !/^(.)\1+$/u.test(letters);
}

function credibleMedicineName(value: string): boolean {
  const words = value.match(/[a-z][a-z0-9-]*/giu) ?? [];
  return words.some((word) => credibleWord(word) && !STOP_WORDS.has(word.toLowerCase()));
}

function clock(hourText: string, minuteText: string | undefined, suffix: string | undefined): string | null {
  let hour = Number(hourText);
  const minute = Number(minuteText ?? '0');
  if (!Number.isInteger(hour) || !Number.isInteger(minute) || minute > 59) return null;
  if (suffix) {
    if (hour < 1 || hour > 12) return null;
    hour = hour % 12 + (/p/iu.test(suffix) ? 12 : 0);
  } else if (hour > 23) return null;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

export function explicitTimes(text: string): string[] {
  const values: string[] = [];
  const occupied: Array<[number, number]> = [];
  const twelveHour = /\b(1[0-2]|0?[1-9])(?:[:.]([0-5]\d))?\s*(a\.?m\.?|p\.?m\.?)\b/giu;
  for (const match of text.matchAll(twelveHour)) {
    const value = clock(match[1], match[2], match[3]);
    if (value) values.push(value);
    occupied.push([match.index ?? 0, (match.index ?? 0) + match[0].length]);
  }
  const twentyFourHour = /\b([01]?\d|2[0-3]):([0-5]\d)\b/gu;
  for (const match of text.matchAll(twentyFourHour)) {
    const start = match.index ?? 0;
    if (occupied.some(([from, to]) => start >= from && start < to)) continue;
    const value = clock(match[1], match[2], undefined);
    if (value) values.push(value);
  }
  return [...new Set(values)].sort();
}

export function mealTimingFromText(text: string): MedicineMealTiming {
  if (/\b(?:before (?:food|meals?|breakfast|lunch|dinner)|empty stomach)\b/iu.test(text)) return 'before-food';
  if (/\b(?:after (?:food|meals?|breakfast|lunch|dinner))\b/iu.test(text)) return 'after-food';
  if (/\b(?:with (?:food|meals?|breakfast|lunch|dinner))\b/iu.test(text)) return 'with-food';
  return 'not-stated';
}

function tokens(text: string): Set<string> {
  return new Set((text.toLowerCase().match(/[a-z][a-z0-9-]{2,}/gu) ?? [])
    .filter((word) => credibleWord(word) && !STOP_WORDS.has(word)));
}

function matchScore(prescription: string, strip: string): number {
  const expected = tokens(prescription);
  const seen = tokens(strip);
  if (!expected.size || !seen.size) return 0;
  const shared = [...expected].filter((word) => seen.has(word)).length;
  return shared / Math.max(1, Math.min(expected.size, seen.size));
}

export function manualMedicineFromImage(text: string, imageIndex: number): OrganizedMedicine {
  return {
    title: '',
    strength: '',
    form: '',
    quantity: '',
    frequency: '',
    durationDays: null,
    prescriptionItemNumber: null,
    instructions: '',
    times: [],
    mealTiming: 'not-stated',
    sourceText: '',
    medicineImageIndex: imageIndex,
    prescriptionPageIndexes: [],
    confidence: null,
    uncertainties: text.trim() ? ['No safe prescription match was found. Enter the medicine from the original prescription.'] : [],
    matchQuality: 'manual',
  };
}

export function organizeMedicineText(prescriptionPages: readonly string[], medicineImages: readonly string[]): MedicineOrganization {
  const candidates: Array<{ line: string; text: string }> = [];
  for (const page of prescriptionPages) {
    const lines = cleanLines(page);
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      if (!looksLikeMedicine(line)) continue;
      const supporting: string[] = [];
      for (let offset = 1; offset <= 2; offset += 1) {
        const next = lines[index + offset];
        if (!next || looksLikeMedicine(next) || !looksLikeInstruction(next)) break;
        supporting.push(next);
      }
      const text = [line, ...supporting].join(' ');
      if (!candidates.some((item) => item.text.toLowerCase() === text.toLowerCase())) candidates.push({ line, text });
    }
  }

  const usedImages = new Set<number>();
  const medicines = candidates.map((candidate): OrganizedMedicine => {
    let bestIndex = -1;
    let bestScore = 0;
    medicineImages.forEach((imageText, imageIndex) => {
      if (usedImages.has(imageIndex)) return;
      const score = matchScore(candidate.text, imageText);
      if (score > bestScore) { bestIndex = imageIndex; bestScore = score; }
    });
    if (bestIndex >= 0 && bestScore >= 0.5) usedImages.add(bestIndex);
    else bestIndex = -1;
    return {
      title: medicineTitle(candidate.line),
      strength: '',
      form: '',
      quantity: '',
      frequency: '',
      durationDays: null,
      prescriptionItemNumber: null,
      instructions: candidate.text,
      times: explicitTimes(candidate.text),
      mealTiming: mealTimingFromText(candidate.text),
      sourceText: candidate.text,
      medicineImageIndex: bestIndex,
      prescriptionPageIndexes: [],
      confidence: null,
      uncertainties: [],
      matchQuality: bestIndex < 0 ? 'prescription-only' : bestScore >= 0.75 ? 'matched' : 'possible',
    };
  });

  if (!medicines.length && medicineImages.length) {
    return {
      medicines: [],
      unmatchedMedicineImageIndexes: medicineImages.map((_, index) => index),
      warnings: [
        'The prescription text was not clear enough to read safely. No medicine names were guessed from packaging.',
        'Review every strip photo and enter details only from the original prescription.',
      ],
    };
  }

  const unmatchedMedicineImageIndexes = medicineImages.map((_, index) => index).filter((index) => !usedImages.has(index));
  const warnings: string[] = [];
  if (medicines.some((medicine) => medicine.matchQuality === 'possible')) warnings.push('Some strip matches are only possible matches. Check the medicine name against the packaging.');
  if (medicines.some((medicine) => medicine.times.length === 0)) warnings.push('One or more prescriptions did not show an exact clock time. Add the prescribed time before saving.');
  if (unmatchedMedicineImageIndexes.length) warnings.push('Some strip photos could not be matched safely. Review, add, or ignore each one.');
  return { medicines, unmatchedMedicineImageIndexes, warnings };
}
