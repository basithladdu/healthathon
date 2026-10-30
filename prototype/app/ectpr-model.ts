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
  /** Who signed the cancellation (added by ectpr-store). */
  revokedBy?: string;
  supersedes?: string;
  /** Only on an issued card's snapshot, never on the stored record: the living will status when the card was issued (the AMD itself stays on the issuing device). */
  amdAtIssue?: AmdStatusSummary | null;
};

/** stage (optional, added by ectpr-rules): 'save' issues stop a draft being saved; 'activate' issues only stop signing/activation. */
export type RuleIssue = { id: string; severity: 'block' | 'confirm' | 'warn'; field: string; message: string; stage?: 'save' | 'activate' };

export type AmdStatus = 'DRAFT' | 'GENERATED' | 'EXECUTED' | 'SUPERSEDED' | 'REVOKED';

export type AuditEvent = { id: string; at: string; actor: string; action: 'VIEW' | 'CREATE' | 'EDIT' | 'SIGN' | 'PRINT' | 'SHARE' | 'REVOKE' | 'BREAK_GLASS' | 'REVIEW' | 'UPLOAD' | 'DELETE'; recordType: 'ECTPR' | 'AMD' | 'CARD'; recordId: string; detail?: string; patientId?: string };

// Pilot audit entities (clinical spec §8, Appendix D.3). Synthetic data only during the hackathon.

/** One emergency episode: did the record reach the ED in time, and was it followed? */
export type EpisodeLog = {
  id: string; patientId: string; ectprId: string | null;
  edArrivalAt: string;
  /** First time anyone opened the record for this episode (from the access log). */
  firstRecordAccessAt: string | null;
  /** Highest breathing support actually delivered, same 0–5 scale as respCeiling. */
  highestSupportDelivered: RespCeiling | null;
  icuAdmitted: boolean | null; vasopressorsGiven: boolean | null; dialysisGiven: boolean | null;
  cprGiven: boolean | null;
  /** Why care differed from the recorded plan, if it did. */
  discordanceReason: string;
  recordedBy: string; recordedAt: string;
};

export type PlaceOfCare = 'HOME' | 'HOSPICE' | 'WARD' | 'ICU' | 'ED' | 'OTHER';

/** After-death outcome record for the pilot indicators. */
export type OutcomeRecord = {
  id: string; patientId: string;
  placeOfDeath: PlaceOfCare | null; preferredPlaceOfDeath: PlaceOfCare | null;
  icuDaysLast30: number | null; ventilatorDaysLast30: number | null;
  /** Last admission: time admitted, and time of the first symptom-control prescription. */
  lastAdmissionAt: string | null; firstSymptomControlRxAt: string | null;
  outOfPocketLastAdmissionInr: number | null;
  recordedBy: string; recordedAt: string;
};

/** Safety incident: plan applied to a reversible illness, or family felt pushed. */
export type IncidentReport = {
  id: string; patientId: string; ectprId: string | null;
  appliedToReversibleIllness: boolean; familyFeltPushed: boolean;
  details: string;
  reportedBy: string; reportedAt: string;
};

/** Tech spec §5 "Distribution" for a signed ECTPR: the signed paper scan and who has a copy. Kept apart from the record, because a signed record is never edited. */
export type EctprCopyKind = 'HOME' | 'FAMILY_TRAVEL' | 'FRONT_DOOR' | 'LOCAL_DOCTOR' | 'PALLIATIVE_TEAM';
export type EctprCopy = { kind: EctprCopyKind; given: boolean; givenOn: string };
export type EctprDistribution = {
  recordId: string; patientId: string;
  scan: { scanKey: string; scanName: string; scanType: string; scanSha256: string; scanUploadedAt: string; uploadedBy: string } | null;
  copies: EctprCopy[];
};

// ════════════════════════════════════════════════════════════════════════════
// AMD (living will) section — owned by the AMD screen (amd-screen.tsx, amd-rules.ts,
// amd-store.ts, amd-pdf.ts). Append-only; ECTPR types above are edited by other agents.
// Template: Vidhi Centre for Legal Policy / ACP India model AMD. Law: Common Cause (2018)
// as modified 24 Jan 2023 (MA 1699/2019 in WP(C) 215/2005); Harish Rana (11 Mar 2026).
// ════════════════════════════════════════════════════════════════════════════

export const AMD_FORM_VERSION = 'Sahara AMD v1.0 (Vidhi/ACP India model; SC order 24 Jan 2023)';

/** Conditions the directive applies to (Vidhi §2, footnotes 2–3). At least one is required. */
export type AmdCondition = 'TERMINAL' | 'END_STAGE' | 'VEGETATIVE';
/** Treatments listed in Vidhi §2. TUBE_FEEDING = clinically assisted nutrition and hydration, medical treatment per Harish Rana (L9). */
export type AmdTreatment = 'CPR' | 'VENTILATION' | 'DIALYSIS' | 'TUBE_FEEDING' | 'IV_FLUIDS_ANTIBIOTICS' | 'CHEMOTHERAPY' | 'RADIOTHERAPY' | 'SURGERY';
/** REFUSE = "I DO NOT WANT", ACCEPT = "I WANT", null = not stated (nothing pre-selected). There is no option to end life. */
export type AmdTreatmentChoice = 'REFUSE' | 'ACCEPT' | null;
export type AmdPlaceOfCare = 'HOSPITAL' | 'HOSPICE' | 'HOME' | 'NAMED_INSTITUTION';
export type AmdAttestorKind = 'NOTARY' | 'GAZETTED_OFFICER';
export type AmdRecipientKind = 'DHR' | 'FAMILY_PHYSICIAN' | 'CUSTODIAN';

/** Designated healthcare representative (Vidhi §4). Stored in preference order (index 0 = primary). */
export type AmdDhr = {
  id: string; name: string; relation: string; dob: string;
  /** Never a full Aadhaar: ID type + last 4 digits only (tech spec §2). */
  idType: IdType | null; idLast4: string;
  phone: string; phoneAlt: string; email: string;
  permanentAddress: string; currentAddress: string;
  /** "They have expressed that they are willing to be nominated" (Vidhi §4). */
  agreed: boolean;
};

export type AmdContent = {
  executant: {
    name: string; gender: string; dob: string;
    idType: IdType | null; idLast4: string;
    permanentAddress: string; currentAddress: string; localBody: string;
    /** Vidhi declaration first point: drafted as directed and read out (cannot read or write). */
    readOutToMe: boolean;
  };
  /** L1 declarations (Vidhi "Declaration"). */
  declarations: { capacity: boolean; consideredCarefully: boolean; voluntary: boolean; understandsNotAppliedIfUnclear: boolean; capacityCertificateAttached: boolean };
  values: string;
  conditions: AmdCondition[];
  anticipatedConditions: string;
  treatments: Record<AmdTreatment, AmdTreatmentChoice>;
  /** Comfort care and comfort feeding by mouth always continue (L9). Not editable. */
  comfortCareAlways: true; comfortOralFeedingAlways: true;
  wishes: { placeOfCare: AmdPlaceOfCare | null; institution: string; physician: string; focus: string; preferredName: string; genderPronouns: string; partnerName: string; partnerTerm: string };
  afterDeath: { organDonor: boolean; bodyDonation: boolean; bodyDonationInstitution: string; lastRites: string };
  dhrs: AmdDhr[];
  dhrsActJointly: boolean;
  familyPhysician: { name: string; registrationNo: string; phone: string; address: string };
  custodianBody: string;
};

export type AmdWitness = { name: string; address: string; idType: IdType | null; idLast4: string; email: string; phone: string };
export type AmdAttestor = { kind: AmdAttestorKind | null; name: string; designation: string; registrationNo: string };

export type AmdExecution = {
  signedOn: string; place: string; thumbImpression: boolean;
  witnesses: [AmdWitness, AmdWitness];
  attestor: AmdAttestor;
  /** Key into the local file store (care-local-store) for the signed, attested scan. */
  scanKey: string; scanName: string; scanType: string; scanSha256: string; scanUploadedAt: string;
};

/** L5 / decided: custodian acknowledgement, or "custodian pending" (few states have appointed custodians). */
export type AmdCustodian = { status: 'ACKNOWLEDGED' | 'PENDING'; body: string; officer: string; acknowledgedOn: string };

/** L4 + DPDP: one entry per copy recipient, each with its own consent. */
export type AmdCopy = {
  id: string; kind: AmdRecipientKind; name: string; dhrId?: string;
  consent: boolean; consentAt: string;
  given: boolean; givenOn: string; method: 'IN_PERSON' | 'POST' | 'DIGITAL' | null;
};

export type AmdRevocation = { revokedOn: string; writtenRevocationSigned: true; reason: string; recordedAt: string; scanKey?: string; scanSha256?: string };

export type AmdVersion = {
  id: string; patientId: string; version: number; formVersion: string; status: AmdStatus;
  createdAt: string; updatedAt: string; createdBy: string;
  content: AmdContent;
  generatedAt?: string; pdfLang?: 'en' | 'hi' | 'te'; pdfSha256?: string; contentSha256?: string;
  execution?: AmdExecution;
  custodian?: AmdCustodian;
  copies: AmdCopy[];
  /** Optional ABHA link (L4: "may join digital health records"), with its own consent. */
  abha?: { address: string; consent: boolean; linkedOn: string };
  supersededAt?: string;
  revocation?: AmdRevocation;
};

/** Shape the ED Quick View reads via getCurrentAmdStatus(patientId). */
export type AmdStatusSummary = { status: AmdStatus; executedOn: string | null; version: number; attestor: string | null };
// ═══════════════════════════ end AMD section ═══════════════════════════════
