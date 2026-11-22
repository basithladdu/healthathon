'use client';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import s from './admin.module.css';
import { AdminLock, useAdminData } from './admin-access';
import { APPROVE_NOTE, DOCTOR_STATUSES, DOCTOR_TRANSITIONS, NMC_REGISTER_URL, type DoctorDecision, type DoctorRequest, type DoctorStatus } from './doctor-rules';

type Data = { rows: DoctorRequest[]; truncated: boolean };
const REVIEWER_KEY = 'sahara-admin-reviewer';
const STATUS_LABEL: Record<DoctorStatus, string> = { pending: 'Waiting for review', approved: 'Approved', rejected: 'Rejected', suspended: 'Suspended' };
const BADGE: Record<DoctorStatus, string> = { pending: s.badgeUnknown, approved: s.badgeYes, rejected: s.badgeNo, suspended: s.badgeNo };
const ACTION: Record<DoctorDecision, string> = { approved: 'Approve', rejected: 'Reject', suspended: 'Suspend' };
const when = (iso: string | null) => { if (!iso) return ''; const d = new Date(iso); return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' }) + ' IST'; };

function pick(value: Record<string, unknown>): Data | null {
  return Array.isArray(value.rows) ? { rows: value.rows as DoctorRequest[], truncated: value.truncated === true } : null;
}

export function DoctorAdmin() {
  const admin = useAdminData<Data>('/api/admin-doctors', pick, 'Could not load doctor sign-ups.');
  const [filter, setFilter] = useState<DoctorStatus | ''>('');
  const [reviewer, setReviewer] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => { const t = setTimeout(() => { try { setReviewer(window.localStorage.getItem(REVIEWER_KEY) ?? ''); } catch { /* ignore */ } }, 0); return () => clearTimeout(t); }, []);
  const saveReviewer = (v: string) => { setReviewer(v); try { window.localStorage.setItem(REVIEWER_KEY, v); } catch { /* ignore */ } };

  const rows = useMemo(() => admin.data?.rows ?? [], [admin.data]);
  const counts = useMemo(() => Object.fromEntries(DOCTOR_STATUSES.map(st => [st, rows.filter(r => r.status === st).length])) as Record<DoctorStatus, number>, [rows]);
  const shown = filter ? rows.filter(r => r.status === filter) : rows;

  const header = <div className={s.toolbarTop}>
    <div><h1 className={s.title}>Doctor sign-ups</h1><p className={s.lead}>Doctors who asked to be verified in the Sahara app. Check each registration on the public register before you approve. A typed name and number do not prove who someone is.</p></div>
    {admin.phase === 'ready' && <div className={s.actions}><button type="button" className={s.secondary} disabled={admin.busy} onClick={() => { setNotice(''); void admin.reload(); }}>{admin.busy ? 'Refreshing…' : 'Refresh'}</button></div>}
  </div>;

  if (admin.phase === 'locked') return <>{header}<AdminLock id="doctor-password" password={admin.password} setPassword={v => { admin.setPassword(v); admin.setMessage(''); }} message={admin.message} busy={admin.busy} onOpen={() => void admin.load(admin.password)} /></>;
  if (admin.phase === 'loading') return <>{header}<p className={s.status} role="status">Loading doctor sign-ups…</p></>;
  if (admin.phase === 'failed') return <>{header}<p className={s.notice} role="alert">{admin.message}</p><button type="button" className={s.secondary} disabled={admin.busy} onClick={() => void admin.reload()}>Try again</button></>;

  return <>
    {header}
    {admin.message && <p className={s.notice} role="alert">{admin.message}</p>}
    {notice && <p className={s.status} role="status">{notice}</p>}
    <section className={s.summary} aria-label="Sign-ups by status">
      <div className={s.stats}>
        {DOCTOR_STATUSES.map(st => <button key={st} type="button" className={`${s.stat} ${s.statButton} ${st === 'pending' && counts.pending ? s.statMain : ''}`} aria-pressed={filter === st} onClick={() => setFilter(f => (f === st ? '' : st))}><strong>{counts[st]}</strong><span>{STATUS_LABEL[st]}</span></button>)}
      </div>
      <label className={`${s.field} ${s.reviewer}`} htmlFor="doctor-reviewer">Your name (saved in the review log)
        <input id="doctor-reviewer" value={reviewer} maxLength={100} autoComplete="name" onChange={e => saveReviewer(e.target.value)} placeholder="e.g. your name, Sahara team" />
      </label>
    </section>
    <p className={s.count}>{filter ? <>Showing <strong>{shown.length}</strong> {STATUS_LABEL[filter].toLowerCase()} · <button type="button" className={s.clear} onClick={() => setFilter('')}>Show all</button></> : <><strong>{rows.length}</strong> sign-ups, waiting ones first</>}{admin.data?.truncated ? ' · only the latest 500 are shown' : ''}</p>
    {shown.length === 0 ? <div className={`${s.summary} ${s.empty}`}><p>{rows.length ? 'No sign-ups with this status.' : 'No doctor has asked to be verified yet.'}</p></div>
      : <div className={s.doctorList}>{shown.map(r => <DoctorCard key={`${r.user_id}:${r.revision}`} row={r} reviewer={reviewer} password={admin.password} onSaved={async text => { setNotice(text); await admin.reload(); }} />)}</div>}
  </>;
}

function DoctorCard({ row, reviewer, password, onSaved }: { row: DoctorRequest; reviewer: string; password: string; onSaved: (text: string) => Promise<void> }) {
  const [decision, setDecision] = useState<DoctorDecision | null>(null);
  const [evidence, setEvidence] = useState(''); const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const options = DOCTOR_TRANSITIONS[row.status];
  const needsReason = decision === 'rejected' || decision === 'suspended';

  async function submit(e: FormEvent) {
    e.preventDefault(); if (!decision || busy) return;
    if (reviewer.trim().length < 2) { setError('Add your name above first, so the review log shows who decided.'); return; }
    if (evidence.trim().length < 8) { setError('Say where you checked the registration (at least 8 characters).'); return; }
    if (needsReason && note.trim().length < 8) { setError('Give a short reason (at least 8 characters). The doctor sees it in the app.'); return; }
    setBusy(true); setError('');
    try {
      const response = await fetch('/api/admin-doctors/review', { method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store',
        body: JSON.stringify({ password, user_id: row.user_id, revision: row.revision, from: row.status, decision, evidence: evidence.trim(), note: note.trim(), reviewer: reviewer.trim() }) });
      const value = await response.json().catch(() => ({})) as { error?: string };
      if (!response.ok) throw new Error(value.error ?? 'Could not save the review.');
      setDecision(null); await onSaved(`${row.name}: ${decision === 'approved' ? 'approved' : decision === 'rejected' ? 'rejected' : 'suspended'}.`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save the review.'); }
    finally { setBusy(false); }
  }

  return <article className={s.doctorCard} aria-labelledby={`doc-${row.user_id}`}>
    <div className={s.doctorHead}>
      <h2 id={`doc-${row.user_id}`} className={s.subtitle}>{row.name}</h2>
      <span className={`${s.badge} ${BADGE[row.status]}`}>{STATUS_LABEL[row.status]}</span>
    </div>
    <dl className={s.doctorFacts}>
      <div className={s.answer}><dt>Medical council</dt><dd>{row.council}</dd></div>
      <div className={s.answer}><dt>Registration number</dt><dd className={s.code}>{row.registration}</dd></div>
      <div className={s.answer}><dt>Account email</dt><dd>{row.email ? <a href={`mailto:${row.email}`}>{row.email}</a> : <span className={s.muted}>Not found</span>}</dd></div>
      <div className={s.answer}><dt>Submitted</dt><dd>{when(row.submitted_at)}</dd></div>
      {row.reviewed_at && <div className={s.answer}><dt>Last reviewed</dt><dd>{when(row.reviewed_at)}</dd></div>}
      {row.review_note && <div className={s.answer}><dt>Note to the doctor</dt><dd className={s.prose}>{row.review_note}</dd></div>}
    </dl>
    <p className={s.doctorCheck}><a href={NMC_REGISTER_URL} target="_blank" rel="noopener noreferrer">Check on the NMC Indian Medical Register<span className={s.visuallyHidden}> (opens in a new tab)</span> ↗</a> <span className={s.small}>Search by registration number {row.registration} and pick the State Medical Council. Doctors registered only with a state council may need that council’s own register.</span></p>
    {options.length > 0 && !decision && <div className={s.actions}>{options.map(d => <button key={d} type="button" className={d === 'approved' ? s.primary : s.secondary} onClick={() => { setDecision(d); setError(''); }}>{row.status === 'suspended' && d === 'approved' ? 'Reinstate' : ACTION[d]}</button>)}</div>}
    {options.length === 0 && <p className={s.small}>A rejected doctor can send a new request from the app; it will show here as waiting.</p>}
    {decision && <form className={`${s.form} ${s.decision}`} onSubmit={submit}>
      <label className={s.field} htmlFor={`ev-${row.user_id}`}>Where you checked the registration
        <input id={`ev-${row.user_id}`} value={evidence} minLength={8} maxLength={1200} required disabled={busy} onChange={e => setEvidence(e.target.value)} placeholder="e.g. NMC Indian Medical Register: name and number match" />
      </label>
      <label className={s.field} htmlFor={`note-${row.user_id}`}>{needsReason ? 'Reason (the doctor sees this in the app)' : 'Note to the doctor (optional)'}
        <textarea id={`note-${row.user_id}`} value={note} rows={3} minLength={needsReason ? 8 : undefined} maxLength={2000} required={needsReason} disabled={busy} onChange={e => setNote(e.target.value)} placeholder={needsReason ? 'e.g. The registration number does not match this name on the register.' : APPROVE_NOTE} />
      </label>
      {error && <p className={s.error} role="alert">{error}</p>}
      <div className={s.actions}>
        <button type="submit" className={decision === 'approved' ? s.primary : s.danger} disabled={busy}>{busy ? 'Saving…' : `${row.status === 'suspended' && decision === 'approved' ? 'Reinstate' : ACTION[decision]} ${row.name}`}</button>
        <button type="button" className={s.secondary} disabled={busy} onClick={() => { setDecision(null); setError(''); }}>Cancel</button>
      </div>
    </form>}
  </article>;
}
