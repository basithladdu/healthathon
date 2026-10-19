import test from 'node:test';
import assert from 'node:assert/strict';
import { careAssistTurns, checkedCareSelection, careSourceHash, careTranslationChunks, translatedNumbersMatch } from '../app/care-assist-contract.ts';
import { careAssistConfiguration, createCareAssistHandler } from '../app/care-assist-server.ts';
import { careAssist, careTranslate, confirmedTranscriptTurn, CareAssistError } from '../app/care-assist-client.ts';

const source = 'Patient: I do not want to decide yet.\r\n\r\nDoctor: We can discuss the options again.';
const selection = { priorities: [1], participants: [], topics: [3], openQuestions: [1], followUp: [] };
const env = { CARE_AI_ENABLED: 'true', CARE_AI_PROVIDER: 'sarvam', CARE_AI_DATA_TERMS_APPROVED: 'true', CARE_AI_DATA_TERMS_REFERENCE: 'synthetic-contract-test', SARVAM_API_KEY: 'synthetic-test-key', CARE_AI_AUDIO_ENABLED: 'true', CARE_AI_TRANSLATION_ENABLED: 'true' };
const token = 'synthetic.token.only';
const requestId = '6c845393-41b6-4bb5-9a6b-c3fd53c4ed8d';
const json = (value, status = 200) => Response.json(value, { status });
const request = (body, headers = {}) => new Request('https://sahara.example/api/care-assist', { method: 'POST', headers: { Origin: 'https://sahara.example', Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ requestId, consent: true, ...body }) });
function remote(output, { quota = true, auth = true } = {}) {
  const calls = [];
  const send = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/auth/v1/user')) return auth ? json({ id: requestId, email_confirmed_at: '2026-10-19T00:00:00Z', is_anonymous: false }) : json({}, 401);
    if (url.endsWith('/rpc/sahara_reserve_assist')) return json(quota);
    return json(typeof output === 'function' ? output(url, options) : output);
  };
  return { send, calls };
}
const completion = (selected = selection) => ({ model: 'sarvam-105b', choices: [{ finish_reason: 'stop', message: { role: 'assistant', content: JSON.stringify(selected) } }] });

test('turns retain original offsets, blank-line numbering, role and negation', () => {
  const turns = careAssistTurns(source);
  assert.deepEqual(turns.map((turn) => [turn.line, turn.speaker]), [[1, 'Patient'], [3, 'Doctor']]);
  for (const turn of turns) assert.equal(source.slice(turn.start, turn.end), turn.text);
  assert.match(checkedCareSelection(selection, source).fields.priorities, /do not want/u);
});

test('model output can select only whole exact turns; no invented, duplicate or inferred patient evidence', () => {
  for (const change of [{ priorities: [3] }, { priorities: [2] }, { topics: [3, 3] }, { topics: ['3'] }, { topics: ['We discussed CPR.'] }]) {
    assert.throws(() => checkedCareSelection({ ...selection, ...change }, source));
  }
  assert.throws(() => checkedCareSelection({ ...selection, diagnosis: [] }, source));
  assert.throws(() => checkedCareSelection({ ...selection, priorities: [1] }, source.replace('Patient:', 'Unknown:')));
});

test('translation chunking retains all text, CRLF and surrogate pairs', () => {
  const text = 'पहले\r\n' + 'word 😀 '.repeat(1500);
  const chunks = careTranslationChunks(text);
  assert.equal(chunks.map((chunk) => chunk.original).join(''), text);
  assert.ok(chunks.every((chunk) => chunk.original.length <= 1800 && text.slice(chunk.start, chunk.end) === chunk.original));
  assert.ok(chunks.every((chunk) => !/[\uD800-\uDBFF]$/u.test(chunk.original)));
});

test('translation rejects changed dose/number instead of silently accepting it', () => {
  assert.equal(translatedNumbersMatch('Review in 2 days at 10:30.', '2 दिनों में 10:30 पर जाँच।'), true);
  assert.equal(translatedNumbersMatch('Take 2 mg', '10 mg लें'), false);
  assert.equal(translatedNumbersMatch('No medicine changes.', '5 mg लें'), false);
});

test('keys alone never enable assistance, audio or translation', () => {
  assert.equal(careAssistConfiguration({ SARVAM_API_KEY: 'synthetic' }).ready, false);
  assert.equal(careAssistConfiguration({ ...env, CARE_AI_DATA_TERMS_REFERENCE: '' }).ready, false);
  assert.equal(careAssistConfiguration({ ...env, CARE_AI_AUDIO_ENABLED: 'false' }).audioReady, false);
});

test('disabled service makes no authentication, quota or provider request', async () => {
  const mock = remote(completion());
  const result = await createCareAssistHandler('draft', mock.send, {})(request({ source }));
  assert.equal(result.status, 503); assert.equal(mock.calls.length, 0);
  assert.match(result.headers.get('cache-control'), /no-store/u);
});

test('bad origin, missing consent and expired account cannot reach the model', async () => {
  const mock = remote(completion(), { auth: false });
  const handler = createCareAssistHandler('draft', mock.send, env);
  assert.equal((await handler(request({ source }, { Origin: 'https://evil.example' }))).status, 403);
  assert.equal((await handler(request({ source, consent: false }))).status, 400);
  assert.equal(mock.calls.length, 0);
  assert.equal((await handler(request({ source }))).status, 401);
  assert.equal(mock.calls.length, 1);
});

test('durable rejected reservation and duplicate request never reach the provider', async () => {
  const mock = remote(completion(), { quota: false });
  const result = await createCareAssistHandler('draft', mock.send, env)(request({ source }));
  assert.equal(result.status, 429); assert.equal(result.headers.get('retry-after'), '60');
  assert.equal(mock.calls.length, 2);
  assert.deepEqual(JSON.parse(mock.calls[1].options.body), { p_request_id: requestId, p_action: 'draft' });
});

test('authenticated model selection yields only hash-bound source evidence', async () => {
  const mock = remote(completion());
  const result = await createCareAssistHandler('draft', mock.send, env)(request({ source }));
  assert.equal(result.status, 200);
  const data = await result.json();
  assert.equal(data.sourceHash, await careSourceHash(source)); assert.equal(data.requiresReview, true);
  assert.equal(data.fields.priorities, 'Patient: I do not want to decide yet.');
  assert.equal(data.evidence[0].speaker, 'Patient');
  const providerBody = JSON.parse(mock.calls[2].options.body);
  assert.equal(providerBody.response_format.type, 'json_schema');
  assert.equal(providerBody.messages[1].content, JSON.stringify(careAssistTurns(source).map(({ line, text, speaker }) => ({ line, text, speaker }))));
  assert.equal('tools' in providerBody, false);
});

test('provider hallucination, changed speaker or truncated generation fills nothing', async () => {
  for (const output of [completion({ ...selection, priorities: [3] }), completion({ ...selection, topics: [999] }), { ...completion(), choices: [{ ...completion().choices[0], finish_reason: 'length' }] }]) {
    const mock = remote(output); const result = await createCareAssistHandler('draft', mock.send, env)(request({ source }));
    assert.ok(result.status >= 400); assert.equal('fields' in await result.json(), false);
  }
});

test('transcription separately checks recording/processing consent, bounded format and duration', async () => {
  const mock = remote({}); const handler = createCareAssistHandler('transcribe', mock.send, env);
  const wav = Buffer.alloc(44); wav.write('RIFF'); wav.write('WAVE', 8);
  const body = { audio: { data: wav.toString('base64'), mimeType: 'audio/wav' }, durationMs: 1000, recordingConsent: true, processingConsent: true, language: 'hi-IN' };
  assert.equal((await handler(request({ ...body, processingConsent: false }))).status, 400);
  assert.equal((await handler(request({ ...body, durationMs: 31000 }))).status, 413);
  assert.equal(mock.calls.length, 0);
});

test('transcript never invents a speaker and is retained in its original language', async () => {
  const mock = remote({ transcript: 'मैं अभी निर्णय नहीं लेना चाहता।', language_code: 'hi-IN' });
  const wav = Buffer.alloc(44); wav.write('RIFF'); wav.write('WAVE', 8);
  const result = await createCareAssistHandler('transcribe', mock.send, env)(request({ audio: { data: wav.toString('base64'), mimeType: 'audio/wav' }, durationMs: 1000, recordingConsent: true, processingConsent: true, language: 'hi-IN' }));
  assert.equal(result.status, 200); const data = await result.json();
  assert.equal(data.speaker, 'Unknown'); assert.equal(data.speakerConfirmed, false); assert.equal(data.transcript, data.originalTranscript);
  assert.equal(mock.calls[2].options.body.get('mode'), 'verbatim');
  assert.equal(mock.calls[2].options.body.has('with_diarization'), false);
});

test('translation keeps original/version hash and cannot auto-approve itself', async () => {
  const original = 'I do not want to decide yet. Review in 2 days.';
  const mock = remote({ translated_text: 'मैं अभी निर्णय नहीं लेना चाहता। 2 दिनों में जाँच।', source_language_code: 'en-IN' });
  const result = await createCareAssistHandler('translate', mock.send, env)(request({ source: original, sourceHash: await careSourceHash(original), versionId: 'version-2', sourceLanguage: 'en-IN', targetLanguage: 'hi-IN' }));
  assert.equal(result.status, 200); const data = await result.json();
  assert.equal(data.original, original); assert.equal(data.versionId, 'version-2'); assert.equal(data.requiresReview, true);
  assert.equal(data.segments[0].original, original);
});

test('client validates evidence again and rejects a response bound to another source', async () => {
  const checked = checkedCareSelection(selection, source);
  const output = { ...checked, sourceHash: await careSourceHash(source), provider: 'sarvam', model: 'sarvam-105b', requiresReview: true, generatedAt: '2026-10-19T00:00:00Z' };
  const result = await careAssist(source, token, { consent: true, request: async () => json(output) });
  assert.equal(result.fields.priorities, checked.fields.priorities);
  await assert.rejects(careAssist(source + ' Changed.', token, { consent: true, request: async () => json(output) }), CareAssistError);
});

test('correction retains machine original and requires explicit speaker review', () => {
  assert.throws(() => confirmedTranscriptTurn('I want a decision.', 'I do not want a decision.', 'Patient', 'en-IN', false), CareAssistError);
  const confirmed = confirmedTranscriptTurn('I want a decision.', 'I do not want a decision.', 'Patient', 'en-IN', true);
  assert.match(confirmed, /Unknown · en-IN · machine original/u); assert.match(confirmed, /Patient · en-IN · human-reviewed/u);
  assert.ok(confirmed.includes('I want a decision.') && confirmed.includes('I do not want a decision.'));
});

test('cancelled client request cannot return a generated draft', async () => {
  const controller = new AbortController(); controller.abort(); let called = false;
  await assert.rejects(careAssist(source, token, { consent: true, signal: controller.signal, request: async () => { called = true; return json({}); } }), (cause) => cause instanceof CareAssistError && cause.code === 'cancelled');
  assert.equal(called, false);
});
