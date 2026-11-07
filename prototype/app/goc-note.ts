// The checklist rows and the draft note for a goals-of-care conversation. Built only from the form's own labels and what the doctor
// confirmed, so it says what was said and decided and carries no emotion. The verbatim transcript is kept apart, for review.
import { careText, type CareLang } from './care-i18n.ts';
import { GOC_TOPIC_ORDER, type GocTopicCoverage, type GocTopicId } from './goc-extract.ts';
import { gocChecklist, gocChecklistOpen, gocChecklistTitle } from './goc-checklist.ts';
import { buildGocFormDocument, gocFormText } from './goc-form.ts';
import type { EctprRecord, GocTopicMark } from './ectpr-model.ts';

export type GocTopicRow = { topic: GocTopicId; status: 'DISCUSSED' | 'NOT_DISCUSSED'; quote: string; verified: boolean; verifiedBy: string };

/** The automatic tick, unless the doctor has verified otherwise. Not discussed never sets a choice. */
export function gocTopicRows(coverage: GocTopicCoverage[], marks: Partial<Record<string, GocTopicMark>>, opts: { consented?: boolean } = {}): GocTopicRow[] {
  return GOC_TOPIC_ORDER.map((topic) => {
    const seen = coverage.find((item) => item.topic === topic);
    const mark = marks[topic];
    const auto = seen?.discussed || (topic === 'consent' && opts.consented);
    return { topic, status: mark?.status ?? (auto ? 'DISCUSSED' : 'NOT_DISCUSSED'), quote: seen?.quote ?? '', verified: Boolean(mark), verifiedBy: mark?.verifiedBy ?? '' };
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
    case 'consent': return t('Consent for this conversation', 'इस बातचीत के लिए सहमति');
    case 'patientDetails': return t('Patient details', 'मरीज़ की जानकारी');
    case 'capacity': return t('Patient capacity', 'मरीज़ की निर्णय-क्षमता');
    case 'directives': return t('Existing medical directives', 'पहले से मौजूद चिकित्सा निर्देश');
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
    case 'decisionMakers': return t('Family details, decision-makers and emergency contact', 'परिवार, फ़ैसला लेने वाले और इमरजेंसी संपर्क');
    case 'homeCare': return t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)');
  }
}

/** The draft note: the clinical document's Sections I to IV as text, for the doctor to edit. It leads into the existing signing step. */
export function buildGocNote(record: EctprRecord, rows: GocTopicRow[], lang: CareLang, meta: { reviewedBy: string; reviewedAt: string; tapped?: number[] }): string {
  const { tapped, ...rest } = meta;
  return gocFormText(buildGocFormDocument(record, rows, lang, { ...rest, notDiscussed: gocNotDiscussedTitles(rows, lang, tapped) }));
}

/** The "Not discussed" list is made of Dr Sujay's 13 checklist rows, not of the finer topics. [tapped]: rows the doctor ticked in the topic guide. */
export function gocNotDiscussedTitles(rows: GocTopicRow[], lang: CareLang, tapped: Iterable<number> = []): string[] {
  return gocChecklistOpen(gocChecklist(rows, tapped)).map((row) => gocChecklistTitle(row, lang));
}
