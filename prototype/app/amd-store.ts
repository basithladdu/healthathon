// Sahara living will (AMD) local-first store. Versions per patient, the signed scan as a local file,
// and every CREATE / EDIT / PRINT / UPLOAD / SHARE / REVOKE written to the shared append-only audit log.
// Persists through care-local-store (IndexedDB, localStorage fallback); memory in tests and SSR.

import { readCareValue, writeCareValue } from './care-local-store.ts';
import { logAudit } from './ectpr-store.ts';
import type { AmdContent, AmdCopy, AmdCustodian, AmdExecution, AmdStatusSummary, AmdVersion, AuditEvent } from './ectpr-model.ts';
import {
  amdId, amdStatusSummary, markExecuted, markGenerated, revokeAmd, sha256Hex, startAmdVersion, updateAbha, updateAmdDraft,
  updateCopy, updateCustodian, withoutUnsignedAmdVersions, type AmdResult,
} from './amd-rules.ts';

export type AmdStorage = { read<T>(key: string): Promise<T | undefined>; write<T>(key: string, value: T): Promise<void> };

export function memoryAmdStorage(): AmdStorage {
  const map = new Map<string, unknown>();
  return {
    async read<T>(key: string) { return map.get(key) as T | undefined; },
    async write<T>(key: string, value: T) { map.set(key, value); },
  };
}

const browserStorage: AmdStorage = { read: readCareValue, write: writeCareValue };
const hasBrowserStorage = () => typeof indexedDB !== 'undefined' || typeof localStorage !== 'undefined';
let storage: AmdStorage | null = null;
let auditSink: (event: Omit<AuditEvent, 'id' | 'at'>) => Promise<unknown> = (event) => logAudit(event);
let queue: Promise<unknown> = Promise.resolve();

function activeStorage(): AmdStorage {
  if (!storage) storage = hasBrowserStorage() ? browserStorage : memoryAmdStorage();
  return storage;
}

/** Swap the backend and the audit sink (tests). */
export function setAmdStorage(backend: AmdStorage, audit?: (event: Omit<AuditEvent, 'id' | 'at'>) => Promise<unknown>): void {
  storage = backend;
  if (audit) auditSink = audit;
  queue = Promise.resolve();
  cache.clear();
}

const versionsKey = (patientId: string) => `sahara-amd:versions:${patientId}`;
const scanKeyPath = (scanKey: string) => `sahara-amd:scan:${scanKey}`;

function serial<T>(task: () => Promise<T>): Promise<T> {
  const next = queue.catch(() => {}).then(task);
  queue = next;
  return next;
}

// Subscriptions + a small cache so the ED view can read the status synchronously after the first load.
type Listener = () => void;
const listeners = new Set<Listener>();
const cache = new Map<string, AmdVersion[]>();
export function subscribeAmd(listener: Listener): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
function notify() { for (const listener of listeners) listener(); }

async function load(patientId: string): Promise<AmdVersion[]> {
  const saved = (await activeStorage().read<AmdVersion[]>(versionsKey(patientId))) ?? [];
  cache.set(patientId, saved);
  return saved;
}

async function save(patientId: string, versions: AmdVersion[]) {
  await activeStorage().write(versionsKey(patientId), versions);
  cache.set(patientId, versions);
}

async function audit(patientId: string, actor: string, action: AuditEvent['action'], recordId: string, detail?: string) {
  try { await auditSink({ actor, action, recordType: 'AMD', recordId, patientId, detail }); }
  catch (error) { console.warn('AMD audit entry could not be written.', error); }
}

/** Apply a pure lifecycle step, persist it, then write the audit entry. */
function mutate(patientId: string, step: (versions: AmdVersion[]) => AmdResult, log?: (result: Extract<AmdResult, { ok: true }>) => [AuditEvent['action'], string?] | null, actor = 'unknown'): Promise<AmdResult> {
  return serial(async () => {
    const versions = await load(patientId);
    const result = step(versions);
    if (!result.ok) return result;
    await save(patientId, result.versions);
    const entry = log?.(result);
    if (entry) await audit(patientId, actor, entry[0], result.version.id, entry[1]);
    notify();
    return result;
  });
}

// ---------------------------------------------------------------------------
// Public API

export function listAmdVersions(patientId: string): Promise<AmdVersion[]> {
  return serial(() => load(patientId));
}

/** First draft, or a new version copied from the latest (L6: edits after printing or signing make a new version). */
export function startAmdDraft(patientId: string, actor: string, executantName = '', now = new Date()): Promise<AmdResult> {
  let created = false;
  return mutate(patientId, (versions) => {
    const result = startAmdVersion(versions, patientId, actor, now, executantName);
    created = result.ok && !versions.some((v) => v.id === result.version.id);
    return result;
  }, (result) => (created ? ['CREATE', `Version ${result.version.version} draft`] : null), actor);
}

export function saveAmdDraft(patientId: string, versionId: string, content: AmdContent, actor: string, note = 'Draft saved', now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => updateAmdDraft(versions, versionId, content, now), () => ['EDIT', note], actor);
}

export function recordAmdGenerated(patientId: string, versionId: string, info: { pdfSha256: string; contentSha256: string; lang: 'en' | 'hi' | 'te' }, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => markGenerated(versions, versionId, info, now), (result) => ['PRINT', `Vidhi-format PDF v${result.version.version} (${info.lang}) SHA-256 ${info.pdfSha256}`], actor);
}

/** Keep the signed scan (or a written cancellation) on this device and return its SHA-256. */
export async function saveAmdScan(patientId: string, file: File, actor: string, purpose: 'EXECUTED_AMD' | 'REVOCATION' = 'EXECUTED_AMD'): Promise<{ scanKey: string; scanSha256: string; scanName: string; scanType: string; scanUploadedAt: string }> {
  const scanSha256 = await sha256Hex(await file.arrayBuffer());
  const scanKey = amdId('scan');
  await serial(() => activeStorage().write(scanKeyPath(scanKey), file));
  await audit(patientId, actor, 'UPLOAD', scanKey, `${purpose === 'REVOCATION' ? 'Written cancellation' : 'Signed, attested AMD'} scan "${file.name}" SHA-256 ${scanSha256}`);
  return { scanKey, scanSha256, scanName: file.name, scanType: file.type, scanUploadedAt: new Date().toISOString() };
}

export function readAmdScan(scanKey: string): Promise<File | undefined> {
  return serial(() => activeStorage().read<File>(scanKeyPath(scanKey)));
}

export function executeAmd(patientId: string, versionId: string, execution: AmdExecution, custodian: AmdCustodian | null, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => markExecuted(versions, versionId, execution, custodian, now), (result) => ['UPLOAD', `Executed v${result.version.version}: signed ${execution.signedOn}, witnesses recorded, ${execution.attestor.kind === 'NOTARY' ? 'notary' : 'gazetted officer'} ${execution.attestor.name}; custodian ${custodian?.status.toLowerCase()}`], actor);
}

export function updateAmdCopy(patientId: string, versionId: string, copyId: string, patch: Partial<Pick<AmdCopy, 'consent' | 'given' | 'givenOn' | 'method'>>, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => updateCopy(versions, versionId, copyId, patch, now), (result) => {
    const copy = result.version.copies.find((c) => c.id === copyId);
    if (!copy) return null;
    if ('given' in patch) return ['SHARE', `Copy ${copy.given ? 'given to' : 'unmarked for'} ${copy.kind.toLowerCase()} ${copy.name}${copy.method ? ` (${copy.method.toLowerCase()})` : ''}`];
    return ['EDIT', `Consent to share with ${copy.kind.toLowerCase()} ${copy.name}: ${copy.consent ? 'given' : 'withdrawn'}`];
  }, actor);
}

export function updateAmdCustodian(patientId: string, versionId: string, custodian: AmdCustodian, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => updateCustodian(versions, versionId, custodian, now), () => ['EDIT', `Custodian ${custodian.status.toLowerCase()}${custodian.officer ? `: ${custodian.officer}` : ''}`], actor);
}

export function updateAmdAbha(patientId: string, versionId: string, abha: { address: string; consent: boolean }, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => updateAbha(versions, versionId, abha, now), () => ['SHARE', abha.address.trim() ? 'ABHA link recorded' : 'ABHA link removed'], actor);
}

export function revokeAmdRecord(patientId: string, revocation: { revokedOn: string; writtenRevocationSigned: boolean; reason: string; scanKey?: string; scanSha256?: string }, actor: string, now = new Date()): Promise<AmdResult> {
  return mutate(patientId, (versions) => revokeAmd(versions, revocation, now), () => ['REVOKE', `Written cancellation dated ${revocation.revokedOn}${revocation.reason.trim() ? `: ${revocation.reason.trim()}` : ''}`], actor);
}

/** "Delete my local draft": removes unsigned versions from this device and logs that it happened. Signed versions and the audit log stay. */
export function deleteAmdDrafts(patientId: string, actor: string): Promise<{ deleted: number; kept: number }> {
  return serial(async () => {
    const { kept, removed } = withoutUnsignedAmdVersions(await load(patientId));
    if (removed.length) {
      await save(patientId, kept);
      for (const v of removed) await audit(patientId, actor, 'DELETE', v.id, `Unsigned living will version ${v.version} deleted from this device at the person's request`);
      notify();
    }
    return { deleted: removed.length, kept: kept.length };
  });
}

/**
 * For the ED Quick View: { status, executedOn, version, attestor } or null when no AMD is recorded.
 * Only status 'EXECUTED' is a valid, current AMD; always show AMD_ED_CAVEAT (amd-rules.ts) beside it.
 */
export async function getCurrentAmdStatus(patientId: string): Promise<AmdStatusSummary | null> {
  return amdStatusSummary(await listAmdVersions(patientId));
}

/** Same as getCurrentAmdStatus from the in-memory cache; undefined until the patient's AMD has been loaded once. */
export function getCachedAmdStatus(patientId: string): AmdStatusSummary | null | undefined {
  const versions = cache.get(patientId);
  return versions ? amdStatusSummary(versions) : undefined;
}
