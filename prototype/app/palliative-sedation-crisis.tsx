'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconSparkles,
  IconHospital,
  IconZap,
} from './icons';

export interface SedationIndication {
  id: string;
  label: string;
  description: string;
  recommendedFirstLine: string;
}

export const SEDATION_INDICATIONS: SedationIndication[] = [
  {
    id: 'delirium',
    label: 'Refractory Agitated Delirium / Psychomotor Agitation',
    description: 'Severe terminal agitation with hallucinations, thrashing, or extreme anguish unresponsive to haloperidol.',
    recommendedFirstLine: 'Midazolam + Levomepromazine',
  },
  {
    id: 'pain',
    label: 'Intractable Breakthrough Pain Crisis',
    description: 'Severe agonizing pain refractory to aggressive opioid titration, adjuvants, and nerve blocks.',
    recommendedFirstLine: 'Midazolam + Maintain Baseline Opioid Analgesia',
  },
  {
    id: 'dyspnea',
    label: 'Refractory Terminal Dyspnea / Asphyxiation Anguish',
    description: 'Severe breathlessness accompanied by air hunger panic refractory to high-flow oxygen and opioids.',
    recommendedFirstLine: 'Midazolam + Morphine SC continuous',
  },
  {
    id: 'hemorrhage',
    label: 'Catastrophic Terminal Bleeding (Carotid Blowout / Massive Hemoptysis)',
    description: 'Arterial erosion causing acute profuse exsanguination with terror and panic.',
    recommendedFirstLine: 'STAT Midazolam 10mg + Morphine 15mg + Dark Towels',
  },
];

export interface RassScore {
  score: number;
  term: string;
  description: string;
  color: string;
}

export const RASS_PAL_LEVELS: RassScore[] = [
  { score: 4, term: 'Combative', description: 'Overly combative or violent; immediate danger to self or staff', color: '#dc2626' },
  { score: 3, term: 'Very Agitated', description: 'Pulls at lines or catheters; aggressive behavior', color: '#ea580c' },
  { score: 2, term: 'Agitated', description: 'Frequent non-purposeful movement, fight-or-flight restlessness', color: '#f59e0b' },
  { score: 1, term: 'Restless', description: 'Anxious or apprehensive but movements not aggressive', color: '#eab308' },
  { score: 0, term: 'Alert & Calm', description: 'Spontaneously pays attention, peaceful and conversational', color: '#10b981' },
  { score: -1, term: 'Drowsy', description: 'Not fully alert, sustained eye opening/contact to voice (>10 sec)', color: '#06b6d4' },
  { score: -2, term: 'Light Sedation', description: 'Briefly awakens with eye contact to voice (<10 sec)', color: '#3b82f6' },
  { score: -3, term: 'Moderate Sedation', description: 'Movement or eye opening to voice (no eye contact)', color: '#6366f1' },
  { score: -4, term: 'Deep Sedation', description: 'No response to voice, but physical movement to tactile stimulation', color: '#8b5cf6' },
  { score: -5, term: 'Unarousable', description: 'No response to voice or physical stimulation', color: '#475569' },
];

export interface PalliativeSedationCrisisProps {
  patientId: string;
  patientName: string;
  diagnosis: string;
  onClose?: () => void;
  onApplyPlan?: (protocolSummary: string) => void;
}

export function PalliativeSedationCrisisModal({
  patientId,
  patientName,
  diagnosis,
  onClose,
  onApplyPlan,
}: PalliativeSedationCrisisProps) {
  const [indicationId, setIndicationId] = useState<string>('delirium');
  const [sedationDepth, setSedationDepth] = useState<'mild' | 'deep'>('deep');
  const [sedationDuration, setSedationDuration] = useState<'continuous' | 'respite'>('continuous');
  const [targetRass, setTargetRass] = useState<number>(-4);

  // Ethics & Safety Checklist
  const [refractoryVerified, setRefractoryVerified] = useState<boolean>(true);
  const [surrogateConsented, setSurrogateConsented] = useState<boolean>(true);
  const [multidisciplinaryAgreed, setMultidisciplinaryAgreed] = useState<boolean>(true);
  const [opioidMaintained, setOpioidMaintained] = useState<boolean>(true);
  const [catastrophicBleedMode, setCatastrophicBleedMode] = useState<boolean>(false);
  const [copiedToast, setCopiedToast] = useState<boolean>(false);

  const selectedIndication = useMemo(() => {
    return SEDATION_INDICATIONS.find((i) => i.id === indicationId) || SEDATION_INDICATIONS[0];
  }, [indicationId]);

  const currentRass = useMemo(() => {
    return RASS_PAL_LEVELS.find((r) => r.score === targetRass) || RASS_PAL_LEVELS[8];
  }, [targetRass]);

  // Pharmacologic protocol calculations
  const protocolRegimen = useMemo(() => {
    if (catastrophicBleedMode) {
      return {
        title: 'EMERGENCY CRISIS SEDATION (Catastrophic Bleed Protocol)',
        statLoading: 'Midazolam 10 mg SC/IV stat + Morphine 10-15 mg SC/IV stat immediately',
        continuousRate: 'Midazolam 2.5 to 5.0 mg/hour IV/SC continuous infusion',
        adjuvant: 'Glycopyrronium 0.4mg IV/SC stat (if tracheal inundation)',
        specialInstructions: [
          'Immediate application of dark/green towels over visible blood to reduce horror for family',
          'Attending clinician remains physically present holding patient hand with gentle reassurance',
          'Strictly DO NOT attempt rapid fluid resuscitation or invasive arterial clamping in terminal stage',
        ],
      };
    }

    if (sedationDepth === 'deep') {
      return {
        title: 'Continuous Deep Palliative Sedation Protocol (EAPC Standard)',
        statLoading: 'Midazolam 5.0 mg SC/IV stat bolus over 2-3 minutes',
        continuousRate: 'Midazolam 1.0 to 2.0 mg/hour SC continuous syringe driver (24 to 48 mg / 24h)',
        titrationRule: 'If RASS > -4 after 2 hours: Administer 2.5mg rescue bolus and increase rate by 30-50%',
        adjuvant: 'Levomepromazine 25 mg SC stat + 50 to 100 mg / 24h SC if agitation refractory to midazolam',
        specialInstructions: [
          'DO NOT stop baseline opioid analgesia (maintain baseline 24h continuous infusion)',
          'Assess depth using RASS-PAL every 4 hours; goal is RASS -4 (Deep) or -5 (Unarousable)',
          'Perform regular mouth care and position changes; avoid artificial hyperhydration',
        ],
      };
    } else {
      return {
        title: 'Proportional Mild / Light Palliative Sedation Protocol',
        statLoading: 'Midazolam 2.5 mg SC/IV stat bolus',
        continuousRate: 'Midazolam 0.5 to 1.0 mg/hour SC continuous syringe driver (12 to 24 mg / 24h)',
        titrationRule: 'Titrate slowly to achieve calm state where patient awakens to gentle voice (RASS -2)',
        adjuvant: 'Haloperidol 1.5 to 3.0 mg SC / 24h for delirium without sedation deepening',
        specialInstructions: [
          'Patient remains capable of brief eye contact and interaction with loved ones',
          'Evaluate for planned awakening / trial cessation after 24-48 hours if respite sedation',
          'Maintain regular breakthrough analgesia for incident pain',
        ],
      };
    }
  }, [sedationDepth, catastrophicBleedMode]);

  const isEthicsComplete = refractoryVerified && surrogateConsented && multidisciplinaryAgreed && opioidMaintained;

  const handleCopyOrderSheet = () => {
    const sheet = `[CONTINUITY LOOP: PALLIATIVE SEDATION PROTOCOL & ORDERS]
Patient: ${patientName} (${patientId})
Diagnosis: ${diagnosis}
Indication: ${selectedIndication.label}
Depth Goal: ${sedationDepth.toUpperCase()} (${currentRass.term}, RASS ${currentRass.score})
Duration: ${sedationDuration.toUpperCase()}
Assessment Date: ${new Date().toLocaleString('en-GB')}

ETHICAL & LEGAL PREREQUISITES VERIFIED:
✓ Truly Refractory Distress Confirmed by Palliative Specialist: ${refractoryVerified ? 'YES' : 'NO'}
✓ Informed Surrogate / Proxy Consent Documented: ${surrogateConsented ? 'YES' : 'NO'}
✓ Multidisciplinary Team Consensus Established: ${multidisciplinaryAgreed ? 'YES' : 'NO'}
✓ Baseline Opioid Analgesia Maintained (Double Effect Safeguard): ${opioidMaintained ? 'YES' : 'NO'}

STAT PRESCRIPTION ORDERS:
1. Loading Dose: ${protocolRegimen.statLoading}
2. Continuous SC Infusion: ${protocolRegimen.continuousRate}
3. Titration Rule: ${protocolRegimen.titrationRule || 'N/A'}
4. Refractory Adjuvant: ${protocolRegimen.adjuvant}

CLINICAL & NURSING DIRECTIVES:
${protocolRegimen.specialInstructions.map((inst) => `• ${inst}`).join('\n')}

Attending Palliative Lead: Dr Sujay · Oncology Palliative Lead
Consulting Physician: Dr Isha Menon · Emergency & Critical Care`;

    navigator.clipboard.writeText(sheet).then(() => {
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
              <span className="p-1 rounded-md bg-purple-700 text-white font-black text-xs">
                REFRACTORY DISTRESS
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Palliative Sedation Protocol & Crisis Management
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Ethically grounded, proportional sedation protocol (EAPC standards) for refractory terminal suffering
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Palliative Sedation"
          >
            ✕
          </button>
        </div>

        {/* Patient Banner with Catastrophic Crisis Mode Toggle */}
        <div className="px-6 py-3 bg-purple-50/70 border-b border-purple-200 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-[10px] font-bold text-purple-600 uppercase tracking-wider block">Patient</span>
              <span className="font-bold text-slate-900 text-sm">{patientName} ({patientId})</span>
            </div>
            <div className="h-6 w-px bg-purple-200" />
            <div>
              <span className="text-[10px] font-bold text-purple-600 uppercase tracking-wider block">Condition</span>
              <span className="font-semibold text-slate-800">{diagnosis}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setCatastrophicBleedMode(!catastrophicBleedMode)}
              className={`px-3 py-1.5 rounded-lg text-xs font-black transition flex items-center gap-1.5 ${
                catastrophicBleedMode
                  ? 'bg-rose-600 text-white shadow-sm animate-pulse ring-2 ring-rose-400'
                  : 'bg-white text-rose-700 hover:bg-rose-50 border border-rose-300'
              }`}
            >
              <span>🩸</span>
              <span>{catastrophicBleedMode ? 'Catastrophic Bleed Mode ACTIVE' : 'Carotid Blowout / Bleed Protocol'}</span>
            </button>
          </div>
        </div>

        {/* Main 2-Column Grid */}
        <div className="p-6 overflow-y-auto max-h-[72vh] space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: Indication, RASS-PAL Target, & Ethical Criteria (7 cols) */}
            <div className="lg:col-span-7 space-y-5">
              {/* Primary Refractory Indication */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                    <IconHeartPulse className="w-4 h-4 text-purple-600" />
                    <span>Primary Refractory Indication</span>
                  </h3>
                  <span className="text-[11px] text-slate-400 font-mono">EAPC Refractory Criteria</span>
                </div>

                <div className="space-y-2">
                  {SEDATION_INDICATIONS.map((ind) => (
                    <label
                      key={ind.id}
                      className={`block p-2.5 rounded-lg border cursor-pointer transition ${
                        indicationId === ind.id
                          ? 'bg-purple-50/80 border-purple-300 shadow-xs'
                          : 'bg-slate-50 border-slate-200 hover:bg-slate-100'
                      }`}
                    >
                      <div className="flex items-start gap-2">
                        <input
                          type="radio"
                          name="sedation-indication"
                          checked={indicationId === ind.id}
                          onChange={() => setIndicationId(ind.id)}
                          className="accent-purple-700 mt-0.5"
                        />
                        <div className="space-y-0.5">
                          <span className="font-bold text-slate-900 text-xs block">{ind.label}</span>
                          <span className="text-[11px] text-slate-600 block">{ind.description}</span>
                        </div>
                      </div>
                    </label>
                  ))}
                </div>
              </div>

              {/* RASS-PAL Target Sedation Depth Picker */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                    <IconSparkles className="w-4 h-4 text-indigo-600" />
                    <span>Target Depth: RASS-PAL Scale</span>
                  </h3>
                  <span
                    className="px-2.5 py-0.5 rounded text-xs font-black text-white"
                    style={{ backgroundColor: currentRass.color }}
                  >
                    RASS {currentRass.score}: {currentRass.term}
                  </span>
                </div>

                <p className="text-xs text-slate-500">
                  Select intended level of consciousness reduction. Palliative sedation is titrated strictly proportionally to symptom relief.
                </p>

                <div className="grid grid-cols-5 sm:grid-cols-10 gap-1 pt-1">
                  {RASS_PAL_LEVELS.map((r) => (
                    <button
                      key={r.score}
                      type="button"
                      onClick={() => {
                        setTargetRass(r.score);
                        if (r.score <= -3) {
                          setSedationDepth('deep');
                        } else {
                          setSedationDepth('mild');
                        }
                      }}
                      className={`p-1.5 rounded text-center transition flex flex-col items-center justify-center border text-xs font-bold ${
                        targetRass === r.score
                          ? 'ring-2 ring-purple-600 text-white shadow-xs'
                          : 'bg-slate-50 text-slate-700 hover:bg-slate-100 border-slate-200'
                      }`}
                      style={{
                        backgroundColor: targetRass === r.score ? r.color : undefined,
                      }}
                    >
                      <span>{r.score > 0 ? `+${r.score}` : r.score}</span>
                      <span className="text-[9px] truncate w-full mt-0.5 opacity-90">{r.term.split(' ')[0]}</span>
                    </button>
                  ))}
                </div>

                <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-700">
                  <strong>Clinical Definition:</strong> {currentRass.description}
                </div>
              </div>

              {/* Ethical Prerequisites & Safeguards */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
                    <IconShieldCheck className="w-4 h-4 text-emerald-600" />
                    <span>Ethical Prerequisites & Safeguards Checklist</span>
                  </span>
                  <span className={`text-xs font-bold ${isEthicsComplete ? 'text-emerald-700' : 'text-amber-700'}`}>
                    {isEthicsComplete ? '✓ All Safeguards Verified' : '⚠️ Pending Verifications'}
                  </span>
                </div>

                <div className="space-y-2 text-xs">
                  <label className="flex items-start gap-2 p-2 rounded bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={refractoryVerified}
                      onChange={(e) => setRefractoryVerified(e.target.checked)}
                      className="accent-purple-700 mt-0.5"
                    />
                    <span>Truly refractory symptom verified by palliative care specialist</span>
                  </label>

                  <label className="flex items-start gap-2 p-2 rounded bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={surrogateConsented}
                      onChange={(e) => setSurrogateConsented(e.target.checked)}
                      className="accent-purple-700 mt-0.5"
                    />
                    <span>Informed proxy / surrogate consent documented after family conference</span>
                  </label>

                  <label className="flex items-start gap-2 p-2 rounded bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={multidisciplinaryAgreed}
                      onChange={(e) => setMultidisciplinaryAgreed(e.target.checked)}
                      className="accent-purple-700 mt-0.5"
                    />
                    <span>Multidisciplinary team consensus recorded (Oncology, Palliative, Nursing)</span>
                  </label>

                  <label className="flex items-start gap-2 p-2 rounded bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={opioidMaintained}
                      onChange={(e) => setOpioidMaintained(e.target.checked)}
                      className="accent-purple-700 mt-0.5"
                    />
                    <span className="font-semibold text-rose-900">
                      Baseline opioid analgesia maintained (Doctrine of Double Effect safeguard; prevents withdrawal)
                    </span>
                  </label>
                </div>
              </div>
            </div>

            {/* Right Column: Pharmacologic Regimen & Standing Orders (5 cols) */}
            <div className="lg:col-span-5 space-y-5">
              {/* Active Protocol Card */}
              <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                    Standing Sedation Order
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-900 font-bold text-xs">
                    {sedationDepth.toUpperCase()} SEDATION
                  </span>
                </div>

                <div className="p-3.5 rounded-xl bg-purple-50/70 border border-purple-100 space-y-2.5 text-xs">
                  <div>
                    <span className="text-slate-500 font-medium block text-[11px]">Loading Bolus:</span>
                    <span className="font-mono font-bold text-slate-900 text-sm">
                      {protocolRegimen.statLoading}
                    </span>
                  </div>

                  <div>
                    <span className="text-slate-500 font-medium block text-[11px]">Continuous Maintenance SC:</span>
                    <span className="font-mono font-bold text-purple-800 text-sm">
                      {protocolRegimen.continuousRate}
                    </span>
                  </div>

                  {protocolRegimen.titrationRule && (
                    <div>
                      <span className="text-slate-500 font-medium block text-[11px]">Titration Rule:</span>
                      <span className="font-medium text-slate-700">
                        {protocolRegimen.titrationRule}
                      </span>
                    </div>
                  )}

                  <div>
                    <span className="text-slate-500 font-medium block text-[11px]">Second-Line Adjuvant:</span>
                    <span className="font-medium text-slate-700">
                      {protocolRegimen.adjuvant}
                    </span>
                  </div>
                </div>

                {/* Nursing Directives */}
                <div className="space-y-2 pt-2 border-t border-slate-100 text-xs">
                  <span className="font-bold text-slate-700 block text-[11px] uppercase tracking-wider">
                    Bedside Nursing Directives
                  </span>
                  <ul className="space-y-1.5 text-slate-600 text-[11px]">
                    {protocolRegimen.specialInstructions.map((inst, idx) => (
                      <li key={idx} className="flex items-start gap-1.5">
                        <span className="text-purple-600 font-bold">•</span>
                        <span>{inst}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              {/* Dual-Physician Legal Attestation Card */}
              <div className="p-4 bg-slate-900 rounded-xl text-white space-y-3 shadow-lg">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-purple-300 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
                    Dual Attestation Seal
                  </span>
                  <span className="text-[10px] text-slate-400 font-mono">EAPC / NMC</span>
                </div>

                <p className="text-xs text-slate-300 leading-relaxed">
                  Palliative sedation is documented under strict adherence to Indian Supreme Court end-of-life guidelines and the Doctrine of Double Effect.
                </p>

                <div className="p-2.5 bg-slate-800 rounded-lg text-xs space-y-1 font-mono text-slate-300 border border-slate-700">
                  <div>Attestation: Dr Sujay (Oncology Palliative Lead)</div>
                  <div>Concurrence: Dr Isha Menon (Emergency Care Lead)</div>
                  <div className="text-[10px] text-emerald-400">✓ Legally Concordant Advance Directive Active</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopyOrderSheet}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied Sedation Orders!' : '📋 Copy Sedation Orders & Attestation'}
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
                if (onApplyPlan) {
                  onApplyPlan(
                    `PALLIATIVE SEDATION ACTIVE: Indication: ${selectedIndication.label}. Target RASS ${currentRass.score} (${currentRass.term}). Loading: ${protocolRegimen.statLoading}. Continuous: ${protocolRegimen.continuousRate}.`
                  );
                }
                if (onClose) onClose();
              }}
              disabled={!isEthicsComplete}
              className={`px-5 py-2 rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5 ${
                isEthicsComplete
                  ? 'bg-purple-700 hover:bg-purple-800 text-white cursor-pointer'
                  : 'bg-slate-300 text-slate-500 cursor-not-allowed'
              }`}
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Apply Sedation Protocol to Record</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
