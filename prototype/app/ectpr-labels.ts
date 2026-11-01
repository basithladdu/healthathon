// Display labels for one ECTPR record, shared by the ED Quick View, the public /ed page, the A4 PDF and the
// wallet card. Records store enums (tech spec §5); this file turns them into words in the reader's language.
// English is the source; Hindi is provided inline; other languages fall back through care-i18n dictionaries.
import { careText, type CareLang } from './care-i18n.ts';
import { ALWAYS_PROVIDED_CARE, IF_IN_DOUBT_RESUSCITATE, TRANSFER_REASON_LABELS, consultantSignature, type EdInterpretation } from './ectpr-rules.ts';
import type {
  AmbulanceOption, AmdStatusSummary, AuthorityBasis, Dialysis, EctprRecord, ExistingDirective, GoalOfCare, HospitalTransfer, Hydration,
  Icu, ImplantedDevice, NgFeeding, PegFeeding, RespCeiling, TransferReason, TrialPlan, Vasopressors, WithdrawalFinal,
} from './ectpr-model.ts';

type T = (en: string, hi: string) => string;
export const textFor = (lang: CareLang): T => (en, hi) => careText(lang, en, hi);

export function noPreference(lang: CareLang): string {
  return careText(lang, 'No preference recorded', 'कोई पसंद दर्ज नहीं');
}
export function noPreferenceStandardCare(lang: CareLang): string {
  return careText(lang, 'No preference recorded — give standard care', 'कोई पसंद दर्ज नहीं — सामान्य इलाज दें');
}
export function notRecorded(lang: CareLang): string {
  return careText(lang, 'Not recorded', 'दर्ज नहीं');
}

const ALWAYS_HI: Record<string, string> = {
  'Mouth care and lip care, to keep the mouth moist and clean.': 'मुँह और होंठों की देखभाल, ताकि मुँह नम और साफ़ रहे।',
  'Food and drink by mouth, given by hand for comfort, as much as the person wants and can safely swallow.': 'आराम के लिए हाथ से मुँह द्वारा खाना-पानी, जितना व्यक्ति चाहे और सुरक्षित रूप से निगल सके।',
  'Relief of pain, breathlessness and other distressing symptoms, at any time of day or night.': 'दर्द, साँस फूलने और अन्य परेशान करने वाले लक्षणों से राहत, दिन या रात किसी भी समय।',
  'Washing, hygiene, skin care and pressure-area care.': 'नहलाना, साफ़-सफ़ाई, त्वचा और दबाव वाली जगहों की देखभाल।',
  'Turning and positioning for comfort.': 'आराम के लिए करवट बदलना और सही स्थिति में रखना।',
  'Family and loved ones may stay with the person.': 'परिवार और प्रियजन व्यक्ति के साथ रह सकते हैं।',
  'Spiritual, emotional and psychological support for the person and family.': 'व्यक्ति और परिवार के लिए आध्यात्मिक, भावनात्मक और मानसिक सहारा।',
  'Dignity, privacy and kindness in all care.': 'हर देखभाल में गरिमा, निजता और दया।',
};
const IF_IN_DOUBT_HI: Record<string, string> = {
  'The record is unsigned, out of date, or cannot be found.': 'रिकॉर्ड पर हस्ताक्षर नहीं है, वह पुराना है, या मिल नहीं रहा।',
  'The problem is new, reversible and not caused by the main illness — for example injury, choking, a drug reaction or low blood sugar.': 'समस्या नई है, ठीक हो सकती है और मुख्य बीमारी से नहीं है — जैसे चोट, गले में अटकना, दवा का असर या कम शुगर।',
  'The patient can decide for themselves and now says something different.': 'मरीज़ खुद निर्णय ले सकता है और अब कुछ अलग कह रहा है।',
  'The record is unclear.': 'रिकॉर्ड साफ़ नहीं है।',
};

export function alwaysProvidedCare(lang: CareLang): string[] {
  return ALWAYS_PROVIDED_CARE.map((en) => careText(lang, en, ALWAYS_HI[en]));
}
export function ifInDoubtConditions(lang: CareLang): string[] {
  return IF_IN_DOUBT_RESUSCITATE.map((en) => careText(lang, en, IF_IN_DOUBT_HI[en]));
}
export function ifInDoubtTitle(lang: CareLang): string {
  return careText(lang, 'IF IN DOUBT, RESUSCITATE', 'संदेह हो तो पुनर्जीवन (CPR) दें');
}
export function ifInDoubtLead(lang: CareLang): string {
  return careText(lang, 'Give full standard emergency care when any of these is true:', 'इनमें से कोई भी बात सही हो तो पूरा सामान्य आपातकालीन इलाज दें:');
}
export function ifInDoubtAfter(lang: CareLang): string {
  return careText(lang, 'Clarify with the treating team afterwards.', 'बाद में इलाज करने वाली टीम से स्पष्ट करें।');
}

export function walletCardLine(lang: CareLang): string {
  return careText(lang, 'If unclear or the problem is new and reversible — treat fully and call.', 'अगर साफ़ न हो या समस्या नई और ठीक होने वाली हो — पूरा इलाज करें और फ़ोन करें।');
}

export function dyingPhasePrompt(lang: CareLang): string {
  return careText(
    lang,
    'Signs of dying (falling blood pressure, cold mottled hands and feet, jaw breathing, Cheyne–Stokes breathing, little urine) mean this plan needs urgent review. If the signs are unclear, get a second opinion — wrongly deciding someone is dying can make it come true.',
    'मृत्यु के लक्षण (गिरता BP, ठंडे धब्बेदार हाथ-पैर, जबड़े से साँस, Cheyne–Stokes साँस, कम पेशाब) दिखें तो इस योजना की तुरंत समीक्षा करें। लक्षण साफ़ न हों तो दूसरी राय लें — गलती से "अंतिम समय" मान लेना उसे सच बना सकता है।',
  );
}

export function amdCaveat(lang: CareLang): string {
  return careText(
    lang,
    'A living will applies only to the situation it describes. If the situation is ambiguous or was not anticipated, it is not applied — give standard care and contact the decision-maker. Where a valid living will exists, it governs over this plan.',
    'लिविंग विल केवल उसी स्थिति पर लागू होती है जिसका उसमें वर्णन है। स्थिति अस्पष्ट हो या पहले से सोची न गई हो, तो वह लागू नहीं होती — सामान्य इलाज दें और निर्णयकर्ता से संपर्क करें। वैध लिविंग विल होने पर वही इस योजना से ऊपर मानी जाएगी।',
  );
}

// ---------------------------------------------------------------------------
// Banner by ED state (rules give the English sentence; Hindi here)

export function edBanner(interp: EdInterpretation, record: EctprRecord | null, lang: CareLang): string {
  if (lang === 'en') return interp.banner;
  const hi: Record<EdInterpretation['state'], string> = {
    NONE: 'कोई हस्ताक्षरित आपातकालीन योजना नहीं मिली। पूरा सामान्य आपातकालीन इलाज दें। संदेह हो तो पुनर्जीवन दें।',
    REVOKED: 'कार्ड वापस ले लिया गया — यह योजना रद्द की गई है। सामान्य आपातकालीन इलाज दें।',
    UNSIGNED: 'हस्ताक्षर नहीं — यह बातचीत का नोट है, योजना नहीं। केवल जानकारी के लिए। सामान्य आपातकालीन इलाज दें।',
    OUT_OF_DATE: 'पुरानी योजना — समीक्षा की तारीख निकल गई या नया संस्करण है। यह जानकारी है, निर्देश नहीं। सामान्य इलाज लागू है। इलाज करने वाली टीम से संपर्क करें।',
    ACTIVE: interp.standardCareApplies
      ? `हस्ताक्षरित योजना, संस्करण ${record?.version ?? ''}, पर किसी भाग में कोई पसंद दर्ज नहीं। सामान्य इलाज दें।`
      : `हस्ताक्षरित योजना, संस्करण ${record?.version ?? ''}। जिस भाग में "कोई पसंद दर्ज नहीं" लिखा है, वहाँ सामान्य इलाज दें।`,
  };
  return careText(lang, interp.banner, hi[interp.state]);
}

export function edFlag(flag: string, lang: CareLang): string {
  const hi: Record<string, string> = {
    'Capacity fluctuates — reassess before acting on any section': 'निर्णय क्षमता बदलती रहती है — किसी भी भाग पर अमल से पहले दोबारा जाँचें',
    'Removing the existing feeding tube needs the medical board process — not from this record alone.': 'लगी हुई फ़ीडिंग ट्यूब हटाने के लिए मेडिकल बोर्ड प्रक्रिया ज़रूरी है — केवल इस रिकॉर्ड से नहीं।',
  };
  return careText(lang, flag, hi[flag]);
}

// ---------------------------------------------------------------------------
// Field labels

export function goalLabel(goal: GoalOfCare | null, lang: CareLang): string {
  const t = textFor(lang);
  if (goal === 'A_LIFE') return t('A · Prioritise life — all medically effective treatment, including ICU and ventilation', 'A · जीवन को प्राथमिकता — ICU और वेंटिलेटर सहित हर असरदार इलाज');
  if (goal === 'B_CEILING') return t('B · Balanced — treat reversible problems within the ceilings below, as time-limited trials', 'B · संतुलित — नीचे लिखी सीमाओं के भीतर ठीक होने वाली समस्याओं का समय-सीमित इलाज');
  if (goal === 'C_COMFORT') return t('C · Prioritise comfort — symptom control is the goal', 'C · आराम को प्राथमिकता — लक्षणों पर नियंत्रण ही लक्ष्य');
  return noPreferenceStandardCare(lang);
}
export function goalShort(goal: GoalOfCare | null, lang: CareLang): string {
  const t = textFor(lang);
  if (goal === 'A_LIFE') return t('A · Prioritise life', 'A · जीवन को प्राथमिकता');
  if (goal === 'B_CEILING') return t('B · Balanced / ceiling of care', 'B · संतुलित / इलाज की सीमा');
  if (goal === 'C_COMFORT') return t('C · Prioritise comfort', 'C · आराम को प्राथमिकता');
  return noPreference(lang);
}

export function transferReason(reason: TransferReason, lang: CareLang): string {
  const hi: Record<TransferReason, string> = {
    PAIN: 'काबू में न आने वाला दर्द', BREATHLESSNESS: 'काबू में न आने वाली साँस फूलना', BLEEDING: 'ज़्यादा खून बहना', SEIZURES: 'दौरे',
    FALL_FRACTURE: 'गिरना और हड्डी टूटने का शक', BLOCKED_TUBE: 'बंद या हिली हुई कैथेटर, ट्यूब या स्टेंट', INFECTION_IV: 'संक्रमण जिसमें ड्रिप (IV) दवा चाहिए',
    CONFUSION: 'अचानक भ्रम या बेचैनी जो शांत न हो', CAREGIVER_RESPITE: 'देखभाल करने वाला अब संभाल नहीं पा रहा (राहत)',
  };
  return careText(lang, TRANSFER_REASON_LABELS[reason], hi[reason]);
}

export function transferLabel(transfer: HospitalTransfer | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!transfer) return noPreferenceStandardCare(lang);
  if (transfer.kind === 'ANY') return t('Yes — for any significant deterioration', 'हाँ — किसी भी गंभीर बिगड़ने पर');
  if (transfer.kind === 'NO') return t('No — care where they are; call the numbers listed before an ambulance', 'नहीं — जहाँ हैं वहीं देखभाल; एम्बुलेंस से पहले दिए नंबरों पर फ़ोन करें');
  const reasons = transfer.reasons.map((r) => transferReason(r, lang)).join('; ');
  return `${t('Only for:', 'केवल इनके लिए:')} ${reasons || notRecorded(lang)}`;
}
export function transferShort(transfer: HospitalTransfer | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!transfer) return noPreference(lang);
  if (transfer.kind === 'ANY') return t('Yes, any deterioration', 'हाँ, किसी भी बिगड़ने पर');
  if (transfer.kind === 'NO') return t('No — call first', 'नहीं — पहले फ़ोन करें');
  return t('Only for listed reasons', 'केवल लिखे कारणों के लिए');
}

export function ambulanceLabel(option: AmbulanceOption | null, lang: CareLang): string {
  const t = textFor(lang);
  if (option === 'FULL_RESUSCITATION') return t('Full pre-hospital resuscitation and transport', 'अस्पताल से पहले पूरा पुनर्जीवन और ले जाना');
  if (option === 'SYMPTOM_RELIEF_TRANSPORT') return t('Transport with symptom relief only (O₂ for breathlessness, pain relief, positioning; no CPR, no intubation)', 'केवल लक्षण राहत के साथ ले जाना (साँस के लिए O₂, दर्द की दवा, स्थिति; CPR नहीं, ट्यूब नहीं)');
  if (option === 'TRANSPORT_HOME_NURSE_ESCORT') return t('Transport home for last days with a nurse escort', 'अंतिम दिनों के लिए नर्स के साथ घर ले जाना');
  return noPreferenceStandardCare(lang);
}

export function cprLabel(record: EctprRecord, lang: CareLang): string {
  const t = textFor(lang);
  const c = record.choices;
  if (c.cpr === 'ATTEMPT') return t('Attempt CPR', 'CPR करें');
  if (c.cpr === 'DNACPR') {
    const reason = c.dnacprReason === 'FUTILITY' ? t('clinical futility', 'इलाज बेअसर होगा')
      : c.dnacprReason === 'PATIENT_REFUSAL' ? t('patient refusal', 'मरीज़ ने मना किया')
      : c.dnacprReason === 'BURDEN_OUTWEIGHS_BENEFIT' ? t('burden outweighs benefit', 'नुकसान फ़ायदे से ज़्यादा') : '';
    return `${t('Do not attempt CPR (DNACPR)', 'CPR न करें (DNACPR)')}${reason ? ` — ${reason}` : ''}`;
  }
  return noPreferenceStandardCare(lang);
}
export function cprShort(record: EctprRecord, lang: CareLang): string {
  const t = textFor(lang);
  if (record.choices.cpr === 'ATTEMPT') return t('Attempt CPR', 'CPR करें');
  if (record.choices.cpr === 'DNACPR') return t('DNACPR — do not attempt', 'DNACPR — CPR न करें');
  return noPreference(lang);
}
export function cprDecidedBy(record: EctprRecord, lang: CareLang): string | null {
  const d = record.choices.dnacprDecidedBy;
  if (record.choices.cpr !== 'DNACPR' || !d) return null;
  const t = textFor(lang);
  const parts = [`${t('Decided by', 'निर्णय')} ${d.name}${d.registrationNo ? ` (${t('Reg.', 'पंजी.')} ${d.registrationNo})` : ''}`, d.decidedAt ? formatDay(d.decidedAt, lang) : ''];
  if (record.choices.dnacprDiscussedWith) parts.push(`${t('discussed with', 'चर्चा की')} ${record.choices.dnacprDiscussedWith}`);
  return parts.filter(Boolean).join(' · ');
}

const CEILING_EN: Record<RespCeiling, string> = {
  0: '0 · Comfort measures only (opioid, anxiolytic, fan, positioning, suction; O₂ only if it relieves)',
  1: '1 · Nasal prongs / simple mask',
  2: '2 · Non-rebreather / venturi mask',
  3: '3 · High-flow nasal oxygen (HFNC)',
  4: '4 · Non-invasive ventilation (CPAP / BiPAP)',
  5: '5 · Intubation and invasive ventilation',
};
const CEILING_HI: Record<RespCeiling, string> = {
  0: '0 · केवल आराम के उपाय (ओपिऑइड, घबराहट की दवा, पंखा, स्थिति, सक्शन; O₂ केवल राहत दे तो)',
  1: '1 · नाक की नली / साधारण मास्क',
  2: '2 · नॉन-रीब्रीदर / वेंचुरी मास्क',
  3: '3 · हाई-फ़्लो नाक ऑक्सीजन (HFNC)',
  4: '4 · बिना ट्यूब वेंटिलेशन (CPAP / BiPAP)',
  5: '5 · ट्यूब डालकर वेंटिलेटर',
};
export function ceilingLabel(level: RespCeiling | null, lang: CareLang): string {
  if (level === null || level === undefined) return noPreferenceStandardCare(lang);
  return careText(lang, CEILING_EN[level], CEILING_HI[level]);
}
export function ceilingShort(level: RespCeiling | null, lang: CareLang): string {
  if (level === null || level === undefined) return noPreference(lang);
  return careText(lang, CEILING_EN[level], CEILING_HI[level]).split(' (')[0];
}
export function ceilingDetails(record: EctprRecord, lang: CareLang): string[] {
  const t = textFor(lang);
  const c = record.choices;
  if (c.respCeiling === null) return [];
  const out = [t('This is the highest acceptable level. All lower levels remain available. Nothing above it starts without a fresh discussion with the decision-maker and treating consultant.', 'यह सबसे ऊँचा स्वीकार्य स्तर है। इससे नीचे के सभी स्तर उपलब्ध हैं। इससे ऊपर कुछ भी निर्णयकर्ता और डॉक्टर से नई बात किए बिना शुरू न करें।')];
  if (c.maxOxygenLpm !== null) out.push(`${t('Maximum oxygen', 'अधिकतम ऑक्सीजन')}: ${c.maxOxygenLpm} L/min`);
  if (c.ceilingConditions.nivMaxHours !== null) out.push(`${t('NIV for at most', 'NIV अधिकतम')} ${c.ceilingConditions.nivMaxHours} ${t('hours', 'घंटे')}`);
  if (c.ceilingConditions.spo2Floor !== null) out.push(`${t('Target SpO₂ floor', 'SpO₂ की न्यूनतम सीमा')}: ${c.ceilingConditions.spo2Floor}%`);
  if (c.ceilingConditions.reversibleCauseException === true) out.push(t('Reversible-cause exception: a new, reversible cause may be treated above this ceiling.', 'ठीक होने वाले कारण की छूट: नए, ठीक होने वाले कारण का इलाज इस सीमा से ऊपर हो सकता है।'));
  if (c.ceilingConditions.reversibleCauseException === false) out.push(t('No reversible-cause exception: keep to this ceiling.', 'कोई छूट नहीं: इसी सीमा में रहें।'));
  return out;
}

export function trialLabel(trial: TrialPlan, lang: CareLang): string {
  const t = textFor(lang);
  const unit = trial.durationUnit === 'HOURS' ? t('hours', 'घंटे') : trial.durationUnit === 'SESSIONS' ? t('sessions', 'सत्र') : t('days', 'दिन');
  const reviewers = trial.reviewers.filter((r) => r.trim()).join(', ');
  return `${t('Time-limited trial', 'समय-सीमित ट्रायल')}: ${trial.durationValue} ${unit}${trial.reviewAt ? ` · ${t('review', 'समीक्षा')} ${formatDateTime(trial.reviewAt, lang)}` : ''}${reviewers ? ` · ${t('by', 'द्वारा')} ${reviewers}` : ''}`;
}

export function icuLabel(icu: Icu | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!icu) return noPreferenceStandardCare(lang);
  if (icu.kind === 'YES') return t('Yes', 'हाँ');
  if (icu.kind === 'NO') return t('No — ward-level or comfort care only', 'नहीं — केवल वार्ड स्तर या आराम की देखभाल');
  return trialLabel(icu.trial, lang);
}
export function icuShort(icu: Icu | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!icu) return noPreference(lang);
  if (icu.kind === 'YES') return t('Yes', 'हाँ');
  if (icu.kind === 'NO') return t('No', 'नहीं');
  return `${t('Trial', 'ट्रायल')} ${icu.trial.durationValue} ${icu.trial.durationUnit === 'HOURS' ? t('h', 'घं') : t('days', 'दिन')}`;
}

export function vasopressorLabel(v: Vasopressors | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!v) return noPreferenceStandardCare(lang);
  if (v.kind === 'NO') return t('No', 'नहीं');
  if (v.kind === 'YES_NO_LIMIT') return t('Yes, no limit', 'हाँ, बिना सीमा');
  if (v.kind === 'YES_MAX_AGENTS') return `${t('Yes, at most', 'हाँ, अधिकतम')} ${v.maxAgents} ${t('agents', 'दवाएँ')}`;
  return trialLabel(v.trial, lang);
}
export function dialysisLabel(d: Dialysis | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!d) return noPreferenceStandardCare(lang);
  if (d.kind === 'NO') return t('No', 'नहीं');
  if (d.kind === 'YES') return t('Yes', 'हाँ');
  if (d.kind === 'ONLY_IF_REVERSIBLE') return t('Only if kidney failure is judged reversible', 'केवल तब जब किडनी फ़ेल्योर ठीक होने वाला माना जाए');
  return trialLabel(d.trial, lang);
}
export function ngLabel(ng: NgFeeding | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!ng) return noPreferenceStandardCare(lang);
  if (ng.kind === 'NO') return t('No', 'नहीं');
  if (ng.kind === 'YES') return t('Yes', 'हाँ');
  return `${t('Up to', 'अधिकतम')} ${ng.days} ${t('days, then review', 'दिन, फिर समीक्षा')}`;
}
export function pegLabel(peg: PegFeeding | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!peg) return noPreferenceStandardCare(lang);
  if (peg === 'NO') return t('No', 'नहीं');
  if (peg === 'YES') return t('Yes', 'हाँ');
  return t('Only after a palliative-care discussion', 'केवल पैलिएटिव केयर चर्चा के बाद');
}
export function hydrationLabel(h: Hydration | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!h) return noPreferenceStandardCare(lang);
  if (h === 'ORAL_SIPS') return t('Oral sips only', 'केवल मुँह से घूँट');
  if (h === 'LIMITED') return t('Limited (≤ 500–1000 mL/24 h IV or SC, reviewed daily)', 'सीमित (24 घंटे में ≤ 500–1000 mL IV या SC, रोज़ समीक्षा)');
  return t('Full maintenance fluids', 'पूरे रखरखाव तरल');
}

export function deviceLabel(record: EctprRecord, lang: CareLang): string {
  const t = textFor(lang);
  const device: ImplantedDevice | null = record.clinical.implantedDevice;
  if (!device) return notRecorded(lang);
  const names: Record<ImplantedDevice, string> = {
    NONE: t('None', 'कोई नहीं'), PACEMAKER: t('Pacemaker', 'पेसमेकर'), ICD: 'ICD', CRT_D: 'CRT-D', OTHER: t('Other device', 'अन्य उपकरण'),
  };
  const base = names[device];
  if (device === 'ICD' || device === 'CRT_D') {
    const d = record.clinical.icdDeactivation;
    if (d.decision === 'DEACTIVATE') return `${base} — ${t('shocks to be deactivated', 'शॉक बंद करने हैं')}${d.date ? ` (${formatDay(d.date, lang)})` : ''}`;
    if (d.decision === 'KEEP') return `${base} — ${t('keep active', 'चालू रखें')}`;
    return `${base} — ${t('no deactivation decision recorded', 'बंद करने का निर्णय दर्ज नहीं')}`;
  }
  return base;
}

export function authorityLabel(basis: AuthorityBasis | null, lang: CareLang): string {
  const t = textFor(lang);
  if (basis === 'NAMED_IN_AMD') return t('named in living will', 'लिविंग विल में नामित');
  if (basis === 'NAMED_BY_PATIENT') return t('named by patient', 'मरीज़ द्वारा नामित');
  if (basis === 'NEXT_OF_KIN') return t('next of kin', 'निकट संबंधी');
  if (basis === 'COURT_APPOINTED') return t('court-appointed', 'अदालत द्वारा नियुक्त');
  return '';
}

export function amdStatusLabel(directive: ExistingDirective | null, lang: CareLang): string {
  const t = textFor(lang);
  if (!directive) return notRecorded(lang);
  if (directive.kind === 'NONE') return t('No living will (AMD)', 'कोई लिविंग विल (AMD) नहीं');
  if (directive.kind === 'PRIOR_WISHES') return `${t('No AMD; earlier wishes noted', 'AMD नहीं; पहले की इच्छाएँ दर्ज')}${directive.note ? `: ${directive.note}` : ''}`;
  return `${t('Valid living will (AMD)', 'वैध लिविंग विल (AMD)')} · ${formatDay(directive.date, lang)} · ${t('held at', 'रखी है')} ${directive.location || notRecorded(lang)} · ${t('attested by', 'सत्यापित')} ${directive.attestor || notRecorded(lang)}`;
}

/**
 * Living will lines for the ED view, spoken summary and PDF (tech spec §1, §6). A living will executed in Sahara outranks what
 * the plan's own form says. Only EXECUTED counts as valid; "if both exist, the AMD governs". `amd` is the patient's status from
 * amd-store, or the card's amdAtIssue. amdCaveat (L7) always goes beside these lines.
 */
export function edAmdLines(directive: ExistingDirective | null, amd: AmdStatusSummary | null | undefined, lang: CareLang): { lines: string[]; governs: boolean } {
  const t = textFor(lang);
  const fromForm = amdStatusLabel(directive, lang);
  const formValid = directive?.kind === 'VALID_AMD';
  const governs = t('The AMD governs over this plan.', 'AMD इस योजना से ऊपर मान्य है।');
  if (amd?.status === 'EXECUTED') {
    const [kind, name] = (amd.attestor ?? '').split(/:\s*/, 2);
    const attestor = amd.attestor ? `${kind === 'Notary' ? t('Notary', 'नोटरी') : t('Gazetted officer', 'राजपत्रित अधिकारी')}: ${name ?? ''}` : notRecorded(lang);
    return {
      governs: true,
      lines: [
        `${t('Living will (AMD) executed in Sahara', 'Sahara में निष्पादित लिविंग विल (AMD)')} · ${t('version', 'संस्करण')} ${amd.version} · ${t('signed', 'हस्ताक्षर')} ${amd.executedOn ? formatDay(amd.executedOn, lang) : notRecorded(lang)} · ${attestor}`,
        ...(formValid ? [`${t('Also on this plan', 'इस योजना में भी')}: ${fromForm}`] : []),
        governs,
      ],
    };
  }
  if (amd?.status === 'REVOKED') {
    return {
      governs: false,
      lines: [
        `${t('Living will cancelled in writing', 'लिविंग विल लिखित रूप में रद्द')} · ${t('version', 'संस्करण')} ${amd.version}`,
        formValid ? `${t('This plan still lists one. Check with the decision-maker', 'यह योजना अब भी एक दिखाती है। निर्णयकर्ता से पूछें')}: ${fromForm}` : fromForm,
      ],
    };
  }
  if (amd) {
    return {
      governs: formValid,
      lines: [
        `${t('Living will in Sahara is not signed and attested, so it is not valid', 'Sahara की लिविंग विल पर हस्ताक्षर और सत्यापन नहीं, इसलिए मान्य नहीं')} · ${t('version', 'संस्करण')} ${amd.version}`,
        fromForm,
        ...(formValid ? [governs] : []),
      ],
    };
  }
  return { governs: formValid, lines: [fromForm, ...(formValid ? [governs] : [])] };
}

/** Harish Rana, 2026 INSC 222, para 322(vi). Same period as AMD_BOARD_PATHWAY step 4 (amd-rules.ts). */
export function boardReconsiderationNotice(lang: CareLang): string {
  return careText(
    lang,
    'After both boards agree, nothing is done for 30 days. In this reconsideration period, anyone affected may go to court (Harish Rana, 2026 INSC 222, para 322(vi)).',
    'दोनों बोर्ड सहमत होने के बाद 30 दिन तक कुछ नहीं किया जाता। इस पुनर्विचार अवधि में कोई भी प्रभावित व्यक्ति अदालत जा सकता है (हरीश राणा, 2026 INSC 222, पैरा 322(vi))।',
  );
}

export function treatingConsultant(record: EctprRecord): { name: string; registrationNo: string; signedAt: string } | null {
  const s = consultantSignature(record);
  return s ? { name: s.name, registrationNo: s.registrationNo ?? '', signedAt: s.signedAt } : null;
}

export function formatDay(value: string | null | undefined, lang: CareLang): string {
  if (!value) return notRecorded(lang);
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
export function formatDateTime(value: string, lang: CareLang): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** tel: link target for a phone number. */
export function telHref(phone: string): string {
  return `tel:${phone.replace(/[^+0-9]/g, '')}`;
}

// ---------------------------------------------------------------------------
// Legal review (Rituparna P, 31 Oct 2026): capacity guidance, home care, withdrawal or withholding

export function capacityGuide(lang: CareLang): { title: string; note: string; points: string[] } {
  const t = textFor(lang);
  return { title: t('How to assess decision-making capacity', 'निर्णय लेने की क्षमता कैसे जाँचें'), note: t('Draft annexure. A clinician and the legal team must approve this wording before the pilot.', 'मसौदा अनुलग्नक। पायलट से पहले डॉक्टर और क़ानूनी टीम को यह भाषा मंज़ूर करनी होगी।'), points: [t('Start by assuming the patient has capacity. Age, diagnosis or disagreeing with the doctor does not remove it.', 'पहले मानें कि मरीज़ में क्षमता है। उम्र, बीमारी या डॉक्टर से असहमति से यह ख़त्म नहीं होती।'), t('Assess this one decision, at this time. Do not rely on old notes.', 'इसी एक फ़ैसले के लिए, अभी जाँचें। पुराने नोट पर भरोसा न करें।'), t('Help them first: use their language, plain words and any aids, at the time of day they are most alert.', 'पहले मदद करें: उनकी भाषा, सरल शब्द और कोई भी सहायक साधन इस्तेमाल करें, उस समय जब वे सबसे ज़्यादा सतर्क हों।'), t('Check all four: can they understand the information, retain it long enough, weigh the choices and their results, and communicate a choice in any way?', 'चारों जाँचें: क्या वे जानकारी समझ सकते हैं, उसे काफ़ी देर याद रख सकते हैं, विकल्पों और नतीजों को तौल सकते हैं, और किसी भी तरह अपना फ़ैसला बता सकते हैं?'), t('If any one is missing, they lack capacity for this decision only. Record the basis and who assessed it, and still involve them as far as possible.', 'अगर एक भी न हो, तो सिर्फ़ इस फ़ैसले की क्षमता नहीं है। आधार और जाँचने वाले का नाम लिखें, और फिर भी उन्हें जितना हो सके शामिल करें।'), t('If it may be temporary (delirium, medicines, pain, infection), treat the cause and check again before acting.', 'अगर यह अस्थायी हो सकती है (डेलिरियम, दवाएँ, दर्द, संक्रमण), तो कारण का इलाज करें और कुछ करने से पहले दोबारा जाँचें।')] };
}

export function withdrawalFinalLabel(value: WithdrawalFinal | null | undefined, lang: CareLang): string {
  const t = textFor(lang);
  switch (value) {
    case 'TO_BE_PERFORMED': return t('Withdrawal or withholding to be performed', 'इलाज रोका जाएगा या शुरू नहीं किया जाएगा');
    case 'FAMILY_REFUSED': return t('Family refused consent (patient has lost decision-making capacity)', 'परिवार ने सहमति नहीं दी (मरीज़ निर्णय क्षमता खो चुके हैं)');
    case 'BOARD_REFUSED': return t('Primary or Secondary Board refused permission', 'प्राथमिक या द्वितीय बोर्ड ने अनुमति नहीं दी');
    case 'PHYSICIAN_REVOKED': return t('Treating doctor revoked their decision to support it', 'इलाज करने वाले डॉक्टर ने समर्थन का अपना फ़ैसला वापस लिया');
    default: return t('No final decision recorded', 'कोई अंतिम फ़ैसला दर्ज नहीं');
  }
}

/** Empty lines are left out. A QR snapshot has no board members or decision, so those lines disappear there. */
export function withdrawalLines(record: EctprRecord, lang: CareLang): string[] {
  const t = textFor(lang);
  const w = record.withdrawal;
  if (!w?.applicable) return [];
  return [
    `${t('Final decision', 'अंतिम फ़ैसला')}: ${withdrawalFinalLabel(w.finalDecision, lang)}`,
    w.primaryBoardDate && `${t('Date the Primary Board was formed', 'प्राथमिक बोर्ड बनने की तारीख़')}: ${w.primaryBoardDate}`,
    w.primaryBoardMembers && `${t('Members of the Primary Board', 'प्राथमिक बोर्ड के सदस्य')}: ${w.primaryBoardMembers}`,
    w.secondaryBoardDate && `${t('Date the Secondary Board was formed', 'द्वितीय बोर्ड बनने की तारीख़')}: ${w.secondaryBoardDate}`,
    w.secondaryBoardMembers && `${t('Members of the Secondary Board', 'द्वितीय बोर्ड के सदस्य')}: ${w.secondaryBoardMembers}`,
    w.boardDecision && `${t('Decision of the Boards', 'बोर्ड का फ़ैसला')}: ${w.boardDecision}`,
    w.protocol && `${t('Protocol for withdrawal or withholding', 'इलाज रोकने या शुरू न करने का तरीका')}: ${w.protocol}`,
  ].filter((line): line is string => !!line);
}

export function homeCareLines(record: EctprRecord, lang: CareLang): string[] {
  const t = textFor(lang);
  const h = record.homeCare;
  if (!h) return [];
  return [
    h.assessmentFrequency && `${t('How often the patient is assessed', 'मरीज़ की जाँच कितनी बार होती है')}: ${h.assessmentFrequency}`,
    h.coordinator && `${t('Person coordinating between the family and the hospital or centre (name, phone, centre)', 'परिवार और अस्पताल या केंद्र के बीच तालमेल रखने वाले (नाम, फ़ोन, केंद्र)')}: ${h.coordinator}`,
    h.ngoContact && `${t('Non-governmental organisation caring for the patient: representative and contact', 'मरीज़ की देखभाल करने वाली ग़ैर-सरकारी संस्था: प्रतिनिधि और संपर्क')}: ${h.ngoContact}${h.ngoConsulted ? ` · ${t('The organisation was consulted about this plan', 'इस योजना पर संस्था से सलाह ली गई')}` : ''}`,
    h.previousAdmission === true && `${t('Admitted before for this condition', 'इस बीमारी के लिए पहले भर्ती')}${h.previousAdmissionDetails ? `: ${h.previousAdmissionDetails}` : ''}`,
  ].filter((line): line is string => !!line);
}
