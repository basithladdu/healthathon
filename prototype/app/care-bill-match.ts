export type CareBillCheck = {
  status: 'matched' | 'needs-review' | 'unreadable';
  checks: { medicine: boolean; oral: boolean; hospital: boolean; date: boolean };
  message: string;
};

// A text match is not proof that a bill is genuine or that medicine is in stock.
export function inspectCareBillText(text: string, hospitalName: string, visitDate: string): CareBillCheck {
  const normal = text.toLowerCase().replace(/[^\p{L}\p{N}\s./-]/gu, ' ');
  const lines = normal.split('\n');
  const medicineLines = lines.filter((line) => /\bmorphine\b/.test(line));
  const negative = /\b(?:not (?:dispensed|available|supplied)|out of stock|cancelled|refunded)\b/;
  const medicine = medicineLines.some((line) => !negative.test(line));
  const oral = medicineLines.some((line) => /\b(?:oral|tablet|tablets|tab|tabs|syrup|solution)\b/.test(line) && !/\b(?:injection|injectable|inj)\b/.test(line) && !negative.test(line));
  const names = hospitalName.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter((word) => word.length > 2 && !['hospital', 'hospitals', 'medical', 'centre', 'center', 'care', 'palliative', 'private', 'limited', 'the', 'and', 'of'].includes(word));
  const matchedNames = names.filter((word) => normal.split(/\s+/).includes(word));
  const hospital = names.length > 0 && matchedNames.length >= Math.min(2, names.length);
  const parsed = /^(\d{4})-(\d{2})-(\d{2})$/.exec(visitDate);
  let date = false;
  if (parsed) {
    const [, year, month, day] = parsed;
    const dates = [visitDate, `${day}/${month}/${year}`, `${day}-${month}-${year}`, `${day}.${month}.${year}`, `${Number(day)}/${Number(month)}/${year}`, `${day}/${month}/${year.slice(2)}`, `${day}-${month}-${year.slice(2)}`];
    const monthName = new Date(`${visitDate}T12:00:00Z`).toLocaleString('en', { month: 'short', timeZone: 'UTC' }).toLowerCase();
    dates.push(`${Number(day)} ${monthName} ${year}`, `${day} ${monthName} ${year}`);
    date = dates.some((value) => normal.includes(value));
  }
  const checks = { medicine, oral, hospital, date };
  if (!normal.trim()) return { status: 'unreadable', checks, message: 'Could not read this bill. Try a clearer photo.' };
  if (Object.values(checks).every(Boolean)) return { status: 'matched', checks, message: 'Bill text matches the hospital, date and oral morphine. Authenticity still needs checking.' };
  return { status: 'needs-review', checks, message: 'Some bill details could not be matched. The report will need checking.' };
}
