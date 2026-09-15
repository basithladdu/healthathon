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

export type BilskyGrade = 'grade_0' | 'grade_1a' | 'grade_1b' | 'grade_1c' | 'grade_2' | 'grade_3';
export type AmbulatoryStatus = 'independent' | 'with_aid' | 'non_ambulatory_recent' | 'paraplegic_prolonged';

export interface NeurologicDeficit {
  id: string;
  name: string;
  hindiName: string;
  isRedFlag: boolean;
  checked: boolean;
  description: string;
}

interface SpinalCordCompressionModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function SpinalCordCompressionModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: SpinalCordCompressionModalProps) {
  const [activeTab, setActiveTab] = useState<'triage_exam' | 'bilsky_scale' | 'dexamethasone' | 'surgical_decision' | 'counseling'>('triage_exam');

  // Clinical Presentation
  const [ambulatoryStatus, setAmbulatoryStatus] = useState<AmbulatoryStatus>('with_aid');
  const [paraplegiaDurationHours, setParaplegiaDurationHours] = useState<number>(12); // hours
  const [bilskyGrade, setBilskyGrade] = useState<BilskyGrade>('grade_2');
  const [sensoryLevel, setSensoryLevel] = useState<string>('T10 (Umbilicus)');
  const [motorPowerMrc, setMotorPowerMrc] = useState<number>(3); // 0 to 5

  // Red Flag Symptoms Checklist
  const [redFlags, setRedFlags] = useState<{ [key: string]: boolean }>({
    radicular_band_pain: true,
    nocturnal_waking_pain: true,
    urinary_retention: true,
    saddle_anesthesia: false,
    progressive_limb_weakness: true,
  });

  // Tumor Radiosensitivity
  const [tumorHistology, setTumorHistology] = useState<'radiosensitive' | 'intermediate' | 'radioresistant'>('intermediate');

  // Feedback Toasts
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // Bilsky Metadata
  const bilskyMetadata: { [key in BilskyGrade]: { label: string; desc: string; surgicalNeed: string; cordCompressed: boolean } } = {
    grade_0: {
      label: 'Grade 0: Bone Only',
      desc: 'Bone metastasis without epidural extension into the spinal canal.',
      surgicalNeed: 'No cord decompression needed. Palliative RT or SBRT appropriate.',
      cordCompressed: false,
    },
    grade_1a: {
      label: 'Grade 1a: Epidural Impingement',
      desc: 'Epidural extension present, but no deformation of the thecal sac.',
      surgicalNeed: 'Medical management / Palliative Radiotherapy.',
      cordCompressed: false,
    },
    grade_1b: {
      label: 'Grade 1b: Thecal Sac Deformation',
      desc: 'Thecal sac is deformed/flattened, but tumor does not abut the spinal cord.',
      surgicalNeed: 'Radiotherapy vs SBRT. Monitor neurology closely.',
      cordCompressed: false,
    },
    grade_1c: {
      label: 'Grade 1c: Cord Abutment (No Compression)',
      desc: 'Tumor abuts the spinal cord, but there is no displacement or cord indentation.',
      surgicalNeed: 'High risk for progression. Consider separation surgery if radioresistant.',
      cordCompressed: false,
    },
    grade_2: {
      label: 'Grade 2: Cord Compression (CSF Visible)',
      desc: 'Spinal cord is compressed and indented, but CSF remains visible around the cord.',
      surgicalNeed: 'High-grade compression. Surgical decompression indicated if fit.',
      cordCompressed: true,
    },
    grade_3: {
      label: 'Grade 3: Severe Compression (No CSF Visible)',
      desc: 'Spinal cord is severely compressed with total circumferential obliteration of CSF.',
      surgicalNeed: 'EMERGENCY: Direct surgical decompression + instrumentation if viable.',
      cordCompressed: true,
    },
  };

  // High-Grade MSCC detection
  const isHighGradeMscc = bilskyGrade === 'grade_2' || bilskyGrade === 'grade_3';

  // Toggle Red Flag
  const toggleRedFlag = (id: string) => {
    setRedFlags((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  // Patchell Surgical vs RT Decision Logic
  const treatmentRecommendation = useMemo(() => {
    const isParaplegicProlonged = ambulatoryStatus === 'paraplegic_prolonged' || paraplegiaDurationHours > 48;
    
    if (isHighGradeMscc) {
      if (isParaplegicProlonged) {
        return {
          tier: 'Urgent Radiotherapy Alone (Prolonged Paraplegia >48h)',
          color: 'text-amber-700 bg-amber-50 border-amber-200',
          badge: 'bg-amber-100 text-amber-800',
          rationale: 'Paraplegia > 48 hours is associated with permanent ischemic axonal necrosis. Surgical recovery of ambulation is <5%. Urgent radiotherapy (8 Gy/1fx or 20 Gy/5fx) + high-dose dexamethasone is standard.',
          action: 'Urgent Radiotherapy + High-Dose Steroids',
        };
      }
      if (tumorHistology === 'radiosensitive') {
        return {
          tier: 'Urgent Radiotherapy (Radiosensitive Histology)',
          color: 'text-teal-700 bg-teal-50 border-teal-200',
          badge: 'bg-teal-100 text-teal-800',
          rationale: 'Hematologic malignancies (Lymphoma, Myeloma) or Small Cell Carcinoma exhibit rapid tumor lysis with radiotherapy + steroids, achieving outcomes equivalent to surgery without operative morbidity.',
          action: 'STAT Radiotherapy & Dexamethasone',
        };
      }
      return {
        tier: 'EMERGENCY SURGICAL DECOMPRESSION (Patchell Protocol)',
        color: 'text-rose-700 bg-rose-50 border-rose-200',
        badge: 'bg-rose-100 text-rose-800',
        rationale: 'Direct decompressive surgical resection + posterior pedicle screw stabilization within 24 hours followed by post-op RT yields an 84% post-treatment ambulation rate vs 57% for radiation alone (Patchell Trial).',
        action: 'STAT Spine Surgery Consultation (<24h)',
      };
    }

    return {
      tier: 'Conservative Palliative Radiotherapy / SBRT',
      color: 'text-emerald-700 bg-emerald-50 border-emerald-200',
      badge: 'bg-emerald-100 text-emerald-800',
      rationale: 'Low-grade epidural disease (Bilsky 0–1b) without cord compression. Radiotherapy achieves 75–80% pain relief and prevents neurologic deterioration.',
      action: 'Palliative Radiotherapy (8 Gy / 1fx)',
    };
  }, [isHighGradeMscc, ambulatoryStatus, paraplegiaDurationHours, tumorHistology]);

  // Copy Plan
  const handleCopyPlan = () => {
    const summary = `MALIGNANT SPINAL CORD COMPRESSION (MSCC) STAT ACTION
Patient: ${patientName} • ${primaryCancer} • Hospital ID: #${patientId}
Ambulatory Status: ${ambulatoryStatus.toUpperCase()} (Motor Power: ${motorPowerMrc}/5)
Sensory Level: ${sensoryLevel} • Bladder: ${redFlags.urinary_retention ? 'Urinary Retention (Foley Placed)' : 'Normal'}
Bilsky Grade: ${bilskyMetadata[bilskyGrade].label}
Decision: ${treatmentRecommendation.tier}

1. HIGH-DOSE STEROID LOADING (IMMEDIATE):
- Dexamethasone: 16 mg IV/PO STAT loading dose immediately.
- Maintenance: 8 mg PO/IV BD (16 mg/day) with breakfast and lunch.
- Gastric Protection: Pantoprazole 40 mg PO/IV daily.
- Blood Glucose Monitoring: Check QID for steroid-induced hyperglycemia.

2. SURGICAL & ONCOLOGY PATHWAY:
- Recommendation: ${treatmentRecommendation.action}
- Rationale: ${treatmentRecommendation.rationale}

3. NURSING & BLADDER PROTOCOL:
- Strict neutral spine alignment / log-roll precautions during transfers.
- Urgent Foley Catheterization with residual volume recording.
- Bedside MRI Spine (Whole Spine T1, T2, STIR) requested STAT.`;

    navigator.clipboard.writeText(summary);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  // Dispatch Plan
  const handleDispatchPlan = () => {
    const summary = `MSCC STAT CODE ACTIVATED: ${treatmentRecommendation.action}. Dexamethasone 16mg given. Bilsky ${bilskyGrade}, Motor ${motorPowerMrc}/5, Ambulatory: ${ambulatoryStatus}.`;
    if (onDispatchPlan) {
      onDispatchPlan(summary);
    }
    setDispatchedToast(true);
    setTimeout(() => setDispatchedToast(false), 3000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-3 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-5xl max-h-[92vh] flex flex-col overflow-hidden text-slate-800 animate-in fade-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="bg-gradient-to-r from-red-800 via-rose-900 to-slate-950 text-white p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-white/15 rounded-xl backdrop-blur-md text-white border border-white/20">
              <IconShieldAlert className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Malignant Spinal Cord Compression (MSCC) Suite</h2>
                <span className="px-2 py-0.5 bg-red-600/80 rounded-full text-[11px] font-black tracking-wide uppercase animate-pulse">
                  NEUROLOGIC ONCOLOGY STAT
                </span>
              </div>
              <p className="text-xs text-rose-200 mt-0.5">
                Patient: <span className="font-semibold text-white">{patientName}</span> • {primaryCancer} • Hospital ID: #{patientId}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-[11px] text-rose-200 font-medium">Bilsky ESCC Score</div>
              <div className="text-sm font-extrabold text-white">{bilskyMetadata[bilskyGrade].label.split(':')[0]}</div>
            </div>
            <button
              onClick={onClose}
              className="p-2 hover:bg-white/20 rounded-lg text-white/90 hover:text-white transition"
              aria-label="Close modal"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-200 bg-slate-50/80 px-4 overflow-x-auto text-xs font-semibold text-slate-600 gap-1">
          <button
            onClick={() => setActiveTab('triage_exam')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'triage_exam'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconHeartPulse className="w-4 h-4" />
            <span>1. Neurologic Exam & Red Flags (MRC Power / Sensory)</span>
          </button>
          <button
            onClick={() => setActiveTab('bilsky_scale')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'bilsky_scale'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconSparkles className="w-4 h-4" />
            <span>2. Bilsky MRI ESCC Staging (Interactive SVG Canvas)</span>
          </button>
          <button
            onClick={() => setActiveTab('dexamethasone')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'dexamethasone'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldCheck className="w-4 h-4" />
            <span>3. STAT Dexamethasone & Gastric Protection</span>
          </button>
          <button
            onClick={() => setActiveTab('surgical_decision')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'surgical_decision'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldAlert className="w-4 h-4" />
            <span>4. Surgical Decompression vs. Radiotherapy (Patchell)</span>
          </button>
          <button
            onClick={() => setActiveTab('counseling')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'counseling'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconUsers className="w-4 h-4" />
            <span>5. Emergency Counseling & Bladder Care (द्विभाषी)</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 bg-slate-50/40">
          
          {/* Quick Alert Bar */}
          <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-xs mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Ambulatory Status:</span>
                <span className={`font-black px-2 py-0.5 rounded border ${
                  ambulatoryStatus === 'independent'
                    ? 'text-emerald-800 bg-emerald-50 border-emerald-200'
                    : 'text-rose-800 bg-rose-50 border-rose-300'
                }`}>
                  {ambulatoryStatus.replace(/_/g, ' ').toUpperCase()}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Motor MRC:</span>
                <span className="font-black text-rose-700">{motorPowerMrc} / 5</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Sensory Level:</span>
                <span className="font-bold text-slate-900">{sensoryLevel}</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className={`px-2.5 py-1 rounded-full text-xs font-black border ${treatmentRecommendation.color}`}>
                {treatmentRecommendation.action}
              </span>
            </div>
          </div>

          {/* TAB 1: NEUROLOGIC EXAM & RED FLAGS */}
          {activeTab === 'triage_exam' && (
            <div className="space-y-6">
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* Motor & Ambulatory Card */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="border-b pb-3 flex items-center justify-between">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">1. Motor Function & Functional Ambulation</h3>
                      <p className="text-xs text-slate-500">Ambulatory status at presentation is the #1 predictor of final outcome</p>
                    </div>
                    <span className="text-xs font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded border border-rose-200">
                      Time is Spine
                    </span>
                  </div>

                  {/* Ambulatory Selector */}
                  <div>
                    <label className="text-xs font-bold text-slate-700 block mb-1.5">Functional Walking Capability:</label>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {[
                        { id: 'independent', label: 'Walks Independently', desc: '80–90% remain walking after therapy' },
                        { id: 'with_aid', label: 'Walks With Stick / Frame', desc: 'Moderate paresis; urgent rescue needed' },
                        { id: 'non_ambulatory_recent', label: 'Paraplegic (< 24h)', desc: 'STAT emergency; axons still salvageable' },
                        { id: 'paraplegic_prolonged', label: 'Paraplegic (> 48h)', desc: '<5% regain walking; palliative goals' },
                      ].map((s) => (
                        <button
                          key={s.id}
                          onClick={() => setAmbulatoryStatus(s.id as any)}
                          className={`p-2.5 rounded-lg border text-left text-xs transition ${
                            ambulatoryStatus === s.id
                              ? 'border-rose-600 bg-rose-50 text-rose-950 font-bold ring-1 ring-rose-500'
                              : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100'
                          }`}
                        >
                          <div className="font-bold">{s.label}</div>
                          <div className="text-[10px] text-slate-500 mt-0.5 font-normal">{s.desc}</div>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* MRC Motor Power Slider */}
                  <div className="pt-2">
                    <div className="flex items-center justify-between text-xs font-bold text-slate-700 mb-1">
                      <span>MRC Lower Limb Motor Power:</span>
                      <span className="font-black text-rose-700 text-sm">Grade {motorPowerMrc} / 5</span>
                    </div>
                    <input
                      type="range"
                      min="0"
                      max="5"
                      step="1"
                      value={motorPowerMrc}
                      onChange={(e) => setMotorPowerMrc(Number(e.target.value))}
                      className="w-full accent-rose-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-400 mt-1 font-bold">
                      <span>0 (Paralyzed)</span>
                      <span>1 (Flicker)</span>
                      <span>2 (Gravity-free)</span>
                      <span>3 (Anti-gravity)</span>
                      <span>4 (Against resist)</span>
                      <span>5 (Normal)</span>
                    </div>
                  </div>

                  {/* Sensory Dermatome Level */}
                  <div className="pt-2">
                    <label className="text-xs font-bold text-slate-700 block mb-1">Sensory Level (Dermatome):</label>
                    <select
                      value={sensoryLevel}
                      onChange={(e) => setSensoryLevel(e.target.value)}
                      className="w-full p-2 border border-slate-300 rounded-lg text-xs bg-slate-50 font-semibold"
                    >
                      <option value="C5–C7 (Upper Limb/Cervical)">C5–C7 (Cervical - Quadriparesis risk)</option>
                      <option value="T4 (Nipple Line)">T4 (Nipple Line - Upper Thoracic)</option>
                      <option value="T10 (Umbilicus)">T10 (Umbilicus - Mid Thoracic)</option>
                      <option value="L1 (Groin Ligament)">L1 (Groin Ligament - Thoracolumbar junction)</option>
                      <option value="L4–L5 (Anterior Knee / Foot drop)">L4–L5 (Lumbar - Cauda Equina pattern)</option>
                      <option value="S2–S5 (Perineal Saddle Anesthesia)">S2–S5 (Sacral Saddle Anesthesia - Conus)</option>
                    </select>
                  </div>
                </div>

                {/* Red Flags & Sphincter Card */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="border-b pb-3">
                    <h3 className="text-sm font-bold text-slate-900">2. Oncologic Red Flag Symptoms Checklist</h3>
                    <p className="text-xs text-slate-500">Classic hallmark signs of impending or frank spinal cord compression</p>
                  </div>

                  <div className="space-y-2.5">
                    {[
                      { id: 'radicular_band_pain', label: 'Radicular Band-Like Pain (धड़ के चारों ओर पट्टे जैसा दर्द)', desc: 'Constant thoracic girdle pain wrapping around chest or abdomen like a tight belt.' },
                      { id: 'nocturnal_waking_pain', label: 'Nocturnal Waking Pain (रात में नींद खुल जाना)', desc: 'Severe skeletal spinal pain that wakes the patient from deep sleep, worse when lying flat.' },
                      { id: 'urinary_retention', label: 'Urinary Retention / Bladder Dysfunction (पेशाब रुकना)', desc: 'Painless bladder distension, overflow incontinence, post-void residual > 200 mL.' },
                      { id: 'saddle_anesthesia', label: 'Saddle Anesthesia (शौच-पेशाब की जगह सुन्न पड़ना)', desc: 'Loss of sensation over buttocks, perineum, and inner thighs (S2–S5 dermatomes).' },
                      { id: 'progressive_limb_weakness', label: 'Progressive Lower Limb Weakness (पैरों में कमजोरी)', desc: 'Legs giving way, difficulty climbing stairs, heavy uncoordinated gait.' },
                    ].map((rf) => {
                      const isChecked = !!redFlags[rf.id];
                      return (
                        <div
                          key={rf.id}
                          onClick={() => toggleRedFlag(rf.id)}
                          className={`p-3 rounded-xl border cursor-pointer transition flex items-start gap-3 ${
                            isChecked
                              ? 'border-rose-600 bg-rose-50/60 ring-1 ring-rose-500'
                              : 'border-slate-200 bg-white hover:bg-slate-50'
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => {}}
                            className="mt-1 h-4 w-4 accent-rose-600 rounded cursor-pointer"
                          />
                          <div>
                            <span className="text-xs font-bold text-slate-900">{rf.label}</span>
                            <p className="text-[11px] text-slate-600 mt-0.5">{rf.desc}</p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 2: BILSKY MRI ESCC STAGING & SVG CANVAS */}
          {activeTab === 'bilsky_scale' && (
            <div className="space-y-6">
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* Interactive SVG Spinal Cross-Section */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="border-b pb-3 mb-3 flex items-center justify-between">
                      <div>
                        <h3 className="text-sm font-bold text-slate-900">Axial Spinal Canal & Cord Morphology</h3>
                        <p className="text-xs text-slate-500">Interactive axial CT/MRI cross-section demonstrating Bilsky grading</p>
                      </div>
                      <span className="text-[11px] font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded border border-rose-200">
                        {bilskyMetadata[bilskyGrade].label.split(':')[0]}
                      </span>
                    </div>

                    {/* SVG Graphic */}
                    <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex flex-col items-center">
                      <svg className="w-full max-w-sm h-64" viewBox="0 0 280 240">
                        {/* Vertebral Body (Anterior) */}
                        <path
                          d="M 60 40 C 60 10, 220 10, 220 40 C 220 90, 60 90, 60 40"
                          fill="#f1f5f9"
                          stroke="#64748b"
                          strokeWidth="2.5"
                        />
                        <text x="140" y="55" fontSize="10" fontWeight="bold" fill="#64748b" textAnchor="middle">
                          Vertebral Body (Anterior)
                        </text>

                        {/* Pedicles & Posterior Arch */}
                        <path d="M 65 65 L 50 120 L 70 170 L 140 215 L 210 170 L 230 120 L 215 65" fill="none" stroke="#64748b" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
                        <line x1="140" y1="215" x2="140" y2="235" stroke="#64748b" strokeWidth="8" strokeLinecap="round" />
                        <text x="140" y="235" fontSize="8" fontWeight="bold" fill="#94a3b8" textAnchor="middle">
                          Spinous Process
                        </text>

                        {/* Spinal Canal Inner Perimeter (White space) */}
                        <circle cx="140" cy="130" r="52" fill="#ffffff" stroke="#cbd5e1" strokeWidth="1.5" />

                        {/* Tumor Mass Invasion into canal based on Bilsky Grade */}
                        {bilskyGrade === 'grade_0' && (
                          <circle cx="140" cy="55" r="14" fill="#ef4444" opacity="0.8" />
                        )}

                        {bilskyGrade === 'grade_1a' && (
                          <path d="M 110 80 C 125 90, 155 90, 170 80 C 160 70, 120 70, 110 80" fill="#ef4444" stroke="#b91c1c" strokeWidth="1.5" />
                        )}

                        {bilskyGrade === 'grade_1b' && (
                          <path d="M 100 85 C 120 105, 160 105, 180 85 C 165 72, 115 72, 100 85" fill="#ef4444" stroke="#b91c1c" strokeWidth="1.5" />
                        )}

                        {bilskyGrade === 'grade_1c' && (
                          <path d="M 95 90 C 115 116, 165 116, 185 90 C 170 72, 110 72, 95 90" fill="#ef4444" stroke="#b91c1c" strokeWidth="1.5" />
                        )}

                        {bilskyGrade === 'grade_2' && (
                          <path d="M 90 92 C 110 132, 170 132, 190 92 C 175 70, 105 70, 90 92" fill="#ef4444" stroke="#b91c1c" strokeWidth="1.5" />
                        )}

                        {bilskyGrade === 'grade_3' && (
                          <path d="M 85 95 C 105 150, 175 150, 195 95 C 180 68, 100 68, 85 95" fill="#ef4444" stroke="#b91c1c" strokeWidth="1.5" />
                        )}

                        {/* Thecal Sac (Blue ring for CSF) */}
                        {(() => {
                          const thecalR = 38;
                          const thecalCy = bilskyGrade === 'grade_3' ? 142 : bilskyGrade === 'grade_2' ? 138 : 132;
                          const thecalRy = bilskyGrade === 'grade_3' ? 18 : bilskyGrade === 'grade_2' ? 26 : 38;
                          return (
                            <ellipse
                              cx="140"
                              cy={thecalCy}
                              rx={thecalR}
                              ry={thecalRy}
                              fill={bilskyGrade === 'grade_3' ? '#fecaca' : '#e0f2fe'}
                              stroke="#38bdf8"
                              strokeWidth="2"
                            />
                          );
                        })()}

                        {/* Spinal Cord (Pink circle inside CSF) */}
                        {(() => {
                          const cordCy = bilskyGrade === 'grade_3' ? 142 : bilskyGrade === 'grade_2' ? 138 : 132;
                          const cordRy = bilskyGrade === 'grade_3' ? 10 : bilskyGrade === 'grade_2' ? 15 : 22;
                          return (
                            <ellipse
                              cx="140"
                              cy={cordCy}
                              rx="22"
                              ry={cordRy}
                              fill="#fda4af"
                              stroke="#e11d48"
                              strokeWidth="2"
                            />
                          );
                        })()}

                        <text x="140" y={bilskyGrade === 'grade_3' ? 145 : bilskyGrade === 'grade_2' ? 141 : 135} fontSize="8" fontWeight="bold" fill="#881337" textAnchor="middle">
                          Spinal Cord
                        </text>
                      </svg>

                      <div className="mt-2 text-center text-xs">
                        <span className="font-bold text-slate-800">{bilskyMetadata[bilskyGrade].label}</span>
                        <p className="text-[11px] text-slate-500 mt-0.5">{bilskyMetadata[bilskyGrade].desc}</p>
                      </div>
                    </div>
                  </div>

                  {/* Cord Compression Risk Badge */}
                  <div className={`mt-4 p-3 rounded-lg border text-xs ${
                    isHighGradeMscc ? 'bg-red-50 border-red-300 text-red-950 font-semibold' : 'bg-slate-50 border-slate-200 text-slate-700'
                  }`}>
                    <span className="font-bold">Surgical Indication: </span>
                    <span>{bilskyMetadata[bilskyGrade].surgicalNeed}</span>
                  </div>
                </div>

                {/* Bilsky Radio Selector */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-3">
                  <div className="border-b pb-2">
                    <h3 className="text-sm font-bold text-slate-900">Bilsky ESCC Grading System</h3>
                    <p className="text-xs text-slate-500">Select MRI appearance of epidural tumor and thecal sac/cord interface</p>
                  </div>

                  <div className="space-y-2">
                    {(Object.keys(bilskyMetadata) as BilskyGrade[]).map((gradeKey) => {
                      const item = bilskyMetadata[gradeKey];
                      const isSelected = bilskyGrade === gradeKey;
                      return (
                        <div
                          key={gradeKey}
                          onClick={() => setBilskyGrade(gradeKey)}
                          className={`p-3 rounded-xl border cursor-pointer transition ${
                            isSelected
                              ? 'border-rose-600 bg-rose-50/70 ring-1 ring-rose-500'
                              : 'border-slate-200 bg-white hover:bg-slate-50'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-slate-900">{item.label}</span>
                            {item.cordCompressed && (
                              <span className="px-1.5 py-0.2 rounded bg-red-100 text-red-800 text-[10px] font-bold">
                                Cord Compressed
                              </span>
                            )}
                          </div>
                          <p className="text-[11px] text-slate-600 mt-0.5 leading-relaxed">{item.desc}</p>
                        </div>
                      );
                    })}
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 3: STAT DEXAMETHASONE & GASTRIC PROTECTION */}
          {activeTab === 'dexamethasone' && (
            <div className="space-y-5">
              
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="border-b pb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="p-2 rounded-lg bg-rose-100 text-rose-700">
                      <IconSparkles className="w-4 h-4" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">STAT Dexamethasone Loading & Maintenance Protocol</h3>
                      <p className="text-xs text-slate-500">Reduces vasogenic spinal cord edema, capillary permeability, and preserves motor evoked potentials</p>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 bg-red-100 text-red-900 rounded-lg text-xs font-black">
                    Immediate Loading
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  
                  {/* Immediate STAT Dose */}
                  <div className="p-4 rounded-xl bg-rose-50/80 border border-rose-200 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-extrabold text-rose-950 text-sm">1. STAT Loading Dose:</span>
                      <span className="text-lg font-black text-rose-900">16 mg IV / PO</span>
                    </div>
                    <p className="text-rose-900 text-[11px] leading-relaxed">
                      Administer <b>Dexamethasone 16 mg IV or oral</b> immediately upon clinical suspicion. <b>DO NOT wait for MRI confirmation!</b> Irreversible ischemic cord infarction begins within hours of untreated cord edema.
                    </p>
                  </div>

                  {/* Maintenance Schedule */}
                  <div className="p-4 rounded-xl bg-amber-50/80 border border-amber-200 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-extrabold text-amber-950 text-sm">2. Maintenance Dose:</span>
                      <span className="text-lg font-black text-amber-900">8 mg PO BD (16 mg/day)</span>
                    </div>
                    <p className="text-amber-900 text-[11px] leading-relaxed">
                      Give 8 mg with breakfast and 8 mg with lunch (avoid late evening doses to prevent severe steroid-induced insomnia and delirium).
                    </p>
                  </div>

                </div>

                {/* Mandatory Co-Prescriptions Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs pt-2">
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="font-bold text-slate-800 block mb-0.5">Mandatory Gastric Protection:</span>
                    <p className="text-[11px] text-slate-600">
                      Co-prescribe <b>Pantoprazole 40 mg PO/IV daily</b> or Omeprazole. High-dose steroids cause acute stress ulceration and mucosal bleeding.
                    </p>
                  </div>

                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="font-bold text-slate-800 block mb-0.5">Blood Glucose Monitoring (QID):</span>
                    <p className="text-[11px] text-slate-600">
                      Steroids induce profound hepatic gluconeogenesis. Monitor capillary blood glucose before meals and at bedtime; initiate sliding-scale insulin if blood sugar exceeds 200 mg/dL.
                    </p>
                  </div>
                </div>

                {/* Steroid Tapering Strategy */}
                <div className="p-3 bg-slate-100 rounded-lg text-xs space-y-1">
                  <span className="font-bold text-slate-800">Tapering Strategy (Post-Decompression):</span>
                  <p className="text-[11px] text-slate-600">
                    Once definitive treatment (surgery or radiotherapy) is completed, taper Dexamethasone by 2–4 mg every 2–3 days over 10–14 days. Never stop abruptly to prevent secondary acute adrenal insufficiency or rebound cord edema.
                  </p>
                </div>
              </div>

            </div>
          )}

          {/* TAB 4: SURGICAL DECOMPRESSION VS RADIOTHERAPY */}
          {activeTab === 'surgical_decision' && (
            <div className="space-y-5">
              
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="border-b pb-3">
                  <h3 className="text-sm font-bold text-slate-900">Patchell Trial Surgical Decompression Algorithm</h3>
                  <p className="text-xs text-slate-500">Determine whether direct circumferential decompression + stabilization or radiation is indicated</p>
                </div>

                {/* Histology Radio Sensitivity Selector */}
                <div className="text-xs space-y-1.5">
                  <label className="font-bold text-slate-700 block">Primary Tumor Radiosensitivity Category:</label>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { id: 'radiosensitive', label: 'Radiosensitive', desc: 'Lymphoma, Myeloma, Small Cell Lung' },
                      { id: 'intermediate', label: 'Intermediate', desc: 'Breast, Prostate, Non-small cell lung' },
                      { id: 'radioresistant', label: 'Radioresistant', desc: 'Renal Cell, Melanoma, Thyroid, Colon' },
                    ].map((h) => (
                      <button
                        key={h.id}
                        onClick={() => setTumorHistology(h.id as any)}
                        className={`p-2.5 rounded-lg border text-left text-xs transition ${
                          tumorHistology === h.id
                            ? 'border-rose-600 bg-rose-50 text-rose-950 font-bold ring-1 ring-rose-500'
                            : 'border-slate-200 bg-slate-50 text-slate-700'
                        }`}
                      >
                        <div className="font-bold">{h.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{h.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Algorithmic Recommendation Banner */}
                <div className={`p-4 rounded-xl border ${treatmentRecommendation.color} space-y-2`}>
                  <div className="flex items-center gap-2">
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-black ${treatmentRecommendation.badge}`}>
                      {treatmentRecommendation.action}
                    </span>
                    <span className="text-xs font-bold text-slate-800">{treatmentRecommendation.tier}</span>
                  </div>
                  <p className="text-xs text-slate-900 leading-relaxed font-medium">
                    {treatmentRecommendation.rationale}
                  </p>
                </div>

                {/* Patchell Trial Landmark Comparison Table */}
                <div className="border border-slate-200 rounded-xl overflow-hidden bg-slate-50/50 text-xs">
                  <div className="bg-slate-100 p-2.5 font-bold text-slate-700 border-b">
                    Landmark Evidence: Patchell et al. (Lancet 2005) Randomized Trial
                  </div>
                  <div className="p-3 grid grid-cols-2 gap-4">
                    <div className="p-2.5 bg-white rounded-lg border border-slate-200">
                      <div className="font-bold text-rose-800 mb-0.5">Surgery + Post-op RT:</div>
                      <div className="text-lg font-black text-rose-950">84% Regained / Retained Walking</div>
                      <p className="text-[11px] text-slate-500 mt-1">Median survival 126 days; significantly lower opioid requirement.</p>
                    </div>

                    <div className="p-2.5 bg-white rounded-lg border border-slate-200">
                      <div className="font-bold text-slate-700 mb-0.5">Radiotherapy Alone:</div>
                      <div className="text-lg font-black text-slate-900">57% Retained Walking</div>
                      <p className="text-[11px] text-slate-500 mt-1">Median survival 35 days in non-ambulatory cohort.</p>
                    </div>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 5: BILINGUAL COUNSELING & BLADDER PROTOCOL */}
          {activeTab === 'counseling' && (
            <div className="space-y-4">
              
              <div className="p-4 rounded-xl bg-gradient-to-r from-red-50 to-rose-50 border border-red-200">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-red-950 text-sm">Emergency Family Communication & Bladder Protocol (द्विभाषी)</span>
                  <span className="px-2 py-0.5 bg-red-200 text-red-900 rounded text-[10px] font-bold">Spine Emergency</span>
                </div>
                <p className="text-xs text-red-950">
                  Every hour of delay in malignant cord compression risks irreversible lower body paralysis. Ensure family understands the critical urgency of immediate hospital evaluation.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* 1. Urgent Warning Phrase */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">1. Explaining the Urgency (तुरंत अस्पताल पहुंचने का महत्व)</span>
                    <span className="text-[10px] text-rose-700 font-bold bg-rose-50 px-2 py-0.5 rounded">Critical Urgency</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Guidance (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5 leading-relaxed">
                        &quot;कैंसर रीढ़ की हड्डी की मुख्य नस (स्पाइनल कॉर्ड) को दबा रहा है। अगर पैरों में कमजोरी आ रही है या पेशाब रुक गया है, तो सुबह होने का इंतजार बिल्कुल न करें! अगले कुछ घंटों के अंदर नस का दबाव हटाना जरूरी है, वरना पैरों की ताकत हमेशा के लिए खत्म हो सकती है।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>English:</b> Stresses that walking ability can only be salvaged if decompression is initiated before total motor loss is established.
                    </p>
                  </div>
                </div>

                {/* 2. Bladder Catheterization Protocol */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">2. Acute Bladder Management (पेशाब की नली / कैथेटर)</span>
                    <span className="text-[10px] text-amber-700 font-bold bg-amber-50 px-2 py-0.5 rounded">Foley Protocol</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Guidance (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5 leading-relaxed">
                        &quot;नस दबने से पेशाब की थैली का महसूस होना बंद हो जाता है और पेशाब अंदर ही भरता रहता है। हमने पेशाब की नली (कैथेटर) लगा दी है ताकि गुर्दों पर दबाव न पड़े और संक्रमण न फैले।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>Nursing Protocol:</b> Place 14–16 Fr Foley catheter; decompress bladder gradually in increments of 500 mL to avoid decompression hematuria.
                    </p>
                  </div>
                </div>

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
              {copiedToast ? '✓ Copied MSCC Protocol!' : '📋 Copy STAT MSCC Protocol'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-red-700 hover:bg-red-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched MSCC STAT Orders!' : '⚡ Dispatch STAT MSCC Spine & Radiation Alert'}</span>
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
