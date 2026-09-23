'use client';

import { useId, useState, type FormEvent } from 'react';
import type { Appointment } from './appointments-page';
import { FAMILY_TASK_KINDS, type CareTaskAssignee, type FamilyTask, type FamilyTaskKind } from './family-care-state';
import './care-task-inbox.css';

export type CareTaskInboxProps = {
  patientId: string;
  patientName: string;
  viewer: CareTaskAssignee;
  assignees: CareTaskAssignee[];
  tasks: FamilyTask[];
  appointments?: Appointment[];
  today?: string;
  hindi?: boolean;
  onAdd: (task: FamilyTask) => void;
  onEdit: (task: FamilyTask) => void;
  onComplete: (taskId: string, complete: boolean) => void;
};

const KIND_HI: Record<FamilyTaskKind, string> = { Appointment: 'मुलाकात', Transport: 'आना-जाना', Medicines: 'दवाएँ', 'Home care': 'घर पर देखभाल', Paperwork: 'कागज़ी काम', 'Family support': 'परिवार की मदद', Question: 'सवाल' };

export function CareTaskInbox({ patientId, patientName, viewer, assignees, tasks, appointments = [], today = '', hindi = false, onAdd, onEdit, onComplete }: CareTaskInboxProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const recipients = [...assignees, viewer].filter((person, index, people) => people.findIndex((candidate) => candidate.id === person.id) === index);
  const selectableRecipients = recipients.filter((person) => person.id !== viewer.id);
  const defaultRecipient = selectableRecipients.find((person) => person.role === 'patient') ?? selectableRecipients[0] ?? viewer;
  const [title, setTitle] = useState('');
  const [kind, setKind] = useState<FamilyTaskKind>('Family support');
  const [assignedToId, setAssignedToId] = useState(defaultRecipient.id);
  const [due, setDue] = useState('');
  const [message, setMessage] = useState('');
  const patientTasks = tasks.filter((task) => task.patientId === patientId);
  const assignedToMe = patientTasks.filter((task) => !task.completedBy && (task.assignedToId === viewer.id || (!task.assignedToId && task.owner.trim().toLocaleLowerCase() === viewer.name.trim().toLocaleLowerCase())));
  const assignedByMe = patientTasks.filter((task) => task.createdById === viewer.id && task.assignedToId !== viewer.id && !task.completedBy);
  const completed = patientTasks.filter((task) => task.completedBy);
  const legacy = patientTasks.filter((task) => !task.assignedToId && !task.completedBy && task.owner.trim().toLocaleLowerCase() !== viewer.name.trim().toLocaleLowerCase());
  const scheduled = appointments.filter((item) => item.hospitalId === patientId && item.status === 'Scheduled' && (!today || item.date >= today)).sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const recipient = recipients.find((person) => person.id === assignedToId);
    if (!title.trim() || !recipient) return;
    onAdd({ id: crypto.randomUUID(), patientId, title: title.trim(), kind, owner: recipient.name, ownerRole: recipient.role, assignedToId: recipient.id, createdById: viewer.id, createdBy: viewer.name, due, completedBy: null });
    setTitle(''); setDue('');
    setMessage(t(`Assigned to ${recipient.name}.`, `काम ${recipient.name} को सौंपा।`));
  }

  function accept(task: FamilyTask) {
    onEdit({ ...task, acceptedBy: viewer.name, acceptedAt: new Date().toISOString() });
  }

  function taskRow(task: FamilyTask) {
    const incoming = task.assignedToId === viewer.id && task.createdById !== viewer.id;
    const outgoing = task.createdById === viewer.id && task.assignedToId !== viewer.id;
    const waiting = incoming && !task.acceptedBy;
    const canComplete = (task.assignedToId === viewer.id || (!task.assignedToId && task.owner.trim().toLocaleLowerCase() === viewer.name.trim().toLocaleLowerCase())) && !task.completedBy && (!incoming || Boolean(task.acceptedBy));
    const status = task.completedBy
      ? `${t('Done by', 'पूरा किया')} ${task.completedBy}`
      : waiting ? t('Waiting for your acceptance', 'आपकी स्वीकृति बाकी है')
        : task.acceptedBy ? `${t('Accepted by', 'स्वीकार किया')} ${task.acceptedBy}`
          : outgoing ? `${t('Assigned to', 'काम सौंपा')} ${task.owner}`
            : task.owner;
    return <li key={task.id} className={task.completedBy ? 'is-complete' : ''}>
      <div className="task-inbox-copy"><strong>{task.title}</strong>{incoming && <span>{t('From', 'की ओर से')}: {task.createdBy}</span>}<span>{status}{task.due ? ` · ${new Date(`${task.due}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' })}` : ''} · {hindi ? KIND_HI[task.kind] : task.kind}</span></div>
      {waiting && <button type="button" className="task-inbox-accept" onClick={() => accept(task)}>{t('Accept', 'स्वीकार करें')}</button>}
      {canComplete && !waiting && <button type="button" className="task-inbox-done" onClick={() => onComplete(task.id, true)}>{t('Done', 'हो गया')}</button>}
    </li>;
  }

  return <section className="care-task-inbox" aria-labelledby={`${id}-title`}>
    <header><div><span>{patientName}</span><h1 id={`${id}-title`}>{viewer.role === 'doctor' ? t('Care tasks', 'देखभाल के काम') : t('Tasks for me', 'मेरे लिए काम')}</h1></div></header>
    <details className="task-inbox-new" open={viewer.role === 'doctor'}><summary>{viewer.role === 'doctor' ? t('Assign follow-up', 'आगे का काम सौंपें') : t('Ask someone for help', 'किसी से मदद माँगें')}</summary><form className="task-inbox-compose" onSubmit={add}>
      <label htmlFor={`${id}-task`}>{t('What needs doing?', 'क्या करना है?')}<input id={`${id}-task`} value={title} required maxLength={240} onChange={(event) => setTitle(event.target.value)} /></label>
      <div className="task-inbox-fields">
        <label htmlFor={`${id}-recipient`}>{t('Assign to', 'काम सौंपें')}<select id={`${id}-recipient`} value={assignedToId} onChange={(event) => setAssignedToId(event.target.value)}>{recipients.map((person) => <option key={person.id} value={person.id}>{person.id === viewer.id ? t('Me', 'मैं') : person.name}</option>)}</select></label>
        <label htmlFor={`${id}-kind`}>{t('Type', 'किस तरह का काम')}<select id={`${id}-kind`} value={kind} onChange={(event) => setKind(event.target.value as FamilyTaskKind)}>{FAMILY_TASK_KINDS.filter((value) => value !== 'Question').map((value) => <option key={value} value={value}>{hindi ? KIND_HI[value] : value}</option>)}</select></label>
        <label htmlFor={`${id}-due`}>{t('When? Optional', 'कब? वैकल्पिक')}<input id={`${id}-due`} type="date" value={due} onChange={(event) => setDue(event.target.value)} /></label>
      </div>
      {viewer.role === 'doctor' && scheduled.length > 0 && <p>{t('For the selected patient’s next visit:', 'चुने हुए मरीज़ की अगली मुलाकात:')} {scheduled[0].date} · {scheduled[0].type}</p>}
      <button type="submit" className="task-inbox-create" disabled={!title.trim() || recipients.length === 0}>{t('Assign task', 'काम सौंपें')}</button>
      {message && <span className="task-inbox-message" role="status">{message}</span>}
    </form></details>
    <section className="task-inbox-section"><h2>{t('For me', 'मेरे लिए')} <span>{assignedToMe.length}</span></h2><ul>{assignedToMe.map(taskRow)}</ul>{assignedToMe.length === 0 && <p>{t('No tasks assigned to you.', 'आपको कोई काम नहीं सौंपा गया।')}</p>}</section>
    {assignedByMe.length > 0 && <section className="task-inbox-section"><h2>{t('Assigned by me', 'मेरे सौंपे काम')} <span>{assignedByMe.length}</span></h2><ul>{assignedByMe.map(taskRow)}</ul></section>}
    {legacy.length > 0 && <details className="task-inbox-section task-inbox-other"><summary>{t('Other open tasks', 'अन्य बाकी काम')} · {legacy.length}</summary><ul>{legacy.map(taskRow)}</ul></details>}
    {completed.length > 0 && <details className="task-inbox-section task-inbox-other"><summary>{t('Completed', 'पूरे हुए')} · {completed.length}</summary><ul>{completed.map(taskRow)}</ul></details>}
  </section>;
}
