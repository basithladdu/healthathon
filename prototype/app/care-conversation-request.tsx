'use client';

import { useId, useState } from 'react';
import { createCareConversationRequest, type CareConversationRequest } from './care-conversation-request-state';
import './care-conversation-request.css';

export type CareConversationRequestFormProps = {
  patientId: string;
  patientName: string;
  requester: string;
  requesterRole: CareConversationRequest['role'];
  hindi?: boolean;
  request?: CareConversationRequest;
  onSave: (request: CareConversationRequest) => void;
  onCancel: (id: string) => void;
  onOpenSignedVersion?: (version: number) => void;
};

const TOPICS = [
  ['What matters to me', 'मेरे लिए क्या मायने रखता है'],
  ['People close to me', 'मेरे अपने लोग'],
  ['Comfort and daily life', 'आराम और रोज़मर्रा की ज़िंदगी'],
  ['Questions for my care team', 'देखभाल टीम से सवाल'],
] as const;

export function CareConversationRequestForm({ patientId, patientName, requester, requesterRole, hindi = false, request, onSave, onCancel, onOpenSignedVersion }: CareConversationRequestFormProps) {
  const id = useId();
  const [topics, setTopics] = useState<string[]>(request?.topics ?? []);
  const [note, setNote] = useState(request?.note ?? '');
  const [editing, setEditing] = useState(!request || request.status === 'cancelled');
  const [requestAnother, setRequestAnother] = useState(false);
  const t = (en: string, hi: string) => hindi ? hi : en;
  const statusLabel: Record<CareConversationRequest['status'], string> = {
    requested: t('Requested', 'अनुरोध किया'),
    'in-progress': t('Conversation in progress', 'बातचीत जारी है'),
    completed: t('Conversation recorded', 'बातचीत दर्ज हुई'),
    cancelled: t('Request cancelled', 'अनुरोध रद्द हुआ'),
  };

  function toggleTopic(topic: string) {
    setTopics((current) => current.includes(topic) ? current.filter((item) => item !== topic) : [...current, topic]);
  }

  function save() {
    if (request && request.status !== 'cancelled' && request.status !== 'completed' && !requestAnother) {
      onSave({ ...request, requestedBy: requester, role: requesterRole, topics, note: note.trim(), updatedAt: new Date().toISOString() });
    } else {
      onSave(createCareConversationRequest({ patientId, requestedBy: requester, role: requesterRole, topics, note: note.trim() }));
    }
    setEditing(false); setRequestAnother(false);
  }

  function edit() {
    setTopics(request?.topics ?? []);
    setNote(request?.note ?? '');
    setEditing(true);
  }

  return <section className="care-conversation-request" aria-labelledby={`${id}-title`}>
    <div className="care-request-head"><span className="care-request-mark" aria-hidden="true">↗</span><div><h2 id={`${id}-title`}>{t('Talk about what matters', 'जो आपके लिए मायने रखता है उस पर बात करें')}</h2></div></div>
    <p className="care-request-intro">{t('A care conversation is a chance to say what matters to you, choose who joins, and ask your care team questions.', 'देखभाल की बातचीत में आप बता सकते हैं कि आपके लिए क्या मायने रखता है, कौन साथ आए, और अपनी देखभाल टीम से सवाल पूछ सकते हैं।')}</p>
    {request && request.status !== 'cancelled' && !editing && !requestAnother ? <div className="care-request-current">
      <div className="care-request-status"><span className={`care-request-status-dot is-${request.status}`} aria-hidden="true" /><strong>{statusLabel[request.status]}</strong></div>
      {request.topics.length > 0 && <ul className="care-request-topic-list">{request.topics.map((topic) => <li key={topic}>{topic}</li>)}</ul>}
      {request.note && <p className="care-request-note">{request.note}</p>}
      <small>{request.requestedBy || requester} · {patientName}</small>
      {request.status === 'completed' && request.signedVersion && onOpenSignedVersion && <button type="button" className="care-request-link" onClick={() => onOpenSignedVersion(request.signedVersion!)}>{t('Open signed Care Note', 'हस्ताक्षरित देखभाल नोट खोलें')} <span aria-hidden="true">↗</span></button>}
      {request.status === 'completed' && <button type="button" className="care-request-another" onClick={() => { setTopics([]); setNote(''); setRequestAnother(true); setEditing(true); }}>{t('Request another conversation', 'एक और बातचीत का अनुरोध करें')}</button>}
      {(request.status === 'requested' || request.status === 'in-progress') && <div className="care-request-actions"><button type="button" className="care-request-secondary" onClick={edit}>{t('Edit request', 'अनुरोध बदलें')}</button><button type="button" className="care-request-cancel" onClick={() => onCancel(request.id)}>{t('Cancel request', 'अनुरोध रद्द करें')}</button></div>}
    </div> : <div className="care-request-compose">
      {requestAnother && request?.status === 'completed' && <div className="care-request-previous"><div className="care-request-status"><span className="care-request-status-dot is-completed" aria-hidden="true" /><strong>{statusLabel.completed}</strong></div>{request.signedVersion && onOpenSignedVersion && <button type="button" className="care-request-link" onClick={() => onOpenSignedVersion(request.signedVersion!)}>{t('Open signed Care Note', 'हस्ताक्षरित देखभाल नोट खोलें')} <span aria-hidden="true">↗</span></button>}{request.topics.length > 0 && <ul className="care-request-topic-list">{request.topics.map((topic) => <li key={topic}>{topic}</li>)}</ul>}{request.note && <p className="care-request-note">{request.note}</p>}<small>{request.requestedBy || requester} · {patientName}</small></div>}
      {request?.status === 'cancelled' && <div className="care-request-status"><span className="care-request-status-dot" aria-hidden="true" /><strong>{statusLabel.cancelled}</strong></div>}
      <fieldset>
        <legend>{t('Anything you’d like to include?', 'क्या आप कुछ बताना चाहेंगे?')} <span>{t('Optional', 'वैकल्पिक')}</span></legend>
        <div className="care-request-topics">{TOPICS.map(([en, hi]) => { const topic = t(en, hi); return <button key={en} type="button" aria-pressed={topics.includes(topic)} onClick={() => toggleTopic(topic)}>{topic}</button>; })}</div>
      </fieldset>
      <label className="care-request-note-label" htmlFor={`${id}-note`}>{t('A note for the care team', 'देखभाल टीम के लिए संदेश')} <span>{t('Optional', 'वैकल्पिक')}</span></label>
      <textarea id={`${id}-note`} value={note} maxLength={240} rows={2} placeholder={t('Add a short note', 'छोटा संदेश लिखें')} onChange={(event) => setNote(event.target.value)} />
      <div className="care-request-compose-actions">
        <button type="button" className="care-request-send" onClick={save}>{request?.status === 'cancelled' ? t('Request again', 'फिर अनुरोध करें') : requestAnother || request?.status === 'completed' ? t('Request another conversation', 'एक और बातचीत का अनुरोध करें') : request ? t('Save changes', 'बदलाव सेव करें') : t('Request a conversation', 'बातचीत का अनुरोध करें')}</button>
        {request && request.status !== 'cancelled' && <button type="button" className="care-request-secondary" onClick={() => { setEditing(false); setRequestAnother(false); }}>{requestAnother ? t('Keep completed record', 'दर्ज बातचीत रखें') : t('Keep current request', 'मौजूदा अनुरोध रखें')}</button>}
      </div>
      <span className="sr-only" aria-live="polite">{requester} · {requesterRole}</span>
    </div>}
  </section>;
}
