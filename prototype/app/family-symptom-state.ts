import { careLangOf, careText, type CareLang } from './care-i18n.ts';
import { isValidAppointmentDate } from './appointment-state.ts';

export const SYMPTOM_SEVERITIES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
const LEGACY_SYMPTOM_SEVERITIES = ['Mild', 'Moderate', 'Severe'] as const;
export type SymptomSeverity = (typeof SYMPTOM_SEVERITIES)[number] | (typeof LEGACY_SYMPTOM_SEVERITIES)[number] | null;
export type SymptomRespondent = 'patient' | 'assisted-patient' | 'caregiver' | 'clinician';
export type SymptomRecall = 'now' | 'average-24h' | 'worst-24h';
export type SymptomChange = 'better' | 'same' | 'worse' | 'not-sure';
export type PainTrigger = 'rest' | 'turning' | 'walking' | 'coughing' | 'other' | 'not-sure';
export const SYMPTOM_SCALE = 'sahara-personal-nrs-v1' as const;
export type SymptomInput = {
  symptom: string;
  severity: SymptomSeverity;
  date: string;
  startedOn: string;
  note: string;
  scale?: typeof SYMPTOM_SCALE;
  time?: string;
  recall?: SymptomRecall;
  respondent?: SymptomRespondent;
  painLocation?: string;
  painTriggers?: PainTrigger[];
  selfReportedChange?: SymptomChange;
  comparedWithId?: string;
};
export type SymptomEntry = SymptomInput & {
  id: string;
  patientId: string;
  author: string;
  editedBy?: string;
  recordedAt?: string;
};
export type SymptomRange = 'all' | 'week' | 'today';
export type SymptomInputError = 'symptom' | 'severity' | 'date' | 'startedOn' | 'note' | 'context' | 'pain' | 'comparison';

const respondents: readonly string[] = ['patient', 'assisted-patient', 'caregiver', 'clinician'];
const recalls: readonly string[] = ['now', 'average-24h', 'worst-24h'];
const changes: readonly string[] = ['better', 'same', 'worse', 'not-sure'];
const painTriggers: readonly string[] = ['rest', 'turning', 'walking', 'coughing', 'other', 'not-sure'];

export function hasSymptomContext(input: SymptomInput): boolean {
  return input.scale === SYMPTOM_SCALE && /^([01]\d|2[0-3]):[0-5]\d$/.test(input.time ?? '')
    && recalls.includes(input.recall ?? '') && respondents.includes(input.respondent ?? '');
}

export function validateSymptomInput(input: SymptomInput, today: string): SymptomInputError | null {
  if (!input.symptom.trim() || input.symptom.trim().length > 80) return 'symptom';
  if (input.severity !== null && (typeof input.severity === 'number'
    ? !Number.isInteger(input.severity) || input.severity < 0 || input.severity > 10
    : !LEGACY_SYMPTOM_SEVERITIES.includes(input.severity))) return 'severity';
  if (!isValidAppointmentDate(today) || !isValidAppointmentDate(input.date) || input.date > today) return 'date';
  if (input.startedOn && (!isValidAppointmentDate(input.startedOn) || input.startedOn > input.date)) return 'startedOn';
  if (input.note.trim().length > 500) return 'note';
  const contextProvided = input.scale !== undefined || input.time !== undefined || input.recall !== undefined || input.respondent !== undefined;
  if (contextProvided && !hasSymptomContext(input)) return 'context';
  if ((input.painLocation?.trim().length ?? 0) > 100
    || (input.painTriggers !== undefined && (!Array.isArray(input.painTriggers) || input.painTriggers.some((value) => !painTriggers.includes(value))))) return 'pain';
  if ((input.painLocation || input.painTriggers?.length) && input.symptom.trim().toLowerCase() !== 'pain') return 'pain';
  if ((input.selfReportedChange !== undefined && !changes.includes(input.selfReportedChange))
    || Boolean(input.selfReportedChange) !== Boolean(input.comparedWithId)) return 'comparison';
  return null;
}

function cleanInput(input: SymptomInput): SymptomInput {
  return {
    symptom: input.symptom.trim(), severity: input.severity, date: input.date, startedOn: input.startedOn, note: input.note.trim(),
    ...(input.scale !== undefined ? { scale: input.scale, time: input.time, recall: input.recall, respondent: input.respondent } : {}),
    ...(input.painLocation !== undefined ? { painLocation: input.painLocation.trim() } : {}),
    ...(input.painTriggers !== undefined ? { painTriggers: [...new Set(input.painTriggers)].sort() } : {}),
    ...(input.selfReportedChange ? { selfReportedChange: input.selfReportedChange, comparedWithId: input.comparedWithId } : {}),
  };
}

function observationKey(input: SymptomInput): string { return `${input.date}T${input.time ?? ''}`; }

export function sameSymptomContext(a: SymptomEntry, b: SymptomEntry): boolean {
  return hasSymptomContext(a) && hasSymptomContext(b) && a.patientId === b.patientId
    && a.symptom.trim().toLowerCase() === b.symptom.trim().toLowerCase()
    && a.scale === b.scale && a.recall === b.recall && a.respondent === b.respondent
    && (!['caregiver', 'clinician'].includes(a.respondent ?? '') || a.author === b.author)
    && (a.painLocation ?? '').trim().toLowerCase() === (b.painLocation ?? '').trim().toLowerCase()
    && [...(a.painTriggers ?? [])].sort().join('|') === [...(b.painTriggers ?? [])].sort().join('|');
}

export function symptomComparison(entries: readonly SymptomEntry[], current: SymptomEntry): {
  baseline: SymptomEntry; previous: SymptomEntry; difference: number;
} | null {
  if (typeof current.severity !== 'number' || !hasSymptomContext(current)) return null;
  const earlier = entries.filter((entry) => entry.id !== current.id && typeof entry.severity === 'number'
    && sameSymptomContext(entry, current) && observationKey(entry) < observationKey(current))
    .sort((a, b) => observationKey(a).localeCompare(observationKey(b)) || a.id.localeCompare(b.id));
  if (!earlier.length) return null;
  const previous = earlier[earlier.length - 1];
  return { baseline: earlier[0], previous, difference: current.severity - (previous.severity as number) };
}

function validComparisonReference(entries: readonly SymptomEntry[], entry: SymptomEntry): boolean {
  if (!entry.selfReportedChange) return true;
  return symptomComparison(entries, entry)?.previous.id === entry.comparedWithId;
}

export function addSymptomEntry(entries: SymptomEntry[], entry: SymptomEntry, today: string): SymptomEntry[] {
  if (!entry.id || !entry.patientId || !entry.author.trim() || validateSymptomInput(entry, today)
    || entries.some((item) => item.id === entry.id) || !validComparisonReference(entries, entry)) return entries;
  return [...entries, { ...cleanInput(entry), id: entry.id, patientId: entry.patientId, author: entry.author.trim(),
    ...(entry.recordedAt ? { recordedAt: entry.recordedAt } : {}) }];
}

export function updateSymptomEntry(entries: SymptomEntry[], patientId: string, entryId: string, input: SymptomInput, author: string, today: string): SymptomEntry[] {
  const original = entries.find((entry) => entry.patientId === patientId && entry.id === entryId);
  if (!author.trim() || validateSymptomInput(input, today) || !original
    || !validComparisonReference(entries, { id: original.id, patientId: original.patientId, author: original.author, ...cleanInput(input) })) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === entryId
    ? { id: entry.id, patientId: entry.patientId, author: entry.author, recordedAt: entry.recordedAt, ...cleanInput(input), editedBy: author.trim() }
    : entry);
}

export function removeSymptomEntry(entries: SymptomEntry[], patientId: string, entryId: string): SymptomEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== entryId);
}

export function restoreSymptomEntry(entries: SymptomEntry[], patientId: string, removed: SymptomEntry, today: string): SymptomEntry[] {
  if (removed.patientId !== patientId || !removed.id || !removed.author.trim() || validateSymptomInput(removed, today)
    || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, { ...removed }];
}

export function selectSymptomEntries(entries: SymptomEntry[], patientId: string, today: string, range: SymptomRange = 'all', symptom = ''): SymptomEntry[] {
  const start = new Date(`${today}T12:00:00Z`);
  if (range !== 'all' && !isValidAppointmentDate(today)) return [];
  if (range === 'week') start.setUTCDate(start.getUTCDate() - 6);
  const earliest = range === 'all' ? '' : start.toISOString().slice(0, 10);
  return entries.filter((entry) => entry.patientId === patientId && (!symptom || entry.symptom === symptom)
    && (range === 'all' || (entry.date >= earliest && entry.date <= today)))
    .sort((a, b) => observationKey(b).localeCompare(observationKey(a)) || b.id.localeCompare(a.id));
}

export function symptomSeverityText(value: SymptomSeverity, hindi: boolean | CareLang = false): string {
  const lang = careLangOf(hindi);
  if (value === null) return careText(lang, 'Not rated', 'अंक नहीं बताया');
  if (typeof value === 'number') return `${value}/10`;
  const labels = { Mild: 'हल्का', Moderate: 'मध्यम', Severe: 'बहुत ज़्यादा' };
  return careText(lang, value, labels[value]);
}

export function symptomDiaryText(entries: SymptomEntry[], patientId: string, today: string, hindi: boolean | CareLang = false): string {
  const t = (en: string, hi: string) => careText(careLangOf(hindi), en, hi);
  return [
    t('Sahara — How I have been feeling', 'सहारा — मेरी तबीयत के नोट'),
    `${t('Saved on', 'कॉपी की तारीख')}: ${today}`,
    t('Patient/family observations. Not sent to the care team.', 'मरीज़ या परिवार के लिखे नोट। देखभाल टीम को नहीं भेजे गए हैं।'),
    ...selectSymptomEntries(entries, patientId, today).map((entry) => [
      `${entry.date}${entry.time ? ` ${entry.time}` : ''} — ${entry.symptom} — ${symptomSeverityText(entry.severity, hindi)}`,
      entry.recall ? `${t('Time frame', 'समय अवधि')}: ${symptomRecallText(entry.recall, hindi)}` : '',
      entry.respondent ? `${t('Reported by', 'किसने बताया')}: ${symptomRespondentText(entry.respondent, hindi)}` : '',
      entry.painLocation ? `${t('Pain location', 'दर्द की जगह')}: ${entry.painLocation}` : '',
      entry.painTriggers?.length ? `${t('When it happens', 'कब होता है')}: ${entry.painTriggers.map((value) => painTriggerText(value, hindi)).join(', ')}` : '',
      entry.selfReportedChange ? `${t('Their comparison', 'उनके मुताबिक बदलाव')}: ${symptomChangeText(entry.selfReportedChange, hindi)}` : '',
      entry.startedOn ? `${t('Started', 'शुरू हुआ')}: ${entry.startedOn}` : '',
      entry.note,
      `${t('Written by', 'लिखा')}: ${entry.author}${entry.editedBy ? `; ${t('edited by', 'बदला')}: ${entry.editedBy}` : ''}`,
    ].filter(Boolean).join('\n')),
  ].join('\n\n');
}

export function symptomRecallText(value: SymptomRecall, hindi: boolean | CareLang = false): string {
  const labels = { now: ['At this time', 'इस समय'], 'average-24h': ['Average over the previous 24 hours', 'पिछले 24 घंटे का औसत'], 'worst-24h': ['Worst in the previous 24 hours', 'पिछले 24 घंटे में सबसे ज़्यादा'] };
  return careText(careLangOf(hindi), labels[value][0], labels[value][1]);
}

export function symptomRespondentText(value: SymptomRespondent, hindi: boolean | CareLang = false): string {
  const labels = { patient: ['Patient', 'मरीज़'], 'assisted-patient': ['Patient, with help', 'मरीज़ ने मदद से बताया'], caregiver: ["Caregiver's observation", 'देखभाल करने वाले ने देखा'], clinician: ["Care team's observation", 'देखभाल टीम ने देखा'] };
  return careText(careLangOf(hindi), labels[value][0], labels[value][1]);
}

export function symptomChangeText(value: SymptomChange, hindi: boolean | CareLang = false): string {
  const labels = { better: ['Better', 'बेहतर'], same: ['Same', 'वैसा ही'], worse: ['Worse', 'ज़्यादा खराब'], 'not-sure': ['Not sure', 'पक्का नहीं'] };
  return careText(careLangOf(hindi), labels[value][0], labels[value][1]);
}

export function painTriggerText(value: PainTrigger, hindi: boolean | CareLang = false): string {
  const labels = { rest: ['At rest', 'आराम करते समय'], turning: ['Turning in bed', 'बिस्तर में करवट लेते समय'], walking: ['Standing or walking', 'खड़े होते या चलते समय'], coughing: ['Coughing', 'खाँसते समय'], other: ['Other situation', 'किसी और समय'], 'not-sure': ['Not sure', 'पक्का नहीं'] };
  return careText(careLangOf(hindi), labels[value][0], labels[value][1]);
}

/**
 * Verbal pain bands beside the 0-10 number, twin of PainBands in CareSymptomData.kt. NCCN adult cancer pain cut-offs:
 * 0 none, 1-3 mild, 4-6 moderate, 7-10 severe (Swarm et al., J Natl Compr Canc Netw 2010;8(9):1046-86). The number stays the record.
 */
export function painBandText(rating: number, hindi: boolean | CareLang = false): string {
  const lang = careLangOf(hindi);
  const value = Math.max(0, Math.min(10, Math.round(rating)));
  if (value === 0) return careText(lang, 'None', 'कोई नहीं');
  if (value <= 3) return careText(lang, 'Mild', 'हल्का');
  if (value <= 6) return careText(lang, 'Moderate', 'मध्यम');
  return careText(lang, 'Severe', 'तेज़');
}
export const PAIN_BANDS_SOURCE_EN = 'Source: bands from the NCCN Adult Cancer Pain guideline (Swarm et al., JNCCN 2010): 0 none, 1–3 mild, 4–6 moderate, 7–10 severe.';
export const PAIN_BANDS_SOURCE_HI = 'स्रोत: NCCN Adult Cancer Pain गाइडलाइन (Swarm et al., JNCCN 2010) के समूह: 0 कोई नहीं, 1–3 हल्का, 4–6 मध्यम, 7–10 तेज़।';
