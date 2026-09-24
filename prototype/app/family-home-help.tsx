'use client';

import { useId, useRef, useState, type FormEvent } from 'react';
import {
  HOME_HELP_CATEGORIES, HOME_HELP_STATUSES, addHomeHelpEntry, updateHomeHelpEntry,
  setHomeHelpStatus, removeHomeHelpEntry, restoreHomeHelpEntry, selectHomeHelpEntries,
  validateHomeHelpInput, homeHelpText, type HomeHelpCategory, type HomeHelpEntry, type HomeHelpStatus,
} from './family-home-help-state';
import './family-home-help.css';

export type FamilyHomeHelpProps = {
  patientId: string; author: string; today: string; hindi: boolean;
  entries: HomeHelpEntry[]; onChange: (entries: HomeHelpEntry[]) => void;
};

const labels: Record<HomeHelpCategory, { hi: string; title: string; hindiTitle: string }> = {
  Transport: { hi: 'आने-जाने में मदद', title: 'Arrange a ride', hindiTitle: 'आने-जाने का इंतज़ाम' },
  'Home nursing': { hi: 'घर पर नर्सिंग', title: 'Arrange home nursing', hindiTitle: 'घर पर नर्सिंग का इंतज़ाम' },
  'Medicines or equipment delivery': { hi: 'दवा या उपकरण की डिलीवरी', title: 'Arrange medicine or equipment delivery', hindiTitle: 'दवा या उपकरण घर मँगाना' },
  'Home help or nursing': { hi: 'घर पर मदद या नर्सिंग', title: 'Find help at home', hindiTitle: 'घर पर मदद का इंतज़ाम' },
  Equipment: { hi: 'सामान का इंतज़ाम', title: 'Arrange equipment', hindiTitle: 'ज़रूरी सामान का इंतज़ाम' },
  Paperwork: { hi: 'कागज़ी काम', title: 'Sort the paperwork', hindiTitle: 'कागज़ी काम पूरा करना' },
  Company: { hi: 'साथ देना', title: 'Spend time together', hindiTitle: 'साथ में समय बिताना' },
  'Caregiver break': { hi: 'देखभाल करने वाले को आराम', title: 'Give the caregiver a break', hindiTitle: 'देखभाल करने वाले को थोड़ा आराम देना' },
};
const statusHindi = { 'To arrange': 'इंतज़ाम बाकी है', Arranged: 'इंतज़ाम हो गया', Done: 'पूरा हो गया' };

export function FamilyHomeHelp(props: FamilyHomeHelpProps) {
  return <HomeHelpForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function HomeHelpForPatient({ patientId, author, today, hindi, entries, onChange }: FamilyHomeHelpProps) {
  const id = useId();
  const form = useRef<HTMLFormElement>(null);
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [category, setCategory] = useState<HomeHelpCategory | ''>('');
  const [title, setTitle] = useState('');
  const [helper, setHelper] = useState('');
  const [phone, setPhone] = useState('');
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [filter, setFilter] = useState<HomeHelpStatus | 'All'>('All');
  const [removed, setRemoved] = useState<HomeHelpEntry[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const patientEntries = selectHomeHelpEntries(entries, patientId);
  const visible = selectHomeHelpEntries(entries, patientId, filter);
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });

  function chooseCategory(next: HomeHelpCategory) {
    const wasDefault = category && (title === labels[category].title || title === labels[category].hindiTitle);
    if (!title.trim() || wasDefault) setTitle(t(labels[next].title, labels[next].hindiTitle));
    setCategory(next);
  }

  function clearForm() {
    setCategory(''); setTitle(''); setHelper(''); setPhone(''); setDate(''); setNote(''); setEditingId(null); setDetailsOpen(false); setError('');
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = { category: category as HomeHelpCategory, title, helper, phone, date, note };
    const invalid = validateHomeHelpInput(input);
    const errors = {
      category: t('Choose the kind of help you need.', 'किस तरह की मदद चाहिए, चुनें।'),
      title: t('Write the need in 160 characters or fewer.', 'मदद के बारे में 160 अक्षरों तक लिखें।'),
      helper: t('Keep the helper name within 80 characters.', 'मदद करने वाले का नाम 80 अक्षरों तक रखें।'),
      phone: t('Check the phone number, or leave it blank.', 'फ़ोन नंबर जाँचें, या खाली छोड़ें।'),
      date: t('Choose a real date, or leave it blank.', 'सही तारीख चुनें, या खाली छोड़ें।'),
      note: t('Keep the note within 500 characters.', 'नोट 500 अक्षरों तक रखें।'),
    };
    if (invalid) { setError(errors[invalid]); if (invalid !== 'category') setDetailsOpen(true); return; }
    const next = editingId ? updateHomeHelpEntry(entries, patientId, editingId, input, author)
      : addHomeHelpEntry(entries, { ...input, id: crypto.randomUUID(), patientId, author, status: 'To arrange' });
    if (next === entries) { setError(t('This could not be saved. Try adding it again.', 'यह सेव नहीं हुआ। फिर से जोड़ें।')); return; }
    onChange(next); setFilter('All'); setMessage(t(editingId ? 'Changes saved.' : 'Added to the list.', editingId ? 'बदलाव सेव हो गए।' : 'सूची में जोड़ दिया।')); clearForm();
  }

  function edit(entry: HomeHelpEntry) {
    setCategory(entry.category); setTitle(entry.title); setHelper(entry.helper); setPhone(entry.phone); setDate(entry.date); setNote(entry.note);
    setEditingId(entry.id); setDetailsOpen(true); setError(''); setMessage('');
    form.current?.scrollIntoView({ block: 'start', behavior: 'smooth' }); form.current?.focus({ preventScroll: true });
  }

  function changeStatus(entry: HomeHelpEntry, status: HomeHelpStatus) {
    onChange(setHomeHelpStatus(entries, patientId, entry.id, status));
    setMessage(t(`${entry.title}: ${status.toLowerCase()}.`, `${entry.title}: ${statusHindi[status]}।`));
  }

  function remove(entry: HomeHelpEntry) {
    onChange(removeHomeHelpEntry(entries, patientId, entry.id)); setRemoved((previous) => [...previous, entry]);
    if (editingId === entry.id) clearForm();
    setMessage(t('Removed from the list.', 'सूची से हटा दिया।'));
  }

  function undo() {
    const entry = removed.at(-1);
    if (!entry) return;
    const next = restoreHomeHelpEntry(entries, patientId, entry);
    if (next === entries) { setMessage(t('This item could not be restored.', 'यह काम वापस नहीं आया।')); return; }
    onChange(next); setRemoved((previous) => previous.slice(0, -1)); setFilter('All'); setMessage(t('Added back to the list.', 'फिर से सूची में जोड़ दिया।'));
  }

  function download() {
    const url = URL.createObjectURL(new Blob([homeHelpText(entries, patientId, today, hindi)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saathi-help-at-home-${today}.txt`; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setMessage(t('Your list is ready to download.', 'आपकी सूची डाउनलोड के लिए तैयार है।'));
  }

  return <section className="family-home-help" aria-labelledby={`${id}-title`}>
    <div className="family-workspace-heading"><h1 id={`${id}-title`}>{t('Help at home', 'घर पर मदद')}</h1><button type="button" className="secondary-button" disabled={!patientEntries.length} onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div>
    <div className="home-help-layout">
      <form ref={form} className="home-help-form" tabIndex={-1} onSubmit={save} aria-label={t(editingId ? 'Edit a need' : 'Add a need', editingId ? 'मदद की ज़रूरत बदलें' : 'मदद की ज़रूरत जोड़ें')}>
        <h2>{t(editingId ? 'Edit the details' : 'What do you need?', editingId ? 'जानकारी बदलें' : 'क्या मदद चाहिए?')}</h2>
        <div className="home-help-categories">{HOME_HELP_CATEGORIES.map((value) => <button key={value} type="button" aria-pressed={category === value} onClick={() => chooseCategory(value)}>{t(value, labels[value].hi)}</button>)}</div>
        {editingId && category && !HOME_HELP_CATEGORIES.some((value) => value === category) && <p className="home-help-previous-category">{t('Saved category', 'दर्ज श्रेणी')}: {t(category, labels[category].hi)}</p>}
        <details open={detailsOpen} onToggle={(event) => setDetailsOpen(event.currentTarget.open)} className="home-help-details"><summary>{t('Add a person, date or note', 'नाम, तारीख या नोट जोड़ें')}</summary><div>
          <label>{t('What needs doing?', 'क्या करना है?')}<input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={160} /></label>
          <label>{t('Who is helping?', 'कौन मदद कर रहा है?')}<input value={helper} onChange={(event) => setHelper(event.target.value)} maxLength={80} autoComplete="off" /></label>
          <div className="home-help-fields"><label>{t('Phone', 'फ़ोन')}<input type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} maxLength={30} autoComplete="off" /></label><label>{t('Day', 'दिन')}<input type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label></div>
          <label>{t('Note', 'नोट')}<textarea rows={2} value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} /></label>
        </div></details>
        {error && <p className="home-help-error" role="alert">{error}</p>}
        <div className="home-help-form-actions"><button type="submit" className="primary-button">{t(editingId ? 'Save changes' : 'Add to list', editingId ? 'बदलाव सेव करें' : 'सूची में जोड़ें')}</button>{editingId && <button type="button" className="secondary-button" onClick={() => { clearForm(); setMessage(t('Changes cancelled.', 'बदलाव रद्द हो गए।')); }}>{t('Cancel', 'रद्द करें')}</button>}</div>
      </form>
      <section className="home-help-list" aria-labelledby={`${id}-list`}>
        <div className="family-section-heading"><h2 id={`${id}-list`}>{t('Our list', 'हमारी सूची')}</h2><span>{visible.length}</span></div>
        <div className="home-help-filters" aria-label={t('Filter arrangements', 'इंतज़ाम के हिसाब से देखें')}>{(['All', ...HOME_HELP_STATUSES] as const).map((value) => <button type="button" key={value} aria-pressed={filter === value} onClick={() => setFilter(value)}>{value === 'All' ? t('All', 'सभी') : t(value, statusHindi[value])}</button>)}</div>
        {visible.map((entry) => <article key={entry.id} className={`home-help-card${entry.status === 'Done' ? ' is-done' : ''}`}>
          <div className="home-help-card-heading"><h3>{entry.title}</h3><span>{t(entry.status, statusHindi[entry.status])}</span></div>
          <p className="home-help-category">{t(entry.category, labels[entry.category].hi)}{entry.date && <> · <time dateTime={entry.date}>{dateLabel(entry.date)}</time></>}</p>
          <p className="home-help-person">{entry.helper || t('No helper added yet', 'मदद करने वाले का नाम अभी नहीं जोड़ा है')}{entry.phone && <> · <a href={`tel:${entry.phone.replace(/[^+\d]/g, '')}`}>{t('Call', 'फ़ोन करें')} {entry.phone}</a></>}</p>
          {entry.note && <p className="home-help-note">{entry.note}</p>}
          <div className="home-help-card-actions"><button type="button" className="home-help-next" onClick={() => changeStatus(entry, entry.status === 'To arrange' ? 'Arranged' : entry.status === 'Arranged' ? 'Done' : 'To arrange')} aria-label={`${entry.status === 'To arrange' ? t('Mark arranged', 'इंतज़ाम हो गया') : entry.status === 'Arranged' ? t('Mark done', 'पूरा हुआ') : t('Reopen', 'फिर खोलें')}: ${entry.title}`}>{entry.status === 'To arrange' ? t('Mark arranged', 'इंतज़ाम हो गया') : entry.status === 'Arranged' ? t('Mark done', 'पूरा हुआ') : t('Reopen', 'फिर खोलें')}</button>{entry.status === 'Arranged' && <button type="button" onClick={() => changeStatus(entry, 'To arrange')} aria-label={`${t('Reopen', 'फिर खोलें')}: ${entry.title}`}>{t('Reopen', 'फिर खोलें')}</button>}<button type="button" onClick={() => edit(entry)} aria-label={`${t('Edit', 'बदलें')}: ${entry.title}`}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => remove(entry)} aria-label={`${t('Remove', 'हटाएँ')}: ${entry.title}`}>{t('Remove', 'हटाएँ')}</button></div>
        </article>)}
        {!visible.length && <p className="home-help-empty">{patientEntries.length ? t('Nothing in this list right now.', 'अभी इस सूची में कोई काम नहीं है।') : t('Add something your family needs help with.', 'परिवार को जिस काम में मदद चाहिए, वह जोड़ें।')}</p>}
      </section>
    </div>
    <div className="home-help-status"><p role="status">{message}</p>{removed.length > 0 && <button type="button" className="care-text-button" onClick={undo}>{t('Undo last removal', 'पिछला हटाया काम वापस लाएँ')}</button>}</div>
  </section>;
}
