import { selectSymptomEntries, type SymptomEntry } from './family-symptom-state.ts';
import { PAIN_BODY_MODEL, validPainViewerState, type PainBodySite, type PainBodyPoint } from './pain-body-map-state.ts';

export function painHistoryEntries(entries: SymptomEntry[], patientId: string, today: string): SymptomEntry[] {
  return selectSymptomEntries(entries, patientId, today).filter((entry) => entry.symptom.trim().toLowerCase() === 'pain');
}

export function painHistoryMapState(entry: SymptomEntry): {
  status: 'mapped' | 'written-location' | 'missing-location' | 'unsupported-map';
  selected: PainBodySite[]; points: PainBodyPoint[]; rating: number | null;
} {
  const rating = typeof entry.severity === 'number' && Number.isInteger(entry.severity) && entry.severity >= 0 && entry.severity <= 10 ? entry.severity : null;
  const empty = { selected: [], points: [], rating };
  if (!entry.painBodySites?.length && !entry.painBodyPoints?.length) return { ...empty, status: entry.painLocation?.trim() ? 'written-location' : 'missing-location' };
  const state = { selected: entry.painBodySites ?? [], points: entry.painBodyPoints ?? [], rating, hindi: false };
  if (!validPainViewerState(state) || (state.points.length > 0 && entry.painBodyModel !== PAIN_BODY_MODEL)) return { ...empty, status: 'unsupported-map' };
  return { status: 'mapped', selected: [...state.selected], points: state.points.map((point) => ({ ...point })), rating };
}
