'use client';

import type { ReactNode } from 'react';
import type { SummaryRelease } from './summary-state';
import { CareRouteLink } from './care-route-link';
import type { CareView } from './care-routes';
import { careSectionFor } from './care-section-pages';

export function SimpleCareShell({ children, view, role, hindi, saveStatus, onNavigate, onRole, onLanguage, onSignOut }: {
  children: ReactNode; view: string; role: 'family' | 'patient' | 'doctor'; hindi: boolean;
  saveStatus: 'saving' | 'saved' | 'unavailable';
  onNavigate: (view: CareView) => void;
  onRole: (role: 'family' | 'patient' | 'doctor') => void; onLanguage: () => void;
  onSignOut: () => void;
}) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const links = role === 'doctor' ? [{ key: 'home', label: t('Overview', 'एक नज़र') }, { key: 'doctor-record', label: t('Record', 'बातचीत') }, { key: 'doctor-review', label: t('Review & sign', 'जाँचें और साइन करें') }, { key: 'doctor-history', label: t('History', 'पुराने नोट') }, { key: 'care-near-me', label: t('Find care', 'देखभाल ढूँढें') }] as const
    : [{ key: 'daily-care', label: t('Home', 'होम') }, { key: 'clinical-documents', label: t('My documents', 'दस्तावेज़') }, { key: 'access-care', label: t('Find support', 'मदद') }, { key: 'my-space', label: t('My space', 'मेरी जगह') }, { key: 'doctor-pack', label: t('Visit pack', 'मुलाक़ात की फ़ाइल') }] as const;
  return <div className="simple-care-app">
    <header className="simple-care-header">
      <CareRouteLink className="simple-care-brand" view={role === 'doctor' ? 'home' : 'daily-care'} onOpen={() => onNavigate(role === 'doctor' ? 'home' : 'daily-care')}>{t('Saathi', 'साथी')}</CareRouteLink>
      <div className="simple-care-switches"><button type="button" onClick={onLanguage} lang={hindi ? 'en' : 'hi'}>{hindi ? 'English' : 'हिन्दी'}</button>
        <select aria-label={t('View as', 'किसका पेज')} value={role} onChange={(event) => onRole(event.target.value as typeof role)}>
          <option value="patient">{t('Patient', 'मरीज़')}</option><option value="family">{t('Family', 'परिवार')}</option><option value="doctor">{t('Doctor', 'डॉक्टर')}</option>
        </select>
        <button type="button" onClick={onSignOut}>{t('Sign out', 'साइन आउट')}</button>
      </div>
    </header>
    <nav className="simple-care-nav" aria-label={t('Main navigation', 'मुख्य मेन्यू')}>{links.map(({ key, label }) => <CareRouteLink key={key} view={key} aria-current={(role === 'doctor' ? view : careSectionFor(view)) === key ? 'page' : undefined} onOpen={() => onNavigate(key)}>{label}</CareRouteLink>)}</nav>
    <main key={view} className="simple-care-content" lang={hindi ? 'hi' : 'en'}>{children}</main>
    {saveStatus === 'unavailable' && <p className="care-save-error" role="alert">{t('Could not save your latest changes. Download a copy before closing.', 'नए बदलाव सेव नहीं हुए। बंद करने से पहले कॉपी डाउनलोड करें।')}</p>}
  </div>;
}

export function FamilyCareNote({ patientName, release, fields, onPrepare, hindi }: {
  patientName: string; release: SummaryRelease | null | undefined; fields: Array<{ label: string; value: string }>; onPrepare: () => void; hindi: boolean;
}) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const labels: Record<string, string> = { 'What matters to you': 'आपके लिए क्या ज़रूरी है', 'Who was there': 'कौन साथ था', 'What you discussed': 'क्या बात हुई', 'Still to discuss': 'क्या बात करना बाकी है', 'Next steps': 'आगे क्या करना है', Summary: 'नोट' };
  function download() {
    const text = [`Saathi — Care Note for ${patientName}`, `Version ${release?.number} · ${release?.physician}`, ...fields.map(({ label, value }) => `${label}\n${value}`), 'A conversation note, not a prescription or legal directive.'].join('\n\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'saathi-care-note.txt'; link.click(); URL.revokeObjectURL(url);
  }
  return <section className="simple-care-note"><div className="family-workspace-heading"><h1>{patientName.split(' ')[0]}{t('’s Care Note', ' का नोट')}</h1>{release && <button type="button" className="secondary-button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button>}</div>
    {release ? <><p className="care-note-meta">{release.physician} · {new Date(release.releasedAt).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })} · {t('Version', 'संस्करण')} {release.number}</p>
      <dl className="care-note-fields">{fields.filter(({ value }) => value !== 'Not stated in this conversation.').map(({ label, value }) => <div key={label}><dt>{hindi ? labels[label] ?? label : label}</dt><dd>{value}</dd></div>)}</dl></> : <p>{t('There is no approved note here yet.', 'यहाँ अभी डॉक्टर का मंज़ूर किया हुआ नोट नहीं है।')}</p>}
    <button type="button" className="care-text-button" onClick={onPrepare}>{t('What would you like to talk about?', 'आप किस बारे में बात करना चाहते हैं?')} →</button>
  </section>;
}
