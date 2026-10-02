import { inspectCareBillText, type CareBillCheck } from './care-bill-match';
import { extractCareDocumentPdf } from './care-document-extract';

export async function checkCareBill(file: File, hospitalName: string, visitDate: string, signal?: AbortSignal): Promise<CareBillCheck> {
  if (file.size > 4 * 1024 * 1024) throw new Error('Choose a bill smaller than 4 MB.');
  if (signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
  if (file.type === 'application/pdf') {
    const pdf = await extractCareDocumentPdf(file, { signal });
    return inspectCareBillText(pdf.text, hospitalName, visitDate);
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('Choose a JPG, PNG, WebP or PDF bill.');
  let worker: import('tesseract.js').Worker | undefined;
  let finished = false;
  let rejectReading: (error: Error) => void = () => {};
  const interrupted = new Promise<never>((_, reject) => { rejectReading = reject; });
  const cancel = () => rejectReading(new DOMException('Cancelled', 'AbortError'));
  const timeout = setTimeout(() => rejectReading(new Error('Reading took too long. Try a smaller, clearer photo.')), 45000);
  signal?.addEventListener('abort', cancel, { once: true });
  try {
    if (signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
    return await Promise.race([interrupted, (async () => {
      const { createWorker } = await import('tesseract.js');
      if (finished) throw new DOMException('Cancelled', 'AbortError');
      worker = await createWorker('eng', 1, {
        workerPath: '/vendor/tesseract/worker.min.js',
        corePath: '/vendor/tesseract',
        langPath: '/vendor/tesseract',
        logger: () => {},
      });
      if (finished) { await worker.terminate(); throw new DOMException('Cancelled', 'AbortError'); }
      const result = await worker.recognize(file);
      return inspectCareBillText(result.data.text, hospitalName, visitDate);
    })()]);
  } finally {
    finished = true;
    clearTimeout(timeout);
    signal?.removeEventListener('abort', cancel);
    if (worker) void worker.terminate();
  }
}
