'use client';

import { useId, useState } from 'react';
import { latestSummaryRelease, type DraftFieldKey, type SummaryState } from './summary-state';
import { approveVisitNote } from './visit-note-state';
import { CARE_NOTE_LABELS } from './doctor-conversation-state';
import { CARE_NOTE_KIND_LABELS, DOCTOR_SIGNED_AWAITING_REVIEW, type CareNoteKind } from './care-note-signing-state';
import { IconCheckCircle, IconClock } from './icons';
import { CareDiscussionForm } from './care-discussion-form';

const CLINICAL_REVIEW_PROMPTS: Record<DraftFieldKey, string> = {
  priorities: 'In the patient’s words: troubling symptoms, what matters, fears, unfinished matters and unacceptable outcomes.',
  participants: 'Attendees and relationships; preferred language; primary and alternate decision-maker, basis for delegation and contacts; care-team contacts.',
  topics: 'Diagnosis, stage and functional status; understanding and capacity (assessor/date); earlier wishes or directive reference. Decisions discussed: overall care goal, hospital transfer, CPR, breathing support/ICU, vasopressors, dialysis and feeding. Record reasons and who took part.',
  openQuestions: 'Choices still unresolved, differences in understanding or agreement, and matters not discussed.',
  followUp: 'Who will do what; any agreed trial duration, review conditions/date and whom to contact.',
};

export function DoctorNoteReview({ patientId, patientName, patients, state, physician, onPatient, onFields, onApprove, onRecord, onEdit, onHistory, onFamily, onFinishLater }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; state: SummaryState; physician: string;
  onPatient: (id: string) => void; onFields: (fields: Record<DraftFieldKey, string>) => void;
  onApprove: (permission: string, reviewed: boolean, signature: string, kind?: CareNoteKind) => void;
  onRecord: () => void; onEdit: () => void; onHistory: () => void; onFamily: () => void; onFinishLater?: () => void;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [kind, setKind] = useState<CareNoteKind | null>(null);
  const [reviewed, setReviewed] = useState(false);
  const [signature, setSignature] = useState('');
  const release = latestSummaryRelease(state);
  const ready = !!kind && reviewed && signature.trim().toLowerCase() === physician.trim().toLowerCase() && approveVisitNote(state, {
    treatingPhysician: true, physician, permission: DOCTOR_SIGNED_AWAITING_REVIEW, reviewed,
    signatureName: signature, noteKind: kind, releasedAt: new Date().toISOString(),
  }) !== state;
  const fields = state.versionPublished && release?.fields ? release.fields : state.draftFields;
  return <section className="doctor-note-review">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1>{state.versionPublished ? (release?.signature ? 'Care note signed' : 'Care note approved') : 'Review & sign'}</h1></div><div className="care-note-top-actions"><select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((patient) => <option key={patient.id} value={patient.id}>{patient.name}</option>)}</select><button type="button" className="primary-button" onClick={onRecord}>+ Record conversation</button></div></header>
    <div className="doctor-note-steps"><button type="button" onClick={onRecord}><span>1</span> Conversation</button><strong><span>2</span> Review & sign</strong><button type="button" onClick={onHistory}><IconClock /> Note history</button></div>
    {state.versionPublished ? <div className="doctor-signed-result"><IconCheckCircle /><div><h2>{release?.signature ? 'Signed by' : 'Approved by'} {release?.signature?.name ?? release?.physician}</h2><p>{release ? new Date(release.releasedAt).toLocaleString('en-GB') : ''} · Version {release?.number}{release?.noteKind ? ` · ${CARE_NOTE_KIND_LABELS[release.noteKind]}` : ''}</p></div><button className="primary-button" type="button" onClick={onFamily}>Open family copy →</button><button className="secondary-button" type="button" onClick={onHistory}>All notes</button></div> : !state.draftSource.trim() ? <div className="care-note-empty"><h2>Start with the conversation</h2><button className="primary-button" type="button" onClick={onRecord}>Record or type a note</button></div> : <>
      <div className="doctor-review-workbench"><section className="doctor-source-card"><div className="family-section-heading"><h2>Conversation</h2><button type="button" className="care-text-button" onClick={onEdit}>Edit note</button></div><p>{state.draftSource}</p></section>
        <section className="doctor-family-draft"><div className="family-section-heading"><h2>Family’s copy</h2><button className="care-text-button" type="button" onClick={() => setEditing(!editing)}>{editing ? 'Done editing' : 'Edit sections'}</button></div>{state.draftPriorVersion !== undefined && <p className="doctor-prior-context">Started from version {state.draftPriorVersion}. Check what still applies.</p>}
        <details className="doctor-prior-context"><summary className="care-text-button">Clinical review prompts</summary><p style={{ margin: '0 0 10px' }}>Use only the conversation and its sources.</p><dl style={{ margin: 0 }}>{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => <div key={key} style={{ marginBottom: 10 }}><dt><strong>{CARE_NOTE_LABELS[key]}</strong></dt><dd id={`${id}-prompt-${key}`} style={{ margin: '3px 0 0' }}>{CLINICAL_REVIEW_PROMPTS[key]}</dd></div>)}</dl></details>
        <CareDiscussionForm key={patientId} fields={fields} onFields={(next) => { onFields(next); setReviewed(false); }} />
        <div className="doctor-draft-fields">{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => {
          const value = fields[key]; const empty = !value?.trim() || value === 'Not stated in this conversation.';
          return editing ? <label key={key}><span>{CARE_NOTE_LABELS[key]}{empty && <small> · Not discussed</small>}</span><textarea rows={3} value={empty ? '' : value} placeholder={CLINICAL_REVIEW_PROMPTS[key]} aria-describedby={`${id}-prompt-${key}`} onChange={(event) => { onFields({ ...fields, [key]: event.target.value }); setReviewed(false); }} /></label> : empty ? null : <section key={key} className={'is-' + key}><h3>{CARE_NOTE_LABELS[key]}</h3><p>{value}</p></section>;
        })}</div></section></div>
      <form className="doctor-sign-panel" onSubmit={(event) => { event.preventDefault(); if (ready && !editing && kind) onApprove(DOCTOR_SIGNED_AWAITING_REVIEW, reviewed, signature, kind); }}>
        <div><h2>Review & sign</h2><fieldset className="doctor-note-kind"><legend>What does this note record?</legend><div>{(Object.keys(CARE_NOTE_KIND_LABELS) as CareNoteKind[]).map((value) => <button key={value} type="button" aria-pressed={kind === value} onClick={() => { setKind(value); setReviewed(false); }}>{CARE_NOTE_KIND_LABELS[value]}</button>)}</div></fieldset></div>
        <div><label htmlFor={id + '-signature'}>Type your name<input id={id + '-signature'} autoComplete="off" maxLength={160} value={signature} placeholder={physician} onChange={(event) => setSignature(event.target.value)} /></label><label className="doctor-review-check"><input type="checkbox" checked={reviewed} onChange={(event) => setReviewed(event.target.checked)} /><span>I checked the wording and note type against the conversation.</span></label></div>
        <div className="doctor-sign-actions"><button className="primary-button" type="submit" disabled={!ready || editing}>Sign for family review</button><button className="secondary-button" type="button" onClick={onFinishLater ?? onHistory}>Finish later</button></div>
      </form>
    </>}
  </section>;
}
