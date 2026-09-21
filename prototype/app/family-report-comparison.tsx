'use client';

import { useEffect, useId, useRef, useState } from 'react';
import type { CareReport } from './care-calendar-state';

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
