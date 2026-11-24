import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import {
  EMERGENCY_PROJECT_URL, NOT_LATEST, NOT_SAVED, OVER_THREE_MONTHS, contactRows, isOlderVersion, isOverThreeMonths, loadEmergencyCard, medicineRows,
} from '../app/emergency/emergency-link.ts';

// Clinical team meeting, 24 Nov 2026: versions, the 3-month warning, tables, and no scanner location.
const token = 'ef'.repeat(32);
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const card = {
  kind: 'sahara-emergency-card', v: 2, cardNumber: 3, cardId: 'AB12CD34', madeAt: Date.parse('2026-11-24T00:00:00Z'), validUntil: Date.parse('2027-02-22T00:00:00Z'),
  sections: ['NAME', 'CONTACTS', 'MEDICINES'], name: 'Asha (fictional)',
  contacts: [{ name: 'Ravi', relationship: 'Son', phone: '+91 90000 00002' }, { name: 'Dr Sujay', relationship: 'Palliative doctor', phone: '' }],
  nextOfKin: { name: 'Ravi', relationship: 'Son', phone: '+91 90000 00002' },
  doctor: { name: 'Dr Shirin', phone: '+91 90000 00007', place: 'Sahara Clinic', landline: '+91 40 1111 2222' },
  otherContacts: [{ name: 'Dr Sujay', relationship: 'Palliative doctor', phone: '' }],
  medicines: [{ name: 'Morphine', strength: '5 mg', when: 'Every 4 hours', detail: 'Strength: 5 mg\nHow often: Every 4 hours' }],
};
const live = (extra = {}) => json({ status: 'live', card, updated_at: '2026-11-24T10:00:00Z', expires_at: '2027-11-24T10:00:00Z', version: 2, published_at: '2026-11-24T10:00:00Z', scanned_version: 2, latest_version: 3, latest_published_at: '2026-11-25T10:00:00Z', ...extra });

test('an older version is flagged and the latest can be opened through the same token', async () => {
  const bodies = [];
  const fetcher = async (url, init) => { assert.equal(url, `${EMERGENCY_PROJECT_URL}/rest/v1/rpc/healthathon_emergency_view`); bodies.push(JSON.parse(init.body)); return live(); };
  const scanned = await loadEmergencyCard(`#t=${token}`, fetcher, 'City Hospital');
  assert.equal(scanned.status, 'live');
  assert.equal(scanned.scannedVersion, 2); assert.equal(scanned.latestVersion, 3);
  assert.equal(isOlderVersion(scanned), true);
  assert.deepEqual(bodies[0], { p_token: token, p_place: 'City Hospital' });
  await loadEmergencyCard(`#t=${token}`, fetcher, 'City Hospital', true);
  // The "latest" call never re-sends the place (it is not counted as another open).
  assert.deepEqual(bodies[1], { p_token: token, p_latest: true });
  assert.equal(NOT_LATEST.title, 'This is not the latest version of this card');
});

test('servers from before the versions migration still open (version 1, not flagged)', async () => {
  const result = await loadEmergencyCard(`#t=${token}`, async () => json({ status: 'live', card: { ...card, v: 1 }, updated_at: '2026-11-01T00:00:00Z', expires_at: '2027-11-01T00:00:00Z' }));
  assert.equal(result.status, 'live');
  assert.equal(result.version, 1); assert.equal(result.latestVersion, 1); assert.equal(result.publishedAt, '2026-11-01T00:00:00Z');
  assert.equal(isOlderVersion(result), false);
});

test('a card more than 3 months old says so', async () => {
  const result = await loadEmergencyCard(`#t=${token}`, async () => live({ scanned_version: 3 }));
  assert.equal(isOverThreeMonths(result, Date.parse('2027-01-01T00:00:00Z')), false);
  assert.equal(isOverThreeMonths(result, Date.parse('2027-02-23T00:00:00Z')), true);
  // Without validUntil (older cards) the 90 days run from when the card was published.
  const old = { ...result, card: { ...card, validUntil: undefined }, publishedAt: '2026-08-01T00:00:00Z' };
  assert.equal(isOverThreeMonths(old, Date.parse('2026-10-29T00:00:00Z')), false);
  assert.equal(isOverThreeMonths(old, Date.parse('2026-10-31T00:00:00Z')), true);
  assert.equal(OVER_THREE_MONTHS.title, 'This card is more than 3 months old — check with the family/doctor');
});

test('contacts and medicines become the same table rows as the printed card', () => {
  assert.deepEqual(contactRows(card), [
    { who: 'Next of kin', name: 'Ravi (Son)', phone: '+91 90000 00002' },
    { who: 'Treating doctor', name: 'Dr Shirin', phone: '+91 90000 00007' },
    { who: 'Hospital / clinic', name: 'Sahara Clinic', phone: '+91 40 1111 2222' },
    { who: 'Palliative doctor', name: 'Dr Sujay', phone: NOT_SAVED },
  ]);
  assert.deepEqual(medicineRows(card), [{ name: 'Morphine', strength: '5 mg', when: 'Every 4 hours' }]);
  // A v1 card (before 24 Nov): first contact is the next of kin; strength and when are read from the saved text.
  const v1 = { kind: 'sahara-emergency-card', v: 1, cardNumber: 1, cardId: 'X', madeAt: 1,
    contacts: [{ name: 'Meera', relationship: 'Sister', phone: '+91 1' }, { name: 'Priya', relationship: 'Nurse', phone: '' }],
    medicines: [{ name: 'Morphine sulfate 10 mg', detail: 'Oral, every 4 hours as needed. For breakthrough pain.' }, { name: 'Senna', detail: 'Strength: 8.6 mg\nHow often: At night\nFood: after food' }] };
  assert.deepEqual(contactRows(v1), [{ who: 'Next of kin', name: 'Meera (Sister)', phone: '+91 1' }, { who: 'Nurse', name: 'Priya', phone: NOT_SAVED }]);
  assert.deepEqual(medicineRows(v1), [
    { name: 'Morphine sulfate 10 mg', strength: '10 mg', when: 'Oral, every 4 hours as needed' },
    { name: 'Senna', strength: '8.6 mg', when: 'At night, after food' },
  ]);
});

test('the emergency page never asks for the browser location and reads the card only from the server', () => {
  const dir = new URL('../app/emergency/', import.meta.url);
  for (const file of readdirSync(dir)) {
    const source = readFileSync(new URL(file, dir), 'utf8');
    assert.doesNotMatch(source, /navigator\.geolocation|getCurrentPosition|watchPosition/, file);
  }
  const link = readFileSync(new URL('emergency-link.ts', dir), 'utf8');
  // The only thing taken from the URL is the 64-hex token in the #fragment.
  assert.match(link, /\^\[a-f0-9\]\{64\}\$/);
  assert.doesNotMatch(link, /atob\(|decodeURIComponent\(|JSON\.parse\(.*hash/);
});

test('the versions migration keeps old tokens working and gives each new card its own token', () => {
  const sql = readFileSync(new URL('../../android/supabase/migrations/20261124120000_emergency_card_versions.sql', import.meta.url), 'utf8');
  assert.match(sql, /create table healthathon_private\.emergency_link_versions/);
  assert.match(sql, /enable row level security/);
  assert.match(sql, /revoke all on table healthathon_private\.emergency_link_versions from public, anon, authenticated/);
  // Backfill: every existing link is version 1 with its existing token hash.
  assert.match(sql, /select l\.id, 1, l\.token_hash, l\.card, l\.updated_at from healthathon_private\.emergency_links l/);
  assert.match(sql, /emergency_link_publish\(p_person uuid,p_card jsonb\)/);
  assert.match(sql, /gen_random_bytes\(32\)/);
  assert.match(sql, /healthathon_emergency_view\(p_token text, p_place text default null, p_latest boolean default false\)/);
  assert.match(sql, /drop function if exists public\.healthathon_emergency_view\(text, text\)/);
  assert.match(sql, /'scanned_version',scanned_version,'latest_version',latest_version/);
  assert.doesNotMatch(sql, /\b(latitude|longitude|geolocation|user_agent|ip_address)\b/);
});
