'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldCheck,
  IconShieldAlert,
  IconCheckCircle,
  IconSparkles,
  IconClock,
  IconUsers,
  IconLungs,
} from './icons';

export type MmrcGrade = 0 | 1 | 2 | 3 | 4;

interface BreathlessnessCrisisModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchOrder?: (summary: string) => void;
}

export function BreathlessnessCrisisModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchOrder,
}: BreathlessnessCrisisModalProps) {
  const [activeTab, setActiveTab] = useState<'assessment' | 'pleural_lent' | 'crisis_protocol' | 'history'>('assessment');

  // Dyspnea Assessment State
  const [dyspneaScore, setDyspneaScore] = useState<number>(7); // 0-10 numeric scale
  const [mmrcGrade, setMmrcGrade] = useState<MmrcGrade>(3); // 0-4
  const [spo2Percent, setSpo2Percent] = useState<number>(93);
  const [respiratoryRate, setRespiratoryRate] = useState<number>(26);
  const [hasAnxietyLoop, setHasAnxietyLoop] = useState<boolean>(true);
  const [hasLymphangiticSpread, setHasLymphangiticSpread] = useState<boolean>(false);
  const [hasTrappedLung, setHasTrappedLung] = useState<boolean>(true);

  // Pleural Effusion & LENT Score State
  const [effusionVolumeMl, setEffusionVolumeMl] = useState<number>(1400); // 0-2500 mL
  const [pleuralLdhHigh, setPleuralLdhHigh] = useState<boolean>(true); // > 1500 IU/L (1 pt)
  const [ecogScore, setEcogScore] = useState<number>(2); // 0, 1, 2, or 3 (pts: 0, 1, 2, 3)
  const [nlrRatioHigh, setNlrRatioHigh] = useState<boolean>(true); // NLR >= 9 (1 pt)
  const [tumorRiskCategory, setTumorRiskCategory] = useState<number>(2); // 0 = Mesothelioma/heme, 1 = Breast/gyn, 2 = Lung/other

  // Interventions
  const [handheldFanDispatched, setHandheldFanDispatched] = useState<boolean>(true);
  const [morphineForDyspneaOrdered, setMorphineForDyspneaOrdered] = useState<boolean>(true);
  const [sublingualLorazepamOrdered, setSublingualLorazepamOrdered] = useState<boolean>(true);

  // Feedback
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchedToast, setDispatchedToast] = useState<boolean>(false);

  // Calculate LENT Score
  const lentScore = useMemo(() => {
    let score = 0;
    if (pleuralLdhHigh) score += 1;
    score += ecogScore;
    if (nlrRatioHigh) score += 1;
    score += tumorRiskCategory;
    return score; // Max 7
  }, [pleuralLdhHigh, ecogScore, nlrRatioHigh, tumorRiskCategory]);

  const lentStratification = useMemo(() => {
    if (lentScore >= 5) {
      return {
        category: 'HIGH RISK MPE (LENT 5–7)',
        medianSurvival: '44 Days (1.5 Months)',
        color: '#be123c',
        bg: '#ffe4e6',
        recommendation: 'Survival is significantly shortened. Avoid aggressive chemical pleurodesis requiring prolonged hospital chest-tube stay. Indwelling Pleural Catheter (IPC / PleurX) or simple palliative needle drainage is preferred for home comfort and symptom relief.',
      };
    }
    if (lentScore >= 2) {
      return {
        category: 'MODERATE RISK MPE (LENT 2–4)',
        medianSurvival: '130 Days (4.3 Months)',
        color: '#c2410c',
        bg: '#ffedd5',
        recommendation: 'Intermediate survival trajectory. If lung fully re-expands upon drainage, chemical talc slurry pleurodesis OR IPC are both evidence-based options. If lung is trapped/non-expandable, IPC is the definitive intervention.',
      };
    }
    return {
      category: 'LOW RISK MPE (LENT 0–1)',
      medianSurvival: '319 Days (10.6 Months)',
      color: '#047857',
      bg: '#d1fae5',
      recommendation: 'Favorable oncologic prognosis. Chemical talc pleurodesis or IPC with early autopleurodesis recommended. Combine with systemic antineoplastic therapy.',
    };
  }, [lentScore]);

  // Overall Dyspnea Urgency
  const dyspneaUrgency = useMemo(() => {
    if (dyspneaScore >= 8 || respiratoryRate >= 30 || spo2Percent < 88) {
      return {
        label: 'ACUTE AIR HUNGER CRISIS',
        color: '#be123c',
        bg: '#ffe4e6',
        badge: 'STAT ESCALATION REQUIRED',
      };
    }
    if (dyspneaScore >= 5 || respiratoryRate >= 24) {
      return {
        label: 'MODERATE DISTRESSING DYSPNEA',
        color: '#c2410c',
        bg: '#ffedd5',
        badge: 'ACTIVE RESCUE TITRATION',
      };
    }
    return {
      label: 'CONTROLLED EXERTIONAL DYSPNEA',
      color: '#047857',
      bg: '#d1fae5',
      badge: 'STABLE HOME PLAN',
    };
  }, [dyspneaScore, respiratoryRate, spo2Percent]);

  const handleCopyPlan = () => {
    const text = `PALLIATIVE BREATHLESSNESS & MALIGNANT PLEURAL EFFUSION (MPE) CLINICAL PROTOCOL
Patient: ${patientName} (${patientId}) · ${primaryCancer}
Dyspnea Severity: ${dyspneaScore}/10 · mMRC Grade: ${mmrcGrade}/4 · SpO2: ${spo2Percent}% on Room Air · RR: ${respiratoryRate} bpm
Clinical Urgency: ${dyspneaUrgency.label}
--------------------------------------------------
MPE LENT PROGNOSTIC SCORE: ${lentScore} / 7 (${lentStratification.category})
Estimated Pleural Fluid: ~${effusionVolumeMl} mL · Trapped Lung: ${hasTrappedLung ? 'Present (Non-expandable)' : 'Absent'}
Median Survival Expectation: ${lentStratification.medianSurvival}
DRAINAGE RECOMMENDATION:
1. Max Single Drainage: Never exceed 1,000–1,500 mL in one sitting (strict safety threshold to prevent Re-expansion Pulmonary Edema [RPE]).
2. Definitive Procedure: ${hasTrappedLung || lentScore >= 5 ? 'Indwelling Pleural Catheter (IPC / PleurX) for home drainage by ASHA/family.' : 'Diagnostic tap followed by Talc pleurodesis if lung expands fully.'}
--------------------------------------------------
DYSPNEA CRISIS INTERVENTIONS:
1. Non-Pharmacological (Cochrane Level 1A): Handheld cool air fan directed at nasal/cheek trigeminal distribution (V2/V3) for 5 mins. Forward-leaning posture with braced elbows.
2. Low-Dose Opioid for Air Hunger: Oral Morphine 2.5–5.0 mg PO (or SC 1.5–2.5 mg) q4h PRN. Decreases central breathlessness perception without hypercapnia.
3. Anxiolysis for Suffocation Panic: Lorazepam 0.5 mg SL PRN (breaks the dyspnea-anxiety cycle).
4. Oxygen Position: Supplemental O2 offers NO benefit over room air/fan in non-hypoxemic patients (SpO2 >= 90%).`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchOrder = () => {
    setDispatchedToast(true);
    if (onDispatchOrder) {
      onDispatchOrder(
        `Dyspnea Crisis Plan Dispatched: ${dyspneaUrgency.label} (${dyspneaScore}/10, ~${effusionVolumeMl}mL MPE, LENT: ${lentScore}/7)`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dyspnea-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-slate-900 via-sky-950 to-slate-900 text-white flex flex-wrap items-center justify-between gap-3 border-b border-sky-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sky-500/20 border border-sky-400/40 flex items-center justify-center text-sky-300">
              <IconLungs className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="dyspnea-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Palliative Breathlessness & Pleural Effusion (MPE) Suite
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-sky-500/20 text-sky-200 border border-sky-400/30">
                  BTS & EAPC Dyspnea Consensus
                </span>
              </div>
              <p className="text-xs text-sky-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: dyspneaUrgency.bg,
                color: dyspneaUrgency.color,
                borderColor: dyspneaUrgency.color,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>{dyspneaUrgency.label}</span>
            </div>
            <button
              onClick={onClose}
              className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white flex items-center justify-center transition"
              aria-label="Close modal"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="bg-slate-100 border-b border-slate-200 px-4 flex gap-2 overflow-x-auto">
          <button
            onClick={() => setActiveTab('assessment')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'assessment'
                ? 'border-sky-600 text-sky-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🫁 mMRC Dyspnea & Respiratory Vitals</span>
          </button>
          <button
            onClick={() => setActiveTab('pleural_lent')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'pleural_lent'
                ? 'border-sky-600 text-sky-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>💧 Pleural Effusion & LENT Score</span>
          </button>
          <button
            onClick={() => setActiveTab('crisis_protocol')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'crisis_protocol'
                ? 'border-sky-600 text-sky-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🌀 Multimodal Breathlessness Breathing Space</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-sky-600 text-sky-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Drainage & Thoracentesis Log</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: ASSESSMENT */}
          {activeTab === 'assessment' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Sliders & mMRC */}
              <div className="lg:col-span-7 space-y-4">
                {/* Numeric Distress Scale */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      1. Subjective Breathlessness Intensity (VAS 0–10)
                    </span>
                    <span className="text-sm font-extrabold text-sky-800">{dyspneaScore} / 10</span>
                  </div>

                  <input
                    type="range"
                    min="0"
                    max="10"
                    step="1"
                    value={dyspneaScore}
                    onChange={(e) => setDyspneaScore(parseInt(e.target.value))}
                    className="w-full accent-sky-600"
                  />
                  <div className="flex justify-between text-[10px] text-slate-400">
                    <span>0: No breathlessness</span>
                    <span>5: Moderately distressing</span>
                    <span>10: Severe suffocation panic</span>
                  </div>
                </div>

                {/* mMRC Functional Dyspnea Scale */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      2. mMRC Functional Limitation Grade
                    </span>
                    <span className="text-xs font-bold text-sky-700">Grade {mmrcGrade}</span>
                  </div>

                  <div className="grid grid-cols-1 gap-1.5 text-xs">
                    {[
                      { grade: 0, desc: 'Grade 0: Only with strenuous exercise' },
                      { grade: 1, desc: 'Grade 1: Hurrying on level ground or slight hill' },
                      { grade: 2, desc: 'Grade 2: Walks slower than peers or stops for breath on level' },
                      { grade: 3, desc: 'Grade 3: Stops for breath after walking 100 meters or few mins' },
                      { grade: 4, desc: 'Grade 4: Too breathless to leave house or breathless dressing/undressing' },
                    ].map((item) => (
                      <button
                        key={item.grade}
                        type="button"
                        onClick={() => setMmrcGrade(item.grade as MmrcGrade)}
                        className={`p-2 rounded-lg text-left border transition ${
                          mmrcGrade === item.grade
                            ? 'bg-sky-50 border-sky-500 text-sky-950 font-bold ring-2 ring-sky-200'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                        }`}
                      >
                        {item.desc}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Respiratory Vitals */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <label className="text-slate-600 font-medium block mb-1">Oxygen Saturation (SpO2)</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        min="70"
                        max="100"
                        value={spo2Percent}
                        onChange={(e) => setSpo2Percent(parseInt(e.target.value) || 0)}
                        className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                      />
                      <span className="font-semibold text-slate-500">%</span>
                    </div>
                    <span className={`text-[10px] font-bold mt-1 block ${spo2Percent < 90 ? 'text-rose-700' : 'text-emerald-700'}`}>
                      {spo2Percent < 90 ? '⚠️ True Hypoxemia (<90%)' : '✓ Normoxemic (Fan > O2)'}
                    </span>
                  </div>

                  <div>
                    <label className="text-slate-600 font-medium block mb-1">Respiratory Rate (RR)</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        min="8"
                        max="60"
                        value={respiratoryRate}
                        onChange={(e) => setRespiratoryRate(parseInt(e.target.value) || 0)}
                        className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                      />
                      <span className="font-semibold text-slate-500">bpm</span>
                    </div>
                    <span className={`text-[10px] font-bold mt-1 block ${respiratoryRate > 24 ? 'text-amber-700' : 'text-emerald-700'}`}>
                      {respiratoryRate > 24 ? 'Tachypnea (Air Hunger)' : 'Normal Rate'}
                    </span>
                  </div>
                </div>

                {/* Etiology Flags */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 space-y-2 text-xs">
                  <span className="font-bold text-slate-800 block">Contributing Clinical Etiologies:</span>
                  <div className="grid grid-cols-2 gap-2">
                    <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                      <input
                        type="checkbox"
                        checked={hasAnxietyLoop}
                        onChange={(e) => setHasAnxietyLoop(e.target.checked)}
                        className="w-4 h-4 text-sky-600 rounded"
                      />
                      <span>Suffocation Panic / Anxiety Loop</span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                      <input
                        type="checkbox"
                        checked={hasLymphangiticSpread}
                        onChange={(e) => setHasLymphangiticSpread(e.target.checked)}
                        className="w-4 h-4 text-sky-600 rounded"
                      />
                      <span>Lymphangitic Carcinomatosis</span>
                    </label>
                  </div>
                </div>
              </div>

              {/* Right Column: Urgency Gauge & Breathing Space Principles */}
              <div className="lg:col-span-5 space-y-4">
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition space-y-2"
                  style={{
                    backgroundColor: dyspneaUrgency.bg,
                    borderColor: dyspneaUrgency.color,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-700">Clinical Severity</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold text-white" style={{ backgroundColor: dyspneaUrgency.color }}>
                      {dyspneaUrgency.badge}
                    </span>
                  </div>

                  <div className="text-base font-black" style={{ color: dyspneaUrgency.color }}>
                    {dyspneaUrgency.label}
                  </div>

                  <p className="text-xs text-slate-800 leading-relaxed font-medium">
                    VAS score {dyspneaScore}/10 with mMRC grade {mmrcGrade}. Patient is experiencing severe respiratory distress. Initiate the 3-step &quot;Breathing Space&quot; bundle immediately.
                  </p>
                </div>

                {/* Breathing Space Quick Guide */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner space-y-3">
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="font-bold text-sky-300">Bedside Dyspnea De-escalation</span>
                    <span>Immediate Action</span>
                  </div>

                  <ul className="text-xs text-slate-300 space-y-2">
                    <li className="flex items-start gap-2">
                      <span className="text-sky-400 font-bold">1.</span>
                      <span><strong>Handheld Cool Fan:</strong> Direct air flow at cheeks/upper lip. Stimulates nasal cold receptors via trigeminal nerve, immediately suppressing sensation of suffocation.</span>
                    </li>
                    <li className="flex items-start gap-2">
                      <span className="text-sky-400 font-bold">2.</span>
                      <span><strong>Forward-Leaning Posture:</strong> Lean forward with forearms braced on a table/pillows. Fixes shoulder girdle and optimizes diaphragm biomechanics.</span>
                    </li>
                    <li className="flex items-start gap-2">
                      <span className="text-sky-400 font-bold">3.</span>
                      <span><strong>Low-Dose Opioid Bolus:</strong> Oral Morphine 2.5–5 mg PO / SC 1.5–2.5 mg relieves air hunger without causing CO2 retention.</span>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: PLEURAL EFFUSION & LENT */}
          {activeTab === 'pleural_lent' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: LENT Calculator */}
              <div className="lg:col-span-7 space-y-4">
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      LENT Prognostic Score for Malignant Pleural Effusion (MPE)
                    </span>
                    <span className="text-xs font-black text-slate-900 bg-white px-2.5 py-1 border rounded shadow-2xs">
                      LENT: {lentScore} / 7
                    </span>
                  </div>

                  {/* Criteria 1: LDH */}
                  <div className="flex items-center justify-between text-xs p-2.5 bg-white rounded-lg border">
                    <div>
                      <span className="font-bold text-slate-800 block">L: Pleural Fluid LDH &gt; 1500 IU/L</span>
                      <span className="text-[10px] text-slate-500">Indicator of high tumor metabolic activity</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={pleuralLdhHigh}
                      onChange={(e) => setPleuralLdhHigh(e.target.checked)}
                      className="w-4 h-4 text-sky-600 rounded"
                    />
                  </div>

                  {/* Criteria 2: ECOG */}
                  <div className="text-xs p-2.5 bg-white rounded-lg border space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-800">E: ECOG Performance Status</span>
                      <span className="font-extrabold text-sky-800">{ecogScore} pts</span>
                    </div>
                    <div className="grid grid-cols-4 gap-1.5">
                      {[
                        { score: 0, label: 'ECOG 0 (0 pt)' },
                        { score: 1, label: 'ECOG 1 (1 pt)' },
                        { score: 2, label: 'ECOG 2 (2 pts)' },
                        { score: 3, label: 'ECOG 3-4 (3 pts)' },
                      ].map((item) => (
                        <button
                          key={item.score}
                          type="button"
                          onClick={() => setEcogScore(item.score)}
                          className={`p-1.5 rounded text-[11px] font-semibold border transition ${
                            ecogScore === item.score
                              ? 'bg-sky-50 border-sky-500 text-sky-950 font-bold'
                              : 'text-slate-600'
                          }`}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Criteria 3: NLR */}
                  <div className="flex items-center justify-between text-xs p-2.5 bg-white rounded-lg border">
                    <div>
                      <span className="font-bold text-slate-800 block">N: Neutrophil-to-Lymphocyte Ratio (NLR) &ge; 9</span>
                      <span className="text-[10px] text-slate-500">Systemic neutrophilic inflammatory drive</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={nlrRatioHigh}
                      onChange={(e) => setNlrRatioHigh(e.target.checked)}
                      className="w-4 h-4 text-sky-600 rounded"
                    />
                  </div>

                  {/* Criteria 4: Tumor Type */}
                  <div className="text-xs p-2.5 bg-white rounded-lg border space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-800">T: Primary Tumor Risk Category</span>
                      <span className="font-extrabold text-sky-800">{tumorRiskCategory} pts</span>
                    </div>
                    <div className="grid grid-cols-3 gap-1.5">
                      {[
                        { score: 0, label: 'Low: Mesothelioma / Heme (0)' },
                        { score: 1, label: 'Mod: Breast / Gyn (1)' },
                        { score: 2, label: 'High: Lung / GI / Other (2)' },
                      ].map((item) => (
                        <button
                          key={item.score}
                          type="button"
                          onClick={() => setTumorRiskCategory(item.score)}
                          className={`p-1.5 rounded text-[11px] font-semibold border transition ${
                            tumorRiskCategory === item.score
                              ? 'bg-sky-50 border-sky-500 text-sky-950 font-bold'
                              : 'text-slate-600'
                          }`}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Fluid Volume Slider */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-800 uppercase">Estimated Pleural Fluid Volume</span>
                    <span className="font-extrabold text-sky-800">~{effusionVolumeMl} mL</span>
                  </div>
                  <input
                    type="range"
                    min="100"
                    max="2500"
                    step="50"
                    value={effusionVolumeMl}
                    onChange={(e) => setEffusionVolumeMl(parseInt(e.target.value))}
                    className="w-full accent-sky-600"
                  />
                  <div className="flex justify-between text-[10px] text-slate-400">
                    <span>Minimal (100-400mL)</span>
                    <span>Moderate Meniscus (800-1400mL)</span>
                    <span>Massive / White-out (&gt;1800mL)</span>
                  </div>
                </div>
              </div>

              {/* Right Column: Interactive SVG Thorax Model & LENT Stratification */}
              <div className="lg:col-span-5 space-y-4">
                {/* LENT Output Banner */}
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition space-y-2"
                  style={{
                    backgroundColor: lentStratification.bg,
                    borderColor: lentStratification.color,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-700">LENT Stratification</span>
                    <span className="text-xs font-extrabold text-slate-900">Median Survival: {lentStratification.medianSurvival}</span>
                  </div>
                  <div className="text-sm font-extrabold" style={{ color: lentStratification.color }}>
                    {lentStratification.category}
                  </div>
                  <p className="text-xs text-slate-800 leading-relaxed font-medium">
                    {lentStratification.recommendation}
                  </p>
                </div>

                {/* Interactive SVG Thorax Schematic */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner flex flex-col items-center">
                  <div className="w-full flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span className="font-semibold text-slate-200">Thoracic Cavity Fluid Model</span>
                    <span className="font-bold text-sky-300">{effusionVolumeMl} mL</span>
                  </div>

                  <div className="w-full max-w-[260px] aspect-square flex items-center justify-center py-2">
                    <svg viewBox="0 0 160 160" className="w-full h-full drop-shadow-md">
                      {/* Rib cage outline */}
                      <path
                        d="M 40 30 C 40 20 120 20 120 30 C 145 70 145 120 125 140 C 105 155 55 155 35 140 C 15 120 15 70 40 30 Z"
                        fill="#1e293b"
                        stroke="#475569"
                        strokeWidth="2"
                      />

                      {/* Trachea & Carina */}
                      <line x1="80" y1="20" x2="80" y2="50" stroke="#94a3b8" strokeWidth="4" />
                      <line x1="80" y1="50" x2="65" y2="70" stroke="#94a3b8" strokeWidth="3" />
                      <line x1="80" y1="50" x2="95" y2="70" stroke="#94a3b8" strokeWidth="3" />

                      {/* Normal Right Lung */}
                      <path
                        d="M 60 55 C 50 70 45 100 45 130 C 55 135 65 135 75 130 C 75 90 70 70 60 55 Z"
                        fill="#0284c7"
                        opacity="0.8"
                      />

                      {/* Compressed Left Lung (affected by effusion) */}
                      <path
                        d={`M 100 55 C 110 65 112 80 110 ${Math.max(80, 130 - effusionVolumeMl * 0.025)} C 100 ${Math.max(80, 130 - effusionVolumeMl * 0.025)} 90 ${Math.max(80, 130 - effusionVolumeMl * 0.025)} 85 ${Math.max(80, 130 - effusionVolumeMl * 0.025)} C 85 90 90 70 100 55 Z`}
                        fill="#0369a1"
                        opacity="0.9"
                      />

                      {/* Pleural Effusion Fluid Meniscus (Left base) */}
                      <path
                        d={`M 85 138 C 85 ${Math.max(80, 140 - effusionVolumeMl * 0.028)} 120 ${Math.max(70, 135 - effusionVolumeMl * 0.03)} 125 ${Math.max(75, 138 - effusionVolumeMl * 0.025)} C 120 142 95 142 85 138 Z`}
                        fill="#38bdf8"
                        opacity="0.85"
                        stroke="#7dd3fc"
                        strokeWidth="1.5"
                      />

                      {/* Re-expansion Safety Warning if > 1500mL */}
                      {effusionVolumeMl > 1500 && (
                        <text x="80" y="152" fill="#f87171" fontSize="7" fontWeight="bold" textAnchor="middle">
                          ⚠️ LIMIT DRAINAGE TO ≤1.5 L (AVOID RPE)
                        </text>
                      )}
                    </svg>
                  </div>

                  <div className="w-full bg-slate-800/80 rounded-lg p-2.5 mt-2 text-xs space-y-1">
                    <div className="flex justify-between text-slate-300">
                      <span>Drainage Safety Threshold:</span>
                      <span className="font-bold text-rose-400">Max 1.0–1.5 L per session</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Catheter Choice:</span>
                      <span className="font-bold text-sky-300">{hasTrappedLung ? 'IPC PleurX (Trapped Lung)' : 'Talc vs IPC'}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: CRISIS PROTOCOL */}
          {activeTab === 'crisis_protocol' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-gradient-to-r from-sky-50 to-blue-50 border border-sky-200 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-sky-600 text-white mt-0.5">
                  <IconSparkles className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-sky-950">Evidence-Based Dyspnea Crisis Standing Orders</h3>
                  <p className="text-xs text-sky-800 mt-0.5">
                    Combining Cochrane Level 1A non-pharmacological facial stimulation with judicious central air-hunger opioid titration.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                {/* Handheld Fan Protocol */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-sky-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[11px] font-extrabold bg-sky-100 text-sky-800">COCHRANE LEVEL 1A</span>
                    <label className="flex items-center gap-1.5 font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={handheldFanDispatched}
                        onChange={(e) => setHandheldFanDispatched(e.target.checked)}
                        className="w-4 h-4 text-sky-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <h4 className="text-sm font-extrabold text-slate-900">Handheld Cool Air Facial Fan Protocol</h4>
                  <p className="text-slate-600 leading-relaxed">
                    Direct a battery-operated cool air fan across the lower face, cheeks, and nose from 15–20 cm away for 5 minutes during an air hunger crisis.
                  </p>
                  <div className="bg-sky-50/70 p-2.5 rounded border border-sky-100 text-slate-700 space-y-1">
                    <div className="font-bold text-sky-900">Neurobiological Basis:</div>
                    <p className="text-[11px]">
                      Stimulates facial mechanoreceptors and cold-sensitive thermoreceptors innervated by the trigeminal nerve (V2/V3), modifying central sensory processing in the sensory cortex and immediately blunting the subjective sensation of breathlessness.
                    </p>
                  </div>
                </div>

                {/* Opioid for Dyspnea */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-sky-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[11px] font-extrabold bg-indigo-100 text-indigo-800">CENTRAL SENSITIVITY</span>
                    <label className="flex items-center gap-1.5 font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={morphineForDyspneaOrdered}
                        onChange={(e) => setMorphineForDyspneaOrdered(e.target.checked)}
                        className="w-4 h-4 text-sky-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <h4 className="text-sm font-extrabold text-slate-900">Low-Dose Morphine for Air Hunger</h4>
                  <p className="text-slate-600 leading-relaxed">
                    Oral Morphine 2.5–5.0 mg PO (or Subcutaneous Morphine 1.5–2.5 mg) every 4 hours PRN. If already on regular baseline opioids, give 25–50% of the normal breakthrough pain dose.
                  </p>
                  <div className="bg-indigo-50/70 p-2.5 rounded border border-indigo-100 text-slate-700 space-y-1">
                    <div className="font-bold text-indigo-900">Safety & Mechanism:</div>
                    <p className="text-[11px]">
                      Reduces medullary respiratory drive response to hypercapnia and emotional air hunger without compromising arterial blood gas oxygenation when dosed conservatively.
                    </p>
                  </div>
                </div>

                {/* Sublingual Lorazepam */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-sky-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[11px] font-extrabold bg-purple-100 text-purple-800">ANXIOLYTIC LOOP BREAKER</span>
                    <label className="flex items-center gap-1.5 font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={sublingualLorazepamOrdered}
                        onChange={(e) => setSublingualLorazepamOrdered(e.target.checked)}
                        className="w-4 h-4 text-sky-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <h4 className="text-sm font-extrabold text-slate-900">Sublingual Lorazepam 0.5 mg PRN</h4>
                  <p className="text-slate-600 leading-relaxed">
                    Dyspnea triggers severe acute panic, which increases oxygen consumption and worsens tachypnea in a vicious cycle. Sublingual lorazepam rapidly breaks the panic feedback loop.
                  </p>
                </div>

                {/* Oxygen vs Room Air Guidance */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-sky-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[11px] font-extrabold bg-amber-100 text-amber-800">OXYGEN STEWARDSHIP</span>
                    <span className="text-slate-500 font-bold">Guidelines</span>
                  </div>
                  <h4 className="text-sm font-extrabold text-slate-900">Room Air / Fan vs Supplemental O2</h4>
                  <p className="text-slate-600 leading-relaxed">
                    Multicenter RCTs (Abercombie et al.) demonstrate that supplemental oxygen provides NO additional symptom relief over room air delivered by a fan in non-hypoxemic patients (SpO2 &ge; 90%). Oxygen therapy can increase nasal mucosal dryness, claustrophobia, and tethering. Reserve O2 for true hypoxemia (SpO2 &lt; 90%).
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: HISTORY */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Malignant Pleural Drainage & Dyspnea Log</h3>
                <span className="text-xs text-slate-500">Kidwai Thoracic Oncology</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date</th>
                      <th className="p-2.5">Procedure / Regimen</th>
                      <th className="p-2.5">Volume Drained</th>
                      <th className="p-2.5">Dyspnea VAS</th>
                      <th className="p-2.5">Complications</th>
                      <th className="p-2.5">Clinician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-sky-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-bold text-sky-800">Therapeutic Thoracentesis Scheduled</td>
                      <td className="p-2.5 font-medium">1,200 mL Target</td>
                      <td className="p-2.5 font-extrabold text-rose-700">{dyspneaScore}/10 (Severe)</td>
                      <td className="p-2.5 text-emerald-700 font-semibold">Pre-procedural</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">03 Sep 2026</td>
                      <td className="p-2.5">Ultrasound Thoracentesis</td>
                      <td className="p-2.5">1,100 mL Amber Exudate</td>
                      <td className="p-2.5 text-slate-500">Decreased from 8/10 to 3/10</td>
                      <td className="p-2.5 text-emerald-700">None (Cough reflex managed)</td>
                      <td className="p-2.5 text-slate-600">Kidwai Interventional Pulm</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">19 Aug 2026</td>
                      <td className="p-2.5">Handheld Fan + Morphine 5mg</td>
                      <td className="p-2.5 text-slate-400">Medical management</td>
                      <td className="p-2.5 text-slate-500">Decreased from 7/10 to 4/10</td>
                      <td className="p-2.5 text-emerald-700">None</td>
                      <td className="p-2.5 text-slate-600">Pushpa Gowda (Nurse)</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopyPlan}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-xs"
            >
              {copiedToast ? '✓ Copied Dyspnea Plan!' : '📋 Copy Dyspnea & MPE Plan'}
            </button>
            <button
              onClick={handleDispatchOrder}
              className="px-4 py-2 bg-sky-700 hover:bg-sky-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Dyspnea Protocol!' : '🫁 Dispatch STAT Dyspnea Emergency Protocol'}</span>
            </button>
          </div>

          <button
            onClick={onClose}
            className="px-5 py-2 bg-slate-800 hover:bg-slate-900 text-white rounded-lg text-xs font-bold transition shadow-xs"
          >
            Done / Close
          </button>
        </div>
      </div>
    </div>
  );
}
