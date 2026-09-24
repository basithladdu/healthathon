'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { SummaryRelease } from './summary-state';
import { downloadCarePdf } from './care-pdf';
import {
  addCareCopyEntry, approvedPatientCopies, careCopyLogText, groupCareCopyEntries, removeCareCopyEntry,
  restoreCareCopyEntry, updateCareCopyEntry, validateCareCopyDraft,
  type CareCopyDraft, type CareCopyEntry, type CareCopyStatus,
} from './family-copy-state';
import './family-copy-tracker.css';
import type { CareNoteAcknowledgement } from './care-note-signing-state';

export type { CareCopyEntry } from './family-copy-state';
type Props = {
  patientId: string; patientName: string; author: string; today: string; hindi: boolean;
  releases: readonly SummaryRelease[]; entries: CareCopyEntry[]; onChange: (entries: CareCopyEntry[]) => void;
  acknowledgements?: readonly CareNoteAcknowledgement[];
};

export function FamilyCopyTracker(props: Props) {
  return <CopyTrackerForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function CopyTrackerForPatient({ patientId, patientName, author, today, hindi, releases, entries, onChange, acknowledgements = [] }: Props) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const copies = approvedPatientCopies(releases, patientId);
  const latest = copies[0];
  const recipients = groupCareCopyEntries(entries, releases, patientId);
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<CareCopyDraft>({ recipient: '', version: latest?.number ?? 0, givenOn: today, locationNote: '' });
  const [removed, setRemoved] = useState<Array<{ entry: CareCopyEntry; index: number }>>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState(false);
  const nameInput = useRef<HTMLInputElement>(null);
  const selectedCopy = copies.find((copy) => copy.number === draft.version);
  const dateLabel = (value: string) => new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const statusLabel = (status: CareCopyStatus) => status === 'latest' ? t('Latest version recorded', 'नया संस्करण दर्ज है') : status === 'older' ? t('Older version recorded', 'पुराना संस्करण दर्ज है') : t('Version not available here', 'यह संस्करण यहाँ नहीं है');

  useEffect(() => { if (formOpen) nameInput.current?.focus(); }, [formOpen, editingId]);

  function openForm(recipient = '', entry?: CareCopyEntry) {
    setDraft(entry ? { recipient: entry.recipient, version: entry.version, givenOn: entry.givenOn, locationNote: entry.locationNote } : { recipient, version: latest?.number ?? 0, givenOn: today, locationNote: '' });
    setEditingId(entry?.id ?? null); setFormOpen(true); setError(''); setMessage('');
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateCareCopyDraft(draft, releases, patientId, today);
    if (invalid) {
      const errors = {
        recipient: t('Add a person or place, up to 160 characters.', 'व्यक्ति या जगह का नाम लिखें, 160 अक्षरों तक।'),
        version: t('Choose an approved version available for this patient.', 'इस मरीज़ का मंज़ूर किया हुआ संस्करण चुनें।'),
        date: t('Choose the date you gave this copy, after its release and no later than today.', 'कॉपी देने की तारीख चुनें: मंज़ूरी के बाद और आज तक की।'),
        location: t('Keep the location note under 1,500 characters.', 'जगह का नोट 1,500 अक्षरों तक रखें।'),
      };
      setError(errors[invalid]); return;
    }
    const updated = editingId
      ? updateCareCopyEntry(entries, patientId, editingId, author, draft, releases, today)
      : addCareCopyEntry(entries, patientId, author, crypto.randomUUID(), draft, releases, today);
    if (updated === entries) { setError(t('This entry could not be saved. Try again.', 'यह जानकारी सेव नहीं हुई। फिर कोशिश करें।')); return; }
    onChange(updated); setFormOpen(false); setEditingId(null); setMessage(t(editingId ? 'Your correction is saved.' : 'Added to your copy log.', editingId ? 'सुधार सेव हो गया।' : 'कॉपी की सूची में जोड़ दिया।'));
  }

  function download(text: string, name: string) {
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = name;
    document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function downloadLatest() {
    if (!latest?.fields || downloading) return;
    setDownloading(true); setMessage('');
    try {
      const fields = latest.fields;
      await downloadCarePdf({
        fileName: `saathi-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}-note-v${latest.number}.pdf`,
        title: t('Doctor-reviewed Care Note', 'डॉक्टर का मंज़ूर किया हुआ नोट'),
        subtitle: `${patientName} · ${patientId}`,
        careNote: { release: latest, acknowledgements },
        sections: [
          { heading: t('Review details', 'मंज़ूरी की जानकारी'), lines: [`${t('Version', 'संस्करण')}: ${latest.number}`, `${t('Doctor', 'डॉक्टर')}: ${latest.physician}`, `${t('Released', 'जारी किया')}: ${latest.releasedAt}`, ...(latest.authorisation ? [latest.authorisation] : []), ...(latest.signature ? [`${t('Signed name', 'दर्ज हस्ताक्षर नाम')}: ${latest.signature.name}`, `${t('Signed on', 'हस्ताक्षर की तारीख')}: ${latest.signature.signedAt}`] : [])] },
          { heading: t('What matters to the patient', 'मरीज़ के लिए क्या ज़रूरी है'), lines: [fields.priorities] },
          { heading: t('Who was there', 'कौन साथ थे'), lines: [fields.participants] },
          { heading: t('What was discussed', 'क्या बात हुई'), lines: [fields.topics] },
          { heading: t('Still to discuss', 'अभी बात करना बाकी है'), lines: [fields.openQuestions] },
          { heading: t('Next steps', 'आगे क्या करना है'), lines: [fields.followUp] },
          { heading: t('Care conversation', 'देखभाल की बातचीत'), lines: [t('This reviewed conversation note is not a prescription or a legal directive.', 'यह मंज़ूर किया हुआ बातचीत का नोट है; दवा का पर्चा या क़ानूनी निर्देश नहीं।')] },
        ],
      });
      setMessage(t(`Version ${latest.number} PDF is downloading.`, `संस्करण ${latest.number} की PDF डाउनलोड हो रही है।`));
    } catch (failure) {
      setMessage(failure instanceof Error ? failure.message : t('The PDF could not download. Try again.', 'PDF डाउनलोड नहीं हुई। फिर कोशिश करें।'));
    } finally { setDownloading(false); }
  }

  function remove(entry: CareCopyEntry) {
    setRemoved((previous) => [...previous, { entry, index: entries.findIndex((item) => item.patientId === patientId && item.id === entry.id) }]);
    onChange(removeCareCopyEntry(entries, patientId, entry.id));
    if (editingId === entry.id) { setFormOpen(false); setEditingId(null); }
    setMessage(t('Entry removed from your log.', 'जानकारी आपकी सूची से हटा दी।'));
  }

  function undo() {
    const last = removed.at(-1);
    if (!last) return;
    const updated = restoreCareCopyEntry(entries, patientId, last.entry, last.index);
    if (updated === entries) return;
    onChange(updated); setRemoved((previous) => previous.slice(0, -1)); setMessage(t('Entry restored.', 'जानकारी वापस जोड़ दी।'));
  }

  return <section className="family-copy-tracker" aria-labelledby={`${id}-heading`}>
    <div className="copy-tracker-heading"><h1 id={`${id}-heading`}>{t('Who has the latest copy?', 'नई कॉपी किसके पास है?')}</h1><button type="button" disabled={!recipients.length} onClick={() => {
      download(careCopyLogText(entries, releases, patientId, patientName), `saathi-copy-log-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`); setMessage(t('Your copy log is downloading.', 'कॉपी की सूची डाउनलोड हो रही है।'));
    }}>{t('Save this list', 'यह सूची डाउनलोड करें')}</button></div>
    <div className="copy-current-note"><div><h2>{latest ? t(`Latest note · version ${latest.number}`, `नया नोट · संस्करण ${latest.number}`) : t('No approved note here yet', 'यहाँ अभी मंज़ूर किया हुआ नोट नहीं है')}</h2>{latest && <p>{latest.physician} · {dateLabel(latest.releasedAt)}</p>}</div><button type="button" className="primary-button" disabled={!latest?.fields || downloading} onClick={downloadLatest}>{downloading ? t('Making PDF…', 'PDF बन रही है…') : t('Download latest PDF', 'नए नोट की PDF लें')}</button></div>
    {!formOpen && <div className="copy-log-start"><button type="button" onClick={() => openForm()} disabled={!copies.length}>{t('I gave someone a copy', 'मैंने किसी को कॉपी दी')}</button></div>}
    {formOpen && <form className="copy-tracker-form" onSubmit={save} aria-labelledby={`${id}-form-title`}>
      <h2 id={`${id}-form-title`}>{editingId ? t('Correct this entry', 'इस जानकारी में सुधार करें') : t('Record the copy you gave', 'दी हुई कॉपी की जानकारी लिखें')}</h2>
      <label className="copy-form-wide" htmlFor={`${id}-recipient`}>{t('Person or place', 'व्यक्ति या जगह')}<input ref={nameInput} id={`${id}-recipient`} list={`${id}-recipients`} value={draft.recipient} required maxLength={160} onChange={(event) => setDraft({ ...draft, recipient: event.target.value })} autoComplete="off" /><datalist id={`${id}-recipients`}>{recipients.map((recipient) => <option key={recipient.key} value={recipient.recipient} />)}</datalist></label>
      <label htmlFor={`${id}-version`}>{t('Which version did you give?', 'कौन सा संस्करण दिया?')}<select id={`${id}-version`} value={draft.version} onChange={(event) => setDraft({ ...draft, version: Number(event.target.value) })} required>
        {!selectedCopy && <option value={draft.version} disabled>{t('Choose a version', 'संस्करण चुनें')}</option>}{copies.map((copy) => <option key={copy.number} value={copy.number}>{t('Version', 'संस्करण')} {copy.number} · {dateLabel(copy.releasedAt)}{copy.number === latest?.number ? ` · ${t('Latest', 'नया')}` : ''}</option>)}
      </select></label>
      <label htmlFor={`${id}-given`}>{t('Date given', 'देने की तारीख')}<input id={`${id}-given`} type="date" value={draft.givenOn} min={selectedCopy?.releasedAt.slice(0, 10)} max={today} required onChange={(event) => setDraft({ ...draft, givenOn: event.target.value })} /></label>
      <label className="copy-form-wide" htmlFor={`${id}-location`}>{t('Where is the copy? (optional)', 'कॉपी कहाँ है? (चाहें तो)')}<textarea id={`${id}-location`} value={draft.locationNote} rows={2} maxLength={1500} onChange={(event) => setDraft({ ...draft, locationNote: event.target.value })} /></label>
      {error && <p className="copy-form-error copy-form-wide" role="alert">{error}</p>}
      <div className="copy-form-actions copy-form-wide"><button type="submit" className="primary-button">{editingId ? t('Save correction', 'सुधार सेव करें') : t('I gave them this copy', 'मैंने उन्हें यह कॉपी दी')}</button><button type="button" onClick={() => { setFormOpen(false); setEditingId(null); setError(''); }}>{t('Cancel', 'रहने दें')}</button></div>
    </form>}
    <div className="copy-recipient-list">{recipients.map((recipient) => <article className="copy-recipient-card" key={recipient.key}>
      <div className="copy-recipient-heading"><div><h2>{recipient.recipient}</h2><p className={`copy-version-state is-${recipient.status}`}>{statusLabel(recipient.status)}</p></div><button type="button" disabled={!copies.length} onClick={() => openForm(recipient.recipient)}>{t('Record another copy', 'एक और कॉपी दर्ज करें')}</button></div>
      <p className="copy-last-entry">{t('Last recorded handover', 'आखिरी दर्ज की गई कॉपी')}: {t('version', 'संस्करण')} {recipient.lastEntry.version} · {dateLabel(recipient.lastEntry.givenOn)}</p>
      {recipient.lastEntry.locationNote && <p className="copy-location-note">{recipient.lastEntry.locationNote}</p>}
      <details className="copy-history"><summary>{t('Handover history', 'कब-कब कॉपी दी')} ({recipient.history.length})</summary><ol>{recipient.history.map((entry) => <li key={entry.id}>
        <div className="copy-entry-heading"><strong>{t('Version', 'संस्करण')} {entry.version} · {dateLabel(entry.givenOn)}</strong><div><button type="button" className="copy-text-button" aria-label={`${t('Edit entry for', 'जानकारी बदलें')} ${entry.recipient}, ${entry.givenOn}, v${entry.version}`} onClick={() => openForm('', entry)}>{t('Edit', 'बदलें')}</button><button type="button" className="copy-text-button" aria-label={`${t('Remove entry for', 'जानकारी हटाएँ')} ${entry.recipient}, ${entry.givenOn}, v${entry.version}`} onClick={() => remove(entry)}>{t('Remove entry', 'जानकारी हटाएँ')}</button></div></div>
        {entry.locationNote && <p className="copy-location-note">{entry.locationNote}</p>}<p className="copy-entry-author">{t('Recorded by', 'दर्ज किया')} {entry.addedBy}{entry.updatedBy !== entry.addedBy && <> · {t('corrected by', 'सुधारा')} {entry.updatedBy}</>}</p>
      </li>)}</ol></details>
    </article>)}</div>
    {!recipients.length && !formOpen && <p className="copy-empty">{t('No handovers recorded yet.', 'अभी कॉपी देने की कोई जानकारी नहीं है।')}</p>}
    <div className="copy-tracker-status"><p role="status">{message}</p>{removed.length > 0 && <button type="button" className="copy-text-button" onClick={undo}>{t('Undo last removal', 'पिछला हटाया वापस लाएँ')}</button>}</div>
  </section>;
}
