'use client';

import { useEffect, useState } from 'react';
import { loadEmergencyCard, MESSAGES, type EmergencyResult } from './emergency-link';
import styles from './emergency.module.css';

const day = (value: number | string) => new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export function EmergencyView() {
  const [result, setResult] = useState<EmergencyResult | null>(null);
  useEffect(() => {
    let live = true;
    const load = () => loadEmergencyCard(window.location.hash).then((value) => { if (live) setResult(value); });
    load();
    window.addEventListener('hashchange', load);
    return () => { live = false; window.removeEventListener('hashchange', load); };
  }, []);

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <span className={styles.brand}>Sahara</span>
          <span className={styles.tag}>Emergency card · आपातकालीन कार्ड</span>
        </header>
        {!result && <p className={styles.muted}>Loading the card… · कार्ड लोड हो रहा है…</p>}
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
