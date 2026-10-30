# Sahara: ECTPR and AMD references

Checks the sources cited in SVK's two specs (`source-materials/team-context/svk-specs-2026-10-29/`) against primary or best-available sources, and records what each one changes in the build. Rule IDs: **L1–L10** (legal, tech spec §2), **V1–V9** (validation, tech spec §5), **C1–C8** (proposed consistency rules, clinical spec §4).

- Researched: 29 Oct 2026. Every URL below was accessed on **2026-10-29** unless the entry says otherwise.
- Confidence: **High** means checked against the primary text (judgment, gazette, standard, or the local Vidhi file). **Medium** means a reputable secondary source, or a primary source read only in part. **Low** means a single news report or an inference.
- This is not legal advice. The Indian position is still changing. Re-check items marked Medium or Low before the pilot.

---

## 1. Common Cause v. Union of India (2018) and the order of 24 Jan 2023

**Sources**
- 2018 judgment: Common Cause (A Regd. Society) v. Union of India, (2018) 5 SCC 1, 9 Mar 2018, Constitution Bench. The guidelines are in paras 198–200.
- 2023 order: MA No. 1699 of 2019 in WP (C) No. 215 of 2005, 24 Jan 2023, Constitution Bench led by K.M. Joseph J. Reported as [2023] 1 SCR 1137, 2023 SCC OnLine SC 99, and (2023) 14 SCC 131. The modified text is set out as a table in para 6.
  - Full text: https://indiankanoon.org/doc/55919876/ (High)
  - SCC Times table of old and new text (this is the "SCC Times" source in the spec): https://www.scconline.com/blog/post/2023/02/03/detailed-order-supreme-court-modifies-the-guidelines-given-in-2018-euthanasia-judgment-legal-research-legal-news-updates/ (Medium. Its summary of 199.2 mentions the Collector. The order text does not, so the order text wins.)
  - Official report (SCR): https://digiscr.sci.gov.in/admin/judgement_file/judgement_pdf/2023/volume%201/Part%20I/2023_1_1137-1168_1702877638.pdf (did not resolve from here)

**What the modified guidelines require (High, from the order text)**

| Para | Requirement | Build impact |
|---|---|---|
| 198.1 | Only "an adult who is of a sound and healthy state of mind", able to communicate and to understand the document's purpose and consequences. | **L1** is confirmed: block an AMD when DOB is under 18, and require the capacity declaration. |
| 198.2.5 | Names "guardian(s) or close relative(s)", who consent on the executor's behalf if the executor becomes incapable. | **L2**: at least one representative is the legal minimum. The Vidhi template advises at least two, so keep "warn below 2". Store the preference order. |
| 198.3.1 | Signed by the executor "in the presence of two attesting witnesses, preferably independent, and attested before a notary or Gazetted Officer". The JMFC countersignature is removed. | **L3** is confirmed. The attestor type is NOTARY or GAZETTED_OFFICER. Never offer "JMFC" as an attestor. |
| 198.3.2 | The witnesses and the notary or Gazetted Officer record their satisfaction that it was executed voluntarily, "without any coercion or inducement or compulsion", and with full understanding. | The PDF needs a satisfaction line for each witness and a notary block. The Vidhi template already has both (§9 below). |
| 198.3.3, 198.3.4, 198.3.7 | Deleted: copies to the JMFC and District Court, and the separate family-physician copy step. | Remove any "send to JMFC or court" item from the L4 checklist. |
| 198.3.5 | The executor informs the named person(s) and the family physician (if any) and gives each a copy. | **L4** checklist: each DHR, the family physician, and the custodian. |
| 198.3.6 | A copy goes to the competent officer of the local Government, Municipal Corporation, Municipality or Panchayat. They "shall nominate a competent official… who shall be the custodian". The executor "may also choose to incorporate" the AD in digital health records, if any. | **L4**: custodian copy plus an optional digital health record or ABHA link. The order puts the duty to nominate a custodian on local bodies, so "custodian pending" is a real state of affairs. |
| 198.4.1 | Only where the executor is terminally ill, on prolonged treatment with no hope of recovery, and lacks capacity: the treating physician checks genuineness "with reference to the existing digital health records… or from the custodian". | **L5** is confirmed: show the scan, the SHA-256 hash and the custodian acknowledgement. |
| 198.4.3 | The physician informs the person(s) named in the AD about the illness, the alternatives, and the consequences of each. | Display text only. |
| 198.4.4 | The hospital constitutes a **Primary Medical Board**: the treating physician plus at least two subject experts of the relevant specialty with at least five years' experience. It gives an opinion "preferably within 48 hours of the case being referred". | **L8** is confirmed. Show this as information only. |
| 198.4.5 | If the Primary Board certifies, the hospital "shall then immediately constitute a **Secondary Medical Board**": one RMP nominated by the district **CMO** plus at least two subject experts with at least five years' experience, none of whom sat on the Primary Board. Opinion "preferably within 48 hours". | **L8** is confirmed. |
| 198.4.6 | Consent comes from the person(s) named in the AD, after the executor's own wishes are checked if the executor can communicate. | Matches the authority_basis value NAMED_IN_AMD. |
| 198.4.7 | The hospital conveys both Boards' decisions and the nominees' consent to the jurisdictional **JMFC** before giving effect to withdrawal. | **L8**: the JMFC receives an intimation. It is not an approval. |
| 198.5.x | If the Boards refuse, the named persons, family, treating doctor or hospital staff may file a writ under Art. 226. The Chief Justice forms a Division Bench, which may appoint a committee of three doctors with 20 years' experience. | Information only. |
| 198.6.1 | The AD can be revoked or changed at any time while the executor has capacity, in writing, by the same procedure. | **L6** is confirmed. The Vidhi FAQ adds that where several valid ADs exist, the most recently executed one applies. |
| 198.6.2 / 198.6.3 | The AD does not apply to circumstances the executor did not anticipate. Where it is ambiguous, the no-AD procedure applies. | **L7** is confirmed. Never auto-apply an AD. |
| 199.1–199.2 (no AD) | The Primary Board consults the family physician and the next of kin, next friend or guardian, and certifies "preferably within 48 hours". The Secondary Board is formed the same way. The hospital gives "intimation… to the JMFC and the next of kin/next friend/guardian… preferably within 48 hours". 199.3 (JMFC visit and endorsement) is deleted. | Supports the authority_basis value NEXT_OF_KIN. The modified 199.2 does **not** involve the Collector. |
| 200 | After withdrawal the Magistrate informs the High Court. Records are kept digitally, and hard copies are destroyed three years after death. | Information only. |

---

## 2. Harish Rana v. Union of India (11 Mar 2026)

**Source**
- MA No. 2238 of 2025 in SLP (C) No. 18225 of 2024, **2026 INSC 222**, J.B. Pardiwala J. writing, with a concurrence by K.V. Viswanathan J. Full text: https://indiankanoon.org/doc/125527568/ (High for the paragraphs cited below. I read paras 289–329 and the early paras. The middle was not read in full.)
- The spec's summary source (Drishti IAS): https://www.drishtiias.com/current-affairs-news-analysis-editorials/news-analysis/13-03-2026/print/manual (not needed, since the primary text was read)

**Holdings and directions**

| Point | What the judgment says | Build impact | Confidence |
|---|---|---|---|
| Clinically assisted nutrition and hydration (CANH) | CANH is **medical treatment**, not basic care (paras 301, 304). Giving it at home does not change that (paras 303–304). Boards may decide to continue, withhold or withdraw CANH under Common Cause (para 305). | **L9** is confirmed: tube feeding is a treatment choice. Comfort feeding by mouth stays outside it. | High |
| Planned palliative withdrawal | Implementation must follow "a structured palliative and end-of-life care plan", not abandonment (para 319). The Court disapproves of "discharge against medical advice" used in its place (para 320). Hospitals should admit home-care patients for reassessment (para 321). AIIMS palliative care was directed to admit the applicant (directions c–d). | Clinical spec §7 is confirmed. Add the "never DAMA" wording to the board-pathway info screen. | High |
| JMFC intimation | Direction (e): High Courts must direct JMFCs to **receive hospital intimations** where both Boards decide to withdraw or withhold treatment. | **L8** is confirmed. | High |
| District CMO panels | Direction (f): CMOs keep a panel of RMPs for Secondary Boards, reviewed at intervals "not exceeding twelve months". Para 322(iv): the CMO nominates "preferably within 48 hours of a hospital's request". Compliance was due August 2026. | Clinical spec §7 is confirmed. | High |
| **30-day reconsideration period** (not in the specs) | Para 322(vi): once both Boards concur, implementation waits **30 days**, during which an aggrieved person with locus may go to court. The period was waived in this case. | **New.** Add it to the L8 pathway display: PMB, then SMB, then JMFC intimation, then 30 days, then a planned palliative withdrawal. The app still never triggers withdrawal. | High (para 322 was read) |
| Written consent of next of kin | Para 322(ii): the next of kin's written consent is integral and should reflect the patient's own wishes as far as possible. | Supports V5 (the substituted-judgement declaration). | High |
| Art. 226 if the hospital won't start the process | Para 322(v). | Information only. | High |
| Financial coercion | Para 289 warns that decisions may reflect "financial distress, lack of insurance coverage, or socio-economic vulnerability" and "economic exhaustion". | Supports the [Sign-off] financial-concern field in clinical spec §8. | High |
| Legislation | The Union is urged to legislate (paras 291, 323). Common Cause remains the law until Parliament acts (para 30). | Information only. | High |
| Withhold vs withdraw | The judgment uses "withdraw and/or withhold" together throughout. It does not separate them. | See the contradiction in §12. | High |

---

## 3. Which states and UTs have notified living-will custodians

Under the 2023 order, the duty to nominate a custodian sits with **local bodies**. State orders tell them to do it. Coverage is uneven and changing.

| State | Status as of 29 Oct 2026 | Source | Confidence |
|---|---|---|---|
| **Andhra Pradesh** (tech spec L4 open question) | The AP Health Department issued **passive-euthanasia and living-will guidelines on 8 Jul 2026**, approved by Health Minister Satya Kumar Yadav. Per news reports: two witnesses, attestation by a notary or gazetted officer, a guardian must be named, Primary Board decides within 48 h, the Secondary Board includes the DMHO, the hospital tells the JMFC before withdrawal, records are kept three years. DMEs and the Director of Secondary Health supervise, and DMHOs are responsible at district level. **No report I found names a living-will custodian, and I did not find the G.O. number or text.** | https://www.thenewsminute.com/andhra-pradesh/explained-andhra-govts-passive-euthanasia-guidelines-for-terminally-ill-patients · https://www.etvbharat.com/te/state/ap-government-approves-guidelines-for-passive-euthanasia-for-terminally-ill-patients-aps26070806109 · https://www.andhrajyothy.com/2026/andhra-pradesh/guntur/ap-government-approves-guidelines-for-passive-euthanasia-in-line-with-supreme-court-directions-vk-1539955.html | Medium (that the guidelines exist). Custodian status: **still open**. |
| **Karnataka** | Health Department order of 30 Jan 2025: DHOs nominate the RMP for Secondary Boards, and AMD copies go to the local-government competent officer. An RDPR order of 14 Feb 2025 reportedly names the **Taluk Panchayat Executive Officer** as competent officer for rural areas. The Karnataka High Court closed a PIL on 24 Apr 2025 after the State's compliance memo. Urban custodians were not confirmed. | https://thesouthfirst.com/karnataka/karnataka-issues-order-upholding-the-right-to-die-with-dignity/ · https://www.livelaw.in/high-court/karnataka-high-court/karnataka-high-court-pil-supreme-court-judgment-living-wills-karnataka-government-compliance-290228 · https://nivarana.org/article/the-right-to-die-with-dignity-the-what-and-the-how-of-the-karnataka-orders | Medium |
| **Telangana** | **No state order or custodian notification found.** The Telangana High Court issued Circular No. 1/2024 (ROC 303/SO/2023, 14 Feb 2024) on the SC safeguards. Sparsh Hospice runs a private "Living Will Clinic" in Hyderabad, which is not a custodian. | https://new.tshc.gov.in/documents/admin_2024_08_07T12_16_39.pdf · https://telanganatoday.com/sparsh-hospice-launches-south-indias-first-living-will-clinic | Medium (only absence of evidence) |
| **Kerala** | **No statewide custodian order found.** The Kerala High Court ordered Kochi Corporation to name a custodian for one petitioner (Dec 2024) and in Jan 2025 asked the State for a timeframe for all LSGIs. In Oct 2025, Onmanorama reported that local bodies had still received no directions. | https://www.livelaw.in/high-court/kerala-high-court/kerala-high-court-doctor-living-will-custodian-doctor-kochi-corporation-277126 · https://livelaw.in/high-court/kerala-high-court/kerala-high-court-living-will-custodian-competent-person-local-body-280519 · https://www.onmanorama.com/lifestyle/health/2025/10/09/what-is-a-living-will-kerala-roadblocks.html | Medium |
| **Maharashtra** | **Notified.** The Urban Development Department published a list of custodians across local bodies, as at 5 Mar 2024 (Vidhi FAQ). In PIL 3 of 2024 (Datar v. State) the State told the Bombay HC it had appointed **417 custodians**: 29 in municipal corporations and 388 in councils and nagar panchayats. BMC named medical health officers in its 24 wards. | Local `Vidhi-EOLC-Toolkit-FAQ.pdf` (Q.32) · https://www.legal500.com/intelligence/india/family-and-matrimonial/mhco-update-custodians-appointed-for-living-wills · https://www.freepressjournal.in/mumbai/mumbai-bmc-appoints-custodians-for-safekeeping-living-wills-following-sc-directives-on-right-to-die-with-dignity | High (Vidhi FAQ) / Medium |
| **Goa** | Operational since 2024. The first AMD was registered by Justice M.S. Sonak in May 2024 (IMA Goa with the Goa SLSA). Listed by Vidhi as a state that has released implementing orders. The exact custodian designation was not confirmed. | https://www.tribuneindia.com/news/india/high-court-judge-first-in-goa-to-execute-living-will-626601 · https://vidhilegalpolicy.in/wp-content/uploads/2025/10/State-of-Implementation_CC.pdf | Medium |
| Tamil Nadu (extra) | TN Advance Medical Directive Guidelines 2026, G.O. dated 31 Jul 2026. The custodian is the **Joint Director of Health Services**, a health official rather than a local body. | https://www.dtnext.in/news/tamilnadu/tamil-nadu-notifies-rules-for-advance-medical-directives-with-or-without-amd | Low–Medium |
| Haryana, Punjab (extra) | Vidhi (Oct 2025) lists Maharashtra, Goa, Karnataka and Haryana as having released implementing orders. South First adds Punjab. | Vidhi tracker above | Low–Medium |

**Build impact (L4, L5):** keep "custodian pending" as a first-class status. For AP, Telangana and Kerala, default to pending and show the Vidhi-recommended step: a letter to the head of the local body (Commissioner, Sarpanch or President) citing para 198.3.6 of the 24 Jan 2023 order. Make custodian type a free-text field with suggestions, because it varies by state: local-body official, Taluk Panchayat EO, MHO, or JDHS in Tamil Nadu.

---

## 4. DPDP Act 2023 and DPDP Rules 2025 (L10, tech spec §7)

| Item | Finding | Source | Confidence |
|---|---|---|---|
| Rules notified | Published 13 Nov 2025 (G.S.R. notifications dated 13 Nov; some outlets say 14 Nov). | https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014 · https://www.azbpartners.com/bank/indias-digital-personal-data-protection-act-phased-rollout-and-key-compliance-milestones/ | High |
| Phase 1 (immediate) | Act ss. 1(2), 2, 18–26, 35, 38–43, 44(1),(3), and Rules 1, 2, 17–21: definitions and the Data Protection Board. | AZB | High |
| Phase 2 (+12 months) | s. 6(9), s. 27(1)(d), Rule 4: Consent Manager registration, from 13 Nov 2026 (AZB says 12 Nov). | AZB | High |
| Phase 3 (+18 months) | All substantive duties: ss. 3–5, 7–17, 28–34, 36–37, 44(2), and Rules 3, 5–16, 22, 23. **From 13 May 2027** (AZB says 12 May). The spec's "duties from May 2027" is **correct**. | AZB | High |
| Proposed acceleration | MeitY consulted in Jan–Apr 2026 on cutting the window to 12 months (to 13 Nov 2026), possibly only for Significant Data Fiduciaries. **I found no notified amendment.** | https://ssrana.in/articles/meity-plans-to-cut-short-dpdp-compliance-timeline-and-notify-cross-border-restrictions-for-sdfs/ | Medium. Re-check before the pilot. |
| Notice (s. 5, Rule 3) | A separate notice that stands on its own, in clear and plain language, listing the personal data and the specific purpose, how to withdraw consent, how to exercise rights, and how to complain to the Board. s. 5(3) lets the Data Principal read it in English or any **Eighth Schedule language**. | PIB note: https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf | High (PIB) / Medium (rule detail) |
| Consent (s. 6) | Free, specific, informed, unconditional and unambiguous. Must be as easy to withdraw as to give. | Act text (from memory, not re-fetched) | Medium |
| Legitimate use: medical emergency (s. 7(f)) | Processing without consent is allowed "for responding to a medical emergency involving a threat to the life or immediate threat to the health" of the Data Principal or another person. **This is the lawful basis for ED break-glass.** It does not cover routine sharing. | Act text (from memory, not re-fetched) | Medium |
| Security (Rule 6) | Reasonable safeguards: encryption, access control, monitoring, and **keeping logs for one year** for breach detection. | https://www.dpdpa.com/dpdparules/rule6.html (secondary) | Medium |
| Breach (Rule 7) | Tell affected individuals without delay. Tell the Board without delay, then send a detailed report **within 72 hours**. | Secondary summaries, PIB | Medium |
| Rights (ss. 11–14, Rule 14) | Access, correction, update and erasure. Requests answered **within 90 days**. The **right to nominate** someone to act on death or incapacity (s. 14) fits DHRs. | PIB note | High |
| Children (s. 9, Rule 10) | Verifiable parental consent. Healthcare is among the exempted purposes. Children are out of scope for the AMD anyway (L1). | PIB note | High |
| Penalties | Up to ₹250 crore for security failures, ₹200 crore for breach-notice and children failures, ₹50 crore otherwise. | PIB note | High |
| Health data | The Act has **no separate "sensitive" or health-data category**. Health data is ordinary personal data. | Act structure | High |

**Build impact:** the spec's §7 items line up with Rule 3 (notice in the user's language), s. 6 (separate consent for each copy recipient), and ss. 11–14 (access, correction and withdrawal requests). Add: (a) cite s. 7(f) as the stated basis for break-glass access; (b) keep the append-only audit log at least one year (Rule 6); (c) a 72-hour breach runbook; (d) a "nominee for data rights" field, which can default to the primary DHR.

---

## 5. Clinical and guidance sources (Saanthvana Appendix F)

| Source | Verified citation and URL | What it changes | Confidence |
|---|---|---|---|
| **ISCCM–IAPC position statement 2024** | Mani RK, Bhatnagar S, Butola S, Gursahani R, Mehta D, Simha S, … Myatra SN. *Indian J Crit Care Med* 2024;28(3):200–250. doi:10.5005/jp-journals-10071-24661. PMID 38477011. https://impressions.manipal.edu/open-access-archive/6786 | Citation is correct. Revises the 2014 ISCCM–IAPC statement in light of the 2018 and 2023 orders. Supports V4 (prognostic understanding before goals) and the ceiling-of-care model. I did not read the full text, so the clinical spec's paraphrase of its content is unverified. | High (citation) / Low (content) |
| **MoHFW/DGHS draft guidelines for withdrawal of life support** | Released **20 Sep 2024**, comments due 20 Oct 2024. https://main.mohfw.gov.in/sites/default/files/Guidelines%20for%20withdrawal%20of%20Life%20Support.pdf (did not resolve from here) · https://theprint.in/health/what-centres-new-draft-norms-on-passive-euthanasia-say-why-doctors-are-divided-over-the-move/2291283 | Reported as four conditions (brainstem death; advanced condition unlikely to benefit; documented informed refusal after prognostic awareness; compliance with the SC procedure). The IMA objected. **Still a draft, and I found no final version.** Treat it as guidance, not law. | Medium (existence) / Low (exact wording) |
| **NCG End-of-Life Care algorithm** | National Cancer Grid, Palliative Care Guidelines: "Approach to managing End of Life Care". Drafts were posted for comment in 2021, and the EOL guideline is dated May 2021 per search snippets. https://tmc.gov.in/ncg-old/images/Approach%20to%20managing%20End%20of%20Life%20Care.pdf (connection reset) · https://palliumindia.org/2021/03/pc-ncg-guidelines | **I could not open the PDF.** The dying-phase signs, the 500–1000 mL/24 h fluid limit, the four realms and the daily routine-care panel cited in the clinical spec §3/§6 and in Saanthvana §12 are **unverified**. | Low |
| **National POLST form** | National POLST Form, 2025 edition (file dated Jan 2026). https://polst.org/wp-content/uploads/2026/01/National-POLST-Form.pdf | Confirms **V1**: "Any incomplete section of POLST creates no presumption about patient's preferences for treatment. Provide standard of care." On **V2**, POLST is stricter: YES CPR *requires* Full Treatments. Sahara's V2 allows Attempt CPR with Goal C or a lower ceiling if a reason is given. That is a deliberate softening, which is fine but should be recorded. The nutrition options match (food by mouth, tube, time-limited trial, no tube, not discussed = standard of care). | High |
| **ReSPECT v3** | Resuscitation Council UK, launched 15 Sep 2020. https://www.resus.org.uk/about-us/news-and-events/resuscitation-council-uk-introduces-version-3-respect-form | v3 added a recommendation that *balances* extending life with comfort (supports goal B), a visual "what matters to me" scale, a reason prompt when the patient is not involved, signature by patient or proxy, and a statement that it is **not legally binding**. Supports the ECTPR-is-not-an-AMD framing (tech spec §1). | High |
| **ATS time-limited-trial workshop report** | Kruser JM et al. *Ann Am Thorac Soc* 2024;21(2):187–199. doi:10.1513/AnnalsATS.202310-925ST. PMID 38063572. https://pmc.ncbi.nlm.nih.gov/articles/PMC10848901 | 16 essential elements in four phases (consider, plan, support, reassess). Supports **C4** (block saving a trial until duration, review time and reviewers are entered). | High |
| TLT duration (Crit Care Explor 2024) | Moy et al. *Crit Care Explor* 2024;6(9):e1148. Median TLT duration 3.0 days (IQR 3.0–4.5). https://pmc.ncbi.nlm.nih.gov/articles/PMC11407817 | Supports the 3-day default trial prompt (clinical spec §9 sign-off). It is a single-centre US study. | High |

---

## 6. FHIR R4 Consent (scope "adr") and ABDM/ABHA

| Item | Finding | Source | Confidence |
|---|---|---|---|
| Consent.scope | Code system `http://terminology.hl7.org/CodeSystem/consentscope`. Code **`adr`**, display "Advanced Care Directive", meaning "Actions to be taken if they are no longer able to make decisions for themselves". | https://hl7.org/fhir/R4/valueset-consent-scope.html | High |
| Consent resource | **Maturity 2, Trial Use.** R4 models only the privacy use case, but adr is in scope. `status` 1..1 (draft, proposed, active, rejected, inactive, entered-in-error), `scope` 1..1, `category` 1..*, `patient` is required when scope = adr, and either policy or policyRule must be present. Use `sourceAttachment` for the signed scan or `sourceReference` to a DocumentReference. `provision` holds nested permit/deny rules. Signatures go in Provenance. | https://hl7.org/fhir/R4/consent.html | High |
| Mapping | AMD becomes Consent (scope adr, status active only once Executed, sourceAttachment = scan plus SHA-256). Revoked or Superseded becomes inactive. The ECTPR is better modelled as a CarePlan or a ServiceRequest/Goal plus DocumentReference, not as Consent, since it is a clinician plan. | Inference | Medium |
| ABDM FHIR IG | NRCeS "FHIR Implementation Guide for ABDM" (R4.0.1). Versions 6.5.0 and 7.0.0 (draft, Jul 2026) are visible. Its Composition-based artefacts include **HealthDocumentRecord**, which carries unstructured or scanned documents such as an AMD scan, plus DocumentReference and Patient. **There is no ABDM Consent or advance-directive profile.** ABDM's consent is the Consent Manager artefact, used for data-sharing permission, not FHIR Consent(adr). | https://nrces.in/ndhm/fhir/r4/index.html · https://nrces.in/ndhm/fhir/r4/StructureDefinition-HealthDocumentRecord.html | Medium (TLS errors blocked a full read) |

**Build impact (stretch, L4/L5):** publish the AMD scan to the patient's ABHA as a HealthDocumentRecord bundle wrapping a DocumentReference (PDF plus hash). Export Consent(adr) as a separate Sahara-native resource. Do not claim ABDM conformance for Consent(adr).

---

## 7. Vidhi Centre model AMD: section structure (for the AMD PDF)

Source: local file `Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf` (8 pages, PDF dated Aug 2025). Drafted by Shreyashi Ray, Rituparna Padhy, Dhvani Mehta and Hardik Dua (Vidhi), with input from Dr Smriti Khanna (P.D. Hinduja). Toolkit page: https://vidhilegalpolicy.in/eolctoolkit (High)

1. **Title:** "Advance Medical Directive".
2. **Section 1, Personal details:** full name, gender, DOB (DD/MM/YYYY), Government ID (document name and ID number), permanent address, current address, Municipality or Panchayat. Then the "final expression of my wishes" statement, an optional free-text box for reasons, beliefs and values, and a request that all concerned treat the directive as the primary basis for decisions.
3. **Section 2, Directions on life-sustaining treatment:** the trigger is no reasonable probability of recovery from a terminal condition, end-stage condition or vegetative state, where treatment would only prolong dying. Footnotes define end-stage condition and vegetative state. The general direction is to withhold or withdraw and allow natural death. Optional **I DO NOT WANT** list: (1) CPR, (2) dialysis, (3) ventilation or artificial life support, (4) chemotherapy, (5) radiotherapy, (6) surgery unless for symptom control, (7) IV fluids and medicines including antibiotics, except comfort ones, (8) artificial nutrition and hydration if it only prolongs dying (comfort feeding excluded). Optional **I WANT** list: (1) CPR, (2) IV fluids and medicines including antibiotics and comfort drugs. Comfort care always continues, including for organ or body donation.
4. **Section 3 (optional), wishes during end-of-life care:** place of care (hospital, hospice, home, or a named institution), preferred physician, special focus such as mental distress, name and gender identity, partner terminology.
5. **Section 4, Authorisation of DHRs:** number of DHRs in words and figures, in order of preference. "You should authorise at least two persons", with an optional joint-decision clause. The powers are to obtain medical information and to consent, refuse or withdraw consent, and their decisions are to be respected over contrary views. The **primary DHR table** has: full name, relationship, DOB, Government ID (name and number), mobile numbers (primary and other), email, permanent address, address on the date of signing. **Alternate DHR 1** and **optional alternate DHR 2** use the same table.
6. **Section 5 (optional), directions upon death:** organ donation (THOTA 1994, Form 7), body donation, last rites.
7. **Declaration:** a clause for an executor who cannot read or write ("read out and explained"), capacity and competence (with an optional RMP capacity certificate, said to be attached but missing from the local file), careful thought, voluntariness. "Executed in accordance with the order dated January 24, 2023 … MA No. 1699/2019 in WP (C) No. 215/2005". Copies forwarded to the DHRs, the family physician (optional), and the custodian of the local government.
8. **Validity:** lifelong unless modified or revoked. Revised versions are shared with the same recipients.
9. **Signature of the executor:** full signature *or thumb impression*, name, date, place.
10. **Signatures of witnesses 1 and 2:** each with a satisfaction statement, name, address, Government ID, email, mobile, signature, date and place.
11. **Notarisation:** signed in the presence of the undersigned, with a satisfaction statement. "SIGNED BEFORE ME", notary name, "APPROPRIATE AUTHORITY, STAMP", date, place. The SC also allows a Gazetted Officer, so label this block "Notary / Gazetted Officer".

**Build impact:** the AMD PDF should follow this order and these headings exactly. Store enum choices per item in Section 2, so the WANT and DO-NOT-WANT lists print in any language. **Leave Government ID blank for handwriting** (the spec stores only the type and last four digits, and that must never be printed as the full ID). V8 page footers apply. Add the thumb-impression path (V6). Warn if a witness is also a DHR or a likely beneficiary (L3).

---

## 8. Spec rule mapping summary

| Rule | Status after research |
|---|---|
| L1 | Confirmed (198.1). |
| L2 | Confirmed: at least one is legal, two is the Vidhi recommendation. |
| L3 | Confirmed. JMFC countersignature removed. Witnesses "preferably independent". The Vidhi FAQ says witnesses must be 18 or older. Add a witness age check. |
| L4 | Confirmed. Remove any JMFC or court copy item. The family-physician copy is "if any". |
| L5 | Confirmed (198.4.1). |
| L6 | Confirmed (198.6.1). The latest executed version applies. |
| L7 | Confirmed (198.6.2–3). |
| L8 | Confirmed, and **extended**: add the Harish Rana 30-day reconsideration period, CMO panel nomination within 48 h, and the ban on DAMA. Also see the withholding issue in §12. |
| L9 | Confirmed (Harish Rana paras 301–305). |
| L10 | Confirmed: substantive duties start 13 May 2027 unless MeitY accelerates them. |
| V1 | Confirmed by POLST wording. |
| V2 | Softer than POLST (POLST requires Full Treatment with CPR). Saanthvana's reason-required version is a deliberate choice. Keep it and note it. |
| V3–V9 | Internal Saanthvana design. No external law conflicts. V4 is consistent with the MoHFW draft's "informed refusal following prognostic awareness" (Low confidence on wording). |
| C4 | Supported by the ATS 2024 report. |
| C6 | Supported by Harish Rana (removing CANH is withdrawal and goes through the boards). |

---

## 9. Open questions answered / still open

| Question (spec) | Answer |
|---|---|
| Has Andhra Pradesh named living-will custodians? (tech spec L4, clinical sign-off) | **Still open, but closer.** AP issued state guidelines on 8 Jul 2026 (two witnesses, notary or gazetted officer, boards, DMHO on the Secondary Board, JMFC intimation). No source I could read names a custodian. Get the G.O. from the AP Health, Medical & Family Welfare Department (goir.ap.gov.in) or the district DMHO. Until then, use "custodian pending". |
| Karnataka, Telangana, Kerala, Maharashtra, Goa custodians? | Maharashtra: yes (417 custodians, UDD list). Karnataka: orders from Jan–Feb 2025, rural custodian is the TP EO, urban not confirmed. Goa: operational, designation not confirmed. Kerala: no statewide order found (HC proceedings). Telangana: nothing found. |
| DPDP commencement "May 2027" | **Answered: correct** (13 May 2027). A proposed acceleration to Nov 2026 was not notified as of the research date. |
| Did Harish Rana add procedure? | **Yes:** a 30-day reconsideration window, CMO panels reviewed at least every 12 months, CMO nomination within 48 h, written next-of-kin consent, and Art. 226 if a hospital won't start the process. |
| Is the MoHFW 2024 draft final? | **Not final**, as far as I found. |
| Will the hospital accept in-app clinician signatures? | Still open. No law found either way. For the AMD, the SC requires a physical signed and attested document (the Vidhi FAQ, Q.68: a digital AMD may be stored, but the AMD itself must be physical). The ECTPR is a clinical record, so this is hospital policy and IT Act e-signature rules. |
| Default review interval? | Still open. No Indian source sets one. |
| Can a palliative consultant sign as treating consultant? | Still open. No legal rule found. 198.4.4 only needs "the treating physician". |

---

## 10. Spec claims we could not verify

1. **NCG End-of-Life Care algorithm content** (dying-phase signs, ≤ 500–1000 mL NS per 24 h matched to urine output, four realms, daily routine-care panel, SPIKES and Ask-Tell-Ask, predictsurvival.com). The NCG PDF could not be opened.
2. **ISCCM–IAPC 2024 content** as paraphrased in Saanthvana Appendix C. The citation is verified. The content was not read.
3. **MoHFW draft 2024 exact conditions and definitions** ("terminal illness … death is inevitable in the foreseeable future", the four conditions). The official PDF host did not resolve. Only news summaries were read.
4. **Andhra Pradesh custodian designation** and the AP G.O. number and text.
5. **Karnataka urban custodian** and Goa's custodian designation.
6. Detailed DPDP Rule text (Rule 6 log retention, Rule 7 72-hour report). These are from secondary summaries. The gazette text was not read.
7. **ABDM IG full profile list.** NRCeS TLS errors blocked a full read.
8. Saanthvana's statement that the Secondary Board must be "of similar standing". The order says "at least two subject experts… with at least five years' experience". "Of similar standing" is not order language. This is minor.

---

## 11. Local sources used

- `source-materials/team-context/svk-specs-2026-10-29/Saanthvana_Clinical_Specification.txt` and `Advance_Medical_Directive_Platform_Tech_Spec.txt`
- `source-materials/user-provided/Saanthvana v 1.0.docx` (Appendix C legal position, Appendix F sources)
- `Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf` (structure in §7)
- `Vidhi-EOLC-Toolkit-FAQ.pdf` (Vidhi, first published June 2024, CC BY-SA 4.0): custodian letter (Q.32), Maharashtra list as at 5 Mar 2024, witnesses 18 or older and preferably independent, digital copies, DNAR orders.

---

## 12. Contradictions with the specs (needs a decision)

1. **Withholding vs withdrawing (L8, clinical spec §7, Saanthvana C.1).** The specs say the board pathway is for *withdrawing* running treatment, and that withholding "is directed by the plan itself". The **Vidhi FAQ** (local, Q.2 and the DNAR section) says the SC guidelines "apply to both the withholding and withdrawal of life-sustaining treatment, and do not make any distinction". It also says DNAR orders for patients **without capacity** need the named person's or next of kin's consent, **both Boards, and JMFC intimation**. Harish Rana's directions also say "withdraw or withhold" together. A patient **with capacity** can still refuse treatment directly. **Impact:** the ECTPR's DNACPR and ceilings are firm where the patient had capacity and signed. Where a surrogate decides for a patient without capacity, the app should not describe withholding as outside the board process. Change the §7 and C.1 wording ("smaller step") to avoid implying no boards are needed. Ask the legal reviewer. Confidence that the Vidhi reading is right: Medium. Practice varies, and ISCCM may read it differently.
2. **Harish Rana's 30-day reconsideration period** is missing from L8 and from clinical spec §7.
3. **V2 vs POLST:** the spec cites POLST, but POLST makes Full Treatment mandatory with CPR. Sahara allows a recorded reason instead. This is a deliberate divergence. Document it rather than cite POLST as the source.
4. **The SCC Times summary of para 199.2** mentions informing the Collector. The order text does not. Follow the order: no Collector.
5. **Custodian type is not always a local body.** Tamil Nadu (2026) uses the Joint Director of Health Services. Make custodian type free-form.
6. **Government ID:** the Vidhi AMD asks for full Government ID numbers for the executor, DHRs and witnesses. The spec stores only the type and last four digits. Generate those fields blank for handwriting so both hold.
