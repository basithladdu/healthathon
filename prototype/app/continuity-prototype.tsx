'use client';

import type { FormEvent } from 'react';
import Image from 'next/image';
import { usePathname, useRouter } from 'next/navigation';
import { CARE_ROUTES, careViewFromPath, canOpenCareView, type CareView as View } from './care-routes';
import React, { useMemo, useState, useEffect, useRef } from 'react';
import type { FamilyMember, PatientAccount } from './auth-screens';
import { SimpleCareShell } from './simple-care-shell';
import { DoctorConversation } from './doctor-conversation';
import type { DoctorConversationEntry, AssistedNote } from './doctor-conversation-state';
import { CareNoteReader } from './care-note-reader';
import { CareSignIn } from './care-sign-in';
import { DoctorNoteReview } from './doctor-note-review';
import { prepareVisitNote, approveVisitNote } from './visit-note-state';
import { AppointmentsPage, initialAppointments, DEMO_TODAY, type Appointment, type AppointmentPatient } from './appointments-page';
import { CareNearMe } from './care-near-me';
import { SupportResources } from './care-directory';
import { ConversationPreparation, type PreparationNotes } from './conversation-preparation';
import { FamilyCareWorkspace } from './family-care-workspace';
import { FamilyCareHome } from './family-care-home';
import { CareCalendar } from './care-calendar';
import { addCareEvent, updateCareEvent, careChecksAfterEventUpdate, setCareCheck, reportFileError, type CareEvent, type CareCheck, type CareReport } from './care-calendar-state';
import { useCareLocalState } from './care-local-store';
import { FamilyCareTools } from './family-care-tools';
import type { HomeHelpEntry } from './family-home-help-state';
import type { SupportPlace } from './family-support-state';
import { addOpenQuestion, type OpenQuestion } from './family-open-question-state';
import type { CareCopyEntry } from './family-copy-state';
import { FamilyComfortSpace } from './family-comfort-space';
import type { ComfortEntry } from './family-comfort-state';
import type { VoiceJournalEntry } from './family-voice-state';
import { createEmptyFamilyCostState, type FamilyCostState } from './family-cost-state';
import { DoctorCareHome } from './doctor-care-home';
import type { LabResultEntry } from './family-lab-state';
import type { SymptomEntry } from './family-symptom-state';
import type { CareStoryEntry } from './family-care-story-state';
import type { HandoverContact } from './family-handover-state';
import { INITIAL_FAMILY_TASKS, addFamilyTask, setFamilyTaskCompleted, type FamilyTask } from './family-care-state';
import { ectprQuickViews, EdQuickView, EmergencyCard } from './ectpr';
import {
  MyCarePlan,
  MyTimeline,
  MyDoctors,
  MyDetails,
  ConsentSignature,
  type PatientDetails,
  type DecisionMaker,
  type ConsentRecord,
  type CareTeamMember,
  type TimelineEntry,
  type PastVisit,
} from './patient-portal';
import { PatientHistory, type HistoryEvent } from './patient-history';
import { reviseSummary, releaseSummary, isSummaryReadyToRelease, invalidateSummaryReview, beginSummaryRevision, latestSummaryRelease, summaryReleaseByNumber, nextSummaryVersion, setSummaryFieldStatus, type DraftFieldKey, type DraftFieldStatus, type SummaryState, type SummaryRelease } from './summary-state';

import { FhirExportModal } from './fhir-export-modal';
import { TreatmentEscalationMatrix } from './tep-matrix';
import { VoiceDictationBar } from './voice-dictation';
import { MapLibreDispatchModal } from './maplibre-dispatch';
import { ContinuityOpsHub, type OpsHubTab } from './continuity-ops-hub';
import { SyringeDriverCalculator } from './syringe-driver-calculator';
import { EsasSymptomTracker } from './esas-symptom-tracker';
import { PalliativeDeprescribingMatrix } from './palliative-deprescribing';
import { PalliativePrognosisCalculator } from './palliative-prognosis-calculator';
import { ClinicalDiffModal } from './clinical-diff-modal';
import { OncologyEmergencyTriageModal } from './oncology-emergency-triage';
import { CaregiverBurdenMatrix } from './caregiver-burden-matrix';
import { PalliativeSedationCrisisModal } from './palliative-sedation-crisis';
import { LongitudinalTrajectoryTimeline } from './longitudinal-trajectory-timeline';
import { AnticipatoryDrugBoxModal } from './anticipatory-drug-box';
import { MalignantWoundCareModal } from './malignant-wound-care';
import { BoneMetastasesSinsModal } from './bone-metastases-sins';
import { CachexiaAscitesModal } from './cachexia-ascites-protocol';
import { NeuropathicPainBlocksModal } from './neuropathic-pain-blocks';
import { BreathlessnessCrisisModal } from './breathlessness-crisis-engine';
import { PalliativeDeliriumModal } from './palliative-delirium-engine';
import { PediatricPalliativeModal } from './pediatric-palliative-engine';
import { MalignantBowelObstructionModal } from './malignant-bowel-obstruction';
import { RenalHepaticPalliativeModal } from './renal-hepatic-palliative';
import { PalliativeRadiotherapyModal } from './palliative-radiotherapy-suite';
import { SpinalCordCompressionModal } from './spinal-cord-compression-suite';
import { MalignantHypercalcemiaModal } from './malignant-hypercalcemia-suite';
import { SvcoThoracicDecompressionModal } from './svco-thoracic-decompression-suite';
import { BleedCrisisModal } from './bleed-crisis';
import { ScenarioReviewModal } from './scenario-review';
import { IconZap, IconFileText, IconHeartPulse, IconHospital, IconSparkles } from './icons';

type FollowUpStatus =
  | 'Due today'
  | 'Overdue'
  | 'Awaiting review'
  | 'Awaiting outreach'
  | 'Escalated'
  | 'Completed';

type UserRole =
  | 'Dr Sujay · Clinical lead'
  | 'Dr Isha Menon · Emergency physician'
  | 'Anitha Rao · Care Coordinator';

type WorkItem = {
  patient: string;
  hospitalId: string;
  purpose: string;
  due: string;
  owner: string;
  status: FollowUpStatus;
  lastActivity: string;
  contactChannel?: string;
  coordinationNote?: string;
};

type AuditEvent = {
  id?: string;
  patientId?: string;
  releaseNumber?: number;
  timestamp?: string;
  time: string;
  actor: string;
  event: string;
  record: string;
  badge?: 'PUBLISH' | 'OUTREACH' | 'ACCESS' | 'ENROL' | 'VIEW' | 'SCHEDULE' | 'PATIENT';
  detail?: string;
  securityImpact?: string;
};

type OutreachRecord = {
  outcome: string;
  note: string;
  nextReviewDate: string;
  actor: string;
};

type ConversationDomainKey =
  | 'priorities'
  | 'careSetting'
  | 'icu'
  | 'ventilation'
  | 'dialysis'
  | 'treatmentTrial'
  | 'participants'
  | 'followUp';

type ConversationDisposition = 'discussed' | 'not-discussed' | 'defer';

type RecordState = SummaryState & {
  retrievalReason: string;
  careRelationship: string;
  retrievalAcknowledged: boolean;
  retrievalUnlocked: boolean;
  retrievalVersion: number | null;
  unlockedVersion: number | null;
  conversationCoverage: Record<ConversationDomainKey, ConversationDisposition>;
  latestOutreach: OutreachRecord | null;
};

const initialWorkItems: WorkItem[] = [
  {
    patient: 'Meera Raghavan',
    hospitalId: 'CANCER-20418',
    purpose: 'Continue family conversation',
    due: '28 Aug 2026',
    owner: 'Anitha Rao',
    status: 'Due today',
    lastActivity: 'Family requested more time',
  },
  {
    patient: 'Farah Nair',
    hospitalId: 'CANCER-20431',
    purpose: 'Reschedule planned discussion',
    due: '25 Aug 2026',
    owner: 'Anitha Rao',
    status: 'Overdue',
    lastActivity: 'Unable to reach patient',
  },
  {
    patient: 'Ramesh Kumar',
    hospitalId: 'CANCER-20392',
    purpose: 'Review conversation summary',
    due: '30 Aug 2026',
    owner: 'Dr Sujay',
    status: 'Awaiting review',
    lastActivity: 'Draft saved by clinician',
  },
  {
    patient: 'Leela Thomas',
    hospitalId: 'CANCER-20377',
    purpose: 'Confirm next review date',
    due: '21 Aug 2026',
    owner: 'Dr Sujay',
    status: 'Completed',
    lastActivity: 'Version 1 verified',
  },
];

const patientProfiles: Record<
  string,
  {
    age: number;
    team: string;
    participant: string;
    verified: string;
    phone: string;
    stage: string;
    dob: string;
    sex: string;
    programs: string[];
    diagnosis?: string;
  }
> = {
  'CANCER-20418': {
    age: 62,
    team: 'Oncology · Palliative care',
    participant: 'Kavya Raghavan · Daughter (Primary Proxy)',
    phone: 'Ask the care team',
    stage: 'Stage IV NSCLC · Continuity Cohort A',
    verified: '21 Aug 2026',
    dob: '14/03/1964',
    sex: 'Female',
    programs: ['Goals-of-care continuity', 'Palliative care'],
  },
  'CANCER-20431': {
    age: 55,
    team: 'Oncology · Thoracic Surgery',
    participant: 'Sameer Nair · Spouse',
    phone: 'Ask the care team',
    stage: 'Stage III Cholangiocarcinoma',
    verified: 'Not yet verified',
    dob: '09/11/1970',
    sex: 'Female',
    programs: ['Goals-of-care continuity', 'Thoracic surgery'],
  },
  'CANCER-20392': {
    age: 68,
    team: 'Pulmonary medicine · Oncology',
    participant: 'Nisha Kumar · Daughter',
    phone: 'Ask the care team',
    stage: 'Stage IV SCLC · Palliative Follow-up',
    verified: 'Awaiting review',
    dob: '22/01/1958',
    sex: 'Male',
    programs: ['Goals-of-care continuity', 'Pulmonary medicine'],
  },
  'CANCER-20377': {
    age: 59,
    team: 'Oncology · Palliative care',
    participant: 'Joel Thomas · Son',
    phone: 'Ask the care team',
    stage: 'Stage IV Ovarian Carcinoma',
    verified: '21 Aug 2026',
    dob: '30/06/1967',
    sex: 'Female',
    programs: ['Goals-of-care continuity', 'Palliative care'],
  },
  'CANCER-20452': {
    age: 47,
    team: 'Gynaecologic oncology',
    participant: 'Aarav Iyer · Brother',
    phone: 'Ask the care team',
    stage: 'Stage III Cervical Carcinoma',
    verified: 'Not yet verified',
    dob: '05/02/1979',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity', 'Gynaecologic oncology'],
  },
  'CANCER-20489': {
    age: 64,
    team: 'Gastrointestinal oncology',
    participant: 'Neha Mehta · Spouse',
    phone: 'Ask the care team',
    stage: 'Stage IV Pancreatic Adenocarcinoma',
    verified: 'Not yet verified',
    dob: '17/12/1961',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity', 'Gastrointestinal oncology'],
  },
};

const initialAuditEvents: AuditEvent[] = [
  {
    id: 'seed-audit-1',
    patientId: 'CANCER-20418',
    timestamp: '2026-08-28T09:42:00+05:30',
    time: '28 Aug · 09:42',
    actor: 'Dr Sujay',
    event: 'Viewed current verified version',
    record: 'Meera Raghavan · v1',
    badge: 'VIEW',
  },
  {
    id: 'seed-audit-2',
    patientId: 'CANCER-20418',
    timestamp: '2026-08-26T16:15:00+05:30',
    time: '26 Aug · 16:15',
    actor: 'Anitha Rao',
    event: 'Recorded outreach and rescheduled follow-up',
    record: 'Meera Raghavan',
    badge: 'OUTREACH',
  },
  {
    id: 'seed-audit-3',
    patientId: 'CANCER-20418',
    timestamp: '2026-08-21T12:08:00+05:30',
    time: '21 Aug · 12:08',
    actor: 'Dr Sujay',
    event: 'Verified and published version 1',
    record: 'Meera Raghavan · v1',
    badge: 'PUBLISH',
  },
  {
    id: 'seed-audit-4',
    patientId: 'CANCER-20418',
    timestamp: '2026-08-21T12:06:00+05:30',
    time: '21 Aug · 12:06',
    actor: 'Kavya Raghavan',
    event: 'Authorisation recorded by care team',
    record: 'Meera Raghavan · v1',
    badge: 'ACCESS',
  },
];

const sourceSnippets = [
  { key: 'participants', text: 'Meera met with me and her daughter Kavya today.' },
  { key: 'priorities', text: 'She said it is important that her family understands what matters to her before we continue the discussion. She wants time to speak with them at home.' },
  { key: 'topics', text: 'We discussed who she wants involved and what questions she would like us to answer next.' },
  { key: 'openQuestions', text: 'Specific treatment interventions were not discussed.' },
  { key: 'followUp', text: 'We agreed to continue the conversation on 28 August.' },
];

const initialDraftFields: Record<DraftFieldKey, string> = {
  priorities:
    'Meera wants her family to understand what matters to her before the conversation continues.',
  participants: 'Meera Raghavan; Kavya Raghavan (daughter); Dr Sujay.',
  topics:
    'People to involve, questions for the next conversation, and time for family discussion.',
  openQuestions: 'Not stated in this conversation.',
  followUp: 'Continue the conversation on 28 Aug 2026.',
};

const initialConversationCoverage: Record<ConversationDomainKey, ConversationDisposition> = {
  priorities: 'discussed',
  careSetting: 'defer',
  icu: 'not-discussed',
  ventilation: 'not-discussed',
  dialysis: 'not-discussed',
  treatmentTrial: 'not-discussed',
  participants: 'discussed',
  followUp: 'discussed',
};

const conversationDomains: Array<{
  key: ConversationDomainKey;
  label: string;
  prompt: string;
}> = [
  {
    key: 'priorities',
    label: 'Understanding and priorities',
    prompt: 'What matters most to the patient if the illness worsens?',
  },
  {
    key: 'careSetting',
    label: 'Preferred care setting',
    prompt: 'Was home care versus hospital care discussed?',
  },
  {
    key: 'icu',
    label: 'Ward and ICU care',
    prompt: 'Were ward admission, ICU care, or limits discussed?',
  },
  {
    key: 'ventilation',
    label: 'Ventilation',
    prompt: 'Was invasive or non-invasive ventilation discussed explicitly?',
  },
  {
    key: 'dialysis',
    label: 'Dialysis',
    prompt: 'Was dialysis discussed explicitly?',
  },
  {
    key: 'treatmentTrial',
    label: 'Time-limited treatment trial',
    prompt: 'Was a time-limited trial and review point discussed?',
  },
  {
    key: 'participants',
    label: 'Participants and surrogate',
    prompt: 'Who was present, and what relationship or authority was recorded?',
  },
  {
    key: 'followUp',
    label: 'Follow-up and responsible clinician',
    prompt: 'What happens next, when, and who owns the next action?',
  },
];

function createInitialRecordState(hospitalId = 'CANCER-20418'): RecordState {
  const isPrimaryScenario = hospitalId === 'CANCER-20418';

  return {
    patientId: hospitalId,
    releases: (patientProfiles[hospitalId]?.verified ?? '').includes('2026') ? [{
      number: 1,
      patientId: hospitalId,
      fields: isPrimaryScenario ? Object.freeze({ ...v1Fields }) : null,
      statuses: null,
      source: null,
      excerpts: null,
      coverage: null,
      authorisation: 'Verbal acknowledgement recorded by care team',
      physician: 'Dr Sujay',
      releasedAt: '2026-08-21T12:00:00+05:30',
    }] : [],
    draftSource: isPrimaryScenario ? sourceSnippets.map(({ text }) => text).join(' ') : '',
    draftExcerpts: Object.fromEntries(sourceSnippets.map(({ key, text }) => [key, isPrimaryScenario ? text : ''])) as Record<DraftFieldKey, string>,
    draftIsNewConversation: !isPrimaryScenario,
    draftPrepared: false,
    draftFields: isPrimaryScenario ? { ...initialDraftFields } : { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' },
    draftStatuses: isPrimaryScenario
      ? { priorities: 'ready', participants: 'ready', topics: 'ready', openQuestions: 'not-stated', followUp: 'ready' }
      : { priorities: 'clarify', participants: 'clarify', topics: 'clarify', openQuestions: 'clarify', followUp: 'clarify' },
    verificationChecks: {
      source: false,
      ambiguity: false,
      inference: false,
      reviewDate: false,
    },
    authorisation: 'Pending patient review',
    attested: false,
    versionPublished: false,
    publishedFields: null,
    retrievalReason: 'Planned consultation / Outpatient review',
    careRelationship: 'Current primary oncology treating team',
    retrievalAcknowledged: false,
    retrievalUnlocked: false,
    retrievalVersion: null,
    unlockedVersion: null,
    conversationCoverage: isPrimaryScenario
      ? { ...initialConversationCoverage }
      : Object.fromEntries(
          Object.keys(initialConversationCoverage).map((key) => [key, 'defer']),
        ) as Record<ConversationDomainKey, ConversationDisposition>,
    latestOutreach: null,
  };
}

const enrolmentCandidates = {
  'CANCER-20452': { patient: 'Sana Iyer', due: '2 Sep 2026' },
  'CANCER-20489': { patient: 'Vikram Mehta', due: '4 Sep 2026' },
} as const;

const v1Fields: Record<DraftFieldKey, string> = {
  priorities: 'Patient initiated discussion regarding family understanding of illness.',
  participants: 'Meera Raghavan; Dr Sujay.',
  topics: 'Initial goals-of-care inquiry and overview of support team.',
  openQuestions: 'Specific intervention scope and surrogate preferences pending family consult.',
  followUp: 'Follow-up planned for late August.',
};

const initialRecordStates: Record<string, RecordState> = Object.fromEntries(
  Object.keys(patientProfiles).map((hospitalId) => [hospitalId, createInitialRecordState(hospitalId)]),
);

function releaseDate(release: SummaryRelease | null | undefined): string {
  if (!release) return 'Not released';
  return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(release.releasedAt));
}

const fieldConfig: Array<{
  key: DraftFieldKey;
  label: string;
  provenance: string;
}> = [
  {
    key: 'priorities',
    label: "Patient's stated priorities",
    provenance: '“important that her family understands what matters to her”',
  },
  {
    key: 'participants',
    label: 'People present',
    provenance: '“Meera met with me and her daughter Kavya”',
  },
  {
    key: 'topics',
    label: 'Topics discussed',
    provenance: '“who she wants involved and what questions she would like us to answer”',
  },
  {
    key: 'openQuestions',
    label: 'Information not stated or unresolved',
    provenance: '“Specific treatment interventions were not discussed”',
  },
  {
    key: 'followUp',
    label: 'Follow-up agreed',
    provenance: '“continue the conversation on 28 August”',
  },
];

// Built by hand rather than with Intl: en-GB spells September "Sept", which
// broke the "DD Mon YYYY" format every other date in the app uses and made
// those dates unsortable in the Today's handoffs list.
const DISPLAY_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function formatDateForDisplay(dateValue: string) {
  if (!dateValue) return '';
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue);
  if (!match) return dateValue;
  const month = DISPLAY_MONTHS[Number(match[2]) - 1];
  if (!month) return dateValue;
  return `${Number(match[3])} ${month} ${match[1]}`;
}

const filters = [
  'All',
  'Due today',
  'Overdue',
  'Awaiting outreach',
  'Awaiting review',
  'Escalated',
  'Completed',
] as const;

/* ==========================================================================
   SVG Icons (Clean, Clinical & Lightweight)
   ========================================================================== */

function IconClipboardList({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
    </svg>
  );
}

function IconUsers({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}

function IconFileEdit({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
    </svg>
  );
}

function IconShieldCheck({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}

function IconHistory({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function IconSearch({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
  );
}

function IconPlus({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
    </svg>
  );
}

function IconCheck({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
    </svg>
  );
}

function IconX({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  );
}

function IconChevronRight({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  );
}

function IconCopy({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
    </svg>
  );
}

function IconInfo({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function IconLock({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
    </svg>
  );
}

function IconColumns({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 4H5a1 1 0 00-1 1v14a1 1 0 001 1h4a1 1 0 001-1V5a1 1 0 00-1-1zm10 0h-4a1 1 0 00-1 1v14a1 1 0 001 1h4a1 1 0 001-1V5a1 1 0 00-1-1z" />
    </svg>
  );
}

function IconChevronLeft({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 18l-6-6 6-6" />
    </svg>
  );
}


function IconExpand({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 4H5a1 1 0 00-1 1v4m0 6v4a1 1 0 001 1h4m6 0h4a1 1 0 001-1v-4m0-6V5a1 1 0 00-1-1h-4" />
    </svg>
  );
}

function IconKebab({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  );
}

function IconCalendar({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <rect x="3" y="5" width="18" height="16" rx="2" strokeLinecap="round" strokeLinejoin="round" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 3v4M16 3v4M3 10h18" />
    </svg>
  );
}

function IconFilter({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 5h16l-6 7v6l-4 2v-8L4 5z" />
    </svg>
  );
}

function IconMapPin({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a2 2 0 01-2.828 0l-4.243-4.243a8 8 0 1111.314 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  );
}

function IconIdCard({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <rect x="3" y="5" width="18" height="14" rx="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="8" cy="11" r="1.6" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 15.5c.5-1.3 1.6-2 2-2s1.5.7 2 2M13 10h5M13 13h5" />
    </svg>
  );
}

function ContinuityMark({ className = '' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 40 40" fill="none" aria-hidden="true">
      <path d="M7.5 14.4c0-4.1 3.3-7.4 7.4-7.4 4.7 0 6.7 3.6 10 8 2.1 2.8 4.1 4.6 7.1 4.6 3.1 0 5.4-2.3 5.4-5.4" stroke="currentColor" strokeWidth="3.25" strokeLinecap="round" />
      <path d="M32.5 25.6c0 4.1-3.3 7.4-7.4 7.4-4.7 0-6.7-3.6-10-8-2.1-2.8-4.1-4.6-7.1-4.6-3.1 0-5.4 2.3-5.4 5.4" stroke="currentColor" strokeWidth="3.25" strokeLinecap="round" />
      <path d="m16.3 20.6 2.5 2.5 5.2-6" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const navItems: Array<{ view: View; label: string; icon: React.FC<{ className?: string }> }> = [
  { view: 'caregiver', label: 'Care journey', icon: IconUsers },
  { view: 'home', label: 'Home', icon: IconShieldCheck },
  { view: 'guide', label: 'Conversations', icon: IconFileEdit },
  { view: 'worklist', label: 'Follow-ups', icon: IconClipboardList },
  { view: 'appointments', label: 'Appointments', icon: IconCalendar },
  { view: 'patients', label: 'Patients', icon: IconUsers },
  { view: 'records', label: 'Notes', icon: IconShieldCheck },
  { view: 'audit', label: 'History', icon: IconHistory },
];

const patientNavItems: Array<{ view: View; label: string; icon: React.FC<{ className?: string }> }> = [
  { view: 'my-plan', label: 'My care plan', icon: IconShieldCheck },
  { view: 'daily-care', label: 'Daily care', icon: IconCalendar },
  { view: 'prepare-conversation', label: 'Prepare for a conversation', icon: IconFileEdit },
  { view: 'my-timeline', label: 'My care journey', icon: IconHistory },
  { view: 'care-near-me', label: 'Care near me', icon: IconMapPin },
  { view: 'my-doctors', label: 'My doctors', icon: IconUsers },
  { view: 'emergency-card', label: 'Emergency card', icon: IconIdCard },
  { view: 'my-details', label: 'My details', icon: IconFileEdit },
  { view: 'consent', label: 'Consent', icon: IconCheck },
];

const familyNavItems: Array<{ view: View; label: string; icon: React.FC<{ className?: string }> }> = [
  { view: 'caregiver', label: 'Care journey', icon: IconUsers },
  { view: 'daily-care', label: 'Daily care', icon: IconCalendar },
  { view: 'prepare-conversation', label: 'Prepare for a conversation', icon: IconFileEdit },
  { view: 'care-near-me', label: 'Care near me', icon: IconMapPin },
  { view: 'my-doctors', label: 'Care team', icon: IconUsers },
  { view: 'emergency-card', label: 'Emergency card', icon: IconIdCard },
];

type CoverageZone = {
  id: string;
  name: string;
  area: string;
  points: number;
  note: string;
  path: string;
};

// Illustrative coverage sketch for the caregiver demonstration. The silhouette is a
// stylised abstraction, not survey geography, and the counts are seeded placeholders.
const coverageZones: CoverageZone[] = [
  {
    id: 'south',
    name: 'South Mumbai',
    area: 'Colaba to Byculla',
    points: 4,
    note: 'Tertiary oncology centres sit here, so most first goals-of-care conversations start in this belt.',
    path: 'M150 442 C142 424 136 404 137 382 C138 362 144 348 152 340 L176 344 C180 366 178 392 172 414 C168 428 160 438 150 442 Z',
  },
  {
    id: 'west',
    name: 'Western suburbs',
    area: 'Bandra to Borivali',
    points: 7,
    note: 'Longest travel times to the treating centre, so continuity follow-up is usually coordinated by phone.',
    path: 'M152 340 C140 330 130 306 128 278 C125 244 130 206 140 176 C146 158 154 146 164 142 L184 150 C186 190 184 250 182 292 C181 314 180 330 176 344 Z',
  },
  {
    id: 'east',
    name: 'Eastern suburbs',
    area: 'Kurla to Mulund',
    points: 5,
    note: 'Mixed public and private receiving hospitals, so an unfamiliar physician is more likely to open the record here.',
    path: 'M176 344 C180 330 181 314 182 292 C184 250 186 190 184 150 L206 158 C216 186 220 226 218 264 C216 300 208 328 196 344 Z',
  },
  {
    id: 'harbour',
    name: 'Harbour and Navi Mumbai',
    area: 'Chembur to Vashi',
    points: 3,
    note: 'Care often continues closer to home, which is where a portable verified summary matters most.',
    path: 'M214 352 C228 340 246 336 262 344 C276 352 282 370 278 390 C272 414 252 428 232 424 C214 420 206 402 208 382 C209 368 211 358 214 352 Z',
  },
];

function CoverageMap({ activeZone }: { activeZone: string }) {
  return (
    <svg className="coverage-map" viewBox="108 126 190 332" role="img" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="coverage-water" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#eef5f6" />
          <stop offset="100%" stopColor="#e2edef" />
        </linearGradient>
      </defs>
      <rect x="108" y="126" width="190" height="332" fill="url(#coverage-water)" />
      <g className="coverage-map-grid">
        {[160, 210, 260, 310, 360, 410].map((y) => (
          <line key={y} x1="108" y1={y} x2="298" y2={y} />
        ))}
      </g>
      {coverageZones.map((zone) => (
        <path
          key={zone.id}
          d={zone.path}
          className={activeZone === zone.id ? 'coverage-zone active' : 'coverage-zone'}
        />
      ))}
    </svg>
  );
}

function statusClass(status: string) {
  return `status-pill status-${status.toLowerCase().replaceAll(' ', '-')}`;
}

function StatusPill({ status }: { status: string }) {
  return (
    <span className={statusClass(status)} role="status" aria-label={status}>
      <span className="status-dot" aria-hidden="true" />
      <span>{status}</span>
    </span>
  );
}

export function ContinuityPrototype() {
  const pathname = usePathname();
  const router = useRouter();
  const simpleMode = !pathname.startsWith('/workspace/');
  const [accessReady, setAccessReady] = useState(false);
  const [hindi, setHindi] = useState(false);
  const [currentRole, setCurrentRole] = useState<UserRole>('Dr Sujay · Clinical lead');
  const [authScreen, setAuthScreen] = useState<'login' | null>('login');
  const [sessionType, setSessionType] = useState<'care-team' | 'family' | 'patient'>('family');
  const [familyMember, setFamilyMember] = useState<FamilyMember | null>({ name: 'Kavya Raghavan', relationship: 'Daughter', patientId: 'CANCER-20418', verifiedSummaryOnly: true });
  const [patientAccount, setPatientAccount] = useState<PatientAccount | null>(null);
  const requestedView = careViewFromPath(pathname);
  const view: View = requestedView && canOpenCareView(requestedView, sessionType, currentRole)
    ? requestedView : sessionType === 'care-team' ? 'home' : 'daily-care';
  function setView(nextView: View) { router.push(CARE_ROUTES[nextView]); }
  const [preparationNotes, setPreparationNotes, preparationSave] = useCareLocalState<Record<string, PreparationNotes>>('preparation', {});
  const [familyTasks, setFamilyTasks, taskSave] = useCareLocalState<FamilyTask[]>('tasks', INITIAL_FAMILY_TASKS);
  const [careEvents, setCareEvents, eventSave] = useCareLocalState<CareEvent[]>('calendar', []);
  const [careChecks, setCareChecks, checkSave] = useCareLocalState<CareCheck[]>('calendar-checks', []);
  const [careReports, setCareReports, reportSave] = useCareLocalState<CareReport[]>('reports', []);
  const [symptoms, setSymptoms, symptomSave] = useCareLocalState<SymptomEntry[]>('symptoms', []);
  const [careStory, setCareStory, storySave] = useCareLocalState<CareStoryEntry[]>('care-story', []);
  const [handoverContacts, setHandoverContacts, contactSave] = useCareLocalState<HandoverContact[]>('handover-contacts', []);
  const [homeHelp, setHomeHelp, helpSave] = useCareLocalState<HomeHelpEntry[]>('home-help', []);
  const [supportPlaces, setSupportPlaces, placesSave] = useCareLocalState<SupportPlace[]>('support-places', []);
  const [openQuestions, setOpenQuestions, questionSave] = useCareLocalState<OpenQuestion[]>('open-questions', []);
  const [careCopies, setCareCopies, copySave] = useCareLocalState<CareCopyEntry[]>('care-copies', []);
  const [comfortEntries, setComfortEntries, comfortSave] = useCareLocalState<ComfortEntry[]>('comfort', []);
  const [voiceJournal, setVoiceJournal, voiceSave] = useCareLocalState<VoiceJournalEntry[]>('voice-journal', []);
  const [familyCosts, setFamilyCosts, costSave] = useCareLocalState<FamilyCostState>('family-costs', createEmptyFamilyCostState());
  const [labResults, setLabResults, labSave] = useCareLocalState<LabResultEntry[]>('lab-results', []);
  const [doctorConversations, setDoctorConversations, conversationSave] = useCareLocalState<DoctorConversationEntry[]>('doctor-conversations', []);
  const [newDoctorConversation, setNewDoctorConversation] = useState(true);
  const isFamilySession = sessionType === 'family';
  const isPatientSession = sessionType === 'patient';
  const [workItems, setWorkItems] = useState(initialWorkItems);
  const [appointments, setAppointments, appointmentSave] = useCareLocalState<Appointment[]>('appointments', initialAppointments);
  const [selectedId, setSelectedId] = useState('CANCER-20418');
  const [recordStates, setRecordStates, noteSave] =
    useCareLocalState<Record<string, RecordState>>('care-notes', initialRecordStates);
  const [activeFilter, setActiveFilter] =
    useState<(typeof filters)[number]>('All');
  const [query, setQuery] = useState('');
  const [enrolOpen, setEnrolOpen] = useState(false);
  const [diffOpen, setDiffOpen] = useState(false);
  const [comparisonVersion, setComparisonVersion] = useState<number | null>(null);
  const [highlightedField, setHighlightedField] = useState<string | null>(null);
  const [auditFilterActor, setAuditFilterActor] = useState<string>('All');
  const [enrolConfirmed, setEnrolConfirmed] = useState(false);
  const [enrolPatientId, setEnrolPatientId] =
    useState<keyof typeof enrolmentCandidates>('CANCER-20452');
  const [enrolDate, setEnrolDate] = useState('2026-09-02');
  const [enrolOwner, setEnrolOwner] = useState('Anitha Rao');
  const [enrolReason, setEnrolReason] = useState(
    'Conversation started and needs continuation',
  );
  const [outreachOutcome, setOutreachOutcome] = useState('');
  const [outreachNote, setOutreachNote] = useState('');
  const [outreachDate, setOutreachDate] = useState('');
  const [outreachError, setOutreachError] = useState('');
  const [auditEvents, setAuditEvents] = useState(initialAuditEvents);
  const [toast, setToast] = useState<{ message: string } | null>(null);
  const [coverageZoneId, setCoverageZoneId] = useState('south');
  const [profileTab, setProfileTab] =
    useState<'overview' | 'care' | 'documents' | 'scheduling' | 'encounters'>('overview');
  const [patientsPanelOpen, setPatientsPanelOpen] = useState(true);
  const [profileSearch, setProfileSearch] = useState('');
  const [profileSort, setProfileSort] = useState<'recent' | 'az'>('recent');
  const [profileStatusFilter, setProfileStatusFilter] = useState<'All' | FollowUpStatus>('All');
  const [profileFilterOpen, setProfileFilterOpen] = useState(false);
  const [openMeasureMenu, setOpenMeasureMenu] = useState<string | null>(null);
  const [homeShowCompleted, setHomeShowCompleted] = useState(false);
  const [patientsScope, setPatientsScope] = useState<'all' | 'mine'>('all');
  const [patientDetailsById, setPatientDetailsById] = useState<Record<string, PatientDetails>>({});
  const [consentById, setConsentById] = useState<Record<string, ConsentRecord>>({});
  const [cardIssuedById, setCardIssuedById] = useState<Record<string, string>>({});
  const [reviewRequestedById, setReviewRequestedById] = useState<Record<string, string>>({});
  const [fhirExportOpen, setFhirExportOpen] = useState(false);
  const [tepModalOpen, setTepModalOpen] = useState(false);
  const [mapOpen, setMapOpen] = useState(false);
  const [opsHubOpen, setOpsHubOpen] = useState(false);
  const [opsHubTab, setOpsHubTab] = useState<OpsHubTab>('map');
  const [clinicCheckInNote, setClinicCheckInNote] = useState<string | null>(null);
  const [syringeDriverOpen, setSyringeDriverOpen] = useState(false);
  const [esasOpen, setEsasOpen] = useState(false);
  const [deprescribingOpen, setDeprescribingOpen] = useState(false);
  const [prognosisOpen, setPrognosisOpen] = useState(false);
  const [emergencyTriageOpen, setEmergencyTriageOpen] = useState(false);
  const [caregiverBurdenOpen, setCaregiverBurdenOpen] = useState(false);
  const [sedationCrisisOpen, setSedationCrisisOpen] = useState(false);
  const [trajectoryTimelineOpen, setTrajectoryTimelineOpen] = useState(false);
  const [drugBoxOpen, setDrugBoxOpen] = useState(false);
  const [woundCareOpen, setWoundCareOpen] = useState(false);
  const [boneSinsOpen, setBoneSinsOpen] = useState(false);
  const [cachexiaAscitesOpen, setCachexiaAscitesOpen] = useState(false);
  const [neuropathicBlocksOpen, setNeuropathicBlocksOpen] = useState(false);
  const [breathlessnessCrisisOpen, setBreathlessnessCrisisOpen] = useState(false);
  const [deliriumOpen, setDeliriumOpen] = useState(false);
  const [pediatricPalliativeOpen, setPediatricPalliativeOpen] = useState(false);
  const [bowelObstructionOpen, setBowelObstructionOpen] = useState(false);
  const [renalHepaticOpen, setRenalHepaticOpen] = useState(false);
  const [radiotherapyOpen, setRadiotherapyOpen] = useState(false);
  const [cordCompressionOpen, setCordCompressionOpen] = useState(false);
  const [hypercalcemiaOpen, setHypercalcemiaOpen] = useState(false);
  const [svcoOpen, setSvcoOpen] = useState(false);
  const [bleedCrisisOpen, setBleedCrisisOpen] = useState(false);
  const [scenarioReviewOpen, setScenarioReviewOpen] = useState(false);
  const profileTimelineRef = useRef<HTMLOListElement>(null);
  const enrolDialogRef = useRef<HTMLElement>(null);
  const diffDialogRef = useRef<HTMLElement>(null);
  const retrievedRecordHeadingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem('saanthvana-care-viewer') ?? 'null');
      if (saved && initialWorkItems.some((item) => item.hospitalId === saved.selectedId)
        && ['family', 'patient', 'care-team'].includes(saved.sessionType)) {
        setSelectedId(saved.selectedId);
        setSessionType(saved.sessionType);
        if (saved.sessionType === 'family') setFamilyMember({ patientId: saved.selectedId, name: saved.name || 'Family member', relationship: 'Family member', verifiedSummaryOnly: true });
        else if (saved.sessionType === 'patient') setPatientAccount({ hospitalId: saved.selectedId, name: saved.name || 'Patient' });
        if (['Dr Sujay · Clinical lead', 'Dr Isha Menon · Emergency physician', 'Anitha Rao · Care Coordinator'].includes(saved.currentRole)) setCurrentRole(saved.currentRole);
        setHindi(saved.hindi === true);
        setAuthScreen(null);
      }
    } catch { /* Opening a care space still works when browser storage is unavailable. */ }
    setAccessReady(true);
  }, []);

  useEffect(() => {
    if (!accessReady) return;
    try {
      if (authScreen !== null) sessionStorage.removeItem('saanthvana-care-viewer');
      else sessionStorage.setItem('saanthvana-care-viewer', JSON.stringify({
        sessionType, selectedId, currentRole, hindi,
        name: sessionType === 'family' ? familyMember?.name : patientAccount?.name,
      }));
    } catch { /* The current care space remains available without remembering this tab. */ }
  }, [accessReady, authScreen, sessionType, selectedId, currentRole, hindi, familyMember, patientAccount]);

  useEffect(() => {
    if (!accessReady || authScreen !== null) return;
    if (pathname === '/' || pathname === '/sign-in' || (requestedView && requestedView !== view)) router.replace(CARE_ROUTES[view]);
  }, [accessReady, authScreen, pathname, requestedView, view, router]);

  const recordState = recordStates[selectedId] ?? createInitialRecordState(selectedId);
  const currentRelease = latestSummaryRelease(recordState);
  const requestedRelease = recordState.retrievalVersion === null
    ? currentRelease : summaryReleaseByNumber(recordState, recordState.retrievalVersion);
  const draftVersion = recordState.versionPublished ? currentRelease?.number ?? 1 : nextSummaryVersion(recordState);
  const comparisonNewer = summaryReleaseByNumber(recordState, comparisonVersion ?? currentRelease?.number ?? 0);
  const comparisonOlder = summaryReleaseByNumber(recordState, (comparisonNewer?.number ?? 1) - 1);
  const {
    draftPrepared,
    draftFields,
    draftStatuses,
    verificationChecks,
    authorisation,
    attested,
    versionPublished,
    retrievalReason,
    careRelationship,
    retrievalAcknowledged,
    retrievalUnlocked,
    conversationCoverage,
    latestOutreach,
  } = recordState;

  function updateRecordState(
    hospitalId: string,
    update: (current: RecordState) => RecordState,
  ) {
    setRecordStates((states) => {
      const current = states[hospitalId] ?? createInitialRecordState(hospitalId);
      return { ...states, [hospitalId]: update(current) };
    });
  }

  function updateSelectedRecord(update: (current: RecordState) => RecordState) {
    updateRecordState(selectedId, update);
  }

  const isTreatingPhysician = currentRole.startsWith('Dr Sujay');
  const isCareCoordinator = currentRole.startsWith('Anitha Rao');

  function closeClinicalAccess() {
    setRecordStates((states) => Object.fromEntries(Object.entries(states).map(([id, state]) => [id, {
      ...state, retrievalAcknowledged: false, retrievalUnlocked: false, retrievalVersion: null, unlockedVersion: null,
    }])));
    setDiffOpen(false);
    setComparisonVersion(null);
  }

  function enterCareTeam(role: UserRole, nextView: View = 'home') {
    closeClinicalAccess();
    setSessionType('care-team');
    setFamilyMember(null);
    setPatientAccount(null);
    setCurrentRole(role);
    setView(nextView);
    setAuthScreen(null);
  }

  function enterFamily(member: FamilyMember, nextView: View = simpleMode ? 'daily-care' : 'caregiver') {
    if (!workItems.some((item) => item.hospitalId === member.patientId)) return;
    closeClinicalAccess();
    setSessionType('family');
    setFamilyMember(member);
    setPatientAccount(null);
    selectPatient(member.patientId);
    setView(nextView);
    setAuthScreen(null);
  }

  function enterPatient(account: PatientAccount, nextView: View = simpleMode ? 'daily-care' : 'my-plan') {
    closeClinicalAccess();
    setSessionType('patient');
    setFamilyMember(null);
    setPatientAccount(account);
    selectPatient(account.hospitalId);
    setView(nextView);
    setAuthScreen(null);
  }

  function signOut() {
    closeClinicalAccess();
    setFamilyMember(null);
    setPatientAccount(null);
    setAuthScreen('login');
    router.push('/sign-in');
  }

  function returnToCare() {
    enterFamily({ name: 'Kavya Raghavan', relationship: 'Daughter', patientId: 'CANCER-20418', verifiedSummaryOnly: true }, 'daily-care');
  }

  function switchRole(role: UserRole) {
    setCurrentRole(role);
    updateSelectedRecord((current) => ({
      ...current,
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
    }));
    if (
      (!role.startsWith('Dr Sujay') && (view === 'guide' || view === 'draft' || view === 'verify')) ||
      (view === 'outreach' && !role.startsWith('Anitha Rao'))
    ) {
      setView('patient');
    }
    notify(`Active role switched to ${role}`);
  }

  function openDemoRoleWorkflow(role: UserRole) {
    if (isFamilySession || isPatientSession) return;
    switchRole(role);

    if (role === 'Dr Sujay · Clinical lead') {
      selectPatient('CANCER-20418');
      navigate('guide');
      return;
    }

    if (role === 'Anitha Rao · Care Coordinator') {
      navigate('worklist');
      return;
    }

    selectPatient('CANCER-20418');
    updateRecordState('CANCER-20418', (current) => ({
      ...current,
      retrievalReason: 'Emergency hand-off retrieval (Break-glass)',
      careRelationship: 'Emergency receiving physician',
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
    }));
    navigate('retrieve');
  }

  function setDraftFields(value: React.SetStateAction<Record<DraftFieldKey, string>>) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => reviseSummary(current, {
      draftFields:
        typeof value === 'function' ? value(current.draftFields) : value,
    }));
  }

  function setVerificationChecks(
    value: React.SetStateAction<RecordState['verificationChecks']>,
  ) {
    if (!isTreatingPhysician || versionPublished) return;
    updateSelectedRecord((current) => ({
      ...current,
      verificationChecks:
        typeof value === 'function' ? value(current.verificationChecks) : value,
    }));
  }

  function setAuthorisation(value: string) {
    if (!isTreatingPhysician || versionPublished) return;
    updateSelectedRecord((current) => current.authorisation === value ? current : ({ ...current, authorisation: value, attested: false }));
  }

  function setAttested(value: boolean) {
    if (!isTreatingPhysician || versionPublished) return;
    updateSelectedRecord((current) => ({ ...current, attested: value }));
  }

  function setRetrievalReason(value: string) {
    updateSelectedRecord((current) => ({
      ...current,
      retrievalReason: value,
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
    }));
  }

  function setCareRelationship(value: string) {
    updateSelectedRecord((current) => ({
      ...current,
      careRelationship: value,
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
    }));
  }

  function setRetrievalAcknowledged(value: boolean) {
    updateSelectedRecord((current) => ({ ...current, retrievalAcknowledged: value }));
  }

  function setConversationDisposition(
    key: ConversationDomainKey,
    value: ConversationDisposition,
  ) {
    if (!isTreatingPhysician || versionPublished) return;
    updateSelectedRecord((current) => current.versionPublished || current.conversationCoverage[key] === value ? current : ({
      ...invalidateSummaryReview(current),
      conversationCoverage: { ...current.conversationCoverage, [key]: value },
    }));
  }

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, [authScreen]);

  useEffect(() => {
    const activeDialog = enrolOpen
      ? enrolDialogRef.current
      : diffOpen
        ? diffDialogRef.current
        : null;
    const previouslyFocused = document.activeElement as HTMLElement | null;

    if (activeDialog) {
      const firstControl = activeDialog.querySelector<HTMLElement>(
        'button, select, input, textarea, [tabindex]:not([tabindex="-1"])',
      );
      firstControl?.focus();
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        if (enrolOpen) setEnrolOpen(false);
        if (diffOpen) setDiffOpen(false);
      }

      if (e.key === 'Tab' && activeDialog) {
        const controls = Array.from(
          activeDialog.querySelectorAll<HTMLElement>(
            'button:not([disabled]), select:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((control) => control.offsetParent !== null);

        if (controls.length === 0) return;
        const first = controls[0];
        const last = controls[controls.length - 1];

        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      previouslyFocused?.focus();
    };
  }, [enrolOpen, diffOpen]);

  useEffect(() => {
    if (retrievalUnlocked) {
      retrievedRecordHeadingRef.current?.focus();
    }
  }, [retrievalUnlocked]);

  useEffect(() => {
    if (!openMeasureMenu) return;
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpenMeasureMenu(null);
    }
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [openMeasureMenu]);

  const selectedItem =
    workItems.find((item) => item.hospitalId === selectedId) ?? workItems[0];
  const selectedProfile = patientProfiles[selectedId] ?? {
    age: 61,
    team: 'Oncology',
    participant: 'Family participant to be confirmed',
    phone: 'Ask the care team',
    stage: 'Oncology Cohort',
    verified: 'Not yet verified',
    dob: 'Not recorded',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity'],
  };
  // Profiles carry `stage` (e.g. "Stage IV NSCLC · Continuity Cohort A"); the
  // leading clause is the working diagnosis used on exports and handoff cards.
  const selectedDiagnosis: string =
    selectedProfile.diagnosis ?? selectedProfile.stage.split(' · ')[0];

  const portalPatientId = isPatientSession
    ? (patientAccount?.hospitalId ?? 'CANCER-20418')
    : isFamilySession ? (familyMember?.patientId ?? 'CANCER-20418') : selectedId;
  const portalAuthor = isFamilySession ? familyMember?.name ?? 'Family member'
    : isPatientSession ? patientAccount?.name ?? 'Patient' : currentRole.split(' · ')[0];
  const portalPatientName =
    workItems.find((item) => item.hospitalId === portalPatientId)?.patient ??
    (isPatientSession ? patientAccount?.name : familyMember?.name) ??
    'Patient';

  function saveAppointmentInstructions(patientId: string, appointmentId: string, value: Appointment['preparationInstructions']) {
    if (value && (!value.text.trim() || value.text.length > 1200 || !value.givenBy.trim() || value.givenBy.length > 120 || !value.recordedBy.trim())) return;
    setAppointments((current) => current.map((visit) => visit.hospitalId === patientId && visit.id === appointmentId ? { ...visit, preparationInstructions: value } : visit));
  }

  function recordedStoryFor(id: string): CareStoryEntry[] {
    const common = { patientId: id, changeReason: '' };
    return [
      ...appointments.filter((visit) => visit.hospitalId === id && visit.status === 'Attended').map((visit): CareStoryEntry => ({ ...common, id: `record-visit-${visit.id}`, date: visit.date, kind: 'Visit', title: visit.type, details: visit.outcome ?? visit.note ?? '', source: 'Appointment record', sourceAuthor: visit.clinician, createdBy: visit.clinician, updatedBy: visit.clinician })),
      ...(recordStates[id]?.releases ?? []).filter((release) => release.patientId === id).map((release): CareStoryEntry => ({ ...common, id: `record-note-${id}-${release.number}`, date: release.releasedAt.slice(0, 10), kind: 'Milestone', title: `Care note approved · Version ${release.number}`, details: release.fields?.topics ?? release.source ?? '', source: `Approved care note V${release.number}`, sourceAuthor: release.physician, createdBy: release.physician, updatedBy: release.physician })),
      ...careReports.filter((report) => report.patientId === id).map((report): CareStoryEntry => ({ ...common, id: `record-report-${report.id}`, date: report.date, kind: 'Report', title: report.file.name, details: '', source: 'Uploaded report', sourceAuthor: report.addedBy, createdBy: report.addedBy, updatedBy: report.addedBy })),
    ];
  }

  function knownContactsFor(id: string): HandoverContact[] {
    const details = detailsFor(id);
    return [
      { id: `contact-primary-${id}`, patientId: id, name: details.primaryDecisionMaker.name, relationship: details.primaryDecisionMaker.relationship || 'Family contact', phone: details.primaryDecisionMaker.phone },
      { id: `contact-alternate-${id}`, patientId: id, name: details.alternateDecisionMaker.name, relationship: details.alternateDecisionMaker.relationship || 'Family contact', phone: details.alternateDecisionMaker.phone },
      { id: `contact-doctor-${id}`, patientId: id, name: details.localPhysician.name || latestReleaseFor(id)?.physician || '', relationship: details.localPhysician.clinic || 'Doctor', phone: details.localPhysician.phone },
    ].filter((contact) => contact.name.trim());
  }

  function saveDoctorConversation(entry: DoctorConversationEntry, draft?: AssistedNote) {
    if (!isTreatingPhysician || sessionType !== 'care-team' || entry.patientId !== selectedId) return;
    setDoctorConversations((entries) => [...entries, entry]);
    if (entry.note.trim()) {
      updateSelectedRecord((current) => {
        let next = !current.versionPublished && entry.note.trim() === current.draftSource.trim() ? current : prepareVisitNote(current, entry.note);
        if (draft) {
          const keys = Object.keys(draft.fields) as DraftFieldKey[];
          next = reviseSummary(next, { draftFields: draft.fields });
          next = reviseSummary(next, { draftStatuses: Object.fromEntries(keys.map((key) => [key, draft.fields[key]?.trim() ? 'ready' : 'not-stated'])) as Record<DraftFieldKey, DraftFieldStatus>, draftExcerpts: draft.excerpts });
        }
        return next;
      });
      setView('doctor-review');
    } else setView('doctor-history');
  }

  function detailsFor(id: string): PatientDetails {
    const stored = patientDetailsById[id];
    if (stored) return stored;
    const item = workItems.find((w) => w.hospitalId === id);
    const profile = patientProfiles[id];
    const [dmName, dmRelationship] = (profile?.participant ?? '').split(' · ');
    const emptyDm: DecisionMaker = { name: '', relationship: '', phone: '', basis: '' };
    return {
      fullName: item?.patient ?? 'Unknown patient',
      dob: profile?.dob ?? '',
      sex: profile?.sex ?? '',
      address: '',
      phone: '',
      language: '',
      primaryDecisionMaker: dmName
        ? { name: dmName, relationship: dmRelationship ?? '', phone: '', basis: '' }
        : emptyDm,
      alternateDecisionMaker: emptyDm,
      localPhysician: { name: '', clinic: '', phone: '' },
    };
  }

  function saveDetails(id: string, next: PatientDetails) {
    setPatientDetailsById((state) => ({ ...state, [id]: next }));
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? next.fullName;
    addAudit('Updated their personal details', patientName, 'PATIENT', patientName);
    notify('Your details were saved.');
  }

  function requestReview(id: string) {
    setReviewRequestedById((state) => ({ ...state, [id]: '28 Aug 2026' }));
    setWorkItems((items) =>
      items.map((item) =>
        item.hospitalId === id
          ? { ...item, status: item.status === 'Escalated' ? 'Escalated' : 'Awaiting review', lastActivity: 'Patient requested a review of the care plan' }
          : item,
      ),
    );
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit('Requested a review of the care plan', patientName, 'PATIENT', patientName);
    notify('Your review request was sent to your care team.');
  }

  function signConsent(id: string, input: { typedName: string; declaration: 'self' | 'delegated' }) {
    if (!hasLoadedReleasedSummary(id)) return;
    const versionLabel = planVersionLabel(id);
    setConsentById((state) => ({
      ...state,
      [`${id}:${latestReleaseFor(id)?.number}`]: {
        versionLabel,
        typedName: input.typedName,
        declaration: input.declaration,
        signedAt: new Date().toLocaleString('en-GB'),
      },
    }));
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit(`Gave consent to ${versionLabel} (typed name)`, patientName, 'PATIENT', patientName);
    notify('Your consent was recorded.');
  }

  function issueCard(id: string) {
    setCardIssuedById((state) => ({ ...state, [id]: '28 Aug 2026' }));
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit('Issued their emergency card', patientName, 'PATIENT', patientName);
    notify('Your emergency card was issued.');
  }

  function planVersionLabel(id: string): string {
    const release = latestReleaseFor(id);
    return release ? `Version ${release.number}` : 'No released version';
  }

  function latestReleaseFor(id: string): SummaryRelease | null | undefined {
    const state = recordStates[id];
    return state ? latestSummaryRelease(state) : undefined;
  }

  function consentFor(id: string): ConsentRecord | null {
    return consentById[`${id}:${latestReleaseFor(id)?.number}`] ?? null;
  }

  function planFieldsFor(id: string): Array<{ label: string; value: string }> {
    const released = latestReleaseFor(id);
    if (released?.fields) {
      const labels: Record<DraftFieldKey, string> = { priorities: 'What matters to you', participants: 'Who was there', topics: 'What you discussed', openQuestions: 'Still to discuss', followUp: 'Next steps' };
      return fieldConfig.map((f) => ({ label: labels[f.key], value: released.fields![f.key] }));
    }
    if (patientHasApprovedSummary(id)) {
      return [
        {
          label: 'Summary',
          value: 'Ask your care team for a copy of this summary.',
        },
      ];
    }
    return [];
  }

  function hasLoadedReleasedSummary(id: string): boolean {
    return Boolean(latestReleaseFor(id)?.fields);
  }

  function clinicalItemsFor(id: string): Array<{ label: string; value: string }> {
    const record = ectprQuickViews[id];
    if (!record) return [{ label: 'Emergency care plan', value: 'No emergency care plan signed yet' }];
    const notDecided = 'Not decided yet';
    const goalLabels: Record<string, string> = {
      'prioritise-life': 'Prioritise length of life',
      balanced: 'Balance comfort and length of life',
      'prioritise-comfort': 'Prioritise comfort',
    };
    const transferLabels: Record<string, string> = {
      'any-deterioration': 'Transfer to hospital for any deterioration',
      'listed-reasons-only': 'Transfer only for listed reasons',
      no: 'Stay in current care setting',
    };
    const cprLabels: Record<string, string> = {
      attempt: 'Attempt CPR',
      dnacpr: 'Do not attempt CPR',
    };
    const breathingLabels: Record<string, string> = {
      'comfort-only': 'Comfort measures only',
      mask: 'Simple oxygen mask',
      'non-rebreather': 'Non-rebreather mask',
      hfnc: 'High-flow nasal oxygen',
      niv: 'Non-invasive ventilation',
      'intubation-trial': 'Intubation, time-limited trial',
      'intubation-no-limit': 'Intubation, no limit',
    };
    const icuLabels: Record<string, string> = {
      yes: 'ICU care appropriate',
      'time-limited-trial': 'Time-limited ICU trial',
      no: 'ICU care not appropriate',
    };
    return [
      { label: 'Overall goal', value: record.goal ? goalLabels[record.goal] : notDecided },
      { label: 'Hospital transfer', value: record.hospitalTransfer ? transferLabels[record.hospitalTransfer] : notDecided },
      { label: 'CPR', value: record.cpr ? cprLabels[record.cpr] : notDecided },
      { label: 'Highest breathing support', value: record.breathingCeiling ? breathingLabels[record.breathingCeiling] : notDecided },
      { label: 'ICU', value: record.icu ? icuLabels[record.icu] : notDecided },
    ];
  }

  function buildHistoryEvents(hospitalId: string): HistoryEvent[] {
    const events: HistoryEvent[] = [];
    const patientName = workItems.find((w) => w.hospitalId === hospitalId)?.patient ?? '';

    if (hospitalId === 'CANCER-20418') {
      events.push({
        id: 'seed-conversation',
        sortKey: Date.UTC(2026, 7, 14),
        dateLabel: '14 Aug 2026',
        kind: 'conversation',
        title: 'Conversation initiated',
        detail: 'Meera requested time to talk through preferences at home with family before writing a clinician-reviewed summary.',
        actor: 'Dr Sujay',
      });
      events.push({
        id: 'seed-enrolment',
        sortKey: Date.UTC(2026, 7, 18),
        dateLabel: '18 Aug 2026',
        kind: 'enrolment',
        title: 'Follow-up coordinator assigned',
        detail: 'Anitha Rao assigned as dedicated continuity owner to maintain accountability for scheduled follow-up.',
        actor: 'Dr Sujay',
      });
    }

    appointments
      .filter((a) => a.hospitalId === hospitalId)
      .forEach((a) => {
        const [y, m, d] = a.date.split('-').map(Number);
        events.push({
          id: `appt-${a.id}`,
          sortKey: Date.UTC(y, (m ?? 1) - 1, d ?? 1, ...(a.time ? a.time.split(':').map(Number) as [number, number] : [0, 0])),
          dateLabel: `${formatDateForDisplay(a.date)} · ${a.time}`,
          kind: 'appointment',
          title: a.type,
          detail: `${a.status}${a.outcome ? ' — ' + a.outcome : ''} · ${a.mode}`,
          actor: a.clinician,
          status: a.status,
        });
      });

    auditEvents
      .filter((e) => e.patientId ? e.patientId === hospitalId : e.record.split(/ [|\u00b7] /)[0] === patientName)
      .forEach((e, index) => {
        const match = /^(\d{1,2}) (\w{3}) · (\d{2}):(\d{2})$/.exec(e.time);
        const monthMap: Record<string, number> = {
          Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11,
        };
        const sortKey = e.timestamp ? Date.parse(e.timestamp) : match
          ? Date.UTC(2026, monthMap[match[2]] ?? 7, Number(match[1]), Number(match[3]), Number(match[4]))
          : Date.UTC(2026, 7, 28);
        const dateLabel = match ? `${Number(match[1])} ${match[2]} 2026 · ${match[3]}:${match[4]}` : e.time;

        let kind: HistoryEvent['kind'] | null = null;
        if (e.badge === 'PUBLISH') kind = 'version';
        else if (e.badge === 'OUTREACH') kind = 'call';
        else if (e.badge === 'ENROL') kind = 'enrolment';
        else if (e.badge === 'SCHEDULE') kind = null;
        else if (e.badge === 'ACCESS') {
          kind = e.event.startsWith('Viewed') ? 'retrieval' : e.event.includes('Authorisation') ? 'consent' : 'other';
        } else if (e.badge === 'VIEW') {
          kind = e.event.includes('draft') ? 'draft' : e.event.startsWith('Viewed') ? 'retrieval' : 'other';
        } else if (e.badge === 'PATIENT') {
          if (e.event.toLowerCase().includes('consent')) kind = 'consent';
          else if (e.event.toLowerCase().includes('emergency card')) kind = 'card';
          else if (e.event.toLowerCase().includes('review')) kind = 'review';
          else if (e.event.toLowerCase().includes('details')) kind = 'details';
          else kind = 'other';
        } else {
          kind = 'other';
        }

        if (kind === null) return;

        events.push({
          id: e.id ?? `audit-${index}-${e.time}`,
          sortKey,
          dateLabel,
          kind,
          title: e.event,
          detail: e.record,
          actor: e.actor,
        });
      });

    const seen = new Set<string>();
    return events.filter((e) => {
      if (seen.has(e.id)) return false;
      seen.add(e.id);
      return true;
    });
  }

  const visibleItems = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return workItems.filter((item) => {
      const matchesFilter = activeFilter === 'All' || item.status === activeFilter;
      const matchesQuery =
        !normalizedQuery ||
        item.patient.toLowerCase().includes(normalizedQuery) ||
        item.hospitalId.toLowerCase().includes(normalizedQuery) ||
        item.owner.toLowerCase().includes(normalizedQuery);
      return matchesFilter && matchesQuery;
    });
  }, [activeFilter, query, workItems]);

  const filteredAuditEvents = useMemo(() => {
    if (auditFilterActor === 'All') return auditEvents;
    return auditEvents.filter((event) => event.actor.includes(auditFilterActor));
  }, [auditEvents, auditFilterActor]);

  const counts = useMemo(() => {
    return {
      all: workItems.length,
      dueToday: workItems.filter((i) => i.status === 'Due today').length,
      overdue: workItems.filter((i) => i.status === 'Overdue').length,
      awaitingOutreach: workItems.filter((i) => i.status === 'Awaiting outreach').length,
      awaiting: workItems.filter((i) => i.status === 'Awaiting review').length,
      escalated: workItems.filter((i) => i.status === 'Escalated').length,
      completed: workItems.filter((i) => i.status === 'Completed').length,
    };
  }, [workItems]);

  const draftFieldBlockers = fieldConfig.filter(
    ({ key }) => draftStatuses[key] === 'clarify' || !draftFields[key].trim() || (draftStatuses[key] === 'ready' && (!recordState.draftExcerpts[key].trim() || !recordState.draftSource.includes(recordState.draftExcerpts[key]))),
  );
  const draftReleaseReady = draftPrepared && Boolean(recordState.draftSource.trim()) && draftFieldBlockers.length === 0;
  const verificationPassedCount = Object.values(verificationChecks).filter(Boolean).length;
  const acknowledgementComplete =
    authorisation !== 'Pending patient review' && authorisation !== 'Declined';
  const verificationReady = isTreatingPhysician && isSummaryReadyToRelease(recordState);

  function navigate(nextView: View) {
    if (!canOpenCareView(nextView, sessionType, currentRole)) {
      notify('This page is for the care team member responsible for it.');
      return;
    }
    if ((nextView === 'draft' || nextView === 'verify') && !isTreatingPhysician) {
      notify('Only the treating physician can prepare or verify a structured summary.');
      return;
    }
    if (nextView === 'outreach' && !isCareCoordinator) {
      notify('Only the care coordinator can save this call.');
      return;
    }
    if (nextView === 'outreach') {
      setOutreachOutcome('');
      setOutreachNote('');
      setOutreachDate('');
      setOutreachError('');
    }
    setView(nextView);
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function notify(message: string) {
    setToast({ message });
  }

  function selectPatient(hospitalId: string) {
    updateRecordState(hospitalId, (current) => ({
      ...current,
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
      retrievalVersion: null,
      unlockedVersion: null,
    }));
    setComparisonVersion(null);
    setSelectedId(hospitalId);
  }

  function beginRetrievalFor(hospitalId: string, releaseNumber?: number) {
    if (!currentRole.startsWith('Dr')) {
      notify('Choose a doctor to view this note.');
      return;
    }
    selectPatient(hospitalId);
    updateRecordState(hospitalId, (current) => ({
      ...current,
      retrievalReason: currentRole.startsWith('Dr Isha')
        ? 'Emergency hand-off retrieval (Break-glass)'
        : 'Planned consultation / Outpatient review',
      careRelationship: currentRole.startsWith('Dr Isha')
        ? 'Emergency receiving physician'
        : 'Current primary oncology treating team',
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
      retrievalVersion: releaseNumber ?? null,
      unlockedVersion: null,
    }));
    navigate('retrieve');
  }

  function beginEmergencyRetrieval() {
    beginRetrievalFor('CANCER-20418');
  }

  function openOpsHub(tab: OpsHubTab = 'map') {
    setOpsHubTab(tab);
    setOpsHubOpen(true);
  }

  function renderContinuityOpsHub(session: 'care-team' | 'family' | 'patient' | 'landing') {
    const release = latestSummaryRelease(recordStates[selectedId] ?? createInitialRecordState(selectedId));
    const ready = patientHasApprovedSummary(selectedId);
    const unlocked = Boolean((recordStates[selectedId] ?? createInitialRecordState(selectedId)).retrievalUnlocked);
    return (
      <ContinuityOpsHub
        open={opsHubOpen}
        initialTab={opsHubTab}
        sessionType={session}
        currentRole={currentRole}
        patientName={selectedItem.patient}
        hospitalId={selectedItem.hospitalId}
        diagnosis={selectedProfile.stage}
        versionLabel={release ? `Version ${release.number}` : 'Version 1'}
        verifiedBy={release?.physician}
        verifiedOn={release ? releaseDate(release) : undefined}
        summaryReady={ready}
        retrievalUnlocked={unlocked}
        cprStatus={selectedItem.hospitalId === 'CANCER-20377' ? 'dnacpr' : 'full_cpr'}
        onClose={() => setOpsHubOpen(false)}
        onClinicCheckIn={(payload) => {
          setClinicCheckInNote(
            `Clinic QR check-in · ${payload.hospitalId} · ${payload.checkedInAt} · locator ${payload.locator}`,
          );
          setAuditEvents((events) => [
            {
              id: `AUDIT-QR-${Date.now().toString().slice(-4)}`,
              time: 'Just now',
              actor: currentRole,
              event: 'Clinic QR check-in',
              record: `${selectedItem.patient} · ${payload.locator}`,
              badge: 'SCHEDULE',
              patientId: payload.hospitalId,
            },
            ...events,
          ]);
          setToast({ message: `Clinic check-in recorded for ${selectedItem.patient}` });
        }}
        onUnlockHandoff={(hospitalId) => {
          if (!ready) {
            setToast({ message: 'No sealed summary yet — handoff unlock blocked.' });
            return;
          }
          updateRecordState(hospitalId, (current) => ({
            ...current,
            retrievalReason: 'Emergency hand-off retrieval (QR opaque locator)',
            careRelationship: 'Emergency receiving physician',
            retrievalAcknowledged: true,
            retrievalUnlocked: true,
            retrievalVersion: latestSummaryRelease(current)?.number ?? null,
            unlockedVersion: latestSummaryRelease(current)?.number ?? null,
          }));
          setToast({ message: `ED QR unlock applied for ${hospitalId}` });
          if (session !== 'landing') {
            setOpsHubOpen(false);
            navigate('retrieve');
          }
        }}
        onSealMro={(mroId) => {
          setAuditEvents((events) => [
            {
              id: `AUDIT-MRO-${Date.now().toString().slice(-4)}`,
              time: 'Just now',
              actor: currentRole,
              event: 'Medical Record Object sealed',
              record: mroId,
              badge: 'PUBLISH',
              patientId: selectedItem.hospitalId,
            },
            ...events,
          ]);
          setToast({ message: `MRO sealed · ${mroId}` });
        }}
      />
    );
  }

  function beginGuidedConversation() {
    if (!isTreatingPhysician) {
      notify('Only the treating physician can record conversation coverage or prepare a structured draft.');
      return;
    }
    selectPatient('CANCER-20418');
    navigate('guide');
  }

  function openPatient(item: WorkItem) {
    selectPatient(item.hospitalId);
    setProfileTab('overview');
    navigate('patient');
  }

  function patientHasApprovedSummary(id: string): boolean {
    return Boolean(latestReleaseFor(id));
  }

  function patientSummaryLabel(id: string): string {
    const release = latestReleaseFor(id);
    return release ? `Version ${release.number} · ${releaseDate(release)}` : 'No approved summary';
  }


  function addAudit(
    event: string,
    record = `${selectedItem.patient}`,
    badge?: AuditEvent['badge'],
    actor?: string,
    patientId = selectedId,
    releaseNumber?: number,
  ) {
    setAuditEvents((events) => [
      {
        id: crypto.randomUUID(),
        patientId,
        releaseNumber,
        timestamp: new Date().toISOString(),
        time: new Date().toLocaleString('en-GB'),
        actor: actor ?? currentRole.split(' · ')[0],
        event,
        record,
        badge,
      },
      ...events,
    ]);
  }

  async function copySummaryToClipboard() {
    const release = view === 'retrieve' ? requestedRelease : currentRelease;
    if (!release?.fields || (view === 'retrieve' && recordState.unlockedVersion !== release.number)) return;
    const summaryText = [`GOALS-OF-CARE SUMMARY`, `Patient: ${selectedItem.patient} (${selectedId})`, `Version ${release.number} | ${releaseDate(release)} | ${release.physician}`, ...fieldConfig.map(({ key, label }) => `${label}: ${release.fields![key]}`)].join('\n');
    try {
      await navigator.clipboard.writeText(summaryText);
      notify(`Version ${release.number} copied.`);
    } catch {
      notify('Copy failed. Use Print to save a copy.');
    }
  }

  function submitEnrolment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!isTreatingPhysician) {
      notify('Only the treating physician can enrol a patient in continuity follow-up.');
      return;
    }
    if (!enrolConfirmed || !enrolDate || !enrolOwner) return;

    const candidate = enrolmentCandidates[enrolPatientId];
    const form = event.currentTarget;
    const contactChannel = form.elements.namedItem('contact-channel') as HTMLSelectElement | null;
    const coordinationNotes = form.elements.namedItem('coordination-notes') as HTMLTextAreaElement | null;
    const coordinationNote = coordinationNotes?.value.trim() ?? '';
    if (!coordinationNote) {
      notify('Add a coordination note before creating the follow-up task.');
      return;
    }
    const formattedDate = new Intl.DateTimeFormat('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(new Date(`${enrolDate}T00:00:00Z`));

    if (workItems.some((item) => item.hospitalId === enrolPatientId)) {
      setEnrolOpen(false);
      notify(`${candidate.patient} already has an active continuity task.`);
      return;
    }

    const newItem: WorkItem = {
      patient: candidate.patient,
      hospitalId: enrolPatientId,
      purpose: enrolReason,
      due: formattedDate,
      owner: enrolOwner,
      status: 'Awaiting outreach',
      lastActivity: `Enrolled by ${currentRole.split(' · ')[0]}`,
      contactChannel: contactChannel?.value ?? 'Not specified',
      coordinationNote,
    };
    setWorkItems((items) => [newItem, ...items]);
    setRecordStates((states) => ({
      ...states,
      [enrolPatientId]: states[enrolPatientId] ?? createInitialRecordState(enrolPatientId),
    }));
    setEnrolOpen(false);
    setEnrolConfirmed(false);
    setActiveFilter('All');
    setQuery('');
    setAuditEvents((events) => [
      {
        time: '28 Aug · 10:02',
        actor: currentRole.split(' · ')[0],
        event: 'Created clinician-selected follow-up task',
        record: candidate.patient,
        badge: 'ENROL',
      },
      ...events,
    ]);
    notify(`Follow-up task created for ${candidate.patient}.`);
  }

  function saveOutreach(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!isCareCoordinator) {
      notify('Only the care coordinator can save this call.');
      return;
    }
    const note = outreachNote.trim();
    const submittedDate = event.currentTarget.querySelector<HTMLInputElement>('input[type="date"]')?.value || outreachDate;
    const nextReviewDate = formatDateForDisplay(submittedDate);
    if (!outreachOutcome || !note || !nextReviewDate) {
      const missing = !outreachOutcome ? 'outcome' : !note ? 'note' : 'date';
      setOutreachError(
        missing === 'outcome' ? 'Select an outreach outcome.'
          : missing === 'note' ? 'Enter an outreach note.'
            : 'Choose the next planned review date.',
      );
      event.currentTarget.querySelector<HTMLElement>(`[data-outreach-field="${missing}"]`)?.focus();
      return;
    }

    const nextStatus: FollowUpStatus =
      outreachOutcome === 'Discussion completed'
        ? 'Awaiting review'
        : outreachOutcome === 'Escalate to attending clinician'
          ? 'Escalated'
          : outreachOutcome === 'Patient unavailable'
            ? 'Awaiting outreach'
            : 'Due today';

    setWorkItems((items) =>
      items.map((item) =>
        item.hospitalId === selectedId
          ? {
              ...item,
              due: nextReviewDate,
              status: nextStatus,
              lastActivity: outreachOutcome,
            }
          : item,
      ),
    );
    updateSelectedRecord((current) => ({
      ...current,
      latestOutreach: {
        outcome: outreachOutcome,
        note,
        nextReviewDate,
        actor: currentRole.split(' · ')[0],
      },
    }));
    addAudit(
      `Recorded outreach · ${outreachOutcome}`,
      selectedItem.patient,
      'OUTREACH',
    );
    notify(`Outreach recorded. Next state: ${nextStatus}.`);
    navigate('patient');
  }

  function prepareDraft() {
    if (versionPublished || draftPrepared) return;
    if (!currentRole.startsWith('Dr Sujay')) {
      notify('Only the treating physician can create or update a structured note.');
      return;
    }
    updateSelectedRecord((current) => ({
      ...current,
      draftPrepared: true,
      verificationChecks: {
        source: false,
        ambiguity: false,
        inference: false,
        reviewDate: false,
      },
      authorisation: 'Pending patient review',
      attested: false,
    }));
    addAudit('Prepared source-linked structured draft', selectedItem.patient, 'VIEW');
    notify(recordState.draftIsNewConversation ? 'Add your conversation notes.' : 'Preloaded draft opened for review.');
  }

  function setDraftFieldStatus(key: DraftFieldKey, status: DraftFieldStatus) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => setSummaryFieldStatus(current, key, status));
  }

  function startConversation() {
    if (!isTreatingPhysician) return;
    const next = beginSummaryRevision(recordState);
    if (next !== recordState) {
      updateSelectedRecord(() => ({
        ...next,
        conversationCoverage: Object.fromEntries(conversationDomains.map(({ key }) => [key, 'defer'])) as Record<ConversationDomainKey, ConversationDisposition>,
        retrievalAcknowledged: false,
        retrievalUnlocked: false,
        unlockedVersion: null,
      }));
      addAudit(`Started draft for Version ${nextSummaryVersion(next)}`, selectedItem.patient, 'VIEW');
    }
    navigate('draft');
  }

  function publishVersion() {
    if (!isTreatingPhysician || !verificationReady) return;
    const next = releaseSummary(recordState, {
      physician: currentRole.split(' \u00b7 ')[0],
      releasedAt: new Date().toISOString(),
      coverage: recordState.conversationCoverage,
    });
    if (next === recordState) {
      notify('Complete the source links and review before releasing.');
      return;
    }
    const release = latestSummaryRelease(next)!;
    updateSelectedRecord(() => ({ ...next, retrievalAcknowledged: false, retrievalUnlocked: false, unlockedVersion: null }));
    setWorkItems((items) => items.map((item) => item.hospitalId === selectedId
      ? { ...item, status: 'Completed', lastActivity: `Version ${release.number} verified` } : item));
    addAudit(`Verified and published version ${release.number}`, `${selectedItem.patient} | v${release.number}`, 'PUBLISH', undefined, selectedId, release.number);
    notify(`Version ${release.number} released. Earlier versions are preserved.`);
    navigate('patient');
  }

  function openRetrievedRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requestedRelease || requestedRelease.patientId !== selectedId) {
      notify('No released summary is available for this patient.');
      return;
    }
    if (!currentRole.startsWith('Dr')) {
      notify('Choose a doctor to view this note.');
      return;
    }
    if (!retrievalReason || !careRelationship) {
      notify('Choose why you need this note and your role in the patient’s care.');
      return;
    }
    const isBreakGlassAccess = retrievalReason === 'Emergency hand-off retrieval (Break-glass)';
    if (
      isBreakGlassAccess &&
      (!currentRole.startsWith('Dr Isha') || careRelationship !== 'Emergency receiving physician')
    ) {
      notify('Emergency notes can only be opened by the receiving doctor.');
      return;
    }
    if (!retrievalAcknowledged) {
      notify('Confirm the retrieval boundary before viewing the verified record.');
      return;
    }
    updateSelectedRecord((current) => ({ ...current, retrievalUnlocked: true, unlockedVersion: requestedRelease.number }));
    window.scrollTo({ top: 0, behavior: 'instant' });
    addAudit(
      `Viewed Version ${requestedRelease.number} · ${isBreakGlassAccess ? 'Emergency' : 'Patient care'} · ${retrievalReason}`,
      `${selectedItem.patient} · v${requestedRelease.number}`,
      'ACCESS',
      undefined,
      selectedId,
      requestedRelease.number,
    );
    notify('Note opened. Reason saved in History.');
  }

  function renderPageHeading(
    _eyebrow: string,
    title: string,
    action?: React.ReactNode,
  ) {
    return (
      <div className="page-heading compact-heading">
        <div>
          <h1>{title}</h1>
        </div>
        {action}
      </div>
    );
  }

  function renderCareJourney() {
    const patientId = isFamilySession ? portalPatientId : selectedId;
    const patient = workItems.find((item) => item.hospitalId === patientId);
    if (!patient) return <p className="ptl-empty">Choose a patient.</p>;
    const firstName = patient.patient.split(' ')[0];
    const released = latestReleaseFor(patientId);
    const releasedFields = released?.fields ?? null;
    const releasedVersion = planVersionLabel(patientId);
    const nextAppointment = appointments
      .filter((item) => item.hospitalId === patientId && item.status === 'Scheduled' && item.date >= DEMO_TODAY)
      .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`))[0];
    const touchpoint = nextAppointment
      ? {
          date: `${new Date(`${nextAppointment.date}T00:00:00`).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })} at ${nextAppointment.time}`,
          purpose: nextAppointment.type,
          owner: nextAppointment.clinician,
        }
      : patient.status !== 'Completed'
        ? { date: patient.due, purpose: patient.purpose, owner: patient.owner }
        : null;
    const activeCoverageZone =
      coverageZones.find((zone) => zone.id === coverageZoneId) ?? coverageZones[0];

    return (
      <section className="care-journey" aria-labelledby="care-journey-title">
        <article className="care-journey-hero">
          <div className="care-journey-copy">
            <h1 id="care-journey-title">{firstName}&rsquo;s next steps</h1>
            {isFamilySession ? (
              <p className="care-journey-signed-in">
                Signed in as <strong>{familyMember?.name}</strong> · {familyMember?.relationship}
              </p>
            ) : (
              <div className="care-journey-actions">
                <button className="primary-button" type="button" onClick={() => navigate('home')}>
                  <IconUsers className="w-4 h-4" />
                  Care team
                </button>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => openDemoRoleWorkflow('Dr Isha Menon · Emergency physician')}
                >
                  Emergency doctor
                  <IconChevronRight className="w-4 h-4" />
                </button>
              </div>
            )}

          </div>

          <div className="family-care-photo">
            <Image src="/care-consultation.jpg" alt="A clinician and patient in conversation" fill sizes="(max-width: 600px) 120px, 300px" />
          </div>
        </article>

        {isFamilySession && <button className="family-daily-entry" type="button" onClick={() => navigate('daily-care')}>
          <span><strong>Daily care</strong><small>{familyTasks.filter((task) => task.patientId === patientId && !task.completedBy && task.kind !== 'Question').length} open tasks · visits and questions</small></span><span aria-hidden="true">→</span>
        </button>}

        <div className="care-journey-primary-grid">
          <article className="care-journey-card care-journey-next">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-warm" aria-hidden="true"><IconHistory className="w-5 h-5" /></span>
              <div>
                <h2>Next visit</h2>
              </div>
            </div>
            {touchpoint ? (
              <div className="care-journey-date-row">
                <strong>{touchpoint.date}</strong>
                <span>{touchpoint.purpose}<br />Contact: {touchpoint.owner}</span>
              </div>
            ) : <p>No upcoming touchpoint is recorded. Ask your care team about the next review.</p>}
            <ul className="care-journey-prep-list" aria-label="Plain-language next-visit checklist">
              <li><IconCheck className="w-3.5 h-3.5" />Write down the questions you want the care team to answer.</li>
              <li><IconCheck className="w-3.5 h-3.5" />Confirm who {firstName} wants included in the conversation.</li>
              <li><IconCheck className="w-3.5 h-3.5" />Tell {touchpoint?.owner ?? patient.owner} if the time or people joining need to change.</li>
            </ul>
            {isFamilySession && <button className="secondary-button" type="button" onClick={() => navigate('prepare-conversation')}>Prepare for the conversation</button>}
          </article>

          <article className="care-journey-card care-journey-summary">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-green" aria-hidden="true"><IconShieldCheck className="w-5 h-5" /></span>
              <div>
                <h2>Reviewed summary</h2>
              </div>
              <span className="care-journey-version">{releasedVersion}</span>
            </div>
            {releasedFields ? <>
            <dl className="care-journey-summary-list">
              <div>
                <dt>What matters now</dt>
                <dd>{releasedFields.priorities}</dd>
              </div>
              <div>
                <dt>Next step</dt>
                <dd>{releasedFields.followUp}</dd>
              </div>
              <div>
                <dt>Still to discuss</dt>
                <dd>{releasedFields.openQuestions}</dd>
              </div>
            </dl>
            <div className="care-journey-provenance">
              <IconShieldCheck className="w-3.5 h-3.5" />
              <span>
                Reviewed by <strong>{released?.physician}</strong> on {releaseDate(released)}.
              </span>
            </div>
            </> : <p className="care-journey-card-copy">{patientHasApprovedSummary(patientId)
              ? 'Ask the care team for a copy of this summary.'
              : 'The care team has not released a written summary yet.'}</p>}
          </article>
        </div>

        <div className="care-journey-secondary-grid">
          <article className="care-journey-card care-journey-team-card">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-indigo" aria-hidden="true"><IconUsers className="w-5 h-5" /></span>
              <div>
                <h2>Care team</h2>
              </div>
            </div>
            <div className="care-journey-people">
              <div className="care-journey-person">
                <span className="care-journey-avatar physician" aria-hidden="true">SS</span>
                <div><strong>Dr Sujay</strong><span>Clinical lead</span></div>
              </div>
              <div className="care-journey-person">
                <span className="care-journey-avatar coordinator" aria-hidden="true">AR</span>
                <div><strong>Anitha Rao</strong><span>Care coordinator</span></div>
              </div>
            </div>
          </article>

          <button className="family-nearby-button" type="button" onClick={() => navigate('care-near-me')}>
            <IconMapPin className="w-5 h-5" /> Nearby care <span aria-hidden="true">↗</span>
          </button>
        </div>

        <details className="care-journey-card care-journey-support-card">
          <summary>Illustrative coverage map</summary>
          <p className="care-journey-card-copy">
            An illustrative sketch of where continuity follow-up happens across Mumbai. The outline is stylised, the counts are seeded,
            and no address, availability, or contact detail here has been verified. Ask the care team what is actually available.
          </p>
          <div className="coverage-layout">
            <div className="coverage-map-frame">
              <CoverageMap activeZone={coverageZoneId} />
              <p className="coverage-map-caption">Stylised outline · not survey geography</p>
            </div>
            <div className="coverage-detail">
              <div className="coverage-zone-buttons" role="group" aria-label="Seeded continuity coverage areas">
                {coverageZones.map((zone) => (
                  <button
                    key={zone.id}
                    type="button"
                    className={coverageZoneId === zone.id ? 'coverage-zone-button active' : 'coverage-zone-button'}
                    aria-pressed={coverageZoneId === zone.id}
                    onClick={() => setCoverageZoneId(zone.id)}
                  >
                    <span>{zone.name}</span>
                    <strong>{zone.points}</strong>
                  </button>
                ))}
              </div>
              <div className="coverage-zone-detail" aria-live="polite">
                <p className="care-journey-label">{activeCoverageZone.area}</p>
                <h3>{activeCoverageZone.name}</h3>
                <p>{activeCoverageZone.note}</p>
                <p className="coverage-zone-count">
                  <strong>{activeCoverageZone.points}</strong> seeded continuity contact points · none independently verified
                </p>
              </div>
            </div>
          </div>
        </details>


      </section>
    );
  }

  function renderHome() {
    const statusRank: Record<string, number> = {
      Overdue: 0,
      Escalated: 1,
      'Due today': 2,
      'Awaiting review': 3,
      'Awaiting outreach': 4,
    };
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    function parseDueDate(due: string): number {
      const isoMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(due);
      if (isoMatch) {
        return Date.UTC(Number(isoMatch[1]), Number(isoMatch[2]) - 1, Number(isoMatch[3]));
      }
      const parts = due.split(' ');
      if (parts.length === 3) {
        const day = Number(parts[0]);
        // Match on the first three letters so "Sep", "Sept" and "September" all parse.
        const monthIndex = monthNames.findIndex(
          (name) => name.slice(0, 3).toLowerCase() === parts[1].slice(0, 3).toLowerCase(),
        );
        const year = Number(parts[2]);
        if (!Number.isNaN(day) && monthIndex !== -1 && !Number.isNaN(year)) {
          return Date.UTC(year, monthIndex, day);
        }
      }
      return Number.POSITIVE_INFINITY;
    }
    function nextAppointmentLabel(id: string): string | null {
      const upcoming = appointments
        .filter((a) => a.hospitalId === id && a.status === 'Scheduled' && a.date >= DEMO_TODAY)
        .sort((a, b) => (a.date === b.date ? a.time.localeCompare(b.time) : a.date.localeCompare(b.date)));
      const next = upcoming[0];
      if (!next) return null;
      const [, monthStr, dayStr] = next.date.split('-');
      const monthName = monthNames[Number(monthStr) - 1] ?? monthStr;
      const day = Number(dayStr);
      return `Next: ${day} ${monthName} · ${next.time}`;
    }
    const openItems = workItems
      .filter((item) => item.status !== 'Completed')
      .slice()
      .sort((a, b) => {
        const rankDiff = (statusRank[a.status] ?? 99) - (statusRank[b.status] ?? 99);
        if (rankDiff !== 0) return rankDiff;
        return parseDueDate(a.due) - parseDueDate(b.due);
      });
    const completedItems = workItems.filter((item) => item.status === 'Completed');

    function renderHandoffRow(item: WorkItem) {
      const initials = item.patient
        .split(' ')
        .map((p) => p[0])
        .join('')
        .slice(0, 2);
      const nextAppt = nextAppointmentLabel(item.hospitalId);
      const approved = patientHasApprovedSummary(item.hospitalId);
      return (
        <li className="th-row" key={item.hospitalId}>
          <div className="th-patient">
            <span className="th-avatar" aria-hidden="true">{initials}</span>
            <div style={{ minWidth: 0 }}>
              <div className="th-name">{item.patient}</div>
              <div className="th-id">{item.hospitalId}</div>
            </div>
          </div>
          <div className="th-task">
            <div>{item.purpose}</div>
            <div className="th-last-update">Last update: {item.lastActivity}</div>
          </div>
          <div className="th-when">
            <div className="th-due">{item.due}</div>
            <StatusPill status={item.status} />
            {nextAppt && <div className="th-next-appt">{nextAppt}</div>}
          </div>
          <div className="th-owner">
            <span className="th-owner-label">Owner</span>
            <span className="owner-badge">{item.owner}</span>
          </div>
          <div className="th-summary">
            {approved ? (
              <span className="th-chip th-chip-ok">
                <IconCheck className="w-3 h-3" />
                {patientSummaryLabel(item.hospitalId)}
              </span>
            ) : (
              <span className="th-chip th-chip-none">No approved summary</span>
            )}
          </div>
          <div className="th-actions">
            <button
              className="secondary-button"
              type="button"
              onClick={() => openPatient(item)}
              aria-label={`Open ${item.patient}`}
            >
              Open
            </button>
            {approved && (
              <button
                className="primary-button"
                type="button"
                disabled={!currentRole.startsWith('Dr')}
                title={!currentRole.startsWith('Dr') ? 'Physician role required' : undefined}
                onClick={() => beginRetrievalFor(item.hospitalId)}
                aria-label={`View summary for ${item.patient}`}
              >
                <IconLock className="w-3.5 h-3.5" />
                View summary
              </button>
            )}
          </div>
        </li>
      );
    }

    return (
      <>
        <section className="care-day-hero">
          <div className="care-day-copy">
            <span className="care-day-date">{new Date(`${DEMO_TODAY}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}</span>
            <h1>Your care day.</h1>
            <div className="care-day-actions">
              <button className="primary-button" type="button" onClick={() => navigate('appointments')}><IconCalendar className="w-4 h-4" />Appointments</button>
              <button className="care-day-link" type="button" onClick={() => navigate('guide')}>Open conversation <span aria-hidden="true">↗</span></button>
              <button className="care-day-link" type="button" onClick={() => openOpsHub('map')}>Ops Hub <span aria-hidden="true">↗</span></button>
            </div>
          </div>
          <div className="care-day-photo">
            <Image src="/care-consultation.jpg" alt="A clinician listening to a patient during a consultation" fill sizes="(max-width: 600px) 40vw, 440px" priority />
          </div>
        </section>

        <section className="care-day-os" aria-label="Continuity OS features">
          <button type="button" onClick={() => openOpsHub('map')}>
            <span className="os-tag">MapLibre</span>
            <strong>GIS dispatch map</strong>
            <small>Live ASHA / 108 routing over Bangalore palliative cohort</small>
          </button>
          <button type="button" onClick={() => openOpsHub('qr')}>
            <span className="os-tag">QR Bridge</span>
            <strong>Check-in / handoff QR</strong>
            <small>Open a care record for clinic check-in or emergency handover</small>
          </button>
          <button type="button" onClick={() => openOpsHub('context')}>
            <span className="os-tag">Context</span>
            <strong>Continuity audit</strong>
            <small>Pass/fail gates on session, patient, geo, seal readiness</small>
          </button>
          <button type="button" onClick={() => openOpsHub('mro')}>
            <span className="os-tag">MRO</span>
            <strong>Medical Record Object</strong>
            <small>Seal + integrity-check the portable verified object</small>
          </button>
        </section>

        {clinicCheckInNote && (
          <p className="th-footnote" role="status" style={{ marginTop: 0, marginBottom: 12, color: '#0c5c3f', fontWeight: 650 }}>
            {clinicCheckInNote}
          </p>
        )}

        <section className="care-day-stats" aria-label="Follow-up overview">
          <button type="button" onClick={() => { setActiveFilter('Due today'); navigate('worklist'); }}><strong>{counts.dueToday}</strong><span>Due today</span><span className="stat-arrow" aria-hidden="true">↗</span></button>
          <button type="button" onClick={() => { setActiveFilter('Overdue'); navigate('worklist'); }}><strong>{counts.overdue}</strong><span>Overdue</span><span className="stat-arrow" aria-hidden="true">↗</span></button>
          <button type="button" onClick={() => { setActiveFilter('Awaiting review'); navigate('worklist'); }}><strong>{counts.awaiting}</strong><span>To review</span><span className="stat-arrow" aria-hidden="true">↗</span></button>
          <button type="button" onClick={() => { setActiveFilter('Awaiting outreach'); navigate('worklist'); }}><strong>{counts.awaitingOutreach}</strong><span>To contact</span><span className="stat-arrow" aria-hidden="true">↗</span></button>
        </section>

        <section className="detail-card th-card" aria-labelledby="th-title">
          <div className="th-header">
            <div>
              <h2 id="th-title">Your follow-ups</h2>
            </div>
            <div className="th-header-right">
              <span className="th-count"><strong>{openItems.length}</strong> open</span>
              <button className="text-button" type="button" onClick={() => navigate('worklist')}>
                View follow-ups →
              </button>
            </div>
          </div>

          {openItems.length > 0 ? (
            <ul className="th-list" role="list">
              {openItems.map(renderHandoffRow)}
            </ul>
          ) : (
            <p className="th-empty">No open follow-ups right now.</p>
          )}

          {completedItems.length > 0 && (
            <>
              <button
                className="th-toggle"
                type="button"
                aria-expanded={homeShowCompleted}
                onClick={() => setHomeShowCompleted((v) => !v)}
              >
                {homeShowCompleted ? 'Hide completed' : `Show completed (${completedItems.length})`}
              </button>
              {homeShowCompleted && (
                <ul className="th-list th-list-completed" role="list">
                  {completedItems.map(renderHandoffRow)}
                </ul>
              )}
            </>
          )}

          <p className="th-footnote">Ordered by follow-up status and date, not clinical urgency.</p>
        </section>

        <details className="role-route care-day-roles">
          <summary>Try another care team role</summary>
          <div className="role-route-grid">
            <button
              className="role-route-card clinician"
              type="button"
              onClick={() => openDemoRoleWorkflow('Dr Sujay · Clinical lead')}
            >
              <span className="role-route-icon" aria-hidden="true"><IconFileEdit className="w-5 h-5" /></span>
              <span>Treating clinician</span>
              <strong>Guide the conversation</strong>
              <small>Capture what was covered, prepare the draft, then verify it.</small>
            </button>
            <button className="role-route-card coordinator" type="button" onClick={() => openDemoRoleWorkflow('Anitha Rao · Care Coordinator')}>
              <span className="role-route-icon" aria-hidden="true"><IconCheck className="w-5 h-5" /></span>
              <span>Care coordinator</span>
              <strong>Keep follow-up on track</strong>
              <small>Record outreach and send clinical decisions back to the named physician.</small>
            </button>
            <button className="role-route-card receiving" type="button" onClick={() => openDemoRoleWorkflow('Dr Isha Menon · Emergency physician')}>
              <span className="role-route-icon" aria-hidden="true"><IconLock className="w-5 h-5" /></span>
              <span>Emergency physician</span>
              <strong>View summary</strong>
              <small>Record the clinical reason before opening the most recent verified version.</small>
            </button>
          </div>
        </details>
      </>
    );
  }

  function renderGuide() {
    const coverageValues = Object.values(conversationCoverage);
    const discussedCount = coverageValues.filter((value) => value === 'discussed').length;
    const deferCount = coverageValues.filter((value) => value === 'defer').length;
    const notDiscussedCount = coverageValues.filter((value) => value === 'not-discussed').length;
    const totalDomains = conversationDomains.length;
    const coverageBreakdown = [
      { key: 'discussed', label: 'Discussed', value: discussedCount },
      { key: 'defer', label: 'Needs clarification', value: deferCount },
      { key: 'not-discussed', label: 'Not discussed', value: notDiscussedCount },
    ];

    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('home')}>
          ← Home
        </button>
        {renderPageHeading(
          'Clinician-owned discussion framework',
          `Conversation with ${selectedItem.patient}`,
          <StatusPill status={`${discussedCount}/${conversationDomains.length} discussed`} />,
        )}

        <div className="conversation-layout">
          <section className="detail-card conversation-checklist">
            <div className="protocol-notice">
              <IconInfo className="w-4 h-4 flex-shrink-0" />
              <span>
                {isTreatingPhysician
                  ? 'Use these topics to guide the conversation.'
                  : `Read-only view for ${currentRole}. Only the treating physician can record coverage or prepare the source-linked draft.`}
              </span>
            </div>

            <div className="coverage-progress" aria-live="polite">
              <div className="coverage-progress-head">
                <div>
                  <p className="care-journey-label">Coverage recorded by the clinician</p>
                  <strong>
                    {discussedCount} of {totalDomains} domains discussed
                  </strong>
                </div>
                <span className="coverage-progress-note">
                  Silence stays recorded as not discussed
                </span>
              </div>
              <div
                className="coverage-progress-bar"
                role="img"
                aria-label={`${discussedCount} discussed, ${deferCount} needing clarification, ${notDiscussedCount} not discussed, of ${totalDomains} domains`}
              >
                {coverageBreakdown
                  .filter((segment) => segment.value > 0)
                  .map((segment) => (
                    <span
                      key={segment.key}
                      className={`coverage-progress-segment is-${segment.key}`}
                      style={{ width: `${(segment.value / totalDomains) * 100}%` }}
                    />
                  ))}
              </div>
              <ul className="coverage-progress-legend">
                {coverageBreakdown.map((segment) => (
                  <li key={segment.key}>
                    <span className={`coverage-progress-swatch is-${segment.key}`} aria-hidden="true" />
                    {segment.label}
                    <strong>{segment.value}</strong>
                  </li>
                ))}
              </ul>
            </div>

            <div className="conversation-domain-list">
              {conversationDomains.map((domain, index) => (
                <article
                  className={`conversation-domain is-${conversationCoverage[domain.key]}`}
                  key={domain.key}
                >
                  <span className="domain-index">{String(index + 1).padStart(2, '0')}</span>
                  <div className="domain-copy">
                    <h2>{domain.label}</h2>
                    <p>{domain.prompt}</p>
                  </div>
                  <label className="domain-disposition">
                    <span className="sr-only">Disposition for {domain.label}</span>
                    <select
                      value={conversationCoverage[domain.key]}
                      disabled={!isTreatingPhysician || versionPublished}
                      onChange={(event) =>
                        setConversationDisposition(
                          domain.key,
                          event.target.value as ConversationDisposition,
                        )
                      }
                    >
                      <option value="discussed">Discussed</option>
                      <option value="not-discussed">Not discussed</option>
                      <option value="defer">Defer / clarify</option>
                    </select>
                  </label>
                </article>
              ))}
            </div>
          </section>

          <aside className="conversation-side">
            <section className="detail-card capture-card">
              <span className="document-kicker">MVP capture mode</span>
              <h2>{recordState.draftIsNewConversation ? 'Conversation notes' : 'Conversation notes'}</h2>
              <dl className="definition-list">
                <div><dt>Participants</dt><dd>{recordState.draftFields.participants || 'Not recorded yet'}</dd></div>
                <div><dt>Input</dt><dd>Clinician-entered source note</dd></div>
                <div><dt>Stored with</dt><dd>Every released version</dd></div>
                <div><dt>Audio</dt><dd>Not recorded or retained</dd></div>
                <div><dt>Preparation</dt><dd>{recordState.draftIsNewConversation ? 'Manual, source-linked fields' : 'Prepared fields'}</dd></div>
              </dl>
            </section>

            <section className="detail-card conversation-next-step">
              <span className="document-kicker">Next step</span>
              <h2>Prepare the reviewable draft</h2>
              <p>
                Every substantive field remains linked to source text. Silence is recorded as not
                discussed, never converted into a preference.
              </p>
              <button
                className="primary-button full-width"
                type="button"
                disabled={!isTreatingPhysician}
                onClick={() => {
                  prepareDraft();
                  navigate('draft');
                }}
              >
                <IconFileEdit className="w-4 h-4" />
                {versionPublished ? 'View released source and summary' : isTreatingPhysician ? 'Continue to source-linked draft' : 'Treating physician update required'}
              </button>
              {versionPublished && isTreatingPhysician && <button className="secondary-button full-width" type="button" onClick={startConversation}>New conversation</button>}
            </section>
          </aside>
        </div>
      </>
    );
  }

  function renderWorklist() {
    const filterCards: Array<{
      filter: (typeof filters)[number];
      label: string;
      count: number;
      tone: string;
    }> = [
      { filter: 'All', label: 'All tasks', count: counts.all, tone: '' },
      { filter: 'Due today', label: 'Due today', count: counts.dueToday, tone: 'due-today' },
      { filter: 'Overdue', label: 'Overdue', count: counts.overdue, tone: 'overdue' },
      { filter: 'Awaiting outreach', label: 'Awaiting outreach', count: counts.awaitingOutreach, tone: '' },
      { filter: 'Awaiting review', label: 'Awaiting review', count: counts.awaiting, tone: 'awaiting' },
      { filter: 'Escalated', label: 'Escalated', count: counts.escalated, tone: 'overdue' },
      { filter: 'Completed', label: 'Completed', count: counts.completed, tone: 'completed' },
    ];

    return (
      <>
        <div className="page-heading">
          <div>
            <p className="eyebrow">
              <span className="environment-dot" /> Patient details
            </p>
            <h1>Goals-of-care follow-up</h1>
            <p className="heading-support">
              Accountable continuity tracking for clinician-planned family conversations and verified goals-of-care summaries.
            </p>
          </div>
          <button
            className="primary-button"
            type="button"
            disabled={!isTreatingPhysician}
            onClick={() => setEnrolOpen(true)}
          >
            <IconPlus className="w-4 h-4" />
            {isTreatingPhysician ? 'Enrol patient' : 'Treating physician role required'}
          </button>
        </div>

        {/*
          One filter control for this page. The pill row below the toolbar used
          to repeat these same states, so selecting a state in one place left the
          other looking untouched.
        */}
        <div className="metrics-strip" role="group" aria-label="Filter follow-up tasks by status">
          {filterCards.map((card) => (
            <button
              key={card.filter}
              type="button"
              className={`metric-card ${card.tone} ${activeFilter === card.filter ? 'active' : ''}`.trim()}
              aria-pressed={activeFilter === card.filter}
              onClick={() => setActiveFilter(card.filter)}
            >
              <span className="metric-label">{card.label}</span>
              <div className="metric-value-row">
                <span className="metric-number">{card.count}</span>
              </div>
            </button>
          ))}
        </div>

        <section className="worklist-panel" aria-labelledby="worklist-title">
          <div className="worklist-toolbar">
            <div className="worklist-toolbar-info">
              <h2 id="worklist-title">Care team worklist</h2>
              <span>
                Showing {visibleItems.length} of {workItems.length} continuity {visibleItems.length === 1 ? 'task' : 'tasks'}
              </span>
            </div>
            <label className="search-field">
              <span className="sr-only">Search patients</span>
              <IconSearch className="search-icon w-4 h-4" />
              <input
                type="search"
                placeholder="Search patient, ID, or owner..."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              {query && (
                <button
                  type="button"
                  className="search-clear-btn"
                  onClick={() => setQuery('')}
                  aria-label="Clear search"
                >
                  <IconX className="w-3.5 h-3.5" />
                </button>
              )}
            </label>
          </div>


          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Patient</th>
                  <th>Follow-up purpose</th>
                  <th>Due Date</th>
                  <th>Assigned Owner</th>
                  <th>Status</th>
                  <th><span className="sr-only">Open record</span></th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((item) => {
                  const initials = item.patient
                    .split(' ')
                    .map((p) => p[0])
                    .join('')
                    .slice(0, 2);
                  return (
                    <tr key={item.hospitalId}>
                      <td data-label="Patient">
                        <div className="patient-cell">
                          <span className="table-avatar">{initials}</span>
                          <div className="patient-meta">
                            <strong>{item.patient}</strong>
                            <span>{item.hospitalId}</span>
                          </div>
                        </div>
                      </td>
                      <td data-label="Follow-up purpose">
                        <strong>{item.purpose}</strong>
                        <span>{item.lastActivity}</span>
                      </td>
                      <td data-label="Due Date">
                        <strong>{item.due}</strong>
                      </td>
                      <td data-label="Assigned Owner">
                        <span className="owner-badge">
                          {item.owner}
                        </span>
                      </td>
                      <td data-label="Status">
                        <StatusPill status={item.status} />
                      </td>
                      <td className="row-action">
                        <button
                          type="button"
                          aria-label={`Open ${item.patient}'s follow-up`}
                          onClick={() => openPatient(item)}
                        >
                          Open <IconChevronRight className="w-3 h-3" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {visibleItems.length === 0 && (
              <div className="empty-state">
                <IconInfo className="w-6 h-6 text-gray-400" />
                <p>No follow-up tasks match the current search & filter criteria.</p>
                <button
                  type="button"
                  className="secondary-button mt-2"
                  onClick={() => {
                    setActiveFilter('All');
                    setQuery('');
                  }}
                >
                  Reset all filters
                </button>
              </div>
            )}
          </div>
        </section>
      </>
    );
  }

  function renderPatientRecord() {
    const isMeera = selectedItem.hospitalId === 'CANCER-20418';
    const hasVerifiedRecord = Boolean(currentRelease);
    const summaryFieldsAvailable = Boolean(currentRelease?.fields);
    const releasedFields = currentRelease?.fields ?? { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' };
    const timelineItems = [
      ...(isMeera
        ? [
            {
              date: '14 Aug',
              title: 'Conversation initiated',
              body: 'Meera requested time to talk through preferences at home with family before writing a clinician-reviewed summary.',
            },
            {
              date: '18 Aug',
              title: 'Follow-up coordinator assigned',
              body: 'Anitha Rao assigned as dedicated continuity owner to maintain accountability for scheduled follow-up.',
            },
            {
              date: '26 Aug',
              title: 'Family outreach conducted',
              body: 'Daughter Kavya confirmed family discussion underway; touchpoint confirmed for 28 August.',
            },
          ]
        : [
            {
              date: 'Task',
              title: 'Continuity task created',
              body: `${selectedItem.purpose} was recorded for this patient.`,
            },
            {
              date: 'Owner',
              title: 'Follow-up owner assigned',
              body: `${selectedItem.owner} owns the next operational touchpoint.`,
            },
          ]),
      ...(latestOutreach
        ? [
            {
              date: latestOutreach.nextReviewDate,
              title: `Outreach · ${latestOutreach.outcome}`,
              body: latestOutreach.note,
            },
          ]
        : []),
      ...recordState.releases.map((release) => ({
        date: releaseDate(release),
        title: `Version ${release.number} released`,
        body: `Reviewed by ${release.physician}. Earlier releases remain in the archive.`,
      })),
    ];
    const initials = selectedItem.patient
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
    const statusDotColor: Record<FollowUpStatus, string> = {
      'Due today': 'var(--medium-ink)',
      Overdue: 'var(--critical-ink)',
      'Awaiting review': 'var(--low-ink)',
      'Awaiting outreach': 'var(--low-ink)',
      Escalated: 'var(--critical-ink)',
      Completed: 'var(--safe-ink)',
    };
    const teamChip = selectedProfile.team.split(' · ').pop() ?? selectedProfile.team;
    const age = selectedProfile.age;
    const discussedCount = Object.values(conversationCoverage).filter(
      (value) => value === 'discussed',
    ).length;
    const draftReadyCount = draftPrepared
      ? fieldConfig.filter(({ key }) => draftStatuses[key] === 'ready').length
      : 0;

    const patientIndex = workItems.findIndex((item) => item.hospitalId === selectedItem.hospitalId);
    function cyclePatient(direction: 1 | -1) {
      if (workItems.length === 0) return;
      const baseIndex = patientIndex === -1 ? 0 : patientIndex;
      const nextIndex = (baseIndex + direction + workItems.length) % workItems.length;
      selectPatient(workItems[nextIndex].hospitalId);
    }

    function switchTab(tab: typeof profileTab) {
      setProfileTab(tab);
      setOpenMeasureMenu(null);
    }

    const goalsRows: Array<{
      key: string;
      label: string;
      percent: number;
      caption: string;
      onClick: () => void;
    }> = isMeera
      ? [
          {
            key: 'coverage',
            label: 'Record conversation coverage',
            percent: Math.round((discussedCount / 8) * 100),
            caption: `${discussedCount} of 8`,
            onClick: () => navigate('guide'),
          },
          {
            key: 'draft',
            label: 'Link draft fields to the source note',
            percent: Math.round((draftReadyCount / 5) * 100),
            caption: `${draftReadyCount} of 5`,
            onClick: () => navigate('draft'),
          },
          {
            key: 'verify',
            label: 'Complete verification checks',
            percent: Math.round((verificationPassedCount / 4) * 100),
            caption: `${verificationPassedCount} of 4`,
            onClick: () => navigate('verify'),
          },
        ]
      : [];

    const actionRows: Array<{ key: string; label: string; done: boolean; onClick: () => void }> = [
      {
        key: 'guide',
        label: 'Guided conversation recorded',
        done: isMeera ? discussedCount > 0 : hasVerifiedRecord,
        onClick: () => navigate('guide'),
      },
      {
        key: 'draft-prepared',
        label: 'Structured draft prepared from the source note',
        done: isMeera
          ? draftPrepared
          : selectedItem.status === 'Awaiting review' || hasVerifiedRecord,
        onClick: () => navigate('draft'),
      },
      {
        key: 'outreach',
        label: 'Coordinator outreach recorded',
        done: latestOutreach !== null || isMeera,
        onClick: () => navigate('outreach'),
      },
      {
        key: 'released',
        label: currentRelease ? `Version ${currentRelease.number} verified and released` : 'Summary verified and released',
        done: hasVerifiedRecord,
        onClick: () => navigate(isMeera ? 'verify' : 'records'),
      },
    ];

    const measureRows: Array<{
      key: string;
      label: string;
      done: boolean;
      menu: Array<{ key: string; label: string; onClick: () => void; disabled?: boolean }>;
    }> = [
      {
        key: 'conversation-documented',
        label: 'Goals-of-care conversation documented',
        done: hasVerifiedRecord || (isMeera && discussedCount > 0),
        menu: [
          { key: 'open-guide', label: 'Open guided conversation', onClick: () => navigate('guide') },
          { key: 'audit', label: 'View in audit log', onClick: () => navigate('audit') },
        ],
      },
      {
        key: 'acknowledgement',
        label: 'Patient or surrogate acknowledgement recorded',
        done: Boolean(currentRelease?.authorisation),
        menu: [
          { key: 'open-verify', label: 'Open verification', onClick: () => navigate('verify') },
          { key: 'audit', label: 'View in audit log', onClick: () => navigate('audit') },
        ],
      },
      {
        key: 'physician-verified',
        label: 'Current summary verified by the treating physician',
        done: hasVerifiedRecord,
        menu: [
          { key: 'open-docs', label: 'Open documents', onClick: () => switchTab('documents') },
          { key: 'audit', label: 'View in audit log', onClick: () => navigate('audit') },
        ],
      },
      {
        key: 'owner-set',
        label: 'Follow-up owner and next review date set',
        done: Boolean(selectedItem.owner && selectedItem.due),
        menu: [
          { key: 'open-scheduling', label: 'Open scheduling', onClick: () => switchTab('scheduling') },
          { key: 'audit', label: 'View in audit log', onClick: () => navigate('audit') },
        ],
      },
      {
        key: 'handoff',
        label: 'Handoff retrievable by a receiving clinician',
        done: hasVerifiedRecord,
        menu: [
          {
            key: 'retrieve',
            label: 'View summary',
            onClick: () => beginRetrievalFor(selectedId),
            disabled: !hasVerifiedRecord,
          },
          { key: 'audit', label: 'View in audit log', onClick: () => navigate('audit') },
        ],
      },
    ];

    const filteredPatients = workItems.filter((item) => {
      const q = profileSearch.trim().toLowerCase();
      const matchesQuery =
        !q ||
        item.patient.toLowerCase().includes(q) ||
        item.hospitalId.toLowerCase().includes(q);
      const matchesStatus = profileStatusFilter === 'All' || item.status === profileStatusFilter;
      return matchesQuery && matchesStatus;
    });
    const sortedPatients =
      profileSort === 'az'
        ? [...filteredPatients].sort((a, b) => a.patient.localeCompare(b.patient))
        : [...filteredPatients].reverse();

    const tabs: Array<{ key: typeof profileTab; label: string }> = [
      { key: 'overview', label: 'Overview' },
      { key: 'care', label: 'Care Management' },
      { key: 'documents', label: 'Documents' },
      { key: 'scheduling', label: 'Scheduling' },
      { key: 'encounters', label: 'Encounters' },
    ];

    return (
      <div className={`pp-root ${patientsPanelOpen ? '' : 'is-panel-collapsed'}`}>
        <div className="pp-main">
          {/* Header */}
          <section className="pp-header">
            <button
              className="pp-back-btn"
              type="button"
              aria-label="Back to worklist"
              onClick={() => navigate('worklist')}
            >
              <IconChevronLeft className="w-4 h-4" />
            </button>
            <div className="pp-header-row">
              <div className="pp-avatar">{initials}</div>
              <div className="pp-identity">
                <h1>{selectedItem.patient}</h1>
                <p className="pp-subline">
                  {selectedProfile.dob} ({age} y) · {selectedProfile.sex}
                </p>
              </div>
              <div className="pp-stats">
                <div className="pp-stat">
                  <span>MRN</span>
                  <strong>{selectedItem.hospitalId}</strong>
                </div>
                <div className="pp-stat">
                  <span>Follow-up</span>
                  <strong className="pp-stat-dot-row">
                    <span
                      className="pp-dot"
                      style={{ background: statusDotColor[selectedItem.status] }}
                    />
                    {selectedItem.status}
                  </strong>
                </div>
                <div className="pp-stat">
                  <span>Programs</span>
                  <strong title={selectedProfile.programs.join(', ')}>
                    {selectedProfile.programs.length}
                  </strong>
                </div>
              </div>
              <div className="pp-header-nav">
                <button
                  className="pp-icon-btn"
                  type="button"
                  aria-label="Previous patient"
                  onClick={() => cyclePatient(-1)}
                >
                  <IconChevronLeft className="w-4 h-4" />
                </button>
                <button
                  className="pp-icon-btn"
                  type="button"
                  aria-label="Next patient"
                  onClick={() => cyclePatient(1)}
                >
                  <IconChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
            <div className="pp-chips">
              <span className="pp-chip">Goals-of-care cohort</span>
              <span className="pp-chip">{teamChip}</span>
              <span className="pp-chip">ABHA: not linked</span>
            </div>
          </section>

          {/* Tab bar */}
          <div className="pp-tabbar">
            <div className="pp-tablist" role="tablist">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  role="tab"
                  type="button"
                  aria-selected={profileTab === tab.key}
                  className={`pp-tab ${profileTab === tab.key ? 'is-active' : ''}`}
                  onClick={() => switchTab(tab.key)}
                >
                  {tab.label}
                </button>
              ))}
            </div>
            <button
              className="secondary-button"
              type="button"
              disabled={!hasVerifiedRecord}
              onClick={() => beginRetrievalFor(selectedId)}
            >
              <IconLock className="w-3.5 h-3.5" />
              View summary
            </button>
          </div>

          {/* Overview tab */}
          {profileTab === 'overview' && (
            <div className="pp-overview">
              <section className="detail-card pp-timeline-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>Patient Timeline</h2>
                  </div>
                  <span className="pp-year-chip">2026</span>
                  <button
                    className="pp-icon-btn"
                    type="button"
                    aria-label="Open all encounters"
                    onClick={() => switchTab('encounters')}
                  >
                    <IconExpand className="w-4 h-4" />
                  </button>
                </div>
                <ol className="timeline-list pp-timeline-list" ref={profileTimelineRef}>
                  {timelineItems.map((item) => (
                    <li key={`${item.date}-${item.title}`}>
                      <span className="timeline-date">{item.date}</span>
                      <div className="timeline-content">
                        <strong>{item.title}</strong>
                        <p className="pp-clamp-2">{item.body}</p>
                      </div>
                    </li>
                  ))}
                </ol>
                <button className="text-button pp-fullhistory-link" type="button" onClick={() => switchTab('encounters')}>
                  Full history →
                </button>
              </section>

              <div className="pp-overview-grid">
                <section className="detail-card pp-description-card">
                  <div className="card-header">
                    <div className="card-header-titles">
                      <h2>Patient Description</h2>
                    </div>
                  </div>
                  <p>
                    {selectedItem.patient}, {age}, is in the goals-of-care continuity cohort under{' '}
                    {selectedProfile.team}. Recorded diagnosis: {selectedProfile.stage}. Primary
                    contact: {selectedProfile.participant}. Current follow-up: {selectedItem.purpose},
                    owned by {selectedItem.owner}, due {selectedItem.due}.
                  </p>
                  <p className="pp-muted-note">
                    This profile coordinates follow-up and documents a
                    clinician-led conversation. It does not score risk, estimate prognosis or
                    recommend treatment.
                  </p>
                </section>

                <section className="detail-card pp-goals-card">
                  <div className="card-header">
                    <div className="card-header-titles">
                      <h2>Goals & Activities</h2>
                    </div>
                  </div>
                  {isMeera ? (
                    <div className="pp-goals-list">
                      {goalsRows.map((row) => (
                        <div className="pp-goal-row" key={row.key}>
                          <span className="pp-goal-percent">{row.percent}%</span>
                          <div className="pp-goal-body">
                            <span className="pp-goal-label">{row.label}</span>
                            <div
                              className="pp-progress"
                              role="progressbar"
                              aria-valuenow={row.percent}
                              aria-valuemin={0}
                              aria-valuemax={100}
                            >
                              <div className="pp-progress-fill" style={{ width: `${row.percent}%` }} />
                            </div>
                            <span className="pp-goal-caption">{row.caption}</span>
                          </div>
                          <button
                            className="pp-icon-btn"
                            type="button"
                            aria-label={row.label}
                            onClick={row.onClick}
                          >
                            <IconChevronRight className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="compact-empty-state">
                      No guided conversation is loaded for this patient.
                    </div>
                  )}
                </section>

                <section className="detail-card pp-actions-card">
                  <div className="card-header">
                    <div className="card-header-titles">
                      <h2>Actions</h2>
                    </div>
                    <button
                      className="pp-icon-btn"
                      type="button"
                      aria-label="Open care management"
                      onClick={() => switchTab('care')}
                    >
                      <IconExpand className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="pp-actions-list">
                    {actionRows.map((row) => (
                      <button
                        key={row.key}
                        type="button"
                        className="pp-action-row"
                        onClick={row.onClick}
                      >
                        <span className={`pp-checkbox ${row.done ? 'is-done' : ''}`}>
                          {row.done && <IconCheck className="w-3 h-3" />}
                        </span>
                        <span>{row.label}</span>
                        <span className="visually-hidden">{row.done ? '(done)' : '(to do)'}</span>
                      </button>
                    ))}
                  </div>
                </section>

                <section className="detail-card pp-measures-card">
                  <div className="card-header">
                    <div className="card-header-titles">
                      <h2>Continuity Measures & Documentation</h2>
                    </div>
                    <button
                      className="pp-icon-btn"
                      type="button"
                      aria-label="Open documents"
                      onClick={() => switchTab('documents')}
                    >
                      <IconExpand className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="pp-measures-list">
                    {measureRows.map((row) => (
                      <div className="pp-measure-row" key={row.key}>
                        <span className="pp-bullet" />
                        <span className="pp-measure-label">{row.label}</span>
                        <StatusPill status={row.done ? 'Completed' : 'Pending'} />
                        <div className="pp-kebab-wrap">
                          <button
                            className="pp-icon-btn"
                            type="button"
                            aria-haspopup="menu"
                            aria-expanded={openMeasureMenu === row.key}
                            aria-label={`Actions for ${row.label}`}
                            onClick={() =>
                              setOpenMeasureMenu(openMeasureMenu === row.key ? null : row.key)
                            }
                          >
                            <IconKebab className="w-4 h-4" />
                          </button>
                          {openMeasureMenu === row.key && (
                            <div className="pp-menu" role="menu">
                              {row.menu.map((item) => (
                                <button
                                  key={item.key}
                                  role="menuitem"
                                  type="button"
                                  disabled={item.disabled}
                                  onClick={() => {
                                    setOpenMeasureMenu(null);
                                    item.onClick();
                                  }}
                                >
                                  {item.label}
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              </div>
            </div>
          )}

          {/* Care management tab */}
          {profileTab === 'care' && (
            <div className="pp-tab-columns">
              <section className="detail-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>Continuity status & coordination</h2>
                    <span>Clinician-planned operational follow-up and proxy engagement</span>
                  </div>
                  <button
                    className="text-button"
                    type="button"
                    disabled={!isCareCoordinator}
                    onClick={() => navigate('outreach')}
                  >
                    {isCareCoordinator ? 'Record outreach →' : 'Coordinator role required'}
                  </button>
                </div>
                <div className="continuity-grid">
                  <div>
                    <span>Follow-Up Purpose</span>
                    <strong>{selectedItem.purpose}</strong>
                  </div>
                  <div>
                    <span>Current Status</span>
                    <strong>{selectedItem.status}</strong>
                  </div>
                  <div>
                    <span>Designated Surrogate / Contact</span>
                    <strong>{selectedProfile.participant}</strong>
                    <span className="text-xs text-gray-500 mt-1 block">{selectedProfile.phone}</span>
                  </div>
                  <div>
                    <span>Latest Care Team Activity</span>
                    <strong>{selectedItem.lastActivity}</strong>
                  </div>
                </div>
                {latestOutreach && (
                  <div className="record-boundary outreach-record" role="status">
                    <IconCheck className="w-4 h-4 flex-shrink-0" />
                    <span>
                      <strong>Latest outreach · {latestOutreach.outcome}</strong>
                      {latestOutreach.note} Next planned review: {latestOutreach.nextReviewDate} · {latestOutreach.actor}.
                    </span>
                  </div>
                )}
                {selectedItem.coordinationNote && (
                  <div className="record-boundary enrolment-record">
                    <IconInfo className="w-4 h-4 flex-shrink-0" />
                    <span>
                      <strong>Enrolment note · {selectedItem.contactChannel}</strong>
                      {selectedItem.coordinationNote}
                    </span>
                  </div>
                )}
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>Workflow actions</h2>
                    <span>Role-aware tasks</span>
                  </div>
                </div>
                <div className="stacked-actions">
                  <button
                    className="primary-button"
                    type="button"
                    disabled={!isTreatingPhysician}
                    onClick={() => {
                      if (versionPublished || !draftPrepared) startConversation(); else navigate('draft');
                    }}
                  >
                    <IconFileEdit className="w-4 h-4" />
                    {versionPublished || !draftPrepared ? 'New conversation' : 'Continue draft'}
                  </button>
                  <button
                    className="secondary-button"
                    type="button"
                    disabled={!isCareCoordinator}
                    onClick={() => navigate('outreach')}
                  >
                    <IconClipboardList className="w-4 h-4" />
                    {isCareCoordinator ? 'Record coordinator outreach' : 'Coordinator role required'}
                  </button>
                  <button className="secondary-button" type="button" onClick={() => navigate('audit')}>
                    <IconHistory className="w-4 h-4" />
                    View full audit trail
                  </button>
                </div>
              </section>
            </div>
          )}

          {/* Documents tab */}
          {profileTab === 'documents' && (
            <div className="pp-tab-stack">
              <section className="detail-card verified-summary-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>{hasVerifiedRecord ? 'Latest verified conversation summary' : 'No verified summary released'}</h2>
                    <span>{currentRelease ? `${patientSummaryLabel(selectedId)} | ${currentRelease.physician}` : 'No released summary'}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    {recordState.releases.length > 1 && (
                      <button className="text-button" type="button" onClick={() => { setComparisonVersion(null); setDiffOpen(true); }}>
                        <IconColumns className="w-3.5 h-3.5" /> Compare versions
                      </button>
                    )}
                    {hasVerifiedRecord && summaryFieldsAvailable && (
                      <button className="text-button" type="button" onClick={copySummaryToClipboard}>
                        <IconCopy className="w-3.5 h-3.5" /> Copy
                      </button>
                    )}
                    <button
                      className="primary-button"
                      style={{ minHeight: '32px', fontSize: '11px', padding: '0 12px' }}
                      type="button"
                      disabled={!isTreatingPhysician}
                      onClick={() => {
                        if (versionPublished || !draftPrepared) startConversation(); else navigate('draft');
                      }}
                    >
                      {versionPublished || !draftPrepared ? 'New conversation' : 'Continue draft'}
                    </button>
                  </div>
                </div>
                {hasVerifiedRecord ? (
                  summaryFieldsAvailable ? (
                    <div className="summary-grid">
                      <div>
                        <span>1. Patient&apos;s Stated Priorities</span>
                        <p>{releasedFields.priorities}</p>
                      </div>
                      <div>
                        <span>2. People Present & Roles</span>
                        <p>{releasedFields.participants}</p>
                      </div>
                      <div>
                        <span>3. Scope & Topics Discussed</span>
                        <p>{releasedFields.topics}</p>
                      </div>
                      <div>
                        <span>4. Unresolved / Items Not Stated</span>
                        <p>{releasedFields.openQuestions}</p>
                      </div>
                    </div>
                  ) : (
                    <div className="empty-state compact-empty-state">
                      <p>The summary text is not available for this version.</p>
                      <button
                        className="primary-button"
                        type="button"
                        disabled
                      >
                        Source note unavailable
                      </button>
                    </div>
                  )
                ) : (
                  <div className="empty-state compact-empty-state">
                    <p>This patient has no released goals-of-care summary.</p>
                    <button
                      className="primary-button"
                      type="button"
                      disabled
                    >
                      Source note unavailable
                    </button>
                  </div>
                )}
                <div className="record-boundary">
                  <IconInfo className="w-4 h-4 flex-shrink-0" />
                  <span>
                    {hasVerifiedRecord
                      ? 'This verifies what was documented in a structured conversation. It is not a treatment order or legal directive.'
                      : 'No treatment preference should be inferred until a clinician-reviewed conversation summary is released.'}
                  </span>
                </div>
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>Version history</h2>
                    <span>Previous versions stay available</span>
                  </div>
                </div>
                {hasVerifiedRecord ? (
                  <div className="version-list">
                    {[...recordState.releases].reverse().map((release) => (
                      <div className={`version-row ${release.number === currentRelease?.number ? 'current' : ''}`} key={release.number}>
                        <div><strong>Version {release.number}</strong><span>{releaseDate(release)} | {release.physician}</span><span>Acknowledgement: {release.authorisation ?? 'Not loaded'}</span></div>
                        <button className="text-button" type="button" disabled={!currentRole.startsWith('Dr')} onClick={() => beginRetrievalFor(selectedId, release.number)}>View version {release.number}</button>
                        <StatusPill status={release.number === currentRelease?.number ? 'Current' : 'Superseded'} />
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="compact-empty-state">No released versions.</div>
                )}
                <div className="stacked-actions">
                  {recordState.releases.length > 1 && (
                    <button className="secondary-button" type="button" onClick={() => { setComparisonVersion(null); setDiffOpen(true); }}>
                      <IconColumns className="w-4 h-4" />
                      Compare latest versions
                    </button>
                  )}
                  <button className="wide-quiet-button" type="button" onClick={() => navigate('records')}>
                    View all version archives
                  </button>
                </div>
              </section>

              <section className="detail-card identity-sharing-card">
                <div className="card-header">
                  <div className="card-header-titles">
                    <h2>Identity & sharing</h2>
                    <span>Record details</span>
                  </div>
                  <StatusPill status="Not connected" />
                </div>
                <dl className="identity-sharing-list">
                  <div>
                    <dt>ABHA Number</dt>
                    <dd>Not connected</dd>
                  </div>
                  <div>
                    <dt>Sharing basis</dt>
                    <dd>Consent + retrieval purpose</dd>
                  </div>
                  <div>
                    <dt>Data location</dt>
                    <dd>Saved for this session</dd>
                  </div>
                </dl>
                <div className="record-boundary">
                  <IconInfo className="w-4 h-4 flex-shrink-0" />
                  <span>ABHA is optional. This prototype does not connect to ABDM or create an ABHA number.</span>
                </div>
              </section>
            </div>
          )}

          {/* Scheduling tab */}
          {profileTab === 'scheduling' && (
            <section className="detail-card">
              <div className="card-header">
                <div className="card-header-titles">
                  <h2>Follow-up schedule</h2>
                </div>
              </div>
              <dl className="identity-sharing-list">
                <div>
                  <dt>Follow-up owner</dt>
                  <dd>{selectedItem.owner}</dd>
                </div>
                <div>
                  <dt>Next planned touchpoint</dt>
                  <dd>{selectedItem.due}</dd>
                </div>
                <div>
                  <dt>Follow-up purpose</dt>
                  <dd>{selectedItem.purpose}</dd>
                </div>
                <div>
                  <dt>Current status</dt>
                  <dd>{selectedItem.status}</dd>
                </div>
                <div>
                  <dt>Latest activity</dt>
                  <dd>{selectedItem.lastActivity}</dd>
                </div>
                <div>
                  <dt>Next review after outreach</dt>
                  <dd>{latestOutreach?.nextReviewDate ?? 'No outreach recorded yet'}</dd>
                </div>
                <div>
                  <dt>Verified version</dt>
                  <dd>
                    {patientSummaryLabel(selectedId)}
                  </dd>
                </div>
                <div>
                  <dt>Who can view</dt>
                  <dd>Authorised care team only</dd>
                </div>
              </dl>
              <div className="stacked-actions">
                <button
                  className="text-button"
                  type="button"
                  disabled={!isCareCoordinator}
                  onClick={() => navigate('outreach')}
                >
                  {isCareCoordinator ? 'Record outreach →' : 'Coordinator role required'}
                </button>
              </div>
            </section>
          )}

          {/* Encounters tab */}
          {profileTab === 'encounters' && (
            <div className="pp-tab-stack">
              <PatientHistory
                patientName={selectedItem.patient}
                events={buildHistoryEvents(selectedItem.hospitalId)}
                currentPerson={currentRole.split(' · ')[0]}
              />
            </div>
          )}
        </div>

        {/* Patients panel */}
        <aside className="pp-patients">
          <div className="pp-patients-body">
            <div className="pp-patients-heading-row">
              <h2>Patients</h2>
              <button
                className="pp-icon-btn pp-panel-handle"
                type="button"
                aria-expanded={patientsPanelOpen}
                aria-label={patientsPanelOpen ? 'Hide patient list' : 'Show patient list'}
                onClick={() => setPatientsPanelOpen((open) => !open)}
              >
                {patientsPanelOpen ? (
                  <IconChevronRight className="w-4 h-4" />
                ) : (
                  <IconChevronLeft className="w-4 h-4" />
                )}
              </button>
            </div>
            <div className="pp-patients-content">
              <div className="pp-search-row">
                <div className="pp-search-input">
                  <IconSearch className="w-4 h-4" />
                  <input
                    type="text"
                    placeholder="Search patients"
                    value={profileSearch}
                    onChange={(event) => setProfileSearch(event.target.value)}
                  />
                </div>
                <button
                  className="pp-icon-btn"
                  type="button"
                  aria-label="Filter patients"
                  aria-expanded={profileFilterOpen}
                  onClick={() => setProfileFilterOpen((open) => !open)}
                >
                  <IconFilter className="w-4 h-4" />
                </button>
              </div>
              {profileFilterOpen && (
                <div className="pp-filter-chips">
                  {(['All', 'Due today', 'Overdue', 'Awaiting review', 'Completed'] as const).map(
                    (statusOption) => (
                      <button
                        key={statusOption}
                        type="button"
                        className={`pp-filter-chip ${profileStatusFilter === statusOption ? 'is-active' : ''}`}
                        onClick={() => setProfileStatusFilter(statusOption)}
                      >
                        {statusOption}
                      </button>
                    ),
                  )}
                </div>
              )}
              <div className="pp-sort-row">
                <button
                  className="text-button"
                  type="button"
                  onClick={() => setProfileSort((sort) => (sort === 'recent' ? 'az' : 'recent'))}
                >
                  {profileSort === 'recent' ? 'Recent ▾' : 'A–Z ▾'}
                </button>
                <button
                  className="pp-icon-btn"
                  type="button"
                  disabled={!isTreatingPhysician}
                  title={isTreatingPhysician ? 'Enrol patient' : 'Treating physician role required'}
                  onClick={() => setEnrolOpen(true)}
                >
                  <IconPlus className="w-4 h-4" />
                </button>
              </div>
              <div className="pp-patient-list">
                {sortedPatients.length === 0 ? (
                  <div className="compact-empty-state">No patients match.</div>
                ) : (
                  sortedPatients.map((item) => {
                    const profile = patientProfiles[item.hospitalId];
                    const itemInitials = item.patient
                      .split(' ')
                      .map((part) => part[0])
                      .join('')
                      .slice(0, 2)
                      .toUpperCase();
                    const isSelected = item.hospitalId === selectedId;
                    return (
                      <button
                        key={item.hospitalId}
                        type="button"
                        className={`pp-patient-card ${isSelected ? 'is-selected' : ''}`}
                        aria-current={isSelected ? 'true' : undefined}
                        onClick={() => selectPatient(item.hospitalId)}
                      >
                        <span className="pp-patient-avatar">
                          {itemInitials}
                          <span
                            className="pp-dot pp-patient-dot"
                            style={{ background: statusDotColor[item.status] }}
                          />
                        </span>
                        <span className="pp-patient-info">
                          <span className="pp-patient-name">{item.patient}</span>
                          <span className="pp-patient-meta">
                            {profile?.dob ?? 'DOB not recorded'}, {item.hospitalId}
                          </span>
                        </span>
                      </button>
                    );
                  })
                )}
              </div>
            </div>
          </div>
        </aside>
      </div>
    );
  }

  function renderOutreach() {
    const outcomes = [
      'Contacted & confirmed discussion',
      'Family requested more time',
      'Patient unavailable',
      'Discussion completed',
      'Escalate to attending clinician',
    ];
    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('patient')}>
          ← Back
        </button>
        {renderPageHeading('Care Coordinator Workflow', `Follow-up outreach · ${selectedItem.patient}`)}

        <div className="form-page-grid outreach-page-grid">
          <section className="detail-card task-context-card">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Task context & instructions</h2>
                <span>Coordinator guidance</span>
              </div>
            </div>
            <dl className="definition-list">
              <div><dt>Follow-up Purpose</dt><dd>{selectedItem.purpose}</dd></div>
              <div><dt>Target Due Date</dt><dd>{selectedItem.due}</dd></div>
              <div><dt>Current Owner</dt><dd>{selectedItem.owner}</dd></div>
              <div><dt>Primary Contact</dt><dd>{selectedProfile.participant}</dd></div>
              <div><dt>Contact handling</dt><dd>{selectedProfile.phone}</dd></div>
              <div><dt>Preferred Channel</dt><dd>{selectedItem.contactChannel ?? 'Telephone call'}</dd></div>
              {selectedItem.coordinationNote && (
                <div><dt>Enrolment note</dt><dd>{selectedItem.coordinationNote}</dd></div>
              )}
            </dl>
            <div className="record-boundary">
              <IconInfo className="w-4 h-4 flex-shrink-0" />
              <span>
                The coordinator handles operational logistics only. Clinical questions or medical decisions must be directed back to the named clinician.
              </span>
            </div>
          </section>

          <form className="detail-card workflow-form" onSubmit={saveOutreach} noValidate>
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Record outreach outcome</h2>
              </div>
            </div>

            {outreachError && <p className="outreach-error" role="alert" id="outreach-error">{outreachError}</p>}

            <fieldset className="option-fieldset" aria-describedby={outreachError ? 'outreach-error' : undefined}>
              <legend>Outcome category</legend>
              <div className="option-grid">
                {outcomes.map((outcome, index) => (
                  <button
                    key={outcome}
                    type="button"
                    data-outreach-field={index === 0 ? 'outcome' : undefined}
                    className={outreachOutcome === outcome ? 'option-button active' : 'option-button'}
                    aria-pressed={outreachOutcome === outcome}
                    onClick={() => { setOutreachOutcome(outcome); setOutreachError(''); }}
                  >
                    {outcome}
                  </button>
                ))}
              </div>
            </fieldset>

            <label className="form-field">
              <span>Detailed outreach note</span>
              <textarea
                rows={4}
                data-outreach-field="note"
                value={outreachNote}
                onChange={(event) => { setOutreachNote(event.target.value); setOutreachError(''); }}
                placeholder="Enter details of the phone or in-person outreach..."
                required
              />
            </label>

            <label className="form-field">
              <span>Next planned review date</span>
              <input
                type="date"
                data-outreach-field="date"
                value={outreachDate}
                onChange={(event) => { setOutreachDate(event.currentTarget.value); setOutreachError(''); }}
                onInput={(event) => setOutreachDate(event.currentTarget.value)}
                required
              />
            </label>

            <div className="form-actions">
              <button className="secondary-button" type="button" onClick={() => navigate('patient')}>
                Cancel
              </button>
              <button className="primary-button" type="submit">
                <IconCheck className="w-4 h-4" />
                Save outreach record
              </button>
            </div>
          </form>
        </div>
      </>
    );
  }

  function renderDraft() {
    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('patient')}>Back</button>
        {renderPageHeading(
          `${selectedItem.patient} | Version ${draftVersion}`,
          versionPublished ? 'Released summary' : 'Conversation draft',
          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setTepModalOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconHeartPulse className="w-4 h-4 text-rose-600" />
              <span>TEP Matrix</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setSyringeDriverOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconZap className="w-4 h-4 text-purple-600" />
              <span>Syringe Driver</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setMapOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconMapPin className="w-4 h-4 text-emerald-600" />
              <span>GIS Map</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setEsasOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconSparkles className="w-4 h-4 text-amber-600" />
              <span>ESAS Symptoms</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setDeprescribingOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconHospital className="w-4 h-4 text-rose-600" />
              <span>Deprescribing</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setPrognosisOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <IconHeartPulse className="w-4 h-4 text-purple-600" />
              <span>PPI Prognosis</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setEmergencyTriageOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fee2e2',
                borderColor: '#fca5a5',
                color: '#991b1b',
                fontWeight: 700,
              }}
            >
              <span>🚨 STAT Triage</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setCaregiverBurdenOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#f0fdfa',
                borderColor: '#99f6e4',
                color: '#0f766e',
                fontWeight: 700,
              }}
            >
              <IconUsers className="w-4 h-4 text-teal-600" />
              <span>Caregiver ZBI</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setSedationCrisisOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#faf5ff',
                borderColor: '#e9d5ff',
                color: '#6b21a8',
                fontWeight: 700,
              }}
            >
              <IconZap className="w-4 h-4 text-purple-700" />
              <span>Sedation Protocol</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setTrajectoryTimelineOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#eef2ff',
                borderColor: '#c7d2fe',
                color: '#3730a3',
                fontWeight: 700,
              }}
            >
              <IconHistory className="w-4 h-4 text-indigo-700" />
              <span>Care Trajectory</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setDrugBoxOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fff1f2',
                borderColor: '#fecdd3',
                color: '#be123c',
                fontWeight: 700,
              }}
            >
              <span>📦 Home JIC Box</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setWoundCareOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fff7ed',
                borderColor: '#fed7aa',
                color: '#c2410c',
                fontWeight: 700,
              }}
            >
              <span>🩹 Malignant Wound</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setBoneSinsOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#eef2ff',
                borderColor: '#c7d2fe',
                color: '#4338ca',
                fontWeight: 700,
              }}
            >
              <span>🦴 Bone Met SINS</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setCachexiaAscitesOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#f0fdfa',
                borderColor: '#99f6e4',
                color: '#0f766e',
                fontWeight: 700,
              }}
            >
              <span>💧 Cachexia & Ascites</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setNeuropathicBlocksOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#f5f3ff',
                borderColor: '#ddd6fe',
                color: '#6d28d9',
                fontWeight: 700,
              }}
            >
              <span>⚡ Neuropathic Blocks</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setBreathlessnessCrisisOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#f0f9ff',
                borderColor: '#bae6fd',
                color: '#0369a1',
                fontWeight: 700,
              }}
            >
              <span>🫁 Dyspnea Crisis</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setDeliriumOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fffbeb',
                borderColor: '#fde68a',
                color: '#b45309',
                fontWeight: 700,
              }}
            >
              <span>🧠 Delirium Care</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setPediatricPalliativeOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fff1f2',
                borderColor: '#fecdd3',
                color: '#be123c',
                fontWeight: 700,
              }}
            >
              <span>🧸 Pediatric Palliative</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setBowelObstructionOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#f0fdf4',
                borderColor: '#bbf7d0',
                color: '#15803d',
                fontWeight: 700,
              }}
            >
              <span>🧪 Bowel Obstruction</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setRenalHepaticOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#eef2ff',
                borderColor: '#c7d2fe',
                color: '#4338ca',
                fontWeight: 700,
              }}
            >
              <span>🫘 Renal & Hepatic</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setRadiotherapyOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fffbeb',
                borderColor: '#fef3c7',
                color: '#92400e',
                fontWeight: 700,
              }}
            >
              <span>⚛️ Radiotherapy</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setCordCompressionOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fef2f2',
                borderColor: '#fecaca',
                color: '#b91c1c',
                fontWeight: 700,
              }}
            >
              <span>⚡ Cord Compression</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setHypercalcemiaOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fff7ed',
                borderColor: '#fed7aa',
                color: '#c2410c',
                fontWeight: 700,
              }}
            >
              <span>🧪 Hypercalcemia</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setSvcoOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#fef2f2',
                borderColor: '#fca5a5',
                color: '#991b1b',
                fontWeight: 700,
              }}
            >
              <span>🫀 SVCO Crisis</span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setBleedCrisisOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                background: '#450a0a',
                borderColor: '#450a0a',
                color: '#fecaca',
                fontWeight: 700,
              }}
            >
              <span>🩸 Bleeding</span>
            </button>
            {versionPublished && <button className="secondary-button" type="button" onClick={startConversation}>New conversation</button>}
            <button className="primary-button" type="button" onClick={() => navigate('verify')}>{versionPublished ? 'Review checklist' : 'Review summary'}</button>
          </div>,
        )}
        <div className="draft-layout">
          <section className="detail-card source-note-panel">
            {isTreatingPhysician && !versionPublished && (
              <VoiceDictationBar
                fieldLabel="Conversation notes"
                onTranscript={(text, append) => {
                  updateSelectedRecord((current) =>
                    reviseSummary(
                      { ...current, draftPrepared: true },
                      {
                        draftSource: append
                          ? current.draftSource
                            ? `${current.draftSource.trim()}\n\n${text}`
                            : text
                          : text,
                      }
                    )
                  );
                }}
              />
            )}
            <label className="form-field" htmlFor="conversation-source">
              <span>Conversation notes</span>
              <textarea id="conversation-source" rows={14} value={recordState.draftSource}
                readOnly={!isTreatingPhysician || versionPublished}
                onChange={(event) => updateSelectedRecord((current) => reviseSummary({ ...current, draftPrepared: true }, { draftSource: event.target.value }))} />
            </label>
            {!draftPrepared && <button className="primary-button" type="button" onClick={prepareDraft}>Open structured draft</button>}
            <p className="record-boundary">Use a fictional conversation. Link each stated field to an exact excerpt; nothing is inferred.</p>
            {highlightedField && recordState.draftExcerpts[highlightedField as DraftFieldKey] && (
              <blockquote className="source-text-interactive"><mark>{recordState.draftExcerpts[highlightedField as DraftFieldKey]}</mark></blockquote>
            )}
          </section>
          <section className="detail-card structured-draft-panel">
            <div className="card-header"><h2>Summary</h2><StatusPill status={versionPublished ? 'Released' : 'Draft'} /></div>
            <div className="draft-fields">
              {fieldConfig.map((field) => (
                <article className={`draft-field status-${draftStatuses[field.key]}`} key={field.key}>
                  <div className="draft-field-heading">
                    <label htmlFor={`field-${field.key}`}>{field.label}</label>
                    <span className={`field-status-tag ${draftStatuses[field.key]}`}>{draftStatuses[field.key] === 'ready' ? 'Source linked' : draftStatuses[field.key] === 'not-stated' ? 'Not stated' : 'Needs review'}</span>
                  </div>
                  <textarea id={`field-${field.key}`} rows={3} value={draftFields[field.key]}
                    readOnly={!isTreatingPhysician || versionPublished}
                    onChange={(event) => setDraftFields((fields) => ({ ...fields, [field.key]: event.target.value }))} />
                  <label className="form-field" htmlFor={`excerpt-${field.key}`}>
                    <span>Exact source excerpt</span>
                    <textarea id={`excerpt-${field.key}`} rows={2} value={recordState.draftExcerpts[field.key]}
                      readOnly={!isTreatingPhysician || versionPublished}
                      onFocus={() => setHighlightedField(field.key)} onBlur={() => setHighlightedField(null)}
                      onChange={(event) => updateSelectedRecord((current) => reviseSummary({ ...current, draftPrepared: true }, { draftExcerpts: { ...current.draftExcerpts, [field.key]: event.target.value } }))} />
                  </label>
                  <div className="field-actions">
                    <button type="button" disabled={!isTreatingPhysician || versionPublished} onClick={() => setDraftFieldStatus(field.key, 'ready')}>Confirm source link</button>
                    <button type="button" disabled={!isTreatingPhysician || versionPublished} onClick={() => setDraftFieldStatus(field.key, 'not-stated')}>Mark not stated</button>
                    <button type="button" disabled={!isTreatingPhysician || versionPublished} onClick={() => setDraftFieldStatus(field.key, 'clarify')}>Needs clarification</button>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>
      </>
    );
  }

  function renderVerify() {
    const checkItems: Array<{ key: keyof typeof verificationChecks; label: string }> = [
      { key: 'source', label: 'All populated fields match the entered source conversation exactly.' },
      { key: 'ambiguity', label: 'Unclear or incomplete items are explicitly marked as not stated or needing clarification.' },
      { key: 'inference', label: 'No clinical prognostic estimates or unstated preferences have been inferred.' },
      { key: 'reviewDate', label: 'The agreed follow-up date and participating surrogates are accurate.' },
    ];

    // Spell out every unmet release condition rather than leaving the clinician to
    // infer why the publish action is disabled. This mirrors verificationReady.
    const outstandingChecks = 4 - verificationPassedCount;
    const releaseBlockers = [
      !currentRole.startsWith('Dr Sujay') &&
        'Only the treating physician can publish a version.',
      versionPublished && `Version ${draftVersion} is already released.`,
      !draftReleaseReady &&
        (draftFieldBlockers.length > 0
          ? `Resolve ${draftFieldBlockers.map(({ label }) => label).join(', ')} in the structured draft.`
          : 'Prepare the structured draft before publishing.'),
      outstandingChecks > 0 &&
        `Complete ${outstandingChecks} remaining verification ${outstandingChecks === 1 ? 'check' : 'checks'}.`,
      !acknowledgementComplete &&
        'Record a patient or surrogate authorisation other than pending or declined.',
      !attested && 'Add the physician attestation.',
    ].filter((blocker): blocker is string => typeof blocker === 'string');

    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('draft')}>
          ← Back to structured draft
        </button>
        {renderPageHeading('Review', 'Review summary')}

        <div className="verification-layout">
          <section className="detail-card verification-card">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Clinical verification checklist</h2>
                <span>Mandatory safety validation ({verificationPassedCount}/4 checks passed)</span>
              </div>
            </div>

            {!versionPublished && isTreatingPhysician && (
              <div style={{ padding: '12px 18px 0' }}>
                <button
                  type="button"
                  className="secondary-button"
                  style={{
                    width: '100%',
                    background: '#e6f5ec',
                    borderColor: '#b4e0c6',
                    color: '#146b3e',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    padding: '10px 14px',
                    borderRadius: '10px',
                  }}
                  onClick={() => {
                    setVerificationChecks({
                      source: true,
                      ambiguity: true,
                      inference: true,
                      reviewDate: true,
                    });
                    setAuthorisation('Authorised by Patient & Surrogate');
                    setAttested(true);
                    notify('All 4 safety checks verified and physician attestation signed');
                  }}
                >
                  <IconZap className="w-4 h-4 text-amber-600" />
                  <span>⚡ 1-Click Fast Complete All 4 Checks &amp; Attestation</span>
                </button>
              </div>
            )}

            <div className="check-list">
              {checkItems.map((item) => (
                <label className="check-row" key={item.key}>
                  <input
                    type="checkbox"
                    checked={verificationChecks[item.key]}
                    disabled={!isTreatingPhysician || versionPublished}
                    onChange={(event) =>
                      setVerificationChecks((checks) => ({ ...checks, [item.key]: event.target.checked }))
                    }
                  />
                  <span>{item.label}</span>
                </label>
              ))}
            </div>

            <div
              className={`record-boundary release-readiness ${draftReleaseReady ? 'ready' : 'blocked'}`}
              role="status"
              aria-live="polite"
            >
              {draftReleaseReady ? (
                <IconCheck className="w-4 h-4 flex-shrink-0" />
              ) : (
                <IconInfo className="w-4 h-4 flex-shrink-0" />
              )}
              <span>
                <strong>
                  {draftReleaseReady ? 'Draft field gate passed.' : 'Draft field gate blocked.'}
                </strong>{' '}
                {draftReleaseReady
                  ? 'Every structured field has an explicit source-linked or not-stated status.'
                  : `Resolve ${draftFieldBlockers.map(({ label }) => label).join(', ')} in the structured draft before publishing.`}
              </span>
            </div>

            <label className="form-field">
              <span>Patient or Surrogate Authorisation</span>
              <select
                value={authorisation}
                disabled={!isTreatingPhysician || versionPublished}
                onChange={(event) => setAuthorisation(event.target.value)}
              >
                <option>Authorised by Patient & Surrogate</option>
                <option>Authorised by Designated Healthcare Proxy</option>
                <option>Authorised verbally (witnessed by care team)</option>
                <option>Pending patient review</option>
                <option>Declined</option>
              </select>
            </label>

            <div className="record-boundary" role="status">
              {consentFor(selectedId) ? (
                <span>
                  Patient consent recorded in the patient portal: {consentFor(selectedId)?.typedName} ·{' '}
                  {consentFor(selectedId)?.declaration === 'self'
                    ? 'made these choices themselves'
                    : 'delegated to their decision-maker'}{' '}
                  · {consentFor(selectedId)?.signedAt} · {consentFor(selectedId)?.versionLabel}. Typed name.
                </span>
              ) : (
                <span>No patient-portal consent recorded for this patient yet.</span>
              )}
            </div>

            <label className="attestation-row">
              <input
                type="checkbox"
                checked={attested}
                disabled={!isTreatingPhysician || versionPublished}
                onChange={(event) => setAttested(event.target.checked)}
              />
              <span>
                <strong>Physician Attestation:</strong> I have reviewed this summary against the source consultation note. It accurately reflects the discussion and authorizations; it is not a treatment order or legally binding directive.
              </span>
            </label>

            {releaseBlockers.length > 0 && (
              <div className="release-blockers" role="status" aria-live="polite">
                <p className="release-blockers-head">
                  <IconLock className="w-3.5 h-3.5" />
                  <span>
                    Release is held until {releaseBlockers.length}{' '}
                    {releaseBlockers.length === 1 ? 'requirement is' : 'requirements are'} met
                  </span>
                </p>
                <ul>
                  {releaseBlockers.map((blocker) => (
                    <li key={blocker}>{blocker}</li>
                  ))}
                </ul>
              </div>
            )}

            <div className="form-actions">
              <button
                className="secondary-button"
                type="button"
                disabled={!isTreatingPhysician || versionPublished}
                onClick={() => notify('Draft saved without publishing.')}
              >
                Save as draft
              </button>
              <button
                className="primary-button"
                type="button"
                disabled={!verificationReady}
                onClick={publishVersion}
              >
                <IconShieldCheck className="w-4 h-4" />
                {versionPublished ? `Version ${draftVersion} published` : `Save Version ${draftVersion}`}
              </button>
            </div>
          </section>

          <aside className="detail-card verification-preview">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Append-only release preview</h2>
                <span>{selectedItem.patient} · Version {draftVersion}</span>
              </div>
            </div>
            {fieldConfig.map((field) => (
              <div className="preview-field" key={field.key}>
                <span>{field.label}</span>
                <p>{draftFields[field.key]}</p>
              </div>
            ))}
            <div className="record-boundary">
              <IconInfo className="w-4 h-4 flex-shrink-0" />
              <span>
                Publication preserves every earlier release. Patient and family views show the latest released summary.
              </span>
            </div>
          </aside>
        </div>
      </>
    );
  }

  function renderRetrieve() {
    const release = requestedRelease;
    const sourceContextAvailable = Boolean(release?.source);
    const releasedFields = release?.fields;
    const isBreakGlassAccess = retrievalReason === 'Emergency hand-off retrieval (Break-glass)';
    const breakGlassRequirementsMet =
      !isBreakGlassAccess ||
      (currentRole.startsWith('Dr Isha') && careRelationship === 'Emergency receiving physician');
    const retrievalGateReady =
      Boolean(release) &&
      currentRole.startsWith('Dr') &&
      Boolean(retrievalReason) &&
      Boolean(careRelationship) &&
      breakGlassRequirementsMet &&
      retrievalAcknowledged;
    const retrievalGateBlocker = !release
      ? 'No released summary is available for this patient.'
      : !currentRole.startsWith('Dr')
      ? 'Choose a doctor to view this note.'
      : !retrievalReason || !careRelationship
        ? 'Choose why you need this note and your role in the patient’s care.'
        : !breakGlassRequirementsMet
          ? 'Only the receiving doctor can open emergency notes.'
          : !retrievalAcknowledged
            ? 'Confirm below to continue.'
            : 'Ready to open. Your reason will be saved in History.';
    const selectedInitials = selectedItem.patient
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('patient')}>
          ← Back
        </button>
        {renderPageHeading('Notes', 'Open summary')}

        {!retrievalUnlocked || !release || recordState.unlockedVersion !== release.number || sessionType !== 'care-team' || !currentRole.startsWith('Dr') ? (
          <div className="retrieval-gate">
            <section className="detail-card retrieval-context">
              <div className="card-header">
                <div className="card-header-titles">
                  <h2>Record requested</h2>
                  <span>Patient</span>
                </div>
              </div>
              <div className="patient-lookup-result">
                <div className="profile-initials">{selectedInitials}</div>
                <div>
                  <strong>{selectedItem.patient}</strong>
                  <span>{selectedItem.hospitalId} · Age {selectedProfile.age}</span>
                </div>
              </div>
              <dl className="definition-list">
                <div><dt>Requested version</dt><dd>{release ? `Version ${release.number} · ${releaseDate(release)}` : 'No released version'}</dd></div>
                <div><dt>Record Category</dt><dd>Verified Goals-of-Care Summary</dd></div>
                <div><dt>Who can view</dt><dd>Doctors on the care team</dd></div>
                <div><dt>History</dt><dd>Reasons for opening notes</dd></div>
              </dl>
              {recordState.releases.length > 1 && <label className="form-field">
                <span>Summary version</span>
                <select value={release?.number ?? ''} onChange={(event) => updateSelectedRecord((current) => ({ ...current, retrievalVersion: Number(event.target.value), retrievalAcknowledged: false, retrievalUnlocked: false, unlockedVersion: null }))}>
                  {[...recordState.releases].reverse().map((version) => <option key={version.number} value={version.number}>Version {version.number}{version.number === currentRelease?.number ? ' (latest)' : ' (historical)'}</option>)}
                </select>
              </label>}
            </section>

            <form className="detail-card workflow-form" onSubmit={openRetrievedRecord}>
              <div className="card-header">
                <div className="card-header-titles">
                  <h2>Before you open this note</h2>
                  <span>Your reason is saved in History</span>
                </div>
              </div>

              <div style={{ marginBottom: '14px' }}>
                <button
                  type="button"
                  className="secondary-button"
                  style={{
                    width: '100%',
                    background: '#fdeaea',
                    borderColor: '#f5c3c1',
                    color: '#99231f',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    padding: '10px 14px',
                    borderRadius: '10px',
                  }}
                  onClick={() => {
                    setCurrentRole('Dr Isha Menon · Emergency physician');
                    const targetVersion = release?.number ?? currentRelease?.number ?? 1;
                    updateSelectedRecord((current) => ({
                      ...current,
                      retrievalReason: 'Emergency hand-off retrieval (Break-glass)',
                      careRelationship: 'Emergency receiving physician',
                      retrievalAcknowledged: true,
                      retrievalUnlocked: true,
                      unlockedVersion: targetVersion,
                    }));
                    setAuditEvents((events) => [
                      {
                        id: `AUDIT-${Date.now().toString().slice(-4)}`,
                        time: 'Just now',
                        timestamp: 'Just now',
                        actor: 'Dr Isha Menon · Emergency physician',
                        event: 'Retrieved verified summary (Break-glass)',
                        record: `${selectedItem.patient} · Version ${targetVersion}`,
                        badge: 'ACCESS',
                        detail: 'Emergency hand-off retrieval (Break-glass) · 1-Click Protocol',
                        securityImpact: 'Critical emergency retrieval logged',
                      },
                      ...events,
                    ]);
                    notify('Break-glass emergency protocol activated — verified summary unlocked');
                  }}
                >
                  <IconZap className="w-4 h-4 text-amber-500" />
                  <span>⚡ 1-Click Break-Glass Emergency Retrieval</span>
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{
                    width: '100%',
                    marginTop: '8px',
                    background: '#fef2f2',
                    borderColor: '#fca5a5',
                    color: '#991b1b',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    padding: '8px 12px',
                    borderRadius: '8px',
                  }}
                  onClick={() => setEmergencyTriageOpen(true)}
                >
                  <span>🚨 Oncologic Emergency Triage & STAT Orders</span>
                </button>
              </div>

              <label className="form-field">
                <span>Signed-in Clinician</span>
                <input value={currentRole} readOnly />
              </label>
              <label className="form-field">
                <span>Clinical Reason for Retrieval</span>
                <select value={retrievalReason} onChange={(event) => setRetrievalReason(event.target.value)}>
                  <option>Planned consultation / Outpatient review</option>
                  <option>Active inpatient treatment team review</option>
                  <option>Transfer of care / Clinical hand-off</option>
                  <option>Patient or surrogate requested copy</option>
                  <option>Emergency hand-off retrieval (Break-glass)</option>
                </select>
              </label>
              <label className="form-field">
                <span>Care Relationship Basis</span>
                <select value={careRelationship} onChange={(event) => setCareRelationship(event.target.value)}>
                  <option>Current primary oncology treating team</option>
                  <option>Consulting palliative specialist</option>
                  <option>Emergency receiving physician</option>
                  <option>Inpatient nursing / care coordinator</option>
                </select>
              </label>
              <div
                className={`record-boundary retrieval-access-status ${isBreakGlassAccess ? 'break-glass' : 'routine'} ${retrievalGateReady ? 'ready' : 'blocked'}`}
                id="retrieval-gate-status"
                role="status"
                aria-live="polite"
                aria-atomic="true"
              >
                <IconInfo className="w-4 h-4 flex-shrink-0" />
                <span>
                  <strong>{isBreakGlassAccess ? 'Emergency care.' : 'Patient care.'}</strong>{' '}
                  {retrievalGateBlocker}
                </span>
              </div>
              <label className="attestation-row retrieval-attestation">
                <input
                  type="checkbox"
                  checked={retrievalAcknowledged}
                  onChange={(event) => setRetrievalAcknowledged(event.target.checked)}
                  disabled={!currentRole.startsWith('Dr')}
                />
                <span>
                  <strong>Confirm:</strong> I am opening this summary for the selected care relationship. I will re-confirm current clinical status with the patient or surrogate and care team as appropriate; this summary is not a treatment order or legal directive.
                </span>
              </label>
              <button
                className="primary-button full-width"
                type="submit"
                disabled={!retrievalGateReady}
                aria-describedby="retrieval-gate-status"
              >
                <IconLock className="w-4 h-4" />
                Open summary
              </button>
            </form>
          </div>
        ) : (
          <section className="retrieved-record">
            <div className="retrieval-warning">
              <IconInfo className="w-4 h-4 flex-shrink-0 text-amber-700" />
              <span>
                <strong>Confidential Clinical Record:</strong> This document summarizes a documented goals-of-care conversation. Re-confirm clinical status with the patient and surrogate before making acute clinical orders.
              </span>
            </div>
            <EdQuickView record={ectprQuickViews[selectedId] ?? null} audience="clinician" />
            <p className="print-only print-notice">
              This page is not a treatment order, resuscitation order, or legal
              directive, and it does not replace confirming the patient&rsquo;s current clinical
              status and wishes with the patient, surrogate and treating team.
            </p>
            <div className="retrieved-record-header">
              <div>
                <p className="eyebrow">
                  <IconShieldCheck className="w-3.5 h-3.5 text-emerald-700" /> Verified Goals-of-Care Summary
                </p>
                <h2 ref={retrievedRecordHeadingRef} tabIndex={-1}>{selectedItem.patient}</h2>
                <span>
                  {selectedItem.hospitalId} · Version {release.number} · Attested by {release.physician}
                </span>
              </div>
              <div className="flex items-center gap-3">
                <StatusPill status="Viewed" />
                {releasedFields && (
                  <button className="secondary-button" type="button" onClick={copySummaryToClipboard}>
                    <IconCopy className="w-3.5 h-3.5" /> Copy
                  </button>
                )}
                <button className="secondary-button" type="button" onClick={() => window.print()}>
                  Print
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#e6f5ec',
                    borderColor: '#b4e0c6',
                    color: '#146b3e',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setFhirExportOpen(true)}
                  title="Export to Ayushman Bharat Digital Mission (ABDM) FHIR R4 Bundle"
                >
                  <IconFileText className="w-3.5 h-3.5" />
                  <span>ABDM FHIR Export</span>
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#fff1f2',
                    borderColor: '#fecdd3',
                    color: '#be123c',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setTepModalOpen(true)}
                  title="View Treatment Escalation Plan (TEP) Organ Ceilings"
                >
                  <IconHeartPulse className="w-3.5 h-3.5" />
                  <span>TEP Matrix</span>
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#f0fdf4',
                    borderColor: '#bbf7d0',
                    color: '#15803d',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setMapOpen(true)}
                  title="Open MapLibre GL Community GIS & Transit Dispatch Map"
                >
                  <IconMapPin className="w-3.5 h-3.5" />
                  <span>GIS Map</span>
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#faf5ff',
                    borderColor: '#e9d5ff',
                    color: '#7e22ce',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setSyringeDriverOpen(true)}
                  title="Continuous Subcutaneous Syringe Driver & Opioid Converter"
                >
                  <IconZap className="w-3.5 h-3.5" />
                  <span>Syringe Driver</span>
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#fffbeb',
                    borderColor: '#fde68a',
                    color: '#b45309',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setEsasOpen(true)}
                  title="Edmonton Symptom Assessment System (ESAS-r) Radar & Tracker"
                >
                  <IconSparkles className="w-3.5 h-3.5" />
                  <span>ESAS Symptoms</span>
                </button>
                <button
                  className="secondary-button"
                  style={{
                    background: '#fff1f2',
                    borderColor: '#fecdd3',
                    color: '#be123c',
                    fontWeight: 650,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                  type="button"
                  onClick={() => setDeprescribingOpen(true)}
                  title="Palliative Polypharmacy Deprescribing & Drug Interaction Matrix"
                >
                  <IconHospital className="w-3.5 h-3.5" />
                  <span>Deprescribing</span>
                </button>
              </div>
            </div>
            {release.number !== currentRelease?.number && <p className="record-boundary">Historical Version {release.number}. The latest released summary is Version {currentRelease?.number}.</p>}
            {releasedFields ? (
              <div className="summary-grid retrieval-summary-grid">
                {fieldConfig.map((field) => (
                  <div key={field.key}>
                    <span>{field.label}</span>
                    <p>{releasedFields[field.key]}</p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="record-boundary retrieval-summary-unavailable">
                <IconInfo className="w-4 h-4 flex-shrink-0" />
                <span>Version metadata is available, but source-linked field content is not loaded for this patient. No field value is inferred.</span>
              </div>
            )}
            <section className="retrieval-source-context" aria-labelledby="retrieval-source-title">
              <div className="card-header">
                <div className="card-header-titles">
                  <h3 id="retrieval-source-title">Source and context</h3>
                  <span>Conversation notes</span>
                </div>
              </div>
              <dl className="definition-list retrieval-context-list">
                <div><dt>Originating team</dt><dd>{selectedProfile.team}</dd></div>
                <div><dt>Source record</dt><dd>Doctor’s conversation notes</dd></div>
                <div><dt>Released</dt><dd>{releaseDate(release)}</dd></div>
                <div><dt>Unresolved / not discussed</dt><dd>{releasedFields?.openQuestions ?? 'Not available'}</dd></div>
              </dl>
              {sourceContextAvailable ? (
                <div className="retrieval-excerpt-list">
                  <span className="document-kicker">Source excerpts</span>
                  <blockquote>{release.source}</blockquote>
                </div>
              ) : (
                <div className="record-boundary">
                  <IconInfo className="w-4 h-4 flex-shrink-0" />
                  <span>Source excerpts are not loaded for this patient. No preference is inferred from missing source text.</span>
                </div>
              )}
            </section>
            <div className="retrieved-footer">
              <div>
                <span>Verified By</span>
                <strong>{release.physician} · {releaseDate(release)}</strong>
              </div>
              <div>
                <span>Authorisation Basis</span>
                <strong>
                  {release.authorisation ?? 'Not loaded'}
                </strong>
              </div>
              <div>
                <span>Patient portal acknowledgement</span>
                <strong>{consentById[`${selectedId}:${release.number}`]?.typedName ?? 'Not recorded for this version'}</strong>
              </div>
              <div>
                <span>Retrieval Justification</span>
                <strong>{retrievalReason}</strong>
              </div>
            </div>
          </section>
        )}
      </>
    );
  }

  function renderAppointments() {
    const patients: AppointmentPatient[] = workItems.map((item) => {
      const profile = patientProfiles[item.hospitalId] ?? {
        age: 61,
        team: 'Oncology',
        participant: 'Family participant to be confirmed',
        dob: 'Not recorded',
        sex: 'Not recorded',
      };
      return {
        hospitalId: item.hospitalId,
        name: item.patient,
        purpose: item.purpose,
        owner: item.owner,
        followUpStatus: item.status,
        lastActivity: item.lastActivity,
        dob: profile.dob,
        sex: profile.sex,
        age: profile.age,
        team: profile.team,
        participant: profile.participant,
        contactChannel: item.contactChannel ?? '',
      };
    });

    function hasVerifiedRecord(id: string) { return patientHasApprovedSummary(id); }
    function versionLabel(id: string) { return patientSummaryLabel(id); }

    return (
      <AppointmentsPage
        patients={patients}
        selectedId={selectedId}
        onSelectPatient={selectPatient}
        appointments={appointments}
        onAppointmentsChange={setAppointments}
        canSchedule={isTreatingPhysician || isCareCoordinator}
        canStartConversation={isTreatingPhysician}
        readOnlyReason="Only the treating doctor and care coordinator can change appointments."
        hasVerifiedRecord={hasVerifiedRecord}
        versionLabel={versionLabel}
        onAudit={(event, record) => addAudit(event, record, 'SCHEDULE')}
        onNotify={notify}
        onOpenProfile={(id) => {
          const item = workItems.find((w) => w.hospitalId === id);
          if (item) openPatient(item);
        }}
        onStartConversation={(id) => {
          selectPatient(id);
          navigate('guide');
        }}
        onRetrieve={(id) => beginRetrievalFor(id)}
        onRecordOutreach={(id) => {
          selectPatient(id);
          navigate('outreach');
        }}
        onOpenAudit={() => navigate('audit')}
        onOpenRecords={() => navigate('records')}
        onPatientActivity={(id, activity, nextDue) =>
          setWorkItems((items) =>
            items.map((w) =>
              w.hospitalId === id
                ? { ...w, lastActivity: activity, ...(nextDue ? { due: nextDue } : {}) }
                : w,
            ),
          )
        }
      />
    );
  }

  function renderPatients() {
    const me = currentRole.split(' · ')[0];
    const minePatients = workItems.filter((item) => {
      if (item.owner === me) return true;
      if (appointments.some((a) => a.hospitalId === item.hospitalId && a.clinician === me)) return true;
      if (auditEvents.some((e) => e.actor === me && e.record.includes(item.patient))) return true;
      return false;
    });
    const displayedPatients = patientsScope === 'mine' ? minePatients : workItems;
    return (
      <>
        {renderPageHeading(
          'Patients',
          'Patients in continuity cohort',
          <button
            className="primary-button"
            type="button"
            disabled={!isTreatingPhysician}
            onClick={() => setEnrolOpen(true)}
          >
            <IconPlus className="w-4 h-4" />
            {isTreatingPhysician ? 'Enrol patient' : 'Treating physician role required'}
          </button>,
        )}
        <div className="segmented-control" role="group" aria-label="Patient scope">
          <button
            type="button"
            className="segmented-button"
            aria-pressed={patientsScope === 'all'}
            onClick={() => setPatientsScope('all')}
          >
            All patients ({workItems.length})
          </button>
          <button
            type="button"
            className="segmented-button"
            aria-pressed={patientsScope === 'mine'}
            onClick={() => setPatientsScope('mine')}
          >
            My patients ({minePatients.length})
          </button>
        </div>
        {displayedPatients.length === 0 ? (
          <div className="empty-state">
            <IconInfo className="w-6 h-6 text-gray-400" />
            <p>No patients are linked to you yet.</p>
          </div>
        ) : (
        <section className="directory-list">
          {displayedPatients.map((item) => {
            const initials = item.patient
              .split(' ')
              .map((p) => p[0])
              .join('')
              .slice(0, 2);
            return (
              <button
                className="directory-row"
                type="button"
                key={item.hospitalId}
                onClick={() => openPatient(item)}
              >
                <span className="table-avatar">{initials}</span>
                <span className="directory-identity">
                  <strong>{item.patient}</strong>
                  <small>{item.hospitalId} · {patientProfiles[item.hospitalId]?.team ?? 'Oncology'}</small>
                </span>
                <span className="directory-purpose">
                  <small>Follow-up Objective</small>
                  <strong>{item.purpose}</strong>
                </span>
                <StatusPill status={item.status} />
                <span className="directory-open">
                  Open profile <IconChevronRight className="w-3 h-3" />
                </span>
              </button>
            );
          })}
        </section>
        )}
      </>
    );
  }

  function renderDrafts() {
    const drafts = workItems.filter((item) => {
      const state = recordStates[item.hospitalId];
      return (state?.draftPrepared && !state.versionPublished) || (!state?.draftPrepared && item.status === 'Awaiting review');
    });
    return (
      <>
        {renderPageHeading('Conversations', 'Drafts')}
        <section className="worklist-panel"><div className="table-wrap"><table>
          <thead><tr><th>Patient</th><th>Version</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>{drafts.map((patient) => {
            const state = recordStates[patient.hospitalId];
            return <tr key={patient.hospitalId}>
              <td data-label="Patient"><strong>{patient.patient}</strong><span>{patient.hospitalId}</span></td>
              <td data-label="Version">Version {nextSummaryVersion(state)}</td>
              <td data-label="Status">{state.draftSource ? 'Draft in review' : 'Source note needed'}</td>
              <td className="row-action"><button type="button" disabled={!isTreatingPhysician} onClick={() => { selectPatient(patient.hospitalId); navigate('draft'); }}>Open draft</button></td>
            </tr>;
          })}</tbody>
        </table>{drafts.length === 0 && <p className="compact-empty-state">No active drafts.</p>}</div></section>
      </>
    );
  }

  function renderRecords() {
    const releases = workItems.flatMap((patient) => (recordStates[patient.hospitalId]?.releases ?? []).map((release) => ({ patient, release }))).sort((a, b) => b.release.releasedAt.localeCompare(a.release.releasedAt));
    return (
      <>
        {renderPageHeading('Released summaries', 'Previous notes')}
        <section className="worklist-panel"><div className="table-wrap"><table>
          <thead><tr><th>Patient</th><th>Version</th><th>Verified by</th><th>Acknowledgement</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>{releases.map(({ patient, release }) => (
            <tr key={`${patient.hospitalId}:${release.number}`}>
              <td data-label="Patient"><strong>{patient.patient}</strong><span>{patient.hospitalId}</span></td>
              <td data-label="Version">Version {release.number}<span>{releaseDate(release)}</span></td>
              <td data-label="Verified by">{release.physician}</td>
              <td data-label="Acknowledgement">{release.authorisation ?? 'Not loaded'}</td>
              <td data-label="Status"><StatusPill status={release.number === latestReleaseFor(patient.hospitalId)?.number ? 'Current' : 'Superseded'} /></td>
              <td className="row-action">
                <button type="button" aria-label={`View ${patient.patient} Version ${release.number}`} disabled={!currentRole.startsWith('Dr')} onClick={() => beginRetrievalFor(patient.hospitalId, release.number)}>View</button>
                {release.number > 1 && <button type="button" aria-label={`Compare ${patient.patient} Version ${release.number} with previous`} disabled={!currentRole.startsWith('Dr')} onClick={() => {
                  selectPatient(patient.hospitalId); setComparisonVersion(release.number); setDiffOpen(true);
                }}>Compare</button>}
              </td>
            </tr>
          ))}</tbody>
        </table>{releases.length === 0 && <p className="compact-empty-state">No released summaries.</p>}</div></section>
      </>
    );
  }

  function renderAudit() {
    return (
      <>
        {renderPageHeading('History', 'Recent activity')}

        <div
          style={{
            display: 'flex',
            gap: '12px',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            marginBottom: '14px',
            padding: '13px 16px',
            borderRadius: '11px',
            border: '1px solid #cbd5e1',
            background: '#0f172a',
          }}
        >
          <div>
            <strong style={{ display: 'block', fontSize: '13px', color: '#ffffff' }}>
              Conversation review
            </strong>
            <small style={{ fontSize: '12px', color: '#94a3b8' }}>
              The workflow review the clinical leads asked for, scored on the seven agreed measures.
            </small>
          </div>
          <button
            type="button"
            onClick={() => setScenarioReviewOpen(true)}
            style={{
              padding: '9px 16px',
              borderRadius: '8px',
              border: 'none',
              background: '#d9ef75',
              color: '#0a2118',
              fontSize: '13px',
              fontWeight: 800,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            Run the review
          </button>
        </div>

        <div className="filter-row mb-4 rounded-lg border border-line bg-surface p-2 flex items-center gap-3">
          <span className="text-xs font-semibold text-muted pl-2">Filter by Actor:</span>
          {['All', 'Dr Sujay', 'Dr Isha Menon', 'Anitha Rao', 'Kavya Raghavan'].map((actor) => (
            <button
              key={actor}
              className={`filter-button ${auditFilterActor === actor ? 'active' : ''}`}
              type="button"
              onClick={() => setAuditFilterActor(actor)}
              aria-pressed={auditFilterActor === actor}
            >
              {actor}
            </button>
          ))}
        </div>

        <section className="worklist-panel">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Actor / Role</th>
                  <th>Event Type</th>
                  <th>Event Description</th>
                  <th>Target Record</th>
                </tr>
              </thead>
              <tbody>
                {filteredAuditEvents.map((event, index) => {
                  const badgeType = event.badge || (event.event.includes('Verified') ? 'PUBLISH' : event.event.includes('outreach') ? 'OUTREACH' : event.event.includes('Enrolled') ? 'ENROL' : 'VIEW');
                  return (
                    <tr key={`${event.time}-${event.event}-${index}`}>
                      <td data-label="Timestamp" style={{ whiteSpace: 'nowrap' }}>
                        {event.time}
                      </td>
                      <td data-label="Actor / Role">
                        <strong>{event.actor}</strong>
                      </td>
                      <td data-label="Event Type">
                        <span className={`audit-badge audit-badge-${badgeType}`}>{badgeType}</span>
                      </td>
                      <td data-label="Event Description">
                        <span>{event.event}</span>
                      </td>
                      <td data-label="Target Record">
                        <strong>{event.record}</strong>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {filteredAuditEvents.length === 0 && (
            <p className="audit-empty">
              No recorded events for {auditFilterActor}. Only actions taken in this session
              appear here.
            </p>
          )}
        </section>

        <div className="audit-footnote" role="note">
          <IconInfo className="w-4 h-4" />
          <p>
            Showing {filteredAuditEvents.length} of {auditEvents.length} recorded events
            {auditFilterActor === 'All' ? '' : ` for ${auditFilterActor}`}. This history is
            saved for this session.
          </p>
        </div>
      </>
    );
  }

  function renderCurrentView() {
    switch (view) {
      case 'caregiver': return renderCareJourney();
      case 'home': return renderHome();
      case 'guide': return renderGuide();
      case 'patient': return renderPatientRecord();
      case 'outreach': return renderOutreach();
      case 'draft': return renderDraft();
      case 'verify': return renderVerify();
      case 'retrieve': return renderRetrieve();
      case 'patients': return renderPatients();
      case 'appointments': return renderAppointments();
      case 'drafts': return renderDrafts();
      case 'records': return renderRecords();
      case 'audit': return renderAudit();
      case 'my-plan':
        return (
          <MyCarePlan
            patientName={portalPatientName}
            versionLabel={planVersionLabel(portalPatientId)}
            verifiedBy={latestReleaseFor(portalPatientId)?.physician ?? ''}
            verifiedOn={releaseDate(latestReleaseFor(portalPatientId))}
            fields={planFieldsFor(portalPatientId)}
            summaryAvailable={hasLoadedReleasedSummary(portalPatientId)}
            hasReleasedVersion={patientHasApprovedSummary(portalPatientId)}
            quickView={<EdQuickView record={ectprQuickViews[portalPatientId] ?? null} audience="patient" />}
            consent={consentFor(portalPatientId)}
            onGoToConsent={() => navigate('consent')}
            onOpenSymptomTracker={() => setEsasOpen(true)}
          />
        );
      case 'my-timeline': {
        const kindMap: Partial<Record<HistoryEvent['kind'], TimelineEntry['kind']>> = {
          appointment: 'appointment',
          call: 'outreach',
          conversation: 'conversation',
          version: 'version',
          consent: 'consent',
          card: 'card',
          review: 'review',
          details: 'details',
        };
        const entries: TimelineEntry[] = buildHistoryEvents(portalPatientId)
          .slice()
          .sort((a, b) => a.sortKey - b.sortKey)
          .map((e) => kindMap[e.kind] && {
            date: e.dateLabel,
            title: e.title,
            body: e.detail,
            kind: kindMap[e.kind] as TimelineEntry['kind'],
          })
          .filter((e): e is TimelineEntry => Boolean(e));
        return (
          <MyTimeline
            audience={isPatientSession ? 'patient' : 'family'}
            patientName={portalPatientName}
            entries={entries}
          />
        );
      }
      case 'care-near-me':
        return <CareNearMe audience={isPatientSession ? 'patient' : 'family'} patientName={portalPatientName} />;
      case 'support-resources':
        return <SupportResources />;
      case 'prepare-conversation': {
        const noteKey = `${sessionType}:${portalPatientId}:${portalAuthor}`;
        return <ConversationPreparation
          patientName={portalPatientName}
          hindi={hindi}
          author={`${portalAuthor} (${sessionType})`}
          notes={preparationNotes[noteKey] ?? {}}
          onChange={(notes) => setPreparationNotes((current) => ({ ...current, [noteKey]: notes }))}
        />;
      }
      case 'daily-care': {
        const nextVisit = appointments.filter((item) => item.hospitalId === portalPatientId && item.status === 'Scheduled' && item.date >= DEMO_TODAY)
          .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`))[0];
        return <FamilyCareHome patientName={portalPatientName} hindi={hindi} nextVisit={nextVisit}
          onOpen={navigate} onCalendar={() => navigate('calendar')} onTasks={() => navigate('family-tasks')}
          onComfort={() => navigate('comfort')} onReports={() => navigate('reports')} />;
      }
      case 'comfort': {
        const author = portalAuthor;
        return <FamilyComfortSpace patientId={portalPatientId} author={author} today={DEMO_TODAY} hindi={hindi}
          entries={comfortEntries} onChange={setComfortEntries} onOpenJournal={() => navigate('voice-journal')} onOpenSymptoms={() => navigate('symptom-diary')} />;
      }
      case 'visit-questions':
      case 'family-tasks': {
        const author = portalAuthor;
        return <FamilyCareWorkspace key={portalPatientId} patientId={portalPatientId} patientName={portalPatientName} author={author}
          tasks={familyTasks} appointments={appointments} demoToday={DEMO_TODAY}
          tasksOnly
          section={view === 'visit-questions' ? 'questions' : 'tasks'}
          onTrackQuestion={(task) => {
            if (task.patientId !== portalPatientId || task.kind !== 'Question') return;
            setOpenQuestions((current) => addOpenQuestion(current, portalPatientId, author, DEMO_TODAY, task.id, { topic: task.title, owner: task.owner, due: task.due }));
            navigate('open-questions');
          }}
          calendarText={[
            'PERSONAL CALENDAR — entered from existing plans; not a prescription',
            ...careEvents.filter((event) => event.patientId === portalPatientId).map((event) => `${event.date} ${event.time}${event.repeatUntil ? `; daily until ${event.repeatUntil}` : ''} — ${event.kind}: ${event.title}\n${event.instructions}\nAdded by ${event.addedBy}`),
            'CALENDAR CHECKS',
            ...careChecks.filter((check) => check.patientId === portalPatientId).map((check) => `${check.date} — ${careEvents.find((event) => event.patientId === portalPatientId && event.id === check.eventId)?.title ?? 'Calendar item'}; marked by ${check.actor} at ${check.checkedAt}`),
            'REPORT FILE LIST — original files are not included in this text copy',
            ...careReports.filter((report) => report.patientId === portalPatientId).map((report) => `${report.date} — ${report.file.name}; added by ${report.addedBy}`),
          ].join('\n\n')}
          onAdd={(task) => setFamilyTasks((current) => task.patientId === portalPatientId ? addFamilyTask(current, task) : current)}
          onComplete={(taskId, complete) => setFamilyTasks((current) => setFamilyTaskCompleted(current, portalPatientId, taskId, author, complete))}
          onPrepare={() => navigate('prepare-conversation')} onCareTeam={() => navigate('my-doctors')} onPlan={() => navigate('my-plan')} hindi={hindi} />;
      }
      case 'reports':
      case 'calendar': {
        const author = portalAuthor;
        return <>{view === 'calendar' && <div className="family-workspace-heading"><h1>{hindi ? 'कैलेंडर' : 'Calendar'}</h1></div>}
          <CareCalendar patientId={portalPatientId} author={author} today={DEMO_TODAY} hindi={hindi}
            focusReports={view === 'reports'} onAppointmentInstructions={saveAppointmentInstructions} onTestResults={() => navigate('lab-history')}
            appointments={appointments} tasks={familyTasks} events={careEvents} checks={careChecks} reports={careReports} reportSave={reportSave}
            onAdd={(event) => setCareEvents((current) => event.patientId === portalPatientId ? addCareEvent(current, event) : current)}
            onUpdate={(event) => {
              const previous = careEvents.find((item) => item.patientId === portalPatientId && item.id === event.id);
              const updated = updateCareEvent(careEvents, portalPatientId, event);
              if (!previous || updated === careEvents) return;
              setCareEvents(updated);
              setCareChecks((current) => careChecksAfterEventUpdate(current, previous, event));
            }}
            onCheck={(eventId, date, complete) => setCareChecks((current) => setCareCheck(current, careEvents, portalPatientId, eventId, date, author, new Date().toISOString(), DEMO_TODAY, complete))}
            onTaskCheck={(taskId, complete) => setFamilyTasks((current) => setFamilyTaskCompleted(current, portalPatientId, taskId, author, complete))}
            onRemove={(eventId) => {
              setCareEvents((current) => current.filter((event) => event.patientId !== portalPatientId || event.id !== eventId));
              setCareChecks((current) => current.filter((check) => check.patientId !== portalPatientId || check.eventId !== eventId));
            }}
            onRestore={(event, restoredChecks) => {
              if (event.patientId !== portalPatientId) return;
              setCareEvents((current) => addCareEvent(current, event));
              setCareChecks((current) => [...current, ...restoredChecks.filter((check) => check.patientId === portalPatientId && check.eventId === event.id && !current.some((existing) => existing.patientId === check.patientId && existing.eventId === check.eventId && existing.date === check.date))]);
            }}
            onReports={(reports) => setCareReports((current) => [...current, ...reports.filter((report) => report.patientId === portalPatientId && !reportFileError(report.file) && !current.some((existing) => existing.id === report.id))])}
            onRemoveReport={(reportId) => setCareReports((current) => current.filter((report) => report.patientId !== portalPatientId || report.id !== reportId))}
            onCareTeam={() => navigate('my-doctors')} /></>;
      }
      case 'symptom-diary':
      case 'care-story':
      case 'doctor-pack':
      case 'home-help':
      case 'support-places':
      case 'open-questions':
      case 'copy-tracker':
      case 'voice-journal':
      case 'cancer-overview':
      case 'cost-help':
      case 'lab-history': {
        const release = latestReleaseFor(portalPatientId);
        const author = portalAuthor;
        return <FamilyCareTools tool={view} patientId={portalPatientId} patientName={portalPatientName} author={author} today={DEMO_TODAY} hindi={hindi}
          recordedStory={recordedStoryFor(portalPatientId)} knownContacts={knownContactsFor(portalPatientId)}
          symptoms={symptoms} story={careStory} contacts={handoverContacts} onSymptoms={setSymptoms} onStory={setCareStory} onContacts={setHandoverContacts}
          homeHelp={homeHelp} supportPlaces={supportPlaces} onHomeHelp={setHomeHelp} onSupportPlaces={setSupportPlaces}
          questions={openQuestions} copies={careCopies} releases={recordStates[portalPatientId]?.releases ?? []} onQuestions={setOpenQuestions} onCopies={setCareCopies}
          voiceJournal={voiceJournal} onVoiceJournal={setVoiceJournal} voiceSave={voiceSave}
          costs={familyCosts} onCosts={setFamilyCosts}
          labResults={labResults} onLabResults={setLabResults}
          diagnosis={patientProfiles[portalPatientId]?.diagnosis ?? patientProfiles[portalPatientId]?.stage.split(' · ')[0] ?? ''} team={patientProfiles[portalPatientId]?.team ?? ''}
          reports={careReports} onOpen={navigate} onCareNote={() => navigate('my-plan')} onReports={() => navigate('reports')}
          note={release ? { version: release.number, physician: release.physician, releasedAt: release.releasedAt, fields: planFieldsFor(portalPatientId) } : null}
          calendarText={[
            ...appointments.filter((item) => item.hospitalId === portalPatientId && item.status === 'Scheduled').map((item) => `${item.date} ${item.time} — ${item.type}; ${item.clinician}${item.preparationInstructions ? `\nInstructions from ${item.preparationInstructions.givenBy}: ${item.preparationInstructions.text}\nWritten here by ${item.preparationInstructions.recordedBy}` : ''}`),
            ...careEvents.filter((item) => item.patientId === portalPatientId).map((item) => `${item.date} ${item.time}${item.repeatUntil ? `; daily until ${item.repeatUntil}` : ''} — ${item.title}\n${item.instructions}; added by ${item.addedBy}`),
            ...familyTasks.filter((item) => item.patientId === portalPatientId).map((item) => `${item.completedBy ? 'Done' : 'To do'}: ${item.title}; ${item.owner}${item.due ? `; ${item.due}` : ''}`),
          ].join('\n\n')}
          reportNames={careReports.filter((report) => report.patientId === portalPatientId).map((report) => `${report.date} — ${report.file.name}`)} />;
      }
      case 'my-doctors': {
        const patientAppointments = appointments.filter(
          (a) => a.hospitalId === portalPatientId && a.status !== 'Scheduled',
        );
        const roleFor = (clinician: string) =>
          clinician === 'Dr Sujay'
            ? 'Treating consultant'
            : clinician === 'Anitha Rao'
              ? 'Care coordinator'
              : clinician === 'Dr Isha Menon'
                ? 'Emergency physician'
                : 'Care team member';
        const grouped = new Map<string, CareTeamMember>();
        for (const a of patientAppointments) {
          const existing = grouped.get(a.clinician);
          const visit: PastVisit = {
            date: formatDateForDisplay(a.date),
            time: a.time,
            type: a.type,
            mode: a.mode,
            status: a.status,
            outcome: a.outcome,
          };
          if (existing) {
            existing.visits.push(visit);
          } else {
            grouped.set(a.clinician, {
              name: a.clinician,
              role: roleFor(a.clinician),
              organisation: 'Oncology · Palliative care ',
              contactNote: 'Contact details are held by the originating team.',
              lastVisited: null,
              visits: [visit],
            });
          }
        }
        if (!grouped.has('Dr Sujay')) {
          grouped.set('Dr Sujay', {
            name: 'Dr Sujay',
            role: 'Treating consultant',
            organisation: 'Oncology · Palliative care ',
            contactNote: 'Contact details are held by the originating team.',
            lastVisited: null,
            visits: [],
          });
        }
        const members = Array.from(grouped.values()).map((m) => {
          const attended = m.visits.filter((v) => v.status === 'Attended');
          const last = attended[attended.length - 1];
          return { ...m, lastVisited: last ? `${last.date}` : null };
        });
        const details = patientDetailsById[portalPatientId];
        if (details?.localPhysician?.name) {
          members.push({
            name: details.localPhysician.name,
            role: 'Local doctor',
            organisation: details.localPhysician.clinic || 'Not recorded',
            contactNote: 'Added by the patient',
            lastVisited: null,
            visits: [],
          });
        }
        return (
          <MyDoctors
            audience={isPatientSession ? 'patient' : 'family'}
            patientName={portalPatientName}
            members={members}
          />
        );
      }
      case 'emergency-card':
        return (
          <>
            {renderPageHeading('Emergency card', 'Keep this with the patient')}
            <EmergencyCard
              record={ectprQuickViews[portalPatientId] ?? null}
              audience={isPatientSession ? 'patient' : 'family'}
              issuedOn={cardIssuedById[portalPatientId] ?? null}
              {...(isPatientSession ? { onIssue: () => issueCard(portalPatientId) } : {})}
            />
          </>
        );
      case 'my-details':
        return (
          <MyDetails
            details={detailsFor(portalPatientId)}
            onSave={(next) => saveDetails(portalPatientId, next)}
            clinicalItems={clinicalItemsFor(portalPatientId)}
            reviewRequestedOn={reviewRequestedById[portalPatientId] ?? null}
            onRequestReview={() => requestReview(portalPatientId)}
          />
        );
      case 'consent':
        return (
          <ConsentSignature
            patientName={portalPatientName}
            versionLabel={planVersionLabel(portalPatientId)}
            summaryFields={planFieldsFor(portalPatientId)}
            summaryAvailable={hasLoadedReleasedSummary(portalPatientId)}
            consent={consentFor(portalPatientId)}
            onSign={(input) => signConsent(portalPatientId, input)}
          />
        );
      default: return renderWorklist();
    }
  }

  const navView = ['draft', 'verify'].includes(view)
    ? 'guide'
    : view === 'retrieve'
      ? 'home'
      : ['patient', 'outreach'].includes(view)
        ? 'patients'
        : view;
  const mobileNavLabels: Partial<Record<View, string>> = {
    'my-plan': 'Plan',
    'my-timeline': 'History',
    'prepare-conversation': 'Prepare',
    'daily-care': 'Daily care',
    'care-near-me': 'Nearby',
    'my-doctors': 'Doctors',
    'emergency-card': 'Card',
    'my-details': 'Details',
    consent: 'Consent',
    caregiver: 'Home',
    home: 'Home',
    guide: 'Conversations',
    worklist: 'Follow-ups',
    appointments: 'Appointments',
    patients: 'Patients',
    records: 'Records',
    audit: 'History',
  };
  const visibleNavItems = isPatientSession
    ? patientNavItems
    : isFamilySession
      ? familyNavItems
      : navItems.filter((item) => item.view !== 'caregiver');

  if (authScreen !== null) {
    return <CareSignIn patients={workItems.map((item) => ({ id: item.hospitalId, name: item.patient }))} hindi={hindi} onLanguage={() => setHindi((value) => !value)}
      onSignIn={({ role, patientId, name }) => {
        if (!workItems.some((item) => item.hospitalId === patientId)) return;
        const nextView = requestedView && canOpenCareView(requestedView, role === 'doctor' ? 'care-team' : role, 'Dr Sujay · Clinical lead')
          ? requestedView : role === 'doctor' ? 'home' : 'daily-care';
        if (role === 'doctor') { selectPatient(patientId); enterCareTeam('Dr Sujay · Clinical lead', nextView); }
        else if (role === 'patient') enterPatient({ hospitalId: patientId, name }, nextView);
        else enterFamily({ patientId, name, relationship: 'Family member', verifiedSummaryOnly: true }, nextView);
      }} />;
  }

  if (simpleMode && authScreen === null) {
    const saves = [preparationSave, taskSave, eventSave, checkSave, reportSave, appointmentSave, noteSave, symptomSave, storySave, contactSave, helpSave, placesSave, questionSave, copySave, comfortSave, voiceSave, costSave, labSave, conversationSave];
    if (saves.includes('loading')) return <main className="care-opening" aria-busy="true">{hindi ? 'देखभाल की जानकारी खोल रहे हैं…' : 'Opening your care…'}</main>;
    const saveStatus = saves.includes('unavailable') ? 'unavailable' : saves.includes('saving') ? 'saving' : 'saved';
    const role = isFamilySession ? 'family' : isPatientSession ? 'patient' : 'doctor';
    return <SimpleCareShell view={view} role={role} hindi={hindi} saveStatus={saveStatus}
      onSignOut={signOut}
      onLanguage={() => setHindi((value) => !value)} onNavigate={navigate}
      onRole={(next) => {
        if (next === 'doctor') enterCareTeam('Dr Sujay · Clinical lead');
        else if (next === 'patient') enterPatient({ hospitalId: selectedId, name: selectedItem.patient, dob: patientProfiles[selectedId]?.dob ?? '' });
        else enterFamily({ name: selectedId === 'CANCER-20418' ? 'Kavya Raghavan' : 'Family member', relationship: 'Family member', patientId: selectedId, verifiedSummaryOnly: true });
      }}>
      {role === 'doctor' && view === 'home' ? <DoctorCareHome
        patients={workItems.map((item) => ({ id: item.hospitalId, name: item.patient, diagnosis: patientProfiles[item.hospitalId]?.diagnosis ?? patientProfiles[item.hospitalId]?.stage.split(' · ')[0] ?? '' }))}
        records={recordStates} appointments={appointments} physician={currentRole.split(' · ')[0]} today={DEMO_TODAY} hindi={hindi}
        onReview={(id) => { selectPatient(id); setView('doctor-review'); }}
        onRecord={(id) => { if (id) selectPatient(id); setNewDoctorConversation(true); setView('doctor-record'); }}
        onHistory={(id) => { if (id) selectPatient(id); setView('doctor-history'); }} onInstructions={saveAppointmentInstructions} />
        : role === 'doctor' && view === 'doctor-record' ? <DoctorConversation key={`${selectedId}:${newDoctorConversation}`}
          patientId={selectedId} patientName={selectedItem.patient} patients={workItems.map((item) => ({ id: item.hospitalId, name: item.patient }))}
          physician={currentRole.split(' · ')[0]} initialNote={newDoctorConversation || recordState.versionPublished ? '' : recordState.draftSource} hindi={hindi}
          onPatient={selectPatient} onSave={saveDoctorConversation} onHistory={() => navigate('doctor-history')} />
        : role === 'doctor' && view === 'doctor-review' ? <DoctorNoteReview key={selectedId}
          patientId={selectedId} patientName={selectedItem.patient} patients={workItems.map((item) => ({ id: item.hospitalId, name: item.patient }))}
          state={recordState} physician={currentRole.split(' · ')[0]} onPatient={selectPatient}
          onRecord={() => { setNewDoctorConversation(true); navigate('doctor-record'); }} onEdit={() => { setNewDoctorConversation(false); navigate('doctor-record'); }} onHistory={() => navigate('doctor-history')}
          onFields={(fields) => {
            if (!isTreatingPhysician || sessionType !== 'care-team') return;
            updateSelectedRecord((current) => {
              const next = reviseSummary(current, { draftFields: fields });
              const keys = Object.keys(fields) as DraftFieldKey[];
              return reviseSummary(next, { draftStatuses: Object.fromEntries(keys.map((key) => [key, fields[key].trim() && fields[key] !== 'Not stated in this conversation.' ? 'ready' : 'not-stated'])) as Record<DraftFieldKey, DraftFieldStatus>,
                draftExcerpts: Object.fromEntries(keys.map((key) => [key, fields[key].trim() && fields[key] !== 'Not stated in this conversation.' ? current.draftSource : ''])) as Record<DraftFieldKey, string> });
            });
          }}
          onApprove={(permission, reviewed, signature) => {
            const next = approveVisitNote(recordState, { treatingPhysician: isTreatingPhysician && sessionType === 'care-team', physician: currentRole.split(' · ')[0], permission, reviewed, signatureName: signature, releasedAt: new Date().toISOString() });
            if (next === recordState) return;
            const release = latestSummaryRelease(next)!;
            updateSelectedRecord(() => ({ ...next, retrievalAcknowledged: false, retrievalUnlocked: false, unlockedVersion: null }));
            addAudit(`Signed care note version ${release.number}`, selectedItem.patient, 'PUBLISH', undefined, selectedId, release.number);
          }}
          onFamily={() => navigate('my-plan')} />
        : view === 'my-plan' || view === 'note-history' || view === 'doctor-history' ? <CareNoteReader key={portalPatientId}
          patientId={portalPatientId} patientName={portalPatientName} releases={recordStates[portalPatientId]?.releases ?? []} hindi={hindi}
          doctor={role === 'doctor'} conversations={doctorConversations}
          patients={role === 'doctor' ? workItems.map((item) => ({ id: item.hospitalId, name: item.patient })) : undefined}
          onPatient={role === 'doctor' ? selectPatient : undefined} onRecord={role === 'doctor' ? () => { setNewDoctorConversation(true); navigate('doctor-record'); } : undefined}
          onPrepare={role === 'doctor' ? undefined : () => navigate('prepare-conversation')} />
        : renderCurrentView()}
    </SimpleCareShell>;
  }

  const familyInitials = (familyMember?.name ?? '')
    .split(' ')
    .filter(Boolean)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <>
      <div className="simple-workspace-return"><button type="button" onClick={returnToCare}>← Back to family care</button></div>
      <main className={isPatientSession || isFamilySession ? 'app-shell is-portal' : 'app-shell'}>
      {/* Sidebar Navigation */}
      <aside className="sidebar" aria-label="Primary navigation">
        <button
          className="brand-button"
          type="button"
          onClick={() => navigate(isPatientSession ? 'my-plan' : isFamilySession ? 'caregiver' : 'home')}
        >
          <span className="brand-mark" aria-hidden="true"><ContinuityMark /></span>
          <div className="brand-titles">
            <strong>Saanthvana</strong>
            <span>Care planning</span>
          </div>
        </button>

        <nav className="main-nav">
          {visibleNavItems.map((item) => {
            const Icon = item.icon;
            const count =
              item.view === 'worklist'
                ? counts.all
                : undefined;
            return (
              <button
                className={navView === item.view ? 'nav-item active' : 'nav-item'}
                type="button"
                key={item.view}
                onClick={() => navigate(item.view)}
                aria-current={navView === item.view ? 'page' : undefined}
              >
                <Icon className="w-4 h-4" />
                <span>{item.label}</span>
                {count !== undefined && <span className="nav-count-badge">{count}</span>}
              </button>
            );
          })}
        </nav>

      </aside>

      {/* Main Workspace Area */}
      <section className="workspace">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="mobile-brand brand-button"
              type="button"
              onClick={() => navigate(isPatientSession ? 'my-plan' : isFamilySession ? 'caregiver' : 'home')}
            >
              <span className="brand-mark" aria-hidden="true"><ContinuityMark /></span>
              <strong>Saanthvana</strong>
            </button>
            <div className="environment-label">

            </div>
          </div>

          <div className="topbar-right">
            {isPatientSession ? (
              <div className="family-session-bar" aria-label="Patient session">
                <span className="family-session-label is-patient"><IconLock className="w-3.5 h-3.5" />Patient view</span>
                <div className="profile-block" aria-label="Signed-in patient">
                  <span className="profile-initials patient" aria-hidden="true">
                    {(patientAccount?.name ?? '')
                      .split(' ')
                      .filter(Boolean)
                      .map((p) => p[0])
                      .join('')
                      .slice(0, 2)
                      .toUpperCase() || 'PT'}
                  </span>
                  <div className="profile-info">
                    <strong>{patientAccount?.name}</strong>
                    <small>Patient</small>
                  </div>
                </div>
                <button className="secondary-button" type="button" onClick={signOut}>Sign out</button>
              </div>
            ) : isFamilySession ? (
              <div className="family-session-bar" aria-label="Family session">
                <span className="family-session-label"><IconLock className="w-3.5 h-3.5" />Family view</span>
                <div className="profile-block" aria-label="Signed-in family member">
                  <span className="profile-initials family" aria-hidden="true">{familyInitials || 'FM'}</span>
                  <div className="profile-info">
                    <strong>{familyMember?.name}</strong>
                    <small>{familyMember?.relationship}</small>
                  </div>
                </div>
                <button className="secondary-button" type="button" onClick={signOut}>Sign out</button>
              </div>
            ) : view === 'caregiver' ? (
              <div className="caregiver-preview-switcher" aria-label="Family view">
                <span><IconLock className="w-3.5 h-3.5" />Previewing the family view</span>
                <button className="secondary-button" type="button" onClick={() => navigate('home')}>
                  Back to care team
                </button>
              </div>
            ) : (
              <>
                <div className="role-switcher-wrap" title="Choose a care team member">
                  <span className="role-label">Care team</span>
                  <select
                    className="role-select"
                    aria-label="Care team member"
                    value={currentRole}
                    onChange={(e) => {
                      const role = e.target.value as UserRole;
                      switchRole(role);
                    }}
                  >
                    <option value="Dr Sujay · Clinical lead">Dr Sujay (Physician)</option>
                    <option value="Dr Isha Menon · Emergency physician">Dr Isha Menon (Emergency)</option>
                    <option value="Anitha Rao · Care Coordinator">Anitha Rao (Coordinator)</option>
                  </select>
                </div>

                <div className="profile-block" aria-label="Current profile">
                  <span className={`profile-initials ${currentRole.startsWith('Anitha') ? 'coordinator' : ''}`} aria-hidden="true">
                    {currentRole.startsWith('Anitha')
                      ? 'AR'
                      : currentRole.startsWith('Dr Isha')
                        ? 'IM'
                        : 'SS'}
                  </span>
                  <div className="profile-info">
                    <strong>{currentRole.split(' · ')[0]}</strong>
                    <small>{currentRole.split(' · ')[1]}</small>
                  </div>
                </div>

                <button
                  className="secondary-button"
                  type="button"
                  onClick={signOut}
                >
                  Sign out
                </button>
              </>
            )}
          </div>
        </header>

        {/* Mobile Navigation Bar */}
        <nav className="mobile-nav" aria-label="Mobile navigation">
          {visibleNavItems.map((item) => (
            <button
              type="button"
              key={item.view}
              className={navView === item.view ? 'active' : ''}
              onClick={() => navigate(item.view)}
              aria-current={navView === item.view ? 'page' : undefined}
            >
              {mobileNavLabels[item.view] ?? item.label}
            </button>
          ))}
        </nav>

        <div className={`content-frame${view === 'home' ? ' care-day' : ''}`}>{renderCurrentView()}</div>
      </section>

      {/* Enrolment Modal */}
      {enrolOpen && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) setEnrolOpen(false);
          }}
        >
          <section
            ref={enrolDialogRef}
            className="modal-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="enrol-title"
          >
            <div className="modal-header">
              <div>
                <p className="eyebrow">
                  <IconPlus className="w-3.5 h-3.5" /> Clinician-Controlled Enrolment
                </p>
                <h2 id="enrol-title">Enrol patient in continuity follow-up</h2>
              </div>
              <button type="button" aria-label="Close enrolment form" onClick={() => setEnrolOpen(false)}>
                <IconX className="w-4 h-4" />
              </button>
            </div>

            <form className="modal-form" onSubmit={submitEnrolment}>
              <label className="form-field">
                <span>Select Patient</span>
                <select
                  value={enrolPatientId}
                  onChange={(event) => {
                    const hospitalId = event.target.value as keyof typeof enrolmentCandidates;
                    setEnrolPatientId(hospitalId);
                    setEnrolDate(hospitalId === 'CANCER-20452' ? '2026-09-02' : '2026-09-04');
                  }}
                >
                  <option value="CANCER-20452">Sana Iyer · CANCER-20452 (Stage III Cervical Carcinoma)</option>
                  <option value="CANCER-20489">Vikram Mehta · CANCER-20489 (Stage IV Pancreatic Adenocarcinoma)</option>
                </select>
              </label>

              <label className="form-field">
                <span>Continuity Objective / Reason</span>
                <select value={enrolReason} onChange={(event) => setEnrolReason(event.target.value)}>
                  <option>Conversation started and needs continuation</option>
                  <option>Review requested by primary treating clinician</option>
                  <option>Patient or surrogate requested another discussion</option>
                  <option>Existing summary needs updating with new preferences</option>
                </select>
              </label>

              <div className="two-field-row">
                <label className="form-field">
                  <span>Assigned Follow-up Owner</span>
                  <select value={enrolOwner} onChange={(event) => setEnrolOwner(event.target.value)} required>
                    <option value="Anitha Rao">Anitha Rao (Care Coordinator)</option>
                    <option value="Dr Sujay">Dr Sujay (Attending Oncologist)</option>
                  </select>
                </label>
                <label className="form-field">
                  <span>Target Follow-up Date</span>
                  <input type="date" value={enrolDate} onChange={(event) => setEnrolDate(event.target.value)} required />
                </label>
              </div>

              <label className="form-field">
                <span>Preferred Contact Channel</span>
                <select name="contact-channel" defaultValue="Phone call with surrogate">
                  <option>Phone call with surrogate</option>
                  <option>Approved SMS follow-up</option>
                  <option>In-person clinic review</option>
                </select>
              </label>

              <label className="form-field">
                <span>Coordination Notes</span>
                <textarea
                  name="coordination-notes"
                  rows={3}
                  defaultValue="Contact patient and daughter to coordinate continuation of clinician-initiated goals-of-care conversation."
                  required
                />
              </label>

              <label className="attestation-row enrol-confirmation">
                <input type="checkbox" checked={enrolConfirmed} onChange={(event) => setEnrolConfirmed(event.target.checked)} />
                <span>
                  <strong>Clinician Confirmation:</strong> I am enrolling this patient based on active clinical care needs. The system does not determine eligibility or urgency automatically.
                </span>
              </label>

              <div className="form-actions">
                <button className="secondary-button" type="button" onClick={() => setEnrolOpen(false)}>
                  Cancel
                </button>
                <button className="primary-button" type="submit" disabled={!enrolConfirmed}>
                  <IconCheck className="w-4 h-4" />
                  Create follow-up task
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {diffOpen && recordState.releases.length > 0 && (
        <ClinicalDiffModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          releases={recordState.releases}
          initialOlderVersion={comparisonOlder?.number ?? 1}
          initialNewerVersion={comparisonNewer?.number ?? (recordState.releases[recordState.releases.length - 1]?.number ?? 1)}
          currentRole={currentRole}
          onClose={() => setDiffOpen(false)}
          onEndorse={(vFrom, vTo) => {
            setAuditEvents((events) => [
              {
                id: `AUDIT-${Date.now().toString().slice(-4)}`,
                time: 'Just now',
                timestamp: 'Just now',
                actor: currentRole,
                event: 'Endorsed version handoff',
                record: `${selectedItem.patient} · V${vFrom} → V${vTo}`,
                badge: 'PUBLISH',
                detail: `Clinical transition handoff endorsement from Version ${vFrom} to Version ${vTo}`,
                securityImpact: 'Routine clinical transition audit logged',
              },
              ...events,
            ]);
            setToast({ message: `Handoff transition V${vFrom} → V${vTo} endorsed by ${currentRole}` });
          }}
        />
      )}

      {fhirExportOpen && (
        <FhirExportModal
          patient={{
            hospitalId: selectedItem.hospitalId,
            name: selectedItem.patient,
            dob: selectedProfile.dob,
            diagnosis: selectedDiagnosis,
            versionLabel: `Version ${recordStates[selectedItem.hospitalId]?.releases[0]?.number ?? 1}`,
            verifiedBy: recordStates[selectedItem.hospitalId]?.releases[0]?.physician,
            verifiedOn: recordStates[selectedItem.hospitalId]?.releases[0]
              ? releaseDate(recordStates[selectedItem.hospitalId]!.releases[0])
              : undefined,
            fields: planFieldsFor(selectedItem.hospitalId),
          }}
          onClose={() => setFhirExportOpen(false)}
        />
      )}

      {tepModalOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setTepModalOpen(false); }}>
          <section className="modal-panel large-modal" role="dialog" aria-modal="true" aria-labelledby="tep-title" style={{ maxWidth: '980px', width: '96%', padding: 0, overflow: 'hidden' }}>
            <TreatmentEscalationMatrix
              patientId={selectedItem.hospitalId}
              patientName={selectedItem.patient}
              onClose={() => setTepModalOpen(false)}
              onSave={(_selections, overallCeiling) => {
                setToast({ message: `TEP Matrix Registered: ${overallCeiling}` });
                setTepModalOpen(false);
              }}
            />
          </section>
        </div>
      )}

      {mapOpen && (
        <MapLibreDispatchModal onClose={() => setMapOpen(false)} />
      )}

      {renderContinuityOpsHub(sessionType)}

      {syringeDriverOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setSyringeDriverOpen(false); }}>
          <section className="modal-panel large-modal" role="dialog" aria-modal="true" aria-labelledby="sd-title" style={{ maxWidth: '980px', width: '96%', padding: 0, overflow: 'hidden' }}>
            <SyringeDriverCalculator
              patientId={selectedItem.hospitalId}
              patientName={selectedItem.patient}
              onClose={() => setSyringeDriverOpen(false)}
              onApplyToCarePlan={(_regimen) => {
                setToast({ message: 'Syringe driver protocol applied to plan' });
                setSyringeDriverOpen(false);
              }}
            />
          </section>
        </div>
      )}

      {esasOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setEsasOpen(false); }}>
          <section className="modal-panel large-modal" role="dialog" aria-modal="true" aria-labelledby="esas-title" style={{ maxWidth: '960px', width: '96%', padding: 0, overflow: 'hidden' }}>
            <EsasSymptomTracker
              patientId={selectedItem.hospitalId}
              patientName={selectedItem.patient}
              onClose={() => setEsasOpen(false)}
              onSave={(_scores, total) => {
                setToast({ message: `ESAS Symptom Assessment saved (Distress Score: ${total}/90)` });
                setEsasOpen(false);
              }}
            />
          </section>
        </div>
      )}

      {deprescribingOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setDeprescribingOpen(false); }}>
          <section className="modal-panel large-modal" role="dialog" aria-modal="true" aria-labelledby="deprescribe-title" style={{ maxWidth: '980px', width: '96%', padding: 0, overflow: 'hidden' }}>
            <PalliativeDeprescribingMatrix
              patientId={selectedItem.hospitalId}
              patientName={selectedItem.patient}
              onClose={() => setDeprescribingOpen(false)}
              onApplyDeprescribingPlan={(_summary) => {
                setToast({ message: 'Deprescribing recommendations applied to summary' });
                setDeprescribingOpen(false);
              }}
            />
          </section>
        </div>
      )}

      {prognosisOpen && (
        <PalliativePrognosisCalculator
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          diagnosis={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          currentCeilingTier={1}
          onClose={() => setPrognosisOpen(false)}
          onApplyPlan={(pps, ppi, _tier, recommendation) => {
            setToast({ message: `Prognostic trajectory applied: PPS ${pps}%, PPI ${ppi} (${recommendation.slice(0, 32)}...)` });
            setPrognosisOpen(false);
          }}
        />
      )}

      {emergencyTriageOpen && (
        <OncologyEmergencyTriageModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          diagnosis={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setEmergencyTriageOpen(false)}
          onDispatchAmbulance={(reason) => {
            setToast({ message: `108 Ambulance dispatched: ${reason}` });
          }}
          onApplyOrdersToRecord={(orders) => {
            setToast({ message: `STAT Emergency Orders Recorded: ${orders.slice(0, 35)}...` });
            setEmergencyTriageOpen(false);
          }}
        />
      )}

      {caregiverBurdenOpen && (
        <CaregiverBurdenMatrix
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          caregiverName="Rohan Sharma"
          caregiverRelation="Son & Primary Healthcare Proxy"
          caregiverPhone="+91 98450 11234"
          onClose={() => setCaregiverBurdenOpen(false)}
          onDispatchRespite={(caregiver, urgency) => {
            setToast({ message: `Respite Nurse dispatched for ${caregiver} (${urgency})` });
          }}
          onSavePlan={(total, tier) => {
            setToast({ message: `Caregiver Support Plan Saved: ZBI-12 ${total}/48 (${tier})` });
            setCaregiverBurdenOpen(false);
          }}
        />
      )}

      {sedationCrisisOpen && (
        <PalliativeSedationCrisisModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          diagnosis={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setSedationCrisisOpen(false)}
          onApplyPlan={(protocolSummary) => {
            setToast({ message: `Palliative Sedation Plan Recorded: ${protocolSummary.slice(0, 35)}...` });
            setSedationCrisisOpen(false);
          }}
        />
      )}

      {trajectoryTimelineOpen && (
        <LongitudinalTrajectoryTimeline
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          diagnosis={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setTrajectoryTimelineOpen(false)}
          onExportHandoffPacket={() => {
            setToast({ message: 'Continuity Transition Packet exported successfully' });
            setTrajectoryTimelineOpen(false);
          }}
        />
      )}

      {drugBoxOpen && (
        <AnticipatoryDrugBoxModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          diagnosis={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setDrugBoxOpen(false)}
          onLogAdministration={(medName, dose) => {
            setToast({ message: `Dose Administered & Logged: ${medName} (${dose})` });
          }}
        />
      )}

      {woundCareOpen && (
        <MalignantWoundCareModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setWoundCareOpen(false)}
          onDispatchKit={(summary) => {
            setToast({ message: `ASHA Home Wound Dressing Kit Dispatched: ${summary}` });
          }}
        />
      )}

      {boneSinsOpen && (
        <BoneMetastasesSinsModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setBoneSinsOpen(false)}
          onDispatchReferral={(summary) => {
            setToast({ message: `Spine / RT Referral Dispatched: ${summary}` });
          }}
        />
      )}

      {cachexiaAscitesOpen && (
        <CachexiaAscitesModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setCachexiaAscitesOpen(false)}
          onDispatchOrder={(summary) => {
            setToast({ message: `Ascites / Nutrition Plan Dispatched: ${summary}` });
          }}
        />
      )}

      {neuropathicBlocksOpen && (
        <NeuropathicPainBlocksModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setNeuropathicBlocksOpen(false)}
          onDispatchReferral={(summary) => {
            setToast({ message: `Interventional Referral Dispatched: ${summary}` });
          }}
        />
      )}

      {breathlessnessCrisisOpen && (
        <BreathlessnessCrisisModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setBreathlessnessCrisisOpen(false)}
          onDispatchOrder={(summary) => {
            setToast({ message: `Dyspnea Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {deliriumOpen && (
        <PalliativeDeliriumModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setDeliriumOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `Delirium Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {pediatricPalliativeOpen && (
        <PediatricPalliativeModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setPediatricPalliativeOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `Pediatric Care Plan Dispatched: ${summary}` });
          }}
        />
      )}

      {bowelObstructionOpen && (
        <MalignantBowelObstructionModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setBowelObstructionOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `MBO Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {renalHepaticOpen && (
        <RenalHepaticPalliativeModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setRenalHepaticOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `Renal/Hepatic Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {radiotherapyOpen && (
        <PalliativeRadiotherapyModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setRadiotherapyOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `Radiotherapy Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {cordCompressionOpen && (
        <SpinalCordCompressionModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setCordCompressionOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `MSCC Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {hypercalcemiaOpen && (
        <MalignantHypercalcemiaModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setHypercalcemiaOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `Hypercalcemia Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {svcoOpen && (
        <SvcoThoracicDecompressionModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedProfile?.diagnosis || 'Metastatic Oncology Disease'}
          onClose={() => setSvcoOpen(false)}
          onDispatchPlan={(summary) => {
            setToast({ message: `SVCO Decompression Protocol Dispatched: ${summary}` });
          }}
        />
      )}

      {bleedCrisisOpen && (
        <BleedCrisisModal
          patientId={selectedItem.hospitalId}
          patientName={selectedItem.patient}
          primaryCancer={selectedDiagnosis}
          onClose={() => setBleedCrisisOpen(false)}
          onDispatchPlan={(summary) => {
            setAuditEvents((events) => [
              {
                id: `AUDIT-BLEED-${Date.now().toString().slice(-4)}`,
                time: 'Just now',
                actor: currentRole,
                event: 'Bleeding plan recorded',
                record: `${selectedItem.patient} · ${summary}`,
                badge: 'PUBLISH',
                patientId: selectedItem.hospitalId,
                detail: summary,
              },
              ...events,
            ]);
            setToast({ message: `Bleeding plan in the record — ${summary.split('.')[0]}` });
          }}
        />
      )}

      {scenarioReviewOpen && (
        <ScenarioReviewModal
          onClose={() => setScenarioReviewOpen(false)}
          onRecordResult={(line) => {
            setAuditEvents((events) => [
              {
                id: `AUDIT-SIM-${Date.now().toString().slice(-4)}`,
                time: 'Just now',
                actor: currentRole,
                event: 'Workflow review run',
                record: line,
                badge: 'PUBLISH',
              },
              ...events,
            ]);
            setToast({ message: line });
          }}
        />
      )}

      {/* Toast Feedback */}
      {toast && (
        <div className="toast" role="status" aria-live="polite">
          <IconCheck className="w-4 h-4" />
          <span>{toast.message}</span>
        </div>
      )}
    </main>
    </>
  );
}
