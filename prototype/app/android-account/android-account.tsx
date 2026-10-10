'use client';

import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import Link from 'next/link';
import {
  AccountAuthError, changeRecoveredPassword, parseAccountCallback, passwordError,
  revokeAccountSession, verifyAccountCallback, type AccountCallback, type PublicAuthConfig,
} from './account-auth';
import styles from './android-account.module.css';

type View = 'checking' | 'missing' | 'expired' | 'unavailable' | 'check-error' | 'confirmed' | 'password' | 'done';

export function AndroidAccount({ config }: { config: PublicAuthConfig | null }) {
  const [view, setView] = useState<View>('checking');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [cleanupWarning, setCleanupWarning] = useState(false);
  const [retry, setRetry] = useState(0);
  const callback = useRef<AccountCallback | null>(null);
  const token = useRef<string | null>(null);
  const submitting = useRef(false);
  const lifecycle = useRef(0);
  const passwordInput = useRef<HTMLInputElement>(null);
  const confirmationInput = useRef<HTMLInputElement>(null);

  const discard = useCallback(() => {
    lifecycle.current += 1;
    const pending = callback.current;
    const savedToken = token.current ?? (pending && 'accessToken' in pending ? pending.accessToken : null);
    token.current = null;
    callback.current = null;
    setPassword(''); setConfirmation(''); setEmail(''); setView('expired');
    if (config && savedToken) void revokeAccountSession(config, savedToken);
  }, [config]);

  useEffect(() => {
    // Read once, then remove all callback parameters before any network request.
    callback.current ??= parseAccountCallback(window.location.hash);
    window.history.replaceState(null, '', window.location.pathname);
    const current = callback.current;
    const generation = lifecycle.current;
    let active = true;
    async function check(): Promise<{ view: View; email?: string; accessToken?: string; cleanupWarning?: boolean } | null> {
      if (!config) return { view: 'unavailable' as View };
      if (!('accessToken' in current)) return { view: current.kind === 'missing' ? 'missing' as View : 'expired' as View };
      const user = await verifyAccountCallback(config, current);
      if (!active || generation !== lifecycle.current) return null;
      if (current.kind === 'confirmation') {
        const revoked = await revokeAccountSession(config, current.accessToken);
        return { view: 'confirmed' as View, cleanupWarning: !revoked };
      }
      return { view: 'password' as View, email: user.email, accessToken: current.accessToken };
    }
    void check().then((result) => {
      if (!active || generation !== lifecycle.current || !result) return;
      token.current = result.accessToken ?? null;
      callback.current = null;
      setEmail(result.email ?? '');
      setCleanupWarning(result.cleanupWarning ?? false);
      setError('');
      setView(result.view);
    }).catch((cause: unknown) => {
      if (!active || generation !== lifecycle.current) return;
      const expired = cause instanceof AccountAuthError && cause.expired;
      if (expired) { callback.current = null; token.current = null; }
      setError(cause instanceof AccountAuthError ? cause.message : 'Could not check this link. Please try again.');
      setView(expired ? 'expired' : 'check-error');
    });
    return () => { active = false; };
  }, [config, retry]);

  useEffect(() => {
    window.addEventListener('pagehide', discard);
    return () => { window.removeEventListener('pagehide', discard); };
  }, [discard]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !config || !token.current || view !== 'password') return;
    const validation = passwordError(password, confirmation);
    if (validation) {
      setError(validation);
      (password.length < 10 || !password.trim() ? passwordInput : confirmationInput).current?.focus();
      return;
    }
    submitting.current = true; setBusy(true); setError('');
    try {
      const result = await changeRecoveredPassword(config, token.current, password, confirmation);
      token.current = null; callback.current = null;
      setPassword(''); setConfirmation(''); setEmail('');
      setCleanupWarning(!result.sessionRevoked); setView('done');
    } catch (cause) {
      if (cause instanceof AccountAuthError && cause.expired) {
        token.current = null; callback.current = null; setPassword(''); setConfirmation(''); setView('expired');
      }
      setError(cause instanceof AccountAuthError ? cause.message : 'Could not change your password. Please try again.');
    } finally { submitting.current = false; setBusy(false); }
  }

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}><Link href="/" className={styles.brand} onNavigate={discard}>Sahara</Link><span>Android account</span></header>
        <section className={styles.panel} aria-live="polite" aria-busy={busy || view === 'checking'}>
          {view === 'checking' && <><h1>Checking your link…</h1><p>This will only take a moment.</p></>}
          {view === 'confirmed' && <><h1>Email confirmed</h1><p>Return to Sahara and sign in with your email and password.</p></>}
          {view === 'done' && <><h1>Password changed</h1><p>Return to Sahara and sign in with your new password. You can close this page.</p></>}
          {view === 'missing' && <><h1>Open your email link</h1><p>Use the confirmation or password reset link sent by Sahara.</p><p>Need a new reset link? Open Your account in Sahara, enter your email and choose Forgot password.</p></>}
          {view === 'expired' && <><h1>This link is no longer available</h1><p>For a password reset, open Sahara and choose Forgot password to request a new link.</p><p>If you were confirming your email, try signing in first. The email may already be confirmed.</p></>}
          {view === 'unavailable' && <><h1>Account help is unavailable</h1><p>Please contact <a href="mailto:workwithdevit@gmail.com">workwithdevit@gmail.com</a> so we can help.</p></>}
          {view === 'check-error' && <><h1>Could not check your link</h1><p>{error}</p><button className={styles.button} type="button" onClick={() => { setView('checking'); setRetry((value) => value + 1); }}>Try again</button></>}
          {view === 'password' && <>
            <h1>Choose a new password</h1>
            <p className={styles.email}>{email}</p>
            <form onSubmit={submit} noValidate>
              <label htmlFor="sahara-new-password">New password</label>
              <input ref={passwordInput} id="sahara-new-password" type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => { setPassword(event.target.value); setError(''); }} autoComplete="new-password" minLength={10} required aria-describedby="sahara-password-help sahara-password-error" disabled={busy} />
              <p id="sahara-password-help" className={styles.hint}>Use at least 10 characters.</p>
              <label htmlFor="sahara-confirm-password">Confirm password</label>
              <input ref={confirmationInput} id="sahara-confirm-password" type={showPassword ? 'text' : 'password'} value={confirmation} onChange={(event) => { setConfirmation(event.target.value); setError(''); }} autoComplete="new-password" required aria-describedby="sahara-password-error" aria-invalid={Boolean(error)} disabled={busy} />
              <label className={styles.checkbox}><input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} />Show passwords</label>
              <p id="sahara-password-error" className={styles.error} role="alert">{error}</p>
              <button className={styles.button} type="submit" disabled={busy}>{busy ? 'Changing password…' : 'Change password'}</button>
            </form>
          </>}
          {cleanupWarning && <p className={styles.warning}>Your change is saved. We could not confirm this browser’s sign-out. Close this page.</p>}
        </section>
        <noscript><p>JavaScript is needed to check your email link. Enable it and open the link again.</p></noscript>
        <footer className={styles.footer}><Link href="/privacy" onNavigate={discard}>Privacy policy</Link><a href="mailto:workwithdevit@gmail.com" onClick={discard}>Get help</a></footer>
      </div>
    </main>
  );
}
