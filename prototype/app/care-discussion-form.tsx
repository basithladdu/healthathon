'use client';

import type { DraftFieldKey } from './summary-state';

const FORM_FIELDS: Array<{ key: DraftFieldKey; label: string }> = [
  { key: 'priorities', label: 'Patient’s values, symptoms, fears and unfinished matters' },
  { key: 'priorities', label: 'Outcomes the patient considers unacceptable' },
  { key: 'participants', label: 'People present, relationships and preferred language' },
  { key: 'participants', label: 'Primary and alternate decision-makers: relationship, 24-hour contact and basis' },
  { key: 'participants', label: 'Other people to inform and care-team contacts' },
  { key: 'topics', label: 'Clinical context: diagnosis, stage, ECOG, capacity/date/basis, directives and prior wishes' },
  { key: 'topics', label: 'Patient and family understanding; what the patient knows' },
  { key: 'topics', label: 'Clinical decisions explicitly agreed' },
  { key: 'openQuestions', label: 'Decisions not discussed' },
  { key: 'openQuestions', label: 'Unresolved questions or differences' },
  { key: 'followUp', label: 'Next steps, owner, review date or conditions' },
];
const MARKER = 'Clinical form details';

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
      const next = FORM_FIELDS.map((field) => section.indexOf(`${field.label}:\n`, valueStart)).filter((index) => index >= 0);
      values[label] = section.slice(valueStart, next.length ? Math.min(...next) : section.length).trimEnd();
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
    const content = group.filter(({ label }) => values[label]?.trim()).map(({ label }) => `${label}:\n${values[label].trim()}`).join('\n');
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
    <div className="doctor-structured-grid">{FORM_FIELDS.map(({ label }) => <label key={label}>{label}<textarea rows={2} value={values[label] ?? ''} onChange={(event) => {
      onFields(writeValues(fields, { ...values, [label]: event.target.value }));
    }} /></label>)}</div>
  </details>;
}
