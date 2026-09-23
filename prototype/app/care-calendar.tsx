'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { Appointment } from './appointments-page';
import type { FamilyTask } from './family-care-state';
import { CARE_EVENT_KINDS, addCareEvent, calendarDays, calendarWeekDays, eventFallsOn, reportFileError, type CareCheck, type CareEvent, type CareEventKind, type CareReport } from './care-calendar-state';
import { FamilyReportComparison, FamilyReportLibrary } from './family-report-comparison';
import type { CareDocumentText } from './care-document-search';
import type { CareSaveStatus } from './care-local-store';
import { VisitInstructions } from './visit-instructions';

type Props = {
  patientId: string; author: string; today: string; hindi: boolean;
  appointments: Appointment[]; tasks: FamilyTask[]; events: CareEvent[]; checks: CareCheck[]; reports: CareReport[];
  reportSave: CareSaveStatus;
  focusReports?: boolean;
  documentText?: CareDocumentText[];
  onReadReport?: (id: string) => void;
  onTestResults: () => void;
  onAppointmentInstructions: (patientId: string, appointmentId: string, value: Appointment['preparationInstructions']) => void;
  onAdd: (event: CareEvent) => void; onCheck: (id: string, date: string, complete: boolean) => void;
  onUpdate?: (event: CareEvent) => void;
  onMedicines?: () => void;
  onTaskCheck: (id: string, complete: boolean) => void; onRemove: (id: string) => void;
  onRestore: (event: CareEvent, checks: CareCheck[]) => void;
  onReports: (reports: CareReport[]) => void; onRemoveReport: (id: string) => void; onCareTeam: () => void;
};

export function CareCalendar(props: Props) {
  return <CareCalendarContent key={`${props.patientId}:${props.author}`} {...props} />;
}

function CareCalendarContent({ patientId, author, today, hindi, appointments, tasks, events, checks, reports, reportSave, focusReports = false, documentText = [], onReadReport, onTestResults, onAppointmentInstructions, onAdd, onUpdate, onMedicines, onCheck, onTaskCheck, onRemove: removeEvent, onRestore, onReports, onRemoveReport: removeReport, onCareTeam }: Props) {
  const id = useId();
  const eventForm = useRef<HTMLFormElement>(null);
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [selected, setSelected] = useState(today);
  const [month, setMonth] = useState(today.slice(0, 7));
  const [expanded, setExpanded] = useState(false);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<CareEvent | null>(null);
  const [agendaMode, setAgendaMode] = useState<'day' | 'week'>('day');
  const [kind, setKind] = useState<CareEventKind>('Appointment');
  const [message, setMessage] = useState('');
  const [removed, setRemoved] = useState<{ event: CareEvent; checks: CareCheck[] } | null>(null);
  const [removedReport, setRemovedReport] = useState<CareReport | null>(null);
  const localEvents = events.filter((item) => item.patientId === patientId);
  const localVisits = appointments.filter((item) => item.hospitalId === patientId && !['Cancelled', 'Rescheduled'].includes(item.status));
  const localTasks = tasks.filter((item) => item.patientId === patientId && item.kind !== 'Question' && item.due);
  const localReports = reports.filter((item) => item.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const days = calendarDays(month);
  const week = calendarWeekDays(selected);
  const displayDate = (date: string, options: Intl.DateTimeFormatOptions) => new Date(`${date}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', options);
  const kindName = (value: string) => hindi ? ({ Chemotherapy: 'कीमोथेरेपी', Radiotherapy: 'रेडियोथेरेपी', Medicine: 'दवा', Appointment: 'मुलाकात', Test: 'जाँच', Procedure: 'प्रक्रिया', 'Follow-up': 'फॉलो-अप', Task: 'काम' }[value] ?? value) : value;
  const eventClass = (value: CareEventKind) => value === 'Medicine' ? 'care-agenda-medicine' : value === 'Chemotherapy' ? 'care-agenda-chemo' : value === 'Radiotherapy' ? 'care-agenda-radiotherapy' : 'care-agenda-visit';
  const agendaDates = agendaMode === 'week' ? week : [selected];

  useEffect(() => {
    if (adding) eventForm.current?.querySelector<HTMLInputElement>('input[name="title"]')?.focus();
  }, [adding, editing?.id]);

  function cancelForm() {
    setAdding(false); setEditing(null); setMessage('');
  }

  function editEvent(item: CareEvent) {
    if (item.kind === 'Medicine' && onMedicines) { onMedicines(); return; }
    setEditing(item); setKind(item.kind); setAdding(true); setMessage('');
  }

  function onRemove(eventId: string) {
    const event = localEvents.find((item) => item.id === eventId);
    if (!event) return;
    setRemoved({ event, checks: checks.filter((check) => check.patientId === patientId && check.eventId === eventId) });
    removeEvent(eventId);
    if (editing?.id === eventId) { setAdding(false); setEditing(null); }
    setMessage(event.repeatUntil ? t('Removed all days of this calendar entry.', 'इस कैलेंडर एंट्री के सभी दिन हटा दिए।') : t('Calendar entry removed.', 'कैलेंडर एंट्री हटा दी।'));
  }

  function onRemoveReport(reportId: string) {
    const report = localReports.find((item) => item.id === reportId);
    if (!report) return;
    setRemovedReport(report); removeReport(reportId);
  }

  function changeMonth(delta: number) {
    const date = new Date(`${month}-01T12:00:00Z`); date.setUTCMonth(date.getUTCMonth() + delta);
    const next = date.toISOString().slice(0, 10); setMonth(next.slice(0, 7)); setSelected(next);
  }

  function changeWeek(delta: number) {
    const date = new Date(`${selected}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + delta * 7);
    const next = date.toISOString().slice(0, 10); setSelected(next); setMonth(next.slice(0, 7));
  }

  function dayButton(date: string) {
    const items = localEvents.filter((item) => eventFallsOn(item, date));
    const hasVisit = localVisits.some((item) => item.date === date);
    const hasTask = localTasks.some((item) => item.due === date);
    const count = items.length + localVisits.filter((item) => item.date === date).length + localTasks.filter((item) => item.due === date).length;
    return <button key={date} type="button" className={`care-calendar-day${date.slice(0, 7) !== month ? ' outside-month' : ''}`} aria-pressed={selected === date} aria-label={`${displayDate(date, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}, ${count} ${t('items', 'काम')}`} aria-current={date === today ? 'date' : undefined} onClick={() => { setSelected(date); if (date.slice(0, 7) !== month) setMonth(date.slice(0, 7)); }}>
      <span>{Number(date.slice(-2))}</span><span className="care-calendar-dots" aria-hidden="true">{items.some((item) => item.kind === 'Medicine') && <i className="medicine" />}{items.some((item) => item.kind === 'Chemotherapy') && <i className="chemo" />}{items.some((item) => item.kind === 'Radiotherapy') && <i className="radiotherapy" />}{(hasVisit || items.some((item) => !['Medicine', 'Chemotherapy', 'Radiotherapy'].includes(item.kind))) && <i className="visit" />}{hasTask && <i className="task" />}</span>
    </button>;
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value: CareEvent = { id: editing?.id ?? crypto.randomUUID(), patientId, addedBy: editing?.addedBy ?? author, kind, title: String(data.get('title') ?? ''), date: String(data.get('date') ?? ''), time: String(data.get('time') ?? ''), instructions: String(data.get('instructions') ?? ''), repeatUntil: editing?.repeatUntil ? String(data.get('repeatUntil') ?? editing.repeatUntil) : '' };
    if (value.repeatUntil && value.repeatUntil < value.date) { setMessage(t('The last day must be on or after the first day.', 'आखिरी तारीख पहली तारीख से पहले नहीं हो सकती।')); return; }
    const validated = addCareEvent([], value)[0];
    if (!validated) { setMessage(t('Add a name, a valid date and the required instructions.', 'नाम, सही तारीख और ज़रूरी निर्देश जोड़ें।')); return; }
    if (editing) {
      if (!onUpdate) return;
      onUpdate(validated);
    } else onAdd(validated);
    const nextSelected = editing && eventFallsOn(validated, selected) ? selected : validated.date;
    setSelected(nextSelected); setMonth(nextSelected.slice(0, 7)); setAdding(false); setEditing(null);
    setMessage(editing ? t('Calendar entry updated.', 'कैलेंडर एंट्री बदल दी।') : t('Added to your calendar.', 'कैलेंडर में जोड़ दिया।'));
  }

  function agendaForDay(date: string) {
    const dueEvents = localEvents.filter((item) => eventFallsOn(item, date));
    const dueVisits = localVisits.filter((item) => item.date === date);
    const dueTasks = localTasks.filter((item) => item.due === date);
    const dateContext = agendaMode === 'week' ? `, ${displayDate(date, { day: 'numeric', month: 'short' })}` : '';
    return <section key={date} className={agendaMode === 'week' ? 'care-week-agenda-day' : undefined} aria-label={displayDate(date, { weekday: 'long', day: 'numeric', month: 'long' })}>
      {agendaMode === 'week' && <h3><time dateTime={date}>{displayDate(date, { weekday: 'short', day: 'numeric', month: 'short' })}</time>{date === today && <span>{t('Today', 'आज')}</span>}</h3>}
      <ul className="care-agenda-list">
        {[...dueVisits.map((visit) => ({ time: visit.time, node: <li className="care-agenda-visit" key={visit.id}><time>{visit.time}</time><div><span className="care-agenda-kind">{t('Appointment', 'मुलाकात')} · {visit.status}</span><strong>{visit.type}</strong><span>{visit.clinician} · {visit.mode}</span></div><button type="button" className="care-text-button" onClick={onCareTeam}>{t('Care team', 'देखभाल टीम')} →</button><VisitInstructions key={visit.id} value={visit.preparationInstructions} author={author} clinician={visit.clinician} hindi={hindi} onChange={(value) => onAppointmentInstructions(patientId, visit.id, value)} /></li> })),
        ...dueEvents.map((item) => {
          const checked = checks.find((check) => check.patientId === patientId && check.eventId === item.id && check.date === date);
          return { time: item.time || '99', node: <li key={item.id} className={`${eventClass(item.kind)}${checked ? ' is-done' : ''}`}><time>{item.time || '—'}</time><div><span className="care-agenda-kind">{kindName(item.kind)}</span><strong>{item.title}</strong>{item.instructions && <span>{item.instructions}</span>}<span className="care-entry-author">{checked ? `${t('Marked by', 'दर्ज किया')} ${checked.actor}` : `${t('Added by', 'जोड़ने वाले')} ${item.addedBy}`}</span></div><div className="care-agenda-actions"><button type="button" className="care-check-button" aria-pressed={Boolean(checked)} disabled={date > today} aria-label={`${checked ? t('Undo', 'वापस करें') : item.kind === 'Medicine' ? t('Mark taken', 'दवा ले ली') : t('Mark done', 'हो गया')}: ${item.title}${dateContext}`} onClick={() => { onCheck(item.id, date, !checked); setMessage(checked ? t('Undone.', 'वापस कर दिया।') : t('Marked for this day.', 'इस दिन के लिए दर्ज किया।')); }}>{checked ? '✓' : t(item.kind === 'Medicine' ? 'Taken' : 'Done', 'हो गया')}</button>{onUpdate && <button type="button" className="care-edit-event" aria-label={`${t('Edit', 'बदलें')} ${item.title}${item.repeatUntil ? t(' on all days', ' सभी दिनों के लिए') : ''}`} onClick={() => editEvent(item)}>{t('Edit', 'बदलें')}</button>}<button type="button" className="care-remove-event" aria-label={`${t('Remove', 'हटाएँ')} ${item.title}`} onClick={() => onRemove(item.id)}>{t('Remove', 'हटाएँ')}</button></div></li> };
        })].sort((a, b) => a.time.localeCompare(b.time)).map((row) => row.node)}
        {dueTasks.map((task) => <li key={task.id} className={`care-agenda-task${task.completedBy ? ' is-done' : ''}`}><span className="care-any-time">{t('Any time', 'कभी भी')}</span><div><span className="care-agenda-kind">{t('Task', 'काम')}</span><strong>{task.title}</strong><span>{task.completedBy || task.owner}</span></div><button type="button" className="care-check-button" aria-pressed={Boolean(task.completedBy)} aria-label={`${task.completedBy ? t('Undo', 'वापस करें') : t('Mark done', 'हो गया')}: ${task.title}${dateContext}`} onClick={() => onTaskCheck(task.id, !task.completedBy)}>{task.completedBy ? '✓' : t('Done', 'हो गया')}</button></li>)}
      </ul>
      {!dueEvents.length && !dueVisits.length && !dueTasks.length && <p className={agendaMode === 'week' ? 'care-week-empty' : 'care-calendar-empty'}>{t('Nothing planned for this day.', 'इस दिन के लिए कुछ नहीं जोड़ा है।')}</p>}
      {date > today && dueEvents.length > 0 && <p className="care-calendar-note">{t('You can mark these done on the day.', 'इन्हें उसी दिन पूरा हुआ दर्ज कर सकते हैं।')}</p>}
    </section>;
  }

  return <section className="care-calendar" aria-label={t('Care calendar', 'देखभाल का कैलेंडर')}>
    {!focusReports && <div className="care-calendar-grid">
      <div className="care-calendar-month">
        <div className="care-month-heading"><button type="button" aria-label={t('Previous month', 'पिछला महीना')} onClick={() => changeMonth(-1)}>‹</button><h2>{displayDate(`${month}-01`, { month: 'long', year: 'numeric' })}</h2><button type="button" aria-label={t('Next month', 'अगला महीना')} onClick={() => changeMonth(1)}>›</button></div>
        <div className="care-calendar-weekdays" aria-hidden="true">{(hindi ? ['सो', 'मं', 'बु', 'गु', 'शु', 'श', 'र'] : ['M', 'T', 'W', 'T', 'F', 'S', 'S']).map((day, index) => <span key={index}>{day}</span>)}</div>
        <div className={`care-calendar-dates ${expanded ? 'is-expanded' : ''}`}>{days.map(dayButton)}</div>
        {!expanded && <div className="care-calendar-week">{week.map(dayButton)}</div>}
        <div className="care-calendar-key"><span><i className="chemo" />{t('Chemotherapy', 'कीमोथेरेपी')}</span><span><i className="radiotherapy" />{t('Radiotherapy', 'रेडियोथेरेपी')}</span><span><i className="visit" />{t('Visits & tests', 'मुलाकात और जाँच')}</span>{localEvents.some((item) => item.kind === 'Medicine') && <span><i className="medicine" />{t('Medicines', 'दवाएँ')}</span>}{localTasks.length > 0 && <span><i className="task" />{t('Tasks', 'काम')}</span>}</div>
        <div className="care-calendar-shortcuts"><button className="care-text-button" type="button" onClick={() => { setSelected(today); setMonth(today.slice(0, 7)); }}>{t('Today', 'आज')}</button><button className="care-text-button care-month-toggle" type="button" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? t('Show week', 'हफ़्ता देखें') : t('Show month', 'महीना देखें')}</button></div>
      </div>
      <div className="care-day-agenda">
        <div className="family-section-heading"><h2>{agendaMode === 'week' ? t('Week', 'हफ़्ता') : selected === today ? t('Today', 'आज') : displayDate(selected, { weekday: 'long' })}<span className="care-selected-date">{agendaMode === 'week' ? `${displayDate(week[0], { day: 'numeric', month: 'short' })} – ${displayDate(week[6], { day: 'numeric', month: 'short' })}` : displayDate(selected, { day: 'numeric', month: 'short' })}</span></h2><button className="primary-button" type="button" aria-expanded={adding} aria-controls={`${id}-add`} onClick={() => { if (adding) cancelForm(); else { setEditing(null); setKind('Appointment'); setAdding(true); setMessage(''); } }}>{adding ? t('Cancel', 'रहने दें') : t('+ Add', '+ जोड़ें')}</button></div>
        <div className="care-agenda-view-controls"><div className="care-agenda-view" role="group" aria-label={t('Agenda view', 'कैलेंडर की सूची')}><button type="button" aria-pressed={agendaMode === 'day'} onClick={() => setAgendaMode('day')}>{t('Day', 'दिन')}</button><button type="button" aria-pressed={agendaMode === 'week'} onClick={() => setAgendaMode('week')}>{t('Week', 'हफ़्ता')}</button></div>{agendaMode === 'week' && <div className="care-agenda-week-controls"><button type="button" aria-label={t('Previous week', 'पिछला हफ़्ता')} onClick={() => changeWeek(-1)}>‹</button><button type="button" aria-label={t('Next week', 'अगला हफ़्ता')} onClick={() => changeWeek(1)}>›</button></div>}</div>
        {adding && <form key={editing?.id ?? 'new'} ref={eventForm} id={`${id}-add`} className="care-event-form" onSubmit={add}>
          {editing && <h3 className="care-event-wide">{t('Edit calendar entry', 'कैलेंडर एंट्री बदलें')}</h3>}
          <label>{t('What?', 'क्या?')}<select name="kind" value={kind} onChange={(event) => setKind(event.target.value as CareEventKind)}>{CARE_EVENT_KINDS.filter((value) => value !== 'Medicine' || editing?.kind === 'Medicine').map((value) => <option key={value} value={value}>{kindName(value)}</option>)}</select></label>
          <label>{kind === 'Medicine' ? t('Medicine name', 'दवा का नाम') : t('Name', 'नाम')}<input name="title" defaultValue={editing?.title ?? ''} required maxLength={160} /></label>
          <label>{t('Date', 'तारीख')}<input name="date" type="date" defaultValue={editing?.date ?? selected} required /></label>
          <label>{t('Time', 'समय')}<input name="time" type="time" defaultValue={editing?.time ?? ''} required={kind === 'Medicine'} /></label>
          <label className="care-event-wide">{kind === 'Medicine' ? t('Dose & instructions from the prescription', 'पर्चे में लिखी खुराक और निर्देश') : t('Doctor’s instructions (optional)', 'डॉक्टर के निर्देश (वैकल्पिक)')}<input name="instructions" defaultValue={editing?.instructions ?? ''} required={kind === 'Medicine'} maxLength={400} /></label>
          {editing?.repeatUntil && <label className="care-event-wide">{t('Last day of this saved daily entry', 'इस दर्ज रोज़ाना एंट्री का आखिरी दिन')}<input name="repeatUntil" type="date" defaultValue={editing.repeatUntil} /></label>}
          {editing?.repeatUntil && <p className="care-event-wide">{t('This updates all days of this entry. Old completion marks are kept only where they still match.', 'इस एंट्री के सभी दिन बदलेंगे। पूरे होने के पुराने निशान तभी रहेंगे जब वे अब भी सही दिन और जानकारी से मेल खाते हों।')}</p>}
          <p className="care-event-wide">{kind === 'Medicine' ? t('Copy the existing prescription. This calendar does not choose or change doses.', 'मौजूदा पर्चे से लिखें। यह कैलेंडर खुराक तय या बदलता नहीं है।') : t('This adds a personal reminder; it does not book with the hospital.', 'यह आपका रिमाइंडर है; अस्पताल में बुकिंग नहीं होती।')}</p>
          <div className="care-event-wide care-event-form-actions"><button type="submit" className="primary-button">{editing ? t('Save changes', 'बदलाव सेव करें') : t('Add to calendar', 'कैलेंडर में जोड़ें')}</button><button type="button" className="care-text-button" onClick={cancelForm}>{t('Cancel', 'रहने दें')}</button></div>
        </form>}
        <div className={agendaMode === 'week' ? 'care-week-agenda' : undefined}>{agendaDates.map(agendaForDay)}</div>
      </div>
    </div>}
    {focusReports && <section className="care-report-section care-report-page" aria-labelledby={`${id}-reports`}>
      <div className="family-section-heading"><h1 id={`${id}-reports`}>{t('My reports', 'मेरी रिपोर्ट')} {localReports.length > 0 && <span>({localReports.length})</span>}</h1><label className="care-report-upload">{t('+ Add report', '+ रिपोर्ट जोड़ें')}<input type="file" multiple accept="application/pdf,image/jpeg,image/png,image/webp" aria-label={t('Add reports', 'रिपोर्ट जोड़ें')} onChange={(event) => {
        const files = Array.from(event.target.files ?? []); if (!files.length) return;
        const accepted = files.filter((file) => !reportFileError(file));
        onReports(accepted.map((file) => ({ id: crypto.randomUUID(), patientId, addedBy: author, date: selected, file })));
        setMessage(files.length !== accepted.length ? t('Use PDF, JPG, PNG or WebP files, up to 10 MB each. Other files were not added.', 'PDF, JPG, PNG या WebP जोड़ें, हर फ़ाइल 10 MB तक। बाकी फ़ाइलें नहीं जोड़ी गईं।') : t('Reports added for the selected date.', 'चुनी हुई तारीख में रिपोर्ट जोड़ दी।'));
        event.target.value = '';
      }} /></label></div>
      <label className="care-report-date">{t('Report date', 'रिपोर्ट की तारीख')}<input type="date" value={selected} required onChange={(event) => { if (event.target.value) setSelected(event.target.value); }} /></label>
      {localReports.length > 0 && reportSave === 'unavailable' && <p className="care-calendar-note" role="alert">{t('Could not save these files. Download the originals before closing.', 'फ़ाइलें सेव नहीं हुईं। बंद करने से पहले मूल फ़ाइलें डाउनलोड करें।')}</p>}
      <FamilyReportLibrary key={patientId} patientId={patientId} hindi={hindi} reports={reports} documents={documentText} onRemove={onRemoveReport} onEditText={onReadReport} />
      {removedReport && <button type="button" className="care-text-button" onClick={() => { onReports([removedReport]); setRemovedReport(null); }}>{t('Undo last report removal', 'आखिरी हटाई रिपोर्ट वापस लाएँ')}</button>}
      <FamilyReportComparison key={patientId} patientId={patientId} reports={reports} hindi={hindi} />
      <button type="button" className="care-text-button" onClick={onTestResults}>{t('See results by test', 'जाँच के नाम से नतीजे देखें')} →</button>
    </section>}
    <p className="care-calendar-status" role="status">{message}</p>
    {removed && <button type="button" className="care-text-button" onClick={() => { onRestore(removed.event, removed.checks); setRemoved(null); setMessage(t('Calendar entry restored.', 'कैलेंडर एंट्री वापस आ गई।')); }}>{t('Undo last calendar removal', 'आखिरी हटाई एंट्री वापस लाएँ')}</button>}
  </section>;
}
