'use client';

/** All engine/language assets are served by Sahara. Document pixels stay inside this browser worker. */
export async function readCareDocumentImage(input: File | HTMLCanvasElement, options: { signal?: AbortSignal; onProgress?: (fraction: number) => void } = {}): Promise<string> {
  if (input instanceof File && (!['image/jpeg', 'image/png', 'image/webp'].includes(input.type) || input.size <= 0 || input.size > 25 * 1024 * 1024)) throw new Error('Choose a JPG, PNG or WebP photo smaller than 25 MB.');
  if (options.signal?.aborted) throw new DOMException('Reading cancelled', 'AbortError');
  let worker: import('tesseract.js').Worker | undefined;
  let finished = false;
  let rejectReading: (error: Error) => void = () => {};
  const interrupted = new Promise<never>((_, reject) => { rejectReading = reject; });
  const cancel = () => rejectReading(new DOMException('Reading cancelled', 'AbortError'));
  const timeout = setTimeout(() => rejectReading(new Error('Reading took too long. Try a clearer, smaller photo or enter the words manually.')), 60_000);
  options.signal?.addEventListener('abort', cancel, { once: true });
  let bitmap: ImageBitmap | undefined;
  try {
    return await Promise.race([interrupted, (async () => {
      let image: File | HTMLCanvasElement = input;
      if (input instanceof File) {
        bitmap = await createImageBitmap(input);
        if (finished) { bitmap.close(); bitmap = undefined; throw new DOMException('Reading cancelled', 'AbortError'); }
        const scale = Math.min(1, 2000 / bitmap.width, 2400 / bitmap.height);
        const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        const context = canvas.getContext('2d'); if (!context) throw new Error('This browser cannot read photos. Enter text from the original instead.');
        context.drawImage(bitmap, 0, 0, canvas.width, canvas.height); image = canvas;
      }
      const { createWorker } = await import('tesseract.js');
      if (finished) throw new DOMException('Reading cancelled', 'AbortError');
      worker = await createWorker('eng', 1, {
        workerPath: '/vendor/tesseract/worker.min.js', corePath: '/vendor/tesseract', langPath: '/vendor/tesseract',
        logger: (event) => { if (!finished && event.status === 'recognizing text') options.onProgress?.(Math.max(0, Math.min(1, event.progress))); },
      });
      if (finished) { await worker.terminate(); throw new DOMException('Reading cancelled', 'AbortError'); }
      const result = await worker.recognize(image);
      if (finished) throw new DOMException('Reading cancelled', 'AbortError');
      if (result.data.text.length > 200_000) throw new Error('This page has too much text. Choose a smaller section.');
      return result.data.text;
    })()]);
  } finally { finished = true; clearTimeout(timeout); options.signal?.removeEventListener('abort', cancel); bitmap?.close(); if (worker) void worker.terminate(); }
}
