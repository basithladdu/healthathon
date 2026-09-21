'use client';

import { useId, useState } from 'react';
import { approveVisitNote } from './visit-note-state';
import { latestSummaryRelease, type DraftFieldKey, type SummaryState } from './summary-state';
import { CARE_NOTE_LABELS } from './doctor-conversation-state';
import { IconCheckCircle, IconClock } from './icons';

export function DoctorNoteReview({ patientId, patientName, patients, state, physician, onPatient, onFields, onApprove, onRecord, onEdit, onHistory, onFamily }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; state: SummaryState; physician: string;
  onPatient: (id: string) => void; onFields: (fields: Record<DraftFieldKey, string>) => void;
  onApprove: (permission: string, reviewed: boolean, signature: string) => void;
  onRecord: () => void; onEdit: () => void; onHistory: () => void; onFamily: () => void;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [permission, setPermission] = useState('Pending patient review');
  const [reviewed, setReviewed] = useState(false);
  const [signature, setSignature] = useState('');
  const release = latestSummaryRelease(state);
  const ready = signature.trim().toLowerCase() === physician.trim().toLowerCase() && approveVisitNote(state, { treatingPhysician: true, physician, permission, reviewed, signatureName: signature, releasedAt: new Date().toISOString() }) !== state;
  const fields = state.versionPublished && release?.fields ? release.fields : state.draftFields;
  return <section className="doctor-note-review">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1>{state.versionPublished ? (release?.signature ? 'Care note signed' : 'Care note approved') : 'Review & sign'}</h1></div><div className="care-note-top-actions"><select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((patient) => <option key={patient.id} value={patient.id}>{patient.name}</option>)}</select><button type="button" className="primary-button" onClick={onRecord}>+ Record conversation</button></div></header>
    <div className="doctor-note-steps"><button type="button" onClick={onRecord}><span>1</span> Conversation</button><strong><span>2</span> Review & sign</strong><button type="button" onClick={onHistory}><IconClock /> Note history</button></div>
    {state.versionPublished ? <div className="doctor-signed-result"><IconCheckCircle /><div><h2>{release?.signature ? 'Signed by' : 'Approved by'} {release?.signature?.name ?? release?.physician}</h2><p>{release ? new Date(release.releasedAt).toLocaleString('en-GB') : ''} · Version {release?.number}</p></div><button className="primary-button" type="button" onClick={onFamily}>Open approved note →</button><button className="secondary-button" type="button" onClick={onHistory}>All notes</button></div> : !state.draftSource.trim() ? <div className="care-note-empty"><h2>Start with the conversation</h2><button className="primary-button" type="button" onClick={onRecord}>Record or type a note</button></div> : <>
      <div className="doctor-review-workbench"><section className="doctor-source-card"><div className="family-section-heading"><h2>Conversation</h2><button type="button" className="care-text-button" onClick={onEdit}>Edit note</button></div><p>{state.draftSource}</p></section>
        <section className="doctor-family-draft"><div className="family-section-heading"><h2>Family’s copy</h2><button className="care-text-button" type="button" onClick={() => setEditing(!editing)}>{editing ? 'Done editing' : 'Edit sections'}</button></div><div className="doctor-draft-fields">{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => {
          const value = fields[key]; const empty = !value || value === 'Not stated in this conversation.';
          return editing ? <label key={key}>{CARE_NOTE_LABELS[key]}<textarea rows={3} value={empty ? '' : value} onChange={(event) => { onFields({ ...fields, [key]: event.target.value }); setReviewed(false); }} /></label> : empty ? null : <section key={key} className={'is-' + key}><h3>{CARE_NOTE_LABELS[key]}</h3><p>{value}</p></section>;
        })}</div></section></div>
      <form className="doctor-sign-panel" onSubmit={(event) => { event.preventDefault(); if (ready && !editing) onApprove(permission, reviewed, signature); }}>
        <div><h2>Sign the care note</h2><label htmlFor={id + '-permission'}>Permission to share<select id={id + '-permission'} value={permission} onChange={(event) => setPermission(event.target.value)}><option value="Pending patient review">Choose permission</option><option value="Authorised by Patient & Surrogate">Patient and family agreed</option><option value="Authorised by Designated Healthcare Proxy">Authorised representative agreed</option><option value="Authorised verbally (witnessed by care team)">Verbal agreement witnessed</option></select></label></div>
        <div><label htmlFor={id + '-signature'}>Your signature<input id={id + '-signature'} autoComplete="off" value={signature} placeholder={physician} onChange={(event) => setSignature(event.target.value)} /></label><label className="doctor-review-check"><input type="checkbox" checked={reviewed} onChange={(event) => setReviewed(event.target.checked)} /><span>I checked this note against the conversation. It records what was said, without adding clinical decisions.</span></label></div>
        <button className="primary-button" type="submit" disabled={!ready || editing}>Sign & make family copy</button>
      </form>
    </>}
  </section>;
}
