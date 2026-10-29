'use client';

import { useRef, useState } from 'react';
import { INDIA_STATES } from './feedback-schema';
import styles from './SurveyStateChoices.module.css';

type Props = {
  name: 'registrationState' | 'state';
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
};

export default function SurveyStateChoices({ name, label, value, onChange, error }: Props) {
  const [query, setQuery] = useState('');
  const search = useRef<HTMLInputElement>(null);
  const id = `hospital-${name}`;
  const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  const matches = INDIA_STATES.filter(state => words.every(word => state.toLocaleLowerCase().includes(word)));

  return <fieldset id={id} tabIndex={-1} className={styles.group} aria-invalid={error ? true : undefined} aria-describedby={`${id}-selected${error ? ` ${id}-error` : ''}`}>
    <legend>{label}</legend>
    <label htmlFor={`${id}-search`} className={styles.searchLabel}>Find a state or union territory</label>
    <div className={styles.searchRow}>
      <input ref={search} id={`${id}-search`} type="text" autoComplete="off" value={query} placeholder="Type to filter the list" onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }} />
      <button type="button" disabled={!query} onClick={() => { setQuery(''); search.current?.focus(); }}>Clear search</button>
    </div>
    <p id={`${id}-selected`} className={styles.selection}>{value ? <>Selected: <strong>{value}</strong></> : 'Not specified.'}</p>
    <div className={styles.options}>
      <label className={styles.option}><input type="radio" name={id} value="" checked={!value} onChange={() => onChange('')} />Not specified</label>
      {matches.map(state => <label key={state} className={styles.option}>
        <input type="radio" name={id} value={state} checked={value === state} onChange={() => onChange(state)} />
        {state}
      </label>)}
      {!matches.length ? <p role="status" className={styles.noResults}>No matching state or union territory. Try fewer letters or clear the search.</p> : null}
    </div>
    {error ? <p id={`${id}-error`} role="alert" className={styles.error}>{error}</p> : null}
  </fieldset>;
}
