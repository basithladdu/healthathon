import test from 'node:test';
import assert from 'node:assert/strict';
import { createTranslateHandler, sameNumbers, asciiDigits, translateLanguage, translateAllowed, BHASHINI_CONFIG_URL, BHASHINI_MEITY_PIPELINE } from '../app/translate-server.ts';

const TOKEN = 'Bearer aaa.bbb.ccc';
const CALLBACK = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';
function transport({ bhashini = 'ok', target = 'నాకు 2 రోజులుగా నొప్పి ఉంది', llm = 'मुझे 2 दिन से दर्द है', callback = CALLBACK } = {}) {
  const calls = [];
  const send = async (url, init = {}) => {
    const text = String(url);
    calls.push({ url: text, init, body: typeof init.body === 'string' ? JSON.parse(init.body) : null });
    if (text.endsWith('/auth/v1/user')) return Response.json({ id: 'u1', email_confirmed_at: '2026-10-01T00:00:00Z' });
    if (text === BHASHINI_CONFIG_URL) return bhashini === 'config-down' ? new Response('', { status: 500 }) : Response.json({
      pipelineResponseConfig: [{ taskType: 'translation', config: [{ serviceId: 'ai4bharat/indictrans-v2-all-gpu--t4', language: { sourceLanguage: 'en', targetLanguage: 'te' } }] }],
      pipelineInferenceAPIEndPoint: { callbackUrl: callback, inferenceApiKey: { name: 'Authorization', value: 'inference-key' } } });
    if (text === CALLBACK || text === callback) return bhashini === 'compute-down' ? new Response('', { status: 503 }) : Response.json({ pipelineResponse: [{ taskType: 'translation', output: [{ source: 'x', target }] }] });
    if (text.endsWith('/chat/completions')) return Response.json({ choices: [{ finish_reason: 'stop', message: { content: llm } }] });
    throw new Error(`unexpected ${text}`);
  };
  return { send, calls };
}
const post = (body, headers = { Authorization: TOKEN }) => new Request('https://sahara.wedevit.in/api/translate', { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers },
  body: JSON.stringify({ consent: true, ...body }) });
const BHASHINI = { BHASHINI_USER_ID: 'user', BHASHINI_API_KEY: 'ulca-key' };

test('Bhashini translates own words through the ULCA config and compute calls, labelled machine translation', async () => {
  const { send, calls } = transport();
  const response = await createTranslateHandler(send, { ...BHASHINI, GROQ_API_KEY: 'q' })(post({ text: 'I have had pain for 2 days', sourceLanguage: 'en-IN', targetLanguage: 'te' }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.label, 'Machine translation'); assert.equal(body.answeredBy, 'Bhashini'); assert.equal(body.original, 'I have had pain for 2 days');
  assert.equal(body.requiresReview, true);
  const config = calls.find((c) => c.url === BHASHINI_CONFIG_URL);
  assert.equal(config.init.headers.userID, 'user'); assert.equal(config.init.headers.ulcaApiKey, 'ulca-key');
  assert.equal(config.body.pipelineRequestConfig.pipelineId, BHASHINI_MEITY_PIPELINE);
  const compute = calls.find((c) => c.url === CALLBACK);
  assert.equal(compute.init.headers.Authorization, 'inference-key');
  assert.equal(compute.body.pipelineTasks[0].config.serviceId, 'ai4bharat/indictrans-v2-all-gpu--t4');
  assert.deepEqual(compute.body.inputData.input, [{ source: 'I have had pain for 2 days' }]);
  assert.ok(!calls.some((c) => c.url.endsWith('/chat/completions')));
});

test('when Bhashini fails, changes a number, or is not set up, the free LLM chain translates', async () => {
  const down = transport({ bhashini: 'config-down' });
  const a = await (await createTranslateHandler(down.send, { ...BHASHINI, GROQ_API_KEY: 'q' })(post({ text: 'I have had pain for 2 days', sourceLanguage: 'en', targetLanguage: 'hi' }))).json();
  assert.equal(a.answeredBy, 'Groq'); assert.equal(a.translation, 'मुझे 2 दिन से दर्द है'); assert.equal(a.label, 'Machine translation');
  const changed = transport({ bhashini: 'ok', target: 'నాకు 3 రోజులుగా నొప్పి ఉంది', llm: 'నాకు 2 రోజులుగా నొప్పి ఉంది' });
  const b = await (await createTranslateHandler(changed.send, { ...BHASHINI, GEMINI_API_KEY: 'g' })(post({ text: 'I have had pain for 2 days', sourceLanguage: 'en', targetLanguage: 'te' }))).json();
  assert.equal(b.answeredBy, 'the AI service');
  const none = transport({ llm: 'It hurts' });
  const c = await (await createTranslateHandler(none.send, { OPENROUTER_API_KEY: 'o' })(post({ text: 'Dard hai', sourceLanguage: 'hi', targetLanguage: 'en' }))).json();
  assert.equal(c.answeredBy, 'OpenRouter');
  assert.ok(!none.calls.some((call) => call.url === BHASHINI_CONFIG_URL));
});

test('a Bhashini config pointing anywhere but Bhashini is not followed', async () => {
  const { send, calls } = transport({ callback: 'https://evil.example/collect' });
  const body = await (await createTranslateHandler(send, { ...BHASHINI, GROQ_API_KEY: 'q' })(post({ text: 'pain for 2 days', sourceLanguage: 'en', targetLanguage: 'hi' }))).json();
  assert.equal(body.answeredBy, 'Groq');
  assert.ok(!calls.some((c) => c.url.startsWith('https://evil.example')));
});

test('a translation that changes a number from every service is refused', async () => {
  const { send } = transport({ bhashini: 'compute-down', llm: '5 दिन' });
  const response = await createTranslateHandler(send, { ...BHASHINI, GROQ_API_KEY: 'q' })(post({ text: 'for 2 days', sourceLanguage: 'en', targetLanguage: 'hi' }));
  assert.equal(response.status, 502); assert.equal((await response.json()).code, 'translation_needs_attention');
});

test('consent, languages, size and sign-in are checked', async () => {
  const { send } = transport();
  const handler = createTranslateHandler(send, { GROQ_API_KEY: 'q' });
  assert.equal((await handler(post({ consent: false, text: 'x', sourceLanguage: 'en', targetLanguage: 'hi' }))).status, 400);
  assert.equal((await handler(post({ text: 'x', sourceLanguage: 'en', targetLanguage: 'ta' }))).status, 400);
  assert.equal((await handler(post({ text: 'x', sourceLanguage: 'hi', targetLanguage: 'hi' }))).status, 400);
  assert.equal((await handler(post({ text: 'x'.repeat(2001), sourceLanguage: 'en', targetLanguage: 'hi' }))).status, 413);
  assert.equal((await handler(post({ text: 'x', sourceLanguage: 'en', targetLanguage: 'hi' }, {}))).status, 401, 'no token and no same-site origin');
  const browser = await handler(post({ text: 'pain for 2 days', sourceLanguage: 'en', targetLanguage: 'hi' }, { Origin: 'https://sahara.wedevit.in', 'Sec-Fetch-Site': 'same-origin' }));
  assert.equal(browser.status, 200);
  assert.equal((await createTranslateHandler(send, {})(post({ text: 'x', sourceLanguage: 'en', targetLanguage: 'hi' }))).status, 503);
  const status = await (await handler(new Request('https://sahara.wedevit.in/api/translate'))).json();
  assert.deepEqual(status.languages, ['en', 'hi', 'te', 'kn', 'ur']); assert.equal(status.label, 'Machine translation');
});

test('Indic digits count as the same numbers; language codes accept xx-IN', () => {
  assert.equal(asciiDigits('२ दिन, ౩ రోజులు, ೪, ٥, ۶'), '2 दिन, 3 రోజులు, 4, 5, 6');
  assert.equal(sameNumbers('Take 2 tablets at 10:30', '१०:३० बजे २ गोलियाँ'), true);
  assert.equal(sameNumbers('2 days', '3 दिन'), false);
  assert.equal(translateLanguage('ur-IN'), 'ur'); assert.equal(translateLanguage('ta-IN'), '');
  for (let i = 0; i < 30; i += 1) assert.equal(translateAllowed('rate-test', 1000 + i), true);
  assert.equal(translateAllowed('rate-test', 2000), false);
});
