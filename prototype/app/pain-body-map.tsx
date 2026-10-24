'use client';

import { useId, useState } from 'react';
import {
  PAIN_BODY_SITES, painBodySiteName, painBodyRegionPath, togglePainBodySite, visiblePainBodySites,
  type PainBodySite, type PainBodyView,
} from './pain-body-map-state';

export function PainBodyMap({ selected, onChange, hindi }: {
  selected: PainBodySite[]; onChange: (sites: PainBodySite[]) => void; hindi: boolean;
}) {
  const id = useId();
  const [view, setView] = useState<PainBodyView>('front');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const toggle = (site: PainBodySite) => onChange(togglePainBodySite(selected, site));
  return <fieldset className="pain-body-picker">
    <legend>{t('Where is the pain? (optional)', 'दर्द कहाँ है? (ज़रूरी नहीं)')}</legend>
    <div className="pain-body-view symptom-choices">
      {(['front', 'back'] as const).map((side) => <button type="button" key={side} aria-pressed={view === side} onClick={() => setView(side)}>
        {side === 'front' ? t('Front', 'सामने') : t('Back', 'पीछे')}
      </button>)}
      <button type="button" disabled={!selected.length} onClick={() => onChange([])}>{t('Clear selected areas', 'चुनी जगहें हटाएँ')}</button>
    </div>
    <p className="pain-body-side-key">{t('Left and right mean the patient’s own sides.', 'बायाँ और दायाँ मरीज़ के अपने शरीर के हिसाब से है।')}</p>
    <div className="pain-body-layout">
      <svg viewBox="0 0 200 390" className="pain-body-diagram" role="group" aria-labelledby={`${id}-title`}>
        <title id={`${id}-title`}>{view === 'front' ? t('Pain areas, front of the body', 'दर्द की जगहें, शरीर का सामने का हिस्सा') : t('Pain areas, back of the body', 'दर्द की जगहें, शरीर का पीछे का हिस्सा')}</title>
        <text x="16" y="26" className="pain-body-side-label">{view === 'front' ? t('R', 'दायाँ') : t('L', 'बायाँ')}</text>
        <text x="184" y="26" textAnchor="end" className="pain-body-side-label">{view === 'front' ? t('L', 'बायाँ') : t('R', 'दायाँ')}</text>
        {visiblePainBodySites(view).map((site) => <g key={site} role="button" tabIndex={0}
          aria-label={painBodySiteName(site, hindi)} aria-pressed={selected.includes(site)}
          className="pain-body-region" onClick={() => toggle(site)}
          onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggle(site); } }}>
          <title>{painBodySiteName(site, hindi)}</title>
          <path d={painBodyRegionPath(site, view)} />
        </g>)}
      </svg>
      <div className="pain-body-text-options symptom-choices" aria-label={t('Body areas', 'शरीर की जगहें')}>
        {PAIN_BODY_SITES.map((site) => <button type="button" key={site.id} aria-pressed={selected.includes(site.id)} onClick={() => toggle(site.id)}>{painBodySiteName(site.id, hindi)}</button>)}
      </div>
    </div>
    <p className="pain-body-selection" aria-live="polite">{selected.length
      ? selected.map((site) => painBodySiteName(site, hindi)).join(' · ')
      : t('No areas selected', 'कोई जगह नहीं चुनी')}</p>
  </fieldset>;
}
