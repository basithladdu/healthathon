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

const CARE_TEAM_BRAND = {
  kicker: 'The handoff layer',
  headline: 'The conversation survives the handoff.',
  lede: 'A verified goals-of-care summary the next clinician can open, trust and act on — without asking the family to start again.',
  features: [
    { num: '01', title: 'Source-linked', body: 'Every field traces back to what was actually said.' },
    { num: '02', title: 'Physician-verified', body: 'Released as a new version. Nothing is overwritten.' },
    { num: '03', title: 'Purpose-logged', body: 'The reason for access is recorded before the record opens.' },
  ],
};

const FAMILY_BRAND = {
  kicker: 'For patients and families',
  headline: 'Stay in the loop on your care.',
  lede: 'See what your care team has confirmed, what happens next, and who to contact, in plain language.',
  features: [
    { num: '01', title: 'What the team confirmed', body: 'The summary your doctor reviewed and approved. Never a rough draft.' },
    { num: '02', title: 'What happens next', body: 'Your next visit, and a simple checklist to prepare for it.' },
    { num: '03', title: 'Read-only', body: 'You can view everything. Nothing can be changed by mistake.' },
  ],
};

const PATIENT_BRAND = {
  kicker: 'For patients',
  headline: 'Your care plan, in your hands.',
  lede: 'See what you and your care team agreed, keep an emergency card with you, and find care close to home.',
  features: [
    { num: '01', title: 'My care plan', body: 'The summary your doctor verified, in plain words.' },
    { num: '02', title: 'Emergency card', body: 'A wallet card with a QR code for the ambulance crew and the emergency department.' },
    { num: '03', title: 'Care near me', body: 'Palliative care, hospitals and clinics near you, on a real map.' },
  ],
};

function BrandPanel({ variant = 'care-team' }: { variant?: 'care-team' | 'family' | 'patient' }) {
  const content = variant === 'family' ? FAMILY_BRAND : variant === 'patient' ? PATIENT_BRAND : CARE_TEAM_BRAND;
  return (
    <div className="auth-brand-panel">
      <div className="auth-brand-noise" aria-hidden="true" />
      <div className="auth-brand-inner">
        <div className="auth-brand-top">
          <div className="auth-brand-mark" aria-hidden="true">CL</div>
          <div className="auth-brand-titles">
            <strong>Continuity Loop</strong>
            <span>Goals-of-Care Workflow</span>
          </div>
        </div>

        <div className="auth-brand-lead">
          <p className="auth-brand-kicker">{content.kicker}</p>
          <h2 className="auth-brand-value">
            {content.headline}
          </h2>
          <p className="auth-brand-lede">
            {content.lede}
          </p>
        </div>

        <ol className="auth-brand-features">
          {content.features.map((f) => (
            <li key={f.num}>
              <span className="auth-brand-num">{f.num}</span>
              <span className="auth-brand-feature-body">
                <strong>{f.title}</strong>
                <span>{f.body}</span>
              </span>
            </li>
          ))}
        </ol>

        <div className="auth-brand-foot">
          <span className="auth-brand-dot" aria-hidden="true" />
          Synthetic demonstration environment · No real patient data
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
      setError('Enter an email and password to continue — any value works in this demo.');
      return;
    }
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
              ← Back to overview
            </button>
          )}
          <span className="auth-door-chip is-care-team">Care team sign-in</span>
          <span className="auth-eyebrow">Welcome back</span>
          <h1 className="auth-title">Sign in to Continuity Loop</h1>
          <p className="auth-sub">Use any credentials — this is a simulated sign-in.</p>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <div className="auth-field">
            <label className="auth-label" htmlFor="login-email">Work email</label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="sujay@synthetic-hospital.demo"
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

          <RoleSelector selectedRole={selectedRole} onSelect={setSelectedRole} idPrefix="login-role" />

          <label className="auth-checkbox-row">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
            />
            <span>Remember this device</span>
          </label>

          <button type="submit" className="auth-submit">Sign in</button>

          <div className="auth-footer">
            New to Continuity Loop?{' '}
            <button type="button" className="auth-textbutton" onClick={onGoToSignup}>
              Create an account
            </button>
          </div>

          {onGoToFamily && (
            <div className="auth-door-switch">
              Patient or family member?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToFamily}>
                Use family access →
              </button>
            </div>
          )}

          {onGoToPatient && (
            <div className="auth-door-switch">
              Patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Use patient access
              </button>
            </div>
          )}

          <SimAuthNotice>
            Simulated authentication. No credentials are checked, stored or transmitted. Any email and password will sign you in.
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
      setError('Fill in every field to continue — any values work in this demo.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (!ack) {
      setError('Please confirm you understand this is a synthetic demonstration.');
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
              ← Back to overview
            </button>
          )}
          <span className="auth-door-chip is-care-team">Care team account</span>
          <span className="auth-eyebrow">Get started</span>
          <h1 className="auth-title">Create your account</h1>
          <p className="auth-sub">Simulated registration for the demonstration.</p>

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
            <span>I understand this is a synthetic demonstration and will not enter real patient information.</span>
          </label>

          <button type="submit" className="auth-submit">Create account</button>

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
                Use family access →
              </button>
            </div>
          )}

          {onGoToPatient && (
            <div className="auth-door-switch">
              Patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Use patient access
              </button>
            </div>
          )}

          <SimAuthNotice>
            Simulated authentication. No credentials are checked, stored or transmitted. Any email and password will sign you in.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}

export type FamilyMember = { name: string; relationship: string; patientId: string };
export type PatientAccount = { hospitalId: string; name: string };

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
  onFamilySignIn: (member: FamilyMember) => void;
  onGoToClinician: () => void;
  onGoHome?: () => void;
  onGoToPatient?: () => void;
}) {
  const { onFamilySignIn, onGoToClinician, onGoHome, onGoToPatient } = props;
  const [patientId, setPatientId] = useState('');
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('Daughter');
  const [accessCode, setAccessCode] = useState('');
  const [error, setError] = useState('');

  function fillDemo() {
    setPatientId('CANCER-20418');
    setName('Kavya Raghavan');
    setRelationship('Daughter');
    setAccessCode('482913');
    setError('');
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!patientId.trim() || !name.trim() || !relationship.trim() || !accessCode.trim()) {
      setError('Fill in every field to continue. Any values work in this demo.');
      return;
    }
    setError('');
    onFamilySignIn({ name, relationship, patientId });
  }

  return (
    <div className="auth-shell">
      <BrandPanel variant="family" />
      <div className="auth-form-side">
        <form className="auth-card" onSubmit={handleSubmit} noValidate>
          {onGoHome && (
            <button type="button" className="auth-textbutton lp-back-link" onClick={onGoHome}>
              ← Back to overview
            </button>
          )}
          <span className="auth-door-chip is-family">Patient & family</span>
          <span className="auth-eyebrow">Family access</span>
          <h1 className="auth-title">View your care journey</h1>
          <p className="auth-sub">For patients and family members. This is simulated access, so no identity is checked.</p>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <button type="button" className="auth-demo-member" onClick={fillDemo}>
            <span className="auth-demo-avatar" aria-hidden="true">KR</span>
            <span className="auth-demo-text">
              <strong>Kavya Raghavan</strong>
              <span>Daughter of Meera Raghavan · CANCER-20418</span>
            </span>
            <span className="auth-demo-label">Use demo</span>
          </button>

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
            <label className="auth-label" htmlFor="family-access-code">Access code</label>
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

          <div className="auth-footer">
            Part of the care team?{' '}
            <button type="button" className="auth-textbutton" onClick={onGoToClinician}>
              Clinician sign in
            </button>
          </div>

          {onGoToPatient && (
            <div className="auth-door-switch">
              Are you the patient?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToPatient}>
                Use patient access
              </button>
            </div>
          )}

          <SimAuthNotice>
            Simulated family access. No identity is verified and nothing is stored. A real portal would need hospital-issued invitations and identity checks.
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
    setPatientId('CANCER-20418');
    setName('Meera Raghavan');
    setDob('14/03/1964');
    setAccessCode('482913');
    setError('');
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!patientId.trim() || !name.trim() || !dob.trim() || !accessCode.trim()) {
      setError('Fill in every field to continue. Any values work in this demo.');
      return;
    }
    const trimmedId = patientId.trim().toUpperCase();
    const matchedPatient = patients?.find((p) => p.hospitalId.toUpperCase() === trimmedId);
    if (patients && !matchedPatient) {
      setError('No demo patient has that ID. Use the demo card above.');
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
              ← Back to overview
            </button>
          )}
          <span className="auth-door-chip is-patient">Patient</span>
          <span className="auth-eyebrow">Welcome</span>
          <h1 className="auth-title">Patient sign-in</h1>
          <p className="auth-sub">Use the demo patient below, or type any details — this is a simulated sign-in.</p>

          {error && <div className="auth-alert" role="alert">{error}</div>}

          <button type="button" className="auth-demo-member" onClick={fillDemo}>
            <span className="auth-demo-avatar" aria-hidden="true">MR</span>
            <span className="auth-demo-text">
              <strong>Meera Raghavan</strong>
              <span>Patient · CANCER-20418 · 14/03/1964</span>
            </span>
            <span className="auth-demo-label">Use demo</span>
          </button>

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
            <label className="auth-label" htmlFor="patient-access-code">Access code</label>
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

          {onGoToFamily && (
            <div className="auth-door-switch">
              Signing in for a relative?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToFamily}>
                Use family access
              </button>
            </div>
          )}

          {onGoToClinician && (
            <div className="auth-door-switch">
              Care team member?{' '}
              <button type="button" className="auth-textbutton" onClick={onGoToClinician}>
                Sign in here
              </button>
            </div>
          )}

          <SimAuthNotice>
            Simulated sign-in. Nothing you type is checked, stored or sent.
          </SimAuthNotice>
        </form>
      </div>
    </div>
  );
}
