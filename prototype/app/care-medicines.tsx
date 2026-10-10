'use client';

import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import Link from 'next/link';
import { eventFallsOn, type CareCheck, type CareEvent, type CareReport } from './care-calendar-state';
import type { CareDocumentText } from './care-document-state';
import { CareMedicineImport } from './care-medicine-import';
import {
  deleteMedicineImageImport,
  saveStandaloneMedicineImage,
  standaloneMedicineImageImportId,
} from './care-medicine-image-store';
import { MedicinePhoto, StoredMedicinePhoto } from './care-medicine-photo';
import { medicineMealTimingLabel, type MedicineMealTiming } from './care-medicine-import-state';
import {
  loadMedicineRegimens,
  patientMedicineRegimens,
  removeMedicineRegimen,
  reviseMedicineRegimen,
  saveMedicineRegimens,
  type MedicineRegimen,
  type MedicineRegimenMealTiming,
} from './care-medicine-regimen-state';
import { literalPrescriptionTime, makeMedicineEvent, medicinePrescription, prescriptionLines, type MedicineDraft } from './care-medicines-state';
import { formatMedicineTime, medicineTimeGroups } from './care-medicine-schedule';
import './care-medicines.css';

export type CareMedicinesProps = {
  patientId: string; author: string; today: string; hindi: boolean;
  events: CareEvent[]; checks: CareCheck[]; reports: CareReport[]; documents: CareDocumentText[];
  onAdd: (event: CareEvent) => void; onUpdate: (event: CareEvent) => void;
  onCheck: (eventId: string, date: string, complete: boolean) => void; onRemove: (eventId: string) => void;
};

function MedicineIcon({ kind = 'medicine' }: { kind?: 'medicine' | 'file' | 'check' | 'plus' | 'camera' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'file' ? <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>
      : kind === 'check' ? <path d="m5 12 4 4L19 6" /> : kind === 'plus' ? <path d="M12 5v14M5 12h14" /> : kind === 'camera' ? <><path d="M14.5 5 16 7h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3l1.5-2Z" /><circle cx="12" cy="13" r="3.5" /></>
        : <><path d="m8 4-4 4a5.66 5.66 0 0 0 8 8l4-4a5.66 5.66 0 0 0-8-8Z" /><path d="m6 6 8 8M18 15v6m-3-3h6" /></>}
  </svg>;
}

function PrescriptionLink({ report, hindi }: { report: CareReport; hindi: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(report.file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [report.file]);
  return <a ref={link} target="_blank" rel="noreferrer"><MedicineIcon kind="file" />{hindi ? 'पर्चा खोलें' : 'Open prescription'} <span aria-hidden="true">↗</span></a>;
}

function SavedFileLink({ file, label }: { file: File; label: string }) {
  const link = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(file);
    if (link.current) link.current.href = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);
  return <a ref={link} target="_blank" rel="noreferrer"><MedicineIcon kind="file" />{label} <span aria-hidden="true">↗</span></a>;
}

function regimenMealLabel(value: MedicineRegimenMealTiming, hindi: boolean): string {
  const timing: MedicineMealTiming = value === 'beforeFood' ? 'before-food'
    : value === 'withFood' ? 'with-food'
      : value === 'afterFood' ? 'after-food'
        : 'not-stated';
  return medicineMealTimingLabel(timing, hindi);
}

export function CareMedicines(props: CareMedicinesProps) {
  return <MedicinesForPatient key={`${props.patientId}:${props.author}`} {...props} />;
}

function MedicinesForPatient({ patientId, author, today, hindi, events, checks, reports, documents, onAdd, onUpdate, onCheck, onRemove }: CareMedicinesProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [date, setDate] = useState(today);
  const [view, setView] = useState<'day' | 'all'>('day');
  const [selectedTime, setSelectedTime] = useState('all');
  const [draft, setDraft] = useState<MedicineDraft | null>(null);
  const [importing, setImporting] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [showSource, setShowSource] = useState(false);
  const [regimens, setRegimens] = useState<MedicineRegimen[]>([]);
  const [savingMedicine, setSavingMedicine] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const openedPrescription = useRef(false);
  const medicines = events.filter((event) => event.patientId === patientId && event.kind === 'Medicine').sort((a, b) => a.time.localeCompare(b.time) || a.title.localeCompare(b.title));
  const available = reports.filter((report) => report.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const timeGroups = medicineTimeGroups(medicines, patientId, date);
  const scheduled = timeGroups.flatMap((group) => group.medicines);
  const regimenIds = new Set(regimens.map((item) => item.id));
  const legacyMedicines = medicines.filter((item) => {
    const regimenId = medicinePrescription(item)?.regimenId;
    return !regimenId || !regimenIds.has(regimenId);
  });
  const visible = view === 'day'
    ? selectedTime === 'all' ? scheduled : timeGroups.find((group) => group.time === selectedTime)?.medicines ?? []
    : legacyMedicines;
  const checked = (item: CareEvent) => checks.find((check) => check.patientId === patientId && check.eventId === item.id && check.date === date);
  const takenCount = scheduled.filter((item) => checked(item)).length;
  const selectedReport = available.find((report) => report.id === draft?.reportId);
  const sourceLines = prescriptionLines(draft?.sourceText ?? '');
  const isEditing = draft !== null;
  const dateLabel = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });

  useEffect(() => { heading.current?.focus(); }, [editingId, isEditing]);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local persisted regimens hydrate after the client mounts.
    setRegimens(patientMedicineRegimens(loadMedicineRegimens(), patientId));
  }, [patientId]);
  useEffect(() => {
    if (openedPrescription.current) return;
    const reportId = new URLSearchParams(window.location.search).get('prescription');
    const report = reports.find((item) => item.patientId === patientId && item.id === reportId);
    if (!report) return;
    openedPrescription.current = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- a report URL opens its editor only after the patient reports hydrate.
    setDraft({ title: '', instructions: '', time: '', date: today, lastDay: today, reportId: report.id, sourceName: report.file.name, sourceText: documents.find((item) => item.patientId === patientId && item.reportId === report.id)?.text ?? '', checked: false });
    setShowSource(false);
  }, [reports, documents, patientId, today]);

  function openEditor(item?: CareEvent) {
    const prescription = item ? medicinePrescription(item) : null;
    const report = available.find((entry) => entry.id === prescription?.reportId);
    const linkedRegimen = regimens.find((entry) => entry.id === prescription?.regimenId);
    setEditingId(item?.id ?? null);
    setDraft({
      title: item?.title ?? '', instructions: item?.instructions ?? '', time: item?.time ?? '',
      date: item?.date ?? today, lastDay: item?.repeatUntil || item?.date || today, ongoing: Boolean(item?.repeatForever),
      reportId: report?.id ?? '', sourceName: prescription?.sourceName ?? '',
      sourceText: prescription?.sourceText ?? '', checked: false,
      mealTiming: prescription?.mealTiming, medicineImageName: prescription?.medicineImageName,
      medicineImageId: linkedRegimen?.medicineImageId ?? prescription?.medicineImageId ?? null,
      medicineImage: prescription?.medicineImage, prescriptionImage: prescription?.prescriptionImage,
      prescriptionImages: prescription?.prescriptionImages, prescriptionPageIndexes: prescription?.prescriptionPageIndexes,
      durationDays: prescription?.durationDays, prescriptionItemNumber: prescription?.prescriptionItemNumber,
      regimenId: prescription?.regimenId,
    });
    setError(''); setMessage(''); setShowSource(!prescription); setConfirmRemove(null);
  }

  function updateDraft(patch: Partial<MedicineDraft>) {
    if (!draft) return;
    setDraft({ ...draft, ...patch, checked: false }); setError('');
  }

  function chooseSource(reportId: string) {
    const report = available.find((item) => item.id === reportId);
    const source = documents.find((item) => item.patientId === patientId && item.reportId === reportId);
    updateDraft({ reportId: report?.id ?? '', sourceName: report?.file.name ?? '', sourceText: source?.text ?? '' });
    setShowSource(false);
  }

  function copyLine(line: string) {
    const time = literalPrescriptionTime(line);
    updateDraft({ instructions: line, ...(time ? { time } : {}) });
  }

  function chooseMedicinePhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size <= 0 || file.size > 25 * 1024 * 1024) {
      setError(t('Choose a JPG, PNG or WebP medicine photo smaller than 25 MB.', '25 MB से छोटी JPG, PNG या WebP दवा की तस्वीर चुनें।'));
      return;
    }
    updateDraft({ medicineImage: file, medicineImageName: file.name });
  }

  function removeMedicinePhoto() {
    updateDraft({ medicineImage: undefined, medicineImageId: null, medicineImageName: undefined });
  }

  function closeEditor() { setDraft(null); setEditingId(null); setError(''); }

  function removeRegimen(item: MedicineRegimen) {
    const stored = loadMedicineRegimens();
    const updated = removeMedicineRegimen(stored, patientId, item.id, { actor: author, at: new Date().toISOString() });
    if (updated === stored || !saveMedicineRegimens(updated)) {
      setMessage(t('The medicine could not be removed.', 'दवा हटाई नहीं जा सकी।'));
      return;
    }
    medicines.filter((event) => medicinePrescription(event)?.regimenId === item.id).forEach((event) => onRemove(event.id));
    setRegimens(patientMedicineRegimens(updated, patientId));
    setConfirmRemove(null);
    setMessage(t('Medicine removed with all of its reminder times.', 'दवा उसके सभी रिमाइंडर समय के साथ हटा दी गई।'));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft) return;
    const previous = editingId ? medicines.find((item) => item.id === editingId) : undefined;
    const checkedAt = new Date().toISOString();
    const context = {
      id: editingId ?? crypto.randomUUID(), patientId, author, checkedAt,
      reportIds: available.map((report) => report.id),
    };
    if (!makeMedicineEvent({ ...draft, medicineImage: undefined }, context, previous)) {
      setError(t('Check the prescription, time and dates before saving.', 'सेव करने से पहले पर्चा, समय और तारीखें जाँचें।'));
      return;
    }

    setSavingMedicine(true); setError('');
    let pendingImageImportId = '';
    try {
      let medicineImageId = draft.medicineImageId ?? null;
      if (draft.medicineImage) {
        const saved = await saveStandaloneMedicineImage({
          id: `medicine-photo-${crypto.randomUUID()}`,
          patientId,
          createdAt: checkedAt,
          file: draft.medicineImage,
        });
        pendingImageImportId = saved.importId;
        medicineImageId = saved.imageId;
      }
      const medicine = makeMedicineEvent({
        ...draft,
        medicineImage: undefined,
        // Regimen-backed reminders share one image reference through the regimen.
        medicineImageId: draft.regimenId ? null : medicineImageId,
        medicineImageName: medicineImageId ? (draft.medicineImage?.name ?? draft.medicineImageName) : undefined,
      }, context, previous);
      if (!medicine) throw new Error(t('The medicine changes could not be prepared.', 'दवा के बदलाव तैयार नहीं हो सके।'));

      if (draft.regimenId) {
        const stored = loadMedicineRegimens();
        const linkedRegimen = stored.find((item) => item.patientId === patientId && item.id === draft.regimenId);
        if (linkedRegimen) {
          const updated = reviseMedicineRegimen(stored, patientId, linkedRegimen.id, { medicineImageId }, { actor: author, at: checkedAt });
          if (updated === stored || !saveMedicineRegimens(updated)) throw new Error(t('The medicine photo could not be linked to every reminder.', 'दवा की तस्वीर सभी रिमाइंडर से नहीं जुड़ सकी।'));
          setRegimens(patientMedicineRegimens(updated, patientId));
        }
      }

      if (editingId) onUpdate(medicine); else onAdd(medicine);
      pendingImageImportId = '';
      const previousStandaloneImportId = draft.medicineImageId ? standaloneMedicineImageImportId(draft.medicineImageId) : null;
      if (previousStandaloneImportId && draft.medicineImageId !== medicineImageId) {
        void deleteMedicineImageImport(previousStandaloneImportId).catch(() => {});
      }
      setView('day');
      setDate(medicine.date <= today && (!medicine.repeatUntil || medicine.repeatUntil >= today) ? (eventFallsOn(medicine, today) ? today : medicine.date) : medicine.date);
      setSelectedTime(medicine.time);
      closeEditor(); setMessage(t('Medicine schedule saved.', 'दवा का समय सेव हो गया।'));
    } catch (reason) {
      if (pendingImageImportId) {
        try { await deleteMedicineImageImport(pendingImageImportId); }
        catch { /* The new photo is not referenced when the save fails. */ }
      }
      setError(reason instanceof Error ? reason.message : t('The medicine could not be saved. Try again.', 'दवा सेव नहीं हो सकी। फिर कोशिश करें।'));
    } finally { setSavingMedicine(false); }
  }

  if (importing) return <section className="care-medicines" aria-labelledby={`${id}-scan-heading`}>
    <header className="medicine-heading"><div className="medicine-heading-title"><span className="medicine-heading-icon"><MedicineIcon kind="file" /></span><h1 id={`${id}-scan-heading`} ref={heading} tabIndex={-1}>{t('Scan prescription', 'पर्चा स्कैन करें')}</h1></div><button type="button" onClick={() => setImporting(false)}>{t('Back', 'वापस')}</button></header>
    <CareMedicineImport patientId={patientId} author={author} today={today} hindi={hindi} onAdd={onAdd} onCancel={() => setImporting(false)} onComplete={(count) => { setRegimens(patientMedicineRegimens(loadMedicineRegimens(), patientId)); setImporting(false); setView('all'); setDate(today); setMessage(t(`${count} medicine${count === 1 ? '' : 's'} saved.`, `${count} दवाएँ सेव हुईं।`)); }} />
  </section>;

  if (draft) return <section className="care-medicines" aria-labelledby={`${id}-edit-heading`}>
    <header className="medicine-heading"><div className="medicine-heading-title"><span className="medicine-heading-icon"><MedicineIcon /></span><h1 id={`${id}-edit-heading`} ref={heading} tabIndex={-1}>{editingId ? t('Edit medicine', 'दवा बदलें') : t('Add from prescription', 'पर्चे से दवा जोड़ें')}</h1></div><button type="button" onClick={closeEditor}>{t('Back', 'वापस')}</button></header>
    <form className="medicine-editor" onSubmit={save}>
      <section className="medicine-source-panel">
        <Link href="/reports?upload=1&next=medicines" className="medicine-upload"><MedicineIcon kind="file" />{t('Upload prescription', 'पर्चा अपलोड करें')}</Link>
        <details className="medicine-source-choice" open={!selectedReport}><summary>{t('Choose prescription', 'पर्चा चुनें')}</summary><label htmlFor={`${id}-source`}>{t('Prescription', 'पर्चा')}<select id={`${id}-source`} value={draft.reportId} onChange={(event) => chooseSource(event.target.value)}><option value="">{t('Paste prescription text', 'पर्चे का टेक्स्ट जोड़ें')}</option>{available.map((report) => <option key={report.id} value={report.id}>{report.file.name} · {dateLabel(report.date)}</option>)}</select></label></details>
        {selectedReport ? <div className="medicine-source-heading"><strong>{selectedReport.file.name}</strong><PrescriptionLink report={selectedReport} hindi={hindi} /></div> : <label htmlFor={`${id}-source-name`}>{t('Prescription name or date', 'पर्चे का नाम या तारीख')}<input id={`${id}-source-name`} value={draft.sourceName} maxLength={240} required onChange={(event) => updateDraft({ sourceName: event.target.value })} /></label>}
        {selectedReport && <button type="button" className="medicine-source-toggle" aria-expanded={showSource} onClick={() => setShowSource(!showSource)}>{showSource ? t('Hide text', 'टेक्स्ट छिपाएँ') : t('Read prescription text', 'पर्चे का टेक्स्ट पढ़ें')}</button>}
        {(!selectedReport || showSource) && <label htmlFor={`${id}-source-text`}>{selectedReport ? t('Text from this prescription', 'इस पर्चे का टेक्स्ट') : t('Prescription text', 'पर्चे का टेक्स्ट')}<textarea id={`${id}-source-text`} rows={3} value={draft.sourceText} maxLength={200000} required={!selectedReport} onChange={(event) => updateDraft({ sourceText: event.target.value })} /></label>}
        {sourceLines.length > 0 && <details className="medicine-source-lines"><summary>{t('Use a line for the instructions', 'निर्देश के लिए एक पंक्ति चुनें')}</summary><ul>{sourceLines.map((line, index) => <li key={`${index}-${line.slice(0, 40)}`}><button type="button" onClick={() => copyLine(line)}>{line}</button></li>)}</ul></details>}
      </section>
      <section className="medicine-fields">
        <section className="medicine-editor-photo" aria-labelledby={`${id}-medicine-photo-heading`}>
          <div className="medicine-editor-photo-heading"><div><strong id={`${id}-medicine-photo-heading`}>{t('Medicine photo', 'दवा की तस्वीर')}</strong><span>{t('Use the strip or box so you can identify the medicine later.', 'स्ट्रिप या डिब्बे की तस्वीर रखें ताकि बाद में दवा पहचान सकें।')}</span></div>{(draft.medicineImage || draft.medicineImageId) && <button type="button" className="medicine-text-button" onClick={removeMedicinePhoto}>{t('Remove', 'हटाएँ')}</button>}</div>
          <div className="medicine-editor-photo-body">
            <div className={`medicine-editor-photo-preview${draft.medicineImage || draft.medicineImageId ? '' : ' is-empty'}`}>
              {draft.medicineImage ? <MedicinePhoto file={draft.medicineImage} alt={draft.title || t('Medicine', 'दवा')} hindi={hindi} />
                : draft.medicineImageId ? <StoredMedicinePhoto imageId={draft.medicineImageId} alt={draft.title || t('Medicine', 'दवा')} hindi={hindi} />
                  : <><MedicineIcon kind="camera" /><span>{t('No photo yet', 'अभी तस्वीर नहीं है')}</span></>}
            </div>
            <div className="medicine-editor-photo-actions">
              <label className="medicine-photo-input"><MedicineIcon kind="file" /><span>{draft.medicineImage || draft.medicineImageId ? t('Replace photo', 'तस्वीर बदलें') : t('Choose photo', 'तस्वीर चुनें')}</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseMedicinePhoto} /></label>
              <label className="medicine-photo-input"><MedicineIcon kind="camera" /><span>{t('Take photo', 'फोटो लें')}</span><input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" onChange={chooseMedicinePhoto} /></label>
            </div>
          </div>
        </section>
        <label htmlFor={`${id}-name`}>{t('Medicine name', 'दवा का नाम')}<input id={`${id}-name`} value={draft.title} required maxLength={160} onChange={(event) => updateDraft({ title: event.target.value })} /></label>
        <label htmlFor={`${id}-instructions`}>{t('Dose & instructions on the prescription', 'पर्चे पर लिखी खुराक और निर्देश')}<textarea id={`${id}-instructions`} rows={2} value={draft.instructions} required maxLength={400} onChange={(event) => updateDraft({ instructions: event.target.value })} /></label>
        <div className="medicine-schedule-fields"><label htmlFor={`${id}-time`}>{t('Time', 'समय')}<input id={`${id}-time`} type="time" value={draft.time} required onChange={(event) => updateDraft({ time: event.target.value })} /></label><label htmlFor={`${id}-first`}>{t('First day', 'पहला दिन')}<input id={`${id}-first`} type="date" value={draft.date} required onChange={(event) => updateDraft({ date: event.target.value, lastDay: draft.lastDay < event.target.value ? event.target.value : draft.lastDay })} /></label><label htmlFor={`${id}-last`}>{t('Last day', 'आखिरी दिन')}<input id={`${id}-last`} type="date" value={draft.lastDay} min={draft.date} required onChange={(event) => updateDraft({ lastDay: event.target.value })} /></label></div>
        <p className="medicine-repeat-caption">{draft.lastDay > draft.date ? t('At this time each day between these dates.', 'इन तारीखों के बीच रोज़ इस समय।') : t('One date, one time. Add another entry for another time.', 'एक तारीख, एक समय। दूसरे समय के लिए अलग एंट्री जोड़ें।')}</p>
        <label className="medicine-prescription-check"><input type="checkbox" checked={draft.checked} onChange={(event) => setDraft({ ...draft, checked: event.target.checked })} /><span>{t('Checked against prescription', 'पर्चे से मिलाकर जाँच लिया')}</span></label>
        {error && <p className="medicine-error" role="alert">{error}</p>}
        <div className="medicine-form-actions"><button type="submit" className="medicine-primary" disabled={savingMedicine || !draft.checked || !draft.title.trim() || !draft.instructions.trim() || !draft.time || !draft.sourceName.trim() || (!draft.reportId && !draft.sourceText.trim())}>{savingMedicine ? t('Saving…', 'सेव हो रहा है…') : t('Save medicine', 'दवा सेव करें')}</button><button type="button" disabled={savingMedicine} onClick={closeEditor}>{t('Cancel', 'रद्द करें')}</button></div>
      </section>
    </form>
  </section>;

  return <section className="care-medicines" aria-labelledby={`${id}-heading`}>
    <header className="medicine-heading">
      <div className="medicine-heading-title"><span className="medicine-heading-icon"><MedicineIcon /></span><h1 id={`${id}-heading`} ref={heading} tabIndex={-1}>{t('Medicines', 'दवाएँ')}</h1></div>
      <details className="medicine-action-menu">
        <summary aria-label={t('Add or import medicine', 'दवा जोड़ें या आयात करें')} title={t('Add or import medicine', 'दवा जोड़ें या आयात करें')}><MedicineIcon kind="plus" /></summary>
        <div className="medicine-action-menu-panel">
          <button type="button" className="medicine-primary" onClick={() => { setImporting(true); setMessage(''); }}><MedicineIcon kind="file" /><span>{t('Scan & organize', 'स्कैन करके सूची बनाएँ')}</span></button>
          <Link href="/reports?upload=1&next=medicines" className="medicine-upload"><MedicineIcon kind="file" /><span>{t('Upload prescription', 'पर्चा अपलोड करें')}</span></Link>
          <button type="button" onClick={() => openEditor()}><MedicineIcon kind="plus" /><span>{t('Add manually', 'खुद जोड़ें')}</span></button>
        </div>
      </details>
    </header>
    {message && <p className="medicine-message" role="status">{message}</p>}
    <div className="medicine-day-controls"><div className="medicine-tabs"><button type="button" aria-pressed={view === 'day'} onClick={() => { setView('day'); setDate(today); setSelectedTime('all'); }}>{t('Today', 'आज')}</button><button type="button" aria-pressed={view === 'all'} onClick={() => { setView('all'); setSelectedTime('all'); }}>{t('All medicines', 'सभी दवाएँ')}</button></div>{view === 'day' && <input type="date" aria-label={t('Medicine schedule date', 'दवा की तारीख')} value={date} onChange={(event) => { if (event.target.value) { setDate(event.target.value); setSelectedTime('all'); } }} />}</div>
    {view === 'day' && <dl className="medicine-overview"><div><dt>{date === today ? t('On today’s list', 'आज की सूची में') : dateLabel(date)}</dt><dd>{scheduled.length}</dd></div><div><dt>{t('Taken', 'ले ली')}</dt><dd>{takenCount}</dd></div><div><dt>{t('Not marked taken', 'लेना दर्ज नहीं किया')}</dt><dd>{scheduled.length - takenCount}</dd></div></dl>}
    {view === 'day' && timeGroups.length > 0 && <section className="medicine-time-filter" aria-labelledby={`${id}-time-filter`}>
      <div className="medicine-time-filter-heading"><strong id={`${id}-time-filter`}>{t('Medicines by time', 'समय के अनुसार दवाएँ')}</strong><span>{t('Choose a time to see only those medicines.', 'सिर्फ उस समय की दवाएँ देखने के लिए समय चुनें।')}</span></div>
      <div className="medicine-time-capsules" role="group" aria-label={t('Filter medicines by time', 'समय के अनुसार दवाएँ छाँटें')}>
        <button type="button" aria-pressed={selectedTime === 'all'} onClick={() => setSelectedTime('all')}><span>{t('All times', 'सभी समय')}</span><strong>{scheduled.length}</strong></button>
        {timeGroups.map((group) => <button type="button" key={group.time} aria-pressed={selectedTime === group.time} onClick={() => setSelectedTime(group.time)}><time dateTime={group.time}>{formatMedicineTime(group.time)}</time><strong>{group.medicines.length}</strong></button>)}
      </div>
    </section>}
    {view === 'all' && regimens.length > 0 && <div className="medicine-regimen-list">{regimens.map((item, index) => <article className={`medicine-regimen-card medicine-color-${index % 3}`} key={item.id}>
      <div className="medicine-regimen-head">{item.medicineImageId ? <StoredMedicinePhoto imageId={item.medicineImageId} alt={item.medicineName} hindi={hindi} /> : <span className="medicine-regimen-placeholder"><MedicineIcon /></span>}<div><h2>{item.medicineName}</h2><p>{item.quantity}</p></div>{item.confidence !== null && <span className="medicine-confidence">{t('AI', 'AI')} {Math.round(item.confidence * 100)}%</span>}</div>
      {item.instructions && <p className="medicine-directions">{item.instructions}</p>}
      <div className="medicine-regimen-meta"><span className="medicine-meal-chip">{regimenMealLabel(item.mealTiming, hindi)}</span>{item.prescriptionItemNumber !== null && <span>{t(`Prescription item ${item.prescriptionItemNumber}`, `पर्चे की दवा ${item.prescriptionItemNumber}`)}</span>}{item.durationDays !== null && <span>{t(`${item.durationDays}-day course`, `${item.durationDays} दिन का कोर्स`)}</span>}<span>{item.endDate ? `${dateLabel(item.startDate)} – ${dateLabel(item.endDate)}` : t(`Ongoing from ${dateLabel(item.startDate)}`, `${dateLabel(item.startDate)} से जारी`)}</span>{item.prescriptionPageIndexes.length > 0 && <span>{t(`${item.prescriptionPageIndexes.length} prescription page${item.prescriptionPageIndexes.length === 1 ? '' : 's'} linked`, `${item.prescriptionPageIndexes.length} पर्चे के पेज जुड़े हैं`)}</span>}</div>
      <div className="medicine-regimen-times">{item.times.length ? item.times.map((time) => <time key={time}>{time}</time>) : <span>{t('No reminder scheduled', 'कोई रिमाइंडर तय नहीं')}</span>}</div>
      {item.uncertainties.length > 0 && <details className="medicine-saved-source"><summary>{t('Review notes', 'जाँच की टिप्पणियाँ')}</summary><ul>{item.uncertainties.map((uncertainty) => <li key={uncertainty}>{uncertainty}</li>)}</ul></details>}
      <div className="medicine-card-actions">{confirmRemove === item.id ? <div className="medicine-remove-confirm"><span>{t('Remove this medicine and all reminders?', 'यह दवा और सभी रिमाइंडर हटाएँ?')}</span><button type="button" onClick={() => removeRegimen(item)}>{t('Remove', 'हटाएँ')}</button><button type="button" onClick={() => setConfirmRemove(null)}>{t('Keep', 'रहने दें')}</button></div> : <button type="button" className="medicine-text-button" onClick={() => setConfirmRemove(item.id)}>{t('Remove medicine', 'दवा हटाएँ')}</button>}</div>
    </article>)}</div>}
    <div className="medicine-list">{visible.map((item, index) => {
      const check = eventFallsOn(item, date) ? checked(item) : undefined;
      const prescription = medicinePrescription(item);
      const report = available.find((entry) => entry.id === prescription?.reportId);
      const linkedRegimen = regimens.find((regimen) => regimen.id === prescription?.regimenId);
      return <article className={`medicine-card medicine-color-${index % 3}${check ? ' is-taken' : ''}`} key={item.id}>
        <div className="medicine-time"><MedicineIcon /><time>{item.time}</time></div>
        <div className="medicine-card-body"><div className="medicine-card-title">{linkedRegimen?.medicineImageId ? <StoredMedicinePhoto imageId={linkedRegimen.medicineImageId} alt={item.title} hindi={hindi} /> : prescription?.medicineImageId ? <StoredMedicinePhoto imageId={prescription.medicineImageId} alt={item.title} hindi={hindi} /> : prescription?.medicineImage ? <MedicinePhoto file={prescription.medicineImage} alt={item.title} hindi={hindi} /> : null}<h2>{item.title}</h2></div><p className="medicine-directions">{item.instructions}</p><div className="medicine-card-meta"><span>{dateLabel(item.date)}{item.repeatUntil && ` – ${dateLabel(item.repeatUntil)}`}</span>{prescription?.mealTiming && <span className="medicine-meal-chip">{medicineMealTimingLabel(prescription.mealTiming)}</span>}{prescription && <span>{prescription.sourceName}</span>}</div>{report && <PrescriptionLink report={report} hindi={hindi} />}{!report && prescription?.prescriptionImage && <SavedFileLink file={prescription.prescriptionImage} label={t('Open prescription image', 'पर्चे की तस्वीर खोलें')} />}{!report && prescription?.sourceText && <details className="medicine-saved-source"><summary>{t('Read prescription text', 'पर्चे का टेक्स्ट पढ़ें')}</summary><p>{prescription.sourceText}</p></details>}</div>
        <div className="medicine-card-actions">{view === 'day' && <button type="button" className="medicine-taken" aria-pressed={Boolean(check)} disabled={date > today} onClick={() => { onCheck(item.id, date, !check); setMessage(check ? t('Taken mark removed.', 'लेने का निशान हटा दिया।') : t('Marked taken.', 'लेना दर्ज हो गया।')); }}><MedicineIcon kind="check" />{check ? t('Undo', 'वापस करें') : t('Taken', 'ले ली')}</button>}{check && <span className="medicine-taken-by">{t('Marked by', 'दर्ज किया')}: {check.actor}</span>}<button type="button" className="medicine-text-button" onClick={() => openEditor(item)}>{prescription ? t('Edit', 'बदलें') : t('Add prescription', 'पर्चा जोड़ें')}</button>{confirmRemove === item.id ? <div className="medicine-remove-confirm"><span>{t('Remove this schedule?', 'यह समय हटाएँ?')}</span><button type="button" onClick={() => { onRemove(item.id); setConfirmRemove(null); setMessage(t('Schedule removed.', 'समय हटा दिया।')); }}>{t('Remove', 'हटाएँ')}</button><button type="button" onClick={() => setConfirmRemove(null)}>{t('Keep', 'रहने दें')}</button></div> : <button type="button" className="medicine-text-button" onClick={() => setConfirmRemove(item.id)} aria-label={`${t('Remove schedule', 'समय हटाएँ')}: ${item.title}`}>{t('Remove', 'हटाएँ')}</button>}</div>
      </article>;
    })}</div>
    {!visible.length && (view === 'day' || regimens.length === 0) && <div className="medicine-empty"><span><MedicineIcon /></span><h2>{selectedTime !== 'all' ? t('No medicines at this time', 'इस समय कोई दवा नहीं है') : medicines.length || regimens.length ? t('Nothing scheduled for this day', 'इस दिन कोई दवा दर्ज नहीं है') : t('Keep your medicines together', 'अपनी दवाएँ एक जगह रखें')}</h2><button type="button" onClick={() => selectedTime !== 'all' ? setSelectedTime('all') : medicines.length || regimens.length ? setView('all') : setImporting(true)}>{selectedTime !== 'all' ? t('Show all times', 'सभी समय दिखाएँ') : medicines.length || regimens.length ? t('See all medicines', 'सभी दवाएँ देखें') : t('Scan a prescription', 'पर्चा स्कैन करें')}</button></div>}
  </section>;
}
