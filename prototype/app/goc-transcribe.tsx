'use client';
// Goals-of-care conversation, in as few taps as possible (owner, 3 Nov meeting): one consent tick, one big Record button, then the
// transcript and the suggested choices appear by themselves; one review screen with "Confirm all suggested"; the note in the clinical document's
// layout; doctor signs, patient or family signs, and it locks. The engine is picked automatically: Google Gemini when the server has it
// (and everyone agreed on the consent screen), else Whisper on this device (downloads once, with progress), else typing.
// Rule V1: "Confirm all" applies only choices the conversation stated, never a default, and nothing undiscussed is set.
import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { careDir, careText, type CareLang } from './care-i18n';
import { logAudit } from './ectpr-store';
import {
  applyGocDecision, applyGocPerson, applyGocQuote, extractGoc, gocCarryForward, gocFieldLock, gocLinesFromText, gocMarksFor, gocNeedsSpeakers, gocSuggestedValue,
  GOC_DECISION_FIELDS, type GocDecisionField, type GocLine, type GocSpeaker, type GocSuggestion,
} from './goc-extract';
import { buildGocNote, gocNotDiscussedTitles, gocTopicName, gocTopicRows } from './goc-note';
import { gocChecklist, gocChecklistDone, gocChecklistOpen, gocChecklistTitle } from './goc-checklist';
import { GOC_ROW_SECTIONS, GOC_ROW_TITLES, gocGuideDecode, gocGuideDiscussed, gocGuideDone, gocGuideEncode, gocGuideNext, gocGuideOutOfOrder, gocGuideTap, type GocGuideState } from './goc-guide';
import { GocRunModePicker, GocTopicPanel } from './goc-guide-panel';
import { gocNoteSuffix, gocStartNewVersion, gocTalkVersion, type GocTalk } from './goc-version';
import { buildGocFormDocument, gocFormBodyHtml, gocFormHtml, gocFormSection, GOC_FORM_CSS } from './goc-form';
import { gocFormDocx } from './goc-docx';
import { GOC_MODELS, GocSpeechError, decodeToSamples, gocDefaultModel, transcribeSamples, type GocProgress } from './goc-transcribe-model';
import type { EctprRecord, GocTopicMark, GocTranscript } from './ectpr-model';
// Loaded only when needed, so a problem in the online client can never stop the on-device path or typing.
import type { GocOnlineResult } from './goc-gemini-client';
const loadGemini = () => import('./goc-gemini-client');
import { GocSignOff } from './goc-signature-pad';
import type { GocSignOffRecord } from './goc-signoff';
import './goc-flow.css';

/** Lines from Gemini become transcript lines. Its speaker roles are the default; the doctor can change each line. */
export function gocLinesFromOnline(result: Pick<GocOnlineResult, 'lines'>): GocLine[] {
  return result.lines.flatMap((line) => {
    const speaker: GocSpeaker = line.speaker === 'DOCTOR' ? 'DOCTOR' : line.speaker === 'PATIENT_PARTY' ? 'PATIENT_PARTY' : null;
    return gocLinesFromText(line.text).map((piece) => ({ text: piece.text, speaker }));
  });
}

export { gocNeedsSpeakers, gocSuggestedValue } from './goc-extract';

/** What the saved transcript keeps about its sign-off. The full record, with the drawn images, stays in the sign-off store under the same id. */
type GocSignOffSummary = { id: string; status: GocSignOffRecord['status']; lockedAt: string | null; sealSha256: string | null; noteSha256: string; signers: Array<{ role: string; name: string; method: string; signedAt: string; relation?: string | null; registrationNo?: string | null }> };
const signOffSummary = (record: GocSignOffRecord): GocSignOffSummary => ({
  id: record.id, status: record.status, lockedAt: record.lockedAt, sealSha256: record.sealSha256, noteSha256: record.noteSha256,
  signers: record.signatures.map((sig) => ({ role: sig.role, name: sig.name, method: sig.method, signedAt: sig.signedAt, relation: sig.relation, registrationNo: sig.registrationNo })),
});

type T = (en: string, hi: string) => string;
type GocOption = { value: string; en: string; hi: string; helpEn?: string; helpHi?: string };
export type GocOptionSets = Record<GocDecisionField, GocOption[]>;
type SpokenLanguage = GocTranscript['language'];
type Stage = 'capture' | 'review' | 'note';

const LANGUAGES: Array<{ value: SpokenLanguage; en: string; hi: string }> = [
  { value: 'en', en: 'English', hi: 'अंग्रेज़ी' }, { value: 'hi', en: 'Hindi', hi: 'हिन्दी' }, { value: 'te', en: 'Telugu', hi: 'तेलुगु' },
  { value: 'ur', en: 'Urdu', hi: 'उर्दू' }, { value: 'kn', en: 'Kannada', hi: 'कन्नड़' }, { value: 'auto', en: 'Mixed', hi: 'मिली-जुली' },
];
const NO_PREFERENCE = '__none';

function clock(seconds: number) {
  const whole = Math.max(0, Math.round(seconds));
  const h = Math.floor(whole / 3600); const m = Math.floor((whole % 3600) / 60); const s = whole % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

function speechErrorText(t: T, error: unknown): string {
  const code = error instanceof GocSpeechError ? error.code : 'engine';
  switch (code) {
    case 'offline': return t('You are offline, so the speech model could not load. Type or paste the conversation.', 'आप ऑफ़लाइन हैं, इसलिए स्पीच मॉडल लोड नहीं हुआ। बातचीत टाइप या पेस्ट करें।');
    case 'network': return t('The speech model could not be downloaded. Check the internet, or type or paste the conversation.', 'स्पीच मॉडल डाउनलोड नहीं हुआ। इंटरनेट जाँचें, या बातचीत टाइप या पेस्ट करें।');
    case 'memory': return t('This device does not have enough free memory for the speech model. Close other apps, or type or paste the conversation.', 'इस डिवाइस में स्पीच मॉडल के लिए काफ़ी मेमोरी नहीं है। दूसरे ऐप बंद करें, या बातचीत टाइप या पेस्ट करें।');
    case 'unsupported': return t('This browser cannot run the speech model. Type or paste the conversation.', 'यह ब्राउज़र स्पीच मॉडल नहीं चला सकता। बातचीत टाइप या पेस्ट करें।');
    case 'too-long': return t('This recording is longer than one hour. Split it, or type or paste the conversation.', 'यह रिकॉर्डिंग एक घंटे से लंबी है। इसे बाँटें, या बातचीत टाइप या पेस्ट करें।');
    case 'decode': return t('This audio file could not be read. Try another file, or type or paste the conversation.', 'यह ऑडियो फ़ाइल पढ़ी नहीं जा सकी। दूसरी फ़ाइल आज़माएँ, या बातचीत टाइप या पेस्ट करें।');
    case 'integrity': return t('The speech tool did not match its checksum, so it was not run. Type or paste the conversation.', 'स्पीच इंजन का चेकसम मेल नहीं खाया, इसलिए उसे नहीं चलाया गया। बातचीत टाइप या पेस्ट करें।');
    default: return t('The speech model stopped. Any lines already made are kept. You can type or paste the rest.', 'स्पीच मॉडल रुक गया। जो पंक्तियाँ बन चुकीं वे रखी हैं। बाक़ी आप टाइप या पेस्ट कर सकते हैं।');
  }
}

/** A small "i" that opens the detail, in place of helper text. */
function Info({ label, children }: { label: string; children: ReactNode }) {
  return <details className="goc-info"><summary aria-label={label} title={label}>i</summary><div className="goc-info-body">{children}</div></details>;
}

export function GocTranscribe({ t, lang, draft, previous, actorName, patientId, edit, options, onClose, onGoToSign }: {
  t: T; lang: CareLang; draft: EctprRecord; previous: EctprRecord | null; actorName: string; patientId: string;
  edit: (change: (record: EctprRecord) => void) => void; options: GocOptionSets; onClose: () => void; onGoToSign: () => void;
}) {
  const saved = draft.gocTranscript;
  const savedSignOff = (saved as (GocTranscript & { signOff?: GocSignOffSummary }) | undefined)?.signOff ?? null;
  const [consentAt, setConsentAt] = useState(saved?.consentAt ?? '');
  const [people, setPeople] = useState('');
  const [stage, setStage] = useState<Stage>(saved?.note ? 'note' : saved?.lines.length ? 'review' : 'capture');
  const [language, setLanguage] = useState<SpokenLanguage>(saved?.language ?? 'auto');
  const [askLanguage, setAskLanguage] = useState(false);
  const [source, setSource] = useState<GocTranscript['source']>(saved?.source ?? 'TYPED');
  const [machineDraft, setMachineDraft] = useState(saved?.machineDraft ?? false);
  const [audio, setAudio] = useState<Blob | null>(null);
  const [audioKept, setAudioKept] = useState(saved?.audioKept ?? false);
  const [recording, setRecording] = useState(false);
  const [paused, setPaused] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [progress, setProgress] = useState<GocProgress | 'preparing' | null>(null);
  const [message, setMessage] = useState('');
  const [typing, setTyping] = useState(false);
  const [paste, setPaste] = useState('');
  const [lines, setLines] = useState<GocLine[]>(saved?.lines ?? []);
  const [aiSuggestions, setAiSuggestions] = useState<GocSuggestion[]>([]);
  const [accepted, setAccepted] = useState<GocTranscript['accepted']>(saved?.accepted ?? []);
  const [doneQuotes, setDoneQuotes] = useState<Record<string, 'added' | 'ignored'>>({});
  const [marks, setMarks] = useState<Partial<Record<string, GocTopicMark>>>(saved?.topics ?? {});
  const [note, setNote] = useState(saved?.note ?? '');
  const [gemini, setGemini] = useState(false);
  const [transcribedBy, setTranscribedBy] = useState(saved?.model ?? '');
  const [signedOff, setSignedOff] = useState<GocSignOffSummary | null>(savedSignOff);
  const [editSigned, setEditSigned] = useState(false);
  const talk = draft.gocTranscript as GocTalk | undefined;
  const talkVersion = gocTalkVersion(talk);
  const [guideMe, setGuideMe] = useState(true);
  const [guide, setGuide] = useState<GocGuideState>(() => ({ stamps: gocGuideDecode((saved as GocTalk | undefined)?.guide ?? ''), now: -1 }));
  const [guideCur, setGuideCur] = useState(0);
  const [guideWarn, setGuideWarn] = useState<string | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const cancel = useRef<AbortController | null>(null);
  const panel = useRef<HTMLElement>(null);

  useEffect(() => { panel.current?.scrollIntoView({ block: 'start', behavior: 'smooth' }); }, [stage]);
  useEffect(() => { let live = true; loadGemini().then((m) => m.gocOnlineEngines()).then((value) => { if (live) setGemini(Boolean(value.gemini)); }).catch(() => { if (live) setGemini(false); }); return () => { live = false; }; }, []);
  useEffect(() => () => { cancel.current?.abort(); stream.current?.getTracks().forEach((track) => track.stop()); }, []);
  useEffect(() => {
    if (!recording || paused) return;
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [recording, paused]);

  const consented = Boolean(consentAt);
  const running = progress !== null;
  const deferredLines = useDeferredValue(lines);
  const found = useMemo(() => extractGoc(deferredLines.filter((line) => line.text.trim())), [deferredLines]);
  const rows = useMemo(() => gocTopicRows(found.topics, marks, { consented }), [found, marks, consented]);
  const tapped = useMemo(() => Object.keys(guide.stamps).map(Number), [guide]);
  const checklist = useMemo(() => gocChecklist(rows, tapped), [rows, tapped]);
  const undiscussed = gocChecklistOpen(checklist);
  const allSuggestions = useMemo(() => [...found.suggestions, ...aiSuggestions], [found, aiSuggestions]);
  const carried = useMemo(() => (previous ? gocCarryForward(previous, draft) : []), [previous, draft]);
  const noteLocked = signedOff?.status === 'LOCKED' && !editSigned;
  const optionName = (field: GocDecisionField, value: string | null) => { const option = options[field].find((item) => item.value === value); return option ? t(option.en, option.hi) : t('No preference recorded', 'कोई पसंद दर्ज नहीं'); };

  // ---- Topic guide: the doctor's taps tick the checklist, no AI ---------------------------------------------------

  const guideSeconds = recording ? seconds : -1;
  function guideTap(index: number) {
    const started = index !== guide.now;
    const next = gocGuideTap(guide, index, guideSeconds);
    setGuide(next);
    if (started && gocGuideOutOfOrder(index, gocGuideDiscussed(next), GOC_ROW_SECTIONS, GOC_ROW_TITLES)) setGuideWarn(gocChecklistTitle(checklist[index], lang));
    else if (started) setGuideWarn(null);
  }
  function guideDone() {
    const next = gocGuideDone(guide, guideCur, guideSeconds);
    setGuide(next);
    const after = gocGuideNext(gocGuideDiscussed(next), guideCur, checklist.length);
    if (after !== null) setGuideCur(after);
  }

  /** Signed and locked: the note stays locked in history, and a new version starts from it (earlier choices stay in the plan, to be confirmed again). */
  function startNewVersion() {
    const next = gocStartNewVersion(talk);
    if (!next) return;
    edit((record) => { record.gocTranscript = next as GocTranscript; });
    setLines([]); setAccepted([]); setMarks({}); setDoneQuotes({}); setNote(''); setSignedOff(null); setEditSigned(false);
    setConsentAt(''); setAudio(null); setAudioKept(false); setMachineDraft(false); setSource('TYPED'); setTranscribedBy('');
    setGuide({ stamps: {}, now: -1 }); setGuideCur(0); setGuideWarn(null); setTyping(false); setStage('capture');
    void logAudit({ actor: actorName, action: 'CREATE', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `New goals-of-care version ${gocTalkVersion(next)} started; version ${talkVersion} stays locked in history` });
  }

  // ---- Capture --------------------------------------------------------------------------------------------------

  async function startRecording() {
    setMessage(''); setAskLanguage(false);
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
        setRecording(false); setPaused(false);
        const blob = new Blob(parts, { type: capture.mimeType || 'audio/webm' });
        setAudio(blob); setSource('RECORDED');
        void transcribe(blob);
      };
      recorder.current = capture;
      capture.start(10_000);
      setSeconds(0); setRecording(true); setPaused(false); setAudio(null); setAudioKept(false);
    } catch {
      setMessage(t('The microphone could not start. Allow the microphone, upload a recording, or type the conversation.', 'माइक्रोफ़ोन शुरू नहीं हुआ। माइक्रोफ़ोन की अनुमति दें, रिकॉर्डिंग अपलोड करें, या बातचीत टाइप करें।'));
    }
  }
  function togglePause() {
    const capture = recorder.current;
    if (!capture) return;
    if (capture.state === 'recording') { capture.pause(); setPaused(true); } else if (capture.state === 'paused') { capture.resume(); setPaused(false); }
  }

  function chooseFile(file: File | undefined) {
    if (!file) return;
    setMessage(''); setAskLanguage(false);
    if (!file.type.startsWith('audio/') && !file.type.startsWith('video/') && !/\.(m4a|mp3|wav|ogg|opus|webm|aac|flac)$/i.test(file.name)) {
      setMessage(t('Choose an audio file, such as MP3, M4A, WAV or WebM.', 'ऑडियो फ़ाइल चुनें, जैसे MP3, M4A, WAV या WebM।'));
      return;
    }
    setAudio(file); setSource('UPLOADED'); setAudioKept(false);
    void transcribe(file);
  }

  const appendText = useCallback((text: string) => { setLines((current) => [...current, ...gocLinesFromText(text)]); }, []);

  /** Picks the engine by itself. When it is done, the review opens with the choices already found. */
  async function transcribe(blob: Blob, spoken: SpokenLanguage = language) {
    if (running) return;
    setMessage(''); setProgress('preparing'); setMachineDraft(true); setAskLanguage(false);
    const controller = new AbortController();
    cancel.current = controller;
    let added = 0;
    const done = () => { setProgress(null); cancel.current = null; if (added > 0) setStage('review'); else { setAskLanguage(true); setTyping(true); } };
    if (gemini && consented) {
      try {
        const { transcribeGocWithGemini } = await loadGemini();
        const result = await transcribeGocWithGemini(blob, { lang: spoken, signal: controller.signal, onProgress: (share) => setProgress({ stage: 'transcribe', done: Math.round(share * 100), total: 100, secondsLeft: null, speed: null }) });
        const next = gocLinesFromOnline(result);
        added = next.length;
        setLines((current) => [...current, ...next]);
        setTranscribedBy('The AI service');
        void logAudit({ actor: actorName, action: 'SHARE', recordType: 'ECTPR', recordId: draft.id, patientId, detail: 'Conversation audio sent to the AI service for transcription, with consent' });
        if (added) { done(); return; }
      } catch (error) {
        if (controller.signal.aborted) { setProgress(null); cancel.current = null; return; }
        const partial = (error as { partial?: GocOnlineResult['lines'] } | null)?.partial;
        if (Array.isArray(partial) && partial.length) { const next = gocLinesFromOnline({ lines: partial }); added += next.length; setLines((current) => [...current, ...next]); }
        setProgress('preparing');
      }
    }
    try {
      const model = gocDefaultModel(spoken);
      setTranscribedBy(`Whisper on this device (${GOC_MODELS[model].modelId})`);
      const samples = await decodeToSamples(blob);
      await transcribeSamples(samples, { language: spoken, model, signal: controller.signal, onProgress: setProgress, onText: (text) => { added += 1; appendText(text); } });
      if (!added) setMessage(t('No words were heard. Choose the language, or type the conversation.', 'कोई शब्द सुनाई नहीं दिए। भाषा चुनें, या बातचीत टाइप करें।'));
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) setMessage(speechErrorText(t, error));
    } finally {
      done();
    }
  }

  function usePaste() {
    const next = gocLinesFromText(paste);
    if (!next.length) return;
    setLines((current) => [...current, ...next]);
    setPaste(''); setTyping(false); setMachineDraft(false); setSource('TYPED'); setStage('review');
  }

  // ---- Review ---------------------------------------------------------------------------------------------------

  function changeLine(index: number, patch: Partial<GocLine>) {
    setLines((current) => current.map((line, at) => (at === index ? { ...line, ...patch } : line)));
  }

  /** Writes one item to the draft. [value] null = "No preference recorded". */
  function confirmDecision(field: GocDecisionField, pick: string) {
    const value = pick === NO_PREFERENCE ? null : pick;
    const match = gocMarksFor(allSuggestions, field).current.find((s) => value !== null && s.values.includes(value));
    const at = new Date().toISOString();
    edit((record) => { applyGocDecision(record, field, value, { reasons: match?.reasons, maxAgents: match?.maxAgents }); });
    setAccepted((current) => [...current.filter((item) => item.field !== field), { field, value, quote: match?.quote ?? '', at }]);
    void logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `Conversation transcript: doctor confirmed ${field} = ${value ?? 'No preference recorded'}` });
  }

  const quoteItems = [
    // Words already shown as a choice (for example "I do not want CPR.") are not offered again as the patient's own words.
    ...found.ownWords.filter((q) => !found.suggestions.some((s) => s.quote.includes(q.quote.trim()) || q.quote.includes(s.quote.trim()))).map((q) => ({ key: `own:${q.quote}`, quote: q.quote, section: 'II' as const, title: t('2.4 In their own words', '2.4 उनके अपने शब्दों में'), apply: (r: EctprRecord) => applyGocQuote(r, 'ownWords', q.quote) })),
    ...found.unacceptableOutcomes.map((q) => ({ key: `un:${q.quote}`, quote: q.quote, section: 'II' as const, title: t('2.5 Outcomes that would be unacceptable', '2.5 जो हालत मंज़ूर नहीं'), apply: (r: EctprRecord) => applyGocQuote(r, 'unacceptableOutcomes', q.quote) })),
    ...found.homeCare.map((q) => ({ key: `home:${q.quote}`, quote: q.quote, section: 'III' as const, title: t('Home care (if relevant)', 'घर पर देखभाल (अगर लागू हो)'), apply: (r: EctprRecord) => applyGocQuote(r, q.field, q.quote) })),
  ];
  const quotesLocked = draft.surrogateDeclaration.answeringAsSurrogate && !draft.surrogateDeclaration.confirmed;
  // V4: the treatment choices open once the illness is recorded as explained. When the talk covered it, that is offered first, from the talk.
  const understandingRow = rows.find((row) => row.topic === 'understanding');
  const understandingOpen = !draft.prognosticUnderstanding.confirmed && understandingRow?.status === 'DISCUSSED';
  const understandingQuote = found.topics.find((topic) => topic.topic === 'understanding')?.quote ?? '';
  const unlocks = (lock: 'V4' | 'V5' | null) => (lock === 'V4' && understandingOpen ? null : lock);
  const decisionItems = GOC_DECISION_FIELDS.map((field) => {
    const info = gocMarksFor(allSuggestions, field);
    const lock = gocFieldLock(draft, field);
    return { field, info, lock, suggested: gocSuggestedValue(allSuggestions, field, unlocks(lock)), done: accepted.find((item) => item.field === field) };
  });
  const heard = decisionItems.filter((item) => item.info.own.length > 0);
  const pending = heard.filter((item) => !item.done && item.suggested).length + (understandingOpen ? 1 : 0) + (quotesLocked ? 0 : quoteItems.filter((q) => !doneQuotes[q.key]).length);

  function confirmUnderstanding() {
    edit((r) => {
      r.prognosticUnderstanding.confirmed = true;
      r.prognosticUnderstanding.confirmedOn = r.prognosticUnderstanding.confirmedOn || new Date().toISOString().slice(0, 10);
      r.prognosticUnderstanding.confirmedBy = r.prognosticUnderstanding.confirmedBy || actorName;
    });
  }

  /** Rule V1: only what the conversation stated, one clear option each; never a default, nothing undiscussed. */
  function confirmAll() {
    if (understandingOpen) confirmUnderstanding();
    for (const item of heard) {
      const value = item.done ? null : item.suggested;
      if (value) confirmDecision(item.field, value);
    }
    if (!quotesLocked) {
      const open = quoteItems.filter((q) => !doneQuotes[q.key]);
      if (open.length) { edit((record) => { open.forEach((q) => q.apply(record)); }); setDoneQuotes((all) => ({ ...all, ...Object.fromEntries(open.map((q) => [q.key, 'added' as const])) })); }
    }
    void logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: draft.id, patientId, detail: 'Conversation transcript: doctor confirmed all suggested choices from the conversation' });
  }

  function markTopic(topic: string, status: GocTopicMark['status'] | null) {
    setMarks((current) => {
      const next = { ...current };
      if (status) next[topic] = { status, verifiedBy: actorName, verifiedAt: new Date().toISOString() };
      else delete next[topic];
      return next;
    });
  }

  // ---- Note and sign-off ----------------------------------------------------------------------------------------

  const formDocument = (withSignatures: boolean) => buildGocFormDocument(draft, rows, lang, {
    reviewedBy: actorName, reviewedAt: saved?.reviewedAt || new Date().toISOString(), notDiscussed: gocNotDiscussedTitles(rows, lang, tapped),
    deviceSignatures: withSignatures ? signedOff?.signers : undefined,
  });

  function downloadDocx() {
    const bytes = gocFormDocx(formDocument(true), careDir(lang) === 'rtl');
    const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }));
    const link = document.createElement('a');
    link.href = url; link.download = `sahara-goals-of-care-${new Date().toISOString().slice(0, 10)}.docx`;
    document.body.appendChild(link); link.click(); link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
    void logAudit({ actor: actorName, action: 'PRINT', recordType: 'ECTPR', recordId: draft.id, patientId, detail: 'Goals-of-care note downloaded as a Word file (clinical document layout)' });
  }
  function printForm() {
    const page = window.open('', '_blank');
    if (!page) return;
    page.document.open(); page.document.write(gocFormHtml(formDocument(true), careDir(lang))); page.document.close();
    page.focus(); window.setTimeout(() => page.print(), 300);
    void logAudit({ actor: actorName, action: 'PRINT', recordType: 'ECTPR', recordId: draft.id, patientId, detail: 'Goals-of-care note printed (clinical document layout)' });
  }

  /** "You haven't spoken about these things" is shown on the review screen itself; Continue (anyway) makes the note and saves. */
  function continueToNote() {
    const at = new Date().toISOString();
    const text = buildGocNote(draft, rows, lang, { reviewedBy: actorName, reviewedAt: at, tapped });
    const transcript: GocTalk = {
      lines: lines.filter((line) => line.text.trim()), language, source, machineDraft, ...(machineDraft ? { model: transcribedBy } : {}),
      consentAt, reviewedBy: actorName, reviewedAt: at, audioKept, accepted, topics: marks, note: text,
      guide: gocGuideEncode(guide.stamps), version: talkVersion, ...(talk?.history ? { history: talk.history } : {}), ...(talk?.carriedNote ? { carriedNote: talk.carriedNote } : {}),
    };
    edit((record) => { record.gocTranscript = transcript; });
    if (!audioKept) setAudio(null);
    setNote(text); setEditSigned(false); setStage('note');
    void logAudit({
      actor: actorName, action: 'REVIEW', recordType: 'ECTPR', recordId: draft.id, patientId,
      detail: `Conversation transcript reviewed and saved: ${transcript.lines.length} lines, ${undiscussed.length ? `not discussed: ${undiscussed.map((row) => row.id).join(', ')}; ` : ''}${accepted.length} choices confirmed, audio ${audioKept ? 'kept by the doctor' : audio ? 'deleted' : 'none'}`,
    });
  }

  function agree(on: boolean) {
    const at = on ? new Date().toISOString() : '';
    setConsentAt(at);
    const names = people.split(/[,\n]/).map((name) => name.trim()).filter(Boolean);
    if (on && names.length) edit((record) => { names.forEach((name) => applyGocPerson(record, { relation: '', name, quote: name }, 'present')); });
  }

  const progressPercent = progress && progress !== 'preparing'
    ? progress.stage === 'download' ? Math.min(100, (progress.loaded / progress.total) * 100) : (progress.done / Math.max(1, progress.total)) * 100 : 0;
  const progressText = progress === null ? '' : progress === 'preparing' ? t('Reading the audio…', 'ऑडियो पढ़ा जा रहा है…')
    : progress.stage === 'download' ? t(`Getting the speech model, first time only: ${Math.round(progress.loaded / 1_000_000)} of ${Math.round(progress.total / 1_000_000)} MB`, `स्पीच मॉडल आ रहा है, सिर्फ़ पहली बार: ${Math.round(progress.total / 1_000_000)} में से ${Math.round(progress.loaded / 1_000_000)} MB`)
      : progress.secondsLeft === null ? t('Writing the conversation…', 'बातचीत लिखी जा रही है…')
        : t(`Writing the conversation · about ${clock(progress.secondsLeft)} left`, `बातचीत लिखी जा रही है · लगभग ${clock(progress.secondsLeft)} बाक़ी`);

  const steps: Array<[Stage, string]> = [['capture', t('Consent and record', 'सहमति और रिकॉर्ड')], ['review', t('Review', 'जाँच')], ['note', t('Note and sign', 'नोट और हस्ताक्षर')]];
  const stageIndex = steps.findIndex(([id]) => id === stage);

  return <section className="ep-card ep-goc goc-flow" ref={panel} aria-label={t('Goals-of-care conversation', 'देखभाल के लक्ष्यों की बातचीत')}>
    <div className="ep-goc-head">
      <h2>{t('Goals-of-care conversation', 'देखभाल के लक्ष्यों की बातचीत')}</h2>
      <Info label={t('About this', 'इसके बारे में')}><p>{t('Sahara writes down what was said and finds the choices that were stated. It does not recommend any treatment. Nothing goes into the plan until you confirm it, and a topic not discussed stays No preference recorded.', 'Sahara लिखता है कि क्या कहा गया और जो फ़ैसले बताए गए उन्हें ढूँढता है। यह कोई इलाज नहीं सुझाता। आपकी पुष्टि के बिना कुछ भी योजना में नहीं जाता, और जिस विषय पर बात नहीं हुई वह “कोई पसंद दर्ज नहीं” रहता है।')}</p></Info>
      <button type="button" className="ep-btn ep-quiet" onClick={onClose}>{t('Close', 'बंद करें')}</button>
    </div>
    <ol className="goc-stepper" aria-label={t('Steps', 'चरण')}>{steps.map(([id, name], index) => <li key={id} data-state={index < stageIndex ? 'done' : index === stageIndex ? 'now' : undefined} aria-current={index === stageIndex ? 'step' : undefined}>
      <span className="goc-step-num" aria-hidden="true">{index < stageIndex ? '✓' : index + 1}</span><span className="goc-step-name">{name}</span>
    </li>)}</ol>

    {stage === 'capture' ? <div className="goc-stage goc-capture">
      {previous && !lines.length ? <details className="goc-carry">
        <summary>{t(`Since version ${previous.version}`, `संस्करण ${previous.version} के बाद से`)}</summary>
        <ul className="ep-goc-carry">{carried.filter((row) => row.previous !== null || row.current !== null).map((row) => <li key={row.field} data-changed={row.changed || undefined}><strong>{gocTopicName(row.field, lang)}</strong>
          <span>{row.changed ? t(`Changed: ${optionName(row.field, row.previous)} to ${optionName(row.field, row.current)}`, `बदला: ${optionName(row.field, row.previous)} से ${optionName(row.field, row.current)}`) : optionName(row.field, row.current)}</span></li>)}</ul>
      </details> : null}
      <label className="goc-consent" data-on={consented || undefined}>
        <input type="checkbox" checked={consented} onChange={(event) => agree(event.target.checked)} />
        <span><strong>{t('Everyone present agrees to this conversation being recorded and written down', 'मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं')}</strong>
          {gemini ? <small>{t('Audio is sent to a secure AI service to transcribe.', 'ऑडियो लिखने के लिए एक सुरक्षित AI सेवा को भेजा जाता है।')}</small> : null}</span>
      </label>
      {talkVersion > 1 || talk?.history?.length ? <details className="goc-carry">
        <summary>{t(`Version ${talkVersion} of this conversation`, `इस बातचीत का संस्करण ${talkVersion}`)}</summary>
        <ul className="goc-history">{(talk?.history ?? []).map((item) => <li key={item.version}>{t(`Version ${item.version}: signed and locked`, `संस्करण ${item.version}: साइन होकर बंद`)}</li>)}</ul>
      </details> : null}
      <GocRunModePicker t={t} guideMe={guideMe} onChange={setGuideMe} />
      <GocTopicPanel t={t} lang={lang} guideMe={guideMe} state={guide} cur={guideCur} warnTitle={guideWarn} onTap={guideTap} onDone={guideDone}
        onAllTopics={() => setGuideMe(false)} onDismissWarning={() => setGuideWarn(null)} />
      {!recording && !running ? <div className="goc-names">
        <label htmlFor="goc-people">{t('Who is present (optional)', 'कौन मौजूद है (वैकल्पिक)')}</label>
        <input id="goc-people" value={people} placeholder={t('e.g. Lakshmi (patient), Priya (daughter)', 'जैसे लक्ष्मी (मरीज़), प्रिया (बेटी)')} onChange={(event) => setPeople(event.target.value)} disabled={consented} />
      </div> : null}

      {!running ? <>
        {recording ? <div className="goc-recording" role="status"><span className="goc-dot" data-paused={paused || undefined} aria-hidden="true" />{paused ? t('Paused', 'रुका हुआ') : t('Recording', 'रिकॉर्ड हो रहा है')} · {clock(seconds)}</div> : null}
        <div className="goc-record-row" data-recording={recording ? (paused ? 'paused' : 'live') : undefined}>
          {recording ? <>
            <button type="button" className="goc-record goc-stop" onClick={() => recorder.current?.stop()}><span aria-hidden="true">■</span>{t('Stop', 'रोकें')}</button>
            <button type="button" className="goc-pause" onClick={togglePause}>{paused ? t('Resume', 'फिर शुरू') : t('Pause', 'रोकें (थोड़ी देर)')}</button>
          </> : <button type="button" className="goc-record" disabled={!consented} onClick={() => void startRecording()}><span aria-hidden="true">●</span>{t('Record', 'रिकॉर्ड करें')}</button>}
        </div>
        {!recording ? <div className="goc-links">
          <label className="goc-link goc-file">{t('Upload a file', 'फ़ाइल अपलोड करें')}
            <input type="file" accept="audio/*,video/webm,.m4a,.mp3,.wav,.ogg,.opus,.webm" onChange={(event) => { chooseFile(event.target.files?.[0]); event.target.value = ''; }} />
          </label>
          <span aria-hidden="true">·</span>
          <button type="button" className="goc-link" onClick={() => setTyping((on) => !on)} aria-expanded={typing}>{t('Type or paste', 'टाइप या पेस्ट करें')}</button>
        </div> : null}
      </> : <div className="goc-working" role="status">
        <div className="goc-working-head"><span className="goc-spinner" aria-hidden="true" /><p>{progressText}</p>{progress !== 'preparing' && progressPercent > 0 ? <strong className="goc-percent">{Math.round(progressPercent)}%</strong> : null}</div>
        <div className="ep-progress-track" role="progressbar" aria-label={t('Progress', 'प्रगति')} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progressPercent)} data-indeterminate={progress === 'preparing' || progressPercent === 0 || undefined}><div className="ep-progress-fill" style={{ width: `${progressPercent}%` }} /></div>
        {transcribedBy ? <small className="goc-engine">{transcribedBy}</small> : null}
        <button type="button" className="goc-link" onClick={() => cancel.current?.abort()}>{t('Stop', 'रोकें')}</button>
      </div>}
      {message ? <p className="ep-notice ep-alert" role="alert">{message}</p> : null}
      {askLanguage && audio ? <div className="goc-language" role="group" aria-label={t('Language', 'भाषा')}>
        <span className="goc-chip-label">{t('Language', 'भाषा')}</span>
        {LANGUAGES.map((item) => <button key={item.value} type="button" className="goc-chip" aria-pressed={language === item.value} onClick={() => { setLanguage(item.value); void transcribe(audio, item.value); }}>{t(item.en, item.hi)}</button>)}
      </div> : null}
      {typing ? <div className="goc-type">
        <textarea id="goc-paste" rows={6} dir="auto" value={paste} aria-label={t('Type or paste the conversation', 'बातचीत टाइप या पेस्ट करें')} placeholder={t('Doctor: …\nPatient: …', 'डॉक्टर: …\nमरीज़: …')} onChange={(event) => setPaste(event.target.value)} />
        <button type="button" className="ep-btn" disabled={!paste.trim()} onClick={usePaste}>{t('Use this text', 'यह लिखावट इस्तेमाल करें')}</button>
      </div> : null}
    </div> : null}

    {stage === 'review' ? <div className="goc-stage goc-review">
      <div className="goc-checklist" aria-label={t('Checklist', 'सूची')}>
        <h3>{t('Checklist', 'सूची')} <span className="goc-count">{t(`${gocChecklistDone(checklist)} of ${checklist.length}`, `${checklist.length} में से ${gocChecklistDone(checklist)}`)}</span></h3>
        <ol>{checklist.map((row) => <li key={row.id} data-status={row.discussed ? 'DISCUSSED' : 'NOT_DISCUSSED'} data-verified={row.verified || undefined}>
          <span className="goc-tick" aria-hidden="true">{row.discussed ? '✓' : ''}</span>
          <span><span className="goc-sec">{row.section}</span>{gocChecklistTitle(row, lang)}</span>
        </li>)}</ol>
      </div>

      {gocNeedsSpeakers(lines) ? <p className="goc-who" role="status"><strong>{t('Who’s the doctor?', 'डॉक्टर कौन हैं?')}</strong> {t('Tap the doctor’s lines below.', 'नीचे डॉक्टर की पंक्तियों पर टैप करें।')}</p> : null}

      <div className="goc-suggest-head">
        <h3>{t('Suggested from the conversation', 'बातचीत से सुझाव')}</h3>
        <Info label={t('How suggestions work', 'सुझाव कैसे बनते हैं')}><p>{t('Each suggestion is the words that were said, not advice. When the same person changed their mind, the latest statement wins and the earlier one is struck through. When different people disagree, both are shown and nothing is suggested. Confirm all applies only what was said.', 'हर सुझाव कहे गए शब्द हैं, सलाह नहीं। अगर एक ही व्यक्ति ने राय बदली, तो आख़िरी बात मानी जाती है और पहले वाली कटी दिखती है। अलग-अलग लोग असहमत हों तो दोनों दिखती हैं और कुछ नहीं सुझाया जाता। “सब पक्का करें” सिर्फ़ कही गई बातें लगाता है।')}</p></Info>
      </div>
      <button type="button" className="ep-btn goc-confirm-all" disabled={pending === 0} onClick={confirmAll}>{pending ? t(`Confirm all suggested (${pending})`, `सभी सुझाव पक्के करें (${pending})`) : t('All suggestions confirmed', 'सभी सुझाव पक्के')}</button>
      {(['I', 'II', 'III'] as const).map((section) => {
        const items = heard.filter((item) => gocFormSection(item.field) === section);
        const quotes = quoteItems.filter((q) => q.section === section);
        const persons = section === 'I' ? found.decisionMakers : [];
        const understandingHere = section === 'II' && understandingOpen;
        if (!items.length && !quotes.length && !persons.length && !understandingHere) return null;
        return <div key={section} className="goc-group">
          <h4><span className="goc-sec">{section}</span>{section === 'I' ? t('General information', 'सामान्य जानकारी') : section === 'II' ? t('Medical information and patient values', 'चिकित्सा जानकारी और मरीज़ के मूल्य') : t('Decisive actions', 'निर्णायक फ़ैसले')}</h4>
          {understandingHere ? <div className="goc-item">
            <div className="goc-item-head"><strong>{t('Illness explained as not curable, and the family can say it back', 'समझाया गया कि बीमारी ठीक नहीं हो सकती, और परिवार दोहरा सकता है')}</strong></div>
            {understandingQuote ? <blockquote dir="auto">“{understandingQuote}”</blockquote> : null}
            <button type="button" className="goc-link" onClick={confirmUnderstanding}>{t('Confirm', 'पक्का करें')}</button>
          </div> : null}
          {items.map(({ field, info, lock, suggested, done }) => <div key={field} className="goc-item" data-done={done ? true : undefined}>
            <div className="goc-item-head">
              <strong>{gocTopicName(field, lang)}</strong>
              {info.conflict ? <span className="goc-tag" data-tone="conflict">{t('People disagree', 'लोगों की राय अलग है')}</span> : info.changedMind ? <span className="goc-tag">{t('Changed during the talk', 'बातचीत में बदला')}</span> : null}
            </div>
            {done ? <p className="goc-answer">✓ {optionName(field, done.value)}</p> : suggested ? <p className="goc-answer goc-suggested">{optionName(field, suggested)}</p> : null}
            {info.current.map((s) => <blockquote key={`${s.at}:${s.quote}`} dir="auto">{s.who ? <small>{s.who}: </small> : null}“{s.quote}”</blockquote>)}
            {info.superseded.map(({ earlier }) => <blockquote key={`old:${earlier.at}`} dir="auto" className="goc-old">{earlier.who ? <small>{earlier.who}: </small> : null}<s>“{earlier.quote}”</s></blockquote>)}
            {lock ? <p className="goc-lock">{lock === 'V4' ? understandingOpen ? t('Opens when the understanding above is confirmed.', 'ऊपर की समझ पक्की होने पर खुलेगा।') : t('Locked until the illness is recorded as explained (Understanding step).', 'बीमारी समझाने की बात दर्ज होने तक बंद (समझ वाला चरण)।') : t('Locked until the surrogate’s promise is confirmed.', 'प्रतिनिधि का वादा पक्का होने तक बंद।')}</p>
              : <label className="goc-change">{done ? t('Change', 'बदलें') : suggested ? t('Change', 'बदलें') : t('Choose', 'चुनें')}
                <select value={done ? done.value ?? NO_PREFERENCE : ''} onChange={(event) => { if (event.target.value) confirmDecision(field, event.target.value); }}>
                  <option value="" disabled>{t('Choose…', 'चुनें…')}</option>
                  {options[field].map((option) => <option key={option.value} value={option.value}>{t(option.en, option.hi)}{info.suggested.includes(option.value) ? ` · ${t('said', 'कहा गया')}` : ''}</option>)}
                  <option value={NO_PREFERENCE}>{t('No preference recorded', 'कोई पसंद दर्ज नहीं')}</option>
                </select></label>}
          </div>)}
          {quotes.map((q) => <div key={q.key} className="goc-item" data-done={doneQuotes[q.key] === 'added' || undefined}>
            <div className="goc-item-head"><strong>{q.title}</strong></div>
            <blockquote dir="auto">“{q.quote}”</blockquote>
            {doneQuotes[q.key] ? <p className="goc-answer">{doneQuotes[q.key] === 'added' ? `✓ ${t('Added', 'जोड़ा')}` : t('Left out', 'छोड़ा')}</p>
              : <button type="button" className="goc-link" disabled={quotesLocked} onClick={() => setDoneQuotes((all) => ({ ...all, [q.key]: 'ignored' }))}>{t('Leave out', 'छोड़ दें')}</button>}
          </div>)}
          {persons.map((person) => <div key={person.quote} className="goc-item">
            <div className="goc-item-head"><strong>{t('Decision maker', 'फ़ैसला लेने वाले')}</strong></div>
            <blockquote dir="auto">“{person.quote}”</blockquote>
            <div className="goc-people-actions">
              <button type="button" className="goc-chip" disabled={Boolean(draft.decisionMakers.primary)} onClick={() => edit((r) => { applyGocPerson(r, person, 'primary'); })}>{t('Primary', 'मुख्य')}</button>
              <button type="button" className="goc-chip" disabled={Boolean(draft.decisionMakers.alternate)} onClick={() => edit((r) => { applyGocPerson(r, person, 'alternate'); })}>{t('Secondary', 'द्वितीय')}</button>
            </div>
          </div>)}
        </div>;
      })}
      {!heard.length && !quoteItems.length ? <p className="goc-empty">{t('No choices were stated in this conversation.', 'इस बातचीत में कोई फ़ैसला नहीं बताया गया।')}</p> : null}

      <details className="goc-transcript">
        <summary>{t(`Transcript · ${lines.length} lines`, `लिखावट · ${lines.length} पंक्तियाँ`)}{transcribedBy && machineDraft ? <small> · {transcribedBy}</small> : null}</summary>
        <ol className="ep-goc-lines">{lines.map((line, index) => <li key={index} data-doctor={line.speaker === 'DOCTOR' || undefined}>
          <button type="button" className="goc-side" aria-pressed={line.speaker === 'DOCTOR'} onClick={() => changeLine(index, { speaker: line.speaker === 'DOCTOR' ? 'PATIENT_PARTY' : 'DOCTOR' })}>
            {line.speaker === 'DOCTOR' ? t('Doctor', 'डॉक्टर') : line.speaker === 'PATIENT_PARTY' ? t('Patient side', 'मरीज़ की तरफ़') : t('Who?', 'कौन?')}</button>
          <textarea aria-label={t(`Line ${index + 1}`, `लाइन ${index + 1}`)} rows={2} dir="auto" value={line.text} onChange={(event) => changeLine(index, { text: event.target.value })} />
        </li>)}</ol>
        <button type="button" className="goc-link" onClick={() => setStage('capture')}>{t('Add more', 'और जोड़ें')}</button>
      </details>

      {undiscussed.length ? <div className="goc-undiscussed" role="status">
        <h3>{t('You haven’t spoken about these things', 'इन बातों पर बात नहीं हुई')}</h3>
        <ul>{undiscussed.map((row) => <li key={row.id}><span>{gocChecklistTitle(row, lang)}</span>
          <button type="button" className="goc-link" onClick={() => setGuide((state) => gocGuideDone(state, row.index, -1))}>{t('It was discussed', 'इस पर बात हुई')}</button></li>)}</ul>
        <p className="goc-muted">{t('They stay No preference recorded, and the note says Not discussed.', 'ये “कोई पसंद दर्ज नहीं” रहेंगे, और नोट में “बात नहीं हुई” लिखा जाएगा।')}</p>
      </div> : null}
      <button type="button" className="ep-btn goc-next" disabled={!lines.some((line) => line.text.trim())} onClick={continueToNote}>{undiscussed.length ? t('Continue anyway', 'फिर भी आगे बढ़ें') : t('Continue', 'आगे बढ़ें')}</button>
    </div> : null}

    {stage === 'note' ? <div className="goc-stage goc-note-stage">
      <div className="goc-note-tools">
        <button type="button" className="goc-link" onClick={() => setStage('review')} disabled={noteLocked}>{t('Back to review', 'जाँच पर वापस')}</button>
        <span className="goc-spacer" />
        <button type="button" className="ep-btn ep-quiet" onClick={downloadDocx}>{t('Word', 'Word')}</button>
        <button type="button" className="ep-btn ep-quiet" onClick={printForm}>{t('Print', 'प्रिंट')}</button>
      </div>
      <style>{GOC_FORM_CSS}</style>
      {/* The form's own markup, escaped in goc-form.ts, so the screen matches print and Word exactly. */}
      <div className="goc-paper" data-locked={noteLocked || undefined}>
        {noteLocked ? <span className="goc-stamp" aria-hidden="true">{t('Locked', 'लॉक')}</span> : null}
        <div className="goc-form" dir={careDir(lang)} dangerouslySetInnerHTML={{ __html: gocFormBodyHtml(formDocument(true)) }} />
      </div>
      {noteLocked ? <p className="ep-notice ep-info" role="status">{t('Signed and locked.', 'साइन होकर बंद।')} <button type="button" className="ep-link" onClick={() => setEditSigned(true)}>{t('Change the note', 'नोट बदलें')}</button></p> : null}
      {editSigned ? <p className="ep-notice ep-warn">{t('Changing the note clears the signatures, and everyone signs again.', 'नोट बदलने पर हस्ताक्षर हट जाते हैं, और सबको फिर साइन करना होगा।')} <button type="button" className="ep-link" onClick={() => setStage('review')}>{t('Back to review', 'जाँच पर वापस')}</button></p> : null}
      {note.trim() ? <GocSignOff t={t} noteId={`${draft.id}:v${draft.version}:goc${gocNoteSuffix(talkVersion)}`} noteText={note} doctorName={actorName} signerName={draft.patient.name} showNote={false}
        onComplete={(record) => {
          const summary = signOffSummary(record);
          setSignedOff(summary); setEditSigned(false);
          edit((r) => { if (r.gocTranscript) Object.assign(r.gocTranscript, { signOff: summary, note }); });
          void logAudit({ actor: actorName, action: 'SIGN', recordType: 'ECTPR', recordId: draft.id, patientId, detail: `Goals-of-care note signed on this device and locked: ${summary.signers.map((s) => `${s.role} ${s.method}`).join(', ')}` });
        }}
        onInvalidated={() => {
          setSignedOff(null);
          edit((r) => { if (r.gocTranscript) Object.assign(r.gocTranscript, { signOff: undefined }); });
        }} /> : null}
      {signedOff?.status === 'LOCKED' ? <div className="goc-done">
        <p role="status">✓ {t(`Signed and locked: ${signedOff.signers.map((s) => s.name).join(', ')}`, `हस्ताक्षर हुए और बंद: ${signedOff.signers.map((s) => s.name).join(', ')}`)}</p>
        <button type="button" className="ep-btn" onClick={onClose}>{t('Done', 'हो गया')}</button>
        <button type="button" className="goc-link" onClick={onGoToSign}>{t('Plan signatures', 'योजना के हस्ताक्षर')}</button>
        <div className="goc-new-version">
          <button type="button" className="ep-btn ep-quiet" data-testid="goc-new-version" onClick={startNewVersion}>{t('Start a new version', 'नया संस्करण शुरू करें')}</button>
          <small className="goc-muted">{t('This signed note stays locked. The new version starts from it, and the choices are confirmed again in the new talk.', 'यह साइन किया नोट बंद रहेगा। नया संस्करण इसी से शुरू होगा, और नई बातचीत में फ़ैसले फिर पक्के किए जाएँगे।')}</small>
        </div>
      </div> : null}
    </div> : null}
  </section>;
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

