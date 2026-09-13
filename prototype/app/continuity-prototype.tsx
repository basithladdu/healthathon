'use client';

import type { FormEvent } from 'react';
import React, { useMemo, useState, useEffect, useRef } from 'react';
import { LoginScreen, SignupScreen, FamilyLoginScreen, PatientLoginScreen, type FamilyMember, type PatientAccount } from './auth-screens';
import { LandingPage } from './landing-page';
import { AppointmentsPage, initialAppointments, DEMO_TODAY, type Appointment, type AppointmentPatient } from './appointments-page';
import { CareNearMe } from './care-near-me';
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

type View =
  | 'caregiver'
  | 'home'
  | 'guide'
  | 'worklist'
  | 'patients'
  | 'drafts'
  | 'records'
  | 'audit'
  | 'help'
  | 'patient'
  | 'outreach'
  | 'draft'
  | 'verify'
  | 'retrieve'
  | 'appointments'
  | 'my-plan'
  | 'my-timeline'
  | 'care-near-me'
  | 'my-doctors'
  | 'emergency-card'
  | 'my-details'
  | 'consent';

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
  time: string;
  actor: string;
  event: string;
  record: string;
  badge?: 'PUBLISH' | 'OUTREACH' | 'ACCESS' | 'ENROL' | 'VIEW' | 'SCHEDULE' | 'PATIENT';
};

type OutreachRecord = {
  outcome: string;
  note: string;
  nextReviewDate: string;
  actor: string;
};

type DraftFieldKey =
  | 'priorities'
  | 'participants'
  | 'topics'
  | 'openQuestions'
  | 'followUp';

type DraftFieldStatus = 'ready' | 'not-stated' | 'clarify';

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

type RecordState = {
  draftPrepared: boolean;
  draftFields: Record<DraftFieldKey, string>;
  draftStatuses: Record<DraftFieldKey, DraftFieldStatus>;
  verificationChecks: {
    source: boolean;
    ambiguity: boolean;
    inference: boolean;
    reviewDate: boolean;
  };
  authorisation: string;
  attested: boolean;
  versionPublished: boolean;
  retrievalReason: string;
  careRelationship: string;
  retrievalAcknowledged: boolean;
  retrievalUnlocked: boolean;
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

const careJourneyTouchpoint = {
  date: '28 Aug 2026',
  purpose: 'Family conversation',
  owner: 'Anitha Rao',
};

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
  }
> = {
  'CANCER-20418': {
    age: 62,
    team: 'Oncology · Palliative care',
    participant: 'Kavya Raghavan · Daughter (Primary Proxy)',
    phone: 'Synthetic contact held by originating team',
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
    phone: 'Synthetic contact held by originating team',
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
    phone: 'Synthetic contact held by originating team',
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
    phone: 'Synthetic contact held by originating team',
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
    phone: 'Synthetic contact held by originating team',
    stage: 'Stage III Cervical Carcinoma · Synthetic scenario',
    verified: 'Not yet verified',
    dob: '05/02/1979',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity', 'Gynaecologic oncology'],
  },
  'CANCER-20489': {
    age: 64,
    team: 'Gastrointestinal oncology',
    participant: 'Neha Mehta · Spouse',
    phone: 'Synthetic contact held by originating team',
    stage: 'Stage IV Pancreatic Adenocarcinoma · Synthetic scenario',
    verified: 'Not yet verified',
    dob: '17/12/1961',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity', 'Gastrointestinal oncology'],
  },
};

const initialAuditEvents: AuditEvent[] = [
  {
    time: '28 Aug · 09:42',
    actor: 'Dr Sujay',
    event: 'Viewed current verified version',
    record: 'Meera Raghavan · v1',
    badge: 'VIEW',
  },
  {
    time: '26 Aug · 16:15',
    actor: 'Anitha Rao',
    event: 'Recorded outreach and rescheduled follow-up',
    record: 'Meera Raghavan',
    badge: 'OUTREACH',
  },
  {
    time: '21 Aug · 12:08',
    actor: 'Dr Sujay',
    event: 'Verified and published version 1',
    record: 'Meera Raghavan · v1',
    badge: 'PUBLISH',
  },
  {
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
  openQuestions: 'Specific treatment interventions were not stated.',
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
  const patientName =
    initialWorkItems.find((item) => item.hospitalId === hospitalId)?.patient ??
    (hospitalId === 'CANCER-20452'
      ? 'Sana Iyer'
      : hospitalId === 'CANCER-20489'
        ? 'Vikram Mehta'
        : 'Synthetic patient');

  return {
    draftPrepared: false,
    draftFields: isPrimaryScenario
      ? { ...initialDraftFields }
      : {
          priorities: `No source-linked priorities have been prepared for ${patientName}.`,
          participants: 'Not stated.',
          topics: 'Not stated.',
          openQuestions: 'Goals-of-care domains require clinician review.',
          followUp: 'Follow-up plan not yet verified.',
        },
    draftStatuses: isPrimaryScenario
      ? {
          priorities: 'ready',
          participants: 'ready',
          topics: 'ready',
          openQuestions: 'not-stated',
          followUp: 'ready',
        }
      : {
          priorities: 'not-stated',
          participants: 'not-stated',
          topics: 'not-stated',
          openQuestions: 'clarify',
          followUp: 'clarify',
        },
    verificationChecks: {
      source: false,
      ambiguity: false,
      inference: false,
      reviewDate: false,
    },
    authorisation: 'Pending patient review',
    attested: false,
    versionPublished: false,
    retrievalReason: 'Planned consultation / Outpatient review',
    careRelationship: 'Current primary oncology treating team',
    retrievalAcknowledged: false,
    retrievalUnlocked: false,
    conversationCoverage: isPrimaryScenario
      ? { ...initialConversationCoverage }
      : Object.fromEntries(
          Object.keys(initialConversationCoverage).map((key) => [key, 'defer']),
        ) as Record<ConversationDomainKey, ConversationDisposition>,
    latestOutreach: null,
  };
}

const initialRecordStates: Record<string, RecordState> = Object.fromEntries(
  Object.keys(patientProfiles).map((hospitalId) => [hospitalId, createInitialRecordState(hospitalId)]),
);

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
  { view: 'home', label: 'Clinical handoff', icon: IconShieldCheck },
  { view: 'guide', label: 'Guided conversation', icon: IconFileEdit },
  { view: 'worklist', label: 'Follow-up worklist', icon: IconClipboardList },
  { view: 'appointments', label: 'Appointments', icon: IconCalendar },
  { view: 'patients', label: 'Patients', icon: IconUsers },
  { view: 'records', label: 'Verified records', icon: IconShieldCheck },
  { view: 'audit', label: 'Audit log', icon: IconHistory },
  { view: 'help', label: 'Plain-language help', icon: IconInfo },
];

const patientNavItems: Array<{ view: View; label: string; icon: React.FC<{ className?: string }> }> = [
  { view: 'my-plan', label: 'My care plan', icon: IconShieldCheck },
  { view: 'my-timeline', label: 'My care journey', icon: IconHistory },
  { view: 'care-near-me', label: 'Care near me', icon: IconMapPin },
  { view: 'my-doctors', label: 'My doctors', icon: IconUsers },
  { view: 'emergency-card', label: 'Emergency card', icon: IconIdCard },
  { view: 'my-details', label: 'My details', icon: IconFileEdit },
  { view: 'consent', label: 'Consent', icon: IconCheck },
  { view: 'help', label: 'Plain-language help', icon: IconInfo },
];

const familyNavItems: Array<{ view: View; label: string; icon: React.FC<{ className?: string }> }> = [
  { view: 'caregiver', label: 'Care journey', icon: IconUsers },
  { view: 'care-near-me', label: 'Care near me', icon: IconMapPin },
  { view: 'my-doctors', label: 'Care team', icon: IconUsers },
  { view: 'emergency-card', label: 'Emergency card', icon: IconIdCard },
  { view: 'help', label: 'Plain-language help', icon: IconInfo },
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
  const [view, setView] = useState<View>('home');
  const [currentRole, setCurrentRole] = useState<UserRole>('Dr Sujay · Clinical lead');
  const [authScreen, setAuthScreen] = useState<'landing' | 'login' | 'signup' | 'family' | 'patient' | null>('landing');
  const [sessionType, setSessionType] = useState<'care-team' | 'family' | 'patient'>('care-team');
  const [familyMember, setFamilyMember] = useState<FamilyMember | null>(null);
  const [patientAccount, setPatientAccount] = useState<PatientAccount | null>(null);
  const isFamilySession = sessionType === 'family';
  const isPatientSession = sessionType === 'patient';
  const [workItems, setWorkItems] = useState(initialWorkItems);
  const [appointments, setAppointments] = useState<Appointment[]>(initialAppointments);
  const [selectedId, setSelectedId] = useState('CANCER-20418');
  const [recordStates, setRecordStates] =
    useState<Record<string, RecordState>>(initialRecordStates);
  const [activeFilter, setActiveFilter] =
    useState<(typeof filters)[number]>('All');
  const [query, setQuery] = useState('');
  const [enrolOpen, setEnrolOpen] = useState(false);
  const [diffOpen, setDiffOpen] = useState(false);
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
  const [toast, setToast] = useState('');
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
  const profileTimelineRef = useRef<HTMLOListElement>(null);
  const enrolDialogRef = useRef<HTMLElement>(null);
  const diffDialogRef = useRef<HTMLElement>(null);
  const retrievedRecordHeadingRef = useRef<HTMLHeadingElement>(null);

  const recordState = recordStates[selectedId] ?? createInitialRecordState(selectedId);
  const primaryRecordState =
    recordStates['CANCER-20418'] ?? createInitialRecordState('CANCER-20418');
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

  function enterCareTeam(role: UserRole) {
    setSessionType('care-team');
    setFamilyMember(null);
    setPatientAccount(null);
    setCurrentRole(role);
    setView('home');
    setAuthScreen(null);
  }

  function enterFamily(member: FamilyMember) {
    setSessionType('family');
    setFamilyMember(member);
    setPatientAccount(null);
    setView('caregiver');
    setAuthScreen(null);
  }

  function enterPatient(account: PatientAccount) {
    setSessionType('patient');
    setFamilyMember(null);
    setPatientAccount(account);
    selectPatient(account.hospitalId);
    setView('my-plan');
    setAuthScreen(null);
  }

  function signOut() {
    setSessionType('care-team');
    setFamilyMember(null);
    setPatientAccount(null);
    setView('home');
    setAuthScreen('landing');
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
    updateSelectedRecord((current) => ({
      ...current,
      draftFields:
        typeof value === 'function' ? value(current.draftFields) : value,
    }));
  }

  function setDraftStatuses(
    value: React.SetStateAction<Record<DraftFieldKey, DraftFieldStatus>>,
  ) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => ({
      ...current,
      draftStatuses:
        typeof value === 'function' ? value(current.draftStatuses) : value,
    }));
  }

  function setVerificationChecks(
    value: React.SetStateAction<RecordState['verificationChecks']>,
  ) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => ({
      ...current,
      verificationChecks:
        typeof value === 'function' ? value(current.verificationChecks) : value,
    }));
  }

  function setAuthorisation(value: string) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => ({ ...current, authorisation: value }));
  }

  function setAttested(value: boolean) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => ({ ...current, attested: value }));
  }

  function setVersionPublished(value: boolean) {
    updateSelectedRecord((current) => ({ ...current, versionPublished: value }));
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

  function setRetrievalUnlocked(value: boolean) {
    updateSelectedRecord((current) => ({ ...current, retrievalUnlocked: value }));
  }

  function setConversationDisposition(
    key: ConversationDomainKey,
    value: ConversationDisposition,
  ) {
    if (!isTreatingPhysician) return;
    updateSelectedRecord((current) => ({
      ...current,
      conversationCoverage: { ...current.conversationCoverage, [key]: value },
    }));
  }

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
    phone: 'Synthetic contact held by originating team',
    stage: 'Oncology Cohort',
    verified: 'Not yet verified',
    dob: 'Not recorded',
    sex: 'Not recorded',
    programs: ['Goals-of-care continuity'],
  };

  const portalPatientId = isPatientSession
    ? (patientAccount?.hospitalId ?? 'CANCER-20418')
    : (familyMember?.patientId ?? 'CANCER-20418');
  const portalPatientName =
    workItems.find((item) => item.hospitalId === portalPatientId)?.patient ??
    (isPatientSession ? patientAccount?.name : familyMember?.name) ??
    'Patient';

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
          ? { ...item, status: 'Awaiting review', lastActivity: 'Patient requested a review of the care plan' }
          : item,
      ),
    );
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit('Requested a review of the care plan', patientName, 'PATIENT', patientName);
    notify('Your review request was sent to your care team.');
  }

  function signConsent(id: string, input: { typedName: string; declaration: 'self' | 'delegated' }) {
    const versionLabel = planVersionLabel(id);
    setConsentById((state) => ({
      ...state,
      [id]: {
        versionLabel,
        typedName: input.typedName,
        declaration: input.declaration,
        signedAt: '28 Aug 2026 · 10:04',
      },
    }));
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit(`Gave consent to ${versionLabel} (simulated typed signature)`, patientName, 'PATIENT', patientName);
    notify('Your consent was recorded.');
  }

  function issueCard(id: string) {
    setCardIssuedById((state) => ({ ...state, [id]: '28 Aug 2026' }));
    const patientName = workItems.find((w) => w.hospitalId === id)?.patient ?? '';
    addAudit('Issued their emergency card', patientName, 'PATIENT', patientName);
    notify('Your emergency card was issued.');
  }

  function planVersionLabel(id: string): string {
    const state = recordStates[id];
    if (state?.versionPublished) return 'Version 2';
    if ((patientProfiles[id]?.verified ?? '').includes('2026')) return 'Version 1';
    return 'No released version';
  }

  function planFieldsFor(id: string): Array<{ label: string; value: string }> {
    const state = recordStates[id];
    if (id === 'CANCER-20418') {
      const released = state?.versionPublished ? state.draftFields : v1Fields;
      return fieldConfig.map((f) => ({ label: f.label, value: released[f.key] }));
    }
    if (patientHasApprovedSummary(id)) {
      return [
        {
          label: 'Summary',
          value: 'Your verified summary is held by your care team and is not loaded in this demonstration.',
        },
      ];
    }
    return [];
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
      .filter((e) => e.record.includes(patientName))
      .forEach((e, index) => {
        const match = /^(\d{1,2}) (\w{3}) · (\d{2}):(\d{2})$/.exec(e.time);
        const monthMap: Record<string, number> = {
          Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11,
        };
        const sortKey = match
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
          id: `audit-${index}-${e.time}`,
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
    ({ key }) => draftStatuses[key] === 'clarify' || !draftFields[key].trim(),
  );
  const draftReleaseReady = draftPrepared && draftFieldBlockers.length === 0;
  const verificationPassedCount = Object.values(verificationChecks).filter(Boolean).length;
  const acknowledgementComplete =
    authorisation !== 'Pending patient review' && authorisation !== 'Declined';
  const verificationReady =
    draftReleaseReady &&
    verificationPassedCount === 4 &&
    attested &&
    acknowledgementComplete &&
    currentRole.startsWith('Dr Sujay') &&
    !versionPublished;

  function navigate(nextView: View) {
    if (isPatientSession) {
      const allowed: View[] = ['my-plan', 'my-timeline', 'care-near-me', 'my-doctors', 'emergency-card', 'my-details', 'consent', 'help'];
      if (!allowed.includes(nextView)) {
        notify('This page is for the care team.');
        return;
      }
    }
    if (isFamilySession) {
      const allowed: View[] = ['caregiver', 'care-near-me', 'my-doctors', 'emergency-card', 'help'];
      if (!allowed.includes(nextView)) {
        notify('Family access is read-only. The care-team workspace needs a clinician sign-in.');
        return;
      }
    }
    if ((nextView === 'draft' || nextView === 'verify') && !isTreatingPhysician) {
      notify('Only the treating physician can prepare or verify a structured summary.');
      return;
    }
    if (nextView === 'outreach' && !isCareCoordinator) {
      notify('Only the simulated care coordinator can record outreach.');
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
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  // One timer at a time: otherwise an older toast's timeout clears a newer
  // toast early whenever two actions land within a few seconds.
  const toastTimerRef = useRef<number | null>(null);

  function notify(message: string) {
    setToast(message);
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => {
      setToast('');
      toastTimerRef.current = null;
    }, 3500);
  }

  function selectPatient(hospitalId: string) {
    updateRecordState(hospitalId, (current) => ({
      ...current,
      retrievalAcknowledged: false,
      retrievalUnlocked: false,
    }));
    setSelectedId(hospitalId);
  }

  function openPrimaryVersionComparison() {
    selectPatient('CANCER-20418');
    setDiffOpen(true);
  }

  function beginRetrievalFor(hospitalId: string) {
    if (!currentRole.startsWith('Dr')) {
      notify('A simulated physician role is required to retrieve a verified clinical record.');
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
    }));
    navigate('retrieve');
  }

  function beginEmergencyRetrieval() {
    beginRetrievalFor('CANCER-20418');
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
    return Boolean(recordStates[id]?.versionPublished) || (patientProfiles[id]?.verified ?? '').includes('2026');
  }

  function patientSummaryLabel(id: string): string {
    if (recordStates[id]?.versionPublished) return 'Version 2 · 28 Aug 2026';
    if ((patientProfiles[id]?.verified ?? '').includes('2026')) return `Version 1 · ${patientProfiles[id].verified}`;
    return 'No approved summary';
  }


  function addAudit(
    event: string,
    record = `${selectedItem.patient}`,
    badge?: AuditEvent['badge'],
    actor?: string,
  ) {
    setAuditEvents((events) => [
      {
        time: '28 Aug · 10:04',
        actor: actor ?? currentRole.split(' · ')[0],
        event,
        record,
        badge,
      },
      ...events,
    ]);
  }

  function copySummaryToClipboard() {
    const releasedFields = versionPublished ? draftFields : v1Fields;
    const summaryText = `[GOALS OF CARE CONTINUITY SUMMARY]\nPatient: ${selectedItem.patient} (${selectedItem.hospitalId})\nStatus: ${versionPublished ? 'Version 2 (Verified 28 Aug 2026)' : 'Version 1 (Verified 21 Aug 2026)'}\nStated Priorities: ${releasedFields.priorities}\nPeople Present: ${releasedFields.participants}\nTopics Discussed: ${releasedFields.topics}\nUnresolved Questions: ${releasedFields.openQuestions}\nFollow-Up: ${releasedFields.followUp}`;

    if (navigator.clipboard) {
      navigator.clipboard.writeText(summaryText);
      notify('Verified summary copied to clipboard.');
    } else {
      notify('Summary text ready.');
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
    notify(`Follow-up task created for synthetic patient ${candidate.patient}.`);
  }

  function saveOutreach(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!isCareCoordinator) {
      notify('Only the simulated care coordinator can record outreach.');
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
    notify('Deterministic draft prepared from the synthetic source note.');
  }

  function setDraftFieldStatus(key: DraftFieldKey, status: DraftFieldStatus) {
    if (!isTreatingPhysician) return;
    setDraftStatuses((statuses) => ({ ...statuses, [key]: status }));
  }

  function publishVersion() {
    if (!isTreatingPhysician || !verificationReady) return;
    setVersionPublished(true);
    setWorkItems((items) =>
      items.map((item) =>
        item.hospitalId === selectedId
          ? {
              ...item,
              status: 'Completed',
              lastActivity: 'Version 2 verified',
            }
          : item,
      ),
    );
    addAudit('Verified and published version 2', `${selectedItem.patient} · v2`, 'PUBLISH');
    notify('Version 2 published. Version 1 remains in the append-only demo history.');
    navigate('patient');
  }

  function openRetrievedRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentRole.startsWith('Dr')) {
      notify('Switch to a simulated physician role to retrieve this clinical record.');
      return;
    }
    if (!retrievalReason || !careRelationship) {
      notify('Select an access purpose and care relationship before continuing.');
      return;
    }
    const isBreakGlassAccess = retrievalReason === 'Emergency hand-off retrieval (Break-glass)';
    if (
      isBreakGlassAccess &&
      (!currentRole.startsWith('Dr Isha') || careRelationship !== 'Emergency receiving physician')
    ) {
      notify('Break-glass access requires the simulated emergency physician role and emergency receiving relationship.');
      return;
    }
    if (!retrievalAcknowledged) {
      notify('Confirm the retrieval boundary before viewing the verified record.');
      return;
    }
    setRetrievalUnlocked(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    addAudit(
      `Viewed latest verified summary · ${isBreakGlassAccess ? 'Break-glass' : 'Routine'} access · ${retrievalReason}`,
      `${selectedItem.patient} · ${versionPublished ? 'v2' : 'v1'}`,
      'ACCESS',
    );
    notify('Access justification recorded in the simulated audit trail.');
  }

  function renderPageHeading(
    eyebrow: string,
    title: string,
    action?: React.ReactNode,
  ) {
    return (
      <div className="page-heading compact-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
        </div>
        {action}
      </div>
    );
  }

  function renderCareJourney() {
    const releasedFields = primaryRecordState.versionPublished
      ? primaryRecordState.draftFields
      : v1Fields;
    const releasedVersion = primaryRecordState.versionPublished ? 'Version 2' : 'Version 1';
    const activeCoverageZone =
      coverageZones.find((zone) => zone.id === coverageZoneId) ?? coverageZones[0];

    return (
      <section className="care-journey" aria-labelledby="care-journey-title">
        <article className="care-journey-hero">
          <div className="care-journey-copy">
            <div className="care-journey-meta">
              <span className="care-journey-kicker">Care journey companion</span>
              <span className="care-journey-demo">Simulated caregiver view</span>
            </div>
            <h1 id="care-journey-title">Meera’s next steps, all in one place.</h1>
            <p className="care-journey-lede">
              A calm, shared view of the conversation her care team has reviewed, what is next, and who can help prepare for it.
            </p>
            {isFamilySession ? (
              <p className="care-journey-signed-in">
                Signed in as <strong>{familyMember?.name}</strong> · {familyMember?.relationship}
              </p>
            ) : (
              <div className="care-journey-actions">
                <button className="primary-button" type="button" onClick={() => navigate('home')}>
                  <IconUsers className="w-4 h-4" />
                  Open care-team workspace
                </button>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => openDemoRoleWorkflow('Dr Isha Menon · Emergency physician')}
                >
                  Emergency clinician demo
                  <IconChevronRight className="w-4 h-4" />
                </button>
              </div>
            )}
            <div className="care-journey-readonly" role="note">
              <IconLock className="w-4 h-4" />
              <span>Read-only by design. Care-team updates are released only after physician review.</span>
            </div>
          </div>

          <figure className="care-journey-illustration">
            {/* The Vinext local runtime cannot hydrate next/image for this bundled public asset. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/care-conversation.jpg"
              alt="Illustration of a patient seated across a desk from a clinician, beside a large checklist of reviewed items"
              width={736}
              height={751}
              loading="eager"
            />
            <figcaption>Continuity starts with a shared conversation.</figcaption>
          </figure>
        </article>

        <div className="care-journey-primary-grid">
          <article className="care-journey-card care-journey-next">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-warm" aria-hidden="true"><IconHistory className="w-5 h-5" /></span>
              <div>
                <p className="care-journey-label">Next touchpoint</p>
                <h2>Next-visit checklist</h2>
              </div>
            </div>
            <div className="care-journey-date-row">
              <strong>{careJourneyTouchpoint.date}</strong>
              <span>{careJourneyTouchpoint.purpose}<br />Continuity owner · {careJourneyTouchpoint.owner}</span>
            </div>
            <ul className="care-journey-prep-list" aria-label="Plain-language next-visit checklist">
              <li><IconCheck className="w-3.5 h-3.5" />Write down the questions you want the care team to answer.</li>
              <li><IconCheck className="w-3.5 h-3.5" />Confirm who Meera wants included in the conversation.</li>
              <li><IconCheck className="w-3.5 h-3.5" />Tell Anitha Rao if the time or people joining need to change.</li>
            </ul>
          </article>

          <article className="care-journey-card care-journey-summary">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-green" aria-hidden="true"><IconShieldCheck className="w-5 h-5" /></span>
              <div>
                <p className="care-journey-label">Clinician-reviewed release</p>
                <h2>What the care team has confirmed</h2>
              </div>
              <span className="care-journey-version">{releasedVersion}</span>
            </div>
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
                Released after physician review by <strong>Dr Sujay</strong> on {releasedVersion === 'Version 2' ? '28 Aug 2026' : '21 Aug 2026'}.
                A new conversation creates the next version; this one is never edited in place.
              </span>
            </div>
          </article>
        </div>

        <div className="care-journey-secondary-grid">
          <article className="care-journey-card care-journey-team-card">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-indigo" aria-hidden="true"><IconUsers className="w-5 h-5" /></span>
              <div>
                <p className="care-journey-label">Your care team</p>
                <h2>People coordinating the next step</h2>
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

          <article className="care-journey-card care-journey-family-card">
            <div className="care-journey-card-heading">
              <span className="care-journey-icon care-journey-icon-cream" aria-hidden="true"><IconCheck className="w-5 h-5" /></span>
              <div>
                <p className="care-journey-label">Family preparation</p>
                <h2>Keep the next conversation focused</h2>
              </div>
            </div>
            <p className="care-journey-card-copy">Family members can keep questions together and confirm who Meera wants involved. This companion cannot edit a clinical record.</p>
            <button className="care-journey-text-button" type="button" onClick={() => navigate('help')}>
              How clinician review protects context <IconChevronRight className="w-4 h-4" />
            </button>
          </article>
        </div>

        <article className="care-journey-card care-journey-support-card" aria-labelledby="care-journey-support-title">
          <div className="care-journey-card-heading">
            <span className="care-journey-icon care-journey-icon-indigo" aria-hidden="true"><IconInfo className="w-5 h-5" /></span>
            <div>
              <p className="care-journey-label">Support resources</p>
              <h2 id="care-journey-support-title">Ask the care team what is available</h2>
            </div>
            <span className="care-journey-resource-status">Seeded · unverified</span>
          </div>
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
        </article>

        <div className="care-journey-boundary" role="note">
          <IconInfo className="w-4 h-4" />
          <p><strong>Demonstration boundary:</strong> Meera, Kavya, the care team, dates, and summary are fictional. This screen has no live login, messaging, medical advice, or record integration.</p>
        </div>
      </section>
    );
  }

  function renderHome() {
    const activeFollowUps = Math.max(0, counts.all - counts.completed);

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
                aria-label={`Retrieve verified record for ${item.patient}`}
              >
                <IconLock className="w-3.5 h-3.5" />
                Retrieve
              </button>
            )}
          </div>
        </li>
      );
    }

    return (
      <>
        <section className="command-intro">
          <div className="command-intro-copy">
            <p className="eyebrow">Continuity handoff</p>
            <h1>Make every handoff easier to act on.</h1>
            <p className="command-support">
              Start with the next follow-up, preserve what was said, and retrieve a verified summary only when the care context calls for it.
            </p>
            <div className="demo-session-summary" aria-label={`Active simulated role: ${currentRole}`}>
              <span className="demo-session-role"><span className="demo-session-dot" aria-hidden="true" />Demo role: <strong>{currentRole.split(' · ')[0]}</strong></span>
              <span>Browser-local demonstration</span>
            </div>
          </div>
          <div className="command-intro-actions">
            <button className="secondary-button command-help" type="button" onClick={() => navigate('help')}>
              <IconInfo className="w-4 h-4" />
              Workflow help
            </button>
          </div>
        </section>

        <section className="detail-card th-card" aria-labelledby="th-title">
          <div className="th-header">
            <div>
              <p className="eyebrow">Today's handoffs</p>
              <h2 id="th-title">Patients who need attention</h2>
              <p className="th-note">Ordered by follow-up status, then due date. This list does not rank clinical urgency.</p>
            </div>
            <div className="th-header-right">
              <span className="th-count"><strong>{openItems.length}</strong> open</span>
              <button className="text-button" type="button" onClick={() => navigate('worklist')}>
                Open all work →
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

          <p className="th-footnote">
            Approved summaries are for clinical review only. Retrieving one asks for a reason and is recorded in the audit log.
          </p>
        </section>

        <section className="today-overview" aria-labelledby="today-overview-title">
          <div className="section-heading">
            <div>
              <span className="document-kicker">Worklist overview</span>
              <h2 id="today-overview-title">What needs attention today</h2>
            </div>
            <div className="today-overview-total">
              <strong>{activeFollowUps}</strong>
              <span>active follow-ups</span>
              <button className="text-button" type="button" onClick={() => navigate('worklist')}>
                Open all work →
              </button>
            </div>
          </div>
          <div className="today-stat-grid">
            <button className="today-stat urgent" type="button" onClick={() => { setActiveFilter('Due today'); navigate('worklist'); }}>
              <strong>{counts.dueToday}</strong>
              <span>Due today</span>
            </button>
            <button className="today-stat overdue" type="button" onClick={() => { setActiveFilter('Overdue'); navigate('worklist'); }}>
              <strong>{counts.overdue}</strong>
              <span>Overdue</span>
            </button>
            <button className="today-stat" type="button" onClick={() => { setActiveFilter('Awaiting review'); navigate('worklist'); }}>
              <strong>{counts.awaiting}</strong>
              <span>Awaiting review</span>
            </button>
            <button className="today-stat" type="button" onClick={() => { setActiveFilter('Awaiting outreach'); navigate('worklist'); }}>
              <strong>{counts.awaitingOutreach}</strong>
              <span>Awaiting outreach</span>
            </button>
          </div>
          <p className="today-overview-boundary">
            <IconInfo className="w-3.5 h-3.5" />
            <span>
              Seeded workflow counts, not clinical outcomes. They reset on reload; no live EHR,
              messaging, or analytics connection is active.
            </span>
          </p>
        </section>

        <section className="role-route" aria-labelledby="role-route-title">
          <div className="section-heading">
            <div>
              <span className="document-kicker">Simulated role access</span>
              <h2 id="role-route-title">Choose the role you want to demonstrate</h2>
            </div>
          </div>
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
              <strong>Retrieve a verified handoff</strong>
              <small>Record the clinical reason before opening the most recent verified version.</small>
            </button>
          </div>
          <div className="role-route-boundary" role="note">
            <IconUsers className="w-4 h-4" />
            <span><strong>Patient and family access:</strong> families use the separate family door on the sign-in page, which is simulated. Preferences are still captured through the clinician-led conversation, and no independently authenticated patient portal is part of this demonstration.</span>
          </div>
        </section>
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
          ← Back to clinical handoff
        </button>
        {renderPageHeading(
          'Clinician-owned discussion framework',
          `Guided conversation · ${selectedItem.patient}`,
          <StatusPill status={`${discussedCount}/${conversationDomains.length} discussed`} />,
        )}

        <div className="conversation-layout">
          <section className="detail-card conversation-checklist">
            <div className="protocol-notice">
              <IconInfo className="w-4 h-4 flex-shrink-0" />
              <span>
                {isTreatingPhysician
                  ? 'Demonstration framework based on the clinician meeting. Final wording and mandatory domains require approval from the clinical leads.'
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
                      disabled={!isTreatingPhysician}
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
              <h2>Preloaded synthetic source</h2>
              <dl className="definition-list">
                <div><dt>Participants</dt><dd>Patient, daughter, treating physician</dd></div>
                <div><dt>Input</dt><dd>Clinician-entered source note</dd></div>
                <div><dt>Stored with</dt><dd>Every released version</dd></div>
                <div><dt>Audio</dt><dd>Not recorded or retained</dd></div>
                <div><dt>Extraction</dt><dd>Deterministic demonstration</dd></div>
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
                {isTreatingPhysician ? 'Continue to source-linked draft' : 'Treating physician update required'}
              </button>
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
              <span className="environment-dot" /> Oncology Continuity Loop · Safe Demonstration
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
    const hasVerifiedRecord =
      versionPublished || selectedProfile.verified.includes('2026');
    const summaryFieldsAvailable = isMeera;
    const releasedFields = versionPublished ? draftFields : v1Fields;
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
              body: `${selectedItem.purpose} was recorded for this synthetic patient.`,
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
      ...(versionPublished
        ? [
            {
              date: '28 Aug',
              title: 'Version 2 verified & published',
              body: 'Clinician review completed; Version 2 added to the append-only demonstration history.',
            },
          ]
        : []),
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
        label: isMeera ? 'Version 2 verified and released' : 'Summary verified and released',
        done: isMeera ? versionPublished : hasVerifiedRecord,
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
        done: versionPublished ? acknowledgementComplete : hasVerifiedRecord,
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
            label: 'Retrieve verified record',
            onClick: () => navigate('retrieve'),
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
              onClick={() => navigate('retrieve')}
            >
              <IconLock className="w-3.5 h-3.5" />
              Retrieve verified record
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
                    Synthetic record. This profile coordinates follow-up and documents a
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
                      No guided conversation is loaded for this synthetic patient.
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
                    disabled={!isMeera || !isTreatingPhysician}
                    onClick={() => {
                      if (isMeera) navigate('draft');
                    }}
                  >
                    <IconFileEdit className="w-4 h-4" />
                    {!isMeera
                      ? 'Source note unavailable'
                      : isTreatingPhysician
                        ? 'Prepare summary draft'
                        : 'Physician update required'}
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
                    <span>
                      {versionPublished
                        ? 'Version 2 · Verified 28 Aug 2026 by Dr Sujay'
                        : hasVerifiedRecord
                          ? `Version 1 · Verified ${selectedProfile.verified} by Dr Sujay`
                          : 'A clinician-reviewed draft is required before retrieval'}
                    </span>
                  </div>
                  <div className="flex items-center gap-3">
                    {versionPublished && (
                      <button className="text-button" type="button" onClick={() => setDiffOpen(true)}>
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
                      disabled={!isMeera || !isTreatingPhysician}
                      onClick={() => {
                        if (isMeera) navigate('draft');
                      }}
                    >
                      {!isMeera
                        ? 'Source note unavailable'
                        : isTreatingPhysician
                          ? 'Update version'
                          : 'Physician update required'}
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
                      <p>Version metadata exists for this synthetic patient, but its source-linked field content is not loaded in this demonstration.</p>
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
                    <p>This synthetic patient has no released goals-of-care summary.</p>
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
                    <span>Append-only version history in this demonstration</span>
                  </div>
                </div>
                {hasVerifiedRecord ? (
                  <div className="version-list">
                    {versionPublished && (
                    <div className="version-row current">
                      <div>
                        <strong>Version 2 (Current)</strong>
                        <span>28 Aug 2026 · Dr Sujay</span>
                        <span>Acknowledgement: {authorisation}</span>
                      </div>
                      <StatusPill status="Current" />
                    </div>
                  )}
                    <div className={`version-row ${versionPublished ? '' : 'current'}`}>
                      <div>
                        <strong>Version 1 {versionPublished ? '(Superseded)' : '(Current)'}</strong>
                        <span>{selectedProfile.verified} · Dr Sujay</span>
                        <span>Acknowledgement: Verbal acknowledgement recorded by care team</span>
                      </div>
                      <StatusPill status={versionPublished ? 'Superseded' : 'Current'} />
                    </div>
                  </div>
                ) : (
                  <div className="compact-empty-state">No released versions.</div>
                )}
                <div className="stacked-actions">
                  {versionPublished && (
                    <button className="secondary-button" type="button" onClick={() => setDiffOpen(true)}>
                      <IconColumns className="w-4 h-4" />
                      Compare v1 vs v2
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
                    <span>Kept honest for this demonstration</span>
                  </div>
                  <StatusPill status="Not connected" />
                </div>
                <dl className="identity-sharing-list">
                  <div>
                    <dt>ABHA Number</dt>
                    <dd>Not linked in this demo</dd>
                  </div>
                  <div>
                    <dt>Sharing basis</dt>
                    <dd>Consent + retrieval purpose</dd>
                  </div>
                  <div>
                    <dt>Data location</dt>
                    <dd>Browser-local synthetic record</dd>
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
                    {versionPublished
                      ? 'Version 2 (28 Aug 2026)'
                      : hasVerifiedRecord
                        ? `Version 1 (${selectedProfile.verified})`
                        : 'No released version'}
                  </dd>
                </div>
                <div>
                  <dt>Access tier</dt>
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
          ← Back to patient record
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
    const sourceContextAvailable = selectedId === 'CANCER-20418';
    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('patient')}>
          ← Back to patient record
        </button>
        {renderPageHeading(
          'Clinician Documentation Studio',
          'Create structured conversation summary',
          draftPrepared ? (
            <button className="primary-button" type="button" onClick={() => navigate('verify')}>
              Continue to verification checklist →
            </button>
          ) : undefined,
        )}
        <div className="draft-layout">
          <section className="detail-card source-note-panel">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Source clinician note</h2>
                <span>Entered with patient consent · 28 Aug 2026, 09:54</span>
              </div>
            </div>

            {sourceContextAvailable ? (
              <>
                <div className="form-field">
                  <span>Interactive source text (hover to inspect provenance)</span>
                  <div className="source-text-interactive">
                    {sourceSnippets.map((snippet) => (
                      <span
                        key={snippet.key}
                        className={`source-snippet ${highlightedField === snippet.key ? 'active-highlight' : ''}`}
                        role="button"
                        tabIndex={0}
                        aria-label={`Highlight source excerpt: ${snippet.text}`}
                        onMouseEnter={() => setHighlightedField(snippet.key)}
                        onMouseLeave={() => setHighlightedField(null)}
                        onFocus={() => setHighlightedField(snippet.key)}
                        onBlur={() => setHighlightedField(null)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            setHighlightedField(snippet.key);
                          }
                        }}
                      >
                        {snippet.text}{' '}
                      </span>
                    ))}
                  </div>
                </div>

                {!draftPrepared ? (
                  <button
                    className="primary-button full-width"
                    type="button"
                    disabled={!isTreatingPhysician}
                    onClick={prepareDraft}
                  >
                    <IconFileEdit className="w-4 h-4" />
                    Extract structured draft from source
                  </button>
                ) : (
                  <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-900 text-xs mt-3 flex items-center gap-2">
                    <IconCheck className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                    <span>Structured fields extracted and linked to source sentences.</span>
                  </div>
                )}
                <div className="record-boundary">
                  <IconInfo className="w-4 h-4 flex-shrink-0" />
                  <span>
                    Deterministic extraction uses only explicitly stated facts in the source note. No clinical inferences or prognostic estimates are made.
                  </span>
                </div>
              </>
            ) : (
              <div className="record-boundary">
                <IconInfo className="w-4 h-4 flex-shrink-0" />
                <span>
                  The source-linked clinician note is not loaded for {selectedItem.patient} in this demonstration. No source text or structured field value is inferred.
                </span>
              </div>
            )}
          </section>

          <section className="detail-card structured-draft-panel">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Structured draft fields</h2>
                <span>{draftPrepared ? 'Review and edit each domain before verification' : 'Awaiting source extraction'}</span>
              </div>
              {draftPrepared && <StatusPill status="Draft" />}
            </div>
            {!sourceContextAvailable ? (
              <div className="draft-placeholder">
                <IconInfo className="w-8 h-8 text-gray-400 mx-auto mb-2" />
                <strong>Source-linked draft unavailable</strong>
                <p>No patient-scoped guided conversation is loaded for this record in this demonstration.</p>
              </div>
            ) : !draftPrepared ? (
              <div className="draft-placeholder">
                <IconFileEdit className="w-8 h-8 text-gray-400 mx-auto mb-2" />
                <strong>No structured fields extracted yet</strong>
                <p>Click &ldquo;Extract structured draft from source&rdquo; to generate verifiable fields.</p>
              </div>
            ) : (
              <div className="draft-fields">
                {fieldConfig.map((field) => (
                  <article
                    className={`draft-field status-${draftStatuses[field.key]} ${highlightedField === field.key ? 'active-field' : ''}`}
                    key={field.key}
                    onMouseEnter={() => setHighlightedField(field.key)}
                    onMouseLeave={() => setHighlightedField(null)}
                  >
                    <div className="draft-field-heading">
                      <label htmlFor={`field-${field.key}`}>{field.label}</label>
                      <span className={`field-status-tag ${draftStatuses[field.key]}`}>
                        {draftStatuses[field.key] === 'ready' && <><IconCheck className="w-3 h-3" /> Source linked</>}
                        {draftStatuses[field.key] === 'not-stated' && <>Marked not stated</>}
                        {draftStatuses[field.key] === 'clarify' && <>Needs clarification</>}
                      </span>
                    </div>
                    <textarea
                      id={`field-${field.key}`}
                      rows={3}
                      value={draftFields[field.key]}
                      readOnly={!isTreatingPhysician}
                      onChange={(event) =>
                        setDraftFields((fields) => ({ ...fields, [field.key]: event.target.value }))
                      }
                    />
                    <blockquote
                      role="button"
                      tabIndex={0}
                      onClick={() => setHighlightedField(field.key)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          setHighlightedField(field.key);
                        }
                      }}
                    >
                      <strong>Provenance:</strong> {field.provenance} (Click to highlight source)
                    </blockquote>
                    <div className="field-actions">
                      <button
                        type="button"
                        disabled={!isTreatingPhysician}
                        onClick={() => setDraftFieldStatus(field.key, 'not-stated')}
                      >
                        Mark not stated
                      </button>
                      <button
                        type="button"
                        disabled={!isTreatingPhysician}
                        onClick={() => setDraftFieldStatus(field.key, 'clarify')}
                      >
                        Mark for clarification
                      </button>
                      {draftStatuses[field.key] !== 'ready' && (
                        <button
                          type="button"
                          disabled={!isTreatingPhysician}
                          onClick={() => setDraftFieldStatus(field.key, 'ready')}
                        >
                          Restore source-linked
                        </button>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
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
      versionPublished && 'Version 2 has already been released for this record.',
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
        {renderPageHeading('Physician-Controlled Release Gate', 'Verify and publish conversation summary')}

        <div className="verification-layout">
          <section className="detail-card verification-card">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Clinical verification checklist</h2>
                <span>Mandatory safety validation ({verificationPassedCount}/4 checks passed)</span>
              </div>
            </div>

            <div className="check-list">
              {checkItems.map((item) => (
                <label className="check-row" key={item.key}>
                  <input
                    type="checkbox"
                    checked={verificationChecks[item.key]}
                    disabled={!isTreatingPhysician}
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
                disabled={!isTreatingPhysician}
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
              {consentById[selectedId] ? (
                <span>
                  Patient consent recorded in the patient portal: {consentById[selectedId].typedName} ·{' '}
                  {consentById[selectedId].declaration === 'self'
                    ? 'made these choices themselves'
                    : 'delegated to their decision-maker'}{' '}
                  · {consentById[selectedId].signedAt} · {consentById[selectedId].versionLabel}. Simulated
                  typed signature.
                </span>
              ) : (
                <span>No patient-portal consent recorded for this patient yet.</span>
              )}
            </div>

            <label className="attestation-row">
              <input
                type="checkbox"
                checked={attested}
                disabled={!isTreatingPhysician}
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
                disabled={!isTreatingPhysician}
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
                {versionPublished ? 'Version 2 published' : 'Verify and publish Version 2'}
              </button>
            </div>
          </section>

          <aside className="detail-card verification-preview">
            <div className="card-header">
              <div className="card-header-titles">
                <h2>Append-only release preview</h2>
                <span>{selectedItem.patient} · Version 2</span>
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
                Publishing adds a new version to this demonstration history. Future discussions create another version instead of editing the released record.
              </span>
            </div>
          </aside>
        </div>
      </>
    );
  }

  function renderRetrieve() {
    const sourceContextAvailable = selectedId === 'CANCER-20418';
    const releasedFields = versionPublished ? draftFields : v1Fields;
    const isBreakGlassAccess = retrievalReason === 'Emergency hand-off retrieval (Break-glass)';
    const breakGlassRequirementsMet =
      !isBreakGlassAccess ||
      (currentRole.startsWith('Dr Isha') && careRelationship === 'Emergency receiving physician');
    const retrievalGateReady =
      currentRole.startsWith('Dr') &&
      Boolean(retrievalReason) &&
      Boolean(careRelationship) &&
      breakGlassRequirementsMet &&
      retrievalAcknowledged;
    const retrievalGateBlocker = !currentRole.startsWith('Dr')
      ? 'A simulated physician role is required to retrieve a verified clinical record.'
      : !retrievalReason || !careRelationship
        ? 'Select an access purpose and care relationship before continuing.'
        : !breakGlassRequirementsMet
          ? 'Break-glass access is limited to the simulated emergency physician role and emergency receiving relationship.'
          : !retrievalAcknowledged
            ? 'Confirm the retrieval boundary before viewing the record.'
            : 'Access gate complete. Viewing this record will be logged in the simulated audit trail.';
    const selectedInitials = selectedItem.patient
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
    return (
      <>
        <button className="back-button" type="button" onClick={() => navigate('patient')}>
          ← Back to patient record
        </button>
        {renderPageHeading('Controlled Clinical Hand-Off Gate', 'Retrieve verified record')}

        {!retrievalUnlocked ? (
          <div className="retrieval-gate">
            <section className="detail-card retrieval-context">
              <div className="card-header">
                <div className="card-header-titles">
                  <h2>Record requested</h2>
                  <span>Identity verification</span>
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
                <div><dt>Latest Verified Version</dt><dd>{versionPublished ? 'Version 2 · 28 Aug 2026' : 'Version 1 · 21 Aug 2026'}</dd></div>
                <div><dt>Record Category</dt><dd>Verified Goals-of-Care Summary</dd></div>
                <div><dt>Access Boundary</dt><dd>Simulated authorised clinical access</dd></div>
                <div><dt>Audit Mode</dt><dd>Simulated access-purpose trail</dd></div>
              </dl>
            </section>

            <form className="detail-card workflow-form" onSubmit={openRetrievedRecord}>
              <div className="card-header">
                <div className="card-header-titles">
                  <h2>Access authorization</h2>
                  <span>Access purpose is recorded in this browser-local demonstration trail</span>
                </div>
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
                  <strong>{isBreakGlassAccess ? 'Emergency / break-glass access.' : 'Routine access.'}</strong>{' '}
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
                  <strong>Retrieval boundary:</strong> I am opening this synthetic summary for the selected care relationship. I will re-confirm current clinical status with the patient or surrogate and care team as appropriate; this summary is not a treatment order or legal directive.
                </span>
              </label>
              <button
                className="primary-button full-width"
                type="submit"
                disabled={!retrievalGateReady}
                aria-describedby="retrieval-gate-status"
              >
                <IconLock className="w-4 h-4" />
                Log access and view verified record
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
              Printed from the Continuity Loop demonstration. The patient, record and dates are
              synthetic. This page is not a treatment order, resuscitation order, or legal
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
                  {selectedItem.hospitalId} · {versionPublished ? 'Version 2' : 'Version 1'} · Attested by Dr Sujay
                </span>
              </div>
              <div className="flex items-center gap-3">
                <StatusPill status="Access logged" />
                {sourceContextAvailable && (
                  <button className="secondary-button" type="button" onClick={copySummaryToClipboard}>
                    <IconCopy className="w-3.5 h-3.5" /> Copy Summary
                  </button>
                )}
                <button className="secondary-button" type="button" onClick={() => window.print()}>
                  Print handoff
                </button>
              </div>
            </div>
            {sourceContextAvailable ? (
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
                <span>Version metadata is available, but source-linked field content is not loaded for this synthetic scenario. No field value is inferred.</span>
              </div>
            )}
            <section className="retrieval-source-context" aria-labelledby="retrieval-source-title">
              <div className="card-header">
                <div className="card-header-titles">
                  <h3 id="retrieval-source-title">Source and context</h3>
                  <span>What this synthetic summary is grounded in</span>
                </div>
              </div>
              <dl className="definition-list retrieval-context-list">
                <div><dt>Originating team</dt><dd>{selectedProfile.team}</dd></div>
                <div><dt>Source record</dt><dd>Clinician-entered synthetic conversation note</dd></div>
                <div><dt>Captured</dt><dd>{sourceContextAvailable ? '28 Aug 2026 · 09:54' : 'Timestamp not loaded for this synthetic scenario'}</dd></div>
                <div><dt>Unresolved / not discussed</dt><dd>{sourceContextAvailable ? releasedFields.openQuestions : 'Not loaded in this synthetic scenario'}</dd></div>
              </dl>
              {sourceContextAvailable ? (
                <div className="retrieval-excerpt-list">
                  <span className="document-kicker">Source excerpts</span>
                  {sourceSnippets.map((snippet) => (
                    <blockquote key={snippet.key}>{snippet.text}</blockquote>
                  ))}
                </div>
              ) : (
                <div className="record-boundary">
                  <IconInfo className="w-4 h-4 flex-shrink-0" />
                  <span>Source excerpts are not loaded for this synthetic scenario. No preference is inferred from missing source text.</span>
                </div>
              )}
            </section>
            <div className="retrieved-footer">
              <div>
                <span>Verified By</span>
                <strong>Dr Sujay · {versionPublished ? '28 Aug 2026' : '21 Aug 2026'}</strong>
              </div>
              <div>
                <span>Authorisation Basis</span>
                <strong>
                  {versionPublished
                    ? authorisation
                    : 'Verbal acknowledgement recorded by care team'}
                </strong>
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

    function hasVerifiedRecord(id: string) {
      return Boolean(recordStates[id]?.versionPublished) || (patientProfiles[id]?.verified ?? '').includes('2026');
    }

    function versionLabel(id: string) {
      return recordStates[id]?.versionPublished
        ? 'Version 2 · 28 Aug 2026'
        : (patientProfiles[id]?.verified ?? '').includes('2026')
          ? `Version 1 · ${patientProfiles[id].verified}`
          : 'No released version';
    }

    return (
      <AppointmentsPage
        patients={patients}
        selectedId={selectedId}
        onSelectPatient={selectPatient}
        appointments={appointments}
        onAppointmentsChange={setAppointments}
        canSchedule={isTreatingPhysician || isCareCoordinator}
        canStartConversation={isTreatingPhysician}
        readOnlyReason="Scheduling is limited to the treating physician and the care coordinator in this demonstration."
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
        onRetrieve={(id) => {
          selectPatient(id);
          navigate('retrieve');
        }}
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
          'Synthetic Patient Registry',
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
    return (
      <>
        {renderPageHeading('Clinician Review Queue', 'Pending structured drafts')}
        <section className="worklist-panel">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Patient</th>
                  <th>Draft Type</th>
                  <th>Author / Initiated</th>
                  <th>Status</th>
                  <th><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td data-label="Patient">
                    <strong>Ramesh Kumar</strong>
                    <span>CANCER-20392</span>
                  </td>
                  <td data-label="Draft Type">Conversation summary</td>
                  <td data-label="Author / Initiated">28 Aug 2026 · Dr Sujay</td>
                  <td data-label="Status"><StatusPill status="Awaiting review" /></td>
                  <td className="row-action">
                    <button
                      type="button"
                      onClick={() => {
                        selectPatient('CANCER-20392');
                        navigate('draft');
                      }}
                    >
                      Review
                    </button>
                  </td>
                </tr>
                {primaryRecordState.draftPrepared && !primaryRecordState.versionPublished && (
                  <tr>
                    <td data-label="Patient">
                      <strong>Meera Raghavan</strong>
                      <span>CANCER-20418</span>
                    </td>
                    <td data-label="Draft Type">Updated conversation summary (v2)</td>
                    <td data-label="Author / Initiated">28 Aug 2026 · Dr Sujay</td>
                    <td data-label="Status"><StatusPill status="Draft" /></td>
                    <td className="row-action">
                      <button
                        type="button"
                        onClick={() => {
                          selectPatient('CANCER-20418');
                          navigate('verify');
                        }}
                      >
                        Verify
                      </button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </>
    );
  }

  function renderRecords() {
    return (
      <>
        <div className="page-heading compact-heading">
          <div>
            <p className="eyebrow">Released version archive</p>
            <h1>Verified goals-of-care records</h1>
          </div>
          {primaryRecordState.versionPublished && (
            <button className="secondary-button" type="button" onClick={openPrimaryVersionComparison}>
              <IconColumns className="w-4 h-4" />
              Compare v1 vs v2 revisions
            </button>
          )}
        </div>

        <section className="worklist-panel">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Patient</th>
                  <th>Version</th>
                  <th>Verified By</th>
                  <th>Authorisation</th>
                  <th>Status</th>
                  <th><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {primaryRecordState.versionPublished && (
                  <tr>
                    <td data-label="Patient">
                      <strong>Meera Raghavan</strong>
                      <span>CANCER-20418</span>
                    </td>
                    <td data-label="Version">Version 2 · 28 Aug 2026</td>
                    <td data-label="Verified By">Dr Sujay</td>
                    <td data-label="Authorisation">Authorised</td>
                    <td data-label="Status"><StatusPill status="Current" /></td>
                    <td className="row-action">
                      <button
                        type="button"
                        onClick={() => {
                          selectPatient('CANCER-20418');
                          navigate('retrieve');
                        }}
                      >
                        View
                      </button>
                    </td>
                  </tr>
                )}
                <tr>
                  <td data-label="Patient">
                    <strong>Meera Raghavan</strong>
                    <span>CANCER-20418</span>
                  </td>
                  <td data-label="Version">Version 1 · 21 Aug 2026</td>
                  <td data-label="Verified By">Dr Sujay</td>
                  <td data-label="Authorisation">Authorised</td>
                  <td data-label="Status"><StatusPill status={primaryRecordState.versionPublished ? 'Superseded' : 'Current'} /></td>
                  <td className="row-action">
                    <button
                      type="button"
                      onClick={() => {
                        selectPatient('CANCER-20418');
                        navigate('patient');
                      }}
                    >
                      View
                    </button>
                  </td>
                </tr>
                <tr>
                  <td data-label="Patient">
                    <strong>Leela Thomas</strong>
                    <span>CANCER-20377</span>
                  </td>
                  <td data-label="Version">Version 1 · 21 Aug 2026</td>
                  <td data-label="Verified By">Dr Sujay</td>
                  <td data-label="Authorisation">Authorised</td>
                  <td data-label="Status"><StatusPill status="Current" /></td>
                  <td className="row-action">
                    <button
                      type="button"
                      onClick={() => {
                        selectPatient('CANCER-20377');
                        navigate('patient');
                      }}
                    >
                      View
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </>
    );
  }

  function renderAudit() {
    return (
      <>
        {renderPageHeading('Simulated audit trail', 'Continuity event and access history')}

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
              No recorded events for {auditFilterActor}. Only actions taken in this demonstration
              appear here.
            </p>
          )}
        </section>

        <div className="audit-footnote" role="note">
          <IconInfo className="w-4 h-4" />
          <p>
            Showing {filteredAuditEvents.length} of {auditEvents.length} recorded events
            {auditFilterActor === 'All' ? '' : ` for ${auditFilterActor}`}. This trail is
            browser-local, is written only when you act in the demonstration, and resets on reload.
            It is not a tamper-evident audit log.
          </p>
        </div>
      </>
    );
  }

  function renderHelp() {
    const glossary = [
      {
        term: 'Goals of care',
        definition:
          'A clinician-led conversation about what matters to the patient and family, what was discussed, and what needs clarification. This prototype does not choose a treatment.',
      },
      {
        term: 'Source-linked draft',
        definition:
          'A draft whose fields point back to the synthetic conversation note. “Not stated” means the note did not say it; the workflow leaves it open instead of guessing.',
      },
      {
        term: 'Physician verified',
        definition:
          'The treating physician checked the summary fields and recorded the review. This label is not a legal order or a treatment instruction.',
      },
      {
        term: 'Patient or surrogate acknowledgement',
        definition:
          'A record that the patient or authorised surrogate reviewed the conversation summary. It is not a treatment consent record.',
      },
      {
        term: 'Break-glass access',
        definition:
          'A simulated emergency access reason recorded before a receiving physician views the latest verified handoff.',
      },
      {
        term: 'Continuity worklist',
        definition:
          'Follow-up tasks for ownership, outreach, and review dates. It supports coordination and does not replace clinical judgement.',
      },
      {
        term: 'Synthetic',
        definition:
          'Fictional demonstration data used to show the workflow. It is not a real patient record.',
      },
    ];

    return (
      <>
        {renderPageHeading('Workflow help', 'Terms used in this demonstration')}
        <section className="glossary-card detail-card" aria-labelledby="glossary-title">
          <div className="protocol-notice" role="note">
            <IconInfo className="w-4 h-4 flex-shrink-0" />
            <span id="glossary-title">
              Plain-language definitions for labels in this prototype. This is not medical advice, a diagnosis, a treatment recommendation, or a legal directive.
            </span>
          </div>
          <dl className="glossary-list">
            {glossary.map(({ term, definition }) => (
              <div className="glossary-entry" key={term}>
                <dt>{term}</dt>
                <dd>{definition}</dd>
              </div>
            ))}
          </dl>
        </section>
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
      case 'help': return renderHelp();
      case 'my-plan':
        return (
          <MyCarePlan
            patientName={portalPatientName}
            versionLabel={planVersionLabel(portalPatientId)}
            verifiedBy="Dr Sujay"
            verifiedOn={
              recordStates[portalPatientId]?.versionPublished
                ? '28 Aug 2026'
                : (patientProfiles[portalPatientId]?.verified ?? 'Not yet verified')
            }
            fields={planFieldsFor(portalPatientId)}
            quickView={<EdQuickView record={ectprQuickViews[portalPatientId] ?? null} audience="patient" />}
            consent={consentById[portalPatientId] ?? null}
            onGoToConsent={() => navigate('consent')}
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
              organisation: 'Oncology · Palliative care (synthetic)',
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
            organisation: 'Oncology · Palliative care (synthetic)',
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
            consent={consentById[portalPatientId] ?? null}
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
    'my-timeline': 'Journey',
    'care-near-me': 'Near me',
    'my-doctors': 'Doctors',
    'emergency-card': 'Card',
    'my-details': 'Details',
    consent: 'Consent',
    caregiver: 'Journey',
    home: 'Handoff',
    guide: 'Guide',
    worklist: 'Worklist',
    appointments: 'Appointments',
    patients: 'Patients',
    records: 'Records',
    audit: 'Audit',
    help: 'Help',
  };
  const visibleNavItems = isPatientSession
    ? patientNavItems
    : isFamilySession
      ? familyNavItems
      : navItems.filter((item) => item.view !== 'caregiver');

  if (authScreen === 'landing') {
    return (
      <LandingPage
        onSignIn={() => setAuthScreen('login')}
        onSignUp={() => setAuthScreen('signup')}
        onExploreDemo={() => enterCareTeam('Dr Sujay · Clinical lead')}
        onFamilyAccess={() => setAuthScreen('family')}
        onPatientAccess={() => setAuthScreen('patient')}
      />
    );
  }
  if (authScreen === 'login') {
    return (
      <LoginScreen
        onSignIn={(role) => enterCareTeam(role)}
        onGoToSignup={() => setAuthScreen('signup')}
        onGoHome={() => setAuthScreen('landing')}
        onGoToFamily={() => setAuthScreen('family')}
        onGoToPatient={() => setAuthScreen('patient')}
      />
    );
  }
  if (authScreen === 'signup') {
    return (
      <SignupScreen
        onSignUp={(role) => enterCareTeam(role)}
        onGoToLogin={() => setAuthScreen('login')}
        onGoHome={() => setAuthScreen('landing')}
        onGoToFamily={() => setAuthScreen('family')}
        onGoToPatient={() => setAuthScreen('patient')}
      />
    );
  }
  if (authScreen === 'patient') {
    return (
      <PatientLoginScreen
        onPatientSignIn={enterPatient}
        onGoToFamily={() => setAuthScreen('family')}
        onGoToClinician={() => setAuthScreen('login')}
        onGoHome={() => setAuthScreen('landing')}
        patients={workItems.map((w) => ({ hospitalId: w.hospitalId, name: w.patient }))}
      />
    );
  }
  if (authScreen === 'family') {
    return (
      <FamilyLoginScreen
        onFamilySignIn={enterFamily}
        onGoToClinician={() => setAuthScreen('login')}
        onGoHome={() => setAuthScreen('landing')}
        onGoToPatient={() => setAuthScreen('patient')}
      />
    );
  }

  const familyInitials = (familyMember?.name ?? '')
    .split(' ')
    .filter(Boolean)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
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
            <strong>Continuity Loop</strong>
            <span>Goals-of-Care Workflow</span>
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

        <div className="scope-note">
          <div className="scope-header">
            <IconShieldCheck className="w-3.5 h-3.5" />
            <span>Safety Boundary</span>
          </div>
          <p>
            {isPatientSession || isFamilySession
              ? 'This app documents what you and your care team agreed. It is not a treatment order or a legal directive.'
              : 'Documents a clinician-led conversation. It is not a treatment order, legal directive, or autonomous triage system.'}
          </p>
        </div>
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
              <strong>Continuity</strong>
            </button>
            <div className="environment-label">
              <span className="environment-dot" />
              <span>Local demonstration · no live records</span>
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
                <span className="family-session-label"><IconLock className="w-3.5 h-3.5" />Family view · read-only</span>
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
              <div className="caregiver-preview-switcher" aria-label="Caregiver demonstration controls">
                <span><IconLock className="w-3.5 h-3.5" />Previewing the family view</span>
                <button className="secondary-button" type="button" onClick={() => navigate('home')}>
                  Back to care team
                </button>
              </div>
            ) : (
              <>
                <div className="role-switcher-wrap" title="Switch simulated demonstration role">
                  <span className="role-label">Demo role</span>
                  <select
                    className="role-select"
                    aria-label="Simulated demonstration role"
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

                <div className="profile-block" aria-label="Active simulated persona">
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

        <div className="content-frame">{renderCurrentView()}</div>
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

      {/* Version Diff Modal */}
      {diffOpen && primaryRecordState.versionPublished && selectedId === 'CANCER-20418' && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) setDiffOpen(false);
          }}
        >
          <section
            ref={diffDialogRef}
            className="modal-panel large-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="diff-title"
          >
            <div className="modal-header">
              <div>
                <p className="eyebrow">
                  <IconColumns className="w-3.5 h-3.5" /> Append-only version comparison
                </p>
                <h2 id="diff-title">Version Comparison: v1 (21 Aug) vs v2 (28 Aug)</h2>
              </div>
              <button type="button" aria-label="Close comparison" onClick={() => setDiffOpen(false)}>
                <IconX className="w-4 h-4" />
              </button>
            </div>

            <div className="p-6">
              <div className="diff-panel">
                <div className="diff-version-col">
                  <div className="diff-header">
                    <strong>Version 1 (Superseded)</strong>
                    <span className="text-xs text-muted">21 Aug 2026</span>
                  </div>
                  {fieldConfig.map((field) => (
                    <div key={field.key} className="diff-item">
                      <span>{field.label}</span>
                      <p>{v1Fields[field.key]}</p>
                    </div>
                  ))}
                </div>

                <div className="diff-version-col">
                  <div className="diff-header">
                    <strong className="text-emerald-800">Version 2 (Current Active)</strong>
                    <span className="text-xs text-emerald-700 font-semibold">28 Aug 2026 (Verified)</span>
                  </div>
                  {fieldConfig.map((field) => (
                    <div key={field.key} className="diff-item highlighted">
                      <span>
                        {field.label}
                        {v1Fields[field.key] !== draftFields[field.key] && (
                          <b className="diff-change-label">Changed</b>
                        )}
                      </span>
                      <p>{draftFields[field.key]}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-4 flex justify-end gap-3">
                <button className="primary-button" type="button" onClick={() => setDiffOpen(false)}>
                  Close comparison
                </button>
              </div>
            </div>
          </section>
        </div>
      )}

      {/* Toast Feedback */}
      {toast && (
        <div className="toast" role="status" aria-live="polite">
          <IconCheck className="w-4 h-4" />
          <span>{toast}</span>
        </div>
      )}
    </main>
  );
}
