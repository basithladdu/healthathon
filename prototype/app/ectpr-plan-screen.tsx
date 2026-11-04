'use client';
// Emergency care plan (ECTPR): step-by-step entry wizard (tech spec §4), rules V1–V9 / C1–C8 shown inline,
// versions (Draft / Active / Superseded / Revoked), review, cancellation, signatures and the audit log.
// Doctors create, edit and sign. Patients view, sign their part, request a review or cancel. Family view and request a review.
import { emptyHomeCare, emptyWithdrawal } from './ectpr-rules.ts';
import type { EctprHomeCare, EctprWithdrawal, WithdrawalFinal } from './ectpr-model.ts';
import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from 'react';
import { careLocale, careText, type CareLang } from './care-i18n';
import { AMD_BOARD_PATHWAY } from './amd-rules';
import { DpdpNotice } from './dpdp-notice';
import { alwaysProvidedCare, capacityGuide, homeCareLines, withdrawalFinalLabel, withdrawalLines, boardReconsiderationNotice } from './ectpr-labels';
import { EctprPilotLog } from './ectpr-pilot-log';
import { FhirExportButton, PaperAndCopies, ReviewReminder } from './ectpr-plan-extras';
import { ReadAloudButton } from './tts-speech';
import type { GocOptionSets } from './goc-transcribe';
import type {
  AmbulanceOption, AuditEvent, AuthorityBasis, Capacity, Cpr, DecisionMaker, Dialysis, DnacprReason, EctprRecord, EctprStatus,
  GoalOfCare, HospitalTransfer, Hydration, Icu, IdType, ImplantedDevice, NgFeeding, PegFeeding, RespCeiling, RuleIssue, Signature,
  TransferReason, TrialPlan, Vasopressors,
} from './ectpr-model';
import { DECISION_FIELDS, ROUTINE_REVIEW_WEEKS, TLT_DEFAULT_DAYS, TRANSFER_REASON_LABELS, blocksActivation, blocksSave, defaultTrialPlan,
  emptyEctpr, isOutOfDate, nextReviewDue, reviewReminderDue, reviewTriggers, surrogateEntriesLocked, treatmentLocked, unrecordedDecisions, validateEctpr,
  type ReviewTrigger,
} from './ectpr-rules';
import { listAudit, listEctprVersions, logAudit, revokeEctpr, saveEctprDraft, signEctpr, startReview, subscribeEctpr } from './ectpr-store';

// Loaded only when the doctor opens the conversation panel. The speech engine loads later still, on Transcribe.
const GocTranscribe = lazy(() => import('./goc-transcribe').then((module) => ({ default: module.GocTranscribe })));

type Role = 'doctor' | 'patient' | 'family';
type T = (en: string, hi: string) => string;

// ---------------------------------------------------------------------------
// Option lists (stored values are enums; labels are shown in the reader's language)

type Opt<V extends string> = { value: V; en: string; hi: string; helpEn?: string; helpHi?: string };

const GOALS: Opt<GoalOfCare>[] = [
  { value: 'A_LIFE', en: 'A · Prioritise life', hi: 'A · जीवन को प्राथमिकता', helpEn: 'All treatment that may work, including ICU and a breathing machine.', helpHi: 'हर इलाज जो काम कर सकता है, ICU और साँस की मशीन समेत।' },
  { value: 'B_CEILING', en: 'B · Balanced: treat within limits', hi: 'B · संतुलित: सीमा के अंदर इलाज', helpEn: 'Treat problems that can get better, within the limits written below, as time-limited trials.', helpHi: 'जो समस्या ठीक हो सकती है उसका इलाज, नीचे लिखी सीमाओं के अंदर, तय समय के ट्रायल की तरह।' },
  { value: 'C_COMFORT', en: 'C · Prioritise comfort', hi: 'C · आराम को प्राथमिकता', helpEn: 'Control symptoms. Go to hospital only if comfort cannot be given where the patient is.', helpHi: 'तकलीफ़ों पर काबू। अस्पताल तभी जाएँ जब मरीज़ जहाँ हैं वहाँ आराम न दिया जा सके।' },
];
const CPR_OPTS: Opt<Cpr>[] = [
  { value: 'ATTEMPT', en: 'Attempt CPR', hi: 'CPR की कोशिश करें', helpEn: 'Try to restart the heart and breathing if they stop.', helpHi: 'दिल और साँस रुकने पर उन्हें फिर से चलाने की कोशिश।' },
  { value: 'DNACPR', en: 'Do not attempt CPR (DNACPR)', hi: 'CPR न करें (DNACPR)', helpEn: 'Only about CPR when the heart stops. All other care still goes on.', helpHi: 'सिर्फ़ दिल रुकने पर CPR के बारे में। बाक़ी सारी देखभाल चलती रहती है।' },
];
const DNACPR_REASONS: Opt<DnacprReason>[] = [
  { value: 'FUTILITY', en: 'CPR would not work (clinical futility)', hi: 'CPR काम नहीं करेगा (चिकित्सकीय रूप से व्यर्थ)' },
  { value: 'PATIENT_REFUSAL', en: 'The patient does not want CPR', hi: 'मरीज़ CPR नहीं चाहते' },
  { value: 'BURDEN_OUTWEIGHS_BENEFIT', en: 'The harm of CPR would be more than the benefit', hi: 'CPR का नुक़सान फ़ायदे से ज़्यादा होगा' },
];
type TransferKind = HospitalTransfer['kind'];
const TRANSFER_OPTS: Opt<TransferKind>[] = [
  { value: 'ANY', en: 'Yes, for any serious change', hi: 'हाँ, किसी भी गंभीर बदलाव पर' },
  { value: 'ONLY_FOR', en: 'Only for the reasons ticked below', hi: 'सिर्फ़ नीचे चुने गए कारणों के लिए' },
  { value: 'NO', en: 'No — care where they are', hi: 'नहीं — जहाँ हैं वहीं देखभाल', helpEn: 'Call the numbers below before calling an ambulance.', helpHi: 'एम्बुलेंस बुलाने से पहले नीचे लिखे नंबरों पर फ़ोन करें।' },
];
const TRANSFER_REASON_HI: Record<TransferReason, string> = {
  PAIN: 'दर्द जो काबू में न हो', BREATHLESSNESS: 'साँस फूलना जो काबू में न हो', BLEEDING: 'बहुत ख़ून बहना', SEIZURES: 'दौरे (फ़िट्स)',
  FALL_FRACTURE: 'गिरना और हड्डी टूटने का शक', BLOCKED_TUBE: 'कैथेटर, नली या स्टेंट बंद होना या हिल जाना',
  INFECTION_IV: 'संक्रमण जिसमें ड्रिप (IV) से दवा चाहिए', CONFUSION: 'अचानक उलझन या बेचैनी जो शांत न हो',
  CAREGIVER_RESPITE: 'देखभाल करने वाला अब संभाल नहीं पा रहा (आराम चाहिए)',
};
const AMBULANCE_OPTS: Opt<AmbulanceOption>[] = [
  { value: 'FULL_RESUSCITATION', en: 'Full emergency care and transport', hi: 'पूरी इमरजेंसी देखभाल और अस्पताल ले जाना' },
  { value: 'SYMPTOM_RELIEF_TRANSPORT', en: 'Transport with symptom relief only', hi: 'सिर्फ़ तकलीफ़ में राहत के साथ ले जाना', helpEn: 'Oxygen for breathlessness, pain relief, positioning. No CPR, no breathing tube.', helpHi: 'साँस के लिए ऑक्सीजन, दर्द से राहत, सही तरह लिटाना। CPR नहीं, साँस की नली नहीं।' },
  { value: 'TRANSPORT_HOME_NURSE_ESCORT', en: 'Take home for the last days, with a nurse', hi: 'आख़िरी दिनों के लिए नर्स के साथ घर ले जाना' },
];
const CEILINGS: Array<{ value: RespCeiling; en: string; hi: string; helpEn: string; helpHi: string }> = [
  { value: 0, en: '0 · Comfort only', hi: '0 · सिर्फ़ आराम', helpEn: 'Medicines for breathlessness, a fan, sitting up, suction. Oxygen only if it helps comfort.', helpHi: 'साँस फूलने की दवा, पंखा, बैठाना, सक्शन। ऑक्सीजन तभी जब उससे आराम मिले।' },
  { value: 1, en: '1 · Nasal prongs or simple mask', hi: '1 · नाक की नली या सादा मास्क', helpEn: 'Low-flow oxygen. Write the highest flow below.', helpHi: 'कम बहाव वाली ऑक्सीजन। सबसे ज़्यादा बहाव नीचे लिखें।' },
  { value: 2, en: '2 · Non-rebreather or Venturi mask', hi: '2 · नॉन-रीब्रीदर या वेंचुरी मास्क', helpEn: 'More oxygen through a tighter mask.', helpHi: 'कसे हुए मास्क से ज़्यादा ऑक्सीजन।' },
  { value: 3, en: '3 · High-flow nasal oxygen (HFNC)', hi: '3 · हाई-फ़्लो नाक ऑक्सीजन (HFNC)', helpEn: 'Warm, high-flow oxygen through the nose. Usually needs a hospital bed.', helpHi: 'नाक से गर्म, तेज़ बहाव वाली ऑक्सीजन। आमतौर पर अस्पताल का बिस्तर चाहिए।' },
  { value: 4, en: '4 · NIV (CPAP or BiPAP)', hi: '4 · NIV (CPAP या BiPAP)', helpEn: 'A tight mask pushes air in to help breathing. No breathing tube.', helpHi: 'कसा मास्क हवा अंदर धकेलकर साँस में मदद करता है। साँस की नली नहीं।' },
  { value: 5, en: '5 · Breathing tube and ventilator', hi: '5 · साँस की नली और वेंटिलेटर', helpEn: 'Intubation and a breathing machine in ICU.', helpHi: 'ICU में नली डालना और साँस की मशीन।' },
];
type IcuKind = Icu['kind'];
const ICU_OPTS: Opt<IcuKind>[] = [
  { value: 'YES', en: 'Yes', hi: 'हाँ' },
  { value: 'TRIAL', en: 'Time-limited trial', hi: 'तय समय का ट्रायल', helpEn: 'Try ICU for a set time, then review together.', helpHi: 'तय समय के लिए ICU आज़माएँ, फिर साथ मिलकर समीक्षा करें।' },
  { value: 'NO', en: 'No', hi: 'नहीं', helpEn: 'Ward-level or comfort care only.', helpHi: 'सिर्फ़ वार्ड स्तर या आराम की देखभाल।' },
];
type VasoKind = Vasopressors['kind'];
const VASO_OPTS: Opt<VasoKind>[] = [
  { value: 'NO', en: 'No', hi: 'नहीं' },
  { value: 'TRIAL', en: 'Time-limited trial', hi: 'तय समय का ट्रायल' },
  { value: 'YES_NO_LIMIT', en: 'Yes, no limit', hi: 'हाँ, कोई सीमा नहीं' },
  { value: 'YES_MAX_AGENTS', en: 'Yes, up to a set number of drugs', hi: 'हाँ, तय संख्या तक दवाएँ' },
];
type DialysisKind = Dialysis['kind'];
const DIALYSIS_OPTS: Opt<DialysisKind>[] = [
  { value: 'NO', en: 'No', hi: 'नहीं' },
  { value: 'TRIAL', en: 'Trial of a set number of sessions or days', hi: 'तय सत्रों या दिनों का ट्रायल' },
  { value: 'YES', en: 'Yes', hi: 'हाँ' },
  { value: 'ONLY_IF_REVERSIBLE', en: 'Only if the kidney problem can get better', hi: 'सिर्फ़ तब, जब गुर्दे की समस्या ठीक हो सकती हो' },
];
type NgKind = NgFeeding['kind'];
const NG_OPTS: Opt<NgKind>[] = [
  { value: 'NO', en: 'No', hi: 'नहीं' },
  { value: 'UP_TO_DAYS', en: 'Try for a set number of days, then review', hi: 'तय दिनों तक आज़माएँ, फिर समीक्षा' },
  { value: 'YES', en: 'Yes', hi: 'हाँ' },
];
const PEG_OPTS: Opt<PegFeeding>[] = [
  { value: 'NO', en: 'No', hi: 'नहीं' },
  { value: 'ONLY_AFTER_PALLIATIVE_DISCUSSION', en: 'Only after a talk with the palliative care team', hi: 'सिर्फ़ पैलिएटिव केयर टीम से बात के बाद' },
  { value: 'YES', en: 'Yes', hi: 'हाँ' },
];
const HYDRATION_OPTS: Opt<Hydration>[] = [
  { value: 'ORAL_SIPS', en: 'Sips by mouth only', hi: 'सिर्फ़ मुँह से घूँट' },
  { value: 'LIMITED', en: 'Limited fluids', hi: 'सीमित तरल', helpEn: 'About 500–1000 mL a day by drip or under the skin, matched to urine, checked every day. Reduce or stop if there is swelling, a wet chest, a swollen belly or worse breathing.', helpHi: 'दिन में लगभग 500–1000 mL ड्रिप या त्वचा के नीचे से, पेशाब के हिसाब से, रोज़ जाँच। सूजन, छाती में पानी, पेट फूलने या साँस बिगड़ने पर कम करें या बंद करें।' },
  { value: 'FULL', en: 'Full fluids (maintenance)', hi: 'पूरे तरल (मेंटेनेंस)' },
];
const DEVICE_OPTS: Opt<ImplantedDevice>[] = [
  { value: 'NONE', en: 'None', hi: 'कोई नहीं' },
  { value: 'PACEMAKER', en: 'Pacemaker', hi: 'पेसमेकर' },
  { value: 'ICD', en: 'ICD (gives shocks)', hi: 'ICD (झटके देता है)' },
  { value: 'CRT_D', en: 'CRT-D (pacemaker that also gives shocks)', hi: 'CRT-D (पेसमेकर जो झटके भी देता है)' },
  { value: 'OTHER', en: 'Other device', hi: 'दूसरा उपकरण' },
];
const CAPACITY_OPTS: Opt<Capacity>[] = [
  { value: 'HAS', en: 'Can make this decision', hi: 'यह फ़ैसला ले सकते हैं' },
  { value: 'HAS_DELEGATED', en: 'Can decide, but wants someone else to decide', hi: 'फ़ैसला ले सकते हैं, पर चाहते हैं कि कोई और ले' },
  { value: 'FLUCTUATES', en: 'Ability to decide comes and goes', hi: 'फ़ैसला लेने की क्षमता कभी होती है, कभी नहीं', helpEn: 'Set a date to check again.', helpHi: 'दोबारा जाँचने की तारीख़ तय करें।' },
  { value: 'LACKS', en: 'Cannot make this decision now', hi: 'अभी यह फ़ैसला नहीं ले सकते' },
];
const ABILITIES: Array<{ key: string; en: string; hi: string }> = [
  { key: 'understand', en: 'Understands the information', hi: 'जानकारी समझते हैं' },
  { key: 'retain', en: 'Remembers it long enough', hi: 'उसे काफ़ी देर याद रखते हैं' },
  { key: 'weigh', en: 'Weighs it up to decide', hi: 'सोच-समझकर तौलते हैं' },
  { key: 'communicate', en: 'Can tell us the decision', hi: 'फ़ैसला बता सकते हैं' },
];
const AUTHORITY_OPTS: Opt<AuthorityBasis>[] = [
  { value: 'NAMED_IN_AMD', en: 'Named in a signed living will (AMD)', hi: 'हस्ताक्षरित लिविंग विल (AMD) में नाम है', helpEn: 'Recognised by the 2023 Supreme Court order. The medical boards take this person’s consent.', helpHi: '2023 के सुप्रीम कोर्ट आदेश से मान्य। मेडिकल बोर्ड इन्हीं की सहमति लेते हैं।' },
  { value: 'NAMED_BY_PATIENT', en: 'Named by the patient (no living will)', hi: 'मरीज़ ने नाम दिया (लिविंग विल नहीं)', helpEn: 'A strong sign of the patient’s wish, but not a legal nomination.', helpHi: 'मरीज़ की इच्छा का मज़बूत संकेत, पर क़ानूनी नामांकन नहीं।' },
  { value: 'NEXT_OF_KIN', en: 'Next of kin, next friend or guardian', hi: 'निकट संबंधी, मित्र या अभिभावक', helpEn: 'The 2023 order’s route when there is no living will.', helpHi: 'लिविंग विल न होने पर 2023 के आदेश का रास्ता।' },
  { value: 'COURT_APPOINTED', en: 'Guardian appointed by a court', hi: 'अदालत द्वारा नियुक्त अभिभावक' },
];
const ID_OPTS: Array<{ value: IdType; en: string; hi: string }> = [
  { value: 'AADHAAR', en: 'Aadhaar', hi: 'आधार' }, { value: 'VOTER_ID', en: 'Voter ID', hi: 'वोटर आईडी' }, { value: 'PAN', en: 'PAN card', hi: 'पैन कार्ड' },
  { value: 'PASSPORT', en: 'Passport', hi: 'पासपोर्ट' }, { value: 'DRIVING_LICENCE', en: 'Driving licence', hi: 'ड्राइविंग लाइसेंस' },
  { value: 'RATION_CARD', en: 'Ration card', hi: 'राशन कार्ड' }, { value: 'OTHER', en: 'Other ID', hi: 'दूसरी आईडी' },
];
type DirectiveKind = NonNullable<EctprRecord['existingDirective']>['kind'];
const DIRECTIVE_OPTS: Opt<DirectiveKind>[] = [
  { value: 'VALID_AMD', en: 'A signed, attested living will (AMD)', hi: 'हस्ताक्षरित और सत्यापित लिविंग विल (AMD)', helpEn: 'If both exist, the living will governs.', helpHi: 'दोनों हों तो लिविंग विल मान्य होगी।' },
  { value: 'PRIOR_WISHES', en: 'Wishes said or written before (not a legal AMD)', hi: 'पहले कही या लिखी इच्छाएँ (क़ानूनी AMD नहीं)' },
  { value: 'NONE', en: 'No living will', hi: 'कोई लिविंग विल नहीं' },
];
const UNIT_OPTS: Array<{ value: TrialPlan['durationUnit']; en: string; hi: string }> = [
  { value: 'DAYS', en: 'days', hi: 'दिन' }, { value: 'HOURS', en: 'hours', hi: 'घंटे' }, { value: 'SESSIONS', en: 'sessions', hi: 'सत्र' },
];
// The conversation panel shows these same lists, so its options can never differ from the plan's.
const GOC_OPTIONS: GocOptionSets = {
  goalOfCare: GOALS, cpr: CPR_OPTS, hospitalTransfer: TRANSFER_OPTS, ambulance: AMBULANCE_OPTS,
  respCeiling: CEILINGS.map((level) => ({ ...level, value: String(level.value) })),
  icu: ICU_OPTS, vasopressors: VASO_OPTS, dialysis: DIALYSIS_OPTS, ng: NG_OPTS, peg: PEG_OPTS, hydration: HYDRATION_OPTS,
  patientKnows: [{ value: 'yes', en: 'Yes', hi: 'हाँ' }, { value: 'no', en: 'No', hi: 'नहीं' }],
  familyAskedNotToTell: [{ value: 'yes', en: 'The family asked that the patient not be told', hi: 'परिवार ने कहा कि मरीज़ को न बताया जाए' }],
};

const LANGUAGE_OPTS = [
  ['en', 'English', 'अंग्रेज़ी'], ['hi', 'Hindi', 'हिन्दी'], ['te', 'Telugu', 'तेलुगु'], ['ur', 'Urdu', 'उर्दू'], ['kn', 'Kannada', 'कन्नड़'],
  ['ta', 'Tamil', 'तमिल'], ['ml', 'Malayalam', 'मलयालम'], ['mr', 'Marathi', 'मराठी'], ['bn', 'Bengali', 'बांग्ला'], ['other', 'Other', 'दूसरी'],
] as const;

const TRIGGER_HI: Record<ReviewTrigger, string> = {
  NEW_ADMISSION: 'अस्पताल में कोई भी नई भर्ती',
  TRANSFER_BETWEEN_SETTINGS: 'जगह बदलना (घर, अस्पताल, हॉस्पिस, केयर होम)',
  SIGNIFICANT_CHANGE: 'हालत में बड़ा बदलाव, बेहतर या बदतर',
  PATIENT_OR_FAMILY_REQUEST: 'मरीज़ या परिवार समीक्षा माँगे',
  NEW_LINE_OF_TREATMENT: 'इलाज का नया दौर शुरू हो',
  ROUTINE_INTERVAL: `हर ${ROUTINE_REVIEW_WEEKS} हफ़्ते में नियमित समीक्षा`,
};

const STEPS: Array<{ en: string; hi: string }> = [
  { en: 'Patient', hi: 'मरीज़' },
  { en: 'Decision-makers', hi: 'फ़ैसला लेने वाले' },
  { en: 'Capacity and living will', hi: 'क्षमता और लिविंग विल' },
  { en: 'Understanding', hi: 'समझ' },
  { en: 'Values', hi: 'मूल्य' },
  { en: 'Emergency choices', hi: 'इमरजेंसी के फ़ैसले' },
  { en: 'Other treatment', hi: 'दूसरे इलाज' },
  { en: 'Stopping or not starting treatment', hi: 'इलाज रोकना या शुरू न करना' },
  { en: 'Review date', hi: 'समीक्षा की तारीख़' },
  { en: 'Signatures', hi: 'हस्ताक्षर' },
];

// ---------------------------------------------------------------------------
// Rule messages in Hindi (rules are written in English in ectpr-rules.ts)

function issueText(lang: CareLang, item: RuleIssue, record: EctprRecord): string {
  if (lang === 'en') return item.message;
  const hi: Record<string, string> = {
    L1: 'यह योजना केवल वयस्कों के लिए है। जन्म तिथि के अनुसार मरीज़ 18 साल से कम हैं।',
    'L1-dob': 'जन्म तिथि लिखें। सहारा योजना केवल वयस्कों (18 साल या ज़्यादा) के लिए है।',
    'ED-new': 'इमरजेंसी में शुरू की गई नई योजना में असली इच्छा की जगह घबराहट दर्ज हो सकती है। हो सके तो शांत समय में योजना बनाएँ: क्लिनिक में, छुट्टी के समय या घर पर।',
    V4: 'पहले पुष्टि करें कि बीमारी के बारे में समझा दिया गया है कि यह ठीक नहीं हो सकती, और परिवार इसे दोहरा सकता है। तब तक इलाज के फ़ैसले बंद रहेंगे।',
    V5: 'मरीज़ की ओर से जवाब देने वाले व्यक्ति को पहले वादा करना होगा कि वे वही बताएँगे जो मरीज़ चुनते, न कि जो वे ख़ुद चाहते हैं।',
    'AUDIT-values': 'इलाज की सीमाएँ भरी हैं, पर मरीज़ की बातें खाली हैं। मरीज़ के लिए क्या ज़रूरी है, उन्हीं के शब्दों में लिखें।',
    'DNACPR-reason': '"CPR न करें" का कारण चुनें।',
    'DNACPR-discussed': 'लिखें कि DNACPR के फ़ैसले पर किससे बात हुई।',
    'DNACPR-decided-by': 'DNACPR का फ़ैसला करने वाले डॉक्टर का नाम, रजिस्ट्रेशन नंबर, और तारीख़ व समय लिखें।',
    V3: 'लक्ष्य जीवन को प्राथमिकता देना है, पर साँस की मदद या ICU सीमित है। अक्सर यह गलती होती है। कृपया जाँचें और पुष्टि करें।',
    'TRANSFER-reasons': 'अस्पताल जाने का कम से कम एक कारण चुनें।',
    'TRANSFER-call-first': 'एम्बुलेंस से पहले फ़ोन करने के नंबर जोड़ें, ताकि परिवार को पता हो कि पहले किसे फ़ोन करना है।',
    'RESP-max-oxygen': 'ऑक्सीजन का सबसे ज़्यादा बहाव (लीटर प्रति मिनट) लिखें।',
    C1: 'योजना में लिखा है "अस्पताल न जाएँ", पर हाई-फ़्लो ऑक्सीजन, NIV या ICU के लिए आमतौर पर अस्पताल में रहना पड़ता है। कारण लिखें (जैसे घर पर NIV) या योजना बदलें।',
    C2: 'लक्ष्य आराम है, पर ICU, ब्लड-प्रेशर की दवा या डायलिसिस "हाँ" पर है। सेव करने से पहले कारण लिखें।',
    C3: 'साँस की नली या ब्लड-प्रेशर की दवा के लिए ICU या HDU चाहिए, पर ICU "नहीं" पर है। कारण लिखें या योजना बदलें।',
    'C4-niv-duration': 'NIV ट्रायल के घंटे 0 से ज़्यादा होने चाहिए।',
    'VASO-max-agents': 'ब्लड-प्रेशर की ज़्यादा से ज़्यादा कितनी दवाएँ दी जा सकती हैं, लिखें (1 या ज़्यादा)।',
    'C4-ng-duration': 'नाक की नली (NG) कितने दिन आज़मानी है, लिखें (1 या ज़्यादा)।',
    C5: 'मरीज़ के दिल में झटका देने वाला उपकरण (ICD) है। DNACPR या आराम के लक्ष्य के साथ ये झटके आख़िरी समय में दर्द दे सकते हैं। इसे बंद करने या चालू रखने का फ़ैसला और तारीख़ लिखें।',
    C6: 'पहले से लगी खाने की नली निकालना इलाज रोकना है। इसके लिए मेडिकल बोर्ड की प्रक्रिया ज़रूरी है। सिर्फ़ इस योजना से इसे नहीं निकाला जा सकता।',
    C7: 'मरीज़ की फ़ैसला लेने की क्षमता कभी होती है, कभी नहीं। दोबारा जाँचने की तारीख़ तय करें।',
    'CAP-assessment': 'लिखें कि क्षमता किसने जाँची, किस तारीख़ को, और कैसे (समझना, याद रखना, तौलना, बताना)।',
    'CAP-missing': 'दर्ज करें कि क्या मरीज़ आज यह फ़ैसला ले सकते हैं।',
    'DM-missing': 'मरीज़ शायद फ़ैसला न ले सकें। उनकी ओर से बोलने वाले व्यक्ति को जोड़ें, जिनका फ़ोन दिन-रात चालू रहे।',
    C8: 'परिवार ने कहा कि मरीज़ को न बताया जाए, पर मरीज़ फ़ैसला ले सकते हैं। अगर मरीज़ सीधे पूछें, तो उन्हें सच बताना ज़रूरी है। इसकी योजना लिखें।',
    'FIN-concern': 'पूछें कि क्या इस फ़ैसले में पैसे की चिंता है। पूरा करने से पहले सोशल वर्क या सरकारी योजना की जाँच की पेशकश करें।',
    'REVIEW-due': 'वह तारीख़ तय करें जब इस योजना की समीक्षा ज़रूरी होगी।',
    'REVIEW-past': 'समीक्षा की तारीख़ निकल चुकी है। आगे की तारीख़ चुनें।',
    V6: 'अँगूठे के निशान या कम पढ़ाई-लिखाई की स्थिति में दो गवाहों के हस्ताक्षर ज़रूरी हैं।',
    V7: 'इलाज करने वाले कंसल्टेंट को अपने रजिस्ट्रेशन नंबर के साथ साइन करना होगा। तब तक यह सिर्फ़ बातचीत का नोट है, योजना नहीं।',
    W1: 'आगे बढ़ने से पहले दोनों बोर्ड की तारीख़, सदस्य, उनका फ़ैसला और तरीका दर्ज करें।',
    W2: 'द्वितीय फ़ैसला लेने वाले का नाम दर्ज है। उन्हें भी साइन करना चाहिए।',
    V8: 'हर छपे पन्ने पर नाम और अस्पताल नंबर (UHID) होता है। दोनों भरें।',
  };
  if (lang === 'hi' && item.id === 'V1') {
    const missing = unrecordedDecisions(record).length;
    return missing === DECISION_FIELDS.length ? 'अभी कुछ नहीं चुना गया है। हर हिस्से में "कोई पसंद दर्ज नहीं" दिखेगा और सामान्य इलाज दिया जाएगा।'
      : `${missing} हिस्सों में कोई पसंद दर्ज नहीं है। इनके लिए सामान्य इलाज दिया जाएगा। इसे कभी मना करना नहीं माना जाता।`;
  }
  if (lang === 'hi' && item.id === 'V2') {
    return record.choices.goalOfCare === 'C_COMFORT'
      ? 'लक्ष्य आराम है, पर CPR "कोशिश करें" पर है। CPR के लिए साँस की नली और ICU चाहिए। कारण लिखें, या DNACPR पर सोचें।'
      : 'साँस की सीमा नली से नीचे है, पर CPR "कोशिश करें" पर है। CPR के लिए साँस की नली चाहिए। कारण लिखें, या DNACPR पर सोचें।';
  }
  const trial = lang === 'hi' ? /^C4-(icu|vasopressors|dialysis)-(duration|review|reviewers)$/.exec(item.id) : null;
  if (trial) {
    const label = trial[1] === 'icu' ? 'ICU' : trial[1] === 'vasopressors' ? 'ब्लड-प्रेशर दवा' : 'डायलिसिस';
    return trial[2] === 'duration' ? `${label} ट्रायल की अवधि 0 से ज़्यादा होनी चाहिए।`
      : trial[2] === 'review' ? `${label} ट्रायल की समीक्षा की तारीख़ और समय लिखें।` : `लिखें कि ${label} ट्रायल की समीक्षा कौन करेगा।`;
  }
  return careText(lang, item.message, hi[item.id]);
}

function storeError(t: T, message: string | undefined): string {
  const known: Record<string, string> = {
    'Fix the items marked as blocking before saving.': 'सेव करने से पहले "रोक" वाले सभी बिंदु ठीक करें।',
    'This plan cannot be made active yet. Fix the items listed first.': 'यह योजना अभी लागू नहीं हो सकती। पहले सूची के बिंदु ठीक करें।',
    'The treating consultant must sign with their registration number.': 'इलाज करने वाले कंसल्टेंट को रजिस्ट्रेशन नंबर के साथ साइन करना होगा।',
    'A signature needs a name.': 'हस्ताक्षर के लिए नाम ज़रूरी है।',
    'This plan is already signed or closed. It cannot be changed.': 'यह योजना पहले ही साइन या बंद हो चुकी है। इसे बदला नहीं जा सकता।',
    'This version is already signed. Start a review to make a new version.': 'यह संस्करण साइन हो चुका है। नया संस्करण बनाने के लिए समीक्षा शुरू करें।',
    'A cancellation needs a reason and the name of the person cancelling.': 'रद्द करने के लिए कारण और रद्द करने वाले का नाम ज़रूरी है।',
    'This version is no longer current.': 'यह संस्करण अब मौजूदा नहीं है।',
    'Plan not found.': 'योजना नहीं मिली।',
  };
  const text = message ?? 'Something went wrong. Please try again.';
  return t(text, known[text] ?? 'कुछ गलत हो गया। कृपया दोबारा कोशिश करें।');
}

// ---------------------------------------------------------------------------
// Small helpers

const SEVERITY_ORDER: Record<RuleIssue['severity'], number> = { block: 0, confirm: 1, warn: 2 };

function stepForField(field: string): number {
  if (field.startsWith('decisionMakers')) return 1;
  if (field.startsWith('capacity') || field.startsWith('existingDirective') || field.startsWith('surrogateDeclaration')) return 2;
  if (field.startsWith('prognosticUnderstanding')) return 3;
  if (field.startsWith('values')) return 4;
  if (/^choices\.(icu|vasopressors|dialysis|feeding|hydration|conflictReasons\.C[23])/.test(field) || field.startsWith('clinical.icdDeactivation')) return 6;
  if (field.startsWith('choices')) return 5;
  if (field === 'withdrawal') return 7;
  if (field === 'reviewDue') return 8;
  if (field === 'signatures') return 9;
  return 0;
}

function toLocalInput(iso: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
function fromLocalInput(value: string): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}
function toNumber(value: string): number | null {
  if (value.trim() === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
const linesToList = (value: string): string[] => (value === '' ? [] : value.split('\n'));
const listToLines = (value: string[]): string => value.join('\n');

function cleanForSave(record: EctprRecord): EctprRecord {
  const next = structuredClone(record);
  const tidy = (list: string[]) => list.map((item) => item.trim()).filter(Boolean);
  next.decisionMakers.familyToInform = tidy(next.decisionMakers.familyToInform);
  next.values.unacceptableOutcomes = tidy(next.values.unacceptableOutcomes);
  next.choices.callBeforeAmbulance = tidy(next.choices.callBeforeAmbulance);
  next.careTeam.extraTeam = tidy(next.careTeam.extraTeam);
  for (const option of [next.choices.icu, next.choices.vasopressors, next.choices.dialysis]) {
    if (option && option.kind === 'TRIAL') option.trial.reviewers = tidy(option.trial.reviewers);
  }
  return next;
}

function formatDay(lang: CareLang, value: string): string {
  if (!value) return '';
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}
function formatTime(lang: CareLang, value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

function statusLabel(t: T, status: EctprStatus): string {
  return status === 'DRAFT' ? t('Draft', 'ड्राफ़्ट') : status === 'ACTIVE' ? t('Active', 'लागू') : status === 'SUPERSEDED' ? t('Superseded', 'पुराना हो गया') : t('Revoked', 'रद्द');
}

function signatureRole(t: T, role: Signature['role']): string {
  switch (role) {
    case 'TREATING_CONSULTANT': return t('Treating consultant', 'इलाज करने वाले कंसल्टेंट');
    case 'PALLIATIVE_CLINICIAN': return t('Palliative care clinician', 'पैलिएटिव केयर डॉक्टर');
    case 'NURSE': return t('Nurse', 'नर्स');
    case 'PATIENT': return t('Patient', 'मरीज़');
    case 'SURROGATE': return t('Person deciding for the patient', 'मरीज़ की ओर से फ़ैसला लेने वाले');
    case 'WITNESS': return t('Witness', 'गवाह');
  }
}

const auditViewed = new Set<string>();

// ---------------------------------------------------------------------------
// Form building blocks

function Choices<V extends string>({ t, name, legend, hint, value, options, onChange, disabled, invalid, cols, noneHint, children }: {
  t: T; name: string; legend: string; hint?: string; value: V | null; options: Opt<V>[]; onChange: (value: V | null) => void;
  disabled?: boolean; invalid?: boolean; cols?: boolean; noneHint?: string; children?: ReactNode;
}) {
  return <fieldset className="ep-group" disabled={disabled} aria-invalid={invalid || undefined}>
    <legend>{legend}</legend>
    {hint ? <p className="ep-hint">{hint}</p> : null}
    <div className={`ep-options${cols ? ' ep-cols' : ''}`}>
      {options.map((option) => <label key={option.value} className="ep-option">
        <input type="radio" name={name} checked={value === option.value} onChange={() => onChange(option.value)} />
        <span><strong>{t(option.en, option.hi)}</strong>{option.helpEn ? <small>{t(option.helpEn, option.helpHi ?? option.helpEn)}</small> : null}</span>
      </label>)}
      <label className="ep-option ep-none">
        <input type="radio" name={name} checked={value === null} onChange={() => onChange(null)} />
        <span><strong>{t('No preference recorded', 'कोई पसंद दर्ज नहीं')}</strong><small>{noneHint ?? t('Standard care will be given. This is never read as a refusal.', 'सामान्य इलाज दिया जाएगा। इसे कभी मना करना नहीं माना जाता।')}</small></span>
      </label>
    </div>
    {children}
  </fieldset>;
}

function YesNo({ t, name, legend, hint, value, onChange, disabled, invalid }: { t: T; name: string; legend: string; hint?: string; value: boolean | null; onChange: (value: boolean | null) => void; disabled?: boolean; invalid?: boolean }) {
  return <Choices t={t} name={name} legend={legend} hint={hint} disabled={disabled} invalid={invalid} cols
    value={value === null ? null : value ? 'yes' : 'no'} onChange={(next) => onChange(next === null ? null : next === 'yes')}
    noneHint={t('Not asked or not recorded yet.', 'अभी पूछा या दर्ज नहीं किया गया।')}
    options={[{ value: 'yes', en: 'Yes', hi: 'हाँ' }, { value: 'no', en: 'No', hi: 'नहीं' }]} />;
}

function Field({ id, label, hint, children, wide }: { id: string; label: string; hint?: string; children: ReactNode; wide?: boolean }) {
  return <div className={`ep-field${wide ? ' ep-wide' : ''}`}>
    <label htmlFor={id}>{label}</label>
    {children}
    {hint ? <p className="ep-hint" id={`${id}-hint`}>{hint}</p> : null}
  </div>;
}

function IssueList({ t, lang, issues, record }: { t: T; lang: CareLang; issues: RuleIssue[]; record: EctprRecord }) {
  if (!issues.length) return null;
  const sorted = [...issues].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]);
  return <ul className="ep-issues" aria-live="polite">
    {sorted.map((item) => <li key={item.id} className="ep-issue" data-severity={item.severity}>
      <div>
        <span className="ep-issue-tag">{item.severity === 'block'
          ? (item.stage === 'activate' ? t('Before signing', 'साइन से पहले') : t('Must fix', 'ठीक करें'))
          : item.severity === 'confirm' ? t('Check', 'जाँचें') : t('Note', 'ध्यान दें')}</span>
        <span>{issueText(lang, item, record)}</span>
      </div>
    </li>)}
  </ul>;
}

function TrialEditor({ t, id, trial, onChange, disabled }: { t: T; id: string; trial: TrialPlan; onChange: (trial: TrialPlan) => void; disabled?: boolean }) {
  return <div className="ep-sub-fields">
    <p className="ep-hint">{t(`A time-limited trial is usually about ${TLT_DEFAULT_DAYS} days (range 3–7). At the end, review it together with the family.`, `तय समय का ट्रायल आमतौर पर लगभग ${TLT_DEFAULT_DAYS} दिन (3–7) का होता है। अंत में परिवार के साथ मिलकर समीक्षा करें।`)}</p>
    <div className="ep-fields">
      <Field id={`${id}-duration`} label={t('How long', 'कितने समय')}>
        <input id={`${id}-duration`} type="number" min={1} inputMode="numeric" disabled={disabled} value={trial.durationValue || ''}
          aria-invalid={!(trial.durationValue > 0) || undefined}
          onChange={(event) => onChange({ ...trial, durationValue: toNumber(event.target.value) ?? 0 })} />
      </Field>
      <Field id={`${id}-unit`} label={t('Unit', 'इकाई')}>
        <select id={`${id}-unit`} disabled={disabled} value={trial.durationUnit} onChange={(event) => onChange({ ...trial, durationUnit: event.target.value as TrialPlan['durationUnit'] })}>
          {UNIT_OPTS.map((unit) => <option key={unit.value} value={unit.value}>{t(unit.en, unit.hi)}</option>)}
        </select>
      </Field>
      <Field id={`${id}-review`} label={t('Review on (date and time)', 'समीक्षा कब (तारीख़ और समय)')}>
        <input id={`${id}-review`} type="datetime-local" disabled={disabled} value={toLocalInput(trial.reviewAt)} aria-invalid={!trial.reviewAt || undefined}
          onChange={(event) => onChange({ ...trial, reviewAt: fromLocalInput(event.target.value) })} />
      </Field>
      <Field id={`${id}-reviewers`} label={t('Who will review (one name per line)', 'समीक्षा कौन करेगा (हर लाइन में एक नाम)')}>
        <textarea id={`${id}-reviewers`} rows={2} disabled={disabled} value={listToLines(trial.reviewers)} aria-invalid={!trial.reviewers.some((name) => name.trim()) || undefined}
          onChange={(event) => onChange({ ...trial, reviewers: linesToList(event.target.value) })} />
      </Field>
    </div>
  </div>;
}

function DecisionMakerForm({ t, id, title, person, onChange, disabled }: { t: T; id: string; title: string; person: DecisionMaker | null; onChange: (person: DecisionMaker | null) => void; disabled?: boolean }) {
  if (!person) {
    return <fieldset className="ep-group" disabled={disabled}><legend>{title}</legend>
      <p className="ep-hint">{t('Not added yet.', 'अभी नहीं जोड़ा गया।')}</p>
      <button type="button" className="ep-btn ep-quiet" onClick={() => onChange({ name: '', relation: '', phone24h: '', idType: null, idLast4: null, authorityBasis: null })}>{t('Add this person', 'इन्हें जोड़ें')}</button>
    </fieldset>;
  }
  const set = (patch: Partial<DecisionMaker>) => onChange({ ...person, ...patch });
  return <fieldset className="ep-group" disabled={disabled}><legend>{title}</legend>
    <div className="ep-fields">
      <Field id={`${id}-name`} label={t('Full name', 'पूरा नाम')}><input id={`${id}-name`} type="text" autoComplete="off" value={person.name} onChange={(event) => set({ name: event.target.value })} /></Field>
      <Field id={`${id}-relation`} label={t('Relation to the patient', 'मरीज़ से रिश्ता')} hint={t('Includes spouses, live-in partners, same-sex and queer partners, and friends the patient chooses.', 'इसमें पति-पत्नी, लिव-इन साथी, समलैंगिक और क्वीयर साथी, और मरीज़ के चुने हुए दोस्त शामिल हैं।')}><input id={`${id}-relation`} type="text" value={person.relation} onChange={(event) => set({ relation: event.target.value })} /></Field>
      <Field id={`${id}-phone`} label={t('Phone that works day and night', 'दिन-रात चालू रहने वाला फ़ोन')}><input id={`${id}-phone`} type="tel" inputMode="tel" autoComplete="off" value={person.phone24h} onChange={(event) => set({ phone24h: event.target.value })} /></Field>
      <Field id={`${id}-idtype`} label={t('ID type', 'पहचान पत्र')}>
        <select id={`${id}-idtype`} value={person.idType ?? ''} onChange={(event) => set({ idType: (event.target.value || null) as IdType | null })}>
          <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>
          {ID_OPTS.map((option) => <option key={option.value} value={option.value}>{t(option.en, option.hi)}</option>)}
        </select>
      </Field>
      <Field id={`${id}-last4`} label={t('Last 4 digits of the ID only', 'पहचान पत्र के सिर्फ़ आख़िरी 4 अंक')} hint={t('Never enter the full Aadhaar or ID number.', 'कभी भी पूरा आधार या आईडी नंबर न लिखें।')}>
        <input id={`${id}-last4`} type="text" inputMode="numeric" maxLength={4} autoComplete="off" value={person.idLast4 ?? ''} aria-describedby={`${id}-last4-hint`}
          onChange={(event) => { const digits = event.target.value.replace(/\D/g, '').slice(0, 4); set({ idLast4: digits || null }); }} />
      </Field>
    </div>
    <Choices t={t} name={`${id}-authority`} legend={t('Why this person can decide', 'यह व्यक्ति फ़ैसला क्यों ले सकते हैं')} value={person.authorityBasis} options={AUTHORITY_OPTS}
      onChange={(value) => set({ authorityBasis: value })} noneHint={t('Not recorded yet.', 'अभी दर्ज नहीं।')} />
    <button type="button" className="ep-link" onClick={() => onChange(null)}>{t('Remove this person', 'इन्हें हटाएँ')}</button>
  </fieldset>;
}

// ---------------------------------------------------------------------------
// Read-only summary (used for the active plan, drafts, and the finished screen)

function describe(t: T, lang: CareLang, record: EctprRecord) {
  const c = record.choices;
  const none = null;
  const opt = <V extends string>(list: Opt<V>[], value: V | null | undefined) => {
    const found = value ? list.find((item) => item.value === value) : undefined;
    return found ? t(found.en, found.hi) : none;
  };
  const trial = (plan: TrialPlan) => {
    const unit = UNIT_OPTS.find((item) => item.value === plan.durationUnit);
    const pieces = [
      `${t('Time-limited trial', 'समय-सीमित ट्रायल')}: ${plan.durationValue} ${unit ? t(unit.en, unit.hi) : ''}`.trim(),
      `${t('Review', 'समीक्षा')}: ${plan.reviewAt ? new Date(plan.reviewAt).toLocaleString(careLocale(lang, 'en-IN')) : t('not set', 'तय नहीं')}`,
      ...(plan.reviewers.length ? [`${t('Reviewed by', 'समीक्षा करेंगे')}: ${plan.reviewers.join(', ')}`] : []),
    ];
    return pieces.join(' · ');
  };
  const transfer = !c.hospitalTransfer ? none : c.hospitalTransfer.kind === 'ONLY_FOR'
    ? `${opt(TRANSFER_OPTS, 'ONLY_FOR')}: ${c.hospitalTransfer.reasons.map((reason) => t(TRANSFER_REASON_LABELS[reason], TRANSFER_REASON_HI[reason])).join('; ')}`
    : opt(TRANSFER_OPTS, c.hospitalTransfer.kind);
  const callFirst = c.callBeforeAmbulance.filter((n) => n.trim()).join(', ');
  const cpr = !c.cpr ? none : c.cpr === 'DNACPR'
    ? [opt(CPR_OPTS, 'DNACPR'), opt(DNACPR_REASONS, c.dnacprReason), c.dnacprDecidedBy?.name ? t(`Decided by ${c.dnacprDecidedBy.name} (${c.dnacprDecidedBy.registrationNo})`, `फ़ैसला: ${c.dnacprDecidedBy.name} (${c.dnacprDecidedBy.registrationNo})`) : null].filter(Boolean).join(' · ')
    : opt(CPR_OPTS, c.cpr);
  const ceiling = c.respCeiling === null ? none : [
    t(CEILINGS[c.respCeiling].en, CEILINGS[c.respCeiling].hi),
    c.maxOxygenLpm !== null ? t(`max ${c.maxOxygenLpm} L/min`, `ज़्यादा से ज़्यादा ${c.maxOxygenLpm} L/min`) : null,
    c.ceilingConditions.reversibleCauseException ? t('Exception: a new reversible cause gets full treatment', 'छूट: नई, ठीक होने वाली वजह का पूरा इलाज') : null,
    c.ceilingConditions.nivMaxHours !== null ? t(`NIV up to ${c.ceilingConditions.nivMaxHours} hours`, `NIV ${c.ceilingConditions.nivMaxHours} घंटे तक`) : null,
    c.ceilingConditions.spo2Floor !== null ? t(`SpO₂ target at least ${c.ceilingConditions.spo2Floor}%`, `SpO₂ कम से कम ${c.ceilingConditions.spo2Floor}%`) : null,
  ].filter(Boolean).join(' · ');
  const icu = !c.icu ? none : c.icu.kind === 'TRIAL' ? trial(c.icu.trial) : opt(ICU_OPTS, c.icu.kind);
  const vaso = !c.vasopressors ? none : c.vasopressors.kind === 'TRIAL' ? trial(c.vasopressors.trial)
    : c.vasopressors.kind === 'YES_MAX_AGENTS' ? t(`Yes, up to ${c.vasopressors.maxAgents} drug(s)`, `हाँ, ${c.vasopressors.maxAgents} दवा तक`) : opt(VASO_OPTS, c.vasopressors.kind);
  const dialysis = !c.dialysis ? none : c.dialysis.kind === 'TRIAL' ? trial(c.dialysis.trial) : opt(DIALYSIS_OPTS, c.dialysis.kind);
  const ng = !c.feeding.ng ? none : c.feeding.ng.kind === 'UP_TO_DAYS' ? t(`Try for up to ${c.feeding.ng.days} days, then review`, `${c.feeding.ng.days} दिन तक आज़माएँ, फिर समीक्षा`) : opt(NG_OPTS, c.feeding.ng.kind);
  const device = record.clinical.implantedDevice ? [opt(DEVICE_OPTS, record.clinical.implantedDevice),
    record.clinical.icdDeactivation.decision ? (record.clinical.icdDeactivation.decision === 'DEACTIVATE' ? t('Shocks to be switched off', 'झटके बंद किए जाएँगे') : t('Shocks kept on', 'झटके चालू रहेंगे')) : null,
  ].filter(Boolean).join(' · ') : none;
  const dm = record.decisionMakers.primary;
  const directive = record.existingDirective;
  return {
    emergency: [
      [t('Go to hospital?', 'अस्पताल जाएँ?'), transfer],
      [t('Call before an ambulance', 'एम्बुलेंस से पहले फ़ोन करें'), callFirst || none],
      [t('CPR', 'CPR'), cpr],
      [t('Highest breathing support', 'साँस की सबसे ज़्यादा मदद'), ceiling],
      [t('ICU', 'ICU'), icu],
      [t('Goal of care', 'देखभाल का लक्ष्य'), opt(GOALS, c.goalOfCare)],
      [t('Ambulance crew', 'एम्बुलेंस टीम'), opt(AMBULANCE_OPTS, c.ambulance)],
    ] as Array<[string, string | null]>,
    other: [
      [t('Blood-pressure drugs', 'ब्लड-प्रेशर की दवाएँ'), vaso],
      [t('Dialysis', 'डायलिसिस'), dialysis],
      [t('Nose tube feeding (NG)', 'नाक की नली से खाना (NG)'), ng],
      [t('Stomach tube (PEG)', 'पेट की नली (PEG)'), opt(PEG_OPTS, c.feeding.peg)],
      [t('Fluids', 'तरल'), opt(HYDRATION_OPTS, c.hydration)],
      [t('Allergies', 'एलर्जी'), record.clinical.allergies.trim() || none],
      [t('Implanted device', 'शरीर में लगा उपकरण'), device],
    ] as Array<[string, string | null]>,
    people: [
      [t('Person who decides if the patient cannot', 'मरीज़ न कर सकें तो फ़ैसला लेने वाले'), dm ? `${dm.name}${dm.relation ? ` (${dm.relation})` : ''}${dm.phone24h ? ` · ${dm.phone24h}` : ''}` : none],
      [t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले'), record.decisionMakers.alternate ? `${record.decisionMakers.alternate.name}${record.decisionMakers.alternate.relation ? ` (${record.decisionMakers.alternate.relation})` : ''}` : none],
      [t('Present for the discussion', 'बातचीत में मौजूद'), (record.decisionMakers.presentAtDiscussion ?? []).join('\n') || none],
      [t('Could not be reached', 'संपर्क नहीं हो सका'), (record.decisionMakers.attemptedContact ?? []).join('\n') || none],
      [t('Home care', 'घर पर देखभाल'), homeCareLines(record, lang).join('\n') || none],
      [t('Withdrawal or withholding (medical boards)', 'इलाज रोकना या शुरू न करना (मेडिकल बोर्ड)'), withdrawalLines(record, lang).join('\n') || none],
      [t('Ability to decide', 'फ़ैसला लेने की क्षमता'), opt(CAPACITY_OPTS, record.capacity.status)],
      [t('Living will', 'लिविंग विल'), directive ? opt(DIRECTIVE_OPTS, directive.kind) : none],
      [t('In the patient’s own words', 'मरीज़ के अपने शब्दों में'), record.values.ownWords.trim() || none],
      [t('Not acceptable to the patient', 'मरीज़ को जो मंज़ूर नहीं'), record.values.unacceptableOutcomes.filter((v) => v.trim()).join('\n') || none],
    ] as Array<[string, string | null]>,
  };
}

/** The reviewed conversation note, read-only. On a signed version it is locked with the rest of the record. */
function GocSavedNote({ t, record, showLines }: { t: T; record: EctprRecord; showLines: boolean }) {
  const saved = record.gocTranscript;
  if (!saved) return null;
  return <details className="ep-group ep-goc-saved-note">
    <summary>{t('Conversation note', 'बातचीत का नोट')} · {t(`reviewed by ${saved.reviewedBy}`, `समीक्षा: ${saved.reviewedBy}`)} · {saved.reviewedAt.slice(0, 10)}</summary>
    <p className="ep-goc-note-text" dir="auto">{saved.note || t('No note was written.', 'कोई नोट नहीं लिखा गया।')}</p>
    {showLines && saved.lines.length ? <details><summary>{t('Word-for-word transcript', 'शब्दशः लिखावट')}{saved.machineDraft ? ` · ${t('machine draft, corrected by the doctor', 'मशीन का ड्राफ़्ट, डॉक्टर ने ठीक किया')}` : ''}</summary>
      <ol className="ep-goc-verbatim">{saved.lines.map((line, index) => <li key={index} dir="auto">{line.speaker === 'DOCTOR' ? `${t('Doctor side', 'डॉक्टर की तरफ़')}: ` : line.speaker === 'PATIENT_PARTY' ? `${t('Patient side', 'मरीज़ की तरफ़')}: ` : ''}{line.text}</li>)}</ol>
    </details> : null}
  </details>;
}

function PlanSummary({ t, lang, record }: { t: T; lang: CareLang; record: EctprRecord }) {
  const groups = describe(t, lang, record);
  const body = useRef<HTMLDivElement>(null);
  const rows = (items: Array<[string, string | null]>) => <dl className="ep-summary">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? <span className="ep-np">{t('No preference recorded — standard care', 'कोई पसंद दर्ज नहीं — सामान्य इलाज')}</span>}</dd></div>)}</dl>;
  return <div ref={body}>
    <ReadAloudButton lang={lang} target={body} label={t('Read the plan aloud', 'योजना पढ़कर सुनाएँ')} />
    <section className="ep-summary-group" aria-label={t('Emergency choices', 'इमरजेंसी के फ़ैसले')}><h3>{t('In an emergency', 'इमरजेंसी में')}</h3>{rows(groups.emergency)}</section>
    <section className="ep-summary-group" aria-label={t('Other treatment', 'दूसरे इलाज')}><h3>{t('Other treatment', 'दूसरे इलाज')}</h3>{rows(groups.other)}</section>
    <section className="ep-summary-group" aria-label={t('People and wishes', 'लोग और इच्छाएँ')}><h3>{t('People and wishes', 'लोग और इच्छाएँ')}</h3>{rows(groups.people)}</section>
    <dl className="ep-summary">
      <div><dt>{t('Review due', 'समीक्षा की तारीख़')}</dt><dd>{record.reviewDue ? formatDay(lang, record.reviewDue) : <span className="ep-np">{t('Not set', 'तय नहीं')}</span>}</dd></div>
      <div><dt>{t('Version', 'संस्करण')}</dt><dd>{record.version} · {record.formVersion}</dd></div>
    </dl>
  </div>;
}

/** Informational only. The plan never authorises withholding or withdrawing on its own (research note: Common Cause 2023; Harish Rana 2026). */
function LegalPending({ t, lang }: { t: T; lang: CareLang }) {
  return <div className="ep-notice ep-warn" role="note">
    <p><strong>{t('Legal review pending', 'क़ानूनी समीक्षा बाक़ी')}</strong> — {t('for a patient who cannot decide, withholding or withdrawing life-sustaining treatment may need the Medical Board process (Common Cause 2023; Harish Rana 2026). This plan records wishes and clinical limits; it does not by itself legally authorise withholding treatment.', 'जो मरीज़ फ़ैसला नहीं ले सकते, उनके लिए जीवन-रक्षक इलाज शुरू न करने या रोकने के लिए मेडिकल बोर्ड की प्रक्रिया ज़रूरी हो सकती है (कॉमन कॉज़ 2023; हरीश राणा 2026)। यह योजना इच्छाएँ और इलाज की सीमाएँ दर्ज करती है; यह अपने आप में इलाज रोकने की क़ानूनी अनुमति नहीं देती।')}</p>
    <p>{boardReconsiderationNotice(lang)}</p>
    <details className="ep-board-steps">
      <summary>{t('See every step of the board process (information only)', 'बोर्ड प्रक्रिया के सभी चरण देखें (केवल जानकारी)')}</summary>
      <ul>{AMD_BOARD_PATHWAY.map((item) => <li key={item.en}>{t(item.en, item.hi)}</li>)}</ul>
    </details>
  </div>;
}

function AlwaysContinues({ t, lang }: { t: T; lang: CareLang }) {
  return <section className="ep-card ep-always" aria-labelledby="ep-always-title">
    <h2 id="ep-always-title">{t('Care that always continues', 'देखभाल जो हमेशा जारी रहती है')}</h2>
    <p>{t('Whatever this plan says, the patient always gets:', 'यह योजना कुछ भी कहे, मरीज़ को हमेशा मिलता है:')}</p>
    <ul>{alwaysProvidedCare(lang).map((line) => <li key={line}>{line}</li>)}</ul>
  </section>;
}

// ---------------------------------------------------------------------------
// Main screen

type Mode = { kind: 'overview' } | { kind: 'wizard' } | { kind: 'done'; record: EctprRecord };

export function EctprPlanScreen(props: { patientId: string; patientName: string; role: 'doctor' | 'patient' | 'family'; clinician: string; lang: CareLang }) {
  const { patientId, patientName, role, clinician, lang } = props;
  const t: T = useCallback((en: string, hi: string) => careText(lang, en, hi), [lang]);
  const actorName = role === 'doctor' ? (clinician.split('·')[0].trim() || clinician) : role === 'patient' ? patientName : 'Family member';

  const [versions, setVersions] = useState<EctprRecord[] | null>(null);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [mode, setMode] = useState<Mode>({ kind: 'overview' });
  const [draft, setDraft] = useState<EctprRecord | null>(null);
  const [step, setStep] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');

  // Load versions and audit; refresh whenever the store changes (this tab or another).
  useEffect(() => {
    let live = true;
    const load = () => {
      Promise.all([listEctprVersions(patientId), listAudit(patientId)]).then(([records, log]) => {
        if (!live) return;
        setVersions(records);
        setAudit(log);
      }).catch(() => { if (live) setVersions([]); });
    };
    load();
    const unsubscribe = subscribeEctpr(load);
    return () => { live = false; unsubscribe(); };
  }, [patientId]);

  const active = versions?.find((record) => record.status === 'ACTIVE') ?? null;
  const openDraft = versions?.find((record) => record.status === 'DRAFT') ?? null;

  // Log one VIEW per patient per session once a plan exists to view.
  useEffect(() => {
    const target = active ?? openDraft;
    if (!target) return;
    const key = `${patientId}:${role}:${target.id}`;
    if (auditViewed.has(key)) return;
    auditViewed.add(key);
    void logAudit({ actor: actorName, action: 'VIEW', recordType: 'ECTPR', recordId: target.id, patientId, detail: `Opened by ${role}` });
  }, [active, openDraft, patientId, role, actorName]);

  const issues = useMemo(() => (draft ? validateEctpr(draft) : []), [draft]);
  const saveBlocked = blocksSave(issues);

  const edit = useCallback((change: (record: EctprRecord) => void) => {
    setDraft((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      change(next);
      return next;
    });
    setDirty(true);
    setToast('');
  }, []);

  // Smooth scroll to the top of the step after it renders (like the hospital survey).
  const top = useRef<HTMLDivElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const jump = useRef(false);
  useEffect(() => {
    if (!jump.current) return;
    jump.current = false;
    const target = heading.current;
    if (target) target.focus({ preventScroll: true });
    const anchor = top.current;
    if (!anchor) return;
    const y = anchor.getBoundingClientRect().top + window.scrollY - 16;
    if (Math.abs(window.scrollY - y) > 8) window.scrollTo({ top: Math.max(0, y), behavior: 'smooth' });
  }, [step, mode]);

  async function persist(quiet: boolean): Promise<boolean> {
    if (!draft) return false;
    if (blocksSave(validateEctpr(draft))) { if (!quiet) setError(t('Fix the items marked “Must fix” before saving.', 'सेव करने से पहले “ठीक करें” वाले बिंदु सुधारें।')); return false; }
    setBusy(true);
    const result = await saveEctprDraft(cleanForSave(draft));
    setBusy(false);
    if (!result.ok || !result.record) { setError(storeError(t, result.error)); return false; }
    const saved = result.record;
    setDraft((current) => current && current.id === saved.id ? { ...current, signatures: saved.signatures, updatedAt: saved.updatedAt } : current);
    setDirty(false);
    setError('');
    if (!quiet) setToast(t('Draft saved. It is not in use until the consultant signs.', 'ड्राफ़्ट सेव हुआ। कंसल्टेंट के साइन तक यह लागू नहीं है।'));
    return true;
  }

  function goTo(next: number) {
    if (next < 0 || next >= STEPS.length) return;
    // Save quietly on the way, so nothing typed is lost. A draft that cannot be saved yet still lets you move.
    if (dirty && !saveBlocked) void persist(true);
    jump.current = true;
    setError('');
    setToast('');
    setStep(next);
  }

  function openWizard(record: EctprRecord, startStep = 0) {
    setDraft(structuredClone(record));
    setDirty(false);
    setError('');
    setToast('');
    jump.current = true;
    setStep(startStep);
    setMode({ kind: 'wizard' });
  }

  function startNew(inEd: boolean) {
    const record = emptyEctpr(patientId, { name: patientName, uhid: patientId }, actorName);
    const highest = versions?.reduce((max, item) => Math.max(max, item.version), 0) ?? 0;
    record.version = highest + 1;
    record.createdInEd = inEd;
    openWizard(record);
    setDirty(true);
  }

  async function beginReview(trigger: ReviewTrigger) {
    setBusy(true);
    const record = await startReview(patientId, trigger, actorName);
    setBusy(false);
    if (!record) { setError(t('There is no signed plan to review yet.', 'समीक्षा के लिए अभी कोई साइन की गई योजना नहीं है।')); return; }
    openWizard(record);
  }

  async function addSignature(signature: Signature): Promise<boolean> {
    if (!draft) return false;
    const known = versions?.some((record) => record.id === draft.id);
    if (dirty || !known) { const ok = await persist(true); if (!ok) { setError(storeError(t, 'Fix the items marked as blocking before saving.')); return false; } }
    setBusy(true);
    const result = await signEctpr(draft.id, signature);
    setBusy(false);
    if (!result.ok || !result.record) { setError(storeError(t, result.error)); return false; }
    const signed = result.record;
    setError('');
    if (signed.status === 'ACTIVE') {
      setDraft(null);
      jump.current = true;
      setMode({ kind: 'done', record: signed });
      return true;
    }
    setDraft((current) => current ? { ...current, signatures: signed.signatures } : current);
    setToast(t('Signature added.', 'हस्ताक्षर जुड़ गया।'));
    return true;
  }

  function closeWizard() {
    if (dirty && !saveBlocked) void persist(true);
    setDraft(null);
    jump.current = true;
    setMode({ kind: 'overview' });
  }

  if (versions === null) {
    return <section className="ectpr-plan" aria-busy="true"><div className="ep-card"><p>{t('Loading the emergency care plan…', 'इमरजेंसी देखभाल योजना खुल रही है…')}</p></div></section>;
  }

  if (mode.kind === 'done') {
    return <section className="ectpr-plan"><div ref={top} />
      <div className="ep-card ep-done">
        <span className="ep-done-mark" aria-hidden="true">✓</span>
        <h1 ref={heading} tabIndex={-1}>{t('The plan is signed and active', 'योजना साइन होकर लागू हो गई है')}</h1>
        <p>{t(`Version ${mode.record.version} is now the one plan in use for ${patientName}. Any older version is marked superseded and its card is withdrawn.`, `${patientName} के लिए अब संस्करण ${mode.record.version} ही लागू योजना है। पुराने संस्करण “पुराना हो गया” दिखेंगे और उनका कार्ड वापस ले लिया गया है।`)}</p>
        <p>{t('Give a signed copy to the family. Ambulance crews follow it only when they are handed a copy.', 'परिवार को साइन की हुई कॉपी दें। एम्बुलेंस टीम तभी मानती है जब उन्हें कॉपी दी जाए।')}</p>
        <div className="ep-actions"><button type="button" className="ep-btn" onClick={() => { jump.current = true; setMode({ kind: 'overview' }); }}>{t('See the plan and versions', 'योजना और संस्करण देखें')}</button></div>
      </div>
      <div className="ep-card"><h2>{t('What the plan says', 'योजना में क्या लिखा है')}</h2><PlanSummary t={t} lang={lang} record={mode.record} /><LegalPending t={t} lang={lang} /></div>
      <AlwaysContinues t={t} lang={lang} />
    </section>;
  }

  if (mode.kind === 'wizard' && draft && role === 'doctor') {
    return <Wizard t={t} lang={lang} draft={draft} previous={versions?.find((record) => record.id === draft.supersedes) ?? null} issues={issues} step={step} busy={busy} error={error} toast={toast} dirty={dirty}
      saveBlocked={saveBlocked} actorName={actorName} patientName={patientName} edit={edit} goTo={goTo} top={top} heading={heading}
      onSave={() => void persist(false)} onClose={closeWizard} onSign={addSignature} />;
  }

  return <Overview anchor={top} t={t} lang={lang} role={role} patientId={patientId} patientName={patientName} actorName={actorName}
    versions={versions} audit={audit} active={active} openDraft={openDraft} busy={busy} error={error} setError={setError}
    onStartNew={startNew} onContinueDraft={(record) => openWizard(record)} onStartReview={beginReview} onSign={async (record, signature) => {
      setBusy(true);
      const result = await signEctpr(record.id, signature);
      setBusy(false);
      if (!result.ok) { setError(storeError(t, result.error)); return false; }
      setError('');
      return true;
    }} />;
}

// ---------------------------------------------------------------------------
// Overview: active plan, versions, review, cancellation, audit

function Overview({ anchor, t, lang, role, patientId, patientName, actorName, versions, audit, active, openDraft, busy, error, setError, onStartNew, onContinueDraft, onStartReview, onSign }: {
  anchor: RefObject<HTMLDivElement | null>; t: T; lang: CareLang; role: Role; patientId: string; patientName: string; actorName: string;
  versions: EctprRecord[]; audit: AuditEvent[]; active: EctprRecord | null; openDraft: EctprRecord | null; busy: boolean; error: string; setError: (value: string) => void;
  onStartNew: (inEd: boolean) => void; onContinueDraft: (record: EctprRecord) => void; onStartReview: (trigger: ReviewTrigger) => Promise<void>;
  onSign: (record: EctprRecord, signature: Signature) => Promise<boolean>;
}) {
  const [panel, setPanel] = useState<'none' | 'new' | 'review' | 'request' | 'revoke' | 'sign'>('none');
  const [inEd, setInEd] = useState(false);
  const [trigger, setTrigger] = useState<ReviewTrigger | null>(null);
  const [reason, setReason] = useState('');
  const [signName, setSignName] = useState(role === 'patient' ? patientName : '');
  const [thumb, setThumb] = useState(false);
  const [notice, setNotice] = useState('');
  const outOfDate = active ? isOutOfDate(active) : false;
  const latestSigned = versions.find((record) => record.status !== 'DRAFT') ?? null;
  const requests = audit.filter((event) => event.action === 'REVIEW' && event.detail?.startsWith('Review requested'));
  const lastChange = versions.reduce((latest, record) => (record.updatedAt > latest ? record.updatedAt : latest), '');
  const pendingRequests = requests.filter((event) => event.at > lastChange);
  const patientSigned = openDraft?.signatures.some((s) => s.role === 'PATIENT' || s.role === 'SURROGATE') ?? false;

  async function requestReview() {
    if (!trigger) return;
    const label = reviewTriggers.find((item) => item.id === trigger)?.label ?? trigger;
    await logAudit({ actor: actorName, action: 'REVIEW', recordType: 'ECTPR', recordId: active?.id ?? latestSigned?.id ?? 'none', patientId, detail: `Review requested by ${role}: ${label}` });
    setPanel('none'); setTrigger(null);
    setNotice(t('Your request was sent to the care team. They will talk with you before changing anything.', 'आपका अनुरोध देखभाल टीम को भेज दिया गया है। कुछ भी बदलने से पहले वे आपसे बात करेंगे।'));
  }

  async function revoke() {
    if (!active || !reason.trim()) return;
    const result = await revokeEctpr(active.id, reason, actorName);
    if (!result.ok) { setError(storeError(t, result.error)); return; }
    setPanel('none'); setReason('');
    setNotice(t('The plan is cancelled. Its card is withdrawn and emergency teams will give standard care.', 'योजना रद्द हो गई। इसका कार्ड वापस ले लिया गया है और इमरजेंसी टीम सामान्य इलाज देगी।'));
  }

  const triggerPicker = (name: string) => <fieldset className="ep-group">
    <legend>{t('Why is a review needed?', 'समीक्षा क्यों चाहिए?')}</legend>
    <div className="ep-options">{reviewTriggers.map((item) => <label key={item.id} className="ep-option">
      <input type="radio" name={name} checked={trigger === item.id} onChange={() => setTrigger(item.id)} />
      <span><strong>{t(item.label, TRIGGER_HI[item.id])}</strong></span>
    </label>)}</div>
  </fieldset>;

  return <section className="ectpr-plan">
    <div ref={anchor} />
    <header className="ep-head">
      <div>
        <h1>{t('Emergency care plan', 'इमरजेंसी देखभाल योजना')}</h1>
        <p className="ep-sub">{patientName}{active ? ` · ${t('Version', 'संस्करण')} ${active.version}` : ''}</p>
      </div>
      {active ? <span className="ep-chip" data-status={outOfDate ? 'DUE' : 'ACTIVE'}>{outOfDate ? t('Review overdue', 'समीक्षा बाक़ी') : statusLabel(t, 'ACTIVE')}</span>
        : openDraft ? <span className="ep-chip" data-status="DRAFT">{statusLabel(t, 'DRAFT')}</span> : null}
    </header>

    <DpdpNotice scope="care-plan" lang={lang} patientId={patientId} actor={actorName} role={role} cardClass="ep-card" buttonClass="ep-btn ep-quiet" dangerClass="ep-btn ep-danger" />

    {notice ? <p className="ep-toast" role="status">{notice}</p> : null}
    {error ? <p className="ep-notice ep-alert" role="alert">{error}</p> : null}

    {role === 'doctor' && active && reviewReminderDue(active) ? <ReviewReminder t={t} lang={lang} record={active} actorName={actorName} /> : null}

    {role === 'doctor' && pendingRequests.length ? <div className="ep-notice ep-warn" role="status">
      <p><strong>{t('Review requested', 'समीक्षा का अनुरोध')}</strong></p>
      {pendingRequests.map((event) => <p key={event.id}>{event.actor} · {formatTime(lang, event.at)} · {event.detail?.replace(/^Review requested by \w+: /, '')}</p>)}
    </div> : null}

    {outOfDate ? <div className="ep-notice ep-alert"><p>{t('The review date has passed. Until it is reviewed, this plan is information only and emergency teams give standard care.', 'समीक्षा की तारीख़ निकल गई है। समीक्षा होने तक यह योजना सिर्फ़ जानकारी है और इमरजेंसी टीम सामान्य इलाज देगी।')}</p></div> : null}

    {/* Primary actions */}
    <div className="ep-card">
      {role === 'doctor' ? <>
        <h2>{openDraft ? t('A draft is waiting', 'एक ड्राफ़्ट बाक़ी है') : active ? t('This plan is in use', 'यह योजना लागू है') : t('No plan yet', 'अभी कोई योजना नहीं')}</h2>
        {openDraft ? <p>{t(`Version ${openDraft.version} was last saved on ${formatTime(lang, openDraft.updatedAt)}. It is not in use until the treating consultant signs.`, `संस्करण ${openDraft.version} आख़िरी बार ${formatTime(lang, openDraft.updatedAt)} को सेव हुआ। इलाज करने वाले कंसल्टेंट के साइन तक यह लागू नहीं है।`)}</p>
          : active ? <p>{t('A signed plan is never edited. To change it, start a review. That makes a new draft version.', 'साइन की गई योजना कभी बदली नहीं जाती। बदलने के लिए समीक्षा शुरू करें। इससे नया ड्राफ़्ट संस्करण बनता है।')}</p>
            : <p>{t('Make the plan at a calm time: a clinic visit, discharge after an admission, or when home palliative care starts.', 'योजना शांत समय में बनाएँ: क्लिनिक में, भर्ती के बाद छुट्टी के समय, या घर पर पैलिएटिव केयर शुरू होने पर।')}</p>}
        <div className="ep-actions">
          {openDraft ? <button type="button" className="ep-btn" onClick={() => onContinueDraft(openDraft)}>{t('Continue the draft', 'ड्राफ़्ट जारी रखें')}</button>
            : latestSigned ? <button type="button" className="ep-btn" onClick={() => { setPanel(panel === 'review' ? 'none' : 'review'); setTrigger(null); }} aria-expanded={panel === 'review'}>{t('Start a review', 'समीक्षा शुरू करें')}</button>
              : null}
          {!openDraft ? <button type="button" className={latestSigned ? 'ep-btn ep-quiet' : 'ep-btn'} onClick={() => setPanel(panel === 'new' ? 'none' : 'new')} aria-expanded={panel === 'new'}>{latestSigned ? t('Start a blank plan', 'खाली योजना शुरू करें') : t('Start a plan', 'योजना शुरू करें')}</button> : null}
          {active ? <button type="button" className="ep-btn ep-danger" onClick={() => setPanel(panel === 'revoke' ? 'none' : 'revoke')} aria-expanded={panel === 'revoke'}>{t('Cancel this plan', 'यह योजना रद्द करें')}</button> : null}
        </div>
        {panel === 'new' ? <div className="ep-dialog">
          <label className="ep-option"><input type="checkbox" checked={inEd} onChange={(event) => setInEd(event.target.checked)} />
            <span><strong>{t('I am starting this in the emergency department', 'मैं इसे इमरजेंसी विभाग में शुरू कर रहा/रही हूँ')}</strong></span></label>
          {inEd ? <div className="ep-notice ep-warn" role="alert"><p>{t('A plan made for the first time in the emergency room may record panic, not real wishes. If you can, give standard care now and make the plan when things are calm.', 'पहली बार इमरजेंसी में बनी योजना में असली इच्छा की जगह घबराहट दर्ज हो सकती है। हो सके तो अभी सामान्य इलाज दें और शांत समय में योजना बनाएँ।')}</p></div> : null}
          <div className="ep-actions"><button type="button" className="ep-btn" onClick={() => onStartNew(inEd)}>{inEd ? t('Start anyway', 'फिर भी शुरू करें') : t('Start', 'शुरू करें')}</button>
            <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel('none')}>{t('Not now', 'अभी नहीं')}</button></div>
        </div> : null}
        {panel === 'review' ? <div className="ep-dialog">
          {triggerPicker('ep-review-trigger')}
          <p className="ep-hint">{t('This copies the current plan into a new draft. The current plan stays in use until the new one is signed.', 'इससे मौजूदा योजना नए ड्राफ़्ट में कॉपी होती है। नया साइन होने तक मौजूदा योजना लागू रहती है।')}</p>
          <div className="ep-actions"><button type="button" className="ep-btn" disabled={!trigger || busy} onClick={() => trigger && void onStartReview(trigger)}>{t('Open the review draft', 'समीक्षा ड्राफ़्ट खोलें')}</button>
            <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel('none')}>{t('Not now', 'अभी नहीं')}</button></div>
        </div> : null}
      </> : <>
        <h2>{active ? t('Your plan is in use', 'आपकी योजना लागू है') : openDraft ? t('Your care team is preparing a plan', 'आपकी देखभाल टीम योजना बना रही है') : t('There is no signed plan yet', 'अभी कोई साइन की गई योजना नहीं है')}</h2>
        <p>{active ? t('This is what doctors and ambulance teams will follow in an emergency. Ask for a review any time your wishes or health change.', 'इमरजेंसी में डॉक्टर और एम्बुलेंस टीम यही मानेंगे। इच्छा या सेहत बदलने पर कभी भी समीक्षा माँगें।')
          : t('Until a doctor signs a plan, emergency teams give full standard care.', 'जब तक डॉक्टर योजना साइन न करें, इमरजेंसी टीम पूरा सामान्य इलाज देती है।')}</p>
        {role === 'patient' ? <p className="ep-hint">{t('You can change your mind at any time. If you can decide, what you say now counts more than this paper.', 'आप कभी भी अपना मन बदल सकते हैं। अगर आप फ़ैसला ले सकते हैं, तो अभी आप जो कहें वह इस काग़ज़ से ज़्यादा मायने रखता है।')}</p> : null}
        <div className="ep-actions">
          <button type="button" className="ep-btn" onClick={() => { setPanel(panel === 'request' ? 'none' : 'request'); setTrigger(null); }} aria-expanded={panel === 'request'}>{t('Ask for a review', 'समीक्षा माँगें')}</button>
          {role === 'patient' && openDraft && !patientSigned ? <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel(panel === 'sign' ? 'none' : 'sign')} aria-expanded={panel === 'sign'}>{t('Sign my part', 'अपना हिस्सा साइन करें')}</button> : null}
          {role === 'patient' && active ? <button type="button" className="ep-btn ep-danger" onClick={() => setPanel(panel === 'revoke' ? 'none' : 'revoke')} aria-expanded={panel === 'revoke'}>{t('Cancel my plan', 'मेरी योजना रद्द करें')}</button> : null}
        </div>
        {panel === 'request' ? <div className="ep-dialog">
          {triggerPicker('ep-request-trigger')}
          <div className="ep-actions"><button type="button" className="ep-btn" disabled={!trigger} onClick={() => void requestReview()}>{t('Send the request', 'अनुरोध भेजें')}</button>
            <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel('none')}>{t('Not now', 'अभी नहीं')}</button></div>
        </div> : null}
        {panel === 'sign' && openDraft ? <div className="ep-dialog">
          <p>{t('Read the draft below first. Signing shows you agree with what is written. The plan starts only after the treating consultant signs.', 'पहले नीचे ड्राफ़्ट पढ़ें। साइन करने का मतलब है कि आप लिखी बातों से सहमत हैं। योजना इलाज करने वाले कंसल्टेंट के साइन के बाद ही शुरू होती है।')}</p>
          <div className="ep-fields">
            <Field id="ep-patient-sign-name" label={t('Your full name', 'आपका पूरा नाम')}><input id="ep-patient-sign-name" type="text" value={signName} onChange={(event) => setSignName(event.target.value)} /></Field>
          </div>
          <label className="ep-option" style={{ marginTop: 10 }}><input type="checkbox" checked={thumb} onChange={(event) => setThumb(event.target.checked)} />
            <span><strong>{t('I am giving a thumb impression', 'मैं अँगूठे का निशान दे रहा/रही हूँ')}</strong><small>{t('Two witnesses will also need to sign.', 'दो गवाहों को भी साइन करना होगा।')}</small></span></label>
          <div className="ep-actions"><button type="button" className="ep-btn" disabled={!signName.trim() || busy} onClick={async () => {
            const ok = await onSign(openDraft, { role: 'PATIENT', name: signName.trim(), signedAt: new Date().toISOString(), ...(thumb ? { thumbImpression: true } : {}) });
            if (ok) { setPanel('none'); setNotice(t('Thank you. Your signature is on the draft.', 'धन्यवाद। ड्राफ़्ट पर आपका हस्ताक्षर हो गया।')); }
          }}>{t('Sign', 'साइन करें')}</button>
            <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel('none')}>{t('Not now', 'अभी नहीं')}</button></div>
        </div> : null}
      </>}
      {panel === 'revoke' && active ? <div className="ep-dialog">
        <p>{t('Cancelling withdraws the emergency card. Emergency teams will then give full standard care until a new plan is signed.', 'रद्द करने से इमरजेंसी कार्ड वापस हो जाता है। फिर नई योजना साइन होने तक इमरजेंसी टीम पूरा सामान्य इलाज देगी।')}</p>
        <Field id="ep-revoke-reason" label={t('Reason for cancelling', 'रद्द करने का कारण')}>
          <textarea id="ep-revoke-reason" rows={3} value={reason} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <div className="ep-actions"><button type="button" className="ep-btn ep-danger" disabled={!reason.trim() || busy} onClick={() => void revoke()}>{t(`Cancel version ${active.version}`, `संस्करण ${active.version} रद्द करें`)}</button>
          <button type="button" className="ep-btn ep-quiet" onClick={() => setPanel('none')}>{t('Keep the plan', 'योजना रखें')}</button></div>
      </div> : null}
    </div>

    {active ? <div className="ep-card"><h2>{t('What the plan says', 'योजना में क्या लिखा है')}</h2>
      <p className="ep-hint">{t('“No preference recorded” means standard care. It is never a refusal.', '“कोई पसंद दर्ज नहीं” का मतलब है सामान्य इलाज। यह कभी मना करना नहीं है।')}</p>
      <PlanSummary t={t} lang={lang} record={active} /><LegalPending t={t} lang={lang} />
      <GocSavedNote t={t} record={active} showLines={role === 'doctor'} />
      {role !== 'family' ? <div className="ep-actions"><FhirExportButton t={t} record={active} actorName={actorName} /></div> : null}</div> : null}
    {active && role !== 'family' ? <PaperAndCopies t={t} lang={lang} record={active} actorName={actorName} /> : null}
    {role !== 'doctor' && openDraft ? <div className="ep-card"><h2>{t(`Draft version ${openDraft.version} (not in use yet)`, `ड्राफ़्ट संस्करण ${openDraft.version} (अभी लागू नहीं)`)}</h2><PlanSummary t={t} lang={lang} record={openDraft} /></div> : null}

    <AlwaysContinues t={t} lang={lang} />

    <section className="ep-card" aria-labelledby="ep-versions-title">
      <h2 id="ep-versions-title">{t('Versions', 'संस्करण')}</h2>
      {versions.length === 0 ? <p className="ep-hint">{t('No versions yet.', 'अभी कोई संस्करण नहीं।')}</p> : <ol className="ep-versions">
        {versions.map((record) => {
          const consultant = record.signatures.find((s) => s.role === 'TREATING_CONSULTANT');
          return <li key={record.id} className="ep-version" aria-current={record.status === 'ACTIVE' || undefined}>
            <div className="ep-version-top"><strong>{t('Version', 'संस्करण')} {record.version}</strong><span className="ep-chip" data-status={record.status}>{statusLabel(t, record.status)}</span></div>
            <p>{t('Last changed', 'आख़िरी बदलाव')} {formatTime(lang, record.updatedAt)} · {t('started by', 'शुरू किया')} {record.createdBy}</p>
            {consultant ? <p>{t('Signed by', 'साइन किया')} {consultant.name} ({consultant.registrationNo}) · {formatTime(lang, consultant.signedAt)}</p> : null}
            {record.signatures.filter((s) => s.role !== 'TREATING_CONSULTANT').length ? <p>{t('Other signatures', 'दूसरे हस्ताक्षर')}: {record.signatures.filter((s) => s.role !== 'TREATING_CONSULTANT').map((s) => `${s.name} (${signatureRole(t, s.role)})`).join(', ')}</p> : null}
            {record.reviewDue && record.status !== 'REVOKED' ? <p>{t('Review due', 'समीक्षा की तारीख़')} {formatDay(lang, record.reviewDue)}</p> : null}
            {record.status === 'REVOKED' ? <p>{t('Cancelled', 'रद्द')} {record.revokedAt ? formatTime(lang, record.revokedAt) : ''}{record.revokedBy ? ` · ${record.revokedBy}` : ''}{record.revokedReason ? ` · ${record.revokedReason}` : ''}</p> : null}
            {record.createdInEd ? <p>{t('Started in the emergency department', 'इमरजेंसी विभाग में शुरू हुई')}</p> : null}
          </li>;
        })}
      </ol>}
    </section>

    <section className="ep-card" aria-labelledby="ep-audit-title">
      <h2 id="ep-audit-title">{t('Who opened or changed this plan', 'किसने यह योजना खोली या बदली')}</h2>
      {audit.length === 0 ? <p className="ep-hint">{t('Nothing recorded yet.', 'अभी कुछ दर्ज नहीं।')}</p> : <ol className="ep-audit">
        {[...audit].reverse().slice(0, 40).map((event) => <li key={event.id}>
          <time dateTime={event.at}>{formatTime(lang, event.at)}</time>
          <span><span className="ep-audit-action">{auditAction(t, event.action)}</span> · {event.actor}{event.detail ? ` · ${event.detail}` : ''}</span>
        </li>)}
      </ol>}
    </section>

    {role === 'doctor' ? <EctprPilotLog t={t} lang={lang} patientId={patientId} actorName={actorName} versions={versions} audit={audit} /> : null}
  </section>;
}

function auditAction(t: T, action: AuditEvent['action']): string {
  const map: Record<AuditEvent['action'], [string, string]> = {
    VIEW: ['Opened', 'खोला'], CREATE: ['Created', 'बनाया'], EDIT: ['Edited', 'बदला'], SIGN: ['Signed', 'साइन किया'], PRINT: ['Printed', 'प्रिंट किया'],
    SHARE: ['Shared', 'साझा किया'], REVOKE: ['Cancelled', 'रद्द किया'], BREAK_GLASS: ['Emergency access', 'आपात पहुँच'], REVIEW: ['Review', 'समीक्षा'], UPLOAD: ['Uploaded', 'अपलोड किया'], DELETE: ['Deleted', 'मिटाया'],
  };
  return t(map[action][0], map[action][1]);
}

// ---------------------------------------------------------------------------
// Wizard (doctor)

function Wizard({ t, lang, draft, previous, issues, step, busy, error, toast, dirty, saveBlocked, actorName, patientName, edit, goTo, top, heading, onSave, onClose, onSign }: {
  t: T; lang: CareLang; draft: EctprRecord; previous: EctprRecord | null; issues: RuleIssue[]; step: number; busy: boolean; error: string; toast: string; dirty: boolean; saveBlocked: boolean;
  actorName: string; patientName: string; edit: (change: (record: EctprRecord) => void) => void; goTo: (step: number) => void;
  top: RefObject<HTMLDivElement | null>; heading: RefObject<HTMLHeadingElement | null>;
  onSave: () => void; onClose: () => void; onSign: (signature: Signature) => Promise<boolean>;
}) {
  // The signatures step lists what still blocks activation itself (computed with the consultant's signature in place).
  const stepIssues = step === STEPS.length - 1 ? [] : issues.filter((item) => item.id !== 'V1' && stepForField(item.field) === step);
  const bad = (field: string) => issues.some((item) => item.field === field && item.severity !== 'warn') || undefined;
  const locked = treatmentLocked(draft);
  const surrogateLocked = surrogateEntriesLocked(draft);
  const c = draft.choices;
  const progress = (step + 1) / STEPS.length;
  const stepTitle = t(STEPS[step].en, STEPS[step].hi);
  const stepBody = useRef<HTMLDivElement>(null);
  const [gocOpen, setGocOpen] = useState(false);
  const gocEntry = <div className="ep-notice ep-info ep-goc-entry">
    <p><strong>{t('Goals-of-care conversation', 'देखभाल के लक्ष्यों की बातचीत')}</strong></p>
    <button type="button" className="ep-btn" onClick={() => setGocOpen(true)}>{t('Record the conversation', 'बातचीत रिकॉर्ड करें')}</button>
  </div>;

  const lockNotice = (locked || surrogateLocked) ? <div className="ep-notice ep-warn ep-lock" role="status">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><rect x="5" y="10.5" width="14" height="10" rx="2" /><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" /></svg>
    <div>
      {locked ? <p>{t('Treatment choices are locked. First record that the illness has been explained as not curable and the family can say it back.', 'इलाज के फ़ैसले बंद हैं। पहले दर्ज करें कि बीमारी के बारे में समझाया गया है कि यह ठीक नहीं हो सकती, और परिवार इसे दोहरा सकता है।')}</p> : null}
      {surrogateLocked ? <p>{t('A person is answering for the patient. Their promise to say what the patient would choose must be confirmed first.', 'कोई मरीज़ की ओर से जवाब दे रहे हैं। पहले उनका वादा पक्का करें कि वे वही बताएँगे जो मरीज़ चुनते।')}</p> : null}
      <button type="button" className="ep-link" onClick={() => goTo(locked ? 3 : 2)}>{locked ? t('Go to Understanding', 'समझ वाले हिस्से पर जाएँ') : t('Go to the declaration', 'वादे वाले हिस्से पर जाएँ')}</button>
    </div>
  </div> : null;

  // Consistency conditions (mirror rules V2, V3, C1, C2, C3) so the reason / confirm fields stay visible once filled.
  const goalA = c.goalOfCare === 'A_LIFE'; const goalC = c.goalOfCare === 'C_COMFORT';
  const ceiling = c.respCeiling;
  const pressorsYes = c.vasopressors?.kind === 'YES_NO_LIMIT' || c.vasopressors?.kind === 'YES_MAX_AGENTS';
  const icuWanted = c.icu?.kind === 'YES' || c.icu?.kind === 'TRIAL';
  const v2 = c.cpr === 'ATTEMPT' && (goalC || (ceiling !== null && ceiling < 5));
  const v3 = goalA && ((ceiling !== null && ceiling < 5) || c.icu?.kind === 'NO');
  const c1 = c.hospitalTransfer?.kind === 'NO' && (icuWanted || (ceiling !== null && ceiling >= 3));
  const c2 = goalC && (c.icu?.kind === 'YES' || pressorsYes || c.dialysis?.kind === 'YES');
  const c3 = (ceiling === 5 || pressorsYes) && c.icu?.kind === 'NO';
  const reasonField = (key: 'C1' | 'C2' | 'C3', label: string) => <Field id={`ep-reason-${key}`} label={label} wide>
    <textarea id={`ep-reason-${key}`} rows={2} value={c.conflictReasons[key] ?? ''} aria-invalid={!(c.conflictReasons[key] ?? '').trim() || undefined}
      onChange={(event) => edit((r) => { r.choices.conflictReasons[key] = event.target.value; })} />
  </Field>;

  const trialOf = (option: Icu | Vasopressors | Dialysis | null): TrialPlan | null => option && option.kind === 'TRIAL' ? option.trial : null;

  return <section className="ectpr-plan">
    <div ref={top} />
    <header className="ep-head">
      <div><h1>{t('Emergency care plan', 'इमरजेंसी देखभाल योजना')}</h1>
        <p className="ep-sub">{patientName} · {t('Version', 'संस्करण')} {draft.version} · <span className="ep-chip" data-status="DRAFT">{statusLabel(t, 'DRAFT')}</span></p></div>
      <button type="button" className="ep-btn ep-quiet" onClick={onClose}>{t('Close', 'बंद करें')}</button>
    </header>

    <div className="ep-progress">
      <div className="ep-progress-top"><strong>{stepTitle}</strong><span>{t(`Step ${step + 1} of ${STEPS.length}`, `चरण ${step + 1} / ${STEPS.length}`)}</span></div>
      <div className="ep-progress-track" role="progressbar" aria-label={t('Plan progress', 'योजना की प्रगति')} aria-valuemin={1} aria-valuemax={STEPS.length} aria-valuenow={step + 1}>
        <div className="ep-progress-fill" style={{ width: `${progress * 100}%` }} />
      </div>
      <ol className="ep-steps" aria-label={t('Steps', 'चरण')}>
        {STEPS.map((item, index) => <li key={item.en}><button type="button" aria-current={index === step ? 'step' : undefined} data-done={index < step || undefined}
          onClick={() => goTo(index)}>{index + 1}. {t(item.en, item.hi)}</button></li>)}
      </ol>
    </div>

    {toast ? <p className="ep-toast" role="status">{toast}</p> : null}
    {error ? <p className="ep-notice ep-alert" role="alert">{error}</p> : null}

    {gocOpen ? <Suspense fallback={<p className="ep-hint" role="status">{t('Loading…', 'लोड हो रहा है…')}</p>}>
      <GocTranscribe t={t} lang={lang} draft={draft} previous={previous} actorName={actorName} patientId={draft.patientId} edit={edit} options={GOC_OPTIONS} onClose={() => setGocOpen(false)} onGoToSign={() => { setGocOpen(false); goTo(STEPS.length - 1); }} />
    </Suspense> : null}

    <form className="ep-card" noValidate onSubmit={(event) => { event.preventDefault(); goTo(step + 1); }}>
      <div key={step} className="ep-step" ref={stepBody}>
        <div className="ep-step-title"><h2 ref={heading} tabIndex={-1}>{stepTitle}</h2><span>{t(`Step ${step + 1} of ${STEPS.length}`, `चरण ${step + 1} / ${STEPS.length}`)}</span><ReadAloudButton lang={lang} target={stepBody} /></div>
        <IssueList t={t} lang={lang} issues={stepIssues} record={draft} />

        {step === 0 ? <>
          <fieldset className="ep-group"><legend>{t('About the patient', 'मरीज़ के बारे में')}</legend>
            <div className="ep-fields">
              <Field id="ep-name" label={t('Full name', 'पूरा नाम')}><input id="ep-name" type="text" value={draft.patient.name} aria-invalid={!draft.patient.name.trim() || undefined} onChange={(event) => edit((r) => { r.patient.name = event.target.value; })} /></Field>
              <Field id="ep-uhid" label={t('Hospital number (UHID)', 'अस्पताल नंबर (UHID)')}><input id="ep-uhid" type="text" value={draft.patient.uhid} aria-invalid={!draft.patient.uhid.trim() || undefined} onChange={(event) => edit((r) => { r.patient.uhid = event.target.value; })} /></Field>
              <Field id="ep-dob" label={t('Date of birth', 'जन्म तिथि')} hint={t('Only for adults, 18 or older.', 'केवल वयस्कों के लिए, 18 साल या ज़्यादा।')}><input id="ep-dob" type="date" value={draft.patient.dob} aria-invalid={bad('patient.dob')} onChange={(event) => edit((r) => { r.patient.dob = event.target.value; })} /></Field>
              <Field id="ep-sex" label={t('Sex', 'लिंग')}>
                <select id="ep-sex" value={draft.patient.sex} onChange={(event) => edit((r) => { r.patient.sex = event.target.value; })}>
                  <option value="">{t('Not recorded', 'दर्ज नहीं')}</option><option value="F">{t('Female', 'महिला')}</option><option value="M">{t('Male', 'पुरुष')}</option><option value="O">{t('Other', 'अन्य')}</option>
                </select>
              </Field>
              <Field id="ep-phone" label={t('Phone', 'फ़ोन')}><input id="ep-phone" type="tel" inputMode="tel" value={draft.patient.phone} onChange={(event) => edit((r) => { r.patient.phone = event.target.value; })} /></Field>
              <Field id="ep-gender" label={t('Gender', 'जेंडर')}><input id="ep-gender" type="text" value={draft.patient.gender ?? ''} onChange={(event) => edit((r) => { r.patient.gender = event.target.value; })} /></Field>
              <Field id="ep-lang" label={t('Preferred language', 'पसंद की भाषा')}>
                <select id="ep-lang" value={draft.patient.languagePref} onChange={(event) => edit((r) => { r.patient.languagePref = event.target.value; })}>
                  <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>
                  {LANGUAGE_OPTS.map(([code, en, hi]) => <option key={code} value={code}>{t(en, hi)}</option>)}
                </select>
              </Field>
              <Field id="ep-address" label={t('Address', 'पता')} wide><textarea id="ep-address" rows={2} value={draft.patient.address} onChange={(event) => edit((r) => { r.patient.address = event.target.value; })} /></Field>
            </div>
          </fieldset>
          <fieldset className="ep-group"><legend>{t('Illness', 'बीमारी')}</legend>
            <div className="ep-fields">
              <Field id="ep-diagnosis" label={t('Diagnosis', 'रोग')}><input id="ep-diagnosis" type="text" value={draft.clinical.diagnosis} onChange={(event) => edit((r) => { r.clinical.diagnosis = event.target.value; })} /></Field>
              <Field id="ep-stage" label={t('Stage', 'स्टेज')}><input id="ep-stage" type="text" value={draft.clinical.stage} onChange={(event) => edit((r) => { r.clinical.stage = event.target.value; })} /></Field>
              <Field id="ep-ecog" label={t('ECOG (0 active to 4 in bed all day)', 'ECOG (0 सक्रिय से 4 पूरा दिन बिस्तर पर)')}>
                <select id="ep-ecog" value={draft.clinical.ecog ?? ''} onChange={(event) => edit((r) => { r.clinical.ecog = event.target.value === '' ? null : Number(event.target.value) as 0 | 1 | 2 | 3 | 4; })}>
                  <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>{[0, 1, 2, 3, 4].map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
              </Field>
              <Field id="ep-eligible" label={t('Date found eligible for this plan', 'इस योजना के योग्य पाए जाने की तारीख़')}><input id="ep-eligible" type="date" value={draft.clinical.eligibilityDate} onChange={(event) => edit((r) => { r.clinical.eligibilityDate = event.target.value; })} /></Field>
              <Field id="ep-meds" label={t('Key medicines', 'मुख्य दवाइयाँ')} wide><textarea id="ep-meds" rows={2} value={draft.clinical.keyMedicines} onChange={(event) => edit((r) => { r.clinical.keyMedicines = event.target.value; })} /></Field>
            </div>
          </fieldset>
          <fieldset className="ep-group"><legend>{t('Care team', 'देखभाल टीम')}</legend>
            <div className="ep-fields">
              <Field id="ep-onc" label={t('Main treating doctor or specialist (oncologist or any other)', 'मुख्य इलाज करने वाले डॉक्टर या विशेषज्ञ (कैंसर या कोई और)')}><input id="ep-onc" type="text" value={draft.careTeam.oncologist} onChange={(event) => edit((r) => { r.careTeam.oncologist = event.target.value; })} /></Field>
              <Field id="ep-pall" label={t('Palliative care doctor', 'पैलिएटिव केयर डॉक्टर')}><input id="ep-pall" type="text" value={draft.careTeam.palliativePhysician} onChange={(event) => edit((r) => { r.careTeam.palliativePhysician = event.target.value; })} /></Field>
              <Field id="ep-pall-phone" label={t('Palliative team phone (day and night)', 'पैलिएटिव टीम का फ़ोन (दिन-रात)')}><input id="ep-pall-phone" type="tel" inputMode="tel" value={draft.careTeam.palliativePhone24h} onChange={(event) => edit((r) => { r.careTeam.palliativePhone24h = event.target.value; })} /></Field>
              <Field id="ep-local" label={t('Local doctor', 'स्थानीय डॉक्टर')}><input id="ep-local" type="text" value={draft.careTeam.localDoctor} onChange={(event) => edit((r) => { r.careTeam.localDoctor = event.target.value; })} /></Field>
              <Field id="ep-extra" label={t('Others in the team (one per line)', 'टीम के दूसरे लोग (हर लाइन में एक)')} wide><textarea id="ep-extra" rows={2} value={listToLines(draft.careTeam.extraTeam)} onChange={(event) => edit((r) => { r.careTeam.extraTeam = linesToList(event.target.value); })} /></Field>
            </div>
          </fieldset>
          {draft.createdInEd ? <div className="ep-notice ep-warn"><p>{t('This plan was started in the emergency department.', 'यह योजना इमरजेंसी विभाग में शुरू हुई।')}</p></div> : null}
        </> : null}

        {step === 1 ? <>
          <p className="ep-hint">{t('Who speaks for the patient if they cannot decide? Store only the ID type and last 4 digits.', 'अगर मरीज़ फ़ैसला न ले सकें तो उनकी ओर से कौन बोलेगा? सिर्फ़ पहचान पत्र का प्रकार और आख़िरी 4 अंक रखें।')}</p>
          <DecisionMakerForm t={t} id="ep-dm1" title={t('Primary decision-maker', 'मुख्य फ़ैसला लेने वाले')} person={draft.decisionMakers.primary} onChange={(person) => edit((r) => { r.decisionMakers.primary = person; })} />
          <DecisionMakerForm t={t} id="ep-dm2" title={t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले')} person={draft.decisionMakers.alternate} onChange={(person) => edit((r) => { r.decisionMakers.alternate = person; })} />
          <fieldset className="ep-group"><legend>{t('Family and friends', 'परिवार और दोस्त')}</legend>
            <div className="ep-fields">
              <Field id="ep-present" label={t('Family or friends present for this discussion, with their relationship (one per line)', 'इस बातचीत में मौजूद परिवार या दोस्त, रिश्ते के साथ (हर लाइन में एक)')} hint={t('Includes spouses, live-in partners, same-sex and queer partners, and friends the patient chooses.', 'इसमें पति-पत्नी, लिव-इन साथी, समलैंगिक और क्वीयर साथी, और मरीज़ के चुने हुए दोस्त शामिल हैं।')} wide><textarea id="ep-present" rows={3} value={listToLines(draft.decisionMakers.presentAtDiscussion ?? [])} aria-describedby="ep-present-hint" onChange={(event) => edit((r) => { r.decisionMakers.presentAtDiscussion = linesToList(event.target.value); })} /></Field>
              <Field id="ep-inform" label={t('Family or friends who must be informed before any major decision: name, relation, phone (one per line)', 'जिन परिवारजनों या दोस्तों को बड़े फ़ैसले से पहले बताना ज़रूरी है: नाम, रिश्ता, फ़ोन (हर लाइन में एक)')} wide><textarea id="ep-inform" rows={3} value={listToLines(draft.decisionMakers.familyToInform)} onChange={(event) => edit((r) => { r.decisionMakers.familyToInform = linesToList(event.target.value); })} /></Field>
              <Field id="ep-attempted" label={t('Family or friends we tried to contact but could not reach (one per line)', 'जिन परिवारजनों या दोस्तों से संपर्क की कोशिश हुई पर बात नहीं हो सकी (हर लाइन में एक)')} wide><textarea id="ep-attempted" rows={2} value={listToLines(draft.decisionMakers.attemptedContact ?? [])} onChange={(event) => edit((r) => { r.decisionMakers.attemptedContact = linesToList(event.target.value); })} /></Field>
              <Field id="ep-consensus" label={t('Family agreement (a note, not a legal basis)', 'परिवार की सहमति (एक नोट, क़ानूनी आधार नहीं)')} hint={t('If the family disagrees: hold one family meeting, go back to what the patient would want, and ask the ethics committee if still unresolved. Current treatment continues meanwhile.', 'अगर परिवार असहमत हो: एक पारिवारिक बैठक करें, देखें कि मरीज़ क्या चाहते, और फिर भी हल न हो तो एथिक्स कमेटी से पूछें। तब तक मौजूदा इलाज जारी रहे।')} wide>
                <textarea id="ep-consensus" rows={3} value={draft.decisionMakers.familyConsensusNote} aria-describedby="ep-consensus-hint" onChange={(event) => edit((r) => { r.decisionMakers.familyConsensusNote = event.target.value; })} />
              </Field>
            </div>
          </fieldset>
        </> : null}

        {step === 2 ? <>
          <CapacityGuide lang={lang} />
          <Choices t={t} name="ep-capacity" legend={t('Can the patient make this decision today?', 'क्या मरीज़ आज यह फ़ैसला ले सकते हैं?')} hint={t('Check it now, for this decision. Do not use old notes.', 'अभी, इसी फ़ैसले के लिए जाँचें। पुराने नोट का इस्तेमाल न करें।')}
            value={draft.capacity.status} options={CAPACITY_OPTS} invalid={bad('capacity.status')} noneHint={t('Not checked yet.', 'अभी नहीं जाँचा।')}
            onChange={(value) => edit((r) => { r.capacity.status = value; })}>
            <div className="ep-sub-fields">
              <AbilityBasis t={t} basis={draft.capacity.basis} onChange={(basis) => edit((r) => { r.capacity.basis = basis; })} invalid={bad('capacity')} />
              <div className="ep-fields" style={{ marginTop: 12 }}>
                <Field id="ep-assessor" label={t('Checked by', 'किसने जाँचा')}><input id="ep-assessor" type="text" value={draft.capacity.assessor} aria-invalid={bad('capacity')} onChange={(event) => edit((r) => { r.capacity.assessor = event.target.value; })} /></Field>
                <Field id="ep-assessed" label={t('Checked on', 'कब जाँचा')}><input id="ep-assessed" type="date" value={draft.capacity.assessedOn} aria-invalid={bad('capacity')} onChange={(event) => edit((r) => { r.capacity.assessedOn = event.target.value; })} /></Field>
                {draft.capacity.status === 'FLUCTUATES' ? <Field id="ep-reassess" label={t('Check again on', 'दोबारा कब जाँचें')}><input id="ep-reassess" type="date" value={draft.capacity.reassessOn} aria-invalid={bad('capacity.reassessOn')} onChange={(event) => edit((r) => { r.capacity.reassessOn = event.target.value; })} /></Field> : null}
              </div>
            </div>
          </Choices>
          <Choices t={t} name="ep-directive" legend={t('Does the patient have a living will (AMD)?', 'क्या मरीज़ की लिविंग विल (AMD) है?')} value={draft.existingDirective?.kind ?? null} options={DIRECTIVE_OPTS} noneHint={t('Not asked yet.', 'अभी नहीं पूछा।')}
            onChange={(kind) => edit((r) => {
              r.existingDirective = kind === null ? null : kind === 'VALID_AMD' ? { kind, date: '', location: '', attestor: '' } : kind === 'PRIOR_WISHES' ? { kind, note: '' } : { kind: 'NONE' };
            })}>
            {draft.existingDirective?.kind === 'VALID_AMD' ? <div className="ep-sub-fields"><div className="ep-fields">
              <Field id="ep-amd-date" label={t('Date signed', 'साइन की तारीख़')}><input id="ep-amd-date" type="date" value={draft.existingDirective.date} onChange={(event) => edit((r) => { if (r.existingDirective?.kind === 'VALID_AMD') r.existingDirective.date = event.target.value; })} /></Field>
              <Field id="ep-amd-place" label={t('Where the original is kept', 'मूल कॉपी कहाँ रखी है')}><input id="ep-amd-place" type="text" value={draft.existingDirective.location} onChange={(event) => edit((r) => { if (r.existingDirective?.kind === 'VALID_AMD') r.existingDirective.location = event.target.value; })} /></Field>
              <Field id="ep-amd-attestor" label={t('Attested by (notary or gazetted officer)', 'सत्यापित करने वाले (नोटरी या राजपत्रित अधिकारी)')} wide><input id="ep-amd-attestor" type="text" value={draft.existingDirective.attestor} onChange={(event) => edit((r) => { if (r.existingDirective?.kind === 'VALID_AMD') r.existingDirective.attestor = event.target.value; })} /></Field>
            </div></div> : draft.existingDirective?.kind === 'PRIOR_WISHES' ? <div className="ep-sub-fields">
              <Field id="ep-prior" label={t('What the patient said or wrote before', 'मरीज़ ने पहले क्या कहा या लिखा')}><textarea id="ep-prior" rows={3} value={draft.existingDirective.note} onChange={(event) => edit((r) => { if (r.existingDirective?.kind === 'PRIOR_WISHES') r.existingDirective.note = event.target.value; })} /></Field>
            </div> : null}
          </Choices>
          <fieldset className="ep-group" aria-invalid={bad('surrogateDeclaration.confirmed')}><legend>{t('Is someone answering for the patient?', 'क्या कोई मरीज़ की ओर से जवाब दे रहे हैं?')}</legend>
            <label className="ep-option"><input type="checkbox" checked={draft.surrogateDeclaration.answeringAsSurrogate} onChange={(event) => edit((r) => { r.surrogateDeclaration.answeringAsSurrogate = event.target.checked; if (!event.target.checked) { r.surrogateDeclaration.confirmed = false; r.surrogateDeclaration.confirmedOn = ''; } })} />
              <span><strong>{t('Yes, a decision-maker is answering for the patient', 'हाँ, फ़ैसला लेने वाले मरीज़ की ओर से जवाब दे रहे हैं')}</strong></span></label>
            {draft.surrogateDeclaration.answeringAsSurrogate ? <div className="ep-sub-fields">
              <label className="ep-option"><input type="checkbox" checked={draft.surrogateDeclaration.confirmed} onChange={(event) => edit((r) => { r.surrogateDeclaration.confirmed = event.target.checked; r.surrogateDeclaration.confirmedOn = event.target.checked ? new Date().toISOString() : ''; })} />
                <span><strong>{t('They promise to say what the patient would have chosen, not what they want themselves.', 'वे वादा करते हैं कि वही बताएँगे जो मरीज़ चुनते, न कि जो वे ख़ुद चाहते हैं।')}</strong><small>{t('Where the patient’s wish is not known, decide together with the team in the patient’s best interest.', 'जहाँ मरीज़ की इच्छा पता न हो, टीम के साथ मिलकर मरीज़ के भले के लिए फ़ैसला करें।')}</small></span></label>
            </div> : null}
          </fieldset>
        </> : null}

        {step === 3 ? <>
          <p>{t('Before any treatment choice, the patient or family must understand the illness. A choice made without this is not informed.', 'किसी भी इलाज के फ़ैसले से पहले मरीज़ या परिवार को बीमारी समझनी चाहिए। इसके बिना लिया गया फ़ैसला सोच-समझकर नहीं होता।')}</p>
          <fieldset className="ep-group" aria-invalid={bad('prognosticUnderstanding.confirmed')}><legend>{t('Understanding the illness', 'बीमारी की समझ')}</legend>
            <label className="ep-option"><input type="checkbox" checked={draft.prognosticUnderstanding.confirmed} onChange={(event) => edit((r) => {
              r.prognosticUnderstanding.confirmed = event.target.checked;
              r.prognosticUnderstanding.confirmedOn = event.target.checked ? (r.prognosticUnderstanding.confirmedOn || new Date().toISOString().slice(0, 10)) : '';
              r.prognosticUnderstanding.confirmedBy = event.target.checked ? (r.prognosticUnderstanding.confirmedBy || actorName) : '';
            })} />
              <span><strong>{t('The illness has been explained as not curable, and the family can say it back in their own words.', 'बीमारी के बारे में समझा दिया गया है कि यह ठीक नहीं हो सकती, और परिवार इसे अपने शब्दों में दोहरा सकता है।')}</strong>
                <small>{t('Treatment steps stay locked until this is ticked.', 'जब तक यह टिक न हो, इलाज वाले चरण बंद रहेंगे।')}</small></span></label>
            {draft.prognosticUnderstanding.confirmed ? <div className="ep-fields" style={{ marginTop: 12 }}>
              <Field id="ep-pu-by" label={t('Confirmed by', 'किसने पुष्टि की')}><input id="ep-pu-by" type="text" value={draft.prognosticUnderstanding.confirmedBy} onChange={(event) => edit((r) => { r.prognosticUnderstanding.confirmedBy = event.target.value; })} /></Field>
              <Field id="ep-pu-on" label={t('Confirmed on', 'कब पुष्टि की')}><input id="ep-pu-on" type="date" value={draft.prognosticUnderstanding.confirmedOn.slice(0, 10)} onChange={(event) => edit((r) => { r.prognosticUnderstanding.confirmedOn = event.target.value; })} /></Field>
            </div> : null}
          </fieldset>
        </> : null}

        {step === 4 ? <>
          {gocOpen ? null : gocEntry}
          {surrogateLocked ? lockNotice : null}
          <fieldset className="ep-group" disabled={surrogateLocked}><legend>{t('What matters to the patient', 'मरीज़ के लिए क्या ज़रूरी है')}</legend>
            <YesNo t={t} name="ep-knows" legend={t('Does the patient know about their illness?', 'क्या मरीज़ अपनी बीमारी के बारे में जानते हैं?')} value={draft.values.patientKnows} onChange={(value) => edit((r) => { r.values.patientKnows = value; })} />
            <div className="ep-fields">
              <Field id="ep-knows-when" label={t('When was this recorded?', 'यह कब दर्ज किया गया?')}>
                <select id="ep-knows-when" value={draft.values.knowsRecordedWhen ?? ''} onChange={(event) => edit((r) => { r.values.knowsRecordedWhen = (event.target.value || null) as EctprRecord['values']['knowsRecordedWhen']; })}>
                  <option value="">{t('Not recorded', 'दर्ज नहीं')}</option><option value="BEFORE_COUNSELLING">{t('Before counselling', 'काउंसलिंग से पहले')}</option><option value="AFTER_COUNSELLING">{t('After counselling', 'काउंसलिंग के बाद')}</option>
                </select>
              </Field>
              <Field id="ep-own-words" label={t('In the patient’s own words', 'मरीज़ के अपने शब्दों में')} hint={t('Write exactly what they said. Do not change their words.', 'ठीक वही लिखें जो उन्होंने कहा। उनके शब्द न बदलें।')} wide>
                <textarea id="ep-own-words" rows={4} value={draft.values.ownWords} aria-describedby="ep-own-words-hint" onChange={(event) => edit((r) => { r.values.ownWords = event.target.value; })} />
              </Field>
              <Field id="ep-unacceptable" label={t('Outcomes the patient would not accept (one per line)', 'जो हालत मरीज़ को मंज़ूर नहीं (हर लाइन में एक)')} wide>
                <textarea id="ep-unacceptable" rows={3} value={listToLines(draft.values.unacceptableOutcomes)} onChange={(event) => edit((r) => { r.values.unacceptableOutcomes = linesToList(event.target.value); })} />
              </Field>
            </div>
          </fieldset>
          <fieldset className="ep-group" disabled={surrogateLocked}><legend>{t('Telling the patient', 'मरीज़ को बताना')}</legend>
            <label className="ep-option"><input type="checkbox" checked={draft.values.familyAskedNotToTell} onChange={(event) => edit((r) => { r.values.familyAskedNotToTell = event.target.checked; })} />
              <span><strong>{t('The family asked that the patient not be told', 'परिवार ने कहा कि मरीज़ को न बताया जाए')}</strong><small>{t('This is common and is not a reason to stop making the plan.', 'ऐसा अक्सर होता है और यह योजना रोकने का कारण नहीं है।')}</small></span></label>
            {draft.values.familyAskedNotToTell ? <div className="ep-sub-fields">
              <Field id="ep-direct" label={t('If the patient asks directly, how will we answer honestly?', 'अगर मरीज़ सीधे पूछें, तो हम सच कैसे बताएँगे?')}>
                <textarea id="ep-direct" rows={3} value={draft.values.directQuestionPlan} aria-invalid={bad('values.directQuestionPlan')} onChange={(event) => edit((r) => { r.values.directQuestionPlan = event.target.value; })} />
              </Field>
            </div> : null}
          </fieldset>
          <fieldset className="ep-group" disabled={surrogateLocked}><legend>{t('Money worries', 'पैसे की चिंता')}</legend>
            <p className="ep-hint">{t('In families who pay themselves, a limit may be about money, not wishes. Ask kindly and directly.', 'जो परिवार ख़ुद ख़र्च उठाते हैं, उनमें सीमा इच्छा की बजाय पैसे की वजह से हो सकती है। प्यार से और सीधे पूछें।')}</p>
            <YesNo t={t} name="ep-fin" legend={t('Was money worry talked about?', 'क्या पैसे की चिंता पर बात हुई?')} value={draft.values.financialConcernDiscussed} onChange={(value) => edit((r) => { r.values.financialConcernDiscussed = value; })} />
            <YesNo t={t} name="ep-sw" legend={t('Referred to social work or a health-scheme check?', 'क्या सोशल वर्क या सरकारी योजना की जाँच के लिए भेजा?')} value={draft.values.socialWorkReferral} onChange={(value) => edit((r) => { r.values.socialWorkReferral = value; })} />
          </fieldset>
        </> : null}

        {step === 5 ? <>
          {gocOpen ? null : gocEntry}
          {lockNotice}
          <LegalPending t={t} lang={lang} />
          <Choices t={t} name="ep-goal" legend={t('Goal of care', 'देखभाल का लक्ष्य')} value={c.goalOfCare} options={GOALS} disabled={locked || surrogateLocked} onChange={(value) => edit((r) => { r.choices.goalOfCare = value; })} />
          <Choices t={t} name="ep-cpr" legend={t('CPR if the heart and breathing stop', 'दिल और साँस रुकने पर CPR')} hint={t('This is only about cardiac arrest. It says nothing about any other treatment.', 'यह सिर्फ़ दिल रुकने के बारे में है। यह किसी दूसरे इलाज के बारे में कुछ नहीं कहता।')}
            value={c.cpr} options={CPR_OPTS} cols disabled={locked || surrogateLocked} onChange={(value) => edit((r) => { r.choices.cpr = value; if (value !== 'DNACPR') { r.choices.dnacprReason = null; } })}>
            {c.cpr === 'DNACPR' ? <div className="ep-sub-fields">
              <Choices t={t} name="ep-dnacpr-reason" legend={t('Reason for DNACPR', 'DNACPR का कारण')} value={c.dnacprReason} options={DNACPR_REASONS} invalid={bad('choices.dnacprReason')} noneHint={t('Not chosen yet.', 'अभी नहीं चुना।')} onChange={(value) => edit((r) => { r.choices.dnacprReason = value; })} />
              <div className="ep-fields">
                <Field id="ep-dnacpr-with" label={t('Discussed with', 'किससे बात हुई')} wide><input id="ep-dnacpr-with" type="text" value={c.dnacprDiscussedWith} aria-invalid={bad('choices.dnacprDiscussedWith')} onChange={(event) => edit((r) => { r.choices.dnacprDiscussedWith = event.target.value; })} /></Field>
                <Field id="ep-dnacpr-by" label={t('Decided by (doctor’s name)', 'फ़ैसला किसने लिया (डॉक्टर का नाम)')}><input id="ep-dnacpr-by" type="text" value={c.dnacprDecidedBy?.name ?? ''} aria-invalid={bad('choices.dnacprDecidedBy')} onChange={(event) => edit((r) => { r.choices.dnacprDecidedBy = { name: event.target.value, registrationNo: r.choices.dnacprDecidedBy?.registrationNo ?? '', decidedAt: r.choices.dnacprDecidedBy?.decidedAt ?? '' }; })} /></Field>
                <Field id="ep-dnacpr-reg" label={t('Registration number', 'रजिस्ट्रेशन नंबर')}><input id="ep-dnacpr-reg" type="text" value={c.dnacprDecidedBy?.registrationNo ?? ''} aria-invalid={bad('choices.dnacprDecidedBy')} onChange={(event) => edit((r) => { r.choices.dnacprDecidedBy = { name: r.choices.dnacprDecidedBy?.name ?? '', registrationNo: event.target.value, decidedAt: r.choices.dnacprDecidedBy?.decidedAt ?? '' }; })} /></Field>
                <Field id="ep-dnacpr-at" label={t('Date and time decided', 'फ़ैसले की तारीख़ और समय')}><input id="ep-dnacpr-at" type="datetime-local" value={toLocalInput(c.dnacprDecidedBy?.decidedAt ?? '')} aria-invalid={bad('choices.dnacprDecidedBy')} onChange={(event) => edit((r) => { r.choices.dnacprDecidedBy = { name: r.choices.dnacprDecidedBy?.name ?? '', registrationNo: r.choices.dnacprDecidedBy?.registrationNo ?? '', decidedAt: fromLocalInput(event.target.value) }; })} /></Field>
              </div>
              <button type="button" className="ep-link" onClick={() => edit((r) => { r.choices.dnacprDecidedBy = { name: actorName, registrationNo: r.choices.dnacprDecidedBy?.registrationNo ?? '', decidedAt: new Date().toISOString() }; })}>{t('I made this decision now', 'यह फ़ैसला मैंने अभी लिया')}</button>
            </div> : null}
          </Choices>
          <Choices t={t} name="ep-transfer" legend={t('Go to hospital if things get worse?', 'हालत बिगड़ने पर अस्पताल जाएँ?')} value={c.hospitalTransfer?.kind ?? null} options={TRANSFER_OPTS} disabled={locked || surrogateLocked}
            onChange={(kind) => edit((r) => { r.choices.hospitalTransfer = kind === null ? null : kind === 'ONLY_FOR' ? { kind, reasons: r.choices.hospitalTransfer?.kind === 'ONLY_FOR' ? r.choices.hospitalTransfer.reasons : [] } : { kind }; })}>
            {c.hospitalTransfer?.kind === 'ONLY_FOR' ? <fieldset className="ep-group ep-sub-fields" aria-invalid={bad('choices.hospitalTransfer.reasons')}><legend>{t('Go to hospital only for', 'सिर्फ़ इनके लिए अस्पताल जाएँ')}</legend>
              <div className="ep-options">{(Object.keys(TRANSFER_REASON_LABELS) as TransferReason[]).map((reason) => {
                const transfer = c.hospitalTransfer;
                const checked = transfer?.kind === 'ONLY_FOR' && transfer.reasons.includes(reason);
                return <label key={reason} className="ep-option"><input type="checkbox" checked={checked} onChange={(event) => edit((r) => {
                  if (r.choices.hospitalTransfer?.kind !== 'ONLY_FOR') return;
                  const set = new Set(r.choices.hospitalTransfer.reasons);
                  if (event.target.checked) set.add(reason); else set.delete(reason);
                  r.choices.hospitalTransfer.reasons = (Object.keys(TRANSFER_REASON_LABELS) as TransferReason[]).filter((key) => set.has(key));
                })} /><span><strong>{t(TRANSFER_REASON_LABELS[reason], TRANSFER_REASON_HI[reason])}</strong></span></label>;
              })}</div>
            </fieldset> : null}
            <div className="ep-sub-fields">
              <Field id="ep-call-first" label={t('Phone numbers to call before an ambulance (one per line)', 'एम्बुलेंस से पहले फ़ोन करने के नंबर (हर लाइन में एक)')} hint={t('For example, the palliative team’s day-and-night number.', 'जैसे पैलिएटिव टीम का दिन-रात वाला नंबर।')}>
                <textarea id="ep-call-first" rows={2} value={listToLines(c.callBeforeAmbulance)} aria-describedby="ep-call-first-hint" onChange={(event) => edit((r) => { r.choices.callBeforeAmbulance = linesToList(event.target.value); })} />
              </Field>
            </div>
          </Choices>
          <Choices t={t} name="ep-ambulance" legend={t('What the ambulance crew should do', 'एम्बुलेंस टीम क्या करे')} hint={t('Crews resuscitate unless they are handed a signed copy of this plan.', 'जब तक इस योजना की साइन की हुई कॉपी न मिले, टीम पूरा इलाज (CPR) करती है।')} value={c.ambulance} options={AMBULANCE_OPTS} disabled={locked || surrogateLocked} onChange={(value) => edit((r) => { r.choices.ambulance = value; })} />
          <fieldset className="ep-group" disabled={locked || surrogateLocked}><legend>{t('Highest breathing support allowed', 'साँस की सबसे ज़्यादा मदद जिसकी अनुमति है')}</legend>
            <p className="ep-hint">{t('The level you tick is the highest allowed. All lower levels can still be used. Nothing higher starts without a new talk with the family and the treating consultant.', 'आप जो स्तर चुनें वह सबसे ऊँचा है। उससे नीचे के सभी स्तर इस्तेमाल हो सकते हैं। उससे ऊपर कुछ भी परिवार और कंसल्टेंट से नई बात के बिना शुरू नहीं होगा।')}</p>
            <div className="ep-options">
              {CEILINGS.map((level) => <label key={level.value} className="ep-option"><input type="radio" name="ep-ceiling" checked={c.respCeiling === level.value} onChange={() => edit((r) => { r.choices.respCeiling = level.value; })} />
                <span><strong>{t(level.en, level.hi)}</strong><small>{t(level.helpEn, level.helpHi)}</small></span></label>)}
              <label className="ep-option ep-none"><input type="radio" name="ep-ceiling" checked={c.respCeiling === null} onChange={() => edit((r) => { r.choices.respCeiling = null; })} />
                <span><strong>{t('No preference recorded', 'कोई पसंद दर्ज नहीं')}</strong><small>{t('Standard care will be given. This is never read as a refusal.', 'सामान्य इलाज दिया जाएगा। इसे कभी मना करना नहीं माना जाता।')}</small></span></label>
            </div>
            {c.respCeiling !== null ? <div className="ep-sub-fields">
              <div className="ep-fields">
                {c.respCeiling >= 1 ? <Field id="ep-max-o2" label={t('Highest oxygen flow (litres per minute)', 'ऑक्सीजन का सबसे ज़्यादा बहाव (लीटर प्रति मिनट)')}><input id="ep-max-o2" type="number" min={0} step={0.5} inputMode="decimal" value={c.maxOxygenLpm ?? ''} aria-invalid={bad('choices.maxOxygenLpm')} onChange={(event) => edit((r) => { r.choices.maxOxygenLpm = toNumber(event.target.value); })} /></Field> : null}
                <Field id="ep-spo2" label={t('Lowest oxygen level (SpO₂ %) to aim for', 'ऑक्सीजन का सबसे कम स्तर (SpO₂ %) जिसका लक्ष्य रखें')}><input id="ep-spo2" type="number" min={50} max={100} inputMode="numeric" value={c.ceilingConditions.spo2Floor ?? ''} onChange={(event) => edit((r) => { r.choices.ceilingConditions.spo2Floor = toNumber(event.target.value); })} /></Field>
                {c.respCeiling >= 4 ? <Field id="ep-niv-hours" label={t('NIV trial: up to how many hours', 'NIV ट्रायल: ज़्यादा से ज़्यादा कितने घंटे')}><input id="ep-niv-hours" type="number" min={1} inputMode="numeric" value={c.ceilingConditions.nivMaxHours ?? ''} aria-invalid={bad('choices.ceilingConditions.nivMaxHours')} onChange={(event) => edit((r) => { r.choices.ceilingConditions.nivMaxHours = toNumber(event.target.value); })} /></Field> : null}
                {c.hospitalTransfer?.kind === 'NO' && c.respCeiling >= 3 ? <Field id="ep-home-niv" label={t('Why breathing support at home is planned (for example, home NIV)', 'घर पर साँस की मदद क्यों तय है (जैसे घर पर NIV)')} wide><textarea id="ep-home-niv" rows={2} value={c.ceilingConditions.homeNivReason} onChange={(event) => edit((r) => { r.choices.ceilingConditions.homeNivReason = event.target.value; })} /></Field> : null}
              </div>
              <YesNo t={t} name="ep-reversible" legend={t('If a new, reversible problem happens (for example choking or a drug reaction), treat it fully even above this level?', 'अगर नई, ठीक होने वाली समस्या हो (जैसे दम घुटना या दवा का असर), तो क्या इस स्तर से ऊपर भी पूरा इलाज करें?')}
                value={c.ceilingConditions.reversibleCauseException} onChange={(value) => edit((r) => { r.choices.ceilingConditions.reversibleCauseException = value; })} />
            </div> : null}
          </fieldset>
          {v2 || v3 || c1 ? <fieldset className="ep-group" disabled={locked || surrogateLocked}><legend>{t('Does the plan fit together?', 'क्या योजना आपस में मेल खाती है?')}</legend>
            {v2 ? <Field id="ep-cpr-reason" label={t('Why CPR is still to be attempted', 'CPR की कोशिश फिर भी क्यों करनी है')} wide><textarea id="ep-cpr-reason" rows={2} value={c.cprConflictReason} aria-invalid={!c.cprConflictReason.trim() || undefined} onChange={(event) => edit((r) => { r.choices.cprConflictReason = event.target.value; })} /></Field> : null}
            {v3 ? <label className="ep-option" style={{ marginTop: 10 }}><input type="checkbox" checked={c.goalAConfirmed} onChange={(event) => edit((r) => { r.choices.goalAConfirmed = event.target.checked; })} />
              <span><strong>{t('I have checked: the goal is to prioritise life, with these limits on breathing support or ICU.', 'मैंने जाँच लिया: लक्ष्य जीवन को प्राथमिकता है, साँस की मदद या ICU पर इन सीमाओं के साथ।')}</strong></span></label> : null}
            {c1 ? reasonField('C1', t('Why “do not go to hospital” goes with high-flow oxygen, NIV or ICU', '“अस्पताल न जाएँ” के साथ हाई-फ़्लो ऑक्सीजन, NIV या ICU क्यों')) : null}
          </fieldset> : null}
          <HomeCareFields t={t} draft={draft} edit={edit} />
        </> : null}

        {step === 6 ? <>
          {lockNotice}
          <LegalPending t={t} lang={lang} />
          <Choices t={t} name="ep-icu" legend={t('Intensive care (ICU)', 'गहन चिकित्सा (ICU)')} value={c.icu?.kind ?? null} options={ICU_OPTS} disabled={locked || surrogateLocked}
            onChange={(kind) => edit((r) => { r.choices.icu = kind === null ? null : kind === 'TRIAL' ? { kind, trial: trialOf(r.choices.icu) ?? defaultTrialPlan() } : { kind }; })}>
            {c.icu?.kind === 'TRIAL' ? <TrialEditor t={t} id="ep-icu-trial" trial={c.icu.trial} onChange={(trial) => edit((r) => { r.choices.icu = { kind: 'TRIAL', trial }; })} /> : null}
          </Choices>
          <Choices t={t} name="ep-vaso" legend={t('Drugs to support blood pressure (vasopressors)', 'ब्लड-प्रेशर सहारा देने वाली दवाएँ (वैसोप्रेसर)')} value={c.vasopressors?.kind ?? null} options={VASO_OPTS} disabled={locked || surrogateLocked}
            onChange={(kind) => edit((r) => {
              r.choices.vasopressors = kind === null ? null : kind === 'TRIAL' ? { kind, trial: trialOf(r.choices.vasopressors) ?? defaultTrialPlan() }
                : kind === 'YES_MAX_AGENTS' ? { kind, maxAgents: r.choices.vasopressors?.kind === 'YES_MAX_AGENTS' ? r.choices.vasopressors.maxAgents : 1 } : { kind };
            })}>
            {c.vasopressors?.kind === 'TRIAL' ? <TrialEditor t={t} id="ep-vaso-trial" trial={c.vasopressors.trial} onChange={(trial) => edit((r) => { r.choices.vasopressors = { kind: 'TRIAL', trial }; })} /> : null}
            {c.vasopressors?.kind === 'YES_MAX_AGENTS' ? <div className="ep-sub-fields"><Field id="ep-vaso-max" label={t('Highest number of drugs', 'ज़्यादा से ज़्यादा दवाएँ')}>
              <input id="ep-vaso-max" type="number" min={1} inputMode="numeric" value={c.vasopressors.maxAgents || ''} aria-invalid={bad('choices.vasopressors.maxAgents')} onChange={(event) => edit((r) => { r.choices.vasopressors = { kind: 'YES_MAX_AGENTS', maxAgents: toNumber(event.target.value) ?? 0 }; })} />
            </Field></div> : null}
          </Choices>
          <Choices t={t} name="ep-dialysis" legend={t('Dialysis (kidney machine)', 'डायलिसिस (गुर्दे की मशीन)')} value={c.dialysis?.kind ?? null} options={DIALYSIS_OPTS} disabled={locked || surrogateLocked}
            onChange={(kind) => edit((r) => { r.choices.dialysis = kind === null ? null : kind === 'TRIAL' ? { kind, trial: trialOf(r.choices.dialysis) ?? { ...defaultTrialPlan(), durationUnit: 'SESSIONS' } } : { kind }; })}>
            {c.dialysis?.kind === 'TRIAL' ? <TrialEditor t={t} id="ep-dialysis-trial" trial={c.dialysis.trial} onChange={(trial) => edit((r) => { r.choices.dialysis = { kind: 'TRIAL', trial }; })} /> : null}
          </Choices>
          <fieldset className="ep-group" disabled={locked || surrogateLocked}><legend>{t('Feeding', 'खाना')}</legend>
            <div className="ep-notice ep-info"><p>{t('Food and drink by mouth for comfort, and mouth care, always continue. Tube feeding is a medical treatment and is a choice.', 'आराम के लिए मुँह से खाना-पीना और मुँह की देखभाल हमेशा जारी रहती है। नली से खाना एक मेडिकल इलाज है और यह एक विकल्प है।')}</p></div>
            <Choices t={t} name="ep-ng" legend={t('Nose tube (NG)', 'नाक की नली (NG)')} value={c.feeding.ng?.kind ?? null} options={NG_OPTS}
              onChange={(kind) => edit((r) => { r.choices.feeding.ng = kind === null ? null : kind === 'UP_TO_DAYS' ? { kind, days: r.choices.feeding.ng?.kind === 'UP_TO_DAYS' ? r.choices.feeding.ng.days : 7 } : { kind }; })}>
              {c.feeding.ng?.kind === 'UP_TO_DAYS' ? <div className="ep-sub-fields"><Field id="ep-ng-days" label={t('Number of days before review', 'समीक्षा से पहले कितने दिन')}>
                <input id="ep-ng-days" type="number" min={1} inputMode="numeric" value={c.feeding.ng.days || ''} aria-invalid={bad('choices.feeding.ng.days')} onChange={(event) => edit((r) => { r.choices.feeding.ng = { kind: 'UP_TO_DAYS', days: toNumber(event.target.value) ?? 0 }; })} />
              </Field></div> : null}
            </Choices>
            <Choices t={t} name="ep-peg" legend={t('Stomach tube (PEG)', 'पेट की नली (PEG)')} value={c.feeding.peg} options={PEG_OPTS} onChange={(value) => edit((r) => { r.choices.feeding.peg = value; })} />
            <label className="ep-option"><input type="checkbox" checked={c.feeding.removeExistingTube} onChange={(event) => edit((r) => { r.choices.feeding.removeExistingTube = event.target.checked; })} />
              <span><strong>{t('The patient or family wish to remove a feeding tube that is already in place', 'मरीज़ या परिवार पहले से लगी खाने की नली निकलवाना चाहते हैं')}</strong></span></label>
            {c.feeding.removeExistingTube ? <div className="ep-notice ep-alert" role="note" style={{ marginTop: 10 }}>
              <p><strong>{t('This needs the medical board process', 'इसके लिए मेडिकल बोर्ड की प्रक्रिया ज़रूरी है')}</strong></p>
              <p>{t('Removing a tube that is already in place is stopping treatment. It needs a Primary and a Secondary Medical Board (each usually within 48 hours) and a message to the local magistrate (JMFC). This plan records the wish only. It cannot be used to remove the tube, and the app never starts a withdrawal.', 'पहले से लगी नली निकालना इलाज रोकना है। इसके लिए प्राथमिक और द्वितीय मेडिकल बोर्ड (हर एक आमतौर पर 48 घंटे में) और स्थानीय मजिस्ट्रेट (JMFC) को सूचना ज़रूरी है। यह योजना सिर्फ़ इच्छा दर्ज करती है। इससे नली नहीं निकाली जा सकती, और ऐप कभी भी इलाज रोकना शुरू नहीं करता।')}</p>
              <p>{boardReconsiderationNotice(lang)}</p>
              <p>{t('Legal review pending — for a patient who cannot decide, withholding or withdrawing life-sustaining treatment may need the Medical Board process (Common Cause 2023; Harish Rana 2026).', 'क़ानूनी समीक्षा बाक़ी — जो मरीज़ फ़ैसला नहीं ले सकते, उनके लिए जीवन-रक्षक इलाज शुरू न करने या रोकने के लिए मेडिकल बोर्ड की प्रक्रिया ज़रूरी हो सकती है (कॉमन कॉज़ 2023; हरीश राणा 2026)।')}</p>
            </div> : null}
          </fieldset>
          <Choices t={t} name="ep-hydration" legend={t('Fluids (drip or under the skin)', 'तरल (ड्रिप या त्वचा के नीचे)')} value={c.hydration} options={HYDRATION_OPTS} disabled={locked || surrogateLocked} onChange={(value) => edit((r) => { r.choices.hydration = value; })} />
          <fieldset className="ep-group"><legend>{t('Allergies and devices', 'एलर्जी और उपकरण')}</legend>
            <Field id="ep-allergies" label={t('Allergies (write “none known” if none)', 'एलर्जी (कोई न हो तो “कोई ज्ञात नहीं” लिखें)')}><textarea id="ep-allergies" rows={2} value={draft.clinical.allergies} onChange={(event) => edit((r) => { r.clinical.allergies = event.target.value; })} /></Field>
            <div style={{ marginTop: 12 }}>
              <Choices t={t} name="ep-device" legend={t('Implanted heart device', 'दिल में लगा उपकरण')} value={draft.clinical.implantedDevice} options={DEVICE_OPTS} cols noneHint={t('Not recorded yet.', 'अभी दर्ज नहीं।')}
                onChange={(value) => edit((r) => { r.clinical.implantedDevice = value; if (value !== 'ICD' && value !== 'CRT_D') r.clinical.icdDeactivation = { decision: null, date: '' }; })}>
                {draft.clinical.implantedDevice === 'ICD' || draft.clinical.implantedDevice === 'CRT_D' ? <div className="ep-sub-fields">
                  <Choices t={t} name="ep-icd" legend={t('Shocks from the device at the end of life', 'जीवन के अंत में उपकरण के झटके')} value={draft.clinical.icdDeactivation.decision} invalid={bad('clinical.icdDeactivation')} cols noneHint={t('Not decided yet.', 'अभी तय नहीं।')}
                    options={[{ value: 'DEACTIVATE', en: 'Switch shocks off', hi: 'झटके बंद करें' }, { value: 'KEEP', en: 'Keep shocks on', hi: 'झटके चालू रखें' }]}
                    onChange={(value) => edit((r) => { r.clinical.icdDeactivation.decision = value; })} />
                  <Field id="ep-icd-date" label={t('Date decided', 'फ़ैसले की तारीख़')}><input id="ep-icd-date" type="date" value={draft.clinical.icdDeactivation.date} onChange={(event) => edit((r) => { r.clinical.icdDeactivation.date = event.target.value; })} /></Field>
                </div> : null}
              </Choices>
            </div>
          </fieldset>
          {c2 || c3 ? <fieldset className="ep-group" disabled={locked || surrogateLocked}><legend>{t('Does the plan fit together?', 'क्या योजना आपस में मेल खाती है?')}</legend>
            {c2 ? reasonField('C2', t('Why ICU, blood-pressure drugs or dialysis with a comfort goal', 'आराम के लक्ष्य के साथ ICU, ब्लड-प्रेशर दवा या डायलिसिस क्यों')) : null}
            {c3 ? reasonField('C3', t('Why a breathing tube or blood-pressure drugs without ICU', 'ICU के बिना साँस की नली या ब्लड-प्रेशर दवा क्यों')) : null}
          </fieldset> : null}
        </> : null}

        {step === 7 ? <WithdrawalFields t={t} lang={lang} draft={draft} edit={edit} invalid={bad('withdrawal')} /> : null}

        {step === 8 ? <>
          <IssueList t={t} lang={lang} issues={issues.filter((item) => item.id === 'V1')} record={draft} />
          <fieldset className="ep-group" aria-invalid={bad('reviewDue')}><legend>{t('When must this plan be reviewed?', 'इस योजना की समीक्षा कब ज़रूरी है?')}</legend>
            <div className="ep-fields">
              <Field id="ep-review-due" label={t('Review by', 'समीक्षा की तारीख़')}><input id="ep-review-due" type="date" value={draft.reviewDue} aria-invalid={bad('reviewDue')} onChange={(event) => edit((r) => { r.reviewDue = event.target.value; })} /></Field>
            </div>
            <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" onClick={() => edit((r) => { r.reviewDue = nextReviewDue(new Date()); })}>{t(`Set the routine review (${ROUTINE_REVIEW_WEEKS} weeks from today)`, `नियमित समीक्षा तय करें (आज से ${ROUTINE_REVIEW_WEEKS} हफ़्ते)`)}</button></div>
          </fieldset>
          <div className="ep-notice ep-info"><p><strong>{t('Review the plan sooner if any of these happens:', 'इनमें से कुछ भी हो तो पहले समीक्षा करें:')}</strong></p>
            <ul>{reviewTriggers.map((item) => <li key={item.id}>{t(item.label, TRIGGER_HI[item.id])}</li>)}</ul>
            <p>{t('After the review date, emergency teams treat the plan as information only and give standard care.', 'समीक्षा की तारीख़ के बाद इमरजेंसी टीम योजना को सिर्फ़ जानकारी मानती है और सामान्य इलाज देती है।')}</p>
          </div>
          <AlwaysContinues t={t} lang={lang} />
        </> : null}

        {step === 9 && draft.gocTranscript ? <GocSavedNote t={t} record={draft} showLines /> : null}
        {step === 9 ? <SignStep t={t} lang={lang} draft={draft} actorName={actorName} busy={busy} dirty={dirty} saveBlocked={saveBlocked} edit={edit} onSign={onSign} />
          : null}
      </div>

      <div className="ep-nav">
        <div>{step > 0 ? <button type="button" className="ep-btn ep-quiet" onClick={() => goTo(step - 1)}><span aria-hidden="true">←</span> {t('Back', 'पीछे')}</button> : null}</div>
        <div>
          <button type="button" className="ep-btn ep-quiet" disabled={busy || saveBlocked || !dirty} onClick={onSave}
            title={saveBlocked ? t('Fix the items marked “Must fix” to save', 'सेव करने के लिए “ठीक करें” वाले बिंदु सुधारें') : undefined}>{busy ? t('Saving…', 'सेव हो रहा है…') : dirty ? t('Save draft', 'ड्राफ़्ट सेव करें') : t('Saved', 'सेव हो गया')}</button>
          {step < STEPS.length - 1 ? <button type="submit" className="ep-btn">{t('Continue', 'आगे बढ़ें')} <span aria-hidden="true">→</span></button> : null}
        </div>
      </div>
      {saveBlocked ? <p className="ep-hint" role="status" style={{ marginTop: 12 }}>{t('This draft cannot be saved until every “Must fix” item is fixed. You can still move between steps.', 'जब तक हर “ठीक करें” बिंदु न सुधरे, यह ड्राफ़्ट सेव नहीं होगा। आप फिर भी चरणों के बीच जा सकते हैं।')}</p> : null}
    </form>
  </section>;
}

function AbilityBasis({ t, basis, onChange, invalid }: { t: T; basis: string; onChange: (basis: string) => void; invalid?: boolean }) {
  // Stored as readable text: "Abilities checked: understand, retain…" on the first line, notes after.
  const prefix = 'Abilities checked:';
  const [firstLine, ...rest] = basis.split('\n');
  const hasLine = firstLine.startsWith(prefix);
  const checked = new Set(hasLine ? firstLine.slice(prefix.length).split(',').map((item) => item.trim()).filter(Boolean) : []);
  const notes = hasLine ? rest.join('\n') : basis;
  const write = (nextChecked: Set<string>, nextNotes: string) => {
    const ordered = ABILITIES.map((item) => item.key).filter((key) => nextChecked.has(key));
    const lines: string[] = [];
    if (ordered.length) lines.push(`${prefix} ${ordered.join(', ')}`);
    if (nextNotes.trim() !== '') lines.push(nextNotes);
    onChange(lines.join('\n'));
  };
  return <fieldset className="ep-group" aria-invalid={invalid}>
    <legend>{t('How it was checked (the four abilities)', 'कैसे जाँचा (चार क्षमताएँ)')}</legend>
    <div className="ep-options ep-cols">{ABILITIES.map((item) => <label key={item.key} className="ep-option"><input type="checkbox" checked={checked.has(item.key)}
      onChange={(event) => { const next = new Set(checked); if (event.target.checked) next.add(item.key); else next.delete(item.key); write(next, notes); }} />
      <span><strong>{t(item.en, item.hi)}</strong></span></label>)}</div>
    <div className="ep-field" style={{ marginTop: 10 }}><label htmlFor="ep-capacity-notes">{t('Notes', 'नोट')}</label>
      <textarea id="ep-capacity-notes" rows={2} value={notes} onChange={(event) => write(checked, event.target.value)} /></div>
  </fieldset>;
}

function CapacityGuide({ lang }: { lang: CareLang }) {
  const guide = capacityGuide(lang);
  return <details className="ep-group"><summary><strong>{guide.title}</strong></summary>
    <p className="ep-hint">{guide.note}</p>
    <ol>{guide.points.map((point) => <li key={point}>{point}</li>)}</ol>
  </details>;
}

function HomeCareFields({ t, draft, edit }: { t: T; draft: EctprRecord; edit: (change: (record: EctprRecord) => void) => void }) {
  const home = draft.homeCare ?? emptyHomeCare();
  const set = (patch: Partial<EctprHomeCare>) => edit((r) => { r.homeCare = { ...(r.homeCare ?? emptyHomeCare()), ...patch }; });
  return <fieldset className="ep-group"><legend>{t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)')}</legend>
    <p className="ep-hint">{t('Helps a doctor called to the home see the care schedule and decide whether home care can continue.', 'घर बुलाए गए डॉक्टर को देखभाल का क्रम देखने और यह तय करने में मदद करता है कि घर पर देखभाल जारी रह सकती है या नहीं।')}</p>
    <div className="ep-fields">
      <Field id="ep-home-freq" label={t('How often the patient is assessed', 'मरीज़ की जाँच कितनी बार होती है')}><input id="ep-home-freq" type="text" value={home.assessmentFrequency} onChange={(event) => set({ assessmentFrequency: event.target.value })} /></Field>
      <Field id="ep-home-coord" label={t('Person coordinating between the family and the hospital or centre (name, phone, centre)', 'परिवार और अस्पताल या केंद्र के बीच तालमेल रखने वाले (नाम, फ़ोन, केंद्र)')} wide><input id="ep-home-coord" type="text" value={home.coordinator} onChange={(event) => set({ coordinator: event.target.value })} /></Field>
      <Field id="ep-home-ngo" label={t('Non-governmental organisation caring for the patient: representative and contact', 'मरीज़ की देखभाल करने वाली ग़ैर-सरकारी संस्था: प्रतिनिधि और संपर्क')} wide><input id="ep-home-ngo" type="text" value={home.ngoContact} onChange={(event) => set({ ngoContact: event.target.value, ...(event.target.value.trim() ? {} : { ngoConsulted: false }) })} /></Field>
    </div>
    {home.ngoContact.trim() ? <label className="ep-option"><input type="checkbox" checked={home.ngoConsulted} onChange={(event) => set({ ngoConsulted: event.target.checked })} /><span><strong>{t('The organisation was consulted about this plan', 'इस योजना पर संस्था से सलाह ली गई')}</strong></span></label> : null}
    <YesNo t={t} name="ep-home-admitted" legend={t('Admitted to any hospital before for this condition?', 'क्या इस बीमारी के लिए पहले किसी अस्पताल में भर्ती हुए थे?')} value={home.previousAdmission} onChange={(value) => set({ previousAdmission: value })} />
    {home.previousAdmission ? <div className="ep-fields"><Field id="ep-home-admitted-where" label={t('Where and when', 'कहाँ और कब')} wide><input id="ep-home-admitted-where" type="text" value={home.previousAdmissionDetails} onChange={(event) => set({ previousAdmissionDetails: event.target.value })} /></Field></div> : null}
  </fieldset>;
}

function WithdrawalFields({ t, lang, draft, edit, invalid }: { t: T; lang: CareLang; draft: EctprRecord; edit: (change: (record: EctprRecord) => void) => void; invalid?: boolean }) {
  const w = draft.withdrawal ?? emptyWithdrawal();
  const set = (patch: Partial<EctprWithdrawal>) => edit((r) => { r.withdrawal = { ...(r.withdrawal ?? emptyWithdrawal()), ...patch }; });
  const finals: WithdrawalFinal[] = ['TO_BE_PERFORMED', 'FAMILY_REFUSED', 'BOARD_REFUSED', 'PHYSICIAN_REVOKED'];
  return <>
    <p>{t('Fill this only if withdrawing or withholding treatment has been decided (Common Cause v Union of India, 2018 and 2023). It records the process. This plan alone never authorises it.', 'इसे तभी भरें जब इलाज रोकने या शुरू न करने का फ़ैसला हुआ हो (Common Cause v Union of India, 2018 और 2023)। यह प्रक्रिया दर्ज करता है। सिर्फ़ यह योजना इसकी अनुमति कभी नहीं देती।')}</p>
    <fieldset className="ep-group" aria-invalid={invalid}><legend>{t('Withdrawal or withholding (medical boards)', 'इलाज रोकना या शुरू न करना (मेडिकल बोर्ड)')}</legend>
      <label className="ep-option"><input type="checkbox" checked={w.applicable} onChange={(event) => set({ applicable: event.target.checked })} /><span><strong>{t('Withdrawing or withholding treatment has been decided for this patient', 'इस मरीज़ के लिए इलाज रोकने या शुरू न करने का फ़ैसला हुआ है')}</strong></span></label>
      {w.applicable ? <>
        <div className="ep-fields" style={{ marginTop: 12 }}>
          <Field id="ep-w-pdate" label={t('Date the Primary Board was formed', 'प्राथमिक बोर्ड बनने की तारीख़')}><input id="ep-w-pdate" type="date" value={w.primaryBoardDate} onChange={(event) => set({ primaryBoardDate: event.target.value })} /></Field>
          <Field id="ep-w-sdate" label={t('Date the Secondary Board was formed', 'द्वितीय बोर्ड बनने की तारीख़')}><input id="ep-w-sdate" type="date" value={w.secondaryBoardDate} onChange={(event) => set({ secondaryBoardDate: event.target.value })} /></Field>
          <Field id="ep-w-pmem" label={t('Members of the Primary Board', 'प्राथमिक बोर्ड के सदस्य')} wide><textarea id="ep-w-pmem" rows={2} value={w.primaryBoardMembers} onChange={(event) => set({ primaryBoardMembers: event.target.value })} /></Field>
          <Field id="ep-w-smem" label={t('Members of the Secondary Board', 'द्वितीय बोर्ड के सदस्य')} wide><textarea id="ep-w-smem" rows={2} value={w.secondaryBoardMembers} onChange={(event) => set({ secondaryBoardMembers: event.target.value })} /></Field>
          <Field id="ep-w-dec" label={t('Decision of the Boards', 'बोर्ड का फ़ैसला')} wide><textarea id="ep-w-dec" rows={2} value={w.boardDecision} onChange={(event) => set({ boardDecision: event.target.value })} /></Field>
          <Field id="ep-w-final" label={t('Final decision', 'अंतिम फ़ैसला')} wide>
            <select id="ep-w-final" value={w.finalDecision ?? ''} onChange={(event) => set({ finalDecision: (event.target.value || null) as WithdrawalFinal | null })}>
              <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>
              {finals.map((value) => <option key={value} value={value}>{withdrawalFinalLabel(value, lang)}</option>)}
            </select>
          </Field>
          <Field id="ep-w-protocol" label={t('Protocol for withdrawal or withholding', 'इलाज रोकने या शुरू न करने का तरीका')} wide><textarea id="ep-w-protocol" rows={3} value={w.protocol} onChange={(event) => set({ protocol: event.target.value })} /></Field>
        </div>
      </> : null}
    </fieldset>
  </>;
}

function SignStep({ t, lang, draft, actorName, busy, dirty, saveBlocked, edit, onSign }: {
  t: T; lang: CareLang; draft: EctprRecord; actorName: string; busy: boolean; dirty: boolean; saveBlocked: boolean;
  edit: (change: (record: EctprRecord) => void) => void; onSign: (signature: Signature) => Promise<boolean>;
}) {
  const [consultant, setConsultant] = useState({ name: actorName, reg: '' });
  const [cosign, setCosign] = useState<{ role: 'PALLIATIVE_CLINICIAN' | 'NURSE'; name: string; reg: string }>({ role: 'PALLIATIVE_CLINICIAN', name: '', reg: '' });
  const [person, setPerson] = useState<{ role: 'PATIENT' | 'PRIMARY' | 'SECONDARY'; name: string; thumb: boolean }>({ role: draft.surrogateDeclaration.answeringAsSurrogate ? 'PRIMARY' : 'PATIENT', name: '', thumb: false });
  const [witness, setWitness] = useState('');
  const now = new Date().toISOString();
  const consultantSig: Signature = { role: 'TREATING_CONSULTANT', name: consultant.name.trim(), registrationNo: consultant.reg.trim(), signedAt: now };
  const activationIssues = validateEctpr({ ...draft, signatures: [...draft.signatures, consultantSig] });
  const activationBlocked = blocksActivation(activationIssues) || saveBlocked;
  const remaining = activationIssues.filter((item) => item.severity !== 'warn');
  const witnesses = draft.signatures.filter((s) => s.role === 'WITNESS').length;
  const needsWitnesses = draft.limitedLiteracy || draft.signatures.some((s) => s.thumbImpression);

  return <>
    <p>{t('Everyone signs here. The plan becomes active only when the treating consultant signs with their registration number. A signature cannot be taken back; if the plan changes later, start a review.', 'सब यहाँ साइन करते हैं। योजना तभी लागू होती है जब इलाज करने वाले कंसल्टेंट अपने रजिस्ट्रेशन नंबर के साथ साइन करें। हस्ताक्षर वापस नहीं लिया जा सकता; बाद में योजना बदले तो समीक्षा शुरू करें।')}</p>
    {draft.signatures.length ? <ul className="ep-sigs" aria-label={t('Signatures so far', 'अब तक के हस्ताक्षर')}>
      {draft.signatures.map((s, index) => <li key={`${s.role}-${index}`}><strong>{s.surrogateRank === 'PRIMARY' ? t('Primary decision-maker', 'मुख्य फ़ैसला लेने वाले') : s.surrogateRank === 'SECONDARY' ? t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले') : signatureRole(t, s.role)}</strong><span>{s.name}{s.registrationNo ? ` · ${s.registrationNo}` : ''}{s.thumbImpression ? ` · ${t('thumb impression', 'अँगूठे का निशान')}` : ''}</span><span className="ep-hint">{formatTime(lang, s.signedAt)}</span></li>)}
    </ul> : null}

    <fieldset className="ep-group"><legend>{t('Patient or the person deciding for them', 'मरीज़ या उनकी ओर से फ़ैसला लेने वाले')}</legend>
      <label className="ep-option"><input type="checkbox" checked={draft.limitedLiteracy} onChange={(event) => edit((r) => { r.limitedLiteracy = event.target.checked; })} />
        <span><strong>{t('The signer cannot read well, or the plan was read aloud to them', 'साइन करने वाले ठीक से पढ़ नहीं सकते, या योजना उन्हें पढ़कर सुनाई गई')}</strong><small>{t('Two witnesses must then sign.', 'तब दो गवाहों को साइन करना होगा।')}</small></span></label>
      <div className="ep-fields" style={{ marginTop: 12 }}>
        <Field id="ep-sig-person-role" label={t('Who is signing', 'कौन साइन कर रहे हैं')}>
          <select id="ep-sig-person-role" value={person.role} onChange={(event) => setPerson({ ...person, role: event.target.value as 'PATIENT' | 'PRIMARY' | 'SECONDARY' })}>
            <option value="PATIENT">{t('Patient', 'मरीज़')}</option><option value="PRIMARY">{t('Primary decision-maker', 'मुख्य फ़ैसला लेने वाले')}</option><option value="SECONDARY">{t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले')}</option>
          </select>
        </Field>
        <Field id="ep-sig-person-name" label={t('Full name', 'पूरा नाम')}><input id="ep-sig-person-name" type="text" value={person.name} onChange={(event) => setPerson({ ...person, name: event.target.value })} /></Field>
      </div>
      <label className="ep-option" style={{ marginTop: 10 }}><input type="checkbox" checked={person.thumb} onChange={(event) => setPerson({ ...person, thumb: event.target.checked })} />
        <span><strong>{t('Signed with a thumb impression', 'अँगूठे के निशान से साइन')}</strong></span></label>
      <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={!person.name.trim() || busy || saveBlocked}
        onClick={async () => { if (await onSign({ role: person.role === 'PATIENT' ? 'PATIENT' : 'SURROGATE', ...(person.role === 'PATIENT' ? {} : { surrogateRank: person.role }), name: person.name.trim(), signedAt: new Date().toISOString(), ...(person.thumb ? { thumbImpression: true } : {}) })) setPerson({ ...person, name: '', thumb: false }); }}>{t('Add this signature', 'यह हस्ताक्षर जोड़ें')}</button></div>
    </fieldset>

    {needsWitnesses ? <fieldset className="ep-group" aria-invalid={witnesses < 2 || undefined}><legend>{t(`Witnesses (${witnesses} of 2)`, `गवाह (2 में से ${witnesses})`)}</legend>
      <p className="ep-hint">{t('Needed when the patient signs by thumb impression or has limited literacy, for both home-based and hospital-based evaluation. Preferably people who are not decision-makers.', 'जब मरीज़ अँगूठे से साइन करें या ठीक से पढ़ न सकें तब ज़रूरी, घर और अस्पताल दोनों जगह की जाँच में। बेहतर है कि ये फ़ैसला लेने वाले न हों।')}</p>
      <div className="ep-fields"><Field id="ep-sig-witness" label={t('Witness full name', 'गवाह का पूरा नाम')}><input id="ep-sig-witness" type="text" value={witness} onChange={(event) => setWitness(event.target.value)} /></Field></div>
      <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={!witness.trim() || busy || saveBlocked}
        onClick={async () => { if (await onSign({ role: 'WITNESS', name: witness.trim(), signedAt: new Date().toISOString() })) setWitness(''); }}>{t('Add witness signature', 'गवाह का हस्ताक्षर जोड़ें')}</button></div>
    </fieldset> : null}

    <fieldset className="ep-group"><legend>{t('Co-signature (palliative clinician or nurse)', 'सह-हस्ताक्षर (पैलिएटिव डॉक्टर या नर्स)')}</legend>
      <div className="ep-fields">
        <Field id="ep-sig-co-role" label={t('Role', 'भूमिका')}>
          <select id="ep-sig-co-role" value={cosign.role} onChange={(event) => setCosign({ ...cosign, role: event.target.value as 'PALLIATIVE_CLINICIAN' | 'NURSE' })}>
            <option value="PALLIATIVE_CLINICIAN">{t('Palliative care clinician', 'पैलिएटिव केयर डॉक्टर')}</option><option value="NURSE">{t('Nurse', 'नर्स')}</option>
          </select>
        </Field>
        <Field id="ep-sig-co-name" label={t('Full name', 'पूरा नाम')}><input id="ep-sig-co-name" type="text" value={cosign.name} onChange={(event) => setCosign({ ...cosign, name: event.target.value })} /></Field>
        <Field id="ep-sig-co-reg" label={t('Registration number (if any)', 'रजिस्ट्रेशन नंबर (अगर हो)')}><input id="ep-sig-co-reg" type="text" value={cosign.reg} onChange={(event) => setCosign({ ...cosign, reg: event.target.value })} /></Field>
      </div>
      <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={!cosign.name.trim() || busy || saveBlocked}
        onClick={async () => { if (await onSign({ role: cosign.role, name: cosign.name.trim(), ...(cosign.reg.trim() ? { registrationNo: cosign.reg.trim() } : {}), signedAt: new Date().toISOString() })) setCosign({ ...cosign, name: '', reg: '' }); }}>{t('Add co-signature', 'सह-हस्ताक्षर जोड़ें')}</button></div>
    </fieldset>

    <fieldset className="ep-group"><legend>{t('Treating consultant', 'इलाज करने वाले कंसल्टेंट')}</legend>
      <div className="ep-fields">
        <Field id="ep-sig-cons-name" label={t('Full name', 'पूरा नाम')}><input id="ep-sig-cons-name" type="text" value={consultant.name} onChange={(event) => setConsultant({ ...consultant, name: event.target.value })} /></Field>
        <Field id="ep-sig-cons-reg" label={t('Medical registration number', 'मेडिकल रजिस्ट्रेशन नंबर')}><input id="ep-sig-cons-reg" type="text" value={consultant.reg} aria-invalid={!consultant.reg.trim() || undefined} onChange={(event) => setConsultant({ ...consultant, reg: event.target.value })} /></Field>
      </div>
      {remaining.length ? <>
        <p className="ep-hint" style={{ marginTop: 12 }}>{t('Before this plan can be signed:', 'यह योजना साइन होने से पहले:')}</p>
        <IssueList t={t} lang={lang} issues={remaining} record={draft} />
      </> : <p className="ep-notice ep-info" style={{ marginTop: 12 }}>{t('Everything needed is in place. Signing makes this the one active plan. Any older version is superseded.', 'ज़रूरी सब कुछ हो गया है। साइन करने पर यही एक लागू योजना होगी। पुराना संस्करण “पुराना हो गया” हो जाएगा।')}</p>}
      {dirty && !saveBlocked ? <p className="ep-hint">{t('Your latest changes will be saved first.', 'आपके नए बदलाव पहले सेव होंगे।')}</p> : null}
      <div className="ep-actions"><button type="button" className="ep-btn" disabled={activationBlocked || busy} onClick={() => void onSign({ ...consultantSig, signedAt: new Date().toISOString() })}>{t('Sign and make this plan active', 'साइन करें और योजना लागू करें')}</button></div>
    </fieldset>
  </>;
}
