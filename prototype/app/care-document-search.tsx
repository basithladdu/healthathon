'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import type { CareReport } from './care-calendar-state';
import { CarePdfReadError, extractCareDocumentPdf, type CarePdfText } from './care-document-extract';
import { careDocumentFileError, careReportCategory, findCareDocumentText, removeCareDocument, restoreCareDocument, type RemovedCareDocument, type CareDocumentCategory, type CareDocumentText } from './care-document-state';
import './care-document-search.css';

export { careReportCategory, findCareDocumentText } from './care-document-state';
export type { CareDocumentCategory, CareDocumentText } from './care-document-state';

export type CareDocumentSearchProps = {
  patientId: string; author: string; hindi: boolean;
  reports: CareReport[]; documents: CareDocumentText[];
  onChange: (documents: CareDocumentText[]) => void;
  onReportsChange?: (reports: CareReport[]) => void;
  editReportId?: string | null; onEditClose?: () => void;
};

function DocumentIcon({ kind = 'file' }: { kind?: 'file' | 'search' | 'upload' | 'check' | 'remove' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'search' ? <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>
      : kind === 'upload' ? <><path d="M12 16V3m-5 5 5-5 5 5M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5" /></>
        : kind === 'remove' ? <><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7" /></>
          : kind === 'check' ? <path d="m5 12 4 4L19 6" />
          : <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>}
  </svg>;
}

function OriginalLink({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <a ref={link} target="_blank" rel="noreferrer">{hindi ? 'मूल फ़ाइल खोलें' : 'View original'} <span aria-hidden="true">↗</span></a>;
}

function DocumentPreview({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const picture = useRef<HTMLImageElement>(null);
  const pdf = useRef<HTMLObjectElement>(null);
  const isImage = report.file.type.startsWith('image/') || /\.(png|jpe?g|webp)$/i.test(report.file.name);
  const isPdf = report.file.type === 'application/pdf' || /\.pdf$/i.test(report.file.name);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (picture.current) picture.current.src = url;
    if (pdf.current) pdf.current.data = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <div className="care-document-preview">
    {isImage
      // User images must stay in the browser; do not proxy them through an image service.
      // eslint-disable-next-line @next/next/no-img-element
      ? <img ref={picture} alt={report.file.name} />
      : isPdf ? <object ref={pdf} type="application/pdf" aria-label={report.file.name}><div className="care-document-preview-fallback"><DocumentIcon /><OriginalLink report={report} hindi={hindi} /></div></object>
        : <div className="care-document-preview-fallback"><DocumentIcon /><strong>{report.file.name}</strong><OriginalLink report={report} hindi={hindi} /></div>}
  </div>;
}

function localDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
function displayDate(date: string, hindi: boolean) {
  const parsed = new Date(`${date}T12:00:00`);
  return Number.isNaN(parsed.getTime()) ? date : parsed.toLocaleDateString(hindi ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function CareDocumentSearch(props: CareDocumentSearchProps) {
  return <DocumentSearchForPatient key={props.patientId} {...props} />;
}

function DocumentSearchForPatient({ patientId, author, hindi, reports, documents, onChange, onReportsChange, editReportId, onEditClose }: CareDocumentSearchProps) {
  const router = useRouter();
  const uploadRequested = useRef(false);
  const returnToMedicines = useRef(false);
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const available = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const availableText = documents.filter((document) => document.patientId === patientId && document.text.trim() && available.some((report) => report.id === document.reportId));
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [folder, setFolder] = useState<CareDocumentCategory | 'all'>('all');
  const [editorId, setEditorId] = useState<string | null>(null);
  const [upload, setUpload] = useState<CareReport | null>(null);
  const [queue, setQueue] = useState<CareReport[]>([]);
  const [text, setText] = useState('');
  const [category, setCategory] = useState<CareDocumentCategory>('other');
  const [reportDate, setReportDate] = useState('');
  const [message, setMessage] = useState('');
  const [removed, setRemoved] = useState<RemovedCareDocument[]>([]);
  const [error, setError] = useState('');
  const [reading, setReading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState({ page: 0, total: 0 });
  const [pdfText, setPdfText] = useState<Omit<CarePdfText, 'text'> | null>(null);
  const [textSource, setTextSource] = useState<CareDocumentText['textSource']>('copied');
  const [readMessage, setReadMessage] = useState('');
  const reader = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const searchInput = useRef<HTMLInputElement>(null);
  const reviewHeading = useRef<HTMLHeadingElement>(null);
  const editing = upload ?? available.find((report) => report.id === editorId);
  const activeReportId = editing?.id;
  const isPdf = editing && (editing.file.type === 'application/pdf' || /\.pdf$/i.test(editing.file.name));
  const filtered = folder === 'all' ? available : available.filter((report) => careReportCategory(report, documents) === folder);
  const matches = findCareDocumentText(submitted, patientId, filtered, documents);
  const folders: Array<{ key: CareDocumentCategory | 'all'; name: string }> = [
    { key: 'all', name: t('All files', 'सभी फ़ाइलें') }, { key: 'cancer', name: t('Cancer care', 'कैंसर की देखभाल') },
    { key: 'monitoring', name: t('Blood & lab tests', 'खून और लैब जाँच') }, { key: 'other', name: t('Other', 'अन्य') },
  ];
  const categoryLabel = (value: CareDocumentCategory) => folders.find((item) => item.key === value)!.name;

  useEffect(() => () => { reader.current?.abort(); reader.current = null; }, []);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    returnToMedicines.current = params.get('next') === 'medicines';
    if (params.get('upload') === '1' && !uploadRequested.current) {
      uploadRequested.current = true;
      input.current?.focus();
      input.current?.click();
    }
  }, []);
  useEffect(() => {
    if (!editReportId) return;
    const report = reports.find((item) => item.patientId === patientId && item.id === editReportId);
    if (report) openText(report);
    // A new edit request must not overwrite typing when another stored record changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editReportId, patientId]);
  useEffect(() => { if (activeReportId) reviewHeading.current?.focus(); }, [activeReportId]);

  function cancelRead() { reader.current?.abort(); reader.current = null; setReading(false); }
  function resetEditor(report: CareReport) {
    const document = documents.find((item) => item.patientId === patientId && item.reportId === report.id);
    cancelRead(); setReadMessage(''); setError(''); setMessage('');
    setPdfText(document?.textSource === 'pdf-text' ? { pagesRead: document.pagesRead ?? 0, pageCount: document.pageCount ?? 0, pagesWithoutText: document.pagesWithoutText ?? [] } : null);
    setText(document?.text ?? ''); setCategory(careReportCategory(report, documents)); setReportDate(report.date);
    setTextSource(document?.textSource ?? 'copied');
  }
  function openText(report: CareReport) {
    resetEditor(report); setUpload(null); setQueue([]); setEditorId(report.id);
    if (!documents.find((document) => document.patientId === patientId && document.reportId === report.id)?.text.trim()) void readReport(report);
  }
  function closeEditor() {
    cancelRead(); setEditorId(null); setUpload(null); setQueue([]); setText(''); setPdfText(null); setReadMessage(''); setError(''); onEditClose?.();
  }
  async function readReport(report: CareReport) {
    cancelRead();
    const controller = new AbortController(); reader.current = controller;
    const pdf = report.file.type === 'application/pdf' || /\.pdf$/i.test(report.file.name);
    const plain = report.file.type === 'text/plain' || /\.txt$/i.test(report.file.name);
    if (!pdf && !plain) { reader.current = null; setReadMessage(t('Add any text you want to find later.', 'जो टेक्स्ट बाद में खोजना हो, उसे जोड़ें।')); return; }
    setReading(true); setProgress({ page: 0, total: 0 }); setError(''); setReadMessage('');
    try {
      if (plain) {
        const content = await report.file.text();
        if (controller.signal.aborted) return;
        if (content.length > 200000) throw new CarePdfReadError('too-much-text');
        setText(content); setPdfText(null); setTextSource('text-file');
      } else {
        const result = await extractCareDocumentPdf(report.file, { signal: controller.signal, onProgress: (page, total) => { if (!controller.signal.aborted) setProgress({ page, total }); } });
        if (controller.signal.aborted) return;
        setPdfText({ pagesRead: result.pagesRead, pageCount: result.pageCount, pagesWithoutText: result.pagesWithoutText });
        if (!result.text.trim()) { setReadMessage(t('This file has no readable text. You can add text from the original.', 'इस फ़ाइल में पढ़ने लायक टेक्स्ट नहीं मिला। मूल फ़ाइल से टेक्स्ट जोड़ सकते हैं।')); return; }
        setText(result.text); setTextSource('pdf-text');
      }
    } catch (caught) {
      if (controller.signal.aborted) return;
      const code = caught instanceof CarePdfReadError ? caught.code : 'unreadable';
      setError(code === 'password' ? t('This PDF is locked. Unlock it or add the text below.', 'यह PDF लॉक है। इसे अनलॉक करें या नीचे टेक्स्ट जोड़ें।')
        : code === 'too-much-text' ? t('Add up to 200,000 characters from the original.', 'मूल फ़ाइल से अधिकतम 2,00,000 अक्षर जोड़ें।')
          : t('Could not read the file text. You can still save the original and add text below.', 'फ़ाइल का टेक्स्ट नहीं पढ़ सके। मूल फ़ाइल सेव करके नीचे टेक्स्ट जोड़ सकते हैं।'));
    } finally { if (reader.current === controller) { reader.current = null; setReading(false); } }
  }
  function addFiles(files: File[]) {
    if (!onReportsChange || !files.length) return;
    if (files.length > 10) { setError(t('Choose up to 10 files at a time.', 'एक बार में अधिकतम 10 फ़ाइलें चुनें।')); return; }
    const invalid = files.find((file) => careDocumentFileError(file));
    if (invalid) { setError(careDocumentFileError(invalid) === 'size' ? t('Choose files under 10 MB.', '10 MB से छोटी फ़ाइलें चुनें।') : t('Choose a PDF, photo or text file.', 'PDF, फ़ोटो या टेक्स्ट फ़ाइल चुनें।')); return; }
    const pending = files.map((file) => ({ id: `document-${crypto.randomUUID()}`, patientId, date: localDate(), addedBy: author.trim(), file }));
    resetEditor(pending[0]); setUpload(pending[0]); setEditorId(null); setQueue(pending.slice(1)); void readReport(pending[0]);
  }
  function saveText(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !author.trim() || reading) return;
    const parsed = new Date(`${reportDate}T12:00:00Z`);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(reportDate) || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== reportDate || reportDate > localDate()) { setError(t('Choose the date printed on the document, up to today.', 'दस्तावेज़ पर लिखी तारीख चुनें, आज तक की।')); return; }
    if (text.length > 200000) { setError(t('Keep the text under 200,000 characters.', 'टेक्स्ट 2,00,000 अक्षरों तक रखें।')); return; }
    const next: CareDocumentText = { reportId: editing.id, patientId, category, text: text.trim(), recordedBy: author.trim(), textSource, reviewedAt: new Date().toISOString(), ...(pdfText ?? {}) };
    const report = { ...editing, date: reportDate };
    onReportsChange?.(upload ? [...reports, report] : reports.map((item) => item.patientId === patientId && item.id === report.id ? report : item));
    onChange([...documents.filter((document) => document.patientId !== patientId || document.reportId !== editing.id), next]);
    if (queue.length) { const [nextReport, ...remaining] = queue; resetEditor(nextReport); setUpload(nextReport); setQueue(remaining); void readReport(nextReport); }
    else {
      closeEditor(); setMessage(t('Document saved.', 'दस्तावेज़ सेव हो गया।')); setSubmitted(''); setFolder(category);
      if (returnToMedicines.current) router.push(`/medicines?prescription=${encodeURIComponent(report.id)}`);
      else window.requestAnimationFrame(() => searchInput.current?.focus());
    }
  }
  function search(value: string) { setQuery(value); setSubmitted(value.trim()); setMessage(''); }
  function removeReport(reportId: string) {
    if (!onReportsChange) return;
    const next = removeCareDocument(patientId, reportId, reports, documents);
    if (!next.removed) return;
    onReportsChange(next.reports); onChange(next.documents);
    setRemoved((current) => [...current, next.removed!]);
    if (editing?.id === reportId) closeEditor();
    setMessage(''); setError('');
  }
  function undoRemoval() {
    const last = removed[removed.length - 1];
    if (!last || !onReportsChange) return;
    const next = restoreCareDocument(patientId, last, reports, documents);
    if (!next.restored) { setError(t('A newer copy is already here. It has been kept.', 'नई कॉपी यहाँ पहले से है। उसे रखा गया है।')); return; }
    onReportsChange(next.reports); onChange(next.documents); setRemoved((current) => current.slice(0, -1));
    setMessage(t('Document restored.', 'दस्तावेज़ वापस आ गया।')); setError('');
  }
  function download() {
    const content = ['SAANTHVANA — DOCUMENT EXCERPTS', `${t('Question', 'सवाल')}: ${submitted}`,
      ...matches.map(({ report, recordedBy, excerpts }) => [report.file.name, `${t('Document date', 'दस्तावेज़ की तारीख')}: ${report.date}`, `${t('Text checked by', 'टेक्स्ट जाँचने वाले')}: ${recordedBy}`, ...excerpts.map((excerpt) => `${excerpt.page ? `${t('Page', 'पृष्ठ')} ${excerpt.page} · ` : ''}${t('Line', 'पंक्ति')} ${excerpt.line}\n${excerpt.text}`)].join('\n')),
    ].join('\n\n');
    const url = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
    const link = window.document.createElement('a'); link.href = url; link.download = 'saanthvana-document-excerpts.txt'; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  if (editing) return <section className="care-document-search care-document-review" aria-labelledby={`${id}-review-heading`}>
    <header className="care-document-heading"><div><span className="care-document-eyebrow">{t('Check & save', 'जाँचें और सेव करें')}</span><h2 id={`${id}-review-heading`} tabIndex={-1} ref={reviewHeading}>{t('Your document', 'आपका दस्तावेज़')}</h2></div><button type="button" onClick={closeEditor}>{t('Cancel', 'रद्द करें')}</button></header>
    <form className="care-document-editor" onSubmit={saveText}><div className="care-document-review-grid">
      <div className="care-document-original"><DocumentPreview report={editing} hindi={hindi} /><div className="care-document-original-caption"><strong>{editing.file.name}</strong><OriginalLink report={editing} hindi={hindi} /></div></div>
      <div className="care-document-review-fields"><div className="care-document-fields-row"><label>{t('Keep under', 'कहाँ रखें')}<select value={category} onChange={(event) => setCategory(event.target.value as CareDocumentCategory)}><option value="cancer">{t('Cancer care', 'कैंसर की देखभाल')}</option><option value="monitoring">{t('Blood & lab tests', 'खून और लैब जाँच')}</option><option value="other">{t('Other', 'अन्य')}</option></select></label><label>{t('Document date', 'दस्तावेज़ की तारीख')}<input type="date" required max={localDate()} value={reportDate} disabled={!onReportsChange} onChange={(event) => setReportDate(event.target.value)} /></label></div>
        {reading && <div className="care-document-reading" role="status"><progress max={progress.total || 12} value={progress.page} /><span>{progress.total ? t(`Reading ${progress.page} of ${progress.total} pages`, `${progress.total} में से ${progress.page} पृष्ठ पढ़े`) : t('Opening your document…', 'दस्तावेज़ खोल रहे हैं…')}</span><button type="button" onClick={cancelRead}>{t('Stop', 'रोकें')}</button></div>}
        {readMessage && <p className="care-document-read-message" role="status">{readMessage}</p>}
        <details className="care-document-text-details"><summary>{t('Text for search · optional', 'खोज के लिए टेक्स्ट · वैकल्पिक')}</summary><div className="care-document-text-field"><div><label htmlFor={`${id}-text`}>{t('Check the text', 'टेक्स्ट जाँचें')}</label>{isPdf && !reading && <button type="button" onClick={() => readReport(editing)}>{t('Read again', 'दोबारा पढ़ें')}</button>}</div><textarea id={`${id}-text`} value={text} disabled={reading} onChange={(event) => setText(event.target.value)} maxLength={200000} rows={5} placeholder={t('Copy the words you want to find later.', 'जो शब्द बाद में खोजने हों, उन्हें यहाँ लिखें।')} /></div></details>
        {pdfText && (pdfText.pageCount > pdfText.pagesRead || pdfText.pagesWithoutText.length > 0) && <p className="care-document-read-scope">{pdfText.pageCount > pdfText.pagesRead ? t(`Read the first ${pdfText.pagesRead} of ${pdfText.pageCount} pages. `, `${pdfText.pageCount} में से पहले ${pdfText.pagesRead} पृष्ठ पढ़े। `) : ''}{pdfText.pagesWithoutText.length > 0 ? t(`No text on pages ${pdfText.pagesWithoutText.join(', ')}. Add their text here to include them in search.`, `पृष्ठ ${pdfText.pagesWithoutText.join(', ')} का टेक्स्ट नहीं मिला। खोज में शामिल करने के लिए यहाँ जोड़ें।`) : ''}</p>}
        {error && <p className="care-document-error" role="alert">{error}</p>}
        <div className="care-document-review-footer"><button type="submit" className="care-document-save" disabled={reading || !author.trim()}><DocumentIcon kind="check" />{queue.length ? t('Save & next', 'सेव करें और आगे बढ़ें') : t('Save document', 'दस्तावेज़ सेव करें')}</button>{queue.length > 0 && <span>{t(`${queue.length} more to review`, `${queue.length} और जाँचने हैं`)}</span>}{!upload && onReportsChange && <button type="button" className="care-document-remove" onClick={() => removeReport(editing.id)}><DocumentIcon kind="remove" />{t('Remove document', 'दस्तावेज़ हटाएँ')}</button>}</div>
      </div></div></form>
  </section>;

  return <section className={`care-document-search${dragging ? ' is-dragging' : ''}`} aria-labelledby={`${id}-heading`}
    onDragOver={(event) => { if (onReportsChange && event.dataTransfer.types.includes('Files')) { event.preventDefault(); setDragging(true); } }}
    onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false); }}
    onDrop={(event) => { if (!onReportsChange) return; event.preventDefault(); setDragging(false); addFiles(Array.from(event.dataTransfer.files)); }}>
    <header className="care-document-heading"><div className="care-document-heading-title"><span className="care-document-heading-icon"><DocumentIcon /></span><div><h2 id={`${id}-heading`}>{t('My documents', 'मेरे दस्तावेज़')}</h2><span className="care-document-count">{available.length} {t(available.length === 1 ? 'document' : 'documents', 'दस्तावेज़')}</span></div></div>{onReportsChange && <><input ref={input} className="care-document-file-input" type="file" multiple accept="application/pdf,image/jpeg,image/png,image/webp,text/plain,.txt" onChange={(event) => { addFiles(Array.from(event.target.files ?? [])); event.target.value = ''; }} aria-label={t('Choose documents', 'दस्तावेज़ चुनें')} /><button type="button" className="care-document-add" onClick={() => input.current?.click()}><DocumentIcon kind="upload" />{t('Upload report', 'रिपोर्ट अपलोड करें')}</button></>}</header>
    <div className="care-document-question-panel"><form className="care-document-search-form" onSubmit={(event) => { event.preventDefault(); search(query); }}>
      <label className="care-document-search-label" htmlFor={`${id}-query`}>{t('Ask your documents', 'अपने दस्तावेज़ों से पूछें')}</label><DocumentIcon kind="search" /><input ref={searchInput} id={`${id}-query`} type="search" value={query} maxLength={160} onChange={(event) => setQuery(event.target.value)} placeholder={t('Ask a question or find a word', 'सवाल पूछें या शब्द खोजें')} /><button type="submit" disabled={!query.trim()}>{t('Find', 'खोजें')}</button>
    </form><div className="care-document-topics">{[['What should I bring to my next visit?', 'मुलाकात के लिए क्या लाना है?'], ['What matters to me?', 'मेरे लिए क्या महत्वपूर्ण है?'], ['My latest haemoglobin', 'मेरा आखिरी हीमोग्लोबिन']].map(([en, hi]) => <button key={en} type="button" aria-pressed={submitted === t(en, hi)} onClick={() => search(t(en, hi))}>{t(en, hi)} <span aria-hidden="true">↗</span></button>)}</div></div>
    <div className="care-document-folders" role="group" aria-label={t('Document folders', 'दस्तावेज़ के फ़ोल्डर')}>{folders.map(({ key, name }) => <button key={key} type="button" data-folder={key} aria-pressed={folder === key} onClick={() => setFolder(key)}>{name}<span>{key === 'all' ? available.length : available.filter((report) => careReportCategory(report, documents) === key).length}</span></button>)}</div>
    {message && <p className="care-document-message" role="status"><DocumentIcon kind="check" />{message}</p>}{error && <p className="care-document-error" role="alert">{error}</p>}
    {removed.length > 0 && <div className="care-document-undo" role="status"><span>{t('Removed', 'हटाया')}: <strong>{removed[removed.length - 1].report.file.name}</strong></span><button type="button" onClick={undoRemoval}>{t('Undo', 'वापस लाएँ')}</button></div>}
    {submitted ? <div className="care-document-results" aria-live="polite"><div className="care-document-results-heading"><h3>{t('From your documents', 'आपके दस्तावेज़ों से')}</h3><button type="button" onClick={() => { setSubmitted(''); setQuery(''); }}>{t('Back to files', 'फ़ाइलों पर वापस')}</button></div>
      {matches.length > 0 ? <><p className="care-document-source-count">{matches.length} {t(matches.length === 1 ? 'source' : 'sources', 'स्रोत')} · {t('Quoted from the checked text', 'जाँचे गए टेक्स्ट से लिए गए अंश')}</p>{matches.map(({ report, excerpts }) => <article className="care-document-match" key={report.id}>
        <header><span className="care-document-file-icon" data-folder={careReportCategory(report, documents)}><DocumentIcon /></span><div><h4>{report.file.name}</h4><time dateTime={report.date}>{displayDate(report.date, hindi)}</time></div></header>
        {excerpts.slice(0, 2).map((excerpt) => <blockquote key={excerpt.line}><p>{excerpt.text}</p><cite>{excerpt.page ? `${t('Page', 'पृष्ठ')} ${excerpt.page} · ` : ''}{t('Line', 'पंक्ति')} {excerpt.line}</cite></blockquote>)}
        {excerpts.length > 2 && <details className="care-document-more"><summary>{t(`${excerpts.length - 2} more passages`, `${excerpts.length - 2} और अंश`)}</summary>{excerpts.slice(2).map((excerpt) => <blockquote key={excerpt.line}><p>{excerpt.text}</p><cite>{excerpt.page ? `${t('Page', 'पृष्ठ')} ${excerpt.page} · ` : ''}{t('Line', 'पंक्ति')} {excerpt.line}</cite></blockquote>)}</details>}
        <footer><button type="button" onClick={() => openText(report)}>{t('Read document', 'दस्तावेज़ पढ़ें')}</button><OriginalLink report={report} hindi={hindi} /></footer>
      </article>)}<button type="button" className="care-document-download" onClick={download}>{t('Download these passages', 'ये अंश डाउनलोड करें')}</button></>
        : <div className="care-document-empty"><DocumentIcon kind="search" /><h3>{t('Not found in these documents', 'इन दस्तावेज़ों में नहीं मिला')}</h3><p>{t('Try a report name, a medicine name, or fewer words.', 'जाँच का नाम, दवा का नाम या कम शब्द लिखकर खोजें।')}</p>{folder !== 'all' && <button type="button" onClick={() => setFolder('all')}>{t('Search all files', 'सभी फ़ाइलों में खोजें')}</button>}</div>}
      {availableText.length < available.length && <p className="care-document-limit">{t(`${available.length - availableText.length} files need text before they can be searched.`, `${available.length - availableText.length} फ़ाइलों में खोजने के लिए टेक्स्ट जोड़ना होगा।`)}</p>}
      {availableText.some((document) => (document.pageCount ?? 0) > (document.pagesRead ?? 0) || Boolean(document.pagesWithoutText?.length)) && <p className="care-document-limit">{t('Some pages have no saved text. View the original for the full document.', 'कुछ पृष्ठों का टेक्स्ट सेव नहीं है। पूरा दस्तावेज़ मूल फ़ाइल में देखें।')}</p>}
    </div> : filtered.length > 0 ? <ul className="care-document-library">{filtered.map((report) => {
      const document = availableText.find((item) => item.reportId === report.id);
      return <li key={report.id} data-folder={careReportCategory(report, documents)}><span className="care-document-file-icon" data-folder={careReportCategory(report, documents)}><DocumentIcon /></span><div><button type="button" className="care-document-file-title" onClick={() => openText(report)}>{report.file.name.replace(/[_]/g, ' ')}</button><span className="care-document-file-meta"><time dateTime={report.date}>{displayDate(report.date, hindi)}</time><span>{categoryLabel(careReportCategory(report, documents))}</span></span></div><div className="care-document-file-actions"><button type="button" className="care-document-file-action" onClick={() => openText(report)}>{document ? t('Read', 'पढ़ें') : t('Add text', 'टेक्स्ट जोड़ें')} <span aria-hidden="true">↗</span></button>{onReportsChange && <button type="button" className="care-document-remove" onClick={() => removeReport(report.id)} aria-label={`${t('Remove', 'हटाएँ')} ${report.file.name}`}><DocumentIcon kind="remove" /></button>}</div></li>;
    })}</ul> : <div className="care-document-empty"><DocumentIcon kind="upload" /><h3>{t(available.length ? 'No files in this folder' : 'Keep your reports together', available.length ? 'इस फ़ोल्डर में कोई फ़ाइल नहीं है' : 'अपनी रिपोर्ट एक जगह रखें')}</h3>{onReportsChange && <button type="button" onClick={() => input.current?.click()}>{t('Add a PDF, photo or text file', 'PDF, फ़ोटो या टेक्स्ट फ़ाइल जोड़ें')}</button>}</div>}
  </section>;
}
