// Read-only emergency card page. The Android app makes the link; the owner applies
// android/supabase/migrations/20261027190000_emergency_card_links.sql before it works,
// 20261123120000_emergency_card_view_places.sql so the patient's doctor sees where it was opened, and
// 20261124120000_emergency_card_versions.sql for card versions ("not the latest version" banner).
// Same Supabase project and public (publishable) key the Android app uses (CareCloud.kt).
//
// The printed QR holds only https://sahara.wedevit.in/emergency#t=<64 hex token>. This page never reads card
// content from the URL: it sends the token to the server and shows what the server returns. It does not ask
// for or keep the scanner's location; the only thing sent besides the token is the optional typed
// "your hospital or clinic" line.
export const EMERGENCY_PROJECT_URL = 'https://kjnhtakqzboayqorrlue.supabase.co';
export const EMERGENCY_PUBLIC_KEY = 'sb_publishable_bheyh26N5MKpXNEJDQgq8w_oDRhW8Xe';

export type EmergencyGoals = { version: number; confirmedBy: string; confirmedAt: number; whatMatters: string; nextSteps: string; newerUnconfirmedVersion?: number };
export type EmergencyPerson = { name: string; relationship: string; phone: string };
export type EmergencyDoctor = { name: string; phone: string; place: string; landline: string };
export type EmergencyCard = {
  kind: 'sahara-emergency-card'; v: number; cardNumber: number; cardId: string; madeAt: number;
  /** v2 (24 Nov 2026): made + 90 days. */
  validUntil?: number;
  sections?: string[];
  name?: string; goals?: EmergencyGoals;
  contacts?: EmergencyPerson[];
  /** v2: the next of kin, the treating doctor, and the contacts other than those two. */
  nextOfKin?: EmergencyPerson; doctor?: EmergencyDoctor; otherContacts?: EmergencyPerson[];
  /** v2 adds strength and when; detail stays for older pages. */
  medicines?: { name: string; detail?: string; strength?: string; when?: string }[];
  allergies?: string;
  notRecorded?: string; notIncluded?: string[];
};
export type EmergencyLive = {
  status: 'live'; card: EmergencyCard; updatedAt: string; expiresAt: string;
  /** Version of the card shown and when it was published (servers before 24 Nov: 1 and updatedAt). */
  version: number; publishedAt: string;
  /** The version whose QR was scanned, and the newest version of this card. */
  scannedVersion: number; latestVersion: number; latestPublishedAt: string;
};
export type EmergencyResult = EmergencyLive | { status: 'missing' | 'unknown' | 'off' | 'expired' | 'not-set-up' | 'unreachable' };

/** A card is valid for 3 months (the app prints the same valid-until date). */
export const VALID_DAYS = 90;
const DAY_MS = 86_400_000;

/** The token is only ever in the #fragment (never sent to this website's server). */
export function tokenFromHash(hash: string): string | null {
  const value = new URLSearchParams(hash.replace(/^#/, '')).getAll('t');
  return value.length === 1 && /^[a-f0-9]{64}$/.test(value[0]) ? value[0] : null;
}

/** Longest hospital or clinic name kept with an open (the server cuts at the same length). */
export const PLACE_MAX = 80;

/** One plain line: control characters and runs of spaces become one space, trimmed, at most [PLACE_MAX] characters. */
export function cleanPlace(value: unknown): string {
  if (typeof value !== 'string') return '';
  return value.replace(/[\u0000-\u001f\u007f-\u009f\s]+/g, ' ').trim().slice(0, PLACE_MAX).trim();
}

const whole = (value: unknown, fallback: number) => (typeof value === 'number' && Number.isInteger(value) && value >= 1 ? value : fallback);
const text = (value: unknown) => (typeof value === 'string' ? value : '');

/**
 * Opens the card for the token in [hash]. [place] is the optional "Your hospital or clinic" line, sent only when
 * it is not blank. If the server is older (the place migration not applied yet) it answers 404 for the two-argument
 * call; the card is then opened again without the place, so the link never breaks because of the new field.
 * [latest] = true asks for the newest version of the same card (after "not the latest version"); it is not counted
 * as another open.
 */
export async function loadEmergencyCard(hash: string, fetcher: typeof fetch = fetch, place = '', latest = false): Promise<EmergencyResult> {
  const token = tokenFromHash(hash);
  if (!token) return { status: 'missing' };
  const where = latest ? '' : cleanPlace(place);
  const call = (body: Record<string, string | boolean>) => fetcher(`${EMERGENCY_PROJECT_URL}/rest/v1/rpc/healthathon_emergency_view`, {
    method: 'POST', cache: 'no-store', referrerPolicy: 'no-referrer',
    headers: { apikey: EMERGENCY_PUBLIC_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  let response: Response;
  try {
    response = await call(latest ? { p_token: token, p_latest: true } : where ? { p_token: token, p_place: where } : { p_token: token });
    if (response.status === 404 && where) response = await call({ p_token: token });
  } catch { return { status: 'unreachable' }; }
  // PostgREST answers 404 while the function does not exist yet (migration not applied).
  if (response.status === 404) return { status: 'not-set-up' };
  if (!response.ok) return { status: 'unreachable' };
  const body = await response.json().catch(() => null) as Record<string, unknown> | null;
  const card = body?.card as EmergencyCard | undefined;
  if (body?.status === 'live' && card && typeof card === 'object' && card.kind === 'sahara-emergency-card') {
    const updatedAt = text(body.updated_at);
    const version = whole(body.version, 1);
    const latestVersion = Math.max(whole(body.latest_version, version), version);
    return {
      status: 'live', card, updatedAt, expiresAt: text(body.expires_at),
      version, publishedAt: text(body.published_at) || updatedAt,
      scannedVersion: whole(body.scanned_version, version), latestVersion,
      latestPublishedAt: text(body.latest_published_at) || text(body.published_at) || updatedAt,
    };
  }
  if (body?.status === 'off' || body?.status === 'expired') return { status: body.status };
  return { status: 'unknown' };
}

/** True when the family has published a newer card than the one whose QR was scanned. */
export function isOlderVersion(result: EmergencyLive): boolean {
  return result.scannedVersion < result.latestVersion;
}

/** When the card shown was made: its published time, else (older servers) the last update or the card's own date. */
export function cardDate(result: EmergencyLive): number {
  for (const value of [result.publishedAt, result.updatedAt]) { const at = Date.parse(value); if (Number.isFinite(at)) return at; }
  return typeof result.card.madeAt === 'number' ? result.card.madeAt : NaN;
}

/** True once the card shown is more than 3 months old (or past the valid-until date printed on it). */
export function isOverThreeMonths(result: EmergencyLive, now = Date.now()): boolean {
  const made = cardDate(result);
  const until = typeof result.card.validUntil === 'number' ? result.card.validUntil : made + VALID_DAYS * DAY_MS;
  return Number.isFinite(until) && now > until;
}

export type ContactRow = { who: string; name: string; phone: string };
export type MedicineRow = { name: string; strength: string; when: string };
export const NOT_SAVED = 'not saved';

const clean = (value: unknown) => text(value).replace(/\s+/g, ' ').trim();

/** Who to call, in the same order as the printed card: next of kin, treating doctor, hospital or clinic, others. */
export function contactRows(card: EmergencyCard): ContactRow[] {
  const all = Array.isArray(card.contacts) ? card.contacts : [];
  const rows: ContactRow[] = [];
  const kin = card.nextOfKin ?? all[0];
  if (kin) rows.push({ who: 'Next of kin', name: [clean(kin.name), clean(kin.relationship) && `(${clean(kin.relationship)})`].filter(Boolean).join(' '), phone: clean(kin.phone) || NOT_SAVED });
  const doctor = card.doctor;
  if (doctor && (clean(doctor.name) || clean(doctor.phone))) rows.push({ who: 'Treating doctor', name: clean(doctor.name) || NOT_SAVED, phone: clean(doctor.phone) || NOT_SAVED });
  if (doctor && (clean(doctor.place) || clean(doctor.landline))) rows.push({ who: 'Hospital / clinic', name: clean(doctor.place) || NOT_SAVED, phone: clean(doctor.landline) || NOT_SAVED });
  const others = Array.isArray(card.otherContacts) ? card.otherContacts : all.slice(1);
  for (const o of others) rows.push({ who: clean(o.relationship) || 'Contact', name: clean(o.name), phone: clean(o.phone) || NOT_SAVED });
  return rows;
}

const STRENGTH = /\b\d+(?:[.,]\d+)?\s?(?:mg|mcg|µg|g|ml|iu|units?|%)(?:\s?\/\s?\d*\s?(?:ml|h|hr|hours?))?(?![a-z])/i;
const LABELS = ['Strength: ', 'Dose: ', 'How often: ', 'Frequency: ', 'Times: ', 'Food: ', 'How much each time: ', 'Form: '];
const labelled = (lines: string[], ...labels: string[]) => {
  for (const line of lines) for (const label of labels) if (line.startsWith(label)) return line.slice(label.length).trim();
  return '';
};

/** Name, strength and when. v2 cards carry them; older cards are read from the saved text the same way the app does. */
export function medicineRows(card: EmergencyCard): MedicineRow[] {
  return (Array.isArray(card.medicines) ? card.medicines : []).map((m) => {
    const name = clean(m.name);
    if (typeof m.strength === 'string' || typeof m.when === 'string') return { name, strength: clean(m.strength), when: clean(m.when) };
    const lines = text(m.detail).split('\n').map((line) => line.trim());
    if (lines.some((line) => LABELS.some((label) => line.startsWith(label)))) {
      const strength = labelled(lines, 'Strength: ', 'Dose: ') || (name.match(STRENGTH)?.[0] ?? '');
      const when = [labelled(lines, 'How much each time: '), labelled(lines, 'How often: ', 'Frequency: '), labelled(lines, 'Times: '), labelled(lines, 'Food: ')].filter(Boolean).join(', ');
      return { name, strength, when };
    }
    const first = (lines[0] ?? '').split('. ')[0].trim().replace(/\.+$/, '');
    return { name, strength: (name.match(STRENGTH)?.[0] ?? first.match(STRENGTH)?.[0] ?? '').trim(), when: first };
  });
}

/** A phone number as a tel: link target (digits and a leading +). */
export const telHref = (phone: string) => `tel:${phone.replace(/[^+0-9]/g, '')}`;

export const NOT_LATEST = { title: 'This is not the latest version of this card', detail: 'The family has made a newer card since this one was printed. · यह इस कार्ड का नवीनतम संस्करण नहीं है।' };
export const OVER_THREE_MONTHS = { title: 'This card is more than 3 months old — check with the family/doctor', detail: 'Medicines and contacts may have changed. · यह कार्ड 3 महीने से पुराना है — परिवार या डॉक्टर से पुष्टि करें।' };

export const MESSAGES: Record<Exclude<EmergencyResult['status'], 'live'>, { title: string; detail: string }> = {
  missing: { title: 'No card in this link', detail: 'Scan the QR code on the Sahara emergency card again. · कार्ड का QR फिर से स्कैन करें।' },
  unknown: { title: 'This link does not open a card', detail: 'It may be mistyped or replaced. Ask the family for their newest card. · परिवार से नया कार्ड माँगें।' },
  off: { title: 'The family turned this link off', detail: 'This card may be lost or out of date. Ask the family for their newest card. · परिवार से नया कार्ड माँगें।' },
  expired: { title: 'This link has expired', detail: 'Ask the family for their newest card. · परिवार से नया कार्ड माँगें।' },
  'not-set-up': { title: 'Emergency links are not live yet', detail: 'The Sahara server is still being set up. Call the family or doctor named on the printed card. · परिवार या डॉक्टर को फ़ोन करें।' },
  unreachable: { title: 'Could not load the card', detail: 'Check the internet connection and reload. Call the family or doctor named on the printed card. · इंटरनेट जाँचें।' },
};
