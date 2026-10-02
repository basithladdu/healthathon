import test from 'node:test';
import assert from 'node:assert/strict';
import { GET, POST, DELETE } from '../app/api/centre-reports/route.ts';
import { handleReportRequest } from '../supabase/functions/saanthvana-centre-reports/index.ts';
import { REPORT_DIRECTORY } from '../supabase/functions/saanthvana-centre-reports/directory.ts';
import { CARE_CENTRES } from '../app/care-directory-data.ts';

const secret = 'a'.repeat(64);
const session = 'b'.repeat(64);
const endpoint = 'https://reports.supabase.co';
const reportId = '00000000-0000-4000-8000-000000000001';
const headers = { 'X-Reports-Proxy-Secret': secret, 'X-Report-Session': session };
const saved = { id: reportId, session_hash: 'private-hash', centre_id: 'hospital', hospital_name: 'Example Hospital', district: 'Bengaluru Urban', address: 'Example Hospital Road', visit_date: '2026-09-01', outcome: 'received', oral_morphine: 'unknown', reporter_role: 'doctor', receipt_path: 'private/receipt.pdf', receipt_hash: 'private-hash', created_at: '2026-09-01T10:00:00Z', latitude: null, longitude: null };

function setup(t, fetcher) {
  const oldFetch = globalThis.fetch;
  const oldDeno = globalThis.Deno;
  const names = ['SAANTHVANA_SUPABASE_URL', 'SAANTHVANA_SUPABASE_ANON_KEY', 'SAANTHVANA_REPORTS_PROXY_SECRET', 'SAANTHVANA_TRUSTED_IP_HEADER'];
  const prior = Object.fromEntries(names.map((name) => [name, process.env[name]]));
  process.env.SAANTHVANA_SUPABASE_URL = endpoint;
  process.env.SAANTHVANA_SUPABASE_ANON_KEY = 'anon-test-key';
  process.env.SAANTHVANA_REPORTS_PROXY_SECRET = secret;
  delete process.env.SAANTHVANA_TRUSTED_IP_HEADER;
  globalThis.Deno = { env: { get: (name) => ({ SUPABASE_URL: endpoint, SUPABASE_SERVICE_ROLE_KEY: 'service-test-key', SAANTHVANA_REPORTS_PROXY_SECRET: secret })[name] } };
  globalThis.fetch = fetcher || (() => { throw new Error('Unexpected network access'); });
  t.after(() => {
    globalThis.fetch = oldFetch;
    if (oldDeno === undefined) delete globalThis.Deno; else globalThis.Deno = oldDeno;
    for (const name of names) { if (prior[name] === undefined) delete process.env[name]; else process.env[name] = prior[name]; }
  });
}

function form(overrides = {}, bill) {
  const data = new FormData();
  for (const [key, value] of Object.entries({ hospitalName: 'Example Hospital', district: 'Bengaluru Urban', address: 'Example Hospital Road', visitDate: '2026-09-01', outcome: 'received', oralMorphine: 'unknown', reporterRole: 'doctor', consent: 'true', ...overrides })) data.set(key, value);
  if (bill) data.set('bill', bill);
  return data;
}
const edgePost = (body) => new Request(`${endpoint}/functions/v1/saanthvana-centre-reports`, { method: 'POST', headers, body });

test('proxy blocks cross-origin writes before network access', async (t) => {
  setup(t);
  const response = await POST(new Request('https://care.example/api/centre-reports', { method: 'POST', headers: { Origin: 'https://other.example' }, body: form() }));
  assert.equal(response.status, 403);
});

test('proxy accepts actual Host origin when Next reconstructs an internal hostname', async (t) => {
  setup(t, async () => Response.json({ report: saved }, { status: 201 }));
  const response = await POST(new Request('http://localhost:3100/api/centre-reports', { method: 'POST', headers: { Host: '127.0.0.1:3100', Origin: 'http://127.0.0.1:3100', 'Sec-Fetch-Site': 'same-origin' }, body: form() }));
  assert.equal(response.status, 201);
});

for (const [name, extra] of Object.entries({
  external: { Origin: 'http://external.example' },
  internal: { Origin: 'http://localhost:3100' },
  forwarded: { Origin: 'http://external.example', 'X-Forwarded-Host': 'external.example', 'X-Forwarded-Proto': 'http' },
  malformedHost: { Host: '127.0.0.1:3100@external.example', Origin: 'http://external.example' },
  crossSite: { Origin: 'http://127.0.0.1:3100', 'Sec-Fetch-Site': 'cross-site' },
  wrongProtocol: { Origin: 'https://127.0.0.1:3100' },
})) {
  test(`Host-based origin check still rejects ${name}`, async (t) => {
    setup(t);
    const response = await POST(new Request('http://localhost:3100/api/centre-reports', { method: 'POST', headers: { Host: '127.0.0.1:3100', ...extra }, body: form() }));
    assert.equal(response.status, 403);
  });
}

test('proxy returns configuration error without fabricating an empty list', async (t) => {
  setup(t);
  delete process.env.SAANTHVANA_SUPABASE_URL;
  const response = await GET(new Request('https://care.example/api/centre-reports'));
  assert.equal(response.status, 503);
  assert.equal((await response.json()).reports, undefined);
});

test('proxy creates a private capability and never forwards untrusted IP headers', async (t) => {
  setup(t, async (url, init) => {
    assert.equal(url, `${endpoint}/functions/v1/saanthvana-centre-reports`);
    assert.match(init.headers['X-Report-Session'], /^[a-f0-9]{64}$/);
    assert.equal(init.headers['X-Report-Client-IP'], undefined);
    return Response.json({ reports: [], ownedIds: [] });
  });
  const response = await GET(new Request('https://care.example/api/centre-reports', { headers: { 'x-forwarded-for': '1.2.3.4' } }));
  assert.equal(response.status, 200);
  assert.match(response.headers.get('set-cookie'), /HttpOnly; SameSite=Strict; Path=\/api\/centre-reports/);
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('proxy rejects oversized multipart before sending anything upstream', async (t) => {
  setup(t);
  const response = await POST(new Request('https://care.example/api/centre-reports', { method: 'POST', headers: { Origin: 'https://care.example', 'Content-Length': '5000000' }, body: form() }));
  assert.equal(response.status, 413);
});

test('proxy deletion requires a report id', async (t) => {
  setup(t);
  const response = await DELETE(new Request('https://care.example/api/centre-reports', { method: 'DELETE', headers: { Origin: 'https://care.example' } }));
  assert.equal(response.status, 400);
});

test('Edge rejects requests without the server secret', async (t) => {
  setup(t);
  assert.equal((await handleReportRequest(new Request(`${endpoint}/reports`))).status, 401);
});

test('public GET strips all receipt paths, hashes and owner identity', async (t) => {
  setup(t, async () => Response.json([saved]));
  const response = await handleReportRequest(new Request(`${endpoint}/reports`, { headers }));
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.reports[0].receiptStatus, 'attached');
  assert.equal(data.reports[0].doctorStatus, 'self-reported');
  assert.deepEqual(data.ownedIds, []);
  assert.doesNotMatch(JSON.stringify(data), /private-hash|private\/receipt|session_hash|receipt_hash/);
});

for (const [name, values] of Object.entries({ consent: { consent: 'false' }, future: { visitDate: '2999-01-01' }, invalidDate: { visitDate: '2026-02-30' }, invalidDistrict: { district: 'Delhi' }, oneCoordinate: { latitude: '12.9' }, wrongRegion: { latitude: '28.6', longitude: '77.2' }, invalidRole: { reporterRole: 'verified-doctor' } })) {
  test(`Edge rejects ${name} before database or storage mutation`, async (t) => {
    setup(t);
    assert.equal((await handleReportRequest(edgePost(form(values)))).status, 400);
  });
}

test('file validation rejects a renamed executable masquerading as PDF', async (t) => {
  setup(t);
  const response = await handleReportRequest(edgePost(form({}, new File(['MZbinary'], 'bill.pdf', { type: 'application/pdf' }))));
  assert.equal(response.status, 400);
});

test('duplicate bills or visits return conflict and never upload the bill', async (t) => {
  setup(t, async (url) => {
    assert.match(url, /rpc\/saanthvana_reserve_report$/);
    return Response.json({ code: '23505' }, { status: 409 });
  });
  assert.equal((await handleReportRequest(edgePost(form({}, new File(['%PDF-1.7 test'], 'bill.pdf', { type: 'application/pdf' }))))).status, 409);
});

test('atomic quota failure returns a rate limit', async (t) => {
  setup(t, async () => Response.json({ message: 'REPORT_RATE_LIMIT' }, { status: 400 }));
  assert.equal((await handleReportRequest(edgePost(form()))).status, 429);
});

test('successful receipt save reserves, uploads privately, then publishes metadata', async (t) => {
  const methods = [];
  setup(t, async (url, init) => {
    methods.push([url, init.method]);
    if (url.includes('/rpc/')) return Response.json(reportId);
    if (url.includes('/storage/')) { assert.match(url, /saanthvana-bills\/[^/]+\/receipt.pdf$/); return Response.json({ Key: 'private' }); }
    assert.deepEqual(JSON.parse(init.body), { published: true });
    return Response.json([saved]);
  });
  const response = await handleReportRequest(edgePost(form({}, new File(['%PDF-1.7 test'], 'patient-name.pdf', { type: 'application/pdf' }))));
  assert.equal(response.status, 201);
  assert.deepEqual(methods.map((item) => item[1]), ['POST', 'POST', 'PATCH']);
  assert.equal((await response.json()).report.receiptStatus, 'attached');
  assert.ok(methods.every((item) => !item[0].includes('patient-name')));
});

test('failed upload hides and removes the reservation and attempts private-file cleanup', async (t) => {
  const methods = [];
  setup(t, async (url, init) => {
    methods.push([url, init.method]);
    if (url.includes('/rpc/')) return Response.json(reportId);
    if (url.includes('/storage/') && init.method === 'POST') return Response.json({ error: 'down' }, { status: 503 });
    if (init.method === 'PATCH') assert.deepEqual(JSON.parse(init.body), { published: false });
    return Response.json({});
  });
  const response = await handleReportRequest(edgePost(form({}, new File(['%PDF-1.7 test'], 'bill.pdf', { type: 'application/pdf' }))));
  assert.equal(response.status, 503);
  assert.deepEqual(methods.map((item) => item[1]), ['POST', 'POST', 'PATCH', 'DELETE', 'DELETE']);
});

test('withdrawal authorizes against session ownership before changing anything', async (t) => {
  setup(t, async (url, init) => {
    assert.match(url, /session_hash=eq\.[a-f0-9]{64}/);
    assert.equal(init.method, undefined);
    return Response.json([]);
  });
  assert.equal((await handleReportRequest(new Request(`${endpoint}/reports?id=${reportId}`, { method: 'DELETE', headers }))).status, 404);
});

test('bundled Edge directory exactly matches the Karnataka application directory', () => {
  assert.deepEqual(REPORT_DIRECTORY, CARE_CENTRES.filter((centre) => centre.state === 'Karnataka').map((centre) => ({ id: centre.id, hospitalName: centre.name, district: centre.district, address: centre.address, latitude: centre.coordinates?.[0] ?? null, longitude: centre.coordinates?.[1] ?? null })));
});

test('existing directory metadata and dedupe key cannot be forged through submitted fields', async (t) => {
  const captured = [];
  setup(t, async (url, init) => {
    if (url.includes('/rpc/')) { captured.push(JSON.parse(init.body).p_report); return Response.json(reportId); }
    return Response.json([saved]);
  });
  for (const suffix of ['one', 'two']) {
    const response = await handleReportRequest(edgePost(form({ centreId: 'pallium-bengaluru', hospitalName: `Forged hospital ${suffix}`, address: `Forged address ${suffix}`, district: 'Delhi', latitude: '28.6', longitude: '77.2' })));
    assert.equal(response.status, 201);
  }
  const canonical = REPORT_DIRECTORY.find((centre) => centre.id === 'pallium-bengaluru');
  for (const row of captured) {
    assert.equal(row.hospital_name, canonical.hospitalName);
    assert.equal(row.address, canonical.address);
    assert.equal(row.district, canonical.district);
    assert.equal(row.latitude, canonical.latitude);
    assert.equal(row.longitude, canonical.longitude);
  }
  assert.equal(captured[0].centre_key, captured[1].centre_key);
});

test('unknown non-community centre IDs are rejected without touching the database', async (t) => {
  setup(t);
  assert.equal((await handleReportRequest(edgePost(form({ centreId: 'fabricated-pallium-id' })))).status, 400);
});

test('unlisted community IDs are rejected before mutation', async (t) => {
  setup(t, async (url, init) => {
    assert.match(url, /centre_id=eq.community-[a-f0-9]{24}/);
    assert.equal(init.method, undefined);
    return Response.json([]);
  });
  assert.equal((await handleReportRequest(edgePost(form({ centreId: `community-${'c'.repeat(24)}` })))).status, 400);
});

test('community-centre reports reuse original public hospital details', async (t) => {
  let captured;
  setup(t, async (url, init) => {
    if (url.includes('centre_id=eq.')) return Response.json([saved]);
    if (url.includes('/rpc/')) { captured = JSON.parse(init.body).p_report; return Response.json(reportId); }
    return Response.json([saved]);
  });
  assert.equal((await handleReportRequest(edgePost(form({ centreId: `community-${'c'.repeat(24)}`, hospitalName: 'Forged hospital', address: 'Forged address' })))).status, 201);
  assert.equal(captured.hospital_name, saved.hospital_name);
  assert.equal(captured.address, saved.address);
});
