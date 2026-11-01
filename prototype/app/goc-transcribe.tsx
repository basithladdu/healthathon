'use client';
// Goals-of-care conversation: consent, then record, upload or type; on-device transcription; a topic checklist in the form's order;
// every option for every decision, with the words that point to one. Nothing goes into the plan until the doctor confirms that item,
// nothing here signs anything, and a topic that was not discussed stays "No preference recorded" (rule V1).
// The speech engine loads only when the doctor taps Transcribe. This file loads only when the doctor opens the panel.
import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { careText, type CareLang } from './care-i18n';
import { logAudit } from './ectpr-store';
import {
  applyGocDecision, applyGocPerson, applyGocQuote, extractGoc, gocCarryForward, gocCurrentValue, gocFieldLock, gocLinesFromText, gocMarksFor, gocTextFromLines,
  GOC_DECISION_FIELDS, type GocDecisionField, type GocExtraction, type GocHomeCareHint, type GocLine, type GocPersonHint, type GocSpeaker, type GocSuggestion,
} from './goc-extract';
import { buildGocNote, gocNoteKind, gocTopicName, gocTopicRows } from './goc-note';
import { GOC_LONGEST_SECONDS, GOC_MODELS, GocSpeechError, decodeToSamples, gocDefaultModel, transcribeSamples, type GocModelKey, type GocProgress } from './goc-transcribe-model';
import type { EctprRecord, GocTopicMark, GocTranscript } from './ectpr-model';

type T = (en: string, hi: string) => string;
type GocOption = { value: string; en: string; hi: string; helpEn?: string; helpHi?: string };
export type GocOptionSets = Record<GocDecisionField, GocOption[]>;
type SpokenLanguage = GocTranscript['language'];

const LANGUAGES: Array<{ value: SpokenLanguage; en: string; hi: string }> = [
  { value: 'auto', en: 'Mixed or not sure (detect for each part)', hi: 'मिली-जुली या पता नहीं (हर हिस्से के लिए पहचानें)' },
  { value: 'en', en: 'English', hi: 'अंग्रेज़ी' },
  { value: 'hi', en: 'Hindi', hi: 'हिन्दी' },
  { value: 'te', en: 'Telugu', hi: 'तेलुगु' },
  { value: 'ur', en: 'Urdu', hi: 'उर्दू' },
  { value: 'kn', en: 'Kannada', hi: 'कन्नड़' },
];

const NO_PREFERENCE = '__none';
const HARD_TO_HEAR = /[ఀ-౿ಀ-೿؀-ۿ]/u;

function clock(seconds: number) {
  const whole = Math.max(0, Math.round(seconds));
  const h = Math.floor(whole / 3600); const m = Math.floor((whole % 3600) / 60); const s = whole % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

function speechErrorText(t: T, error: unknown): string {
  const code = error instanceof GocSpeechError ? error.code : 'engine';
  switch (code) {
    case 'offline': return t('You are offline, so the speech model could not load. Type or paste the conversation below.', 'आप ऑफ़लाइन हैं, इसलिए स्पीच मॉडल लोड नहीं हुआ। बातचीत नीचे टाइप या पेस्ट करें।');
    case 'network': return t('The speech model could not be downloaded. Check the internet, or type or paste the conversation below.', 'स्पीच मॉडल डाउनलोड नहीं हुआ। इंटरनेट जाँचें, या बातचीत नीचे टाइप या पेस्ट करें।');
    case 'memory': return t('This device does not have enough free memory for the speech model. Close other apps, or type or paste the conversation below.', 'इस डिवाइस में स्पीच मॉडल के लिए काफ़ी मेमोरी नहीं है। दूसरे ऐप बंद करें, या बातचीत नीचे टाइप या पेस्ट करें।');
    case 'unsupported': return t('This browser cannot run the speech model. Type or paste the conversation below.', 'यह ब्राउज़र स्पीच मॉडल नहीं चला सकता। बातचीत नीचे टाइप या पेस्ट करें।');
    case 'too-long': return t('This recording is longer than one hour. Split it, or type or paste the conversation below.', 'यह रिकॉर्डिंग एक घंटे से लंबी है। इसे बाँटें, या बातचीत नीचे टाइप या पेस्ट करें।');
    case 'decode': return t('This audio file could not be read. Try another file, or type or paste the conversation below.', 'यह ऑडियो फ़ाइल पढ़ी नहीं जा सकी। दूसरी फ़ाइल आज़माएँ, या बातचीत नीचे टाइप या पेस्ट करें।');
    case 'integrity': return t('The speech engine did not match its checksum, so it was not run. Type or paste the conversation below.', 'स्पीच इंजन का चेकसम मेल नहीं खाया, इसलिए उसे नहीं चलाया गया। बातचीत नीचे टाइप या पेस्ट करें।');
    default: return t('The speech model stopped. Any lines already made are kept below. You can type or paste the rest.', 'स्पीच मॉडल रुक गया। जो पंक्तियाँ बन चुकीं वे नीचे हैं। बाक़ी आप टाइप या पेस्ट कर सकते हैं।');
  }
}

export function GocTranscribe({ t, lang, draft, previous, actorName, patientId, edit, options, onClose, onGoToSign }: {
  t: T; lang: CareLang; draft: EctprRecord; previous: EctprRecord | null; actorName: string; patientId: string;
  edit: (change: (record: EctprRecord) => void) => void; options: GocOptionSets; onClose: () => void; onGoToSign: () => void;
}) {
  const saved = draft.gocTranscript;
  const [consentAt, setConsentAt] = useState(saved?.consentAt ?? '');
  const [language, setLanguage] = useState<SpokenLanguage>(saved?.language ?? 'auto');
  const [modelChoice, setModelChoice] = useState<GocModelKey | null>(null);
  const [source, setSource] = useState<GocTranscript['source']>(saved?.source ?? 'TYPED');
  const [machineDraft, setMachineDraft] = useState(saved?.machineDraft ?? false);
  const [audio, setAudio] = useState<Blob | null>(null);
  const [audioUrl, setAudioUrl] = useState('');
  const [audioKept, setAudioKept] = useState(saved?.audioKept ?? false);
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [progress, setProgress] = useState<GocProgress | 'preparing' | null>(null);
  const [message, setMessage] = useState('');
  const [paste, setPaste] = useState('');
  const [lines, setLines] = useState<GocLine[]>(saved?.lines ?? []);
  const [reviewed, setReviewed] = useState(Boolean(saved));
  const [found, setFound] = useState<GocExtraction | null>(null);
  const [aiSuggestions, setAiSuggestions] = useState<GocSuggestion[]>([]);
  const [picks, setPicks] = useState<Partial<Record<GocDecisionField, string>>>({});
  const [ignored, setIgnored] = useState<Partial<Record<GocDecisionField, boolean>>>({});
  const [accepted, setAccepted] = useState<GocTranscript['accepted']>(saved?.accepted ?? []);
  const [doneQuotes, setDoneQuotes] = useState<Record<string, 'added' | 'ignored'>>({});
  const [marks, setMarks] = useState<Partial<Record<string, GocTopicMark>>>(saved?.topics ?? {});
  const [carriedConfirmed, setCarriedConfirmed] = useState(false);
  const [note, setNote] = useState(saved?.note ?? '');
  const [savedAt, setSavedAt] = useState(saved?.reviewedAt ?? '');
  const [audioDeleted, setAudioDeleted] = useState(false);
  const [ai, setAi] = useState<{ available: boolean; label: string } | null>(null);
  const [aiConsent, setAiConsent] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiMessage, setAiMessage] = useState('');
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const cancel = useRef<AbortController | null>(null);
  const panel = useRef<HTMLElement>(null);
  const audioUrlRef = useRef('');

  /** Holds the recording in memory only. Replacing or clearing it frees the old copy. */
  function holdAudio(next: Blob | null) {
    if (audioUrlRef.current) URL.revokeObjectURL(audioUrlRef.current);
    audioUrlRef.current = next ? URL.createObjectURL(next) : '';
    setAudio(next);
    setAudioUrl(audioUrlRef.current);
  }

  useEffect(() => { panel.current?.scrollIntoView({ block: 'start', behavior: 'smooth' }); }, []);
  useEffect(() => () => { cancel.current?.abort(); stream.current?.getTracks().forEach((track) => track.stop()); }, []);
  useEffect(() => () => { if (audioUrlRef.current) URL.revokeObjectURL(audioUrlRef.current); }, []);
  useEffect(() => {
    if (!recording) return;
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [recording]);

  const consented = Boolean(consentAt);
  const modelKey = modelChoice ?? gocDefaultModel(language);
  const modelMb = Math.round(GOC_MODELS[modelKey].bytes / 1_000_000);
  const running = progress !== null;
  const typedText = gocTextFromLines(lines);
  const deferredLines = useDeferredValue(lines);
  const liveCoverage = useMemo(() => extractGoc(deferredLines).topics, [deferredLines]);
  const rows = useMemo(() => gocTopicRows(liveCoverage, marks), [liveCoverage, marks]);
  const nextTopic = rows.find((row) => row.status === 'NOT_DISCUSSED' && !row.verified)?.topic ?? null;
  const allSuggestions = useMemo(() => [...(found?.suggestions ?? []), ...aiSuggestions], [found, aiSuggestions]);
  const carried = useMemo(() => (previous ? gocCarryForward(previous, draft) : []), [previous, draft]);
  const flagAudio = machineDraft && (language === 'te' || language === 'ur' || language === 'kn' || language === 'auto' || HARD_TO_HEAR.test(typedText));

  async function startRecording() {
    setMessage('');
    try {
      if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') throw new Error('unsupported');
      const input = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.current = input;
      const mimeType = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm'].find((type) => MediaRecorder.isTypeSupported(type));
      const capture = new MediaRecorder(input, { audioBitsPerSecond: 32000, ...(mimeType ? { mimeType } : {}) });
      const parts: Blob[] = [];
      capture.ondataavailable = (event) => { if (event.data.size) parts.push(event.data); };
      capture.onstop = () => {
        input.getTracks().forEach((track) => track.stop());
        setRecording(false);
        holdAudio(new Blob(parts, { type: capture.mimeType || 'audio/webm' }));
        setSource('RECORDED');
      };
      recorder.current = capture;
      capture.start(10_000);
      setSeconds(0); setRecording(true); holdAudio(null); setAudioKept(false);
    } catch {
      setMessage(t('The microphone could not start. Allow the microphone, upload a recording, or type the conversation below.', 'माइक्रोफ़ोन शुरू नहीं हुआ। माइक्रोफ़ोन की अनुमति दें, रिकॉर्डिंग अपलोड करें, या बातचीत नीचे टाइप करें।'));
    }
  }

  function chooseFile(file: File | undefined) {
    if (!file) return;
    setMessage('');
    if (!file.type.startsWith('audio/') && !file.type.startsWith('video/') && !/\.(m4a|mp3|wav|ogg|opus|webm|aac|flac)$/i.test(file.name)) {
      setMessage(t('Choose an audio file, such as MP3, M4A, WAV or WebM.', 'ऑडियो फ़ाइल चुनें, जैसे MP3, M4A, WAV या WebM।'));
      return;
    }
    holdAudio(file); setSource('UPLOADED'); setAudioKept(false);
  }

  const appendText = useCallback((text: string) => {
    setLines((current) => [...current, ...gocLinesFromText(text)]);
  }, []);

  async function transcribe() {
    if (!audio || running) return;
    setMessage(''); setProgress('preparing'); setMachineDraft(true); setReviewed(false); setFound(null);
    const controller = new AbortController();
    cancel.current = controller;
    try {
      const samples = await decodeToSamples(audio);
      await transcribeSamples(samples, { language, model: modelKey, signal: controller.signal, onProgress: setProgress, onText: appendText });
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) setMessage(speechErrorText(t, error));
    } finally {
      setProgress(null);
      cancel.current = null;
    }
  }

  function usePaste() {
    const next = gocLinesFromText(paste);
    if (!next.length) return;
    setLines((current) => [...current, ...next]);
    setPaste(''); setReviewed(false); setFound(null);
  }

  function changeLine(index: number, patch: Partial<GocLine>) {
    setLines((current) => current.flatMap((line, at) => {
      if (at !== index) return [line];
      const next = { ...line, ...patch };
      if (typeof patch.text === 'string' && patch.text.includes('\n')) return patch.text.split('\n').map((text) => ({ text, speaker: next.speaker }));
      return [next];
    }));
    setReviewed(false);
  }

  function findChoices() {
    const clean = lines.filter((line) => line.text.trim());
    setLines(clean);
    setFound(extractGoc(clean));
    setAiSuggestions([]);
    setPicks({}); setIgnored({});
    if (ai === null) {
      fetch('/api/goc-extract').then((res) => (res.ok ? res.json() as Promise<{ available?: boolean; label?: string }> : null)).then((value) => setAi({ available: Boolean(value?.available), label: String(value?.label ?? '') })).catch(() => setAi({ available: false, label: '' }));
    }
  }

  async function readWithAi() {
    if (!aiConsent || aiBusy) return;
    setAiBusy(true); setAiMessage('');
    try {
      const res = await fetch('/api/goc-extract', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ text: typedText, consent: true }) });
      const value = await res.json() as { error?: string; suggestions?: GocSuggestion[]; answeredBy?: string };
      if (!res.ok) throw new Error(String(value?.error ?? ''));
      setAiSuggestions(value.suggestions ?? []);
      setAiMessage(t(`${(value.suggestions ?? []).length} more suggestions from ${value.answeredBy}. Check each one.`, `${value.answeredBy} से ${(value.suggestions ?? []).length} और सुझाव। हर एक जाँचें।`));
      void logAudit({ actor: actorName, action: 'SHARE', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `Transcript sent to ${value.answeredBy} for reading, with consent` });
    } catch {
      setAiMessage(t('The AI reading did not finish. The suggestions above are unchanged.', 'AI से पढ़ना पूरा नहीं हुआ। ऊपर के सुझाव वही हैं।'));
    } finally {
      setAiBusy(false);
    }
  }

  function confirmDecision(field: GocDecisionField) {
    const pick = picks[field];
    if (!pick) return;
    const value = pick === NO_PREFERENCE ? null : pick;
    const own = allSuggestions.filter((s) => s.field === field);
    const match = own.find((s) => value !== null && s.values.includes(value));
    const at = new Date().toISOString();
    edit((record) => { applyGocDecision(record, field, value, { reasons: match?.reasons, maxAgents: match?.maxAgents }); });
    setAccepted((current) => [...current.filter((item) => item.field !== field), { field, value, quote: match?.quote ?? '', at }]);
    void logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `Conversation transcript: doctor confirmed ${field} = ${value ?? 'No preference recorded'}` });
  }

  function markTopic(topic: string, status: GocTopicMark['status'] | null) {
    setMarks((current) => {
      const next = { ...current };
      if (status) next[topic] = { status, verifiedBy: actorName, verifiedAt: new Date().toISOString() };
      else delete next[topic];
      return next;
    });
  }

  function makeNote() {
    setNote(buildGocNote(draft, rows, lang, { reviewedBy: actorName, reviewedAt: new Date().toISOString() }));
  }

  function save() {
    const at = new Date().toISOString();
    const transcript: GocTranscript = {
      lines: lines.filter((line) => line.text.trim()), language, source, machineDraft, ...(machineDraft ? { model: GOC_MODELS[modelKey].modelId } : {}),
      consentAt, reviewedBy: actorName, reviewedAt: at, audioKept, accepted, topics: marks, note,
    };
    edit((record) => { record.gocTranscript = transcript; });
    setSavedAt(at);
    if (!audioKept && audio) { holdAudio(null); setAudioDeleted(true); }
    void logAudit({
      actor: actorName, action: 'REVIEW', recordType: 'ECTPR', recordId: draft.id, patientId,
      detail: `Conversation transcript reviewed and saved: ${transcript.lines.length} lines, ${accepted.length} choices confirmed, ${Object.values(marks).filter((m) => m?.status === 'NOT_DISCUSSED').length} topics verified as not discussed, audio ${audioKept ? 'kept by the doctor' : audio ? 'deleted' : 'none'}${carriedConfirmed ? ', carried-over choices checked' : ''}`,
    });
  }

  const progressPercent = progress && progress !== 'preparing'
    ? progress.stage === 'download' ? Math.min(100, (progress.loaded / progress.total) * 100) : (progress.done / Math.max(1, progress.total)) * 100 : 0;

  return <section className="ep-card ep-goc" ref={panel} aria-label={t('Conversation transcript', 'बातचीत की लिखावट')}>
    <div className="ep-goc-head">
      <h2>{t('Conversation transcript', 'बातचीत की लिखावट')}</h2>
      <button type="button" className="ep-btn ep-quiet" onClick={onClose}>{t('Close', 'बंद करें')}</button>
    </div>
    <p className="ep-hint">{t('This helps you write down what was said. It does not recommend any treatment. Nothing goes into the plan until you confirm it, and nothing here signs the plan.', 'इससे आप लिख सकते हैं कि क्या कहा गया। यह कोई इलाज नहीं सुझाता। आपकी पुष्टि के बिना कुछ भी योजना में नहीं जाता, और यहाँ से योजना साइन नहीं होती।')}</p>

    {previous ? <details className="ep-goc-block" open>
      <summary>{t(`Since version ${previous.version}`, `संस्करण ${previous.version} के बाद से`)}</summary>
      <p className="ep-hint">{t('This draft starts from the last signed version, so only changes need editing. These are still a draft until you sign. An empty choice stays No preference recorded.', 'यह ड्राफ़्ट पिछले साइन किए गए संस्करण से शुरू होता है, इसलिए सिर्फ़ बदलाव करने हैं। साइन होने तक ये ड्राफ़्ट ही हैं। ख़ाली फ़ैसला “कोई पसंद दर्ज नहीं” ही रहता है।')}</p>
      <ul className="ep-goc-carry">{carried.filter((row) => row.previous !== null || row.current !== null).map((row) => {
        const name = (value: string | null) => { const option = options[row.field].find((item) => item.value === value); return option ? t(option.en, option.hi) : t('No preference recorded', 'कोई पसंद दर्ज नहीं'); };
        return <li key={row.field} data-changed={row.changed || undefined}><strong>{gocTopicName(row.field, lang)}</strong><span>{row.changed ? t(`Changed: ${name(row.previous)} to ${name(row.current)}`, `बदला: ${name(row.previous)} से ${name(row.current)}`) : t(`Same as last time: ${name(row.current)}`, `पिछली बार जैसा: ${name(row.current)}`)}</span></li>;
      })}</ul>
      {previous.gocTranscript?.note ? <details><summary>{t('Last conversation note', 'पिछली बातचीत का नोट')}</summary><p className="ep-goc-note-text" dir="auto">{previous.gocTranscript.note}</p></details> : null}
      <label className="ep-option"><input type="checkbox" checked={carriedConfirmed} onChange={(event) => setCarriedConfirmed(event.target.checked)} /><span><strong>{t('I have checked the carried-over choices with the patient or family', 'मैंने पिछले फ़ैसले मरीज़ या परिवार के साथ जाँच लिए')}</strong></span></label>
    </details> : null}

    <label className="ep-option">
      <input type="checkbox" checked={consented} onChange={(event) => setConsentAt(event.target.checked ? new Date().toISOString() : '')} />
      <span><strong>{t('Everyone in the room has agreed to this conversation being recorded and written down', 'कमरे में मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं')}</strong>
        <small>{t('The audio stays on this device. It is deleted after your review unless you keep a copy.', 'ऑडियो इसी डिवाइस पर रहता है। आपकी समीक्षा के बाद यह मिट जाता है, जब तक आप कॉपी न रखें।')}</small></span>
    </label>

    <fieldset className="ep-group" disabled={!consented}>
      <legend>{t('Topics to talk through', 'जिन विषयों पर बात करनी है')}</legend>
      <p className="ep-hint">{t('In the order of the form. A topic is ticked once the transcript covers it. A tick does not record any choice. A topic not discussed stays No preference recorded, and you verify that.', 'फ़ॉर्म के क्रम में। लिखावट में बात आने पर विषय टिक हो जाता है। टिक से कोई फ़ैसला दर्ज नहीं होता। जिस विषय पर बात नहीं हुई वह “कोई पसंद दर्ज नहीं” रहता है, और इसकी जाँच आप करते हैं।')}</p>
      <ol className="ep-goc-topics">{rows.map((row) => <li key={row.topic} data-status={row.status} data-next={row.topic === nextTopic || undefined}>
        <span className="ep-goc-tick" aria-hidden="true">{row.status === 'DISCUSSED' ? '✓' : ''}</span>
        <span className="ep-goc-topic-name"><strong>{gocTopicName(row.topic, lang)}</strong>
          <small>{row.status === 'DISCUSSED' ? t('Discussed', 'बात हुई') : row.verified ? t(`Not discussed, verified by ${row.verifiedBy}. No preference recorded.`, `बात नहीं हुई, ${row.verifiedBy} ने जाँचा। कोई पसंद दर्ज नहीं।`) : row.topic === nextTopic ? t('Talk about this next', 'अब इस पर बात करें') : t('Not discussed', 'बात नहीं हुई')}</small></span>
        {lines.length ? <span className="ep-goc-topic-actions">
          {row.status === 'NOT_DISCUSSED' && !row.verified ? <button type="button" className="ep-link" onClick={() => markTopic(row.topic, 'NOT_DISCUSSED')}>{t('Verify: not discussed', 'जाँचा: बात नहीं हुई')}</button> : null}
          {row.status === 'DISCUSSED' && !row.verified ? <button type="button" className="ep-link" onClick={() => markTopic(row.topic, 'NOT_DISCUSSED')}>{t('It was not discussed', 'इस पर बात नहीं हुई')}</button> : null}
          {row.status === 'NOT_DISCUSSED' && !row.verified && !row.quote ? <button type="button" className="ep-link" onClick={() => markTopic(row.topic, 'DISCUSSED')}>{t('It was discussed', 'इस पर बात हुई')}</button> : null}
          {row.verified ? <button type="button" className="ep-link" onClick={() => markTopic(row.topic, null)}>{t('Undo', 'वापस लें')}</button> : null}
        </span> : null}
      </li>)}</ol>
    </fieldset>

    <fieldset className="ep-group" disabled={!consented}>
      <legend>{t('Record, upload or type', 'रिकॉर्ड, अपलोड या टाइप करें')}</legend>
      <div className="ep-field" style={{ marginBottom: 12 }}>
        <label htmlFor="goc-language">{t('Language spoken', 'बोली गई भाषा')}</label>
        <select id="goc-language" value={language} onChange={(event) => setLanguage(event.target.value as SpokenLanguage)}>
          {LANGUAGES.map((item) => <option key={item.value} value={item.value}>{t(item.en, item.hi)}</option>)}
        </select>
      </div>
      <div className="ep-actions">
        {recording
          ? <button type="button" className="ep-btn ep-danger" onClick={() => recorder.current?.stop()}>{t(`Stop recording (${clock(seconds)})`, `रिकॉर्डिंग रोकें (${clock(seconds)})`)}</button>
          : <button type="button" className="ep-btn ep-quiet" disabled={running} onClick={() => void startRecording()}>{t('Record', 'रिकॉर्ड करें')}</button>}
        <label className="ep-btn ep-quiet ep-goc-file" data-disabled={recording || running || !consented || undefined}>
          {t('Upload a recording', 'रिकॉर्डिंग अपलोड करें')}
          <input type="file" accept="audio/*,video/webm,.m4a,.mp3,.wav,.ogg,.opus,.webm" disabled={recording || running || !consented} onChange={(event) => { chooseFile(event.target.files?.[0]); event.target.value = ''; }} />
        </label>
      </div>
      <div className="ep-field" style={{ marginBottom: 12 }}>
        <label htmlFor="goc-model">{t('Speech model', 'स्पीच मॉडल')}</label>
        <select id="goc-model" value={modelKey} disabled={running} onChange={(event) => setModelChoice(event.target.value as GocModelKey)}>
          <option value="base">{t(`Faster: about as long as the talk (${Math.round(GOC_MODELS.base.bytes / 1_000_000)} MB)`, `तेज़: लगभग बातचीत जितना समय (${Math.round(GOC_MODELS.base.bytes / 1_000_000)} MB)`)}</option>
          <option value="small">{t(`More accurate: about 3 to 4 times as long as the talk (${Math.round(GOC_MODELS.small.bytes / 1_000_000)} MB)`, `ज़्यादा सटीक: बातचीत से लगभग 3 से 4 गुना समय (${Math.round(GOC_MODELS.small.bytes / 1_000_000)} MB)`)}</option>
        </select>
      </div>
      {language === 'te' || language === 'kn' ? <p className="ep-notice ep-warn">{t('For Telugu and Kannada this small model gets most words wrong, and it may write them in English letters. Typing is more reliable.', 'तेलुगु और कन्नड़ में यह छोटा मॉडल ज़्यादातर शब्द ग़लत लिखता है, और अंग्रेज़ी अक्षरों में लिख सकता है। टाइप करना ज़्यादा भरोसेमंद है।')}</p> : null}
      {audioUrl ? <div className="ep-goc-block">
        <audio controls src={audioUrl} preload="metadata" aria-label={t('Conversation recording', 'बातचीत की रिकॉर्डिंग')} />
        <p className="ep-hint">{t(`The first time, this downloads the free speech model, about ${modelMb} MB, once. Use Wi-Fi if you can. The audio is not sent anywhere.`, `पहली बार यह मुफ़्त स्पीच मॉडल डाउनलोड करता है, लगभग ${modelMb} MB, एक बार। हो सके तो Wi-Fi इस्तेमाल करें। ऑडियो कहीं नहीं भेजा जाता।`)}</p>
        <p className="ep-hint">{t(`Long talks: up to ${GOC_LONGEST_SECONDS / 60} minutes are accepted. Times above were measured on a good laptop, and a phone or an older computer takes much longer. Keep this page open. The time left is shown once it starts. For a full hour, typing or pasting the notes may be quicker.`, `लंबी बातचीत: ${GOC_LONGEST_SECONDS / 60} मिनट तक स्वीकार है। ऊपर के समय एक अच्छे लैपटॉप पर मापे गए हैं, फ़ोन या पुराने कंप्यूटर पर बहुत ज़्यादा लगता है। यह पेज खुला रखें। शुरू होने पर बाक़ी समय दिखता है। पूरे एक घंटे के लिए नोट टाइप या पेस्ट करना जल्दी हो सकता है।`)}</p>
        <div className="ep-actions">
          {running ? <button type="button" className="ep-btn ep-quiet" onClick={() => cancel.current?.abort()}>{t('Stop transcribing', 'लिखना रोकें')}</button>
            : <button type="button" className="ep-btn" onClick={() => void transcribe()}>{t('Transcribe on this device', 'इसी डिवाइस पर लिखें')}</button>}
          <a className="ep-btn ep-quiet" href={audioUrl} download={`sahara-conversation-${new Date().toISOString().slice(0, 10)}.${audio?.type.includes('mp4') ? 'm4a' : 'webm'}`} onClick={() => setAudioKept(true)}>{t('Keep a copy of the audio', 'ऑडियो की कॉपी रखें')}</a>
          <button type="button" className="ep-btn ep-danger" disabled={running} onClick={() => { holdAudio(null); setAudioKept(false); }}>{t('Delete the audio', 'ऑडियो मिटाएँ')}</button>
        </div>
      </div> : null}
      {progress ? <div className="ep-goc-block" role="status">
        <div className="ep-progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progressPercent)}><div className="ep-progress-fill" style={{ width: `${progressPercent}%` }} /></div>
        <p className="ep-hint">{progress === 'preparing' ? t('Reading the audio…', 'ऑडियो पढ़ा जा रहा है…')
          : progress.stage === 'download' ? t(`Getting the speech model: ${Math.round(progress.loaded / 1_000_000)} of about ${Math.round(progress.total / 1_000_000)} MB`, `स्पीच मॉडल आ रहा है: लगभग ${Math.round(progress.total / 1_000_000)} में से ${Math.round(progress.loaded / 1_000_000)} MB`)
            : progress.secondsLeft === null ? t(`Writing part 1 of ${progress.total}…`, `${progress.total} में से भाग 1 लिखा जा रहा है…`)
              : t(`Written ${progress.done} of ${progress.total} parts. About ${clock(progress.secondsLeft)} left on this device.`, `${progress.total} में से ${progress.done} भाग लिखे गए। इस डिवाइस पर लगभग ${clock(progress.secondsLeft)} बाक़ी।`)}</p>
      </div> : null}
      {message ? <p className="ep-notice ep-alert" role="alert">{message}</p> : null}
      <div className="ep-field" style={{ marginTop: 12 }}>
        <label htmlFor="goc-paste">{t('Or type or paste the conversation', 'या बातचीत टाइप या पेस्ट करें')}</label>
        <textarea id="goc-paste" rows={4} dir="auto" value={paste} onChange={(event) => setPaste(event.target.value)} aria-describedby="goc-paste-hint" />
        <p className="ep-hint" id="goc-paste-hint">{t('This always works, with no download. You can start a line with “Doctor:” or “Family:”.', 'यह हमेशा चलता है, बिना डाउनलोड। आप लाइन “Doctor:” या “Family:” से शुरू कर सकते हैं।')}</p>
      </div>
      <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={!paste.trim()} onClick={usePaste}>{t('Add this text', 'यह लिखावट जोड़ें')}</button></div>
    </fieldset>

    {lines.length ? <fieldset className="ep-group">
      <legend>{t('Check the transcript', 'लिखावट जाँचें')}</legend>
      {machineDraft ? <p className="ep-notice ep-warn">{t('This is a machine draft. It will have mistakes, and it may be written in the wrong script. Correct it against the audio.', 'यह मशीन का ड्राफ़्ट है। इसमें ग़लतियाँ होंगी, और लिपि भी ग़लत हो सकती है। ऑडियो सुनकर ठीक करें।')}</p> : null}
      {flagAudio ? <p className="ep-notice ep-alert">{t('Telugu, Urdu and Kannada are often transcribed badly. Check every line against the audio, or type it yourself.', 'तेलुगु, उर्दू और कन्नड़ अक्सर ठीक से नहीं लिखे जाते। हर लाइन ऑडियो से मिलाएँ, या ख़ुद टाइप करें।')}</p> : null}
      <p className="ep-hint">{t('Mark each line as the doctor side or the patient side. Sahara cannot tell who is speaking by itself. Lines from the doctor side are not used as suggested choices.', 'हर लाइन पर बताएँ कि वह डॉक्टर की तरफ़ से है या मरीज़ की तरफ़ से। Sahara ख़ुद नहीं पहचान सकता कि कौन बोल रहा है। डॉक्टर की तरफ़ की लाइनें सुझाव नहीं बनतीं।')}</p>
      <ol className="ep-goc-lines">{lines.map((line, index) => <li key={index}>
        <textarea aria-label={t(`Line ${index + 1}`, `लाइन ${index + 1}`)} rows={2} dir="auto" value={line.text} onChange={(event) => changeLine(index, { text: event.target.value })} />
        <div role="group" aria-label={t(`Who said line ${index + 1}`, `लाइन ${index + 1} किसने कही`)} className="ep-goc-sides">
          {([['DOCTOR', t('Doctor side', 'डॉक्टर की तरफ़')], ['PATIENT_PARTY', t('Patient side', 'मरीज़ की तरफ़')]] as Array<[GocSpeaker, string]>).map(([side, label]) =>
            <button key={String(side)} type="button" aria-pressed={line.speaker === side} onClick={() => changeLine(index, { speaker: line.speaker === side ? null : side })}>{label}</button>)}
        </div>
      </li>)}</ol>
      <div className="ep-actions"><button type="button" className="ep-link" onClick={() => setLines((current) => [...current, { text: '', speaker: null }])}>{t('Add a line', 'लाइन जोड़ें')}</button></div>
      <label className="ep-option" style={{ marginTop: 10 }}>
        <input type="checkbox" checked={reviewed} disabled={running} onChange={(event) => setReviewed(event.target.checked)} />
        <span><strong>{t('I have read this transcript and corrected it', 'मैंने यह लिखावट पढ़ ली और ठीक कर दी')}</strong></span>
      </label>
      <div className="ep-actions"><button type="button" className="ep-btn" disabled={!reviewed || running} onClick={findChoices}>{t('Show the choices', 'फ़ैसले दिखाएँ')}</button></div>
    </fieldset> : null}

    {found ? <>
      <h3 className="ep-goc-title">{t('All the choices', 'सभी फ़ैसले')}</h3>
      <p className="ep-hint">{t('Every option in the plan is shown. “Said in the talk” marks words from the transcript, not advice. Tap an option, then confirm that item. Nothing is chosen for you.', 'योजना का हर विकल्प दिखता है। “बातचीत में कहा” लिखावट के शब्द बताता है, सलाह नहीं। विकल्प चुनें, फिर उसकी पुष्टि करें। आपके लिए कुछ नहीं चुना जाता।')}</p>
      {[...GOC_DECISION_FIELDS].sort((a, b) => Number(gocMarksFor(allSuggestions, b).own.length > 0) - Number(gocMarksFor(allSuggestions, a).own.length > 0)).map((field) => {
        const info = gocMarksFor(allSuggestions, field);
        const lock = gocFieldLock(draft, field);
        const current = gocCurrentValue(draft, field);
        const pick = picks[field];
        const done = accepted.find((item) => item.field === field);
        const discussed = rows.find((row) => row.topic === field)?.status === 'DISCUSSED';
        return <details key={field} className="ep-group ep-goc-decision" open={info.own.length > 0 || undefined}>
          <summary><strong>{gocTopicName(field, lang)}</strong>
            <span className="ep-chip" data-tone={info.conflict ? 'conflict' : info.own.length ? 'said' : 'none'}>{info.conflict ? t('Said different things', 'अलग-अलग बातें कहीं') : info.own.length ? t('Said in the talk', 'बातचीत में कहा') : discussed ? t('Discussed, no choice stated', 'बात हुई, फ़ैसला नहीं बताया') : t('Not discussed', 'बात नहीं हुई')}</span></summary>
          {lock ? <p className="ep-notice ep-warn">{lock === 'V4'
            ? t('Locked. First record that the illness has been explained as not curable and the family can say it back.', 'बंद है। पहले दर्ज करें कि बीमारी के बारे में समझाया गया है कि यह ठीक नहीं हो सकती, और परिवार इसे दोहरा सकता है।')
            : t('Locked. The person answering for the patient must first promise to say what the patient would choose.', 'बंद है। मरीज़ की ओर से जवाब देने वाले पहले वादा करें कि वे वही बताएँगे जो मरीज़ चुनते।')}</p> : null}
          {info.own.some((s) => !s.values.length) ? <p className="ep-hint">{t('The talk said no to the options marked below. It did not say which level is the highest allowed. Choose one only if you know.', 'बातचीत में नीचे चिह्नित विकल्पों को मना किया गया। यह नहीं बताया कि सबसे ऊँचा स्तर कौन सा है। पता हो तभी चुनें।')}</p> : null}
          {info.conflict ? <p className="ep-hint">{t('The talk said different things. Check with the patient or family which one stands.', 'बातचीत में अलग-अलग बातें कही गईं। मरीज़ या परिवार से जाँचें कि कौन सी बात सही है।')}</p> : null}
          <div className="ep-options">
            {options[field].map((option) => {
              const quotes = info.own.filter((s) => s.values.includes(option.value) || s.ruledOut.includes(option.value));
              const saidNo = info.ruledOut.includes(option.value);
              return <label key={option.value} className="ep-option">
                <input type="radio" name={`goc-${field}`} disabled={Boolean(lock)} checked={pick === option.value} onChange={() => setPicks((all) => ({ ...all, [field]: option.value }))} />
                <span><strong>{t(option.en, option.hi)}</strong>
                  {option.helpEn ? <small>{t(option.helpEn, option.helpHi ?? option.helpEn)}</small> : null}
                  <span className="ep-goc-tags">
                    {info.suggested.includes(option.value) ? <span className="ep-goc-tag" data-tone="said">{t('Said in the talk', 'बातचीत में कहा')}</span> : null}
                    {saidNo ? <span className="ep-goc-tag" data-tone="no">{t('Said no to this', 'इसे मना किया')}</span> : null}
                    {current === option.value ? <span className="ep-goc-tag">{t('In the plan now', 'अभी योजना में')}</span> : null}
                  </span>
                  {quotes.map((s) => <blockquote key={s.quote} dir="auto">“{s.quote}”{s.source === 'ai' ? <small> ({t('found by AI', 'AI ने ढूँढा')})</small> : null}</blockquote>)}
                </span>
              </label>;
            })}
            <label className="ep-option ep-none">
              <input type="radio" name={`goc-${field}`} disabled={Boolean(lock)} checked={pick === NO_PREFERENCE} onChange={() => setPicks((all) => ({ ...all, [field]: NO_PREFERENCE }))} />
              <span><strong>{t('No preference recorded', 'कोई पसंद दर्ज नहीं')}</strong><small>{t('Standard care will be given. This is never read as a refusal.', 'सामान्य इलाज दिया जाएगा। इसे कभी मना करना नहीं माना जाता।')}</small>
                {current === null ? <span className="ep-goc-tags"><span className="ep-goc-tag">{t('In the plan now', 'अभी योजना में')}</span></span> : null}</span>
            </label>
          </div>
          <div className="ep-actions">
            <button type="button" className="ep-btn" disabled={!pick || Boolean(lock)} onClick={() => confirmDecision(field)}>{t('Confirm this choice', 'इस फ़ैसले की पुष्टि करें')}</button>
            <button type="button" className="ep-link" onClick={() => setIgnored((all) => ({ ...all, [field]: !all[field] }))}>{ignored[field] ? t('Ignored. Undo', 'छोड़ दिया। वापस लें') : t('Ignore', 'छोड़ दें')}</button>
            {done ? <span className="ep-goc-saved" role="status">{t('Added to this draft', 'इस ड्राफ़्ट में जोड़ा')}</span> : null}
          </div>
          {done && field === 'cpr' && done.value === 'DNACPR' ? <p className="ep-hint">{t('The plan still needs the reason and who decided. Fill them in the Emergency choices step.', 'योजना में अभी कारण और फ़ैसला लेने वाले का नाम चाहिए। इन्हें इमरजेंसी के फ़ैसले वाले चरण में भरें।')}</p> : null}
          {done && (done.value === 'TRIAL' || done.value === 'UP_TO_DAYS') ? <p className="ep-hint">{t('Check the length and review date of the trial in the wizard step.', 'ट्रायल की अवधि और समीक्षा की तारीख़ उसके चरण में जाँचें।')}</p> : null}
        </details>;
      })}

      <QuoteList t={t} title={t('In the patient’s own words', 'मरीज़ के अपने शब्दों में')} hint={t('Possible words to keep. Check who said them.', 'रखने लायक़ संभावित शब्द। जाँचें कि किसने कहे।')} items={found.ownWords.map((q) => q.quote)} prefix="own" done={doneQuotes} locked={draft.surrogateDeclaration.answeringAsSurrogate && !draft.surrogateDeclaration.confirmed}
        onAdd={(quote) => { edit((r) => { applyGocQuote(r, 'ownWords', quote); }); setDoneQuotes((all) => ({ ...all, [`own:${quote}`]: 'added' })); }} onIgnore={(quote) => setDoneQuotes((all) => ({ ...all, [`own:${quote}`]: 'ignored' }))} />
      <QuoteList t={t} title={t('Outcomes the patient would not accept (one per line)', 'जो हालत मरीज़ को मंज़ूर नहीं (हर लाइन में एक)')} items={found.unacceptableOutcomes.map((q) => q.quote)} prefix="un" done={doneQuotes} locked={draft.surrogateDeclaration.answeringAsSurrogate && !draft.surrogateDeclaration.confirmed}
        onAdd={(quote) => { edit((r) => { applyGocQuote(r, 'unacceptableOutcomes', quote); }); setDoneQuotes((all) => ({ ...all, [`un:${quote}`]: 'added' })); }} onIgnore={(quote) => setDoneQuotes((all) => ({ ...all, [`un:${quote}`]: 'ignored' }))} />
      <QuoteList t={t} title={t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)')} items={found.homeCare.map((h) => h.quote)} prefix="home" done={doneQuotes} locked={false}
        onAdd={(quote) => { const hint = found.homeCare.find((h) => h.quote === quote) as GocHomeCareHint; edit((r) => { applyGocQuote(r, hint.field, quote); }); setDoneQuotes((all) => ({ ...all, [`home:${quote}`]: 'added' })); }} onIgnore={(quote) => setDoneQuotes((all) => ({ ...all, [`home:${quote}`]: 'ignored' }))} />
      {found.decisionMakers.length ? <fieldset className="ep-group"><legend>{t('Decision-makers', 'फ़ैसला लेने वाले')}</legend>
        <ul className="ep-goc-quotes">{found.decisionMakers.map((person: GocPersonHint) => <li key={person.quote}>
          <blockquote dir="auto">“{person.quote}”</blockquote>
          <div className="ep-actions">
            <button type="button" className="ep-btn ep-quiet" disabled={Boolean(draft.decisionMakers.primary)} onClick={() => edit((r) => { applyGocPerson(r, person, 'primary'); })}>{t('Primary decision-maker', 'मुख्य फ़ैसला लेने वाले')}</button>
            <button type="button" className="ep-btn ep-quiet" disabled={Boolean(draft.decisionMakers.alternate)} onClick={() => edit((r) => { applyGocPerson(r, person, 'alternate'); })}>{t('Secondary decision-maker', 'द्वितीय फ़ैसला लेने वाले')}</button>
            <button type="button" className="ep-btn ep-quiet" onClick={() => edit((r) => { applyGocPerson(r, person, 'present'); })}>{t('Present at the talk', 'बातचीत में मौजूद')}</button>
          </div>
        </li>)}</ul>
        <p className="ep-hint">{t('Add the phone number and authority in the Decision-makers step.', 'फ़ोन नंबर और अधिकार फ़ैसला लेने वाले चरण में जोड़ें।')}</p>
      </fieldset> : null}

      {ai?.available ? <fieldset className="ep-group"><legend>{t('Read again with AI (optional)', 'AI से दोबारा पढ़ें (वैकल्पिक)')}</legend>
        <label className="ep-option"><input type="checkbox" checked={aiConsent} onChange={(event) => setAiConsent(event.target.checked)} />
          <span><strong>{t(`Send this transcript to ${ai.label} to look for more choices`, `और फ़ैसले ढूँढने के लिए यह लिखावट ${ai.label} को भेजें`)}</strong><small>{t('The words leave this device. The patient or family has agreed to this. Free services may keep what is sent.', 'शब्द इस डिवाइस से बाहर जाते हैं। मरीज़ या परिवार इसके लिए राज़ी है। मुफ़्त सेवाएँ भेजी गई बातें रख सकती हैं।')}</small></span></label>
        <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={!aiConsent || aiBusy} onClick={() => void readWithAi()}>{aiBusy ? t('Reading…', 'पढ़ा जा रहा है…') : t('Read with AI', 'AI से पढ़ें')}</button></div>
        {aiMessage ? <p className="ep-hint" role="status">{aiMessage}</p> : null}
      </fieldset> : null}

      <fieldset className="ep-group"><legend>{t('Draft note', 'नोट का ड्राफ़्ट')}</legend>
        <p className="ep-hint">{t('This follows the form and states what was said and decided, without feelings. The word-for-word transcript above is kept apart, for review.', 'यह फ़ॉर्म के क्रम में है और बताता है कि क्या कहा और तय हुआ, भावनाओं के बिना। ऊपर की शब्दशः लिखावट अलग रखी जाती है, समीक्षा के लिए।')}</p>
        <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" onClick={makeNote}>{note ? t('Make the draft note again', 'नोट का ड्राफ़्ट फिर बनाएँ') : t('Make the draft note', 'नोट का ड्राफ़्ट बनाएँ')}</button></div>
        {note ? <div className="ep-field" style={{ marginTop: 12 }}>
          <label htmlFor="goc-note">{t('Note for you to edit', 'आपके बदलने के लिए नोट')} · {gocNoteKind(draft) === 'DECISION' ? t('Decisions were recorded', 'फ़ैसले दर्ज हुए') : t('General discussion, no decision recorded', 'सामान्य बातचीत, कोई फ़ैसला दर्ज नहीं')}</label>
          <textarea id="goc-note" rows={16} dir="auto" value={note} onChange={(event) => setNote(event.target.value)} />
        </div> : null}
      </fieldset>

      <div className="ep-actions">
        <button type="button" className="ep-btn" disabled={!reviewed} onClick={save}>{t('Save transcript and note to this draft', 'लिखावट और नोट इस ड्राफ़्ट में सेव करें')}</button>
        {savedAt ? <button type="button" className="ep-btn ep-quiet" onClick={onGoToSign}>{t('Go to signatures', 'हस्ताक्षर पर जाएँ')}</button> : null}
      </div>
      {savedAt ? <p className="ep-toast" role="status">{t(`Saved by ${actorName}.`, `${actorName} ने सेव किया।`)} {audioKept ? t('You kept a copy of the audio.', 'आपने ऑडियो की कॉपी रखी।') : audioDeleted ? t('The audio was deleted.', 'ऑडियो मिटा दिया गया।') : ''} {t('Tap Save draft below to keep this draft. Signing stays a separate step, and a signed version is locked.', 'यह ड्राफ़्ट रखने के लिए नीचे “ड्राफ़्ट सेव करें” दबाएँ। साइन करना अलग चरण है, और साइन किया गया संस्करण बंद रहता है।')}</p> : null}
    </> : null}
  </section>;
}

function QuoteList({ t, title, hint, items, prefix, done, locked, onAdd, onIgnore }: { t: T; title: string; hint?: string; items: string[]; prefix: string; done: Record<string, 'added' | 'ignored'>; locked: boolean; onAdd: (quote: string) => void; onIgnore: (quote: string) => void }) {
  if (!items.length) return null;
  return <fieldset className="ep-group" disabled={locked}><legend>{title}</legend>
    {hint ? <p className="ep-hint">{hint}</p> : null}
    <ul className="ep-goc-quotes">{items.map((quote) => {
      const state = done[`${prefix}:${quote}`];
      return <li key={quote} data-state={state}>
        <blockquote dir="auto">“{quote}”</blockquote>
        {state ? <span className="ep-goc-saved" role="status">{state === 'added' ? t('Added to this draft', 'इस ड्राफ़्ट में जोड़ा') : t('Ignored', 'छोड़ दिया')}</span>
          : <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" onClick={() => onAdd(quote)}>{t('Add to the plan', 'योजना में जोड़ें')}</button><button type="button" className="ep-link" onClick={() => onIgnore(quote)}>{t('Ignore', 'छोड़ दें')}</button></div>}
      </li>;
    })}</ul>
  </fieldset>;
}

/** The small button for the doctor conversation screen: fills its note with a draft from a recording it already holds. */
export function GocTranscribeButton({ file, lang, onText }: { file: Blob; lang: CareLang; onText: (text: string) => void }) {
  const t: T = (en, hi) => careText(lang, en, hi);
  const [progress, setProgress] = useState<GocProgress | 'preparing' | null>(null);
  const [message, setMessage] = useState('');
  const cancel = useRef<AbortController | null>(null);
  useEffect(() => () => cancel.current?.abort(), []);
  async function run() {
    setMessage(''); setProgress('preparing');
    const controller = new AbortController();
    cancel.current = controller;
    try {
      const samples = await decodeToSamples(file);
      await transcribeSamples(samples, { language: 'auto', signal: controller.signal, onProgress: setProgress, onText });
      setMessage(t('Machine draft added. Check it against the audio before saving.', 'मशीन का ड्राफ़्ट जुड़ गया। सेव करने से पहले ऑडियो से मिलाएँ।'));
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) setMessage(speechErrorText(t, error));
    } finally { setProgress(null); cancel.current = null; }
  }
  const status = progress === null ? '' : progress === 'preparing' ? t('Reading the audio…', 'ऑडियो पढ़ा जा रहा है…')
    : progress.stage === 'download' ? t(`Getting the speech model: ${Math.round(progress.loaded / 1_000_000)} of about ${Math.round(progress.total / 1_000_000)} MB`, `स्पीच मॉडल आ रहा है: लगभग ${Math.round(progress.total / 1_000_000)} में से ${Math.round(progress.loaded / 1_000_000)} MB`)
      : t(`Written ${progress.done} of ${progress.total} parts.`, `${progress.total} में से ${progress.done} भाग लिखे गए।`);
  return <div className="goc-doctor-transcribe">
    {progress ? <button type="button" onClick={() => cancel.current?.abort()}>{t('Stop transcribing', 'लिखना रोकें')}</button>
      : <button type="button" onClick={() => void run()}>{t('Transcribe on this device', 'इसी डिवाइस पर लिखें')}</button>}
    {status ? <span role="status">{status}</span> : null}
    {message ? <span role="status">{message}</span> : null}
  </div>;
}
