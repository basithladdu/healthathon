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
  IconBaby,
} from './icons';

export type AssessmentScale = 'flacc' | 'faces' | 'lansky';

export interface FlaccItem {
  id: 'face' | 'legs' | 'activity' | 'cry' | 'consolability';
  label: string;
  hindiLabel: string;
  options: {
    score: number;
    text: string;
    hindiText: string;
  }[];
}

interface PediatricPalliativeModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function PediatricPalliativeModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: PediatricPalliativeModalProps) {
  const [activeTab, setActiveTab] = useState<'pain_assessment' | 'dosing_engine' | 'symptom_protocols' | 'grief_siblings' | 'history'>('pain_assessment');
  const [activeScale, setActiveScale] = useState<AssessmentScale>('flacc');

  // Pediatric Patient Baseline
  const [childAge, setChildAge] = useState<number>(6); // years
  const [childWeightKg, setChildWeightKg] = useState<number>(18); // kg
  const [syrupConcentration, setSyrupConcentration] = useState<'2mg_ml' | '1mg_ml'>('2mg_ml'); // 10mg/5mL or 5mg/5mL
  const [morphineMgPerKg, setMorphineMgPerKg] = useState<number>(0.2); // mg/kg PO q4h

  // FLACC Behavioral Pain Scale
  const [flaccScores, setFlaccScores] = useState({
    face: 1,
    legs: 1,
    activity: 1,
    cry: 1,
    consolability: 1,
  });

  // Wong-Baker FACES Score (0, 2, 4, 6, 8, 10)
  const [facesScore, setFacesScore] = useState<number>(4);

  // Lansky Play-Performance Scale (10 to 100)
  const [lanskyScore, setLanskyScore] = useState<number>(50);

  // Notification / Toast states
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // FLACC definition items
  const flaccItems: FlaccItem[] = [
    {
      id: 'face',
      label: 'Face (चेहरा)',
      hindiLabel: 'चेहरे के हाव-भाव',
      options: [
        { score: 0, text: 'No particular expression or smile', hindiText: 'शांत या मुस्कुराता हुआ' },
        { score: 1, text: 'Occasional grimace/frown, withdrawn, disinterested', hindiText: 'कभी-कभार त्योरी चढ़ाना, उदास या असहज' },
        { score: 2, text: 'Frequent to constant quivering chin, clenched jaw', hindiText: 'लगातार जबड़े भींचना या ठुड्डी कांपना' },
      ],
    },
    {
      id: 'legs',
      label: 'Legs (पैर)',
      hindiLabel: 'पैरों की स्थिति',
      options: [
        { score: 0, text: 'Normal position or relaxed', hindiText: 'सामान्य या ढीले पैर' },
        { score: 1, text: 'Uneasy, restless, tense', hindiText: 'बेचैन, हिलते हुए या थोड़े अकड़े' },
        { score: 2, text: 'Kicking, or legs drawn up tightly to belly', hindiText: 'पटकना या पेट की तरफ कसकर खींचना' },
      ],
    },
    {
      id: 'activity',
      label: 'Activity (शारीरिक हरकत)',
      hindiLabel: 'गतिविधि एवं मुद्रा',
      options: [
        { score: 0, text: 'Lying quietly, normal position, moves easily', hindiText: 'आराम से लेटा हुआ, सहजता से हिलना' },
        { score: 1, text: 'Squirming, shifting back and forth, tense', hindiText: 'करवटें बदलना, छटपटाना, तनाव' },
        { score: 2, text: 'Arched, rigid or jerking stiffly', hindiText: 'धनुषाकार शरीर, अकड़न या झटके' },
      ],
    },
    {
      id: 'cry',
      label: 'Cry (रोना/आवाज)',
      hindiLabel: 'रोने का प्रकार',
      options: [
        { score: 0, text: 'No cry (awake or asleep)', hindiText: 'बिल्कुल नहीं रो रहा' },
        { score: 1, text: 'Moans or whimpers; occasional complaint', hindiText: 'सिसकना या हल्की कराहना' },
        { score: 2, text: 'Crying steadily, screams or sobs, frequent complaints', hindiText: 'लगातार चीखना या बिलख-बिलख कर रोना' },
      ],
    },
    {
      id: 'consolability',
      label: 'Consolability (सांत्वना)',
      hindiLabel: 'चुप कराने पर प्रतिक्रिया',
      options: [
        { score: 0, text: 'Content, relaxed, responds to voice', hindiText: 'संतुष्ट, बात करने पर शांत' },
        { score: 1, text: 'Reassured by touching, hugging, or talking to; distractible', hindiText: 'गोद लेने या बहलाने पर थोड़ी देर शांत' },
        { score: 2, text: 'Difficult to console or comfort; inconsolable', hindiText: 'चुप कराना अत्यधिक कठिन या असंभव' },
      ],
    },
  ];

  // Total FLACC score
  const totalFlaccScore = useMemo(() => {
    return flaccScores.face + flaccScores.legs + flaccScores.activity + flaccScores.cry + flaccScores.consolability;
  }, [flaccScores]);

  // Active pain score based on chosen scale
  const effectivePainScore = useMemo(() => {
    if (activeScale === 'flacc') return totalFlaccScore;
    if (activeScale === 'faces') return facesScore;
    // Map Lansky inverse (100 = 0 pain, 10 = 10 pain approx)
    return Math.round((100 - lanskyScore) / 10);
  }, [activeScale, totalFlaccScore, facesScore, lanskyScore]);

  // Pain Severity Category
  const painSeverity = useMemo(() => {
    if (effectivePainScore === 0) {
      return {
        level: 'Comfortable / No Pain',
        color: 'text-emerald-700 bg-emerald-50 border-emerald-200',
        badge: 'bg-emerald-100 text-emerald-800',
        recommendation: 'Continue supportive nursing and non-pharmacologic comforting.',
      };
    }
    if (effectivePainScore <= 3) {
      return {
        level: 'Mild Pain (हल्का दर्द)',
        color: 'text-lime-700 bg-lime-50 border-lime-200',
        badge: 'bg-lime-100 text-lime-800',
        recommendation: 'First-line: Paracetamol 15mg/kg PO q6h ± child distraction / play therapy.',
      };
    }
    if (effectivePainScore <= 6) {
      return {
        level: 'Moderate Pain (मध्यम दर्द)',
        color: 'text-amber-700 bg-amber-50 border-amber-200',
        badge: 'bg-amber-100 text-amber-800',
        recommendation: 'WHO Step 2/3: Initiate low-dose oral morphine 0.15–0.2 mg/kg q4h + regular paracetamol.',
      };
    }
    return {
      level: 'Severe Distress / Breakthrough Pain (गंभीर दर्द)',
      color: 'text-rose-700 bg-rose-50 border-rose-200',
      badge: 'bg-rose-100 text-rose-800',
      recommendation: 'Urgent: Morphine 0.25–0.3 mg/kg q4h with breakthrough rescue dosing and laxative co-prescription.',
    };
  }, [effectivePainScore]);

  // Lansky Play-Performance Descriptions
  const lanskyDescriptions: { [key: number]: { text: string; hindi: string } } = {
    100: { text: 'Fully active, normal play. No physical restrictions.', hindi: 'पूरी तरह सक्रिय, सामान्य खेलकूद।' },
    90: { text: 'Minor restrictions, physically energetic, plays vigorously.', hindi: 'मामूली प्रतिबंध, काफी ऊर्जावान।' },
    80: { text: 'Active, but tires more quickly. Periods of resting between play.', hindi: 'सक्रिय परंतु जल्दी थक जाता है।' },
    70: { text: 'Both greater restriction of and less time spent in play activity.', hindi: 'खेलकूद में स्पष्ट कमी और धीमापन।' },
    60: { text: 'Up and around, but minimal active play; keeps busy with quiet activities.', hindi: 'घूमता-फिरता है पर शांत गतिविधियों में व्यस्त रहता है।' },
    50: { text: 'Gets dressed but lies around much of the day; no active play; quiet play.', hindi: 'दिनभर बिस्तर या सोफे पर लेटा रहता है, केवल शांत खेल।' },
    40: { text: 'Mostly in bed; participates in quiet activities when supported.', hindi: 'अधिकांश समय बिस्तर पर, सहारा देने पर ही खेल।' },
    30: { text: 'In bed; needs assistance even for quiet passive play.', hindi: 'पूरा समय बिस्तर में, साधारण खेल के लिए भी सहायता आवश्यक।' },
    20: { text: 'Often sleeping; play entirely limited to passive screen or audio.', hindi: 'अक्सर सोता रहता है, सिर्फ सुनना या देखना।' },
    10: { text: 'No play; does not get out of bed; severe debilitation.', hindi: 'कोई खेल नहीं, अत्यधिक दुर्बलता, बिस्तर से नहीं उठ सकता।' },
  };

  // Weight-based dosing calculations
  const concentrationMgPerMl = syrupConcentration === '2mg_ml' ? 2.0 : 1.0;
  const singleMorphineDoseMg = Number((childWeightKg * morphineMgPerKg).toFixed(2));
  const singleMorphineVolumeMl = Number((singleMorphineDoseMg / concentrationMgPerMl).toFixed(2));
  const dailyTotalMorphineMg = Number((singleMorphineDoseMg * 6).toFixed(2)); // q4h = 6 doses
  const breakthroughRescueDoseMg = Number((dailyTotalMorphineMg / 6).toFixed(2));
  const breakthroughRescueVolumeMl = Number((breakthroughRescueDoseMg / concentrationMgPerMl).toFixed(2));

  // Co-analgesic calculations
  const paracetamolDoseMg = Number((childWeightKg * 15).toFixed(0)); // 15 mg/kg
  const paracetamolMaxDailyMg = Math.min(childWeightKg * 60, 4000);
  const paracetamolSyrupMl = Number((paracetamolDoseMg / (120 / 5)).toFixed(1)); // standard 120mg/5mL syrup

  const ibuprofenDoseMg = Number((childWeightKg * 10).toFixed(0)); // 10 mg/kg
  const gabapentinStartDoseMg = Number((childWeightKg * 5).toFixed(0)); // 5 mg/kg/day start
  const gabapentinTargetDoseMg = Number((childWeightKg * 20).toFixed(0)); // 20 mg/kg/day target

  // Antiemetic Ondansetron
  const ondansetronDoseMg = Number((childWeightKg * 0.15).toFixed(1)); // 0.15 mg/kg

  // Glycopyrrolate for terminal secretions
  const glycopyrrolateDoseMcg = Number((childWeightKg * 6).toFixed(0)); // 6 mcg/kg = 0.006 mg/kg

  // Copy Plan
  const handleCopyPlan = () => {
    const summary = `PEDIATRIC PALLIATIVE ONCOLOGY PROTOCOL
Patient: ${patientName} (${childAge}y, ${childWeightKg}kg)
Diagnosis: ${primaryCancer}
Scale Assessed: ${activeScale.toUpperCase()} Score = ${effectivePainScore}/10 (${painSeverity.level})
Lansky Play Scale: ${lanskyScore}%

1. BASELINE OPIOID RX:
- Oral Morphine Solution (${syrupConcentration === '2mg_ml' ? '10mg/5mL = 2mg/mL' : '5mg/5mL = 1mg/mL'})
- Scheduled Dose: ${singleMorphineDoseMg} mg (${singleMorphineVolumeMl} mL) PO q4h around the clock.
- Breakthrough Dose: ${breakthroughRescueDoseMg} mg (${breakthroughRescueVolumeMl} mL) PO q1h PRN.
- Bowel Regimen: Pediatric Lactulose ${childWeightKg} mL PO once daily (1 mL/kg).

2. CO-ANALGESICS:
- Paracetamol Syrup (120mg/5mL): ${paracetamolDoseMg} mg (${paracetamolSyrupMl} mL) PO q6h PRN (Max ${paracetamolMaxDailyMg} mg/day).
- Ibuprofen Suspension (100mg/5mL): ${ibuprofenDoseMg} mg PO q8h with food (hold if thrombocytopenic).
- Gabapentin: Start ${gabapentinStartDoseMg} mg nocte, titrate to ${gabapentinTargetDoseMg} mg/day in 3 divided doses for neuropathic pain.

3. CRISIS & SYMPTOM MEDICATIONS:
- Ondansetron: ${ondansetronDoseMg} mg PO/IV q8h PRN for nausea.
- Glycopyrrolate: ${glycopyrrolateDoseMcg} mcg SC q6h for respiratory secretions / death rattle.

4. DEVELOPMENTAL & SIBLING SUPPORT:
- Age-adapted communication provided. No euphemisms like "went to sleep".
- Memory box and sibling inclusion active.`;

    navigator.clipboard.writeText(summary);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  // Dispatch Plan
  const handleDispatchPlan = () => {
    const summary = `Pediatric Palliative Dosing Dispatched: Morphine ${singleMorphineDoseMg}mg (${singleMorphineVolumeMl}mL) q4h for ${childWeightKg}kg child. FLACC/FACES ${effectivePainScore}/10, Lansky ${lanskyScore}%.`;
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
        <div className="bg-gradient-to-r from-pink-700 via-rose-700 to-amber-700 text-white p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-white/15 rounded-xl backdrop-blur-md text-white border border-white/20">
              <IconBaby className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Pediatric Palliative Oncology & Symptom Engine</h2>
                <span className="px-2 py-0.5 bg-white/20 rounded-full text-[11px] font-semibold tracking-wide uppercase">
                  Developmental Care
                </span>
              </div>
              <p className="text-xs text-rose-100 mt-0.5">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({childAge} yrs, {childWeightKg} kg) • {primaryCancer} • Hospital ID: #{patientId}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-[11px] text-rose-100 font-medium">Lansky Play Performance</div>
              <div className="text-sm font-extrabold text-white">{lanskyScore}% Functional</div>
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
            onClick={() => setActiveTab('pain_assessment')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'pain_assessment'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconHeartPulse className="w-4 h-4" />
            <span>1. Pain & Play Staging (FLACC / FACES / Lansky)</span>
          </button>
          <button
            onClick={() => setActiveTab('dosing_engine')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'dosing_engine'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconSparkles className="w-4 h-4" />
            <span>2. Weight-Based Opioid & Syringe Calibrator</span>
          </button>
          <button
            onClick={() => setActiveTab('symptom_protocols')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'symptom_protocols'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldCheck className="w-4 h-4" />
            <span>3. Pediatric Symptom Management</span>
          </button>
          <button
            onClick={() => setActiveTab('grief_siblings')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'grief_siblings'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconUsers className="w-4 h-4" />
            <span>4. Developmental Grief & Sibling Care (द्विभाषी)</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-rose-600 text-rose-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconClock className="w-4 h-4" />
            <span>5. Pediatric Trajectory Log</span>
          </button>
        </div>

        {/* Body Content */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 bg-slate-50/40">
          
          {/* Quick Patient Spec Bar */}
          <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-xs mb-5 flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-700">Child Age:</span>
                <input
                  type="number"
                  min="1"
                  max="18"
                  value={childAge}
                  onChange={(e) => setChildAge(Math.max(1, Math.min(18, Number(e.target.value))))}
                  className="w-16 px-2 py-1 border border-slate-300 rounded font-semibold text-center"
                />
                <span className="text-slate-500 font-medium">years</span>
              </div>

              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-700">Weight:</span>
                <input
                  type="number"
                  min="3"
                  max="70"
                  step="0.5"
                  value={childWeightKg}
                  onChange={(e) => setChildWeightKg(Math.max(3, Math.min(70, Number(e.target.value))))}
                  className="w-20 px-2 py-1 border border-slate-300 rounded font-semibold text-center text-rose-700"
                />
                <span className="text-slate-500 font-medium">kg</span>
              </div>

              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-700">Liquid Concentration:</span>
                <select
                  value={syrupConcentration}
                  onChange={(e) => setSyrupConcentration(e.target.value as any)}
                  className="px-2 py-1 border border-slate-300 rounded font-medium text-xs bg-slate-50"
                >
                  <option value="2mg_ml">10 mg / 5 mL (2 mg/mL) - Standard</option>
                  <option value="1mg_ml">5 mg / 5 mL (1 mg/mL) - Dilute</option>
                </select>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className={`px-2.5 py-1 rounded-full text-xs font-bold border ${painSeverity.color}`}>
                Score: {effectivePainScore} / 10 • {painSeverity.level}
              </span>
            </div>
          </div>

          {/* TAB 1: PAIN & PLAY ASSESSMENT */}
          {activeTab === 'pain_assessment' && (
            <div className="space-y-6">
              
              {/* Scale Selector Sub-Navigation */}
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Select Assessment Tool:</span>
                  <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 shadow-2xs">
                    <button
                      onClick={() => setActiveScale('flacc')}
                      className={`px-3 py-1.5 rounded-md text-xs font-bold transition ${
                        activeScale === 'flacc' ? 'bg-rose-600 text-white shadow-2xs' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      FLACC (Infants & Non-Verbal)
                    </button>
                    <button
                      onClick={() => setActiveScale('faces')}
                      className={`px-3 py-1.5 rounded-md text-xs font-bold transition ${
                        activeScale === 'faces' ? 'bg-rose-600 text-white shadow-2xs' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      Wong-Baker FACES (Verbal 3+)
                    </button>
                    <button
                      onClick={() => setActiveScale('lansky')}
                      className={`px-3 py-1.5 rounded-md text-xs font-bold transition ${
                        activeScale === 'lansky' ? 'bg-rose-600 text-white shadow-2xs' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      Lansky Play Scale (0–100%)
                    </button>
                  </div>
                </div>

                <div className="text-xs text-slate-500 font-medium">
                  {activeScale === 'flacc' && 'Validated for children 2 months to 7 years or non-verbal patients'}
                  {activeScale === 'faces' && 'Validated for verbal children aged 3 years and above'}
                  {activeScale === 'lansky' && 'Validated pediatric oncology functional performance index'}
                </div>
              </div>

              {/* SCALE 1: FLACC */}
              {activeScale === 'flacc' && (
                <div className="space-y-4">
                  <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-bold text-slate-900">FLACC Behavioral Pain Assessment (0–10)</h3>
                        <p className="text-xs text-slate-500">Assess Face, Legs, Activity, Cry, and Consolability by observing child for 1–5 minutes.</p>
                      </div>
                      <div className="text-right">
                        <span className="text-2xl font-black text-rose-700">{totalFlaccScore}</span>
                        <span className="text-xs font-bold text-slate-400"> / 10</span>
                      </div>
                    </div>

                    <div className="space-y-3">
                      {flaccItems.map((item) => (
                        <div key={item.id} className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-xs font-bold text-slate-800">{item.label}</span>
                            <span className="text-[11px] text-slate-500 font-medium">{item.hindiLabel}</span>
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                            {item.options.map((opt) => {
                              const isSelected = flaccScores[item.id] === opt.score;
                              return (
                                <button
                                  key={opt.score}
                                  onClick={() => setFlaccScores({ ...flaccScores, [item.id]: opt.score })}
                                  className={`text-left p-2.5 rounded-lg border text-xs transition ${
                                    isSelected
                                      ? 'border-rose-600 bg-rose-50/80 text-rose-950 font-semibold shadow-2xs ring-1 ring-rose-500'
                                      : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                                  }`}
                                >
                                  <div className="flex items-center justify-between mb-1">
                                    <span className="font-bold text-[11px] px-1.5 py-0.2 rounded bg-slate-200 text-slate-800">
                                      Score {opt.score}
                                    </span>
                                    {isSelected && <span className="text-rose-600 text-xs font-bold">✓ Selected</span>}
                                  </div>
                                  <div className="text-xs leading-tight mb-1">{opt.text}</div>
                                  <div className="text-[10px] text-slate-500 italic">{opt.hindiText}</div>
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* SCALE 2: WONG-BAKER FACES */}
              {activeScale === 'faces' && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                  <div className="flex items-center justify-between mb-4">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">Wong-Baker FACES® Pain Rating Scale</h3>
                      <p className="text-xs text-slate-500">Ask child: &quot;Point to each face and tell me which face feels like you right now?&quot;</p>
                    </div>
                    <div className="text-right">
                      <span className="text-2xl font-black text-rose-700">{facesScore}</span>
                      <span className="text-xs font-bold text-slate-400"> / 10</span>
                    </div>
                  </div>

                  {/* Interactive SVG Faces */}
                  <div className="grid grid-cols-2 sm:grid-cols-6 gap-3">
                    {[
                      { score: 0, label: 'No Hurt', hindi: 'बिल्कुल दर्द नहीं', color: '#10b981', mouth: 'M 15 25 Q 25 35 35 25', tear: false },
                      { score: 2, label: 'Hurts Little Bit', hindi: 'थोड़ा सा दर्द', color: '#84cc16', mouth: 'M 16 26 Q 25 31 34 26', tear: false },
                      { score: 4, label: 'Hurts Little More', hindi: 'थोड़ा और दर्द', color: '#eab308', mouth: 'M 17 27 L 33 27', tear: false },
                      { score: 6, label: 'Hurts Even More', hindi: 'काफी अधिक दर्द', color: '#f97316', mouth: 'M 16 30 Q 25 24 34 30', tear: false },
                      { score: 8, label: 'Hurts Whole Lot', hindi: 'बहुत तेज दर्द', color: '#ef4444', mouth: 'M 15 32 Q 25 22 35 32', tear: true },
                      { score: 10, label: 'Hurts Worst', hindi: 'असहनीय भयंकर दर्द', color: '#b91c1c', mouth: 'M 16 34 Q 25 18 34 34', tear: true },
                    ].map((f) => {
                      const isSelected = facesScore === f.score;
                      return (
                        <button
                          key={f.score}
                          onClick={() => setFacesScore(f.score)}
                          className={`p-3 rounded-xl border flex flex-col items-center text-center transition ${
                            isSelected
                              ? 'border-rose-600 bg-rose-50/70 shadow-sm ring-2 ring-rose-500'
                              : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100 hover:border-slate-300'
                          }`}
                        >
                          {/* Face SVG */}
                          <svg className="w-16 h-16 mb-2" viewBox="0 0 50 50">
                            {/* Head */}
                            <circle cx="25" cy="25" r="22" fill={isSelected ? '#fff' : '#fafafa'} stroke={f.color} strokeWidth="2.5" />
                            
                            {/* Eyes */}
                            {f.score <= 4 ? (
                              <>
                                <circle cx="18" cy="18" r="2.5" fill={f.color} />
                                <circle cx="32" cy="18" r="2.5" fill={f.color} />
                              </>
                            ) : (
                              <>
                                <path d="M 15 17 Q 18 19 21 17" fill="none" stroke={f.color} strokeWidth="2" strokeLinecap="round" />
                                <path d="M 29 17 Q 32 19 35 17" fill="none" stroke={f.color} strokeWidth="2" strokeLinecap="round" />
                              </>
                            )}

                            {/* Eyebrows */}
                            {f.score >= 6 && (
                              <>
                                <line x1="15" y1="13" x2="21" y2="15" stroke={f.color} strokeWidth="2" strokeLinecap="round" />
                                <line x1="35" y1="13" x2="29" y2="15" stroke={f.color} strokeWidth="2" strokeLinecap="round" />
                              </>
                            )}

                            {/* Mouth */}
                            <path d={f.mouth} fill="none" stroke={f.color} strokeWidth="2.5" strokeLinecap="round" />

                            {/* Tears */}
                            {f.tear && (
                              <path
                                d="M 13 22 Q 11 26 13 28 Q 15 26 13 22"
                                fill="#38bdf8"
                                stroke="#0284c7"
                                strokeWidth="0.5"
                              />
                            )}
                            {f.score === 10 && (
                              <path
                                d="M 37 22 Q 35 26 37 28 Q 39 26 37 22"
                                fill="#38bdf8"
                                stroke="#0284c7"
                                strokeWidth="0.5"
                              />
                            )}
                          </svg>

                          <div className="font-black text-sm" style={{ color: f.color }}>
                            {f.score}
                          </div>
                          <div className="text-xs font-bold text-slate-800 leading-tight mt-0.5">{f.label}</div>
                          <div className="text-[10px] text-slate-500 mt-0.5">{f.hindi}</div>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* SCALE 3: LANSKY PLAY-PERFORMANCE SCALE */}
              {activeScale === 'lansky' && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">Lansky Play-Performance Scale for Children</h3>
                      <p className="text-xs text-slate-500">Parent/clinician reported index of physical play ability and daily activity restriction.</p>
                    </div>
                    <div className="text-right">
                      <span className="text-2xl font-black text-rose-700">{lanskyScore}%</span>
                      <span className="text-xs block text-slate-500 font-semibold">
                        {lanskyScore >= 70 ? 'Mild Restriction' : lanskyScore >= 50 ? 'Moderate Restriction' : 'Severe Debilitation'}
                      </span>
                    </div>
                  </div>

                  {/* Lansky Slider */}
                  <div className="px-2">
                    <input
                      type="range"
                      min="10"
                      max="100"
                      step="10"
                      value={lanskyScore}
                      onChange={(e) => setLanskyScore(Number(e.target.value))}
                      className="w-full accent-rose-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-400 font-bold mt-1 px-1">
                      <span>10% (No Play)</span>
                      <span>30%</span>
                      <span>50% (Quiet Play)</span>
                      <span>70%</span>
                      <span>100% (Normal Play)</span>
                    </div>
                  </div>

                  {/* Current Lansky Description Box */}
                  <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="px-2 py-0.5 rounded bg-amber-200 text-amber-900 font-bold text-xs">
                        Lansky {lanskyScore}% Rating
                      </span>
                      <span className="text-xs font-semibold text-amber-800">
                        {lanskyDescriptions[lanskyScore]?.hindi}
                      </span>
                    </div>
                    <p className="text-xs text-amber-950 font-medium">
                      {lanskyDescriptions[lanskyScore]?.text}
                    </p>
                  </div>
                </div>
              )}

              {/* Comprehensive Clinical Staging Banner */}
              <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                    Guideline Recommended Strategy ({effectivePainScore}/10)
                  </div>
                  <div className="text-sm font-bold text-slate-900">{painSeverity.recommendation}</div>
                </div>
                <button
                  onClick={() => setActiveTab('dosing_engine')}
                  className="px-4 py-2 bg-rose-700 hover:bg-rose-800 text-white text-xs font-bold rounded-lg transition shadow-xs flex items-center gap-1.5"
                >
                  <span>Proceed to Weight Dosing & Syringe</span>
                  <span>→</span>
                </button>
              </div>
            </div>
          )}

          {/* TAB 2: WEIGHT-BASED OPIOID & SYRINGE CALIBRATOR */}
          {activeTab === 'dosing_engine' && (
            <div className="space-y-6">
              
              {/* Opioid Calculation Controls */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* Dosing Prescriber Card */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="flex items-center justify-between border-b pb-3">
                    <div className="flex items-center gap-2">
                      <div className="p-2 rounded-lg bg-rose-100 text-rose-700">
                        <IconSparkles className="w-4 h-4" />
                      </div>
                      <div>
                        <h3 className="text-sm font-bold text-slate-900">Oral Morphine Liquid Calculation</h3>
                        <p className="text-xs text-slate-500">WHO Pediatric Palliative Guidelines (q4h schedule)</p>
                      </div>
                    </div>
                    <span className="px-2.5 py-1 bg-rose-50 text-rose-700 border border-rose-200 rounded-lg text-xs font-bold">
                      {childWeightKg} kg child
                    </span>
                  </div>

                  {/* Slider for mg/kg */}
                  <div>
                    <div className="flex items-center justify-between text-xs font-bold text-slate-700 mb-1.5">
                      <span>Dose Rate (mg / kg / dose):</span>
                      <span className="text-rose-700 font-extrabold">{morphineMgPerKg} mg/kg</span>
                    </div>
                    <input
                      type="range"
                      min="0.10"
                      max="0.40"
                      step="0.05"
                      value={morphineMgPerKg}
                      onChange={(e) => setMorphineMgPerKg(Number(e.target.value))}
                      className="w-full accent-rose-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-400 mt-1">
                      <span>0.10 (Infant/Naive)</span>
                      <span>0.20 (Standard)</span>
                      <span>0.30 (Severe/Experienced)</span>
                      <span>0.40 (Escalation)</span>
                    </div>
                  </div>

                  {/* Calculated Results Box */}
                  <div className="grid grid-cols-2 gap-3 pt-2">
                    <div className="p-3 rounded-xl bg-rose-50/80 border border-rose-200">
                      <div className="text-[11px] font-bold text-rose-800 uppercase tracking-wide">Single Q4H Dose</div>
                      <div className="text-xl font-black text-rose-950 mt-1">{singleMorphineDoseMg} mg</div>
                      <div className="text-xs font-extrabold text-rose-700 mt-0.5">= {singleMorphineVolumeMl} mL liquid</div>
                      <div className="text-[10px] text-rose-600 mt-1 font-medium">Give every 4 hours (6 times/day)</div>
                    </div>

                    <div className="p-3 rounded-xl bg-amber-50/80 border border-amber-200">
                      <div className="text-[11px] font-bold text-amber-800 uppercase tracking-wide">Breakthrough Dose</div>
                      <div className="text-xl font-black text-amber-950 mt-1">{breakthroughRescueDoseMg} mg</div>
                      <div className="text-xs font-extrabold text-amber-700 mt-0.5">= {breakthroughRescueVolumeMl} mL liquid</div>
                      <div className="text-[10px] text-amber-600 mt-1 font-medium">Available q1h PRN for spike pain</div>
                    </div>
                  </div>

                  {/* 24-Hour Opioid Metrics */}
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-xs flex justify-between items-center">
                    <div>
                      <span className="text-slate-500 font-medium">Total 24h Baseline Morphine: </span>
                      <span className="font-bold text-slate-800">{dailyTotalMorphineMg} mg / day</span>
                    </div>
                    <div className="text-slate-500 font-medium">
                      Max rescue limit: <span className="font-bold text-slate-800">4 doses/24h</span>
                    </div>
                  </div>

                  {/* Mandatory Prophylaxis Alert */}
                  <div className="p-3 rounded-lg bg-red-50 border border-red-200 flex items-start gap-2.5 text-xs text-red-900">
                    <IconShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                    <div>
                      <span className="font-bold">Mandatory Pediatric Bowel Prophylaxis:</span>
                      <p className="mt-0.5 text-[11px] text-red-800">
                        Opioids cause constipation in 100% of children. Always co-prescribe <b>Lactulose ({childWeightKg} mL PO once daily)</b> or Macrogol/PEG 3350 ({Math.round(childWeightKg * 0.5)} g/day).
                      </p>
                    </div>
                  </div>
                </div>

                {/* SVG Oral Syringe Calibrator Card */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between border-b pb-3 mb-4">
                      <div>
                        <h3 className="text-sm font-bold text-slate-900">Bedside Oral Syringe Visualizer</h3>
                        <p className="text-xs text-slate-500">Visual calibration for parents & ASHA community health workers</p>
                      </div>
                      <span className="px-2 py-0.5 bg-sky-50 text-sky-700 border border-sky-200 rounded text-xs font-bold">
                        5 mL Oral Dispenser
                      </span>
                    </div>

                    {/* Interactive SVG Syringe */}
                    <div className="p-4 bg-slate-50/70 rounded-xl border border-slate-200 flex flex-col items-center">
                      <div className="text-xs font-bold text-slate-700 mb-2">
                        Liquid Level: <span className="text-rose-700 font-black">{singleMorphineVolumeMl} mL</span>
                      </div>

                      <svg className="w-full max-w-sm h-36" viewBox="0 0 320 90">
                        {/* Syringe Tip / Nozzle */}
                        <rect x="15" y="38" width="25" height="14" rx="2" fill="#94a3b8" />
                        
                        {/* Syringe Barrel (Outer clear glass) */}
                        <rect x="40" y="20" width="220" height="50" rx="4" fill="#f8fafc" stroke="#64748b" strokeWidth="2.5" />
                        
                        {/* Flange / Finger Grips */}
                        <rect x="255" y="10" width="10" height="70" rx="3" fill="#cbd5e1" stroke="#64748b" strokeWidth="2" />

                        {/* Liquid Fill Inside Barrel based on volume (0 to 5 mL -> 0 to 200px width) */}
                        {(() => {
                          const maxMl = 5;
                          const fillWidth = Math.min(200, Math.max(0, (singleMorphineVolumeMl / maxMl) * 200));
                          return (
                            <rect
                              x="42"
                              y="22"
                              width={fillWidth}
                              height="46"
                              fill="url(#roseLiquidGrad)"
                              opacity="0.85"
                            />
                          );
                        })()}

                        {/* Liquid Gradient definition */}
                        <defs>
                          <linearGradient id="roseLiquidGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                            <stop offset="0%" stopColor="#f43f5e" />
                            <stop offset="50%" stopColor="#e11d48" />
                            <stop offset="100%" stopColor="#be123c" />
                          </linearGradient>
                        </defs>

                        {/* Syringe Plunger (Piston) */}
                        {(() => {
                          const maxMl = 5;
                          const plungerX = 42 + Math.min(200, Math.max(0, (singleMorphineVolumeMl / maxMl) * 200));
                          return (
                            <g>
                              {/* Black Rubber Piston Head */}
                              <rect x={plungerX} y="22" width="12" height="46" rx="2" fill="#1e293b" />
                              <line x1={plungerX + 6} y1="22" x2={plungerX + 6} y2="68" stroke="#475569" strokeWidth="2" />

                              {/* Plunger Shaft */}
                              <rect x={plungerX + 12} y="38" width={300 - plungerX} height="14" fill="#cbd5e1" stroke="#94a3b8" strokeWidth="1" />
                              {/* Plunger Thumb Press */}
                              <rect x={295} y="24" width="8" height="42" rx="2" fill="#94a3b8" />
                            </g>
                          );
                        })()}

                        {/* Graduation Marks (0, 1, 2, 3, 4, 5 mL) */}
                        {[0, 1, 2, 3, 4, 5].map((ml) => {
                          const markX = 42 + (ml / 5) * 200;
                          return (
                            <g key={ml}>
                              <line x1={markX} y1="20" x2={markX} y2="32" stroke="#334155" strokeWidth="1.5" />
                              <text x={markX} y="16" fontSize="9" fontWeight="bold" fill="#475569" textAnchor="middle">
                                {ml} mL
                              </text>
                            </g>
                          );
                        })}

                        {/* Half-mL Sub-divisions */}
                        {[0.5, 1.5, 2.5, 3.5, 4.5].map((ml) => {
                          const markX = 42 + (ml / 5) * 200;
                          return (
                            <line key={ml} x1={markX} y1="20" x2={markX} y2="27" stroke="#64748b" strokeWidth="1" />
                          );
                        })}
                      </svg>

                      {/* Explicit Instructions for Caregiver */}
                      <div className="mt-3 text-center">
                        <div className="text-xs font-bold text-slate-800">
                          Parent Instruction: &quot;सिरिंज में ठीक <span className="text-rose-700 underline">{singleMorphineVolumeMl} mL</span> के निशान तक दवाई भरें&quot;
                        </div>
                        <p className="text-[11px] text-slate-500 mt-1">
                          Never use a teaspoon or tablespoon. Always use the marked oral syringe provided by the palliative team.
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Co-Analgesic Syrups Table */}
                  <div className="mt-4 border border-slate-200 rounded-lg overflow-hidden">
                    <div className="bg-slate-100 px-3 py-1.5 text-[11px] font-bold text-slate-700 uppercase">
                      Non-Opioid Pediatric Co-Analgesia
                    </div>
                    <div className="p-3 text-xs space-y-2 bg-white">
                      <div className="flex justify-between items-center border-b pb-1.5">
                        <div>
                          <span className="font-bold text-slate-800">Paracetamol Syrup (120mg/5mL):</span>
                          <span className="text-slate-500 text-[11px] block">15 mg/kg PO q6h PRN</span>
                        </div>
                        <div className="text-right font-black text-slate-900">
                          {paracetamolDoseMg} mg ({paracetamolSyrupMl} mL)
                        </div>
                      </div>

                      <div className="flex justify-between items-center border-b pb-1.5">
                        <div>
                          <span className="font-bold text-slate-800">Ibuprofen Suspension (100mg/5mL):</span>
                          <span className="text-slate-500 text-[11px] block">10 mg/kg PO q8h with food</span>
                        </div>
                        <div className="text-right font-black text-slate-900">
                          {ibuprofenDoseMg} mg
                        </div>
                      </div>

                      <div className="flex justify-between items-center">
                        <div>
                          <span className="font-bold text-slate-800">Gabapentin (Neuropathic / Nerve Pain):</span>
                          <span className="text-slate-500 text-[11px] block">5 mg/kg nocte $\rightarrow$ titrate to 20 mg/kg/day</span>
                        </div>
                        <div className="text-right font-black text-slate-900">
                          {gabapentinStartDoseMg} mg nocte
                        </div>
                      </div>
                    </div>
                  </div>

                </div>
              </div>

            </div>
          )}

          {/* TAB 3: PEDIATRIC SYMPTOM PROTOCOLS */}
          {activeTab === 'symptom_protocols' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* 1. Nausea & Vomiting */}
                <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="p-1.5 rounded-md bg-emerald-100 text-emerald-800 font-bold text-xs">1</span>
                    <h4 className="text-xs font-bold text-slate-900 uppercase">Anticipatory Nausea & Vomiting</h4>
                  </div>
                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Ondansetron (5-HT3 Antagonist):</div>
                      <div className="text-emerald-800 font-semibold mt-0.5">
                        Dose: {ondansetronDoseMg} mg ({childWeightKg} kg $\times$ 0.15 mg/kg) PO/IV q8h PRN.
                      </div>
                      <p className="text-[11px] text-slate-500 mt-1">
                        First-line for chemotherapy-induced or visceral stretch nausea. Very low incidence of sedation.
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Dexamethasone (Adjuvant Antiemetic):</div>
                      <div className="text-slate-800 font-medium mt-0.5">
                        Dose: 0.15–0.25 mg/kg PO/IV daily (Morning dose to avoid sleep disturbance).
                      </div>
                      <p className="text-[11px] text-slate-500 mt-1">
                        Indicated if raised intracranial pressure, bowel obstruction, or refractory chemotherapy emesis.
                      </p>
                    </div>
                  </div>
                </div>

                {/* 2. Dyspnea & Terminal Secretions */}
                <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="p-1.5 rounded-md bg-sky-100 text-sky-800 font-bold text-xs">2</span>
                    <h4 className="text-xs font-bold text-slate-900 uppercase">Pediatric Dyspnea & Death Rattle</h4>
                  </div>
                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Glycopyrrolate (Preferred Anticholinergic):</div>
                      <div className="text-sky-800 font-semibold mt-0.5">
                        Dose: {glycopyrrolateDoseMcg} mcg ({childWeightKg} kg $\times$ 6 mcg/kg) SC/IV q6–8h.
                      </div>
                      <p className="text-[11px] text-slate-500 mt-1">
                        <b>Critical Pediatric Nuance:</b> Glycopyrrolate does NOT cross the blood-brain barrier, unlike scopolamine/hyoscine. It prevents central anticholinergic delirium and hallucinations in children!
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Non-Pharmacologic Airway Protocol:</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Elevate child head 30–45 degrees. Gentle handheld fan air across the child cheeks stimulates trigeminal afferents and relieves air hunger. Avoid traumatic deep endotracheal suctioning.
                      </p>
                    </div>
                  </div>
                </div>

                {/* 3. Procedural Distress & Dressing Changes */}
                <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="p-1.5 rounded-md bg-purple-100 text-purple-800 font-bold text-xs">3</span>
                    <h4 className="text-xs font-bold text-slate-900 uppercase">Procedural Pain & Dressing Changes</h4>
                  </div>
                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Topical EMLA Cream (Lidocaine 2.5% + Prilocaine 2.5%):</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Apply thick layer under occlusive dressing (Tegaderm) 60 minutes prior to blood draws, lumbar punctures, or painful dressing removals.
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Pre-procedure Breakthrough Opioid:</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Administer breakthrough oral morphine ({breakthroughRescueDoseMg} mg) 30–45 minutes before painful dressing changes or physiotherapy.
                      </p>
                    </div>
                  </div>
                </div>

                {/* 4. Child-Life Non-Pharmacological Comfort */}
                <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="p-1.5 rounded-md bg-rose-100 text-rose-800 font-bold text-xs">4</span>
                    <h4 className="text-xs font-bold text-slate-900 uppercase">Child-Life Comfort & Distraction</h4>
                  </div>
                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Sensory Distraction Techniques:</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Bubble blowing (encourages deep exhalation), musical stories, handheld kaleidoscope, VR goggles or tablet cartoons during acute distress.
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Kangaroo Care / Skin-to-Skin (Infants):</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Skin-to-skin maternal contact + 24% oral sucrose drops 2 minutes before heel sticks or minor procedures significantly attenuates infant pain pathways.
                      </p>
                    </div>
                  </div>
                </div>

              </div>
            </div>
          )}

          {/* TAB 4: DEVELOPMENTAL GRIEF & SIBLING CARE */}
          {activeTab === 'grief_siblings' && (
            <div className="space-y-5">
              
              {/* Introduction Banner */}
              <div className="p-4 rounded-xl bg-gradient-to-r from-purple-50 to-pink-50 border border-purple-200">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-purple-900 text-sm">Developmentally Adapted Grief & Truth-Telling Framework</span>
                  <span className="px-2 py-0.5 bg-purple-200 text-purple-800 rounded text-[10px] font-bold">द्विभाषी गाइड</span>
                </div>
                <p className="text-xs text-purple-950">
                  Children comprehend illness and mortality through their developmental cognitive stage. Avoid euphemisms such as &quot;going to sleep&quot; or &quot;taken away by God&quot;, which create severe phobias of bedtime or doctor visits.
                </p>
              </div>

              {/* Developmental Stages Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* Stage 1: Under 3 Years */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs">Infants & Toddlers (&lt; 3 Years)</span>
                    <span className="text-[10px] font-semibold text-slate-500">Separation & Sensory Stage</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-1.5">
                    <div>
                      <span className="font-semibold text-slate-800">Cognitive Concept:</span>
                      <p className="text-[11px] text-slate-600">No concept of death; perceives parental anxiety and fear of separation.</p>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-800">Support Action:</span>
                      <p className="text-[11px] text-slate-600">Maintain physical touch, rocking, familiar blanket/stuffed toy, keep primary caregiver close at all times.</p>
                    </div>
                    <div className="p-2 bg-slate-50 rounded border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Counseling Phrase:</span>
                      <p className="text-xs text-slate-800 font-medium italic mt-0.5">
                        &quot;बच्चे को अपनी गोद में रखें, आपकी आवाज और छुअन उसे सबसे ज्यादा सुकून और सुरक्षा देती है।&quot;
                      </p>
                    </div>
                  </div>
                </div>

                {/* Stage 2: 3 to 6 Years */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs">Preschoolers (3 to 6 Years)</span>
                    <span className="text-[10px] font-semibold text-slate-500">Magical Thinking Stage</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-1.5">
                    <div>
                      <span className="font-semibold text-slate-800">Cognitive Concept:</span>
                      <p className="text-[11px] text-slate-600">Thinks death is temporary or reversible like cartoons. May fear their bad thoughts or misbehavior caused the illness.</p>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-800">Support Action:</span>
                      <p className="text-[11px] text-slate-600">Clearly reassure: &quot;Nothing you said, thought, or did caused this sickness. It is nobody&apos;s fault.&quot;</p>
                    </div>
                    <div className="p-2 bg-slate-50 rounded border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Counseling Phrase:</span>
                      <p className="text-xs text-slate-800 font-medium italic mt-0.5">
                        &quot;यह बीमारी किसी की गलती या किसी बात से नहीं हुई है। आप बिल्कुल सुरक्षित हैं और हम सब आपसे बहुत प्यार करते हैं।&quot;
                      </p>
                    </div>
                  </div>
                </div>

                {/* Stage 3: 6 to 12 Years */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs">School-Age (6 to 12 Years)</span>
                    <span className="text-[10px] font-semibold text-slate-500">Concrete Understanding</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-1.5">
                    <div>
                      <span className="font-semibold text-slate-800">Cognitive Concept:</span>
                      <p className="text-[11px] text-slate-600">Understands death is irreversible and final. May ask detailed biological questions about organs and bodies.</p>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-800">Support Action:</span>
                      <p className="text-[11px] text-slate-600">Give simple, honest medical explanations without confusing metaphors. Allow them to express feelings through art and writing.</p>
                    </div>
                    <div className="p-2 bg-slate-50 rounded border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Counseling Phrase:</span>
                      <p className="text-xs text-slate-800 font-medium italic mt-0.5">
                        &quot;शरीर जब बहुत बीमार होता है और दवाएं काम नहीं करतीं, तो शरीर के अंग धीरे-धीरे काम करना बंद कर देते हैं और कोई दर्द महसूस नहीं होता।&quot;
                      </p>
                    </div>
                  </div>
                </div>

                {/* Stage 4: Adolescents (12+ Years) */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-2">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs">Adolescents (12+ Years)</span>
                    <span className="text-[10px] font-semibold text-slate-500">Identity & Autonomy</span>
                  </div>
                  <div className="text-xs text-slate-700 space-y-1.5">
                    <div>
                      <span className="font-semibold text-slate-800">Cognitive Concept:</span>
                      <p className="text-[11px] text-slate-600">Adult understanding; experiences anger over loss of future, body image changes, and loss of independence.</p>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-800">Support Action:</span>
                      <p className="text-[11px] text-slate-600">Involve directly in care choices, respect privacy, facilitate peer connections and legacy projects (music playlist, letters).</p>
                    </div>
                    <div className="p-2 bg-slate-50 rounded border border-slate-200">
                      <span className="text-[10px] font-bold text-slate-500 uppercase">Hindi Counseling Phrase:</span>
                      <p className="text-xs text-slate-800 font-medium italic mt-0.5">
                        &quot;आपकी राय और आपकी इच्छाएं हमारे लिए सबसे ज्यादा महत्वपूर्ण हैं। जो भी फैसला होगा, उसमें आपकी पूरी भागीदारी होगी।&quot;
                      </p>
                    </div>
                  </div>
                </div>

              </div>

              {/* Sibling Care Section */}
              <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <IconUsers className="w-4 h-4 text-rose-600" />
                  <h4 className="text-xs font-bold text-slate-900 uppercase">
                    Preventing &quot;Forgotten Child Syndrome&quot; in Healthy Siblings
                  </h4>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                    <div className="font-bold text-slate-800 mb-1">1. Dedicated One-on-One Time:</div>
                    <p className="text-[11px] text-slate-600">
                      Ensure each healthy sibling receives at least 20 minutes of undivided parental attention daily without discussing cancer.
                    </p>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                    <div className="font-bold text-slate-800 mb-1">2. Inclusion in Bedside Visits:</div>
                    <p className="text-[11px] text-slate-600">
                      Prepare siblings before visits (&quot;You might see tubes or sleeping&quot;). Never force contact; encourage drawings, cards, or reading together.
                    </p>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                    <div className="font-bold text-slate-800 mb-1">3. Legacy & Memory Box:</div>
                    <p className="text-[11px] text-slate-600">
                      Help siblings create a shared memory keepsake box with clay handprints, photos, favorite bedtime stories, and personal messages.
                    </p>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 5: PEDIATRIC TRAJECTORY LOG */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Pediatric Palliative Trajectory Log</h3>
                  <p className="text-xs text-slate-500">Kidwai Pediatric Oncology Palliative Ward</p>
                </div>
                <span className="px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg text-xs font-bold border">
                  3 Prior Assessments
                </span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden bg-white">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-3">Date / Time</th>
                      <th className="p-3">Tool</th>
                      <th className="p-3">Score</th>
                      <th className="p-3">Lansky %</th>
                      <th className="p-3">Morphine Regimen</th>
                      <th className="p-3">Clinician / Observer</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-rose-50/40">
                      <td className="p-3 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-3 uppercase font-semibold text-rose-800">{activeScale}</td>
                      <td className="p-3 font-black text-rose-700">{effectivePainScore} / 10 ({painSeverity.level})</td>
                      <td className="p-3 font-bold">{lanskyScore}%</td>
                      <td className="p-3 font-semibold text-slate-800">{singleMorphineDoseMg} mg ({singleMorphineVolumeMl} mL) q4h</td>
                      <td className="p-3 text-slate-600">Dr Ananya (Pediatric Palliative)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">08 Sep 2026</td>
                      <td className="p-3 uppercase font-semibold">FACES</td>
                      <td className="p-3 font-bold text-amber-700">6 / 10 (Moderate)</td>
                      <td className="p-3 font-bold">60%</td>
                      <td className="p-3 text-slate-700">2.5 mg (1.25 mL) q4h</td>
                      <td className="p-3 text-slate-600">Sister Deepa (Pediatric RN)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">28 Aug 2026</td>
                      <td className="p-3 uppercase font-semibold">FLACC</td>
                      <td className="p-3 font-bold text-emerald-700">2 / 10 (Mild)</td>
                      <td className="p-3 font-bold">80%</td>
                      <td className="p-3 text-slate-700">Paracetamol only</td>
                      <td className="p-3 text-slate-600">Dr Ananya (Pediatric Palliative)</td>
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
              {copiedToast ? '✓ Copied Pediatric Protocol!' : '📋 Copy Complete Pediatric Protocol'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-rose-700 hover:bg-rose-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Pediatric Care Plan!' : '🧸 Dispatch Pediatric Orders & Syringe Chart'}</span>
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
