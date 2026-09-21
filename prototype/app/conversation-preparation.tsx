'use client';

import { useId } from 'react';

const QUESTIONS = [
  ['understanding', 'What do you understand about the illness?', 'बीमारी के बारे में आप क्या समझते हैं?'],
  ['priorities', 'What matters to you?', 'आपके लिए क्या ज़रूरी है?'],
  ['worries', 'What is worrying you?', 'आपको किस बात की चिंता है?'],
  ['questions', 'What would you like to ask?', 'आप क्या पूछना चाहते हैं?'],
  ['people', 'Who would you like with you?', 'आप किसे अपने साथ चाहते हैं?'],
  ['language', 'Which language do you prefer?', 'आप किस भाषा में बात करना चाहते हैं?'],
] as const;

export type PreparationNotes = Partial<Record<(typeof QUESTIONS)[number][0], string>>;

export function ConversationPreparation({ patientName, author, notes, onChange, hindi = false }: {
  patientName: string; author: string; notes: PreparationNotes; onChange: (notes: PreparationNotes) => void; hindi?: boolean;
}) {
  const id = useId();
  const hasNotes = Object.values(notes).some((value) => value?.trim());
  function downloadNotes() {
    const text = [
      'Saanthvana — notes to bring to your doctor', `Patient: ${patientName}`, `Written by: ${author}`,
      'Personal notes. Not reviewed by a doctor; not a treatment decision or an advance medical directive.',
      ...QUESTIONS.filter(([key]) => notes[key]?.trim()).map(([key, question]) => `${question}\n${notes[key]?.trim()}`),
    ].join('\n\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'saanthvana-conversation-notes.txt'; link.click(); URL.revokeObjectURL(url);
  }
  return <section className="conversation-preparation" aria-labelledby={`${id}-title`}>
    <h1 id={`${id}-title`}>{hindi ? 'आपके लिए क्या ज़रूरी है?' : 'What matters to you?'}</h1>
    <p className="preparation-person">{patientName} · {author}</p>
    <p className="preparation-boundary">{hindi ? 'ये आपके निजी नोट हैं। इन्हें डॉक्टर को दिखाने के लिए डाउनलोड कर सकते हैं।' : 'Your own notes. Save a copy to bring to the doctor.'}</p>
    <div className="preparation-fields"><label htmlFor={`${id}-priorities`}>{hindi ? 'डॉक्टर को क्या बताना चाहते हैं?' : 'What would you like the doctor to know?'}<textarea id={`${id}-priorities`} rows={5} value={notes.priorities ?? ''} onChange={(event) => onChange({ ...notes, priorities: event.target.value })} /></label></div>
    <details className="care-preparation-prompts"><summary>{hindi ? 'सोचने के लिए कुछ और सवाल' : 'A few prompts, if you need them'}</summary><div className="preparation-fields">
      {QUESTIONS.filter(([key]) => key !== 'priorities').map(([key, en, hi]) => <label htmlFor={`${id}-${key}`} key={key}>{hindi ? hi : en}
        {key === 'language' ? <input id={`${id}-${key}`} value={notes[key] ?? ''} onChange={(event) => onChange({ ...notes, [key]: event.target.value })} /> : <textarea id={`${id}-${key}`} rows={2} value={notes[key] ?? ''} onChange={(event) => onChange({ ...notes, [key]: event.target.value })} />}
      </label>)}
    </div></details>
    <button className="primary-button" type="button" disabled={!hasNotes} onClick={downloadNotes}>{hindi ? 'कॉपी डाउनलोड करें' : 'Save a copy'}</button>
  </section>;
}
