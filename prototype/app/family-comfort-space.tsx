'use client';

import { useId, useState } from 'react';
import {
  COMFORT_FEELINGS, addComfortEntry, exportComfortEntries, latestComfortEntryToday, removeComfortEntry,
  restoreComfortEntry, selectComfortEntries, type ComfortEntry, type ComfortFeeling,
} from './family-comfort-state';

export type { ComfortEntry } from './family-comfort-state';
type FamilyComfortSpaceProps = {
  patientId: string;
  author: string;
  today: string;
  hindi: boolean;
  entries: ComfortEntry[];
  onChange: (entries: ComfortEntry[]) => void;
  onOpenJournal: () => void;
  onOpenSymptoms?: () => void;
};

const FEELING_HINDI: Record<ComfortFeeling, string> = { Peaceful: 'सुकून है', 'In pain': 'दर्द है', Tired: 'थकान है', Happy: 'खुश हूँ' };

export function FamilyComfortSpace(props: FamilyComfortSpaceProps) {
  return <ComfortSpaceContent key={`${props.patientId}:${props.author}`} {...props} />;
}

function ComfortSpaceContent({ patientId, author, today, hindi, entries, onChange, onOpenJournal, onOpenSymptoms }: FamilyComfortSpaceProps) {
  const id = useId();
  const [removed, setRemoved] = useState<ComfortEntry[]>([]);
  const [message, setMessage] = useState('');
  const history = selectComfortEntries(entries, patientId, author);
  const current = latestComfortEntryToday(entries, patientId, author, today);
  const lastRemoved = removed.at(-1);
  const t = (english: string, translated: string) => hindi ? translated : english;
  const feelingLabel = (feeling: ComfortFeeling) => hindi ? FEELING_HINDI[feeling] : feeling;
  const dateLabel = (date: string) => new Date(`${date}T00:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const timeLabel = (createdAt: string) => new Date(createdAt).toLocaleTimeString(hindi ? 'hi-IN' : 'en-GB', { hour: '2-digit', minute: '2-digit' });

  function record(feeling: ComfortFeeling) {
    const next = addComfortEntry(entries, { id: crypto.randomUUID(), patientId, author, date: today, feeling, createdAt: new Date().toISOString() });
    if (next === entries) return;
    onChange(next);
    setMessage(t('Check-in added.', 'आपका हाल दर्ज हो गया।'));
  }

  function remove(entry: ComfortEntry) {
    onChange(removeComfortEntry(entries, patientId, author, entry.id));
    setRemoved((previous) => [...previous, entry]);
    setMessage('');
  }

  function undo() {
    if (!lastRemoved) return;
    const next = restoreComfortEntry(entries, patientId, author, lastRemoved);
    if (next === entries) return;
    onChange(next);
    setRemoved((previous) => previous.slice(0, -1));
    setMessage(t('Check-in restored.', 'आपका हाल फिर से जोड़ दिया।'));
  }

  function download() {
    const text = exportComfortEntries(entries, patientId, author);
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saanthvana-comfort-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`; link.click(); URL.revokeObjectURL(url);
    setMessage(t('Your check-ins were downloaded.', 'आपका लिखा हुआ हाल डाउनलोड हो गया।'));
  }

  return <section className="family-comfort-space" aria-labelledby={`${id}-heading`}>
    <div className="comfort-bento">
      <div className="comfort-check-in">
        <h2 id={`${id}-heading`}>{t('My comfort space', 'मेरे सुकून की जगह')}</h2>
        <fieldset><legend>{author.split(' ')[0]}, {t('how are you feeling?', 'कैसा महसूस हो रहा है?')}</legend><div className="comfort-feelings">{COMFORT_FEELINGS.map((feeling) => <button key={feeling} type="button" className="comfort-feeling" data-feeling={feeling} aria-pressed={current?.feeling === feeling} onClick={() => record(feeling)}>{feelingLabel(feeling)}</button>)}</div></fieldset>
      </div>
      <div className="comfort-thought">
        <div className="comfort-orb" data-feeling={current?.feeling ?? 'none'} aria-hidden="true" />
        <p className="comfort-current" aria-live="polite">{current ? <>{feelingLabel(current.feeling)} <span>· {timeLabel(current.createdAt)}</span></> : t('No check-in today', 'आज का हाल अभी नहीं लिखा')}</p>
        <button className="comfort-thought-button" type="button" onClick={onOpenJournal}>{t('Record a thought', 'मन की बात लिखें या बोलें')}</button>
        {current?.feeling === 'In pain' && onOpenSymptoms && <button type="button" className="comfort-symptom-button" onClick={onOpenSymptoms}>{t('Add details in my diary', 'डायरी में और लिखें')}</button>}
      </div>
    </div>
    {lastRemoved && <div className="comfort-undo" role="status"><span>{t('Removed', 'हटा दिया')}: {feelingLabel(lastRemoved.feeling)} · {dateLabel(lastRemoved.date)}</span><button type="button" onClick={undo}>{t('Undo', 'वापस लाएँ')}</button></div>}
    <div className="comfort-history-wrap">
      <details className="comfort-history"><summary>{t('My check-ins', 'मैंने अपना हाल कब लिखा')} ({history.length})</summary>
        {history.length ? <><div className="comfort-history-heading"><span>{author}</span><button type="button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div><ol>{history.map((entry) => <li key={entry.id}><div><strong>{feelingLabel(entry.feeling)}</strong><time dateTime={entry.createdAt}>{dateLabel(entry.date)} · {timeLabel(entry.createdAt)}</time></div><button type="button" onClick={() => remove(entry)} aria-label={`${t('Remove', 'हटाएँ')}: ${feelingLabel(entry.feeling)}, ${dateLabel(entry.date)}, ${timeLabel(entry.createdAt)}`}>{t('Remove', 'हटाएँ')}</button></li>)}</ol></> : <p>{t('Your check-ins will appear here.', 'आपका लिखा हुआ हाल यहाँ दिखाई देगा।')}</p>}
      </details>
      <p className="comfort-message" role="status">{message}</p>
    </div>
  </section>;
}
