'use client';

import { useState } from 'react';
import type { SummaryRelease, DraftFieldKey } from './summary-state';
import { selectFamilyNoteVersions, compareFamilyNoteVersions } from './family-note-history-state';
import { CARE_NOTE_LABELS, type DoctorConversationEntry } from './doctor-conversation-state';
import { ConversationAudio } from './doctor-conversation';
import { useSpeechSynthesis } from './tts-speech';
import { IconFileText, IconClock, IconCheckCircle, IconVolume2 } from './icons';
import { downloadCarePdf } from './care-pdf';

const KEYS: DraftFieldKey[] = ['followUp', 'priorities', 'topics', 'participants', 'openQuestions'];
const NOT_STATED = 'Not stated in this conversation.';

export function CareNoteReader({ patientId, patientName, releases, hindi, conversations = [], doctor = false, patients, onPatient, onRecord, onPrepare }: {
  patientId: string; patientName: string; releases: readonly SummaryRelease[]; hindi: boolean;
  conversations?: readonly DoctorConversationEntry[]; doctor?: boolean;
  patients?: Array<{ id: string; name: string }>; onPatient?: (id: string) => void; onRecord?: () => void; onPrepare?: () => void;
}) {
  const [selectedNumber, setSelectedNumber] = useState<number>();
  const [tab, setTab] = useState<'notes' | 'conversations'>('notes');
  const [comparing, setComparing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState('');
  const speech = useSpeechSynthesis();
  const versions = selectFamilyNoteVersions(releases, patientId);
  const selected = versions.find((release) => release.number === selectedNumber) ?? versions[0];
  const previous = selected ? versions.find((release) => release.number < selected.number) : undefined;
  const comparison = selected && previous ? compareFamilyNoteVersions(releases, patientId, selected.number, previous.number) : null;
  const recordings = conversations.filter((entry) => entry.patientId === patientId).slice().sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
  const t = (en: string, hi: string) => hindi ? hi : en;
  const date = (value: string) => new Date(value).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const sections = selected?.fields ? KEYS.filter((key) => selected.fields?.[key]?.trim() && selected.fields[key] !== NOT_STATED).map((key) => ({ key, heading: CARE_NOTE_LABELS[key], lines: [selected.fields![key]] }))
    : selected?.source ? [{ key: 'topics' as const, heading: 'Conversation note', lines: [selected.source] }] : [];

  async function download() {
    if (!selected || !sections.length || downloading) return;
    setDownloading(true); setError('');
    try {
      await downloadCarePdf({ fileName: `saanthvana-${patientId}-care-note-v${selected.number}.pdf`, title: `${patientName} — Care note`,
        subtitle: `Version ${selected.number} · ${date(selected.releasedAt)} · ${selected.physician}`,
        sections: [...sections, ...(selected.signature ? [{ heading: 'Signed by', lines: [`${selected.signature.name} · ${new Date(selected.signature.signedAt).toLocaleString('en-GB')}`, 'Typed signature · Care conversation record'] }] : []),
          { heading: 'Record', lines: ['Approved conversation note. It does not replace a prescription or an advance medical directive.'] }] });
    } catch { setError('The PDF could not be downloaded. Please try again.'); }
    finally { setDownloading(false); }
  }

  return <section className="care-note-reader">
    <header className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1>{doctor ? t('Note history', 'पुराने नोट') : t('Care note', 'देखभाल का नोट')}</h1></div>
      <div className="care-note-top-actions">{patients && onPatient && <select aria-label="Patient" value={patientId} onChange={(event) => onPatient(event.target.value)}>{patients.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select>}{onRecord && <button className="primary-button" type="button" onClick={onRecord}>+ Record conversation</button>}</div></header>
    {doctor && <div className="doctor-history-tabs" role="group" aria-label="Note history view"><button type="button" aria-pressed={tab === 'notes'} onClick={() => setTab('notes')}><IconCheckCircle />Approved notes <span>{versions.length}</span></button><button type="button" aria-pressed={tab === 'conversations'} onClick={() => setTab('conversations')}><IconClock />Conversations <span>{recordings.length}</span></button></div>}
    {tab === 'conversations' ? <div className="doctor-conversation-history">{recordings.length ? recordings.map((entry) => <article key={entry.id}><span className="care-timeline-dot" /><time dateTime={entry.recordedAt}>{date(entry.recordedAt)}</time><h2>{entry.physician}</h2>{entry.audio && <ConversationAudio file={entry.audio} />}{entry.note && <details><summary>Conversation note</summary><p>{entry.note}</p></details>}</article>) : <div className="care-note-empty"><IconClock /><h2>No conversations recorded yet</h2>{onRecord && <button className="primary-button" type="button" onClick={onRecord}>Record a conversation</button>}</div>}</div>
      : selected ? <div className="care-note-layout">
        <aside className="care-note-date-rail" aria-label="Care note timeline"><ol>{versions.map((release, index) => <li key={release.number}><button type="button" aria-pressed={selected.number === release.number} onClick={() => { setSelectedNumber(release.number); setComparing(false); speech.cancel(); }}><span className="care-timeline-dot" /><time dateTime={release.releasedAt}>{date(release.releasedAt)}</time><strong>{index === 0 ? t('Latest note', 'सबसे नया नोट') : t('Earlier note', 'पुराना नोट')}</strong><small>{release.physician} · V{release.number}</small></button></li>)}</ol></aside>
        <article className="care-note-paper"><header className="care-note-paper-heading"><div><span className="care-note-approved"><IconCheckCircle />{selected.signature ? t('Signed care note', 'हस्ताक्षर किया हुआ नोट') : t('Approved care note', 'मंज़ूर देखभाल नोट')}</span><h2>{date(selected.releasedAt)}</h2><span>{selected.physician} · V{selected.number}</span></div><div className="care-note-tools"><button className="primary-button" type="button" disabled={downloading || !sections.length} onClick={download}><IconFileText />{downloading ? 'Preparing…' : 'PDF'}</button>{speech.isSupported && sections.length > 0 && <button type="button" className="secondary-button" onClick={() => speech.isPlaying ? speech.cancel() : speech.speak(sections.map((section) => `${section.heading}. ${section.lines.join(' ')}`).join('. '))}><IconVolume2 />{speech.isPlaying ? 'Stop' : 'Listen'}</button>}</div></header>
          <div className="care-note-story-cards">{sections.map((section, index) => <section className={`care-note-story-card is-${section.key}`} key={section.key}><span aria-hidden="true" className="care-note-section-number">{String(index + 1).padStart(2, '0')}</span><div><h3>{section.heading}</h3>{section.lines.map((line, item) => line.length > 650 ? <details key={item}><summary>{line.slice(0, 260)}…</summary><p>{line}</p></details> : <p key={item}>{line}</p>)}</div></section>)}</div>
          {!sections.length && <p>The text for this earlier note is unavailable.</p>}
          {selected.signature && <div className="care-note-signature"><span>{t('Signed by', 'हस्ताक्षरकर्ता')}</span><strong>{selected.signature.name}</strong><time dateTime={selected.signature.signedAt}>{new Date(selected.signature.signedAt).toLocaleString(hindi ? 'hi-IN' : 'en-GB')}</time></div>}
          {previous && <button className="care-text-button" type="button" aria-expanded={comparing} onClick={() => setComparing(!comparing)}>{comparing ? 'Close changes' : 'What changed since the last note?'} →</button>}
          {comparing && comparison && <div className="care-note-changes">{comparison.rows.filter((row) => row.change === 'changed').map((row) => <section key={row.key}><h3>{row.label}</h3><del>{row.earlier}</del><p>{row.later}</p></section>)}{!comparison.rows.some((row) => row.change === 'changed') && <p>{comparison.rows.some((row) => row.change === 'unavailable') ? 'The earlier text is unavailable for comparison.' : 'No wording changes between these notes.'}</p>}</div>}
        </article>
      </div> : <div className="care-note-empty"><IconFileText /><h2>{t('No approved note yet', 'अभी कोई मंज़ूर नोट नहीं है')}</h2>{onRecord && <button className="primary-button" type="button" onClick={onRecord}>Record a conversation</button>}</div>}
    {onPrepare && <button className="care-text-button" type="button" onClick={onPrepare}>{t('Something to ask at the next visit?', 'अगली मुलाकात में कुछ पूछना है?')} →</button>}
    {error && <p role="alert" className="doctor-action-message">{error}</p>}
  </section>;
}
