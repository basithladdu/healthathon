import test from 'node:test';
import assert from 'node:assert/strict';
import {
  CARE_CENTRES,
  KARNATAKA_CENTRES,
  defaultCareCentreFilter,
  filterCareCentres,
  centreDirectionsUrl,
} from '../app/care-directory-data.ts';

test('location, state and service filters intersect, including the Bangalore alias', () => {
  const found = filterCareCentres('  BANGALORE  ', 'Karnataka', 'Home care');
  assert.equal(found.length, 11);
  assert.ok(found.every((centre) => centre.city === 'Bengaluru' && centre.services.includes('Home care')));
  assert.deepEqual(filterCareCentres('Hyderabad', 'Karnataka', ''), []);
  assert.deepEqual(filterCareCentres('Sparsh Hyderabad', 'Telangana', 'Inpatient care').map((centre) => centre.id), ['sparsh-hospice']);
});

test('unspecified services never imply availability', () => {
  assert.equal(filterCareCentres('Advanced Research', '', '').length, 1);
  assert.equal(filterCareCentres('Advanced Research', '', 'Home care').length, 0);
  assert.equal(filterCareCentres('', '', '').length, CARE_CENTRES.length);
});

test('morphine filters use explicit source status and tolerate source wording', () => {
  assert.equal(filterCareCentres('', 'Karnataka', '', 'available').length, 23);
  assert.equal(filterCareCentres('', 'Karnataka', '', 'unavailable').length, 10);
  assert.equal(filterCareCentres('', 'Karnataka', '', 'Not Available').length, 10);
  assert.deepEqual(filterCareCentres('Pallium', 'Karnataka', '', 'available'), []);
});

test('Karnataka coverage includes every current district row without invented coordinates', () => {
  assert.equal(KARNATAKA_CENTRES.length, 33);
  assert.equal(new Set(KARNATAKA_CENTRES.map((centre) => centre.district)).size, 11);
  assert.ok(CARE_CENTRES.every((centre) => centre.state.trim()));
  assert.ok(CARE_CENTRES.every((centre) => centre.sourceURL === centre.directoryUrl));
  assert.ok(KARNATAKA_CENTRES.every((centre) => centre.state === 'Karnataka'));
  assert.ok(KARNATAKA_CENTRES.every((centre) => centre.district?.trim()));
  assert.ok(KARNATAKA_CENTRES.every((centre) => !centre.coordinates));
  assert.ok(KARNATAKA_CENTRES.every((centre) => centre.sourceURL === 'https://palliumindia.org/clinics/karnataka'));
  assert.ok(KARNATAKA_CENTRES.every((centre) => centre.sourceAsOf === '2026-10-02'));
  assert.ok(KARNATAKA_CENTRES.every((centre) => ['available', 'unavailable', 'unspecified'].includes(centre.morphine)));
  assert.ok(KARNATAKA_CENTRES.every((centre) => centre.phone.trim() && centre.services && Array.isArray(centre.services)));
  assert.deepEqual(KARNATAKA_CENTRES.slice(0, 4).map((centre) => centre.id), [
    'pallium-bengaluru', 'aster-cmi', 'baptist-bengaluru', 'karunashraya',
  ]);
  assert.equal(defaultCareCentreFilter().length, KARNATAKA_CENTRES.length);
});

test('directions use the listed provider address without inventing coordinates or sending patient data', () => {
  const centre = CARE_CENTRES.find((item) => item.id === 'sparsh-hospice');
  const url = new URL(centreDirectionsUrl(centre));
  assert.equal(url.origin, 'https://www.google.com');
  assert.equal(url.searchParams.get('destination'), `${centre.name}, ${centre.address}, India`);
  assert.equal(url.searchParams.has('origin'), false);
  assert.equal(new Set(CARE_CENTRES.map((item) => item.id)).size, CARE_CENTRES.length);
  assert.ok(CARE_CENTRES.every((item) => item.phone.trim().length > 0));
});
