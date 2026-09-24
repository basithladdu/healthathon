'use client';

import { useEffect, useId, useState } from 'react';
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

export function DoctorNoteReview({ patientId, patientName, patients, state, physician, canSign = true, onPatient, onFields, onConfirmField, onApprove, onRecord, onEdit, onHistory, onFamily, onFinishLater }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; state: SummaryState; physician: string;
  canSign?: boolean;
  onPatient: (id: string) => void; onFields: (fields: Record<DraftFieldKey, string>) => void; onConfirmField?: (key: DraftFieldKey) => void;
  onApprove: (permission: string, reviewed: boolean, signature: string, kind?: CareNoteKind) => void;
  onRecord: () => void; onEdit: () => void; onHistory: () => void; onFamily: (version?: number) => void; onFinishLater?: () => void;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [kind, setKind] = useState<CareNoteKind | null>(null);
  const [reviewed, setReviewed] = useState(false);
  const [signature, setSignature] = useState(physician);
  const [signMessage, setSignMessage] = useState('');
  useEffect(() => setSignature(physician), [physician]);
  const release = latestSummaryRelease(state);
  const fields = state.versionPublished && release?.fields ? release.fields : state.draftFields;
  function signingBlocker() {
    if (!canSign) return 'Only the treating physician can sign this note.';
    if (!kind) return 'Choose whether this note records a discussion or decisions recorded.';
    if (editing) return 'Finish editing the note before signing.';
    if (!physician.trim()) return 'A signed-in clinician name is required.';
    if (signature.trim().toLowerCase() !== physician.trim().toLowerCase()) return 'The typed name must match the signed-in clinician name.';
    if (!reviewed) return 'Confirm that you checked the wording and note type against the conversation.';
    if (!state.draftSource.trim()) return 'Add the conversation source before signing.';
    for (const key of Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]) {
      const value = state.draftFields[key]; const status = state.draftStatuses[key];
      if (!value.trim()) return `Complete “${CARE_NOTE_LABELS[key]}” or mark it as not stated.`;
      if (status === 'not-stated' && value !== 'Not stated in this conversation.') return `Review “${CARE_NOTE_LABELS[key]}”.`;
      if (status !== 'not-stated' && status !== 'ready') return `Confirm or edit “${CARE_NOTE_LABELS[key]}” before signing.`;
      if (status === 'ready' && (!state.draftExcerpts[key].trim() || !state.draftSource.includes(state.draftExcerpts[key]))) return `Link “${CARE_NOTE_LABELS[key]}” to the conversation source.`;
    }
    const next = approveVisitNote(state, {
      treatingPhysician: true, physician, permission: DOCTOR_SIGNED_AWAITING_REVIEW, reviewed,
      signatureName: signature, noteKind: kind, releasedAt: new Date().toISOString(),
    });
    return next === state ? 'Review the note details and try signing again.' : '';
  }
  const blockedReason = signingBlocker();
  const ready = !blockedReason;
  return <section className="doctor-note-review">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1>{state.versionPublished ? (release?.signature ? 'Care Note signed' : 'Care Note approved') : 'Review Care Note'}</h1></div><div className="care-note-top-actions"><select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((patient) => <option key={patient.id} value={patient.id}>{patient.name}</option>)}</select><button type="button" className="primary-button" onClick={onRecord}>+ Record conversation</button></div></header>
    <ol className="doctor-note-steps" aria-label="Care Note workflow">
      <li><button type="button" onClick={onRecord}><span>1</span> Prepare draft</button></li>
      <li aria-current="step"><strong><span>2</span> Review & edit</strong></li>
      <li><button type="button" onClick={() => document.getElementById(`${id}-sign-panel`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })}><span>3</span> Sign Care Note</button></li>
      <li><button type="button" onClick={onHistory}><IconClock /> Note history</button></li>
    </ol>
    {state.versionPublished ? <div className="doctor-signed-result"><IconCheckCircle /><div><h2>{release?.signature ? 'Signed by' : 'Approved by'} {release?.signature?.name ?? release?.physician}</h2><p>{release ? new Date(release.releasedAt).toLocaleString('en-GB') : ''} · Version {release?.number}{release?.noteKind ? ` · ${CARE_NOTE_KIND_LABELS[release.noteKind]}` : ''}</p></div><button className="primary-button" type="button" onClick={() => onFamily(release?.number)}>Open family copy →</button><button className="secondary-button" type="button" onClick={onHistory}>All notes</button></div> : !state.draftSource.trim() ? <div className="care-note-empty"><h2>Start with the conversation</h2><button className="primary-button" type="button" onClick={onRecord}>Record or type a note</button></div> : <>
      <div className="doctor-review-workbench"><section className="doctor-source-card"><div className="family-section-heading"><h2>Conversation source</h2><button type="button" className="care-text-button" onClick={onEdit}>Edit draft source</button></div><details><summary>View full conversation</summary><p>{state.draftSource}</p></details></section>
        <section className="doctor-family-draft"><div className="family-section-heading"><h2>Care Note draft</h2><button className="care-text-button" type="button" onClick={() => setEditing(!editing)}>{editing ? 'Done editing' : 'Edit sections'}</button></div>{state.draftPriorVersion !== undefined && <p className="doctor-prior-context">Started from version {state.draftPriorVersion}. Check what still applies.</p>}
        <details className="doctor-prior-context"><summary className="care-text-button">Clinical review prompts</summary><p style={{ margin: '0 0 10px' }}>Use only the conversation and its sources.</p><dl style={{ margin: 0 }}>{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => <div key={key} style={{ marginBottom: 10 }}><dt><strong>{CARE_NOTE_LABELS[key]}</strong></dt><dd id={`${id}-prompt-${key}`} style={{ margin: '3px 0 0' }}>{CLINICAL_REVIEW_PROMPTS[key]}</dd></div>)}</dl></details>
        <CareDiscussionForm key={patientId} fields={fields} onFields={(next) => { onFields(next); setReviewed(false); setSignMessage(''); }} />
        <div className="doctor-draft-fields">{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).map((key) => {
          const value = fields[key]; const empty = !value?.trim() || value === 'Not stated in this conversation.';
          return editing ? <label key={key}><span>{CARE_NOTE_LABELS[key]}{empty && <small> · Not discussed</small>}</span><textarea rows={3} value={empty ? '' : value} placeholder={CLINICAL_REVIEW_PROMPTS[key]} aria-describedby={`${id}-prompt-${key}`} onChange={(event) => { onFields({ ...fields, [key]: event.target.value }); setReviewed(false); setSignMessage(''); }} /></label> : empty ? null : <section key={key} className={'is-' + key}><div className="doctor-draft-field-heading"><h3>{CARE_NOTE_LABELS[key]}</h3>{state.draftStatuses[key] === 'clarify' && onConfirmField && <button type="button" className="care-text-button" onClick={() => { onConfirmField(key); setReviewed(false); setSignMessage(''); }}>Confirm this section</button>}</div><p>{value}</p></section>;
        })}</div></section></div>
      <form id={`${id}-sign-panel`} className="doctor-sign-panel" onSubmit={(event) => { event.preventDefault(); if (blockedReason) { setSignMessage(blockedReason); return; } if (kind) onApprove(DOCTOR_SIGNED_AWAITING_REVIEW, reviewed, signature, kind); }}>
        <div><h2>Sign Care Note</h2><fieldset className="doctor-note-kind"><legend>What does this note record?</legend><div>{(Object.keys(CARE_NOTE_KIND_LABELS) as CareNoteKind[]).map((value) => <button key={value} type="button" aria-pressed={kind === value} onClick={() => { setKind(value); setReviewed(false); setSignMessage(''); }}>{CARE_NOTE_KIND_LABELS[value]}</button>)}</div></fieldset></div>
        <div><label htmlFor={id + '-signature'}>Clinician name<input id={id + '-signature'} autoComplete="name" maxLength={160} value={signature} onChange={(event) => { setSignature(event.target.value); setSignMessage(''); }} /></label><label className="doctor-review-check"><input type="checkbox" checked={reviewed} onChange={(event) => { setReviewed(event.target.checked); setSignMessage(''); }} /><span>I checked the wording and note type against the conversation.</span></label></div>
        <div className="doctor-sign-actions"><button className="primary-button" type="submit">Sign Care Note for patient review</button><button className="secondary-button" type="button" onClick={onFinishLater ?? onHistory}>Finish later</button></div>
        <p className="doctor-signing-reason" role="status" aria-live="polite">{signMessage || (ready ? 'Ready to sign this reviewed version.' : blockedReason)}</p>
      </form>
    </>}
  </section>;
}
