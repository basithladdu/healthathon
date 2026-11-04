// The finished goals-of-care note in the structure of the legal Word form (legal review, 31 Oct 2026:
// source-materials/team-context/legal-review-2026-10-31/extracted-text-and-comments.txt), Sections I to IV, so it can be printed or
// downloaded and then signed. Built only from what the doctor confirmed in the plan and the form's own labels: no spoken quotes,
// nothing pre-selected, and an empty decision says "No preference recorded" (rule V1).
import { careText, type CareLang } from './care-i18n.ts';
import {
  ambulanceLabel, authorityLabel, amdStatusLabel, ceilingLabel, cprLabel, dialysisLabel, goalLabel, homeCareLines, hydrationLabel, icuLabel, ngLabel,
  noPreference, notRecorded, pegLabel, transferLabel, vasopressorLabel, withdrawalLines,
} from './ectpr-labels.ts';
import { withoutSoundCaptions, type GocTopicId } from './goc-extract.ts';
import type { DecisionMaker, EctprRecord, Signature } from './ectpr-model.ts';

export type GocFormRow = { label: string; value: string; open?: boolean };
export type GocFormGroup = { title: string; rows: GocFormRow[] };
export type GocFormSection = { id: 'I' | 'II' | 'III' | 'IV'; title: string; groups: GocFormGroup[] };
export type GocFormDocument = { title: string; lead: string[]; sections: GocFormSection[] };
type Row = { topic: GocTopicId; status: 'DISCUSSED' | 'NOT_DISCUSSED'; verified: boolean };

/** Where each guide row sits on the legal form. */
export function gocFormSection(topic: GocTopicId): 'I' | 'II' | 'III' | null {
  if (topic === 'consent') return null;
  if (topic === 'patientDetails' || topic === 'decisionMakers') return 'I';
  if (topic === 'understanding' || topic === 'patientKnows' || topic === 'familyAskedNotToTell' || topic === 'values') return 'II';
  return 'III';
}

const BLANK = '______________________';

export function buildGocFormDocument(record: EctprRecord, rows: Row[], lang: CareLang, meta: { reviewedBy: string; reviewedAt: string; notDiscussed?: string[] }): GocFormDocument {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const none = noPreference(lang);
  const empty = notRecorded(lang);
  const rowOf = (topic: GocTopicId) => rows.find((row) => row.topic === topic);
  const status = (topic: GocTopicId) => {
    const row = rowOf(topic);
    if (!row || row.status === 'NOT_DISCUSSED') return `${t('Not discussed', 'बात नहीं हुई')}${row?.verified ? ` (${t('doctor verified', 'डॉक्टर ने जाँचा')})` : ''}`;
    return t('Discussed, no choice confirmed', 'बात हुई, कोई फ़ैसला पक्का नहीं');
  };
  const text = (label: string, value: string | null | undefined): GocFormRow => (value && value.trim() ? { label, value: value.trim() } : { label, value: empty, open: true });
  const decision = (label: string, topic: GocTopicId, chosen: unknown, show: () => string): GocFormRow =>
    (chosen === null || chosen === undefined ? { label, value: `${status(topic)}. ${none}.`, open: true } : { label, value: show() });
  const person = (who: DecisionMaker | null): string => (who ? [who.name, who.relation, who.phone24h && `${t('phone', 'फ़ोन')} ${who.phone24h}`, who.idType && `${who.idType}${who.idLast4 ? ` …${who.idLast4}` : ''}`, authorityLabel(who.authorityBasis, lang)].filter(Boolean).join(' · ') : '');
  const p = record.patient; const c = record.choices; const v = record.values;
  const signed = (match: (s: Signature) => boolean) => {
    const s = record.signatures.find(match);
    return s ? `${s.name}${s.registrationNo ? ` · ${t('reg.', 'पंजीकरण')} ${s.registrationNo}` : ''}${s.thumbImpression ? ` · ${t('thumb impression', 'अँगूठे का निशान')}` : ''} · ${s.signedAt.slice(0, 10)}` : `${t('Signature', 'हस्ताक्षर')} ${BLANK}  ${t('Name', 'नाम')} ${BLANK}  ${t('Date', 'तारीख़')} ________`;
  };
  const capacity = record.capacity.status === 'HAS' ? t('Has capacity for these decisions', 'इन फ़ैसलों की क्षमता है')
    : record.capacity.status === 'HAS_DELEGATED' ? t('Has capacity, and asked family to decide with the team', 'क्षमता है, और परिवार से टीम के साथ फ़ैसला करने को कहा')
      : record.capacity.status === 'FLUCTUATES' ? t('Capacity fluctuates', 'क्षमता बदलती रहती है')
        : record.capacity.status === 'LACKS' ? t('Lacks capacity for these decisions', 'इन फ़ैसलों की क्षमता नहीं है') : '';
  const knows = v.patientKnows === null ? '' : v.patientKnows ? t('The patient knows the diagnosis', 'मरीज़ बीमारी के बारे में जानते हैं') : t('The patient does not know the diagnosis', 'मरीज़ बीमारी के बारे में नहीं जानते');
  const withdrawal = withdrawalLines(record, lang);

  const sections: GocFormSection[] = [
    { id: 'I', title: t('Section I – General information', 'भाग I – सामान्य जानकारी'), groups: [
      { title: t('Patient details', 'मरीज़ की जानकारी'), rows: [
        text(t('Name', 'नाम'), p.name),
        text(t('Date of birth, sex, gender', 'जन्म तिथि, लिंग, जेंडर'), [p.dob, p.sex, p.gender].filter(Boolean).join(' · ')),
        text(t('Address', 'पता'), p.address), text(t('Phone number', 'फ़ोन नंबर'), p.phone), text('UHID', p.uhid),
        text(t('Language preferred', 'पसंद की भाषा'), p.languagePref),
      ] },
      { title: t('Physician details', 'डॉक्टरों की जानकारी'), rows: [
        text(t('Primary oncologist: name, hospital and contact', 'मुख्य ऑन्कोलॉजिस्ट: नाम, अस्पताल और संपर्क'), record.careTeam.oncologist),
        text(t('Palliative care physician: name, hospital and contact', 'पैलिएटिव केयर डॉक्टर: नाम, अस्पताल और संपर्क'), [record.careTeam.palliativePhysician, record.careTeam.palliativePhone24h].filter(Boolean).join(' · ')),
        text(t('Additional team', 'और टीम'), record.careTeam.extraTeam.join('; ')),
        text(t('Local physician', 'स्थानीय डॉक्टर'), record.careTeam.localDoctor),
      ] },
      { title: t('Family and friend details', 'परिवार और दोस्तों की जानकारी'), rows: [
        text(t('Members present for the discussion, and relationship', 'बातचीत में मौजूद लोग, और रिश्ता'), (record.decisionMakers.presentAtDiscussion ?? []).join('; ')),
        text(t('Primary decision-maker and contact details', 'मुख्य फ़ैसला लेने वाले और संपर्क'), person(record.decisionMakers.primary)),
        text(t('Secondary decision-maker and contact details', 'द्वितीय फ़ैसला लेने वाले और संपर्क'), person(record.decisionMakers.alternate)),
        text(t('Family who must be informed before any major decision', 'जिन्हें बड़े फ़ैसले से पहले बताना ज़रूरी है'), record.decisionMakers.familyToInform.join('; ')),
        ...(record.decisionMakers.attemptedContact?.length ? [text(t('Tried to contact, but unavailable', 'संपर्क की कोशिश की, पर नहीं मिले'), record.decisionMakers.attemptedContact.join('; '))] : []),
      ] },
    ] },
    { id: 'II', title: t('Section II – Preliminary medical information and patient values', 'भाग II – शुरुआती चिकित्सा जानकारी और मरीज़ के मूल्य'), groups: [
      { title: t('Medical information', 'चिकित्सा जानकारी'), rows: [
        text(t('Primary diagnosis and stage', 'मुख्य बीमारी और स्टेज'), [record.clinical.diagnosis, record.clinical.stage].filter(Boolean).join(' · ')),
        text(t('Functional status (ECOG)', 'काम करने की क्षमता (ECOG)'), record.clinical.ecog === null ? '' : String(record.clinical.ecog)),
        text(t('4.1 The patient’s capacity, now', '4.1 मरीज़ की अभी की क्षमता'), [capacity, record.capacity.assessor, record.capacity.assessedOn].filter(Boolean).join(' · ')),
        { label: t('4.2 Existing directives', '4.2 पहले के निर्देश'), value: amdStatusLabel(record.existingDirective, lang), open: !record.existingDirective },
      ] },
      { title: t('5.3 What the patient knows', '5.3 मरीज़ क्या जानते हैं'), rows: [
        knows ? { label: t('What the patient knows', 'मरीज़ क्या जानते हैं'), value: knows } : { label: t('What the patient knows', 'मरीज़ क्या जानते हैं'), value: status('patientKnows'), open: true },
        ...(v.familyAskedNotToTell ? [{ label: t('Telling the patient', 'मरीज़ को बताना'), value: t('The family has asked that the patient not be told', 'परिवार ने कहा कि मरीज़ को न बताया जाए') }] : []),
        { label: t('Illness explained as not curable, and the family can say it back', 'समझाया गया कि बीमारी ठीक नहीं हो सकती, और परिवार दोहरा सकता है'), value: record.prognosticUnderstanding.confirmed ? `${t('Yes', 'हाँ')}${record.prognosticUnderstanding.confirmedBy ? ` · ${record.prognosticUnderstanding.confirmedBy}` : ''}` : status('understanding'), open: !record.prognosticUnderstanding.confirmed },
      ] },
      { title: t('6.1 In their own words', '6.1 उनके अपने शब्दों में'), rows: [
        v.ownWords.trim() ? { label: t('What matters most', 'सबसे ज़रूरी क्या है'), value: withoutSoundCaptions(v.ownWords) } : { label: t('What matters most', 'सबसे ज़रूरी क्या है'), value: status('values'), open: true },
      ] },
      { title: t('6.2 Outcomes that would be unacceptable', '6.2 जो हालत मंज़ूर नहीं'), rows: v.unacceptableOutcomes.length
        ? v.unacceptableOutcomes.map((item) => ({ label: '☑', value: withoutSoundCaptions(item) }))
        : [{ label: '—', value: empty, open: true }] },
    ] },
    { id: 'III', title: t('Section III – Decisive actions', 'भाग III – निर्णायक फ़ैसले'), groups: [
      { title: t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)'), rows: [
        decision(t('Should the patient be brought to the hospital?', 'क्या मरीज़ को अस्पताल लाया जाए?'), 'hospitalTransfer', c.hospitalTransfer, () => transferLabel(c.hospitalTransfer, lang)),
        ...homeCareLines(record, lang).map((line) => ({ label: '·', value: line })),
      ] },
      { title: t('Should CPR be performed?', 'क्या CPR किया जाए?'), rows: [
        decision('CPR', 'cpr', c.cpr, () => `${cprLabel(record, lang)}${c.dnacprDiscussedWith ? ` · ${t('discussed with', 'किससे बात हुई')}: ${c.dnacprDiscussedWith}` : ''}`),
      ] },
      { title: t('Level of oxygen or ventilatory support', 'ऑक्सीजन या साँस की मदद का स्तर'), rows: [
        decision(t('Highest level allowed', 'सबसे ऊँचा स्तर'), 'respCeiling', c.respCeiling, () => ceilingLabel(c.respCeiling, lang)),
      ] },
      { title: t('Other parameters', 'दूसरी बातें'), rows: [
        decision(t('Dialysis or CRRT', 'डायलिसिस या CRRT'), 'dialysis', c.dialysis, () => dialysisLabel(c.dialysis, lang)),
        decision(t('Requirement of ICU', 'ICU की ज़रूरत'), 'icu', c.icu, () => icuLabel(c.icu, lang)),
        decision(t('Vasopressors or inotropes', 'वैसोप्रेसर या इनोट्रोप'), 'vasopressors', c.vasopressors, () => vasopressorLabel(c.vasopressors, lang)),
        ...(withdrawal.length ? [{ label: t('Withdrawal or withholding of treatment', 'इलाज रोकना या शुरू न करना'), value: withdrawal.join('; ') }] : []),
      ] },
      { title: t('12.1 Feeding', '12.1 खाना'), rows: [
        { label: t('Comfort feeding by mouth', 'मुँह से आराम के लिए खाना'), value: t('Always offered, with mouth care', 'हमेशा, मुँह की देखभाल के साथ') },
        decision(t('Nasogastric (Ryle’s) tube', 'नाक की नली (राइल्स)'), 'ng', c.feeding.ng, () => ngLabel(c.feeding.ng, lang)),
        decision(t('Gastrostomy (PEG)', 'पेट की नली (PEG)'), 'peg', c.feeding.peg, () => pegLabel(c.feeding.peg, lang)),
        decision(t('Fluids', 'तरल'), 'hydration', c.hydration, () => hydrationLabel(c.hydration, lang)),
      ] },
      { title: t('Goal of care', 'देखभाल का लक्ष्य'), rows: [
        decision(t('A, B or C', 'A, B या C'), 'goalOfCare', c.goalOfCare, () => goalLabel(c.goalOfCare, lang)),
        decision(t('What the ambulance crew should do', 'एम्बुलेंस टीम क्या करे'), 'ambulance', c.ambulance, () => ambulanceLabel(c.ambulance, lang)),
      ] },
    ] },
    { id: 'IV', title: t('Section IV – Signatures and declarations', 'भाग IV – हस्ताक्षर और घोषणाएँ'), groups: [
      { title: t('19.1 Patient', '19.1 मरीज़'), rows: [{ label: t('I have made these choices myself, or have delegated to my surrogate', 'ये फ़ैसले मैंने ख़ुद लिए, या अपने प्रतिनिधि को सौंपे'), value: signed((s) => s.role === 'PATIENT') }] },
      { title: t('19.2 Surrogate (primary and secondary)', '19.2 प्रतिनिधि (मुख्य और द्वितीय)'), rows: [
        { label: t('Primary', 'मुख्य'), value: signed((s) => s.role === 'SURROGATE' && s.surrogateRank !== 'SECONDARY') },
        { label: t('Secondary', 'द्वितीय'), value: signed((s) => s.role === 'SURROGATE' && s.surrogateRank === 'SECONDARY') },
      ] },
      { title: t('19.3 Witnesses', '19.3 गवाह'), rows: [0, 1].map((i) => ({ label: `${t('Witness', 'गवाह')} ${i + 1}`, value: signed((s) => s === record.signatures.filter((w) => w.role === 'WITNESS')[i]) })) },
      { title: t('19.4 Clinicians', '19.4 डॉक्टर और नर्स'), rows: [
        { label: t('Treating consultant', 'इलाज करने वाले कंसल्टेंट'), value: signed((s) => s.role === 'TREATING_CONSULTANT') },
        { label: t('Palliative care clinician', 'पैलिएटिव केयर डॉक्टर'), value: signed((s) => s.role === 'PALLIATIVE_CLINICIAN') },
        { label: t('Nurse involved in the discussion', 'बातचीत में शामिल नर्स'), value: signed((s) => s.role === 'NURSE') },
      ] },
    ] },
  ];
  const lead = [
    t('Goals-of-care conversation note, in the order of the legal form', 'देखभाल के लक्ष्यों की बातचीत का नोट, क़ानूनी फ़ॉर्म के क्रम में'),
    record.status === 'DRAFT' ? t('For the doctor, and the patient or family, to read together and sign below.', 'डॉक्टर और मरीज़ या परिवार साथ पढ़ें और नीचे साइन करें।') : `${t('Version', 'संस्करण')} ${record.version}`,
    `${t('Reviewed by', 'समीक्षा करने वाले')}: ${meta.reviewedBy || empty} · ${meta.reviewedAt.slice(0, 10)}`,
    ...(meta.notDiscussed?.length ? [`${t('Not discussed', 'बात नहीं हुई')}: ${meta.notDiscussed.join('; ')}`] : []),
  ];
  return { title: t('Emergency care and treatment plan: goals of care', 'आपातकालीन देखभाल और इलाज की योजना: देखभाल के लक्ष्य'), lead, sections };
}

/** Plain text, for the note box the doctor edits. */
export function gocFormText(doc: GocFormDocument): string {
  const out = [doc.title, ...doc.lead];
  for (const section of doc.sections) {
    out.push('', section.title.toUpperCase());
    for (const group of section.groups) {
      out.push(group.title);
      for (const row of group.rows) out.push(`- ${row.label}: ${row.value}`);
    }
  }
  return out.join('\n');
}

const esc = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** A standalone page for the browser's print dialog (or Save as PDF). No scripts, no outside files. */
export function gocFormHtml(doc: GocFormDocument, dir: 'ltr' | 'rtl' = 'ltr', note = ''): string {
  const body = doc.sections.map((section) => `<h2>${esc(section.title)}</h2>${section.groups.map((group) => `<h3>${esc(group.title)}</h3><table>${group.rows.map((row) => `<tr${row.open ? ' class="open"' : ''}><th>${esc(row.label)}</th><td>${esc(row.value)}</td></tr>`).join('')}</table>`).join('')}`).join('');
  const extra = note.trim() ? `<h2>${esc('Doctor’s note')}</h2><p class="note">${esc(note)}</p>` : '';
  return `<!doctype html><html dir="${dir}"><head><meta charset="utf-8"><title>${esc(doc.title)}</title><style>
body{font:14px/1.45 system-ui,"Noto Sans","Noto Sans Devanagari","Noto Sans Telugu","Noto Sans Kannada","Noto Nastaliq Urdu",sans-serif;color:#222;margin:24px;max-width:820px}
h1{font-size:20px;margin:0 0 6px}h2{font-size:16px;margin:22px 0 6px;border-bottom:2px solid #333;padding-bottom:3px}h3{font-size:14px;margin:12px 0 4px}
table{width:100%;border-collapse:collapse}th,td{border:1px solid #999;padding:5px 7px;vertical-align:top;text-align:start}th{width:38%;font-weight:600;background:#f4f1ec}
tr.open td{color:#666}.lead{margin:2px 0;color:#444}.note{white-space:pre-wrap;border:1px solid #999;padding:8px}@media print{body{margin:12mm}}</style></head><body>
<h1>${esc(doc.title)}</h1>${doc.lead.map((line) => `<p class="lead">${esc(line)}</p>`).join('')}${body}${extra}</body></html>`;
}
