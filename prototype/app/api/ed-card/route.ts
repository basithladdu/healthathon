// ED card tokens (tech spec §6–7). One POST endpoint, four actions:
//   issue        – the issuing device stores a snapshot of the signed (Active) plan under a fresh random token
//   revoke       – the issuing device withdraws its token(s) (plan revoked or superseded); needs the manage key
//   view         – public, no sign-in: the ED Quick View fields for a token; logged as QR_VIEW
//   break-glass  – public, needs a typed reason: the full plan; logged as BREAK_GLASS; SMS to the primary decision-maker
// Supabase is reached only from here with SUPABASE_SERVICE_ROLE_KEY (RLS on, no anon policies).
// Owner must apply supabase/migrations/20261029230000_sahara_ed_cards.sql first; until then every action
// answers 503 { status: 'not-set-up' } with a clear message.
import type { EctprRecord } from '../../ectpr-model.ts';
import { BREAK_GLASS_MIN_REASON, ED_TOKEN_PATTERN, MANAGE_KEY_PATTERN, issuableProblem, quickViewSnapshot, sha256Hex, withoutGocTranscript } from '../../ed-card-snapshot.ts';
import { sendBreakGlassSms } from './sms.ts';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const SUPABASE = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const MAX_BODY = 200_000;
const NOT_SET_UP = 'Emergency card links are not set up on the server yet: the owner needs to run the sahara_ed_cards migration in Supabase. Use the offline code printed on the card.';
const NOT_CONFIGURED = 'Emergency card links are not configured on this server (SUPABASE_SERVICE_ROLE_KEY is missing). Use the offline code printed on the card.';

const json = (body: unknown, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Robots-Tag': 'noindex', 'Referrer-Policy': 'no-referrer' } });

class TableMissing extends Error {}
class UpstreamError extends Error {}

function keyHeaders(key: string): Record<string, string> {
  // sb_ (new-style) secret keys go only in the apikey header; legacy JWT keys also need Authorization.
  return { apikey: key, ...(key.startsWith('sb_') ? {} : { Authorization: `Bearer ${key}` }) };
}

async function rest(key: string, path: string, init: { method?: string; body?: unknown; prefer?: string } = {}): Promise<unknown> {
  const response = await fetch(`${SUPABASE}/rest/v1/${path}`, {
    method: init.method ?? 'GET',
    headers: { ...keyHeaders(key), 'Content-Type': 'application/json', ...(init.prefer ? { Prefer: init.prefer } : {}) },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
    cache: 'no-store',
    signal: AbortSignal.timeout(15_000),
  });
  const body = await response.json().catch(() => null) as { code?: string; message?: string } | unknown[] | null;
  if (!response.ok) {
    const code = (body as { code?: string } | null)?.code ?? '';
    const message = (body as { message?: string } | null)?.message ?? '';
    // PostgREST: PGRST205 "Could not find the table"; Postgres: 42P01 undefined_table.
    if (code === 'PGRST205' || code === '42P01' || /could not find the table|relation .* does not exist/i.test(message)) throw new TableMissing(message);
    throw new UpstreamError(message || `Supabase returned ${response.status}.`);
  }
  return body;
}

type CardRow = { token: string; patient_ref: string; record_id: string; version: number; snapshot: EctprRecord; manage_key_hash: string; created_at: string; revoked_at: string | null };

async function findCard(key: string, token: string): Promise<CardRow | null> {
  const rows = await rest(key, `sahara_ed_cards?token=eq.${encodeURIComponent(token)}&select=*`) as CardRow[];
  return Array.isArray(rows) && rows[0] ? rows[0] : null;
}

async function logAccess(key: string, row: { token: string; kind: 'QR_VIEW' | 'BREAK_GLASS'; reason?: string | null; ua?: string | null; sms_status?: string | null }) {
  await rest(key, 'sahara_ed_access_log', { method: 'POST', body: { token: row.token, kind: row.kind, reason: row.reason ?? null, ua: row.ua?.slice(0, 300) ?? null, sms_status: row.sms_status ?? null }, prefer: 'return=minimal' });
}

function str(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

export async function POST(request: Request) {
  const length = Number(request.headers.get('content-length') ?? '0');
  if (length > MAX_BODY) return json({ status: 'error', message: 'Request too large.' }, 413);
  const text = await request.text().catch(() => '');
  if (text.length > MAX_BODY) return json({ status: 'error', message: 'Request too large.' }, 413);
  let input: Record<string, unknown>;
  try { input = JSON.parse(text) as Record<string, unknown>; } catch { return json({ status: 'error', message: 'Send JSON.' }, 400); }
  if (!input || typeof input !== 'object') return json({ status: 'error', message: 'Send JSON.' }, 400);

  const action = input.action;
  const token = str(input.token, 80);
  if (!ED_TOKEN_PATTERN.test(token)) return json({ status: 'unknown' }, 404);

  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key) return json({ status: 'not-configured', message: NOT_CONFIGURED }, 503);

  try {
    if (action === 'issue') {
      const manageKey = str(input.manageKey, 80);
      if (!MANAGE_KEY_PATTERN.test(manageKey)) return json({ status: 'error', message: 'Missing card management key.' }, 400);
      const record = withoutGocTranscript(input.snapshot as EctprRecord);
      const problem = issuableProblem(record);
      if (problem) return json({ status: 'error', message: problem }, 422);
      const patientRef = str(input.patientRef, 120);
      if (!patientRef) return json({ status: 'error', message: 'Missing patient reference.' }, 400);
      // Issuing and the background sync can send the same token together, so a repeat is not an error.
      await rest(key, 'sahara_ed_cards?on_conflict=token', {
        method: 'POST', prefer: 'resolution=ignore-duplicates,return=minimal',
        body: { token, patient_ref: patientRef, record_id: str(record.id, 120), version: Math.max(1, Math.floor(Number(record.version) || 1)), snapshot: record, manage_key_hash: await sha256Hex(manageKey) },
      });
      return json({ status: 'issued', token }, 201);
    }

    if (action === 'revoke') {
      const manageKey = str(input.manageKey, 80);
      const card = await findCard(key, token);
      if (!card) return json({ status: 'unknown' }, 404);
      if (!MANAGE_KEY_PATTERN.test(manageKey) || (await sha256Hex(manageKey)) !== card.manage_key_hash) return json({ status: 'error', message: 'Only the device that issued this card can withdraw it.' }, 403);
      if (card.revoked_at) return json({ status: 'revoked', revokedAt: card.revoked_at });
      const revokedAt = new Date().toISOString();
      await rest(key, `sahara_ed_cards?token=eq.${encodeURIComponent(token)}&revoked_at=is.null`, { method: 'PATCH', body: { revoked_at: revokedAt, revoked_reason: str(input.reason, 200) || null }, prefer: 'return=minimal' });
      return json({ status: 'revoked', revokedAt });
    }

    if (action === 'view' || action === 'break-glass') {
      const card = await findCard(key, token);
      if (!card) return json({ status: 'unknown' }, 404);
      // A withdrawn card shows nothing but "card withdrawn" (tech spec §8).
      if (card.revoked_at) return json({ status: 'revoked', revokedAt: card.revoked_at });
      const ua = request.headers.get('user-agent');
      if (action === 'view') {
        await logAccess(key, { token, kind: 'QR_VIEW', ua });
        return json({ status: 'live', record: quickViewSnapshot(card.snapshot), issuedAt: card.created_at, full: false });
      }
      const reason = str(input.reason, 500);
      if (reason.length < BREAK_GLASS_MIN_REASON) return json({ status: 'error', message: `Type the clinical reason (at least ${BREAK_GLASS_MIN_REASON} characters).` }, 400);
      const primary = card.snapshot.decisionMakers?.primary ?? null;
      const sms = await sendBreakGlassSms({ phone: primary?.phone24h, patientFirstName: (card.snapshot.patient?.name ?? 'the patient').split(/\s+/)[0] || 'the patient', at: new Date() });
      await logAccess(key, { token, kind: 'BREAK_GLASS', reason, ua, sms_status: sms.status });
      const message = sms.sent
        ? `SMS sent to the primary decision-maker (${primary?.name ?? 'named person'}).`
        : sms.status === 'not-configured' ? 'SMS not configured on this server — no message was sent. Phone the decision-maker yourself.'
        : sms.status === 'no-valid-number' ? 'No valid mobile number for the primary decision-maker — no SMS was sent.'
        : 'The SMS could not be sent — phone the decision-maker yourself.';
      return json({ status: 'live', record: card.snapshot, issuedAt: card.created_at, full: true, sms: { sent: sms.sent, message } });
    }

    return json({ status: 'error', message: 'Unknown action.' }, 400);
  } catch (error) {
    if (error instanceof TableMissing) return json({ status: 'not-set-up', message: NOT_SET_UP }, 503);
    if (error instanceof UpstreamError) return json({ status: 'error', message: 'The emergency card server returned an error. Use the offline code printed on the card.' }, 502);
    return json({ status: 'unreachable', message: 'The emergency card server could not be reached. Use the offline code printed on the card.' }, 502);
  }
}
