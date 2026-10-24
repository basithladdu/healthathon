import { hasSymptomContext, sameSymptomContext, validateSymptomInput, type SymptomEntry } from './family-symptom-state.ts';

export function symptomTrendGroups(entries: readonly SymptomEntry[], patientId: string, today: string, symptom: string) {
  const eligible = entries.filter((entry) => entry.patientId === patientId && entry.date <= today
    && entry.symptom === symptom && hasSymptomContext(entry) && !validateSymptomInput(entry, today)
    && (entry.severity === null || typeof entry.severity === 'number'))
    .sort((a, b) => `${a.date}T${a.time}`.localeCompare(`${b.date}T${b.time}`) || a.id.localeCompare(b.id));
  const groups: { id: string; reference: SymptomEntry; entries: SymptomEntry[] }[] = [];
  for (const entry of eligible) {
    const group = groups.find((item) => sameSymptomContext(item.reference, entry));
    if (group) group.entries.push(entry);
    else groups.push({ id: entry.id, reference: entry, entries: [entry] });
  }
  return groups.sort((a, b) => `${b.entries.at(-1)?.date}T${b.entries.at(-1)?.time}`.localeCompare(`${a.entries.at(-1)?.date}T${a.entries.at(-1)?.time}`));
}

export function symptomTrendSegments(entries: readonly SymptomEntry[]): SymptomEntry[][] {
  const segments: SymptomEntry[][] = [];
  let current: SymptomEntry[] = [];
  for (const entry of entries) {
    if (typeof entry.severity === 'number') current.push(entry);
    else { if (current.length) segments.push(current); current = []; }
  }
  if (current.length) segments.push(current);
  return segments;
}
