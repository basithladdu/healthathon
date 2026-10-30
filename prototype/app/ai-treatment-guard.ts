// SVK Saanthvana spec, "Won't": AI treatment advice. No AI route may recommend a treatment, a medicine or dose change, or a choice in an
// emergency care plan (ECTPR) or living will (CPR, ventilation, ICU, feeding, hospital transfer). AI may only find, quote, sort or
// translate what a person or their care team already wrote. These checks run before anything is sent and again on every answer.

export const TREATMENT_ADVICE_CODE = 'treatment_advice_refused';
export const TREATMENT_ADVICE_MESSAGE = 'Sahara does not give treatment advice or suggest choices for the emergency care plan or living will. '
  + 'Ask the palliative care team or your doctor. Sahara can only find what is already written in your documents.';

/** Added to every generative system prompt. */
export const NO_TREATMENT_ADVICE_RULE = 'Never give treatment advice: never recommend, suggest or rank a treatment, medicine, dose, test or hospital, '
  + 'and never suggest a choice about CPR, ventilation, ICU, intubation, dialysis, feeding tubes, hospital transfer, an emergency care plan (ECTPR) '
  + 'or a living will. If asked for any of these, return the "not found" answer.';

const CARE_TERMS = String.raw`cpr|resuscitat\w*|dnr|dnar|dni|ventilat\w*|intubat\w*|icu|dialysis|feeding\s+tube|ryles?|peg|chemo\w*|radiation|radiotherapy|surgery|operation|antibiotic\w*|morphine|opioid\w*|fentanyl|medicine\w*|medication\w*|drug\w*|dose\w*|dosage|tablet\w*|injection\w*|treatment\w*|therapy|sedat\w*|oxygen|transfusion|hospital|admi(?:t|ssion)\w*|living\s+will|advance\s+directive|ectpr|care\s+plan|ceiling\s+of\s+care`;
const ACTIONS = String.raw`give|take|start|stop|continue|increase|decrease|reduce|raise|double|skip|change|switch|choose|opt|refuse|accept|sign|go|use|try|ask\s+for|put|get|do|have`;

const ASKING: RegExp[] = [
  // "Should we choose CPR?", "Should my mother go on a ventilator?", "Must I sign the DNR?"
  new RegExp(String.raw`\b(?:should|shall|must|ought\s+to)\b[^?.!]{0,80}\b(?:${CARE_TERMS})\b`, 'iu'),
  // "Can I increase the morphine?", "Is it ok to stop the tablets?"
  new RegExp(String.raw`\b(?:can|could|may|is\s+it\s+(?:ok|okay|safe|fine|right|better))\b[^?.!]{0,40}\b(?:${ACTIONS})\b[^?.!]{0,60}\b(?:${CARE_TERMS})\b`, 'iu'),
  // "What medicine should I give?", "Which treatment is best?"
  new RegExp(String.raw`\b(?:what|which)\s+(?:${CARE_TERMS})\b[^?.!]{0,60}\b(?:should|best|better|to\s+(?:${ACTIONS})|do\s+you\s+(?:recommend|suggest))\b`, 'iu'),
  new RegExp(String.raw`\b(?:how\s+much|how\s+many)\b[^?.!]{0,60}\b(?:should|to\s+(?:give|take))\b`, 'iu'),
  new RegExp(String.raw`\b(?:recommend|advise|suggest|advice)\b[^?.!]{0,80}\b(?:${CARE_TERMS})\b`, 'iu'),
  new RegExp(String.raw`\b(?:${CARE_TERMS})\b[^?.!]{0,60}\b(?:recommend|advise|suggest|advice|best\s+option|right\s+choice)\b`, 'iu'),
  // Hindi (Devanagari and romanised)
  /(?:क्या|किसे|कब).{0,60}(?:देना|देनी|देने|लेना|लेनी|लेने|करना|करनी|करवाना|लगवाना|बंद|शुरू|बढ़ा|घटा).{0,20}(?:चाहिए|ठीक|सही)/u,
  /(?:कौन\s*सी|कौनसी|कौन\s*सा)\s*(?:दवा|दवाई|इलाज|उपचार)/u,
  /(?:दवा|दवाई|इलाज|उपचार|खुराक).{0,30}(?:बताओ|बताइए|बताएं|बताइये|सुझाओ|सुझाइए|सलाह)/u,
  /\b(?:kaun\s*si|konsi|kaunsi)\s+(?:dawa|dawai|davai|ilaj|ilaaj)\b/iu,
  /\b(?:dena|deni|lena|leni|karna|karni|band\s+karna|shuru\s+karna)\s+chahiye\b/iu,
  // Urdu
  /(?:دوا|علاج|خوراک).{0,40}(?:چاہیے|بتائیں|بتاؤ|مشورہ)/u,
  // Telugu: medicine/treatment + "should I give/use/take?"
  /(?:మందు|మందులు|చికిత్స|మోతాదు).{0,40}(?:ఇవ్వాలా|వాడాలా|తీసుకోవాలా|ఆపాలా|చెప్పండి|సలహా)/u,
  // Kannada: medicine/treatment + "should …?"
  /(?:ಔಷಧ|ಮಾತ್ರೆ|ಚಿಕಿತ್ಸೆ|ಡೋಸ್).{0,40}(?:ಕೊಡಬೇಕಾ|ತೆಗೆದುಕೊಳ್ಳಬೇಕಾ|ನಿಲ್ಲಿಸಬೇಕಾ|ಬೇಕಾ|ಬೇಕೇ|ಸಲಹೆ|ಹೇಳಿ)/u,
];

/** True when the question asks the AI to decide or recommend care, rather than to find what is written. */
export function asksForTreatmentAdvice(text: string): boolean {
  const value = text.normalize('NFC');
  if (DECIDING.test(value)) return true;
  // "When should I take the tablet?" / "What does the prescription say about the dose?" asks what is written: a cited look-up, not advice.
  if (LOOKUP.test(value)) return false;
  return ASKING.some((pattern) => pattern.test(value));
}
const LOOKUP = /^\s*(?:when|what\s+time|at\s+what\s+time)\b|\b(?:says?|said|written|wrote|according\s+to|mentioned|listed|prescribed|in\s+the\s+(?:note|report|prescription|summary))\b|लिखा|लिखी|के\s+अनुसार/iu;
const DECIDING = new RegExp(String.raw`\b(?:choose|opt\s+for|decide|recommend|advise|suggest|best\s+(?:option|choice|treatment)|right\s+choice)\b[^?.!]{0,80}\b(?:${CARE_TERMS})\b|\b(?:${CARE_TERMS})\b[^?.!]{0,60}\b(?:choose|opt\s+for|decide|recommend|advise|suggest)\b`, 'iu');

const ADVISING: RegExp[] = [
  /\b(?:you|we|they|he|she|the\s+patient|your\s+\w+)\s+(?:should|must|ought\s+to|need\s+to|have\s+to)\b/iu,
  /\b(?:i|we)\s+(?:would\s+)?(?:recommend|advise|suggest)\b/iu,
  /\b(?:is|are)\s+(?:recommended|advisable|advised)\b/iu,
  /\bit\s+(?:is|would\s+be)\s+(?:best|better|safer|wise)\s+to\b/iu,
  /\b(?:consider|try)\s+(?:starting|stopping|taking|increasing|reducing|giving|asking\s+for|choosing|signing)\b/iu,
  /\b(?:the\s+)?best\s+(?:option|choice|treatment)\s+(?:is|would\s+be)\b/iu,
  /(?:चाहिए|सलाह\s+(?:है|देते|दी\s+जाती)|बेहतर\s+होगा)/u,
  /(?:چاہیے|مشورہ\s+ہے|بہتر\s+ہوگا)/u,
  /(?:ఇవ్వాలి|వాడాలి|తీసుకోవాలి|సలహా)/u,
  /(?:ಕೊಡಬೇಕು|ತೆಗೆದುಕೊಳ್ಳಬೇಕು|ಸಲಹೆ)/u,
];

/**
 * True when generated text gives advice. Quoting a document is allowed: a phrase that also appears in the cited lines
 * (for example a prescription that says "should be taken after food") is a read-back, not advice from the AI.
 */
export function containsTreatmentAdvice(answer: string, citedText = ''): boolean {
  const cited = citedText.normalize('NFC').toLowerCase();
  for (const pattern of ADVISING) {
    const global = new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`);
    for (const match of answer.normalize('NFC').matchAll(global)) if (!cited.includes(match[0].toLowerCase())) return true;
  }
  return false;
}

/** Topics that are a plan or directive: a request tagged with one of these never reaches a generative model. */
export const CARE_PLAN_PURPOSES = new Set(['ectpr', 'emergency-care-plan', 'living-will', 'advance-directive', 'treatment-plan']);
export function isCarePlanAdvicePurpose(purpose: unknown) {
  return typeof purpose === 'string' && CARE_PLAN_PURPOSES.has(purpose.trim().toLowerCase());
}
