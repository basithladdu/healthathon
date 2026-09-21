export type DraftFieldKey = 'priorities' | 'participants' | 'topics' | 'openQuestions' | 'followUp';
export type DraftFieldStatus = 'ready' | 'not-stated' | 'clarify';

export type SummaryRelease = {
  signature?: { name: string; signedAt: string; method: 'typed' };
  number: number;
  patientId: string;
  fields: Readonly<Record<DraftFieldKey, string>> | null;
  statuses: Readonly<Record<DraftFieldKey, DraftFieldStatus>> | null;
  source: string | null;
  excerpts: Readonly<Record<DraftFieldKey, string>> | null;
  coverage: Readonly<Record<string, string>> | null;
  authorisation: string | null;
  physician: string;
  releasedAt: string;
};

export type SummaryState = {
  patientId: string;
  releases: readonly SummaryRelease[];
  draftSource: string;
  draftExcerpts: Record<DraftFieldKey, string>;
  draftIsNewConversation: boolean;
  draftPrepared: boolean;
  draftFields: Record<DraftFieldKey, string>;
  draftStatuses: Record<DraftFieldKey, DraftFieldStatus>;
  verificationChecks: { source: boolean; ambiguity: boolean; inference: boolean; reviewDate: boolean };
  authorisation: string;
  attested: boolean;
  versionPublished: boolean;
  publishedFields: Readonly<Record<DraftFieldKey, string>> | null;
};

export type SummaryReleaseOptions = {
  signature?: SummaryRelease['signature'];
  physician: string;
  releasedAt: string;
  coverage?: Readonly<Record<string, string>> | null;
};

export type SummaryRevisionPatch = {
  draftFields?: Partial<Record<DraftFieldKey, string>>;
  draftStatuses?: Partial<Record<DraftFieldKey, DraftFieldStatus>>;
  draftSource?: string;
  draftExcerpts?: Partial<Record<DraftFieldKey, string>>;
};

const FIELD_KEYS: readonly DraftFieldKey[] = [
  'priorities',
  'participants',
  'topics',
  'openQuestions',
  'followUp',
];
const NOT_STATED_TEXT = 'Not stated in this conversation.';
const PENDING_AUTHORISATION = 'Pending patient review';
const PERMITTED_AUTHORISATIONS = new Set([
  'Authorised by Patient & Surrogate',
  'Authorised by Designated Healthcare Proxy',
  'Authorised verbally (witnessed by care team)',
]);

function blankFields(): Record<DraftFieldKey, string> {
  return Object.fromEntries(FIELD_KEYS.map((key) => [key, ''])) as Record<DraftFieldKey, string>;
}

function blankStatuses(): Record<DraftFieldKey, DraftFieldStatus> {
  return Object.fromEntries(FIELD_KEYS.map((key) => [key, 'clarify'])) as Record<DraftFieldKey, DraftFieldStatus>;
}

function blankExcerpts(): Record<DraftFieldKey, string> {
  return Object.fromEntries(FIELD_KEYS.map((key) => [key, ''])) as Record<DraftFieldKey, string>;
}

function sameDraftRecord<T extends string>(a: Record<DraftFieldKey, T>, b: Record<DraftFieldKey, T>): boolean {
  return FIELD_KEYS.every((key) => a[key] === b[key]);
}

function resetReview<T extends SummaryState>(state: T): T {
  return {
    ...state,
    verificationChecks: { source: false, ambiguity: false, inference: false, reviewDate: false },
    authorisation: PENDING_AUTHORISATION,
    attested: false,
  } as T;
}

export function invalidateSummaryReview<T extends SummaryState>(state: T): T {
  if (state.versionPublished) return state;
  return resetReview(state);
}

function patientReleases(state: SummaryState): SummaryRelease[] {
  return state.releases.filter((release) => release.patientId === state.patientId);
}

export function latestSummaryRelease(state: SummaryState): SummaryRelease | null {
  return patientReleases(state).reduce<SummaryRelease | null>(
    (latest, release) => (!latest || release.number > latest.number ? release : latest),
    null,
  );
}

export function summaryReleaseByNumber(state: SummaryState, number: number): SummaryRelease | null {
  return patientReleases(state).find((release) => release.number === number) ?? null;
}

export function nextSummaryVersion(state: SummaryState): number {
  const highest = patientReleases(state).reduce(
    (max, release) => (Number.isInteger(release.number) && release.number > max ? release.number : max),
    0,
  );
  return highest + 1;
}

export function beginSummaryRevision<T extends SummaryState>(state: T): T {
  if (state.draftPrepared && !state.versionPublished) return state;
  return {
    ...state,
    draftPrepared: true,
    draftFields: blankFields(),
    draftStatuses: blankStatuses(),
    draftSource: '',
    draftExcerpts: blankExcerpts(),
    draftIsNewConversation: true,
    verificationChecks: { source: false, ambiguity: false, inference: false, reviewDate: false },
    authorisation: PENDING_AUTHORISATION,
    attested: false,
    versionPublished: false,
  } as T;
}

export function reviseSummary<T extends SummaryState>(state: T, patch: SummaryRevisionPatch = {}): T {
  if (state.versionPublished) return state;

  const fields = { ...state.draftFields };
  const statuses = { ...state.draftStatuses };
  const excerpts = { ...state.draftExcerpts };

  for (const key of FIELD_KEYS) {
    if (patch.draftFields && Object.prototype.hasOwnProperty.call(patch.draftFields, key)) {
      fields[key] = patch.draftFields[key] ?? '';
    }
    if (patch.draftStatuses && Object.prototype.hasOwnProperty.call(patch.draftStatuses, key)) {
      const status = patch.draftStatuses[key];
      if (status) statuses[key] = status;
    }
    if (patch.draftExcerpts && Object.prototype.hasOwnProperty.call(patch.draftExcerpts, key)) {
      excerpts[key] = patch.draftExcerpts[key] ?? '';
    }
  }

  const source = patch.draftSource ?? state.draftSource;
  const sourceChanged = source !== state.draftSource;
  const textChanged = !sameDraftRecord(state.draftFields, fields);

  for (const key of FIELD_KEYS) {
    if (fields[key] !== state.draftFields[key]) statuses[key] = 'clarify';
  }
  if (sourceChanged) {
    for (const key of FIELD_KEYS) {
      if (statuses[key] === 'ready') statuses[key] = 'clarify';
      excerpts[key] = '';
    }
  }
  for (const key of FIELD_KEYS) {
    if (statuses[key] === 'not-stated') {
      fields[key] = NOT_STATED_TEXT;
      excerpts[key] = '';
    }
  }

  const statusesChanged = !sameDraftRecord(state.draftStatuses, statuses);
  const excerptsChanged = !sameDraftRecord(state.draftExcerpts, excerpts);
  const fieldsChanged = !sameDraftRecord(state.draftFields, fields);
  if (!sourceChanged && !textChanged && !statusesChanged && !excerptsChanged && !fieldsChanged) return state;

  return {
    ...resetReview(state),
    draftFields: fields,
    draftStatuses: statuses,
    draftSource: source,
    draftExcerpts: excerpts,
  } as T;
}

export function setSummaryFieldStatus<T extends SummaryState>(
  state: T,
  key: DraftFieldKey,
  status: DraftFieldStatus,
): T {
  return reviseSummary(state, { draftStatuses: { [key]: status } });
}

function releaseIsValid(state: SummaryState): boolean {
  if (state.versionPublished || !state.draftPrepared || !state.attested) return false;
  if (!PERMITTED_AUTHORISATIONS.has(state.authorisation)) return false;
  if (!state.draftSource.trim()) return false;
  if (!Object.values(state.verificationChecks).every(Boolean)) return false;

  for (const key of FIELD_KEYS) {
    const value = state.draftFields[key];
    const status = state.draftStatuses[key];
    if (!value.trim()) return false;
    if (status === 'not-stated') {
      if (value !== NOT_STATED_TEXT) return false;
      continue;
    }
    if (status !== 'ready') return false;
    const excerpt = state.draftExcerpts[key];
    if (!excerpt.trim() || !state.draftSource.includes(excerpt)) return false;
  }
  return true;
}

export function isSummaryReadyToRelease(state: SummaryState): boolean {
  return releaseIsValid(state);
}

function freezeRecord<K extends string, V>(record: Record<K, V>): Readonly<Record<K, V>> {
  return Object.freeze({ ...record }) as Readonly<Record<K, V>>;
}

export function releaseSummary<T extends SummaryState>(
  state: T,
  options: SummaryReleaseOptions = { physician: '', releasedAt: '' },
): T {
  if (
    !releaseIsValid(state) ||
    typeof options.physician !== 'string' ||
    !options.physician.trim() ||
    typeof options.releasedAt !== 'string' ||
    !options.releasedAt.trim() ||
    !Number.isFinite(Date.parse(options.releasedAt))
  ) return state;

  const fields = freezeRecord(state.draftFields);
  const statuses = freezeRecord(state.draftStatuses);
  const excerpts = freezeRecord(state.draftExcerpts);
  const coverage = options.coverage == null ? null : freezeRecord({ ...options.coverage });
  const release = Object.freeze({
    ...(options.signature ? { signature: Object.freeze({ ...options.signature }) } : {}),
    number: nextSummaryVersion(state),
    patientId: state.patientId,
    fields,
    statuses,
    source: state.draftSource,
    excerpts,
    coverage,
    authorisation: state.authorisation ?? null,
    physician: options.physician,
    releasedAt: options.releasedAt,
  }) as SummaryRelease;

  return {
    ...state,
    versionPublished: true,
    publishedFields: fields,
    releases: Object.freeze([...state.releases, release]),
  } as T;
}
