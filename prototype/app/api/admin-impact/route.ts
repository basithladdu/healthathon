// Impact numbers for /admin. POST { password } → { tiles, generatedAt }.
// Counts only (never rows), read with the service-role keys. A table that is not on the live project yet, or a key
// that is missing, makes that one tile "not available" instead of failing the page.
import type { ImpactTile } from '../../admin/impact-facts.ts';
import { adminBody, adminJson, androidKey, ANDROID_SUPABASE, postgrestError, serviceHeaders, WEB_SUPABASE } from '../../admin-server.ts';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type Count = { total: number; last30: number | null } | { unavailable: string };

// The survey admin's hidden test submissions (same list as app/api/admin-survey/route.ts).
const SURVEY_TEST_ROWS = ['b88410e6-20bd-4876-9af0-40e888d037fd', '3ab35ab6-50ad-47bd-a151-7c54c4d4864a'];

async function exactCount(base: string, key: string, table: string, filter: string): Promise<number> {
  const response = await fetch(`${base}/rest/v1/${table}?select=*${filter ? `&${filter}` : ''}`, { method: 'HEAD', headers: { ...serviceHeaders(key), Prefer: 'count=exact' }, cache: 'no-store', signal: AbortSignal.timeout(15000) });
  if (!response.ok) {
    // HEAD replies have no body; ask again with GET to read the reason.
    const again = await fetch(`${base}/rest/v1/${table}?select=*&limit=0${filter ? `&${filter}` : ''}`, { headers: serviceHeaders(key), cache: 'no-store', signal: AbortSignal.timeout(15000) });
    const failure = again.ok ? { code: '', message: `Supabase returned ${response.status}.` } : await postgrestError(again);
    throw new Error(failure.code === 'PGRST205' || failure.code === '42P01' ? 'table not on the live project yet' : failure.message);
  }
  const total = Number(response.headers.get('content-range')?.split('/')[1]);
  if (!Number.isSafeInteger(total)) throw new Error('no count in the reply');
  return total;
}

async function count(base: string, key: string, table: string, filter = '', time?: string): Promise<Count> {
  try {
    const since = new Date(Date.now() - 30 * 864e5).toISOString();
    const [total, last30] = await Promise.all([exactCount(base, key, table, filter), time ? exactCount(base, key, table, [filter, `${time}=gte.${encodeURIComponent(since)}`].filter(Boolean).join('&')) : Promise.resolve(null)]);
    return { total, last30 };
  } catch (e) { return { unavailable: e instanceof Error ? e.message : 'could not count' }; }
}

/** Every sign-in on the Android project, paged through the auth admin API (created_at gives the 30-day figure). */
async function accounts(key: string): Promise<Count> {
  try {
    const since = Date.now() - 30 * 864e5; let total = 0, last30 = 0;
    for (let page = 1; page <= 50; page++) {
      const response = await fetch(`${ANDROID_SUPABASE}/auth/v1/admin/users?page=${page}&per_page=1000`, { headers: serviceHeaders(key), cache: 'no-store', signal: AbortSignal.timeout(20000) });
      if (!response.ok) throw new Error((await postgrestError(response)).message);
      const value = await response.json().catch(() => null) as { users?: { created_at?: unknown }[] } | null;
      if (!Array.isArray(value?.users)) throw new Error('unexpected reply from the auth admin API');
      total += value.users.length;
      last30 += value.users.filter(u => typeof u.created_at === 'string' && Date.parse(u.created_at) >= since).length;
      if (value.users.length < 1000) return { total, last30 };
    }
    return { unavailable: 'more than 50,000 accounts; count in Supabase' };
  } catch (e) { return { unavailable: e instanceof Error ? e.message : 'could not count accounts' }; }
}

async function privateCounts(key: string): Promise<Record<string, number> | string> {
  try {
    const response = await fetch(`${ANDROID_SUPABASE}/rest/v1/rpc/sahara_admin_private_counts`, { method: 'POST', headers: { ...serviceHeaders(key), 'Content-Type': 'application/json' }, body: '{}', cache: 'no-store', signal: AbortSignal.timeout(15000) });
    if (!response.ok) { const f = await postgrestError(response); return f.code === 'PGRST202' ? 'needs migration 20261120120000_admin_clinician_review.sql' : f.message; }
    const value = await response.json().catch(() => null) as Record<string, unknown> | null;
    if (!value || typeof value !== 'object') return 'unexpected reply';
    return Object.fromEntries(Object.entries(value).filter(([, v]) => typeof v === 'number')) as Record<string, number>;
  } catch (e) { return e instanceof Error ? e.message : 'could not count'; }
}

const tile = (key: string, group: string, label: string, c: Count, note?: string): ImpactTile =>
  'unavailable' in c ? { key, group, label, total: null, last30: null, unavailable: c.unavailable, note } : { key, group, label, total: c.total, last30: c.last30, note };

export async function POST(request: Request) {
  const checked = await adminBody(request); if ('error' in checked) return checked.error;
  const access = androidKey();
  const webKey = process.env.SUPABASE_SERVICE_ROLE_KEY?.trim();
  const missing = (why: string): { unavailable: string } => ({ unavailable: why });
  const noAndroid = 'error' in access ? missing('Android project key not set') : null;
  const a = 'key' in access ? access.key : '';
  const A = (table: string, filter = '', time?: string) => noAndroid ? Promise.resolve(noAndroid) : count(ANDROID_SUPABASE, a, table, filter, time);

  const [users, people, notes, acks, signatures, analyses, symptoms, distress, taken, suggestions, posts, feedback, pending, approved, rejected, suspended, survey, priv] = await Promise.all([
    noAndroid ? Promise.resolve(noAndroid) : accounts(a),
    A('healthathon_people', '', 'created_at'),
    A('healthathon_records', 'kind=eq.note', 'updated_at'),
    A('healthathon_records', 'kind=eq.ack', 'updated_at'),
    A('healthathon_note_signatures', '', 'created_at'),
    A('sahara_goc_analyses', '', 'created_at'),
    A('healthathon_records', `kind=eq.entry&data->>detail=like.${encodeURIComponent('sahara:symptom:v1*')}`, 'updated_at'),
    A('healthathon_records', `kind=eq.entry&data->>detail=like.${encodeURIComponent('sahara:distress:v1*')}`, 'updated_at'),
    A('healthathon_medicine_events', 'outcome=eq.taken', 'logged_at'),
    A('sahara_centre_suggestions', '', 'created_at'),
    A('community_posts', '', 'created_at'),
    A('sahara_android_feedback', '', 'created_at'),
    A('healthathon_clinician_requests', 'status=eq.pending', 'submitted_at'),
    A('healthathon_clinician_requests', 'status=eq.approved'),
    A('healthathon_clinician_requests', 'status=eq.rejected'),
    A('healthathon_clinician_requests', 'status=eq.suspended'),
    webKey ? count(WEB_SUPABASE, webKey, 'sahara_hospital_submissions', `id=not.in.(${SURVEY_TEST_ROWS.join(',')})`, 'created_at') : Promise.resolve(missing('website project key not set')),
    noAndroid ? Promise.resolve(noAndroid.unavailable) : privateCounts(a),
  ]);
  const fromPrivate = (field: string, field30?: string): Count => typeof priv === 'string' ? { unavailable: priv }
    : typeof priv[field] === 'number' ? { total: priv[field], last30: field30 && typeof priv[field30] === 'number' ? priv[field30] : null } : { unavailable: 'emergency card links table not on the live project yet' };

  const tiles: ImpactTile[] = [
    tile('accounts', 'People reached', 'Accounts (sign-ins on the Android app)', users, 'New in last 30 days: by sign-up date.'),
    tile('people', 'People reached', 'Patients added to a care record', people),
    tile('notes', 'Goals of care', 'Goals-of-care notes published', notes),
    tile('acks', 'Goals of care', 'Notes confirmed by the patient or family', acks),
    tile('signatures', 'Goals of care', 'Notes signed (drawn or photo signature)', signatures),
    tile('analyses', 'Goals of care', 'Recorded conversations analysed', analyses),
    tile('symptoms', 'Daily care', 'Symptom notes', symptoms, 'Synced to the cloud only; last 30 days by last save.'),
    tile('distress', 'Daily care', 'Distress thermometer check-ins', distress, 'Synced to the cloud only; last 30 days by last save.'),
    tile('doses', 'Daily care', 'Medicine doses marked taken', taken),
    tile('emergency', 'Emergency', 'Emergency card links made', fromPrivate('emergency_links', 'emergency_links_30d')),
    tile('emergency_live', 'Emergency', 'Emergency card links live now', fromPrivate('emergency_links_live')),
    tile('emergency_views', 'Emergency', 'Emergency card views (all time)', fromPrivate('emergency_views', 'emergency_viewed_30d'), 'Last 30 days: links viewed at least once.'),
    tile('suggestions', 'Community', 'Palliative centres suggested by users', suggestions),
    tile('posts', 'Community', 'Community posts', posts),
    tile('feedback', 'Community', 'In-app feedback ratings', feedback),
    tile('doctors_pending', 'Doctors', 'Doctor sign-ups waiting', pending, 'Last 30 days: submitted or re-submitted.'),
    tile('doctors_approved', 'Doctors', 'Doctors approved', approved),
    tile('doctors_rejected', 'Doctors', 'Doctor sign-ups rejected', rejected),
    tile('doctors_suspended', 'Doctors', 'Doctors suspended', suspended),
    tile('survey', 'Hospitals', 'Hospital survey submissions', survey, 'Team test submissions excluded.'),
  ];
  const setup = [
    ...('error' in access ? [access.error] : []),
    ...(webKey ? [] : ['To count hospital survey submissions, add SUPABASE_SERVICE_ROLE_KEY in Vercel (the same key the survey tab uses), then redeploy.']),
  ];
  return adminJson({ tiles, setup, generatedAt: new Date().toISOString() });
}
