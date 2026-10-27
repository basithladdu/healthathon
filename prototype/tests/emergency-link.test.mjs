import test from 'node:test';
import assert from 'node:assert/strict';
import { EMERGENCY_PROJECT_URL, MESSAGES, loadEmergencyCard, tokenFromHash } from '../app/emergency/emergency-link.ts';

const token = 'ab'.repeat(32);
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const card = { kind: 'sahara-emergency-card', v: 1, cardNumber: 2, cardId: 'AB12CD34', madeAt: 1, name: 'Asha (fictional)' };

test('the token is read from the fragment only and must be exact', () => {
  assert.equal(tokenFromHash(`#t=${token}`), token);
  assert.equal(tokenFromHash('#t=ABC'), null);
  assert.equal(tokenFromHash(`#t=${token}&t=${token}`), null);
  assert.equal(tokenFromHash(''), null);
});

test('a live link calls the one public function with the token and shows the card', async () => {
  let seen;
  const result = await loadEmergencyCard(`#t=${token}`, async (url, init) => { seen = { url, init }; return json({ status: 'live', card, updated_at: '2026-10-27T00:00:00Z', expires_at: '2027-10-27T00:00:00Z' }); });
  assert.equal(seen.url, `${EMERGENCY_PROJECT_URL}/rest/v1/rpc/healthathon_emergency_view`);
  assert.deepEqual(JSON.parse(seen.init.body), { p_token: token });
  assert.equal(result.status, 'live');
  assert.equal(result.card.name, 'Asha (fictional)');
});

test('every non-live answer is explained, including before the owner applies the migration', async () => {
  assert.equal((await loadEmergencyCard('', async () => json({}))).status, 'missing');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ code: 'PGRST202' }, 404))).status, 'not-set-up');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ status: 'off' }))).status, 'off');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ status: 'expired' }))).status, 'expired');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ status: 'unknown' }))).status, 'unknown');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => { throw new TypeError('offline'); })).status, 'unreachable');
  assert.equal((await loadEmergencyCard(`#t=${token}`, async () => json({ status: 'live', card: { kind: 'other' } }))).status, 'unknown');
  for (const key of ['missing', 'unknown', 'off', 'expired', 'not-set-up', 'unreachable']) assert.ok(MESSAGES[key].title && MESSAGES[key].detail.includes('·'));
});
