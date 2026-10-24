'use client';

import { useId, useState } from 'react';
import { PainBodyMap } from './pain-body-map';
import { painHistoryEntries, painHistoryMapState } from './family-pain-history-state';
import { symptomRecallText, symptomRespondentText, symptomSeverityText, type SymptomEntry } from './family-symptom-state';

export function FamilyPainHistory({ entries, patientId, today, hindi }: {
  entries: SymptomEntry[]; patientId: string; today: string; hindi: boolean;
}) {
  const id = useId();
  const [selectedId, setSelectedId] = useState('');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const saved = painHistoryEntries(entries, patientId, today);
  const index = Math.max(0, saved.findIndex((entry) => entry.id === selectedId));
  const selected = saved[index];
  const map = selected ? painHistoryMapState(selected) : null;
  const pageStart = Math.floor(index / 5) * 5;
  const dateLabel = (entry: SymptomEntry) => `${new Date(`${entry.date}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })}${entry.time ? ` · ${entry.time}` : ''}`;
  return <section className="pain-history" aria-labelledby={`${id}-title`}>
    <h2 id={`${id}-title`}>{t('Pain history in 3D', '3D में दर्द का इतिहास')}</h2>
    {!selected || !map ? <p>{t('Save a pain entry to see its date, rating and chosen areas here.', 'दर्द का नोट सेव करने पर उसकी तारीख, अंक और चुनी जगहें यहाँ दिखेंगी।')}</p> : <>
      <div className="pain-history-dates symptom-choices" aria-label={t('Choose a saved pain entry', 'सेव किया दर्द का नोट चुनें')}>
        {saved.slice(pageStart, pageStart + 5).map((entry) => <button type="button" key={entry.id} aria-pressed={entry.id === selected.id} onClick={() => setSelectedId(entry.id)}>{dateLabel(entry)} · {symptomSeverityText(entry.severity, hindi)}</button>)}
      </div>
      <div className="pain-history-navigation symptom-choices">
        <button type="button" disabled={index <= 0} onClick={() => setSelectedId(saved[index - 1].id)}>{t('Newer entry', 'नया नोट')}</button>
        <span>{index + 1} / {saved.length}</span>
        <button type="button" disabled={index >= saved.length - 1} onClick={() => setSelectedId(saved[index + 1].id)}>{t('Older entry', 'पुराना नोट')}</button>
      </div>
      <p className="pain-history-entry" aria-live="polite"><strong>{dateLabel(selected)} · {symptomSeverityText(selected.severity, hindi)}</strong>
        {selected.respondent && <> · {symptomRespondentText(selected.respondent, hindi)}</>}
        {selected.recall && <> · {symptomRecallText(selected.recall, hindi)}</>}
      </p>
      {map.status === 'mapped' ? <PainBodyMap readOnly selected={map.selected} points={map.points} rating={map.rating} hindi={hindi} />
        : <p className="pain-history-no-map">{map.status === 'written-location'
          ? t('This note has a written location, without a saved body map.', 'इस नोट में जगह लिखी है, सेव किया बॉडी मैप नहीं है।')
          : map.status === 'unsupported-map'
            ? t('This saved map version cannot be displayed here. The original note is unchanged.', 'यह सेव किया बॉडी मैप यहाँ नहीं दिख सकता। पुराना नोट नहीं बदला है।')
            : t('No body areas were saved for this entry.', 'इस नोट में शरीर की कोई जगह सेव नहीं की गई।')}</p>}
      {selected.painLocation && <p>{selected.painLocation}</p>}
      {selected.note && <p className="symptom-note-text">{selected.note}</p>}
      <p className="symptom-scale-key">{t('One saved entry at a time. Rotating or choosing a date does not change it.', 'एक समय पर एक सेव किया नोट। घुमाने या तारीख चुनने से नोट नहीं बदलता।')}</p>
    </>}
  </section>;
}
