'use client';
// ED Quick View, emergency wallet card, and ECTPR PDF actions — all drawn from the real signed EctprRecord in
// ectpr-store (tech spec §6–8, clinical spec §6). The old EctprQuickView samples stay exported because the store
// seeds its demo patients from them and continuity-prototype still passes them in; the screens below look the
// patient up in the store by `patientId` (or the sample's hospitalId) and never render the sample itself.
import { useEffect, useMemo, useRef, useState } from 'react';
import QRCode from 'qrcode';
import type { CareLang } from './care-i18n';
import { careText } from './care-i18n';
import { getCurrentAmdStatus, subscribeAmd } from './amd-store';
import type { AmdStatusSummary, EctprRecord } from './ectpr-model';
import { edInterpretation } from './ectpr-rules';
import { getActiveEctpr, listEctprVersions, logAudit, subscribeEctpr } from './ectpr-store';
import { EctprEdView, edSpokenText, type EdAudience } from './ectpr-ed-view';
import {
  ceilingShort, cprShort, edBanner, formatDay, notRecorded, textFor, transferShort, treatingConsultant, walletCardLine,
} from './ectpr-labels';
import { edCardUrl, issueEdCard, localCardFor, revokeEdCard, startEdCardSync, subscribeEdCards, syncEdCards, type LocalEdCard } from './ed-card-client';
import { AudioTtsPlayer } from './tts-speech';
import { OfflineQrScannerModal } from './offline-qr-scanner-modal';

// ---------------------------------------------------------------------------
// Legacy sample shape (seed for ectpr-store's demo patients). Synthetic data.

export type BreathingCeiling =
  | 'comfort-only'
  | 'mask'
  | 'non-rebreather'
  | 'hfnc'
  | 'niv'
  | 'intubation-trial'
  | 'intubation-no-limit';

export type EctprQuickView = {
  hospitalId: string;
  patientName: string;
  diagnosis: string;
  goal: 'prioritise-life' | 'balanced' | 'prioritise-comfort' | null;
  hospitalTransfer: 'any-deterioration' | 'listed-reasons-only' | 'no' | null;
  cpr: 'attempt' | 'dnacpr' | null;
  breathingCeiling: BreathingCeiling | null;
  icu: 'yes' | 'time-limited-trial' | 'no' | null;
  icuTrialDays: number | null;
  decisionMaker: { name: string; relationship: string } | null;
  treatingConsultant: string | null;
  palliativeContact: string | null;
  signedBy: string | null;
  signedOn: string | null;
  reviewDueBy: string | null;
  formVersion: string;
  recordVersion: string;
};

export const ectprQuickViews: Record<string, EctprQuickView> = {
  'CANCER-20418': {
    hospitalId: 'CANCER-20418',
    patientName: 'Meera Raghavan',
    diagnosis: 'Stage IV NSCLC',
    goal: null,
    hospitalTransfer: null,
    cpr: null,
    breathingCeiling: null,
    icu: null,
    icuTrialDays: null,
    decisionMaker: { name: 'Kavya Raghavan', relationship: 'Daughter (primary proxy)' },
    treatingConsultant: 'Dr Sujay',
    palliativeContact: 'Palliative care team',
    signedBy: 'Dr Sujay',
    // Signed plan with every choice left blank: demo of "No preference recorded" = standard care everywhere.
    signedOn: '24 Oct 2026',
    reviewDueBy: '16 Jan 2027',
    formVersion: 'ECTPR Beta v0.1',
    recordVersion: 'Version 1',
  },
  'CANCER-20377': {
    hospitalId: 'CANCER-20377',
    patientName: 'Leela Thomas',
    diagnosis: 'Stage IV Ovarian Carcinoma',
    goal: 'balanced',
    hospitalTransfer: 'listed-reasons-only',
    cpr: 'dnacpr',
    breathingCeiling: 'niv',
    icu: 'no',
    icuTrialDays: null,
    decisionMaker: { name: 'Joel Thomas', relationship: 'Son' },
    treatingConsultant: 'Dr Sujay',
    palliativeContact: 'Palliative care team',
    signedBy: 'Dr Sujay',
    signedOn: '21 Oct 2026',
    reviewDueBy: '13 Jan 2027',
    formVersion: 'ECTPR Beta v0.1',
    recordVersion: 'Version 1',
  },
};

// ---------------------------------------------------------------------------
// Store hooks

/** The record the ED should see: the Active version, else the newest version (so revoked/unsigned states show). */
export function useEdRecord(patientId: string | null | undefined): { record: EctprRecord | null; loading: boolean } {
  const [state, setState] = useState<{ id: string | null; record: EctprRecord | null; loaded: boolean }>({ id: null, record: null, loaded: false });
  useEffect(() => {
    if (!patientId) return;
    let live = true;
    const load = async () => {
      const active = await getActiveEctpr(patientId).catch(() => null);
      const record = active ?? (await listEctprVersions(patientId).catch(() => []))[0] ?? null;
      // Audit entries also notify the store; skip re-renders when the record itself did not change.
      if (live) setState((prev) => (prev.id === patientId && prev.loaded && JSON.stringify(prev.record) === JSON.stringify(record) ? prev : { id: patientId, record, loaded: true }));
    };
    void load();
    const off = subscribeEctpr(() => { void load(); });
    return () => { live = false; off(); };
  }, [patientId]);
  if (!patientId) return { record: null, loading: false };
  return state.id === patientId ? { record: state.record, loading: !state.loaded } : { record: null, loading: true };
}

/** The patient's living will status on this device; undefined until it has loaded. */
function useAmdStatus(patientId: string | null | undefined): AmdStatusSummary | null | undefined {
  const [state, setState] = useState<{ id: string; status: AmdStatusSummary | null } | null>(null);
  useEffect(() => {
    if (!patientId) return;
    let live = true;
    const load = () => { void getCurrentAmdStatus(patientId).then((status) => { if (live) setState({ id: patientId, status }); }).catch(() => undefined); };
    load();
    const off = subscribeAmd(load);
    return () => { live = false; off(); };
  }, [patientId]);
  return state && state.id === patientId ? state.status : undefined;
}

function useLocalCard(patientId: string | null | undefined): LocalEdCard | null {
  const [, setTick] = useState(0);
  useEffect(() => {
    startEdCardSync();
    return subscribeEdCards(() => setTick((n) => n + 1));
  }, []);
  return patientId ? localCardFor(patientId) : null;
}

function actorFor(audience: EdAudience | 'patient' | 'family', actor?: string): string {
  if (actor) return actor;
  return audience === 'clinician' ? 'Clinician (Sahara app)' : audience === 'patient' ? 'Patient (Sahara app)' : 'Family (Sahara app)';
}

/** Log one VIEW per record version per mounted screen. */
function useViewAudit(record: EctprRecord | null, actor: string, detail: string) {
  const logged = useRef<string | null>(null);
  useEffect(() => {
    const key = record ? `${record.id}:${record.version}:${record.status}` : null;
    if (!record || logged.current === key) return;
    logged.current = key;
    void logAudit({ actor, action: 'VIEW', recordType: 'ECTPR', recordId: record.id, patientId: record.patientId, detail }).catch(() => undefined);
  }, [record, actor, detail]);
}

// ---------------------------------------------------------------------------
// ED Quick View (in-app)

export function EdQuickView(props: {
  /** Legacy: the sample card; only its hospitalId is used to find the patient in the store. */
  record?: EctprQuickView | null;
  patientId?: string;
  /** Pass a record directly (e.g. a draft being reviewed). undefined = look it up in the store. */
  ectpr?: EctprRecord | null;
  audience: EdAudience;
  lang?: CareLang;
  actor?: string;
}): React.JSX.Element {
  const lang = props.lang ?? 'en';
  const id = props.patientId ?? props.record?.hospitalId ?? null;
  const looked = useEdRecord(props.ectpr === undefined ? id : null);
  const record = props.ectpr !== undefined ? props.ectpr : looked.record;
  const loading = props.ectpr === undefined && looked.loading && !!id;
  const actor = actorFor(props.audience, props.actor);
  useViewAudit(record, actor, 'ED Quick View opened in the app');
  const amd = useAmdStatus(record?.patientId ?? id);
  const spoken = useMemo(() => edSpokenText(record, edInterpretation(record), lang, amd), [record, lang, amd]);

  if (loading) return <div className="ectpr-container"><div className="ectpr-card ectpr-quickview"><p className="ectpr-muted">{careText(lang, 'Loading the emergency care plan…', 'आपातकालीन योजना लोड हो रही है…')}</p></div></div>;
  return (
    <>
      <AudioTtsPlayer title={careText(lang, 'Read the emergency plan aloud', 'आपातकालीन योजना पढ़कर सुनाएँ')} subtitle={careText(lang, 'Plays on this device', 'इसी डिवाइस पर')} text={spoken} variant="emergency" lang={lang} className="mb-3" />
      <EctprEdView record={record} lang={lang} audience={props.audience} amd={amd} />
      {record && props.audience === 'clinician' && <EctprPdfActions record={record} lang={lang} actor={actor} liveUrl={null} />}
    </>
  );
}

// ---------------------------------------------------------------------------
// PDF actions

export function EctprPdfActions(props: { record: EctprRecord; lang?: CareLang; actor: string; liveUrl: string | null; showCard?: boolean }) {
  const lang = props.lang ?? 'en';
  const t = textFor(lang);
  const [busy, setBusy] = useState<'plan' | 'card' | null>(null);
  const [done, setDone] = useState<{ kind: 'plan' | 'card'; sha256: string; fileName: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function make(kind: 'plan' | 'card') {
    setBusy(kind); setError(null);
    try {
      const mod = await import('./ectpr-pdf');
      // The printed plan carries the living will status as it is now on this device (date, version, attestor, L7 caveat).
      const amd = await getCurrentAmdStatus(props.record.patientId).catch(() => props.record.amdAtIssue ?? null);
      const printed: EctprRecord = { ...props.record, amdAtIssue: amd };
      const result = kind === 'plan'
        ? await mod.makeEctprPdf(printed, { lang, liveUrl: props.liveUrl })
        : await mod.makeWalletCardPdf(printed, { lang, liveUrl: props.liveUrl });
      mod.downloadPdf(result);
      setDone({ kind, sha256: result.sha256, fileName: result.fileName });
      await logAudit({ actor: props.actor, action: 'PRINT', recordType: kind === 'plan' ? 'ECTPR' : 'CARD', recordId: props.record.id, patientId: props.record.patientId, detail: `${kind === 'plan' ? 'ECTPR PDF' : 'Wallet card PDF'} v${props.record.version} (${lang}) ${result.fileName} SHA-256 ${result.sha256}` });
    } catch (e) {
      setError(e instanceof Error ? e.message : t('The PDF could not be made.', 'PDF नहीं बन सकी।'));
    } finally { setBusy(null); }
  }

  return (
    <div className="ectpr-pdf-actions">
      <div className="ectpr-actions-row">
        <button type="button" className="ectpr-button ectpr-button--primary" onClick={() => make('plan')} disabled={busy !== null}>
          {busy === 'plan' ? t('Making PDF…', 'PDF बन रही है…') : t('Download ED plan (A4 PDF)', 'ED योजना डाउनलोड (A4 PDF)')}
        </button>
        {props.showCard !== false && (
          <button type="button" className="ectpr-button ectpr-button--secondary" onClick={() => make('card')} disabled={busy !== null}>
            {busy === 'card' ? t('Making PDF…', 'PDF बन रही है…') : t('Download wallet card (PDF)', 'वॉलेट कार्ड डाउनलोड (PDF)')}
          </button>
        )}
      </div>
      {done && <p className="ectpr-sha" role="status">{t('Saved', 'सहेजा')} {done.fileName} · SHA-256 <code>{done.sha256}</code> · {t('logged in the audit log', 'ऑडिट लॉग में दर्ज')}</p>}
      {error && <p className="ectpr-error" role="alert">{error}</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Wallet card (Appendix E)

function CardFace(props: { record: EctprRecord; lang: CareLang; watermark?: boolean; liveQr?: string | null; offlineQr?: string | null; issuedOn?: string | null }) {
  const { record, lang } = props;
  const t = textFor(lang);
  const interp = edInterpretation(record);
  const consultant = treatingConsultant(record);
  const dm = record.decisionMakers.primary;
  return (
    <div className="ectpr-card ectpr-emergency-card">
      {props.watermark && <div className="ectpr-watermark">{t('PREVIEW — NOT ISSUED', 'पूर्वावलोकन — जारी नहीं')}</div>}
      <div className={`ectpr-card-band${interp.state === 'ACTIVE' ? '' : ' ectpr-card-band--invalid'}`}>
        {interp.state === 'ACTIVE' ? t('Emergency care plan', 'आपातकालीन देखभाल योजना') : t('Not a valid plan — give standard care', 'मान्य योजना नहीं — सामान्य इलाज दें')}
      </div>
      <dl className="ectpr-card-body ectpr-card-fields">
        <div><dt>{t('Patient', 'मरीज़')}</dt><dd>{record.patient.name} · UHID {record.patient.uhid}</dd></div>
        <div><dt>{t('Diagnosis', 'निदान')}</dt><dd>{record.clinical.diagnosis || notRecorded(lang)}</dd></div>
        <div><dt>CPR</dt><dd>{cprShort(record, lang)}</dd></div>
        <div><dt>{t('Breathing ceiling', 'साँस सीमा')}</dt><dd>{ceilingShort(record.choices.respCeiling, lang)}</dd></div>
        <div><dt>{t('Hospital transfer', 'अस्पताल ले जाना')}</dt><dd>{transferShort(record.choices.hospitalTransfer, lang)}</dd></div>
        <div><dt>{t('Call first', 'पहले फ़ोन करें')}</dt><dd>{record.careTeam.palliativePhysician || notRecorded(lang)}{record.careTeam.palliativePhone24h ? ` · ${record.careTeam.palliativePhone24h}` : ''}</dd></div>
        <div><dt>{t('Decision-maker', 'निर्णयकर्ता')}</dt><dd>{dm ? `${dm.name} (${dm.relation})${dm.phone24h ? ` · ${dm.phone24h}` : ''}` : notRecorded(lang)}</dd></div>
        <div><dt>{t('Full record', 'पूरा रिकॉर्ड')}</dt><dd>{t('Sahara — scan the QR; full record by break-glass', 'Sahara — QR स्कैन करें; पूरा रिकॉर्ड ब्रेक-ग्लास से')}</dd></div>
        <div><dt>{t('Signed', 'हस्ताक्षर')}</dt><dd>{consultant ? `${consultant.name} · ${formatDay(consultant.signedAt, lang)}` : t('Not signed', 'हस्ताक्षर नहीं')} · {t('version', 'संस्करण')} {record.version}</dd></div>
      </dl>
      {(props.liveQr || props.offlineQr) && (
        <div className="ectpr-card-qr ectpr-card-qr--pair">
          {props.liveQr && (
            <figure>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={props.liveQr} alt={t('QR code: link to the live emergency card', 'QR कोड: लाइव आपातकालीन कार्ड का लिंक')} width={120} height={120} />
              <figcaption>{t('Live card (internet)', 'लाइव कार्ड (इंटरनेट)')}</figcaption>
            </figure>
          )}
          {props.offlineQr && (
            <figure>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={props.offlineQr} alt={t('QR code: offline text summary of this card', 'QR कोड: इस कार्ड का ऑफ़लाइन सारांश')} width={120} height={120} />
              <figcaption>{t('Offline summary (any camera)', 'ऑफ़लाइन सारांश (कोई भी कैमरा)')}</figcaption>
            </figure>
          )}
        </div>
      )}
      {props.issuedOn && <div className="ectpr-card-issued">{t('Issued', 'जारी')} {formatDay(props.issuedOn, lang)} · {t('review due', 'समीक्षा')} {formatDay(record.reviewDue, lang)}</div>}
      <div className="ectpr-card-footer">
        <div className="ectpr-card-rule">{walletCardLine(lang)}</div>
        <div className="ectpr-muted">{t('IF IN DOUBT, RESUSCITATE. What the patient says now overrides this card.', 'संदेह हो तो CPR दें। मरीज़ की अभी की बात इस कार्ड से ऊपर है।')}</div>
      </div>
    </div>
  );
}

function useQr(text: string | null): string | null {
  const [state, setState] = useState<{ text: string; url: string | null } | null>(null);
  useEffect(() => {
    if (!text) return;
    let live = true;
    QRCode.toDataURL(text, { errorCorrectionLevel: 'M', margin: 1, width: 240 })
      .then((url) => { if (live) setState({ text, url }); })
      .catch(() => { if (live) setState({ text, url: null }); });
    return () => { live = false; };
  }, [text]);
  return state && state.text === text ? state.url : null;
}

export function EmergencyCard(props: {
  /** Legacy sample; only its hospitalId is used to find the patient in the store. */
  record?: EctprQuickView | null;
  patientId?: string;
  audience: 'patient' | 'family';
  /** Legacy issued flag from the parent; the card's own token on this device is the source of truth. */
  issuedOn?: string | null;
  onIssue?: () => void;
  lang?: CareLang;
  actor?: string;
}): React.JSX.Element {
  const lang = props.lang ?? 'en';
  const t = textFor(lang);
  const id = props.patientId ?? props.record?.hospitalId ?? null;
  const { record, loading } = useEdRecord(id);
  const card = useLocalCard(id);
  const amdNow = useAmdStatus(id);
  const actor = actorFor(props.audience, props.actor);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [offlineText, setOfflineText] = useState<string | null>(null);
  const interp = edInterpretation(record);
  const current = card && record && !card.revokedAt && card.recordId === record.id && record.status === 'ACTIVE' ? card : null;
  const liveUrl = current ? edCardUrl(current.token) : null;
  const liveQr = useQr(liveUrl);
  const offlineQr = useQr(current ? offlineText : null);
  useViewAudit(current ? record : null, actor, 'Emergency wallet card opened in the app');

  useEffect(() => {
    if (!current) return;
    let live = true;
    import('./ectpr-pdf').then((m) => { if (live) setOfflineText(m.offlineCardText(current.snapshot, lang)); }).catch(() => undefined);
    return () => { live = false; };
  }, [current, lang]);

  if (!id) return <div className="ectpr-container"><div className="ectpr-card ectpr-empty-card">{t('Choose a patient first.', 'पहले मरीज़ चुनें।')}</div></div>;
  if (loading) return <div className="ectpr-container"><div className="ectpr-card ectpr-empty-card">{t('Loading…', 'लोड हो रहा है…')}</div></div>;
  if (!record) {
    return (
      <div className="ectpr-container">
        <div className="ectpr-card ectpr-empty-card">{t('No emergency care plan yet. A card can be made once the treating consultant signs the plan.', 'अभी कोई आपातकालीन योजना नहीं। मुख्य डॉक्टर के हस्ताक्षर के बाद कार्ड बन सकता है।')}</div>
      </div>
    );
  }

  async function issue() {
    if (!record) return;
    setBusy('issue'); setMessage(null);
    try {
      // The AMD itself stays on this device; the card carries its status so a scanning ED doctor can see it.
      const amd = await getCurrentAmdStatus(record.patientId).catch(() => null);
      const made = await issueEdCard({ ...record, amdAtIssue: amd }, actor);
      props.onIssue?.();
      setMessage(made.server === 'issued' ? t('Card issued. Older cards for this patient were withdrawn.', 'कार्ड जारी हुआ। पुराने कार्ड वापस ले लिए गए।') : made.serverMessage ?? null);
    } finally { setBusy(null); }
  }
  async function withdraw() {
    if (!current) return;
    const reason = window.prompt(t('Why are you withdrawing this card?', 'यह कार्ड क्यों वापस ले रहे हैं?'), t('Card lost or replaced', 'कार्ड खो गया या बदला गया'));
    if (!reason?.trim()) return;
    setBusy('withdraw');
    try {
      const after = await revokeEdCard(current.token, reason.trim(), actor);
      setMessage(after?.server === 'revoked' ? t('Card withdrawn. Its QR now shows "card withdrawn".', 'कार्ड वापस लिया गया। अब इसका QR "कार्ड वापस लिया गया" दिखाएगा।') : t('Withdrawn on this device. The server will be told when you are online.', 'इस डिवाइस पर वापस लिया गया। ऑनलाइन होने पर सर्वर को बताया जाएगा।'));
    } finally { setBusy(null); }
  }
  async function retry() {
    setBusy('retry');
    try { await syncEdCards(actor); } finally { setBusy(null); }
  }
  async function share() {
    if (!current || !liveUrl) return;
    const text = t('Sahara emergency card. Scan or open in an emergency.', 'Sahara आपातकालीन कार्ड। आपात स्थिति में खोलें।');
    try {
      if (navigator.share) await navigator.share({ title: 'Sahara emergency card', text, url: liveUrl });
      else { await navigator.clipboard.writeText(liveUrl); setMessage(t('Link copied.', 'लिंक कॉपी हुआ।')); }
      await logAudit({ actor, action: 'SHARE', recordType: 'CARD', recordId: current.recordId, patientId: current.patientId, detail: 'ED card link shared' });
    } catch { /* share sheet closed */ }
  }
  function handlePrint() {
    if (!current) return;
    document.body.classList.add('ectpr-printing');
    const cleanup = () => document.body.classList.remove('ectpr-printing');
    window.addEventListener('afterprint', cleanup, { once: true });
    setTimeout(cleanup, 3000);
    void logAudit({ actor, action: 'PRINT', recordType: 'CARD', recordId: current.recordId, patientId: current.patientId, detail: `Wallet card printed from the browser, version ${current.version}` }).catch(() => undefined);
    window.print();
  }

  if (interp.state !== 'ACTIVE') {
    return (
      <div className="ectpr-container">
        <div className="ectpr-card ectpr-empty-card">
          {card?.revokedAt && <p className="ectpr-ed-withdrawn">{t('CARD WITHDRAWN', 'कार्ड वापस लिया गया')} · {formatDay(card.revokedAt, lang)}</p>}
          <p>{edBanner(interp, record, lang)}</p>
          <p className="ectpr-muted">{interp.state === 'OUT_OF_DATE'
            ? t('The plan must be reviewed and signed again before a new card can be issued.', 'नया कार्ड जारी करने से पहले योजना की समीक्षा और दोबारा हस्ताक्षर ज़रूरी है।')
            : t('A card can be issued only for a signed, in-date plan.', 'कार्ड केवल हस्ताक्षरित, वैध योजना के लिए जारी होता है।')}</p>
        </div>
      </div>
    );
  }

  if (!current) {
    if (props.audience === 'family') {
      return <div className="ectpr-container"><div className="ectpr-card ectpr-empty-card">{t(`${record.patient.name.split(' ')[0] || 'The patient'} has not issued an emergency card on this device yet.`, `${record.patient.name.split(' ')[0] || 'मरीज़'} ने इस डिवाइस पर अभी आपातकालीन कार्ड जारी नहीं किया है।`)}</div></div>;
    }
    return (
      <div className="ectpr-container">
        <CardFace record={record} lang={lang} watermark />
        <button type="button" className="ectpr-button ectpr-button--primary" onClick={issue} disabled={busy !== null}>
          {busy === 'issue' ? t('Issuing…', 'जारी हो रहा है…') : t('Issue my emergency card', 'मेरा आपातकालीन कार्ड जारी करें')}
        </button>
        <p className="ectpr-issue-help">
          {t('Issuing makes a new private link for the QR code (no name or number in the link) and withdraws any older card. Keep one copy at home, one with the family member who travels with you, and one inside the front door for the ambulance crew.', 'जारी करने पर QR के लिए नया निजी लिंक बनता है (लिंक में नाम या नंबर नहीं) और पुराना कार्ड वापस हो जाता है। एक कॉपी घर पर, एक साथ जाने वाले परिजन के पास, और एक दरवाज़े के अंदर एम्बुलेंस टीम के लिए रखें।')}
        </p>
        {message && <p className="ectpr-muted" role="status">{message}</p>}
      </div>
    );
  }

  return (
    <div className="ectpr-container">
      <CardFace record={current.snapshot} lang={lang} liveQr={liveQr} offlineQr={offlineQr} issuedOn={current.issuedAt} />
      <div className={`ectpr-card-server ectpr-card-server--${current.server}`} role="status">
        {current.server === 'issued'
          ? t('Live link is on the Sahara server. A copy is also saved on this device for offline use.', 'लाइव लिंक Sahara सर्वर पर है। ऑफ़लाइन के लिए एक कॉपी इस डिवाइस पर भी है।')
          : <>{t('Saved on this device only:', 'केवल इस डिवाइस पर सहेजा:')} {current.serverMessage ?? ''} <button type="button" className="ectpr-link-button" onClick={retry} disabled={busy !== null}>{t('Try again', 'फिर कोशिश करें')}</button></>}
      </div>
      {amdNow !== undefined && (amdNow?.status ?? null) !== (current.snapshot.amdAtIssue?.status ?? null) || amdNow !== undefined && (amdNow?.version ?? null) !== (current.snapshot.amdAtIssue?.version ?? null) ? (
        <div className="ectpr-card-server ectpr-card-server--local-only" role="status">
          {t('The living will has changed since this card was issued, so the card shows an old status. Issue a new card.', 'यह कार्ड जारी होने के बाद लिविंग विल बदली है, इसलिए कार्ड पुरानी स्थिति दिखाता है। नया कार्ड जारी करें।')}{' '}
          <button type="button" className="ectpr-link-button" onClick={issue} disabled={busy !== null}>{t('Issue a new card', 'नया कार्ड जारी करें')}</button>
        </div>
      ) : null}
      {!liveQr && <p className="ectpr-muted" role="status">{t('Preparing the QR code…', 'QR कोड तैयार हो रहा है…')}</p>}
      <div className="ectpr-actions-row">
        <button type="button" className="ectpr-button ectpr-button--primary" onClick={handlePrint} disabled={!liveQr}>{t('Print card', 'कार्ड प्रिंट करें')}</button>
        <button type="button" className="ectpr-button ectpr-button--secondary" onClick={share}>{t('Share link', 'लिंक भेजें')}</button>
        <button type="button" className="ectpr-button ectpr-button--secondary" onClick={() => setScannerOpen(true)}>{t('Paramedic offline view', 'पैरामेडिक ऑफ़लाइन दृश्य')}</button>
        <button type="button" className="ectpr-button ectpr-button--danger" onClick={withdraw} disabled={busy !== null}>{t('Withdraw card', 'कार्ड वापस लें')}</button>
      </div>
      <EctprPdfActions record={current.snapshot} lang={lang} actor={actor} liveUrl={liveUrl} />
      {message && <p className="ectpr-muted" role="status">{message}</p>}
      <p className="ectpr-issue-help">
        {t('The QR holds only a random code, not the patient’s details. Anyone with the card can open the emergency view; the full record needs a typed reason (break-glass), is logged, and the decision-maker is told by SMS where SMS is set up.', 'QR में केवल एक बेतरतीब कोड है, मरीज़ की जानकारी नहीं। कार्ड वाला कोई भी आपातकालीन दृश्य खोल सकता है; पूरा रिकॉर्ड कारण लिखने (ब्रेक-ग्लास) पर ही खुलता है, दर्ज होता है, और SMS सेट हो तो निर्णयकर्ता को बताया जाता है।')}
      </p>
      {scannerOpen && (
        <OfflineQrScannerModal record={current.snapshot} offlineText={offlineText} lang={lang} actor={actor} onClose={() => setScannerOpen(false)} />
      )}
    </div>
  );
}
