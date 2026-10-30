'use client';
// DPDP notice at the start of the care plan and the living will (tech spec §7, L10), with a working "download my data" and
// "delete my local draft". Signed versions and the audit log cannot be deleted. Family members see the notice only.
import { useState } from 'react';
import { careDir, careText, type CareLang } from './care-i18n';
import { listAmdVersions, deleteAmdDrafts, readAmdScan } from './amd-store';
import { amdScanKeys, buildDataExport, dataExportFileName, dpdpNotice, type DpdpScope } from './dpdp';
import { localCardsFor } from './ed-card-client';
import {
  deleteEctprDrafts, listAudit, listEctprDistribution, listEctprVersions, listEpisodeLogs, listIncidentReports, listOutcomeRecords, logAudit, readEctprScan,
} from './ectpr-store';
import { ReadAloudButton } from './tts-speech';

const SEEN_KEY = 'sahara-dpdp-seen:';

function readSeen(scope: DpdpScope): boolean {
  try { return localStorage.getItem(SEEN_KEY + scope) === '1'; } catch { return false; }
}

function fileToDataUrl(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function collectMyData(patientId: string) {
  const [ectprVersions, distribution, audit, episodes, outcomes, incidents, amdVersions] = await Promise.all([
    listEctprVersions(patientId), listEctprDistribution(patientId), listAudit(patientId), listEpisodeLogs(patientId), listOutcomeRecords(patientId), listIncidentReports(patientId), listAmdVersions(patientId),
  ]);
  const scans: Record<string, { name: string; type: string; dataUrl: string }> = {};
  const keep = async (key: string, file: File | undefined) => {
    if (file) scans[key] = { name: file.name, type: file.type, dataUrl: await fileToDataUrl(file) };
  };
  for (const key of amdScanKeys(amdVersions)) await keep(key, await readAmdScan(key));
  for (const item of distribution) if (item.scan) await keep(item.scan.scanKey, await readEctprScan(item.scan.scanKey));
  return buildDataExport({ patientId, ectprVersions, distribution, audit, episodes, outcomes, incidents, amdVersions, scans, cards: localCardsFor(patientId) });
}

export function DpdpNotice(props: {
  scope: DpdpScope; lang: CareLang; patientId: string; actor: string; role: 'doctor' | 'patient' | 'family';
  /** Keeps the notice in the look of the screen it sits on. */
  cardClass: string; buttonClass: string; dangerClass: string;
}) {
  const { scope, lang, patientId, actor, role, cardClass, buttonClass, dangerClass } = props;
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const [open, setOpen] = useState(() => !readSeen(scope));
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const blocks = dpdpNotice(scope);
  const spoken = () => blocks.map((b) => `${t(b.titleEn, b.titleHi)}. ${t(b.en, b.hi)}`).join('\n');

  function understood() {
    try { localStorage.setItem(SEEN_KEY + scope, '1'); } catch { /* The notice just opens again next time. */ }
    setOpen(false);
  }

  async function download() {
    setBusy(true); setMessage('');
    try {
      const now = new Date();
      const data = await collectMyData(patientId);
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = dataExportFileName(patientId, now);
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      await logAudit({ actor, action: 'SHARE', recordType: scope === 'care-plan' ? 'ECTPR' : 'AMD', recordId: patientId, patientId, detail: 'Personal data export downloaded' });
      setMessage(t('Your data is downloading. It holds private health details, so keep the file safe.', 'आपका डेटा डाउनलोड हो रहा है। इसमें निजी सेहत की जानकारी है, इसलिए फ़ाइल सँभालकर रखें।'));
    } catch {
      setMessage(t('The download could not be made. Try again.', 'डाउनलोड नहीं बन सका। फिर कोशिश करें।'));
    } finally { setBusy(false); }
  }

  async function removeDraft() {
    setBusy(true); setMessage('');
    try {
      const { deleted } = scope === 'care-plan' ? await deleteEctprDrafts(patientId, actor) : await deleteAmdDrafts(patientId, actor);
      setConfirming(false);
      setMessage(deleted > 0
        ? t('The unsigned draft is deleted from this device. Signed versions and the log were kept.', 'बिना साइन का ड्राफ़्ट इस डिवाइस से मिट गया। साइन किए गए संस्करण और रिकॉर्ड रखे गए हैं।')
        : t('There is no unsigned draft to delete. A draft that someone has already signed is kept.', 'मिटाने के लिए कोई बिना साइन का ड्राफ़्ट नहीं है। जिस ड्राफ़्ट पर किसी ने साइन कर दिया है, वह रखा जाता है।'));
    } catch {
      setMessage(t('The draft could not be deleted. Try again.', 'ड्राफ़्ट नहीं मिट सका। फिर कोशिश करें।'));
    } finally { setBusy(false); }
  }

  return <section className={`${cardClass} dpdp-notice`} dir={careDir(lang)} aria-labelledby={`dpdp-${scope}`}>
    <details open={open} onToggle={(event) => setOpen(event.currentTarget.open)}>
      <summary id={`dpdp-${scope}`} className="dpdp-summary">{t('Your data and your rights', 'आपका डेटा और आपके अधिकार')}</summary>
      <ReadAloudButton lang={lang} text={spoken} />
      <dl className="dpdp-list">
        {blocks.map((block) => <div key={block.titleEn}><dt>{t(block.titleEn, block.titleHi)}</dt><dd>{t(block.en, block.hi)}</dd></div>)}
      </dl>
      {role === 'family' ? <p className="dpdp-hint">{t('Only the patient or the care team can download or delete this data. Please ask them.', 'यह डेटा सिर्फ़ मरीज़ या देखभाल टीम डाउनलोड कर या मिटा सकती है। कृपया उनसे कहें।')}</p> : <div className="dpdp-actions">
        <button type="button" className={buttonClass} onClick={() => void download()} disabled={busy}>{t('Download my data', 'मेरा डेटा डाउनलोड करें')}</button>
        <button type="button" className={dangerClass} onClick={() => setConfirming(!confirming)} aria-expanded={confirming} disabled={busy}>{t('Delete my local draft', 'मेरा लोकल ड्राफ़्ट मिटाएँ')}</button>
      </div>}
      {confirming ? <div className="dpdp-confirm" role="alertdialog" aria-label={t('Delete my local draft', 'मेरा लोकल ड्राफ़्ट मिटाएँ')}>
        <p>{t('This deletes the unsigned draft from this device. It cannot be undone. Signed versions and the log stay.', 'इससे बिना साइन का ड्राफ़्ट इस डिवाइस से मिट जाएगा। इसे वापस नहीं लाया जा सकता। साइन किए गए संस्करण और रिकॉर्ड रहेंगे।')}</p>
        <div className="dpdp-actions">
          <button type="button" className={dangerClass} onClick={() => void removeDraft()} disabled={busy}>{t('Yes, delete the draft', 'हाँ, ड्राफ़्ट मिटाएँ')}</button>
          <button type="button" className={buttonClass} onClick={() => setConfirming(false)}>{t('Keep the draft', 'ड्राफ़्ट रखें')}</button>
        </div>
      </div> : null}
      {message ? <p className="dpdp-message" role="status">{message}</p> : null}
      <div className="dpdp-actions"><button type="button" className="dpdp-link" onClick={understood}>{t('I have read this', 'मैंने यह पढ़ लिया')}</button></div>
    </details>
  </section>;
}
