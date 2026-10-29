'use client';
// Living will (AMD) screen: Vidhi-format questions, PDF, execution and copies. Placeholder until built.
import type { CareLang } from './care-i18n';

export function AmdScreen(props: { patientId: string; patientName: string; role: 'doctor' | 'patient' | 'family'; lang: CareLang }) {
  return <section className="care-page"><h1>Living will</h1><p>{props.patientName}</p></section>;
}
