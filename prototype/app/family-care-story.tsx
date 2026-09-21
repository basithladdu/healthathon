'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import {
  CARE_STORY_KINDS, addCareStoryEntry, removeCareStoryEntry, restoreCareStoryEntry,
  selectCareStoryEntries, updateCareStoryEntry, type CareStoryDraft, type CareStoryEntry, type CareStoryFilter, type CareStoryKind,
} from './family-care-story-state';

export type { CareStoryEntry } from './family-care-story-state';

type FamilyCareStoryProps = {
  patientId: string;
  author: string;
  today: string;
  hindi: boolean;
  entries: CareStoryEntry[];
  recordedEntries?: readonly CareStoryEntry[];
  onChange: (entries: CareStoryEntry[]) => void;
};

const HINDI_KINDS: Record<CareStoryKind, string> = {
  Visit: 'मुलाकात', Treatment: 'इलाज', 'Treatment change': 'इलाज में बदलाव', Report: 'रिपोर्ट', Milestone: 'अहम पड़ाव', 'Past history': 'पुराना इलाज और सेहत',
};

function emptyDraft(today: string): CareStoryDraft {
  return { date: today, kind: 'Visit', title: '', details: '', changeReason: '', source: '', sourceAuthor: '' };
}

export function FamilyCareStory(props: FamilyCareStoryProps) {
  return <CareStoryContent key={`${props.patientId}:${props.author}`} {...props} />;
}

function CareStoryContent({ patientId, author, today, hindi, entries, recordedEntries = [], onChange }: FamilyCareStoryProps) {
  const id = useId();
  const titleInput = useRef<HTMLInputElement>(null);
  const [filter, setFilter] = useState<CareStoryFilter>('All');
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState(() => emptyDraft(today));
  const [removed, setRemoved] = useState<CareStoryEntry[]>([]);
  const [message, setMessage] = useState('');
  const t = (english: string, translated: string) => hindi ? translated : english;
  const kindLabel = (kind: CareStoryKind) => hindi ? HINDI_KINDS[kind] : kind;
  const allEntries = [
    ...selectCareStoryEntries([...recordedEntries], patientId).map((entry) => ({ ...entry, fromRecord: true })),
    ...selectCareStoryEntries(entries, patientId).map((entry) => ({ ...entry, fromRecord: false })),
  ].sort((a, b) => b.date.localeCompare(a.date));
  const visible = allEntries.filter((entry) => filter === 'All' || entry.kind === filter);
  const lastRemoved = removed.at(-1);

  useEffect(() => {
    if (formOpen) titleInput.current?.focus();
  }, [formOpen, editingId]);

  function edit(entry?: CareStoryEntry) {
    setEditingId(entry?.id ?? null);
    setDraft(entry ? { date: entry.date, kind: entry.kind, title: entry.title, details: entry.details, changeReason: entry.changeReason, source: entry.source, sourceAuthor: entry.sourceAuthor } : emptyDraft(today));
    setMessage('');
    setFormOpen(true);
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = editingId
      ? updateCareStoryEntry(entries, patientId, editingId, author, draft)
      : addCareStoryEntry(entries, patientId, author, crypto.randomUUID(), draft);
    if (next === entries) {
      setMessage(t('Check the date and title, then try again.', 'तारीख और नाम जाँचकर फिर से कोशिश करें।'));
      return;
    }
    onChange(next);
    setFilter('All');
    setFormOpen(false);
    setMessage(t(editingId ? 'Changes saved.' : 'Added to your care story.', editingId ? 'बदलाव सेव हो गए।' : 'देखभाल की कहानी में जोड़ दिया।'));
    setEditingId(null);
    setDraft(emptyDraft(today));
  }

  function remove(entry: CareStoryEntry) {
    onChange(removeCareStoryEntry(entries, patientId, entry.id));
    setRemoved((previous) => [...previous, entry]);
    if (editingId === entry.id) { setFormOpen(false); setEditingId(null); }
    setMessage('');
  }

  function undo() {
    if (!lastRemoved) return;
    const next = restoreCareStoryEntry(entries, patientId, lastRemoved);
    if (next === entries) return;
    onChange(next);
    setRemoved((previous) => previous.slice(0, -1));
    setFilter('All');
    setMessage(t('The note is back.', 'नोट वापस जोड़ दिया।'));
  }

  function download() {
    const text = [
      'SAANTHVANA — YOUR CARE STORY', `Patient: ${patientId}`,
      'Existing records and family notes. Family notes do not change the original records.',
      ...allEntries.map((entry) => [
        `${entry.date} · ${entry.kind} · ${entry.title}`,
        entry.fromRecord ? 'From existing records' : 'Family note',
        entry.details,
        entry.changeReason ? `What changed / reason as documented: ${entry.changeReason}` : '',
        entry.source ? `Source: ${entry.source}` : '',
        entry.sourceAuthor ? `Source written by: ${entry.sourceAuthor}` : '',
        `${entry.fromRecord ? 'Recorded by' : 'Added by'}: ${entry.createdBy}`,
        entry.updatedBy !== entry.createdBy ? `Last edited by: ${entry.updatedBy}` : '',
      ].filter(Boolean).join('\n')),
    ].join('\n\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `saanthvana-care-story-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`;
    link.click();
    URL.revokeObjectURL(url);
    setMessage(t('Your care story was downloaded.', 'आपकी देखभाल की कहानी डाउनलोड हो गई।'));
  }

  return <section className="family-care-story" aria-labelledby={`${id}-heading`}>
    <div className="care-story-heading">
      <h1 id={`${id}-heading`}>{t('Your care story', 'देखभाल की कहानी')}</h1>
      <div className="care-story-actions">
        <button type="button" className="secondary-button" onClick={download} disabled={allEntries.length === 0}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button>
        {!formOpen && <button type="button" className="primary-button" onClick={() => edit()}>{t('Add a note', 'नोट जोड़ें')}</button>}
      </div>
    </div>

    {formOpen && <form className="care-story-form" onSubmit={save} aria-labelledby={`${id}-form-heading`}>
      <h2 id={`${id}-form-heading`}>{editingId ? t('Edit family note', 'परिवार का नोट बदलें') : t('Add a family note', 'परिवार का नोट जोड़ें')}</h2>
      <label className="care-story-wide" htmlFor={`${id}-title`}>{t('What happened?', 'क्या हुआ?')}<input ref={titleInput} id={`${id}-title`} value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} required maxLength={160} /></label>
      <label htmlFor={`${id}-date`}>{t('Date', 'तारीख')}<input id={`${id}-date`} type="date" value={draft.date} onChange={(event) => setDraft({ ...draft, date: event.target.value })} required /></label>
      <label htmlFor={`${id}-kind`}>{t('Type', 'किस तरह का नोट')}<select id={`${id}-kind`} value={draft.kind} onChange={(event) => setDraft({ ...draft, kind: event.target.value as CareStoryKind })}>{CARE_STORY_KINDS.map((kind) => <option key={kind} value={kind}>{kindLabel(kind)}</option>)}</select></label>
      <details className="care-story-wide care-story-source" open={Boolean(draft.details || draft.changeReason || draft.source || draft.sourceAuthor || draft.kind === 'Treatment change') || undefined}>
        <summary>{t('More details or a source (optional)', 'और जानकारी या स्रोत (चाहें तो)')}</summary>
        <div className="care-story-source-fields">
          <label className="care-story-wide" htmlFor={`${id}-details`}>{t('Details', 'जानकारी')}<textarea id={`${id}-details`} rows={3} value={draft.details} onChange={(event) => setDraft({ ...draft, details: event.target.value })} maxLength={4000} /></label>
          {(draft.kind === 'Treatment change' || draft.changeReason) && <label className="care-story-wide" htmlFor={`${id}-reason`}>{t('What changed / reason as documented', 'क्या बदला / लिखी हुई वजह')}<textarea id={`${id}-reason`} rows={2} value={draft.changeReason} onChange={(event) => setDraft({ ...draft, changeReason: event.target.value })} maxLength={2000} /></label>}
          <label htmlFor={`${id}-source`}>{t('Report or note name', 'रिपोर्ट या नोट का नाम')}<input id={`${id}-source`} value={draft.source} onChange={(event) => setDraft({ ...draft, source: event.target.value })} maxLength={240} /></label>
          <label htmlFor={`${id}-source-author`}>{t('Who wrote the source?', 'मूल नोट किसने लिखा?')}<input id={`${id}-source-author`} value={draft.sourceAuthor} onChange={(event) => setDraft({ ...draft, sourceAuthor: event.target.value })} maxLength={100} /></label>
        </div>
      </details>
      <div className="care-story-wide care-story-form-actions">
        <button type="submit" className="primary-button" disabled={!draft.title.trim() || !draft.date}>{t('Save note', 'नोट सेव करें')}</button>
        <button type="button" className="secondary-button" onClick={() => { setFormOpen(false); setEditingId(null); setDraft(emptyDraft(today)); setMessage(''); }}>{t('Cancel', 'रहने दें')}</button>
      </div>
    </form>}

    {allEntries.length > 0 && <div className="care-story-filter">
      <label htmlFor={`${id}-filter`}>{t('Show', 'दिखाएँ')}<select id={`${id}-filter`} value={filter} onChange={(event) => setFilter(event.target.value as CareStoryFilter)}><option value="All">{t('Everything', 'सभी नोट')}</option>{CARE_STORY_KINDS.map((kind) => <option key={kind} value={kind}>{kindLabel(kind)}</option>)}</select></label>
      <span>{visible.length} {t(visible.length === 1 ? 'event' : 'events', 'बातें')} · {t('Newest first', 'नई बातें पहले')}</span>
    </div>}

    {lastRemoved && <div className="care-story-undo" role="status"><span>{t('Removed', 'हटा दिया')}: {lastRemoved.title}</span><button type="button" onClick={undo}>{t('Undo', 'वापस लाएँ')}</button></div>}
    <p className="care-story-message" role="status">{message}</p>

    {visible.length > 0 ? <ol className="care-story-timeline" aria-label={t('Care timeline', 'देखभाल का क्रम')} tabIndex={0}>{visible.map((entry) => <li key={`${entry.fromRecord ? 'record' : 'family'}-${entry.id}`} data-kind={entry.kind}>
      <div className="care-story-date"><time dateTime={entry.date}>{new Date(`${entry.date}T00:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}</time></div>
      <article className="care-story-card">
        <div className="care-story-card-heading"><span>{kindLabel(entry.kind)}{entry.date > today && <> · {t('Planned', 'तय किया गया')}</>}</span>{!entry.fromRecord && <div><button type="button" onClick={() => edit(entry)} aria-label={`${t('Edit', 'बदलें')}: ${entry.title}`}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => remove(entry)} aria-label={`${t('Remove', 'हटाएँ')}: ${entry.title}`}>{t('Remove', 'हटाएँ')}</button></div>}</div>
        <h2>{entry.title}</h2>
        {(entry.details || entry.changeReason) && <div className="care-story-note">
          {entry.details && <p>{entry.details}</p>}
          {entry.changeReason && <div><h3>{t('What changed / reason as documented', 'क्या बदला / लिखी हुई वजह')}</h3><p>{entry.changeReason}</p></div>}
        </div>}
        <p className="care-story-author">{entry.fromRecord ? [entry.source, entry.sourceAuthor || entry.createdBy].filter(Boolean).join(' · ') : `${t('Family note', 'परिवार का नोट')} · ${entry.createdBy}`}{!entry.fromRecord && entry.source && <> · {entry.source}{entry.sourceAuthor && ` · ${entry.sourceAuthor}`}</>}{entry.updatedBy !== entry.createdBy && <> · {t('edited by', 'बदला')} {entry.updatedBy}</>}</p>
      </article>
    </li>)}</ol> : <p className="care-story-empty">{allEntries.length ? t('No events of this type yet.', 'इस तरह की कोई बात अभी नहीं है।') : t('No care records yet.', 'अभी कोई देखभाल रिकॉर्ड नहीं है।')}</p>}
  </section>;
}
