'use client';

import { useEffect, useId, useState } from 'react';
import { careLocale, careText, type CareLang } from './care-i18n';
import {
  JOURNAL_MAX_CHARS, MAPS_URL, SUPPORT_GROUP_SOULUP, SUPPORT_GROUP_SWAYAM, TELE_MANAS_HREF, ZBI_ANSWERS_EN, ZBI_ANSWERS_HI,
  ZBI_ITEM_MAX, ZBI_MAX_SCORE, ZBI_SOURCE_EN, ZBI_SOURCE_HI, addCheck, addNote, isHighBurden, loadHistory, loadJournal, recentChecks,
  saveHistory, saveJournal, shouldFlag, wellbeingStoreKey, wellbeingTrend, zbiScore, type JournalNote, type WellbeingEntry,
} from './caregiver-wellbeing-state';
import {
  CAREGIVER_CHECK_KEY, DISTRESS_SCORES, DISTRESS_TALK_FROM, distressBandText, distressColour, loadDistress, saveDistress,
} from './distress-check-state';
import './caregiver-wellbeing.css';

type Props = { patientId: string; personName: string; author: string; today: string; lang: CareLang };

/** "You matter too": a short, warm screen shown once after a family member finishes logging symptoms. */
export function CaregiverYouMatter({ lang, onDone, onCheck }: { lang: CareLang; onDone: () => void; onCheck: () => void }) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  return <section className="caregiver-you-matter" aria-labelledby="caregiver-you-matter-title" data-testid="caregiver-you-matter">
    <span className="caregiver-heart" aria-hidden="true">♥</span>
    <h2 id="caregiver-you-matter-title">{t('You matter, and your health matters too.', 'आप मायने रखते हैं, और आपकी सेहत भी मायने रखती है।')}</h2>
    <p>{t('Thank you for caring so well. Have you had some water, something to eat, and a moment to sit down today? Looking after yourself is part of looking after them.',
      'इतने प्यार से देखभाल करने के लिए धन्यवाद। आज आपने पानी पिया, कुछ खाया, और थोड़ी देर बैठकर आराम किया? अपना ध्यान रखना भी उनकी देखभाल का हिस्सा है।')}</p>
    <button type="button" className="caregiver-primary" onClick={onCheck} data-testid="caregiver-you-matter-check">{t('Check how I am (1 minute)', 'अपना हाल जाँचें (1 मिनट)')}</button>
    <button type="button" className="caregiver-text-button" onClick={onDone} data-testid="caregiver-you-matter-done">{t('Done', 'हो गया')}</button>
  </section>;
}

/** "Support for you": always on the family home. Calm and folded when the check is fine; open, red and flagged when it is high or rising. */
export function CaregiverSupportCard({ lang, flagged }: { lang: CareLang; flagged: boolean }) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const [openState, setOpenState] = useState<{ flagged: boolean; open: boolean }>({ flagged, open: flagged });
  // A new flag opens the card; a cleared flag folds it again.
  const open = openState.flagged === flagged ? openState.open : flagged;
  const bodyId = useId();
  return <section className={`caregiver-support${flagged ? ' is-flagged' : ''}`} data-testid={flagged ? 'caregiver-flag' : 'caregiver-support'}>
    <h3><button type="button" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpenState({ flagged, open: !open })}>
      <span>{flagged ? t('You’re carrying a lot', 'आप बहुत कुछ उठा रहे हैं') : t('Support for you', 'आपके लिए मदद')}</span>
      <span aria-hidden="true">{open ? '▴' : '▾'}</span>
    </button></h3>
    {!open && <p className="caregiver-sub">{t('Psychiatrists and counsellors, respite care and caregiver support groups.', 'मनोचिकित्सक और काउंसलर, आराम के लिए देखभाल और देखभालकर्ता सहायता समूह।')}</p>}
    {open && <div id={bodyId} className="caregiver-support-body">
      {flagged && <p>{t('Many family caregivers feel this way. It is not a failing, and help is there. This check is a screening, not a diagnosis.',
        'कई परिवारजन ऐसा ही महसूस करते हैं। यह कोई कमी नहीं है, और मदद उपलब्ध है। यह जाँच सिर्फ़ एक शुरुआती संकेत है, निदान नहीं।')}</p>}
      <h4>{t('Talk to a psychiatrist or counsellor', 'मनोचिकित्सक या काउंसलर से बात करें')}</h4>
      <p className="caregiver-sub">{t('Tele-MANAS is free, day and night.', 'टेली-मानस मुफ़्त है, दिन-रात।')}</p>
      <a className="caregiver-primary" href={TELE_MANAS_HREF} data-testid="caregiver-flag-call">{t('Call Tele-MANAS 14416', 'टेली-मानस 14416 पर कॉल करें')}</a>
      <a className="caregiver-secondary" href={MAPS_URL} target="_blank" rel="noreferrer" data-testid="caregiver-flag-nearby">{t('Find a psychiatrist or counsellor nearby', 'पास में मनोचिकित्सक या काउंसलर खोजें')}</a>
      <h4>{t('Respite care', 'कुछ समय का आराम (रेस्पाइट केयर)')}</h4>
      <p>{t('Your palliative care team can arrange temporary nursing or day-care support, so you can rest. Ask them at your next call or visit.',
        'आपकी पैलिएटिव केयर टीम कुछ समय के लिए नर्सिंग या डे-केयर सहायता का इंतज़ाम कर सकती है, ताकि आप आराम कर सकें। अगली कॉल या मुलाक़ात में उनसे पूछें।')}</p>
      <h4>{t('Caregiver support groups', 'देखभालकर्ता सहायता समूह')}</h4>
      <a className="caregiver-secondary" href={SUPPORT_GROUP_SWAYAM} target="_blank" rel="noreferrer" data-testid="caregiver-flag-swayam">{t('Swayam Foundation caregiver support', 'स्वयं फ़ाउंडेशन देखभालकर्ता सहायता')}</a>
      <a className="caregiver-secondary" href={SUPPORT_GROUP_SOULUP} target="_blank" rel="noreferrer" data-testid="caregiver-flag-soulup">{t('SoulUp caregiving support group', 'सोलअप देखभाल सहायता समूह')}</a>
      <p className="caregiver-sub">{t('These open in your browser. In an emergency, call 112.', 'ये आपके ब्राउज़र में खुलते हैं। आपात स्थिति में 112 पर कॉल करें।')}</p>
    </div>}
  </section>;
}

function WellbeingTrend({ history, lang }: { history: WellbeingEntry[]; lang: CareLang }) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const shown = recentChecks(history);
  if (shown.length < 2) return null;
  const summary = shown.map((entry) => entry.score).join(', ');
  const trend = wellbeingTrend(history);
  const label = trend === 'rising' ? t('Higher than your last check', 'पिछली जाँच से ज़्यादा')
    : trend === 'falling' ? t('Lower than your last check', 'पिछली जाँच से कम') : t('About the same as your last check', 'पिछली जाँच जैसा ही');
  return <div className="caregiver-trend" data-testid="wellbeing-trend">
    <div className="caregiver-trend-bars" role="img" aria-label={t(`Past checks, oldest first: ${summary} out of ${ZBI_MAX_SCORE}`, `पिछली जाँचें, पुरानी पहले: ${summary}, कुल ${ZBI_MAX_SCORE} में से`)}>
      {shown.map((entry) => <span key={entry.day} className={isHighBurden(entry.score) ? 'is-high' : ''} style={{ height: `${6 + (42 * entry.score) / ZBI_MAX_SCORE}px` }} />)}
    </div>
    <p className="caregiver-sub">{label}</p>
  </div>;
}

/** The "Your wellbeing" card on the family home: ZBI-4 check with a small trend, the support card, and a private journal. */
export function CaregiverWellbeingCard({ patientId, personName, author, today, lang }: Props) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const key = wellbeingStoreKey(author, patientId);
  const [history, setHistory] = useState<WellbeingEntry[]>([]);
  const [journal, setJournal] = useState<JournalNote[]>([]);
  const [checking, setChecking] = useState(false);
  const [writing, setWriting] = useState(false);
  const [answers, setAnswers] = useState<(number | null)[]>([null, null, null, null]);
  const [draft, setDraft] = useState('');
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the private check and journal hydrate from this browser after mount.
    setHistory(loadHistory(window.localStorage, key)); setJournal(loadJournal(window.localStorage, key));
    setChecking(false); setWriting(false); setAnswers([null, null, null, null]); setDraft('');
  }, [key]);
  const latest = [...history].sort((a, b) => a.day.localeCompare(b.day)).pop();
  const flagged = shouldFlag(history);
  const total = zbiScore(answers);
  const name = personName.trim().split(/\s+/)[0] || t('them', 'उन');
  const questions = [
    t(`Stressed, between caring for ${name} and meeting your other responsibilities?`, `${name} की देखभाल और अपनी दूसरी ज़िम्मेदारियों के बीच तनाव?`),
    t(`Strained when you are around ${name}?`, `${name} के पास होने पर दबाव या खिंचाव?`),
    t(`That your own health has suffered because of caring for ${name}?`, `${name} की देखभाल से आपकी अपनी सेहत बिगड़ी है?`),
    t(`That you don't have enough time for yourself because of caring for ${name}?`, `${name} की देखभाल के कारण अपने लिए समय नहीं मिलता?`),
  ];
  const answerLabels = lang === 'hi' ? ZBI_ANSWERS_HI : ZBI_ANSWERS_EN;
  const timeFormat = new Intl.DateTimeFormat(careLocale(lang), { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });

  function saveCheck() {
    if (total === null) return;
    const next = addCheck(history, { day: today, score: total });
    setHistory(next); saveHistory(window.localStorage, key, next);
    setChecking(false); setAnswers([null, null, null, null]);
  }
  function saveNote() {
    const next = addNote(journal, Date.now(), draft);
    setJournal(next); saveJournal(window.localStorage, key, next);
    setDraft(''); setWriting(false);
  }

  return <section className="caregiver-wellbeing" aria-labelledby={`wellbeing-${key}`} data-testid="caregiver-wellbeing">
    <header>
      <span className="caregiver-heart" aria-hidden="true">♥</span>
      <div>
        <h2 id={`wellbeing-${key}`}>{t('Your wellbeing', 'आपकी सेहत')}</h2>
        <p className="caregiver-sub">{t('Kept on this device only. Not shared.', 'सिर्फ़ इसी डिवाइस पर। साझा नहीं होता।')}</p>
      </div>
    </header>
    {!latest
      ? <p>{t('Caring is hard work. A one-minute check helps you notice when you need support.', 'देखभाल करना मेहनत का काम है। एक मिनट की जाँच से पता चलता है कि आपको कब सहारे की ज़रूरत है।')}</p>
      : <>
        <p className="caregiver-pill">{t(`Last check: ${latest.score}/${ZBI_MAX_SCORE} · ${isHighBurden(latest.score) ? 'A lot to carry' : 'Manageable'}`,
          `पिछली जाँच: ${latest.score}/${ZBI_MAX_SCORE} · ${isHighBurden(latest.score) ? 'बहुत बोझ' : 'संभल रहा है'}`)}</p>
        <WellbeingTrend history={history} lang={lang} />
      </>}
    {checking
      ? <form className="caregiver-check" onSubmit={(event) => { event.preventDefault(); saveCheck(); }}>
        <p><strong>{t('In the past month, how often have you felt this way?', 'पिछले एक महीने में, आपको कितनी बार ऐसा लगा?')}</strong></p>
        {questions.map((question, index) => <fieldset key={question}>
          <legend>{question}</legend>
          <div className="caregiver-choices">{Array.from({ length: ZBI_ITEM_MAX + 1 }, (_, value) => <button key={value} type="button" aria-pressed={answers[index] === value} data-testid={`wellbeing-${index}-${value}`}
            onClick={() => setAnswers((previous) => previous.map((item, position) => (position === index ? value : item)))}>{answerLabels[value]}</button>)}</div>
        </fieldset>)}
        <button type="submit" className="caregiver-primary" disabled={total === null} data-testid="wellbeing-save">{t('Save', 'सहेजें')}</button>
        {total === null && <p className="caregiver-sub">{t('Answer all four to save.', 'सहेजने के लिए चारों के जवाब दें।')}</p>}
        <button type="button" className="caregiver-text-button" onClick={() => setChecking(false)}>{t('Not now', 'अभी नहीं')}</button>
      </form>
      : <button type="button" className="caregiver-primary" onClick={() => setChecking(true)} data-testid="wellbeing-start">{latest ? t('Check again', 'फिर से जाँचें') : t('Take the 1-minute check', '1 मिनट की जाँच करें')}</button>}
    <p className="caregiver-source" data-testid="wellbeing-source">{t(ZBI_SOURCE_EN, ZBI_SOURCE_HI)}</p>
    {!checking && <CaregiverSupportCard lang={lang} flagged={flagged} />}
    <div className="caregiver-journal">
      <p><strong>{t(`How are you feeling about caring for ${name}?`, `${name} की देखभाल को लेकर आप कैसा महसूस कर रहे हैं?`)}</strong></p>
      {writing
        ? <>
          <textarea rows={3} value={draft} maxLength={JOURNAL_MAX_CHARS} onChange={(event) => setDraft(event.target.value)} aria-label={t('Journal note', 'डायरी का नोट')}
            placeholder={t('Write a few words. Only you can see this.', 'कुछ शब्द लिखें। इसे सिर्फ़ आप देख सकते हैं।')} data-testid="journal-text" />
          <button type="button" className="caregiver-primary" disabled={!draft.trim()} onClick={saveNote} data-testid="journal-save">{t('Save note', 'नोट सहेजें')}</button>
          <button type="button" className="caregiver-text-button" onClick={() => setWriting(false)}>{t('Cancel', 'रद्द करें')}</button>
        </>
        : <button type="button" className="caregiver-secondary" onClick={() => setWriting(true)} data-testid="journal-write">{t('Write in my journal', 'अपनी डायरी में लिखें')}</button>}
      {journal.length > 0 && <>
        <p className="caregiver-sub">{t('Only you can see these, on this device.', 'इन्हें सिर्फ़ आप देख सकते हैं, इसी डिवाइस पर।')}</p>
        {journal.slice(0, 5).map((note) => <article key={note.at} className="caregiver-note"><time dateTime={new Date(note.at).toISOString()}>{timeFormat.format(note.at)}</time><p>{note.text}</p></article>)}
      </>}
    </div>
  </section>;
}

/** The 0-10 distress row with None under 0 and Extreme under 10. All eleven numbers sit on ONE row. */
export function DistressScoreRow({ value, lang, onPick, name }: { value: number | null; lang: CareLang; onPick: (score: number) => void; name: string }) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  return <div className="distress-row-wrap">
    <div className="distress-row" role="radiogroup" aria-label={name}>
      {DISTRESS_SCORES.map((score) => {
        const chosen = value === score;
        return <button key={score} type="button" role="radio" aria-checked={chosen} aria-label={`${score}/10`} data-testid={`family-check-${score}`}
          style={{ ['--distress' as string]: distressColour(score) }} className={chosen ? 'is-chosen' : ''} onClick={() => onPick(score)}><span>{score}</span></button>;
      })}
    </div>
    <div className="distress-ends"><span>{t('None', 'कोई नहीं')}</span><span>{t('Extreme', 'बहुत ज़्यादा')}</span></div>
  </div>;
}

/** "How are you holding up?": the caregiver's own 0-10 distress check. Not the patient's, and not shared. */
export function FamilyDistressCard({ patientId, author, today, lang }: Omit<Props, 'personName'>) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const key = CAREGIVER_CHECK_KEY(wellbeingStoreKey(author, patientId));
  const [saved, setSaved] = useState<number | null>(null);
  const [picking, setPicking] = useState(false);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- today's private score hydrates from this browser after mount.
    setSaved(loadDistress(window.localStorage, key, today)); setPicking(false);
  }, [key, today]);
  const choosing = saved === null || picking;
  return <section className="caregiver-wellbeing caregiver-distress" aria-labelledby={`holding-${key}`} data-testid="family-caregiver-check">
    <header>
      <span className="caregiver-heart" aria-hidden="true">♥</span>
      <div>
        <h2 id={`holding-${key}`}>{t('How are you holding up?', 'आप ख़ुद कैसे हैं?')}</h2>
        {saved !== null && <p className="caregiver-pill">{t(`Today: ${saved}/10 · ${distressBandText(saved, t)}`, `आज: ${saved}/10 · ${distressBandText(saved, t)}`)}</p>}
      </div>
    </header>
    {choosing
      ? <>
        <p><strong>{t('Distress this past week, including today', 'पिछले हफ़्ते, आज समेत, परेशानी')}</strong></p>
        <DistressScoreRow value={saved} lang={lang} name={t('Distress this past week, including today', 'पिछले हफ़्ते, आज समेत, परेशानी')}
          onPick={(score) => { saveDistress(window.localStorage, key, today, score); setSaved(score); setPicking(false); }} />
      </>
      : <>
        <div className="distress-bar" role="img" aria-label={`${saved}/10`}><span style={{ width: `${(saved ?? 0) * 10}%`, background: distressColour(saved ?? 0) }} /></div>
        {(saved ?? 0) >= DISTRESS_TALK_FROM && <p>{t('Tell your care team how you are.', 'देखभाल टीम को अपना हाल बताएँ।')}</p>}
        <button type="button" className="caregiver-text-button" onClick={() => setPicking(true)} data-testid="family-check-change">{t('Change', 'बदलें')}</button>
      </>}
  </section>;
}
