'use client';

import { Suspense, lazy, useEffect, useRef, useState } from 'react';
import { IconFileText, IconClock } from './icons';
import { CARE_NOTE_LABELS, organiseConversation, type AssistedNote, type DoctorConversationEntry } from './doctor-conversation-state';
import type { DraftFieldKey } from './summary-state';
import { CarePdfReadError, extractCareDocumentPdf } from './care-document-extract';
import { careLocale, careText, type CareLang } from './care-i18n';

// Loaded only once a recording exists. The speech engine loads later still, on Transcribe.
const GocTranscribeButton = lazy(() => import('./goc-transcribe').then((module) => ({ default: module.GocTranscribeButton })));

export function ConversationAudio({ file }: { file: File }) {
  const audio = useRef<HTMLAudioElement | null>(null);
  useEffect(() => { const next = URL.createObjectURL(file); if (audio.current) audio.current.src = next; return () => URL.revokeObjectURL(next); }, [file]);
  return <audio ref={audio} controls preload="metadata" aria-label="Conversation recording" />;
}

export function DoctorConversation({ patientId, patientName, patients, physician, initialNote, referenceNote, previousContext, requestContext, lang, onPatient, onSave, onHistory }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; physician: string;
  initialNote: string; referenceNote?: string; lang: CareLang; onPatient: (id: string) => void;
  previousContext?: { version: number; releasedAt: string; fields: Readonly<Record<DraftFieldKey, string>> | null };
  requestContext?: { requestedBy: string; topics: string[]; note: string };
  onSave: (entry: DoctorConversationEntry, draft?: AssistedNote) => void; onHistory: () => void;
}) {
  const [note, setNote] = useState(initialNote);
  const [audio, setAudio] = useState<File>();
  const [agreed, setAgreed] = useState(false);
  const [recording, setRecording] = useState(false);
  const [starting, setStarting] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [language, setLanguage] = useState(careLocale(lang, 'en-IN'));
  const recorder = useRef<MediaRecorder | null>(null);
  const sourceFileInput = useRef<HTMLInputElement | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const mounted = useRef(true);
  const preparing = useRef(false);
  const t = (en: string, hi: string) => careText(lang, en, hi);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; if (recorder.current?.state === 'recording') recorder.current.stop(); stream.current?.getTracks().forEach((track) => track.stop()); };
  }, []);
  useEffect(() => {
    if (!recording) return;
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [recording]);

  async function startRecording() {
    if (!agreed || starting || recording || audio || busy) return;
    setMessage(''); setStarting(true);
    try {
      if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') throw new Error('This browser cannot record audio. You can upload a recording or type the note.');
      const input = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mounted.current) { input.getTracks().forEach((track) => track.stop()); return; }
      stream.current = input;
      const mimeType = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm'].find((type) => MediaRecorder.isTypeSupported(type));
      const capture = new MediaRecorder(input, mimeType ? { mimeType } : undefined);
      const chunks: Blob[] = [];
      capture.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
      capture.onstop = () => {
        input.getTracks().forEach((track) => track.stop());
        if (!mounted.current) return;
        const type = capture.mimeType || 'audio/webm';
        setAudio(new File(chunks, `conversation-${language}-${Date.now()}.${type.includes('mp4') ? 'm4a' : 'webm'}`, { type }));
        setRecording(false);
      };
      recorder.current = capture; capture.start(1000); setSeconds(0); setRecording(true);
      // Browser SpeechRecognition may silently use a remote recognizer. Online transcription
      // belongs to the separately consented, authenticated provider flow in the shared workspace.
      setMessage('Audio is recording. Add or edit a reviewed transcript below.');
    } catch (error) { stream.current?.getTracks().forEach((track) => track.stop()); setMessage(error instanceof Error && error.name !== 'NotAllowedError' ? error.message : 'Microphone access was not allowed. You can type or upload instead.'); }
    finally { setStarting(false); }
  }

  function stopRecording() { if (recorder.current?.state === 'recording') recorder.current.stop(); }
  async function importSourceFile(file: File) {
    setMessage(''); setBusy(true);
    try {
      let text = '';
      if (file.type === 'application/pdf' || /\.pdf$/iu.test(file.name)) {
        const result = await extractCareDocumentPdf(file);
        text = result.text;
        if (!text.trim()) throw new Error('No selectable text was found in this PDF. Paste a reviewed transcript into the note.');
      } else if (file.type === 'text/plain' || /\.txt$/iu.test(file.name)) {
        if (file.size > 5 * 1024 * 1024) throw new Error('Choose a text file under 5 MB.');
        text = await file.text();
      } else throw new Error('Choose a plain-text transcript or a text-based PDF.');
      if (!text.trim()) throw new Error('The selected file is empty.');
      setNote((current) => current.trim() ? `${current.trim()}\n\n[Text imported from ${file.name}]\n${text.trim()}` : text.trim());
      setMessage(`Text imported from ${file.name}. Check the source before preparing the Care Note.`);
    } catch (error) {
      const reason = error instanceof CarePdfReadError ? error.code === 'password' ? 'This PDF is password protected.' : error.code === 'too-much-text' ? 'This PDF is too large to read here.' : 'This PDF could not be read.' : error instanceof Error ? error.message : 'The selected file could not be read.';
      setMessage(reason);
    } finally { setBusy(false); if (sourceFileInput.current) sourceFileInput.current.value = ''; }
  }
  function save(draft?: AssistedNote, source = note.trim()) {
    if (recording || starting || (!note.trim() && !audio)) return;
    onSave({ id: crypto.randomUUID(), patientId, physician, note: source, recordedAt: new Date().toISOString(), audio, recordingAgreed: agreed }, draft);
  }
  async function assist() {
    if (!note.trim() || preparing.current || recording || starting) return;
    const source = note.trim();
    preparing.current = true;
    setBusy(true); setMessage('');
    try {
      // This local action organises original wording. It must never masquerade as a failed AI call.
      const draft = organiseConversation(source);
      if (mounted.current) save(draft, source);
    } finally {
      preparing.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return <section className="doctor-conversation">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{physician}</span><h1>{t('Record a conversation', 'बातचीत दर्ज करें')}</h1></div><label className="doctor-source-import">Add transcript or PDF<input ref={sourceFileInput} type="file" accept="text/plain,.txt,application/pdf,.pdf" disabled={recording || starting || busy} onChange={(event) => { const file = event.target.files?.[0]; if (file) void importSourceFile(file); }} /></label><select aria-label="Patient" value={patientId} disabled={recording || starting || busy} onChange={(event) => onPatient(event.target.value)}>{patients.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select></header>
    <ol className="doctor-note-steps" aria-label="Care Note workflow"><li aria-current="step"><strong><span>1</span> {t('Prepare draft', 'मसौदा तैयार करें')}</strong></li><li><span><span>2</span> {t('Review & edit', 'जाँचें और बदलें')}</span></li><li><span><span>3</span> {t('Sign Care Note', 'देखभाल के नोट पर हस्ताक्षर')}</span></li><li><button type="button" onClick={onHistory}><IconClock /> {t('Note history', 'पुराने नोट')}</button></li></ol>
    {requestContext && <section className="doctor-request-context"><h2>Requested discussion</h2><p>From {requestContext.requestedBy}</p>{requestContext.topics.length > 0 && <ul>{requestContext.topics.map((topic, index) => <li key={`${topic}-${index}`}>{topic}</li>)}</ul>}{requestContext.note.trim() && <p>{requestContext.note}</p>}</section>}
    {previousContext && <details className="doctor-previous-discussion"><summary><strong>Previous signed discussion · Version {previousContext.version}</strong><time dateTime={previousContext.releasedAt}>{new Date(previousContext.releasedAt).toLocaleDateString('en-GB')}</time></summary>{previousContext.fields && <dl>{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).filter((key) => {
      const value = previousContext.fields?.[key]?.trim(); return Boolean(value && value !== 'Not stated in this conversation.');
    }).map((key) => <div key={key}><dt>{CARE_NOTE_LABELS[key]}</dt><dd>{previousContext.fields?.[key]}</dd></div>)}</dl>}</details>}
    <div className="doctor-capture-grid">
      <section className={`doctor-record-card ${recording ? 'is-recording' : ''}`}>
        <div className="doctor-record-orb" aria-hidden="true"><svg viewBox="0 0 48 48"><rect x="17" y="6" width="14" height="25" rx="7"/><path d="M11 23v2a13 13 0 0 0 26 0v-2M24 38v6M17 44h14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"/></svg></div>
        <h2>{recording ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : t('Voice note', 'आवाज़ में नोट')}</h2>
        <label className="doctor-record-consent"><input type="checkbox" checked={agreed} disabled={recording || starting} onChange={(event) => setAgreed(event.target.checked)} />{t('Everyone agreed to recording', 'सभी ने रिकॉर्डिंग की अनुमति दी')}</label>
        <button type="button" className="primary-button doctor-record-button" disabled={!agreed || starting || busy || Boolean(audio)} onClick={recording ? stopRecording : startRecording}>{starting ? 'Opening microphone…' : recording ? t('Stop recording', 'रिकॉर्डिंग रोकें') : t('Start recording', 'रिकॉर्डिंग शुरू करें')}</button>
        <select aria-label="Recording language" value={language} disabled={recording || starting} onChange={(event) => setLanguage(event.target.value)}><option value="en-IN">English</option><option value="hi-IN">हिन्दी</option></select>
        <label className={`doctor-audio-upload ${!agreed || audio || busy ? 'is-disabled' : ''}`}>{t('Upload audio', 'ऑडियो जोड़ें')}<input type="file" accept="audio/*" disabled={!agreed || recording || starting || busy || Boolean(audio)} onChange={(event) => { const file = event.target.files?.[0]; if (!file || audio) return; if (file.size > 30 * 1024 * 1024) { setMessage('Choose an audio file under 30 MB.'); return; } setAudio(file); event.target.value = ''; }} /></label>
        {audio && <div className="doctor-audio-review"><ConversationAudio file={audio} /><button type="button" className="care-text-button" disabled={busy} onClick={() => setAudio(undefined)}>Remove recording</button><Suspense fallback={null}><GocTranscribeButton file={audio} lang={lang} onText={(text) => setNote((current) => `${current.trim()}${current.trim() ? '\n' : ''}${text}`.slice(0, 12000))} /></Suspense></div>}
      </section>
      <section className="doctor-transcript-card"><div className="family-section-heading"><h2><IconFileText /> {t('Conversation note', 'बातचीत का नोट')}</h2><span>{patientName.split(' ')[0]}</span></div>
        {!previousContext && referenceNote?.trim() && !note.trim() && !audio && !recording && !starting && <button className="care-text-button" type="button" disabled={busy} onClick={() => { setNote(referenceNote); setMessage(''); }}>{t('Use latest conversation', 'पिछली बातचीत लें')}</button>}
        <textarea aria-label="Conversation note" value={note} disabled={busy} maxLength={12000} rows={12} onChange={(event) => setNote(event.target.value)} placeholder={t('Record, type, or paste the conversation here.', 'बातचीत रिकॉर्ड करें, लिखें या यहाँ पेस्ट करें।')} />
        <div className="doctor-note-actions"><button className="primary-button" type="button" disabled={recording || starting || busy || (!note.trim() && !audio)} onClick={() => { if (note.trim()) void assist(); else save(); }}>{busy ? t('Preparing Care Note…', 'देखभाल नोट तैयार हो रहा है…') : !note.trim() && audio ? t('Save recording', 'रिकॉर्डिंग सेव करें') : t('Prepare Care Note', 'देखभाल नोट तैयार करें')} →</button></div>
        <p className="doctor-action-message">{t('This prepares a draft from your original wording for separate review.', 'आपके मूल शब्दों से मसौदा बनेगा; उसकी अलग से जाँच करें।')} <a href="/shared-care" target="_blank" rel="noopener noreferrer">{t('Online conversation assistance · sign in', 'ऑनलाइन बातचीत सहायता · साइन इन')}</a></p>
      </section>
    </div>
    {message && <p className="doctor-action-message" role="status">{message}</p>}
  </section>;
}
