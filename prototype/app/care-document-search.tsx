'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { CareReport } from './care-calendar-state';
import { CarePdfReadError, extractCareDocumentPdf, type CarePdfText } from './care-document-extract';
import './care-document-search.css';

export type CareDocumentCategory = 'cancer' | 'monitoring' | 'other';
export type CareDocumentText = {
  reportId: string;
  patientId: string;
  category: CareDocumentCategory;
  text: string;
  recordedBy: string;
  textSource?: 'copied' | 'pdf-text';
  pagesRead?: number;
  pageCount?: number;
  pagesWithoutText?: number[];
};
export type CareDocumentSearchProps = {
  patientId: string;
  author: string;
  hindi: boolean;
  reports: CareReport[];
  documents: CareDocumentText[];
  onChange: (documents: CareDocumentText[]) => void;
  editReportId?: string | null;
  onEditClose?: () => void;
};
type DocumentMatch = { report: CareReport; recordedBy: string; excerpts: Array<{ line: number; text: string; page?: number }> };

export function careReportCategory(report: CareReport, documents: readonly CareDocumentText[] = []): CareDocumentCategory {
  const saved = documents.find((document) => document.patientId === report.patientId && document.reportId === report.id);
  if (saved) return saved.category;
  const name = report.file.name.toLocaleLowerCase().replace(/[_-]/g, ' ');
  if (/\b(pet|ct|mri|biopsy|histopathology|histology|chemotherapy|radiotherapy|treatment|discharge|cancer)\b/.test(name)) return 'cancer';
  if (/\b(cbc|lft|kft|rft|blood|haemogram|hemogram|haemoglobin|hemoglobin|creatinine|bilirubin|platelet|monitoring)\b/.test(name)) return 'monitoring';
  return 'other';
}

const ignoredWords = new Set(['a', 'an', 'the', 'my', 'our', 'patient', 'was', 'were', 'is', 'are', 'has', 'have', 'had', 'did', 'do', 'does', 'when', 'what', 'where', 'which', 'how', 'last', 'latest', 'date', 'of', 'on', 'in', 'at', 'to', 'for', 'from', 'and', 'or', 'tell', 'me', 'show', 'find', 'about', 'please', 'get', 'got', 'given', 'receive', 'received', 'happen', 'happened', 'report', 'reports', 'document', 'documents', 'कब', 'क्या', 'है', 'था', 'की', 'का', 'के', 'में', 'मुझे', 'आखिरी', 'तारीख', 'बताएँ']);
const relatedTerms = [
  ['chemotherapy', 'chemo', 'कीमोथेरेपी', 'कीमो'],
  ['radiotherapy', 'radiation', 'रेडियोथेरेपी', 'रेडिएशन'],
  ['biopsy', 'histopathology', 'histology', 'बायोप्सी'],
  ['diagnosis', 'diagnosed', 'diagnostic', 'निदान'],
];

export function findCareDocumentText(query: string, patientId: string, reports: CareReport[], documents: readonly CareDocumentText[]): DocumentMatch[] {
  const terms = [...new Set((query.toLocaleLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) ?? []).filter((term) => !ignoredWords.has(term)))];
  if (!terms.length) return [];
  const needles = terms.map((term) => relatedTerms.find((group) => group.includes(term)) ?? [term]);
  const matches: DocumentMatch[] = [];
  for (const report of reports.filter((item) => item.patientId === patientId)) {
    const document = documents.find((item) => item.patientId === patientId && item.reportId === report.id);
    if (!document?.text.trim()) continue;
    const lines = document.text.split(/\r?\n/);
    let sourcePage: number | undefined;
    const linePages = lines.map((line) => {
      const marker = /^\[Page (\d+)\]$/.exec(line.trim());
      if (document.textSource === 'pdf-text' && marker) sourcePage = Number(marker[1]);
      return sourcePage;
    });
    const matchingLines = lines.map((text, index) => ({ text, index })).filter(({ text }) => {
      const words: string[] = text.toLocaleLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) ?? [];
      return needles.every((group) => group.some((term) => words.includes(term)));
    });
    if (!matchingLines.length) continue;
    const shown = new Set<number>();
    const excerpts: DocumentMatch['excerpts'] = [];
    for (const match of matchingLines) {
      const first = match.index > 0 && linePages[match.index - 1] === linePages[match.index] && !/^\[Page \d+\]$/.test(lines[match.index - 1].trim()) ? match.index - 1 : match.index;
      const last = match.index + 1 < lines.length && linePages[match.index + 1] === linePages[match.index] ? match.index + 1 : match.index;
      if (shown.has(match.index)) continue;
      for (let index = first; index <= last; index += 1) shown.add(index);
      excerpts.push({ line: first + 1, text: lines.slice(first, last + 1).join('\n'), page: linePages[match.index] });
    }
    matches.push({ report, recordedBy: document.recordedBy, excerpts });
  }
  return matches.sort((a, b) => b.report.date.localeCompare(a.report.date) || a.report.file.name.localeCompare(b.report.file.name));
}

function OriginalLink({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <a ref={link} target="_blank" rel="noreferrer">{hindi ? 'मूल फ़ाइल खोलें' : 'Open original'}</a>;
}

export function CareDocumentSearch(props: CareDocumentSearchProps) {
  return <DocumentSearchForPatient key={props.patientId} {...props} />;
}

function DocumentSearchForPatient({ patientId, author, hindi, reports, documents, onChange, editReportId, onEditClose }: CareDocumentSearchProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const available = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const availableText = documents.filter((document) => document.patientId === patientId && document.text.trim() && available.some((report) => report.id === document.reportId));
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [editorId, setEditorId] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [category, setCategory] = useState<CareDocumentCategory>('other');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [reading, setReading] = useState(false);
  const [progress, setProgress] = useState({ page: 0, total: 0 });
  const [pdfText, setPdfText] = useState<Omit<CarePdfText, 'text'> | null>(null);
  const [readMessage, setReadMessage] = useState('');
  const reader = useRef<AbortController | null>(null);
  const matches = findCareDocumentText(submitted, patientId, reports, documents);
  const editing = available.find((report) => report.id === editorId);
  const isPdf = editing && (editing.file.type === 'application/pdf' || /\.pdf$/i.test(editing.file.name));

  useEffect(() => () => { reader.current?.abort(); reader.current = null; }, []);

  useEffect(() => {
    if (!editReportId) return;
    const report = reports.find((item) => item.patientId === patientId && item.id === editReportId);
    if (!report) return;
    const document = documents.find((item) => item.patientId === patientId && item.reportId === report.id);
    reader.current?.abort(); reader.current = null; setReading(false); setReadMessage('');
    setPdfText(document?.textSource === 'pdf-text' ? { pagesRead: document.pagesRead ?? 0, pageCount: document.pageCount ?? 0, pagesWithoutText: document.pagesWithoutText ?? [] } : null);
    setEditorId(report.id); setText(document?.text ?? ''); setCategory(careReportCategory(report, documents)); setError(''); setMessage('');
    // A new edit request opens the latest stored text without overwriting typing on unrelated updates.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editReportId, patientId]);

  function openText(report: CareReport) {
    const document = documents.find((item) => item.patientId === patientId && item.reportId === report.id);
    reader.current?.abort(); reader.current = null; setReading(false); setReadMessage('');
    setPdfText(document?.textSource === 'pdf-text' ? { pagesRead: document.pagesRead ?? 0, pageCount: document.pageCount ?? 0, pagesWithoutText: document.pagesWithoutText ?? [] } : null);
    setEditorId(report.id); setText(document?.text ?? ''); setCategory(careReportCategory(report, documents)); setError(''); setMessage('');
  }
  function closeEditor() { reader.current?.abort(); reader.current = null; setReading(false); setEditorId(null); setText(''); setPdfText(null); setReadMessage(''); setError(''); onEditClose?.(); }
  async function readReport() {
    if (!editing || reader.current) return;
    const controller = new AbortController(); reader.current = controller;
    setReading(true); setProgress({ page: 0, total: 0 }); setError(''); setReadMessage('');
    try {
      const result = await extractCareDocumentPdf(editing.file, { signal: controller.signal, onProgress: (page, total) => {
        if (!controller.signal.aborted) setProgress({ page, total });
      } });
      if (controller.signal.aborted) return;
      if (!result.text.trim()) {
        setReadMessage(t('No readable text found. Add the report text below.', 'पढ़ने लायक टेक्स्ट नहीं मिला। नीचे रिपोर्ट का टेक्स्ट जोड़ें।'));
        return;
      }
      setText(result.text); setPdfText({ pagesRead: result.pagesRead, pageCount: result.pageCount, pagesWithoutText: result.pagesWithoutText });
      setReadMessage(t('Check the text against the original, then save.', 'मूल रिपोर्ट से टेक्स्ट मिलाएँ, फिर सेव करें।'));
    } catch (caught) {
      if (controller.signal.aborted) return;
      const code = caught instanceof CarePdfReadError ? caught.code : 'unreadable';
      setError(code === 'password' ? t('This PDF is locked. Add the report text below.', 'यह PDF लॉक है। नीचे रिपोर्ट का टेक्स्ट जोड़ें।')
        : code === 'too-much-text' ? t('This PDF has too much text. Copy the relevant report text below.', 'इस PDF में बहुत ज़्यादा टेक्स्ट है। ज़रूरी टेक्स्ट नीचे जोड़ें।')
          : t('Could not read this file. Add the report text below.', 'यह फ़ाइल नहीं पढ़ी जा सकी। नीचे रिपोर्ट का टेक्स्ट जोड़ें।'));
    } finally {
      if (reader.current === controller) { reader.current = null; setReading(false); }
    }
  }
  function saveText(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !author.trim() || reading) return;
    if (text.length > 200000) { setError(t('Keep each document under 200,000 characters.', 'हर दस्तावेज़ का टेक्स्ट 2,00,000 अक्षरों तक रखें।')); return; }
    const next: CareDocumentText = { reportId: editing.id, patientId, category, text, recordedBy: author.trim(), textSource: pdfText ? 'pdf-text' : 'copied', ...(pdfText ?? {}) };
    onChange([...documents.filter((document) => document.patientId !== patientId || document.reportId !== editing.id), next]);
    closeEditor(); setMessage(t('Document updated.', 'दस्तावेज़ अपडेट हो गया।'));
  }
  function search(value: string) { setQuery(value); setSubmitted(value.trim()); setMessage(''); }
  function download() {
    const content = [t('SAANTHVANA — DOCUMENT EXCERPTS', 'सांत्वना — दस्तावेज़ के अंश'), `${t('Search', 'खोज')}: ${submitted}`,
      ...matches.map(({ report, recordedBy, excerpts }) => [report.file.name, `${t('Document date', 'दस्तावेज़ की तारीख')}: ${report.date}`, `${t('Text saved by', 'टेक्स्ट सेव करने वाले')}: ${recordedBy}`, ...excerpts.map((excerpt) => `${excerpt.page ? `${t('Page', 'पृष्ठ')} ${excerpt.page} · ` : ''}${t('Line', 'पंक्ति')} ${excerpt.line}\n${excerpt.text}`)].join('\n')),
    ].join('\n\n');
    const url = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
    const link = window.document.createElement('a'); link.href = url; link.download = 'saanthvana-document-excerpts.txt'; link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return <section className="care-document-search" aria-labelledby={`${id}-heading`}>
    <header className="care-document-heading"><h2 id={`${id}-heading`}>{t('Find in my documents', 'मेरे दस्तावेज़ों में खोजें')}</h2><span>{availableText.length}/{available.length} {t('with text', 'में टेक्स्ट है')}</span></header>
    <form className="care-document-search-form" onSubmit={(event) => { event.preventDefault(); search(query); }}>
      <label className="care-document-search-label" htmlFor={`${id}-query`}>{t('Find a word or treatment', 'शब्द या इलाज खोजें')}</label>
      <input id={`${id}-query`} type="search" value={query} maxLength={160} onChange={(event) => setQuery(event.target.value)} placeholder={t('e.g. chemotherapy', 'जैसे कीमोथेरेपी')} />
      <button type="submit" disabled={!query.trim()}>{t('Find', 'खोजें')}</button>
    </form>
    <div className="care-document-topics">{[['Chemotherapy', 'कीमोथेरेपी'], ['Radiotherapy', 'रेडियोथेरेपी'], ['Biopsy', 'बायोप्सी'], ['Diagnosis', 'निदान']].map(([en, hi]) => <button key={en} type="button" aria-pressed={submitted === en} onClick={() => search(en)}>{t(en, hi)}</button>)}</div>
    {submitted && <div className="care-document-results" aria-live="polite">
      {matches.length > 0 ? <><div className="care-document-results-heading"><h3>{t('From your documents', 'आपके दस्तावेज़ों से')}</h3><button type="button" onClick={download}>{t('Download excerpts', 'अंश डाउनलोड करें')}</button></div>
        {matches.map(({ report, recordedBy, excerpts }) => <article className="care-document-match" key={report.id}>
          <header><h4>{report.file.name}</h4><time dateTime={report.date}>{t('Document', 'दस्तावेज़')} · {report.date}</time></header>
          {excerpts.map((excerpt) => <blockquote key={excerpt.line}><p>{excerpt.text}</p><cite>{excerpt.page ? `${t('Page', 'पृष्ठ')} ${excerpt.page} · ` : ''}{t('Text line', 'टेक्स्ट की पंक्ति')} {excerpt.line}</cite></blockquote>)}
          <footer><span>{t('Text saved by', 'टेक्स्ट सेव करने वाले')} {recordedBy}</span><OriginalLink report={report} hindi={hindi} /></footer>
        </article>)}</> : <p className="care-document-not-found">{t('Not found in your documents.', 'आपके दस्तावेज़ों में नहीं मिला।')}</p>}
      {availableText.length < available.length && <p className="care-document-limit">{t('Files without saved text are not searched.', 'जिन फ़ाइलों का टेक्स्ट सेव नहीं है, उनमें खोज नहीं होती।')}</p>}
      {availableText.some((document) => (document.pageCount ?? 0) > (document.pagesRead ?? 0) || Boolean(document.pagesWithoutText?.length)) && <p className="care-document-limit">{t('Some pages have no saved text. Open the originals for the full document.', 'कुछ पृष्ठों का टेक्स्ट सेव नहीं है। पूरा दस्तावेज़ देखने के लिए मूल फ़ाइल खोलें।')}</p>}
    </div>}
    {message && <p className="care-document-message" role="status">{message}</p>}
    {editing ? <form className="care-document-editor" onSubmit={saveText}>
      <header><h3>{editing.file.name}</h3><button type="button" onClick={closeEditor}>{t('Cancel', 'रद्द करें')}</button></header>
      <div className="care-document-read-actions"><OriginalLink report={editing} hindi={hindi} />{isPdf && <button type="button" disabled={reading} onClick={readReport}>{reading ? t('Reading…', 'पढ़ रहे हैं…') : t('Read PDF', 'PDF पढ़ें')}</button>}</div>
      {reading && <div className="care-document-reading" role="status"><progress max={progress.total || 12} value={progress.page} /><span>{progress.total ? t(`Reading page ${Math.min(progress.page + 1, progress.total)} of ${progress.total}`, `${progress.total} में से पृष्ठ ${Math.min(progress.page + 1, progress.total)} पढ़ रहे हैं`) : t('Opening PDF…', 'PDF खोल रहे हैं…')}</span><button type="button" onClick={() => { reader.current?.abort(); reader.current = null; setReading(false); }}>{t('Stop', 'रोकें')}</button></div>}
      {readMessage && <p className="care-document-read-message" role="status">{readMessage}</p>}
      {pdfText && <p className="care-document-read-scope">{t(`Pages read: ${pdfText.pagesRead} of ${pdfText.pageCount}.`, `पढ़े गए पृष्ठ: ${pdfText.pageCount} में से ${pdfText.pagesRead}।`)}{pdfText.pagesWithoutText.length > 0 ? ` ${t('No readable text on pages', 'इन पृष्ठों पर पढ़ने लायक टेक्स्ट नहीं मिला')}: ${pdfText.pagesWithoutText.join(', ')}.` : ''}</p>}
      <label>{t('Keep under', 'कहाँ रखें')}<select value={category} onChange={(event) => setCategory(event.target.value as CareDocumentCategory)}><option value="cancer">{t('Cancer documents', 'कैंसर के दस्तावेज़')}</option><option value="monitoring">{t('Monitoring · CBC, LFT, KFT', 'निगरानी · CBC, LFT, KFT')}</option><option value="other">{t('Other documents', 'अन्य दस्तावेज़')}</option></select></label>
      <label>{t('Check the report text', 'रिपोर्ट का टेक्स्ट जाँचें')}<textarea value={text} disabled={reading} onChange={(event) => setText(event.target.value)} maxLength={200000} rows={8} /></label>
      <p>{isPdf ? t('Reads typed text from up to 12 pages. Scanned pages need their text added here.', 'अधिकतम 12 पृष्ठों का टाइप किया टेक्स्ट पढ़ता है। स्कैन पृष्ठों का टेक्स्ट यहाँ जोड़ें।') : t('Add the report text. Photos cannot be read here yet.', 'रिपोर्ट का टेक्स्ट जोड़ें। यहाँ फ़ोटो अभी नहीं पढ़ी जाती हैं।')}</p>
      {error && <p className="care-document-error" role="alert">{error}</p>}
      <button type="submit" className="care-document-save" disabled={reading}>{text.trim() ? t('Save checked text', 'जाँचा टेक्स्ट सेव करें') : t('Save folder', 'फ़ोल्डर सेव करें')}</button>
    </form> : available.length > 0 && <details className="care-document-text-list"><summary>{t('Document text & folders', 'दस्तावेज़ का टेक्स्ट और फ़ोल्डर')}</summary><ul>{available.map((report) => <li key={report.id}><span>{report.file.name}</span><button type="button" onClick={() => openText(report)}>{documents.some((document) => document.patientId === patientId && document.reportId === report.id && document.text.trim()) ? t('Edit text', 'टेक्स्ट बदलें') : t('Read report', 'रिपोर्ट पढ़ें')}</button></li>)}</ul></details>}
    {!available.length && <p className="care-document-not-found">{t('Add a report to get started.', 'शुरू करने के लिए रिपोर्ट जोड़ें।')}</p>}
  </section>;
}
