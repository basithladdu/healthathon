'use client';

import { useId, useState, type FormEvent } from 'react';
import type { PatientDetails } from './patient-portal';
import { isValidBirthDate } from './patient-date';
import { CareRouteLink } from './care-route-link';
import './care-profile.css';

export function CareProfile({ details, hindi, onSave }: { details: PatientDetails; hindi: boolean; onSave: (details: PatientDetails) => void }) {
  const id = useId();
  const [form, setForm] = useState(details);
  const [status, setStatus] = useState('');
  const t = (en: string, hi: string) => hindi ? hi : en;
  function update(key: keyof PatientDetails, value: string) { setForm((current) => ({...current, [key]:value})); setStatus(''); }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.fullName.trim()) return;
    if (form.dob.trim() && !isValidBirthDate(form.dob.trim())) { setStatus(t('Use a valid birth date: DD/MM/YYYY.', 'जन्म की सही तारीख DD/MM/YYYY में लिखें।')); return; }
    if (form.abhaNumber && !/^\d{14}$/.test(form.abhaNumber.replace(/[\s-]/g, ''))) { setStatus(t('ABHA needs 14 digits.', 'ABHA में 14 अंक होने चाहिए।')); return; }
    onSave({...form,fullName:form.fullName.trim()}); setStatus(t('Profile saved.', 'जानकारी सेव हो गई।'));
  }
  return <section className="care-profile"><header><h1>{t('My profile', 'मेरी जानकारी')}</h1><CareRouteLink view="daily-care">{t('Home', 'होम')}</CareRouteLink></header>
    <form onSubmit={save}>
      <div className="care-profile-fields">
        <label htmlFor={`${id}-name`}>{t('Full name', 'पूरा नाम')}<input id={`${id}-name`} value={form.fullName} onChange={(event) => update('fullName',event.target.value)} required maxLength={160} /></label>
        <label htmlFor={`${id}-dob`}>{t('Date of birth', 'जन्म की तारीख')}<input id={`${id}-dob`} value={form.dob} placeholder="DD/MM/YYYY" onChange={(event) => update('dob',event.target.value)} maxLength={10} /></label>
        <label htmlFor={`${id}-gender`}>{t('Gender', 'लिंग')}<select id={`${id}-gender`} value={form.sex} onChange={(event) => update('sex',event.target.value)}><option value="">{t('Select', 'चुनें')}</option><option value="Female">{t('Female', 'महिला')}</option><option value="Male">{t('Male', 'पुरुष')}</option><option value="Other">{t('Other', 'अन्य')}</option><option value="Prefer not to say">{t('Prefer not to say', 'बताना नहीं चाहते')}</option></select></label>
        <label htmlFor={`${id}-phone`}>{t('Phone number', 'फ़ोन नंबर')}<input id={`${id}-phone`} type="tel" value={form.phone} onChange={(event) => update('phone',event.target.value)} maxLength={20} /></label>
        <label htmlFor={`${id}-abha`}>{t('ABHA number (optional)', 'ABHA नंबर (वैकल्पिक)')}<input id={`${id}-abha`} inputMode="numeric" autoComplete="off" value={form.abhaNumber ?? ''} onChange={(event) => update('abhaNumber',event.target.value)} maxLength={20} /></label>
        <label htmlFor={`${id}-language`}>{t('Preferred language', 'पसंदीदा भाषा')}<input id={`${id}-language`} value={form.language} onChange={(event) => update('language',event.target.value)} maxLength={80} /></label>
        <label className="care-profile-wide" htmlFor={`${id}-address`}>{t('Home address', 'घर का पता')}<textarea id={`${id}-address`} value={form.address} onChange={(event) => update('address',event.target.value)} maxLength={500} rows={2} /></label>
      </div>
      <details><summary>{t('My oncologist', 'मेरे कैंसर डॉक्टर')}</summary><div className="care-profile-fields">{(['name','clinic','phone'] as const).map((field) => <label key={field}>{t({name:'Name',clinic:'Hospital',phone:'Phone'}[field],{name:'नाम',clinic:'अस्पताल',phone:'फ़ोन'}[field])}<input value={form.localPhysician[field]} type={field === 'phone' ? 'tel' : 'text'} maxLength={160} onChange={(event) => {setForm((current)=>({...current,localPhysician:{...current.localPhysician,[field]:event.target.value}}));setStatus('');}} /></label>)}</div></details>
      <div className="care-profile-actions"><button type="submit" className="primary-button">{t('Save profile', 'जानकारी सेव करें')}</button><span role="status">{status}</span></div>
    </form>
  </section>;
}
