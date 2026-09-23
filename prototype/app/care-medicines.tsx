'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { eventFallsOn, type CareCheck, type CareEvent, type CareReport } from './care-calendar-state';
import type { CareDocumentText } from './care-document-state';
import { literalPrescriptionTime, makeMedicineEvent, medicinePrescription, prescriptionLines, type MedicineDraft } from './care-medicines-state';
import './care-medicines.css';

export type CareMedicinesProps = {
  patientId: string; author: string; today: string; hindi: boolean;
  events: CareEvent[]; checks: CareCheck[]; reports: CareReport[]; documents: CareDocumentText[];
  onAdd: (event: CareEvent) => void; onUpdate: (event: CareEvent) => void;
  onCheck: (eventId: string, date: string, complete: boolean) => void; onRemove: (eventId: string) => void;
};

function MedicineIcon({ kind = 'medicine' }: { kind?: 'medicine' | 'file' | 'check' | 'plus' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'file' ? <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>
      : kind === 'check' ? <path d="m5 12 4 4L19 6" /> : kind === 'plus' ? <path d="M12 5v14M5 12h14" />
        : <><path d="m8 4-4 4a5.66 5.66 0 0 0 8 8l4-4a5.66 5.66 0 0 0-8-8Z" /><path d="m6 6 8 8M18 15v6m-3-3h6" /></>}
  </svg>;
}

function PrescriptionLink({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <a ref={link} target="_blank" rel="noreferrer"><MedicineIcon kind="file" />{hindi ? 'पर्चा खोलें' : 'Open prescription'} <span aria-hidden="true">↗</span></a>;
}

export function CareMedicines(props: CareMedicinesProps) {
  return <MedicinesForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function MedicinesForPatient({ patientId, author, today, hindi, events, checks, reports, documents, onAdd, onUpdate, onCheck, onRemove }: CareMedicinesProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [date, setDate] = useState(today);
  const [view, setView] = useState<'day' | 'all'>('day');
  const [draft, setDraft] = useState<MedicineDraft | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [showSource, setShowSource] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const medicines = events.filter((event) => event.patientId === patientId && event.kind === 'Medicine').sort((a, b) => a.time.localeCompare(b.time) || a.title.localeCompare(b.title));
  const available = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const scheduled = medicines.filter((item) => eventFallsOn(item, date));
  const visible = view === 'day' ? scheduled : medicines;
  const checked = (item: CareEvent) => checks.find((check) => check.patientId === patientId && check.eventId === item.id && check.date === date);
  const takenCount = scheduled.filter((item) => checked(item)).length;
  const selectedReport = available.find((report) => report.id === draft?.reportId);
  const sourceLines = prescriptionLines(draft?.sourceText ?? '');
  const isEditing = draft !== null;
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });

  useEffect(() => { heading.current?.focus(); }, [editingId, isEditing]);

  function openEditor(item?: CareEvent) {
    const prescription = item ? medicinePrescription(item) : null;
    const report = available.find((entry) => entry.id === prescription?.reportId);
    setEditingId(item?.id ?? null);
    setDraft({
      title: item?.title ?? '', instructions: item?.instructions ?? '', time: item?.time ?? '',
      date: item?.date ?? today, lastDay: item?.repeatUntil || item?.date || today,
      reportId: report?.id ?? '', sourceName: prescription?.sourceName ?? '',
      sourceText: prescription?.sourceText ?? '', checked: false,
    });
    setError(''); setMessage(''); setShowSource(!prescription); setConfirmRemove(null);
  }

  function updateDraft(patch: Partial<MedicineDraft>) {
    if (!draft) return;
    setDraft({ ...draft, ...patch, checked: false }); setError('');
  }

  function chooseSource(reportId: string) {
    const report = available.find((item) => item.id === reportId);
    const source = documents.find((item) => item.patientId === patientId && item.reportId === reportId);
    updateDraft({ reportId: report?.id ?? '', sourceName: report?.file.name ?? '', sourceText: source?.text ?? '' });
    setShowSource(true);
  }

  function copyLine(line: string) {
    const time = literalPrescriptionTime(line);
    updateDraft({ instructions: line, ...(time ? { time } : {}) });
  }

  function closeEditor() { setDraft(null); setEditingId(null); setError(''); }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft) return;
    const medicine = makeMedicineEvent(draft, {
      id: editingId ?? crypto.randomUUID(), patientId, author,
      checkedAt: new Date().toISOString(), reportIds: available.map((report) => report.id),
    }, editingId ? medicines.find((item) => item.id === editingId) : undefined);
    if (!medicine) { setError(t('Check the prescription, time and dates before saving.', 'सेव करने से पहले पर्चा, समय और तारीखें जाँचें।')); return; }
    if (editingId) onUpdate(medicine); else onAdd(medicine);
    setView('day'); setDate(medicine.date <= today && (!medicine.repeatUntil || medicine.repeatUntil >= today) ? (eventFallsOn(medicine, today) ? today : medicine.date) : medicine.date);
    closeEditor(); setMessage(t('Medicine schedule saved.', 'दवा का समय सेव हो गया।'));
  }

  if (draft) return <section className="care-medicines" aria-labelledby={`${id}-edit-heading`}>
    <header className="medicine-heading"><div className="medicine-heading-title"><span className="medicine-heading-icon"><MedicineIcon /></span><h1 id={`${id}-edit-heading`} ref={heading} tabIndex={-1}>{editingId ? t('Edit medicine', 'दवा बदलें') : t('Add from prescription', 'पर्चे से दवा जोड़ें')}</h1></div><button type="button" onClick={closeEditor}>{t('Back', 'वापस')}</button></header>
    <form className="medicine-editor" onSubmit={save}>
      <section className="medicine-source-panel">
        <label htmlFor={`${id}-source`}>{t('Prescription', 'पर्चा')}<select id={`${id}-source`} value={draft.reportId} onChange={(event) => chooseSource(event.target.value)}><option value="">{t('Paste prescription text', 'पर्चे का टेक्स्ट जोड़ें')}</option>{available.map((report) => <option key={report.id} value={report.id}>{report.file.name} · {dateLabel(report.date)}</option>)}</select></label>
        {selectedReport ? <div className="medicine-source-heading"><strong>{selectedReport.file.name}</strong><PrescriptionLink report={selectedReport} hindi={hindi} /></div> : <label htmlFor={`${id}-source-name`}>{t('Prescription name or date', 'पर्चे का नाम या तारीख')}<input id={`${id}-source-name`} value={draft.sourceName} maxLength={240} required onChange={(event) => updateDraft({ sourceName: event.target.value })} /></label>}
        {selectedReport && <button type="button" className="medicine-source-toggle" aria-expanded={showSource} onClick={() => setShowSource(!showSource)}>{showSource ? t('Hide text', 'टेक्स्ट छिपाएँ') : t('Read prescription text', 'पर्चे का टेक्स्ट पढ़ें')}</button>}
        {(!selectedReport || showSource) && <label htmlFor={`${id}-source-text`}>{selectedReport ? t('Text from this prescription', 'इस पर्चे का टेक्स्ट') : t('Prescription text', 'पर्चे का टेक्स्ट')}<textarea id={`${id}-source-text`} rows={6} value={draft.sourceText} maxLength={200000} required={!selectedReport} onChange={(event) => updateDraft({ sourceText: event.target.value })} /></label>}
        {sourceLines.length > 0 && <details className="medicine-source-lines"><summary>{t('Use a line for the instructions', 'निर्देश के लिए एक पंक्ति चुनें')}</summary><ul>{sourceLines.map((line, index) => <li key={`${index}-${line.slice(0, 40)}`}><button type="button" onClick={() => copyLine(line)}>{line}</button></li>)}</ul></details>}
      </section>
      <section className="medicine-fields">
        <label htmlFor={`${id}-name`}>{t('Medicine name', 'दवा का नाम')}<input id={`${id}-name`} value={draft.title} required maxLength={160} onChange={(event) => updateDraft({ title: event.target.value })} /></label>
        <label htmlFor={`${id}-instructions`}>{t('Dose & instructions on the prescription', 'पर्चे पर लिखी खुराक और निर्देश')}<textarea id={`${id}-instructions`} rows={3} value={draft.instructions} required maxLength={400} onChange={(event) => updateDraft({ instructions: event.target.value })} /></label>
        <div className="medicine-schedule-fields"><label htmlFor={`${id}-time`}>{t('Time', 'समय')}<input id={`${id}-time`} type="time" value={draft.time} required onChange={(event) => updateDraft({ time: event.target.value })} /></label><label htmlFor={`${id}-first`}>{t('First day', 'पहला दिन')}<input id={`${id}-first`} type="date" value={draft.date} required onChange={(event) => updateDraft({ date: event.target.value, lastDay: draft.lastDay < event.target.value ? event.target.value : draft.lastDay })} /></label><label htmlFor={`${id}-last`}>{t('Last day', 'आखिरी दिन')}<input id={`${id}-last`} type="date" value={draft.lastDay} min={draft.date} required onChange={(event) => updateDraft({ lastDay: event.target.value })} /></label></div>
        <p className="medicine-repeat-caption">{draft.lastDay > draft.date ? t('At this time each day between these dates.', 'इन तारीखों के बीच रोज़ इस समय।') : t('One date, one time. Add another entry for another time.', 'एक तारीख, एक समय। दूसरे समय के लिए अलग एंट्री जोड़ें।')}</p>
        <label className="medicine-prescription-check"><input type="checkbox" checked={draft.checked} onChange={(event) => setDraft({ ...draft, checked: event.target.checked })} /><span>{t('Checked against prescription', 'पर्चे से मिलाकर जाँच लिया')}</span></label>
        {error && <p className="medicine-error" role="alert">{error}</p>}
        <div className="medicine-form-actions"><button type="submit" className="medicine-primary" disabled={!draft.checked || !draft.title.trim() || !draft.instructions.trim() || !draft.time || !draft.sourceName.trim() || (!draft.reportId && !draft.sourceText.trim())}>{t('Save medicine', 'दवा सेव करें')}</button><button type="button" onClick={closeEditor}>{t('Cancel', 'रद्द करें')}</button></div>
      </section>
    </form>
  </section>;

  return <section className="care-medicines" aria-labelledby={`${id}-heading`}>
    <header className="medicine-heading"><div className="medicine-heading-title"><span className="medicine-heading-icon"><MedicineIcon /></span><h1 id={`${id}-heading`} ref={heading} tabIndex={-1}>{t('Medicines', 'दवाएँ')}</h1></div><button type="button" className="medicine-primary" onClick={() => openEditor()}><MedicineIcon kind="plus" />{t('Add from prescription', 'पर्चे से जोड़ें')}</button></header>
    {message && <p className="medicine-message" role="status">{message}</p>}
    <div className="medicine-day-controls"><div className="medicine-tabs"><button type="button" aria-pressed={view === 'day'} onClick={() => { setView('day'); setDate(today); }}>{t('Today', 'आज')}</button><button type="button" aria-pressed={view === 'all'} onClick={() => setView('all')}>{t('All medicines', 'सभी दवाएँ')}</button></div>{view === 'day' && <input type="date" aria-label={t('Medicine schedule date', 'दवा की तारीख')} value={date} onChange={(event) => { if (event.target.value) setDate(event.target.value); }} />}</div>
    {view === 'day' && <dl className="medicine-overview"><div><dt>{date === today ? t('On today’s list', 'आज की सूची में') : dateLabel(date)}</dt><dd>{scheduled.length}</dd></div><div><dt>{t('Taken', 'ले ली')}</dt><dd>{takenCount}</dd></div><div><dt>{t('Not marked taken', 'लेना दर्ज नहीं किया')}</dt><dd>{scheduled.length - takenCount}</dd></div></dl>}
    <div className="medicine-list">{visible.map((item, index) => {
      const check = eventFallsOn(item, date) ? checked(item) : undefined;
      const prescription = medicinePrescription(item);
      const report = available.find((entry) => entry.id === prescription?.reportId);
      return <article className={`medicine-card medicine-color-${index % 3}${check ? ' is-taken' : ''}`} key={item.id}>
        <div className="medicine-time"><MedicineIcon /><time>{item.time}</time></div>
        <div className="medicine-card-body"><h2>{item.title}</h2><p className="medicine-directions">{item.instructions}</p><div className="medicine-card-meta"><span>{dateLabel(item.date)}{item.repeatUntil && ` – ${dateLabel(item.repeatUntil)}`}</span>{prescription && <span>{prescription.sourceName}</span>}</div>{report && <PrescriptionLink report={report} hindi={hindi} />}{!report && prescription?.sourceText && <details className="medicine-saved-source"><summary>{t('Read prescription', 'पर्चा पढ़ें')}</summary><p>{prescription.sourceText}</p></details>}</div>
        <div className="medicine-card-actions">{view === 'day' && <button type="button" className="medicine-taken" aria-pressed={Boolean(check)} disabled={date > today} onClick={() => { onCheck(item.id, date, !check); setMessage(check ? t('Taken mark removed.', 'लेने का निशान हटा दिया।') : t('Marked taken.', 'लेना दर्ज हो गया।')); }}><MedicineIcon kind="check" />{check ? t('Undo', 'वापस करें') : t('Taken', 'ले ली')}</button>}{check && <span className="medicine-taken-by">{t('Marked by', 'दर्ज किया')}: {check.actor}</span>}<button type="button" className="medicine-text-button" onClick={() => openEditor(item)}>{prescription ? t('Edit', 'बदलें') : t('Add prescription', 'पर्चा जोड़ें')}</button>{confirmRemove === item.id ? <div className="medicine-remove-confirm"><span>{t('Remove this schedule?', 'यह समय हटाएँ?')}</span><button type="button" onClick={() => { onRemove(item.id); setConfirmRemove(null); setMessage(t('Schedule removed.', 'समय हटा दिया।')); }}>{t('Remove', 'हटाएँ')}</button><button type="button" onClick={() => setConfirmRemove(null)}>{t('Keep', 'रहने दें')}</button></div> : <button type="button" className="medicine-text-button" onClick={() => setConfirmRemove(item.id)} aria-label={`${t('Remove schedule', 'समय हटाएँ')}: ${item.title}`}>{t('Remove', 'हटाएँ')}</button>}</div>
      </article>;
    })}</div>
    {!visible.length && <div className="medicine-empty"><span><MedicineIcon /></span><h2>{medicines.length ? t('Nothing scheduled for this day', 'इस दिन कोई दवा दर्ज नहीं है') : t('Keep your medicines together', 'अपनी दवाएँ एक जगह रखें')}</h2><button type="button" onClick={() => medicines.length ? setView('all') : openEditor()}>{medicines.length ? t('See all medicines', 'सभी दवाएँ देखें') : t('Add from prescription', 'पर्चे से जोड़ें')}</button></div>}
  </section>;
}
