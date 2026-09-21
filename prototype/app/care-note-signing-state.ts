import type { DraftFieldKey, SummaryRelease } from './summary-state.ts';

export type CareNoteKind = 'discussion' | 'decisions-recorded';
export type CareNoteSignerRole = 'patient' | 'family';
export const DOCTOR_SIGNED_AWAITING_REVIEW = 'Doctor signed; awaiting patient/family review';
export const CARE_NOTE_KIND_LABELS: Record<CareNoteKind, string> = {
  discussion: 'Discussion', 'decisions-recorded': 'Decisions recorded',
};

export type CareNoteAcknowledgement = Readonly<{
  id: string;
  patientId: string;
  releaseId: string;
  version: number;
  releaseSnapshot: string;
  signerName: string;
  signerRole: CareNoteSignerRole;
  signedAt: string;
  method: 'typed';
  statement: string;
}>;

const KEYS: readonly DraftFieldKey[] = ['priorities', 'participants', 'topics', 'openQuestions', 'followUp'];

export function isCareNoteKind(value: unknown): value is CareNoteKind {
  return value === 'discussion' || value === 'decisions-recorded';
}

export function careNoteReleaseId(release: SummaryRelease): string {
  return release.id ?? `${release.patientId}:care-note:${release.number}:${release.releasedAt}`;
}

// A canonical text snapshot binds an acknowledgement to the exact saved version.
// It is not a cryptographic signature or an identity check.
export function careNoteContentSnapshot(release: SummaryRelease): string {
  return JSON.stringify({
    id: careNoteReleaseId(release), patientId: release.patientId, version: release.number,
    releasedAt: release.releasedAt, physician: release.physician, noteKind: release.noteKind ?? null,
    ...(release.priorVersion !== undefined ? { priorVersion: release.priorVersion } : {}),
    source: release.source, fields: KEYS.map((key) => [key, release.fields?.[key] ?? null]),
    statuses: KEYS.map((key) => [key, release.statuses?.[key] ?? null]),
    excerpts: KEYS.map((key) => [key, release.excerpts?.[key] ?? null]),
    coverage: Object.entries(release.coverage ?? {}).sort(([first], [second]) => first.localeCompare(second)),
    authorisation: release.authorisation,
    signature: release.signature ? [release.signature.name, release.signature.signedAt, release.signature.method] : null,
  });
}

function sameRelease(entry: CareNoteAcknowledgement, release: SummaryRelease): boolean {
  return entry.patientId === release.patientId && entry.releaseId === careNoteReleaseId(release) && entry.version === release.number;
}

export function matchingCareNoteAcknowledgements(entries: readonly CareNoteAcknowledgement[], release: SummaryRelease): CareNoteAcknowledgement[] {
  const snapshot = careNoteContentSnapshot(release);
  return entries.filter((entry) => sameRelease(entry, release) && entry.releaseSnapshot === snapshot);
}

export function hasChangedSignedCareNote(entries: readonly CareNoteAcknowledgement[], release: SummaryRelease): boolean {
  const snapshot = careNoteContentSnapshot(release);
  return entries.some((entry) => sameRelease(entry, release) && entry.releaseSnapshot !== snapshot);
}

export function careNoteAcknowledgementStatement(kind: CareNoteKind): string {
  return kind === 'decisions-recorded'
    ? 'I reviewed this exact version. It accurately records our discussion and the decisions written in it.'
    : 'I reviewed this exact version. It accurately records our discussion.';
}

export function createCareNoteAcknowledgement(release: SummaryRelease, input: {
  patientId: string; signerName: string; signatureName: string; signerRole: CareNoteSignerRole;
  reviewed: boolean; signedAt: string;
}): CareNoteAcknowledgement | null {
  const signatureName = input.signatureName.trim();
  if (input.patientId !== release.patientId || !input.reviewed || !isCareNoteKind(release.noteKind)) return null;
  if (!release.patientId || !Number.isInteger(release.number) || release.number < 1) return null;
  if (!release.signature || release.signature.method !== 'typed' || release.signature.name.trim().toLowerCase() !== release.physician.trim().toLowerCase()) return null;
  if (!signatureName || signatureName.length > 160 || signatureName.toLowerCase() !== input.signerName.trim().toLowerCase()) return null;
  if (input.signerRole !== 'patient' && input.signerRole !== 'family') return null;
  const signedAt = Date.parse(input.signedAt);
  const doctorSignedAt = Date.parse(release.signature.signedAt);
  if (!Number.isFinite(signedAt) || !Number.isFinite(doctorSignedAt) || signedAt < doctorSignedAt) return null;
  const releaseId = careNoteReleaseId(release);
  return Object.freeze({
    id: `${releaseId}:${input.signerRole}:${input.signedAt}`,
    patientId: release.patientId, releaseId, version: release.number,
    releaseSnapshot: careNoteContentSnapshot(release), signerName: signatureName,
    signerRole: input.signerRole, signedAt: input.signedAt, method: 'typed',
    statement: careNoteAcknowledgementStatement(release.noteKind),
  });
}

export function appendCareNoteAcknowledgement(entries: readonly CareNoteAcknowledgement[], release: SummaryRelease, acknowledgement: CareNoteAcknowledgement): readonly CareNoteAcknowledgement[] {
  if (!sameRelease(acknowledgement, release) || hasChangedSignedCareNote(entries, release)) return entries;
  const validated = createCareNoteAcknowledgement(release, {
    patientId: acknowledgement.patientId, signerName: acknowledgement.signerName,
    signatureName: acknowledgement.signerName, signerRole: acknowledgement.signerRole,
    reviewed: true, signedAt: acknowledgement.signedAt,
  });
  if (!validated || acknowledgement.method !== 'typed' || acknowledgement.id !== validated.id || acknowledgement.statement !== validated.statement || acknowledgement.releaseSnapshot !== validated.releaseSnapshot) return entries;
  if (entries.some((entry) => entry.id === acknowledgement.id || (sameRelease(entry, release) && entry.signerRole === acknowledgement.signerRole && entry.signerName.trim().toLowerCase() === acknowledgement.signerName.trim().toLowerCase()))) return entries;
  return Object.freeze([...entries, Object.freeze({ ...acknowledgement })]);
}
