'use client';

import { useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import type { Appointment } from './appointments-page';
import { FAMILY_TASK_KINDS, editFamilyTask, restoreFamilyTask, type FamilyTask, type FamilyTaskKind } from './family-care-state';
import { FamilyToolButtons, type FamilyTool } from './family-care-tools';
import { CareRouteLink } from './care-route-link';
import './family-help-checklist.css';

const HELP_SUGGESTIONS: Array<{ title: string; hindi: string; kind: FamilyTaskKind }> = [
  { title: 'Arrange a ride', hindi: 'आने-जाने का इंतज़ाम', kind: 'Transport' },
  { title: 'Collect medicines', hindi: 'दवाएँ लाना', kind: 'Medicines' },
  { title: 'Bring reports', hindi: 'रिपोर्ट साथ लाना', kind: 'Paperwork' },
  { title: 'Spend some time together', hindi: 'कुछ समय साथ बिताना', kind: 'Family support' },
  { title: 'Cover an hour at home', hindi: 'घर पर एक घंटा मदद करना', kind: 'Home care' },
];
const HINDI_TASK_KINDS: Record<FamilyTaskKind, string> = { Appointment: 'मुलाकात', Transport: 'आना-जाना', Medicines: 'दवाएँ', 'Home care': 'घर पर देखभाल', Paperwork: 'कागज़ी काम', 'Family support': 'परिवार की मदद', Question: 'सवाल' };

export function FamilyCareWorkspace({ patientId, patientName, author, tasks, appointments, demoToday, calendar, comfort, calendarText = '', tasksOnly = false, section = 'all', onOpenTool, onTrackQuestion, onAdd, onEdit, onRemove, onRestore, onComplete, onPrepare, onCareTeam, onPlan, hindi = false }: {
  patientId: string; patientName: string; author: string; tasks: FamilyTask[]; appointments: Appointment[]; demoToday: string; hindi?: boolean;
  calendar?: ReactNode; comfort?: ReactNode; calendarText?: string;
  tasksOnly?: boolean;
  section?: 'all' | 'tasks' | 'questions';
  onOpenTool?: (tool: FamilyTool) => void;
  onTrackQuestion?: (task: FamilyTask) => void;
  onAdd: (task: FamilyTask) => void; onComplete: (taskId: string, complete: boolean) => void;
  onEdit?: (task: FamilyTask) => void; onRemove?: (taskId: string) => void; onRestore?: (task: FamilyTask) => void;
  onPrepare: () => void; onCareTeam: () => void; onPlan: () => void;
}) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const dateLabel = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });
  const [title, setTitle] = useState('');
  const [question, setQuestion] = useState('');
  const [kind, setKind] = useState<FamilyTaskKind>('Home care');
  const [owner, setOwner] = useState(author);
  const [due, setDue] = useState('');
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState<FamilyTask | null>(null);
  const [removed, setRemoved] = useState<FamilyTask[]>([]);
  const taskInput = useRef<HTMLInputElement>(null);
  const patientTasks = tasks.filter((task) => task.patientId === patientId);
  const scheduled = appointments.filter((item) => item.hospitalId === patientId && item.status === 'Scheduled' && item.date >= demoToday)
    .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  const visit = scheduled[0];
  const openTasks = patientTasks.filter((task) => !task.completedBy && task.kind !== 'Question' && (!calendar || !task.due));
  const questions = patientTasks.filter((task) => !task.completedBy && task.kind === 'Question');
  const doneTasks = patientTasks.filter((task) => task.completedBy && task.kind !== 'Question');
  const askedQuestions = patientTasks.filter((task) => task.completedBy && task.kind === 'Question');
  const lastRemoved = removed.at(-1);

  function add(event: FormEvent<HTMLFormElement>, isQuestion = false) {
    event.preventDefault();
    const value = isQuestion ? question : title;
    if (!value.trim() || (!isQuestion && !owner.trim())) return;
    onAdd({ id: crypto.randomUUID(), patientId, title: value, kind: isQuestion ? 'Question' : kind, owner: isQuestion ? author : owner, due: isQuestion ? '' : due, createdBy: author, completedBy: null });
    if (isQuestion) setQuestion(''); else { setTitle(''); setDue(''); }
    setMessage(t(isQuestion ? 'Added to your questions.' : 'Added to your list.', isQuestion ? 'सवाल जोड़ दिया।' : 'काम जोड़ दिया।'));
  }

  function saveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !onEdit) return;
    const previous = patientTasks.find((task) => task.id === editing.id);
    if (!previous) return;
    if (previous.title === editing.title.trim() && previous.owner === editing.owner.trim() && previous.due === editing.due && previous.kind === editing.kind) { setEditing(null); return; }
    const next = editFamilyTask(tasks, patientId, editing);
    if (next === tasks) { setMessage(t('Check the task, owner and date.', 'काम, नाम और तारीख जाँचें।')); return; }
    const updated = next.find((task) => task.patientId === patientId && task.id === editing.id)!;
    onEdit(updated); setEditing(null);
    setMessage(previous.completedBy ? t('Updated and moved back to the open list.', 'बदलकर फिर से बाकी कामों में जोड़ दिया।') : t('Task updated.', 'काम बदल दिया।'));
  }

  function removeTask(task: FamilyTask) {
    if (!onRemove) return;
    onRemove(task.id); setRemoved((items) => [...items, task]); setMessage('');
    if (editing?.id === task.id) setEditing(null);
  }

  function undoRemove() {
    if (!lastRemoved || !onRestore) return;
    if (restoreFamilyTask(tasks, patientId, lastRemoved) === tasks) { setMessage(t('This task is already on the list or has a matching open task.', 'यह काम सूची में है या इससे मिलता काम अभी बाकी है।')); return; }
    onRestore(lastRemoved); setRemoved((items) => items.slice(0, -1)); setMessage(t('Restored.', 'वापस जोड़ दिया।'));
  }

  function download() {
    const text = [
      `Saanthvana — ${patientName}'s checklist`,
      `Written by ${author}. Personal notes; not a prescription or a doctor-approved care note.`,
      'VISITS', ...scheduled.map((item) => `${item.date} ${item.time} — ${item.type}, ${item.clinician}, ${item.mode}${item.preparationInstructions ? `\nInstructions from ${item.preparationInstructions.givenBy}: ${item.preparationInstructions.text}\nWritten here by ${item.preparationInstructions.recordedBy}` : ''}`),
      'TO DO AND ASK', ...patientTasks.map((task) => `${task.completedBy ? 'Done / asked' : 'Open'}: ${task.title}\n${task.owner}${task.due ? `; ${task.due}` : ''}${task.completedBy ? `; completed by ${task.completedBy}` : ''}`),
      calendarText,
    ].join('\n\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'saanthvana-family-checklist.txt'; link.click(); URL.revokeObjectURL(url);
  }

  function taskRow(task: FamilyTask) {
    if (editing?.id === task.id) return <li key={task.id} className="family-task-row family-task-edit-row"><form className="family-task-edit" onSubmit={saveEdit}>
      <label htmlFor={`${id}-edit-title`}>{task.kind === 'Question' ? t('Question', 'सवाल') : t('Task', 'काम')}<input id={`${id}-edit-title`} value={editing.title} required maxLength={240} onChange={(event) => setEditing({ ...editing, title: event.target.value })} /></label>
      <div className="family-task-edit-fields"><label htmlFor={`${id}-edit-owner`}>{t('Who?', 'कौन?')}<input id={`${id}-edit-owner`} value={editing.owner} required maxLength={80} onChange={(event) => setEditing({ ...editing, owner: event.target.value })} /></label><label htmlFor={`${id}-edit-due`}>{t('When?', 'कब?')}<input id={`${id}-edit-due`} type="date" value={editing.due} onChange={(event) => setEditing({ ...editing, due: event.target.value })} /></label><label htmlFor={`${id}-edit-kind`}>{t('Type', 'किस तरह का काम')}<select id={`${id}-edit-kind`} value={editing.kind} onChange={(event) => setEditing({ ...editing, kind: event.target.value as FamilyTaskKind })}>{FAMILY_TASK_KINDS.filter((value) => task.kind === 'Question' ? value === 'Question' : value !== 'Question').map((value) => <option key={value} value={value}>{hindi ? HINDI_TASK_KINDS[value] : value}</option>)}</select></label></div>
      <div className="family-task-row-actions"><button type="submit" className="primary-button" disabled={!editing.title.trim() || !editing.owner.trim()}>{t('Save changes', 'बदलाव सेव करें')}</button><button type="button" className="care-text-button" onClick={() => setEditing(null)}>{t('Cancel', 'रद्द करें')}</button></div>
    </form></li>;
    return <li key={task.id} className={`family-task-row${task.completedBy ? ' is-complete' : ''}`} data-kind={task.kind}>
      <label><input type="checkbox" checked={Boolean(task.completedBy)} aria-label={`${task.kind === 'Question' ? t('Asked', 'पूछ लिया') : t('Done', 'हो गया')}: ${task.title}`} onChange={(event) => {
        onComplete(task.id, event.target.checked);
        setMessage(event.target.checked ? t(task.kind === 'Question' ? 'Marked as asked.' : 'Done.', 'हो गया।') : t('Added back to your list.', 'फिर से सूची में जोड़ दिया।'));
      }} /><span><strong>{task.title}</strong><span className="family-task-meta">{task.owner}{task.due ? ` · ${dateLabel(task.due)}` : ''} · {hindi ? HINDI_TASK_KINDS[task.kind] : task.kind}</span>{task.completedBy && <span className="family-task-meta">{t(task.kind === 'Question' ? 'Asked by' : 'Done by', task.kind === 'Question' ? 'पूछा' : 'पूरा किया')} {task.completedBy}</span>}</span></label>
      <div className="family-task-row-actions">{task.kind === 'Question' && onTrackQuestion && <button type="button" className="care-text-button" onClick={() => onTrackQuestion(task)}>{t('Keep replies', 'जवाब रखें')} →</button>}{onEdit && <button type="button" className="care-text-button" onClick={() => { setEditing({ ...task }); setMessage(''); }} aria-label={`${t('Edit', 'बदलें')}: ${task.title}`}>{t('Edit', 'बदलें')}</button>}{onRemove && <button type="button" className="care-text-button" onClick={() => removeTask(task)} aria-label={`${t('Remove', 'हटाएँ')}: ${task.title}`}>{t('Remove', 'हटाएँ')}</button>}</div>
    </li>;
  }

  return <section className="family-workspace" aria-labelledby={`${id}-title`}>
    <div className="family-workspace-heading"><h1 id={`${id}-title`}>{section === 'questions' ? t('Ask at the next visit', 'अगली मुलाकात में पूछें') : tasksOnly ? t('Family tasks', 'परिवार के काम') : `${patientName.split(' ')[0]}${t('’s care', ' की देखभाल')}`}</h1><button type="button" className="secondary-button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div>
    {lastRemoved && <div className="family-task-undo" role="status"><span>{t('Removed', 'हटाया')}: {lastRemoved.title}</span>{onRestore && <button type="button" className="care-text-button" onClick={undoRemove}>{t('Undo', 'वापस लाएँ')}</button>}</div>}
    {!tasksOnly && <div className="family-next-visit-line">{visit && <span>{t('Next visit', 'अगली मुलाकात')}: {dateLabel(visit.date)} · {visit.time} · {visit.clinician}</span>}<button className="care-text-button" type="button" onClick={onPlan}>{t('Read the care note', 'डॉक्टर का नोट पढ़ें')} →</button></div>}
    {calendar}
    {comfort}
    {onOpenTool && <FamilyToolButtons hindi={hindi} onOpen={onOpenTool} />}
    <div className={`care-home-grid${section !== 'all' ? ' care-home-single' : ''}`}>
      {section !== 'questions' && <div>
        {!calendar && !tasksOnly && <section className="care-next-visit" aria-labelledby={`${id}-visit`}>
          <div className="family-section-heading"><h2 id={`${id}-visit`}>{t('Next visit', 'अगली मुलाकात')}</h2><button className="care-text-button" type="button" onClick={onCareTeam}>{t('Care team', 'देखभाल टीम')} →</button></div>
          {visit ? <><p className="care-visit-time"><time dateTime={`${visit.date}T${visit.time}`}>{dateLabel(visit.date)} · {visit.time}</time></p><p>{visit.clinician} · {visit.mode}</p><p className="care-visit-purpose">{visit.type}</p></> : <p>{t('No visit is booked here yet.', 'यहाँ अभी कोई मुलाकात तय नहीं है।')}</p>}
          <button className="care-text-button" type="button" onClick={onPlan}>{t('Read the care note', 'डॉक्टर का नोट पढ़ें')} →</button>
        </section>}
        <section className="care-home-section" aria-labelledby={`${id}-tasks`}>
          <div className="family-section-heading"><h2 id={`${id}-tasks`}>{calendar ? t('Other tasks', 'बाकी काम') : t('Things to do', 'करने के काम')}</h2><span>{openTasks.length} {t('left', 'बाकी')}</span></div>
          <div className="family-task-suggestions" aria-label={t('Task suggestions', 'काम के सुझाव')}>{HELP_SUGGESTIONS.map((suggestion) => <button type="button" key={suggestion.title} onClick={() => { setTitle(t(suggestion.title, suggestion.hindi)); setKind(suggestion.kind); setMessage(''); taskInput.current?.focus(); }}>{t(suggestion.title, suggestion.hindi)}</button>)}</div>
          <form className="care-quick-add" onSubmit={(event) => add(event)}>
            <div className="care-input-row"><input ref={taskInput} aria-label={t('Add a task', 'काम जोड़ें')} placeholder={t('Add something to do', 'कोई काम लिखें')} value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={240} /><button type="submit" className="primary-button" disabled={!title.trim() || !owner.trim()}>{t('Add', 'जोड़ें')}</button></div>
            <details className="care-task-options"><summary>{owner || t('Choose who', 'नाम चुनें')}{due ? ` · ${dateLabel(due)}` : ''}</summary><div className="family-task-form">
              <label htmlFor={`${id}-owner`}>{t('Who?', 'कौन?')}<input id={`${id}-owner`} value={owner} onChange={(event) => setOwner(event.target.value)} required maxLength={80} /></label>
              <label htmlFor={`${id}-due`}>{t('When?', 'कब?')}<input id={`${id}-due`} type="date" value={due} onChange={(event) => setDue(event.target.value)} /></label>
              <label htmlFor={`${id}-kind`}>{t('Type', 'किस तरह का काम')}<select id={`${id}-kind`} value={kind} onChange={(event) => setKind(event.target.value as FamilyTaskKind)}>{FAMILY_TASK_KINDS.filter((item) => item !== 'Question').map((item) => <option key={item} value={item}>{hindi ? HINDI_TASK_KINDS[item] : item}</option>)}</select></label>
              {kind === 'Medicines' && <p className="family-task-title">{t('Follow the existing prescription. This list does not change medicines or doses.', 'डॉक्टर के लिखे पर्चे का पालन करें। यह सूची दवा या खुराक नहीं बदलती।')}</p>}
            </div></details>
          </form>
          <ul className="family-task-list">{openTasks.map(taskRow)}</ul>
          {openTasks.length === 0 && !calendar && <p>{t('All done for now.', 'अभी के सभी काम हो गए।')}</p>}
          {doneTasks.length > 0 && <details className="care-completed"><summary>{t('Done', 'हो गए')} ({doneTasks.length})</summary><ul className="family-task-list">{doneTasks.map(taskRow)}</ul></details>}
        </section>
      </div>}
      {section !== 'tasks' && <section className="care-home-section care-question-panel" aria-labelledby={`${id}-questions`}>
        <h2 id={`${id}-questions`}>{section === 'questions' ? t('My questions', 'मेरे सवाल') : t('Ask at the next visit', 'अगली मुलाकात में पूछें')}</h2>
        <ul className="family-task-list">{questions.map(taskRow)}</ul>
        <form className="care-quick-add" onSubmit={(event) => add(event, true)}><div className="care-input-row"><input aria-label={t('Add a question', 'सवाल जोड़ें')} placeholder={t('What would you like to ask?', 'आप क्या पूछना चाहते हैं?')} value={question} onChange={(event) => setQuestion(event.target.value)} required maxLength={240} /><button type="submit" className="primary-button" disabled={!question.trim()}>{t('Add', 'जोड़ें')}</button></div></form>
        <button className="care-text-button care-prepare-link" type="button" onClick={onPrepare}>{t('Think through what matters to you', 'आपके लिए क्या ज़रूरी है?')} →</button>
        {askedQuestions.length > 0 && <details className="care-completed"><summary>{t('Asked', 'पूछ लिए')} ({askedQuestions.length})</summary><ul className="family-task-list">{askedQuestions.map(taskRow)}</ul></details>}
      </section>}
    </div>
    {section === 'tasks' && <CareRouteLink className="care-text-button" view="visit-questions">{t('Questions for the next visit', 'अगली मुलाकात के सवाल')} →</CareRouteLink>}
    <p className="family-workspace-message" role="status">{message}</p>
  </section>;
}
