'use client';

import React, { useState } from 'react';

export type AuthRole =
  | 'Dr Sujay · Clinical lead'
  | 'Dr Isha Menon · Emergency physician'
  | 'Anitha Rao · Care Coordinator';

const ROLES: { role: AuthRole; name: string; label: string; initials: string }[] = [
  { role: 'Dr Sujay · Clinical lead', name: 'Dr Sujay', label: 'Clinical lead', initials: 'SS' },
  { role: 'Dr Isha Menon · Emergency physician', name: 'Dr Isha Menon', label: 'Emergency physician', initials: 'IM' },
  { role: 'Anitha Rao · Care Coordinator', name: 'Anitha Rao', label: 'Care Coordinator', initials: 'AR' },
];

const DEMO_PATIENT = {
  hospitalId: 'CANCER-20418',
  name: 'Meera Raghavan',
  dob: '14/03/1964',
  accessCode: '482913',
} as const;

const DEMO_FAMILY_MEMBER = {
  name: 'Kavya Raghavan',
  relationship: 'Daughter',
} as const;

const CARE_TEAM_BRAND = {
  kicker: 'Care team',
  headline: 'Care, together.',
  lede: 'A source-linked, physician-reviewed plan for the next handoff.',
  features: [
    { num: '01', title: 'Source-linked', body: 'Each field starts with what was said.' },
    { num: '02', title: 'Physician-verified', body: 'Every release keeps the previous version.' },
    { num: '03', title: 'Purpose-logged', body: 'Access opens with a stated reason.' },
  ],
};

const FAMILY_BRAND = {
  kicker: 'Patients and families',
  headline: 'By your side.',
  lede: 'See what your team agreed and what comes next.',
  features: [
    { num: '01', title: 'Team-confirmed', body: 'The summary your doctor reviewed and approved.' },
    { num: '02', title: 'Next steps', body: 'Your next visit and a simple preparation list.' },
    { num: '03', title: 'Read-only', body: 'View everything. Nothing changes by mistake.' },
  ],
};

const PATIENT_BRAND = {
  kicker: 'Patient view',
  headline: 'Your care plan, close at hand.',
  lede: 'Open the summary, emergency card, and nearby care.',
  features: [
    { num: '01', title: 'My care plan', body: 'The summary your doctor verified.' },
    { num: '02', title: 'Emergency card', body: 'A wallet card for the ambulance crew.' },
    { num: '03', title: 'Care near me', body: 'Best supportive care, hospitals and clinics on a map.' },
  ],
};

function BrandPanel({ variant = 'care-team' }: { variant?: 'care-team' | 'family' | 'patient' }) {
  const content = variant === 'family' ? FAMILY_BRAND : variant === 'patient' ? PATIENT_BRAND : CARE_TEAM_BRAND;
  return (
    <div className="auth-brand-panel">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        className="auth-brand-image"
        src="/care-consultation.jpg"
        alt=""
        aria-hidden="true"
        loading="eager"
        decoding="async"
      />
      <div className="auth-brand-noise" aria-hidden="true" />
      <div className="auth-brand-inner">
        <div className="auth-brand-top">
          <div className="auth-brand-mark" aria-hidden="true">S</div>
          <div className="auth-brand-titles">
            <strong>Saathi</strong>
            <span>Care planning</span>
          </div>
        </div>

        <div className="auth-brand-lead">
          <p className="auth-brand-kicker">{content.kicker}</p>
          <h2 className="auth-brand-value">
            {content.headline}
          </h2>

        </div>



        <div className="auth-brand-foot">
          <span className="auth-brand-dot" aria-hidden="true" />

        </div>
      </div>
    </div>
  );
}

function RoleSelector({
  selectedRole,
  onSelect,
  idPrefix,
}: {
  selectedRole: AuthRole;
  onSelect: (role: AuthRole) => void;
  idPrefix: string;
}) {
  return (
    <div className="auth-field">
      <span className="auth-label">Continue as</span>
      <div className="auth-role-group" role="radiogroup" aria-label="Continue as">
        {ROLES.map((r) => {
          const checked = selectedRole === r.role;
          return (
            <div
              key={r.role}
              role="radio"
              aria-checked={checked}
              tabIndex={0}
              id={`${idPrefix}-${r.initials}`}
              className={checked ? 'auth-role-card is-selected' : 'auth-role-card'}
              onClick={() => onSelect(r.role)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelect(r.role);
                }
              }}
            >
              <span className="auth-role-avatar" aria-hidden="true">{r.initials}</span>
              <span className="auth-role-text">
                <strong>{r.name}</strong>
                <small>{r.label}</small>
              </span>
              <span className="auth-role-action" aria-hidden="true">Select</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function SimAuthNotice({ children }: { children: React.ReactNode }) {
  return <div className="auth-notice">{children}</div>;
}

export function LoginScreen(props: {
  onSignIn: (role: AuthRole) => void;
  onGoToSignup: () => void;
  onGoHome?: () => void;
  onGoToFamily?: () => void;
  onGoToPatient?: () => void;
}) {
  const { onSignIn, onGoToSignup, onGoHome, onGoToFamily, onGoToPatient } = props;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);
  const [selectedRole, setSelectedRole] = useState<AuthRole>('Dr Sujay · Clinical lead');
  const [error, setError] = useState('');

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      setError('Enter an email and password.');
      return;
    }
    setError('');
    onSignIn(selectedRole);
  }

  function openDemo() {
    setError('');
    onSignIn(selectedRole);
  }

  return (
    <div className="auth-shell">
      <BrandPanel />
      <div className="auth-form-side">
        <form className="auth-card" onSubmit={handleSubmit} noValidate>
          {onGoHome && (
            <button type="button" className="auth-textbutton lp-back-link" onClick={onGoHome}>
              Back
            </button>
          )}
          <span className="auth-door-chip is-care-team">Care team</span>
          <h1 className="auth-title">Care team</h1>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <RoleSelector selectedRole={selectedRole} onSelect={setSelectedRole} idPrefix="login-role" />

          <button type="button" className="auth-demo-submit" onClick={openDemo}>
            Continue
          </button>

          <details className="auth-manual">
            <summary>Enter details</summary>
            <div className="auth-manual-body">
              <div className="auth-field">
                <label className="auth-label" htmlFor="login-email">Work email</label>
                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  placeholder="name@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className="auth-field">
                <div className="auth-label-row">
                  <label className="auth-label" htmlFor="login-password">Password</label>
                  <button
                    type="button"
                    className="auth-toggle-visibility"
                    onClick={() => setShowPassword((v) => !v)}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>

              <label className="auth-checkbox-row">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                <span>Remember this device</span>
              </label>

              <button type="submit" className="auth-submit">Continue</button>
            </div>
          </details>

          <div className="auth-footer">
            New to Saathi?{' '}
            <button type="button" className="auth-textbutton" onClick={onGoToSignup}>
              Create profile
            </button>
          </div>

          {onGoToFamily && (
            <div className="auth-door-switch">
              Patient or family member?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToFamily}>
                Family
              </button>
            </div>
          )}

          {onGoToPatient && (
            <div className="auth-door-switch">
              Patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Patient
              </button>
            </div>
          )}

          <SimAuthNotice>
            Hospital accounts aren’t connected.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}

export function SignupScreen(props: {
  onSignUp: (role: AuthRole) => void;
  onGoToLogin: () => void;
  onGoHome?: () => void;
  onGoToFamily?: () => void;
  onGoToPatient?: () => void;
}) {
  const { onSignUp, onGoToLogin, onGoHome, onGoToFamily, onGoToPatient } = props;
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [hospital, setHospital] = useState('');
  const [department, setDepartment] = useState('Medical Oncology');
  const [selectedRole, setSelectedRole] = useState<AuthRole>('Dr Sujay · Clinical lead');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [ack, setAck] = useState(false);
  const [error, setError] = useState('');

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!fullName.trim() || !email.trim() || !hospital.trim() || !department.trim() || !password.trim() || !confirmPassword.trim()) {
      setError('Fill in the missing details.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (!ack) {
      setError('Confirm that this does not create a hospital account.');
      return;
    }
    setError('');
    onSignUp(selectedRole);
  }

  return (
    <div className="auth-shell">
      <BrandPanel />
      <div className="auth-form-side">
        <form className="auth-card" onSubmit={handleSubmit} noValidate>
          {onGoHome && (
            <button type="button" className="auth-textbutton lp-back-link" onClick={onGoHome}>
              Back
            </button>
          )}
          <span className="auth-door-chip is-care-team">Care team account</span>
          <h1 className="auth-title">Create your account</h1>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-name">Full name</label>
            <input
              id="signup-name"
              type="text"
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-email">Work email</label>
            <input
              id="signup-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="auth-field-row">
            <div className="auth-field">
              <label className="auth-label" htmlFor="signup-hospital">Hospital / Institution</label>
              <input
                id="signup-hospital"
                type="text"
                autoComplete="organization"
                value={hospital}
                onChange={(e) => setHospital(e.target.value)}
              />
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="signup-department">Department</label>
              <input
                id="signup-department"
                type="text"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
              />
            </div>
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-role">Your role</label>
            <select
              id="signup-role"
              value={selectedRole}
              onChange={(e) => setSelectedRole(e.target.value as AuthRole)}
            >
              {ROLES.map((r) => (
                <option key={r.role} value={r.role}>{r.name} · {r.label}</option>
              ))}
            </select>
          </div>

          <div className="auth-field-row">
            <div className="auth-field">
              <label className="auth-label" htmlFor="signup-password">Password</label>
              <input
                id="signup-password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="signup-confirm-password">Confirm password</label>
              <input
                id="signup-confirm-password"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>
          </div>

          <label className="auth-checkbox-row">
            <input
              type="checkbox"
              checked={ack}
              onChange={(e) => setAck(e.target.checked)}
            />
            <span>I understand this does not create a hospital account.</span>
          </label>

          <button type="submit" className="auth-submit">Create profile</button>

          <div className="auth-footer">
            Already have an account?{' '}
            <button type="button" className="auth-textbutton" onClick={onGoToLogin}>
              Sign in
            </button>
          </div>

          {onGoToFamily && (
            <div className="auth-door-switch">
              Patient or family member?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToFamily}>
                Family
              </button>
            </div>
          )}

          {onGoToPatient && (
            <div className="auth-door-switch">
              Patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Patient
              </button>
            </div>
          )}

          <SimAuthNotice>
            Hospital accounts aren’t connected.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}

export type FamilyMember = { name: string; relationship: string; patientId: string; verifiedSummaryOnly?: boolean };
export type PatientAccount = { hospitalId: string; name: string; dob?: string };

const FAMILY_RELATIONSHIPS = [
  'I am the patient',
  'Daughter',
  'Son',
  'Spouse or partner',
  'Parent',
  'Other family member',
  'Caregiver',
];

export function FamilyLoginScreen(props: {
  patients: Array<{ hospitalId: string; name: string }>;
  onFamilySignIn: (member: FamilyMember) => void;
  onGoToClinician: () => void;
  onGoHome?: () => void;
  onGoToPatient?: () => void;
}) {
  const { patients, onFamilySignIn, onGoToClinician, onGoHome, onGoToPatient } = props;
  const [patientId, setPatientId] = useState('');
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('Daughter');
  const [accessCode, setAccessCode] = useState('');
  const [error, setError] = useState('');

  function fillDemo() {
    setPatientId(DEMO_PATIENT.hospitalId);
    setName(DEMO_FAMILY_MEMBER.name);
    setRelationship(DEMO_FAMILY_MEMBER.relationship);
    setAccessCode(DEMO_PATIENT.accessCode);
    setError('');
  }

  function openDemo() {
    const matchedPatient = patients.find(
      (patient) => patient.hospitalId.toUpperCase() === DEMO_PATIENT.hospitalId,
    );
    if (!matchedPatient) {
      setError('This patient is not on the list.');
      return;
    }
    fillDemo();
    onFamilySignIn({
      name: DEMO_FAMILY_MEMBER.name,
      relationship: DEMO_FAMILY_MEMBER.relationship,
      patientId: matchedPatient.hospitalId,
    });
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!patientId.trim() || !name.trim() || !relationship.trim() || !accessCode.trim()) {
      setError('Fill in every field to continue.');
      return;
    }
    const matchedPatient = patients.find((patient) => patient.hospitalId === patientId.trim().toUpperCase());
    if (!matchedPatient) {
      setError('Patient not found. Check the ID.');
      return;
    }
    setError('');
    onFamilySignIn({ name: name.trim(), relationship, patientId: matchedPatient.hospitalId });
  }

  return (
    <div className="auth-shell">
      <BrandPanel variant="family" />
      <div className="auth-form-side">
        <form className="auth-card" onSubmit={handleSubmit} noValidate>
          {onGoHome && (
            <button type="button" className="auth-textbutton lp-back-link" onClick={onGoHome}>
              Back
            </button>
          )}
          <span className="auth-door-chip is-family">Patient & family</span>
          <h1 className="auth-title">Your care journey</h1>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <button type="button" className="auth-demo-member" onClick={openDemo}>
            <span className="auth-demo-avatar" aria-hidden="true">KR</span>
            <span className="auth-demo-text">
              <strong>{DEMO_FAMILY_MEMBER.name}</strong>
              <span>{DEMO_FAMILY_MEMBER.relationship} of {DEMO_PATIENT.name} · {DEMO_PATIENT.hospitalId}</span>
            </span>
            <span className="auth-demo-label">Continue</span>
          </button>

          <details className="auth-manual">
            <summary>Find a patient</summary>
            <div className="auth-manual-body">
              <div className="auth-field">
                <label className="auth-label" htmlFor="family-patient-id">Patient ID</label>
                <input
                  id="family-patient-id"
                  type="text"
                  placeholder="CANCER-20418"
                  value={patientId}
                  onChange={(e) => { setPatientId(e.target.value); setError(''); }}
                />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="family-name">Your name</label>
                <input
                  id="family-name"
                  type="text"
                  autoComplete="name"
                  value={name}
                  onChange={(e) => { setName(e.target.value); setError(''); }}
                />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="family-relationship">Relationship to patient</label>
                <select
                  id="family-relationship"
                  value={relationship}
                  onChange={(e) => { setRelationship(e.target.value); setError(''); }}
                >
                  {FAMILY_RELATIONSHIPS.map((r) => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="family-access-code">Code</label>
                <input
                  id="family-access-code"
                  type="text"
                  inputMode="numeric"
                  placeholder="6-digit code from your care team"
                  value={accessCode}
                  onChange={(e) => { setAccessCode(e.target.value); setError(''); }}
                />
              </div>

              <button type="submit" className="auth-submit">View care journey</button>
            </div>
          </details>

          <div className="auth-footer">
            Part of the care team?{' '}
            <button type="button" className="auth-textbutton" onClick={onGoToClinician}>
              Care team
            </button>
          </div>

          {onGoToPatient && (
            <div className="auth-door-switch">
              Are you the patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Patient
              </button>
            </div>
          )}

          <SimAuthNotice>
            Hospital accounts aren’t connected.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}

export function PatientLoginScreen(props: {
  onPatientSignIn: (account: PatientAccount) => void;
  onGoToFamily?: () => void;
  onGoToClinician?: () => void;
  onGoHome?: () => void;
  patients?: Array<{ hospitalId: string; name: string }>;
}): React.JSX.Element {
  const { onPatientSignIn, onGoToFamily, onGoToClinician, onGoHome, patients } = props;
  const [patientId, setPatientId] = useState('');
  const [name, setName] = useState('');
  const [dob, setDob] = useState('');
  const [accessCode, setAccessCode] = useState('');
  const [error, setError] = useState('');

  function fillDemo() {
    setPatientId(DEMO_PATIENT.hospitalId);
    setName(DEMO_PATIENT.name);
    setDob(DEMO_PATIENT.dob);
    setAccessCode(DEMO_PATIENT.accessCode);
    setError('');
  }

  function openDemo() {
    const matchedPatient = patients?.find(
      (patient) => patient.hospitalId.toUpperCase() === DEMO_PATIENT.hospitalId,
    );
    if (patients && !matchedPatient) {
      setError('This patient is not on the list.');
      return;
    }
    fillDemo();
    onPatientSignIn({
      hospitalId: matchedPatient?.hospitalId ?? DEMO_PATIENT.hospitalId,
      name: matchedPatient?.name ?? DEMO_PATIENT.name,
    });
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!patientId.trim() || !name.trim() || !dob.trim() || !accessCode.trim()) {
      setError('Fill in every field to continue.');
      return;
    }
    const trimmedId = patientId.trim().toUpperCase();
    const matchedPatient = patients?.find((p) => p.hospitalId.toUpperCase() === trimmedId);
    if (patients && !matchedPatient) {
      setError('Patient not found. Check the ID.');
      return;
    }
    setError('');
    onPatientSignIn({ hospitalId: trimmedId, name: matchedPatient?.name ?? name.trim() });
  }

  return (
    <div className="auth-shell">
      <BrandPanel variant="patient" />
      <div className="auth-form-side">
        <form className="auth-card" onSubmit={handleSubmit} noValidate>
          {onGoHome && (
            <button type="button" className="auth-textbutton lp-back-link" onClick={onGoHome}>
              Back
            </button>
          )}
          <span className="auth-door-chip is-patient">Patient</span>
          <h1 className="auth-title">Patient</h1>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <button type="button" className="auth-demo-member" onClick={openDemo}>
            <span className="auth-demo-avatar" aria-hidden="true">MR</span>
            <span className="auth-demo-text">
              <strong>{DEMO_PATIENT.name}</strong>
              <span>Patient · {DEMO_PATIENT.hospitalId} · {DEMO_PATIENT.dob}</span>
            </span>
            <span className="auth-demo-label">Continue</span>
          </button>

          <details className="auth-manual">
            <summary>Find a patient</summary>
            <div className="auth-manual-body">
              <div className="auth-field">
                <label className="auth-label" htmlFor="patient-id">Patient ID (MRN)</label>
                <input
                  id="patient-id"
                  type="text"
                  placeholder="CANCER-20418"
                  value={patientId}
                  onChange={(e) => { setPatientId(e.target.value); setError(''); }}
                />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="patient-name">Full name</label>
                <input
                  id="patient-name"
                  type="text"
                  autoComplete="name"
                  value={name}
                  onChange={(e) => { setName(e.target.value); setError(''); }}
                />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="patient-dob">Date of birth</label>
                <input
                  id="patient-dob"
                  type="text"
                  placeholder="DD/MM/YYYY"
                  autoComplete="bday"
                  value={dob}
                  onChange={(e) => { setDob(e.target.value); setError(''); }}
                />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="patient-access-code">Code</label>
                <input
                  id="patient-access-code"
                  type="text"
                  inputMode="numeric"
                  placeholder="6-digit code from your care team"
                  value={accessCode}
                  onChange={(e) => { setAccessCode(e.target.value); setError(''); }}
                />
              </div>

              <button type="submit" className="auth-submit">Sign in</button>
            </div>
          </details>

          {onGoToFamily && (
            <div className="auth-door-switch">
              Signing in for a relative?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToFamily}>
                Family
              </button>
            </div>
          )}

          {onGoToClinician && (
            <div className="auth-door-switch">
              Care team member?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToClinician}>
                Sign in
              </button>
            </div>
          )}

          <SimAuthNotice>
            Hospital accounts aren’t connected.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}
