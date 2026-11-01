import test from 'node:test';
import assert from 'node:assert/strict';
import { edInterpretation, emptyEctpr, emptyWithdrawal, validateEctpr } from '../app/ectpr-rules.ts';
import { quickViewSnapshot } from '../app/ed-card-snapshot.ts';
import { capacityGuide, homeCareLines, withdrawalFinalLabel, withdrawalLines } from '../app/ectpr-labels.ts';

const withdrawal = (patch = {}) => ({ ...emptyWithdrawal(), applicable: true, ...patch });
const complete = { primaryBoardDate: '2026-11-01', secondaryBoardDate: '2026-11-02', primaryBoardMembers: 'A, B, C', secondaryBoardMembers: 'D, E, F', boardDecision: 'Permitted', protocol: 'Stepwise', finalDecision: 'TO_BE_PERFORMED' };

test('going ahead with withdrawal or withholding needs the whole Board record', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.withdrawal = withdrawal({ finalDecision: 'TO_BE_PERFORMED' });
  assert.ok(validateEctpr(record).some((item) => item.id === 'W1' && item.severity === 'block' && item.stage === 'activate'));
  record.withdrawal = withdrawal(complete);
  assert.ok(!validateEctpr(record).some((item) => item.id === 'W1'));
});

test('a refusal needs no Board record, and an unticked section is ignored', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.withdrawal = withdrawal({ finalDecision: 'FAMILY_REFUSED' });
  assert.ok(!validateEctpr(record).some((item) => item.id === 'W1'));
  record.withdrawal = { ...emptyWithdrawal(), finalDecision: 'TO_BE_PERFORMED' };
  assert.ok(!validateEctpr(record).some((item) => item.id === 'W1'));
  assert.deepEqual(withdrawalLines(record, 'en'), []);
});

test('the emergency view is told when withdrawal is recorded', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.withdrawal = withdrawal(complete);
  assert.ok(edInterpretation(record).flags.some((flag) => flag.includes('medical board')));
});

test('secondary decision-maker is asked to sign once a surrogate has signed', () => {
  const record = emptyEctpr('p', {}, 'dr');
  record.decisionMakers.alternate = { name: 'Asha', relation: 'Partner', phone24h: '', idType: null, idLast4: null, authorityBasis: null };
  record.signatures = [{ role: 'SURROGATE', surrogateRank: 'PRIMARY', name: 'Ravi', signedAt: '2026-11-01T00:00:00Z' }];
  assert.ok(validateEctpr(record).some((item) => item.id === 'W2' && item.severity === 'warn'));
  record.signatures.push({ role: 'SURROGATE', surrogateRank: 'SECONDARY', name: 'Asha', signedAt: '2026-11-01T00:00:00Z' });
  assert.ok(!validateEctpr(record).some((item) => item.id === 'W2'));
});

test('the public QR view drops gender, family lists, Board members and the Board decision', () => {
  const record = emptyEctpr('p', { gender: 'Non-binary' }, 'dr');
  record.decisionMakers.presentAtDiscussion = ['Asha (live-in partner)'];
  record.decisionMakers.attemptedContact = ['Ravi (brother)'];
  record.withdrawal = withdrawal(complete);
  const shown = JSON.stringify(quickViewSnapshot(record));
  for (const hidden of ['Non-binary', 'Asha', 'Ravi', 'A, B, C', 'D, E, F', 'Permitted']) assert.ok(!shown.includes(hidden), hidden);
  assert.ok(withdrawalLines(quickViewSnapshot(record), 'en').some((line) => line.includes('Withdrawal or withholding to be performed')));
});

test('older records without the new fields still read', () => {
  const record = emptyEctpr('p', {}, 'dr');
  delete record.homeCare; delete record.withdrawal;
  assert.deepEqual(homeCareLines(record, 'en'), []);
  assert.deepEqual(withdrawalLines(record, 'en'), []);
  assert.ok(Array.isArray(validateEctpr(record)));
  assert.equal(withdrawalFinalLabel(null, 'en'), 'No final decision recorded');
});

test('capacity guidance is marked as a draft and checks all four abilities', () => {
  const guide = capacityGuide('en');
  assert.match(guide.note, /Draft/);
  assert.ok(guide.points.some((point) => /understand.*retain.*weigh.*communicate/.test(point)));
});
