// The checklist rows and the draft note for a goals-of-care conversation. Built only from the form's own labels and what the doctor
// confirmed, so it says what was said and decided and carries no emotion. The verbatim transcript is kept apart, for review.
import { careText, type CareLang } from './care-i18n.ts';
import { ambulanceLabel, ceilingLabel, cprShort, dialysisLabel, goalLabel, hydrationLabel, icuLabel, ngLabel, noPreference, pegLabel, transferLabel, vasopressorLabel } from './ectpr-labels.ts';
import { GOC_TOPIC_ORDER, withoutSoundCaptions, type GocTopicCoverage, type GocTopicId } from './goc-extract.ts';
import type { EctprRecord, GocTopicMark } from './ectpr-model.ts';

export type GocTopicRow = { topic: GocTopicId; status: 'DISCUSSED' | 'NOT_DISCUSSED'; quote: string; verified: boolean; verifiedBy: string };

/** The automatic tick, unless the doctor has verified otherwise. Not discussed never sets a choice. */
export function gocTopicRows(coverage: GocTopicCoverage[], marks: Partial<Record<string, GocTopicMark>>): GocTopicRow[] {
  return GOC_TOPIC_ORDER.map((topic) => {
    const seen = coverage.find((item) => item.topic === topic);
    const mark = marks[topic];
    return { topic, status: mark?.status ?? (seen?.discussed ? 'DISCUSSED' : 'NOT_DISCUSSED'), quote: seen?.quote ?? '', verified: Boolean(mark), verifiedBy: mark?.verifiedBy ?? '' };
  });
}

/** The team's split: a note that records decisions, or a general discussion note. */
export function gocNoteKind(record: EctprRecord): 'DECISION' | 'DISCUSSION' {
  const c = record.choices;
  const decided = [c.goalOfCare, c.cpr, c.hospitalTransfer, c.ambulance, c.respCeiling, c.icu, c.vasopressors, c.dialysis, c.feeding.ng, c.feeding.peg, c.hydration];
  return decided.some((value) => value !== null && value !== undefined) ? 'DECISION' : 'DISCUSSION';
}

export function gocTopicName(topic: GocTopicId, lang: CareLang): string {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  switch (topic) {
    case 'icu': return t('Intensive care (ICU)', 'गहन चिकित्सा (ICU)');
    case 'respCeiling': return t('Ventilator and breathing support', 'वेंटिलेटर और साँस की मदद');
    case 'dialysis': return t('Dialysis (kidney machine)', 'डायलिसिस (गुर्दे की मशीन)');
    case 'vasopressors': return t('Drugs to support blood pressure (vasopressors)', 'ब्लड-प्रेशर सहारा देने वाली दवाएँ (वैसोप्रेसर)');
    case 'ng': return t('Nose tube (NG)', 'नाक की नली (NG)');
    case 'peg': return t('Stomach tube (PEG)', 'पेट की नली (PEG)');
    case 'hydration': return t('Fluids (drip or under the skin)', 'तरल (ड्रिप या त्वचा के नीचे)');
    case 'cpr': return t('CPR if the heart and breathing stop', 'दिल और साँस रुकने पर CPR');
    case 'hospitalTransfer': return t('Go to hospital if things get worse?', 'हालत बिगड़ने पर अस्पताल जाएँ?');
    case 'ambulance': return t('What the ambulance crew should do', 'एम्बुलेंस टीम क्या करे');
    case 'goalOfCare': return t('Goal of care', 'देखभाल का लक्ष्य');
    case 'understanding': return t('Understanding the illness', 'बीमारी की समझ');
    case 'patientKnows': return t('Does the patient know about their illness?', 'क्या मरीज़ अपनी बीमारी के बारे में जानते हैं?');
    case 'familyAskedNotToTell': return t('Telling the patient', 'मरीज़ को बताना');
    case 'values': return t('What matters to the patient', 'मरीज़ के लिए क्या ज़रूरी है');
    case 'decisionMakers': return t('Decision-makers', 'फ़ैसला लेने वाले');
    case 'homeCare': return t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)');
  }
}

function decisionText(topic: GocTopicId, record: EctprRecord, lang: CareLang): string | null {
  const c = record.choices;
  const none = (value: unknown) => value === null || value === undefined;
  switch (topic) {
    case 'icu': return none(c.icu) ? null : icuLabel(c.icu, lang);
    case 'respCeiling': return none(c.respCeiling) ? null : ceilingLabel(c.respCeiling, lang);
    case 'dialysis': return none(c.dialysis) ? null : dialysisLabel(c.dialysis, lang);
    case 'vasopressors': return none(c.vasopressors) ? null : vasopressorLabel(c.vasopressors, lang);
    case 'ng': return none(c.feeding.ng) ? null : ngLabel(c.feeding.ng, lang);
    case 'peg': return none(c.feeding.peg) ? null : pegLabel(c.feeding.peg, lang);
    case 'hydration': return none(c.hydration) ? null : hydrationLabel(c.hydration, lang);
    case 'cpr': return none(c.cpr) ? null : cprShort(record, lang);
    case 'hospitalTransfer': return none(c.hospitalTransfer) ? null : transferLabel(c.hospitalTransfer, lang);
    case 'ambulance': return none(c.ambulance) ? null : ambulanceLabel(c.ambulance, lang);
    case 'goalOfCare': return none(c.goalOfCare) ? null : goalLabel(c.goalOfCare, lang);
    case 'patientKnows': return none(record.values.patientKnows) ? null : record.values.patientKnows ? careText(lang, 'Yes', 'हाँ') : careText(lang, 'No', 'नहीं');
    case 'familyAskedNotToTell': return record.values.familyAskedNotToTell ? careText(lang, 'The family asked that the patient not be told', 'परिवार ने कहा कि मरीज़ को न बताया जाए') : null;
    default: return null;
  }
}

const DECISION_TOPICS: readonly GocTopicId[] = ['icu', 'respCeiling', 'dialysis', 'vasopressors', 'ng', 'peg', 'hydration', 'cpr', 'hospitalTransfer', 'ambulance', 'goalOfCare'];

/** A structured draft, in the form's order, for the doctor to edit. It leads into the existing signing step. */
export function buildGocNote(record: EctprRecord, rows: GocTopicRow[], lang: CareLang, meta: { reviewedBy: string; reviewedAt: string }): string {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const rowOf = (topic: GocTopicId) => rows.find((row) => row.topic === topic);
  const notDiscussed = t('Not discussed', 'बात नहीं हुई');
  const discussedOpen = t('Discussed, no choice confirmed', 'बात हुई, कोई फ़ैसला पक्का नहीं');
  const none = noPreference(lang);
  const statusOf = (topic: GocTopicId) => {
    const row = rowOf(topic);
    if (!row || row.status === 'NOT_DISCUSSED') return `${notDiscussed}${row?.verified ? ` (${t('doctor verified', 'डॉक्टर ने जाँचा')})` : ''}`;
    return discussedOpen;
  };
  const decisionLine = (topic: GocTopicId) => {
    const chosen = decisionText(topic, record, lang);
    return chosen ? `- ${gocTopicName(topic, lang)}: ${chosen}` : `- ${gocTopicName(topic, lang)}: ${statusOf(topic)}. ${none}.`;
  };
  const person = (label: string, who: EctprRecord['decisionMakers']['primary']) => (who ? [`- ${label}: ${[who.name, who.relation].filter(Boolean).join(', ')}`] : []);
  const kind = gocNoteKind(record);
  const lines = [
    t('Goals-of-care conversation note', 'देखभाल के लक्ष्यों की बातचीत का नोट'),
    t('Draft for the doctor to edit. Not signed.', 'डॉक्टर के बदलने के लिए ड्राफ़्ट। साइन नहीं हुआ।'),
    `${t('Type', 'प्रकार')}: ${kind === 'DECISION' ? t('Decisions were recorded', 'फ़ैसले दर्ज हुए') : t('General discussion, no decision recorded', 'सामान्य बातचीत, कोई फ़ैसला दर्ज नहीं')}`,
    `${t('Reviewed by', 'समीक्षा करने वाले')}: ${meta.reviewedBy} · ${meta.reviewedAt.slice(0, 10)}`,
    '',
    t('Understanding', 'समझ'),
    `- ${gocTopicName('understanding', lang)}: ${record.prognosticUnderstanding.confirmed ? t('Explained, and the family can say it back', 'समझाया गया, परिवार दोहरा सकता है') : statusOf('understanding')}`,
    `- ${gocTopicName('patientKnows', lang)}: ${decisionText('patientKnows', record, lang) ?? statusOf('patientKnows')}`,
    ...(record.values.familyAskedNotToTell ? [`- ${gocTopicName('familyAskedNotToTell', lang)}: ${decisionText('familyAskedNotToTell', record, lang)}`] : []),
    '',
    t('Decisions', 'फ़ैसले'),
    ...DECISION_TOPICS.map(decisionLine),
    '',
    t('Values', 'मूल्य'),
    ...(record.values.ownWords.trim() ? [`- ${t('In the patient’s own words', 'मरीज़ के अपने शब्दों में')}: ${withoutSoundCaptions(record.values.ownWords)}`] : [`- ${gocTopicName('values', lang)}: ${statusOf('values')}`]),
    ...record.values.unacceptableOutcomes.map((item) => `- ${t('Would not accept', 'मंज़ूर नहीं')}: ${withoutSoundCaptions(item)}`),
    '',
    t('Decision-makers', 'फ़ैसला लेने वाले'),
    ...person(t('Primary decision-maker', 'मुख्य फ़ैसला लेने वाले'), record.decisionMakers.primary),
    ...person(t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले'), record.decisionMakers.alternate),
    ...(record.decisionMakers.presentAtDiscussion?.length ? [`- ${t('Present', 'मौजूद')}: ${record.decisionMakers.presentAtDiscussion.join('; ')}`] : []),
    ...(!record.decisionMakers.primary && !record.decisionMakers.alternate && !record.decisionMakers.presentAtDiscussion?.length ? [`- ${statusOf('decisionMakers')}`] : []),
  ];
  return lines.join('\n');
}
