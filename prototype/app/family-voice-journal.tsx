'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import {
  addVoiceJournalEntry, editVoiceJournalText, removeVoiceJournalEntry, restoreVoiceJournalEntry,
  selectVoiceJournalEntries, validVoiceJournalDraft, voiceJournalEntryText, type VoiceJournalEntry,
} from './family-voice-state';
import './family-voice-journal.css';

export type { VoiceJournalEntry } from './family-voice-state';
type Props = {
  patientId: string; author: string; today: string; hindi: boolean; entries: VoiceJournalEntry[]; onChange: (entries: VoiceJournalEntry[]) => void;
  saveStatus?: 'loading' | 'saving' | 'saved' | 'unavailable';
};
type RecordingPhase = 'idle' | 'requesting' | 'recording' | 'stopping';

function clipDuration(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
}

function AudioClip({ file, hindi, label }: { file: File; hindi: boolean; label: string }) {
  const audio = useRef<HTMLAudioElement>(null);
  const download = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(file);
    const player = audio.current;
    const link = download.current;
    if (player) player.src = url;
    if (link) link.href = url;
    return () => { if (player) { player.pause(); player.removeAttribute('src'); player.load(); } URL.revokeObjectURL(url); };
  }, [file]);
  return <div className="voice-journal-audio"><audio ref={audio} controls preload="metadata" aria-label={label} /><a ref={download} download={file.name}>{hindi ? 'आवाज़ डाउनलोड करें' : 'Download audio'}</a></div>;
}

function JournalRecorder({ patientId, hindi, onRecorded, onBusy, onTextFallback }: { patientId: string; hindi: boolean; onRecorded: (file: File, duration: number) => void; onBusy: (busy: boolean) => void; onTextFallback: () => void }) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [phase, setPhase] = useState<RecordingPhase>('idle');
  const [elapsed, setElapsed] = useState(0);
  const [message, setMessage] = useState('');
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const session = useRef(0);
  const ticker = useRef<number | null>(null);
  const limit = useRef<number | null>(null);
  const startedAt = useRef(0);
  const stoppedDuration = useRef<number | null>(null);

  useEffect(() => () => {
    session.current += 1;
    if (ticker.current !== null) window.clearInterval(ticker.current);
    if (limit.current !== null) window.clearTimeout(limit.current);
    const active = recorder.current;
    if (active && active.state !== 'inactive') active.stop();
    stream.current?.getTracks().forEach((track) => track.stop());
  }, []);

  function clearTimers() {
    if (ticker.current !== null) window.clearInterval(ticker.current);
    if (limit.current !== null) window.clearTimeout(limit.current);
    ticker.current = null; limit.current = null;
  }

  function stop() {
    const active = recorder.current;
    if (!active || active.state === 'inactive') return;
    stoppedDuration.current = Math.min(90, Math.max(1, Math.round((performance.now() - startedAt.current) / 1000)));
    setPhase('stopping'); clearTimers(); active.stop();
    stream.current?.getTracks().forEach((track) => track.stop());
  }

  function cancel() {
    session.current += 1; clearTimers();
    const active = recorder.current;
    if (active && active.state !== 'inactive') active.stop();
    stream.current?.getTracks().forEach((track) => track.stop());
    recorder.current = null; stream.current = null; stoppedDuration.current = null;
    setPhase('idle'); setElapsed(0); onBusy(false); setMessage(t('Recording cancelled.', 'रिकॉर्डिंग रद्द कर दी।'));
  }

  async function start() {
    if (phase !== 'idle') return;
    if (typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      setMessage(t('Recording is unavailable here. You can write a thought below.', 'यहाँ रिकॉर्डिंग नहीं हो सकती। नीचे अपनी बात लिख सकते हैं।'));
      onTextFallback();
      return;
    }
    const currentSession = ++session.current;
    setPhase('requesting'); setMessage(''); onBusy(true); stoppedDuration.current = null;
    let requestedStream: MediaStream | null = null;
    try {
      requestedStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (session.current !== currentSession) { requestedStream.getTracks().forEach((track) => track.stop()); return; }
      stream.current = requestedStream;
      const mimeType = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'].find((type) => MediaRecorder.isTypeSupported(type));
      const active = new MediaRecorder(requestedStream, mimeType ? { mimeType } : undefined);
      const chunks: Blob[] = [];
      recorder.current = active;
      active.ondataavailable = (event) => { if (event.data.size && session.current === currentSession) chunks.push(event.data); };
      active.onerror = () => {
        if (session.current !== currentSession) return;
        session.current += 1; clearTimers();
        if (active.state !== 'inactive') active.stop();
        requestedStream?.getTracks().forEach((track) => track.stop());
        recorder.current = null; stream.current = null; setPhase('idle'); onBusy(false);
        setMessage(t('Recording stopped unexpectedly. Try again, or write below.', 'रिकॉर्डिंग रुक गई। फिर कोशिश करें, या नीचे लिखें।'));
        onTextFallback();
      };
      active.onstop = () => {
        requestedStream?.getTracks().forEach((track) => track.stop());
        if (session.current !== currentSession) return;
        clearTimers(); recorder.current = null; stream.current = null; setPhase('idle'); onBusy(false);
        const type = active.mimeType || chunks[0]?.type || 'application/octet-stream';
        const blob = new Blob(chunks, { type });
        if (!blob.size || blob.size > 20 * 1024 * 1024) { setMessage(t('No usable audio was captured. Try again, or write below.', 'आवाज़ ठीक से रिकॉर्ड नहीं हुई। फिर कोशिश करें, या नीचे लिखें।')); onTextFallback(); return; }
        const duration = stoppedDuration.current ?? Math.min(90, Math.max(1, Math.round((performance.now() - startedAt.current) / 1000)));
        const extension = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : type.includes('webm') ? 'webm' : 'bin';
        const file = new File([blob], `saanthvana-voice-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}-${Date.now()}.${extension}`, { type });
        onRecorded(file, duration); setElapsed(duration); setMessage(t('Ready to listen. Save it when you’re ready.', 'सुनने के लिए तैयार है। चाहें तो सेव करें।'));
      };
      startedAt.current = performance.now(); active.start(1000); setPhase('recording'); setElapsed(0);
      ticker.current = window.setInterval(() => setElapsed(Math.min(90, Math.floor((performance.now() - startedAt.current) / 1000))), 250);
      limit.current = window.setTimeout(() => {
        if (session.current !== currentSession || active.state === 'inactive') return;
        stoppedDuration.current = 90; setPhase('stopping'); clearTimers(); active.stop(); requestedStream?.getTracks().forEach((track) => track.stop());
      }, 90000);
    } catch (error) {
      requestedStream?.getTracks().forEach((track) => track.stop());
      if (session.current !== currentSession) return;
      clearTimers(); recorder.current = null; stream.current = null; setPhase('idle'); onBusy(false);
      const denied = error instanceof DOMException && ['NotAllowedError', 'PermissionDeniedError'].includes(error.name);
      setMessage(denied ? t('Microphone access wasn’t allowed. You can still write below.', 'माइक्रोफ़ोन की अनुमति नहीं मिली। आप नीचे लिख सकते हैं।') : t('The microphone could not open. Try again, or write below.', 'माइक्रोफ़ोन नहीं खुला। फिर कोशिश करें, या नीचे लिखें।'));
      onTextFallback();
    }
  }

  return <div className={`voice-recorder${phase === 'recording' ? ' is-recording' : ''}`}>
    <button type="button" className="voice-record-button" disabled={phase === 'requesting' || phase === 'stopping'} onClick={phase === 'recording' ? stop : start}>
      {phase === 'recording' ? <span className="voice-stop-shape" aria-hidden="true" /> : <svg width="23" height="27" viewBox="0 0 24 28" fill="none" aria-hidden="true"><rect x="8" y="2" width="8" height="15" rx="4" stroke="currentColor" strokeWidth="1.8" /><path d="M4 13v2a8 8 0 0 0 16 0v-2M12 23v3M8 26h8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>}
      {phase === 'recording' ? t('Stop recording', 'रिकॉर्डिंग रोकें') : phase === 'requesting' ? t('Opening microphone…', 'माइक्रोफ़ोन खुल रहा है…') : phase === 'stopping' ? t('Finishing…', 'पूरा हो रहा है…') : t('Start recording', 'रिकॉर्डिंग शुरू करें')}
    </button>
    <div className="voice-recorder-under"><span aria-hidden="true">{phase === 'recording' || phase === 'stopping' ? `${clipDuration(elapsed)} / 1:30` : t('Up to 90 seconds', '90 सेकंड तक')}</span>{phase !== 'idle' && <button type="button" className="voice-text-button" onClick={cancel}>{t('Cancel recording', 'रिकॉर्डिंग रद्द करें')}</button>}</div>
    <p className="voice-recorder-message" role="status">{message}</p>
  </div>;
}

export function FamilyVoiceJournal(props: Props) {
  return <VoiceJournalForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function VoiceJournalForPatient({ patientId, author, today, hindi, entries, onChange, saveStatus }: Props) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [title, setTitle] = useState('');
  const [thought, setThought] = useState('');
  const [audio, setAudio] = useState<File | null>(null);
  const [durationSeconds, setDurationSeconds] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [writing, setWriting] = useState(false);
  const [recordingReset, setRecordingReset] = useState(0);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [removed, setRemoved] = useState<Array<{ entry: VoiceJournalEntry; index: number }>>([]);
  const [message, setMessage] = useState('');
  const titleInput = useRef<HTMLInputElement>(null);
  const journal = selectVoiceJournalEntries(entries, patientId);
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const ready = !busy && validVoiceJournalDraft({ title, thought, audio, durationSeconds });

  useEffect(() => { if (editingId) titleInput.current?.focus(); }, [editingId]);

  function clear() { setTitle(''); setThought(''); setAudio(null); setDurationSeconds(null); setEditingId(null); setWriting(false); setRecordingReset((value) => value + 1); }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready) return;
    const updated = editingId ? editVoiceJournalText(entries, patientId, editingId, author, title, thought)
      : addVoiceJournalEntry(entries, patientId, author, today, crypto.randomUUID(), { title, thought, audio, durationSeconds });
    if (updated === entries) { setMessage(t('This note could not be saved. Try again.', 'यह नोट सेव नहीं हुआ। फिर कोशिश करें।')); return; }
    onChange(updated); clear(); setMessage(t(editingId ? 'Your changes are saved.' : 'Added to your journal.', editingId ? 'बदलाव सेव हो गए।' : 'आपकी डायरी में जोड़ दिया।'));
  }

  function edit(entry: VoiceJournalEntry) {
    if (busy) return;
    setEditingId(entry.id); setTitle(entry.title); setThought(entry.thought); setAudio(entry.audio); setDurationSeconds(entry.durationSeconds); setWriting(true); setMessage('');
    titleInput.current?.focus();
  }

  function remove(entry: VoiceJournalEntry) {
    setRemoved((previous) => [...previous, { entry, index: entries.findIndex((item) => item.patientId === patientId && item.id === entry.id) }]);
    onChange(removeVoiceJournalEntry(entries, patientId, entry.id));
    if (editingId === entry.id) clear();
    setMessage(t('Removed from your journal.', 'डायरी से हटा दिया।'));
  }

  function undo() {
    const last = removed.at(-1);
    if (!last) return;
    const updated = restoreVoiceJournalEntry(entries, patientId, last.entry, last.index);
    if (updated === entries) return;
    onChange(updated); setRemoved((previous) => previous.slice(0, -1)); setMessage(t('Your note is back.', 'नोट वापस जोड़ दिया।'));
  }

  function downloadText(entry: VoiceJournalEntry) {
    const url = URL.createObjectURL(new Blob([voiceJournalEntryText(entry)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saanthvana-journal-${entry.id.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`;
    document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return <section className="family-voice-journal" aria-labelledby={`${id}-heading`}>
    <h1 id={`${id}-heading`}>{t('Your voice journal', 'अपनी बात कहें')}</h1>
    <div className="voice-journal-layout">
      <section className="voice-journal-compose" aria-label={t('Add to your journal', 'अपनी डायरी में जोड़ें')}>
        {editingId ? <h2>{t('Edit your words', 'अपनी बात बदलें')}</h2> : <JournalRecorder key={recordingReset} patientId={patientId} hindi={hindi} onBusy={setBusy} onTextFallback={() => setWriting(true)} onRecorded={(file, duration) => { setAudio(file); setDurationSeconds(duration); setMessage(''); }} />}
        {audio && !busy && <div className="voice-draft-clip"><AudioClip file={audio} hindi={hindi} label={t('Listen to your voice note', 'अपना वॉइस नोट सुनें')} />{!editingId && <button type="button" className="voice-text-button" onClick={() => { setAudio(null); setDurationSeconds(null); setRecordingReset((value) => value + 1); }}>{t('Remove this recording', 'यह रिकॉर्डिंग हटाएँ')}</button>}</div>}
        <form className="voice-journal-form" onSubmit={save}>
          <details className="voice-writing-options" open={writing} onToggle={(event) => setWriting(event.currentTarget.open)}><summary>{t('Add words, if you like', 'चाहें तो कुछ लिखें')}</summary>
            {!editingId && <div className="voice-journal-prompts" aria-label={t('Optional thought starters', 'चाहें तो इनमें से चुनें')}>{[['A favourite memory', 'एक प्यारी याद'], ['A person I love', 'मेरा कोई अपना'], ['A song I like', 'मेरा पसंदीदा गाना']].map(([en, hi]) => <button type="button" key={en} onClick={() => setTitle(t(en, hi))}>{t(en, hi)}</button>)}</div>}
            <div className="voice-writing-fields"><label htmlFor={`${id}-title`}>{t('Title (optional)', 'नाम (चाहें तो)')}<input ref={titleInput} id={`${id}-title`} value={title} maxLength={160} onChange={(event) => setTitle(event.target.value)} /></label>
              <label htmlFor={`${id}-thought`}>{t('A thought, if you feel like writing', 'मन हो तो कुछ लिखें')}<textarea id={`${id}-thought`} value={thought} rows={3} maxLength={6000} onChange={(event) => setThought(event.target.value)} /></label></div>
          </details>
          <div className="voice-journal-save"><button type="submit" className="voice-save-button" disabled={!ready}>{editingId ? t('Save changes', 'बदलाव सेव करें') : t('Save to journal', 'डायरी में सेव करें')}</button>{editingId && <button type="button" onClick={clear}>{t('Cancel', 'रहने दें')}</button>}</div>
        </form>
      </section>
      <section className="voice-journal-entries" aria-labelledby={`${id}-entries`}>
        <h2 id={`${id}-entries`}>{t('Your moments', 'आपकी यादें')}</h2>
        {(saveStatus === 'unavailable' || saveStatus === 'saving' || saveStatus === 'loading') && <p className={`voice-storage-status${saveStatus === 'unavailable' ? ' is-unavailable' : ''}`} role={saveStatus === 'unavailable' ? 'alert' : 'status'}>{
          saveStatus === 'unavailable' ? t('This journal could not be saved. Download your recordings and text before leaving.', 'यह डायरी सेव नहीं हो सकी। जाने से पहले अपनी रिकॉर्डिंग और लिखे नोट डाउनलोड करें।')
            : saveStatus === 'saving' ? t('Saving…', 'सेव हो रहा है…')
              : t('Loading notes…', 'नोट खुल रहे हैं…')
        }</p>}
        {!journal.length && <p className="voice-journal-empty">{t('Your first thought can be spoken or written.', 'पहली बात बोलकर या लिखकर रख सकते हैं।')}</p>}
        {journal.map((entry) => <article className="voice-journal-entry" key={entry.id}>
          <div className="voice-entry-heading"><h3>{entry.title || (entry.audio ? t('A voice note', 'एक वॉइस नोट') : t('A thought', 'एक बात'))}</h3><time dateTime={entry.date}>{dateLabel(entry.date)}</time></div>
          {entry.audio && !busy && <AudioClip file={entry.audio} hindi={hindi} label={entry.title || t('Play voice note', 'वॉइस नोट सुनें')} />}
          {entry.thought && <p className="voice-entry-thought">{entry.thought}</p>}
          <div className="voice-entry-footer"><span>{t('Added by', 'जोड़ा')} {entry.createdBy}{entry.updatedBy !== entry.createdBy && <> · {t('edited by', 'बदला')} {entry.updatedBy}</>}</span><div><button type="button" className="voice-text-button" disabled={busy} onClick={() => edit(entry)}>{t('Edit words', 'बात बदलें')}</button><button type="button" className="voice-text-button" onClick={() => downloadText(entry)}>{t('Save text', 'लिखी बात डाउनलोड करें')}</button><button type="button" className="voice-text-button" onClick={() => remove(entry)}>{t('Remove', 'हटाएँ')}</button></div></div>
        </article>)}
      </section>
    </div>
    <div className="voice-journal-status"><p role="status">{message}</p>{removed.length > 0 && <button type="button" className="voice-text-button" onClick={undo}>{t('Undo last removal', 'पिछला हटाया वापस लाएँ')}</button>}</div>
  </section>;
}
