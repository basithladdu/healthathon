'use client';

import { useCallback, useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import type { CareEvent } from './care-calendar-state';
import {
  organizeMedicineImagesWithAi,
  type CareMedicineAiProgress,
} from './care-medicine-ai';
import {
  MEDICINE_MEAL_TIMINGS,
  manualMedicineFromImage,
  medicineCourseDurationDays,
  medicineCourseEndDate,
  medicineMealTimingLabel,
  sortOrganizedMedicinesByPrescriptionOrder,
  type MedicineMealTiming,
  type OrganizedMedicine,
} from './care-medicine-import-state';
import {
  addMedicineRegimen,
  loadMedicineRegimens,
  makeMedicineRegimen,
  medicineRegimenId,
  saveMedicineRegimens,
  type MedicineRegimenMealTiming,
} from './care-medicine-regimen-state';
import {
  deleteMedicineImageImport,
  medicineStoredImageId,
  saveMedicineImageImport,
} from './care-medicine-image-store';
import { makeMedicineEvent } from './care-medicines-state';

type ReviewMedicine = OrganizedMedicine & {
  id: string;
  dose: string;
  times: string[];
  startDate: string;
  endDate: string;
  ongoing: boolean;
  confirmed: boolean;
};

export type CareMedicineImportProps = {
  patientId: string;
  author: string;
  today: string;
  hindi: boolean;
  onAdd: (event: CareEvent) => void;
  onCancel: () => void;
  onComplete: (medicineCount: number) => void;
};

function FileThumbnail({ file, alt }: { file: File; alt: string }) {
  const objectUrl = useRef('');
  const attachImage = useCallback((image: HTMLImageElement | null) => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = '';
    if (!image) return;
    objectUrl.current = URL.createObjectURL(file);
    image.src = objectUrl.current;
  }, [file]);
  return <img ref={attachImage} alt={alt} />;
}

function sourceName(files: readonly File[]): string {
  if (!files.length) return 'Prescription image';
  if (files.length === 1) return files[0].name.slice(0, 240);
  return `${files[0].name} + ${files.length - 1} more page${files.length === 2 ? '' : 's'}`.slice(0, 240);
}

function matchLabel(value: OrganizedMedicine['matchQuality'], hindi: boolean): string {
  if (value === 'matched') return hindi ? 'पर्चे से मिलान हुआ' : 'Matched to prescription';
  if (value === 'possible') return hindi ? 'संभावित मिलान — जाँचें' : 'Possible match — check it';
  if (value === 'manual') return hindi ? 'मैन्युअल एंट्री' : 'Manual entry';
  return hindi ? 'पर्चे से मिला, स्ट्रिप नहीं मिली' : 'From prescription · no strip match';
}

function doseFromMedicine(medicine: OrganizedMedicine): string {
  return [medicine.strength, medicine.quantity, medicine.form].filter(Boolean).join(' · ').slice(0, 160);
}

function instructionsFromMedicine(medicine: OrganizedMedicine): string {
  return [medicine.frequency, medicine.instructions].filter(Boolean).join(' · ').slice(0, 500);
}

function reviewMedicine(medicine: OrganizedMedicine, today: string): ReviewMedicine {
  const prescribedEndDate = medicineCourseEndDate(today, medicine.durationDays);
  return {
    ...medicine,
    id: crypto.randomUUID(),
    dose: doseFromMedicine(medicine),
    instructions: instructionsFromMedicine(medicine),
    times: [...medicine.times].sort(),
    startDate: today,
    endDate: prescribedEndDate ?? '',
    ongoing: prescribedEndDate === null,
    confirmed: false,
  };
}

function regimenMealTiming(value: MedicineMealTiming): MedicineRegimenMealTiming {
  return value === 'before-food' ? 'beforeFood'
    : value === 'with-food' ? 'withFood'
      : value === 'after-food' ? 'afterFood'
        : 'anytime';
}

function nextDefaultTime(times: readonly string[]): string {
  for (let hour = 8; hour < 24; hour += 1) {
    const candidate = `${String(hour).padStart(2, '0')}:00`;
    if (!times.includes(candidate)) return candidate;
  }
  for (let hour = 0; hour < 8; hour += 1) {
    const candidate = `${String(hour).padStart(2, '0')}:00`;
    if (!times.includes(candidate)) return candidate;
  }
  return '';
}

export function CareMedicineImport({ patientId, author, today, hindi, onAdd, onCancel, onComplete }: CareMedicineImportProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [prescriptions, setPrescriptions] = useState<File[]>([]);
  const [medicineImages, setMedicineImages] = useState<File[]>([]);
  const [preparedPrescriptions, setPreparedPrescriptions] = useState<File[]>([]);
  const [preparedMedicineImages, setPreparedMedicineImages] = useState<File[]>([]);
  const [drafts, setDrafts] = useState<ReviewMedicine[]>([]);
  const [unmatched, setUnmatched] = useState<number[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [phase, setPhase] = useState<'select' | 'scanning' | 'review'>('select');
  const [progress, setProgress] = useState<CareMedicineAiProgress | null>(null);
  const [error, setError] = useState('');
  const [invalidDraftId, setInvalidDraftId] = useState('');
  const [saving, setSaving] = useState(false);
  const aborter = useRef<AbortController | null>(null);

  useEffect(() => () => aborter.current?.abort(), []);

  function addFiles(kind: 'prescription' | 'medicine', event: ChangeEvent<HTMLInputElement>) {
    const maximum = kind === 'prescription' ? 5 : 10;
    const current = kind === 'prescription' ? prescriptions : medicineImages;
    const selected = [...(event.target.files ?? [])];
    event.target.value = '';
    const valid = selected.filter((file) => ['image/jpeg', 'image/png', 'image/webp'].includes(file.type) && file.size > 0 && file.size <= 25 * 1024 * 1024);
    if (valid.length !== selected.length) setError(t('Use JPG, PNG or WebP images smaller than 25 MB. They are compressed before upload.', '25 MB से छोटी JPG, PNG या WebP तस्वीरें चुनें। अपलोड से पहले वे छोटी की जाएँगी।'));
    else setError('');
    const known = new Set(current.map((file) => `${file.name}:${file.size}:${file.lastModified}`));
    const next = [...current, ...valid.filter((file) => !known.has(`${file.name}:${file.size}:${file.lastModified}`))].slice(0, maximum);
    if (kind === 'prescription') setPrescriptions(next); else setMedicineImages(next);
    if (current.length + valid.length > maximum) setError(t(`You can use up to ${maximum} images here.`, `यहाँ अधिकतम ${maximum} तस्वीरें चुनें।`));
  }

  function removeFile(kind: 'prescription' | 'medicine', index: number) {
    if (kind === 'prescription') setPrescriptions((files) => files.filter((_, item) => item !== index));
    else setMedicineImages((files) => files.filter((_, item) => item !== index));
  }

  async function organize() {
    if (!prescriptions.length || !medicineImages.length) {
      setError(t('Add at least one prescription page and one medicine-strip photo.', 'कम से कम एक पर्चे का पेज और एक दवा की स्ट्रिप की तस्वीर जोड़ें।'));
      return;
    }
    const controller = new AbortController();
    aborter.current = controller;
    setError(''); setProgress(null); setPhase('scanning');
    try {
      const result = await organizeMedicineImagesWithAi(prescriptions, medicineImages, { signal: controller.signal, onProgress: setProgress });
      setPreparedPrescriptions(result.preparedPrescriptionPages);
      setPreparedMedicineImages(result.preparedMedicineImages);
      setDrafts(sortOrganizedMedicinesByPrescriptionOrder(result.medicines)
        .map((medicine) => reviewMedicine(medicine, today)));
      setUnmatched(result.unmatchedMedicineImageIndexes);
      setWarnings(result.warnings);
      setPhase('review');
    } catch (reason) {
      if (controller.signal.aborted) { setPhase('select'); return; }
      setError(reason instanceof Error ? reason.message : t('The images could not be organized. Try clearer photos.', 'तस्वीरों से सूची नहीं बन सकी। साफ़ तस्वीरों के साथ फिर कोशिश करें।'));
      setPhase('select');
    } finally { aborter.current = null; }
  }

  function patchDraft(index: number, patch: Partial<ReviewMedicine>) {
    setDrafts((items) => items.map((item, itemIndex) => itemIndex === index
      ? { ...item, ...patch, ...(!('confirmed' in patch) ? { confirmed: false } : {}) }
      : item));
    setInvalidDraftId(''); setError('');
  }

  function patchTime(draftIndex: number, timeIndex: number, value: string) {
    const times = [...drafts[draftIndex].times];
    times[timeIndex] = value;
    patchDraft(draftIndex, { times: [...new Set(times.filter(Boolean))].sort() });
  }

  function addTime(index: number) {
    if (drafts[index].times.length >= 6) return;
    const value = nextDefaultTime(drafts[index].times);
    if (value) patchDraft(index, { times: [...drafts[index].times, value].sort() });
  }

  function removeTime(draftIndex: number, timeIndex: number) {
    patchDraft(draftIndex, { times: drafts[draftIndex].times.filter((_, index) => index !== timeIndex) });
  }

  function patchCourseStart(index: number, startDate: string) {
    const draft = drafts[index];
    const prescribedEnd = medicineCourseEndDate(startDate, draft.durationDays);
    const endDate = prescribedEnd ?? (draft.endDate && draft.endDate >= startDate ? draft.endDate : startDate);
    patchDraft(index, {
      startDate,
      endDate: draft.ongoing ? '' : endDate,
      ...(prescribedEnd ? { ongoing: false } : {}),
    });
  }

  function patchCourseDuration(index: number, value: string) {
    const draft = drafts[index];
    if (!value.trim()) { patchDraft(index, { durationDays: null }); return; }
    const durationDays = Number(value);
    const endDate = Number.isSafeInteger(durationDays) && durationDays > 0
      ? medicineCourseEndDate(draft.startDate, durationDays) : null;
    if (!endDate) { patchDraft(index, { durationDays: null }); return; }
    patchDraft(index, { durationDays, endDate, ongoing: false });
  }

  function patchCourseEnd(index: number, endDate: string) {
    const draft = drafts[index];
    patchDraft(index, { endDate, durationDays: medicineCourseDurationDays(draft.startDate, endDate) });
  }

  function patchOngoing(index: number, ongoing: boolean) {
    const draft = drafts[index];
    if (ongoing) { patchDraft(index, { ongoing: true, endDate: '', durationDays: null }); return; }
    const endDate = draft.endDate || medicineCourseEndDate(draft.startDate, draft.durationDays) || draft.startDate;
    patchDraft(index, {
      ongoing: false,
      endDate,
      durationDays: medicineCourseDurationDays(draft.startDate, endDate),
    });
  }

  function addManual(imageIndex = -1) {
    const medicine = manualMedicineFromImage('', imageIndex);
    setDrafts((items) => [...items, reviewMedicine(medicine, today)]);
    if (imageIndex >= 0) setUnmatched((items) => items.filter((item) => item !== imageIndex));
  }

  function showDraftError(index: number, message: string, confirmation = false) {
    const draft = drafts[index];
    setInvalidDraftId(draft.id); setError(message);
    requestAnimationFrame(() => {
      const card = document.getElementById(`${id}-draft-${draft.id}`);
      card?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      card?.querySelector<HTMLInputElement>(confirmation
        ? '.medicine-prescription-check input'
        : 'input:not([type="checkbox"]), textarea')?.focus();
    });
  }

  async function saveAll() {
    if (unmatched.length) { setError(t('Review or ignore every unmatched strip photo first.', 'पहले हर बिना-मिलान वाली स्ट्रिप की तस्वीर जाँचें या छोड़ें।')); return; }
    if (!drafts.length) { setError(t('Add at least one medicine.', 'कम से कम एक दवा जोड़ें।')); return; }
    const names = new Set<string>();
    for (let index = 0; index < drafts.length; index += 1) {
      const draft = drafts[index];
      if (!draft.title.trim() || !draft.dose.trim()) {
        showDraftError(index, t(`Medicine ${index + 1} needs a name and dose.`, `दवा ${index + 1} के लिए नाम और खुराक भरें।`)); return;
      }
      const normalizedName = draft.title.trim().toLocaleLowerCase();
      if (names.has(normalizedName)) {
        showDraftError(index, t(`Medicine ${index + 1} repeats a medicine name already in this list.`, `दवा ${index + 1} का नाम सूची में पहले से है।`)); return;
      }
      names.add(normalizedName);
      if (!draft.startDate || (!draft.ongoing && (!draft.endDate || draft.endDate < draft.startDate))) {
        showDraftError(index, t(`Check the course dates for medicine ${index + 1}.`, `दवा ${index + 1} की तारीखें जाँचें।`)); return;
      }
      if (draft.times.some((time) => !/^([01]\d|2[0-3]):[0-5]\d$/u.test(time)) || new Set(draft.times).size !== draft.times.length) {
        showDraftError(index, t(`Check the reminder times for medicine ${index + 1}.`, `दवा ${index + 1} के समय जाँचें।`)); return;
      }
      if (!draft.confirmed) {
        showDraftError(index, t(`Confirm medicine ${index + 1} against the original prescription before saving.`, `सेव करने से पहले दवा ${index + 1} को असली पर्चे से जाँचकर पुष्टि करें।`), true); return;
      }
    }

    setSaving(true); setError('');
    let pendingImageImportId = '';
    try {
      const importId = crypto.randomUUID();
      const checkedAt = new Date().toISOString();
      const audit = { actor: author, at: checkedAt };
      const stored = loadMedicineRegimens();
      let next = stored;
      const schedules: CareEvent[] = [];

      for (let index = 0; index < drafts.length; index += 1) {
        const draft = drafts[index];
        const regimenId = medicineRegimenId(patientId, importId, index);
        const regimen = makeMedicineRegimen({
          id: regimenId,
          patientId,
          sourceImportId: importId,
          sourceMedicineIndex: index,
          medicineName: draft.title,
          strength: draft.strength,
          form: draft.form,
          quantity: draft.dose,
          frequency: draft.frequency,
          durationDays: draft.durationDays,
          prescriptionItemNumber: draft.prescriptionItemNumber,
          instructions: draft.instructions,
          mealTiming: regimenMealTiming(draft.mealTiming),
          times: draft.times,
          startDate: draft.startDate,
          endDate: draft.ongoing ? null : draft.endDate,
          medicineImageId: draft.medicineImageIndex >= 0
            ? medicineStoredImageId(importId, 'medicine', draft.medicineImageIndex) : null,
          prescriptionPageIndexes: draft.prescriptionPageIndexes,
          confidence: draft.confidence,
          uncertainties: draft.uncertainties,
        }, audit);
        if (!regimen) throw new Error(t(`Medicine ${index + 1} could not be saved.`, `दवा ${index + 1} सेव नहीं हो सकी।`));
        const updated = addMedicineRegimen(next, regimen);
        if (updated === next) throw new Error(t(`Medicine ${index + 1} could not be added.`, `दवा ${index + 1} जोड़ी नहीं जा सकी।`));
        next = updated;

        const reminderInstructions = [draft.dose, draft.instructions].filter(Boolean).join(' · ');
        for (const time of draft.times) {
          const medicine = makeMedicineEvent({
            title: draft.title,
            instructions: reminderInstructions,
            time,
            date: draft.startDate,
            lastDay: draft.ongoing ? draft.startDate : draft.endDate,
            ongoing: draft.ongoing,
            reportId: '',
            sourceName: sourceName(prescriptions),
            sourceText: draft.sourceText.trim() || reminderInstructions,
            checked: true,
            mealTiming: draft.mealTiming,
            medicineImageName: draft.medicineImageIndex >= 0 ? medicineImages[draft.medicineImageIndex]?.name : undefined,
            prescriptionPageIndexes: draft.prescriptionPageIndexes,
            durationDays: draft.durationDays,
            prescriptionItemNumber: draft.prescriptionItemNumber,
            regimenId,
          }, { id: crypto.randomUUID(), patientId, author, checkedAt, reportIds: [] });
          if (!medicine) throw new Error(t(`A reminder for medicine ${index + 1} could not be saved.`, `दवा ${index + 1} का रिमाइंडर सेव नहीं हो सका।`));
          schedules.push(medicine);
        }
      }

      await saveMedicineImageImport({
        id: importId,
        patientId,
        createdAt: checkedAt,
        prescriptionImages: prescriptions.map((file, index) => preparedPrescriptions[index] ?? file),
        medicineImages: medicineImages.map((file, index) => preparedMedicineImages[index] ?? file),
      });
      pendingImageImportId = importId;
      if (!saveMedicineRegimens(next)) throw new Error(t('The medicine list could not be stored in this browser.', 'दवाओं की सूची इस ब्राउज़र में सेव नहीं हो सकी।'));
      pendingImageImportId = '';
      schedules.forEach(onAdd);
      onComplete(drafts.length);
    } catch (reason) {
      if (pendingImageImportId) {
        try { await deleteMedicineImageImport(pendingImageImportId); }
        catch { /* Keep the original save error. An orphaned import is not referenced by any medicine. */ }
      }
      setError(reason instanceof Error ? reason.message : t('The medicines could not be saved. Try again.', 'दवाएँ सेव नहीं हो सकीं। फिर कोशिश करें।'));
    } finally { setSaving(false); }
  }

  if (phase === 'scanning') return <div className="medicine-scan-progress" role="status" aria-live="polite">
    <span className="medicine-scan-spinner" aria-hidden="true" />
    <h2>{progress?.stage === 'organizing' ? t('Sahara AI is matching the medicines', 'सहारा AI दवाओं का मिलान कर रहा है') : t('Preparing clear, private copies', 'साफ़ और निजी प्रतियाँ तैयार हो रही हैं')}</h2>
    <p>{progress?.stage === 'preparing' ? `${progress.completed + 1} / ${progress.total} · ${progress.fileName}` : t('Reading every prescription page and strip together…', 'पर्चे के सभी पेज और स्ट्रिप साथ में पढ़े जा रहे हैं…')}</p>
    <p className="medicine-scan-privacy">{t('Photos are sent through Sahara’s protected Supabase function and are not saved by the organizer. Keep this page open until review appears.', 'तस्वीरें सहारा के सुरक्षित Supabase फ़ंक्शन से भेजी जाती हैं और ऑर्गनाइज़र उन्हें सेव नहीं करता। समीक्षा आने तक यह पेज खुला रखें।')}</p>
    <button type="button" onClick={() => aborter.current?.abort()}>{t('Cancel', 'रद्द करें')}</button>
  </div>;

  if (phase === 'select') return <div className="medicine-import-flow">
    <div className="medicine-import-intro"><div><p className="medicine-eyebrow">{t('Sahara AI organizer', 'सहारा AI ऑर्गनाइज़र')}</p><h2>{t('Turn prescription photos into a medicine list', 'पर्चे की तस्वीरों से दवाओं की सूची बनाएँ')}</h2><p>{t('Add prescription pages in order and one clear photo of each strip. Sahara reads and matches all images together, then you review every result.', 'पर्चे के पेज क्रम में और हर स्ट्रिप की एक साफ़ तस्वीर जोड़ें। सहारा सभी तस्वीरें साथ पढ़कर मिलान करता है, फिर आप हर नतीजा जाँचते हैं।')}</p></div><span className="medicine-import-step">1 / 2</span></div>
    <div className="medicine-import-grid">
      <ImageGroup id={`${id}-prescriptions`} title={t('Prescription pages', 'पर्चे के पेज')} hint={t('Add in page order · up to 5', 'पेज के क्रम में · अधिकतम 5')} files={prescriptions} onChange={(event) => addFiles('prescription', event)} onRemove={(index) => removeFile('prescription', index)} cameraLabel={t('Photograph prescription', 'पर्चे की तस्वीर लें')} chooseLabel={t('Choose pages', 'पेज चुनें')} />
      <ImageGroup id={`${id}-strips`} title={t('Medicine strips or boxes', 'दवा की स्ट्रिप या डिब्बे')} hint={t('One clear photo per medicine · up to 10', 'हर दवा की एक साफ़ तस्वीर · अधिकतम 10')} files={medicineImages} onChange={(event) => addFiles('medicine', event)} onRemove={(index) => removeFile('medicine', index)} cameraLabel={t('Photograph medicine', 'दवा की तस्वीर लें')} chooseLabel={t('Choose medicine photos', 'दवा की तस्वीरें चुनें')} />
    </div>
    <div className="medicine-import-note"><strong>{t('AI organizes; you decide.', 'AI सूची बनाता है; फैसला आपका है।')}</strong> {t('Unknown details stay blank. Verify every medicine, dose, food instruction and reminder against the original prescription.', 'अनजान जानकारी खाली रहती है। हर दवा, खुराक, खाने के निर्देश और रिमाइंडर को असली पर्चे से जाँचें।')}</div>
    {error && <p className="medicine-error" role="alert">{error}</p>}
    <div className="medicine-form-actions"><button type="button" className="medicine-primary" disabled={!prescriptions.length || !medicineImages.length} onClick={organize}>{t('Organize with Sahara AI', 'सहारा AI से सूची बनाएँ')}</button><button type="button" onClick={() => { setPhase('review'); addManual(); }}>{t('Add one manually', 'एक दवा खुद जोड़ें')}</button><button type="button" onClick={onCancel}>{t('Cancel', 'रद्द करें')}</button></div>
  </div>;

  return <div className="medicine-import-flow">
    <div className="medicine-import-intro"><div><p className="medicine-eyebrow">{t('Review before saving', 'सेव करने से पहले जाँचें')}</p><h2>{t(`${drafts.length} medicine${drafts.length === 1 ? '' : 's'} organized`, `${drafts.length} दवाओं की सूची तैयार है`)}</h2><p>{t('Correct anything that does not match the prescription. Unknown or uncertain details are called out below.', 'जो जानकारी पर्चे से मेल न खाए उसे ठीक करें। अनजान या संदिग्ध जानकारी नीचे दिखाई गई है।')}</p></div><span className="medicine-import-step">2 / 2</span></div>
    {warnings.length > 0 && <div className="medicine-import-warnings"><strong>{t('Sahara AI notes', 'सहारा AI की टिप्पणी')}</strong><ul>{warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul></div>}
    <div className="medicine-review-list">{drafts.map((draft, index) => <article id={`${id}-draft-${draft.id}`} className={`medicine-review-card${draft.confirmed ? ' is-checked' : ''}${invalidDraftId === draft.id ? ' has-error' : ''}`} key={draft.id}>
      <div className="medicine-review-card-head">{draft.medicineImageIndex >= 0 && medicineImages[draft.medicineImageIndex] ? <div className="medicine-review-photo"><FileThumbnail file={medicineImages[draft.medicineImageIndex]} alt={t(`Photo matched to medicine ${index + 1}`, `दवा ${index + 1} से मिली तस्वीर`)} /></div> : <div className="medicine-review-photo is-empty" aria-hidden="true">Rx</div>}<div><span>{draft.prescriptionItemNumber === null ? t(`Medicine ${index + 1}`, `दवा ${index + 1}`) : t(`Prescription item ${draft.prescriptionItemNumber}`, `पर्चे की दवा ${draft.prescriptionItemNumber}`)}</span><strong>{matchLabel(draft.matchQuality, hindi)}</strong>{draft.confidence !== null && <small>{t(`AI confidence ${Math.round(draft.confidence * 100)}%`, `AI भरोसा ${Math.round(draft.confidence * 100)}%`)}</small>}</div><button type="button" className="medicine-text-button" onClick={() => setDrafts((items) => items.filter((_, itemIndex) => itemIndex !== index))}>{t('Remove', 'हटाएँ')}</button></div>
      {draft.uncertainties.length > 0 && <div className="medicine-ai-uncertainties"><strong>{t('Check carefully', 'ध्यान से जाँचें')}</strong><ul>{draft.uncertainties.map((uncertainty) => <li key={uncertainty}>{uncertainty}</li>)}</ul></div>}
      <div className="medicine-review-fields">
        <label>{t('Medicine name', 'दवा का नाम')}<input value={draft.title} maxLength={120} onChange={(event) => patchDraft(index, { title: event.target.value })} /></label>
        <label>{t('Dose & quantity exactly as prescribed', 'पर्चे के अनुसार खुराक और मात्रा')}<input value={draft.dose} maxLength={160} onChange={(event) => patchDraft(index, { dose: event.target.value })} /></label>
        <label>{t('Frequency & instructions', 'कितनी बार और निर्देश')}<textarea rows={2} value={draft.instructions} maxLength={500} onChange={(event) => patchDraft(index, { instructions: event.target.value })} /></label>
        <fieldset className="medicine-meal-timing"><legend>{t('When to take it with food', 'खाने के हिसाब से कब लें')}</legend>{MEDICINE_MEAL_TIMINGS.map((value) => <button type="button" key={value} aria-pressed={draft.mealTiming === value} onClick={() => patchDraft(index, { mealTiming: value as MedicineMealTiming })}>{medicineMealTimingLabel(value, hindi)}</button>)}</fieldset>
        <div className="medicine-review-times"><div className="medicine-review-times-heading"><div><strong>{t('Optional reminder times', 'वैकल्पिक रिमाइंडर समय')}</strong><span>{t('No time means no notification; the medicine still stays in your list.', 'समय न होने पर सूचना नहीं आएगी; दवा सूची में फिर भी रहेगी।')}</span></div><button type="button" disabled={draft.times.length >= 6} onClick={() => addTime(index)}>+ {t('Add time', 'समय जोड़ें')}</button></div>{draft.times.length === 0 ? <p className="medicine-no-reminders">{t('No reminder will be scheduled. You can add one later.', 'कोई रिमाइंडर तय नहीं होगा। आप बाद में जोड़ सकते हैं।')}</p> : draft.times.map((time, timeIndex) => <div className="medicine-review-time" key={`${draft.id}-${time}`}><input type="time" value={time} aria-label={t(`Time ${timeIndex + 1} for ${draft.title || `medicine ${index + 1}`}`, `${draft.title || `दवा ${index + 1}`} का समय ${timeIndex + 1}`)} onChange={(event) => patchTime(index, timeIndex, event.target.value)} /><button type="button" onClick={() => removeTime(index, timeIndex)} aria-label={t('Remove this time', 'यह समय हटाएँ')}>×</button></div>)}</div>
        <div className="medicine-course-dates"><label>{t('First day', 'पहला दिन')}<input type="date" value={draft.startDate} onChange={(event) => patchCourseStart(index, event.target.value)} /></label><label>{t('Course length (days)', 'कोर्स की अवधि (दिन)')}<input type="number" min="1" step="1" inputMode="numeric" value={draft.durationDays ?? ''} placeholder={t('Not stated', 'नहीं बताया')} onChange={(event) => patchCourseDuration(index, event.target.value)} /></label><label className="medicine-ongoing"><input type="checkbox" checked={draft.ongoing} onChange={(event) => patchOngoing(index, event.target.checked)} /><span>{t('Ongoing until I stop it', 'जब तक मैं रोकूँ तब तक जारी')}</span></label>{!draft.ongoing && <label>{t('Last day', 'आखिरी दिन')}<input type="date" min={draft.startDate} value={draft.endDate} onChange={(event) => patchCourseEnd(index, event.target.value)} /></label>}</div>
      </div>
      <label className="medicine-prescription-check"><input type="checkbox" checked={draft.confirmed} onChange={(event) => patchDraft(index, { confirmed: event.target.checked })} /><span>{t('I checked this medicine, dose, food instruction and time against the original prescription.', 'मैंने इस दवा, खुराक, खाने के निर्देश और समय को असली पर्चे से जाँच लिया है।')}</span></label>
    </article>)}</div>
    <button type="button" className="medicine-add-manual-row" onClick={() => addManual()}>+ {t('Add another medicine manually', 'एक और दवा खुद जोड़ें')}</button>
    {unmatched.length > 0 && <section className="medicine-unmatched"><h3>{t('Photos that need your help', 'इन तस्वीरों के लिए आपकी मदद चाहिए')}</h3><p>{t('No safe prescription match was found. Enter the medicine from the original prescription or ignore the photo.', 'पर्चे से सुरक्षित मिलान नहीं मिला। असली पर्चे से जानकारी भरें या तस्वीर छोड़ दें।')}</p><div>{unmatched.map((imageIndex) => <article key={imageIndex}><div className="medicine-review-photo"><FileThumbnail file={medicineImages[imageIndex]} alt={t(`Unmatched medicine strip ${imageIndex + 1}`, `बिना-मिलान वाली दवा की तस्वीर ${imageIndex + 1}`)} /></div><span>{medicineImages[imageIndex].name}</span><button type="button" onClick={() => addManual(imageIndex)}>{t('Enter manually', 'खुद भरें')}</button><button type="button" className="medicine-text-button" onClick={() => setUnmatched((items) => items.filter((item) => item !== imageIndex))}>{t('Ignore', 'छोड़ें')}</button></article>)}</div></section>}
    {error && <p className="medicine-error" role="alert">{error}</p>}
    <div className="medicine-form-actions medicine-import-actions"><button type="button" disabled={saving} onClick={() => { setPhase('select'); setError(''); }}>{t('Back to images', 'तस्वीरों पर वापस')}</button><button type="button" className="medicine-primary" disabled={saving} onClick={saveAll}>{saving ? t('Saving…', 'सेव हो रहा है…') : t('Save medicine list', 'दवाओं की सूची सेव करें')}</button></div>
  </div>;
}

function ImageGroup({ id, title, hint, files, chooseLabel, cameraLabel, onChange, onRemove }: {
  id: string; title: string; hint: string; files: File[]; chooseLabel: string; cameraLabel: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void; onRemove: (index: number) => void;
}) {
  return <section className="medicine-image-group"><div className="medicine-image-group-heading"><div><h3>{title}</h3><p>{hint}</p></div><span>{files.length}</span></div>{files.length > 0 && <div className="medicine-image-previews">{files.map((file, index) => <div key={`${file.name}:${file.lastModified}`}><FileThumbnail file={file} alt={`${title} ${index + 1}`} /><button type="button" onClick={() => onRemove(index)} aria-label={`Remove ${file.name}`}>×</button><span>{file.name}</span></div>)}</div>}<div className="medicine-image-buttons"><label htmlFor={`${id}-choose`}>{chooseLabel}<input id={`${id}-choose`} type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={onChange} /></label><label htmlFor={`${id}-camera`}>{cameraLabel}<input id={`${id}-camera`} type="file" accept="image/jpeg,image/png,image/webp" capture="environment" onChange={onChange} /></label></div></section>;
}
