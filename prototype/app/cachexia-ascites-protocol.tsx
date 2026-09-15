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

export type CachexiaStage = 'pre_cachexia' | 'cachexia' | 'refractory_cachexia';
export type AscitesSeverity = 'mild_subclinical' | 'moderate_tense' | 'gross_refractory';

interface CachexiaAscitesModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchOrder?: (summary: string) => void;
}

export function CachexiaAscitesModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchOrder,
}: CachexiaAscitesModalProps) {
  const [activeTab, setActiveTab] = useState<'cachexia' | 'ascites' | 'family_counseling' | 'history'>('cachexia');

  // Cachexia state (Fearon 2011 International Consensus)
  const [baselineWeightKg, setBaselineWeightKg] = useState<number>(62);
  const [currentWeightKg, setCurrentWeightKg] = useState<number>(51);
  const [heightCm, setHeightCm] = useState<number>(160);
  const [foodIntakePercent, setFoodIntakePercent] = useState<number>(30); // % of normal
  const [systemicInflammation, setSystemicInflammation] = useState<boolean>(true); // CRP > 10 or Albumin < 3.2
  const [chemoUnresponsive, setChemoUnresponsive] = useState<boolean>(true);
  const [performanceStatusLow, setPerformanceStatusLow] = useState<boolean>(true); // ECOG 3-4

  // Ascites state
  const [abdominalGirthCm, setAbdominalGirthCm] = useState<number>(94);
  const [baselineGirthCm, setBaselineGirthCm] = useState<number>(76);
  const [ascitesSeverity, setAscitesSeverity] = useState<AscitesSeverity>('moderate_tense');
  const [serumAlbumin, setSerumAlbumin] = useState<number>(2.8);
  const [asciticAlbumin, setAsciticAlbumin] = useState<number>(2.2);
  const [hasPeripheralEdema, setHasPeripheralEdema] = useState<boolean>(true);
  const [indwellingCatheterCandidate, setIndwellingCatheterCandidate] = useState<boolean>(true);

  // Notifications
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchedToast, setDispatchedToast] = useState<boolean>(false);

  // Calculations
  const bmi = useMemo(() => {
    const heightM = heightCm / 100;
    return parseFloat((currentWeightKg / (heightM * heightM)).toFixed(1));
  }, [currentWeightKg, heightCm]);

  const weightLossPercent = useMemo(() => {
    if (baselineWeightKg <= 0) return 0;
    return parseFloat((((baselineWeightKg - currentWeightKg) / baselineWeightKg) * 100).toFixed(1));
  }, [baselineWeightKg, currentWeightKg]);

  // Fearon 2011 Cachexia Classification
  const cachexiaClassification = useMemo(() => {
    if (chemoUnresponsive && performanceStatusLow && (weightLossPercent > 10 || foodIntakePercent < 25)) {
      return {
        stage: 'refractory_cachexia' as CachexiaStage,
        label: 'REFRACTORY CACHEXIA',
        color: '#be123c',
        bg: '#ffe4e6',
        badge: 'END-STAGE CATABOLISM',
        guidance: 'Pro-catabolic state driven by tumor cytokines. Enteral tube feeding (PEG/NG) or TPN does NOT improve survival or functional capacity and increases risks of fluid overload, nausea, and aspiration. Shift strictly to "Comfort Feeding Only" — honor patient food cravings in micro-portions.',
      };
    }
    if (weightLossPercent > 5 || (bmi < 20 && weightLossPercent > 2) || foodIntakePercent < 50) {
      return {
        stage: 'cachexia' as CachexiaStage,
        label: 'CANCER CACHEXIA SYNDROME',
        color: '#c2410c',
        bg: '#ffedd5',
        badge: 'ACTIVE SKELETAL WASTING',
        guidance: 'Significant loss of skeletal muscle and adipose mass. Screen for reversible secondary causes: oral thrush (Candida), opioid constipation, gastroparesis (Metoclopramide 10mg TID), and uncontrolled pain. Consider short-course Dexamethasone 2–4 mg PO daily for acute appetite stimulation (limit to 2–4 weeks to prevent myopathy).',
      };
    }
    return {
      stage: 'pre_cachexia' as CachexiaStage,
      label: 'PRE-CACHEXIA',
      color: '#047857',
      bg: '#d1fae5',
      badge: 'EARLY METABOLIC SHIFT',
      guidance: 'Early weight loss (<5%) with anorexia and impaired glucose tolerance. Nutritionist consult for high-protein, calorie-dense oral nutritional supplements. Early intervention may stabilize lean mass.',
    };
  }, [weightLossPercent, bmi, foodIntakePercent, chemoUnresponsive, performanceStatusLow]);

  // SAAG Calculation
  const saag = useMemo(() => {
    return parseFloat((serumAlbumin - asciticAlbumin).toFixed(2));
  }, [serumAlbumin, asciticAlbumin]);

  const saagInterpretation = useMemo(() => {
    if (saag < 1.1) {
      return {
        label: 'LOW SAAG (< 1.1 g/dL): PERITONEAL CARCINOMATOSIS',
        color: '#be123c',
        bg: '#ffe4e6',
        description: 'Exudative malignant ascites caused by peritoneal tumor seeding, increased vascular permeability, and lymphatic obstruction. Portal hypertension is absent. Diuretics (Spironolactone/Furosemide) typically have LOW response (~15-20%) compared to cirrhotic ascites.',
        paracentesisAlbuminAdvice: 'Routine IV albumin replacement (Human Albumin 20%) is generally NOT routinely required for non-cirrhotic peritoneal carcinomatosis unless >5 L drained in a hemodynamically unstable patient.',
      };
    }
    return {
      label: 'HIGH SAAG (≥ 1.1 g/dL): PORTAL HYPERTENSION / HEPATIC METASTASIS',
      color: '#0284c7',
      bg: '#e0f2fe',
      description: 'Transudative ascites driven by elevated sinusoidal portal pressure (hepatic metastases, portal vein thrombosis, or underlying cirrhosis). Good response to Spironolactone (100–200mg) + Furosemide (40mg).',
      paracentesisAlbuminAdvice: 'Infuse 20% Human Albumin at 6–8 g per liter of ascitic fluid removed beyond 5 L to prevent Post-Paracentesis Circulatory Dysfunction (PPCD).',
    };
  }, [saag]);

  const estimatedAscitesLiters = useMemo(() => {
    const diff = Math.max(0, abdominalGirthCm - baselineGirthCm);
    return parseFloat((diff * 0.25).toFixed(1)); // Approx 250mL per cm increase in girth
  }, [abdominalGirthCm, baselineGirthCm]);

  const handleCopyProtocol = () => {
    const text = `PALLIATIVE CACHEXIA, NUTRITION & MALIGNANT ASCITES CLINICAL PROTOCOL
Patient: ${patientName} (${patientId}) · ${primaryCancer}
Current Weight: ${currentWeightKg} kg (Baseline: ${baselineWeightKg} kg) · Loss: ${weightLossPercent}% · BMI: ${bmi}
Estimated Oral Intake: ${foodIntakePercent}% of normal · Inflammation: ${systemicInflammation ? 'Present' : 'None'}
--------------------------------------------------
CACHEXIA STAGE: ${cachexiaClassification.label}
CLINICAL GUIDELINE: ${cachexiaClassification.guidance}
ARTIFICIAL NUTRITION DIRECTIVE: ${cachexiaClassification.stage === 'refractory_cachexia' ? 'Artificial Nutrition (PEG/TPN) NOT indicated. Comfort feeding and mouth care prioritized.' : 'Oral dietary optimization and antiemetic review.'}
--------------------------------------------------
MALIGNANT ASCITES EVALUATION:
Abdominal Girth: ${abdominalGirthCm} cm (Baseline: ${baselineGirthCm} cm) · Estimated Volume: ~${estimatedAscitesLiters} Liters
SAAG: ${saag} g/dL (${saagInterpretation.label})
RECOMMENDED PARACENTESIS PLAN:
1. Target Drainage: 2.5 to 4.0 Liters over 4–6 hours via gentle gravity drainage.
2. Indwelling PleurX/Rocket Catheter: ${indwellingCatheterCandidate ? 'Candidate for tunneled peritoneal catheter to avoid frequent hospital taps.' : 'Intermittent ultrasound-guided paracentesis.'}
3. Albumin Strategy: ${saagInterpretation.paracentesisAlbuminAdvice}
4. Diuretic Strategy: ${saag < 1.1 ? 'Discontinue high-dose diuretics if no peripheral edema (ineffective in carcinomatosis and causes renal prerenal azotemia).' : 'Spironolactone 100mg + Furosemide 40mg PO daily.'}`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchOrder = () => {
    setDispatchedToast(true);
    if (onDispatchOrder) {
      onDispatchOrder(
        `Ascites & Nutrition Plan Logged: ${cachexiaClassification.label}, Girth ${abdominalGirthCm}cm (~${estimatedAscitesLiters}L ascites), SAAG ${saag}`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cachexia-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-slate-900 via-teal-950 to-slate-900 text-white flex flex-wrap items-center justify-between gap-3 border-b border-teal-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-teal-500/20 border border-teal-400/40 flex items-center justify-center text-teal-300">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="cachexia-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Palliative Nutrition, Cachexia & Malignant Ascites Engine
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-teal-500/20 text-teal-200 border border-teal-400/30">
                  Fearon Consensus & EAPC Palliative Guidelines
                </span>
              </div>
              <p className="text-xs text-teal-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: cachexiaClassification.bg,
                color: cachexiaClassification.color,
                borderColor: cachexiaClassification.color,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>{cachexiaClassification.label}</span>
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
            onClick={() => setActiveTab('cachexia')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'cachexia'
                ? 'border-teal-600 text-teal-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>⚖️ Fearon Cachexia & Nutrition Staging</span>
          </button>
          <button
            onClick={() => setActiveTab('ascites')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'ascites'
                ? 'border-teal-600 text-teal-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>💧 Malignant Ascites & SAAG Calculator</span>
          </button>
          <button
            onClick={() => setActiveTab('family_counseling')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'family_counseling'
                ? 'border-teal-600 text-teal-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🇮🇳 Bilingual Family Counseling (Hindi/English)</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-teal-600 text-teal-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Girth & Paracentesis Tracker</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: CACHEXIA STAGING */}
          {activeTab === 'cachexia' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Anthropometrics & Clinical Parameters */}
              <div className="lg:col-span-7 space-y-4">
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-800 block">
                    1. Weight Kinetics & Anthropometric Metrics
                  </span>
                  <div className="grid grid-cols-3 gap-3 text-xs">
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Baseline Weight (6 mo ago)</label>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={baselineWeightKg}
                          onChange={(e) => setBaselineWeightKg(parseFloat(e.target.value) || 0)}
                          className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                        />
                        <span className="text-slate-500 font-semibold">kg</span>
                      </div>
                    </div>

                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Current Weight</label>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={currentWeightKg}
                          onChange={(e) => setCurrentWeightKg(parseFloat(e.target.value) || 0)}
                          className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                        />
                        <span className="text-slate-500 font-semibold">kg</span>
                      </div>
                    </div>

                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Patient Height</label>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={heightCm}
                          onChange={(e) => setHeightCm(parseFloat(e.target.value) || 0)}
                          className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                        />
                        <span className="text-slate-500 font-semibold">cm</span>
                      </div>
                    </div>
                  </div>

                  {/* Summary Bar */}
                  <div className="pt-2 border-t border-slate-200 flex flex-wrap items-center justify-between text-xs">
                    <div className="flex items-center gap-4">
                      <span>Body Mass Index (BMI): <strong className={bmi < 18.5 ? 'text-red-700' : 'text-slate-900'}>{bmi} kg/m²</strong></span>
                      <span>Total Loss: <strong className={weightLossPercent > 5 ? 'text-red-700' : 'text-slate-900'}>{weightLossPercent}%</strong></span>
                    </div>
                    <span className="text-[11px] font-semibold text-slate-500">Threshold: &gt;5% or BMI &lt;20</span>
                  </div>
                </div>

                {/* Oral Intake & Performance Status */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      2. Food Intake & Metabolic Profile
                    </span>
                    <span className="text-xs font-extrabold text-teal-700">{foodIntakePercent}% of normal</span>
                  </div>

                  <div>
                    <label className="text-xs text-slate-600 font-medium block mb-1">
                      Estimated Daily Caloric/Oral Intake relative to healthy state:
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="100"
                      step="5"
                      value={foodIntakePercent}
                      onChange={(e) => setFoodIntakePercent(parseInt(e.target.value))}
                      className="w-full accent-teal-600"
                    />
                    <div className="flex justify-between text-[10px] text-slate-400 mt-0.5">
                      <span>Sips only / Anorexic (0-20%)</span>
                      <span>Half portions (50%)</span>
                      <span>Normal Appetite (100%)</span>
                    </div>
                  </div>

                  <div className="space-y-2 pt-2 border-t border-slate-200 text-xs">
                    <label className="flex items-center gap-2 cursor-pointer font-medium text-slate-700">
                      <input
                        type="checkbox"
                        checked={systemicInflammation}
                        onChange={(e) => setSystemicInflammation(e.target.checked)}
                        className="w-4 h-4 text-teal-600 rounded border-slate-300"
                      />
                      <span>Systemic Inflammatory Response (CRP &gt;10 mg/L or Serum Albumin &lt;3.2 g/dL)</span>
                    </label>

                    <label className="flex items-center gap-2 cursor-pointer font-medium text-slate-700">
                      <input
                        type="checkbox"
                        checked={chemoUnresponsive}
                        onChange={(e) => setChemoUnresponsive(e.target.checked)}
                        className="w-4 h-4 text-teal-600 rounded border-slate-300"
                      />
                      <span>Anticancer therapy exhausted / Unresponsive to active antineoplastic treatment</span>
                    </label>

                    <label className="flex items-center gap-2 cursor-pointer font-medium text-slate-700">
                      <input
                        type="checkbox"
                        checked={performanceStatusLow}
                        onChange={(e) => setPerformanceStatusLow(e.target.checked)}
                        className="w-4 h-4 text-teal-600 rounded border-slate-300"
                      />
                      <span>Impaired Performance (ECOG 3–4 / PPSv2 &le; 40% - bedbound &gt;50% of day)</span>
                    </label>
                  </div>
                </div>

                {/* Artificial Hydration & Nutrition (ANH) Stance */}
                <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold text-amber-950 uppercase tracking-wider">
                    <IconShieldAlert className="w-4 h-4 text-amber-700" />
                    <span>Clinical Position on Artificial Nutrition & Hydration (ANH)</span>
                  </div>
                  <p className="text-xs text-amber-900 leading-relaxed font-medium">
                    {cachexiaClassification.stage === 'refractory_cachexia' ? (
                      <>
                        <strong>TPN and Enteral Feeding Tubes are NOT recommended.</strong> Extensive randomized trials show no prolongation of life or reversal of muscle wasting in refractory cachexia. Tube feeding causes fluid retention, increased bronchial secretions (death rattle), gastric distension, and aspiration pneumonia.
                      </>
                    ) : (
                      <>
                        Focus on oral calorie density, small frequent nutrient-rich snacks, and treating secondary anorexia causes (nausea, candidiasis, constipation).
                      </>
                    )}
                  </p>
                </div>
              </div>

              {/* Right Column: Staging Card & Comfort Feeding Compass */}
              <div className="lg:col-span-5 space-y-4">
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition space-y-2"
                  style={{
                    backgroundColor: cachexiaClassification.bg,
                    borderColor: cachexiaClassification.color,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold tracking-wider uppercase text-slate-700">Fearon Consensus Staging</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold text-white" style={{ backgroundColor: cachexiaClassification.color }}>
                      {cachexiaClassification.badge}
                    </span>
                  </div>

                  <div className="text-base font-black" style={{ color: cachexiaClassification.color }}>
                    {cachexiaClassification.label}
                  </div>

                  <p className="text-xs text-slate-800 leading-relaxed font-medium">
                    {cachexiaClassification.guidance}
                  </p>
                </div>

                {/* Comfort Feeding Principles */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner space-y-3">
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="font-bold text-teal-300">Palliative "Comfort Feeding" Checklist</span>
                    <span>Micro-Portions</span>
                  </div>

                  <ul className="text-xs text-slate-300 space-y-2">
                    <li className="flex items-start gap-2">
                      <span className="text-teal-400 font-bold">✓</span>
                      <span><strong>Eat for Pleasure, Not Volume:</strong> Patient dictates what, when, and how much to eat without calorie pressure.</span>
                    </li>
                    <li className="flex items-start gap-2">
                      <span className="text-teal-400 font-bold">✓</span>
                      <span><strong>Small Dessert Spoons:</strong> Serve meals in miniature ramekins or dessert bowls to avoid visual intimidation.</span>
                    </li>
                    <li className="flex items-start gap-2">
                      <span className="text-teal-400 font-bold">✓</span>
                      <span><strong>Avoid Strong Kitchen Odors:</strong> Cooking smells often trigger anticipatory nausea; serve food cool or at room temperature.</span>
                    </li>
                    <li className="flex items-start gap-2">
                      <span className="text-teal-400 font-bold">✓</span>
                      <span><strong>Rigorous Oral Hygiene:</strong> Hydrating oral swabs, crushed ice chips, and artificial saliva sprays provide greater relief than forced meals.</span>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: MALIGNANT ASCITES & SAAG */}
          {activeTab === 'ascites' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Girth & SAAG Controls */}
              <div className="lg:col-span-7 space-y-4">
                {/* Abdominal Girth */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      1. Abdominal Girth & Fluid Volume
                    </span>
                    <span className="text-xs font-extrabold text-teal-700">~{estimatedAscitesLiters} L Estimated Fluid</span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Baseline Non-Ascitic Girth</label>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={baselineGirthCm}
                          onChange={(e) => setBaselineGirthCm(parseFloat(e.target.value) || 0)}
                          className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                        />
                        <span className="text-slate-500 font-semibold">cm</span>
                      </div>
                    </div>

                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Current Measured Girth (Umbilicus)</label>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={abdominalGirthCm}
                          onChange={(e) => setAbdominalGirthCm(parseFloat(e.target.value) || 0)}
                          className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                        />
                        <span className="text-slate-500 font-semibold">cm</span>
                      </div>
                    </div>
                  </div>

                  <input
                    type="range"
                    min="60"
                    max="140"
                    step="1"
                    value={abdominalGirthCm}
                    onChange={(e) => setAbdominalGirthCm(parseFloat(e.target.value))}
                    className="w-full accent-teal-600"
                  />
                  <div className="flex justify-between text-[10px] text-slate-400">
                    <span>Baseline (60-75cm)</span>
                    <span>Tense / Distended (90-110cm)</span>
                    <span>Massive / Respiratory compromise (&gt;120cm)</span>
                  </div>
                </div>

                {/* SAAG Calculator */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      2. Serum-Ascites Albumin Gradient (SAAG)
                    </span>
                    <span className="text-xs font-black text-slate-900 bg-white px-2 py-1 border rounded shadow-2xs">
                      SAAG: {saag} g/dL
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Serum Albumin (g/dL)</label>
                      <input
                        type="number"
                        step="0.1"
                        value={serumAlbumin}
                        onChange={(e) => setSerumAlbumin(parseFloat(e.target.value) || 0)}
                        className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                      />
                    </div>
                    <div>
                      <label className="text-slate-600 font-medium block mb-1">Ascitic Fluid Albumin (g/dL)</label>
                      <input
                        type="number"
                        step="0.1"
                        value={asciticAlbumin}
                        onChange={(e) => setAsciticAlbumin(parseFloat(e.target.value) || 0)}
                        className="w-full px-2.5 py-1.5 border rounded-lg bg-white font-bold text-slate-800"
                      />
                    </div>
                  </div>

                  {/* SAAG Diagnostic Banner */}
                  <div
                    className="p-3 rounded-lg border text-xs space-y-1"
                    style={{
                      backgroundColor: saagInterpretation.bg,
                      borderColor: saagInterpretation.color,
                    }}
                  >
                    <div className="font-extrabold" style={{ color: saagInterpretation.color }}>
                      {saagInterpretation.label}
                    </div>
                    <p className="text-slate-700 leading-relaxed font-medium">
                      {saagInterpretation.description}
                    </p>
                  </div>
                </div>

                {/* Paracentesis & Drainage Plan */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 space-y-2 text-xs">
                  <span className="font-bold text-slate-800 block">Therapeutic Paracentesis Standing Plan</span>
                  <ul className="space-y-1.5 text-slate-600">
                    <li>• <strong>Target Volume:</strong> 2.5 to 4.0 Liters over 4–6 hours via gentle gravity drainage kit.</li>
                    <li>• <strong>Albumin Administration:</strong> {saagInterpretation.paracentesisAlbuminAdvice}</li>
                    <li>• <strong>Diuretics:</strong> Spironolactone 100mg PO daily (effective if concomitant peripheral edema; stop if dehydration/azotemia occurs).</li>
                  </ul>
                </div>
              </div>

              {/* Right Column: Visual Abdominal Distension SVG & Catheter Option */}
              <div className="lg:col-span-5 space-y-4">
                {/* SVG Abdominal Distension Visualizer */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner flex flex-col items-center">
                  <div className="w-full flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span className="font-semibold text-slate-200">Abdominal Fluid Model</span>
                    <span className="font-bold text-teal-300">{abdominalGirthCm} cm Girth</span>
                  </div>

                  <div className="w-full max-w-[260px] aspect-square flex items-center justify-center py-2">
                    <svg viewBox="0 0 160 160" className="w-full h-full drop-shadow-md">
                      {/* Torso Outline */}
                      <path
                        d="M 40 20 C 40 40 30 60 30 90 C 30 130 50 145 80 145 C 110 145 130 130 130 90 C 130 60 120 40 120 20 Z"
                        fill="#1e293b"
                        stroke="#475569"
                        strokeWidth="2"
                      />

                      {/* Distended Abdomen Contour based on Girth */}
                      <path
                        d={`M 40 60 C ${20 - (abdominalGirthCm - 70) * 0.4} 90 ${30 - (abdominalGirthCm - 70) * 0.4} 125 80 135 C ${130 + (abdominalGirthCm - 70) * 0.4} 125 ${140 + (abdominalGirthCm - 70) * 0.4} 90 120 60 Z`}
                        fill="#0f766e"
                        opacity="0.85"
                        stroke="#14b8a6"
                        strokeWidth="2"
                      />

                      {/* Ascitic Fluid Pool */}
                      <ellipse
                        cx="80"
                        cy="110"
                        rx={Math.min(48, 20 + estimatedAscitesLiters * 6)}
                        ry={Math.min(28, 10 + estimatedAscitesLiters * 4)}
                        fill="#2dd4bf"
                        opacity="0.75"
                      />

                      {/* Umbilicus */}
                      <circle cx="80" cy="95" r="2.5" fill="#042f2e" />

                      {/* Fluid wave ripples */}
                      <path
                        d="M 60 108 Q 80 102 100 108"
                        stroke="#ccfbf1"
                        strokeWidth="1.5"
                        fill="none"
                        opacity="0.8"
                      />
                      <path
                        d="M 65 116 Q 80 112 95 116"
                        stroke="#ccfbf1"
                        strokeWidth="1.2"
                        fill="none"
                        opacity="0.6"
                      />
                    </svg>
                  </div>

                  <div className="w-full bg-slate-800/80 rounded-lg p-2.5 mt-2 text-xs space-y-1">
                    <div className="flex justify-between text-slate-300">
                      <span>Diaphragm Elevation:</span>
                      <span className="font-bold text-teal-300">{abdominalGirthCm > 100 ? 'Significant (Orthopnea)' : 'Mild'}</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Refilling Velocity:</span>
                      <span className="font-bold text-amber-300">~1.5 to 2.0 L per week</span>
                    </div>
                  </div>
                </div>

                {/* Tunneled Indwelling Peritoneal Catheter (PleurX / Rocket) */}
                <div className="p-4 rounded-xl bg-teal-50 border border-teal-200 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-teal-950 uppercase tracking-wider">
                      Indwelling Peritoneal Catheter (PleurX)
                    </span>
                    <input
                      type="checkbox"
                      checked={indwellingCatheterCandidate}
                      onChange={(e) => setIndwellingCatheterCandidate(e.target.checked)}
                      className="w-4 h-4 text-teal-600 rounded"
                    />
                  </div>
                  <p className="text-xs text-teal-900 leading-relaxed font-medium">
                    For patients with rapid re-accumulation (&lt;10 days between taps): A tunneled silicone peritoneal catheter allows family or ASHA nurse to drain 1–1.5 L at home every 2–3 days without emergency hospital admissions.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: BILINGUAL FAMILY COUNSELING */}
          {activeTab === 'family_counseling' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                <div className="flex items-center gap-2 mb-1">
                  <IconUsers className="w-5 h-5 text-blue-700" />
                  <h3 className="text-sm font-bold text-blue-950">
                    Bilingual Family Guidance: Addressing the "Starvation Myth" (Hindi & English)
                  </h3>
                </div>
                <p className="text-xs text-blue-800">
                  Families often experience overwhelming guilt believing that lack of food is killing their loved one. Use these authentic, empathetic scripts to counsel family members.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Hindi Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="text-xs font-bold text-slate-800">🇮🇳 परिवार के लिए सांत्वना व मार्गदर्शन (Hindi)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">हिंदी</span>
                  </div>

                  <div className="space-y-2.5 text-xs text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">१. भूख न लगना स्वाभाविक प्रक्रिया है:</div>
                      <p className="text-[11px] text-emerald-800">
                        बीमारी के इस चरण में शरीर का मेटाबॉलिज्म धीमा हो जाता है। मरीज भूख से नहीं तड़प रहा है; उनका शरीर खाना पचाने की स्थिति में नहीं है।
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">२. जबरदस्ती खाना न खिलाएं:</div>
                      <p className="text-[11px] text-rose-800">
                        जबरन खिलाने से उल्टी, पेट फूलना और फेफड़ों में खाना अटकने (एस्पिरेशन) का गंभीर खतरा होता है। जो मरीज मांगे, सिर्फ वही थोड़ा सा दें।
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">३. मुंह की देखभाल (Comfort Care):</div>
                      <p className="text-[11px] text-amber-800">
                        खाने से ज्यादा जरूरी है मुंह को गीला रखना। बर्फ के छोटे टुकड़े, गीले रुई के फाहे या लिप बाम से होठों और जीभ को नम रखें।
                      </p>
                    </div>

                    <div className="p-2.5 bg-teal-50 rounded-lg border border-teal-200">
                      <div className="font-bold text-teal-900 mb-0.5">४. प्यार जताने के अन्य तरीके:</div>
                      <p className="text-[11px] text-teal-800">
                        हाथ थामना, सिर सहलाना, उनकी पसंदीदा बातें करना या संगीत सुनाना—यही सबसे बड़ी सेवा और सांत्वना है।
                      </p>
                    </div>
                  </div>
                </div>

                {/* English Card */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="text-xs font-bold text-slate-800">🇬🇧 Compassionate Clinical Script (English)</span>
                    <span className="text-[10px] bg-slate-100 px-2 py-0.5 rounded font-bold text-slate-600">English</span>
                  </div>

                  <div className="space-y-2.5 text-xs text-slate-700">
                    <div className="p-2.5 bg-emerald-50 rounded-lg border border-emerald-200">
                      <div className="font-bold text-emerald-900 mb-0.5">1. Anorexia is a Natural Metabolic Adaptation:</div>
                      <p className="text-[11px] text-emerald-800">
                        The cancer produces chemicals that tell the brain there is no hunger. The body is conserving energy and does not experience painful starving sensations.
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50 rounded-lg border border-rose-200">
                      <div className="font-bold text-rose-900 mb-0.5">2. The Harm of Forcible Feeding:</div>
                      <p className="text-[11px] text-rose-800">
                        Forcing food causes gastric stasis, painful bloating, nausea, and pulmonary aspiration. Artificial feeding tubes do not prolong life in advanced cachexia.
                      </p>
                    </div>

                    <div className="p-2.5 bg-amber-50 rounded-lg border border-amber-200">
                      <div className="font-bold text-amber-900 mb-0.5">3. Rigorous Mouth Care Relieves Thirst:</div>
                      <p className="text-[11px] text-amber-800">
                        Thirst is quenched by moistening the oral mucous membranes with wet swabs, ice chips, and petroleum jelly on lips, not by high-volume IV fluids.
                      </p>
                    </div>

                    <div className="p-2.5 bg-teal-50 rounded-lg border border-teal-200">
                      <div className="font-bold text-teal-900 mb-0.5">4. Nurturing Beyond Calories:</div>
                      <p className="text-[11px] text-teal-800">
                        Presence, gentle foot massage, holding hands, and peaceful conversation provide far deeper solace than pushing food.
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: HISTORY */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Abdominal Girth & Paracentesis Longitudinal Log</h3>
                <span className="text-xs text-slate-500">Kidwai Palliative Day Care</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date</th>
                      <th className="p-2.5">Girth (cm)</th>
                      <th className="p-2.5">Drainage Vol</th>
                      <th className="p-2.5">Albumin Infused</th>
                      <th className="p-2.5">Complications</th>
                      <th className="p-2.5">Clinician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-teal-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-bold text-teal-800">{abdominalGirthCm} cm</td>
                      <td className="p-2.5 font-medium">3.0 L Scheduled</td>
                      <td className="p-2.5">{saag < 1.1 ? 'Not required (Carcinomatosis)' : '20% Albumin 20g'}</td>
                      <td className="p-2.5 text-emerald-700 font-semibold">None (Hemodynamics stable)</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">04 Sep 2026</td>
                      <td className="p-2.5">91 cm</td>
                      <td className="p-2.5">3.5 L Drained</td>
                      <td className="p-2.5">None</td>
                      <td className="p-2.5 text-emerald-700">Immediate relief of dyspnea</td>
                      <td className="p-2.5 text-slate-600">Kidwai Day Care</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">21 Aug 2026</td>
                      <td className="p-2.5">88 cm</td>
                      <td className="p-2.5">2.8 L Drained</td>
                      <td className="p-2.5">None</td>
                      <td className="p-2.5 text-emerald-700">Transient post-drainage fatigue</td>
                      <td className="p-2.5 text-slate-600">Kidwai Day Care</td>
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
              {copiedToast ? '✓ Copied Cachexia & Ascites Protocol!' : '📋 Copy Cachexia & Ascites Protocol'}
            </button>
            <button
              onClick={handleDispatchOrder}
              className="px-4 py-2 bg-teal-700 hover:bg-teal-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Nutrition / Drainage Plan!' : '💧 Dispatch Paracentesis & Nutrition Plan'}</span>
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
