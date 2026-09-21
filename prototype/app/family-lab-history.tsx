'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { CareReport } from './care-calendar-state';
import {
  LAB_REPORT_FLAGS, LAB_TEST_SUGGESTIONS, addLabResult, updateLabResult, removeLabResult,
  restoreLabResult, selectLabResults, labNumericSeries, labHistoryText, validateLabResult,
  type LabResultDraft, type LabResultEntry, type LabReportFlag, type LabNumericPoint,
} from './family-lab-state';
import './family-lab-history.css';

export type FamilyLabHistoryProps = {
  patientId: string;
  author: string;
  today: string;
  hindi: boolean;
  reports: CareReport[];
  entries: LabResultEntry[];
  onChange: (entries: LabResultEntry[]) => void;
};

const hindiTests: Record<string, string> = {
  Haemoglobin: 'हीमोग्लोबिन', 'RBC count': 'आरबीसी गिनती',
  'White cell count': 'सफ़ेद रक्त कोशिकाओं की गिनती', 'Platelet count': 'प्लेटलेट गिनती',
};
const hindiFlags: Record<LabReportFlag, string> = { 'Not stated': 'नहीं लिखा है', Low: 'कम', High: 'ज़्यादा', 'Within range': 'रेंज के अंदर' };

function blankDraft(today: string, testName = ''): LabResultDraft {
  return { testName, value: '', unit: '', date: today, printedRange: '', reportFlag: 'Not stated', reportId: null, sourceName: '' };
}

function LabReportLinks({ report, hindi, compact = false }: { report: CareReport; hindi: boolean; compact?: boolean }) {
  const open = useRef<HTMLAnchorElement>(null);
  const download = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (open.current) open.current.href = url;
    if (download.current) download.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <span className="lab-report-links">
    <a ref={open} target="_blank" rel="noreferrer">{compact ? (hindi ? 'मूल रिपोर्ट खोलें' : 'Open original') : report.file.name}</a>
    <a ref={download} download={report.file.name}>{hindi ? 'डाउनलोड' : 'Download'}</a>
  </span>;
}

function LabPlot({ points, testName, unit, hindi }: { points: LabNumericPoint[]; testName: string; unit: string; hindi: boolean }) {
  const labelId = useId();
  const dateLabel = (date: string) => new Date(`${date}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: '2-digit', timeZone: 'UTC' });
  const dates = points.map((point) => Date.parse(`${point.date}T12:00:00Z`));
  const values = points.map((point) => point.numericValue);
  const first = Math.min(...dates);
  const last = Math.max(...dates);
  const low = Math.min(...values);
  const high = Math.max(...values);
  const x = (date: number) => first === last ? 286 : 58 + ((date - first) / (last - first)) * 452;
  const y = (value: number) => low === high ? 83 : 140 - ((value - low) / (high - low)) * 114;
  const coordinates = points.map((point, index) => ({ point, x: x(dates[index]), y: y(point.numericValue) }));
  const canConnect = new Set(dates).size === dates.length;
  return <figure className="lab-result-plot" aria-labelledby={labelId}>
    <figcaption id={labelId}>{testName} <span>· {unit}</span></figcaption>
    <svg viewBox="0 0 540 184" role="img" aria-label={hindi ? `${points.length} नतीजे, तारीख के हिसाब से, ${unit}` : `${points.length} recorded values by date, ${unit}`}>
      <line x1="58" x2="510" y1="140" y2="140" className="lab-plot-axis" />
      {low !== high && <line x1="58" x2="510" y1="26" y2="26" className="lab-plot-guide" />}
      <text x="48" y={y(low) + 4} textAnchor="end">{low}</text>
      {low !== high && <text x="48" y={y(high) + 4} textAnchor="end">{high}</text>}
      {canConnect && <polyline points={coordinates.map((point) => `${point.x},${point.y}`).join(' ')} className="lab-plot-line" />}
      {coordinates.map(({ point, x: px, y: py }) => <circle key={point.id} cx={px} cy={py} r="5" className="lab-plot-point"><title>{dateLabel(point.date)} · {point.value} {unit}</title></circle>)}
      <text x={first === last ? 286 : 58} y="168" textAnchor={first === last ? 'middle' : 'start'}>{dateLabel(points[0].date)}</text>
      {first !== last && <text x="510" y="168" textAnchor="end">{dateLabel(points[points.length - 1].date)}</text>}
    </svg>
    <p>{hindi ? `सिर्फ़ ${unit} में लिखे संख्यात्मक नतीजे दिखाए हैं।` : `Only numeric results using ${unit} are plotted.`}</p>
  </figure>;
}

export function FamilyLabHistory(props: FamilyLabHistoryProps) {
  return <LabHistoryForPatient key={props.patientId} {...props} />;
}

function LabHistoryForPatient({ patientId, author, today, hindi, reports, entries, onChange }: FamilyLabHistoryProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const t = (en: string, hi: string) => hindi ? hi : en;
  const testLabel = (name: string) => hindi ? hindiTests[name] ?? name : name;
  const flagLabel = (flag: LabReportFlag) => hindi ? hindiFlags[flag] : flag;
  const dateLabel = (date: string) => new Date(`${date}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const patientEntries = selectLabResults(entries, patientId);
  const patientReports = reports.filter((report) => report.patientId === patientId);
  const tests = [...new Set(patientEntries.map((entry) => entry.testName))].sort((a, b) => a.localeCompare(b));
  const [selectedTest, setSelectedTest] = useState(() => patientEntries[0]?.testName ?? '');
  const [unitChoice, setUnitChoice] = useState('');
  const [formOpen, setFormOpen] = useState(patientEntries.length === 0);
  const [draft, setDraft] = useState<LabResultDraft>(() => blankDraft(today));
  const [sourceChoice, setSourceChoice] = useState(patientReports.length ? '' : 'manual');
  const [showPrinted, setShowPrinted] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [removed, setRemoved] = useState<LabResultEntry[]>([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const filter = tests.includes(selectedTest) ? selectedTest : '';
  const visible = selectLabResults(entries, patientId, filter);
  const units = [...new Set(visible.map((entry) => entry.unit))];
  const plotUnit = units.includes(unitChoice) ? unitChoice : units[0] ?? '';
  const points = filter ? labNumericSeries(entries, patientId, filter, plotUnit) : [];
  const linkedReport = patientReports.find((report) => report.id === draft.reportId);
  const lastRemoved = removed.at(-1);

  useEffect(() => {
    if (formOpen) inputRef.current?.focus();
  }, [formOpen, editingId]);

  function openNew() {
    setEditingId(null); setDraft(blankDraft(today, filter));
    setSourceChoice(patientReports.length ? '' : 'manual'); setShowPrinted(false);
    setError(''); setMessage(''); setFormOpen(true);
  }

  function edit(entry: LabResultEntry) {
    setEditingId(entry.id);
    setDraft({ testName: entry.testName, value: entry.value, unit: entry.unit, date: entry.date, printedRange: entry.printedRange, reportFlag: entry.reportFlag, reportId: entry.reportId, sourceName: entry.sourceName });
    setSourceChoice(entry.reportId || 'manual');
    setShowPrinted(Boolean(entry.printedRange) || entry.reportFlag !== 'Not stated');
    setError(''); setMessage(''); setFormOpen(true);
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateLabResult(draft, patientId, today, reports);
    const errors = {
      testName: t('Enter the test name, up to 80 characters.', 'जाँच का नाम 80 अक्षरों तक लिखें।'),
      value: t('Copy the result as printed, up to 80 characters.', 'रिपोर्ट का नतीजा जैसा लिखा है वैसा लिखें, 80 अक्षरों तक।'),
      unit: t('Copy the unit from the report.', 'रिपोर्ट में लिखी इकाई दर्ज करें।'),
      date: t('Choose a real date, today or earlier.', 'आज या उससे पहले की सही तारीख चुनें।'),
      printedRange: t('Keep the printed range within 160 characters.', 'रिपोर्ट की रेंज 160 अक्षरों तक लिखें।'),
      reportFlag: t('Choose the flag printed on the report.', 'रिपोर्ट में लिखा फ़्लैग चुनें।'),
      source: t('Choose an available report, or enter a report name.', 'मौजूद रिपोर्ट चुनें या रिपोर्ट का नाम लिखें।'),
    };
    if (!sourceChoice || invalid) { setError(errors[invalid ?? 'source']); return; }
    const next = editingId
      ? updateLabResult(entries, patientId, editingId, author, draft, today, reports)
      : addLabResult(entries, patientId, author, crypto.randomUUID(), draft, today, reports);
    if (next === entries) { setError(t('This result could not be saved. Try again.', 'नतीजा सेव नहीं हुआ। फिर से कोशिश करें।')); return; }
    onChange(next); setSelectedTest(draft.testName.trim()); setUnitChoice(draft.unit.trim());
    setFormOpen(false); setEditingId(null); setError('');
    setMessage(t(editingId ? 'Changes saved.' : 'Result saved.', editingId ? 'बदलाव सेव हो गए।' : 'नतीजा सेव हो गया।'));
  }

  function remove(entry: LabResultEntry) {
    onChange(removeLabResult(entries, patientId, entry.id));
    setRemoved((previous) => [...previous, entry]);
    if (editingId === entry.id) { setFormOpen(false); setEditingId(null); }
    setMessage('');
  }

  function undo() {
    if (!lastRemoved) return;
    const next = restoreLabResult(entries, patientId, lastRemoved);
    if (next === entries) { setMessage(t('This result could not be restored.', 'यह नतीजा वापस नहीं आया।')); return; }
    onChange(next); setRemoved((previous) => previous.slice(0, -1));
    setSelectedTest(lastRemoved.testName); setUnitChoice(lastRemoved.unit);
    setMessage(t('Result restored.', 'नतीजा वापस आ गया।'));
  }

  function download() {
    const url = URL.createObjectURL(new Blob([labHistoryText(entries, patientId, hindi)], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'saanthvana-lab-results.txt'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
    setMessage(t('Download started.', 'डाउनलोड शुरू हो गया।'));
  }

  return <section className="family-lab-history" aria-labelledby={`${id}-heading`}>
    <header className="lab-history-heading"><h1 id={`${id}-heading`}>{t('Lab results', 'जाँच के नतीजे')}</h1><div>
      <button type="button" className="secondary-button" disabled={!patientEntries.length} onClick={download}>{t('Download results', 'नतीजे डाउनलोड करें')}</button>
      {!formOpen && <button type="button" className="primary-button" onClick={openNew}>{t('+ Add result', '+ नतीजा जोड़ें')}</button>}
    </div></header>

    {formOpen && <form className="lab-result-form" onSubmit={save}>
      <div className="lab-result-form-heading"><h2>{t(editingId ? 'Edit result' : 'Copy a result from a report', editingId ? 'नतीजा बदलें' : 'रिपोर्ट से नतीजा लिखें')}</h2><button type="button" onClick={() => { setFormOpen(false); setEditingId(null); setError(''); }}>{t('Cancel', 'रद्द करें')}</button></div>
      <div className="lab-result-fields">
        <label className="lab-field-wide">{t('Test name', 'जाँच का नाम')}<input ref={inputRef} list={`${id}-tests`} value={draft.testName} maxLength={80} required onChange={(event) => setDraft({ ...draft, testName: event.target.value })} /><datalist id={`${id}-tests`}>{[...new Set([...LAB_TEST_SUGGESTIONS, ...tests])].map((name) => <option key={name} value={name}>{testLabel(name)}</option>)}</datalist></label>
        <label>{t('Result as printed', 'रिपोर्ट में लिखा नतीजा')}<input value={draft.value} maxLength={80} required onChange={(event) => setDraft({ ...draft, value: event.target.value })} /></label>
        <label>{t('Unit as printed', 'रिपोर्ट में लिखी इकाई')}<input value={draft.unit} maxLength={60} required onChange={(event) => setDraft({ ...draft, unit: event.target.value })} /></label>
        <label>{t('Report date', 'रिपोर्ट की तारीख')}<input type="date" value={draft.date} max={today} required onChange={(event) => setDraft({ ...draft, date: event.target.value })} /></label>
        <label>{t('Source report', 'किस रिपोर्ट से')}<select value={sourceChoice} required onChange={(event) => {
          const choice = event.target.value; const report = patientReports.find((item) => item.id === choice);
          setSourceChoice(choice); setDraft({ ...draft, reportId: report?.id ?? null, sourceName: report?.file.name ?? (choice === 'manual' ? draft.sourceName : '') });
        }}>
          <option value="">{t('Choose a report', 'रिपोर्ट चुनें')}</option>
          {draft.reportId && !linkedReport && <option value={draft.reportId} disabled>{draft.sourceName} — {t('file unavailable', 'फ़ाइल मौजूद नहीं है')}</option>}
          {patientReports.map((report) => <option key={report.id} value={report.id}>{report.file.name}</option>)}
          <option value="manual">{t('Use a report name', 'रिपोर्ट का नाम लिखें')}</option>
        </select></label>
        {sourceChoice === 'manual' && <label className="lab-field-wide">{t('Lab or report name', 'लैब या रिपोर्ट का नाम')}<input value={draft.sourceName} required maxLength={300} onChange={(event) => setDraft({ ...draft, sourceName: event.target.value })} /></label>}
        {linkedReport && <div className="lab-field-wide"><LabReportLinks report={linkedReport} hindi={hindi} compact /></div>}
      </div>
      <details className="lab-printed-options" open={showPrinted} onToggle={(event) => setShowPrinted(event.currentTarget.open)}><summary>{t('Printed range & flag (optional)', 'रिपोर्ट की रेंज और फ़्लैग (वैकल्पिक)')}</summary><div className="lab-result-fields">
        <label>{t('Range as printed', 'रिपोर्ट में लिखी रेंज')}<input value={draft.printedRange} maxLength={160} onChange={(event) => setDraft({ ...draft, printedRange: event.target.value })} /></label>
        <label>{t('Flag printed on report', 'रिपोर्ट में लिखा फ़्लैग')}<select value={draft.reportFlag} onChange={(event) => setDraft({ ...draft, reportFlag: event.target.value as LabReportFlag })}>{LAB_REPORT_FLAGS.map((flag) => <option key={flag} value={flag}>{flagLabel(flag)}</option>)}</select></label>
      </div></details>
      {error && <p className="lab-result-error" role="alert">{error}</p>}
      <button type="submit" className="primary-button">{t(editingId ? 'Save changes' : 'Save result', editingId ? 'बदलाव सेव करें' : 'नतीजा सेव करें')}</button>
    </form>}

    {tests.length > 0 && <nav className="lab-test-filters" aria-label={t('Choose a test', 'जाँच चुनें')}>
      <button type="button" aria-pressed={!filter} onClick={() => { setSelectedTest(''); setUnitChoice(''); }}>{t('All results', 'सभी नतीजे')}</button>
      {tests.map((name) => <button key={name} type="button" aria-pressed={filter === name} onClick={() => { setSelectedTest(name); setUnitChoice(''); }}>{testLabel(name)}</button>)}
    </nav>}
    {filter && units.length > 1 && <label className="lab-chart-unit">{t('Chart unit', 'चार्ट की इकाई')}<select value={plotUnit} onChange={(event) => setUnitChoice(event.target.value)}>{units.map((unit) => <option key={unit}>{unit}</option>)}</select></label>}
    {points.length > 1 && <LabPlot points={points} testName={testLabel(filter)} unit={plotUnit} hindi={hindi} />}

    {lastRemoved && <div className="lab-result-undo" role="status"><span>{t('Removed', 'हटा दिया')}: {testLabel(lastRemoved.testName)} · {lastRemoved.value} {lastRemoved.unit}</span><button type="button" onClick={undo}>{t('Undo', 'वापस लाएँ')}</button></div>}
    {message && <p className="lab-result-message" role="status">{message}</p>}
    {visible.length ? <ol className="lab-result-list">{visible.map((entry) => {
      const report = patientReports.find((item) => item.id === entry.reportId);
      return <li key={entry.id} className="lab-result-card">
        <div className="lab-result-card-heading"><h2>{testLabel(entry.testName)}</h2><time dateTime={entry.date}>{dateLabel(entry.date)}</time></div>
        <p className="lab-result-value"><strong>{entry.value}</strong> <span>{entry.unit}</span></p>
        <dl className="lab-result-reference"><div><dt>{t('Range as printed', 'रिपोर्ट में लिखी रेंज')}</dt><dd>{entry.printedRange || t('Not entered', 'दर्ज नहीं है')}</dd></div><div><dt>{t('Flag as printed', 'रिपोर्ट में लिखा फ़्लैग')}</dt><dd>{flagLabel(entry.reportFlag)}</dd></div></dl>
        <div className="lab-result-source"><span>{t('Source', 'रिपोर्ट')}</span>{report ? <LabReportLinks report={report} hindi={hindi} /> : <p>{entry.sourceName}{entry.reportId && <span>{t('File no longer here', 'फ़ाइल अब यहाँ नहीं है')}</span>}</p>}</div>
        <div className="lab-result-card-footer"><span>{t('Copied by', 'लिखने वाले')} {entry.recordedBy}{entry.updatedBy !== entry.recordedBy ? ` · ${t('edited by', 'बदला')} ${entry.updatedBy}` : ''}</span><div><button type="button" onClick={() => edit(entry)}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => remove(entry)}>{t('Remove', 'हटाएँ')}</button></div></div>
      </li>;
    })}</ol> : !formOpen && <div className="lab-history-empty"><p>{t('No results added yet.', 'अभी कोई नतीजा नहीं जोड़ा है।')}</p><button type="button" className="primary-button" onClick={openNew}>{t('Add a result', 'नतीजा जोड़ें')}</button></div>}
  </section>;
}
