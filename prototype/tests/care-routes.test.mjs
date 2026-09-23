import test from 'node:test';
import assert from 'node:assert/strict';
import { CARE_ROUTES, careViewFromPath, canOpenCareView } from '../app/care-routes.ts';

test('legacy workspace URLs always resolve to a supported care screen', () => {
  for (const path of Object.values(CARE_ROUTES).filter((path) => path.startsWith('/workspace/'))) {
    const resolved = careViewFromPath(path);
    assert.ok(resolved, path);
    assert.ok(!CARE_ROUTES[resolved].startsWith('/workspace/'), `${path}: ${resolved}`);
  }
  assert.equal(careViewFromPath('/workspace/draft'), 'doctor-record');
  assert.equal(careViewFromPath('/workspace/retrieve'), 'doctor-history');
  assert.equal(careViewFromPath('/workspace/unknown'), 'home');
});

test('legacy clinical views cannot be opened by any session role', () => {
  for (const [view, path] of Object.entries(CARE_ROUTES)) {
    if (!path.startsWith('/workspace/')) continue;
    for (const role of ['patient', 'family', 'care-team']) {
      assert.equal(canOpenCareView(view, role, 'Dr Sujay · Clinical lead'), false, `${role}: ${view}`);
    }
  }
});

test('family coordination tools have distinct routes and keep doctor review separate', () => {
  for (const [path, view] of [['/medicines', 'medicines'], ['/care-circle', 'care-circle'], ['/family-tasks', 'family-tasks'], ['/costs', 'cost-help'], ['/reports', 'reports']]) {
    assert.equal(careViewFromPath(path), view);
    assert.equal(careViewFromPath(`${path}/`), view);
    assert.equal(canOpenCareView(view, 'family', ''), true);
  }
  assert.equal(canOpenCareView('doctor-review', 'family', ''), false);
  assert.equal(canOpenCareView('doctor-review', 'patient', ''), false);
});
