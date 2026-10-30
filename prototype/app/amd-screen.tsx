'use client';
// Living will (AMD) screen: guided Vidhi-template questions → preview → Vidhi-format PDF → record the signed,
// witnessed and attested paper → copies log. Lifecycle DRAFT → GENERATED → EXECUTED (tech spec §6, rules L1–L9).
// The app never signs or validates an AMD, never offers any act to end life, and has no "withdraw" action.

import { useCallback, useEffect, useId, useState, type ReactNode } from 'react';
import { careText, type CareLang } from './care-i18n';
import type { AmdAttestorKind, AmdContent, AmdCopy, AmdCustodian, AmdDhr, AmdExecution, AmdPlaceOfCare, AmdTreatment, AmdTreatmentChoice, AmdVersion, AmdWitness, IdType } from './ectpr-model';
import {
  AMD_BOARD_PATHWAY, AMD_ED_CAVEAT, AMD_ED_CAVEAT_HI, AMD_TREATMENTS, ID_TYPE_LABELS, ageOn, amdContentHash, blocking, currentExecuted, emptyDhr,
  emptyWitness, normaliseName, toLast4, validateAmdContent, validateExecution, workingVersion, latestVersion, type AmdIssue,
} from './amd-rules';
import {
  executeAmd, listAmdVersions, readAmdScan, recordAmdGenerated, revokeAmdRecord, saveAmdDraft, saveAmdScan, startAmdDraft, subscribeAmd,
  updateAmdAbha, updateAmdCopy, updateAmdCustodian,
} from './amd-store';

type Props = { patientId: string; patientName: string; role: 'doctor' | 'patient' | 'family'; lang: CareLang };
type T = (en: string, hi: string) => string;
type PdfLang = 'en' | 'hi' | 'te';

const STEPS: Array<[string, string]> = [
  ['Before you start', 'शुरू करने से पहले'], ['About you', 'आपके बारे में'], ['When it applies', 'यह कब लागू होगा'],
  ['Treatments', 'इलाज'], ['Other wishes', 'अन्य इच्छाएँ'], ['Representatives', 'प्रतिनिधि'], ['Check and print', 'जाँचें और प्रिंट करें'],
];

const STEP_FIELDS: Array<(field: string) => boolean> = [
  () => false,
  (f) => f.startsWith('executant') || f.startsWith('declarations') || f === 'values',
  (f) => f === 'conditions' || f === 'anticipatedConditions',
  (f) => f === 'treatments',
  (f) => f.startsWith('wishes') || f.startsWith('afterDeath'),
  (f) => f.startsWith('dhrs') || f.startsWith('familyPhysician') || f === 'custodianBody',
  () => true,
];

const TREATMENT_TEXT: Record<AmdTreatment, { en: string; hi: string; helpEn: string; helpHi: string }> = {
  CPR: { en: 'CPR (pressing the chest, electric shocks to restart the heart)', hi: 'CPR (छाती दबाना, दिल दोबारा चलाने के लिए बिजली का झटका)', helpEn: 'Used when the heart stops. In very serious illness it rarely works and can break ribs.', helpHi: 'दिल रुकने पर किया जाता है। बहुत गंभीर बीमारी में यह कम ही काम करता है और पसलियाँ टूट सकती हैं।' },
  VENTILATION: { en: 'Breathing machine (ventilator) or other life support', hi: 'साँस की मशीन (वेंटिलेटर) या अन्य जीवन-रक्षक मशीन', helpEn: 'A tube in the throat connected to a machine that breathes for you, usually in the ICU.', helpHi: 'गले में एक नली जो मशीन से जुड़ी होती है और आपके लिए साँस लेती है, आमतौर पर ICU में।' },
  DIALYSIS: { en: 'Dialysis (machine that cleans the blood when kidneys fail)', hi: 'डायलिसिस (गुर्दे फेल होने पर ख़ून साफ़ करने वाली मशीन)', helpEn: 'Usually several hours, a few times a week.', helpHi: 'आमतौर पर हफ़्ते में कुछ बार, कई घंटे।' },
  TUBE_FEEDING: { en: 'Feeding through a tube or drip', hi: 'नली या ड्रिप से खाना देना', helpEn: 'A tube in the nose or stomach, or a drip. The Supreme Court treats this as medical treatment (Harish Rana, 2026). Food and drink by mouth for comfort, mouth care and hand feeding always continue.', helpHi: 'नाक या पेट में नली, या ड्रिप। सुप्रीम कोर्ट इसे चिकित्सा इलाज मानता है (हरीश राणा, 2026)। आराम के लिए मुँह से खाना-पीना, मुँह की देखभाल और हाथ से खिलाना हमेशा जारी रहता है।' },
  IV_FLUIDS_ANTIBIOTICS: { en: 'Drips and medicines such as antibiotics (comfort medicines always continue)', hi: 'ड्रिप और एंटीबायोटिक जैसी दवाएँ (आराम की दवाएँ हमेशा जारी)', helpEn: 'Pain and comfort medicines are never stopped by this choice.', helpHi: 'इस चुनाव से दर्द और आराम की दवाएँ कभी बंद नहीं होतीं।' },
  CHEMOTHERAPY: { en: 'Chemotherapy', hi: 'कीमोथेरेपी', helpEn: 'Medicines that treat cancer.', helpHi: 'कैंसर का इलाज करने वाली दवाएँ।' },
  RADIOTHERAPY: { en: 'Radiotherapy', hi: 'रेडियोथेरेपी', helpEn: 'Radiation treatment for cancer.', helpHi: 'कैंसर के लिए विकिरण इलाज।' },
  SURGERY: { en: 'Surgery (operations), except for comfort', hi: 'सर्जरी (ऑपरेशन), आराम के लिए छोड़कर', helpEn: 'Operations to relieve symptoms or improve quality of life are still allowed.', helpHi: 'लक्षणों से राहत या जीवन की गुणवत्ता के लिए ऑपरेशन फिर भी हो सकते हैं।' },
};

const STATUS_TEXT: Record<AmdVersion['status'], [string, string]> = {
  DRAFT: ['Draft', 'ड्राफ़्ट'], GENERATED: ['Printed, not yet signed', 'प्रिंट हुआ, अभी हस्ताक्षर नहीं'],
  EXECUTED: ['Signed and recorded', 'हस्ताक्षरित और दर्ज'], SUPERSEDED: ['Replaced by a newer version', 'नए संस्करण से बदला गया'], REVOKED: ['Cancelled in writing', 'लिखित रूप में रद्द'],
};

const ID_TYPES = Object.keys(ID_TYPE_LABELS) as IdType[];
const CUSTODIAN_SUGGESTIONS = ['Municipal Corporation', 'Municipality', 'Nagar Panchayat', 'Gram Panchayat', 'Taluk Panchayat Executive Officer (Karnataka, rural)', 'Joint Director of Health Services (Tamil Nadu)'];

const formatDay = (iso?: string | null) => {
  if (!iso) return '';
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : iso;
};

// ---------------------------------------------------------------------------
// Form pieces

function Field({ label, hint, issue, children, id }: { label: string; hint?: string; issue?: AmdIssue[]; children: ReactNode; id: string }) {
  return <div className="amd-field">
    <label htmlFor={id}>{label}</label>
    {children}
    {hint ? <p className="amd-hint" id={`${id}-hint`}>{hint}</p> : null}
    {issue?.map((item) => <p key={item.id + item.field} className={`amd-issue amd-issue-${item.severity}`} role={item.severity === 'block' ? 'alert' : undefined}>{item.message}</p>)}
  </div>;
}

function TextInput({ id, value, onChange, type = 'text', maxLength = 200, multiline = false, inputMode, invalid, placeholder, list }: { id: string; value: string; onChange: (value: string) => void; type?: string; maxLength?: number; multiline?: boolean; inputMode?: 'numeric' | 'tel' | 'email' | 'text'; invalid?: boolean; placeholder?: string; list?: string }) {
  if (multiline) return <textarea id={id} value={value} rows={3} maxLength={maxLength} aria-invalid={invalid || undefined} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />;
  return <input id={id} type={type} value={value} maxLength={maxLength} inputMode={inputMode} aria-invalid={invalid || undefined} placeholder={placeholder} list={list} onChange={(event) => onChange(event.target.value)} />;
}

function Check({ checked, onChange, children, disabled }: { checked: boolean; onChange?: (checked: boolean) => void; children: ReactNode; disabled?: boolean }) {
  return <label className="amd-check"><input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange?.(event.target.checked)} /><span>{children}</span></label>;
}

function IdFields({ idPrefix, t, idType, idLast4, onType, onLast4, issues }: { idPrefix: string; t: T; idType: IdType | null; idLast4: string; onType: (value: IdType | null) => void; onLast4: (value: string) => void; issues?: AmdIssue[] }) {
  return <div className="amd-row">
    <Field id={`${idPrefix}-idtype`} label={t('ID document', 'पहचान पत्र')}>
      <select id={`${idPrefix}-idtype`} value={idType ?? ''} onChange={(event) => onType((event.target.value || null) as IdType | null)}>
        <option value="">{t('Choose', 'चुनें')}</option>
        {ID_TYPES.map((type) => <option key={type} value={type}>{t(ID_TYPE_LABELS[type].en, ID_TYPE_LABELS[type].hi)}</option>)}
      </select>
    </Field>
    <Field id={`${idPrefix}-last4`} label={t('Last 4 digits only', 'केवल आख़िरी 4 अंक')} hint={t('Never type the full number here. Write it by hand on the paper.', 'पूरा नंबर यहाँ कभी न लिखें। उसे काग़ज़ पर हाथ से लिखें।')} issue={issues}>
      <TextInput id={`${idPrefix}-last4`} value={idLast4} maxLength={4} inputMode="numeric" placeholder="1234" onChange={(value) => onLast4(toLast4(value))} />
    </Field>
  </div>;
}

function Callout({ tone = 'info', title, children }: { tone?: 'info' | 'warn' | 'ok' | 'danger'; title?: string; children: ReactNode }) {
  return <div className={`amd-callout amd-callout-${tone}`}>{title ? <strong>{title}</strong> : null}{children}</div>;
}

function BoardPathway({ t }: { t: T }) {
  return <details className="amd-details">
    <summary>{t('What happens if treatment is to be held back or stopped (information only)', 'अगर इलाज रोकना या न शुरू करना हो तो क्या होता है (केवल जानकारी)')}</summary>
    <ul className="amd-pathway">{AMD_BOARD_PATHWAY.map((item) => <li key={item.en}>{t(item.en, item.hi)}</li>)}</ul>
  </details>;
}

function Caveat({ t }: { t: T }) {
  return <Callout tone="warn" title={t('For the emergency doctor', 'आपातकालीन डॉक्टर के लिए')}><p>{t(AMD_ED_CAVEAT, AMD_ED_CAVEAT_HI)}</p></Callout>;
}

// ---------------------------------------------------------------------------
// Screen

export function AmdScreen(props: Props) {
  const { patientId, patientName, role, lang } = props;
  const t: T = useCallback((en, hi) => careText(lang, en, hi), [lang]);
  const [versions, setVersions] = useState<AmdVersion[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const load = () => listAmdVersions(patientId)
      .then((list) => { if (active) setVersions(list); })
      .catch(() => { if (active) setError(careText(lang, 'Saved records could not be opened on this device.', 'सहेजे गए रिकॉर्ड इस डिवाइस पर नहीं खुल सके।')); });
    void load();
    const unsubscribe = subscribeAmd(() => { void load(); });
    return () => { active = false; unsubscribe(); };
  }, [patientId, lang]);

  const actor = role === 'doctor' ? 'doctor' : role === 'family' ? 'family (helping patient)' : 'patient';
  const working = versions ? workingVersion(versions) : null;
  const current = versions ? currentExecuted(versions) : null;
  const latest = versions ? latestVersion(versions) : null;

  async function start() {
    setError('');
    const result = await startAmdDraft(patientId, actor, patientName);
    if (!result.ok) setError(result.issues[0]?.message ?? 'Could not start.');
  }

  return <section className="amd-screen" aria-labelledby="amd-title">
    <header className="amd-head">
      <p className="amd-eyebrow">{t('Advance medical directive', 'अग्रिम चिकित्सा निर्देश')}</p>
      <h1 id="amd-title">{t('Living will', 'लिविंग विल')}</h1>
      <p className="amd-lede">{t(`A living will lets ${patientName} write down, while able to decide, which treatments they do not want if they become very ill and cannot speak, and who should speak for them.`, `लिविंग विल से ${patientName} तब लिख सकते हैं जब वे ख़ुद निर्णय ले सकें, कि बहुत बीमार होने और बोल न पाने पर उन्हें कौन से इलाज नहीं चाहिए, और उनकी ओर से कौन बोलेगा।`)}</p>
    </header>
    {error ? <p className="amd-issue amd-issue-block" role="alert">{error}</p> : null}
    {versions === null ? <p role="status">{t('Loading…', 'लोड हो रहा है…')}</p> : <>
      <StatusCard t={t} versions={versions} current={current} working={working} />
      {role === 'doctor'
        ? <DoctorView t={t} current={current} latest={latest} />
        : <>
          {role === 'family' ? <Callout title={t('You are helping', 'आप मदद कर रहे हैं')}><p>{t(`Only ${patientName} can make and sign a living will, in their own words. You can help them fill it in and read it out to them.`, `केवल ${patientName} ही अपने शब्दों में लिविंग विल बना और हस्ताक्षर कर सकते हैं। आप भरने में और पढ़कर सुनाने में मदद कर सकते हैं।`)}</p></Callout> : null}
          {working?.status === 'DRAFT' ? <AmdWizard key={working.id} t={t} lang={lang} version={working} patientId={patientId} actor={actor} /> : null}
          {working?.status === 'GENERATED' ? <SignAndRecord key={working.id} t={t} version={working} patientId={patientId} actor={actor} onError={setError} /> : null}
          {current ? <ExecutedPanel key={current.id} t={t} version={current} patientId={patientId} actor={actor} hasWorking={Boolean(working)} onNewVersion={start} /> : null}
          {!working && !current ? <section className="amd-card">
            {latest?.status === 'REVOKED' ? <Callout tone="danger" title={t('Cancelled', 'रद्द')}><p>{t(`The living will was cancelled in writing on ${formatDay(latest.revocation?.revokedOn)}. It no longer applies.`, `लिविंग विल ${formatDay(latest.revocation?.revokedOn)} को लिखित रूप में रद्द की गई। यह अब लागू नहीं है।`)}</p></Callout> : null}
            <h2>{latest ? t('Make a new living will', 'नई लिविंग विल बनाएँ') : t('Start a living will', 'लिविंग विल शुरू करें')}</h2>
            <p>{t('It takes about 20 minutes. Your answers stay on this device until you print them.', 'इसमें लगभग 20 मिनट लगते हैं। प्रिंट करने तक आपके जवाब इसी डिवाइस पर रहते हैं।')}</p>
            <button type="button" className="primary-button amd-primary" onClick={start}>{t('Start', 'शुरू करें')}</button>
          </section> : null}
        </>}
      <section className="amd-card amd-info">
        <h2>{t('Good to know', 'जानने योग्य')}</h2>
        <Caveat t={t} />
        <BoardPathway t={t} />
      </section>
    </>}
  </section>;
}

function StatusCard({ t, versions, current, working }: { t: T; versions: AmdVersion[]; current: AmdVersion | null; working: AmdVersion | null }) {
  if (!versions.length) return null;
  const steps: Array<[AmdVersion['status'], string, string]> = [['DRAFT', 'Answer questions', 'सवालों के जवाब'], ['GENERATED', 'Print', 'प्रिंट'], ['EXECUTED', 'Sign, attest, upload', 'हस्ताक्षर, सत्यापन, अपलोड']];
  const focus = working ?? current ?? versions.at(-1)!;
  const reached = focus.status === 'EXECUTED' ? 2 : focus.status === 'GENERATED' ? 1 : 0;
  return <section className="amd-card amd-status" aria-label={t('Living will status', 'लिविंग विल की स्थिति')}>
    <div className="amd-status-line">
      <span className={`amd-chip amd-chip-${current ? 'ok' : 'none'}`}>{current ? t(`Current: version ${current.version}, signed ${formatDay(current.execution?.signedOn)}`, `मौजूदा: संस्करण ${current.version}, हस्ताक्षर ${formatDay(current.execution?.signedOn)}`) : t('No signed living will recorded yet', 'अभी कोई हस्ताक्षरित लिविंग विल दर्ज नहीं')}</span>
      {working ? <span className="amd-chip amd-chip-work">{t(`Version ${working.version}: ${STATUS_TEXT[working.status][0]}`, `संस्करण ${working.version}: ${STATUS_TEXT[working.status][1]}`)}</span> : null}
    </div>
    {focus.status !== 'REVOKED' && focus.status !== 'SUPERSEDED' ? <ol className="amd-life">{steps.map(([status, en, hi], index) => <li key={status} data-state={index < reached || (index === reached && focus.status === 'EXECUTED') ? 'done' : index === reached ? 'current' : 'next'}>{t(en, hi)}</li>)}</ol> : null}
    <details className="amd-details"><summary>{t('All versions', 'सभी संस्करण')}</summary>
      <ul className="amd-history">{[...versions].reverse().map((v) => <li key={v.id}><strong>{t(`Version ${v.version}`, `संस्करण ${v.version}`)}</strong> · {t(...STATUS_TEXT[v.status])} · {formatDay(v.updatedAt)}{v.pdfSha256 ? <> · <code title={v.pdfSha256}>PDF {v.pdfSha256.slice(0, 12)}…</code></> : null}</li>)}</ul>
    </details>
  </section>;
}

// ---------------------------------------------------------------------------
// Wizard (DRAFT only)

function AmdWizard({ t, lang, version, patientId, actor }: { t: T; lang: CareLang; version: AmdVersion; patientId: string; actor: string }) {
  const uid = useId();
  const [content, setContent] = useState<AmdContent>(version.content);
  const [step, setStep] = useState(0);
  const [today] = useState(() => new Date());
  const [shown, setShown] = useState<number[]>([]);
  const [pdfLang, setPdfLang] = useState<PdfLang>(lang === 'te' ? 'te' : lang === 'hi' ? 'hi' : 'en');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const issues = validateAmdContent(content, today);
  const stepIssues = (index: number) => issues.filter((item) => STEP_FIELDS[index](item.field));

  const set = <K extends keyof AmdContent>(key: K, value: AmdContent[K]) => setContent((previous) => ({ ...previous, [key]: value }));
  const setEx = (patch: Partial<AmdContent['executant']>) => setContent((previous) => ({ ...previous, executant: { ...previous.executant, ...patch } }));
  const setDecl = (patch: Partial<AmdContent['declarations']>) => setContent((previous) => ({ ...previous, declarations: { ...previous.declarations, ...patch } }));
  const setWish = (patch: Partial<AmdContent['wishes']>) => setContent((previous) => ({ ...previous, wishes: { ...previous.wishes, ...patch } }));
  const setAfter = (patch: Partial<AmdContent['afterDeath']>) => setContent((previous) => ({ ...previous, afterDeath: { ...previous.afterDeath, ...patch } }));
  const setDhr = (index: number, patch: Partial<AmdDhr>) => setContent((previous) => ({ ...previous, dhrs: previous.dhrs.map((dhr, i) => (i === index ? { ...dhr, ...patch } : dhr)) }));
  const moveDhr = (index: number, delta: number) => setContent((previous) => {
    const next = [...previous.dhrs];
    const [item] = next.splice(index, 1);
    next.splice(index + delta, 0, item);
    return { ...previous, dhrs: next };
  });

  async function persist(note: string) {
    const result = await saveAmdDraft(patientId, version.id, content, actor, note);
    if (!result.ok) throw new Error(result.issues[0]?.message ?? 'Not saved');
  }

  async function go(target: number) {
    setMessage('');
    if (target > step && blocking(stepIssues(step)).length) { setShown((previous) => [...previous, step]); return; }
    try { await persist(`Step "${STEPS[step][0]}" saved`); } catch (cause) { setMessage(cause instanceof Error ? cause.message : String(cause)); return; }
    setStep(target);
    requestAnimationFrame(() => document.getElementById(`${uid}-step`)?.focus());
  }

  async function generate() {
    setMessage('');
    setShown([0, 1, 2, 3, 4, 5, 6]);
    if (blocking(issues).length) { setMessage(t('Some answers need fixing first. They are listed above.', 'पहले कुछ जवाब ठीक करने होंगे। वे ऊपर दिए हैं।')); return; }
    setBusy(true);
    try {
      await persist('Saved before printing');
      const now = new Date();
      const contentSha256 = await amdContentHash({ ...version, content });
      const { downloadAmdPdf } = await import('./amd-pdf');
      const printable: AmdVersion = { ...version, content, status: 'GENERATED', generatedAt: now.toISOString() };
      const { sha256 } = await downloadAmdPdf({ version: printable, lang: pdfLang, contentSha256, printedAt: now });
      const result = await recordAmdGenerated(patientId, version.id, { pdfSha256: sha256, contentSha256, lang: pdfLang }, actor, now);
      if (!result.ok) setMessage(result.issues.map((item) => item.message).join(' '));
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : t('The PDF could not be made. Try again.', 'PDF नहीं बन सका। फिर कोशिश करें।'));
    } finally { setBusy(false); }
  }

  const id = (name: string) => `${uid}-${name}`;
  const age = ageOn(content.executant.dob, today);
  const fieldIssues = (field: string) => (shown.includes(step) || step === 6 ? issues.filter((item) => item.field === field) : field === 'executant.dob' ? issues.filter((item) => item.id === 'L1_ADULT' && item.field === field) : []);

  return <section className="amd-card amd-wizard" aria-labelledby={id('step')}>
    <ol className="amd-steps" aria-label={t('Progress', 'प्रगति')}>{STEPS.map(([en, hi], index) => <li key={en} aria-current={index === step ? 'step' : undefined} data-state={index < step ? 'done' : index === step ? 'current' : 'next'}>
      <button type="button" onClick={() => go(index)} disabled={index > step}><span aria-hidden="true">{index < step ? '✓' : index + 1}</span>{t(en, hi)}</button>
    </li>)}</ol>
    <div className="amd-step-title"><h2 id={id('step')} tabIndex={-1}>{t(...STEPS[step])}</h2><span>{t(`Step ${step + 1} of ${STEPS.length} · Version ${version.version}`, `चरण ${step + 1} / ${STEPS.length} · संस्करण ${version.version}`)}</span></div>

    {step === 0 ? <div className="amd-prose">
      <p>{t('This follows the model living will by the Vidhi Centre for Legal Policy and ACP India, and the Supreme Court’s rules (2018, changed on 24 January 2023).', 'यह विधि सेंटर फ़ॉर लीगल पॉलिसी और ACP इंडिया की मॉडल लिविंग विल, और सुप्रीम कोर्ट के नियमों (2018, 24 जनवरी 2023 को बदले गए) पर आधारित है।')}</p>
      <ul>
        <li>{t('You must be 18 or older and able to understand what you are deciding.', 'आपकी उम्र 18 या उससे ज़्यादा होनी चाहिए और आप समझ सकें कि क्या तय कर रहे हैं।')}</li>
        <li>{t('Nobody may pressure you. You can change or cancel it at any time, in writing.', 'कोई आप पर दबाव नहीं डाल सकता। आप इसे कभी भी, लिखित रूप में, बदल या रद्द कर सकते हैं।')}</li>
        <li>{t('It is only about refusing treatments that would just prolong dying. Comfort care, pain relief and feeding by mouth always continue.', 'यह केवल उन इलाजों को मना करने के बारे में है जो बस मृत्यु की प्रक्रिया लंबी करते। आराम की देखभाल, दर्द से राहत और मुँह से खाना हमेशा जारी रहते हैं।')}</li>
        <li>{t('It cannot ask for anything meant to end life. That is against the law.', 'इसमें जीवन ख़त्म करने के लिए कुछ भी नहीं माँगा जा सकता। यह क़ानून के ख़िलाफ़ है।')}</li>
        <li>{t('Name at least one, better two, people who can speak for you (representatives).', 'कम से कम एक, बेहतर दो, लोगों के नाम लिखें जो आपकी ओर से बोल सकें (प्रतिनिधि)।')}</li>
      </ul>
      <Callout title={t('The app does not make it valid', 'ऐप इसे वैध नहीं बनाता')}><p>{t('Sahara prints the paper. It becomes valid only when you sign it by hand in front of two witnesses and a notary or gazetted officer attests it. Then you upload a photo or scan here.', 'सहारा काग़ज़ प्रिंट करता है। यह तभी वैध होता है जब आप दो गवाहों के सामने हाथ से हस्ताक्षर करें और नोटरी या राजपत्रित अधिकारी इसे सत्यापित करे। फिर आप यहाँ फ़ोटो या स्कैन अपलोड करें।')}</p></Callout>
    </div> : null}

    {step === 1 ? <>
      <Field id={id('name')} label={t('Full name', 'पूरा नाम')} issue={fieldIssues('executant.name')}><TextInput id={id('name')} value={content.executant.name} onChange={(name) => setEx({ name })} /></Field>
      <div className="amd-row">
        <Field id={id('gender')} label={t('Gender', 'लिंग')}><TextInput id={id('gender')} value={content.executant.gender} maxLength={40} onChange={(gender) => setEx({ gender })} /></Field>
        <Field id={id('dob')} label={t('Date of birth', 'जन्म तिथि')} issue={fieldIssues('executant.dob')} hint={age !== null && age >= 18 ? t(`Age ${age}`, `उम्र ${age}`) : undefined}><TextInput id={id('dob')} type="date" value={content.executant.dob} invalid={age !== null && age < 18} onChange={(dob) => setEx({ dob })} /></Field>
      </div>
      <IdFields idPrefix={id('ex')} t={t} idType={content.executant.idType} idLast4={content.executant.idLast4} onType={(idType) => setEx({ idType })} onLast4={(idLast4) => setEx({ idLast4 })} issues={fieldIssues('executant.idLast4')} />
      <Field id={id('perm')} label={t('Permanent address', 'स्थायी पता')} issue={fieldIssues('executant.permanentAddress')}><TextInput id={id('perm')} multiline maxLength={300} value={content.executant.permanentAddress} onChange={(permanentAddress) => setEx({ permanentAddress })} /></Field>
      <Field id={id('cur')} label={t('Current address (if different)', 'मौजूदा पता (अगर अलग हो)')}><TextInput id={id('cur')} multiline maxLength={300} value={content.executant.currentAddress} onChange={(currentAddress) => setEx({ currentAddress })} /></Field>
      <Field id={id('lb')} label={t('Municipality or panchayat', 'नगरपालिका या पंचायत')}><TextInput id={id('lb')} value={content.executant.localBody} onChange={(localBody) => setEx({ localBody })} /></Field>
      <Field id={id('values')} label={t('Why you are making this, and what matters to you (optional)', 'आप यह क्यों बना रहे हैं, और आपके लिए क्या ज़रूरी है (वैकल्पिक)')} issue={fieldIssues('values')}><TextInput id={id('values')} multiline maxLength={1500} value={content.values} onChange={(values) => set('values', values)} /></Field>
      <fieldset className="amd-fieldset"><legend>{t('Please confirm', 'कृपया पुष्टि करें')}</legend>
        <Check checked={content.declarations.capacity} onChange={(capacity) => setDecl({ capacity })}>{t('I understand what this document means and what can happen because of it.', 'मैं समझता/समझती हूँ कि इस दस्तावेज़ का क्या मतलब है और इससे क्या हो सकता है।')}</Check>
        <Check checked={content.declarations.consideredCarefully} onChange={(consideredCarefully) => setDecl({ consideredCarefully })}>{t('I have thought about it carefully.', 'मैंने इस पर ध्यान से सोचा है।')}</Check>
        <Check checked={content.declarations.voluntary} onChange={(voluntary) => setDecl({ voluntary })}>{t('I am doing this by my own free will. Nobody is forcing or pressuring me.', 'मैं यह अपनी मर्ज़ी से कर रहा/रही हूँ। कोई मुझ पर ज़ोर या दबाव नहीं डाल रहा।')}</Check>
        <Check checked={content.declarations.understandsNotAppliedIfUnclear} onChange={(understandsNotAppliedIfUnclear) => setDecl({ understandsNotAppliedIfUnclear })}>{t('I understand doctors will not use it if the situation is unclear or one I did not think of.', 'मैं समझता/समझती हूँ कि स्थिति साफ़ न हो या मैंने उसके बारे में न सोचा हो, तो डॉक्टर इसका उपयोग नहीं करेंगे।')}</Check>
        <Check checked={content.executant.readOutToMe} onChange={(readOutToMe) => setEx({ readOutToMe })}>{t('I cannot read or write. Someone wrote this as I said and read it out to me.', 'मैं पढ़-लिख नहीं सकता/सकती। किसी ने इसे मेरे कहे अनुसार लिखा और मुझे पढ़कर सुनाया।')}</Check>
        <Check checked={content.declarations.capacityCertificateAttached} onChange={(capacityCertificateAttached) => setDecl({ capacityCertificateAttached })}>{t('A doctor’s certificate that I can decide is attached (optional; not required).', 'डॉक्टर का प्रमाणपत्र कि मैं निर्णय ले सकता/सकती हूँ, साथ लगा है (वैकल्पिक; ज़रूरी नहीं)।')}</Check>
        {['declarations.capacity', 'declarations.consideredCarefully', 'declarations.voluntary', 'declarations.understandsNotAppliedIfUnclear'].flatMap(fieldIssues).map((item) => <p key={item.id} className="amd-issue amd-issue-block" role="alert">{item.message}</p>)}
      </fieldset>
    </> : null}

    {step === 2 ? <>
      <p className="amd-prose">{t('It applies only when your doctors decide there is no reasonable chance you will recover, and more treatment would only make dying longer. Choose the situations:', 'यह तभी लागू होगा जब आपके डॉक्टर तय करें कि ठीक होने की कोई उचित संभावना नहीं है, और आगे का इलाज बस मृत्यु को लंबा करेगा। स्थितियाँ चुनें:')}</p>
      <fieldset className="amd-fieldset"><legend>{t('When should it apply?', 'यह कब लागू हो?')}</legend>
        {([['TERMINAL', 'A terminal illness (an illness that will end my life)', 'अंतिम अवस्था की बीमारी (जो मेरा जीवन ख़त्म करेगी)'], ['END_STAGE', 'An end-stage condition (lasting damage that keeps getting worse, where treatment will not work)', 'अंतिम चरण की स्थिति (स्थायी नुक़सान जो बढ़ता जाए, जहाँ इलाज काम न करे)'], ['VEGETATIVE', 'A vegetative state (awake but not aware, for many months)', 'वेजिटेटिव अवस्था (जागे हुए पर कई महीनों तक होश नहीं)']] as const).map(([value, en, hi]) => <Check key={value} checked={content.conditions.includes(value)} onChange={(on) => set('conditions', on ? [...content.conditions, value] : content.conditions.filter((c) => c !== value))}>{t(en, hi)}</Check>)}
        {fieldIssues('conditions').map((item) => <p key={item.id} className="amd-issue amd-issue-block" role="alert">{item.message}</p>)}
      </fieldset>
      <Field id={id('anticipated')} label={t('Any illness you expect, and what you want for it (optional)', 'कोई बीमारी जिसकी आप उम्मीद करते हैं, और उसके लिए आप क्या चाहते हैं (वैकल्पिक)')} issue={fieldIssues('anticipatedConditions')}><TextInput id={id('anticipated')} multiline maxLength={1000} value={content.anticipatedConditions} onChange={(value) => set('anticipatedConditions', value)} /></Field>
    </> : null}

    {step === 3 ? <>
      <p className="amd-prose">{t('For each treatment, choose what you want in those situations. You can leave any as “Not decided”.', 'हर इलाज के लिए चुनें कि उन स्थितियों में आप क्या चाहते हैं। किसी को भी “तय नहीं” छोड़ सकते हैं।')}</p>
      {AMD_TREATMENTS.map((treatment) => {
        const text = TREATMENT_TEXT[treatment];
        const choice = content.treatments[treatment];
        const options: Array<[AmdTreatmentChoice, string, string]> = [['REFUSE', 'I do not want', 'मुझे नहीं चाहिए'], ['ACCEPT', 'I want', 'मुझे चाहिए'], [null, 'Not decided', 'तय नहीं']];
        return <fieldset key={treatment} className="amd-treatment"><legend>{t(text.en, text.hi)}</legend>
          <p className="amd-hint">{t(text.helpEn, text.helpHi)}</p>
          <div className="amd-segment">{options.map(([value, en, hi]) => <label key={String(value)} data-on={choice === value || undefined}><input type="radio" name={`${uid}-${treatment}`} checked={choice === value} onChange={() => set('treatments', { ...content.treatments, [treatment]: value })} />{t(en, hi)}</label>)}</div>
        </fieldset>;
      })}
      <Check checked disabled>{t('Comfort care, pain relief, mouth care and feeding by mouth always continue. This cannot be changed.', 'आराम की देखभाल, दर्द से राहत, मुँह की देखभाल और मुँह से खिलाना हमेशा जारी रहते हैं। इसे बदला नहीं जा सकता।')}</Check>
      {fieldIssues('treatments').map((item) => <p key={item.id} className={`amd-issue amd-issue-${item.severity}`}>{item.message}</p>)}
    </> : null}

    {step === 4 ? <>
      <p className="amd-prose">{t('All of this page is optional.', 'यह पूरा पेज वैकल्पिक है।')}</p>
      <fieldset className="amd-fieldset"><legend>{t('Where would you like to be cared for at the end?', 'अंत में आप कहाँ देखभाल चाहेंगे?')}</legend>
        {([[null, 'No preference', 'कोई पसंद नहीं'], ['HOME', 'At home', 'घर पर'], ['HOSPITAL', 'In a hospital', 'अस्पताल में'], ['HOSPICE', 'In a hospice', 'हॉस्पिस में'], ['NAMED_INSTITUTION', 'A place I name', 'मेरी बताई जगह']] as Array<[AmdPlaceOfCare | null, string, string]>).map(([value, en, hi]) => <label key={String(value)} className="amd-check"><input type="radio" name={`${uid}-place`} checked={content.wishes.placeOfCare === value} onChange={() => setWish({ placeOfCare: value })} /><span>{t(en, hi)}</span></label>)}
        {content.wishes.placeOfCare === 'NAMED_INSTITUTION' ? <Field id={id('inst')} label={t('Name of the place', 'जगह का नाम')}><TextInput id={id('inst')} value={content.wishes.institution} onChange={(institution) => setWish({ institution })} /></Field> : null}
      </fieldset>
      <Field id={id('phys')} label={t('Doctor you would like to look after you (name, place)', 'डॉक्टर जिनसे आप देखभाल चाहेंगे (नाम, जगह)')}><TextInput id={id('phys')} value={content.wishes.physician} onChange={(physician) => setWish({ physician })} /></Field>
      <Field id={id('focus')} label={t('Anything doctors should pay special attention to', 'कुछ जिस पर डॉक्टर ख़ास ध्यान दें')} issue={fieldIssues('wishes.focus')}><TextInput id={id('focus')} multiline maxLength={600} value={content.wishes.focus} placeholder={t('For example: easing worry and confusion so I can spend time with family', 'जैसे: चिंता और उलझन कम करना ताकि मैं परिवार के साथ समय बिता सकूँ')} onChange={(focus) => setWish({ focus })} /></Field>
      <div className="amd-row">
        <Field id={id('pname')} label={t('Name you want to be called', 'जिस नाम से आप बुलाए जाना चाहते हैं')}><TextInput id={id('pname')} value={content.wishes.preferredName} onChange={(preferredName) => setWish({ preferredName })} /></Field>
        <Field id={id('pron')} label={t('Gender or pronouns to respect', 'सम्मान किए जाने वाला लिंग या सर्वनाम')}><TextInput id={id('pron')} value={content.wishes.genderPronouns} onChange={(genderPronouns) => setWish({ genderPronouns })} /></Field>
      </div>
      <div className="amd-row">
        <Field id={id('partner')} label={t('Partner’s name', 'साथी का नाम')}><TextInput id={id('partner')} value={content.wishes.partnerName} onChange={(partnerName) => setWish({ partnerName })} /></Field>
        <Field id={id('pterm')} label={t('How staff should refer to them', 'स्टाफ़ उन्हें किस तरह संबोधित करे')}><TextInput id={id('pterm')} value={content.wishes.partnerTerm} placeholder={t('my partner', 'मेरा साथी')} onChange={(partnerTerm) => setWish({ partnerTerm })} /></Field>
      </div>
      <fieldset className="amd-fieldset"><legend>{t('After death', 'मृत्यु के बाद')}</legend>
        <Check checked={content.afterDeath.organDonor} onChange={(organDonor) => setAfter({ organDonor })}>{t('I am a registered organ donor (Form 7, THOTA 1994).', 'मैं पंजीकृत अंगदाता हूँ (फ़ॉर्म 7, THOTA 1994)।')}</Check>
        <Check checked={content.afterDeath.bodyDonation} onChange={(bodyDonation) => setAfter({ bodyDonation })}>{t('I want to donate my body for medical study.', 'मैं चिकित्सा अध्ययन के लिए अपना शरीर दान करना चाहता/चाहती हूँ।')}</Check>
        {content.afterDeath.bodyDonation ? <Field id={id('bodyinst')} label={t('Medical college or organisation', 'मेडिकल कॉलेज या संस्था')}><TextInput id={id('bodyinst')} value={content.afterDeath.bodyDonationInstitution} onChange={(bodyDonationInstitution) => setAfter({ bodyDonationInstitution })} /></Field> : null}
        <Field id={id('rites')} label={t('Wishes about last rites', 'अंतिम संस्कार के बारे में इच्छाएँ')} issue={fieldIssues('afterDeath.lastRites')}><TextInput id={id('rites')} multiline maxLength={500} value={content.afterDeath.lastRites} onChange={(lastRites) => setAfter({ lastRites })} /></Field>
      </fieldset>
    </> : null}

    {step === 5 ? <>
      <p className="amd-prose">{t('Representatives (DHRs) speak for you when you cannot. Put them in order: the first is asked first. Talk to them before you name them.', 'प्रतिनिधि (DHR) तब आपकी ओर से बोलते हैं जब आप नहीं बोल सकते। उन्हें क्रम में रखें: पहले वाले से पहले पूछा जाता है। नाम लिखने से पहले उनसे बात करें।')}</p>
      {[...fieldIssues('dhrs')].map((item) => <p key={item.id} className={`amd-issue amd-issue-${item.severity}`} role={item.severity === 'block' ? 'alert' : undefined}>{item.message}</p>)}
      {content.dhrs.map((dhr, index) => {
        const dhrIssues = (name: string) => fieldIssues(`dhrs.${index}.${name}`);
        return <fieldset key={dhr.id} className="amd-dhr"><legend>{index === 0 ? t('1. First representative', '1. पहले प्रतिनिधि') : t(`${index + 1}. If the one before cannot be reached`, `${index + 1}. अगर पिछले से संपर्क न हो`)}</legend>
          <div className="amd-dhr-tools">
            <button type="button" className="secondary-button" disabled={index === 0} onClick={() => moveDhr(index, -1)} aria-label={t('Move up', 'ऊपर करें')}>↑</button>
            <button type="button" className="secondary-button" disabled={index === content.dhrs.length - 1} onClick={() => moveDhr(index, 1)} aria-label={t('Move down', 'नीचे करें')}>↓</button>
            <button type="button" className="secondary-button" disabled={content.dhrs.length === 1} onClick={() => set('dhrs', content.dhrs.filter((_, i) => i !== index))}>{t('Remove', 'हटाएँ')}</button>
          </div>
          <div className="amd-row">
            <Field id={id(`d${index}name`)} label={t('Full name', 'पूरा नाम')} issue={dhrIssues('name')}><TextInput id={id(`d${index}name`)} value={dhr.name} onChange={(name) => setDhr(index, { name })} /></Field>
            <Field id={id(`d${index}rel`)} label={t('How they are related to you', 'वे आपके क्या लगते हैं')} issue={dhrIssues('relation')}><TextInput id={id(`d${index}rel`)} value={dhr.relation} maxLength={60} onChange={(relation) => setDhr(index, { relation })} /></Field>
          </div>
          <div className="amd-row">
            <Field id={id(`d${index}phone`)} label={t('Mobile number', 'मोबाइल नंबर')} issue={dhrIssues('phone')}><TextInput id={id(`d${index}phone`)} type="tel" inputMode="tel" maxLength={20} value={dhr.phone} onChange={(phone) => setDhr(index, { phone })} /></Field>
            <Field id={id(`d${index}alt`)} label={t('Other number (optional)', 'दूसरा नंबर (वैकल्पिक)')}><TextInput id={id(`d${index}alt`)} type="tel" inputMode="tel" maxLength={20} value={dhr.phoneAlt} onChange={(phoneAlt) => setDhr(index, { phoneAlt })} /></Field>
          </div>
          <div className="amd-row">
            <Field id={id(`d${index}dob`)} label={t('Date of birth (optional)', 'जन्म तिथि (वैकल्पिक)')} issue={dhrIssues('dob')}><TextInput id={id(`d${index}dob`)} type="date" value={dhr.dob} onChange={(dob) => setDhr(index, { dob })} /></Field>
            <Field id={id(`d${index}email`)} label={t('Email (optional)', 'ईमेल (वैकल्पिक)')}><TextInput id={id(`d${index}email`)} type="email" inputMode="email" value={dhr.email} onChange={(email) => setDhr(index, { email })} /></Field>
          </div>
          <IdFields idPrefix={id(`d${index}`)} t={t} idType={dhr.idType} idLast4={dhr.idLast4} onType={(idType) => setDhr(index, { idType })} onLast4={(idLast4) => setDhr(index, { idLast4 })} issues={dhrIssues('idLast4')} />
          <Field id={id(`d${index}addr`)} label={t('Address', 'पता')}><TextInput id={id(`d${index}addr`)} multiline maxLength={300} value={dhr.permanentAddress} onChange={(permanentAddress) => setDhr(index, { permanentAddress })} /></Field>
          <Check checked={dhr.agreed} onChange={(agreed) => setDhr(index, { agreed })}>{t('I have talked to them and they agreed to be my representative.', 'मैंने उनसे बात की है और वे मेरे प्रतिनिधि बनने के लिए सहमत हैं।')}</Check>
          {dhrIssues('agreed').map((item) => <p key={item.id} className="amd-issue amd-issue-warn">{item.message}</p>)}
        </fieldset>;
      })}
      {content.dhrs.length < 4 ? <button type="button" className="secondary-button" onClick={() => set('dhrs', [...content.dhrs, emptyDhr()])}>{t('+ Add another representative', '+ एक और प्रतिनिधि जोड़ें')}</button> : null}
      {content.dhrs.filter((dhr) => dhr.name.trim()).length > 1 ? <Check checked={content.dhrsActJointly} onChange={(dhrsActJointly) => set('dhrsActJointly', dhrsActJointly)}>{t('They must decide together (instead of in order).', 'उन्हें साथ मिलकर तय करना होगा (क्रम के बजाय)।')}</Check> : null}
      <fieldset className="amd-fieldset"><legend>{t('Family doctor (optional)', 'पारिवारिक डॉक्टर (वैकल्पिक)')}</legend>
        <div className="amd-row">
          <Field id={id('fpname')} label={t('Name', 'नाम')}><TextInput id={id('fpname')} value={content.familyPhysician.name} onChange={(name) => set('familyPhysician', { ...content.familyPhysician, name })} /></Field>
          <Field id={id('fpreg')} label={t('Registration number', 'पंजीकरण नंबर')}><TextInput id={id('fpreg')} value={content.familyPhysician.registrationNo} maxLength={40} onChange={(registrationNo) => set('familyPhysician', { ...content.familyPhysician, registrationNo })} /></Field>
        </div>
        <Field id={id('fpphone')} label={t('Phone', 'फ़ोन')}><TextInput id={id('fpphone')} type="tel" inputMode="tel" maxLength={20} value={content.familyPhysician.phone} onChange={(phone) => set('familyPhysician', { ...content.familyPhysician, phone })} /></Field>
      </fieldset>
      <Field id={id('cust')} label={t('Local body that keeps living wills (custodian)', 'स्थानीय निकाय जो लिविंग विल रखता है (संरक्षक)')} issue={fieldIssues('custodianBody')} hint={t('For example your municipal corporation or gram panchayat. Maharashtra and Karnataka (rural) have named custodians; Andhra Pradesh has not confirmed one yet. You can still sign and record “custodian pending”.', 'जैसे आपका नगर निगम या ग्राम पंचायत। महाराष्ट्र और कर्नाटक (ग्रामीण) ने संरक्षक तय किए हैं; आंध्र प्रदेश ने अभी पुष्टि नहीं की है। आप फिर भी हस्ताक्षर करके “संरक्षक बाक़ी” दर्ज कर सकते हैं।')}>
        <TextInput id={id('cust')} value={content.custodianBody} list={id('custlist')} onChange={(value) => set('custodianBody', value)} />
        <datalist id={id('custlist')}>{CUSTODIAN_SUGGESTIONS.map((value) => <option key={value} value={value} />)}</datalist>
      </Field>
    </> : null}

    {step === 6 ? <Preview t={t} content={content} issues={issues} pdfLang={pdfLang} setPdfLang={setPdfLang} /> : null}

    {message ? <p className="amd-issue amd-issue-block" role="alert">{message}</p> : null}
    {shown.includes(step) && step < 6 && blocking(stepIssues(step)).length ? <p className="amd-issue amd-issue-block" role="alert">{t('Please fix the items marked above to continue.', 'आगे बढ़ने के लिए ऊपर चिह्नित बातें ठीक करें।')}</p> : null}
    <div className="amd-actions">
      {step > 0 ? <button type="button" className="secondary-button" onClick={() => go(step - 1)}>{t('Back', 'पीछे')}</button> : <span />}
      {step < 6
        ? <button type="button" className="primary-button amd-primary" onClick={() => go(step + 1)}>{t('Continue', 'आगे')} →</button>
        : <button type="button" className="primary-button amd-primary" disabled={busy} onClick={generate}>{busy ? t('Making the PDF…', 'PDF बन रहा है…') : t('Make the PDF to print and sign', 'प्रिंट और हस्ताक्षर के लिए PDF बनाएँ')}</button>}
    </div>
  </section>;
}

function Preview({ t, content, issues, pdfLang, setPdfLang }: { t: T; content: AmdContent; issues: AmdIssue[]; pdfLang: PdfLang; setPdfLang: (value: PdfLang) => void }) {
  const refused = AMD_TREATMENTS.filter((tr) => content.treatments[tr] === 'REFUSE');
  const accepted = AMD_TREATMENTS.filter((tr) => content.treatments[tr] === 'ACCEPT');
  const named = content.dhrs.filter((dhr) => dhr.name.trim());
  const conditions = { TERMINAL: t('terminal illness', 'अंतिम अवस्था की बीमारी'), END_STAGE: t('end-stage condition', 'अंतिम चरण की स्थिति'), VEGETATIVE: t('vegetative state', 'वेजिटेटिव अवस्था') };
  const none = t('Not filled', 'नहीं भरा');
  return <>
    {issues.length ? <div className="amd-issue-list">{issues.map((item) => <p key={item.id + item.field} className={`amd-issue amd-issue-${item.severity}`}>{item.message}</p>)}</div> : <Callout tone="ok"><p>{t('Everything needed is filled in.', 'सभी ज़रूरी बातें भरी हैं।')}</p></Callout>}
    <dl className="amd-summary">
      <div><dt>{t('Made by', 'बनाने वाले')}</dt><dd>{content.executant.name || none}{content.executant.dob ? ` · ${formatDay(content.executant.dob)}` : ''}</dd></div>
      <div><dt>{t('Applies to', 'लागू होगा')}</dt><dd>{content.conditions.map((c) => conditions[c]).join(', ') || none}</dd></div>
      <div><dt>{t('I do not want', 'मुझे नहीं चाहिए')}</dt><dd>{refused.map((tr) => t(TREATMENT_TEXT[tr].en, TREATMENT_TEXT[tr].hi)).join('; ') || '—'}</dd></div>
      <div><dt>{t('I want', 'मुझे चाहिए')}</dt><dd>{accepted.map((tr) => t(TREATMENT_TEXT[tr].en, TREATMENT_TEXT[tr].hi)).join('; ') || '—'}</dd></div>
      <div><dt>{t('Always continues', 'हमेशा जारी')}</dt><dd>{t('Comfort care, pain relief, mouth care, feeding by mouth', 'आराम की देखभाल, दर्द से राहत, मुँह की देखभाल, मुँह से खिलाना')}</dd></div>
      <div><dt>{t('Representatives, in order', 'प्रतिनिधि, क्रम से')}</dt><dd>{named.map((dhr, i) => `${i + 1}. ${dhr.name} (${dhr.relation})`).join(' · ') || none}{content.dhrsActJointly ? ` · ${t('decide together', 'साथ मिलकर तय करें')}` : ''}</dd></div>
      <div><dt>{t('Copies go to', 'प्रतियाँ जाएँगी')}</dt><dd>{[...named.map((dhr) => dhr.name), content.familyPhysician.name, content.custodianBody || t('local-body custodian', 'स्थानीय निकाय संरक्षक')].filter(Boolean).join(', ')}</dd></div>
    </dl>
    <fieldset className="amd-fieldset"><legend>{t('PDF headings', 'PDF शीर्षक')}</legend>
      <p className="amd-hint">{t('The legal text is in English, like the Vidhi model. You can add Hindi or Telugu headings to help reading.', 'क़ानूनी पाठ विधि मॉडल की तरह अंग्रेज़ी में है। पढ़ने में मदद के लिए हिंदी या तेलुगु शीर्षक जोड़ सकते हैं।')}</p>
      {([['en', 'English only', 'केवल अंग्रेज़ी'], ['hi', 'English with Hindi headings', 'अंग्रेज़ी, हिंदी शीर्षकों के साथ'], ['te', 'English with Telugu headings (తెలుగు)', 'अंग्रेज़ी, तेलुगु शीर्षकों के साथ (తెలుగు)']] as const).map(([value, en, hi]) => <label key={value} className="amd-check"><input type="radio" name="amd-pdf-lang" checked={pdfLang === value} onChange={() => setPdfLang(value)} /><span>{t(en, hi)}</span></label>)}
    </fieldset>
    <Callout title={t('After printing', 'प्रिंट करने के बाद')}><p>{t('Once printed, this version cannot be changed in the app. To change anything, start a new version and print again.', 'प्रिंट होने के बाद यह संस्करण ऐप में बदला नहीं जा सकता। कुछ भी बदलने के लिए नया संस्करण शुरू करें और फिर प्रिंट करें।')}</p></Callout>
  </>;
}

// ---------------------------------------------------------------------------
// GENERATED: record the signed, witnessed, attested paper (L3, L5)

function WitnessFields({ t, index, value, onChange, dhrNames, uid, issues }: { t: T; index: number; value: AmdWitness; onChange: (value: AmdWitness) => void; dhrNames: string[]; uid: string; issues: AmdIssue[] }) {
  const isDhr = value.name.trim() && dhrNames.includes(normaliseName(value.name));
  const id = (name: string) => `${uid}-w${index}-${name}`;
  return <fieldset className="amd-dhr"><legend>{t(`Witness ${index + 1}`, `गवाह ${index + 1}`)}</legend>
    <div className="amd-row">
      <Field id={id('name')} label={t('Full name', 'पूरा नाम')} issue={issues.filter((item) => item.field === `execution.witnesses.${index}.name`)}><TextInput id={id('name')} value={value.name} onChange={(name) => onChange({ ...value, name })} /></Field>
      <Field id={id('phone')} label={t('Mobile', 'मोबाइल')}><TextInput id={id('phone')} type="tel" inputMode="tel" maxLength={20} value={value.phone} onChange={(phone) => onChange({ ...value, phone })} /></Field>
    </div>
    {isDhr ? <p className="amd-issue amd-issue-warn">{t('This witness is also a named representative. Witnesses should preferably be independent.', 'यह गवाह एक नामित प्रतिनिधि भी है। गवाह हो सके तो स्वतंत्र होने चाहिए।')}</p> : null}
    <Field id={id('addr')} label={t('Address', 'पता')}><TextInput id={id('addr')} value={value.address} maxLength={300} onChange={(address) => onChange({ ...value, address })} /></Field>
    <IdFields idPrefix={id('id')} t={t} idType={value.idType} idLast4={value.idLast4} onType={(idType) => onChange({ ...value, idType })} onLast4={(idLast4) => onChange({ ...value, idLast4 })} />
  </fieldset>;
}

function CustodianFields({ t, value, onChange, uid, defaultBody }: { t: T; value: AmdCustodian | null; onChange: (value: AmdCustodian) => void; uid: string; defaultBody: string }) {
  const current = value ?? { status: 'PENDING' as const, body: defaultBody, officer: '', acknowledgedOn: '' };
  return <fieldset className="amd-fieldset"><legend>{t('Custodian (local body)', 'संरक्षक (स्थानीय निकाय)')}</legend>
    <label className="amd-check"><input type="radio" name={`${uid}-cust`} checked={value?.status === 'ACKNOWLEDGED'} onChange={() => onChange({ ...current, status: 'ACKNOWLEDGED' })} /><span>{t('The custodian received a copy and acknowledged it', 'संरक्षक को प्रति मिली और उन्होंने पावती दी')}</span></label>
    <label className="amd-check"><input type="radio" name={`${uid}-cust`} checked={value?.status === 'PENDING'} onChange={() => onChange({ ...current, status: 'PENDING' })} /><span>{t('Custodian pending (none appointed yet, or no reply)', 'संरक्षक बाक़ी (अभी नियुक्त नहीं, या जवाब नहीं)')}</span></label>
    {value?.status === 'ACKNOWLEDGED' ? <div className="amd-row">
      <Field id={`${uid}-cust-off`} label={t('Officer’s name and office', 'अधिकारी का नाम और कार्यालय')}><TextInput id={`${uid}-cust-off`} value={current.officer} onChange={(officer) => onChange({ ...current, officer })} /></Field>
      <Field id={`${uid}-cust-on`} label={t('Date acknowledged', 'पावती की तारीख़')}><TextInput id={`${uid}-cust-on`} type="date" value={current.acknowledgedOn} onChange={(acknowledgedOn) => onChange({ ...current, acknowledgedOn })} /></Field>
    </div> : null}
    {value?.status === 'PENDING' ? <p className="amd-hint">{t('Tip from the Vidhi toolkit: write to the head of your local body (Commissioner, Sarpanch or President), citing para 198.3.6 of the Supreme Court order of 24 January 2023, and ask them to name a custodian. Attach a copy.', 'विधि टूलकिट की सलाह: अपने स्थानीय निकाय के प्रमुख (आयुक्त, सरपंच या अध्यक्ष) को पत्र लिखें, 24 जनवरी 2023 के सुप्रीम कोर्ट आदेश के पैरा 198.3.6 का हवाला दें, और संरक्षक नियुक्त करने को कहें। एक प्रति साथ लगाएँ।')}</p> : null}
  </fieldset>;
}

function SignAndRecord({ t, version, patientId, actor, onError }: { t: T; version: AmdVersion; patientId: string; actor: string; onError: (message: string) => void }) {
  const uid = useId();
  const [today] = useState(() => new Date());
  const [execution, setExecution] = useState<AmdExecution>({ signedOn: '', place: '', thumbImpression: false, witnesses: [emptyWitness(), emptyWitness()], attestor: { kind: null, name: '', designation: '', registrationNo: '' }, scanKey: '', scanName: '', scanType: '', scanSha256: '', scanUploadedAt: '' });
  const [custodian, setCustodian] = useState<AmdCustodian | null>(null);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState('');
  const dhrNames = version.content.dhrs.map((dhr) => normaliseName(dhr.name)).filter(Boolean);
  const issues = validateExecution(version, execution, custodian, today);
  const show = tried ? issues : issues.filter((item) => item.severity !== 'block');
  const setWitness = (index: 0 | 1, value: AmdWitness) => setExecution((previous) => ({ ...previous, witnesses: (index === 0 ? [value, previous.witnesses[1]] : [previous.witnesses[0], value]) as [AmdWitness, AmdWitness] }));
  const setAttestor = (patch: Partial<AmdExecution['attestor']>) => setExecution((previous) => ({ ...previous, attestor: { ...previous.attestor, ...patch } }));

  async function reprint() {
    setBusy('print');
    try {
      const { downloadAmdPdf } = await import('./amd-pdf');
      const lang = version.pdfLang ?? 'en';
      const contentSha256 = version.contentSha256 ?? await amdContentHash(version);
      const { sha256 } = await downloadAmdPdf({ version, lang, contentSha256, printedAt: new Date(version.generatedAt ?? Date.now()) });
      await recordAmdGenerated(patientId, version.id, { pdfSha256: sha256, contentSha256, lang }, actor);
    } catch (cause) { onError(cause instanceof Error ? cause.message : String(cause)); } finally { setBusy(''); }
  }

  async function upload(file: File | undefined) {
    if (!file) return;
    setBusy('upload');
    try {
      const saved = await saveAmdScan(patientId, file, actor);
      setExecution((previous) => ({ ...previous, ...saved }));
    } catch (cause) { onError(cause instanceof Error ? cause.message : 'The file could not be saved on this device.'); } finally { setBusy(''); }
  }

  async function record() {
    setTried(true);
    if (blocking(issues).length) return;
    setBusy('record');
    const result = await executeAmd(patientId, version.id, execution, custodian, actor);
    setBusy('');
    if (!result.ok) onError(result.issues.map((item) => item.message).join(' '));
  }

  const blockFor = (field: string) => show.filter((item) => item.field === field || item.field.startsWith(field));
  return <section className="amd-card" aria-labelledby={`${uid}-h`}>
    <h2 id={`${uid}-h`}>{t(`Sign version ${version.version} and record it`, `संस्करण ${version.version} पर हस्ताक्षर करें और दर्ज करें`)}</h2>
    <ol className="amd-numbered">
      <li>{t('Print the PDF. Read it once more.', 'PDF प्रिंट करें। एक बार फिर पढ़ें।')} <button type="button" className="care-text-button" disabled={busy === 'print'} onClick={reprint}>{busy === 'print' ? t('Making…', 'बन रहा है…') : t('Download the PDF again', 'PDF फिर से डाउनलोड करें')}</button></li>
      <li>{t('Sign (or give a thumb impression) in front of two adult witnesses. Better if they are not your representatives.', 'दो वयस्क गवाहों के सामने हस्ताक्षर करें (या अँगूठा लगाएँ)। बेहतर है कि वे आपके प्रतिनिधि न हों।')}</li>
      <li>{t('Take it to a notary or a gazetted officer to attest and stamp.', 'इसे नोटरी या राजपत्रित अधिकारी के पास सत्यापन और मुहर के लिए ले जाएँ।')}</li>
      <li>{t('Take a clear photo or scan of every page and upload it here.', 'हर पेज की साफ़ फ़ोटो या स्कैन लें और यहाँ अपलोड करें।')}</li>
    </ol>
    {version.pdfSha256 ? <p className="amd-hint">{t('Printed PDF SHA-256:', 'प्रिंट PDF का SHA-256:')} <code className="amd-hash">{version.pdfSha256}</code></p> : null}
    <div className="amd-row">
      <Field id={`${uid}-on`} label={t('Date signed', 'हस्ताक्षर की तारीख़')} issue={blockFor('execution.signedOn')}><TextInput id={`${uid}-on`} type="date" value={execution.signedOn} onChange={(signedOn) => setExecution((p) => ({ ...p, signedOn }))} /></Field>
      <Field id={`${uid}-place`} label={t('Place', 'जगह')}><TextInput id={`${uid}-place`} value={execution.place} onChange={(place) => setExecution((p) => ({ ...p, place }))} /></Field>
    </div>
    <Check checked={execution.thumbImpression} onChange={(thumbImpression) => setExecution((p) => ({ ...p, thumbImpression }))}>{t('Thumb impression instead of signature', 'हस्ताक्षर की जगह अँगूठे का निशान')}</Check>
    <WitnessFields t={t} index={0} uid={uid} value={execution.witnesses[0]} dhrNames={dhrNames} issues={show} onChange={(value) => setWitness(0, value)} />
    <WitnessFields t={t} index={1} uid={uid} value={execution.witnesses[1]} dhrNames={dhrNames} issues={show} onChange={(value) => setWitness(1, value)} />
    <fieldset className="amd-fieldset"><legend>{t('Attested by', 'सत्यापित करने वाले')}</legend>
      {([['NOTARY', 'Notary', 'नोटरी'], ['GAZETTED_OFFICER', 'Gazetted officer', 'राजपत्रित अधिकारी']] as Array<[AmdAttestorKind, string, string]>).map(([kind, en, hi]) => <label key={kind} className="amd-check"><input type="radio" name={`${uid}-att`} checked={execution.attestor.kind === kind} onChange={() => setAttestor({ kind })} /><span>{t(en, hi)}</span></label>)}
      {blockFor('execution.attestor.kind').map((item) => <p key={item.id} className="amd-issue amd-issue-block">{item.message}</p>)}
      <div className="amd-row">
        <Field id={`${uid}-att-name`} label={t('Full name', 'पूरा नाम')} issue={blockFor('execution.attestor.name')}><TextInput id={`${uid}-att-name`} value={execution.attestor.name} onChange={(name) => setAttestor({ name })} /></Field>
        <Field id={`${uid}-att-reg`} label={t('Registration no. or designation', 'पंजीकरण नंबर या पद')}><TextInput id={`${uid}-att-reg`} value={execution.attestor.registrationNo} onChange={(registrationNo) => setAttestor({ registrationNo })} /></Field>
      </div>
    </fieldset>
    <Field id={`${uid}-scan`} label={t('Photo or scan of the signed, attested paper', 'हस्ताक्षरित, सत्यापित काग़ज़ की फ़ोटो या स्कैन')} issue={blockFor('execution.scan')} hint={t('Kept on this device only. A SHA-256 fingerprint is recorded so a doctor can check the copy is the same.', 'केवल इसी डिवाइस पर रखा जाता है। एक SHA-256 फ़िंगरप्रिंट दर्ज होता है ताकि डॉक्टर जाँच सके कि प्रति वही है।')}>
      <input id={`${uid}-scan`} type="file" accept="application/pdf,image/*" disabled={busy === 'upload'} onChange={(event) => upload(event.target.files?.[0])} />
    </Field>
    {execution.scanSha256 ? <p className="amd-hint">✓ {execution.scanName} · SHA-256 <code className="amd-hash">{execution.scanSha256}</code></p> : null}
    <CustodianFields t={t} uid={uid} value={custodian} defaultBody={version.content.custodianBody} onChange={setCustodian} />
    {blockFor('custodian').map((item) => <p key={item.id} className="amd-issue amd-issue-block">{item.message}</p>)}
    {tried && blocking(issues).length ? <p className="amd-issue amd-issue-block" role="alert">{t('It stays “printed, not signed” until all of this is recorded.', 'जब तक यह सब दर्ज न हो, यह “प्रिंट हुआ, हस्ताक्षर नहीं” रहेगा।')}</p> : null}
    <div className="amd-actions"><span /><button type="button" className="primary-button amd-primary" disabled={busy !== ''} onClick={record}>{t('Record as signed and attested', 'हस्ताक्षरित और सत्यापित के रूप में दर्ज करें')}</button></div>
    <p className="amd-hint">{t('Need to change something? Start a new version from the signed one below, or ask for help. This printed copy must then not be signed.', 'कुछ बदलना है? नीचे से नया संस्करण शुरू करें। तब इस प्रिंट की गई प्रति पर हस्ताक्षर नहीं करने चाहिए।')}</p>
  </section>;
}

// ---------------------------------------------------------------------------
// EXECUTED: proof, copies, ABHA, new version, revoke

function ScanButton({ t, scanKey, label }: { t: T; scanKey: string; label?: string }) {
  const [message, setMessage] = useState('');
  async function open() {
    const file = await readAmdScan(scanKey);
    if (!file) { setMessage(t('The scan is not on this device.', 'स्कैन इस डिवाइस पर नहीं है।')); return; }
    const url = URL.createObjectURL(file);
    window.open(url, '_blank', 'noopener');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
  return <>{' '}<button type="button" className="secondary-button" onClick={open}>{label ?? t('View the signed scan', 'हस्ताक्षरित स्कैन देखें')}</button>{message ? <span className="amd-hint"> {message}</span> : null}</>;
}

function ProofList({ t, version }: { t: T; version: AmdVersion }) {
  const ex = version.execution!;
  return <dl className="amd-summary">
    <div><dt>{t('Signed', 'हस्ताक्षर')}</dt><dd>{formatDay(ex.signedOn)}{ex.place ? `, ${ex.place}` : ''}{ex.thumbImpression ? ` · ${t('thumb impression', 'अँगूठे का निशान')}` : ''}</dd></div>
    <div><dt>{t('Witnesses', 'गवाह')}</dt><dd>{ex.witnesses.map((w) => w.name).join(', ')}</dd></div>
    <div><dt>{t('Attested by', 'सत्यापित')}</dt><dd>{ex.attestor.kind === 'NOTARY' ? t('Notary', 'नोटरी') : t('Gazetted officer', 'राजपत्रित अधिकारी')}: {ex.attestor.name}{ex.attestor.registrationNo ? ` (${ex.attestor.registrationNo})` : ''}</dd></div>
    <div><dt>{t('Custodian', 'संरक्षक')}</dt><dd>{version.custodian?.status === 'ACKNOWLEDGED' ? t(`Acknowledged by ${version.custodian.officer} on ${formatDay(version.custodian.acknowledgedOn)}`, `${version.custodian.officer} द्वारा ${formatDay(version.custodian.acknowledgedOn)} को पावती`) : t('Custodian pending', 'संरक्षक बाक़ी')}</dd></div>
    <div><dt>{t('Scan SHA-256', 'स्कैन SHA-256')}</dt><dd><code className="amd-hash">{ex.scanSha256}</code></dd></div>
    {version.pdfSha256 ? <div><dt>{t('Printed PDF SHA-256', 'प्रिंट PDF SHA-256')}</dt><dd><code className="amd-hash">{version.pdfSha256}</code></dd></div> : null}
    {version.abha ? <div><dt>ABHA</dt><dd>{version.abha.address}</dd></div> : null}
  </dl>;
}

function ExecutedPanel({ t, version, patientId, actor, hasWorking, onNewVersion }: { t: T; version: AmdVersion; patientId: string; actor: string; hasWorking: boolean; onNewVersion: () => void }) {
  const uid = useId();
  const [error, setError] = useState('');
  const [custodian, setCustodian] = useState<AmdCustodian | null>(version.custodian ?? null);
  const [abha, setAbha] = useState({ address: version.abha?.address ?? '', consent: Boolean(version.abha?.consent) });
  const [revoke, setRevoke] = useState({ revokedOn: '', writtenRevocationSigned: false, reason: '', scanKey: '', scanSha256: '' });
  const [confirmRevoke, setConfirmRevoke] = useState(false);
  const done = (result: { ok: boolean; issues?: AmdIssue[] }) => setError(result.ok ? '' : (result.issues ?? []).map((item) => item.message).join(' '));
  const copyKind = (copy: AmdCopy) => copy.kind === 'DHR' ? t('Representative', 'प्रतिनिधि') : copy.kind === 'FAMILY_PHYSICIAN' ? t('Family doctor', 'पारिवारिक डॉक्टर') : t('Custodian', 'संरक्षक');

  async function revokeNow() {
    const result = await revokeAmdRecord(patientId, { ...revoke, scanKey: revoke.scanKey || undefined, scanSha256: revoke.scanSha256 || undefined }, actor);
    done(result);
    if (result.ok) setConfirmRevoke(false);
  }

  return <section className="amd-card" aria-labelledby={`${uid}-h`}>
    <h2 id={`${uid}-h`}>{t(`Signed living will · version ${version.version}`, `हस्ताक्षरित लिविंग विल · संस्करण ${version.version}`)}</h2>
    <Callout tone="ok"><p>{t('This is the current living will. Doctors can see that it is signed, witnessed and attested.', 'यह मौजूदा लिविंग विल है। डॉक्टर देख सकते हैं कि यह हस्ताक्षरित, गवाहों के सामने और सत्यापित है।')}</p></Callout>
    <ProofList t={t} version={version} />
    <ScanButton t={t} scanKey={version.execution!.scanKey} />

    <h3>{t('Copies', 'प्रतियाँ')}</h3>
    <p className="amd-hint">{t('Give a copy to each person below. Ask each one first: their consent to receive it is recorded separately.', 'नीचे हर व्यक्ति को एक प्रति दें। पहले हर एक से पूछें: प्रति लेने की उनकी सहमति अलग से दर्ज होती है।')}</p>
    <ul className="amd-copies">{version.copies.map((copy) => <li key={copy.id}>
      <div><strong>{copy.name}</strong> <span className="amd-chip">{copyKind(copy)}</span></div>
      <Check checked={copy.consent} onChange={async (consent) => done(await updateAmdCopy(patientId, version.id, copy.id, { consent }, actor))}>{copy.kind === 'CUSTODIAN' ? t('The patient agrees to send a copy to the custodian.', 'मरीज़ संरक्षक को प्रति भेजने के लिए सहमत है।') : t('The patient agrees to share it, and this person agrees to receive and keep it.', 'मरीज़ इसे साझा करने के लिए, और यह व्यक्ति इसे लेने और रखने के लिए सहमत है।')}</Check>
      <div className="amd-row">
        <Check checked={copy.given} disabled={!copy.consent} onChange={async (given) => done(await updateAmdCopy(patientId, version.id, copy.id, { given, method: copy.method ?? 'IN_PERSON' }, actor))}>{copy.given ? t(`Copy given on ${formatDay(copy.givenOn)}`, `प्रति ${formatDay(copy.givenOn)} को दी गई`) : t('Copy given', 'प्रति दी गई')}</Check>
        <select aria-label={t('How it was given', 'कैसे दी गई')} value={copy.method ?? ''} disabled={!copy.consent} onChange={async (event) => done(await updateAmdCopy(patientId, version.id, copy.id, { method: (event.target.value || null) as AmdCopy['method'] }, actor))}>
          <option value="">{t('How?', 'कैसे?')}</option><option value="IN_PERSON">{t('In person', 'हाथ से')}</option><option value="POST">{t('By post', 'डाक से')}</option><option value="DIGITAL">{t('Digital copy', 'डिजिटल प्रति')}</option>
        </select>
      </div>
    </li>)}</ul>

    {version.custodian?.status === 'PENDING' ? <>
      <CustodianFields t={t} uid={uid} value={custodian} defaultBody={version.content.custodianBody} onChange={setCustodian} />
      <button type="button" className="secondary-button" onClick={async () => custodian && done(await updateAmdCustodian(patientId, version.id, custodian, actor))}>{t('Save custodian', 'संरक्षक सहेजें')}</button>
    </> : null}

    <details className="amd-details"><summary>{t('Link to ABHA health records (optional)', 'ABHA स्वास्थ्य रिकॉर्ड से जोड़ें (वैकल्पिक)')}</summary>
      <p className="amd-hint">{t('The Supreme Court allows adding the living will to digital health records. Sahara only notes the ABHA address here; upload the scan in your ABHA app yourself.', 'सुप्रीम कोर्ट लिविंग विल को डिजिटल स्वास्थ्य रिकॉर्ड में जोड़ने की अनुमति देता है। सहारा यहाँ केवल ABHA पता नोट करता है; स्कैन अपने ABHA ऐप में ख़ुद अपलोड करें।')}</p>
      <Field id={`${uid}-abha`} label={t('ABHA address or number', 'ABHA पता या नंबर')}><TextInput id={`${uid}-abha`} value={abha.address} placeholder="name@abdm" onChange={(address) => setAbha((p) => ({ ...p, address }))} /></Field>
      <Check checked={abha.consent} onChange={(consent) => setAbha((p) => ({ ...p, consent }))}>{t('The patient agrees to link the living will with their ABHA records.', 'मरीज़ लिविंग विल को अपने ABHA रिकॉर्ड से जोड़ने के लिए सहमत है।')}</Check>
      <button type="button" className="secondary-button" onClick={async () => done(await updateAmdAbha(patientId, version.id, abha, actor))}>{t('Save ABHA link', 'ABHA लिंक सहेजें')}</button>
    </details>

    {error ? <p className="amd-issue amd-issue-block" role="alert">{error}</p> : null}

    <div className="amd-split">
      <div>
        <h3>{t('Change it', 'इसे बदलें')}</h3>
        <p className="amd-hint">{t(`Changes make a new version that must be printed, signed and attested again. Version ${version.version} stays current until then.`, `बदलाव से नया संस्करण बनता है जिसे फिर से प्रिंट, हस्ताक्षर और सत्यापित करना होगा। तब तक संस्करण ${version.version} मौजूदा रहेगा।`)}</p>
        {!hasWorking ? <button type="button" className="secondary-button" onClick={onNewVersion}>{t('Start a new version', 'नया संस्करण शुरू करें')}</button> : <p className="amd-hint">{t('A new version is already in progress above.', 'एक नया संस्करण ऊपर पहले से चल रहा है।')}</p>}
      </div>
      <div>
        <h3>{t('Cancel it', 'इसे रद्द करें')}</h3>
        <p className="amd-hint">{t('A living will is cancelled in writing: the person signs a short note saying they cancel it, the same way it was made. Then give the note to everyone who has a copy.', 'लिविंग विल लिखित रूप में रद्द होती है: व्यक्ति एक छोटे नोट पर हस्ताक्षर करता है कि वह इसे रद्द कर रहा है, उसी तरह जैसे बनाई गई थी। फिर वह नोट हर उस व्यक्ति को दें जिसके पास प्रति है।')}</p>
        {!confirmRevoke ? <button type="button" className="secondary-button amd-danger" onClick={() => setConfirmRevoke(true)}>{t('Record a written cancellation', 'लिखित रद्दीकरण दर्ज करें')}</button> : <div className="amd-revoke">
          <Field id={`${uid}-rev-on`} label={t('Date of the written cancellation', 'लिखित रद्दीकरण की तारीख़')}><TextInput id={`${uid}-rev-on`} type="date" value={revoke.revokedOn} onChange={(revokedOn) => setRevoke((p) => ({ ...p, revokedOn }))} /></Field>
          <Check checked={revoke.writtenRevocationSigned} onChange={(writtenRevocationSigned) => setRevoke((p) => ({ ...p, writtenRevocationSigned }))}>{t('The person signed a written cancellation.', 'व्यक्ति ने लिखित रद्दीकरण पर हस्ताक्षर किए हैं।')}</Check>
          <Field id={`${uid}-rev-why`} label={t('Reason (optional)', 'कारण (वैकल्पिक)')}><TextInput id={`${uid}-rev-why`} value={revoke.reason} onChange={(reason) => setRevoke((p) => ({ ...p, reason }))} /></Field>
          <Field id={`${uid}-rev-scan`} label={t('Photo of the cancellation (optional)', 'रद्दीकरण की फ़ोटो (वैकल्पिक)')}><input id={`${uid}-rev-scan`} type="file" accept="application/pdf,image/*" onChange={async (event) => { const file = event.target.files?.[0]; if (!file) return; const saved = await saveAmdScan(patientId, file, actor, 'REVOCATION'); setRevoke((p) => ({ ...p, scanKey: saved.scanKey, scanSha256: saved.scanSha256 })); }} /></Field>
          <div className="amd-actions"><button type="button" className="secondary-button" onClick={() => setConfirmRevoke(false)}>{t('Keep it', 'रहने दें')}</button><button type="button" className="primary-button amd-primary amd-danger" onClick={revokeNow}>{t('Cancel the living will', 'लिविंग विल रद्द करें')}</button></div>
        </div>}
      </div>
    </div>
  </section>;
}

// ---------------------------------------------------------------------------
// Doctor: read-only verification (L5, L7)

function DoctorView({ t, current, latest }: { t: T; current: AmdVersion | null; latest: AmdVersion | null }) {
  if (!current) {
    return <section className="amd-card">
      <h2>{t('Living will for this patient', 'इस मरीज़ की लिविंग विल')}</h2>
      <Callout tone="warn"><p>{latest?.status === 'REVOKED'
        ? t(`The living will was cancelled in writing on ${formatDay(latest.revocation?.revokedOn)}. It does not apply.`, `लिविंग विल ${formatDay(latest.revocation?.revokedOn)} को लिखित रूप में रद्द की गई। यह लागू नहीं है।`)
        : latest ? t('A living will has been started or printed but is not signed and recorded. It is not valid.', 'लिविंग विल शुरू या प्रिंट हुई है पर हस्ताक्षरित और दर्ज नहीं है। यह वैध नहीं है।')
          : t('No living will is recorded in Sahara. Ask the family if a signed paper exists.', 'सहारा में कोई लिविंग विल दर्ज नहीं है। परिवार से पूछें कि क्या कोई हस्ताक्षरित काग़ज़ है।')}</p></Callout>
    </section>;
  }
  const refused = AMD_TREATMENTS.filter((tr) => current.content.treatments[tr] === 'REFUSE');
  const accepted = AMD_TREATMENTS.filter((tr) => current.content.treatments[tr] === 'ACCEPT');
  return <section className="amd-card">
    <h2>{t(`Signed living will · version ${current.version}`, `हस्ताक्षरित लिविंग विल · संस्करण ${current.version}`)}</h2>
    <p className="amd-hint">{t('Check the paper copy against the scan and its SHA-256, or with the custodian. Doctors only read this here; nothing in the app starts or stops treatment.', 'काग़ज़ की प्रति को स्कैन और उसके SHA-256 से, या संरक्षक से मिलाएँ। डॉक्टर इसे यहाँ केवल पढ़ते हैं; ऐप में कुछ भी इलाज शुरू या बंद नहीं करता।')}</p>
    <ProofList t={t} version={current} />
    <ScanButton t={t} scanKey={current.execution!.scanKey} />
    <dl className="amd-summary">
      <div><dt>{t('Does not want', 'नहीं चाहते')}</dt><dd>{refused.map((tr) => t(TREATMENT_TEXT[tr].en, TREATMENT_TEXT[tr].hi)).join('; ') || '—'}</dd></div>
      <div><dt>{t('Wants', 'चाहते हैं')}</dt><dd>{accepted.map((tr) => t(TREATMENT_TEXT[tr].en, TREATMENT_TEXT[tr].hi)).join('; ') || '—'}</dd></div>
      <div><dt>{t('Representatives, in order', 'प्रतिनिधि, क्रम से')}</dt><dd>{current.content.dhrs.filter((d) => d.name.trim()).map((d, i) => `${i + 1}. ${d.name} (${d.relation}) ${d.phone}`).join(' · ')}</dd></div>
    </dl>
  </section>;
}
