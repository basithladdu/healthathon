export const ANDROID_ACCOUNT_URL = 'https://sahara.wedevit.in/android-account';
const AUTH_PROJECT_URL = 'https://cghlkqeqiokjqdionrkc.supabase.co';

export type PublicAuthConfig = { url: string; key: string };
export type AccountCallback =
  | { kind: 'confirmation' | 'recovery'; accessToken: string }
  | { kind: 'missing' | 'expired' | 'invalid' };

function claims(value: string): Record<string, unknown> | null {
  try {
    const part = value.split('.')[1];
    const binary = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    const decoded = new TextDecoder().decode(Uint8Array.from(binary, (character) => character.charCodeAt(0)));
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch { return null; }
}

export function publicAuthConfig(url: string | undefined, key: string | undefined): PublicAuthConfig | null {
  const cleanKey = key?.trim();
  if (url?.replace(/\/$/, '') !== AUTH_PROJECT_URL || !cleanKey) return null;
  const publicKey = /^sb_publishable_[A-Za-z0-9_-]+$/.test(cleanKey)
    || (claims(cleanKey)?.role === 'anon' && claims(cleanKey)?.ref === 'cghlkqeqiokjqdionrkc');
  return publicKey ? { url: AUTH_PROJECT_URL, key: cleanKey } : null;
}

export function parseAccountCallback(hash: string): AccountCallback {
  if (!hash || hash === '#') return { kind: 'missing' };
  const values = new URLSearchParams(hash.replace(/^#/, ''));
  if (values.has('error') || values.has('error_code')) return { kind: 'expired' };
  const accessToken = values.get('access_token');
  const type = values.get('type');
  if (values.getAll('access_token').length !== 1 || values.getAll('type').length !== 1
    || values.get('token_type')?.toLowerCase() !== 'bearer'
    || !accessToken || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(accessToken)) {
    return { kind: 'invalid' };
  }
  if (type === 'signup') return { kind: 'confirmation', accessToken };
  if (type === 'recovery') return { kind: 'recovery', accessToken };
  return { kind: 'invalid' };
}

export function passwordError(password: string, confirmation: string): string | null {
  if (password.length < 10 || !password.trim()) return 'Use at least 10 characters.';
  if (password !== confirmation) return 'The passwords do not match.';
  return null;
}

export class AccountAuthError extends Error {
  readonly expired: boolean;
  constructor(message: string, expired = false) { super(message); this.expired = expired; }
}

type Request = typeof fetch;

async function authRequest(config: PublicAuthConfig, path: string, token: string, options: RequestInit, request: Request) {
  if (!publicAuthConfig(config.url, config.key)) throw new AccountAuthError('Account support is unavailable. Please contact us.');
  try {
    return await request(`${config.url}/auth/v1${path}`, {
      ...options,
      headers: { apikey: config.key, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      cache: 'no-store', credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new AccountAuthError('Could not connect. Check your internet connection and try again.'); }
}

export async function verifyAccountCallback(
  config: PublicAuthConfig,
  callback: Extract<AccountCallback, { accessToken: string }>,
  request: Request = fetch,
  now = Date.now(),
): Promise<{ id: string; email: string }> {
  const tokenClaims = claims(callback.accessToken);
  const methods = Array.isArray(tokenClaims?.amr) ? tokenClaims.amr : [];
  if (tokenClaims?.iss !== `${AUTH_PROJECT_URL}/auth/v1` || tokenClaims?.role !== 'authenticated'
    || typeof tokenClaims?.sub !== 'string' || typeof tokenClaims?.exp !== 'number' || tokenClaims.exp <= now / 1000
    || (callback.kind === 'recovery' && !methods.some((method) => method?.method === 'otp' || method?.method === 'recovery'))) {
    throw new AccountAuthError('This link has expired or is not valid. Request a new link in Sahara.', true);
  }
  // Claims only filter malformed links. The Auth server must still verify the token.
  const response = await authRequest(config, '/user', callback.accessToken, { method: 'GET' }, request);
  if (response.status === 401 || response.status === 403) {
    throw new AccountAuthError('This link has expired. Request a new link in Sahara.', true);
  }
  if (!response.ok) throw new AccountAuthError('Could not check this link. Try again in a moment.');
  const user = await response.json() as Record<string, unknown> | null;
  if (!user || user.id !== tokenClaims.sub || typeof user.email !== 'string' || !user.email_confirmed_at || user.is_anonymous) {
    throw new AccountAuthError('This link could not be verified. Request a new link in Sahara.', true);
  }
  return { id: user.id, email: user.email };
}

export async function revokeAccountSession(config: PublicAuthConfig, token: string, request: Request = fetch): Promise<boolean> {
  try {
    const response = await authRequest(config, '/logout?scope=local', token, { method: 'POST', body: '{}', keepalive: true }, request);
    return response.ok;
  } catch { return false; }
}

export async function changeRecoveredPassword(
  config: PublicAuthConfig, token: string, password: string, confirmation: string, request: Request = fetch,
): Promise<{ sessionRevoked: boolean }> {
  const validation = passwordError(password, confirmation);
  if (validation) throw new AccountAuthError(validation);
  const response = await authRequest(config, '/user', token, { method: 'PUT', body: JSON.stringify({ password }) }, request);
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new AccountAuthError('This link has expired. Request a new link in Sahara.', true);
    }
    const error = await response.json().catch(() => ({})) as Record<string, unknown> | null;
    const code = error?.code ?? error?.error_code;
    if (code === 'same_password') throw new AccountAuthError('Choose a password different from your current one.');
    if (code === 'weak_password') throw new AccountAuthError('Choose a stronger password with a mix of words, numbers or symbols.');
    if (response.status === 429) throw new AccountAuthError('Too many attempts. Wait a little, then try again.');
    throw new AccountAuthError('Could not change your password. Please try again.');
  }
  // A sign-out failure must not turn a successful password change into a failure.
  const sessionRevoked = await revokeAccountSession(config, token, request)
    || await revokeAccountSession(config, token, request);
  return { sessionRevoked };
}
