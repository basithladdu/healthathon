'use client';
import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import s from './admin.module.css';
import { DoctorAdmin } from './doctor-admin';
import { ImpactAdmin } from './impact-admin';
import { SurveyAdmin } from './survey-admin';

const TABS = [
  { id: 'survey', label: 'Hospital survey' },
  { id: 'doctors', label: 'Doctor sign-ups' },
  { id: 'impact', label: 'Impact' },
] as const;
type Tab = typeof TABS[number]['id'];
const TAB_KEY = 'sahara-admin-tab';
const isTab = (v: unknown): v is Tab => TABS.some(t => t.id === v);

export function AdminTabs() {
  const [tab, setTab] = useState<Tab>('survey');
  // A tab stays mounted once opened, so switching back does not reload it.
  const [opened, setOpened] = useState<Set<Tab>>(() => new Set(['survey']));
  const buttons = useRef<Record<string, HTMLButtonElement | null>>({});
  const choose = (next: Tab, focus = false) => {
    setTab(next); setOpened(o => (o.has(next) ? o : new Set([...o, next])));
    try { window.localStorage.setItem(TAB_KEY, next); } catch { /* ignore */ }
    if (focus) buttons.current[next]?.focus();
  };
  useEffect(() => {
    const t = setTimeout(() => { let saved: string | null = null; try { saved = window.localStorage.getItem(TAB_KEY); } catch { /* ignore */ } if (isTab(saved) && saved !== 'survey') choose(saved); }, 0);
    return () => clearTimeout(t);
  }, []);
  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    const i = TABS.findIndex(t => t.id === tab);
    const next = e.key === 'ArrowRight' ? (i + 1) % TABS.length : e.key === 'ArrowLeft' ? (i - 1 + TABS.length) % TABS.length : e.key === 'Home' ? 0 : e.key === 'End' ? TABS.length - 1 : -1;
    if (next < 0) return; e.preventDefault(); choose(TABS[next].id, true);
  }
  return <>
    <div className={s.tabs} role="tablist" aria-label="Admin sections" onKeyDown={onKey}>
      {TABS.map(t => <button key={t.id} ref={el => { buttons.current[t.id] = el; }} type="button" role="tab" id={`tab-${t.id}`} aria-controls={`panel-${t.id}`} aria-selected={tab === t.id} tabIndex={tab === t.id ? 0 : -1} className={s.tab} onClick={() => choose(t.id)}>{t.label}</button>)}
    </div>
    {TABS.map(t => <div key={t.id} role="tabpanel" id={`panel-${t.id}`} aria-labelledby={`tab-${t.id}`} hidden={tab !== t.id}>
      {opened.has(t.id) && (t.id === 'survey' ? <SurveyAdmin /> : t.id === 'doctors' ? <DoctorAdmin /> : <ImpactAdmin />)}
    </div>)}
  </>;
}
