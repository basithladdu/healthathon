// Survey admin data, read on the server with one reviewer account so the admin page needs no sign-in.
// Set SAHARA_ADMIN_EMAIL and SAHARA_ADMIN_PASSWORD in Vercel; that account needs the directory_reviewer role.
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
const SUPABASE = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const KEY = 'sb_publishable_43mvHQiHJXVGwS71SX0GVA_GWxXBVOC';
const PAGE_SIZE = 50;
const json = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Robots-Tag': 'noindex' } });

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function call(path: string, body: unknown, token?: string): Promise<any> {
  const response = await fetch(SUPABASE + path, { method: 'POST', headers: { apikey: KEY, 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body), cache: 'no-store', signal: AbortSignal.timeout(20000) });
  const value = await response.json().catch(() => null) as { message?: string; error_description?: string } | null;
  if (!response.ok) throw new Error(value?.message ?? value?.error_description ?? `Supabase returned ${response.status}.`);
  return value;
}

export async function GET() {
  const email = process.env.SAHARA_ADMIN_EMAIL, password = process.env.SAHARA_ADMIN_PASSWORD;
  if (!email || !password) return json({ error: 'Not set up yet: add SAHARA_ADMIN_EMAIL and SAHARA_ADMIN_PASSWORD in Vercel, then redeploy.' }, 503);
  try {
    const { access_token: token } = await call('/auth/v1/token?grant_type=password', { email, password });
    if (await call('/rest/v1/rpc/healthathon_staff_role', { p_role: 'directory_reviewer' }, token) !== true) return json({ error: `${email} does not have the directory_reviewer role yet.` }, 503);
    const rows = new Map<string, { id: string }>();
    for (let offset = 0; offset <= 100000; offset += PAGE_SIZE) {
      const page = await call('/rest/v1/rpc/sahara_directory_queue', { p_offset: offset }, token);
      if (!Array.isArray(page)) throw new Error('The survey submissions could not be read.');
      page.forEach((r: { id: string }) => rows.set(r.id, r));
      if (page.length < PAGE_SIZE) break;
    }
    return json({ rows: [...rows.values()] });
  } catch (e) { return json({ error: e instanceof Error ? e.message : 'Could not load the survey submissions.' }, 502); }
}
