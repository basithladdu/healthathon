// The goals-of-care note IS the clinical document: Healthathon_form_Rituparna.docx (legal review, 31 Oct 2026), with Rituparna's tracked
// additions, in source-materials/team-context/legal-review-2026-10-31/. The form's own wording, headings, numbering (4.1, 4.2, 5.3, 6.1,
// 6.2, 12.1, Section 19, 19.1 to 19.4), order, tick boxes and tables, filled in like a completed paper form. The screen, print, Word file
// and the Android PDF all draw this one document. Line-by-line map: docs/goc-legal-form-coverage.md.
// Built only from what the doctor confirmed: no spoken quotes, nothing pre-selected (rule V1). A blank nobody filled reads "Not discussed".
import { careText, type CareLang } from './care-i18n.ts';
import { cprLabel, dialysisLabel, goalLabel, icuLabel, ngLabel, pegLabel, transferLabel, trialLabel, vasopressorLabel } from './ectpr-labels.ts';
import { withoutSoundCaptions, type GocTopicId } from './goc-extract.ts';
import type { AuthorityBasis, DecisionMaker, EctprRecord, Signature, TransferReason } from './ectpr-model.ts';

/** One tick box of the paper form. [on] = ticked; [detail] is what goes in the form's blank (days, hours, L/min, a name). */
export type GocFormTick = { text: string; on: boolean; detail?: string };
/**
 * One line of the form. [value] fills the blank ('' when the boxes carry the answer). [ticks] are the form's boxes, in its order.
 * [open] = nothing filled, shown as "Not discussed".
 */
export type GocFormRow = { label: string; value: string; open?: boolean; ticks?: GocFormTick[] };
/** A table of the form (decision-makers, the oxygen LEVEL / WHAT IT MEANS / CEILING table, Section 19). Cell lines that start with a box are boxes. */
export type GocFormTable = { head: string[]; rows: string[][] };
export type GocFormGroup = { title: string; rows: GocFormRow[]; table?: GocFormTable };
export type GocFormSection = { id: 'I' | 'II' | 'III' | 'IV'; title: string; groups: GocFormGroup[] };
export type GocFormDocument = { title: string; lead: string[]; sections: GocFormSection[] };
type Row = { topic: GocTopicId; status: 'DISCUSSED' | 'NOT_DISCUSSED'; verified: boolean };
/** Signatures drawn on the doctor's device for this note (goc-signoff.ts), so Section 19 shows them. */
export type GocFormDeviceSignature = { role: string; name: string; method: string; signedAt: string; relation?: string | null; registrationNo?: string | null };

/** Where each guide row sits on the clinical document. */
export function gocFormSection(topic: GocTopicId): 'I' | 'II' | 'III' | null {
  if (topic === 'consent') return null;
  if (topic === 'patientDetails' || topic === 'decisionMakers') return 'I';
  if (topic === 'understanding' || topic === 'patientKnows' || topic === 'familyAskedNotToTell' || topic === 'values') return 'II';
  return 'III';
}

export const GOC_TICK_ON = '☑';
export const GOC_TICK_OFF = '☐';
const box = (on: boolean) => (on ? GOC_TICK_ON : GOC_TICK_OFF);

/** Form 6.2, in its order. A recorded outcome that matches none of them goes under "Other". */
export const GOC_UNACCEPTABLE_OUTCOMES: Array<[string, string]> = [
  ['Being kept alive on a ventilator with no prospect of coming off it', 'ऐसे वेंटिलेटर पर ज़िंदा रखा जाना जिससे हटने की कोई उम्मीद न हो'],
  ['Being permanently dependent on a feeding tube', 'हमेशा के लिए खाने की नली पर निर्भर रहना'],
  ['Not being able to recognise or respond to family', 'परिवार को पहचान या जवाब न दे पाना'],
  ['Surviving with severe, permanent physical dependence', 'गंभीर, स्थायी शारीरिक निर्भरता के साथ जीना'],
  ['Dying in an ICU, away from family', 'परिवार से दूर ICU में मृत्यु'],
  ['Uncontrolled pain or breathlessness at any stage', 'किसी भी समय बेक़ाबू दर्द या साँस फूलना'],
  ['Loss of dignity in bathing, toileting or exposure', 'नहाने, शौच या शरीर खुलने में सम्मान खोना'],
  ['Being alone at the time of death', 'मृत्यु के समय अकेले होना'],
  ['Exhausting the family’s savings on treatment that will not change the outcome', 'ऐसे इलाज पर परिवार की बचत ख़त्म होना जो नतीजा न बदले'],
];
/** The form's own words for each hospital-transfer reason, in its order. */
const TRANSFER_REASONS: Array<[TransferReason, string, string]> = [
  ['PAIN', 'Pain that cannot be controlled at home', 'घर पर काबू न होने वाला दर्द'],
  ['BREATHLESSNESS', 'Breathlessness that cannot be controlled at home', 'घर पर काबू न होने वाली साँस फूलना'],
  ['BLEEDING', 'Significant bleeding', 'ज़्यादा खून बहना'],
  ['SEIZURES', 'Seizures', 'दौरे'],
  ['FALL_FRACTURE', 'Fall with suspected fracture', 'गिरना और हड्डी टूटने का शक'],
  ['BLOCKED_TUBE', 'Blocked or displaced catheter, feeding tube or stent', 'बंद या हिली हुई कैथेटर, खाने की नली या स्टेंट'],
  ['INFECTION_IV', 'Suspected infection needing intravenous treatment', 'संक्रमण का शक जिसमें नस से (IV) इलाज चाहिए'],
  ['CONFUSION', 'Acute confusion or agitation not settling', 'अचानक भ्रम या बेचैनी जो शांत न हो'],
  ['CAREGIVER_RESPITE', 'Caregiver unable to cope — respite admission', 'देखभाल करने वाला संभाल नहीं पा रहा — राहत के लिए भर्ती'],
];
const AUTHORITY: Array<[AuthorityBasis, string, string]> = [
  ['NAMED_BY_PATIENT', 'Named by patient', 'मरीज़ द्वारा नामित'], ['NEXT_OF_KIN', 'Family consensus', 'परिवार की सहमति'],
  ['NAMED_IN_AMD', 'Named in AMD', 'AMD में नामित'], ['COURT_APPOINTED', 'Court-appointed', 'अदालत द्वारा नियुक्त'],
];
/** The oxygen / ventilation table, LEVEL / WHAT IT MEANS FOR THE PATIENT / CEILING, word for word. */
export const GOC_OXYGEN_LEVELS: Array<[0 | 1 | 2 | 3 | 4 | 5, string, string, string, string]> = [
  [0, 'Comfort measures only', 'केवल आराम के उपाय', 'Opioid and anxiolytic for breathlessness, fan, positioning, suction, mouth care. Oxygen used only if it makes the patient feel better — no saturation target is chased.', 'साँस फूलने पर ओपिऑइड और घबराहट की दवा, पंखा, सही स्थिति, सक्शन, मुँह की देखभाल। ऑक्सीजन तभी जब उससे आराम मिले — कोई सैचुरेशन लक्ष्य नहीं।'],
  [1, 'Nasal prongs / simple face mask', 'नाक की नली / साधारण मास्क', 'Low-flow oxygen up to ____ L/min. Comfortable, no interference with talking, eating or being at home.', 'कम बहाव की ऑक्सीजन, ____ L/min तक। आरामदायक, बोलने, खाने या घर रहने में बाधा नहीं।'],
  [2, 'Non-rebreather / venturi mask', 'नॉन-रीब्रीदर / वेंचुरी मास्क', 'Higher-concentration oxygen. Tolerable, but the mask can feel confining and interferes with speech.', 'ज़्यादा ऑक्सीजन। सहने लायक़, पर मास्क घुटन भरा लग सकता है और बोलने में बाधा डालता है।'],
  [3, 'High-flow nasal cannula (HFNC)', 'हाई-फ़्लो नाक ऑक्सीजन (HFNC)', 'Warmed humidified high-flow oxygen. Better tolerated than a mask, usually needs a high-dependency bed, and can be used purely for comfort.', 'गर्म, नम, तेज़ बहाव की ऑक्सीजन। मास्क से बेहतर सहन होती है, आम तौर पर HDU बिस्तर चाहिए, और सिर्फ़ आराम के लिए भी दी जा सकती है।'],
  [4, 'Non-invasive ventilation (CPAP / BiPAP)', 'बिना ट्यूब वेंटिलेशन (CPAP / BiPAP)', 'A tight mask driving air in. Effective for fluid overload and COPD; often uncomfortable, restricts speaking and eating, and can be distressing if used for many hours.', 'कसा हुआ मास्क जो हवा भरता है। फ़्लूइड ओवरलोड और COPD में असरदार; अक्सर असहज, बोलना और खाना रोकता है, और कई घंटे चले तो परेशान कर सकता है।'],
  [5, 'Intubation and invasive mechanical ventilation', 'ट्यूब डालकर वेंटिलेटर', 'A tube in the windpipe, sedation, inability to speak, ICU. Recovery may or may not follow; weaning may not be possible.', 'साँस की नली में ट्यूब, बेहोशी की दवा, बोल न पाना, ICU। ठीक हो भी सकते हैं और नहीं भी; मशीन हटाना संभव न हो।'],
];
const same = (a: string, b: string) => a.trim().toLowerCase().replace(/[’']/g, "'") === b.trim().toLowerCase().replace(/[’']/g, "'");

export function buildGocFormDocument(record: EctprRecord, rows: Row[], lang: CareLang, meta: { reviewedBy: string; reviewedAt: string; notDiscussed?: string[]; deviceSignatures?: GocFormDeviceSignature[] }): GocFormDocument {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const nd = t('Not discussed', 'बात नहीं हुई');
  const npr = t('No preference recorded', 'कोई पसंद दर्ज नहीं');
  const rowOf = (topic: GocTopicId) => rows.find((row) => row.topic === topic);
  /** An unticked decision: "Not discussed" (or "discussed, no choice"), and V1's "No preference recorded". */
  const status = (topic: GocTopicId) => {
    const row = rowOf(topic);
    const head = !row || row.status === 'NOT_DISCUSSED' ? `${nd}${row?.verified ? ` (${t('doctor verified', 'डॉक्टर ने जाँचा')})` : ''}` : t('Discussed, no choice confirmed', 'बात हुई, कोई फ़ैसला पक्का नहीं');
    return `${head}. ${npr}.`;
  };
  const tk = (on: boolean, en: string, hi: string, detail?: string | null): GocFormTick => ({ text: t(en, hi), on, ...(detail && detail.trim() ? { detail: detail.trim() } : {}) });
  const field = (label: string, value: string | null | undefined): GocFormRow => (value && value.trim() ? { label, value: value.trim() } : { label, value: nd, open: true });
  /** A row of boxes: the answer is in the boxes; when none is ticked the row also says why. */
  const boxes = (label: string, ticks: GocFormTick[], unticked: string, filled = ''): GocFormRow =>
    (ticks.some((x) => x.on) ? { label, value: filled, ticks } : { label, value: unticked, open: true, ticks });
  const p = record.patient; const c = record.choices; const v = record.values; const h = record.homeCare; const w = record.withdrawal; const cap = record.capacity;
  const device = meta.deviceSignatures ?? [];
  const cellBoxes = (ticks: GocFormTick[]) => ticks.map((x) => `${box(x.on)} ${x.text}${x.detail ? ` ${x.detail}` : ''}`).join('\n');

  // Section I: the decision-maker table, PRIMARY / SECONDARY.
  const dm = [record.decisionMakers.primary, record.decisionMakers.alternate];
  const dmCell = (who: DecisionMaker | null, pick: (w: DecisionMaker) => string) => (who && pick(who).trim() ? pick(who).trim() : nd);
  const dmTable: GocFormTable = { head: ['', t('PRIMARY', 'मुख्य'), t('SECONDARY', 'द्वितीय')], rows: [
    [t('Name', 'नाम'), ...dm.map((who) => dmCell(who, (x) => x.name))],
    [t('Relationship to patient', 'मरीज़ से रिश्ता'), ...dm.map((who) => dmCell(who, (x) => x.relation))],
    [t('Phone (must be reachable 24 h)', 'फ़ोन (24 घंटे मिलना चाहिए)'), ...dm.map((who) => dmCell(who, (x) => x.phone24h))],
    [t('ID type & number', 'पहचान पत्र का प्रकार और नंबर'), ...dm.map((who) => dmCell(who, (x) => (x.idType ? `${x.idType}${x.idLast4 ? ` …${x.idLast4}` : ''}` : '')))],
    [t('Basis for authority', 'अधिकार का आधार'), ...dm.map((who) => cellBoxes(AUTHORITY.map(([key, en, hi]) => tk(who?.authorityBasis === key, en, hi))))],
  ] };

  // Section II
  const capacityTicks = [
    tk(cap.status === 'HAS', 'The patient has capacity for these decisions and has completed this record themselves.', 'मरीज़ में इन फ़ैसलों की क्षमता है और उन्होंने यह रिकॉर्ड ख़ुद भरा।'),
    tk(cap.status === 'HAS_DELEGATED', 'The patient has capacity but has asked family to decide with the team. Record the patient’s own words:', 'मरीज़ में क्षमता है, पर उन्होंने परिवार से टीम के साथ फ़ैसला करने को कहा। मरीज़ के अपने शब्द:', cap.status === 'HAS_DELEGATED' ? cap.basis : ''),
    tk(cap.status === 'FLUCTUATES', 'Capacity fluctuates. Assessed by: ____ on ____. Re-assess before acting on any section.', 'क्षमता बदलती रहती है। किसने जाँचा: ____ कब: ____। किसी भी भाग पर अमल से पहले दोबारा जाँचें।', cap.status === 'FLUCTUATES' ? [cap.assessor, cap.assessedOn].filter(Boolean).join(' · ') : ''),
    tk(cap.status === 'LACKS', 'The patient lacks capacity for these decisions. Basis of assessment (understand / retain / weigh / communicate):', 'मरीज़ में इन फ़ैसलों की क्षमता नहीं है। जाँच का आधार (समझना / याद रखना / तौलना / बताना):', cap.status === 'LACKS' ? cap.basis : ''),
  ];
  const dir = record.existingDirective;
  const amd = dir?.kind === 'VALID_AMD' ? dir : null;
  const directiveTicks = [
    tk(Boolean(amd), 'There is a valid written Advance care planning document. Executed on ____. Location of original: ____', 'वैध लिखित अग्रिम देखभाल योजना दस्तावेज़ है। कब बनी: ____। मूल कहाँ है: ____', amd ? [amd.date, amd.location].filter(Boolean).join(' · ') : ''),
    tk(Boolean(amd && /notary/i.test(amd.attestor)), '    Attested by Notary', '    नोटरी ने सत्यापित किया'),
    tk(Boolean(amd && /gazett/i.test(amd.attestor)), '    Attested by Gazetted Officer', '    राजपत्रित अधिकारी ने सत्यापित किया', amd && !/notary|gazett/i.test(amd.attestor) ? amd.attestor : ''),
    tk(false, '    With two witnesses? Yes / No. If yes, this ECTPR must be read as subordinate to it — the directive governs; this record operationalises it.', '    दो गवाहों के साथ? हाँ / नहीं। अगर हाँ, तो यह ECTPR उसके अधीन है — डायरेक्टिव ही मान्य है; यह रिकॉर्ड उसे लागू करता है।'),
    tk(dir?.kind === 'PRIOR_WISHES', 'There is no directive, but the patient previously expressed clear wishes. Recorded here, with who heard them and when:', 'कोई डायरेक्टिव नहीं, पर मरीज़ ने पहले साफ़ इच्छाएँ बताई थीं। यहाँ दर्ज, किसने और कब सुनीं:', dir?.kind === 'PRIOR_WISHES' ? dir.note : ''),
    tk(dir?.kind === 'NONE', 'No directive and no recorded prior wishes. Decisions will be made in the patient’s best interests through family consensus with the team.', 'न डायरेक्टिव, न पहले की दर्ज इच्छाएँ। फ़ैसले मरीज़ के हित में, परिवार और टीम की सहमति से होंगे।'),
  ];
  // The plan stores only "knows the diagnosis" (yes / no): the prognosis box and "does not wish to know" stay empty, never guessed.
  const knowsTicks = [
    tk(false, 'The patient knows the diagnosis and the prognosis.', 'मरीज़ बीमारी और आगे क्या होगा, दोनों जानते हैं।'),
    tk(v.patientKnows === true, 'The patient knows the diagnosis but not the prognosis.', 'मरीज़ बीमारी जानते हैं, पर आगे क्या होगा यह नहीं।'),
    tk(v.familyAskedNotToTell, 'The family has asked that the patient not be told. Discussed with the family that the patient may ask directly and will not be lied to. Family understands: Yes / Not yet resolved', 'परिवार ने कहा कि मरीज़ को न बताया जाए। परिवार से बात हुई कि मरीज़ सीधे पूछ सकते हैं और उनसे झूठ नहीं बोला जाएगा। परिवार समझता है: हाँ / अभी तय नहीं', v.familyAskedNotToTell ? v.directQuestionPlan : ''),
    tk(false, 'The patient has said they do not wish to know details and has delegated:', 'मरीज़ ने कहा कि वे विस्तार नहीं जानना चाहते और यह सौंपा:'),
  ];
  const outcomes = v.unacceptableOutcomes.map((item) => withoutSoundCaptions(item).trim()).filter(Boolean);
  const other = outcomes.filter((o) => !GOC_UNACCEPTABLE_OUTCOMES.some(([en, hi]) => same(o, en) || same(o, hi)));
  const outcomeTicks = [...GOC_UNACCEPTABLE_OUTCOMES.map(([en, hi]) => tk(outcomes.some((o) => same(o, en) || same(o, hi)), en, hi)), tk(other.length > 0, 'Other:', 'अन्य:', other.join('; '))];
  const firstTick = (ticks: GocFormTick[]) => ticks.find((x) => x.on);
  const summary = (ticks: GocFormTick[]) => { const on = firstTick(ticks); return on ? `${on.text.replace(/:$/, '')}${on.detail ? ` ${on.detail}` : ''}` : nd; };

  // Section III
  const tr = c.hospitalTransfer;
  const transferTicks = [
    tk(tr?.kind === 'ANY', 'YES — for any significant deterioration.', 'हाँ — किसी भी गंभीर बिगड़ने पर।'),
    tk(tr?.kind === 'ONLY_FOR', 'ONLY FOR THE FOLLOWING (tick each that applies):', 'केवल इनके लिए (जो लागू हों उन पर निशान):'),
    ...TRANSFER_REASONS.map(([reason, en, hi]) => tk(tr?.kind === 'ONLY_FOR' && tr.reasons.includes(reason), `    ${en}`, `    ${hi}`)),
    tk(false, '    Other:', '    अन्य:'),
    tk(tr?.kind === 'NO', 'NO — the patient is to be cared for where they are.', 'नहीं — मरीज़ की देखभाल वहीं होगी जहाँ वे हैं।'),
  ];
  const cprTicks = [tk(c.cpr === 'ATTEMPT', 'ATTEMPT CPR.', 'CPR करें।'), tk(c.cpr === 'DNACPR', 'DO NOT ATTEMPT CPR (DNACPR).', 'CPR न करें (DNACPR)।')];
  const reason = c.dnacprReason === 'FUTILITY' ? t('clinical futility', 'इलाज बेअसर') : c.dnacprReason === 'PATIENT_REFUSAL' ? t('patient refusal', 'मरीज़ ने मना किया') : c.dnacprReason === 'BURDEN_OUTWEIGHS_BENEFIT' ? t('burden outweighs benefit', 'नुकसान फ़ायदे से ज़्यादा') : '';
  const ceilingSet = c.respCeiling !== null && c.respCeiling !== undefined;
  const oxygenTable: GocFormTable = { head: [t('LEVEL', 'स्तर'), t('WHAT IT MEANS FOR THE PATIENT', 'मरीज़ के लिए इसका मतलब'), t('CEILING', 'सबसे ऊँचा स्तर')], rows: GOC_OXYGEN_LEVELS.map(([level, en, hi, meanEn, meanHi]) => [
    String(level),
    `${t(en, hi)}\n${level === 1 && c.maxOxygenLpm ? t(meanEn, meanHi).replace('____', String(c.maxOxygenLpm)) : t(meanEn, meanHi)}`,
    box(c.respCeiling === level),
  ]) };
  const vp = c.vasopressors; const dy = c.dialysis; const ng = c.feeding.ng; const icu = c.icu;
  const vasoTicks = [
    tk(vp?.kind === 'NO', 'No', 'नहीं'), tk(vp?.kind === 'TRIAL', 'Short trial only, up to ______ hours', 'केवल छोटा ट्रायल, ______ घंटे तक', vp?.kind === 'TRIAL' ? trialLabel(vp.trial, lang) : ''),
    tk(vp?.kind === 'YES_NO_LIMIT', 'Yes, without a pre-set limit', 'हाँ, बिना पहले से तय सीमा'), tk(vp?.kind === 'YES_MAX_AGENTS', 'Yes, but no escalation beyond ____ agents', 'हाँ, पर ____ दवाओं से आगे नहीं', vp?.kind === 'YES_MAX_AGENTS' ? String(vp.maxAgents) : ''),
  ];
  const dialysisTicks = [
    tk(dy?.kind === 'NO', 'No', 'नहीं'), tk(dy?.kind === 'TRIAL', 'Trial of ______ sessions / days, then review', '______ सत्र / दिन का ट्रायल, फिर समीक्षा', dy?.kind === 'TRIAL' ? trialLabel(dy.trial, lang) : ''),
    tk(dy?.kind === 'YES', 'Yes', 'हाँ'), tk(dy?.kind === 'ONLY_IF_REVERSIBLE', 'Only if the renal failure is judged reversible', 'केवल तब जब किडनी फ़ेल्योर ठीक होने वाला माना जाए'),
  ];
  const icuTicks = [tk(icu?.kind === 'YES', 'Yes', 'हाँ'), tk(icu?.kind === 'TRIAL', 'Time-limited trial', 'समय-सीमित ट्रायल', icu?.kind === 'TRIAL' ? trialLabel(icu.trial, lang) : ''), tk(icu?.kind === 'NO', 'No', 'नहीं')];
  const ngTicks = [tk(ng?.kind === 'NO', 'No', 'नहीं'), tk(ng?.kind === 'UP_TO_DAYS', 'Yes, short-term only, up to ______ days, then review', 'हाँ, थोड़े समय, ______ दिन तक, फिर समीक्षा', ng?.kind === 'UP_TO_DAYS' ? `${ng.days} ${t('days', 'दिन')}` : ''), tk(ng?.kind === 'YES', 'Yes', 'हाँ')];
  const pegTicks = [tk(c.feeding.peg === 'NO', 'No', 'नहीं'), tk(c.feeding.peg === 'ONLY_AFTER_PALLIATIVE_DISCUSSION', 'Only after discussion with the palliative care team', 'केवल पैलिएटिव केयर टीम से बात के बाद'), tk(c.feeding.peg === 'YES', 'Yes', 'हाँ')];
  const goalTicks = [
    tk(c.goalOfCare === 'A_LIFE', 'A. PRIORITISE LIFE — All medically effective treatment, including ICU and ventilation.', 'A. जीवन को प्राथमिकता — ICU और वेंटिलेटर सहित हर असरदार इलाज।'),
    tk(c.goalOfCare === 'B_CEILING', 'B. BALANCED / CEILING OF CARE — Treat reversible problems, but with the specific limits and as a time-limited trial.', 'B. संतुलित / इलाज की सीमा — ठीक होने वाली समस्याओं का इलाज, तय सीमाओं में और समय-सीमित ट्रायल के रूप में।'),
    tk(c.goalOfCare === 'C_COMFORT', 'C. PRIORITISE COMFORT — Symptom control is the goal. Hospital transfer only if comfort cannot be achieved where the patient is.', 'C. आराम को प्राथमिकता — लक्षणों पर नियंत्रण ही लक्ष्य। अस्पताल तभी जब वहाँ आराम न मिल सके।'),
  ];
  const withdrawn = Boolean(w?.applicable);
  const wField = (label: string, value: string | undefined) => (withdrawn ? field(label, value) : { label, value: nd, open: true });
  const finalTicks = [
    tk(withdrawn && w?.finalDecision === 'TO_BE_PERFORMED', 'withdrawal/withholding to be performed', 'इलाज रोका जाएगा / शुरू नहीं होगा'),
    tk(withdrawn && w?.finalDecision === 'FAMILY_REFUSED', 'Family has refused consent (if patient has lost decision-making capacity)', 'परिवार ने सहमति नहीं दी (अगर मरीज़ निर्णय क्षमता खो चुके हों)'),
    tk(withdrawn && w?.finalDecision === 'BOARD_REFUSED', 'Primary/Secondary Board has refused permission', 'प्राथमिक/द्वितीय बोर्ड ने अनुमति नहीं दी'),
    tk(withdrawn && w?.finalDecision === 'PHYSICIAN_REVOKED', 'Treating physician has revoked their decision to support withdrawal/withholding', 'इलाज करने वाले डॉक्टर ने इलाज रोकने/शुरू न करने का समर्थन वापस लिया'),
  ];

  // Section 19: the plan's signatures, then those drawn on this device for this note.
  const planSig = (match: (s: Signature) => boolean) => record.signatures.find(match);
  const devSig = (role: string) => device.find((d) => d.role === role);
  const how = (method: string | undefined, thumb?: boolean) => (thumb || method === 'THUMB' ? t('Thumb impression', 'अँगूठे का निशान') : t('Signed', 'हस्ताक्षर किए'));
  const day = (iso: string | undefined) => (iso ? iso.slice(0, 10) : '');
  const patient = planSig((s) => s.role === 'PATIENT'); const patientDev = devSig('PATIENT');
  const familyDev = devSig('FAMILY');
  const surrogate = (rank: 'PRIMARY' | 'SECONDARY') => planSig((s) => s.role === 'SURROGATE' && (rank === 'SECONDARY' ? s.surrogateRank === 'SECONDARY' : s.surrogateRank !== 'SECONDARY'));
  const primary = surrogate('PRIMARY'); const secondary = surrogate('SECONDARY');
  const witnesses = record.signatures.filter((s) => s.role === 'WITNESS'); const witnessDev = devSig('WITNESS');
  const clinician = (role: Signature['role'], deviceRole?: string): string[] => {
    const s = planSig((x) => x.role === role); const d = deviceRole ? devSig(deviceRole) : undefined;
    return [s?.name ?? d?.name ?? '', s?.registrationNo ?? d?.registrationNo ?? '', s ? how(undefined, s.thumbImpression) : d ? how(d.method) : '', day(s?.signedAt ?? d?.signedAt)];
  };
  const declaration19 = cellBoxes([
    tk(cap.status === 'HAS', 'I have made these choices myself.', 'ये फ़ैसले मैंने ख़ुद लिए।'),
    tk(cap.status === 'HAS_DELEGATED', 'I have chosen not to be involved in detail and have delegated to my surrogate.', 'मैंने विस्तार में शामिल न होने का फ़ैसला किया और अपने प्रतिनिधि को सौंपा।'),
  ]);

  const sections: GocFormSection[] = [
    { id: 'I', title: t('Section I – General information', 'भाग I – सामान्य जानकारी'), groups: [
      { title: t('Patient details', 'मरीज़ की जानकारी'), rows: [
        field(t('Name', 'नाम'), p.name),
        field(t('Age, Sex, Gender', 'उम्र, लिंग, जेंडर'), [p.dob, p.sex, p.gender].filter(Boolean).join(' · ')),
        field(t('Address', 'पता'), p.address), field(t('Phone number', 'फ़ोन नंबर'), p.phone),
      ] },
      { title: t('Physician details', 'डॉक्टरों की जानकारी'), rows: [
        field(t('Primary Oncologist name and hospital – and contact', 'मुख्य ऑन्कोलॉजिस्ट का नाम और अस्पताल – और संपर्क'), record.careTeam.oncologist),
        field(t('Palliative care physician name and hospital – and contact', 'पैलिएटिव केयर डॉक्टर का नाम और अस्पताल – और संपर्क'), [record.careTeam.palliativePhysician, record.careTeam.palliativePhone24h].filter(Boolean).join(' · ')),
        field(t('Additional space for the team – Surgical oncology, radiation oncology, etc.', 'टीम के लिए और जगह – सर्जिकल ऑन्कोलॉजी, रेडिएशन ऑन्कोलॉजी आदि'), record.careTeam.extraTeam.join('; ')),
        field(t('Additional option to add a local physician who can be briefed about the care and part of the team', 'स्थानीय डॉक्टर जिन्हें देखभाल के बारे में बताया जा सके और जो टीम में हों'), record.careTeam.localDoctor),
      ] },
      { title: t('Family/Friend details', 'परिवार/दोस्तों की जानकारी'), rows: [
        field(t('Members present for discussion and relationship with the patient', 'बातचीत में मौजूद सदस्य और मरीज़ से रिश्ता'), (record.decisionMakers.presentAtDiscussion ?? []).join('; ')),
        field(t('Language preferred', 'पसंद की भाषा'), p.languagePref),
      ] },
      { title: t('Primary decision maker and contact details · Secondary decision maker and contact details', 'मुख्य फ़ैसला लेने वाले और संपर्क · द्वितीय फ़ैसला लेने वाले और संपर्क'), rows: [], table: dmTable },
      { title: t('Family members who must be informed before any major decision (name, relation, phone)', 'जिन परिवार वालों को किसी बड़े फ़ैसले से पहले बताना ज़रूरी है (नाम, रिश्ता, फ़ोन)'), rows: [
        field(t('Name, relation, phone', 'नाम, रिश्ता, फ़ोन'), record.decisionMakers.familyToInform.join('; ')),
        field(t('Family/friends who were attempted to be contacted, but were unavailable', 'परिवार/दोस्त जिनसे संपर्क की कोशिश हुई, पर नहीं मिले'), (record.decisionMakers.attemptedContact ?? []).join('; ')),
      ] },
    ] },
    { id: 'II', title: t('Section II – Preliminary medical information and patient values', 'भाग II – शुरुआती चिकित्सा जानकारी और मरीज़ के मूल्य'), groups: [
      { title: '', rows: [
        field(t('Primary diagnosis and stage', 'मुख्य बीमारी और स्टेज'), [record.clinical.diagnosis, record.clinical.stage].filter(Boolean).join(' · ')),
        field(t('Functional status – ECOG', 'काम करने की क्षमता – ECOG'), record.clinical.ecog === null ? '' : String(record.clinical.ecog)),
        { label: t('Patient’s capacity', 'मरीज़ की क्षमता'), value: summary(capacityTicks), open: !firstTick(capacityTicks) },
        { label: t('Existing medical directives', 'पहले के चिकित्सा निर्देश'), value: summary(directiveTicks), open: !firstTick(directiveTicks) },
        { label: t('What the patient knows', 'मरीज़ क्या जानते हैं'), value: summary(knowsTicks), open: !firstTick(knowsTicks) },
      ] },
      { title: t('4.1  The patient’s capacity, now', '4.1  मरीज़ की अभी की क्षमता'), rows: [boxes('', capacityTicks, nd)] },
      { title: t('4.2  Existing directives', '4.2  पहले के निर्देश'), rows: [boxes('', directiveTicks, nd)] },
      { title: t('5.3  What the patient knows', '5.3  मरीज़ क्या जानते हैं'), rows: [boxes('', knowsTicks, `${nd}`)] },
      { title: t('6.1  In their own words', '6.1  उनके अपने शब्दों में'), rows: [
        field(t('The most distressing physical symptom right now is', 'अभी सबसे ज़्यादा तकलीफ़ देने वाला शारीरिक लक्षण है'), ''),
        field(t('What matters most to me / to them is', 'मेरे / उनके लिए सबसे ज़रूरी है'), withoutSoundCaptions(v.ownWords)),
        field(t('What I am most afraid of is', 'मुझे सबसे ज़्यादा डर है'), ''),
        field(t('Unfinished things that matter (people to see, disputes to settle, papers to sign, a place to visit)', 'अधूरे ज़रूरी काम (किससे मिलना है, कौन सा झगड़ा सुलझाना है, कौन से काग़ज़ साइन करने हैं, कहाँ जाना है)'), ''),
      ] },
      { title: t('6.2  Outcomes that would be unacceptable  (tick any that apply)', '6.2  जो हालत मंज़ूर नहीं  (जो लागू हों उन पर निशान)'), rows: [boxes('', outcomeTicks, nd)] },
    ] },
    { id: 'III', title: t('Section III – Decisive Actions', 'भाग III – निर्णायक फ़ैसले'), groups: [
      { title: t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)'), rows: [
        boxes(t('Should the patient be brought to the hospital? (To inform any doctor that has been called to the home for evaluation)', 'क्या मरीज़ को अस्पताल लाया जाए? (घर पर जाँच के लिए बुलाए गए किसी भी डॉक्टर को बताने के लिए)'), transferTicks, status('hospitalTransfer'), tr ? transferLabel(tr, lang) : ''),
        field(t('If not, what is the frequency of assessments, and the details of the person coordinating between the patient/family and the relevant hospital/centre', 'अगर नहीं, तो जाँच कितनी बार होगी, और मरीज़/परिवार और अस्पताल/केंद्र के बीच तालमेल रखने वाले व्यक्ति की जानकारी'), [h?.assessmentFrequency, h?.coordinator].filter(Boolean).join(' · ')),
        field(t('If a non-governmental organisation has been taking care of the patients, contact details of their representative + declaration confirming consultation with the NGO', 'अगर कोई ग़ैर-सरकारी संस्था मरीज़ की देखभाल कर रही है, तो उसके प्रतिनिधि का संपर्क + संस्था से सलाह की पुष्टि'), h?.ngoContact ? `${h.ngoContact}${h.ngoConsulted ? ` · ${box(true)} ${t('NGO consulted', 'संस्था से सलाह ली गई')}` : ''}` : ''),
        field(t('Had they been previously admitted to any hospital for the current condition?', 'क्या इसी बीमारी के लिए पहले किसी अस्पताल में भर्ती हुए थे?'), h?.previousAdmission === true ? `${t('Yes', 'हाँ')}${h.previousAdmissionDetails ? `: ${h.previousAdmissionDetails}` : ''}` : h?.previousAdmission === false ? t('No', 'नहीं') : ''),
      ] },
      { title: t('Should CPR be performed?', 'क्या CPR किया जाए?'), rows: [
        boxes('CPR', cprTicks, status('cpr'), c.cpr ? cprLabel(record, lang) : ''),
        field(t('Reason recorded (clinical futility / patient refusal / burden outweighs benefit)', 'दर्ज कारण (इलाज बेअसर / मरीज़ ने मना किया / नुकसान फ़ायदे से ज़्यादा)'), reason),
        field(t('Discussed with (patient / surrogate / both)', 'किससे बात हुई (मरीज़ / प्रतिनिधि / दोनों)'), c.dnacprDiscussedWith),
      ] },
      { title: t('What is the level of oxygen/ventilatory support that should be provided?', 'ऑक्सीजन/साँस की कितनी मदद दी जानी चाहिए?'),
        rows: ceilingSet ? [] : [{ label: t('Ceiling', 'सबसे ऊँचा स्तर'), value: status('respCeiling'), open: true }], table: oxygenTable },
      { title: t('Other parameters', 'दूसरी बातें'), rows: [
        boxes(t('Dialysis or CRRT', 'डायलिसिस या CRRT'), dialysisTicks, status('dialysis'), dy ? dialysisLabel(dy, lang) : ''),
        field(t('Others', 'अन्य'), ''),
        boxes(t('Requirement of ICU?', 'ICU की ज़रूरत?'), icuTicks, status('icu'), icu ? icuLabel(icu, lang) : ''),
      ] },
      { title: t('If withdrawal and withholding of treatment has been decided (as per Common Cause v Union of India, 2018 and 2023)', 'अगर इलाज रोकने और शुरू न करने का फ़ैसला हुआ है (कॉमन कॉज़ बनाम भारत संघ, 2018 और 2023 के अनुसार)'), rows: [
        wField(t('Dates of constitution of the Primary and Secondary Boards', 'प्राथमिक और द्वितीय बोर्ड बनने की तारीख़ें'), [w?.primaryBoardDate, w?.secondaryBoardDate].filter(Boolean).join(' · ')),
        wField(t('Composition of the Boards', 'बोर्ड के सदस्य'), [w?.primaryBoardMembers, w?.secondaryBoardMembers].filter(Boolean).join(' · ')),
        wField(t('Decision of the Boards', 'बोर्ड का फ़ैसला'), w?.boardDecision),
        boxes(t('Final decision about withdrawal/withholding', 'इलाज रोकने/शुरू न करने का अंतिम फ़ैसला'), finalTicks, nd),
        wField(t('Protocol for withdrawal/withholding', 'इलाज रोकने/शुरू न करने का तरीका'), w?.protocol),
      ] },
      { title: t('Vasopressors / inotropes', 'वैसोप्रेसर / इनोट्रोप'), rows: [boxes(t('Vasopressors / inotropes', 'वैसोप्रेसर / इनोट्रोप'), vasoTicks, status('vasopressors'), vp ? vasopressorLabel(vp, lang) : '')] },
      { title: t('12.1  Feeding', '12.1  खाना'), rows: [
        { label: '', value: '', ticks: [tk(true, 'Comfort feeding by mouth only — careful hand feeding, sips, tastes, favourite foods, no volume target, no forcing. Mouth care given regardless.', 'केवल मुँह से आराम के लिए खाना — ध्यान से हाथ से खिलाना, घूँट, स्वाद, पसंद का खाना, कोई मात्रा लक्ष्य नहीं, कोई ज़बरदस्ती नहीं। मुँह की देखभाल हर हाल में।')] },
        boxes(t('Nasogastric (Ryle’s) tube', 'नाक की नली (राइल्स ट्यूब)'), ngTicks, status('ng'), ng ? ngLabel(ng, lang) : ''),
        boxes(t('Gastrostomy (PEG)', 'पेट की नली (PEG)'), pegTicks, status('peg'), c.feeding.peg ? pegLabel(c.feeding.peg, lang) : ''),
      ] },
      { title: t('Goal of care', 'देखभाल का लक्ष्य'), rows: [boxes(t('A, B or C', 'A, B या C'), goalTicks, status('goalOfCare'), c.goalOfCare ? goalLabel(c.goalOfCare, lang) : '')] },
    ] },
    { id: 'IV', title: t('Section IV – Signatures and declarations (SECTION 19 — Declarations and signatures)', 'भाग IV – हस्ताक्षर और घोषणाएँ (भाग 19 — घोषणाएँ और हस्ताक्षर)'), groups: [
      { title: t('19.1  Patient', '19.1  मरीज़'), rows: [], table: { head: [declaration19, t('Signature / thumb impression', 'हस्ताक्षर / अँगूठे का निशान'), t('Name in capitals', 'बड़े अक्षरों में नाम'), t('Date', 'तारीख़')], rows: [
        [' ', patient ? how(undefined, patient.thumbImpression) : patientDev ? how(patientDev.method) : '', (patient?.name ?? patientDev?.name ?? '').toUpperCase(), day(patient?.signedAt ?? patientDev?.signedAt)],
      ] } },
      { title: t('19.2  Surrogate / healthcare proxy (primary and secondary surrogate decision-makers)', '19.2  प्रतिनिधि / हेल्थकेयर प्रॉक्सी (मुख्य और द्वितीय फ़ैसला लेने वाले)'), rows: [], table: {
        head: [cellBoxes([tk(record.surrogateDeclaration.confirmed, 'I confirm I am conveying what the patient would have wanted, so far as I can judge.', 'मैं पुष्टि करता/करती हूँ कि जितना मैं समझ सकता/सकती हूँ, मैं वही बता रहा/रही हूँ जो मरीज़ चाहते।')]), t('Signature', 'हस्ताक्षर'), t('Name & relationship', 'नाम और रिश्ता'), t('Date', 'तारीख़')],
        rows: [
          [t('Primary', 'मुख्य'), primary ? how(undefined, primary.thumbImpression) : familyDev ? how(familyDev.method) : '', primary?.name ?? (familyDev ? `${familyDev.name}${familyDev.relation ? ` (${familyDev.relation})` : ''}` : ''), day(primary?.signedAt ?? familyDev?.signedAt)],
          [t('Secondary', 'द्वितीय'), secondary ? how(undefined, secondary.thumbImpression) : '', secondary?.name ?? '', day(secondary?.signedAt)],
        ] } },
      { title: t('19.3  Witnesses  (required where the patient signs by thumb impression, or where literacy is limited – for both home-based and hospital-based evaluation)', '19.3  गवाह  (जब मरीज़ अँगूठा लगाएँ, या पढ़ना-लिखना कम हो, तब ज़रूरी – घर और अस्पताल दोनों जगह की जाँच में)'), rows: [], table: {
        head: [t('Witness — name', 'गवाह — नाम'), t('Address / ID', 'पता / पहचान'), t('Signature', 'हस्ताक्षर'), t('Date', 'तारीख़')],
        rows: [0, 1].map((i) => {
          const s = witnesses[i]; const d = i === 0 && !s ? witnessDev : undefined;
          return [`${t('Witness', 'गवाह')} ${i + 1} — ${s?.name ?? d?.name ?? ''}`, d?.relation ?? '', s ? how(undefined, s.thumbImpression) : d ? how(d.method) : '', day(s?.signedAt ?? d?.signedAt)];
        }) } },
      { title: t('19.4  Clinicians', '19.4  डॉक्टर और नर्स'), rows: [], table: {
        head: [t('ROLE', 'भूमिका'), t('NAME', 'नाम'), t('REGISTRATION NO.', 'रजिस्ट्रेशन नंबर'), t('SIGNATURE', 'हस्ताक्षर'), t('DATE', 'तारीख़')],
        rows: [
          [t('Treating consultant', 'इलाज करने वाले कंसल्टेंट'), ...clinician('TREATING_CONSULTANT', 'DOCTOR')],
          [t('Palliative care clinician', 'पैलिएटिव केयर डॉक्टर'), ...clinician('PALLIATIVE_CLINICIAN')],
          [t('Nurse involved in the discussion', 'बातचीत में शामिल नर्स'), ...clinician('NURSE')],
        ] } },
    ] },
  ];
  const lead = [
    t('Goals-of-care conversation note, in the order of the clinical document', 'देखभाल के लक्ष्यों की बातचीत का नोट, क्लिनिकल दस्तावेज़ के क्रम में'),
    record.status === 'DRAFT' ? t('For the doctor, and the patient or family, to read together and sign below.', 'डॉक्टर और मरीज़ या परिवार साथ पढ़ें और नीचे साइन करें।') : `${t('Version', 'संस्करण')} ${record.version}`,
    `${t('Reviewed by', 'समीक्षा करने वाले')}: ${meta.reviewedBy || nd} · ${meta.reviewedAt.slice(0, 10)}`,
    ...(meta.notDiscussed?.length ? [`${nd}: ${meta.notDiscussed.join('; ')}`] : []),
  ];
  return { title: t('Emergency care and treatment plan: goals of care', 'आपातकालीन देखभाल और इलाज की योजना: देखभाल के लक्ष्य'), lead, sections };
}

const tickLine = (tick: GocFormTick) => `${box(tick.on)} ${tick.text}${tick.detail ? ` ${tick.detail}` : ''}`;

/** Plain text: the note that is signed (its SHA-256 seals the sign-off). Every box and table of the form is in it. */
export function gocFormText(doc: GocFormDocument): string {
  const out = [doc.title, ...doc.lead];
  for (const section of doc.sections) {
    out.push('', section.title.toUpperCase());
    for (const group of section.groups) {
      if (group.title) out.push(group.title);
      for (const row of group.rows) {
        if (row.label || row.value) out.push(`- ${row.label ? `${row.label}: ` : ''}${row.value}`);
        for (const tick of row.ticks ?? []) out.push(`    ${tickLine(tick)}`);
      }
      if (group.table) for (const cells of [group.table.head, ...group.table.rows]) out.push(`| ${cells.map((cell) => cell.replace(/\n/g, ' / ')).join(' | ')} |`);
    }
  }
  return out.join('\n');
}

const esc = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
/** A table cell: lines that start with a box are drawn as boxes, ticked ones bold. */
function cellHtml(text: string): string {
  return text.split('\n').map((line) => {
    const on = line.startsWith(GOC_TICK_ON); const off = line.startsWith(GOC_TICK_OFF);
    if (!on && !off) return `<div${line.trim() === 'Not discussed' ? ' class="open"' : ''}>${esc(line)}</div>`;
    return `<div class="tick${on ? ' on' : ''}"><span class="box">${on ? GOC_TICK_ON : GOC_TICK_OFF}</span> ${esc(line.slice(1).trim())}</div>`;
  }).join('');
}
/** A row's answer: the blank filled in bold ("Not discussed" muted), then the form's boxes. */
export function gocFormCellHtml(row: GocFormRow): string {
  const ticks = (row.ticks ?? []).map((tick) => `<div class="tick${tick.on ? ' on' : ''}"><span class="box">${box(tick.on)}</span> ${esc(tick.text)}${tick.detail ? ` <u>${esc(tick.detail)}</u>` : ''}</div>`).join('');
  return `${row.value ? `<div class="val${row.open ? ' open' : ''}">${esc(row.value)}</div>` : ''}${ticks}`;
}

/** Warm Sahara colours, like a completed paper form. Shared by the screen (goc-form-view.tsx) and the print page. */
export const GOC_FORM_CSS = `
.goc-form{font:15px/1.5 system-ui,"Noto Sans","Noto Sans Devanagari","Noto Sans Telugu","Noto Sans Kannada","Noto Nastaliq Urdu",sans-serif;color:#2b211c}
.goc-form h1{font-size:21px;margin:0 0 4px}.goc-form .lead{margin:2px 0;color:#5d5048}
.goc-form section{border:1px solid #e3d6c6;border-radius:16px;background:#fffdf9;margin:18px 0;overflow:hidden}
.goc-form section>h2{display:flex;align-items:center;gap:12px;margin:0;padding:12px 16px;font-size:17px;background:#f6ece1;border-bottom:1px solid #e3d6c6}
.goc-form .num{display:inline-grid;place-items:center;min-width:38px;height:38px;padding:0 6px;border-radius:12px;background:#b9472c;color:#fff;font-weight:800;font-size:15px}
.goc-form h3{font-size:14.5px;margin:16px 16px 6px;color:#7a3b26}
.goc-form table{width:calc(100% - 32px);margin:0 16px 10px;border-collapse:collapse}
.goc-form th,.goc-form td{border:1px solid #e6dbcd;padding:9px 11px;vertical-align:top;text-align:start}
.goc-form th{font-weight:600;background:#fbf6f0}.goc-form table.rows th{width:38%}
.goc-form table.grid th{font-size:13px;letter-spacing:.02em}
.goc-form table.levels th:first-child{width:64px}.goc-form table.levels th:last-child{width:96px;text-align:center}.goc-form table.levels td:last-child{text-align:center;font-size:20px!important}.goc-form table.levels td:nth-child(2) div:first-child{font-weight:700}
.goc-form table.sign th:first-child{width:40%}
.goc-form .val{font-weight:700}.goc-form .open,.goc-form .val.open{font-weight:400;color:#8d7f74;font-style:italic}
.goc-form .tick{color:#6d6058;margin-top:4px}.goc-form .tick.on{color:#2b211c;font-weight:700}
.goc-form .box{display:inline-block;width:1.3em;font-size:17px;line-height:1}.goc-form .tick.on .box{color:#b9472c}
.goc-form u{text-decoration-color:#b9472c;text-underline-offset:3px;font-weight:700}
.goc-form .note{white-space:pre-wrap;border:1px solid #e3d6c6;border-radius:12px;padding:10px;margin:0 16px 12px}
.goc-form table{display:table!important;font-size:inherit!important;table-layout:fixed}.goc-form tr{display:table-row!important}.goc-form tbody{display:table-row-group!important}
.goc-form th,.goc-form td{display:table-cell!important;padding:9px 11px!important;border:1px solid #e6dbcd!important;white-space:normal!important;text-transform:none!important;letter-spacing:normal!important;font-size:14px!important;color:#2b211c!important;background-clip:padding-box;overflow-wrap:anywhere}
.goc-form th{color:#4a3b33!important}.goc-form td::before{content:none!important}.goc-form tr:hover{background:transparent}
.goc-form td .open,.goc-form td .val.open{color:#8d7f74!important}.goc-form .tick{color:#6d6058}.goc-form .tick.on{color:#2b211c}
@media (max-width:640px){.goc-form table{width:calc(100% - 16px);margin:0 8px 10px}.goc-form table.rows th{width:42%}.goc-form th,.goc-form td{padding:7px 8px!important;font-size:13px!important}}
@media print{.goc-form section{border-radius:0;background:#fff}.goc-form section>h2{background:#f4efe8}.goc-form{font-size:12px}}`;

/** The form's body as HTML: the print page, and the same markup on screen. */
export function gocFormBodyHtml(doc: GocFormDocument, note = ''): string {
  const body = doc.sections.map((section) => `<section><h2><span class="num">${section.id}</span><span>${esc(section.title)}</span></h2>${section.groups.map((group) => {
    const rows = group.rows.length ? `<table class="rows">${group.rows.map((row) => (row.label ? `<tr><th>${esc(row.label)}</th><td>${gocFormCellHtml(row)}</td></tr>` : `<tr><td colspan="2">${gocFormCellHtml(row)}</td></tr>`)).join('')}</table>` : '';
    const table = group.table ? `<table class="grid${group.table.head.length === 3 && group.table.head[0].length <= 6 ? ' levels' : ''}${group.table.head.length === 4 ? ' sign' : ''}"><tr>${group.table.head.map((cell) => `<th>${cellHtml(cell)}</th>`).join('')}</tr>${group.table.rows.map((cells) => `<tr>${cells.map((cell) => `<td>${cellHtml(cell)}</td>`).join('')}</tr>`).join('')}</table>` : '';
    return `${group.title ? `<h3>${esc(group.title)}</h3>` : ''}${rows}${table}`;
  }).join('')}</section>`).join('');
  const extra = note.trim() ? `<section><h2>${esc('Doctor’s note')}</h2><p class="note">${esc(note)}</p></section>` : '';
  return `<h1>${esc(doc.title)}</h1>${doc.lead.map((line) => `<p class="lead">${esc(line)}</p>`).join('')}${body}${extra}`;
}

/** A standalone page for the browser's print dialog (or Save as PDF). No scripts, no outside files. */
export function gocFormHtml(doc: GocFormDocument, dir: 'ltr' | 'rtl' = 'ltr', note = ''): string {
  return `<!doctype html><html dir="${dir}"><head><meta charset="utf-8"><title>${esc(doc.title)}</title><style>body{margin:24px;max-width:880px;background:#fff}@media print{body{margin:12mm}}${GOC_FORM_CSS}</style></head><body><div class="goc-form">${gocFormBodyHtml(doc, note)}</div></body></html>`;
}
