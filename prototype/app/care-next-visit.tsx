'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { Appointment } from './appointments-page';
import type { CareEvent, CareReport } from './care-calendar-state';
import { addFamilyTask, type FamilyTask, type FamilyTaskKind } from './family-care-state';
import { downloadCarePdf } from './care-pdf';
import './care-next-visit.css';

export type CareNextVisitProps = {
  patientId: string; patientName: string; author: string; today: string; hindi: boolean;
  appointments: Appointment[]; events: CareEvent[]; tasks: FamilyTask[]; reports: CareReport[];
  onAddTask: (task: FamilyTask) => void;
  onCompleteTask: (id: string, complete: boolean) => void;
  onOpen: (view: 'calendar' | 'reports' | 'family-tasks' | 'visit-questions') => void;
};
type Visit = { id: string; source: 'appointment' | 'calendar'; title: string; date: string; time: string; clinician: string; mode: string; instructions: string; instructionAuthor: string };

function VisitIcon({ kind = 'calendar' }: { kind?: 'calendar' | 'file' | 'question' | 'help' | 'download' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'file' ? <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>
      : kind === 'question' ? <><path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9H13a8.5 8.5 0 0 1 8 8v.5Z" /><path d="M10 9a2 2 0 0 1 4 .5c0 1.5-2 1.5-2 3M12 16h.01" /></>
        : kind === 'help' ? <><circle cx="9" cy="7" r="3" /><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M21 20v-2a6 6 0 0 0-3-5" /></>
          : kind === 'download' ? <><path d="M12 3v12m-5-5 5 5 5-5M5 17v4h14v-4" /></>
            : <><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M7 3v4M17 3v4M3 11h18M8 15h2M14 15h2" /></>}
  </svg>;
}

function VisitReportLink({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <a ref={link} target="_blank" rel="noreferrer"><span className="visit-report-icon"><VisitIcon kind="file" /></span><span><strong>{report.file.name}</strong><time dateTime={report.date}>{new Date(`${report.date}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}</time></span><span aria-hidden="true">↗</span></a>;
}

export function CareNextVisit(props: CareNextVisitProps) {
  return <NextVisitForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function NextVisitForPatient({ patientId, patientName, author, today, hindi, appointments, events, tasks, reports, onAddTask, onCompleteTask, onOpen }: CareNextVisitProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [question, setQuestion] = useState('');
  const [taskTitle, setTaskTitle] = useState('');
  const [taskKind, setTaskKind] = useState<FamilyTaskKind>('Transport');
  const [owner, setOwner] = useState('');
  const [message, setMessage] = useState('');
  const [savingPdf, setSavingPdf] = useState(false);
  const helpInput = useRef<HTMLInputElement>(null);
  const candidates: Visit[] = [
    ...appointments.filter((item) => item.hospitalId === patientId && item.status === 'Scheduled' && item.date >= today).map((item): Visit => ({
      id: item.id, source: 'appointment', title: item.type, date: item.date, time: item.time,
      clinician: item.clinician, mode: item.mode, instructions: item.preparationInstructions?.text ?? '', instructionAuthor: item.preparationInstructions?.givenBy ?? '',
    })),
    ...events.filter((item) => item.patientId === patientId && item.kind !== 'Medicine' && (item.date >= today || Boolean(item.repeatUntil && item.repeatUntil >= today))).map((item): Visit => ({
      id: item.id, source: 'calendar', title: item.title, date: item.date < today ? today : item.date,
      time: item.time, clinician: '', mode: '', instructions: item.instructions, instructionAuthor: item.addedBy,
    })),
  ];
  candidates.sort((a, b) => `${a.date} ${a.time || '99:99'}`.localeCompare(`${b.date} ${b.time || '99:99'}`));
  const next = candidates[0];
  const patientTasks = tasks.filter((task) => task.patientId === patientId);
  const questions = patientTasks.filter((task) => task.kind === 'Question' && !task.completedBy);
  const help = patientTasks.filter((task) => task.kind !== 'Question' && (next
    ? task.appointmentId === (next.source === 'appointment' ? next.id : '__none__') || task.due === next.date || (!task.due && !task.completedBy)
    : !task.completedBy));
  const files = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'long' });

  function addQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!question.trim()) return;
    onAddTask({ id: crypto.randomUUID(), patientId, title: question.trim(), kind: 'Question', owner: author, due: '', createdBy: author, completedBy: null });
    setQuestion(''); setMessage(t('Question added.', 'सवाल जोड़ दिया।'));
  }

  function addHelp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!taskTitle.trim()) return;
    const task: FamilyTask = { id: crypto.randomUUID(), patientId, title: taskTitle.trim(), kind: taskKind, owner: owner.trim() || author, due: next?.date ?? '', createdBy: author, completedBy: null,
      ...(next?.source === 'appointment' ? { appointmentId: next.id } : {}),
    };
    if (addFamilyTask(tasks, task) === tasks) { setMessage(t('A matching task is already on the list. You can change its owner in Family tasks.', 'ऐसा काम सूची में पहले से है। परिवार के कामों में नाम बदल सकते हैं।')); return; }
    onAddTask(task); setTaskTitle(''); setOwner(''); setMessage(t('Added to the help list.', 'मदद की सूची में जोड़ दिया।'));
  }

  function chooseHelp(title: string, kind: FamilyTaskKind) {
    setTaskTitle(title); setTaskKind(kind); helpInput.current?.focus();
  }

  async function savePdf() {
    if (savingPdf) return;
    setSavingPdf(true); setMessage('');
    try {
      await downloadCarePdf({
        fileName: `saanthvana-next-visit-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.pdf`,
        title: t('Next visit', 'अगली मुलाकात'), subtitle: patientName,
        sections: [
          { heading: t('Visit', 'मुलाकात'), lines: next ? [next.title, `${dateLabel(next.date)}${next.time ? ` · ${next.time}` : ''}`, ...[next.clinician, next.mode].filter(Boolean), next.source === 'appointment' ? t('From the appointment list', 'मुलाकात की सूची से') : t('From your calendar', 'आपके कैलेंडर से')] : [t('No upcoming visit added.', 'अगली मुलाकात अभी नहीं जोड़ी है।')] },
          { heading: t('Preparation notes', 'तैयारी के निर्देश'), lines: next?.instructions ? [next.instructions, ...(next.instructionAuthor ? [`${t(next.source === 'appointment' ? 'From' : 'Added by', next.source === 'appointment' ? 'दिए गए' : 'जोड़ने वाले')}: ${next.instructionAuthor}`] : [])] : [t('No preparation note added.', 'तैयारी का निर्देश नहीं जोड़ा है।')] },
          { heading: t('Questions to ask', 'पूछने के सवाल'), lines: questions.length ? questions.map((task) => `${task.title} — ${task.owner}`) : [t('No open questions.', 'अभी कोई सवाल बाकी नहीं है।')] },
          { heading: t('Help for the visit', 'मुलाकात के लिए मदद'), lines: help.length ? help.map((task) => `${task.completedBy ? t('Done', 'हो गया') : t('To do', 'करना है')}: ${task.title}\n${task.owner}${task.due ? ` · ${task.due}` : ''}`) : [t('No help tasks added.', 'मदद का काम अभी नहीं जोड़ा है।')] },
          { heading: t('Reports', 'रिपोर्ट'), lines: files.length ? files.map((report) => `${report.date} — ${report.file.name}`) : [t('No reports added.', 'अभी रिपोर्ट नहीं जोड़ी है।')] },
        ],
      });
      setMessage(t('Visit brief downloaded.', 'मुलाकात का सारांश डाउनलोड हो गया।'));
    } catch { setMessage(t('Could not save the PDF. Please try again.', 'PDF सेव नहीं हुई। फिर कोशिश करें।')); }
    finally { setSavingPdf(false); }
  }

  return <section className="care-next-visit-page" aria-labelledby={`${id}-heading`}>
    <header className="next-visit-heading"><div><span>{patientName}</span><h1 id={`${id}-heading`}>{t('Next visit', 'अगली मुलाकात')}</h1></div><button type="button" className="next-visit-download" disabled={savingPdf} onClick={savePdf}><VisitIcon kind="download" />{savingPdf ? t('Saving…', 'सेव हो रहा है…') : t('Save PDF', 'PDF सेव करें')}</button></header>
    {message && <p className="next-visit-message" role="status">{message}</p>}
    <div className="next-visit-top">
      <section className="next-visit-appointment"><div className="next-visit-date-icon"><VisitIcon />{next && <strong>{Number(next.date.slice(-2))}</strong>}</div><div>{next ? <><span className="next-visit-source">{next.source === 'appointment' ? t('Appointment', 'मुलाकात') : t('Calendar entry', 'कैलेंडर एंट्री')}</span><h2>{next.title}</h2><time dateTime={`${next.date}${next.time ? `T${next.time}` : ''}`}>{dateLabel(next.date)}{next.time && ` · ${next.time}`}</time>{next.clinician && <p>{next.clinician}{next.mode && ` · ${next.mode}`}</p>}</> : <h2>{t('Add your next visit', 'अगली मुलाकात जोड़ें')}</h2>}<button type="button" onClick={() => onOpen('calendar')}>{next ? t('Open calendar', 'कैलेंडर खोलें') : t('Add to calendar', 'कैलेंडर में जोड़ें')} <span aria-hidden="true">↗</span></button></div></section>
      <section className="next-visit-prep"><h2>{t('Before you go', 'जाने से पहले')}</h2>{next?.instructions ? <><p>{next.instructions}</p>{next.instructionAuthor && <span>{t(next.source === 'appointment' ? 'From' : 'Added by', next.source === 'appointment' ? 'दिए गए' : 'जोड़ने वाले')}: {next.instructionAuthor}</span>}</> : <p className="next-visit-empty-line">{t('No preparation note added.', 'तैयारी का निर्देश अभी नहीं जोड़ा है।')}</p>}</section>
    </div>
    <div className="next-visit-grid">
      <section className="next-visit-panel next-visit-questions"><div className="next-visit-section-heading"><h2><VisitIcon kind="question" />{t('Questions to ask', 'पूछने के सवाल')}</h2><button type="button" onClick={() => onOpen('visit-questions')}>{t('All questions', 'सभी सवाल')} ↗</button></div><form className="next-visit-quick-add" onSubmit={addQuestion}><input aria-label={t('Question for the visit', 'मुलाकात का सवाल')} value={question} placeholder={t('What would you like to ask?', 'क्या पूछना चाहते हैं?')} required maxLength={240} onChange={(event) => setQuestion(event.target.value)} /><button type="submit" disabled={!question.trim()}>{t('Add', 'जोड़ें')}</button></form><ul className="next-visit-checklist">{questions.slice(0, 6).map((task) => <li key={task.id}><label><input type="checkbox" checked={false} onChange={() => onCompleteTask(task.id, true)} /><span>{task.title}<small>{task.owner}</small></span></label></li>)}</ul>{questions.length === 0 && <p className="next-visit-empty-line">{t('Keep your questions here before the visit.', 'मुलाकात से पहले अपने सवाल यहाँ रखें।')}</p>}</section>
      <section className="next-visit-panel next-visit-help"><div className="next-visit-section-heading"><h2><VisitIcon kind="help" />{t('Who’s helping?', 'कौन मदद करेगा?')}</h2><button type="button" onClick={() => onOpen('family-tasks')}>{t('All tasks', 'सभी काम')} ↗</button></div><div className="next-visit-help-options"><button type="button" onClick={() => chooseHelp(t('Arrange a ride', 'आने-जाने का इंतज़ाम'), 'Transport')}>{t('Arrange a ride', 'आने-जाने का इंतज़ाम')}</button><button type="button" onClick={() => chooseHelp(t('Bring reports', 'रिपोर्ट साथ लाना'), 'Paperwork')}>{t('Bring reports', 'रिपोर्ट साथ लाना')}</button></div><form className="next-visit-help-form" onSubmit={addHelp}><input ref={helpInput} aria-label={t('Help needed', 'किस मदद की ज़रूरत है')} value={taskTitle} placeholder={t('What needs doing?', 'क्या करना है?')} required maxLength={240} onChange={(event) => setTaskTitle(event.target.value)} /><div className="next-visit-quick-add"><input aria-label={t('Who is helping? Optional', 'कौन मदद करेगा? वैकल्पिक')} value={owner} placeholder={author} maxLength={80} onChange={(event) => setOwner(event.target.value)} /><button type="submit" disabled={!taskTitle.trim()}>{t('Add', 'जोड़ें')}</button></div></form><ul className="next-visit-checklist">{help.slice(0, 6).map((task) => <li key={task.id} className={task.completedBy ? 'is-done' : ''}><label><input type="checkbox" checked={Boolean(task.completedBy)} onChange={(event) => onCompleteTask(task.id, event.target.checked)} /><span>{task.title}<small>{task.owner}{task.completedBy ? ` · ${t('Done by', 'पूरा किया')} ${task.completedBy}` : ''}</small></span></label></li>)}</ul></section>
      <section className="next-visit-panel next-visit-reports"><div className="next-visit-section-heading"><h2><VisitIcon kind="file" />{t('Reports to hand', 'रिपोर्ट तैयार रखें')}</h2><button type="button" onClick={() => onOpen('reports')}>{t('All reports', 'सभी रिपोर्ट')} ↗</button></div>{files.length ? <ul className="next-visit-report-list">{files.slice(0, 6).map((report) => <li key={report.id}><VisitReportLink report={report} hindi={hindi} /></li>)}</ul> : <button type="button" className="next-visit-add-report" onClick={() => onOpen('reports')}>{t('Add a report', 'रिपोर्ट जोड़ें')}</button>}</section>
    </div>
  </section>;
}
