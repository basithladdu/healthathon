'use client';

import { useId, useState, type FormEvent } from 'react';
import {
  addOpenQuestion, addOpenQuestionReply, editOpenQuestion, exportOpenQuestions, markOpenQuestionAsked, postponeOpenQuestion,
  removeOpenQuestion, restoreOpenQuestion, selectOpenQuestions, setOpenQuestionClosed, setOpenQuestionNextStep,
  type OpenQuestion, type OpenQuestionStatus,
} from './family-open-question-state';

export type { OpenQuestion, OpenQuestionReply } from './family-open-question-state';
type FamilyOpenQuestionsProps = { patientId: string; author: string; today: string; hindi: boolean; entries: OpenQuestion[]; onChange: (entries: OpenQuestion[]) => void };

export function FamilyOpenQuestions(props: FamilyOpenQuestionsProps) {
  return <QuestionsContent key={`${props.patientId}:${props.author}`} {...props} />;
}

function QuestionsContent(props: FamilyOpenQuestionsProps) {
  const { patientId, author, today, hindi, entries, onChange } = props;
  const id = useId();
  const [topic, setTopic] = useState('');
  const [owner, setOwner] = useState('');
  const [due, setDue] = useState('');
  const [message, setMessage] = useState('');
  const [removed, setRemoved] = useState<OpenQuestion[]>([]);
  const open = selectOpenQuestions(entries, patientId);
  const closed = selectOpenQuestions(entries, patientId, true);
  const lastRemoved = removed.at(-1);
  const t = (english: string, translated: string) => hindi ? translated : english;

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = addOpenQuestion(entries, patientId, author, today, crypto.randomUUID(), { topic, owner, due });
    if (next === entries) { setMessage(t('Check the question and date.', 'सवाल और तारीख जाँचें।')); return; }
    onChange(next); setTopic(''); setOwner(''); setDue(''); setMessage(t('Added. It has not been marked as asked.', 'जोड़ दिया। अभी इसे पूछा हुआ नहीं माना गया है।'));
  }

  function remove(question: OpenQuestion) {
    onChange(removeOpenQuestion(entries, patientId, question.id));
    setRemoved((previous) => [...previous, question]);
    setMessage('');
  }

  function undo() {
    if (!lastRemoved) return;
    const next = restoreOpenQuestion(entries, patientId, lastRemoved);
    if (next === entries) return;
    onChange(next); setRemoved((previous) => previous.slice(0, -1)); setMessage(t('Question restored.', 'सवाल वापस जोड़ दिया।'));
  }

  function download() {
    const url = URL.createObjectURL(new Blob([exportOpenQuestions(entries, patientId)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saanthvana-questions-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`; link.click(); URL.revokeObjectURL(url);
    setMessage(t('Questions and saved replies downloaded.', 'सवाल और सेव किए हुए जवाब डाउनलोड हो गए।'));
  }

  return <section className="family-open-questions" aria-labelledby={`${id}-heading`}>
    <div className="open-questions-heading"><h1 id={`${id}-heading`}>{t('Things we still need to ask', 'जो बातें अभी पूछनी हैं')}</h1><button type="button" className="secondary-button" disabled={open.length + closed.length === 0} onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div>
    <form className="open-question-add" onSubmit={add}>
      <div className="open-question-add-row"><input aria-label={t('Your question', 'आपका सवाल')} placeholder={t('What do you want to ask?', 'क्या पूछना चाहते हैं?')} value={topic} onChange={(event) => setTopic(event.target.value)} required maxLength={240} /><button type="submit" className="primary-button" disabled={!topic.trim()}>{t('Add', 'जोड़ें')}</button></div>
      <details><summary>{t('Who follows up and when? (optional)', 'कौन और कब पूछेगा? (चाहें तो)')}</summary><div className="open-question-two-fields"><label htmlFor={`${id}-owner`}>{t('Who?', 'कौन?')}<input id={`${id}-owner`} value={owner} onChange={(event) => setOwner(event.target.value)} maxLength={100} /></label><label htmlFor={`${id}-due`}>{t('When?', 'कब?')}<input id={`${id}-due`} type="date" value={due} onChange={(event) => setDue(event.target.value)} /></label></div></details>
    </form>
    {lastRemoved && <div className="open-question-undo" role="status"><span>{t('Removed', 'हटा दिया')}: {lastRemoved.topic}</span><button type="button" onClick={undo}>{t('Undo', 'वापस लाएँ')}</button></div>}
    <p className="open-question-message" role="status">{message}</p>
    <div className="open-question-list">{open.map((question) => <QuestionCard key={question.id} {...props} question={question} onRemove={() => remove(question)} />)}</div>
    {open.length === 0 && <p className="open-question-empty">{t('No open questions. Add one whenever something comes to mind.', 'अभी कोई सवाल बाकी नहीं है। कोई बात याद आए तो यहाँ जोड़ें।')}</p>}
    {closed.length > 0 && <details className="open-question-closed"><summary>{t('Closed questions', 'बंद किए हुए सवाल')} ({closed.length})</summary><div className="open-question-list">{closed.map((question) => <QuestionCard key={question.id} {...props} question={question} onRemove={() => remove(question)} />)}</div></details>}
  </section>;
}

function QuestionCard({ patientId, author, today, hindi, entries, onChange, question, onRemove }: FamilyOpenQuestionsProps & { question: OpenQuestion; onRemove: () => void }) {
  const id = useId();
  const [panel, setPanel] = useState<'reply' | 'next' | 'later' | 'edit' | null>(null);
  const [text, setText] = useState('');
  const [person, setPerson] = useState('');
  const [date, setDate] = useState('');
  const [message, setMessage] = useState('');
  const t = (english: string, translated: string) => hindi ? translated : english;
  const statuses: Record<OpenQuestionStatus, string> = { 'to-ask': t('To ask', 'अभी पूछना है'), waiting: t('Asked · no reply saved yet', 'पूछ लिया · जवाब अभी सेव नहीं हुआ'), replied: t('Reply saved by family', 'परिवार ने जवाब सेव किया'), closed: t('Closed', 'बंद किया') };
  const dateLabel = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

  function openPanel(next: typeof panel) {
    setPanel(next); setMessage('');
    setText(next === 'edit' ? question.topic : next === 'next' ? question.nextStep : '');
    setPerson(next === 'reply' ? '' : question.owner);
    setDate(next === 'reply' ? today : question.due);
  }

  function commit(next: OpenQuestion[], success: string) {
    if (next === entries) { setMessage(t('Check the details and try again.', 'जानकारी जाँचकर फिर से कोशिश करें।')); return; }
    onChange(next); setPanel(null); setMessage(success);
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (panel === 'reply') commit(addOpenQuestionReply(entries, patientId, question.id, author, { id: crypto.randomUUID(), text, saidBy: person, date }), t('Reply saved as your note.', 'जवाब आपके नोट के रूप में सेव हो गया।'));
    if (panel === 'next') commit(setOpenQuestionNextStep(entries, patientId, question.id, author, text, person, date), t('Next step saved.', 'अगला काम सेव हो गया।'));
    if (panel === 'later') commit(postponeOpenQuestion(entries, patientId, question.id, author, date, today), t('Follow-up date saved.', 'दोबारा पूछने की तारीख सेव हो गई।'));
    if (panel === 'edit') commit(editOpenQuestion(entries, patientId, question.id, author, { topic: text, owner: person, due: date }), t('Question updated.', 'सवाल बदल दिया।'));
  }

  function primaryAction() {
    if (question.status === 'to-ask') commit(markOpenQuestionAsked(entries, patientId, question.id, author, today), t('Marked as asked. No reply has been added.', 'पूछा हुआ मार्क किया। अभी कोई जवाब नहीं जोड़ा गया।'));
    if (question.status === 'waiting') openPanel('reply');
    if (question.status === 'replied') commit(setOpenQuestionClosed(entries, patientId, question.id, author, true), t('Question closed.', 'सवाल बंद किया।'));
    if (question.status === 'closed') commit(setOpenQuestionClosed(entries, patientId, question.id, author, false), t('Reopened. Earlier replies are kept.', 'फिर से खोल दिया। पहले के जवाब सुरक्षित हैं।'));
  }

  return <article className="open-question-card" aria-labelledby={`${id}-topic`}>
    <span className="open-question-status">{statuses[question.status]}</span>
    <h2 id={`${id}-topic`}>{question.topic}</h2>
    {(question.owner || question.due) && <p className="open-question-owner">{question.owner}{question.owner && question.due ? ' · ' : ''}{question.due && <time dateTime={question.due}>{dateLabel(question.due)}</time>}</p>}
    {question.nextStep && <div className="open-question-next"><h3>{t('Next step', 'अगला काम')}</h3><p>{question.nextStep}</p></div>}
    {question.replies.length > 0 && <details className="open-question-replies" open={question.status === 'replied' || undefined}><summary>{t('Family-saved replies', 'परिवार ने जो जवाब लिखे')} ({question.replies.length})</summary>{question.replies.map((reply) => <div className="open-question-reply" key={reply.id}><p>{reply.text}</p><span>{t('Said by', 'किसने कहा')}: {reply.saidBy} · {dateLabel(reply.date)}</span><span>{t('Written here by', 'यहाँ लिखा')}: {reply.recordedBy}</span></div>)}</details>}
    {question.status === 'closed' && question.replies.length === 0 && <p className="open-question-owner">{t('Closed without a saved reply.', 'कोई जवाब सेव किए बिना बंद किया।')}</p>}
    {!panel && <div className="open-question-actions"><button type="button" className="primary-button" onClick={primaryAction}>{question.status === 'to-ask' ? t('Mark as asked', 'पूछ लिया') : question.status === 'waiting' ? t('Add the reply', 'जवाब लिखें') : question.status === 'replied' ? t('Close question', 'सवाल बंद करें') : t('Reopen', 'फिर खोलें')}</button>
      {question.status !== 'closed' && <><button type="button" className="open-question-link" onClick={() => openPanel('next')}>{t('Next step', 'अगला काम')}</button><button type="button" className="open-question-link" onClick={() => openPanel('later')}>{t('Ask later', 'बाद में पूछें')}</button></>}
    </div>}
    {panel && <form className="open-question-form" onSubmit={save}>
      <h3>{panel === 'reply' ? t('Save a reply in your words', 'जवाब अपने शब्दों में लिखें') : panel === 'next' ? t('What will you do next?', 'अब आगे क्या करेंगे?') : panel === 'later' ? t('When should you follow up?', 'दोबारा कब पूछना है?') : t('Edit question', 'सवाल बदलें')}</h3>
      {panel !== 'later' && <label htmlFor={`${id}-text`}>{panel === 'reply' ? t('What was said?', 'क्या कहा गया?') : panel === 'next' ? t('Next step', 'अगला काम') : t('Question', 'सवाल')}{panel === 'edit' ? <input id={`${id}-text`} value={text} onChange={(event) => setText(event.target.value)} maxLength={240} required /> : <textarea id={`${id}-text`} value={text} onChange={(event) => setText(event.target.value)} rows={3} maxLength={panel === 'reply' ? 4000 : 1000} required />}</label>}
      {panel === 'reply' ? <div className="open-question-two-fields"><label htmlFor={`${id}-person`}>{t('Who said it?', 'किसने कहा?')}<input id={`${id}-person`} value={person} onChange={(event) => setPerson(event.target.value)} maxLength={100} required /></label><label htmlFor={`${id}-date`}>{t('When was it said?', 'कब कहा?')}<input id={`${id}-date`} type="date" value={date} onChange={(event) => setDate(event.target.value)} max={today} required /></label></div>
        : panel === 'later' ? <label htmlFor={`${id}-date`}>{t('Follow up on', 'इस दिन दोबारा पूछें')}<input id={`${id}-date`} type="date" value={date} onChange={(event) => setDate(event.target.value)} min={today} required /></label>
          : <details><summary>{t('Who and when? (optional)', 'कौन और कब? (चाहें तो)')}</summary><div className="open-question-two-fields"><label htmlFor={`${id}-person`}>{t('Who?', 'कौन?')}<input id={`${id}-person`} value={person} onChange={(event) => setPerson(event.target.value)} maxLength={100} /></label><label htmlFor={`${id}-date`}>{t('When?', 'कब?')}<input id={`${id}-date`} type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label></div></details>}
      <div className="open-question-actions"><button type="submit" className="primary-button" disabled={panel === 'later' ? !date : !text.trim() || (panel === 'reply' && (!person.trim() || !date))}>{t('Save', 'सेव करें')}</button><button type="button" className="secondary-button" onClick={() => { setPanel(null); setMessage(''); }}>{t('Cancel', 'रहने दें')}</button></div>
    </form>}
    <details className="open-question-more"><summary>{t('More', 'और विकल्प')}</summary><div className="open-question-actions"><button type="button" className="open-question-link" onClick={() => openPanel('edit')}>{t('Edit question', 'सवाल बदलें')}</button><button type="button" className="open-question-link" onClick={onRemove}>{t('Remove', 'हटाएँ')}</button>{question.status !== 'closed' && question.status !== 'replied' && <button type="button" className="open-question-link" onClick={() => commit(setOpenQuestionClosed(entries, patientId, question.id, author, true), t('Closed without adding a reply.', 'बिना जवाब जोड़े बंद कर दिया।'))}>{t('Close without a reply', 'बिना जवाब के बंद करें')}</button>}</div></details>
    <p className="open-question-by">{t('Added by', 'जोड़ा')}: {question.createdBy}{question.updatedBy !== question.createdBy && <> · {t('updated by', 'बदला')}: {question.updatedBy}</>}</p>
    <p className="open-question-message" role="status">{message}</p>
  </article>;
}
