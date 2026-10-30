'use client';
// Parts of the care plan screen for a signed plan: the review SMS (tech spec §8), the signed paper scan with its hash and the copy
// checklist (§8 "Should", same as the living will has), and the draft FHIR R4 export (§7 stretch).
import { useEffect, useMemo, useState } from 'react';
import { careLocale, type CareLang } from './care-i18n';
import { ECTPR_FHIR_DRAFT_NOTE, ectprFhirBundle } from './ectpr-fhir';
import type { EctprCopyKind, EctprDistribution, EctprRecord } from './ectpr-model';
import { daysUntilReview } from './ectpr-rules';
import { getEctprDistribution, logAudit, readEctprScan, saveEctprScan, setEctprCopy } from './ectpr-store';
import { indianMobileE164 } from './ed-card-snapshot';

type T = (en: string, hi: string) => string;

// ---------------------------------------------------------------------------
// Review reminder

function dayText(lang: CareLang, iso: string): string {
  const date = new Date(`${iso}T00:00:00`);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleDateString(careLocale(lang, 'en-IN'), { day: 'numeric', month: 'short', year: 'numeric' });
}

export function ReviewReminder({ t, lang, record, actorName }: { t: T; lang: CareLang; record: EctprRecord; actorName: string }) {
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const days = daysUntilReview(record) ?? 0;
  const dm = record.decisionMakers.primary;
  const recipient = indianMobileE164(record.patient.phone)
    ? { name: record.patient.name, phone: record.patient.phone, who: 'patient' }
    : indianMobileE164(dm?.phone24h) ? { name: dm?.name ?? '', phone: dm?.phone24h ?? '', who: 'primary decision-maker' } : null;

  useEffect(() => {
    let live = true;
    fetch('/api/care-plan-reminder', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action: 'status' }) })
      .then((response) => response.json() as Promise<{ configured?: boolean }>)
      .then((answer) => { if (live) setConfigured(Boolean(answer.configured)); })
      .catch(() => { if (live) setConfigured(false); });
    return () => { live = false; };
  }, []);

  async function send() {
    if (!recipient) return;
    setBusy(true); setResult(null);
    let status = 'unreachable';
    try {
      const response = await fetch('/api/care-plan-reminder', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'send', phone: recipient.phone, patientFirstName: record.patient.name.split(/\s+/)[0] ?? '', reviewDue: record.reviewDue }),
      });
      status = ((await response.json()) as { status?: string }).status ?? 'error';
    } catch { /* Stays "unreachable". */ }
    setBusy(false);
    const sent = status === 'sent';
    setResult({
      ok: sent,
      text: sent ? t(`Reminder sent to ${recipient.name}.`, `${recipient.name} को याद दिलाने वाला SMS भेजा गया।`)
        : status === 'not-configured' ? t('SMS not configured on this server. No message was sent. Please phone them.', 'इस सर्वर पर SMS सेट नहीं है। कोई संदेश नहीं गया। कृपया फ़ोन करें।')
          : status === 'too-many' ? t('Too many reminders to this number in the last hour. No message was sent.', 'पिछले एक घंटे में इस नंबर पर बहुत सारे संदेश गए। कोई संदेश नहीं गया।')
            : t('The SMS could not be sent. Please phone them.', 'SMS नहीं भेजा जा सका। कृपया फ़ोन करें।'),
    });
    await logAudit({ actor: actorName, action: 'SHARE', recordType: 'ECTPR', recordId: record.id, patientId: record.patientId, detail: `Review reminder SMS to ${recipient.who} (number ending ${recipient.phone.replace(/\D/g, '').slice(-4)}): ${status}` }).catch(() => undefined);
  }

  return <div className="ep-notice ep-warn" role="status">
    <p><strong>{days < 0 ? t(`Review overdue since ${dayText(lang, record.reviewDue)}`, `समीक्षा ${dayText(lang, record.reviewDue)} से बाक़ी है`)
      : days === 0 ? t('Review is due today', 'समीक्षा आज है')
        : t(`Review due in ${days} days, by ${dayText(lang, record.reviewDue)}`, `समीक्षा ${days} दिन में, ${dayText(lang, record.reviewDue)} तक`)}</strong></p>
    {configured === false ? <p>{t('SMS not configured on this server, so reminders cannot be sent. Please phone them.', 'इस सर्वर पर SMS सेट नहीं है, इसलिए संदेश नहीं भेजे जा सकते। कृपया फ़ोन करें।')}</p> : null}
    {!recipient ? <p>{t('No valid mobile number for the patient or the primary decision-maker.', 'मरीज़ या मुख्य निर्णयकर्ता का कोई सही मोबाइल नंबर नहीं है।')}</p> : <p>{t(`The reminder goes to ${recipient.name}.`, `संदेश ${recipient.name} को जाएगा।`)}</p>}
    <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" disabled={busy || !recipient || configured !== true} onClick={() => void send()}>{busy ? t('Sending…', 'भेज रहे हैं…') : t('Send review reminder', 'समीक्षा का संदेश भेजें')}</button></div>
    {result ? <p style={{ marginTop: 10 }}>{result.text}</p> : null}
  </div>;
}

// ---------------------------------------------------------------------------
// Signed paper scan and copies

const COPIES: Array<{ kind: EctprCopyKind; en: string; hi: string }> = [
  { kind: 'HOME', en: 'One copy at home', hi: 'एक कॉपी घर पर' },
  { kind: 'FAMILY_TRAVEL', en: 'One with the family member who travels with the patient', hi: 'एक उस परिजन के पास जो मरीज़ के साथ जाते हैं' },
  { kind: 'FRONT_DOOR', en: 'One inside the front door, for the ambulance crew', hi: 'एक दरवाज़े के अंदर, एम्बुलेंस टीम के लिए' },
  { kind: 'LOCAL_DOCTOR', en: 'One with the local doctor', hi: 'एक स्थानीय डॉक्टर के पास' },
  { kind: 'PALLIATIVE_TEAM', en: 'One with the palliative care team', hi: 'एक पैलिएटिव केयर टीम के पास' },
];

export function PaperAndCopies({ t, lang, record, actorName }: { t: T; lang: CareLang; record: EctprRecord; actorName: string }) {
  const [state, setState] = useState<EctprDistribution | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let live = true;
    void getEctprDistribution(record).then((found) => { if (live) setState(found); });
    return () => { live = false; };
  }, [record]);

  async function upload(file: File | undefined) {
    if (!file) return;
    if (file.size > 15 * 1024 * 1024) { setMessage(t('This file is too large. Use one under 15 MB.', 'यह फ़ाइल बहुत बड़ी है। 15 MB से छोटी फ़ाइल चुनें।')); return; }
    setBusy(true); setMessage('');
    try { setState(await saveEctprScan(record, file, actorName)); }
    catch { setMessage(t('The file could not be saved on this device.', 'फ़ाइल इस डिवाइस पर सेव नहीं हो सकी।')); }
    finally { setBusy(false); }
  }

  async function openScan() {
    if (!state?.scan) return;
    const file = await readEctprScan(state.scan.scanKey);
    if (!file) { setMessage(t('The scan is not on this device.', 'स्कैन इस डिवाइस पर नहीं है।')); return; }
    const url = URL.createObjectURL(file);
    window.open(url, '_blank', 'noopener');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  if (!state) return null;
  const inputId = `ep-scan-${record.id}`;
  return <section className="ep-card" aria-labelledby="ep-paper-title">
    <h2 id="ep-paper-title">{t('Signed paper and copies', 'साइन किया काग़ज़ और कॉपियाँ')}</h2>
    {state.scan ? <div className="ep-notice ep-info">
      <p><strong>{state.scan.scanName}</strong> · {t('uploaded by', 'अपलोड किया')} {state.scan.uploadedBy} · {dayText(lang, state.scan.scanUploadedAt.slice(0, 10))}</p>
      <p className="ep-hash">SHA-256 <code>{state.scan.scanSha256}</code></p>
      <div className="ep-actions"><button type="button" className="ep-btn ep-quiet" onClick={() => void openScan()}>{t('View the scan', 'स्कैन देखें')}</button></div>
    </div> : <p className="ep-hint">{t('No scan yet. Add a photo or scan of the signed paper. It stays on this device, and its fingerprint (SHA-256) goes in the log.', 'अभी कोई स्कैन नहीं। साइन किए काग़ज़ की फ़ोटो या स्कैन जोड़ें। यह इसी डिवाइस पर रहेगा, और इसकी पहचान (SHA-256) रिकॉर्ड में जाएगी।')}</p>}
    <div className="ep-field" style={{ marginTop: 10 }}>
      <label htmlFor={inputId}>{state.scan ? t('Replace the scan', 'स्कैन बदलें') : t('Add the signed paper', 'साइन किया काग़ज़ जोड़ें')}</label>
      <input id={inputId} type="file" accept="image/*,application/pdf" disabled={busy} onChange={(event) => { void upload(event.target.files?.[0]); event.target.value = ''; }} />
    </div>
    {message ? <p className="ep-notice ep-alert" role="alert" style={{ marginTop: 10 }}>{message}</p> : null}
    <fieldset className="ep-group" style={{ marginTop: 14, marginBottom: 0 }}>
      <legend>{t('Who has a copy?', 'कॉपी किसके पास है?')}</legend>
      <div className="ep-options">{COPIES.map((copy) => {
        const entry = state.copies.find((c) => c.kind === copy.kind);
        return <label key={copy.kind} className="ep-option">
          <input type="checkbox" checked={entry?.given ?? false} onChange={(event) => { void setEctprCopy(record, copy.kind, event.target.checked, actorName).then(setState); }} />
          <span><strong>{t(copy.en, copy.hi)}</strong>{entry?.given && entry.givenOn ? <small>{t('Given on', 'दी गई')} {dayText(lang, entry.givenOn)}</small> : null}</span>
        </label>;
      })}</div>
    </fieldset>
  </section>;
}

// ---------------------------------------------------------------------------
// Draft FHIR R4 export

export function FhirExportButton({ t, record, actorName }: { t: T; record: EctprRecord; actorName: string }) {
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const json = useMemo(() => {
    if (!open) return '';
    const bundle = ectprFhirBundle(record);
    return bundle ? JSON.stringify(bundle, null, 2) : '';
  }, [open, record]);
  if (!ectprFhirBundle(record)) return null;

  function logged(detail: string) {
    void logAudit({ actor: actorName, action: 'SHARE', recordType: 'ECTPR', recordId: record.id, patientId: record.patientId, detail }).catch(() => undefined);
  }
  function download() {
    const url = URL.createObjectURL(new Blob([json], { type: 'application/fhir+json' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `ECTPR-FHIR-R4-draft-${record.patient.uhid.replace(/[^A-Za-z0-9_-]+/g, '-')}-v${record.version}.json`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    logged(`FHIR R4 draft export of version ${record.version} downloaded`);
  }
  function copy() {
    void navigator.clipboard?.writeText(json).then(() => { setCopied(true); logged(`FHIR R4 draft export of version ${record.version} copied`); });
  }

  return <>
    <button type="button" className="ep-btn ep-quiet" onClick={() => setOpen(true)}>{t('Export FHIR (R4)', 'FHIR (R4) निर्यात करें')}</button>
    {open ? <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false); }}>
      <section className="modal-panel large-modal fhir-modal" role="dialog" aria-modal="true" aria-labelledby="ep-fhir-title">
        <div className="modal-header">
          <div>
            <span className="fhir-badge">{t('FHIR R4 · draft format', 'FHIR R4 · ड्राफ़्ट फ़ॉर्मैट')}</span>
            <h2 id="ep-fhir-title">{t('Consent resource (scope adr)', 'Consent रिसोर्स (scope adr)')}</h2>
            <p className="ep-hint">{t(ECTPR_FHIR_DRAFT_NOTE, 'ड्राफ़्ट फ़ॉर्मैट। ABDM प्रोफ़ाइल या FHIR वैलिडेटर से जाँचा नहीं गया।')}</p>
          </div>
          <button type="button" className="close-btn" onClick={() => setOpen(false)} aria-label={t('Close', 'बंद करें')}>✕</button>
        </div>
        <div className="fhir-body"><div className="fhir-json-wrap"><pre className="fhir-json-code"><code>{json}</code></pre></div></div>
        <div className="fhir-footer">
          <div className="ep-actions" style={{ marginTop: 0 }}>
            <button type="button" className="secondary-button" onClick={copy}>{copied ? t('Copied', 'कॉपी हुआ') : t('Copy JSON', 'JSON कॉपी करें')}</button>
            <button type="button" className="primary-button" onClick={download}>{t('Download (.json)', 'डाउनलोड (.json)')}</button>
          </div>
          <button type="button" className="secondary-button" onClick={() => setOpen(false)}>{t('Close', 'बंद करें')}</button>
        </div>
      </section>
    </div> : null}
  </>;
}
