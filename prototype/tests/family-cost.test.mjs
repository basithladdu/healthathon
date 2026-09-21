import test from 'node:test';
import assert from 'node:assert/strict';
import { addFamilyExpense, addFamilyFundraiser, addFamilyPaperwork, createEmptyFamilyCostState, editFamilyExpense, editFamilyFundraiser, editFamilyPaperwork, exportFamilyCostState, exportFamilyFundraiserLink, familyExpenseTotals, parseFamilyExpenseRupees, removeFamilyCostItem, restoreFamilyCostItem, safeFamilyFundraiserUrl, setFamilyExpensePaid, setFamilyPaperworkDone } from '../app/family-cost-state.ts';

const expense = { title: 'Travel receipt', category: 'Travel', date: '2026-09-21', amountRupees: .1, paid: false };
const fundraiser = { title: 'Family fundraiser', url: 'https://example.org/fund/family', note: 'Our existing link' };

test('expense totals use recorded amounts and paid flags with exact paise, scoped to the patient', () => {
  let state = addFamilyExpense(createEmptyFamilyCostState(), 'a', 'Kavya', 'one', expense);
  state = addFamilyExpense(state, 'a', 'Kavya', 'two', { ...expense, amountRupees: .2, paid: true });
  state = addFamilyExpense(state, 'b', 'Ravi', 'other', { ...expense, amountRupees: 900 });
  assert.deepEqual(familyExpenseTotals(state, 'a'), { totalRupees: .3, paidRupees: .2, unpaidRupees: .1 });
  const paid = setFamilyExpensePaid(state, 'a', 'one', 'Ravi', true);
  assert.deepEqual(familyExpenseTotals(paid, 'a'), { totalRupees: .3, paidRupees: .3, unpaidRupees: 0 });
  assert.equal(state.expenses[0].paid, false);
  assert.equal(setFamilyExpensePaid(state, 'b', 'one', 'Ravi', true), state);
});

test('amount entry rejects negative, over-precision and nonnumeric values while dates remain valid', () => {
  assert.equal(parseFamilyExpenseRupees(' 123.45 '), 123.45);
  assert.equal(parseFamilyExpenseRupees('.50'), .5);
  for (const input of ['0', '-1', '2.345', '1e5', 'NaN', 'Infinity', '']) assert.equal(parseFamilyExpenseRupees(input), null);
  const state = createEmptyFamilyCostState();
  assert.equal(addFamilyExpense(state, 'a', 'Kavya', 'one', { ...expense, date: '2026-02-30' }), state);
  assert.equal(addFamilyExpense(state, 'a', 'Kavya', 'one', { ...expense, amountRupees: 1.001 }), state);
});

test('fundraiser URLs require HTTPS without credentials, scripts, data or embedded controls', () => {
  assert.equal(safeFamilyFundraiserUrl(' https://example.org/a?ref=family '), 'https://example.org/a?ref=family');
  for (const url of ['javascript:alert(1)', 'data:text/html,hi', 'http://example.org/', 'https://user:password@example.org/', 'https://user@example.org/', 'https://exa\nmple.org/', 'https://', '/local', '//example.org']) assert.equal(safeFamilyFundraiserUrl(url), null);
  const state = createEmptyFamilyCostState();
  assert.equal(addFamilyFundraiser(state, 'a', 'Kavya', 'link', { ...fundraiser, url: 'javascript:alert(1)' }), state);
});

test('editing and paperwork completion preserve ownership and cannot reach another patient', () => {
  let state = addFamilyExpense(createEmptyFamilyCostState(), 'a', 'Kavya', 'expense', expense);
  state = addFamilyFundraiser(state, 'a', 'Kavya', 'link', fundraiser);
  state = addFamilyPaperwork(state, 'a', 'Kavya', 'task', 'Find the existing bill');
  assert.equal(editFamilyExpense(state, 'b', 'expense', 'Ravi', { ...expense, amountRupees: 12 }), state);
  assert.equal(editFamilyFundraiser(state, 'b', 'link', 'Ravi', fundraiser), state);
  assert.equal(editFamilyPaperwork(state, 'b', 'task', 'Ravi', 'Changed'), state);
  const next = setFamilyPaperworkDone(state, 'a', 'task', 'Ravi', true);
  assert.equal(next.paperwork[0].done, true);
  assert.equal(next.paperwork[0].createdBy, 'Kavya');
  assert.equal(next.paperwork[0].updatedBy, 'Ravi');
  assert.equal(state.paperwork[0].done, false);
  assert.equal(setFamilyPaperworkDone(next, 'a', 'task', 'Ravi', false).paperwork[0].done, false);
});

test('remove/undo recovers full items without duplicates and downloads contain only the selected patient', () => {
  let state = addFamilyExpense(createEmptyFamilyCostState(), 'a', 'Kavya', 'expense', expense);
  state = addFamilyFundraiser(state, 'a', 'Kavya', 'link', fundraiser);
  state = addFamilyPaperwork(state, 'a', 'Kavya', 'task', 'Find the existing bill');
  state = addFamilyFundraiser(state, 'b', 'Ravi', 'private-link', { ...fundraiser, title: 'Other patient private fundraiser' });
  for (const removed of [{ kind: 'expense', item: state.expenses[0] }, { kind: 'fundraiser', item: state.fundraisers[0] }, { kind: 'paperwork', item: state.paperwork[0] }]) {
    const next = removeFamilyCostItem(state, 'a', removed);
    assert.equal(restoreFamilyCostItem(next, 'b', removed), next);
    const restored = restoreFamilyCostItem(next, 'a', removed);
    assert.equal(restoreFamilyCostItem(restored, 'a', removed), restored);
    assert.ok(exportFamilyCostState(restored, 'a').includes(removed.item.title));
  }
  assert.equal(exportFamilyFundraiserLink(state, 'b', 'link'), null);
  assert.equal(exportFamilyFundraiserLink(state, 'a', 'link'), 'Family fundraiser\n\nhttps://example.org/fund/family\n\nOur existing link');
  assert.ok(!exportFamilyCostState(state, 'a').includes('Other patient private fundraiser'));
  assert.deepEqual(createEmptyFamilyCostState(), { expenses: [], fundraisers: [], paperwork: [] });
});
