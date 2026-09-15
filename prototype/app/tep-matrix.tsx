'use client';

import React, { useState } from 'react';
import {
  IconHeartPulse,
  IconLungs,
  IconZap,
  IconHospital,
  IconShieldCheck,
  IconShieldAlert,
  IconCheckCircle,
  IconStethoscope,
} from './icons';

export interface TepModality {
  id: string;
  name: string;
  category: string;
  icon: React.ComponentType<{ className?: string }>;
  levels: {
    level: 1 | 2 | 3;
    title: string;
    description: string;
    clinicalRationale: string;
  }[];
}

export const TEP_MODALITIES: TepModality[] = [
  {
    id: 'cpr',
    name: 'Cardiopulmonary Resuscitation',
    category: 'Cardiac Arrest',
    icon: IconHeartPulse,
    levels: [
      {
        level: 1,
        title: 'DNACPR (Comfort Care Only)',
        description: 'No closed-chest compressions or electrical cardioversion in cardiac arrest.',
        clinicalRationale: 'Focus exclusively on peaceful natural passing with anticipatory syringe driver medication.',
      },
      {
        level: 2,
        title: 'Defibrillation Only (Shockable Rhythms)',
        description: 'Rapid shock delivery for VF/VT; no prolonged mechanical CPR or invasive intubation.',
        clinicalRationale: 'Allows treatment of sudden arrhythmias with rapid return of spontaneous circulation.',
      },
      {
        level: 3,
        title: 'Full Resuscitation (ACLS Protocol)',
        description: 'Complete ALS protocol including chest compressions, vasopressors, and advanced airway.',
        clinicalRationale: 'Recommended when acute deterioration is judged fully reversible.',
      },
    ],
  },
  {
    id: 'airway',
    name: 'Ventilatory & Airway Support',
    category: 'Respiratory Support',
    icon: IconLungs,
    levels: [
      {
        level: 1,
        title: 'Ward Comfort Oxygen (≤40% FiO2)',
        description: 'Nasal prongs, simple face mask, and subcutaneous opioids/glycopyrronium for secretions.',
        clinicalRationale: 'Relieves sensation of dyspnea without burdensome machine interfaces.',
      },
      {
        level: 2,
        title: 'Non-Invasive Ventilation (NIV / HFNC)',
        description: 'High-Flow Nasal Cannula or trial of CPAP/BiPAP on HDU ward; ceiling of non-invasive.',
        clinicalRationale: 'Supports acute hypercapnic or hypoxemic respiratory failure without sedation.',
      },
      {
        level: 3,
        title: 'Invasive Mechanical Ventilation (ICU)',
        description: 'Endotracheal intubation, neuromuscular blockade, and prolonged mechanical ventilation.',
        clinicalRationale: 'Full organ support for acute acute distress syndrome (ARDS) or post-op collapse.',
      },
    ],
  },
  {
    id: 'vasopressors',
    name: 'Hemodynamic Support & Inotropes',
    category: 'Circulatory Failure',
    icon: IconZap,
    levels: [
      {
        level: 1,
        title: 'Conservative Hydration',
        description: 'Gentle subcutaneous / IV crystalloid rehydration up to 1L/24h to avoid pulmonary edema.',
        clinicalRationale: 'Avoids fluid overload and worsening peripheral edema in advanced organ failure.',
      },
      {
        level: 2,
        title: 'Peripheral Vasopressors (HDU)',
        description: 'Low-to-moderate peripheral vasopressor infusion (noradrenaline) on stepdown unit.',
        clinicalRationale: 'Maintains minimum mean arterial pressure (MAP ≥65 mmHg) for acute transient sepsis.',
      },
      {
        level: 3,
        title: 'Central Line Multi-Agent Inotropes (ICU)',
        description: 'Central venous line insertion, arterial line monitoring, high-dose pressors and inotropes.',
        clinicalRationale: 'Comprehensive circulatory salvage in distributive or cardiogenic shock.',
      },
    ],
  },
  {
    id: 'renal',
    name: 'Renal Replacement Therapy',
    category: 'Kidney Function',
    icon: IconStethoscope,
    levels: [
      {
        level: 1,
        title: 'Conservative Management (No Dialysis)',
        description: 'Medical management of hyperkalemia and uremia; palliative diuretic titrations.',
        clinicalRationale: 'Spares patient discomfort of vascular access and hemodialysis cycles.',
      },
      {
        level: 2,
        title: 'Acute Temporary Hemodialysis',
        description: 'Short-term trial of intermittent hemodialysis for clearly reversible toxic or drug insult.',
        clinicalRationale: 'Time-limited trial to bridge acute kidney injury back to baseline function.',
      },
      {
        level: 3,
        title: 'Continuous Renal Replacement (CRRT)',
        description: 'Continuous venovenous hemofiltration (CVVH) with systemic anticoagulation in ICU.',
        clinicalRationale: 'Critical care nephrology for multi-organ dysfunction syndrome.',
      },
    ],
  },
  {
    id: 'location',
    name: 'Care Setting Ceiling',
    category: 'Discharge & Transfer',
    icon: IconHospital,
    levels: [
      {
        level: 1,
        title: 'Home / Hospice Palliative Ward',
        description: 'Ceiling of care is home palliative care or specialized inpatient hospice.',
        clinicalRationale: 'Avoids emergency department transitions and distressing hospital sirens.',
      },
      {
        level: 2,
        title: 'General Oncology Ward / High Dependency Unit',
        description: 'Active acute hospital admissions on oncology ward; no physical ICU transfer.',
        clinicalRationale: 'Provides inpatient medical care while preserving dignifying boundaries.',
      },
      {
        level: 3,
        title: 'Intensive Care Unit (ICU / ITU)',
        description: 'Transfer to adult critical care unit without location restriction.',
        clinicalRationale: 'Full access to intensive care nursing and invasive monitoring.',
      },
    ],
  },
];

export interface TepMatrixProps {
  patientId: string;
  patientName: string;
  readOnly?: boolean;
  initialSelections?: Record<string, 1 | 2 | 3>;
  onSave?: (selections: Record<string, 1 | 2 | 3>, overallCeiling: string) => void;
  onClose?: () => void;
}

export function TreatmentEscalationMatrix({
  patientId,
  patientName,
  readOnly = false,
  initialSelections,
  onSave,
  onClose,
}: TepMatrixProps) {
  const [selections, setSelections] = useState<Record<string, 1 | 2 | 3>>(() => {
    if (initialSelections) return initialSelections;
    return {
      cpr: 1, // DNACPR default for palliative care patients
      airway: 2, // NIV ceiling
      vasopressors: 1, // Conservative
      renal: 1, // No dialysis
      location: 2, // Ward ceiling
    };
  });

  const [notes, setNotes] = useState(
    'Discussed with patient and family proxy. Overall agreement reached for Ward/HDU ceiling with active non-invasive symptom relief.'
  );

  const [savedSuccess, setSavedSuccess] = useState(false);

  // Determine overall ceiling:
  const maxLevel = Math.max(...Object.values(selections)) as 1 | 2 | 3;
  const overallTier =
    maxLevel === 3
      ? 'Tier 3 · Full ICU Escalation'
      : maxLevel === 2
      ? 'Tier 2 · HDU / Ward Ceiling (No Invasive ICU/Intubation)'
      : 'Tier 1 · Comfort & Palliative Ceiling (Symptom-Focused)';

  const overallTierColor =
    maxLevel === 3
      ? 'badge-tier-3'
      : maxLevel === 2
      ? 'badge-tier-2'
      : 'badge-tier-1';

  const handleLevelSelect = (modalityId: string, level: 1 | 2 | 3) => {
    if (readOnly) return;
    setSelections((prev) => ({ ...prev, [modalityId]: level }));
  };

  const handleSave = () => {
    if (onSave) {
      onSave(selections, overallTier);
    }
    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      if (onClose) onClose();
    }, 1200);
  };

  return (
    <div className="tep-container">
      {/* Header Banner */}
      <div className="tep-header">
        <div className="tep-title-group">
          <div className="flex items-center gap-2">
            <span className="tep-kicker">Clinical Governance · Resuscitation Council</span>
            <span className={`tep-overall-pill ${overallTierColor}`}>{overallTier}</span>
          </div>
          <h2 className="tep-title">Treatment Escalation Plan (TEP) Matrix</h2>
          <p className="tep-subtitle">
            Organ-system ceiling boundaries for <strong>{patientName}</strong> ({patientId}). Specifies actionable thresholds for emergency and on-call teams.
          </p>
        </div>
        {onClose && (
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close TEP Matrix">
            ✕
          </button>
        )}
      </div>

      {/* Grid of Modalities */}
      <div className="tep-grid">
        {TEP_MODALITIES.map((modality) => {
          const Icon = modality.icon;
          const currentSelection = selections[modality.id] ?? 1;

          return (
            <div key={modality.id} className="tep-modality-card">
              <div className="tep-modality-head">
                <div className="flex items-center gap-2">
                  <div className="tep-icon-wrap">
                    <Icon className="w-4 h-4 text-emerald-800" />
                  </div>
                  <div>
                    <h3 className="tep-modality-name">{modality.name}</h3>
                    <span className="tep-modality-category">{modality.category}</span>
                  </div>
                </div>
                <span className={`tep-active-level-chip level-${currentSelection}`}>
                  Level {currentSelection}
                </span>
              </div>

              <div className="tep-levels-group">
                {modality.levels.map((lvl) => {
                  const isSelected = currentSelection === lvl.level;
                  return (
                    <button
                      key={lvl.level}
                      type="button"
                      disabled={readOnly}
                      className={`tep-level-btn ${isSelected ? 'is-selected' : ''} ${
                        readOnly ? 'is-readonly' : ''
                      }`}
                      onClick={() => handleLevelSelect(modality.id, lvl.level)}
                    >
                      <div className="tep-level-head">
                        <span className={`tep-radio-dot ${isSelected ? 'is-active' : ''}`} />
                        <strong className="tep-level-title">{lvl.title}</strong>
                      </div>
                      <p className="tep-level-desc">{lvl.description}</p>
                      {isSelected && (
                        <div className="tep-rationale-box">
                          <strong>Clinical Intent:</strong> {lvl.clinicalRationale}
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Clinical Notes & Rationale */}
      <div className="tep-notes-section">
        <label className="form-field">
          <span>Attending Physician Rationale &amp; Multidisciplinary Consensus</span>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            disabled={readOnly}
            rows={2}
            className="tep-notes-input"
            placeholder="Record clinician notes regarding discussions with patient and family..."
          />
        </label>
      </div>

      {/* Footer Controls */}
      <div className="tep-footer">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <IconShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Compliant with Resuscitation Council UK &amp; Indian Council of Medical Research (ICMR) Guidelines</span>
        </div>

        <div className="flex items-center gap-2">
          {onClose && (
            <button type="button" className="secondary-button" onClick={onClose}>
              Cancel
            </button>
          )}
          {!readOnly && (
            <button
              type="button"
              className="primary-button flex items-center gap-2"
              onClick={handleSave}
              disabled={savedSuccess}
            >
              {savedSuccess ? (
                <>
                  <IconCheckCircle className="w-4 h-4 text-white" />
                  <span>TEP Plan Registered!</span>
                </>
              ) : (
                <>
                  <IconShieldCheck className="w-4 h-4" />
                  <span>Save Treatment Escalation Plan</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
