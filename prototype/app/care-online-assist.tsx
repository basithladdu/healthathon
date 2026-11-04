'use client';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { careAssistCapabilities, careTranscribe, careTranslate, confirmedTranscriptTurn } from './care-assist-client';
import { CARE_ASSIST_LANGUAGES, careSourceHash, careTranslationChunks, type CareTranscriptResult, type CareTranslationResult } from './care-assist-contract';
import './care-online-assist.css';

function reason(error: unknown) { return error instanceof Error ? error.message : 'Could not finish. Your original content is unchanged.'; }
function Language({ value, onChange, label, disabled = false }: { value: string; onChange: (value: string) => void; label: string; disabled?: boolean }) {
  return <label>{label}<select value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled}>{CARE_ASSIST_LANGUAGES.map((language) => <option key={language.code} value={language.code}>{language.label}</option>)}</select></label>;
}

async function audioDuration(file: File, signal: AbortSignal) {
  return new Promise<number>((resolve, reject) => {
    const url = URL.createObjectURL(file); const audio = document.createElement('audio');
    const dispose = () => { clearTimeout(timer); signal.removeEventListener('abort', aborted); audio.removeAttribute('src'); audio.load(); URL.revokeObjectURL(url); };
    const aborted = () => { dispose(); reject(new Error('Stopped. Your recording is unchanged.')); };
    const timer = setTimeout(() => { dispose(); reject(new Error('The recording length could not be read. Use a WAV or M4A clip up to 30 seconds.')); }, 10000);
    audio.onloadedmetadata = () => { const duration = audio.duration * 1000; dispose(); if (!Number.isFinite(duration) || duration <= 0) reject(new Error('The recording length could not be read. Use a short WAV or M4A clip.')); else resolve(duration); };
    audio.onerror = () => { dispose(); reject(new Error('This recording could not be opened. Try a short WAV or M4A clip.')); };
    signal.addEventListener('abort', aborted, { once: true });
    if (signal.aborted) aborted(); else { audio.preload = 'metadata'; audio.src = url; }
  });
}

export function CareOnlineConversationTools({ source, identity, onAppend, token }: { source: string; identity: string; onAppend: (source: string) => void; token: () => Promise<string> }) {
  const [ready, setReady] = useState(false), [file, setFile] = useState<File>(), [url, setUrl] = useState('');
  const [language, setLanguage] = useState('en-IN'), [speaker, setSpeaker] = useState('Unknown');
  const [recordingConsent, setRecordingConsent] = useState(false), [processingConsent, setProcessingConsent] = useState(false), [checked, setChecked] = useState(false);
  const [busy, setBusy] = useState(false), [recording, setRecording] = useState(false), [starting, setStarting] = useState(false), [message, setMessage] = useState('');
  const [result, setResult] = useState<CareTranscriptResult>(), [reviewed, setReviewed] = useState('');
  const [resultSource, setResultSource] = useState('');
  const request = useRef<AbortController | null>(null), recorder = useRef<MediaRecorder | null>(null), stream = useRef<MediaStream | null>(null), timeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const capturedDuration = useRef<number | null>(null), active = useRef(true), latest = useRef({ source, identity });
  useLayoutEffect(() => { latest.current = { source, identity }; }, [source, identity]);
  useEffect(() => { const controller = new AbortController(); careAssistCapabilities(controller.signal).then((value) => setReady(value.audioReady)).catch(() => {}); return () => controller.abort(); }, []);
  useEffect(() => {
    active.current = true;
    const stop = () => { if (recorder.current?.state === 'recording') recorder.current.stop(); stream.current?.getTracks().forEach((track) => track.stop()); };
    const hidden = () => { if (document.hidden) { stop(); request.current?.abort(); } };
    document.addEventListener('visibilitychange', hidden);
    return () => { active.current = false; request.current?.abort(); stop(); if (timeout.current) clearTimeout(timeout.current); document.removeEventListener('visibilitychange', hidden); };
  }, [identity]);
  useEffect(() => { request.current?.abort(); }, [source]);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  function reset(next?: File) { request.current?.abort(); setFile(next); setUrl(next ? URL.createObjectURL(next) : ''); capturedDuration.current = null; setResult(undefined); setReviewed(''); setChecked(false); setProcessingConsent(false); setMessage(''); }
  async function start() {
    if (!recordingConsent || starting || recording || busy) return;
    setStarting(true); setMessage(''); const selectedIdentity = identity;
    try {
      if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') throw new Error('Microphone recording is unavailable here. Choose a short audio file instead.');
      const input = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!active.current || latest.current.identity !== selectedIdentity || document.hidden) { input.getTracks().forEach((track) => track.stop()); return; }
      stream.current = input;
      const mime = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm'].find((type) => MediaRecorder.isTypeSupported(type));
      const capture = new MediaRecorder(input, mime ? { mimeType: mime } : undefined); const chunks: Blob[] = []; const began = performance.now();
      capture.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
      capture.onstop = () => {
        if (timeout.current) clearTimeout(timeout.current); input.getTracks().forEach((track) => track.stop());
        if (!active.current || latest.current.identity !== selectedIdentity) return;
        const duration = performance.now() - began; const type = capture.mimeType || 'audio/webm';
        reset(new File(chunks, `conversation-turn.${type.includes('mp4') ? 'm4a' : 'webm'}`, { type })); capturedDuration.current = duration;
        setRecording(false); if (duration > 30000) setMessage('This turn exceeds 30 seconds. Keep it for manual review or record a shorter turn.');
      };
      recorder.current = capture; capture.start(250); setRecording(true);
      // Stop the actual recorder before the provider's 30-second limit; this is not proof of recognition.
      timeout.current = setTimeout(() => { if (capture.state === 'recording') capture.stop(); }, 28000);
    } catch (error) { stream.current?.getTracks().forEach((track) => track.stop()); setMessage(reason(error)); }
    finally { if (active.current) setStarting(false); }
  }
  async function transcribe() {
    if (!file || busy || !processingConsent || !recordingConsent) return;
    const controller = new AbortController(); request.current = controller; const selected = { source, identity };
    setBusy(true); setMessage('');
    try {
      const durationMs = capturedDuration.current ?? await audioDuration(file, controller.signal);
      const next = await careTranscribe(file, { language, durationMs, recordingConsent: true, processingConsent: true }, await token(), { consent: true, signal: controller.signal });
      if (!controller.signal.aborted && selected.identity === latest.current.identity && selected.source === latest.current.source) { setResult(next); setResultSource(selected.source); setReviewed(next.originalTranscript); setChecked(false); }
    } catch (error) { if (active.current) setMessage(reason(error)); }
    finally { if (active.current) setBusy(false); }
  }
  return <details className="care-online-tools"><summary>Record or transcribe a conversation turn</summary>
    <p className="care-online-hint">One person at a time, up to 30 seconds.</p>
    <Language label="Spoken language" value={language} onChange={setLanguage} disabled={busy || recording || starting} />
    <label className="care-online-check"><input type="checkbox" checked={recordingConsent} disabled={busy || recording || starting} onChange={(event) => setRecordingConsent(event.target.checked)} />Everyone here agrees to recording this turn.</label>
    <div className="care-online-row"><button type="button" className="secondary" disabled={!recordingConsent || starting || busy || Boolean(file)} onClick={recording ? () => recorder.current?.stop() : start}>{starting ? 'Opening microphone…' : recording ? 'Stop recording' : 'Record a turn'}</button><label className="care-online-file">Choose audio<input type="file" accept="audio/wav,audio/x-wav,audio/mp4,audio/m4a,audio/webm,audio/ogg,audio/mpeg" disabled={!recordingConsent || busy || recording || starting} onChange={(event) => { const next = event.target.files?.[0]; if (next && next.size > 2 * 1024 * 1024) setMessage('Choose audio under 2 MB.'); else reset(next); event.target.value = ''; }} /></label></div>
    {url && <div className="care-online-audio"><audio controls preload="metadata" src={url} aria-label="Original conversation turn" /><a href={url} download={file?.name || 'conversation-turn.webm'}>Save recording file</a><p className="care-online-hint">Save the recording file before leaving this page if you need to keep the audio.</p><button type="button" className="quiet" disabled={busy} onClick={() => reset()}>Remove recording</button></div>}
    {!ready ? <p className="care-online-hint">Online transcription is unavailable. Your recording stays here; type a reviewed transcript into the original conversation.</p> : <><label className="care-online-check"><input type="checkbox" checked={processingConsent} disabled={busy} onChange={(event) => setProcessingConsent(event.target.checked)} />Everyone agrees to sending this recording to Sarvam for transcription. Speaker identity and words must be checked.</label><div className="care-online-row"><button type="button" disabled={!file || !recordingConsent || !processingConsent || busy || recording || starting} onClick={transcribe}>{busy ? 'Transcribing…' : 'Transcribe this turn'}</button>{busy && <button type="button" className="quiet" onClick={() => request.current?.abort()}>Stop</button>}</div></>}
    {result && resultSource === source && <div className="care-online-review"><details><summary>Machine transcript · original words</summary><p className="care-online-words">{result.originalTranscript}</p></details><label>Checked words<textarea value={reviewed} maxLength={12000} rows={4} onChange={(event) => { setReviewed(event.target.value); setChecked(false); }} /></label><label>Who spoke?<select value={speaker} onChange={(event) => { setSpeaker(event.target.value); setChecked(false); }}>{['Unknown', 'Patient', 'Family / carer', 'Doctor'].map((role) => <option key={role}>{role}</option>)}</select></label><label className="care-online-check"><input type="checkbox" checked={checked} onChange={(event) => setChecked(event.target.checked)} />I checked these words against the audio, including who spoke, negation and uncertainty.</label><button type="button" disabled={!checked || !reviewed.trim()} onClick={() => { try { const turn = confirmedTranscriptTurn(result.originalTranscript, reviewed, speaker, result.language, checked); const combined = [source, turn].filter(Boolean).join('\n'); if (combined.length > 12000) throw new Error('The conversation is full. Copy these words before starting another note.'); onAppend(combined); reset(); } catch (error) { setMessage(reason(error)); } }}>Add checked turn</button></div>}
    {message && <p className="care-online-message" role="status">{message}</p>}
  </details>;
}

export function CareOnlineTranslation({ source, versionId, token }: { source: string; versionId: string; token: () => Promise<string> }) {
  const [ready, setReady] = useState(false), [from, setFrom] = useState('en-IN'), [to, setTo] = useState('hi-IN'), [consent, setConsent] = useState(false), [checked, setChecked] = useState(false), [busy, setBusy] = useState(false), [message, setMessage] = useState(''), [speaking, setSpeaking] = useState(false);
  const [consentFor, setConsentFor] = useState('');
  const consentValid = consent && consentFor === `${versionId}:${source}`;
  const [result, setResult] = useState<CareTranslationResult>(); const request = useRef<AbortController | null>(null), active = useRef(true), latest = useRef({ source, versionId });
  useLayoutEffect(() => { latest.current = { source, versionId }; }, [source, versionId]);
  useEffect(() => { const controller = new AbortController(); careAssistCapabilities(controller.signal).then((value) => setReady(value.translationReady)).catch(() => {}); return () => controller.abort(); }, []);
  useEffect(() => { active.current = true; return () => { active.current = false; request.current?.abort(); window.speechSynthesis?.cancel(); }; }, []);
  useEffect(() => { request.current?.abort(); window.speechSynthesis?.cancel(); }, [source, versionId, from, to]);
  async function translate() {
    if (busy || !consentValid) return;
    const controller = new AbortController(); request.current = controller; const selected = { source, versionId };
    setBusy(true); setMessage(''); setResult(undefined); setChecked(false); setSpeaking(false);
    try {
      const next = await careTranslate(source, { versionId, sourceHash: await careSourceHash(source), sourceLanguage: from, targetLanguage: to }, await token(), { consent: true, signal: controller.signal });
      if (!controller.signal.aborted && selected.source === latest.current.source && selected.versionId === latest.current.versionId) setResult(next);
    } catch (error) { if (active.current) setMessage(reason(error)); }
    finally { if (active.current) setBusy(false); }
  }
  function readAloud() {
    if (!result || !checked || !window.speechSynthesis) return;
    const engine = window.speechSynthesis; engine.cancel();
    if (speaking) { setSpeaking(false); return; }
    const voice = engine.getVoices().find((voice) => voice.localService && voice.lang.toLowerCase().split('-')[0] === to.toLowerCase().split('-')[0]);
    if (!voice) { setMessage('No downloaded voice is available for this language. Read the translation or install a matching voice in device settings.'); return; }
    const chunks = careTranslationChunks(result.translation, 1000); setSpeaking(true);
    chunks.forEach((chunk, index) => { const words = new SpeechSynthesisUtterance(chunk.original); words.lang = to; words.voice = voice; words.onend = () => { if (index === chunks.length - 1 && active.current) setSpeaking(false); }; words.onerror = () => { engine.cancel(); if (active.current) { setSpeaking(false); setMessage('Read aloud stopped. Your translation and original are unchanged.'); } }; engine.speak(words); });
  }
  return <details className="care-online-tools"><summary>Read in another language</summary>{!ready ? <p className="care-online-hint">Online translation is unavailable. Keep using the original note.</p> : <><div className="care-online-languages"><Language label="Original language" value={from} onChange={setFrom} disabled={busy} /><Language label="Translate to" value={to} onChange={setTo} disabled={busy} /></div><label className="care-online-check"><input type="checkbox" checked={consentValid} onChange={(event) => { setConsent(event.target.checked); setConsentFor(`${versionId}:${source}`); }} disabled={busy} />I agree to sending this note to Sarvam for translation. The original signed version stays unchanged.</label><div className="care-online-row"><button type="button" disabled={!consentValid || busy || from === to} onClick={translate}>{busy ? 'Translating…' : 'Translate this version'}</button>{busy && <button type="button" className="quiet" onClick={() => request.current?.abort()}>Stop</button>}</div></>}
    {result && result.original === source && result.versionId === versionId && result.sourceLanguage === from && result.targetLanguage === to && <section className="care-online-review"><p className="care-online-hint">AI translation · check against the original above. This does not change its signature or acknowledgement.</p><p className="care-online-words" lang={to}>{result.translation}</p><label className="care-online-check"><input type="checkbox" checked={checked} onChange={(event) => { setChecked(event.target.checked); if (!event.target.checked) { window.speechSynthesis?.cancel(); setSpeaking(false); } }} />A person who understands both languages checked names, numbers, negation, uncertainty and the complete meaning.</label>{checked && <button type="button" className="secondary" onClick={readAloud}>{speaking ? 'Stop reading' : 'Read checked translation aloud'}</button>}</section>}{message && <p className="care-online-message" role="status">{message}</p>}</details>;
}
