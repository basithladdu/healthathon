// Shared by the /admin doctor sign-ups tab and its API routes (no server-only imports here).
// Limits match healthathon_private.review_clinician (android/supabase/migrations/20261019151322_shared_care_clinician_review.sql)
// and the admin RPC in android/supabase/migrations/20261120120000_admin_clinician_review.sql.

export const DOCTOR_STATUSES = ['pending', 'approved', 'rejected', 'suspended'] as const;
export type DoctorStatus = typeof DOCTOR_STATUSES[number];
export type DoctorDecision = 'approved' | 'rejected' | 'suspended';
export type DoctorRequest = { user_id: string; name: string; council: string; registration: string; status: DoctorStatus; submitted_at: string; reviewed_at: string | null; review_note: string; revision: number; email: string | null };

/** The NMC's public Indian Medical Register search (checked 20 Nov 2026: searches by name, registration number, year and State Medical Council). */
export const NMC_REGISTER_URL = 'https://www.nmc.org.in/information-desk/indian-medical-register';

/** Which decisions the team can take from each status. A rejected doctor re-applies from the app, which resets the request to pending. */
export const DOCTOR_TRANSITIONS: Record<DoctorStatus, DoctorDecision[]> = {
  pending: ['approved', 'rejected'],
  approved: ['suspended'],
  suspended: ['approved'],
  rejected: [],
};

export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const APPROVE_NOTE = 'Registration checked and approved by the Sahara team.';

export type ReviewInput = { user_id: string; revision: number; decision: DoctorDecision; evidence: string; note: string; reviewer: string };

/** Validates a review request body (without the password). Returns the cleaned input or an error message. */
export function checkReview(body: Record<string, unknown>): { value: ReviewInput } | { error: string } {
  const { user_id, revision, decision } = body;
  if (typeof user_id !== 'string' || !UUID_PATTERN.test(user_id)) return { error: 'Choose a doctor sign-up to review.' };
  if (typeof revision !== 'number' || !Number.isSafeInteger(revision) || revision < 1) return { error: 'This sign-up has no valid revision. Refresh and try again.' };
  if (decision !== 'approved' && decision !== 'rejected' && decision !== 'suspended') return { error: 'Choose approve, reject or suspend.' };
  const reviewer = typeof body.reviewer === 'string' ? body.reviewer.trim() : '';
  if (reviewer.length < 2 || reviewer.length > 100) return { error: 'Add your name (2 to 100 characters) so the review log shows who decided.' };
  const evidence = typeof body.evidence === 'string' ? body.evidence.trim() : '';
  if (evidence.length < 8 || evidence.length > 1200) return { error: 'Say where you checked the registration (8 to 1200 characters), for example “NMC Indian Medical Register, reg. no. matched”.' };
  let note = typeof body.note === 'string' ? body.note.trim() : '';
  if (!note && decision === 'approved') note = APPROVE_NOTE;
  if (note.length < 8 || note.length > 2000) return { error: decision === 'approved' ? 'The note must be 8 to 2000 characters.' : `Give a short reason (8 to 2000 characters). The doctor sees it in the app.` };
  return { value: { user_id, revision, decision, evidence, note, reviewer } };
}

/** Pending requests first (oldest first, so nobody waits longest), then the rest newest first. */
export function orderRequests<T extends { status: string; submitted_at: string }>(rows: T[]): T[] {
  const t = (r: T) => Date.parse(r.submitted_at) || 0;
  return [...rows].sort((a, b) => {
    const pa = a.status === 'pending' ? 0 : 1, pb = b.status === 'pending' ? 0 : 1;
    if (pa !== pb) return pa - pb;
    return pa === 0 ? t(a) - t(b) : t(b) - t(a);
  });
}
