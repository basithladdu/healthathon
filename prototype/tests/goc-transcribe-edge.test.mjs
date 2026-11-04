// The Android path: goals-of-care transcription inside the Supabase function sahara-care-assist (android/supabase/functions/sahara-care-assist/goc-gemini.ts).
import test from 'node:test';
import assert from 'node:assert/strict';
import { gocConfiguration, handleGoc, gocActionFor, checkedGocLines, parseGeminiAnswer, uploadUrlIsSafe, signGocTicket, readGocTicket, GEMINI_UPLOAD, GOC_FALLBACK_MODELS }
  from '../../android/supabase/functions/sahara-care-assist/goc-gemini.ts';

const KEY = 'edge-gemini-key';
const env = (values) => (name) => values[name] ?? '';
const CONFIG = gocConfiguration(env({ SAHARA_AI_BASE_URL: 'https://generativelanguage.googleapis.com/v1beta/openai', SAHARA_AI_API_KEY: KEY, SAHARA_AI_MODEL: 'gemini-3.1-flash-lite' }));

function google({ answers = [], uploadUrl = 'https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=UP1&upload_protocol=resumable' } = {}) {
  const files = new Map(); const sessions = new Map(); const calls = []; let next = 0; const queue = [...answers];
  const send = async (url, init = {}) => {
    const text = String(url); const headers = new Headers(init.headers || {}); calls.push({ url: text, method: init.method || 'GET' });
    if (text === GEMINI_UPLOAD) {
      assert.equal(headers.get('x-goog-api-key'), KEY);
      const id = `UP${++next}`; sessions.set(id, { display: JSON.parse(init.body).file.display_name, mime: headers.get('x-goog-upload-header-content-type'), size: 0 });
      return new Response('{}', { headers: { 'x-goog-upload-url': uploadUrl.replace('UP1', id), 'x-goog-upload-chunk-granularity': '262144' } });
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
    if (match && (init.method || 'GET') === 'GET') return files.has(match[1]) ? Response.json(files.get(match[1])) : new Response('{}', { status: 404 });
    if (match && init.method === 'DELETE') { files.delete(match[1]); return new Response('{}'); }
    if (text.includes(':generateContent')) {
      const model = text.split('/models/')[1].split(':')[0];
      const step = queue.length ? queue.shift() : 'ok';
      if (typeof step === 'number') return new Response('{"error":{"message":"busy"}}', { status: step });
      return Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify({ lines: [
        { startSec: 3, speaker: 'DOCTOR', lang: 'en', text: `What matters most to you? (${model})` }, { startSec: 9, speaker: 'PATIENT_PARTY', lang: 'hi', text: 'मुझे घर पर रहना है' }] }) }] } }] });
    }
    throw new Error(`unexpected ${text}`);
  };
  return { send, files, calls };
}
const post = (action, body, headers = {}) => new Request(`https://x.supabase.co/functions/v1/sahara-care-assist/${action}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: typeof body === 'string' || body instanceof Uint8Array ? body : JSON.stringify(body) });
const RID = '0f8b7a3e-2c4d-4e6f-8a9b-1c2d3e4f5a6b';
const noPause = async () => {};

async function upload(g, started, bytes) {
  const response = await g.send(started.uploadUrl, { method: 'POST', body: new Uint8Array(bytes), headers: { 'X-Goog-Upload-Command': 'upload, finalize', 'X-Goog-Upload-Offset': '0' } });
  return (await response.json()).file.name;
}

test('the key and model come from the function secrets SAHARA_AI_API_KEY / SAHARA_AI_MODEL', () => {
  assert.equal(CONFIG.ready, true); assert.equal(CONFIG.key, KEY); assert.deepEqual(CONFIG.models, ['gemini-3.1-flash-lite', ...GOC_FALLBACK_MODELS]);
  assert.equal(gocConfiguration(env({ SAHARA_AI_BASE_URL: 'https://api.groq.com/openai/v1', SAHARA_AI_API_KEY: 'g', SAHARA_AI_MODEL: 'llama' })).ready, false);
  assert.equal(gocConfiguration(env({ GEMINI_API_KEY: 'b' })).key, 'b');
  assert.equal(gocActionFor('/functions/v1/sahara-care-assist/goc-transcribe-window'), 'goc-transcribe-window'); assert.equal(gocActionFor('/functions/v1/sahara-care-assist/transcribe'), null);
});

test('a recording reserves one attempt, uploads straight to Google, gets checked lines with speakers and the file is deleted', async () => {
  const g = google(); const reserved = [];
  const deps = { config: CONFIG, send: g.send, userId: 'u1', reserve: async (id) => { reserved.push(id); }, pause: noPause };
  const first = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 4000 }), deps);
  assert.equal(first.status, 200); assert.equal(first.body.mode, 'direct'); assert.equal(first.body.windowSec, 300);
  assert.ok(!JSON.stringify(first.body).includes(KEY));
  const name = await upload(g, first.body, 4000);
  const window = await handleGoc('goc-transcribe-window', post('goc-transcribe-window', { consent: true, ticket: first.body.ticket, file: name, lang: 'hi', offsetSec: 300, overlapSec: 2, durationSec: 300 }), deps);
  assert.equal(window.status, 200); assert.equal(window.body.model, 'gemini-3.1-flash-lite');
  assert.deepEqual(window.body.lines.map((l) => [l.startSec, l.speaker, l.lang]), [[303, 'DOCTOR', 'en'], [309, 'PATIENT_PARTY', 'hi']]);
  assert.equal(g.files.size, 0);
  // The next window of the same recording uses the run ticket: no second reservation.
  const second = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, run: first.body.run, mimeType: 'audio/aac', bytes: 10 * 1000 }), deps);
  assert.equal(second.status, 200); assert.deepEqual(reserved, [RID]);
});

test('tickets are bound to the account; consent and audio format are checked', async () => {
  const g = google();
  const deps = { config: CONFIG, send: g.send, userId: 'u1', reserve: async () => {}, pause: noPause };
  const started = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 100 }), deps);
  const name = await upload(g, started.body, 100);
  await assert.rejects(handleGoc('goc-transcribe-window', post('goc-transcribe-window', { consent: true, ticket: started.body.ticket, file: name, offsetSec: 0, durationSec: 30 }), { ...deps, userId: 'u2' }), (e) => e.status === 403);
  await assert.rejects(handleGoc('goc-transcribe-start', post('goc-transcribe-start', { requestId: RID, mimeType: 'audio/aac', bytes: 100 }), deps), (e) => e.code === 'consent_required');
  await assert.rejects(handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/m4a', bytes: 100 }), deps), (e) => e.status === 415);
  await assert.rejects(handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 100 }), { ...deps, config: gocConfiguration(env({})) }), (e) => e.code === 'not_enabled');
  const ticket = await signGocTicket('s', { k: 'file', u: 'u1', r: RID, e: Date.now() + 1000, d: 'd', b: 1, m: 'audio/aac' }, 'https://generativelanguage.googleapis.com/upload/x?key=K');
  assert.equal((await readGocTicket('s', ticket, 'file', 'u1')).relayUrl, 'https://generativelanguage.googleapis.com/upload/x?key=K');
  await assert.rejects(readGocTicket('s', ticket, 'run', 'u1'), (e) => e.status === 403);
});

test('busy Gemini is retried, then reported as provider_busy with the file kept for one more try; a missing model moves on', async () => {
  const g = google({ answers: [503, 'ok', 429, 429, 429, 429, 429, 429, 404, 'ok'] });
  const deps = { config: CONFIG, send: g.send, userId: 'u1', reserve: async () => {}, pause: noPause };
  const run = async () => {
    const started = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 100 }), deps);
    const name = await upload(g, started.body, 100);
    return handleGoc('goc-transcribe-window', post('goc-transcribe-window', { consent: true, ticket: started.body.ticket, file: name, offsetSec: 0, durationSec: 60 }), deps);
  };
  assert.equal((await run()).body.model, 'gemini-3.1-flash-lite');
  await assert.rejects(run(), (e) => e.code === 'provider_busy' && e.status === 503);
  assert.equal(g.files.size, 1, 'kept after busy');
  assert.equal((await run()).body.model, GOC_FALLBACK_MODELS[0]);
});

test('an upload URL with the key in it is never handed out (relay mode); answers are checked', async () => {
  const g = google({ uploadUrl: `https://generativelanguage.googleapis.com/upload/v1beta/files?key=${KEY}&upload_id=UP1` });
  const deps = { config: CONFIG, send: g.send, userId: 'u1', reserve: async () => {}, pause: noPause };
  const started = await handleGoc('goc-transcribe-start', post('goc-transcribe-start', { consent: true, requestId: RID, mimeType: 'audio/aac', bytes: 100 }), deps);
  assert.equal(started.body.mode, 'relay'); assert.ok(!JSON.stringify(started.body).includes(KEY));
  const relayed = await handleGoc('goc-transcribe-chunk', post('goc-transcribe-chunk', new Uint8Array(100), { 'X-Sahara-Consent': 'online-transcription', 'X-Goc-Ticket': started.body.ticket, 'X-Goc-Offset': '0', 'X-Goc-Final': '1' }), deps);
  assert.equal(relayed.body.status, 'final'); assert.match(relayed.body.file, /^files\//u);
  assert.equal(uploadUrlIsSafe('https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=A', KEY), true);
  assert.throws(() => checkedGocLines({ lines: [{ startSec: 400, text: 'x' }] }, { offsetSec: 0, overlapSec: 0, durationSec: 300 }), /after the end/u);
  assert.throws(() => parseGeminiAnswer({ candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [] } }] }, { offsetSec: 0, overlapSec: 0, durationSec: 300 }), (e) => e.code === 'output_truncated');
});
