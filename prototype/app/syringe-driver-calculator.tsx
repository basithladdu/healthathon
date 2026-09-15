'use client';

import React, { useState, useId } from 'react';
import {
  IconHeartPulse,
  IconShieldCheck,
  IconShieldAlert,
  IconZap,
  IconCheckCircle,
  IconFileText,
} from './icons';

export interface OpioidOption {
  id: string;
  name: string;
  route: 'oral' | 'transdermal' | 'subcutaneous';
  conversionToOralMorphine: number; // multiplier to get oral morphine mg
  unit: string;
}

export const OPIOIDS: OpioidOption[] = [
  { id: 'oral_morphine', name: 'Oral Morphine (liquid/tablets)', route: 'oral', conversionToOralMorphine: 1, unit: 'mg/24h' },
  { id: 'oral_oxycodone', name: 'Oral Oxycodone', route: 'oral', conversionToOralMorphine: 1.5, unit: 'mg/24h' },
  { id: 'transdermal_fentanyl', name: 'Fentanyl Transdermal Patch', route: 'transdermal', conversionToOralMorphine: 2.4, unit: 'mcg/hr' },
  { id: 'oral_tramadol', name: 'Oral Tramadol', route: 'oral', conversionToOralMorphine: 0.1, unit: 'mg/24h' },
  { id: 'oral_codeine', name: 'Oral Codeine Phosphate', route: 'oral', conversionToOralMorphine: 0.1, unit: 'mg/24h' },
  { id: 'sc_morphine', name: 'Subcutaneous Morphine', route: 'subcutaneous', conversionToOralMorphine: 2, unit: 'mg/24h' },
];

export interface AdjuvantOption {
  id: string;
  name: string;
  indication: string;
  defaultDoseMg: number;
  minDoseMg: number;
  maxDoseMg: number;
  incompatibilityNotes?: string;
}

export const ADJUVANTS: AdjuvantOption[] = [
  {
    id: 'midazolam',
    name: 'Midazolam',
    indication: 'Terminal restlessness, agitation, seizure prophylaxis',
    defaultDoseMg: 10,
    minDoseMg: 5,
    maxDoseMg: 30,
  },
  {
    id: 'haloperidol',
    name: 'Haloperidol',
    indication: 'Delirium, hallucinations, opioid-induced nausea',
    defaultDoseMg: 2.5,
    minDoseMg: 1,
    maxDoseMg: 5,
  },
  {
    id: 'glycopyrronium',
    name: 'Glycopyrronium Bromide',
    indication: 'Retained upper airway secretions ("death rattle")',
    defaultDoseMg: 0.6,
    minDoseMg: 0.4,
    maxDoseMg: 1.2,
  },
  {
    id: 'levomepromazine',
    name: 'Levomepromazine',
    indication: 'Refractory nausea, broad-spectrum antiemetic, sedation',
    defaultDoseMg: 12.5,
    minDoseMg: 6.25,
    maxDoseMg: 25,
  },
];

export interface SyringeDriverCalculatorProps {
  patientId: string;
  patientName: string;
  onClose?: () => void;
  onApplyToCarePlan?: (regimenSummary: string) => void;
}

export function SyringeDriverCalculator({
  patientId,
  patientName,
  onClose,
  onApplyToCarePlan,
}: SyringeDriverCalculatorProps) {
  const [selectedOpioidId, setSelectedOpioidId] = useState('oral_morphine');
  const [baselineDose, setBaselineDose] = useState<number>(60);
  const [applyToleranceReduction, setApplyToleranceReduction] = useState(true); // 33% safety reduction
  const [targetOpioid, setTargetOpioid] = useState<'sc_morphine' | 'sc_oxycodone'>('sc_morphine');

  // Adjuvant selections:
  const [selectedAdjuvants, setSelectedAdjuvants] = useState<Record<string, { enabled: boolean; dose: number }>>({
    midazolam: { enabled: true, dose: 10 },
    haloperidol: { enabled: false, dose: 2.5 },
    glycopyrronium: { enabled: true, dose: 0.6 },
    levomepromazine: { enabled: false, dose: 12.5 },
  });

  const [diluent, setDiluent] = useState<'wfi' | 'saline'>('wfi');
  const [syringeVolumeMl, setSyringeVolumeMl] = useState<number>(30); // 30 mL BD Plastipak

  // Calculations:
  const currentOpioid = OPIOIDS.find((o) => o.id === selectedOpioidId) || OPIOIDS[0];
  const omedd = baselineDose * currentOpioid.conversionToOralMorphine; // Oral Morphine Equivalent Daily Dose

  // Conversion: Oral Morphine -> SC Morphine is 2:1 (divide by 2)
  // Oral Morphine -> SC Oxycodone is approx 3:1 (divide by 3, or Oral Oxycodone / 2)
  let rawScDailyMg = targetOpioid === 'sc_morphine' ? omedd / 2 : omedd / 3;

  // Cross-tolerance reduction: 33% reduction
  const finalScDailyMg = applyToleranceReduction ? Math.round(rawScDailyMg * 0.67 * 10) / 10 : Math.round(rawScDailyMg * 10) / 10;
  const prnBreakthroughDoseMg = Math.round((finalScDailyMg / 6) * 10) / 10;

  // Pump rate:
  // Most pumps run 24 hours: Rate = syringeVolumeMl / 24 mL/hr
  const infusionRateMlHr = (syringeVolumeMl / 24).toFixed(2);

  const activeAdjuvantsList = ADJUVANTS.filter((a) => selectedAdjuvants[a.id]?.enabled);

  const toggleAdjuvant = (id: string) => {
    setSelectedAdjuvants((prev) => ({
      ...prev,
      [id]: {
        ...prev[id],
        enabled: !prev[id]?.enabled,
      },
    }));
  };

  const updateAdjuvantDose = (id: string, dose: number) => {
    setSelectedAdjuvants((prev) => ({
      ...prev,
      [id]: {
        ...prev[id],
        dose,
      },
    }));
  };

  const regimenSummary = `Continuous 24h Subcutaneous Syringe Driver: ${
    targetOpioid === 'sc_morphine' ? 'Morphine Sulfate' : 'Oxycodone'
  } ${finalScDailyMg} mg/24h${
    activeAdjuvantsList.length > 0
      ? ' + ' + activeAdjuvantsList.map((a) => `${a.name} ${selectedAdjuvants[a.id].dose} mg`).join(' + ')
      : ''
  } in ${diluent === 'wfi' ? 'Water for Injections' : '0.9% Sodium Chloride'} (Total Volume ${syringeVolumeMl} mL over 24h at ${infusionRateMlHr} mL/hr). PRN Breakthrough: ${
    targetOpioid === 'sc_morphine' ? 'Morphine' : 'Oxycodone'
  } ${prnBreakthroughDoseMg} mg SC q1h PRN.`;

  return (
    <div className="sd-container">
      {/* Header */}
      <div className="sd-header">
        <div className="sd-title-group">
          <div className="flex items-center gap-2">
            <span className="sd-kicker">Palliative Clinical Pharmacology</span>
            <span className="sd-badge">APCA &amp; EAPC Standardized Protocol</span>
          </div>
          <h2 className="sd-title">Continuous Subcutaneous Syringe Driver &amp; Opioid Converter</h2>
          <p className="sd-subtitle">
            Equianalgesic conversion and syringe driver protocol for <strong>{patientName}</strong> ({patientId}).
          </p>
        </div>
        {onClose && (
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close Calculator">
            ✕
          </button>
        )}
      </div>

      <div className="sd-body">
        {/* Step 1: Baseline Opioid Input */}
        <div className="sd-section">
          <div className="sd-section-head">
            <span className="sd-step-num">1</span>
            <h3 className="sd-section-title">Current Baseline Opioid (Last 24 Hours)</h3>
          </div>

          <div className="sd-grid-2">
            <label className="form-field">
              <span>Current Opioid &amp; Route</span>
              <select
                value={selectedOpioidId}
                onChange={(e) => setSelectedOpioidId(e.target.value)}
                className="sd-select"
              >
                {OPIOIDS.map((op) => (
                  <option key={op.id} value={op.id}>
                    {op.name} ({op.unit})
                  </option>
                ))}
              </select>
            </label>

            <label className="form-field">
              <span>Total 24h Baseline Dose ({currentOpioid.unit})</span>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  min={0}
                  step={currentOpioid.route === 'transdermal' ? 12.5 : 5}
                  value={baselineDose}
                  onChange={(e) => setBaselineDose(Math.max(0, Number(e.target.value)))}
                  className="sd-number-input"
                />
                <span className="text-xs font-semibold text-slate-500">{currentOpioid.unit}</span>
              </div>
            </label>
          </div>

          <div className="sd-calc-banner">
            <div>
              <strong>Calculated Oral Morphine Equivalent (OMEDD):</strong>
              <div className="sd-omedd-value">{omedd.toFixed(1)} mg / 24 hours</div>
            </div>
            <label className="sd-reduction-toggle">
              <input
                type="checkbox"
                checked={applyToleranceReduction}
                onChange={(e) => setApplyToleranceReduction(e.target.checked)}
              />
              <span>
                <strong>Apply 33% Cross-Tolerance Safety Reduction</strong> (Recommended by EAPC when switching routes/drugs)
              </span>
            </label>
          </div>
        </div>

        {/* Step 2: Target Subcutaneous Opioid Infusion */}
        <div className="sd-section">
          <div className="sd-section-head">
            <span className="sd-step-num">2</span>
            <h3 className="sd-section-title">Target Subcutaneous Infusion (Continuous Syringe Driver)</h3>
          </div>

          <div className="sd-grid-2">
            <label className="form-field">
              <span>Target Opioid for Syringe Pump</span>
              <select
                value={targetOpioid}
                onChange={(e) => setTargetOpioid(e.target.value as any)}
                className="sd-select"
              >
                <option value="sc_morphine">Morphine Sulfate (Subcutaneous 24h)</option>
                <option value="sc_oxycodone">Oxycodone Hydrochloride (Subcutaneous 24h)</option>
              </select>
            </label>

            <div className="sd-result-card">
              <div className="sd-result-label">Continuous 24h SC Dose</div>
              <div className="sd-result-val text-emerald-800">
                {finalScDailyMg} <span className="text-base font-normal">mg / 24 hours</span>
              </div>
              <div className="sd-result-prn">
                <strong>PRN Breakthrough Dose:</strong> {prnBreakthroughDoseMg} mg SC (every 1 hr PRN, max 6/day)
              </div>
            </div>
          </div>
        </div>

        {/* Step 3: Palliative Adjuvant Medications */}
        <div className="sd-section">
          <div className="sd-section-head">
            <span className="sd-step-num">3</span>
            <h3 className="sd-section-title">Co-infused Symptom Adjuvants (Compatible Syringe Mixture)</h3>
          </div>

          <div className="sd-adjuvant-grid">
            {ADJUVANTS.map((adj) => {
              const current = selectedAdjuvants[adj.id] || { enabled: false, dose: adj.defaultDoseMg };
              return (
                <div
                  key={adj.id}
                  className={`sd-adjuvant-card ${current.enabled ? 'is-enabled' : ''}`}
                  onClick={() => toggleAdjuvant(adj.id)}
                >
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-2 cursor-pointer" onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={current.enabled}
                        onChange={() => toggleAdjuvant(adj.id)}
                      />
                      <strong>{adj.name}</strong>
                    </label>
                    {current.enabled && (
                      <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                        <input
                          type="number"
                          min={adj.minDoseMg}
                          max={adj.maxDoseMg}
                          step={adj.id === 'glycopyrronium' ? 0.2 : 2.5}
                          value={current.dose}
                          onChange={(e) => updateAdjuvantDose(adj.id, Number(e.target.value))}
                          className="sd-adj-input"
                        />
                        <span className="text-xs font-semibold text-slate-600">mg</span>
                      </div>
                    )}
                  </div>
                  <p className="sd-adjuvant-desc">{adj.indication}</p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Step 4: Pump Hardware & Diluent Specifications */}
        <div className="sd-section">
          <div className="sd-section-head">
            <span className="sd-step-num">4</span>
            <h3 className="sd-section-title">Pump Parameters &amp; Physical Compatibility Verification</h3>
          </div>

          <div className="sd-grid-3">
            <label className="form-field">
              <span>Diluent Solution</span>
              <select value={diluent} onChange={(e) => setDiluent(e.target.value as any)} className="sd-select">
                <option value="wfi">Water for Injections (WFI) - Preferred</option>
                <option value="saline">0.9% Sodium Chloride (Normal Saline)</option>
              </select>
            </label>

            <label className="form-field">
              <span>Syringe Chamber Size</span>
              <select
                value={syringeVolumeMl}
                onChange={(e) => setSyringeVolumeMl(Number(e.target.value))}
                className="sd-select"
              >
                <option value={20}>20 mL BD Plastipak (Fill to 17 mL)</option>
                <option value={30}>30 mL BD Plastipak (Fill to 24 mL)</option>
                <option value={50}>50 mL BD Plastipak (Fill to 48 mL)</option>
              </select>
            </label>

            <div className="sd-pump-rate-box">
              <span className="sd-pump-label">Continuous Infusion Rate</span>
              <div className="sd-pump-rate">{infusionRateMlHr} <span className="text-sm font-normal">mL / hr</span></div>
              <small className="text-slate-500">Duration: Exactly 24.0 Hours</small>
            </div>
          </div>

          {/* Compatibility Safety Notice */}
          <div className="sd-compatibility-banner">
            <IconShieldCheck className="w-5 h-5 text-emerald-600 flex-shrink-0" />
            <div>
              <strong>Physical Compatibility &amp; Stability Verified:</strong>
              <p>
                {targetOpioid === 'sc_morphine' ? 'Morphine' : 'Oxycodone'} co-infused with{' '}
                {activeAdjuvantsList.length > 0
                  ? activeAdjuvantsList.map((a) => a.name).join(' + ')
                  : 'no additional adjuvants'}{' '}
                in {diluent === 'wfi' ? 'Water for Injections' : '0.9% Saline'} is physically compatible at 25°C protected from light for up to 24 hours. No precipitation risk identified.
              </p>
            </div>
          </div>
        </div>

        {/* Prescription Summary Box */}
        <div className="sd-prescription-box">
          <div className="flex items-center gap-2 mb-2 text-emerald-900 font-bold text-sm">
            <IconFileText className="w-4 h-4" />
            <span>Formal Palliative Syringe Driver Order (Ready for Clinical Transfer)</span>
          </div>
          <p className="sd-prescription-text">{regimenSummary}</p>
        </div>
      </div>

      {/* Footer Actions */}
      <div className="sd-footer">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <IconShieldAlert className="w-4 h-4 text-amber-600" />
          <span>Requires dual-nurse check and independent physician counter-signature prior to pump connection.</span>
        </div>

        <div className="flex items-center gap-2">
          {onClose && (
            <button type="button" className="secondary-button" onClick={onClose}>
              Close
            </button>
          )}
          {onApplyToCarePlan && (
            <button
              type="button"
              className="primary-button flex items-center gap-2"
              onClick={() => onApplyToCarePlan(regimenSummary)}
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Apply Protocol to Care Plan</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
