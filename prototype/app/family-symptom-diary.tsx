'use client';

import { useId, useRef, useState, type FormEvent } from 'react';
import {
  SYMPTOM_SEVERITIES, addSymptomEntry, updateSymptomEntry, removeSymptomEntry,
  restoreSymptomEntry, selectSymptomEntries, symptomDiaryText, symptomSeverityText, validateSymptomInput,
  SYMPTOM_SCALE, symptomComparison, symptomRecallText, symptomRespondentText, symptomChangeText, painTriggerText,
  type SymptomEntry, type SymptomRange, type SymptomSeverity, type SymptomRespondent, type SymptomRecall, type SymptomChange, type PainTrigger,
} from './family-symptom-state';
import type { CareSaveStatus } from './care-local-store';
import './family-symptom-diary.css';
import { careLocale, careText, type CareLang } from './care-i18n';

export type FamilySymptomDiaryProps = {
  patientId: string;
  author: string;
  today: string;
  lang: CareLang;
  entries: SymptomEntry[];
  onChange: (entries: SymptomEntry[]) => void;
  saveStatus?: CareSaveStatus;
};

const symptoms = [
  ['Pain', 'दर्द'], ['Nausea', 'जी मिचलाना'], ['Tiredness', 'थकान'], ['Low appetite', 'भूख कम लगना'],
  ['Sleep trouble', 'नींद की परेशानी'], ['Breathlessness', 'साँस फूलना'], ['Low mood', 'मन उदास होना'],
  ['Drowsiness', 'उनींदापन'], ['Anxiety', 'घबराहट'], ['Overall discomfort', 'कुल मिलाकर परेशानी'], ['Other', 'कुछ और'],
] as const;

const clockTime = () => new Date().toTimeString().slice(0, 5);

export function FamilySymptomDiary(props: FamilySymptomDiaryProps) {
  return <SymptomDiaryForPatient key={props.patientId} {...props} />;
}

function SymptomDiaryForPatient({ patientId, author, today, lang, entries, onChange, saveStatus }: FamilySymptomDiaryProps) {
  const id = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const [selectedSymptoms, setSelectedSymptoms] = useState<string[]>([]);
  const [customSymptom, setCustomSymptom] = useState('');
  const [symptomSeverities, setSymptomSeverities] = useState<Partial<Record<string, SymptomSeverity>>>({});
  const [date, setDate] = useState(today);
  const [time, setTime] = useState(clockTime);
  const [respondent, setRespondent] = useState<SymptomRespondent | undefined>();
  const [recall, setRecall] = useState<SymptomRecall | undefined>('now');
  const [ownChanges, setOwnChanges] = useState<Partial<Record<string, SymptomChange>>>({});
  const [painLocation, setPainLocation] = useState('');
  const [painTriggers, setPainTriggers] = useState<PainTrigger[]>([]);
  const [startedOn, setStartedOn] = useState('');
  const [note, setNote] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [range, setRange] = useState<SymptomRange>('all');
  const [filter, setFilter] = useState('');
  const [removed, setRemoved] = useState<SymptomEntry[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const patientEntries = selectSymptomEntries(entries, patientId, today);
  const entryAuthor = entries.find((entry) => entry.id === editingId && entry.patientId === patientId)?.author ?? author;
  const visibleEntries = selectSymptomEntries(entries, patientId, today, range, filter);
  const knownSymptoms = [...new Set(patientEntries.map((entry) => entry.symptom))].sort();
  const symptomLabel = (value: string) => careText(lang, value, symptoms.find(([name]) => name === value)?.[1] ?? value);
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(careLocale(lang), { day: 'numeric', month: 'short', year: 'numeric' });

  function clearForm() {
    setSelectedSymptoms([]); setCustomSymptom(''); setSymptomSeverities({});
    setDate(today); setTime(clockTime()); setStartedOn(''); setNote(''); setEditingId(null); setError('');
    setOwnChanges({}); setPainLocation(''); setPainTriggers([]);
  }

  function toggleSymptom(value: string) {
    if (editingId) {
      if (!selectedSymptoms.includes(value)) setSymptomSeverities({});
      setSelectedSymptoms([value]); setError(''); return;
    }
    const next = selectedSymptoms.includes(value) ? selectedSymptoms.filter((item) => item !== value) : [...selectedSymptoms, value];
    if (!next.includes(value)) {
      setSymptomSeverities((previous) => { const updated = { ...previous }; delete updated[value]; return updated; });
    }
    setSelectedSymptoms(next); setError('');
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const recordedAt = new Date().toISOString();
    const inputs = selectedSymptoms.map((value) => {
      const input = {
      symptom: value === 'Other' ? customSymptom.trim() : value,
      severity: symptomSeverities[value] as SymptomSeverity,
      date, time, startedOn, note, scale: SYMPTOM_SCALE, recall, respondent,
      ...(value === 'Pain' ? { painLocation, painTriggers } : {}),
      };
      const comparison = symptomComparison(entries, { ...input, id: editingId ?? '', patientId, author: entryAuthor });
      return { ...input, ...(ownChanges[value] && comparison ? { selfReportedChange: ownChanges[value], comparedWithId: comparison.previous.id } : {}) };
    });
    const invalid = inputs.map((input) => validateSymptomInput(input, today)).find(Boolean);
    const errors = {
      symptom: t('Choose what is bothering you, or write it in 80 characters or fewer.', 'परेशानी चुनें, या 80 अक्षरों तक में लिखें।'),
      severity: t('Choose a number from 0 to 10 for each symptom.', 'हर परेशानी के लिए 0 से 10 तक का अंक चुनें।'),
      date: t('Choose a real date, today or earlier.', 'आज या उससे पहले की सही तारीख चुनें।'),
      startedOn: t('The start date must be on or before the day of this note.', 'शुरू होने की तारीख इस नोट की तारीख या उससे पहले होनी चाहिए।'),
      note: t('Keep the note within 500 characters.', 'नोट 500 अक्षरों तक रखें।'),
      context: t('Choose who reported this, the time and the time frame.', 'किसने बताया, समय और समय अवधि चुनें।'),
      pain: t('Keep the pain location within 100 characters.', 'दर्द की जगह 100 अक्षरों तक लिखें।'),
      comparison: t('Choose a comparison with the previous matching note.', 'पिछले मिलते-जुलते नोट के मुकाबले बदलाव चुनें।'),
    };
    if (!inputs.length || invalid) { setError(errors[invalid || 'symptom']); return; }
    if (new Set(inputs.map((input) => input.symptom.toLowerCase())).size !== inputs.length) {
      setError(t('That symptom is already selected. Use a different name for Other.', 'यह परेशानी पहले से चुनी है। कुछ और के लिए अलग नाम लिखें।')); return;
    }
    let updated = entries;
    if (editingId) updated = updateSymptomEntry(entries, patientId, editingId, inputs[0], author, today);
    else {
      for (const input of inputs) {
        const next = addSymptomEntry(updated, { ...input, id: crypto.randomUUID(), patientId, author, recordedAt }, today);
        if (next === updated) { setError(t('These symptoms could not be saved. Try again.', 'परेशानियाँ सेव नहीं हुईं। फिर से कोशिश करें।')); return; }
        updated = next;
      }
    }
    if (updated === entries) { setError(t('This note could not be saved. Try adding it again.', 'यह नोट सेव नहीं हुआ। फिर से जोड़ें।')); return; }
    onChange(updated);
    setMessage(editingId ? t('Entry updated.', 'नोट बदल गया।') : inputs.length > 1 ? t(`${inputs.length} symptom entries added.`, `${inputs.length} परेशानियों के नोट जुड़ गए।`) : t('Entry added.', 'नोट जुड़ गया।'));
    setRange('all'); setFilter(''); clearForm();
  }

  function edit(entry: SymptomEntry) {
    const known = symptoms.some(([value]) => value !== 'Other' && value === entry.symptom);
    setSelectedSymptoms([known ? entry.symptom : 'Other']); setCustomSymptom(known ? '' : entry.symptom);
    setSymptomSeverities({ [known ? entry.symptom : 'Other']: entry.severity });
    setDate(entry.date); setStartedOn(entry.startedOn); setNote(entry.note); setEditingId(entry.id); setError(''); setMessage('');
    setTime(entry.time ?? ''); setRecall(entry.recall); setRespondent(entry.respondent);
    setPainLocation(entry.painLocation ?? ''); setPainTriggers(entry.painTriggers ?? []);
    setOwnChanges(entry.selfReportedChange ? { [known ? entry.symptom : 'Other']: entry.selfReportedChange } : {});
    formRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' });
    formRef.current?.focus({ preventScroll: true });
  }

  function remove(entry: SymptomEntry) {
    onChange(removeSymptomEntry(entries, patientId, entry.id));
    setRemoved((previous) => [...previous, entry]);
    if (editingId === entry.id) clearForm();
    setMessage(t('Note removed.', 'नोट हटा दिया।'));
    if (filter && patientEntries.filter((item) => item.symptom === filter).length === 1) setFilter('');
  }

  function undo() {
    const entry = removed[removed.length - 1];
    if (!entry) return;
    const restored = restoreSymptomEntry(entries, patientId, entry, today);
    if (restored === entries) { setMessage(t('That note could not be restored.', 'वह नोट वापस नहीं आया।')); return; }
    onChange(restored); setRemoved((previous) => previous.slice(0, -1)); setRange('all'); setFilter('');
    setMessage(t('Note restored.', 'नोट वापस आ गया।'));
  }

  function download() {
    const url = URL.createObjectURL(new Blob([symptomDiaryText(entries, patientId, today, lang)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `sahara-feeling-notes-${today}.txt`; link.click(); URL.revokeObjectURL(url);
    setMessage(t('Your notes are ready to download.', 'आपके नोट डाउनलोड के लिए तैयार हैं।'));
  }

  return <section className="family-symptom-diary" aria-labelledby={`${id}-title`}>
    <div className="family-workspace-heading"><h1 id={`${id}-title`}>{t('How are you feeling?', 'तबीयत कैसी है?')}</h1>{patientEntries.length > 0 && <button type="button" className="secondary-button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button>}</div>
    <div className="symptom-diary-layout">
      <form ref={formRef} tabIndex={-1} className="symptom-diary-form" onSubmit={save} aria-label={t(editingId ? 'Edit your note' : 'Add a feeling note', editingId ? 'अपना नोट बदलें' : 'तबीयत का नोट जोड़ें')}>
        <h2>{t(editingId ? 'Edit your note' : 'Add a note', editingId ? 'अपना नोट बदलें' : 'नोट जोड़ें')}</h2>
        <fieldset><legend>{t('Who is describing this?', 'यह किसने बताया?')}</legend><div className="symptom-choices">{(['patient', 'assisted-patient', 'caregiver', 'clinician'] as const).map((value) => <button key={value} type="button" aria-pressed={respondent === value} onClick={() => { setRespondent(value); setOwnChanges({}); }}>{symptomRespondentText(value, lang)}</button>)}</div></fieldset>
        <fieldset><legend>{t('Which time are you describing?', 'किस समय की बात है?')}</legend><div className="symptom-choices">{(['now', 'average-24h', 'worst-24h'] as const).map((value) => <button key={value} type="button" aria-pressed={recall === value} onClick={() => { setRecall(value); setOwnChanges({}); }}>{symptomRecallText(value, lang)}</button>)}</div></fieldset>
        <fieldset><legend>{t(editingId ? 'Symptom' : 'Symptoms (choose any)', editingId ? 'परेशानी' : 'परेशानियाँ (जो भी हैं चुनें)')}</legend><div className="symptom-choices">{symptoms.map(([value, hi]) => <button key={value} type="button" aria-pressed={selectedSymptoms.includes(value)} onClick={() => toggleSymptom(value)}>{t(value, hi)}</button>)}</div></fieldset>
        {selectedSymptoms.includes('Other') && <label>{t('Other symptom', 'दूसरी परेशानी')}<input value={customSymptom} onChange={(event) => setCustomSymptom(event.target.value)} maxLength={80} required /></label>}
        {selectedSymptoms.length > 0 && <div className="symptom-ratings">
          <h3>{t('How strong is each symptom?', 'हर परेशानी कितनी है?')}</h3>
          <p className="symptom-scale-key">{t('0 = not at all · 10 = worst you can imagine', '0 = बिल्कुल नहीं · 10 = सबसे ज़्यादा जितना आप सोच सकते हैं')}</p>
          {selectedSymptoms.map((value) => {
            const label = value === 'Other' && customSymptom.trim() ? customSymptom : symptomLabel(value);
            const rating = symptomSeverities[value];
            const comparison = rating === undefined ? null : symptomComparison(entries, {
              id: editingId ?? '', patientId, author: entryAuthor, symptom: value === 'Other' ? customSymptom : value,
              severity: rating, date, time, startedOn, note, scale: SYMPTOM_SCALE, recall, respondent,
              ...(value === 'Pain' ? { painLocation, painTriggers } : {}),
            });
            return <fieldset className="symptom-rating" key={value}>
              <legend><span>{label}</span><span className="symptom-rating-value">{rating === undefined ? t('Choose 0–10', '0–10 चुनें') : symptomSeverityText(rating, lang)}</span></legend>
              <div className="symptom-rating-options">{SYMPTOM_SEVERITIES.map((level) => <button key={level} type="button" aria-pressed={rating === level} aria-label={`${label}: ${level}/10`} onClick={() => { setSymptomSeverities((previous) => ({ ...previous, [value]: level })); setError(''); }}>{level}</button>)}</div>
              <button type="button" className="care-text-button" aria-pressed={rating === null} onClick={() => { setSymptomSeverities((previous) => ({ ...previous, [value]: null })); setOwnChanges((previous) => ({ ...previous, [value]: undefined })); }}>{t('I cannot give a number', 'अंक नहीं बता सकते')}</button>
              {comparison && <div>
                <p>{t('Previous', 'पिछला')}: {dateLabel(comparison.previous.date)} {comparison.previous.time} · {symptomSeverityText(comparison.previous.severity, lang)}. {t('First matching entry', 'पहला मिलते-जुलते हालात का नोट')}: {dateLabel(comparison.baseline.date)} {comparison.baseline.time} · {symptomSeverityText(comparison.baseline.severity, lang)}.</p>
                <p>{t('Compared with that previous entry (optional)', 'उस पिछले नोट के मुकाबले (ज़रूरी नहीं)')}</p>
                <div className="symptom-choices">{(['better', 'same', 'worse', 'not-sure'] as const).map((change) => <button key={change} type="button" aria-pressed={ownChanges[value] === change} onClick={() => setOwnChanges((previous) => ({ ...previous, [value]: previous[value] === change ? undefined : change }))}>{symptomChangeText(change, lang)}</button>)}</div>
              </div>}
            </fieldset>;
          })}
        </div>}
        <div className="symptom-date-fields"><label>{t('Day', 'दिन')}<input type="date" value={date} max={today} onChange={(event) => { setDate(event.target.value); setOwnChanges({}); if (event.target.value !== today) setTime(''); }} required /></label><label>{t('Time being described', 'किस समय की हालत है')}<input type="time" value={time} onChange={(event) => { setTime(event.target.value); setOwnChanges({}); }} required /></label></div>
        <label>{t(selectedSymptoms.length > 1 ? 'When did these start? (optional)' : 'When did it start? (optional)', 'कब शुरू हुआ? (ज़रूरी नहीं)')}<input type="date" value={startedOn} max={date || today} onChange={(event) => setStartedOn(event.target.value)} /></label>
        {selectedSymptoms.includes('Pain') && <>
          <label>{t('Where is the pain? (optional)', 'दर्द कहाँ है? (ज़रूरी नहीं)')}<input maxLength={100} value={painLocation} onChange={(event) => { setPainLocation(event.target.value); setOwnChanges({}); }} /></label>
          <fieldset><legend>{t('When does it happen? (optional)', 'कब होता है? (ज़रूरी नहीं)')}</legend><div className="symptom-choices">{(['rest', 'turning', 'walking', 'coughing', 'other', 'not-sure'] as const).map((trigger) => <button key={trigger} type="button" aria-pressed={painTriggers.includes(trigger)} onClick={() => { setPainTriggers((previous) => previous.includes(trigger) ? previous.filter((item) => item !== trigger) : [...previous, trigger]); setOwnChanges({}); }}>{painTriggerText(trigger, lang)}</button>)}</div></fieldset>
        </>}
        <label>{t(selectedSymptoms.length > 1 ? 'Note for these symptoms (optional)' : 'Anything else? (optional)', selectedSymptoms.length > 1 ? 'इन परेशानियों का नोट (ज़रूरी नहीं)' : 'कुछ और? (ज़रूरी नहीं)')}<textarea rows={2} value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} /></label>
        {error && <p className="symptom-error" role="alert">{error}</p>}
        <div className="symptom-form-actions"><button type="submit" className="primary-button">{editingId ? t('Save changes', 'बदलाव सेव करें') : selectedSymptoms.length > 1 ? t(`Save ${selectedSymptoms.length} symptoms`, `${selectedSymptoms.length} परेशानियाँ सेव करें`) : t('Save note', 'नोट सेव करें')}</button>{editingId && <button type="button" className="secondary-button" onClick={() => { clearForm(); setMessage(t('Changes cancelled.', 'बदलाव रद्द हो गए।')); }}>{t('Cancel', 'रद्द करें')}</button>}</div>
      </form>
      <section className="symptom-history" aria-labelledby={`${id}-history`}>
        <div className="family-section-heading"><h2 id={`${id}-history`}>{t('Your notes', 'आपके नोट')}</h2><span>{visibleEntries.length}</span></div>
        <div className="symptom-history-filters"><div className="symptom-range" aria-label={t('Filter by day', 'दिन के हिसाब से देखें')}>{(['all', 'week', 'today'] as const).map((value) => <button type="button" key={value} aria-pressed={range === value} onClick={() => setRange(value)}>{value === 'all' ? t('All', 'सभी') : value === 'week' ? t('7 days', '7 दिन') : t('Today', 'आज')}</button>)}</div><div className="symptom-choices" aria-label={t('Filter by symptom', 'परेशानी के हिसाब से देखें')}><button type="button" aria-pressed={!filter} onClick={() => setFilter('')}>{t('All symptoms', 'सभी परेशानियाँ')}</button>{knownSymptoms.map((value) => <button type="button" aria-pressed={filter === value} onClick={() => setFilter(value)} key={value}>{symptomLabel(value)}</button>)}</div></div>
        <div className="symptom-notes">{visibleEntries.map((entry) => {
          const comparison = symptomComparison(entries, entry);
          const ownReference = entry.comparedWithId ? entries.find((item) => item.id === entry.comparedWithId && item.patientId === patientId) : undefined;
          return <article key={entry.id} className="symptom-note">
          <div className="symptom-note-heading"><h3>{symptomLabel(entry.symptom)}</h3><time dateTime={`${entry.date}${entry.time ? `T${entry.time}` : ''}`}>{dateLabel(entry.date)} {entry.time}</time></div>
          <p className="symptom-note-level">{symptomSeverityText(entry.severity, lang)}{entry.startedOn && <span> · {t('Started', 'शुरू हुआ')} {dateLabel(entry.startedOn)}</span>}</p>
          {entry.respondent && entry.recall && <p className="symptom-note-level"><span>{symptomRespondentText(entry.respondent, lang)} · {symptomRecallText(entry.recall, lang)}</span></p>}
          {comparison && <p>{t('Previous', 'पिछला')}: {dateLabel(comparison.previous.date)} {comparison.previous.time} · {symptomSeverityText(comparison.previous.severity, lang)}. {t('Change in number', 'अंक में बदलाव')}: {comparison.difference > 0 ? '+' : ''}{comparison.difference}.<br />{t('First matching entry', 'पहला मिलते-जुलते हालात का नोट')}: {dateLabel(comparison.baseline.date)} {comparison.baseline.time} · {symptomSeverityText(comparison.baseline.severity, lang)}.</p>}
          {!comparison && typeof entry.severity === 'number' && entry.scale === SYMPTOM_SCALE && <p>{t('First entry for these reporting details.', 'इन हालात के लिए पहला नोट।')}</p>}
          {entry.selfReportedChange && <p>{t('Their comparison', 'उनके मुताबिक बदलाव')}: {symptomChangeText(entry.selfReportedChange, lang)}{ownReference ? ` · ${dateLabel(ownReference.date)} ${ownReference.time ?? ''}` : ` · ${t('previous note removed', 'पिछला नोट हटा दिया गया')}`}</p>}
          {Boolean(entry.painLocation || entry.painTriggers?.length) && <p>{[entry.painLocation, entry.painTriggers?.map((value) => painTriggerText(value, lang)).join(', ')].filter(Boolean).join(' · ')}</p>}
          {entry.note && <p className="symptom-note-text">{entry.note}</p>}
          <div className="symptom-note-footer"><span>{entry.editedBy ? `${t('Edited by', 'बदला')} ${entry.editedBy}` : `${t('Written by', 'लिखा')} ${entry.author}`}</span><div><button type="button" onClick={() => edit(entry)} aria-label={`${t('Edit', 'बदलें')}: ${symptomLabel(entry.symptom)}, ${entry.date}`}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => remove(entry)} aria-label={`${t('Remove', 'हटाएँ')}: ${symptomLabel(entry.symptom)}, ${entry.date}`}>{t('Remove', 'हटाएँ')}</button></div></div>
        </article>; })}</div>
        {!visibleEntries.length && <p className="symptom-empty">{patientEntries.length ? t('No notes match these filters.', 'इन फ़िल्टर के लिए कोई नोट नहीं है।') : t('Your first note will appear here.', 'आपका पहला नोट यहाँ दिखेगा।')}</p>}
      </section>
    </div>
    <div className="symptom-status"><p role="status">{saveStatus === 'unavailable' ? t('Saving failed. Keep this page open and save a copy of your entries.', 'सेव नहीं हुआ। पेज खुला रखें और नोटों की कॉपी डाउनलोड करें।') : saveStatus === 'saving' ? t('Saving entries…', 'नोट सेव हो रहे हैं…') : message}</p>{removed.length > 0 && <button type="button" className="care-text-button" onClick={undo}>{t('Undo last removal', 'पिछला हटाया नोट वापस लाएँ')}</button>}</div>
  </section>;
}
