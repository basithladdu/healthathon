import test from 'node:test';
import assert from 'node:assert/strict';
import { checkedGocLines, mergeGocLines, planGocWindows, gocSpeaker, gocLang, gocSeconds, gocWav, gocConsentLine, GocOutputError } from '../app/goc-online-lines.ts';
import { createGocTranscribeHandler, gocTranscribeConfig, parseGeminiAnswer, uploadUrlIsSafe, signTicket, readTicket, GEMINI_UPLOAD, GOC_GEMINI_MODELS } from '../app/goc-transcribe-server.ts';
import { runGocOnline, samplesSource, GocOnlineError } from '../app/goc-gemini-client.ts';

const W = { offsetSec: 0, overlapSec: 0, durationSec: 600 };

test('lines are checked, mapped onto the recording clock and sorted', () => {
  const lines = checkedGocLines({ lines: [
    { startSec: 12.5, speaker: 'PATIENT_PARTY', lang: 'hi', text: '  मुझे घर   जाना है ' },
    { startSec: '00:03', speaker: 'Doctor', lang: 'en-IN', text: 'How are you feeling today?' },
    { startSec: 20, speaker: 'nurse?', lang: 'Telugu', text: 'ఇంటికి వెళ్ళాలి' },
    { startSec: 25, speaker: 'UNKNOWN', lang: 'kn', text: '   ' },
  ] }, { offsetSec: 600, overlapSec: 0, durationSec: 600 });
  assert.deepEqual(lines, [
    { startSec: 603, speaker: 'DOCTOR', lang: 'en', text: 'How are you feeling today?' },
    { startSec: 612.5, speaker: 'PATIENT_PARTY', lang: 'hi', text: 'मुझे घर जाना है' },
    { startSec: 620, speaker: 'UNKNOWN', lang: 'te', text: 'ఇంటికి వెళ్ళాలి' },
  ]);
});

test('a malformed answer is rejected so the engine is retried or the next one is used', () => {
  for (const bad of [null, 'text', { lines: 'x' }, { lines: [{ speaker: 'DOCTOR', text: 'hi' }] }, { lines: [{ startSec: 1, speaker: 'DOCTOR' }] }, { lines: [{ startSec: -1, text: 'a' }] }, { lines: [5] }]) {
    assert.throws(() => checkedGocLines(bad, W), GocOutputError, JSON.stringify(bad));
  }
  assert.throws(() => checkedGocLines({ lines: [{ startSec: 700, speaker: 'DOCTOR', lang: 'en', text: 'late' }] }, W), /after the end/u);
  assert.deepEqual(checkedGocLines({ lines: [] }, W), []);
});

test('lines inside a leading overlap are dropped and a looping line is kept once', () => {
  const lines = checkedGocLines({ lines: [
    { startSec: 0.5, speaker: 'DOCTOR', lang: 'en', text: 'tail of the previous part' },
    { startSec: 2.6, speaker: 'DOCTOR', lang: 'en', text: 'new words' },
    { startSec: 4, speaker: 'DOCTOR', lang: 'en', text: 'new words' },
  ] }, { offsetSec: 598, overlapSec: 2, durationSec: 300 });
  assert.deepEqual(lines.map((l) => [l.startSec, l.text]), [[600.6, 'new words']]);
});

test('speaker, language and time helpers', () => {
  assert.equal(gocSpeaker('patient'), 'PATIENT_PARTY'); assert.equal(gocSpeaker('Family'), 'PATIENT_PARTY'); assert.equal(gocSpeaker('DOCTOR'), 'DOCTOR'); assert.equal(gocSpeaker(3), 'UNKNOWN');
  assert.equal(gocLang('ur-IN'), 'ur'); assert.equal(gocLang('Kannada'), 'kn'); assert.equal(gocLang('??'), 'und');
  assert.equal(gocSeconds('1:01:15'), 3675); assert.equal(gocSeconds('75.5s'), 75.5); assert.equal(gocSeconds('soon'), null);
  assert.equal(gocConsentLine(true), 'Audio is sent to a secure AI service to transcribe.'); assert.equal(gocConsentLine(false), '');
});

test('windows split long audio at quiet cuts; merge drops a line repeated across a cut', () => {
  assert.deepEqual(planGocWindows(1300, 600), [{ start: 0, end: 600 }, { start: 600, end: 1200 }, { start: 1200, end: 1300 }]);
  assert.deepEqual(planGocWindows(1300, 600, (at) => at - 2), [{ start: 0, end: 598 }, { start: 598, end: 1196 }, { start: 1196, end: 1300 }]);
  const merged = mergeGocLines([[{ startSec: 598, speaker: 'DOCTOR', lang: 'en', text: 'same' }], [{ startSec: 599, speaker: 'DOCTOR', lang: 'en', text: 'same' }, { startSec: 590, speaker: 'PATIENT_PARTY', lang: 'en', text: 'earlier' }]]);
  assert.deepEqual(merged.map((l) => l.text), ['earlier', 'same']);
  const wav = gocWav(new Float32Array([0, 1, -1]));
  assert.equal(new TextDecoder().decode(wav.slice(0, 4)), 'RIFF'); assert.equal(wav.length, 50);
});

test('Gemini answers: fenced JSON is read, a truncated or blocked answer is reported as such', () => {
  const answer = (text, finishReason = 'STOP') => ({ candidates: [{ finishReason, content: { parts: [{ text }] } }] });
  assert.equal(parseGeminiAnswer(answer('```json\n{"lines":[{"startSec":1,"speaker":"DOCTOR","lang":"en","text":"Hello"}]}\n```'), W)[0].text, 'Hello');
  assert.throws(() => parseGeminiAnswer(answer('{"lines":[', 'MAX_TOKENS'), W), (e) => e.code === 'output_truncated');
  assert.throws(() => parseGeminiAnswer(answer('', 'SAFETY'), W), (e) => e.code === 'blocked');
  assert.throws(() => parseGeminiAnswer(answer('not json'), W), (e) => e.code === 'invalid_output');
  assert.throws(() => parseGeminiAnswer({ promptFeedback: { blockReason: 'OTHER' } }, W), (e) => e.code === 'blocked');
});

test('the upload URL is handed out only when it carries no key', () => {
  assert.equal(uploadUrlIsSafe('https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=AB12&upload_protocol=resumable', 'SECRETKEY'), true);
  assert.equal(uploadUrlIsSafe('https://generativelanguage.googleapis.com/upload/v1beta/files?key=SECRETKEY&upload_id=AB12', 'SECRETKEY'), false);
  assert.equal(uploadUrlIsSafe('https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=xSECRETKEYx', 'SECRETKEY'), false);
  assert.equal(uploadUrlIsSafe('https://evil.example/upload/v1beta/files?upload_id=AB12', 'SECRETKEY'), false);
  assert.equal(uploadUrlIsSafe(null, 'k'), false);
});

test('tickets are signed, expire and keep a relay URL sealed', async () => {
  const ticket = await signTicket('s', { d: 'sahara-goc-1', b: 10, m: 'audio/wav', e: Date.now() + 1000 }, 'https://generativelanguage.googleapis.com/upload/x?key=K');
  assert.ok(!ticket.includes('key=K'));
  assert.equal((await readTicket('s', ticket)).relayUrl, 'https://generativelanguage.googleapis.com/upload/x?key=K');
  await assert.rejects(readTicket('other', ticket), /Start the upload again/u);
  await assert.rejects(readTicket('s', ticket, Date.now() + 5000), /expired/u);
});

// ---------- a fake Google behind the real route handler ----------
const KEY = 'test-gemini-key';
const UPLOAD_URL = 'https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=UP1&upload_protocol=resumable';

function upstream({ gemini = [], uploadUrl = UPLOAD_URL } = {}) {
  const calls = []; const files = new Map(); let next = 0; const sessions = new Map();
  const geminiQueue = [...gemini];
  const send = async (url, init = {}) => {
    const text = String(url); const headers = new Headers(init.headers || {});
    calls.push({ url: text, method: init.method || 'GET', headers });
    if (text === GEMINI_UPLOAD) {
      assert.equal(headers.get('x-goog-api-key'), KEY); assert.ok(!text.includes(KEY));
      const id = `UP${++next}`; sessions.set(id, { display: JSON.parse(init.body).file.display_name, mime: headers.get('x-goog-upload-header-content-type'), bytes: [] });
      return new Response('{}', { headers: { 'x-goog-upload-url': uploadUrl.replace('UP1', id), 'x-goog-upload-chunk-granularity': '262144' } });
    }
    if (text.startsWith('https://generativelanguage.googleapis.com/upload/')) {
      const id = new URL(text).searchParams.get('upload_id'); const session = sessions.get(id);
      session.bytes.push(new Uint8Array(await new Response(init.body).arrayBuffer()));
      if (!headers.get('x-goog-upload-command').includes('finalize')) return new Response('', { headers: { 'x-goog-upload-status': 'active' } });
      const name = `files/f${id.toLowerCase()}`; const size = session.bytes.reduce((n, b) => n + b.length, 0);
      const file = { name, uri: `https://generativelanguage.googleapis.com/v1beta/${name}`, mimeType: session.mime, state: 'ACTIVE', sizeBytes: String(size), displayName: session.display };
      files.set(name, file);
      return Response.json({ file }, { headers: { 'x-goog-upload-status': 'final' } });
    }
    const fileMatch = /\/v1beta\/(files\/[a-z0-9-]+)$/u.exec(text);
    if (fileMatch && (init.method || 'GET') === 'GET') return files.has(fileMatch[1]) ? Response.json(files.get(fileMatch[1])) : new Response('{}', { status: 404 });
    if (fileMatch && init.method === 'DELETE') { files.delete(fileMatch[1]); return new Response('{}'); }
    if (text.includes(':generateContent')) {
      assert.equal(headers.get('x-goog-api-key'), KEY);
      const body = JSON.parse(init.body);
      assert.ok(body.contents[0].parts[1].file_data.file_uri.includes('/files/'));
      assert.equal(body.generationConfig.responseMimeType, 'application/json');
      const step = geminiQueue.length ? geminiQueue.shift() : 'ok';
      if (typeof step === 'number') return new Response('{}', { status: step });
      if (step === 'bad') return Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: 'Here is a summary of the talk.' }] } }] });
      if (step === 'long') return Response.json({ candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [{ text: '{"lines":[' }] } }] });
      return Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify({ lines: [
        { startSec: 1, speaker: 'DOCTOR', lang: 'en', text: `Doctor at ${text.split('/models/')[1].split(':')[0]}` },
        { startSec: 5, speaker: 'PATIENT_PARTY', lang: 'hi', text: 'मुझे घर पर रहना है' }] }) }] } }] });
    }
    throw new Error(`unexpected ${text}`);
  };
  return { send, calls, files };
}

const ORIGIN = 'https://sahara.wedevit.in';
/** The client's HTTP calls, sent to the real handler in-process; uploads go to the fake Google. */
function httpFor(handler, send) {
  const route = `${ORIGIN}/api/goc-transcribe`;
  const read = async (response) => ({ status: response.status, body: await response.json().catch(() => ({})) });
  return {
    info: async () => (await handler(new Request(route))).json(),
    json: async (body) => read(await handler(new Request(route, { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: ORIGIN }, body: JSON.stringify(body) }))),
    binary: async (query, data, headers) => read(await handler(new Request(`${route}?${new URLSearchParams(query)}`, { method: 'POST', body: data,
      headers: { 'Content-Type': data.type, 'X-Sahara-Consent': 'online-transcription', Origin: ORIGIN, ...headers } }))),
    upload: async (url, data, headers) => { const r = await send(url, { method: 'POST', body: data, headers }); return { status: r.status, uploadStatus: r.headers.get('x-goog-upload-status') || '', body: await r.json().catch(() => null) }; },
  };
}
const RATE = 100; // samples a second: keeps a 20-minute fake recording tiny
const source = (seconds) => samplesSource(new Float32Array(seconds * RATE).fill(0.1), undefined, RATE);
const noPause = async () => {};

test('GET says which engines are set up and gives the consent line, without the keys', async () => {
  const off = await (await createGocTranscribeHandler(async () => { throw new Error('no'); }, {})(new Request(`${ORIGIN}/api/goc-transcribe`))).json();
  assert.equal(off.available, false); assert.equal(off.consentLine, '');
  const response = await createGocTranscribeHandler(async () => { throw new Error('no'); }, { SAHARA_AI_API_KEY: KEY })(new Request(`${ORIGIN}/api/goc-transcribe`));
  const text = await response.text();
  assert.ok(!text.includes(KEY));
  const on = JSON.parse(text);
  assert.equal(on.gemini, true); assert.equal('groq' in on, false); assert.equal(on.consentLine, 'Audio is sent to a secure AI service to transcribe.'); assert.equal(on.geminiModel, GOC_GEMINI_MODELS[0]);
});

test('POST needs consent and a same-origin, Android or signed-in caller', async () => {
  const handler = createGocTranscribeHandler(upstream().send, { SAHARA_AI_API_KEY: KEY }, noPause);
  const post = (body, headers) => handler(new Request(`${ORIGIN}/api/goc-transcribe`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) }));
  assert.equal((await post({ action: 'start', mimeType: 'audio/wav', bytes: 100 }, { Origin: ORIGIN })).status, 400);
  assert.equal((await post({ action: 'start', consent: true, mimeType: 'audio/wav', bytes: 100 }, { Origin: 'https://evil.example' })).status, 401);
  assert.equal((await post({ action: 'start', consent: true, mimeType: 'audio/webm', bytes: 100 }, { Origin: ORIGIN })).status, 415);
  const started = await post({ action: 'start', consent: true, mimeType: 'audio/wav', bytes: 100 }, { 'X-Sahara-Client': 'android' });
  assert.equal(started.status, 200);
  const body = await started.json();
  assert.equal(body.mode, 'direct'); assert.ok(body.uploadUrl.startsWith('https://generativelanguage.googleapis.com/upload/')); assert.ok(!JSON.stringify(body).includes(KEY));
});

test('an upload URL carrying the key is never handed out: relay mode instead', async () => {
  const google = upstream({ uploadUrl: `https://generativelanguage.googleapis.com/upload/v1beta/files?key=${KEY}&upload_id=UP1` });
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  const result = await runGocOnline(source(30), { lang: 'hi' }, { http: httpFor(handler, google.send), pause: noPause });
  assert.equal(result.engine, 'gemini');
  assert.ok(google.calls.some((c) => c.url.includes('upload_id=UP1') && c.url.includes('key=')), 'the server relayed the chunk');
  assert.equal(google.files.size, 0, 'the file was deleted');
});

test('Gemini transcribes window by window, labels speakers and deletes every file', async () => {
  const google = upstream();
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  const progress = [];
  const result = await runGocOnline(source(1300), { lang: 'auto', onProgress: (p) => progress.push(p) }, { http: httpFor(handler, google.send), pause: noPause });
  assert.equal(result.engine, 'gemini'); assert.equal(result.model, GOC_GEMINI_MODELS[0]);
  assert.equal(result.lines.length, 6);
  assert.deepEqual(result.lines.slice(0, 2).map((l) => [l.startSec, l.speaker]), [[1, 'DOCTOR'], [5, 'PATIENT_PARTY']]);
  assert.ok(result.lines.some((l) => l.startSec > 1200));
  assert.equal(google.files.size, 0);
  assert.equal(progress.at(-1), 1); assert.ok(progress.every((p, i) => i === 0 || p >= progress[i - 1]));
});

test('Gemini 429 and 5xx are retried once; a model that is not found moves to the next model', async () => {
  const google = upstream({ gemini: [429, 'ok', 503, 'ok', 404, 'ok'] });
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  const http = httpFor(handler, google.send);
  const first = await runGocOnline(source(30), {}, { http, pause: noPause });
  const second = await runGocOnline(source(30), {}, { http, pause: noPause });
  const third = await runGocOnline(source(30), {}, { http, pause: noPause });
  assert.equal(first.model, GOC_GEMINI_MODELS[0]); assert.equal(second.model, GOC_GEMINI_MODELS[0]); assert.equal(third.model, GOC_GEMINI_MODELS[1]);
});

test('an unusable Gemini answer is retried, and a too-long answer splits the window', async () => {
  const google = upstream({ gemini: ['bad', 'ok', 'long', 'ok', 'ok'] });
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  const http = httpFor(handler, google.send);
  assert.equal((await runGocOnline(source(30), {}, { http, pause: noPause })).engine, 'gemini');
  const split = await runGocOnline(source(600), {}, { http, pause: noPause });
  assert.equal(split.lines.length, 4, 'two halves, two lines each');
  assert.equal(google.files.size, 0);
});

test('the key and model come from SAHARA_AI_API_KEY / SAHARA_AI_MODEL, with GEMINI_API_KEY as the fallback name', () => {
  const sahara = gocTranscribeConfig({ SAHARA_AI_API_KEY: 'a', SAHARA_AI_MODEL: 'gemini-3.1-flash-lite', GEMINI_API_KEY: 'b' });
  assert.equal(sahara.gemini, 'a'); assert.deepEqual(sahara.geminiModels, [...new Set(['gemini-3.1-flash-lite', ...GOC_GEMINI_MODELS])]);
  assert.equal(gocTranscribeConfig({ GEMINI_API_KEY: 'b' }).gemini, 'b');
  // A custom non-Gemini service in SAHARA_AI_* is not a Gemini key.
  const custom = gocTranscribeConfig({ SAHARA_AI_BASE_URL: 'https://api.example.com/v1', SAHARA_AI_API_KEY: 'x', SAHARA_AI_MODEL: 'llama' });
  assert.equal(custom.gemini, ''); assert.deepEqual(custom.geminiModels, GOC_GEMINI_MODELS);
  assert.deepEqual(gocTranscribeConfig({ SAHARA_AI_API_KEY: 'a', GEMINI_GOC_MODEL: 'gemini-x' }).geminiModels, ['gemini-x']);
});

test('when Gemini stays busy the caller gets GocOnlineError with the finished lines, to go on-device', async () => {
  // Window 1 fine; window 2 busy on both models twice, plus the client's one wait.
  const google = upstream({ gemini: ['ok', 429, 429, 429, 429, 429, 429, 429, 429, 429, 429, 429, 429] });
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  await assert.rejects(runGocOnline(source(1300), {}, { http: httpFor(handler, google.send), pause: noPause }), (error) => {
    assert.ok(error instanceof GocOnlineError); assert.equal(error.code, 'provider_busy'); assert.equal(error.partial.length, 2);
    assert.deepEqual(error.partial.map((l) => l.speaker), ['DOCTOR', 'PATIENT_PARTY']); return true;
  });
  assert.equal(google.files.size, 0, 'even the busy file is deleted');
});

test('a Gemini failure or a missing key ends the online run without trying another service', async () => {
  const google = upstream({ gemini: ['ok', 500, 500, 500, 500, 500, 500] });
  const handler = createGocTranscribeHandler(google.send, { SAHARA_AI_API_KEY: KEY }, noPause);
  await assert.rejects(runGocOnline(source(1300), {}, { http: httpFor(handler, google.send), pause: noPause }), (error) => {
    assert.ok(error instanceof GocOnlineError); assert.equal(error.code, 'unavailable'); assert.equal(error.partial.length, 2); return true;
  });
  const off = createGocTranscribeHandler(google.send, {}, noPause);
  await assert.rejects(runGocOnline(source(10), {}, { http: httpFor(off, google.send), pause: noPause }), (error) => error.code === 'not_enabled');
});
