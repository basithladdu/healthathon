'use client';

import { useId, useState } from 'react';
import { approveVisitNote } from './visit-note-state';
import { latestSummaryRelease, type SummaryState } from './summary-state';

const FIELDS = { priorities: 'What matters to the patient', participants: 'Who was there', topics: 'What was discussed', openQuestions: 'Still to discuss', followUp: 'Next steps' } as const;

export function DoctorNoteReview({ patientId, patientName, patients, state, physician, onPatient, onUseNote, onApprove, onFamily }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; state: SummaryState; physician: string;
  onPatient: (id: string) => void; onUseNote: (note: string) => void;
  onApprove: (permission: string, reviewed: boolean) => void; onFamily: () => void;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [note, setNote] = useState(state.draftSource);
  const [permission, setPermission] = useState('Pending patient review');
  const [reviewed, setReviewed] = useState(false);
  const release = latestSummaryRelease(state);
  const ready = approveVisitNote(state, { treatingPhysician: true, physician, permission, reviewed, releasedAt: '2026-08-28T10:30:00+05:30' }) !== state;
  const fields = state.versionPublished && release?.fields ? release.fields : state.draftFields;
  return <section className="doctor-note-review">
    <div className="family-workspace-heading"><h1>{state.versionPublished ? 'Care note approved' : 'Review the care note'}</h1><select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((patient) => <option key={patient.id} value={patient.id}>{patient.name}</option>)}</select></div>
    <p className="care-note-meta">{patientName} · {physician}</p>
    <div className="doctor-review-grid">
      <section className="doctor-source"><div className="family-section-heading"><h2>Visit note</h2><button type="button" className="care-text-button" onClick={() => { setEditing(!editing); setNote(state.draftSource); }}>{editing ? 'Cancel' : 'Use another note'}</button></div>
        {editing || !state.draftSource ? <form onSubmit={(event) => { event.preventDefault(); if (!note.trim()) return; onUseNote(note); setReviewed(false); setPermission('Pending patient review'); setEditing(false); }}>
          <label htmlFor={`${id}-source`}>Paste an existing visit note</label><textarea id={`${id}-source`} rows={9} value={note} onChange={(event) => setNote(event.target.value)} required />
          <button className="primary-button" type="submit" disabled={!note.trim()}>Use this note</button><p className="care-note-meta">Copied as written. Nothing is inferred or added.</p>
        </form> : <p className="doctor-source-text">{state.draftSource}</p>}
      </section>
      <section className="doctor-family-copy"><h2>Family’s copy</h2><dl className="care-note-fields">{Object.entries(FIELDS).map(([key, label]) => {
        const value = fields[key as keyof typeof FIELDS];
        return value && value !== 'Not stated in this conversation.' ? <div key={key}><dt>{label}</dt><dd>{value}</dd></div> : null;
      })}</dl>{!state.draftSource && <p>Add the visit note to start.</p>}</section>
    </div>
    {state.versionPublished ? <div className="doctor-release-row"><p role="status">Version {release?.number} is available in the family’s care note.</p><button className="primary-button" type="button" onClick={onFamily}>Open family view</button></div> : <form className="doctor-approval" onSubmit={(event) => { event.preventDefault(); if (ready) onApprove(permission, reviewed); }}>
      <label htmlFor={`${id}-permission`}>Permission to share<select id={`${id}-permission`} value={permission} onChange={(event) => setPermission(event.target.value)}>
        <option value="Pending patient review">Not confirmed yet</option><option value="Authorised by Patient & Surrogate">Patient and family agreed</option><option value="Authorised by Designated Healthcare Proxy">Authorised representative agreed</option><option value="Authorised verbally (witnessed by care team)">Verbal agreement witnessed by the care team</option>
      </select></label>
      <label className="doctor-review-check"><input type="checkbox" checked={reviewed} onChange={(event) => setReviewed(event.target.checked)} /><span>I checked this copy against the note, including people, dates and unanswered points. It adds no unstated preferences or clinical advice.</span></label>
      <button className="primary-button" type="submit" disabled={!ready || editing}>Approve care note</button>
    </form>}
  </section>;
}
