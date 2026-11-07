// Dr Sujay's goals-of-care checklist (7 Nov 2026): 13 rows in his order, each ticked when any of its guide items came up.
// Twin of GocChecklist.ROWS in CareGocExtract.kt. Consent is taken before recording, so it is not a row.
// A guide item is a topic of the phrase matcher (goc-extract.ts); the row is covered when any of its topics is discussed,
// or when the doctor tapped it in the topic guide (goc-guide.ts). The "Not discussed" list is made of rows, not of topics.
import type { CareLang } from './care-i18n.ts';
import { careText } from './care-i18n.ts';
import type { GocTopicId } from './goc-extract.ts';

export type GocChecklistRowDef = { id: string; en: string; hi: string; section: 'I' | 'II' | 'III'; topics: readonly GocTopicId[] };

export const GOC_CHECKLIST_ROWS: readonly GocChecklistRowDef[] = [
  { id: 'patientDetails', en: 'Patient details', hi: 'मरीज़ की जानकारी', section: 'I', topics: ['patientDetails'] },
  { id: 'caregiver', en: 'Caregiver details', hi: 'देखभाल करने वाले की जानकारी', section: 'I', topics: ['decisionMakers'] },
  { id: 'capacity', en: 'Patient capacity', hi: 'मरीज़ की निर्णय-क्षमता', section: 'I', topics: ['capacity'] },
  { id: 'directives', en: 'Existing medical directives', hi: 'पहले से मौजूद चिकित्सा निर्देश', section: 'I', topics: ['directives'] },
  { id: 'understanding', en: "Patient's understanding of disease", hi: 'बीमारी के बारे में मरीज़ की समझ', section: 'II', topics: ['understanding', 'patientKnows'] },
  { id: 'values', en: 'Patient values', hi: 'मरीज़ के मूल्य', section: 'II', topics: ['values'] },
  { id: 'goals', en: 'Goals of care', hi: 'देखभाल के लक्ष्य', section: 'III', topics: ['goalOfCare'] },
  { id: 'homeVsHospital', en: 'Home care vs hospital transfer', hi: 'घर पर देखभाल या अस्पताल ले जाना', section: 'III', topics: ['homeCare', 'hospitalTransfer', 'ambulance'] },
  { id: 'cpr', en: 'CPR preference', hi: 'CPR की पसंद', section: 'III', topics: ['cpr'] },
  { id: 'icu', en: 'ICU care', hi: 'ICU देखभाल', section: 'III', topics: ['icu', 'vasopressors'] },
  { id: 'ventilation', en: 'Ventilatory support', hi: 'साँस की मशीन की मदद', section: 'III', topics: ['respCeiling'] },
  { id: 'dialysis', en: 'Dialysis preference', hi: 'डायलिसिस की पसंद', section: 'III', topics: ['dialysis'] },
  { id: 'nutrition', en: 'Nutritional support', hi: 'पोषण की मदद', section: 'III', topics: ['ng', 'peg', 'hydration'] },
];

type TopicRowLike = { topic: GocTopicId; status: 'DISCUSSED' | 'NOT_DISCUSSED'; verified: boolean };
export type GocChecklistRow = GocChecklistRowDef & { index: number; discussed: boolean; verified: boolean; byTap: boolean };

export function gocChecklistTitle(row: Pick<GocChecklistRowDef, 'en' | 'hi'>, lang: CareLang): string {
  return careText(lang, row.en, row.hi);
}

/**
 * The 13 rows with their state. [rows]: the topic rows (automatic ticks plus the doctor's verifications). [tapped]: row indexes the doctor
 * started or finished in the topic guide. A row is discussed when any of its topics is, or it was tapped. A row the doctor verified as
 * not discussed (every topic marked so by hand, none discussed) is verified and leaves the "Not discussed" list.
 */
export function gocChecklist(rows: TopicRowLike[], tapped: Iterable<number> = []): GocChecklistRow[] {
  const taps = new Set(tapped);
  return GOC_CHECKLIST_ROWS.map((def, index) => {
    const mine = rows.filter((row) => def.topics.includes(row.topic));
    const byTap = taps.has(index);
    return { ...def, index, byTap, discussed: byTap || mine.some((row) => row.status === 'DISCUSSED'), verified: mine.some((row) => row.verified) };
  });
}

/** Rows not covered and not verified by the doctor: what the end-of-talk prompt lists. */
export function gocChecklistOpen(list: GocChecklistRow[]): GocChecklistRow[] {
  return list.filter((row) => !row.discussed && !row.verified);
}

export function gocChecklistDone(list: GocChecklistRow[]): number {
  return list.filter((row) => row.discussed).length;
}
