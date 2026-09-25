'use client';

import { useId, useState, type FormEvent } from 'react';
import {
  FAMILY_EXPENSE_CATEGORIES, addFamilyExpense, addFamilyPaperwork, editFamilyExpense,
  editFamilyPaperwork, exportFamilyCostState, familyExpenseTotals, parseFamilyExpenseRupees,
  removeFamilyCostItem, restoreFamilyCostItem, setFamilyExpensePaid, setFamilyPaperworkDone,
  type FamilyCostState, type FamilyExpense, type FamilyExpenseCategory, type RemovedFamilyCostItem,
} from './family-cost-state';

export type { FamilyCostState } from './family-cost-state';
type FamilyCostHelpProps = { patientId: string; author: string; today: string; hindi: boolean; state: FamilyCostState; onChange: (state: FamilyCostState) => void };
type ExpenseDraft = { title: string; category: FamilyExpenseCategory; date: string; amount: string; paid: boolean };
const HINDI_CATEGORIES: Record<FamilyExpenseCategory, string> = { Medicines: 'दवाइयाँ', Travel: 'आना-जाना', Tests: 'जाँच', Hospital: 'अस्पताल', 'Home care': 'घर पर देखभाल', Other: 'अन्य' };
const PAPERWORK_SUGGESTIONS = [
  ['ID document', 'पहचान पत्र'],
  ['Hospital estimate', 'अस्पताल का खर्च अनुमान'],
  ['Income document', 'आय का दस्तावेज़'],
  ['Treatment summary', 'इलाज का सारांश'],
] as const;

export function FamilyCostHelp(props: FamilyCostHelpProps) {
  return <CostHelpContent key={`${props.patientId}:${props.author}`} {...props} />;
}

function CostHelpContent({ patientId, author, today, hindi, state, onChange }: FamilyCostHelpProps) {
  const id = useId();
  const [expense, setExpense] = useState<ExpenseDraft | null>(null);
  const [expenseId, setExpenseId] = useState<string | null>(null);
  const [paperworkTitle, setPaperworkTitle] = useState('');
  const [paperworkId, setPaperworkId] = useState<string | null>(null);
  const [removed, setRemoved] = useState<RemovedFamilyCostItem[]>([]);
  const [message, setMessage] = useState('');
  const expenses = state.expenses.filter((item) => item.patientId === patientId).sort((a, b) => b.date.localeCompare(a.date));
  const paperwork = state.paperwork.filter((item) => item.patientId === patientId);
  const totals = familyExpenseTotals(state, patientId);
  const lastRemoved = removed.at(-1);
  const t = (english: string, translated: string) => hindi ? translated : english;
  const supportQuestion = t('Could I speak with the hospital social worker about help with treatment costs? Which assistance options can we apply for, what documents do you need, and what should we do next?', 'क्या मैं इलाज के खर्च में मदद के बारे में अस्पताल के सामाजिक कार्यकर्ता से बात कर सकता/सकती हूँ? हम किन सहायता विकल्पों के लिए आवेदन कर सकते हैं, कौन से दस्तावेज़ चाहिए और अगला कदम क्या है?');
  const money = (value: number) => value.toLocaleString(hindi ? 'hi-IN' : 'en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dateLabel = (date: string) => new Date(`${date}T00:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

  function editExpense(item?: FamilyExpense) {
    setExpenseId(item?.id ?? null);
    setExpense(item ? { title: item.title, category: item.category, date: item.date, amount: item.amountRupees.toFixed(2), paid: item.paid } : { title: '', category: 'Other', date: today, amount: '', paid: false });
    setMessage('');
  }

  function saveExpense(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!expense) return;
    const amountRupees = parseFamilyExpenseRupees(expense.amount);
    if (amountRupees === null) { setMessage(t('Enter a positive amount in rupees, with up to two decimal places.', 'रुपयों में सही रकम लिखें। दशमलव के बाद अधिकतम दो अंक रखें।')); return; }
    const input = { title: expense.title, category: expense.category, date: expense.date, amountRupees, paid: expense.paid };
    const next = expenseId ? editFamilyExpense(state, patientId, expenseId, author, input) : addFamilyExpense(state, patientId, author, crypto.randomUUID(), input);
    if (next === state) { setMessage(t('Check the expense name and date.', 'खर्च का नाम और तारीख जाँचें।')); return; }
    onChange(next); setExpense(null); setExpenseId(null); setMessage(t('Expense saved.', 'खर्च सेव हो गया।'));
  }

  function savePaperwork(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = paperworkId ? editFamilyPaperwork(state, patientId, paperworkId, author, paperworkTitle) : addFamilyPaperwork(state, patientId, author, crypto.randomUUID(), paperworkTitle);
    if (next === state) return;
    onChange(next); setPaperworkTitle(''); setPaperworkId(null); setMessage(t('Paperwork task saved.', 'कागज़ी काम सेव हो गया।'));
  }

  function paperworkAdded(english: string, translated: string) {
    return paperwork.some((item) => [english.toLowerCase(), translated].includes(item.title.trim().toLowerCase()));
  }

  function addPaperworkSuggestion(english: string, translated: string) {
    if (paperworkAdded(english, translated)) return;
    const next = addFamilyPaperwork(state, patientId, author, crypto.randomUUID(), t(english, translated));
    if (next === state) return;
    onChange(next); setMessage(t('Added to your checklist.', 'चेकलिस्ट में जोड़ दिया।'));
  }

  async function copySupportQuestion() {
    try {
      await navigator.clipboard.writeText(supportQuestion);
      setMessage(t('Question copied.', 'सवाल कॉपी हो गया।'));
    } catch {
      setMessage(t('Select the question below to copy it.', 'कॉपी करने के लिए नीचे दिए सवाल को चुनें।'));
    }
  }

  function remove(item: RemovedFamilyCostItem) {
    onChange(removeFamilyCostItem(state, patientId, item)); setRemoved((previous) => [...previous, item]); setMessage('');
    if (item.kind === 'expense' && expenseId === item.item.id) { setExpense(null); setExpenseId(null); }
    if (item.kind === 'paperwork' && paperworkId === item.item.id) { setPaperworkTitle(''); setPaperworkId(null); }
  }

  function undo() {
    if (!lastRemoved) return;
    const next = restoreFamilyCostItem(state, patientId, lastRemoved);
    if (next === state) return;
    onChange(next); setRemoved((previous) => previous.slice(0, -1)); setMessage(t('Restored.', 'वापस जोड़ दिया।'));
  }

  function download() {
    // Keep older saved links intact while this page exports expenses and paperwork only.
    const text = exportFamilyCostState({ ...state, fundraisers: [] }, patientId)
      .replace('SAANTHVANA — HELP WITH COSTS', 'SAANTHVANA — COSTS & SUPPORT')
      .replace('\n\nFUNDRAISER LINKS', '');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `saanthvana-costs-${patientId.replace(/[^a-zA-Z0-9_-]/g, '-')}.txt`; link.click(); URL.revokeObjectURL(url);
    setMessage(t('Your costs list was downloaded.', 'खर्च की सूची डाउनलोड हो गई।'));
  }

  return <section className="family-cost-help" aria-labelledby={`${id}-heading`}>
    <div className="family-cost-heading"><h1 id={`${id}-heading`}>{t('Costs & support', 'खर्च और मदद')}</h1><button type="button" className="secondary-button" disabled={!expenses.length && !paperwork.length} onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button></div>
    <p className="family-cost-message" role="status">{message}</p>
    {lastRemoved && <div className="family-cost-undo" role="status"><span>{t('Removed', 'हटा दिया')}: {lastRemoved.item.title}</span><button type="button" onClick={undo}>{t('Undo', 'वापस लाएँ')}</button></div>}
    <div className="family-cost-grid">
      <section className="family-cost-panel" aria-labelledby={`${id}-expenses`}>
        <div className="family-cost-section-heading"><h2 id={`${id}-expenses`}>{t('Expenses', 'खर्च')}</h2>{!expense && <button type="button" className="family-cost-primary" onClick={() => editExpense()}>{t('Add expense', 'खर्च जोड़ें')}</button>}</div>
        <dl className="family-cost-totals"><div><dt>{t('Total', 'कुल खर्च')}</dt><dd>{money(totals.totalRupees)}</dd></div><div><dt>{t('Paid', 'भुगतान किया')}</dt><dd>{money(totals.paidRupees)}</dd></div><div><dt>{t('To pay', 'भुगतान बाकी')}</dt><dd>{money(totals.unpaidRupees)}</dd></div></dl>
        {expense && <form className="family-cost-form" onSubmit={saveExpense}>
          <h3>{expenseId ? t('Edit expense', 'खर्च बदलें') : t('Add expense', 'खर्च जोड़ें')}</h3>
          <label className="family-cost-wide" htmlFor={`${id}-expense-title`}>{t('What was it for?', 'किस चीज़ का खर्च?')}<input id={`${id}-expense-title`} value={expense.title} onChange={(event) => setExpense({ ...expense, title: event.target.value })} required maxLength={160} /></label>
          <label htmlFor={`${id}-amount`}>{t('Amount (₹)', 'रकम (₹)')}<input id={`${id}-amount`} inputMode="decimal" value={expense.amount} onChange={(event) => setExpense({ ...expense, amount: event.target.value })} required /></label>
          <label htmlFor={`${id}-expense-date`}>{t('Date', 'तारीख')}<input id={`${id}-expense-date`} type="date" value={expense.date} onChange={(event) => setExpense({ ...expense, date: event.target.value })} required /></label>
          <label htmlFor={`${id}-category`}>{t('Type', 'किस तरह का खर्च')}<select id={`${id}-category`} value={expense.category} onChange={(event) => setExpense({ ...expense, category: event.target.value as FamilyExpenseCategory })}>{FAMILY_EXPENSE_CATEGORIES.map((category) => <option key={category} value={category}>{hindi ? HINDI_CATEGORIES[category] : category}</option>)}</select></label>
          <label className="family-cost-checkbox"><input type="checkbox" checked={expense.paid} onChange={(event) => setExpense({ ...expense, paid: event.target.checked })} />{t('Already paid', 'भुगतान हो गया')}</label>
          <div className="family-cost-wide family-cost-actions"><button type="submit" className="family-cost-primary" disabled={!expense.title.trim() || !expense.date || !expense.amount.trim()}>{t('Save expense', 'खर्च सेव करें')}</button><button type="button" className="family-cost-link" onClick={() => { setExpense(null); setExpenseId(null); }}>{t('Cancel', 'रहने दें')}</button></div>
        </form>}
        <ul className="family-expense-list">{expenses.map((item) => <li key={item.id}><div className="family-expense-title"><strong>{item.title}</strong><span>{money(item.amountRupees)}</span></div><p>{hindi ? HINDI_CATEGORIES[item.category] : item.category} · {dateLabel(item.date)}</p><div className="family-cost-actions"><label className="family-cost-checkbox"><input type="checkbox" checked={item.paid} onChange={(event) => onChange(setFamilyExpensePaid(state, patientId, item.id, author, event.target.checked))} />{t('Paid', 'भुगतान किया')}</label><button type="button" className="family-cost-link" onClick={() => editExpense(item)} aria-label={`${t('Edit expense', 'खर्च बदलें')}: ${item.title}`}>{t('Edit', 'बदलें')}</button><button type="button" className="family-cost-link" onClick={() => remove({ kind: 'expense', item })} aria-label={`${t('Remove expense', 'खर्च हटाएँ')}: ${item.title}`}>{t('Remove', 'हटाएँ')}</button></div></li>)}</ul>
        {expenses.length === 0 && !expense && <p className="family-cost-empty">{t('No expenses yet.', 'अभी कोई खर्च नहीं जोड़ा है।')}</p>}
      </section>
      <div className="family-cost-side">
        <section className="family-cost-panel family-paperwork-panel" aria-labelledby={`${id}-paperwork`}>
          <h2 id={`${id}-paperwork`}>{t('Paperwork', 'कागज़ी काम')}</h2>
          <p className="family-paperwork-hint">{t('Optional checklist. Ask which documents apply.', 'वैकल्पिक सूची। पूछ लें कि कौन से दस्तावेज़ चाहिए।')}</p>
          <div className="family-paperwork-suggestions">{PAPERWORK_SUGGESTIONS.map(([english, translated]) => {
            const added = paperworkAdded(english, translated);
            return <button key={english} type="button" disabled={added} onClick={() => addPaperworkSuggestion(english, translated)} aria-label={`${added ? t('Added', 'जोड़ दिया') : t('Add', 'जोड़ें')}: ${t(english, translated)}`}><span aria-hidden="true">{added ? '✓' : '+'}</span>{t(english, translated)}</button>;
          })}</div>
          <ul className="family-paperwork-list">{paperwork.map((item) => <li key={item.id}><label className="family-cost-checkbox"><input type="checkbox" checked={item.done} onChange={(event) => onChange(setFamilyPaperworkDone(state, patientId, item.id, author, event.target.checked))} /><span className={item.done ? 'is-done' : undefined}>{item.title}</span></label><div className="family-cost-actions"><button type="button" className="family-cost-link" onClick={() => { setPaperworkId(item.id); setPaperworkTitle(item.title); }} aria-label={`${t('Edit paperwork', 'कागज़ी काम बदलें')}: ${item.title}`}>{t('Edit', 'बदलें')}</button><button type="button" className="family-cost-link" onClick={() => remove({ kind: 'paperwork', item })} aria-label={`${t('Remove paperwork', 'कागज़ी काम हटाएँ')}: ${item.title}`}>{t('Remove', 'हटाएँ')}</button></div></li>)}</ul>
          <form className="family-paperwork-add" onSubmit={savePaperwork}><div className="family-cost-actions"><input aria-label={t('Paperwork task', 'कागज़ी काम')} placeholder={t('Add paperwork to do', 'कागज़ी काम लिखें')} value={paperworkTitle} onChange={(event) => setPaperworkTitle(event.target.value)} required maxLength={240} /><button type="submit" className="family-cost-primary" disabled={!paperworkTitle.trim()}>{paperworkId ? t('Save', 'सेव करें') : t('Add', 'जोड़ें')}</button></div>{paperworkId && <button type="button" className="family-cost-link" onClick={() => { setPaperworkId(null); setPaperworkTitle(''); }}>{t('Cancel edit', 'बदलना रद्द करें')}</button>}</form>
        </section>
        <section className="family-cost-panel family-cost-support" aria-labelledby={`${id}-support`}>
          <h2 id={`${id}-support`}>{t('Ask the hospital social worker', 'अस्पताल के सामाजिक कार्यकर्ता से पूछें')}</h2>
          <p className="family-cost-question">{supportQuestion}</p>
          <button type="button" className="family-cost-primary" onClick={copySupportQuestion}>{t('Copy question', 'सवाल कॉपी करें')}</button>
        </section>
      </div>
    </div>
  </section>;
}
