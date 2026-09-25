import type { DraftFieldKey, SummaryRelease } from './summary-state.ts';

export const FAMILY_NOTE_FIELDS: readonly { key: DraftFieldKey; label: string; hindi: string }[] = [
  { key: 'priorities', label: 'What matters to you', hindi: 'आपके लिए क्या ज़रूरी है' },
  { key: 'participants', label: 'Who was there', hindi: 'कौन साथ था' },
  { key: 'topics', label: 'What you discussed', hindi: 'क्या बात हुई' },
  { key: 'openQuestions', label: 'Still to discuss', hindi: 'क्या बात करना बाकी है' },
  { key: 'followUp', label: 'Next steps', hindi: 'आगे क्या करना है' },
];

export type FamilyNoteComparisonRow = {
  key: DraftFieldKey | 'source';
  label: string;
  hindi: string;
  earlier: string | null;
  later: string | null;
  change: 'changed' | 'unchanged' | 'unavailable';
};

export type FamilyNoteComparison = {
  earlier: SummaryRelease;
  later: SummaryRelease;
  mode: 'fields' | 'source';
  rows: FamilyNoteComparisonRow[];
};

export function selectFamilyNoteVersions(releases: readonly SummaryRelease[], patientId: string): SummaryRelease[] {
  return releases.filter((release) => release.patientId === patientId).sort((a, b) => b.number - a.number);
}

export function compareFamilyNoteVersions(releases: readonly SummaryRelease[], patientId: string, firstNumber: number, secondNumber: number): FamilyNoteComparison | null {
  if (firstNumber === secondNumber) return null;
  const versions = selectFamilyNoteVersions(releases, patientId);
  const first = versions.find((release) => release.number === firstNumber);
  const second = versions.find((release) => release.number === secondNumber);
  if (!first || !second) return null;
  const [earlier, later] = first.number < second.number ? [first, second] : [second, first];
  const mode = earlier.fields && later.fields ? 'fields' : 'source';
  const fields = mode === 'fields' ? FAMILY_NOTE_FIELDS : [{ key: 'source' as const, label: 'Saved conversation', hindi: 'सेव की गई बातचीत' }];
  const rows: FamilyNoteComparisonRow[] = fields.map((field) => {
    const earlierText = field.key === 'source' ? earlier.source : earlier.fields?.[field.key] ?? null;
    const laterText = field.key === 'source' ? later.source : later.fields?.[field.key] ?? null;
    return {
      ...field,
      earlier: earlierText,
      later: laterText,
      change: earlierText === null || laterText === null ? 'unavailable' : earlierText === laterText ? 'unchanged' : 'changed',
    };
  });
  return { earlier, later, mode, rows };
}

export function familyNoteHasText(release: SummaryRelease): boolean {
  return release.fields !== null || release.source !== null;
}

function noteMetadata(release: SummaryRelease): string {
  return `Version ${release.number}\nReviewed by: ${release.physician}\nApproved on: ${release.releasedAt}${release.authorisation ? `\nPermission recorded: ${release.authorisation}` : ''}`;
}

export function exportFamilyNoteVersion(releases: readonly SummaryRelease[], patientId: string, patientName: string, number: number): string | null {
  const release = selectFamilyNoteVersions(releases, patientId).find((version) => version.number === number);
  if (!release || !familyNoteHasText(release)) return null;
  return [
    `SAANTHVANA — CARE NOTE FOR ${patientName}`,
    `Patient: ${patientId}`,
    noteMetadata(release),
    ...(release.fields ? FAMILY_NOTE_FIELDS.map(({ key, label }) => `${label}\n${release.fields![key]}`) : [`Saved conversation\n${release.source}`]),
    'Conversation note. Not a prescription, signed medical order or legal directive.',
  ].join('\n\n');
}

export function exportFamilyNoteComparison(releases: readonly SummaryRelease[], patientId: string, patientName: string, firstNumber: number, secondNumber: number): string | null {
  const comparison = compareFamilyNoteVersions(releases, patientId, firstNumber, secondNumber);
  if (!comparison) return null;
  return [
    `SAANTHVANA — CARE NOTE COMPARISON FOR ${patientName}`,
    `Patient: ${patientId}`,
    noteMetadata(comparison.earlier),
    noteMetadata(comparison.later),
    'Changed / unchanged describes exact saved text only. It does not explain why a change was made.',
    ...comparison.rows.map((row) => [
      `${row.label} — ${row.change === 'unavailable' ? 'Cannot compare: text was not saved for one or both versions' : row.change === 'changed' ? 'Changed' : 'Unchanged'}`,
      `VERSION ${comparison.earlier.number}\n${row.earlier ?? '[Text was not saved for this version]'}`,
      `VERSION ${comparison.later.number}\n${row.later ?? '[Text was not saved for this version]'}`,
    ].join('\n\n')),
    'Conversation note. Not a prescription, signed medical order or legal directive.',
  ].join('\n\n');
}
