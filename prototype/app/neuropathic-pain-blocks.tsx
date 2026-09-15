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

export interface Dn4Item {
  id: string;
  category: 'interview' | 'examination';
  question: string;
  hindi: string;
  positive: boolean;
}

export type TargetPlexus = 'celiac' | 'hypogastric' | 'impar' | 'paravertebral' | 'intrathecal';

interface NeuropathicPainBlocksModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchReferral?: (summary: string) => void;
}

export function NeuropathicPainBlocksModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchReferral,
}: NeuropathicPainBlocksModalProps) {
  const [activeTab, setActiveTab] = useState<'dn4_screen' | 'co_analgesics' | 'interventional_blocks' | 'history'>('dn4_screen');

  // DN4 Questionnaire State
  const [dn4Items, setDn4Items] = useState<Dn4Item[]>([
    { id: 'burning', category: 'interview', question: 'Burning sensation (like fire / hot iron)', hindi: 'जलन जैसा दर्द (आग जैसा)', positive: true },
    { id: 'cold', category: 'interview', question: 'Painfully cold sensation', hindi: 'दर्दनाक ठंडक का अहसास', positive: false },
    { id: 'electric', category: 'interview', question: 'Electric shocks / shooting jolt', hindi: 'बिजली के झटके जैसा दर्द', positive: true },
    { id: 'tingling', category: 'interview', question: 'Tingling (pins and needles)', hindi: 'चींटियां रेंगने जैसा अहसास', positive: true },
    { id: 'pins_needles', category: 'interview', question: 'Pins and needles sensation', hindi: 'सुई चुभने जैसा अहसास', positive: true },
    { id: 'numbness', category: 'interview', question: 'Numbness in the painful area', hindi: 'दर्द वाली जगह पर सुन्नपन', positive: true },
    { id: 'itching', category: 'interview', question: 'Itching sensation in painful zone', hindi: 'दर्द वाली जगह पर खुजली', positive: false },
    { id: 'touch_hypo', category: 'examination', question: 'Hypoesthesia to touch (cotton wisp)', hindi: 'छूने पर कम अहसास होना', positive: true },
    { id: 'pinprick_hypo', category: 'examination', question: 'Hypoesthesia to pinprick', hindi: 'सुई चुभाने पर कम अहसास होना', positive: true },
    { id: 'allodynia', category: 'examination', question: 'Dynamic mechanical allodynia (pain on soft brush stroke)', hindi: 'हल्का कपड़ा या ब्रश छूने पर तेज दर्द', positive: true },
  ]);

  // Co-analgesic state
  const [egfrValue, setEgfrValue] = useState<number>(55); // mL/min/1.73m2
  const [selectedGabapentinoid, setSelectedGabapentinoid] = useState<'pregabalin' | 'gabapentin'>('pregabalin');
  const [includeDuloxetine, setIncludeDuloxetine] = useState<boolean>(true);
  const [includeKetamineBurst, setIncludeKetamineBurst] = useState<boolean>(false);
  const [includeLidocainePatch, setIncludeLidocainePatch] = useState<boolean>(true);

  // Interventional Block State
  const [selectedPlexus, setSelectedPlexus] = useState<TargetPlexus>('celiac');

  // Feedback states
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchedToast, setDispatchedToast] = useState<boolean>(false);

  // Calculate DN4 Score
  const dn4Score = useMemo(() => {
    return dn4Items.filter((i) => i.positive).length;
  }, [dn4Items]);

  const dn4Diagnosis = useMemo(() => {
    if (dn4Score >= 4) {
      return {
        confirmed: true,
        label: 'NEUROPATHIC PAIN CONFIRMED (DN4 ≥ 4/10)',
        color: '#be123c',
        bg: '#ffe4e6',
        borderColor: '#f43f5e',
        badge: 'OPIOID-RESISTANT COMPONENT',
        advice: 'Patient has definitive neuropathic cancer pain (tumor infiltration, perineural invasion, or CIPN). Opioid dose escalation alone will produce severe sedation and toxicity without analgesia. Immediate co-analgesic titration and interventional block evaluation indicated.',
      };
    }
    return {
      confirmed: false,
      label: 'NOCICEPTIVE PAIN PREDOMINANT (DN4 < 4/10)',
      color: '#047857',
      bg: '#d1fae5',
      borderColor: '#34d399',
      badge: 'STANDARD OPIOID RESPONSIVE',
      advice: 'Pain is predominantly somatic/visceral nociceptive. Responsive to standard WHO Step 3 opioid titration and non-opioid adjuvants.',
    };
  }, [dn4Score]);

  // Renal-adjusted Pregabalin Dose
  const pregabalinRecommendation = useMemo(() => {
    if (egfrValue >= 60) {
      return {
        dose: 'Pregabalin 75 mg PO nocte for 3 days, then 75 mg BID (Max: 300 mg/day)',
        note: 'Normal renal clearance. Titrate weekly based on somnolence vs pain response.',
      };
    }
    if (egfrValue >= 30) {
      return {
        dose: 'Pregabalin 50 mg PO nocte for 7 days, then 50 mg BID (Max: 150 mg/day)',
        note: 'Moderate renal impairment. Clearance delayed; monitor for excessive dizziness and ataxia.',
      };
    }
    return {
      dose: 'Pregabalin 25 mg PO once daily at bedtime (Max: 50–75 mg/day)',
      note: 'Severe renal impairment (eGFR <30). High risk of myoclonus and sedation without dose adjustment.',
    };
  }, [egfrValue]);

  // Plexus Interventional Profiles
  const plexusDetails: Record<
    TargetPlexus,
    {
      name: string;
      indications: string;
      technique: string;
      expectedRelief: string;
      complications: string;
      opioidReduction: string;
    }
  > = {
    celiac: {
      name: 'Celiac Plexus Neurolysis (CPN)',
      indications: 'Intractable upper abdominal cancer pain (Pancreatic adenocarcinoma, Gastric, Cholangiocarcinoma, Hepatic metastasis, or Duodenal carcinoma).',
      technique: 'Percutaneous retrocrural / antecrural under CT guidance or Endoscopic Ultrasound (EUS)-guided transgastric injection of 10% dehydrated alcohol (50–100 mL) preceded by bupivacaine.',
      expectedRelief: '75–85% excellent pain relief lasting 2–6 months or until end-of-life.',
      complications: 'Transient orthostatic hypotension (due to splanchnic vasodilation), mild diarrhea (parasympathetic unopposed action - self-limiting in 48h), retroperitoneal hematoma.',
      opioidReduction: 'Reduces baseline daily opioid requirement by 50–70%, significantly decreasing opioid-induced delirium, nausea, and severe constipation.',
    },
    hypogastric: {
      name: 'Superior Hypogastric Plexus Block (SHPB)',
      indications: 'Intractable deep pelvic pain from Cervical, Uterine, Ovarian, Prostate, Bladder, or Rectal carcinomas.',
      technique: 'Bilateral anterior L5-S1 vertebral promontory approach under fluoroscopic or CT guidance. Diagnostic block with 0.25% bupivacaine followed by 10% phenol or 70% alcohol neurolysis.',
      expectedRelief: '70% reduction in visceral tenesmus and deep unremitting pelvic ache.',
      complications: 'Puncture of iliac vessels (rare under real-time imaging), transient bladder or rectal dysfunction, paresthesias in L5 dermatome.',
      opioidReduction: 'Allows substantial opioid dose taper and marked reduction in pelvic muscle spasms.',
    },
    impar: {
      name: 'Ganglion of Impar (Walther) Neurolysis',
      indications: 'Refractory burning perineal, anal, vulvar, or coccygeal pain from advanced anal, low rectal, vaginal, or pelvic tumor recurrence.',
      technique: 'Transcoccygeal or sacrococcygeal ligament puncture under fluoroscopic lateral view. Neurolytic injection of 3–5 mL 6% phenol in water or pulsed radiofrequency (PRF).',
      expectedRelief: 'Prompt relief of localized burning and sitting intolerance in 80% of treated patients.',
      complications: 'Rectal perforation (prevented by non-dominant index finger in rectum during needle passage), perineal numbness, infection.',
      opioidReduction: 'Permits comfortable sitting and lying down with reduced breakthrough opioid boluses.',
    },
    paravertebral: {
      name: 'Thoracic Paravertebral & Intercostal Block',
      indications: 'Unilateral chest wall tumor infiltration, pleural mesothelioma, rib metastases, or post-thoracotomy neuralgia.',
      technique: 'Ultrasound-guided paravertebral space injection (T3 to T8) with 0.5% ropivacaine + dexamethasone or continuous catheter infusion / radiofrequency ablation.',
      expectedRelief: 'Segmental somatic dermatomal anesthesia without contralateral motor block.',
      complications: 'Pneumothorax (<0.5% with ultrasound), epidural spread, vascular puncture.',
      opioidReduction: 'Dramatic improvement in respiratory excursion and deep inspiration pain without sedation.',
    },
    intrathecal: {
      name: 'Targeted Intrathecal Drug Delivery (ITDD / Pain Pump)',
      indications: 'Severe refractory cancer pain unresponsive to oral/SC opioids or with intolerable dose-limiting toxicity (delirium, refractory myoclonus).',
      technique: 'Surgically implanted subarachnoid catheter linked to programmable subcutaneous pump infusing low-dose Morphine + Bupivacaine ± Ziconotide.',
      expectedRelief: 'Over 90% pain control at 1/100th to 1/300th of systemic oral morphine equivalence.',
      complications: 'Catheter granuloma, CSF hygroma, device infection, post-dural puncture headache.',
      opioidReduction: 'Systemic oral/SC opioids completely eliminated or minimized to minor breakthrough rescue.',
    },
  };

  const handleToggleDn4 = (id: string) => {
    setDn4Items((prev) =>
      prev.map((item) => (item.id === id ? { ...item, positive: !item.positive } : item))
    );
  };

  const handleCopyPlan = () => {
    const text = `NEUROPATHIC CANCER PAIN & INTERVENTIONAL BLOCK CONSULTATION
Patient: ${patientName} (${patientId}) · ${primaryCancer}
DN4 SCREENING SCORE: ${dn4Score} / 10 (${dn4Diagnosis.label})
Renal Function: eGFR ${egfrValue} mL/min/1.73m²
--------------------------------------------------
RECOMMENDED CO-ANALGESIC PHARMACOTHERAPY:
1. Gabapentinoid: ${pregabalinRecommendation.dose}
   Rationale: ${pregabalinRecommendation.note}
2. SNRI: ${includeDuloxetine ? 'Duloxetine 30 mg PO daily for 7 days, then 60 mg PO daily (first-line for CIPN/allodynia)' : 'Not indicated'}
3. Ketamine Burst: ${includeKetamineBurst ? 'Oral Ketamine 10 mg TID (or SC 0.5 mg/kg/24h) for 3-5 days burst for NMDA receptor wind-up' : 'Not required'}
4. Topical Therapy: ${includeLidocainePatch ? 'Lidocaine 5% Medicated Patch applied to maximal allodynia zone (12h on / 12h off)' : 'Not indicated'}
--------------------------------------------------
SELECTED INTERVENTIONAL NEUROLYSIS TARGET:
Procedure: ${plexusDetails[selectedPlexus].name}
Indication: ${plexusDetails[selectedPlexus].indications}
Technique: ${plexusDetails[selectedPlexus].technique}
Anticipated Benefit: ${plexusDetails[selectedPlexus].expectedRelief} (${plexusDetails[selectedPlexus].opioidReduction})
Potential Risks: ${plexusDetails[selectedPlexus].complications}`;

    navigator.clipboard.writeText(text);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  const handleDispatchReferral = () => {
    setDispatchedToast(true);
    if (onDispatchReferral) {
      onDispatchReferral(
        `Interventional Neurolysis Referral Logged: ${plexusDetails[selectedPlexus].name} for ${patientName} (DN4: ${dn4Score}/10)`
      );
    }
    setTimeout(() => setDispatchedToast(false), 4000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="neuropathic-modal-title"
    >
      <div className="relative w-full max-w-5xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="p-4 sm:p-5 bg-gradient-to-r from-slate-900 via-violet-950 to-slate-900 text-white flex flex-wrap items-center justify-between gap-3 border-b border-violet-800/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-violet-500/20 border border-violet-400/40 flex items-center justify-center text-violet-300">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="neuropathic-modal-title" className="text-lg font-bold tracking-tight text-white">
                  Neuropathic Cancer Pain & Interventional Blocks Suite
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-violet-500/20 text-violet-200 border border-violet-400/30">
                  IASP & ESMO Algology Consensus
                </span>
              </div>
              <p className="text-xs text-violet-200/80">
                Patient: <span className="font-semibold text-white">{patientName}</span> ({patientId}) · {primaryCancer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="px-3 py-1 rounded-lg text-xs font-bold border flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: dn4Diagnosis.bg,
                color: dn4Diagnosis.color,
                borderColor: dn4Diagnosis.borderColor,
              }}
            >
              <IconShieldAlert className="w-3.5 h-3.5" />
              <span>DN4 Score: {dn4Score} / 10 ({dn4Diagnosis.confirmed ? 'Confirmed' : 'Unlikely'})</span>
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
            onClick={() => setActiveTab('dn4_screen')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'dn4_screen'
                ? 'border-violet-600 text-violet-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>⚡ DN4 Neuropathic Pain Screening</span>
          </button>
          <button
            onClick={() => setActiveTab('co_analgesics')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'co_analgesics'
                ? 'border-violet-600 text-violet-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>💊 Renal-Adjusted Co-Analgesic Titration</span>
          </button>
          <button
            onClick={() => setActiveTab('interventional_blocks')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'interventional_blocks'
                ? 'border-violet-600 text-violet-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>🎯 Interventional Neurolysis & Blocks</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-2.5 px-4 text-xs font-bold border-b-2 transition flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-violet-600 text-violet-700 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>📋 Interventional Algology Log</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-4 sm:p-6 overflow-y-auto flex-1 space-y-6">
          {/* TAB 1: DN4 SCREEN */}
          {activeTab === 'dn4_screen' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: 10 Items */}
              <div className="lg:col-span-7 space-y-3">
                <div className="flex items-center justify-between text-xs text-slate-700">
                  <span className="font-bold uppercase tracking-wider">DN4 Diagnostic Criteria (Score ≥ 4 confirms neuropathic etiology)</span>
                  <span className="font-extrabold text-violet-800">{dn4Score} / 10 checked</span>
                </div>

                <div className="space-y-1.5">
                  <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider pt-1">
                    A. Patient Interview (Pain Characteristics & Descriptors)
                  </div>
                  {dn4Items
                    .filter((item) => item.category === 'interview')
                    .map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => handleToggleDn4(item.id)}
                        className={`w-full p-2.5 rounded-xl border text-left flex items-center justify-between transition ${
                          item.positive
                            ? 'bg-violet-50/80 border-violet-400 text-violet-950 font-bold shadow-2xs'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs">
                          <div className="text-slate-900">{item.question}</div>
                          <div className="text-[10px] text-slate-500 font-normal">{item.hindi}</div>
                        </div>
                        <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                          item.positive ? 'bg-violet-600 text-white' : 'border border-slate-300 text-transparent'
                        }`}>
                          ✓
                        </span>
                      </button>
                    ))}

                  <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider pt-2">
                    B. Physical Bedside Examination (Sensory Mapping)
                  </div>
                  {dn4Items
                    .filter((item) => item.category === 'examination')
                    .map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => handleToggleDn4(item.id)}
                        className={`w-full p-2.5 rounded-xl border text-left flex items-center justify-between transition ${
                          item.positive
                            ? 'bg-violet-50/80 border-violet-400 text-violet-950 font-bold shadow-2xs'
                            : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                        }`}
                      >
                        <div className="text-xs">
                          <div className="text-slate-900">{item.question}</div>
                          <div className="text-[10px] text-slate-500 font-normal">{item.hindi}</div>
                        </div>
                        <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                          item.positive ? 'bg-violet-600 text-white' : 'border border-slate-300 text-transparent'
                        }`}>
                          ✓
                        </span>
                      </button>
                    ))}
                </div>
              </div>

              {/* Right Column: Diagnostic Interpretation & Summary */}
              <div className="lg:col-span-5 space-y-4">
                <div
                  className="p-4 rounded-xl border-2 shadow-xs transition space-y-2.5"
                  style={{
                    backgroundColor: dn4Diagnosis.bg,
                    borderColor: dn4Diagnosis.borderColor,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-700">Validated Diagnostic Output</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold text-white" style={{ backgroundColor: dn4Diagnosis.color }}>
                      {dn4Diagnosis.badge}
                    </span>
                  </div>

                  <div className="text-base font-black" style={{ color: dn4Diagnosis.color }}>
                    {dn4Diagnosis.label}
                  </div>

                  <p className="text-xs text-slate-800 leading-relaxed font-medium">
                    {dn4Diagnosis.advice}
                  </p>
                </div>

                {/* Algology Guidance Alert */}
                <div className="p-4 rounded-xl bg-slate-900 text-white border border-slate-800 shadow-inner space-y-2">
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="font-bold text-violet-300">WHO Step 3 Co-analgesic Rationale</span>
                    <span>IASP Consensus</span>
                  </div>
                  <p className="text-xs text-slate-300 leading-relaxed">
                    Opioids alone have poor efficacy against spontaneous shooting neuropathic pains and allodynia. Adding an α2-δ ligand (Pregabalin) or an SNRI (Duloxetine) produces synergistic analgesia, allowing up to 50% lower opioid doses and halting opioid escalation toxicity.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: CO-ANALGESICS */}
          {activeTab === 'co_analgesics' && (
            <div className="space-y-5">
              {/* Renal GFR Selector */}
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex flex-wrap items-center justify-between gap-4">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Renal Function Assessment (eGFR)</span>
                  <span className="text-[11px] text-slate-500">Essential for α2-δ ligand dose calibration (prevents myoclonus & encephalopathy)</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-extrabold text-violet-800">{egfrValue} mL/min/1.73m²</span>
                  <input
                    type="range"
                    min="10"
                    max="100"
                    step="5"
                    value={egfrValue}
                    onChange={(e) => setEgfrValue(parseInt(e.target.value))}
                    className="w-36 accent-violet-600"
                  />
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                    egfrValue >= 60 ? 'bg-emerald-100 text-emerald-800' : egfrValue >= 30 ? 'bg-amber-100 text-amber-800' : 'bg-red-100 text-red-800'
                  }`}>
                    {egfrValue >= 60 ? 'Stage 1-2 Normal' : egfrValue >= 30 ? 'Stage 3 Impaired' : 'Stage 4-5 Severe'}
                  </span>
                </div>
              </div>

              {/* Drug Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Pregabalin */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-violet-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-violet-100 text-violet-800">FIRST-LINE α2-δ LIGAND</span>
                    <span className="text-xs font-bold text-slate-500">Pregabalin</span>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    {pregabalinRecommendation.dose}
                  </div>
                  <p className="text-xs text-slate-600 leading-relaxed font-medium">
                    {pregabalinRecommendation.note}
                  </p>
                  <div className="text-[11px] text-slate-500 bg-slate-50 p-2 rounded">
                    💡 <em>Mechanism:</em> Binds voltage-gated Ca2+ channels at dorsal horn; attenuates glutamate and substance P release.
                  </div>
                </div>

                {/* Duloxetine */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-violet-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-blue-100 text-blue-800">FIRST-LINE SNRI</span>
                    <label className="flex items-center gap-1.5 text-xs font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={includeDuloxetine}
                        onChange={(e) => setIncludeDuloxetine(e.target.checked)}
                        className="w-4 h-4 text-violet-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Duloxetine 30 mg PO daily for 7 days, then 60 mg PO daily
                  </div>
                  <p className="text-xs text-slate-600 leading-relaxed font-medium">
                    Evidence-based gold standard (ASCO guidelines) for Chemotherapy-Induced Peripheral Neuropathy (CIPN) from Paclitaxel, Oxaliplatin, or Cisplatin.
                  </p>
                  <div className="text-[11px] text-slate-500 bg-slate-50 p-2 rounded">
                    💡 <em>Mechanism:</em> Boosts descending inhibitory serotonergic and noradrenergic pain pathways.
                  </div>
                </div>

                {/* Ketamine Burst */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-violet-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-rose-100 text-rose-800">NMDA RECEPTOR ANTAGONIST</span>
                    <label className="flex items-center gap-1.5 text-xs font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={includeKetamineBurst}
                        onChange={(e) => setIncludeKetamineBurst(e.target.checked)}
                        className="w-4 h-4 text-violet-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Oral Ketamine 10 mg TID (or Subcutaneous 0.5–1 mg/kg/day continuous)
                  </div>
                  <p className="text-xs text-slate-600 leading-relaxed font-medium">
                    Reserved for refractory neuropathic pain or ischemic limb pain unresponsive to high-dose opioids. 3- to 5-day &quot;burst&quot; protocol reverses dorsal horn central sensitization.
                  </p>
                </div>

                {/* Topical Lidocaine 5% */}
                <div className="p-4 rounded-xl bg-white border-2 border-slate-200 hover:border-violet-400 transition space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 rounded text-xs font-extrabold bg-emerald-100 text-emerald-800">TOPICAL PERIPHERAL</span>
                    <label className="flex items-center gap-1.5 text-xs font-bold text-slate-700 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={includeLidocainePatch}
                        onChange={(e) => setIncludeLidocainePatch(e.target.checked)}
                        className="w-4 h-4 text-violet-600 rounded"
                      />
                      <span>Active</span>
                    </label>
                  </div>
                  <div className="text-sm font-extrabold text-slate-900">
                    Lidocaine 5% Medicated Plaster (Apply up to 3 patches for 12h ON / 12h OFF)
                  </div>
                  <p className="text-xs text-slate-600 leading-relaxed font-medium">
                    Zero systemic sedation. Targets ectopic sodium channel discharges in cutaneous allodynia and post-mastectomy or post-radiation neuralgia.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: INTERVENTIONAL BLOCKS */}
          {activeTab === 'interventional_blocks' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Left Column: Anatomical Plexus SVG Map & Selector */}
              <div className="lg:col-span-5 space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-800 block">
                  Select Anatomical Neuro-Visceral Target
                </span>

                <div className="grid grid-cols-1 gap-2">
                  {[
                    { id: 'celiac', label: 'Celiac Plexus Neurolysis (CPN)', target: 'Pancreatic / Upper GI (T12-L1)' },
                    { id: 'hypogastric', label: 'Superior Hypogastric Plexus (SHPB)', target: 'Pelvic / Gynecologic / Rectal (L5-S1)' },
                    { id: 'impar', label: 'Ganglion of Impar (Walther)', target: 'Perineal / Anal / Vulvar (Coccyx)' },
                    { id: 'paravertebral', label: 'Thoracic Paravertebral Block', target: 'Chest Wall / Ribs / Pleura (T3-T8)' },
                    { id: 'intrathecal', label: 'Targeted Intrathecal Pump (ITDD)', target: 'Subarachnoid Infusion (Refractory)' },
                  ].map((block) => (
                    <button
                      key={block.id}
                      type="button"
                      onClick={() => setSelectedPlexus(block.id as TargetPlexus)}
                      className={`p-2.5 rounded-xl border text-left transition ${
                        selectedPlexus === block.id
                          ? 'bg-violet-50 border-violet-500 text-violet-950 ring-2 ring-violet-200 font-bold'
                          : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                      }`}
                    >
                      <div className="text-xs font-bold text-slate-900">{block.label}</div>
                      <div className="text-[11px] text-slate-500 font-normal">{block.target}</div>
                    </button>
                  ))}
                </div>

                {/* SVG Neuro-Anatomy Schematic */}
                <div className="p-3 bg-slate-900 rounded-xl text-white flex flex-col items-center">
                  <span className="text-[11px] font-bold text-slate-300 mb-1">Target Plexus Location</span>
                  <div className="w-full max-w-[180px] aspect-[1/2] flex items-center justify-center">
                    <svg viewBox="0 0 100 200" className="w-full h-full drop-shadow-md">
                      {/* Spine / Body axis */}
                      <line x1="50" y1="20" x2="50" y2="180" stroke="#334155" strokeWidth="4" />
                      
                      {/* Thoracic Paravertebral (T3-T8) */}
                      <circle
                        cx="42"
                        cy="60"
                        r="6"
                        fill={selectedPlexus === 'paravertebral' ? '#8b5cf6' : '#475569'}
                        className="cursor-pointer"
                        onClick={() => setSelectedPlexus('paravertebral')}
                      />
                      <circle
                        cx="58"
                        cy="60"
                        r="6"
                        fill={selectedPlexus === 'paravertebral' ? '#8b5cf6' : '#475569'}
                        className="cursor-pointer"
                        onClick={() => setSelectedPlexus('paravertebral')}
                      />
                      <text x="70" y="63" fill="white" fontSize="7">T3-T8</text>

                      {/* Celiac Plexus (T12-L1) */}
                      <circle
                        cx="50"
                        cy="95"
                        r={selectedPlexus === 'celiac' ? '9' : '7'}
                        fill={selectedPlexus === 'celiac' ? '#a855f7' : '#64748b'}
                        stroke="#f3e8ff"
                        strokeWidth={selectedPlexus === 'celiac' ? '2' : '0'}
                        className="cursor-pointer"
                        onClick={() => setSelectedPlexus('celiac')}
                      />
                      <text x="64" y="98" fill="#f3e8ff" fontSize="8" fontWeight="bold">Celiac</text>

                      {/* Superior Hypogastric (L5-S1) */}
                      <circle
                        cx="50"
                        cy="135"
                        r={selectedPlexus === 'hypogastric' ? '8' : '6'}
                        fill={selectedPlexus === 'hypogastric' ? '#a855f7' : '#64748b'}
                        className="cursor-pointer"
                        onClick={() => setSelectedPlexus('hypogastric')}
                      />
                      <text x="64" y="138" fill="white" fontSize="7">Hypogastric</text>

                      {/* Ganglion of Impar (Coccyx) */}
                      <circle
                        cx="50"
                        cy="165"
                        r={selectedPlexus === 'impar' ? '8' : '5'}
                        fill={selectedPlexus === 'impar' ? '#a855f7' : '#64748b'}
                        className="cursor-pointer"
                        onClick={() => setSelectedPlexus('impar')}
                      />
                      <text x="64" y="168" fill="white" fontSize="7">Impar</text>
                    </svg>
                  </div>
                </div>
              </div>

              {/* Right Column: Procedure Protocol Details */}
              <div className="lg:col-span-7 space-y-4">
                <div className="p-4 rounded-xl bg-violet-50 border border-violet-200 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-violet-800 uppercase tracking-wider">Interventional Technique Specification</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold bg-violet-200 text-violet-900">EBM Consensus</span>
                  </div>
                  <h3 className="text-sm font-extrabold text-violet-950">
                    {plexusDetails[selectedPlexus].name}
                  </h3>
                  <p className="text-xs text-violet-900 leading-relaxed font-medium">
                    <strong>Clinical Indication:</strong> {plexusDetails[selectedPlexus].indications}
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-white border border-slate-200 space-y-2.5 text-xs">
                  <div>
                    <span className="font-bold text-slate-800 block mb-0.5">Procedural Approach & Injectate:</span>
                    <p className="text-slate-600 leading-relaxed">{plexusDetails[selectedPlexus].technique}</p>
                  </div>

                  <div className="pt-2 border-t border-slate-100">
                    <span className="font-bold text-emerald-800 block mb-0.5">Anticipated Efficacy:</span>
                    <p className="text-slate-600 leading-relaxed">{plexusDetails[selectedPlexus].expectedRelief}</p>
                  </div>

                  <div className="pt-2 border-t border-slate-100">
                    <span className="font-bold text-blue-800 block mb-0.5">Systemic Opioid Dose Impact:</span>
                    <p className="text-slate-600 leading-relaxed">{plexusDetails[selectedPlexus].opioidReduction}</p>
                  </div>

                  <div className="pt-2 border-t border-slate-100">
                    <span className="font-bold text-rose-800 block mb-0.5">Safety & Potential Complications:</span>
                    <p className="text-slate-600 leading-relaxed">{plexusDetails[selectedPlexus].complications}</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: HISTORY */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Interventional Pain & Co-Analgesic History</h3>
                <span className="text-xs text-slate-500">Kidwai Interventional Pain Service</span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-2.5">Date</th>
                      <th className="p-2.5">Intervention / Drug</th>
                      <th className="p-2.5">DN4 Score</th>
                      <th className="p-2.5">Outcome</th>
                      <th className="p-2.5">Opioid Sparing</th>
                      <th className="p-2.5">Clinician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-violet-50/50">
                      <td className="p-2.5 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-2.5 font-bold text-violet-800">{plexusDetails[selectedPlexus].name}</td>
                      <td className="p-2.5 font-extrabold text-violet-700">{dn4Score}/10</td>
                      <td className="p-2.5 text-emerald-700 font-semibold">Scheduled / Referred</td>
                      <td className="p-2.5 font-medium">-50% Expected</td>
                      <td className="p-2.5 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">01 Sep 2026</td>
                      <td className="p-2.5">Pregabalin 75mg PO nocte</td>
                      <td className="p-2.5 text-slate-500">6/10</td>
                      <td className="p-2.5 text-emerald-700">Allodynia reduced by 40%</td>
                      <td className="p-2.5">OMEDD stabilized at 60mg</td>
                      <td className="p-2.5 text-slate-600">Kidwai Pain Clinic</td>
                    </tr>
                    <tr>
                      <td className="p-2.5 text-slate-600">14 Aug 2026</td>
                      <td className="p-2.5">Lidocaine 5% patch to chest wall</td>
                      <td className="p-2.5 text-slate-500">5/10</td>
                      <td className="p-2.5 text-emerald-700">Local contact hypersensitivity resolved</td>
                      <td className="p-2.5">No breakthrough doses needed</td>
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
              {copiedToast ? '✓ Copied Algology Plan!' : '📋 Copy Co-Analgesic & Block Plan'}
            </button>
            <button
              onClick={handleDispatchReferral}
              className="px-4 py-2 bg-violet-700 hover:bg-violet-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched Interventional Referral!' : '🎯 Dispatch STAT Interventional Pain Referral'}</span>
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
