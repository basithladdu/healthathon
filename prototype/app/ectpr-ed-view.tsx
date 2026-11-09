'use client';
// ED Quick View body (tech spec §6, clinical spec §6), drawn from one real EctprRecord.
// Kept light on purpose (no QR, PDF or speech libraries) so the public /ed/<token> page loads fast on 3G.
// Order: transfer · CPR · breathing ceiling · ICU · goal · allergies · implanted device · decision-maker (24 h) ·
// treating consultant + palliative contact · capacity flag · AMD status (L7 caveat) · version, signed, review due.
import type React from 'react';
import type { CareLang } from './care-i18n';
import { careDir, careText } from './care-i18n';
import type { AmdStatusSummary, EctprRecord } from './ectpr-model';
import { edInterpretation, type EdInterpretation } from './ectpr-rules';
import { homeCareLines,
  alwaysProvidedCare, amdCaveat, ambulanceLabel, authorityLabel, ceilingDetails, ceilingLabel, cprDecidedBy, cprLabel, deviceLabel,
  dialysisLabel, dyingPhasePrompt, edAmdLines, edBanner, edFlag, formatDay, goalLabel, hydrationLabel, icuLabel, ifInDoubtAfter, ifInDoubtConditions, ifInDoubtTitle,
  ngLabel, notRecorded, pegLabel, telHref, textFor, transferLabel, treatingConsultant, vasopressorLabel,
} from './ectpr-labels';

export type EdAudience = 'clinician' | 'patient' | 'family';

function Phone(props: { phone: string; lang: CareLang }) {
  if (!props.phone.trim()) return <span className="ectpr-ed-missing">{careText(props.lang, 'phone not recorded', 'फ़ोन दर्ज नहीं')}</span>;
  return <a className="ectpr-ed-tel" href={telHref(props.phone)}>{props.phone}</a>;
}

function Row(props: { label: string; recorded: boolean; children: React.ReactNode; lead?: boolean }) {
  return (
    <div className={`ectpr-ed-row${props.lead ? ' ectpr-ed-row--lead' : ''}${props.recorded ? '' : ' ectpr-ed-row--none'}`}>
      <div className="ectpr-ed-label">{props.label}</div>
      <div className="ectpr-ed-value">{props.children}</div>
    </div>
  );
}

export function IfInDoubtBox(props: { lang: CareLang }) {
  const { lang } = props;
  return (
    <section className="ectpr-ed-doubt" aria-label={ifInDoubtTitle(lang)}>
      <h3>{ifInDoubtTitle(lang)}</h3>
      {/* Each line starts with "If" (Sujay, 1 Nov), then the action. */}
      <ol style={{ listStyleType: 'disc' }}>{ifInDoubtConditions(lang).map((c) => <li key={c}>{c}</li>)}</ol>
      <p><strong>{ifInDoubtAfter(lang)}</strong></p>
    </section>
  );
}

export function AlwaysContinues(props: { lang: CareLang }) {
  return (
    <section className="ectpr-ed-always">
      <h3>{careText(props.lang, 'Care that always continues', 'जो देखभाल हमेशा जारी रहती है')}</h3>
      <ul>{alwaysProvidedCare(props.lang).map((c) => <li key={c}>{c}</li>)}</ul>
    </section>
  );
}

/** Spoken summary for read-aloud (same order as the screen). */
export function edSpokenText(record: EctprRecord | null, interp: EdInterpretation, lang: CareLang, amd?: AmdStatusSummary | null): string {
  const t = textFor(lang);
  if (!record || interp.state === 'NONE' || interp.state === 'REVOKED' || interp.state === 'UNSIGNED') return `${edBanner(interp, record, lang)} ${ifInDoubtTitle(lang)}.`;
  const c = record.choices;
  const dm = record.decisionMakers.primary;
  return [
    edBanner(interp, record, lang),
    `${record.patient.name}, UHID ${record.patient.uhid}.`,
    `${t('Hospital transfer', 'अस्पताल ले जाना')}: ${transferLabel(c.hospitalTransfer, lang)}.`,
    `CPR: ${cprLabel(record, lang)}.`,
    `${t('Highest breathing support', 'सबसे ऊँची साँस सहायता')}: ${ceilingLabel(c.respCeiling, lang)}.`,
    `ICU: ${icuLabel(c.icu, lang)}.`,
    `${t('Goal of care', 'देखभाल का लक्ष्य')}: ${goalLabel(c.goalOfCare, lang)}.`,
    `${t('Allergies', 'एलर्जी')}: ${record.clinical.allergies || notRecorded(lang)}.`,
    `${t('Decision-maker', 'निर्णयकर्ता')}: ${dm ? `${dm.name}, ${dm.relation}` : notRecorded(lang)}.`,
    ...edAmdLines(record.existingDirective, amd === undefined ? record.amdAtIssue : amd, lang).lines.map((line) => `${line}.`),
    amdCaveat(lang),
    `${ifInDoubtTitle(lang)}.`,
  ].join(' ');
}

/**
 * The ED Quick View for one record. `record` null = nothing found. `full` adds the break-glass sections.
 * Unsigned plans never show their choices; out-of-date plans show them greyed as information [Sign-off pending];
 * revoked plans show only "card withdrawn". `amd`: the patient's advance care plan status from amd-store; left out on the public
 * /ed page, where the card's own amdAtIssue is used (the AMD itself stays on the issuing device).
 */
export function EctprEdView(props: { record: EctprRecord | null; lang?: CareLang; audience?: EdAudience; full?: boolean; now?: Date; amd?: AmdStatusSummary | null; children?: React.ReactNode }) {
  const lang = props.lang ?? 'en';
  const audience = props.audience ?? 'clinician';
  const t = textFor(lang);
  const record = props.record;
  const interp = edInterpretation(record, props.now ?? new Date());
  const state = interp.state;
  const showChoices = !!record && (state === 'ACTIVE' || state === 'OUT_OF_DATE');
  const informationOnly = state !== 'ACTIVE';
  const consultant = record ? treatingConsultant(record) : null;
  const pending = t('[Sign-off pending]', '[स्वीकृति बाकी]');

  return (
    <div className="ectpr-container" dir={careDir(lang)} lang={lang}>
      <div className={`ectpr-card ectpr-quickview ectpr-ed ectpr-ed--${state.toLowerCase()}`}>
        <div className="ectpr-eyebrow">{t('Emergency care and treatment plan · For the emergency room', 'आपातकालीन देखभाल और इलाज योजना · आपातकालीन कक्ष के लिए')}</div>
        {record && (
          <h2 className="ectpr-heading">{record.patient.name || notRecorded(lang)} <span className="ectpr-ed-uhid">UHID {record.patient.uhid || notRecorded(lang)}</span></h2>
        )}
        {record?.clinical.diagnosis && <div className="ectpr-subline">{record.clinical.diagnosis}{record.clinical.stage ? ` · ${record.clinical.stage}` : ''}</div>}
        {audience !== 'clinician' && (
          <p className="ectpr-ed-audience">
            {audience === 'patient'
              ? t('This is what an emergency doctor sees when they scan your card. What you say at the time always comes first.', 'कार्ड स्कैन करने पर आपातकालीन डॉक्टर यही देखते हैं। उस समय आप जो कहें, वही सबसे पहले मान्य है।')
              : t('This is what an emergency doctor sees when they scan the card. What the patient says at the time always comes first.', 'कार्ड स्कैन करने पर आपातकालीन डॉक्टर यही देखते हैं। उस समय मरीज़ जो कहे, वही सबसे पहले मान्य है।')}
          </p>
        )}

        <div className={`ectpr-ed-banner ectpr-ed-banner--${state.toLowerCase()}${interp.standardCareApplies ? ' is-standard' : ''}`} role="status">
          {state === 'REVOKED' && <strong className="ectpr-ed-withdrawn">{t('CARD WITHDRAWN', 'कार्ड वापस लिया गया')}</strong>}
          <span>{edBanner(interp, record, lang)}</span>
        </div>
        {interp.flags.length > 0 && <ul className="ectpr-ed-flags">{interp.flags.map((f) => <li key={f}>{edFlag(f, lang)}</li>)}</ul>}

        <IfInDoubtBox lang={lang} />

        {state === 'UNSIGNED' && (
          <p className="ectpr-ed-note">{t('The treating consultant has not signed this record, so its choices are not shown here. Ask the treating team.', 'मुख्य डॉक्टर ने इस रिकॉर्ड पर हस्ताक्षर नहीं किए हैं, इसलिए इसके विकल्प यहाँ नहीं दिखाए गए। इलाज करने वाली टीम से पूछें।')}</p>
        )}

        {showChoices && record && (() => {
          const c = record.choices;
          const dm = record.decisionMakers.primary;
          const alt = record.decisionMakers.alternate;
          const decided = cprDecidedBy(record, lang);
          const amdStatus = props.amd === undefined ? record.amdAtIssue : props.amd;
          const amdView = edAmdLines(record.existingDirective, amdStatus, lang);
          return (
            <div className={`ectpr-ed-plan${informationOnly ? ' is-information' : ''}`}>
              {informationOnly && <div className="ectpr-ed-pending">{pending} {t('Shown as information, not instruction.', 'जानकारी के रूप में, निर्देश नहीं।')}</div>}
              <div className="ectpr-ed-lead">
                <Row lead label={t('1 · Hospital transfer', '1 · अस्पताल ले जाना')} recorded={!!c.hospitalTransfer}>
                  {transferLabel(c.hospitalTransfer, lang)}
                  {c.callBeforeAmbulance.filter(Boolean).length > 0 && <div className="ectpr-ed-sub">{t('Call before an ambulance', 'एम्बुलेंस से पहले फ़ोन करें')}: {c.callBeforeAmbulance.filter(Boolean).join('; ')}</div>}
                  {c.ambulance && <div className="ectpr-ed-sub">{t('Ambulance crew', 'एम्बुलेंस टीम')}: {ambulanceLabel(c.ambulance, lang)}</div>}
                </Row>
                <Row lead label={t('2 · CPR', '2 · CPR')} recorded={!!c.cpr}>
                  {cprLabel(record, lang)}
                  {decided && <div className="ectpr-ed-sub">{decided}</div>}
                </Row>
                <Row lead label={t('3 · Breathing support ceiling', '3 · साँस सहायता की सीमा')} recorded={c.respCeiling !== null}>
                  {ceilingLabel(c.respCeiling, lang)}
                  {ceilingDetails(record, lang).map((d) => <div className="ectpr-ed-sub" key={d}>{d}</div>)}
                </Row>
                <Row lead label={t('4 · ICU', '4 · ICU')} recorded={!!c.icu}>{icuLabel(c.icu, lang)}</Row>
              </div>
              <Row label={t('5 · Goal of care', '5 · देखभाल का लक्ष्य')} recorded={!!c.goalOfCare}>{goalLabel(c.goalOfCare, lang)}</Row>
              <Row label={t('6 · Allergies', '6 · एलर्जी')} recorded={!!record.clinical.allergies}>{record.clinical.allergies || notRecorded(lang)}</Row>
              <Row label={t('7 · Implanted device', '7 · लगा हुआ उपकरण')} recorded={!!record.clinical.implantedDevice}>{deviceLabel(record, lang)}</Row>
              <Row label={t('8 · Decision-maker (24 h)', '8 · निर्णयकर्ता (24 घंटे)')} recorded={!!dm}>
                {dm ? <>{dm.name} · {dm.relation}{authorityLabel(dm.authorityBasis, lang) ? ` · ${authorityLabel(dm.authorityBasis, lang)}` : ''} · <Phone phone={dm.phone24h} lang={lang} /></> : notRecorded(lang)}
                {alt && <div className="ectpr-ed-sub">{t('Secondary', 'द्वितीय')}: {alt.name} · {alt.relation} · <Phone phone={alt.phone24h} lang={lang} /></div>}
              </Row>
              <Row label={t('9 · Treating consultant · palliative care', '9 · मुख्य डॉक्टर · पैलिएटिव केयर')} recorded={!!consultant || !!record.careTeam.palliativePhysician}>
                <div>{t('Consultant', 'डॉक्टर')}: {consultant ? `${consultant.name} (${t('Reg.', 'पंजी.')} ${consultant.registrationNo})` : record.careTeam.oncologist || notRecorded(lang)}</div>
                <div>{t('Palliative care', 'पैलिएटिव केयर')}: {record.careTeam.palliativePhysician || notRecorded(lang)} · <Phone phone={record.careTeam.palliativePhone24h} lang={lang} /></div>
              </Row>
              {homeCareLines(record, lang).length > 0 && <Row label={t('Home care', 'घर पर देखभाल')} recorded>{homeCareLines(record, lang).map((line) => <div key={line}>{line}</div>)}</Row>}
              <Row label={t('10 · Capacity', '10 · निर्णय क्षमता')} recorded={!!record.capacity.status}>
                {record.capacity.status === 'FLUCTUATES'
                  ? <strong className="ectpr-ed-alert">{t('Capacity fluctuates — reassess', 'निर्णय क्षमता बदलती है — दोबारा जाँचें')}{record.capacity.reassessOn ? ` (${formatDay(record.capacity.reassessOn, lang)})` : ''}</strong>
                  : record.capacity.status === 'LACKS' ? t('Lacks capacity for these decisions — use the decision-maker', 'इन निर्णयों की क्षमता नहीं — निर्णयकर्ता से पूछें')
                  : record.capacity.status ? t('Has capacity — ask the patient; what they say now comes first', 'क्षमता है — मरीज़ से पूछें; उनकी अभी की बात सबसे पहले')
                  : notRecorded(lang)}
              </Row>
              <Row label={t('11 · Advance care planning (AMD)', '11 · अग्रिम देखभाल योजना (AMD)')} recorded={!!record.existingDirective || !!amdStatus}>
                {amdView.lines.map((line, index) => <div key={line} className={amdView.governs && index === amdView.lines.length - 1 ? 'ectpr-ed-alert' : undefined}>{line}</div>)}
                <div className="ectpr-ed-caveat">{amdCaveat(lang)}</div>
              </Row>
              <Row label={t('12 · Version · signed · review due', '12 · संस्करण · हस्ताक्षर · समीक्षा')} recorded>
                {t('Version', 'संस्करण')} {record.version} · {record.formVersion} · {t('signed', 'हस्ताक्षर')} {consultant ? formatDay(consultant.signedAt, lang) : t('not signed', 'नहीं')} · <strong>{t('review due', 'समीक्षा')} {formatDay(record.reviewDue, lang)}</strong>
              </Row>
            </div>
          );
        })()}

        {state === 'UNSIGNED' && record && (
          <div className="ectpr-ed-plan is-information">
            <Row label={t('Decision-maker (24 h)', 'निर्णयकर्ता (24 घंटे)')} recorded={!!record.decisionMakers.primary}>
              {record.decisionMakers.primary ? <>{record.decisionMakers.primary.name} · {record.decisionMakers.primary.relation} · <Phone phone={record.decisionMakers.primary.phone24h} lang={lang} /></> : notRecorded(lang)}
            </Row>
            <Row label={t('Palliative care', 'पैलिएटिव केयर')} recorded={!!record.careTeam.palliativePhysician}>
              {record.careTeam.palliativePhysician || notRecorded(lang)} · <Phone phone={record.careTeam.palliativePhone24h} lang={lang} />
            </Row>
          </div>
        )}

        {props.full && record && showChoices && <EctprFullRecord record={record} lang={lang} />}
        {props.children}

        {state !== 'REVOKED' && <AlwaysContinues lang={lang} />}
        {showChoices && <p className="ectpr-ed-dying">{dyingPhasePrompt(lang)}</p>}
        <div className="ectpr-footer">
          {t('A patient with capacity can change this plan at any time. Unticked items mean no preference recorded — give standard care, never read as refusal.', 'क्षमता वाला मरीज़ कभी भी योजना बदल सकता है। बिना निशान वाले विकल्प का अर्थ है कोई पसंद दर्ज नहीं — सामान्य इलाज दें, इसे मना करना न समझें।')}
        </div>
      </div>
    </div>
  );
}

/** Break-glass sections: the rest of the signed record (only after a typed reason has been logged). */
export function EctprFullRecord(props: { record: EctprRecord; lang?: CareLang }) {
  const lang = props.lang ?? 'en';
  const t = textFor(lang);
  const r = props.record;
  const c = r.choices;
  const dms = [r.decisionMakers.primary, r.decisionMakers.alternate].filter((d): d is NonNullable<typeof d> => !!d);
  return (
    <section className="ectpr-ed-full" aria-label={t('Full record', 'पूरा रिकॉर्ड')}>
      <h3>{t('Full record (break-glass)', 'पूरा रिकॉर्ड (ब्रेक-ग्लास)')}</h3>
      <Row label={t('Vasopressors', 'वैसोप्रेसर')} recorded={!!c.vasopressors}>{vasopressorLabel(c.vasopressors, lang)}</Row>
      <Row label={t('Dialysis', 'डायलिसिस')} recorded={!!c.dialysis}>{dialysisLabel(c.dialysis, lang)}</Row>
      <Row label={t('Feeding', 'खाना')} recorded={!!c.feeding.ng || !!c.feeding.peg}>
        {t('Comfort feeding by mouth always continues.', 'मुँह से आराम के लिए खिलाना हमेशा जारी रहेगा।')} NG: {ngLabel(c.feeding.ng, lang)} · PEG: {pegLabel(c.feeding.peg, lang)}
        {c.feeding.removeExistingTube && <div className="ectpr-ed-alert">{t('Wish to remove an existing tube: withdrawal — medical board pathway only.', 'लगी ट्यूब हटाने की इच्छा: यह इलाज रोकना है — केवल मेडिकल बोर्ड प्रक्रिया से।')}</div>}
      </Row>
      <Row label={t('Hydration', 'तरल')} recorded={!!c.hydration}>{hydrationLabel(c.hydration, lang)}</Row>
      <Row label={t('Key medicines', 'मुख्य दवाएँ')} recorded={!!r.clinical.keyMedicines}>{r.clinical.keyMedicines || notRecorded(lang)}</Row>
      <Row label={t('Patient', 'मरीज़')} recorded>
        {[r.patient.dob && `${t('DOB', 'जन्म')} ${formatDay(r.patient.dob, lang)}`, r.patient.sex, r.patient.gender, r.patient.address, r.patient.phone && <Phone key="p" phone={r.patient.phone} lang={lang} />].filter(Boolean).map((x, i) => <span key={i}>{i > 0 ? ' · ' : ''}{x}</span>)}
        {r.clinical.ecog !== null && <div className="ectpr-ed-sub">ECOG {r.clinical.ecog}</div>}
      </Row>
      <Row label={t('Decision-makers', 'निर्णयकर्ता')} recorded={dms.length > 0}>
        {dms.length ? dms.map((d) => <div key={d.name}>{d.name} · {d.relation} · <Phone phone={d.phone24h} lang={lang} />{d.idType ? ` · ${d.idType} …${d.idLast4 ?? ''}` : ''}{authorityLabel(d.authorityBasis, lang) ? ` · ${authorityLabel(d.authorityBasis, lang)}` : ''}</div>) : notRecorded(lang)}
        {r.decisionMakers.familyToInform.length > 0 && <div className="ectpr-ed-sub">{t('Family to inform', 'जिन्हें बताना है')}: {r.decisionMakers.familyToInform.join(', ')}</div>}
        {(r.decisionMakers.presentAtDiscussion ?? []).length > 0 && <div className="ectpr-ed-sub">{t('Present for the discussion', 'बातचीत में मौजूद')}: {r.decisionMakers.presentAtDiscussion!.join(', ')}</div>}
        {(r.decisionMakers.attemptedContact ?? []).length > 0 && <div className="ectpr-ed-sub">{t('Could not be reached', 'संपर्क नहीं हो सका')}: {r.decisionMakers.attemptedContact!.join(', ')}</div>}
        {r.decisionMakers.familyConsensusNote && <div className="ectpr-ed-sub">{t('Family note', 'परिवार का नोट')}: {r.decisionMakers.familyConsensusNote}</div>}
      </Row>
      <Row label={t('Capacity assessment', 'क्षमता की जाँच')} recorded={!!r.capacity.status}>
        {[r.capacity.assessor, r.capacity.assessedOn && formatDay(r.capacity.assessedOn, lang), r.capacity.basis].filter(Boolean).join(' · ') || notRecorded(lang)}
      </Row>
      <Row label={t("Patient's own words", 'मरीज़ के अपने शब्द')} recorded={!!r.values.ownWords}>
        {r.values.ownWords ? <q>{r.values.ownWords}</q> : notRecorded(lang)}
        {r.values.knowsRecordedWhen && <div className="ectpr-ed-sub">{t('When was this recorded?', 'यह कब दर्ज किया गया?')} {r.values.knowsRecordedWhen === 'BEFORE_COUNSELLING' ? t('Before counselling', 'काउंसलिंग से पहले') : t('After counselling', 'काउंसलिंग के बाद')}</div>}
        {r.values.unacceptableOutcomes.length > 0 && <div className="ectpr-ed-sub">{t('Would not accept', 'स्वीकार नहीं')}: {r.values.unacceptableOutcomes.join('; ')}</div>}
      </Row>
      <Row label={t('Signatures', 'हस्ताक्षर')} recorded={r.signatures.length > 0}>
        {r.signatures.length ? r.signatures.map((s, i) => <div key={i}>{s.role.replace(/_/g, ' ').toLowerCase()}{s.surrogateRank ? ` (${s.surrogateRank.toLowerCase()})` : ''}: {s.name}{s.registrationNo ? ` (${t('Reg.', 'पंजी.')} ${s.registrationNo})` : ''} · {formatDay(s.signedAt, lang)}</div>) : notRecorded(lang)}
      </Row>
    </section>
  );
}
