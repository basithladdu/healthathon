'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldCheck,
  IconShieldAlert,
  IconCheckCircle,
  IconSparkles,
  IconHospital,
  IconClock,
  IconUsers,
} from './icons';

export interface AnticipatoryMedication {
  id: string;
  symptom: string;
  hindiSymptom: string;
  drug: string;
  ampouleConcentration: string;
  singleDose: string;
  route: string;
  minInterval: string;
  max24hDose: string;
  initialStock: number;
  currentStock: number;
  badgeColor: string;
  instructions: string;
  hindiInstructions: string;
}

export interface AdminLogEntry {
  id: string;
  timestamp: string;
  medication: string;
  dose: string;
  symptomReason: string;
  administeredBy: string;
  effect: 'Effective relief' | 'Partial relief' | 'Refractory / Escalated';
}

export const INITIAL_BOX_MEDS: AnticipatoryMedication[] = [
  {
    id: 'morphine',
    symptom: 'Severe Pain & Air Hunger (Dyspnea)',
    hindiSymptom: 'गंभीर दर्द और सांस फूलना (Pain & Dyspnea)',
    drug: 'Morphine Sulfate Injection',
    ampouleConcentration: '10 mg / 1 mL ampoule',
    singleDose: '2.5 mg to 5.0 mg SC (0.25 to 0.5 mL)',
    route: 'Subcutaneous (via Butterfly needle)',
    minInterval: 'Wait at least 60 minutes before repeating',
    max24hDose: 'Maximum 6 PRN doses in 24 hours without doctor review',
    initialStock: 10,
    currentStock: 8,
    badgeColor: '#e11d48',
    instructions: 'Draw into 1mL syringe with needle filter. Administer via SC butterfly. Do not rush injection.',
    hindiInstructions: '1mL सिरिंज में निकालें। बटरफ्लाई सुई से त्वचा के नीचे धीरे-धीरे लगाएं।',
  },
  {
    id: 'midazolam',
    symptom: 'Severe Agitation, Distress & Panic',
    hindiSymptom: 'अत्यधिक घबराहट, बेचैनी और भ्रम (Agitation & Panic)',
    drug: 'Midazolam Injection',
    ampouleConcentration: '5 mg / 1 mL ampoule',
    singleDose: '2.5 mg SC (0.5 mL)',
    route: 'Subcutaneous (via Butterfly needle)',
    minInterval: 'Wait at least 60 minutes before repeating',
    max24hDose: 'Maximum 4 PRN doses in 24 hours',
    initialStock: 8,
    currentStock: 7,
    badgeColor: '#7c3aed',
    instructions: 'For extreme restlessness, thrashing, or breathing panic. Expect drowsiness within 10-15 mins.',
    hindiInstructions: 'अत्यधिक बेचैनी या सांस घुटने के डर के लिए। 10-15 मिनट में मरीज शांत हो जाएगा।',
  },
  {
    id: 'glycopyrronium',
    symptom: 'Noisy Breathing / Secretions ("Death Rattle")',
    hindiSymptom: 'गले से घरघराहट की आवाज (Respiratory Secretions)',
    drug: 'Glycopyrronium Bromide',
    ampouleConcentration: '0.2 mg / 1 mL ampoule',
    singleDose: '0.2 mg SC (1.0 mL)',
    route: 'Subcutaneous (via Butterfly needle)',
    minInterval: 'Wait at least 4 hours before repeating',
    max24hDose: 'Maximum 1.2 mg (6 doses) in 24 hours',
    initialStock: 6,
    currentStock: 6,
    badgeColor: '#0284c7',
    instructions: 'Administer at earliest onset of secretions. Turn patient into lateral recovery position.',
    hindiInstructions: 'गले में कफ की आवाज शुरू होते ही लगाएं। मरीज को करवट दिलाकर लिटाएं।',
  },
  {
    id: 'haloperidol',
    symptom: 'Nausea, Vomiting & Hallucinations',
    hindiSymptom: 'जी मिचलाना, उल्टी और मतिभ्रम (Nausea & Delirium)',
    drug: 'Haloperidol Injection',
    ampouleConcentration: '5 mg / 1 mL ampoule',
    singleDose: '1.0 mg to 1.5 mg SC (0.2 to 0.3 mL)',
    route: 'Subcutaneous (via Butterfly needle)',
    minInterval: 'Wait at least 4 to 8 hours before repeating',
    max24hDose: 'Maximum 5 mg in 24 hours',
    initialStock: 6,
    currentStock: 5,
    badgeColor: '#059669',
    instructions: 'Calms nausea without causing gut paralysis. Also helpful for nighttime delirium and confusion.',
    hindiInstructions: 'उल्टी और भ्रांति को रोकता है। रात के समय बेचैनी कम करने में भी सहायक है।',
  },
];

export interface AnticipatoryDrugBoxProps {
  patientId: string;
  patientName: string;
  diagnosis: string;
  onClose?: () => void;
  onLogAdministration?: (medName: string, dose: string) => void;
}

export function AnticipatoryDrugBoxModal({
  patientId,
  patientName,
  diagnosis,
  onClose,
  onLogAdministration,
}: AnticipatoryDrugBoxProps) {
  const [medications, setMedications] = useState<AnticipatoryMedication[]>(INITIAL_BOX_MEDS);
  const [activeLang, setActiveLang] = useState<'en' | 'hi'>('en');
  const [selectedMedId, setSelectedMedId] = useState<string>('morphine');
  const [adminLog, setAdminLog] = useState<AdminLogEntry[]>([
    {
      id: 'LOG-101',
      timestamp: 'Today, 04:30 AM',
      medication: 'Morphine Sulfate',
      dose: '2.5 mg SC',
      symptomReason: 'Breakthrough thoracic bone pain (Pain 8/10)',
      administeredBy: 'Rohan Sharma (Son / Proxy)',
      effect: 'Effective relief',
    },
    {
      id: 'LOG-102',
      timestamp: 'Yesterday, 09:15 PM',
      medication: 'Midazolam',
      dose: '2.5 mg SC',
      symptomReason: 'Evening agitation & breathlessness distress',
      administeredBy: 'Lakshmi Devi (ASHA Nurse)',
      effect: 'Effective relief',
    },
  ]);

  const [logModalMed, setLogModalMed] = useState<AnticipatoryMedication | null>(null);
  const [adminReason, setAdminReason] = useState<string>('Acute breakthrough pain spike');
  const [adminBy, setAdminBy] = useState<string>('Lakshmi Devi (ASHA Worker)');
  const [adminEffect, setAdminEffect] = useState<'Effective relief' | 'Partial relief' | 'Refractory / Escalated'>('Effective relief');
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [activeStepTab, setActiveStepTab] = useState<'butterfly' | 'audit' | 'safety'>('butterfly');

  const activeMed = useMemo(() => {
    return medications.find((m) => m.id === selectedMedId) || medications[0];
  }, [medications, selectedMedId]);

  // Execute drug administration log entry
  const handleConfirmAdministration = () => {
    if (!logModalMed) return;

    // Decrement stock
    setMedications((prev) =>
      prev.map((m) => {
        if (m.id === logModalMed.id && m.currentStock > 0) {
          return { ...m, currentStock: m.currentStock - 1 };
        }
        return m;
      })
    );

    // Add entry
    const newEntry: AdminLogEntry = {
      id: `LOG-${Date.now().toString().slice(-4)}`,
      timestamp: 'Just now',
      medication: logModalMed.drug,
      dose: logModalMed.singleDose.split(' ')[0] + ' ' + logModalMed.singleDose.split(' ')[1],
      symptomReason: adminReason,
      administeredBy: adminBy,
      effect: adminEffect,
    };

    setAdminLog((prev) => [newEntry, ...prev]);

    if (onLogAdministration) {
      onLogAdministration(logModalMed.drug, logModalMed.singleDose);
    }

    setLogModalMed(null);
  };

  const handleCopyAuditRecord = () => {
    const auditText = `[CONTINUITY LOOP: HOME JUST-IN-CASE DRUG BOX AUDIT & LOG]
Patient: ${patientName} (${patientId})
Diagnosis: ${diagnosis}
Box Location: Bedside Cabinet (Safe Lock) · Jayanagar Residence
Supervising Physician: Dr Sujay · Reg: KMC-58291
Authorized ASHA Lead: Lakshmi Devi (+91 98451 22340)

CURRENT CONTROLLED DRUG STOCK:
${medications.map((m) => `• ${m.drug}: ${m.currentStock} of ${m.initialStock} ampoules remaining (Single dose: ${m.singleDose})`).join('\n')}

RECENT ADMINISTRATION LOG:
${adminLog.map((l) => `[${l.timestamp}] ${l.medication} (${l.dose}) administered by ${l.administeredBy}\n Reason: ${l.symptomReason} | Outcome: ${l.effect}`).join('\n\n')}

NDPS Compliance: Form 3E Authorized Medical Palliative Supply`;

    navigator.clipboard.writeText(auditText).then(() => {
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
              <span className="p-1 rounded-md bg-rose-600 text-white font-black text-xs">
                JUST-IN-CASE (JIC)
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Home Palliative Emergency Drug Box & Subcutaneous Protocol
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Anticipatory home symptom management, controlled drug counting, and subcutaneous administration guide
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Drug Box"
          >
            ✕
          </button>
        </div>

        {/* Patient Ribbon & Language Switcher */}
        <div className="px-6 py-3 bg-rose-50/60 border-b border-rose-200 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-[10px] font-bold text-rose-500 uppercase tracking-wider block">Patient</span>
              <span className="font-bold text-slate-900 text-sm">{patientName} ({patientId})</span>
            </div>
            <div className="h-6 w-px bg-rose-200" />
            <div>
              <span className="text-[10px] font-bold text-rose-500 uppercase tracking-wider block">Box Custodian</span>
              <span className="font-semibold text-slate-800">Rohan Sharma (Proxy) & Lakshmi Devi (ASHA)</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500 font-semibold">Language:</span>
            <div className="flex bg-white p-0.5 rounded-lg border border-rose-200 text-xs">
              <button
                onClick={() => setActiveLang('en')}
                className={`px-2.5 py-1 rounded font-bold transition ${
                  activeLang === 'en' ? 'bg-rose-600 text-white' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                English
              </button>
              <button
                onClick={() => setActiveLang('hi')}
                className={`px-2.5 py-1 rounded font-bold transition ${
                  activeLang === 'hi' ? 'bg-rose-600 text-white' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                🇮🇳 हिंदी
              </button>
            </div>
          </div>
        </div>

        {/* Main 2-Column Layout */}
        <div className="p-6 overflow-y-auto max-h-[72vh] space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: Anticipatory Drug Inventory (7 cols) */}
            <div className="lg:col-span-7 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                  <IconHospital className="w-4 h-4 text-rose-600" />
                  <span>Bedside Box Medications (Controlled Stock)</span>
                </h3>
                <span className="text-xs text-slate-400 font-mono">NDPS Rule 52 Compliant</span>
              </div>

              {/* Drug Cards */}
              <div className="space-y-3">
                {medications.map((med) => {
                  const isSelected = med.id === selectedMedId;
                  const isLow = med.currentStock <= 2;
                  return (
                    <div
                      key={med.id}
                      onClick={() => setSelectedMedId(med.id)}
                      className={`p-4 rounded-xl border transition cursor-pointer space-y-2.5 ${
                        isSelected
                          ? 'bg-rose-50/40 border-rose-400 ring-2 ring-rose-200 shadow-sm'
                          : 'bg-white border-slate-200 hover:border-slate-300 shadow-xs'
                      }`}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: med.badgeColor }}
                          />
                          <h4 className="font-bold text-slate-900 text-sm">{med.drug}</h4>
                          <span className="text-xs text-slate-500 font-mono">({med.ampouleConcentration})</span>
                        </div>

                        {/* Stock pill & Administer Button */}
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
                              isLow ? 'bg-red-100 text-red-800 animate-pulse' : 'bg-slate-100 text-slate-800'
                            }`}
                          >
                            {med.currentStock} of {med.initialStock} ampoules left
                          </span>

                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setLogModalMed(med);
                            }}
                            className="px-3 py-1 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1"
                          >
                            <span>💉 Give Dose</span>
                          </button>
                        </div>
                      </div>

                      <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-xs space-y-1">
                        <div className="font-semibold text-slate-800">
                          <strong>Indication:</strong> {activeLang === 'hi' ? med.hindiSymptom : med.symptom}
                        </div>
                        <div className="text-slate-600 flex flex-wrap gap-x-4">
                          <span><strong>Single Dose:</strong> <code className="font-bold text-indigo-700">{med.singleDose}</code></span>
                          <span><strong>Route:</strong> {med.route}</span>
                        </div>
                        <div className="text-slate-500 text-[11px]">
                          <strong>Wait Time:</strong> {med.minInterval} · <strong>Daily Max:</strong> {med.max24hDose}
                        </div>
                      </div>

                      <div className="text-xs text-slate-600 font-medium italic">
                        &quot;{activeLang === 'hi' ? med.hindiInstructions : med.instructions}&quot;
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Right Column: Butterfly Guide, Administration Log, & Escalate (5 cols) */}
            <div className="lg:col-span-5 space-y-5">
              {/* Tab Selector */}
              <div className="flex bg-slate-100 p-1 rounded-xl gap-1 border border-slate-200 text-xs">
                <button
                  type="button"
                  onClick={() => setActiveStepTab('butterfly')}
                  className={`flex-1 py-1.5 rounded-lg font-bold transition ${
                    activeStepTab === 'butterfly' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Needle Guide
                </button>
                <button
                  type="button"
                  onClick={() => setActiveStepTab('audit')}
                  className={`flex-1 py-1.5 rounded-lg font-bold transition ${
                    activeStepTab === 'audit' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Admin Log ({adminLog.length})
                </button>
                <button
                  type="button"
                  onClick={() => setActiveStepTab('safety')}
                  className={`flex-1 py-1.5 rounded-lg font-bold transition ${
                    activeStepTab === 'safety' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Emergency Contacts
                </button>
              </div>

              {/* Tab 1: Subcutaneous Butterfly Guide */}
              {activeStepTab === 'butterfly' && (
                <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                  <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
                    <IconSparkles className="w-4 h-4 text-rose-600" />
                    <span>How to Administer via Subcutaneous Butterfly Needle</span>
                  </div>

                  <div className="space-y-2.5 text-xs">
                    <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-200 space-y-1">
                      <div className="font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-rose-100 text-rose-800 font-black text-[11px] flex items-center justify-center">1</span>
                        <span>Confirm Injection Site</span>
                      </div>
                      <p className="text-slate-600 pl-6 text-[11px]">
                        Inspect anterior chest wall (infraclavicular), abdomen (2 inches from navel), or outer upper arm. Ensure no edema, redness, or broken skin.
                      </p>
                    </div>

                    <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-200 space-y-1">
                      <div className="font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-rose-100 text-rose-800 font-black text-[11px] flex items-center justify-center">2</span>
                        <span>Draw Accurate Volume</span>
                      </div>
                      <p className="text-slate-600 pl-6 text-[11px]">
                        Carefully check ampoule label: <strong className="text-slate-800">{activeMed.drug}</strong>. Draw exact volume ({activeMed.singleDose.split('(')[1]?.replace(')', '') || '0.5 mL'}).
                      </p>
                    </div>

                    <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-200 space-y-1">
                      <div className="font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-rose-100 text-rose-800 font-black text-[11px] flex items-center justify-center">3</span>
                        <span>Inject Slowly & Flush</span>
                      </div>
                      <p className="text-slate-600 pl-6 text-[11px]">
                        Clean rubber bung with alcohol swab. Inject slowly over 20-30 seconds. Flush line with 0.2 mL sterile saline to ensure full dose delivery.
                      </p>
                    </div>

                    <div className="p-2.5 bg-rose-50/80 rounded-lg border border-rose-200 space-y-1 text-rose-950">
                      <div className="font-bold flex items-center gap-1.5 text-rose-800">
                        <IconShieldAlert className="w-4 h-4" />
                        <span>Golden Safety Rule</span>
                      </div>
                      <p className="text-[11px] font-medium leading-relaxed">
                        If patient requires more than 2 rescue doses within a 4-hour window, DO NOT keep giving more injections. Call the 24/7 Palliative Outreach Nurse immediately!
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Tab 2: Administration Log */}
              {activeStepTab === 'audit' && (
                <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-800">Recent Administrations</span>
                    <button
                      type="button"
                      onClick={handleCopyAuditRecord}
                      className="text-indigo-600 font-bold text-xs hover:underline"
                    >
                      {copiedToast ? '✓ Copied' : 'Copy Audit Log'}
                    </button>
                  </div>

                  <div className="space-y-2 max-h-[350px] overflow-y-auto">
                    {adminLog.map((log) => (
                      <div
                        key={log.id}
                        className="p-3 bg-slate-50 rounded-lg border border-slate-200 space-y-1 text-xs"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-slate-900">{log.medication} ({log.dose})</span>
                          <span className="text-[10px] text-slate-400">{log.timestamp}</span>
                        </div>
                        <div className="text-slate-600 text-[11px]">
                          <strong>Reason:</strong> {log.symptomReason}
                        </div>
                        <div className="flex items-center justify-between text-[11px] pt-1 border-t border-slate-100 text-slate-500">
                          <span>By: {log.administeredBy}</span>
                          <span className="font-bold text-emerald-700">✓ {log.effect}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Tab 3: Emergency Contacts & Helpline */}
              {activeStepTab === 'safety' && (
                <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                  <div className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                    <IconHospital className="w-4 h-4 text-purple-600" />
                    <span>24/7 Community Palliative Support Network</span>
                  </div>

                  <div className="space-y-2 text-xs">
                    <div className="p-3 bg-purple-50 rounded-lg border border-purple-200 flex items-center justify-between">
                      <div>
                        <div className="font-bold text-purple-950">Karunashraya Hospice 24/7 Helpline</div>
                        <div className="text-[11px] text-purple-700 font-mono">+91 80 4268 5666</div>
                      </div>
                      <a
                        href="tel:+918042685666"
                        className="px-3 py-1 bg-purple-700 text-white rounded text-xs font-bold"
                      >
                        Call Now
                      </a>
                    </div>

                    <div className="p-3 bg-teal-50 rounded-lg border border-teal-200 flex items-center justify-between">
                      <div>
                        <div className="font-bold text-teal-950">Lakshmi Devi (Assigned ASHA Nurse)</div>
                        <div className="text-[11px] text-teal-700 font-mono">+91 98451 22340</div>
                      </div>
                      <a
                        href="tel:+919845122340"
                        className="px-3 py-1 bg-teal-700 text-white rounded text-xs font-bold"
                      >
                        Call ASHA
                      </a>
                    </div>

                    <div className="p-3 bg-indigo-50 rounded-lg border border-indigo-200 flex items-center justify-between">
                      <div>
                        <div className="font-bold text-indigo-950">Dr Sujay (Palliative Lead)</div>
                        <div className="text-[11px] text-indigo-700 font-mono">Kidwai Palliative On-Call</div>
                      </div>
                      <span className="px-2 py-0.5 bg-indigo-100 text-indigo-800 rounded text-[10px] font-bold">
                        Paging Active
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Modal Sub-Dialog for Confirming Dose Administration */}
        {logModalMed && (
          <div className="modal-backdrop" role="dialog" aria-modal="true">
            <div className="modal-panel" style={{ maxWidth: '500px', padding: '24px' }}>
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b pb-3">
                  <h3 className="font-bold text-slate-900 text-base flex items-center gap-1.5">
                    <span>💉 Confirm Administration</span>
                  </h3>
                  <button onClick={() => setLogModalMed(null)} className="text-slate-400 hover:text-slate-600 font-bold">
                    ✕
                  </button>
                </div>

                <div className="p-3 bg-rose-50 rounded-lg border border-rose-200 space-y-1 text-xs">
                  <div className="font-bold text-rose-900">{logModalMed.drug}</div>
                  <div className="text-rose-800">
                    Dose: <strong>{logModalMed.singleDose}</strong>
                  </div>
                  <div className="text-[11px] text-slate-500">
                    Current stock will decrease from {logModalMed.currentStock} to {logModalMed.currentStock - 1} ampoules.
                  </div>
                </div>

                <div className="space-y-3 text-xs">
                  <div>
                    <label className="block text-slate-700 font-bold mb-1">Reason / Clinical Indication</label>
                    <input
                      type="text"
                      value={adminReason}
                      onChange={(e) => setAdminReason(e.target.value)}
                      className="w-full px-3 py-1.5 border rounded-lg bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-700 font-bold mb-1">Administered By</label>
                    <select
                      value={adminBy}
                      onChange={(e) => setAdminBy(e.target.value)}
                      className="w-full px-3 py-1.5 border rounded-lg bg-white text-slate-800 font-medium"
                    >
                      <option value="Lakshmi Devi (ASHA Worker)">Lakshmi Devi (ASHA Worker)</option>
                      <option value="Rohan Sharma (Son / Proxy)">Rohan Sharma (Son / Proxy)</option>
                      <option value="Visiting Community Palliative Nurse">Visiting Community Palliative Nurse</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-slate-700 font-bold mb-1">Observed Clinical Outcome</label>
                    <select
                      value={adminEffect}
                      onChange={(e) => setAdminEffect(e.target.value as any)}
                      className="w-full px-3 py-1.5 border rounded-lg bg-white text-slate-800 font-medium"
                    >
                      <option value="Effective relief">Effective relief</option>
                      <option value="Partial relief">Partial relief</option>
                      <option value="Refractory / Escalated">Refractory / Escalated</option>
                    </select>
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-3 border-t">
                  <button
                    type="button"
                    onClick={() => setLogModalMed(null)}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleConfirmAdministration}
                    className="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold shadow-xs"
                  >
                    Confirm & Record Administration
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopyAuditRecord}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied Box Audit Record!' : '📋 Copy Drug Box Audit Record'}
            </button>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-5 py-2 bg-slate-800 hover:bg-slate-900 text-white rounded-lg text-xs font-bold transition shadow-sm"
            >
              Done / Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
