// Browser side of the ED card (tech spec §6–7): issue a random revocable token for the signed plan, cache the
// snapshot on the issuing device (so the card still opens here offline), and withdraw tokens whenever the plan is
// revoked or superseded. The server copy lives behind /api/ed-card (see that route for the Supabase tables).
import type { EctprRecord } from './ectpr-model.ts';
import { getEctpr, logAudit, subscribeEctpr } from './ectpr-store.ts';
import { quickViewSnapshot, randomUrlToken, withoutGocTranscript, type EdCardViewResult } from './ed-card-snapshot.ts';

export type LocalEdCard = {
  token: string;
  /** Proves this device issued the card; needed to withdraw it. Never leaves this device except to /api/ed-card. */
  manageKey: string;
  patientId: string; recordId: string; version: number;
  issuedAt: string;
  snapshot: EctprRecord;
  revokedAt?: string; revokedReason?: string;
  /** issued = on the server; local-only = server not reached/set up yet; revoke-pending = withdrawn here, server not told yet. */
  server: 'issued' | 'local-only' | 'revoked' | 'revoke-pending';
  serverMessage?: string;
};

const KEY = 'sahara-ed-cards:v1';
const listeners = new Set<() => void>();

// Parsed once per stored value, so screens get the same objects back until something changes.
let cache: { raw: string | null; list: LocalEdCard[] } = { raw: null, list: [] };
function readAll(): LocalEdCard[] {
  try {
    const raw = typeof localStorage === 'undefined' ? null : localStorage.getItem(KEY);
    if (raw === cache.raw) return cache.list;
    const parsed = raw ? JSON.parse(raw) as LocalEdCard[] : [];
    cache = { raw, list: Array.isArray(parsed) ? parsed : [] };
    return cache.list;
  } catch { return []; }
}
function writeAll(list: LocalEdCard[]): void {
  try { localStorage.setItem(KEY, JSON.stringify(list)); } catch { /* storage full or blocked: the server copy still works */ }
  for (const l of [...listeners]) { try { l(); } catch { /* ignore */ } }
}
function update(token: string, patch: Partial<LocalEdCard>): LocalEdCard | null {
  const list = [...readAll()];
  const index = list.findIndex((c) => c.token === token);
  if (index < 0) return null;
  list[index] = { ...list[index], ...patch };
  writeAll(list);
  return list[index];
}

export function subscribeEdCards(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function edCardUrl(token: string, origin = typeof location !== 'undefined' ? location.origin : 'https://sahara.app'): string {
  return `${origin}/ed/${token}`;
}

/** The newest card this device issued for a patient (any state). */
export function localCardFor(patientId: string): LocalEdCard | null {
  const cards = readAll().filter((c) => c.patientId === patientId).sort((a, b) => b.issuedAt.localeCompare(a.issuedAt));
  return cards[0] ?? null;
}
/** What the "download my data" file may say about this patient's cards. The token and the manage key stay out: they are credentials. */
export function localCardsFor(patientId: string): Array<{ issuedAt: string; version: number; server: string; revokedAt?: string }> {
  return readAll().filter((c) => c.patientId === patientId).map(({ issuedAt, version, server, revokedAt }) => ({ issuedAt, version, server, ...(revokedAt ? { revokedAt } : {}) }));
}
export function localCardByToken(token: string): LocalEdCard | null {
  return readAll().find((c) => c.token === token) ?? null;
}

type ApiAnswer = { status: string; message?: string; revokedAt?: string; record?: EctprRecord; issuedAt?: string; full?: boolean; sms?: { sent: boolean; message: string } };

async function callApi(body: Record<string, unknown>, timeoutMs = 12_000): Promise<{ ok: boolean; answer: ApiAnswer | null; network: boolean }> {
  try {
    const response = await fetch('/api/ed-card', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      cache: 'no-store', referrerPolicy: 'no-referrer', signal: AbortSignal.timeout(timeoutMs),
    });
    const answer = await response.json().catch(() => null) as ApiAnswer | null;
    return { ok: response.ok, answer, network: false };
  } catch {
    return { ok: false, answer: null, network: true };
  }
}

function serverProblem(result: { answer: ApiAnswer | null; network: boolean }): string {
  if (result.network) return 'No internet. The card works on this device; it will be uploaded when you are online.';
  return result.answer?.message ?? 'The server did not accept the card.';
}

async function pushIssue(card: LocalEdCard): Promise<LocalEdCard> {
  const result = await callApi({ action: 'issue', token: card.token, manageKey: card.manageKey, patientRef: card.patientId, snapshot: card.snapshot });
  return update(card.token, result.ok ? { server: 'issued', serverMessage: undefined } : { server: 'local-only', serverMessage: serverProblem(result) }) ?? card;
}

async function pushRevoke(card: LocalEdCard): Promise<LocalEdCard> {
  if (card.server === 'local-only') return update(card.token, { server: 'revoked' }) ?? card; // never reached the server
  const result = await callApi({ action: 'revoke', token: card.token, manageKey: card.manageKey, reason: card.revokedReason ?? '' });
  const done = result.ok || result.answer?.status === 'revoked' || result.answer?.status === 'unknown';
  return update(card.token, done ? { server: 'revoked', serverMessage: undefined } : { server: 'revoke-pending', serverMessage: serverProblem(result) }) ?? card;
}

/** Withdraw one card: at once on this device, and on the server as soon as it can be reached. */
export async function revokeEdCard(token: string, reason: string, actor: string): Promise<LocalEdCard | null> {
  const card = localCardByToken(token);
  if (!card || card.revokedAt) return card;
  const marked = update(token, { revokedAt: new Date().toISOString(), revokedReason: reason, server: card.server === 'local-only' ? 'local-only' : 'revoke-pending' });
  if (!marked) return null;
  await logAudit({ actor, action: 'REVOKE', recordType: 'CARD', recordId: card.recordId, patientId: card.patientId, detail: `ED card token withdrawn: ${reason}` }).catch(() => undefined);
  return pushRevoke(marked);
}

/**
 * Issue a new card for the Active, signed plan. Older cards for the same patient are withdrawn (V8: one version in
 * circulation). The card is saved on this device first, so it works even if the server is not reachable.
 */
export async function issueEdCard(record: EctprRecord, actor: string): Promise<LocalEdCard> {
  const card: LocalEdCard = {
    token: randomUrlToken(32), manageKey: randomUrlToken(32),
    patientId: record.patientId, recordId: record.id, version: record.version,
    issuedAt: new Date().toISOString(), snapshot: withoutGocTranscript(record), server: 'local-only',
  };
  const previous = readAll().filter((c) => c.patientId === record.patientId && !c.revokedAt);
  writeAll([...readAll(), card]);
  await logAudit({ actor, action: 'SHARE', recordType: 'CARD', recordId: record.id, patientId: record.patientId, detail: `ED card issued for version ${record.version}` }).catch(() => undefined);
  for (const old of previous) await revokeEdCard(old.token, `Replaced by a new card for version ${record.version}`, actor);
  return pushIssue(card);
}

/** Retry anything not yet on the server, and withdraw cards whose plan is no longer the Active version. */
export async function syncEdCards(actor = 'Sahara (automatic)'): Promise<void> {
  for (const card of readAll()) {
    if (!card.revokedAt) {
      const current = await getEctpr(card.recordId).catch(() => null);
      if (current && current.status !== 'ACTIVE') {
        await revokeEdCard(card.token, current.status === 'REVOKED' ? `Plan cancelled${current.revokedReason ? `: ${current.revokedReason}` : ''}` : 'Plan superseded by a newer version', actor);
        continue;
      }
      if (card.server === 'local-only' && typeof navigator !== 'undefined' && navigator.onLine !== false) await pushIssue(card);
    } else if (card.server === 'revoke-pending') {
      await pushRevoke(card);
    }
  }
}

let syncing: Promise<void> | null = null;
let started = false;
function runSync() {
  if (syncing) return syncing;
  syncing = syncEdCards().catch(() => undefined).finally(() => { syncing = null; });
  return syncing;
}

/** Start watching the ECTPR store (idempotent). Revoking or superseding a plan withdraws its card tokens. */
export function startEdCardSync(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  subscribeEctpr(() => { void runSync(); });
  window.addEventListener('online', () => { void runSync(); });
  void runSync();
}

/** Open a card by token: the server first; if it cannot be reached, this device's cached copy (issuing device only). */
export async function openEdCard(token: string, action: 'view' | 'break-glass', reason?: string): Promise<EdCardViewResult & { offline?: boolean }> {
  const result = await callApi({ action, token, ...(reason ? { reason } : {}) });
  const answer = result.answer;
  if (answer?.status === 'live' && answer.record) {
    return { status: 'live', record: answer.record, issuedAt: answer.issuedAt ?? '', full: Boolean(answer.full), ...(answer.sms ? { sms: answer.sms } : {}) };
  }
  if (answer?.status === 'revoked') return { status: 'revoked', revokedAt: answer.revokedAt ?? '' };
  const local = localCardByToken(token);
  if (local?.revokedAt) return { status: 'revoked', revokedAt: local.revokedAt };
  if (answer?.status === 'unknown' && !local) return { status: 'unknown' };
  if (local && (result.network || answer?.status === 'not-set-up' || answer?.status === 'not-configured' || answer?.status === 'unreachable' || answer?.status === 'unknown')) {
    if (action === 'view') return { status: 'live', record: quickViewSnapshot(local.snapshot), issuedAt: local.issuedAt, full: false, offline: true };
    return { status: 'live', record: local.snapshot, issuedAt: local.issuedAt, full: true, offline: true, sms: { sent: false, message: 'Offline — no SMS was sent. Phone the decision-maker yourself.' } };
  }
  if (answer?.status === 'error' && action === 'break-glass') return { status: 'error', message: answer.message ?? 'Break-glass failed.' };
  const status = result.network ? 'unreachable' : (answer?.status as 'not-set-up' | 'not-configured' | 'unreachable' | 'error' | undefined) ?? 'error';
  return { status: ['not-set-up', 'not-configured', 'unreachable'].includes(status) ? status : 'error', message: answer?.message ?? (result.network ? 'No internet connection.' : 'The card could not be opened.') } as EdCardViewResult;
}
