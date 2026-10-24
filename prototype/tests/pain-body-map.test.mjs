import test from 'node:test';
import assert from 'node:assert/strict';
import {
  PAIN_BODY_SITES, painBodyRegionCenter, visiblePainBodySites, classifyPainBodyPoint,
  togglePainBodySite, validPainViewerState, isPainBodyPoint, quantizePainBodyPoint, painBodyPointContext,
} from '../app/pain-body-map-state.ts';

test('front/back laterality always describes the patient, not the viewer', () => {
  assert.equal(PAIN_BODY_SITES.length, 12);
  for (const limb of ['shoulder', 'arm', 'leg']) {
    assert.ok(painBodyRegionCenter(`left-${limb}`, 'front').x > 100);
    assert.ok(painBodyRegionCenter(`left-${limb}`, 'back').x < 100);
    assert.ok(painBodyRegionCenter(`right-${limb}`, 'front').x < 100);
    assert.ok(painBodyRegionCenter(`right-${limb}`, 'back').x > 100);
  }
  assert.ok(!visiblePainBodySites('front').includes('upper-back'));
  assert.ok(!visiblePainBodySites('back').includes('chest'));
});

test('3D picks use fixed patient coordinates even when camera rotates', () => {
  assert.equal(classifyPainBodyPoint({ x: 0.7, y: 0.6, z: 0.1 }), 'left-arm');
  assert.equal(classifyPainBodyPoint({ x: -0.7, y: 0.6, z: -0.1 }), 'right-arm');
  assert.equal(classifyPainBodyPoint({ x: 0.2, y: 0.3, z: 0 }), 'left-leg');
  assert.equal(classifyPainBodyPoint({ x: -0.2, y: 0.3, z: 0 }), 'right-leg');
  assert.equal(classifyPainBodyPoint({ x: 0, y: 0.7, z: 0.8 }), 'chest');
  assert.equal(classifyPainBodyPoint({ x: 0, y: 0.7, z: -0.8 }), 'upper-back');
  assert.equal(classifyPainBodyPoint({ x: NaN, y: 0.7, z: 0 }), null);
});

test('viewer accepts only whitelisted sites, valid scores and bounded associated points', () => {
  const state = { selected: ['left-arm'], rating: 7, hindi: false, points: [{ site: 'left-arm', x: 0.7, y: 0.6, z: 0.1 }] };
  assert.ok(validPainViewerState(state));
  assert.ok(validPainViewerState({ ...state, readOnly: true }));
  assert.equal(validPainViewerState({ ...state, readOnly: 'true' }), false);
  assert.ok(validPainViewerState({ ...state, rating: null }));
  for (const change of [
    { rating: 11 }, { rating: -1 }, { rating: 1.2 }, { rating: '7' }, { hindi: 'false' },
    { selected: ['heart'] }, { selected: ['left-arm', 'left-arm'] },
    { points: [{ site: 'right-arm', x: 0, y: 0.6, z: 0 }] },
    { points: [{ site: 'left-arm', x: Infinity, y: 0.6, z: 0 }] },
    { points: [{ site: 'left-arm', x: 0, y: -0.1, z: 0 }] },
    { points: [state.points[0], state.points[0]] },
  ]) assert.equal(validPainViewerState({ ...state, ...change }), false, JSON.stringify(change));
  assert.ok(!isPainBodyPoint(null));
});

test('point quantization is deterministic and selecting a broad region never invents a point', () => {
  const point = { site: 'left-arm', x: 0.123456, y: 0.654321, z: -0.123456 };
  assert.deepEqual(quantizePainBodyPoint(point), { site: 'left-arm', x: 0.123, y: 0.654, z: -0.123 });
  assert.equal(painBodyPointContext([point]), painBodyPointContext([{ ...point, x: 0.12349 }]));
  assert.notEqual(painBodyPointContext([point]), painBodyPointContext([{ ...point, x: 0.129 }]));
  const selected = ['left-arm'];
  assert.deepEqual(togglePainBodySite(selected, 'right-arm'), ['left-arm', 'right-arm']);
  assert.deepEqual(selected, ['left-arm']);
});
