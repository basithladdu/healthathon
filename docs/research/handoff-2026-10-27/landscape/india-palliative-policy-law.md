# India palliative policy, law and regulation: what it means for Sahara

_Research agent report, 27 Oct 2026. Unedited except for this header._

## India government policy and access for Sahara: policy, law and regulatory landscape (researched 27 Oct 2026)

**Method.** I fetched and read the primary or near-primary text for most claims below; the rest come only from search-result summaries and are marked [snippet]. Sites I could not reach: nha.gov.in (HBP package PDFs), cbcindia.gov.in and mohfw.gov.in all failed the TLS check, and moneylife.in blocked me. So the PM-JAY package-level claims are weaker than the rest. The shared web-search budget ran out near the end. There are no follow-up searches on eSanjeevani, a current national morphine figure, or the Tamil Nadu and Punjab policy texts. The repo already covers ABDM M1–M3 basics, "don't claim ABDM", and "explain the advance directive, never create one". Everything below adds to that.

---

### (1) Ten key findings for Sahara

1. **Morphine cannot be prescribed through teleconsultation.** The Telemedicine Practice Guidelines 2020 say NDPS drugs "cannot be prescribed via telemedicine" ([PSA Legal FAQ](https://psalegal.com/telemedicine-guidelines-2020-faq/)). A refill has to happen in person at a Recognised Medical Institution (RMI).
   - Sahara should never imply an online morphine refill.
   - A real feature it could build: a refill planner. It would show "doses left / days until the RMI visit", based on the prescription's "total quantity of the END, daily dose and the duration" ([NCG RMI guidelines](https://palliumindia.org/wp-content/uploads/2023/09/RMI-Guidelines-by-NCG-1.pdf)).
   - Health and Wellness Centre (HWC) guidelines allow "Oral Morphine can be prescribed up to 4 weeks" at a PHC ([NHSRC HWC guidelines](https://nhsrcindia.org/sites/default/files/2021-06/Operational%20Guidelines%20for%20Palliative%20Care%20at%20HWC.pdf)).
2. **A family member can consult a doctor for the patient, but needs proof.** The 2020 guidelines say, for an incapacitated patient, "The caregiver needs to have formal authorization or a verified document that establishes his relationship with the patient". A caregiver "includes a family member" ([PSA](https://psalegal.com/telemedicine-guidelines-2020-faq/)).
   - Sahara's family-invite and roles feature could produce a caregiver-authorisation page for the doctor's records. This is a gap nobody fills.
   - The same guidelines say AI "cannot counsel or prescribe".
3. **CDSCO's final guidance on medical device software (MDSW) took effect 21 July 2026** (CDSCO/MD/GD/MDSW/01/2026).
   - Out of scope: software "limited to data transfer, storage, archival, formatting, communication", and general wellness ([NatLawReview](https://natlawreview.com/article/code-compliance-decoding-indias-medical-device-software-guidance)).
   - The trap: the exclusion "is function-specific rather than product-specific". And software "intended for non-clinical users in a 'serious situation…' without support from specialised professionals, may be considered as used in a 'critical situation'" ([MedDeviceGuide, citing Table 2](https://meddeviceguide.com/blog/india-cdsco-medical-device-software-mdsw-guidance-2026-guide)).
   - Families are non-clinical users. If Sahara auto-generates "do X now" alerts from pain scores, that could move it to Class B or C. Keep it as a record plus doctor-set thresholds.
4. **DPDP (data protection) dates.** The Rules were notified 13 Nov 2025 (some sources say 14 Nov). Consent-manager registration starts 13 Nov 2026. The substantive duties (notice and consent, data rights, children's data, breach rules) start **13 May 2027** ([AZB](https://www.azbpartners.com/bank/update-indias-digital-personal-data-protection-framework-comes-into-effect/)).
   - In January 2026 MeitY proposed cutting the gap to 12 months, which would bring it to 13 Nov 2026. I found no confirmation that this was gazetted [snippet: [S.S. Rana](https://ssrana.in/articles/meity-plans-to-cut-short-dpdp-compliance-timeline-and-notify-cross-border-restrictions-for-sdfs/), [ConsentOS](https://consentos.in/learn/dpdp-rules-notification-status/)].
   - Plan to be compliant before any pilot anyway.
5. **DPDP Section 14 (right to nominate) fits Sahara directly.** A person can nominate someone who "shall, in the event of death or incapacity… exercise the rights" ([s.14 text](https://www.dpdpa.com/dpdpa2023/chapter-3/section14.html), unofficial mirror).
   - A palliative app should ask the patient, while they still have capacity, to name a data nominee.
   - Section 7(f) allows processing without consent only for a "medical emergency involving a threat to the life" ([s.7](https://www.dpdpa.com/dpdpa2023/chapter-2/section7.html)). That does not cover routine family entries.
6. **Advance directives exist in law but are hard to lodge in practice.**
   - In 2023 the Supreme Court dropped the magistrate countersignature. Lodging now goes through a custodian appointed by local government.
   - Maharashtra named 417 custodians in March 2024 ([LawBeat](https://lawbeat.in/news-updates/custodians-living-will-appointed-local-bodies-state-government-tells-bombay-high-court)).
   - The Kerala High Court had to order Kochi to name one in December 2024 ([Bar & Bench](https://www.barandbench.com/news/kerala-high-court-kochi-municipal-corporation-custodian-living-wills)).
   - In October 2025 Kerala departments "have not yet received official directions" ([Onmanorama](https://www.onmanorama.com/lifestyle/health/2025/10/09/what-is-a-living-will-kerala-roadblocks.html)).
   - Opportunity for Sahara: a city-by-city "where to lodge it" guide, while still not creating the document itself.
7. **Harish Rana v. Union of India (11 March 2026)** is the first individual case the Supreme Court decided under this framework.
   - Tube feeding counts as medical treatment and can be withdrawn.
   - Without a living will, the family can decide together with two medical boards. The patient was sent to AIIMS palliative care ([Wikipedia summary](https://en.wikipedia.org/wiki/Harish_Rana_v._Union_of_India)).
   - This makes Sahara's goals-of-care note more relevant, and the family's confirmed version may be evidence of the patient's wishes. It is still not a legal instrument.
8. **The national programme is big on paper and thin in delivery.** Six hundred districts were covered by October 2025. In 2025-26 (to October), 12.6 lakh patients used OPD, more than 19 lakh were visited at home, and 3.3 lakh were admitted ([Medical Dialogues/Nadda](https://medicaldialogues.in/news/health/600-districts-covered-under-palliative-care-till-october-union-health-minister-159971)).
   - Kerala made about 650,000 home visits in 8 months. UP made about 42,500, and Bihar ran services in only 6 districts ([IndiaSpend/Scroll](https://scroll.in/article/1091264/indias-elder-care-was-supposed-to-reach-homes-but-households-and-families-are-taking-on-the-work)).
   - In the Supreme Court case WP 138/2024, the Union's affidavit answered "No information provided" on most 2017 programme duties ([CLPR chart](https://clpr.org.in/wp-content/uploads/2024/04/Chart.petitioners.pdf)).
9. **Morphine access is still the bottleneck.**
   - Only "5.8% provide uninterrupted access to oral morphine" (223 centres, 2019) ([ecancer](https://ecancer.org/en/journal/article/1486-situational-analysis-of-the-quality-of-palliative-care-services-across-india-a-cross-sectional-survey)).
   - "In one national and seventeen state EMLs [essential medicines lists], oral morphine was absent" ([ecancer EML study](https://ecancer.org/en/journal/article/1837-an-evaluation-of-the-adequacy-of-indian-national-and-state-essential-medicines-lists-emls-for-palliative-care-medical-needs-a-comparative-analysis)).
   - For Sahara: add "not available / out of stock" as a reason for a missed dose. The data then has clinical and advocacy value.
10. **PM-JAY gives little to families at home.** It covers hospital stays. A palliative medicine professor said palliative care "is poorly defined and inadequately packaged… symptom control, counselling, advance care planning and caregiver support, are rarely reimbursed" ([Scroll](https://scroll.in/article/1091264/indias-elder-care-was-supposed-to-reach-homes-but-households-and-families-are-taking-on-the-work)).
    - The heavily used "unlisted palliative package" is **palliative chemotherapy**, not palliative care. It was misused: only 33% of audited claims matched the guidelines [snippet: [BMC HSR 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11654404/)].
    - Sahara has no PM-JAY revenue route.

---

### (2) Detailed tables

**A. Policy and law: what each means for Sahara**

**NPPC (2012; operational guidelines 2017)**
- Current state:
  - Funded through each state's NHM implementation plan.
  - 609 districts approved and 493 functional by December 2023, per the Union's affidavit ([CLPR](https://clpr.org.in/wp-content/uploads/2024/04/Chart.petitioners.pdf)).
  - 600 districts by October 2025 ([MD](https://medicaldialogues.in/news/health/600-districts-covered-under-palliative-care-till-october-union-health-minister-159971)).
  - The 2017 guidelines say "Family members/caregivers must also be involved" and "Empower community and family participation".
- What it means for Sahara:
  - The policy explicitly wants caregiver training and empowerment, but the Union gave "No information" on doing it. That is the gap Sahara fills.
  - It also makes an NHM or state pilot pitch credible.

**HWC palliative guidelines (NHSRC)**
- Current state:
  - ASHAs identify bedridden patients.
  - Screening tool: "Cut off of 4 or more… referral".
  - The CHO "shall refer patients with high pain score (pain score of 6 and above) to the PHC-HWC".
  - "Separate case sheet and patient card shall be maintained".
  - District doctors advise PHC doctors through teleconsultation ([NHSRC](https://nhsrcindia.org/sites/default/files/2021-06/Operational%20Guidelines%20for%20Palliative%20Care%20at%20HWC.pdf)).
- What it means for Sahara:
  - Sahara's 0–10 pain diary can mirror the government's pain ≥6 referral rule, set by a clinician and not decided by Sahara.
  - It can position itself as the family's digital copy of the patient card.
  - The screening fields (ECOG, symptoms, family distress) map onto Sahara's intake.

**NP-NCD 2023–30**
- Current state: critics say palliative care was narrowed to cancer and home-based care was dropped [snippet: [IAS Parliament](https://www.iasparliament.com/current-affairs/the-need-to-focus-on-palliative-care)].
- What it means for Sahara: don't make Sahara cancer-only. Families of non-cancer patients (heart failure, COPD, dementia, kidney failure) are the least served.

**SC WP(C) 138/2024 (Dr Rajshree Nagaraju)**
- Current state:
  - Admitted 7 March 2024.
  - On 13 October 2025 the Court gave the Union three weeks to collect state data. Next listing was 25 November 2025 ([CLPR](https://clpr.org.in/litigation/dr-rajshree-nagaraju-v-union-of-india-and-ors/)).
  - Counsel told the court "one to two per cent of patients get palliative care" [snippet: [Verdictum](https://www.verdictum.in/court-updates/supreme-court/dr-rajshree-nagaraju-v-union-of-india-palliative-care-article-21-1524747)].
  - I found no outcome after November 2025.
- What it means for Sahara:
  - A court-driven push for the states to report data and act. Watch it.
  - State data duties could create demand for simple family-reported data.

**NDPS Amendment 2014 / Rules 2015**
- Current state:
  - Created "Essential Narcotic Drugs" and central rules ([Pallium 2014](https://palliumindia.org/2014/03/done-the-process-is-complete)).
  - The State Drugs Controller is the only authority that grants RMI status. Excise licensing "no longer valid". Government hospitals are "deemed RMIs" ([Pallium UP 2023](https://palliumindia.org/?p=26088)).
  - The controller must decide on a Form 3G application within 60 days.
  - ENDs are dispensed "only to selected patients, registered with the RMI". Each patient's record (Form 3E) is kept for 2 years.
  - Home-care staff may carry ENDs for registered patients ([NCG](https://palliumindia.org/wp-content/uploads/2023/09/RMI-Guidelines-by-NCG-1.pdf)).
  - Of 6 ENDs, "only Morphine, methadone and Fentanyl are available in India".
- What it means for Sahara:
  - Medicine records need fields for "registered RMI" and "prescription end date / quantity".
  - Show "Your RMI: name, phone" on the medicines screen.
  - Never facilitate transfer of opioids between families.
  - Unused morphine should be returned to the RMI ("unused ENDs returned by the patients shall be considered as receipts"). Sahara could show a bereavement "return medicines" step.

**RMI training rules (state-by-state)**
- Current state: in 2021 Kerala's drugs controller accepted online training for RMI status; elsewhere "left to the discretion" of each state controller ([Pallium 2021](https://palliumindia.org/2021/04/rmi-online-pain-training)).
- What it means for Sahara: morphine access varies by state. A directory of the nearest RMIs is a real need, but data is scarce. Don't claim coverage.

**Telemedicine Practice Guidelines 2020**
- Current state:
  - Lists O, A and B. NDPS and Schedule X drugs are banned.
  - Explicit consent is needed when a caregiver or health worker starts the consultation.
  - Doctors must keep chat and video logs.
  - Platforms must show each doctor's registration number.
  - AI "cannot counsel or prescribe" ([PSA](https://psalegal.com/telemedicine-guidelines-2020-faq/)).
- What it means for Sahara:
  - If doctors message families through Sahara, Sahara becomes a telemedicine platform. It must check doctors' registration, show their registration numbers and keep logs.
  - The family-confirmed note flow is compatible, but the doctor's review is a record they must keep.

**CDSCO MDSW guidance (final, 21 July 2026)**
- Current state:
  - Class A–D matrix: "Inform clinical management" in a non-serious condition is Class A, in a serious condition Class A, in a critical condition Class B. "Drive" in a serious condition is Class B.
  - "EHR remain non-device health IT unless they execute medical algorithms" ([MedDeviceGuide](https://meddeviceguide.com/blog/india-cdsco-medical-device-software-mdsw-guidance-2026-guide)).
  - Class A and B are licensed by the State Licensing Authority; C and D by the Central authority ([Nishith Desai](https://www.nishithdesai.com/research-and-articles/hotline/pharma-healthcare-update/the-next-frontier-in-medtech-regulation-dissecting-cdscos-draft-guidance-on-medical-device-software-15465)).
- What it means for Sahara:
  - Diary, medicine log, documents and family sharing: non-device.
  - Any automatic triage ("call ambulance", "increase dose"), AI symptom interpretation, or pain-trend risk score: likely regulated software, possibly Class B/C because users are lay people in a serious condition.
  - Keep thresholds clinician-set and wording neutral ("you set: tell the team if pain ≥6").

**DPDP Act 2023 + Rules 2025**
- Current state:
  - s.9 requires "verifiable consent of… the lawful guardian" for a person with disability who has a lawful guardian ([s.9](https://www.dpdpa.com/dpdpa2023/chapter-2/section9.html)).
  - Breach: tell the Board and users "without delay", with a detailed report within 72 hours. Report to CERT-In within 6 hours.
  - Keep logs for at least 1 year.
  - Clinics and doctors are exempt from parental consent for children's healthcare data ([AZB](https://www.azbpartners.com/bank/update-indias-digital-personal-data-protection-framework-comes-into-effect/)).
- What it means for Sahara:
  - Sahara is the data fiduciary. Symptoms that family members enter are the *patient's* personal data, so consent must come from the patient or a lawful guardian.
  - Gap: many dying patients lack capacity but have no court-appointed guardian. Design consent capture while the patient still has capacity, plus a s.14 nominee.
  - The children's exemption probably does not cover a consumer app (paediatric palliative care).
  - Penalties go up to Rs 250 crore under the Act's Schedule (from my knowledge of the Act, not re-fetched).

**ABDM (national digital health mission)**
- Current state:
  - May 2026: "over 100 crore health records are now linked to more than 90 crore ABHA accounts".
  - **Aarogya Setu 2.0** launched 29 June 2026 as a national PHR (personal health record) app, with "AI-driven health insights by combining uploaded records" ([PIB July 2026](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/jul/doc202676912801.pdf)).
  - An ABDM nominee to manage consent was "being developed" in 2021, and I found no confirmation it was built [snippet: [Medianama](https://www.medianama.com/2021/12/223-national-health-authority-health-data-nominee/)].
- What it means for Sahara:
  - The government's own PHR is now a free alternative for storing documents. Sahara must win on caregiving, not storage.
  - A family-delegate or nominee model is still a gap in ABDM. Sahara can fill it outside ABDM.

**PM-JAY / Vay Vandana (70+)**
- Current state:
  - Rs 5 lakh per family per year for hospital care [snippet: [Outlook Money](https://www.outlookmoney.com/retirement/ayushman-bharat-pm-jay-coverage-hospital-admissions-and-number-of-beneficiaries-explained)].
  - Vay Vandana covers everyone 70+ from 29 October 2024 [snippet: [Business Today](https://www.businesstoday.in/amp/personal-finance/insurance/story/ab-pmjay-2024-what-is-ayushman-vaya-vandana-card-know-how-to-register-for-additional-health-cover-for-seniors-452061-2024-10-30)].
  - I found no dedicated home or outpatient palliative package (not verified; the NHA PDFs were unreachable).
- What it means for Sahara:
  - An info card ("Is your father 70+? He has a Vay Vandana card for hospital stays") is useful.
  - Don't claim PM-JAY covers home palliative care or pays for Sahara.

**Advance directives: Common Cause 2018 → 24 January 2023 changes**
- Current state:
  - The magistrate requirement was removed ([SCC Online](https://www.scconline.com/blog/post/2023/01/25/supreme-court-simplifies-procedure-to-withhold-life-support-of-a-terminally-ill-patient-modifies-guidelines-given-in-2018-euthanasia-judgment-legal-research-legal-news-updates/)).
  - Signed before two witnesses and attested by a notary or gazetted officer. A copy goes to a local-government custodian and may sit in the hospital's paper or digital records.
  - Primary board within 48 hours; secondary board of 3 doctors with 5 or more years' experience, one nominated by the district officer ([Onmanorama](https://www.onmanorama.com/lifestyle/health/2025/10/09/what-is-a-living-will-kerala-roadblocks.html); [TNM on the Karnataka order](https://www.thenewsminute.com/karnataka/karnataka-approves-passive-euthanasia-what-the-sc-guidelines-say)).
  - Karnataka's order says AMDs "can also be maintained in the paper/digital health records… maintained by the medical establishment", and lets the patient nominate two people.
  - I did not read the 2023 order itself. Treat the board details as moderate confidence.
- What it means for Sahara:
  - "Maintained by the medical establishment" means a family app is not the official store. Sahara may keep a *copy* and show where the original is lodged.
  - Sahara should keep its goals-of-care note as a clinical note and add a separate "Have you made a living will? Where is it lodged?" record.
  - A useful idea: the Datar PIL in Bombay HC suggested DigiLocker for storing living wills ([LawBeat](https://lawbeat.in/news-updates/custodians-living-will-appointed-local-bodies-state-government-tells-bombay-high-court)).

**Harish Rana (2026 SCC OnLine SC 358)**
- Current state: tube feeding is medical treatment; family and boards can decide together; withdrawal is linked to palliative care.
- What it means for Sahara:
  - Families will increasingly face decisions about tube feeding. Sahara's conversation guide should cover feeding tubes, not just ventilators.
  - Don't advise on withdrawal.

**B. State policies**

**Kerala**
- Instruments:
  - Policy in 2008, revised 2019 ([Pallium resources](https://palliumindia.org/resources)).
  - November 2024: all palliative systems unified under the local-government department (LSGD), with registration of providers and volunteers ([ETV/PTI](https://www.etvbharat.com/amp/en/!state/kerala-govt-to-unify-all-palliative-care-systems-in-the-state-enn24112907065)).
- Signal:
  - July 2026 funding crisis in Kannur: home visits stopped, nurses unpaid for 6 months ([Onmanorama](https://www.onmanorama.com/news/kerala/2026/07/30/palliative-care-fund-crisis-kannur.html)).
  - Morphine use 1.32 mg per person per year (2012–15) from 167 providers ([PMC](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5735973/)).
- For Sahara: the strongest pilot state, but every pilot needs local-government buy-in. Kerala's volunteer registry could be an integration point. Budgets are tight, so a free or cheap model is needed.

**Maharashtra**
- Instruments:
  - Policy 2012, government resolution (GR) 2013, operational guidelines 2022.
  - **February 2026 GR on universal palliative care**: door-to-door survey, 4–6 palliative beds per district hospital, "morphine available at primary health centres" ([Indian Practitioner](https://theindianpractitioner.com/maharashtra-to-implement-statewide-palliative-care-programme/)).
- Signal: 417 living-will custodians.
- For Sahara: the newest, most expansive programme. The survey will identify patients, and families will then need a follow-through tool. Good timing.

**Karnataka**
- Instruments: palliative care policy 2016 (the copy found is a "DRAFT FOR APPROVAL", [PDF](https://palliumindia.org/wp-content/uploads/2020/05/Karnataka-Palliative-Care-Policy-2016.pdf)); 30 January 2025 order on dignified death and AMDs.
- Signal: the 2025 order requires board decisions to go to the magistrate (JMFC), as reported by TNM.
- For Sahara: the most specific AMD procedure of any state. Use it for Karnataka-specific guidance text.

**Tamil Nadu**
- Instruments: 2019 policy G.O. (listed by Pallium, text not read); Makkalai Thedi Maruthuvam (MTM) home care includes palliative care [snippet].
- Signal: MTM gave 1.9 lakh palliative services by February 2022 [snippet, unverified].
- For Sahara: an existing state home-visit programme means family-side follow-up could fit alongside MTM.

**Goa**
- Instruments: State Palliative Care Policy 2023 (listed on Pallium resources); claims first operational living-will facility [snippet: Tribune].
- For Sahara: a small state, suitable for a living-will pathway demo.

**Punjab**
- Instruments: no state palliative policy found.
- Signal: Elderline answered fewer than 3,000 of about 18,000 calls ([Scroll](https://scroll.in/article/1091264/indias-elder-care-was-supposed-to-reach-homes-but-households-and-families-are-taking-on-the-work)).
- For Sahara: an unserved state, but no policy hook.

**MP, Gujarat, Rajasthan**
- Signal: lead in functional palliative centres (51, 41 and 42) ([MD](https://medicaldialogues.in/news/health/600-districts-covered-under-palliative-care-till-october-union-health-minister-159971)).
- For Sahara: possible Hindi-belt pilots, given Sahara's Hindi UI.

**Odisha**
- Instruments: district-level palliative care programme guidelines only.

**C. Morphine availability data**

| Metric | Value | Source |
|---|---|---|
| National morphine out of the government factory (GOAW) | 329 kg (2015); pre-1985 level was 411 kg | [Pallium](https://palliumindia.org/?p=4802) |
| Kerala use per person | 1.32 mg (2012–15); 19-fold difference between districts in 2015 | [PMC5735973](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5735973/) |
| Centres with uninterrupted oral morphine | 5.8% of 223 (2019) | [ecancer](https://ecancer.org/en/journal/article/1486-situational-analysis-of-the-quality-of-palliative-care-services-across-india-a-cross-sectional-survey) |
| Oral morphine absent from essential medicines lists | 1 national and 17 state lists | [ecancer EML](https://ecancer.org/en/journal/article/1837-an-evaluation-of-the-adequacy-of-indian-national-and-state-essential-medicines-lists-emls-for-palliative-care-medical-needs-a-comparative-analysis) |
| India's share of global palliative need | about 1 crore of 6 crore people (Lancet Commission 2017) | [Pallium](https://palliumindia.org/2017/10/a-sixth-of-worlds-sick-needing-palliative-are-in-india-study-finds) |
| Need met by morphine supply | about 4% [snippet, unverified] | [HPA briefing](https://www.healthpovertyaction.org/wp-content/uploads/2019/12/India-opioid-crisis-briefing.pdf) |

No national RMI count and no national figure after 2016 were found.

---

### (3) Gaps nobody fills that Sahara could

- **Caregiver authorisation pack.** The telemedicine guidelines require a "verified document" of the relationship. No tool produces one. Sahara could generate a patient-signed delegation page plus a DPDP s.14 nominee designation.
- **Morphine continuity.** A refill countdown tied to the prescription's quantity, daily dose and duration. Show the RMI's contact. Log "not available" as a reason for missed doses. Add a bereavement step to return unused morphine. No state or NGO app does this as far as I found (moderate confidence).
- **Living-will "where to lodge it" guide.** Custodians differ by city (Maharashtra local bodies, Kochi Corporation, Karnataka). Kerala counters exist at Parippally and Alappuzha. No one keeps a directory.
- **Family-side twin of the government patient card.** The HWC guidelines say "patient card shall be maintained". ASHAs and CHOs do home visits. Families have nothing to record between visits.
- **Non-cancer palliative families.** The 2023 NP-NCD focus on cancer leaves heart-failure, COPD, kidney and dementia families out.
- **Consent for patients without capacity.** DPDP s.9 needs a "lawful guardian", which most families don't have. Capture consent and a nominee early, while the patient can still give them.

### (4) Threats, competitors, and what not to claim

**Threats**
- **Aarogya Setu 2.0** is a free national PHR with AI insights (launched June 2026) and can absorb the "store documents" pitch.
- Kerala's integrated local-government palliative system (plus the CARE grid already covered in the repo) could add a family module.
- The DPDP compliance deadline may be pulled forward to 13 November 2026 (not confirmed).

**Do not claim:**
- PM-JAY coverage or reimbursement for home palliative care, or for Sahara.
- That Sahara gives "access to morphine" or online refills (the NDPS ban on teleprescribing).
- That the goals-of-care note is a "living will", "AMD", or "legally valid".
- That Sahara holds the official copy of an AMD (the hospital records and the custodian do).
- "CDSCO-exempt" or "not a medical device" in general terms; the exclusion depends on each function.
- AI triage or AI advice (telemedicine guidelines and CDSCO).
- "DPDP compliant" before a formal review.
- "ABDM-integrated" (already in the repo).
- "Government-endorsed / NPPC partner".
- That palliative care reaches X% of patients, beyond the cited "1–2%" estimate.

**Open questions (unverified):**
- Exact HBP package codes for palliative care.
- The final outcome of WP 138/2024.
- The text of the 2023 Supreme Court order.
- Current RMI counts.
- The text of the Tamil Nadu 2019 policy.
- Whether the DPDP timeline compression was adopted.

### (5) Source log

1. https://nhsrcindia.org/sites/default/files/2021-06/Operational%20Guidelines%20for%20Palliative%20Care%20at%20HWC.pdf | HWC palliative guidelines: pain ≥6 referral, screening cutoff ≥4, morphine at PHC up to 4 weeks, patient card
2. https://clpr.org.in/wp-content/uploads/2024/04/Chart.petitioners.pdf | WP 138/2024 chart: 2017 NPPC duties vs Union affidavit; 609 districts approved, 493 functional
3. https://clpr.org.in/litigation/dr-rajshree-nagaraju-v-union-of-india-and-ors/ | Case timeline: orders of 7 Mar 2024 and 13 Oct 2025; listed 25 Nov 2025
4. https://clpr.org.in/wp-content/uploads/2024/04/6585_2024_3_44_64959_Order_13-Oct-2025_251028_003024.pdf | SC order, 13 Oct 2025
5. https://www.verdictum.in/court-updates/supreme-court/dr-rajshree-nagaraju-v-union-of-india-palliative-care-article-21-1524747 | "1–2% get palliative care" [snippet]
6. https://palliumindia.org/2014/03/done-the-process-is-complete | What the 2014 NDPS amendment changed
7. https://palliumindia.org/wp-content/uploads/2023/09/RMI-Guidelines-by-NCG-1.pdf | RMI rules: Form 3E/3H/3I, 60-day decision, home-care carriage, prescription format
8. https://palliumindia.org/?p=26088 | UP workshop: State Drugs Controller is the sole RMI authority; government hospitals deemed RMIs; 3 of 6 ENDs available
9. https://palliumindia.org/?p=22442 | Rajasthan workshop: lack of awareness of the 2015 Rules
10. https://palliumindia.org/2021/04/rmi-online-pain-training | Kerala accepts online training for RMI status
11. https://palliumindia.org/?p=4802 | 329 kg national morphine (2015)
12. https://palliumindia.org/2017/10/a-sixth-of-worlds-sick-needing-palliative-are-in-india-study-finds | Lancet Commission: 1/6 of global need is in India
13. https://palliumindia.org/resources | List of state policies (Goa 2023, TN 2019, Kerala 2019, Karnataka 2016, Maharashtra 2012/2022, Odisha)
14. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5735973/ | Kerala morphine use per person and RMI system
15. https://ecancer.org/en/journal/article/1486-situational-analysis-of-the-quality-of-palliative-care-services-across-india-a-cross-sectional-survey | 5.8% of centres have uninterrupted morphine
16. https://ecancer.org/en/journal/article/1837-an-evaluation-of-the-adequacy-of-indian-national-and-state-essential-medicines-lists-emls-for-palliative-care-medical-needs-a-comparative-analysis | Oral morphine missing from 17 state essential medicines lists
17. https://theindianpractitioner.com/maharashtra-to-implement-statewide-palliative-care-programme/ | Maharashtra Feb 2026 universal palliative care GR
18. https://palliumindia.org/2022/06/maharashtra-operational-guidelines-palliative-care | Maharashtra 2022 operational guidelines
19. https://www.etvbharat.com/amp/en/!state/kerala-govt-to-unify-all-palliative-care-systems-in-the-state-enn24112907065 | Kerala unification under the local-government department (Nov 2024)
20. https://www.onmanorama.com/news/kerala/2026/07/30/palliative-care-fund-crisis-kannur.html | Kannur funding crisis (Jul 2026)
21. https://www.palliativecare.in/palliative-care-status-in-india-west-zone/ | IAPC west-zone status (Goa policy draft at that time)
22. https://palliumindia.org/wp-content/uploads/2020/05/Karnataka-Palliative-Care-Policy-2016.pdf | Karnataka 2016 policy (draft for approval)
23. https://medicaldialogues.in/news/health/600-districts-covered-under-palliative-care-till-october-union-health-minister-159971 | Dec 2025 Rajya Sabha figures: 600 districts, 19 lakh home visits
24. https://scroll.in/article/1091264/indias-elder-care-was-supposed-to-reach-homes-but-households-and-families-are-taking-on-the-work | Home visits by state; quote on poor PM-JAY palliative coverage
25. https://pmc.ncbi.nlm.nih.gov/articles/PMC11654404/ | PM-JAY palliative-chemotherapy package audit, 33% appropriate [snippet; page was captcha-blocked]
26. https://www.businesstoday.in/amp/personal-finance/insurance/story/ab-pmjay-2024-what-is-ayushman-vaya-vandana-card-know-how-to-register-for-additional-health-cover-for-seniors-452061-2024-10-30 | Vay Vandana 70+ [snippet]
27. https://www.outlookmoney.com/retirement/ayushman-bharat-pm-jay-coverage-hospital-admissions-and-number-of-beneficiaries-explained | PM-JAY Rs 5 lakh hospital cover [snippet]
28. https://www.dpdpa.com/dpdpa2023/chapter-2/section7.html | DPDP s.7 legitimate uses incl. 7(f) medical emergency (unofficial mirror)
29. https://www.dpdpa.com/dpdpa2023/chapter-2/section9.html | s.9 children and persons with disability, lawful guardian
30. https://www.dpdpa.com/dpdpa2023/chapter-3/section14.html | s.14 right to nominate
31. https://www.azbpartners.com/bank/update-indias-digital-personal-data-protection-framework-comes-into-effect/ | DPDP Rules phases, 72-hour breach report, 1-year logs, healthcare exemption
32. https://www.scconline.com/blog/post/2025/12/26/digital-personal-data-protection-rules-2025-key-highlights/ | Rules notified 14 Nov 2025 (date conflict)
33. https://ssrana.in/articles/meity-plans-to-cut-short-dpdp-compliance-timeline-and-notify-cross-border-restrictions-for-sdfs/ | Proposed 12-month compression [snippet]
34. https://consentos.in/learn/dpdp-rules-notification-status/ | Compression not gazetted [snippet]
35. https://www.nishithdesai.com/research-and-articles/hotline/pharma-healthcare-update/the-next-frontier-in-medtech-regulation-dissecting-cdscos-draft-guidance-on-medical-device-software-15465 | CDSCO Oct 2025 draft: scope, classes, state vs central licensing
36. https://natlawreview.com/article/code-compliance-decoding-indias-medical-device-software-guidance | Final guidance of 21 Jul 2026; exclusions are function-specific
37. https://asiaactual.com/blog/india-medical-device-software-guidance/ | Final guidance; wellness apps out of scope
38. https://meddeviceguide.com/blog/india-cdsco-medical-device-software-mdsw-guidance-2026-guide | Table 2 matrix; lay-user serious condition may count as critical; EHR is non-device
39. https://psalegal.com/telemedicine-guidelines-2020-faq/ | Telemedicine guidelines: NDPS ban, caregiver authorisation, platform duties, no AI prescribing
40. https://www.scconline.com/blog/post/2023/01/25/supreme-court-simplifies-procedure-to-withhold-life-support-of-a-terminally-ill-patient-modifies-guidelines-given-in-2018-euthanasia-judgment-legal-research-legal-news-updates/ | 2023 hearing: magistrate removed, 48-hour boards; "not a single" AMD since 2018
41. https://lawbeat.in/news-updates/custodians-living-will-appointed-local-bodies-state-government-tells-bombay-high-court | Maharashtra 417 custodians; DigiLocker suggestion
42. https://www.barandbench.com/news/kerala-high-court-kochi-municipal-corporation-custodian-living-wills | Kerala HC orders Kochi to name a custodian (Dec 2024)
43. https://www.onmanorama.com/lifestyle/health/2025/10/09/what-is-a-living-will-kerala-roadblocks.html | Kerala living-will roadblocks; about 40 wills at Parippally; board composition
44. https://www.thenewsminute.com/karnataka/karnataka-approves-passive-euthanasia-what-the-sc-guidelines-say | Karnataka 30 Jan 2025 order details
45. https://en.wikipedia.org/wiki/Harish_Rana_v._Union_of_India | Harish Rana judgment, 11 Mar 2026
46. https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/jul/doc202676912801.pdf | ABDM: 90 crore+ ABHA, 100 crore+ records; Aarogya Setu 2.0 PHR
47. https://www.medianama.com/2021/12/223-national-health-authority-health-data-nominee/ | ABDM nominee for emergencies planned in 2021 [snippet]
48. https://www.iasparliament.com/current-affairs/the-need-to-focus-on-palliative-care | NP-NCD 2023 narrowing to cancer [snippet]
49. https://www.healthpovertyaction.org/wp-content/uploads/2019/12/India-opioid-crisis-briefing.pdf | About 4% of morphine need met [snippet, unverified]
50. https://nhm.assam.gov.in/sites/default/files/swf_utility_folder/departments/nhm_lipl_in_oid_6/menu/document/nppc_approved_district_rop_guidelines_fy_2024-2026.pdf | Assam NPPC 2024–26 district targets [snippet; fetch reset]

The downloaded source texts are in (scratch path) (one .txt per source).
