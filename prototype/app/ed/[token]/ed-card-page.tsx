'use client';

import { useEffect, useRef, useState } from 'react';
import type { CareLang } from '../../care-i18n';
import { careText } from '../../care-i18n';
import type { EctprRecord } from '../../ectpr-model';
import { logAudit } from '../../ectpr-store';
import { EctprEdView, IfInDoubtBox, edSpokenText } from '../../ectpr-ed-view';
import { edInterpretation } from '../../ectpr-rules';
import { ReadAloudButton } from '../../tts-speech';
import { edCardUrl, openEdCard } from '../../ed-card-client';
import { BREAK_GLASS_MIN_REASON, ED_TOKEN_PATTERN, type EdCardViewResult } from '../../ed-card-snapshot';
import { formatDay } from '../../ectpr-labels';
import { OfflineQrScannerModal } from '../../offline-qr-scanner-modal';

type Loaded = (EdCardViewResult & { offline?: boolean }) | null;

export function EdCardPage(props: { token: string }) {
  const token = ED_TOKEN_PATTERN.test(props.token) ? props.token : null;
  const [lang, setLang] = useState<CareLang>('en');
  const [result, setResult] = useState<Loaded>(token ? null : { status: 'unknown' });
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [glassError, setGlassError] = useState<string | null>(null);
  const [scanner, setScanner] = useState(false);
  const logged = useRef(false);
  const t = (en: string, hi: string) => careText(lang, en, hi);

  useEffect(() => {
    if (!token) return;
    let live = true;
    openEdCard(token, 'view').then((r) => { if (live) setResult(r); });
    return () => { live = false; };
  }, [token]);

  useEffect(() => {
    if (!result || result.status !== 'live' || logged.current) return;
    logged.current = true;
    void logAudit({ actor: 'ED (QR scan)', action: 'VIEW', recordType: 'CARD', recordId: result.record.id, patientId: result.record.patientId, detail: result.offline ? 'ED Quick View from the copy saved on this device (offline)' : 'ED Quick View opened from the card QR' }).catch(() => undefined);
  }, [result]);

  async function breakGlass(event: React.FormEvent) {
    event.preventDefault();
    if (!token || reason.trim().length < BREAK_GLASS_MIN_REASON) return;
    setBusy(true); setGlassError(null);
    const r = await openEdCard(token, 'break-glass', reason.trim());
    setBusy(false);
    if (r.status === 'live' && r.full) {
      setResult(r);
      void logAudit({ actor: 'ED (break-glass)', action: 'BREAK_GLASS', recordType: 'ECTPR', recordId: r.record.id, patientId: r.record.patientId, detail: `Reason: ${reason.trim()}${r.offline ? ' (offline copy on this device)' : ''} · ${r.sms?.message ?? ''}` }).catch(() => undefined);
    } else if (r.status === 'revoked') setResult(r);
    else setGlassError('message' in r ? r.message : t('Break-glass failed.', 'ब्रेक-ग्लास नहीं हुआ।'));
  }

  const record: EctprRecord | null = result?.status === 'live' ? result.record : null;

  return (
    <main className="ed-page" lang={lang}>
      <header className="ed-page-header">
        <span className="ed-page-brand">Sahara</span>
        <span className="ed-page-tag">{t('Emergency care plan', 'आपातकालीन देखभाल योजना')}</span>
        <button type="button" className="ed-page-lang" onClick={() => setLang(lang === 'en' ? 'hi' : 'en')}>{lang === 'en' ? 'हिन्दी' : 'English'}</button>
      </header>

      {!result && <p className="ectpr-muted">{t('Opening the emergency card…', 'आपातकालीन कार्ड खुल रहा है…')}</p>}

      {result?.status === 'revoked' && (
        <section className="ectpr-card ectpr-ed ectpr-ed--revoked">
          <div className="ectpr-ed-banner ectpr-ed-banner--revoked" role="status">
            <strong className="ectpr-ed-withdrawn">{t('CARD WITHDRAWN', 'कार्ड वापस लिया गया')}</strong>
            <span>{result.revokedAt ? t(`This card was withdrawn on ${formatDay(result.revokedAt, lang)}.`, `यह कार्ड ${formatDay(result.revokedAt, lang)} को वापस लिया गया।`) : t('This card was withdrawn.', 'यह कार्ड वापस लिया गया।')} {t('Do not use it. Give standard emergency care and contact the family or treating team for the current plan.', 'इसका उपयोग न करें। सामान्य आपातकालीन इलाज दें और मौजूदा योजना के लिए परिवार या इलाज करने वाली टीम से संपर्क करें।')}</span>
          </div>
          <IfInDoubtBox lang={lang} />
        </section>
      )}

      {result?.status === 'unknown' && (
        <section className="ectpr-card ectpr-ed">
          <div className="ectpr-ed-banner ectpr-ed-banner--none" role="status">{t('This QR code does not open a care plan. It may be mistyped or very old. Give standard emergency care.', 'यह QR कोई योजना नहीं खोलता। गलत या बहुत पुराना हो सकता है। सामान्य आपातकालीन इलाज दें।')}</div>
          <IfInDoubtBox lang={lang} />
        </section>
      )}

      {result && result.status !== 'live' && result.status !== 'revoked' && result.status !== 'unknown' && (
        <section className="ectpr-card ectpr-ed">
          <div className="ectpr-ed-banner ectpr-ed-banner--none" role="status">
            <strong>{t('The live card could not be opened.', 'लाइव कार्ड नहीं खुल सका।')}</strong> <span>{result.message}</span>
          </div>
          <p>{t('Use the second QR code on the printed card ("offline summary"): any phone camera shows it as text, without internet.', 'छपे कार्ड का दूसरा QR ("ऑफ़लाइन सारांश") इस्तेमाल करें: कोई भी फ़ोन कैमरा इसे बिना इंटरनेट के टेक्स्ट में दिखाता है।')}</p>
          <div className="ectpr-actions-row">
            <button type="button" className="ectpr-button ectpr-button--primary" onClick={() => setScanner(true)}>{t('Scan the offline code', 'ऑफ़लाइन कोड स्कैन करें')}</button>
            <button type="button" className="ectpr-button ectpr-button--secondary" onClick={() => window.location.reload()}>{t('Try again', 'फिर कोशिश करें')}</button>
          </div>
          <IfInDoubtBox lang={lang} />
        </section>
      )}

      {result?.status === 'live' && record && (
        <>
          {result.offline && <p className="ectpr-card-server ectpr-card-server--local-only" role="status">{t('No connection to the Sahara server — showing the copy saved on this phone when the card was issued.', 'Sahara सर्वर से संपर्क नहीं — कार्ड जारी होते समय इस फ़ोन पर सहेजी कॉपी दिखा रहे हैं।')} {formatDay(result.issuedAt, lang)}</p>}
          <ReadAloudButton lang={lang} text={() => edSpokenText(record, edInterpretation(record), lang)} label={t('Read the emergency plan aloud', 'आपातकालीन योजना पढ़कर सुनाएँ')} />
          <EctprEdView record={record} lang={lang} audience="clinician" full={result.full}>
            {result.full && result.sms && <p className={`ectpr-ed-sms${result.sms.sent ? ' is-sent' : ''}`} role="status">{result.sms.message}</p>}
            {!result.full && (
              <form className="ectpr-ed-glass" onSubmit={breakGlass}>
                <h3>{t('Full record (break-glass)', 'पूरा रिकॉर्ड (ब्रेक-ग्लास)')}</h3>
                <p className="ectpr-muted">{t('Opening the full record is logged with your reason, and the primary decision-maker is sent an SMS where SMS is set up.', 'पूरा रिकॉर्ड खोलना आपके कारण के साथ दर्ज होता है, और SMS सेट हो तो मुख्य निर्णयकर्ता को SMS जाता है।')}</p>
                <label htmlFor="glass-reason">{t('Clinical reason', 'क्लिनिकल कारण')}</label>
                <textarea id="glass-reason" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={500} required minLength={BREAK_GLASS_MIN_REASON}
                  placeholder={t('e.g. Unresponsive in ED, need medicines and decision-maker details', 'जैसे: ED में बेहोश, दवाएँ और निर्णयकर्ता की जानकारी चाहिए')} />
                <button type="submit" className="ectpr-button ectpr-button--danger" disabled={busy || reason.trim().length < BREAK_GLASS_MIN_REASON}>
                  {busy ? t('Opening…', 'खुल रहा है…') : t('Open full record', 'पूरा रिकॉर्ड खोलें')}
                </button>
                {glassError && <p className="ectpr-error" role="alert">{glassError}</p>}
              </form>
            )}
          </EctprEdView>
          <p className="ectpr-muted ed-page-foot">{t('Card issued', 'कार्ड जारी')} {formatDay(result.issuedAt, lang)} · {t('This link', 'यह लिंक')}: {token ? edCardUrl(`${token.slice(0, 6)}…`) : ''}</p>
        </>
      )}

      {scanner && <OfflineQrScannerModal lang={lang} actor="ED (offline scan)" onClose={() => setScanner(false)} />}
    </main>
  );
}
