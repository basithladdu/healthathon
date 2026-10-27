# India palliative, cancer, caregiver and eldercare apps: landscape

_Research agent report, 27 Oct 2026. Unedited except for this header._

## India palliative, cancer, caregiver and eldercare digital products: landscape for Sahara (retrieved 27 Oct 2026)

**Method.** I pulled live Google Play India data (installs band, rating, rating count, last update, description, up to 40 most-relevant reviews per app) for about 60 apps using the `google-play-scraper` Python library, from about 90 Play searches. I also ran about 45 web searches and fetched several sites. The raw JSON is in `(scratch path)` (reviews in `revs.txt`, search hits in `search.txt` and `search2.txt`).

**Limits.**
- The tool's web-search budget ran out near the end. I could not verify Onco Caregiver, CanSupport's current helpline, Sambhal or Bajaj Finserv Health's patient app further.
- sambhal.app returned 406 and arike.care was blocked by the proxy. A retry with browser headers was blocked by the permission classifier, so I stopped there.
- I read the App Store only through search snippets.
- I did not read Reddit or Quora directly, because the earlier repo doc already covers 15 threads.

The repo already had: Saath-Saath, Sukoon, Kehna, Sambhal (naming only), PalliKare and Pallium (in CARE_DEEP_DIVE), and the Hindi IPOS/CanSupport paper. Everything below is new to the repo unless marked.

---

## 1. Key findings for Sahara

1. **No family-facing palliative app in India has any traction.** Every Play India listing with "palliative" in it falls into one of four groups:
   - Clinician references: PalliKare 1,000+ installs, 39 ratings; Orthodose (Belgian), 10,000+.
   - Team or NGO management tools: Palliative App (osperb) 50+; Panha 50+; Pallidex Patient 10+; PalaSh (Experiasoft) 0+.
   - Student prototypes: "Palliative care" by CSD Manipal, 1+ installs, medicine reminders only.
   - Sahara itself: `in.wedevit.sahara`, 1+ installs, released 14 Oct 2026.

   The "first" position is real but undefended. It is also unproven that families will download a palliative-labelled app.
2. **The clinician side is digitising fast.** This is the main structural threat or opportunity:
   - PalaSh (AIIMS Bathinda, Cipla Foundation): "more than 1,200 patients and over 4,000 patient visits" since May 2025.
   - Kerala Care Palliative Grid (June 2025, Kerala Digital University): about 1.58 lakh patients enrolled.
   - Malabar Care Connect (Malabar Group with IPM Kozhikode, June 2026): tele-consult support for patients and caregivers.
   - Pallidex: "connectivity between home care, local care, and hospital care".

   Any of these could add a family module. Sahara's best position is to feed them family-side data, not to replace them.
3. **Venture-backed, digital-only cancer navigation in India has mostly failed or pivoted.**
   - Onco.com raised $13M, then reportedly shut down; Inc42's sources say Apollo acquired it in Dec 2024. A LinkedIn post attributed the failure to "unit economics just don't work with digital-only solutions" (low ARPU).
   - Karkinos was a distress sale to Reliance for ₹375 cr (Dec 2024).
   - ZenOnco's Play listing is now an app for its own "ZenHeal Cancer Hospital — Varanasi / Prayagraj".

   Treat a B2C subscription model as high-risk. Products tied to a hospital or programme survive.
4. **Hospital oncology apps have real usage, and reliability dominates their reviews.**
   - Tata Memorial's EMR-TMH app has 100,000+ installs (4.48 from 939 ratings). Reviews are full of "after update not able to login" (June to Sep 2026) and "file downloaded but no file".
   - TMC Punjab Saarthi: "Always show unable to connect the server".
   - HCG Care: "OTP is not being received".

   OTP and login failure is the single most common complaint across Indian health apps: ABHA, Driefcase, SilverWings, Eka. Sahara's sign-in has to work on a weak signal inside a hospital.
5. **Documents, family profiles and medicine reminders are already commodities, and the government gives them away.** Aarogya Setu 2.0 relaunched on 29 Jun 2026 as a PHR with "medication reminders, and family health profiles". It has 100M+ installs. The ABHA app, Driefcase and Eka each have 10M+.

   Sahara should not pitch "a records locker" or "a pill reminder". Its edge is the goals-of-care record and the palliative symptom vocabulary. An ABHA or ABDM link is a feature to add later, not the product.
6. **The generic reminder leaders show what to avoid.** MyTherapy and Medisafe (5M+ each) draw 1-star reviews for ads inside medication notifications ("ads on the med notification play sound") and for subscriptions that lock old data ("lost access to historical data"). Sahara can lead with no ads, no paywall on a patient's own history, and export.
7. **Eldercare startups sell services, not palliative workflows.** They are channels or future competitors:
   - Emoha / Age Care Labs: about $21M raised plus ₹85 cr in 2026; dementia add-on reported at ₹8,199/month [unverified third-party].
   - Samarth: ₹12 cr in Feb 2024; Gold plan was about ₹7,500–8,000/month in 2024.
   - Khyaal: 5M+ installs, but engagement only (tambola, yoga).
   - Life Circle (shift-based home care) and Anvayaa (a separate dementia-caregiver app, 500+ installs).

   Their buyers are adult children, often NRIs, who are paying for a parent. That is the same "family member at a distance" Sahara targets.
8. **Several names collide with real products, and the repo should know what these actually are.**
   - Kehna (Sai Apps Studio, 1,000+, 3.5★) and Sukoon "Baatein Karo" (Shivaura, 5,000+) are pay-per-minute emotional-listener apps.
   - Sukoon Health is a psychiatric hospital chain. `io.sukoonapp.sukoon`, the link PRODUCT_NAMING.md uses, is an Arabic anxiety companion.
   - Saath-Saath is a phone helpline: 1800-202-7777, Mon–Sat 10–6, 7 languages, 11 founding organisations. I found no app.
   - "Mitr – Care & Connect" (TruMitr) is a listener-earnings app, not CancerMitr.

   None of these is palliative.
9. **Nobody serves goals of care or advance care planning in India.**
   - The only "Living Will" app on Play India is US-made (500+ installs).
   - Registering an Advance Medical Directive is described as "tedious and nearly impossible".
   - A digital AMD registry exists only as a LiveLaw reform proposal.

   This is Sahara's clearest whitespace. It is also legally sensitive (see section 4).
10. **Hindi or local-language support is rare but valued, and evidence for caregiver-reported outcomes exists.**
    - AIIMS Delhi's "Cancer Care" app (Nutricare) says "Fully localized in English and Hindi", but has only 100+ installs.
    - Indian Cancer Society's Rise Against Cancer (10,000+) uses audio-video for "Illiterate as well as literate" users.
    - An AarogyaOne reviewer wrote "Gujarati language is helping a lot".
    - A 2021 JPSM codesign study in India, Uganda and Zimbabwe had 149 caregivers upload 837 weekly PRO assessments and found it "acceptable". Caregivers asked for better feedback and training. That supports Sahara's caregiver-entered model.

---

## 2. Detailed tables

Play data is Google Play India, 27 Oct 2026. "Inst" is Play's install band and ★ is the rating with rating count. Review themes come from the 40 most-relevant English reviews.

### A. Palliative-specific (India or available in India)

| Product (owner) | What / who for | Lang | Inst / ★ | Review themes | Model / funding | Palliative? | Overlap with Sahara |
|---|---|---|---|---|---|---|---|
| PalliKare (Karunashraya/BHT) | Symptom reference: "Ready reference for doctors with limited Palliative Care experience", 8 common symptoms, India drug costs | EN | 1,000+; 4.9 (39); updated Mar 2026 | 2018-era praise ("Concise and correct information relevant to Indian set-up"); "too technical for laymen"; permissions complaint | Free NGO | Yes (clinician) | Low; possible content partner for symptom guidance |
| PalaSh (AIIMS Bathinda, Dr Mayank Gupta; Cipla Foundation) | Team platform: ESAS, SPICT-4ALL, PHQ, EORTC QLQ-C15-PAL, triage, geo-tagged home visits, bereavement alerts, Aadhaar-linkable ID | n/s | Not public on Play (an Experiasoft "PalaSh" listing has 0+) | A commenter asks "How can we get this app. Is it open source?" | Grant-funded; open to institutions | Yes (team) | High complement: Sahara's family diary could feed it. Threat if it adds a family view |
| Kerala Care Palliative Grid (Govt of Kerala / Digital University) | State registry, home-visit planning, volunteer portal (Sannadha Sena) | ML | No public app found | n/a | Government | Yes (system) | Integration target; Kerala families |
| Malabar Care Connect (Malabar Group + IPM Kozhikode) | AV helpdesk, teleconsult for patients and caregivers, e-learning | n/s | No app found | n/a | CSR | Yes | Partner or channel |
| Palliative App (osperb / ziyaq) | NGO software: "patient records, home visits, equipment tracking, medicine supply, and volunteer coordination" (Kerala) | EN | 50+ | none | B2B SaaS, demo requests | Yes (team) | Low; NGO back office |
| Panha Palliative Care (Panha, Jamnalal Bajaj-linked package `com.jmbaxi`) | Patient sign-up and dashboards with a provider | EN | 50+ (Apr 2024) | none | n/s | Yes | Medium; tiny |
| Pallidex Patient (Pallidex) | Vitals, food and water logs, family profiles, linked "Pallidex hospitals" | EN | 10+ | none | n/s | Partly | Medium; early |
| Palliative care (CSD Manipal) | Medicine reminders only | EN | 1+ (Sep 2026) | none | Student / academic | Label only | Low |
| Saath-Saath helpline (Cipla Palliative Care + 10 organisations incl. IAPC, CanSupport, TMH, Karunashraya, Pallium) | Connects callers to the nearest palliative centre | HI, EN, KN, ML, TE, AS, MR | Phone only; "over 15,000 callers" (Cipla Foundation, via search summary) | n/a | CSR / NGO | Yes | Sahara can surface the number; it is not a competitor |
| CanSupport (Delhi) | 29 home-care teams, 2,200 patients at a time, free; staff app "Seva at Home" (2020) | HI | Staff tool, not public | n/a | NGO, donations | Yes | Pilot partner (Hindi IPOS cohort) |
| Pallium India | Helplines and telehealth; Sukh-Dukh bereavement line 759 4052 605; links to Caregiver Saathi | ML, EN | No patient app | n/a | NGO | Yes | Partner; directory source |
| Orthodose (MedLight, Belgium) | Opioid conversion and rescue-dose calculator for doctors | EN | 10,000+ | none in sample | Paid IAP | Yes (clinician) | None |
| palliMEDS (Australia) | Medicine reference | EN | 10,000+ | n/a | Free | Yes (clinician) | None |

### B. Cancer navigation, cancer-hospital and cancer-support apps

| Product (owner) | What / who | Lang | Inst / ★ | Review themes | Model / funding | Palliative? | Overlap |
|---|---|---|---|---|---|---|---|
| EMR-TMH & ACTREC (Tata Memorial) | Appointments, prescriptions, reports, wallet top-up | EN | 100,000+; 4.48 (939) | Login broken after the June 2026 update; downloads fail; "appointment timing not showing"; also praise: "relief for patient and attendant as all reports are available" | Public hospital (TCS-built per a review) | No | Medium: families already carry reports here |
| TMC Punjab Saarthi (HBCH&RC) | Same, for Punjab | EN | 10,000+; 3.86 (70) | "unable to connect the server", reports lag "a day or two" | Public | No | Medium |
| HCG Care (HCG) | Appointments, reports, medicine orders | EN | 50,000+; 4.75 (1,634) | Bugs, OTP not received, discharge summaries missing; praise for doctors | Hospital chain | No | Medium |
| OncoBuddy (4baseCare) | AI-organised records, "consolidated case summary PDF", symptom tracking, caregiver access, counselling | EN | 5,000+; 4.98 (146) | Uniformly 5★, staff-style ("the all staff listened well"), service-tied | Genomics company: Series A $6M (Aug 2024), about ₹128 cr (Jun 2026) | No | **High** (records, symptoms, caregiver). Biased toward genomic-test patients |
| ZenOnco Cancer Care (ZenHeal Wellness) | Now: requests for ZenHeal hospitals in Varanasi/Prayagraj, reports, reminders, "Let a caregiver phone view" | EN | 10,000+; 4.06 (97) | 2022 diet-tip praise; "doctor didn't show up… customer care never connects" | $1.4M seed (Nov 2021); pivoted to hospitals | No (integrative) | Medium |
| Onco.com | Oncologist network, second opinions | EN | (app gone) | n/a | $13M raised; reported shut, then acquired by Apollo Dec 2024 | No | Cautionary tale |
| Navya (TMC / NCG) | Online expert opinion; "over 100,000 people from 75 countries" (claimed on 1mg's partner page) | EN | Web, via 1mg / MediBuddy | n/a | Fee per opinion | No | Low |
| CancerMitr (Mumbai, Mansi Mehta) | Web: "Smart Card", symptom-tracker "CanTrack", consults, home care | EN | No Play app found | n/a | Small startup (founded 2021; directory lists 1 employee [unverified]) | No | Low–medium |
| Karkinos / Karemitra (now Reliance) | Cancer risk assessment, navigators | EN | 1,000+ / 5,000+ | none | Distress sale, ₹375 cr | No | Low |
| Rise Against Cancer (Indian Cancer Society) | Multilingual audio-video information, webinars, for literate and illiterate users | Multi | 10,000+; 4.3 (50) | Informative; "app not working" (Dec 2025); Hindi review | NGO, free | No | Low; content partner |
| Cancer Care (AIIMS New Delhi, Nutricare) | Diet, vitals, medicine dashboard, summaries | EN + HI | 100+ (Jan 2026) | none | Academic | Supportive | **High** feature overlap, but no traction |
| Onco Care (JSS Medical College) | Chemo scheduling, side effects | EN | 100+; 5 (6) | none | Academic | No | Medium |
| Onco Care Giver (Santric) | Caregiver information and resources | EN | 50+ | none | n/s [unverified] | No | Medium |
| OncoDiary | Symptoms, caregiver invites with choice of "exactly what" they see, doctor prep; tied to a book by Ellie Urban | EN | 1,000+ | none | Paid IAP; origin appears non-Indian [unverified] | No | **High** feature overlap (family sharing) |
| Mango Cancer Care Foundation | Loans and financial assistance for immunotherapy | EN | 100+ | none | Non-profit | No | Low; financial-help directory item |
| Cancer Healer Center (Dr Tarang Krishna) | 27-centre integrative "immunology-led" clinic chain | — | No app found | n/a | Clinics; marketing claims unvalidated | No | None; avoid association |
| SeekMed | Second opinions, medical travel | EN | 500,000+; 4.39 | Payment and technical misses; praise for doctors | Fee | No | Low |

### C. ABDM / PHR / pharmacy apps

| Product | What | Inst / ★ | Review themes | Model / funding | Overlap |
|---|---|---|---|---|---|
| AarogyaSetu 2.0 (NHA) | ABHA, records via DigiLocker, PM-JAY wallet, reminders, family profiles (relaunched 29 Jun 2026) | 100M+; 3.97 (1.66M) | "phone is rooted or emulator" false positives, crashes, DigiLocker sync glitches, no delete for a wrong report | Government | **High** on records and reminders; integration target |
| ABHA (NHA) | ABHA address, consented record linking, hospital Scan & Share tokens | 10M+; 4.51 (483k) | Queue tokens, rooted-phone errors, switch-account bug | Government; PIB: about 93 crore ABHA accounts | Integration |
| Driefcase | First NHA-approved PHR; OCR cataloguing | 10M+; 4.46 (621k) | OTP lockouts ("try after 12hrs"), ads, wrong name on token | Self-funded; premium, scan-at-home, doctor software | Medium |
| Eka Care | ABHA, records, AI bot, doctor EMR | 10M+; 4.38 (91k) | Can't delete account; "Doctor made this account without explicit concent"; premium paywall; misreads labs | $15M Series A (2022) | Medium; consent cautionary tale |
| Health-e | PHR plus lab tests | 1M+; 4.87 | Wrong report given; heavy permissions; ad-supported | Labs | Low |
| AarogyaOne (Jdeanz) | PHR, vitals, Gujarati | 500k+; 4.40 | Language praised; camera records unreadable | n/s | Low |
| Ayushman App (NHA) | PM-JAY card | 10M+; 3.74 | n/a | Government | Financial-help link |
| Tata 1mg / Apollo 24\|7 / Max MyHealth | Pharmacy, consults, reminders | 10M+ / 10M+ / 1M+ | Delivery failures and spam calls (1mg); payment bugs (Max) | Commerce | Low (refill links only) |
| MyTherapy / Medisafe | Generic reminders, Medfriend caregiver alerts (Medisafe) | 5M+ each; 4.69 / 4.10 | Ads in notifications; paywall locks history | Freemium | Medium; set the UX bar |

Bajaj Finserv Health's patient app: the scraper returned no app ID, so its install count is unverified. Its doctor app has 100,000+ installs (3.73).

### D. Elder-care and caregiver startups

| Product | What | Inst / ★ | Themes | Model / funding | Palliative? | Overlap |
|---|---|---|---|---|---|---|
| Emoha (Age Care Labs) | 24/7 emergency, care, events; separate Carer and Responder apps | 100k+; 4.16 (481) | Emergency button valued; "parents live alone"; poor stream audio | Membership (Engage ₹399 / Empower ₹4,999 / Enhance ₹11,999, period not stated [unverified]); about $21M plus ₹85 cr (2026) | No | Medium; channel |
| Samarth Care / Community | Care managers in "95 cities", updates to children abroad | 1k+ (4.56) / 5k+ (4.93) | "App hangs frequently", "poorly designed and buggy" | ₹12 cr (Feb 2024, Aroa); about ₹7.5–15k/month (2024 offer) | No | Medium; channel |
| Khyaal | Senior engagement club | 5M+; 4.10 (2,225) | Tambola, yoga, community | $4.2M seed (2024) | No | Low |
| SilverWings (SeniorWorld) | Senior club | 100k+; 3.98 | OTP broken (Jul–Aug 2026) | n/s | No | Low |
| Life Circle | Subscription home health workers in 5 cities | 1k+; 4.72 | Caregiver praise by name | $150k (Groupe SOS); about ₹3 cr DBS grant (2025) | No | Medium; home-care referral |
| Anvayaa / Dementia Care – Anvayaa | NRI-parent care; dementia activities and reminders | 1k+ / 500+ (4.43, 7) | n/a | About $340k grant/angel [unverified] | No | Medium (caregiver) |
| Medibees, CareOff, Careait, FamCare, Care24, Portea | Book caretakers or nurses | 1k–50k | Booking-service praise | Services; Portea FY25 revenue ₹160 cr; Portea is listed as offering palliative care | No (Portea partly) | Low; referral |
| Dozee Home | Contactless vitals monitoring | 10k+; 4.49 | Vitals of parents remotely; regressions after updates | Device | No | Low |
| Caregiver Saathi (Mumbai) | Caregiver community, e-learning, counselling | Web | n/a | NGO (2023 income about ₹20.7 lakh) | Partly | Medium; partner |
| Elderline 14567 (MoSJE) | Senior helpline, 8am–8pm, "over 29 lakh calls" | Phone | n/a | Government | No | Surface the number |

---

## 3. Gaps nobody fills that Sahara could

1. **A family-side palliative symptom diary in Hindi** that a palliative team can read. Every comparable tool (PalaSh, Kerala Grid, Palliative App, Panha) is team-side. The India/Uganda/Zimbabwe PRO-app study shows caregivers will submit and asked for feedback in return.
2. **A goals-of-care conversation with versioned, family-confirmed records.** I found zero Indian products. The nearest are a US living-will app and paper AMD forms.
3. **One handover summary across hospital apps.** Families juggle TMH, HCG and ABHA logins that break. OncoBuddy's "consolidated case summary PDF" exists only for 4baseCare's own customers.
4. **Reminders with no ads, no paywall on history, and taken/missed visible to the whole family.** The leading apps alienate users on exactly these points.
5. **Links to palliative services inside the app.** Saath-Saath, Sukh-Dukh, Elderline 14567, CanSupport and Karunashraya are phone-only or web-only, with no app gateway.
6. **Bereavement follow-up for families.** PalaSh has clinician "bereavement follow-up" alerts. Nothing serves the family.
7. **Low-literacy and voice-first use.** Only ICS does audio-video. No palliative app does.

## 4. Threats, competitors and what not to claim

**Competitors and threats**
- **Strongest feature overlap:** OncoBuddy (well funded, caregiver access, symptom tracking, case-summary PDF), OncoDiary (fine-grained family sharing) and AIIMS Cancer Care (Hindi/English).
- **Platform threat:** Aarogya Setu 2.0, with government distribution, already has family profiles and reminders.
- **Institutional threat:** PalaSh, Kerala Grid or Malabar Care Connect could add a caregiver front-end.
- **Business-model risk:** Onco.com, Karkinos and ZenOnco show that digital-only cancer B2C did not sustain. Plan on hospital, NGO or CSR buyers. Cipla Foundation funds PalaSh, so it is a plausible funder.

**What not to claim**
- Not "the first palliative app in India". PalliKare dates to 2018, plus Panha (2024), PalaSh and others. Say "family-side" and qualify it with "as far as we found".
- Not that Sahara's goals-of-care note is a legally valid AMD or living will. The Supreme Court framework requires the 2023 process.
- Not ABDM or ABHA integration until it is built and approved.
- Not validated symptom measurement. The Hindi IPOS evidence is interviewer-administered, as the repo already notes.
- Not any affiliation with Saath-Saath, Cipla, Pallium or CanSupport without a written agreement.
- Not "trusted by" or install counts. Sahara has 1+ installs.
- Avoid the Sukoon, Kehna and Saath names, all taken (already decided in PRODUCT_NAMING.md).
- Do not associate with Cancer Healer Center: its "no side effects" marketing claims are unverified.

## 5. Source log (URL | one line)

**Play India listings, retrieved 27 Oct 2026 via google-play-scraper; all at `https://play.google.com/store/apps/details?id=<id>`**
- `org.karunashraya.pallikare` | PalliKare, 1,000+, 4.9 (39), "Ready reference for doctors…"
- `com.manipal.palliativecare` | CSD Manipal reminders, 1+
- `com.osperb.palliative` | Palliative App (osperb), team software, 50+
- `com.jmbaxi.panha` | Panha Palliative Care, 50+
- `com.pallidex.patient` | Pallidex Patient, 10+
- `in.wedevit.sahara` | Sahara, 1+, released 14 Oct 2026
- `com.orthodose.orthodose` | Orthodose, 10,000+
- `com.cancercare.android` | AIIMS Cancer Care, "Fully localized in English and Hindi"
- `com.zenonco.cancer` | ZenOnco, now ZenHeal hospitals
- `com.ics.riseagainstcancer` | ICS multilingual, 10,000+
- `com.oncobuddy.app` | OncoBuddy, 5,000+, 4.98
- `com.mangosciences.vbc` | Mango Cancer Care loans
- `com.hcg.patient` | HCG Care, 50,000+
- `com.actrec.emedicalrecords.emedicalrecords` | TMH EMR, 100,000+, login complaints
- `com.actrec.campusnavigator` | ACTREC navigation
- `com.hbchrcm` | TMC Punjab Saarthi, server complaints
- `io.ionic.oncocare` | JSS Onco Care
- `com.wnapp.id1739258387753` | Onco Care Giver, 50+
- `com.oncodiary.app` | OncoDiary, family sharing
- `com.karkinos` and `com.karemitra` | Karkinos risk apps
- `com.tm.guide.app` | TruMitr listener app (not CancerMitr)
- `com.emoha` | Emoha, 100k+
- `com.samarth.care` and `com.community.samarth` | Samarth apps
- `com.eldercare.anvayaa` and `com.dementiacare.anvayaa` | Anvayaa apps
- `com.khyaal.senior` | Khyaal, 5M+
- `in.lifecircle.lifecircle` | Life Circle, 5 cities
- `com.medibees.userapp`, `com.careoff.android.seeker`, `com.famcare.praja`, `com.careallianz.app` | caretaker booking apps
- `com.seniorworld.silverwings` | SilverWings, OTP complaints
- `com.gens.life` | Gen S Life
- `me.dozee.dozee` | Dozee Home
- `com.trysukoon` | Sukoon listener app (Hinglish)
- `com.sukoonhealth.patientapp` | Sukoon psychiatric chain
- `io.sukoonapp.sukoon` | Arabic Sukoon
- `app.kehna.mobile` | Kehna listener app
- `nic.goi.aarogyasetu` | AarogyaSetu 2.0, 100M+
- `in.ndhm.phr` | ABHA, 10M+
- `apps.driefcase.com` | Driefcase, 10M+
- `eka.care` | Eka, 10M+, consent complaint
- `com.healthe.app` | Health-e
- `com.jdeanzhealthtech.aarogyaone` | AarogyaOne, Gujarati praised
- `com.beneficiaryapp` | Ayushman App
- `com.drx` | Bajaj Health doctor app
- `eu.smartpatient.mytherapy` | MyTherapy, ads-in-notification complaints
- `com.medisafe.android.client` | Medisafe, paywall complaints
- `com.aranoah.healthkart.plus` | Tata 1mg, has reminders
- `com.apollo.patientapp`, `com.maxhealthcare` | hospital and pharmacy apps
- `com.omega_r.healthcare` | SeekMed
- `com.mylivingwillapp` | US Living Will app, 500+
- `com.saanvicharity.canceraware` | Cancer Sathi awareness
- `com.lifebridge.seniorshield` | KITES seniorshield

**Web**
- https://ehospice.com/india_posts/palash-the-palliative-care-app-for-india/ | PalaSh features; 1,200 patients since May 2025
- https://palliumindia.org/2021/10/saath-saath-palliativecare-helpline | Saath-Saath: 11 organisations, 7 languages, 1800-202-7777
- https://www.palliativecare.in/saath-saath-helpline-launched-by-consortium-of-indian-palliative-care-organisations/ | IAPC launch note
- https://thelogicalindian.com/?p=129966 | Cipla Foundation: helpline "over 15,000 callers"
- https://biospectrumindia.com/news/98/19721/palliative-care-organisations-launch-saath-saath-helpline.html | Launch coverage
- https://www.thenewsminute.com/kerala/sukh-dukh-helpline-families-who-lost-their-loved-ones-covid-19-147933 | Pallium Sukh-Dukh line
- https://apm.amegroups.org/article/view/11024 | CanSupport home-care model
- https://aphn.org/member-spotlight-cansupport/ | CanSupport: 29 teams, 2,200 patients
- https://palliativecareworks.org/Conference/SpeakerPresentations/CanSupport in the Time of COVID-19.pdf | CanSupport staff digital app
- https://give.do/discover/3JP/cansupport/ | CanSupport helpline call count
- https://palliumindia.org/2024/02/transforming-palliative-care-with-technology | Pallium names PalliKare / Palliative / palliMEDS
- https://hospicecare.com/what-we-do/publications/newsletter/2021/02/app-review | IAHPC review of PalliKare
- https://apps.apple.com/app/id1423074474 | PalliKare iOS listing
- https://yourstory.com/socialstory/2021/09/bengaluru-karunashraya-palliative-care-institution-cancer-patients | Karunashraya scale
- https://newskarnataka.com/india/kerala-launches-tech-driven-palliative-care-grid-to-support-ageing-population/30062025/ | Kerala grid launch
- https://test.uniindia.com/all-bedridden-patients-in-kerala-to-get-proper-care-govt-plans-palliative-care-project/south/news/3498423.html | Kerala grid numbers
- https://test.uniindia.com/who-praises-kerala-s-palliative-care-model-as-exceptional-and-inspiring/south/news/3503618.html | 2,400 units, 1.5 lakh patients
- https://www.onmanorama.com/news/kerala/2025/06/06/kerala-palliative-care-volunteer-program.html | Volunteer portal
- https://www.indianpharmapost.com/healthcare/lite/malabar-group-partners-with-institute-of-palliative-medicine-to-launch-malabar-care-connect-20572 | Malabar Care Connect
- https://www.digitalhealthnews.com/malabar-group-ipm-launch-digital-platform-to-expand-palliative-care-access | Same
- https://palliativecareapp.ziyaq.in/ | osperb Palliative App site
- https://www.pallidex.com/ | Pallidex meta description
- https://kehna.app/ | Kehna: pay-per-minute listeners
- https://www.cancermitr.com/ | CancerMitr site (Smart Card, CanTrack)
- https://industrialautomationindia.in/women-special/mansi-mehta-founder-ceo-cancermitr-transforming-cancer-care | CancerMitr founder claims
- https://oncodiary.com/ | OncoDiary (book, journal, app)
- https://www.thenewsminute.com/atom/cancer-care-platform-oncocom-raises-7-million-series-funding-109626 | Onco.com $7M Series A
- https://www.digitalhealthnews.com/cancer-care-startup-onco-shuts-down | Onco.com shutdown report
- https://inc42.com/?p=522010 | Apollo acquired Onco.com (Dec 2024)
- https://www.cxodigitalpulse.com/?p=33152 | Same
- https://www.dailypioneer.com/2024/business/reliance-acquires-karkinos-healthcare-for-rs-375-cr.html | Karkinos to Reliance
- https://the-ken.com/story/why-reliance-bought-tata-backed-karkinos-for-rs-375-crore/ | "distress sale"
- https://biospectrumindia.com/news/20/20072/oncology-innovator-zenonco-io-raises-1-4-m-in-seed-funding.html | ZenOnco seed
- https://india.entrepreneur.com/news-and-trends/4basecare-raises-usd-6-mn-in-series-a-round-led-by-yali/478344 | 4baseCare Series A
- https://indiamedtoday.com/infosys-backed-4basecare-raises-%E2%82%B9128-crore-plans-to-take-its-cancer-testing-platform-global/ | 4baseCare ₹128 cr
- https://www.1mg.com/cancer-care/Get-Second-Opinion | Navya via 1mg
- https://biovoicenews.com/navya-care-offers-online-expert-opinion-services-for-cancer-patients/ | Navya
- https://mhealth.medicaldialogues.in/article/latest-news/cancer-healer-center-strengthens-access-to-immunology-led-cancer-care-with-its-27th-center-in-nashik-169633/amp | Cancer Healer 27th centre
- https://deccanherald.com/india/karnataka/bengaluru/indian-cancer-society-launches-awareness-mobile-app-2881251 | ICS app launch
- https://yourstory.com/2026/07/age-care-labs-raises-85-crore-to-expand-elder-care-platform | Emoha / Age Care Labs ₹85 cr
- https://inc42.com/company/emoha-elder-care/funding/ | Emoha totals
- https://voice.lapaas.com/startup/emoha-eldercare/business-model/ | Emoha plan prices (third-party)
- https://entrackr.com/2024/02/elder-care-startup-samarth-life-raises-new-round-led-by-aroa-venture/ | Samarth ₹12 cr
- https://www.icici.bank.in/offers/get-exclusive-discount-on-various-plans | Samarth 2024 prices
- https://frontiertech.niti.gov.in/story/facilitating-aging-with-grace-how-a-tech-platform-is-supporting-senior-citizens-in-indian-cities/ | NITI on Samarth
- https://inc42.com/?p=441318 | Khyaal $4.2M
- https://www.biospectrumindia.com/news/86/26249/life-circle-health-services-receives-grant-worth-rs-3-cr-to-scale-tech-enabled-elder-care-.html | Life Circle grant
- https://bwdisrupt.com/article/startup-life-circle-raises-150k-from-groupe-sos-europe%E2%80%99s-leading-senior-healthcare-provider-97948 | Life Circle $150k
- https://www.caplight.com/company/anvayaa | Anvayaa funding (aggregator)
- https://entrackr.com/fintrackr/portea-posts-rs-160-cr-revenue-in-fy25-narrows-losses-11003816 | Portea FY25
- https://palliumindia.org/2020/04/link-to-caregiversaathi | Caregiver Saathi
- https://give.do/discover/18PI/caregiver-saathi-foundation/ | Caregiver Saathi financials
- https://pib.gov.in/PressReleasePage.aspx?PRID=1758880 | Elderline 14567
- https://superkalam.com/current-affairs/24-09-2026/elderline-14567-provides-information-guidance-and-support-to-senior-citizens-across-india-launched-on-1st-october-2021-1d4ac0e4-602a-468e-95e6-e77421bbc43f | 29 lakh calls
- https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/jul/doc202676912801.pdf | PIB on Aarogya Setu 2.0 and ABHA counts
- https://www.angelone.in/news/government-schemes/aarogya-setu-2-0-launch-new-digital-health-platform-under-ayushman-bharat-digital-mission | Aarogya Setu 2.0 features
- https://www.drishtiias.com/state-pcs-current-affairs/aarogya-setu-20-launched-as-national-health-app/print_manually | Launch summary
- https://india.entrepreneur.com/news-and-trends/eka-care-raises-15-million-in-series-a-led-by-hummingbird/431692 | Eka Series A
- https://www.biospectrumindia.com/news/98/20503/nha-approves-driefcases-phr-app-for-abdm-roll-out.html | Driefcase first PHR
- https://biovoicenews.com/driefcase-achieves-a-new-milestone-by-acquiring-3-million-users/ | Driefcase users
- https://www.india.gov.in/website-ayushman-bharat-digital-mission?page=5 | ABDM PHR partners
- https://www.livelaw.in/amp/articles/passive-euthanasia-in-india-and-procedural-difficulties-295988 | Digital AMD registry proposal
- https://openthemagazine.com/feature/final-choice | AMD registration "tedious"
- https://www.scconline.com/blog/post/2025/05/29/the-legal-journey-of-living-wills-in-india/ | Living-will law
- https://www.medboundtimes.com/amp/story/medbound-blog/hinduja-first-living-will-clinic | Hinduja living-will clinic
- https://researcher.manipal.edu/en/publications/enhancing-advance-care-planning-in-india-through-a-12-step-pathwa/ | ACP 12-step pathway
- https://nru.uncst.go.ug/handle/123456789/11910 | JPSM 2021 caregiver PRO app (India/Uganda/Zimbabwe), 149 caregivers, 837 assessments
- https://cicelysaundersinternational.org/?p=1913 | Same project
- https://mhealth.jmir.org/2026/1/e73483/PDF | mPallCare 2026 follow-on
- https://jpalliativecare.com/smartphone-applications-in-palliative-homecare/ | IJPC TMH note on home-care apps
- https://busqueda.bvsalud.org/portal/resource/es/mdl-26962291 | IJPC 2016 app review (Jamwal & Kumar)
- https://doaj.org/article/9194d9af3f3a409097c631e5a0b4c3e3 | JMIR 2020 scoping review on family in palliative mHealth
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12904676/ | Access study: under 4% access; 526 centres
- https://newsreel.asia/articles/96-percent-indians-no-access-palliative-care-cancer | Rajagopal on the "access abyss"
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6145572/ | Indian caregivers and app readiness (mental health)
- https://citizenmatters.in/where-to-seek-palliative-care-in-mumbai-and-why/ | TMH home care is in person

**Confidence.** High for Play figures (live scrape) and launch facts with primary sources. Moderate for funding totals, where aggregators conflict. Low or unverified for Emoha prices, CancerMitr scale, OncoDiary's origin and Sambhal, which I could not open.
