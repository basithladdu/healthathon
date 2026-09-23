export const CARE_CIRCLE_PERMISSIONS = ['careNote', 'calendar', 'reports', 'costs'] as const;
export type CareCirclePermission = (typeof CARE_CIRCLE_PERMISSIONS)[number];
export type CareCircleRole = 'patient' | 'family' | 'doctor';
export type CareCircleMember = {
  id: string; patientId: string; name: string; relationship: string;
  status: 'active' | 'revoked'; permissions: Record<CareCirclePermission, boolean>;
  addedBy: string; addedAt: string;
};
export type CareCircleAudience = { patient: boolean; careTeam: boolean; memberIds: string[] };
export type CareCircleRead = { actor: string; role: CareCircleRole; at: string };
export type CareCircleUpdate = {
  id: string; patientId: string; text: string; author: string; authorRole: CareCircleRole;
  createdAt: string; audience: CareCircleAudience; reads: CareCircleRead[];
};
export type CareCircleAudit = {
  id: string; patientId: string; actor: string; at: string;
  action: 'member-added' | 'access-changed' | 'member-revoked' | 'member-restored' | 'update-added' | 'update-read';
  targetId: string; targetName: string; permission?: CareCirclePermission; allowed?: boolean;
};
export type CareCircleState = { members: CareCircleMember[]; updates: CareCircleUpdate[]; audit: CareCircleAudit[] };
export type CareCircleAction = { patientId: string; actor: string; role: CareCircleRole; at: string };
const DEFAULT_PERMISSIONS = { careNote: true, calendar: true, reports: true, costs: false };
const normalizedName = (name: string) => name.trim().replace(/\s+/g, ' ').toLocaleLowerCase();

export function createInitialCareCircle(): CareCircleState {
  // Fictional people for the existing Meera walkthrough account. No updates or
  // read receipts are pre-populated: those exist only after the user's actions.
  return {
    members: [
      { id: 'meera-circle-kavya', patientId: 'CANCER-20418', name: 'Kavya Raghavan', relationship: 'Daughter', status: 'active', permissions: { ...DEFAULT_PERMISSIONS }, addedBy: 'Meera Raghavan', addedAt: '2026-09-23T09:00:00.000Z' },
      { id: 'meera-circle-arun', patientId: 'CANCER-20418', name: 'Arun Raghavan', relationship: 'Brother', status: 'active', permissions: { ...DEFAULT_PERMISSIONS }, addedBy: 'Meera Raghavan', addedAt: '2026-09-23T09:00:00.000Z' },
    ],
    updates: [], audit: [],
  };
}

export function activeCareCircleMember(state: CareCircleState, patientId: string, author: string): CareCircleMember | undefined {
  return state.members.find((member) => member.patientId === patientId && member.status === 'active' && normalizedName(member.name) === normalizedName(author));
}

const PUBLIC_VIEWS = new Set(['home', 'daily-care', 'care-circle', 'journal', 'voice-journal', 'my-space', 'access-care', 'find-care', 'care-near-me', 'find-support', 'support-resources', 'community', 'support-groups']);
const VIEW_PERMISSION: Record<string, CareCirclePermission> = {
  reports: 'reports', 'document-search': 'reports', search: 'reports', 'find-in-documents': 'reports',
  'lab-history': 'reports', labs: 'reports', 'test-results': 'reports', 'clinical-documents': 'reports', 'my-documents': 'reports',
  'care-note': 'careNote', 'my-plan': 'careNote', 'note-history': 'careNote', history: 'careNote', 'care-note/history': 'careNote',
  summary: 'careNote', 'care-summary': 'careNote', 'doctor-pack': 'careNote', 'next-doctor': 'careNote',
  'care-story': 'careNote', 'my-timeline': 'careNote', 'care-timeline': 'careNote', 'cancer-overview': 'careNote', 'my-cancer-care': 'careNote',
  'copy-tracker': 'careNote', 'shared-copies': 'careNote', 'my-doctors': 'careNote', 'care-team': 'careNote',
  'symptom-diary': 'careNote', 'how-i-feel': 'careNote',
  calendar: 'calendar', 'next-visit': 'calendar', medicines: 'calendar', tasks: 'calendar', 'family-tasks': 'calendar', 'visit-questions': 'calendar', 'next-visit-questions': 'calendar',
  'prepare-conversation': 'calendar', 'prepare-for-a-visit': 'calendar', 'open-questions': 'calendar', questions: 'calendar',
  'home-help': 'calendar', 'help-at-home': 'calendar',
  costs: 'costs', 'cost-help': 'costs',
};

/** UI access gate only. Real authentication/authorization must also be enforced by a backend. */
export function canFamilyOpenView(state: CareCircleState, patientId: string, author: string, view: string): boolean {
  const normalized = view.replace(/^\/+|\/+$/g, '');
  if (PUBLIC_VIEWS.has(normalized)) return true;
  const member = activeCareCircleMember(state, patientId, author);
  // This combined screen includes both care history and report filenames.
  if (normalized === 'cancer-overview' || normalized === 'my-cancer-care') return Boolean(member?.permissions.careNote && member.permissions.reports);
  // Medicine entries keep a reviewed prescription excerpt alongside the schedule.
  if (normalized === 'medicines') return Boolean(member?.permissions.calendar && member.permissions.reports);
  const permission = VIEW_PERMISSION[normalized];
  return Boolean(permission && member?.permissions[permission]);
}

function validAction(input: CareCircleAction) {
  return Boolean(input.patientId.trim() && input.actor.trim() && ['patient', 'family', 'doctor'].includes(input.role) && Number.isFinite(Date.parse(input.at)));
}
function appendAudit(state: CareCircleState, input: CareCircleAction, entry: Omit<CareCircleAudit, 'id' | 'patientId' | 'actor' | 'at'>): CareCircleAudit[] {
  return [...state.audit, { ...entry, id: `${entry.action}-${entry.targetId}-${input.at}-${state.audit.length}`, patientId: input.patientId, actor: input.actor.trim(), at: input.at }];
}

export function addCareCircleMember(state: CareCircleState, input: CareCircleAction & { id: string; name: string; relationship: string }): CareCircleState {
  if (!validAction(input) || input.role !== 'patient' || !input.id || !input.name.trim() || input.name.trim().length > 80
    || !input.relationship.trim() || input.relationship.trim().length > 50 || normalizedName(input.name) === normalizedName(input.actor)
    || state.members.some((member) => member.id === input.id || (member.patientId === input.patientId && normalizedName(member.name) === normalizedName(input.name)))) return state;
  const member: CareCircleMember = { id: input.id, patientId: input.patientId, name: input.name.trim().replace(/\s+/g, ' '), relationship: input.relationship.trim(), status: 'active', permissions: { ...DEFAULT_PERMISSIONS }, addedBy: input.actor.trim(), addedAt: input.at };
  return { ...state, members: [...state.members, member], audit: appendAudit(state, input, { action: 'member-added', targetId: member.id, targetName: member.name }) };
}

export function setCareCirclePermission(state: CareCircleState, input: CareCircleAction & { memberId: string; permission: CareCirclePermission; allowed: boolean }): CareCircleState {
  const member = state.members.find((item) => item.patientId === input.patientId && item.id === input.memberId && item.status === 'active');
  if (!validAction(input) || input.role !== 'patient' || !member || !CARE_CIRCLE_PERMISSIONS.includes(input.permission) || typeof input.allowed !== 'boolean' || member.permissions[input.permission] === input.allowed) return state;
  return { ...state, members: state.members.map((item) => item === member ? { ...item, permissions: { ...item.permissions, [input.permission]: input.allowed } } : item), audit: appendAudit(state, input, { action: 'access-changed', targetId: member.id, targetName: member.name, permission: input.permission, allowed: input.allowed }) };
}

export function setCareCircleMemberActive(state: CareCircleState, input: CareCircleAction & { memberId: string; active: boolean }): CareCircleState {
  const member = state.members.find((item) => item.patientId === input.patientId && item.id === input.memberId);
  const status = input.active ? 'active' : 'revoked';
  if (!validAction(input) || input.role !== 'patient' || !member || typeof input.active !== 'boolean' || member.status === status) return state;
  return { ...state, members: state.members.map((item) => item === member ? { ...item, status } : item), audit: appendAudit(state, input, { action: input.active ? 'member-restored' : 'member-revoked', targetId: member.id, targetName: member.name }) };
}

export function canWriteCareCircleUpdate(state: CareCircleState, patientId: string, author: string, role: CareCircleRole): boolean {
  return Boolean(patientId.trim() && author.trim() && (role === 'patient' || role === 'doctor' || (role === 'family' && activeCareCircleMember(state, patientId, author))));
}

export function addCareCircleUpdate(state: CareCircleState, input: CareCircleAction & { id: string; text: string; audience: CareCircleAudience }): CareCircleState {
  if (!validAction(input) || !canWriteCareCircleUpdate(state, input.patientId, input.actor, input.role) || !input.id || !input.text.trim() || input.text.trim().length > 1200
    || state.updates.some((update) => update.id === input.id) || typeof input.audience.patient !== 'boolean' || typeof input.audience.careTeam !== 'boolean'
    || input.audience.memberIds.some((id) => !state.members.some((member) => member.patientId === input.patientId && member.id === id && member.status === 'active'))) return state;
  const audience = { ...input.audience, memberIds: [...new Set(input.audience.memberIds)] };
  const update: CareCircleUpdate = { id: input.id, patientId: input.patientId, author: input.actor.trim(), authorRole: input.role, text: input.text.trim(), createdAt: input.at, audience, reads: [] };
  return { ...state, updates: [...state.updates, update], audit: appendAudit(state, input, { action: 'update-added', targetId: update.id, targetName: update.author }) };
}

export function visibleCareCircleUpdates(state: CareCircleState, patientId: string, author: string, role: CareCircleRole): CareCircleUpdate[] {
  if (!canWriteCareCircleUpdate(state, patientId, author, role)) return [];
  const member = role === 'family' ? activeCareCircleMember(state, patientId, author) : undefined;
  return state.updates.filter((update) => update.patientId === patientId && (
    (update.authorRole === role && normalizedName(update.author) === normalizedName(author))
    || (role === 'patient' && update.audience.patient)
    || (role === 'doctor' && update.audience.careTeam)
    || (member && update.audience.memberIds.includes(member.id))
  )).sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
}

export function acknowledgeCareCircleUpdate(state: CareCircleState, input: CareCircleAction & { updateId: string }): CareCircleState {
  const update = visibleCareCircleUpdates(state, input.patientId, input.actor, input.role).find((item) => item.id === input.updateId);
  if (!validAction(input) || !update || (update.authorRole === input.role && normalizedName(update.author) === normalizedName(input.actor))
    || update.reads.some((read) => read.role === input.role && normalizedName(read.actor) === normalizedName(input.actor))) return state;
  const read = { actor: input.actor.trim(), role: input.role, at: input.at };
  return { ...state, updates: state.updates.map((item) => item === update ? { ...item, reads: [...item.reads, read] } : item), audit: appendAudit(state, input, { action: 'update-read', targetId: update.id, targetName: update.author }) };
}

export function careCircleUpdateText(update: CareCircleUpdate, patientName: string) {
  return `${patientName}\n${update.text}\n\n${update.author}\n${update.createdAt}`;
}
