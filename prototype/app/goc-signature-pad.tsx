'use client';

// Drawn-signature sign-off for the goals-of-care note. Reusable pieces, wired in by the GOC screens:
//   <GocSignaturePad>  a finger, stylus or thumb pad that outputs a small 1-bit PNG data URL, stroke count and time.
//   <GocSignOff>       doctor, then patient or family member, then a witness when needed, then the note version is locked.
// Rules, hashes and the JSON field names (shared with Android CareGocSignOff.kt) are documented in goc-signoff.ts.
// Stored on this device only, through goc-signoff-store.ts (IndexedDB, localStorage fallback), like the care plan.

import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import {
  GOC_DEFAULT_STEPS, GocSignOffError, gocAddSignature, gocBytesToDataUrl, gocEncodeMonoPng, gocExportSize, gocLock, gocNextStep,
  gocReconcile, gocRestart, gocStartSignOff,
  type GocSignOffErrorCode, type GocSignOffRecord, type GocSignatureMethod, type GocSignatureRole, type GocSignerStep,
} from './goc-signoff.ts';
import { loadGocSignOffs, saveGocSignOff } from './goc-signoff-store.ts';
import './goc-signature-pad.css';

type T = (en: string, hi: string) => string;

export type GocSignatureResult = {
  dataUrl: string; strokeCount: number; capturedAt: string; method: GocSignatureMethod; width: number; height: number;
};

type Point = [number, number];
/** Ink width as a share of the pad height. The same numbers are used on Android. */
export const GOC_INK_DRAWN = 0.02;
export const GOC_INK_THUMB = 0.22;
const MIN_DRAWN_LENGTH = 0.2; // total stroke length in pad widths, so a stray tap is not a signature

function inkLength(strokes: Point[][]): number {
  let sum = 0;
  for (const s of strokes) for (let i = 1; i < s.length; i++) sum += Math.hypot(s[i][0] - s[i - 1][0], (s[i][1] - s[i - 1][1]) / 3);
  return sum;
}

function paint(ctx: CanvasRenderingContext2D, strokes: Point[][], width: number, height: number, thumb: boolean, ink: string) {
  ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = ink; ctx.fillStyle = ink;
  ctx.lineWidth = Math.max(1.5, height * (thumb ? GOC_INK_THUMB : GOC_INK_DRAWN));
  for (const stroke of strokes) {
    if (!stroke.length) continue;
    if (stroke.length === 1) { ctx.beginPath(); ctx.arc(stroke[0][0] * width, stroke[0][1] * height, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill(); continue; }
    ctx.beginPath();
    stroke.forEach(([x, y], i) => i ? ctx.lineTo(x * width, y * height) : ctx.moveTo(x * width, y * height));
    ctx.stroke();
  }
}

/** Renders the strokes black on white at most 600 px wide, keeps only black and white, and encodes a 1-bit PNG. */
export async function gocRenderSignature(strokes: Point[][], padWidth: number, padHeight: number, thumb: boolean): Promise<{ dataUrl: string; width: number; height: number }> {
  const { width, height } = gocExportSize(padWidth, padHeight);
  const canvas = document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('No canvas.');
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, width, height);
  paint(ctx, strokes, width, height, thumb, '#000');
  const pixels = ctx.getImageData(0, 0, width, height).data;
  const black = new Uint8Array(width * height);
  for (let i = 0; i < black.length; i++) black[i] = (pixels[i * 4] * 0.299 + pixels[i * 4 + 1] * 0.587 + pixels[i * 4 + 2] * 0.114) < 160 ? 1 : 0;
  if (typeof CompressionStream !== 'undefined') return { dataUrl: gocBytesToDataUrl(await gocEncodeMonoPng(width, height, black)), width, height };
  // Older browsers: same black and white pixels through the canvas encoder (8-bit, still small).
  const image = ctx.createImageData(width, height);
  for (let i = 0; i < black.length; i++) { const v = black[i] ? 0 : 255; image.data.set([v, v, v, 255], i * 4); }
  ctx.putImageData(image, 0, 0);
  return { dataUrl: canvas.toDataURL('image/png'), width, height };
}

export function GocSignaturePad({ t, onDone, disabled = false, allowThumb = true, label, doneLabel }: {
  t: T;
  /** Return true (or resolve true) to clear the pad, false to keep the drawing (e.g. a form field is missing). */
  onDone: (result: GocSignatureResult) => boolean | void | Promise<boolean | void>;
  disabled?: boolean; allowThumb?: boolean; label?: string; doneLabel?: string;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const strokes = useRef<Point[][]>([]);
  const drawing = useRef<number | null>(null);
  const [count, setCount] = useState(0);
  const [ink, setInk] = useState(0);
  const [thumb, setThumb] = useState(false);
  const [busy, setBusy] = useState(false);

  const redraw = useCallback(() => {
    const el = canvas.current; const ctx = el?.getContext('2d');
    if (!el || !ctx) return;
    const ratio = window.devicePixelRatio || 1;
    const w = el.clientWidth, h = el.clientHeight;
    if (el.width !== Math.round(w * ratio) || el.height !== Math.round(h * ratio)) { el.width = Math.round(w * ratio); el.height = Math.round(h * ratio); }
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, w, h);
    paint(ctx, strokes.current, w, h, thumb, '#241c18');
  }, [thumb]);

  useEffect(() => {
    redraw();
    const el = canvas.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(redraw);
    observer.observe(el);
    return () => observer.disconnect();
  }, [redraw]);

  const clear = () => { strokes.current = []; setCount(0); setInk(0); redraw(); };
  const point = (event: { clientX: number; clientY: number }): Point => {
    const box = canvas.current!.getBoundingClientRect();
    return [Math.min(1, Math.max(0, (event.clientX - box.left) / box.width)), Math.min(1, Math.max(0, (event.clientY - box.top) / box.height))];
  };
  const down = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (disabled || busy || (drawing.current !== null && drawing.current !== event.pointerId)) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    drawing.current = event.pointerId;
    strokes.current = [...strokes.current, [point(event)]];
    setCount(strokes.current.length);
    redraw();
  };
  const move = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (drawing.current !== event.pointerId) return;
    event.preventDefault();
    const native = event.nativeEvent as PointerEvent;
    const events = typeof native.getCoalescedEvents === 'function' ? native.getCoalescedEvents() : [];
    const last = strokes.current[strokes.current.length - 1];
    for (const e of events.length ? events : [native]) last.push(point(e));
    redraw();
  };
  const up = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (drawing.current !== event.pointerId) return;
    drawing.current = null;
    setInk(inkLength(strokes.current));
  };
  const ready = count > 0 && (thumb || ink >= MIN_DRAWN_LENGTH);

  const done = async () => {
    const el = canvas.current;
    if (!el || !ready) return;
    setBusy(true);
    try {
      const image = await gocRenderSignature(strokes.current, el.clientWidth, el.clientHeight, thumb);
      const keep = await onDone({ ...image, strokeCount: strokes.current.length, capturedAt: new Date().toISOString(), method: thumb ? 'THUMB' : 'DRAWN' });
      if (keep !== false) clear();
    } finally { setBusy(false); }
  };

  return <div className="goc-pad" data-thumb={thumb || undefined}>
    <div className="goc-pad-head">
      <strong>{label ?? (thumb ? t('Thumb impression here', 'यहाँ अंगूठा लगाएँ') : t('Sign here', 'यहाँ हस्ताक्षर करें'))}</strong>
      {allowThumb ? <label className="goc-pad-toggle">
        <input type="checkbox" role="switch" checked={thumb} disabled={disabled || busy} onChange={(event) => { setThumb(event.target.checked); clear(); }} />
        <span>{t('Thumb impression', 'अंगूठे का निशान')}</span>
      </label> : null}
    </div>
    <canvas ref={canvas} className="goc-pad-canvas" aria-label={thumb ? t('Thumb impression pad', 'अंगूठे के निशान की जगह') : t('Signature pad', 'हस्ताक्षर की जगह')}
      onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up} onLostPointerCapture={up} data-disabled={disabled || undefined} />
    <div className="ep-actions goc-pad-actions">
      <button type="button" className="ep-btn ep-quiet" disabled={disabled || busy || count === 0} onClick={clear}>{t('Clear', 'मिटाएँ')}</button>
      <button type="button" className="ep-btn" disabled={disabled || busy || !ready} onClick={() => void done()}>{doneLabel ?? t('Done', 'हो गया')}</button>
    </div>
  </div>;
}

// ---------------------------------------------------------------------------

function errorText(t: T, code: GocSignOffErrorCode): string {
  switch (code) {
    case 'NOTE_CHANGED': return t('The note has changed. Read the new note together before signing.', 'नोट बदल गया है। साइन करने से पहले नया नोट साथ में पढ़ें।');
    case 'LOCKED': return t('This note version is locked. No more signatures can be added.', 'नोट का यह संस्करण लॉक है। अब और हस्ताक्षर नहीं जुड़ सकते।');
    case 'INVALIDATED': return t('These signatures are no longer valid. Sign again.', 'ये हस्ताक्षर अब मान्य नहीं हैं। फिर से साइन करें।');
    case 'WRONG_ORDER': return t('Sign in order: doctor, then patient or family, then witness.', 'क्रम से साइन करें: डॉक्टर, फिर मरीज़ या परिवार, फिर गवाह।');
    case 'NAME_BLANK': return t('Write the name of the person signing.', 'साइन करने वाले का नाम लिखें।');
    case 'REGISTRATION_BLANK': return t('Write the doctor’s registration number.', 'डॉक्टर का रजिस्ट्रेशन नंबर लिखें।');
    case 'RELATION_BLANK': return t('Write how the family member is related to the patient.', 'लिखें कि परिवार के सदस्य का मरीज़ से क्या रिश्ता है।');
    case 'CONSENT_MISSING': return t('Tick the box to confirm before signing.', 'साइन करने से पहले पुष्टि के लिए बॉक्स पर टिक करें।');
    case 'CAN_READ_MISSING': return t('Say whether they can read the note themselves.', 'बताएँ कि क्या वे नोट ख़ुद पढ़ सकते हैं।');
    case 'NO_SIGNATURE': return t('Sign or give a thumb impression on the pad.', 'जगह पर साइन करें या अंगूठा लगाएँ।');
    case 'NOT_READY': return t('Someone still needs to sign.', 'अभी किसी के हस्ताक्षर बाक़ी हैं।');
  }
}

function roleText(t: T, role: GocSignatureRole): string {
  switch (role) {
    case 'DOCTOR': return t('Doctor', 'डॉक्टर');
    case 'PATIENT': return t('Patient', 'मरीज़');
    case 'FAMILY': return t('Family member', 'परिवार के सदस्य');
    case 'WITNESS': return t('Witness', 'गवाह');
  }
}

const shortHash = (hash: string | null) => !hash ? '' : hash.length < 16 ? hash : `${hash.slice(0, 8)}…${hash.slice(-8)}`;
const when = (iso: string) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }); };

export function GocSignOff({ t, noteId, noteText, roles = GOC_DEFAULT_STEPS, onComplete, onInvalidated }: {
  t: T;
  /** The GOC note this sign-off belongs to. Sign-offs are stored on this device under this id. */
  noteId: string;
  /** The exact note text that was read out or read together. Any change invalidates signatures already made. */
  noteText: string;
  /** Steps needed besides the doctor. WITNESS is added on its own for a thumb impression or a signer who cannot read. */
  roles?: readonly GocSignerStep[];
  /** Called once, when the note version is locked. */
  onComplete: (record: GocSignOffRecord) => void;
  /** Called when a change to the note text invalidated earlier signatures. */
  onInvalidated?: (invalidated: GocSignOffRecord) => void;
}) {
  const [record, setRecord] = useState<GocSignOffRecord | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [storageProblem, setStorageProblem] = useState(false);
  const recordRef = useRef<GocSignOffRecord | null>(null);
  const chain = useRef<Promise<void>>(Promise.resolve());
  const callbacks = useRef({ onComplete, onInvalidated });
  useEffect(() => { callbacks.current = { onComplete, onInvalidated }; }, [onComplete, onInvalidated]);

  // Doctor
  const [doctorName, setDoctorName] = useState('');
  const [registrationNo, setRegistrationNo] = useState('');
  // Patient or family member
  const [signerRole, setSignerRole] = useState<'PATIENT' | 'FAMILY'>('PATIENT');
  const [signerName, setSignerName] = useState('');
  const [relation, setRelation] = useState('');
  const [canRead, setCanRead] = useState<boolean | null>(null);
  const [consent, setConsent] = useState(false);
  // Witness
  const [witnessName, setWitnessName] = useState('');
  const [witnessRole, setWitnessRole] = useState('');
  const [witnessConsent, setWitnessConsent] = useState(false);

  const show = (next: GocSignOffRecord) => { recordRef.current = next; setRecord(next); };
  const persist = async (next: GocSignOffRecord) => {
    try { await saveGocSignOff(next); setStorageProblem(false); } catch (e) { console.warn('GOC sign-off could not be saved.', e); setStorageProblem(true); }
  };
  const invalidatedNotice = (old: GocSignOffRecord) => t(
    `The note was changed after it was signed. All ${old.signatures.length} signatures on the earlier text are no longer valid. Read the new note together and sign again.`,
    `साइन होने के बाद नोट बदला गया। पुराने नोट पर किए सभी ${old.signatures.length} हस्ताक्षर अब मान्य नहीं हैं। नया नोट साथ में पढ़ें और फिर से साइन करें।`);

  // Load, then follow the note text: a change after any signature invalidates them all.
  useEffect(() => {
    let active = true;
    // One check at a time, so fast edits to the note never invalidate the same record twice.
    chain.current = chain.current.catch(() => {}).then(async () => {
      let current = recordRef.current?.noteId === noteId ? recordRef.current : null;
      if (!current) {
        const saved = await loadGocSignOffs(noteId).catch(() => [] as GocSignOffRecord[]);
        const last = saved[saved.length - 1];
        current = last && last.status !== 'INVALIDATED' ? last : await gocStartSignOff(noteId, noteText);
        if (!last || last.status === 'INVALIDATED') await persist(current);
      }
      const result = await gocReconcile(current, noteText);
      if (result.invalidated) {
        await persist(result.invalidated);
        if (active) setNotice(invalidatedNotice(result.invalidated));
        callbacks.current.onInvalidated?.(result.invalidated);
      }
      if (result.record !== current) await persist(result.record);
      show(result.record);
    });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [noteId, noteText]);

  if (!record) return <section className="ep-card goc-signoff" aria-busy="true"><p className="ep-hint">{t('Opening the sign-off…', 'साइन-ऑफ़ खुल रहा है…')}</p></section>;
  const step = gocNextStep(record, roles);

  const sign = async (input: Parameters<typeof gocAddSignature>[2]): Promise<boolean> => {
    setError(null);
    const base = recordRef.current;
    if (!base) return false;
    try {
      const next = await gocAddSignature(base, noteText, input, roles);
      await persist(next);
      show(next);
      setNotice(null);
      return true;
    } catch (e) {
      setError(e instanceof GocSignOffError ? errorText(t, e.code) : t('The signature could not be added. Try again.', 'हस्ताक्षर नहीं जुड़ सका। फिर कोशिश करें।'));
      return false;
    }
  };

  const lock = async () => {
    setError(null);
    try {
      const next = await gocLock(record, noteText, roles);
      await persist(next);
      show(next);
      callbacks.current.onComplete(next);
    } catch (e) { setError(e instanceof GocSignOffError ? errorText(t, e.code) : t('The note could not be locked. Try again.', 'नोट लॉक नहीं हो सका। फिर कोशिश करें।')); }
  };

  const restart = async () => {
    const result = await gocRestart(record, noteText);
    if (result.invalidated) await persist(result.invalidated);
    await persist(result.record);
    show(result.record);
    setError(null);
  };

  const signed = record.signatures;
  const witnessReason = signed.some((s) => s.method === 'THUMB')
    ? t('A thumb impression was used, so a witness also signs.', 'अंगूठे का निशान लगा है, इसलिए एक गवाह भी साइन करेंगे।')
    : signed.some((s) => s.canRead === false)
      ? t('The signer cannot read the note, so a witness also signs.', 'साइन करने वाले नोट नहीं पढ़ सकते, इसलिए एक गवाह भी साइन करेंगे।')
      : t('A witness also signs.', 'एक गवाह भी साइन करेंगे।');

  return <section className="ep-card goc-signoff" data-status={record.status} aria-label={t('Sign the note', 'नोट पर हस्ताक्षर')}>
    <div className="goc-signoff-head">
      <h2>{record.status === 'LOCKED' ? t('Note signed and locked', 'नोट पर हस्ताक्षर हुए और लॉक हुआ') : t('Sign the note', 'नोट पर हस्ताक्षर')}</h2>
      {record.status === 'LOCKED' ? <span className="ep-chip goc-locked-chip">{t('Locked', 'लॉक')}</span> : null}
    </div>
    {notice ? <p className="ep-notice ep-alert" role="alert">{notice}</p> : null}
    {storageProblem ? <p className="ep-notice ep-warn" role="alert">{t('This could not be saved on this device. Keep this page open and try again.', 'यह इस डिवाइस पर सेव नहीं हो सका। पेज खुला रखें और फिर कोशिश करें।')}</p> : null}

    {signed.length ? <ol className="goc-signed">{signed.map((s, i) => <li key={`${s.role}-${i}`}>
      {/* eslint-disable-next-line @next/next/no-img-element -- a small local data URL, not a remote image */}
      <img src={s.imageDataUrl} alt={t('Signature of ', 'हस्ताक्षर: ') + s.name} width={150} />
      <span><strong>{s.name}</strong> · {roleText(t, s.role)}{s.relation && s.role !== 'PATIENT' ? ` (${s.relation})` : ''}{s.registrationNo ? ` · ${t('Reg. no.', 'रजि. नं.')} ${s.registrationNo}` : ''}
        <br /><small>{s.method === 'THUMB' ? t('Thumb impression', 'अंगूठे का निशान') : t('Signed', 'हस्ताक्षर')} · {when(s.signedAt)}</small></span>
    </li>)}</ol> : null}

    {step === 'DOCTOR' ? <div className="goc-step">
      <h3>{t('1. Doctor', '1. डॉक्टर')}</h3>
      <div className="goc-fields">
        <label className="ep-field"><span>{t('Name', 'नाम')}</span><input value={doctorName} maxLength={120} autoComplete="name" onChange={(e) => setDoctorName(e.target.value)} /></label>
        <label className="ep-field"><span>{t('Registration no.', 'रजिस्ट्रेशन नंबर')}</span><input value={registrationNo} maxLength={60} onChange={(e) => setRegistrationNo(e.target.value)} /></label>
      </div>
      <GocSignaturePad t={t} allowThumb={false} onDone={(r) => sign({ role: 'DOCTOR', name: doctorName, registrationNo, consent: true, method: r.method, imageDataUrl: r.dataUrl, strokeCount: r.strokeCount })} />
    </div> : null}

    {step === 'PATIENT_OR_FAMILY' ? <div className="goc-step">
      <h3>{t('2. Patient or family member', '2. मरीज़ या परिवार के सदस्य')}</h3>
      <details className="goc-note" open><summary>{t('The note', 'नोट')}</summary><p dir="auto">{noteText}</p></details>
      <div className="goc-choice" role="radiogroup" aria-label={t('Who is signing', 'कौन साइन कर रहे हैं')}>
        <label className="ep-option"><input type="radio" name={`goc-who-${record.id}`} checked={signerRole === 'PATIENT'} onChange={() => setSignerRole('PATIENT')} /><span>{t('Patient', 'मरीज़')}</span></label>
        <label className="ep-option"><input type="radio" name={`goc-who-${record.id}`} checked={signerRole === 'FAMILY'} onChange={() => setSignerRole('FAMILY')} /><span>{t('Family member', 'परिवार के सदस्य')}</span></label>
      </div>
      <div className="goc-fields">
        <label className="ep-field"><span>{t('Name', 'नाम')}</span><input value={signerName} maxLength={120} onChange={(e) => setSignerName(e.target.value)} /></label>
        {signerRole === 'FAMILY' ? <label className="ep-field"><span>{t('Relation to the patient', 'मरीज़ से रिश्ता')}</span><input value={relation} maxLength={120} placeholder={t('e.g. Daughter', 'जैसे बेटी')} onChange={(e) => setRelation(e.target.value)} /></label> : null}
      </div>
      <div className="goc-choice" role="radiogroup" aria-label={t('Can they read the note themselves?', 'क्या वे नोट ख़ुद पढ़ सकते हैं?')}>
        <span>{t('Can they read the note themselves?', 'क्या वे नोट ख़ुद पढ़ सकते हैं?')}</span>
        <label className="ep-option"><input type="radio" name={`goc-read-${record.id}`} checked={canRead === true} onChange={() => setCanRead(true)} /><span>{t('Yes', 'हाँ')}</span></label>
        <label className="ep-option"><input type="radio" name={`goc-read-${record.id}`} checked={canRead === false} onChange={() => setCanRead(false)} /><span>{t('No, it was read to them', 'नहीं, उन्हें पढ़कर सुनाया गया')}</span></label>
      </div>
      <label className="ep-option"><input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span><strong>{t('I have read or heard this note and it records what we discussed', 'मैंने यह नोट पढ़ा या सुना है और इसमें वही लिखा है जिस पर हमने बात की')}</strong></span></label>
      <GocSignaturePad t={t} onDone={(r) => sign({ role: signerRole, name: signerName, relation: signerRole === 'FAMILY' ? relation : 'Self', canRead, consent, method: r.method, imageDataUrl: r.dataUrl, strokeCount: r.strokeCount })} />
    </div> : null}

    {step === 'WITNESS' ? <div className="goc-step">
      <h3>{t('3. Witness', '3. गवाह')}</h3>
      <p className="ep-hint">{witnessReason}</p>
      <div className="goc-fields">
        <label className="ep-field"><span>{t('Name', 'नाम')}</span><input value={witnessName} maxLength={120} onChange={(e) => setWitnessName(e.target.value)} /></label>
        <label className="ep-field"><span>{t('Role (optional)', 'भूमिका (वैकल्पिक)')}</span><input value={witnessRole} maxLength={120} placeholder={t('e.g. Nurse', 'जैसे नर्स')} onChange={(e) => setWitnessRole(e.target.value)} /></label>
      </div>
      <label className="ep-option"><input type="checkbox" checked={witnessConsent} onChange={(e) => setWitnessConsent(e.target.checked)} />
        <span><strong>{t('I saw the note read out and the person sign or give their thumb impression', 'मैंने नोट पढ़कर सुनाते और व्यक्ति को साइन करते या अंगूठा लगाते देखा')}</strong></span></label>
      <GocSignaturePad t={t} allowThumb={false} onDone={(r) => sign({ role: 'WITNESS', name: witnessName, relation: witnessRole, consent: witnessConsent, method: r.method, imageDataUrl: r.dataUrl, strokeCount: r.strokeCount })} />
    </div> : null}

    {error ? <p className="ep-notice ep-warn" role="alert">{error}</p> : null}

    {step === 'LOCK' ? <div className="ep-actions">
      <button type="button" className="ep-btn" onClick={() => void lock()}>{t('Lock this note version', 'नोट का यह संस्करण लॉक करें')}</button>
    </div> : null}
    {record.status === 'OPEN' && signed.length ? <div className="ep-actions">
      <button type="button" className="ep-btn ep-danger" onClick={() => void restart()}>{t('Start the signatures again', 'हस्ताक्षर फिर से शुरू करें')}</button>
    </div> : null}

    {record.status === 'LOCKED' ? <p className="ep-notice ep-info goc-seal">
      {t('Locked on ', 'लॉक हुआ: ')}{when(record.lockedAt ?? '')}. {t('Any change to the note or a signature will show.', 'नोट या किसी हस्ताक्षर में बदलाव दिख जाएगा।')}
      <br /><small>SHA-256 {shortHash(record.sealSha256)}</small>
    </p> : null}
  </section>;
}
