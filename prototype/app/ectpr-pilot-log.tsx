'use client';
// Pilot audit data (clinical spec §8, Appendix D.3), doctor only: emergency episodes, after-death outcomes and safety incidents,
// with the indicators the pilot reports: reach, values section, retrieval within 10 minutes, concordance. Synthetic data only.
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { careLocale, type CareLang } from './care-i18n';
import type { AuditEvent, EctprRecord, EpisodeLog, IncidentReport, OutcomeRecord, PlaceOfCare, RespCeiling } from './ectpr-model';
import {
  RETRIEVAL_TARGET_MINUTES, episodeProblems, episodeRow, firstEdAccessAfter, hoursToSymptomRx, incidentIsEmpty, outcomeProblems, pilotSummary,
  placeMatchesPreference, planInForceAt, reachDays, valuesIndicator, type EpisodeProblem, type OutcomeProblem,
} from './ectpr-pilot';
import {
  listEpisodeLogs, listIncidentReports, listOutcomeRecords, logAudit, saveEpisodeLog, saveIncidentReport, saveOutcomeRecord, subscribeEctpr,
} from './ectpr-store';

type T = (en: string, hi: string) => string;

const CEILING_OPTS: Array<{ value: RespCeiling; en: string; hi: string }> = [
  { value: 0, en: 'No breathing support: comfort care only', hi: 'साँस की कोई मदद नहीं: सिर्फ़ आराम की देखभाल' },
  { value: 1, en: 'Nasal prongs or simple mask', hi: 'नाक की नली या सादा मास्क' },
  { value: 2, en: 'Non-rebreather or Venturi mask', hi: 'नॉन-रीब्रीदर या वेंचुरी मास्क' },
  { value: 3, en: 'High-flow nasal oxygen', hi: 'हाई-फ़्लो ऑक्सीजन' },
  { value: 4, en: 'Non-invasive ventilation (NIV)', hi: 'बिना नली का वेंटिलेटर (NIV)' },
  { value: 5, en: 'Breathing tube and ventilator', hi: 'साँस की नली और वेंटिलेटर' },
];

const PLACE_OPTS: Array<{ value: PlaceOfCare; en: string; hi: string }> = [
  { value: 'HOME', en: 'Home', hi: 'घर' },
  { value: 'HOSPICE', en: 'Hospice', hi: 'हॉस्पिस' },
  { value: 'WARD', en: 'Hospital ward', hi: 'अस्पताल का वार्ड' },
  { value: 'ICU', en: 'ICU', hi: 'ICU' },
  { value: 'ED', en: 'Emergency department', hi: 'इमरजेंसी विभाग' },
  { value: 'OTHER', en: 'Somewhere else', hi: 'कहीं और' },
];

const placeLabel = (t: T, value: PlaceOfCare | null) => { const found = PLACE_OPTS.find((o) => o.value === value); return found ? t(found.en, found.hi) : t('Not recorded', 'दर्ज नहीं'); };
const fromLocal = (value: string): string | null => { if (!value) return null; const date = new Date(value); return Number.isNaN(date.getTime()) ? null : date.toISOString(); };
const toLocal = (iso: string): string => { const date = new Date(iso); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); };
const toNumber = (value: string): number | null => { if (value.trim() === '') return null; const n = Number(value); return Number.isFinite(n) ? n : null; };

function Field({ id, label, hint, wide, children }: { id: string; label: string; hint?: string; wide?: boolean; children: ReactNode }) {
  return <div className={`ep-field${wide ? ' ep-wide' : ''}`}><label htmlFor={id}>{label}</label>{children}{hint ? <p className="ep-hint">{hint}</p> : null}</div>;
}

function YesNoSelect({ t, id, label, value, onChange }: { t: T; id: string; label: string; value: boolean | null; onChange: (value: boolean | null) => void }) {
  return <Field id={id} label={label}>
    <select id={id} value={value === null ? '' : value ? 'yes' : 'no'} onChange={(event) => onChange(event.target.value === '' ? null : event.target.value === 'yes')}>
      <option value="">{t('Not recorded', 'दर्ज नहीं')}</option><option value="yes">{t('Yes', 'हाँ')}</option><option value="no">{t('No', 'नहीं')}</option>
    </select>
  </Field>;
}

function Indicator({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' }) {
  return <div className="ep-indicator" data-tone={tone}><dt>{label}</dt><dd>{value}</dd></div>;
}

export function EctprPilotLog({ t, lang, patientId, actorName, versions, audit }: { t: T; lang: CareLang; patientId: string; actorName: string; versions: EctprRecord[]; audit: AuditEvent[] }) {
  const [episodes, setEpisodes] = useState<EpisodeLog[]>([]);
  const [outcomes, setOutcomes] = useState<OutcomeRecord[]>([]);
  const [incidents, setIncidents] = useState<IncidentReport[]>([]);
  const [panel, setPanel] = useState<'none' | 'episode' | 'outcome' | 'incident'>('none');
  const [saved, setSaved] = useState('');

  useEffect(() => {
    let live = true;
    const load = () => {
      void Promise.all([listEpisodeLogs(patientId), listOutcomeRecords(patientId), listIncidentReports(patientId)]).then(([e, o, i]) => {
        if (!live) return;
        setEpisodes(e); setOutcomes(o); setIncidents(i);
      });
    };
    load();
    const off = subscribeEctpr(load);
    return () => { live = false; off(); };
  }, [patientId]);

  const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString(careLocale(lang, 'en-IN'), { dateStyle: 'medium', timeStyle: 'short' }) : t('Not recorded', 'दर्ज नहीं'));
  const rows = useMemo(() => episodes.map((episode) => episodeRow(episode, versions)), [episodes, versions]);
  const summary = pilotSummary(rows);
  const latestSigned = versions.find((r) => r.status !== 'DRAFT') ?? null;
  const reach = latestSigned ? reachDays(latestSigned) : null;
  const values = versions[0] ? valuesIndicator(latestSigned ?? versions[0]) : null;

  function done(message: string) { setPanel('none'); setSaved(message); }

  const toggle = (name: typeof panel, en: string, hi: string) => <button type="button" className={panel === name ? 'ep-btn' : 'ep-btn ep-quiet'} aria-expanded={panel === name} onClick={() => { setSaved(''); setPanel(panel === name ? 'none' : name); }}>{t(en, hi)}</button>;

  return <section className="ep-card" aria-labelledby="ep-pilot-title">
    <h2 id="ep-pilot-title">{t('Pilot audit log', 'पायलट ऑडिट लॉग')}</h2>
    <p className="ep-hint">{t('For the care team only. Made-up patients only during the pilot. Saved on this device.', 'केवल देखभाल टीम के लिए। पायलट में सिर्फ़ काल्पनिक मरीज़। इसी डिवाइस पर सेव।')}</p>

    <dl className="ep-indicators">
      <Indicator label={t('Reach: found eligible to signed', 'पहुँच: योग्य पाए जाने से साइन तक')} value={reach === null ? t('Not known yet: needs the date found eligible and a signature', 'अभी पता नहीं: योग्य पाए जाने की तारीख़ और साइन चाहिए') : `${reach} ${t('days', 'दिन')}`} />
      <Indicator label={t('Values section completed', 'मूल्य वाला हिस्सा पूरा')} tone={values?.limitsWithoutValues ? 'bad' : values?.valuesFilled ? 'good' : undefined}
        value={!values ? t('No plan yet', 'अभी कोई योजना नहीं') : `${values.valuesFilled ? t('Yes', 'हाँ') : t('No', 'नहीं')} · ${t(`treatment fields filled: ${values.treatmentFilled} of ${values.treatmentTotal}`, `भरे गए इलाज के हिस्से: ${values.treatmentTotal} में से ${values.treatmentFilled}`)}`} />
      <Indicator label={t(`Record found within ${RETRIEVAL_TARGET_MINUTES} minutes of ED arrival`, `ED पहुँचने के ${RETRIEVAL_TARGET_MINUTES} मिनट में रिकॉर्ड मिला`)} value={t(`${summary.withinTarget} of ${summary.episodes} episodes`, `${summary.episodes} में से ${summary.withinTarget} मामले`)} />
      <Indicator label={t('Care matched the recorded plan', 'इलाज दर्ज योजना के अनुसार हुआ')} value={t(`${summary.concordant} of ${summary.compared} that could be compared`, `जिनकी तुलना हो सकी, उन ${summary.compared} में से ${summary.concordant}`)} />
    </dl>

    {saved ? <p className="ep-toast" role="status">{saved}</p> : null}
    <div className="ep-actions">
      {toggle('episode', 'Log an ED episode', 'ED का मामला दर्ज करें')}
      {toggle('outcome', 'Record an outcome', 'नतीजा दर्ज करें')}
      {toggle('incident', 'Report an incident', 'घटना की रिपोर्ट करें')}
    </div>

    {panel === 'episode' ? <EpisodeForm t={t} patientId={patientId} actorName={actorName} versions={versions} audit={audit} onDone={() => done(t('ED episode saved.', 'ED का मामला सेव हुआ।'))} onCancel={() => setPanel('none')} /> : null}
    {panel === 'outcome' ? <OutcomeForm t={t} patientId={patientId} actorName={actorName} onDone={() => done(t('Outcome saved.', 'नतीजा सेव हुआ।'))} onCancel={() => setPanel('none')} /> : null}
    {panel === 'incident' ? <IncidentForm t={t} patientId={patientId} actorName={actorName} active={versions.find((r) => r.status === 'ACTIVE') ?? null} onDone={() => done(t('Incident saved.', 'घटना सेव हुई।'))} onCancel={() => setPanel('none')} /> : null}

    <h3 className="ep-pilot-heading">{t('ED episodes', 'ED के मामले')}</h3>
    {rows.length === 0 ? <p className="ep-hint">{t('None recorded yet.', 'अभी कुछ दर्ज नहीं।')}</p> : <ul className="ep-versions">
      {[...rows].reverse().map(({ episode, minutes, withinTarget, concordant }) => <li key={episode.id} className="ep-version">
        <div className="ep-version-top"><strong>{when(episode.edArrivalAt)}</strong>
          <span className="ep-chip" data-status={concordant === false ? 'REVOKED' : concordant ? 'ACTIVE' : 'SUPERSEDED'}>{concordant === null ? t('Nothing to compare', 'तुलना के लिए कुछ नहीं') : concordant ? t('Matched the plan', 'योजना के अनुसार') : t('Differed from the plan', 'योजना से अलग')}</span></div>
        <p>{minutes === null ? t('The record was not opened', 'रिकॉर्ड नहीं खोला गया') : t(`Record opened after ${minutes} minutes`, `${minutes} मिनट बाद रिकॉर्ड खुला`)}{withinTarget === false ? ` · ${t(`over the ${RETRIEVAL_TARGET_MINUTES}-minute target`, `${RETRIEVAL_TARGET_MINUTES} मिनट के लक्ष्य से ज़्यादा`)}` : ''}</p>
        <p>{t('Highest support', 'सबसे ज़्यादा मदद')}: {episode.highestSupportDelivered === null ? t('Not recorded', 'दर्ज नहीं') : t(CEILING_OPTS[episode.highestSupportDelivered].en, CEILING_OPTS[episode.highestSupportDelivered].hi)} · CPR: {episode.cprGiven === null ? t('Not recorded', 'दर्ज नहीं') : episode.cprGiven ? t('Yes', 'हाँ') : t('No', 'नहीं')}</p>
        {episode.discordanceReason ? <p>{t('Why care differed', 'इलाज अलग क्यों हुआ')}: {episode.discordanceReason}</p> : null}
        <p>{t('Recorded by', 'दर्ज किया')} {episode.recordedBy}</p>
      </li>)}
    </ul>}

    <h3 className="ep-pilot-heading">{t('Outcomes', 'नतीजे')}</h3>
    {outcomes.length === 0 ? <p className="ep-hint">{t('None recorded yet.', 'अभी कुछ दर्ज नहीं।')}</p> : <ul className="ep-versions">
      {[...outcomes].reverse().map((outcome) => {
        const match = placeMatchesPreference(outcome);
        const hours = hoursToSymptomRx(outcome);
        return <li key={outcome.id} className="ep-version">
          <div className="ep-version-top"><strong>{placeLabel(t, outcome.placeOfDeath)}</strong>
            <span className="ep-chip" data-status={match === false ? 'REVOKED' : match ? 'ACTIVE' : 'SUPERSEDED'}>{match === null ? t('Preference not known', 'पसंद पता नहीं') : match ? t('Where they preferred', 'जहाँ वे चाहते थे') : t('Not where they preferred', 'जहाँ वे चाहते थे, वहाँ नहीं')}</span></div>
          <p>{t('Preferred place', 'पसंद की जगह')}: {placeLabel(t, outcome.preferredPlaceOfDeath)}</p>
          <p>{t('Last 30 days', 'आख़िरी 30 दिन')}: {t('ICU days', 'ICU के दिन')} {outcome.icuDaysLast30 ?? '–'} · {t('ventilator days', 'वेंटिलेटर के दिन')} {outcome.ventilatorDaysLast30 ?? '–'}</p>
          <p>{t('Admission to first symptom-control prescription', 'भर्ती से लक्षण की पहली दवा तक')}: {hours === null ? t('Not recorded', 'दर्ज नहीं') : `${hours} ${t('hours', 'घंटे')}`}</p>
          <p>{t('Out-of-pocket spend, last admission', 'आख़िरी भर्ती का अपनी जेब से ख़र्च')}: {outcome.outOfPocketLastAdmissionInr === null ? t('Not recorded', 'दर्ज नहीं') : `₹${outcome.outOfPocketLastAdmissionInr.toLocaleString('en-IN')}`}</p>
          <p>{t('Recorded by', 'दर्ज किया')} {outcome.recordedBy} · {when(outcome.recordedAt)}</p>
        </li>;
      })}
    </ul>}

    <h3 className="ep-pilot-heading">{t('Incidents', 'घटनाएँ')}</h3>
    {incidents.length === 0 ? <p className="ep-hint">{t('None reported yet.', 'अभी कोई रिपोर्ट नहीं।')}</p> : <ul className="ep-versions">
      {[...incidents].reverse().map((incident) => <li key={incident.id} className="ep-version">
        <div className="ep-version-top"><strong>{when(incident.reportedAt)}</strong></div>
        {incident.appliedToReversibleIllness ? <p><strong>{t('Plan applied to a reversible illness', 'ठीक होने वाली बीमारी पर योजना लागू हुई')}</strong></p> : null}
        {incident.familyFeltPushed ? <p><strong>{t('Family felt pushed', 'परिवार को दबाव महसूस हुआ')}</strong></p> : null}
        {incident.details ? <p>{incident.details}</p> : null}
        <p>{t('Reported by', 'रिपोर्ट की')} {incident.reportedBy}</p>
      </li>)}
    </ul>}
  </section>;
}

function EpisodeForm({ t, patientId, actorName, versions, audit, onDone, onCancel }: { t: T; patientId: string; actorName: string; versions: EctprRecord[]; audit: AuditEvent[]; onDone: () => void; onCancel: () => void }) {
  const [arrival, setArrival] = useState('');
  const [access, setAccess] = useState('');
  const [support, setSupport] = useState<RespCeiling | null>(null);
  const [cpr, setCpr] = useState<boolean | null>(null);
  const [icu, setIcu] = useState<boolean | null>(null);
  const [reason, setReason] = useState('');
  const [problems, setProblems] = useState<EpisodeProblem[]>([]);
  const [hint, setHint] = useState('');

  const edArrivalAt = fromLocal(arrival) ?? '';
  const plan = edArrivalAt ? planInForceAt(versions, edArrivalAt) : null;
  const draft = { edArrivalAt, firstRecordAccessAt: fromLocal(access), highestSupportDelivered: support, icuAdmitted: icu, vasopressorsGiven: null, dialysisGiven: null, cprGiven: cpr, discordanceReason: reason.trim() };
  const preview = edArrivalAt ? episodeRow({ ...draft, id: 'preview', patientId, ectprId: plan?.id ?? null, recordedBy: '', recordedAt: '' }, versions) : null;

  const wording: Record<EpisodeProblem, string> = {
    NO_ARRIVAL: t('Enter the ED arrival time.', 'ED पहुँचने का समय लिखें।'),
    ACCESS_BEFORE_ARRIVAL: t('The first access time cannot be before arrival.', 'रिकॉर्ड खोलने का समय पहुँचने से पहले का नहीं हो सकता।'),
    REASON_NEEDED: t('Care differed from the plan. Write why.', 'इलाज योजना से अलग हुआ। कारण लिखें।'),
  };

  async function save() {
    const found = episodeProblems(draft, plan);
    setProblems(found);
    if (found.length) return;
    const saved = await saveEpisodeLog({ ...draft, patientId, ectprId: plan?.id ?? null, recordedBy: actorName, recordedAt: new Date().toISOString() });
    await logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: saved.id, patientId, detail: 'Pilot: ED episode logged' });
    onDone();
  }

  return <div className="ep-dialog">
    <div className="ep-fields">
      <Field id="ep-pilot-arrival" label={t('ED arrival time', 'ED पहुँचने का समय')}><input id="ep-pilot-arrival" type="datetime-local" value={arrival} onChange={(event) => setArrival(event.target.value)} /></Field>
      <Field id="ep-pilot-access" label={t('First time the record was opened', 'रिकॉर्ड पहली बार कब खुला')} hint={preview?.minutes !== null && preview?.minutes !== undefined ? t(`${preview.minutes} minutes after arrival`, `पहुँचने के ${preview.minutes} मिनट बाद`) : undefined}><input id="ep-pilot-access" type="datetime-local" value={access} onChange={(event) => setAccess(event.target.value)} /></Field>
      <div className="ep-wide">
        <button type="button" className="ep-link" onClick={() => {
          const found = firstEdAccessAfter(audit, edArrivalAt);
          if (found) { setAccess(toLocal(found)); setHint(''); } else setHint(t('The access log has no ED access after this arrival time.', 'ऐक्सेस लॉग में इस समय के बाद ED का कोई ऐक्सेस नहीं है।'));
        }} disabled={!edArrivalAt}>{t('Fill from the access log', 'ऐक्सेस लॉग से भरें')}</button>
        {hint ? <p className="ep-hint" role="status">{hint}</p> : null}
      </div>
      <Field id="ep-pilot-support" label={t('Highest breathing support actually given', 'असल में दी गई साँस की सबसे ज़्यादा मदद')}>
        <select id="ep-pilot-support" value={support ?? ''} onChange={(event) => setSupport(event.target.value === '' ? null : Number(event.target.value) as RespCeiling)}>
          <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>
          {CEILING_OPTS.map((option) => <option key={option.value} value={option.value}>{t(option.en, option.hi)}</option>)}
        </select>
      </Field>
      <YesNoSelect t={t} id="ep-pilot-cpr" label={t('CPR given?', 'CPR दिया गया?')} value={cpr} onChange={setCpr} />
      <YesNoSelect t={t} id="ep-pilot-icu" label={t('Admitted to ICU?', 'ICU में भर्ती?')} value={icu} onChange={setIcu} />
      <Field id="ep-pilot-reason" wide label={t('If care differed from the plan, why?', 'अगर इलाज योजना से अलग हुआ, तो क्यों?')}><textarea id="ep-pilot-reason" rows={2} value={reason} onChange={(event) => setReason(event.target.value)} /></Field>
    </div>
    {preview ? <p className="ep-hint" role="status" style={{ marginTop: 10 }}>
      {!plan ? t('No signed plan was in force at this time, so there is nothing to compare.', 'उस समय कोई साइन की गई योजना लागू नहीं थी, इसलिए तुलना नहीं हो सकती।')
        : preview.concordant === null ? t(`Compared with version ${plan.version}: nothing to compare yet.`, `संस्करण ${plan.version} से तुलना: अभी तुलना के लिए कुछ नहीं।`)
          : preview.concordant ? t(`Compared with version ${plan.version}: matches the plan.`, `संस्करण ${plan.version} से तुलना: योजना के अनुसार।`)
            : t(`Compared with version ${plan.version}: differs from the plan.`, `संस्करण ${plan.version} से तुलना: योजना से अलग।`)}
    </p> : null}
    {problems.length ? <div className="ep-notice ep-alert" role="alert" style={{ marginTop: 10 }}>{problems.map((p) => <p key={p}>{wording[p]}</p>)}</div> : null}
    <div className="ep-actions"><button type="button" className="ep-btn" onClick={() => void save()}>{t('Save the episode', 'मामला सेव करें')}</button>
      <button type="button" className="ep-btn ep-quiet" onClick={onCancel}>{t('Not now', 'अभी नहीं')}</button></div>
  </div>;
}

function OutcomeForm({ t, patientId, actorName, onDone, onCancel }: { t: T; patientId: string; actorName: string; onDone: () => void; onCancel: () => void }) {
  const [place, setPlace] = useState<PlaceOfCare | ''>('');
  const [preferred, setPreferred] = useState<PlaceOfCare | ''>('');
  const [icuDays, setIcuDays] = useState('');
  const [ventDays, setVentDays] = useState('');
  const [admitted, setAdmitted] = useState('');
  const [rx, setRx] = useState('');
  const [spend, setSpend] = useState('');
  const [problems, setProblems] = useState<OutcomeProblem[]>([]);

  const wording: Record<OutcomeProblem, string> = {
    NOTHING_ENTERED: t('Enter at least one item.', 'कम से कम एक चीज़ भरें।'),
    DAYS_OUT_OF_RANGE: t('Days must be between 0 and 30.', 'दिन 0 से 30 के बीच होने चाहिए।'),
    RX_BEFORE_ADMISSION: t('The first prescription cannot be before admission.', 'पहली दवा भर्ती से पहले की नहीं हो सकती।'),
    NEGATIVE_SPEND: t('Spend cannot be below zero.', 'ख़र्च शून्य से कम नहीं हो सकता।'),
  };
  const placeSelect = (id: string, label: string, value: PlaceOfCare | '', onChange: (value: PlaceOfCare | '') => void) => <Field id={id} label={label}>
    <select id={id} value={value} onChange={(event) => onChange(event.target.value as PlaceOfCare | '')}>
      <option value="">{t('Not recorded', 'दर्ज नहीं')}</option>
      {PLACE_OPTS.map((option) => <option key={option.value} value={option.value}>{t(option.en, option.hi)}</option>)}
    </select>
  </Field>;

  async function save() {
    const draft = { placeOfDeath: place || null, preferredPlaceOfDeath: preferred || null, icuDaysLast30: toNumber(icuDays), ventilatorDaysLast30: toNumber(ventDays), lastAdmissionAt: fromLocal(admitted), firstSymptomControlRxAt: fromLocal(rx), outOfPocketLastAdmissionInr: toNumber(spend) };
    const found = outcomeProblems(draft);
    setProblems(found);
    if (found.length) return;
    const saved = await saveOutcomeRecord({ ...draft, patientId, recordedBy: actorName, recordedAt: new Date().toISOString() });
    await logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: saved.id, patientId, detail: 'Pilot: outcome recorded' });
    onDone();
  }

  return <div className="ep-dialog">
    <div className="ep-fields">
      {placeSelect('ep-pilot-place', t('Place of death', 'मृत्यु की जगह'), place, setPlace)}
      {placeSelect('ep-pilot-preferred', t('Preferred place', 'पसंद की जगह'), preferred, setPreferred)}
      <Field id="ep-pilot-icu-days" label={t('ICU days in the last 30 days', 'आख़िरी 30 दिनों में ICU के दिन')}><input id="ep-pilot-icu-days" type="number" inputMode="numeric" min={0} max={30} value={icuDays} onChange={(event) => setIcuDays(event.target.value)} /></Field>
      <Field id="ep-pilot-vent-days" label={t('Ventilator days in the last 30 days', 'आख़िरी 30 दिनों में वेंटिलेटर के दिन')}><input id="ep-pilot-vent-days" type="number" inputMode="numeric" min={0} max={30} value={ventDays} onChange={(event) => setVentDays(event.target.value)} /></Field>
      <Field id="ep-pilot-admitted" label={t('Last admission time', 'आख़िरी भर्ती का समय')}><input id="ep-pilot-admitted" type="datetime-local" value={admitted} onChange={(event) => setAdmitted(event.target.value)} /></Field>
      <Field id="ep-pilot-rx" label={t('First symptom-control prescription', 'लक्षण की पहली दवा')}><input id="ep-pilot-rx" type="datetime-local" value={rx} onChange={(event) => setRx(event.target.value)} /></Field>
      <Field id="ep-pilot-spend" label={t('Out-of-pocket spend, last admission (₹)', 'आख़िरी भर्ती का अपनी जेब से ख़र्च (₹)')}><input id="ep-pilot-spend" type="number" inputMode="numeric" min={0} value={spend} onChange={(event) => setSpend(event.target.value)} /></Field>
    </div>
    {problems.length ? <div className="ep-notice ep-alert" role="alert" style={{ marginTop: 10 }}>{problems.map((p) => <p key={p}>{wording[p]}</p>)}</div> : null}
    <div className="ep-actions"><button type="button" className="ep-btn" onClick={() => void save()}>{t('Save the outcome', 'नतीजा सेव करें')}</button>
      <button type="button" className="ep-btn ep-quiet" onClick={onCancel}>{t('Not now', 'अभी नहीं')}</button></div>
  </div>;
}

function IncidentForm({ t, patientId, actorName, active, onDone, onCancel }: { t: T; patientId: string; actorName: string; active: EctprRecord | null; onDone: () => void; onCancel: () => void }) {
  const [reversible, setReversible] = useState(false);
  const [pushed, setPushed] = useState(false);
  const [details, setDetails] = useState('');
  const [empty, setEmpty] = useState(false);

  async function save() {
    const draft = { appliedToReversibleIllness: reversible, familyFeltPushed: pushed, details: details.trim() };
    if (incidentIsEmpty(draft)) { setEmpty(true); return; }
    const saved = await saveIncidentReport({ ...draft, patientId, ectprId: active?.id ?? null, reportedBy: actorName, reportedAt: new Date().toISOString() });
    await logAudit({ actor: actorName, action: 'EDIT', recordType: 'ECTPR', recordId: saved.id, patientId, detail: 'Pilot: safety incident reported' });
    onDone();
  }

  return <div className="ep-dialog">
    <div className="ep-options">
      <label className="ep-option"><input type="checkbox" checked={reversible} onChange={(event) => setReversible(event.target.checked)} /><span><strong>{t('The plan was applied to a reversible illness', 'ठीक होने वाली बीमारी पर योजना लागू की गई')}</strong></span></label>
      <label className="ep-option"><input type="checkbox" checked={pushed} onChange={(event) => setPushed(event.target.checked)} /><span><strong>{t('The family felt pushed', 'परिवार को दबाव महसूस हुआ')}</strong></span></label>
    </div>
    <div className="ep-fields" style={{ marginTop: 12 }}>
      <Field id="ep-pilot-notes" wide label={t('Notes', 'नोट')}><textarea id="ep-pilot-notes" rows={3} value={details} onChange={(event) => setDetails(event.target.value)} /></Field>
    </div>
    {empty ? <div className="ep-notice ep-alert" role="alert" style={{ marginTop: 10 }}><p>{t('Tick a box or write what happened.', 'कोई बॉक्स चुनें या लिखें कि क्या हुआ।')}</p></div> : null}
    <div className="ep-actions"><button type="button" className="ep-btn" onClick={() => void save()}>{t('Save the incident', 'घटना सेव करें')}</button>
      <button type="button" className="ep-btn ep-quiet" onClick={onCancel}>{t('Not now', 'अभी नहीं')}</button></div>
  </div>;
}
