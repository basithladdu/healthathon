import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectCareBillText } from '../app/care-bill-match.ts';

test('matches hospital, oral formulation and date without declaring a bill verified', () => {
  const result = inspectCareBillText('Kidwai Memorial Hospital\n02/10/2026\nMorphine sulphate tablets 10 mg', 'Kidwai Memorial Hospital', '2026-10-02');
  assert.equal(result.status, 'matched');
  assert.match(result.message, /Authenticity still needs checking/);
});
test('an injection, a different hospital, a different date or an unavailable item cannot pass all bill checks', () => {
  for (const text of [
    'Kidwai Memorial 02/10/2026\nMorphine injection 10 mg',
    'Other Hospital 02/10/2026\nMorphine tablets',
    'Kidwai Memorial 01/10/2026\nMorphine tablets',
    'Kidwai Memorial 02/10/2026\nMorphine tablets not dispensed',
  ]) assert.equal(inspectCareBillText(text, 'Kidwai Memorial', '2026-10-02').status, 'needs-review');
  assert.equal(inspectCareBillText('', 'Kidwai Memorial', '2026-10-02').status, 'unreadable');
});
