'use client';

import { useId, useState, type FormEvent } from 'react';
import { CareArt, type CareArtKind } from './care-art';
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
  const [selectedId, setSelectedId] = useState(patients[0]?.id ?? '');
  const [customName, setCustomName] = useState<string | null>(null);
  const patient = patients.find((item) => item.id === selectedId) ?? patients[0];
  const defaultName = role === 'doctor' ? 'Dr Sujay' : role === 'patient' ? patient?.name ?? ''
    : patient && /\bmeera\b/i.test(patient.name) ? 'Kavya Raghavan' : 'Family member';
  const name = customName ?? defaultName;
  const t = (en: string, hi: string) => hindi ? hi : en;
  const roleArt: Record<CareSignInInput['role'], CareArtKind> = {
    family: 'home-help', patient: 'cancer-overview', doctor: 'doctor-pack',
  };

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!patient || !name.trim()) return;
    onSignIn({ role, patientId: patient.id, name: name.trim() });
  }

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
        <form className="care-sign-in-form" onSubmit={submit} aria-labelledby={`${id}-title`}>
          <h1 id={`${id}-title`}>{t('Your care starts here', 'देखभाल की शुरुआत यहीं से')}</h1>
          <fieldset className="care-sign-in-roles"><legend>{t('Continue as', 'किसके तौर पर आगे बढ़ें')}</legend><div>{([
            ['family', 'Family', 'परिवार'], ['patient', 'Patient', 'मरीज़'], ['doctor', 'Doctor', 'डॉक्टर'],
          ] as const).map(([value, en, hi]) => <button key={value} type="button" className={`care-sign-in-role care-sign-in-role-${value}`} aria-pressed={role === value} onClick={() => { setRole(value); setCustomName(null); }}><CareArt kind={roleArt[value]} /><span>{t(en, hi)}</span></button>)}</div></fieldset>
          <label className="care-sign-in-label" htmlFor={`${id}-patient`}>{t('Care for', 'किसकी देखभाल')}<select id={`${id}-patient`} value={patient?.id ?? ''} disabled={!patients.length} onChange={(event) => { setSelectedId(event.target.value); setCustomName(null); }} required>
            {!patients.length && <option value="">{t('No people added yet', 'अभी कोई नाम नहीं जोड़ा है')}</option>}
            {patients.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select></label>
          <details className="care-sign-in-name"><summary><span>{t('Use another name', 'दूसरा नाम इस्तेमाल करें')}</span><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m12 4 4 4M4 16l4-1 9-9a2.8 2.8 0 0 0-4-4l-9 9-1 5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" /></svg></summary><label className="care-sign-in-label" htmlFor={`${id}-name`}>{t('Your name', 'आपका नाम')}<input id={`${id}-name`} value={name} onChange={(event) => setCustomName(event.target.value)} maxLength={80} autoComplete="off" /></label></details>
          <button type="submit" className="care-sign-in-submit" disabled={!patient || !name.trim()}><span>{t('Continue', 'आगे बढ़ें')}</span><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg></button>
        </form>
      </div>
    </main>
  </div>;
}
