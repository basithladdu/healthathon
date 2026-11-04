// Try online goals-of-care transcription on a local audio file, through the same flow the browser uses
// (start → direct upload to Google → Gemini transcribes each window → delete).
//
// Needs ffmpeg on PATH (to decode the file to 16 kHz mono, as the browser does). Node 22.13+.
//
//   In-process, with keys from your shell (nothing is printed about them):
//     SAHARA_AI_API_KEY=... node --experimental-strip-types scripts/goc-transcribe-try.mjs talk.m4a --local --lang hi
//   Against a running server (next dev, or a Vercel preview whose env has the keys):
//     node --experimental-strip-types scripts/goc-transcribe-try.mjs talk.m4a --base http://localhost:3000
//     node --experimental-strip-types scripts/goc-transcribe-try.mjs talk.m4a --base https://<preview>.vercel.app --bypass <protection-bypass-secret>
//   Options: --lang en|hi|te|ur|kn|auto (default auto), --out result.json
import { spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import { extname } from 'node:path';
import { fetchHttp, runGocOnline, samplesSource, gocOnlineEngines } from '../app/goc-gemini-client.ts';
import { createGocTranscribeHandler } from '../app/goc-transcribe-server.ts';

const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const option = (name, fallback = '') => { const at = args.indexOf(`--${name}`); return at >= 0 && args[at + 1] ? args[at + 1] : fallback; };
const path = args.find((arg) => !arg.startsWith('--') && !args[args.indexOf(arg) - 1]?.startsWith('--'));
if (!path) { console.error('Usage: node --experimental-strip-types scripts/goc-transcribe-try.mjs <audio file> (--local | --base <url>) [--lang hi] [--out file.json]'); process.exit(2); }

const MIME = { '.wav': 'audio/wav', '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.mp4': 'audio/mp4', '.aac': 'audio/aac', '.ogg': 'audio/ogg', '.opus': 'audio/ogg', '.flac': 'audio/flac', '.webm': 'audio/webm', '.aiff': 'audio/aiff' };

function decode(file) {
  return new Promise((resolve, reject) => {
    const ffmpeg = spawn('ffmpeg', ['-v', 'error', '-i', file, '-ac', '1', '-ar', '16000', '-f', 'f32le', 'pipe:1']);
    const chunks = []; let errors = '';
    ffmpeg.stdout.on('data', (chunk) => chunks.push(chunk)); ffmpeg.stderr.on('data', (chunk) => { errors += chunk; });
    ffmpeg.on('error', reject);
    ffmpeg.on('close', (code) => {
      if (code !== 0) { reject(new Error(`ffmpeg failed: ${errors.trim()}`)); return; }
      const bytes = Buffer.concat(chunks);
      resolve(new Float32Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.length / 4)).slice());
    });
  });
}

let http;
if (flag('local')) {
  const origin = 'http://localhost';
  const handler = createGocTranscribeHandler(fetch, process.env);
  const route = `${origin}/api/goc-transcribe`;
  const read = async (response) => ({ status: response.status, body: await response.json().catch(() => ({})) });
  http = {
    info: async () => (await handler(new Request(route))).json(),
    json: async (body) => read(await handler(new Request(route, { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: origin }, body: JSON.stringify(body) }))),
    binary: async (query, data, headers) => read(await handler(new Request(`${route}?${new URLSearchParams(query)}`, { method: 'POST', body: data,
      headers: { 'Content-Type': data.type, 'X-Sahara-Consent': 'online-transcription', Origin: origin, ...headers } }))),
    upload: fetchHttp().upload,
  };
} else {
  const base = option('base').replace(/\/+$/u, '');
  if (!base) { console.error('Give --local or --base <url>.'); process.exit(2); }
  const bypass = option('bypass');
  http = fetchHttp(base, { Origin: base, ...(bypass ? { 'x-vercel-protection-bypass': bypass } : {}) });
}

const engines = await gocOnlineEngines(http);
console.error(`Gemini on the server: ${engines.gemini}. Consent line: "${engines.consentLine}"`);
if (!engines.gemini) { console.error('Gemini is not set up there (SAHARA_AI_API_KEY or GEMINI_API_KEY).'); process.exit(1); }

const original = new Blob([await readFile(path)], { type: MIME[extname(path).toLowerCase()] || 'application/octet-stream' });
const samples = await decode(path);
console.error(`Decoded ${(samples.length / 16000).toFixed(1)} s of audio from ${path} (${(original.size / 1e6).toFixed(2)} MB).`);
const started = Date.now();
let lastShown = -1;
try {
  const result = await runGocOnline(samplesSource(samples, original), {
    lang: option('lang', 'auto'),
    onProgress: (p) => { const pct = Math.floor(p * 100); if (pct !== lastShown) { lastShown = pct; process.stderr.write(`\r${pct}%   `); } },
  }, { http });
  process.stderr.write('\n');
  console.error(`Transcribed by ${result.engine} (${result.model}) in ${((Date.now() - started) / 1000).toFixed(1)} s: ${result.lines.length} lines.`);
  for (const line of result.lines) {
    const m = Math.floor(line.startSec / 60); const s = (line.startSec % 60).toFixed(1).padStart(4, '0');
    console.log(`[${m}:${s}] ${line.speaker.padEnd(13)} ${line.lang.padEnd(5)} ${line.text}`);
  }
  if (option('out')) await writeFile(option('out'), JSON.stringify(result, null, 2), 'utf8');
} catch (error) {
  process.stderr.write('\n');
  console.error(`Failed: ${error.code ?? ''} ${error.message}`);
  if (error.partial?.length) console.error(`${error.partial.length} lines were finished before it stopped.`);
  process.exit(1);
}
