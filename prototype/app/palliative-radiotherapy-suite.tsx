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

export type RadiotherapyIndication =
  | 'bone_metastases'
  | 'spinal_cord_compression'
  | 'hemostatic_bleeding'
  | 'svco_obstruction'
  | 'brain_metastases';

export type FractionationScheme = 'single_8gy' | 'five_20gy' | 'ten_30gy' | 'stereotactic_srs';

interface PalliativeRadiotherapyModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function PalliativeRadiotherapyModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: PalliativeRadiotherapyModalProps) {
  const [activeTab, setActiveTab] = useState<'regimen_planner' | 'toxicity_flare' | 'beam_visualizer' | 'counseling' | 'history'>('regimen_planner');

  // Treatment Parameters
  const [indication, setIndication] = useState<RadiotherapyIndication>('bone_metastases');
  const [targetSite, setTargetSite] = useState<string>('L3–L4 Lumbar Spine & Sacrum');
  const [fractionScheme, setFractionScheme] = useState<FractionationScheme>('single_8gy');
  const [priorRadiationToSite, setPriorRadiationToSite] = useState<boolean>(false);
  const [priorDoseGy, setPriorDoseGy] = useState<number>(0);
  const [fractionsCompleted, setFractionsCompleted] = useState<number>(1);

  // Toxicity & Flare Profile
  const [hasPainFlare, setHasPainFlare] = useState<boolean>(true);
  const [dermatitisGrade, setDermatitisGrade] = useState<1 | 2 | 3>(1);
  const [nauseaGrade, setNauseaGrade] = useState<0 | 1 | 2>(1);

  // Notification Toast states
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // Dose and Fractionation Calculations
  const schemeDetails = useMemo(() => {
    switch (fractionScheme) {
      case 'single_8gy':
        return {
          totalDose: 8,
          fractions: 1,
          fractionDose: 8,
          bed10: 14.4, // 8 * (1 + 8/10)
          bed3: 29.3, // 8 * (1 + 8/3)
          eqd2_10: 12.0,
          eqd2_3: 17.6,
          label: '8 Gy in 1 Single Fraction (ASTRO Gold Standard)',
          badge: 'ASTRO / ESTRO Preferred',
          rationale: 'Equal pain response (70–75%) to multi-fraction regimens; maximum convenience for frail patients with minimal travel burden.',
        };
      case 'five_20gy':
        return {
          totalDose: 20,
          fractions: 5,
          fractionDose: 4,
          bed10: 28.0, // 20 * (1 + 4/10)
          bed3: 46.7, // 20 * (1 + 4/3)
          eqd2_10: 23.3,
          eqd2_3: 28.0,
          label: '20 Gy in 5 Fractions (4 Gy / fx)',
          badge: 'Standard Palliative Fractionation',
          rationale: 'Balanced tumoricidal cell kill with lower retreatment rate (8% vs 20% in single fraction).',
        };
      case 'ten_30gy':
        return {
          totalDose: 30,
          fractions: 10,
          fractionDose: 3,
          bed10: 39.0, // 30 * (1 + 3/10)
          bed3: 60.0, // 30 * (1 + 3/3)
          eqd2_10: 32.5,
          eqd2_3: 36.0,
          label: '30 Gy in 10 Fractions (3 Gy / fx)',
          badge: 'Protracted Course (>6m Prognosis)',
          rationale: 'Indicated for patients with favorable prognosis (e.g. solitary breast/prostate metastasis, good performance status).',
        };
      case 'stereotactic_srs':
        return {
          totalDose: 24,
          fractions: 2,
          fractionDose: 12,
          bed10: 52.8,
          bed3: 120.0,
          label: '24 Gy in 2 Fractions (Stereotactic SBRT / SRS)',
          badge: 'Oligometastatic Precision Target',
          rationale: 'Ablative high-dose conformal delivery sparing adjacent spinal cord and critical organs at risk (OAR).',
        };
    }
  }, [fractionScheme]);

  // Spinal Cord Tolerance Warning
  const cumulativeSpinalBed3 = priorDoseGy > 0 ? schemeDetails.bed3 + (priorDoseGy * 2.3) : schemeDetails.bed3;
  const isSpinalCordAtRisk = (indication === 'bone_metastases' || indication === 'spinal_cord_compression') && cumulativeSpinalBed3 > 100;

  // Copy Plan
  const handleCopyPlan = () => {
    const summary = `PALLIATIVE RADIOTHERAPY CLINICAL ORDERS
Patient: ${patientName} • ${primaryCancer} • Hospital ID: #${patientId}
Indication: ${indication.replace('_', ' ').toUpperCase()} • Site: ${targetSite}
Prescription: ${schemeDetails.label}
- Total Dose: ${schemeDetails.totalDose} Gy in ${schemeDetails.fractions} fraction(s) (${schemeDetails.fractionDose} Gy/fx)
- BED-10: ${schemeDetails.bed10} Gy10 | BED-3 (Late tissue): ${schemeDetails.bed3} Gy3
- Spinal Cord Constraint: ${isSpinalCordAtRisk ? 'WARNING: High cumulative re-irradiation dose' : 'Safe (<100 Gy3 cumulative)'}

1. PAIN FLARE PROPHYLAXIS PROTOCOL:
- Dexamethasone: 8 mg PO daily in morning for Days 1–5 (reduces post-radiation pain flare from 40% to <15%).
- Breakthrough Opioid: Pre-authorize rescue analgesia q1h PRN during acute post-radiation days 1–3.

2. RADIATION TOXICITY MANAGEMENT:
- Radiation Dermatitis: Grade ${dermatitisGrade} (Aqueous cream BD, avoid rubbing, perfume, or thermal pads).
- Nausea Prophylaxis: Ondansetron 8 mg PO 1 hour before pelvic/abdominal radiation fields.

3. PATIENT & FAMILY COUNSELING:
- Confirmed: Patient is NOT radioactive. Family contact is 100% safe.
- Expected timeline: Pain relief begins at 10–14 days; maximum analgesic response at 3–4 weeks.`;

    navigator.clipboard.writeText(summary);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  // Dispatch Plan
  const handleDispatchPlan = () => {
    const summary = `Palliative RT Dispatched: ${schemeDetails.totalDose}Gy in ${schemeDetails.fractions}fx for ${targetSite}. Dexamethasone 8mg flare prophylaxis & ASHA home monitoring active.`;
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
        <div className="bg-gradient-to-r from-amber-700 via-orange-800 to-rose-900 text-white p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-white/15 rounded-xl backdrop-blur-md text-white border border-white/20">
              <IconSparkles className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Palliative Radiotherapy & Toxicity Staging Suite</h2>
                <span className="px-2 py-0.5 bg-white/20 rounded-full text-[11px] font-semibold tracking-wide uppercase">
                  Radiation Oncology
                </span>
              </div>
              <p className="text-xs text-orange-200 mt-0.5">
                Patient: <span className="font-semibold text-white">{patientName}</span> • {primaryCancer} • Hospital ID: #{patientId}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-[11px] text-orange-200 font-medium">Prescribed Regimen</div>
              <div className="text-sm font-extrabold text-white">{schemeDetails.totalDose} Gy in {schemeDetails.fractions} fx</div>
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
            onClick={() => setActiveTab('regimen_planner')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'regimen_planner'
                ? 'border-amber-600 text-amber-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconHeartPulse className="w-4 h-4" />
            <span>1. Clinical Indication & Fractionation Staging</span>
          </button>
          <button
            onClick={() => setActiveTab('toxicity_flare')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'toxicity_flare'
                ? 'border-amber-600 text-amber-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldAlert className="w-4 h-4" />
            <span>2. Pain Flare & Toxicity (CTCAE v5.0)</span>
          </button>
          <button
            onClick={() => setActiveTab('beam_visualizer')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'beam_visualizer'
                ? 'border-amber-600 text-amber-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconSparkles className="w-4 h-4" />
            <span>3. Target Volume & Isodose Canvas</span>
          </button>
          <button
            onClick={() => setActiveTab('counseling')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'counseling'
                ? 'border-amber-600 text-amber-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconUsers className="w-4 h-4" />
            <span>4. Family Counseling & Myths (द्विभाषी)</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-amber-600 text-amber-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconClock className="w-4 h-4" />
            <span>5. Radiotherapy Trajectory Log</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 bg-slate-50/40">
          
          {/* Quick RT Specification Bar */}
          <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-xs mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Target Site:</span>
                <span className="font-extrabold text-amber-900 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                  {targetSite}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Scheme:</span>
                <span className="font-bold text-slate-900">{schemeDetails.label}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Progress:</span>
                <span className="font-semibold text-emerald-700">
                  Fraction {fractionsCompleted} of {schemeDetails.fractions} ({Math.round((fractionsCompleted / schemeDetails.fractions) * 100)}%)
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-900 border border-amber-300">
                {schemeDetails.badge}
              </span>
            </div>
          </div>

          {/* TAB 1: INDICATION & FRACTIONATION STAGING */}
          {activeTab === 'regimen_planner' && (
            <div className="space-y-6">
              
              {/* Indication Selection */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="border-b pb-3 flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">1. Palliative Clinical Indication</h3>
                    <p className="text-xs text-slate-500">Select clinical presentation to determine evidence-based radiation dose</p>
                  </div>
                  <span className="text-xs font-bold text-amber-800 bg-amber-50 px-2.5 py-1 rounded-lg border border-amber-200">
                    Evidence-Based Protocol
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                  {[
                    { id: 'bone_metastases', title: 'Uncomplicated Bone Metastasis', desc: 'Skeletal pain relief (8 Gy / 1fx preferred)' },
                    { id: 'spinal_cord_compression', title: 'Spinal Cord Compression (MSCC)', desc: 'Motor preservation & neuro decompression' },
                    { id: 'hemostatic_bleeding', title: 'Hemostatic Hemorrhage', desc: 'Pelvic / Cervical / Gastric tumor bleeding' },
                    { id: 'svco_obstruction', title: 'Superior Vena Cava Obstruction', desc: 'Venous engorgement & thoracic inlet relief' },
                    { id: 'brain_metastases', title: 'Brain Metastases (WBRT / SRS)', desc: 'Intracranial pressure & focal control' },
                  ].map((ind) => (
                    <button
                      key={ind.id}
                      onClick={() => setIndication(ind.id as any)}
                      className={`p-3 rounded-xl border text-left text-xs transition ${
                        indication === ind.id
                          ? 'border-amber-600 bg-amber-50/80 text-amber-950 font-bold ring-1 ring-amber-500'
                          : 'border-slate-200 bg-white hover:bg-slate-50 text-slate-700'
                      }`}
                    >
                      <div className="font-bold text-slate-900 mb-0.5">{ind.title}</div>
                      <div className="text-[11px] text-slate-500 font-normal leading-tight">{ind.desc}</div>
                    </button>
                  ))}
                </div>

                {/* Target Site Free-Text Input */}
                <div className="pt-2 text-xs">
                  <label className="font-bold text-slate-700 block mb-1">Anatomical Target Site:</label>
                  <input
                    type="text"
                    value={targetSite}
                    onChange={(e) => setTargetSite(e.target.value)}
                    className="w-full p-2 border border-slate-300 rounded-lg text-xs font-semibold bg-slate-50 text-slate-900"
                    placeholder="e.g. L3–L4 Lumbar Spine, Right Femoral Neck, Pelvic Cervical Mass"
                  />
                </div>
              </div>

              {/* Fractionation Scheme Selector */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="border-b pb-3 flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">2. Dose & Fractionation Prescribing Engine</h3>
                    <p className="text-xs text-slate-500">ASTRO / ESTRO consensus guidelines for palliative radiotherapy</p>
                  </div>
                  <span className="text-xs font-bold text-slate-500">Radiobiology Models</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {[
                    { id: 'single_8gy', title: '8 Gy in 1 Single Fraction', note: 'ASTRO Gold Standard for bone pain', bed: 'BED10: 14.4 Gy' },
                    { id: 'five_20gy', title: '20 Gy in 5 Fractions (4 Gy/fx)', note: 'Higher local control, lower retreatment', bed: 'BED10: 28.0 Gy' },
                    { id: 'ten_30gy', title: '30 Gy in 10 Fractions (3 Gy/fx)', note: 'Favorable prognosis (>6 months survival)', bed: 'BED10: 39.0 Gy' },
                    { id: 'stereotactic_srs', title: '24 Gy in 2 Fractions (SBRT)', note: 'Oligometastases, high ablative precision', bed: 'BED10: 52.8 Gy' },
                  ].map((s) => (
                    <button
                      key={s.id}
                      onClick={() => setFractionScheme(s.id as any)}
                      className={`p-3.5 rounded-xl border text-left text-xs transition ${
                        fractionScheme === s.id
                          ? 'border-amber-600 bg-amber-50/70 text-amber-950 font-bold ring-1 ring-amber-500'
                          : 'border-slate-200 bg-white hover:bg-slate-50 text-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-bold text-slate-900">{s.title}</span>
                        <span className="px-1.5 py-0.5 rounded bg-slate-200 text-slate-800 text-[10px] font-semibold">{s.bed}</span>
                      </div>
                      <p className="text-[11px] text-slate-500 font-normal">{s.note}</p>
                    </button>
                  ))}
                </div>

                {/* Radiobiology Metrics Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-xs">
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="text-slate-500 block text-[11px]">Total Physical Dose:</span>
                    <span className="text-lg font-black text-slate-900">{schemeDetails.totalDose} Gy</span>
                  </div>
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="text-slate-500 block text-[11px]">Fraction Size:</span>
                    <span className="text-lg font-black text-slate-900">{schemeDetails.fractionDose} Gy / fx</span>
                  </div>
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="text-slate-500 block text-[11px]">Tumor BED (α/β=10):</span>
                    <span className="text-lg font-black text-amber-800">{schemeDetails.bed10} Gy₁₀</span>
                  </div>
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg">
                    <span className="text-slate-500 block text-[11px]">Late Tissue BED (α/β=3):</span>
                    <span className="text-lg font-black text-amber-800">{schemeDetails.bed3} Gy₃</span>
                  </div>
                </div>

                {/* ASTRO Clinical Practice Recommendation Note */}
                <div className="p-3.5 bg-amber-50/80 border border-amber-200 rounded-xl text-xs space-y-1">
                  <div className="font-bold text-amber-950">ASTRO Clinical Guidance Rationale:</div>
                  <p className="text-[11px] text-amber-900 leading-relaxed">{schemeDetails.rationale}</p>
                </div>
              </div>

            </div>
          )}

          {/* TAB 2: PAIN FLARE & TOXICITY (CTCAE v5.0) */}
          {activeTab === 'toxicity_flare' && (
            <div className="space-y-5">
              
              {/* Pain Flare Prophylaxis Card */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-3">
                <div className="flex items-center justify-between border-b pb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-2 rounded-lg bg-rose-100 text-rose-700">
                      <IconShieldAlert className="w-4 h-4" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">Post-Radiation Pain Flare Prophylaxis Protocol</h3>
                      <p className="text-xs text-slate-500">Transient severe pain exacerbation occurs in 35–40% of bone RT patients on Days 1–3</p>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 bg-rose-50 text-rose-800 border border-rose-200 rounded-lg text-xs font-bold">
                    Dexamethasone Protocol
                  </span>
                </div>

                <div className="p-3.5 bg-rose-50/60 border border-rose-200 rounded-xl text-xs space-y-2">
                  <div className="font-bold text-rose-950 text-sm">Evidence-Based Prevention (NCIC CTG SC.23 Trial):</div>
                  <p className="text-xs text-rose-900 leading-relaxed">
                    Radiation triggers acute periosteal edema, mast cell degranulation, and inflammatory cytokine release. Prophylactic <b>Dexamethasone 8 mg PO daily for 5 days</b> (starting 1 hour before the first fraction) cuts pain flare incidence from 35% down to 17%.
                  </p>
                  <div className="flex items-center gap-2 font-bold text-rose-800 text-xs mt-1">
                    <span>Rx: Dexamethasone 8 mg PO morning daily $\times$ 5 days</span>
                    <span className="text-slate-400">•</span>
                    <span>Proton Pump Inhibitor (Pantoprazole 40mg) co-prescribed</span>
                  </div>
                </div>
              </div>

              {/* Radiation Toxicity Staging (CTCAE v5.0) */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="border-b pb-3">
                  <h3 className="text-sm font-bold text-slate-900">Radiation Toxicity Grading (CTCAE v5.0)</h3>
                  <p className="text-xs text-slate-500">Stage skin and gastrointestinal adverse reactions to direct radiation fields</p>
                </div>

                {/* Radiation Dermatitis */}
                <div className="space-y-2 text-xs">
                  <span className="font-bold text-slate-800 block">1. Radiation Dermatitis:</span>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    {[
                      { grade: 1, title: 'Grade 1: Faint Erythema', desc: 'Mild erythema or dry desquamation; skin intact', care: 'Aqueous cream BD, gentle lukewarm water' },
                      { grade: 2, title: 'Grade 2: Moderate Erythema', desc: 'Patchy moist desquamation confined to skin folds', care: 'Hydrogel / non-adherent foam dressings' },
                      { grade: 3, title: 'Grade 3: Confluent Desquamation', desc: 'Moist desquamation outside skin folds, bleeding', care: 'Silver sulfadiazine, temporary RT pause' },
                    ].map((g) => (
                      <button
                        key={g.grade}
                        onClick={() => setDermatitisGrade(g.grade as any)}
                        className={`p-3 rounded-lg border text-left text-xs transition ${
                          dermatitisGrade === g.grade
                            ? 'border-amber-600 bg-amber-50 text-amber-950 font-bold ring-1 ring-amber-500'
                            : 'border-slate-200 bg-slate-50 text-slate-700'
                        }`}
                      >
                        <div className="font-bold text-slate-900 mb-0.5">{g.title}</div>
                        <div className="text-[11px] text-slate-500 mb-1">{g.desc}</div>
                        <div className="text-[10px] text-amber-800 font-semibold">Care: {g.care}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Radiation Nausea / Enteritis */}
                <div className="space-y-2 text-xs pt-2">
                  <span className="font-bold text-slate-800 block">2. Radiation-Induced Nausea & Vomiting (RINV):</span>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { grade: 0, title: 'Grade 0: None', desc: 'No nausea or anorexia' },
                      { grade: 1, title: 'Grade 1: Mild', desc: 'Loss of appetite without vomiting' },
                      { grade: 2, title: 'Grade 2: Moderate', desc: '2–5 vomiting episodes in 24h (Ondansetron 8mg indicated)' },
                    ].map((n) => (
                      <button
                        key={n.grade}
                        onClick={() => setNauseaGrade(n.grade as any)}
                        className={`p-2.5 rounded-lg border text-left text-xs transition ${
                          nauseaGrade === n.grade
                            ? 'border-amber-600 bg-amber-50 text-amber-950 font-bold ring-1 ring-amber-500'
                            : 'border-slate-200 bg-slate-50 text-slate-700'
                        }`}
                      >
                        <div className="font-bold text-slate-900">{n.title}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{n.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 3: TARGET VOLUME & ISODOSE CANVAS */}
          {activeTab === 'beam_visualizer' && (
            <div className="space-y-6">
              
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                <div className="flex items-center justify-between border-b pb-3 mb-4">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Anatomical Radiation Field & Isodose Coverage</h3>
                    <p className="text-xs text-slate-500">2D/3D Conformal Collimator Aperture & Spinal Cord Sparing Model</p>
                  </div>
                  <span className="text-xs font-bold text-amber-800 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                    Target: {targetSite}
                  </span>
                </div>

                {/* SVG Visualizer */}
                <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 flex flex-col items-center">
                  <svg className="w-full max-w-md h-64" viewBox="0 0 360 220">
                    {/* Background Grid */}
                    <defs>
                      <pattern id="rtGrid" width="20" height="20" patternUnits="userSpaceOnUse">
                        <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#e2e8f0" strokeWidth="0.8" />
                      </pattern>
                      <radialGradient id="isodoseGlow" cx="50%" cy="50%" r="50%">
                        <stop offset="0%" stopColor="#f59e0b" stopOpacity="0.8" />
                        <stop offset="60%" stopColor="#d97706" stopOpacity="0.4" />
                        <stop offset="100%" stopColor="#b45309" stopOpacity="0.0" />
                      </radialGradient>
                    </defs>
                    <rect width="360" height="220" fill="url(#rtGrid)" />

                    {/* Patient Body Outline (Thoracolumbar cross section) */}
                    <ellipse cx="180" cy="110" rx="140" ry="85" fill="#f8fafc" stroke="#94a3b8" strokeWidth="2.5" />
                    
                    {/* Spinal Column / Vertebral Body */}
                    <circle cx="180" cy="155" r="28" fill="#e2e8f0" stroke="#64748b" strokeWidth="2" />
                    <text x="180" y="160" fontSize="9" fontWeight="bold" fill="#475569" textAnchor="middle">
                      Vertebral Body
                    </text>

                    {/* Spinal Cord (Critical Organ at Risk) */}
                    <circle cx="180" cy="178" r="9" fill="#fecdd3" stroke="#e11d48" strokeWidth="1.5" />
                    <text x="180" y="181" fontSize="7" fontWeight="bold" fill="#9f1239" textAnchor="middle">
                      Cord
                    </text>

                    {/* Collimator Radiation Beam Vectors (Anterior-Posterior Opposed Beams) */}
                    {/* AP Beam from Anterior */}
                    <line x1="180" y1="5" x2="180" y2="125" stroke="#f59e0b" strokeWidth="3" strokeDasharray="4 2" />
                    <polygon points="175,125 185,125 180,135" fill="#f59e0b" />
                    <text x="180" y="20" fontSize="9" fontWeight="extrabold" fill="#b45309" textAnchor="middle">
                      Beam 1 (Anterior 6 MV)
                    </text>

                    {/* PA Beam from Posterior */}
                    <line x1="180" y1="215" x2="180" y2="188" stroke="#f59e0b" strokeWidth="3" strokeDasharray="4 2" />
                    <polygon points="175,188 185,188 180,180" fill="#f59e0b" />
                    <text x="180" y="210" fontSize="9" fontWeight="extrabold" fill="#b45309" textAnchor="middle">
                      Beam 2 (Posterior 6 MV)
                    </text>

                    {/* Isodose Coverage Cloud (95% isodose line covering tumor mass in vertebral body) */}
                    <circle cx="180" cy="155" r="38" fill="url(#isodoseGlow)" />
                    <circle cx="180" cy="155" r="38" fill="none" stroke="#d97706" strokeWidth="2" strokeDasharray="3 3" />
                    
                    {/* Beam Aperture Boundaries (Collimator Field Width) */}
                    <rect x="135" y="60" width="90" height="135" fill="none" stroke="#ef4444" strokeWidth="1.5" opacity="0.6" />
                    <text x="138" y="75" fontSize="8" fontWeight="bold" fill="#dc2626">
                      Field: 8 × 12 cm
                    </text>

                    {/* Legend Text */}
                    <text x="30" y="30" fontSize="9" fontWeight="bold" fill="#047857">
                      ✓ Cord Dose: Safe &lt; 45 Gy
                    </text>
                    <text x="30" y="45" fontSize="9" fontWeight="bold" fill="#b45309">
                      ★ Target Coverage: 100%
                    </text>
                  </svg>

                  {/* Fraction Progress Tracker */}
                  <div className="w-full max-w-md mt-4 space-y-1.5">
                    <div className="flex justify-between text-xs font-bold text-slate-700">
                      <span>Delivery Fraction Progress:</span>
                      <span className="text-amber-800 font-extrabold">
                        {fractionsCompleted} of {schemeDetails.fractions} Completed
                      </span>
                    </div>
                    <div className="w-full h-3 bg-slate-200 rounded-full overflow-hidden flex">
                      {Array.from({ length: schemeDetails.fractions }).map((_, idx) => (
                        <div
                          key={idx}
                          className={`flex-1 border-r border-white transition ${
                            idx < fractionsCompleted ? 'bg-amber-600' : 'bg-slate-300'
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 4: FAMILY COUNSELING & MYTH-BUSTING (द्विभाषी) */}
          {activeTab === 'counseling' && (
            <div className="space-y-4">
              
              <div className="p-4 rounded-xl bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-200">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-amber-900 text-sm">Radiotherapy Truth-Telling & Myth-Busting (द्विभाषी मार्गदर्शन)</span>
                  <span className="px-2 py-0.5 bg-amber-200 text-amber-900 rounded text-[10px] font-bold">Radiation Myths</span>
                </div>
                <p className="text-xs text-amber-950">
                  Patients and families fear radiation oncology due to misconceptions of radioactivity, burning, or permanent organ loss. Clear communication reduces anxiety and prevents abandonment of treatment.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* Myth 1: Radioactivity */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">1. The &quot;Radioactive Patient&quot; Myth (विकिरण का डर)</span>
                    <span className="text-[10px] text-emerald-700 font-bold bg-emerald-50 px-2 py-0.5 rounded">100% Safe</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Guidance (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5 leading-relaxed">
                        &quot;मशीन से सिकाई (रेडिएशन) होने के बाद मरीज के शरीर में कोई रेडिएशन नहीं रहता। मरीज बिल्कुल सुरक्षित है; बच्चे, नाती-पोते और परिवार वाले उन्हें छू सकते हैं, गले लगा सकते हैं और साथ सो सकते हैं। इससे किसी को कोई खतरा नहीं है।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>English Fact:</b> External beam radiotherapy passes through tissue like light from a flashlight. The patient does NOT emit radiation.
                    </p>
                  </div>
                </div>

                {/* Myth 2: Pain Relief Timeline & Flare */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">2. Delayed Relief & Day 2–3 Pain Flare (दर्द की समयसीमा)</span>
                    <span className="text-[10px] text-amber-700 font-bold bg-amber-50 px-2 py-0.5 rounded">10–14 Day Lag</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Guidance (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5 leading-relaxed">
                        &quot;सिकाई के तुरंत बाद दर्द जादू की तरह गायब नहीं होता। असर दिखने में 10 से 15 दिन लगते हैं। शुरू के 2-3 दिनों में सूजन की वजह से दर्द थोड़ा बढ़ सकता है (पेन फ्लेयर), घबराएं नहीं! हमने इसके लिए सूजन कम करने वाली खास दवा (डेक्सामेथासोन) दी है।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>English Fact:</b> Preparing the family for the transient pain flare prevents emergency room panics and restores confidence in analgesia.
                    </p>
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 5: TRAJECTORY LOG */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Radiotherapy Verification & History Log</h3>
                  <p className="text-xs text-slate-500">Kidwai Department of Radiation Oncology (LINAC 3)</p>
                </div>
                <span className="px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg text-xs font-bold border">
                  3 Past Treatments
                </span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden bg-white">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-3">Date</th>
                      <th className="p-3">Anatomic Target</th>
                      <th className="p-3">Dose / Fx</th>
                      <th className="p-3">Linear Accelerator</th>
                      <th className="p-3">Pain Response</th>
                      <th className="p-3">Radiation Oncologist</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-amber-50/40">
                      <td className="p-3 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-3 font-bold text-amber-900">{targetSite}</td>
                      <td className="p-3 font-black text-slate-900">{schemeDetails.totalDose} Gy / {schemeDetails.fractions} fx</td>
                      <td className="p-3 text-slate-700 font-medium">TrueBeam 6 MV Photon</td>
                      <td className="p-3 text-amber-700 font-bold">Initiated (Day 1)</td>
                      <td className="p-3 text-slate-600">Dr Ramesh (Radiation Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">12 Jun 2026</td>
                      <td className="p-3">Right Femur Neck</td>
                      <td className="p-3">8 Gy / 1 fx</td>
                      <td className="p-3 text-slate-700">Elekta Synergy</td>
                      <td className="p-3 text-emerald-700 font-bold">Complete Response (100%)</td>
                      <td className="p-3 text-slate-600">Dr Ramesh (Radiation Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">15 Jan 2026</td>
                      <td className="p-3">T6–T8 Thoracic Spine</td>
                      <td className="p-3">20 Gy / 5 fx</td>
                      <td className="p-3 text-slate-700">Clinac iX</td>
                      <td className="p-3 text-emerald-700 font-bold">Partial Response (70%)</td>
                      <td className="p-3 text-slate-600">Dr Ramesh (Radiation Lead)</td>
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
              {copiedToast ? '✓ Copied Radiotherapy Orders!' : '📋 Copy Radiotherapy Care Orders'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-amber-700 hover:bg-amber-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Radiotherapy Plan!' : '⚛️ Dispatch RT Orders & Flare Prophylaxis'}</span>
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
