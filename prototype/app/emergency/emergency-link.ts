// Read-only emergency card page. The Android app makes the link; the owner applies
// android/supabase/migrations/20261027190000_emergency_card_links.sql before it works.
// Same Supabase project and public (publishable) key the Android app uses (CareCloud.kt).
export const EMERGENCY_PROJECT_URL = 'https://kjnhtakqzboayqorrlue.supabase.co';
export const EMERGENCY_PUBLIC_KEY = 'sb_publishable_bheyh26N5MKpXNEJDQgq8w_oDRhW8Xe';

export type EmergencyGoals = { version: number; confirmedBy: string; confirmedAt: number; whatMatters: string; nextSteps: string; newerUnconfirmedVersion?: number };
export type EmergencyCard = {
  kind: 'sahara-emergency-card'; v: number; cardNumber: number; cardId: string; madeAt: number;
  name?: string; goals?: EmergencyGoals;
  contacts?: { name: string; relationship: string; phone: string }[];
  medicines?: { name: string; detail: string }[];
  notRecorded?: string; notIncluded?: string[];
};
export type EmergencyResult =
  | { status: 'live'; card: EmergencyCard; updatedAt: string; expiresAt: string }
  | { status: 'missing' | 'unknown' | 'off' | 'expired' | 'not-set-up' | 'unreachable' };

/** The token is only ever in the #fragment (never sent to this website's server). */
export function tokenFromHash(hash: string): string | null {
  const value = new URLSearchParams(hash.replace(/^#/, '')).getAll('t');
  return value.length === 1 && /^[a-f0-9]{64}$/.test(value[0]) ? value[0] : null;
}

export async function loadEmergencyCard(hash: string, fetcher: typeof fetch = fetch): Promise<EmergencyResult> {
  const token = tokenFromHash(hash);
  if (!token) return { status: 'missing' };
  let response: Response;
  try {
    response = await fetcher(`${EMERGENCY_PROJECT_URL}/rest/v1/rpc/healthathon_emergency_view`, {
      method: 'POST', cache: 'no-store', referrerPolicy: 'no-referrer',
      headers: { apikey: EMERGENCY_PUBLIC_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_token: token }),
    });
  } catch { return { status: 'unreachable' }; }
  // PostgREST answers 404 while the function does not exist yet (migration not applied).
  if (response.status === 404) return { status: 'not-set-up' };
  if (!response.ok) return { status: 'unreachable' };
  const body = await response.json().catch(() => null) as { status?: string; card?: EmergencyCard; updated_at?: string; expires_at?: string } | null;
  if (body?.status === 'live' && body.card?.kind === 'sahara-emergency-card') return { status: 'live', card: body.card, updatedAt: body.updated_at ?? '', expiresAt: body.expires_at ?? '' };
  if (body?.status === 'off' || body?.status === 'expired') return { status: body.status };
  return { status: 'unknown' };
}

export const MESSAGES: Record<Exclude<EmergencyResult['status'], 'live'>, { title: string; detail: string }> = {
  missing: { title: 'No card in this link', detail: 'Scan the QR code on the Sahara emergency card again. · कार्ड का QR फिर से स्कैन करें।' },
  unknown: { title: 'This link does not open a card', detail: 'It may be mistyped or replaced. Use the offline code on the printed card, or ask the family. · ऑफ़लाइन कोड देखें या परिवार से पूछें।' },
  off: { title: 'The family turned this link off', detail: 'Ask the family for their newest card. The offline code on a printed card still shows its text. · परिवार से नया कार्ड माँगें।' },
  expired: { title: 'This link has expired', detail: 'Ask the family for their newest card. · परिवार से नया कार्ड माँगें।' },
  'not-set-up': { title: 'Emergency links are not live yet', detail: 'The Sahara server is still being set up. Use the offline code on the printed card. · सर्वर अभी सेट हो रहा है, ऑफ़लाइन कोड देखें।' },
  unreachable: { title: 'Could not load the card', detail: 'Check the internet connection and reload. The offline code on the printed card works without internet. · इंटरनेट जाँचें।' },
};
