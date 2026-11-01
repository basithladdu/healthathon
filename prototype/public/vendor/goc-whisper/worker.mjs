// Sahara: on-device speech to text for the goals-of-care conversation. This file is the only thing Sahara serves for it.
// It runs only after the doctor taps Transcribe, and receives its settings from app/goc-transcribe-model.ts.
// The engine is fetched from a version-pinned CDN URL and checked against a SHA-384 before it runs. Audio samples arrive
// one chunk at a time by postMessage and are never sent anywhere: the only network calls here fetch the engine and the weights.

async function fetchVerified(file, onBytes) {
  const response = await fetch(file.url, { mode: 'cors', credentials: 'omit' });
  if (!response.ok || !response.body) throw Object.assign(new Error(`HTTP ${response.status} for ${file.url}`), { code: 'network' });
  const reader = response.body.getReader();
  const chunks = [];
  let loaded = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    loaded += value.byteLength;
    onBytes(loaded);
  }
  const bytes = new Uint8Array(loaded);
  let at = 0;
  for (const chunk of chunks) { bytes.set(chunk, at); at += chunk.byteLength; }
  const digest = await crypto.subtle.digest('SHA-384', bytes);
  const hex = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
  if (hex !== file.sha384) throw Object.assign(new Error(`Checksum mismatch for ${file.url}`), { code: 'integrity' });
  return URL.createObjectURL(new Blob([bytes], { type: file.type }));
}

let transcriber = null;
let loadedModel = '';

async function load(config) {
  if (transcriber && loadedModel === config.modelId) return;
  const bytes = {};
  const report = (name, loaded) => {
    bytes[name] = loaded;
    self.postMessage({ type: 'download', loaded: Object.values(bytes).reduce((n, v) => n + v, 0) });
  };
  const [library, wasm, factory] = await Promise.all([
    fetchVerified(config.engine.library, (n) => report('library', n)),
    fetchVerified(config.engine.wasm, (n) => report('wasm', n)),
    fetchVerified(config.engine.factory, (n) => report('factory', n)),
  ]);
  const { env, pipeline } = await import(library);
  env.allowLocalModels = false;
  env.useBrowserCache = true;
  env.useWasmCache = false;
  env.backends.onnx.wasm.wasmPaths = { mjs: factory, wasm };
  // Sahara does not set COOP/COEP, so the page is not cross-origin isolated and WebAssembly threads are unavailable.
  env.backends.onnx.wasm.numThreads = self.crossOriginIsolated ? Math.min(4, navigator.hardwareConcurrency || 1) : 1;
  transcriber = await pipeline('automatic-speech-recognition', config.modelId, {
    revision: config.revision,
    device: 'wasm',
    dtype: config.dtype,
    progress_callback: (event) => {
      if (event.status === 'progress' && event.file) report(event.file, event.loaded ?? 0);
    },
  });
  loadedModel = config.modelId;
}

// Whisper's language tokens, and the five languages Sahara works in. This library writes English when no language is given, so "auto" has
// to ask the model first: one decoder step after the start token, with every other language blocked.
const FIRST_LANGUAGE_TOKEN = 50259;
const LAST_LANGUAGE_TOKEN = 50357;
const START_TOKEN = 50258;
const SAHARA_LANGUAGES = new Map([[50259, 'en'], [50276, 'hi'], [50290, 'ur'], [50299, 'te'], [50306, 'kn']]);
const BLOCKED_LANGUAGES = [];
for (let id = FIRST_LANGUAGE_TOKEN; id <= LAST_LANGUAGE_TOKEN; id++) if (!SAHARA_LANGUAGES.has(id)) BLOCKED_LANGUAGES.push(id);

async function detectLanguage(samples) {
  const { input_features } = await transcriber.processor(samples);
  const output = await transcriber.model.generate({ inputs: input_features, decoder_input_ids: [[START_TOKEN]], max_new_tokens: 1, suppress_tokens: BLOCKED_LANGUAGES });
  const ids = output[0].tolist().map(Number);
  return SAHARA_LANGUAGES.get(ids[ids.length - 1]) ?? 'en';
}

function classify(error) {
  const message = String(error?.message ?? error);
  if (error?.code) return error.code;
  if (/memory|allocat|out of bounds|OOM/i.test(message)) return 'memory';
  if (/fetch|network|load|HTTP|Failed to/i.test(message)) return 'network';
  return 'engine';
}

self.onmessage = async ({ data }) => {
  try {
    if (data?.type === 'load') {
      const started = performance.now();
      await load(data.config);
      self.postMessage({ type: 'loaded', ms: performance.now() - started });
    } else if (data?.type === 'chunk') {
      const started = performance.now();
      const language = data.language || await detectLanguage(data.samples);
      const result = await transcriber(data.samples, { task: 'transcribe', language, return_timestamps: false });
      self.postMessage({ type: 'chunk-done', index: data.index, language, text: String(result?.text ?? '').trim(), ms: performance.now() - started });
    }
  } catch (error) {
    self.postMessage({ type: 'error', code: classify(error), message: String(error?.message ?? error) });
  }
};
