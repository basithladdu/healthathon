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

export type WoundSite = 'breast' | 'head_neck' | 'axilla_groin' | 'stoma_fistula' | 'perineal_sacral';
export type TissueBedType = 'exophytic_granulation' | 'necrotic_black' | 'sloughy_fibrinous' | 'ulcerated_cavity';
export type OdorGrade = 0 | 1 | 2 | 3;
export type ExudateLevel = 'minimal_dry' | 'moderate' | 'heavy_copious';
export type BleedingTier = 0 | 1 | 2 | 3;

export interface MalignantWoundState {
  site: WoundSite;
  tissueBed: TissueBedType;
  odorGrade: OdorGrade;
  exudateLevel: ExudateLevel;
  bleedingTier: BleedingTier;
  lengthCm: number;
  widthCm: number;
  depthCm: number;
  periwoundSkin: 'intact' | 'erythematous' | 'macerated' | 'excoriated';
  premedicationAdministered: boolean;
}

interface MalignantWoundCareModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchKit?: (summary: string) => void;
}

export function MalignantWoundCareModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchKit,
}: MalignantWoundCareModalProps) {
  const [activeTab, setActiveTab] = useState<'assessment' | 'protocol' | 'asha_guide' | 'log'>('assessment');
  const [site, setSite] = useState<WoundSite>('breast');
  const [tissueBed, setTissueBed] = useState<TissueBedType>('exophytic_granulation');
  const [odorGrade, setOdorGrade] = useState<OdorGrade>(2);
  const [exudateLevel, setExudateLevel] = useState<ExudateLevel>('heavy_copious');
  const [bleedingTier, setBleedingTier] = useState<BleedingTier>(1);
  const [lengthCm, setLengthCm] = useState<number>(8);
  const [widthCm, setWidthCm] = useState<number>(6);
  const [depthCm, setDepthCm] = useState<number>(2.5);
  const [periwoundSkin, setPeriwoundSkin] = useState<'intact' | 'erythematous' | 'macerated' | 'excoriated'>('macerated');
  const [premedicationAdministered, setPremedicationAdministered] = useState<boolean>(true);
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // Clinical Recommendations Engine based on T.I.M.E. & EAPC Palliative Wound Consensus
  const recommendations = useMemo(() => {
    // Primary Layer
    let primaryLayer = '';
    let primaryRational = '';
    if (bleedingTier >= 2) {
      primaryLayer = 'Tranexamic Acid (TXA 500mg/5mL) soaked gauze with calcium alginate hemostatic mesh';
      primaryRational = 'Topical antifibrinolytic stabilizes friable capillary clots without systemic hypercoagulability risk.';
    } else if (bleedingTier === 1) {
      primaryLayer = 'Calcium Alginate hemostatic sheet (Kaltostat / Algisite M)';
      primaryRational = 'Forms gentle hydrophilic sodium-calcium gel on contact, promoting capillary hemostasis and non-traumatic removal.';
    } else if (exudateLevel === 'minimal_dry') {
      primaryLayer = 'Silicone contact layer (Mepitel) coated with 2% Lidocaine gel or amorphous hydrogel';
      primaryRational = 'Prevents dressing adherence to exposed nerve fibers, reducing incident pain during dressing changes.';
    } else {
      primaryLayer = 'Non-adherent porous silicone contact layer (Mepitel / Atrauman Silicone)';
      primaryRational = 'Allows exudate to pass through freely to secondary absorbent layer while preventing mechanical shear to fragile tumor.';
    }

    // Secondary Layer (Bioburden & Odor)
    let secondaryOdor = '';
    let secondaryAbsorption = '';
    if (odorGrade >= 2) {
      secondaryOdor = 'Topical Metronidazole 0.8% gel (or 500mg crushed tabs suspended in normal saline) + Activated Charcoal dressing with silver lining';
      secondaryAbsorption = 'Charcoal cloth adsorbs volatile fatty acid putrescine/cadaverine gas; metronidazole eradicates anaerobic Bacteroides.';
    } else if (odorGrade === 1) {
      secondaryOdor = 'Topical Metronidazole 0.8% gel applied directly to sloughy areas';
      secondaryAbsorption = 'Suppresses early anaerobic colonization before malodor causes social distress.';
    } else {
      secondaryOdor = 'Standard antimicrobial cadexomer iodine or medical-grade honey sheet';
      secondaryAbsorption = 'Maintains low bioburden without masking.';
    }

    // Secondary Absorption Layer
    let absorptionLayer = '';
    if (exudateLevel === 'heavy_copious') {
      absorptionLayer = 'Superabsorbent Polyacrylate Polymer dressing (Zetuvit Plus / DryMax Extra)';
    } else if (exudateLevel === 'moderate') {
      absorptionLayer = 'Hydrofiber (Aquacel Extra) or High-capacity Polyurethane Foam';
    } else {
      absorptionLayer = 'Sterile multi-layered absorbent cotton gauze pad';
    }

    // Tertiary Layer & Periwound Care
    let periwoundCare = '';
    if (periwoundSkin === 'macerated') {
      periwoundCare = 'Apply No-Sting Cavilon Barrier Film (3M) + Zinc Oxide 40% barrier paste in a 3cm periwound halo margin.';
    } else if (periwoundSkin === 'erythematous') {
      periwoundCare = 'Apply silicone-based moisturizing barrier cream. Avoid alcohol-containing preps.';
    } else if (periwoundSkin === 'excoriated') {
      periwoundCare = 'Hydrocolloid thin wafer border to seal fragile excoriated skin from enzymatic wound fluid.';
    } else {
      periwoundCare = 'Non-stinging silicone barrier film wipe prior to tape application.';
    }

    // Premedication Analgesia
    const premedicationAdvice = 'Immediate-release oral Morphine 5–10 mg (or SC Morphine 2.5–5 mg) administered 30–45 minutes PRIOR to dressing manipulation. Irrigate exclusively with warm 37°C sterile Normal Saline (avoid cold fluids which trigger vascular spasm and shivering pain).';

    return {
      primaryLayer,
      primaryRational,
      secondaryOdor,
      secondaryAbsorption,
      absorptionLayer,
      periwoundCare,
      premedicationAdvice,
    };
  }, [bleedingTier, exudateLevel, odorGrade, periwoundSkin]);

  // Overall Severity Index
  const severityScore = useMemo(() => {
    let score = 0;
    if (tissueBed === 'exophytic_granulation') score += 3;
    if (tissueBed === 'necrotic_black') score += 3;
    if (tissueBed === 'sloughy_fibrinous') score += 2;
    if (tissueBed === 'ulcerated_cavity') score += 3;
    score += odorGrade * 2;
    if (exudateLevel === 'heavy_copious') score += 3;
    else if (exudateLevel === 'moderate') score += 2;
    score += bleedingTier * 3;
    return score; // Max around 18
  }, [tissueBed, odorGrade, exudateLevel, bleedingTier]);

  const severityTier = useMemo(() => {
    if (severityScore >= 13) return { label: 'CRITICAL HIGH-COMPLEXITY WOUND', color: '#be123c', bg: '#ffe4e6' };
    if (severityScore >= 8) return { label: 'MODERATE MULTISYMPTOM WOUND', color: '#c2410c', bg: '#ffedd5' };
    return { label: 'STABLE CONTROLLED WOUND', color: '#047857', bg: '#d1fae5' };
  }, [severityScore]);

  const handleCopyProtocol = () => {
    const text = `MALIGNANT WOUND & FUNGATING TUMOR MANAGEMENT PROTOCOL
Patient: ${patientName} (${patientId}) - ${primaryCancer}
Site: ${site.toUpperCase()} | Dimensions: ${lengthCm}cm x ${widthCm}cm x ${depthCm}cm
Tissue Bed: ${tissueBed.replace('_', ' ').toUpperCase()} | Odor Grade: ${odorGrade}/3 | Exudate: ${exudateLevel.toUpperCase()}
Hemorrhage Risk: Tier ${bleedingTier}/3 | Periwound Skin: ${periwoundSkin.toUpperCase()}
--------------------------------------------------
RECOMMENDED 3-LAYER DRESSING PROTOCOL:
1. Primary Contact: ${recommendations.primaryLayer}
   RATIONALE: ${recommendations.primaryRational}
2. Secondary Odor & Absorption: ${recommendations.secondaryOdor}
   ABSORPTION: ${recommendations.absorptionLayer}
3. Periwound & Skin Protection: ${recommendations.periwoundCare}
4. Analgesia Premedication: ${recommendations.premedicationAdvice}
--------------------------------------------------
EMERGENCY HEMORRHAGE INSTRUCTION:
In event of frank arterial or heavy tumor bleed: Apply Tranexamic Acid soaked gauze with firm continuous manual pressure for 15 mins. Use dark green or black towels to reduce visual anxiety. DO NOT lift gauze to peek. Call Palliative On-Call.`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchKit = () => {
    setDispatchedToast(true);
    if (onDispatchKit) {
      onDispatchKit(
        `Wound Care Kit Dispatched: ${site.toUpperCase()} (${lengthCm}x${widthCm}cm, Odor Gr.${odorGrade}, Exudate ${exudateLevel})`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="wound-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-rose-950 via-slate-900 to-amber-950 text-white flex flex-wrap items-center justify-between gap-3 border-b border-rose-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-rose-500/20 border border-rose-400/40 flex items-center justify-center text-rose-300">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="wound-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Malignant Fungating Wound & Stoma Management
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-rose-500/20 text-rose-200 border border-rose-400/30">
                  T.I.M.E. Oncology Suite
                </span>
              </div>
              <p className="text-xs text-rose-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: severityTier.bg,
                color: severityTier.color,
                borderColor: severityTier.color,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>{severityTier.label}</span>
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
                ? 'border-rose-600 text-rose-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🔬 T.I.M.E. Clinical Assessment</span>
          </button>
          <button
            onClick={() => setActiveTab('protocol')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'protocol'
                ? 'border-rose-600 text-rose-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🩹 Evidence-Based Dressing Protocol</span>
          </button>
          <button
            onClick={() => setActiveTab('asha_guide')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'asha_guide'
                ? 'border-rose-600 text-rose-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🇮🇳 Bilingual ASHA / Home Guide</span>
          </button>
          <button
            onClick={() => setActiveTab('log')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'log'
                ? 'border-rose-600 text-rose-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Longitudinal Wound Tracker</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: ASSESSMENT */}
          {activeTab === 'assessment' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: T.I.M.E. Selectors */}
              <div className="lg:col-span-7 space-y-5">
                {/* Site & Dimensions */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-700">1. Anatomical Location & Measurements</span>
                    <span className="text-[11px] font-semibold text-rose-700">Surface Area: {(lengthCm * widthCm).toFixed(1)} cm²</span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-3">
                    {[
                      { id: 'breast', label: 'Breast / Chest Wall' },
                      { id: 'head_neck', label: 'Head & Neck / Cervical' },
                      { id: 'axilla_groin', label: 'Axilla / Inguinal Nodes' },
                      { id: 'stoma_fistula', label: 'Peristomal / Fistula' },
                      { id: 'perineal_sacral', label: 'Perineal / Sacral' },
                    ].map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setSite(item.id as WoundSite)}
                        className={`p-2 rounded-lg text-xs font-semibold border text-left transition ${
                          site === item.id
                            ? 'bg-rose-50 border-rose-500 text-rose-900 ring-2 ring-rose-200'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>

                  <div className="grid grid-cols-3 gap-3 pt-2 border-t border-slate-200 text-xs">
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Length: <span className="font-bold text-slate-900">{lengthCm} cm</span></label>
                      <input
                        type="range"
                        min="1"
                        max="25"
                        step="0.5"
                        value={lengthCm}
                        onChange={(e) => setLengthCm(parseFloat(e.target.value))}
                        className="w-full accent-rose-600"
                      />
                    </div>
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Width: <span className="font-bold text-slate-900">{widthCm} cm</span></label>
                      <input
                        type="range"
                        min="1"
                        max="25"
                        step="0.5"
                        value={widthCm}
                        onChange={(e) => setWidthCm(parseFloat(e.target.value))}
                        className="w-full accent-rose-600"
                      />
                    </div>
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Depth: <span className="font-bold text-slate-900">{depthCm} cm</span></label>
                      <input
                        type="range"
                        min="0"
                        max="10"
                        step="0.5"
                        value={depthCm}
                        onChange={(e) => setDepthCm(parseFloat(e.target.value))}
                        className="w-full accent-rose-600"
                      />
                    </div>
                  </div>
                </div>

                {/* T: Tissue Bed */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="w-5 h-5 rounded-full bg-rose-600 text-white text-xs font-bold flex items-center justify-center">T</span>
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">Tissue Bed Morphology</span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {[
                      { id: 'exophytic_granulation', title: 'Exophytic Fungation', desc: 'Cauliflower / nodular friable vascular tumor mass' },
                      { id: 'necrotic_black', title: 'Necrotic Black Eschar', desc: 'Avascular necrotic core with anaerobic colonization' },
                      { id: 'sloughy_fibrinous', title: 'Sloughy Fibrinous Bed', desc: 'Viscous yellow exudative debris, non-viable matrix' },
                      { id: 'ulcerated_cavity', title: 'Ulcerated Deep Cavity', desc: 'Crater with tunneling sinus tracts / undermining' },
                    ].map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setTissueBed(item.id as TissueBedType)}
                        className={`p-2.5 rounded-lg text-left border transition ${
                          tissueBed === item.id
                            ? 'bg-rose-50 border-rose-500 text-rose-950 ring-2 ring-rose-200'
                            : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.title}</div>
                        <div className="text-[11px] text-slate-500">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* I: Infection & Malodor */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="w-5 h-5 rounded-full bg-amber-600 text-white text-xs font-bold flex items-center justify-center">I</span>
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-800">Infection & Malodor Severity</span>
                    </div>
                    <span className="text-xs font-extrabold text-amber-700">Grade {odorGrade} of 3</span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {[
                      { grade: 0, label: '0: None', desc: 'No perceptible odor' },
                      { grade: 1, label: '1: Faint', desc: 'At dressing removal (<1m)' },
                      { grade: 2, label: '2: Moderate', desc: 'Noticed upon entering room' },
                      { grade: 3, label: '3: Severe / Putrid', desc: 'Evident outside room / hall' },
                    ].map((item) => (
                      <button
                        key={item.grade}
                        type="button"
                        onClick={() => setOdorGrade(item.grade as OdorGrade)}
                        className={`p-2 rounded-lg text-left border transition ${
                          odorGrade === item.grade
                            ? 'bg-amber-50 border-amber-500 text-amber-950 ring-2 ring-amber-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 text-xs'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* M: Moisture & Exudate */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="w-5 h-5 rounded-full bg-cyan-600 text-white text-xs font-bold flex items-center justify-center">M</span>
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">Moisture & Exudate Balance</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { id: 'minimal_dry', label: 'Minimal / Dry', desc: 'Crusted, dressing adheres' },
                      { id: 'moderate', label: 'Moderate', desc: '1–2 pads soaked / 24h' },
                      { id: 'heavy_copious', label: 'Heavy Copious', desc: '>3 pads/day, maceration' },
                    ].map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setExudateLevel(item.id as ExudateLevel)}
                        className={`p-2.5 rounded-lg text-left border transition ${
                          exudateLevel === item.id
                            ? 'bg-cyan-50 border-cyan-500 text-cyan-950 ring-2 ring-cyan-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 text-xs'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[11px] text-slate-500">{item.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* E: Edge & Hemorrhage Risk */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="w-5 h-5 rounded-full bg-red-600 text-white text-xs font-bold flex items-center justify-center">E</span>
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-800">Edge & Hemorrhage Risk</span>
                    </div>
                    {bleedingTier >= 2 && (
                      <span className="px-2 py-0.5 bg-red-100 text-red-800 text-[10px] font-bold rounded-full animate-pulse">
                        ⚠️ HEMORRHAGE PROTOCOL ACTIVE
                      </span>
                    )}
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {[
                      { tier: 0, label: 'Tier 0: None', desc: 'Stable non-friable edge' },
                      { tier: 1, label: 'Tier 1: Contact Ooze', desc: 'Bleeds with minor touch' },
                      { tier: 2, label: 'Tier 2: Spontaneous', desc: 'Persistent venous ooze' },
                      { tier: 3, label: 'Tier 3: Arterial Crisis', desc: 'Carotid / Major vessel risk' },
                    ].map((item) => (
                      <button
                        key={item.tier}
                        type="button"
                        onClick={() => setBleedingTier(item.tier as BleedingTier)}
                        className={`p-2 rounded-lg text-left border transition ${
                          bleedingTier === item.tier
                            ? 'bg-red-50 border-red-500 text-red-950 ring-2 ring-red-200 font-bold'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 text-xs'
                        }`}
                      >
                        <div className="text-xs font-bold">{item.label}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{item.desc}</div>
                      </button>
                    ))}
                  </div>

                  {/* Periwound selection */}
                  <div className="mt-3 pt-3 border-t border-slate-200 flex flex-wrap items-center justify-between gap-2">
                    <span className="text-xs text-slate-600 font-medium">Periwound Skin:</span>
                    <div className="flex gap-1.5">
                      {[
                        { id: 'intact', label: 'Intact' },
                        { id: 'erythematous', label: 'Red / Inflamed' },
                        { id: 'macerated', label: 'Macerated (White)' },
                        { id: 'excoriated', label: 'Excoriated / Denuded' },
                      ].map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => setPeriwoundSkin(item.id as any)}
                          className={`px-2 py-1 rounded text-[11px] font-semibold border transition ${
                            periwoundSkin === item.id
                              ? 'bg-purple-50 border-purple-500 text-purple-900 font-bold'
                              : 'bg-white border-slate-200 text-slate-600'
                          }`}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Column: Visual Interactive Wound Canvas & Snapshot */}
              <div className="lg:col-span-5 space-y-4">
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner flex flex-col items-center">
                  <div className="w-full flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span className="font-semibold text-slate-200">Interactive Wound Topography</span>
                    <span>Scale: 1:1 Computed</span>
                  </div>

                  {/* SVG Canvas */}
                  <div className="relative w-full max-w-[320px] aspect-square flex items-center justify-center p-2">
                    <svg
                      viewBox="0 0 200 200"
                      className="w-full h-full drop-shadow-md"
                    >
                      {/* Background periwound margin */}
                      <circle
                        cx="100"
                        cy="100"
                        r={Math.min(90, 45 + lengthCm * 1.8)}
                        fill={
                          periwoundSkin === 'macerated'
                            ? '#f1f5f9'
                            : periwoundSkin === 'erythematous'
                            ? '#fecdd3'
                            : periwoundSkin === 'excoriated'
                            ? '#fca5a5'
                            : '#fed7aa'
                        }
                        stroke={periwoundSkin === 'macerated' ? '#94a3b8' : '#e11d48'}
                        strokeWidth="2"
                        strokeDasharray={periwoundSkin === 'macerated' ? '4,3' : undefined}
                      />

                      {/* Odor Aura Rings if Grade >= 1 */}
                      {odorGrade >= 1 && (
                        <circle
                          cx="100"
                          cy="100"
                          r={Math.min(98, 55 + lengthCm * 1.8 + odorGrade * 6)}
                          fill="none"
                          stroke="#d97706"
                          strokeWidth="1.5"
                          strokeDasharray="6,4"
                          opacity={0.3 * odorGrade}
                          className="animate-spin"
                          style={{ transformOrigin: 'center', animationDuration: '18s' }}
                        />
                      )}
                      {odorGrade >= 2 && (
                        <circle
                          cx="100"
                          cy="100"
                          r={Math.min(99, 62 + lengthCm * 1.8 + odorGrade * 8)}
                          fill="none"
                          stroke="#b45309"
                          strokeWidth="1"
                          strokeDasharray="4,6"
                          opacity={0.4}
                          className="animate-spin"
                          style={{ transformOrigin: 'center', animationDuration: '24s' }}
                        />
                      )}

                      {/* Tumor Base / Tissue Bed */}
                      {tissueBed === 'exophytic_granulation' && (
                        <g>
                          {/* Cauliflower lobules */}
                          <circle cx="90" cy="90" r={Math.min(45, 15 + lengthCm * 1.2)} fill="#be123c" opacity="0.9" />
                          <circle cx="115" cy="95" r={Math.min(40, 14 + widthCm * 1.1)} fill="#9f1239" opacity="0.9" />
                          <circle cx="100" cy="115" r={Math.min(42, 16 + lengthCm * 1.1)} fill="#e11d48" opacity="0.85" />
                          <circle cx="82" cy="110" r={Math.min(30, 10 + widthCm * 1.0)} fill="#881337" opacity="0.8" />
                          <circle cx="102" cy="98" r={Math.min(25, 8 + depthCm * 1.5)} fill="#f43f5e" opacity="0.9" />
                        </g>
                      )}

                      {tissueBed === 'necrotic_black' && (
                        <g>
                          {/* Black necrotic eschar center */}
                          <ellipse
                            cx="100"
                            cy="100"
                            rx={Math.min(50, 20 + lengthCm * 1.2)}
                            ry={Math.min(45, 18 + widthCm * 1.1)}
                            fill="#1e293b"
                          />
                          <circle cx="100" cy="100" r={Math.min(30, 12 + lengthCm * 0.8)} fill="#0f172a" />
                          <path
                            d="M 85 90 Q 100 80 115 90 T 110 115 T 85 105 Z"
                            fill="#020617"
                            opacity="0.8"
                          />
                          {/* Slough ring margin */}
                          <ellipse
                            cx="100"
                            cy="100"
                            rx={Math.min(55, 23 + lengthCm * 1.3)}
                            ry={Math.min(48, 20 + widthCm * 1.2)}
                            fill="none"
                            stroke="#eab308"
                            strokeWidth="4"
                            opacity="0.7"
                          />
                        </g>
                      )}

                      {tissueBed === 'sloughy_fibrinous' && (
                        <g>
                          <ellipse
                            cx="100"
                            cy="100"
                            rx={Math.min(52, 22 + lengthCm * 1.2)}
                            ry={Math.min(45, 18 + widthCm * 1.1)}
                            fill="#ca8a04"
                            opacity="0.85"
                          />
                          <circle cx="95" cy="95" r={Math.min(28, 12 + lengthCm * 0.7)} fill="#facc15" opacity="0.9" />
                          <circle cx="112" cy="108" r={Math.min(22, 10 + widthCm * 0.7)} fill="#eab308" opacity="0.95" />
                        </g>
                      )}

                      {tissueBed === 'ulcerated_cavity' && (
                        <g>
                          <ellipse
                            cx="100"
                            cy="100"
                            rx={Math.min(55, 24 + lengthCm * 1.2)}
                            ry={Math.min(48, 20 + widthCm * 1.1)}
                            fill="#475569"
                          />
                          {/* Deep crater shadow */}
                          <ellipse
                            cx="100"
                            cy="100"
                            rx={Math.min(40, 15 + lengthCm * 0.9)}
                            ry={Math.min(32, 12 + widthCm * 0.8)}
                            fill="#0f172a"
                          />
                          <circle cx="100" cy="100" r={Math.min(20, 8 + depthCm * 1.8)} fill="#020617" />
                        </g>
                      )}

                      {/* Exudate droplets if Moderate or Heavy */}
                      {exudateLevel !== 'minimal_dry' && (
                        <g fill="#38bdf8" opacity="0.85">
                          <circle cx="78" cy="85" r="3" />
                          <circle cx="120" cy="90" r="3.5" />
                          <circle cx="105" cy="125" r="4" />
                          {exudateLevel === 'heavy_copious' && (
                            <>
                              <circle cx="85" cy="120" r="4.5" />
                              <circle cx="125" cy="115" r="4" />
                              <circle cx="95" cy="75" r="3.5" />
                              <path d="M 100 120 C 100 130 95 135 95 140 C 95 143 100 145 105 140 C 105 135 100 130 100 120 Z" fill="#0284c7" />
                            </>
                          )}
                        </g>
                      )}

                      {/* Bleeding Indicator */}
                      {bleedingTier >= 1 && (
                        <g>
                          <circle cx="95" cy="105" r="4" fill="#dc2626" />
                          <circle cx="110" cy="100" r="3" fill="#ef4444" />
                          {bleedingTier >= 2 && (
                            <>
                              <circle cx="85" cy="98" r="5" fill="#991b1b" />
                              <circle cx="105" cy="115" r="4.5" fill="#b91c1c" />
                              <path d="M 95 105 Q 92 120 90 128" stroke="#dc2626" strokeWidth="2.5" fill="none" strokeLinecap="round" />
                            </>
                          )}
                        </g>
                      )}
                    </svg>
                  </div>

                  <div className="w-full bg-slate-800/80 rounded-lg p-2.5 mt-2 text-xs space-y-1">
                    <div className="flex justify-between text-slate-300">
                      <span>Lesion Dimensions:</span>
                      <span className="font-bold text-white">{lengthCm} × {widthCm} × {depthCm} cm</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Odor Rating:</span>
                      <span className="font-bold text-amber-300">Grade {odorGrade}/3 ({odorGrade === 0 ? 'None' : odorGrade === 1 ? 'Faint' : odorGrade === 2 ? 'Moderate' : 'Severe'})</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Exudate Load:</span>
                      <span className="font-bold text-cyan-300">{exudateLevel.replace('_', ' ').toUpperCase()}</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Bleeding Tier:</span>
                      <span className="font-bold text-rose-400">Tier {bleedingTier}/3 ({bleedingTier === 0 ? 'None' : bleedingTier === 1 ? 'Capillary Ooze' : bleedingTier === 2 ? 'Persistent Venous' : 'Arterial Risk'})</span>
                    </div>
                  </div>
                </div>

                {/* Premedication Checklist */}
                <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200">
                  <div className="flex items-center gap-2 mb-1.5">
                    <IconClock className="w-4 h-4 text-amber-700" />
                    <span className="text-xs font-bold text-amber-900">Pre-Dressing Analgesia Verification</span>
                  </div>
                  <p className="text-xs text-amber-800 mb-2">
                    Malignant dressing changes trigger intense incident procedural pain. Has pre-medication been provided?
                  </p>
                  <label className="flex items-center gap-2 text-xs font-bold text-amber-950 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={premedicationAdministered}
                      onChange={(e) => setPremedicationAdministered(e.target.checked)}
                      className="w-4 h-4 text-amber-600 rounded border-amber-300 focus:ring-amber-500"
                    />
                    <span>Morphine / Breakthrough Analgesia given 30–45 mins prior</span>
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: PROTOCOL */}
          {activeTab === 'protocol' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-gradient-to-r from-rose-50 to-amber-50 border border-rose-200 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-rose-600 text-white mt-0.5">
                  <IconSparkles className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-rose-950">Tailored 3-Layer Oncologic Dressing Prescription</h3>
                  <p className="text-xs text-rose-800 mt-0.5">
                    Generated dynamically according to EAPC Palliative Consensus & Toronto Symptom Assessment Tool (TSAT-Wound).
                  </p>
                </div>
              </div>

              {/* Layer 1 */}
              <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-rose-400 transition shadow-xs space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-rose-100 text-rose-800">LAYER 1</span>
                    <span className="text-sm font-bold text-slate-900">Primary Wound Contact Layer</span>
                  </div>
                  <span className="text-xs font-semibold text-slate-500">Direct Tissue Interface</span>
                </div>
                <div className="p-3 bg-rose-50/70 rounded-lg text-xs font-bold text-rose-950 border border-rose-100">
                  {recommendations.primaryLayer}
                </div>
                <p className="text-xs text-slate-600">
                  <span className="font-semibold text-slate-800">Clinical Rationale:</span> {recommendations.primaryRational}
                </p>
                <div className="text-[11px] text-amber-800 bg-amber-50 p-2 rounded border border-amber-200">
                  ⚠️ <span className="font-bold">Crucial Rule:</span> NEVER forcibly rip dry gauze from a fungating tumor bed. Moisten thoroughly with warm 0.9% Normal Saline for 5 minutes before removal.
                </div>
              </div>

              {/* Layer 2 */}
              <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-amber-400 transition shadow-xs space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-amber-100 text-amber-800">LAYER 2</span>
                    <span className="text-sm font-bold text-slate-900">Bioburden Suppression & Odor Adsorption</span>
                  </div>
                  <span className="text-xs font-semibold text-slate-500">Intermediate Active Layer</span>
                </div>
                <div className="p-3 bg-amber-50/70 rounded-lg text-xs font-bold text-amber-950 border border-amber-100">
                  {recommendations.secondaryOdor}
                </div>
                <p className="text-xs text-slate-600">
                  <span className="font-semibold text-slate-800">Absorption Matrix:</span> {recommendations.absorptionLayer} ({recommendations.secondaryAbsorption})
                </p>
                <div className="text-[11px] text-slate-700 bg-slate-50 p-2 rounded border border-slate-200">
                  💡 <span className="font-bold">Handling Charcoal Dressing:</span> Never cut activated charcoal pads; loose carbon particles tattoo the tissue and induce foreign body reaction.
                </div>
              </div>

              {/* Layer 3 */}
              <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-purple-400 transition shadow-xs space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-purple-100 text-purple-800">LAYER 3</span>
                    <span className="text-sm font-bold text-slate-900">Periwound Protection & Low-Trauma Fixation</span>
                  </div>
                  <span className="text-xs font-semibold text-slate-500">Outer Retention Layer</span>
                </div>
                <div className="p-3 bg-purple-50/70 rounded-lg text-xs font-bold text-purple-950 border border-purple-100">
                  {recommendations.periwoundCare}
                </div>
                <p className="text-xs text-slate-600">
                  <span className="font-semibold text-slate-800">Retention Strategy:</span> Use soft conforming tubular net bandage (Tubifast) or wide silicone tape border (Mepitac). Avoid strong zinc oxide adhesive tapes which strip cancer cachectic skin.
                </p>
              </div>

              {/* Red Towel / Carotid Catastrophe Box */}
              {bleedingTier >= 2 && (
                <div className="p-4 rounded-xl bg-red-950 text-white border-2 border-red-600 shadow-md space-y-2">
                  <div className="flex items-center gap-2 text-red-300 font-bold text-xs uppercase tracking-wider">
                    <IconShieldAlert className="w-4 h-4 text-red-400" />
                    <span>Palliative Hemorrhage & "Dark Towels" Crisis Protocol</span>
                  </div>
                  <p className="text-xs text-red-100 leading-relaxed">
                    In the event of rapid tumor hemorrhage:
                  </p>
                  <ul className="text-xs text-red-200 list-disc list-inside space-y-1">
                    <li><strong className="text-white">Dark / Green Towels:</strong> Use dark-colored towels to absorb blood. White towels create extreme visual horror and hysteria for family and patient.</li>
                    <li><strong className="text-white">Topical Tranexamic Acid (TXA):</strong> Saturate sterile gauze with 500mg/5mL injectable TXA and press firmly for 15 unbroken minutes.</li>
                    <li><strong className="text-white">Crisis Sedation:</strong> If catastrophic arterial rupture (carotid/femoral) occurs and patient is in acute distress, administer crisis Midazolam 5–10 mg SC/IM immediately as per Advance Care Directive.</li>
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: BILINGUAL ASHA GUIDE */}
          {activeTab === 'asha_guide' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                <div className="flex items-center gap-2 mb-1">
                  <IconUsers className="w-5 h-5 text-blue-700" />
                  <h3 className="text-sm font-bold text-blue-950">
                    Bilingual Community ASHA / Home Health Worker Protocol (Hindi & English)
                  </h3>
                </div>
                <p className="text-xs text-blue-800">
                  Simple, unambiguous directives designed for home visits, rural sub-centers, and primary health workers.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Hindi Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="text-xs font-bold text-slate-800">🇮🇳 आशा व गृह परिचारिका के लिए मुख्य निर्देश (Hindi)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">हिंदी</span>
                  </div>

                  <div className="space-y-2.5 text-xs text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">१. दर्द की दवा पहले दें (Pain First):</div>
                      <p className="text-[11px] text-emerald-800">
                        पट्टी बदलने से ३० से ४० मिनट पहले मरीज को डॉक्टर द्वारा लिखी दर्द की दवा (मॉर्फिन) जरूर दें।
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">२. जबरदस्ती पट्टी न खींचें (No Force):</div>
                      <p className="text-[11px] text-rose-800">
                        सूखी पट्टी को कभी भी जोर से न खींचें। गुनगुने नॉर्मल सलाइन से ५ मिनट तक अच्छी तरह भिगोएँ, ताकि खून न बहे।
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">३. बदबू कम करने का उपाय (Odor Control):</div>
                      <p className="text-[11px] text-amber-800">
                        मेट्रोनिडाजोल जेल सीधे घाव पर लगाएं। एक्टिवेटेड चारकोल पट्टी ऊपर रखें। चारकोल पट्टी को कभी कैंची से न काटें।
                      </p>
                    </div>

                    <div className="p-2.5 bg-red-50 rounded-lg border border-red-200">
                      <div className="font-bold text-red-900 mb-0.5">४. खून बहने पर क्या करें (Bleeding Emergency):</div>
                      <p className="text-[11px] text-red-800">
                        यदि खून बहने लगे तो ट्रैनेक्सामिक एसिड (TXA) में भीगी गॉज रखकर १५ मिनट तक हाथ से दबाकर रखें। सफेद तौलिये के बदले गहरे रंग (गहरा हरा/काला) का कपड़ा इस्तेमाल करें।
                      </p>
                    </div>
                  </div>
                </div>

                {/* English Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="text-xs font-bold text-slate-800">🇬🇧 Community Nurse / Caregiver Steps (English)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">English</span>
                  </div>

                  <div className="space-y-2.5 text-xs text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">1. Pre-medicate for Incident Pain:</div>
                      <p className="text-[11px] text-emerald-800">
                        Administer prescribed oral/SC opioid 30–45 mins prior to touching the dressing.
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">2. Soak Before Peeling:</div>
                      <p className="text-[11px] text-rose-800">
                        Flood adhering dressings with body-warm 0.9% saline. Allow 5 minutes of soaking to prevent capillary avulsion.
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">3. Topical Metronidazole & Charcoal:</div>
                      <p className="text-[11px] text-amber-800">
                        Apply Metronidazole 0.8% gel directly over necrotic tissue. Cover with intact activated charcoal dressing.
                      </p>
                    </div>

                    <div className="p-2.5 bg-red-50 rounded-lg border border-red-200">
                      <div className="font-bold text-red-900 mb-0.5">4. Hemostasis Protocol:</div>
                      <p className="text-[11px] text-red-800">
                        Apply TXA-soaked gauze with unbroken firm manual pressure for 15 minutes. Use dark-colored linens to decrease distress.
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: LONGITUDINAL LOG */}
          {activeTab === 'log' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Longitudinal Malignant Wound Records</h3>
                <span className="text-xs text-slate-500">Toronto Symptom Assessment Tool (TSAT-W)</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date / Time</th>
                      <th className="p-2.5">Location</th>
                      <th className="p-2.5">Size (L×W×D)</th>
                      <th className="p-2.5">Odor</th>
                      <th className="p-2.5">Exudate</th>
                      <th className="p-2.5">Bleed Risk</th>
                      <th className="p-2.5">Assessor</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-rose-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-medium">{site.toUpperCase()}</td>
                      <td className="p-2.5 font-medium">{lengthCm} × {widthCm} × {depthCm} cm</td>
                      <td className="p-2.5">
                        <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-100 text-amber-800">
                          Grade {odorGrade}/3
                        </span>
                      </td>
                      <td className="p-2.5 capitalize">{exudateLevel.replace('_', ' ')}</td>
                      <td className="p-2.5 font-bold text-rose-700">Tier {bleedingTier}</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">10 Sep 2026</td>
                      <td className="p-2.5">BREAST</td>
                      <td className="p-2.5">7.5 × 5.5 × 2.0 cm</td>
                      <td className="p-2.5">
                        <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-100 text-amber-800">
                          Grade 2/3
                        </span>
                      </td>
                      <td className="p-2.5">Moderate</td>
                      <td className="p-2.5 text-slate-700">Tier 1</td>
                      <td className="p-2.5 text-slate-600">Pushpa Gowda (Home Nurse)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">28 Aug 2026</td>
                      <td className="p-2.5">BREAST</td>
                      <td className="p-2.5">6.0 × 4.5 × 1.5 cm</td>
                      <td className="p-2.5">
                        <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-100 text-emerald-800">
                          Grade 1/3
                        </span>
                      </td>
                      <td className="p-2.5">Moderate</td>
                      <td className="p-2.5 text-slate-700">Tier 0</td>
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
              onClick={handleCopyProtocol}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-xs"
            >
              {copiedToast ? '✓ Copied Protocol to Clipboard!' : '📋 Copy Complete Wound Protocol'}
            </button>
            <button
              onClick={handleDispatchKit}
              className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched ASHA Kit!' : '🚑 Dispatch Home Wound Kit to ASHA'}</span>
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
