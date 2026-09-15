'use client';

import React, { useState, useMemo } from 'react';
import {
  IconUsers,
  IconHeartPulse,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconSparkles,
  IconClock,
  IconHospital,
} from './icons';

export interface ZaritQuestion {
  id: number;
  question: string;
  hindiQuestion: string;
  domain: 'time' | 'health' | 'emotional' | 'social';
}

export const ZARIT_12_QUESTIONS: ZaritQuestion[] = [
  {
    id: 1,
    question: 'Do you feel you do not have enough time for yourself because of your relative?',
    hindiQuestion: 'क्या आपको लगता है कि मरीज की देखभाल के कारण आपके पास अपने लिए समय नहीं है?',
    domain: 'time',
  },
  {
    id: 2,
    question: 'Do you feel stressed between caring for your relative and meeting other family or work responsibilities?',
    hindiQuestion: 'क्या आप देखभाल और काम/परिवार की अन्य जिम्मेदारियों के बीच तनाव महसूस करते हैं?',
    domain: 'time',
  },
  {
    id: 3,
    question: 'Do you feel angry or irritable when you are around your relative?',
    hindiQuestion: 'क्या मरीज के साथ रहते हुए आपको कभी गुस्सा या चिड़चिड़ापन महसूस होता है?',
    domain: 'emotional',
  },
  {
    id: 4,
    question: 'Do you feel that your relative currently affects your relationships with other family members negatively?',
    hindiQuestion: 'क्या मरीज की बीमारी का असर आपके परिवार के अन्य सदस्यों के साथ संबंधों पर पड़ रहा है?',
    domain: 'social',
  },
  {
    id: 5,
    question: 'Do you feel strained when you are caring for your relative?',
    hindiQuestion: 'क्या मरीज की देखभाल करते समय आप अत्यधिक मानसिक या शारीरिक खिंचाव महसूस करते हैं?',
    domain: 'emotional',
  },
  {
    id: 6,
    question: 'Do you feel your physical or mental health has suffered because of your caregiving involvement?',
    hindiQuestion: 'क्या आपको लगता है कि देखभाल के कारण आपका अपना स्वास्थ्य खराब हो गया है?',
    domain: 'health',
  },
  {
    id: 7,
    question: 'Do you feel that you do not have as much privacy as you would like?',
    hindiQuestion: 'क्या आपको लगता है कि आपके पास अपनी निजी जिंदगी या निजता के लिए जगह नहीं बची है?',
    domain: 'time',
  },
  {
    id: 8,
    question: 'Do you feel that your social life or community engagement has suffered?',
    hindiQuestion: 'क्या देखभाल की वजह से आपकी सामाजिक जिंदगी और मेलजोल प्रभावित हुआ है?',
    domain: 'social',
  },
  {
    id: 9,
    question: 'Do you feel you have lost control of your life since your relative’s cancer diagnosis?',
    hindiQuestion: 'क्या आपको लगता है कि मरीज की बीमारी के बाद से आपने अपनी जिंदगी पर से नियंत्रण खो दिया है?',
    domain: 'emotional',
  },
  {
    id: 10,
    question: 'Do you feel uncertain or fearful about what to do next about your relative?',
    hindiQuestion: 'क्या आप मरीज की आगे की स्थिति या आपातकाल को लेकर अनिश्चित और भयभीत महसूस करते हैं?',
    domain: 'emotional',
  },
  {
    id: 11,
    question: 'Do you feel you should be doing more for your relative than you are currently able to?',
    hindiQuestion: 'क्या आपको लगता है कि आपको मरीज के लिए जितना कर रहे हैं उससे कहीं अधिक करना चाहिए?',
    domain: 'emotional',
  },
  {
    id: 12,
    question: 'Do you feel you could do a better job in caring for your relative?',
    hindiQuestion: 'क्या आपको अक्सर अपराधबोध (guilt) होता है कि आप और बेहतर देखभाल कर सकते थे?',
    domain: 'emotional',
  },
];

export const LIKERT_OPTIONS = [
  { value: 0, label: 'Never', hindi: 'कभी नहीं' },
  { value: 1, label: 'Rarely', hindi: 'शायद ही कभी' },
  { value: 2, label: 'Sometimes', hindi: 'कभी-कभी' },
  { value: 3, label: 'Quite Frequently', hindi: 'काफी बार' },
  { value: 4, label: 'Nearly Always', hindi: 'लगभग हमेशा' },
];

export interface CaregiverBurdenProps {
  patientId: string;
  patientName: string;
  caregiverName: string;
  caregiverRelation: string;
  caregiverPhone: string;
  onClose?: () => void;
  onDispatchRespite?: (caregiver: string, urgency: string) => void;
  onSavePlan?: (totalScore: number, tier: string) => void;
}

export function CaregiverBurdenMatrix({
  patientId,
  patientName,
  caregiverName,
  caregiverRelation,
  caregiverPhone,
  onClose,
  onDispatchRespite,
  onSavePlan,
}: CaregiverBurdenProps) {
  // 12 question scores (default realistic baseline)
  const [answers, setAnswers] = useState<Record<number, number>>({
    1: 3,
    2: 3,
    3: 1,
    4: 2,
    5: 3,
    6: 2,
    7: 3,
    8: 3,
    9: 2,
    10: 3,
    11: 3,
    12: 2,
  });

  const [activeLang, setActiveLang] = useState<'en' | 'hi'>('en');
  const [nightRespiteRequested, setNightRespiteRequested] = useState<boolean>(true);
  const [psychosocialCounseling, setPsychosocialCounseling] = useState<boolean>(true);
  const [homeDeathProtocolReviewed, setHomeDeathProtocolReviewed] = useState<boolean>(false);
  const [copiedToast, setCopiedToast] = useState<boolean>(false);
  const [respiteToast, setRespiteToast] = useState<string | null>(null);

  const handleScoreChange = (qId: number, val: number) => {
    setAnswers((prev) => ({ ...prev, [qId]: val }));
  };

  // Total ZBI-12 score (0 to 48)
  const totalScore = useMemo(() => {
    return Object.values(answers).reduce((acc, curr) => acc + curr, 0);
  }, [answers]);

  // Risk Stratification
  const burdenStratification = useMemo(() => {
    if (totalScore >= 21) {
      return {
        level: 'Severe Burden (Crisis Alert)',
        badgeClass: 'bg-rose-100 text-rose-900 border-rose-300',
        textColor: 'text-rose-600',
        summary: 'High risk of acute caregiver collapse, psychological burnout, and non-concordant emergency hospital readmission.',
        urgency: 'Immediate In-Person Respite Required',
        recommendedActions: [
          'Dispatch Community ASHA / Palliative Nurse for 4-hour bedside relief',
          'Urgent referral to Oncology Medical Social Work for counseling & volunteer network',
          'Activate 24/7 Hospice Emergency Direct Line for home crisis prevention',
          'Review Home Death & Anticipatory Grief checklist with primary surrogate',
        ],
      };
    } else if (totalScore >= 11) {
      return {
        level: 'Moderate Burden (Strain Emerging)',
        badgeClass: 'bg-amber-100 text-amber-900 border-amber-300',
        textColor: 'text-amber-600',
        summary: 'Moderate strain balancing physical caregiving, domestic duties, and emotional uncertainty.',
        urgency: 'Weekly Community Respite & Support Plan',
        recommendedActions: [
          'Schedule bi-weekly ASHA home respite visits (Lakshmi Devi)',
          'Provide hands-on caregiver training for syringe driver & subcutaneous injections',
          'Coordinate extended family member schedule rotation',
          'Introduce palliative caregiver support group contacts',
        ],
      };
    } else {
      return {
        level: 'Mild / Sustainable Burden',
        badgeClass: 'bg-emerald-100 text-emerald-900 border-emerald-300',
        textColor: 'text-emerald-600',
        summary: 'Caregiver reports manageable strain; coping mechanisms and support circle currently adequate.',
        urgency: 'Routine Monitoring',
        recommendedActions: [
          'Maintain regular telephone check-ins every 2 weeks',
          'Re-administer ZBI-12 upon any functional decline (PPS drop) or disease transition',
          'Reiterate 24/7 helpline availability',
        ],
      };
    }
  }, [totalScore]);

  // Domain score aggregates
  const domainScores = useMemo(() => {
    let time = 0;
    let health = 0;
    let emotional = 0;
    let social = 0;

    ZARIT_12_QUESTIONS.forEach((q) => {
      const val = answers[q.id] || 0;
      if (q.domain === 'time') time += val;
      if (q.domain === 'health') health += val;
      if (q.domain === 'emotional') emotional += val;
      if (q.domain === 'social') social += val;
    });

    return {
      time: Math.round((time / 12) * 100), // 3 questions = max 12
      health: Math.round((health / 4) * 100), // 1 question = max 4
      emotional: Math.round((emotional / 24) * 100), // 6 questions = max 24
      social: Math.round((social / 8) * 100), // 2 questions = max 8
    };
  }, [answers]);

  const handleCopySupportPlan = () => {
    const planText = `[CONTINUITY LOOP: CAREGIVER BURDEN ASSESSMENT & RESPITE PLAN]
Patient: ${patientName} (${patientId})
Primary Caregiver: ${caregiverName} (${caregiverRelation})
Contact: ${caregiverPhone}
Assessment Date: ${new Date().toLocaleDateString('en-GB')}

ZBI-12 Total Score: ${totalScore} / 48
Stratification: ${burdenStratification.level}
Domain Strain Breakdown:
- Time-Dependence Strain: ${domainScores.time}%
- Physical/Health Toll: ${domainScores.health}%
- Emotional/Guilt Strain: ${domainScores.emotional}%
- Social Isolation: ${domainScores.social}%

Active Caregiver Directives:
• Night Respite Nurse Requested: ${nightRespiteRequested ? 'YES' : 'NO'}
• Psychosocial Counseling Referral: ${psychosocialCounseling ? 'ACTIVE' : 'NONE'}
• Home Death Verification Protocol: ${homeDeathProtocolReviewed ? 'REVIEWED & UNDERSTOOD' : 'PENDING REVIEW'}

Action Bundle:
${burdenStratification.recommendedActions.map((a) => `• ${a}`).join('\n')}

Assessing Coordinator: Anitha Rao · Care Coordinator`;

    navigator.clipboard.writeText(planText).then(() => {
      setCopiedToast(true);
      setTimeout(() => setCopiedToast(false), 3000);
    });
  };

  const handleTriggerRespite = () => {
    if (onDispatchRespite) {
      onDispatchRespite(caregiverName, burdenStratification.level);
    }
    setRespiteToast(`ASHA Palliative Respite Nurse Dispatched for ${caregiverName}`);
    setTimeout(() => setRespiteToast(null), 4000);
  };

  return (
    <div className="tep-overlay" role="dialog" aria-modal="true">
      <div className="tep-modal prog-modal-wide" style={{ maxWidth: '1100px' }}>
        {/* Header */}
        <div className="tep-header">
          <div className="tep-header-title">
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-teal-600 text-white font-black text-xs">
                CAREGIVER CARE
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Zarit Burden Scale (ZBI-12) & Palliative Respite Engine
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Screening caregiver burnout, anticipatory grief, and dispatching home respite to prevent crisis hospital readmissions
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Caregiver Assessment"
          >
            ✕
          </button>
        </div>

        {/* Caregiver & Language Header Ribbon */}
        <div className="px-6 py-3 bg-teal-50/70 border-b border-teal-200 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-4">
            <div>
              <span className="text-[10px] font-bold text-teal-600 uppercase tracking-wider block">Caregiver Contact</span>
              <span className="font-bold text-slate-900 text-sm">
                {caregiverName} ({caregiverRelation}) · <span className="font-mono text-slate-600">{caregiverPhone}</span>
              </span>
            </div>
            <div className="h-6 w-px bg-teal-200" />
            <div>
              <span className="text-[10px] font-bold text-teal-600 uppercase tracking-wider block">Care Recipient</span>
              <span className="font-semibold text-slate-800">{patientName} ({patientId})</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500 font-semibold">Language:</span>
            <div className="flex bg-white p-0.5 rounded-lg border border-teal-200 text-xs">
              <button
                onClick={() => setActiveLang('en')}
                className={`px-2.5 py-1 rounded font-bold transition ${
                  activeLang === 'en' ? 'bg-teal-700 text-white' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                English
              </button>
              <button
                onClick={() => setActiveLang('hi')}
                className={`px-2.5 py-1 rounded font-bold transition ${
                  activeLang === 'hi' ? 'bg-teal-700 text-white' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                🇮🇳 हिंदी
              </button>
            </div>
          </div>
        </div>

        {/* Main 2-Column Body */}
        <div className="p-6 overflow-y-auto max-h-[72vh] space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: 12 Questions (7 cols) */}
            <div className="lg:col-span-7 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                  <IconUsers className="w-4 h-4 text-teal-600" />
                  <span>ZBI-12 Assessment Inventory</span>
                </h3>
                <span className="text-xs text-slate-400">12 Validated Items (0-4 pt scale)</span>
              </div>

              <div className="space-y-3">
                {ZARIT_12_QUESTIONS.map((q) => {
                  const currentVal = answers[q.id] || 0;
                  return (
                    <div
                      key={q.id}
                      className="p-3 bg-white rounded-xl border border-slate-200 shadow-xs space-y-2 hover:border-teal-200 transition"
                    >
                      <div className="text-xs font-semibold text-slate-800 leading-snug">
                        <span className="text-teal-600 font-bold mr-1.5">Q{q.id}.</span>
                        {activeLang === 'hi' ? q.hindiQuestion : q.question}
                      </div>

                      {/* Likert Buttons */}
                      <div className="grid grid-cols-5 gap-1 pt-1">
                        {LIKERT_OPTIONS.map((opt) => (
                          <button
                            key={opt.value}
                            type="button"
                            onClick={() => handleScoreChange(q.id, opt.value)}
                            className={`py-1.5 px-1 rounded text-[11px] font-bold text-center transition flex flex-col items-center justify-center border ${
                              currentVal === opt.value
                                ? 'bg-teal-700 text-white border-teal-700 shadow-xs ring-1 ring-teal-500'
                                : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border-slate-200'
                            }`}
                          >
                            <span className="text-xs">{opt.value}</span>
                            <span className="text-[9.5px] truncate w-full">
                              {activeLang === 'hi' ? opt.hindi : opt.label}
                            </span>
                          </button>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Right Column: Score Radar, Stratification & Respite Dispatch (5 cols) */}
            <div className="lg:col-span-5 space-y-5">
              {/* Score & Risk Card */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                    Caregiver Burden Index
                  </span>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-black border ${burdenStratification.badgeClass}`}>
                    {burdenStratification.level}
                  </span>
                </div>

                <div className="flex items-baseline justify-between p-3 bg-slate-50 rounded-xl border border-slate-100">
                  <div>
                    <span className="text-3xl font-black text-slate-900">{totalScore}</span>
                    <span className="text-slate-400 font-bold text-sm"> / 48</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">ZBI-12 Total Burden Score</span>
                  </div>
                  <div className="text-right">
                    <span className={`text-xs font-extrabold ${burdenStratification.textColor}`}>
                      {burdenStratification.urgency}
                    </span>
                    <span className="block text-[10px] text-slate-400">Cut-off: &ge;21 High Risk</span>
                  </div>
                </div>

                {/* Domain Strain Progress Bars */}
                <div className="space-y-2.5 text-xs">
                  <span className="font-bold text-slate-700 block text-[11px] uppercase tracking-wider">
                    Burden Sub-Domain Breakdown
                  </span>

                  <div>
                    <div className="flex justify-between text-[11px] mb-1 font-medium">
                      <span>Time-Dependence Strain</span>
                      <span className="font-bold text-slate-800">{domainScores.time}%</span>
                    </div>
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-indigo-500 rounded-full" style={{ width: `${domainScores.time}%` }} />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-[11px] mb-1 font-medium">
                      <span>Physical & Health Exhaustion</span>
                      <span className="font-bold text-slate-800">{domainScores.health}%</span>
                    </div>
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-rose-500 rounded-full" style={{ width: `${domainScores.health}%` }} />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-[11px] mb-1 font-medium">
                      <span>Emotional Guilt & Uncertainty</span>
                      <span className="font-bold text-slate-800">{domainScores.emotional}%</span>
                    </div>
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-amber-500 rounded-full" style={{ width: `${domainScores.emotional}%` }} />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-[11px] mb-1 font-medium">
                      <span>Social & Relationship Isolation</span>
                      <span className="font-bold text-slate-800">{domainScores.social}%</span>
                    </div>
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-teal-500 rounded-full" style={{ width: `${domainScores.social}%` }} />
                    </div>
                  </div>
                </div>
              </div>

              {/* Home Death Preparedness & Anticipatory Grief Checklist */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
                  <IconSparkles className="w-4 h-4 text-teal-600" />
                  <span>Home Palliative Care & Respite Directives</span>
                </div>

                <div className="space-y-2 text-xs">
                  <label className="flex items-start gap-2.5 p-2 rounded-lg bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={nightRespiteRequested}
                      onChange={(e) => setNightRespiteRequested(e.target.checked)}
                      className="accent-teal-600 mt-0.5"
                    />
                    <div>
                      <span className="font-bold text-slate-900 block">Night Respite Nurse Coverage</span>
                      <span className="text-[11px] text-slate-500">Provide 8h overnight caregiver sleep relief via Karunashraya outreach pool</span>
                    </div>
                  </label>

                  <label className="flex items-start gap-2.5 p-2 rounded-lg bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={psychosocialCounseling}
                      onChange={(e) => setPsychosocialCounseling(e.target.checked)}
                      className="accent-teal-600 mt-0.5"
                    />
                    <div>
                      <span className="font-bold text-slate-900 block">Anticipatory Grief Counseling</span>
                      <span className="text-[11px] text-slate-500">Refer caregiver to oncology psycho-oncologist / palliative social worker</span>
                    </div>
                  </label>

                  <label className="flex items-start gap-2.5 p-2 rounded-lg bg-slate-50 border border-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={homeDeathProtocolReviewed}
                      onChange={(e) => setHomeDeathProtocolReviewed(e.target.checked)}
                      className="accent-teal-600 mt-0.5"
                    />
                    <div>
                      <span className="font-bold text-slate-900 block">Expected Death Verification Protocol</span>
                      <span className="text-[11px] text-slate-500">Family instructed to call ASHA/Palliative Doctor instead of 108/police to issue Form 4 MCCD</span>
                    </div>
                  </label>
                </div>
              </div>

              {/* 1-Click Community Respite Dispatch Card */}
              <div className="p-4 bg-teal-900 rounded-xl text-white space-y-3 shadow-md">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-teal-300">
                    Community Respite Dispatch
                  </span>
                  <span className="text-[10px] bg-teal-800 px-2 py-0.5 rounded text-teal-200 font-mono">
                    PHC Outreach
                  </span>
                </div>

                <p className="text-xs text-teal-100 leading-relaxed">
                  Dispatches designated ASHA worker (Lakshmi Devi) or community nurse to the residence for in-person caregiver relief.
                </p>

                <button
                  type="button"
                  onClick={handleTriggerRespite}
                  className="w-full py-2.5 bg-teal-500 hover:bg-teal-400 text-teal-950 rounded-lg text-xs font-black transition flex items-center justify-center gap-2 shadow-sm"
                >
                  <span>🤝</span>
                  <span>Dispatch Community Respite Nurse Now</span>
                </button>
                {respiteToast && (
                  <div className="p-2 bg-teal-800/90 border border-teal-400 text-teal-100 text-xs rounded text-center font-bold">
                    ✓ {respiteToast}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopySupportPlan}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
            >
              {copiedToast ? '✓ Copied Caregiver Plan!' : '📋 Copy Caregiver SBAR Support Plan'}
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
                if (onSavePlan) {
                  onSavePlan(totalScore, burdenStratification.level);
                }
                if (onClose) onClose();
              }}
              className="px-5 py-2 bg-teal-700 hover:bg-teal-800 text-white rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>Record Caregiver Plan</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
