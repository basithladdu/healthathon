'use client';

import { useId } from 'react';
import { symptomSeverityText, type SymptomEntry } from './family-symptom-state';
import type { CareStoryEntry } from './family-care-story-state';
import type { CareReport } from './care-calendar-state';
import { IconArrowRight, IconFileText } from './icons';
import { CareArt } from './care-art';
import './family-cancer-overview.css';

export type FamilyCancerOverviewProps = {
  patientId: string;
  patientName: string;
  diagnosis: string;
  team: string;
  hindi: boolean;
  symptoms: SymptomEntry[];
  story: CareStoryEntry[];
  reports: CareReport[];
  onCareStory: () => void;
  onCareNote: () => void;
  onSymptoms: () => void;
  onReports: () => void;
};

const symptomHindi: Record<string, string> = {
  Pain: 'दर्द', Nausea: 'जी मिचलाना', Tiredness: 'थकान', 'Low appetite': 'भूख कम लगना',
  'Sleep trouble': 'नींद की परेशानी', Breathlessness: 'साँस फूलना', 'Low mood': 'मन उदास होना',
};

export function FamilyCancerOverview({ patientId, patientName, diagnosis, team, hindi, symptoms, story, reports, onCareStory, onCareNote, onSymptoms, onReports }: FamilyCancerOverviewProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const dateLabel = (value: string) => new Date(`${value}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const patientStory = story.filter((entry) => entry.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const patientSymptoms = symptoms.filter((entry) => entry.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const patientReports = reports.filter((entry) => entry.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const latestStory = patientStory[0];
  const latestSymptom = patientSymptoms[0];
  const latestReport = patientReports[0];
  const symptomLabel = (value: string) => hindi ? symptomHindi[value] ?? value : value;
  const timeline = [
    ...patientStory.map((entry) => ({
      key: `story:${entry.id}`, date: entry.date, title: entry.title,
      source: t('Care story', 'देखभाल की कहानी'), author: entry.updatedBy || entry.createdBy,
      kind: 'story', open: onCareStory,
    })),
    ...patientSymptoms.map((entry) => ({
      key: `symptom:${entry.id}`, date: entry.date,
      title: `${symptomLabel(entry.symptom)} · ${symptomSeverityText(entry.severity, hindi)}`,
      source: t('Feeling note', 'तबीयत का नोट'), author: entry.editedBy || entry.author,
      kind: 'symptom', open: onSymptoms,
    })),
  ].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 6);

  return <section className="family-cancer-overview" aria-labelledby={`${id}-title`}>
    <header className="cancer-overview-heading">
      <h1 id={`${id}-title`}>{t('Cancer care', 'कैंसर की देखभाल')}</h1>
      <span>{patientName}</span>
    </header>

    <section className="cancer-overview-record" aria-labelledby={`${id}-record`}>
      <h2 id={`${id}-record`}>{t('In the care record', 'देखभाल के रिकॉर्ड में')}</h2>
      <dl>
        <div><dt>{t('Diagnosis', 'बीमारी')}</dt><dd>{diagnosis || t('Not recorded', 'दर्ज नहीं है')}</dd></div>
        <div><dt>{t('Care team', 'देखभाल टीम')}</dt><dd>{team || t('Not recorded', 'दर्ज नहीं है')}</dd></div>
      </dl>
    </section>

    <div className="cancer-overview-bento">
      <button type="button" className="cancer-overview-card cancer-overview-story" onClick={onCareStory}>
        <span className="cancer-overview-card-top"><span className="cancer-overview-icon" aria-hidden="true"><CareArt kind="care-story" /></span><span className="cancer-overview-card-title">{t('Your care story', 'अब तक की देखभाल')}</span></span>
        {latestStory ? <>
          <span className="cancer-overview-count"><strong>{patientStory.length}</strong> {t(patientStory.length === 1 ? 'entry' : 'entries', 'नोट')}</span>
          <span className="cancer-overview-preview">{latestStory.title}</span>
          <span className="cancer-overview-date">{dateLabel(latestStory.date)}</span>
        </> : <span className="cancer-overview-empty">{t('No care history added yet', 'अभी देखभाल की जानकारी नहीं जोड़ी है')}</span>}
        <span className="cancer-overview-card-action">{t(latestStory ? 'Open care story' : 'Add care history', latestStory ? 'देखभाल की कहानी देखें' : 'देखभाल की जानकारी जोड़ें')}<IconArrowRight /></span>
      </button>

      <button type="button" className="cancer-overview-card cancer-overview-symptoms" onClick={onSymptoms}>
        <span className="cancer-overview-card-top"><span className="cancer-overview-icon" aria-hidden="true"><CareArt kind="symptom-diary" /></span><span className="cancer-overview-card-title">{t('How you’ve been', 'कैसी रही तबीयत')}</span></span>
        {latestSymptom ? <>
          <span className="cancer-overview-count"><strong>{patientSymptoms.length}</strong> {t(patientSymptoms.length === 1 ? 'feeling note' : 'feeling notes', 'तबीयत के नोट')}</span>
          <span className="cancer-overview-preview">{symptomLabel(latestSymptom.symptom)}</span>
          <span className="cancer-overview-date">{dateLabel(latestSymptom.date)}</span>
        </> : <span className="cancer-overview-empty">{t('No feeling notes yet', 'अभी तबीयत का कोई नोट नहीं है')}</span>}
        <span className="cancer-overview-card-action">{t('Record how you feel', 'अपनी तबीयत लिखें')}<IconArrowRight /></span>
      </button>

      <button type="button" className="cancer-overview-card cancer-overview-reports" onClick={onReports}>
        <span className="cancer-overview-card-top"><span className="cancer-overview-icon" aria-hidden="true"><CareArt kind="lab-history" /></span><span className="cancer-overview-card-title">{t('Reports', 'रिपोर्ट')}</span></span>
        {latestReport ? <>
          <span className="cancer-overview-count"><strong>{patientReports.length}</strong> {t(patientReports.length === 1 ? 'file' : 'files', 'फ़ाइलें')}</span>
          <span className="cancer-overview-preview">{latestReport.file.name}</span>
          <span className="cancer-overview-date">{dateLabel(latestReport.date)}</span>
        </> : <span className="cancer-overview-empty">{t('No reports added yet', 'अभी कोई रिपोर्ट नहीं जोड़ी है')}</span>}
        <span className="cancer-overview-card-action">{t(latestReport ? 'Open reports' : 'Add a report', latestReport ? 'रिपोर्ट देखें' : 'रिपोर्ट जोड़ें')}<IconArrowRight /></span>
      </button>

      <button type="button" className="cancer-overview-card cancer-overview-note" onClick={onCareNote}>
        <span className="cancer-overview-card-top"><span className="cancer-overview-icon" aria-hidden="true"><CareArt kind="doctor-pack" /></span><span className="cancer-overview-card-title">{t('Care Note', 'डॉक्टर का नोट')}</span></span>
        <span className="cancer-overview-note-art" aria-hidden="true"><IconFileText /></span>
        <span className="cancer-overview-card-action">{t('Open Care Note', 'देखभाल का नोट खोलें')}<IconArrowRight /></span>
      </button>
    </div>

    <section className="cancer-overview-history" aria-labelledby={`${id}-recent`}>
      <h2 id={`${id}-recent`}>{t('Notes by date', 'तारीख के हिसाब से नोट')}</h2>
      {timeline.length ? <ol>{timeline.map((entry) => <li key={entry.key}>
        <button type="button" className={`cancer-overview-entry cancer-overview-entry-${entry.kind}`} onClick={entry.open}>
          <time dateTime={entry.date}>{dateLabel(entry.date)}</time>
          <span className="cancer-overview-entry-copy"><strong>{entry.title}</strong><span>{entry.source}{entry.author ? ` · ${entry.author}` : ''}</span></span>
          <span className="cancer-overview-entry-arrow" aria-hidden="true"><IconArrowRight /></span>
        </button>
      </li>)}</ol> : <div className="cancer-overview-history-empty">
        <p>{t('No notes here yet.', 'अभी यहाँ कोई नोट नहीं है।')}</p>
        <button type="button" onClick={onCareStory}>{t('Add a care story entry', 'देखभाल का नोट जोड़ें')}<IconArrowRight /></button>
      </div>}
    </section>
  </section>;
}
