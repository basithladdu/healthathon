// Pure helpers shared by the ED card API route, the public /ed/<token> page and the issuing device.
// The card token is the only thing in the QR URL: random, >= 128 bits, no personal data (tech spec §7).
import type { EctprRecord } from './ectpr-model.ts';

/** 32 random bytes as base64url = 43 characters (256 bits). */
export const ED_TOKEN_PATTERN = /^[A-Za-z0-9_-]{22,64}$/;
export const MANAGE_KEY_PATTERN = /^[A-Za-z0-9_-]{22,64}$/;
export const BREAK_GLASS_MIN_REASON = 10;

export function randomUrlToken(bytes = 32): string {
  const raw = crypto.getRandomValues(new Uint8Array(bytes));
  let binary = '';
  raw.forEach((b) => { binary += String.fromCharCode(b); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function sha256Hex(input: string | Uint8Array): Promise<string> {
  const data = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  const digest = await crypto.subtle.digest('SHA-256', data as BufferSource);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Rule V7: only a signed, Active plan with the treating consultant's registration number can be put on a card. */
export function issuableProblem(record: EctprRecord | null | undefined): string | null {
  if (!record || typeof record !== 'object') return 'No care plan was sent.';
  if (record.status !== 'ACTIVE') return 'Only the Active (signed) care plan can be put on an emergency card.';
  const consultant = (record.signatures ?? []).find((s) => s.role === 'TREATING_CONSULTANT');
  if (!consultant || !consultant.name?.trim() || !consultant.registrationNo?.trim()) return 'The treating consultant has not signed this plan with a registration number.';
  if (!record.patient?.uhid || !record.reviewDue) return 'The plan is missing the UHID or review date.';
  return null;
}

/** The goals-of-care transcript and note stay on the issuing device: they never go on a card, even behind break-glass. */
export function withoutGocTranscript(record: EctprRecord): EctprRecord {
  if (!record || typeof record !== 'object') return record;
  const rest = { ...record };
  delete rest.gocTranscript;
  return rest;
}

/**
 * What the QR view shows without break-glass: the ED Quick View fields (clinical spec §6) and nothing more.
 * Address, date of birth, ID digits, the patient's own words and family notes stay behind break-glass.
 */
export function quickViewSnapshot(record: EctprRecord): EctprRecord {
  const dm = record.decisionMakers?.primary ?? null;
  const alt = record.decisionMakers?.alternate ?? null;
  const strip = (d: typeof dm) => (d ? { ...d, idType: null, idLast4: null } : null);
  return {
    ...withoutGocTranscript(record),
    patient: { ...record.patient, dob: '', address: '', phone: '', gender: '' },
    decisionMakers: { primary: strip(dm), alternate: strip(alt), familyToInform: [], familyConsensusNote: '' },
    values: { patientKnows: null, ownWords: '', unacceptableOutcomes: [], familyAskedNotToTell: false, directQuestionPlan: '', financialConcernDiscussed: null, socialWorkReferral: null },
    capacity: { ...record.capacity, basis: '' },
    ...(record.withdrawal ? { withdrawal: { ...record.withdrawal, primaryBoardMembers: '', secondaryBoardMembers: '', boardDecision: '' } } : {}),
  };
}

/** Indian mobile numbers to E.164 (+91XXXXXXXXXX). Returns null when it does not look like a mobile number. */
export function indianMobileE164(phone: string | null | undefined): string | null {
  const digits = String(phone ?? '').replace(/[^0-9+]/g, '');
  const plain = digits.replace(/^\+/, '');
  const bare = plain.length === 12 && plain.startsWith('91') ? plain.slice(2) : plain.length === 11 && plain.startsWith('0') ? plain.slice(1) : plain;
  if (/^[6-9][0-9]{9}$/.test(bare)) return `+91${bare}`;
  if (/^\+[1-9][0-9]{7,14}$/.test(digits)) return digits;
  return null;
}

export type EdCardViewResult =
  | { status: 'live'; record: EctprRecord; issuedAt: string; full: boolean; sms?: { sent: boolean; message: string } }
  | { status: 'revoked'; revokedAt: string }
  | { status: 'unknown' }
  | { status: 'not-set-up' | 'not-configured' | 'unreachable' | 'error'; message: string };
