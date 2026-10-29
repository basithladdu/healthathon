import { INDIA_STATES } from './feedback-schema.ts';

export type HospitalSurveyErrors = Record<string, string>;

// Only the contributor's name (initials are fine), the hospital name and consent are required for the v4 intake.
// Keep basic bounds for malformed input; incomplete answers are reviewed later.
export function validateHospitalSurveyStep(
  step: number,
  fields: Record<string, string>,
  services: readonly string[],
  locationConfirmed: boolean,
  _accuracy: boolean,
  consent: boolean,
): HospitalSurveyErrors {
  const errors: HospitalSurveyErrors = {};
  const value = (key: string) => fields[key] ?? '';
  const choice = (key: string, options: readonly string[], message: string) => {
    if (value(key) && !options.includes(value(key))) errors[key] = message;
  };
  const text = (key: string, min: number, max: number, label: string) => {
    const raw = value(key), length = raw.trim().length;
    if (length < min) errors[key] = `Enter ${label}.`;
    else if (length > max) errors[key] = `Keep ${label} within ${max} characters.`;
    else if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(raw)) errors[key] = `Remove unsupported characters from ${label}.`;
  };
  const other = (selected: string, detail: string, label: string) => {
    if (value(selected) === 'other') text(detail, 0, 120, label);
  };

  if (step === 0) {
    choice('role', ['oncologist','palliative_physician','physician','resident','nurse','pharmacist','other'], 'Choose a listed role, or leave it unanswered.');
    other('role', 'roleOther', 'your professional role');
    text('fullName', 2, 120, 'your name or initials');
    choice('council', ['nmc','state_medical','state_nursing','state_pharmacy','other'], 'Choose a listed council, or leave it unanswered.');
    other('council', 'councilOther', 'the registering council name');
    if (value('council') !== 'nmc') choice('registrationState', INDIA_STATES, 'Choose a listed state, or leave it unanswered.');
    text('registrationNumber', 0, 120, 'your registration number');
    text('email', 0, 254, 'your email address');
  }

  if (step === 1) {
    text('hospitalName', 1, 160, 'the hospital or institution name');
    choice('institutionType', ['government_hospital','government_medical_college','private_hospital','private_medical_college','private_clinic','ngo_hospice','other'], 'Choose a listed institution type, or leave it unanswered.');
    other('institutionType', 'institutionOther', 'the type of institution');
    choice('connection', ['works_here','refers_here','other'], 'Choose a listed connection, or leave it unanswered.');
    other('connection', 'connectionOther', 'your connection to this hospital');
    choice('state', INDIA_STATES, 'Choose a listed state, or leave it unanswered.');
    text('city', 0, 80, 'the city or district');
    text('hospitalAddress', 0, 500, 'the hospital address');
    if (locationConfirmed) {
      const lat = Number(value('latitude')), lng = Number(value('longitude'));
      if (!value('latitude').trim() || !value('longitude').trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || lat < 6 || lat > 38 || lng < 68 || lng > 98) {
        errors.location = 'Check this map pin, or continue without it.';
      }
    }
  }

  if (step === 2) {
    if (services.length > 3 || new Set(services).size !== services.length || services.some(service => !['opd','ipd','home','none','unknown'].includes(service)) || (services.some(service => service === 'none' || service === 'unknown') && services.length !== 1)) {
      errors.services = 'Choose the available services, or choose None or I don’t know on its own.';
    }
    choice('morphine', ['yes','no','unknown'], 'Choose a listed answer, or leave it unanswered.');
    if (value('morphine') === 'yes') choice('supply', ['regular','intermittent','unknown'], 'Choose a supply option, or leave it unanswered.');
    text('palliativePhysicians', 0, 1000, 'the physician and department details');
    text('familyNotes', 0, 1000, 'the information for families');
  }

  if (step === 3 && !consent) errors.consent = 'Agree to how this information is used before sending.';
  return errors;
}
