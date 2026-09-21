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

export type YuSvcoGrade = 'grade_0' | 'grade_1' | 'grade_2' | 'grade_3' | 'grade_4';
export type TumorEtiology = 'sclc' | 'nsclc' | 'lymphoma' | 'germ_cell' | 'thymoma' | 'catheter_thrombus';

interface SvcoThoracicDecompressionModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function SvcoThoracicDecompressionModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: SvcoThoracicDecompressionModalProps) {
  const [activeTab, setActiveTab] = useState<
    'grading_triage' | 'emergency_orders' | 'vascular_anatomy' | 'decompression_strategy' | 'family_counseling'
  >('grading_triage');

  // Clinical Staging Parameters
  const [yuGrade, setYuGrade] = useState<YuSvcoGrade>('grade_3');
  const [tumorType, setTumorType] = useState<TumorEtiology>('nsclc');
  const [stenosisPercentage, setStenosisPercentage] = useState<number>(85);
  const [stentDeployed, setStentDeployed] = useState<boolean>(false);

  // Symptoms Checklist
  const [symptoms, setSymptoms] = useState<{ [key: string]: boolean }>({
    facial_edema: true,
    plethora_cyanosis: true,
    jugular_distension: true,
    dyspnea_orthopnea: true,
    dysphagia: true,
    cough_stridor: false,
    cerebral_headache_dizziness: true,
    visual_disturbances: false,
    upper_limb_edema: true,
  });

  // Vascular & Access Guard
  const [lowerExtremityIvConfirmed, setLowerExtremityIvConfirmed] = useState<boolean>(true);
  const [upperLimbAccessBanned, setUpperLimbAccessBanned] = useState<boolean>(true);
  const [headOfBedElevated, setHeadOfBedElevated] = useState<boolean>(true);

  // Language for counseling
  const [counselingLang, setCounselingLang] = useState<'en' | 'hi'>('en');

  // Kishi Score Calculation (0 to 10 points)
  // Evaluates clinical urgency
  const kishiScore = useMemo(() => {
    let score = 0;
    if (symptoms.facial_edema) score += 2;
    if (symptoms.dyspnea_orthopnea) score += 2;
    if (symptoms.cough_stridor) score += 3;
    if (symptoms.cerebral_headache_dizziness) score += 2;
    if (symptoms.visual_disturbances) score += 1;
    if (stenosisPercentage >= 80) score += 1;
    return Math.min(10, score);
  }, [symptoms, stenosisPercentage]);

  // Primary Clinical Recommendation
  const clinicalPlan = useMemo(() => {
    if (yuGrade === 'grade_4') {
      return {
        priority: 'EMERGENCY STAT · IMMEDIATE RESUSCITATION',
        color: '#dc2626',
        airway: 'Anesthesia / ENT standby for urgent fiberoptic airway management.',
        stenting: 'STAT Endovascular Stenting within 6 to 12 hours (First-Line Gold Standard).',
        steroids: 'Dexamethasone 16 mg IV STAT bolus immediately, then 8 mg IV q12h.',
        radiotherapy: 'Backup radiotherapy if endovascular stenting technically impossible.',
      };
    }
    if (yuGrade === 'grade_3') {
      return {
        priority: 'URGENT ONCOLOGIC DECOMPRESSION (<24 HOURS)',
        color: '#ea580c',
        airway: 'Continuous pulse oximetry, head-of-bed 45°-90°, avoid sedation.',
        stenting: tumorType === 'nsclc' || tumorType === 'catheter_thrombus' ? 'Endovascular Stenting within 24 hours provides >95% symptom relief.' : 'Chemotherapy or Stenting depending on histological speed.',
        steroids: 'Dexamethasone 16 mg IV STAT bolus, then 8 mg IV twice daily with Pantoprazole 40 mg.',
        radiotherapy: 'Consider emergency palliative RT if not chemo/stent candidate.',
      };
    }
    return {
      priority: 'SUBACUTE STAGED MANAGEMENT',
      color: '#0284c7',
      airway: 'Supplemental O2 as needed, head elevated.',
      stenting: 'Elective stenting if chemotherapy response is delayed.',
      steroids: 'Dexamethasone 8 mg IV/PO twice daily.',
      radiotherapy: 'Guided by histology.',
    };
  }, [yuGrade, tumorType]);

  const handleDispatch = () => {
    const summary = `SVCO (${yuGrade.toUpperCase()} - Kishi Score: ${kishiScore}/10). HOB elevated. Strict lower extremity IV access. Dexamethasone 16mg IV STAT. ${clinicalPlan.stenting}.`;
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
      aria-labelledby="svco-suite-title"
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
            background: yuGrade === 'grade_4' || yuGrade === 'grade_3' ? 'linear-gradient(135deg, #7f1d1d 0%, #991b1b 100%)' : 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
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
                  background: yuGrade === 'grade_4' || yuGrade === 'grade_3' ? '#fef2f2' : '#e0f2fe',
                  color: yuGrade === 'grade_4' || yuGrade === 'grade_3' ? '#b91c1c' : '#0369a1',
                }}
              >
                <IconShieldAlert className="w-3.5 h-3.5" />
                {yuGrade === 'grade_4' ? '🚨 Grade 4 Life-Threatening SVCO' : yuGrade === 'grade_3' ? '⚠️ Grade 3 Severe SVCO (Emergency)' : '⚡ Grade 1–2 Moderate SVCO'}
              </span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>
                Patient: <strong style={{ color: '#ffffff' }}>{patientName}</strong> ({patientId})
              </span>
              <span style={{ fontSize: '12px', color: '#94a3b8' }}>·</span>
              <span style={{ fontSize: '13px', color: '#cbd5e1' }}>{primaryCancer}</span>
            </div>
            <h1
              id="svco-suite-title"
              style={{
                margin: '8px 0 2px 0',
                fontSize: '22px',
                fontWeight: 800,
                letterSpacing: '-0.02em',
                color: '#ffffff',
              }}
            >
              Superior Vena Cava Obstruction (SVCO) &amp; Thoracic Decompression Suite
            </h1>
            <p style={{ margin: 0, fontSize: '13px', color: '#e2e8f0', opacity: 0.9 }}>
              Yu/Yale severity grading, STAT Dexamethasone, strict lower-extremity vascular access guards, and interactive SVG thoracic venous stent visualizer.
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
            onClick={() => setActiveTab('grading_triage')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'grading_triage' ? 700 : 500,
              color: activeTab === 'grading_triage' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'grading_triage' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            📋 1. Yu Grading &amp; Triage
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('emergency_orders')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'emergency_orders' ? 700 : 500,
              color: activeTab === 'emergency_orders' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'emergency_orders' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🚨 2. STAT Emergency Orders &amp; IV Guard
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('vascular_anatomy')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'vascular_anatomy' ? 700 : 500,
              color: activeTab === 'vascular_anatomy' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'vascular_anatomy' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🫀 3. SVC Anatomy &amp; Stent View
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('decompression_strategy')}
            style={{
              padding: '14px 16px',
              fontSize: '13px',
              fontWeight: activeTab === 'decompression_strategy' ? 700 : 500,
              color: activeTab === 'decompression_strategy' ? '#0f172a' : '#64748b',
              borderBottom: activeTab === 'decompression_strategy' ? '3px solid #dc2626' : '3px solid transparent',
              background: 'none',
              borderTop: 'none',
              borderLeft: 'none',
              borderRight: 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            🛡️ 4. Stenting vs RT vs Chemo
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
            🗣️ 5. Family Counseling (हिन्दी / EN)
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>

          {/* TAB 1: YU GRADING & CLINICAL PRESENTATION */}
          {activeTab === 'grading_triage' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

              {/* Status Banner */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                  gap: '16px',
                  background: yuGrade === 'grade_4' || yuGrade === 'grade_3' ? '#fef2f2' : '#eff6ff',
                  border: `2px solid ${yuGrade === 'grade_4' || yuGrade === 'grade_3' ? '#f87171' : '#93c5fd'}`,
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                    Yu / Yale SVCO Classification
                  </span>
                  <div style={{ fontSize: '24px', fontWeight: 800, color: yuGrade === 'grade_4' ? '#dc2626' : yuGrade === 'grade_3' ? '#ea580c' : '#0284c7', marginTop: '4px' }}>
                    {yuGrade === 'grade_4' ? 'GRADE 4 · LIFE-THREATENING' : yuGrade === 'grade_3' ? 'GRADE 3 · SEVERE SVCO' : yuGrade === 'grade_2' ? 'GRADE 2 · MODERATE' : 'GRADE 1 · MILD'}
                  </div>
                  <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                    {yuGrade === 'grade_4' ? 'Critical laryngeal stridor or severe cerebral edema (syncope/coma).' : yuGrade === 'grade_3' ? 'Significant cerebral (headache/dizziness) or laryngeal edema.' : 'Facial & upper body plethora with mild functional impact.'}
                  </div>
                </div>

                <div>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                    Kishi Urgency Score
                  </span>
                  <div style={{ fontSize: '32px', fontWeight: 800, color: kishiScore >= 7 ? '#dc2626' : kishiScore >= 4 ? '#ea580c' : '#16a34a', lineHeight: 1.1 }}>
                    {kishiScore} <span style={{ fontSize: '16px', fontWeight: 600 }}>/ 10</span>
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                    {kishiScore >= 7 ? 'Immediate interventional stenting indicated' : 'Semi-urgent decompression within 24–48h'}
                  </div>
                </div>

                <div>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                    Primary Oncology Etiology
                  </span>
                  <div style={{ fontSize: '18px', fontWeight: 800, color: '#0f172a', marginTop: '4px' }}>
                    {tumorType === 'sclc' ? 'Small Cell Lung (SCLC)' : tumorType === 'nsclc' ? 'Non-Small Cell Lung (NSCLC)' : tumorType === 'lymphoma' ? 'Mediastinal Lymphoma' : tumorType === 'catheter_thrombus' ? 'Catheter / Port Thrombus' : 'Thoracic Neoplasm'}
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                    {tumorType === 'sclc' ? 'Highly chemosensitive (80% response)' : tumorType === 'nsclc' ? 'Endovascular stent preferred over RT' : 'Requires antithrombotic therapy'}
                  </div>
                </div>
              </div>

              {/* Yu Grade Radio Buttons */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '18px',
                }}
              >
                <h3 style={{ margin: '0 0 12px 0', fontSize: '15px', fontWeight: 700, color: '#1e293b' }}>
                  Select Yu / Yale Clinical Severity Grade:
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '10px' }}>
                  <button
                    type="button"
                    onClick={() => setYuGrade('grade_1')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: yuGrade === 'grade_1' ? '2px solid #0284c7' : '1px solid #cbd5e1',
                      background: yuGrade === 'grade_1' ? '#f0f9ff' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#0f172a' }}>Grade 1: Mild</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      Head/neck edema, plethora, cyanosis, distended superficial veins. No functional compromise.
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setYuGrade('grade_2')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: yuGrade === 'grade_2' ? '2px solid #f59e0b' : '1px solid #cbd5e1',
                      background: yuGrade === 'grade_2' ? '#fffbeb' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#0f172a' }}>Grade 2: Moderate</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      Edema with functional impairment: dysphagia, cough, mild orthopnea, hoarseness.
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setYuGrade('grade_3')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: yuGrade === 'grade_3' ? '2px solid #ea580c' : '1px solid #cbd5e1',
                      background: yuGrade === 'grade_3' ? '#fff7ed' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#0f172a' }}>Grade 3: Severe (Emergency)</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      Mild/moderate cerebral edema (headache, dizziness, confusion) OR laryngeal edema.
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setYuGrade('grade_4')}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: yuGrade === 'grade_4' ? '2px solid #dc2626' : '1px solid #cbd5e1',
                      background: yuGrade === 'grade_4' ? '#fef2f2' : '#ffffff',
                      textAlign: 'left',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '13px', color: '#dc2626' }}>Grade 4: Life-Threatening</div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                      Severe cerebral edema (syncope, seizures, coma) OR laryngeal stridor, shock.
                    </div>
                  </button>
                </div>
              </div>

              {/* Symptoms Checklist */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '18px',
                }}
              >
                <h3 style={{ margin: '0 0 10px 0', fontSize: '15px', fontWeight: 700, color: '#1e293b' }}>
                  Clinical Symptom Checklist &amp; Physical Exam:
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px' }}>
                  {[
                    { key: 'facial_edema', label: '👤 Facial & Periorbital Edema' },
                    { key: 'plethora_cyanosis', label: '🔴 Facial Plethora / Cyanosis' },
                    { key: 'jugular_distension', label: '🩸 Distended Neck & Chest Collaterals' },
                    { key: 'dyspnea_orthopnea', label: '🫁 Dyspnea / Orthopnea on lying flat' },
                    { key: 'cough_stridor', label: '⚠️ Laryngeal Stridor / Hoarseness' },
                    { key: 'cerebral_headache_dizziness', label: '🧠 Cerebral Headache / Dizziness' },
                    { key: 'upper_limb_edema', label: '💪 Bilateral Upper Limb Swelling' },
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

          {/* TAB 2: EMERGENCY ORDERS & IV VASCULAR GUARD */}
          {activeTab === 'emergency_orders' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

              {/* STRICT UPPER LIMB VASCULAR BAN */}
              <div
                style={{
                  background: '#fef2f2',
                  border: '2px solid #ef4444',
                  borderRadius: '12px',
                  padding: '18px 20px',
                  display: 'flex',
                  gap: '16px',
                  alignItems: 'flex-start',
                }}
              >
                <div style={{ fontSize: '32px', lineHeight: 1 }}>🚫</div>
                <div>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '16px', fontWeight: 800, color: '#991b1b' }}>
                    MANDATORY SAFETY GUARD: STRICT PROHIBITION OF UPPER-EXTREMITY IV ACCESS &amp; BP CUFFS!
                  </h4>
                  <p style={{ margin: 0, fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                    Because the Superior Vena Cava is mechanically obstructed, central venous pressure in the upper limbs, neck, and brain is dramatically elevated (often exceeding 30–40 mmHg). <strong>Infusing fluids or inserting lines into the arms, internal jugular, or subclavian veins causes immediate extravasation, local thrombosis, and elevates intracranial pressure.</strong>
                  </p>
                  <div style={{ marginTop: '10px', display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: 700, color: '#991b1b' }}>
                      <input
                        type="checkbox"
                        checked={upperLimbAccessBanned}
                        onChange={(e) => setUpperLimbAccessBanned(e.target.checked)}
                      />
                      ✓ Upper limbs flagged &amp; banded (&quot;NO IV / NO BP&quot;)
                    </label>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: 700, color: '#166534' }}>
                      <input
                        type="checkbox"
                        checked={lowerExtremityIvConfirmed}
                        onChange={(e) => setLowerExtremityIvConfirmed(e.target.checked)}
                      />
                      ✓ Lower extremity / Saphenous / Femoral access established
                    </label>
                  </div>
                </div>
              </div>

              {/* STAT Standing Orders Card */}
              <div
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <h3 style={{ margin: '0 0 14px 0', fontSize: '16px', fontWeight: 800, color: '#0f172a' }}>
                  ⚡ STAT Oncologic Resuscitation Bundle:
                </h3>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
                  <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase' }}>
                      Positioning Order
                    </div>
                    <div style={{ fontSize: '16px', fontWeight: 800, color: '#0f172a', margin: '4px 0' }}>
                      Head of Bed Elevated 45° to 90°
                    </div>
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      Immediately lowers hydrostatic venous pressure in head and neck, mitigating brain herniation risk. <em>Do NOT allow patient to lie flat.</em>
                    </div>
                  </div>

                  <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#dc2626', textTransform: 'uppercase' }}>
                      STAT Corticosteroid Loading
                    </div>
                    <div style={{ fontSize: '16px', fontWeight: 800, color: '#dc2626', margin: '4px 0' }}>
                      Dexamethasone 16 mg IV STAT
                    </div>
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      Followed by 8 mg IV twice daily. Reduces tumor perivascular edema and prevents acute laryngeal occlusion. Provide Pantoprazole 40 mg IV daily.
                    </div>
                  </div>

                  <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                    <div style={{ fontSize: '11px', fontWeight: 700, color: '#059669', textTransform: 'uppercase' }}>
                      Oxygen &amp; Fluid Restriction
                    </div>
                    <div style={{ fontSize: '16px', fontWeight: 800, color: '#0f172a', margin: '4px 0' }}>
                      High-Flow O₂ Titrated to SpO₂ ≥ 94%
                    </div>
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      Avoid rapid fluid boluses which worsen venous congestion. Maintain strict fluid neutral balance.
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: SVC VASCULAR ANATOMY & INTERACTIVE STENT VISUALIZER */}
          {activeTab === 'vascular_anatomy' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

              {/* Visualizer Controls */}
              <div
                style={{
                  background: '#0f172a',
                  color: '#ffffff',
                  padding: '16px 20px',
                  borderRadius: '12px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '12px',
                }}
              >
                <div>
                  <div style={{ fontSize: '11px', color: '#38bdf8', fontWeight: 700, textTransform: 'uppercase' }}>
                    Thoracic Venous Cavography Model
                  </div>
                  <h3 style={{ margin: '4px 0 0 0', fontSize: '18px', fontWeight: 800 }}>
                    SVC Stenosis &amp; Endovascular Stent Decompression
                  </h3>
                </div>

                <div style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '12px', color: '#cbd5e1' }}>Stenosis:</span>
                    <input
                      type="range"
                      min="0"
                      max="100"
                      value={stenosisPercentage}
                      onChange={(e) => setStenosisPercentage(parseInt(e.target.value))}
                      style={{ width: '100px', accentColor: '#dc2626' }}
                    />
                    <span style={{ fontSize: '13px', fontWeight: 700, color: '#f87171' }}>{stenosisPercentage}%</span>
                  </div>

                  <button
                    type="button"
                    onClick={() => setStentDeployed(!stentDeployed)}
                    style={{
                      padding: '8px 14px',
                      borderRadius: '8px',
                      background: stentDeployed ? '#16a34a' : '#2563eb',
                      border: 'none',
                      color: '#ffffff',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    {stentDeployed ? '✓ Stent Deployed (Expand)' : '🚀 Deploy Endovascular Stent'}
                  </button>
                </div>
              </div>

              {/* Dynamic SVG Canvas */}
              <div
                style={{
                  background: '#1e293b',
                  borderRadius: '12px',
                  padding: '20px',
                  border: '1px solid #334155',
                }}
              >
                <svg
                  viewBox="0 0 800 360"
                  style={{ width: '100%', height: 'auto', display: 'block', background: '#0b1329', borderRadius: '8px' }}
                >
                  <defs>
                    <linearGradient id="venousEngorged" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#7c3aed" />
                      <stop offset="100%" stopColor="#4338ca" />
                    </linearGradient>
                    <linearGradient id="venousNormal" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#3b82f6" />
                      <stop offset="100%" stopColor="#1d4ed8" />
                    </linearGradient>
                    <pattern id="stentMesh" width="10" height="10" patternUnits="userSpaceOnUse">
                      <path d="M 0 0 L 10 10 M 10 0 L 0 10" stroke="#f59e0b" strokeWidth="1.2" />
                    </pattern>
                  </defs>

                  {/* Anatomical Outlines: Trachea */}
                  <rect x="375" y="20" width="50" height="140" fill="#334155" opacity="0.4" rx="6" />
                  <text x="400" y="45" fill="#94a3b8" fontSize="11" textAnchor="middle">Trachea</text>

                  {/* Right Atrium at Bottom */}
                  <ellipse cx="400" cy="300" rx="90" ry="45" fill="#1e3a8a" stroke="#3b82f6" strokeWidth="2" />
                  <text x="400" y="305" fill="#93c5fd" fontSize="13" fontWeight="bold" textAnchor="middle">Right Atrium</text>

                  {/* Left & Right Brachiocephalic Veins */}
                  {/* Right Brachiocephalic */}
                  <path
                    d="M 260 40 Q 320 80 370 120"
                    fill="none"
                    stroke={stenosisPercentage > 70 && !stentDeployed ? 'url(#venousEngorged)' : 'url(#venousNormal)'}
                    strokeWidth={stenosisPercentage > 70 && !stentDeployed ? 26 : 20}
                    strokeLinecap="round"
                  />
                  <text x="250" y="30" fill="#a5b4fc" fontSize="11" fontWeight="bold">Rt Brachiocephalic</text>

                  {/* Left Brachiocephalic */}
                  <path
                    d="M 540 40 Q 480 80 430 120"
                    fill="none"
                    stroke={stenosisPercentage > 70 && !stentDeployed ? 'url(#venousEngorged)' : 'url(#venousNormal)'}
                    strokeWidth={stenosisPercentage > 70 && !stentDeployed ? 26 : 20}
                    strokeLinecap="round"
                  />
                  <text x="545" y="30" fill="#a5b4fc" fontSize="11" fontWeight="bold">Lt Brachiocephalic</text>

                  {/* Main Superior Vena Cava (SVC) Trunk */}
                  {/* Outer Lumen */}
                  {(() => {
                    // Effective lumen width based on stenosis & stent
                    const compressedWidth = stentDeployed ? 24 : Math.max(4, 24 - (stenosisPercentage / 100) * 20);
                    return (
                      <g>
                        {/* SVC Upper segment */}
                        <path
                          d="M 370 120 L 370 170 Q 380 190 380 200 L 380 255"
                          fill="none"
                          stroke={stenosisPercentage > 70 && !stentDeployed ? 'url(#venousEngorged)' : 'url(#venousNormal)'}
                          strokeWidth={compressedWidth}
                        />
                        <path
                          d="M 430 120 L 430 170 Q 420 190 420 200 L 420 255"
                          fill="none"
                          stroke={stenosisPercentage > 70 && !stentDeployed ? 'url(#venousEngorged)' : 'url(#venousNormal)'}
                          strokeWidth={compressedWidth}
                        />

                        {/* Central Flow Channel */}
                        <line
                          x1="400"
                          y1="120"
                          x2="400"
                          y2="255"
                          stroke={stentDeployed ? '#60a5fa' : stenosisPercentage > 80 ? '#ef4444' : '#60a5fa'}
                          strokeWidth={compressedWidth + 4}
                          strokeLinecap="round"
                        />

                        {/* Stent Mesh Overlay when Deployed */}
                        {stentDeployed && (
                          <rect
                            x="386"
                            y="160"
                            width="28"
                            height="80"
                            fill="url(#stentMesh)"
                            stroke="#fbbf24"
                            strokeWidth="2"
                            rx="4"
                          />
                        )}

                        {/* Tumor Mass Compressing the SVC */}
                        <ellipse
                          cx={stentDeployed ? 460 : 435}
                          cy="200"
                          rx={stentDeployed ? 35 : 45}
                          ry={stentDeployed ? 30 : 40}
                          fill="#b91c1c"
                          stroke="#ef4444"
                          strokeWidth="2"
                          opacity="0.85"
                        />
                        <text
                          x={stentDeployed ? 460 : 435}
                          y="204"
                          fill="#ffffff"
                          fontSize="10"
                          fontWeight="bold"
                          textAnchor="middle"
                        >
                          Tumor
                        </text>
                      </g>
                    );
                  })()}

                  {/* Azygos Vein Collateral System */}
                  <path
                    d="M 400 180 C 310 170, 310 240, 370 255"
                    fill="none"
                    stroke={stenosisPercentage > 70 && !stentDeployed ? '#e11d48' : '#3b82f6'}
                    strokeWidth={stenosisPercentage > 70 && !stentDeployed ? 8 : 4}
                    strokeDasharray={stenosisPercentage > 70 && !stentDeployed ? '4 3' : 'none'}
                  />
                  <text x="300" y="210" fill={stenosisPercentage > 70 && !stentDeployed ? '#fb7185' : '#94a3b8'} fontSize="10" fontWeight="bold">
                    Azygos Collateral {stenosisPercentage > 70 && !stentDeployed ? '(Engorged Flow)' : ''}
                  </text>

                  {/* Callout Labels */}
                  <g fill="#e2e8f0" fontSize="11">
                    <text x="400" y="115" textAnchor="middle" fontWeight="bold">SVC Inlet</text>
                    <text x="400" y="275" textAnchor="middle">Cavoatrial Junction</text>
                  </g>
                </svg>
              </div>

              {/* Status Explanation */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px' }}>
                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '14px', fontWeight: 700, color: '#0f172a' }}>
                    1. Hemodynamic Stasis &amp; Intracranial Pressure
                  </h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>
                    With {stenosisPercentage}% luminal reduction, blood from the cerebral and brachiocephalic circuits is forced into collateral networks (azygos, internal mammary, vertebral plexus). Severe congestion induces cerebral edema, visual obscuration, and risk of brain herniation.
                  </p>
                </div>

                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '14px', fontWeight: 700, color: '#0f172a' }}>
                    2. Endovascular Stent Decompression Impact
                  </h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>
                    {stentDeployed
                      ? '✓ Stent expanded: Normal lumen restored (16–20 mm). Immediate reduction in central venous pressure, resolving facial plethora within 24 to 48 hours.'
                      : 'Deploying a self-expanding metallic stent provides immediate mechanical relief without waiting for chemotherapy or radiation response.'}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: DECOMPRESSION STRATEGY & MODALITY SELECTION */}
          {activeTab === 'decompression_strategy' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

              {/* Staged Modality Selection Matrix */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '18px' }}>

                {/* Option 1: Endovascular Stenting */}
                <div
                  style={{
                    background: '#eff6ff',
                    border: '2px solid #2563eb',
                    borderRadius: '12px',
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', color: '#1d4ed8', background: '#dbeafe', padding: '2px 8px', borderRadius: '4px' }}>
                      First-Line Emergency Choice (Grade 3–4)
                    </span>
                    <h4 style={{ margin: '10px 0 6px 0', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                      Endovascular SVC Stenting
                    </h4>
                    <p style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#475569' }}>
                      Self-expanding metallic stents (Wallstent, Gianturco) deployed across the obstruction via femoral vein access.
                    </p>

                    <div style={{ background: '#ffffff', borderRadius: '8px', padding: '12px', border: '1px solid #cbd5e1', marginBottom: '12px' }}>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: '#16a34a' }}>
                        ✓ Rapid Symptom Relief: 24 to 48 hours in &gt;95% of patients
                      </div>
                      <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                        Does NOT delay future chemotherapy or radiotherapy.
                      </div>
                    </div>

                    <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#475569', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <li><strong>Primary Indication:</strong> NSCLC, recurrent cancer, severe crisis, or radioresistant histologies.</li>
                      <li><strong>Anticoagulation:</strong> LMWH or dual antiplatelet therapy for 1–3 months post-stent.</li>
                    </ul>
                  </div>
                </div>

                {/* Option 2: Chemotherapy */}
                <div
                  style={{
                    background: tumorType === 'sclc' || tumorType === 'lymphoma' ? '#f0fdf4' : '#f8fafc',
                    border: `2px solid ${tumorType === 'sclc' || tumorType === 'lymphoma' ? '#16a34a' : '#e2e8f0'}`,
                    borderRadius: '12px',
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', color: '#15803d', background: '#dcfce7', padding: '2px 8px', borderRadius: '4px' }}>
                      Chemosensitive Tumors (Grade 1–2)
                    </span>
                    <h4 style={{ margin: '10px 0 6px 0', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                      Systemic Chemotherapy
                    </h4>
                    <p style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#475569' }}>
                      First-line for chemo-naive Small Cell Lung Cancer (SCLC), Lymphoma, or Germ Cell Tumors.
                    </p>

                    <div style={{ background: '#ffffff', borderRadius: '8px', padding: '12px', border: '1px solid #cbd5e1', marginBottom: '12px' }}>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: '#0f172a' }}>
                        Response Rate: 80% to 90% in SCLC
                      </div>
                      <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                        Median time to relief: 5 to 7 days post-cycle 1.
                      </div>
                    </div>

                    <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#475569', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <li><strong>SCLC Regimen:</strong> Carboplatin/Cisplatin + Etoposide (EP).</li>
                      <li><strong>Caution:</strong> If airway compromise is present, stenting must precede chemo!</li>
                    </ul>
                  </div>
                </div>

                {/* Option 3: Radiotherapy */}
                <div
                  style={{
                    background: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    borderRadius: '12px',
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', color: '#92400e', background: '#fef3c7', padding: '2px 8px', borderRadius: '4px' }}>
                      Palliative Radiotherapy
                    </span>
                    <h4 style={{ margin: '10px 0 6px 0', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                      Emergency Thoracic RT
                    </h4>
                    <p style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#475569' }}>
                      Indicated when endovascular stenting is unavailable and the tumor is radioresistant or chemo-refractory.
                    </p>

                    <div style={{ background: '#ffffff', borderRadius: '8px', padding: '12px', border: '1px solid #cbd5e1', marginBottom: '12px' }}>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: '#0f172a' }}>
                        Fractionation: 8 Gy in 1 fx OR 20 Gy in 5 fx
                      </div>
                      <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                        Response onset: 7 to 14 days (slower than stenting).
                      </div>
                    </div>

                    <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#475569', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <li><strong>Risk:</strong> Transient radiation edema can worsen obstruction; mandatory Dexamethasone coverage.</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: BILINGUAL FAMILY COUNSELING */}
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

              {counselingLang === 'en' ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      1. Explaining Facial Puffiness &amp; Swollen Veins:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;The main blood vessel (Superior Vena Cava) returning blood from the head, neck, and arms back to the heart is being compressed by the tumor. Because the blood cannot drain downward, it pools in the face, eyes, and neck, causing puffiness, a bluish tint, and a feeling of fullness in the head. Sitting upright helps gravity drain this blood.&quot;
                    </p>
                  </div>

                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      2. Explaining Why We Must NOT Use the Arms for IV Lines:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;You will notice that we have placed a strict warning band on both arms. Because the veins in the arms are under high back-pressure, putting an IV drip or taking blood pressure in the arms will cause severe swelling, pain, and blood clots. All IV fluids and medications must enter safely through the legs.&quot;
                    </p>
                  </div>

                  <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#991b1b' }}>
                      3. Red Flag Emergency Symptoms (Airway &amp; Brain):
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                      <li>Any high-pitched whistling noise when breathing in (stridor).</li>
                      <li>Inability to swallow liquids or sudden choking spells.</li>
                      <li>New confusion, blurred vision, or fainting when bending forward.</li>
                    </ul>
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      १. चेहरे और गले की सूजन का कारण समझाना:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;सिर और चेहरे से गंदा खून दिल तक ले जाने वाली मुख्य नस (SVC) पर छाती के ट्यूमर का दबाव बन गया है। नस दबने के कारण खून नीचे नहीं उतर पा रहा है, जिससे चेहरे पर सूजन, आंखों में भारीपन और गले की नसें फूल गई हैं। मरीज को सीधा बैठाकर रखने से गुरुत्वाकर्षण के कारण खून का बहाव सुधरता है।&quot;
                    </p>
                  </div>

                  <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                      २. हाथों में सुई या बीपी क्यों मना है:
                    </h4>
                    <p style={{ margin: 0, fontSize: '13px', color: '#334155', lineHeight: 1.6, fontStyle: 'italic' }}>
                      &quot;हाथों की नसों में खून का दबाव बहुत ज्यादा है। अगर हम हाथ में सलाइन या इंजेक्शन लगाएंगे, तो हाथ में भारी सूजन और खून का थक्का जम सकता है। इसलिए सभी दवाइयां और सलाइन केवल पैर की नस से दिए जाएंगे।&quot;
                    </p>
                  </div>

                  <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '16px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '15px', fontWeight: 700, color: '#991b1b' }}>
                      ३. तुरंत डॉक्टर को बुलाने के गंभीर लक्षण:
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '13px', color: '#7f1d1d', lineHeight: 1.5 }}>
                      <li>सांस लेते समय सीटी जैसी आवाज (stridor) आना।</li>
                      <li>पानी या थूक निगलने में अचानक भारी कठिनाई होना।</li>
                      <li>आगे झुकने पर चक्कर आना, आंखों के आगे अंधेरा छाना या बेहोशी महसूस होना।</li>
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
            SVCO: <strong style={{ color: yuGrade === 'grade_4' ? '#dc2626' : '#0f172a' }}>{yuGrade.toUpperCase()}</strong> · Kishi: <strong>{kishiScore}/10</strong> · Access: <strong>{lowerExtremityIvConfirmed ? 'Lower Extremity Confirmed' : 'Check IV'}</strong>
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
                background: yuGrade === 'grade_4' || yuGrade === 'grade_3' ? 'linear-gradient(135deg, #dc2626 0%, #b91c1c 100%)' : 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
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
              <span>Dispatch SVCO Decompression Protocol</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
