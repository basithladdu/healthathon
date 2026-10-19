'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { isSharedId, type CareSharedClient } from './care-shared-client';

type RequestRow = { user_id: string; name: string; council: string; registration: string; status: string; revision: number; submitted_at: string; reviewed_by?: string; reviewed_at?: string; review_note?: string };
type Decision = 'approved' | 'rejected' | 'suspended';
function validRow(value: unknown): value is RequestRow {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const row = value as Record<string, unknown>;
  return isSharedId(row.user_id) && typeof row.name === 'string' && row.name.length <= 100
    && typeof row.council === 'string' && row.council.length <= 160 && typeof row.registration === 'string' && row.registration.length <= 80
    && ['pending', 'approved', 'rejected', 'suspended'].includes(String(row.status))
    && Number.isSafeInteger(row.revision) && Number(row.revision) >= 1 && typeof row.submitted_at === 'string';
}
const reason = (error: unknown) => error instanceof Error ? error.message : 'The review could not finish. Your evidence and reason are retained.';

export function CareClinicianReview({ client }: { client: CareSharedClient }) {
  const account = client.currentSession()?.userId;
  return account ? <Verifier key={account} client={client} account={account} /> : <p>Sign in with your authorised verifier account.</p>;
}

function Verifier({ client, account }: { client: CareSharedClient; account: string }) {
  const [allowed, setAllowed] = useState<boolean | null>(null), [rows, setRows] = useState<RequestRow[]>([]), [busy, setBusy] = useState(true), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [filter, setFilter] = useState('pending'), [offset, setOffset] = useState(0), [pageCount, setPageCount] = useState(0);
  const generation = useRef(0), mounted = useRef(true);
  const load = useCallback(async (selectedFilter: string, selectedOffset: number) => {
    if (!['pending', 'approved', 'rejected', 'suspended'].includes(selectedFilter) || !Number.isSafeInteger(selectedOffset) || selectedOffset < 0) return;
    const current = ++generation.current; setBusy(true); setError('');
    try {
      const role = await client.rpc<unknown>('healthathon_staff_role', { p_role: 'clinical_verifier' });
      if (!mounted.current || current !== generation.current || client.currentSession()?.userId !== account) return;
      if (role !== true) { setAllowed(false); setRows([]); return; }
      setAllowed(true);
      const value = await client.request<unknown>(`/rest/v1/healthathon_clinician_requests?status=eq.${selectedFilter}&order=submitted_at.asc&limit=100&offset=${selectedOffset}`);
      if (!mounted.current || current !== generation.current || client.currentSession()?.userId !== account) return;
      if (!Array.isArray(value) || !value.every(validRow)) throw new Error('The verification queue could not be checked. Refresh before making a decision.');
      setRows(value); setPageCount(value.length); setFilter(selectedFilter); setOffset(selectedOffset);
    } catch (cause) { if (mounted.current && current === generation.current) setError(reason(cause)); }
    finally { if (mounted.current && current === generation.current) setBusy(false); }
  }, [client, account]);
  useEffect(() => {
    mounted.current = true;
    // Async role lookup is authoritative; display-role or user metadata never grants verifier access.
    void Promise.resolve().then(() => load('pending', 0));
    return () => { mounted.current = false; generation.current += 1; };
  }, [load]);
  if (allowed === false) return <section className="shared-toolbox"><h3>Clinician request review</h3><p>Only an independently authorised verification account can review professional registrations. This account has no verifier role.</p></section>;
  return <section className="shared-toolbox"><div className="shared-row"><h3>Clinician request review</h3><button type="button" className="secondary" disabled={busy} onClick={() => load(filter, offset)}>{busy ? 'Checking queue…' : 'Refresh queue'}</button></div>
    <p className="shared-hint">Check the current public medical register yourself. A registration number, typed name or selected doctor role does not establish identity. You cannot review your own request.</p>
    {allowed === true && <label>Request status<select value={filter} disabled={busy} onChange={(event) => load(event.target.value, 0)}>{['pending', 'approved', 'rejected', 'suspended'].map((status) => <option key={status}>{status}</option>)}</select></label>}
    {error && <p className="shared-inline-error" role="alert">{error}</p>}{notice && <p className="shared-banner" role="status">{notice}</p>}
    {allowed === null && <p role="status">Checking the account’s verification permission…</p>}
    {allowed === true && !busy && rows.length === 0 && <p>No {filter} requests on this page.</p>}
    {allowed === true && rows.map((row) => <ReviewRequest key={`${account}:${row.user_id}`} row={row} client={client} account={account} disabled={busy} onSaved={async (decision) => { setNotice(`Registration review recorded as ${decision}.`); await load(filter, offset); }} />)}
    {allowed === true && <div className="shared-row"><button type="button" className="quiet" disabled={busy || offset === 0} onClick={() => load(filter, Math.max(0, offset - 100))}>Previous page</button><span className="shared-hint">Page {Math.floor(offset / 100) + 1} · {rows.length} requests shown</span><button type="button" className="quiet" disabled={busy || pageCount < 100} onClick={() => load(filter, offset + 100)}>Next page</button></div>}
  </section>;
}

function ReviewRequest({ row, client, account, disabled, onSaved }: { row: RequestRow; client: CareSharedClient; account: string; disabled: boolean; onSaved: (decision: Decision) => Promise<void> }) {
  const [evidence, setEvidence] = useState(''), [note, setNote] = useState(''), [reviewedRevision, setReviewedRevision] = useState<number | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const mounted = useRef(true), saving = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function decide(decision: Decision) {
    if (saving.current || busy || disabled || account === row.user_id || reviewedRevision !== row.revision) return;
    if (evidence.trim().length < 8 || note.trim().length < 8) { setError('Add the checked register/source reference and a clear review reason (at least 8 characters each).'); return; }
    const revision = row.revision; const person = row.user_id; saving.current = true; setBusy(true); setError('');
    try {
      if (client.currentSession()?.userId !== account) throw new Error('Your account changed. Sign in with the authorised verifier account.');
      const receipt = await client.rpc<unknown>('healthathon_review_clinician', { p_user: person, p_revision: revision, p_decision: decision, p_evidence: evidence.trim(), p_note: note.trim() });
      if (!mounted.current || client.currentSession()?.userId !== account) return;
      if (!validRow(receipt) || receipt.user_id !== person || receipt.revision !== revision + 1 || receipt.status !== decision || receipt.reviewed_by !== account
        || receipt.review_note !== note.trim() || typeof receipt.reviewed_at !== 'string' || !Number.isFinite(Date.parse(receipt.reviewed_at))) throw new Error('The review receipt did not match this account and request. Refresh the queue before retrying; your evidence is retained.');
      setReviewedRevision(null); await onSaved(decision);
    } catch (cause) { if (mounted.current) setError(reason(cause)); }
    finally { saving.current = false; if (mounted.current) setBusy(false); }
  }
  return <article className="shared-card"><h4>{row.name}</h4><p>{row.council} · {row.registration}</p><p className="shared-meta">Status: {row.status} · revision {row.revision} · submitted {new Date(row.submitted_at).toLocaleString('en-IN')}</p>
    {row.user_id === account ? <p className="shared-banner">An independent verifier must review your own registration.</p> : <><label>Checked public register URL or source reference<input value={evidence} minLength={8} maxLength={1200} disabled={busy || disabled} onChange={(event) => { setEvidence(event.target.value); setReviewedRevision(null); }} /></label><label>Review reason<textarea value={note} minLength={8} maxLength={2000} rows={3} disabled={busy || disabled} onChange={(event) => { setNote(event.target.value); setReviewedRevision(null); }} /></label><label className="check"><input type="checkbox" checked={reviewedRevision === row.revision} disabled={busy || disabled} onChange={(event) => setReviewedRevision(event.target.checked ? row.revision : null)} />I independently checked this request’s current name, council, registration and evidence. The decision records my review; it is not an automatic identity match.</label><div className="shared-row">{(['approved', 'rejected', 'suspended'] as const).map((decision) => <button type="button" className={decision === 'approved' ? '' : 'secondary'} key={decision} disabled={busy || disabled || reviewedRevision !== row.revision || evidence.trim().length < 8 || note.trim().length < 8} onClick={() => decide(decision)}>{busy ? 'Recording decision…' : decision === 'approved' ? 'Approve registration' : decision === 'rejected' ? 'Reject request' : 'Suspend registration'}</button>)}</div></>}
    {error && <p className="shared-inline-error" role="alert">{error}</p>}
  </article>;
}
