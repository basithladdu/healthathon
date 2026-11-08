'use client';

import { useId, useState, type FormEvent } from 'react';
import type { PatientDetails } from './patient-portal';
import { isValidBirthDate } from './patient-date';
import { CareRouteLink } from './care-route-link';
import { addWeight, removeWeight, validWeight, weightChange, weightTrend, WEIGHT_MAX_KG, WEIGHT_MIN_KG, type CareWeightEntry } from './care-weight-state';
import './care-profile.css';
import { careLocale, careText, type CareLang } from './care-i18n';

/** A small line of weights by day, the same look as the test trends. */
function WeightChart({ weights, lang }: { weights: CareWeightEntry[]; lang: CareLang }) {
  const dates = weights.map((entry) => Date.parse(`${entry.date}T12:00:00Z`));
  const values = weights.map((entry) => entry.kg);
  const first = Math.min(...dates); const last = Math.max(...dates);
  const low = Math.min(...values); const high = Math.max(...values);
  const x = (date: number) => first === last ? 270 : 58 + ((date - first) / (last - first)) * 452;
  const y = (value: number) => low === high ? 83 : 140 - ((value - low) / (high - low)) * 114;
  const label = (date: string) => new Date(`${date}T12:00:00Z`).toLocaleDateString(careLocale(lang), { day: 'numeric', month: 'short', timeZone: 'UTC' });
  const points = weights.map((entry, i) => ({ entry, x: x(dates[i]), y: y(entry.kg) }));
  return <svg className="care-weight-chart" viewBox="0 0 540 184" role="img" aria-label={careText(lang, `Weight by date: ${weights.map((entry) => `${entry.kg} kg`).join(', ')}`, `तारीख़ के हिसाब से वज़न: ${weights.map((entry) => `${entry.kg} किलो`).join(', ')}`)}>
    <line x1="58" x2="510" y1="140" y2="140" className="care-weight-axis" />
    <text x="48" y={y(low) + 4} textAnchor="end">{low}</text>
    {low !== high && <text x="48" y={y(high) + 4} textAnchor="end">{high}</text>}
    {points.length > 1 && <polyline points={points.map((point) => `${point.x},${point.y}`).join(' ')} className="care-weight-line" />}
    {points.map((point) => <circle key={point.entry.date} cx={point.x} cy={point.y} r="5" className="care-weight-point"><title>{label(point.entry.date)} · {point.entry.kg} kg</title></circle>)}
    <text x={first === last ? 270 : 58} y="168" textAnchor={first === last ? 'middle' : 'start'}>{label(weights[0].date)}</text>
    {first !== last && <text x="510" y="168" textAnchor="end">{label(weights[weights.length - 1].date)}</text>}
  </svg>;
}

export function CareProfile({ details, lang, onSave, diagnosis = '', today = new Date().toISOString().slice(0, 10) }: { details: PatientDetails; lang: CareLang; onSave: (details: PatientDetails) => void; diagnosis?: string; today?: string }) {
  const id = useId();
  const [form, setForm] = useState(details);
  const [status, setStatus] = useState('');
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const [weightDay, setWeightDay] = useState(today);
  const [weightKg, setWeightKg] = useState('');
  const [weightStatus, setWeightStatus] = useState('');
  const weights = details.weights ?? [];
  const latest = weights[weights.length - 1];
  const change = weightChange(weights); const trend = weightTrend(weights);
  function saveWeights(next: CareWeightEntry[], message: string) { onSave({ ...details, weights: next }); setForm((current) => ({ ...current, weights: next })); setWeightStatus(message); }
  function addWeightEntry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const kg = Number(weightKg.replace(',', '.'));
    if (!weightKg.trim() || !validWeight({ date: weightDay, kg }, today)) { setWeightStatus(t(`Enter a weight from ${WEIGHT_MIN_KG} to ${WEIGHT_MAX_KG} kg, for today or earlier.`, `आज या पहले की तारीख़ के लिए ${WEIGHT_MIN_KG} से ${WEIGHT_MAX_KG} किलो तक का वज़न लिखें।`)); return; }
    setWeightKg(''); saveWeights(addWeight(weights, { date: weightDay, kg }, today), t('Weight saved.', 'वज़न सेव हो गया।'));
  }
  function update(key: keyof PatientDetails, value: string) { setForm((current) => ({...current, [key]:value})); setStatus(''); }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.fullName.trim()) return;
    if (form.dob.trim() && !isValidBirthDate(form.dob.trim())) { setStatus(t('Use a valid birth date: DD/MM/YYYY.', 'जन्म की सही तारीख DD/MM/YYYY में लिखें।')); return; }
    if (form.abhaNumber && !/^\d{14}$/.test(form.abhaNumber.replace(/[\s-]/g, ''))) { setStatus(t('ABHA needs 14 digits.', 'ABHA में 14 अंक होने चाहिए।')); return; }
    onSave({...form,fullName:form.fullName.trim()}); setStatus(t('Profile saved.', 'जानकारी सेव हो गई।'));
  }
  return <section className="care-profile"><header><h1>{t('My profile', 'मेरी जानकारी')}</h1><CareRouteLink view="daily-care">{t('Home', 'होम')}</CareRouteLink></header>
    {diagnosis.trim() && <section className="care-profile-card" aria-labelledby={`${id}-diagnosis`}>
      <h2 id={`${id}-diagnosis`}>{t('Diagnosis', 'बीमारी')}</h2>
      <p className="care-profile-diagnosis" data-testid="profile-diagnosis">{diagnosis}</p>
    </section>}
    <section className="care-profile-card" aria-labelledby={`${id}-weight`}>
      <div className="care-profile-card-head"><h2 id={`${id}-weight`}>{t('Weight', 'वज़न')}</h2>
        {latest && <p><strong>{latest.kg} {t('kg', 'किलो')}</strong>{trend && <span className={`care-weight-trend is-${trend}`}>{trend === 'steady' ? t('No change', 'कोई बदलाव नहीं') : `${trend === 'up' ? '▲' : '▼'} ${Math.abs(change ?? 0)} ${t('kg', 'किलो')}`}</span>}</p>}</div>
      {weights.length ? <WeightChart weights={weights} lang={lang} /> : <p className="care-profile-hint">{t('No weight recorded yet.', 'अभी कोई वज़न दर्ज नहीं।')}</p>}
      <form className="care-weight-form" onSubmit={addWeightEntry}>
        <label>{t('Day', 'दिन')}<input type="date" value={weightDay} max={today} onChange={(event) => setWeightDay(event.target.value)} required /></label>
        <label>{t('Weight (kg)', 'वज़न (किलो)')}<input inputMode="decimal" value={weightKg} onChange={(event) => { setWeightKg(event.target.value); setWeightStatus(''); }} maxLength={6} required data-testid="profile-weight-input" /></label>
        <button type="submit" className="secondary-button">{t('Add weight', 'वज़न जोड़ें')}</button>
      </form>
      {weights.length > 0 && <details><summary>{t('All weights', 'सभी वज़न')} · {weights.length}</summary><ul className="care-weight-list">{[...weights].reverse().map((entry) => <li key={entry.date}><span>{new Date(`${entry.date}T12:00:00Z`).toLocaleDateString(careLocale(lang), { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })} · {entry.kg} {t('kg', 'किलो')}</span><button type="button" className="care-text-button" onClick={() => saveWeights(removeWeight(weights, entry.date), t('Weight removed.', 'वज़न हटा दिया।'))}>{t('Remove', 'हटाएँ')}</button></li>)}</ul></details>}
      <p role="status" className="care-profile-hint">{weightStatus}</p>
    </section>
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
