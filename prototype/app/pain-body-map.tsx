'use client';

import { useEffect, useId, useRef, useState } from 'react';
import {
  PAIN_BODY_SITES, isPainBodySite, isPainBodyPoint, quantizePainBodyPoint, painBodySiteName, painBodyRegionPath, togglePainBodySite, visiblePainBodySites,
  type PainBodySite, type PainBodyView, type PainBodyPoint,
} from './pain-body-map-state';

const noChange = () => {};

export function PainBodyMap({ selected, points, onChange = noChange, onPointsChange = noChange, hindi, rating, readOnly = false }: {
  selected: PainBodySite[]; points: PainBodyPoint[]; onChange?: (sites: PainBodySite[]) => void; onPointsChange?: (points: PainBodyPoint[]) => void; hindi: boolean; rating: number | null; readOnly?: boolean;
}) {
  const id = useId();
  const frame = useRef<HTMLIFrameElement>(null);
  const [viewerStatus, setViewerStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [view, setView] = useState<PainBodyView>('front');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const toggle = (site: PainBodySite) => {
    if (readOnly) return;
    const next = togglePainBodySite(selected, site);
    onChange(next); onPointsChange(points.filter((point) => next.includes(point.site)));
  };
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.current?.contentWindow || event.origin !== window.location.origin
        || !event.data || typeof event.data !== 'object') return;
      if (event.data.type === 'ready') setViewerStatus('ready');
      if (event.data.type === 'error') setViewerStatus('error');
      if (!readOnly && event.data.type === 'pick' && isPainBodySite(event.data.site) && isPainBodyPoint({ ...event.data.point, site: event.data.site })) {
        const point = quantizePainBodyPoint({ ...event.data.point, site: event.data.site });
        onChange(selected.includes(point.site) ? selected : [...selected, point.site]);
        onPointsChange([...points.filter((item) => item.site !== point.site), point]);
      }
      if (!readOnly && event.data.type === 'toggle' && isPainBodySite(event.data.site)) {
        const next = togglePainBodySite(selected, event.data.site);
        onChange(next); onPointsChange(points.filter((point) => next.includes(point.site)));
      }
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, [onChange, onPointsChange, selected, points, readOnly]);
  useEffect(() => {
    frame.current?.contentWindow?.postMessage({ type: 'state', selected, points, rating, hindi, readOnly }, window.location.origin);
  }, [selected, points, rating, hindi, viewerStatus, readOnly]);
  function chooseView(side: PainBodyView) {
    setView(side);
    frame.current?.contentWindow?.postMessage({ type: 'view', view: side }, window.location.origin);
  }
  return <fieldset className="pain-body-picker">
    <legend>{readOnly ? t('Saved pain areas', 'सेव की गई दर्द की जगहें') : t('Where is the pain? (optional)', 'दर्द कहाँ है? (ज़रूरी नहीं)')}</legend>
    <div className="pain-body-view symptom-choices">
      {(['front', 'back'] as const).map((side) => <button type="button" key={side} aria-pressed={viewerStatus === 'error' ? view === side : undefined} onClick={() => chooseView(side)}>
        {side === 'front' ? t('Front', 'सामने') : t('Back', 'पीछे')}
      </button>)}
      {!readOnly && <button type="button" disabled={!selected.length} onClick={() => { onChange([]); onPointsChange([]); }}>{t('Clear selected areas', 'चुनी जगहें हटाएँ')}</button>}
    </div>
    <p className="pain-body-side-key">{t('Left and right mean the patient’s own sides.', 'बायाँ और दायाँ मरीज़ के अपने शरीर के हिसाब से है।')}</p>
    <div className="pain-body-layout">
      <div className="pain-body-stage">
        {viewerStatus !== 'error' && <iframe ref={frame} src="/pain-body-viewer/index.html"
          title={readOnly ? t('Rotate the saved pain map', 'सेव किया दर्द का मैप घुमाएँ') : t('Rotate and select a pain location', 'शरीर घुमाकर दर्द की जगह चुनें')} className="pain-body-frame" onError={() => setViewerStatus('error')}
          onLoad={() => frame.current?.contentWindow?.postMessage({ type: 'state', selected, points, rating, hindi, readOnly }, window.location.origin)} />}
        {viewerStatus === 'loading' && <p className="pain-viewer-loading" role="status">{t('Loading 3D body…', '3D शरीर खुल रहा है…')}</p>}
        {viewerStatus === 'error' && <p role="status">{readOnly ? t('3D is unavailable. The saved areas remain on this diagram.', '3D नहीं खुला। सेव की गई जगहें इस चित्र में हैं।') : t('3D is unavailable. Choose on this diagram or use the buttons.', '3D नहीं खुला। इस चित्र या बटनों से जगह चुनें।')}</p>}
        {viewerStatus === 'error' && <svg viewBox="0 0 200 390" className="pain-body-diagram" role="group" aria-labelledby={`${id}-title`}>
        <title id={`${id}-title`}>{view === 'front' ? t('Pain areas, front of the body', 'दर्द की जगहें, शरीर का सामने का हिस्सा') : t('Pain areas, back of the body', 'दर्द की जगहें, शरीर का पीछे का हिस्सा')}</title>
        <text x="16" y="26" className="pain-body-side-label">{view === 'front' ? t('R', 'दायाँ') : t('L', 'बायाँ')}</text>
        <text x="184" y="26" textAnchor="end" className="pain-body-side-label">{view === 'front' ? t('L', 'बायाँ') : t('R', 'दायाँ')}</text>
        {visiblePainBodySites(view).map((site) => <g key={site} role={readOnly ? 'img' : 'button'} tabIndex={readOnly ? undefined : 0}
          aria-label={painBodySiteName(site, hindi)} aria-pressed={readOnly ? undefined : selected.includes(site)} data-selected={selected.includes(site)}
          className="pain-body-region" onClick={readOnly ? undefined : () => toggle(site)}
          onKeyDown={readOnly ? undefined : (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggle(site); } }}>
          <title>{painBodySiteName(site, hindi)}</title>
          <path d={painBodyRegionPath(site, view)} />
        </g>)}
      </svg>}
      </div>
      <div className="pain-body-text-options symptom-choices" aria-label={t('Body areas', 'शरीर की जगहें')}>
        {readOnly ? selected.map((site) => <span className="pain-body-saved-site" key={site}>{painBodySiteName(site, hindi)}</span>) : PAIN_BODY_SITES.map((site) => <button type="button" key={site.id} aria-pressed={selected.includes(site.id)} onClick={() => toggle(site.id)}>{painBodySiteName(site.id, hindi)}</button>)}
      </div>
    </div>
    <p className="pain-body-side-key">{rating === null
      ? readOnly ? t('No numeric rating was saved. The map stays neutral.', 'अंक सेव नहीं किया गया था। मैप का रंग नहीं बदला गया है।') : t('Choose a pain rating to colour the selected areas.', 'चुनी जगहों को रंग देने के लिए दर्द का अंक चुनें।')
      : t(`Pain ${rating}/10 applies to every selected area. Colour shows the reported rating.`, `दर्द ${rating}/10 हर चुनी जगह के लिए है। रंग बताए गए अंक को दिखाता है।`)}</p>
    <p className="pain-body-selection" aria-live="polite">{selected.length
      ? selected.map((site) => painBodySiteName(site, hindi)).join(' · ')
      : t('No areas selected', 'कोई जगह नहीं चुनी')}</p>
  </fieldset>;
}
