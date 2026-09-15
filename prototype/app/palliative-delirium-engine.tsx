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
} from './icons';

export type DeliriumSubtype = 'hyperactive' | 'hypoactive' | 'mixed';

export interface NudescDomain {
  id: string;
  name: string;
  hindiName: string;
  score: number; // 0, 1, or 2
  descriptions: string[];
}

interface PalliativeDeliriumModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function PalliativeDeliriumModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: PalliativeDeliriumModalProps) {
  const [activeTab, setActiveTab] = useState<'nudesc_screen' | 'reversible_causes' | 'management' | 'counseling' | 'history'>('nudesc_screen');

  // Nu-DESC 5 Domains
  const [nudescDomains, setNudescDomains] = useState<NudescDomain[]>([
    {
      id: 'disorientation',
      name: '1. Disorientation',
      hindiName: 'समय, स्थान या व्यक्ति की पहचान भूलना',
      score: 2,
      descriptions: [
        '0: Oriented to time, place, and person',
        '1: Intermittent confusion or mild mistakes in time/place',
        '2: Frank disorientation to place or person / unaware of hospital setting',
      ],
    },
    {
      id: 'behavior',
      name: '2. Inappropriate Behavior',
      hindiName: 'अनुचित या उत्तेजित व्यवहार',
      score: 2,
      descriptions: [
        '0: Calm and cooperative behavior',
        '1: Restless, fidgeting with blankets or bedsheets',
        '2: Aggressive, climbing out of bed, pulling IV lines / urinary catheter',
      ],
    },
    {
      id: 'communication',
      name: '3. Inappropriate Communication',
      hindiName: 'असंगत या असंबद्ध बातचीत',
      score: 1,
      descriptions: [
        '0: Coherent, clear communication',
        '1: Tangential speech, slow response, or rambling sentences',
        '2: Incoherent, shouting, muttering incomprehensibly',
      ],
    },
    {
      id: 'hallucinations',
      name: '4. Illusions / Hallucinations',
      hindiName: 'मतिभ्रम (आवाजें सुनना या न दिखने वाली चीजें देखना)',
      score: 1,
      descriptions: [
        '0: No perceptual disturbances reported or observed',
        '1: Misinterpreting shadows/sounds (illusions)',
        '2: Frank visual/auditory hallucinations (seeing deceased relatives, bugs)',
      ],
    },
    {
      id: 'psychomotor',
      name: '5. Psychomotor Retardation / Agitation',
      hindiName: 'शारीरिक सुस्ती या अत्यधिक बेचैनी',
      score: 2,
      descriptions: [
        '0: Normal motor activity and posture',
        '1: Mild lethargy or mild motor restlessness',
        '2: Severe lethargy/stupor OR severe uncontrolled motor agitation',
      ],
    },
  ]);

  const [deliriumSubtype, setDeliriumSubtype] = useState<DeliriumSubtype>('hyperactive');
  const [isTerminalRestlessness, setIsTerminalRestlessness] = useState<boolean>(true); // Imminent dying phase (<72h)

  // Reversible Causes Checklist ("D.E.L.I.R.I.U.M.")
  const [reversibleCauses, setReversibleCauses] = useState({
    drugsOpioidToxicity: true, // Opioid accumulation / neurotoxicity
    drugsAnticholinergics: true, // Promethazine, hyoscine, tricyclics
    electrolytesHypercalcemia: false,
    electrolytesHyponatremiaUremia: true,
    lackOfOxygen: false,
    infectionSepsisUTI: false,
    retentionUrinaryBladder: true, // Acute urinary retention
    retentionFecalImpaction: true, // Severe constipation
    intracranialMets: false,
    uncontrolledPain: true,
  });

  // Pharmacological Selection
  const [selectedMedication, setSelectedMedication] = useState<'haloperidol' | 'olanzapine' | 'levomepromazine' | 'midazolam'>('haloperidol');

  // Feedback
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchedToast, setDispatchedToast] = useState<boolean>(false);

  // Total Nu-DESC Score
  const totalNudescScore = useMemo(() => {
    return nudescDomains.reduce((acc, domain) => acc + domain.score, 0);
  }, [nudescDomains]);

  const deliriumStatus = useMemo(() => {
    if (totalNudescScore >= 2) {
      return {
        confirmed: true,
        label: 'ACTIVE DELIRIUM CONFIRMED (Nu-DESC ≥ 2)',
        color: '#be123c',
        bg: '#ffe4e6',
        borderColor: '#f43f5e',
        badge: 'ACUTE NEUROCOGNITIVE DISTRESS',
        summary: `Score ${totalNudescScore}/10 indicates acute neurocognitive breakdown (${deliriumSubtype.toUpperCase()} subtype). Immediate investigation of reversible triggers and targeted non-sedating or comforting antipsychotic titration is warranted.`,
      };
    }
    return {
      confirmed: false,
      label: 'NO DELIRIUM DETECTED (Nu-DESC < 2)',
      color: '#047857',
      bg: '#d1fae5',
      borderColor: '#34d399',
      badge: 'STABLE SENSORIUM',
      summary: `Score ${totalNudescScore}/10 is below the clinical threshold. Continue preventive delirium environmental precautions.`,
    };
  }, [totalNudescScore, deliriumSubtype]);

  const handleDomainScoreChange = (id: string, newScore: number) => {
    setNudescDomains((prev) =>
      prev.map((d) => (d.id === id ? { ...d, score: newScore } : d))
    );
  };

  const handleCopyPlan = () => {
    const text = `PALLIATIVE DELIRIUM, TERMINAL RESTLESSNESS & AGITATION PROTOCOL
Patient: ${patientName} (${patientId}) · ${primaryCancer}
Nu-DESC Total Score: ${totalNudescScore} / 10 (${deliriumStatus.label})
Subtype: ${deliriumSubtype.toUpperCase()} | Terminal Phase Restlessness: ${isTerminalRestlessness ? 'YES (<72h of life)' : 'NO'}
--------------------------------------------------
ACTIVE REVERSIBLE CAUSES IDENTIFIED:
${reversibleCauses.retentionUrinaryBladder ? '• Urinary Retention: Bladder scan / catheter check required immediately.\n' : ''}${reversibleCauses.retentionFecalImpaction ? '• Fecal Impaction: Digital rectal examination & gentle enema indicated.\n' : ''}${reversibleCauses.drugsOpioidToxicity ? '• Opioid Neurotoxicity / Accumulation: Consider 30-50% opioid dose reduction or rotation.\n' : ''}${reversibleCauses.drugsAnticholinergics ? '• Anticholinergic Burden: Deprescribe sedating antihistamines & tricyclics.\n' : ''}${reversibleCauses.uncontrolledPain ? '• Uncontrolled Visceral/Neuropathic Pain: Review analgesia schedule.\n' : ''}--------------------------------------------------
NON-PHARMACOLOGICAL CARE BUNDLE:
1. Reorientation: Day/night circadian light control; familiar family members present at bedside.
2. Avoid Physical Restraints: Physical restraints exacerbate paranoia and agitation; pad bedrails and lower bed to floor.
3. Quiet Sensory Environment: Minimize alarms, avoid loud shift changes, speak in calm re-assuring tones.
--------------------------------------------------
PHARMACOLOGICAL TREATMENT:
Primary Drug: ${selectedMedication === 'haloperidol' ? 'Haloperidol 0.5–1.5 mg PO/SC q4-6h PRN (First-line for hallucinations/paranoia)' : selectedMedication === 'olanzapine' ? 'Olanzapine 2.5–5 mg PO/SL nocte (Atypical neuroleptic with antiemetic synergy)' : selectedMedication === 'levomepromazine' ? 'Levomepromazine 12.5–25 mg SC (Sedating neuroleptic for refractory terminal agitation)' : 'Midazolam 2.5–5 mg SC (End-stage refractory crisis rescue only)'}
Crucial Safety Warning: Avoid Benzodiazepine monotherapy in non-terminal delirium (causes paradoxical disinhibition, worsening confusion, and falls).`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchPlan = () => {
    setDispatchedToast(true);
    if (onDispatchPlan) {
      onDispatchPlan(
        `Delirium Care Protocol Dispatched: Nu-DESC ${totalNudescScore}/10 (${deliriumSubtype.toUpperCase()}), Primary Rx: ${selectedMedication.toUpperCase()}`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delirium-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-slate-900 via-amber-950 to-slate-900 text-white flex flex-wrap items-center justify-between gap-3 border-b border-amber-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-500/20 border border-amber-400/40 flex items-center justify-center text-amber-300">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="delirium-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Palliative Delirium & Terminal Agitation Suite
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-amber-500/20 text-amber-200 border border-amber-400/30">
                  Nu-DESC & EAPC Guidelines
                </span>
              </div>
              <p className="text-xs text-amber-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: deliriumStatus.bg,
                color: deliriumStatus.color,
                borderColor: deliriumStatus.borderColor,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>Nu-DESC: {totalNudescScore} / 10 ({deliriumStatus.confirmed ? 'Delirium' : 'Clear'})</span>
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
            onClick={() => setActiveTab('nudesc_screen')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'nudesc_screen'
                ? 'border-amber-600 text-amber-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🧠 Nu-DESC Delirium Assessment</span>
          </button>
          <button
            onClick={() => setActiveTab('reversible_causes')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'reversible_causes'
                ? 'border-amber-600 text-amber-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🔍 D.E.L.I.R.I.U.M. Reversible Causes</span>
          </button>
          <button
            onClick={() => setActiveTab('management')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'management'
                ? 'border-amber-600 text-amber-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>💊 Evidence-Based Neuroleptic Protocol</span>
          </button>
          <button
            onClick={() => setActiveTab('counseling')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'counseling'
                ? 'border-amber-600 text-amber-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🇮🇳 Bilingual Family Counseling (Hindi/English)</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-amber-600 text-amber-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Longitudinal Trajectory Log</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: NU-DESC SCREEN */}
          {activeTab === 'nudesc_screen' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: 5 Nu-DESC Domains */}
              <div className="lg:col-span-7 space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                    Nursing Delirium Screening Scale (Nu-DESC)
                  </span>
                  <span className="text-xs font-extrabold text-amber-800">Score &ge; 2 confirms Delirium</span>
                </div>

                <div className="space-y-3">
                  {nudescDomains.map((domain) => (
                    <div key={domain.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200 space-y-1.5 text-xs">
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="font-bold text-slate-900">{domain.name}</span>
                          <span className="text-[10px] text-slate-500 block">{domain.hindiName}</span>
                        </div>
                        <span className="font-black text-amber-800 bg-white px-2 py-0.5 border rounded">
                          {domain.score} / 2 pts
                        </span>
                      </div>

                      <div className="grid grid-cols-3 gap-1.5 pt-1">
                        {[0, 1, 2].map((pts) => (
                          <button
                            key={pts}
                            type="button"
                            onClick={() => handleDomainScoreChange(domain.id, pts)}
                            className={`p-2 rounded-lg text-left border transition ${
                              domain.score === pts
                                ? 'bg-amber-100/80 border-amber-500 text-amber-950 font-bold ring-2 ring-amber-200'
                                : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                            }`}
                          >
                            <div className="text-[11px] font-bold">{pts} Points</div>
                            <div className="text-[10px] text-slate-500 line-clamp-2 mt-0.5">
                              {domain.descriptions[pts]}
                            </div>
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>

                {/* Subtype & Terminal Phase Picker */}
                <div className="p-3.5 bg-white rounded-xl border border-slate-200 space-y-2 text-xs">
                  <span className="font-bold text-slate-800 block">Clinical Subtype & Prognostic Trajectory</span>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { id: 'hyperactive', label: 'Hyperactive', desc: 'Agitated, restless, pulling lines' },
                      { id: 'hypoactive', label: 'Hypoactive', desc: 'Quiet, lethargic (commonly missed!)' },
                      { id: 'mixed', label: 'Mixed', desc: 'Fluctuates between lethargy & agitation' },
                    ].map((st) => (
                      <button
                        key={st.id}
                        type="button"
                        onClick={() => setDeliriumSubtype(st.id as DeliriumSubtype)}
                        className={`p-2 rounded-lg text-left border transition ${
                          deliriumSubtype === st.id
                            ? 'bg-amber-50 border-amber-500 text-amber-950 font-bold'
                            : 'text-slate-600'
                        }`}
                      >
                        <div className="font-bold">{st.label}</div>
                        <div className="text-[10px] text-slate-500">{st.desc}</div>
                      </button>
                    ))}
                  </div>

                  <div className="pt-2 border-t border-slate-100 flex items-center justify-between">
                    <label className="flex items-center gap-2 cursor-pointer font-medium text-slate-700">
                      <input
                        type="checkbox"
                        checked={isTerminalRestlessness}
                        onChange={(e) => setIsTerminalRestlessness(e.target.checked)}
                        className="w-4 h-4 text-amber-600 rounded"
                      />
                      <span>Terminal Phase Restlessness (&lt;72 hours of expected survival)</span>
                    </label>
                  </div>
                </div>
              </div>

              {/* Right Column: Delirium Staging Output & Sundowning Visualizer */}
              <div className="lg:col-span-5 space-y-4">
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition space-y-2"
                  style={{
                    backgroundColor: deliriumStatus.bg,
                    borderColor: deliriumStatus.borderColor,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-700">Diagnostic Verdict</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold text-white" style={{ backgroundColor: deliriumStatus.color }}>
                      {deliriumStatus.badge}
                    </span>
                  </div>

                  <div className="text-base font-black" style={{ color: deliriumStatus.color }}>
                    {deliriumStatus.label}
                  </div>

                  <p className="text-xs text-slate-800 leading-relaxed font-medium">
                    {deliriumStatus.summary}
                  </p>
                </div>

                {/* 24-Hour Sundowning SVG Visualizer */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner flex flex-col items-center">
                  <div className="w-full flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span className="font-semibold text-slate-200">24-Hour Diurnal Fluctuations</span>
                    <span className="text-amber-400 font-bold">Sundowning Peak</span>
                  </div>

                  <div className="w-full max-w-[280px] aspect-[16/9] flex items-center justify-center py-2">
                    <svg viewBox="0 0 200 100" className="w-full h-full drop-shadow-md">
                      {/* Grid lines */}
                      <line x1="20" y1="20" x2="190" y2="20" stroke="#334155" strokeWidth="1" strokeDasharray="2,2" />
                      <line x1="20" y1="50" x2="190" y2="50" stroke="#334155" strokeWidth="1" strokeDasharray="2,2" />
                      <line x1="20" y1="80" x2="190" y2="80" stroke="#334155" strokeWidth="1" />

                      {/* Day / Night zones */}
                      <rect x="20" y="10" width="70" height="70" fill="#0f172a" opacity="0.4" />
                      <rect x="90" y="10" width="60" height="70" fill="#f59e0b" opacity="0.1" />
                      <rect x="150" y="10" width="40" height="70" fill="#0f172a" opacity="0.4" />

                      {/* Sundowning Curve */}
                      <path
                        d="M 20 70 Q 50 65 90 60 Q 130 55 150 25 Q 170 15 190 35"
                        fill="none"
                        stroke="#f59e0b"
                        strokeWidth="2.5"
                      />

                      {/* Area fill */}
                      <path
                        d="M 20 70 Q 50 65 90 60 Q 130 55 150 25 Q 170 15 190 35 L 190 80 L 20 80 Z"
                        fill="#f59e0b"
                        opacity="0.15"
                      />

                      {/* Peak Marker */}
                      <circle cx="160" cy="20" r="4" fill="#ef4444" />
                      <text x="160" y="14" fill="#fca5a5" fontSize="7" fontWeight="bold" textAnchor="middle">Peak (20:00 - 02:00)</text>

                      {/* Time labels */}
                      <text x="25" y="92" fill="#94a3b8" fontSize="7">06:00</text>
                      <text x="90" y="92" fill="#94a3b8" fontSize="7">12:00 (Noon)</text>
                      <text x="150" y="92" fill="#94a3b8" fontSize="7">18:00 (Dusk)</text>
                      <text x="185" y="92" fill="#94a3b8" fontSize="7">Midnight</text>
                    </svg>
                  </div>

                  <div className="text-[11px] text-slate-400 text-center mt-1">
                    Delirium characteristically worsens in evening hours (&quot;Sundowning&quot;) as sensory visual cues diminish.
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: REVERSIBLE CAUSES CHECKLIST */}
          {activeTab === 'reversible_causes' && (
            <div className="space-y-4">
              <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200">
                <h3 className="text-xs font-bold text-amber-950 uppercase tracking-wider mb-0.5">
                  D.E.L.I.R.I.U.M. Systematic Reversible Causes Framework
                </h3>
                <p className="text-xs text-amber-900 leading-relaxed font-medium">
                  Up to 50% of cancer delirium episodes are fully or partially reversible if physical triggers are recognized and treated. Full bladder and severe fecal impaction are the most commonly missed silent causes of intense terminal agitation!
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                {/* Retention */}
                <div className="p-3.5 bg-white rounded-xl border-2 border-red-200 hover:border-red-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-red-900">R: Physical Retention (#1 Overlooked!)</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-800">URGENT BEDSIDE CHECK</span>
                  </div>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700 font-medium">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.retentionUrinaryBladder}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, retentionUrinaryBladder: e.target.checked })}
                      className="w-4 h-4 text-red-600 rounded"
                    />
                    <span>Acute Urinary Retention (Palpable suprapubic globe / Catheter blocked)</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700 font-medium">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.retentionFecalImpaction}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, retentionFecalImpaction: e.target.checked })}
                      className="w-4 h-4 text-red-600 rounded"
                    />
                    <span>Fecal Impaction (No bowel movement &gt;3 days / Hard rectal mass on DRE)</span>
                  </label>
                </div>

                {/* Drugs */}
                <div className="p-3.5 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="font-bold text-slate-900 block">D: Medication Toxicity</span>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.drugsOpioidToxicity}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, drugsOpioidToxicity: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Opioid Neurotoxicity (Accumulation of M3G/M6G metabolites in renal failure)</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.drugsAnticholinergics}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, drugsAnticholinergics: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Anticholinergic Toxicity (Promethazine, Tricyclics, Hyoscine)</span>
                  </label>
                </div>

                {/* Electrolytes */}
                <div className="p-3.5 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="font-bold text-slate-900 block">E: Electrolyte Imbalance</span>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.electrolytesHypercalcemia}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, electrolytesHypercalcemia: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Hypercalcemia of Malignancy (Corrected Ca &gt; 10.5 mg/dL)</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.electrolytesHyponatremiaUremia}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, electrolytesHyponatremiaUremia: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Hyponatremia / Uremia / Hepatic Encephalopathy</span>
                  </label>
                </div>

                {/* Pain & Intracranial */}
                <div className="p-3.5 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="font-bold text-slate-900 block">U & I: Pain & Intracranial Disease</span>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.uncontrolledPain}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, uncontrolledPain: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Uncontrolled Visceral or Neuropathic Pain (Agitation as a pain cry)</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer text-slate-700">
                    <input
                      type="checkbox"
                      checked={reversibleCauses.intracranialMets}
                      onChange={(e) => setReversibleCauses({ ...reversibleCauses, intracranialMets: e.target.checked })}
                      className="w-4 h-4 text-amber-600 rounded"
                    />
                    <span>Brain Metastases / Peritumoral Cerebral Edema</span>
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: MANAGEMENT */}
          {activeTab === 'management' && (
            <div className="space-y-5">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                {/* Haloperidol */}
                <div
                  onClick={() => setSelectedMedication('haloperidol')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer space-y-2 ${
                    selectedMedication === 'haloperidol'
                      ? 'bg-amber-50/70 border-amber-600 shadow-sm ring-2 ring-amber-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">FIRST-LINE ANTIPSYCHOTIC</span>
                    <span className="font-bold text-slate-500">Haloperidol</span>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Haloperidol 0.5 mg to 1.5 mg PO / SC q4-6h PRN
                  </div>
                  <p className="text-slate-600 leading-relaxed font-medium">
                    Gold standard for distressing hallucinations, illusions, and paranoid psychosis. Strong D2 receptor antagonism with minimal anticholinergic side effects and no respiratory depression.
                  </p>
                  <div className="text-[10px] text-slate-500 bg-slate-50 p-2 rounded">
                    Max 24h dose: 5 mg SC in palliative settings. Avoid in Parkinson&apos;s / Lewy Body Dementia.
                  </div>
                </div>

                {/* Olanzapine */}
                <div
                  onClick={() => setSelectedMedication('olanzapine')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer space-y-2 ${
                    selectedMedication === 'olanzapine'
                      ? 'bg-amber-50/70 border-amber-600 shadow-sm ring-2 ring-amber-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800">SECOND-LINE ATYPICAL</span>
                    <span className="font-bold text-slate-500">Olanzapine</span>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Olanzapine 2.5 mg to 5.0 mg PO / Sublingual (Orodispersible) nocte
                  </div>
                  <p className="text-slate-600 leading-relaxed font-medium">
                    Excellent for patients with concomitant nausea/anorexia or mild insomnia. Dissolves on tongue without swallowing effort. Lower extrapyramidal symptom (EPS) risk.
                  </p>
                </div>

                {/* Levomepromazine */}
                <div
                  onClick={() => setSelectedMedication('levomepromazine')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer space-y-2 ${
                    selectedMedication === 'levomepromazine'
                      ? 'bg-amber-50/70 border-amber-600 shadow-sm ring-2 ring-amber-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-800">REFRACTORY AGITATION</span>
                    <span className="font-bold text-slate-500">Levomepromazine</span>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Levomepromazine 12.5 mg to 25 mg SC bolus (or 25–50 mg / 24h SC driver)
                  </div>
                  <p className="text-slate-600 leading-relaxed font-medium">
                    Broad-spectrum dirty receptor antagonist (D2, 5-HT2, H1, α1). Provides deep calming sedation for severe agitated delirium and terminal restlessness unresponsive to haloperidol.
                  </p>
                </div>

                {/* Midazolam & Benzodiazepine Warning */}
                <div
                  onClick={() => setSelectedMedication('midazolam')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer space-y-2 ${
                    selectedMedication === 'midazolam'
                      ? 'bg-amber-50/70 border-amber-600 shadow-sm ring-2 ring-amber-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-800">TERMINAL CRISIS RESCUE ONLY</span>
                    <span className="font-bold text-slate-500">Midazolam</span>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Midazolam 2.5 mg to 5.0 mg SC PRN (or continuous syringe driver)
                  </div>
                  <p className="text-slate-600 leading-relaxed font-medium">
                    Indicated ONLY in active end-of-life terminal restlessness (last 48h) or intractable muscle spasm.
                  </p>
                  <div className="text-[10px] text-red-800 bg-red-50 p-2 rounded border border-red-200">
                    ⚠️ <strong>Crucial Rule:</strong> Never use benzodiazepines alone in non-terminal delirium. They cause paradoxical agitation and increase falls!
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: BILINGUAL COUNSELING */}
          {activeTab === 'counseling' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                <div className="flex items-center gap-2 mb-1">
                  <IconUsers className="w-5 h-5 text-blue-700" />
                  <h3 className="text-sm font-bold text-blue-950">
                    Bilingual Family Counseling: Reassurance in Delirium & Confusion (Hindi & English)
                  </h3>
                </div>
                <p className="text-xs text-blue-800">
                  Families are often horrified when patients hallucinate or fail to recognize them, fearing permanent psychiatric madness. Use these compassionate scripts to provide comfort.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                {/* Hindi Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-800">🇮🇳 परिवार के लिए सांत्वना व मार्गदर्शन (Hindi)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">हिंदी</span>
                  </div>

                  <div className="space-y-2.5 text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">१. यह पागलपन नहीं है:</div>
                      <p className="text-[11px] text-emerald-800">
                        बीमारी और दवाओं के कारण मस्तिष्क में कमजोरी आ गई है। मरीज जानबूझकर ऐसा नहीं कर रहे हैं, न ही वे पागल हुए हैं।
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">२. बहस न करें, शांत स्वर में बात करें:</div>
                      <p className="text-[11px] text-rose-800">
                        यदि वे कुछ ऐसा देखते हैं जो आपको नहीं दिख रहा, तो उनसे बहस न करें। कहें &quot;मुझे वह नहीं दिख रहा, लेकिन मैं आपके पास हूँ और आप सुरक्षित हैं।&quot;
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">३. कमरा शांत और परिचित रखें:</div>
                      <p className="text-[11px] text-amber-800">
                        दिन में हल्की धूप आने दें और रात में धीमी रोशनी रखें। परिवार का कोई एक जाना-पहचाना व्यक्ति पास बैठकर हाथ थामे।
                      </p>
                    </div>
                  </div>
                </div>

                {/* English Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-800">🇬🇧 Compassionate Clinical Script (English)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">English</span>
                  </div>

                  <div className="space-y-2.5 text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">1. Delirium is Brain Failure, Not Madness:</div>
                      <p className="text-[11px] text-emerald-800">
                        Just as the kidneys or liver struggle in advanced cancer, the brain is struggling to process sensory information. It is a temporary clouding of consciousness.
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">2. Validate Feelings, Do Not Argue:</div>
                      <p className="text-[11px] text-rose-800">
                        Never confront or argue with hallucinations. Reassure the patient with: &quot;I know this feels frightening, but I am right here with you and you are safe.&quot;
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">3. Grounding & Familiar Cues:</div>
                      <p className="text-[11px] text-amber-800">
                        Keep familiar objects (a family photo, favorite blanket), gently remind them of the time and place, and avoid excessive visitors or loud television.
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: HISTORY */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Nu-DESC Delirium Trajectory Log</h3>
                <span className="text-xs text-slate-500">Kidwai Palliative Ward</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date / Time</th>
                      <th className="p-2.5">Nu-DESC</th>
                      <th className="p-2.5">Subtype</th>
                      <th className="p-2.5">Reversible Cause Identified</th>
                      <th className="p-2.5">Treatment Given</th>
                      <th className="p-2.5">Clinician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-amber-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-extrabold text-amber-800">{totalNudescScore} / 10</td>
                      <td className="p-2.5 uppercase font-bold">{deliriumSubtype}</td>
                      <td className="p-2.5 text-red-700 font-semibold">Urinary Retention + Opioid accumulation</td>
                      <td className="p-2.5 font-medium">{selectedMedication.toUpperCase()} 1.0 mg SC</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">05 Sep 2026</td>
                      <td className="p-2.5 font-bold">4 / 10</td>
                      <td className="p-2.5 uppercase">Hyperactive</td>
                      <td className="p-2.5">Severe constipation (Fecal impaction)</td>
                      <td className="p-2.5">Enema + Haloperidol 0.5mg PO</td>
                      <td className="p-2.5 text-slate-600">Pushpa Gowda (Nurse)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">22 Aug 2026</td>
                      <td className="p-2.5 font-bold text-emerald-700">1 / 10</td>
                      <td className="p-2.5 uppercase text-slate-500">None</td>
                      <td className="p-2.5 text-slate-400">Mild night restlessness</td>
                      <td className="p-2.5">Environmental reorientation</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
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
              {copiedToast ? '✓ Copied Delirium Protocol!' : '📋 Copy Complete Delirium Protocol'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-amber-700 hover:bg-amber-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Delirium Orders!' : '🧠 Dispatch STAT Delirium Orders & ASHA Visit'}</span>
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
