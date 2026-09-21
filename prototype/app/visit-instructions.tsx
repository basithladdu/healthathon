'use client';

import { useId, useState } from 'react';
import type { Appointment } from './appointments-page';

export function VisitInstructions({ value, author, clinician, hindi, onChange }: {
  value: Appointment['preparationInstructions']; author: string; clinician: string; hindi: boolean;
  onChange: (value: Appointment['preparationInstructions']) => void;
}) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');
  const [givenBy, setGivenBy] = useState('');
  const [removed, setRemoved] = useState<Appointment['preparationInstructions']>();
  return <div className="visit-instructions">
    {value && <div className="visit-instructions-copy"><strong>{t('Doctor’s instructions', 'डॉक्टर के निर्देश')}</strong><p>{value.text}</p><small>{value.givenBy} · {t('Written here by', 'यहाँ लिखा')} {value.recordedBy}</small></div>}
    {editing ? <form onSubmit={(event) => {
      event.preventDefault(); if (!text.trim() || !givenBy.trim()) return;
      onChange({ text: text.trim(), givenBy: givenBy.trim(), recordedBy: author, updatedAt: new Date().toISOString() }); setEditing(false);
    }}>
      <label htmlFor={`${id}-text`}>{t('Copy the instructions', 'निर्देश ज्यों के त्यों लिखें')}<textarea id={`${id}-text`} value={text} onChange={(event) => setText(event.target.value)} rows={3} maxLength={1200} required /></label>
      <label htmlFor={`${id}-source`}>{t('Who gave these instructions?', 'ये निर्देश किसने दिए?')}<input id={`${id}-source`} value={givenBy} placeholder={clinician} onChange={(event) => setGivenBy(event.target.value)} maxLength={120} required /></label>
      <div className="visit-instructions-actions"><button type="submit" className="primary-button" disabled={!text.trim() || !givenBy.trim()}>{t('Save instructions', 'निर्देश सेव करें')}</button><button type="button" className="care-text-button" onClick={() => setEditing(false)}>{t('Cancel', 'रद्द करें')}</button></div>
    </form> : <div className="visit-instructions-actions">
      <button type="button" className="care-text-button" onClick={() => { setText(value?.text ?? ''); setGivenBy(value?.givenBy ?? ''); setEditing(true); }}>{value ? t('Edit instructions', 'निर्देश बदलें') : t('+ Doctor’s instructions', '+ डॉक्टर के निर्देश')}</button>
      {value && <button type="button" className="care-text-button" onClick={() => { setRemoved(value); onChange(undefined); }}>{t('Remove instructions', 'निर्देश हटाएँ')}</button>}
      {removed && !value && <button type="button" className="care-text-button" onClick={() => { onChange(removed); setRemoved(undefined); }}>{t('Undo removal', 'हटाना वापस करें')}</button>}
    </div>}
  </div>;
}
