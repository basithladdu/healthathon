// Goals-of-care transcription by Sarvam (android/supabase/functions/sahara-care-assist/goc-sarvam.ts), with Google Gemini as the fallback.
import test from 'node:test';
import assert from 'node:assert/strict';
import { gocConfiguration, handleGoc, GEMINI_UPLOAD } from '../../android/supabase/functions/sahara-care-assist/goc-gemini.ts';
import { sarvamEntries, shortLang, sarvamLanguage, putSarvamChunk } from '../../android/supabase/functions/sahara-care-assist/goc-sarvam.ts';

const KEY = 'edge-gemini-key'; const SKEY = 'edge-sarvam-key';
const env = (values) => (name) => values[name] ?? '';
const BASE = { SAHARA_AI_BASE_URL: 'https://generativelanguage.googleapis.com/v1beta/openai', SAHARA_AI_API_KEY: KEY, SAHARA_AI_MODEL: 'gemini-3.1-flash-lite' };
const CONFIG = gocConfiguration(env({ ...BASE, SARVAM_API_KEY: SKEY }));
const RID = '0f8b7a3e-2c4d-4e6f-8a9b-1c2d3e4f5a6b';
const BLOB = 'https://sarvamstore.blob.core.windows.net/jobs/J1/window.aac?sv=2024&sig=SECRET';
const post = (action, body, headers = {}) => new Request(`https://x.supabase.co/functions/v1/sahara-care-assist/${action}`, { method: 'POST',
  headers: { 'Content-Type': 'application/json', ...headers }, body: body instanceof Uint8Array ? body : JSON.stringify(body) });

/** Google (resumable upload, files, generateContent) and Sarvam (batch jobs, Azure blob storage) in one fake fetch. */
function services({ sarvamStates = ['Accepted', 'Running', 'Completed'], sarvamResult, createFails = false } = {}) {
  const calls = []; const files = new Map(); const sessions = new Map(); const blocks = new Map(); let blob = null; let next = 0;
  const states = [...sarvamStates];
  const result = sarvamResult ?? { language_code: 'hi-IN', transcript: 'x', diarized_transcript: { entries: [
    { transcript: 'What matters most to you now?', start_time_seconds: 2.5, end_time_seconds: 5, speaker_id: '0' },
    { transcript: 'मुझे घर पर रहना है', start_time_seconds: 6, end_time_seconds: 8, speaker_id: '1' },
    { transcript: 'ಅವಳು ನೋವಿನಲ್ಲಿ ಇದ್ದಾಳೆ', start_time_seconds: 9, end_time_seconds: 11, speaker_id: '1' }] } };
  const send = async (url, init = {}) => {
    const text = String(url); const headers = new Headers(init.headers || {}); const method = init.method || 'GET';
    calls.push({ url: text, method });
    if (text.startsWith('https://api.sarvam.ai')) {
      assert.equal(headers.get('api-subscription-key'), SKEY);
      const path = new URL(text).pathname; const body = init.body ? JSON.parse(init.body) : null;
      if (path === '/speech-to-text/job/v1') {
        if (createFails) return new Response('{"error":{"code":"invalid_api_key_error"}}', { status: 403 });
        assert.equal(body.job_parameters.model, 'saaras:v4'); assert.equal(body.job_parameters.with_diarization, true); assert.equal(body.job_parameters.language_code, 'unknown');
        return Response.json({ job_id: 'J1', storage_container_type: 'Azure', job_state: 'Accepted' }, { status: 202 });
      }
      if (path.endsWith('/upload-files')) return Response.json({ job_id: 'J1', upload_urls: { [body.files[0]]: { file_url: BLOB } } });
      if (path.endsWith('/status')) return Response.json({ job_state: states.length > 1 ? states.shift() : states[0], job_details: [{ outputs: [{ file_name: '0.json' }] }] });
      if (path.endsWith('/start')) { if (states[0] === 'Accepted') states.shift(); return Response.json({ job_state: 'Running' }); }
      if (path.endsWith('/download-files')) return Response.json({ download_urls: { '0.json': { file_url: 'https://sarvamstore.blob.core.windows.net/out/0.json?sig=X' } } });
    }
    if (text.startsWith('https://sarvamstore.blob.core.windows.net/out/')) return Response.json(result);
    if (text.startsWith('https://sarvamstore.blob.core.windows.net/jobs/')) {
      const q = new URL(text).searchParams; const bytes = new Uint8Array(await new Response(init.body).arrayBuffer());
      if (q.get('comp') === 'block') blocks.set(q.get('blockid'), bytes);
      else if (q.get('comp') === 'blocklist') { const ids = [...new TextDecoder().decode(bytes).matchAll(/<Latest>([^<]+)<\/Latest>/gu)].map((m) => m[1]); blob = ids.flatMap((id) => [...blocks.get(id)]).length; }
      else { assert.equal(headers.get('x-ms-blob-type'), 'BlockBlob'); blob = bytes.length; }
      return new Response('', { status: 201 });
    }
    if (text === GEMINI_UPLOAD) {
      const id = `UP${++next}`; sessions.set(id, { display: JSON.parse(init.body).file.display_name, mime: headers.get('x-goog-upload-header-content-type'), size: 0 });
      return new Response('{}', { headers: { 'x-goog-upload-url': `https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=${id}&upload_protocol=resumable`, 'x-goog-upload-chunk-granularity': '262144' } });
    }
    if (text.includes('/upload/v1beta/files?')) {
      const session = sessions.get(new URL(text).searchParams.get('upload_id'));
      session.size += new Uint8Array(await new Response(init.body).arrayBuffer()).length;
      if (!headers.get('x-goog-upload-command').includes('finalize')) return new Response('', { headers: { 'x-goog-upload-status': 'active' } });
      const name = `files/f${next}`;
      const file = { name, uri: `https://generativelanguage.googleapis.com/v1beta/${name}`, mimeType: session.mime, state: 'ACTIVE', sizeBytes: String(session.size), displayName: session.display };
      files.set(name, file); return Response.json({ file }, { headers: { 'x-goog-upload-status': 'final' } });
    }
    const match = /\/v1beta\/(files\/[a-z0-9-]+)$/u.exec(text);
    if (match && method === 'GET') return files.has(match[1]) ? Response.json(files.get(match[1])) : new Response('{}', { status: 404 });
    if (match && method === 'DELETE') { files.delete(match[1]); return new Response('{}'); }
    if (text.includes(':generateContent')) {
      if (String(init.body).includes('speaker id')) return Response.json({ candidates: [{ content: { parts: [{ text: '{"speakers":{"0":"DOCTOR","1":"PATIENT_PARTY"}}' }] } }] });
      return Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify({ lines: [{ startSec: 3, speaker: 'DOCTOR', lang: 'en', text: 'Gemini line' }] }) }] } }] });
    }
    throw new Error(`unexpected ${text}`);
  };
  return { send, calls, files, blob: () => blob };
}

async function record(s, bytes = 5_000_000) {
  const deps = { config: CONFIG, send: s.send, userId: 'u1', reserve: async () => {}, pause: async () => {} };
  const start = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes }), deps);
  assert.equal(start.status, 200); assert.equal(start.body.mode, 'relay');
  assert.ok(!JSON.stringify(start.body).includes('SECRET'), 'the Sarvam storage URL stays sealed');
  const audio = new Uint8Array(bytes).fill(7); let file = null;
  for (let offset = 0; offset < bytes; offset += start.body.chunkBytes) {
    const part = audio.slice(offset, offset + start.body.chunkBytes); const final = offset + part.length >= bytes;
    const chunk = await handleGoc('goc-transcribe-chunk', post('goc-transcribe-chunk', part, { 'x-sahara-consent': 'online-transcription', 'x-goc-ticket': start.body.ticket,
      'x-goc-offset': String(offset), 'x-goc-final': final ? '1' : '0', 'content-length': String(part.length) }), deps);
    assert.equal(chunk.status, 200); if (chunk.body.file) file = chunk.body.file;
  }
  const ask = () => handleGoc('goc-transcribe-window', post('goc-transcribe-window', { consent: true, ticket: start.body.ticket, file, lang: 'auto', offsetSec: 300, overlapSec: 0, durationSec: 300 }), deps);
  return { ask, deps };
}

test('Sarvam transcribes the window in any language, speakers are named, and the Gemini copy is deleted', async () => {
  const s = services();
  const { ask } = await record(s);
  assert.equal(s.blob(), 5_000_000, 'both chunks reached Sarvam storage as committed blocks');
  const answer = await ask();
  assert.equal(answer.status, 200); assert.equal(answer.body.engine, 'sarvam'); assert.equal(answer.body.model, 'saaras:v4');
  assert.deepEqual(answer.body.lines.map((l) => [l.startSec, l.speaker, l.lang]), [[302.5, 'DOCTOR', 'hi'], [306, 'PATIENT_PARTY', 'hi'], [309, 'PATIENT_PARTY', 'hi']]);
  assert.equal(answer.body.lines[2].text, 'ಅವಳು ನೋವಿನಲ್ಲಿ ಇದ್ದಾಳೆ');
  assert.equal(s.files.size, 0);
  assert.ok(!s.calls.some((c) => c.url.includes(':generateContent') && !c.url.includes('flash-lite')), 'only the short speaker call went to Gemini');
});

test('while Sarvam is still working the phone is asked to wait (202) and the audio is kept', async () => {
  const s = services({ sarvamStates: ['Accepted', 'Running'] });
  const { ask, deps } = await record(s, 1000);
  let t = 0; deps.now = () => (t += 30_000);
  const answer = await ask();
  assert.equal(answer.status, 202); assert.equal(answer.body.pending, true); assert.equal(s.files.size, 1);
});

test('a failed Sarvam job falls back to Gemini for that window', async () => {
  const s = services({ sarvamStates: ['Failed'] });
  const { ask } = await record(s, 1000);
  const answer = await ask();
  assert.equal(answer.status, 200); assert.equal(answer.body.engine, 'gemini'); assert.equal(answer.body.lines[0].text, 'Gemini line');
});

test('if Sarvam refuses the job, the recording goes to Gemini exactly as before', async () => {
  const s = services({ createFails: true });
  const deps = { config: CONFIG, send: s.send, userId: 'u1', reserve: async () => {}, pause: async () => {} };
  const start = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 1000 }), deps);
  assert.equal(start.body.mode, 'direct');
});

test('without SARVAM_API_KEY nothing changes', async () => {
  const s = services();
  const deps = { config: gocConfiguration(env(BASE)), send: s.send, userId: 'u1', reserve: async () => {}, pause: async () => {} };
  const start = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 1000 }), deps);
  assert.equal(start.body.mode, 'direct'); assert.ok(!s.calls.some((c) => c.url.includes('sarvam')));
});

test('language codes, result shapes and single-chunk uploads', async () => {
  assert.equal(sarvamLanguage('hi'), 'hi-IN'); assert.equal(sarvamLanguage('auto'), 'unknown');
  assert.equal(shortLang('sa-IN'), 'sa'); assert.equal(shortLang('kok-IN'), 'kok'); assert.equal(shortLang('od-IN'), 'or'); assert.equal(shortLang(null), 'und');
  assert.deepEqual(sarvamEntries({ language_code: 'ta-IN', timestamps: { chunks: ['a', 'b'], start_time_seconds: [0, 4] } }).entries.map((e) => e.start), [0, 4]);
  assert.equal(sarvamEntries({ language_code: 'sa-IN', transcript: 'धर्मक्षेत्रे कुरुक्षेत्रे' }).entries[0].text, 'धर्मक्षेत्रे कुरुक्षेत्रे');
  const s = services();
  assert.equal(await putSarvamChunk({ key: SKEY, send: s.send }, { url: BLOB, azure: false, chunkBytes: 10, mime: 'audio/aac' }, new Uint8Array(5), 0, true), true);
  assert.equal(await putSarvamChunk({ key: SKEY, send: s.send }, { url: BLOB, azure: false, chunkBytes: 10, mime: 'audio/aac' }, new Uint8Array(5), 10, true), false);
});
