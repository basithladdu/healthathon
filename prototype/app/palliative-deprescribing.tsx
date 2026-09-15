'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconZap,
  IconSparkles,
} from './icons';

export interface MedicationItem {
  id: string;
  name: string;
  dose: string;
  frequency: string;
  indication: string;
  category: 'preventive' | 'symptom_control' | 'chronic_disease' | 'supportive';
  deprescribingTier: 'deprescribe' | 'reduce' | 'maintain';
  guidelineRationale: string;
  evidenceSource: string; // e.g., OncPal / STOPP-Frail
  isActive: boolean;
}

export interface DrugInteraction {
  id: string;
  drugs: [string, string];
  severity: 'high' | 'moderate' | 'caution';
  mechanism: string;
  clinicalAction: string;
}

export const INITIAL_PATIENT_MEDS: Record<string, MedicationItem[]> = {
  'CANCER-20418': [
    {
      id: 'atorvastatin',
      name: 'Atorvastatin',
      dose: '20 mg',
      frequency: 'Once daily at night (Oral)',
      indication: 'Primary hyperlipidemia / Cardiovascular prevention',
      category: 'preventive',
      deprescribingTier: 'deprescribe',
      guidelineRationale: 'Preventive statin therapy has a time-to-benefit of >2-5 years and provides no survival benefit in metastatic lung cancer. Deprescribing reduces pill burden and hepatotoxicity.',
      evidenceSource: 'OncPal Deprescribing Guideline & STOPP-Frail 2020',
      isActive: true,
    },
    {
      id: 'metformin',
      name: 'Metformin Hydrochloride',
      dose: '500 mg',
      frequency: 'Twice daily (Oral)',
      indication: 'Type 2 Diabetes Mellitus',
      category: 'chronic_disease',
      deprescribingTier: 'reduce',
      guidelineRationale: 'Tight glycemic control increases risk of fatal hypoglycemia during reduced oral intake. Relax HbA1c targets to <8.5% and reduce or stop if oral intake drops further.',
      evidenceSource: 'ADA Palliative Diabetes Consensus',
      isActive: true,
    },
    {
      id: 'amlodipine',
      name: 'Amlodipine',
      dose: '5 mg',
      frequency: 'Once daily (Oral)',
      indication: 'Essential Hypertension',
      category: 'chronic_disease',
      deprescribingTier: 'reduce',
      guidelineRationale: 'Blood pressure decreases naturally with advanced cancer cachexia. Deprescribe if systolic BP <110 mmHg to avoid postural falls.',
      evidenceSource: 'European Society for Medical Oncology (ESMO)',
      isActive: true,
    },
    {
      id: 'calcium_vit_d',
      name: 'Calcium Carbonate + Cholecalciferol',
      dose: '500 mg / 400 IU',
      frequency: 'Once daily (Oral)',
      indication: 'Bone density maintenance',
      category: 'preventive',
      deprescribingTier: 'deprescribe',
      guidelineRationale: 'Long-term osteoporosis mineral supplement provides zero benefit in advanced disease and causes distressing constipation and swallowing difficulty.',
      evidenceSource: 'Deprescribing.org Guidelines',
      isActive: true,
    },
    {
      id: 'multivitamin',
      name: 'Multivitamin Complex + Zinc',
      dose: '1 tablet',
      frequency: 'Once daily (Oral)',
      indication: 'Nutritional supplement',
      category: 'preventive',
      deprescribingTier: 'deprescribe',
      guidelineRationale: 'Large burdensome oral pill with zero therapeutic efficacy in malignant anorexia-cachexia syndrome.',
      evidenceSource: 'American Academy of Hospice and Palliative Medicine',
      isActive: true,
    },
    {
      id: 'morphine',
      name: 'Morphine Sulfate Sustained Release',
      dose: '30 mg',
      frequency: 'Every 12 hours (Oral)',
      indication: 'Malignant pleural and chest wall pain',
      category: 'symptom_control',
      deprescribingTier: 'maintain',
      guidelineRationale: 'Essential baseline opioid for pain and dyspnea palliation. Maintain with co-prescribed laxative.',
      evidenceSource: 'WHO Guidelines for the Pharmacological Treatment of Cancer Pain',
      isActive: true,
    },
    {
      id: 'pantoprazole',
      name: 'Pantoprazole Gastro-resistant',
      dose: '40 mg',
      frequency: 'Once daily before food (Oral)',
      indication: 'Gastroprotection with Dexamethasone',
      category: 'supportive',
      deprescribingTier: 'maintain',
      guidelineRationale: 'Protects gastric mucosa against steroid-induced peptic ulceration. Continue while corticosteroids remain active.',
      evidenceSource: 'British National Formulary (BNF)',
      isActive: true,
    },
    {
      id: 'dexamethasone',
      name: 'Dexamethasone',
      dose: '4 mg',
      frequency: 'Once daily in the morning (Oral)',
      indication: 'Peritumoral edema & appetite stimulation',
      category: 'symptom_control',
      deprescribingTier: 'maintain',
      guidelineRationale: 'Reduces peritumoral inflammatory edema and improves energy. Plan slow taper to minimum effective dose.',
      evidenceSource: 'MASCC/ESMO Antiemetic & Symptom Guidelines',
      isActive: true,
    },
  ],
};

export const DRUG_INTERACTIONS: DrugInteraction[] = [
  {
    id: 'int_1',
    drugs: ['Morphine', 'Amlodipine'],
    severity: 'moderate',
    mechanism: 'Additive vasodilation and orthostatic hypotension.',
    clinicalAction: 'Monitor standing BP; consider deprescribing Amlodipine if patient is bedbound.',
  },
  {
    id: 'int_2',
    drugs: ['Dexamethasone', 'Metformin'],
    severity: 'moderate',
    mechanism: 'Corticosteroids induce hepatic gluconeogenesis, antagonizing oral hypoglycemics.',
    clinicalAction: 'Avoid escalating Metformin to high doses; prioritize comfort over strict euglycemia.',
  },
];

export interface PalliativeDeprescribingProps {
  patientId: string;
  patientName: string;
  onClose?: () => void;
  onApplyDeprescribingPlan?: (summary: string) => void;
}

export function PalliativeDeprescribingMatrix({
  patientId,
  patientName,
  onClose,
  onApplyDeprescribingPlan,
}: PalliativeDeprescribingProps) {
  const [medList, setMedList] = useState<MedicationItem[]>(
    INITIAL_PATIENT_MEDS[patientId] || INITIAL_PATIENT_MEDS['CANCER-20418']
  );

  const [activeFilter, setActiveFilter] = useState<'all' | 'deprescribe' | 'reduce' | 'maintain'>('all');
  const [appliedToast, setAppliedToast] = useState(false);

  const deprescribeCount = useMemo(() => {
    return medList.filter((m) => m.deprescribingTier === 'deprescribe').length;
  }, [medList]);

  const reduceCount = useMemo(() => {
    return medList.filter((m) => m.deprescribingTier === 'reduce').length;
  }, [medList]);

  const maintainCount = useMemo(() => {
    return medList.filter((m) => m.deprescribingTier === 'maintain').length;
  }, [medList]);

  const activePillBurden = useMemo(() => {
    return medList.filter((m) => m.isActive).length;
  }, [medList]);

  const toggleMedActive = (id: string) => {
    setMedList((prev) =>
      prev.map((m) => (m.id === id ? { ...m, isActive: !m.isActive } : m))
    );
  };

  const handle1ClickDeprescribeFutile = () => {
    setMedList((prev) =>
      prev.map((m) => (m.deprescribingTier === 'deprescribe' ? { ...m, isActive: false } : m))
    );
  };

  const filteredMeds = useMemo(() => {
    if (activeFilter === 'all') return medList;
    return medList.filter((m) => m.deprescribingTier === activeFilter);
  }, [medList, activeFilter]);

  const deprescribingSummary = useMemo(() => {
    const discontinued = medList.filter((m) => !m.isActive).map((m) => m.name);
    const retained = medList.filter((m) => m.isActive).map((m) => m.name);
    return `Palliative Polypharmacy Review (STOPP-Frail / OncPal): Discontinued ${discontinued.length} futile/burdensome medications (${discontinued.join(', ')}). Retained ${retained.length} essential comfort medications (${retained.join(', ')}). Pill burden reduced from ${medList.length} to ${retained.length} daily oral formulations.`;
  }, [medList]);

  const handleApply = () => {
    if (onApplyDeprescribingPlan) {
      onApplyDeprescribingPlan(deprescribingSummary);
    }
    setAppliedToast(true);
    setTimeout(() => {
      setAppliedToast(false);
      if (onClose) onClose();
    }, 1200);
  };

  return (
    <div className="deprescribe-container">
      {/* Header */}
      <div className="deprescribe-header">
        <div className="deprescribe-title-group">
          <div className="flex items-center gap-2">
            <span className="deprescribe-kicker">Clinical Pharmacology · Polypharmacy Reduction</span>
            <span className="deprescribe-badge">STOPP-Frail &amp; OncPal Validated Criteria</span>
          </div>
          <h2 className="deprescribe-title">Palliative Deprescribing &amp; Drug Interaction Matrix</h2>
          <p className="deprescribe-subtitle">
            Systematic medication optimization for <strong>{patientName}</strong> ({patientId}). Reduces pill burden, prevents adverse drug events, and aligns pharmacotherapy with comfort care goals.
          </p>
        </div>
        {onClose && (
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close Matrix">
            ✕
          </button>
        )}
      </div>

      <div className="deprescribe-body">
        {/* Metric Strip */}
        <div className="deprescribe-metric-strip">
          <div className="metric-pill">
            <span className="metric-label">Current Pill Burden</span>
            <span className="metric-val">{activePillBurden} <span className="text-xs font-normal text-slate-500">/ {medList.length} meds</span></span>
          </div>

          <div className="metric-pill border-rose-200 bg-rose-50">
            <span className="metric-label text-rose-800">Deprescribe Recommended</span>
            <span className="metric-val text-rose-700">{deprescribeCount} <span className="text-xs font-normal">futile</span></span>
          </div>

          <div className="metric-pill border-amber-200 bg-amber-50">
            <span className="metric-label text-amber-800">Dose Reduce / Relax</span>
            <span className="metric-val text-amber-700">{reduceCount} <span className="text-xs font-normal">targets</span></span>
          </div>

          <div className="metric-pill border-emerald-200 bg-emerald-50">
            <span className="metric-label text-emerald-800">Essential Comfort Care</span>
            <span className="metric-val text-emerald-700">{maintainCount} <span className="text-xs font-normal">maintain</span></span>
          </div>
        </div>

        {/* 1-Click Fast Action Bar */}
        <div className="deprescribe-fast-bar">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="deprescribe-fast-btn"
              onClick={handle1ClickDeprescribeFutile}
            >
              <IconZap className="w-4 h-4 text-rose-500" />
              <span>1-Click Discontinue All {deprescribeCount} Futile Medications</span>
            </button>

            <span className="text-xs text-slate-500">
              (Discontinues Statins, Calcium, and Multivitamins in 1 click)
            </span>
          </div>

          {/* Filter tabs */}
          <div className="filter-tabs">
            <button
              type="button"
              className={`filter-tab ${activeFilter === 'all' ? 'is-active' : ''}`}
              onClick={() => setActiveFilter('all')}
            >
              All ({medList.length})
            </button>
            <button
              type="button"
              className={`filter-tab ${activeFilter === 'deprescribe' ? 'is-active text-rose-700' : ''}`}
              onClick={() => setActiveFilter('deprescribe')}
            >
              Deprescribe ({deprescribeCount})
            </button>
            <button
              type="button"
              className={`filter-tab ${activeFilter === 'reduce' ? 'is-active text-amber-700' : ''}`}
              onClick={() => setActiveFilter('reduce')}
            >
              Dose Reduce ({reduceCount})
            </button>
            <button
              type="button"
              className={`filter-tab ${activeFilter === 'maintain' ? 'is-active text-emerald-700' : ''}`}
              onClick={() => setActiveFilter('maintain')}
            >
              Maintain ({maintainCount})
            </button>
          </div>
        </div>

        {/* Medications Table / Cards */}
        <div className="med-grid">
          {filteredMeds.map((med) => {
            const tierPill =
              med.deprescribingTier === 'deprescribe'
                ? 'pill-deprescribe'
                : med.deprescribingTier === 'reduce'
                ? 'pill-reduce'
                : 'pill-maintain';

            const tierLabel =
              med.deprescribingTier === 'deprescribe'
                ? 'Deprescribe Futile'
                : med.deprescribingTier === 'reduce'
                ? 'Dose Reduce / Relax'
                : 'Essential Maintenance';

            return (
              <div
                key={med.id}
                className={`med-card ${!med.isActive ? 'is-discontinued' : ''}`}
              >
                <div className="med-head">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      className={`med-toggle-btn ${med.isActive ? 'is-active' : 'is-off'}`}
                      onClick={() => toggleMedActive(med.id)}
                      title={med.isActive ? 'Click to discontinue medication' : 'Click to re-activate medication'}
                    >
                      {med.isActive ? 'Active' : 'Discontinued'}
                    </button>
                    <div>
                      <h4 className="med-name">{med.name}</h4>
                      <span className="med-dose">{med.dose} · {med.frequency}</span>
                    </div>
                  </div>
                  <span className={`med-tier-badge ${tierPill}`}>{tierLabel}</span>
                </div>

                <p className="med-indication"><strong>Indication:</strong> {med.indication}</p>

                <div className="med-rationale-box">
                  <strong>Clinical Rationale:</strong> {med.guidelineRationale}
                  <div className="med-source">Source: {med.evidenceSource}</div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Drug-Drug Interaction Alerts */}
        <div className="interaction-section">
          <div className="flex items-center gap-2 mb-2 text-amber-900 font-bold text-xs uppercase tracking-wide">
            <IconShieldAlert className="w-4 h-4 text-amber-600" />
            <span>Active Drug-Drug Interaction Alerts ({DRUG_INTERACTIONS.length})</span>
          </div>

          <div className="interaction-grid">
            {DRUG_INTERACTIONS.map((int) => (
              <div key={int.id} className="interaction-card">
                <div className="flex items-center justify-between mb-1">
                  <strong>{int.drugs[0]} + {int.drugs[1]}</strong>
                  <span className="int-sev-chip">{int.severity.toUpperCase()}</span>
                </div>
                <p className="text-xs text-slate-600 mb-1">{int.mechanism}</p>
                <div className="text-xs font-semibold text-amber-800">
                  Recommendation: {int.clinicalAction}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Footer Actions */}
      <div className="deprescribe-footer">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <IconShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Compliant with OncPal Deprescribing &amp; British Geriatrics Society STOPP/START Criteria</span>
        </div>

        <div className="flex items-center gap-2">
          {onClose && (
            <button type="button" className="secondary-button" onClick={onClose}>
              Close
            </button>
          )}
          {onApplyDeprescribingPlan && (
            <button
              type="button"
              className="primary-button flex items-center gap-2"
              onClick={handleApply}
              disabled={appliedToast}
            >
              {appliedToast ? (
                <>
                  <IconCheckCircle className="w-4 h-4 text-white" />
                  <span>Deprescribing Plan Applied!</span>
                </>
              ) : (
                <>
                  <IconSparkles className="w-4 h-4" />
                  <span>Apply Deprescribing to Clinical Summary</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
