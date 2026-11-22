'use client';
// The doctor sign-ups and impact tabs share the survey admin's password (kept in this browser under the same key),
// so unlocking any tab unlocks the others.
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import s from './admin.module.css';

export const ADMIN_PASSWORD_KEY = 'sahara-admin-password';
export const savedAdminPassword = () => { try { return window.localStorage.getItem(ADMIN_PASSWORD_KEY) ?? ''; } catch { return ''; } };
const remember = (code: string) => { try { if (code) window.localStorage.setItem(ADMIN_PASSWORD_KEY, code); else window.localStorage.removeItem(ADMIN_PASSWORD_KEY); } catch { /* ignore */ } };

export type AdminPhase = 'locked' | 'loading' | 'failed' | 'ready';

/** POSTs { password } to `url` and keeps the reply. `pick` returns the data, or null when the reply is not usable. */
export function useAdminData<T>(url: string, pick: (value: Record<string, unknown>) => T | null, failure: string) {
  const [phase, setPhase] = useState<AdminPhase>('locked');
  const [password, setPassword] = useState('');
  const [data, setData] = useState<T | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const live = useRef(true), running = useRef(false), pickRef = useRef(pick);
  useEffect(() => { pickRef.current = pick; });

  const load = useCallback(async (code: string) => {
    if (running.current || !code) return; running.current = true; setBusy(true); setMessage(''); setPhase(p => (p === 'ready' ? p : 'loading'));
    try {
      const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: code }), cache: 'no-store' });
      const value = await response.json().catch(() => ({})) as Record<string, unknown>;
      if (!live.current) return;
      if (response.status === 401) { remember(''); setPassword(''); setData(null); setPhase('locked'); setMessage('Wrong password.'); return; }
      remember(code); setPassword(code);
      const picked = response.ok ? pickRef.current(value) : null;
      if (picked === null) throw new Error(typeof value.error === 'string' ? value.error : failure);
      setData(picked); setPhase('ready');
    } catch (e) { if (live.current) { setMessage(e instanceof Error ? e.message : failure); setPhase(p => (p === 'ready' ? p : 'failed')); } }
    finally { running.current = false; if (live.current) setBusy(false); }
  }, [url, failure]);

  useEffect(() => {
    live.current = true;
    const start = setTimeout(() => { const code = savedAdminPassword(); if (code) void load(code); }, 0);
    return () => { live.current = false; clearTimeout(start); };
  }, [load]);

  return { phase, password, setPassword, data, busy, message, setMessage, load, reload: () => load(password || savedAdminPassword()) };
}

export function AdminLock({ id, password, setPassword, message, busy, onOpen }: { id: string; password: string; setPassword: (v: string) => void; message: string; busy: boolean; onOpen: () => void }) {
  return <form className={`${s.card} ${s.form}`} onSubmit={(e: FormEvent) => { e.preventDefault(); onOpen(); }}>
    <label className={s.field} htmlFor={id}>Password<input id={id} type="password" autoComplete="off" value={password} onChange={e => setPassword(e.target.value)} required /></label>
    {message && <p className={s.error} role="alert">{message}</p>}
    <button className={s.primary} type="submit" disabled={busy}>Open</button>
  </form>;
}
