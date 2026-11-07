// The doctor-driven topic guide (Dr Sujay, 7 Nov 2026), twin of CareGocGuideRules.kt and the prompt text of CareGocGuideUi.kt.
// The doctor taps through the 13 topics of the checklist as the talk goes. No AI is involved. Topics are identified by their row index in
// GOC_CHECKLIST_ROWS; times are seconds from the recording start (-1 when no recording runs).
import { GOC_CHECKLIST_ROWS } from './goc-checklist.ts';

export type GocGuideState = { stamps: Record<number, number>; now: number };
export const GOC_GUIDE_EMPTY: GocGuideState = { stamps: {}, now: -1 };

export const GOC_ESSENTIAL_COUNT = 5;
export const GOC_UNDERSTANDING_TITLE = "Patient's understanding of disease";
const ESSENTIAL_TITLES = new Set([GOC_UNDERSTANDING_TITLE, 'Home care vs hospital transfer', 'CPR preference', 'ICU care', 'Ventilatory support']);

const keys = (stamps: Record<number, number>) => Object.keys(stamps).map(Number);

/** Started and finished: everything with a start time except the topic being discussed right now. */
export function gocGuideDiscussed(s: GocGuideState): Set<number> {
  return new Set(keys(s.stamps).filter((i) => i !== s.now));
}

export const gocIsEssential = (titleEn: string) => ESSENTIAL_TITLES.has(titleEn);

/** How many of the five essentials are discussed. [titles] are the English row titles in row order. */
export function gocEssentialsDone(discussed: Iterable<number>, titles: string[]): number {
  return [...discussed].filter((i) => i >= 0 && i < titles.length && ESSENTIAL_TITLES.has(titles[i])).length;
}

/** The next topic not yet discussed after [from], wrapping round; null when every topic is discussed. */
export function gocGuideNext(discussed: ReadonlySet<number>, from: number, count: number): number | null {
  if (count <= 0) return null;
  for (let step = 1; step <= count; step++) {
    const i = (((from + step) % count) + count) % count;
    if (!discussed.has(i)) return i;
  }
  return null;
}

/** Tapping a tile: it becomes "now discussing" and the previous one is left discussed at its own start time; tapping the current one finishes it. */
export function gocGuideTap(s: GocGuideState, idx: number, sec: number): GocGuideState {
  if (idx === s.now) return { stamps: s.stamps, now: -1 };
  return { stamps: { ...s.stamps, [idx]: s.stamps[idx] ?? sec }, now: idx };
}

/** "Done": the topic is discussed, stamped with the time it was marked unless it already had a start time. */
export function gocGuideDone(s: GocGuideState, idx: number, sec: number): GocGuideState {
  return { stamps: { ...s.stamps, [idx]: s.stamps[idx] ?? sec }, now: s.now === idx ? -1 : s.now };
}

/** True when a Section III topic was started while the patient's understanding of the disease has not been discussed. */
export function gocGuideOutOfOrder(started: number, discussed: ReadonlySet<number>, sections: string[], titles: string[]): boolean {
  const understanding = titles.indexOf(GOC_UNDERSTANDING_TITLE);
  if (started < 0 || started >= sections.length || understanding < 0 || started === understanding) return false;
  return sections[started] === 'III' && !discussed.has(understanding);
}

/** mm:ss from a number of seconds; "--:--" when no recording time is known. */
export function gocMmss(seconds: number): string {
  if (seconds < 0) return '--:--';
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export function gocGuideEncode(stamps: Record<number, number>): string {
  return keys(stamps).sort((a, b) => a - b).map((k) => `${k}:${stamps[k]}`).join(';');
}
export function gocGuideDecode(text: string): Record<number, number> {
  const out: Record<number, number> = {};
  for (const part of text.split(';')) {
    const [k, v] = part.split(':');
    const key = Number.parseInt(k ?? '', 10); const value = Number.parseInt(v ?? '', 10);
    if (Number.isFinite(key) && Number.isFinite(value)) out[key] = value;
  }
  return out;
}

export const GOC_ROW_TITLES = GOC_CHECKLIST_ROWS.map((row) => row.en);
export const GOC_ROW_SECTIONS: string[] = GOC_CHECKLIST_ROWS.map((row) => row.section);

type Bi = [string, string];
/** What the doctor says and what is decided for one topic, English and Hindi. In row order (twin of GUIDE_TEXT in CareGocGuideUi.kt). */
export type GocGuideText = { decide: Bi; say: Bi[]; answers: Bi[] };
export const GOC_GUIDE_TEXT: readonly GocGuideText[] = [
  { decide: ['Confirm who the patient is and how to reach them.', 'पुष्टि करें कि मरीज़ कौन हैं और उनसे कैसे संपर्क करें।'],
    say: [['Let me first check your name and contact details.', 'पहले मैं आपका नाम और संपर्क विवरण देख लूँ।'], ['Is this still the best number to reach you?', 'क्या अब भी इसी नंबर पर आपसे बात हो सकती है?']],
    answers: [['Details correct', 'विवरण सही है'], ['Needs updating', 'बदलना है']] },
  { decide: ['Who looks after the patient most, and how to reach them.', 'मरीज़ की सबसे ज़्यादा देखभाल कौन करता है, और उससे कैसे संपर्क करें।'],
    say: [['Who looks after you most at home?', 'घर पर आपकी सबसे ज़्यादा देखभाल कौन करता है?'], ['Who should we call first if something changes?', 'कुछ बदले तो हम सबसे पहले किसे फ़ोन करें?']],
    answers: [['Main caregiver named', 'मुख्य देखभालकर्ता का नाम'], ['Contact number noted', 'फ़ोन नंबर लिखा']] },
  { decide: ['Whether the patient can understand and decide for themselves.', 'क्या मरीज़ खुद समझकर निर्णय ले सकते हैं।'],
    say: [['Can you tell me in your own words what we are talking about today?', 'आज हम किस बारे में बात कर रहे हैं, क्या आप अपने शब्दों में बता सकते हैं?'], ['How comfortable do you feel making these decisions?', 'ये निर्णय लेने में आप कितना सहज महसूस करते हैं?']],
    answers: [['Can decide', 'खुद निर्णय ले सकते हैं'], ['Needs support to decide', 'निर्णय में सहारा चाहिए'], ['Cannot decide now', 'अभी निर्णय नहीं ले सकते']] },
  { decide: ['Any wishes or forms already written down.', 'पहले से लिखी कोई इच्छा या फ़ॉर्म।'],
    say: [['Have you written down any wishes about your care before?', 'क्या आपने पहले अपनी देखभाल के बारे में कोई इच्छा लिखी है?'], ['Is there a paper, or a person, we should know about?', 'क्या कोई कागज़ या कोई व्यक्ति है जिसके बारे में हमें जानना चाहिए?']],
    answers: [['None', 'कोई नहीं'], ['Written directive exists', 'लिखित निर्देश है'], ['Decision-maker chosen', 'निर्णय लेने वाला चुना गया']] },
  { decide: ['What the patient knows, and how much they want to know.', 'मरीज़ क्या जानते हैं, और कितना जानना चाहते हैं।'],
    say: [['What have the doctors told you about your illness so far?', 'अब तक डॉक्टरों ने आपको बीमारी के बारे में क्या बताया है?'], ['How much would you like me to share with you?', 'आप चाहेंगे कि मैं आपको कितना बताऊँ?']],
    answers: [['Understands', 'समझते हैं'], ['Partly understands', 'कुछ समझते हैं'], ['Prefers family to be told', 'परिवार को बताना ठीक']] },
  { decide: ['What matters most to the patient in the time ahead.', 'आने वाले समय में मरीज़ के लिए सबसे ज़रूरी क्या है।'],
    say: [['What matters most to you in the time ahead?', 'आने वाले समय में आपके लिए सबसे ज़रूरी क्या है?'], ['What worries you the most right now?', 'अभी आपको सबसे ज़्यादा किस बात की चिंता है?']],
    answers: [['Comfort', 'आराम'], ['Time with family', 'परिवार के साथ समय'], ['Being at home', 'घर पर रहना'], ['Staying alert', 'होश में रहना'], ['Spiritual needs', 'आध्यात्मिक ज़रूरतें']] },
  { decide: ['The main aim of care from here.', 'अब से देखभाल का मुख्य लक्ष्य।'],
    say: [['Given everything, what would you most hope for?', 'सब कुछ देखते हुए, आप सबसे ज़्यादा किसकी आशा रखते हैं?'], ['If time is limited, what would a good day look like?', 'अगर समय सीमित हो, तो आपके लिए एक अच्छा दिन कैसा होगा?']],
    answers: [['Live as long as possible', 'जितना हो सके जीवन बढ़ाना'], ['Comfort first', 'सबसे पहले आराम'], ['A balance of both', 'दोनों में संतुलन']] },
  { decide: ['Where the patient would like to be cared for if they become more unwell.', 'ज़्यादा तबीयत बिगड़ने पर मरीज़ कहाँ देखभाल चाहते हैं।'],
    say: [['If you become more unwell, where would you like to be looked after?', 'अगर तबीयत और बिगड़े, तो आप कहाँ देखभाल चाहेंगे?'], ['Would you want to go to hospital, or have us help you at home?', 'क्या आप अस्पताल जाना चाहेंगे, या हम घर पर ही मदद करें?']],
    answers: [['Stay home', 'घर पर रहें'], ['Hospital for symptoms only', 'सिर्फ़ तकलीफ़ के लिए अस्पताल'], ['Hospital for everything', 'हर बात के लिए अस्पताल']] },
  { decide: ['Whether to try to restart the heart or breathing if they stop.', 'अगर दिल या साँस रुक जाए तो उसे दोबारा चलाने की कोशिश करनी है या नहीं।'],
    say: [['If your heart were to stop, would you want us to try to restart it?', 'अगर आपका दिल रुक जाए, तो क्या आप चाहेंगे कि हम उसे दोबारा चलाने की कोशिश करें?'], ['Let me explain what CPR involves, and then I would like to hear your thoughts.', 'पहले मैं समझा दूँ कि CPR में क्या होता है, फिर मैं आपकी राय सुनना चाहूँगा।']],
    answers: [['Attempt CPR', 'CPR की कोशिश करें'], ['Do not attempt CPR', 'CPR की कोशिश न करें']] },
  { decide: ['Whether to be cared for in intensive care if very unwell.', 'बहुत बीमार होने पर गहन देखभाल (ICU) में रखना है या नहीं।'],
    say: [['If you became very unwell, would you want to be looked after in intensive care?', 'अगर आप बहुत बीमार हो जाएँ, तो क्या आप ICU में देखभाल चाहेंगे?'], ['Some people choose a short trial of ICU care. What feels right to you?', 'कुछ लोग ICU की थोड़े समय की कोशिश चुनते हैं। आपको क्या ठीक लगता है?']],
    answers: [['Full ICU care', 'पूरी ICU देखभाल'], ['Trial of ICU', 'ICU की कोशिश'], ['No ICU', 'ICU नहीं']] },
  { decide: ['What breathing support the patient would want if breathing became hard.', 'साँस लेना कठिन हो तो मरीज़ कौन सी मदद चाहते हैं।'],
    say: [['If breathing became hard, what kind of help would you want?', 'अगर साँस लेना कठिन हो जाए, तो आप किस तरह की मदद चाहेंगे?'], ['There are masks, and tubes with a machine. Shall I explain each one?', 'इसमें मास्क होते हैं, और मशीन वाली नली भी। क्या मैं हर एक समझाऊँ?']],
    answers: [['None', 'कोई नहीं'], ['Oxygen only', 'सिर्फ़ ऑक्सीजन'], ['NIV (BiPAP)', 'NIV (BiPAP)'], ['Invasive ventilation', 'नली वाली साँस की मशीन']] },
  { decide: ['Whether to have dialysis if the kidneys stop working.', 'अगर गुर्दे काम करना बंद कर दें तो डायलिसिस कराना है या नहीं।'],
    say: [['If your kidneys stopped working, would you want dialysis?', 'अगर आपके गुर्दे काम करना बंद कर दें, तो क्या आप डायलिसिस चाहेंगे?'], ['Some people try it for a while and then review. How do you feel about that?', 'कुछ लोग इसे कुछ समय आज़माकर फिर देखते हैं। इस बारे में आप क्या सोचते हैं?']],
    answers: [['Yes', 'हाँ'], ['Trial', 'आज़माकर देखें'], ['No', 'नहीं']] },
  { decide: ['How to help with food and fluids if eating becomes difficult.', 'खाना मुश्किल हो जाए तो भोजन और पानी में कैसे मदद करें।'],
    say: [['If eating became difficult, how would you like us to help?', 'अगर खाना मुश्किल हो जाए, तो आप चाहेंगे कि हम कैसे मदद करें?'], ['Is a feeding tube or a drip something you would want?', 'क्या आप नली से खाना या ड्रिप चाहेंगे?']],
    answers: [['Normal diet', 'सामान्य भोजन'], ['NG tube', 'नाक की नली (NG)'], ['PEG', 'PEG'], ['IV fluids', 'नस से तरल'], ['None', 'कोई नहीं']] },
];

export const GOC_SECTION_TITLES: Record<'I' | 'II' | 'III', Bi> = {
  I: ['SECTION I', 'खंड I'],
  II: ['SECTION II', 'खंड II'],
  III: ['SECTION III · DECISIVE ACTIONS', 'खंड III · निर्णायक कदम'],
};
