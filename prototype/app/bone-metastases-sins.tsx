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

export interface SinsAssessment {
  locationScore: number;
  locationLabel: string;
  painScore: number;
  painLabel: string;
  boneLesionScore: number;
  boneLesionLabel: string;
  alignmentScore: number;
  alignmentLabel: string;
  collapseScore: number;
  collapseLabel: string;
  posterolateralScore: number;
  posterolateralLabel: string;
}

export interface MirelsAssessment {
  site: number; // 1 = Upper limb, 2 = Lower limb, 3 = Peritrochanteric
  pain: number; // 1 = Mild, 2 = Moderate, 3 = Functional/Mechanical
  lesion: number; // 1 = Blastic, 2 = Mixed, 3 = Lytic
  size: number; // 1 = <1/3 cortex, 2 = 1/3-2/3 cortex, 3 = >2/3 cortex
}

interface BoneMetastasesSinsModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchReferral?: (summary: string) => void;
}

export function BoneMetastasesSinsModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchReferral,
}: BoneMetastasesSinsModalProps) {
  const [activeTab, setActiveTab] = useState<'sins' | 'radiation' | 'mirels' | 'history'>('sins');

  // SINS State
  const [selectedVertebra, setSelectedVertebra] = useState<string>('T11-L1 Junction');
  const [locationScore, setLocationScore] = useState<number>(3); // 3 = Junctional
  const [painScore, setPainScore] = useState<number>(3); // 3 = Mechanical pain
  const [boneLesionScore, setBoneLesionScore] = useState<number>(2); // 2 = Lytic
  const [alignmentScore, setAlignmentScore] = useState<number>(2); // 2 = De novo deformity
  const [collapseScore, setCollapseScore] = useState<number>(2); // 2 = <50% collapse
  const [posterolateralScore, setPosterolateralScore] = useState<number>(1); // 1 = Unilateral

  // Radiation fractionation choice
  const [selectedFractionation, setSelectedFractionation] = useState<'8gy_1fx' | '20gy_5fx' | '30gy_10fx'>('8gy_1fx');
  const [dexamethasoneProphylaxis, setDexamethasoneProphylaxis] = useState<boolean>(true);

  // Mirels State
  const [mirelsSite, setMirelsSite] = useState<number>(3); // Peritrochanteric femur
  const [mirelsPain, setMirelsPain] = useState<number>(3); // Functional mechanical pain
  const [mirelsLesion, setMirelsLesion] = useState<number>(3); // Lytic
  const [mirelsSize, setMirelsSize] = useState<number>(2); // 1/3-2/3 cortex

  // Feedback states
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchedToast, setDispatchedToast] = useState<boolean>(false);

  // Calculate Total SINS Score
  const totalSinsScore = useMemo(() => {
    return locationScore + painScore + boneLesionScore + alignmentScore + collapseScore + posterolateralScore;
  }, [locationScore, painScore, boneLesionScore, alignmentScore, collapseScore, posterolateralScore]);

  // SINS Classification & Recommendation
  const sinsClassification = useMemo(() => {
    if (totalSinsScore >= 13) {
      return {
        category: 'FRANK MECHANICAL INSTABILITY',
        tier: 'unstable',
        color: '#be123c',
        bg: '#ffe4e6',
        borderColor: '#f43f5e',
        badge: 'SURGICAL EMERGENCY',
        recommendation: 'Urgent Spine Neurosurgery Consultation for mechanical stabilization / pedicle fixation BEFORE any radiotherapy. Unstable spine carries imminent risk of pathological collapse and irreversible cord injury.',
      };
    }
    if (totalSinsScore >= 7) {
      return {
        category: 'POTENTIALLY UNSTABLE SPINE',
        tier: 'indeterminate',
        color: '#c2410c',
        bg: '#ffedd5',
        borderColor: '#fb923c',
        badge: 'SURGICAL EVALUATION REQUIRED',
        recommendation: 'Spine Surgeon / Ortho Oncology review required. Assess for percutaneous cement augmentation (kyphoplasty/vertebroplasty) or short-segment instrumentation prior to initiating definitive palliative radiation.',
      };
    }
    return {
      category: 'STABLE SPINE',
      tier: 'stable',
      color: '#047857',
      bg: '#d1fae5',
      borderColor: '#34d399',
      badge: 'RADIOTHERAPY CANDIDATE',
      recommendation: 'Spine is mechanically stable. Proceed directly with ASTRO/Chow-guided single fraction (8 Gy in 1 fx) or multi-fraction palliative radiotherapy. No immediate orthopedic surgery indicated.',
    };
  }, [totalSinsScore]);

  // Mirels Score Calculation
  const totalMirelsScore = useMemo(() => {
    return mirelsSite + mirelsPain + mirelsLesion + mirelsSize;
  }, [mirelsSite, mirelsPain, mirelsLesion, mirelsSize]);

  const mirelsClassification = useMemo(() => {
    if (totalMirelsScore >= 9) {
      return {
        category: 'HIGH FRACTURE RISK (>33%) - SURGICAL FIXATION INDICATED',
        color: '#be123c',
        bg: '#ffe4e6',
        badge: 'PROPHYLACTIC NAILING / ARTHROPLASTY',
        advice: 'Orthopaedic surgery consult for prophylactic intramedullary rod or plate fixation before weight-bearing. Do not initiate radiotherapy alone on an impending pathological fracture.',
      };
    }
    if (totalMirelsScore === 8) {
      return {
        category: 'BORDERLINE RISK (15%) - CLOSE CLINICAL MONITORING',
        color: '#c2410c',
        bg: '#ffedd5',
        badge: 'SURGICAL DISCRETION',
        advice: 'Careful orthopaedic review. Consider protected weight-bearing (crutches/walker) and short-course radiotherapy with frequent radiographic follow-up.',
      };
    }
    return {
      category: 'LOW FRACTURE RISK (<5%) - RADIOTHERAPY SAFE',
      color: '#047857',
      bg: '#d1fae5',
      badge: 'SAFE FOR PALLIATIVE RT',
      advice: 'Low risk of structural fracture. Safe to proceed with 8 Gy single-fraction radiotherapy and medical bone-modifying agents (Zoledronic Acid 4mg IV or Denosumab 120mg SC).',
    };
  }, [totalMirelsScore]);

  const handleCopyReferral = () => {
    const text = `SPINE INSTABILITY NEOPLASTIC SCORE (SINS) & PALLIATIVE RADIOTHERAPY CONSULTATION
Patient: ${patientName} (${patientId})
Primary Diagnosis: ${primaryCancer}
Target Spine Segment: ${selectedVertebra}
--------------------------------------------------
SINS CRITERIA BREAKDOWN (Total: ${totalSinsScore} / 18):
1. Location: ${locationScore} pts (${locationScore === 3 ? 'Junctional: Occiput-C2, C7-T2, T11-L1, L5-S1' : locationScore === 2 ? 'Mobile: C3-C6, L2-L4' : locationScore === 1 ? 'Semirigid: T3-T10' : 'Rigid: S2-S5'})
2. Pain: ${painScore} pts (${painScore === 3 ? 'Mechanical pain (worse standing/bending)' : painScore === 1 ? 'Non-mechanical / night aching' : 'Pain-free'})
3. Bone Lesion: ${boneLesionScore} pts (${boneLesionScore === 2 ? 'Lytic' : boneLesionScore === 1 ? 'Mixed' : 'Blastic'})
4. Spinal Alignment: ${alignmentScore} pts (${alignmentScore === 4 ? 'Subluxation/Translation' : alignmentScore === 2 ? 'De novo kyphosis/scoliosis' : 'Normal'})
5. Vertebral Body Collapse: ${collapseScore} pts (${collapseScore === 3 ? '>50% collapse' : collapseScore === 2 ? '<50% collapse' : collapseScore === 1 ? '>50% body involved no collapse' : 'None'})
6. Posterolateral Elements: ${posterolateralScore} pts (${posterolateralScore === 3 ? 'Bilateral' : posterolateralScore === 1 ? 'Unilateral' : 'None'})
--------------------------------------------------
CLINICAL CLASSIFICATION: ${sinsClassification.category}
RECOMMENDATION: ${sinsClassification.recommendation}
PROPOSED RADIATION REGIMEN: ${selectedFractionation === '8gy_1fx' ? '8 Gy in 1 Fraction (ASTRO Gold Standard)' : selectedFractionation === '20gy_5fx' ? '20 Gy in 5 Fractions' : '30 Gy in 10 Fractions'}
Dexamethasone Pain Flare Prophylaxis: ${dexamethasoneProphylaxis ? 'Prescribed: 8 mg PO Day of RT + Days 1-2 post-RT' : 'Not prescribed'}`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchReferral = () => {
    setDispatchedToast(true);
    if (onDispatchReferral) {
      onDispatchReferral(
        `STAT Spine Evaluation Logged: SINS ${totalSinsScore}/18 (${sinsClassification.category}) at ${selectedVertebra}`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bone-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white flex flex-wrap items-center justify-between gap-3 border-b border-indigo-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/20 border border-indigo-400/40 flex items-center justify-center text-indigo-300">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="bone-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Spine Instability (SINS) & Palliative Radiotherapy Engine
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-indigo-500/20 text-indigo-200 border border-indigo-400/30">
                  Spine Oncology Study Group (SOSG)
                </span>
              </div>
              <p className="text-xs text-indigo-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: sinsClassification.bg,
                color: sinsClassification.color,
                borderColor: sinsClassification.color,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>{sinsClassification.badge} (SINS {totalSinsScore}/18)</span>
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
            onClick={() => setActiveTab('sins')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'sins'
                ? 'border-indigo-600 text-indigo-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🦴 SINS Spine Instability Calculator</span>
          </button>
          <button
            onClick={() => setActiveTab('radiation')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'radiation'
                ? 'border-indigo-600 text-indigo-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>⚡ ASTRO Palliative Radiotherapy</span>
          </button>
          <button
            onClick={() => setActiveTab('mirels')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'mirels'
                ? 'border-indigo-600 text-indigo-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🩻 Mirels Long-Bone Fracture Risk</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-indigo-600 text-indigo-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Metastasis Treatment Log</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: SINS CALCULATOR */}
          {activeTab === 'sins' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: 6 Criteria Pickers */}
              <div className="lg:col-span-7 space-y-4">
                {/* 1. Location */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">1. Anatomical Location</span>
                    <span className="text-xs font-extrabold text-indigo-700">{locationScore} pts</span>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      { score: 3, label: 'Junctional (3 pts)', desc: 'Occiput-C2, C7-T2, T11-L1, L5-S1' },
                      { score: 2, label: 'Mobile Spine (2 pts)', desc: 'C3-C6, L2-L4' },
                      { score: 1, label: 'Semirigid (1 pt)', desc: 'T3-T10 (rib cage stabilization)' },
                      { score: 0, label: 'Rigid (0 pts)', desc: 'S2-S5 (pelvic fixation)' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => {
                          setLocationScore(item.score);
                          if (item.score === 3) setSelectedVertebra('T11-L1 Junction');
                          else if (item.score === 2) setSelectedVertebra('L3 Lumbar');
                          else if (item.score === 1) setSelectedVertebra('T6 Mid-Thoracic');
                          else setSelectedVertebra('Sacrum S2');
                        }}
                        className={`p-2 rounded-lg text-left border transition ${
                          locationScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 2. Pain Quality */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">2. Pain Type & Provocation</span>
                    <span className="text-xs font-extrabold text-indigo-700">{painScore} pts</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { score: 3, label: 'Mechanical (3 pts)', desc: 'Sharp, worse with movement/sitting up, relieved lying flat' },
                      { score: 1, label: 'Non-Mechanical (1 pt)', desc: 'Dull nocturnal/inflammatory ache, not posture-related' },
                      { score: 0, label: 'Pain Free (0 pts)', desc: 'Incidental finding, no localized spine pain' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => setPainScore(item.score)}
                        className={`p-2 rounded-lg text-left border transition ${
                          painScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 3. Bone Lesion Quality */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">3. Radiographic Lesion Type</span>
                    <span className="text-xs font-extrabold text-indigo-700">{boneLesionScore} pts</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { score: 2, label: 'Lytic (2 pts)', desc: 'Osteolytic destruction (high collapse risk)' },
                      { score: 1, label: 'Mixed (1 pt)', desc: 'Mixed lytic and osteoblastic' },
                      { score: 0, label: 'Blastic (0 pts)', desc: 'Dense sclerotic osteoblastic matrix' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => setBoneLesionScore(item.score)}
                        className={`p-2 rounded-lg text-left border transition ${
                          boneLesionScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 4. Spinal Alignment */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">4. Spinal Alignment & Deformity</span>
                    <span className="text-xs font-extrabold text-indigo-700">{alignmentScore} pts</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { score: 4, label: 'Subluxation (4 pts)', desc: 'Vertebral translation / slip' },
                      { score: 2, label: 'De Novo (2 pts)', desc: 'New kyphosis / scoliosis' },
                      { score: 0, label: 'Normal (0 pts)', desc: 'Preserved physiological curvature' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => setAlignmentScore(item.score)}
                        className={`p-2 rounded-lg text-left border transition ${
                          alignmentScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 5. Vertebral Body Collapse */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">5. Vertebral Body Collapse</span>
                    <span className="text-xs font-extrabold text-indigo-700">{collapseScore} pts</span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {[
                      { score: 3, label: '>50% Collapse (3 pts)', desc: 'Severe vertebral height loss' },
                      { score: 2, label: '<50% Collapse (2 pts)', desc: 'Moderate wedge fracture' },
                      { score: 1, label: '>50% Body Inv (1 pt)', desc: 'No height loss yet' },
                      { score: 0, label: 'None (0 pts)', desc: '<50% body involvement' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => setCollapseScore(item.score)}
                        className={`p-2 rounded-lg text-left border transition ${
                          collapseScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* 6. Posterolateral Elements */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">6. Posterolateral Elements (Pedicles, Facets, Lamina)</span>
                    <span className="text-xs font-extrabold text-indigo-700">{posterolateralScore} pts</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { score: 3, label: 'Bilateral (3 pts)', desc: 'Destruction of both pedicles/facets' },
                      { score: 1, label: 'Unilateral (1 pt)', desc: 'One pedicle / facet involved' },
                      { score: 0, label: 'None (0 pts)', desc: 'Posterior arch intact' },
                    ].map((item) => (
                      <button
                        key={item.score}
                        type="button"
                        onClick={() => setPosterolateralScore(item.score)}
                        className={`p-2 rounded-lg text-left border transition ${
                          posterolateralScore === item.score
                            ? 'bg-indigo-50 border-indigo-500 text-indigo-950 ring-2 ring-indigo-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Right Column: Score Gauge & Anatomical Spine Visualizer */}
              <div className="lg:col-span-5 space-y-4">
                {/* SINS Score Gauge Card */}
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition"
                  style={{
                    backgroundColor: sinsClassification.bg,
                    borderColor: sinsClassification.borderColor,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold tracking-wider uppercase text-slate-700">Total SINS Score</span>
                    <span className="text-2xl font-black" style={{ color: sinsClassification.color }}>
                      {totalSinsScore} <span className="text-sm font-semibold text-slate-600">/ 18</span>
                    </span>
                  </div>

                  <div className="mt-2 text-sm font-extrabold" style={{ color: sinsClassification.color }}>
                    {sinsClassification.category}
                  </div>

                  {/* Progress bar */}
                  <div className="w-full bg-slate-200 h-2.5 rounded-full overflow-hidden mt-2">
                    <div
                      className="h-full transition-all duration-500 rounded-full"
                      style={{
                        width: `${(totalSinsScore / 18) * 100}%`,
                        backgroundColor: sinsClassification.color,
                      }}
                    />
                  </div>

                  <p className="text-xs text-slate-700 mt-2.5 leading-relaxed font-medium">
                    {sinsClassification.recommendation}
                  </p>
                </div>

                {/* Anatomical Spine SVG Schematic */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner flex flex-col items-center">
                  <div className="w-full flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span className="font-semibold text-slate-200">Interactive Spine Topography</span>
                    <span className="font-bold text-indigo-300">{selectedVertebra}</span>
                  </div>

                  <div className="w-full max-w-[240px] aspect-[1/2] flex items-center justify-center py-2">
                    <svg viewBox="0 0 100 240" className="w-full h-full drop-shadow-md">
                      {/* Cervical (C1-C7) */}
                      <g
                        onClick={() => {
                          setLocationScore(3);
                          setSelectedVertebra('C1-C2 Junction');
                        }}
                        className="cursor-pointer group"
                      >
                        <rect x="35" y="10" width="30" height="24" rx="4" fill={selectedVertebra.includes('C') ? sinsClassification.color : '#334155'} />
                        <text x="50" y="26" fill="white" fontSize="9" fontWeight="bold" textAnchor="middle">Cervical</text>
                      </g>

                      {/* Upper Thoracic (T1-T2) */}
                      <g
                        onClick={() => {
                          setLocationScore(3);
                          setSelectedVertebra('C7-T2 Junction');
                        }}
                        className="cursor-pointer"
                      >
                        <rect x="33" y="38" width="34" height="18" rx="4" fill={selectedVertebra.includes('T2') ? sinsClassification.color : '#334155'} />
                        <text x="50" y="51" fill="white" fontSize="8" fontWeight="bold" textAnchor="middle">C7-T2</text>
                      </g>

                      {/* Mid Thoracic (T3-T10) */}
                      <g
                        onClick={() => {
                          setLocationScore(1);
                          setSelectedVertebra('T6 Mid-Thoracic');
                        }}
                        className="cursor-pointer"
                      >
                        <rect x="31" y="60" width="38" height="50" rx="5" fill={selectedVertebra.includes('T6') ? sinsClassification.color : '#334155'} />
                        <text x="50" y="88" fill="white" fontSize="9" fontWeight="bold" textAnchor="middle">T3-T10</text>
                      </g>

                      {/* Thoracolumbar Junction (T11-L1) */}
                      <g
                        onClick={() => {
                          setLocationScore(3);
                          setSelectedVertebra('T11-L1 Junction');
                        }}
                        className="cursor-pointer"
                      >
                        <rect x="29" y="114" width="42" height="28" rx="5" fill={selectedVertebra.includes('T11-L1') ? sinsClassification.color : '#334155'} stroke="#818cf8" strokeWidth={selectedVertebra.includes('T11-L1') ? '2' : '0'} />
                        <text x="50" y="132" fill="white" fontSize="9" fontWeight="bold" textAnchor="middle">T11-L1 ★</text>
                      </g>

                      {/* Lumbar (L2-L4) */}
                      <g
                        onClick={() => {
                          setLocationScore(2);
                          setSelectedVertebra('L3 Lumbar');
                        }}
                        className="cursor-pointer"
                      >
                        <rect x="27" y="146" width="46" height="34" rx="5" fill={selectedVertebra.includes('L3') ? sinsClassification.color : '#334155'} />
                        <text x="50" y="167" fill="white" fontSize="9" fontWeight="bold" textAnchor="middle">L2-L4</text>
                      </g>

                      {/* Lumbosacral (L5-S1) */}
                      <g
                        onClick={() => {
                          setLocationScore(3);
                          setSelectedVertebra('L5-S1 Junction');
                        }}
                        className="cursor-pointer"
                      >
                        <rect x="26" y="184" width="48" height="20" rx="4" fill={selectedVertebra.includes('L5-S1') ? sinsClassification.color : '#334155'} />
                        <text x="50" y="198" fill="white" fontSize="8" fontWeight="bold" textAnchor="middle">L5-S1</text>
                      </g>

                      {/* Sacrum (S2-S5) */}
                      <g
                        onClick={() => {
                          setLocationScore(0);
                          setSelectedVertebra('Sacrum S2');
                        }}
                        className="cursor-pointer"
                      >
                        <polygon points="28,208 72,208 50,234" fill={selectedVertebra.includes('Sacrum') ? sinsClassification.color : '#1e293b'} />
                        <text x="50" y="220" fill="white" fontSize="7" fontWeight="bold" textAnchor="middle">Sacrum</text>
                      </g>
                    </svg>
                  </div>

                  <div className="text-[11px] text-slate-400 text-center">
                    Click any segment above to test spinal junction stress points.
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: ASTRO RADIATION REGIMENS */}
          {activeTab === 'radiation' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-200 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-indigo-600 text-white mt-0.5">
                  <IconSparkles className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-indigo-950">ASTRO / Chow 2020 Evidence-Based Radiotherapy Regimens</h3>
                  <p className="text-xs text-indigo-800 mt-0.5">
                    Randomized clinical trials (Dutch Bone Metastasis, RTOG 9714) confirm equivalence in overall pain response rate (~60–70%) between single and multi-fraction regimens.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* 8 Gy in 1 fx */}
                <div
                  onClick={() => setSelectedFractionation('8gy_1fx')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer flex flex-col justify-between ${
                    selectedFractionation === '8gy_1fx'
                      ? 'bg-indigo-50/70 border-indigo-600 shadow-sm ring-2 ring-indigo-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">RECOMMENDED</span>
                      <span className="text-xs font-bold text-indigo-900">1 Day Visit</span>
                    </div>
                    <div className="text-base font-extrabold text-slate-900">8 Gy in 1 Fraction</div>
                    <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">
                      <strong>First-line for frail / poor-prognosis patients.</strong> Minimizes hospital transit burden for patient and family. Proven identical pain relief compared to longer courses.
                    </p>
                  </div>
                  <div className="mt-3 pt-3 border-t text-[11px] text-slate-500">
                    Retreatment rate: ~20% (easily retreated if pain recurs).
                  </div>
                </div>

                {/* 20 Gy in 5 fx */}
                <div
                  onClick={() => setSelectedFractionation('20gy_5fx')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer flex flex-col justify-between ${
                    selectedFractionation === '20gy_5fx'
                      ? 'bg-indigo-50/70 border-indigo-600 shadow-sm ring-2 ring-indigo-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800">5 FRACTIONS</span>
                      <span className="text-xs font-bold text-slate-600">1 Week Course</span>
                    </div>
                    <div className="text-base font-extrabold text-slate-900">20 Gy in 5 Fractions</div>
                    <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">
                      Intermediate regimen. Indicated for patients with reasonable performance status (ECOG 0–2) or spinal lesions with soft-tissue extraosseous component.
                    </p>
                  </div>
                  <div className="mt-3 pt-3 border-t text-[11px] text-slate-500">
                    Lower retreatment rate (~8%). Requires 5 daily hospital visits.
                  </div>
                </div>

                {/* 30 Gy in 10 fx */}
                <div
                  onClick={() => setSelectedFractionation('30gy_10fx')}
                  className={`p-4 rounded-xl border-2 transition cursor-pointer flex flex-col justify-between ${
                    selectedFractionation === '30gy_10fx'
                      ? 'bg-indigo-50/70 border-indigo-600 shadow-sm ring-2 ring-indigo-200'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700">10 FRACTIONS</span>
                      <span className="text-xs font-bold text-slate-600">2 Week Course</span>
                    </div>
                    <div className="text-base font-extrabold text-slate-900">30 Gy in 10 Fractions</div>
                    <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">
                      Reserved for patients with oligometastatic disease, anticipated survival &gt;1 year, or postoperative spinal fixation bed.
                    </p>
                  </div>
                  <div className="mt-3 pt-3 border-t text-[11px] text-slate-500">
                    Highest acute fatigue burden and logistical travel toxicity.
                  </div>
                </div>
              </div>

              {/* Dexamethasone Pain Flare Prophylaxis */}
              <div className="p-4 rounded-xl bg-amber-50 border border-amber-200">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <IconClock className="w-5 h-5 text-amber-700" />
                    <span className="text-xs font-bold text-amber-950 uppercase tracking-wider">
                      Pain Flare Prophylaxis Protocol (NCIC CTG SC.23 Trial)
                    </span>
                  </div>
                  <label className="flex items-center gap-2 text-xs font-bold text-amber-900 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={dexamethasoneProphylaxis}
                      onChange={(e) => setDexamethasoneProphylaxis(e.target.checked)}
                      className="w-4 h-4 text-amber-600 rounded border-amber-300 focus:ring-amber-500"
                    />
                    <span>Include Dexamethasone</span>
                  </label>
                </div>
                <p className="text-xs text-amber-900 leading-relaxed">
                  Up to 35% of patients experience a transient acute &quot;pain flare&quot; within 48 hours of radiotherapy.
                  <strong> Dexamethasone 8 mg PO daily</strong> on the day of radiation and for 2 consecutive days post-radiation reduces pain flare incidence by over 50%.
                </p>
              </div>
            </div>
          )}

          {/* TAB 3: MIRELS LONG BONE FRACTURE SCORE */}
          {activeTab === 'mirels' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-bold text-blue-950">Mirels Scoring System (Extremity Pathological Fracture Risk)</h3>
                  <p className="text-xs text-blue-800 mt-0.5">
                    Evaluates cortical integrity of long bones (Femur, Humerus, Tibia) to identify impending fractures requiring prophylactic orthopedic stabilization.
                  </p>
                </div>
                <div
                  className="px-3 py-1.5 rounded-lg text-xs font-extrabold border shadow-xs"
                  style={{
                    backgroundColor: mirelsClassification.bg,
                    color: mirelsClassification.color,
                  }}
                >
                  Mirels: {totalMirelsScore} / 12
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                {/* Site */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Anatomical Site</span>
                  {[
                    { val: 1, label: 'Upper Limb (1 pt)' },
                    { val: 2, label: 'Lower Limb (2 pts)' },
                    { val: 3, label: 'Peritrochanteric Femur (3 pts)' },
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      onClick={() => setMirelsSite(opt.val)}
                      className={`w-full p-2 rounded text-xs text-left border transition ${
                        mirelsSite === opt.val ? 'bg-blue-50 border-blue-500 text-blue-900 font-bold' : 'text-slate-600'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* Pain */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Pain Severity</span>
                  {[
                    { val: 1, label: 'Mild (1 pt)' },
                    { val: 2, label: 'Moderate (2 pts)' },
                    { val: 3, label: 'Functional / Weight-bearing (3 pts)' },
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      onClick={() => setMirelsPain(opt.val)}
                      className={`w-full p-2 rounded text-xs text-left border transition ${
                        mirelsPain === opt.val ? 'bg-blue-50 border-blue-500 text-blue-900 font-bold' : 'text-slate-600'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* Lesion Type */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Lesion Character</span>
                  {[
                    { val: 1, label: 'Blastic (1 pt)' },
                    { val: 2, label: 'Mixed (2 pts)' },
                    { val: 3, label: 'Lytic (3 pts)' },
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      onClick={() => setMirelsLesion(opt.val)}
                      className={`w-full p-2 rounded text-xs text-left border transition ${
                        mirelsLesion === opt.val ? 'bg-blue-50 border-blue-500 text-blue-900 font-bold' : 'text-slate-600'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* Cortical Size */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Cortical Width Involvement</span>
                  {[
                    { val: 1, label: '< 1/3 Cortex (1 pt)' },
                    { val: 2, label: '1/3 to 2/3 Cortex (2 pts)' },
                    { val: 3, label: '> 2/3 Cortex (3 pts)' },
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      onClick={() => setMirelsSize(opt.val)}
                      className={`w-full p-2 rounded text-xs text-left border transition ${
                        mirelsSize === opt.val ? 'bg-blue-50 border-blue-500 text-blue-900 font-bold' : 'text-slate-600'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Mirels Output Banner */}
              <div
                className="p-4 rounded-xl border-2"
                style={{
                  backgroundColor: mirelsClassification.bg,
                  borderColor: mirelsClassification.color,
                }}
              >
                <div className="flex items-center gap-2 font-bold text-xs uppercase tracking-wider mb-1" style={{ color: mirelsClassification.color }}>
                  <IconShieldAlert className="w-4 h-4" />
                  <span>{mirelsClassification.category}</span>
                </div>
                <p className="text-xs text-slate-800 font-medium">
                  {mirelsClassification.advice}
                </p>
              </div>
            </div>
          )}

          {/* TAB 4: HISTORY */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Metastasis Evaluation & Treatment Records</h3>
                <span className="text-xs text-slate-500">Multidisciplinary Spine Board</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date</th>
                      <th className="p-2.5">Segment</th>
                      <th className="p-2.5">SINS</th>
                      <th className="p-2.5">Stability Status</th>
                      <th className="p-2.5">Action Taken</th>
                      <th className="p-2.5">Clinician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-indigo-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-bold">{selectedVertebra}</td>
                      <td className="p-2.5 font-extrabold text-indigo-700">{totalSinsScore}/18</td>
                      <td className="p-2.5 font-bold" style={{ color: sinsClassification.color }}>{sinsClassification.category}</td>
                      <td className="p-2.5">{selectedFractionation === '8gy_1fx' ? '8 Gy in 1 fx scheduled' : 'Multifraction RT'}</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">02 Sep 2026</td>
                      <td className="p-2.5">Right Proximal Femur</td>
                      <td className="p-2.5 text-slate-400">N/A (Mirels: 10)</td>
                      <td className="p-2.5 font-bold text-rose-700">High Fracture Risk</td>
                      <td className="p-2.5">Prophylactic Cephalomedullary Nail placed</td>
                      <td className="p-2.5 text-slate-600">Dr Anita (Ortho Oncology)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">18 Aug 2026</td>
                      <td className="p-2.5">T4 Thoracic</td>
                      <td className="p-2.5 font-bold text-emerald-700">5/18 (Stable)</td>
                      <td className="p-2.5 text-emerald-800 font-semibold">Stable Spine</td>
                      <td className="p-2.5">8 Gy Single-Fraction RT delivered</td>
                      <td className="p-2.5 text-slate-600">Kidwai Radiation Oncology</td>
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
              onClick={handleCopyReferral}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-xs"
            >
              {copiedToast ? '✓ Copied SINS Referral Note!' : '📋 Copy SINS Spine & RT Consultation'}
            </button>
            <button
              onClick={handleDispatchReferral}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched STAT Referral!' : '🚨 Dispatch STAT Spine Surgery / RT Referral'}</span>
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
