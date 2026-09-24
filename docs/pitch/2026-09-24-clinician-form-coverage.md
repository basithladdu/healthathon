# Clinician form coverage

The optional structured clinical form records conversation details in the existing Care Note fields. Each group can be opened independently. New entries use the same versioned note content as the clinician’s draft, so they appear in the patient reader, note history and PDF and are included in the doctor-signed version snapshot.

## Recorded sections

- Patient and care team: patient name, age or date of birth and sex, address and phone, UHID or medical record number, diagnosis/stage/functional status, treating doctor and contact, palliative care contact, and other/local care contacts.
- Family and contacts: attendees and relationships, preferred language, primary and alternate decision-makers with contacts and stated basis, people to inform, and contact details carried forward from earlier drafts.
- Understanding and existing directives: recorded capacity status and assessment details, reported directive or prior wishes, what the patient understands or wishes to know, family understanding, and earlier clinical context retained from previous drafts.
- Patient values: distressing symptom, what matters, fears, unfinished matters, unacceptable outcomes, and other values or needs discussed.
- Declarations: the patient’s stated role or words about delegating, the surrogate’s stated relationship and account of the patient’s wishes, other declarations made during the conversation, and optional recorded witness details. Recording those details does not execute a witness signature.
- Care choices discussed: blank free-text records for hospital transfer, CPR, breathing support, ICU or other organ support, and feeding/fluids. The group is labelled as the clinician’s record of the conversation; it supplies no choices or defaults.
- Decisions and follow-up: clinical decisions discussed and participants, decisions not discussed, unresolved questions or differences, and next steps with owner/review timing.
- Signatures: the clinician signs the reviewed Care Note version first. The patient can then review and type their name against that exact latest doctor-signed version. Older versions remain readable and cannot receive a new signature. A family acknowledgement is separate, uses the family role/name, and is available only after patient review.

## Boundaries and remaining work

- Typed names are browser-local acknowledgements. They do not verify identity, provide a cryptographic signature, or establish legal execution.
- Existing directives and prior wishes are recorded as reported; the app does not authenticate or interpret them. Witness, notary and advance-medical-directive execution workflows are not implemented.
- The app does not deliver these notes across phones or to a shared clinical record. A shared backend, authenticated identity and controlled retrieval remain separate work.
- The choices section only records what the clinician says was discussed. It does not recommend or preselect treatment, apply clinical logic, set prognoses or scores, or record drug doses.
