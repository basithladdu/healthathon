// Loads and runs on-device speech to text for the goals-of-care conversation. Nothing here runs until the doctor taps Transcribe.
// The engine and model are not in the app bundle or the repo. See docs/FREE_MODELS.md, "On-device speech to text", for the comparison.

export const GOC_SAMPLE_RATE = 16000;
const TRANSFORMERS_VERSION = '4.3.1';
const ORT_VERSION = '1.31.0-dev.20260914-8d85527a0';
const CDN = 'https://cdn.jsdelivr.net/npm';

/** Exact file checksums: the worker refuses to run anything else. */
export const GOC_ENGINE = {
  library: { url: `${CDN}/@huggingface/transformers@${TRANSFORMERS_VERSION}/dist/transformers.min.js`, sha384: '00536d94d3addaf6f282d9510945f64c382413e132a278db08bc407ea7b36302490d5968cd4860fb22ace1c3f0ca31d1', type: 'text/javascript' },
  wasm: { url: `${CDN}/onnxruntime-web@${ORT_VERSION}/dist/ort-wasm-simd-threaded.wasm`, sha384: '2eb38f7b5c8db0b57386cae2b907f13b4e915b1fd76a44744bcba238cc7b1d524fe8dfc811a4751008093057936a789f', type: 'application/wasm' },
  factory: { url: `${CDN}/onnxruntime-web@${ORT_VERSION}/dist/ort-wasm-simd-threaded.mjs`, sha384: 'af081d51e232a9f77697132e85da9760f9d98a5b83a92339962cffabeba522de2f62d079d8d0a84d5581926d4a14e78d', type: 'text/javascript' },
} as const;

/** Bytes were measured in the browser with an empty cache on 2026-11-01, engine included. They drive the first-time note and the progress bar. */
export const GOC_MODELS = {
  base: { modelId: 'onnx-community/whisper-base', revision: '1846881b6b3a3024392c1eea3ad983695bc23925', dtype: { encoder_model: 'q8', decoder_model_merged: 'q8' }, bytes: 94_539_640 },
  small: { modelId: 'onnx-community/whisper-small', revision: '36050c46d777d46dc4b5f43f6d90574fc38f8732', dtype: { encoder_model: 'q8', decoder_model_merged: 'q8' }, bytes: 266_722_062 },
} as const;
export type GocModelKey = keyof typeof GOC_MODELS;
export const GOC_MODEL_DOWNLOAD_BYTES = GOC_MODELS.base.bytes;

/** Small wrote Hindi in Devanagari with far fewer errors, where base writes Latin letters. It is about three times slower and no real help elsewhere, so everything else starts on base. */
export function gocDefaultModel(language: string): GocModelKey {
  return language === 'hi' ? 'small' : 'base';
}
/** Longest audio the screen accepts. See the measured numbers in docs/FREE_MODELS.md. */
export const GOC_LONGEST_SECONDS = 3600;

const WINDOW = 30 * GOC_SAMPLE_RATE;
const SEARCH = 5 * GOC_SAMPLE_RATE;
const FRAME = GOC_SAMPLE_RATE / 50;
/** Average level under which a chunk has no speech. Whisper invents words for silence, so silent chunks are skipped. */
const SILENCE_RMS = 0.004;

function rms(samples: Float32Array, start: number, end: number) {
  let sum = 0;
  const n = Math.max(1, end - start);
  for (let i = start; i < end; i++) sum += samples[i] * samples[i];
  return Math.sqrt(sum / n);
}

export type GocChunk = { start: number; end: number; silent: boolean };

/** Splits long audio into pieces of at most 30 seconds, cutting at the quietest moment near the end of each piece so words stay whole. */
export function planChunks(samples: Float32Array): GocChunk[] {
  const out: GocChunk[] = [];
  let start = 0;
  while (start < samples.length) {
    let end = Math.min(samples.length, start + WINDOW);
    if (end < samples.length) {
      let best = end; let lowest = Infinity;
      for (let at = end - FRAME; at >= end - SEARCH; at -= FRAME) {
        const level = rms(samples, at, at + FRAME);
        if (level < lowest - 1e-6) { lowest = level; best = at + FRAME; }
      }
      end = best;
    }
    out.push({ start, end, silent: rms(samples, start, end) < SILENCE_RMS });
    start = end;
  }
  return out;
}

export type GocTranscribeError = 'offline' | 'memory' | 'unsupported' | 'too-long' | 'decode' | 'engine' | 'network' | 'integrity';
export class GocSpeechError extends Error {
  code: GocTranscribeError;
  constructor(code: GocTranscribeError, detail = '') { super(detail || code); this.code = code; }
}

/** Decodes a recording or uploaded file to 16 kHz mono. An hour takes about 230 MB of memory while it is held here. */
export async function decodeToSamples(file: Blob): Promise<Float32Array> {
  if (typeof OfflineAudioContext === 'undefined') throw new GocSpeechError('unsupported');
  const buffer = await file.arrayBuffer();
  let decoded: AudioBuffer;
  try {
    decoded = await new OfflineAudioContext(1, 1, GOC_SAMPLE_RATE).decodeAudioData(buffer);
  } catch (error) {
    throw new GocSpeechError(error instanceof RangeError ? 'memory' : 'decode', String(error));
  }
  if (decoded.duration > GOC_LONGEST_SECONDS + 60) throw new GocSpeechError('too-long');
  if (decoded.numberOfChannels === 1) return decoded.getChannelData(0);
  const mono = new Float32Array(decoded.length);
  for (let c = 0; c < decoded.numberOfChannels; c++) {
    const channel = decoded.getChannelData(c);
    for (let i = 0; i < mono.length; i++) mono[i] += channel[i] / decoded.numberOfChannels;
  }
  return mono;
}

export type GocProgress =
  | { stage: 'download'; loaded: number; total: number }
  | { stage: 'transcribe'; done: number; total: number; secondsLeft: number | null; speed: number | null };

export type GocRunOptions = {
  /** 'auto' asks the model which of Sahara's five languages each 30 second piece is in, which suits mixed-language talk but takes longer. */
  language: string;
  model?: GocModelKey;
  signal?: AbortSignal;
  onProgress: (progress: GocProgress) => void;
  /** Called with each finished piece, so a stopped or failed run keeps what it already has. */
  onText: (text: string) => void;
};

/** Runs the whole recording, one piece at a time. Finished pieces survive a cancel or a failure. */
export async function transcribeSamples(samples: Float32Array, options: GocRunOptions): Promise<{ seconds: number; ms: number }> {
  if (typeof Worker === 'undefined' || typeof WebAssembly === 'undefined') throw new GocSpeechError('unsupported');
  if (samples.length / GOC_SAMPLE_RATE > GOC_LONGEST_SECONDS + 60) throw new GocSpeechError('too-long');
  const model = GOC_MODELS[options.model ?? 'base'];
  const worker = new Worker('/vendor/goc-whisper/worker.mjs', { type: 'module' });
  const pending: { resolve?: (value: { text: string }) => void; reject?: (error: Error) => void } = {};
  const stop = () => pending.reject?.(new DOMException('Cancelled', 'AbortError'));
  options.signal?.addEventListener('abort', stop, { once: true });
  worker.onerror = (event) => pending.reject?.(new GocSpeechError('engine', event.message));
  worker.onmessage = ({ data }) => {
    if (data.type === 'download') options.onProgress({ stage: 'download', loaded: data.loaded, total: model.bytes });
    else if (data.type === 'error') {
      const offline = typeof navigator !== 'undefined' && navigator.onLine === false;
      pending.reject?.(new GocSpeechError(offline ? 'offline' : data.code, data.message));
    } else if (data.type === 'loaded' || data.type === 'chunk-done') pending.resolve?.({ text: data.text ?? '' });
  };
  const ask = (message: unknown, transfer: Transferable[] = []) => new Promise<{ text: string }>((resolve, reject) => {
    pending.resolve = resolve; pending.reject = reject;
    if (options.signal?.aborted) { reject(new DOMException('Cancelled', 'AbortError')); return; }
    worker.postMessage(message, transfer);
  });
  try {
    await ask({ type: 'load', config: { modelId: model.modelId, revision: model.revision, dtype: model.dtype, engine: GOC_ENGINE } });
    const chunks = planChunks(samples);
    const spoken = chunks.filter((c) => !c.silent);
    const totalSpoken = spoken.reduce((n, c) => n + c.end - c.start, 0);
    const began = performance.now();
    let doneSamples = 0; let done = 0;
    options.onProgress({ stage: 'transcribe', done: 0, total: spoken.length, secondsLeft: null, speed: null });
    for (const chunk of spoken) {
      const piece = samples.slice(chunk.start, chunk.end);
      const result = await ask({ type: 'chunk', index: done, samples: piece, language: options.language === 'auto' ? null : options.language }, [piece.buffer]);
      if (result.text.trim()) options.onText(result.text.trim());
      done += 1; doneSamples += chunk.end - chunk.start;
      const spent = (performance.now() - began) / 1000;
      const speed = (doneSamples / GOC_SAMPLE_RATE) / spent;
      options.onProgress({ stage: 'transcribe', done, total: spoken.length, speed, secondsLeft: ((totalSpoken - doneSamples) / GOC_SAMPLE_RATE) / speed });
    }
    return { seconds: samples.length / GOC_SAMPLE_RATE, ms: performance.now() - began };
  } finally {
    options.signal?.removeEventListener('abort', stop);
    worker.terminate();
  }
}
