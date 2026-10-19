export type CarePdfText = {
  text: string;
  pagesRead: number;
  pageCount: number;
  pagesWithoutText: number[];
  ocrPages?: number[];
};
export type CarePdfReadErrorCode = 'not-pdf' | 'password' | 'unreadable' | 'too-much-text';

export class CarePdfReadError extends Error {
  constructor(public code: CarePdfReadErrorCode) {
    super(code);
    this.name = 'CarePdfReadError';
  }
}

export async function extractCareDocumentPdf(file: File, options: {
  signal?: AbortSignal;
  onProgress?: (page: number, total: number) => void;
} = {}): Promise<CarePdfText> {
  if (file.type !== 'application/pdf' && !/\.pdf$/i.test(file.name)) throw new CarePdfReadError('not-pdf');
  const cancelled = () => {
    if (options.signal?.aborted) throw new DOMException('Reading cancelled', 'AbortError');
  };
  cancelled();
  const pdfjs = await import('pdfjs-dist');
  cancelled();
  pdfjs.GlobalWorkerOptions.workerSrc = new URL('/vendor/pdf.worker.min.mjs', window.location.origin).href;
  const data = new Uint8Array(await file.arrayBuffer());
  cancelled();
  const task = pdfjs.getDocument({
    data, disableFontFace: true, useSystemFonts: true, useWasm: false,
    useWorkerFetch: false, stopAtErrors: true, verbosity: 0, enableXfa: false,
  });
  let cleanup: Promise<void> | undefined;
  const destroy = () => { cleanup ??= task.destroy().catch(() => undefined); return cleanup; };
  const abort = () => { void destroy(); };
  options.signal?.addEventListener('abort', abort, { once: true });
  try {
    cancelled();
    const pdf = await task.promise;
    const pagesRead = Math.min(12, pdf.numPages);
    const pages: string[] = [];
    const pagesWithoutText: number[] = [];
    const ocrPages: number[] = [];
    let totalCharacters = 0;
    options.onProgress?.(0, pagesRead);
    for (let pageNumber = 1; pageNumber <= pagesRead; pageNumber += 1) {
      cancelled();
      const page = await pdf.getPage(pageNumber);
      try {
        const content = await page.getTextContent({ disableNormalization: true });
        cancelled();
        const lines: string[] = [];
        let line = '';
        let lastY: number | undefined;
        let lastEndX: number | undefined;
        const flush = () => { if (line.trim()) lines.push(line.trim()); line = ''; lastEndX = undefined; };
        for (const item of content.items) {
          if (!('str' in item)) continue;
          const y = Number(item.transform[5]);
          const x = Number(item.transform[4]);
          if (lastY !== undefined && Math.abs(y - lastY) > 2) flush();
          if (line && lastEndX !== undefined && x - lastEndX > .75 && !/\s$/.test(line) && !/^\s/.test(item.str)) line += ' ';
          line += item.str;
          lastY = y;
          lastEndX = x + item.width;
          if (item.hasEOL) flush();
        }
        flush();
        let pageText = lines.join('\n');
        if (!pageText.trim()) {
          const viewport = page.getViewport({ scale: 1 });
          const scale = Math.min(1600 / viewport.width, 2000 / viewport.height);
          const rendered = page.getViewport({ scale });
          const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(rendered.width)); canvas.height = Math.max(1, Math.round(rendered.height));
          const context = canvas.getContext('2d');
          if (context) {
            const render = page.render({ canvasContext: context, canvas, viewport: rendered });
            const cancelRender = () => render.cancel(); options.signal?.addEventListener('abort', cancelRender, { once: true });
            try {
              await render.promise; cancelled();
              const { readCareDocumentImage } = await import('./care-document-ocr');
              pageText = await readCareDocumentImage(canvas, { signal: options.signal });
              ocrPages.push(pageNumber);
            } catch (error) { cancelled(); if (error instanceof DOMException && error.name === 'AbortError') throw error; /* Preserve original and continue with explicitly missing text. */ }
            finally { options.signal?.removeEventListener('abort', cancelRender); canvas.width = 1; canvas.height = 1; }
          }
        }
        totalCharacters += pageText.length + 20;
        if (totalCharacters > 200000) throw new CarePdfReadError('too-much-text');
        if (!pageText.trim()) pagesWithoutText.push(pageNumber);
        pages.push(`[Page ${pageNumber}]\n${pageText}`);
        options.onProgress?.(pageNumber, pagesRead);
      } finally {
        page.cleanup();
      }
    }
    return { text: pagesWithoutText.length === pagesRead ? '' : pages.join('\n\n'), pagesRead, pageCount: pdf.numPages, pagesWithoutText, ocrPages };
  } catch (error) {
    cancelled();
    if (error instanceof CarePdfReadError) throw error;
    if (error && typeof error === 'object' && 'name' in error && error.name === 'PasswordException') throw new CarePdfReadError('password');
    throw new CarePdfReadError('unreadable');
  } finally {
    options.signal?.removeEventListener('abort', abort);
    await destroy();
  }
}
