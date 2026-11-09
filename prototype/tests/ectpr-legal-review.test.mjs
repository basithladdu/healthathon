import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { edInterpretation, emptyEctpr, validateEctpr } from '../app/ectpr-rules.ts';
import { quickViewSnapshot } from '../app/ed-card-snapshot.ts';
import { capacityGuide, homeCareLines } from '../app/ectpr-labels.ts';

// The withdrawal or withholding section was removed from the plan (lead doctor, Nov 2026).
// Older saved plans may still carry it: they must load, and the section is ignored everywhere.
const legacyWithdrawal = { applicable: true, primaryBoardDate: '2026-11-01', secondaryBoardDate: '2026-11-02', primaryBoardMembers: 'A, B, C', secondaryBoardMembers: 'D, E, F', boardDecision: 'Permitted', protocol: 'Stepwise', finalDecision: 'TO_BE_PERFORMED' };

test('an older plan with the removed withdrawal section still loads, and the section is ignored', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.withdrawal = { ...legacyWithdrawal, finalDecision: 'TO_BE_PERFORMED', primaryBoardDate: '' };
  const issues = validateEctpr(record);
  assert.ok(Array.isArray(issues));
  assert.ok(!issues.some((item) => item.id === 'W1' || item.field === 'withdrawal'));
  assert.ok(!edInterpretation(record).flags.some((flag) => /withdrawal or withholding/i.test(flag)));
});

test('the plan has no stopping or not starting treatment step, and the card and printout leave it out', () => {
  const screen = readFileSync(new URL('../app/ectpr-plan-screen.tsx', import.meta.url), 'utf8');
  assert.ok(!screen.includes('Stopping or not starting treatment'));
  assert.ok(!screen.includes('WithdrawalFields'));
  for (const file of ['ectpr-plan-screen.tsx', 'ectpr-ed-view.tsx', 'ectpr-pdf.ts']) {
    assert.ok(!readFileSync(new URL(`../app/${file}`, import.meta.url), 'utf8').includes('Withdrawal or withholding (medical boards)'), file);
  }
});

test('secondary decision-maker is asked to sign once a surrogate has signed', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.decisionMakers.alternate = { name: 'Asha', relation: 'Partner', phone24h: '', idType: null, idLast4: null, authorityBasis: null };
  record.signatures = [{ role: 'SURROGATE', surrogateRank: 'PRIMARY', name: 'Ravi', signedAt: '2026-11-01T00:00:00Z' }];
  assert.ok(validateEctpr(record).some((item) => item.id === 'W2' && item.severity === 'warn'));
  record.signatures.push({ role: 'SURROGATE', surrogateRank: 'SECONDARY', name: 'Asha', signedAt: '2026-11-01T00:00:00Z' });
  assert.ok(!validateEctpr(record).some((item) => item.id === 'W2'));
});

test('the public QR view drops gender, family lists and any old withdrawal section', () => {
  const record = emptyEctpr('p', { gender: 'Non-binary' }, 'dr');
  record.decisionMakers.presentAtDiscussion = ['Asha (live-in partner)'];
  record.decisionMakers.attemptedContact = ['Ravi (brother)'];
  record.withdrawal = legacyWithdrawal;
  const snapshot = quickViewSnapshot(record);
  const shown = JSON.stringify(snapshot);
  for (const hidden of ['Non-binary', 'Asha', 'Ravi', 'A, B, C', 'D, E, F', 'Permitted', 'Stepwise']) assert.ok(!shown.includes(hidden), hidden);
  assert.equal('withdrawal' in snapshot, false);
});

test('older records without the new fields still read', () => {
  const record = emptyEctpr('p', {}, 'dr');
  delete record.homeCare; delete record.withdrawal;
  assert.deepEqual(homeCareLines(record, 'en'), []);
  assert.ok(Array.isArray(validateEctpr(record)));
});

test('capacity guidance is marked as a draft and checks all four abilities', () => {
  const guide = capacityGuide('en');
  assert.match(guide.note, /Draft/);
  assert.ok(guide.points.some((point) => /understand.*retain.*weigh.*communicate/.test(point)));
});
