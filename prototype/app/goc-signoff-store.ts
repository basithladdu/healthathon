// Where goals-of-care sign-offs are kept: on this device only, the same way as the care plan (ectpr-store.ts).
// care-local-store writes to IndexedDB (database 'saanthvana-family-care', object store 'care'), or to localStorage
// under the 'saanthvana-care:' prefix when IndexedDB cannot be opened. Key: `sahara-goc-signoff:<noteId>`, value
// { records: GocSignOffRecord[] } oldest first, so INVALIDATED records stay as history beside the current one.
// Nothing here is sent to a server. Falls back to memory where neither store exists (tests, SSR).

import { readCareValue, writeCareValue } from './care-local-store.ts';
import { isGocSignOffRecord, type GocSignOffRecord } from './goc-signoff.ts';

export type GocSignOffStorage = { read<T>(key: string): Promise<T | undefined>; write<T>(key: string, value: T): Promise<void> };

export function memoryGocSignOffStorage(): GocSignOffStorage {
  const map = new Map<string, string>();
  return {
    async read<T>(key: string) { const v = map.get(key); return v === undefined ? undefined : JSON.parse(v) as T; },
    async write<T>(key: string, value: T) { map.set(key, JSON.stringify(value)); },
  };
}

let storage: GocSignOffStorage | null = null;
let queue: Promise<unknown> = Promise.resolve();
const hasBrowserStorage = () => typeof indexedDB !== 'undefined' || typeof localStorage !== 'undefined';
function active(): GocSignOffStorage {
  if (!storage) storage = hasBrowserStorage() ? { read: readCareValue, write: writeCareValue } : memoryGocSignOffStorage();
  return storage;
}

/** Swap the backend (tests). */
export function setGocSignOffStorage(backend: GocSignOffStorage): void { storage = backend; queue = Promise.resolve(); }

export const gocSignOffKey = (noteId: string) => `sahara-goc-signoff:${noteId}`;

/** All sign-offs for a note, oldest first. The last one is the current one. */
export async function loadGocSignOffs(noteId: string): Promise<GocSignOffRecord[]> {
  const saved = await active().read<{ records?: unknown[] }>(gocSignOffKey(noteId));
  return (saved?.records ?? []).filter(isGocSignOffRecord).filter((r) => r.noteId === noteId);
}

/** Adds or replaces (by id) one record. A LOCKED record is never overwritten except to mark it INVALIDATED. */
export function saveGocSignOff(record: GocSignOffRecord): Promise<GocSignOffRecord[]> {
  const next = queue.catch(() => {}).then(async () => {
    const records = await loadGocSignOffs(record.noteId);
    const old = records.find((r) => r.id === record.id);
    if (old?.status === 'LOCKED' && record.status !== 'INVALIDATED') throw new Error('A locked sign-off cannot be changed.');
    if (old?.status === 'INVALIDATED') throw new Error('An invalidated sign-off cannot be changed.');
    const all = old ? records.map((r) => r.id === record.id ? record : r) : [...records, record];
    await active().write(gocSignOffKey(record.noteId), { records: all });
    return all;
  });
  queue = next;
  return next;
}
