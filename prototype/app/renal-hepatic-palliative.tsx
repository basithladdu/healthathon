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

export interface OpioidProfile {
  name: string;
  renalStatus: 'safe' | 'caution' | 'contraindicated';
  hepaticStatus: 'safe' | 'caution' | 'contraindicated';
  activeMetabolites: string;
  eliminationRoute: string;
  recommendation: string;
  hindiRecommendation: string;
}

interface RenalHepaticPalliativeModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function RenalHepaticPalliativeModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: RenalHepaticPalliativeModalProps) {
  const [activeTab, setActiveTab] = useState<'organ_calculators' | 'opioid_matrix' | 'oin_protocol' | 'co_analgesics' | 'counseling'>('organ_calculators');

  // Patient Biomarkers for Renal Function (Cockcroft-Gault)
  const [serumCreatinine, setSerumCreatinine] = useState<number>(2.4); // mg/dL
  const [patientAge, setPatientAge] = useState<number>(66); // years
  const [patientWeightKg, setPatientWeightKg] = useState<number>(54); // kg
  const [patientGender, setPatientGender] = useState<'male' | 'female'>('female');

  // Biomarkers for Hepatic Function (Child-Pugh Score)
  const [bilirubinMgDl, setBilirubinMgDl] = useState<number>(2.6); // mg/dL
  const [albuminGDl, setAlbuminGDl] = useState<number>(2.6); // g/dL
  const [inr, setInr] = useState<number>(1.8);
  const [ascitesGrade, setAscitesGrade] = useState<'none' | 'mild' | 'moderate_tense'>('moderate_tense');
  const [encephalopathyGrade, setEncephalopathyGrade] = useState<'none' | 'grade_1_2' | 'grade_3_4'>('grade_1_2');

  // Active Offending Opioid for OIN screening
  const [selectedCurrentOpioid, setSelectedCurrentOpioid] = useState<'morphine' | 'oxycodone' | 'tramadol' | 'fentanyl'>('morphine');
  const [hasMyoclonus, setHasMyoclonus] = useState<boolean>(true);
  const [hasHyperalgesia, setHasHyperalgesia] = useState<boolean>(true);
  const [hasHallucinations, setHasHallucinations] = useState<boolean>(false);

  // Toast notifications
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // Calculate Cockcroft-Gault Creatinine Clearance (CrCl)
  const creatinineClearance = useMemo(() => {
    if (serumCreatinine <= 0) return 100;
    const factor = patientGender === 'female' ? 0.85 : 1.0;
    const crcl = ((140 - patientAge) * patientWeightKg * factor) / (72 * serumCreatinine);
    return Math.max(5, Number(crcl.toFixed(1)));
  }, [serumCreatinine, patientAge, patientWeightKg, patientGender]);

  // Renal CKD Stage
  const renalStage = useMemo(() => {
    if (creatinineClearance >= 90) return { stage: 'Stage 1 (Normal)', severity: 'normal', color: 'text-emerald-700 bg-emerald-50 border-emerald-200' };
    if (creatinineClearance >= 60) return { stage: 'Stage 2 (Mild)', severity: 'mild', color: 'text-lime-700 bg-lime-50 border-lime-200' };
    if (creatinineClearance >= 30) return { stage: 'Stage 3 (Moderate)', severity: 'moderate', color: 'text-amber-700 bg-amber-50 border-amber-200' };
    if (creatinineClearance >= 15) return { stage: 'Stage 4 (Severe Impairment)', severity: 'severe', color: 'text-orange-700 bg-orange-50 border-orange-200' };
    return { stage: 'Stage 5 (End-Stage / Anuria)', severity: 'end_stage', color: 'text-rose-700 bg-rose-50 border-rose-200' };
  }, [creatinineClearance]);

  // Calculate Child-Pugh Score
  const childPughScore = useMemo(() => {
    let score = 0;

    // Bilirubin
    if (bilirubinMgDl < 2.0) score += 1;
    else if (bilirubinMgDl <= 3.0) score += 2;
    else score += 3;

    // Albumin
    if (albuminGDl > 3.5) score += 1;
    else if (albuminGDl >= 2.8) score += 2;
    else score += 3;

    // INR
    if (inr < 1.7) score += 1;
    else if (inr <= 2.3) score += 2;
    else score += 3;

    // Ascites
    if (ascitesGrade === 'none') score += 1;
    else if (ascitesGrade === 'mild') score += 2;
    else score += 3;

    // Encephalopathy
    if (encephalopathyGrade === 'none') score += 1;
    else if (encephalopathyGrade === 'grade_1_2') score += 2;
    else score += 3;

    return score;
  }, [bilirubinMgDl, albuminGDl, inr, ascitesGrade, encephalopathyGrade]);

  // Child-Pugh Class
  const childPughClass = useMemo(() => {
    if (childPughScore <= 6) return { class: 'Class A (Compensated)', mortality: 'Low perioperative risk', color: 'text-emerald-700 bg-emerald-50 border-emerald-200' };
    if (childPughScore <= 9) return { class: 'Class B (Significant Impairment)', mortality: 'Moderate decompensation', color: 'text-amber-700 bg-amber-50 border-amber-200' };
    return { class: 'Class C (Decompensated Cirrhosis)', mortality: 'High 1-yr mortality (>50%)', color: 'text-rose-700 bg-rose-50 border-rose-200' };
  }, [childPughScore]);

  // Opioid Profiles & Organ Clearance Knowledgebase
  const opioidProfiles: OpioidProfile[] = [
    {
      name: 'Fentanyl (Transdermal / Subcutaneous)',
      renalStatus: 'safe',
      hepaticStatus: creatinineClearance < 30 && childPughScore >= 10 ? 'caution' : 'safe',
      activeMetabolites: 'None. Inactive norfentanyl.',
      eliminationRoute: 'Hepatic metabolism to inactive polar metabolites. Fecal & renal excretion of inert products.',
      recommendation: 'First-line preferred opioid in moderate-to-severe renal failure (CrCl < 30 mL/min). Minimal accumulation risk.',
      hindiRecommendation: 'गुर्दे की खराबी में सबसे सुरक्षित विकल्प। जहरीले अंश शरीर में नहीं जमते।',
    },
    {
      name: 'Buprenorphine (Transdermal / Sublingual)',
      renalStatus: 'safe',
      hepaticStatus: childPughScore >= 10 ? 'caution' : 'safe',
      activeMetabolites: 'Norbuprenorphine (minimal clinical activity).',
      eliminationRoute: 'Biliary & Fecal (approx 70%). Fecal elimination independent of renal filtration.',
      recommendation: 'Safest choice in dialysis and end-stage renal disease (CrCl < 15 mL/min). No dose adjustment needed for renal failure.',
      hindiRecommendation: 'डायलिसिस और गंभीर गुर्दा विफलता में सर्वोत्तम दवा। खुराक घटाने की आवश्यकता नहीं।',
    },
    {
      name: 'Morphine (Oral / Subcutaneous / IV)',
      renalStatus: creatinineClearance < 30 ? 'contraindicated' : creatinineClearance < 60 ? 'caution' : 'safe',
      hepaticStatus: childPughScore >= 7 ? 'caution' : 'safe',
      activeMetabolites: 'M6G (profound respiratory arrest) & M3G (severe neurotoxicity, myoclonus, seizures).',
      eliminationRoute: 'Hepatic glucuronidation $\\rightarrow$ active polar glucuronides cleared 90% by kidneys.',
      recommendation: creatinineClearance < 30
        ? 'AVOID / CONTRAINDICATED in CrCl < 30 mL/min. Lethal accumulation of M3G/M6G triggers myoclonus, hyperalgesia, and coma.'
        : 'Use with caution. Extend dosing intervals (q6h or q8h instead of q4h).',
      hindiRecommendation: 'गुर्दे कमजोर होने पर सख्त मना! दिमाग में जहर की तरह जमा होकर झटके और बेहोशी लाती है।',
    },
    {
      name: 'Oxycodone (Oral / SC)',
      renalStatus: creatinineClearance < 30 ? 'caution' : 'safe',
      hepaticStatus: childPughScore >= 7 ? 'caution' : 'safe',
      activeMetabolites: 'Noroxycodone & Oxymorphone (accumulate in severe renal failure).',
      eliminationRoute: 'Hepatic CYP3A4 & CYP2D6 metabolism; partial renal clearance of metabolites.',
      recommendation: creatinineClearance < 30
        ? 'Reduce starting dose by 50% and extend interval. Second-line to Fentanyl/Buprenorphine.'
        : 'Well tolerated with normal or mild impairment.',
      hindiRecommendation: 'खुराक आधी करें और निगरानी रखें। फेंटानिल से कम सुरक्षित।',
    },
    {
      name: 'Tramadol / Codeine',
      renalStatus: 'contraindicated',
      hepaticStatus: 'contraindicated',
      activeMetabolites: 'M1 (O-desmethyltramadol) active opioid metabolite; renal clearance.',
      eliminationRoute: 'Hepatic activation followed by mandatory renal excretion.',
      recommendation: 'ABSOLUTE CONTRAINDICATION in CrCl < 30 or Child-Pugh B/C. Unpredictable accumulation causes fatal respiratory depression and seizures.',
      hindiRecommendation: 'पूरी तरह प्रतिबंधित! फेफड़े रुकने और मिर्गी के दौरे पड़ने का जानलेवा खतरा।',
    },
    {
      name: 'Methadone',
      renalStatus: 'safe',
      hepaticStatus: childPughScore >= 10 ? 'contraindicated' : 'caution',
      activeMetabolites: 'None. Inactive EDDP.',
      eliminationRoute: 'Fecal/biliary elimination predominates. Highly lipophilic with tissue reservoir.',
      recommendation: 'Safe in renal impairment, but requires specialist palliative titration due to long variable half-life.',
      hindiRecommendation: 'गुर्दे के लिए सुरक्षित, परंतु केवल विशेषज्ञ डॉक्टर की सीधी देखरेख में ही दें।',
    },
  ];

  // Opioid-Induced Neurotoxicity (OIN) Severity
  const oinScore = (hasMyoclonus ? 2 : 0) + (hasHyperalgesia ? 2 : 0) + (hasHallucinations ? 2 : 0);
  const isOinPositive = oinScore >= 2 && (selectedCurrentOpioid === 'morphine' || selectedCurrentOpioid === 'oxycodone');

  // Copy Plan
  const handleCopyPlan = () => {
    const summary = `RENAL & HEPATIC PALLIATIVE ONCOLOGY ASSESSMENT
Patient: ${patientName} • ${primaryCancer} • Hospital ID: #${patientId}

1. ORGAN IMPAIRMENT METRICS:
- Cockcroft-Gault CrCl: ${creatinineClearance} mL/min (${renalStage.stage})
- Child-Pugh Score: ${childPughScore}/15 (${childPughClass.class})

2. OPIOID ROTATION & SAFETY RECOMMENDATION:
- Offending Opioid: ${selectedCurrentOpioid.toUpperCase()}
- Current Status: ${creatinineClearance < 30 && (selectedCurrentOpioid === 'morphine' || selectedCurrentOpioid === 'tramadol') ? 'DANGEROUS ACCUMULATION DETECTED' : 'Monitored'}
- Safe Replacement: Fentanyl Transdermal Patch or Sublingual Buprenorphine (Zero active renal metabolites).
- OIN Assessment: ${isOinPositive ? 'POSITIVE FOR OPIOID-INDUCED NEUROTOXICITY (OIN)' : 'Negative'}

3. OIN MANAGEMENT PROTOCOL:
- Discontinue Morphine/Codeine/Tramadol immediately.
- Opioid Switch to Fentanyl or Buprenorphine with 30–50% cross-tolerance reduction.
- Hydration: Gentle SC hypodermoclysis 500–1000 mL/24h to clear polar glucuronides.
- Myoclonus Control: Clonazepam 0.5–1.0 mg PO/SC or Midazolam 2.5–5.0 mg SC PRN.

4. CO-ANALGESIC ADJUSTMENTS:
- Paracetamol: Max 2 g/day in Child-Pugh B/C cirrhosis (avoid glutathione depletion).
- Gabapentin / Pregabalin: Reduce to 100–300 mg once daily or alternate days (100% renally cleared).
- NSAIDs: STRICTLY CONTRAINDICATED in CrCl < 60 or cirrhosis (risk of acute renal shutdown & variceal bleed).`;

    navigator.clipboard.writeText(summary);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  // Dispatch Plan
  const handleDispatchPlan = () => {
    const summary = `Renal/Hepatic Protocol Dispatched: CrCl ${creatinineClearance}mL/min (${renalStage.severity}), Child-Pugh ${childPughScore}/15. Opioid switched to Fentanyl/Buprenorphine with renal co-analgesic adjustments.`;
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
        <div className="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 text-white p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-white/15 rounded-xl backdrop-blur-md text-white border border-white/20">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Palliative Renal & Hepatic Opioid Safety Suite</h2>
                <span className="px-2 py-0.5 bg-white/20 rounded-full text-[11px] font-semibold tracking-wide uppercase">
                  Organ Clearance Engine
                </span>
              </div>
              <p className="text-xs text-indigo-200 mt-0.5">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientAge}y, {patientGender}, {patientWeightKg}kg) • {primaryCancer} • ID: #{patientId}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-[11px] text-indigo-200 font-medium">CrCl: {creatinineClearance} mL/min</div>
              <div className="text-sm font-extrabold text-white">Child-Pugh: {childPughScore}/15 ({childPughClass.class.split(' ')[0]} {childPughClass.class.split(' ')[1]})</div>
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
            onClick={() => setActiveTab('organ_calculators')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'organ_calculators'
                ? 'border-indigo-600 text-indigo-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconSparkles className="w-4 h-4" />
            <span>1. Organ Clearance Calculators (CrCl & Child-Pugh)</span>
          </button>
          <button
            onClick={() => setActiveTab('opioid_matrix')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'opioid_matrix'
                ? 'border-indigo-600 text-indigo-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldCheck className="w-4 h-4" />
            <span>2. Opioid Clearance & Toxicity Matrix</span>
          </button>
          <button
            onClick={() => setActiveTab('oin_protocol')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'oin_protocol'
                ? 'border-indigo-600 text-indigo-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldAlert className="w-4 h-4" />
            <span>3. Opioid Neurotoxicity (OIN) Rapid Action</span>
          </button>
          <button
            onClick={() => setActiveTab('co_analgesics')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'co_analgesics'
                ? 'border-indigo-600 text-indigo-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconClock className="w-4 h-4" />
            <span>4. Co-Analgesic Renal Adjustments (Gabapentin/NSAIDs)</span>
          </button>
          <button
            onClick={() => setActiveTab('counseling')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'counseling'
                ? 'border-indigo-600 text-indigo-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconUsers className="w-4 h-4" />
            <span>5. Family Reassurance & Counseling (द्विभाषी)</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 bg-slate-50/40">
          
          {/* Quick Organ Alert Header */}
          <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-xs mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Renal Cockcroft-Gault:</span>
                <span className="font-black text-indigo-900 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200">
                  {creatinineClearance} mL/min
                </span>
                <span className={`px-2 py-0.5 rounded text-[11px] font-bold border ${renalStage.color}`}>
                  {renalStage.stage}
                </span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Hepatic Child-Pugh:</span>
                <span className="font-black text-indigo-900 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200">
                  Score {childPughScore} / 15
                </span>
                <span className={`px-2 py-0.5 rounded text-[11px] font-bold border ${childPughClass.color}`}>
                  {childPughClass.class}
                </span>
              </div>
            </div>

            {creatinineClearance < 30 && (
              <span className="px-2.5 py-1 rounded-full text-xs font-black bg-rose-100 text-rose-800 border border-rose-300 animate-pulse">
                ⚠️ Severe Renal Risk: AVOID Morphine / Tramadol
              </span>
            )}
          </div>

          {/* TAB 1: ORGAN CLEARANCE CALCULATORS */}
          {activeTab === 'organ_calculators' && (
            <div className="space-y-6">
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* 1. Cockcroft-Gault Renal Calculator */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="flex items-center justify-between border-b pb-3">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">1. Renal Function (Cockcroft-Gault CrCl)</h3>
                      <p className="text-xs text-slate-500">Estimates glomerular filtration rate for renal opioid clearance</p>
                    </div>
                    <span className="text-xs font-bold text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded-lg border border-indigo-200">
                      eGFR: {creatinineClearance} mL/min
                    </span>
                  </div>

                  {/* Input Fields */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Serum Creatinine (mg/dL):</label>
                      <input
                        type="number"
                        step="0.1"
                        min="0.4"
                        max="10.0"
                        value={serumCreatinine}
                        onChange={(e) => setSerumCreatinine(Math.max(0.2, Number(e.target.value)))}
                        className="w-full p-2 border border-slate-300 rounded-lg font-bold text-slate-900 text-center"
                      />
                    </div>

                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Patient Age (years):</label>
                      <input
                        type="number"
                        min="18"
                        max="100"
                        value={patientAge}
                        onChange={(e) => setPatientAge(Math.max(18, Number(e.target.value)))}
                        className="w-full p-2 border border-slate-300 rounded-lg font-bold text-slate-900 text-center"
                      />
                    </div>

                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Body Weight (kg):</label>
                      <input
                        type="number"
                        min="30"
                        max="150"
                        value={patientWeightKg}
                        onChange={(e) => setPatientWeightKg(Math.max(30, Number(e.target.value)))}
                        className="w-full p-2 border border-slate-300 rounded-lg font-bold text-slate-900 text-center"
                      />
                    </div>

                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Biological Gender:</label>
                      <div className="grid grid-cols-2 gap-1 mt-1">
                        <button
                          onClick={() => setPatientGender('female')}
                          className={`py-1.5 rounded-lg border text-xs font-bold transition ${
                            patientGender === 'female' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-50 text-slate-700'
                          }`}
                        >
                          Female (0.85)
                        </button>
                        <button
                          onClick={() => setPatientGender('male')}
                          className={`py-1.5 rounded-lg border text-xs font-bold transition ${
                            patientGender === 'male' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-50 text-slate-700'
                          }`}
                        >
                          Male (1.0)
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Renal Staging Callout */}
                  <div className={`p-4 rounded-xl border ${renalStage.color} space-y-1`}>
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold">Calculated Creatinine Clearance:</span>
                      <span className="font-black text-sm">{creatinineClearance} mL/min</span>
                    </div>
                    <p className="text-[11px] leading-relaxed">
                      {creatinineClearance < 30 ? (
                        <span className="font-bold text-rose-800">
                          CRITICAL RENAL CLEARANCE IMPAIRMENT: Active glucuronide metabolites (M3G, M6G) cannot be filtered by kidneys. High risk of fatal respiratory arrest and myoclonic encephalopathy.
                        </span>
                      ) : creatinineClearance < 60 ? (
                        <span className="font-semibold text-amber-800">
                          MODERATE RENAL INSUFFICIENCY: Requires dose reduction or dosing interval extension for renally excreted drugs.
                        </span>
                      ) : (
                        <span className="text-emerald-800">
                          NORMAL RENAL FUNCTION: Standard opioid clearance kinetics apply.
                        </span>
                      )}
                    </p>
                  </div>
                </div>

                {/* 2. Child-Pugh Hepatic Calculator */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="flex items-center justify-between border-b pb-3">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">2. Hepatic Impairment (Child-Pugh Score)</h3>
                      <p className="text-xs text-slate-500">Staging cirrhosis and malignant liver metastases for drug metabolism</p>
                    </div>
                    <span className="text-xs font-bold text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded-lg border border-indigo-200">
                      Score: {childPughScore} / 15
                    </span>
                  </div>

                  {/* Hepatic Biomarkers */}
                  <div className="space-y-2.5 text-xs">
                    <div className="grid grid-cols-3 gap-2">
                      <div>
                        <label className="font-bold text-slate-700 block mb-1">Total Bilirubin (mg/dL):</label>
                        <input
                          type="number"
                          step="0.1"
                          min="0.2"
                          max="20.0"
                          value={bilirubinMgDl}
                          onChange={(e) => setBilirubinMgDl(Math.max(0.1, Number(e.target.value)))}
                          className="w-full p-2 border border-slate-300 rounded-lg font-bold text-center"
                        />
                      </div>

                      <div>
                        <label className="font-bold text-slate-700 block mb-1">Serum Albumin (g/dL):</label>
                        <input
                          type="number"
                          step="0.1"
                          min="1.0"
                          max="5.5"
                          value={albuminGDl}
                          onChange={(e) => setAlbuminGDl(Math.max(1.0, Number(e.target.value)))}
                          className="w-full p-2 border border-slate-300 rounded-lg font-bold text-center"
                        />
                      </div>

                      <div>
                        <label className="font-bold text-slate-700 block mb-1">INR (Coagulation):</label>
                        <input
                          type="number"
                          step="0.1"
                          min="0.8"
                          max="6.0"
                          value={inr}
                          onChange={(e) => setInr(Math.max(0.8, Number(e.target.value)))}
                          className="w-full p-2 border border-slate-300 rounded-lg font-bold text-center"
                        />
                      </div>
                    </div>

                    {/* Ascites Grade */}
                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Ascites Staging:</label>
                      <div className="grid grid-cols-3 gap-1.5">
                        {[
                          { id: 'none', label: 'None (1 pt)' },
                          { id: 'mild', label: 'Mild / Meds (2 pts)' },
                          { id: 'moderate_tense', label: 'Tense / Refractory (3 pts)' },
                        ].map((item) => (
                          <button
                            key={item.id}
                            onClick={() => setAscitesGrade(item.id as any)}
                            className={`p-1.5 rounded-lg border text-xs font-semibold transition ${
                              ascitesGrade === item.id ? 'bg-indigo-600 text-white border-indigo-600 font-bold' : 'bg-slate-50 text-slate-700'
                            }`}
                          >
                            {item.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Encephalopathy Grade */}
                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Hepatic Encephalopathy:</label>
                      <div className="grid grid-cols-3 gap-1.5">
                        {[
                          { id: 'none', label: 'None (1 pt)' },
                          { id: 'grade_1_2', label: 'Grade 1–2 Confusion (2 pts)' },
                          { id: 'grade_3_4', label: 'Grade 3–4 Stupor (3 pts)' },
                        ].map((item) => (
                          <button
                            key={item.id}
                            onClick={() => setEncephalopathyGrade(item.id as any)}
                            className={`p-1.5 rounded-lg border text-xs font-semibold transition ${
                              encephalopathyGrade === item.id ? 'bg-indigo-600 text-white border-indigo-600 font-bold' : 'bg-slate-50 text-slate-700'
                            }`}
                          >
                            {item.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Hepatic Staging Callout */}
                  <div className={`p-4 rounded-xl border ${childPughClass.color} space-y-1`}>
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold">Child-Pugh Classification:</span>
                      <span className="font-black text-sm">{childPughClass.class}</span>
                    </div>
                    <p className="text-[11px] leading-relaxed">
                      First-pass metabolism of oral opioids is severely diminished in liver failure, dramatically increasing bioavailability and systemic toxicity. Reduce initial doses by 50% and extend intervals.
                    </p>
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 2: OPIOID CLEARANCE & TOXICITY MATRIX */}
          {activeTab === 'opioid_matrix' && (
            <div className="space-y-4">
              
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Palliative Opioid Clearance & Organ Safety Matrix</h3>
                  <p className="text-xs text-slate-500">
                    Real-time safety stratification tailored to patient&apos;s current CrCl ({creatinineClearance} mL/min) and Child-Pugh ({childPughScore})
                  </p>
                </div>
                <span className="text-xs text-slate-500 font-medium hidden sm:block">
                  Based on EAPC & APCA Palliative Guidelines
                </span>
              </div>

              {/* Opioid Cards Grid */}
              <div className="space-y-3">
                {opioidProfiles.map((drug) => {
                  const isRenalContraindicated = drug.renalStatus === 'contraindicated';
                  const isRenalCaution = drug.renalStatus === 'caution';
                  return (
                    <div
                      key={drug.name}
                      className={`p-4 rounded-xl border transition ${
                        isRenalContraindicated
                          ? 'bg-rose-50/70 border-rose-300 ring-1 ring-rose-400'
                          : isRenalCaution
                          ? 'bg-amber-50/60 border-amber-200'
                          : 'bg-white border-slate-200 shadow-xs'
                      }`}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-2.5 mb-2.5">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-black text-slate-900">{drug.name}</span>
                          {isRenalContraindicated && (
                            <span className="px-2 py-0.5 rounded bg-rose-200 text-rose-900 font-black text-[10px] uppercase">
                              ⛔ CONTRAINDICATED (CrCl &lt; 30)
                            </span>
                          )}
                          {isRenalCaution && (
                            <span className="px-2 py-0.5 rounded bg-amber-200 text-amber-900 font-bold text-[10px] uppercase">
                              ⚠️ Dose Reduction Required
                            </span>
                          )}
                          {drug.renalStatus === 'safe' && (
                            <span className="px-2 py-0.5 rounded bg-emerald-200 text-emerald-900 font-bold text-[10px] uppercase">
                              ✓ Safe / First-Line
                            </span>
                          )}
                        </div>

                        <div className="flex items-center gap-2 text-xs">
                          <span className="font-semibold text-slate-500">Renal:</span>
                          <span className={`font-bold capitalize ${
                            drug.renalStatus === 'safe' ? 'text-emerald-700' : drug.renalStatus === 'caution' ? 'text-amber-700' : 'text-rose-700'
                          }`}>
                            {drug.renalStatus}
                          </span>
                          <span className="text-slate-300">|</span>
                          <span className="font-semibold text-slate-500">Hepatic:</span>
                          <span className={`font-bold capitalize ${
                            drug.hepaticStatus === 'safe' ? 'text-emerald-700' : drug.hepaticStatus === 'caution' ? 'text-amber-700' : 'text-rose-700'
                          }`}>
                            {drug.hepaticStatus}
                          </span>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                        <div>
                          <span className="font-bold text-slate-700 block">Metabolites & Elimination Route:</span>
                          <p className="text-[11px] text-slate-600 mt-0.5"><b>Metabolites:</b> {drug.activeMetabolites}</p>
                          <p className="text-[11px] text-slate-500 mt-0.5"><b>Elimination:</b> {drug.eliminationRoute}</p>
                        </div>

                        <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                          <span className="font-bold text-slate-800 block">Clinical Prescribing Directive:</span>
                          <p className="text-xs text-slate-900 font-medium mt-0.5">{drug.recommendation}</p>
                          <p className="text-[11px] text-slate-600 italic mt-1 font-medium">{drug.hindiRecommendation}</p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

            </div>
          )}

          {/* TAB 3: OPIOID NEUROTOXICITY (OIN) RAPID ACTION */}
          {activeTab === 'oin_protocol' && (
            <div className="space-y-5">
              
              {/* Diagnostic Checklist */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                <div className="flex items-center justify-between border-b pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Opioid-Induced Neurotoxicity (OIN) Screening</h3>
                    <p className="text-xs text-slate-500">Rapid assessment for active toxic polar metabolite (M3G / M6G) encephalopathy</p>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold border ${
                    isOinPositive ? 'bg-rose-100 text-rose-800 border-rose-300' : 'bg-emerald-100 text-emerald-800 border-emerald-300'
                  }`}>
                    {isOinPositive ? '⚠️ OIN Suspected / Positive' : 'No Acute Neurotoxicity'}
                  </span>
                </div>

                {/* Offending Opioid Selection */}
                <div className="text-xs space-y-1.5">
                  <label className="font-bold text-slate-700 block">Current Primary Opioid Being Administered:</label>
                  <div className="grid grid-cols-4 gap-2">
                    {[
                      { id: 'morphine', name: 'Morphine (M3G/M6G)', risk: 'Extreme Renal Risk' },
                      { id: 'oxycodone', name: 'Oxycodone', risk: 'Moderate Risk' },
                      { id: 'tramadol', name: 'Tramadol / Codeine', risk: 'Severe Risk' },
                      { id: 'fentanyl', name: 'Fentanyl / Buprenorphine', risk: 'Minimal Risk' },
                    ].map((op) => (
                      <button
                        key={op.id}
                        onClick={() => setSelectedCurrentOpioid(op.id as any)}
                        className={`p-2.5 rounded-lg border text-left text-xs transition ${
                          selectedCurrentOpioid === op.id
                            ? 'bg-indigo-50 border-indigo-600 text-indigo-950 font-bold ring-1 ring-indigo-500'
                            : 'bg-slate-50 border-slate-200 text-slate-700'
                        }`}
                      >
                        <div>{op.name}</div>
                        <div className="text-[10px] text-slate-500 font-normal mt-0.5">{op.risk}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Symptom Checkboxes */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  <div
                    onClick={() => setHasMyoclonus(!hasMyoclonus)}
                    className={`p-3 rounded-xl border cursor-pointer transition ${
                      hasMyoclonus ? 'bg-rose-50/70 border-rose-300 text-rose-950 font-bold' : 'bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span>1. Myoclonus (मांसपेशियों के झटके)</span>
                      <input type="checkbox" checked={hasMyoclonus} onChange={() => {}} className="accent-rose-600" />
                    </div>
                    <p className="text-[11px] text-slate-600 font-normal">Sudden involuntary jerks or twitching of limbs, fingers, or trunk.</p>
                  </div>

                  <div
                    onClick={() => setHasHyperalgesia(!hasHyperalgesia)}
                    className={`p-3 rounded-xl border cursor-pointer transition ${
                      hasHyperalgesia ? 'bg-rose-50/70 border-rose-300 text-rose-950 font-bold' : 'bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span>2. Paradoxical Hyperalgesia (दर्द बढ़ना)</span>
                      <input type="checkbox" checked={hasHyperalgesia} onChange={() => {}} className="accent-rose-600" />
                    </div>
                    <p className="text-[11px] text-slate-600 font-normal">Increased diffuse pain despite escalating doses; painful response to light touch (allodynia).</p>
                  </div>

                  <div
                    onClick={() => setHasHallucinations(!hasHallucinations)}
                    className={`p-3 rounded-xl border cursor-pointer transition ${
                      hasHallucinations ? 'bg-rose-50/70 border-rose-300 text-rose-950 font-bold' : 'bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span>3. Delirium / Hallucinations (मतिभ्रम)</span>
                      <input type="checkbox" checked={hasHallucinations} onChange={() => {}} className="accent-rose-600" />
                    </div>
                    <p className="text-[11px] text-slate-600 font-normal">Vivid visual hallucinations, plucking at bedsheets, agitation or cognitive disorientation.</p>
                  </div>
                </div>
              </div>

              {/* 4-Step Action Protocol */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-3">
                <h4 className="text-xs font-bold text-slate-900 uppercase">Emergency 4-Step OIN Rescue Protocol</h4>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="font-bold text-rose-900 block mb-0.5">Step 1: STOP Offending Opioid</span>
                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      Immediately withhold Morphine or Oxycodone. Do not increase the dose to treat paradoxical hyperalgesia!
                    </p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="font-bold text-indigo-900 block mb-0.5">Step 2: Opioid Rotation to Fentanyl</span>
                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      Switch to Transdermal Fentanyl (12.5–25 mcg/hr) or Sublingual Buprenorphine (200–400 mcg TDS) with a 30–50% cross-tolerance reduction.
                    </p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="font-bold text-teal-900 block mb-0.5">Step 3: Gentle Hydration</span>
                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      Initiate subcutaneous hypodermoclysis (500–1000 mL Normal Saline over 24h) to enhance renal elimination of accumulated polar glucuronide metabolites (if patient not in pulmonary edema).
                    </p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="font-bold text-amber-900 block mb-0.5">Step 4: Symptomatic Myoclonus Control</span>
                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      Administer <b>Clonazepam 0.5–1.0 mg PO/SC</b> nocte or <b>Midazolam 2.5–5.0 mg SC PRN</b> for disabling involuntary myoclonic jerks.
                    </p>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 4: CO-ANALGESIC RENAL ADJUSTMENTS */}
          {activeTab === 'co_analgesics' && (
            <div className="space-y-4">
              
              <div className="border border-slate-200 rounded-xl overflow-hidden bg-white shadow-xs">
                <div className="bg-slate-100 p-3 text-xs font-bold text-slate-700 uppercase">
                  Non-Opioid & Co-Analgesic Renal/Hepatic Dosing Guidelines
                </div>

                <div className="divide-y divide-slate-200 text-xs">
                  {/* Paracetamol */}
                  <div className="p-4 space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-900 text-sm">Paracetamol (Acetaminophen)</span>
                      <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-bold text-xs">
                        Renal: Safe • Hepatic: Max 2g/day
                      </span>
                    </div>
                    <p className="text-slate-600 text-[11px]">
                      Completely safe in all stages of renal failure. In severe cirrhosis (Child-Pugh B or C), glutathione stores are depleted; restrict maximum total daily dose to <b>2.0 grams / 24h</b> (500 mg QDS) to avoid hepatotoxicity.
                    </p>
                  </div>

                  {/* Gabapentin */}
                  <div className="p-4 space-y-1 bg-amber-50/40">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-amber-950 text-sm">Gabapentin / Pregabalin (Neuropathic Agents)</span>
                      <span className="px-2 py-0.5 rounded bg-amber-200 text-amber-900 font-bold text-xs">
                        MANDATORY Renal Dose Reduction
                      </span>
                    </div>
                    <p className="text-amber-900 text-[11px]">
                      <b>100% renally cleared unchanged!</b> In CrCl &lt; 30 mL/min, standard doses cause severe somnolence, respiratory arrest, and coma.
                    </p>
                    <div className="grid grid-cols-3 gap-2 mt-2 text-[11px] font-medium bg-white p-2 rounded border border-amber-200">
                      <div><b>CrCl &gt; 60:</b> 300 mg TDS</div>
                      <div><b>CrCl 30–59:</b> 300 mg BD</div>
                      <div className="text-rose-700"><b>CrCl &lt; 30:</b> 100–300 mg ON or alternate days</div>
                    </div>
                  </div>

                  {/* NSAIDs */}
                  <div className="p-4 space-y-1 bg-rose-50/50">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-rose-950 text-sm">NSAIDs (Diclofenac, Ibuprofen, Naproxen)</span>
                      <span className="px-2 py-0.5 rounded bg-rose-200 text-rose-900 font-black text-xs">
                        STRICTLY CONTRAINDICATED
                      </span>
                    </div>
                    <p className="text-rose-900 text-[11px]">
                      Inhibits renal prostaglandins, precipitating acute renal shutdown and hyperkalemia. In cirrhosis, triggers hepatorenal syndrome and fatal variceal upper GI hemorrhage.
                    </p>
                  </div>

                  {/* Bisphosphonates */}
                  <div className="p-4 space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-900 text-sm">Zoledronic Acid / Pamidronate</span>
                      <span className="px-2 py-0.5 rounded bg-orange-100 text-orange-800 font-bold text-xs">
                        Hold if CrCl &lt; 30 mL/min
                      </span>
                    </div>
                    <p className="text-slate-600 text-[11px]">
                      Direct tubular nephrotoxicity. If bone metastases present with CrCl &lt; 30, switch to <b>Denosumab (RANK-L inhibitor)</b> which does not require renal dose adjustment (monitor calcium closely).
                    </p>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 5: BILINGUAL FAMILY COUNSELING */}
          {activeTab === 'counseling' && (
            <div className="space-y-4">
              
              <div className="p-4 rounded-xl bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-blue-900 text-sm">Bilingual Family Explanation Scripts (द्विभाषी मार्गदर्शन)</span>
                  <span className="px-2 py-0.5 bg-blue-200 text-blue-800 rounded text-[10px] font-bold">Kidney & Liver Care</span>
                </div>
                <p className="text-xs text-blue-950">
                  Families frequently misinterpret involuntary myoclonic twitches as brain metastases or agony, and worry when morphine is switched to patches. Use these validated phrases to provide reassurance.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* 1. Explaining the Switch from Morphine to Skin Patch */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">1. Why We Are Changing the Pain Medication</span>
                    <span className="text-[10px] text-slate-500 font-semibold">Morphine $\rightarrow$ Patch</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Phrasing (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5">
                        &quot;मरीज के गुर्दे (किडनी) इस समय कमजोर हैं, इसलिए मॉर्फिन गोली शरीर से बाहर नहीं निकल पा रही और खून में जमा हो रही है। हम दर्द की दवा बंद नहीं कर रहे हैं, बल्कि चमड़ी पर चिपकने वाला ऐसा पैच (फेंटानिल) लगा रहे हैं जो गुर्दों पर कोई असर नहीं डालता और दर्द से पूरा आराम देता है।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>English:</b> Reassures family that the change is made for safety and organ protection, not because cancer is untreatable.
                    </p>
                  </div>
                </div>

                {/* 2. Explaining Involuntary Twitches (Myoclonus) */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">2. Explaining Muscle Twitches (Myoclonus)</span>
                    <span className="text-[10px] text-slate-500 font-semibold">Reassuring Agitation</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-2">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Phrasing (हिंदी):</span>
                      <p className="text-xs text-slate-900 italic mt-0.5">
                        &quot;हाथ-पैर में जो अचानक झटके लग रहे हैं, यह कोई नया दौरा या दिमागी कैंसर नहीं है। यह पुरानी दवाई के कुछ अंश जमा होने की वजह से मांसपेशियों का खिंचाव है। हमने दवा बदल दी है और झटकों को शांत करने के लिए हल्की दवा दे रहे हैं, यह धीरे-धीरे ठीक हो जाएगा।&quot;
                      </p>
                    </div>
                    <p className="text-[11px] text-slate-600">
                      <b>English:</b> Demystifies myoclonus as a temporary drug metabolite effect that subsides with opioid rotation and gentle hydration.
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
              {copiedToast ? '✓ Copied Organ Plan!' : '📋 Copy Organ Clearance Protocol'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-indigo-700 hover:bg-indigo-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Organ Safety Plan!' : '🫘 Dispatch Renal Opioid Switch & Caregiver Alert'}</span>
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
