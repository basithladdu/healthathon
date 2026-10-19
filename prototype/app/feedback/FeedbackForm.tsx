'use client';

import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react';
import styles from './feedback.module.css';
import HospitalSurvey from './HospitalSurvey';
import Link from 'next/link';

const ratings = ['Very difficult', 'Not great', 'Okay', 'Good', 'Really good'];
const roles = [['patient', 'Patient'], ['caregiver', 'Family / caregiver'], ['clinician', 'Doctor / health professional'], ['other', 'Other']] as const;
const categories = [['navigation', 'Finding my way'], ['design', 'Look & feel'], ['bug', 'Something isn’t working'], ['idea', 'An idea'], ['other', 'Something else']] as const;

function Face({ value }: { value: number }) {
  const position = (value - 1) / 4;
  const eyeWidth = position < .5 ? 21 + position * 38 : 40 + (position - .5) * 10;
  const eyeHeight = position < .5 ? 22 - position * 32 : 6 + (position - .5) * 70;
  const curve = 112 + position * 80;
  return <svg viewBox="0 0 260 210" className={styles.face} aria-hidden="true"><ellipse cx="75" cy="73" rx={eyeWidth / 2} ry={eyeHeight / 2} /><ellipse cx="185" cy="73" rx={eyeWidth / 2} ry={eyeHeight / 2} /><path d={`M 93 151 Q 130 ${curve} 167 151`} fill="none" stroke="currentColor" strokeWidth="9" strokeLinecap="round" /></svg>;
}

export default function FeedbackForm({ initialMode = 'app' }: { initialMode?: 'app' | 'hospital' }) {
  const [mode, setMode] = useState<'app' | 'hospital'>(initialMode);
  useEffect(() => {
    const syncMode = () => {
      const values = new URLSearchParams(window.location.search).getAll('form');
      setMode(values.length === 1 && values[0] === 'hospital' ? 'hospital' : 'app');
    };
    window.addEventListener('popstate', syncMode);
    return () => window.removeEventListener('popstate', syncMode);
  }, []);
  function selectMode(next: 'app' | 'hospital') {
    if (next === mode) return;
    window.history.pushState(null, '', `${window.location.pathname}?form=${next}`);
    setMode(next);
  }
  const [rating, setRating] = useState(3);
  const [role, setRole] = useState('');
  const [category, setCategory] = useState('');
  const [message, setMessage] = useState('');
  const [consent, setConsent] = useState(false);
  const [website, setWebsite] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [received, setReceived] = useState(false);
  const attempt = useRef<string | null>(null);
  const attemptBody = useRef<string | null>(null);
  const pending = useRef(false);
  const notice = useRef<HTMLDivElement>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    if (!role || !category || !consent) { setError('Choose your role and a topic, then confirm how we may use your feedback.'); return; }
    pending.current = true; setBusy(true); setError('');
    try {
      const payload = { kind: 'app', rating: Math.round(rating), role, category, message: message.trim(), consent: true, website };
      const canonical = JSON.stringify(payload);
      if (attemptBody.current !== canonical) { attempt.current = crypto.randomUUID(); attemptBody.current = canonical; }
      const response = await fetch('/api/feedback', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...payload, submissionId: attempt.current }), signal: AbortSignal.timeout(20_000) });
      const result: unknown = await response.json().catch(() => null);
      if (!response.ok || !result || typeof result !== 'object' || !('ok' in result) || result.ok !== true || !('id' in result) || typeof result.id !== 'string' || !result.id.trim()) {
        throw new Error(response.status === 429 ? 'Submission limit reached. Please try later.' : response.status === 503 ? 'Feedback is temporarily unavailable. Your answers are still here; please try again later.' : 'We couldn’t confirm that your feedback was saved. Your answers are still here; please try again.');
      }
      setReceived(true); setMessage(''); setConsent(false); setWebsite(''); setRole(''); setCategory(''); attempt.current = null; attemptBody.current = null;
      requestAnimationFrame(() => notice.current?.focus());
    } catch (cause) {
      setError(cause instanceof Error && cause.name !== 'TimeoutError' && cause.name !== 'TypeError' ? cause.message : 'Connection interrupted. Your answers are still here. Try again when you’re connected.');
    } finally { pending.current = false; setBusy(false); }
  }

  return <><nav className={styles.modes} aria-label="Choose a feedback form"><button type="button" aria-pressed={mode === 'app'} onClick={() => selectMode('app')} disabled={busy}>App feedback<span>Tell us about using Sahara</span></button><button type="button" aria-pressed={mode === 'hospital'} onClick={() => selectMode('hospital')} disabled={busy}>Hospital survey<span>Share palliative-care service information</span></button></nav><div hidden={mode !== 'hospital'}><HospitalSurvey /></div><div hidden={mode !== 'app'}><div className={styles.layout}>
    <section className={styles.experience} aria-labelledby="feedback-title">
      <h1 id="feedback-title">How was Sahara{' '}<br />for you?</h1><p>Your experience can help us make the app easier to use.</p>
      <div className={styles.expression} data-feeling={rating <= 2 ? 'low' : rating >= 4 ? 'high' : 'middle'}><Face value={rating} /><span className={styles.feeling}>{ratings[Math.round(rating) - 1]}</span></div>
      <label className={styles.ratingLabel} htmlFor="feedback-rating">Your overall experience</label>
      <input id="feedback-rating" className={styles.slider} style={{ '--rating-fill': `${(rating - 1) * 25}%` } as CSSProperties} type="range" min="1" max="5" step="0.01" value={rating} aria-valuetext={`${Math.round(rating)} out of 5 — ${ratings[Math.round(rating) - 1]}`} onChange={(event) => setRating(Number(event.target.value))} disabled={busy || received} />
      <div className={styles.scale} aria-hidden="true"><span>Very difficult</span><span>Okay</span><span>Really good</span></div>
      <p className={styles.notClinical}>About the app—not your health or care. This form isn’t monitored for urgent help or medical advice.</p>
    </section>
    {received ? <div className={styles.receipt} ref={notice} tabIndex={-1} role="status"><span className={styles.receiptMark} aria-hidden="true">✓</span><h2>Thank you.<br />We received your feedback.</h2><p>Your experience helps us see what needs attention.</p><button type="button" className={styles.submit} onClick={() => { setReceived(false); setRating(3); }}>Give more feedback</button><Link href="/" className={styles.return}>Back to Sahara</Link></div> : <form className={styles.form} onSubmit={submit}>
      <fieldset disabled={busy} className={styles.fields}><legend className={styles.formTitle}>Tell us a little more</legend>
        <label htmlFor="feedback-role">I’m using Sahara as <span>(required)</span></label><select id="feedback-role" value={role} onChange={(event) => setRole(event.target.value)} required><option value="">Choose a role</option>{roles.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><p className={styles.hint}>This is how you describe yourself, not a verified account role.</p>
        <label htmlFor="feedback-category">What’s your feedback about? <span>(required)</span></label><select id="feedback-category" value={category} onChange={(event) => setCategory(event.target.value)} required><option value="">Choose a topic</option>{categories.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        <label htmlFor="feedback-message" className={styles.noteLabel}>Anything you’d like to add? <span>(optional)</span></label><textarea id="feedback-message" value={message} onChange={(event) => setMessage(event.target.value)} maxLength={1000} rows={4} aria-describedby="feedback-privacy feedback-count" placeholder="What worked well? What could be easier?" /><div id="feedback-count" className={styles.count}>{message.length} / 1,000</div>
        <p id="feedback-privacy" className={styles.privacy}>Your rating and comments are sent to Sahara’s Supabase database for app review. Please don’t include names, phone numbers, ABHA numbers or medical details. <Link href="/privacy">Privacy policy</Link>.</p>
        <div className={styles.trap} aria-hidden="true"><label htmlFor="feedback-website">Leave this empty</label><input id="feedback-website" name="website" tabIndex={-1} autoComplete="off" value={website} onChange={(event) => setWebsite(event.target.value)} /></div>
        <label className={styles.consent}><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} required /><span>I agree that Sahara may store and use this feedback to improve the app. <span>No contact details are requested.</span></span></label>
        <div className={styles.error} role="alert" aria-live="polite">{error}</div>
        <button className={styles.submit} type="submit" disabled={busy}>{busy ? 'Sending feedback…' : 'Send feedback'}<span aria-hidden="true">↗</span></button>
      </fieldset>
    </form>}
  </div></div></>;
}
