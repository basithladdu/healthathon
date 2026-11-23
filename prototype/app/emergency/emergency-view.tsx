'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { cleanPlace, loadEmergencyCard, MESSAGES, PLACE_MAX, tokenFromHash, type EmergencyResult } from './emergency-link';
import styles from './emergency.module.css';

const day = (value: number | string) => new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

const subscribeHash = (onChange: () => void) => { window.addEventListener('hashchange', onChange); return () => window.removeEventListener('hashchange', onChange); };

export function EmergencyView() {
  // null while rendering on the server, then the page's #fragment (the token never reaches this website's server).
  const hash = useSyncExternalStore<string | null>(subscribeHash, () => window.location.hash, () => null);
  const [place, setPlace] = useState('');
  // null until the person taps "Show the card" or "Skip"; then the (possibly empty) hospital or clinic line.
  const [chosen, setChosen] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<{ key: string; result: EmergencyResult } | null>(null);
  const token = hash === null ? null : tokenFromHash(hash);
  const key = hash !== null && token && chosen !== null ? `${hash}\n${chosen}` : null;
  useEffect(() => {
    if (key === null || hash === null || chosen === null) return;
    let live = true;
    loadEmergencyCard(hash, undefined, chosen).then((result) => { if (live) setLoaded({ key, result }); });
    return () => { live = false; };
  }, [key, hash, chosen]);
  const asking = hash !== null && token !== null && chosen === null;
  const result: EmergencyResult | null = hash === null ? null : !token ? { status: 'missing' } : loaded?.key === key ? loaded.result : null;

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <span className={styles.brand}>Sahara</span>
          <span className={styles.tag}>Pocket emergency card · जेब में रखने का आपातकालीन कार्ड</span>
        </header>
        {asking && (
          <form className={styles.ask} onSubmit={(event) => { event.preventDefault(); setChosen(cleanPlace(place)); }}>
            <label htmlFor="emergency-place">Your hospital or clinic (optional) · आपका अस्पताल या क्लिनिक (वैकल्पिक)</label>
            <input id="emergency-place" className={styles.input} value={place} maxLength={PLACE_MAX} autoComplete="organization" enterKeyHint="go"
              placeholder="e.g. City Hospital, Emergency" onChange={(event) => setPlace(event.target.value)} />
            <p className={styles.muted}>Only this name and the time are kept, so the patient&apos;s doctor and family know where the card was opened. Please don&apos;t type patient details. · केवल यह नाम और समय रखा जाता है।</p>
            <div className={styles.askActions}>
              <button type="submit" className={styles.primary}>Show the card · कार्ड दिखाएँ</button>
              <button type="button" className={styles.skip} onClick={() => setChosen('')}>Skip · छोड़ें</button>
            </div>
          </form>
        )}
        {!asking && !result && <p className={`${styles.muted} ${styles.loading}`} role="status">Loading the card… · कार्ड लोड हो रहा है…</p>}
        {result && result.status !== 'live' && (
          <section className={styles.notice}><h1>{MESSAGES[result.status].title}</h1><p>{MESSAGES[result.status].detail}</p></section>
        )}
        {result?.status === 'live' && (() => {
          const card = result.card;
          return (
            <>
              <p className={styles.banner}>Shared by the family from what they recorded in Sahara. Read-only. Not a treatment order, legal directive or hospital record. Names are self-entered. · परिवार द्वारा साझा किया गया, केवल पढ़ने के लिए।</p>
              {card.name && <h1 className={styles.name}>{card.name}</h1>}
              <p className={styles.muted}>Card {card.cardNumber} · ID {card.cardId} · updated {day(result.updatedAt || card.madeAt)}</p>
              {card.goals && (
                <section className={styles.section}>
                  <h2>Goals of care · देखभाल के लक्ष्य</h2>
                  <p className={styles.muted}>Note v{card.goals.version}, confirmed in the app by {card.goals.confirmedBy} on {day(card.goals.confirmedAt)}{card.goals.newerUnconfirmedVersion ? `; a newer note v${card.goals.newerUnconfirmedVersion} is not yet confirmed` : ''}.</p>
                  {card.goals.whatMatters && <><h3>What matters</h3><p className={styles.text}>{card.goals.whatMatters}</p></>}
                  {card.goals.nextSteps && <><h3>Next steps</h3><p className={styles.text}>{card.goals.nextSteps}</p></>}
                </section>
              )}
              {card.contacts && card.contacts.length > 0 && (
                <section className={styles.section}>
                  <h2>Emergency contacts · आपातकालीन संपर्क</h2>
                  <ul>{card.contacts.map((c, i) => <li key={i}>{c.name} ({c.relationship}){c.phone && <> · <a href={`tel:${c.phone.replace(/[^+0-9]/g, '')}`}>{c.phone}</a></>}</li>)}</ul>
                </section>
              )}
              {card.medicines && card.medicines.length > 0 && (
                <section className={styles.section}>
                  <h2>Current medicines (as listed by family) · अभी की दवाएँ</h2>
                  <ul>{card.medicines.map((m, i) => <li key={i}>{m.name}{m.detail && <span className={styles.muted}> · {m.detail}</span>}</li>)}</ul>
                </section>
              )}
              {card.allergies && (
                <section className={styles.section}>
                  <h2>Allergies (as listed by family) · एलर्जी</h2>
                  <p className={styles.text}>{card.allergies}</p>
                </section>
              )}
              <section className={styles.section}>
                {card.notRecorded && <p className={styles.muted}>Not recorded in Sahara: {card.notRecorded}.</p>}
                {card.notIncluded && card.notIncluded.length > 0 && <p className={styles.muted}>Not on this card: {card.notIncluded.join('; ')}.</p>}
                <p className={styles.muted}>This link works until {day(result.expiresAt)} unless the family turns it off.</p>
              </section>
            </>
          );
        })()}
      </div>
    </main>
  );
}
