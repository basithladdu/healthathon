// Groups current medicines by time of day (mirrors Android CareMedicineList.kt MedicineTimeGroups).
// A medicine taken at several times is listed ONCE, under its earliest dose time.
import type { CareEvent } from './care-calendar-state.ts';

export type MedicineSlot = 'morning' | 'afternoon' | 'evening' | 'night' | 'as-needed' | 'other';
export const MEDICINE_SLOTS: readonly MedicineSlot[] = ['morning', 'afternoon', 'evening', 'night', 'as-needed', 'other'];

/** What grouping needs to know about one medicine. `times` are its dose times ("08:00"), empty when it has none. */
export type MedicineSlotInput = { id: string; name: string; times: string[]; asNeeded: boolean };

/** The search field shows only for a long list. */
export const MEDICINE_SEARCH_ABOVE = 8;

const AS_NEEDED = /(?<![A-Za-z])(?:as needed|when needed|if needed|only when|when required|as required|sos|prn|stat)(?![A-Za-z])/i;
export function isAsNeededText(text: string): boolean { return AS_NEEDED.test(text); }

/** Morning 05:00 to 11:59, afternoon 12:00 to 16:59, evening 17:00 to 20:59, night 21:00 to 04:59. */
export function slotOfTime(time: string): MedicineSlot | null {
  const hour = Number.parseInt(time.slice(0, 2), 10);
  if (!Number.isInteger(hour) || hour < 0 || hour > 23) return null;
  if (hour >= 5 && hour <= 11) return 'morning';
  if (hour >= 12 && hour <= 16) return 'afternoon';
  if (hour >= 17 && hour <= 20) return 'evening';
  return 'night';
}

function earliest(times: string[]): string | undefined {
  return times.filter((time) => time.length >= 4).sort()[0];
}

/** By earliest dose time; else "as needed" when it says so; else "other" (weekly, or no time). */
export function slotOf(input: MedicineSlotInput): MedicineSlot {
  const first = earliest(input.times);
  return (first ? slotOfTime(first) : null) ?? (input.asNeeded ? 'as-needed' : 'other');
}

/** Non-empty groups in day order; inside a group by earliest time then name. Each medicine appears exactly once. */
export function groupMedicines<T extends MedicineSlotInput>(inputs: T[]): { slot: MedicineSlot; medicines: T[] }[] {
  const key = (input: MedicineSlotInput) => earliest(input.times) ?? '99:99';
  const sorted = [...inputs].sort((a, b) => key(a).localeCompare(key(b)) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  return MEDICINE_SLOTS
    .map((slot) => ({ slot, medicines: sorted.filter((input) => slotOf(input) === slot) }))
    .filter((group) => group.medicines.length > 0);
}

/** Medicines whose name contains the query (ignoring case and surrounding spaces); everything when blank. */
export function filterMedicinesByName<T extends MedicineSlotInput>(inputs: T[], query: string): T[] {
  const q = query.trim().toLowerCase();
  return q ? inputs.filter((input) => input.name.toLowerCase().includes(q)) : inputs;
}

/** One medicine on the web: all of its dose events (one per time) merged into one entry. */
export type MedicineEntry = MedicineSlotInput & { events: CareEvent[] };

/** Merge dose events into one entry per medicine (same regimen, else same title). */
export function medicineEntries(events: readonly CareEvent[], regimenOf: (event: CareEvent) => string | undefined = () => undefined): MedicineEntry[] {
  const byKey = new Map<string, CareEvent[]>();
  for (const event of events) {
    const key = regimenOf(event) ?? `title:${event.title.trim().toLowerCase()}`;
    byKey.set(key, [...(byKey.get(key) ?? []), event]);
  }
  return [...byKey.entries()].map(([key, list]) => {
    const ordered = [...list].sort((a, b) => a.time.localeCompare(b.time));
    const asNeeded = ordered.some((event) => isAsNeededText(event.instructions));
    return {
      id: key, name: ordered[0].title,
      // "As needed" medicines have no fixed time of day; they go under "As needed", not under a clock time.
      times: asNeeded ? [] : [...new Set(ordered.map((event) => event.time).filter(Boolean))],
      asNeeded, events: ordered,
    };
  });
}

export function slotTitle(slot: MedicineSlot, t: (en: string, hi: string) => string): string {
  switch (slot) {
    case 'morning': return t('Morning', 'सुबह');
    case 'afternoon': return t('Afternoon', 'दोपहर');
    case 'evening': return t('Evening', 'शाम');
    case 'night': return t('Night', 'रात');
    case 'as-needed': return t('As needed', 'ज़रूरत पड़ने पर');
    default: return t('Other', 'अन्य');
  }
}
