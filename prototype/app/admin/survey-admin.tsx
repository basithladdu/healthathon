'use client';
import { useEffect, useMemo, useRef, useState, type FormEvent, type MouseEvent, type ReactNode } from 'react';
import s from './admin.module.css';

type Row = { id: string; facility: Record<string, unknown>; created_at: string; full_name: string; respondent_role: string; council: string; registration_state: string; registration_number: string; mobile: string | null; email: string | null; revision: number; decision: string };

/* ---------- labels (mirroring app/feedback/HospitalSurvey.tsx) ---------- */
const ROLE: Record<string, string> = { oncologist: 'Oncologist', palliative_physician: 'Palliative-care physician', physician: 'Other physician', resident: 'Resident / postgraduate trainee', nurse: 'Nurse', pharmacist: 'Pharmacist', other: 'Other' };
const COUNCIL: Record<string, string> = { nmc: 'National Medical Commission (NMC)', state_medical: 'State medical council', state_nursing: 'State nursing council', state_pharmacy: 'State pharmacy council', other: 'Other council' };
const INSTITUTION: Record<string, string> = { government_hospital: 'Government hospital', government_medical_college: 'Government medical college', private_hospital: 'Private hospital', private_medical_college: 'Private medical college', private_clinic: 'Private clinic', ngo_hospice: 'NGO / trust / hospice', other: 'Other' };
const CONNECTION: Record<string, string> = { works_here: 'Works here', refers_here: 'Refers patients here', other: 'Other' };
const SERVICE: Record<string, string> = { opd: 'OPD', ipd: 'IPD', home: 'Home care', none: 'None', unknown: 'Don’t know' };
const SERVICE_LONG: Record<string, string> = { opd: 'Outpatient (OPD)', ipd: 'Inpatient (IPD)', home: 'Home care', none: 'None', unknown: 'Don’t know' };
const MORPHINE: Record<string, string> = { yes: 'Yes', no: 'No', unknown: 'Don’t know' };
const SUPPLY: Record<string, string> = { regular: 'Regular', intermittent: 'Intermittent', unknown: 'Don’t know' };
const DECISION: Record<string, string> = { pending_review: 'Pending review', approved: 'Approved', needs_clarification: 'Needs clarification', rejected: 'Rejected', withdrawn: 'Withdrawn' };

const str = (v: unknown) => (v === null || v === undefined ? '' : typeof v === 'string' ? v.trim() : typeof v === 'object' ? JSON.stringify(v) : String(v));
const label = (map: Record<string, string>, v: unknown) => { const k = str(v); return k ? map[k] ?? k : ''; };
const withOther = (map: Record<string, string>, v: unknown, other: unknown) => { const base = label(map, v); const extra = str(other); return str(v) === 'other' && extra ? `${base}: ${extra}` : base; };
const serviceKeys = (r: Row) => (Array.isArray(r.facility.services) ? r.facility.services : []).map(v => str(v)).filter(Boolean);
const services = (r: Row) => serviceKeys(r).map(v => label(SERVICE, v)).join(', ');
const hasPalliative = (r: Row) => serviceKeys(r).some(v => ['opd', 'ipd', 'home'].includes(v));
const hasMap = (r: Row) => Boolean(str(r.facility.latitude) && str(r.facility.longitude));
const mapUrl = (r: Row) => `https://www.google.com/maps?q=${encodeURIComponent(`${str(r.facility.latitude)},${str(r.facility.longitude)}`)}`;

/** India Standard Time, sortable: 2026-10-29 14:05 */
function ist(iso: string) { const t = Date.parse(iso); if (!Number.isFinite(t)) return iso; return new Date(t + 330 * 60000).toISOString().slice(0, 16).replace('T', ' '); }
function istDate() { return new Date(Date.now() + 330 * 60000).toISOString().slice(0, 10); }
const pretty = (iso: string) => { const t = Date.parse(iso); return Number.isFinite(t) ? new Date(t).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }) : iso; };
const prettyDay = (day: string) => { const t = Date.parse(`${day}T00:00:00Z`); return Number.isFinite(t) ? new Date(t).toLocaleDateString('en-IN', { dateStyle: 'medium', timeZone: 'UTC' }) : day; };

function registration(r: Row) { return [label(COUNCIL, r.council), str(r.registration_state), str(r.registration_number)].filter(Boolean).join(' · '); }

type Column = { key: string; label: string; get: (r: Row) => string; wide?: boolean; nowrap?: boolean; csvOnly?: boolean; render?: (r: Row) => ReactNode };
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
const roleOf = SUBMITTER_COLUMNS[1].get;

function MorphineBadge({ r }: { r: Row }) {
  const v = str(r.facility.morphine);
  if (!v) return <span className={s.muted}>—</span>;
  return <span className={`${s.badge} ${v === 'yes' ? s.badgeYes : v === 'no' ? s.badgeNo : s.badgeUnknown}`}>{label(MORPHINE, v)}</span>;
}
function ServiceTags({ r }: { r: Row }) {
  const keys = serviceKeys(r);
  if (!keys.length) return <span className={s.muted}>—</span>;
  return <span className={s.tags}>{keys.map(k => <span key={k} className={`${s.tag} ${['opd', 'ipd', 'home'].includes(k) ? s.tagOn : ''}`}>{label(SERVICE, k)}</span>)}</span>;
}

const FACILITY_COLUMNS: Column[] = [
  { key: 'hospitalName', label: 'Hospital', get: r => str(r.facility.hospitalName), wide: true },
  { key: 'institutionType', label: 'Institution type', get: r => withOther(INSTITUTION, r.facility.institutionType, r.facility.institutionOther) },
  { key: 'connection', label: 'Connection to hospital', get: r => withOther(CONNECTION, r.facility.connection, r.facility.connectionOther) },
  { key: 'state', label: 'State / UT', get: r => str(r.facility.state) },
  { key: 'city', label: 'City / district', get: r => str(r.facility.city) },
  { key: 'hospitalAddress', label: 'Address', get: r => str(r.facility.hospitalAddress), wide: true },
  { key: 'map', label: 'Map', get: r => (hasMap(r) ? 'Open map' : ''), render: r => <a href={mapUrl(r)} target="_blank" rel="noreferrer">Open map</a>, nowrap: true },
  { csvOnly: true, key: 'latitude', label: 'Latitude', get: r => str(r.facility.latitude), nowrap: true },
  { csvOnly: true, key: 'longitude', label: 'Longitude', get: r => str(r.facility.longitude), nowrap: true },
  { csvOnly: true, key: 'locationConfirmed', label: 'Pin confirmed', get: r => (r.facility.locationConfirmed === true ? 'Yes' : r.facility.locationConfirmed === false ? 'No' : '') },
  { key: 'services', label: 'Palliative services', get: services, render: r => <ServiceTags r={r} /> },
  { key: 'morphine', label: 'Oral morphine (last 3 months)', get: r => label(MORPHINE, r.facility.morphine), render: r => <MorphineBadge r={r} />, nowrap: true },
  { key: 'supply', label: 'Morphine supply', get: r => label(SUPPLY, r.facility.supply), nowrap: true },
  { key: 'palliativePhysicians', label: 'Palliative doctors / departments', get: r => str(r.facility.palliativePhysicians), wide: true },
  { key: 'familyNotes', label: 'Notes for families', get: r => str(r.facility.familyNotes), wide: true },
];
const col = (key: string) => FACILITY_COLUMNS.find(c => c.key === key)!;
/* Table order after the sticky Hospital column; name, type, city and state sit inside that first cell. */
const TABLE_KEYS = ['services', 'morphine', 'supply', 'connection', 'palliativePhysicians', 'familyNotes', 'hospitalAddress', 'map'];
const META_COLUMNS: Column[] = [
  // Kept in the CSV; too technical for the table.
  { csvOnly: true, key: 'decision', label: 'Review status', get: r => label(DECISION, r.decision) },
  { csvOnly: true, key: 'revision', label: 'Review revision', get: r => str(r.revision) },
  { csvOnly: true, key: 'formVersion', label: 'Form version', get: r => str(r.facility.formVersion), nowrap: true },
  { csvOnly: true, key: 'id', label: 'Submission ID', get: r => str(r.id), nowrap: true },
];
/* Keys already shown, or internal/anti-spam fields not worth a column. */
const KNOWN = new Set(['hospitalName', 'institutionType', 'institutionOther', 'connection', 'connectionOther', 'state', 'city', 'hospitalAddress', 'latitude', 'longitude', 'locationConfirmed', 'services', 'morphine', 'supply', 'palliativePhysicians', 'familyNotes', 'formVersion', 'roleOther', 'kind', 'website', 'submissionId', 'consent', 'accuracy', 'mobile']);
const humanise = (k: string) => k.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());

/* ---------- filters ---------- */
type Filters = { state: string; morphine: string; service: string; institution: string; role: string; from: string; to: string };
type FilterKey = keyof Filters;
const NO_FILTERS: Filters = { state: '', morphine: '', service: '', institution: '', role: '', from: '', to: '' };
const BLANK = '__blank';
const ANY_PALLIATIVE = '__any';
const dayOf = (r: Row) => ist(r.created_at).slice(0, 10);

function matches(r: Row, f: Filters) {
  const eq = (want: string, have: string) => !want || (want === BLANK ? !have : have === want);
  if (!eq(f.state, str(r.facility.state))) return false;
  if (!eq(f.morphine, str(r.facility.morphine))) return false;
  if (!eq(f.institution, str(r.facility.institutionType))) return false;
  if (!eq(f.role, str(r.respondent_role))) return false;
  if (f.service) {
    const keys = serviceKeys(r);
    if (f.service === ANY_PALLIATIVE ? !hasPalliative(r) : f.service === BLANK ? keys.length > 0 : !keys.includes(f.service)) return false;
  }
  const day = dayOf(r);
  if (f.from && day < f.from) return false;
  if (f.to && day > f.to) return false;
  return true;
}

type Option = { value: string; label: string; count: number };
/** Options for one filter, with how many submissions have each answer. Fixed lists keep their survey order. */
function options(rows: Row[], keysOf: (r: Row) => string[], map: Record<string, string> | null, keepAll: boolean): Option[] {
  const counts = new Map<string, number>();
  rows.forEach(r => { const keys = keysOf(r); (keys.length ? keys : [BLANK]).forEach(k => counts.set(k, (counts.get(k) ?? 0) + 1)); });
  const known = map ? Object.keys(map) : [];
  const others = [...counts.keys()].filter(k => k !== BLANK && !known.includes(k)).sort((a, b) => a.localeCompare(b));
  const list = [...known, ...others].filter(k => keepAll || counts.has(k)).map(k => ({ value: k, label: map?.[k] ?? k, count: counts.get(k) ?? 0 }));
  if (counts.has(BLANK)) list.push({ value: BLANK, label: 'Not answered', count: counts.get(BLANK)! });
  return list;
}
const single = (get: (r: Row) => string) => (r: Row) => { const v = get(r); return v ? [v] : []; };

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

/* ---------- detail panel ---------- */
function Answer({ name, children }: { name: string; children: ReactNode }) {
  return <div className={s.answer}><dt>{name}</dt><dd>{children || <span className={s.muted}>Not answered</span>}</dd></div>;
}
function Details({ r, extra, position, onPrev, onNext, onClose }: { r: Row; extra: Column[]; position: string; onPrev?: () => void; onNext?: () => void; onClose: () => void }) {
  const f = r.facility;
  const pin = f.locationConfirmed === true ? 'Pin confirmed by the submitter' : f.locationConfirmed === false ? 'Pin not confirmed' : '';
  const email = str(r.email), mobile = str(r.mobile);
  const filledExtra = extra.filter(c => c.get(r));
  return <div className={s.drawerInner}>
    <div className={s.drawerHead}>
      <div className={s.drawerTitle}>
        <p className={s.eyebrow}>{position}</p>
        <h2 id="admin-detail-title">{str(f.hospitalName) || 'Hospital name not given'}</h2>
        <p>{[col('institutionType').get(r), str(f.city), str(f.state)].filter(Boolean).join(' · ')}</p>
      </div>
      <button type="button" className={s.close} onClick={onClose} aria-label="Close details"><span aria-hidden="true">×</span></button>
    </div>
    <div className={s.drawerBody} data-drawer-body>
      <section className={s.detailSection} aria-labelledby="d-hospital">
        <h3 id="d-hospital">Hospital</h3>
        <dl>
          <Answer name="Hospital name">{str(f.hospitalName)}</Answer>
          <Answer name="Institution type">{col('institutionType').get(r)}</Answer>
          <Answer name="Submitter’s connection">{col('connection').get(r)}</Answer>
          <Answer name="State / UT">{str(f.state)}</Answer>
          <Answer name="City / district">{str(f.city)}</Answer>
          <Answer name="Address">{str(f.hospitalAddress)}</Answer>
          <Answer name="Location">{hasMap(r) ? <><a href={mapUrl(r)} target="_blank" rel="noreferrer">Open in Google Maps</a><span className={s.small}>{str(f.latitude)}, {str(f.longitude)}{pin ? ` · ${pin}` : ''}</span></> : ''}</Answer>
        </dl>
      </section>
      <section className={s.detailSection} aria-labelledby="d-services">
        <h3 id="d-services">Palliative care and morphine</h3>
        <dl>
          <Answer name="Palliative services">{serviceKeys(r).length ? serviceKeys(r).map(k => label(SERVICE_LONG, k)).join(', ') : ''}</Answer>
          <Answer name="Oral morphine in the last 3 months">{str(f.morphine) ? <MorphineBadge r={r} /> : ''}</Answer>
          <Answer name="Morphine supply">{label(SUPPLY, f.supply)}</Answer>
          <Answer name="Palliative doctors / departments"><span className={s.prose}>{str(f.palliativePhysicians)}</span></Answer>
          <Answer name="Notes for families"><span className={s.prose}>{str(f.familyNotes)}</span></Answer>
        </dl>
      </section>
      {filledExtra.length > 0 && <section className={s.detailSection} aria-labelledby="d-other">
        <h3 id="d-other">Other answers</h3>
        <dl>{filledExtra.map(c => <Answer key={c.key} name={c.label}><span className={s.prose}>{c.get(r)}</span></Answer>)}</dl>
      </section>}
      <section className={`${s.detailSection} ${s.private}`} aria-labelledby="d-person">
        <h3 id="d-person">Submitted by <span className={s.privateTag}>Private</span></h3>
        <dl>
          <Answer name="Name">{str(r.full_name)}</Answer>
          <Answer name="Role">{roleOf(r)}</Answer>
          <Answer name="Council">{label(COUNCIL, r.council)}</Answer>
          <Answer name="Registration state">{str(r.registration_state)}</Answer>
          <Answer name="Registration number">{str(r.registration_number)}</Answer>
          <Answer name="Email">{email ? <a href={`mailto:${email}`}>{email}</a> : ''}</Answer>
          <Answer name="Mobile">{mobile ? <a href={`tel:${mobile.replace(/[^\d+]/g, '')}`}>{mobile}</a> : ''}</Answer>
        </dl>
      </section>
      <section className={s.detailSection} aria-labelledby="d-record">
        <h3 id="d-record">Record</h3>
        <dl>
          <Answer name="Submitted">{pretty(r.created_at)} IST</Answer>
          <Answer name="Submission ID"><span className={s.code}>{str(r.id)}</span></Answer>
          <Answer name="Form version">{str(f.formVersion)}</Answer>
        </dl>
      </section>
    </div>
    <div className={s.drawerFoot}>
      <button type="button" className={s.secondary} disabled={!onPrev} onClick={onPrev}>← Previous</button>
      <button type="button" className={s.secondary} disabled={!onNext} onClick={onNext}>Next →</button>
    </div>
  </div>;
}

type Phase = 'locked' | 'loading' | 'failed' | 'ready';
type Sort = { by: 'date' | 'hospital'; desc: boolean };
const SAVED = 'sahara-admin-password';
const saved = () => { try { return window.localStorage.getItem(SAVED) ?? ''; } catch { return ''; } };

export function SurveyAdmin() {
  const [phase, setPhase] = useState<Phase>('locked'); const [password, setPassword] = useState('');
  const [rows, setRows] = useState<Row[]>([]); const [busy, setBusy] = useState(false); const [message, setMessage] = useState('');
  const [loadedAt, setLoadedAt] = useState('');
  const [query, setQuery] = useState(''); const [sort, setSort] = useState<Sort>({ by: 'date', desc: true });
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [openId, setOpenId] = useState<string | null>(null);
  const live = useRef(true);
  const dialog = useRef<HTMLDialogElement>(null);

  async function load(code = password) {
    if (busy || !code) return; setBusy(true); setMessage(''); setPhase(p => (p === 'ready' ? p : 'loading'));
    try {
      const response = await fetch('/api/admin-survey', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: code }), cache: 'no-store' });
      const value = await response.json().catch(() => ({})) as { rows?: Row[]; error?: string };
      if (!live.current) return;
      if (response.status === 401) { try { window.localStorage.removeItem(SAVED); } catch { /* ignore */ } setPassword(''); setPhase('locked'); setMessage('Wrong password.'); return; }
      try { window.localStorage.setItem(SAVED, code); } catch { /* ignore */ } setPassword(code);
      if (!response.ok || !Array.isArray(value.rows)) throw new Error(value.error ?? 'Could not load the survey submissions.');
      setRows(value.rows.map(r => ({ ...r, facility: r.facility && typeof r.facility === 'object' ? r.facility : {} }))); setLoadedAt(new Date().toISOString()); setPhase('ready');
      setMessage(value.rows.length ? '' : 'No survey submissions yet.');
    } catch (e) { if (live.current) { setMessage(e instanceof Error ? e.message : 'Could not load the survey submissions.'); setPhase(p => (p === 'ready' ? p : 'failed')); } }
    finally { if (live.current) setBusy(false); }
  }
  useEffect(() => { live.current = true; const start = setTimeout(() => { const code = saved(); if (code) void load(code); }, 0); return () => { live.current = false; clearTimeout(start); }; }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const extraColumns = useMemo<Column[]>(() => {
    const keys = new Set<string>();
    rows.forEach(r => Object.keys(r.facility).forEach(k => { if (!KNOWN.has(k)) keys.add(k); }));
    return [...keys].sort().map(k => ({ key: `x:${k}`, label: humanise(k), get: (r: Row) => str(r.facility[k]), wide: true }));
  }, [rows]);
  // CSV keeps every column; the table hides technical columns and any column nobody has filled in yet.
  const columns = useMemo(() => [...FACILITY_COLUMNS, ...extraColumns, ...META_COLUMNS].filter(c => c.key !== 'map'), [extraColumns]);
  const tableColumns = useMemo(() => [...TABLE_KEYS.map(col), ...extraColumns].filter(c => rows.some(r => c.get(r))), [rows, extraColumns]);

  const filterOptions = useMemo(() => ({
    state: options(rows, single(r => str(r.facility.state)), null, false),
    morphine: options(rows, single(r => str(r.facility.morphine)), MORPHINE, true),
    service: [{ value: ANY_PALLIATIVE, label: 'Any of OPD, IPD or home care', count: rows.filter(hasPalliative).length }, ...options(rows, serviceKeys, SERVICE_LONG, true)],
    institution: options(rows, single(r => str(r.facility.institutionType)), INSTITUTION, false),
    role: options(rows, single(r => str(r.respondent_role)), ROLE, false),
  }), [rows]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter(r => matches(r, filters) && (!q || [pretty(r.created_at), ist(r.created_at), ...SUBMITTER_COLUMNS.map(c => c.get(r)), ...columns.map(c => c.get(r))].join('\u0000').toLowerCase().includes(q)));
  }, [rows, filters, query, columns]);
  const shown = useMemo(() => [...filtered].sort((a, b) => {
    const d = sort.by === 'hospital'
      ? str(a.facility.hospitalName).localeCompare(str(b.facility.hospitalName), 'en', { sensitivity: 'base' }) || (Date.parse(b.created_at) || 0) - (Date.parse(a.created_at) || 0)
      : (Date.parse(a.created_at) || 0) - (Date.parse(b.created_at) || 0);
    return sort.desc ? -d : d;
  }), [filtered, sort]);

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

  /* Active filters as removable chips. */
  const FILTER_NAMES: Record<Exclude<FilterKey, 'from' | 'to'>, string> = { state: 'State / UT', morphine: 'Oral morphine', service: 'Palliative services', institution: 'Institution', role: 'Submitted by' };
  const chips: { key: FilterKey | 'query'; text: string }[] = [];
  (Object.keys(FILTER_NAMES) as (keyof typeof FILTER_NAMES)[]).forEach(k => { const v = filters[k]; if (v) chips.push({ key: k, text: `${FILTER_NAMES[k]}: ${filterOptions[k].find(o => o.value === v)?.label ?? v}` }); });
  if (filters.from) chips.push({ key: 'from', text: `From ${prettyDay(filters.from)}` });
  if (filters.to) chips.push({ key: 'to', text: `Up to ${prettyDay(filters.to)}` });
  if (query.trim()) chips.push({ key: 'query', text: `Search: “${query.trim()}”` });
  const narrowed = chips.length > 0;
  const setFilter = (k: FilterKey, v: string) => setFilters(f => ({ ...f, [k]: v }));
  const removeChip = (k: FilterKey | 'query') => (k === 'query' ? setQuery('') : setFilter(k, ''));
  const clearAll = () => { setFilters(NO_FILTERS); setQuery(''); };

  /* Detail panel (native modal dialog: Esc closes, focus returns to the row). */
  const openIndex = openId ? shown.findIndex(r => r.id === openId) : -1;
  const openRow = openIndex >= 0 ? shown[openIndex] : null;
  useEffect(() => {
    const d = dialog.current; if (!d) return;
    if (openRow && !d.open) d.showModal();
    if (!openRow && d.open) d.close();
  }, [openRow]);
  useEffect(() => { dialog.current?.querySelector('[data-drawer-body]')?.scrollTo?.(0, 0); }, [openId]);
  function rowClick(e: MouseEvent<HTMLTableRowElement>, id: string) {
    if ((e.target as HTMLElement).closest('a,button')) return;
    if (window.getSelection()?.toString()) return; // let people select text without opening the panel
    setOpenId(id);
  }

  const header = <div className={s.toolbarTop}>
    <div><h1 className={s.title}>Hospital survey responses</h1><p className={s.lead}>Every submission from the hospital survey. Use the filters to narrow the list; the summary, CSV and PDF follow what you see.</p></div>
  </div>;

  if (phase === 'locked') return <>{header}<form className={`${s.card} ${s.form}`} onSubmit={(e: FormEvent) => { e.preventDefault(); void load(password); }}>
    <label className={s.field} htmlFor="admin-password">Password<input id="admin-password" type="password" autoComplete="off" value={password} onChange={e => { setPassword(e.target.value); setMessage(''); }} required /></label>
    {message && <p className={s.error} role="alert">{message}</p>}
    <button className={s.primary} type="submit" disabled={busy}>Open</button>
  </form></>;
  if (phase === 'loading') return <>{header}<p className={s.status} role="status">Loading survey submissions…</p></>;
  if (phase === 'failed') return <>{header}<p className={s.notice} role="alert">{message}</p><button type="button" className={s.secondary} disabled={busy} onClick={() => void load()}>Try again</button></>;

  const stat = (n: number, text: ReactNode, tone?: string) => <div className={`${s.stat} ${tone ?? ''}`}><strong>{n}</strong><span>{text}</span></div>;
  const select = (k: Exclude<FilterKey, 'from' | 'to'>, name: string, any: string) => <label className={s.filter} htmlFor={`f-${k}`}>{name}
    <select id={`f-${k}`} value={filters[k]} onChange={e => setFilter(k, e.target.value)} data-active={filters[k] ? 'true' : undefined}>
      <option value="">{any}</option>
      {filterOptions[k].map(o => <option key={o.value} value={o.value}>{o.label} ({o.count})</option>)}
    </select>
  </label>;
  const sortButton = (by: Sort['by'], text: string) => {
    const active = sort.by === by;
    return <button type="button" className={s.sort} onClick={() => setSort(v => (v.by === by ? { by, desc: !v.desc } : { by, desc: by === 'date' }))}>
      {text} <span aria-hidden="true" className={active ? undefined : s.sortIdle}>{active ? (sort.desc ? '↓' : '↑') : '↕'}</span>
      <span className={s.visuallyHidden}>{active ? `, sorted ${by === 'date' ? (sort.desc ? 'newest first' : 'oldest first') : (sort.desc ? 'Z to A' : 'A to Z')}` : ''}. Activate to sort.</span>
    </button>;
  };
  const ariaSort = (by: Sort['by']) => (sort.by === by ? (sort.desc ? 'descending' : 'ascending') : undefined);
  const sortText = sort.by === 'date' ? (sort.desc ? 'newest first' : 'oldest first') : (sort.desc ? 'hospital Z to A' : 'hospital A to Z');

  return <>
    {header}
    <div className={s.printOnly}><p className={s.reportMeta}>Report generated {pretty(new Date().toISOString())} IST{loadedAt ? ` · data loaded ${pretty(loadedAt)} IST` : ''} · {shown.length} of {rows.length} submissions{narrowed ? ` · filters: ${chips.map(c => c.text).join('; ')}` : ''}</p></div>
    {message && <p className={s.status} role="status">{message}</p>}

    <section className={s.summary} aria-labelledby="admin-summary-title">
      <h2 id="admin-summary-title" className={s.subtitle}>Summary{narrowed ? <span className={s.subNote}> · filtered</span> : null}</h2>
      <div className={s.stats}>
        {stat(summary.total, narrowed ? `Submissions shown (of ${rows.length})` : 'Total submissions', s.statMain)}
        {stat(summary.palliative, 'Have palliative services (OPD, IPD or home care)')}
        {stat(summary.morphine.yes, 'Oral morphine: yes', s.statYes)}
        {stat(summary.morphine.no, 'Oral morphine: no', s.statNo)}
        {stat(summary.morphine.unknown, 'Oral morphine: don’t know')}
        {summary.morphine.blank ? stat(summary.morphine.blank, 'Oral morphine: not answered') : null}
      </div>
      {summary.byState.length > 0 && <details className={s.states} open>
        <summary>By state / UT ({summary.byState.length})</summary>
        <ul>{summary.byState.map(([st, n]) => {
          const value = st === 'Not specified' ? BLANK : st;
          return <li key={st}><button type="button" className={s.stateButton} aria-pressed={filters.state === value} title={`Show only ${st}`} onClick={() => setFilter('state', filters.state === value ? '' : value)}><span>{st}</span><strong>{n}</strong></button></li>;
        })}</ul>
      </details>}
    </section>

    <section className={`${s.filters} ${s.noPrint}`} aria-labelledby="admin-filters-title">
      <div className={s.filtersHead}>
        <h2 id="admin-filters-title" className={s.subtitle}>Filters</h2>
        <div className={s.actions}>
          <button type="button" className={s.secondary} disabled={busy} onClick={() => void load()}>{busy ? 'Refreshing…' : 'Refresh'}</button>
          <button type="button" className={s.secondary} disabled={!shown.length} onClick={() => downloadCsv(shown, columns)}>Download CSV</button>
          <button type="button" className={s.primary} disabled={!shown.length} onClick={() => window.print()}>PDF report</button>
        </div>
      </div>
      <label className={s.search} htmlFor="admin-search">Search
        <input id="admin-search" type="search" value={query} placeholder="Hospital, city, name, email, notes…" onChange={e => setQuery(e.target.value)} />
      </label>
      <div className={s.filterGrid}>
        {select('state', 'State / UT', 'All states')}
        {select('morphine', 'Oral morphine (last 3 months)', 'Any answer')}
        {select('service', 'Palliative services', 'Any answer')}
        {select('institution', 'Institution type', 'All types')}
        {select('role', 'Submitted by (role)', 'All roles')}
        <div className={s.dates} role="group" aria-labelledby="f-dates">
          <span id="f-dates" className={s.datesLabel}>Submitted between</span>
          <div className={s.dateRow}>
            <label htmlFor="f-from"><span className={s.visuallyHidden}>From date</span><input id="f-from" type="date" value={filters.from} max={filters.to || undefined} onChange={e => setFilter('from', e.target.value)} data-active={filters.from ? 'true' : undefined} /></label>
            <span aria-hidden="true">–</span>
            <label htmlFor="f-to"><span className={s.visuallyHidden}>To date</span><input id="f-to" type="date" value={filters.to} min={filters.from || undefined} onChange={e => setFilter('to', e.target.value)} data-active={filters.to ? 'true' : undefined} /></label>
          </div>
        </div>
      </div>
      {narrowed && <div className={s.chips} aria-label="Active filters">
        {chips.map(c => <button key={c.key} type="button" className={s.chip} onClick={() => removeChip(c.key)}>{c.text}<span aria-hidden="true" className={s.chipX}>×</span><span className={s.visuallyHidden}> (remove)</span></button>)}
        <button type="button" className={s.clear} onClick={clearAll}>Clear all</button>
      </div>}
    </section>

    <p className={s.count} role="status" aria-live="polite">
      <strong>Showing {shown.length} of {rows.length}</strong> submission{rows.length === 1 ? '' : 's'} · {sortText}
      <span className={s.hint}> · Tap a row to see every answer</span>
    </p>

    <div className={s.tableWrap} role="region" aria-labelledby="admin-table-caption" tabIndex={0}>
      <table className={s.table}>
        <caption id="admin-table-caption" className={s.visuallyHidden}>Hospital survey submissions, one row per submission. Choose a hospital name to see every answer.</caption>
        <thead><tr>
          <th scope="col" className={s.stickyCol} aria-sort={ariaSort('hospital')}>{sortButton('hospital', 'Hospital')}</th>
          <th scope="col" aria-sort={ariaSort('date')}>{sortButton('date', 'Submitted')}</th>
          <th scope="col">Submitted by</th>
          {tableColumns.map(c => <th scope="col" key={c.key}>{c.label}</th>)}
        </tr></thead>
        <tbody>{shown.map(r => {
          const name = str(r.facility.hospitalName), place = [str(r.facility.city), str(r.facility.state)].filter(Boolean).join(', ');
          const type = col('institutionType').get(r), role = roleOf(r), reg = registration(r), contact = [str(r.email), str(r.mobile)].filter(Boolean);
          return <tr key={r.id} className={s.row} onClick={e => rowClick(e, r.id)}>
            <th scope="row" className={`${s.stickyCol} ${s.hospital}`}>
              <button type="button" className={s.hospitalName} onClick={() => setOpenId(r.id)}>{name || <span className={s.muted}>Name not given</span>}</button>
              {place && <span className={s.place}>{place}</span>}
              {type && <span className={s.type}>{type}</span>}
            </th>
            <td className={s.nowrap}><time dateTime={r.created_at}>{pretty(r.created_at)}</time></td>
            <td className={s.who}>
              {str(r.full_name) ? <strong>{str(r.full_name)}</strong> : <span className={s.muted}>No name given</span>}
              {role && <span>{role}</span>}
              {reg && <span className={s.printOnlyInline}>{reg}</span>}
              {contact.map(c => <span key={c} className={s.printOnlyInline}>{c}</span>)}
            </td>
            {tableColumns.map(c => { const v = c.get(r); return <td key={c.key} className={c.wide ? s.wide : c.nowrap ? s.nowrap : undefined}>{v ? (c.render ? c.render(r) : v) : <span className={s.muted}>—</span>}</td>; })}
          </tr>;
        })}</tbody>
      </table>
      {!shown.length && <div className={s.empty}>
        <p>{rows.length ? 'No submissions match these filters.' : 'No submissions yet.'}</p>
        {narrowed && <button type="button" className={s.secondary} onClick={clearAll}>Clear all filters</button>}
      </div>}
    </div>

    <dialog ref={dialog} className={`${s.drawer} ${s.noPrint}`} aria-labelledby="admin-detail-title" onClose={() => setOpenId(null)} onClick={e => { if (e.target === e.currentTarget) setOpenId(null); }}>
      {openRow && <Details r={openRow} extra={extraColumns} position={`Submission ${openIndex + 1} of ${shown.length}`}
        onPrev={openIndex > 0 ? () => setOpenId(shown[openIndex - 1].id) : undefined}
        onNext={openIndex < shown.length - 1 ? () => setOpenId(shown[openIndex + 1].id) : undefined}
        onClose={() => setOpenId(null)} />}
    </dialog>
  </>;
}
