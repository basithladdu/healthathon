'use client';

import { useId, useState } from 'react';
import { downloadCarePdf } from './care-pdf';

const QUESTIONS = [
  ['understanding', 'What do you understand about the illness?', 'बीमारी के बारे में आप क्या समझते हैं?'],
  ['priorities', 'What matters to you?', 'आपके लिए क्या ज़रूरी है?'],
  ['worries', 'What is worrying you?', 'आपको किस बात की चिंता है?'],
  ['questions', 'What would you like to ask?', 'आप क्या पूछना चाहते हैं?'],
  ['people', 'Who would you like with you?', 'आप किसे अपने साथ चाहते हैं?'],
  ['language', 'Which language do you prefer?', 'आप किस भाषा में बात करना चाहते हैं?'],
] as const;

export type PreparationNotes = Partial<Record<(typeof QUESTIONS)[number][0], string>> & { checkIn?: 'no-change' | 'nothing-to-add' };

export function ConversationPreparation({ patientName, author, notes, onChange, hindi = false }: {
  patientName: string; author: string; notes: PreparationNotes; onChange: (notes: PreparationNotes) => void; hindi?: boolean;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [prompts, setPrompts] = useState(false);
  const [error, setError] = useState('');
  const hasNotes = QUESTIONS.some(([key]) => notes[key]?.trim());
  const t = (en: string, hi: string) => hindi ? hi : en;
  async function downloadNotes() {
    try { await downloadCarePdf({ fileName: 'saathi-next-visit-notes.pdf', title: patientName + ' — Next visit', subtitle: 'Written by ' + author,
      sections: QUESTIONS.filter(([key]) => notes[key]?.trim()).map(([key, en, hi]) => ({ heading: t(en, hi), lines: [notes[key]!.trim()] })) }); }
    catch { setError('The PDF could not be downloaded. Please try again.'); }
  }
  return <section className="conversation-preparation" aria-labelledby={id + '-title'}>
    <div className="doctor-page-heading"><div><span className="doctor-eyebrow">{patientName}</span><h1 id={id + '-title'}>{t('Anything for the next visit?', 'अगली मुलाकात के लिए कुछ?')}</h1></div>{hasNotes && <button className="secondary-button" type="button" onClick={downloadNotes}>PDF</button>}</div>
    <div className="preparation-quick-choices"><button type="button" aria-pressed={notes.checkIn === 'nothing-to-add'} onClick={() => { onChange({ ...notes, checkIn: 'nothing-to-add' }); setEditing(false); setPrompts(false); }}>{t('Nothing to add', 'कुछ जोड़ना नहीं है')}</button>{hasNotes && <button type="button" aria-pressed={notes.checkIn === 'no-change'} onClick={() => { onChange({ ...notes, checkIn: 'no-change' }); setEditing(false); }}>{t('No change', 'कोई बदलाव नहीं')}</button>}<button type="button" className="primary-button" onClick={() => { setEditing(true); onChange({ ...notes, checkIn: undefined }); }}>{hasNotes ? t('Edit my note', 'नोट बदलें') : t('Add a note', 'एक नोट जोड़ें')}</button><button type="button" aria-pressed={prompts} onClick={() => setPrompts(!prompts)}>{t('Help me think', 'सोचने में मदद करें')}</button></div>
    {notes.checkIn && <p className="preparation-check-in" role="status">{notes.checkIn === 'no-change' ? t('Your notes stay as they are.', 'आपके नोट वैसे ही रहेंगे।') : t('Nothing to add for now.', 'अभी कुछ जोड़ना नहीं है।')}</p>}
    {hasNotes && !editing && <div className="preparation-saved-notes">{QUESTIONS.filter(([key]) => notes[key]?.trim()).map(([key, en, hi]) => <article key={key}><h2>{t(en, hi)}</h2><p>{notes[key]}</p></article>)}</div>}
    {editing && <div className="preparation-fields"><label htmlFor={id + '-priorities'}>{t('What would you like the doctor to know?', 'डॉक्टर को क्या बताना चाहते हैं?')}<textarea id={id + '-priorities'} rows={4} value={notes.priorities ?? ''} onChange={(event) => onChange({ ...notes, priorities: event.target.value, checkIn: undefined })} /></label><button type="button" className="primary-button" onClick={() => setEditing(false)}>{t('Done', 'हो गया')}</button></div>}
    {prompts && <div className="preparation-prompt-grid">{QUESTIONS.filter(([key]) => key !== 'priorities').map(([key, en, hi]) => <details key={key}><summary>{t(en, hi)}</summary><label htmlFor={id + key} className="sr-only">{t(en, hi)}</label><textarea id={id + key} rows={2} value={notes[key] ?? ''} onChange={(event) => onChange({ ...notes, [key]: event.target.value, checkIn: undefined })} /></details>)}</div>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
