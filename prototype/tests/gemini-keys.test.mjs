// Gemini key fallback for the goals-of-care path (GEMINI_API_KEY_1 … _5), and the analyze action that maps a transcript onto the form.
import test from 'node:test';
import assert from 'node:assert/strict';
import { geminiKeySlots, withGeminiKeys, GeminiCallError, keyMayHelp, resetGeminiKeyRest } from '../app/gemini-keys.ts';
import { createGocTranscribeHandler, gocTranscribeConfig, GEMINI_UPLOAD } from '../app/goc-transcribe-server.ts';
import { checkedGocAnalysis, GOC_ANALYZE_SCHEMA } from '../app/goc-analyze-server.ts';
import { GOC_EXTRACT_RULES } from '../app/goc-extract-server.ts';

const slots = (env) => geminiKeySlots(env).map((s) => s.slot);
const quiet = () => {};

test('only the key slots that are set are used, in order, gaps skipped', () => {
  assert.deepEqual(slots({ GEMINI_API_KEY_1: 'a' }), ['GEMINI_API_KEY_1']);
  assert.deepEqual(slots({ GEMINI_API_KEY_1: 'a', GEMINI_API_KEY_3: 'c' }), ['GEMINI_API_KEY_1', 'GEMINI_API_KEY_3']);
  assert.deepEqual(slots({ GEMINI_API_KEY_4: 'd', GEMINI_API_KEY_2: ' b ', GEMINI_API_KEY_5: '' }), ['GEMINI_API_KEY_2', 'GEMINI_API_KEY_4']);
  assert.deepEqual(geminiKeySlots({ GEMINI_API_KEY_2: ' b ' })[0].key, 'b');
  // The older names come last; the same key under two names is used once.
  assert.deepEqual(slots({ GEMINI_API_KEY: 'z', GEMINI_API_KEY_1: 'a', SAHARA_AI_API_KEY: 's' }), ['GEMINI_API_KEY_1', 'SAHARA_AI_API_KEY', 'GEMINI_API_KEY']);
  assert.deepEqual(slots({ GEMINI_API_KEY: 'a', GEMINI_API_KEY_1: 'a' }), ['GEMINI_API_KEY_1']);
  // SAHARA_AI_API_KEY is not a Gemini key while SAHARA_AI_BASE_URL points elsewhere.
  assert.deepEqual(slots({ SAHARA_AI_BASE_URL: 'https://api.example.com/v1', SAHARA_AI_API_KEY: 's', GEMINI_API_KEY_5: 'e' }), ['GEMINI_API_KEY_5']);
  assert.deepEqual(slots({}), []);
  assert.equal(gocTranscribeConfig({ GEMINI_API_KEY_3: 'c' }).gemini, 'c');
  assert.equal(gocTranscribeConfig({}).gemini, '');
});

test('which failures move on to the next key', () => {
  for (const status of [null, 429, 500, 503, 401, 403]) assert.equal(keyMayHelp(status), true, String(status));
  assert.equal(keyMayHelp(400, '{"error":{"status":"INVALID_ARGUMENT","message":"Invalid JSON payload"}}'), false);
  assert.equal(keyMayHelp(400, '{"error":{"message":"API key not valid. Please pass a valid API key.","details":[{"reason":"API_KEY_INVALID"}]}}'), true);
  assert.equal(keyMayHelp(404), false); assert.equal(keyMayHelp(413), false);
});

test('withGeminiKeys falls through busy, broken and unreachable keys, and logs slot names only', async () => {
  resetGeminiKeyRest();
  const pool = geminiKeySlots({ GEMINI_API_KEY_1: 'k1-secret', GEMINI_API_KEY_2: 'k2-secret', GEMINI_API_KEY_3: 'k3-secret', GEMINI_API_KEY_4: 'k4-secret', GEMINI_API_KEY_5: 'k5-secret' });
  const fails = { 'k1-secret': 429, 'k2-secret': 503, 'k3-secret': 403, 'k4-secret': null };
  const logs = []; const tried = [];
  const result = await withGeminiKeys(pool, async (slot) => { tried.push(slot.slot); if (slot.key in fails) throw new GeminiCallError(fails[slot.key]); return slot.key; },
    { log: (event, detail) => logs.push(JSON.stringify([event, detail])) });
  assert.equal(result.value, 'k5-secret'); assert.equal(result.slot.slot, 'GEMINI_API_KEY_5');
  assert.deepEqual(tried, ['GEMINI_API_KEY_1', 'GEMINI_API_KEY_2', 'GEMINI_API_KEY_3', 'GEMINI_API_KEY_4', 'GEMINI_API_KEY_5']);
  assert.equal(logs.length, 4); assert.ok(logs.every((line) => !line.includes('secret')));
  // The key that ran out of quota is tried last next time.
  const order = [];
  await withGeminiKeys(pool, async (slot) => { order.push(slot.slot); if (order.length < 5) throw new GeminiCallError(500); return 1; }, { log: quiet });
  assert.equal(order.at(-1), 'GEMINI_API_KEY_1');
  resetGeminiKeyRest();
});

test('a request the API rejects stops at once instead of burning every key', async () => {
  const pool = geminiKeySlots({ GEMINI_API_KEY_1: 'a', GEMINI_API_KEY_2: 'b', GEMINI_API_KEY_3: 'c' });
  const tried = [];
  await assert.rejects(withGeminiKeys(pool, async (slot) => { tried.push(slot.slot); throw new GeminiCallError(400, 'Invalid JSON payload'); }, { log: quiet }),
    (error) => error instanceof GeminiCallError && error.status === 400);
  assert.deepEqual(tried, ['GEMINI_API_KEY_1']);
  // Every key failing: the last failure comes back.
  await assert.rejects(withGeminiKeys(pool, async () => { throw new GeminiCallError(429); }, { log: quiet }), (error) => error.status === 429);
  resetGeminiKeyRest();
  await assert.rejects(withGeminiKeys([], async () => 1), (error) => error.status === 503);
});

test('a single key is the only key ever hit', async () => {
  const pool = geminiKeySlots({ GEMINI_API_KEY_1: 'only' });
  const tried = [];
  await assert.rejects(withGeminiKeys(pool, async (slot) => { tried.push(slot.key); throw new GeminiCallError(503); }, { log: quiet }));
  assert.deepEqual(tried, ['only']);
});

// ---------- the route, with a fake Google and a fake Supabase ----------
const ORIGIN = 'https://sahara.wedevit.in';
const ANDROID = 'https://kjnhtakqzboayqorrlue.supabase.co';
const TOKEN = 'Bearer aaa.bbb.ccc';
const LINES = [
  { startSec: 1, speaker: 'DOCTOR', lang: 'en', text: 'If your heart stops, would you want us to try CPR?' },
  { startSec: 6, speaker: 'PATIENT_PARTY', lang: 'en', text: 'No, I do not want CPR. I want to be at home with my family.' },
  { startSec: 12, speaker: 'PATIENT_PARTY', lang: 'hi', text: 'मुझे वेंटिलेटर पर नहीं रहना है' },
];
const ANSWER = { suggestions: [
  { field: 'cpr', value: 'DNACPR', line: 2, quote: 'No, I do not want CPR.' },
  { field: 'icu', value: 'NO', line: 3, quote: 'words nobody said' },
  { field: 'dialysis', value: 'MAYBE', line: 2, quote: 'I do not want CPR' },
], ownWords: [{ line: 2, quote: 'I want to be at home with my family.' }], unacceptableOutcomes: [{ line: 3, quote: 'मुझे वेंटिलेटर पर नहीं रहना है' }] };

function backend({ keys = {}, saveStatus = 201 } = {}) {
  const calls = []; const saved = []; const sessions = new Map(); let next = 0;
  const send = async (url, init = {}) => {
    const text = String(url); const headers = new Headers(init.headers || {}); const key = headers.get('x-goog-api-key');
    calls.push({ url: text, key, method: init.method || 'GET' });
    if (text === `${ANDROID}/auth/v1/user`) return Response.json({ id: '11111111-1111-4111-8111-111111111111', email_confirmed_at: 'x' });
    if (text.startsWith(`${ANDROID}/rest/v1/sahara_goc_analyses`)) {
      assert.equal(headers.get('authorization'), TOKEN); saved.push(JSON.parse(init.body)); return new Response(null, { status: saveStatus });
    }
    const behaviour = keys[key] ?? 'ok';
    if (typeof behaviour === 'number') return Response.json({ error: { message: 'x' } }, { status: behaviour });
    if (behaviour === 'down') throw new TypeError('fetch failed');
    if (text === GEMINI_UPLOAD) {
      const id = `UP${++next}`; sessions.set(id, JSON.parse(init.body).file.display_name);
      return new Response('{}', { headers: { 'x-goog-upload-url': `https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=${id}`, 'x-goog-upload-chunk-granularity': '262144' } });
    }
    const file = /\/v1beta\/(files\/[a-z0-9-]+)$/u.exec(text);
    if (file && (init.method || 'GET') === 'GET') return Response.json({ name: file[1], uri: `https://x/${file[1]}`, mimeType: 'audio/aac', state: 'ACTIVE', sizeBytes: '100', displayName: sessions.get(`UP${file[1].slice('files/f'.length)}`) });
    if (file) return new Response('{}');
    if (text.includes(':generateContent')) {
      const body = JSON.parse(init.body);
      if (body.contents[0].parts[1]?.file_data) return behaviour === 'busy-audio' ? Response.json({}, { status: 429 }) : Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: '{"lines":[]}' }] } }] });
      return Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify(ANSWER) }] } }] });
    }
    throw new Error(`unexpected ${text}`);
  };
  return { send, calls, saved };
}
const post = (handler, body, headers = {}) => handler(new Request(`${ORIGIN}/api/goc-transcribe`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Sahara-Client': 'android', Authorization: TOKEN, ...headers }, body: JSON.stringify(body) }));

test('analyze reads the transcript with the first key that works, keeps only quoted evidence and saves it', async () => {
  resetGeminiKeyRest();
  const fake = backend({ keys: { 'an-k1': 429, 'an-k2': 'down' } });
  const handler = createGocTranscribeHandler(fake.send, { GEMINI_API_KEY_1: 'an-k1', GEMINI_API_KEY_2: 'an-k2', GEMINI_API_KEY_4: 'an-k4' }, async () => {});
  const response = await post(handler, { action: 'analyze', consent: true, lines: LINES, requestId: '22222222-2222-4222-8222-222222222222', patientId: 'P1', conversationId: 'c-1', transcribedBy: 'Google Gemini (x)' });
  const text = await response.text();
  assert.equal(response.status, 200, text);
  assert.ok(!text.includes('an-k'));
  const body = JSON.parse(text);
  assert.deepEqual(body.suggestions.map((s) => [s.field, s.values[0], s.quote, s.who]), [['cpr', 'DNACPR', 'No, I do not want CPR.', 'patient or family']]);
  assert.deepEqual(body.ownWords, [{ quote: 'I want to be at home with my family.' }]);
  assert.deepEqual(body.unacceptableOutcomes, [{ quote: 'मुझे वेंटिलेटर पर नहीं रहना है' }]);
  assert.ok(body.notDiscussed.includes('icu') && !body.notDiscussed.includes('cpr'));
  assert.equal(body.saved, true); assert.equal(body.requiresReview, true); assert.equal(body.id, '22222222-2222-4222-8222-222222222222');
  assert.deepEqual(fake.calls.filter((c) => c.url.includes(':generateContent')).map((c) => c.key), ['an-k1', 'an-k2', 'an-k4']);
  const row = fake.saved[0];
  assert.equal(row.patient_ref, 'P1'); assert.equal(row.conversation_ref, 'c-1'); assert.equal(row.transcript.length, 3); assert.match(row.transcript_sha256, /^[a-f0-9]{64}$/u);
  assert.equal(row.suggestions[0].field, 'cpr'); assert.ok(row.model.startsWith('gemini-'));
  resetGeminiKeyRest();
});

test('analyze needs consent, a key and lines; a missing table still returns the reading', async () => {
  const fake = backend({ saveStatus: 404 });
  const none = createGocTranscribeHandler(fake.send, {}, async () => {});
  assert.equal((await post(none, { action: 'analyze', consent: true, lines: LINES })).status, 503);
  const handler = createGocTranscribeHandler(fake.send, { GEMINI_API_KEY_1: 'one' }, async () => {});
  assert.equal((await post(handler, { action: 'analyze', lines: LINES })).status, 400);
  assert.equal((await post(handler, { action: 'analyze', consent: true, lines: [] })).status, 400);
  const body = await (await post(handler, { action: 'analyze', consent: true, lines: LINES })).json();
  assert.equal(body.saved, false); assert.equal(body.suggestions.length, 1);
  // Without a signed-in account nothing is saved.
  const anon = await (await post(handler, { action: 'analyze', consent: true, lines: LINES }, { Authorization: '' })).json();
  assert.equal(anon.saved, false);
});

test('an upload stays on the key that started it; a busy key asks the app to upload that part again on another key', async () => {
  resetGeminiKeyRest();
  const fake = backend({ keys: { 'up-k1': 'busy-audio' } });
  const handler = createGocTranscribeHandler(fake.send, { GEMINI_API_KEY_1: 'up-k1', GEMINI_API_KEY_3: 'up-k3' }, async () => {});
  const started = await (await post(handler, { action: 'start', consent: true, mimeType: 'audio/aac', bytes: 100 })).json();
  assert.equal(started.mode, 'direct');
  const busy = await post(handler, { action: 'transcribe', consent: true, ticket: started.ticket, file: 'files/f1', offsetSec: 0, durationSec: 30 });
  assert.equal(busy.status, 429); assert.equal((await busy.json()).restart, true);
  // Every call for that file used key 1 (its Google project); the next upload starts on key 3.
  assert.ok(fake.calls.filter((c) => c.url.includes('/files/f1') || c.url.includes(':generateContent')).every((c) => c.key === 'up-k1'));
  const again = await (await post(handler, { action: 'start', consent: true, mimeType: 'audio/aac', bytes: 100 })).json();
  const ok = await post(handler, { action: 'transcribe', consent: true, ticket: again.ticket, file: 'files/f2', offsetSec: 0, durationSec: 30 });
  assert.equal(ok.status, 200);
  assert.equal(fake.calls.filter((c) => c.url === GEMINI_UPLOAD).at(-1).key, 'up-k3');
  resetGeminiKeyRest();
});

test('the analyze prompt keeps the form fields and the evidence rules', () => {
  assert.match(GOC_EXTRACT_RULES, /Only what was said/u); assert.match(GOC_EXTRACT_RULES, /character for character/u);
  assert.match(GOC_EXTRACT_RULES, /Leave a field out when it was not discussed/u); assert.match(GOC_EXTRACT_RULES, /Never invent or infer medical facts/u);
  assert.ok(GOC_ANALYZE_SCHEMA.properties.suggestions.items.properties.field.enum.includes('cpr'));
  assert.deepEqual(checkedGocAnalysis(null, LINES).suggestions, []);
});
