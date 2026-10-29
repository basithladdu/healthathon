// Survey admin data. The page sends the admin password (ADMIN_PASSWORD in Vercel); rows are read on the
// server with the Supabase secret key (SUPABASE_SERVICE_ROLE_KEY in Vercel), so nobody has to sign in.
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
const SUPABASE = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const SELECT = 'id,facility,created_at,c:sahara_hospital_contributors(full_name,respondent_role,council,registration_state,registration_number,mobile,email)';
const json = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Robots-Tag': 'noindex' } });

type Raw = { id: string; facility: Record<string, unknown>; created_at: string; c: Record<string, unknown> | Record<string, unknown>[] | null };

export async function POST(request: Request) {
  const password = process.env.ADMIN_PASSWORD || '0';
  const sent = await request.json().catch(() => null) as { password?: unknown } | null;
  if (sent?.password !== password) return json({ error: 'Wrong password.' }, 401);
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key) return json({ error: 'Password is right. To show the responses, add SUPABASE_SERVICE_ROLE_KEY in Vercel (Supabase → Project Settings → API keys → secret key), then redeploy.' }, 503);
  try {
    const rows: Record<string, unknown>[] = [];
    for (let from = 0; from < 100000; from += 1000) {
      const response = await fetch(`${SUPABASE}/rest/v1/sahara_hospital_submissions?select=${SELECT}&order=created_at.desc`, { headers: { apikey: key, Authorization: `Bearer ${key}`, Range: `${from}-${from + 999}` }, cache: 'no-store', signal: AbortSignal.timeout(20000) });
      const page = await response.json().catch(() => null) as Raw[] | { message?: string } | null;
      if (!response.ok || !Array.isArray(page)) throw new Error((page as { message?: string } | null)?.message ?? `Supabase returned ${response.status}.`);
      page.forEach(r => { const c = (Array.isArray(r.c) ? r.c[0] : r.c) ?? {}; rows.push({ id: r.id, facility: r.facility ?? {}, created_at: r.created_at, ...c, revision: 0, decision: 'pending_review' }); });
      if (page.length < 1000) break;
    }
    return json({ rows });
  } catch (e) { return json({ error: e instanceof Error ? e.message : 'Could not load the survey submissions.' }, 502); }
}
