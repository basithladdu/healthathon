// Draft FHIR R4 export of one signed ECTPR (tech spec §7 stretch: "Consent scope adr").
// A collection Bundle of Patient, Practitioner and Consent. DRAFT FORMAT: checked only against the rules tested in
// tests/ectpr-gaps.test.mjs, not run through a FHIR validator and not validated against ABDM profiles. No ABHA is invented.
// V1 holds here too: a choice left blank means no preference recorded, so it makes no provision at all, never a "deny".
import type { EctprRecord } from './ectpr-model.ts';
import { consultantSignature } from './ectpr-rules.ts';

export const ECTPR_FHIR_DRAFT_NOTE = 'Draft format. Not validated against ABDM profiles or a FHIR validator.';
const CHOICE_SYSTEM = 'urn:sahara:ectpr:choice';

type Provision = { type: 'permit' | 'deny'; code: Array<{ coding: Array<{ system: string; code: string }>; text: string }> };
export type FhirResource = Record<string, unknown> & { resourceType: string };
export type EctprFhirBundle = { resourceType: 'Bundle'; id: string; type: 'collection'; timestamp: string; meta: { tag: Array<{ code: string; display: string }> }; entry: Array<{ fullUrl: string; resource: FhirResource }> };

const CEILING_TEXT = ['Comfort measures only', 'Nasal prongs or simple mask', 'Non-rebreather or Venturi mask', 'High-flow nasal oxygen', 'Non-invasive ventilation', 'Intubation'];

function provision(type: Provision['type'], code: string, text: string): Provision {
  return { type, code: [{ coding: [{ system: CHOICE_SYSTEM, code }], text }] };
}

/** One nested provision per recorded choice, in ED Quick View order. Unrecorded choices are left out (V1). */
export function ectprProvisions(record: EctprRecord): Provision[] {
  const c = record.choices;
  const out: Provision[] = [];
  if (c.hospitalTransfer) {
    if (c.hospitalTransfer.kind === 'NO') out.push(provision('deny', 'TRANSFER_NO', 'Hospital transfer'));
    else if (c.hospitalTransfer.kind === 'ANY') out.push(provision('permit', 'TRANSFER_ANY', 'Hospital transfer for any significant deterioration'));
    else out.push(provision('permit', 'TRANSFER_ONLY_FOR', `Hospital transfer only for: ${c.hospitalTransfer.reasons.join(', ').toLowerCase().replace(/_/g, ' ')}`));
  }
  if (c.cpr) out.push(provision(c.cpr === 'DNACPR' ? 'deny' : 'permit', `CPR_${c.cpr}`, 'Cardiopulmonary resuscitation'));
  if (c.respCeiling !== null) {
    out.push(provision('permit', `RESP_CEILING_${c.respCeiling}`, `Breathing support up to: ${CEILING_TEXT[c.respCeiling]}`));
    if (c.respCeiling < 5) out.push(provision('deny', `RESP_ABOVE_${c.respCeiling}`, `Breathing support above: ${CEILING_TEXT[c.respCeiling]}`));
  }
  if (c.icu) {
    if (c.icu.kind === 'NO') out.push(provision('deny', 'ICU_NO', 'ICU admission'));
    else if (c.icu.kind === 'YES') out.push(provision('permit', 'ICU_YES', 'ICU admission'));
    else out.push(provision('permit', 'ICU_TRIAL', `ICU admission as a time-limited trial: ${c.icu.trial.durationValue} ${c.icu.trial.durationUnit.toLowerCase()}`));
  }
  if (c.goalOfCare) out.push(provision('permit', `GOAL_${c.goalOfCare}`, { A_LIFE: 'Goal of care: prioritise life', B_CEILING: 'Goal of care: balanced, within the ceilings', C_COMFORT: 'Goal of care: prioritise comfort' }[c.goalOfCare]));
  return out;
}

const GENDER: Record<string, string> = { F: 'female', M: 'male', O: 'other' };

/** Null unless the treating consultant has signed: an unsigned plan must never leave as if it were in force. */
export function ectprFhirBundle(record: EctprRecord, now: Date = new Date()): EctprFhirBundle | null {
  const consultant = consultantSignature(record);
  if (!consultant || record.status === 'DRAFT') return null;
  const patientRef = `urn:uuid:patient-${record.patientId}`;
  const practitionerRef = `urn:uuid:practitioner-${record.id}`;
  const consent: FhirResource = {
    resourceType: 'Consent',
    id: `${record.id}-consent`,
    // A superseded or cancelled version is kept for the record but is no longer in force.
    status: record.status === 'ACTIVE' ? 'active' : 'inactive',
    scope: { coding: [{ system: 'http://terminology.hl7.org/CodeSystem/consentscope', code: 'adr', display: 'Advanced Care Directive' }] },
    category: [
      { coding: [{ system: 'http://terminology.hl7.org/CodeSystem/consentcategorycodes', code: 'acd', display: 'Advance Directive' }], text: 'Emergency care and treatment plan (ECTPR)' },
      ...(record.choices.cpr === 'DNACPR' ? [{ coding: [{ system: 'http://terminology.hl7.org/CodeSystem/consentcategorycodes', code: 'dnr', display: 'Do Not Resuscitate' }] }] : []),
    ],
    patient: { reference: patientRef },
    dateTime: consultant.signedAt,
    performer: [{ reference: practitionerRef }],
    // Scope "adr" is what the spec asks for. The text says plainly that this is not a living will.
    policyRule: { text: `${record.formVersion}, version ${record.version}. A clinician-signed emergency plan, not a living will (AMD). If a valid AMD exists, it governs. A patient with capacity overrides this plan at any time. If unsigned, out of date or ambiguous: standard care.` },
    provision: { type: 'permit', ...(record.reviewDue ? { period: { end: record.reviewDue } } : {}), provision: ectprProvisions(record) },
  };
  const patient: FhirResource = {
    resourceType: 'Patient',
    id: record.patientId,
    identifier: [{ system: 'urn:sahara:uhid', value: record.patient.uhid, type: { text: 'Hospital UHID' } }],
    name: [{ text: record.patient.name }],
    ...(GENDER[record.patient.sex] ? { gender: GENDER[record.patient.sex] } : {}),
    ...(record.patient.dob ? { birthDate: record.patient.dob } : {}),
  };
  const practitioner: FhirResource = {
    resourceType: 'Practitioner',
    id: `${record.id}-consultant`,
    identifier: [{ system: 'urn:sahara:medical-registration', value: consultant.registrationNo ?? '', type: { text: 'Medical registration number' } }],
    name: [{ text: consultant.name }],
  };
  return {
    resourceType: 'Bundle',
    id: `${record.id}-fhir`,
    type: 'collection',
    timestamp: now.toISOString(),
    meta: { tag: [{ code: 'draft-format', display: ECTPR_FHIR_DRAFT_NOTE }] },
    entry: [
      { fullUrl: patientRef, resource: patient },
      { fullUrl: practitionerRef, resource: practitioner },
      { fullUrl: `urn:uuid:consent-${record.id}`, resource: consent },
    ],
  };
}
