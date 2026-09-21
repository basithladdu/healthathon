import test from 'node:test';
import assert from 'node:assert/strict';
import { CARE_CENTRES, filterCareCentres, centreDirectionsUrl } from '../app/care-directory-data.ts';

test('location, state and service filters intersect, including the Bangalore alias', () => {
  const found = filterCareCentres('  BANGALORE  ', 'Karnataka', 'Home care');
  assert.equal(found.length, 4);
  assert.ok(found.every((centre) => centre.city === 'Bengaluru' && centre.services.includes('Home care')));
  assert.deepEqual(filterCareCentres('Hyderabad', 'Karnataka', ''), []);
  assert.deepEqual(filterCareCentres('Sparsh Hyderabad', 'Telangana', 'Inpatient care').map((centre) => centre.id), ['sparsh-hospice']);
});

test('unspecified services never imply availability', () => {
  assert.equal(filterCareCentres('Advanced Research', '', '').length, 1);
  assert.equal(filterCareCentres('Advanced Research', '', 'Home care').length, 0);
  assert.equal(filterCareCentres('', '', '').length, CARE_CENTRES.length);
});

test('directions use the listed provider address without inventing coordinates or sending patient data', () => {
  const centre = CARE_CENTRES.find((item) => item.id === 'sparsh-hospice');
  const url = new URL(centreDirectionsUrl(centre));
  assert.equal(url.origin, 'https://www.google.com');
  assert.equal(url.searchParams.get('destination'), `${centre.name}, ${centre.address}, India`);
  assert.equal(url.searchParams.has('origin'), false);
  assert.equal(new Set(CARE_CENTRES.map((item) => item.id)).size, CARE_CENTRES.length);
  assert.ok(CARE_CENTRES.every((item) => /^(\+91\d{10}|1800\d{7})$/.test(item.phone)));
});
