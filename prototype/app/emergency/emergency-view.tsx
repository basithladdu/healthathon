'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import {
  cardDate, cleanPlace, contactRows, isOlderVersion, isOverThreeMonths, loadEmergencyCard, medicineRows, MESSAGES, NOT_LATEST, NOT_SAVED,
  OVER_THREE_MONTHS, PLACE_MAX, telHref, tokenFromHash, type EmergencyLive, type EmergencyResult,
} from './emergency-link';
import styles from './emergency.module.css';

const day = (value: number | string) => new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

const subscribeHash = (onChange: () => void) => { window.addEventListener('hashchange', onChange); return () => window.removeEventListener('hashchange', onChange); };

// No geolocation here, by design (clinical team, 24 Nov 2026): the scanner's location is never asked for or sent.
export function EmergencyView() {
  // null while rendering on the server, then the page's #fragment (the token never reaches this website's server).
  const hash = useSyncExternalStore<string | null>(subscribeHash, () => window.location.hash, () => null);
  const [place, setPlace] = useState('');
  // null until the person taps "Show the card" or "Skip"; then the (possibly empty) hospital or clinic line.
  const [chosen, setChosen] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<{ key: string; result: EmergencyResult } | null>(null);
  // The newest version of the same card, opened from the "not the latest version" banner.
  const [latest, setLatest] = useState<{ key: string; result: EmergencyResult | 'loading' } | null>(null);
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
  const newest = latest?.key === key ? latest.result : null;
  const openLatest = () => {
    if (hash === null || key === null) return;
    setLatest({ key, result: 'loading' });
    loadEmergencyCard(hash, undefined, '', true).then((r) => setLatest({ key, result: r }));
  };

  const shown: EmergencyLive | null = newest && newest !== 'loading' && newest.status === 'live' ? newest : result?.status === 'live' ? result : null;

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <span className={styles.brand}>Sahara</span>
          <span className={styles.tag}>Emergency card · आपातकालीन कार्ड</span>
        </header>
        {asking && (
          <form className={styles.ask} onSubmit={(event) => { event.preventDefault(); setChosen(cleanPlace(place)); }}>
            <label htmlFor="emergency-place">Your hospital or clinic (optional) · आपका अस्पताल या क्लिनिक (वैकल्पिक)</label>
            <input id="emergency-place" className={styles.input} value={place} maxLength={PLACE_MAX} autoComplete="organization" enterKeyHint="go"
              placeholder="e.g. City Hospital, Emergency" onChange={(event) => setPlace(event.target.value)} />
            <p className={styles.muted}>Only this name and the time are kept, so the patient&apos;s doctor and family know where the card was opened. Your location is not asked for or kept. Please don&apos;t type patient details. · केवल यह नाम और समय रखा जाता है।</p>
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
        {result?.status === 'live' && shown && (
          <>
            {isOlderVersion(result) && (
              <section className={styles.warning} role="alert" data-testid="emergency-not-latest">
                <h2>{NOT_LATEST.title}</h2>
                {shown === result ? (
                  <>
                    <p>{NOT_LATEST.detail} You scanned version {result.scannedVersion}; the latest is version {result.latestVersion} ({day(result.latestPublishedAt)}).</p>
                    <button type="button" className={styles.primary} onClick={openLatest} disabled={newest === 'loading'}>
                      {newest === 'loading' ? 'Opening… · खुल रहा है…' : 'Show the latest version · नवीनतम संस्करण दिखाएँ'}
                    </button>
                    {newest && newest !== 'loading' && newest.status !== 'live' && <p>{MESSAGES[newest.status].title}. {MESSAGES[newest.status].detail}</p>}
                  </>
                ) : (
                  <p>Now showing the latest version {shown.version}. The printed card you scanned is version {result.scannedVersion}; ask the family for the new card. · अब नवीनतम संस्करण दिखाया जा रहा है।</p>
                )}
              </section>
            )}
            {isOverThreeMonths(shown) && (
              <section className={styles.warning} role="alert" data-testid="emergency-over-three-months">
                <h2>{OVER_THREE_MONTHS.title}</h2>
                <p>{OVER_THREE_MONTHS.detail}</p>
              </section>
            )}
            <EmergencyCardBody result={shown} />
          </>
        )}
      </div>
    </main>
  );
}

function EmergencyCardBody({ result }: { result: EmergencyLive }) {
  const card = result.card;
  const sections = Array.isArray(card.sections) ? card.sections : null;
  const has = (name: string, present: boolean) => (sections ? sections.includes(name) : present);
  const contacts = contactRows(card);
  const medicines = medicineRows(card);
  const made = cardDate(result);
  const until = typeof card.validUntil === 'number' ? card.validUntil : made + 90 * 86_400_000;
  return (
    <>
      <p className={styles.banner}>Shared by the family from what they recorded in Sahara. Read-only. Not a treatment order, legal directive or hospital record. Names are self-entered. · परिवार द्वारा साझा किया गया, केवल पढ़ने के लिए।</p>
      {card.name && <h1 className={styles.name}>{card.name}</h1>}
      <p className={styles.meta}>
        Card {card.cardNumber} · version {result.version} · ID {card.cardId}
        {Number.isFinite(made) && <> · made {day(made)}</>}
        {Number.isFinite(until) && <> · <strong>valid until {day(until)}</strong></>}
      </p>
      {card.allergies && <p className={styles.allergy}><strong>Allergies (as listed by family) · एलर्जी:</strong> {card.allergies}</p>}
      {has('CONTACTS', contacts.length > 0) && (
        <section className={styles.section}>
          <h2>Who to call · किसे फ़ोन करें</h2>
          {contacts.length === 0 ? <p className={styles.muted}>No contacts saved.</p> : (
            <table className={styles.table}>
              <thead><tr><th scope="col">Who · कौन</th><th scope="col">Name · नाम</th><th scope="col">Phone · फ़ोन</th></tr></thead>
              <tbody>{contacts.map((row, i) => (
                <tr key={i}><th scope="row">{row.who}</th><td>{row.name}</td><td>{row.phone === NOT_SAVED ? <span className={styles.muted}>{NOT_SAVED}</span> : <a href={telHref(row.phone)}>{row.phone}</a>}</td></tr>
              ))}</tbody>
            </table>
          )}
        </section>
      )}
      {has('MEDICINES', medicines.length > 0) && (
        <section className={styles.section}>
          <h2>Current medicines (as listed by family) · अभी की दवाएँ</h2>
          {medicines.length === 0 ? <p className={styles.muted}>No current medicines listed.</p> : (
            <table className={styles.table}>
              <thead><tr><th scope="col">Medicine · दवा</th><th scope="col">Strength · मात्रा</th><th scope="col">When · कब</th></tr></thead>
              <tbody>{medicines.map((m, i) => <tr key={i}><th scope="row">{m.name}</th><td>{m.strength}</td><td>{m.when}</td></tr>)}</tbody>
            </table>
          )}
        </section>
      )}
      {card.goals && (
        <section className={styles.section}>
          <h2>Goals of care · देखभाल के लक्ष्य</h2>
          <p className={styles.muted}>Note v{card.goals.version}, confirmed in the app by {card.goals.confirmedBy} on {day(card.goals.confirmedAt)}{card.goals.newerUnconfirmedVersion ? `; a newer note v${card.goals.newerUnconfirmedVersion} is not yet confirmed` : ''}.</p>
          {card.goals.whatMatters && <><h3>What matters</h3><p className={styles.text}>{card.goals.whatMatters}</p></>}
          {card.goals.nextSteps && <><h3>Next steps</h3><p className={styles.text}>{card.goals.nextSteps}</p></>}
        </section>
      )}
      <section className={styles.footnote}>
        {card.notRecorded && <p className={styles.muted}>Not recorded in Sahara: {card.notRecorded}.</p>}
        {card.notIncluded && card.notIncluded.length > 0 && <p className={styles.muted}>Not on this card: {card.notIncluded.join('; ')}.</p>}
        {result.expiresAt && <p className={styles.muted}>This link works until {day(result.expiresAt)} unless the family turns it off.</p>}
      </section>
    </>
  );
}
