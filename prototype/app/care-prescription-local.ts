'use client';

import { readCareDocumentImage } from './care-document-ocr';
import { copiedPrescriptionPages } from './care-prescription-local-state';
import type { CareMedicineAiProgress, CareMedicineAiResult } from './care-medicine-ai';

/** Local OCR supplies copied candidates only. Package matching and dose selection stay with the reviewer. */
export async function readPrescriptionLocally(prescriptions: File[], packaging: File[], options: { signal?: AbortSignal; onProgress?: (progress: CareMedicineAiProgress) => void } = {}): Promise<CareMedicineAiResult> {
  if (!prescriptions.length || prescriptions.length > 5 || packaging.length > 10) throw new Error('Choose one to five prescription pages. Packaging photos are optional.');
  const pages: string[] = [];
  for (let index = 0; index < prescriptions.length; index += 1) {
    if (options.signal?.aborted) throw new DOMException('Reading cancelled', 'AbortError');
    options.onProgress?.({ stage: 'preparing', completed: index, total: prescriptions.length, fileName: prescriptions[index].name });
    const text = await readCareDocumentImage(prescriptions[index], { signal: options.signal });
    pages.push(text);
    options.onProgress?.({ stage: 'preparing', completed: index + 1, total: prescriptions.length, fileName: prescriptions[index].name });
  }
  return { ...copiedPrescriptionPages(pages, packaging.length),
    preparedPrescriptionPages: prescriptions, preparedMedicineImages: packaging };
}
