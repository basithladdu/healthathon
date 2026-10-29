'use client';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore, type FormEvent, type ReactNode } from 'react';
import { CareSharedClient } from '../care-shared-client';
import s from './admin.module.css';

/* Owner's explicit decision: a simple hardcoded gate. The real protection is the
   Supabase sign-in plus the directory_reviewer staff role checked server-side. */
const GATE_PASSWORD = '0';
const GATE_KEY = 'sahara-admin-unlocked';
const PAGE_SIZE = 50;

type Row = { id: string; facility: Record<string, unknown>; created_at: string; full_name: string; respondent_role: string; council: string; registration_state: string; registration_number: string; mobile: string | null; email: string | null; revision: number; decision: string };

/* ---------- labels (mirroring app/feedback/HospitalSurvey.tsx) ---------- */
const ROLE: Record<string, string> = { oncologist: 'Oncologist', palliative_physician: 'Palliative-care physician', physician: 'Other physician', resident: 'Resident / postgraduate trainee', nurse: 'Nurse', pharmacist: 'Pharmacist', other: 'Other' };
const COUNCIL: Record<string, string> = { nmc: 'National Medical Commission (NMC)', state_medical: 'State medical council', state_nursing: 'State nursing council', state_pharmacy: 'State pharmacy council', other: 'Other council' };
const INSTITUTION: Record<string, string> = { government_hospital: 'Government hospital', government_medical_college: 'Government medical college', private_hospital: 'Private hospital', private_medical_college: 'Private medical college', private_clinic: 'Private clinic', ngo_hospice: 'NGO / trust / hospice', other: 'Other' };
const CONNECTION: Record<string, string> = { works_here: 'Works here', refers_here: 'Refers patients here', other: 'Other' };
const SERVICE: Record<string, string> = { opd: 'OPD', ipd: 'IPD', home: 'Home care', none: 'None', unknown: 'Don’t know' };
const MORPHINE: Record<string, string> = { yes: 'Yes', no: 'No', unknown: 'Don’t know' };
const SUPPLY: Record<string, string> = { regular: 'Regular', intermittent: 'Intermittent', unknown: 'Don’t know' };
const DECISION: Record<string, string> = { pending_review: 'Pending review', approved: 'Approved', needs_clarification: 'Needs clarification', rejected: 'Rejected', withdrawn: 'Withdrawn' };

const str = (v: unknown) => (v === null || v === undefined ? '' : typeof v === 'string' ? v.trim() : typeof v === 'object' ? JSON.stringify(v) : String(v));
const label = (map: Record<string, string>, v: unknown) => { const k = str(v); return k ? map[k] ?? k : ''; };
const withOther = (map: Record<string, string>, v: unknown, other: unknown) => { const base = label(map, v); const extra = str(other); return str(v) === 'other' && extra ? `${base}: ${extra}` : base; };
const services = (r: Row) => (Array.isArray(r.facility.services) ? r.facility.services : []).map(v => label(SERVICE, v)).filter(Boolean).join(', ');
const hasPalliative = (r: Row) => Array.isArray(r.facility.services) && r.facility.services.some(v => ['opd', 'ipd', 'home'].includes(String(v)));

/** India Standard Time, sortable: 2026-10-29 14:05 */
function ist(iso: string) { const t = Date.parse(iso); if (!Number.isFinite(t)) return iso; return new Date(t + 330 * 60000).toISOString().slice(0, 16).replace('T', ' '); }
function istDate() { return new Date(Date.now() + 330 * 60000).toISOString().slice(0, 10); }
const pretty = (iso: string) => { const t = Date.parse(iso); return Number.isFinite(t) ? new Date(t).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }) : iso; };

function registration(r: Row) { return [label(COUNCIL, r.council), str(r.registration_state), str(r.registration_number)].filter(Boolean).join(' · '); }

type Column = { key: string; label: string; get: (r: Row) => string; wide?: boolean; nowrap?: boolean };
/* "Submitted by" is shown as one combined cell in the table but split into separate CSV columns. */
const SUBMITTER_COLUMNS: Column[] = [
  { key: 'full_name', label: 'Name', get: r => str(r.full_name) },
  { key: 'role', label: 'Role', get: r => withOther(ROLE, r.respondent_role, r.facility.roleOther) },
  { key: 'council', label: 'Council', get: r => label(COUNCIL, r.council) },
  { key: 'registration_state', label: 'Registration state', get: r => str(r.registration_state) },
  { key: 'registration_number', label: 'Registration number', get: r => str(r.registration_number) },
  { key: 'email', label: 'Email', get: r => str(r.email) },
  { key: 'mobile', label: 'Mobile', get: r => str(r.mobile) },
];
const FACILITY_COLUMNS: Column[] = [
  { key: 'hospitalName', label: 'Hospital', get: r => str(r.facility.hospitalName), wide: true },
  { key: 'institutionType', label: 'Institution type', get: r => withOther(INSTITUTION, r.facility.institutionType, r.facility.institutionOther) },
  { key: 'connection', label: 'Connection', get: r => withOther(CONNECTION, r.facility.connection, r.facility.connectionOther) },
  { key: 'state', label: 'State / UT', get: r => str(r.facility.state) },
  { key: 'city', label: 'City / district', get: r => str(r.facility.city) },
  { key: 'hospitalAddress', label: 'Address', get: r => str(r.facility.hospitalAddress), wide: true },
  { key: 'latitude', label: 'Latitude', get: r => str(r.facility.latitude), nowrap: true },
  { key: 'longitude', label: 'Longitude', get: r => str(r.facility.longitude), nowrap: true },
  { key: 'locationConfirmed', label: 'Pin confirmed', get: r => (r.facility.locationConfirmed === true ? 'Yes' : r.facility.locationConfirmed === false ? 'No' : '') },
  { key: 'services', label: 'Palliative services', get: services },
  { key: 'morphine', label: 'Oral morphine (last 3 months)', get: r => label(MORPHINE, r.facility.morphine) },
  { key: 'supply', label: 'Morphine supply', get: r => label(SUPPLY, r.facility.supply) },
  { key: 'palliativePhysicians', label: 'Palliative physicians / departments', get: r => str(r.facility.palliativePhysicians), wide: true },
  { key: 'familyNotes', label: 'Notes for families', get: r => str(r.facility.familyNotes), wide: true },
];
const META_COLUMNS: Column[] = [
  { key: 'decision', label: 'Review status', get: r => label(DECISION, r.decision) },
  { key: 'revision', label: 'Review revision', get: r => str(r.revision) },
  { key: 'formVersion', label: 'Form version', get: r => str(r.facility.formVersion), nowrap: true },
  { key: 'id', label: 'Submission ID', get: r => str(r.id), nowrap: true },
];
/* Keys already shown, or internal/anti-spam fields not worth a column. */
const KNOWN = new Set(['hospitalName', 'institutionType', 'institutionOther', 'connection', 'connectionOther', 'state', 'city', 'hospitalAddress', 'latitude', 'longitude', 'locationConfirmed', 'services', 'morphine', 'supply', 'palliativePhysicians', 'familyNotes', 'formVersion', 'roleOther', 'kind', 'website', 'submissionId', 'consent', 'accuracy', 'mobile']);
const humanise = (k: string) => k.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());

/* ---------- CSV ---------- */
function csvCell(raw: string) {
  // Neutralise spreadsheet formulas in free-text answers, but leave plain numbers alone.
  let v = /^[=+\-@\t\r]/.test(raw) && !/^[+-]?\d+(\.\d+)?$/.test(raw) ? `'${raw}` : raw;
  if (/[",\r\n]/.test(v) || v !== v.trim()) v = `"${v.replace(/"/g, '""')}"`;
  return v;
}
function downloadCsv(rows: Row[], columns: Column[]) {
  const head: Column[] = [{ key: 'created_at', label: 'Submitted (IST)', get: r => ist(r.created_at) }, ...SUBMITTER_COLUMNS, ...columns];
  const lines = [head.map(c => csvCell(c.label)).join(','), ...rows.map(r => head.map(c => csvCell(c.get(r))).join(','))];
  const blob = new Blob(['﻿' + lines.join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = `sahara-hospital-survey-${istDate()}.csv`;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/* ---------- password gate (sessionStorage, guarded) ---------- */
const gateListeners = new Set<() => void>();
function readGate() { try { return window.sessionStorage.getItem(GATE_KEY) === '1'; } catch { return false; } }
function writeGate(on: boolean) { try { if (on) window.sessionStorage.setItem(GATE_KEY, '1'); else window.sessionStorage.removeItem(GATE_KEY); } catch { /* memory-only unlock still works for this page view */ } gateListeners.forEach(fn => fn()); }
const subscribeGate = (fn: () => void) => { gateListeners.add(fn); return () => { gateListeners.delete(fn); }; };

export function SurveyAdmin() {
  const stored = useSyncExternalStore(subscribeGate, readGate, () => false);
  const [memoryUnlock, setMemoryUnlock] = useState(false);
  const [code, setCode] = useState(''); const [error, setError] = useState('');
  const unlocked = stored || memoryUnlock;
  if (!unlocked) return <section className={s.card} aria-labelledby="admin-gate-title">
    <h1 id="admin-gate-title" className={s.title}>Survey admin</h1>
    <p className={s.lead}>Enter the admin password to continue.</p>
    <form className={s.form} onSubmit={(e: FormEvent) => { e.preventDefault(); if (code === GATE_PASSWORD) { setMemoryUnlock(true); writeGate(true); setCode(''); setError(''); } else setError('That password is not correct.'); }}>
      <label className={s.field} htmlFor="admin-gate">Admin password<input id="admin-gate" type="password" autoComplete="off" value={code} onChange={e => { setCode(e.target.value); setError(''); }} aria-invalid={error ? true : undefined} aria-describedby={error ? 'admin-gate-error' : undefined} required /></label>
      {error && <p id="admin-gate-error" className={s.error} role="alert">{error}</p>}
      <button className={s.primary} type="submit">Continue</button>
    </form>
  </section>;
  return <SurveyData onLock={() => { setMemoryUnlock(false); writeGate(false); }} />;
}

type Phase = 'init' | 'signed-out' | 'loading' | 'denied' | 'ready';

function SurveyData({ onLock }: { onLock: () => void }) {
  const client = useRef<CareSharedClient | null>(null), epoch = useRef(0), locked = useRef(false);
  const [phase, setPhase] = useState<Phase>('init');
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [rows, setRows] = useState<Row[]>([]); const [busy, setBusy] = useState(false); const [message, setMessage] = useState('');
  const [account, setAccount] = useState(''); const [loadedAt, setLoadedAt] = useState('');
  const [query, setQuery] = useState(''); const [newestFirst, setNewestFirst] = useState(true);

  async function load() {
    const c = client.current!, user = c.currentSession()?.userId, stamp = ++epoch.current;
    const current = () => client.current === c && epoch.current === stamp && c.currentSession()?.userId === user;
    setPhase('loading');
    await c.verifyAccount(); if (!current()) return;
    setAccount(c.currentSession()?.email ?? '');
    const role = await c.rpc<boolean>('healthathon_staff_role', { p_role: 'directory_reviewer' }); if (!current()) return;
    if (role !== true) { setRows([]); setPhase('denied'); setMessage('This account is signed in but does not have the directory reviewer role, so survey submissions cannot be read. Ask the Sahara team to assign “directory_reviewer”, or sign out and use a reviewer account.'); return; }
    const all = new Map<string, Row>();
    for (let offset = 0; offset <= 100000; offset += PAGE_SIZE) {
      const page = await c.rpc<Row[]>('sahara_directory_queue', { p_offset: offset }); if (!current()) return;
      if (!Array.isArray(page)) throw Error('The survey submissions could not be read.');
      page.forEach(r => all.set(r.id, r));
      if (page.length < PAGE_SIZE) break;
    }
    setRows([...all.values()]); setLoadedAt(new Date().toISOString()); setPhase('ready');
    setMessage(all.size ? '' : 'No survey submissions yet.');
  }
  async function action(fn: () => Promise<void>) {
    if (locked.current) return; locked.current = true; setBusy(true); setMessage('');
    try { await fn(); }
    catch (e) { if (client.current) { setMessage(e instanceof Error ? e.message : 'Could not load the survey submissions.'); setPhase(client.current.currentSession() ? 'ready' : 'signed-out'); } }
    finally { locked.current = false; if (client.current) setBusy(false); }
  }
  useEffect(() => {
    client.current = new CareSharedClient(window.sessionStorage);
    if (client.current.currentSession()) void action(load);
    else setPhase('signed-out');
    const generation = epoch, current = client;
    return () => { generation.current++; current.current = null; };
  }, []);

  const signOut = () => void action(async () => { const c = client.current!; epoch.current++; setRows([]); setPhase('signed-out'); setPassword(''); setAccount(''); await c.signOut(); });

  const extraColumns = useMemo<Column[]>(() => {
    const keys = new Set<string>();
    rows.forEach(r => Object.keys(r.facility ?? {}).forEach(k => { if (!KNOWN.has(k)) keys.add(k); }));
    return [...keys].sort().map(k => ({ key: `x:${k}`, label: humanise(k), get: (r: Row) => str(r.facility[k]) }));
  }, [rows]);
  const columns = useMemo(() => [...FACILITY_COLUMNS, ...extraColumns, ...META_COLUMNS], [extraColumns]);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = q ? rows.filter(r => [pretty(r.created_at), ist(r.created_at), ...SUBMITTER_COLUMNS.map(c => c.get(r)), ...columns.map(c => c.get(r))].join('\u0000').toLowerCase().includes(q)) : rows;
    return [...filtered].sort((a, b) => { const d = (Date.parse(a.created_at) || 0) - (Date.parse(b.created_at) || 0); return newestFirst ? -d : d; });
  }, [rows, query, newestFirst, columns]);

  const summary = useMemo(() => {
    const byState = new Map<string, number>();
    const morphine = { yes: 0, no: 0, unknown: 0, blank: 0 };
    let palliative = 0;
    shown.forEach(r => {
      const st = str(r.facility.state) || 'Not specified'; byState.set(st, (byState.get(st) ?? 0) + 1);
      if (hasPalliative(r)) palliative++;
      const m = str(r.facility.morphine); if (m === 'yes' || m === 'no' || m === 'unknown') morphine[m]++; else morphine.blank++;
    });
    return { total: shown.length, palliative, morphine, byState: [...byState.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])) };
  }, [shown]);

  const header = <div className={s.toolbarTop}>
    <div><h1 className={s.title}>Hospital survey responses</h1><p className={s.lead}>Every submission from the hospital survey. Professional and contact details are private: do not share this export outside the review team.</p></div>
    <div className={`${s.accountBar} ${s.noPrint}`}>
      {account && <span className={s.account}>Signed in as <strong>{account}</strong></span>}
      {phase !== 'signed-out' && phase !== 'init' && <button type="button" className={s.secondary} disabled={busy} onClick={signOut}>Sign out</button>}
      <button type="button" className={s.ghost} onClick={onLock}>Lock page</button>
    </div>
  </div>;

  if (phase === 'signed-out') return <>{header}<section className={s.card} aria-labelledby="admin-signin-title">
    <h2 id="admin-signin-title" className={s.subtitle}>Sign in with a reviewer account</h2>
    <p className={s.lead}>Survey answers can only be read by a Sahara account with the directory reviewer role.</p>
    <form className={s.form} onSubmit={e => { e.preventDefault(); void action(async () => { await client.current!.signIn(email, password); setPassword(''); await load(); }); }}>
      <label className={s.field} htmlFor="admin-email">Email<input id="admin-email" type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required /></label>
      <label className={s.field} htmlFor="admin-password">Password<input id="admin-password" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
      <button className={s.primary} type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    </form>
    {message && <p className={s.error} role="alert">{message}</p>}
  </section></>;

  if (phase === 'init' || phase === 'loading') return <>{header}<p className={s.status} role="status">{phase === 'init' ? 'Checking your account…' : 'Loading all survey submissions…'}</p></>;
  if (phase === 'denied') return <>{header}<p className={s.notice} role="alert">{message}</p></>;

  const stat = (n: number, text: ReactNode) => <div className={s.stat}><strong>{n}</strong><span>{text}</span></div>;
  return <>
    {header}
    <div className={s.printOnly}><p className={s.reportMeta}>Report generated {pretty(new Date().toISOString())} IST{loadedAt ? ` · data loaded ${pretty(loadedAt)} IST` : ''}{query.trim() ? ` · filtered by “${query.trim()}”` : ''}</p></div>
    {message && <p className={s.status} role="status">{message}</p>}
    <section className={s.summary} aria-labelledby="admin-summary-title">
      <h2 id="admin-summary-title" className={s.subtitle}>Summary{query.trim() ? ' (filtered rows)' : ''}</h2>
      <div className={s.stats}>
        {stat(summary.total, 'Total submissions')}
        {stat(summary.palliative, 'Report palliative services (OPD, IPD or home care)')}
        {stat(summary.morphine.yes, 'Oral morphine: yes')}
        {stat(summary.morphine.no, 'Oral morphine: no')}
        {stat(summary.morphine.unknown, 'Oral morphine: don’t know')}
        {summary.morphine.blank ? stat(summary.morphine.blank, 'Oral morphine: not answered') : null}
      </div>
      {summary.byState.length > 0 && <details className={s.states} open>
        <summary>Submissions by state / UT ({summary.byState.length})</summary>
        <ul>{summary.byState.map(([st, n]) => <li key={st}><span>{st}</span><strong>{n}</strong></li>)}</ul>
      </details>}
    </section>

    <div className={`${s.controls} ${s.noPrint}`}>
      <label className={s.search} htmlFor="admin-search">Search responses<input id="admin-search" type="search" value={query} placeholder="Hospital, state, name, email…" onChange={e => setQuery(e.target.value)} /></label>
      <div className={s.actions}>
        <button type="button" className={s.secondary} disabled={busy} onClick={() => void action(load)}>Refresh</button>
        <button type="button" className={s.secondary} disabled={!shown.length} onClick={() => downloadCsv(shown, columns)}>Download CSV</button>
        <button type="button" className={s.primary} disabled={!shown.length} onClick={() => window.print()}>PDF report</button>
      </div>
    </div>
    <p className={s.count} role="status" aria-live="polite">Showing {shown.length} of {rows.length} submission{rows.length === 1 ? '' : 's'} · {newestFirst ? 'newest' : 'oldest'} first{query.trim() ? ' · CSV and PDF include only the rows shown' : ''}</p>

    <div className={s.tableWrap} role="region" aria-labelledby="admin-table-caption" tabIndex={0}>
      <table className={s.table}>
        <caption id="admin-table-caption" className={s.visuallyHidden}>Hospital survey submissions, one row per submission</caption>
        <thead><tr>
          <th scope="col" aria-sort={newestFirst ? 'descending' : 'ascending'} className={s.nowrap}><button type="button" className={s.sort} onClick={() => setNewestFirst(v => !v)}>Submitted <span aria-hidden="true">{newestFirst ? '↓' : '↑'}</span><span className={s.visuallyHidden}>, sorted {newestFirst ? 'newest first' : 'oldest first'}. Activate to reverse.</span></button></th>
          <th scope="col">Submitted by</th>
          {columns.map(c => <th scope="col" key={c.key}>{c.label}</th>)}
        </tr></thead>
        <tbody>{shown.map(r => {
          const role = SUBMITTER_COLUMNS[1].get(r), reg = registration(r), contact = [str(r.email), str(r.mobile)].filter(Boolean);
          return <tr key={r.id}>
            <th scope="row" className={s.nowrap}><time dateTime={r.created_at}>{pretty(r.created_at)}</time></th>
            <td className={s.who}><strong>{str(r.full_name) || 'Name not given'}</strong>{role && <span>{role}</span>}{reg && <span>{reg}</span>}{contact.length ? contact.map(c => <span key={c} className={s.contact}>{c}</span>) : <span className={s.muted}>No contact given</span>}</td>
            {columns.map(c => { const v = c.get(r); return <td key={c.key} className={c.wide ? s.wide : c.nowrap ? s.nowrap : undefined}>{v || <span className={s.muted}>—</span>}</td>; })}
          </tr>;
        })}</tbody>
      </table>
      {!shown.length && <p className={s.empty}>{rows.length ? 'No submissions match this search.' : 'No submissions yet.'}</p>}
    </div>
  </>;
}
