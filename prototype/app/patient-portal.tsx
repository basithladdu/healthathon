'use client';

import { useMemo, useState } from 'react';

export type DecisionMaker = {
  name: string;
  relationship: string;
  phone: string;
  basis: '' | 'Named by patient' | 'Family consensus' | 'Named in AMD' | 'Court-appointed';
};

export type PatientDetails = {
  fullName: string;
  dob: string;
  sex: string;
  address: string;
  phone: string;
  language: string;
  primaryDecisionMaker: DecisionMaker;
  alternateDecisionMaker: DecisionMaker;
  localPhysician: { name: string; clinic: string; phone: string };
};

export type ConsentRecord = {
  versionLabel: string;
  typedName: string;
  declaration: 'self' | 'delegated';
  signedAt: string;
};

export type PastVisit = {
  date: string;
  time: string;
  type: string;
  mode: string;
  status: string;
  outcome?: string;
};

export type CareTeamMember = {
  name: string;
  role: string;
  organisation: string;
  contactNote: string;
  lastVisited: string | null;
  visits: PastVisit[];
};

export type TimelineEntry = {
  date: string;
  title: string;
  body: string;
  kind: 'conversation' | 'appointment' | 'outreach' | 'version' | 'consent' | 'card' | 'review' | 'details';
};

const KIND_LABELS: Record<TimelineEntry['kind'], string> = {
  conversation: 'Conversation',
  appointment: 'Appointment',
  outreach: 'Call',
  version: 'Care plan version',
  consent: 'Consent',
  card: 'Emergency card',
  review: 'Review request',
  details: 'Details updated',
};

const BASIS_OPTIONS: DecisionMaker['basis'][] = [
  '',
  'Named by patient',
  'Family consensus',
  'Named in AMD',
  'Court-appointed',
];

const ALWAYS_PROVIDED = [
  'Relief of pain, using opioids in adequate doses, by whatever route works',
  'Relief of breathlessness — opioid, anxiolytic, fan, positioning, oxygen if it helps',
  'Sedation for distress that cannot be relieved any other way',
  'Control of nausea, secretions, agitation, itch, hiccups',
  'Mouth care and eye care, given often',
  'Comfortable positioning; turning as tolerated; air mattress to prevent pressure sores',
  'Skin and wound care; management of odour',
  'Bowel care — assessment and management of constipation',
  'Bladder care — assessment and management of retention and incontinence',
  'Privacy, dignity, and gentle handling in every personal-care task',
  'Presence of family; spiritual and religious support as the patient wishes',
  'Honest, unhurried communication with the patient and the family, every day',
];

function sameDecisionMaker(a: DecisionMaker, b: DecisionMaker): boolean {
  return a.name === b.name && a.relationship === b.relationship && a.phone === b.phone && a.basis === b.basis;
}

function sameDetails(a: PatientDetails, b: PatientDetails): boolean {
  return (
    a.fullName === b.fullName &&
    a.dob === b.dob &&
    a.sex === b.sex &&
    a.address === b.address &&
    a.phone === b.phone &&
    a.language === b.language &&
    sameDecisionMaker(a.primaryDecisionMaker, b.primaryDecisionMaker) &&
    sameDecisionMaker(a.alternateDecisionMaker, b.alternateDecisionMaker) &&
    a.localPhysician.name === b.localPhysician.name &&
    a.localPhysician.clinic === b.localPhysician.clinic &&
    a.localPhysician.phone === b.localPhysician.phone
  );
}

const DOB_PATTERN = /^\d{2}\/\d{2}\/\d{4}$/;

export function MyCarePlan(props: {
  patientName: string;
  versionLabel: string;
  verifiedBy: string;
  verifiedOn: string;
  fields: Array<{ label: string; value: string }>;
  quickView: React.ReactNode;
  consent: ConsentRecord | null;
  onGoToConsent: () => void;
}) {
  const { versionLabel, verifiedBy, verifiedOn, fields, quickView, consent, onGoToConsent } = props;
  const consentForVersion = consent && consent.versionLabel === versionLabel ? consent : null;

  return (
    <section className="ptl-page">
      <p className="ptl-eyebrow">My care plan</p>
      <h1 className="ptl-heading">What you and your care team agreed</h1>
      <p className="ptl-lede">A plain-language summary of the plan your doctor has released.</p>

      <div className="ptl-status-row">
        <p className="ptl-status-line">
          {versionLabel} · verified by {verifiedBy} on {verifiedOn}
        </p>
        {consentForVersion ? (
          <span className="ptl-chip ptl-chip--safe">You gave consent on {consentForVersion.signedAt}</span>
        ) : (
          <span className="ptl-chip ptl-chip--medium">
            Your consent is not recorded yet
            <button type="button" className="ptl-link-btn" onClick={onGoToConsent}>
              Review and give consent →
            </button>
          </span>
        )}
      </div>

      {fields.length === 0 ? (
        <p className="ptl-empty">Your care team has not released a written summary yet.</p>
      ) : (
        <div className="ptl-card-grid">
          {fields.map((f) => (
            <div className="ptl-card ptl-def-card" key={f.label}>
              <p className="ptl-overline">{f.label}</p>
              <p className="ptl-def-value">{f.value}</p>
            </div>
          ))}
        </div>
      )}

      <div className="ptl-quickview">{quickView}</div>

      <section className="ptl-section">
        <h2 className="ptl-h2">What will always be provided</h2>
        <p className="ptl-body-text">
          Nothing in this plan reduces care. These will always continue, wherever you are.
        </p>
        <div className="ptl-checklist ptl-two-col">
          {ALWAYS_PROVIDED.map((item) => (
            <div className="ptl-check-item" key={item}>
              <span className="ptl-check-mark" aria-hidden="true">✓</span>
              <span>{item}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="ptl-section">
        <h2 className="ptl-h2">Want to change something?</h2>
        <p className="ptl-body-text">
          Your wishes can change at any time. Tell your care team — what you say now always comes first. Your
          doctor updates the plan and releases a new version; the old one is kept.
        </p>
      </section>

      <p className="ptl-footer-note">
        This summary documents a conversation. It is not a treatment order or a legal directive. Synthetic
        demonstration record.
      </p>
    </section>
  );
}

export function MyTimeline(props: { audience: 'patient' | 'family'; patientName: string; entries: TimelineEntry[] }) {
  const { audience, patientName, entries } = props;
  const eyebrow = audience === 'family' ? 'Care journey' : 'My care journey';
  const heading = audience === 'family' ? `${patientName}'s care so far` : 'Everything so far, in order';

  return (
    <section className="ptl-page">
      <p className="ptl-eyebrow">{eyebrow}</p>
      <h1 className="ptl-heading">{heading}</h1>
      <p className="ptl-lede">A record of conversations, visits, and updates, oldest to newest.</p>

      {entries.length === 0 ? (
        <p className="ptl-empty">Nothing has been recorded yet.</p>
      ) : (
        <ol className="ptl-timeline">
          {entries.map((e, i) => (
            <li className="ptl-timeline-item" key={`${e.date}-${i}`}>
              <p className="ptl-overline">{e.date}</p>
              <div className="ptl-timeline-head">
                <span className={`ptl-kind-chip ptl-kind-chip--${e.kind}`}>{KIND_LABELS[e.kind]}</span>
                <h3 className="ptl-card-title">{e.title}</h3>
              </div>
              <p className="ptl-body-text">{e.body}</p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

export function MyDoctors(props: { audience: 'patient' | 'family'; patientName: string; members: CareTeamMember[] }) {
  const { audience, patientName, members } = props;
  const eyebrow = audience === 'family' ? 'Care team' : 'My doctors & care team';
  const heading =
    audience === 'family' ? `People who have cared for ${patientName}` : 'People who have cared for you';

  return (
    <section className="ptl-page">
      <p className="ptl-eyebrow">{eyebrow}</p>
      <h1 className="ptl-heading">{heading}</h1>
      <p className="ptl-lede">Everyone on your care team, and when you last saw them.</p>

      {members.length === 0 ? (
        <p className="ptl-empty">No care team recorded yet.</p>
      ) : (
        <div className="ptl-card-grid">
          {members.map((m, i) => (
            <div className="ptl-card ptl-doctor-card" key={`${m.name}-${i}`}>
              <h3 className="ptl-card-title">{m.name}</h3>
              <p className="ptl-doctor-role">{m.role}</p>
              <p className="ptl-doctor-org">{m.organisation}</p>
              <p className="ptl-body-text">
                {m.lastVisited ? `Last seen ${m.lastVisited}` : 'No visits recorded yet'}
              </p>
              <p className="ptl-body-text">{m.contactNote}</p>
              {m.visits.length > 0 && (
                <ul className="ptl-visit-list">
                  {m.visits.map((v, vi) => (
                    <li className="ptl-visit-item" key={`${v.date}-${vi}`}>
                      <span className="ptl-visit-when">{v.date} · {v.time}</span>
                      <span>{v.type}</span>
                      <span>{v.mode}</span>
                      <span>{v.status}</span>
                      {v.outcome && <span>{v.outcome}</span>}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export function MyDetails(props: {
  details: PatientDetails;
  onSave: (next: PatientDetails) => void;
  clinicalItems: Array<{ label: string; value: string }>;
  reviewRequestedOn: string | null;
  onRequestReview: () => void;
}) {
  const { details, onSave, clinicalItems, reviewRequestedOn, onRequestReview } = props;
  const [form, setForm] = useState<PatientDetails>(details);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);

  const dirty = useMemo(() => !sameDetails(form, details), [form, details]);

  function updateField<K extends keyof PatientDetails>(key: K, value: PatientDetails[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function updateDecisionMaker(which: 'primaryDecisionMaker' | 'alternateDecisionMaker', patch: Partial<DecisionMaker>) {
    setForm((prev) => ({ ...prev, [which]: { ...prev[which], ...patch } }));
    setSaved(false);
  }

  function updateLocalPhysician(patch: Partial<PatientDetails['localPhysician']>) {
    setForm((prev) => ({ ...prev, localPhysician: { ...prev.localPhysician, ...patch } }));
    setSaved(false);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!form.fullName.trim()) nextErrors.fullName = 'Full name is required.';
    if (!form.dob.trim()) {
      nextErrors.dob = 'Date of birth is required.';
    } else if (!DOB_PATTERN.test(form.dob.trim())) {
      nextErrors.dob = 'Date of birth must be in DD/MM/YYYY format.';
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    onSave(form);
    setSaved(true);
  }

  function handleDiscard() {
    setForm(details);
    setErrors({});
    setSaved(false);
  }

  const errorList = Object.values(errors);

  return (
    <section className="ptl-page">
      <p className="ptl-eyebrow">My details</p>
      <h1 className="ptl-heading">Keep your details up to date</h1>
      <p className="ptl-lede">Your demographics, decision-makers, and local doctor.</p>

      {errorList.length > 0 && (
        <div className="ptl-alert" role="alert">
          {errorList.map((msg) => (
            <p key={msg}>{msg}</p>
          ))}
        </div>
      )}

      <form className="ptl-form" onSubmit={handleSubmit}>
        <fieldset className="ptl-fieldset">
          <legend className="ptl-legend">About you</legend>
          <div className="ptl-field-grid ptl-two-col">
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-fullname">Full name</label>
              <input
                id="ptl-fullname"
                className="ptl-input"
                autoComplete="name"
                value={form.fullName}
                onChange={(e) => updateField('fullName', e.target.value)}
              />
              {errors.fullName && <p className="ptl-field-error" role="alert">{errors.fullName}</p>}
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-dob">Date of birth (DD/MM/YYYY)</label>
              <input
                id="ptl-dob"
                className="ptl-input"
                autoComplete="bday"
                placeholder="DD/MM/YYYY"
                value={form.dob}
                onChange={(e) => updateField('dob', e.target.value)}
              />
              {errors.dob && <p className="ptl-field-error" role="alert">{errors.dob}</p>}
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-sex">Sex</label>
              <input id="ptl-sex" className="ptl-input" value={form.sex} onChange={(e) => updateField('sex', e.target.value)} />
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-phone">Phone</label>
              <input
                id="ptl-phone"
                className="ptl-input"
                autoComplete="tel"
                value={form.phone}
                onChange={(e) => updateField('phone', e.target.value)}
              />
            </div>
            <div className="ptl-field ptl-field--wide">
              <label className="ptl-label" htmlFor="ptl-address">Address</label>
              <textarea
                id="ptl-address"
                className="ptl-textarea"
                autoComplete="street-address"
                value={form.address}
                onChange={(e) => updateField('address', e.target.value)}
              />
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-lang">Preferred language</label>
              <input
                id="ptl-lang"
                className="ptl-input"
                value={form.language}
                onChange={(e) => updateField('language', e.target.value)}
              />
            </div>
          </div>
        </fieldset>

        <fieldset className="ptl-fieldset">
          <legend className="ptl-legend">Who decides if you cannot</legend>
          <div className="ptl-field-grid ptl-two-col">
            <div className="ptl-decision-block">
              <p className="ptl-sub-legend">Primary decision-maker</p>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-pdm-name">Name</label>
                <input id="ptl-pdm-name" className="ptl-input" value={form.primaryDecisionMaker.name} onChange={(e) => updateDecisionMaker('primaryDecisionMaker', { name: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-pdm-rel">Relationship</label>
                <input id="ptl-pdm-rel" className="ptl-input" value={form.primaryDecisionMaker.relationship} onChange={(e) => updateDecisionMaker('primaryDecisionMaker', { relationship: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-pdm-phone">Phone (must be reachable 24 h)</label>
                <input id="ptl-pdm-phone" className="ptl-input" autoComplete="tel" value={form.primaryDecisionMaker.phone} onChange={(e) => updateDecisionMaker('primaryDecisionMaker', { phone: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-pdm-basis">Basis for authority</label>
                <select id="ptl-pdm-basis" className="ptl-select" value={form.primaryDecisionMaker.basis} onChange={(e) => updateDecisionMaker('primaryDecisionMaker', { basis: e.target.value as DecisionMaker['basis'] })}>
                  {BASIS_OPTIONS.map((opt) => (
                    <option key={opt || 'empty'} value={opt}>{opt || 'Choose…'}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="ptl-decision-block">
              <p className="ptl-sub-legend">Alternate decision-maker</p>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-adm-name">Name</label>
                <input id="ptl-adm-name" className="ptl-input" value={form.alternateDecisionMaker.name} onChange={(e) => updateDecisionMaker('alternateDecisionMaker', { name: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-adm-rel">Relationship</label>
                <input id="ptl-adm-rel" className="ptl-input" value={form.alternateDecisionMaker.relationship} onChange={(e) => updateDecisionMaker('alternateDecisionMaker', { relationship: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-adm-phone">Phone (must be reachable 24 h)</label>
                <input id="ptl-adm-phone" className="ptl-input" autoComplete="tel" value={form.alternateDecisionMaker.phone} onChange={(e) => updateDecisionMaker('alternateDecisionMaker', { phone: e.target.value })} />
              </div>
              <div className="ptl-field">
                <label className="ptl-label" htmlFor="ptl-adm-basis">Basis for authority</label>
                <select id="ptl-adm-basis" className="ptl-select" value={form.alternateDecisionMaker.basis} onChange={(e) => updateDecisionMaker('alternateDecisionMaker', { basis: e.target.value as DecisionMaker['basis'] })}>
                  {BASIS_OPTIONS.map((opt) => (
                    <option key={opt || 'empty'} value={opt}>{opt || 'Choose…'}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        </fieldset>

        <fieldset className="ptl-fieldset">
          <legend className="ptl-legend">Local doctor (optional)</legend>
          <p className="ptl-body-text">A local physician who can be briefed about your care.</p>
          <div className="ptl-field-grid ptl-two-col">
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-lp-name">Name</label>
              <input id="ptl-lp-name" className="ptl-input" value={form.localPhysician.name} onChange={(e) => updateLocalPhysician({ name: e.target.value })} />
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-lp-clinic">Clinic</label>
              <input id="ptl-lp-clinic" className="ptl-input" value={form.localPhysician.clinic} onChange={(e) => updateLocalPhysician({ clinic: e.target.value })} />
            </div>
            <div className="ptl-field">
              <label className="ptl-label" htmlFor="ptl-lp-phone">Phone</label>
              <input id="ptl-lp-phone" className="ptl-input" autoComplete="tel" value={form.localPhysician.phone} onChange={(e) => updateLocalPhysician({ phone: e.target.value })} />
            </div>
          </div>
        </fieldset>

        <div className="ptl-form-actions">
          <button type="submit" className="ptl-btn ptl-btn--primary" disabled={!dirty}>Save</button>
          <button type="button" className="ptl-btn ptl-btn--secondary" onClick={handleDiscard}>Discard changes</button>
        </div>

        {saved && <p className="ptl-status-msg" role="status">Saved. Your care plan has not changed.</p>}

        <p className="ptl-form-note">
          Changing your details does not change your care plan. Phone numbers you add here stay in this browser
          for the demonstration.
        </p>
      </form>

      <div className="ptl-card ptl-readonly-card">
        <h2 className="ptl-h2">Set with your care team</h2>
        <ul className="ptl-locked-list">
          {clinicalItems.map((item) => (
            <li className="ptl-locked-item" key={item.label}>
              <span className="ptl-lock-icon" aria-hidden="true">🔒</span>
              <span><strong>{item.label}:</strong> {item.value}</span>
            </li>
          ))}
        </ul>
        <p className="ptl-body-text">Only your doctor can change these, after talking with you.</p>
        {reviewRequestedOn ? (
          <p className="ptl-body-text">
            Review requested on {reviewRequestedOn}. Your care team will contact you.
          </p>
        ) : (
          <button type="button" className="ptl-btn ptl-btn--secondary" onClick={onRequestReview}>
            Request a review
          </button>
        )}
      </div>
    </section>
  );
}

export function ConsentSignature(props: {
  patientName: string;
  versionLabel: string;
  summaryFields: Array<{ label: string; value: string }>;
  consent: ConsentRecord | null;
  onSign: (input: { typedName: string; declaration: 'self' | 'delegated' }) => void;
}) {
  const { patientName, versionLabel, summaryFields, consent, onSign } = props;
  const [declaration, setDeclaration] = useState<'self' | 'delegated' | ''>('');
  const [readConfirmed, setReadConfirmed] = useState(false);
  const [typedName, setTypedName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const recorded = consent && consent.versionLabel === versionLabel ? consent : null;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (typedName.trim().toLowerCase() !== patientName.trim().toLowerCase()) {
      setError(`Type your name exactly as it appears on your record: ${patientName}.`);
      return;
    }
    if (!declaration || !readConfirmed) {
      setError('Please complete the declaration and confirm you have read the summary.');
      return;
    }
    setError(null);
    onSign({ typedName, declaration });
  }

  return (
    <section className="ptl-page">
      <p className="ptl-eyebrow">Consent</p>
      <h1 className="ptl-heading">Review and sign your care plan</h1>
      <p className="ptl-lede">{versionLabel}</p>

      <div className="ptl-card-grid">
        {summaryFields.map((f) => (
          <div className="ptl-card ptl-def-card" key={f.label}>
            <p className="ptl-overline">{f.label}</p>
            <p className="ptl-def-value">{f.value}</p>
          </div>
        ))}
      </div>

      {recorded ? (
        <div className="ptl-card ptl-consent-confirm">
          <h2 className="ptl-h2">Consent recorded</h2>
          <p className="ptl-body-text">{recorded.typedName}</p>
          <p className="ptl-body-text">
            {recorded.declaration === 'self'
              ? 'I have made these choices myself.'
              : 'I have chosen not to be involved in detail and have asked my decision-maker to decide with the team.'}
          </p>
          <p className="ptl-body-text">Signed on {recorded.signedAt}</p>
          <p className="ptl-body-text">Your plan is locked when your doctor releases it.</p>
        </div>
      ) : (
        <form className="ptl-form" onSubmit={handleSubmit}>
          {error && <p className="ptl-field-error" role="alert">{error}</p>}
          <fieldset className="ptl-fieldset">
            <legend className="ptl-legend">Declaration</legend>
            <div className="ptl-radio-item">
              <input
                type="radio"
                id="ptl-decl-self"
                name="ptl-declaration"
                checked={declaration === 'self'}
                onChange={() => setDeclaration('self')}
              />
              <label htmlFor="ptl-decl-self">I have made these choices myself.</label>
            </div>
            <div className="ptl-radio-item">
              <input
                type="radio"
                id="ptl-decl-delegated"
                name="ptl-declaration"
                checked={declaration === 'delegated'}
                onChange={() => setDeclaration('delegated')}
              />
              <label htmlFor="ptl-decl-delegated">
                I have chosen not to be involved in detail and have asked my decision-maker to decide with the team.
              </label>
            </div>
          </fieldset>

          <div className="ptl-checkbox-item">
            <input
              type="checkbox"
              id="ptl-read-confirm"
              checked={readConfirmed}
              onChange={(e) => setReadConfirmed(e.target.checked)}
            />
            <label htmlFor="ptl-read-confirm">I have read the summary above.</label>
          </div>

          <div className="ptl-field">
            <label className="ptl-label" htmlFor="ptl-typed-name">Type your full name</label>
            <input
              id="ptl-typed-name"
              className="ptl-input"
              value={typedName}
              onChange={(e) => setTypedName(e.target.value)}
            />
          </div>

          <div className="ptl-form-actions">
            <button type="submit" className="ptl-btn ptl-btn--primary">Give consent</button>
          </div>
        </form>
      )}

      <p className="ptl-footer-note">
        This is a simulated typed signature for the demonstration. It is not a legally valid electronic signature.
        The plan becomes official only when your doctor releases it.
      </p>
    </section>
  );
}
