'use client';

import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import type { SummaryRelease } from './summary-state';
import { CareRouteLink } from './care-route-link';
import type { CareView } from './care-routes';
import { careSectionFor } from './care-section-pages';
import { SaharaLogo } from './sahara-logo';

type CareNavIconName = 'home' | 'documents' | 'support' | 'space' | 'visit' | 'record' | 'review' | 'history' | 'care';

const CARE_NAV_ICON_PATHS: Record<CareNavIconName, ReactNode> = {
  home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5.5 9.5V21h13V9.5" /><path d="M9.5 21v-6h5v6" /></>,
  documents: <><path d="M6 2.75h8l4 4V21.25H6z" /><path d="M14 2.75v4h4" /><path d="M9 12h6M9 16h6" /></>,
  support: <><path d="M12 21s7-4.35 7-10.1A4.1 4.1 0 0 0 12 8a4.1 4.1 0 0 0-7 2.9C5 16.65 12 21 12 21Z" /><path d="M12 8c.55-2.15 2.05-3.5 4.35-3.5" /></>,
  space: <><circle cx="12" cy="8" r="3.25" /><path d="M5.5 20c.6-4 2.75-6 6.5-6s5.9 2 6.5 6" /><path d="M4 5.5 2.5 7 4 8.5M20 5.5 21.5 7 20 8.5" /></>,
  visit: <><path d="M4 7.5h16v12H4z" /><path d="M9 7.5V5h6v2.5M4 12h16" /><path d="M10 15h4" /></>,
  record: <><path d="M6 3h12v18H6z" /><path d="M9 7h6M9 11h6M9 15h4" /></>,
  review: <><circle cx="12" cy="12" r="9" /><path d="m8.5 12 2.25 2.25L15.75 9" /></>,
  history: <><path d="M4 5v5h5" /><path d="M4.7 15.5A8.5 8.5 0 1 0 5 8" /><path d="M12 7.5V12l3 2" /></>,
  care: <><path d="M12 21s7-4.35 7-10.1A4.1 4.1 0 0 0 12 8a4.1 4.1 0 0 0-7 2.9C5 16.65 12 21 12 21Z" /><path d="M9 12h6M12 9v6" /></>,
};

function CareNavIcon({ name }: { name: CareNavIconName }) {
  return <svg className="simple-care-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{CARE_NAV_ICON_PATHS[name]}</svg>;
}

type CareRole = 'family' | 'patient' | 'doctor';

function CareRoleSwitcher({ role, hindi, onRole }: { role: CareRole; hindi: boolean; onRole: (role: CareRole) => void }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const roles = [
    { value: 'patient', label: t('Patient', 'मरीज़') },
    { value: 'family', label: t('Family', 'परिवार') },
    { value: 'doctor', label: t('Doctor', 'डॉक्टर') },
  ] as const;
  const current = roles.find(({ value }) => value === role)?.label ?? roles[0].label;

  useEffect(() => {
    if (!open) return;
    const closeOutside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      setOpen(false);
      trigger.current?.focus();
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  function focusOption(position: 'first' | 'last') {
    window.requestAnimationFrame(() => {
      const options = menu.current?.querySelectorAll<HTMLButtonElement>('[role="option"]');
      options?.[position === 'first' ? 0 : options.length - 1]?.focus();
    });
  }

  function openWithKeyboard(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    event.preventDefault();
    setOpen(true);
    focusOption(event.key === 'ArrowDown' ? 'first' : 'last');
  }

  function moveWithinMenu(event: KeyboardEvent<HTMLDivElement>) {
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    const options = Array.from(menu.current?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? []);
    if (!options.length) return;
    event.preventDefault();
    const currentIndex = options.indexOf(document.activeElement as HTMLButtonElement);
    const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
      : event.key === 'ArrowDown' ? (currentIndex + 1 + options.length) % options.length
        : (currentIndex - 1 + options.length) % options.length;
    options[nextIndex].focus();
  }

  function choose(nextRole: CareRole) {
    onRole(nextRole);
    setOpen(false);
    window.requestAnimationFrame(() => trigger.current?.focus());
  }

  return <div className={`simple-care-role-switcher${open ? ' is-open' : ''}`} ref={root}>
    <button ref={trigger} type="button" className="simple-care-role-trigger" aria-label={t(`View as ${current}`, `${current} का पेज`)} aria-haspopup="listbox" aria-expanded={open} aria-controls={menuId} onClick={() => setOpen((value) => !value)} onKeyDown={openWithKeyboard}>
      <span>{current}</span>
      <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5.5 7.5 4.5 4.5 4.5-4.5" /></svg>
    </button>
    {open ? <div id={menuId} ref={menu} className="simple-care-role-menu" role="listbox" aria-label={t('View as', 'किसका पेज')} onKeyDown={moveWithinMenu}>
      {roles.map(({ value, label }) => <button type="button" role="option" aria-selected={role === value} key={value} onClick={() => choose(value)}><span>{label}</span>{role === value ? <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m4.5 10 3.5 3.5 7.5-7.5" /></svg> : null}</button>)}
    </div> : null}
  </div>;
}

export function SimpleCareShell({ children, view, role, hindi, saveStatus, onNavigate, onRole, onLanguage, onSignOut }: {
  children: ReactNode; view: string; role: CareRole; hindi: boolean;
  saveStatus: 'saving' | 'saved' | 'unavailable';
  onNavigate: (view: CareView) => void;
  onRole: (role: CareRole) => void; onLanguage: () => void;
  onSignOut: () => void;
}) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const links = role === 'doctor' ? [
    { key: 'home', label: t('Overview', 'एक नज़र'), shortLabel: t('Home', 'होम'), icon: 'home' },
    { key: 'doctor-record', label: t('Record', 'बातचीत'), shortLabel: t('Record', 'नोट'), icon: 'record' },
    { key: 'doctor-review', label: t('Review & sign', 'जाँचें और साइन करें'), shortLabel: t('Review', 'जाँच'), icon: 'review' },
    { key: 'doctor-history', label: t('History', 'पुराने नोट'), shortLabel: t('History', 'इतिहास'), icon: 'history' },
    { key: 'care-near-me', label: t('Find care', 'देखभाल ढूँढें'), shortLabel: t('Care', 'देखभाल'), icon: 'care' },
  ] as const : [
    { key: 'daily-care', label: t('Home', 'होम'), shortLabel: t('Home', 'होम'), icon: 'home' },
    { key: 'clinical-documents', label: t('My documents', 'दस्तावेज़'), shortLabel: t('Docs', 'काग़ज़'), icon: 'documents' },
    { key: 'access-care', label: t('Find support', 'मदद ढूँढें'), shortLabel: t('Support', 'मदद'), icon: 'support' },
    { key: 'my-space', label: t('My space', 'मेरी जगह'), shortLabel: t('My space', 'मेरी जगह'), icon: 'space' },
    { key: 'doctor-pack', label: t('Visit pack', 'मुलाक़ात की फ़ाइल'), shortLabel: t('Visit', 'मुलाक़ात'), icon: 'visit' },
  ] as const;
  return <div className="simple-care-app">
    <header className="simple-care-header">
      <CareRouteLink className="simple-care-brand" view={role === 'doctor' ? 'home' : 'daily-care'} onOpen={() => onNavigate(role === 'doctor' ? 'home' : 'daily-care')} aria-label={t('Sahara - A Palliative Care Companion', 'सहारा - पैलिएटिव केयर साथी')}>
        <SaharaLogo className="simple-care-brand-logo" priority />
        <span className="simple-care-brand-copy"><span className="simple-care-brand-name">{t('Sahara', 'सहारा')}</span><span className="simple-care-brand-tagline">{t('- A Palliative Care Companion', '- पैलिएटिव केयर साथी')}</span></span>
      </CareRouteLink>
      <div className="simple-care-switches"><button type="button" onClick={onLanguage} lang={hindi ? 'en' : 'hi'}>{hindi ? 'English' : 'हिन्दी'}</button>
        <CareRoleSwitcher role={role} hindi={hindi} onRole={onRole} />
        <button type="button" onClick={onSignOut}>{t('Sign out', 'साइन आउट')}</button>
      </div>
    </header>
    <nav className="simple-care-nav" aria-label={t('Main navigation', 'मुख्य मेन्यू')}>{links.map(({ key, label, shortLabel, icon }) => <CareRouteLink className="simple-care-nav-link" key={key} view={key} aria-label={label} aria-current={(role === 'doctor' ? view : careSectionFor(view)) === key ? 'page' : undefined} onOpen={() => onNavigate(key)}><CareNavIcon name={icon} /><span className="simple-care-nav-label">{label}</span><span className="simple-care-nav-short-label">{shortLabel}</span></CareRouteLink>)}</nav>
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
    const text = [`Sahara — A Palliative Care Companion`, `Care Note for ${patientName}`, `Version ${release?.number} · ${release?.physician}`, ...fields.map(({ label, value }) => `${label}\n${value}`), 'A conversation note, not a prescription or legal directive.'].join('\n\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'sahara-care-note.txt'; link.click(); URL.revokeObjectURL(url);
  }
  return <section className="simple-care-note"><div className="family-workspace-heading"><h1>{patientName.split(' ')[0]}{t('’s Care Note', ' का नोट')}</h1>{release && <button type="button" className="secondary-button" onClick={download}>{t('Save a copy', 'कॉपी डाउनलोड करें')}</button>}</div>
    {release ? <><p className="care-note-meta">{release.physician} · {new Date(release.releasedAt).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })} · {t('Version', 'संस्करण')} {release.number}</p>
      <dl className="care-note-fields">{fields.filter(({ value }) => value !== 'Not stated in this conversation.').map(({ label, value }) => <div key={label}><dt>{hindi ? labels[label] ?? label : label}</dt><dd>{value}</dd></div>)}</dl></> : <p>{t('There is no approved note here yet.', 'यहाँ अभी डॉक्टर का मंज़ूर किया हुआ नोट नहीं है।')}</p>}
    <button type="button" className="care-text-button" onClick={onPrepare}>{t('What would you like to talk about?', 'आप किस बारे में बात करना चाहते हैं?')} →</button>
  </section>;
}
