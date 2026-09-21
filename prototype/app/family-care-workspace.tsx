'use client';

import { useId, useState, type FormEvent, type ReactNode } from 'react';
import type { Appointment } from './appointments-page';
import { FAMILY_TASK_KINDS, type FamilyTask, type FamilyTaskKind } from './family-care-state';
import { FamilyToolButtons, type FamilyTool } from './family-care-tools';
import { CareRouteLink } from './care-route-link';

export function FamilyCareWorkspace({ patientId, patientName, author, tasks, appointments, demoToday, calendar, comfort, calendarText = '', tasksOnly = false, section = 'all', onOpenTool, onTrackQuestion, onAdd, onComplete, onPrepare, onCareTeam, onPlan, hindi = false }: {
  patientId: string; patientName: string; author: string; tasks: FamilyTask[]; appointments: Appointment[]; demoToday: string; hindi?: boolean;
  calendar?: ReactNode; comfort?: ReactNode; calendarText?: string;
  tasksOnly?: boolean;
  section?: 'all' | 'tasks' | 'questions';
  onOpenTool?: (tool: FamilyTool) => void;
  onTrackQuestion?: (task: FamilyTask) => void;
  onAdd: (task: FamilyTask) => void; onComplete: (taskId: string, complete: boolean) => void;
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
  const patientTasks = tasks.filter((task) => task.patientId === patientId);
  const scheduled = appointments.filter((item) => item.hospitalId === patientId && item.status === 'Scheduled' && item.date >= demoToday)
    .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  const visit = scheduled[0];
  const openTasks = patientTasks.filter((task) => !task.completedBy && task.kind !== 'Question' && (!calendar || !task.due));
  const questions = patientTasks.filter((task) => !task.completedBy && task.kind === 'Question');
  const done = patientTasks.filter((task) => task.completedBy);

  function add(event: FormEvent<HTMLFormElement>, isQuestion = false) {
    event.preventDefault();
    const value = isQuestion ? question : title;
    if (!value.trim() || (!isQuestion && !owner.trim())) return;
    onAdd({ id: crypto.randomUUID(), patientId, title: value, kind: isQuestion ? 'Question' : kind, owner: isQuestion ? author : owner, due: isQuestion ? '' : due, createdBy: author, completedBy: null });
    if (isQuestion) setQuestion(''); else { setTitle(''); setDue(''); }
    setMessage(t(isQuestion ? 'Added to your questions.' : 'Added to your list.', isQuestion ? 'सवाल जोड़ दिया।' : 'काम जोड़ दिया।'));
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
    return <li key={task.id} className={task.completedBy ? 'is-complete' : ''}>
      <label><input type="checkbox" checked={Boolean(task.completedBy)} aria-label={`${task.kind === 'Question' ? t('Asked', 'पूछ लिया') : t('Done', 'हो गया')}: ${task.title}`} onChange={(event) => {
        onComplete(task.id, event.target.checked);
        setMessage(event.target.checked ? t(task.kind === 'Question' ? 'Marked as asked.' : 'Done.', 'हो गया।') : t('Added back to your list.', 'फिर से सूची में जोड़ दिया।'));
      }} /><span><strong>{task.title}</strong><span className="family-task-meta">{task.completedBy ? `${t('Done by', 'पूरा किया')} ${task.completedBy}` : `${task.owner.split(' ')[0]}${task.due ? ` · ${dateLabel(task.due)}` : ''}`}</span></span></label>
      {task.kind === 'Question' && onTrackQuestion && <button type="button" className="care-text-button" onClick={() => onTrackQuestion(task)}>{t('Keep replies', 'जवाब रखें')} →</button>}
    </li>;
  }

  return <section className="family-workspace" aria-labelledby={`${id}-title`}>
    <div className="family-workspace-heading"><h1 id={`${id}-title`}>{section === 'questions' ? t('Ask at the next visit', 'अगली मुलाकात में पूछें') : tasksOnly ? t('Family tasks', 'परिवार के काम') : `${patientName.split(' ')[0]}${t('’s care', ' की देखभाल')}`}</h1><button type="button" className="secondary-button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div>
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
          <ul className="family-task-list">{openTasks.map(taskRow)}</ul>
          {openTasks.length === 0 && !calendar && <p>{t('All done for now.', 'अभी के सभी काम हो गए।')}</p>}
          <form className="care-quick-add" onSubmit={(event) => add(event)}>
            <div className="care-input-row"><input aria-label={t('Add a task', 'काम जोड़ें')} placeholder={t('Add something to do', 'कोई काम लिखें')} value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={240} /><button type="submit" className="primary-button" disabled={!title.trim() || !owner.trim()}>{t('Add', 'जोड़ें')}</button></div>
            <details className="care-task-options"><summary>{t('Assign or set a date', 'किसे और कब करना है')}</summary><div className="family-task-form">
              <label htmlFor={`${id}-owner`}>{t('Who?', 'कौन?')}<input id={`${id}-owner`} value={owner} onChange={(event) => setOwner(event.target.value)} required maxLength={80} /></label>
              <label htmlFor={`${id}-due`}>{t('When?', 'कब?')}<input id={`${id}-due`} type="date" value={due} onChange={(event) => setDue(event.target.value)} /></label>
              <label htmlFor={`${id}-kind`}>{t('Type', 'किस तरह का काम')}<select id={`${id}-kind`} value={kind} onChange={(event) => setKind(event.target.value as FamilyTaskKind)}>{FAMILY_TASK_KINDS.filter((item) => item !== 'Question').map((item) => <option key={item}>{item}</option>)}</select></label>
              {kind === 'Medicines' && <p className="family-task-title">{t('Follow the existing prescription. This list does not change medicines or doses.', 'डॉक्टर के लिखे पर्चे का पालन करें। यह सूची दवा या खुराक नहीं बदलती।')}</p>}
            </div></details>
          </form>
        </section>
      </div>}
      {section !== 'tasks' && <section className="care-home-section care-question-panel" aria-labelledby={`${id}-questions`}>
        <h2 id={`${id}-questions`}>{section === 'questions' ? t('My questions', 'मेरे सवाल') : t('Ask at the next visit', 'अगली मुलाकात में पूछें')}</h2>
        <ul className="family-task-list">{questions.map(taskRow)}</ul>
        <form className="care-quick-add" onSubmit={(event) => add(event, true)}><div className="care-input-row"><input aria-label={t('Add a question', 'सवाल जोड़ें')} placeholder={t('What would you like to ask?', 'आप क्या पूछना चाहते हैं?')} value={question} onChange={(event) => setQuestion(event.target.value)} required maxLength={240} /><button type="submit" className="primary-button" disabled={!question.trim()}>{t('Add', 'जोड़ें')}</button></div></form>
        <button className="care-text-button care-prepare-link" type="button" onClick={onPrepare}>{t('Think through what matters to you', 'आपके लिए क्या ज़रूरी है?')} →</button>
      </section>}
    </div>
    {section === 'tasks' && <CareRouteLink className="care-text-button" view="visit-questions">{t('Questions for the next visit', 'अगली मुलाकात के सवाल')} →</CareRouteLink>}
    {done.length > 0 && <details className="care-completed"><summary>{t('Done', 'हो गए')} ({done.length})</summary><ul className="family-task-list">{done.map(taskRow)}</ul></details>}
    <p className="family-workspace-message" role="status">{message}</p>
  </section>;
}
