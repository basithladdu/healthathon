// Sahara ECTPR rules: validation (tech spec V1–V9, L1), consistency checks (clinical spec C1–C8),
// ED interpretation (clinical spec §6) and review timing (§18.4). Pure TypeScript, no UI, no storage.
// Messages are written for a Class 8 reading level.

import { FORM_VERSION } from './ectpr-model.ts';
import type { EctprRecord, RuleIssue, TrialPlan, Signature } from './ectpr-model.ts';

// ---------------------------------------------------------------------------
// Constants

/** Routine review interval in weeks. [Sign-off] Medical lead to confirm (clinical spec §6, §9). */
export const ROUTINE_REVIEW_WEEKS = 12;
/** Default time-limited-trial length in days. [Sign-off] Sahaara §10 suggests about 3 days (usual range 3–7). */
export const TLT_DEFAULT_DAYS = 3;
export const ADULT_AGE_YEARS = 18;

/** V1: how an unanswered field is always shown. Never read as refusal. */
export const NO_PREFERENCE_LABEL = 'No preference recorded';
export const STANDARD_CARE_LABEL = 'No preference recorded — give standard care';

/** V9 / Sahaara §16: care that always continues, whatever else is recorded. Printed on every record; not editable. */
export const ALWAYS_PROVIDED_CARE: readonly string[] = [
  'Mouth care and lip care, to keep the mouth moist and clean.',
  'Food and drink by mouth, given by hand for comfort, as much as the person wants and can safely swallow.',
  'Relief of pain, breathlessness and other distressing symptoms, at any time of day or night.',
  'Washing, hygiene, skin care and pressure-area care.',
  'Turning and positioning for comfort.',
  'Family and loved ones may stay with the person.',
  'Spiritual, emotional and psychological support for the person and family.',
  'Dignity, privacy and kindness in all care.',
];

/** Sahaara §1 "If in doubt, resuscitate": give full standard emergency care when any of these is true. */
export const IF_IN_DOUBT_RESUSCITATE: readonly string[] = [
  'The record is unsigned, out of date, or cannot be found.',
  'The problem is new, reversible and not caused by the main illness — for example injury, choking, a drug reaction or low blood sugar.',
  'The patient can decide for themselves and now says something different.',
  'The record is unclear.',
];

/** Listed reasons for hospital transfer (clinical spec §3, Sahaara §7). */
export const TRANSFER_REASON_LABELS = {
  PAIN: 'Pain that is not controlled',
  BREATHLESSNESS: 'Breathlessness that is not controlled',
  BLEEDING: 'Heavy bleeding',
  SEIZURES: 'Fits (seizures)',
  FALL_FRACTURE: 'A fall with a possible broken bone',
  BLOCKED_TUBE: 'A blocked or moved catheter, tube or stent',
  INFECTION_IV: 'An infection that may need a drip (IV) medicine',
  CONFUSION: 'Sudden confusion or restlessness that does not settle',
  CAREGIVER_RESPITE: 'The carer can no longer cope (respite)',
} as const;

// ---------------------------------------------------------------------------
// V1 helpers and factory

/** V1: true when nothing has been chosen. Null/undefined = "No preference recorded" = standard care, never refusal. */
export function noPreference(value: unknown): value is null | undefined {
  return value === null || value === undefined;
}

/** The emergency decision fields, in ED Quick View order (clinical spec §6). */
export const DECISION_FIELDS = [
  'choices.hospitalTransfer', 'choices.cpr', 'choices.respCeiling', 'choices.icu', 'choices.goalOfCare',
  'choices.vasopressors', 'choices.dialysis', 'choices.feeding.ng', 'choices.feeding.peg', 'choices.hydration', 'choices.ambulance',
] as const;
export type DecisionField = (typeof DECISION_FIELDS)[number];

function decisionValue(record: EctprRecord, field: DecisionField): unknown {
  const c = record.choices;
  switch (field) {
    case 'choices.hospitalTransfer': return c.hospitalTransfer;
    case 'choices.cpr': return c.cpr;
    case 'choices.respCeiling': return c.respCeiling;
    case 'choices.icu': return c.icu;
    case 'choices.goalOfCare': return c.goalOfCare;
    case 'choices.vasopressors': return c.vasopressors;
    case 'choices.dialysis': return c.dialysis;
    case 'choices.feeding.ng': return c.feeding.ng;
    case 'choices.feeding.peg': return c.feeding.peg;
    case 'choices.hydration': return c.hydration;
    case 'choices.ambulance': return c.ambulance;
  }
}

/** Decision fields with no preference recorded (each one = standard care). */
export function unrecordedDecisions(record: EctprRecord): DecisionField[] {
  return DECISION_FIELDS.filter((field) => noPreference(decisionValue(record, field)));
}

function anyTreatmentChosen(record: EctprRecord): boolean {
  const c = record.choices;
  return unrecordedDecisions(record).length < DECISION_FIELDS.length || c.feeding.removeExistingTube || c.maxOxygenLpm !== null
    || c.ceilingConditions.nivMaxHours !== null || c.ceilingConditions.spo2Floor !== null || c.ceilingConditions.reversibleCauseException !== null
    || c.dnacprReason !== null;
}

function anyValuesEntered(record: EctprRecord): boolean {
  const v = record.values;
  return v.ownWords.trim() !== '' || v.unacceptableOutcomes.length > 0;
}

export function newId(prefix: string): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}-${random}`;
}

/** A fresh DRAFT version 1 with every choice null (V1: nothing pre-selected). */
export function emptyEctpr(patientId: string, patient: Partial<EctprRecord['patient']>, createdBy: string, now: Date = new Date()): EctprRecord {
  const at = now.toISOString();
  return {
    id: newId('ectpr'), patientId, version: 1, formVersion: FORM_VERSION, status: 'DRAFT',
    createdAt: at, updatedAt: at, createdBy,
    patient: { name: '', dob: '', sex: '', address: '', phone: '', uhid: '', languagePref: '', ...patient },
    careTeam: { oncologist: '', palliativePhysician: '', palliativePhone24h: '', extraTeam: [], localDoctor: '' },
    decisionMakers: { primary: null, alternate: null, familyToInform: [], familyConsensusNote: '' },
    clinical: { diagnosis: '', stage: '', ecog: null, allergies: '', implantedDevice: null, icdDeactivation: { decision: null, date: '' }, keyMedicines: '', eligibilityDate: '' },
    capacity: { status: null, assessor: '', assessedOn: '', basis: '', reassessOn: '' },
    existingDirective: null,
    prognosticUnderstanding: { confirmed: false, confirmedBy: '', confirmedOn: '' },
    surrogateDeclaration: { answeringAsSurrogate: false, confirmed: false, confirmedOn: '' },
    values: { patientKnows: null, ownWords: '', unacceptableOutcomes: [], familyAskedNotToTell: false, directQuestionPlan: '', financialConcernDiscussed: null, socialWorkReferral: null },
    choices: {
      goalOfCare: null,
      cpr: null, dnacprReason: null, dnacprDiscussedWith: '', dnacprDecidedBy: null, cprConflictReason: '',
      hospitalTransfer: null, callBeforeAmbulance: [], ambulance: null,
      respCeiling: null, maxOxygenLpm: null,
      ceilingConditions: { reversibleCauseException: null, nivMaxHours: null, spo2Floor: null, homeNivReason: '' },
      icu: null, vasopressors: null, dialysis: null,
      feeding: { comfortOral: true, ng: null, peg: null, removeExistingTube: false },
      hydration: null,
      conflictReasons: {},
      goalAConfirmed: false,
    },
    reviewDue: '',
    signatures: [],
    limitedLiteracy: false,
  };
}

// ---------------------------------------------------------------------------
// Dates

/** Parse 'YYYY-MM-DD' (or a full ISO string) to a Date; null when invalid or empty. */
export function parseDate(value: string | null | undefined): Date | null {
  if (!value || !value.trim()) return null;
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (dateOnly) {
    const [y, m, d] = [Number(dateOnly[1]), Number(dateOnly[2]), Number(dateOnly[3])];
    const date = new Date(y, m - 1, d);
    return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d ? date : null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Local calendar date as 'YYYY-MM-DD'. */
export function isoDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate() + days, date.getHours(), date.getMinutes());
  return next;
}

/** Age in whole years on `now`; null when the DOB is missing or invalid. */
export function ageInYears(dob: string, now: Date = new Date()): number | null {
  const birth = parseDate(dob);
  if (!birth) return null;
  let age = now.getFullYear() - birth.getFullYear();
  const beforeBirthday = now.getMonth() < birth.getMonth() || (now.getMonth() === birth.getMonth() && now.getDate() < birth.getDate());
  if (beforeBirthday) age -= 1;
  return age;
}

/** True when the review-due date is before today (a review due today is still in date). */
/** Tech spec §8 "review SMS": the reminder is offered once a signed plan's review is due within this many days. */
export const REVIEW_REMINDER_DAYS = 14;

/** Whole days until the review date (negative = overdue), or null when the plan has no valid review date. */
export function daysUntilReview(record: EctprRecord, now: Date = new Date()): number | null {
  const due = parseDate(record.reviewDue);
  if (!due) return null;
  const startOfDay = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  return Math.round((startOfDay(due) - startOfDay(now)) / 86_400_000);
}

/** The reminder is for the plan in use only, and only inside the window (an overdue plan still counts). */
export function reviewReminderDue(record: EctprRecord, now: Date = new Date()): boolean {
  const days = daysUntilReview(record, now);
  return record.status === 'ACTIVE' && days !== null && days <= REVIEW_REMINDER_DAYS;
}

export function isOutOfDate(record: EctprRecord, now: Date = new Date()): boolean {
  const due = parseDate(record.reviewDue);
  if (!due) return false;
  return isoDay(due) < isoDay(now);
}

// ---------------------------------------------------------------------------
// Review triggers (Sahaara §18.4)

export type ReviewTrigger = 'NEW_ADMISSION' | 'TRANSFER_BETWEEN_SETTINGS' | 'SIGNIFICANT_CHANGE' | 'PATIENT_OR_FAMILY_REQUEST' | 'NEW_LINE_OF_TREATMENT' | 'ROUTINE_INTERVAL';

export const reviewTriggers: readonly { id: ReviewTrigger; label: string }[] = [
  { id: 'NEW_ADMISSION', label: 'Any new admission to hospital' },
  { id: 'TRANSFER_BETWEEN_SETTINGS', label: 'Move between settings (home, hospice, ward, another hospital)' },
  { id: 'SIGNIFICANT_CHANGE', label: 'A big change in condition, better or worse' },
  { id: 'PATIENT_OR_FAMILY_REQUEST', label: 'The patient or family asks for a review' },
  { id: 'NEW_LINE_OF_TREATMENT', label: 'A new line of treatment is started' },
  { id: 'ROUTINE_INTERVAL', label: `Routine review every ${ROUTINE_REVIEW_WEEKS} weeks` },
];

/**
 * Review-due date ('YYYY-MM-DD').
 * - No trigger or ROUTINE_INTERVAL: `fromDate` + ROUTINE_REVIEW_WEEKS (use after signing a plan).
 * - Any event trigger (admission, transfer, change, request, new treatment): the review is due now, so this returns `fromDate`.
 */
export function nextReviewDue(fromDate: Date | string, trigger?: ReviewTrigger): string {
  const from = typeof fromDate === 'string' ? parseDate(fromDate) ?? new Date() : fromDate;
  if (trigger && trigger !== 'ROUTINE_INTERVAL') return isoDay(from);
  return isoDay(addDays(from, ROUTINE_REVIEW_WEEKS * 7));
}

/** A starting time-limited trial: TLT_DEFAULT_DAYS, review at the end, reviewers still to be named. */
export function defaultTrialPlan(from: Date = new Date()): TrialPlan {
  return { durationValue: TLT_DEFAULT_DAYS, durationUnit: 'DAYS', reviewAt: addDays(from, TLT_DEFAULT_DAYS).toISOString(), reviewers: [] };
}

// ---------------------------------------------------------------------------
// Locks

/** V4: treatment sections stay locked until "illness explained as incurable; family can repeat it back" is ticked. */
export function treatmentLocked(record: EctprRecord): boolean {
  return !record.prognosticUnderstanding.confirmed;
}

/** V5: when a surrogate is answering, their entries stay locked until the substituted-judgement declaration is confirmed. */
export function surrogateEntriesLocked(record: EctprRecord): boolean {
  return record.surrogateDeclaration.answeringAsSurrogate && !record.surrogateDeclaration.confirmed;
}

/** C6: removing an existing feeding tube is withdrawal and needs the medical-board pathway. Never actionable from the ECTPR alone. */
export function boardPathwayRequired(record: EctprRecord): boolean {
  return record.choices.feeding.removeExistingTube;
}

/** V7: the signature that can activate a plan — treating consultant with a registration number. */
export function consultantSignature(record: EctprRecord): Signature | undefined {
  return record.signatures.find((s) => s.role === 'TREATING_CONSULTANT' && s.name.trim() !== '' && (s.registrationNo ?? '').trim() !== '');
}

/** C7: line the ED view must show when capacity fluctuates. */
export function capacityFlag(record: EctprRecord): string | null {
  return record.capacity.status === 'FLUCTUATES' ? 'Capacity fluctuates — reassess before acting on any section' : null;
}

// ---------------------------------------------------------------------------
// V8 page footer

/** V8: footer for every printed page — name, UHID, version, form version, print date, page x of y. */
export function pageFooter(record: EctprRecord, page: number, pages: number, printDate: Date | string = new Date()): string {
  const printed = typeof printDate === 'string' ? printDate : isoDay(printDate);
  const name = record.patient.name.trim() || 'Name not recorded';
  const uhid = record.patient.uhid.trim() || 'not recorded';
  return `${name} · UHID ${uhid} · Version ${record.version} · ${record.formVersion} · Printed ${printed} · Page ${page} of ${pages}`;
}

// ---------------------------------------------------------------------------
// Validation

function issue(id: string, severity: RuleIssue['severity'], field: string, message: string, stage: RuleIssue['stage'] = 'save'): RuleIssue {
  return { id, severity, field, message, stage };
}

function trialIssues(trial: TrialPlan, key: string, label: string, field: string): RuleIssue[] {
  const out: RuleIssue[] = [];
  if (!(trial.durationValue > 0)) out.push(issue(`C4-${key}-duration`, 'block', `${field}.trial.durationValue`, `The ${label} trial needs a length of more than 0.`));
  if (!parseDate(trial.reviewAt)) out.push(issue(`C4-${key}-review`, 'block', `${field}.trial.reviewAt`, `Write the date and time when the ${label} trial will be reviewed.`));
  if (!trial.reviewers.some((r) => r.trim() !== '')) out.push(issue(`C4-${key}-reviewers`, 'block', `${field}.trial.reviewers`, `Name who will review the ${label} trial.`));
  return out;
}

/**
 * Every rule for one record. `stage: 'save'` issues with severity 'block' stop the draft being saved;
 * 'activate' issues (and any unresolved 'confirm') stop signing/activation. 'warn' never blocks.
 */
export function validateEctpr(record: EctprRecord, now: Date = new Date()): RuleIssue[] {
  const out: RuleIssue[] = [];
  const c = record.choices;
  const goalC = c.goalOfCare === 'C_COMFORT';
  const goalA = c.goalOfCare === 'A_LIFE';
  const ceiling = c.respCeiling;
  const pressorsYes = c.vasopressors?.kind === 'YES_NO_LIMIT' || c.vasopressors?.kind === 'YES_MAX_AGENTS';

  // L1 adult only.
  const age = ageInYears(record.patient.dob, now);
  if (age !== null && age < ADULT_AGE_YEARS) {
    out.push(issue('L1', 'block', 'patient.dob', 'This plan is only for adults. The date of birth shows the patient is under 18.'));
  } else if (age === null) {
    out.push(issue('L1-dob', 'block', 'patient.dob', 'Enter the date of birth. Sahara plans are only for adults (18 or older).', 'activate'));
  }

  // ED encounter warning (Appendix D.2).
  if (record.createdInEd && !record.supersedes) {
    out.push(issue('ED-new', 'warn', 'createdInEd', 'A new plan started in the emergency room may record panic, not real wishes. If you can, make the plan when things are calm: at a clinic visit, at discharge, or at home.'));
  }

  // V1 — unanswered fields mean standard care.
  const unrecorded = unrecordedDecisions(record);
  if (unrecorded.length > 0) {
    out.push(issue('V1', 'warn', unrecorded[0], unrecorded.length === DECISION_FIELDS.length
      ? 'Nothing has been chosen yet. Every section will show "No preference recorded" and standard care will be given.'
      : `${unrecorded.length} section(s) have no preference recorded. Standard care will be given for these. This is never read as a refusal.`));
  }

  // V4 — prognostic understanding gate.
  if (treatmentLocked(record) && anyTreatmentChosen(record)) {
    out.push(issue('V4', 'block', 'prognosticUnderstanding.confirmed', 'First confirm that the illness has been explained as not curable and the family can say it back. Treatment choices stay locked until then.'));
  }

  // V5 — surrogate declaration.
  if (surrogateEntriesLocked(record)) {
    const filled = anyTreatmentChosen(record) || anyValuesEntered(record);
    out.push(issue('V5', 'block', 'surrogateDeclaration.confirmed',
      'The person answering for the patient must first promise to say what the patient would have chosen, not what they want themselves.',
      filled ? 'save' : 'activate'));
  }

  // Values blank but treatment filled — "ceiling creep" audit flag (clinical spec §8).
  if (anyTreatmentChosen(record) && !anyValuesEntered(record) && record.values.patientKnows === null) {
    out.push(issue('AUDIT-values', 'warn', 'values.ownWords', 'Treatment limits are filled in but the patient\'s values are blank. Write what matters to the patient, in their own words.'));
  }

  // V2 — Goal C or ceiling below 5 with Attempt CPR.
  if (c.cpr === 'ATTEMPT' && (goalC || (ceiling !== null && ceiling < 5)) && !c.cprConflictReason.trim()) {
    out.push(issue('V2', 'block', 'choices.cprConflictReason', goalC
      ? 'The goal is comfort, but CPR is set to "Attempt". CPR needs a breathing tube and intensive care. Write the reason, or think about DNACPR.'
      : 'The breathing limit is below a breathing tube, but CPR is set to "Attempt". CPR needs a breathing tube. Write the reason, or think about DNACPR.'));
  }

  // DNACPR details (Sahaara §8).
  if (c.cpr === 'DNACPR') {
    if (!c.dnacprReason) out.push(issue('DNACPR-reason', 'block', 'choices.dnacprReason', 'Choose the reason for "Do not attempt CPR".'));
    if (!c.dnacprDiscussedWith.trim()) out.push(issue('DNACPR-discussed', 'block', 'choices.dnacprDiscussedWith', 'Write who the DNACPR decision was discussed with.'));
    const by = c.dnacprDecidedBy;
    if (!by || !by.name.trim() || !by.registrationNo.trim() || !parseDate(by.decidedAt)) {
      out.push(issue('DNACPR-decided-by', 'block', 'choices.dnacprDecidedBy', 'Write the name, registration number, and date and time of the doctor who made the DNACPR decision.', 'activate'));
    }
  }

  // V3 — Goal A with limits.
  if (goalA && ((ceiling !== null && ceiling < 5) || c.icu?.kind === 'NO') && !c.goalAConfirmed) {
    out.push(issue('V3', 'confirm', 'choices.goalAConfirmed', 'The goal is to prioritise life, but breathing support or intensive care is limited. This is often a mistake. Please check and confirm.'));
  }

  // Transfer details.
  if (c.hospitalTransfer?.kind === 'ONLY_FOR' && c.hospitalTransfer.reasons.length === 0) {
    out.push(issue('TRANSFER-reasons', 'block', 'choices.hospitalTransfer.reasons', 'Choose at least one reason for going to hospital.'));
  }
  if (c.hospitalTransfer?.kind === 'NO' && !c.callBeforeAmbulance.some((n) => n.trim() !== '')) {
    out.push(issue('TRANSFER-call-first', 'warn', 'choices.callBeforeAmbulance', 'Add the phone numbers to call before an ambulance, so the family knows who to ring first.'));
  }
  if (ceiling === 1 && c.maxOxygenLpm === null) {
    out.push(issue('RESP-max-oxygen', 'warn', 'choices.maxOxygenLpm', 'Write the highest oxygen flow allowed (litres per minute).'));
  }

  // C1 — no transfer but ICU or ceiling ≥ 3.
  const icuWanted = c.icu?.kind === 'YES' || c.icu?.kind === 'TRIAL';
  if (c.hospitalTransfer?.kind === 'NO' && (icuWanted || (ceiling !== null && ceiling >= 3))) {
    const homeNivOk = !icuWanted && c.ceilingConditions.homeNivReason.trim() !== '';
    if (!homeNivOk && !(c.conflictReasons.C1 ?? '').trim()) {
      out.push(issue('C1', 'confirm', 'choices.conflictReasons.C1', 'The plan says "do not go to hospital", but high-flow oxygen, NIV or intensive care usually need a hospital stay. Write a reason (for example, NIV at home) or change the plan.'));
    }
  }

  // C2 — Goal C with ICU, vasopressors or dialysis = Yes.
  if (goalC && (c.icu?.kind === 'YES' || pressorsYes || c.dialysis?.kind === 'YES') && !(c.conflictReasons.C2 ?? '').trim()) {
    out.push(issue('C2', 'block', 'choices.conflictReasons.C2', 'The goal is comfort, but intensive care, blood-pressure drugs or dialysis is set to "Yes". Write the reason before saving.'));
  }

  // C3 — ceiling 5 or vasopressors yes, ICU no.
  if ((ceiling === 5 || pressorsYes) && c.icu?.kind === 'NO' && !(c.conflictReasons.C3 ?? '').trim()) {
    out.push(issue('C3', 'confirm', 'choices.conflictReasons.C3', 'A breathing tube or blood-pressure drugs need intensive care (ICU or HDU), but ICU is set to "No". Write a reason or change the plan.'));
  }

  // C4 — every trial complete.
  if (c.icu?.kind === 'TRIAL') out.push(...trialIssues(c.icu.trial, 'icu', 'ICU', 'choices.icu'));
  if (c.vasopressors?.kind === 'TRIAL') out.push(...trialIssues(c.vasopressors.trial, 'vasopressors', 'blood-pressure drug', 'choices.vasopressors'));
  if (c.dialysis?.kind === 'TRIAL') out.push(...trialIssues(c.dialysis.trial, 'dialysis', 'dialysis', 'choices.dialysis'));
  if (c.ceilingConditions.nivMaxHours !== null && !(c.ceilingConditions.nivMaxHours > 0)) {
    out.push(issue('C4-niv-duration', 'block', 'choices.ceilingConditions.nivMaxHours', 'The NIV trial needs a number of hours more than 0.'));
  }
  if (c.vasopressors?.kind === 'YES_MAX_AGENTS' && !(c.vasopressors.maxAgents > 0)) {
    out.push(issue('VASO-max-agents', 'block', 'choices.vasopressors.maxAgents', 'Write the highest number of blood-pressure drugs allowed (1 or more).'));
  }
  if (c.feeding.ng?.kind === 'UP_TO_DAYS' && !(c.feeding.ng.days > 0)) {
    out.push(issue('C4-ng-duration', 'block', 'choices.feeding.ng.days', 'Write how many days to try the nose tube (NG) before review (1 or more).'));
  }

  // C5 — ICD with DNACPR or comfort goal.
  const hasIcd = record.clinical.implantedDevice === 'ICD' || record.clinical.implantedDevice === 'CRT_D';
  if (hasIcd && (c.cpr === 'DNACPR' || goalC) && (!record.clinical.icdDeactivation.decision || !parseDate(record.clinical.icdDeactivation.date))) {
    out.push(issue('C5', 'warn', 'clinical.icdDeactivation', 'The patient has a heart device that gives shocks (ICD). With DNACPR or a comfort goal, these shocks can cause pain at the end of life. Record the decision to switch it off or keep it, and the date.'));
  }

  // C6 — removing an existing feeding tube.
  if (boardPathwayRequired(record)) {
    out.push(issue('C6', 'warn', 'choices.feeding.removeExistingTube', 'Taking out a feeding tube that is already in place is stopping treatment. It needs the medical board process. This plan alone cannot be used to remove it.'));
  }

  // C7 — fluctuating capacity.
  const cap = record.capacity;
  if (cap.status === 'FLUCTUATES' && !parseDate(cap.reassessOn)) {
    out.push(issue('C7', 'block', 'capacity.reassessOn', 'The patient\'s ability to decide comes and goes. Set a date to check it again.'));
  }
  if ((cap.status === 'FLUCTUATES' || cap.status === 'LACKS') && (!cap.assessor.trim() || !parseDate(cap.assessedOn) || !cap.basis.trim())) {
    out.push(issue('CAP-assessment', 'block', 'capacity', 'Write who checked the patient\'s ability to decide, the date, and how they decided (understand, remember, weigh up, communicate).'));
  }
  if (cap.status === null) {
    out.push(issue('CAP-missing', 'block', 'capacity.status', 'Record whether the patient can make this decision today.', 'activate'));
  }
  if ((cap.status === 'LACKS' || cap.status === 'FLUCTUATES') && !record.decisionMakers.primary) {
    out.push(issue('DM-missing', 'warn', 'decisionMakers.primary', 'The patient may not be able to decide. Add the person who will speak for them, with a phone number that works day and night.'));
  }

  // C8 — family asked not to tell a patient who has capacity.
  if (record.values.familyAskedNotToTell && (cap.status === 'HAS' || cap.status === 'HAS_DELEGATED') && !record.values.directQuestionPlan.trim()) {
    out.push(issue('C8', 'block', 'values.directQuestionPlan', 'The family asked that the patient not be told, but the patient can decide. If the patient asks directly, they must get an honest answer. Write the plan for this.'));
  }

  // Financial concern (clinical spec §8, [Sign-off]).
  const limiting = c.goalOfCare === 'B_CEILING' || goalC || c.cpr === 'DNACPR' || c.icu?.kind === 'NO';
  if (limiting && record.values.financialConcernDiscussed !== true) {
    out.push(issue('FIN-concern', 'warn', 'values.financialConcernDiscussed', 'Ask if money is part of this choice. Offer social work or a health-scheme check before you finish.'));
  }

  // Review date.
  if (!parseDate(record.reviewDue)) {
    out.push(issue('REVIEW-due', 'block', 'reviewDue', 'Set the date when this plan must be reviewed.', 'activate'));
  } else if (isOutOfDate(record, now)) {
    out.push(issue('REVIEW-past', 'warn', 'reviewDue', 'The review date has already passed. Choose a future date.'));
  }

  // V6 — two witnesses for thumb impression or limited literacy.
  const thumb = record.signatures.some((s) => s.thumbImpression);
  if (record.limitedLiteracy || thumb) {
    const witnesses = record.signatures.filter((s) => s.role === 'WITNESS' && s.name.trim() !== '');
    if (witnesses.length < 2) {
      out.push(issue('V6', 'block', 'signatures', 'A thumb impression or limited reading needs two witnesses to sign.', 'activate'));
    }
  }

  // V7 — consultant signature + registration number.
  if (!consultantSignature(record)) {
    out.push(issue('V7', 'block', 'signatures', 'The treating consultant must sign with their registration number. Until then this is a conversation note, not a plan.', 'activate'));
  }

  // V8 — footer fields present.
  if (!record.patient.name.trim() || !record.patient.uhid.trim()) {
    out.push(issue('V8', 'block', 'patient', 'Every printed page shows the name and hospital number (UHID). Enter both.', 'activate'));
  }

  return out;
}

/** True when the draft cannot be saved. */
export function blocksSave(issues: RuleIssue[]): boolean {
  return issues.some((i) => i.severity === 'block' && (i.stage ?? 'save') === 'save');
}

/** True when the record cannot be signed into ACTIVE: any block, or any unresolved confirm. */
export function blocksActivation(issues: RuleIssue[]): boolean {
  return issues.some((i) => i.severity === 'block' || i.severity === 'confirm');
}

// ---------------------------------------------------------------------------
// ED interpretation (clinical spec §6)

export type EdState = 'NONE' | 'UNSIGNED' | 'OUT_OF_DATE' | 'REVOKED' | 'ACTIVE';
export type EdInterpretation = {
  state: EdState;
  /** True when the record is information, not instruction — give full standard emergency care. */
  standardCareApplies: boolean;
  banner: string;
  /** Always shown: Sahaara §1 "If in doubt, resuscitate". */
  ifInDoubt: readonly string[];
  /** Extra lines for the ED view, e.g. C7 capacity flag, C6 board pathway. */
  flags: string[];
};

function readableDay(value: string): string {
  const date = parseDate(value);
  if (!date) return value || 'unknown date';
  return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

/** How the ED must read this record. Out-of-date, unsigned or missing = information, not instruction; standard care applies. */
export function edInterpretation(record: EctprRecord | null | undefined, now: Date = new Date()): EdInterpretation {
  const base = { ifInDoubt: IF_IN_DOUBT_RESUSCITATE };
  if (!record) {
    return { ...base, state: 'NONE', standardCareApplies: true, flags: [], banner: 'No signed emergency care plan was found. Give full standard emergency care. If in doubt, resuscitate.' };
  }
  const flags: string[] = [];
  const capacity = capacityFlag(record);
  if (capacity) flags.push(capacity);
  if (boardPathwayRequired(record)) flags.push('Removing the existing feeding tube needs the medical board process — not from this record alone.');

  if (record.status === 'REVOKED') {
    return { ...base, state: 'REVOKED', standardCareApplies: true, flags, banner: `CARD WITHDRAWN — this plan was cancelled${record.revokedAt ? ` on ${readableDay(record.revokedAt)}` : ''}. Give standard emergency care.` };
  }
  if (record.status === 'DRAFT' || !consultantSignature(record)) {
    return { ...base, state: 'UNSIGNED', standardCareApplies: true, flags, banner: 'NOT SIGNED — this is a conversation note, not a plan. It is information only. Give standard emergency care.' };
  }
  if (record.status === 'SUPERSEDED') {
    return { ...base, state: 'OUT_OF_DATE', standardCareApplies: true, flags, banner: `OUT OF DATE — a newer version of this plan exists (this is version ${record.version}). This is information, not instruction. Standard care applies. Find the current version.` };
  }
  if (!parseDate(record.reviewDue) || isOutOfDate(record, now)) {
    return { ...base, state: 'OUT_OF_DATE', standardCareApplies: true, flags, banner: `OUT OF DATE — review was due ${record.reviewDue ? `on ${readableDay(record.reviewDue)}` : 'but no date was set'}. This is information, not instruction. Standard care applies. Contact the treating team.` };
  }
  const unrecorded = unrecordedDecisions(record);
  const nothingChosen = unrecorded.length === DECISION_FIELDS.length;
  const signed = consultantSignature(record);
  return {
    ...base,
    state: 'ACTIVE',
    standardCareApplies: nothingChosen,
    flags,
    banner: nothingChosen
      ? `Signed plan, version ${record.version}, but no preference is recorded in any section. Give standard care.`
      : `Signed plan, version ${record.version}${signed ? `, signed by ${signed.name} on ${readableDay(signed.signedAt)}` : ''}, review due ${readableDay(record.reviewDue)}. Any section showing "${NO_PREFERENCE_LABEL}" means standard care.`,
  };
}
