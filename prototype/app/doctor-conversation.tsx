'use client';

import { useEffect, useRef, useState } from 'react';
import { IconFileText, IconSparkles, IconClock } from './icons';
import { CARE_NOTE_LABELS, organiseConversation, type AssistedNote, type DoctorConversationEntry } from './doctor-conversation-state';
import type { DraftFieldKey } from './summary-state';
import { CarePdfReadError, extractCareDocumentPdf } from './care-document-extract';

type RecognitionEvent = { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> };
type Recognition = { continuous: boolean; interimResults: boolean; lang: string; start: () => void; stop: () => void; abort: () => void; onresult: ((event: RecognitionEvent) => void) | null; onerror: ((event: { error: string }) => void) | null; onend: (() => void) | null };
type RecognitionWindow = Window & { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition };

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isAssistedNote(value: unknown, source: string): value is AssistedNote {
  if (!isRecord(value) || !isRecord(value.fields) || !isRecord(value.excerpts)) return false;
  const { fields, excerpts } = value;
  return Object.keys(CARE_NOTE_LABELS).every((key) => typeof fields[key] === 'string' && typeof excerpts[key] === 'string'
    && fields[key] === excerpts[key] && (!fields[key] || source.includes(fields[key] as string)));
}

export function ConversationAudio({ file }: { file: File }) {
  const [url, setUrl] = useState('');
  useEffect(() => { const next = URL.createObjectURL(file); setUrl(next); return () => URL.revokeObjectURL(next); }, [file]);
  return <audio controls preload="metadata" src={url} aria-label="Conversation recording" />;
}

export function DoctorConversation({ patientId, patientName, patients, physician, initialNote, referenceNote, previousContext, requestContext, hindi, onPatient, onSave, onHistory }: {
  patientId: string; patientName: string; patients: Array<{ id: string; name: string }>; physician: string;
  initialNote: string; referenceNote?: string; hindi: boolean; onPatient: (id: string) => void;
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
  const [interim, setInterim] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [language, setLanguage] = useState(hindi ? 'hi-IN' : 'en-IN');
  const [aiReady, setAiReady] = useState(false);
  const recorder = useRef<MediaRecorder | null>(null);
  const sourceFileInput = useRef<HTMLInputElement | null>(null);
  const recognition = useRef<Recognition | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const mounted = useRef(true);
  const assistRequest = useRef<AbortController | null>(null);
  const preparing = useRef(false);
  const t = (en: string, hi: string) => hindi ? hi : en;

  useEffect(() => {
    mounted.current = true;
    const controller = new AbortController();
    fetch('/api/care-assist', { signal: controller.signal }).then((response) => response.json()).then((status) => setAiReady(isRecord(status) && status.ready === true)).catch(() => {});
    return () => { mounted.current = false; controller.abort(); assistRequest.current?.abort(); recognition.current?.abort(); if (recorder.current?.state === 'recording') recorder.current.stop(); stream.current?.getTracks().forEach((track) => track.stop()); };
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
        setAudio(new File(chunks, `conversation-${Date.now()}.${type.includes('mp4') ? 'm4a' : 'webm'}`, { type }));
        setRecording(false); setInterim('');
      };
      recorder.current = capture; capture.start(1000); setSeconds(0); setRecording(true);
      const speechWindow = window as RecognitionWindow;
      const Speech = speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition;
      if (Speech) {
        const speech = new Speech(); recognition.current = speech; speech.lang = language; speech.continuous = true; speech.interimResults = true;
        speech.onresult = (event) => {
          let finalText = ''; let partial = '';
          for (let index = event.resultIndex; index < event.results.length; index++) {
            const text = event.results[index][0].transcript;
            if (event.results[index].isFinal) finalText += `${text} `; else partial += text;
          }
          if (finalText.trim()) setNote((value) => `${value}${value ? '\n' : ''}${finalText.trim()}`);
          setInterim(partial);
        };
        speech.onerror = () => { if (mounted.current) setMessage('Audio is recording. Live captions are unavailable; add or edit the note below.'); };
        speech.onend = () => { if (mounted.current) { setInterim(''); if (recorder.current?.state === 'recording') setMessage('Live captions stopped. Audio is still recording; check the note below.'); } };
        try { speech.start(); } catch { setMessage('Audio is recording. Add or edit the note below.'); }
      } else setMessage('Audio is recording. Add or edit the note below.');
    } catch (error) { stream.current?.getTracks().forEach((track) => track.stop()); setMessage(error instanceof Error && error.name !== 'NotAllowedError' ? error.message : 'Microphone access was not allowed. You can type or upload instead.'); }
    finally { setStarting(false); }
  }

  function stopRecording() { recognition.current?.stop(); if (recorder.current?.state === 'recording') recorder.current.stop(); }
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
      setMessage(`Text imported from ${file.name}. Check the source before preparing the care note.`);
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
      let draft = organiseConversation(source);
      if (aiReady) {
        const request = new AbortController(); assistRequest.current = request;
        try {
          const response = await fetch('/api/care-assist', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ source }), signal: request.signal });
          const result: unknown = await response.json();
          if (response.ok && isAssistedNote(result, source) && Object.values(result.fields).some((value) => value.trim())) draft = result;
        } catch {
          // The exact-text draft remains available if the optional service cannot respond.
        }
        if (request.signal.aborted) return;
      }
      if (mounted.current) save(draft, source);
    } finally {
      preparing.current = false;
      assistRequest.current = null;
      if (mounted.current) setBusy(false);
    }
  }

  return <section className="doctor-conversation">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{physician}</span><h1>{t('Record a conversation', 'बातचीत दर्ज करें')}</h1></div><label className="doctor-source-import">Add transcript or PDF<input ref={sourceFileInput} type="file" accept="text/plain,.txt,application/pdf,.pdf" disabled={recording || starting || busy} onChange={(event) => { const file = event.target.files?.[0]; if (file) void importSourceFile(file); }} /></label><select aria-label="Patient" value={patientId} disabled={recording || starting || busy} onChange={(event) => onPatient(event.target.value)}>{patients.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select></header>
    <div className="doctor-note-steps" aria-label="Care note steps"><strong><span>1</span> {t('Conversation', 'बातचीत')}</strong><span><span>2</span> {t('Review & sign', 'जाँचें और हस्ताक्षर करें')}</span><button type="button" onClick={onHistory}><IconClock /> {t('Note history', 'पुराने नोट')}</button></div>
    {requestContext && <section className="doctor-request-context"><h2>Requested discussion</h2><p>From {requestContext.requestedBy}</p>{requestContext.topics.length > 0 && <ul>{requestContext.topics.map((topic, index) => <li key={`${topic}-${index}`}>{topic}</li>)}</ul>}{requestContext.note.trim() && <p>{requestContext.note}</p>}</section>}
    {previousContext && <details className="doctor-previous-discussion"><summary><strong>Previous signed discussion · Version {previousContext.version}</strong><time dateTime={previousContext.releasedAt}>{new Date(previousContext.releasedAt).toLocaleDateString('en-GB')}</time></summary>{previousContext.fields && <dl>{(Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]).filter((key) => {
      const value = previousContext.fields?.[key]?.trim(); return Boolean(value && value !== 'Not stated in this conversation.');
    }).map((key) => <div key={key}><dt>{CARE_NOTE_LABELS[key]}</dt><dd>{previousContext.fields?.[key]}</dd></div>)}</dl>}</details>}
    <div className="doctor-capture-grid">
      <section className={`doctor-record-card ${recording ? 'is-recording' : ''}`}>
        <div className="doctor-record-orb" aria-hidden="true"><svg viewBox="0 0 48 48"><rect x="17" y="6" width="14" height="25" rx="7"/><path d="M11 23v2a13 13 0 0 0 26 0v-2M24 38v6M17 44h14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"/></svg></div>
        <h2>{recording ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : t('Voice note', 'आवाज़ में नोट')}</h2>
        <label className="doctor-record-consent"><input type="checkbox" checked={agreed} disabled={recording || starting} onChange={(event) => setAgreed(event.target.checked)} />{t('Everyone agreed to recording and live captions', 'सभी ने रिकॉर्डिंग और लाइव कैप्शन की अनुमति दी')}</label>
        <button type="button" className="primary-button doctor-record-button" disabled={!agreed || starting || busy || Boolean(audio)} onClick={recording ? stopRecording : startRecording}>{starting ? 'Opening microphone…' : recording ? t('Stop recording', 'रिकॉर्डिंग रोकें') : t('Start recording', 'रिकॉर्डिंग शुरू करें')}</button>
        <select aria-label="Recording language" value={language} disabled={recording || starting} onChange={(event) => setLanguage(event.target.value)}><option value="en-IN">English</option><option value="hi-IN">हिन्दी</option></select>
        <label className={`doctor-audio-upload ${!agreed || audio || busy ? 'is-disabled' : ''}`}>{t('Upload audio', 'ऑडियो जोड़ें')}<input type="file" accept="audio/*" disabled={!agreed || recording || starting || busy || Boolean(audio)} onChange={(event) => { const file = event.target.files?.[0]; if (!file || audio) return; if (file.size > 30 * 1024 * 1024) { setMessage('Choose an audio file under 30 MB.'); return; } setAudio(file); event.target.value = ''; }} /></label>
        {audio && <div className="doctor-audio-review"><ConversationAudio file={audio} /><button type="button" className="care-text-button" disabled={busy} onClick={() => setAudio(undefined)}>Remove recording</button></div>}
      </section>
      <section className="doctor-transcript-card"><div className="family-section-heading"><h2><IconFileText /> {t('Conversation note', 'बातचीत का नोट')}</h2><span>{patientName.split(' ')[0]}</span></div>
        {!previousContext && referenceNote?.trim() && !note.trim() && !audio && !recording && !starting && <button className="care-text-button" type="button" disabled={busy} onClick={() => { setNote(referenceNote); setMessage(''); }}>{t('Use latest conversation', 'पिछली बातचीत लें')}</button>}
        <textarea aria-label="Conversation note" value={note} disabled={busy} maxLength={12000} rows={12} onChange={(event) => setNote(event.target.value)} placeholder={t('Record, type, or paste the conversation here.', 'बातचीत रिकॉर्ड करें, लिखें या यहाँ पेस्ट करें।')} />
        {interim && <p className="doctor-caption" aria-live="polite">{interim}</p>}
        <div className="doctor-note-actions"><button className="primary-button" type="button" disabled={recording || starting || busy || (!note.trim() && !audio)} onClick={() => { if (note.trim()) void assist(); else save(); }}>{note.trim() && <IconSparkles />}{busy ? t('Preparing care note…', 'देखभाल नोट तैयार हो रहा है…') : !note.trim() && audio ? t('Save recording', 'रिकॉर्डिंग सेव करें') : t('Prepare care note', 'देखभाल नोट तैयार करें')} →</button></div>
      </section>
    </div>
    {message && <p className="doctor-action-message" role="status">{message}</p>}
  </section>;
}
