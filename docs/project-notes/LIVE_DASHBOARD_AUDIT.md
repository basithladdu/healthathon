# Health-a-thon live dashboard audit

## Observation

- Date: 24 August 2026
- Method: read-only inspection of the user's existing authenticated Chrome tab
- URL: `https://healthathon.reskilll.com/dashboard`
- Account type shown: Technologist registration
- Track: **Cancer Care · NCG**
- Solution type: **Clinician focused**
- Use case: **Patient Follow-up & Continuity of Care**
- Entry path: **Path B · Needs a doctor**

## Editability

The dashboard exposed a team-management link and sign-out control, but no visible control to edit the track, solution type or use case. One bounded read-only check of the public `/register` route showed account creation, not a registration-edit workflow.

Conclusion: there is no verified self-service way to change the selected use case. This does not prove that the selection is permanently immutable; the organizers may be able to change it. Treat the current selection as fixed unless they confirm otherwise.

## Schedule discrepancy

The dashboard displayed:

- Doctor and healthcare-professional registration: through 25 September 2026.
- Technology-team registration: through 25 September 2026.
- Matchmaking and team formation: through 25 September 2026.
- Round 1 idea submission: 12-25 September 2026.

The saved public-site capture contains earlier closing dates for the first three stages. The live authenticated dashboard is evidence of current account-visible state, but the organizers should confirm the authoritative schedule.

## Safety and privacy boundary

No forms were submitted, no registration values were changed, no message was sent, and no account or team code is reproduced in this audit.
