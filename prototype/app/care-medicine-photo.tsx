'use client';

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { loadMedicineImageFile } from './care-medicine-image-store';
import { medicineStoredImageId } from './care-medicine-image-store';
import './care-medicine-photo.css';
import { careText, type CareLang } from './care-i18n';

function ObjectUrlImage({ file, alt, className }: { file: File; alt: string; className: string }) {
  const objectUrl = useRef('');
  const attachImage = useCallback((image: HTMLImageElement | null) => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = '';
    if (!image) return;
    objectUrl.current = URL.createObjectURL(file);
    image.src = objectUrl.current;
  }, [file]);
  return <img ref={attachImage} className={className} alt={alt} />;
}

export function MedicinePhoto({ file, alt, lang, prescription = false }: { file: File; alt: string; lang: CareLang; prescription?: boolean }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const t = (en: string, hi: string) => careText(lang, en, hi);
  return <>
    <button type="button" className="medicine-photo-trigger" aria-label={t(`View photo of ${alt}`, `${alt} की तस्वीर देखें`)} onClick={() => dialog.current?.showModal()}>
      <ObjectUrlImage file={file} className="medicine-card-photo" alt="" />
      <span className="medicine-photo-zoom" aria-hidden="true">↗</span>
    </button>
    <dialog ref={dialog} className="medicine-photo-dialog" aria-labelledby={titleId} onClick={(event) => { if (event.target === event.currentTarget) event.currentTarget.close(); }}>
      <div className="medicine-photo-dialog-panel">
        <header><strong id={titleId}>{alt}</strong><button type="button" onClick={() => dialog.current?.close()}>{t('Close', 'बंद करें')}</button></header>
        <ObjectUrlImage file={file} className="medicine-photo-large" alt={t(`${alt} medicine packaging`, `${alt} दवा की पैकिंग`)} />
        <p>{prescription ? t('Compare every copied word and dose with this original page.', 'हर लिखे शब्द और खुराक को इस मूल पेज से मिलाएँ।') : t('Compare this photo with the medicine in your hand before taking it.', 'दवा लेने से पहले इस तस्वीर को अपने हाथ की दवा से मिलाएँ।')}</p>
      </div>
    </dialog>
  </>;
}

export function StoredPrescriptionPages({ importId, pages, lang }: { importId: string; pages: readonly number[]; lang: CareLang }) {
  const [files, setFiles] = useState<Array<{ page: number; file: File }>>([]);
  const [missing, setMissing] = useState(false);
  const positions = [...new Set(pages)].filter((page) => Number.isInteger(page) && page >= 0 && page < 5).sort((a, b) => a - b).join(',');
  useEffect(() => {
    let active = true;
    const indexes = positions ? positions.split(',').map(Number) : [];
    Promise.all(indexes.map(async (page) => ({ page, file: await loadMedicineImageFile(medicineStoredImageId(importId, 'prescription', page)) }))).then((loaded) => {
      if (!active) return;
      setFiles(loaded.filter((item): item is { page: number; file: File } => Boolean(item.file)));
      setMissing(loaded.some((item) => !item.file));
    }).catch(() => { if (active) setMissing(true); });
    return () => { active = false; };
  }, [importId, positions]);
  if (!positions) return null;
  return <details className="medicine-saved-source"><summary>{careText(lang, 'View original prescription pages', 'मूल पर्चे के पेज देखें')}</summary><div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>{files.map(({ page, file }) => <div key={page}><span>{careText(lang, 'Page', 'पेज')} {page + 1}</span><MedicinePhoto file={file} alt={`${careText(lang, 'Prescription · page', 'पर्चा · पेज')} ${page + 1}`} lang={lang} prescription /></div>)}</div>{missing && <p role="status">{careText(lang, 'An original photo is no longer available on this device. Check your original file.', 'मूल तस्वीर अब इस डिवाइस पर उपलब्ध नहीं है। अपनी मूल फ़ाइल से जाँचें।')}</p>}</details>;
}

function MedicinePhotoFallback() {
  return <span className="medicine-photo-fallback" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m8 4-4 4a5.66 5.66 0 0 0 8 8l4-4a5.66 5.66 0 0 0-8-8Z" /><path d="m6 6 8 8" /></svg></span>;
}

export function StoredMedicinePhoto({ imageId, alt, lang }: { imageId: string; alt: string; lang: CareLang }) {
  const [file, setFile] = useState<File | null>(null);
  useEffect(() => {
    let active = true;
    loadMedicineImageFile(imageId).then((storedFile) => {
      if (active) setFile(storedFile);
    }).catch(() => { if (active) setFile(null); });
    return () => { active = false; };
  }, [imageId]);
  return file ? <MedicinePhoto file={file} alt={alt} lang={lang} /> : <MedicinePhotoFallback />;
}
