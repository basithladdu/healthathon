import { isValidAppointmentDate } from './appointment-state.ts';

export const FAMILY_EXPENSE_CATEGORIES = ['Medicines', 'Travel', 'Tests', 'Hospital', 'Home care', 'Other'] as const;
export type FamilyExpenseCategory = (typeof FAMILY_EXPENSE_CATEGORIES)[number];
type FamilyCostItem = { id: string; patientId: string; createdBy: string; updatedBy: string };
export type FamilyExpense = FamilyCostItem & { title: string; category: FamilyExpenseCategory; date: string; amountRupees: number; paid: boolean };
export type FamilyFundraiser = FamilyCostItem & { title: string; url: string; note: string };
export type FamilyPaperwork = FamilyCostItem & { title: string; done: boolean };
export type FamilyCostState = { expenses: FamilyExpense[]; fundraisers: FamilyFundraiser[]; paperwork: FamilyPaperwork[] };
export type FamilyExpenseInput = Pick<FamilyExpense, 'title' | 'category' | 'date' | 'amountRupees' | 'paid'>;
export type FamilyFundraiserInput = Pick<FamilyFundraiser, 'title' | 'url' | 'note'>;
export type RemovedFamilyCostItem = { kind: 'expense'; item: FamilyExpense } | { kind: 'fundraiser'; item: FamilyFundraiser } | { kind: 'paperwork'; item: FamilyPaperwork };

export function createEmptyFamilyCostState(): FamilyCostState {
  return { expenses: [], fundraisers: [], paperwork: [] };
}

export function parseFamilyExpenseRupees(value: string): number | null {
  const clean = value.trim();
  if (!/^(?:\d+|\d*\.\d{1,2})$/.test(clean)) return null;
  const amount = Number(clean);
  return validAmount(amount) ? amount : null;
}

function validAmount(amount: number): boolean {
  return Number.isFinite(amount) && amount > 0 && Number.isSafeInteger(Math.round(amount * 100)) && Math.round(amount * 100) / 100 === amount;
}

export function safeFamilyFundraiserUrl(value: string): string | null {
  const clean = value.trim();
  if (/[\u0000-\u0020\u007f]/.test(clean)) return null;
  try {
    const url = new URL(clean);
    return url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

function identityValid(patientId: string, author: string, id: string): boolean {
  return Boolean(patientId.trim() && author.trim() && id.trim());
}

function updateItem<T extends FamilyCostItem>(items: T[], patientId: string, id: string, author: string, patch: Partial<T>): T[] {
  if (!author.trim() || !items.some((item) => item.patientId === patientId && item.id === id)) return items;
  return items.map((item) => item.patientId === patientId && item.id === id ? { ...item, ...patch, updatedBy: author.trim() } : item);
}

function validExpense(input: FamilyExpenseInput): boolean {
  return Boolean(input.title.trim()) && input.title.length <= 160 && FAMILY_EXPENSE_CATEGORIES.includes(input.category)
    && isValidAppointmentDate(input.date) && validAmount(input.amountRupees);
}

export function addFamilyExpense(state: FamilyCostState, patientId: string, author: string, id: string, input: FamilyExpenseInput): FamilyCostState {
  if (!identityValid(patientId, author, id) || !validExpense(input) || state.expenses.some((item) => item.id === id)) return state;
  return { ...state, expenses: [...state.expenses, { ...input, title: input.title.trim(), id, patientId, createdBy: author.trim(), updatedBy: author.trim() }] };
}

export function editFamilyExpense(state: FamilyCostState, patientId: string, id: string, author: string, input: FamilyExpenseInput): FamilyCostState {
  if (!validExpense(input)) return state;
  const expenses = updateItem(state.expenses, patientId, id, author, { ...input, title: input.title.trim() });
  return expenses === state.expenses ? state : { ...state, expenses };
}

export function setFamilyExpensePaid(state: FamilyCostState, patientId: string, id: string, author: string, paid: boolean): FamilyCostState {
  const expenses = updateItem(state.expenses, patientId, id, author, { paid });
  return expenses === state.expenses ? state : { ...state, expenses };
}

export function addFamilyFundraiser(state: FamilyCostState, patientId: string, author: string, id: string, input: FamilyFundraiserInput): FamilyCostState {
  const url = safeFamilyFundraiserUrl(input.url);
  if (!identityValid(patientId, author, id) || !input.title.trim() || input.title.length > 160 || input.note.length > 1000 || !url || state.fundraisers.some((item) => item.id === id)) return state;
  return { ...state, fundraisers: [...state.fundraisers, { ...input, title: input.title.trim(), url, id, patientId, createdBy: author.trim(), updatedBy: author.trim() }] };
}

export function editFamilyFundraiser(state: FamilyCostState, patientId: string, id: string, author: string, input: FamilyFundraiserInput): FamilyCostState {
  const url = safeFamilyFundraiserUrl(input.url);
  if (!input.title.trim() || input.title.length > 160 || input.note.length > 1000 || !url) return state;
  const fundraisers = updateItem(state.fundraisers, patientId, id, author, { ...input, title: input.title.trim(), url });
  return fundraisers === state.fundraisers ? state : { ...state, fundraisers };
}

export function addFamilyPaperwork(state: FamilyCostState, patientId: string, author: string, id: string, title: string): FamilyCostState {
  if (!identityValid(patientId, author, id) || !title.trim() || title.length > 240 || state.paperwork.some((item) => item.id === id)) return state;
  return { ...state, paperwork: [...state.paperwork, { id, patientId, title: title.trim(), done: false, createdBy: author.trim(), updatedBy: author.trim() }] };
}

export function editFamilyPaperwork(state: FamilyCostState, patientId: string, id: string, author: string, title: string): FamilyCostState {
  if (!title.trim() || title.length > 240) return state;
  const paperwork = updateItem(state.paperwork, patientId, id, author, { title: title.trim() });
  return paperwork === state.paperwork ? state : { ...state, paperwork };
}

export function setFamilyPaperworkDone(state: FamilyCostState, patientId: string, id: string, author: string, done: boolean): FamilyCostState {
  const paperwork = updateItem(state.paperwork, patientId, id, author, { done });
  return paperwork === state.paperwork ? state : { ...state, paperwork };
}

export function removeFamilyCostItem(state: FamilyCostState, patientId: string, removed: RemovedFamilyCostItem): FamilyCostState {
  const keep = (item: FamilyCostItem) => item.patientId !== patientId || item.id !== removed.item.id;
  if (removed.kind === 'expense') return { ...state, expenses: state.expenses.filter(keep) };
  if (removed.kind === 'fundraiser') return { ...state, fundraisers: state.fundraisers.filter(keep) };
  return { ...state, paperwork: state.paperwork.filter(keep) };
}

export function restoreFamilyCostItem(state: FamilyCostState, patientId: string, removed: RemovedFamilyCostItem): FamilyCostState {
  if (removed.item.patientId !== patientId) return state;
  if (removed.kind === 'expense') return state.expenses.some((item) => item.id === removed.item.id) ? state : { ...state, expenses: [...state.expenses, removed.item] };
  if (removed.kind === 'fundraiser') return state.fundraisers.some((item) => item.id === removed.item.id) ? state : { ...state, fundraisers: [...state.fundraisers, removed.item] };
  return state.paperwork.some((item) => item.id === removed.item.id) ? state : { ...state, paperwork: [...state.paperwork, removed.item] };
}

export function familyExpenseTotals(state: FamilyCostState, patientId: string): { totalRupees: number; paidRupees: number; unpaidRupees: number } {
  const expenses = state.expenses.filter((item) => item.patientId === patientId);
  const paise = expenses.reduce((sum, item) => sum + Math.round(item.amountRupees * 100), 0);
  const paidPaise = expenses.filter((item) => item.paid).reduce((sum, item) => sum + Math.round(item.amountRupees * 100), 0);
  return { totalRupees: paise / 100, paidRupees: paidPaise / 100, unpaidRupees: (paise - paidPaise) / 100 };
}

export function exportFamilyFundraiserLink(state: FamilyCostState, patientId: string, id: string): string | null {
  const fundraiser = state.fundraisers.find((item) => item.patientId === patientId && item.id === id);
  if (!fundraiser) return null;
  const url = safeFamilyFundraiserUrl(fundraiser.url);
  return url ? [fundraiser.title, url, fundraiser.note].filter(Boolean).join('\n\n') : null;
}

export function exportFamilyCostState(state: FamilyCostState, patientId: string): string {
  const totals = familyExpenseTotals(state, patientId);
  return [
    'SAANTHVANA — HELP WITH COSTS', `Patient: ${patientId}`,
    'Only expenses and tasks entered by the family are included. This is not a payment statement or funding approval.',
    `RECORDED EXPENSES\nTotal: INR ${totals.totalRupees.toFixed(2)}\nMarked paid: INR ${totals.paidRupees.toFixed(2)}\nNot marked paid: INR ${totals.unpaidRupees.toFixed(2)}`,
    ...state.expenses.filter((item) => item.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date)).map((item) => `${item.date} — ${item.title}\n${item.category} · INR ${item.amountRupees.toFixed(2)} · ${item.paid ? 'Marked paid' : 'Not marked paid'}\nAdded by: ${item.createdBy}\nLast edited by: ${item.updatedBy}`),
    'FUNDRAISER LINKS',
    ...state.fundraisers.filter((item) => item.patientId === patientId).map((item) => `${exportFamilyFundraiserLink(state, patientId, item.id) ?? 'Invalid link not included'}\nAdded by: ${item.createdBy}\nLast edited by: ${item.updatedBy}`),
    'PAPERWORK',
    ...state.paperwork.filter((item) => item.patientId === patientId).map((item) => `${item.done ? 'Done' : 'To do'} — ${item.title}\nAdded by: ${item.createdBy}\nLast edited by: ${item.updatedBy}`),
  ].join('\n\n');
}
