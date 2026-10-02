import test from 'node:test';
import assert from 'node:assert/strict';
import { aggregateCentreReports, validateReportInput } from '../app/care-centre-report-state.ts';

const baseExisting = {
  centreId: 'aster-cmi',
  centreName: 'Aster CMI Hospital',
  district: 'Bengaluru Urban',
  address: 'New Airport Road, Bengaluru',
  reporterRole: 'patient',
  doctorStatus: undefined,
  visitDate: '2026-09-20',
  outcome: 'received',
  oralMorphine: 'available',
  consent: true,
};

test('report validation requires a real centre, consent and a non-future visit date', () => {
  assert.equal(validateReportInput(baseExisting, '2026-09-30').valid, true);
  assert.equal(validateReportInput({ ...baseExisting, visitDate: '2026-10-01' }, '2026-09-30').errors.visitDate, 'Visit date cannot be in the future.');
  assert.equal(validateReportInput({ ...baseExisting, consent: false }, '2026-09-30').errors.consent, 'Consent is required to share this report.');
  assert.ok(validateReportInput({ ...baseExisting, centreId: '', isNewCentre: false }, '2026-09-30').errors.centre);
});

test('new hospitals require Karnataka name, district and address, with explicit coordinate pairs', () => {
  const valid = validateReportInput({
    ...baseExisting,
    isNewCentre: true,
    centreId: '',
    state: 'Karnataka',
    newCentreName: 'Jeevika Hospital',
    newCentreDistrict: 'Mysuru',
    newCentreAddress: 'A known address, Mysuru',
    newCentreLatitude: '12.2958',
    newCentreLongitude: '76.6394',
  }, '2026-09-30');
  assert.equal(valid.valid, true);
  const bad = validateReportInput({ ...baseExisting, isNewCentre: true, centreId: '', state: 'Karnataka', newCentreName: 'Jeevika Hospital', newCentreDistrict: '', newCentreAddress: '', newCentreLatitude: '12.2', newCentreLongitude: '' }, '2026-09-30');
  assert.ok(bad.errors.district);
  assert.ok(bad.errors.address);
  assert.ok(bad.errors.coordinates);
  assert.ok(validateReportInput({ ...baseExisting, isNewCentre: true, centreId: '', state: 'Kerala', newCentreName: 'Outside Karnataka', newCentreDistrict: 'Kochi', newCentreAddress: 'Address' }, '2026-09-30').errors.centre);
});

test('optional bill accepts private image or PDF metadata only within the server file limit', () => {
  assert.equal(validateReportInput({ ...baseExisting, bill: { type: 'application/pdf', size: 4 * 1024 * 1024 } }, '2026-09-30').valid, true);
  assert.ok(validateReportInput({ ...baseExisting, bill: { type: 'text/plain', size: 12 } }, '2026-09-30').errors.bill);
  assert.ok(validateReportInput({ ...baseExisting, bill: { type: 'image/png', size: 4 * 1024 * 1024 + 1 } }, '2026-09-30').errors.bill);
});

test('doctor reports remain explicitly self-reported and never verified', () => {
  assert.equal(validateReportInput({ ...baseExisting, reporterRole: 'doctor', doctorStatus: 'self-reported' }, '2026-09-30').valid, true);
  assert.ok(validateReportInput({ ...baseExisting, reporterRole: 'doctor', doctorStatus: 'verified' }, '2026-09-30').errors.doctorStatus);
});

test('summary dedupes private reporter ids for the same centre and visit day', () => {
  const reports = [
    { id: 'a', centreId: 'aster-cmi', visitDate: '2026-09-20', outcome: 'received', oralMorphine: 'available', reporterId: 'browser-a' },
    { id: 'a-duplicate', centreId: 'aster-cmi', visitDate: '2026-09-20', outcome: 'received', oralMorphine: 'available', reporterId: 'browser-a' },
    { id: 'b', centreId: 'aster-cmi', visitDate: '2026-09-18', outcome: 'unavailable', oralMorphine: 'unavailable', reporterId: 'browser-b' },
    { id: 'c', centreId: 'aster-cmi', visitDate: '2026-09-17', outcome: 'received', oralMorphine: 'available' },
  ];
  const summary = aggregateCentreReports(reports, '2026-09-30');
  assert.equal(summary.totalCount, 3);
  assert.equal(summary.recentCount, 3);
  assert.equal(summary.distinctRecentReports, 3);
  assert.equal(summary.lastDate, '2026-09-20');
  assert.equal(summary.status, 'mixed');
  assert.equal(summary.hasConflictingReports, true);
});

test('aged reports are visible in the count but marked outdated, while future rows do not count', () => {
  const summary = aggregateCentreReports([
    { id: 'old', centreId: 'aster-cmi', visitDate: '2026-01-01', outcome: 'received', oralMorphine: 'available' },
    { id: 'future', centreId: 'aster-cmi', visitDate: '2026-10-03', outcome: 'received', oralMorphine: 'available' },
  ], '2026-09-30');
  assert.equal(summary.totalCount, 1);
  assert.equal(summary.recentCount, 0);
  assert.equal(summary.lastDate, '2026-01-01');
  assert.equal(summary.status, 'outdated');
});

test('anonymous public rows remain separate because reporter ids are not public', () => {
  const summary = aggregateCentreReports([
    { id: 'public-a', centreId: 'aster-cmi', visitDate: '2026-09-20', outcome: 'received', oralMorphine: 'available' },
    { id: 'public-b', centreId: 'aster-cmi', visitDate: '2026-09-20', outcome: 'received', oralMorphine: 'available' },
  ], '2026-09-30');
  assert.equal(summary.recentCount, 2);
});
