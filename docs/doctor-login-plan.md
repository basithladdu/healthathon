# Doctor login and verification plan

**DRAFT — for discussion, not implemented.** Prepared 15 October 2026 (Asia/Calcutta).

## Purpose and present boundary

Allow a real clinician to use a verified clinical workspace without confusing ordinary sign-in, professional registration, identity verification and patient access.

Current Android offers self-selected Patient/Family/Doctor presentation. This does not verify a doctor. Backend read/edit access is not a clinical role and must not be treated as permission to publish a verified clinician-reviewed Care Note. This document proposes future work only.

## One account, different contexts

- Each individual has one personal account and their own credentials.
- An account can be a patient, help relatives and request a care-professional workspace.
- Professional verification attaches to the individual account; hospital membership and patient permissions remain separate.
- Becoming a verified clinician must not grant automatic access to any patient.
- Family/helper access must not grant clinical publishing permission.

## Proposed first-time screens

1. **Create account / Sign in:** verify the email or phone channel using the selected authentication method.
2. **Choose workspace:** personal care, or request a clinician workspace. Do not label the user verified at this point.
3. **Professional details:** registered full name, registering council, registration number and registration year; optional NMR ID and hospital affiliation.
4. **Verification consent:** explain purpose, data used, verification provider/reviewer, retention and correction/contact route.
5. **Checks pending:** show progress without unlocking clinical publishing.
6. **Manual review if needed:** allow corrections or hospital review when records are absent, ambiguous or unavailable.
7. **Verified clinician workspace:** show approval basis/status and authorised patients, not a nationwide patient directory.
8. **Patient connection:** accept a scoped patient invitation or approved hospital assignment before viewing the patient's records.

Email/phone OTP proves control of that channel, not professional registration. Matching a public registry entry proves a corresponding record exists, not that the applicant is that person. Identity binding and current registration status need separate checks.

## Verification routes to discuss

### A. Hospital-approved pilot — recommended starting point

- Partner hospital supplies an approved clinician roster and appoints a verification owner.
- Hospital reviewer checks registration evidence, applicant identity and current employment/authorisation.
- Invite the approved individual, or use a properly approved hospital SSO integration if available.
- Store reviewer, evidence/source, decision and date; support correction and revocation.
- An email domain alone is not sufficient: staff, students, contractors or former employees may have addresses.
- Hospital approval does not replace the individual's registration check or patient-specific authorisation.

### B. Commercial registration API — candidate, not selected

Decentro documents an NMC membership-check endpoint. Its API reference requires consent, purpose, member ID, state council and MBBS admission year; this differs from its marketing's single-input description. Our proposed registration-year field must not silently be sent as admission year.

Before adopting any vendor, confirm accepted identifiers, response semantics, lawful/authorised source access, councils covered, update freshness, suspension handling, identity binding, contract/privacy terms and price. Costs, complete coverage, official authorisation and freshness guarantees are not confirmed. No purchase, integration or zero-cost promise is made.

### C. ABDM Healthcare Professionals Registry (HPR) — exploratory

Evaluate only through approved integration access and documented consent/authentication flows. We have not confirmed a turnkey public “Login with HPR” service suitable for this app. Creation or possession of an HPR ID alone must not be treated as proof of an active medical licence. Determine what verified professional fields and status an approved integration can actually return.

## Verification states

| Internal state | Meaning and permitted behaviour |
|---|---|
| `pending_checks` | Submitted; no clinician publishing privileges. |
| `needs_manual_review` | Missing/ambiguous data, identity mismatch or unavailable source; explain next step. |
| `verified` | Identity and professional checks approved under the chosen policy; patient access still separate. |
| `rejected` | Review declined with recorded reason and correction/appeal route; never automatically label someone fake. |
| `suspended` | Clinical privileges disabled pending review or due to confirmed policy/status change. |

An ordinary account may remain usable in personal/family contexts after professional rejection or suspension, subject to account-security policy. Professional status and account-security status are different.

## Permissions and clinician work

- Enforce verified-clinician status and relevant patient/hospital permissions on the server for every clinical publication, not just by hiding buttons.
- A patient editor cannot publish a verified clinician-reviewed note without the separate professional permission.
- A clinician sees only authorised patients: patient list → requests/prior note → capture/draft review → publish/version history.
- Keep the source, AI draft, clinician review, publication and each member's exact-version acknowledgement distinct.
- Verify claimed specialty separately; general registration does not prove oncology/palliative specialisation.
- Record access/publication decisions in an audit trail. Support rechecks, expiry review and immediate privilege revocation.
- Clinical decision-making and representative authority must not be inferred from app access.

## Returning login

Use normal account authentication; decide MFA/session policy before clinical rollout. A daily registry lookup is not required merely to sign in. Recheck professional status under an agreed risk/expiry policy and before sensitive actions when status is stale or suspended. Never continue clinical publishing solely because an old session once had approval.

## Architecture and failure handling

`App → authenticated server → approved registry service or hospital review → verification decision → server permissions`

- Keep provider credentials and privileged decisions off the app; never trust a client-supplied verified flag.
- Use permitted APIs/approved processes, not scraping or CAPTCHA bypass.
- Provider outage or a missing result means pending/manual review, not automatic approval or automatic rejection.
- Public NMR/IMR lookup has nationwide modern-medicine intent, not a verified guarantee that every current doctor will be found.
- Keep non-medical professions such as physiotherapy on an appropriately scoped future verification route, not automatically through NMC.

## Minimal verification data and safeguards

Store only necessary registered name, council, identifier type/number, source/reference, checked-at date, decision/status basis, reviewer/service actor, review/expiry date and relevant hospital membership. Restrict access, protect records and define retention/deletion before collection. Store professional evidence separately from public profile details.

Do not collect NMR/HPR passwords, government-service OTPs or Aadhaar OTPs. Do not default to government-ID uploads. If stronger identity evidence is necessary, select a lawful minimal method and explain its purpose/retention. Never include professional documents, private identifiers or provider secrets in routine logs.

## Phases and acceptance criteria

1. **Agree policy:** choose pilot hospital, verifier, identity method, registration sources, correction process and retention/recheck rules.
2. **Build manual pilot:** request/status screens, hospital review, separate server permissions, patient connection and audits.
3. **Evaluate automation:** sandbox an approved provider using permitted test data; compare coverage/failures and cost before purchase/integration.
4. **Controlled rollout:** test revocation, stale status, access isolation, account recovery and clinician workflow with the hospital.

Acceptance: self-selecting Doctor or editing local state never enables verified publication; reader/editor invitations never bypass clinical checks; knowing another doctor's registration number never suffices to claim their identity; provider failure cannot auto-approve; a verified doctor cannot open unrelated patients; suspension blocks new clinical publication; reviewed decisions are auditable and correctable.

## Open discussion decisions

Hospital partner and verification owner; identity binding method; API access/price/coverage/freshness; NMR/SMC identifier policy; specialty evidence; recheck/expiry interval; MFA; manual review turnaround; data retention; patient-to-doctor connection; and how independent clinicians join without a hospital partner. Do not make a legal claim here about mandatory NMR enrolment.

## Sources and evidence limits

- [NMR FAQ](https://nmr.nmc.org.in/faq): NMR ID, registration workflow and modern-medicine scope; specialty information described as in process.
- [NMR public search](https://nmr.nmc.org.in/search-doctor): NMR/SMC identifiers and registration status search; not applicant authentication.
- [NMC Indian Medical Register](https://nmc.org.in/information-desk/indian-medical-register/): public registration search; data is being updated.
- [NMC doctor-registration form](https://application.nmc.org.in/doctor/register): distinguishes NMR, IMR and SMC identifiers.
- [Decentro NMC API reference](https://docs.decentro.tech/reference/kyc-and-onboarding-api-reference-identities-verification-services-professional-verification-nmc): concrete vendor request fields, not proof of nationwide completeness or government endorsement.
- [Government-hosted HPR creation manual](https://rajswasthya.rajasthan.gov.in/admin/upload/news_img/2025/08/HPR_Creation.pdf): account/ID creation guidance, not proof that Sahara has an approved HPR login or licence-verification integration.

No signup, provider request, payment, production change or medical-licence determination was performed for this plan.
