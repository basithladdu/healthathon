import test from 'node:test';
import assert from 'node:assert/strict';
import { chatProviders, audioProviders, chainLabel, runChatChain, ChainFailure, FinalOutputError, stripThinking } from '../app/ai-providers.ts';
import { configuration, createSaharaAssistHandler } from '../app/sahara-assist-server.ts';

const ALL = { GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q', OPENROUTER_API_KEY: 'o', HF_TOKEN: 'h', CLOUDFLARE_ACCOUNT_ID: 'a'.repeat(32), CLOUDFLARE_API_TOKEN: 'c' };
const ok = (content, extra = {}) => Response.json({ choices: [{ finish_reason: 'stop', message: { role: 'assistant', content } }], ...extra });
const host = (url) => new URL(url).hostname;

/** Synthetic transport: each provider host gets a scripted reply; nothing reaches a live service. */
function script(replies) {
  const calls = [];
  const send = async (url, init = {}) => {
    calls.push({ url: String(url), host: host(String(url)), body: typeof init.body === 'string' ? JSON.parse(init.body) : init.body, headers: init.headers ?? {} });
    const make = replies[host(String(url))];
    if (!make) throw new Error(`unexpected ${url}`);
    return make(init);
  };
  return { send, calls };
}
const hang = (init) => new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(init.signal.reason), { once: true }));

test('the chain is Gemini, Groq, OpenRouter, Hugging Face, Cloudflare; missing keys are left out', () => {
  assert.deepEqual(chatProviders(ALL).map((p) => p.id), ['gemini', 'groq', 'openrouter', 'huggingface', 'cloudflare']);
  assert.deepEqual(chatProviders({ GROQ_API_KEY: 'q', HF_TOKEN: 'h' }).map((p) => p.id), ['groq', 'huggingface']);
  assert.deepEqual(chatProviders({ ...ALL, SAHARA_AI_CHAIN: 'cloudflare, groq' }).map((p) => p.id), ['cloudflare', 'groq']);
  assert.equal(chatProviders({}).length, 0);
  // A paid OpenRouter model is never used, and a malformed Cloudflare account id is ignored.
  assert.equal(chatProviders({ OPENROUTER_API_KEY: 'o', OPENROUTER_MODEL: 'openai/gpt-5' }).length, 0);
  assert.equal(chatProviders({ CLOUDFLARE_ACCOUNT_ID: '../x', CLOUDFLARE_API_TOKEN: 'c' }).length, 0);
  const custom = chatProviders({ ...ALL, SAHARA_AI_BASE_URL: 'https://llm.example.org/v1/', SAHARA_AI_MODEL: 'm', SAHARA_AI_API_KEY: 'k' });
  assert.equal(custom[0].id, 'custom'); assert.equal(custom[0].base, 'https://llm.example.org/v1');
  const cf = chatProviders(ALL).find((p) => p.id === 'cloudflare');
  assert.equal(cf.base, `https://api.cloudflare.com/client/v4/accounts/${'a'.repeat(32)}/ai/v1`);
});

test('the consent label names every service that may receive the words, within 60 characters', () => {
  const label = chainLabel(chatProviders(ALL));
  assert.ok(label.startsWith('the AI service / Groq / OpenRouter')); assert.ok(label.length <= 60);
  assert.equal(configuration({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }).label, 'the AI service / Groq');
  assert.equal(chainLabel(chatProviders(ALL), 'Sahara AI partners'), 'Sahara AI partners');
});

test('on 429, 5xx, a bad key or a network error the next provider answers and is reported', async () => {
  const { send, calls } = script({
    'generativelanguage.googleapis.com': () => new Response('busy', { status: 429 }),
    'api.groq.com': () => new Response('down', { status: 503 }),
    'openrouter.ai': () => ok('{"x":1}', { model: 'google/gemma-4-26b-a4b-it:free' }),
    'router.huggingface.co': () => ok('{"x":2}'),
  });
  const result = await runChatChain(chatProviders(ALL), { system: 's', user: 'u', json: true }, (c) => JSON.parse(c), { send });
  assert.equal(result.provider.id, 'openrouter'); assert.deepEqual(result.value, { x: 1 });
  assert.equal(result.model, 'google/gemma-4-26b-a4b-it:free', 'OpenRouter reports which free model answered');
  assert.deepEqual(calls.map((c) => c.host), ['generativelanguage.googleapis.com', 'api.groq.com', 'openrouter.ai']);
  assert.deepEqual(result.attempts.map((a) => [a.provider, a.ok, a.reason]), [['gemini', false, 'busy'], ['groq', false, 'http'], ['openrouter', true, undefined]]);
  // JSON mode where documented; OpenRouter only ever lists ":free" fallbacks; keys go in the Authorization header.
  assert.deepEqual(calls[0].body.response_format, { type: 'json_object' });
  assert.ok(calls[2].body.models.every((m) => m.endsWith(':free')));
  assert.equal(calls[1].headers.Authorization, 'Bearer q');
});

test('a provider that does not answer in time is abandoned for the next one', async () => {
  const { send } = script({ 'generativelanguage.googleapis.com': hang, 'api.groq.com': () => ok('fine') });
  const result = await runChatChain(chatProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }), { system: 's', user: 'u', json: false }, (c) => c, { send, perAttemptMs: 50 });
  assert.equal(result.provider.id, 'groq'); assert.equal(result.attempts[0].reason, 'timeout');
});

test('Hugging Face and Cloudflare get the JSON instruction in the prompt instead of response_format', async () => {
  const { send, calls } = script({ 'router.huggingface.co': () => new Response('', { status: 402 }), 'api.cloudflare.com': () => ok('<think>{"no":1}</think>Sure: {"ok":true}') });
  const result = await runChatChain(chatProviders({ HF_TOKEN: 'h', CLOUDFLARE_ACCOUNT_ID: 'b'.repeat(32), CLOUDFLARE_API_TOKEN: 'c' }), { system: 's', user: 'u', json: true },
    (c) => JSON.parse(stripThinking(c).slice(stripThinking(c).indexOf('{'))), { send });
  assert.deepEqual(result.value, { ok: true });
  assert.ok(calls.every((c) => c.body.response_format === undefined));
  assert.match(calls[1].body.messages[0].content, /JSON object only/u);
});

test('all busy is a 429; anything else is a 502; no providers is 503', async () => {
  const busy = script({ 'generativelanguage.googleapis.com': () => new Response('', { status: 429 }), 'api.groq.com': () => new Response('', { status: 429 }) });
  await assert.rejects(runChatChain(chatProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }), { system: 's', user: 'u', json: false }, (c) => c, { send: busy.send }),
    (error) => error instanceof ChainFailure && error.status === 429 && error.code === 'provider_busy');
  const mixed = script({ 'generativelanguage.googleapis.com': () => new Response('', { status: 429 }), 'api.groq.com': () => new Response('', { status: 401 }) });
  await assert.rejects(runChatChain(chatProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }), { system: 's', user: 'u', json: false }, (c) => c, { send: mixed.send }),
    (error) => error instanceof ChainFailure && error.status === 502);
  await assert.rejects(runChatChain([], { system: 's', user: 'u', json: false }, (c) => c, { send: mixed.send }), (error) => error.status === 503);
});

test('an unusable answer tries the next provider; if all are unusable the checker error is kept', async () => {
  const { send, calls } = script({ 'generativelanguage.googleapis.com': () => ok('not json'), 'api.groq.com': () => ok('{"ok":1}') });
  const two = chatProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' });
  assert.equal((await runChatChain(two, { system: 's', user: 'u', json: true }, (c) => JSON.parse(c), { send })).provider.id, 'groq');
  assert.equal(calls.length, 2);
  const bad = script({ 'generativelanguage.googleapis.com': () => ok('x'), 'api.groq.com': () => ok('y') });
  await assert.rejects(runChatChain(two, { system: 's', user: 'u', json: true }, () => { throw new RangeError('mine'); }, { send: bad.send }), RangeError);
  // A final refusal stops the chain: no second model is asked.
  const refuse = script({ 'generativelanguage.googleapis.com': () => ok('x'), 'api.groq.com': () => ok('y') });
  await assert.rejects(runChatChain(two, { system: 's', user: 'u', json: true }, () => { throw new FinalOutputError(new TypeError('stop')); }, { send: refuse.send }), TypeError);
  assert.equal(refuse.calls.length, 1);
});

test('a truncated answer counts as a failure', async () => {
  const { send } = script({ 'generativelanguage.googleapis.com': () => Response.json({ choices: [{ finish_reason: 'length', message: { content: '{"a"' } }] }), 'api.groq.com': () => ok('done') });
  assert.equal((await runChatChain(chatProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }), { system: 's', user: 'u', json: false }, (c) => c, { send })).value, 'done');
});

// ---- Through the Android endpoint ----
const TOKEN = 'Bearer aaa.bbb.ccc';
const ID = '6f9619ff-8b86-4d01-b42d-00cf4fc964ff';
const supabase = { 'kjnhtakqzboayqorrlue.supabase.co': (init) => String(init.body ?? '').includes('p_request_id') ? Response.json(true) : Response.json({ id: ID, email_confirmed_at: '2026-10-01T00:00:00Z' }) };
const request = (path, body) => new Request(`https://sahara.wedevit.in/api/sahara-assist${path}`, { method: body ? 'POST' : 'GET', body: body && JSON.stringify(body), headers: { Authorization: TOKEN, 'Content-Type': 'application/json' } });
const docs = [{ ref: 'D1', lines: [{ page: 1, line: 1, text: 'Review in OPD on 12/11/2026' }, { page: 1, line: 2, text: 'Tab paracetamol should be taken after food' }] }];
const ask = (question, extra = {}) => ({ requestId: crypto.randomUUID(), consent: true, question, sourceHash: 'a'.repeat(64), documents: docs, ...extra });

test('the Android ask answer says which free service answered, keeping the contract provider id', async () => {
  const { send } = script({ ...supabase, 'generativelanguage.googleapis.com': () => new Response('', { status: 429 }),
    'api.groq.com': () => ok('{"found":true,"answer":"Your review is on 12/11/2026.","citations":[{"document":"D1","page":1,"line":1}]}') });
  const handler = createSaharaAssistHandler(send, { GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' });
  const status = await (await handler(request(''))).json();
  assert.equal(status.label, 'the AI service / Groq'); assert.deepEqual(status.providers, ['the AI service', 'Groq']);
  const response = await handler(request('/ask', ask('When is my review?')));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.provider, 'openai-compatible'); assert.equal(body.answeredBy, 'Groq'); assert.equal(body.model, 'llama-3.3-70b-versatile');
});

test('Android transcription falls back from Gemini to Groq Whisper', async () => {
  const { send, calls } = script({ ...supabase, 'generativelanguage.googleapis.com': () => new Response('', { status: 503 }), 'api.groq.com': () => Response.json({ text: 'नमस्ते' }) });
  assert.equal(audioProviders({ GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' }).length, 2);
  const audio = btoa(String.fromCharCode(...new Uint8Array(64)));
  const response = await createSaharaAssistHandler(send, { GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' })(request('/transcribe',
    { requestId: crypto.randomUUID(), consent: true, recordingConsent: true, processingConsent: true, durationMs: 4000, language: 'hi-IN', audio: { mimeType: 'audio/m4a', data: audio } }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.transcript, 'नमस्ते'); assert.equal(body.answeredBy, 'Groq'); assert.equal(body.model, 'whisper-large-v3-turbo');
  const whisper = calls.find((c) => c.url.endsWith('/audio/transcriptions'));
  assert.equal(whisper.body.get('language'), 'hi');
});
