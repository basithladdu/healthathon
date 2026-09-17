'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconShieldAlert,
  IconCheckCircle,
  IconSparkles,
  IconClock,
  IconUsers,
} from './icons';

export type CalciumUnit = 'mg_dl' | 'mmol_l';
export type HypercalcemiaSeverity = 'normal' | 'mild' | 'moderate' | 'severe_crisis';
export type AntiresorptiveChoice = 'zoledronic_acid' | 'denosumab' | 'pamidronate';

interface MalignantHypercalcemiaModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function MalignantHypercalcemiaModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: MalignantHypercalcemiaModalProps) {
  const [activeTab, setActiveTab] = useState<
    'calculator_staging' | 'saline_resuscitation' | 'antiresorptive_dosing' | 'calcitonin_steroids' | 'ecg_cardiac_telemetry' | 'family_counseling'
  >('calculator_staging');

  // Lab inputs
  const [calciumUnit, setCalciumUnit] = useState<CalciumUnit>('mg_dl');
  const [totalCalciumInput, setTotalCalciumInput] = useState<number>(13.8); // in mg/dL
  const [serumAlbuminInput, setSerumAlbuminInput] = useState<number>(2.6); // in g/dL
  
  // Renal / Patient parameters
  const [patientAge, setPatientAge] = useState<number>(64);
  const [patientWeightKg, setPatientWeightKg] = useState<number>(58);
  const [patientGender, setPatientGender] = useState<'female' | 'male'>('female');
  const [serumCreatinine, setSerumCreatinine] = useState<number>(1.8); // mg/dL
  
  // Clinical Symptoms Checklist
  const [symptoms, setSymptoms] = useState<{ [key: string]: boolean }>({
    altered_mental_status: true,
    nausea_vomiting: true,
    severe_dehydration: true,
    constipation: true,
    polyuria_polydipsia: true,
    cardiac_palpitations: false,
    muscle_weakness: true,
  });

  // Suspected HCM Mechanism
  const [hcmMechanism, setHcmMechanism] = useState<'hhm_pthrp' | 'osteolytic_bone' | 'calcitriol_lymphoma' | 'unknown'>('hhm_pthrp');

  // Language toggle for counseling
  const [counselingLang, setCounselingLang] = useState<'en' | 'hi'>('en');

  // Corrected Calcium Calculation
  // Standard equation: Ca_corr (mg/dL) = Total Ca + 0.8 * (4.0 - Albumin g/dL)
  // Standard equation: Ca_corr (mmol/L) = Total Ca (mmol/L) + 0.02 * (40 - Albumin g/L)
  const correctedCalciumMgDl = useMemo(() => {
    let rawCa = totalCalciumInput;
    if (calciumUnit === 'mmol_l') {
      rawCa = totalCalciumInput * 4.008; // convert mmol/L to mg/dL for standardized math
    }
    const corr = rawCa + 0.8 * (4.0 - serumAlbuminInput);
    return Math.round(corr * 100) / 100;
  }, [totalCalciumInput, serumAlbuminInput, calciumUnit]);

  const correctedCalciumMmolL = useMemo(() => {
    return Math.round((correctedCalciumMgDl / 4.008) * 100) / 100;
  }, [correctedCalciumMgDl]);

  // Severity Staging
  const severity: HypercalcemiaSeverity = useMemo(() => {
    if (correctedCalciumMgDl < 10.3) return 'normal';
    if (correctedCalciumMgDl < 12.0) return 'mild';
    if (correctedCalciumMgDl < 14.0) return 'moderate';
    return 'severe_crisis';
  }, [correctedCalciumMgDl]);

  // Cockcroft-Gault CrCl calculation
  // CrCl = [(140 - Age) * Weight (kg)] / [72 * Creatinine (mg/dL)] * (0.85 if female)
  const creatinineClearance = useMemo(() => {
    if (serumCreatinine <= 0) return 60;
    const base = ((140 - patientAge) * patientWeightKg) / (72 * serumCreatinine);
    const result = patientGender === 'female' ? base * 0.85 : base;
    return Math.round(result * 10) / 10;
  }, [patientAge, patientWeightKg, patientGender, serumCreatinine]);

  // Recommended Zoledronic Acid Dose based on CrCl
  const zoledronicRecommendation = useMemo(() => {
    if (creatinineClearance >= 60) {
      return {
        dose: '4.0 mg IV',
        infusionTime: 'Over at least 15 minutes in 100 mL 0.9% NaCl',
        safety: 'Standard Full Dose',
        safe: true,
      };
    }
    if (creatinineClearance >= 50) {
      return {
        dose: '3.5 mg IV',
        infusionTime: 'Over at least 15 minutes in 100 mL 0.9% NaCl',
        safety: 'Mild Renal Impairment (CrCl 50-59 mL/min)',
        safe: true,
      };
    }
    if (creatinineClearance >= 40) {
      return {
        dose: '3.3 mg IV',
        infusionTime: 'Over at least 15 minutes in 100 mL 0.9% NaCl',
        safety: 'Moderate Renal Impairment (CrCl 40-49 mL/min)',
        safe: true,
      };
    }
    if (creatinineClearance >= 30) {
      return {
        dose: '3.0 mg IV',
        infusionTime: 'Over at least 20-30 minutes in 100 mL 0.9% NaCl',
        safety: 'Severe Renal Impairment (CrCl 30-39 mL/min) - High Monitoring Required',
        safe: true,
      };
    }
    return {
      dose: 'CONTRAINDICATED',
      infusionTime: 'Not recommended due to severe acute tubular necrosis risk',
      safety: 'Critical Renal Impairment (CrCl < 30 mL/min) - Switch to Denosumab',
      safe: false,
    };
  }, [creatinineClearance]);

  // Recommended Antiresorptive Choice
  const recommendedAgent: AntiresorptiveChoice = useMemo(() => {
    if (creatinineClearance < 30) return 'denosumab';
    return 'zoledronic_acid';
  }, [creatinineClearance]);

  // Calcitonin weight-based calculation (4 to 8 IU/kg q12h)
  const calcitoninDose = useMemo(() => {
    const minDose = Math.round(patientWeightKg * 4);
    const maxDose = Math.round(patientWeightKg * 8);
    return { minDose, maxDose };
  }, [patientWeightKg]);

  // Fluid Deficit Estimation
  const fluidDeficitLiters = useMemo(() => {
    if (severity === 'severe_crisis') return 4.5;
    if (severity === 'moderate') return 3.0;
    if (severity === 'mild') return 1.5;
    return 0.5;
  }, [severity]);

  // QTc Calculation & Visualizer dynamics
  // Normal QTc is ~400-420 ms. Hypercalcemia shortens ST segment and QTc.
  // When Ca exceeds 14 mg/dL, QTc shortens to <350 ms, ST segment nearly disappears.
  const estimatedQTcMs = useMemo(() => {
    // Normal baseline: 420 ms at Ca 9.5
    // Each 1 mg/dL above 10 reduces QTc by ~18 ms
    const excessCa = Math.max(0, correctedCalciumMgDl - 9.5);
    const qtc = Math.max(300, Math.round(420 - excessCa * 18));
    return qtc;
  }, [correctedCalciumMgDl]);

  const handleUnitToggle = (newUnit: CalciumUnit) => {
    if (newUnit === calciumUnit) return;
    if (newUnit === 'mmol_l') {
      setTotalCalciumInput(Math.round((totalCalciumInput / 4.008) * 100) / 100);
    } else {
      setTotalCalciumInput(Math.round((totalCalciumInput * 4.008) * 10) / 10);
    }
    setCalciumUnit(newUnit);
  };

  const handleDispatch = () => {
    const summary = `Hypercalcemia (${severity.toUpperCase()} - Corrected Ca: ${correctedCalciumMgDl} mg/dL [${correctedCalciumMmolL} mmol/L]). Saline rate: 200-250 mL/h. Antiresorptive: ${recommendedAgent === 'denosumab' ? 'Denosumab 120mg SC' : `Zoledronic Acid ${zoledronicRecommendation.dose}`}. Calcitonin: ${calcitoninDose.minDose}-${calcitoninDose.maxDose} IU SC q12h (max 48h).`;
    if (onDispatchPlan) {
      onDispatchPlan(summary);
    }
    onClose();
  };

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="hypercalcemia-suite-title"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.75)',
        backdropFilter: 'blur(6px)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        overflowY: 'auto',
      }}
    >
      <div
        className="modal-card"
        style={{
          width: '100%',
          maxWidth: '1020px',
          maxHeight: '92vh',
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid #e2e8f0',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {/* Header Bar */}
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid #e2e8f0',
            background: severity === 'severe_crisis' ? 'linear-gradient(135deg, #7f1d1d 0%, #991b1b 100%)' : severity === 'moderate' ? 'linear-gradient(135deg, #9a3412 0%, #c2410c 100%)' : 'linear-gradient(135deg, #1e293b 0%, #334155 100%)',
            color: '#ffffff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '4px 10px',
                  borderRadius: '9999px',
                  fontSize: '11px',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                  background: severity === 'severe_crisis' ? '#fef2f2' : '#fef3c7',
                  color: severity === 'severe_crisis' ? '#b91c1c' : '#92400e',
                }}
              >
                <IconShieldAlert className="w-3.5 h-3.5" />
                {severity === 'severe_crisis' ? '🚨 Severe Hypercalcemic Crisis' : severity === 'moderate' ? '⚠️ Moderate Hypercalcemia' : severity === 'mild' ? '⚡ Mild Hypercalcemia' : '✅ Normocalcemia'}
              </span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>
                Patient: <strong style={{ color: '#ffffff' }}>{patientName}</strong> ({patientId})
              </span>
              <span style={{ fontSize: '12px', color: '#94a3b8' }}>·</span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>{primaryCancer}</span>
            </div>
            <h1
              id="hypercalcemia-suite-title"
              style={{
                margin: '8px 0 2px 0',
                fontSize: '22px',
                fontWeight: 800,
                letterSpacing: '-0.02em',
                color: '#ffffff',
              }}
            >
              Malignant Hypercalcemia &amp; Nephro-Oncology Rescue Suite
            </h1>
            <p style={{ margin: 0, fontSize: '13px', color: '#e2e8f0', opacity: 0.9 }}>
              Albumin-corrected calcium calculator, Cockcroft-Gault renal bisphosphonate safety, calcitonin bridging, and dynamic ECG QTc shortening telemetry.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            style={{
              background: 'rgba(255, 255, 255, 0.15)',
              border: 'none',
              borderRadius: '8px',
              color: '#ffffff',
              padding: '6px 12px',
              fontSize: '14px',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            ✕ Close
          </button>
        </div>

        {/* Tab Navigation */}
        <div
          style={{
            display: 'flex',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
            padding: '0 16px',
            overflowX: 'auto',
          }}
        >
          <button
            type="button"
            onClick={() => setActiveTab('calculator_staging')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'calculator_staging' ? 700 : 500,
              color: activeTab === 'calculator_staging' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'calculator_staging' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🧮 1. Corrected Ca &amp; Staging
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('saline_resuscitation')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'saline_resuscitation' ? 700 : 500,
              color: activeTab === 'saline_resuscitation' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'saline_resuscitation' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            💧 2. IV Saline &amp; Electrolytes
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('antiresorptive_dosing')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'antiresorptive_dosing' ? 700 : 500,
              color: activeTab === 'antiresorptive_dosing' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'antiresorptive_dosing' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🛡️ 3. Zoledronate vs Denosumab
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('calcitonin_steroids')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'calcitonin_steroids' ? 700 : 500,
              color: activeTab === 'calcitonin_steroids' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'calcitonin_steroids' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            ⏱️ 4. Calcitonin &amp; Steroids
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('ecg_cardiac_telemetry')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'ecg_cardiac_telemetry' ? 700 : 500,
              color: activeTab === 'ecg_cardiac_telemetry' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'ecg_cardiac_telemetry' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            📈 5. ECG QTc Visualizer
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('family_counseling')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'family_counseling' ? 700 : 500,
              color: activeTab === 'family_counseling' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'family_counseling' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🗣️ 6. Counseling (हिन्दी / EN)
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
          
          {/* TAB 1: CALCULATOR & STAGING */}
          {activeTab === 'calculator_staging' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Primary Score Board Banner */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                  gap: '16px',
                  background: severity === 'severe_crisis' ? '#fef2f2' : severity === 'moderate' ? '#fff7ed' : '#f0fdf4',
                  border: `2px solid ${severity === 'severe_crisis' ? '#f87171' : severity === 'moderate' ? '#fdba74' : '#86efac'}`,
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div>
                  <span style={{ fontSize: '12px', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>
                    Corrected Calcium (mg/dL)
                  </span>
                  <div style={{ fontSize: '36px', fontWeight: 800, color: severity === 'severe_crisis' ? '#dc2626' : severity === 'moderate' ? '#ea580c' : '#16a34a', lineHeight: 1.1 }}>
                    {correctedCalciumMgDl} <span style={{ fontSize: '18px', fontWeight: 500 }}>mg/dL</span>
                  </div>
                  <div style={{ fontSize: '13px', color: '#475569', marginTop: '4px' }}>
                    Normal Reference: 8.5 – 10.2 mg/dL
                  </div>
                </div>

                <div>
                  <span style={{ fontSize: '12px', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>
                    SI Units (mmol/L)
                  </span>
                  <div style={{ fontSize: '36px', fontWeight: 800, color: severity === 'severe_crisis' ? '#dc2626' : severity === 'moderate' ? '#ea580c' : '#16a34a', lineHeight: 1.1 }}>
                    {correctedCalciumMmolL} <span style={{ fontSize: '18px', fontWeight: 500 }}>mmol/L</span>
                  </div>
                  <div style={{ fontSize: '13px', color: '#475569', marginTop: '4px' }}>
                    Normal Reference: 2.15 – 2.55 mmol/L
                  </div>
                </div>

                <div>
                  <span style={{ fontSize: '12px', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>
                    Severity Classification
                  </span>
                  <div style={{ fontSize: '20px', fontWeight: 800, color: severity === 'severe_crisis' ? '#dc2626' : severity === 'moderate' ? '#ea580c' : '#16a34a', marginTop: '6px' }}>
                    {severity === 'severe_crisis' ? '🚨 SEVERE CRISIS (≥14.0)' : severity === 'moderate' ? '⚠️ MODERATE (12.0–13.9)' : severity === 'mild' ? '⚡ MILD (10.3–11.9)' : '✅ NORMAL'}
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                    {severity === 'severe_crisis' ? 'Cardiac telemetry, urgent saline bolus & dual antiresorptive indicated' : severity === 'moderate' ? 'IV Saline rehydration + bisphosphonate infusion recommended' : 'Oral hydration + review medication causes'}
                  </div>
                </div>
              </div>

              {/* Lab Sliders & Formula */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
                  gap: '20px',
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <label style={{ fontSize: '14px', fontWeight: 700, color: '#1e293b' }}>
                      Measured Serum Total Calcium:
                    </label>
                    <div style={{ display: 'flex', gap: '4px', background: '#e2e8f0', borderRadius: '6px', padding: '2px' }}>
                      <button
                        type="button"
                        onClick={() => handleUnitToggle('mg_dl')}
                        style={{
                          background: calciumUnit === 'mg_dl' ? '#ffffff' : 'transparent',
                          border: 'none',
                          borderRadius: '4px',
                          padding: '2px 8px',
                          fontSize: '11px',
                          fontWeight: 700,
                          cursor: 'pointer',
                        }}
                      >
                        mg/dL
                      </button>
                      <button
                        type="button"
                        onClick={() => handleUnitToggle('mmol_l')}
                        style={{
                          background: calciumUnit === 'mmol_l' ? '#ffffff' : 'transparent',
                          border: 'none',
                          borderRadius: '4px',
                          padding: '2px 8px',
                          fontSize: '11px',
                          fontWeight: 700,
                          cursor: 'pointer',
                        }}
                      >
                        mmol/L
                      </button>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <input
                      type="range"
                      min={calciumUnit === 'mg_dl' ? '7.0' : '1.75'}
                      max={calciumUnit === 'mg_dl' ? '20.0' : '5.00'}
                      step="0.1"
                      value={totalCalciumInput}
                      onChange={(e) => setTotalCalciumInput(parseFloat(e.target.value))}
                      style={{ flex: 1, accentColor: '#dc2626' }}
                    />
                    <input
                      type="number"
                      step="0.1"
                      value={totalCalciumInput}
                      onChange={(e) => setTotalCalciumInput(parseFloat(e.target.value) || 0)}
                      style={{
                        width: '75px',
                        padding: '6px 8px',
                        border: '1px solid #cbd5e1',
                        borderRadius: '6px',
                        fontSize: '14px',
                        fontWeight: 700,
                        textAlign: 'right',
                      }}
                    />
                    <span style={{ fontSize: '13px', color: '#64748b' }}>{calciumUnit === 'mg_dl' ? 'mg/dL' : 'mmol/L'}</span>
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '6px' }}>
                    Raw lab total calcium unadjusted for hypoalbuminemia.
                  </div>
                </div>

                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <label style={{ fontSize: '14px', fontWeight: 700, color: '#1e293b' }}>
                      Serum Albumin:
                    </label>
                    <span style={{ fontSize: '14px', fontWeight: 800, color: '#0284c7' }}>
                      {serumAlbuminInput} g/dL
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <input
                      type="range"
                      min="1.0"
                      max="5.0"
                      step="0.1"
                      value={serumAlbuminInput}
                      onChange={(e) => setSerumAlbuminInput(parseFloat(e.target.value))}
                      style={{ flex: 1, accentColor: '#0284c7' }}
                    />
                    <input
                      type="number"
                      step="0.1"
                      value={serumAlbuminInput}
                      onChange={(e) => setSerumAlbuminInput(parseFloat(e.target.value) || 0)}
                      style={{
                        width: '75px',
                        padding: '6px 8px',
                        border: '1px solid #cbd5e1',
                        borderRadius: '6px',
                        fontSize: '14px',
                        fontWeight: 700,
                        textAlign: 'right',
                      }}
                    />
                    <span style={{ fontSize: '13px', color: '#64748b' }}>g/dL</span>
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '6px' }}>
                    Hypoalbuminemia falsely lowers total calcium! Correction reveals true ionized excess.
                  </div>
                </div>
              </div>

              {/* Mathematical Equation Callout */}
              <div
                style={{
                  background: '#f1f5f9',
                  borderRadius: '10px',
                  padding: '14px 18px',
                  fontSize: '13px',
                  color: '#334155',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ fontWeight: 700, marginBottom: '4px' }}>📐 Standardized Correction Formula:</div>
                <code>Corrected Calcium (mg/dL) = Measured Total Ca + 0.8 × (4.0 - Albumin g/dL)</code>
                <div style={{ marginTop: '6px', color: '#64748b', fontSize: '12px' }}>
                  Calculation: {calciumUnit === 'mg_dl' ? totalCalciumInput : Math.round(totalCalciumInput * 4.008 * 10) / 10} + 0.8 × (4.0 - {serumAlbuminInput}) = <strong>{correctedCalciumMgDl} mg/dL</strong>
                  {' '}(Delta from normal upper limit: +{Math.max(0, Math.round((correctedCalciumMgDl - 10.2) * 10) / 10)} mg/dL)
                </div>
              </div>

              {/* Etiology / Mechanism Selector */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '18px',
                }}
              >
                <h3 style={{ margin: '0 0 12px 0', fontSize: '15px', fontWeight: 700, color: '#1e293b' }}>
                  Pathophysiologic Mechanism of Hypercalcemia:
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '10px' }}>
                  <button
                    type="button"
                    onClick={() => setHcmMechanism('hhm_pthrp')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: hcmMechanism === 'hhm_pthrp' ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: hcmMechanism === 'hhm_pthrp' ? '#eff6ff' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e293b' }}>Humoral HHM (PTHrP)</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      80% of cases: Squamous lung, head/neck, esophagus, RCC, bladder.
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setHcmMechanism('osteolytic_bone')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: hcmMechanism === 'osteolytic_bone' ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: hcmMechanism === 'osteolytic_bone' ? '#eff6ff' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e293b' }}>Local Osteolytic Bone Mets</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      20% of cases: Breast cancer, Multiple Myeloma, extensive bone lysis.
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setHcmMechanism('calcitriol_lymphoma')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: hcmMechanism === 'calcitriol_lymphoma' ? '2px solid #2563eb' : '1px solid #cbd5e1',
                      background: hcmMechanism === 'calcitriol_lymphoma' ? '#eff6ff' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e293b' }}>Calcitriol [1,25(OH)₂D] Overproduction</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      ~1% of cases: Hodgkin &amp; Non-Hodgkin Lymphoma. Responds to Glucocorticoids.
                    </div>
                  </button>
                </div>
              </div>

              {/* Acute Symptoms Presentation */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '18px',
                }}
              >
                <h3 style={{ margin: '0 0 10px 0', fontSize: '15px', fontWeight: 700, color: '#1e293b' }}>
                  Clinical Manifestations &amp; End-Organ Symptoms (&quot;Stones, Bones, Groans, Psychiatric Moans&quot;):
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px' }}>
                  {[
                    { key: 'altered_mental_status', label: '🧠 Confusion / Delirium / Stupor' },
                    { key: 'severe_dehydration', label: '💧 Severe Dehydration / Hypovolemia' },
                    { key: 'nausea_vomiting', label: '🤢 Intractable Nausea & Vomiting' },
                    { key: 'constipation', label: '🛑 Severe Constipation / Ileus' },
                    { key: 'polyuria_polydipsia', label: '🚽 Nephrogenic Polyuria & Thirst' },
                    { key: 'muscle_weakness', label: '⚡ Profound Muscle Weakness' },
                    { key: 'cardiac_palpitations', label: '❤️ Palpitations / Arrhythmia Risk' },
                  ].map((item) => (
                    <label
                      key={item.key}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        padding: '8px 10px',
                        borderRadius: '6px',
                        background: symptoms[item.key] ? '#fef2f2' : '#f8fafc',
                        border: `1px solid ${symptoms[item.key] ? '#fca5a5' : '#e2e8f0'}`,
                        fontSize: '12px',
                        fontWeight: symptoms[item.key] ? 600 : 400,
                        color: symptoms[item.key] ? '#991b1b' : '#334155',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={symptoms[item.key]}
                        onChange={(e) =>
                          setSymptoms({ ...symptoms, [item.key]: e.target.checked })
                        }
                      />
                      {item.label}
                    </label>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: SALINE RESUSCITATION & ELECTROLYTES */}
          {activeTab === 'saline_resuscitation' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Critical Diuretic Warning Banner */}
              <div
                style={{
                  background: '#fef2f2',
                  border: '2px solid #ef4444',
                  borderRadius: '12px',
                  padding: '16px 20px',
                  display: 'flex',
                  gap: '14px',
                  alignItems: 'flex-start',
                }}
              >
                <div style={{ fontSize: '28px', lineHeight: 1 }}>🚫</div>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: '15px', fontWeight: 800, color: '#991b1b' }}>
                    CRITICAL SAFETY WARNING: DO NOT ADMINISTER FUROSEMIDE (LOOP DIURETICS) PRIOR TO FULL VOLUME REPLETION!
                  </h4>
                  <p style={{ margin: 0, fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                    Hypercalcemia produces severe nephrogenic diabetes insipidus by blocking the Na-K-2Cl cotransporter and downregulating aquaporin channels, inducing <strong>3 to 6 Liters of volume deficit</strong>. Giving loop diuretics to a dehydrated patient precipitates hemodynamic collapse, acute tubular necrosis, and paradoxically elevates serum calcium by hemoconcentration. <em>Furosemide is strictly reserved for patients with verified iatrogenic volume overload or congestive heart failure.</em>
                  </p>
                </div>
              </div>

              {/* Volume Repletion Protocol Card */}
              <div
                style={{
                  background: '#f0f9ff',
                  border: '1px solid #bae6fd',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <h3 style={{ margin: '0 0 12px 0', fontSize: '16px', fontWeight: 800, color: '#0369a1' }}>
                  💧 Step 1: Rapid Intravenous Saline Expansion Protocol
                </h3>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #e0f2fe' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase' }}>
                      Estimated Fluid Deficit
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#0369a1', margin: '4px 0' }}>
                      ~{fluidDeficitLiters} Liters
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b' }}>
                      Based on corrected Ca of {correctedCalciumMgDl} mg/dL &amp; clinical dehydration.
                    </div>
                  </div>

                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #e0f2fe' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase' }}>
                      Initial Infusion Rate (Hours 1–4)
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#0369a1', margin: '4px 0' }}>
                      200 – 300 mL/hr
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b' }}>
                      0.9% Normal Saline IV (total 1000 mL over first 2–4 hours).
                    </div>
                  </div>

                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #e0f2fe' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase' }}>
                      Maintenance Rate &amp; Target Urine
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#0369a1', margin: '4px 0' }}>
                      150 – 200 mL/hr
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b' }}>
                      Titrate to maintain urine output of 100 – 150 mL/hr.
                    </div>
                  </div>
                </div>

                <div style={{ marginTop: '16px', fontSize: '13px', color: '#0369a1', lineHeight: 1.5 }}>
                  <strong>Mechanism:</strong> Volume expansion restores renal perfusion, increases glomerular filtration of calcium, and inhibits sodium and calcium reabsorption in the proximal tubule, lowering calcium by 1.5 to 2.5 mg/dL within 24 hours.
                </div>
              </div>

              {/* Electrolyte Wastage Prevention (K+ and Mg2+) */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '18px',
                }}
              >
                <h3 style={{ margin: '0 0 10px 0', fontSize: '15px', fontWeight: 700, color: '#1e293b' }}>
                  ⚡ Electrolyte Depletion Guard (Potassium &amp; Magnesium Supplementation):
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '14px' }}>
                  <div style={{ background: '#f8fafc', padding: '12px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#0f172a' }}>Potassium (KCl) Monitoring</div>
                    <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                      Vigorous saline diuresis washes out urinary potassium. Add <strong>20 mEq KCl per liter of NS</strong> once urine output is verified, maintaining K &gt; 4.0 mEq/L to protect against lethal cardiac dysrhythmias.
                    </div>
                  </div>

                  <div style={{ background: '#f8fafc', padding: '12px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#0f172a' }}>Magnesium Sulfate Replacement</div>
                    <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                      Hypercalcemia and high sodium flow impair loop magnesium reabsorption. Target Serum Mg &gt; 2.0 mg/dL. Administer <strong>Magnesium Sulfate 2 g (8.1 mEq) IV in 100 mL NS over 1 hour</strong> if subnormal.
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: ANTIRESORPTIVE DOSING (ZOLEDRONATE VS DENOSUMAB) */}
          {activeTab === 'antiresorptive_dosing' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Cockcroft-Gault Renal Estimator */}
              <div
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
                  <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 800, color: '#1e293b' }}>
                    Cockcroft-Gault Creatinine Clearance (CrCl) Assessment:
                  </h3>
                  <div
                    style={{
                      padding: '4px 12px',
                      borderRadius: '8px',
                      fontWeight: 800,
                      fontSize: '14px',
                      background: creatinineClearance >= 60 ? '#f0fdf4' : creatinineClearance >= 30 ? '#fffbeb' : '#fef2f2',
                      color: creatinineClearance >= 60 ? '#166534' : creatinineClearance >= 30 ? '#92400e' : '#991b1b',
                      border: `1px solid ${creatinineClearance >= 60 ? '#86efac' : creatinineClearance >= 30 ? '#fde68a' : '#fca5a5'}`,
                    }}
                  >
                    Est. CrCl: {creatinineClearance} mL/min
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '14px' }}>
                  <div>
                    <label style={{ fontSize: '12px', fontWeight: 700, color: '#475569' }}>Patient Age:</label>
                    <input
                      type="number"
                      value={patientAge}
                      onChange={(e) => setPatientAge(parseInt(e.target.value) || 60)}
                      style={{ width: '100%', padding: '6px 8px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '13px', marginTop: '4px' }}
                    />
                  </div>

                  <div>
                    <label style={{ fontSize: '12px', fontWeight: 700, color: '#475569' }}>Weight (kg):</label>
                    <input
                      type="number"
                      value={patientWeightKg}
                      onChange={(e) => setPatientWeightKg(parseFloat(e.target.value) || 50)}
                      style={{ width: '100%', padding: '6px 8px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '13px', marginTop: '4px' }}
                    />
                  </div>

                  <div>
                    <label style={{ fontSize: '12px', fontWeight: 700, color: '#475569' }}>Gender:</label>
                    <select
                      value={patientGender}
                      onChange={(e) => setPatientGender(e.target.value as 'female' | 'male')}
                      style={{ width: '100%', padding: '6px 8px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '13px', marginTop: '4px' }}
                    >
                      <option value="female">Female (×0.85 factor)</option>
                      <option value="male">Male (Standard)</option>
                    </select>
                  </div>

                  <div>
                    <label style={{ fontSize: '12px', fontWeight: 700, color: '#475569' }}>Serum Creatinine (mg/dL):</label>
                    <input
                      type="number"
                      step="0.1"
                      value={serumCreatinine}
                      onChange={(e) => setSerumCreatinine(parseFloat(e.target.value) || 1.0)}
                      style={{ width: '100%', padding: '6px 8px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '13px', marginTop: '4px' }}
                    />
                  </div>
                </div>
              </div>

              {/* Head-to-Head Comparison: Zoledronate vs Denosumab */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
                
                {/* Zoledronic Acid Card */}
                <div
                  style={{
                    background: recommendedAgent === 'zoledronic_acid' ? '#eff6ff' : '#f8fafc',
                    border: `2px solid ${recommendedAgent === 'zoledronic_acid' ? '#2563eb' : '#e2e8f0'}`,
                    borderRadius: '12px',
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', color: '#1d4ed8', background: '#dbeafe', padding: '2px 8px', borderRadius: '4px' }}>
                        Bisphosphonate (First-Line in Euvolemic Normal Renal)
                      </span>
                      {recommendedAgent === 'zoledronic_acid' && (
                        <span style={{ fontSize: '11px', fontWeight: 800, color: '#16a34a' }}>★ RECOMMENDED</span>
                      )}
                    </div>
                    <h4 style={{ margin: '10px 0 4px 0', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                      Zoledronic Acid (Zometa)
                    </h4>
                    <p style={{ margin: '0 0 14px 0', fontSize: '13px', color: '#475569' }}>
                      Potent 3rd generation aminobisphosphonate. Inhibits farnesyl pyrophosphate synthase in osteoclasts.
                    </p>

                    <div style={{ background: '#ffffff', borderRadius: '8px', padding: '12px', border: '1px solid #cbd5e1', marginBottom: '12px' }}>
                      <div style={{ fontSize: '12px', color: '#64748b' }}>Calculated Renal Dosing:</div>
                      <div style={{ fontSize: '20px', fontWeight: 800, color: zoledronicRecommendation.safe ? '#0f172a' : '#dc2626', margin: '4px 0' }}>
                        {zoledronicRecommendation.dose}
                      </div>
                      <div style={{ fontSize: '12px', color: '#334155' }}>
                        {zoledronicRecommendation.infusionTime}
                      </div>
                      <div style={{ fontSize: '11px', color: '#dc2626', fontWeight: 600, marginTop: '4px' }}>
                        {zoledronicRecommendation.safety}
                      </div>
                    </div>

                    <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#475569', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <li><strong>Nadir Response:</strong> 2 to 4 days post-infusion (does NOT work immediately).</li>
                      <li><strong>Duration of Control:</strong> Median 32 – 43 days.</li>
                      <li><strong>Infusion Warning:</strong> Must infuse over AT LEAST 15 minutes; faster boluses cause acute renal collapse.</li>
                    </ul>
                  </div>
                </div>

                {/* Denosumab Card */}
                <div
                  style={{
                    background: recommendedAgent === 'denosumab' ? '#f0fdf4' : '#f8fafc',
                    border: `2px solid ${recommendedAgent === 'denosumab' ? '#16a34a' : '#e2e8f0'}`,
                    borderRadius: '12px',
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', color: '#15803d', background: '#dcfce7', padding: '2px 8px', borderRadius: '4px' }}>
                        RANK-Ligand Monoclonal Antibody
                      </span>
                      {recommendedAgent === 'denosumab' && (
                        <span style={{ fontSize: '11px', fontWeight: 800, color: '#16a34a' }}>★ RECOMMENDED</span>
                      )}
                    </div>
                    <h4 style={{ margin: '10px 0 4px 0', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                      Denosumab (Xgeva)
                    </h4>
                    <p style={{ margin: '0 0 14px 0', fontSize: '13px', color: '#475569' }}>
                      Fully human IgG2 monoclonal antibody binding RANKL, blocking osteoclast formation &amp; survival.
                    </p>

                    <div style={{ background: '#ffffff', borderRadius: '8px', padding: '12px', border: '1px solid #cbd5e1', marginBottom: '12px' }}>
                      <div style={{ fontSize: '12px', color: '#64748b' }}>Standard Hypercalcemia Regimen:</div>
                      <div style={{ fontSize: '20px', fontWeight: 800, color: '#0f172a', margin: '4px 0' }}>
                        120 mg Subcutaneously
                      </div>
                      <div style={{ fontSize: '12px', color: '#334155' }}>
                        Loading Schedule: <strong>Days 1, 8, and 15</strong>, then every 4 weeks.
                      </div>
                      <div style={{ fontSize: '11px', color: '#16a34a', fontWeight: 600, marginTop: '4px' }}>
                        ✓ Completely independent of renal clearance (Safe in CrCl &lt; 30 mL/min &amp; Dialysis)
                      </div>
                    </div>

                    <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#475569', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <li><strong>Primary Indication:</strong> Renal failure (CrCl &lt; 30) or Bisphosphonate-Refractory HCM.</li>
                      <li><strong>Response Rate:</strong> 64% complete response in refractory cases within 10 days.</li>
                      <li><strong>Safety Note:</strong> Daily calcium monitoring required; high risk of late severe hypocalcemia.</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: CALCITONIN RAPID BRIDGING & GLUCOCORTICOIDS */}
          {activeTab === 'calcitonin_steroids' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Calcitonin Rapid Bridging Engine */}
              <div
                style={{
                  background: '#fefce8',
                  border: '1px solid #fef08a',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '12px' }}>
                  <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 800, color: '#854d0e' }}>
                    ⏱️ Calcitonin (Salmon) - Rapid Acute Bridging Protocol (First 24–48 Hours)
                  </h3>
                  <span style={{ fontSize: '11px', fontWeight: 800, background: '#fef08a', color: '#713f12', padding: '3px 8px', borderRadius: '4px' }}>
                    Rapid Onset: 2 to 4 Hours
                  </span>
                </div>

                <p style={{ margin: '0 0 16px 0', fontSize: '13px', color: '#713f12', lineHeight: 1.5 }}>
                  Because Zoledronic acid and Denosumab require 48 to 72 hours to manifest their antiresorptive effect, <strong>Calcitonin salmon</strong> is the drug of choice for rapid calcium reduction in severe crisis or symptomatic patients.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '14px' }}>
                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #fde047' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#854d0e', textTransform: 'uppercase' }}>
                      Weight-Based Dose (4 – 8 IU/kg)
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#854d0e', margin: '4px 0' }}>
                      {calcitoninDose.minDose} – {calcitoninDose.maxDose} IU
                    </div>
                    <div style={{ fontSize: '12px', color: '#713f12' }}>
                      Subcutaneously or IM every 12 hours (Patient weight: {patientWeightKg} kg).
                    </div>
                  </div>

                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #fde047' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#854d0e', textTransform: 'uppercase' }}>
                      Expected Calcium Drop
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#854d0e', margin: '4px 0' }}>
                      ↓ 1.0 – 2.0 mg/dL
                    </div>
                    <div style={{ fontSize: '12px', color: '#713f12' }}>
                      Onset within 2–4 hours; peaks at 12–24 hours.
                    </div>
                  </div>

                  <div style={{ background: '#ffffff', borderRadius: '8px', padding: '14px', border: '1px solid #fde047' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#dc2626', textTransform: 'uppercase' }}>
                      Tachyphylaxis Ceiling
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: '#dc2626', margin: '4px 0' }}>
                      Stop at 48 Hours
                    </div>
                    <div style={{ fontSize: '12px', color: '#713f12' }}>
                      Receptors downregulate rapidly; continuing beyond 48h provides no clinical benefit.
                    </div>
                  </div>
                </div>
              </div>

              {/* Glucocorticoids for Lymphoma / Granulomatous */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 800, color: '#1e293b' }}>
                    💊 Glucocorticoids for Calcitriol-Mediated Hypercalcemia
                  </h3>
                  <span style={{ fontSize: '11px', fontWeight: 700, background: hcmMechanism === 'calcitriol_lymphoma' ? '#dcfce7' : '#f1f5f9', color: hcmMechanism === 'calcitriol_lymphoma' ? '#166534' : '#64748b', padding: '3px 8px', borderRadius: '4px' }}>
                    {hcmMechanism === 'calcitriol_lymphoma' ? 'Directly Indicated' : 'Second-Line / Specific Etiologies'}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
                  <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <div style={{ fontWeight: 700, fontSize: '14px', color: '#0f172a' }}>Oral Prednisone Protocol</div>
                    <div style={{ fontSize: '18px', fontWeight: 800, color: '#0284c7', margin: '4px 0' }}>
                      40 – 60 mg PO Daily
                    </div>
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      Inhibits 1α-hydroxylase enzyme converting 25-OH-D to active calcitriol. Useful in Hodgkin/Non-Hodgkin Lymphoma and Multiple Myeloma. Response seen in 2 to 5 days.
                    </div>
                  </div>

                  <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <div style={{ fontWeight: 700, fontSize: '14px', color: '#0f172a' }}>Intravenous Hydrocortisone (NPO / Crisis)</div>
                    <div style={{ fontSize: '18px', fontWeight: 800, color: '#0284c7', margin: '4px 0' }}>
                      100 mg IV every 8 hours
                    </div>
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      Used when patient is obtunded, nauseated, or unable to take oral medications. Provide PPI gastroprotection (Pantoprazole 40 mg daily).
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: ECG CARDIAC TELEMETRY & QTC VISUALIZER */}
          {activeTab === 'ecg_cardiac_telemetry' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Telemetry Header */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: '#0f172a',
                  color: '#ffffff',
                  padding: '16px 20px',
                  borderRadius: '12px',
                  flexWrap: 'wrap',
                  gap: '12px',
                }}
              >
                <div>
                  <div style={{ fontSize: '12px', color: '#38bdf8', fontWeight: 700, textTransform: 'uppercase' }}>
                    Lead II Real-Time Electrophysiology Simulator
                  </div>
                  <h3 style={{ margin: '4px 0 0 0', fontSize: '18px', fontWeight: 800 }}>
                    Cardiac ST-Segment &amp; QTc Shortening Under Hypercalcemia
                  </h3>
                </div>

                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '11px', color: '#94a3b8' }}>Calculated QTc Interval:</div>
                    <div style={{ fontSize: '24px', fontWeight: 800, color: estimatedQTcMs < 350 ? '#f87171' : estimatedQTcMs < 390 ? '#fbbf24' : '#4ade80' }}>
                      {estimatedQTcMs} ms
                    </div>
                  </div>
                  <div
                    style={{
                      padding: '6px 12px',
                      borderRadius: '8px',
                      fontSize: '12px',
                      fontWeight: 800,
                      background: estimatedQTcMs < 350 ? '#7f1d1d' : estimatedQTcMs < 390 ? '#78350f' : '#14532d',
                      color: estimatedQTcMs < 350 ? '#fecaca' : estimatedQTcMs < 390 ? '#fef3c7' : '#bbf7d0',
                    }}
                  >
                    {estimatedQTcMs < 350 ? '⚠️ High Arrhythmia Risk' : estimatedQTcMs < 390 ? '⚡ Shortened QTc' : '✓ Normal Interval'}
                  </div>
                </div>
              </div>

              {/* Dynamic SVG ECG Strip */}
              <div
                style={{
                  background: '#1e293b',
                  borderRadius: '12px',
                  padding: '16px',
                  border: '1px solid #334155',
                  position: 'relative',
                  overflow: 'hidden',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', color: '#94a3b8', fontSize: '11px' }}>
                  <span>25 mm/sec · 10 mm/mV · Standard Calibration</span>
                  <span>Paper Grid: 0.04s small box / 0.20s large box</span>
                </div>

                {/* SVG Canvas */}
                <svg
                  viewBox="0 0 800 240"
                  style={{ width: '100%', height: 'auto', display: 'block', background: '#0b1329', borderRadius: '8px' }}
                >
                  <defs>
                    {/* ECG Grid Pattern */}
                    <pattern id="smallGrid" width="8" height="8" patternUnits="userSpaceOnUse">
                      <path d="M 8 0 L 0 0 0 8" fill="none" stroke="rgba(239, 68, 68, 0.12)" strokeWidth="0.5" />
                    </pattern>
                    <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                      <rect width="40" height="40" fill="url(#smallGrid)" />
                      <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(239, 68, 68, 0.3)" strokeWidth="1" />
                    </pattern>
                    <linearGradient id="traceGlow" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stopColor="#22c55e" />
                      <stop offset="100%" stopColor="#4ade80" />
                    </linearGradient>
                  </defs>

                  {/* Grid Background */}
                  <rect width="100%" height="100%" fill="url(#grid)" />

                  {/* Baseline center line */}
                  <line x1="0" y1="130" x2="800" y2="130" stroke="rgba(255, 255, 255, 0.1)" strokeWidth="1" strokeDasharray="4 4" />

                  {/* Real-time Dynamic ECG Beats (2 Full Cardiac Cycles) */}
                  {/* We compute dynamic ST-segment length based on corrected calcium */}
                  {(() => {
                    // ST segment length scales inversely with corrected calcium
                    // Normal ST length: 70px. Severe crisis: 15px (T wave immediately abuts S wave)
                    const stLength = Math.max(10, Math.round(70 - (correctedCalciumMgDl - 9.5) * 6.5));
                    // J-wave deflection if Ca > 14
                    const jWaveHeight = correctedCalciumMgDl >= 14 ? Math.min(22, (correctedCalciumMgDl - 14) * 5) : 0;

                    // Beat 1 start at x=40
                    // P wave: 70 to 110, QRS: 140 to 170, ST: 170 to (170 + stLength), T wave: (170 + stLength) to (230 + stLength)
                    const beat1_qrs_end = 170;
                    const beat1_t_start = beat1_qrs_end + stLength;
                    const beat1_t_end = beat1_t_start + 65;

                    // Beat 2 start offset
                    const beat2_offset = 380;
                    const beat2_qrs_end = beat2_offset + 130;
                    const beat2_t_start = beat2_qrs_end + stLength;
                    const beat2_t_end = beat2_t_start + 65;

                    return (
                      <g>
                        {/* Waveform Trace */}
                        <path
                          d={`
                            M 0 130
                            L 60 130
                            C 70 130, 75 110, 85 110
                            C 95 110, 100 130, 110 130
                            L 140 130
                            L 145 142
                            L 155 30
                            L 165 165
                            L 170 ${130 - jWaveHeight}
                            L ${beat1_t_start} 130
                            C ${beat1_t_start + 18} 130, ${beat1_t_start + 30} 80, ${beat1_t_start + 36} 80
                            C ${beat1_t_start + 45} 80, ${beat1_t_start + 55} 130, ${beat1_t_end} 130
                            L ${beat2_offset + 20} 130
                            C ${beat2_offset + 30} 130, ${beat2_offset + 35} 110, ${beat2_offset + 45} 110
                            C ${beat2_offset + 55} 110, ${beat2_offset + 60} 130, ${beat2_offset + 70} 130
                            L ${beat2_offset + 100} 130
                            L ${beat2_offset + 105} 142
                            L ${beat2_offset + 115} 30
                            L ${beat2_offset + 125} 165
                            L ${beat2_offset + 130} ${130 - jWaveHeight}
                            L ${beat2_t_start} 130
                            C ${beat2_t_start + 18} 130, ${beat2_t_start + 30} 80, ${beat2_t_start + 36} 80
                            C ${beat2_t_start + 45} 80, ${beat2_t_start + 55} 130, ${beat2_t_end} 130
                            L 800 130
                          `}
                          fill="none"
                          stroke={correctedCalciumMgDl >= 14 ? '#f87171' : '#4ade80'}
                          strokeWidth="2.5"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />

                        {/* Annotations */}
                        <g fill="#94a3b8" fontSize="11" fontFamily="monospace">
                          <text x="80" y="100">P</text>
                          <text x="150" y="22">R</text>
                          <text x="175" y="150">S</text>
                          {correctedCalciumMgDl >= 14 && (
                            <text x="180" y="115" fill="#f87171" fontWeight="bold">J (Osborn)</text>
                          )}
                          <text x={beat1_t_start + 28} y="70">T</text>
                        </g>

                        {/* ST Segment Marker Bracket */}
                        <line
                          x1="170"
                          y1="185"
                          x2={beat1_t_start}
                          y2="185"
                          stroke="#38bdf8"
                          strokeWidth="2"
                        />
                        <text
                          x={170 + stLength / 2}
                          y="202"
                          fill="#38bdf8"
                          fontSize="10"
                          fontWeight="bold"
                          textAnchor="middle"
                        >
                          ST: {Math.round(stLength * 2.2)} ms {stLength < 25 ? '(Virtually Absent)' : ''}
                        </text>

                        {/* QTc Interval Bracket */}
                        <line
                          x1="145"
                          y1="220"
                          x2={beat1_t_end}
                          y2="220"
                          stroke={estimatedQTcMs < 350 ? '#f87171' : '#4ade80'}
                          strokeWidth="2"
                        />
                        <text
                          x={(145 + beat1_t_end) / 2}
                          y="235"
                          fill={estimatedQTcMs < 350 ? '#f87171' : '#4ade80'}
                          fontSize="11"
                          fontWeight="bold"
                          textAnchor="middle"
                        >
                          QTc: {estimatedQTcMs} ms
                        </text>
                      </g>
                    );
                  })()}
                </svg>
              </div>

              {/* Pathophysiology Explainer Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px' }}>
                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '14px', fontWeight: 700, color: '#1e293b' }}>
                    1. ST-Segment Obliteration (Short Phase 2 Plateau)
                  </h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>
                    Extracellular hypercalcemia enhances L-type calcium channel current (I_Ca-L), accelerating Phase 2 repolarization of ventricular myocytes. The ST segment contracts or virtually disappears, causing the T-wave to emerge directly from the S-wave.
                  </p>
                </div>

                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '14px', fontWeight: 700, color: '#1e293b' }}>
                    2. Osborn (J) Waves &amp; Arrhythmogenic Threshold
                  </h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>
                    When corrected calcium exceeds 14 mg/dL, an elevated J-point notch (Osborn wave) develops at the QRS-ST junction. Severe hypercalcemia increases automaticity, predisposing to complete AV heart block, ventricular tachycardia, and sudden cardiac arrest.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 6: BILINGUAL FAMILY COUNSELING & DISCHARGE PLANNING */}
          {activeTab === 'family_counseling' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              {/* Language Selector */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f1f5f9', padding: '10px 16px', borderRadius: '8px' }}>
                <span style={{ fontSize: '13px', fontWeight: 700, color: '#334155' }}>
                  Select Communication Language / भाषा चुनें:
                </span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    onClick={() => setCounselingLang('en')}
                    style={{
                      padding: '4px 12px',
                      borderRadius: '6px',
                      fontSize: '12px',
                      fontWeight: 700,
                      background: counselingLang === 'en' ? '#0284c7' : '#ffffff',
                      color: counselingLang === 'en' ? '#ffffff' : '#334155',
                      border: '1px solid #cbd5e1',
                      cursor: 'pointer',
                    }}
                  >
                    English
                  </button>
                  <button
                    type="button"
                    onClick={() => setCounselingLang('hi')}
                    style={{
                      padding: '4px 12px',
                      borderRadius: '6px',
                      fontSize: '12px',
                      fontWeight: 700,
                      background: counselingLang === 'hi' ? '#0284c7' : '#ffffff',
                      color: counselingLang === 'hi' ? '#ffffff' : '#334155',
                      border: '1px solid #cbd5e1',
                      cursor: 'pointer',
                    }}
                  >
                    हिन्दी (Hindi)
                  </button>
                </div>
              </div>

              {/* Authentic Clinician Guidance Scripts */}
              {counselingLang === 'en' ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      1. Explaining Hypercalcemia to Family Members:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;The cancer cells have released signals that cause calcium from the bones to leach out into the bloodstream. High calcium is like a tranquilizer or toxin to the brain, kidneys, and heart—it is the reason your loved one has become so sleepy, confused, and dehydrated. We are giving large volumes of special intravenous fluids to flush out this excess calcium, along with targeted medications (Zoledronic acid or Denosumab) to seal the bone and stop calcium from leaking. As their calcium levels return to normal over the next 2 to 4 days, their mental alertness will improve.&quot;
                    </p>
                  </div>

                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      2. Explaining Why Hydration Precedes Cancer Therapy:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;You might wonder why we are not immediately giving chemotherapy right now. High calcium causes severe dehydration and strains the kidneys. If we gave chemotherapy today, the kidneys could fail completely. Our first priority is protecting the kidneys and heart with IV fluids and calcium-lowering therapy. Once the calcium is safe, we can resume oncologic treatment.&quot;
                    </p>
                  </div>

                  <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#991b1b' }}>
                      3. Red Flag Warning Signs for Immediate Emergency Re-admission:
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                      <li>Inability to wake the patient or severe new disorientation/hallucinations.</li>
                      <li>Inability to keep liquids down due to constant nausea or vomiting.</li>
                      <li>Sudden racing heartbeat, irregular pulse, or fainting spells.</li>
                      <li>Complete cessation of urination for more than 8 hours.</li>
                    </ul>
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      १. परिवार को बीमारी और सुस्ती समझाने का तरीका:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;कैंसर की वजह से हड्डियों से कैल्शियम निकलकर खून में बहुत अधिक मात्रा में आ गया है। जब कैल्शियम बहुत बढ़ जाता है, तो यह दिमाग, गुर्दों और दिल पर भारी असर डालता है—यही कारण है कि मरीज को इतनी अधिक सुस्ती, भ्रम (भूलने की आदत), प्यास और कमजोरी हो रही है। हम सलाइन की मदद से इस अतिरिक्त कैल्शियम को पेशाब के रास्ते बाहर निकाल रहे हैं और हड्डियों को सुरक्षित करने के लिए विशेष दवा दे रहे हैं। जैसे-जैसे कैल्शियम सामान्य होगा, अगले २ से ४ दिनों में मरीज की सुस्ती और चेतना में सुधार आएगा।&quot;
                    </p>
                  </div>

                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      २. कीमोथेरेपी से पहले सलाइन क्यों जरूरी है:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;शरीर में पानी की बहुत कमी हो चुकी है और गुर्दे खतरे में हैं। अगर हम अभी भारी कैंसर दवाइयां देंगे तो गुर्दे खराब हो सकते हैं। इसलिए पहले २-३ दिन सलाइन और कैल्शियम घटाने वाली दवा से शरीर को स्थिर करना सबसे जरूरी है।&quot;
                    </p>
                  </div>

                  <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#991b1b' }}>
                      ३. तुरंत अस्पताल लाने के खतरे के लक्षण (रेड फ्लैग):
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                      <li>मरीज का बहुत गहरा बेहोश हो जाना या जगाने पर भी आंखें न खोलना।</li>
                      <li>लगातार उल्टी होना और पानी भी पेट में न रुकना।</li>
                      <li>दिल की धड़कन बहुत तेज या अनियमित महसूस होना।</li>
                      <li>८ घंटे से अधिक समय तक पेशाब बिल्कुल न होना।</li>
                    </ul>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer Action Bar */}
        <div
          style={{
            padding: '16px 24px',
            borderTop: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
          }}
        >
          <div style={{ fontSize: '12px', color: '#64748b' }}>
            Corrected Ca: <strong style={{ color: severity === 'severe_crisis' ? '#dc2626' : '#0f172a' }}>{correctedCalciumMgDl} mg/dL</strong> · CrCl: <strong>{creatinineClearance} mL/min</strong> · Recommended: <strong>{recommendedAgent === 'denosumab' ? 'Denosumab 120mg SC' : `Zoledronic Acid ${zoledronicRecommendation.dose}`}</strong>
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 16px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                color: '#334155',
                fontSize: '13px',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleDispatch}
              style={{
                padding: '8px 20px',
                borderRadius: '8px',
                border: 'none',
                background: severity === 'severe_crisis' ? 'linear-gradient(135deg, #dc2626 0%, #b91c1c 100%)' : 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
                color: '#ffffff',
                fontSize: '13px',
                fontWeight: 700,
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                cursor: 'pointer',
                boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
              }}
            >
              <IconSparkles className="w-4 h-4" />
              <span>Dispatch Hypercalcemia Order &amp; Protocols</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
