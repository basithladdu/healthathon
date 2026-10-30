// Sahara ECTPR local-first store: versioned records, one ACTIVE version per patient, signed records never edited,
// append-only audit log, and the pilot entities (episode log, outcome record, incident report; clinical spec §8).
// Persists through care-local-store (IndexedDB, localStorage fallback). Falls back to memory where neither exists (tests, SSR).

import { readCareValue, writeCareValue } from './care-local-store.ts';
import type { AuditEvent, EctprCopyKind, EctprDistribution, EctprRecord, EpisodeLog, IncidentReport, OutcomeRecord, RespCeiling, RuleIssue, Signature } from './ectpr-model.ts';
import type { EctprQuickView } from './ectpr';
import { sha256Hex } from './ed-card-snapshot.ts';
import { blocksActivation, blocksSave, consultantSignature, isoDay, newId, nextReviewDue, validateEctpr, emptyEctpr, TRANSFER_REASON_LABELS, type ReviewTrigger } from './ectpr-rules.ts';

// ---------------------------------------------------------------------------
// Storage backend

export type EctprStorage = { read<T>(key: string): Promise<T | undefined>; write<T>(key: string, value: T): Promise<void> };

export function memoryEctprStorage(): EctprStorage {
  const map = new Map<string, string>();
  return {
    async read<T>(key: string) { const v = map.get(key); return v === undefined ? undefined : JSON.parse(v) as T; },
    async write<T>(key: string, value: T) { map.set(key, JSON.stringify(value)); },
  };
}

const browserStorage: EctprStorage = { read: readCareValue, write: writeCareValue };
const hasBrowserStorage = () => typeof indexedDB !== 'undefined' || typeof localStorage !== 'undefined';

let storage: EctprStorage | null = null;
let seedViews: Record<string, EctprQuickView> | null | undefined; // undefined = load from ./ectpr
let queue: Promise<unknown> = Promise.resolve();

function activeStorage(): EctprStorage {
  if (!storage) storage = hasBrowserStorage() ? browserStorage : memoryEctprStorage();
  return storage;
}

/** Swap the backend (tests). `seed`: quick views to seed from; null = no seed; omitted = the samples in ectpr.tsx. */
export function setEctprStorage(backend: EctprStorage, seed?: Record<string, EctprQuickView> | null): void {
  storage = backend;
  seedViews = seed;
  queue = Promise.resolve();
}

const KEYS = {
  records: 'sahara-ectpr:records',
  audit: 'sahara-ectpr:audit',
  episodes: 'sahara-ectpr:episodes',
  outcomes: 'sahara-ectpr:outcomes',
  incidents: 'sahara-ectpr:incidents',
  distribution: 'sahara-ectpr:distribution',
} as const;
const scanPath = (scanKey: string) => `sahara-ectpr:scan:${scanKey}`;

/** Run read-modify-write steps one at a time so two saves in one tab never overwrite each other. */
function serial<T>(task: () => Promise<T>): Promise<T> {
  const next = queue.catch(() => {}).then(task);
  queue = next;
  return next;
}

// ---------------------------------------------------------------------------
// Subscriptions

type Listener = () => void;
const listeners = new Set<Listener>();
let channel: BroadcastChannel | null = null;

function notify(fromOtherTab = false): void {
  for (const listener of [...listeners]) {
    try { listener(); } catch (error) { console.warn('ECTPR listener failed.', error); }
  }
  if (!fromOtherTab && channel) channel.postMessage('changed');
}

/** Re-render hook for UIs. Returns an unsubscribe function. Also fires for changes made in other tabs. */
export function subscribeEctpr(listener: Listener): () => void {
  listeners.add(listener);
  if (!channel && typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel('sahara-ectpr');
    channel.onmessage = () => notify(true);
    (channel as BroadcastChannel & { unref?: () => void }).unref?.(); // Node: never keep the process alive.
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && channel) { channel.close(); channel = null; }
  };
}

// ---------------------------------------------------------------------------
// Seed from the sample quick views in ectpr.tsx

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

/** '21 Aug 2026' → '2026-08-21'. Returns '' when it cannot be read. */
function quickViewDay(value: string | null): string {
  const match = value ? /^(\d{1,2})\s+([A-Za-z]{3})\w*\s+(\d{4})$/.exec(value.trim()) : null;
  if (!match) return '';
  const month = MONTHS.indexOf(match[2].toLowerCase());
  return month < 0 ? '' : isoDay(new Date(Number(match[3]), month, Number(match[1])));
}

const CEILING_FROM_VIEW: Record<NonNullable<EctprQuickView['breathingCeiling']>, RespCeiling> = {
  'comfort-only': 0, mask: 1, 'non-rebreather': 2, hfnc: 3, niv: 4, 'intubation-trial': 5, 'intubation-no-limit': 5,
};

/** Convert one sample quick view into an ACTIVE, consultant-signed record (synthetic demo data). */
export function ectprFromQuickView(view: EctprQuickView): EctprRecord {
  const signedDay = quickViewDay(view.signedOn) || isoDay(new Date());
  const signedAt = new Date(`${signedDay}T10:00:00`).toISOString();
  const consultant = view.treatingConsultant ?? view.signedBy ?? 'Treating consultant';
  const record = emptyEctpr(view.hospitalId, { name: view.patientName, uhid: view.hospitalId, languagePref: 'en' }, consultant, new Date(signedAt));
  record.id = `ectpr-seed-${view.hospitalId}-v1`;
  const version = Number(/\d+/.exec(view.recordVersion)?.[0] ?? 1);
  record.version = version > 0 ? version : 1;
  record.status = 'ACTIVE';
  record.careTeam.oncologist = consultant;
  record.careTeam.palliativePhysician = view.palliativeContact ?? '';
  record.clinical.diagnosis = view.diagnosis;
  record.reviewDue = quickViewDay(view.reviewDueBy);
  if (view.decisionMaker) {
    record.decisionMakers.primary = { name: view.decisionMaker.name, relation: view.decisionMaker.relationship, phone24h: '', idType: null, idLast4: null, authorityBasis: 'NAMED_BY_PATIENT' };
  }
  record.capacity = { status: 'HAS', assessor: consultant, assessedOn: signedDay, basis: 'Understands, retains, weighs and communicates the decision.', reassessOn: '' };
  record.existingDirective = { kind: 'NONE' };
  const c = record.choices;
  c.goalOfCare = view.goal === 'prioritise-life' ? 'A_LIFE' : view.goal === 'balanced' ? 'B_CEILING' : view.goal === 'prioritise-comfort' ? 'C_COMFORT' : null;
  c.hospitalTransfer = view.hospitalTransfer === 'any-deterioration' ? { kind: 'ANY' }
    : view.hospitalTransfer === 'listed-reasons-only' ? { kind: 'ONLY_FOR', reasons: Object.keys(TRANSFER_REASON_LABELS) as (keyof typeof TRANSFER_REASON_LABELS)[] }
    : view.hospitalTransfer === 'no' ? { kind: 'NO' } : null;
  c.cpr = view.cpr === 'attempt' ? 'ATTEMPT' : view.cpr === 'dnacpr' ? 'DNACPR' : null;
  if (c.cpr === 'DNACPR') {
    c.dnacprReason = 'BURDEN_OUTWEIGHS_BENEFIT';
    c.dnacprDiscussedWith = view.decisionMaker ? `${view.decisionMaker.name} (${view.decisionMaker.relationship})` : 'Patient';
    c.dnacprDecidedBy = { name: consultant, registrationNo: 'DEMO-REG-0001', decidedAt: signedAt };
  }
  c.respCeiling = view.breathingCeiling ? CEILING_FROM_VIEW[view.breathingCeiling] : null;
  c.icu = view.icu === 'yes' ? { kind: 'YES' } : view.icu === 'no' ? { kind: 'NO' }
    : view.icu === 'time-limited-trial' ? { kind: 'TRIAL', trial: { durationValue: view.icuTrialDays ?? 3, durationUnit: 'DAYS', reviewAt: signedAt, reviewers: [consultant] } } : null;
  const anyChoice = c.goalOfCare || c.hospitalTransfer || c.cpr || c.respCeiling !== null || c.icu;
  record.prognosticUnderstanding = anyChoice ? { confirmed: true, confirmedBy: consultant, confirmedOn: signedDay } : record.prognosticUnderstanding;
  record.values.patientKnows = anyChoice ? true : null;
  record.values.financialConcernDiscussed = anyChoice ? true : null;
  record.signatures = [{ role: 'TREATING_CONSULTANT', name: view.signedBy ?? consultant, registrationNo: 'DEMO-REG-0001', signedAt }];
  return record;
}

async function loadSeed(): Promise<EctprRecord[]> {
  let views = seedViews;
  if (views === undefined) {
    try { views = (await import('./ectpr')).ectprQuickViews; } catch { views = null; }
  }
  return views ? Object.values(views).map(ectprFromQuickView) : [];
}

// ---------------------------------------------------------------------------
// Record access

async function readRecords(): Promise<EctprRecord[]> {
  const saved = await activeStorage().read<EctprRecord[]>(KEYS.records);
  if (saved !== undefined) return saved;
  const seeded = await loadSeed();
  await activeStorage().write(KEYS.records, seeded);
  return seeded;
}

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

/** All versions for one patient, newest first. */
export async function listEctprVersions(patientId: string): Promise<EctprRecord[]> {
  const records = await serial(readRecords);
  return records.filter((r) => r.patientId === patientId).sort((a, b) => b.version - a.version).map(clone);
}

/** The one ACTIVE version, or null. (It may still be out of date — use edInterpretation.) */
export async function getActiveEctpr(patientId: string): Promise<EctprRecord | null> {
  const records = await serial(readRecords);
  const active = records.find((r) => r.patientId === patientId && r.status === 'ACTIVE');
  return active ? clone(active) : null;
}

export async function getEctpr(recordId: string): Promise<EctprRecord | null> {
  const records = await serial(readRecords);
  const found = records.find((r) => r.id === recordId);
  return found ? clone(found) : null;
}

export type EctprResult = { ok: boolean; record: EctprRecord | null; issues: RuleIssue[]; error?: string };

/** Save a DRAFT. Refused when a save-blocking rule fails, or when the stored version is already signed (signed records are never edited). */
export function saveEctprDraft(record: EctprRecord, now: Date = new Date()): Promise<EctprResult> {
  return serial(async () => {
    const issues = validateEctpr(record, now);
    if (record.status !== 'DRAFT') return { ok: false, record: null, issues, error: 'Only a draft can be saved. A signed plan is never edited — start a review instead.' };
    const records = await readRecords();
    const index = records.findIndex((r) => r.id === record.id);
    if (index >= 0 && records[index].status !== 'DRAFT') return { ok: false, record: null, issues, error: 'This version is already signed. Start a review to make a new version.' };
    if (blocksSave(issues)) return { ok: false, record: null, issues, error: 'Fix the items marked as blocking before saving.' };
    // Signatures are only added through signEctpr; keep the stored ones, ignore any passed in.
    const saved: EctprRecord = { ...clone(record), signatures: index >= 0 ? records[index].signatures : [], updatedAt: now.toISOString() };
    if (index >= 0) records[index] = saved; else records.push(saved);
    await activeStorage().write(KEYS.records, records);
    await appendAudit({ actor: record.createdBy, action: index >= 0 ? 'EDIT' : 'CREATE', recordType: 'ECTPR', recordId: record.id, patientId: record.patientId, detail: `Draft version ${record.version} saved` }, now);
    notify();
    return { ok: true, record: clone(saved), issues };
  });
}

/**
 * Add a signature to a DRAFT. A treating-consultant signature with a registration number activates the record (V7)
 * only when no rule blocks activation; the previous ACTIVE version becomes SUPERSEDED (one active per patient).
 * Other signatures (co-signers, witnesses, patient) are added while it stays DRAFT.
 */
export function signEctpr(recordId: string, signature: Signature, now: Date = new Date()): Promise<EctprResult> {
  return serial(async () => {
    const records = await readRecords();
    const index = records.findIndex((r) => r.id === recordId);
    if (index < 0) return { ok: false, record: null, issues: [], error: 'Plan not found.' };
    const current = records[index];
    if (current.status !== 'DRAFT') return { ok: false, record: clone(current), issues: [], error: 'This plan is already signed or closed. It cannot be changed.' };
    if (!signature.name.trim()) return { ok: false, record: clone(current), issues: [], error: 'A signature needs a name.' };
    const signed: EctprRecord = { ...clone(current), signatures: [...current.signatures, { ...signature, signedAt: signature.signedAt || now.toISOString() }], updatedAt: now.toISOString() };
    const issues = validateEctpr(signed, now);
    const isConsultant = signature.role === 'TREATING_CONSULTANT';
    if (isConsultant && !consultantSignature(signed)) {
      return { ok: false, record: clone(current), issues, error: 'The treating consultant must sign with their registration number.' };
    }
    if (isConsultant && blocksActivation(issues)) {
      return { ok: false, record: clone(current), issues, error: 'This plan cannot be made active yet. Fix the items listed first.' };
    }
    let superseded: EctprRecord | undefined;
    if (isConsultant) {
      signed.status = 'ACTIVE';
      for (let i = 0; i < records.length; i++) {
        if (i !== index && records[i].patientId === signed.patientId && records[i].status === 'ACTIVE') {
          records[i] = { ...records[i], status: 'SUPERSEDED', updatedAt: now.toISOString() };
          superseded = records[i];
        }
      }
    }
    records[index] = signed;
    await activeStorage().write(KEYS.records, records);
    await appendAudit({ actor: signature.name, action: 'SIGN', recordType: 'ECTPR', recordId, patientId: signed.patientId, detail: `${signature.role} signed version ${signed.version}${isConsultant ? ' — now active' : ''}` }, now);
    if (superseded) {
      await appendAudit({ actor: signature.name, action: 'REVOKE', recordType: 'CARD', recordId: superseded.id, patientId: signed.patientId, detail: `Version ${superseded.version} superseded by version ${signed.version}; its QR card tokens are withdrawn` }, now);
    }
    notify();
    return { ok: true, record: clone(signed), issues };
  });
}

/**
 * Start a review: a prefilled DRAFT copied from the active (or latest) version, version + 1, linked by `supersedes`.
 * If a review draft already exists for that version, it is returned instead of making a second one. Null when the patient has no record.
 */
export function startReview(patientId: string, trigger: ReviewTrigger, actor: string, now: Date = new Date()): Promise<EctprRecord | null> {
  return serial(async () => {
    const records = await readRecords();
    const versions = records.filter((r) => r.patientId === patientId).sort((a, b) => b.version - a.version);
    const base = versions.find((r) => r.status === 'ACTIVE') ?? versions.find((r) => r.status !== 'DRAFT') ?? null;
    if (!base) return null;
    const existing = versions.find((r) => r.status === 'DRAFT' && r.supersedes === base.id);
    if (existing) return clone(existing);
    const at = now.toISOString();
    const draft: EctprRecord = {
      ...clone(base),
      id: newId('ectpr'),
      version: (versions[0]?.version ?? 0) + 1,
      status: 'DRAFT',
      createdAt: at, updatedAt: at, createdBy: actor,
      createdInEd: false,
      signatures: [],
      supersedes: base.id,
      reviewDue: nextReviewDue(now),
      revokedAt: undefined, revokedReason: undefined, revokedBy: undefined,
    };
    draft.choices.goalAConfirmed = false;
    records.push(draft);
    await activeStorage().write(KEYS.records, records);
    await appendAudit({ actor, action: 'REVIEW', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `Review started (${trigger}) from version ${base.version}` }, now);
    notify();
    return clone(draft);
  });
}

/** Signed cancellation. The record becomes REVOKED, its QR card shows "card withdrawn", and the ED gets standard care. */
export function revokeEctpr(recordId: string, reason: string, actor: string, now: Date = new Date()): Promise<EctprResult> {
  return serial(async () => {
    if (!reason.trim() || !actor.trim()) return { ok: false, record: null, issues: [], error: 'A cancellation needs a reason and the name of the person cancelling.' };
    const records = await readRecords();
    const index = records.findIndex((r) => r.id === recordId);
    if (index < 0) return { ok: false, record: null, issues: [], error: 'Plan not found.' };
    const current = records[index];
    if (current.status === 'REVOKED' || current.status === 'SUPERSEDED') return { ok: false, record: clone(current), issues: [], error: 'This version is no longer current.' };
    const revoked: EctprRecord = { ...current, status: 'REVOKED', revokedAt: now.toISOString(), revokedReason: reason.trim(), revokedBy: actor.trim(), updatedAt: now.toISOString() };
    records[index] = revoked;
    await activeStorage().write(KEYS.records, records);
    await appendAudit({ actor, action: 'REVOKE', recordType: 'ECTPR', recordId, patientId: current.patientId, detail: `Cancelled: ${reason.trim()}. QR card tokens withdrawn.` }, now);
    notify();
    return { ok: true, record: clone(revoked), issues: [] };
  });
}

/**
 * "Delete my local draft" (DPDP notice). Removes unsigned drafts only. A draft that already carries a signature, every
 * signed version and the audit log stay. The deletion itself is logged, without the deleted content.
 */
export function deleteEctprDrafts(patientId: string, actor: string, now: Date = new Date()): Promise<{ deleted: number; kept: number }> {
  return serial(async () => {
    const records = await readRecords();
    const mine = records.filter((r) => r.patientId === patientId);
    const removable = mine.filter((r) => r.status === 'DRAFT' && r.signatures.length === 0);
    if (removable.length) {
      const gone = new Set(removable.map((r) => r.id));
      await activeStorage().write(KEYS.records, records.filter((r) => !gone.has(r.id)));
      for (const r of removable) await appendAudit({ actor, action: 'DELETE', recordType: 'ECTPR', recordId: r.id, patientId, detail: `Unsigned draft version ${r.version} deleted from this device at the person's request` }, now);
      notify();
    }
    return { deleted: removable.length, kept: mine.length - removable.length };
  });
}

// ---------------------------------------------------------------------------
// Signed paper scan and copy checklist for a signed plan (tech spec §8 "Should")

async function readDistribution(): Promise<EctprDistribution[]> {
  return (await activeStorage().read<EctprDistribution[]>(KEYS.distribution)) ?? [];
}

const emptyDistribution = (record: EctprRecord): EctprDistribution => ({ recordId: record.id, patientId: record.patientId, scan: null, copies: [] });

export async function getEctprDistribution(record: EctprRecord): Promise<EctprDistribution> {
  const list = await serial(readDistribution);
  return clone(list.find((d) => d.recordId === record.id) ?? emptyDistribution(record));
}

export async function listEctprDistribution(patientId: string): Promise<EctprDistribution[]> {
  return (await serial(readDistribution)).filter((d) => d.patientId === patientId).map(clone);
}

function writeDistribution(record: EctprRecord, change: (current: EctprDistribution) => EctprDistribution): Promise<EctprDistribution> {
  return serial(async () => {
    const list = await readDistribution();
    const index = list.findIndex((d) => d.recordId === record.id);
    const next = change(index >= 0 ? list[index] : emptyDistribution(record));
    if (index >= 0) list[index] = next; else list.push(next);
    await activeStorage().write(KEYS.distribution, list);
    return next;
  });
}

/** Keep the signed paper's scan on this device with its SHA-256. Only for a signed version; each upload is logged. */
export async function saveEctprScan(record: EctprRecord, file: { name: string; type: string; arrayBuffer(): Promise<ArrayBuffer> }, actor: string, now: Date = new Date()): Promise<EctprDistribution> {
  if (record.status === 'DRAFT') throw new Error('A scan can only be added to a signed plan.');
  const scanSha256 = await sha256Hex(new Uint8Array(await file.arrayBuffer()));
  const scanKey = newId('scan');
  await serial(() => activeStorage().write(scanPath(scanKey), file));
  const saved = await writeDistribution(record, (current) => ({ ...current, scan: { scanKey, scanName: file.name, scanType: file.type, scanSha256, scanUploadedAt: now.toISOString(), uploadedBy: actor } }));
  await logAudit({ actor, action: 'UPLOAD', recordType: 'ECTPR', recordId: record.id, patientId: record.patientId, detail: `Signed paper scan "${file.name}" for version ${record.version} SHA-256 ${scanSha256}` });
  return clone(saved);
}

export function readEctprScan(scanKey: string): Promise<File | undefined> {
  return serial(() => activeStorage().read<File>(scanPath(scanKey)));
}

export async function setEctprCopy(record: EctprRecord, kind: EctprCopyKind, given: boolean, actor: string, now: Date = new Date()): Promise<EctprDistribution> {
  const givenOn = given ? isoDay(now) : '';
  const saved = await writeDistribution(record, (current) => ({ ...current, copies: [...current.copies.filter((c) => c.kind !== kind), { kind, given, givenOn }] }));
  await logAudit({ actor, action: 'SHARE', recordType: 'CARD', recordId: record.id, patientId: record.patientId, detail: `Copy of version ${record.version} ${given ? 'given' : 'unmarked'}: ${kind.toLowerCase().replace(/_/g, ' ')}` });
  return clone(saved);
}

// ---------------------------------------------------------------------------
// Append-only audit log

export type AuditInput = Omit<AuditEvent, 'id' | 'at'> & { at?: string };

async function appendAudit(input: AuditInput, now: Date = new Date()): Promise<AuditEvent> {
  const event: AuditEvent = { ...input, id: newId('audit'), at: input.at ?? now.toISOString() };
  const log = (await activeStorage().read<AuditEvent[]>(KEYS.audit)) ?? [];
  await activeStorage().write(KEYS.audit, [...log, event]);
  return event;
}

/** Append one entry. Entries are never changed or deleted. */
export function logAudit(input: AuditInput): Promise<AuditEvent> {
  return serial(async () => {
    const event = await appendAudit(input);
    notify();
    return Object.freeze({ ...event });
  });
}

/** Audit entries, oldest first, optionally for one patient. Returns copies. */
export async function listAudit(patientId?: string): Promise<AuditEvent[]> {
  const log = await serial(async () => (await activeStorage().read<AuditEvent[]>(KEYS.audit)) ?? []);
  return log.filter((e) => !patientId || e.patientId === patientId).map((e) => Object.freeze({ ...e }));
}

// ---------------------------------------------------------------------------
// Pilot entities (clinical spec §8)

type Stamped = { id: string; patientId: string };

function saveEntity<T extends Stamped>(key: string, entity: Omit<T, 'id'> & { id?: string }): Promise<T> {
  return serial(async () => {
    const list = (await activeStorage().read<T[]>(key)) ?? [];
    const saved = { ...entity, id: entity.id || newId(key.split(':')[1]) } as T;
    const index = list.findIndex((e) => e.id === saved.id);
    if (index >= 0) list[index] = saved; else list.push(saved);
    await activeStorage().write(key, list);
    notify();
    return clone(saved);
  });
}

async function listEntity<T extends Stamped>(key: string, patientId?: string): Promise<T[]> {
  const list = await serial(async () => (await activeStorage().read<T[]>(key)) ?? []);
  return list.filter((e) => !patientId || e.patientId === patientId).map(clone);
}

export const saveEpisodeLog = (episode: Omit<EpisodeLog, 'id'> & { id?: string }) => saveEntity<EpisodeLog>(KEYS.episodes, episode);
export const listEpisodeLogs = (patientId?: string) => listEntity<EpisodeLog>(KEYS.episodes, patientId);
export const saveOutcomeRecord = (outcome: Omit<OutcomeRecord, 'id'> & { id?: string }) => saveEntity<OutcomeRecord>(KEYS.outcomes, outcome);
export const listOutcomeRecords = (patientId?: string) => listEntity<OutcomeRecord>(KEYS.outcomes, patientId);
export const saveIncidentReport = (incident: Omit<IncidentReport, 'id'> & { id?: string }) => saveEntity<IncidentReport>(KEYS.incidents, incident);
export const listIncidentReports = (patientId?: string) => listEntity<IncidentReport>(KEYS.incidents, patientId);

/** Minutes from ED arrival to first record access (pilot target: within 10). Null when not accessed. */
export function retrievalMinutes(episode: EpisodeLog): number | null {
  if (!episode.firstRecordAccessAt) return null;
  const ms = new Date(episode.firstRecordAccessAt).getTime() - new Date(episode.edArrivalAt).getTime();
  return Number.isNaN(ms) ? null : Math.round(ms / 60000);
}

/** Concordance: delivered care stayed within the recorded ceiling and CPR decision. Null when nothing was recorded to compare. */
export function episodeConcordant(episode: EpisodeLog, record: EctprRecord): boolean | null {
  const c = record.choices;
  const checks: boolean[] = [];
  if (c.respCeiling !== null && episode.highestSupportDelivered !== null) checks.push(episode.highestSupportDelivered <= c.respCeiling);
  if (c.cpr === 'DNACPR' && episode.cprGiven !== null) checks.push(!episode.cprGiven);
  if (c.icu?.kind === 'NO' && episode.icuAdmitted !== null) checks.push(!episode.icuAdmitted);
  return checks.length ? checks.every(Boolean) : null;
}
