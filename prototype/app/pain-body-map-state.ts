export const PAIN_BODY_SITES = [
  { id: 'head', en: 'Head', hi: 'सिर' },
  { id: 'neck', en: 'Neck', hi: 'गर्दन' },
  { id: 'chest', en: 'Chest', hi: 'छाती' },
  { id: 'abdomen', en: 'Abdomen', hi: 'पेट' },
  { id: 'upper-back', en: 'Upper back', hi: 'पीठ का ऊपरी हिस्सा' },
  { id: 'lower-back', en: 'Lower back', hi: 'पीठ का निचला हिस्सा' },
  { id: 'left-shoulder', en: 'Left shoulder', hi: 'बायाँ कंधा' },
  { id: 'right-shoulder', en: 'Right shoulder', hi: 'दायाँ कंधा' },
  { id: 'left-arm', en: 'Left arm / hand', hi: 'बायाँ हाथ' },
  { id: 'right-arm', en: 'Right arm / hand', hi: 'दायाँ हाथ' },
  { id: 'left-leg', en: 'Left leg / foot', hi: 'बायाँ पैर' },
  { id: 'right-leg', en: 'Right leg / foot', hi: 'दायाँ पैर' },
] as const;

export type PainBodySite = (typeof PAIN_BODY_SITES)[number]['id'];
export type PainBodyView = 'front' | 'back';
export const PAIN_BODY_MODEL = 'sahara-body-v1' as const;
export type PainBodyPoint = { site: PainBodySite; x: number; y: number; z: number };

export function isPainBodyPoint(value: unknown): value is PainBodyPoint {
  if (!value || typeof value !== 'object') return false;
  const point = value as Record<string, unknown>;
  return isPainBodySite(point.site) && typeof point.x === 'number' && typeof point.y === 'number' && typeof point.z === 'number'
    && [point.x, point.y, point.z].every(Number.isFinite)
    && Math.abs(point.x) <= 1 && point.y >= 0 && point.y <= 1 && Math.abs(point.z) <= 1;
}

export function quantizePainBodyPoint(point: PainBodyPoint): PainBodyPoint {
  return { site: point.site, x: Math.round(point.x * 1000) / 1000, y: Math.round(point.y * 1000) / 1000, z: Math.round(point.z * 1000) / 1000 };
}

export function painBodyPointContext(points: readonly PainBodyPoint[] = []): string {
  return points.map(quantizePainBodyPoint).sort((a, b) => a.site.localeCompare(b.site))
    .map((point) => `${point.site}:${point.x},${point.y},${point.z}`).join('|');
}

export function isPainBodySite(value: unknown): value is PainBodySite {
  return typeof value === 'string' && PAIN_BODY_SITES.some((site) => site.id === value);
}

export function painBodySiteName(value: PainBodySite, hindi = false): string {
  const site = PAIN_BODY_SITES.find((item) => item.id === value);
  return site ? site[hindi ? 'hi' : 'en'] : value;
}

export function togglePainBodySite(selected: readonly PainBodySite[], site: PainBodySite): PainBodySite[] {
  return selected.includes(site) ? selected.filter((value) => value !== site) : [...selected, site];
}

// Normalized model coordinates: x is patient's left (+) / right (-), y is feet 0 to head 1,
// z is front (+) / back (-). These are broad location groups, not individual muscles.
export function classifyPainBodyPoint(point: { x: number; y: number; z: number }): PainBodySite | null {
  const { x, y, z } = point;
  if (![x, y, z].every(Number.isFinite) || Math.abs(x) > 1.05 || y < -0.05 || y > 1.05 || Math.abs(z) > 1.05) return null;
  if (y >= 0.88) return 'head';
  if (y >= 0.82 && Math.abs(x) < 0.35) return 'neck';
  const side = x >= 0 ? 'left' : 'right';
  if (y < 0.44) return `${side}-leg`;
  if (Math.abs(x) > 0.38) return y >= 0.72 ? `${side}-shoulder` : `${side}-arm`;
  if (z >= 0) return y >= 0.64 ? 'chest' : 'abdomen';
  return y >= 0.64 ? 'upper-back' : 'lower-back';
}

export function validPainViewerState(value: unknown): value is { selected: PainBodySite[]; rating: number | null; hindi: boolean; points?: PainBodyPoint[] } {
  if (!value || typeof value !== 'object') return false;
  const state = value as Record<string, unknown>;
  return Array.isArray(state.selected) && state.selected.length <= PAIN_BODY_SITES.length
    && state.selected.every(isPainBodySite) && new Set(state.selected).size === state.selected.length
    && (state.rating === null || (typeof state.rating === 'number' && Number.isInteger(state.rating) && state.rating >= 0 && state.rating <= 10))
    && typeof state.hindi === 'boolean'
    && (state.points === undefined || (Array.isArray(state.points) && state.points.length <= PAIN_BODY_SITES.length
      && state.points.every(isPainBodyPoint) && new Set(state.points.map((point) => point.site)).size === state.points.length
      && state.points.every((point) => (state.selected as unknown[]).includes(point.site))));
}

// Positions use the patient's own left and right. The back view reverses the screen side.
export function painBodyRegionCenter(site: PainBodySite, view: PainBodyView): { x: number; y: number } {
  const ownLeft = site.startsWith('left-');
  const limbX = view === 'front' ? (ownLeft ? 157 : 43) : (ownLeft ? 43 : 157);
  if (site.endsWith('-shoulder')) return { x: limbX + (limbX < 100 ? 19 : -19), y: 86 };
  if (site.endsWith('-arm')) return { x: limbX, y: 169 };
  if (site.endsWith('-leg')) return { x: view === 'front' ? (ownLeft ? 119 : 81) : (ownLeft ? 81 : 119), y: 293 };
  return { x: 100, y: site === 'head' ? 34 : site === 'neck' ? 67 : ['chest', 'upper-back'].includes(site) ? 111 : 166 };
}

export function visiblePainBodySites(view: PainBodyView): PainBodySite[] {
  return PAIN_BODY_SITES.map((site) => site.id).filter((site) => view === 'front'
    ? site !== 'upper-back' && site !== 'lower-back'
    : site !== 'chest' && site !== 'abdomen');
}

// Original, deliberately broad body regions; this is a location picker, not a diagnostic diagram.
export function painBodyRegionPath(site: PainBodySite, view: PainBodyView): string {
  const { x } = painBodyRegionCenter(site, view);
  if (site === 'head') return 'M100 9 C126 9 128 55 112 60 L88 60 C72 55 74 9 100 9 Z';
  if (site === 'neck') return 'M90 60 L110 60 L112 78 L88 78 Z';
  if (site === 'chest' || site === 'upper-back') return 'M78 79 Q100 85 122 79 L128 135 L72 135 Z';
  if (site === 'abdomen' || site === 'lower-back') return 'M72 138 L128 138 L126 198 Q100 209 74 198 Z';
  if (site.endsWith('-shoulder')) return x < 100
    ? 'M76 79 Q60 72 47 91 L44 111 L69 116 Z'
    : 'M124 79 Q140 72 153 91 L156 111 L131 116 Z';
  if (site.endsWith('-arm')) return x < 100
    ? 'M43 114 L68 119 L58 172 L50 225 Q47 240 34 237 L28 228 L35 171 Z'
    : 'M157 114 L132 119 L142 172 L150 225 Q153 240 166 237 L172 228 L165 171 Z';
  return x < 100
    ? 'M74 203 L98 210 L96 274 L91 356 L89 371 L64 371 L66 356 L64 277 Z'
    : 'M126 203 L102 210 L104 274 L109 356 L111 371 L136 371 L134 356 L136 277 Z';
}
