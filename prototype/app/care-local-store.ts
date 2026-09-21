'use client';

import { useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react';

export type CareSaveStatus = 'loading' | 'saving' | 'saved' | 'unavailable';
let database: Promise<IDBDatabase> | undefined;
const LOCAL_PREFIX = 'saanthvana-care:';
let backend: Promise<'database' | 'local'> | undefined;

function openDatabase(): Promise<IDBDatabase> {
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open('saanthvana-family-care', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('care');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return database;
}

async function readValue<T>(key: string): Promise<T | undefined> {
  if (await storageBackend() === 'local') {
    const saved = localStorage.getItem(LOCAL_PREFIX + key);
    const record = saved ? JSON.parse(saved) : null;
    return record?.version === 1 ? restoreFiles(record.value) as T : undefined;
  }
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const request = db.transaction('care', 'readonly').objectStore('care').get(key);
    request.onsuccess = () => resolve(request.result?.version === 1 ? request.result.value as T : undefined);
    request.onerror = () => reject(request.error);
  });
}

async function writeValue<T>(key: string, value: T): Promise<void> {
  if (await storageBackend() === 'local') {
    const saved = await storeFiles(value);
    localStorage.setItem(LOCAL_PREFIX + key, JSON.stringify({ version: 1, value: saved }));
    return;
  }
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('care', 'readwrite');
    transaction.objectStore('care').put({ version: 1, value }, key);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

function storageBackend(): Promise<'database' | 'local'> {
  if (!backend) backend = (async () => {
    let savedBackend: string | null = null;
    try { savedBackend = localStorage.getItem(LOCAL_PREFIX + 'backend'); } catch { /* IndexedDB may still be available. */ }
    if (savedBackend === 'local') return 'local';
    try { await openDatabase(); return 'database'; }
    catch {
      // Some browser profiles cannot open IndexedDB. Keep the fallback stable
      // across visits so two stores never silently show different records.
      localStorage.setItem(LOCAL_PREFIX + 'backend', 'local');
      return 'local';
    }
  })();
  return backend;
}

async function storeFiles(value: unknown): Promise<unknown> {
  if (value instanceof File) {
    const data = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(value);
    });
    return { careFile: 1, name: value.name, type: value.type, lastModified: value.lastModified, data };
  }
  if (Array.isArray(value)) return Promise.all(value.map(storeFiles));
  if (value && typeof value === 'object') return Object.fromEntries(await Promise.all(Object.entries(value).map(async ([key, item]) => [key, await storeFiles(item)])));
  return value;
}

function restoreFiles(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(restoreFiles);
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    if (record.careFile === 1 && typeof record.data === 'string' && typeof record.name === 'string') {
      const bytes = Uint8Array.from(atob(record.data.slice(record.data.indexOf(',') + 1)), (char) => char.charCodeAt(0));
      return new File([bytes], record.name, { type: String(record.type), lastModified: Number(record.lastModified) });
    }
    return Object.fromEntries(Object.entries(record).map(([key, item]) => [key, restoreFiles(item)]));
  }
  return value;
}

export function useCareLocalState<T>(key: string, initial: T): [T, Dispatch<SetStateAction<T>>, CareSaveStatus] {
  const [value, setValue] = useState(initial);
  const [status, setStatus] = useState<CareSaveStatus>('loading');
  const initialValue = useRef(initial);
  const ready = useRef(false);
  const revision = useRef(0);
  const queue = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    let active = true;
    readValue<T>(key).then((saved) => {
      if (!active) return;
      ready.current = true;
      setValue(saved ?? initialValue.current);
      setStatus('saved');
    }).catch((error: unknown) => {
      if (!active) return;
      console.warn('Care storage could not be opened.', error);
      ready.current = true;
      setStatus('unavailable');
    });
    return () => { active = false; };
  }, [key]);

  useEffect(() => {
    if (!ready.current) return;
    let active = true;
    const version = ++revision.current;
    queue.current = queue.current.catch(() => {}).then(async () => {
      if (active && version === revision.current) setStatus('saving');
      await writeValue(key, value);
      if (active && version === revision.current) setStatus('saved');
    }).catch((error: unknown) => {
      console.warn('Care changes could not be saved.', error);
      if (active && version === revision.current) setStatus('unavailable');
    });
    return () => { active = false; };
  }, [key, value]);

  return [value, setValue, status];
}
