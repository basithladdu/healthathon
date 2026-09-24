'use client';

import { useId, useRef, useState, type FormEvent } from 'react';
import type { CareReport } from './care-calendar-state';
import type { CareDocumentText } from './care-document-state';
import { confirmCareDocumentTreatment, type CareDocumentTreatmentError, type CareDocumentTreatmentExcerpt, type ConfirmedDocumentTreatment } from './care-document-treatment-state';

export type { CareDocumentTreatmentSource, ConfirmedDocumentTreatment } from './care-document-treatment-state';
export type CareDocumentTreatmentReviewProps = {
  patientId: string; author: string; today: string; hindi: boolean;
  report: CareReport; document: CareDocumentText; excerpt: CareDocumentTreatmentExcerpt;
  onSave: (treatment: ConfirmedDocumentTreatment) => void;
  onOpenHistory?: () => void;
};

const errors: Record<CareDocumentTreatmentError, [string, string]> = {
  patient: ['Open a document for this person.', 'इस व्यक्ति का दस्तावेज़ खोलें।'],
  report: ['Open the original document before adding this date.', 'तारीख जोड़ने से पहले मूल दस्तावेज़ खोलें।'],
  source: ['This passage changed. Search the document again.', 'यह अंश बदल गया है। दस्तावेज़ में फिर खोजें।'],
  'source-long': ['Choose a shorter passage, up to 4,000 characters.', 'छोटा अंश चुनें, अधिकतम 4,000 अक्षर।'],
  title: ['Enter a treatment name, up to 160 characters.', 'इलाज का नाम लिखें, अधिकतम 160 अक्षर।'],
  date: ['Enter a valid date when treatment happened.', 'इलाज होने की सही तारीख लिखें।'],
  future: ['Use the date treatment happened, today or earlier.', 'इलाज होने की तारीख आज या उससे पहले की होनी चाहिए।'],
  confirmation: ['Confirm that the document records treatment happening on this date.', 'पुष्टि करें कि दस्तावेज़ में इस तारीख को इलाज होने का ज़िक्र है।'],
  author: ['Sign in before adding this treatment date.', 'इलाज की तारीख जोड़ने से पहले साइन इन करें।'],
  'review-time': ['Check your device clock and try again.', 'डिवाइस की घड़ी जाँचें और फिर कोशिश करें।'],
};

export function CareDocumentTreatmentReview(props: CareDocumentTreatmentReviewProps) {
  const { patientId, author, report, document, excerpt } = props;
  const passage = JSON.stringify([patientId, author, report.id, report.file.name, document.patientId, document.reportId, document.textSource, excerpt.line, excerpt.page, excerpt.text]);
  return <TreatmentReviewForPassage key={passage} {...props} />;
}

function TreatmentReviewForPassage({ patientId, author, today, hindi, report, document, excerpt, onSave, onOpenHistory }: CareDocumentTreatmentReviewProps) {
  const id = useId();
  const savedOnce = useRef(false);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<CareDocumentTreatmentError | null>(null);
  const t = (en: string, hi: string) => hindi ? hi : en;

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (savedOnce.current) return;
    const result = confirmCareDocumentTreatment({ patientId, author, today, report, document, excerpt, title, date, confirmed, reviewedAt: new Date().toISOString() });
    if (result.error !== null) { setError(result.error); return; }
    savedOnce.current = true;
    onSave(result.treatment);
    setSaved(true);
  }

  if (saved) return <div className="care-document-review-footer"><span role="status">{t('Added to care story.', 'इलाज की कहानी में जोड़ा गया।')}</span>{onOpenHistory && <button type="button" onClick={onOpenHistory}>{t('View care story', 'इलाज की कहानी देखें')}</button>}</div>;
  if (!open) return <button type="button" aria-expanded={false} aria-controls={`${id}-review`} onClick={() => setOpen(true)}>{t('Add treatment date', 'इलाज की तारीख जोड़ें')}</button>;
  return <form id={`${id}-review`} className="care-document-editor" onSubmit={save}>
    <div className="care-document-review-fields">
      <div className="care-document-fields-row">
        <label htmlFor={`${id}-title`}>{t('Treatment name', 'इलाज का नाम')}<input id={`${id}-title`} value={title} required maxLength={160} onChange={(event) => { setTitle(event.target.value); setConfirmed(false); setError(null); }} /></label>
        <label htmlFor={`${id}-date`}>{t('Date treatment happened', 'इलाज होने की तारीख')}<input id={`${id}-date`} type="date" value={date} max={today} required onChange={(event) => { setDate(event.target.value); setConfirmed(false); setError(null); }} /></label>
      </div>
      <label className="care-document-confirm" htmlFor={`${id}-confirmed`}><input id={`${id}-confirmed`} type="checkbox" checked={confirmed} required onChange={(event) => { setConfirmed(event.target.checked); setError(null); }} />{t('The document confirms this treatment happened on this date.', 'दस्तावेज़ पुष्टि करता है कि यह इलाज इस तारीख को हुआ था।')}</label>
      {error && <p className="care-document-error" role="alert">{errors[error][hindi ? 1 : 0]}</p>}
      <div className="care-document-review-footer"><button className="care-document-save" type="submit" disabled={!title.trim() || !date || !confirmed}>{t('Add to care story', 'इलाज की कहानी में जोड़ें')}</button><button type="button" onClick={() => { setOpen(false); setTitle(''); setDate(''); setConfirmed(false); setError(null); }}>{t('Cancel', 'रद्द करें')}</button></div>
    </div>
  </form>;
}
