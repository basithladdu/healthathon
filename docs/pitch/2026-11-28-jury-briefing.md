# Jury briefing: Health-a-thon 2026 Grand Finale, Cancer Care track

Team Zeros and Ones, pitching **Sahara**. Saturday 28 Nov 2026, IIT Bombay (KCDH + Koita Foundation; OpenAI technology partner, Sarvam AI partner; National Cancer Grid collaboration). 10 minutes, closed room, other teams watching. Prepared 23 Nov 2026.

**Confidence key:** High = an official or primary page confirms name and role. Medium = a single or secondary source. Low = inferred.

---

## 1. The jury

### 1.1 Dr Harit Kumar Chaturvedi (spelling: one "h": Chaturvedi). Confidence: high
- **Who:** Surgical oncologist (MCh Surgical Oncology). Since April 2026 he has been **CEO and Clinical Head of the Apollo Oncology Network**, Apollo Hospitals Enterprise ([BioSpectrum, 20 Apr 2026](https://biospectrumindia.com/news/16/27687/apollo-hospitals-strengthens-oncology-leadership-with-appointment-of-dr-harit-kumar-chaturvedi.html); [Apollo profile](https://www.apollohospitals.com/doctors/surgical-oncologist/delhi/dr-harit-kumar-chaturvedi)). He was long the chairman of Max Institute of Cancer Care, and Max pages still list him there ([Max leadership](https://www.maxhealthcare.in/leadership/dr-harit-chaturvedi)). Both the WhatsApp message (Apollo) and the Max guess were partly right: **Apollo is his current role**. Call him "Dr Chaturvedi, Apollo Oncology Network".
- **Cares about:** running cancer care across many centres with one standard of quality; throughput and outcomes; what changes for the oncologist in a busy OPD.
- **Likely probes:** "Who in my OPD has time for a goals-of-care talk?" "How does this connect to our EMR?" "Where does palliative care sit next to active treatment?"
- **Pitch to him:** Present Sahara as *early, concurrent* palliative care that sits beside oncology. The doctor's work is a signed note and an emergency card, with no new system to log into. Show the doctor screen, not the patient screen.

### 1.2 Dr Joy John Mammen, CMC Vellore ("Joy Manin" = Joy Mammen). Confidence: medium-high
- **Who:** Senior Professor, Immunohaematology and Blood Transfusion, CMC Vellore ([CMC faculty page](https://www.cmch-vellore.edu/dr-joy-john-mammen/)). He is also listed as **Head of the CMC Biomedical Informatics Unit** ([CAHO DPDP Act webinar flyer, Feb 2026](https://www.caho.in/files/CAHO%20DPDP%20Act%20Webinar%20(4).pdf)), and in that webinar he spoke on the **DPDP Act**. Not an oncologist; a health-data, informatics and governance person.
- **Important context:** a consortium **led by CMC Vellore** is building **BandhuCare**, a multilingual AI companion for cancer patients that records symptoms through conversation. It is supported by the **National Cancer Grid** and IndiaAI (MeitY), has reached TRL 5 and is moving to a head-and-neck pilot ([BioSpectrum, 14 Jul 2026](https://biospectrumindia.com/news/102/28121/cmc-vellore-iiit-h-develop-multilingual-ai-companion-for-cancer-patients.html)). We have not confirmed that he is personally on that project. Assume he knows it.
- **Cares about:** data governance, consent, where data lives, interoperability, and evidence before scale.
- **Likely probes:** DPDP fiduciary duties, consent withdrawal, server region, who can see the data, ABDM/FHIR, "how is this different from BandhuCare?"
- **Pitch to him:** Be exact and modest on data. Say what is on the phone, what goes to the server, what goes to Sarvam or Gemini, and only with which consent tick. Present BandhuCare as **complementary**: they capture symptoms by conversation; we handle the goals-of-care note, the emergency card and the family. **Never criticise it.**

### 1.3 "Mr Kil Sakenna", AIG Hospitals = probably **Kinjal Saxena, CTO, AIG Hospitals**. Confidence: medium
- **Who:** An AIG Hospitals event flyer lists "Kinjal Saxena, CTO, AIG Hospitals" as speaker and moderator of the "AI @ AIG" session ([AIG flyer PDF](https://aighospitals.com/uploads/images/17549055666899bbde6f51f.pdf)). A second-hand report gives the title as Chief Technology and Product Officer; we could not confirm that wording. **Gender not confirmed: avoid "Mr" until it is.** Another AIG AI leader is Dr Rakesh Kalapala, Director of AIG's Centre for AI and Innovation. If the person in the room is a doctor, it may be him. AIG's chairman, Dr D Nageshwar Reddy, often says AI should augment doctors, not replace them ([Telangana Today](https://telanganatoday.com/doctors-effectively-using-ai-will-surpass-those-who-do-not-says-dr-nageshwar-reddy)).
- **Cares about:** production-grade hospital AI, integration, uptime, security, cost per inference, vendor lock-in.
- **Likely probes:** architecture, fallbacks, offline behaviour, accuracy on Indic speech, cost per patient, model risk management, how a hospital IT team would deploy it.
- **Pitch to him/her:** One architecture line: Android app, Supabase backend, consented audio to Sarvam (Saaras) with Gemini as fallback and on-device Whisper or typing as the last resort, and the doctor signs every draft. Admit the gaps: Telugu and Kannada on-device ASR is poor, and Android UI tests are partly stale.

### 1.4 Healthcare VC representative (unnamed). Confidence: n/a
- **Cares about:** who pays, the buyer versus the user, distribution, retention, defensibility, team commitment after the hackathon.
- **Likely probes:** business model, CAC via NCG or hospitals, unit economics, competition (Pallium India, BandhuCare, global ACP tools), whether the team stays together.
- **Pitch to them:** The buyer is the cancer hospital or network (and CSR/philanthropy for NGOs). The user is free. The wedge is the goals-of-care note plus the emergency card, which no one else in India does end to end. Do not invent revenue numbers.

### 1.5 OpenAI India: Mr Prabhjeet Singh, Managing Director (keynote; may or may not sit on the jury). Confidence: high for the role
- **Who:** OpenAI's Managing Director for India, announced June 2026. Ex-President of Uber India and South Asia; alumnus of IIT Kharagpur and IIM Ahmedabad. Reports to the APAC MD ([YourStory](https://yourstory.com/ai-story/openai-names-prabhjeet-singh-as-md-for-india); [Business Today](https://www.businesstoday.in/technology/news/story/all-about-prabhjeet-singh-the-ex-uber-executive-named-as-openais-india-operations-lead-539503-2026-06-27)). If a different OpenAI India person joins the jury, the same points apply.
- **Cares about:** India-scale adoption, responsible AI in health, use of OpenAI models.
- **Likely probes:** "Why not OpenAI models?" Safety evaluation, hallucination controls.
- **Pitch to him:** State truthfully what we use: Sarvam for Indic speech, Gemini as fallback, and OpenAI's open-source **Whisper** on the phone as the offline route. Our design does not depend on one vendor: the provider is swappable behind one server function, and an OpenAI transcription or drafting model could be added behind the same consent and doctor-signs rule. Do not claim we use OpenAI's API today.

---

## 2. Ten-minute pitch outline (mapped to the organisers' five points)

| Time | Segment | Organiser point | Speaker | Content |
|---|---|---|---|---|
| 0:00–1:15 | The patient story | (b) | **Clinician (Dr Shirin)** | One real-type case (anonymised): advanced cancer, family never told what to do at 2 am, ED intubates against unspoken wishes. Then the gap: fewer than 4% of Indians who need palliative care get it ([ecancer 2022 survey](https://ecancer.org/en/journal/article/1486-situational-analysis-of-the-quality-of-palliative-care-services-across-india-a-cross-sectional-survey)). Goals-of-care talks are rarely documented, and wishes do not reach the ED. |
| 1:15–2:30 | Why this use case | (b) | **Clinician (Dr Sujay)** | Three breaks in the loop: the talk is not written down, home symptoms and carer strain are invisible, and the ED is blind. Legal basis now exists: the 2023 Supreme Court simplification of advance directives ([Medical Dialogues summary](https://medicaldialogues.in/news/health/government-policies/passive-euthanasia-sc-modifies-2018-guidelines-on-living-will-advance-medical-directives-106848)). |
| 2:30–5:30 | **Live demo** (phone mirrored) | product | **Tech (Basith)**, with the clinician narrating the doctor side | (1) Doctor records a goals-of-care talk in Hindi; Sarvam transcript; structured note in the legal form's sections; doctor and family sign, and the version locks. (2) Emergency care plan, then an offline QR card with a valid-until date 90 days out. (3) Family view: ESAS-style symptom diary with body map, NCCN distress thermometer, ZBI-4 carer check. (4) Nearby: palliative centres map, morphine report. Keep a recorded backup video ready. |
| 5:30–6:45 | How doctors and tech built it together | (a) | **Clinician + Tech, alternating** | Meetings and transcripts became specs. Rituparna's legal form is the note word for word. Dr Sujay's video review of 22 Nov became a 30-item fix list, fixed within days. Clinicians chose the tools (ZBI-4, NCCN, ESAS items) and the surprise-question timing. Shipped to the Play Store in production, not just a prototype. |
| 6:45–8:00 | Fit into everyday workflow across digital maturity | (c) | **Clinician (Dr Sharada)** | Works at three levels: **paper hospital** (app on the doctor's phone, printed PDF note, QR card works offline in any ED); **partly digital** (PDF or FHIR export attached to the HIS); **digital** (future ABDM/HIS link). The doctor-only surprise-question prompt suggests when to start the talk. Approval by registration number keeps doctor identity honest. |
| 8:00–9:00 | Extending to other uses | (d) | **Tech** | Same pipeline (consented recording, Indic transcript, structured note, signed version, offline card) applies to heart failure, CKD/dialysis, COPD, dementia, ICU family meetings, and any informed-consent conversation. |
| 9:00–10:00 | Scaling and the ask | (e) | **Clinician (lead) + Tech** | Ask NCG for a **pilot in 2–3 member centres** (one paper-based, one digital) with goals-of-care documentation rate and ED concordance as measures. Then an NCG palliative-care working-group endorsement, hospital licences, and NGO partners for the home side. Close on the patient. |

**Rules:** the clinician opens and closes. Whoever holds the phone does not also narrate clinical claims. Practise to 9:30.

---

## 3. Likely questions and model answers

**[NUMBER]** = the team must supply a real number; do not guess on stage.

### A. Clinical

1. **"Palliative care in India is mostly home-based NGO work. Where does Sahara sit?"** Beside the treating team, from diagnosis onwards. The family logs at home; the doctor sees trends; Nearby lists **1,631 published centre listings across 36 states and UTs** (from the store listing; not independently verified, call before travelling).
2. **"How does this help with morphine access?"** Nearby shows centres listed as stocking oral morphine (historical listings, not stock confirmation). When a family logs oral morphine, the app asks where they got it, to build a verified map. Reports stay private until the team checks them. The 2014 NDPS amendment created "essential narcotic drugs" with a uniform licensing system, but access is still poor ([IJME 2015](https://ijme.in/articles/access-to-controlled-medicines-for-palliative-care-in-india-gains-and-challenges/?galley=html)). We do not prescribe or supply.
3. **"Is the goals-of-care note a legal advance directive?"** No. It records a discussion, signed by the doctor and the patient or family. It can *inform* an advance directive. Under the 2023 modification, an AD needs two witnesses and attestation by a notary or gazetted officer; withdrawal of life support goes through hospital primary and secondary medical boards (each "preferably within 48 hours"), and the JMFC is informed ([Medical Dialogues](https://medicaldialogues.in/news/health/government-policies/passive-euthanasia-sc-modifies-2018-guidelines-on-living-will-advance-medical-directives-106848); [LiveLaw judgment PDF](https://www.livelaw.in/pdf_upload/79-common-cause-v-union-of-india-24-jan-2023-457290.pdf)). The living-will section helps people prepare that document. It does not replace attestation.
4. **"DNAR has no clear legal status in India. How do you handle CPR?"** We record the patient's or family's stated preference about CPR, dialysis and ventilation, with the doctor's signature and date. The ED doctor still makes the decision. The card says "if in doubt" in plain conditions, and that care which always continues (comfort, symptom relief) never stops. Ask the clinician to cite the ICMR/ISCCM consensus they rely on. **[clinician to confirm source]**
5. **"What's the evidence that documenting goals of care helps?"** Clinician answer: serious-illness conversations are linked to care that matches goals and less distress. We do **not** yet have Indian outcome data from Sahara. Our pilot measures documentation rate, ED concordance and carer burden. **[clinician to name 1–2 papers they know well]**
6. **"The surprise question is a weak predictor."** Agreed. The pooled sensitivity is about 67% and specificity 80%, a little better in cancer (AUC 0.83) ([Downar et al., CMAJ 2017](https://pubmed.ncbi.nlm.nih.gov/28385893/)). We use it only as a doctor-only *reminder to have the conversation*, never as a prognosis shown to patients. It is paired with ECOG and cites Downar in-app.
7. **"Are ESAS and ZBI validated in Indian languages?"** Honest answer: ESAS has a Kannada (renal ESAS-r) validation ([IJPC 2023](https://jpalliativecare.com/kannada-translation-and-validation-of-the-esas-r-renal-for-symptom-burden-survey-in-patients-with-end-stage-kidney-disease/)); we found none for Hindi, Tamil or Malayalam. ZBI-4 is from Bédard et al. 2001 (4-item screen, r 0.83–0.93 with the full ZBI) and was checked in palliative carers ([PMC7546983](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7546983/)); we found no Indian ZBI-4 validation. Our translations are machine-drafted and need clinician back-translation. Say "**based on** validated tools", not "validated in Indian languages".
8. **"Paediatric patients?"** Not designed for children. The store audience is 18+, and the family app is for adult patients' carers. Paediatric palliative care needs different scales and consent (parents, assent). It is future work with a paediatric palliative partner.
9. **"Capacity: who speaks for the patient?"** The goals-of-care flow has a capacity step (made clearer after Dr Shirin's review). If the patient lacks capacity, named family speak and are recorded as such. Each person's latest statement wins, and when people disagree both views are shown to the doctor.
10. **"What about collusion: families asking doctors not to tell?"** The note records who knows what. Patients never see the death-at-home guide; only family do. This is a clinician-led conversation; the app does not decide what is disclosed.
11. **"Death at home: what happens?"** Family-only SOS row ("Not breathing or not waking up?") opens a practical guide. Still under team discussion (22 Nov minutes); be candid.
12. **"A stale QR card is dangerous."** Every offline card shows a valid-until date 90 days after it was made, and says to call the family and ask whether a newer card exists. The online link always shows the latest signed version.

### B. Safety and regulatory

13. **"Is this a medical device under CDSCO?"** We designed it as record-keeping and communication: no diagnosis, no treatment recommendation, and a doctor signs every AI draft. The Play listing declares "not a medical device". CDSCO's final Medical Device Software guidance (July 2026) excludes software without a medical purpose, such as data management and wellness, and classifies other software A–D ([Emergo summary](https://www.emergobyul.com/news/india-cdsco-finalizes-guidance-medical-device-software)). The surprise-question prompt and symptom flags are the closest to decision support. We would get a formal regulatory opinion before a hospital rollout. The hackathon itself excluded clinical decision support ([Internshala brief](https://internshala.com/competitions/health-a-thon-2026/)), so our design stays on documentation.
14. **"DPDP compliance?"** Consent is collected before any online AI step and can be withdrawn. Accounts are optional: basic records stay on the phone. In-app deletion of a person's records, a privacy policy and a deletion page exist. DPDP Rules were notified Nov 2025; the main duties apply 18 months later, around May 2027 ([Mondaq/AZB](https://webiis10.mondaq.com/india/data-protection/1706878/indias-digital-personal-data-protection-act-phased-rollout-and-key-compliance-milestones)). **Gaps we admit:** a named grievance officer, a fixed retention period and a nominee process are still open in our draft policy.
15. **"Where is data stored?"** Supabase; internal notes record Mumbai (ap-south-1). **[owner to confirm in the dashboard before saying "in India"]** Audio goes to Sarvam (an Indian company) or Google Gemini only with the doctor's consent tick, and is deleted after transcription.
16. **"AI hallucination in a legal-ish note?"** The AI only drafts. The doctor reviews every field against the transcript; quotes are linked to the source; unclear choices stay "No preference recorded" until confirmed. A negation test set ("We do not want dialysis") passes. Signing locks the version, and any edit breaks the SHA-256 seal.
17. **"Consent for recording?"** Everyone present must tick consent before the microphone starts. Audio stays on the phone unless the doctor ticks online transcription.
18. **"ABDM/ABHA?"** Not integrated yet. The structured note maps to FHIR-style fields, and ABHA linking is on the roadmap for digitally mature NCG centres. Do not claim integration.
19. **"Doctor identity?"** Doctors submit their council and registration number. An admin checks it against the NMC register and approves, rejects or suspends. Patients join only by the doctor's or family's invitation.

### C. Tech and AI

20. **"Why Sarvam and not OpenAI?"** Sarvam's Saaras covers all 22 scheduled languages plus English, with code-mixing ([Sarvam docs](https://docs.sarvam.ai/api-reference-docs/models/saaras)), and is the event's AI partner. Gemini is the fallback; OpenAI's open-source Whisper runs on the phone offline. The provider sits behind one server function, so it can be swapped.
21. **"How accurate is Indic transcription?"** **[NUMBER: our own measured WER/CER on real recordings]**. Known: on-device Whisper was poor for Telugu and Kannada (CER around 0.9 and 1.6–1.8 in our own small test), so typing is offered. Sarvam reports about 19% WER across 10 languages on IndicVoices (vendor figure). The doctor checks every line regardless.
22. **"Does it work offline?"** Yes for the core: diary, medicines, reminders, the QR card (readable without internet) and on-device transcription. Sync happens when the phone is back online. Online AI is optional.
23. **"Low-end phones?"** **[NUMBER: minimum Android version, APK size, RAM tested]**. Min SDK is 26 (Android 8). Large speech models download on demand, not in the APK. Tested on Samsung A34 and an emulator; a Xiaomi faded-text fix was made.
24. **"Cost per patient?"** **[NUMBER: Sarvam per-minute rate × typical 20–40 min talk, plus Supabase per-user cost]**. Daily use (diary, reminders) has close to zero marginal AI cost; AI cost falls on the goals-of-care conversation only.
25. **"How did you test it?"** About 877 unit tests pass. Android on-device UI tests: 170 run, many stale after the redesign and being fixed. Eight mock goals-of-care conversations across languages. **[NUMBER: real-phone goals-of-care tests done before the 28th; target is 5]**

### D. Business and VC

26. **"Who pays?"** Hospitals and cancer networks pay per active patient or per centre (documentation, fewer unwanted ED escalations, NABH-style quality). Free for patients and families. NGOs and government centres are funded through CSR, philanthropy or state palliative programmes. **[team to agree one model before Saturday]**
27. **"Go-to-market?"** NCG first: a pilot, then a working-group endorsement, then member centres. In parallel, palliative NGOs for the home side and state palliative programmes (Kerala-style networks).
28. **"Competitors?"** In India: Pallium India's directory, Palli Sahayak, Panha; BandhuCare (CMC-led, symptom companion). Abroad: Vynca, Abridge, ReSPECT, Kaiku/Noona. Each does one piece. Sahara joins the goals-of-care note, the home diary, the family and the ED card in Indian languages (team comparison, checked 20 Nov).
29. **"What's the moat?"** Clinician-designed workflow matched to Indian law (legal form wording, 2023 AD rules); a verified morphine and centre map built from family reports; and distribution through NCG. Be honest that code alone is not a moat.
30. **"Traction?"** Live on Google Play in India (0.7.1; 0.8.0 in review). **[NUMBER: installs, signed-up doctors, real goals-of-care notes; say "pre-pilot" if small]**
31. **"Will the team continue?"** **[team to answer truthfully: who commits time after 28 Nov, and the clinician champion]**

### E. Scaling

32. **"How would NCG adopt this?"** A 3-month pilot in 2–3 centres of different digital maturity. Measures: share of eligible patients with a documented goals-of-care note, ED visits where the card was used, ZBI-4 trend, and doctor minutes per note **[NUMBER: measured minutes per note]**. Then a protocol for the NCG palliative group.
33. **"Integration with hospital HIS?"** PDF today; FHIR export next; ABDM after that. Paper hospitals need no integration.
34. **"Multi-language clinical content at scale?"** UI translations are machine-drafted and need clinician back-translation per language. We would ask NCG centres to own review for their language.

---

## 4. Watch-outs: claims we must not overstate

1. **"Validated tools."** Say "based on ESAS, NCCN Distress Thermometer and ZBI-4". Not "validated in Indian languages", and not "same items and scoring as the validated originals" for the symptom diary (it adds a body map and custom items). **The current `pitch/Sahara_Validated_Tools_Slide` shows the AMA Caregiver Self-Assessment Questionnaire (18 items). The app uses ZBI-4. Fix the slide before Saturday.**
2. **AI vendors.** Recordings use **Sarvam first, Gemini fallback**, and on-device Whisper or typing as the last resort. Do not say we use OpenAI's API. Confirm on the 28th that the `SARVAM_API_KEY` is live in Vercel and Supabase (the handoff listed it as an open step), and that the demo shows "Transcribed by saaras".
3. **Outcomes.** No measured patient outcomes, ED concordance, time saved or cost savings. Say "we will measure".
4. **"22 languages."** True for Sarvam transcription and note translation. The app UI is mainly English and Hindi, with Telugu, Urdu and Kannada dictionaries, and machine-drafted translations. Don't imply the whole app is in 22 languages.
5. **Legal status.** The note is not an advance directive and not a DNAR order. The QR card is "offline, not linked to any hospital system".
6. **Centre and morphine map.** Listings are published sources, not verified stock. Use "listed", never "available".
7. **Data residency.** Do not say "all data stays in India" until the Supabase region is confirmed. Gemini processing may be outside India.
8. **Doctor-only surprise question.** It is a reminder, not a prognosis tool; never describe it as AI prediction.
9. **Play Store.** Say "live on Google Play in India". Don't cite install or user numbers unless we have them.
10. **Testing.** Don't say "fully tested". The unit tests pass; the on-device UI tests are being updated; real-phone goals-of-care testing is in progress.
11. **BandhuCare and Pallium India.** Never disparage them. Juror 2's institution leads BandhuCare, and NCG supports it. Position Sahara as complementary.
12. **Names.** Say "Dr Chaturvedi (Apollo)", "Dr Mammen". Confirm the AIG juror's name and title from the programme on the day before addressing them.
