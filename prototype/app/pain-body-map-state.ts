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
