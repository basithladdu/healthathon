'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { CARE_CENTRES, DIRECTORY_CHECKED_ON, centreDirectionsUrl, filterCareCentres } from './care-directory-data';
import {
  addSupportContactNote, addSupportPlace, completeSupportFollowUp, orderedSupportNotes, removeSupportContactNote, removeSupportPlace,
  restoreSupportContactNote, restoreSupportPlace, setSupportFollowUp, supportPlacesText, updateSupportContactNote,
  validSupportFollowUp, validateSupportContactDraft,
  type RemovedSupportNote, type SupportContactNote, type SupportPlace,
} from './family-support-state';
import './family-support-places.css';

export type { SupportPlace } from './family-support-state';

type Props = { patientId: string; author: string; today: string; hindi: boolean; entries: SupportPlace[]; onChange: (entries: SupportPlace[]) => void };
type Editor = { placeId: string; noteId: string | null; mode: 'note' | 'follow-up'; date: string; person: string; reply: string; nextStep: string; followUpOn: string };
type Undo = { kind: 'place'; place: SupportPlace; index: number } | { kind: 'note'; removed: RemovedSupportNote };

export function FamilySupportPlaces(props: Props) {
  return <SupportPlacesForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function SupportPlacesForPatient({ patientId, author, today, hindi, entries, onChange }: Props) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const places = entries.filter((entry) => entry.patientId === patientId);
  const [choosing, setChoosing] = useState(places.length === 0);
  const [query, setQuery] = useState('');
  const [editor, setEditor] = useState<Editor | null>(null);
  const [undoItems, setUndoItems] = useState<Undo[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const formRef = useRef<HTMLFormElement>(null);
  const matches = filterCareCentres(query, '', '');
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const editingPlaceId = editor?.placeId;
  const editingNoteId = editor?.noteId;
  const editingMode = editor?.mode;

  useEffect(() => { if (editingPlaceId) formRef.current?.focus(); }, [editingPlaceId, editingNoteId, editingMode]);

  function savePlace(centreId: string) {
    const updated = addSupportPlace(entries, patientId, centreId, author, crypto.randomUUID());
    if (updated === entries) return;
    onChange(updated); setChoosing(false); setQuery(''); setMessage(t('Place saved.', 'जगह सेव हो गई।'));
  }

  function openEditor(place: SupportPlace, mode: 'note' | 'follow-up', note?: SupportContactNote) {
    setEditor({ placeId: place.id, noteId: note?.id ?? null, mode, date: note?.date ?? today, person: note?.person ?? '', reply: note?.reply ?? '', nextStep: place.nextStep, followUpOn: place.followUpOn });
    setError(''); setMessage('');
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor) return;
    const draft = { date: editor.date, person: editor.person, reply: editor.reply };
    const invalid = editor.mode === 'note' ? validateSupportContactDraft(draft, today) : null;
    if (invalid) {
      setError(invalid === 'date' ? t('Choose a real contact date, up to today.', 'संपर्क की सही तारीख चुनें, आज तक की।') : invalid === 'person' ? t('Keep the name under 100 characters.', 'नाम 100 अक्षरों तक रखें।') : t('Write what happened, up to 3,000 characters.', 'क्या बात हुई, 3,000 अक्षरों तक लिखें।'));
      return;
    }
    if (!validSupportFollowUp(editor.nextStep, editor.followUpOn)) { setError(t('Add a next step before choosing its date.', 'तारीख चुनने से पहले अगला काम लिखें।')); return; }
    let updated = entries;
    if (editor.mode === 'note') updated = editor.noteId
      ? updateSupportContactNote(updated, patientId, editor.placeId, editor.noteId, author, today, draft)
      : addSupportContactNote(updated, patientId, editor.placeId, author, today, crypto.randomUUID(), draft);
    updated = setSupportFollowUp(updated, patientId, editor.placeId, editor.nextStep, editor.followUpOn);
    onChange(updated); setEditor(null); setError(''); setMessage(t('Your note is saved.', 'आपका नोट सेव हो गया।'));
  }

  function removePlace(place: SupportPlace) {
    setUndoItems((items) => [...items, { kind: 'place', place, index: entries.findIndex((entry) => entry.patientId === patientId && entry.id === place.id) }]);
    onChange(removeSupportPlace(entries, patientId, place.id));
    if (editor?.placeId === place.id) setEditor(null);
    setMessage(t('Place removed.', 'जगह हटा दी।'));
  }

  function removeNote(place: SupportPlace, note: SupportContactNote) {
    setUndoItems((items) => [...items, { kind: 'note', removed: { patientId, placeId: place.id, note, index: place.notes.findIndex((item) => item.id === note.id) } }]);
    onChange(removeSupportContactNote(entries, patientId, place.id, note.id));
    if (editor?.noteId === note.id) setEditor(null);
    setMessage(t('Call note removed.', 'कॉल का नोट हटा दिया।'));
  }

  function undo() {
    const last = undoItems.at(-1);
    if (!last) return;
    const updated = last.kind === 'place' ? restoreSupportPlace(entries, patientId, last.place, last.index) : restoreSupportContactNote(entries, patientId, last.removed);
    if (updated === entries) { setMessage(t('Already restored, or its place is no longer saved.', 'पहले से मौजूद है, या उस जगह को हटा दिया गया है।')); return; }
    onChange(updated); setUndoItems((items) => items.slice(0, -1)); setMessage(t('Restored.', 'वापस जोड़ दिया।'));
  }

  function download() {
    const url = URL.createObjectURL(new Blob([supportPlacesText(entries, patientId)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saanthvana-places-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`;
    document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setMessage(t('Your copy is downloading.', 'आपकी कॉपी डाउनलोड हो रही है।'));
  }

  function editForm(place: SupportPlace) {
    if (!editor || editor.placeId !== place.id) return null;
    return <form ref={formRef} tabIndex={-1} className="support-place-form" onSubmit={save} aria-label={editor.mode === 'note' ? t('Your call note', 'आपके कॉल का नोट') : t('Your next step', 'आपका अगला काम')}>
      <h3>{editor.mode === 'note' ? editor.noteId ? t('Edit call note', 'कॉल का नोट बदलें') : t('Add a call note', 'कॉल का नोट जोड़ें') : t('Next step', 'अगला काम')}</h3>
      {editor.mode === 'note' && <>
        <div className="support-form-row"><label htmlFor={`${id}-date`}>{t('Contact date', 'संपर्क की तारीख')}<input id={`${id}-date`} type="date" value={editor.date} max={today} required onChange={(event) => setEditor({ ...editor, date: event.target.value })} /></label>
          <label htmlFor={`${id}-person`}>{t('Spoke to (optional)', 'किससे बात हुई (चाहें तो)')}<input id={`${id}-person`} value={editor.person} maxLength={100} onChange={(event) => setEditor({ ...editor, person: event.target.value })} /></label></div>
        <label htmlFor={`${id}-reply`}>{t('What did they say?', 'उन्होंने क्या कहा?')}<textarea id={`${id}-reply`} value={editor.reply} required maxLength={3000} rows={3} onChange={(event) => setEditor({ ...editor, reply: event.target.value })} /></label>
      </>}
      <label htmlFor={`${id}-next`}>{editor.mode === 'note' ? t('Next step (optional)', 'अगला काम (चाहें तो)') : t('What will you do next?', 'अब क्या करेंगे?')}<input id={`${id}-next`} value={editor.nextStep} maxLength={500} onChange={(event) => setEditor({ ...editor, nextStep: event.target.value })} /></label>
      <label htmlFor={`${id}-follow-up`}>{t('Follow up on (optional)', 'कब करना है (चाहें तो)')}<input id={`${id}-follow-up`} type="date" value={editor.followUpOn} onChange={(event) => setEditor({ ...editor, followUpOn: event.target.value })} /></label>
      {error && <p className="support-form-error" role="alert">{error}</p>}
      <div className="support-form-actions"><button className="primary-button" type="submit">{t('Save', 'सेव करें')}</button><button type="button" onClick={() => { setEditor(null); setError(''); }}>{t('Cancel', 'रहने दें')}</button></div>
    </form>;
  }

  return <section className="family-support-places" aria-labelledby={`${id}-heading`}>
    <div className="support-places-heading"><h1 id={`${id}-heading`}>{t('Places I’ve checked', 'मदद के लिए चुनी जगहें')}</h1><div><button type="button" disabled={!places.length} onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button><button type="button" className="primary-button" aria-expanded={choosing} onClick={() => setChoosing(!choosing)}>{choosing ? t('Close list', 'सूची बंद करें') : t('Add a place', 'जगह जोड़ें')}</button></div></div>
    {choosing && <section className="support-place-picker" aria-label={t('Choose a care centre', 'देखभाल केंद्र चुनें')}>
      <label htmlFor={`${id}-search`}>{t('City or centre', 'शहर या केंद्र')}<input id={`${id}-search`} type="search" value={query} placeholder={t('Search the directory', 'सूची में ढूँढें')} onChange={(event) => setQuery(event.target.value)} /></label>
      <div className="support-picker-results">{matches.map((centre) => {
        const saved = places.some((place) => place.centreId === centre.id);
        return <article key={centre.id}><div><h2>{centre.name}</h2><span>{centre.city} · {centre.state}</span></div><button type="button" disabled={saved} aria-label={saved ? `${centre.name} ${t('saved', 'सेव है')}` : `${t('Save', 'सेव करें')} ${centre.name}`} onClick={() => savePlace(centre.id)}>{saved ? t('Saved', 'सेव है') : t('Save', 'सेव करें')}</button></article>;
      })}</div>
      {!matches.length && <p>{t('No match in this list. Try another city or name.', 'इस सूची में नहीं मिला। दूसरा शहर या नाम लिखें।')}</p>}
      <p className="support-source-note">{t(`Selected Pallium India listings · checked ${DIRECTORY_CHECKED_ON}.`, `Pallium India की चुनी सूची · जाँची गई: ${DIRECTORY_CHECKED_ON}।`)}</p>
    </section>}
    <div className="support-place-list">{places.map((place) => {
      const centre = CARE_CENTRES.find((item) => item.id === place.centreId);
      if (!centre) return null;
      const callNotes = orderedSupportNotes(place);
      const lastNote = callNotes[0];
      return <article className="support-place-card" key={place.id}>
        <div className="support-place-title"><div><h2>{centre.name}</h2><p>{centre.city} · {centre.state}</p></div><button type="button" className="support-text-button" aria-label={`${t('Remove', 'हटाएँ')} ${centre.name}`} onClick={() => removePlace(place)}>{t('Remove', 'हटाएँ')}</button></div>
        <div className="support-place-links"><a href={`tel:${centre.phone}`}>{t('Call', 'फ़ोन करें')} {centre.phone}</a><a href={centreDirectionsUrl(centre)} target="_blank" rel="noreferrer">{t('Directions', 'रास्ता देखें')} ↗</a><a href={centre.directoryUrl} target="_blank" rel="noreferrer">{t('Directory entry', 'मूल सूची')} ↗</a></div>
        {centre.phoneNote && <p className="support-source-note">{centre.phoneNote}</p>}
        {lastNote && <div className="support-latest-note"><h3>{t('Your latest call note', 'आपके आखिरी कॉल का नोट')}</h3><p className="support-call-date">{dateLabel(lastNote.date)}{lastNote.person && <> · {lastNote.person}</>}</p><p className="support-exact-reply">{lastNote.reply}</p></div>}
        {place.nextStep.trim() && <div className={`support-next-step${place.followUpDoneBy ? ' is-done' : ''}`}><div><h3>{t('Next step', 'अगला काम')}{place.followUpOn && <> · {dateLabel(place.followUpOn)}</>}</h3><p>{place.nextStep}</p>{place.followUpDoneBy && <span>{t('Marked done by', 'पूरा हुआ दर्ज किया')} {place.followUpDoneBy}</span>}</div><button type="button" aria-pressed={Boolean(place.followUpDoneBy)} onClick={() => {
          onChange(completeSupportFollowUp(entries, patientId, place.id, author, !place.followUpDoneBy)); setMessage(place.followUpDoneBy ? t('Follow-up reopened.', 'काम फिर से जोड़ दिया।') : t('Follow-up marked done.', 'काम पूरा हुआ दर्ज किया।'));
        }}>{place.followUpDoneBy ? t('Reopen', 'फिर से खोलें') : t('Done', 'हो गया')}</button></div>}
        <div className="support-place-actions"><button type="button" className="primary-button" onClick={() => openEditor(place, 'note')}>{t('Add call note', 'कॉल का नोट जोड़ें')}</button><button type="button" onClick={() => openEditor(place, 'follow-up')}>{place.nextStep.trim() ? t('Edit next step', 'अगला काम बदलें') : t('Add next step', 'अगला काम जोड़ें')}</button></div>
        {editForm(place)}
        {place.notes.length > 0 && <details className="support-call-history"><summary>{t('All call notes', 'सभी कॉल के नोट')} ({place.notes.length})</summary><ol>{callNotes.map((note) => <li key={note.id}>
          <div className="support-note-heading"><strong>{dateLabel(note.date)}{note.person && <> · {note.person}</>}</strong><div><button type="button" className="support-text-button" aria-label={`${t('Edit call note', 'कॉल का नोट बदलें')}: ${note.date}, ${centre.name}`} onClick={() => openEditor(place, 'note', note)}>{t('Edit', 'बदलें')}</button><button type="button" className="support-text-button" aria-label={`${t('Remove call note', 'कॉल का नोट हटाएँ')}: ${note.date}, ${centre.name}`} onClick={() => removeNote(place, note)}>{t('Remove', 'हटाएँ')}</button></div></div>
          <p className="support-exact-reply">{note.reply}</p><span className="support-note-author">{t('Family note · added by', 'परिवार का नोट · जोड़ा')} {note.addedBy}{note.updatedBy !== note.addedBy && <> · {t('edited by', 'बदला')} {note.updatedBy}</>}</span>
        </li>)}</ol></details>}
      </article>;
    })}</div>
    {!places.length && !choosing && <p>{t('Save a place to keep your calls and next steps together.', 'जगह सेव करें ताकि कॉल और अगले काम साथ रहें।')}</p>}
    {places.length > 0 && !choosing && <p className="support-source-note">{t(`Pallium India listings · checked ${DIRECTORY_CHECKED_ON}.`, `Pallium India की सूची · जाँची गई: ${DIRECTORY_CHECKED_ON}।`)}</p>}
    <div className="support-places-status"><p role="status">{message}</p>{undoItems.length > 0 && <button type="button" className="support-text-button" onClick={undo}>{t('Undo last removal', 'पिछला हटाया वापस लाएँ')}</button>}</div>
  </section>;
}
