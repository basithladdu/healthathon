import assert from 'node:assert/strict';
import test from 'node:test';
import { isValidBirthDate } from '../app/patient-date.ts';

const today = new Date(2026, 8, 13, 12);

test('birth dates must be real calendar dates, including leap-year rules', () => {
  for (const value of ['14/03/1964', '29/02/2000', '29/02/2024', '13/09/2026']) {
    assert.equal(isValidBirthDate(value, today), true, value);
  }
  for (const value of ['31/02/1964', '29/02/1900', '29/02/2025', '31/04/2020', '00/01/2020', '01/13/2020', '01/01/0000']) {
    assert.equal(isValidBirthDate(value, today), false, value);
  }
});

test('birth dates reject future dates and malformed input', () => {
  for (const value of ['14/09/2026', '01/01/2099', '', '1964-03-14', '1/1/2000', 'not a date']) {
    assert.equal(isValidBirthDate(value, today), false, value);
  }
});
