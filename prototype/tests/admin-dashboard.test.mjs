import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { checkReview, orderRequests, DOCTOR_TRANSITIONS, APPROVE_NOTE, NMC_REGISTER_URL } from '../app/admin/doctor-rules.ts';
import { DIRECTORY_FACTS, SUPPORTED_LANGUAGES } from '../app/admin/impact-facts.ts';
import { POST as listDoctors } from '../app/api/admin-doctors/route.ts';
import { POST as reviewDoctor } from '../app/api/admin-doctors/review/route.ts';
import { POST as impact } from '../app/api/admin-impact/route.ts';

const USER = '00000000-0000-4000-8000-000000000001';
const USER2 = '00000000-0000-4000-8000-000000000002';
const KEY = 'sb_secret_synthetic_test_only';
const req = (url, body) => new Request(`https://sahara.test${url}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const ok = (body, headers = {}) => new Response(body === null ? null : JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json', ...headers } });
const fail = (status, code, message) => new Response(JSON.stringify({ code, message }), { status, headers: { 'Content-Type': 'application/json' } });

function env(t, values, fetcher) {
  const names = ['ADMIN_PASSWORD', 'ADMIN_SURVEY_PASSWORD', 'SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY', 'SUPABASE_SERVICE_ROLE_KEY'];
  const before = Object.fromEntries(names.map(n => [n, process.env[n]])); const f = globalThis.fetch;
  names.forEach(n => { if (n in values) process.env[n] = values[n]; else delete process.env[n]; });
  const calls = []; globalThis.fetch = async (url, init = {}) => { calls.push({ url: String(url), init }); if (!fetcher) throw Error('Unexpected network'); return fetcher(String(url), init); };
  t.after(() => { globalThis.fetch = f; names.forEach(n => { if (before[n] === undefined) delete process.env[n]; else process.env[n] = before[n]; }); });
  return calls;
}
const review = { password: 'pw', user_id: USER, revision: 3, from: 'pending', decision: 'rejected', evidence: 'NMC register: number not found', note: 'Registration number not found on the register.', reviewer: 'Team tester' };

test('bundled directory and language facts match the repo', () => {
  const centres = JSON.parse(readFileSync(new URL('../../android/app/src/main/assets/india.json', import.meta.url), 'utf8'));
  assert.equal(centres.length, DIRECTORY_FACTS.centres);
  assert.equal(new Set(centres.map(c => c.state)).size, DIRECTORY_FACTS.states);
  assert.equal(centres.filter(c => /Morphine:\s*(available|yes)/i.test(c.listedServices || '')).length, DIRECTORY_FACTS.morphineListed);
  const i18n = readFileSync(new URL('../app/care-i18n.ts', import.meta.url), 'utf8');
  const langs = [...i18n.matchAll(/english: '([^']+)'/g)].map(m => m[1]);
  assert.deepEqual(langs, [...SUPPORTED_LANGUAGES]);
});

test('review input follows the database limits; reject and suspend need a reason', () => {
  assert.ok('error' in checkReview({ ...review, user_id: 'nope' }));
  assert.ok('error' in checkReview({ ...review, revision: 0 }));
  assert.ok('error' in checkReview({ ...review, decision: 'deleted' }));
  assert.ok('error' in checkReview({ ...review, reviewer: ' ' }));
  assert.ok('error' in checkReview({ ...review, evidence: 'short' }));
  assert.ok('error' in checkReview({ ...review, note: '' }));
  assert.ok('error' in checkReview({ ...review, decision: 'suspended', note: 'no' }));
  assert.equal(checkReview({ ...review, decision: 'approved', note: '' }).value.note, APPROVE_NOTE);
  assert.deepEqual(DOCTOR_TRANSITIONS.pending, ['approved', 'rejected']);
  assert.deepEqual(DOCTOR_TRANSITIONS.approved, ['suspended']);
  assert.match(NMC_REGISTER_URL, /^https:\/\/www\.nmc\.org\.in\//);
});

test('waiting sign-ups come first, oldest first; the rest newest first', () => {
  const rows = [{ id: 'a', status: 'approved', submitted_at: '2026-11-01T00:00:00Z' }, { id: 'b', status: 'pending', submitted_at: '2026-11-05T00:00:00Z' }, { id: 'c', status: 'rejected', submitted_at: '2026-11-09T00:00:00Z' }, { id: 'd', status: 'pending', submitted_at: '2026-11-02T00:00:00Z' }];
  assert.deepEqual(orderRequests(rows).map(r => r.id), ['d', 'b', 'c', 'a']);
});

test('admin routes say when no admin password is set up', async t => {
  env(t, {});
  assert.equal((await listDoctors(req('/api/admin-doctors', { password: 'x' }))).status, 503);
});

test('admin routes need the password before touching Supabase', async t => {
  env(t, { ADMIN_PASSWORD: 'pw', SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY: KEY });
  for (const call of [listDoctors(req('/api/admin-doctors', { password: 'wrong' })), reviewDoctor(req('/api/admin-doctors/review', { ...review, password: 'wrong' })), impact(req('/api/admin-impact', {}))]) assert.equal((await call).status, 401);
});

test('a missing Android service key is named, never shown', async t => {
  env(t, { ADMIN_PASSWORD: 'pw' });
  const response = await listDoctors(req('/api/admin-doctors', { password: 'pw' }));
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY/);
});

test('doctor list reads the Android project and adds account emails', async t => {
  const calls = env(t, { ADMIN_PASSWORD: 'pw', SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY: KEY }, url => {
    if (url.includes('/rest/v1/healthathon_clinician_requests')) return ok([
      { user_id: USER, name: 'Dr A', council: 'NMC', registration: '12345', status: 'approved', submitted_at: '2026-11-10T00:00:00Z', reviewed_at: null, review_note: '', revision: 2 },
      { user_id: USER2, name: 'Dr B', council: 'Kerala', registration: 'KL-9', status: 'pending', submitted_at: '2026-11-12T00:00:00Z', reviewed_at: null, review_note: '', revision: 1 }]);
    if (url.endsWith(`/auth/v1/admin/users/${USER2}`)) return ok({ id: USER2, email: 'b@example.test' });
    return fail(404, 'user_not_found', 'User not found');
  });
  const response = await listDoctors(req('/api/admin-doctors', { password: 'pw' }));
  assert.equal(response.status, 200);
  const { rows } = await response.json();
  assert.deepEqual(rows.map(r => [r.name, r.email]), [['Dr B', 'b@example.test'], ['Dr A', null]]);
  assert.ok(calls.every(c => c.url.startsWith('https://kjnhtakqzboayqorrlue.supabase.co/') && c.init.headers.apikey === KEY && !c.init.headers.Authorization));
});

test('review calls the admin RPC with the expected revision and maps errors', async t => {
  let reply = () => ok({ user_id: USER, status: 'rejected', revision: 4, review_note: review.note });
  const calls = env(t, { ADMIN_PASSWORD: 'pw', SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY: KEY }, () => reply());
  const saved = await reviewDoctor(req('/api/admin-doctors/review', review));
  assert.equal(saved.status, 200);
  assert.equal(calls[0].url, 'https://kjnhtakqzboayqorrlue.supabase.co/rest/v1/rpc/sahara_admin_review_clinician');
  assert.deepEqual(JSON.parse(calls[0].init.body), { p_user: USER, p_revision: 3, p_decision: 'rejected', p_evidence: review.evidence, p_note: review.note, p_reviewer: 'Team tester' });
  reply = () => fail(404, 'PGRST202', 'Could not find the function');
  const missing = await reviewDoctor(req('/api/admin-doctors/review', review));
  assert.equal(missing.status, 503); assert.match((await missing.json()).error, /20261120120000_admin_clinician_review\.sql/);
  reply = () => fail(500, '40001', 'This request changed. Refresh before reviewing.');
  assert.equal((await reviewDoctor(req('/api/admin-doctors/review', review))).status, 409);
  const before = calls.length;
  assert.equal((await reviewDoctor(req('/api/admin-doctors/review', { ...review, from: 'rejected', decision: 'approved', note: '' }))).status, 400);
  assert.equal((await reviewDoctor(req('/api/admin-doctors/review', { ...review, note: '' }))).status, 400);
  assert.equal(calls.length, before, 'invalid reviews never reach Supabase');
});

test('impact counts each table and marks missing ones not available', async t => {
  env(t, { ADMIN_PASSWORD: 'pw', SAHARA_ANDROID_SUPABASE_SERVICE_ROLE_KEY: KEY }, (url, init) => {
    if (url.includes('/auth/v1/admin/users')) return ok({ users: [{ created_at: new Date().toISOString() }, { created_at: '2020-01-01T00:00:00Z' }] });
    if (url.includes('/rpc/sahara_admin_private_counts')) return fail(404, 'PGRST202', 'Could not find the function');
    if (url.includes('/community_posts')) return init.method === 'HEAD' ? new Response(null, { status: 404 }) : fail(404, 'PGRST205', 'Could not find the table');
    const recent = url.includes('=gte.');
    return new Response(null, { status: 200, headers: { 'Content-Range': recent ? '*/2' : '0-0/7' } });
  });
  const response = await impact(req('/api/admin-impact', { password: 'pw' }));
  assert.equal(response.status, 200);
  const { tiles } = await response.json();
  const by = Object.fromEntries(tiles.map(x => [x.key, x]));
  assert.deepEqual([by.accounts.total, by.accounts.last30], [2, 1]);
  assert.deepEqual([by.doses.total, by.doses.last30], [7, 2]);
  assert.equal(by.doctors_approved.last30, null);
  assert.equal(by.posts.total, null); assert.match(by.posts.unavailable, /not on the live project/);
  assert.equal(by.emergency.total, null); assert.match(by.emergency.unavailable, /migration/);
  assert.equal(by.survey.total, null); assert.match(by.survey.unavailable, /website project key not set/);
});
