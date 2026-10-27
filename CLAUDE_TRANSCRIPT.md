# Claude transcript: CARE conversation (27 Oct 2026)

These are the owner's messages and Claude's replies about CARE (Open Healthcare Network), kept as close to the original wording as possible. The research behind them is in [`docs/research/CARE_BRIEF_2026-10-27.md`](docs/research/CARE_BRIEF_2026-10-27.md).

---

## Owner: "do whatever and append to handoff and tell awaiz to do rest but wait lets understand the care thing"

**Claude:**

### What CARE is
CARE is free, open-source software that hospitals and palliative-care programmes run for their **staff**. It is made by the **Open Healthcare Network Foundation** in Kochi, Kerala. Kerala's palliative programme uses it and claims more than 330,000 patients on it; nobody has checked that figure.

Nurses, doctors and field workers use it to:
- keep the list of palliative patients;
- plan and record home visits;
- assign care teams;
- write care plans;
- refer patients to a doctor or hospital;
- record consents, including DNR and advance directives.

Think of it as the hospital's back-office system for palliative care.

### How it relates to Sahara
Sahara is the app in the **patient's and family's** hand; CARE is the system on the **staff's** screen.

Most of what they share is staff-side work: patient records, visits, medicines, care team and consents. CARE does these at real scale, so don't pitch those parts as unique.

Where Sahara is different:
- **The goals-of-care conversation:** recording with consent, answers drawn from it, doctor review of the note, then the patient or family confirming that exact note.
- **The family-side tools:** the symptom diary, the 3D pain body and the daily check-in.

### What the organisers asked
CARE is **optional**.
- Their 15 Oct email offers the "opportunity" to build on CARE and "encourages" teams to explore it.
- The final submission template never mentions CARE.
- Its only integration question is slide 11: "Will the solution need to be integrated with other systems? If yes, outline."

### Options

| | What | Time | View |
|---|---|---|---|
| A | Slide 11: say Sahara is the patient/family side and can feed a hospital's CARE | 1–2 h | Do now |
| B | Export the confirmed note as a FHIR-style file, not connected live | 1–2 days | Only if time after the demo video |
| C | A small add-on inside CARE (a family-confirmation tab) | 1–2 weeks | Too risky before 7 Nov; pitch as the next pilot |
| D | Rebuild Sahara on CARE | weeks | No |

**Recommendation:** A now, B only if there is time (moderate confidence).
- **Weak spot:** we don't know whether judges reward actually building on CARE. No known judging criterion mentions it.
- **Honesty note for the pitch:** AI drafting is off, goals-of-care answers come from word matching, and doctor approval is done by hand in the database.

---

## Owner: "CARE's own documentation says it has no screen for a family or patient to confirm a consent. WHERE"

**Claude:** It is on CARE's own documentation site, in two places (checked live on 27 Oct, docs version 3.1; the 23 Oct archive has the same wording).

**1. "Verify a consent":** https://docs.ohc.network/flows/clinical/consent/verify-a-consent/
> "A verification is a second confirmation of a consent that a member of staff recorded for a patient. Note: The current version of Care has no screen for consent verification. You cannot add, view, or remove a verification from the web application. … Review this flow again after the product adds the screen."

Under step 2 it says: "The consent record has no verification section, so you cannot add a verification here." It then lists the fields the system would store:
- **Verification Type:** "Family for a family member or a representative, or Validation for the patient";
- **Verified by:** "the name of the user who confirms";
- **Verification date.**

**2. The consent concepts page:** https://docs.ohc.network/concepts/clinical/consent/
> "Note: The current version of Care has no screen to add, show, or remove a verification. This is a known gap in the product."

**Correction (Claude had overstated it):** the gap is narrower.
- **Who records it:** a CARE staff user enters the verification. Only the Admin, Doctor, Nurse and Facility Admin roles have that permission. The family or patient doesn't confirm anything themselves.
- **What it covers:** a consent record (for example DNR or an advance directive), not a goals-of-care conversation note.

The honest pitch line:
> "CARE's documentation lists family/patient verification of a consent as a known gap with no screen. Sahara lets the patient or family confirm the exact note themselves, on their own phone."

Do **not** say "Sahara fills CARE's gap" as if they were the same feature.

Archived copies:
- `output/research/care-website-20261023/text/docs.ohc.network__flows__clinical__consent__verify-a-consent.txt`, line 8;
- `output/research/care-website-20261023/text/docs.ohc.network__concepts__clinical__consent.txt`, line 71.

---

## Owner: "if CARE is staff side for palliative then can we do family side exactly how they did it so that we can fit in the puzzle and make it whole with our additional features"

**Claude:** Yes, that is the strongest way to position Sahara. "Exactly how they did it" should mean using **CARE's data model and words**, not copying its screens.

Sahara should keep its own phone app. It should store and export its records in the shape CARE uses, so a hospital running CARE can take them in without anyone retyping them:
- patient;
- encounter or visit;
- consent, with a verification of type Family or Validation, who verified it, and the date;
- care plan;
- questionnaire responses for symptoms and check-ins.

**Strongest counterargument:** this only "fits the puzzle" on paper until it works against a real CARE server.
- CARE is a staff system, and its patient-facing login is limited.
- A family app writing into a hospital's CARE needs that hospital's deployment, sign-in and permission.
- Nothing connects today.
- With 11 days to 7 Nov, a live connection is not realistic. A matching data model plus an export file is.

**Status:** an agent is checking this against CARE's actual source code. It is looking at:
- what patient or family sign-in CARE has;
- which APIs a family app could call;
- the exact consent-verification fields;
- how care plans and questionnaires are stored.

It will write the field-by-field mapping in `docs/research/CARE_FAMILY_SIDE_FIT.md` before anything is built.
