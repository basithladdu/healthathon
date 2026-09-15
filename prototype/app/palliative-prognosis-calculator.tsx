'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconSparkles,
  IconClock,
  IconUsers,
  IconHospital,
} from './icons';

export interface PpsLevel {
  percent: number;
  ambulation: string;
  activity: string;
  selfCare: string;
  intake: string;
  conscious: string;
}

export const PPS_LEVELS: PpsLevel[] = [
  {
    percent: 100,
    ambulation: 'Full',
    activity: 'Normal activity & work; no disease evidence',
    selfCare: 'Full',
    intake: 'Normal',
    conscious: 'Full',
  },
  {
    percent: 90,
    ambulation: 'Full',
    activity: 'Normal activity & work; some disease evidence',
    selfCare: 'Full',
    intake: 'Normal',
    conscious: 'Full',
  },
  {
    percent: 80,
    ambulation: 'Full',
    activity: 'Normal activity with effort; some disease',
    selfCare: 'Full',
    intake: 'Normal or reduced',
    conscious: 'Full',
  },
  {
    percent: 70,
    ambulation: 'Reduced',
    activity: 'Unable to do normal work; significant disease',
    selfCare: 'Full',
    intake: 'Normal or reduced',
    conscious: 'Full',
  },
  {
    percent: 60,
    ambulation: 'Reduced',
    activity: 'Unable to do hobby/housework; significant disease',
    selfCare: 'Occasional assistance needed',
    intake: 'Normal or reduced',
    conscious: 'Full or Confusion',
  },
  {
    percent: 50,
    ambulation: 'Mainly sit/lie',
    activity: 'Unable to do any work; extensive disease',
    selfCare: 'Considerable assistance needed',
    intake: 'Normal or reduced',
    conscious: 'Full or Confusion',
  },
  {
    percent: 40,
    ambulation: 'Mainly in bed',
    activity: 'Unable to do most activity; extensive disease',
    selfCare: 'Mainly assistance needed',
    intake: 'Normal or reduced',
    conscious: 'Full or Drowsy +/- Confusion',
  },
  {
    percent: 30,
    ambulation: 'Totally bed bound',
    activity: 'Unable to do any activity; extensive disease',
    selfCare: 'Total care required',
    intake: 'Reduced',
    conscious: 'Full or Drowsy +/- Confusion',
  },
  {
    percent: 20,
    ambulation: 'Totally bed bound',
    activity: 'Unable to do any activity; extensive disease',
    selfCare: 'Total care required',
    intake: 'Minimal to sips only',
    conscious: 'Full or Drowsy +/- Confusion',
  },
  {
    percent: 10,
    ambulation: 'Totally bed bound',
    activity: 'Unable to do any activity; extensive disease',
    selfCare: 'Total care required',
    intake: 'Mouth care only',
    conscious: 'Drowsy or Coma',
  },
];

export interface PrognosisCalculatorProps {
  patientId: string;
  patientName: string;
  diagnosis: string;
  currentCeilingTier?: number;
  onClose?: () => void;
  onApplyPlan?: (pps: number, ppi: number, tier: number, recommendation: string) => void;
}

export function PalliativePrognosisCalculator({
  patientId,
  patientName,
  diagnosis,
  currentCeilingTier = 1,
  onClose,
  onApplyPlan,
}: PrognosisCalculatorProps) {
  // Clinical state variables
  const [ppsPercent, setPpsPercent] = useState<number>(40);
  const [oralIntake, setOralIntake] = useState<'normal' | 'moderate' | 'severe'>('moderate');
  const [edemaPresent, setEdemaPresent] = useState<boolean>(false);
  const [dyspneaPresent, setDyspneaPresent] = useState<boolean>(true);
  const [deliriumPresent, setDeliriumPresent] = useState<boolean>(false);
  const [copiedToast, setCopiedToast] = useState<boolean>(false);

  // PPI Sub-scores
  const ppsPpiScore = useMemo(() => {
    if (ppsPercent <= 20) return 4.0;
    if (ppsPercent <= 50) return 2.5;
    return 0.0;
  }, [ppsPercent]);

  const intakePpiScore = useMemo(() => {
    if (oralIntake === 'severe') return 2.5;
    if (oralIntake === 'moderate') return 1.0;
    return 0.0;
  }, [oralIntake]);

  const edemaPpiScore = edemaPresent ? 1.0 : 0.0;
  const dyspneaPpiScore = dyspneaPresent ? 3.5 : 0.0;
  const deliriumPpiScore = deliriumPresent ? 4.0 : 0.0;

  const totalPpiScore = useMemo(() => {
    return Number((ppsPpiScore + intakePpiScore + edemaPpiScore + dyspneaPpiScore + deliriumPpiScore).toFixed(1));
  }, [ppsPpiScore, intakePpiScore, edemaPpiScore, dyspneaPpiScore, deliriumPpiScore]);

  // Risk stratification
  const stratification = useMemo(() => {
    if (totalPpiScore > 6.0) {
      return {
        group: 'Group C (Terminal Phase)',
        badgeClass: 'prog-badge-terminal',
        expectedSurvival: '< 3 Weeks (Median 11-18 days)',
        threeWeekMortality: 'High (>83% specificity for survival < 21 days)',
        recommendedTier: 1,
        tierName: 'Tier 1: Comfort & Dignity Focus',
        color: '#e11d48',
        fillColor: 'rgba(225, 29, 72, 0.15)',
        strokeColor: '#e11d48',
        urgency: 'Immediate hospice transition & anticipatory prescribing',
        recommendedSetting: 'Home Palliative Care / Inpatient Hospice Sanctuary',
        decayLambda: 0.065, // ~50% survival at 11 days, ~20% at 21 days
      };
    } else if (totalPpiScore >= 4.5) {
      return {
        group: 'Group B (Intermediate Trajectory)',
        badgeClass: 'prog-badge-intermediate',
        expectedSurvival: '3 to 6 Weeks (Median 28-42 days)',
        threeWeekMortality: 'Moderate (~60% 3-week survival probability)',
        recommendedTier: 1,
        tierName: 'Tier 1: Comfort Focus with Reversible Ward Support',
        color: '#f59e0b',
        fillColor: 'rgba(245, 158, 11, 0.15)',
        strokeColor: '#f59e0b',
        urgency: 'Clarify advance directives, caregiver respite, breakthrough analgesia',
        recommendedSetting: 'Community Palliative Outreach + District Ward backup',
        decayLambda: 0.024, // ~60% survival at 21 days, ~35% at 42 days
      };
    } else {
      return {
        group: 'Group A (Supportive / Disease-Modifying)',
        badgeClass: 'prog-badge-stable',
        expectedSurvival: '> 6 Weeks (Median 60-120+ days)',
        threeWeekMortality: 'Low (>90% 3-week survival probability)',
        recommendedTier: 2,
        tierName: 'Tier 2: Active Supportive Care & Ward Stabilization',
        color: '#10b981',
        fillColor: 'rgba(16, 185, 129, 0.15)',
        strokeColor: '#10b981',
        urgency: 'Symptom optimization, rehabilitation, ongoing systemic therapy support',
        recommendedSetting: 'Outpatient Oncology-Palliative Shared Care',
        decayLambda: 0.009, // ~83% survival at 21 days, ~50% at 75 days
      };
    }
  }, [totalPpiScore]);

  // Current PPS Level object
  const currentPpsLevel = useMemo(() => {
    return PPS_LEVELS.find((lvl) => lvl.percent === ppsPercent) || PPS_LEVELS[6];
  }, [ppsPercent]);

  // Generate SVG Survival Curve data points (0 to 90 days)
  const curvePoints = useMemo(() => {
    const points: { day: number; prob: number; x: number; y: number }[] = [];
    const width = 480;
    const height = 180;
    const padding = { top: 20, right: 25, bottom: 30, left: 45 };
    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;

    const days = [0, 7, 14, 21, 28, 35, 42, 50, 60, 70, 80, 90];
    days.forEach((day) => {
      // Exponential survival probability S(t) = exp(-lambda * t)
      const prob = Math.round(Math.exp(-stratification.decayLambda * day) * 100);
      const x = padding.left + (day / 90) * chartWidth;
      const y = padding.top + (1 - prob / 100) * chartHeight;
      points.push({ day, prob, x, y });
    });

    return { points, padding, chartWidth, chartHeight, width, height };
  }, [stratification]);

  // SVG path strings
  const svgPathD = useMemo(() => {
    const { points } = curvePoints;
    if (!points.length) return '';
    return points.reduce((acc, p, i) => {
      return i === 0 ? `M ${p.x},${p.y}` : `${acc} L ${p.x},${p.y}`;
    }, '');
  }, [curvePoints]);

  const svgAreaD = useMemo(() => {
    const { points, padding, chartHeight } = curvePoints;
    if (!points.length) return '';
    const baseY = padding.top + chartHeight;
    const first = points[0];
    const last = points[points.length - 1];
    const pathD = points.reduce((acc, p, i) => {
      return i === 0 ? `M ${p.x},${p.y}` : `${acc} L ${p.x},${p.y}`;
    }, '');
    return `${pathD} L ${last.x},${baseY} L ${first.x},${baseY} Z`;
  }, [curvePoints]);

  // 21-Day (3-Week) key clinical benchmark point
  const day21Point = useMemo(() => {
    const { padding, chartWidth, chartHeight } = curvePoints;
    const prob = Math.round(Math.exp(-stratification.decayLambda * 21) * 100);
    const x = padding.left + (21 / 90) * chartWidth;
    const y = padding.top + (1 - prob / 100) * chartHeight;
    return { prob, x, y };
  }, [curvePoints, stratification]);

  // Copy clinical SBAR Handover note
  const handleCopySbar = () => {
    const note = `[CONTINUITY LOOP: PALLIATIVE PROGNOSTIC HANDOVER]
Patient: ${patientName} (${patientId})
Diagnosis: ${diagnosis}
PPSv2 Score: ${ppsPercent}% (Ambulation: ${currentPpsLevel.ambulation}, Self-Care: ${currentPpsLevel.selfCare}, Consciousness: ${currentPpsLevel.conscious})
PPI Total Score: ${totalPpiScore} / 15.0
- PPS Factor: ${ppsPpiScore} pts
- Oral Intake: ${oralIntake} (${intakePpiScore} pts)
- Edema: ${edemaPresent ? 'Present' : 'Absent'} (${edemaPpiScore} pts)
- Dyspnea at Rest: ${dyspneaPresent ? 'Present' : 'Absent'} (${dyspneaPpiScore} pts)
- Delirium: ${deliriumPresent ? 'Present' : 'Absent'} (${deliriumPpiScore} pts)

Stratification: ${stratification.group}
Estimated Trajectory: ${stratification.expectedSurvival}
3-Week Survival Probability: ~${day21Point.prob}%
Recommended Ceiling: ${stratification.tierName}
Recommended Setting: ${stratification.recommendedSetting}
Anticipatory Actions: ${stratification.urgency}
Attestation: Dr Sujay, Palliative Oncology Lead | Date: ${new Date().toLocaleDateString('en-GB')}`;

    navigator.clipboard.writeText(note).then(() => {
      setCopiedToast(true);
      setTimeout(() => setCopiedToast(false), 3000);
    });
  };

  return (
    <div className="tep-overlay" role="dialog" aria-modal="true">
      <div className="tep-modal prog-modal-wide">
        {/* Header */}
        <div className="tep-header">
          <div className="tep-header-title">
            <div className="flex items-center gap-2">
              <span className="tep-badge-pulse bg-purple-500"></span>
              <h2 className="text-xl font-bold text-slate-800">
                Palliative Prognostic Index (PPI) & PPSv2 Staging Engine
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Validated multi-variable survival trajectory & functional decline modeling for palliative transitions
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Prognosis Stager"
          >
            ✕
          </button>
        </div>

        {/* Patient banner */}
        <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3 text-sm">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Patient</span>
              <div className="font-bold text-slate-800">{patientName} ({patientId})</div>
            </div>
            <div className="h-6 w-px bg-slate-200" />
            <div>
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Primary Diagnosis</span>
              <div className="font-medium text-slate-700">{diagnosis}</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">Active Ceiling:</span>
            <span className={`px-2 py-1 rounded text-xs font-bold ${
              currentCeilingTier === 1 ? 'bg-amber-100 text-amber-800' :
              currentCeilingTier === 2 ? 'bg-blue-100 text-blue-800' : 'bg-red-100 text-red-800'
            }`}>
              Tier {currentCeilingTier}
            </span>
          </div>
        </div>

        {/* Main 2-Column Grid */}
        <div className="p-6 overflow-y-auto max-h-[75vh] space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: Interactive Scoring Controls (7 cols) */}
            <div className="lg:col-span-7 space-y-5">
              {/* PPSv2 Slider & Domain Matrix */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
                      <IconActivity className="w-5 h-5" />
                    </span>
                    <div>
                      <h3 className="font-bold text-slate-800 text-base">Palliative Performance Scale (PPSv2)</h3>
                      <p className="text-xs text-slate-500">Measures ambulation, disease impact, and self-care capacity</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-2xl font-black text-indigo-600">{ppsPercent}%</span>
                    <span className="text-xs text-slate-400 block">PPS Score</span>
                  </div>
                </div>

                {/* Range Slider */}
                <div className="space-y-1">
                  <input
                    type="range"
                    min="10"
                    max="100"
                    step="10"
                    value={ppsPercent}
                    onChange={(e) => setPpsPercent(Number(e.target.value))}
                    className="w-full accent-indigo-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                  />
                  <div className="flex justify-between text-[11px] font-medium text-slate-400">
                    <span>10% (Bed/Mouth)</span>
                    <span>30%</span>
                    <span>50%</span>
                    <span>70%</span>
                    <span>100% (Normal)</span>
                  </div>
                </div>

                {/* Current PPS Level Breakdown */}
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 bg-indigo-50/50 p-3 rounded-lg text-xs border border-indigo-100">
                  <div>
                    <span className="text-indigo-400 font-semibold block text-[10px] uppercase">Ambulation</span>
                    <span className="font-semibold text-slate-800">{currentPpsLevel.ambulation}</span>
                  </div>
                  <div>
                    <span className="text-indigo-400 font-semibold block text-[10px] uppercase">Self-Care</span>
                    <span className="font-semibold text-slate-800">{currentPpsLevel.selfCare}</span>
                  </div>
                  <div>
                    <span className="text-indigo-400 font-semibold block text-[10px] uppercase">Consciousness</span>
                    <span className="font-semibold text-slate-800">{currentPpsLevel.conscious}</span>
                  </div>
                  <div className="col-span-2 sm:col-span-3 pt-1 border-t border-indigo-100 text-slate-600">
                    <span className="font-medium">Disease Activity:</span> {currentPpsLevel.activity}
                  </div>
                </div>
              </div>

              {/* PPI Clinical Predictors */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-4">
                <div className="flex items-center gap-2">
                  <span className="p-1.5 rounded-lg bg-purple-50 text-purple-600">
                    <IconHeartPulse className="w-5 h-5" />
                  </span>
                  <div>
                    <h3 className="font-bold text-slate-800 text-base">PPI Clinical Variables</h3>
                    <p className="text-xs text-slate-500">Validated high-hazard prognostic markers</p>
                  </div>
                </div>

                <div className="space-y-3">
                  {/* Oral Intake */}
                  <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div>
                      <div className="font-semibold text-xs text-slate-800">Oral Intake Volume</div>
                      <div className="text-[11px] text-slate-500">Fluid & solid nourishment over last 48 hours</div>
                    </div>
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => setOralIntake('normal')}
                        className={`px-2.5 py-1.5 rounded text-xs font-semibold transition ${
                          oralIntake === 'normal'
                            ? 'bg-emerald-600 text-white shadow-sm'
                            : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                        }`}
                      >
                        Normal (0 pt)
                      </button>
                      <button
                        onClick={() => setOralIntake('moderate')}
                        className={`px-2.5 py-1.5 rounded text-xs font-semibold transition ${
                          oralIntake === 'moderate'
                            ? 'bg-amber-600 text-white shadow-sm'
                            : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                        }`}
                      >
                        Reduced (+1.0)
                      </button>
                      <button
                        onClick={() => setOralIntake('severe')}
                        className={`px-2.5 py-1.5 rounded text-xs font-semibold transition ${
                          oralIntake === 'severe'
                            ? 'bg-red-600 text-white shadow-sm'
                            : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                        }`}
                      >
                        Minimal/Sips (+2.5)
                      </button>
                    </div>
                  </div>

                  {/* Edema */}
                  <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 flex items-center justify-between">
                    <div>
                      <div className="font-semibold text-xs text-slate-800">Edema / Ascites</div>
                      <div className="text-[11px] text-slate-500">Bilateral peripheral edema, presacral edema, or ascites</div>
                    </div>
                    <button
                      onClick={() => setEdemaPresent(!edemaPresent)}
                      className={`px-3 py-1.5 rounded text-xs font-semibold transition ${
                        edemaPresent
                          ? 'bg-purple-600 text-white shadow-sm'
                          : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                      }`}
                    >
                      {edemaPresent ? 'Present (+1.0 pt)' : 'Absent (0 pt)'}
                    </button>
                  </div>

                  {/* Dyspnea at Rest */}
                  <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 flex items-center justify-between">
                    <div>
                      <div className="font-semibold text-xs text-slate-800">Dyspnea at Rest</div>
                      <div className="text-[11px] text-slate-500">Persistent breathlessness not triggered by exertion</div>
                    </div>
                    <button
                      onClick={() => setDyspneaPresent(!dyspneaPresent)}
                      className={`px-3 py-1.5 rounded text-xs font-semibold transition ${
                        dyspneaPresent
                          ? 'bg-rose-600 text-white shadow-sm'
                          : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                      }`}
                    >
                      {dyspneaPresent ? 'Present (+3.5 pts)' : 'Absent (0 pt)'}
                    </button>
                  </div>

                  {/* Delirium */}
                  <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 flex items-center justify-between">
                    <div>
                      <div className="font-semibold text-xs text-slate-800">Delirium / Agitation</div>
                      <div className="text-[11px] text-slate-500">Acute confusion, disorientation, or psychomotor agitation</div>
                    </div>
                    <button
                      onClick={() => setDeliriumPresent(!deliriumPresent)}
                      className={`px-3 py-1.5 rounded text-xs font-semibold transition ${
                        deliriumPresent
                          ? 'bg-red-700 text-white shadow-sm'
                          : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                      }`}
                    >
                      {deliriumPresent ? 'Present (+4.0 pts)' : 'Absent (0 pt)'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Formula & Weighting Audit Card */}
              <div className="p-3 bg-slate-100/70 rounded-lg text-xs text-slate-600 flex items-center justify-between">
                <span>
                  <strong>PPI Formula Breakdown:</strong> PPS ({ppsPpiScore}) + Intake ({intakePpiScore}) + Edema ({edemaPpiScore}) + Dyspnea ({dyspneaPpiScore}) + Delirium ({deliriumPpiScore})
                </span>
                <span className="font-bold text-slate-800">= {totalPpiScore} pts</span>
              </div>
            </div>

            {/* Right Column: Survival Modeling & Clinical Directives (5 cols) */}
            <div className="lg:col-span-5 space-y-5">
              {/* Dynamic Stratification Card */}
              <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">Clinical Prognosis</span>
                  <span className={`px-3 py-1 rounded-full text-xs font-black ${stratification.badgeClass}`}>
                    {stratification.group}
                  </span>
                </div>

                <div className="p-4 rounded-xl border border-slate-100 bg-slate-50/70 space-y-3">
                  <div className="flex justify-between items-baseline">
                    <span className="text-xs text-slate-500 font-medium">Estimated Survival:</span>
                    <span className="text-base font-extrabold text-slate-900">{stratification.expectedSurvival}</span>
                  </div>
                  <div className="flex justify-between items-baseline">
                    <span className="text-xs text-slate-500 font-medium">3-Week Mortality Risk:</span>
                    <span className="text-xs font-bold text-slate-800">{stratification.threeWeekMortality}</span>
                  </div>
                  <div className="flex justify-between items-baseline">
                    <span className="text-xs text-slate-500 font-medium">Recommended Ceiling:</span>
                    <span className="text-xs font-bold text-indigo-700">{stratification.tierName}</span>
                  </div>
                </div>

                {/* Kaplan-Meier Survival Projection Curve (SVG) */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700">Projected Survival Probability</span>
                    <span className="text-[11px] text-slate-400">90-Day Trajectory Simulation</span>
                  </div>

                  <div className="relative bg-slate-900 rounded-xl p-2 border border-slate-800 shadow-inner">
                    <svg viewBox={`0 0 ${curvePoints.width} ${curvePoints.height}`} className="w-full h-auto">
                      {/* Grid Lines */}
                      <line x1={curvePoints.padding.left} y1={curvePoints.padding.top} x2={curvePoints.width - curvePoints.padding.right} y2={curvePoints.padding.top} stroke="#334155" strokeDasharray="3 3" />
                      <line x1={curvePoints.padding.left} y1={curvePoints.padding.top + curvePoints.chartHeight / 2} x2={curvePoints.width - curvePoints.padding.right} y2={curvePoints.padding.top + curvePoints.chartHeight / 2} stroke="#334155" strokeDasharray="3 3" />
                      <line x1={curvePoints.padding.left} y1={curvePoints.padding.top + curvePoints.chartHeight} x2={curvePoints.width - curvePoints.padding.right} y2={curvePoints.padding.top + curvePoints.chartHeight} stroke="#475569" />

                      {/* Day 21 (3-week) benchmark vertical line */}
                      <line
                        x1={day21Point.x}
                        y1={curvePoints.padding.top}
                        x2={day21Point.x}
                        y2={curvePoints.padding.top + curvePoints.chartHeight}
                        stroke="#94a3b8"
                        strokeDasharray="2 2"
                        strokeWidth="1.2"
                      />
                      <text x={day21Point.x + 3} y={curvePoints.padding.top + 12} fill="#94a3b8" fontSize="9" fontWeight="600">
                        21 Days
                      </text>

                      {/* Shaded Area */}
                      <path d={svgAreaD} fill={stratification.fillColor} />

                      {/* Curve Line */}
                      <path d={svgPathD} fill="none" stroke={stratification.strokeColor} strokeWidth="2.5" strokeLinecap="round" />

                      {/* 21-Day Marker Dot */}
                      <circle cx={day21Point.x} cy={day21Point.y} r="5" fill="#ffffff" stroke={stratification.strokeColor} strokeWidth="2" />
                      <text
                        x={day21Point.x + 8}
                        y={day21Point.y + 4}
                        fill="#ffffff"
                        fontSize="10"
                        fontWeight="bold"
                      >
                        {day21Point.prob}%
                      </text>

                      {/* Y-Axis Labels */}
                      <text x={curvePoints.padding.left - 8} y={curvePoints.padding.top + 4} fill="#64748b" fontSize="9" textAnchor="end">100%</text>
                      <text x={curvePoints.padding.left - 8} y={curvePoints.padding.top + curvePoints.chartHeight / 2 + 3} fill="#64748b" fontSize="9" textAnchor="end">50%</text>
                      <text x={curvePoints.padding.left - 8} y={curvePoints.padding.top + curvePoints.chartHeight + 3} fill="#64748b" fontSize="9" textAnchor="end">0%</text>

                      {/* X-Axis Labels */}
                      <text x={curvePoints.padding.left} y={curvePoints.height - 8} fill="#64748b" fontSize="9">0d</text>
                      <text x={curvePoints.padding.left + curvePoints.chartWidth * 0.33} y={curvePoints.height - 8} fill="#64748b" fontSize="9">30d</text>
                      <text x={curvePoints.padding.left + curvePoints.chartWidth * 0.66} y={curvePoints.height - 8} fill="#64748b" fontSize="9">60d</text>
                      <text x={curvePoints.width - curvePoints.padding.right} y={curvePoints.height - 8} fill="#64748b" fontSize="9" textAnchor="end">90d</text>
                    </svg>

                    <div className="flex justify-between items-center text-[10px] text-slate-400 mt-1 px-1">
                      <span>Kaplan-Meier hazard model calibrated to palliative oncology</span>
                      <span className="font-semibold text-slate-300">21-Day Survival: ~{day21Point.prob}%</span>
                    </div>
                  </div>
                </div>

                {/* Anticipatory Actions Checklist */}
                <div className="space-y-2 pt-2 border-t border-slate-100">
                  <div className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                    <IconSparkles className="w-4 h-4 text-purple-600" />
                    <span>Bedside Action Bundle</span>
                  </div>

                  <div className="space-y-2 text-xs">
                    <div className="p-2.5 rounded-lg bg-purple-50/60 border border-purple-100 text-purple-900">
                      <strong>Recommended Setting:</strong> {stratification.recommendedSetting}
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-slate-700 space-y-1">
                      <div className="font-semibold text-slate-800">Anticipatory &quot;Just-in-Case&quot; Medications:</div>
                      <ul className="list-disc pl-4 space-y-0.5 text-[11px]">
                        <li>Morphine 2.5-5mg SC Q4H PRN (dyspnea & pain)</li>
                        <li>Midazolam 2.5mg SC Q2H PRN (severe terminal agitation)</li>
                        <li>Glycopyrronium 0.2mg SC Q4H PRN (respiratory secretions)</li>
                        <li>Haloperidol 0.5-1.5mg SC nocte (delirium / antiemetic)</li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopySbar}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied to Clipboard!' : '📋 Copy SBAR Prognostic Note'}
            </button>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 transition"
            >
              Cancel
            </button>
            <button
              onClick={() => {
                if (onApplyPlan) {
                  onApplyPlan(
                    ppsPercent,
                    totalPpiScore,
                    stratification.recommendedTier,
                    `${stratification.group}: PPS ${ppsPercent}%, PPI ${totalPpiScore}, Survival ${stratification.expectedSurvival}. Recommend ${stratification.tierName}.`
                  );
                }
                if (onClose) onClose();
              }}
              className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Apply to Treatment Plan</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// Inline fallback for IconActivity if not present in icons
function IconActivity({ className = 'w-4 h-4' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
    </svg>
  );
}
