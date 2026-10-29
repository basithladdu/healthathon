// Shared data model for the Sahara emergency care plan (ECTPR) and living will (AMD).
// Source: SVK's tech spec §5 and clinical spec §3 (source-materials/team-context/svk-specs-2026-10-29).
// Choices are stored as enums, never translated text, so one record prints in any language.
// null always means "No preference recorded" = standard care (rule V1). Nothing is pre-selected.

export const FORM_VERSION = 'Sahara ECTPR v1.0';

export type GoalOfCare = 'A_LIFE' | 'B_CEILING' | 'C_COMFORT';
export type Cpr = 'ATTEMPT' | 'DNACPR';
export type DnacprReason = 'FUTILITY' | 'PATIENT_REFUSAL' | 'BURDEN_OUTWEIGHS_BENEFIT';
export type TransferReason =
  | 'PAIN' | 'BREATHLESSNESS' | 'BLEEDING' | 'SEIZURES' | 'FALL_FRACTURE'
  | 'BLOCKED_TUBE' | 'INFECTION_IV' | 'CONFUSION' | 'CAREGIVER_RESPITE';
export type HospitalTransfer = { kind: 'ANY' } | { kind: 'ONLY_FOR'; reasons: TransferReason[] } | { kind: 'NO' };
/** 0 comfort only · 1 nasal prongs/simple mask · 2 non-rebreather/venturi · 3 HFNC · 4 NIV · 5 intubation. Highest acceptable level. */
export type RespCeiling = 0 | 1 | 2 | 3 | 4 | 5;
export type TrialPlan = { durationValue: number; durationUnit: 'HOURS' | 'DAYS' | 'SESSIONS'; reviewAt: string; reviewers: string[] };
export type Icu = { kind: 'YES' } | { kind: 'TRIAL'; trial: TrialPlan } | { kind: 'NO' };
export type Vasopressors = { kind: 'NO' } | { kind: 'TRIAL'; trial: TrialPlan } | { kind: 'YES_NO_LIMIT' } | { kind: 'YES_MAX_AGENTS'; maxAgents: number };
export type Dialysis = { kind: 'NO' } | { kind: 'TRIAL'; trial: TrialPlan } | { kind: 'YES' } | { kind: 'ONLY_IF_REVERSIBLE' };
export type NgFeeding = { kind: 'NO' } | { kind: 'UP_TO_DAYS'; days: number } | { kind: 'YES' };
export type PegFeeding = 'NO' | 'ONLY_AFTER_PALLIATIVE_DISCUSSION' | 'YES';
export type Hydration = 'ORAL_SIPS' | 'LIMITED' | 'FULL';
export type AmbulanceOption = 'FULL_RESUSCITATION' | 'SYMPTOM_RELIEF_TRANSPORT' | 'TRANSPORT_HOME_NURSE_ESCORT';
export type Capacity = 'HAS' | 'HAS_DELEGATED' | 'FLUCTUATES' | 'LACKS';
export type AuthorityBasis = 'NAMED_IN_AMD' | 'NAMED_BY_PATIENT' | 'NEXT_OF_KIN' | 'COURT_APPOINTED';
export type IdType = 'AADHAAR' | 'VOTER_ID' | 'PAN' | 'PASSPORT' | 'DRIVING_LICENCE' | 'RATION_CARD' | 'OTHER';
export type ExistingDirective = { kind: 'VALID_AMD'; date: string; location: string; attestor: string } | { kind: 'PRIOR_WISHES'; note: string } | { kind: 'NONE' };
export type ImplantedDevice = 'NONE' | 'PACEMAKER' | 'ICD' | 'CRT_D' | 'OTHER';

export type DecisionMaker = {
  name: string; relation: string; phone24h: string;
  /** Never a full Aadhaar: ID type + last 4 digits only (tech spec §2). */
  idType: IdType | null; idLast4: string | null;
  authorityBasis: AuthorityBasis | null;
};

export type EctprStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'REVOKED';

export type Signature = {
  role: 'TREATING_CONSULTANT' | 'PALLIATIVE_CLINICIAN' | 'NURSE' | 'PATIENT' | 'SURROGATE' | 'WITNESS';
  name: string; registrationNo?: string; signedAt: string; thumbImpression?: boolean;
};

export type EctprRecord = {
  id: string; patientId: string; version: number; formVersion: string; status: EctprStatus;
  createdAt: string; updatedAt: string; createdBy: string;
  createdInEd?: boolean;
  patient: { name: string; dob: string; sex: string; address: string; phone: string; uhid: string; languagePref: string };
  careTeam: { oncologist: string; palliativePhysician: string; palliativePhone24h: string; extraTeam: string[]; localDoctor: string };
  decisionMakers: { primary: DecisionMaker | null; alternate: DecisionMaker | null; familyToInform: string[]; familyConsensusNote: string };
  clinical: { diagnosis: string; stage: string; ecog: 0 | 1 | 2 | 3 | 4 | null; allergies: string; implantedDevice: ImplantedDevice | null; icdDeactivation: { decision: 'DEACTIVATE' | 'KEEP' | null; date: string } ; keyMedicines: string; eligibilityDate: string };
  capacity: { status: Capacity | null; assessor: string; assessedOn: string; basis: string; reassessOn: string };
  existingDirective: ExistingDirective | null;
  /** V4 gate: "illness explained as incurable; family can repeat it back". */
  prognosticUnderstanding: { confirmed: boolean; confirmedBy: string; confirmedOn: string };
  /** V5: surrogate's substituted-judgement declaration. */
  surrogateDeclaration: { answeringAsSurrogate: boolean; confirmed: boolean; confirmedOn: string };
  values: { patientKnows: boolean | null; ownWords: string; unacceptableOutcomes: string[]; familyAskedNotToTell: boolean; directQuestionPlan: string; financialConcernDiscussed: boolean | null; socialWorkReferral: boolean | null };
  choices: {
    goalOfCare: GoalOfCare | null;
    cpr: Cpr | null; dnacprReason: DnacprReason | null; dnacprDiscussedWith: string;
    dnacprDecidedBy: { name: string; registrationNo: string; decidedAt: string } | null;
    cprConflictReason: string; // V2 reason
    hospitalTransfer: HospitalTransfer | null;
    callBeforeAmbulance: string[];
    ambulance: AmbulanceOption | null;
    respCeiling: RespCeiling | null; maxOxygenLpm: number | null;
    ceilingConditions: { reversibleCauseException: boolean | null; nivMaxHours: number | null; spo2Floor: number | null; homeNivReason: string };
    icu: Icu | null; vasopressors: Vasopressors | null; dialysis: Dialysis | null;
    feeding: { comfortOral: true; ng: NgFeeding | null; peg: PegFeeding | null; removeExistingTube: boolean };
    hydration: Hydration | null;
    conflictReasons: Partial<Record<'C1' | 'C2' | 'C3', string>>;
    goalAConfirmed: boolean; // V3 confirmation
  };
  reviewDue: string;
  signatures: Signature[];
  limitedLiteracy: boolean;
  revokedAt?: string; revokedReason?: string;
  supersedes?: string;
};

export type RuleIssue = { id: string; severity: 'block' | 'confirm' | 'warn'; field: string; message: string };

export type AmdStatus = 'DRAFT' | 'GENERATED' | 'EXECUTED' | 'SUPERSEDED' | 'REVOKED';

export type AuditEvent = { id: string; at: string; actor: string; action: 'VIEW' | 'CREATE' | 'EDIT' | 'SIGN' | 'PRINT' | 'SHARE' | 'REVOKE' | 'BREAK_GLASS' | 'REVIEW' | 'UPLOAD'; recordType: 'ECTPR' | 'AMD' | 'CARD'; recordId: string; detail?: string };
