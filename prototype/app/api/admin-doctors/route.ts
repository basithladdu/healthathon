// Doctor sign-ups for /admin. POST { password } → { rows, truncated }.
// Rows come from healthathon_clinician_requests in the Android app's project (written by the app's
// "verify as a doctor" form); the account email is looked up with the auth admin API.
import { adminBody, adminJson, androidKey, ANDROID_SUPABASE, postgrestError, serviceHeaders } from '../../admin-server.ts';
import { DOCTOR_STATUSES, orderRequests, UUID_PATTERN, type DoctorRequest, type DoctorStatus } from '../../admin/doctor-rules.ts';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const COLUMNS = 'user_id,name,council,registration,status,submitted_at,reviewed_at,review_note,revision';
const LIMIT = 500;

type Raw = Omit<DoctorRequest, 'email'>;
function validRow(value: unknown): value is Raw {
  if (!value || typeof value !== 'object') return false;
  const r = value as Record<string, unknown>;
  return typeof r.user_id === 'string' && UUID_PATTERN.test(r.user_id) && typeof r.name === 'string' && typeof r.council === 'string' && typeof r.registration === 'string'
    && DOCTOR_STATUSES.includes(r.status as DoctorStatus) && typeof r.submitted_at === 'string' && Number.isSafeInteger(r.revision);
}

async function emailOf(key: string, id: string): Promise<string | null> {
  try {
    const response = await fetch(`${ANDROID_SUPABASE}/auth/v1/admin/users/${id}`, { headers: serviceHeaders(key), cache: 'no-store', signal: AbortSignal.timeout(10000) });
    if (!response.ok) return null;
    const user = await response.json().catch(() => null) as { email?: unknown } | null;
    return typeof user?.email === 'string' && user.email ? user.email : null;
  } catch { return null; }
}

export async function POST(request: Request) {
  const checked = await adminBody(request); if ('error' in checked) return checked.error;
  const access = androidKey(); if ('error' in access) return adminJson({ error: access.error }, 503);
  try {
    const response = await fetch(`${ANDROID_SUPABASE}/rest/v1/healthathon_clinician_requests?select=${COLUMNS}&order=submitted_at.desc&limit=${LIMIT}`, { headers: serviceHeaders(access.key), cache: 'no-store', signal: AbortSignal.timeout(20000) });
    if (!response.ok) {
      const failure = await postgrestError(response);
      if (failure.code === 'PGRST205' || failure.code === '42P01') return adminJson({ error: 'The doctor sign-up table is not on the Android project yet (migration 20261019151322_shared_care_clinician_review.sql).' }, 503);
      return adminJson({ error: `Could not load doctor sign-ups: ${failure.message}` }, 502);
    }
    const value = await response.json().catch(() => null) as unknown;
    if (!Array.isArray(value)) return adminJson({ error: 'Supabase sent an unexpected reply.' }, 502);
    const rows = value.filter(validRow);
    // Account emails, eight lookups at a time.
    const emails = new Map<string, string | null>();
    for (let i = 0; i < rows.length; i += 8) {
      const batch = rows.slice(i, i + 8);
      const found = await Promise.all(batch.map(r => emailOf(access.key, r.user_id)));
      batch.forEach((r, j) => emails.set(r.user_id, found[j]));
    }
    const out: DoctorRequest[] = orderRequests(rows.map(r => ({ user_id: r.user_id, name: r.name, council: r.council, registration: r.registration, status: r.status, submitted_at: r.submitted_at,
      reviewed_at: typeof r.reviewed_at === 'string' ? r.reviewed_at : null, review_note: typeof r.review_note === 'string' ? r.review_note : '', revision: r.revision, email: emails.get(r.user_id) ?? null })));
    return adminJson({ rows: out, truncated: value.length >= LIMIT });
  } catch (e) { return adminJson({ error: e instanceof Error ? e.message : 'Could not load doctor sign-ups.' }, 502); }
}
