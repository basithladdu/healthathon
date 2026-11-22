// Sarvam in the website route the Android app calls (/api/goc-transcribe, app/goc-transcribe-server.ts), with Gemini as the fallback.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createGocTranscribeHandler, GEMINI_UPLOAD } from '../app/goc-transcribe-server.ts';

const SKEY = 'edge-sarvam-key';
const BLOB = 'https://sarvamstore.blob.core.windows.net/jobs/J1/window.aac?sv=2024&sig=SECRET';
const ENV = { GEMINI_API_KEY_1: 'route-gemini-key', SARVAM_API_KEY: SKEY, GOC_TICKET_SECRET: 'route-ticket-secret' };
const URL0 = 'https://sahara.wedevit.in/api/goc-transcribe';
const json = (body) => new Request(URL0, { method: 'POST', headers: { 'Content-Type': 'application/json', 'x-sahara-client': 'android' }, body: JSON.stringify(body) });
const chunk = (bytes, headers) => new Request(`${URL0}?action=chunk`, { method: 'POST', headers: { 'x-sahara-client': 'android', 'x-sahara-consent': 'online-transcription', 'content-length': String(bytes.length), ...headers }, body: bytes });

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


async function record(s, env = ENV, bytes = 5_000_000) {
  const handle = createGocTranscribeHandler(s.send, env, async () => {});
  const start = await (await handle(json({ action: 'start', consent: true, mimeType: 'audio/aac', bytes }))).json();
  const audio = new Uint8Array(bytes).fill(7); let file = null;
  if (start.mode === 'relay') for (let offset = 0; offset < bytes; offset += start.chunkBytes) {
    const part = audio.slice(offset, offset + start.chunkBytes); const final = offset + part.length >= bytes;
    const r = await handle(chunk(part, { 'x-goc-ticket': start.ticket, 'x-goc-offset': String(offset), 'x-goc-final': final ? '1' : '0' }));
    assert.equal(r.status, 200); const v = await r.json(); if (v.file) file = v.file;
  }
  const ask = async () => { const r = await handle(json({ action: 'transcribe', consent: true, ticket: start.ticket, file, lang: 'auto', offsetSec: 300, overlapSec: 0, durationSec: 300 })); return { status: r.status, body: await r.json() }; };
  return { start, ask };
}

test('the route sends the audio to Sarvam and Google, and Sarvam transcribes with named speakers', async () => {
  const s = services();
  const { start, ask } = await record(s);
  assert.equal(start.mode, 'relay'); assert.ok(!JSON.stringify(start).includes('SECRET'));
  assert.equal(s.blob(), 5_000_000);
  const answer = await ask();
  assert.equal(answer.status, 200); assert.equal(answer.body.engine, 'sarvam'); assert.equal(answer.body.model, 'saaras:v4');
  assert.deepEqual(answer.body.lines.map((l) => [l.startSec, l.speaker]), [[302.5, 'DOCTOR'], [306, 'PATIENT_PARTY'], [309, 'PATIENT_PARTY']]);
  assert.equal(s.files.size, 0);
});

test('a failed Sarvam job falls back to Gemini in the route', async () => {
  const s = services({ sarvamStates: ['Failed'] });
  const { ask } = await record(s, ENV, 1000);
  const answer = await ask();
  assert.equal(answer.status, 200); assert.equal(answer.body.engine, 'gemini');
});

test('while Sarvam works the route answers 202 and keeps the audio', async () => {
  const s = services({ sarvamStates: ['Accepted', 'Running'] });
  let t = 0; const realNow = Date.now; Date.now = () => (t += 40_000);
  try {
    const { ask } = await record(s, ENV, 1000);
    const answer = await ask();
    assert.equal(answer.status, 202); assert.equal(answer.body.pending, true); assert.equal(s.files.size, 1);
  } finally { Date.now = realNow; }
});

test('without SARVAM_API_KEY the route behaves as before', async () => {
  const s = services();
  const { start } = await record(s, { GEMINI_API_KEY_1: 'k', GOC_TICKET_SECRET: 'x' }, 1000);
  assert.equal(start.mode, 'direct'); assert.ok(!s.calls.some((c) => c.url.includes('sarvam')));
});
