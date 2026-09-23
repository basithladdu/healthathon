'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { Appointment } from './appointments-page';
import type { CareEvent, CareReport } from './care-calendar-state';
import { addFamilyTask, type CareTaskAssignee, type FamilyTask, type FamilyTaskKind } from './family-care-state';
import { downloadCarePdf } from './care-pdf';
import './care-next-visit.css';

export type CareNextVisitProps = {
  patientId: string; patientName: string; author: string; today: string; hindi: boolean;
  viewer?: CareTaskAssignee; assignees?: CareTaskAssignee[];
  appointments: Appointment[]; events: CareEvent[]; tasks: FamilyTask[]; reports: CareReport[];
  onAddTask: (task: FamilyTask) => void;
  onEditTask?: (task: FamilyTask) => void;
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

function NextVisitForPatient({ patientId, patientName, author, today, hindi, viewer, assignees = [], appointments, events, tasks, reports, onAddTask, onEditTask, onCompleteTask, onOpen }: CareNextVisitProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [actionText, setActionText] = useState('');
  const [actionKind, setActionKind] = useState<'Question' | Exclude<FamilyTaskKind, 'Question'>>('Question');
  const [assigneeId, setAssigneeId] = useState(viewer?.id ?? '');
  const [message, setMessage] = useState('');
  const [savingPdf, setSavingPdf] = useState(false);
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
  const appointmentCandidates = candidates.filter((item) => item.source === 'appointment').sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  const calendarCandidates = candidates.filter((item) => item.source === 'calendar').sort((a, b) => `${a.date} ${a.time || '99:99'}`.localeCompare(`${b.date} ${b.time || '99:99'}`));
  const next = appointmentCandidates[0] ?? calendarCandidates[0];
  const patientTasks = tasks.filter((task) => task.patientId === patientId);
  const openTasks = patientTasks.filter((task) => !task.completedBy);
  const questions = openTasks.filter((task) => task.kind === 'Question');
  const help = openTasks.filter((task) => task.kind !== 'Question');
  const files = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const recipients = [...(viewer ? [viewer] : []), ...assignees].filter((person, index, people) => people.findIndex((candidate) => candidate.id === person.id) === index);
  const selectedRecipient = recipients.find((person) => person.id === assigneeId) ?? viewer;
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'long' });

  function addAction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!actionText.trim()) return;
    const isQuestion = actionKind === 'Question';
    const recipient = isQuestion ? undefined : selectedRecipient;
    if (!isQuestion && recipients.length > 0 && !recipient) return;
    const task: FamilyTask = { id: crypto.randomUUID(), patientId, title: actionText.trim(), kind: actionKind, owner: recipient?.name ?? author, due: isQuestion ? '' : next?.date ?? '', createdBy: author, completedBy: null,
      ...(!isQuestion && recipient ? { assignedToId: recipient.id, ownerRole: recipient.role } : {}), ...(viewer ? { createdById: viewer.id } : {}),
      ...(next?.source === 'appointment' && !isQuestion ? { appointmentId: next.id } : {}),
    };
    if (addFamilyTask(tasks, task) === tasks) { setMessage(t('A matching task is already on the list. Change its recipient in Family tasks.', 'ऐसा काम सूची में पहले से है। परिवार के कामों में पाने वाले का नाम बदलें।')); return; }
    onAddTask(task); setActionText(''); setMessage(t(isQuestion ? 'Question added.' : `Assigned to ${recipient?.name ?? author}.`, isQuestion ? 'सवाल जोड़ दिया।' : `${recipient?.name ?? author} को सौंपा।`));
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

  function taskAction(task: FamilyTask) {
    const isRecipient = viewer ? task.assignedToId ? task.assignedToId === viewer.id : task.owner.trim().toLocaleLowerCase() === viewer.name.trim().toLocaleLowerCase() : !task.assignedToId;
    if (!isRecipient || task.completedBy) return null;
    const needsAcceptance = Boolean(task.assignedToId && task.createdById !== task.assignedToId && !task.acceptedBy);
    if (needsAcceptance) return onEditTask ? <button type="button" className="next-visit-task-accept" onClick={() => onEditTask({ ...task, acceptedBy: author, acceptedAt: new Date().toISOString() })}>{t('Accept', 'स्वीकार करें')}</button> : null;
    return <button type="button" className="next-visit-task-done" onClick={() => onCompleteTask(task.id, true)}>{t('Done', 'हो गया')}</button>;
  }

  return <section className="care-next-visit-page" aria-labelledby={`${id}-heading`}>
    <header className="next-visit-heading"><div><span>{patientName}</span><h1 id={`${id}-heading`}>{t('Next visit', 'अगली मुलाकात')}</h1></div><button type="button" className="next-visit-download" disabled={savingPdf} onClick={savePdf}><VisitIcon kind="download" />{savingPdf ? t('Saving…', 'सेव हो रहा है…') : t('Save PDF', 'PDF सेव करें')}</button></header>
    {message && <p className="next-visit-message" role="status">{message}</p>}
    <div className="next-visit-top">
      <section className="next-visit-appointment"><div className="next-visit-date-icon"><VisitIcon />{next && <strong>{Number(next.date.slice(-2))}</strong>}</div><div>{next ? <><span className="next-visit-source">{next.source === 'appointment' ? t('Appointment', 'मुलाकात') : t('Calendar entry', 'कैलेंडर एंट्री')}</span><h2>{next.title}</h2><time dateTime={`${next.date}${next.time ? `T${next.time}` : ''}`}>{dateLabel(next.date)}{next.time && ` · ${next.time}`}</time>{next.clinician && <p>{next.clinician}{next.mode && ` · ${next.mode}`}</p>}</> : <h2>{t('Add your next visit', 'अगली मुलाकात जोड़ें')}</h2>}<button type="button" onClick={() => onOpen('calendar')}>{next ? t('Open calendar', 'कैलेंडर खोलें') : t('Add to calendar', 'कैलेंडर में जोड़ें')} <span aria-hidden="true">↗</span></button></div></section>
      <section className="next-visit-prep"><h2>{t('Before you go', 'जाने से पहले')}</h2>{next?.instructions ? <><p>{next.instructions}</p>{next.instructionAuthor && <span>{t(next.source === 'appointment' ? 'From' : 'Added by', next.source === 'appointment' ? 'दिए गए' : 'जोड़ने वाले')}: {next.instructionAuthor}</span>}</> : <p className="next-visit-empty-line">{t('No preparation note added.', 'तैयारी का निर्देश अभी नहीं जोड़ा है।')}</p>}</section>
    </div>
    <div className="next-visit-content">
      <details className="next-visit-action"><summary>{t('Add a question or ask someone for help', 'सवाल पूछें या किसी से मदद माँगें')}</summary><form onSubmit={addAction}>
        <label>{t('What would you like to add?', 'क्या जोड़ना चाहेंगे?')}<select value={actionKind} onChange={(event) => setActionKind(event.target.value as typeof actionKind)}><option value="Question">{t('Question for the care team', 'देखभाल टीम से सवाल')}</option><option value="Transport">{t('Task for someone', 'किसी के लिए काम')}</option><option value="Paperwork">{t('Bring a report or document', 'रिपोर्ट या दस्तावेज़ लाएँ')}</option><option value="Home care">{t('Help at home', 'घर पर मदद')}</option><option value="Medicines">{t('Medicine pickup', 'दवा लाना')}</option></select></label>
        <label>{t(actionKind === 'Question' ? 'Your question' : 'What needs doing?', actionKind === 'Question' ? 'आपका सवाल' : 'क्या करना है?')}<input value={actionText} onChange={(event) => setActionText(event.target.value)} maxLength={240} required /></label>
        {actionKind !== 'Question' && recipients.length > 0 && <label>{t('Assign to', 'काम सौंपें')}<select value={selectedRecipient?.id ?? ''} onChange={(event) => setAssigneeId(event.target.value)}>{recipients.map((person) => <option key={person.id} value={person.id}>{person.id === viewer?.id ? t('Me', 'मैं') : person.name}</option>)}</select></label>}
        <button type="submit" disabled={!actionText.trim() || (actionKind !== 'Question' && recipients.length > 0 && !selectedRecipient)}>{t('Add', 'जोड़ें')}</button>
      </form></details>
      {message && <p className="next-visit-message" role="status">{message}</p>}
      {openTasks.length > 0 && <details className="next-visit-task-list"><summary>{t('Questions and tasks', 'सवाल और काम')} · {openTasks.length}</summary><button type="button" className="next-visit-all-tasks" onClick={() => onOpen('family-tasks')}>{t('All tasks', 'सभी काम')} ↗</button><ul>{openTasks.slice(0, 4).map((task) => <li key={task.id}><span><strong>{task.title}</strong><small>{task.owner}{task.due ? ` · ${dateLabel(task.due)}` : ''}{task.acceptedBy ? ` · ${t('Accepted by', 'स्वीकार किया')} ${task.acceptedBy}` : ''}</small></span>{taskAction(task)}</li>)}</ul></details>}
      <section className="next-visit-panel next-visit-reports"><div className="next-visit-section-heading"><h2><VisitIcon kind="file" />{t('Reports to bring', 'साथ लाने की रिपोर्ट')}</h2><button type="button" onClick={() => onOpen('reports')}>{t('All reports', 'सभी रिपोर्ट')} ↗</button></div>{files.length ? <><ul className="next-visit-report-list">{files.slice(0, 3).map((report) => <li key={report.id}><VisitReportLink report={report} hindi={hindi} /></li>)}</ul>{files.length > 3 && <details className="next-visit-more-reports"><summary>{t('More reports', 'और रिपोर्ट')} · {files.length - 3}</summary><ul className="next-visit-report-list">{files.slice(3).map((report) => <li key={report.id}><VisitReportLink report={report} hindi={hindi} /></li>)}</ul></details>}</> : <button type="button" className="next-visit-add-report" onClick={() => onOpen('reports')}>{t('Add a report', 'रिपोर्ट जोड़ें')}</button>}</section>
    </div>
  </section>;
}
