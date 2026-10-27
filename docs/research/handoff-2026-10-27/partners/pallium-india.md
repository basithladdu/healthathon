# Pallium India (Trivandrum) partner research

_Research agent report, 27 Oct 2026. Unedited except for this header._

# Pallium India: partner research for Sahara (read 27 Oct 2026)

**How I read it.** palliumindia.org is a WordPress site. Its own sitemap lists 2,831 posts and 115 pages. A normal crawl ran at about 18 s per page, so I pulled the full text of every post and page through the site's public WordPress API instead: 31 requests, 1 s apart, **2,947 items**. I searched all of it and read about 45 items in full.

I also fetched 8 Pallium PDFs and read them in full or searched them:
- Annual Reports 2021-22, 2022-23, 2023-24 and 2024-25
- the 2026 Give.do due-diligence report
- the ACP booklet
- two Advance Medical Directive (AMD) templates

Outside the site:
- 86 PubMed papers with a Pallium or TIPS affiliation. I read 12 abstracts in full.
- OHC blog posts. These came from the team's saved copies of 23 Oct, not fetched live.
- Wikipedia and the Give.do profile.

**Gaps.**
- WebFetch could not resolve hosts, so outside pages came through curl only.
- GitHub code search was blocked for this session, so I found no Pallium-specific CARE code.
- I found no Reddit or Quora threads about Pallium.
- I did not open YouTube; the annual report links its webinars there.

Raw corpus, if you want to grep it: `(scratch path)` (index in `raw/index.tsv`). PDFs and text are in `.../pallium/pdf/`.

**Already in our app.** Sahara already uses Pallium's directory. TEAM_HANDOFF.md says: "1,631 public listings across all 36 states/UTs, from 81 Pallium India/IAPC pages". The app also links to Pallium's living-will resources. That matters for section 6.

---

## 1. Who they are

**Legal form and aim**
- palliumindia.org/pallium-india: "a national registered charitable trust (No. 693/IV/2003) formed in 2003… based out of Trivandrum… alleviating serious health-related suffering (SHS) of 7-10 million people in India. We strive to demonstrate quality palliative care, to educate professionals and the public, and facilitate development of palliative care services."
- Their model is "Demonstrate, Educate & Facilitate (DEF)" (Annual Report 2024-25, p.5).

**Clinical arm (TIPS)**
- TIPS (Trivandrum Institute of Palliative Sciences) is "a World Health Organization Collaborating Centre (WHOCC) for Training and Policy on Access to Pain Relief" (/about-tips). It was created in 2006 and named a WHO centre in 2012 (/pallium-india/genesis).
- Address: "Aisha Memorial Hospital Building, Manacaud P.O., Paruthikkuzhy, Thiruvananthapuram – 695009" (/contact).
- There is also a Bengaluru office at Jayanagar (/contact).

**Scale (team page)**
- /pallium-india/team: "138 full-time staff, including 10 consultants, and a network of over 150 active volunteers. Our care team includes 15 doctors, 31 nurses, and 16 non-medical clinical staff… 17 OPDs, 16 homecare teams and the State Facilitation Team with 7 regional coordinators."
- OHC's account matches: "an 18-bed inpatient facility, 17 outpatient departments, 138 full-time staff, and 16 home-care teams" (ohc.network/blog/care-dashboards-testing-new-deployments).

**Trivandrum activity, FY 2024-25 (Annual Report 2024-25, p.5)**

| Measure | Count |
|---|---|
| Unique patients | 5,215 |
| Patient contacts | 39,817 |
| Home visits | 13,955 |
| Home-care patients | 2,643 |
| Outpatients | 2,848 |
| New registrations | 2,240 |
| Telehealth contacts | 3,144 |
| Telehealth beneficiaries | 1,501 |
| Inpatient admissions (patients) | 198 |
| Inpatient admissions (total) | 246 |
| Psychosocial support | 7,454 |
| Free medicines | 3,266 |
| Bereavement support | 448 families |

- Earlier years: FY 2023-24 had 5,278 unique patients and 13,946 home visits. FY 2022-23 had 4,874 patients and 12,295 visits.
- Over 2007–2016, TIPS saw 11,620 new patients and home visits grew 57% between 2014 and 2016 (Krishnan et al., J Glob Oncol 2018, PMID 30241254).

**Outside Kerala (Annual Report 2024-25)**
- "over 695 healthcare professionals and 368 volunteers were trained."
- "We helped catalyse 70 new palliative care centres across India and conducted 15 workshops in 24 states/UTs to improve access to essential opioids."
- Karnataka: a new outpatient service at Jayanagar General Hospital, Bengaluru (Jul 2026 post /2026/08/listening-to-the-community).

**Funding (Annual Report 2024-25, p.34)**
- Consolidated income ₹1,072.52 lakh: grants ₹744.37 lakh, donations ₹230.60 lakh.
- Expenditure ₹1,087.24 lakh, so a small deficit.
- About 17% of income is foreign money (FCRA, the foreign-contribution licence), per the Give.do due-diligence report.
- SBI CSR gave "₹25 lakhs… to strengthen Pallium India's homecare services" (Annual Report 2024-25).
- HCLTech Grant Edition X, runner-up, "₹25 Lakh" (/2025/05/hcl-tech-grant-edition-x).
- Give.do Gold accreditation "valid through August 16, 2027" (/pallium-india/reports).
- Home care is free: "home-care services are provided free of cost, including the provision of medicines" (due-diligence report).

**Leadership (public only)**
- Founder and Chairman Emeritus: Dr M R Rajagopal, Padma Shri 2018.
- Chairman & CEO: Binod Hariharan. He is an engineer on the boards of 3 IT companies, and his July 2023 message said: "We must adopt technologies which can improve the quality and quantity of our care delivery."
- Others: Dr Sunil Kumar M M (Director, TIPS); Manoj Gopalakrishnan (Executive Director, TIPS); Smriti Rana (Director – Strategic Communications); Shriya Singh (COO); Dr Adarsh Varghese (Chief National Capacity Officer); Dr Amirtha Thampi (clinical; the 2024-25 report calls her "Head, Clinical Services"); Dr Arjun Devarajan (Clinical Services In-Charge, Karnataka Mission); Dr Sreejini Jaya (Head – Research and Innovation).
- The Advisory Board includes **Dr Athul Joseph Manuel, "Co-founder of Arike Care and Chief Medical Officer at Open Healthcare Network"** (/pallium-india/team). He is a direct link between OHC/CARE and Pallium.
- Also on the Advisory Board: Kishore Nair, "technical and management advisor… since its inception".
- **Leadership is changing:** a CEO vacancy was posted 14 Sep 2026: "seeking a visionary… CEO… to professionalize, scale, and future-proof" (/2026/09/ceo-pallium-india-trivandrum). A CHRO vacancy was posted the same day.
- Contacts: info@palliumindia.org; general +91 97467 45497; telehealth +91 96458 84889 / +91 86068 84889; Bengaluru +91 79073 01551 (/contact).

## 2. Day-to-day work

**Settings of care**
- /our-work: "Services are provided through Out-Patient Clinics, In-Patient Services, and Home Visits."
- Outpatient clinics run at Government Medical College, General Hospital, CHC Poovar, Vizhinjam and SAT Hospital (paediatrics), plus volunteer link centres.
- **Link centres** are "manned by volunteer groups from the same community… a primary school, a library or a community centre" (/our-work).

**Home care**
- Visit frequency: "patients generally receiving visits at least twice a month and additional visits undertaken when required, including in emergency situations… medication support, dressing and general wellbeing checks, while caregivers are also provided counselling" (Give.do due-diligence report 2026).
- Seven days a week since May 2024: "Sundays from 9am to 5pm… physician, nurse, and palliative care assistant… home care is now operational seven days a week in Trivandrum District" (Annual Report 2024-25, p.27).
- Cost: Give.do quotes "INR 2000 covers a palliative care home visit" [unverified: seen in a search snippet only].

**Telehealth**
- /2022/04/telehealth-consultation: "FREE national telehealth helpline… English, Hindi, Malayalam and Tamil… Monday to Saturday 9:30 a.m. to 4pm."
- It grew out of COVID: "calls rose from 2 in January, to 320 in November" 2021 (/2022/01/the-year-that-was).
- Other helplines: Sukh-Dukh grief helpline, 8 languages, since 2020; Saath Saath national line 1800-202-7777, joined with Cipla Foundation and others.

**Care pathway**
- The PALLIUM framework paper (ecancer 2026, PMID 42555446): "Care is delivered through home visits, outpatient clinics, inpatient units or telehealth, depending on the severity of symptoms and geographical access… Social officers assess and address financial and community-based needs."

**Morphine and opioid access**
- Pallium is a Recognised Medical Institution (RMI, licensed to stock morphine). The Punjab drug controller "observed Pallium's opioid management systems" (Annual Report 2024-25).
- Free medicines went to 3,266 patients.
- National work: 15 opioid workshops in 2024-25.

**Training**
- Courses: a 6-week certificate course (CCPPM/CCPN), online foundation courses, the Global Fellowship (29 fellows passed in 2024), Project ECHO, and volunteer training. The first **Hindi** online volunteer course had "nearly 230 participants" (Annual Report 2024-25, p.20).
- There is also a hands-on clinical module (/2026/09/my-experience-at-the-pallium-india-hands-on-module).

**Rehabilitation and social support**
- Halfway Home for spinal injury, vocational rehabilitation, Ujjwal (women patients and carers), Kuttikoottam (children's education; 305 children supported), and food kits (/our-work).

**Bereavement**
- Unarv: "a bereavement support group called 'Unarv' (meaning awakening)" (/2022/08/grief-can-be-fatal).
- 448 bereaved families were supported in FY 2024-25.

## 3. Tech they use

**CARE HMIS**
- OHC: "On 5 December 2025, Pallium India officially launched CARE HMIS… CARE supported inpatient, outpatient, and home-care operations" (ohc.network/blog/one-million-encounters-paperless-governance).
- In January 2026 they "transitioned to full-scale CARE usage across outpatient and inpatient workflows… refine workflows for facility and home-care operations" (ohc.network/blog/care-hmis-hospital-go-live).
- OHC's setup work: "patient data migration, structured questionnaires aligned with care protocols, analytics dashboards, inventory digitization, charge item configuration" (ohc.network/blog/care-dashboards-testing-new-deployments).
- What Pallium needed: "inpatient, outpatient, home-care workflows, reporting, and care-protocol questionnaires" (ohc.network/blog/care-core-as-operating-layer).
- Give.do (2026) confirms: "uses a Care Platform to capture and record program and beneficiary data" and "uses Tally integrated with Care software."

**Before CARE**
- OHC mentions "inconsistencies from the previous system", so there was an earlier system [name unknown].
- Research data 2020–24 was "extracted and digitized from registries maintained by Pallium India" (BMC Palliat Care 2025, PMID 40604810).
- Earlier attempts: an HP telemedicine and EMR MoU in 2011 (/2011/09/telemedicine-a-new-endeavour-with-hp-india) and MoveInSync home-visit routing in 2023 (/2023/07/july-2023).
- Today they also use Excel and Google Sheets (Give.do).

**WhatsApp and phone**
- These are the de facto family channels. The caregiver-burden study collected its data "telephonically" (PMID 41239314).
- A guest post by a Mumbai participant in Pallium's volunteer course, describing PalCare (another NGO, not Pallium), shows the pattern: "a WhatsApp group was created so the whole family… would stay on the same page" (/2026/07/what-i-saw-shadowing-a-palliative-care-team-for-a-day).

**No family or patient app.** I found no Pallium app for patients or families anywhere.

**Stated digital needs**
- "Palliative care mobile apps offer symptom tracking and management features… medication reminders… status updates, which help coordinate care plans" (/2024/02/transforming-palliative-care-with-technology). The post closes by inviting readers to suggest useful technologies.
- The HCLTech grant proposal: "A digital platform will facilitate patient tracking and service monitoring in rural Kerala" (/2025/05/hcl-tech-grant-edition-x).
- A Karnataka job post asks for "Familiarity with electronic medical records (EMR) and digital documentation" (/2026/07/lead-palliative-care-physician-karnataka).
- A June 2026 post by their Karnataka nurse lead: "Tele-support enables ongoing symptom monitoring, timely intervention, and caregiver guidance" (/2026/06/nursing-excellence-in-home-based-palliative-care).

## 4. Pain points they describe publicly

**Caregivers are untrained and overloaded**
- "A tiny proportion received any caregiver training, while most stepped into this role without any prior knowledge" (/2025/12/generating-evidence-in-fostering-palliative-care-delivery).
- 54.8% carry moderate burden. Burden is linked to "difficulty in seeking help… Lack of breaks, limited support" (PMID 41239314).

**What families wish they had been told** (Karnataka listening session, May 2026)
- "when and how to shift from hospital to home care, training for caregivers, expectations during disease progression, symptom management, and financial planning."
- What good care looks like to them: "availability during emergencies, continuity and consistency… caregiver literacy… direct access to healthcare providers, respite care" (/2026/05/report-of-community-listening-session-on-home-based-palliative-care-in-karnataka).

**Women's voices are left out of decisions**
- "despite women often being the primary caregivers, their opinions are frequently ignored during medical decisions" (same report).
- See also /2026/03/beyond-resilience-why-systems-must-support-women-caregivers.

**Practical coordination**
- "arranging transportation, coordinating hospital visits, procuring oxygen cylinders… balancing employment with caregiving" (/2026/08/listening-to-the-community).
- "families are frequently expected to perform complex caregiving tasks without adequate preparation" (same post).

**Money**
- "Debt was reported by 31.3% of families… 66.8% of patients could not afford… medicines" (PMID 40604810).

**Morphine access**
- "accessing morphine… proved nearly impossible… regulatory barriers, stigma, patchy availability" (/2025/08/indias-struggle-with-quality-care).
- "Only 5.8% provide uninterrupted access to oral morphine" (Ghoshal et al. 2022, PMID 36819806; Rajagopal co-author).

**Advance care planning**
- "families often avoid these conversations—until it's too late… treating them not as documents, but as dialogues" (/2025/06/amd-legal-right-to-practical-reality).

**Between visits**
- "Only you people call to ask if I'm okay" (a beneficiary).
- "All I need is for someone to pick up my call" (another beneficiary).
- Both quotes are in the Annual Report 2024-25 testimonials, p.35–36.

**Language**
- Their materials come in Malayalam, Tamil and Kannada (AMD templates, the Malayalam monthly "Sahayatra"). The telehealth line runs in four languages.

**Staff and reach**
- The workforce gap is the general Indian picture, and teams cap their caseloads.
- Rural reach is addressed through link centres and telehealth.

## 5. Published research relevant to Sahara

- **Caregiver burden** (Suresh, Guruprasad, Jaya; BMC Palliat Care 2025; PMID 41239314). 270 caregivers in Thiruvananthapuram, measured with the Achutha Menon Centre Caregiver Burden Inventory; 54.8% moderate burden.
- **ACP booklet** (Sharma P, … Sharma G, Rana S…; BMC Med Educ 2026; PMID 42185821). It was validated for understandability (95%) and actionability (100%) using PEMAT, a standard checklist for patient leaflets. This is very likely the booklet "Care That Respects You: A Simple Guide to Advance Medical Directives" that Pallium posted on 23 Oct 2026. The booklet credits "Gayatri Sharma… Supported by Advance Care Planning India" and has two worksheets: "My Values and Health Care Goals" and "My Treatment Choices". Link: palliumindia.org/wp-content/uploads/2026/10/ACP_booklet_English_ver2025.pdf. The PDF's font encoding is garbled, so I can quote only headings exactly. That the booklet and the paper are the same is [unverified].
- **Socioeconomic vulnerability in home care** (PMID 40604810). n=964, drawn from Pallium registries.
- **PALLIUM framework** (PMID 42555446). The decision flowchart for choosing home, outpatient, inpatient or telehealth care.
- **City Homecare quality-improvement project** (IJPC 2021, PMID 34511785). It tracked a 0–10 team satisfaction score and triage accuracy.
- **ESAS** (the Edmonton Symptom Assessment Scale, a 0–10 checklist) appears in a paper Pallium co-authored on a Srinagar clinic: pain fell from 7 to 2 (BMC Palliat Care 2026, PMID 42277812). That clinic is not Pallium's own service.
- **Pain scale in their projects:** pain is "documented on a 0-10 numerical scale" (/our-work/facilitation). In Himachal training they taught "standardized documentation (pain score, ECOG, ADL, distress thermometer)" (/2025/07/compassionate-care-in-the-hills-of-chamba).
- **The symptom form TIPS uses inside CARE is not published** [unverified]. Ask them. It decides whether Sahara's 0–10 diary maps onto their form.
- **Other:** a screening tool for serious health-related suffering (IJPC 2022, PMID 35673368); "Family caregiving as a pathway" (Front Public Health 2025, PMID 41283053).

## 6. How Sahara could help, what to avoid, and the first ask

Each match below pairs a Sahara feature with a pain point they have stated.

1. **Symptom diary 0–10 plus 3D pain body, filled in between visits.**
   - Need: visits are about twice a month; staff talk about "tele-support… ongoing symptom monitoring"; they have published praise for apps with "symptom tracking".
   - Pitch: Sahara collects the family's input; staff review it in CARE.
2. **Medicine list with a taken/missed log.**
   - Need: 3,266 patients get free medicines, morphine is tightly controlled, and caregivers are untrained.
   - Value: a taken/missed log shows adherence and opioid use between visits, which CARE does not capture from families.
   - Careful: we must not look like we change morphine doses.
3. **Family invites and a shared view.**
   - This replaces ad-hoc WhatsApp groups and gives every carer, including women who are "frequently ignored", the same information.
4. **Goals-of-care conversation, recorded with consent, then a doctor-reviewed note, then family confirmation.**
   - Their themes: "dialogues, not documents", their ACP booklet worksheets, and their AMD templates.
   - Offer to build their booklet's two worksheets into the app, with credit. This is the strongest content fit.
5. **Visits and documents.**
   - "expectations during disease progression" and "who to call".
   - A Sahara emergency or contact card could carry their telehealth hours.
6. **Directory.**
   - We already reuse 1,631 listings from their pages. Offer to send corrections back to them (our survey data) rather than only taking from them.

**What they would likely not want**
- A second system of record, or any duplicate entry for staff. OHC is still smoothing their CARE rollout in 2026.
- A Hindi/English-only family app in Trivandrum. Their patients speak Malayalam; Karnataka needs Kannada.
- Patient data on Sahara's server without data-protection terms, consent and data-protection-law compliance.
- Anything AI that suggests doses.
- Republishing their directory, or claiming morphine is in stock, without their permission. Their entries do not show live stock.
- Apps that assume a smartphone and data. 80%+ of their families are "extremely poor" (/2020/07/community-palliativecare-during-lockdown).

**Best first ask**
- **Who to approach:** Dr Arjun Devarajan or Dr Shani John Sequeira (Karnataka Mission, Jayanagar GH, Bengaluru). Their service is new, they run home care, they have listening-session data on caregiver needs, and Bengaluru is close to us.
- **Through whom:** a warm introduction via Dr Athul Joseph Manuel (OHC CMO and Pallium advisor), so Sahara is framed as CARE's family side.
- **The pilot:** 10–15 consenting families for 8 weeks, using the symptom diary, medicine log and booklet-based goals-of-care worksheet. Before starting, agree which fields map to their CARE questionnaire.
- **Measures:** calls avoided, symptom alerts caught, and caregiver burden before and after, using their own burden inventory.
- **Second ask:** permission and a correction feed for the directory.
- **Timing:** wait out the CEO change before asking for anything that touches the whole organisation.

---

## Page log (URL | read? | one line)

**Their website**
- palliumindia.org/pallium-india | full | trust number, vision, quality policy
- /pallium-india/team | full | 138 staff, 16 home-care teams; OHC CMO on the advisory board
- /pallium-india/genesis | full | history, WHO centre 2012
- /pallium-india/reports | full | Give.do Gold; list of reports
- /our-work | full | outpatient, inpatient, home care, link centres, rehabilitation
- /our-work/community-engagement | full | volunteers; COVID helpline
- /our-work/facilitation | full | pain recorded 0–10 as the "5th vital sign"
- /about-tips | full | TIPS functions
- /contact | full | addresses, helplines, regional facilitators
- /publication | full | publications list
- /2022/04/telehealth-consultation | full | free 4-language telehealth line
- /2022/01/the-year-that-was | full | telehealth growth; 20 research projects
- /2020/04/palliative-care-helpline-and-the-power-of-the-community | full | volunteer helpline
- /2020/07/community-palliativecare-during-lockdown | full | 80% very poor; helpline
- /2024/02/transforming-palliative-care-with-technology | full | praises symptom-tracking apps
- /2025/05/hcl-tech-grant-edition-x | full | digital patient-tracking platform proposed
- /2023/07/july-2023 | partial | chairman says adopt technology; MoveInSync routing
- /2011/09/telemedicine-a-new-endeavour-with-hp-india | full | 2011 EMR/telemedicine MoU
- /2025/12/generating-evidence-in-fostering-palliative-care-delivery | full | caregiver burden results
- /2026/05/report-of-community-listening-session-on-home-based-palliative-care-in-karnataka | full | caregiver needs
- /2026/08/listening-to-the-community | full | Bengaluru launch; caregiver burden
- /2026/07/lead-palliative-care-physician-karnataka | full | EMR skills; goals of care in the job
- /2026/09/ceo-pallium-india-trivandrum | full | CEO search
- /2025/06/amd-legal-right-to-practical-reality | full | AMD as dialogue
- /2025/08/indias-struggle-with-quality-care | full | morphine barriers
- /2026/03/beyond-resilience-why-systems-must-support-women-caregivers | full | women caregivers
- /2026/06/nursing-excellence-in-home-based-palliative-care | full | tele-palliative nursing
- /2026/09/my-experience-at-the-pallium-india-hands-on-module | full | caregiver burden seen on home visits
- /2026/09/caring-for-a-patient-who-is-terminally-ill | full | nursing workshop
- /2026/07/what-i-saw-shadowing-a-palliative-care-team-for-a-day | full | guest post about PalCare, not Pallium; WhatsApp groups
- /2026/05/what-does-it-mean-to-care-for-someone-with-a-serious-illness | full | PalCare (another NGO) via IDR, not Pallium
- /2026/10/care-that-respects-you | full | ACP booklet download page
- /2022/08/grief-can-be-fatal | partial | Unarv bereavement group
- /2025/07/compassionate-care-in-the-hills-of-chamba | partial | documentation training
- /2019/10/a-screening-tool-for-serious-health-related-suffering-please-comment | full | suffering screening tool
- /2026/09/september-2026 | partial | current telehealth numbers
- /donate | partial | donation page; no per-visit price on the page itself
- The other 2,900 posts, pages and directory pages | searched by keyword only | not read individually

**Pallium PDFs**
- Annual-Report-2024-2025-1.pdf | full | key 2024-25 figures
- Pallium-India-Annual-Report-23-24-Final.pdf | searched | 2023-24 figures
- Pallium-India-Annual-Report-22-23-.pdf | searched | 2022-23 figures
- Pallium-India-Annual-Report-21-22.pdf | searched | telehealth trend
- 2026/08/Pallium-India-Trust-DD-report.pdf | searched | CARE platform, visits twice a month
- 2026/10/ACP_booklet_English_ver2025.pdf | partial | text garbled; worksheet headings readable
- Advance-Medical-Directive-Template.pdf (2026) and the 2024 AMD toolkit | downloaded | not reviewed for content

**OHC blog** (saved copies, 23 Oct)
- ohc.network/blog/care-dashboards-testing-new-deployments | full | Pallium onboarding and scale
- /one-million-encounters-paperless-governance | full | 5 Dec 2025 go-live
- /care-hmis-hospital-go-live | full | Jan 2026 full-scale use
- /care-core-as-operating-layer | full | what Pallium needed from CARE
- /why-open-healthcare-needs-a-foundation | full | summary of the rollout
- /plugs-v3-extensible-care-platform | partial | Pallium listed among deployments

**PubMed** (each: full abstract)
- 41239314 | caregiver burden study
- 42185821 | ACP booklet validation
- 40604810 | socioeconomic vulnerability in home care
- 42555446 | PALLIUM framework
- 34511785 | City Homecare quality project
- 42695083 | quality project on palliative referral in oncology
- 30241254 | TIPS service volumes
- 42277812 | ESAS in Kashmir
- 36819806 | quality of services across India
- 32858163 | PC-PAICE quality collaborative
- 41283053 | family caregiving
- 29911969 | home-care pain narratives
- The other 74 Pallium-affiliated titles | titles only

**Other**
- en.wikipedia.org/wiki/Pallium_India | full | overview
- discover.give.do/I26/pallium-india | partial | Gold 2023; financials
- greatplacetowork.in profile | failed to load | only the "1,03,608 beneficiaries" search snippet [unverified]
- biospectrumindia.com Saath Saath article | search snippet only | 1800-202-7777 helpline
- jpalliativecare.com Kerala provider study | search snippet only | provider survey
- Reddit / Quora | nothing found
- github.com/ohcnetwork code search | blocked | no access

Roughly 60 sources were read in full or in part; about 2,900 more site items were searched by keyword.
