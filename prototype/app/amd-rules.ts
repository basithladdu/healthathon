// Sahara living will (AMD) rules: validation L1–L9 and the record lifecycle (tech spec §2, §6).
// Pure functions only, so they run in the browser, in node tests and in the sample-PDF script.
// Law: Common Cause v Union of India (2018) as modified on 24 Jan 2023 (MA 1699/2019 in WP(C) 215/2005);
// Harish Rana v Union of India (11 Mar 2026). Template: Vidhi Centre for Legal Policy / ACP India model AMD.
// The app never decides to withhold or withdraw treatment, never validates an AMD by e-signature and
// never offers any act intended to end life.

import {
  AMD_FORM_VERSION,
  type AmdContent, type AmdCopy, type AmdCustodian, type AmdDhr, type AmdExecution, type AmdStatusSummary,
  type AmdTreatment, type AmdVersion, type AmdWitness, type IdType, type RuleIssue,
} from './ectpr-model.ts';

export type AmdIssue = RuleIssue & { messageHi: string };

// ---------------------------------------------------------------------------
// Fixed texts shown in the app and the PDF

/** L7: shown wherever the AMD status appears in the ED view. */
export const AMD_ED_CAVEAT = 'If the situation is ambiguous or not anticipated by the AMD, it is not applied.';
export const AMD_ED_CAVEAT_HI = 'अगर स्थिति साफ़ नहीं है या AMD में उसका अनुमान नहीं था, तो AMD लागू नहीं होता।';

/**
 * L8: board pathway, information only. The app has no "withdraw" action and never starts, stops or decides treatment.
 * Common Cause 2018 as modified 24 Jan 2023 (paras 198.4–198.5); Harish Rana, 2026 INSC 222, para 322 (ii), (iv), (v), (vi) and directions (e)–(f).
 * Withholding and withdrawing are not separated by the Court or the Vidhi FAQ for a patient who cannot decide (legal review pending).
 */
export const AMD_BOARD_PATHWAY: Array<{ en: string; hi: string }> = [
  { en: 'Withholding or withdrawing life-sustaining treatment for a patient who cannot decide follows the board process below. This app only shows it as information. It never starts, stops or decides any treatment. (Legal review pending.)', hi: 'जो मरीज़ ख़ुद निर्णय नहीं ले सकता, उसके लिए जीवन-रक्षक इलाज न शुरू करना या रोकना नीचे दी गई बोर्ड प्रक्रिया से होता है। यह ऐप इसे केवल जानकारी के रूप में दिखाता है। यह कभी कोई इलाज शुरू, बंद या तय नहीं करता। (क़ानूनी समीक्षा बाक़ी।)' },
  { en: '1. Primary Medical Board (hospital): the treating doctor and at least two experts with 5+ years of experience. It meets the patient with the named representatives and gives its opinion, preferably within 48 hours.', hi: '1. प्राथमिक मेडिकल बोर्ड (अस्पताल): इलाज करने वाले डॉक्टर और कम से कम दो विशेषज्ञ जिन्हें 5+ साल का अनुभव हो। यह नामित प्रतिनिधियों के साथ मरीज़ से मिलता है और हो सके तो 48 घंटे में राय देता है।' },
  { en: '2. Secondary Medical Board: a doctor nominated by the district Chief Medical Officer (from a panel reviewed at least every 12 months; nomination preferably within 48 hours of the hospital’s request) and at least two other experts, none from the first board. Opinion preferably within 48 hours.', hi: '2. द्वितीयक मेडिकल बोर्ड: ज़िला मुख्य चिकित्सा अधिकारी द्वारा नामित डॉक्टर (ऐसे पैनल से जिसकी समीक्षा कम से कम हर 12 महीने हो; नामांकन हो सके तो अस्पताल के अनुरोध के 48 घंटे में) और कम से कम दो अन्य विशेषज्ञ, जो पहले बोर्ड में न हों। राय हो सके तो 48 घंटे में।' },
  { en: '3. Consent: the named representatives (DHRs) consent in writing; without an AMD, the next of kin’s written consent, reflecting the patient’s own wishes. The hospital then informs the Judicial Magistrate First Class (JMFC).', hi: '3. सहमति: नामित प्रतिनिधि (DHR) लिखित सहमति देते हैं; AMD न हो तो निकट संबंधी की लिखित सहमति, जो मरीज़ की अपनी इच्छा दिखाए। फिर अस्पताल न्यायिक मजिस्ट्रेट प्रथम श्रेणी (JMFC) को सूचित करता है।' },
  { en: '4. After both boards agree there is a 30-day reconsideration period, when anyone affected may go to court (Harish Rana, 2026). Only then is there a planned palliative process, never abandonment.', hi: '4. दोनों बोर्ड सहमत होने के बाद 30 दिन की पुनर्विचार अवधि होती है, जिसमें कोई भी प्रभावित व्यक्ति अदालत जा सकता है (हरीश राणा, 2026)। उसके बाद ही योजना के साथ पैलिएटिव देखभाल की प्रक्रिया होती है, कभी भी छोड़ देना नहीं।' },
  { en: '5. If a board refuses, or the hospital will not start the process, the representatives, family or doctors may approach the High Court (Article 226).', hi: '5. अगर बोर्ड मना करे, या अस्पताल प्रक्रिया शुरू न करे, तो प्रतिनिधि, परिवार या डॉक्टर हाई कोर्ट (अनुच्छेद 226) जा सकते हैं।' },
];

export const AMD_TREATMENTS: AmdTreatment[] = ['CPR', 'VENTILATION', 'DIALYSIS', 'TUBE_FEEDING', 'IV_FLUIDS_ANTIBIOTICS', 'CHEMOTHERAPY', 'RADIOTHERAPY', 'SURGERY'];

/** Vidhi §2 wording for each treatment, used in the PDF. */
export const AMD_TREATMENT_PDF_TEXT: Record<AmdTreatment, { refuse: string; accept: string }> = {
  CPR: { refuse: 'Cardio-pulmonary resuscitation (CPR)', accept: 'Cardio-pulmonary resuscitation (CPR)' },
  VENTILATION: { refuse: 'Ventilation or other kinds of artificial life support', accept: 'Ventilation or other kinds of artificial life support' },
  DIALYSIS: { refuse: 'Dialysis', accept: 'Dialysis' },
  TUBE_FEEDING: {
    refuse: 'Artificial administration of nutrition and hydration through means such as intravenous catheters or feeding tubes, if it serves only to artificially prolong the process of dying. [This does not apply to comfort feeding by mouth, mouth care, or nutrition and hydration given for comfort/symptom relief.]',
    accept: 'Artificial administration of nutrition and hydration through feeding tubes or intravenous catheters',
  },
  IV_FLUIDS_ANTIBIOTICS: {
    refuse: 'Intravenous fluids and medications including antibiotics; excluding those that would provide comfort/relieve pain and suffering',
    accept: 'Intravenous fluids and medications, including antibiotics and drugs that will provide comfort or relieve suffering/pain',
  },
  CHEMOTHERAPY: { refuse: 'Chemotherapy', accept: 'Chemotherapy' },
  RADIOTHERAPY: { refuse: 'Radiotherapy', accept: 'Radiotherapy' },
  SURGERY: { refuse: 'Surgery, unless it is for symptom control or to improve the quality of my life', accept: 'Surgery' },
};

export const ID_TYPE_LABELS: Record<IdType, { en: string; hi: string }> = {
  AADHAAR: { en: 'Aadhaar', hi: 'आधार' },
  VOTER_ID: { en: 'Voter ID', hi: 'वोटर आईडी' },
  PAN: { en: 'PAN card', hi: 'पैन कार्ड' },
  PASSPORT: { en: 'Passport', hi: 'पासपोर्ट' },
  DRIVING_LICENCE: { en: 'Driving licence', hi: 'ड्राइविंग लाइसेंस' },
  RATION_CARD: { en: 'Ration card', hi: 'राशन कार्ड' },
  OTHER: { en: 'Other ID', hi: 'अन्य पहचान पत्र' },
};

// ---------------------------------------------------------------------------
// Small helpers

export function amdId(prefix: string): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}-${random}`;
}

export function amdIsoDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Whole years between an ISO date of birth and `today`. null if the date is not a real past date. */
export function ageOn(dob: string, today: Date): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dob.trim());
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const birth = new Date(year, month - 1, day);
  if (birth.getFullYear() !== year || birth.getMonth() !== month - 1 || birth.getDate() !== day) return null;
  let age = today.getFullYear() - year;
  if (today.getMonth() < month - 1 || (today.getMonth() === month - 1 && today.getDate() < day)) age -= 1;
  return age;
}

export const normaliseName = (name: string) => name.normalize('NFKC').toLowerCase().replace(/^(dr|mr|mrs|ms|shri|smt|sri|kumari)\.?\s+/u, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();

/** Only the last 4 digits are ever stored. Empty is allowed (ID can be written by hand on paper). */
export const validLast4 = (value: string) => value === '' || /^\d{4}$/.test(value);

/** Strip anything that is not a digit and keep at most the last 4, so a pasted full Aadhaar is never kept. */
export function toLast4(input: string): string {
  const digits = input.replace(/\D/g, '');
  return digits.slice(-4);
}

const WORDS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten'];
export const numberInWords = (n: number) => WORDS[n] ?? String(n);

/** Deterministic JSON (sorted keys) for hashing. */
export function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>).filter((key) => (value as Record<string, unknown>)[key] !== undefined).sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify((value as Record<string, unknown>)[key])}`).join(',')}}`;
  }
  return JSON.stringify(value ?? null);
}

export async function sha256Hex(data: ArrayBuffer | Uint8Array | string): Promise<string> {
  const bytes = typeof data === 'string' ? new TextEncoder().encode(data) : data instanceof Uint8Array ? data : new Uint8Array(data);
  const digest = await crypto.subtle.digest('SHA-256', bytes as BufferSource);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** SHA-256 of what the person chose (printed on every PDF page; the file's own SHA-256 is kept in the record). */
export function amdContentHash(version: Pick<AmdVersion, 'patientId' | 'version' | 'formVersion' | 'content'>): Promise<string> {
  return sha256Hex(stableStringify({ patientId: version.patientId, version: version.version, formVersion: version.formVersion, content: version.content }));
}

// ---------------------------------------------------------------------------
// Empty records

export function emptyDhr(): AmdDhr {
  return { id: amdId('dhr'), name: '', relation: '', dob: '', idType: null, idLast4: '', phone: '', phoneAlt: '', email: '', permanentAddress: '', currentAddress: '', agreed: false };
}

export function emptyWitness(): AmdWitness {
  return { name: '', address: '', idType: null, idLast4: '', email: '', phone: '' };
}

export function emptyAmdContent(executantName = ''): AmdContent {
  return {
    executant: { name: executantName, gender: '', dob: '', idType: null, idLast4: '', permanentAddress: '', currentAddress: '', localBody: '', readOutToMe: false },
    declarations: { capacity: false, consideredCarefully: false, voluntary: false, understandsNotAppliedIfUnclear: false, capacityCertificateAttached: false },
    values: '',
    conditions: [],
    anticipatedConditions: '',
    treatments: { CPR: null, VENTILATION: null, DIALYSIS: null, TUBE_FEEDING: null, IV_FLUIDS_ANTIBIOTICS: null, CHEMOTHERAPY: null, RADIOTHERAPY: null, SURGERY: null },
    comfortCareAlways: true, comfortOralFeedingAlways: true,
    wishes: { placeOfCare: null, institution: '', physician: '', focus: '', preferredName: '', genderPronouns: '', partnerName: '', partnerTerm: '' },
    afterDeath: { organDonor: false, bodyDonation: false, bodyDonationInstitution: '', lastRites: '' },
    dhrs: [emptyDhr()],
    dhrsActJointly: false,
    familyPhysician: { name: '', registrationNo: '', phone: '', address: '' },
    custodianBody: '',
  };
}

export function newAmdDraft(patientId: string, createdBy: string, now: Date, content: AmdContent, versionNumber = 1): AmdVersion {
  const at = now.toISOString();
  return { id: amdId('amd'), patientId, version: versionNumber, formVersion: AMD_FORM_VERSION, status: 'DRAFT', createdAt: at, updatedAt: at, createdBy, content: structuredClone(content), copies: [] };
}

// ---------------------------------------------------------------------------
// Validation

const issue = (id: string, severity: AmdIssue['severity'], field: string, message: string, messageHi: string): AmdIssue => ({ id, severity, field, message, messageHi });

/** Words that ask for an act intended to end life. Such a request is illegal in India and cannot be printed. */
const ENDING_LIFE = /\b(active\s+euthanasia|mercy\s+killing|lethal\s+injection|assisted\s+(suicide|dying)|help\s+me\s+die|end\s+my\s+life|kill\s+me)\b|इच्छा\s*मृत्यु\s*का\s*इंजेक्शन|ज़हर\s*का\s*इंजेक्शन|मुझे\s*मार\s*दें/iu;

function freeTexts(content: AmdContent): Array<[string, string]> {
  return [
    ['values', content.values], ['anticipatedConditions', content.anticipatedConditions],
    ['wishes.focus', content.wishes.focus], ['afterDeath.lastRites', content.afterDeath.lastRites],
  ];
}

/** Rules for the content (L1, L2, L9 and the no-euthanasia rule). `block` stops PDF generation. */
export function validateAmdContent(content: AmdContent, today: Date): AmdIssue[] {
  const issues: AmdIssue[] = [];
  const ex = content.executant;
  if (!ex.name.trim()) issues.push(issue('NAME', 'block', 'executant.name', 'Enter your full name.', 'अपना पूरा नाम लिखें।'));
  const age = ageOn(ex.dob, today);
  if (!ex.dob) issues.push(issue('L1_DOB', 'block', 'executant.dob', 'Enter your date of birth.', 'अपनी जन्म तिथि लिखें।'));
  else if (age === null || age < 0) issues.push(issue('L1_DOB', 'block', 'executant.dob', 'Enter a real date of birth.', 'सही जन्म तिथि लिखें।'));
  else if (age < 18) issues.push(issue('L1_ADULT', 'block', 'executant.dob', 'Only an adult (18 years or older) can make a living will.', 'केवल वयस्क (18 साल या उससे ज़्यादा) ही लिविंग विल बना सकते हैं।'));
  if (!validLast4(ex.idLast4)) issues.push(issue('ID_LAST4', 'block', 'executant.idLast4', 'Enter only the last 4 digits of the ID.', 'पहचान पत्र के केवल आख़िरी 4 अंक लिखें।'));
  if (!ex.permanentAddress.trim()) issues.push(issue('ADDRESS', 'warn', 'executant.permanentAddress', 'Your address is empty. You will need to write it by hand on the paper.', 'आपका पता खाली है। आपको इसे काग़ज़ पर हाथ से लिखना होगा।'));
  const d = content.declarations;
  if (!d.capacity) issues.push(issue('L1_CAPACITY', 'block', 'declarations.capacity', 'Confirm that you understand what this document means.', 'पुष्टि करें कि आप समझते हैं कि इस दस्तावेज़ का क्या मतलब है।'));
  if (!d.voluntary) issues.push(issue('L1_VOLUNTARY', 'block', 'declarations.voluntary', 'Confirm that nobody is forcing or pressuring you.', 'पुष्टि करें कि कोई आप पर दबाव नहीं डाल रहा है।'));
  if (!d.consideredCarefully) issues.push(issue('L1_CONSIDERED', 'block', 'declarations.consideredCarefully', 'Confirm that you have thought about this carefully.', 'पुष्टि करें कि आपने इस पर ध्यान से सोचा है।'));
  if (!d.understandsNotAppliedIfUnclear) issues.push(issue('L7_UNDERSTOOD', 'block', 'declarations.understandsNotAppliedIfUnclear', 'Confirm that you understand it will not be used if the situation is unclear or was not foreseen.', 'पुष्टि करें कि आप समझते हैं कि स्थिति साफ़ न होने या पहले से न सोची गई होने पर इसका उपयोग नहीं होगा।'));

  if (content.conditions.length === 0) issues.push(issue('CONDITIONS', 'block', 'conditions', 'Choose at least one situation when this living will applies.', 'कम से कम एक स्थिति चुनें जब यह लिविंग विल लागू हो।'));
  if (!Object.values(content.treatments).some((choice) => choice !== null)) {
    issues.push(issue('NO_TREATMENT_CHOICE', 'warn', 'treatments', 'You have not chosen any specific treatment. The general direction in Section 2 will still apply.', 'आपने कोई ख़ास इलाज नहीं चुना है। खंड 2 का सामान्य निर्देश फिर भी लागू होगा।'));
  }
  if (content.comfortCareAlways !== true || content.comfortOralFeedingAlways !== true) {
    issues.push(issue('L9_COMFORT', 'block', 'treatments', 'Comfort care and feeding by mouth always continue. They cannot be refused here.', 'आराम की देखभाल और मुँह से खिलाना हमेशा जारी रहता है। इन्हें यहाँ मना नहीं किया जा सकता।'));
  }
  for (const [field, text] of freeTexts(content)) {
    if (ENDING_LIFE.test(text)) issues.push(issue('NO_EUTHANASIA', 'block', field, 'A living will cannot ask for anything meant to end life. That is against the law in India. Please change these words.', 'लिविंग विल में जीवन ख़त्म करने के लिए कुछ भी नहीं माँगा जा सकता। यह भारत में क़ानून के ख़िलाफ़ है। कृपया ये शब्द बदलें।'));
  }

  // L2: at least one DHR; warn below two; preference order is the array order.
  const named = content.dhrs.filter((dhr) => dhr.name.trim());
  if (named.length === 0) issues.push(issue('L2_DHR_REQUIRED', 'block', 'dhrs', 'Name at least one person who can speak for you (a representative).', 'कम से कम एक व्यक्ति का नाम लिखें जो आपकी ओर से बोल सके (प्रतिनिधि)।'));
  else if (named.length < 2) issues.push(issue('L2_DHR_TWO', 'warn', 'dhrs', 'It is better to name two representatives, in order of preference, in case the first cannot be reached.', 'दो प्रतिनिधियों के नाम, पसंद के क्रम में, लिखना बेहतर है, ताकि पहले से संपर्क न हो पाने पर दूसरा हो।'));
  content.dhrs.forEach((dhr, index) => {
    const field = `dhrs.${index}`;
    const filled = dhr.name.trim() || dhr.phone.trim() || dhr.relation.trim();
    if (!filled) return;
    if (!dhr.name.trim()) issues.push(issue('DHR_NAME', 'block', `${field}.name`, `Representative ${index + 1}: enter the full name.`, `प्रतिनिधि ${index + 1}: पूरा नाम लिखें।`));
    if (!dhr.relation.trim()) issues.push(issue('DHR_RELATION', 'block', `${field}.relation`, `Representative ${index + 1}: say how they are related to you.`, `प्रतिनिधि ${index + 1}: बताएँ कि वे आपके क्या लगते हैं।`));
    if (!dhr.phone.trim()) issues.push(issue('DHR_PHONE', 'block', `${field}.phone`, `Representative ${index + 1}: enter a mobile number.`, `प्रतिनिधि ${index + 1}: मोबाइल नंबर लिखें।`));
    if (!validLast4(dhr.idLast4)) issues.push(issue('ID_LAST4', 'block', `${field}.idLast4`, `Representative ${index + 1}: enter only the last 4 digits of the ID.`, `प्रतिनिधि ${index + 1}: पहचान पत्र के केवल आख़िरी 4 अंक लिखें।`));
    const dhrAge = dhr.dob ? ageOn(dhr.dob, today) : null;
    if (dhr.dob && (dhrAge === null || dhrAge < 18)) issues.push(issue('DHR_ADULT', 'block', `${field}.dob`, `Representative ${index + 1} must be an adult (18 or older).`, `प्रतिनिधि ${index + 1} वयस्क (18 या उससे ज़्यादा) होना चाहिए।`));
    if (dhr.name.trim() && ex.name.trim() && normaliseName(dhr.name) === normaliseName(ex.name)) issues.push(issue('DHR_SELF', 'block', `${field}.name`, 'You cannot name yourself as your representative.', 'आप ख़ुद को अपना प्रतिनिधि नहीं बना सकते।'));
    if (!dhr.agreed) issues.push(issue('DHR_AGREED', 'warn', `${field}.agreed`, `Representative ${index + 1}: talk to them and tick that they agreed before you sign.`, `प्रतिनिधि ${index + 1}: उनसे बात करें और हस्ताक्षर से पहले टिक करें कि वे सहमत हैं।`));
  });
  const names = named.map((dhr) => normaliseName(dhr.name));
  if (new Set(names).size !== names.length) issues.push(issue('DHR_DUPLICATE', 'block', 'dhrs', 'The same person is listed twice as a representative.', 'एक ही व्यक्ति प्रतिनिधि के रूप में दो बार लिखा है।'));
  if (!content.custodianBody.trim()) issues.push(issue('CUSTODIAN_BODY', 'warn', 'custodianBody', 'Write your panchayat or municipality so a copy can go to its custodian.', 'अपनी पंचायत या नगरपालिका लिखें ताकि एक प्रति उसके संरक्षक को जा सके।'));
  return issues;
}

/** L3 rules for recording execution. `block` keeps the AMD in GENERATED. */
export function validateExecution(version: AmdVersion, execution: AmdExecution, custodian: AmdCustodian | null, today: Date): AmdIssue[] {
  const issues: AmdIssue[] = [];
  if (version.status !== 'GENERATED') issues.push(issue('NOT_GENERATED', 'block', 'status', 'Print the PDF first. Only a printed and signed version can be recorded.', 'पहले PDF प्रिंट करें। केवल प्रिंट और हस्ताक्षरित संस्करण दर्ज हो सकता है।'));
  if (!execution.signedOn) issues.push(issue('SIGNED_ON', 'block', 'execution.signedOn', 'Enter the date it was signed.', 'हस्ताक्षर की तारीख़ लिखें।'));
  else if (execution.signedOn > amdIsoDay(today)) issues.push(issue('SIGNED_FUTURE', 'block', 'execution.signedOn', 'The signing date cannot be in the future.', 'हस्ताक्षर की तारीख़ भविष्य की नहीं हो सकती।'));
  else if (version.generatedAt && execution.signedOn < version.generatedAt.slice(0, 10)) issues.push(issue('SIGNED_BEFORE_PRINT', 'block', 'execution.signedOn', 'The signing date is before this version was printed. Check you uploaded the right version.', 'हस्ताक्षर की तारीख़ इस संस्करण के प्रिंट से पहले की है। जाँचें कि सही संस्करण अपलोड किया है।'));
  if (!execution.scanSha256 || !execution.scanKey) issues.push(issue('L3_SCAN', 'block', 'execution.scan', 'Upload a photo or scan of the signed and attested paper.', 'हस्ताक्षरित और सत्यापित काग़ज़ की फ़ोटो या स्कैन अपलोड करें।'));
  const executant = normaliseName(version.content.executant.name);
  const dhrNames = version.content.dhrs.map((dhr) => normaliseName(dhr.name)).filter(Boolean);
  execution.witnesses.forEach((witness, index) => {
    const field = `execution.witnesses.${index}`;
    if (!witness.name.trim()) { issues.push(issue('L3_WITNESS', 'block', `${field}.name`, `Enter the name of witness ${index + 1}.`, `गवाह ${index + 1} का नाम लिखें।`)); return; }
    const name = normaliseName(witness.name);
    if (name === executant) issues.push(issue('WITNESS_SELF', 'block', `${field}.name`, 'The person making the living will cannot be their own witness.', 'लिविंग विल बनाने वाला व्यक्ति ख़ुद अपना गवाह नहीं हो सकता।'));
    if (dhrNames.includes(name)) issues.push(issue('L3_WITNESS_IS_DHR', 'warn', `${field}.name`, `Witness ${index + 1} is also a named representative. Witnesses should preferably be independent.`, `गवाह ${index + 1} एक नामित प्रतिनिधि भी है। गवाह हो सके तो स्वतंत्र होने चाहिए।`));
    if (!validLast4(witness.idLast4)) issues.push(issue('ID_LAST4', 'block', `${field}.idLast4`, `Witness ${index + 1}: enter only the last 4 digits of the ID.`, `गवाह ${index + 1}: पहचान पत्र के केवल आख़िरी 4 अंक लिखें।`));
  });
  const [first, second] = execution.witnesses.map((witness) => normaliseName(witness.name));
  if (first && first === second) issues.push(issue('WITNESS_SAME', 'block', 'execution.witnesses.1.name', 'The two witnesses must be two different people.', 'दोनों गवाह अलग-अलग व्यक्ति होने चाहिए।'));
  const attestor = execution.attestor;
  if (!attestor.kind) issues.push(issue('L3_ATTESTOR_KIND', 'block', 'execution.attestor.kind', 'Choose who attested it: a notary or a gazetted officer.', 'चुनें कि किसने सत्यापित किया: नोटरी या राजपत्रित अधिकारी।'));
  if (!attestor.name.trim()) issues.push(issue('L3_ATTESTOR', 'block', 'execution.attestor.name', 'Enter the name of the notary or gazetted officer.', 'नोटरी या राजपत्रित अधिकारी का नाम लिखें।'));
  else if ([executant, ...dhrNames, ...execution.witnesses.map((w) => normaliseName(w.name))].includes(normaliseName(attestor.name))) {
    issues.push(issue('ATTESTOR_INDEPENDENT', 'block', 'execution.attestor.name', 'The notary or gazetted officer must be a different person from you, your representatives and the witnesses.', 'नोटरी या राजपत्रित अधिकारी आपसे, आपके प्रतिनिधियों से और गवाहों से अलग व्यक्ति होना चाहिए।'));
  }
  if (!custodian) issues.push(issue('L5_CUSTODIAN', 'block', 'custodian', 'Record the custodian: acknowledged, or "custodian pending".', 'संरक्षक दर्ज करें: पावती मिली, या "संरक्षक बाक़ी"।'));
  else if (custodian.status === 'ACKNOWLEDGED' && (!custodian.officer.trim() || !custodian.acknowledgedOn)) {
    issues.push(issue('L5_CUSTODIAN_DETAIL', 'block', 'custodian', 'Enter the custodian officer’s name and the date they acknowledged it.', 'संरक्षक अधिकारी का नाम और पावती की तारीख़ लिखें।'));
  }
  return issues;
}

export const blocking = (issues: AmdIssue[]) => issues.filter((item) => item.severity === 'block');

// ---------------------------------------------------------------------------
// Lifecycle (tech spec §6): DRAFT → GENERATED → EXECUTED; edits make a new version (L6); revoke in writing.

export type AmdResult = { ok: true; versions: AmdVersion[]; version: AmdVersion } | { ok: false; versions: AmdVersion[]; issues: AmdIssue[] };

const sortByVersion = (versions: AmdVersion[]) => [...versions].sort((a, b) => a.version - b.version);
export const latestVersion = (versions: AmdVersion[]) => sortByVersion(versions).at(-1) ?? null;

/** L6: the latest EXECUTED version is the current one. Revoked or superseded versions are never current. */
export function currentExecuted(versions: AmdVersion[]): AmdVersion | null {
  return sortByVersion(versions).filter((v) => v.status === 'EXECUTED').at(-1) ?? null;
}

/** The version the person is working on (DRAFT or GENERATED and not yet executed), if any. */
export function workingVersion(versions: AmdVersion[]): AmdVersion | null {
  const latest = latestVersion(versions);
  return latest && (latest.status === 'DRAFT' || latest.status === 'GENERATED') ? latest : null;
}

function replace(versions: AmdVersion[], next: AmdVersion): AmdVersion[] {
  return versions.map((v) => (v.id === next.id ? next : v));
}

/** Start the first draft, or a new version from the latest content. Only DRAFT versions can be edited. */
export function startAmdVersion(versions: AmdVersion[], patientId: string, actor: string, now: Date, executantName = ''): AmdResult {
  const latest = latestVersion(versions);
  if (latest?.status === 'DRAFT') return { ok: true, versions, version: latest };
  const at = now.toISOString();
  // An unsigned printed version is set aside: its paper copy must not be signed after changes.
  const cleaned = versions.map((v) => (v.status === 'GENERATED' ? { ...v, status: 'SUPERSEDED' as const, supersededAt: at, updatedAt: at } : v));
  const base = latest && latest.status !== 'REVOKED' ? latest.content : emptyAmdContent(executantName);
  const draft = newAmdDraft(patientId, actor, now, base, (latest?.version ?? 0) + 1);
  return { ok: true, versions: [...cleaned, draft], version: draft };
}

export function updateAmdDraft(versions: AmdVersion[], id: string, content: AmdContent, now: Date): AmdResult {
  const target = versions.find((v) => v.id === id);
  if (!target || target.status !== 'DRAFT') return { ok: false, versions, issues: [issue('NOT_DRAFT', 'block', 'status', 'This version is printed or signed and cannot be changed. Start a new version.', 'यह संस्करण प्रिंट या हस्ताक्षरित है और बदला नहीं जा सकता। नया संस्करण शुरू करें।')] };
  const next = { ...target, content: structuredClone(content), updatedAt: now.toISOString() };
  return { ok: true, versions: replace(versions, next), version: next };
}

/** DRAFT → GENERATED once the PDF is made. The content is frozen from here. */
export function markGenerated(versions: AmdVersion[], id: string, info: { pdfSha256: string; contentSha256: string; lang: 'en' | 'hi' | 'te' }, now: Date): AmdResult {
  const target = versions.find((v) => v.id === id);
  if (!target) return { ok: false, versions, issues: [issue('MISSING', 'block', 'status', 'Version not found.', 'संस्करण नहीं मिला।')] };
  if (target.status === 'GENERATED') {
    // Printing again keeps the same content; record the latest file hash.
    const next = { ...target, pdfSha256: info.pdfSha256, pdfLang: info.lang, updatedAt: now.toISOString() };
    return { ok: true, versions: replace(versions, next), version: next };
  }
  if (target.status !== 'DRAFT') return { ok: false, versions, issues: [issue('NOT_DRAFT', 'block', 'status', 'Only a draft can be printed for signing.', 'केवल ड्राफ़्ट ही हस्ताक्षर के लिए प्रिंट हो सकता है।')] };
  const blocks = blocking(validateAmdContent(target.content, now));
  if (blocks.length) return { ok: false, versions, issues: blocks };
  const at = now.toISOString();
  const next: AmdVersion = { ...target, status: 'GENERATED', generatedAt: at, updatedAt: at, pdfSha256: info.pdfSha256, contentSha256: info.contentSha256, pdfLang: info.lang };
  return { ok: true, versions: replace(versions, next), version: next };
}

/** Copy checklist seeded from the content: every DHR, the family physician and the local-body custodian (L4). */
export function seedCopies(content: AmdContent, existing: AmdCopy[] = []): AmdCopy[] {
  const wanted: Array<Omit<AmdCopy, 'id' | 'consent' | 'consentAt' | 'given' | 'givenOn' | 'method'>> = [
    ...content.dhrs.filter((dhr) => dhr.name.trim()).map((dhr) => ({ kind: 'DHR' as const, name: dhr.name.trim(), dhrId: dhr.id })),
    ...(content.familyPhysician.name.trim() ? [{ kind: 'FAMILY_PHYSICIAN' as const, name: content.familyPhysician.name.trim() }] : []),
    { kind: 'CUSTODIAN' as const, name: content.custodianBody.trim() || 'Local-body custodian' },
  ];
  return wanted.map((item) => {
    const match = existing.find((copy) => copy.kind === item.kind && (item.kind !== 'DHR' || copy.dhrId === item.dhrId));
    return match ? { ...match, name: item.name } : { id: amdId('copy'), ...item, consent: false, consentAt: '', given: false, givenOn: '', method: null };
  });
}

/** GENERATED → EXECUTED only with the signed scan, two witnesses and the attestor (L3) and a custodian state (L5). */
export function markExecuted(versions: AmdVersion[], id: string, execution: AmdExecution, custodian: AmdCustodian | null, now: Date): AmdResult {
  const target = versions.find((v) => v.id === id);
  if (!target) return { ok: false, versions, issues: [issue('MISSING', 'block', 'status', 'Version not found.', 'संस्करण नहीं मिला।')] };
  const blocks = blocking(validateExecution(target, execution, custodian, now));
  if (blocks.length) return { ok: false, versions, issues: blocks };
  const at = now.toISOString();
  // Each executed version needs its own copies (and fresh consent per recipient): older copies are out of date.
  const next: AmdVersion = { ...target, status: 'EXECUTED', updatedAt: at, execution: structuredClone(execution), custodian: { ...custodian! }, copies: seedCopies(target.content) };
  const updated = versions.map((v) => (v.id === id ? next : v.status === 'EXECUTED' ? { ...v, status: 'SUPERSEDED' as const, supersededAt: at, updatedAt: at } : v));
  return { ok: true, versions: updated, version: next };
}

/** A copy can only be marked as given after that recipient's own consent is recorded (DPDP: consent per recipient). */
export function updateCopy(versions: AmdVersion[], versionId: string, copyId: string, patch: Partial<Pick<AmdCopy, 'consent' | 'given' | 'givenOn' | 'method'>>, now: Date): AmdResult {
  const target = versions.find((v) => v.id === versionId);
  const copy = target?.copies.find((c) => c.id === copyId);
  if (!target || !copy || target.status !== 'EXECUTED') return { ok: false, versions, issues: [issue('COPY_MISSING', 'block', 'copies', 'Copies can be logged only for the signed version.', 'प्रतियाँ केवल हस्ताक्षरित संस्करण के लिए दर्ज हो सकती हैं।')] };
  const nextCopy: AmdCopy = { ...copy, ...patch };
  if (patch.consent === false) { nextCopy.given = false; nextCopy.givenOn = ''; nextCopy.method = null; nextCopy.consentAt = ''; }
  if (patch.consent === true && !copy.consent) nextCopy.consentAt = now.toISOString();
  if (nextCopy.given && !nextCopy.consent) return { ok: false, versions, issues: [issue('COPY_CONSENT', 'block', 'copies', 'Record consent to share with this person before marking the copy as given.', 'प्रति दी गई चिह्नित करने से पहले इस व्यक्ति से साझा करने की सहमति दर्ज करें।')] };
  if (nextCopy.given && !nextCopy.givenOn) nextCopy.givenOn = amdIsoDay(now);
  const next = { ...target, copies: target.copies.map((c) => (c.id === copyId ? nextCopy : c)), updatedAt: now.toISOString() };
  return { ok: true, versions: replace(versions, next), version: next };
}

export function updateCustodian(versions: AmdVersion[], versionId: string, custodian: AmdCustodian, now: Date): AmdResult {
  const target = versions.find((v) => v.id === versionId);
  if (!target || target.status !== 'EXECUTED') return { ok: false, versions, issues: [issue('NOT_EXECUTED', 'block', 'custodian', 'Only a signed version has a custodian record.', 'केवल हस्ताक्षरित संस्करण का संरक्षक रिकॉर्ड होता है।')] };
  if (custodian.status === 'ACKNOWLEDGED' && (!custodian.officer.trim() || !custodian.acknowledgedOn)) return { ok: false, versions, issues: [issue('L5_CUSTODIAN_DETAIL', 'block', 'custodian', 'Enter the custodian officer’s name and the date they acknowledged it.', 'संरक्षक अधिकारी का नाम और पावती की तारीख़ लिखें।')] };
  const next = { ...target, custodian: { ...custodian }, updatedAt: now.toISOString() };
  return { ok: true, versions: replace(versions, next), version: next };
}

export function updateAbha(versions: AmdVersion[], versionId: string, abha: { address: string; consent: boolean }, now: Date): AmdResult {
  const target = versions.find((v) => v.id === versionId);
  if (!target || target.status !== 'EXECUTED') return { ok: false, versions, issues: [issue('NOT_EXECUTED', 'block', 'abha', 'Only a signed version can be linked.', 'केवल हस्ताक्षरित संस्करण ही जोड़ा जा सकता है।')] };
  const address = abha.address.trim();
  if (address && !abha.consent) return { ok: false, versions, issues: [issue('ABHA_CONSENT', 'block', 'abha', 'Tick consent before linking to ABHA.', 'ABHA से जोड़ने से पहले सहमति पर टिक करें।')] };
  if (address && !/^[a-z0-9._-]{4,}@(abdm|sbx)$/i.test(address) && !/^\d{2}-?\d{4}-?\d{4}-?\d{4}$/.test(address)) {
    return { ok: false, versions, issues: [issue('ABHA_FORMAT', 'block', 'abha', 'Enter an ABHA address like name@abdm, or the 14-digit ABHA number.', 'ABHA पता जैसे name@abdm, या 14 अंकों का ABHA नंबर लिखें।')] };
  }
  const next = { ...target, abha: address ? { address, consent: true, linkedOn: target.abha?.linkedOn || amdIsoDay(now) } : undefined, updatedAt: now.toISOString() };
  return { ok: true, versions: replace(versions, next), version: next };
}

/** Revoke in writing (L6): needs the date of the signed written revocation. Every open version ends. */
export function revokeAmd(versions: AmdVersion[], revocation: { revokedOn: string; writtenRevocationSigned: boolean; reason: string; scanKey?: string; scanSha256?: string }, now: Date): AmdResult {
  const issues: AmdIssue[] = [];
  if (!versions.some((v) => v.status === 'EXECUTED' || v.status === 'GENERATED' || v.status === 'DRAFT')) issues.push(issue('NOTHING', 'block', 'status', 'There is no living will to cancel.', 'रद्द करने के लिए कोई लिविंग विल नहीं है।'));
  if (!revocation.writtenRevocationSigned) issues.push(issue('L6_WRITTEN', 'block', 'revocation.written', 'A living will is cancelled in writing. Tick that the person signed a written cancellation.', 'लिविंग विल लिखित रूप में रद्द होती है। टिक करें कि व्यक्ति ने लिखित रद्दीकरण पर हस्ताक्षर किए हैं।'));
  if (!revocation.revokedOn) issues.push(issue('REVOKED_ON', 'block', 'revocation.revokedOn', 'Enter the date of the written cancellation.', 'लिखित रद्दीकरण की तारीख़ लिखें।'));
  else if (revocation.revokedOn > amdIsoDay(now)) issues.push(issue('REVOKED_FUTURE', 'block', 'revocation.revokedOn', 'The date cannot be in the future.', 'तारीख़ भविष्य की नहीं हो सकती।'));
  if (issues.length) return { ok: false, versions, issues };
  const at = now.toISOString();
  const record = { revokedOn: revocation.revokedOn, writtenRevocationSigned: true as const, reason: revocation.reason.trim(), recordedAt: at, scanKey: revocation.scanKey, scanSha256: revocation.scanSha256 };
  const updated = versions.map((v) => (v.status === 'EXECUTED' || v.status === 'GENERATED' || v.status === 'DRAFT' ? { ...v, status: 'REVOKED' as const, revocation: record, updatedAt: at } : v));
  return { ok: true, versions: updated, version: latestVersion(updated)! };
}

/**
 * Summary for the ED Quick View. EXECUTED (current) wins; otherwise the latest state so the ED can show
 * "revoked" or "not executed — not valid". null = no AMD recorded in Sahara.
 */
export function amdStatusSummary(versions: AmdVersion[]): AmdStatusSummary | null {
  const current = currentExecuted(versions);
  const pick = current ?? latestVersion(versions);
  if (!pick) return null;
  const attestor = pick.execution ? `${pick.execution.attestor.kind === 'NOTARY' ? 'Notary' : 'Gazetted officer'}: ${pick.execution.attestor.name}` : null;
  return { status: pick.status, executedOn: pick.execution?.signedOn ?? null, version: pick.version, attestor };
}
