'use client';

import type { DraftFieldKey } from './summary-state';

type FormField = { key: DraftFieldKey; label: string };
type FormGroup = { title: string; fields: FormField[] };

const GROUPS: FormGroup[] = [
  { title: 'Patient and care team', fields: [
    { key: 'participants', label: 'Patient name, age or date of birth, and sex' },
    { key: 'participants', label: 'Patient address and phone' },
    { key: 'participants', label: 'UHID or medical record number' },
    { key: 'topics', label: 'Diagnosis, stage and functional status' },
    { key: 'participants', label: 'Treating doctor, unit and contact' },
    { key: 'participants', label: 'Palliative care doctor or team and contact' },
    { key: 'participants', label: 'Other doctors or local care contacts' },
  ] },
  { title: 'Family and contacts', fields: [
    { key: 'participants', label: 'People present and their relationship to the patient' },
    { key: 'participants', label: 'Preferred language' },
    { key: 'participants', label: 'Primary decision-maker: name, relationship, reachable contact and stated basis' },
    { key: 'participants', label: 'Alternate decision-maker: name, relationship, reachable contact and stated basis' },
    { key: 'participants', label: 'Other people to inform: name, relationship and contact' },
    { key: 'participants', label: 'Earlier decision-maker details carried forward from this draft' },
    { key: 'participants', label: 'Earlier people-to-inform and care-team contacts carried forward from this draft' },
    { key: 'participants', label: 'Earlier combined contact details from an older form version' },
  ] },
  { title: 'Understanding and existing directives', fields: [
    { key: 'topics', label: 'Capacity for this discussion: recorded status, assessor, date and basis' },
    { key: 'topics', label: 'Existing directive or prior wishes: what was reported and where it is recorded' },
    { key: 'topics', label: 'What the patient understands or wishes to know' },
    { key: 'topics', label: 'What the family understands or needs clarified' },
    { key: 'topics', label: 'Earlier clinical context carried forward from this draft' },
    { key: 'topics', label: 'Earlier understanding details carried forward from this draft' },
  ] },
  { title: 'Patient values', fields: [
    { key: 'priorities', label: 'Most distressing symptom, in the patient’s or family’s words' },
    { key: 'priorities', label: 'What matters most to the patient' },
    { key: 'priorities', label: 'What the patient is most afraid of' },
    { key: 'priorities', label: 'Unfinished matters important to the patient' },
    { key: 'priorities', label: 'Outcomes the patient considers unacceptable' },
    { key: 'priorities', label: 'Other values or needs discussed' },
  ] },
  { title: 'Declarations recorded in the discussion', fields: [
    { key: 'participants', label: 'Patient’s stated role in these choices or words about delegating' },
    { key: 'participants', label: 'Surrogate’s stated relationship and account of the patient’s wishes' },
    { key: 'topics', label: 'Other declarations or acknowledgements made during the discussion' },
    { key: 'participants', label: 'Witness details recorded: name, role and date (optional)' },
  ] },
  { title: 'Care choices discussed (clinician’s record of the conversation)', fields: [
    { key: 'topics', label: 'Hospital transfer discussed' },
    { key: 'topics', label: 'CPR discussed' },
    { key: 'topics', label: 'Breathing support discussed' },
    { key: 'topics', label: 'ICU or other organ support discussed' },
    { key: 'topics', label: 'Feeding and fluids discussed' },
  ] },
  { title: 'Decisions and follow-up', fields: [
    { key: 'topics', label: 'Clinical decisions explicitly discussed and who took part' },
    { key: 'openQuestions', label: 'Decisions not discussed' },
    { key: 'openQuestions', label: 'Unresolved questions or differences' },
    { key: 'followUp', label: 'Next steps, owner, review date or conditions' },
  ] },
];

const FORM_FIELDS = GROUPS.flatMap((group) => group.fields);
const MARKER = 'Clinical form details';
const LEGACY_FIELDS: Record<string, string> = {
  'Patient name and age or date of birth': 'Patient name, age or date of birth, and sex',
  'Patient’s values, symptoms, fears and unfinished matters': 'Other values or needs discussed',
  'Outcomes the patient considers unacceptable': 'Outcomes the patient considers unacceptable',
  'People present, relationships and preferred language': 'People present and their relationship to the patient',
  'Primary and alternate decision-makers: relationship, 24-hour contact and basis': 'Earlier decision-maker details carried forward from this draft',
  'Other people to inform and care-team contacts': 'Earlier people-to-inform and care-team contacts carried forward from this draft',
  'Earlier contact details carried forward from this draft': 'Earlier combined contact details from an older form version',
  'Clinical context: diagnosis, stage, ECOG, capacity/date/basis, directives and prior wishes': 'Earlier clinical context carried forward from this draft',
  'Patient and family understanding; what the patient knows': 'Earlier understanding details carried forward from this draft',
  'Clinical decisions explicitly agreed': 'Clinical decisions explicitly discussed and who took part',
};

function readValues(fields: Record<DraftFieldKey, string>): Record<string, string> {
  const values: Record<string, string> = Object.fromEntries(FORM_FIELDS.map(({ label }) => [label, '']));
  for (const key of Object.keys(fields) as DraftFieldKey[]) {
    const text = fields[key]; const marker = text.indexOf(`${MARKER}:\n`);
    if (marker < 0) continue;
    const section = text.slice(marker + MARKER.length + 2);
    for (const { label } of FORM_FIELDS.filter((field) => field.key === key)) {
      const heading = `${label}:\n`; const start = section.indexOf(heading);
      if (start < 0) continue;
      const valueStart = start + heading.length;
      const next = [...FORM_FIELDS.map((field) => section.indexOf(`${field.label}:\n`, valueStart)), ...Object.keys(LEGACY_FIELDS).map((label) => section.indexOf(`${label}:\n`, valueStart))].filter((index) => index >= 0);
      const end = next.length ? Math.min(...next) : section.length;
      const rawValue = section.slice(valueStart, end);
      values[label] = next.length && rawValue.endsWith('\n') ? rawValue.slice(0, -1) : rawValue;
    }
    // Keep entries created by the earlier form version visible after an update.
    for (const [label, destination] of Object.entries(LEGACY_FIELDS)) {
      const heading = `${label}:\n`; const start = section.indexOf(heading);
      if (start < 0) continue;
      const valueStart = start + heading.length;
      const next = [...FORM_FIELDS.map((field) => section.indexOf(`${field.label}:\n`, valueStart)), ...Object.keys(LEGACY_FIELDS).map((legacy) => section.indexOf(`${legacy}:\n`, valueStart))].filter((index) => index >= 0);
      const end = next.length ? Math.min(...next) : section.length;
      const rawValue = section.slice(valueStart, end);
      const legacyValue = next.length && rawValue.endsWith('\n') ? rawValue.slice(0, -1) : rawValue;
      const duplicateHeading = FORM_FIELDS.some(({ label: currentLabel }) => values[currentLabel]?.trim() && values[currentLabel].trim() === legacyValue.trim());
      if (legacyValue.trim() && !values[destination]?.trim() && !duplicateHeading) values[destination] = legacyValue;
    }
  }
  return values;
}

function writeValues(fields: Record<DraftFieldKey, string>, values: Record<string, string>) {
  const result = { ...fields };
  for (const key of Object.keys(fields) as DraftFieldKey[]) {
    const current = fields[key]; const marker = current.indexOf(`${MARKER}:\n`);
    const group = FORM_FIELDS.filter((field) => field.key === key);
    const hasEntries = group.some(({ label }) => values[label]?.trim());
    let narrative = (marker < 0 ? current : current.slice(0, marker)).trimEnd();
    if (hasEntries && narrative.trim() === 'Not stated in this conversation.') narrative = '';
    if (!hasEntries) { result[key] = narrative; continue; }
    const content = group.filter(({ label }) => values[label]?.trim()).map(({ label }) => `${label}:\n${values[label]}`).join('\n');
    result[key] = `${narrative}${narrative ? '\n\n' : ''}${MARKER}:\n${content}`;
  }
  return result;
}

export function CareDiscussionForm({ fields, onFields }: {
  fields: Record<DraftFieldKey, string>; onFields: (fields: Record<DraftFieldKey, string>) => void;
}) {
  const values = readValues(fields);
  return <details className="doctor-structured-form">
    <summary>Structured clinical form</summary>
    {GROUPS.map((group) => <details key={group.title} className="doctor-structured-group">
      <summary>{group.title}</summary>
      <div className="doctor-structured-grid">{group.fields.map(({ label }) => <label key={label}>{label}<textarea rows={2} value={values[label] ?? ''} onChange={(event) => {
        onFields(writeValues(fields, { ...values, [label]: event.target.value }));
      }} /></label>)}</div>
    </details>)}
  </details>;
}
