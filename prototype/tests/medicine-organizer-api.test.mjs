import test from 'node:test';
import assert from 'node:assert/strict';
import { GET, POST } from '../app/api/medicine-organizer/route.ts';
import { handleMedicineOrganizer } from '../supabase/functions/saanthvana-medicine-organizer/index.ts';

const endpoint = 'https://example.supabase.co';
const secret = 's'.repeat(64);
const image = { name: 'medicine.jpg', mimeType: 'image/jpeg', data: 'AQ==' };
const body = JSON.stringify({ prescriptionPages: [image], medicineImages: [image] });
const originHeaders = { Origin: 'https://care.example', 'Sec-Fetch-Site': 'same-origin', 'Content-Type': 'application/json' };

function setup(t, fetcher, { gemini = true } = {}) {
  const oldFetch = globalThis.fetch;
  const oldDeno = globalThis.Deno;
  const names = ['SAANTHVANA_SUPABASE_URL', 'SAANTHVANA_SUPABASE_PUBLISHABLE_KEY', 'SAANTHVANA_SUPABASE_ANON_KEY', 'SAANTHVANA_MEDICINE_ORGANIZER_SECRET'];
  const prior = Object.fromEntries(names.map((name) => [name, process.env[name]]));
  process.env.SAANTHVANA_SUPABASE_URL = endpoint;
  process.env.SAANTHVANA_SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_test';
  process.env.SAANTHVANA_MEDICINE_ORGANIZER_SECRET = secret;
  globalThis.Deno = { env: { get: (name) => ({ SAANTHVANA_MEDICINE_ORGANIZER_SECRET: secret, GEMINI_API_KEY: gemini ? 'gemini-test-key' : undefined })[name] } };
  globalThis.fetch = fetcher || (() => { throw new Error('Unexpected network access'); });
  t.after(() => {
    globalThis.fetch = oldFetch;
    if (oldDeno === undefined) delete globalThis.Deno; else globalThis.Deno = oldDeno;
    for (const name of names) { if (prior[name] === undefined) delete process.env[name]; else process.env[name] = prior[name]; }
  });
}

test('readiness reports whether the Supabase proxy is configured', async (t) => {
  setup(t);
  assert.deepEqual(await (await GET()).json(), { ready: true });
  delete process.env.SAANTHVANA_MEDICINE_ORGANIZER_SECRET;
  assert.deepEqual(await (await GET()).json(), { ready: false });
});

test('proxy rejects cross-origin uploads before network access', async (t) => {
  setup(t);
  const response = await POST(new Request('https://care.example/api/medicine-organizer', { method: 'POST', headers: { ...originHeaders, Origin: 'https://other.example' }, body }));
  assert.equal(response.status, 403);
});

test('proxy sends images to Supabase with only the publishable key and private capability', async (t) => {
  setup(t, async (url, init) => {
    assert.equal(url, `${endpoint}/functions/v1/saanthvana-medicine-organizer`);
    assert.equal(init.headers.apikey, 'sb_publishable_test');
    assert.equal(init.headers.Authorization, undefined);
    assert.equal(init.headers['X-Saanthvana-Organizer-Secret'], secret);
    assert.equal(new TextDecoder().decode(init.body), body);
    return Response.json({ medicines: [], unmatchedMedicineImageIndexes: [0], warnings: [] });
  });
  const response = await POST(new Request('https://care.example/api/medicine-organizer', { method: 'POST', headers: originHeaders, body }));
  assert.equal(response.status, 200);
});

test('Edge Function rejects callers without the server capability', async (t) => {
  setup(t);
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body }));
  assert.equal(response.status, 401);
});

test('Edge Function reports a missing Gemini key without accepting images', async (t) => {
  setup(t, undefined, { gemini: false });
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Saanthvana-Organizer-Secret': secret }, body }));
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /Gemini API key/iu);
});

test('Edge Function labels images, requests non-stored structured Gemini output, and returns it', async (t) => {
  const organization = { medicines: [{ medicineName: 'Example' }], unmatchedMedicineImageIndexes: [], warnings: [] };
  setup(t, async (url, init) => {
    assert.equal(url, 'https://generativelanguage.googleapis.com/v1beta/interactions');
    assert.equal(init.headers['x-goog-api-key'], 'gemini-test-key');
    const request = JSON.parse(init.body);
    assert.equal(request.store, false);
    assert.equal(request.model, 'gemini-3.8-flash');
    assert.equal(request.generation_config.thinking_level, 'low');
    assert.deepEqual(request.input.filter((item) => item.type === 'text').slice(1).map((item) => item.text), ['PRESCRIPTION PAGE 0', 'MEDICINE STRIP IMAGE 0']);
    assert.equal(request.input.filter((item) => item.type === 'image').length, 2);
    assert.equal(request.response_format.mime_type, 'application/json');
    assert.deepEqual(request.response_format.schema.required, ['medicines', 'unmatchedMedicineImageIndexes', 'warnings']);
    return Response.json({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'text', text: JSON.stringify(organization) }] }] });
  });
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Saanthvana-Organizer-Secret': secret }, body }));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), organization);
});

test('Edge Function retries one transient Gemini failure and then returns the result', async (t) => {
  const organization = { medicines: [{ medicineName: 'Recovered' }], unmatchedMedicineImageIndexes: [], warnings: [] };
  let calls = 0;
  const oldError = console.error;
  console.error = () => {};
  t.after(() => { console.error = oldError; });
  setup(t, async () => {
    calls += 1;
    if (calls === 1) return Response.json({ error: { status: 'UNAVAILABLE' } }, { status: 503 });
    return Response.json({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'text', text: JSON.stringify(organization) }] }] });
  });
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Saanthvana-Organizer-Secret': secret }, body }));
  assert.equal(response.status, 200);
  assert.equal(calls, 2);
  assert.deepEqual(await response.json(), organization);
});

test('Edge Function reports safe provider rejection details without retrying', async (t) => {
  let calls = 0;
  const oldError = console.error;
  console.error = () => {};
  t.after(() => { console.error = oldError; });
  setup(t, async () => {
    calls += 1;
    return Response.json({ error: { status: 'INVALID_ARGUMENT', message: 'private provider detail' } }, { status: 400 });
  });
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Saanthvana-Organizer-Secret': secret }, body }));
  const payload = await response.json();
  assert.equal(response.status, 502);
  assert.equal(calls, 1);
  assert.equal(payload.code, 'provider_rejected');
  assert.doesNotMatch(payload.error, /private provider detail/iu);
});

test('Edge Function rejects malformed image payloads before calling Gemini', async (t) => {
  setup(t);
  const response = await handleMedicineOrganizer(new Request(`${endpoint}/functions/v1/saanthvana-medicine-organizer`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Saanthvana-Organizer-Secret': secret }, body: JSON.stringify({ prescriptionPages: [], medicineImages: [image] }) }));
  assert.equal(response.status, 400);
});
