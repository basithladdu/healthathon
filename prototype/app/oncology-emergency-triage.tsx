'use client';

import React, { useState, useMemo } from 'react';
import {
  IconZap,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconHeartPulse,
  IconSparkles,
  IconHospital,
} from './icons';

export type EmergencyId = 'mscc' | 'hypercalcemia' | 'febrile_neutropenia' | 'svco' | 'bowel_obstruction';

export interface EmergencyCondition {
  id: EmergencyId;
  name: string;
  shortName: string;
  badge: string;
  urgency: 'CRITICAL (0-2h)' | 'IMMEDIATE (2-6h)' | 'URGENT (6-24h)';
  urgencyColor: string;
  icon: string;
  clinicalPresentation: string;
  diagnosticWorkup: string[];
  immediateStatOrders: {
    medication: string;
    doseRoute: string;
    rationale: string;
  }[];
  criticalContraindications: string[];
  definitivePathway: string;
}

export const EMERGENCY_CONDITIONS: EmergencyCondition[] = [
  {
    id: 'mscc',
    name: 'Malignant Spinal Cord Compression (MSCC)',
    shortName: 'MSCC',
    badge: 'Neurologic Emergency',
    urgency: 'CRITICAL (0-2h)',
    urgencyColor: '#dc2626',
    icon: '⚡',
    clinicalPresentation:
      'Progressive thoracic/lumbar back pain with radicular band, motor weakness in lower limbs, sensory level, or urinary hesitancy/incontinence.',
    diagnosticWorkup: [
      'Emergency whole-spine sagittal MRI within 24 hours (or CT myelogram if MRI contraindicated)',
      'Neurological examination: Motor power (MRC scale 0-5), sensory level (T4 nipple, T10 umbilicus), post-void residual bladder volume by ultrasound',
    ],
    immediateStatOrders: [
      {
        medication: 'Dexamethasone Sodium Phosphate',
        doseRoute: '16 mg IV/PO stat + Pantoprazole 40mg IV',
        rationale: 'Reduces vasogenic spinal cord edema and preserves motor neuron viability',
      },
      {
        medication: 'Dexamethasone Maintenance',
        doseRoute: '8 mg PO twice daily (morning and noon)',
        rationale: 'Sustained anti-edema coverage until definitive decompression or radiotherapy',
      },
      {
        medication: 'Analgesia & Spine Precautions',
        doseRoute: 'Morphine 5-10mg SC/IV PRN + Flat bed rest until spinal stability confirmed',
        rationale: 'Prevent mechanical subluxation and treat neuropathic bone pain',
      },
    ],
    criticalContraindications: [
      'DO NOT delay dexamethasone administration pending MRI scan results if clinical suspicion is high',
      'Avoid high-impact mobilization or physiotherapy before radiological spine clearance',
    ],
    definitivePathway:
      'Emergency Spine Surgery consult (Patchell criteria for decompression within 48h) OR Urgent Palliative Radiotherapy (8 Gy single fraction or 20 Gy in 5 fractions).',
  },
  {
    id: 'hypercalcemia',
    name: 'Severe Hypercalcemia of Malignancy (HCM)',
    shortName: 'Hypercalcemia',
    badge: 'Metabolic Emergency',
    urgency: 'IMMEDIATE (2-6h)',
    urgencyColor: '#ea580c',
    icon: '🧪',
    clinicalPresentation:
      'Severe fatigue, confusion/lethargy ("bones, stones, groans, psychiatric moans"), polyuria, polydipsia, shortened QTc interval, and cardiac dysrhythmias.',
    diagnosticWorkup: [
      'Serum Ionized Calcium and Total Calcium with Albumin to calculate Corrected Calcium',
      '12-Lead ECG: Assess QTc interval, PR prolongation, and Osborn waves',
      'Renal function (eGFR, Serum Creatinine) and Serum Phosphate/Potassium/Magnesium',
    ],
    immediateStatOrders: [
      {
        medication: 'Isotonic Saline (0.9% NaCl)',
        doseRoute: '200 to 300 mL/hr IV (rehydrate 2-4 Liters in first 24h)',
        rationale: 'Restores intravascular volume and induces renal calcium excretion',
      },
      {
        medication: 'Zoledronic Acid',
        doseRoute: '4 mg IV in 100 mL Saline infused over 15 minutes',
        rationale: 'Potent bisphosphonate osteoclast inhibition; nadir serum calcium in 48-72h',
      },
      {
        medication: 'Calcitonin (Salmon)',
        doseRoute: '4 to 8 IU/kg SC/IM every 12 hours (if calcium > 14 mg/dL)',
        rationale: 'Rapid calcium lowering within 4-6 hours while awaiting bisphosphonate onset',
      },
    ],
    criticalContraindications: [
      'DO NOT administer Furosemide (loop diuretics) until dehydration and intravascular volume are fully repleted',
      'Dose adjust Zoledronic acid if eGFR < 30 mL/min, or substitute with Denosumab 120mg SC',
    ],
    definitivePathway:
      'Denosumab 120mg SC on days 1, 8, 15, and 28 for bisphosphonate-refractory hypercalcemia. Monitor for hypocalcemia and hypomagnesemia at day 5-7.',
  },
  {
    id: 'febrile_neutropenia',
    name: 'Febrile Neutropenia (MASCC Risk Stratified)',
    shortName: 'Febrile Neutropenia',
    badge: 'Infectious Emergency',
    urgency: 'CRITICAL (0-2h)',
    urgencyColor: '#dc2626',
    icon: '🌡️',
    clinicalPresentation:
      'Single oral temp >= 38.3 C (101 F) or >= 38.0 C for > 1 hour with Absolute Neutrophil Count (ANC) < 500/uL, often with muted signs of inflammation.',
    diagnosticWorkup: [
      'Blood cultures x 2 sets (1 peripheral + 1 from each lumen of indwelling central venous port/PICC)',
      'Complete Blood Count with differential, serum lactate, procalcitonin, chest radiograph, urine culture',
    ],
    immediateStatOrders: [
      {
        medication: 'Piperacillin-Tazobactam (Zosyn)',
        doseRoute: '4.5 g IV infused over 30 mins (Door-to-Antibiotic < 60 mins)',
        rationale: 'Broad anti-pseudomonal and gram-negative coverage',
      },
      {
        medication: 'Vancomycin (Empiric Gram-Positive)',
        doseRoute: '15-20 mg/kg IV (if port infection, hypotension, or MRSA colonization)',
        rationale: 'Covers catheter-related bloodstream infections and severe mucositis',
      },
      {
        medication: 'Isotonic Resuscitation Bolus',
        doseRoute: '30 mL/kg Balanced Crystalloid IV within 3 hours if MAP < 65 mmHg',
        rationale: 'Prevent progression to septic shock',
      },
    ],
    criticalContraindications: [
      'NO digital rectal examinations, rectal thermometers, or suppositories (mucosal bacterial translocation risk)',
      'DO NOT withhold antibiotics to wait for culture collection if draw is delayed > 15 mins',
    ],
    definitivePathway:
      'Evaluate MASCC Risk Index Score. If MASCC < 21: High-risk, mandatory inpatient ICU/oncology admission. If MASCC >= 21: Low-risk, assess for outpatient oral fluoroquinolone step-down.',
  },
  {
    id: 'svco',
    name: 'Superior Vena Cava (SVC) Syndrome',
    shortName: 'SVC Syndrome',
    badge: 'Cardiovascular Emergency',
    urgency: 'IMMEDIATE (2-6h)',
    urgencyColor: '#ea580c',
    icon: '🫁',
    clinicalPresentation:
      'Facial and bilateral upper extremity edema, cyanosis, distended neck veins with collateral chest wall veins, orthopnea, stridor, or Pemberton sign positive.',
    diagnosticWorkup: [
      'Contrast-enhanced thoracic CT venography: Defines level of obstruction and presence of intravascular thrombus',
      'Clinical Staging: Kishi Score / Yale Grading for airway edema and hemodynamic compromise',
    ],
    immediateStatOrders: [
      {
        medication: 'Dexamethasone Sodium Phosphate',
        doseRoute: '8 to 16 mg IV stat followed by 8mg twice daily',
        rationale: 'Reduces peri-tumoral inflammatory edema and airway compression',
      },
      {
        medication: 'Elevated Head-of-Bed Positioning',
        doseRoute: '45-90 degrees upright + Supplemental Oxygen to keep SpO2 > 92%',
        rationale: 'Reduces hydrostatic capillary pressure in the head and upper neck',
      },
      {
        medication: 'Therapeutic Anticoagulation (if catheter-related thrombus)',
        doseRoute: 'Enoxaparin 1.5 mg/kg SC once daily or 1 mg/kg SC twice daily',
        rationale: 'Treat catheter-associated thrombosis and prevent clot propagation',
      },
    ],
    criticalContraindications: [
      'NEVER insert peripheral IV lines or blood draw catheters into upper extremities (use lower limbs/saphenous if central line unavailable)',
      'Avoid supine sedation without airway equipment on standby',
    ],
    definitivePathway:
      'Urgent endovascular stenting provides symptomatic relief within 24-48 hours with 95% technical success. For lymphoma or SCLC: Palliative chemotherapy/radiotherapy.',
  },
  {
    id: 'bowel_obstruction',
    name: 'Malignant Bowel Obstruction (MBO)',
    shortName: 'Bowel Obstruction',
    badge: 'Gastrointestinal Emergency',
    urgency: 'URGENT (6-24h)',
    urgencyColor: '#d97706',
    icon: '🩺',
    clinicalPresentation:
      'Abdominal colic, distension, absent bowel sounds or high-pitched tinkling, failure to pass flatus, and continuous feculent or bilious vomiting.',
    diagnosticWorkup: [
      'Plain abdominal radiograph (erect and supine) or CT abdomen with water-soluble IV/oral contrast',
      'Assess for mechanical complete obstruction vs partial vs paralytic ileus / carcinomatosis',
    ],
    immediateStatOrders: [
      {
        medication: 'Octreotide (Somatostatin Analogue)',
        doseRoute: '200 to 300 mcg SC/IV continuous infusion over 24 hours',
        rationale: 'Reduces gastrointestinal secretagogue activity and luminal fluid accumulation',
      },
      {
        medication: 'Dexamethasone Sodium Phosphate',
        doseRoute: '8 to 12 mg IV/SC once daily (morning)',
        rationale: 'Anti-inflammatory effect on bowel wall edema; promotes spontaneous resolution',
      },
      {
        medication: 'Haloperidol (Antiemetic)',
        doseRoute: '1.5 to 3 mg SC/IV / 24h continuous or Q8H PRN',
        rationale: 'Central antiemetic without prokinetic peristaltic stimulation',
      },
      {
        medication: 'Hyoscine Butylbromide (Buscopan)',
        doseRoute: '60 to 120 mg / 24h SC continuous infusion',
        rationale: 'Potent antispasmodic targeting smooth muscle colic',
      },
    ],
    criticalContraindications: [
      'STRICTLY CONTRAINDICATED: Prokinetic agents (Metoclopramide, Domperidone) in complete mechanical obstruction (risk of bowel perforation!)',
      'Avoid aggressive hypertonic enteral nutrition or aggressive stimulant laxatives (Senna)',
    ],
    definitivePathway:
      'Determine surgical resectability vs venting gastrostomy (PEG) vs palliative medical management with subcutaneous syringe driver.',
  },
];

export interface OncologyEmergencyTriageProps {
  patientId: string;
  patientName: string;
  diagnosis: string;
  onClose?: () => void;
  onDispatchAmbulance?: (reason: string) => void;
  onApplyOrdersToRecord?: (orders: string) => void;
}

export function OncologyEmergencyTriageModal({
  patientId,
  patientName,
  diagnosis,
  onClose,
  onDispatchAmbulance,
  onApplyOrdersToRecord,
}: OncologyEmergencyTriageProps) {
  const [selectedEmergencyId, setSelectedEmergencyId] = useState<EmergencyId>('mscc');

  // Corrected Calcium Interactive Calculator
  const [serumCalcium, setSerumCalcium] = useState<number>(12.8);
  const [serumAlbumin, setSerumAlbumin] = useState<number>(3.0);

  // MASCC Risk Index Calculator
  const [masccBurden, setMasccBurden] = useState<number>(5); // 5=None/mild, 3=Moderate, 0=Severe
  const [masccNoHypotension, setMasccNoHypotension] = useState<boolean>(true); // 5 pts
  const [masccNoCopd, setMasccNoCopd] = useState<boolean>(true); // 4 pts
  const [masccSolidTumor, setMasccSolidTumor] = useState<boolean>(true); // 4 pts
  const [masccNoDehydration, setMasccNoDehydration] = useState<boolean>(true); // 3 pts
  const [masccOutpatient, setMasccOutpatient] = useState<boolean>(true); // 3 pts
  const [masccAgeUnder60, setMasccAgeUnder60] = useState<boolean>(false); // 2 pts

  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [dispatchToast, setDispatchToast] = useState<string | null>(null);

  // Computed Corrected Calcium
  const correctedCalcium = useMemo(() => {
    // Formula: Total Ca + 0.8 * (4.0 - Albumin)
    const val = serumCalcium + 0.8 * (4.0 - serumAlbumin);
    return Number(val.toFixed(2));
  }, [serumCalcium, serumAlbumin]);

  // Computed MASCC Total
  const totalMascc = useMemo(() => {
    let score = masccBurden;
    if (masccNoHypotension) score += 5;
    if (masccNoCopd) score += 4;
    if (masccSolidTumor) score += 4;
    if (masccNoDehydration) score += 3;
    if (masccOutpatient) score += 3;
    if (masccAgeUnder60) score += 2;
    return score;
  }, [
    masccBurden,
    masccNoHypotension,
    masccNoCopd,
    masccSolidTumor,
    masccNoDehydration,
    masccOutpatient,
    masccAgeUnder60,
  ]);

  const activeCondition = useMemo(() => {
    return EMERGENCY_CONDITIONS.find((c) => c.id === selectedEmergencyId) || EMERGENCY_CONDITIONS[0];
  }, [selectedEmergencyId]);

  // Copy Orders Sheet
  const handleCopyOrderSheet = () => {
    const ordersText = `[CONTINUITY LOOP: ONCOLOGIC EMERGENCY STAT ORDERS]
Patient: ${patientName} (${patientId})
Diagnosis: ${diagnosis}
Emergency Protocol: ${activeCondition.name}
Urgency Level: ${activeCondition.urgency}
Generated: ${new Date().toLocaleString('en-GB')}

STAT IMMEDIATE ORDERS:
${activeCondition.immediateStatOrders
  .map((o, idx) => `${idx + 1}. ${o.medication} - ${o.doseRoute}\n   Rationale: ${o.rationale}`)
  .join('\n')}

CRITICAL CONTRAINDICATIONS:
${activeCondition.criticalContraindications.map((c) => `⚠️ ${c}`).join('\n')}

DIAGNOSTIC WORKUP REQUIRED:
${activeCondition.diagnosticWorkup.map((d) => `• ${d}`).join('\n')}

DEFINITIVE PATHWAY:
${activeCondition.definitivePathway}

Attending Clinician: Dr Isha Menon · Emergency Physician`;

    navigator.clipboard.writeText(ordersText).then(() => {
      setCopiedToast(true);
      setTimeout(() => setCopiedToast(false), 3000);
    });
  };

  const handleDispatchAmbulance = () => {
    const reason = `Oncologic Emergency: ${activeCondition.shortName} (${activeCondition.urgency})`;
    if (onDispatchAmbulance) {
      onDispatchAmbulance(reason);
    }
    setDispatchToast(`108 Emergency Ambulance Dispatched for ${activeCondition.shortName}`);
    setTimeout(() => setDispatchToast(null), 4000);
  };

  return (
    <div className="tep-overlay" role="dialog" aria-modal="true">
      <div className="tep-modal prog-modal-wide" style={{ maxWidth: '1150px' }}>
        {/* Modal Header */}
        <div className="tep-header">
          <div className="tep-header-title">
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-rose-600 text-white font-black text-xs animate-pulse">
                🚨 STAT EMERGENCY
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Oncologic Emergency Clinical Triage & Standing Orders
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Immediate bedside algorithms, weight-adjusted orders, and high-velocity escalation pathways
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Emergency Triage"
          >
            ✕
          </button>
        </div>

        {/* Emergency Selector Tabs */}
        <div className="px-6 py-2.5 bg-slate-100 border-b border-slate-200 flex flex-wrap items-center gap-2 overflow-x-auto">
          {EMERGENCY_CONDITIONS.map((cond) => {
            const isSelected = cond.id === selectedEmergencyId;
            return (
              <button
                key={cond.id}
                onClick={() => setSelectedEmergencyId(cond.id)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 shadow-xs whitespace-nowrap ${
                  isSelected
                    ? 'bg-slate-900 text-white ring-2 ring-rose-500'
                    : 'bg-white text-slate-700 hover:bg-slate-200 border border-slate-300'
                }`}
              >
                <span>{cond.icon}</span>
                <span>{cond.shortName}</span>
                <span
                  className="w-2 h-2 rounded-full ml-1"
                  style={{ backgroundColor: cond.urgencyColor }}
                />
              </button>
            );
          })}
        </div>

        {/* Patient Context & Alert Ribbon */}
        <div className="px-6 py-3 bg-rose-50 border-b border-rose-200 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-[10px] font-bold text-rose-400 uppercase tracking-wider block">Patient Context</span>
              <span className="font-bold text-slate-900 text-sm">{patientName} ({patientId})</span>
            </div>
            <div className="h-6 w-px bg-rose-200" />
            <div>
              <span className="text-[10px] font-bold text-rose-400 uppercase tracking-wider block">Primary Oncology</span>
              <span className="font-medium text-slate-700">{diagnosis}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className="px-2.5 py-1 rounded font-black uppercase tracking-wider text-[11px] text-white"
              style={{ backgroundColor: activeCondition.urgencyColor }}
            >
              {activeCondition.urgency}
            </span>
            <span className="px-2.5 py-1 rounded bg-white text-slate-800 font-bold border border-rose-200">
              {activeCondition.badge}
            </span>
          </div>
        </div>

        {/* Main Content Body */}
        <div className="p-6 overflow-y-auto max-h-[72vh] space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: Clinical Algorithms & Interactive Calculators (7 cols) */}
            <div className="lg:col-span-7 space-y-5">
              {/* Clinical Presentation */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-2">
                <div className="flex items-center gap-2 text-rose-600 font-bold text-sm">
                  <IconHeartPulse className="w-4 h-4" />
                  <span>Clinical Presentation & Triage Signs</span>
                </div>
                <p className="text-xs text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100 font-medium">
                  {activeCondition.clinicalPresentation}
                </p>
              </div>

              {/* Special Interactive Calculators for Specific Emergencies */}
              {activeCondition.id === 'hypercalcemia' && (
                <div className="p-4 bg-amber-50/70 rounded-xl border border-amber-200 shadow-sm space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-amber-900 uppercase tracking-wider">
                      Corrected Calcium Calculator
                    </span>
                    <span className="text-xs font-mono font-bold text-amber-800">
                      Formula: Ca + 0.8 × (4.0 - Alb)
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="block text-slate-600 font-medium mb-1">
                        Measured Total Calcium (mg/dL)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={serumCalcium}
                        onChange={(e) => setSerumCalcium(Number(e.target.value))}
                        className="w-full px-3 py-1.5 bg-white border border-amber-300 rounded-lg font-bold text-slate-900 focus:outline-none focus:ring-1 focus:ring-amber-500"
                      />
                    </div>
                    <div>
                      <label className="block text-slate-600 font-medium mb-1">
                        Serum Albumin (g/dL)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={serumAlbumin}
                        onChange={(e) => setSerumAlbumin(Number(e.target.value))}
                        className="w-full px-3 py-1.5 bg-white border border-amber-300 rounded-lg font-bold text-slate-900 focus:outline-none focus:ring-1 focus:ring-amber-500"
                      />
                    </div>
                  </div>

                  <div className="p-3 bg-white rounded-lg border border-amber-200 flex items-center justify-between">
                    <div>
                      <span className="text-xs text-slate-500 font-medium block">Corrected Calcium:</span>
                      <span className="text-2xl font-black text-amber-600">{correctedCalcium} mg/dL</span>
                    </div>
                    <div className="text-right">
                      <span
                        className={`px-2.5 py-1 rounded text-xs font-bold ${
                          correctedCalcium >= 14
                            ? 'bg-red-600 text-white'
                            : correctedCalcium >= 12
                            ? 'bg-amber-500 text-white'
                            : 'bg-emerald-100 text-emerald-800'
                        }`}
                      >
                        {correctedCalcium >= 14
                          ? 'Hypercalcemic Crisis (Severe)'
                          : correctedCalcium >= 12
                          ? 'Moderate Hypercalcemia'
                          : 'Mild / Normal'}
                      </span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">Normal: 8.5 - 10.2 mg/dL</span>
                    </div>
                  </div>
                </div>
              )}

              {activeCondition.id === 'febrile_neutropenia' && (
                <div className="p-4 bg-purple-50/70 rounded-xl border border-purple-200 shadow-sm space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-purple-900 uppercase tracking-wider">
                      MASCC Risk Index Scoring
                    </span>
                    <span className={`text-xs font-black px-2 py-0.5 rounded ${
                      totalMascc >= 21 ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'
                    }`}>
                      Score: {totalMascc} / 26 ({totalMascc >= 21 ? 'Low Risk' : 'High Risk'})
                    </span>
                  </div>

                  <div className="space-y-1.5 text-xs">
                    <div className="flex items-center justify-between p-2 bg-white rounded border border-purple-100">
                      <span>Burden of febrile illness</span>
                      <select
                        value={masccBurden}
                        onChange={(e) => setMasccBurden(Number(e.target.value))}
                        className="font-bold text-slate-800 text-xs bg-transparent border-none focus:outline-none"
                      >
                        <option value={5}>None / Mild symptoms (5 pts)</option>
                        <option value={3}>Moderate symptoms (3 pts)</option>
                        <option value={0}>Severe symptoms (0 pts)</option>
                      </select>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccNoHypotension}
                          onChange={(e) => setMasccNoHypotension(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>No hypotension (5 pts)</span>
                      </label>
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccNoCopd}
                          onChange={(e) => setMasccNoCopd(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>No active COPD (4 pts)</span>
                      </label>
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccSolidTumor}
                          onChange={(e) => setMasccSolidTumor(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>Solid tumor (4 pts)</span>
                      </label>
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccNoDehydration}
                          onChange={(e) => setMasccNoDehydration(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>No dehydration (3 pts)</span>
                      </label>
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccOutpatient}
                          onChange={(e) => setMasccOutpatient(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>Outpatient onset (3 pts)</span>
                      </label>
                      <label className="flex items-center gap-2 p-2 bg-white rounded border border-purple-100 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={masccAgeUnder60}
                          onChange={(e) => setMasccAgeUnder60(e.target.checked)}
                          className="accent-purple-600"
                        />
                        <span>Age &lt; 60 years (2 pts)</span>
                      </label>
                    </div>
                  </div>
                </div>
              )}

              {/* Diagnostic Workup */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-2">
                <div className="flex items-center gap-2 text-indigo-600 font-bold text-sm">
                  <IconHospital className="w-4 h-4" />
                  <span>Mandatory Diagnostic Workup</span>
                </div>
                <ul className="list-disc pl-5 space-y-1 text-xs text-slate-700">
                  {activeCondition.diagnosticWorkup.map((item, idx) => (
                    <li key={idx}>{item}</li>
                  ))}
                </ul>
              </div>

              {/* Critical Contraindications */}
              <div className="p-4 bg-rose-50/80 rounded-xl border border-rose-200 shadow-sm space-y-2">
                <div className="flex items-center gap-2 text-rose-700 font-bold text-sm">
                  <IconShieldAlert className="w-4 h-4" />
                  <span>Critical Contraindications & High-Risk Hazards</span>
                </div>
                <ul className="space-y-1.5 text-xs text-rose-900 font-medium">
                  {activeCondition.criticalContraindications.map((contra, idx) => (
                    <li key={idx} className="flex items-start gap-2">
                      <span className="text-rose-600 font-bold">✕</span>
                      <span>{contra}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            {/* Right Column: STAT Standing Orders & Rapid Execution (5 cols) */}
            <div className="lg:col-span-5 space-y-5">
              {/* STAT Standing Orders Card */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
                    <IconZap className="w-4 h-4 text-amber-500" />
                    <span>Immediate STAT Orders</span>
                  </div>
                  <span className="text-[10px] text-slate-400 font-mono">Door-to-Needle &lt; 60m</span>
                </div>

                <div className="space-y-2.5">
                  {activeCondition.immediateStatOrders.map((order, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded-lg bg-slate-50 border border-slate-200 space-y-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-slate-900">{order.medication}</span>
                        <span className="px-1.5 py-0.5 rounded bg-rose-100 text-rose-800 font-bold text-[10px]">
                          STAT
                        </span>
                      </div>
                      <div className="font-mono text-indigo-700 font-bold text-[11px]">
                        {order.doseRoute}
                      </div>
                      <div className="text-slate-500 text-[11px] italic">
                        {order.rationale}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Definitive Clinical Pathway */}
              <div className="p-4 bg-indigo-50/60 rounded-xl border border-indigo-100 shadow-sm space-y-2">
                <div className="text-xs font-bold text-indigo-950 flex items-center gap-1.5">
                  <IconSparkles className="w-4 h-4 text-indigo-600" />
                  <span>Definitive Specialist Pathway</span>
                </div>
                <p className="text-xs text-indigo-900 leading-relaxed font-medium">
                  {activeCondition.definitivePathway}
                </p>
              </div>

              {/* Emergency Ambulance Dispatch Card */}
              <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 text-white space-y-3 shadow-lg">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-rose-400 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-rose-500 animate-ping"></span>
                    Emergency Transit Dispatch
                  </span>
                  <span className="text-[10px] text-slate-400 font-mono">108 Network</span>
                </div>

                <p className="text-xs text-slate-300 leading-relaxed">
                  Requires immediate transfer to tertiary oncology facility (Kidwai Memorial or Manipal) with priority ALS resuscitation capability.
                </p>

                <button
                  type="button"
                  onClick={handleDispatchAmbulance}
                  className="w-full py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold transition flex items-center justify-center gap-2 shadow-sm"
                >
                  <span>🚑</span>
                  <span>Dispatch 108 Emergency Ambulance Now</span>
                </button>
                {dispatchToast && (
                  <div className="p-2 bg-emerald-900/80 border border-emerald-500 text-emerald-200 text-xs rounded text-center font-bold">
                    ✓ {dispatchToast}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopyOrderSheet}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied STAT Orders to Clipboard!' : '📋 Copy STAT Emergency Order Sheet'}
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
                if (onApplyOrdersToRecord) {
                  onApplyOrdersToRecord(
                    `[EMERGENCY PROTOCOL APPLIED: ${activeCondition.name}] STAT: ${activeCondition.immediateStatOrders[0].medication} ${activeCondition.immediateStatOrders[0].doseRoute}. Definitive: ${activeCondition.definitivePathway}`
                  );
                }
                if (onClose) onClose();
              }}
              className="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Apply STAT Protocol to Record</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
