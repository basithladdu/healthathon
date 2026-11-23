import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { EMERGENCY_PROJECT_URL, PLACE_MAX, cleanPlace, loadEmergencyCard } from '../app/emergency/emergency-link.ts';

const token = 'cd'.repeat(32);
const card = { kind: 'sahara-emergency-card', v: 1, cardNumber: 1, cardId: 'AB12CD34', madeAt: 1, name: 'Asha (fictional)' };
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const live = () => json({ status: 'live', card, updated_at: '2026-11-23T00:00:00Z', expires_at: '2027-11-23T00:00:00Z' });

test('the hospital line is one trimmed line of at most 80 characters', () => {
  assert.equal(PLACE_MAX, 80);
  assert.equal(cleanPlace('  City Hospital,\n\tEmergency  '), 'City Hospital, Emergency');
  assert.equal(cleanPlace('a\u0000b\u0007c'), 'a b c');
  assert.equal(cleanPlace('x'.repeat(200)).length, 80);
  assert.equal(cleanPlace(`${'y'.repeat(79)} z`), 'y'.repeat(79));
  assert.equal(cleanPlace('   '), '');
  assert.equal(cleanPlace(undefined), '');
  assert.equal(cleanPlace(42), '');
});

test('a typed hospital is sent with the token; a skipped one is not sent at all', async () => {
  const bodies = [];
  const fetcher = async (url, init) => { assert.equal(url, `${EMERGENCY_PROJECT_URL}/rest/v1/rpc/healthathon_emergency_view`); bodies.push(JSON.parse(init.body)); return live(); };
  assert.equal((await loadEmergencyCard(`#t=${token}`, fetcher, '  Apollo, Hyderabad ')).status, 'live');
  assert.deepEqual(bodies[0], { p_token: token, p_place: 'Apollo, Hyderabad' });
  assert.equal((await loadEmergencyCard(`#t=${token}`, fetcher, '')).status, 'live');
  assert.deepEqual(bodies[1], { p_token: token });
  assert.equal((await loadEmergencyCard(`#t=${token}`, fetcher)).status, 'live');
  assert.deepEqual(bodies[2], { p_token: token });
});

test('before the place migration is applied, the card still opens (retried without the place)', async () => {
  const bodies = [];
  const fetcher = async (_url, init) => { const body = JSON.parse(init.body); bodies.push(body); return 'p_place' in body ? json({ code: 'PGRST202' }, 404) : live(); };
  const result = await loadEmergencyCard(`#t=${token}`, fetcher, 'City Hospital');
  assert.equal(result.status, 'live');
  assert.equal(bodies.length, 2);
  assert.deepEqual(bodies[1], { p_token: token });
  // With no server function at all it is still "not set up", never a broken page.
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ code: 'PGRST202' }, 404), 'City Hospital')).status, 'not-set-up');
});

test('the migration keeps only time and place, under RLS, and returns the last 10 opens', () => {
  const sql = readFileSync(new URL('../../android/supabase/migrations/20261123120000_emergency_card_view_places.sql', import.meta.url), 'utf8');
  assert.match(sql, /create table healthathon_private\.emergency_link_views \(/);
  assert.match(sql, /enable row level security/);
  assert.match(sql, /revoke all on table healthathon_private\.emergency_link_views from public, anon, authenticated/);
  assert.match(sql, /char_length\(place\) between 1 and 80/);
  assert.match(sql, /healthathon_emergency_view\(p_token text, p_place text default null\)/);
  assert.match(sql, /limit 10/);
  assert.match(sql, /'recent_views'/);
  assert.match(sql, /m\.role='clinician'/);
  // Nothing but the time and the typed place: no user agent, IP address or other personal data.
  const table = sql.slice(sql.indexOf('create table healthathon_private.emergency_link_views'), sql.indexOf(');', sql.indexOf('create table healthathon_private.emergency_link_views')));
  assert.doesNotMatch(table, /\b(ua|ip|user_agent|name|phone)\b/);
});
