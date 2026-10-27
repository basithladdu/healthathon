import { INDIA_STATES, normalizeEmail } from './feedback-schema.ts';

export type HospitalSurveyErrors = Record<string, string>;

// UI validation mirrors the intake schema, but checks only the visible step.
// Inactive Other/supply drafts are omitted by the survey's payload builder.
export function validateHospitalSurveyStep(
  step: number,
  fields: Record<string, string>,
  services: readonly string[],
  locationConfirmed: boolean,
  accuracy: boolean,
  consent: boolean,
): HospitalSurveyErrors {
  const errors: HospitalSurveyErrors = {};
  const value = (key: string) => fields[key] ?? '';
  const choice = (key: string, options: readonly string[], message: string) => {
    if (!options.includes(value(key))) errors[key] = message;
  };
  const text = (key: string, min: number, max: number, label: string) => {
    const raw = value(key), length = raw.trim().length;
    if (length < min) errors[key] = `Enter ${label} (at least ${min} characters).`;
    else if (length > max) errors[key] = `Keep ${label} within ${max} characters.`;
    else if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(raw)) errors[key] = `Remove unsupported characters from ${label}.`;
  };
  const other = (selected: string, detail: string, label: string) => {
    if (value(selected) === 'other') text(detail, 2, 120, label);
  };

  if (step === 0) {
    choice('role', ['oncologist','palliative_physician','physician','resident','nurse','pharmacist','other'], 'Choose your professional role.');
    other('role', 'roleOther', 'your professional role');
    text('fullName', 2, 120, 'your full name');
    choice('council', ['nmc','state_medical','state_nursing','state_pharmacy','other'], 'Choose your registering council.');
    other('council', 'councilOther', 'the registering council name');
    if (value('council') && value('council') !== 'nmc') choice('registrationState', INDIA_STATES, 'Choose your registration state or union territory.');
    if (!/^[A-Za-z0-9/-]{3,20}$/.test(value('registrationNumber').trim())) errors.registrationNumber = 'Enter 3–20 letters, numbers, / or - for your registration number.';
    if (normalizeEmail(value('email')) === null) errors.email = 'Enter a valid email address, or leave it blank.';
  }

  if (step === 1) {
    text('hospitalName', 2, 160, 'the hospital or institution name');
    choice('institutionType', ['government_hospital','government_medical_college','private_hospital','private_medical_college','private_clinic','ngo_hospice','other'], 'Choose the type of institution.');
    other('institutionType', 'institutionOther', 'the type of institution');
    choice('connection', ['works_here','refers_here','other'], 'Choose your connection to this hospital.');
    other('connection', 'connectionOther', 'your connection to this hospital');
    choice('state', INDIA_STATES, 'Choose the hospital’s state or union territory.');
    text('city', 2, 80, 'the city or district');
    text('hospitalAddress', 5, 500, 'the hospital address');
    const lat = Number(value('latitude')), lng = Number(value('longitude'));
    if (!locationConfirmed || !value('latitude').trim() || !value('longitude').trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || lat < 6 || lat > 38 || lng < 68 || lng > 98) {
      errors.location = 'Check the hospital pin on the map and confirm it before continuing.';
    }
  }

  if (step === 2) {
    if (!services.length) errors.services = 'Choose at least one service option.';
    else if (services.length > 3 || new Set(services).size !== services.length || services.some(service => !['opd','ipd','home','none','unknown'].includes(service)) || (services.some(service => service === 'none' || service === 'unknown') && services.length !== 1)) {
      errors.services = 'Choose the available services, or choose None or I don’t know on its own.';
    }
    choice('morphine', ['yes','no','unknown'], 'Choose whether oral morphine has been dispensed.');
    if (value('morphine') === 'yes') choice('supply', ['','regular','intermittent','unknown'], 'Choose a supply option, or leave it unanswered.');
    text('palliativePhysicians', 0, 1000, 'the physician and department details');
    text('familyNotes', 0, 1000, 'the information for families');
  }

  if (step === 3) {
    if (!accuracy) errors.accuracy = 'Confirm that the information is accurate before sending.';
    if (!consent) errors.consent = 'Agree to how this information is used before sending.';
  }
  return errors;
}
