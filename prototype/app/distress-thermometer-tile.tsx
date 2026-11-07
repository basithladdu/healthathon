'use client';

import { useEffect, useId, useState } from 'react';
import { careText, type CareLang } from './care-i18n';
import { DistressScoreRow } from './caregiver-wellbeing';
import {
  NCCN_DISTRESS_SOURCE_EN, NCCN_DISTRESS_SOURCE_HI, PATIENT_DISTRESS_KEY, distressBandText, loadDistress, saveDistress,
} from './distress-check-state';
import './caregiver-wellbeing.css';

/** The NCCN distress thermometer on the symptoms page: one 0-10 row for the past week, saved for today on this device. */
export function DistressThermometerTile({ patientId, today, lang }: { patientId: string; today: string; lang: CareLang }) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const id = useId();
  const key = PATIENT_DISTRESS_KEY(patientId);
  const [score, setScore] = useState<number | null>(null);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- today's score hydrates from this browser after mount.
    setScore(loadDistress(window.localStorage, key, today));
  }, [key, today]);
  return <section className="distress-tile" aria-labelledby={`${id}-title`} data-testid="distress-thermometer">
    <h2 id={`${id}-title`}>{t('Distress thermometer', 'परेशानी थर्मामीटर')}</h2>
    <p>{score === null
      ? t('How much distress this past week? 0–10', 'इस हफ़्ते कितनी परेशानी? 0–10')
      : `${t('Today', 'आज')}: ${score}/10 · ${distressBandText(score, t)}`}</p>
    <DistressScoreRow value={score} lang={lang} name={t('Distress thermometer', 'परेशानी थर्मामीटर')}
      onPick={(next) => { saveDistress(window.localStorage, key, today, next); setScore(next); }} />
    <p className="distress-source">NCCN Distress Thermometer</p>
    <p className="distress-source">{t(NCCN_DISTRESS_SOURCE_EN, NCCN_DISTRESS_SOURCE_HI)}</p>
  </section>;
}
