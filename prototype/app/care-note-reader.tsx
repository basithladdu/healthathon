'use client';

import { useState } from 'react';
import type { SummaryRelease, DraftFieldKey } from './summary-state';
import { selectFamilyNoteVersions, compareFamilyNoteVersions } from './family-note-history-state';
import { CARE_NOTE_LABELS, type DoctorConversationEntry } from './doctor-conversation-state';
import { ConversationAudio } from './doctor-conversation';
import { useSpeechSynthesis } from './tts-speech';
import { IconFileText, IconClock, IconCheckCircle, IconVolume2 } from './icons';
import { downloadCarePdf } from './care-pdf';
import { CARE_NOTE_KIND_LABELS, careNoteReleaseId, createCareNoteAcknowledgement, matchingCareNoteAcknowledgements, hasChangedSignedCareNote, hasTypedCareNoteDoctorSignature, type CareNoteAcknowledgement, type CareNoteSignerRole } from './care-note-signing-state';

const KEYS: DraftFieldKey[] = ['followUp', 'priorities', 'topics', 'participants', 'openQuestions'];
const NOT_STATED = 'Not stated in this conversation.';

export function CareNoteReader({ patientId, patientName, releases, hindi, conversations = [], doctor = false, patients, onPatient, onRecord, onPrepare, acknowledgements = [], signerName, signerRole, onAcknowledge, onFinishLater, initialVersion }: {
  patientId: string; patientName: string; releases: readonly SummaryRelease[]; hindi: boolean;
  conversations?: readonly DoctorConversationEntry[]; doctor?: boolean;
  patients?: Array<{ id: string; name: string }>; onPatient?: (id: string) => void; onRecord?: () => void; onPrepare?: () => void;
  acknowledgements?: readonly CareNoteAcknowledgement[]; signerName?: string; signerRole?: CareNoteSignerRole;
  onAcknowledge?: (acknowledgement: CareNoteAcknowledgement) => void; onFinishLater?: () => void;
  initialVersion?: number;
}) {
  const routeParams = typeof window === 'undefined' ? null : new URLSearchParams(window.location.search);
  const routePatientId = routeParams?.get('patientId') ?? null;
  const hasRouteVersion = routeParams?.has('version') ?? false;
  const routeVersionValue = routeParams?.get('version') ?? '';
  const routeVersion = /^\d+$/.test(routeVersionValue) && Number(routeVersionValue) > 0 ? Number(routeVersionValue) : undefined;
  const requestedVersion = hasRouteVersion ? routeVersion : initialVersion;
  const routePatientMismatch = Boolean(routePatientId && routePatientId !== patientId);
  const [selectedNumber, setSelectedNumber] = useState<number | undefined>(requestedVersion);
  const [tab, setTab] = useState<'notes' | 'conversations'>('notes');
  const [comparing, setComparing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState('');
  const speech = useSpeechSynthesis();
  const versions = selectFamilyNoteVersions(releases, patientId);
  const latestSignedVersion = versions.find(hasTypedCareNoteDoctorSignature);
  const defaultRelease = latestSignedVersion ?? versions[0];
  const routeVersionUnavailable = hasRouteVersion && (!routeVersion || !versions.some((release) => release.number === routeVersion && hasTypedCareNoteDoctorSignature(release)));
  const selected = routePatientMismatch || routeVersionUnavailable
    ? undefined
    : versions.find((release) => release.number === selectedNumber) ?? defaultRelease;
  const signedCopies = selected ? matchingCareNoteAcknowledgements(acknowledgements, selected) : [];
  const patientSigned = signedCopies.some((entry) => entry.signerRole === 'patient');
  const changedSignedNote = selected ? hasChangedSignedCareNote(acknowledgements, selected) : false;
  const previous = selected ? versions.find((release) => release.number < selected.number) : undefined;
  const comparison = selected && previous ? compareFamilyNoteVersions(releases, patientId, selected.number, previous.number) : null;
  const recordings = conversations.filter((entry) => entry.patientId === patientId).slice().sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
  const t = (en: string, hi: string) => hindi ? hi : en;
  const date = (value: string) => new Date(value).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const sections = selected?.fields ? KEYS.filter((key) => selected.fields?.[key]?.trim() && selected.fields[key] !== NOT_STATED).map((key) => ({ key, heading: CARE_NOTE_LABELS[key], lines: [selected.fields![key]] }))
    : selected?.source ? [{ key: 'topics' as const, heading: 'Conversation note', lines: [selected.source] }] : [];

  async function download() {
    if (!selected || !sections.length || downloading || changedSignedNote) return;
    setDownloading(true); setError('');
    try {
      await downloadCarePdf({ fileName: `saanthvana-${patientId}-care-note-v${selected.number}.pdf`, title: `${patientName} — Care Note`,
        subtitle: `Version ${selected.number} · ${date(selected.releasedAt)} · ${selected.physician}`,
        sections, careNote: { release: selected, acknowledgements } });
    } catch { setError('The PDF could not be downloaded. Please try again.'); }
    finally { setDownloading(false); }
  }

  return <section className="care-note-reader">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1>{doctor ? t('Care Note history', 'डॉक्टर के पुराने नोट') : t('Care Note', 'देखभाल का नोट')}</h1></div>
      <div className="care-note-top-actions">{patients && onPatient && <select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select>}{onRecord && <button className="primary-button" type="button" onClick={onRecord}>+ Record conversation</button>}</div></header>
    {doctor && <div className="doctor-history-tabs" role="group" aria-label="Note history view"><button type="button" aria-pressed={tab === 'notes'} onClick={() => setTab('notes')}><IconCheckCircle />Approved notes <span>{versions.length}</span></button><button type="button" aria-pressed={tab === 'conversations'} onClick={() => setTab('conversations')}><IconClock />Conversations <span>{recordings.length}</span></button></div>}
    {tab === 'conversations' ? <div className="doctor-conversation-history">{recordings.length ? recordings.map((entry) => <article key={entry.id}><span className="care-timeline-dot" /><time dateTime={entry.recordedAt}>{date(entry.recordedAt)}</time><h2>{entry.physician}</h2>{entry.audio && <ConversationAudio file={entry.audio} />}{entry.note && <details><summary>Conversation note</summary><p>{entry.note}</p></details>}</article>) : <div className="care-note-empty"><IconClock /><h2>No conversations recorded yet</h2>{onRecord && <button className="primary-button" type="button" onClick={onRecord}>Record a conversation</button>}</div>}</div>
      : selected ? <div className="care-note-layout">
        <aside className="care-note-date-rail" aria-label="Care Note timeline"><ol>{versions.map((release, index) => <li key={release.number}><button type="button" aria-pressed={selected.number === release.number} onClick={() => { setSelectedNumber(release.number); setComparing(false); speech.cancel(); }}><span className="care-timeline-dot" /><time dateTime={release.releasedAt}>{date(release.releasedAt)}</time><strong>{index === 0 ? t('Latest Care Note', 'सबसे नया नोट') : t('Earlier Care Note', 'पुराना नोट')}</strong><small>{release.physician} · V{release.number}</small></button></li>)}</ol></aside>
        <article className="care-note-paper"><header className="care-note-paper-heading"><div><span className="care-note-approved"><IconCheckCircle />{patientSigned ? t('Patient-signed version', 'मरीज़ द्वारा हस्ताक्षरित संस्करण') : selected.signature ? t('Doctor signed', 'डॉक्टर ने हस्ताक्षर किए') : t('Approved Care Note', 'मंज़ूर देखभाल नोट')}</span><h2>{date(selected.releasedAt)}</h2><span>{selected.physician} · V{selected.number}{selected.noteKind ? ` · ${CARE_NOTE_KIND_LABELS[selected.noteKind]}` : ''}</span></div><div className="care-note-tools"><button className="primary-button" type="button" disabled={downloading || !sections.length || changedSignedNote} onClick={download}><IconFileText />{downloading ? 'Preparing…' : 'PDF'}</button>{speech.isSupported && sections.length > 0 && <button type="button" className="secondary-button" onClick={() => speech.isPlaying ? speech.cancel() : speech.speak(sections.map((section) => `${section.heading}. ${section.lines.join(' ')}`).join('. '))}><IconVolume2 />{speech.isPlaying ? 'Stop' : 'Listen'}</button>}</div></header>
          <div className="care-note-story-cards">{sections.map((section, index) => <section className={`care-note-story-card is-${section.key}`} key={section.key}><span aria-hidden="true" className="care-note-section-number">{String(index + 1).padStart(2, '0')}</span><div><h3>{section.heading}</h3>{section.lines.map((line, item) => line.length > 650 ? <details key={item}><summary>{line.slice(0, 260)}…</summary><p>{line}</p></details> : <p key={item}>{line}</p>)}</div></section>)}</div>
          {!sections.length && <p>The text for this earlier note is unavailable.</p>}
          {selected.signature && <div className="care-note-signature"><span>{t('Doctor signature · typed name', 'डॉक्टर के हस्ताक्षर · टाइप किया हुआ नाम')}</span><strong>{selected.signature.name}</strong><time dateTime={selected.signature.signedAt}>{new Date(selected.signature.signedAt).toLocaleString(hindi ? 'hi-IN' : 'en-GB')}</time></div>}
          {previous && <button className="care-text-button" type="button" aria-expanded={comparing} onClick={() => setComparing(!comparing)}>{comparing ? 'Close changes' : 'What changed since the last note?'} →</button>}
          {comparing && comparison && <div className="care-note-changes">{comparison.rows.filter((row) => row.change === 'changed').map((row) => <section key={row.key}><h3>{row.label}</h3><del>{row.earlier}</del><p>{row.later}</p></section>)}{!comparison.rows.some((row) => row.change === 'changed') && <p>{comparison.rows.some((row) => row.change === 'unavailable') ? 'The earlier text is unavailable for comparison.' : 'No wording changes between these notes.'}</p>}</div>}
          <CareNoteReviewSignature key={`${careNoteReleaseId(selected)}:${signerRole ?? ''}:${signerName ?? ''}`} release={selected} patientName={patientName} acknowledgements={acknowledgements} hindi={hindi} doctor={doctor} isLatest={selected.number === latestSignedVersion?.number} signerName={signerName} signerRole={signerRole} onAcknowledge={onAcknowledge} onFinishLater={onFinishLater} />
        </article>
      </div> : <div className="care-note-empty"><IconFileText /><h2>{routePatientMismatch ? t('This QR belongs to a different patient profile', 'यह QR किसी दूसरे मरीज़ की प्रोफ़ाइल के लिए है') : hasRouteVersion ? t('This Care Note version is not available here', 'यह देखभाल नोट संस्करण यहाँ उपलब्ध नहीं है') : t('No approved Care Note yet', 'अभी कोई मंज़ूर देखभाल नोट नहीं है')}</h2>{onRecord && !routePatientMismatch && !hasRouteVersion && <button className="primary-button" type="button" onClick={onRecord}>Record a conversation</button>}</div>}
    {onPrepare && <button className="care-text-button" type="button" onClick={onPrepare}>{t('Something to ask at the next visit?', 'अगली मुलाकात में कुछ पूछना है?')} →</button>}
    {error && <p role="alert" className="doctor-action-message">{error}</p>}
  </section>;
}

function CareNoteReviewSignature({ release, patientName, acknowledgements, hindi, doctor, isLatest, signerName, signerRole, onAcknowledge, onFinishLater }: {
  release: SummaryRelease; patientName: string; acknowledgements: readonly CareNoteAcknowledgement[]; hindi: boolean; doctor: boolean; isLatest: boolean;
  signerName?: string; signerRole?: CareNoteSignerRole; onAcknowledge?: (acknowledgement: CareNoteAcknowledgement) => void; onFinishLater?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const selectedRole = signerRole ?? 'patient';
  const [signature, setSignature] = useState('');
  const [reviewed, setReviewed] = useState(false);
  const [message, setMessage] = useState('');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const signed = matchingCareNoteAcknowledgements(acknowledgements, release);
  const changed = hasChangedSignedCareNote(acknowledgements, release);
  const patientHasSigned = signed.some((entry) => entry.signerRole === 'patient');
  const patientSignerName = signerRole === 'patient' ? signerName?.trim() ?? '' : patientName.trim();
  const chosenSignerName = selectedRole === 'patient' ? patientSignerName : signature.trim();
  const alreadySigned = signed.some((entry) => entry.signerRole === selectedRole && entry.signerName.trim().toLowerCase() === chosenSignerName.toLowerCase());
  const canSign = !doctor && isLatest && !changed && !!release.signature && !!release.noteKind && !!signerName?.trim() && !!signerRole && !!onAcknowledge && (signerRole === 'patient' || patientHasSigned);
  const nameMatches = selectedRole === 'family' || signature.trim().toLowerCase() === patientSignerName.toLowerCase();
  const ready = canSign && !alreadySigned && reviewed && !!signature.trim() && nameMatches;
  function finishLater() { setOpen(false); setReviewed(false); setSignature(''); setMessage(''); onFinishLater?.(); }
  function sign() {
    if (!ready || !chosenSignerName || !onAcknowledge) return;
    const acknowledgement = createCareNoteAcknowledgement(release, { patientId: release.patientId, signerName: chosenSignerName, signatureName: signature, signerRole: selectedRole, reviewed, signedAt: new Date().toISOString() });
    if (!acknowledgement) { setMessage(t('This version is not ready for your signature.', 'यह संस्करण अभी हस्ताक्षर के लिए तैयार नहीं है।')); return; }
    onAcknowledge(acknowledgement); setOpen(false); setSignature(''); setReviewed(false);
  }
  if (!release.signature && !signed.length && !changed) return null;
  return <section className="care-note-family-signing">
    {changed ? <p className="care-note-signing-error" role="alert">{t('This note no longer matches its signed version. Its signature cannot be applied to changed text.', 'यह नोट अपने हस्ताक्षर किए हुए संस्करण से अलग है। पुराने हस्ताक्षर बदले हुए नोट पर लागू नहीं होते।')}</p> : <>
      {signed.map((entry) => <div key={entry.id} className="care-note-signature is-family"><span>{entry.signerRole === 'patient' ? t('Patient signature · typed name', 'मरीज़ के हस्ताक्षर · टाइप किया हुआ नाम') : t('Family signature · typed name', 'परिवार के हस्ताक्षर · टाइप किया हुआ नाम')}</span><strong>{entry.signerName}</strong><time dateTime={entry.signedAt}>{new Date(entry.signedAt).toLocaleString(hindi ? 'hi-IN' : 'en-GB')} · V{entry.version}</time></div>)}
      {signed.length > 0 && <p className="care-note-final-version"><IconCheckCircle />{patientHasSigned ? t(`The patient reviewed version ${release.number}. Changes need a new version.`, `मरीज़ ने संस्करण ${release.number} की समीक्षा की। बदलाव के लिए नया संस्करण बनाएँ।`) : t('Family acknowledgement recorded; patient review is still required.', 'परिवार की स्वीकृति दर्ज है; मरीज़ की समीक्षा अभी बाकी है।')}</p>}
      {doctor && !signed.length && release.noteKind && <p className="care-note-review-pending">{t('Awaiting patient review', 'मरीज़ की समीक्षा बाकी है')}</p>}
      {!doctor && signerRole === 'family' && !patientHasSigned && release.signature && <p className="care-note-review-pending">{t('The patient must review and sign this version before a family member can acknowledge it.', 'परिवार के सदस्य की स्वीकृति से पहले मरीज़ को इस संस्करण की समीक्षा और हस्ताक्षर करना होगा।')}</p>}
      {canSign && (open ? <form className="care-note-acknowledge-form" onSubmit={(event) => { event.preventDefault(); sign(); }}>
        <h3>{t(`Review & sign version ${release.number}`, `संस्करण ${release.number} देखें और हस्ताक्षर करें`)}</h3>
        <label className="care-note-signature-name">{selectedRole === 'patient' ? t('Patient’s full name', 'मरीज़ का पूरा नाम') : t('Your full name', 'आपका पूरा नाम')}<input autoComplete="off" maxLength={160} required value={signature} placeholder={selectedRole === 'patient' ? patientSignerName : signerRole === 'family' ? signerName : undefined} aria-invalid={!!signature.trim() && !nameMatches} onChange={(event) => setSignature(event.target.value)} /></label>
        <label className="care-note-review-check"><input type="checkbox" checked={reviewed} onChange={(event) => setReviewed(event.target.checked)} /><span>{release.noteKind === 'decisions-recorded' ? t('I reviewed this version. It accurately records our discussion and the decisions written here.', 'मैंने यह संस्करण देखा। इसमें हमारी बातचीत और लिखे गए फैसले सही दर्ज हैं।') : t('I reviewed this version. It accurately records our discussion.', 'मैंने यह संस्करण देखा। इसमें हमारी बातचीत सही दर्ज है।')}</span></label>
        {alreadySigned && <p className="care-note-signing-error">{t('This name has already signed this version.', 'इस नाम से इस संस्करण पर पहले ही हस्ताक्षर हैं।')}</p>}
        {!!signature.trim() && !nameMatches && <p className="care-note-signing-error">{t(`Name must match ${patientSignerName}.`, `नाम ${patientSignerName} से मेल खाना चाहिए।`)}</p>}
        <div className="care-note-review-actions"><button className="primary-button" type="submit" disabled={!ready}>{t('Sign this version', 'इस संस्करण पर हस्ताक्षर करें')}</button><button className="secondary-button" type="button" onClick={finishLater}>{t('Finish later', 'बाद में करें')}</button></div>
      </form> : <div className="care-note-review-actions">{isLatest && <button className="primary-button" type="button" onClick={() => setOpen(true)}>{t('Review & sign', 'देखें और हस्ताक्षर करें')}</button>}{!isLatest && !doctor && <span>{t('Earlier versions are available to read; sign the latest version.', 'पुराने संस्करण पढ़ने के लिए उपलब्ध हैं; नवीनतम संस्करण पर हस्ताक्षर करें।')}</span>}{onFinishLater && <button className="secondary-button" type="button" onClick={finishLater}>{t('Finish later', 'बाद में करें')}</button>}</div>)}
    </>}
    {message && <p className="care-note-signing-error" role="status">{message}</p>}
  </section>;
}
