'use client';

import { useId, useState } from 'react';
import type { SummaryRelease } from './summary-state';
import {
  FAMILY_NOTE_FIELDS, compareFamilyNoteVersions, exportFamilyNoteComparison, exportFamilyNoteVersion,
  familyNoteHasText, selectFamilyNoteVersions,
} from './family-note-history-state';

type FamilyNoteHistoryProps = { patientId: string; patientName: string; hindi: boolean; releases: readonly SummaryRelease[] };

export function FamilyNoteHistory(props: FamilyNoteHistoryProps) {
  return <NoteHistoryContent key={props.patientId} {...props} />;
}

function NoteHistoryContent({ patientId, patientName, hindi, releases }: FamilyNoteHistoryProps) {
  const id = useId();
  const [selectedNumber, setSelectedNumber] = useState<number | null>(null);
  const [compareNumber, setCompareNumber] = useState<number | null>(null);
  const [showComparison, setShowComparison] = useState(false);
  const [message, setMessage] = useState('');
  const versions = selectFamilyNoteVersions(releases, patientId);
  const selected = versions.find((version) => version.number === selectedNumber) ?? versions[0];
  const others = versions.filter((version) => version.number !== selected?.number);
  const compareWith = others.find((version) => version.number === compareNumber) ?? others[0];
  const comparison = selected && compareWith ? compareFamilyNoteVersions(releases, patientId, selected.number, compareWith.number) : null;
  const t = (english: string, translated: string) => hindi ? translated : english;
  const dateLabel = (value: string) => new Date(value).toLocaleString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

  function download(compare = false) {
    if (!selected) return;
    const text = compare && compareWith ? exportFamilyNoteComparison(releases, patientId, patientName, selected.number, compareWith.number)
      : exportFamilyNoteVersion(releases, patientId, patientName, selected.number);
    if (text === null) return;
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    const safeId = patientId.replace(/[^a-zA-Z0-9_-]/g, '-');
    link.download = compare && comparison ? `saanthvana-${safeId}-versions-${comparison.earlier.number}-${comparison.later.number}.txt` : `saanthvana-${safeId}-care-note-v${selected.number}.txt`;
    link.click();
    URL.revokeObjectURL(url);
    setMessage(t(compare ? 'Comparison downloaded.' : 'Selected note downloaded.', compare ? 'तुलना डाउनलोड हो गई।' : 'चुना हुआ नोट डाउनलोड हो गया।'));
  }

  function storedText(text: string | null) {
    return text === null ? <p className="family-note-no-text">{t('Text was not saved for this version.', 'इस संस्करण का टेक्स्ट सेव नहीं किया गया था।')}</p>
      : text === '' ? <p className="family-note-no-text">{t('Empty text', 'खाली टेक्स्ट')}</p> : <p>{text}</p>;
  }

  function metadata(release: SummaryRelease) {
    return <div className="family-note-version-meta"><strong>{t('Version', 'संस्करण')} {release.number}</strong><span>{release.physician}</span><time dateTime={release.releasedAt}>{dateLabel(release.releasedAt)}</time></div>;
  }

  return <section className="family-note-history" aria-labelledby={`${id}-heading`}>
    <details>
      <summary id={`${id}-heading`}>{t('Care note history', 'डॉक्टर के पुराने नोट')} <span>({versions.length})</span></summary>
      {selected ? <>
        <div className="family-note-version-controls">
          <label htmlFor={`${id}-version`}>{t('Open version', 'संस्करण खोलें')}<select id={`${id}-version`} value={selected.number} onChange={(event) => { setSelectedNumber(Number(event.target.value)); setMessage(''); }}>{versions.map((version, index) => <option key={version.number} value={version.number}>{t('Version', 'संस्करण')} {version.number}{index === 0 ? t(' · latest', ' · सबसे नया') : ''}</option>)}</select></label>
          {selected.number !== versions[0].number && <button type="button" className="secondary-button" onClick={() => { setSelectedNumber(null); setMessage(''); }}>{t('Latest note', 'सबसे नया नोट')}</button>}
          <button type="button" className="secondary-button" disabled={!familyNoteHasText(selected)} onClick={() => download()}>{t('Save this version', 'इसकी कॉपी डाउनलोड करें')}</button>
        </div>
        <article className="family-note-selected" aria-label={`${t('Care note version', 'डॉक्टर का नोट संस्करण')} ${selected.number}`}>
          {metadata(selected)}
          {selected.fields ? <dl>{FAMILY_NOTE_FIELDS.map(({ key, label, hindi: translated }) => <div key={key}><dt>{hindi ? translated : label}</dt><dd>{storedText(selected.fields![key])}</dd></div>)}</dl> : storedText(selected.source)}
        </article>
        {compareWith ? <>
          <div className="family-note-compare-controls">
            <label htmlFor={`${id}-compare`}>{t('Compare with', 'इससे तुलना करें')}<select id={`${id}-compare`} value={compareWith.number} onChange={(event) => { setCompareNumber(Number(event.target.value)); setMessage(''); }}>{others.map((version) => <option key={version.number} value={version.number}>{t('Version', 'संस्करण')} {version.number}</option>)}</select></label>
            <button type="button" className="primary-button" aria-expanded={showComparison} aria-controls={`${id}-comparison`} onClick={() => setShowComparison((show) => !show)}>{showComparison ? t('Close comparison', 'तुलना बंद करें') : t('Compare the notes', 'नोट की तुलना करें')}</button>
          </div>
          {showComparison && comparison && <div id={`${id}-comparison`} className="family-note-comparison">
            <div className="family-note-comparison-heading"><h3>{t('What changed?', 'क्या बदला?')}</h3><button type="button" className="secondary-button" onClick={() => download(true)}>{t('Save comparison', 'तुलना डाउनलोड करें')}</button></div>
            <p className="family-note-comparison-rule">{t('Changed means the saved words differ. The notes do not tell us why unless the doctor wrote a reason.', 'बदलाव का मतलब सेव किए गए शब्द अलग हैं। वजह तभी पता चलेगी जब डॉक्टर ने लिखी हो।')}</p>
            <div className="family-note-comparison-meta">{metadata(comparison.earlier)}{metadata(comparison.later)}</div>
            {comparison.rows.map((row) => <section className="family-note-comparison-row" key={row.key}>
              <div className="family-note-comparison-label"><h4>{hindi ? row.hindi : row.label}</h4><span>{row.change === 'changed' ? t('Changed', 'बदला है') : row.change === 'unchanged' ? t('Unchanged', 'वही है') : t('Cannot compare', 'तुलना नहीं हो सकती')}</span></div>
              <div className="family-note-comparison-values"><div><span>{t('Version', 'संस्करण')} {comparison.earlier.number}</span>{storedText(row.earlier)}</div><div><span>{t('Version', 'संस्करण')} {comparison.later.number}</span>{storedText(row.later)}</div></div>
            </section>)}
          </div>}
        </> : <p className="family-note-single">{t('This is the only approved version saved here.', 'यहाँ अभी यही एक मंज़ूर किया हुआ संस्करण सेव है।')}</p>}
      </> : <p className="family-note-single">{t('No approved versions have been saved here yet.', 'यहाँ अभी कोई मंज़ूर किया हुआ संस्करण सेव नहीं है।')}</p>}
      <p className="family-note-history-message" role="status">{message}</p>
    </details>
  </section>;
}
