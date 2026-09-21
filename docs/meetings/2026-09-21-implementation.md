# Meeting implementation record

Prepared 21 September 2026 from the full 1,209-line user-supplied meeting transcript. The transcript is preserved separately in `source-materials/meetings/2026-09-21-transcript.txt`. Meeting minutes are a decision record, not a claim that every discussed service is connected.

## Implemented in this change

- Combined Patient & family sign-in; doctor entry asks for medical registration number and state medical council. These inputs are format checks, not government identity verification.
- Home groups: My documents, Find support, My space; direct care note and care summary links. Each feature has its own route. Old next-doctor and removed section URLs remain usable redirects within the app.
- Editable, persistent patient profile with optional ABHA, birth date, address, language and oncologist contact. Find care can reuse the address when the user chooses it.
- Doctor explicitly selects Discussion or Decisions recorded, reviews and signs. Patient and family can then review and record their own typed signatures against that exact version. Changed text cannot reuse an earlier acknowledgement.
- New discussions can bring forward priorities, questions and follow-up from the previous signed note, with source and version labels. Current participants and agreement are not copied. Clinician review prompts use the supplied Healthathon form.
- PDFs carry the note version and recorded signatures. They do not claim a verified digital signature or advance medical directive.
- Reports are grouped into cancer documents, monitoring tests and other documents. Typed PDFs can be read up to 12 pages, reviewed, corrected and saved for source-linked text search. Scanned pages are identified as missing text.
- Test trends use a date-by-test table, preserve units and printed ranges, link to reports, and show recorded or planned treatment events without interpreting them.
- Calendar adds chemotherapy and radiotherapy. It stops offering new manual daily medication entries; existing entries remain available.
- Each selected symptom has its own 0-10 rating. Older descriptive ratings are preserved.
- Home help focuses on transport, home nursing and medicine/equipment delivery. Community resources use the person's chosen condition. Journal content is not used to choose groups.
- Fundraising, daily family tasks and duplicate discussion links are removed from the main navigation; saved records are not deleted.

## Still requires implementation or a service connection

- Real ABHA authentication, council registration verification, server-side accounts, access control and cross-device storage. Entering an identifier does not prove identity.
- Connected AI generation, speaker diarisation, document question answering beyond source text matching, and photo/scanned-report OCR. No diagnosis or treatment recommendation is generated.
- Full structured clinical form coverage, proxy/witness declarations, verified digital signatures and clinician validation. The current five-section note provides review prompts, not a legal directive workflow.
- Secure QR/link sharing, expiry/revocation and single-use access. A downloaded PDF cannot be remotely revoked.
- Automatic reviewed prescription-to-calendar import and automatic report-value extraction. Handwritten prescriptions are not interpreted.
- Additional verified ambulance/imaging service data, native Android/iOS distribution, and the revised competition deck.

## Verification

- Application TypeScript source check passed using a temporary configuration that excludes stale generated Next.js validators.
- No local production build or test suite was run, as requested. Local development compilation stopped before browser review; Vercel compilation and live browser review are recorded at delivery.
- Existing unrelated documents and submission changes were left untouched.
