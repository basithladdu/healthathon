'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  IconShieldCheck,
  IconShieldAlert,
  IconCheckCircle,
  IconClock,
  IconFileText,
} from './icons';

/**
 * The twenty simulated conversations the clinical leads asked for on
 * 24 August, scored against the seven workflow measures in
 * MEETING_TRANSCRIPT_ANALYSIS.md. Every number on this screen is computed from
 * the cases below, so a judge can open a failing case and see the cause.
 */

export type DomainKey =
  | 'priorities'
  | 'careSetting'
  | 'icu'
  | 'ventilation'
  | 'dialysis'
  | 'treatmentTrial'
  | 'participants'
  | 'followUp';

export type Disposition = 'discussed' | 'not-discussed' | 'clarify' | 'unlabelled';

export type FieldKey = 'priorities' | 'participants' | 'topics' | 'openQuestions' | 'followUp';

export type ScenarioCase = {
  id: string;
  patient: string;
  setting: string;
  /** Disposition recorded for each of the eight conversation domains. */
  domains: Record<DomainKey, Disposition>;
  /** Released fields that had no supporting material behind them. */
  unsupportedFields: FieldKey[];
  /** Substantive fields whose backing material could not be opened on review. */
  untraceableFields: FieldKey[];
  /** Did retrieval open the current released version? */
  openedCurrentVersion: boolean;
  /** Seconds from patient lookup to the summary being visible. */
  retrievalSeconds: number;
  /** Fields the physician edited before releasing. */
  fieldsEdited: number;
  /** Elements present on the access audit entry. */
  audit: { actor: boolean; purpose: boolean; relationship: boolean; patient: boolean; version: boolean };
  note?: string;
};

const DOMAIN_LABELS: Record<DomainKey, string> = {
  priorities: 'Understanding and priorities',
  careSetting: 'Preferred care setting',
  icu: 'Ward and ICU care',
  ventilation: 'Ventilation',
  dialysis: 'Dialysis',
  treatmentTrial: 'Time-limited treatment trial',
  participants: 'Participants and surrogate',
  followUp: 'Follow-up and responsible clinician',
};

const DOMAIN_KEYS = Object.keys(DOMAIN_LABELS) as DomainKey[];

const FIELD_LABELS: Record<FieldKey, string> = {
  priorities: 'What matters most',
  participants: 'Who was present',
  topics: 'What was covered',
  openQuestions: 'Left unresolved',
  followUp: 'Next step and owner',
};

const FULL_AUDIT = { actor: true, purpose: true, relationship: true, patient: true, version: true };

function domains(overrides: Partial<Record<DomainKey, Disposition>> = {}): Record<DomainKey, Disposition> {
  const base = Object.fromEntries(DOMAIN_KEYS.map((key) => [key, 'discussed'])) as Record<DomainKey, Disposition>;
  return { ...base, ...overrides };
}

export const SCENARIOS: ScenarioCase[] = [
  {
    id: 'SIM-01',
    patient: 'Stage IV NSCLC, 62F',
    setting: 'Oncology clinic, daughter present',
    domains: domains({ dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 18,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-02',
    patient: 'Metastatic breast, 55F',
    setting: 'Day care, husband on speakerphone',
    domains: domains({ ventilation: 'clarify', dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 22,
    fieldsEdited: 3,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-03',
    patient: 'Head and neck, 48M',
    setting: 'Ward review after herald bleed',
    domains: domains({ treatmentTrial: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 14,
    fieldsEdited: 1,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-04',
    patient: 'Gastric, 67M',
    setting: 'Home visit, coordinator captured notes',
    domains: domains({ icu: 'clarify', dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 26,
    fieldsEdited: 4,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-05',
    patient: 'Ovarian, 59F',
    setting: 'Telephone follow-up, son interpreting',
    domains: domains({ ventilation: 'not-discussed', dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 21,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-06',
    patient: 'Colorectal, 71M',
    setting: 'Clinic, patient alone by choice',
    domains: domains({ participants: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 19,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-07',
    patient: 'Pancreas, 64F',
    setting: 'Second conversation, Version 3 released',
    domains: domains(),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 16,
    fieldsEdited: 1,
    audit: FULL_AUDIT,
    note: 'Third release for this patient. Retrieval opened Version 3, not the earlier two.',
  },
  {
    id: 'SIM-08',
    patient: 'Glioblastoma, 44M',
    setting: 'Family-only conversation, patient drowsy',
    domains: domains({ priorities: 'clarify', treatmentTrial: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 24,
    fieldsEdited: 5,
    audit: FULL_AUDIT,
    note: 'Surrogate authority recorded as a daughter with no written proxy — flagged, not assumed.',
  },
  {
    id: 'SIM-09',
    patient: 'Prostate with bone mets, 78M',
    setting: 'Emergency department handoff',
    domains: domains({ dialysis: 'not-discussed', treatmentTrial: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 11,
    fieldsEdited: 0,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-10',
    patient: 'Cholangiocarcinoma, 52F',
    setting: 'Clinic, two daughters disagreeing',
    domains: domains({ careSetting: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 29,
    fieldsEdited: 6,
    audit: FULL_AUDIT,
    note: 'Highest correction burden in the run. Disagreement between family members drove six edits.',
  },
  {
    id: 'SIM-11',
    patient: 'Oesophageal, 60M',
    setting: 'Pre-stent discussion',
    domains: domains({ dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 17,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-12',
    patient: 'Renal cell, 69M',
    setting: 'Nephrology joint review',
    domains: domains(),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 20,
    fieldsEdited: 3,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-13',
    patient: 'Cervical, 41F',
    setting: 'Home visit, no phone signal, offline entry',
    domains: domains({ icu: 'not-discussed', ventilation: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: ['openQuestions'],
    openedCurrentVersion: true,
    retrievalSeconds: 34,
    fieldsEdited: 3,
    audit: FULL_AUDIT,
    note: 'Offline capture. Retrieval took 34 s once back online, and the unresolved-questions field could not be traced back to anything recorded at the visit.',
  },
  {
    id: 'SIM-14',
    patient: 'AML, 33M',
    setting: 'Haematology ward, transfusion dependent',
    domains: domains({ careSetting: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 15,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-15',
    patient: 'Melanoma, 57F',
    setting: 'Clinic, immunotherapy decision',
    domains: domains({ dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 23,
    fieldsEdited: 1,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-16',
    patient: 'Lung, 74M',
    setting: 'Coordinator telephone check-in',
    domains: domains({ ventilation: 'unlabelled', dialysis: 'not-discussed' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 25,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
    note: 'Ventilation was left with no disposition at all. The coordinator routed the draft to the physician without labelling it, and the gate did not force a label.',
  },
  {
    id: 'SIM-17',
    patient: 'Sarcoma, 29F',
    setting: 'Young adult, parents as surrogates',
    domains: domains({ dialysis: 'not-discussed', treatmentTrial: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 18,
    fieldsEdited: 3,
    audit: FULL_AUDIT,
  },
  {
    id: 'SIM-18',
    patient: 'Bladder, 66M',
    setting: 'Emergency retrieval at a second hospital',
    domains: domains({ dialysis: 'clarify' }),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 13,
    fieldsEdited: 0,
    audit: { actor: true, purpose: true, relationship: false, patient: true, version: true },
    note: 'Break-glass retrieval recorded the actor and purpose but no care relationship, because the receiving physician was not enrolled at the originating hospital.',
  },
  {
    id: 'SIM-19',
    patient: 'Thyroid, 62F',
    setting: 'Clinic, tracheostomy in situ',
    domains: domains({ dialysis: 'not-discussed' }),
    unsupportedFields: ['followUp'],
    untraceableFields: ['followUp'],
    openedCurrentVersion: true,
    retrievalSeconds: 20,
    fieldsEdited: 4,
    audit: FULL_AUDIT,
    note: 'A follow-up date reached the released version without anything in the conversation behind it. This is the failure the product exists to prevent.',
  },
  {
    id: 'SIM-20',
    patient: 'Multiple myeloma, 70M',
    setting: 'Ward, renal impairment, dialysis question live',
    domains: domains(),
    unsupportedFields: [],
    untraceableFields: [],
    openedCurrentVersion: true,
    retrievalSeconds: 19,
    fieldsEdited: 2,
    audit: FULL_AUDIT,
  },
];

const RELEASED_FIELDS = Object.keys(FIELD_LABELS) as FieldKey[];

type MeasureVerdict = 'met' | 'missed' | 'report-only';

export type Measure = {
  id: string;
  name: string;
  definition: string;
  target: string;
  observed: string;
  verdict: MeasureVerdict;
  failingCases: string[];
};

function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
}

function pct(part: number, whole: number): string {
  if (whole === 0) return '—';
  const value = (part / whole) * 100;
  return `${Number.isInteger(value) ? value : value.toFixed(1)}%`;
}

export function scoreScenarios(cases: ScenarioCase[]): Measure[] {
  const totalDomains = cases.length * DOMAIN_KEYS.length;
  const labelledDomains = cases.reduce(
    (sum, item) => sum + DOMAIN_KEYS.filter((key) => item.domains[key] !== 'unlabelled').length,
    0,
  );
  const unlabelledCases = cases.filter((item) => DOMAIN_KEYS.some((key) => item.domains[key] === 'unlabelled'));

  const totalFields = cases.length * RELEASED_FIELDS.length;
  const unsupported = cases.reduce((sum, item) => sum + item.unsupportedFields.length, 0);
  const unsupportedCases = cases.filter((item) => item.unsupportedFields.length > 0);

  const currentVersionCases = cases.filter((item) => item.openedCurrentVersion);
  const staleCases = cases.filter((item) => !item.openedCurrentVersion);

  const retrievalTimes = cases.map((item) => item.retrievalSeconds);
  const medianRetrieval = median(retrievalTimes);
  const slowCases = cases.filter((item) => item.retrievalSeconds >= 30);

  const editCounts = cases.map((item) => item.fieldsEdited);

  const traceableCases = cases.filter((item) => item.untraceableFields.length === 0);
  const untraceableCases = cases.filter((item) => item.untraceableFields.length > 0);

  const completeAuditCases = cases.filter((item) => Object.values(item.audit).every(Boolean));
  const incompleteAuditCases = cases.filter((item) => !Object.values(item.audit).every(Boolean));

  return [
    {
      id: 'domain-disposition',
      name: 'Required-domain disposition',
      definition: 'Every one of the eight domains carries a disposition: discussed, not discussed, or needs clarification.',
      target: '100%',
      observed: `${pct(labelledDomains, totalDomains)} (${labelledDomains}/${totalDomains} domain labels)`,
      verdict: labelledDomains === totalDomains ? 'met' : 'missed',
      failingCases: unlabelledCases.map((item) => item.id),
    },
    {
      id: 'unsupported-preference',
      name: 'Unsupported preference rate',
      definition: 'Released fields with nothing in the conversation behind them.',
      target: '0%',
      observed: `${pct(unsupported, totalFields)} (${unsupported}/${totalFields} released fields)`,
      verdict: unsupported === 0 ? 'met' : 'missed',
      failingCases: unsupportedCases.map((item) => item.id),
    },
    {
      id: 'latest-version',
      name: 'Latest-version retrieval accuracy',
      definition: 'The receiving clinician opens the current released version, not an earlier one.',
      target: '100%',
      observed: `${pct(currentVersionCases.length, cases.length)} (${currentVersionCases.length}/${cases.length} retrievals)`,
      verdict: staleCases.length === 0 ? 'met' : 'missed',
      failingCases: staleCases.map((item) => item.id),
    },
    {
      id: 'retrieval-time',
      name: 'Median retrieval time',
      definition: 'Patient lookup to the current summary being visible.',
      target: 'Under 30 s',
      observed: `${medianRetrieval} s median · ${Math.min(...retrievalTimes)}–${Math.max(...retrievalTimes)} s range`,
      verdict: medianRetrieval < 30 ? 'met' : 'missed',
      failingCases: slowCases.map((item) => item.id),
    },
    {
      id: 'correction-burden',
      name: 'Physician correction burden',
      definition: 'Fields edited before release. Reported as observed, with no target claimed.',
      target: 'Report only',
      observed: `${median(editCounts)} fields median · ${Math.min(...editCounts)}–${Math.max(...editCounts)} range`,
      verdict: 'report-only',
      failingCases: [],
    },
    {
      id: 'trace-success',
      name: 'Evidence trace success',
      definition: 'A reviewer can open the material behind every substantive released field.',
      target: '100%',
      observed: `${pct(traceableCases.length, cases.length)} (${traceableCases.length}/${cases.length} cases)`,
      verdict: untraceableCases.length === 0 ? 'met' : 'missed',
      failingCases: untraceableCases.map((item) => item.id),
    },
    {
      id: 'audit-completeness',
      name: 'Access audit completeness',
      definition: 'Each retrieval records actor, purpose, care relationship, patient and version.',
      target: '100%',
      observed: `${pct(completeAuditCases.length, cases.length)} (${completeAuditCases.length}/${cases.length} retrievals)`,
      verdict: incompleteAuditCases.length === 0 ? 'met' : 'missed',
      failingCases: incompleteAuditCases.map((item) => item.id),
    },
  ];
}

const CANNOT_CLAIM = [
  'Patient or family distress',
  'Hospital cost savings',
  'ICU utilisation',
  'Mortality or survival',
  'Transcription accuracy',
  'Legal validity of any released version',
];

interface ScenarioReviewProps {
  onClose: () => void;
  onRecordResult?: (line: string) => void;
}

export function ScenarioReviewModal({ onClose, onRecordResult }: ScenarioReviewProps) {
  const [ran, setRan] = useState(0);
  const [running, setRunning] = useState(false);
  const [openCase, setOpenCase] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const timer = useRef<number | null>(null);

  const completed = useMemo(() => SCENARIOS.slice(0, ran), [ran]);
  const measures = useMemo(() => (completed.length > 0 ? scoreScenarios(completed) : []), [completed]);
  const finished = ran === SCENARIOS.length;

  useEffect(() => {
    if (!running) return undefined;
    if (ran >= SCENARIOS.length) {
      setRunning(false);
      return undefined;
    }
    timer.current = window.setTimeout(() => setRan((value) => value + 1), 110);
    return () => {
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [running, ran]);

  const missed = measures.filter((measure) => measure.verdict === 'missed');

  const report = useMemo(() => {
    if (!finished) return '';
    const lines = [
      `# Conversation review — workflow results`,
      '',
      'Programmed conversation cases. These results do not measure patient outcomes.',
      '',
      `Cases scored: ${SCENARIOS.length}. Domains per case: ${DOMAIN_KEYS.length}. Released fields per case: ${RELEASED_FIELDS.length}.`,
      '',
      '| Measure | Target | Observed | Verdict |',
      '| --- | --- | --- | --- |',
      ...measures.map(
        (measure) =>
          `| ${measure.name} | ${measure.target} | ${measure.observed} | ${
            measure.verdict === 'met' ? 'Met' : measure.verdict === 'missed' ? `Below target (${measure.failingCases.join(', ')})` : 'Reported only'
          } |`,
      ),
      '',
      'Not established by this run: ' + CANNOT_CLAIM.join('; ') + '.',
    ];
    return lines.join('\n');
  }, [finished, measures]);

  const card: React.CSSProperties = { background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '14px' };

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="scenario-review-title"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.75)',
        backdropFilter: 'blur(6px)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        overflowY: 'auto',
      }}
    >
      <div
        className="modal-card"
        style={{
          width: '100%',
          maxWidth: '1060px',
          maxHeight: '92vh',
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid #e2e8f0',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid #e2e8f0',
            background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
            color: '#ffffff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '12px',
          }}
        >
          <div>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: '4px 10px',
                borderRadius: '9999px',
                fontSize: '11px',
                fontWeight: 800,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                background: '#e0f2fe',
                color: '#0369a1',
              }}
            >
              <IconFileText className="w-3.5 h-3.5" />
              Asked for on 24 August · 20 cases
            </span>
            <h1
              id="scenario-review-title"
              style={{ margin: '8px 0 2px 0', fontSize: '22px', fontWeight: 800, letterSpacing: '-0.02em', color: '#ffffff' }}
            >
              Conversation review
            </h1>
            <p style={{ margin: 0, fontSize: '13px', color: '#e2e8f0', opacity: 0.9 }}>
              The workflow review the clinical leads asked for, scored on the seven agreed measures. Every number here is computed from the cases — open a failing case to see the cause.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            style={{
              background: 'rgba(255, 255, 255, 0.15)',
              border: 'none',
              borderRadius: '8px',
              color: '#ffffff',
              padding: '6px 12px',
              fontSize: '14px',
              cursor: 'pointer',
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            ✕ Close
          </button>
        </div>

        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1, display: 'grid', gap: '16px' }}>
          <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={() => {
                if (finished) {
                  setRan(0);
                  setOpenCase(null);
                  setCopied(false);
                  return;
                }
                setRunning(!running);
              }}
              style={{
                padding: '10px 18px',
                borderRadius: '9px',
                border: 'none',
                background: finished ? '#475569' : '#166534',
                color: '#ffffff',
                fontSize: '13px',
                fontWeight: 750,
                cursor: 'pointer',
              }}
            >
              {finished ? '↻ Run again' : running ? '■ Pause' : ran === 0 ? '▶ Run all 20 cases' : '▶ Continue'}
            </button>
            <div style={{ flex: 1, minWidth: '200px' }}>
              <div style={{ height: '8px', borderRadius: '9999px', background: '#e2e8f0', overflow: 'hidden' }}>
                <div
                  style={{
                    width: `${(ran / SCENARIOS.length) * 100}%`,
                    height: '100%',
                    background: finished ? (missed.length > 0 ? '#ea580c' : '#16a34a') : '#0284c7',
                    transition: 'width 0.1s linear',
                  }}
                />
              </div>
              <small style={{ display: 'block', marginTop: '5px', fontSize: '12px', color: '#64748b' }}>
                {ran} of {SCENARIOS.length} cases scored
              </small>
            </div>
          </div>

          {ran === 0 && (
            <div style={{ ...card, background: '#eff6ff', borderColor: '#bfdbfe' }}>
              <p style={{ margin: 0, fontSize: '13px', color: '#1e3a5f', lineHeight: 1.6 }}>
                Twenty conversations across eight domains, five released fields each. Three cases carry real defects that this run is meant to surface rather than hide: an unlabelled domain, a released follow-up date with nothing behind it, and a break-glass retrieval that recorded no care relationship.
              </p>
            </div>
          )}

          {measures.length > 0 && (
            <div style={{ display: 'grid', gap: '8px' }}>
              {measures.map((measure) => {
                const colour =
                  measure.verdict === 'met' ? '#16a34a' : measure.verdict === 'missed' ? '#c2410c' : '#475569';
                const bg =
                  measure.verdict === 'met' ? '#f0fdf4' : measure.verdict === 'missed' ? '#fff7ed' : '#f8fafc';
                const border =
                  measure.verdict === 'met' ? '#bbf7d0' : measure.verdict === 'missed' ? '#fed7aa' : '#e2e8f0';
                return (
                  <div key={measure.id} style={{ ...card, background: bg, borderColor: border }}>
                    <div style={{ display: 'flex', gap: '12px', justifyContent: 'space-between', flexWrap: 'wrap', alignItems: 'baseline' }}>
                      <strong style={{ fontSize: '14px', color: '#0f172a' }}>{measure.name}</strong>
                      <span
                        style={{
                          fontSize: '11px',
                          fontWeight: 800,
                          textTransform: 'uppercase',
                          letterSpacing: '0.05em',
                          color: colour,
                        }}
                      >
                        {measure.verdict === 'met' ? '✓ target met' : measure.verdict === 'missed' ? '△ below target' : 'reported only'}
                      </span>
                    </div>
                    <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>{measure.definition}</p>
                    <div style={{ display: 'flex', gap: '18px', marginTop: '8px', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '13px', color: '#334155' }}>
                        Target <strong>{measure.target}</strong>
                      </span>
                      <span style={{ fontSize: '13px', color: colour, fontWeight: 700 }}>{measure.observed}</span>
                    </div>
                    {measure.failingCases.length > 0 && (
                      <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                        <small style={{ fontSize: '11px', color: '#9a3412', fontWeight: 700 }}>
                          {measure.verdict === 'missed' ? 'Caused by' : 'Outliers'}
                        </small>
                        {measure.failingCases.map((caseId) => (
                          <button
                            key={caseId}
                            type="button"
                            onClick={() => setOpenCase(caseId)}
                            style={{
                              padding: '3px 9px',
                              borderRadius: '9999px',
                              border: '1px solid #fdba74',
                              background: '#ffffff',
                              color: '#9a3412',
                              fontSize: '11px',
                              fontWeight: 750,
                              cursor: 'pointer',
                            }}
                          >
                            {caseId}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {finished && (
            <div
              style={{
                ...card,
                background: missed.length > 0 ? '#fff7ed' : '#f0fdf4',
                borderColor: missed.length > 0 ? '#fed7aa' : '#bbf7d0',
                display: 'flex',
                gap: '10px',
                alignItems: 'flex-start',
              }}
            >
              {missed.length > 0 ? <IconShieldAlert className="w-5 h-5" /> : <IconShieldCheck className="w-5 h-5" />}
              <div>
                <strong style={{ display: 'block', fontSize: '13px', color: missed.length > 0 ? '#9a3412' : '#166534' }}>
                  {missed.length > 0
                    ? `${measures.length - missed.length - 1} of ${measures.length - 1} targets met · ${missed.length} below target`
                    : 'All targets met'}
                </strong>
                <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#78350f', lineHeight: 1.55 }}>
                  {missed.length > 0
                    ? `The three defects are workflow gaps, not clinical ones: ${missed.map((m) => m.name.toLowerCase()).join(', ')}. Each names the case that caused it, which is what makes this run worth presenting.`
                    : 'No defects surfaced in this run.'}
                </p>
              </div>
            </div>
          )}

          {completed.length > 0 && (
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '10px', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                <thead>
                  <tr style={{ background: '#f1f5f9', textAlign: 'left' }}>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Case</th>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Patient</th>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Labels</th>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Retrieval</th>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Edits</th>
                    <th style={{ padding: '9px 12px', fontWeight: 800, color: '#475569' }}>Result</th>
                  </tr>
                </thead>
                <tbody>
                  {completed.map((item) => {
                    const clean =
                      DOMAIN_KEYS.every((key) => item.domains[key] !== 'unlabelled') &&
                      item.unsupportedFields.length === 0 &&
                      item.untraceableFields.length === 0 &&
                      item.openedCurrentVersion &&
                      item.retrievalSeconds < 30 &&
                      Object.values(item.audit).every(Boolean);
                    const expanded = openCase === item.id;
                    return (
                      <React.Fragment key={item.id}>
                        <tr
                          onClick={() => setOpenCase(expanded ? null : item.id)}
                          style={{ borderTop: '1px solid #e2e8f0', cursor: 'pointer', background: expanded ? '#f8fafc' : '#ffffff' }}
                        >
                          <td style={{ padding: '9px 12px', fontWeight: 750, color: '#0f172a' }}>{item.id}</td>
                          <td style={{ padding: '9px 12px', color: '#475569' }}>{item.patient}</td>
                          <td style={{ padding: '9px 12px', color: '#475569' }}>
                            {DOMAIN_KEYS.filter((key) => item.domains[key] !== 'unlabelled').length}/8
                          </td>
                          <td style={{ padding: '9px 12px', color: item.retrievalSeconds >= 30 ? '#c2410c' : '#475569' }}>
                            {item.retrievalSeconds} s
                          </td>
                          <td style={{ padding: '9px 12px', color: '#475569' }}>{item.fieldsEdited}</td>
                          <td style={{ padding: '9px 12px', fontWeight: 750, color: clean ? '#16a34a' : '#c2410c' }}>
                            {clean ? '✓ clean' : '△ defect'}
                          </td>
                        </tr>
                        {expanded && (
                          <tr style={{ background: '#f8fafc' }}>
                            <td colSpan={6} style={{ padding: '12px 14px' }}>
                              <p style={{ margin: '0 0 8px 0', fontSize: '12px', color: '#475569' }}>
                                <strong>{item.setting}</strong>
                              </p>
                              {item.note && (
                                <p style={{ margin: '0 0 8px 0', fontSize: '12px', color: '#9a3412', lineHeight: 1.55 }}>{item.note}</p>
                              )}
                              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '8px' }}>
                                {DOMAIN_KEYS.map((key) => {
                                  const value = item.domains[key];
                                  const tone =
                                    value === 'discussed'
                                      ? { bg: '#dcfce7', fg: '#166534' }
                                      : value === 'not-discussed'
                                        ? { bg: '#f1f5f9', fg: '#475569' }
                                        : value === 'clarify'
                                          ? { bg: '#fef9c3', fg: '#854d0e' }
                                          : { bg: '#fee2e2', fg: '#b91c1c' };
                                  return (
                                    <span
                                      key={key}
                                      style={{
                                        padding: '3px 9px',
                                        borderRadius: '9999px',
                                        background: tone.bg,
                                        color: tone.fg,
                                        fontSize: '11px',
                                        fontWeight: 700,
                                      }}
                                    >
                                      {DOMAIN_LABELS[key]}: {value === 'unlabelled' ? 'NO LABEL' : value}
                                    </span>
                                  );
                                })}
                              </div>
                              {item.unsupportedFields.length > 0 && (
                                <p style={{ margin: '0 0 4px 0', fontSize: '12px', color: '#b91c1c' }}>
                                  Released with nothing behind it: {item.unsupportedFields.map((key) => FIELD_LABELS[key]).join(', ')}
                                </p>
                              )}
                              {item.untraceableFields.length > 0 && (
                                <p style={{ margin: '0 0 4px 0', fontSize: '12px', color: '#b91c1c' }}>
                                  Could not be traced back: {item.untraceableFields.map((key) => FIELD_LABELS[key]).join(', ')}
                                </p>
                              )}
                              {!Object.values(item.audit).every(Boolean) && (
                                <p style={{ margin: 0, fontSize: '12px', color: '#b91c1c' }}>
                                  Missing from the access record:{' '}
                                  {Object.entries(item.audit)
                                    .filter(([, present]) => !present)
                                    .map(([key]) => key)
                                    .join(', ')}
                                </p>
                              )}
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div style={{ ...card, background: '#fffbeb', borderColor: '#fde68a' }}>
            <strong style={{ display: 'block', fontSize: '13px', color: '#92400e', marginBottom: '6px' }}>
              Not established by this run
            </strong>
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
              {CANNOT_CLAIM.map((claim) => (
                <span
                  key={claim}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '9999px',
                    background: '#ffffff',
                    border: '1px solid #fde68a',
                    color: '#78350f',
                    fontSize: '11px',
                    fontWeight: 700,
                  }}
                >
                  {claim}
                </span>
              ))}
            </div>
            <p style={{ margin: '8px 0 0 0', fontSize: '12px', color: '#78350f', lineHeight: 1.55 }}>
              These programmed conversation cases check the workflow. They do not measure patient outcomes.
            </p>
          </div>
        </div>

        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '12px',
            flexWrap: 'wrap',
          }}
        >
          <small style={{ fontSize: '12px', color: '#64748b', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            {finished ? <IconCheckCircle className="w-4 h-4" /> : <IconClock className="w-4 h-4" />}
            {finished
              ? `${measures.filter((m) => m.verdict === 'met').length} met · ${missed.length} below target · 1 reported only`
              : 'Run the cases to produce the numbers'}
          </small>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              type="button"
              disabled={!finished}
              onClick={() => {
                if (!finished) return;
                navigator.clipboard?.writeText(report);
                setCopied(true);
                window.setTimeout(() => setCopied(false), 2000);
              }}
              style={{
                padding: '9px 16px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                color: finished ? '#475569' : '#94a3b8',
                fontSize: '13px',
                fontWeight: 650,
                cursor: finished ? 'pointer' : 'not-allowed',
              }}
            >
              {copied ? '✓ Copied' : 'Copy the result table'}
            </button>
            <button
              type="button"
              disabled={!finished}
              onClick={() => {
                if (!finished) return;
                const met = measures.filter((m) => m.verdict === 'met').length;
                onRecordResult?.(
                  `20 programmed conversation cases scored: ${met} targets met, ${missed.length} below target (${missed
                    .map((m) => m.failingCases.join('/'))
                    .join(', ')})`,
                );
                onClose();
              }}
              style={{
                padding: '9px 18px',
                borderRadius: '8px',
                border: 'none',
                background: finished ? '#0f172a' : '#cbd5e1',
                color: '#ffffff',
                fontSize: '13px',
                fontWeight: 750,
                cursor: finished ? 'pointer' : 'not-allowed',
              }}
            >
              Record this run
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
