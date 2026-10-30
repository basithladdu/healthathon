// "Send review reminder" for a care plan whose review is near (tech spec §8). Same SMS providers as break-glass (../ed-card/sms.ts).
//   status - is SMS set up on this server?   send - one fixed message, to one number.
// Guards against being used to text strangers: same-site only, fixed wording, validated name and date, and a limit per number.
// The limit is in memory, so it is per server instance. There is no server-side sign-in to check yet (records live on the device).
import { indianMobileE164 } from '../../ed-card-snapshot.ts';
import { reviewSmsProvider, sendReviewReminderSms } from '../ed-card/sms.ts';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const LIMIT_PER_NUMBER = 3;
const WINDOW_MS = 60 * 60 * 1000;
const sends = new Map<string, number[]>();

function withinLimit(number: string, now: number): boolean {
  const recent = (sends.get(number) ?? []).filter((at) => now - at < WINDOW_MS);
  if (recent.length >= LIMIT_PER_NUMBER) { sends.set(number, recent); return false; }
  sends.set(number, [...recent, now]);
  return true;
}

const json = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });

function sameSite(request: Request): boolean {
  const origin = request.headers.get('origin');
  if (!origin) return true; // Not a browser cross-site request.
  try { return new URL(origin).host === (request.headers.get('host') ?? new URL(request.url).host); } catch { return false; }
}

export async function POST(request: Request) {
  if (!sameSite(request)) return json({ status: 'error', message: 'Not allowed from another site.' }, 403);
  const input = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!input || typeof input !== 'object') return json({ status: 'error', message: 'Send JSON.' }, 400);

  if (input.action === 'status') return json({ status: 'ok', configured: reviewSmsProvider() !== null });
  if (input.action !== 'send') return json({ status: 'error', message: 'Unknown action.' }, 400);

  const to = indianMobileE164(typeof input.phone === 'string' ? input.phone : null);
  const reviewDue = typeof input.reviewDue === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(input.reviewDue) ? input.reviewDue : null;
  if (!to) return json({ status: 'no-valid-number' }, 400);
  if (!reviewDue || Number.isNaN(new Date(`${reviewDue}T00:00:00Z`).getTime())) return json({ status: 'error', message: 'Missing review date.' }, 400);
  const name = typeof input.patientFirstName === 'string' && /^[\p{L}\p{M} .'-]{1,40}$/u.test(input.patientFirstName.trim()) ? input.patientFirstName.trim() : 'the patient';

  if (!reviewSmsProvider()) return json({ status: 'not-configured', sent: false });
  if (!withinLimit(to, Date.now())) return json({ status: 'too-many', sent: false }, 429);
  const result = await sendReviewReminderSms({ phone: to, patientFirstName: name, reviewDue });
  return json({ status: result.status, sent: result.sent });
}
