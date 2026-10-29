import test from 'node:test';
import assert from 'node:assert/strict';
import { configuration, createSaharaAssistHandler, localReserve } from '../app/sahara-assist-server.ts';

const TOKEN = 'Bearer aaa.bbb.ccc';
const ID = '6f9619ff-8b86-4d01-b42d-00cf4fc964ff';
const hash = 'a'.repeat(64);
function fakeFetch({ reserve = 200, model = '{"found":true,"answer":"Your review is on 12/11/2026.","citations":[{"document":"D3","page":2,"line":2}]}' } = {}) {
  const calls = [];
  const send = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith('/auth/v1/user')) return Response.json({ id: ID, email_confirmed_at: '2026-10-01T00:00:00Z' });
    if (String(url).includes('sahara_reserve_assist')) return reserve === 404 ? new Response('missing', { status: 404 }) : Response.json(true, { status: reserve });
    if (String(url).endsWith('/chat/completions')) return Response.json({ choices: [{ finish_reason: 'stop', message: { role: 'assistant', content: model } }] });
    throw new Error(`unexpected ${url}`);
  };
  return { send, calls };
}
const askBody = (documents) => JSON.stringify({ requestId: crypto.randomUUID(), consent: true, question: 'When is my review?', sourceHash: hash, documents });
const request = (path, body) => new Request(`https://sahara.wedevit.in/api/sahara-assist${path}`, { method: body ? 'POST' : 'GET', body, headers: { Authorization: TOKEN, 'Content-Type': 'application/json' } });
const docs = Array.from({ length: 12 }, (_, index) => ({ ref: `D${index + 1}`, lines: [{ page: 2, line: 2, text: `Review in OPD on 12/11/2026 (${index})` }] }));

test('a Gemini key alone sets up drafting, asking, translation and transcription', () => {
  const config = configuration({ GEMINI_API_KEY: 'test-key' });
  assert.equal(config.ready, true); assert.equal(config.audioReady, true);
  assert.equal(config.base, 'https://generativelanguage.googleapis.com/v1beta/openai');
  assert.equal(config.model, 'gemini-2.5-flash'); assert.equal(config.label, 'Google Gemini');
  assert.equal(configuration({}).ready, false);
});

test('status needs a signed-in Sahara account and never exposes the key', async () => {
  const { send } = fakeFetch();
  const handler = createSaharaAssistHandler(send, { GEMINI_API_KEY: 'secret-test-key' });
  const ok = await handler(request(''));
  const body = await ok.json();
  assert.equal(body.askReady, true); assert.ok(!JSON.stringify(body).includes('secret-test-key'));
  const anonymous = await handler(new Request('https://sahara.wedevit.in/api/sahara-assist'));
  assert.equal(anonymous.status, 401);
});

test('ask accepts retrieved lines from up to 20 documents and checks the citations', async () => {
  const { send, calls } = fakeFetch();
  const response = await createSaharaAssistHandler(send, { GEMINI_API_KEY: 'k' })(request('/ask', askBody(docs)));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.citations, [{ document: 'D3', page: 2, line: 2 }]);
  const chat = calls.find((call) => call.url.endsWith('/chat/completions'));
  assert.equal(chat.init.headers.Authorization, 'Bearer k');
  const tooMany = Array.from({ length: 21 }, (_, index) => ({ ref: `D${index + 1}`, lines: [{ page: 1, line: 1, text: 'x' }] }));
  assert.equal((await createSaharaAssistHandler(send, { GEMINI_API_KEY: 'k' })(request('/ask', askBody(tooMany)))).status, 400);
});

test('an answer citing a line that was not sent, or a number not in it, is refused', async () => {
  const wrongLine = fakeFetch({ model: '{"found":true,"answer":"On 12/11/2026.","citations":[{"document":"D1","page":9,"line":9}]}' });
  assert.equal((await createSaharaAssistHandler(wrongLine.send, { GEMINI_API_KEY: 'k' })(request('/ask', askBody(docs)))).status, 502);
  const wrongNumber = fakeFetch({ model: '{"found":true,"answer":"On 13/11/2026.","citations":[{"document":"D1","page":2,"line":2}]}' });
  assert.equal((await createSaharaAssistHandler(wrongNumber.send, { GEMINI_API_KEY: 'k' })(request('/ask', askBody(docs)))).status, 502);
});

test('without the database limit installed, a per-account limit still applies', async () => {
  assert.equal(localReserve('user-a', 'r1', 0), true);
  assert.equal(localReserve('user-a', 'r1', 120_000), false);
  assert.equal(localReserve('user-a', 'r2', 30_000), false);
  assert.equal(localReserve('user-a', 'r3', 120_000), true);
  const { send } = fakeFetch({ reserve: 404 });
  const response = await createSaharaAssistHandler(send, { GEMINI_API_KEY: 'k' })(request('/ask', askBody(docs)));
  assert.equal(response.status, 200);
});
