'use client';

import { useEffect, useId, useRef, useState } from 'react';
import type { CareReport } from './care-calendar-state';
import { careReportCategory, type CareDocumentCategory, type CareDocumentText } from './care-document-search';
import './family-report-comparison.css';

function ReportLibraryFile({ report, hindi, onRemove, onEditText }: { report: CareReport; hindi: boolean; onRemove?: (id: string) => void; onEditText?: (id: string) => void }) {
  const open = useRef<HTMLAnchorElement>(null);
  const download = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (open.current) open.current.href = url;
    if (download.current) download.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <li className="report-library-file">
    <div><a ref={open} target="_blank" rel="noreferrer">{report.file.name}</a><span><time dateTime={report.date}>{report.date}</time> · {report.addedBy}</span></div>
    <div className="report-library-file-actions">
      {onEditText && <button type="button" onClick={() => onEditText(report.id)}>{hindi ? 'रिपोर्ट पढ़ें' : 'Read report'}</button>}
      <a ref={download} download={report.file.name}>{hindi ? 'डाउनलोड' : 'Download'}</a>
      {onRemove && <button type="button" aria-label={`${hindi ? 'हटाएँ' : 'Remove'} ${report.file.name}`} onClick={() => onRemove(report.id)}>{hindi ? 'हटाएँ' : 'Remove'}</button>}
    </div>
  </li>;
}

export function FamilyReportLibrary({ patientId, hindi, reports, documents = [], onRemove, onEditText }: {
  patientId: string; hindi: boolean; reports: CareReport[]; documents?: readonly CareDocumentText[];
  onRemove?: (reportId: string) => void; onEditText?: (reportId: string) => void;
}) {
  const id = useId();
  const [choice, setChoice] = useState<CareDocumentCategory | null>(null);
  const available = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const folders: Array<{ key: CareDocumentCategory; name: string; short: string; files: CareReport[] }> = [
    { key: 'cancer', name: hindi ? 'कैंसर के दस्तावेज़' : 'Cancer documents', short: 'PET · CT · Biopsy', files: [] },
    { key: 'monitoring', name: hindi ? 'निगरानी की जाँचें' : 'Monitoring tests', short: 'CBC · LFT · KFT', files: [] },
    { key: 'other', name: hindi ? 'अन्य दस्तावेज़' : 'Other documents', short: '', files: [] },
  ].map((folder) => ({ ...folder, key: folder.key as CareDocumentCategory, files: available.filter((report) => careReportCategory(report, documents) === folder.key) }));
  const selected = choice ?? folders.find((folder) => folder.files.length)?.key ?? 'cancer';
  const visible = folders.find((folder) => folder.key === selected)!;
  return <section className="care-report-library" aria-label={hindi ? 'रिपोर्ट के फ़ोल्डर' : 'Report folders'}>
    <div className="report-library-folders" role="group" aria-label={hindi ? 'फ़ोल्डर चुनें' : 'Choose a folder'}>{folders.map((folder) => <button key={folder.key} type="button" data-folder={folder.key} aria-pressed={selected === folder.key} aria-controls={`${id}-files`} onClick={() => setChoice(folder.key)}>
      <span className="report-library-folder-icon" aria-hidden="true"><svg viewBox="0 0 34 29" fill="none"><path d="M3 7a3 3 0 0 1 3-3h7l4 4h11a3 3 0 0 1 3 3v13a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7Z" fill="currentColor" opacity=".2" /><path d="M3 12h28M3 7a3 3 0 0 1 3-3h7l4 4h11a3 3 0 0 1 3 3v13a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg></span>
      <strong>{folder.name}</strong>{folder.short && <span className="report-library-folder-types">{folder.short}</span>}<span className="report-library-count">{folder.files.length}</span>
    </button>)}</div>
    <div id={`${id}-files`} className="report-library-content">
      {visible.files.length ? <ul>{visible.files.map((report) => <ReportLibraryFile key={report.id} report={report} hindi={hindi} onRemove={onRemove} onEditText={onEditText} />)}</ul> : <p>{hindi ? 'इस फ़ोल्डर में अभी कोई फ़ाइल नहीं है।' : 'No files in this folder yet.'}</p>}
    </div>
  </section>;
}

function OriginalReport({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const picture = useRef<HTMLImageElement>(null);
  const document = useRef<HTMLObjectElement>(null);
  const open = useRef<HTMLAnchorElement>(null);
  const download = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (picture.current) picture.current.src = url;
    if (document.current) document.current.data = url;
    if (open.current) open.current.href = url;
    if (download.current) download.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <div className="family-report-original">
    <div className="family-report-links"><a ref={open} target="_blank" rel="noreferrer">{hindi ? 'पूरा खोलें' : 'Open full size'} ↗</a><a ref={download} download={report.file.name}>{hindi ? 'डाउनलोड करें' : 'Download'}</a></div>
    {report.file.type === 'application/pdf'
      ? <object ref={document} type="application/pdf" aria-label={report.file.name}><p>{hindi ? 'PDF देखने के लिए पूरा खोलें।' : 'Open full size to view this PDF.'}</p></object>
      // Original user files use blob URLs; they must not pass through an image service.
      // eslint-disable-next-line @next/next/no-img-element
      : <img ref={picture} alt={report.file.name} />}
  </div>;
}

export function FamilyReportComparison({ patientId, reports, hindi }: { patientId: string; reports: CareReport[]; hindi: boolean }) {
  const id = useId();
  const [expanded, setExpanded] = useState(false);
  const [leftId, setLeftId] = useState('');
  const [rightId, setRightId] = useState('');
  const available = reports.filter((item) => item.patientId === patientId).sort((a, b) => a.date.localeCompare(b.date));
  if (available.length < 2) return null;
  const left = available.find((item) => item.id === leftId) ?? available[0];
  const right = available.find((item) => item.id === rightId && item.id !== left.id) ?? available.find((item) => item.id !== left.id)!;
  return <div className="family-report-comparison">
    <button type="button" className="care-text-button" aria-expanded={expanded} aria-controls={id} onClick={() => setExpanded(!expanded)}>{expanded ? (hindi ? 'तुलना बंद करें' : 'Close comparison') : (hindi ? 'दो रिपोर्ट साथ देखें' : 'Compare reports')}</button>
    {expanded && <section id={id} aria-label={hindi ? 'दो मूल रिपोर्ट' : 'Two original reports'}>
      <div className="family-report-pair">
        {[{ report: left, label: hindi ? 'पहली रिपोर्ट' : 'First report', change: setLeftId }, { report: right, label: hindi ? 'दूसरी रिपोर्ट' : 'Second report', change: setRightId }].map(({ report, label, change }) => <div key={label}>
          <label>{label}<select value={report.id} onChange={(event) => change(event.target.value)}>{available.map((item) => <option key={item.id} value={item.id} disabled={item.id === (report.id === left.id ? right.id : left.id)}>{item.date} · {item.file.name}</option>)}</select></label>
          <OriginalReport report={report} hindi={hindi} />
        </div>)}
      </div>
      <p className="care-calendar-note">{hindi ? 'ये आपकी मूल फ़ाइलें हैं। यहाँ जाँच के नतीजों का मतलब नहीं बताया जाता।' : 'Original files, as uploaded. Test results are not interpreted here.'}</p>
    </section>}
  </div>;
}
