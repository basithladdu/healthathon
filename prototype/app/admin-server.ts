// Server-only helpers for the /admin routes (doctor sign-ups, impact).
// Access is the same shared password as the survey admin (ADMIN_SURVEY_PASSWORD or ADMIN_PASSWORD, set in Vercel),
// compared timing-safe. Data is read with service-role keys that live only in Vercel:
//  - SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY: the Android app's project, Saanthvana (kjnhtakqzboayqorrlue),
//    where doctor sign-ups (healthathon_clinician_requests) and the app's care data live.
//  - SUPABASE_SERVICE_ROLE_KEY: the website's project (cghlkqeqiokjqdionrkc), used by the survey admin.
import { Buffer } from 'node:buffer';
import { createHash, timingSafeEqual } from 'node:crypto';

export const ANDROID_PROJECT_REF = 'kjnhtakqzboayqorrlue';
export const ANDROID_SUPABASE = `https://${ANDROID_PROJECT_REF}.supabase.co`;
export const WEB_SUPABASE = 'https://cghlkqeqiokjqdionrkc.supabase.co';
export const ANDROID_KEY_ENV = 'SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY';

export const adminJson = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Robots-Tag': 'noindex' } });

const digest = (value: string) => createHash('sha256').update(value).digest();
export const passwordMatches = (sent: unknown, expected: string) => typeof sent === 'string' && timingSafeEqual(digest(sent), digest(expected));

/** Reads the JSON body and checks the admin password. Returns the body, or the error Response to send. */
export async function adminBody(request: Request): Promise<{ body: Record<string, unknown> } | { error: Response }> {
  const password = process.env.ADMIN_SURVEY_PASSWORD || process.env.ADMIN_PASSWORD;
  if (!password) return { error: adminJson({ error: 'Admin is not set up. Add ADMIN_PASSWORD in Vercel, then redeploy.' }, 503) };
  if (!(request.headers.get('content-type') ?? '').toLowerCase().includes('application/json')) return { error: adminJson({ error: 'Send JSON.' }, 415) };
  const body = await request.json().catch(() => null) as unknown;
  if (!body || typeof body !== 'object' || Array.isArray(body)) return { error: adminJson({ error: 'Send JSON.' }, 400) };
  if (!passwordMatches((body as Record<string, unknown>).password, password)) return { error: adminJson({ error: 'Wrong password.' }, 401) };
  return { body: body as Record<string, unknown> };
}

/** A JWT-style service key names its project; refuse one for another project. New sb_secret_ keys carry no project. */
function keyProject(key: string): string | null {
  const part = key.split('.')[1]; if (!part) return null;
  try { const claims = JSON.parse(Buffer.from(part, 'base64url').toString('utf8')) as { ref?: unknown }; return typeof claims.ref === 'string' ? claims.ref : null; } catch { return null; }
}
export function androidKey(): { key: string } | { error: string } {
  const key = process.env[ANDROID_KEY_ENV]?.trim();
  if (!key) return { error: `Password is right. To show this, add ${ANDROID_KEY_ENV} in Vercel (Supabase project ${ANDROID_PROJECT_REF} → Project Settings → API keys → secret key), then redeploy.` };
  const ref = keyProject(key);
  if (ref && ref !== ANDROID_PROJECT_REF) return { error: `${ANDROID_KEY_ENV} belongs to another Supabase project. Use the secret key of ${ANDROID_PROJECT_REF}.` };
  return { key };
}

export const serviceHeaders = (key: string): Record<string, string> => ({ apikey: key, ...(key.startsWith('sb_') ? {} : { Authorization: `Bearer ${key}` }) });

export type PostgrestError = { status: number; code: string; message: string };
export async function postgrestError(response: Response): Promise<PostgrestError> {
  const value = await response.json().catch(() => null) as { code?: unknown; message?: unknown; msg?: unknown } | null;
  const message = typeof value?.message === 'string' ? value.message : typeof value?.msg === 'string' ? value.msg : `Supabase returned ${response.status}.`;
  return { status: response.status, code: typeof value?.code === 'string' ? value.code : '', message: message.slice(0, 300) };
}
