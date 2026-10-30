import test from 'node:test';
import assert from 'node:assert/strict';
import { POST } from '../app/api/ed-card/route.ts';
import { sendBreakGlassSms, smsProvider } from '../app/api/ed-card/sms.ts';
import { ED_TOKEN_PATTERN, indianMobileE164, issuableProblem, quickViewSnapshot, randomUrlToken, sha256Hex } from '../app/ed-card-snapshot.ts';
import { emptyEctpr, edInterpretation } from '../app/ectpr-rules.ts';
import { ceilingLabel, cprShort, noPreferenceStandardCare, transferLabel } from '../app/ectpr-labels.ts';

const SUPABASE = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const post = (body) => POST(new Request('http://localhost/api/ed-card', { method: 'POST', headers: { 'Content-Type': 'application/json', 'User-Agent': 'test-agent' }, body: JSON.stringify(body) }));

function signedRecord() {
  const r = emptyEctpr('P1', { name: 'Asha Test (fictional)', uhid: 'UH-1', dob: '1950-01-01', address: '12 Example Road', phone: '9876500000' }, 'Dr Test', new Date('2026-10-20T10:00:00Z'));
  r.status = 'ACTIVE';
  r.reviewDue = '2099-01-01';
  r.signatures = [{ role: 'TREATING_CONSULTANT', name: 'Dr Test', registrationNo: 'KMC-1', signedAt: '2026-10-20T10:00:00Z' }];
  r.decisionMakers.primary = { name: 'Ravi', relation: 'Son', phone24h: '98765 43210', idType: 'AADHAAR', idLast4: '1234', authorityBasis: 'NAMED_BY_PATIENT' };
  r.values.ownWords = 'I want to be at home.';
  r.choices.cpr = 'DNACPR';
  return r;
}

function setup(t, handler, env = {}) {
  const oldFetch = globalThis.fetch;
  const names = ['SUPABASE_SERVICE_ROLE_KEY', 'TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM', 'MSG91_AUTH_KEY', 'MSG91_TEMPLATE_ID'];
  const prior = Object.fromEntries(names.map((n) => [n, process.env[n]]));
  for (const n of names) delete process.env[n];
  Object.assign(process.env, { SUPABASE_SERVICE_ROLE_KEY: 'sb_secret_test', ...env });
  const calls = [];
  globalThis.fetch = async (url, init = {}) => { calls.push({ url: String(url), init }); return handler(String(url), init); };
  t.after(() => {
    globalThis.fetch = oldFetch;
    for (const n of names) { if (prior[n] === undefined) delete process.env[n]; else process.env[n] = prior[n]; }
  });
  return calls;
}

test('tokens are random, >= 128 bits and URL-safe; phone numbers normalise', async () => {
  const a = randomUrlToken(32);
  assert.match(a, ED_TOKEN_PATTERN);
  assert.equal(a.length, 43);
  assert.notEqual(a, randomUrlToken(32));
  assert.match(randomUrlToken(16), ED_TOKEN_PATTERN);
  assert.equal(indianMobileE164('98765 43210'), '+919876543210');
  assert.equal(indianMobileE164('+91 98765-43210'), '+919876543210');
  assert.equal(indianMobileE164('09123456789'), '+919123456789');
  assert.equal(indianMobileE164('9123456789'), '+919123456789');
  assert.equal(indianMobileE164('12345'), null);
  assert.equal((await sha256Hex('abc')).slice(0, 8), 'ba7816bf');
});

test('only a consultant-signed Active plan can go on a card (V7); QR view hides private fields', () => {
  const r = signedRecord();
  assert.equal(issuableProblem(r), null);
  assert.match(issuableProblem({ ...r, status: 'DRAFT' }), /Active/);
  assert.match(issuableProblem({ ...r, signatures: [] }), /consultant/);
  const q = quickViewSnapshot(r);
  assert.equal(q.patient.address, '');
  assert.equal(q.patient.dob, '');
  assert.equal(q.decisionMakers.primary.idLast4, null);
  assert.equal(q.decisionMakers.primary.phone24h, '98765 43210');
  assert.equal(q.values.ownWords, '');
  assert.equal(q.choices.cpr, 'DNACPR');
});

test('unticked choices read "No preference recorded", in English and Hindi', () => {
  const r = emptyEctpr('P1', {}, 'x');
  assert.equal(transferLabel(null, 'en'), noPreferenceStandardCare('en'));
  assert.equal(ceilingLabel(null, 'hi'), 'कोई पसंद दर्ज नहीं — सामान्य इलाज दें');
  assert.equal(cprShort(r, 'en'), 'No preference recorded');
  assert.equal(edInterpretation(null).standardCareApplies, true);
});

test('no service key or no table gives a clear, honest answer', async (t) => {
  const token = randomUrlToken();
  setup(t, () => json({ code: 'PGRST205', message: "Could not find the table 'public.sahara_ed_cards' in the schema cache" }, 404));
  let res = await post({ action: 'view', token });
  assert.equal(res.status, 503);
  assert.equal((await res.json()).status, 'not-set-up');
  delete process.env.SUPABASE_SERVICE_ROLE_KEY;
  res = await post({ action: 'view', token });
  assert.equal((await res.json()).status, 'not-configured');
  res = await post({ action: 'view', token: 'short' });
  assert.equal(res.status, 404);
});

test('issue stores the snapshot with the key only in the apikey header; view logs QR_VIEW and hides private fields', async (t) => {
  const token = randomUrlToken();
  const manageKey = randomUrlToken();
  const record = signedRecord();
  const rows = [];
  const calls = setup(t, async (url, init) => {
    if (url.startsWith(`${SUPABASE}/rest/v1/sahara_ed_cards`) && init.method === 'POST') { rows.push(JSON.parse(init.body)); return new Response(null, { status: 201 }); }
    if (url.startsWith(`${SUPABASE}/rest/v1/sahara_ed_cards?token=eq.`)) return json(rows.map((r) => ({ ...r, created_at: '2026-10-29T00:00:00Z', revoked_at: null })));
    if (url === `${SUPABASE}/rest/v1/sahara_ed_access_log`) return new Response(null, { status: 201 });
    throw new Error(`unexpected ${url}`);
  });
  let res = await post({ action: 'issue', token, manageKey, patientRef: 'P1', snapshot: record });
  assert.equal(res.status, 201);
  assert.equal(rows[0].manage_key_hash, await sha256Hex(manageKey));
  assert.equal(calls[0].init.headers.apikey, 'sb_secret_test');
  assert.equal(calls[0].init.headers.Authorization, undefined);
  res = await post({ action: 'issue', token: randomUrlToken(), manageKey, patientRef: 'P1', snapshot: { ...record, status: 'DRAFT' } });
  assert.equal(res.status, 422);

  res = await post({ action: 'view', token });
  const body = await res.json();
  assert.equal(body.status, 'live');
  assert.equal(body.full, false);
  assert.equal(body.record.patient.address, '');
  const log = calls.find((c) => c.url.endsWith('/sahara_ed_access_log'));
  assert.equal(JSON.parse(log.init.body).kind, 'QR_VIEW');
});

test('revoked cards show only "revoked"; revoking needs the manage key', async (t) => {
  const token = randomUrlToken();
  const manageKey = randomUrlToken();
  const row = { token, patient_ref: 'P1', record_id: 'r', version: 1, snapshot: signedRecord(), manage_key_hash: await sha256Hex(manageKey), created_at: '2026-10-29T00:00:00Z', revoked_at: null };
  setup(t, async (url, init) => {
    if (init.method === 'PATCH') { row.revoked_at = '2026-10-29T01:00:00Z'; return new Response(null, { status: 204 }); }
    if (url.includes('sahara_ed_cards?token=eq.')) return json([row]);
    return new Response(null, { status: 201 });
  });
  let res = await post({ action: 'revoke', token, manageKey: randomUrlToken() });
  assert.equal(res.status, 403);
  res = await post({ action: 'revoke', token, manageKey, reason: 'Plan cancelled' });
  assert.equal((await res.json()).status, 'revoked');
  res = await post({ action: 'view', token });
  const body = await res.json();
  assert.equal(body.status, 'revoked');
  assert.equal(body.record, undefined);
});

test('break-glass needs a reason, logs BREAK_GLASS, and reports SMS honestly', async (t) => {
  const token = randomUrlToken();
  const row = { token, patient_ref: 'P1', record_id: 'r', version: 1, snapshot: signedRecord(), manage_key_hash: 'a'.repeat(64), created_at: '2026-10-29T00:00:00Z', revoked_at: null };
  const calls = setup(t, async (url) => url.includes('sahara_ed_cards?token=eq.') ? json([row]) : new Response(null, { status: 201 }));
  let res = await post({ action: 'break-glass', token, reason: 'short' });
  assert.equal(res.status, 400);
  res = await post({ action: 'break-glass', token, reason: 'Unresponsive in ED, need full record' });
  const body = await res.json();
  assert.equal(body.full, true);
  assert.equal(body.record.values.ownWords, 'I want to be at home.');
  assert.equal(body.sms.sent, false);
  assert.match(body.sms.message, /SMS not configured/);
  const log = JSON.parse(calls.find((c) => c.url.endsWith('/sahara_ed_access_log')).init.body);
  assert.equal(log.kind, 'BREAK_GLASS');
  assert.equal(log.sms_status, 'not-configured');
  assert.equal(log.reason, 'Unresponsive in ED, need full record');
});

test('SMS goes through Twilio when its env vars are set', async (t) => {
  assert.equal(smsProvider({}), null);
  assert.equal(smsProvider({ MSG91_AUTH_KEY: 'k', MSG91_TEMPLATE_ID: 't' }), 'msg91');
  let seen;
  const result = await sendBreakGlassSms({ phone: '98765 43210', patientFirstName: 'Asha', at: new Date('2026-10-29T10:00:00Z') },
    { TWILIO_ACCOUNT_SID: 'AC1', TWILIO_AUTH_TOKEN: 'tok', TWILIO_FROM: '+15550000000' },
    async (url, init) => { seen = { url, init }; return json({ sid: 'SM1' }, 201); });
  assert.equal(result.sent, true);
  assert.match(seen.url, /Accounts\/AC1\/Messages\.json$/);
  assert.equal(new URLSearchParams(seen.init.body).get('To'), '+919876543210');
  const noNumber = await sendBreakGlassSms({ phone: '', patientFirstName: 'Asha', at: new Date() }, { MSG91_AUTH_KEY: 'k', MSG91_TEMPLATE_ID: 't' }, async () => json({}));
  assert.deepEqual(noNumber, { sent: false, provider: 'msg91', status: 'no-valid-number' });
  void t;
});
