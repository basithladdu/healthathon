# Email to OHC (Open Healthcare Network): final draft

**DRAFT, not sent.** It replaces the short draft in `everyday.md` ("Mail CARE / OHC"). Send only after the team agrees the wording.

- **To:** support@ohc.network. Also post a short version in their Slack (slack.ohc.network).
- **Named contacts (public):** Bodhish Thomas (CEO), Vignesh Hari (CTO). Source: `docs/research/CARE_DEEP_DIVE_2026-10-27.md` §7.
- **Attach, don't link:** our repo is private (HANDOFF item H14), so a "[repo link]" would not open for them. Attach the three questionnaire JSONs from `docs/research/CARE_DEEP_DIVE_2026-10-27.md` §5.2 as files.
- **Keep security out of this email.** The two OHC security issues go by a separate private email (`SECURITY_DISCLOSURE_DRAFTS.md`), to the address in CARE's `SECURITY.md`.

---

**Subject:** Family-side add-on for CARE palliative units: pilot and form review

Hello OHC team,

We are Team Sahara (Health-a-thon 2026). Sahara is an Android app for the families of palliative patients: a symptom diary with a pain body map, a medicine list with a taken/missed log, and a goals-of-care note that a doctor reviews and the family then confirms in its exact wording.

CARE is the staff record, and it has no family app. We want to sit next to CARE, not replace it. We have three requests:

1. **Review our forms.** We wrote our symptom diary, pain areas and goals-of-care questions as CARE Questionnaires (attached: `sahara-symptom-diary`, `sahara-pain-body-map`, `sahara-goals-of-care-conversation`). We checked them against CARE's code, but have not loaded them into a CARE server. Could someone tell us if they would load cleanly?
2. **A small pilot, after the hackathon.** We would like to try a family inbox with one palliative unit that already runs CARE: family entries wait, and a nurse accepts each one before it reaches CARE. From your blog, Pallium India (Karnataka Mission, Bengaluru), Arike (Kochi) and Niram (Tumkur) look like good fits. Would you be open to joining that conversation?
3. **Licences.** `care_scaffold`, the docs, `care_spec` and `care_report_templates` have no licence file. May we reuse them, and under which licence?

Four short questions:
- The docs give a slug limit of 25 characters, but the code seems to allow 50. Which applies? Our goals-of-care slug is 33 characters.
- Is a patient or family app already being built for CARE (for example Arike's planned patient app)? We don't want to duplicate it.
- For a family inbox, should we build a backend plugin, or is there a better route?
- What is the best private address for reporting a security concern in an OHC repo?

Thank you for building CARE in the open.

Team Sahara
[sender name, email]

---

## Checks before sending
- [ ] Team agrees the wording.
- [ ] Questionnaire JSONs copied out of `docs/research/CARE_DEEP_DIVE_2026-10-27.md` §5.2 into files and attached. Decide whether to rename the goals-of-care slug first (`HANDOFF.md` suggests `sahara-goc-conversation`).
- [ ] Sender name and reply address filled in.
- [ ] The slug limits (docs 25, code 50) come from our notes (`HANDOFF.md`; deep dive §5.1). [unverified on a live CARE server]
- [ ] Partner names come from OHC's blog. None of these partners has been contacted yet. Don't say they agreed.
- [ ] Don't say "integrated with CARE", "FHIR-compliant" or "fills CARE's gap" (`docs/research/handoff-2026-10-27/README.md`).
