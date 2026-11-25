// Goals-of-care note sign-off: the rules, the hashes and a small 1-bit PNG encoder. No React, so it runs in node tests.
// The Android twin is CareGocSignOff.kt (GocSignOffRules); both write the same JSON with the same field names.
//
// JSON shape (field names shared with Android, do not rename):
//   GocSignOffRecord {
//     scheme: "sahara-goc-signoff-v1"
//     id: string                       record id (a new one each time the note is signed afresh)
//     noteId: string                   the GOC note this sign-off belongs to
//     noteSha256: hex                  SHA-256 of the note text (UTF-8) that was read and signed
//     status: "OPEN" | "LOCKED" | "INVALIDATED"
//     createdAt: ISO-8601
//     signatures: GocSignature[]       in signing order: doctor, patient or family, witness
//     lockedAt: ISO-8601 | null
//     sealSha256: hex | null           set on lock, see gocSealInput()
//     invalidatedAt: ISO-8601 | null
//     invalidatedReason: string | null English text, for the record
//   }
//   GocSignature {
//     role: "DOCTOR" | "PATIENT" | "FAMILY" | "WITNESS"
//     name: string                     never blank
//     registrationNo: string | null    DOCTOR only, never blank there
//     relation: string | null          PATIENT is "Self"; FAMILY e.g. "Daughter" (never blank); WITNESS optional (e.g. "Nurse")
//     consent: boolean                 PATIENT/FAMILY: "I have read or heard this note and it records what we discussed";
//                                      WITNESS: saw the note read out and the person sign; DOCTOR: true
//     canRead: boolean | null          PATIENT/FAMILY only: can the signer read the note themselves
//     method: "DRAWN" | "THUMB"
//     imageDataUrl: "data:image/png;base64,..."   1-bit black and white PNG, at most 600 px wide
//     imageSha256: hex                 SHA-256 of the PNG bytes
//     strokeCount: number
//     signedAt: ISO-8601
//     noteSha256: hex                  the note hash this signature was made on
//     readAloud: GocReadAloud | null   PATIENT/FAMILY: how the note was read to them (Android ConsentReadAloud.kt);
//                                      null or missing when not recorded
//   }
//   GocReadAloud {
//     method: "SELF" | "PHONE" | "PERSON"   read it themselves, read out by the phone, or by a person
//     readerName, readerRelation: string    PERSON only, otherwise ""
//     language: string                      PHONE only: the language the phone read in ("hi", "te", ...), otherwise ""
//     witnessName, witnessRelation: string  PHONE/PERSON only, otherwise "" (in the GOC sign-off the witness signs separately)
//     at: ISO-8601                          when the reading was chosen or finished
//   }
//
// Seal: sealSha256 = SHA-256 of the UTF-8 bytes of gocSealInput(record): every part written as `<length>:<value>;`
// (length in UTF-16 code units, the same in JS and Kotlin), parts = scheme, noteId, noteSha256, then per signature
// role, name, registrationNo, relation, consent (1/0), canRead (1/0/-), method, imageSha256, strokeCount, signedAt, noteSha256,
// and only when readAloud is present (with a known method) its parts: "readAloud", method, readerName, readerRelation,
// language, witnessName, witnessRelation, at (cleaned as gocCleanReading does, like Kotlin ConsentReadingRules.sealParts).
// A seal made without a reading is unchanged.
// So the seal covers the note text and every signature's PNG; any later edit to either is detectable with gocVerify().

export const GOC_SIGNOFF_SCHEME = 'sahara-goc-signoff-v1';
export const GOC_PNG_MAX_WIDTH = 600;
export const GOC_NAME_MAX = 120;

export type GocSignatureRole = 'DOCTOR' | 'PATIENT' | 'FAMILY' | 'WITNESS';
/** Steps a sign-off can require. WITNESS is added on its own when a thumb impression is used or the signer cannot read. */
export type GocSignerStep = 'DOCTOR' | 'PATIENT_OR_FAMILY' | 'WITNESS';
export type GocSignOffStatus = 'OPEN' | 'LOCKED' | 'INVALIDATED';
export type GocSignatureMethod = 'DRAWN' | 'THUMB';

export type GocSignature = {
  role: GocSignatureRole; name: string; registrationNo: string | null; relation: string | null;
  consent: boolean; canRead: boolean | null; method: GocSignatureMethod;
  imageDataUrl: string; imageSha256: string; strokeCount: number; signedAt: string; noteSha256: string;
  /** How the note was read to the patient or family member before they signed (phone-made sign-offs record it). */
  readAloud?: GocReadAloud | null;
};

export type GocReadingMethod = 'SELF' | 'PHONE' | 'PERSON';
export type GocReadAloud = {
  method: GocReadingMethod; readerName: string; readerRelation: string; language: string;
  witnessName: string; witnessRelation: string; at: string;
};

export type GocSignOffRecord = {
  scheme: typeof GOC_SIGNOFF_SCHEME; id: string; noteId: string; noteSha256: string; status: GocSignOffStatus;
  createdAt: string; signatures: GocSignature[]; lockedAt: string | null; sealSha256: string | null;
  invalidatedAt: string | null; invalidatedReason: string | null;
};

/** What the person signing typed and drew. imageSha256, noteSha256 and signedAt are filled in by gocAddSignature. */
export type GocSignatureInput = {
  role: GocSignatureRole; name: string; registrationNo?: string | null; relation?: string | null;
  consent: boolean; canRead?: boolean | null; method: GocSignatureMethod; imageDataUrl: string; strokeCount: number;
  /** PATIENT/FAMILY: the read-aloud step. When given it must be complete, and it decides canRead (SELF = can read). */
  readAloud?: Partial<GocReadAloud> & { method: string } | null;
};

export type GocSignOffErrorCode =
  | 'NOTE_CHANGED' | 'LOCKED' | 'INVALIDATED' | 'WRONG_ORDER' | 'NAME_BLANK' | 'REGISTRATION_BLANK'
  | 'RELATION_BLANK' | 'CONSENT_MISSING' | 'CAN_READ_MISSING' | 'READ_ALOUD_MISSING' | 'WITNESS_IS_READER' | 'NO_SIGNATURE' | 'NOT_READY';

export class GocSignOffError extends Error {
  readonly code: GocSignOffErrorCode;
  constructor(code: GocSignOffErrorCode) { super(code); this.code = code; this.name = 'GocSignOffError'; }
}

export const GOC_DEFAULT_STEPS: readonly GocSignerStep[] = ['DOCTOR', 'PATIENT_OR_FAMILY'];

// ---------------------------------------------------------------------------
// Hashing

export async function gocSha256Hex(input: string | Uint8Array): Promise<string> {
  const data = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  const digest = await crypto.subtle.digest('SHA-256', data as BufferSource);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** The note hash. The text is hashed exactly as given (no trimming), so any change counts. */
export function gocNoteSha256(noteText: string): Promise<string> { return gocSha256Hex(noteText); }

export function gocDataUrlBytes(dataUrl: string): Uint8Array | null {
  const prefix = 'data:image/png;base64,';
  if (typeof dataUrl !== 'string' || !dataUrl.startsWith(prefix) || dataUrl.length <= prefix.length) return null;
  try {
    const binary = atob(dataUrl.slice(prefix.length));
    return Uint8Array.from(binary, (c) => c.charCodeAt(0));
  } catch { return null; }
}

export function gocBytesToDataUrl(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return 'data:image/png;base64,' + btoa(binary);
}

// ---------------------------------------------------------------------------
// Read aloud (twin of Android ConsentReadingRules in ConsentReadAloud.kt; the cleaning must match it character for
// character, because the cleaned values are sealed).

export const GOC_READ_METHODS: readonly GocReadingMethod[] = ['SELF', 'PHONE', 'PERSON'];
export const GOC_READ_NAME_MAX = 120;

// Kotlin String.trim() drops every char where Char.isWhitespace() (JVM: Character.isWhitespace || isSpaceChar), i.e.
// Unicode Zs/Zl/Zp plus \t \n \v \f \r and \u001C-\u001F. JavaScript's trim() differs (e.g. U+FEFF), so it is not used.
const KT_SPACE = /[\p{Zs}\p{Zl}\p{Zp}\t\n\v\f\r\u001C-\u001F]/u;
function ktTrim(value: string): string {
  let start = 0, end = value.length;
  while (start < end && KT_SPACE.test(value[start])) start++;
  while (end > start && KT_SPACE.test(value[end - 1])) end--;
  return value.slice(start, end);
}
// Java regex \s is ASCII only: [ \t\n\x0B\f\r].
const javaSpaces = (value: string) => value.replace(/[ \t\n\v\f\r]+/g, ' ');
const cleanName = (value: string) => javaSpaces(ktTrim(value)).slice(0, GOC_READ_NAME_MAX);
const normaliseName = (value: string) => javaSpaces(ktTrim(value).toLowerCase());

export const gocReadingNeedsWitness = (method: string | null | undefined) => method === 'PHONE' || method === 'PERSON';
/** The canRead answer the sign-off stores, from how the note was read. */
export const gocReadingCanRead = (r: { method: string }) => r.method === 'SELF';

type ReadingLike = Partial<Record<keyof GocReadAloud, unknown>>;
const text = (value: unknown) => value === undefined ? '' : value === null ? 'null' : typeof value === 'string' ? value : String(value);

/** Trimmed, capped, and only the fields that belong to the method (Kotlin ConsentReadingRules.clean). */
export function gocCleanReading(r: ReadingLike): GocReadAloud {
  const method = text(r.method);
  const person = method === 'PERSON', witness = gocReadingNeedsWitness(method);
  return {
    method: method as GocReadingMethod,
    readerName: person ? cleanName(text(r.readerName)) : '', readerRelation: person ? cleanName(text(r.readerRelation)) : '',
    language: method === 'PHONE' ? ktTrim(text(r.language)) : '',
    witnessName: witness ? cleanName(text(r.witnessName)) : '', witnessRelation: witness ? cleanName(text(r.witnessRelation)) : '',
    at: ktTrim(text(r.at)),
  };
}

/** A stored readAloud value as Android reads it back: null unless it is an object with a known method, then cleaned. */
export function gocDecodeReading(value: unknown): GocReadAloud | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const r = value as ReadingLike;
  if (typeof r.method !== 'string' || !(GOC_READ_METHODS as readonly string[]).includes(r.method)) return null;
  return gocCleanReading(r);
}

/** The parts a seal covers for one reading, in order. */
export function gocReadingSealParts(r: ReadingLike): string[] {
  const c = gocCleanReading(r);
  return ['readAloud', c.method, c.readerName, c.readerRelation, c.language, c.witnessName, c.witnessRelation, c.at];
}

export type GocReadingProblem = 'METHOD' | 'NOT_HEARD' | 'READER_NAME' | 'READER_RELATION' | 'READER_IS_SIGNER'
  | 'WITNESS_NAME' | 'WITNESS_RELATION' | 'WITNESS_IS_SIGNER' | 'WITNESS_IS_READER';

/** Everything still missing before the reading counts (Kotlin ConsentReadingRules.problems). */
export function gocReadingProblems(reading: ReadingLike | null | undefined, signerName: string, askWitness: boolean, heard = true): GocReadingProblem[] {
  const out: GocReadingProblem[] = [];
  if (!reading) return ['METHOD'];
  const r = gocCleanReading(reading);
  if (!(GOC_READ_METHODS as readonly string[]).includes(r.method)) return ['METHOD'];
  const signer = normaliseName(signerName);
  if (r.method === 'PHONE' && !heard) out.push('NOT_HEARD');
  if (r.method === 'PERSON') {
    if (!r.readerName) out.push('READER_NAME');
    if (!r.readerRelation) out.push('READER_RELATION');
    if (signer && normaliseName(r.readerName) === signer) out.push('READER_IS_SIGNER');
  }
  if (askWitness && gocReadingNeedsWitness(r.method)) {
    if (!r.witnessName) out.push('WITNESS_NAME');
    if (!r.witnessRelation) out.push('WITNESS_RELATION');
    const witness = normaliseName(r.witnessName);
    if (witness && witness === signer) out.push('WITNESS_IS_SIGNER');
    if (witness && r.method === 'PERSON' && witness === normaliseName(r.readerName)) out.push('WITNESS_IS_READER');
  }
  return out;
}

const GOC_LANGUAGE_LABELS: Record<string, string> = {
  en: 'English', hi: 'हिन्दी', te: 'తెలుగు', ur: 'اردو', kn: 'ಕನ್ನಡ', ta: 'தமிழ்', ml: 'മലയാളം', mr: 'मराठी', bn: 'বাংলা', gu: 'ગુજરાતી', pa: 'ਪੰਜਾਬੀ',
};

/** A stored English relationship in the screen language; a typed one as written (Kotlin ConsentReadingRules.relationLabel). */
export function gocRelationLabel(t: (en: string, hi: string) => string, relation: string): string {
  switch (relation) {
    case 'Son': return t('Son', 'बेटा'); case 'Daughter': return t('Daughter', 'बेटी'); case 'Husband': return t('Husband', 'पति'); case 'Wife': return t('Wife', 'पत्नी');
    case 'Brother': return t('Brother', 'भाई'); case 'Sister': return t('Sister', 'बहन'); case 'Grandchild': return t('Grandchild', 'पोता / पोती'); case 'Friend': return t('Friend', 'दोस्त');
    case 'Neighbour': return t('Neighbour', 'पड़ोसी'); case 'Nurse': return t('Nurse', 'नर्स'); case 'Doctor': return t('Doctor', 'डॉक्टर'); case 'Social worker': return t('Social worker', 'सामाजिक कार्यकर्ता');
    case 'Volunteer': return t('Volunteer', 'स्वयंसेवक'); case 'Self': return t('Self', 'ख़ुद');
    default: return relation;
  }
}

/** "Read aloud by Ravi (Son) · witnessed by Mohan (Neighbour)", the same line as Android ConsentReadingRules.summary. */
export function gocReadingSummary(reading: ReadingLike, t: (en: string, hi: string) => string): string {
  const c = gocCleanReading(reading);
  const who = (name: string, relation: string) => name + (relation.trim() ? ` (${relation})` : '');
  const head = c.method === 'SELF' ? t('Read it themselves', 'ख़ुद पढ़ा')
    : c.method === 'PHONE' ? t('Read aloud by the phone', 'फ़ोन ने पढ़कर सुनाया') + (GOC_LANGUAGE_LABELS[c.language] ? ' · ' + GOC_LANGUAGE_LABELS[c.language] : '')
      : t('Read aloud by', 'पढ़कर सुनाया:') + ' ' + who(c.readerName, gocRelationLabel(t, c.readerRelation));
  const witness = c.witnessName.trim() ? ' · ' + t('witnessed by', 'गवाह:') + ' ' + who(c.witnessName, gocRelationLabel(t, c.witnessRelation)) : '';
  return head + witness;
}

// ---------------------------------------------------------------------------
// Seal

const part = (value: string) => `${value.length}:${value};`;
const flag = (value: boolean | null) => value === null ? '-' : value ? '1' : '0';

export function gocSealInput(record: Pick<GocSignOffRecord, 'noteId' | 'noteSha256' | 'signatures'>): string {
  let out = part(GOC_SIGNOFF_SCHEME) + part(record.noteId) + part(record.noteSha256);
  for (const s of record.signatures) {
    out += part(s.role) + part(s.name) + part(s.registrationNo ?? '') + part(s.relation ?? '') + part(s.consent ? '1' : '0')
      + part(flag(s.canRead)) + part(s.method) + part(s.imageSha256) + part(String(s.strokeCount)) + part(s.signedAt) + part(s.noteSha256);
    // Only when a reading is present, so seals made without one (older web and phone seals) stay the same.
    const reading = gocDecodeReading(s.readAloud);
    if (reading) for (const p of gocReadingSealParts(reading)) out += part(p);
  }
  return out;
}

export function gocSealSha256(record: Pick<GocSignOffRecord, 'noteId' | 'noteSha256' | 'signatures'>): Promise<string> {
  return gocSha256Hex(gocSealInput(record));
}

// ---------------------------------------------------------------------------
// Lifecycle

const newId = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `goc-${Date.now()}-${Math.random().toString(36).slice(2)}`);

export async function gocStartSignOff(noteId: string, noteText: string, now: Date = new Date(), id: string = newId()): Promise<GocSignOffRecord> {
  return {
    scheme: GOC_SIGNOFF_SCHEME, id, noteId, noteSha256: await gocNoteSha256(noteText), status: 'OPEN', createdAt: now.toISOString(),
    signatures: [], lockedAt: null, sealSha256: null, invalidatedAt: null, invalidatedReason: null,
  };
}

/** A witness is needed when asked for, when a thumb impression is used, or when the patient or family signer cannot read. */
export function gocWitnessRequired(record: Pick<GocSignOffRecord, 'signatures'>, steps: readonly GocSignerStep[] = GOC_DEFAULT_STEPS): boolean {
  return steps.includes('WITNESS') || record.signatures.some((s) => (s.role === 'PATIENT' || s.role === 'FAMILY') && (s.method === 'THUMB' || s.canRead === false));
}

/** The steps this sign-off needs, in order. The doctor always signs first. */
export function gocRequiredSteps(record: Pick<GocSignOffRecord, 'signatures'>, steps: readonly GocSignerStep[] = GOC_DEFAULT_STEPS): GocSignerStep[] {
  const out: GocSignerStep[] = ['DOCTOR'];
  if (steps.includes('PATIENT_OR_FAMILY')) out.push('PATIENT_OR_FAMILY');
  if (gocWitnessRequired(record, steps)) out.push('WITNESS');
  return out;
}

const stepOf = (role: GocSignatureRole): GocSignerStep => role === 'DOCTOR' ? 'DOCTOR' : role === 'WITNESS' ? 'WITNESS' : 'PATIENT_OR_FAMILY';

/** The next step to sign, 'LOCK' when everyone has signed, or null once locked or invalidated. */
export function gocNextStep(record: GocSignOffRecord, steps: readonly GocSignerStep[] = GOC_DEFAULT_STEPS): GocSignerStep | 'LOCK' | null {
  if (record.status !== 'OPEN') return null;
  const done = new Set(record.signatures.map((s) => stepOf(s.role)));
  return gocRequiredSteps(record, steps).find((step) => !done.has(step)) ?? 'LOCK';
}

function checkOpen(record: GocSignOffRecord, currentNoteSha: string) {
  if (record.status === 'LOCKED') throw new GocSignOffError('LOCKED');
  if (record.status === 'INVALIDATED') throw new GocSignOffError('INVALIDATED');
  if (currentNoteSha !== record.noteSha256) throw new GocSignOffError('NOTE_CHANGED');
}

/** Names of the people who read the note aloud to an earlier signer (method PERSON). */
export function gocReadersOf(signatures: readonly GocSignature[]): string[] {
  return signatures.flatMap((s) => s.readAloud?.method === 'PERSON' && (s.readAloud.readerName ?? '').trim() ? [s.readAloud.readerName] : []);
}

/** Adds one signature. Refused when the note text is not the one this sign-off started on, or anything is missing. */
export async function gocAddSignature(record: GocSignOffRecord, noteText: string, input: GocSignatureInput, steps: readonly GocSignerStep[] = GOC_DEFAULT_STEPS, now: Date = new Date()): Promise<GocSignOffRecord> {
  checkOpen(record, await gocNoteSha256(noteText));
  if (gocNextStep(record, steps) !== stepOf(input.role)) throw new GocSignOffError('WRONG_ORDER');
  const name = (input.name ?? '').trim().slice(0, GOC_NAME_MAX);
  if (!name) throw new GocSignOffError('NAME_BLANK');
  const registrationNo = input.role === 'DOCTOR' ? (input.registrationNo ?? '').trim() : null;
  if (input.role === 'DOCTOR' && !registrationNo) throw new GocSignOffError('REGISTRATION_BLANK');
  let relation: string | null = (input.relation ?? '').trim().slice(0, GOC_NAME_MAX) || null;
  if (input.role === 'PATIENT') relation = 'Self';
  if (input.role === 'DOCTOR') relation = null;
  if (input.role === 'FAMILY' && !relation) throw new GocSignOffError('RELATION_BLANK');
  const signer = input.role === 'PATIENT' || input.role === 'FAMILY';
  // The read-aloud step, when recorded, must be complete; a witness signs separately, so none is asked for here.
  const reading = signer && input.readAloud ? gocCleanReading(input.readAloud) : null;
  if (reading && gocReadingProblems(reading, name, false).length) throw new GocSignOffError('READ_ALOUD_MISSING');
  const canRead = reading ? gocReadingCanRead(reading) : input.canRead;
  if (signer && typeof canRead !== 'boolean') throw new GocSignOffError('CAN_READ_MISSING');
  // The witness saw it being read, so it is someone other than the person who read it aloud.
  if (input.role === 'WITNESS' && gocReadersOf(record.signatures).some((reader) => normaliseName(reader) === normaliseName(name))) throw new GocSignOffError('WITNESS_IS_READER');
  if (input.role !== 'DOCTOR' && input.consent !== true) throw new GocSignOffError('CONSENT_MISSING');
  const bytes = gocDataUrlBytes(input.imageDataUrl);
  if (!bytes || !(input.strokeCount >= 1)) throw new GocSignOffError('NO_SIGNATURE');
  const signature: GocSignature = {
    role: input.role, name, registrationNo, relation, consent: input.role === 'DOCTOR' ? true : input.consent,
    canRead: signer ? canRead as boolean : null, method: input.method === 'THUMB' ? 'THUMB' : 'DRAWN',
    imageDataUrl: input.imageDataUrl, imageSha256: await gocSha256Hex(bytes), strokeCount: Math.floor(input.strokeCount),
    signedAt: now.toISOString(), noteSha256: record.noteSha256, readAloud: reading,
  };
  return { ...record, signatures: [...record.signatures, signature] };
}

/** Locks the note version once every required person has signed. The seal hash is computed here. */
export async function gocLock(record: GocSignOffRecord, noteText: string, steps: readonly GocSignerStep[] = GOC_DEFAULT_STEPS, now: Date = new Date()): Promise<GocSignOffRecord> {
  checkOpen(record, await gocNoteSha256(noteText));
  if (gocNextStep(record, steps) !== 'LOCK') throw new GocSignOffError('NOT_READY');
  return { ...record, status: 'LOCKED', lockedAt: now.toISOString(), sealSha256: await gocSealSha256(record) };
}

export const GOC_INVALIDATED_REASON = 'The note was changed after it was signed. All signatures on the earlier text are no longer valid. Read the new note together and sign again.';

export type GocReconcile = { record: GocSignOffRecord; invalidated: GocSignOffRecord | null };

/**
 * Call whenever the note text may have changed. Same text: nothing happens. Changed text with no signatures yet: the
 * sign-off simply moves to the new text. Changed text after any signature (or after locking): the old record is marked
 * INVALIDATED, with every signature on it, and a fresh OPEN record is started for the new text.
 */
export async function gocReconcile(record: GocSignOffRecord, noteText: string, now: Date = new Date()): Promise<GocReconcile> {
  const sha = await gocNoteSha256(noteText);
  if (sha === record.noteSha256 || record.status === 'INVALIDATED') {
    if (record.status === 'INVALIDATED' && sha !== record.noteSha256) return { record: await gocStartSignOff(record.noteId, noteText, now), invalidated: null };
    return { record, invalidated: null };
  }
  if (record.status === 'OPEN' && record.signatures.length === 0) return { record: { ...record, noteSha256: sha }, invalidated: null };
  const invalidated: GocSignOffRecord = { ...record, status: 'INVALIDATED', invalidatedAt: now.toISOString(), invalidatedReason: GOC_INVALIDATED_REASON };
  return { record: await gocStartSignOff(record.noteId, noteText, now), invalidated };
}

export const GOC_RESTARTED_REASON = 'The signatures were started again before the note was locked.';

/** The doctor starts the signatures again (a wrong name, say). The open record is kept as INVALIDATED; a locked one cannot be discarded. */
export async function gocRestart(record: GocSignOffRecord, noteText: string, now: Date = new Date()): Promise<GocReconcile> {
  if (record.status === 'LOCKED') throw new GocSignOffError('LOCKED');
  const fresh = await gocStartSignOff(record.noteId, noteText, now);
  if (record.status === 'INVALIDATED' || record.signatures.length === 0) return { record: fresh, invalidated: null };
  return { record: fresh, invalidated: { ...record, status: 'INVALIDATED', invalidatedAt: now.toISOString(), invalidatedReason: GOC_RESTARTED_REASON } };
}

export type GocVerifyProblem = 'NOTE_CHANGED' | 'SIGNATURE_ON_OTHER_TEXT' | 'IMAGE_CHANGED' | 'SEAL_MISSING' | 'SEAL_CHANGED' | 'INVALIDATED';

/** Checks a stored sign-off against the note text shown now. ok only for a LOCKED record whose seal and images still match. */
export async function gocVerify(record: GocSignOffRecord, noteText: string): Promise<{ ok: boolean; problems: GocVerifyProblem[] }> {
  const problems: GocVerifyProblem[] = [];
  if (record.status === 'INVALIDATED') problems.push('INVALIDATED');
  if (await gocNoteSha256(noteText) !== record.noteSha256) problems.push('NOTE_CHANGED');
  for (const s of record.signatures) {
    if (s.noteSha256 !== record.noteSha256) problems.push('SIGNATURE_ON_OTHER_TEXT');
    const bytes = gocDataUrlBytes(s.imageDataUrl);
    if (!bytes || await gocSha256Hex(bytes) !== s.imageSha256) problems.push('IMAGE_CHANGED');
  }
  if (!record.sealSha256) problems.push('SEAL_MISSING');
  else if (await gocSealSha256(record) !== record.sealSha256) problems.push('SEAL_CHANGED');
  const unique = [...new Set(problems)];
  return { ok: record.status === 'LOCKED' && unique.length === 0, problems: unique };
}

/** Accepts only a well-formed record (for reading back from storage). */
export function isGocSignOffRecord(value: unknown): value is GocSignOffRecord {
  const r = value as GocSignOffRecord;
  return Boolean(r) && typeof r === 'object' && r.scheme === GOC_SIGNOFF_SCHEME && typeof r.id === 'string' && typeof r.noteId === 'string'
    && typeof r.noteSha256 === 'string' && ['OPEN', 'LOCKED', 'INVALIDATED'].includes(r.status) && Array.isArray(r.signatures);
}

// ---------------------------------------------------------------------------
// 1-bit black and white PNG (colour type 0, bit depth 1). Same encoder as GocMonoPng in CareSignaturePad.kt.

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; table[n] = c >>> 0; }
  return table;
})();

export function gocCrc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (const b of bytes) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** Rows of packed bits, each row starting with filter byte 0. 1 = white, 0 = black. `black[i]` is pixel i, row by row. */
export function gocMonoRows(width: number, height: number, black: ArrayLike<number | boolean>): Uint8Array {
  const stride = Math.ceil(width / 8) + 1;
  const out = new Uint8Array(stride * height);
  for (let y = 0; y < height; y++) {
    const row = y * stride;
    for (let x = 0; x < width; x++) if (!black[y * width + x]) out[row + 1 + (x >> 3)] |= 0x80 >> (x & 7);
  }
  return out;
}

async function zlib(data: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([data as BlobPart]).stream().pipeThrough(new CompressionStream('deflate'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function chunk(type: string, data: Uint8Array): Uint8Array {
  const out = new Uint8Array(12 + data.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, data.length);
  for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
  out.set(data, 8);
  view.setUint32(8 + data.length, gocCrc32(out.subarray(4, 8 + data.length)));
  return out;
}

/** Encodes black/white pixels as a 1-bit PNG. Needs CompressionStream (browsers since 2023, Node 18+). */
export async function gocEncodeMonoPng(width: number, height: number, black: ArrayLike<number | boolean>): Promise<Uint8Array> {
  if (!(width > 0 && height > 0) || black.length < width * height) throw new Error('Bad image size.');
  const header = new Uint8Array(13);
  const view = new DataView(header.buffer);
  view.setUint32(0, width); view.setUint32(4, height);
  header[8] = 1; header[9] = 0; header[10] = 0; header[11] = 0; header[12] = 0;
  const parts = [new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header), chunk('IDAT', await zlib(gocMonoRows(width, height, black))), chunk('IEND', new Uint8Array(0))];
  const out = new Uint8Array(parts.reduce((sum, p) => sum + p.length, 0));
  let at = 0;
  for (const p of parts) { out.set(p, at); at += p.length; }
  return out;
}

/** Export size for a pad: at most 600 px wide, keeping the aspect ratio. */
export function gocExportSize(padWidth: number, padHeight: number): { width: number; height: number } {
  const width = Math.max(1, Math.min(GOC_PNG_MAX_WIDTH, Math.round(padWidth)));
  return { width, height: Math.max(1, Math.round(width * (padHeight / Math.max(1, padWidth)))) };
}
