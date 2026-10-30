'use client';

import { useId } from 'react';
import { CARE_LANGS, careText, type CareLang } from './care-i18n';
import './care-language-select.css';

/** Header language dropdown. A native select keeps it keyboard and screen-reader friendly on every phone. */
export function CareLanguageSelect({ lang, onChange, className = '' }: { lang: CareLang; onChange: (lang: CareLang) => void; className?: string }) {
  const id = useId();
  const label = careText(lang, 'Language', 'भाषा');
  return <span className={`care-language-select ${className}`.trim()}>
    <label className="care-language-select-label" htmlFor={id}>{label}</label>
    <svg className="care-language-select-globe" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.6 3.75 5.6 3.75 9S14.5 18.4 12 21c-2.5-2.6-3.75-5.6-3.75-9S9.5 5.6 12 3Z" /></svg>
    <select id={id} value={lang} title={label} onChange={(event) => onChange(event.target.value as CareLang)}>
      {CARE_LANGS.map((item) => <option key={item.code} value={item.code} lang={item.code} dir={item.dir}>{item.code === 'en' ? item.label : `${item.label} (${item.english})`}</option>)}
    </select>
    <svg className="care-language-select-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
  </span>;
}
