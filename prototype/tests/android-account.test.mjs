import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ANDROID_ACCOUNT_URL, AccountAuthError, changeRecoveredPassword, parseAccountCallback,
  passwordError, publicAuthConfig, revokeAccountSession, verifyAccountCallback,
} from '../app/android-account/account-auth.ts';

const base = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const config = { url: base, key: 'sb_publishable_test_only' };
const now = Date.UTC(2026, 9, 10);
const userId = 'c35b06dc-90f8-42d0-940d-ce216f2cf4de';
const jwt = (value) => `header.${Buffer.from(JSON.stringify(value)).toString('base64url')}.signature`;
const token = (change = {}) => jwt({ iss: `${base}/auth/v1`, role: 'authenticated', sub: userId, exp: now / 1000 + 3600, amr: [{ method: 'otp', timestamp: now / 1000 }], ...change });
const callback = (change = {}) => ({ kind: 'recovery', accessToken: token(), ...change });
const user = (change = {}) => ({ id: userId, email: 'review@example.test', email_confirmed_at: '2026-10-10T00:00:00Z', ...change });
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });

test('only the intended project and public API keys can reach the client', () => {
  assert.equal(ANDROID_ACCOUNT_URL, 'https://sahara.wedevit.in/android-account');
  assert.deepEqual(publicAuthConfig(base, config.key), config);
  assert.equal(publicAuthConfig('https://other-project.supabase.co', config.key), null);
  assert.equal(publicAuthConfig(base, 'sb_secret_do_not_expose'), null);
  assert.equal(publicAuthConfig(base, jwt({ role: 'service_role', ref: 'cghlkqeqiokjqdionrkc' })), null);
  assert.equal(publicAuthConfig(base, jwt({ role: 'anon', ref: 'another-project' })), null);
  assert.equal(publicAuthConfig(base, jwt({ role: 'anon', ref: 'cghlkqeqiokjqdionrkc' }))?.url, base);
});

test('callback needs the expected type, bearer token and unambiguous fields', () => {
  const suffix = `access_token=${token()}&token_type=bearer`;
  assert.deepEqual(parseAccountCallback(`#type=recovery&${suffix}&refresh_token=discard-this`), callback());
  assert.equal(parseAccountCallback(`#type=signup&${suffix}`).kind, 'confirmation');
  assert.equal(parseAccountCallback(`#type=magiclink&${suffix}`).kind, 'invalid');
  assert.equal(parseAccountCallback(`#type=recovery&type=signup&${suffix}`).kind, 'invalid');
  assert.equal(parseAccountCallback(`#type=recovery&${suffix}&access_token=extra`).kind, 'invalid');
  assert.equal(parseAccountCallback(`#type=recovery&access_token=${token()}`).kind, 'invalid');
  assert.deepEqual(parseAccountCallback(''), { kind: 'missing' });
});

test('callback errors discard provider text and credentials', () => {
  assert.deepEqual(parseAccountCallback('#error=access_denied&error_description=private-data&access_token=secret'), { kind: 'expired' });
  assert.deepEqual(parseAccountCallback('#'), { kind: 'missing' });
});

test('recovery requires an unexpired OTP session from this project before the server check', async () => {
  const unexpectedFetch = async () => { throw new Error('Must reject before calling Auth'); };
  for (const change of [{ amr: [{ method: 'password' }] }, { exp: now / 1000 - 1 }, { iss: 'https://another.example/auth/v1' }]) {
    await assert.rejects(verifyAccountCallback(config, callback({ accessToken: token(change) }), unexpectedFetch, now), (error) => error instanceof AccountAuthError && error.expired);
  }
});

test('decoding claims is insufficient: Auth must accept the token and return the same confirmed user', async () => {
  await assert.rejects(verifyAccountCallback(config, callback(), async () => json({}, 401), now), /expired/);
  await assert.rejects(verifyAccountCallback(config, callback(), async () => json(user({ id: 'someone-else' })), now), /could not be verified/);
  await assert.rejects(verifyAccountCallback(config, callback(), async () => json(user({ email_confirmed_at: null })), now), /could not be verified/);
  const result = await verifyAccountCallback(config, callback(), async (url, options) => {
    assert.equal(url, `${base}/auth/v1/user`);
    assert.equal(options.method, 'GET');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.cache, 'no-store');
    assert.equal(options.referrerPolicy, 'no-referrer');
    assert.equal(options.redirect, 'error');
    assert.equal(options.headers.Authorization, `Bearer ${token()}`);
    return json(user());
  }, now);
  assert.deepEqual(result, { id: userId, email: 'review@example.test' });
});

test('password validation blocks short, blank and mismatched input before a request', async () => {
  assert.ok(passwordError('short', 'short'));
  assert.ok(passwordError('          ', '          '));
  assert.ok(passwordError('a long new password', 'different password'));
  let calls = 0;
  await assert.rejects(changeRecoveredPassword(config, token(), 'short', 'short', async () => { calls += 1; return json({}); }), /10 characters/);
  assert.equal(calls, 0);
});

test('successful password update revokes only its recovery session', async () => {
  const requests = [];
  const password = 'A fresh password for review!';
  const result = await changeRecoveredPassword(config, token(), password, password, async (url, options) => {
    requests.push([url, options.method]);
    if (options.method === 'PUT') {
      assert.deepEqual(JSON.parse(options.body), { password });
      assert.equal(options.cache, 'no-store');
      return json(user());
    }
    assert.equal(options.keepalive, true);
    return new Response(null, { status: 204 });
  });
  assert.deepEqual(result, { sessionRevoked: true });
  assert.deepEqual(requests, [[`${base}/auth/v1/user`, 'PUT'], [`${base}/auth/v1/logout?scope=local`, 'POST']]);
});

test('a failed update never claims success or logs out before the user can retry', async () => {
  let calls = 0;
  await assert.rejects(changeRecoveredPassword(config, token(), 'Another long password!', 'Another long password!', async () => {
    calls += 1; return json({ code: 'same_password' }, 422);
  }), /different from your current/);
  assert.equal(calls, 1);
});

test('expired recovery sessions require a fresh link', async () => {
  await assert.rejects(changeRecoveredPassword(config, token(), 'Another long password!', 'Another long password!', async () => json({}, 401)), (error) => error.expired);
});

test('sign-out retries separately without repeating a successful password change', async () => {
  let updates = 0; let logouts = 0;
  const result = await changeRecoveredPassword(config, token(), 'Another long password!', 'Another long password!', async (_url, options) => {
    if (options.method === 'PUT') { updates += 1; return json(user()); }
    logouts += 1; return json({}, 503);
  });
  assert.deepEqual(result, { sessionRevoked: false });
  assert.equal(updates, 1);
  assert.equal(logouts, 2);
});

test('only accepted sign-out confirms revocation; expired tokens and network failures do not', async () => {
  assert.equal(await revokeAccountSession(config, token(), async () => json({}, 401)), false);
  assert.equal(await revokeAccountSession(config, token(), async () => { throw new Error('offline'); }), false);
});
