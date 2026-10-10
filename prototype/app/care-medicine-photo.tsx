'use client';

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { loadMedicineImageFile } from './care-medicine-image-store';
import './care-medicine-photo.css';

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

export function MedicinePhoto({ file, alt, hindi }: { file: File; alt: string; hindi: boolean }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  return <>
    <button type="button" className="medicine-photo-trigger" aria-label={t(`View photo of ${alt}`, `${alt} की तस्वीर देखें`)} onClick={() => dialog.current?.showModal()}>
      <ObjectUrlImage file={file} className="medicine-card-photo" alt="" />
      <span className="medicine-photo-zoom" aria-hidden="true">↗</span>
    </button>
    <dialog ref={dialog} className="medicine-photo-dialog" aria-labelledby={titleId} onClick={(event) => { if (event.target === event.currentTarget) event.currentTarget.close(); }}>
      <div className="medicine-photo-dialog-panel">
        <header><strong id={titleId}>{alt}</strong><button type="button" onClick={() => dialog.current?.close()}>{t('Close', 'बंद करें')}</button></header>
        <ObjectUrlImage file={file} className="medicine-photo-large" alt={t(`${alt} medicine packaging`, `${alt} दवा की पैकिंग`)} />
        <p>{t('Compare this photo with the medicine in your hand before taking it.', 'दवा लेने से पहले इस तस्वीर को अपने हाथ की दवा से मिलाएँ।')}</p>
      </div>
    </dialog>
  </>;
}

function MedicinePhotoFallback() {
  return <span className="medicine-photo-fallback" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m8 4-4 4a5.66 5.66 0 0 0 8 8l4-4a5.66 5.66 0 0 0-8-8Z" /><path d="m6 6 8 8" /></svg></span>;
}

export function StoredMedicinePhoto({ imageId, alt, hindi }: { imageId: string; alt: string; hindi: boolean }) {
  const [file, setFile] = useState<File | null>(null);
  useEffect(() => {
    let active = true;
    loadMedicineImageFile(imageId).then((storedFile) => {
      if (active) setFile(storedFile);
    }).catch(() => { if (active) setFile(null); });
    return () => { active = false; };
  }, [imageId]);
  return file ? <MedicinePhoto file={file} alt={alt} hindi={hindi} /> : <MedicinePhotoFallback />;
}
