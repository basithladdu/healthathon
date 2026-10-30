'use client';
// Offline path for paramedics and the ED when there is no internet (tech spec §7 "works offline"):
//  1. "Saved on this device": the issuing phone shows the ED Quick View from its cached snapshot.
//  2. "Scan a printed card": reads the card's offline QR (plain text, no internet) with the camera where the
//     browser supports BarcodeDetector; otherwise any phone camera app shows the same text.
// A scanned live-card QR (/ed/<token>) is opened directly.
import { useEffect, useRef, useState } from 'react';
import type { CareLang } from './care-i18n';
import { careText } from './care-i18n';
import type { EctprRecord } from './ectpr-model';
import { edInterpretation } from './ectpr-rules';
import { logAudit } from './ectpr-store';
import { EctprEdView, edSpokenText } from './ectpr-ed-view';
import { ifInDoubtTitle, walletCardLine } from './ectpr-labels';
import { quickViewSnapshot } from './ed-card-snapshot';
import { IconVolume2 } from './icons';
import { useSpeechSynthesis } from './tts-speech';

type Detector = { detect(source: CanvasImageSource): Promise<{ rawValue: string }[]> };
type DetectorCtor = new (options: { formats: string[] }) => Detector;

function barcodeDetector(): DetectorCtor | null {
  const ctor = (globalThis as { BarcodeDetector?: DetectorCtor }).BarcodeDetector;
  return typeof ctor === 'function' ? ctor : null;
}

function CameraScan(props: { lang: CareLang; onText: (text: string) => void }) {
  const { lang, onText } = props;
  const video = useRef<HTMLVideoElement | null>(null);
  // The latest callback, so a parent re-render does not restart the camera.
  const latest = useRef(onText);
  useEffect(() => { latest.current = onText; }, [onText]);
  const [error, setError] = useState<string | null>(() => barcodeDetector() ? null : careText(lang, 'This browser cannot read QR codes. Open the phone’s camera app and point it at the “offline summary” code on the card — it shows the plan as text, without internet.', 'यह ब्राउज़र QR नहीं पढ़ सकता। फ़ोन का कैमरा ऐप खोलें और कार्ड के "ऑफ़लाइन सारांश" कोड पर रखें — बिना इंटरनेट के योजना टेक्स्ट में दिखेगी।'));

  useEffect(() => {
    const Ctor = barcodeDetector();
    if (!Ctor) return;
    let stream: MediaStream | null = null;
    let timer = 0;
    let stopped = false;
    const detector = new Ctor({ formats: ['qr_code'] });
    navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((s) => {
        stream = s;
        if (stopped || !video.current) { s.getTracks().forEach((track) => track.stop()); return; }
        video.current.srcObject = s;
        void video.current.play();
        const tick = async () => {
          if (stopped || !video.current) return;
          try {
            const found = await detector.detect(video.current);
            if (found[0]?.rawValue) { latest.current(found[0].rawValue); return; }
          } catch { /* frame not ready */ }
          timer = window.setTimeout(tick, 300);
        };
        void tick();
      })
      .catch(() => setError(careText(lang, 'The camera could not start. Allow camera access, or use the phone’s camera app on the offline code.', 'कैमरा शुरू नहीं हुआ। कैमरा की अनुमति दें, या फ़ोन के कैमरा ऐप से ऑफ़लाइन कोड पढ़ें।')));
    return () => { stopped = true; window.clearTimeout(timer); stream?.getTracks().forEach((track) => track.stop()); };
  }, [lang]);

  if (error) return <p className="scanner-status-text">{error}</p>;
  return (
    <div className="scanner-viewfinder">
      <video ref={video} className="scanner-video" muted playsInline aria-label={careText(lang, 'Camera view', 'कैमरा दृश्य')} />
      <div className="scanner-target-box"><span className="corner top-left" /><span className="corner top-right" /><span className="corner bottom-left" /><span className="corner bottom-right" /></div>
      <p className="scanner-status-text">{careText(lang, 'Point the camera at the QR code on the card…', 'कैमरा को कार्ड के QR कोड पर रखें…')}</p>
    </div>
  );
}

export function OfflineQrScannerModal(props: {
  /** The issuing device's cached snapshot, when there is one. */
  record?: EctprRecord | null;
  /** Text of the card's offline QR (for display alongside the cached copy). */
  offlineText?: string | null;
  lang?: CareLang;
  actor?: string;
  onClose: () => void;
}) {
  const lang = props.lang ?? 'en';
  const { record, onClose } = props;
  const actor = props.actor ?? 'Paramedic (offline view)';
  const [mode, setMode] = useState<'saved' | 'scan'>(record ? 'saved' : 'scan');
  const [scanned, setScanned] = useState<string | null>(null);
  const { speak, isPlaying, cancel } = useSpeechSynthesis(lang);
  const quick = record ? quickViewSnapshot(record) : null;

  const loggedSaved = useRef<string | null>(null);
  useEffect(() => {
    if (!record || mode !== 'saved') return;
    const key = `${record.id}:${record.version}`;
    if (loggedSaved.current === key) return; // one entry per opening, not per re-render
    loggedSaved.current = key;
    void logAudit({ actor, action: 'VIEW', recordType: 'CARD', recordId: record.id, patientId: record.patientId, detail: 'Offline view of the card saved on this device' }).catch(() => undefined);
  }, [record, mode, actor]);


  function onText(text: string) {
    const live = /^https?:\/\/[^\s]+\/ed\/[A-Za-z0-9_-]{22,64}$/.exec(text.trim());
    if (live && navigator.onLine !== false) { window.location.assign(text.trim()); return; }
    setScanned(text);
    void logAudit({ actor, action: 'VIEW', recordType: 'CARD', recordId: record?.id ?? 'scanned-card', ...(record ? { patientId: record.patientId } : {}), detail: live ? 'Scanned a live-card QR while offline' : 'Scanned the offline summary QR' }).catch(() => undefined);
  }

  const spoken = quick ? edSpokenText(quick, edInterpretation(quick), lang) : scanned ?? '';

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-panel offline-scanner-modal" role="dialog" aria-modal="true" aria-labelledby="scanner-modal-title">
        <div className="modal-header">
          <div className="flex items-center gap-2">
            <span className="scanner-badge">{careText(lang, 'Works offline', 'ऑफ़लाइन चलता है')}</span>
            <h2 id="scanner-modal-title">{careText(lang, 'Emergency card — offline', 'आपातकालीन कार्ड — ऑफ़लाइन')}</h2>
          </div>
          <button type="button" className="close-btn" onClick={onClose} aria-label={careText(lang, 'Close', 'बंद करें')}>✕</button>
        </div>

        <div className="scanner-tabs" role="tablist">
          {record && <button type="button" role="tab" aria-selected={mode === 'saved'} className={mode === 'saved' ? 'is-active' : ''} onClick={() => setMode('saved')}>{careText(lang, 'Saved on this device', 'इस डिवाइस पर सहेजा')}</button>}
          <button type="button" role="tab" aria-selected={mode === 'scan'} className={mode === 'scan' ? 'is-active' : ''} onClick={() => { setScanned(null); setMode('scan'); }}>{careText(lang, 'Scan a printed card', 'छपा कार्ड स्कैन करें')}</button>
        </div>

        <div className="scanner-content">
          {mode === 'saved' && quick && <EctprEdView record={quick} lang={lang} />}
          {mode === 'saved' && props.offlineText && (
            <details className="scanner-offline-details">
              <summary>{careText(lang, 'Text inside the offline QR code', 'ऑफ़लाइन QR कोड का टेक्स्ट')}</summary>
              <pre className="scanner-offline-text">{props.offlineText}</pre>
            </details>
          )}
          {mode === 'scan' && !scanned && <CameraScan lang={lang} onText={onText} />}
          {mode === 'scan' && scanned && (
            <div className="scanner-result-view">
              <div className="ectpr-ed-doubt"><h3>{ifInDoubtTitle(lang)}</h3><p>{walletCardLine(lang)}</p></div>
              <p className="scanner-rule-callout">{careText(lang, 'Printed offline summary. Check the version and review date on it: if the review date has passed or the card is unsigned, give standard care.', 'छपा ऑफ़लाइन सारांश। उस पर संस्करण और समीक्षा तारीख देखें: तारीख निकल गई हो या हस्ताक्षर न हो, तो सामान्य इलाज दें।')}</p>
              <pre className="scanner-offline-text">{scanned}</pre>
            </div>
          )}
        </div>

        <div className="scanner-footer">
          {spoken && (
            <button type="button" className="secondary-button" onClick={() => (isPlaying ? cancel() : speak(spoken, 1.0))}>
              <IconVolume2 className="w-4 h-4" /> {isPlaying ? careText(lang, 'Stop reading', 'पढ़ना रोकें') : careText(lang, 'Read aloud', 'पढ़कर सुनाएँ')}
            </button>
          )}
          <button type="button" className="secondary-button" onClick={onClose}>{careText(lang, 'Close', 'बंद करें')}</button>
        </div>
      </section>
    </div>
  );
}
