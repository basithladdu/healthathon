# IAPC and IAPC Kerala partner research

_Research agent report, 27 Oct 2026. Unedited except for this header._

# IAPC and IAPC Kerala: partner research for Sahara (27 Oct 2026)

**Method.** I pulled the whole palliativecare.in WordPress site through its public API: 1,450 of 1,452 pages and posts. One page (pages offset 431) kept returning HTML and could not be read. I converted everything to text and searched it, then read the key pages in full. I also checked care.iapckerala.in (headers and its public front-end bundle only; no login), arikecare.org, the OHC blog copies already saved in `healthathon/output/research/care-website-20261023/`, about 80 PubMed records (12 abstracts read in full), Pallium India, Cairdeas, News Karnataka and COSCUP. The extracted corpus is in `(scratch path)` (`INDEX.tsv` lists every page). I sent no forms, logins or messages.

**Gaps.**
- I did not search Reddit, Quora or LinkedIn.
- web.archive.org failed at the proxy.
- The auto-mode classifier blocked further probing of the CARE API hosts, so I stopped there.
- I did not read the Bylaws PDF.

## Headline findings

1. **IAPC does not provide care.** It is India's national umbrella body for palliative care: training, the journal, conferences, advocacy and a directory. It runs no home visits and has no patient-facing service. Sahara's "partner" here is a channel: courses, the journal, IAPCON, state chapters and members.
2. **care.iapckerala.in now forwards to Arike's CARE.** It returns `HTTP/2 301 location: https://care.arikecare.org/`. That instance's public config shows:
   - `REACT_CARE_API_URL = https://careapi.arikecare.org`
   - `REACT_DEFAULT_ENCOUNTER_TYPE = hh` (home health)
   - `REACT_ALLOWED_LOCALES = en,hi,ta,ml,mr,kn`
   - `REACT_ENABLE_SCRIBE = true`, `REACT_ENABLE_ABDM = false`
   - `REACT_SENTRY_ENVIRONMENT = prod-arike`
   
   So the CARE deployment OHC built "for IAPC" now resolves to Arike's production instance. Whether the two now share one instance is my inference [unverified].
3. **IAPC Kerala is a volunteer-led chapter and a named "Strategy Partner" of Arike.** It has no website of its own (iapckerala.in does not resolve; www returns Cloudflare error 526).

## 1. Who they are

**National IAPC**
- **Founding:** "a registered Public Trust and Society and was formed on 16 th March, 1994 in Ahmedabad, in consultation with the World Health Organisation (WHO) and the Government of India" (https://www.palliativecare.in/about-iapc/).
- **Scale:** "over 2500 registered life members which includes Individual Members, Institutional Members and Overseas members" (same page).
- **Mission:** "To serve as a national forum to inspire, inform and empower people, governments and medical organisations to improve the availability, accessibility and affordability of palliative care".
- **What they do:** "Train professionals and volunteers, lead advocacy and awareness initiatives, bring out publications, and help establish palliative care networks across the country".
- **Governance:** President, Secretary and Treasurer (the Core Committee), plus regional Executive Committee members and an Administration Team.
  - President (2025–2028): Dr Geeta Joshi, former CEO of the Community Oncology Centre, GCRI Ahmedabad; president.iapc.india@gmail.com.
  - Secretary (2026–2029): Dr Sanghamitra Bora, CEO of Mahavir Seva Sadan, Kolkata.
  - Treasurer (2026–2029): Dr Mayur Ghoghari, Bareilly.
  - Source: https://www.palliativecare.in/the-core-committee/
- **Executive Committee:** includes South region members Dr Praneeth Suvvari (Hyderabad; Scientific Chair, IAPCON 2026) and Dr Divya Narayan (Chennai). The IJPC Editor, Dr Raghavendra Ramanjulu (Aster Bengaluru, editor@jpalliativecare.com), sits on it. Source: https://www.palliativecare.in/the-executive-committee-members/
- **Office:** Ahmedabad, Mon–Sat 9 am–5 pm. National Information Officer Ms Nisha Krishnadas, nioiapc@gmail.com, info@palliativecare.in, +91 9446693898 (https://www.palliativecare.in/contact/).
- **Funding:** membership fees, conference income, donations ("Income tax relaxation under 80-G is available for all donations to IAPC") and endowments; for example, a ₹11 lakh gift created the "Sambit Das – IAPC Oration" (President's desk, Aug 2026).
- **Financial pressure:** IAPC became "liable for GST as its income exceeded the prescribed threshold of ₹20 lakhs" from FY 2018–19, and "the matter could not be brought to a final resolution… now requires immediate and decisive attention" (https://www.palliativecare.in/from-the-presidents-desk-september-2026/).
- **State chapters:** Assam, Delhi, Gujarat, J&K, Kerala, Odisha, Telangana, West Bengal, and a Northeast Regional Chapter. Chapters "work closely with governments and other stakeholders to advocate for and support the development of policies and programmes" (https://www.palliativecare.in/iapc-state-chapters/). The Northeast chapter was inaugurated on 12 Sep 2026 in Guwahati (President's desk, Oct 2026).
- **IAPC Academy of Palliative Medicine (APM):** set up 1 May 2022 with the Suprajit Foundation and BHT-Karunashraya, "to create an alternate path to specialist palliative medicine training" through the FAPM qualification (about-iapc).

**Kerala chapter (IAPC Kerala)**
- **Character:** "Kerala chapter is a particularly vibrant organ of Indian Association of Palliative Care (IAPC) and has most of the time been led by community activists rather than by professionals" (Pallium India, 23 Dec 2022, https://palliumindia.org/2022/12/office-bearers-iapc-kerala).
- **Office bearers elected 2022:** President Pradeep Koottanad, Secretary Kareem Vazhakkad, Treasurer K. Ramakrishnan Nair. The current term is [unverified].
- **Earlier leaders:** "Mr. Narayanan P., President, IAPC Kerala" (2020 newsletter item, https://www.palliativecare.in/for-palliative-care-with-the-global-community/); "Mr. Narayanan Puthukkudy, General Secretary, Indian Association of Palliative Care Kerala" (2017).
- **Main activity is volunteer conventions:**
  - Dec 2024: "a State meet of palliative care volunteers in Kerala… 28 th December, 2024… at Dayara, Pampady, Kottayam… inaugurated by Ms Veena George (Hon. Minster for Health)… over 1000 committed and selfless volunteers" (https://www.palliativecare.in/education-and-training-february-2025/).
  - April 2017, Aluva: "750 volunteers participated". Topics included "Government assistance for the palliative care patients under the Ardram Mission", "A review of Home-based care" and "Empowerment of patient's family in palliative care" (https://www.palliativecare.in/annual-meeting-of-palliative-care-volunteers-april-2017/).
- **Co-organised IPM PALMEDCON 2026 (7–8 Feb 2026, Kozhikode)** with the Institute of Palliative Medicine. Sessions covered "opioid safety, community partnerships, and government schemes for people with palliative care needs"; "over 60 participants" (https://www.palliativecare.in/education-and-training-march-2026/; https://www.palliativecare.in/other-upcoming-events-january-2026/).
- **Arike partnership:** listed among Arike's "National & Professional Bodies" and as "IAPC Kerala – Strategy Partner" alongside "Open Healthcare Network – Technology Partner" (https://arikecare.org/, https://arikecare.org/programmes).

**Kerala context** (IAPC status report, Kerala section written by Dr M R Rajagopal, https://www.palliativecare.in/palliative-care-status-update-in-india-south-zone/):
- "Of Kerala's 36 million population, at least 1.5 lakhs of people are estimated to be in need of palliative care at any given point in time… government network of 1,295 hospitals and over 345 non-government agencies".
- "More than 900 primary health centers have one fulltime palliative care nurse visiting every bed bound patient at home once a month."
- "57% of government palliative care providers and 55% of non-government palliative care providers could have uninterrupted access to oral morphine".
- Per-capita morphine use in 2015 was 1.56 mg, "roughly one fourth the global mean… 14 times more than the national consumption".
- Kerala "harbours 90% of the country's palliative care programmes though it has only 3% of the country's population" (IAPCON Kochi 2019 proceedings, https://pmc.ncbi.nlm.nih.gov/articles/PMC6504740/).

## 2. How they work day to day

IAPC's daily work is education and events, not patient care.

**Courses**
- **FCEPC** (formerly CCEPC): "launched in November 2007 in 12 centers… currently being delivered in over 50 centers"; online evening theory sessions (6–8 pm) plus an optional 10-day hands-on part; batch 13 started July 2026 (https://www.palliativecare.in/fcepc/).
  - July 2026 enrolment: "222 doctors and nurses are attending" (President's desk, Aug 2026).
  - 26 Sep 2026 exam: "196 doctors and nurses appearing at 34 centres" (President's desk, Oct 2026).
- **Volunteer foundation course:** a 16-hour online part plus a 16-hour observership, at most 30 per batch, with the next batch on 16–24 Nov 2026. Its goal is "creating a compassionate community" (https://www.palliativecare.in/iapcs-foundation-course-in-palliative-care-for-volunteers/).
- **Others:** Fellowship in Home and Hospice Palliative Care (four or five centres from Aug 2026), Pharmacy, Physiotherapy, Psychosocial, Paediatric, Nursing Faculty ("Sharpen Your Skills", 21–22 Nov 2026), Certificate in Basics (APM, 3rd cohort), the Nurses' Gold Medal, a virtual internship, and the IAPC Academy lecture series videos (about-iapc; home page).

**Conferences**
- **IAPCON** is held every February; "average of 500 delegates". IAPCON 2026 (Hyderabad, 13–15 Feb) had "more than 700 delegates", "256 abstracts", "16 workshops" and a "silent-conference" format (https://www.palliativecare.in/3-5-days-700-delegates-how-iapcon-2026-evolved-into-a-vibrant-knowledge-hub/).
- **IAPCON 2027** (34th) will be at SOA University, Bhubaneswar, in Feb 2027. Abstracts are open; there are Rs 4 lakh in awards and participation grants for early-career staff and volunteers.
- **Kerala international conference** with ALPHA Palliative Care, 9–11 Oct 2026 at Alpha Hospice, Edamuttam, Thrissur, on the WHPCD theme "Pain Management: An Essential Part of Palliative Care" (https://www.palliativecare.in/iapc-and-alpha-palliative-care-organise-international-conference-on-palliative-care-9-11-october-2026/). I found no post-event report.

**Journal**
- **IJPC:** "interdisciplinary, peer reviewed and open access… published Quarterly under the aegis of the IAPC". Indexed in PubMed Central, SCOPUS, ESCI and others (https://www.palliativecare.in/journal/).

**Advocacy, morphine access and NDPS**
- One stated aim: "Assist in implementing the amended Narcotic rules to ensure that morphine and other opioids are freely available" (about-iapc).
- 2014 NDPS rules: "The Director of Narcotics… consulted with leaders in palliative care on July 28, 2014… Dr Nagesh Simha, President of IAPC… the government had invited IAPC to submit its suggestions for the NDPS (Essential Narcotic Drugs) Rules 2014" (https://www.palliativecare.in/government-consults-with-iapc-and-other-stakeholders-on-proposed-narcotic-drug-rules/).
- Pune Declaration 2016 asks for NPPC funding, NDPS implementation and undergraduate palliative care education; "less than 2% of the those in need have access" (https://www.palliativecare.in/pune-declaration/).
- 2025 article: "Only 4% of India's population has access to palliative care… The challenge is not a lack of frameworks… it is uneven, incomplete, and inconsistent implementation" (https://www.palliativecare.in/from-paper-to-patients-why-india-must-put-palliative-care-policies-into-practice/).
- Kochi Declaration (IAPCON 2019), based on the Astana declaration: palliative care in primary care with "partnership and control by the community" (PMC6504740).

**End-of-life law**
- Advance directives: in 2018 IAPC "pointed out serious difficulties with the implementation of the procedures laid down by the Supreme Court" (https://www.palliativecare.in/iapc-newsletter-march-2019/).
- 11 March 2026, Harish Rana v. Union of India: IAPC published 11 salient points, including "CANH is a Medical Treatment… Even if CANH is given at home, it is still a medical treatment" (https://www.palliativecare.in/salient-points-of-supreme-court-judgment-on-11th-march-2026-regarding-harish-rana-case/).
- ACP-India team (Dr Savita Butola and others) supported the first implementation at AIIMS on 24 Mar 2026 (https://www.palliativecare.in/the-first-case-of-withdrawal-withholding-of-life-support-in-india/).
- The Resources page lists a "Living Will and Healthcare Power of Attorney Authorisation", an "Advance Medical Directive (Living Will) worksheet template", SpiDiScI-PC, the "Standards Audit Tool for Indian Palliative Care Programs" and the "Model Legal Framework 2.0" (https://www.palliativecare.in/resources/).

**Caregiver material and awareness**
- Caregivers page (2020): "Palliative Care… treats the patient and the family as one unit… The aim is to provide links to useful resources". It is a list of outside links: Caregiver Saathi, Dementia Care Notes and others (https://www.palliativecare.in/caregivers/).
- Monthly infographics include "Role of Caregiver" (Jan 2024), "Self-care for Informal Caregivers" (Jun 2024), "Basics of Morphine" and "Titrating doses of oral Morphine" (Mar–Apr 2024), "Advance Care Planning and Medical Directives in India" (Nov 2024), "Palliative Care at Home" (Sep 2025) and "Bereavement Care" (Oct 2026) (https://www.palliativecare.in/iapcs-infographics/).
- Hindi "Palliative Care Workbook for Carers": "the need to translate it in Hindi was felt… looking for funds and publishers" (https://www.palliativecare.in/hindi-version-of-palliative-workbook-of-carers/).
- **Directory:** a state- and district-wise list of centres, with the warning that IAPC "does not claim the authenticity of the centers listed" (https://www.palliativecare.in/palliative-care-centres-kerala/).

IAPC runs no helpline, home visits or OPD.

## 3. Technology they use

- **CARE (OHC)**
  - Dec 2024, OHC blog: "CARE was deployed for the Indian Association of Palliative Care at care.iapckerala.in, and the team mapped the additional features needed for home care, including a nurse log update feature. The team also studied Arike's NGO-based deployment model". Source: https://ohc.network/blog/building-care-open-healthcare-foundation, saved locally at `healthathon/output/research/care-website-20261023/text/ohc.network__blog__building-care-open-healthcare-foundation.full.txt`.
  - Today the domain forwards to care.arikecare.org (see headline finding 2).
  - Arike's own plan: "Integrating the CARE software across units for a single source of truth" (https://arikecare.org/transparency).
  - Arike moved from CARE 2.0 to 3.0 in May 2025 (OHC blog, fhir-lab-module-and-care-3-deployments).
  - I found nothing in which IAPC national or IAPC Kerala talks publicly about using CARE themselves; every mention comes from OHC or Arike.
- **Kerala Palliative Care Grid:** launched 28 Jun 2025. "The grid will systematically register patients, arrange home visits, track treatments, and guide caregivers. Over 1.58 lakh patients have been enrolled" (https://newskarnataka.com/india/kerala-launches-tech-driven-palliative-care-grid-to-support-ageing-population/30062025/). OHC's COSCUP talk says CARE is used "to coordinate telehealth and community health workers" (https://pretalx.coscup.org/coscup-2025/talk/PTURTS/). That IAPC Kerala has a role in the Grid is [unverified].
- **IAPC Mobile App:** launched Feb 2026 for conference updates (Play `com.app.iapc`, App Store id6757645161). "IAPC-related sections are currently not fully active" (https://www.palliativecare.in/download-iapc-app-now-for-iapcon-2026-updates/). It is for members, not patients.
- **PalliKare:** promoted by IAPC, a Karunashraya clinician app (https://www.palliativecare.in/free-palliative-care-app-pallikare/).
- **Other tools:** a WordPress member portal, WhatsApp numbers for course coordinators, and Google Forms-style submissions.
- **Stated digital needs:** none written as a technology plan. Closest are the 2025 policy article, which asks states to "add patient-reported pain scores, caregiver burden indicators, and place-of-death metrics to their monitoring systems", and the plan to revise the Standards Audit Tool.

## 4. Pain points and gaps they describe

- **Opioid access:**
  - Kerala: "None of the GO and only 36% (36/100) of NGO PCPs were able to prescribe oral morphine" (Lijimol et al., IJPC 2020, PMID 33623312).
  - Nationally: "Only 5.8% provide uninterrupted access to oral morphine" (Ghoshal et al. 2022, IAPC Standards Audit Tool survey of 223 centres, PMID 36819806).
  - Essential medicine lists: "many state lists omitted oral morphine altogether" (2025 article).
  - Opiophobia "among both providers and beneficiaries" (https://www.palliativecare.in/partnering-with-echs-to-initiate-palliative-care-in-armed-forces/); "We addressed their opiophobia" (https://www.palliativecare.in/experiences-in-caring-for-the-elderly-in-a-rural-hospice/).
- **Symptom documentation:** "Patient-reported pain was routinely documented by 21%… of the GO and 65%… of NGO sites"; "Psychosocial and spiritual issues were documented routinely in only 4%… and 48%"; "60% of GO PCPs had no full time physician with training" (PMID 33623312).
- **Prognosis and goals-of-care records:** a Thrissur NGO wrote that "documentation of discussion on prognostication was not done… This had created communication gap between the professionals, the patients and their family members… mismatch between the patients' expectations and the services provided". A quality-improvement project raised documentation "from 0 to 80%" (PMID 34511788, Pain and Palliative Care Society, Thrissur).
- **Follow-up between visits:** in Kerala, PHC nurses visit bed-bound patients only "once a month" (status report). Volunteers report that "families often intrude (via Phone calls, messages) on one's 'me time' and beyond official hours" (https://www.palliativecare.in/reflections-from-a-palliative-care-volunteer-counsellor/).
- **Caregiver invisibility:** "very little awareness, let alone sensitivity towards the challenges of the caregiver" (https://www.palliativecare.in/recognizing-the-invisible-life-of-caregivers/); "Always ensure the caregiver feels informed and included" (https://www.palliativecare.in/what-being-a-caregiver-taught-me-a-doctors-perspective/).
- **Language:** Hindi carer material lacks funding; ZBI-22 has Malayalam translation problems (see section 5).
- **Advance directives:** procedures are hard to carry out (2018 statement); the Harish Rana judgment needs a "comprehensive legal framework".
- **Kerala system gaps:** "lack of integration of palliative care into medical colleges" and "inadequate collaboration between the government and non-government organisations" (status report).

## 5. Research and data relevant to Sahara

- **Brief AMC-CBI (Kerala, 2026):** a 3-item caregiver burden screen ('lost hope', 'feel sad', 'financial burden'), cut-off ≥3. It was tested on 60 home-care caregivers in rural Kollam who were "interviewed telephonically". The Malayalam ZBI-22 showed "translation issues… especially with the word 'duritham'" (Kochuvilayil & Varma, IJPC 2026;32(3):296-303, PMID 42694816). **This is the best fit for a Sahara caregiver check-in.**
- **ESAS:** presented at IAPC academic sessions as "one of the most commonly used multidimensional screening tool[s]" (IAPC corpus, education pages). There is also a Kannada ESAS-r Renal validation (PMID 37325264). IPOS appears only in a webinar mention; I found no IAPC adoption of IPOS or POS.
- **IAPC Standards Audit Tool:** used for the national quality survey (PMID 36819806).
- **SpiDiScI-PC:** a spiritual distress scale for India (Resources page).
- **Telemedicine and caregiver acceptance:** "Nearly 82% of patient/caregivers gave a positive feedback… 76%… would prefer teleconsultation in future" (Atreya et al., Tata Medical Center Kolkata, PMID 33088085).
- **WhatsApp in home care:** "provides a means for quick tele-consultation… continuity of the care is maintained" (Dhiliwal & Salins, TMH, PMID 25709195).
- **App gap named in IJPC:** "need for sincere palliative care clinicians to work together with software professionals to develop… apps in accordance with the family/caregivers' necessities" (PMID 26962291).
- **Kerala sustainability case study:** PMID 34035612.
- **End-of-life preferences (Tamil Nadu hospice):** PMID 41113852.
- **ISCCM–IAPC end-of-life position statement:** PMID 38477011.
- **IAPC–IAPSM primary care position:** PMID 38665464 / 40124875.

## 6. How Sahara could help, what they would not want, and the first ask

**Feature-to-gap matches**

| Sahara feature | Their stated gap | Source |
|---|---|---|
| 0–10 symptom diary and 3D pain body that families fill in between visits | Pain documented routinely at only 21% of GO sites; monthly PHC nurse visits | PMID 33623312; status report |
| Medicine log (taken/missed), including morphine doses | Opiophobia; titration education (IAPC infographics Mar–Apr 2024) | infographics; rural hospice article |
| Goals-of-care conversation, recorded with consent, then a doctor-reviewed note, then the family confirms the exact version | Prognosis discussions not documented, causing family–team mismatch; Harish Rana procedures; Living Will worksheet | PMID 34511788; Rana salient points; Resources |
| Brief AMC-CBI caregiver check (3 items, Malayalam) | Caregiver burden invisible; Malayalam ZBI-22 hard to use | PMID 42694816; caregiver articles |
| Hindi/English, with Malayalam a likely priority for Kerala | Hindi carer workbook unfunded; Kerala's CARE instance already allows `ml` | Hindi workbook post; Arike config |
| Fewer ad-hoc phone calls and messages to volunteers | Volunteer burnout from families calling | volunteer counsellor article |

**What they would likely not want**
- A competing staff EMR. Kerala is standardising on CARE through the Grid and Arike's "single source of truth".
- Anything implying IAPC endorses a product. IAPC even disclaims its own directory.
- Data leaving the phone outside India, or anything that sidesteps NDPS rules. Sahara must not suggest morphine doses.
- A family app that does not feed into the team's record.

**Best first ask**
1. **To the IAPC national office** (Nisha Krishnadas, nioiapc@gmail.com): submit a Sahara pilot abstract or workshop proposal for IAPCON 2027 in Bhubaneswar, and offer a short caregiver-diary module or slot for the volunteer course (next batch 16–24 Nov 2026). The aim is a review by the IAPC education team, not an endorsement.
2. **To IAPC Kerala, through Arike:** a 4–6 week, small-n family-side pilot alongside Arike's CARE home-care teams in Kochi. Measure symptom-diary completion and use the Brief AMC-CBI, with the family-confirmed goals-of-care note as the main feature. Ask OHC or Arike whether CARE can take a read-only summary [CARE has no family side, per `CARE_VS_SAHARA_FOR_TEAM.md`].
3. **Low-cost contribution:** a Malayalam/Hindi caregiver page that turns IAPC's own infographics (Caregiver role, Self-care for informal caregivers, Morphine basics, ACP) into in-app content, credited to IAPC and with their permission.

## Page log (URL | read? | one line)

**palliativecare.in**
- palliativecare.in/ | Y | home page; links to IAPCON 2027, WHPCD 2026 and courses
- palliativecare.in/wp-json pages and posts (1,450 items) | Y (full text, searched) | whole site
- /about-iapc/ | Y | history, members, mission, projects
- /the-core-committee/ | Y | President, Secretary, Treasurer
- /the-executive-committee-members/ | Y | regional EC members, IJPC editor
- /the-administration-team/ and /contact/ | Y | NIO contact, Ahmedabad office
- /iapc-state-chapters/ | Y | 9 chapters, Kerala included
- /iapc-definition-of-palliative-care/ | Y | IAPC definition (Rajagopal, Paleri, Simha)
- /palliative-care-status-update-in-india/ and /palliative-care-status-update-in-india-south-zone/ | Y | Kerala status, morphine figures
- /palliative-care-centres-kerala/ (plus 15 district pages) | Y | directory with disclaimer
- /from-the-presidents-desk-august-2026/, -september-2026/, -october-2026/ | Y | courses, GST, conferences
- /download-iapc-app-now-for-iapcon-2026-updates/ | Y | IAPC app
- /free-palliative-care-app-pallikare/ | Y | PalliKare
- /3-5-days-700-delegates-how-iapcon-2026-evolved-into-a-vibrant-knowledge-hub/ | Y | IAPCON 2026 figures
- /iapc-and-alpha-palliative-care-organise-international-conference-on-palliative-care-9-11-october-2026/ | Y | Thrissur conference
- /fcepc/ | Y | FCEPC
- /iapcs-foundation-course-in-palliative-care-for-volunteers/ | Y | volunteer course
- /journal/ | Y | IJPC
- /resources/ | Y | legal and ACP tools
- /iapcs-infographics/ | Y | monthly topics
- /important-highlights/ | Y | index of highlights
- /advocacy/ | Y | CPC and NDPS 2014
- /government-consults-with-iapc-and-other-stakeholders-on-proposed-narcotic-drug-rules/ | Y | NDPS rules consultation
- /pune-declaration/ | Y | 2016 asks
- /from-paper-to-patients-why-india-must-put-palliative-care-policies-into-practice/ | Y | implementation gap
- /salient-points-of-supreme-court-judgment-on-11th-march-2026-regarding-harish-rana-case/ | Y | end-of-life law
- /the-first-case-of-withdrawal-withholding-of-life-support-in-india/ | Y | ACP-I report
- /iapc-newsletter-march-2019/ | Y | IAPC stand on advance directives
- /caregivers/ | Y | caregiver links page
- /hindi-version-of-palliative-workbook-of-carers/ | Y | Hindi workbook needs funding
- /caring-for-the-caregivers/, /what-being-a-caregiver-taught-me-a-doctors-perspective/, /recognizing-the-invisible-life-of-caregivers/ | Y | caregiver narratives
- /reflections-from-a-palliative-care-volunteer-counsellor/ | Y (quote) | volunteer burden
- /experiences-in-caring-for-the-elderly-in-a-rural-hospice/ and /partnering-with-echs-to-initiate-palliative-care-in-armed-forces/ | Y (quotes) | opiophobia
- /education-and-training-february-2025/ | Y | IAPC Kerala volunteer conference, Dec 2024
- /education-and-training-march-2026/ and /other-upcoming-events-january-2026/ | Y | PALMEDCON with IAPC Kerala
- /annual-meeting-of-palliative-care-volunteers-april-2017/ | Y | Kerala volunteer meet, 750 people
- /for-palliative-care-with-the-global-community/ | Y | IAPC Kerala president, 2020
- /help/ | Y | member portal help
- Bylaws PDF (2025) | N | not read

**Kerala chapter, CARE and Arike**
- care.iapckerala.in | Y (headers and front-end config) | 301 to care.arikecare.org; CARE home-health configuration
- www.iapckerala.in / iapckerala.in | Y | Cloudflare error 526 / does not resolve
- arikecare.org/, /programmes, /transparency, /annual-report, /understand | Y | IAPC Kerala named as strategy partner; CARE "single source of truth"
- palliumindia.org/2022/12/office-bearers-iapc-kerala | Y | 2022 Kerala office bearers
- ohc.network blog: building-care-open-healthcare-foundation (local copy) | Y | care.iapckerala.in deployment
- ohc.network blog: fhir-lab-module-and-care-3-deployments and care-core-as-operating-layer (local copies) | Y | Arike on CARE 3.0
- newskarnataka.com Kerala Palliative Care Grid article | Y | Grid launch, 1.58 lakh patients
- pretalx.coscup.org/coscup-2025/talk/PTURTS/ | Y | CARE and Grid pilot

**Research and other sources**
- cairdeas.org.uk IAPC Kerala 2019 blog | Y | IAPCON Kochi 2019
- pmc.ncbi.nlm.nih.gov/articles/PMC6504740/ | Y | IAPCON Kochi proceedings, Kochi Declaration
- PubMed abstracts read: 25709195, 26962291, 33088085, 42694816, 33623312, 34035612, 36819806, 34511788, 41113852, 33088087, 34898939, 33088083 | Y | see section 5
- About 70 further PubMed titles | titles only | IJPC topics on caregiver burden, ESAS, ACP, Kerala and morphine
- Reddit, Quora, LinkedIn, YouTube | N | not searched
- web.archive.org | N | proxy failure
