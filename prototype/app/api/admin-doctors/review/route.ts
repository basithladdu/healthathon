// Approve, reject, suspend or reinstate a doctor sign-up from /admin.
// POST { password, user_id, revision, decision, evidence, note, reviewer } → { row }.
// Calls public.sahara_admin_review_clinician on the Android project (service role only). Like
// healthathon_private.review_clinician it updates healthathon_clinician_requests only at the expected revision and
// writes a healthathon_private.clinician_reviews row in the same transaction; the reviewer is the typed name
// because the shared admin password is not a Supabase account.
import { adminBody, adminJson, androidKey, ANDROID_SUPABASE, postgrestError, serviceHeaders } from '../../../admin-server.ts';
import { checkReview, DOCTOR_TRANSITIONS, type DoctorStatus } from '../../../admin/doctor-rules.ts';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const MIGRATION = 'android/supabase/migrations/20261120120000_admin_clinician_review.sql';

export async function POST(request: Request) {
  const checked = await adminBody(request); if ('error' in checked) return checked.error;
  const input = checkReview(checked.body); if ('error' in input) return adminJson({ error: input.error }, 400);
  const { value } = input;
  // Friendly early check of the allowed transitions; the database checks again under a row lock.
  const from = checked.body.from;
  if (typeof from === 'string' && from in DOCTOR_TRANSITIONS && !DOCTOR_TRANSITIONS[from as DoctorStatus].includes(value.decision)) {
    return adminJson({ error: `A ${from} sign-up cannot be marked ${value.decision}.` }, 400);
  }
  const access = androidKey(); if ('error' in access) return adminJson({ error: access.error }, 503);
  try {
    const response = await fetch(`${ANDROID_SUPABASE}/rest/v1/rpc/sahara_admin_review_clinician`, {
      method: 'POST', headers: { ...serviceHeaders(access.key), 'Content-Type': 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(20000),
      body: JSON.stringify({ p_user: value.user_id, p_revision: value.revision, p_decision: value.decision, p_evidence: value.evidence, p_note: value.note, p_reviewer: value.reviewer }),
    });
    if (!response.ok) {
      const failure = await postgrestError(response);
      if (failure.code === 'PGRST202' || failure.code === '42883') return adminJson({ error: `Reviewing from /admin needs one database change first: apply ${MIGRATION} to the Android project (kjnhtakqzboayqorrlue).` }, 503);
      if (failure.code === '40001') return adminJson({ error: 'This sign-up changed since you loaded it (the doctor may have re-applied, or someone else reviewed it). Refresh and check again.' }, 409);
      if (failure.code === '22023' || failure.code === 'P0001') return adminJson({ error: failure.message }, 400);
      return adminJson({ error: `Could not save the review: ${failure.message}` }, 502);
    }
    const row = await response.json().catch(() => null) as Record<string, unknown> | null;
    if (!row || row.user_id !== value.user_id || row.status !== value.decision || row.revision !== value.revision + 1) return adminJson({ error: 'The review may not have saved as expected. Refresh before trying again.' }, 502);
    return adminJson({ row });
  } catch (e) { return adminJson({ error: e instanceof Error ? e.message : 'Could not save the review.' }, 502); }
}
