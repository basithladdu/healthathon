/** Same account and row-level permissions as the Android app. No service key. */
export const CARE_SUPABASE_URL = 'https://cghlkqeqiokjqdionrkc.supabase.co';
export const CARE_PUBLISHABLE_KEY = 'sb_publishable_43mvHQiHJXVGwS71SX0GVA_GWxXBVOC';
export type SharedSession = { userId: string; email: string; access: string; refresh: string; expires: number };
export type SharedPerson = { id: string; owner_id: string; name: string; created_ms: number };
export type SharedPermissions = Record<'careNote' | 'calendar' | 'reports' | 'costs', boolean>;
export type SharedMember = { person_id: string; user_id: string; can_edit: boolean; role?: 'family' | 'clinician'; permissions?: SharedPermissions; expires_at?: string | null };
export type SharedNoteData = { version: number; personName: string; source: string; priorities: string; participants: string; topics: string; questions: string; nextSteps: string; doctor: string; published: number; contentHash: string; reviewStatus?: string; reviewerVerification?: string; reviewerId?: string };
export type SharedRecord = { id: string; kind: 'note' | 'ack' | 'entry'; person_id: string; revision: number; updated_at: string; data: Record<string, unknown> };
export type SharedDraftData = { source: string; priorities: string; participants: string; topics: string; questions: string; nextSteps: string };
export type SharedDraft = { id: string; person_id: string; author_id: string; data: SharedDraftData; revision: number; status: 'draft' | 'submitted' | 'reviewed'; reviewed_note_id: string | null; updated_at: string };
export type ClinicianRequest = { user_id: string; name: string; council: string; registration: string; status: 'pending' | 'approved' | 'rejected' | 'suspended'; review_note: string; revision: number };
export type SharedSnapshot = { people: SharedPerson[]; members: SharedMember[]; records: SharedRecord[]; drafts: SharedDraft[]; clinician: ClinicianRequest | null; workflowReady: boolean };
export type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
export type Fetcher = typeof fetch;
const SESSION_KEY = 'sahara-shared-session-v1';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const isSharedId = (value: unknown): value is string => typeof value === 'string' && UUID.test(value);
export function sharedNote(row: SharedRecord): SharedNoteData | null {
  const d = row.data;
  if (row.kind !== 'note' || !isSharedId(row.id) || !isSharedId(row.person_id) || !Number.isSafeInteger(d.version) || Number(d.version) < 1
    || typeof d.contentHash !== 'string' || !/^[a-f0-9]{64}$/.test(d.contentHash)
    || !['source','personName','doctor','priorities','participants','topics','questions','nextSteps'].every((key) => typeof d[key] === 'string')
    || !Number.isSafeInteger(d.published)) return null;
  return d as SharedNoteData;
}
/** An exact QR request never silently opens another person or a newer/older note. */
export function selectSharedNote(records: SharedRecord[], person: string, version?: number, id?: string): SharedRecord | undefined {
  if (!isSharedId(person) || (version !== undefined && (!Number.isSafeInteger(version) || version < 1)) || (id !== undefined && !isSharedId(id))) return;
  return records.filter((row) => row.person_id === person && sharedNote(row) && (version === undefined || row.data.version === version) && (id === undefined || row.id === id))
    .sort((a, b) => Number(b.data.version) - Number(a.data.version))[0];
}
export function sharedNoteUrl(origin: string, note: SharedRecord): string {
  const data = sharedNote(note); if (!data) throw new Error('This note cannot be linked.');
  const target = new URL('/shared-care', origin);
  target.searchParams.set('person', note.person_id); target.searchParams.set('version', String(data.version)); target.searchParams.set('note', note.id);
  return target.toString();
}
export function emptySharedDraft(): SharedDraftData { return { source: '', priorities: '', participants: '', topics: '', questions: '', nextSteps: '' }; }
export function validateSharedDraft(data: SharedDraftData): void {
  if (!data.source.trim() || data.source.length > 12000 || Object.entries(data).some(([key, value]) => typeof value !== 'string' || (key !== 'source' && value.length > 4000))) throw new Error('Add the original conversation (up to 12,000 characters) and keep each section under 4,000 characters.');
}
export class CareSharedClient {
  private session: SharedSession | null = null;
  private refreshing: Promise<string> | null = null;
  private storage?: SessionStorage;
  private transport: Fetcher;
  constructor(storage?: SessionStorage, transport: Fetcher = fetch) {
    // Browser fetch requires its Window/global receiver. Node's fetch does not
    // expose that mistake, so keeping it as an unbound instance method breaks
    // real sign-in before any network request is sent.
    this.storage = storage; this.transport = transport.bind(globalThis);
    try {
      const saved = JSON.parse(storage?.getItem(SESSION_KEY) ?? 'null') as SharedSession | null;
      if (saved && isSharedId(saved.userId) && typeof saved.access === 'string' && typeof saved.refresh === 'string' && Number.isFinite(saved.expires)) this.session = saved;
    } catch { try { storage?.removeItem(SESSION_KEY); } catch { /* Memory-only account state remains usable. */ } }
  }
  currentSession() { return this.session; }
  private remember(value: Record<string, unknown>) {
    const user = value.user as { id?: string; email?: string } | undefined;
    if (!isSharedId(user?.id) || typeof value.access_token !== 'string' || typeof value.refresh_token !== 'string') throw new Error('Sign-in did not return a valid account.');
    this.session = { userId: user.id, email: user.email ?? '', access: value.access_token, refresh: value.refresh_token, expires: Number(value.expires_at) || Date.now() / 1000 + Number(value.expires_in) };
    this.storage?.setItem(SESSION_KEY, JSON.stringify(this.session));
  }
  async signIn(email: string, password: string) {
    const value = await this.request('/auth/v1/token?grant_type=password', 'POST', { email: email.trim(), password }, false);
    this.remember(value); await this.verifyAccount(); return this.session!;
  }
  async signUp(email: string, password: string) {
    if (password.length < 10) throw new Error('Use at least 10 characters for your password.');
    // Existing project email confirmation settings/callbacks are left unchanged.
    const value = await this.request('/auth/v1/signup', 'POST', { email: email.trim(), password }, false);
    if (value.access_token) { this.remember(value); await this.verifyAccount(); return true; } return false;
  }
  async signOut() {
    try { await this.request('/auth/v1/logout', 'POST', {}); }
    finally { this.session = null; this.storage?.removeItem(SESSION_KEY); }
  }
  async verifyAccount() {
    const expectedUser = this.session?.userId;
    const value = await this.request('/auth/v1/user');
    if (this.session?.userId !== expectedUser) throw new Error('Your account changed. Sign in again.');
    if (!this.session || value.id !== this.session.userId || value.is_anonymous === true) { this.session = null; this.storage?.removeItem(SESSION_KEY); throw new Error('Sign in with your confirmed Sahara account.'); }
    return value;
  }
  async token(): Promise<string> {
    const saved = this.session; if (!saved) throw new Error('Sign in to continue.');
    if (saved.expires > Date.now() / 1000 + 60) return saved.access;
    if (!this.refreshing) this.refreshing = (async () => {
      const value = await this.request('/auth/v1/token?grant_type=refresh_token', 'POST', { refresh_token: saved.refresh }, false);
      // Sign-out during an outstanding refresh must never recreate a session.
      if (this.session?.userId !== saved.userId || this.session.refresh !== saved.refresh) throw new Error('Your account changed. Sign in again.');
      this.remember(value); return this.session!.access;
    })().finally(() => { this.refreshing = null; });
    return this.refreshing;
  }
  async request<T = Record<string, unknown>>(path: string, method = 'GET', body?: unknown, authenticated = true): Promise<T> {
    const access = authenticated ? await this.token() : undefined;
    let response: Response;
    try { response = await this.transport(CARE_SUPABASE_URL + path, { method, headers: { apikey: CARE_PUBLISHABLE_KEY, ...(access ? { Authorization: `Bearer ${access}` } : {}), 'Content-Type': 'application/json', Prefer: 'return=representation' }, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'omit', redirect: 'error', cache: 'no-store', signal: AbortSignal.timeout(20000) }); }
    catch { throw new Error('Could not reach your care records. Your draft is still here.'); }
    const value = await response.json().catch(() => null) as Record<string, unknown> | null;
    if (!response.ok) {
      if (response.status === 401) throw new Error('Sign in again to continue.');
      if (response.status === 403 || value?.code === '42501') throw new Error('Your account does not have permission for this action.');
      if (value?.code === '40001' || value?.code === '23505') throw new Error('This record changed. Refresh and compare it before saving. Your draft is still here.');
      if (response.status === 429) throw new Error('Please wait a moment before trying again.');
      if (value?.code === 'PGRST202' || value?.code === 'PGRST205' || value?.code === '42P01') throw new Error('The shared review service is not ready yet. Your existing records and draft are unchanged.');
      throw new Error(value?.code === 'P0001' && typeof value.message === 'string' ? value.message : path.startsWith('/auth/') ? 'Sign-in did not finish. Check your email, password and confirmation status.' : 'Could not save. Your draft is still here.');
    }
    return value as T;
  }
  rpc<T = Record<string, unknown>>(name: string, body: Record<string, unknown>) { if (!/^[a-z_]+$/.test(name)) throw new Error('Invalid action.'); return this.request<T>(`/rest/v1/rpc/${name}`, 'POST', body); }
  private async rows<T>(table: string, query: string): Promise<T[]> {
    const all: T[] = []; let offset = 0;
    for (;;) { const page = await this.request<unknown>(`/rest/v1/${table}?${query}&limit=500&offset=${offset}`); if (!Array.isArray(page)) throw new Error('The saved records could not be read.'); all.push(...page as T[]); if (page.length < 500) return all; offset += page.length; }
  }
  async load(): Promise<SharedSnapshot> {
    await this.verifyAccount(); const user = this.session!.userId;
    const [people, members, records] = await Promise.all([this.rows<SharedPerson>('healthathon_people', 'order=created_ms.asc'), this.rows<SharedMember>('healthathon_members', `user_id=eq.${user}`), this.rows<SharedRecord>('healthathon_records', 'order=updated_at.asc')]);
    let drafts: SharedDraft[] = [], clinician: ClinicianRequest | null = null, workflowReady = true;
    try { const values = await Promise.all([this.rows<SharedDraft>('healthathon_care_drafts', 'order=updated_at.desc'), this.rows<ClinicianRequest>('healthathon_clinician_requests', `user_id=eq.${user}`)]); drafts = values[0]; clinician = values[1][0] ?? null; }
    catch (error) { if (!(error instanceof Error && error.message.includes('not ready yet'))) throw error; workflowReady = false; }
    if (this.session?.userId !== user) throw new Error('Your account changed. Sign in again.');
    return { people, members, records, drafts, clinician, workflowReady };
  }
  async createPerson(name: string) {
    if (!name.trim() || name.trim().length > 100) throw new Error('Add a name, up to 100 characters.');
    const person = { id: crypto.randomUUID(), name: name.trim(), created_ms: Date.now() };
    const rows = await this.request('/rest/v1/healthathon_people', 'POST', person);
    if (!Array.isArray(rows) || rows[0]?.id !== person.id) throw new Error('The saved person could not be confirmed. Refresh before trying again.'); return rows[0] as SharedPerson;
  }
  async saveDraft(draft: SharedDraftData, person: string, id: string, revision: number, submit: boolean): Promise<SharedDraft> {
    validateSharedDraft(draft); if (!isSharedId(id) || !isSharedId(person)) throw new Error('Choose the correct person.');
    const saved = await this.rpc<SharedDraft>('healthathon_save_draft', { p_id: id, p_person: person, p_data: draft, p_revision: revision, p_submit: submit });
    if (saved?.id !== id || saved.person_id !== person || saved.author_id !== this.session?.userId || !Number.isSafeInteger(saved.revision) || saved.revision !== revision + 1) throw new Error('The saved draft did not match. Refresh before trying again.'); return saved;
  }
}
