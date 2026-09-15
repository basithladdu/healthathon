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

export type ObstructionLevel = 'gastric_outlet' | 'small_bowel_proximal' | 'small_bowel_distal' | 'large_bowel';
export type ObstructionType = 'mechanical' | 'functional_paralytic' | 'mixed';

export interface SurgicalFactor {
  id: string;
  name: string;
  hindiName: string;
  points: number;
  description: string;
}

interface MalignantBowelObstructionModalProps {
  patientId: string;
  patientName: string;
  primaryCancer: string;
  onClose: () => void;
  onDispatchPlan?: (summary: string) => void;
}

export function MalignantBowelObstructionModal({
  patientId,
  patientName,
  primaryCancer,
  onClose,
  onDispatchPlan,
}: MalignantBowelObstructionModalProps) {
  const [activeTab, setActiveTab] = useState<'pathophysiology' | 'surgical_score' | 'quad_regimen' | 'venting_comfort' | 'history'>('pathophysiology');

  // Clinical Characteristics
  const [obstructionLevel, setObstructionLevel] = useState<ObstructionLevel>('small_bowel_distal');
  const [obstructionType, setObstructionType] = useState<ObstructionType>('mechanical');
  const [hasColickyPain, setHasColickyPain] = useState<boolean>(true);
  const [vomitCharacter, setVomitCharacter] = useState<'bilious' | 'feculent' | 'undigested_food' | 'clear'>('feculent');
  const [bowelSounds, setBowelSounds] = useState<'hyperactive_tinkling' | 'hypoactive' | 'absent'>('hyperactive_tinkling');

  // Surgical Candidacy Criteria (Pothuri / Ripamonti Risk Criteria)
  const [selectedRiskFactors, setSelectedRiskFactors] = useState<{ [key: string]: boolean }>({
    ascites_massive: true, // +2
    multiple_levels: true, // +2
    poor_ecog: true, // +2
    prior_radiotherapy: false, // +1
    severe_cachexia: true, // +1
    extra_abdominal_mets: false, // +1
  });

  // Quad-Drug Syringe Driver Parameters
  const [octreotideDoseMcg, setOctreotideDoseMcg] = useState<number>(600); // 300 to 900 mcg/24h
  const [buscopanDoseMg, setBuscopanDoseMg] = useState<number>(60); // 40 to 120 mg/24h
  const [haloperidolDoseMg, setHaloperidolDoseMg] = useState<number>(2.5); // 1.5 to 5 mg/24h
  const [morphineDoseMg, setMorphineDoseMg] = useState<number>(20); // 10 to 60 mg/24h
  const [dexamethasoneDoseMg, setDexamethasoneDoseMg] = useState<number>(8); // 8 to 16 mg IV/SC daily

  // Toast feedback
  const [copiedToast, setCopiedToast] = useState(false);
  const [dispatchedToast, setDispatchedToast] = useState(false);

  // Surgical Risk Factors List
  const surgicalRiskList: SurgicalFactor[] = [
    {
      id: 'ascites_massive',
      name: 'Gross Clinical / Radiologic Ascites (>1 Liter)',
      hindiName: 'पेट में अत्यधिक पानी (जलोदर > 1 लीटर)',
      points: 2,
      description: 'Presence of tense or moderate-to-severe ascites indicates diffuse peritoneal carcinomatosis and correlates with >70% surgical anastomotic failure.',
    },
    {
      id: 'multiple_levels',
      name: 'Multiple Levels of Obstruction on CT',
      hindiName: 'सीटी स्कैन पर आंतों में कई जगह रुकावट',
      points: 2,
      description: 'Multiple discrete transition points or diffused peritoneal tumor caking makes surgical bypass or resection anatomically non-feasible.',
    },
    {
      id: 'poor_ecog',
      name: 'Poor Performance Status (ECOG 3–4 / Karnofsky < 50%)',
      hindiName: 'अत्यधिक शारीरिक कमजोरी (बिस्तर पर आश्रित)',
      points: 2,
      description: 'Bedridden or severely debilitated patients have a 30-day post-laparotomy mortality exceeding 35–45%.',
    },
    {
      id: 'prior_radiotherapy',
      name: 'Prior High-Dose Pelvic / Abdominal Radiotherapy',
      hindiName: 'पूर्व में पेट या पेल्विस की रेडिएशन सिकाई',
      points: 1,
      description: 'Induces dense radiation enteritis, obliterating tissue planes and markedly increasing risks of postoperative enterocutaneous fistulae.',
    },
    {
      id: 'severe_cachexia',
      name: 'Severe Hypoalbuminemia (< 2.5 g/dL) & Cachexia',
      hindiName: 'गंभीर कुपोषण एवं एल्ब्यूमिन में भारी कमी',
      points: 1,
      description: 'Impairs wound healing and leads to wound breakdown, evisceration, and septic shock.',
    },
    {
      id: 'extra_abdominal_mets',
      name: 'Widespread Distant Metastases (Liver / Pleural / Lung)',
      hindiName: 'पेट के बाहर अंगों (फेफड़े, यकृत) में कैंसर का फैलाव',
      points: 1,
      description: 'Shortens overall life expectancy, making major palliative abdominal surgery a net negative in terms of recovery time vs. remaining survival.',
    },
  ];

  // Calculate Total Surgical Risk Score
  const totalSurgicalRiskScore = useMemo(() => {
    return surgicalRiskList.reduce((sum, factor) => {
      return sum + (selectedRiskFactors[factor.id] ? factor.points : 0);
    }, 0);
  }, [selectedRiskFactors]);

  // Surgical Candidacy Interpretation
  const surgicalCandidacy = useMemo(() => {
    if (totalSurgicalRiskScore <= 2) {
      return {
        tier: 'Surgical Consideration (Low Palliative Risk)',
        color: 'text-emerald-700 bg-emerald-50 border-emerald-200',
        badge: 'bg-emerald-100 text-emerald-800',
        recommendation: 'Potential candidate for palliative surgical bypass, loop venting ostomy, or endoscopic self-expanding metal stent (SEMS). Urgent surgical oncology consult indicated.',
        approach: 'Surgical / Interventional Stent',
      };
    }
    if (totalSurgicalRiskScore <= 4) {
      return {
        tier: 'Intermediate Risk / Multidisciplinary Decision',
        color: 'text-amber-700 bg-amber-50 border-amber-200',
        badge: 'bg-amber-100 text-amber-800',
        recommendation: 'Surgical risk is substantial. Initiate immediate 72-hour trial of medical decompression (Octreotide + Dexamethasone + Buscopan). Reserve surgery only if complete refractory and single focal transition point.',
        approach: 'Trial of Medical Decompression First',
      };
    }
    return {
      tier: 'High Surgical Mortality / Surgical Futility (Scores 5–9)',
      color: 'text-rose-700 bg-rose-50 border-rose-200',
      badge: 'bg-rose-100 text-rose-800',
      recommendation: 'Palliative laparotomy is contraindicated due to prohibitive operative mortality (>40%) and lack of symptom benefit. Strict medical decompression via subcutaneous continuous syringe driver is the standard of care.',
      approach: 'Conservative Medical Palliation & Venting',
    };
  }, [totalSurgicalRiskScore]);

  // Obstruction Level Details
  const levelMetadata = {
    gastric_outlet: {
      name: 'Gastric Outlet / Duodenal Obstruction',
      hindi: 'आमाशय निकास द्वार की रुकावट',
      symptoms: 'Large-volume non-bilious vomitus containing food eaten hours earlier, early satiety, epigastric fullness, succussion splash.',
      stentFeasible: true,
      perforationRisk: 'Low',
    },
    small_bowel_proximal: {
      name: 'Proximal Small Bowel (Jejunal)',
      hindi: 'ऊपरी छोटी आंत (जेजुनम) की रुकावट',
      symptoms: 'Rapid onset bilious (green/yellow) vomiting, epigastric & periumbilical colicky cramps, minimal initial distension, rapid electrolyte depletion.',
      stentFeasible: false,
      perforationRisk: 'Moderate',
    },
    small_bowel_distal: {
      name: 'Distal Small Bowel (Ileal)',
      hindi: 'निचली छोटी आंत (इलियम) की रुकावट',
      symptoms: 'Late feculent (brown, malodorous) vomiting, diffuse wave-like abdominal colic, marked progressive central abdominal distension, multiple air-fluid levels.',
      stentFeasible: false,
      perforationRisk: 'High if competent valve',
    },
    large_bowel: {
      name: 'Large Bowel / Colonic',
      hindi: 'बड़ी आंत (कोलन) की रुकावट',
      symptoms: 'Absolute obstipation (no flatus or stool), delayed vomiting, massive peripheral abdominal distension, imminent risk of cecal perforation if diameter > 10–12 cm.',
      stentFeasible: true,
      perforationRisk: 'Critical (Cecal blowout)',
    },
  };

  // Toggle factor
  const toggleRiskFactor = (id: string) => {
    setSelectedRiskFactors((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  // Copy Plan
  const handleCopyPlan = () => {
    const summary = `MALIGNANT BOWEL OBSTRUCTION (MBO) CLINICAL DECISION
Patient: ${patientName} • ${primaryCancer} • Hospital ID: #${patientId}
Diagnosis: Malignant Bowel Obstruction (${levelMetadata[obstructionLevel].name})
Pathology: ${obstructionType.toUpperCase()} • Bowel Sounds: ${bowelSounds.replace('_', ' ')}
Palliative Surgical Risk Score: ${totalSurgicalRiskScore}/9 (${surgicalCandidacy.tier})
Recommendation: ${surgicalCandidacy.recommendation}

1. CONTINUOUS 24-HOUR SC SYRINGE DRIVER (QUAD REGIMEN):
- Octreotide: ${octreotideDoseMcg} mcg/24h continuous SC (Reduces GI secretions by ~2 L/day).
- Hyoscine Butylbromide (Buscopan): ${buscopanDoseMg} mg/24h continuous SC (Antispasmodic for colic).
- Haloperidol: ${haloperidolDoseMg} mg/24h continuous SC (Central antiemetic).
- Morphine Sulfate: ${morphineDoseMg} mg/24h continuous SC (Visceral tumor pain).
- Diluent: Water for Injections to 48 mL in 50 mL syringe (infusion rate 2 mL/hr).

2. ANTI-INFLAMMATORY ADJUVANT:
- Dexamethasone: ${dexamethasoneDoseMg} mg SC/IV daily morning dose (Reduces peritumoral edema).

3. SAFETY WARNING:
- CONTRAINDICATION: Prokinetic agents (Metoclopramide) are strictly PROHIBITED in complete mechanical obstruction due to risk of agonizing colic and bowel perforation!

4. VENTING & DECOMPRESSION:
- ${totalSurgicalRiskScore >= 5 ? 'Venting PEG or short-term NGT decompression until Octreotide controls secretions.' : 'Evaluation for Endoscopic SEMS or Surgical Ostomy.'}
- Mouth care: Ice chips, lemon glycerin swabs, sips of fluid for pleasure only (no force-feeding).`;

    navigator.clipboard.writeText(summary);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 3000);
  };

  // Dispatch Plan
  const handleDispatchPlan = () => {
    const summary = `MBO Quad Regimen Dispatched: Octreotide ${octreotideDoseMcg}mcg + Buscopan ${buscopanDoseMg}mg + Haloperidol ${haloperidolDoseMg}mg + Morphine ${morphineDoseMg}mg/24h SC. Surgical Score ${totalSurgicalRiskScore}/9 (${surgicalCandidacy.approach}).`;
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
        <div className="bg-gradient-to-r from-teal-800 via-emerald-800 to-cyan-900 text-white p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-white/15 rounded-xl backdrop-blur-md text-white border border-white/20">
              <IconHeartPulse className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Malignant Bowel Obstruction (MBO) Decision Suite</h2>
                <span className="px-2 py-0.5 bg-white/20 rounded-full text-[11px] font-semibold tracking-wide uppercase">
                  Palliative GI Care
                </span>
              </div>
              <p className="text-xs text-teal-100 mt-0.5">
                Patient: <span className="font-semibold text-white">{patientName}</span> • {primaryCancer} • Hospital ID: #{patientId}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-[11px] text-teal-100 font-medium">Surgical Risk Score</div>
              <div className="text-sm font-extrabold text-white">{totalSurgicalRiskScore} / 9 • {surgicalCandidacy.approach}</div>
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
            onClick={() => setActiveTab('pathophysiology')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'pathophysiology'
                ? 'border-teal-600 text-teal-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconSparkles className="w-4 h-4" />
            <span>1. Obstruction Anatomy & Pathophysiology</span>
          </button>
          <button
            onClick={() => setActiveTab('surgical_score')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'surgical_score'
                ? 'border-teal-600 text-teal-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldAlert className="w-4 h-4" />
            <span>2. Palliative Surgical Candidacy (Pothuri/Ripamonti)</span>
          </button>
          <button
            onClick={() => setActiveTab('quad_regimen')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'quad_regimen'
                ? 'border-teal-600 text-teal-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconShieldCheck className="w-4 h-4" />
            <span>3. Syringe Driver Quad-Drug Regimen (Octreotide/Buscopan)</span>
          </button>
          <button
            onClick={() => setActiveTab('venting_comfort')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'venting_comfort'
                ? 'border-teal-600 text-teal-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconUsers className="w-4 h-4" />
            <span>4. Venting Decompression & Mouth Care (द्विभाषी)</span>
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-3 px-3.5 border-b-2 transition flex items-center gap-2 whitespace-nowrap ${
              activeTab === 'history'
                ? 'border-teal-600 text-teal-700 font-bold bg-white rounded-t-lg'
                : 'border-transparent hover:text-slate-900'
            }`}
          >
            <IconClock className="w-4 h-4" />
            <span>5. GI Symptom Trajectory Log</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 bg-slate-50/40">
          
          {/* Quick GI Alert Bar */}
          <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-xs mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Obstruction Level:</span>
                <span className="font-extrabold text-teal-800 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                  {levelMetadata[obstructionLevel].name}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Type:</span>
                <span className="capitalize font-semibold text-slate-800">{obstructionType.replace('_', ' ')}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-slate-700">Vomitus:</span>
                <span className="capitalize font-semibold text-amber-800">{vomitCharacter}</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className={`px-2.5 py-1 rounded-full text-xs font-bold border ${surgicalCandidacy.color}`}>
                Risk Score: {totalSurgicalRiskScore} / 9 • {surgicalCandidacy.approach}
              </span>
            </div>
          </div>

          {/* TAB 1: OBSTRUCTION ANATOMY & PATHOPHYSIOLOGY */}
          {activeTab === 'pathophysiology' && (
            <div className="space-y-6">
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* Left: Anatomical SVG Visualizer */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between border-b pb-3 mb-3">
                      <div>
                        <h3 className="text-sm font-bold text-slate-900">Gastrointestinal Tract Obstruction Map</h3>
                        <p className="text-xs text-slate-500">Visual mapping of tumor transition points and proximal fluid distension</p>
                      </div>
                      <span className="text-[11px] font-bold text-teal-700 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                        Interactive SVG
                      </span>
                    </div>

                    {/* SVG Gastrointestinal Schematic */}
                    <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex flex-col items-center">
                      <svg className="w-full max-w-sm h-64" viewBox="0 0 300 240">
                        {/* Esophagus */}
                        <path d="M 140 10 L 140 45" stroke="#94a3b8" strokeWidth="12" strokeLinecap="round" />
                        
                        {/* Stomach */}
                        <path
                          d="M 140 45 C 170 45, 200 65, 195 95 C 190 120, 150 125, 130 115 C 115 108, 120 70, 140 45"
                          fill={obstructionLevel === 'gastric_outlet' ? '#fed7aa' : '#f1f5f9'}
                          stroke={obstructionLevel === 'gastric_outlet' ? '#ea580c' : '#64748b'}
                          strokeWidth="2.5"
                        />
                        <text x="160" y="80" fontSize="10" fontWeight="bold" fill="#475569" textAnchor="middle">
                          Stomach
                        </text>

                        {/* Duodenal Loop */}
                        <path
                          d="M 130 115 C 105 115, 95 140, 115 155"
                          fill="none"
                          stroke={obstructionLevel === 'gastric_outlet' ? '#ea580c' : '#64748b'}
                          strokeWidth="8"
                          strokeLinecap="round"
                        />

                        {/* Small Intestine Coils (Jejunum & Ileum) */}
                        <g>
                          {/* Proximal Jejunum */}
                          <path
                            d="M 115 155 C 135 150, 155 170, 130 175 C 110 180, 160 190, 140 195"
                            fill="none"
                            stroke={
                              obstructionLevel === 'small_bowel_proximal' || obstructionLevel === 'small_bowel_distal'
                                ? '#f87171'
                                : '#cbd5e1'
                            }
                            strokeWidth="9"
                            strokeLinecap="round"
                          />
                          {/* Distal Ileum */}
                          <path
                            d="M 140 195 C 120 200, 180 205, 195 200"
                            fill="none"
                            stroke={obstructionLevel === 'small_bowel_distal' ? '#ef4444' : '#cbd5e1'}
                            strokeWidth="9"
                            strokeLinecap="round"
                          />
                        </g>

                        {/* Colon (Ascending, Transverse, Descending) */}
                        <g>
                          {/* Cecum */}
                          <rect
                            x="195"
                            y="180"
                            width="25"
                            height="35"
                            rx="10"
                            fill={obstructionLevel === 'large_bowel' ? '#fecaca' : '#f8fafc'}
                            stroke={obstructionLevel === 'large_bowel' ? '#dc2626' : '#94a3b8'}
                            strokeWidth="2"
                          />
                          <text x="207" y="200" fontSize="8" fontWeight="bold" fill="#64748b" textAnchor="middle">
                            Cecum
                          </text>

                          {/* Ascending Colon */}
                          <path d="M 210 180 L 210 80" stroke={obstructionLevel === 'large_bowel' ? '#dc2626' : '#94a3b8'} strokeWidth="14" strokeLinecap="round" />
                          {/* Transverse Colon */}
                          <path d="M 210 80 L 80 80" stroke={obstructionLevel === 'large_bowel' ? '#dc2626' : '#94a3b8'} strokeWidth="14" strokeLinecap="round" />
                          {/* Descending Colon */}
                          <path d="M 80 80 L 80 190" stroke={obstructionLevel === 'large_bowel' ? '#dc2626' : '#94a3b8'} strokeWidth="14" strokeLinecap="round" />
                          {/* Sigmoid / Rectum */}
                          <path d="M 80 190 C 80 215, 120 215, 120 235" stroke="#94a3b8" strokeWidth="12" fill="none" strokeLinecap="round" />
                        </g>

                        {/* Obstruction Indicators (Red Tumor Mass & Marker) */}
                        {obstructionLevel === 'gastric_outlet' && (
                          <g>
                            <circle cx="130" cy="115" r="12" fill="#ef4444" opacity="0.8" />
                            <circle cx="130" cy="115" r="6" fill="#b91c1c" />
                            <text x="130" y="140" fontSize="10" fontWeight="extrabold" fill="#b91c1c" textAnchor="middle">
                              ⚡ Pyloric / Duodenal Tumor
                            </text>
                          </g>
                        )}

                        {obstructionLevel === 'small_bowel_proximal' && (
                          <g>
                            <circle cx="130" cy="175" r="12" fill="#ef4444" opacity="0.8" />
                            <circle cx="130" cy="175" r="6" fill="#b91c1c" />
                            <text x="130" y="165" fontSize="10" fontWeight="extrabold" fill="#b91c1c" textAnchor="middle">
                              ⚡ Mid-Jejunal Stricture
                            </text>
                          </g>
                        )}

                        {obstructionLevel === 'small_bowel_distal' && (
                          <g>
                            <circle cx="180" cy="200" r="12" fill="#ef4444" opacity="0.8" />
                            <circle cx="180" cy="200" r="6" fill="#b91c1c" />
                            <text x="180" y="225" fontSize="10" fontWeight="extrabold" fill="#b91c1c" textAnchor="middle">
                              ⚡ Distal Ileal Carcinomatosis
                            </text>
                          </g>
                        )}

                        {obstructionLevel === 'large_bowel' && (
                          <g>
                            <circle cx="80" cy="140" r="12" fill="#ef4444" opacity="0.8" />
                            <circle cx="80" cy="140" r="6" fill="#b91c1c" />
                            <text x="80" y="165" fontSize="10" fontWeight="extrabold" fill="#b91c1c" textAnchor="middle">
                              ⚡ Colonic Ring Mass
                            </text>
                          </g>
                        )}
                      </svg>

                      {/* Decompression Mechanism Callout */}
                      <div className="mt-2 text-center text-xs text-slate-700">
                        <span className="font-bold text-slate-900">Perfusion & Decompression Dynamics:</span>
                        <p className="text-[11px] text-slate-500 mt-0.5">
                          Intestinal lumen secretes up to 8 liters of fluid/day. Obstruction leads to bacterial overgrowth, intramural edema, third-spacing, and painful peristaltic contraction waves.
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Danger Alert */}
                  <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-xs text-red-900">
                    <IconShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                    <div>
                      <span className="font-bold">CRITICAL PROKINETIC WARNING:</span>
                      <p className="text-[11px] text-red-800 mt-0.5">
                        Never give prokinetic agents like <b>Metoclopramide (Reglan)</b> or Erythromycin if mechanical obstruction is suspected! Forcing peristalsis against a closed obstruction causes agonizing colic and increases bowel perforation risk.
                      </p>
                    </div>
                  </div>
                </div>

                {/* Right: Diagnostic Assessment & Level Selectors */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="border-b pb-3">
                    <h3 className="text-sm font-bold text-slate-900">Clinical Staging & Examination Findings</h3>
                    <p className="text-xs text-slate-500">Configure patient presentation to determine obstruction level</p>
                  </div>

                  {/* Level Selector Buttons */}
                  <div>
                    <label className="text-xs font-bold text-slate-700 block mb-1.5">1. Suspected Anatomical Level:</label>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {[
                        { id: 'gastric_outlet', name: 'Gastric Outlet / Duodenal', hindi: 'आमाशय / ग्रहणी' },
                        { id: 'small_bowel_proximal', name: 'Proximal Small Bowel (Jejunal)', hindi: 'ऊपरी छोटी आंत' },
                        { id: 'small_bowel_distal', name: 'Distal Small Bowel (Ileal)', hindi: 'निचली छोटी आंत' },
                        { id: 'large_bowel', name: 'Large Bowel / Colonic', hindi: 'बड़ी आंत' },
                      ].map((lvl) => (
                        <button
                          key={lvl.id}
                          onClick={() => setObstructionLevel(lvl.id as any)}
                          className={`p-2.5 rounded-lg border text-left text-xs transition ${
                            obstructionLevel === lvl.id
                              ? 'border-teal-600 bg-teal-50 text-teal-950 font-bold ring-1 ring-teal-500'
                              : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100 text-slate-700'
                          }`}
                        >
                          <div>{lvl.name}</div>
                          <div className="text-[10px] text-slate-500 font-normal">{lvl.hindi}</div>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Mechanical vs Functional */}
                  <div>
                    <label className="text-xs font-bold text-slate-700 block mb-1.5">2. Pathologic Mechanism:</label>
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { id: 'mechanical', name: 'Mechanical', desc: 'Focal tumor mass / stricture' },
                        { id: 'functional_paralytic', name: 'Paralytic Ileus', desc: 'Diffuse peritoneal carcinomatosis' },
                        { id: 'mixed', name: 'Mixed Presentation', desc: 'Mass + opioid hypomotility' },
                      ].map((type) => (
                        <button
                          key={type.id}
                          onClick={() => setObstructionType(type.id as any)}
                          className={`p-2 rounded-lg border text-left text-xs transition ${
                            obstructionType === type.id
                              ? 'border-teal-600 bg-teal-50 text-teal-950 font-bold ring-1 ring-teal-500'
                              : 'border-slate-200 bg-slate-50 text-slate-700'
                          }`}
                        >
                          <div className="font-bold">{type.name}</div>
                          <div className="text-[10px] text-slate-500 leading-tight mt-0.5">{type.desc}</div>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Vomitus Character & Colic */}
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-bold text-slate-700 block mb-1">3. Vomitus Character:</label>
                      <select
                        value={vomitCharacter}
                        onChange={(e) => setVomitCharacter(e.target.value as any)}
                        className="w-full p-2 border border-slate-300 rounded-lg text-xs bg-slate-50 font-medium"
                      >
                        <option value="feculent">Feculent (Brown, foul odor) - Distal</option>
                        <option value="bilious">Bilious (Green/golden) - Mid small bowel</option>
                        <option value="undigested_food">Undigested Food - Gastric outlet</option>
                        <option value="clear">Clear / Mucus</option>
                      </select>
                    </div>

                    <div>
                      <label className="text-xs font-bold text-slate-700 block mb-1">4. Bowel Auscultation:</label>
                      <select
                        value={bowelSounds}
                        onChange={(e) => setBowelSounds(e.target.value as any)}
                        className="w-full p-2 border border-slate-300 rounded-lg text-xs bg-slate-50 font-medium"
                      >
                        <option value="hyperactive_tinkling">High-pitched &quot;Tinkling&quot; (Mechanical)</option>
                        <option value="hypoactive">Hypoactive / Sluggish</option>
                        <option value="absent">Silent / Absent (Paralytic / Peritonitis)</option>
                      </select>
                    </div>
                  </div>

                  {/* Level Summary Box */}
                  <div className="p-3 bg-teal-50/70 border border-teal-200 rounded-xl text-xs space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-teal-950">{levelMetadata[obstructionLevel].name}</span>
                      <span className="text-[11px] font-semibold text-teal-700">
                        Endoscopic Stent (SEMS): {levelMetadata[obstructionLevel].stentFeasible ? '✓ Feasible' : '✗ Not Feasible'}
                      </span>
                    </div>
                    <p className="text-[11px] text-teal-900">{levelMetadata[obstructionLevel].symptoms}</p>
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 2: PALLIATIVE SURGICAL CANDIDACY (POTHURI / RIPAMONTI) */}
          {activeTab === 'surgical_score' && (
            <div className="space-y-5">
              
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                <div className="flex items-center justify-between border-b pb-3 mb-4">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Palliative Surgical Candidacy Assessment (Pothuri / Ripamonti Criteria)</h3>
                    <p className="text-xs text-slate-500">
                      Predicts surgical morbidity, 30-day perioperative mortality, and identifies patients where surgery is futile.
                    </p>
                  </div>
                  <div className="text-right">
                    <span className="text-2xl font-black text-teal-700">{totalSurgicalRiskScore}</span>
                    <span className="text-xs font-bold text-slate-400"> / 9</span>
                  </div>
                </div>

                {/* Risk Factors Checklist */}
                <div className="space-y-2.5">
                  {surgicalRiskList.map((factor) => {
                    const isChecked = !!selectedRiskFactors[factor.id];
                    return (
                      <div
                        key={factor.id}
                        onClick={() => toggleRiskFactor(factor.id)}
                        className={`p-3.5 rounded-xl border cursor-pointer transition flex items-start justify-between gap-3 ${
                          isChecked
                            ? 'border-teal-600 bg-teal-50/60 ring-1 ring-teal-500'
                            : 'border-slate-200 bg-white hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-start gap-3">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => {}}
                            className="mt-1 h-4 w-4 accent-teal-600 rounded cursor-pointer"
                          />
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-bold text-slate-900">{factor.name}</span>
                              <span className="text-[11px] text-slate-500">({factor.hindiName})</span>
                            </div>
                            <p className="text-[11px] text-slate-600 mt-0.5 leading-relaxed">{factor.description}</p>
                          </div>
                        </div>

                        <span className={`px-2 py-0.5 rounded text-xs font-bold shrink-0 ${
                          isChecked ? 'bg-teal-200 text-teal-900' : 'bg-slate-100 text-slate-600'
                        }`}>
                          +{factor.points} {factor.points === 1 ? 'pt' : 'pts'}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Recommendation Banner */}
              <div className={`p-4 rounded-xl border ${surgicalCandidacy.color} flex flex-wrap items-center justify-between gap-4`}>
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${surgicalCandidacy.badge}`}>
                      {surgicalCandidacy.tier}
                    </span>
                    <span className="text-xs font-bold text-slate-700">Pothuri Risk Score: {totalSurgicalRiskScore} / 9</span>
                  </div>
                  <p className="text-xs text-slate-900 font-medium max-w-3xl leading-relaxed">
                    {surgicalCandidacy.recommendation}
                  </p>
                </div>

                <button
                  onClick={() => setActiveTab('quad_regimen')}
                  className="px-4 py-2 bg-teal-700 hover:bg-teal-800 text-white text-xs font-bold rounded-lg transition shadow-xs flex items-center gap-1.5 shrink-0"
                >
                  <span>Configure Syringe Driver Regimen</span>
                  <span>→</span>
                </button>
              </div>

            </div>
          )}

          {/* TAB 3: SYRINGE DRIVER QUAD-DRUG REGIMEN */}
          {activeTab === 'quad_regimen' && (
            <div className="space-y-6">
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                
                {/* Left: Quad-Drug Sliders */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
                  <div className="border-b pb-3 flex items-center justify-between">
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">24-Hour Continuous SC Syringe Driver Recipe</h3>
                      <p className="text-xs text-slate-500">Evidence-based Quad Regimen for inoperable malignant bowel obstruction</p>
                    </div>
                    <span className="px-2 py-0.5 bg-emerald-50 text-emerald-800 border border-emerald-200 rounded text-xs font-bold">
                      Compatible Mixture
                    </span>
                  </div>

                  {/* 1. Octreotide (Somatostatin Analogue) */}
                  <div className="p-3 rounded-xl bg-teal-50/60 border border-teal-200 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-teal-950">1. Octreotide (Secretory Inhibitor):</span>
                      <span className="font-black text-teal-800 text-sm">{octreotideDoseMcg} mcg / 24h</span>
                    </div>
                    <input
                      type="range"
                      min="300"
                      max="900"
                      step="100"
                      value={octreotideDoseMcg}
                      onChange={(e) => setOctreotideDoseMcg(Number(e.target.value))}
                      className="w-full accent-teal-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-500">
                      <span>300 mcg (Starting)</span>
                      <span>600 mcg (Standard)</span>
                      <span>900 mcg (Refractory)</span>
                    </div>
                    <p className="text-[11px] text-teal-900 mt-1">
                      Inhibits gastrin, secretin & VIP. Reduces intra-luminal GI secretions by up to 2 liters/day, eliminating nausea without nasogastric tube.
                    </p>
                  </div>

                  {/* 2. Hyoscine Butylbromide (Buscopan) */}
                  <div className="p-3 rounded-xl bg-sky-50/60 border border-sky-200 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-sky-950">2. Buscopan (Antispasmodic):</span>
                      <span className="font-black text-sky-800 text-sm">{buscopanDoseMg} mg / 24h</span>
                    </div>
                    <input
                      type="range"
                      min="40"
                      max="120"
                      step="20"
                      value={buscopanDoseMg}
                      onChange={(e) => setBuscopanDoseMg(Number(e.target.value))}
                      className="w-full accent-sky-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-500">
                      <span>40 mg (Mild Colic)</span>
                      <span>60 mg (Standard)</span>
                      <span>120 mg (Max Antispasmodic)</span>
                    </div>
                    <p className="text-[11px] text-sky-900 mt-1">
                      Smooth muscle relaxant. Relieves severe peristaltic colic cramps and also reduces oral/gastric secretions.
                    </p>
                  </div>

                  {/* 3. Haloperidol (Antiemetic) */}
                  <div className="p-3 rounded-xl bg-amber-50/60 border border-amber-200 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-amber-950">3. Haloperidol (CTZ Antiemetic):</span>
                      <span className="font-black text-amber-800 text-sm">{haloperidolDoseMg} mg / 24h</span>
                    </div>
                    <input
                      type="range"
                      min="1.5"
                      max="5.0"
                      step="0.5"
                      value={haloperidolDoseMg}
                      onChange={(e) => setHaloperidolDoseMg(Number(e.target.value))}
                      className="w-full accent-amber-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-500">
                      <span>1.5 mg</span>
                      <span>2.5 mg (Standard)</span>
                      <span>5.0 mg</span>
                    </div>
                    <p className="text-[11px] text-amber-900 mt-1">
                      Potent dopamine D2 antagonist at chemoreceptor trigger zone. Controls central chemical nausea without prokinetic stimulation.
                    </p>
                  </div>

                  {/* 4. Morphine (Analgesia) */}
                  <div className="p-3 rounded-xl bg-rose-50/60 border border-rose-200 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-rose-950">4. Morphine Sulfate (Visceral Pain):</span>
                      <span className="font-black text-rose-800 text-sm">{morphineDoseMg} mg / 24h</span>
                    </div>
                    <input
                      type="range"
                      min="10"
                      max="60"
                      step="5"
                      value={morphineDoseMg}
                      onChange={(e) => setMorphineDoseMg(Number(e.target.value))}
                      className="w-full accent-rose-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] text-slate-500">
                      <span>10 mg (Naive)</span>
                      <span>20–30 mg (Standard)</span>
                      <span>60 mg (High baseline)</span>
                    </div>
                    <p className="text-[11px] text-rose-900 mt-1">
                      Controls constant background tumor pain. Breakthrough PRN: {Number((morphineDoseMg / 6).toFixed(1))} mg SC q1h PRN.
                    </p>
                  </div>
                </div>

                {/* Right: Formulation Card & Dexamethasone */}
                <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs flex flex-col justify-between space-y-4">
                  <div>
                    <div className="border-b pb-3 mb-3">
                      <h3 className="text-sm font-bold text-slate-900">Bedside Syringe Driver Compounding Order</h3>
                      <p className="text-xs text-slate-500">Standard 50 mL McKinley / BodyGuard Infusion Device</p>
                    </div>

                    {/* Prescription Summary Table */}
                    <div className="border border-slate-200 rounded-xl overflow-hidden bg-slate-50/50">
                      <table className="w-full text-xs text-left">
                        <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                          <tr>
                            <th className="p-2.5">Medication</th>
                            <th className="p-2.5">24h Dose</th>
                            <th className="p-2.5">Role</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 bg-white">
                          <tr>
                            <td className="p-2.5 font-bold text-teal-800">Octreotide</td>
                            <td className="p-2.5 font-black">{octreotideDoseMcg} mcg</td>
                            <td className="p-2.5 text-slate-500">GI Secretion Reduction</td>
                          </tr>
                          <tr>
                            <td className="p-2.5 font-bold text-sky-800">Hyoscine Butylbromide</td>
                            <td className="p-2.5 font-black">{buscopanDoseMg} mg</td>
                            <td className="p-2.5 text-slate-500">Colic Spasmolysis</td>
                          </tr>
                          <tr>
                            <td className="p-2.5 font-bold text-amber-800">Haloperidol</td>
                            <td className="p-2.5 font-black">{haloperidolDoseMg} mg</td>
                            <td className="p-2.5 text-slate-500">CTZ Antiemetic</td>
                          </tr>
                          <tr>
                            <td className="p-2.5 font-bold text-rose-800">Morphine Sulfate</td>
                            <td className="p-2.5 font-black">{morphineDoseMg} mg</td>
                            <td className="p-2.5 text-slate-500">Visceral Tumor Pain</td>
                          </tr>
                          <tr className="bg-slate-50 font-bold text-slate-800">
                            <td className="p-2.5">Diluent (Water for Inj.)</td>
                            <td className="p-2.5">To 48 mL total</td>
                            <td className="p-2.5 text-slate-600">Rate: 2.0 mL / hr SC</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>

                    {/* Adjuvant Dexamethasone Card */}
                    <div className="mt-4 p-3.5 bg-purple-50/70 border border-purple-200 rounded-xl space-y-2">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-bold text-purple-950">5. Dexamethasone (Anti-Edema Adjuvant):</span>
                        <span className="font-black text-purple-900">{dexamethasoneDoseMg} mg SC/IV daily</span>
                      </div>
                      <div className="flex gap-2">
                        {[8, 12, 16].map((dose) => (
                          <button
                            key={dose}
                            onClick={() => setDexamethasoneDoseMg(dose)}
                            className={`px-3 py-1 rounded text-xs font-bold transition ${
                              dexamethasoneDoseMg === dose
                                ? 'bg-purple-700 text-white shadow-2xs'
                                : 'bg-white border border-purple-200 text-purple-800 hover:bg-purple-100'
                            }`}
                          >
                            {dose} mg / day
                          </button>
                        ))}
                      </div>
                      <p className="text-[11px] text-purple-900 leading-tight">
                        Administer as separate morning SC/IV bolus (do not mix in syringe driver due to precipitation risks). Reduces peritumoral inflammatory edema, resolving partial obstruction in up to 60% of cases within 3–5 days.
                      </p>
                    </div>
                  </div>

                  {/* Compatibility Badge */}
                  <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-xs text-emerald-900 flex items-center gap-2">
                    <IconCheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
                    <span><b>Physicochemical Stability Verified:</b> Octreotide + Buscopan + Haloperidol + Morphine in Water for Injections is documented stable for 24 hours at 25°C.</span>
                  </div>
                </div>

              </div>

            </div>
          )}

          {/* TAB 4: VENTING DECOMPRESSION & MOUTH CARE */}
          {activeTab === 'venting_comfort' && (
            <div className="space-y-5">
              
              <div className="p-4 rounded-xl bg-gradient-to-r from-teal-50 to-emerald-50 border border-teal-200">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-teal-900 text-sm">Venting Decompression & Mouth Hydration Protocols</span>
                  <span className="px-2 py-0.5 bg-teal-200 text-teal-800 rounded text-[10px] font-bold">द्विभाषी गाइड</span>
                </div>
                <p className="text-xs text-teal-950">
                  When medical management cannot fully decompress proximal distension, venting relieves violent regurgitation. Support family through shifting goals from nutritional feeding to oral comfort and dignity.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* Venting Options */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">1. Mechanical Decompression Pathways</span>
                    <span className="text-[10px] font-semibold text-slate-500">NGT vs. Venting PEG</span>
                  </div>

                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Short-Term Nasogastric Tube (NGT):</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Indicated for acute intractable vomiting crisis. Aim to remove within 48–72 hours once Octreotide suppresses gastric secretions, as long-term NGT causes alar necrosis, sinusitis, and profound discomfort.
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Venting Gastrostomy (PEG):</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Gold-standard for prolonged home palliative management of proximal MBO. Connects to gravity drainage bag; allows patient to sip tea, coffee, or broth for pleasure, which vents harmlessly into the drainage bag.
                      </p>
                    </div>
                  </div>
                </div>

                {/* Mouth Care & Pleasure Feeds */}
                <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                  <div className="flex items-center justify-between border-b pb-2">
                    <span className="font-bold text-slate-900 text-xs uppercase">2. Eating for Pleasure & Thirst Relief</span>
                    <span className="text-[10px] font-semibold text-slate-500">Mouth Comfort Protocol</span>
                  </div>

                  <div className="space-y-2 text-xs text-slate-700">
                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Mouth Care Q2H:</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Clean dry tongue and buccal mucosa with soft foam swabs soaked in chilled water or diluted pineapple juice (natural enzymes dissolve thick mucus). Apply lip balm or ghee to prevent painful cracking.
                      </p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                      <div className="font-bold text-slate-800">Pleasure Sips (Not Nutrition):</div>
                      <p className="text-[11px] text-slate-600 mt-0.5">
                        Ice chips, frozen fruit slushies, or single teaspoon sips of favorite drinks. Thirst sensation originates from dry oral mucosa, not total body hydration, and is relieved by local moistening.
                      </p>
                    </div>
                  </div>
                </div>

              </div>

              {/* Bilingual Counseling Section */}
              <div className="p-4 rounded-xl bg-white border border-slate-200 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <IconUsers className="w-4 h-4 text-teal-700" />
                  <h4 className="text-xs font-bold text-slate-900 uppercase">
                    Bilingual Family Counseling Scripts for MBO (परिवार के लिए मार्गदर्शन)
                  </h4>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 space-y-1">
                    <div className="font-bold text-slate-800">1. Why We Must Avoid Forcing Food (भोजन न ठूंसने का कारण):</div>
                    <p className="text-[11px] text-slate-700 italic">
                      &quot;आंतों में रुकावट की वजह से खाना आगे नहीं जा सकता। अगर हम मरीज को जबरदस्ती खाना या भारी तरल पदार्थ देंगे, तो वह पेट में सड़कर उल्टी बनकर बाहर आएगा और फेफड़ों में जाने का खतरा बढ़ जाएगा। मरीज भूखा नहीं है, यह बीमारी का असर है।&quot;
                    </p>
                  </div>

                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 space-y-1">
                    <div className="font-bold text-slate-800">2. Why Excessive IV Drips Worsen Suffering (अधिक ड्रिप लगाने का नुकसान):</div>
                    <p className="text-[11px] text-slate-700 italic">
                      &quot;नसों से बहुत ज्यादा पानी (ड्रिप) चढ़ाने से रुकावट के ऊपर पानी और तेजी से भरेगा, जिससे उल्टी और पेट फूलने का दर्द और बढ़ जाएगा। हमारी दवाई (ऑक्ट्रियोटाइड) पेट के रस को सुखाकर मरीज को उल्टी और ऐंठन से तुरंत राहत देती है।&quot;
                    </p>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* TAB 5: GI SYMPTOM TRAJECTORY LOG */}
          {activeTab === 'history' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">MBO Episode & Decompression Log</h3>
                  <p className="text-xs text-slate-500">Kidwai Palliative Oncology Surgical Ward</p>
                </div>
                <span className="px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg text-xs font-bold border">
                  3 Prior Evaluations
                </span>
              </div>

              <div className="border border-slate-200 rounded-xl overflow-hidden bg-white">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                    <tr>
                      <th className="p-3">Date / Time</th>
                      <th className="p-3">Level</th>
                      <th className="p-3">Surgical Score</th>
                      <th className="p-3">Vomiting Episodes</th>
                      <th className="p-3">Quad Regimen Prescribed</th>
                      <th className="p-3">Attending Physician</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr className="bg-teal-50/40">
                      <td className="p-3 font-bold text-slate-900">Today (Current)</td>
                      <td className="p-3 font-bold text-teal-800">{levelMetadata[obstructionLevel].name}</td>
                      <td className="p-3 font-black text-rose-700">{totalSurgicalRiskScore} / 9 (Futile)</td>
                      <td className="p-3 text-amber-800 font-semibold">4x / day ({vomitCharacter})</td>
                      <td className="p-3 font-semibold text-slate-800">Octreotide {octreotideDoseMcg}mcg + Buscopan {buscopanDoseMg}mg SC</td>
                      <td className="p-3 text-slate-600">Dr Sujay (Palliative Lead)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">02 Sep 2026</td>
                      <td className="p-3">Small Bowel (Ileal)</td>
                      <td className="p-3 font-bold">5 / 9</td>
                      <td className="p-3 text-slate-700">6x / day (Bilious)</td>
                      <td className="p-3 text-slate-700">Octreotide 300mcg + Haloperidol 2.5mg</td>
                      <td className="p-3 text-slate-600">Dr Ananya (Surgical Oncology)</td>
                    </tr>
                    <tr>
                      <td className="p-3 text-slate-600">18 Aug 2026</td>
                      <td className="p-3">Partial Subacute Ileus</td>
                      <td className="p-3 font-bold text-emerald-700">2 / 9</td>
                      <td className="p-3 text-slate-700">1x / day</td>
                      <td className="p-3 text-slate-700">Dexamethasone 8mg PO only</td>
                      <td className="p-3 text-slate-600">Dr Sujay (Palliative Lead)</td>
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
              {copiedToast ? '✓ Copied MBO Protocol!' : '📋 Copy Complete MBO Protocol'}
            </button>
            <button
              onClick={handleDispatchPlan}
              className="px-4 py-2 bg-teal-700 hover:bg-teal-800 text-white rounded-lg text-xs font-bold transition shadow-xs flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-3.5 h-3.5" />
              <span>{dispatchedToast ? '✓ Dispatched MBO Syringe Orders!' : '🧪 Dispatch STAT MBO Quad Orders & ASHA Alert'}</span>
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
