// Goals-of-care conversation: a deterministic phrase matcher across English, Hindi, Urdu, Telugu and Kannada.
// It finds what the conversation SAID about each field of the emergency care plan and hands back the exact words.
// It never recommends, and a decision that was not addressed gets no suggestion: rule V1, null means standard care.
// A bare mention ("the doctor explained the ventilator") gives no choice, only a ticked topic. A choice needs a stated want or refusal.
// Nothing here writes to a plan by itself: the screen calls applyGocDecision only after the doctor confirms that item.
import { defaultTrialPlan, emptyHomeCare, surrogateEntriesLocked, treatmentLocked } from './ectpr-rules.ts';
import type { DecisionMaker, EctprRecord, GoalOfCare, HospitalTransfer, RespCeiling, TransferReason } from './ectpr-model.ts';

export type GocDecisionField =
  | 'goalOfCare' | 'cpr' | 'hospitalTransfer' | 'ambulance' | 'respCeiling' | 'icu' | 'vasopressors' | 'dialysis' | 'ng' | 'peg' | 'hydration'
  | 'patientKnows' | 'familyAskedNotToTell';
export type GocSpeaker = 'DOCTOR' | 'PATIENT_PARTY' | null;
export type GocLine = { text: string; speaker: GocSpeaker };
export type GocSuggestion = {
  field: GocDecisionField;
  /** Options the words fit. One = the suggested option. Several = "said yes", and the doctor picks which. Empty = only ruled something out. */
  values: string[];
  /** Options the words said no to, when no single option follows (for example "no ventilator" says nothing about NIV). */
  ruledOut: string[];
  /** Copied exactly from the transcript. */
  quote: string;
  source: 'phrases' | 'ai';
  reasons?: TransferReason[];
  maxAgents?: number;
};
export type GocQuote = { quote: string };
export type GocHomeCareHint = { field: 'assessmentFrequency' | 'coordinator' | 'ngoContact' | 'previousAdmission'; quote: string };
export type GocPersonHint = { relation: string; name: string; quote: string };
export type GocTopicId =
  | 'icu' | 'respCeiling' | 'dialysis' | 'vasopressors' | 'ng' | 'peg' | 'hydration' | 'cpr' | 'hospitalTransfer' | 'ambulance' | 'goalOfCare'
  | 'understanding' | 'patientKnows' | 'familyAskedNotToTell' | 'values' | 'decisionMakers' | 'homeCare';
export type GocTopicCoverage = { topic: GocTopicId; discussed: boolean; quote: string };
export type GocExtraction = {
  suggestions: GocSuggestion[]; ownWords: GocQuote[]; unacceptableOutcomes: GocQuote[]; homeCare: GocHomeCareHint[]; decisionMakers: GocPersonHint[]; topics: GocTopicCoverage[];
};

/** The checklist order. It follows how the palliative team asked doctors to be prompted: ICU, ventilator, dialysis, then on through the form. */
export const GOC_TOPIC_ORDER: readonly GocTopicId[] = ['icu', 'respCeiling', 'dialysis', 'vasopressors', 'ng', 'peg', 'hydration', 'cpr', 'hospitalTransfer', 'ambulance', 'goalOfCare', 'understanding', 'patientKnows', 'familyAskedNotToTell', 'values', 'decisionMakers', 'homeCare'];
export const GOC_DECISION_FIELDS: readonly GocDecisionField[] = ['icu', 'respCeiling', 'dialysis', 'vasopressors', 'ng', 'peg', 'hydration', 'cpr', 'hospitalTransfer', 'ambulance', 'goalOfCare', 'patientKnows', 'familyAskedNotToTell'];

// ---------------------------------------------------------------------------
// Regular expression helpers. Latin words need whole-word matches. Indian-script terms take suffixes ("వెంటిలేటర్‌ను"), so only the left edge is checked.

const LEFT = '(?<![\\p{L}\\p{M}\\p{N}])';
const RIGHT = '(?![\\p{L}\\p{M}\\p{N}])';
function words(latin: string[], native: string[] = []) {
  const parts: string[] = [];
  if (latin.length) parts.push(`${LEFT}(?:${latin.join('|')})${RIGHT}`);
  if (native.length) parts.push(`${LEFT}(?:${native.join('|')})`);
  return parts.join('|');
}
const rx = (latin: string[], native: string[] = []) => new RegExp(words(latin, native), 'iu');
/** Verbs in Telugu and Kannada join onto the word before them ("చేయవద్దు", "ಮಾಡಬೇಡಿ"), so these match anywhere in the word. */
const loose = (native: string[]) => native.join('|');

/** Same length as the input, so positions stay valid for quoting: Arabic-script letter variants and curly apostrophes only. */
function fold(text: string) {
  return text.replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/ه/g, 'ہ').replace(/[’‘]/g, "'");
}

// ---------------------------------------------------------------------------
// What was said about a term: a refusal, a want, or nothing usable.

/** strict: nothing but other treatments and small words may sit between this cue and the term ("no pain and dialysis" says nothing about dialysis). */
type Cue = { re: RegExp; polarity: 'yes' | 'no'; before: number; after: number; preferAfter: boolean; strict?: boolean };

const NO_EN = new RegExp(words(["do(?:es)? ?n[o']?t (?:really )?(?:want|wish|need|agree|like)(?: to)?(?: have| be put on| go on| do| start| put)?", 'did ?n.?t (?:want|agree)', "would ?n.?t (?:want|like|accept)", 'would not (?:want|like|accept)', "wo ?n.?t (?:want|accept|have)", 'will not (?:want|accept|have)', "do ?n.?t (?:do|put|give|start|use|try|bring|take|go)", 'do not (?:do|put|give|start|use|try|bring|take|go)', 'not to (?:have|do|put|give|start|use|go|be)', 'not (?:be )?(?:go|taken|take|put|started)(?: on| to)?', 'never(?: want| put| do| give| go)?', 'refuse[sd]?', 'refusing', 'decline[sd]?', 'say(?:s|ing)? no to', 'said no to', 'no to', 'does ?n.?t want']), 'iu');
const NO_BARE = new RegExp(words(['no(?: more| further| need for| need of| need to)?', 'against', 'avoid', 'without']), 'iu');
const NO_AFTER = new RegExp(words(['not (?:needed|required|wanted|necessary|acceptable|ok|okay|fine)', 'is not (?:needed|required|wanted|necessary|acceptable|ok|okay|fine)']), 'iu');
/** "Ventilator, no." A "no" after the term counts only when it ends the phrase: "dialysis no pain" is about pain. */
const NO_AFTER_WORD = /(?<![\p{L}\p{M}\p{N}])no(?=\s*(?:[,.;!।]|$))/iu;
const YES_EN = new RegExp(words(['wants?(?: to(?: have| go| be| try| do)?)?', 'wishe?s?(?: to(?: have)?)?', "(?:'d|would) like(?: to(?: have| try)?)?", 'agrees?(?: to)?', 'agreed(?: to)?', 'accepts?', 'accepted', 'okay with', 'ok with', 'fine with', 'happy with', 'yes(?: to)?', 'please (?:do|give|start|put|try)', 'go ahead(?: with)?', 'prefers?', 'keen on']), 'iu');
const YES_VERB = new RegExp(words(['continue', 'give', 'start', 'do', 'take', 'bring', 'shift']), 'iu');
const YES_AFTER = new RegExp(words(['is (?:ok|okay|fine|acceptable|alright)', 'are (?:ok|okay|fine|acceptable)', 'ok', 'okay', 'yes', 'please']), 'iu');
const NO_HI = rx([], ['मत(?![\\p{L}\\p{M}])(?:\\s+\\S+){0,2}', 'न ले(?:\\s+\\S+)?', 'नहीं चाहिए', 'नहीं चाहते', 'नहीं चाहती', 'नहीं चाहता', 'नहीं करना', 'नहीं करवाना', 'नहीं करवानी', 'नहीं डालना', 'नहीं लगाना', 'नहीं देना', 'नहीं लेना', 'नहीं जाना', 'नहीं ले जाना', 'नहीं रखना', 'मत करना', 'मत करो', 'मत करें', 'मत डालना', 'मत लगाना', 'मत देना', 'मत ले जाना', 'न करें', 'न करना', 'न डालें', 'न लगाएँ', 'न लगाएं', 'न दें', 'न ले जाएँ', 'न ले जाएं', 'मना', 'इनकार', 'इंकार', 'बिना', 'nahi chahiye', 'nahin chahiye', 'nahi chahte', 'nahi chahta', 'nahi chahti', 'mat karna', 'mat karo', 'mat lagana', 'nahi karna', 'nahi karwana', 'nahi lagana', 'nahi jana', 'nahi dena', 'nahi lena', 'na karein', 'vaddu', 'oddu', 'beda', 'bedaa']);
const NO_HI_END = /(?:नहीं|nahi|nahin)\s*$/iu;
const YES_HI = rx([], ['चाहिए', 'चाहते हैं', 'चाहती हैं', 'चाहता हूँ', 'चाहती हूँ', 'चाहेंगे', 'करवाना है', 'करना है', 'कर दीजिए', 'कीजिए', 'करें', 'लगाइए', 'लगा दीजिए', 'ठीक है', 'मंज़ूर', 'मंजूर', 'हाँ', 'हां', 'ले जाइए', 'ले जाएँ', 'ले जाएं', 'जाना है', 'chahiye', 'chahte hain', 'chahti hain', 'karna hai', 'karwana hai', 'theek hai', 'thik hai', 'kavali', 'kaavali', 'beku']);
const NO_UR = rx([], ['مت(?![\\p{L}\\p{M}])(?:\\s+\\S+){0,2}', 'نہ لے(?:\\s+\\S+)?', 'نہیں چاہیے', 'نہیں چاہتے', 'نہیں چاہتی', 'نہیں چاہتا', 'نہیں کرنا', 'نہیں کروانا', 'نہیں لگانا', 'نہیں ڈالنا', 'نہیں لینا', 'نہیں جانا', 'نہیں لے جانا', 'مت کرنا', 'مت کریں', 'مت لگانا', 'نہ کریں', 'نہ لگائیں', 'نہ ڈالیں', 'نہ لے جائیں', 'منع', 'انکار', 'بغیر']);
const NO_UR_END = /نہیں\s*$/u;
const YES_UR = rx([], ['چاہیے', 'چاہتے ہیں', 'چاہتی ہیں', 'چاہتا ہوں', 'چاہتی ہوں', 'کرنا ہے', 'کروانا ہے', 'کر دیں', 'لگائیں', 'ٹھیک ہے', 'منظور', 'ہاں', 'لے جائیں', 'جانا ہے']);
const NO_TE = new RegExp(loose(['కండి', 'వద్దు', 'వద్దని', 'వద్దం', 'కూడదు', 'ఒద్దు', 'పెట్టొద్దు', 'చేయొద్దు', 'వేయొద్దు', 'తీసుకెళ్ళొద్దు', 'తీసుకెళ్లొద్దు', 'వెళ్ళొద్దు', 'అక్కర్లేదు', 'అవసరం లేదు', 'ఇష్టం లేదు', 'తిరస్కరి', 'లేకుండా']), 'u');
const YES_TE = new RegExp(loose(['కావాలి', 'కావాలని', 'ఇష్టం', 'సరే', 'ఒప్పుకుంటా', 'చేయండి', 'పెట్టండి', 'వేయండి', 'తీసుకెళ్లండి', 'తీసుకెళ్ళండి', 'అంగీకరి']), 'u');
const NO_KN = new RegExp(loose(['ಬೇಕಾಗಿಲ್ಲ', 'ಬೇಕಿಲ್ಲ', 'ಬೇಡ', 'ಬೇಡಿ', 'ಅಗತ್ಯವಿಲ್ಲ', 'ಒಪ್ಪುವುದಿಲ್ಲ', 'ಒಪ್ಪಲ್ಲ', 'ಇಷ್ಟವಿಲ್ಲ', 'ನಿರಾಕರಿಸ', 'ಇಲ್ಲದೆ']), 'u');
const YES_KN = new RegExp(loose(['ಬೇಕು', 'ಬೇಕಾಗಿದೆ', 'ಇಷ್ಟ', 'ಸರಿ', 'ಒಪ್ಪಿಗೆ', 'ಒಪ್ಪುತ್ತೇ', 'ಮಾಡಿ', 'ಹಾಕಿ', 'ಕರೆದೊಯ್ಯಿರಿ']), 'u');

const CUES: Cue[] = [
  { re: NO_EN, polarity: 'no', before: 60, after: 0, preferAfter: false },
  { re: NO_BARE, polarity: 'no', before: 40, after: 0, preferAfter: false, strict: true },
  { re: NO_AFTER, polarity: 'no', before: 0, after: 8, preferAfter: false },
  { re: NO_AFTER_WORD, polarity: 'no', before: 0, after: 3, preferAfter: false },
  { re: YES_EN, polarity: 'yes', before: 40, after: 0, preferAfter: false },
  { re: YES_VERB, polarity: 'yes', before: 12, after: 0, preferAfter: false },
  { re: YES_AFTER, polarity: 'yes', before: 0, after: 8, preferAfter: false },
  { re: NO_HI, polarity: 'no', before: 16, after: 50, preferAfter: true },
  { re: NO_HI_END, polarity: 'no', before: 0, after: 4, preferAfter: true },
  { re: YES_HI, polarity: 'yes', before: 16, after: 50, preferAfter: true },
  { re: NO_UR, polarity: 'no', before: 16, after: 50, preferAfter: true },
  { re: NO_UR_END, polarity: 'no', before: 0, after: 4, preferAfter: true },
  { re: YES_UR, polarity: 'yes', before: 16, after: 50, preferAfter: true },
  { re: NO_TE, polarity: 'no', before: 16, after: 50, preferAfter: true },
  { re: YES_TE, polarity: 'yes', before: 16, after: 50, preferAfter: true },
  { re: NO_KN, polarity: 'no', before: 16, after: 50, preferAfter: true },
  { re: YES_KN, polarity: 'yes', before: 16, after: 50, preferAfter: true },
];

/** Words of doubt, or talk about other people or the past: the sentence states no decision. */
const UNSURE = rx(['not sure', 'unsure', "do ?n.?t know", 'do not know', 'undecided', "have ?n.?t decided", 'not decided', 'think about', 'think over', 'maybe', 'perhaps', 'later'],
  ['पता नहीं', 'नहीं पता', 'मालूम नहीं', 'सोचेंगे', 'सोचना', 'शायद', 'तय नहीं', 'पता नہیں', 'پتا نہیں', 'پتہ نہیں', 'نہیں پتا', 'معلوم نہیں', 'سوچیں گے', 'شاید', 'తెలియదు', 'ఆలోచి', 'నిర్ణయించలేదు', 'ಗೊತ್ತಿಲ್ಲ', 'ಯೋಚಿಸ', 'ಬಹುಶಃ', 'ನಿರ್ಧರಿಸಿಲ್ಲ']);
const NOT_A_DECISION = rx(['some patients?', 'many patients?', 'other patients?', 'some people', 'many people', 'most people', 'some families', 'usually', 'generally', 'in general', 'means?', 'what is', 'what does', 'explain(?:ed|s|ing)?', 'for example', 'last year', 'last month', 'last time', 'years? ago', 'months? ago', 'previously', 'in the past', 'had to', 'we can', 'we could', 'you can', 'you could', 'you may', 'options?', 'if you want', 'if they want', 'what if', 'told (?:him|her|them) about', 'neighbou?r', 'on tv'],
  ['कुछ मरीज़', 'कुछ मरीज', 'बहुत से', 'ज़्यादातर', 'मतलब', 'उदाहरण', 'पिछली बार', 'पिछले साल', 'पिछले महीने', 'विकल्प', 'अगर आप चाहें', 'कर सकते', 'समझाया', 'کچھ مریض', 'مطلب', 'پچھلی بار', 'پچھلے سال', 'اگر آپ چاہیں', 'کر سکتے', 'سمجھایا', 'కొందరు', 'అంటే', 'గత', 'పోయిన', 'కావాలంటే', 'చేయవచ్చు', 'వివరించ', 'ಕೆಲವರು', 'ಅಂದರೆ', 'ಕಳೆದ', 'ಹಿಂದಿನ', 'ಬೇಕಾದರೆ', 'ಮಾಡಬಹುದು', 'ವಿವರಿಸ']);
/** Stopping something already running is a different decision from starting it, and the plan's options do not say it. Give no mark. */
const STOPPING = rx(['stop', 'stopping', 'withdraw\\w*', 'discontinue\\w*', 'remov(?:e|ing)', 'take off', 'switch off', 'turn off'], ['रोक', 'बंद कर', 'हटा', 'روک', 'بند کر', 'ہٹا', 'ఆప', 'తీసేయ', 'ನಿಲ್ಲಿಸ', 'ತೆಗೆ']);
const UNLESS = rx(['unless', 'until'], ['जब तक', 'جب تک', 'తప్ప', 'ಹೊರತು']);
const DOUBLE_NEGATIVE = rx(['no objections?(?: to| with)?', "(?:do|does|did) ?n.?t (?:mind|object to)", '(?:do|does|did) not (?:mind|object to)', 'no (?:problem|issue)s? with'], ['कोई आपत्ति नहीं', 'आपत्ति नहीं', 'एतराज़ नहीं', 'ऐतराज़ नहीं', 'एतराज नहीं', 'اعتراض نہیں', 'అభ్యంతరం లేదు', 'ಆಕ್ಷೇಪ ಇಲ್ಲ', 'ಅಭ್ಯಂತರ ಇಲ್ಲ']);
const QUESTION_START = new RegExp(`^\\s*(?:${words(['do (?:you|they|we|i)', 'does (?:he|she|the|your)', 'did (?:you|he|she|they|we)', 'would', 'will (?:you|he|she|they|we)', 'should', 'can', 'could', 'shall', 'is', 'are', 'what(?! matters)', 'how', 'which', 'who', 'when', 'why', 'kya'], ['क्या', 'کیا'])})`, 'iu');
const QUESTION_WORD = /(?:కావాలా|వద్దా|చేయాలా|పెట్టాలా|ఏమిటి|ఎలా|ಬೇಕಾ(?![\p{L}\p{M}])|ಬೇಕೇ(?![\p{L}\p{M}])|ಬೇಡವೇ|ಬೇಡವಾ|ಏನು|ಹೇಗೆ|या नहीं|کہ نہیں|یا نہیں|do you want|would you (?:like|want)|what do you)/iu;

const TRIAL = rx(['trial', 'time[- ]limited', 'for (?:a )?(?:few|some|couple of|\\d+) (?:days?|sessions?|weeks?)', 'short time', 'try (?:it )?for'], ['ट्रायल', 'कुछ दिन', 'कुछ दिनों', 'ٹرائل', 'کچھ دن', 'ట్రయల్', 'కొన్ని రోజులు', 'ಟ್ರಯಲ್', 'ಕೆಲವು ದಿನ']);
const ONLY = rx(['only', 'just'], ['सिर्फ़', 'सिर्फ', 'केवल', 'बस', 'तभी', 'صرف', 'بس']);
const ONLY_NATIVE = /మాత్రమే|మాత్రం|ಮಾತ್ರ/u;
const REVERSIBLE = rx(['reversible', 'recover(?:s|able)?', 'get(?:s)? better', 'can improve'], ['ठीक हो सक', 'ٹھیک ہو سک', 'కోలుకుంటే', 'నయమయ్యే', 'ಗುಣವಾಗುವ', 'ಸರಿಯಾಗುವ']);
const PALLIATIVE = rx(['palliative'], ['पैलिएटिव', 'پیلی ایٹو', 'పాలియేటివ్', 'ಪಾಲಿಯೇಟಿವ್']);

// ---------------------------------------------------------------------------
// Terms for each topic

const T_ICU = rx(['icu', 'i\\.c\\.u\\.?', 'intensive care', 'critical care'], ['आईसीयू', 'आई\\.सी\\.यू', 'गहन चिकित्सा', 'آئی سی یو', 'ఐసీయూ', 'ఐసియు', 'ಐಸಿಯು', 'ಐ\\.ಸಿ\\.ಯು']);
const T_VENT = rx(['ventilators?', 'breathing machines?', 'breathing tubes?', 'intubat\\w*', 'life support'], ['वेंटिलेटर', 'वेंटीलेटर', 'साँस की मशीन', 'सांस की मशीन', 'साँस की नली', 'सांस की नली', 'وینٹیلیٹر', 'وینٹی لیٹر', 'ونٹیلیٹر', 'سانس کی مشین', 'వెంటిలేటర్', 'శ్వాస యంత్రం', 'ವೆಂಟಿಲೇಟರ್', 'ಉಸಿರಾಟದ ಯಂತ್ರ']);
const T_NIV = rx(['niv', 'bipap', 'cpap', 'bi-pap', 'non-?invasive'], ['बाइपैप', 'बायपैप', 'सीपैप', 'بائی پیپ', 'బైపాప్', 'ಬೈಪ್ಯಾಪ್']);
const T_HFNC = rx(['hfnc', 'high[- ]flow'], ['हाई फ्लो', 'हाई-फ़्लो', 'ہائی فلو', 'హై ఫ్లో', 'ಹೈ ಫ್ಲೋ']);
const T_MASK2 = rx(['non-?rebreather', 'venturi'], ['नॉन-रीब्रीदर', 'वेंचुरी']);
const T_O2 = rx(['oxygen', 'nasal prongs?', 'nasal cannula', 'simple mask'], ['ऑक्सीजन', 'آکسیجن', 'ఆక్సిజన్', 'ಆಮ್ಲಜನಕ', 'ಆಕ್ಸಿಜನ್']);
const T_DIALYSIS = rx(['dialysis', 'kidney machines?'], ['डायलिसिस', 'गुर्दे की मशीन', 'ڈائیلاسز', 'ڈائیلیسس', 'ڈائلیسس', 'ڈائلیسز', 'گردے کی مشین', 'డయాలసిస్', 'ಡಯಾಲಿಸಿಸ್']);
const T_VASO = rx(['vasopressors?', 'pressors?', 'noradrenaline', 'norepinephrine', 'blood pressure support', 'drugs? to support (?:the )?blood pressure'], ['वैसोप्रेसर', 'ब्लड-प्रेशर सहारा', 'ویسوپریسر', 'వాసోప్రెసర్', 'ವಾಸೊಪ್ರೆಸರ್']);
const T_NG = rx(['ng tubes?', 'ng', 'ryles?(?: tube)?', "ryle's(?: tube)?", 'nose tubes?', 'nasal tubes?'], ['नाक की नली', 'राइल्स', 'ناک کی نلی', 'ముక్కు గొట్టం', 'ముక్కు ట్యూబ్', 'ಮೂಗಿನ ಟ್ಯೂಬ್', 'ಮೂಗಿನ ನಳಿಕೆ']);
const T_PEG = rx(['peg', 'peg tube', 'stomach tubes?', 'gastrostomy'], ['पेट की नली', 'پیٹ کی نلی', 'కడుపు గొట్టం', 'కడుపు ట్యూబ్', 'ಹೊಟ್ಟೆಯ ಟ್ಯೂಬ್', 'ಹೊಟ್ಟೆಯ ನಳಿಕೆ']);
const T_FEEDTUBE = rx(['feeding tubes?', 'tube feed\\w*', 'food through a tube'], ['खाने की नली', 'फीडिंग ट्यूब', 'فیڈنگ ٹیوب', 'خوراک کی نلی', 'ఫీడింగ్ ట్యూబ్', 'ఆహార గొట్టం', 'ಫೀಡಿಂಗ್ ಟ್ಯೂಬ್', 'ಆಹಾರ ನಳಿಕೆ']);
const T_FLUIDS = rx(['drips?', 'iv fluids?', 'fluids?', 'saline', 'subcutaneous fluids?'], ['ड्रिप', 'सेलाइन', 'तरल', 'ڈرپ', 'سیلائن', 'డ్రిప్', 'సెలైన్', 'ಡ್ರಿಪ್', 'ಸಲೈನ್']);
const T_CPR = rx(['cpr', 'c\\.p\\.r\\.?', 'c p r', 'resuscitat\\w*', 'restart the heart', 'chest compressions?', 'pump(?:ing)? the chest'], ['सीपीआर', 'सी\\.पी\\.आर', 'सी पी आर', 'سی پی آر', 'సీపీఆర్', 'సిపిఆర్', 'ಸಿಪಿಆರ್', 'ಸಿ\\.ಪಿ\\.ಆರ್']);
const T_DNR = rx(['dnr', 'dnar', 'dnacpr'], ['डीएनआर', 'ڈی این آر', 'డీఎన్ఆర్', 'ಡಿಎನ್ಆರ್']);
const T_NO_CPR_PHRASE = rx(['do not resuscitate', 'no resuscitation', 'not (?:be )?resuscitated', "do ?n.?t resuscitate"]);
const T_HAS_DNR = rx(['(?:has|have|signed|got)(?: a| an| the)? (?:dnr|dnar|dnacpr)', '(?:dnr|dnar|dnacpr) (?:order|form|is in place)']);
const T_HOSPITAL = rx(['hospitals?', 'admit(?:ted)?', 'admission', 'aspatal', 'haspatal'], ['अस्पताल', 'हॉस्पिटल', 'भर्ती', 'ہسپتال', 'اسپتال', 'ہاسپٹل', 'ఆసుపత్రి', 'హాస్పిటల్', 'ఆస్పత్రి', 'ಆಸ್ಪತ್ರೆ', 'ಹಾಸ್ಪಿಟಲ್']);
const T_STAY_HOME = rx(['stay(?:ing)? at home', 'stay home', 'at home only', 'die at home', 'remain at home'], ['ghar par hi', 'ghar pe hi', 'घर पर ही', 'घर में ही', 'घर पर रहना', 'घर पर रहें', 'گھر پر ہی', 'گھر میں ہی', 'گھر پر رہنا', 'ఇంట్లోనే', 'ಮನೆಯಲ್ಲೇ', 'ಮನೆಯಲ್ಲಿಯೇ']);
const T_AMBULANCE = rx(['ambulances?', '108'], ['एम्बुलेंस', 'एंबुलेंस', 'ایمبولینس', 'అంబులెన్స్', 'ಆಂಬ್ಯುಲೆನ್ಸ್']);
const T_GOAL_LIFE = rx(['do everything', 'everything possible', 'all (?:the )?treatments?', 'full treatment', 'fight (?:this|it|on)', 'prioriti[sz]e life', 'live as long', 'as long as possible'], ['सब कुछ कर', 'पूरा इलाज', 'हर इलाज', 'जो भी हो सके', 'سب کچھ کر', 'پورا علاج', 'ہر علاج', 'అన్నీ చేయ', 'పూర్తి చికిత్స', 'ಎಲ್ಲಾ ಚಿಕಿತ್ಸೆ', 'ಪೂರ್ಣ ಚಿಕಿತ್ಸೆ']);
const T_GOAL_LIMITS = rx(['within limits', 'with limits', 'balanced', 'treat what can (?:be treated|get better)', 'treat only what can', 'up to a limit', 'certain limit'], ['सीमा के अंदर', 'सीमित इलाज', 'जो ठीक हो सके', 'حد کے اندر', 'محدود علاج', 'పరిమితి', 'పరిమిత చికిత్స', 'ಮಿತಿಯೊಳಗೆ', 'ಸೀಮಿತ ಚಿಕಿತ್ಸೆ']);
const T_GOAL_COMFORT = rx(['sirf aaram', 'bas aaram', 'comfort only', 'only comfort', 'just comfort', 'keep (?:him|her|me|them) comfortable', 'comfort care', 'prioriti[sz]e comfort', 'no suffering', 'without suffering', 'peaceful(?:ly)?', 'without pain'], ['सिर्फ़ आराम', 'सिर्फ आराम', 'केवल आराम', 'बस आराम', 'आराम से जाने', 'शांति से', 'तकलीफ़ न हो', 'तकलीफ न हो', 'صرف آرام', 'بس آرام', 'سکون سے', 'تکلیف نہ ہو', 'సౌకర్యం మాత్రమే', 'ప్రశాంతంగా', 'నొప్పి లేకుండా', 'ಆರಾಮ ಮಾತ್ರ', 'ನೆಮ್ಮದಿಯಾಗಿ', 'ನೋವಿಲ್ಲದೆ', 'ಶಾಂತಿಯಿಂದ']);
const T_UNDERSTANDING = rx(['not curable', 'cannot be cured', "can ?n.?t be cured", 'incurable', 'no cure', 'prognosis', 'how long', 'time left', 'terminal'], ['ठीक नहीं हो', 'इलाज नहीं है', 'लाइलाज', 'ٹھیک نہیں ہو', 'لاعلاج', 'నయం కాదు', 'నయం కాని', 'ಗುಣವಾಗುವುದಿಲ್ಲ', 'ಗುಣಮುಖ']);
const T_ILLNESS = rx(['illness', 'cancer', 'diagnosis', 'disease', 'prognosis', 'condition'], ['बीमारी', 'कैंसर', 'بیماری', 'کینسر', 'జబ్బు', 'వ్యాధి', 'క్యాన్సర్', 'ಕಾಯಿಲೆ', 'ಕ್ಯಾನ್ಸರ್', 'ರೋಗ']);
const KNOWS_NO = rx(["do(?:es)? ?n.?t know", 'does not know', 'do not know', 'not been told', "has ?n.?t been told", 'not aware', 'unaware'], ['नहीं जानते', 'नहीं जानती', 'नहीं जानता', 'पता नहीं', 'मालूम नहीं', 'नहीं पता', 'नहीं बताया', 'معلوم نہیں', 'نہیں جانتے', 'نہیں جانتی', 'پتا نہیں', 'پتہ نہیں', 'نہیں بتایا', 'తెలియదు', 'చెప్పలేదు', 'ಗೊತ್ತಿಲ್ಲ', 'ಹೇಳಿಲ್ಲ']);
const KNOWS_YES = rx(['knows?', 'been told', 'aware', 'understands?'], ['पता है', 'जानते हैं', 'जानती हैं', 'जानता है', 'मालूम है', 'معلوم ہے', 'جانتے ہیں', 'جانتی ہیں', 'پتا ہے', 'پتہ ہے', 'తెలుసు', 'ಗೊತ್ತಿದೆ', 'ಗೊತ್ತು']);
const NOT_TO_TELL = new RegExp(`${words(["do ?n.?t (?:tell|inform)", 'do not (?:tell|inform)', 'not to (?:tell|inform)', 'keep (?:it|this) from', 'hide (?:it|this) from', 'not be told', 'should not know'], ['मत बताना', 'मत बताइए', 'मत बताओ', 'मत बताएं', 'न बताएँ', 'न बताएं', 'न बताया जाए', 'नहीं बताना', 'مت بتائیں', 'مت بتانا', 'نہ بتائیں', 'نہیں بتانا'])}|${loose(['చెప్పకండి', 'చెప్పొద్దు', 'చెప్పవద్దు', 'ಹೇಳಬೇಡಿ', 'ಹೇಳಬೇಡ'])}`, 'iu');
const T_MATTERS = rx(['what matters', 'matters most', 'most important']);
const T_FIRST_PERSON = rx(['i', "i'd", "i'm", 'my', 'me'], ['मैं', 'मेरी', 'मेरा', 'मुझे', 'میں', 'میری', 'میرا', 'مجھے', 'నేను', 'నాకు', 'నా', 'ನಾನು', 'ನನಗೆ', 'ನನ್ನ']);
const T_WISH = rx(['want', 'wish', 'hope', 'would like', 'matters?', 'important', 'afraid', 'fear', 'worry', 'worried'], ['चाहता', 'चाहती', 'इच्छा', 'ज़रूरी', 'जरूरी', 'डर', 'चिंता', 'چاہتا', 'چاہتی', 'خواہش', 'ضروری', 'ڈر', 'فکر', 'కోరిక', 'ఇష్టం', 'భయం', 'ముఖ్యం', 'కావాలి', 'ಆಸೆ', 'ಇಷ್ಟ', 'ಭಯ', 'ಮುಖ್ಯ', 'ಬೇಕು']);
const T_UNACCEPTABLE = rx(["would ?n.?t (?:want to )?live", 'would not (?:want to )?live', 'bed-?ridden', 'bed bound', 'on machines', 'kept alive', 'not recogni[sz]e', "can ?n.?t recogni[sz]e", 'a burden', 'be a burden', 'cannot (?:eat|walk|talk)', "can ?n.?t (?:eat|walk|talk)", 'never accept', 'worse than death', 'would not accept', "would ?n.?t accept"], ['बोझ', 'बिस्तर पर पड़े', 'पहचान न', 'मशीनों पर', 'मंज़ूर नहीं', 'मंजूर नहीं', 'بوجھ', 'بستر پر', 'پہچان نہ', 'مشینوں پر', 'منظور نہیں', 'భారం', 'మంచం పట్టి', 'గుర్తు పట్ట', 'యంత్రాల మీద', 'ಹೊರೆ', 'ಹಾಸಿಗೆ ಹಿಡಿ', 'ಗುರುತಿಸಲು', 'ಯಂತ್ರಗಳ ಮೇಲೆ']);
const T_RELATION = new RegExp(words(['son', 'daughter', 'wife', 'husband', 'brother', 'sister', 'father', 'mother', 'nephew', 'niece', 'partner', 'friend', 'daughter-in-law', 'son-in-law', 'guardian'], ['बेटा', 'बेटे', 'बेटी', 'पत्नी', 'पति', 'भाई', 'बहन', 'माँ', 'मां', 'पिता', 'साथी', 'दोस्त', 'بیٹا', 'بیٹے', 'بیٹی', 'بیوی', 'شوہر', 'بھائی', 'بہن', 'ماں', 'والد', 'కొడుకు', 'కూతురు', 'భార్య', 'భర్త', 'అన్న', 'తమ్ముడు', 'అక్క', 'చెల్లి', 'ಮಗಳು', 'ಮಗ', 'ಹೆಂಡತಿ', 'ಗಂಡ', 'ಅಣ್ಣ', 'ಅಕ್ಕ', 'ತಂಗಿ']), 'iu');
const T_DECIDE = rx(['decide', 'decides', 'decision', 'decisions', 'speak for', 'speak on', 'in charge', 'power of attorney', 'representative'], ['फ़ैसला', 'फैसला', 'निर्णय', 'فیصلہ', 'నిర్ణయం', 'నిర్ణయి', 'ನಿರ್ಧಾರ', 'ನಿರ್ಧರಿ']);
const T_HOME_CARE = rx(['home care', 'home nurse', 'home visit', 'home visits', 'hospice'], ['घर पर देखभाल', 'घर पर नर्स', 'گھر پر دیکھ بھال', 'گھر پر نرس', 'ఇంటి వద్ద', 'ఇంట్లో నర్సు', 'ಮನೆ ಆರೈಕೆ', 'ಮನೆಯಲ್ಲಿ ಆರೈಕೆ']);
const T_NURSE = rx(['nurse', 'doctor', 'team', 'visits?'], ['नर्स', 'डॉक्टर', 'टीम', 'نرس', 'ڈاکٹر', 'ٹیم', 'నర్సు', 'నర్స్', 'డాక్టర్', 'ನರ್ಸ್', 'ಡಾಕ್ಟರ್']);
const T_FREQUENCY = rx(['every (?:day|week|month|\\d+ days)', 'once a', 'twice a', 'times a', 'daily', 'weekly'], ['हर हफ़्ते', 'हर हफ्ते', 'हफ़्ते में', 'हफ्ते में', 'रोज़', 'रोज', 'ہر ہفتے', 'ہفتے میں', 'روزانہ', 'వారానికి', 'ప్రతి రోజు', 'ರಾಜ', 'ವಾರಕ್ಕೆ', 'ಪ್ರತಿದಿನ']);
const T_NGO = rx(['ngo', 'n\\.g\\.o\\.?'], ['एनजीओ', 'این جی او', 'ఎన్జీఓ', 'ఎన్‌జీఓ', 'ಎನ್‌ಜಿಒ', 'ಎನ್ಜಿಒ']);
const T_COORDINATOR = rx(['coordinator'], ['समन्वयक', 'कोऑर्डिनेटर', 'کوآرڈینیٹر', 'కోఆర్డినేటర్', 'ಸಂಯೋಜಕ']);
const T_PREV_ADMIT = rx(['admitted before', 'admitted earlier', 'admitted last', 'previous admission', 'was admitted', 'been admitted', 'hospitali[sz]ed before'], ['पहले भर्ती', 'پہلے داخل', 'ఇంతకు ముందు చేర్చ', 'ముందు అడ్మిట్', 'ಹಿಂದೆ ದಾಖಲ']);

const REASON_TERMS: Array<[TransferReason, RegExp]> = [
  ['PAIN', rx(['pain'], ['दर्द', 'درد', 'నొప్పి', 'ನೋವು'])],
  ['BREATHLESSNESS', rx(['breathless\\w*', 'breathing trouble'], ['साँस', 'सांस', 'سانس', 'ఆయాసం', 'ఉసిరి', 'ಉಸಿರಾಟ'])],
  ['BLEEDING', rx(['bleed\\w*'], ['ख़ून', 'खून', 'خون', 'రక్తస్రావం', 'ರಕ್ತಸ್ರಾವ'])],
  ['SEIZURES', rx(['seizures?', 'fits?'], ['दौरे', 'دورے', 'మూర్ఛ', 'ಸೆಳವು'])],
  ['FALL_FRACTURE', rx(['falls?', 'fracture'], ['गिर', 'گر جا', 'పడిపో', 'ಬಿದ್ದ'])],
  ['BLOCKED_TUBE', rx(['blocked (?:tube|catheter|stent)', 'catheter'], ['कैथेटर', 'کیتھیٹر', 'కాథెటర్', 'ಕ್ಯಾತಿಟರ್'])],
  ['INFECTION_IV', rx(['infection'], ['संक्रमण', 'انفیکشن', 'ఇన్ఫెక్షన్', 'ಸೋಂಕು'])],
  ['CONFUSION', rx(['confus\\w*', 'restless\\w*'], ['उलझन', 'बेचैनी', 'الجھن', 'بے چینی', 'గందరగోళం', 'ಗೊಂದಲ'])],
  ['CAREGIVER_RESPITE', rx(['cannot cope', "can ?n.?t cope", 'respite'], ['संभाल नहीं', 'سنبھال نہیں', 'చూసుకోలేక', 'ನೋಡಿಕೊಳ್ಳಲಾಗ'])],
];

const TOPIC_TERMS: Record<GocTopicId, RegExp[]> = {
  icu: [T_ICU], respCeiling: [T_VENT, T_NIV, T_HFNC, T_MASK2, T_O2], dialysis: [T_DIALYSIS], vasopressors: [T_VASO], ng: [T_NG, T_FEEDTUBE], peg: [T_PEG, T_FEEDTUBE], hydration: [T_FLUIDS],
  cpr: [T_CPR, T_DNR], hospitalTransfer: [T_HOSPITAL, T_STAY_HOME], ambulance: [T_AMBULANCE], goalOfCare: [T_GOAL_LIFE, T_GOAL_LIMITS, T_GOAL_COMFORT], understanding: [T_UNDERSTANDING],
  patientKnows: [T_ILLNESS], familyAskedNotToTell: [NOT_TO_TELL], values: [T_UNACCEPTABLE], decisionMakers: [T_DECIDE], homeCare: [T_HOME_CARE, T_NGO, T_COORDINATOR],
};

// ---------------------------------------------------------------------------
// Lines and sentences

const DOCTOR_PREFIX = /^\s*(?:doctor|dr\.?|consultant|nurse|डॉक्टर|डॉ\.?|ڈاکٹر|డాక్టర్|ಡಾಕ್ಟರ್|ವೈದ್ಯರು)\s*[:：\-–]\s*/iu;
const PARTY_PREFIX = /^\s*(?:patient|family|son|daughter|wife|husband|relative|attender|मरीज़|मरीज|परिवार|बेटा|बेटी|पत्नी|पति|مریض|خاندان|بیٹا|بیٹی|రోగి|కుటుంబం|ರೋಗಿ|ಕುಟುಂಬ)\s*[:：\-–]\s*/iu;

/** Splits pasted text or a machine draft into one line per sentence. "Doctor:" or "Family:" at the start sets the side; it is never guessed. */
export function gocLinesFromText(text: string): GocLine[] {
  const out: GocLine[] = [];
  for (const raw of String(text ?? '').normalize('NFC').split(/\r?\n/)) {
    const doctor = DOCTOR_PREFIX.exec(raw);
    const party = doctor ? null : PARTY_PREFIX.exec(raw);
    const body = (doctor ? raw.slice(doctor[0].length) : party ? raw.slice(party[0].length) : raw).trim();
    if (!body) continue;
    const speaker: GocSpeaker = doctor ? 'DOCTOR' : party ? 'PATIENT_PARTY' : null;
    for (const [start, end] of sentenceSpans(body)) {
      const part = body.slice(start, end).trim();
      if (part) out.push({ text: part, speaker });
    }
  }
  return out;
}

export function gocTextFromLines(lines: GocLine[]): string {
  return lines.map((line) => line.text).join('\n');
}

/** Removes sound captions the speech model writes, such as "[crying]" or "(sobs)". The verbatim transcript keeps them. */
export function withoutSoundCaptions(text: string): string {
  return text.replace(/\[[^\]]{0,40}\]|\((?:[^)]{0,30})\)|♪+|\*[^*]{0,30}\*/gu, ' ').replace(/\s{2,}/g, ' ').trim();
}

function sentenceSpans(text: string): Array<[number, number]> {
  const spans: Array<[number, number]> = [];
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    const stop = c === '\n' || c === '!' || c === '?' || c === '؟' || c === '।' || c === '۔' || c === '॥'
      || (c === '.' && !/\d/.test(text[i + 1] ?? '') && !/(?:\b(?:dr|mr|mrs|ms)|[A-Za-z]\.[A-Za-z]|\b[A-Za-z])$/i.test(text.slice(Math.max(0, i - 4), i)));
    if (stop) { if (i + 1 > start) spans.push([start, i + 1]); start = i + 1; }
  }
  if (start < text.length) spans.push([start, text.length]);
  return spans;
}

const CLAUSE_BREAK = new RegExp(`;|${words(['but', 'however', 'though', 'although', 'whereas', 'except'], ['लेकिन', 'मगर', 'किंतु', 'परंतु', 'لیکن', 'مگر', 'కానీ', 'అయితే', 'ಆದರೆ'])}`, 'giu');

type Sentence = { start: number; end: number; text: string; folded: string; speaker: GocSpeaker; question: boolean };
type Clause = { start: number; end: number; text: string; speaker: GocSpeaker };

function isQuestion(text: string): boolean {
  return /[?؟]\s*$/.test(text) || QUESTION_START.test(text) || QUESTION_WORD.test(text);
}

function readLines(input: string | GocLine[]): { text: string; folded: string; sentences: Sentence[] } {
  const lines: GocLine[] = typeof input === 'string' ? [{ text: input.normalize('NFC'), speaker: null }] : input.map((line) => ({ ...line, text: line.text.normalize('NFC') }));
  let text = '';
  const sentences: Sentence[] = [];
  for (const line of lines) {
    const base = text.length;
    text += line.text;
    for (const [s, e] of sentenceSpans(line.text)) {
      const body = line.text.slice(s, e);
      if (!body.trim()) continue;
      sentences.push({ start: base + s, end: base + e, text: body, folded: fold(body), speaker: line.speaker, question: isQuestion(fold(body)) });
    }
    text += '\n';
  }
  return { text: text.replace(/\n$/, ''), folded: fold(text), sentences };
}

function clausesOf(sentence: Sentence): Clause[] {
  const out: Clause[] = [];
  let at = 0;
  for (const match of sentence.folded.matchAll(CLAUSE_BREAK)) {
    if (match.index > at) out.push({ start: sentence.start + at, end: sentence.start + match.index, text: sentence.folded.slice(at, match.index), speaker: sentence.speaker });
    at = match.index + match[0].length;
  }
  if (at < sentence.folded.length) out.push({ start: sentence.start + at, end: sentence.end, text: sentence.folded.slice(at), speaker: sentence.speaker });
  return out;
}

function allMatches(re: RegExp, text: string) {
  const global = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return [...text.matchAll(global)].filter((m) => m[0].length).map((m) => ({ start: m.index, end: m.index + m[0].length }));
}

/** The stated want or refusal nearest to the term in this clause. Refusals are found first, and a want inside one ("do not want") does not count. */
function polarityNear(fullClause: string, term: { start: number; end: number }): 'yes' | 'no' | null {
  // "No objection to dialysis" is a yes. Those words are taken out first so their "no" cannot be read as a refusal.
  let clause = fullClause;
  for (const m of allMatches(DOUBLE_NEGATIVE, fullClause)) {
    if (m.end <= term.start ? term.start - m.end <= 40 : m.start - term.end <= 40) return 'yes';
    clause = clause.slice(0, m.start) + ' '.repeat(m.end - m.start) + clause.slice(m.end);
  }
  const noSpans = CUES.filter((c) => c.polarity === 'no').flatMap((c) => allMatches(c.re, clause));
  let best: { score: number; polarity: 'yes' | 'no' } | null = null;
  for (const cue of CUES) {
    for (const m of allMatches(cue.re, clause)) {
      if (m.end > term.start && m.start < term.end) continue;
      if (cue.polarity === 'yes' && noSpans.some((n) => m.start < n.end && n.start < m.end)) continue;
      const isBefore = m.end <= term.start;
      const distance = isBefore ? term.start - m.end : m.start - term.end;
      if (distance > (isBefore ? cue.before : cue.after)) continue;
      const between = isBefore ? clause.slice(m.end, term.start) : clause.slice(term.end, m.start);
      if (cue.strict ? !onlyListWords(between) : (LIST_JOIN.test(between) && !onlyListWords(between))) continue;
      const score = distance + (isBefore && cue.preferAfter ? 100 : 0) + (cue.polarity === 'yes' ? 0.5 : 0);
      if (!best || score < best.score) best = { score, polarity: cue.polarity };
    }
  }
  return best?.polarity ?? null;
}

const ALL_TREATMENT_TERMS = [T_ICU, T_VENT, T_NIV, T_HFNC, T_MASK2, T_O2, T_DIALYSIS, T_VASO, T_NG, T_PEG, T_FEEDTUBE, T_FLUIDS, T_CPR, T_DNR, T_HOSPITAL];
const LIST_JOIN = /,|(?<![\p{L}\p{M}])(?:and|or|nor|और|या|اور|یا|మరియు|లేదా|ಮತ್ತು|ಅಥವಾ)(?![\p{L}\p{M}])/iu;
const LIST_WORDS = /(?<![\p{L}\p{M}])(?:and|or|nor|a|an|the|any|more|further|of|on|to|for|trial|starting|start|going|being|put|use|using|his|her|their|my|और|या|की|का|के|में|पर|اور|یا|کی|کا|کے|میں|پر|మరియు|లేదా|ಮತ್ತು|ಅಥವಾ)(?![\p{L}\p{M}])/giu;
/** True when the text between a cue and a term holds only other treatments and small joining words, so the cue still reaches the term. */
function onlyListWords(between: string): boolean {
  let rest = between;
  for (const term of ALL_TREATMENT_TERMS) rest = rest.replace(new RegExp(term.source, 'giu'), ' ');
  return !rest.replace(LIST_WORDS, ' ').replace(/[\s,.;:!-]+/gu, '').length;
}

const CEILING_ORDER = ['0', '1', '2', '3', '4', '5'];
const fromLevel = (level: number) => CEILING_ORDER.slice(level);

type Rule = { field: GocDecisionField; term: RegExp; decide: (polarity: 'yes' | 'no', clause: string) => Pick<GocSuggestion, 'values' | 'ruledOut'> & Partial<GocSuggestion> };

const yesNo = (yes: string[], no: string[]): Rule['decide'] => (polarity) => ({ values: polarity === 'yes' ? yes : no, ruledOut: [] });
const withTrial = (yes: string[], trialValue: string): Rule['decide'] => (polarity, clause) => {
  if (polarity === 'no') return { values: ['NO'], ruledOut: [] };
  return { values: TRIAL.test(clause) ? [trialValue] : yes, ruledOut: [] };
};
const ceiling = (level: number): Rule['decide'] => (polarity) => (polarity === 'yes' ? { values: [String(level)], ruledOut: [] } : { values: [], ruledOut: fromLevel(level) });

const reversibleCondition = (clause: string) => REVERSIBLE.test(clause) && (ONLY.test(clause) || ONLY_NATIVE.test(clause) || UNLESS.test(clause) || /\bif\b/i.test(clause) || /अगर|اگر/u.test(clause));
const onlyForReason = (clause: string) => (ONLY.test(clause) || ONLY_NATIVE.test(clause)) && REASON_TERMS.some(([, re]) => re.test(clause));

const RULES: Rule[] = [
  { field: 'icu', term: T_ICU, decide: withTrial(['YES'], 'TRIAL') },
  { field: 'respCeiling', term: T_VENT, decide: ceiling(5) },
  { field: 'respCeiling', term: T_NIV, decide: ceiling(4) },
  { field: 'respCeiling', term: T_HFNC, decide: ceiling(3) },
  { field: 'respCeiling', term: T_MASK2, decide: ceiling(2) },
  { field: 'respCeiling', term: T_O2, decide: ceiling(1) },
  { field: 'dialysis', term: T_DIALYSIS, decide: (polarity, clause) => {
    if (reversibleCondition(clause)) return { values: ['ONLY_IF_REVERSIBLE'], ruledOut: [] };
    if (polarity === 'no') return { values: ['NO'], ruledOut: [] };
    return { values: TRIAL.test(clause) ? ['TRIAL'] : ['YES'], ruledOut: [] };
  } },
  { field: 'vasopressors', term: T_VASO, decide: (polarity, clause) => {
    if (polarity === 'no') return { values: ['NO'], ruledOut: [] };
    if (TRIAL.test(clause)) return { values: ['TRIAL'], ruledOut: [] };
    const max = /up to (\d+|one|two|three)\b/i.exec(clause);
    if (max) return { values: ['YES_MAX_AGENTS'], ruledOut: [], maxAgents: { one: 1, two: 2, three: 3 }[max[1].toLowerCase()] ?? Number(max[1]) };
    return { values: ['YES_NO_LIMIT', 'YES_MAX_AGENTS'], ruledOut: [] };
  } },
  { field: 'ng', term: T_NG, decide: withTrial(['YES'], 'UP_TO_DAYS') },
  { field: 'ng', term: T_FEEDTUBE, decide: withTrial(['YES'], 'UP_TO_DAYS') },
  { field: 'peg', term: T_PEG, decide: (polarity, clause) => (polarity === 'no' ? { values: ['NO'], ruledOut: [] } : { values: (ONLY.test(clause) || ONLY_NATIVE.test(clause)) && PALLIATIVE.test(clause) ? ['ONLY_AFTER_PALLIATIVE_DISCUSSION'] : ['YES'], ruledOut: [] }) },
  { field: 'peg', term: T_FEEDTUBE, decide: yesNo(['YES'], ['NO']) },
  { field: 'hydration', term: T_FLUIDS, decide: (polarity, clause) => {
    if (polarity === 'no') return { values: ['ORAL_SIPS'], ruledOut: [] };
    if (rx(['limited', 'a little', 'small amount'], ['सीमित', 'थोड़ा', 'محدود', 'تھوڑا', 'పరిమిత', 'ಸೀಮಿತ']).test(clause)) return { values: ['LIMITED'], ruledOut: [] };
    if (rx(['full', 'all'], ['पूरे', 'पूरा', 'پورا', 'పూర్తి', 'ಪೂರ್ಣ']).test(clause)) return { values: ['FULL'], ruledOut: [] };
    return { values: ['LIMITED', 'FULL'], ruledOut: [] };
  } },
  { field: 'cpr', term: T_CPR, decide: yesNo(['ATTEMPT'], ['DNACPR']) },
  { field: 'cpr', term: T_DNR, decide: yesNo(['DNACPR'], ['ATTEMPT']) },
  { field: 'hospitalTransfer', term: T_HOSPITAL, decide: (polarity, clause) => {
    if (polarity === 'no') return { values: ['NO'], ruledOut: [] };
    const reasons = REASON_TERMS.filter(([, re]) => re.test(clause)).map(([reason]) => reason);
    if ((ONLY.test(clause) || ONLY_NATIVE.test(clause)) && reasons.length) return { values: ['ONLY_FOR'], ruledOut: [], reasons };
    return { values: ['ANY'], ruledOut: [] };
  } },
];

type Direct = { field: GocDecisionField; term: RegExp; values: string[] };
const DIRECT: Direct[] = [
  { field: 'goalOfCare', term: T_GOAL_LIFE, values: ['A_LIFE'] },
  { field: 'goalOfCare', term: T_GOAL_LIMITS, values: ['B_CEILING'] },
  { field: 'goalOfCare', term: T_GOAL_COMFORT, values: ['C_COMFORT'] },
  { field: 'hospitalTransfer', term: T_STAY_HOME, values: ['NO'] },
  { field: 'cpr', term: T_NO_CPR_PHRASE, values: ['DNACPR'] },
  { field: 'cpr', term: T_HAS_DNR, values: ['DNACPR'] },
];

const ANSWER_TAIL = '(?:[\\s,]+(?:please|thanks|thank you|doctor|sir|madam|ji|जी|डॉक्टर|ڈاکٹر|డాక్టర్|ಡಾಕ್ಟರ್))?';
const YES_ANSWER = new RegExp(`^\\s*(?:yes|yeah|yep|ok|okay|sure|please|हाँ|हां|हाँ जी|जी हाँ|جی|جی ہاں|ہاں|అవును|సరే|కావాలి|ಹೌದು|ಸರಿ|ಬೇಕು|चाहिए|ٹھیک ہے|چاہیے)${ANSWER_TAIL}[\\s.!,।۔]*$`, 'iu');
const NO_ANSWER = new RegExp(`^\\s*(?:no|nope|नहीं|ना|नहीं चाहिए|نہیں|نہیں چاہیے|వద్దు|లేదు|ಬೇಡ|ಇಲ್ಲ)${ANSWER_TAIL}[\\s.!,।۔]*$`, 'iu');

function dedupe<T extends { quote: string }>(items: T[], key: (item: T) => string): T[] {
  const seen = new Set<string>();
  return items.filter((item) => { const k = key(item); if (seen.has(k)) return false; seen.add(k); return true; });
}

/**
 * Reads a transcript. Lines the doctor marked as "doctor side" count for the topic checklist and as the question before an answer,
 * but a doctor's own sentence never becomes a suggested choice. Unlabelled lines count as possibly either side and are still only suggestions.
 */
export function extractGoc(input: string | GocLine[]): GocExtraction {
  const { text, sentences } = readLines(input);
  const suggestions: GocSuggestion[] = [];
  const ownWords: GocQuote[] = []; const unacceptableOutcomes: GocQuote[] = []; const homeCare: GocHomeCareHint[] = []; const decisionMakers: GocPersonHint[] = [];
  const quoteOf = (start: number, end: number) => text.slice(start, end).trim();

  sentences.forEach((sentence, index) => {
    const quote = quoteOf(sentence.start, sentence.end);
    const clean = sentence.folded;
    const partySide = sentence.speaker !== 'DOCTOR';

    // A short answer to the question before it: "Do you want ICU?" "No."
    if (partySide && (YES_ANSWER.test(clean) || NO_ANSWER.test(clean)) && index > 0 && sentences[index - 1].question) {
      const asked = sentences[index - 1];
      const hits = RULES.filter((rule) => rule.term.test(asked.folded));
      if (new Set(hits.map((hit) => hit.field)).size === 1 && !NOT_A_DECISION.test(asked.folded.replace(/do you want|would you like/gi, ''))) {
        const polarity = NO_ANSWER.test(clean) ? 'no' : 'yes';
        const seen = new Set<string>();
        for (const rule of hits) {
          if (seen.has(rule.field)) continue;
          seen.add(rule.field);
          suggestions.push({ field: rule.field, source: 'phrases', quote: quoteOf(asked.start, sentence.end), ...rule.decide(polarity, asked.folded) });
        }
      }
      return;
    }

    if (sentence.question || !partySide) return;

    for (const clause of clausesOf(sentence)) {
      const body = clause.text;
      if (UNSURE.test(body) || NOT_A_DECISION.test(body)) continue;
      for (const rule of DIRECT) {
        if (!rule.term.test(body)) continue;
        const refused = rule.field === 'goalOfCare' && polarityNear(body, allMatches(rule.term, body)[0]) === 'no' && !T_GOAL_COMFORT.test(body);
        if (!refused) suggestions.push({ field: rule.field, values: rule.values, ruledOut: [], quote, source: 'phrases' });
      }
      if (T_NO_CPR_PHRASE.test(body)) continue;
      if (STOPPING.test(body)) continue;
      for (const rule of RULES) {
        // "No ICU unless ..." is a condition this form cannot hold, so it gets no mark. Dialysis has its own "only if it can get better" option.
        if (UNLESS.test(body) && !(rule.field === 'dialysis' && reversibleCondition(body))) continue;
        for (const term of allMatches(rule.term, body)) {
          const trial = TRIAL.test(body) && ['icu', 'vasopressors', 'dialysis', 'ng'].includes(rule.field);
          const implied = trial || (rule.field === 'dialysis' && reversibleCondition(body)) || (rule.field === 'hospitalTransfer' && onlyForReason(body));
          const polarity = polarityNear(body, term) ?? (implied ? 'yes' : null);
          if (polarity) { suggestions.push({ field: rule.field, source: 'phrases', quote, ...rule.decide(polarity, body) }); break; }
        }
      }
    }

    if (T_ILLNESS.test(clean)) {
      if (KNOWS_NO.test(clean)) suggestions.push({ field: 'patientKnows', values: ['no'], ruledOut: [], quote, source: 'phrases' });
      else if (KNOWS_YES.test(clean)) suggestions.push({ field: 'patientKnows', values: ['yes'], ruledOut: [], quote, source: 'phrases' });
    }
    if (NOT_TO_TELL.test(clean)) suggestions.push({ field: 'familyAskedNotToTell', values: ['yes'], ruledOut: [], quote, source: 'phrases' });
    if (T_UNACCEPTABLE.test(clean)) unacceptableOutcomes.push({ quote });
    if (((T_FIRST_PERSON.test(clean) && T_WISH.test(clean)) || T_MATTERS.test(clean)) && !/not important/i.test(clean) && quote.length >= 12) ownWords.push({ quote });

    if (T_DECIDE.test(clean) && T_RELATION.test(clean)) {
      const relationMatch = new RegExp(T_RELATION.source, 'iu').exec(clean);
      const relation = relationMatch?.[0] ?? '';
      const after = relationMatch ? clean.slice((relationMatch.index ?? 0) + relation.length) : '';
      const name = /^[\s,]*([A-Z][a-z]{1,20})\b/.exec(after)?.[1] ?? '';
      const skip = new Set(['Will', 'Should', 'Can', 'And', 'Is', 'Must', 'Has', 'Would']);
      decisionMakers.push({ relation, name: skip.has(name) ? '' : name, quote });
    }
    if (T_FREQUENCY.test(clean) && T_NURSE.test(clean)) homeCare.push({ field: 'assessmentFrequency', quote });
    if (T_NGO.test(clean)) homeCare.push({ field: 'ngoContact', quote });
    if (T_COORDINATOR.test(clean)) homeCare.push({ field: 'coordinator', quote });
    if (T_PREV_ADMIT.test(clean)) homeCare.push({ field: 'previousAdmission', quote });
  });

  // Topics: any mention, on either side, ticks the box. Ticking says the talk covered it, never what was chosen.
  const topics: GocTopicCoverage[] = GOC_TOPIC_ORDER.map((topic) => {
    const hit = sentences.find((s) => TOPIC_TERMS[topic].some((re) => re.test(s.folded)) || (topic === 'values' && T_FIRST_PERSON.test(s.folded) && T_WISH.test(s.folded) && s.speaker !== 'DOCTOR') || (topic === 'decisionMakers' && T_RELATION.test(s.folded) && T_DECIDE.test(s.folded)));
    return { topic, discussed: Boolean(hit), quote: hit ? quoteOf(hit.start, hit.end) : '' };
  });

  return {
    suggestions: dedupe(suggestions, (s) => `${s.field}|${s.values.join()}|${s.ruledOut.join()}|${s.quote}`),
    ownWords: dedupe(ownWords, (q) => q.quote).slice(0, 8),
    unacceptableOutcomes: dedupe(unacceptableOutcomes, (q) => q.quote).slice(0, 8),
    homeCare: dedupe(homeCare, (h) => `${h.field}|${h.quote}`),
    decisionMakers: dedupe(decisionMakers, (p) => p.quote),
    topics,
  };
}

/** Topics covered so far, for the live checklist. */
export function gocTopicsCovered(input: string | GocLine[]): GocTopicCoverage[] {
  return extractGoc(input).topics;
}

// ---------------------------------------------------------------------------
// Reading from and writing to the draft plan. Option values match the enums in ectpr-model.ts.

export function gocCurrentValue(record: EctprRecord, field: GocDecisionField): string | null {
  const c = record.choices;
  switch (field) {
    case 'goalOfCare': return c.goalOfCare;
    case 'cpr': return c.cpr;
    case 'hospitalTransfer': return c.hospitalTransfer?.kind ?? null;
    case 'ambulance': return c.ambulance;
    case 'respCeiling': return c.respCeiling === null ? null : String(c.respCeiling);
    case 'icu': return c.icu?.kind ?? null;
    case 'vasopressors': return c.vasopressors?.kind ?? null;
    case 'dialysis': return c.dialysis?.kind ?? null;
    case 'ng': return c.feeding.ng?.kind ?? null;
    case 'peg': return c.feeding.peg;
    case 'hydration': return c.hydration;
    case 'patientKnows': return record.values.patientKnows === null ? null : record.values.patientKnows ? 'yes' : 'no';
    case 'familyAskedNotToTell': return record.values.familyAskedNotToTell ? 'yes' : null;
  }
}

/** The same gates as the wizard: V4 for treatment choices, V5 for anything a surrogate answers. */
export function gocFieldLock(record: EctprRecord, field: GocDecisionField): 'V4' | 'V5' | null {
  if (surrogateEntriesLocked(record)) return 'V5';
  if (field !== 'patientKnows' && field !== 'familyAskedNotToTell' && treatmentLocked(record)) return 'V4';
  return null;
}

/** Writes one confirmed decision into a draft, the same way the wizard's own controls do. Returns false, and changes nothing, when a gate is closed or the record is not a draft. */
export function applyGocDecision(record: EctprRecord, field: GocDecisionField, value: string | null, detail: { reasons?: TransferReason[]; maxAgents?: number } = {}): boolean {
  if (record.status !== 'DRAFT' || gocFieldLock(record, field)) return false;
  const c = record.choices;
  const keepTrial = (option: { kind: string; trial?: unknown } | null) => (option && option.kind === 'TRIAL' ? (option as { trial: ReturnType<typeof defaultTrialPlan> }).trial : null);
  switch (field) {
    case 'goalOfCare': c.goalOfCare = value as GoalOfCare | null; break;
    case 'cpr':
      c.cpr = value as EctprRecord['choices']['cpr'];
      if (value !== 'DNACPR') c.dnacprReason = null;
      break;
    case 'hospitalTransfer':
      c.hospitalTransfer = value === null ? null : value === 'ONLY_FOR'
        ? { kind: 'ONLY_FOR', reasons: detail.reasons ?? (c.hospitalTransfer?.kind === 'ONLY_FOR' ? c.hospitalTransfer.reasons : []) }
        : { kind: value } as HospitalTransfer;
      break;
    case 'ambulance': c.ambulance = value as EctprRecord['choices']['ambulance']; break;
    case 'respCeiling': c.respCeiling = value === null ? null : Number(value) as RespCeiling; break;
    case 'icu':
      c.icu = value === null ? null : value === 'TRIAL' ? { kind: 'TRIAL', trial: keepTrial(c.icu) ?? defaultTrialPlan() } : { kind: value } as EctprRecord['choices']['icu'];
      break;
    case 'vasopressors':
      c.vasopressors = value === null ? null : value === 'TRIAL' ? { kind: 'TRIAL', trial: keepTrial(c.vasopressors) ?? defaultTrialPlan() }
        : value === 'YES_MAX_AGENTS' ? { kind: 'YES_MAX_AGENTS', maxAgents: detail.maxAgents ?? (c.vasopressors?.kind === 'YES_MAX_AGENTS' ? c.vasopressors.maxAgents : 1) }
          : { kind: value } as EctprRecord['choices']['vasopressors'];
      break;
    case 'dialysis':
      c.dialysis = value === null ? null : value === 'TRIAL' ? { kind: 'TRIAL', trial: keepTrial(c.dialysis) ?? { ...defaultTrialPlan(), durationUnit: 'SESSIONS' } } : { kind: value } as EctprRecord['choices']['dialysis'];
      break;
    case 'ng':
      c.feeding.ng = value === null ? null : value === 'UP_TO_DAYS' ? { kind: 'UP_TO_DAYS', days: c.feeding.ng?.kind === 'UP_TO_DAYS' ? c.feeding.ng.days : 7 } : { kind: value } as EctprRecord['choices']['feeding']['ng'];
      break;
    case 'peg': c.feeding.peg = value as EctprRecord['choices']['feeding']['peg']; break;
    case 'hydration': c.hydration = value as EctprRecord['choices']['hydration']; break;
    case 'patientKnows': record.values.patientKnows = value === null ? null : value === 'yes'; break;
    case 'familyAskedNotToTell': record.values.familyAskedNotToTell = value === 'yes'; break;
  }
  return true;
}

/** Adds a quote the doctor accepted to a free-text part of the plan. Existing text is kept. */
export function applyGocQuote(record: EctprRecord, target: 'ownWords' | 'unacceptableOutcomes' | GocHomeCareHint['field'], quote: string): boolean {
  if (record.status !== 'DRAFT' || surrogateEntriesLocked(record)) return false;
  const text = quote.replace(/\s*\n\s*/g, ' ').trim();
  if (target === 'ownWords') record.values.ownWords = record.values.ownWords.trim() ? `${record.values.ownWords.trim()}\n${text}` : text;
  else if (target === 'unacceptableOutcomes') { if (!record.values.unacceptableOutcomes.includes(text)) record.values.unacceptableOutcomes.push(text); }
  else {
    const home = { ...(record.homeCare ?? emptyHomeCare()) };
    if (target === 'previousAdmission') { home.previousAdmission = true; home.previousAdmissionDetails = home.previousAdmissionDetails.trim() ? home.previousAdmissionDetails : text; }
    else home[target] = home[target].trim() ? `${home[target].trim()}; ${text}` : text;
    record.homeCare = home;
  }
  return true;
}

/** Puts a person the conversation named into an empty decision-maker slot, or on the list of who was present. Never overwrites. */
export function applyGocPerson(record: EctprRecord, hint: GocPersonHint, slot: 'primary' | 'alternate' | 'present'): boolean {
  if (record.status !== 'DRAFT') return false;
  const label = hint.name || hint.relation;
  if (slot === 'present') {
    const present = record.decisionMakers.presentAtDiscussion ?? [];
    const line = hint.name ? `${hint.name} (${hint.relation})` : hint.relation;
    if (!present.includes(line)) record.decisionMakers.presentAtDiscussion = [...present, line];
    return true;
  }
  if (record.decisionMakers[slot]) return false;
  const person: DecisionMaker = { name: label, relation: hint.relation, phone24h: '', idType: null, idLast4: null, authorityBasis: null };
  record.decisionMakers[slot] = person;
  return true;
}

// ---------------------------------------------------------------------------
// The next conversation: what the last version said, so only changes need editing

export type GocCarryRow = { field: GocDecisionField; previous: string | null; current: string | null; changed: boolean };

/** Compares a draft with the version it came from. An empty choice on either side stays empty (V1). */
export function gocCarryForward(previous: EctprRecord, draft: EctprRecord): GocCarryRow[] {
  return GOC_DECISION_FIELDS.map((field) => {
    const before = gocCurrentValue(previous, field); const now = gocCurrentValue(draft, field);
    return { field, previous: before, current: now, changed: before !== now };
  });
}

/** Options the conversation pointed at for this field, with their quotes. More than one value across quotes means it said different things. */
export function gocMarksFor(suggestions: GocSuggestion[], field: GocDecisionField) {
  const own = suggestions.filter((s) => s.field === field);
  const suggested = [...new Set(own.flatMap((s) => s.values))];
  const ruledOut = [...new Set(own.flatMap((s) => s.ruledOut))];
  return { own, suggested, ruledOut, conflict: new Set(own.filter((s) => s.values.length).map((s) => s.values.join())).size > 1 };
}

/** Checks an answer from a server model: unknown fields and any quote that is not word-for-word in the transcript are dropped. */
export const GOC_FIELD_VALUES: Record<GocDecisionField, readonly string[]> = {
  goalOfCare: ['A_LIFE', 'B_CEILING', 'C_COMFORT'], cpr: ['ATTEMPT', 'DNACPR'], hospitalTransfer: ['ANY', 'ONLY_FOR', 'NO'],
  ambulance: ['FULL_RESUSCITATION', 'SYMPTOM_RELIEF_TRANSPORT', 'TRANSPORT_HOME_NURSE_ESCORT'], respCeiling: CEILING_ORDER,
  icu: ['YES', 'TRIAL', 'NO'], vasopressors: ['NO', 'TRIAL', 'YES_NO_LIMIT', 'YES_MAX_AGENTS'], dialysis: ['NO', 'TRIAL', 'YES', 'ONLY_IF_REVERSIBLE'],
  ng: ['NO', 'UP_TO_DAYS', 'YES'], peg: ['NO', 'ONLY_AFTER_PALLIATIVE_DISCUSSION', 'YES'], hydration: ['ORAL_SIPS', 'LIMITED', 'FULL'],
  patientKnows: ['yes', 'no'], familyAskedNotToTell: ['yes'],
};

export function checkedAiSuggestions(answer: unknown, transcript: string): GocSuggestion[] {
  const items = (answer as { suggestions?: unknown } | null)?.suggestions;
  if (!Array.isArray(items)) return [];
  const source = transcript.normalize('NFC');
  const out: GocSuggestion[] = [];
  for (const item of items.slice(0, 40)) {
    const { field, value, quote } = (item ?? {}) as { field?: unknown; value?: unknown; quote?: unknown };
    if (typeof field !== 'string' || !(field in GOC_FIELD_VALUES) || typeof quote !== 'string') continue;
    const name = field as GocDecisionField;
    const words = quote.normalize('NFC').trim();
    if (words.length < 3 || !source.includes(words) || !GOC_FIELD_VALUES[name].includes(String(value))) continue;
    out.push({ field: name, values: [String(value)], ruledOut: [], quote: words, source: 'ai' });
  }
  return out;
}
