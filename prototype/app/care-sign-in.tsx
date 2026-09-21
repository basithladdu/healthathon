'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { CareArt, type CareArtKind } from './care-art';
import { CareLoading } from './care-loading';
import './care-sign-in.css';

export type CareSignInInput = { role: 'family' | 'patient' | 'doctor'; patientId: string; name: string };
export type CareSignInProps = {
  patients: Array<{ id: string; name: string }>;
  hindi: boolean;
  onLanguage: () => void;
  onSignIn: (input: CareSignInInput) => void;
};

export function CareSignIn({ patients, hindi, onLanguage, onSignIn }: CareSignInProps) {
  const id = useId();
  const [role, setRole] = useState<CareSignInInput['role']>('family');
  const [selectedId] = useState(patients[0]?.id ?? '');
  const [identifier, setIdentifier] = useState('');
  const [error, setError] = useState<'abha' | 'doctor' | null>(null);
  const [signingIn, setSigningIn] = useState(false);
  const identifierInput = useRef<HTMLInputElement>(null);
  const submitting = useRef(false);
  const loadingTimer = useRef<number | null>(null);
  const patient = patients.find((item) => item.id === selectedId) ?? patients[0];
  const name = role === 'doctor' ? 'Dr Sujay' : role === 'patient' ? patient?.name ?? ''
    : patient && /\bmeera\b/i.test(patient.name) ? 'Kavya Raghavan' : 'Family member';
  const t = (en: string, hi: string) => hindi ? hi : en;
  const roleArt: Record<CareSignInInput['role'], CareArtKind> = {
    family: 'home-help', patient: 'cancer-overview', doctor: 'doctor-pack',
  };

  useEffect(() => () => {
    if (loadingTimer.current !== null) window.clearTimeout(loadingTimer.current);
  }, []);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !patient || !name.trim()) return;
    const value = identifier.trim();
    const valid = role === 'doctor'
      ? /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || /^(?:\+?91)?[6-9]\d{9}$/.test(value.replace(/[\s()-]/g, ''))
      : /^\d{14}$/.test(value.replace(/[\s-]/g, ''));
    if (!valid) {
      setError(role === 'doctor' ? 'doctor' : 'abha');
      identifierInput.current?.focus();
      return;
    }
    const input: CareSignInInput = { role, patientId: patient.id, name: name.trim() };
    submitting.current = true;
    setError(null);
    setIdentifier('');
    setSigningIn(true);
    loadingTimer.current = window.setTimeout(() => {
      loadingTimer.current = null;
      onSignIn(input);
    }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 180 : 850);
  }

  if (signingIn) return <CareLoading hindi={hindi} />;

  return <div className="care-sign-in" lang={hindi ? 'hi' : 'en'}>
    <header className="care-sign-in-header">
      <span className="care-sign-in-brand"><svg viewBox="0 0 36 36" fill="none" aria-hidden="true"><rect width="36" height="36" rx="12" fill="#267E72" /><path d="M18 27C14 24 8 19 8 14a6 6 0 0 1 10-4 6 6 0 0 1 10 4c0 5-6 10-10 13Z" fill="#FFE0A0" /><path d="M18 27V17" stroke="#267E72" strokeWidth="2" strokeLinecap="round" /></svg>Saanthvana</span>
      <button type="button" className="care-sign-in-language" onClick={onLanguage} lang={hindi ? 'en' : 'hi'}>{hindi ? 'English' : 'हिन्दी'}</button>
    </header>
    <main className="care-sign-in-main">
      <div className="care-sign-in-card">
        <div className="care-sign-in-art" aria-hidden="true" data-role={role}>
          <div className="care-sign-in-sun" />
          <div className="care-sign-in-arch" />
          <svg className="care-sign-in-branch" viewBox="0 0 110 230" fill="none"><path d="M50 221C65 169 34 115 64 23" stroke="#498572" strokeWidth="4" strokeLinecap="round" /><path d="M57 73C21 70 9 47 20 28c30 6 40 21 37 45Z" fill="#76A58A" /><path d="M52 111c39-1 52-21 44-43-31 2-46 19-44 43Z" fill="#3E8875" /><path d="M51 150C19 148 4 130 9 109c31-1 45 19 42 41Z" fill="#5A977F" /><path d="M58 192c31-2 44-18 39-37-30 0-44 17-39 37Z" fill="#8FB296" /></svg>
          <div className="care-sign-in-art-tile care-sign-in-art-main"><CareArt kind={roleArt[role]} /></div>
          <div className="care-sign-in-art-tile care-sign-in-art-story"><CareArt kind="care-story" /></div>
          <div className="care-sign-in-art-tile care-sign-in-art-voice"><CareArt kind="voice-journal" /></div>
        </div>
        <form className="care-sign-in-form" onSubmit={submit} aria-labelledby={`${id}-title`} noValidate>
          <h1 id={`${id}-title`}>{t('Your care starts here', 'देखभाल की शुरुआत यहीं से')}</h1>
          <fieldset className="care-sign-in-roles"><legend>{t('Sign in as', 'किसके तौर पर साइन इन करें')}</legend><div>{([
            ['family', 'Family', 'परिवार'], ['patient', 'Patient', 'मरीज़'], ['doctor', 'Doctor', 'डॉक्टर'],
          ] as const).map(([value, en, hi]) => <button key={value} type="button" className={`care-sign-in-role care-sign-in-role-${value}`} aria-pressed={role === value} onClick={() => { if (role !== value) { setRole(value); setIdentifier(''); setError(null); } }}><CareArt kind={roleArt[value]} /><span>{t(en, hi)}</span></button>)}</div></fieldset>
          <label className="care-sign-in-label" htmlFor={`${id}-identifier`}>
            {role === 'doctor' ? t('Email or mobile number', 'ईमेल या मोबाइल नंबर') : role === 'family' ? t('Patient’s ABHA number', 'मरीज़ का ABHA नंबर') : t('ABHA number', 'ABHA नंबर')}
            <input ref={identifierInput} id={`${id}-identifier`} type="text" inputMode={role === 'doctor' ? 'text' : 'numeric'} enterKeyHint="go" value={identifier} onChange={(event) => { setIdentifier(event.target.value); setError(null); }} placeholder={role === 'doctor' ? t('Email / mobile', 'ईमेल / मोबाइल') : t('14 digits', '14 अंक')} maxLength={role === 'doctor' ? 254 : 24} autoComplete="off" autoCapitalize="none" spellCheck={false} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined} required />
          </label>
          {error && <p id={`${id}-error`} className="care-sign-in-error" role="alert">{error === 'abha' ? t('Enter a 14-digit ABHA number.', '14 अंकों का ABHA नंबर डालें।') : t('Enter an email or a 10-digit Indian mobile number.', 'ईमेल या 10 अंकों का भारतीय मोबाइल नंबर डालें।')}</p>}
          <button type="submit" className="care-sign-in-submit" disabled={!patient || !name.trim() || !identifier.trim()}><span>{t('Sign in', 'साइन इन करें')}</span><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg></button>
        </form>
      </div>
    </main>
  </div>;
}
