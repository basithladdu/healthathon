'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconHospital,
  IconUsers,
  IconClock,
  IconShieldCheck,
  IconSparkles,
  IconCheckCircle,
  IconZap,
} from './icons';

export interface TrajectoryPoint {
  date: string;
  pps: number; // 10 to 100
  esas: number; // 0 to 90
  omedd: number; // mg/day oral morphine equivalent
  facility: 'Kidwai Oncology' | 'District Hospital' | 'Karunashraya Hospice' | 'Home Palliative';
  facilityType: 'tertiary' | 'district' | 'hospice' | 'home';
  clinician: string;
  role: string;
  milestoneTitle: string;
  clinicalSummary: string;
  keyInterventions: string[];
  ceilingTier: 1 | 2 | 3;
  isKeyMilestone?: boolean;
}

export const INITIAL_TRAJECTORY: TrajectoryPoint[] = [
  {
    date: '12 Mar 2026',
    pps: 80,
    esas: 18,
    omedd: 0,
    facility: 'Kidwai Oncology',
    facilityType: 'tertiary',
    clinician: 'Dr Ramesh Kumar',
    role: 'Medical Oncologist',
    milestoneTitle: 'Initial Diagnosis & EGFR Mutation Confirmation',
    clinicalSummary: 'Presenting with persistent cough and hemoptysis. CT chest revealed 4.2cm right upper lobe mass. Core biopsy confirmed EGFR+ (Exon 19 del) Adenocarcinoma.',
    keyInterventions: ['Osimertinib 80mg daily initiated', 'Nutritional oncology counseling', 'Family orientation to disease trajectory'],
    ceilingTier: 3,
    isKeyMilestone: true,
  },
  {
    date: '28 May 2026',
    pps: 70,
    esas: 26,
    omedd: 20,
    facility: 'District Hospital',
    facilityType: 'district',
    clinician: 'Dr Priya Sharma',
    role: 'District Specialist',
    milestoneTitle: 'First-Line Monitoring & Incident Bone Pain',
    clinicalSummary: 'Thoracic back pain developed due to T6 vertebral body metastasis. Good response in primary lung lesion.',
    keyInterventions: ['Oral Morphine 10mg PO Q4H PRN prescribed', 'Single-fraction palliative radiotherapy (8 Gy to T6) completed', 'Bone antiresorptive therapy (Zoledronic acid 4mg)'],
    ceilingTier: 3,
    isKeyMilestone: false,
  },
  {
    date: '14 Jul 2026',
    pps: 60,
    esas: 42,
    omedd: 40,
    facility: 'Kidwai Oncology',
    facilityType: 'tertiary',
    clinician: 'Dr Sujay',
    role: 'Clinical Lead & Palliative Specialist',
    milestoneTitle: 'Advance Care Planning & Goals of Care Alignment',
    clinicalSummary: 'Symptom burden increasing with malignant pleural effusion. Patient explicitly requested home-based care and avoidance of invasive mechanical ventilation or ICU transfer.',
    keyInterventions: ['Advance Directive signed with son Rohan Sharma as designated proxy', 'Resuscitation ceiling adjusted to Tier 1 (Comfort care)', 'Therapeutic thoracentesis (1200 mL drained)'],
    ceilingTier: 1,
    isKeyMilestone: true,
  },
  {
    date: '20 Aug 2026',
    pps: 50,
    esas: 48,
    omedd: 60,
    facility: 'Karunashraya Hospice',
    facilityType: 'hospice',
    clinician: 'Dr Anita Das',
    role: 'Palliative Care Physician',
    milestoneTitle: 'Hospice Transition & Home Outreach Onboarding',
    clinicalSummary: 'Enrolled in community palliative outreach program. Ambulatory with walker, limited to short distances. Caregiver strain emerging.',
    keyInterventions: ['Opioid rotation to MST Continus 30mg BD', 'Assigned ASHA Worker Lakshmi Devi for weekly visits', 'Emergency anticipatory Just-in-Case prescribing pack delivered'],
    ceilingTier: 1,
    isKeyMilestone: false,
  },
  {
    date: '02 Sep 2026',
    pps: 40,
    esas: 38,
    omedd: 90,
    facility: 'Home Palliative',
    facilityType: 'home',
    clinician: 'Lakshmi Devi & Dr Sujay',
    role: 'Community Lead / Palliative Outreach',
    milestoneTitle: 'Continuous Syringe Driver Initiation & Comfort Stabilization',
    clinicalSummary: 'Difficulty swallowing oral tablets; persistent breakthrough breathlessness and pain. Initiated continuous subcutaneous syringe driver at bedside.',
    keyInterventions: ['Continuous SC Syringe Driver (Morphine 30mg + Midazolam 10mg + Glycopyrronium 0.6mg / 24h)', 'Night respite nurse scheduled', 'ESAS tracking demonstrates improved pain score (6 -> 3)'],
    ceilingTier: 1,
    isKeyMilestone: true,
  },
];

export interface LongitudinalTrajectoryProps {
  patientId: string;
  patientName: string;
  diagnosis: string;
  onClose?: () => void;
  onExportHandoffPacket?: () => void;
}

export function LongitudinalTrajectoryTimeline({
  patientId,
  patientName,
  diagnosis,
  onClose,
  onExportHandoffPacket,
}: LongitudinalTrajectoryProps) {
  const [selectedMetric, setSelectedMetric] = useState<'pps' | 'esas' | 'omedd'>('pps');
  const [filterFacility, setFilterFacility] = useState<string>('all');
  const [copiedToast, setCopiedToast] = useState(false);

  // Filtered trajectory points
  const displayedPoints = useMemo(() => {
    if (filterFacility === 'all') return INITIAL_TRAJECTORY;
    return INITIAL_TRAJECTORY.filter((p) => p.facilityType === filterFacility);
  }, [filterFacility]);

  // SVG Sparkline dimensions
  const chartConfig = useMemo(() => {
    const width = 640;
    const height = 140;
    const padding = { top: 20, right: 30, bottom: 25, left: 45 };
    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;

    const points = INITIAL_TRAJECTORY.map((p, idx) => {
      const x = padding.left + (idx / (INITIAL_TRAJECTORY.length - 1)) * chartWidth;
      let val = 0;
      let maxVal = 100;
      let unit = '';

      if (selectedMetric === 'pps') {
        val = p.pps;
        maxVal = 100;
        unit = '%';
      } else if (selectedMetric === 'esas') {
        val = p.esas;
        maxVal = 90;
        unit = 'pts';
      } else {
        val = p.omedd;
        maxVal = 120;
        unit = 'mg';
      }

      const y = padding.top + (1 - val / maxVal) * chartHeight;
      return { x, y, val, date: p.date, unit };
    });

    const pathD = points.reduce((acc, pt, idx) => {
      return idx === 0 ? `M ${pt.x},${pt.y}` : `${acc} L ${pt.x},${pt.y}`;
    }, '');

    const areaD = `${pathD} L ${points[points.length - 1].x},${padding.top + chartHeight} L ${points[0].x},${padding.top + chartHeight} Z`;

    return { width, height, padding, chartWidth, chartHeight, points, pathD, areaD };
  }, [selectedMetric]);

  const metricMeta = useMemo(() => {
    switch (selectedMetric) {
      case 'pps':
        return {
          title: 'Palliative Performance Scale (PPSv2)',
          color: '#4f46e5',
          fill: 'rgba(79, 70, 229, 0.12)',
          description: 'Functional capacity and ambulation trajectory (100% to 10%)',
          trend: 'Declining as anticipated in advanced EGFR+ disease (80% -> 40%)',
        };
      case 'esas':
        return {
          title: 'ESAS Total Symptom Distress Score',
          color: '#ea580c',
          fill: 'rgba(234, 88, 12, 0.12)',
          description: 'Multi-domain physical and emotional distress index (0 to 90)',
          trend: 'Peaked at 48 prior to Syringe Driver initiation; stabilized to 38',
        };
      case 'omedd':
        return {
          title: 'Daily Opioid Requirement (OMEDD)',
          color: '#059669',
          fill: 'rgba(5, 150, 105, 0.12)',
          description: 'Oral Morphine Equivalent Daily Dose (mg/24h)',
          trend: 'Titrated from 0mg to 90mg oral equivalent (30mg SC continuous)',
        };
    }
  }, [selectedMetric]);

  const handleCopyHandoff = () => {
    const summaryText = `[CONTINUITY LOOP: LONGITUDINAL TRAJECTORY & ENCOUNTER HANDOFF]
Patient: ${patientName} (${patientId})
Diagnosis: ${diagnosis}
Current Status: PPS 40%, ESAS 38/90, OMEDD 90mg (Syringe Driver active)
Active Resuscitation Tier: Tier 1 (Comfort & Dignity Focus, DNACPR active)

LONGITUDINAL ENCOUNTER TIMELINE:
${INITIAL_TRAJECTORY.map(
  (p, idx) => `${idx + 1}. [${p.date}] ${p.facility} - ${p.milestoneTitle}
   Clinician: ${p.clinician} (${p.role}) | Ceiling: Tier ${p.ceilingTier}
   Metrics: PPS ${p.pps}%, ESAS ${p.esas}, OMEDD ${p.omedd}mg
   Summary: ${p.clinicalSummary}
   Key Actions: ${p.keyInterventions.join('; ')}`
).join('\n\n')}

Attestation: Dr Sujay · Oncology Palliative Lead | Date: ${new Date().toLocaleDateString('en-GB')}`;

    navigator.clipboard.writeText(summaryText).then(() => {
      setCopiedToast(true);
      setTimeout(() => setCopiedToast(false), 3000);
    });
  };

  return (
    <div className="tep-overlay" role="dialog" aria-modal="true">
      <div className="tep-modal prog-modal-wide" style={{ maxWidth: '1150px' }}>
        {/* Header */}
        <div className="tep-header">
          <div className="tep-header-title">
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-indigo-600 text-white font-black text-xs">
                LONGITUDINAL TRAJECTORY
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Cross-Encounter Trajectory & Multi-Center Journey
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Longitudinal tracking of functional decline (PPS), symptom burden (ESAS), and opioid titration across care settings
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Trajectory Timeline"
          >
            ✕
          </button>
        </div>

        {/* Patient Banner */}
        <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Patient</span>
              <span className="font-bold text-slate-900 text-sm">{patientName} ({patientId})</span>
            </div>
            <div className="h-6 w-px bg-slate-200" />
            <div>
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Diagnosis</span>
              <span className="font-semibold text-slate-800">{diagnosis}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500">Active Care Path:</span>
            <span className="px-2.5 py-1 rounded bg-amber-100 text-amber-900 font-bold border border-amber-200">
              Tier 1: Comfort Care Home (Syringe Driver Active)
            </span>
          </div>
        </div>

        {/* Metric Selector & SVG Sparkline Panel */}
        <div className="px-6 py-4 bg-white border-b border-slate-200 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold text-slate-800">{metricMeta.title}</h3>
              <p className="text-xs text-slate-500">{metricMeta.description} · <span className="font-medium text-slate-700">{metricMeta.trend}</span></p>
            </div>

            {/* Metric Switcher */}
            <div className="flex bg-slate-100 p-1 rounded-lg text-xs gap-1 border border-slate-200">
              <button
                type="button"
                onClick={() => setSelectedMetric('pps')}
                className={`px-3 py-1 rounded font-bold transition ${
                  selectedMetric === 'pps'
                    ? 'bg-indigo-600 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                PPSv2 (%)
              </button>
              <button
                type="button"
                onClick={() => setSelectedMetric('esas')}
                className={`px-3 py-1 rounded font-bold transition ${
                  selectedMetric === 'esas'
                    ? 'bg-orange-600 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                ESAS Distress
              </button>
              <button
                type="button"
                onClick={() => setSelectedMetric('omedd')}
                className={`px-3 py-1 rounded font-bold transition ${
                  selectedMetric === 'omedd'
                    ? 'bg-emerald-600 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Opioid (OMEDD)
              </button>
            </div>
          </div>

          {/* SVG Trend Line */}
          <div className="relative bg-slate-900 rounded-xl p-2 border border-slate-800 shadow-inner">
            <svg viewBox={`0 0 ${chartConfig.width} ${chartConfig.height}`} className="w-full h-auto">
              {/* Grid Lines */}
              <line x1={chartConfig.padding.left} y1={chartConfig.padding.top} x2={chartConfig.width - chartConfig.padding.right} y2={chartConfig.padding.top} stroke="#334155" strokeDasharray="3 3" />
              <line x1={chartConfig.padding.left} y1={chartConfig.padding.top + chartConfig.chartHeight / 2} x2={chartConfig.width - chartConfig.padding.right} y2={chartConfig.padding.top + chartConfig.chartHeight / 2} stroke="#334155" strokeDasharray="3 3" />
              <line x1={chartConfig.padding.left} y1={chartConfig.padding.top + chartConfig.chartHeight} x2={chartConfig.width - chartConfig.padding.right} y2={chartConfig.padding.top + chartConfig.chartHeight} stroke="#475569" />

              {/* Shaded Area */}
              <path d={chartConfig.areaD} fill={metricMeta.fill} />

              {/* Line */}
              <path d={chartConfig.pathD} fill="none" stroke={metricMeta.color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />

              {/* Data Points */}
              {chartConfig.points.map((pt, idx) => (
                <g key={idx}>
                  <circle cx={pt.x} cy={pt.y} r="4.5" fill="#ffffff" stroke={metricMeta.color} strokeWidth="2.5" />
                  <text x={pt.x} y={pt.y - 8} fill="#ffffff" fontSize="10" fontWeight="bold" textAnchor="middle">
                    {pt.val}{pt.unit}
                  </text>
                  <text x={pt.x} y={chartConfig.height - 6} fill="#94a3b8" fontSize="9" textAnchor="middle">
                    {pt.date.split(' ')[0]} {pt.date.split(' ')[1]}
                  </text>
                </g>
              ))}
            </svg>
          </div>
        </div>

        {/* Facility Filter Pills */}
        <div className="px-6 py-2 bg-slate-100 border-b border-slate-200 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <span className="text-slate-500 font-semibold">Filter Encounter Setting:</span>
            <div className="flex gap-1">
              {[
                { id: 'all', label: 'All Encounters (5)' },
                { id: 'tertiary', label: 'Kidwai Regional Center' },
                { id: 'district', label: 'District Hospital' },
                { id: 'hospice', label: 'Karunashraya Hospice' },
                { id: 'home', label: 'Home Outreach' },
              ].map((f) => (
                <button
                  key={f.id}
                  onClick={() => setFilterFacility(f.id)}
                  className={`px-2.5 py-1 rounded text-xs font-semibold transition ${
                    filterFacility === f.id
                      ? 'bg-slate-800 text-white'
                      : 'bg-white text-slate-600 hover:bg-slate-200 border border-slate-300'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Chronological Encounter Journey Feed */}
        <div className="p-6 overflow-y-auto max-h-[60vh] space-y-4">
          <div className="relative pl-6 border-l-2 border-indigo-200 space-y-6">
            {displayedPoints.map((point, idx) => (
              <div key={idx} className="relative group">
                {/* Node Dot */}
                <div
                  className={`absolute -left-[31px] top-1.5 w-4 h-4 rounded-full border-2 border-white shadow-sm ${
                    point.isKeyMilestone ? 'bg-rose-500 ring-4 ring-rose-100' : 'bg-indigo-500'
                  }`}
                />

                {/* Encounter Card */}
                <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-xs hover:shadow-sm transition space-y-2.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-black text-slate-800">{point.date}</span>
                      <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                        point.facilityType === 'tertiary' ? 'bg-blue-100 text-blue-800' :
                        point.facilityType === 'district' ? 'bg-amber-100 text-amber-800' :
                        point.facilityType === 'hospice' ? 'bg-purple-100 text-purple-800' : 'bg-emerald-100 text-emerald-800'
                      }`}>
                        {point.facility}
                      </span>
                      {point.isKeyMilestone && (
                        <span className="px-2 py-0.5 rounded bg-rose-100 text-rose-800 text-[10px] font-extrabold uppercase">
                          Key Decision Milestone
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2 text-xs">
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-bold">
                        PPS {point.pps}%
                      </span>
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-bold">
                        ESAS {point.esas}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-bold">
                        OMEDD {point.omedd}mg
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                        point.ceilingTier === 1 ? 'bg-amber-100 text-amber-900' : 'bg-blue-100 text-blue-900'
                      }`}>
                        Tier {point.ceilingTier}
                      </span>
                    </div>
                  </div>

                  <h4 className="font-bold text-slate-900 text-sm">{point.milestoneTitle}</h4>

                  <p className="text-xs text-slate-600 leading-relaxed">
                    {point.clinicalSummary}
                  </p>

                  {/* Interventions */}
                  <div className="pt-2 border-t border-slate-100 space-y-1">
                    <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Key Clinical Actions & Decisions:</span>
                    <ul className="grid grid-cols-1 md:grid-cols-2 gap-1 text-xs text-slate-700">
                      {point.keyInterventions.map((action, aIdx) => (
                        <li key={aIdx} className="flex items-start gap-1.5">
                          <span className="text-emerald-600 font-bold">✓</span>
                          <span>{action}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div className="pt-2 flex items-center justify-between text-[11px] text-slate-400 border-t border-slate-50">
                    <span>Treating Clinician: <strong className="text-slate-600">{point.clinician}</strong> ({point.role})</span>
                    <span>✓ Authenticated Electronic Record</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopyHandoff}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied Handoff Packet!' : '📋 Copy Inter-Facility Handoff Summary'}
            </button>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 transition"
            >
              Close
            </button>
            <button
              onClick={() => {
                if (onExportHandoffPacket) onExportHandoffPacket();
                if (onClose) onClose();
              }}
              className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Export Continuity Packet</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
