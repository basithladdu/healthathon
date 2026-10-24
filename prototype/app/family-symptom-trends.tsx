'use client';

import { useId, useState } from 'react';
import { symptomName, symptomRecallText, symptomRespondentText, painTriggerText, symptomScaleAnchors, symptomSeverityText, type SymptomEntry } from './family-symptom-state';
import { painBodySiteName } from './pain-body-map-state';
import { symptomTrendGroups, symptomTrendSegments } from './family-symptom-trend-state';

export function FamilySymptomTrends({ entries, patientId, today, hindi }: {
  entries: SymptomEntry[]; patientId: string; today: string; hindi: boolean;
}) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const symptoms = [...new Set(entries.filter((entry) => entry.patientId === patientId).map((entry) => entry.symptom))];
  const [selectedSymptom, setSelectedSymptom] = useState('');
  const [selectedGroup, setSelectedGroup] = useState('');
  const symptom = symptoms.includes(selectedSymptom) ? selectedSymptom : symptoms[0];
  if (!symptom) return null;
  const groups = symptomTrendGroups(entries, patientId, today, symptom);
  const group = groups.find((item) => item.id === selectedGroup) ?? groups[0];
  const points = group?.entries ?? [];
  const stamp = (entry: SymptomEntry) => Date.parse(`${entry.date}T${entry.time ?? '00:00'}:00Z`);
  const first = points[0] ? stamp(points[0]) : 0;
  const last = points.at(-1) ? stamp(points.at(-1)!) : first;
  const x = (entry: SymptomEntry) => last === first ? 280 : 42 + (stamp(entry) - first) / (last - first) * 476;
  const y = (entry: SymptomEntry) => 180 - (entry.severity as number) * 15;
  const anchors = symptomScaleAnchors(symptom, hindi);
  const dateText = (entry: SymptomEntry) => `${entry.date} ${entry.time ?? ''}`;
  return <section className="symptom-trends" aria-labelledby={`${id}-title`}>
    <h2 id={`${id}-title`}>{t('Over time', 'समय के साथ')}</h2>
    <div className="symptom-choices">{symptoms.map((name) => <button type="button" key={name} aria-pressed={name === symptom} onClick={() => { setSelectedSymptom(name); setSelectedGroup(''); }}>{symptomName(name, hindi)}</button>)}</div>
    {groups.length > 0 && <div className="symptom-trend-contexts symptom-choices">{groups.map((item) => <button key={item.id} type="button" aria-pressed={item.id === group?.id} onClick={() => setSelectedGroup(item.id)}>
      {symptomRespondentText(item.reference.respondent!, hindi)} · {symptomRecallText(item.reference.recall!, hindi)}
      {item.reference.painBodySites?.length ? ` · ${item.reference.painBodySites.map((site) => painBodySiteName(site, hindi)).join(', ')}` : ''}
      {item.reference.painLocation ? ` · ${item.reference.painLocation}` : ''}
      {item.reference.painTriggers?.length ? ` · ${item.reference.painTriggers.map((trigger) => painTriggerText(trigger, hindi)).join(', ')}` : ''}
      {['caregiver', 'clinician'].includes(item.reference.respondent ?? '') ? ` · ${item.reference.author}` : ''}
    </button>)}</div>}
    {!group && <p>{t('Earlier notes remain in your history. A graph needs dated numeric entries with matching reporting details.', 'पुराने नोट नीचे हैं। ग्राफ़ के लिए तारीख और एक जैसे हालात के अंक वाले नोट चाहिए।')}</p>}
    {group && <>
      <p className="symptom-scale-key">0 = {anchors.zero} · 10 = {anchors.ten}</p>
      <svg className="symptom-trend-chart" viewBox="0 0 560 220" role="img" aria-labelledby={`${id}-chart-title ${id}-chart-desc`}>
        <title id={`${id}-chart-title`}>{symptomName(symptom, hindi)}: {t('recorded ratings over time', 'समय के साथ बताए गए अंक')}</title>
        <desc id={`${id}-chart-desc`}>{t('Each dot is a saved rating. Unrated entries break the line. The exact dates and values are listed below.', 'हर बिंदु सेव किया गया अंक है। बिना अंक वाले नोट पर रेखा टूटती है। सही तारीख और अंक नीचे हैं।')}</desc>
        {[0, 5, 10].map((level) => <g key={level}><line x1="42" x2="518" y1={180 - level * 15} y2={180 - level * 15} stroke="#d7e1d4" /><text x="30" y={184 - level * 15} textAnchor="end">{level}</text></g>)}
        {symptomTrendSegments(points).map((segment) => <polyline key={segment[0].id} points={segment.map((entry) => `${x(entry)},${y(entry)}`).join(' ')} fill="none" stroke="#286249" strokeWidth="2.5" />)}
        {points.filter((entry) => typeof entry.severity === 'number').map((entry) => <circle key={entry.id} cx={x(entry)} cy={y(entry)} r="4.5" fill="#286249"><title>{dateText(entry)}: {entry.severity}/10</title></circle>)}
        {points[0] && <text x="42" y="207">{points[0].date}</text>}
        {points.length > 1 && <text x="518" y="207" textAnchor="end">{points.at(-1)?.date}</text>}
      </svg>
      <p className="symptom-scale-key">{t('Recorded entries only. Gaps between dates are not estimated.', 'सिर्फ़ लिखे गए नोट। बीच की तारीखों का अनुमान नहीं लगाया गया है।')}</p>
      <div className="symptom-trend-values">{points.map((entry) => <span key={entry.id}><time>{dateText(entry)}</time> <strong>{symptomSeverityText(entry.severity, hindi)}</strong></span>)}</div>
    </>}
  </section>;
}
