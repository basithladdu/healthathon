# India palliative-care evidence: numbers to quote and design requirements

_Research agent report, 27 Oct 2026. Unedited except for this header._

India palliative-care evidence for Sahara (checked 27 Oct 2026)

**Method.** I first checked the repo, so this adds to it rather than repeating it. `symptom-tracker-clinical-brief-2026-10-23.md` already covers the ESAS-r, IPOS, BPI and NCCN instruments, their licences and the 2026 Hindi IPOS validation; I don't repeat that. Abstracts come from the PubMed E-utilities and Europe PMC APIs (read directly). Web figures were fetched with curl; anything I could not read in the original is marked [unverified]. PubMed IDs are given as PMID.

## (1) Ten key findings for Sahara

1. **Need is large, coverage is tiny.**
   - "Nearly 7–10 million people require palliative care (PC) in India, with less than 4% having access to it." (Sharma et al., ecancermedicalscience, Nov 2025, PMID 41694154)
   - An older government-adjacent estimate is 5.4 million a year (Kar, Indian J Cancer 2015, PMID 26837989).
   - A meta-analysis pools need at 6.21 per 1,000 people, and 37.86 per 1,000 among people over 60 (Chandra, IJPC 2023, PMID 38058486).
2. **Hindi-speaking states are the gap, not Kerala.**
   - India had 526 centres in 2022, 4 per 10 million people. The median travel time to the nearest one is 118 minutes, and only 23.6% of people live within 30 minutes.
   - "44.5% of all the PC centres in the country were present in Kerala", where the median travel time is 14 minutes.
   - "Uttar Pradesh, with nearly 17% of the country's population, had only 12 (2.3%) PC centres with 10.9% of the population within 30 minutes" (same ecancer paper, full text).
3. **The burden keeps rising.**
   - Global serious health-related suffering (SHS) rose 74% from 1990 to 2021, to about 73.5 million people. 80% of it is in low- and middle-income countries (LMICs), and 63% is now among people who are *not* dying that year, i.e. people living with illness for a long time (Knaul, Lancet Glob Health 2025, PMID 40021301).
   - The paper says South Asia per-capita SHS declined while "India remained constant". India's country numbers are only in its appendix, which I did not read.
4. **Families pay, mostly in non-medical costs.**
   - Hyderabad, 400 palliative patients: median monthly out-of-pocket cost of ₹4,240, "direct non-medical expenses (transportation and caregiver wage loss) forming the bulk" (PMID 41164715, 2025).
   - Cancer has the highest rate of catastrophic household spending among diseases (79%) and of distress financing (43%) (Kastor, PLoS One 2018, PMID 29746481).
   - Financial toxicity: 92.6% of palliative outpatients at a regional cancer centre (PMID 38061493, 2024); 73.7% of older cancer patients (Tata Memorial, n=953, PMID 40278900, 2025).
5. **Caregiver burden is measurable, and India now has its own short tool.**
   - Kerala: 32.1% moderate-to-severe burden (n=115, BMJ SPC 2026, PMID 39537204); 54.8% moderate burden (Pallium India, n=270, BMC Palliat Care 2025, PMID 41239314).
   - Tata Memorial geriatric clinic: 76.4% little or no burden (n=127, PMID 35750629), so burden varies by setting.
   - The 3-item Brief AMC-CBI uses the items "lost hope", "feel sad" and "financial burden", with a cut-off of ≥3 (IJPC 2026, PMID 42694816).
6. **Families often hide the diagnosis from the patient, and that harms the patient.** The literature calls this "collusion" (non-disclosure).
   - 37% prevalence in Bengaluru, where it was independently associated with poor quality of life (OR 10.53) (Victor, IJPC 2018, PMID 29440809).
   - Global meta-analysis: 24% diagnostic and 38% prognostic collusion (PMID 34562324).
   - At AIIMS, 73% of advanced-cancer patients did not know their stage (APPROACH study, PMID 34477010).
   - Yet 94% of patients in Jaipur wanted to know if they had cancer (PMID 24455553).
7. **People want information but rarely sign documents; families decide together.**
   - Kidney failure: 90.7% of patients want detailed information, but only 9–20% are interested in formal advance-care-plan documents, and "The majority preferred collaborative decision-making involving family and clinicians" (BMC Nephrol 2025, PMID 40707883).
   - 93.6% of physicians agree advance care planning is needed, but only 25% feel they have the time (PMID 42057103, 2026).
   - The Supreme Court simplified living wills in January 2023 (Mani, Simha & Gursahani, PMID 37214121).
8. **Phone and WhatsApp work in India, and there is now Indian trial-level evidence.**
   - A Hindi website, booklet and moderated WhatsApp group lowered caregiver burden (Zarit score 12.26 vs 17.53, p=0.021) and unplanned follow-ups (1.23 vs 2.64). 30-day complications were no different (Tata Memorial unit, Muzaffarpur, randomised pilot, n=75, Indian J Cancer 2026, PMID 42301659).
   - A 10-minute caregiver video in Odia improved patient quality of life and reduced pain, nausea and fatigue (randomised trial, n=180 randomised, 70 completed, PMID 37530959).
9. **Voice calls beat video, and people want to keep remote follow-up.**
   - 84.46% of patients used voice calls; 73.49% were very satisfied; 72.94% would continue (Manipal, PMID 34511801).
   - 87% preferred voice over video "because of the limited internet access" (Kolkata, PMID 41255044, 2025).
   - At Tata Medical Center Kolkata, teleconsultation cut outpatient visits by 51% and admissions by 44%; 76% would pay for it (PMID 33088085).
   - At AIIMS Delhi, 86 of 314 callers needed opioid restocks and 55 needed contacts for opioid access in their home state (PMID 33088083).
10. **Symptom monitoring by patient report has strong efficacy evidence, but from rich countries.**
    - US trials: survival at 1 year 75% vs 69%, and fewer ER visits, 34% vs 41% (Basch, JCO 2016, PMID 26644527). The 2025 PRO-TECT trial found 91.5% weekly survey completion (PMID 39920394).
    - Meta-analysis of 36 RCTs: quality-of-life effect size SMD 0.35 (PMID 40294959).
    - In India, a phone-based ESAS symptom form works: agreement with paper ICC 0.75–0.9. But only 595 of 1,160 patients completed it, about 51% (SMART-ESAS, AIIMS, PMID 38386957).
    - In India, Uganda and Zimbabwe, 149 caregivers uploaded 837 assessments. They asked for "better feedback to caregivers" (Harding, JPSM 2021, PMID 33246073).

## (2) Detailed tables

### A. Figures Sahara can quote

| Claim | Exact figure | Source and year | Confidence / caveat |
|---|---|---|---|
| People in India needing palliative care | "Nearly 7–10 million … less than 4% having access" | Sharma et al., ecancermedicalscience 2025, doi 10.3332/ecancer.2025.2038 | Moderate. It is the paper's background claim; the study itself measured travel time |
| Same, older estimate | "5.4 million people a year" | Kar et al., Indian J Cancer 2015, PMID 26837989 | Moderate, dated |
| Pooled prevalence of need | 6.21 per 1,000; over-60s 37.86 per 1,000; south 10.83 vs north 2.24 | Chandra, IJPC 2023, PMID 38058486 | Moderate. Only 8 studies; low northern figures may reflect under-identification |
| Centres and travel time | 526 centres, 4 per 10 million; median 118 minutes; 23.6% within 30 minutes | ecancer 2025 | High (it measures physical access, not actual use) |
| Kerala vs Uttar Pradesh | Kerala has 44.5% of centres and 2.5% of the population, median 14 minutes. UP has 12 centres (2.3%), ~17% of the population, 10.9% within 30 minutes | ecancer 2025, full text | High |
| India's share of global need | "1/6th, that is one crore, are in India" | Pallium India, Oct 2017, on the Lancet Commission | Low–moderate. Secondary, and differs from the 7M figure |
| Global need | 56.8 million a year; "only about 14% … currently receive it" | WHO fact sheet, 5 Aug 2020 | High |
| Global SHS trend | +74% from 1990 to 2021, to about 73.5 million; LMICs 80%; non-dying 63% | Knaul, Lancet Glob Health 2025, PMID 40021301 | High |
| Projection to 2060 | 48 million deaths with SHS by 2060 (47% of all deaths), +87% | Sleeman, Lancet Glob Health 2019, PMID 31129125 | High (a projection) |
| Opioid access | India's morphine supply met about 4% of palliative need | Lancet Commission 2017 (Knaul, PMID 29032993), via STAT/CBS | [unverified] in the Commission itself; data years 2010–13 |
| Quality of Death Index | India 67th of 80 (EIU 2015); 59th of 81 (Finkelstein, JPSM 2022) | IndiaSpend 21 Oct 2015; Pallium India Jan 2022; PMID 34952169 | High for the ranks. The two indices use different methods; do not present them as "improvement" |
| Cancer cases | 14.6 lakh (2022) projected to 15.7 lakh (2025) | ICMR-NCRP via a Rajya Sabha reply, reported by Deccan Herald | Moderate (projection) |
| Household out-of-pocket cost | Median ₹4,240 a month, mostly transport and lost wages | J Pharm Bioallied Sci 2025, PMID 41164715 | Moderate (one Hyderabad centre) |
| Catastrophic spending, cancer | 79% catastrophic spending, 43% distress financing | Kastor, PLoS One 2018, PMID 29746481 | High (national survey data) |
| Breast cancer | Catastrophic spending 84.2%, distress financing 72.4% | Mohanty, Int J Equity Health 2024, PMID 39044204 | High (Tata cohort) |
| Financial toxicity | 92.6% (palliative outpatients); 73.7% (older patients) | PMID 38061493; PMID 40278900 | Moderate |
| Caregiver burden | 32.1% moderate-to-severe; 54.8% moderate | PMID 39537204; PMID 41239314 | Moderate (Kerala only) |
| Collusion | 37%; linked to poor quality of life (OR 10.53) | Victor, IJPC 2018, PMID 29440809 | Moderate (one centre) |
| Unaware of stage | 73% | Satija, AJHPM 2022, PMID 34477010 | Moderate (AIIMS, n=200) |
| Want to know | 94% want to know if it is cancer; 92% want chance of cure | Laxmi, SAJC 2013, PMID 24455553 | Moderate |
| Preferred place of end-of-life care | 59.69% home, 31.78% hospital | Maurya, AJHPM 2025, PMID 39069375 | Moderate |
| Home deaths achievable | 76% of 514 deaths at home (CanSupport) | Yeager, APM 2016, PMID 27481319 | Moderate (NGO data) |

### B. Digital, phone and WhatsApp evidence (India and other LMICs)

| Study | Design | Result | Implication for Sahara |
|---|---|---|---|
| CCSS, Tata Memorial unit Muzaffarpur, 2026 (PMID 42301659) | Randomised pilot, n=75. Hindi website, booklet, moderated WhatsApp group | Zarit burden 12.26 vs 17.53 (p=0.021); unplanned follow-ups 1.23 vs 2.64; complications no different | Strongest India comparator. WhatsApp plus Hindi is the baseline Sahara must beat |
| VIVid, Odisha, 2023 (PMID 37530959) | Randomised trial; caregiver video in Odia | Better quality of life; less pain, nausea and fatigue | Short vernacular videos for caregivers are evidence-based |
| SMART-ESAS, AIIMS, 2024 (PMID 38386957) | Symptom form sent by SMS link | Agreement with paper ICC 0.75–0.9; ~51% completed both versions | Electronic ESAS is valid, but expect about half to drop off |
| Harding, India/Uganda/Zimbabwe, 2021 (PMID 33246073) | Co-designed caregiver app with clinician dashboard | 837 assessments, viewed 355 times; caregivers wanted feedback | Close the loop: show the family that a clinician saw the entry |
| Adhikari, Manipal, 2021 (PMID 34511801) | Observational, n=547 | 84.46% voice; 63.62% of calls about uncontrolled symptoms; 72.94% would continue | Voice first; symptom questions dominate |
| Biswas, AIIMS Delhi, 2020 (PMID 33088083) | Phone service, n=314 | 54% from other states; 86 needed opioid restocks | Medicine and supply continuity is a core need |
| Atreya, Tata Medical Center Kolkata, 2020 (PMID 33088085) | Survey | Outpatient visits −51%, admissions −44%; 82% positive; 76% willing to pay | Caregivers will pay for remote follow-up |
| Datta, Kolkata, 2025 (PMID 41255044) | Phone psychological support | 87% preferred voice; 67% kept following up | Low bandwidth; audio is fine |
| Malabar Cancer Centre e-Palliative, Kerala, 2022 (PMID 36072246) | Nurse at home with laptop, remote doctor, n=120 | Satisfaction 4.39/5 | A government-run competitor/partner model |
| Kannan, Chennai, 2013 (PMID 23766596) | SMS pain follow-up | 93% responded promptly | Even SMS works |
| Dhiliwal, Tata Memorial, 2015 (PMID 25709195) | WhatsApp case reports | Quick teleconsultation; less needless transport | WhatsApp is already a clinical tool |
| Goodman, Palliat Med 2026 (PMID 42036948) | LMIC telemedicine scoping review, 21 studies | Mostly phone monitoring; barriers: digital literacy, connectivity | Only "emerging" evidence |
| Dilhani 2024 (PMID 38617095) | LMIC systematic review, 15 studies | Barriers: resources, literacy, governance, technical issues | Design for low literacy |
| Kamdar, ePAL, US 2024 (PMID 38866116) | Randomised trial, n=112 | Pain 2.99 vs 4.05; pain admissions 7.1% vs 23.2% | Pain-app efficacy (high-income setting) |
| Basch 2016 (PMID 26644527) and PRO-TECT 2025 (PMID 39920394) | Randomised trials, US | Better quality of life, fewer ER visits, survival signal; 91.5% weekly completion | Cite as international evidence only |
| Narikeldaha Prayas, 2015 (PMID 25960509) | Social-worker calls every two weeks | 76% handled by phone; 24% needed a specialist | Frequency model for follow-up calls |

### C. Symptom tools validated in Indian languages (new items only)

| Tool / language | Evidence | Status |
|---|---|---|
| NCCN Distress Thermometer, Malayalam | Biji, IJPC 2018 (PMID 29440810): face validity 100%, feasibility 90%, n=10 | Weak, small |
| NCCN Distress Thermometer, Kannada | Chaudhary 2024 (PMID 38566653): alpha 0.71–1 | Moderate |
| IPOS, Hindi | Bhardwaj, Palliat Med 2023 (PMID 36719047): includes "A visual thermometer … to facilitate responses from less educated patients" | Already in the repo, plus the 2026 validation |
| IPOS, other Indian languages | The official translations page lists only Hindi (no Malayalam, Tamil, Kannada, Bengali or Marathi) | Gap |
| ESAS, Hindi | No Indian psychometric validation found in PubMed or Europe PMC; only the Ontario form exists | Gap; do not claim it is validated |
| Neuropathic pain inventory (NPSI), Hindi | Saxena, IJPC 2025 (PMID 41342025) | Narrow use |
| AMC-CBI caregiver burden (9-item / 3-item) | PMID 33372853 (alpha 0.82); PMID 42694816 | Kerala-developed; good for a caregiver check-in |
| ESAS change thresholds | Hui, Cancer 2015 (PMID 26059846): ≥1 point means improvement | Canadian/US data |

## (3) Gaps nobody fills that Sahara could

- **Families in north and central India** (UP: 2.3% of centres for ~17% of the population). Kerala tools such as the e-Palliative service and the Kerala Care grid don't reach them.
- **Structured, family-shared symptom records with clinician acknowledgement.** WhatsApp groups (CCSS) have no structure. Harding's caregivers asked for feedback.
- **Goals-of-care support that works with family-centred decisions and collusion,** not against them. Indian evidence shows high information-seeking but low uptake of formal documents.
- **Caregiver burden screening:** the 3-item Brief AMC-CBI is free to adopt subject to permission, and no app uses it [unverified as to licence].
- **Medicine and opioid continuity:** restocks and contacts for opioid access in the patient's home state (AIIMS data).
- **Cost tracking:** non-medical costs (transport, lost wages) dominate, and no app tracks them or connects families to entitlements.
- **Voice/audio input for low-literacy, low-bandwidth users.**

## (4) Threats, competitors, and what not to claim

**Threats and comparators**
- WhatsApp plus a hospital helpline is the real comparator; the CCSS trial already shows it works.
- Malabar Cancer Centre e-Palliative; Kerala Care Palliative Grid (1,043 organisations, 7,765 volunteers per a UNI report [unverified date]); Malabar Care Connect with the Institute of Palliative Medicine (June 2026).
- Tata Memorial's CCSS may scale ("integration with AI-driven personalization are planned").

**Do not claim**
- "Only 1–2% get palliative care": it comes from exam-prep sites. Use "<4% (Pallium/ASAR 2025)".
- India 59th as "an improvement" on 67th: different indices.
- The 4% morphine figure as current: 2010–13 data.
- The WHO 14% figure as an India number: it is global.
- "Validated Hindi ESAS": no Indian validation found.
- IPOS validated in any Indian language other than Hindi.
- That US survival gains (Basch) will repeat in India.
- That Sahara reduces caregiver burden, cost or hospitalisations: no Sahara data exist. CCSS showed lower burden but no difference in complications.
- Telemedicine replacing visits: Mercadante found families consider bedside presence "irreplaceable".
- Disclosure being always good: Ozdemir (PMID 34930691) found prognostic awareness linked to more anxiety and depression unless acceptance is supported.

## Evidence-based design requirements for Sahara

1. **Voice- and audio-first, works at low bandwidth.** Accept voice notes; no video dependency; SMS fallback. (Evidence: Datta, Adhikari, Kannan, Goodman.)
2. **Hindi first, with short vernacular caregiver videos** of about 10 minutes for home-care tasks. (VIVid, CCSS.)
3. **Show the family that a clinician saw it:** "reviewed by" receipts and a clinician-side view. Don't collect data nobody reads. (Harding 2021.)
4. **Weekly structured check-ins rather than daily long forms,** with dropout reminders. (SMART-ESAS ~51% completion; PRO-TECT weekly 91.5%.)
5. **Record who is answering:** patient, assisted, or caregiver proxy. (Collusion and low prognostic awareness mean caregivers often answer.)
6. **Disclosure-sensitive design.** A per-person "what the patient knows" setting; family-visible vs patient-visible content; no push alerts that reveal a diagnosis on a shared phone. (Victor, Harding 2019, Satija.)
7. **Goals-of-care tool that supports family-centred decisions.** It should name a preferred decision-maker or surrogate, record preferred place of care, and be revisited over time. Formal living wills (the 2023 procedure) stay optional. (Naik 2025; Maurya 2025; Damani 2025.) Optional video explainers for advance care planning: video decision aids increased willingness to have a goals-of-care conversation, RR 1.87 (PMID 36348512).
8. **Brief caregiver wellbeing check,** e.g. the Brief AMC-CBI (≥3) once permission is cleared, routing to support; depression screen if the caregiver reports pain. (PMID 42694816; PMID 35138163.)
9. **Medicine continuity:** taken/missed log, low-stock flag, list of verified opioid-dispensing centres by state. (AIIMS Delhi teleconsult data.)
10. **Cost and entitlement helper:** log transport and lost-wage costs; PM-JAY scheme info (only 33.1% of Tata beneficiaries knew of the scheme, PMID 39993628).
11. **Moderated group channel plus structured record:** the CCSS WhatsApp model, but with structure, so it beats plain WhatsApp.
12. **Pictorial or visual scales for low literacy,** e.g. the Hindi IPOS visual thermometer. Validated instruments only, as the repo brief already requires.

## (5) Source log (54)

1. https://doi.org/10.3332/ecancer.2025.2038 | PMID 41694154 | 526 centres, 118 min, Kerala 44.5%, UP 2.3%
2. https://ecancer.org/en/journal/article/2038-access-to-palliative-care-in-india-situational-analysis-and-modeling-of-access-from-public-healthcare-centers | full text, Kerala/UP quotes
3. PMID 26837989 | Kar 2015, 5.4M a year
4. PMID 38058486 | Chandra 2023 meta-analysis, 6.21/1,000
5. PMC11868779 / PMID 40021301 | Knaul 2025 SHS update
6. PMID 31129125 | Sleeman 2019, 2060 projection
7. PMID 32945226 | Sleeman 2021, cancer SHS 2060
8. PMID 29032993 | Lancet Commission 2017 (record only)
9. https://www.who.int/news-room/fact-sheets/detail/palliative-care | 56.8M, 14%
10. https://palliumindia.org/2017/10/a-sixth-of-worlds-sick-needing-palliative-are-in-india-study-finds | one-sixth, one crore
11. https://palliumindia.org/2022/01/quality-of-death-index-2021 | 59th of 81
12. https://www.indiaspend.com/quality-of-death-uk-tops-india-67th-above-china-87613 | 67th of 80
13. PMID 34952169 | Finkelstein 2022 quality of death and dying
14. https://ehospice.com/international_posts/only-4-percent-of-indian-population-has-access-to-palliative-care/ | 4% (2023)
15. https://www.statnews.com/2017/10/13/global-opioid-gap/ | morphine 4% of need [secondary]
16. https://www.deccanherald.com/india/no-of-cancer-cases-in-india-projected-to-go-up-from-146-lakh-in-2022-to-157l-in-2025-govt-1200114.html | ICMR cancer cases
17. PMID 41164715 | Hyderabad out-of-pocket ₹4,240
18. PMID 29746481 | Kastor 2018, catastrophic spending
19. PMID 39044204 | breast cancer catastrophic spending 84.2%
20. PMID 38061493 | financial toxicity 92.6%
21. PMID 40278900 | financial toxicity in older patients 73.7%
22. PMID 40171460 | financial toxicity in palliative care review
23. PMID 39993628 | PM-JAY awareness 33.1%
24. PMID 38715047 | Kochuvilayil 2024, Kerala caregivers
25. PMID 33372853 | AMC-CBI development
26. PMID 42694816 | Brief AMC-CBI
27. PMID 35138163 | depression in women caregivers
28. PMID 39537204 | Shefeek, burden 32.1%
29. PMID 41239314 | Pallium, burden 54.8%
30. PMID 35750629 | Tata geriatric caregiver burden
31. PMID 30717717 | Kerala qualitative study
32. PMID 39371507 | soldiers' wives as caregivers
33. PMID 29440809 | collusion 37%
34. PMID 34562324 | collusion meta-analysis
35. PMID 20606848 | collusion cross-cultural
36. PMID 30837250 | Harding 2019 stigma model
37. PMID 34477010 | APPROACH 73% unaware
38. PMID 24455553 | 94% want to know
39. PMID 34930691 | prognostic awareness and distress (Asia)
40. PMID 40707883 | advance care planning in kidney failure, India
41. PMID 42057103 | physician advance-care-planning barriers
42. PMID 39250897 | nephrology providers 8%
43. PMID 37214121 | 2023 simplified legal procedure
44. PMID 40322227 | 12-step advance care planning pathway
45. PMID 37353428 | Gursahani advance care planning review
46. PMID 25249748 | ISCCM/IAPC end-of-life policy
47. https://www.scconline.com/blog/post/2023/01/25/supreme-court-simplifies-procedure-to-withhold-life-support-of-a-terminally-ill-patient-modifies-guidelines-given-in-2018-euthanasia-judgment-legal-research-legal-news-updates/ | 2023 Supreme Court order
48. PMID 39069375 | end-of-life care preferences (Tata)
49. PMID 27481319 | CanSupport home deaths
50. PMID 42301659 | CCSS WhatsApp randomised pilot
51. PMID 37530959 | VIVid Odia video randomised trial
52. PMID 38386957 | SMART-ESAS
53. PMID 33246073 | Harding mHealth app
54. Phone and telemedicine studies: PMID 34511801 (Manipal), 33088083 (AIIMS), 33088085 (Tata Medical Center Kolkata), 41255044 (Kolkata voice), 36072246 (Malabar e-Palliative), 23766596 (SMS), 25709195 (WhatsApp), 25960509 (phone calls). Reviews and trials: PMID 42036948, 38617095, 38125709, 26644527, 39920394, 40294959, 38866116, 25800762, 27893131, 39305684, 36348512, 29440810, 38566653, 36719047, 26059846, 41342025. Also https://pos-pal.org/maix/ipos-translations.php (Hindi is the only Indian language listed).

PubMed raw dumps are in `(scratch path)` (pm1–pm14.txt), with the helper scripts in `(scratch path)`.
