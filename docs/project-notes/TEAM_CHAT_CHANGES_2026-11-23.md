# Team chat: what's new, 23 Oct – 23 Nov 2026

Source: the "Internal Zeros and Ones" WhatsApp export of 23 Nov 2026. It has 1,031 new messages since the last saved copy:

| Person | Messages |
|---|---|
| Sujay | 438 |
| Basith | 237 |
| SVK (Dr Sharada) | 198 |
| Shirin | 124 |
| Awaiz | 33 |
| Muqeeth | 1 |

The cleaned chat is in [`source-materials/team-context/2026-11-23`](../../source-materials/team-context/2026-11-23/README.md).

How to read the Status column:

- **Done:** I found it in the code or commits on 23 Nov.
- **Partly:** some of it is there.
- **Open:** I found nothing in the code for it.
- **Check:** something matched, but someone needs to look at the app.

None of these are clinical sign-offs.

## 1. Headline

- **Round 1 submitted on 7 Nov, 23:58.** Submitted: the app, the 13-slide PPT, the two-page brief, and the video, uploaded through a Drive link.
- **Shortlisted for the Grand Finale on 18 Nov.** The finale is at **IIT Bombay on 28 Nov**:
  - Be there by **07:00** to check the slides on screen.
  - The event ends around 17:00.
  - The pitch is **10 minutes, in a closed room**. Teams from other tracks may watch.
  - The top 3 also present on stage. That stage showcase isn't scored.
- **Jury, from the 23 Nov briefing (names as Basith wrote them in the chat; not checked):**
  - "Dr. Harit Chhaturvedi" (Apollo oncology network)
  - "Dr. Joy Manin" (CMC Vellore)
  - "Mr. Kil Sakenna" (head of technology and AI, AIG Hospitals)
  - a healthcare VC
  - an OpenAI India team head

  The organisers stressed that the jury is senior and **will ask clinical questions**.
- **Travel:**
  - Hotel: Anantha Executive Suites, Powai. Basith, Awaiz and Shirin booked on 19 Nov; Sujay booked on 20 Nov.
  - Basith, Shirin and Awaiz fly from Hyderabad on the 27th.
  - Team T-shirts: black, Sahara logo on the front (4×4 in), "Zeros & Ones" logo on the back. Ordered on 22 Nov.

## 2. Decisions the team made

| Topic | Decision | Who, when |
|---|---|---|
| Positioning | Sahara is a **patient-facing extension of CARE** (ohc.network). The goals-of-care discussion (GOCD) is the bridge between the doctor side and the patient side. | Sujay, 26 Oct; Basith agreed |
| Wording | Remove **"legal"** and **"living will"** everywhere. Say **"clinical document"**, "goals of care discussion" or "advance care planning". | Sujay, 7 Nov |
| Legal reviewer | Don't name Rituparna. Say legal advice was sought on the GOCD wording and format. | Sujay, 7 Nov |
| GOCD sections | Patient details, caregiver details, patient capacity, existing directives, understanding of the illness, values, goals of care, home vs hospital transfer, CPR, ICU, ventilation, dialysis, nutrition. **Withholding life support is left out.** | Sujay, 7 Nov |
| Recording screen | Offer **"Guide me"** (section cards) or **"Free flow"**. No AI in the guide. | Sujay, 7 Nov; Basith added it the same day |
| Symptom tracker | Based on Shirin's list, modelled on ESAS-r and IPOS. The first entry has full detail; later entries are mostly severity, with optional detail. Date every entry. Show trends as connected line plots. Entries are optional, at most once every 24 h. | Shirin, SVK, Sujay, 24 Oct |
| Distress alerts | **No push notification** to the doctor for severe distress. In-app pop-up when the doctor opens the patient. Notify the doctor when the emergency QR or link is opened. | Sujay and Shirin, 23 Nov |
| Recording consent | Either the patient or the family should be enough, not both. Patients or families sometimes don't want the diagnosis shared. | SVK and Sujay, 23 Nov |
| Stage demo | Use a pre-recorded simulated conversation, not real patient data. | Sujay, 19 Nov |
| Pitch deck | New deck for the finale. Turn sheet 1 of SVK's comparison sheet into a competitor slide (Panha, PinkShield AI, TANUH). Agree what to say and in what order in person on the 27th. | 20 Nov meeting |

## 3. App change requests and status

### Goals-of-care discussion (doctor side)

| Request | From | Status |
|---|---|---|
| Guide me / free flow choice; a fixed checklist the doctor can see before the first conversation | Sujay, 7 Nov | **Done.** "Guide me" is in the code. |
| Fix GOCD PDF numbering (4.2, 5.3, 6.1…) | Sujay, 7 Nov | Check |
| Let the doctor create a new GOCD after one is signed and locked | Sujay, 7 Nov | Check |
| Remove the life-support-withholding section from the template, the recording and the PDF | Sujay, 7 Nov | Check |
| Remove "living will" and "legal form" from what users see | Sujay, 7 Nov | **Partly.** The GOC form text still opens with "advance medical directive (living will)". "Legal form" now appears only in code comments. |
| Show the conversation checklist while recording too, not only when typing | SVK, 23 Nov | Open |
| "Surprise question" (would you be surprised if the patient died within 12 months?) as an algorithm, placed above the goals-of-care card | Basith, 20 Nov; SVK, 23 Nov; minutes, 22 Nov | **Partly.** The surprise question is in the code; check where it sits and how it works. |
| ECOG next to the performance question | SVK, 23 Nov | Check. ECOG is in the code. |
| A tick box that can't be ticked, although signing works | SVK, 23 Nov | Open (bug) |
| Explain "patient capacity" better | Shirin, 21 Nov | Open |
| Let the patient ask for a GOCD from their own side | Sujay, 22 Nov | Check. It shows on the doctor side only. |
| A new GOCD version makes old prints invalid. How do we stop an old printed copy being used? | Sujay, 21 Nov | Open. Needs a decision. "Failsafe for older physical QR codes" is in the 22 Nov minutes. |
| Remove the GOCD steps from emergency planning and from advance care planning on the patient side | Sujay, 21 Nov | Check |
| Use Sarvam instead of Gemini for multilingual GOC | 8 and 20 Nov | **Partly.** `goc-sarvam.ts` exists in `sahara-care-assist`; the key and deploy are pending. |

### Symptoms and distress

| Request | From | Status |
|---|---|---|
| Distress thermometer on one screen, not hidden behind "write more" | Sujay, 1 and 7 Nov | Check |
| Line plots instead of bar charts; most severe symptom first, or a sort toggle | Sujay, 1 Nov | Check |
| "All symptoms" more button is hard to notice; symptom names too small when scoring several | Sujay, 7 and 21 Nov | Check |
| Faces from the video are better; add a sad face; 0 and 10 labels on one line | Sujay and SVK, 7, 21 and 23 Nov | Check |
| Thermometer: text and top spacing; no green and yellow together; gap at the bulb | Sujay, 21 Nov | Check |
| Explain distress vs physical concerns: each symptom has its own 0–10; the thermometer is overall distress | SVK and Sujay, 23 Nov | Open. Make the wording clearer. |
| Doctor pop-up for pain or distress ≥ 7/10 in the last 7 days | Shirin and Sujay, 23 Nov | **Done** (`c97a307`) |
| Family profile: "How is the patient feeling?" instead of "How are you feeling?" | Shirin, 6 Nov | Check |

### Medicines and reminders

| Request | From | Status |
|---|---|---|
| 20 or more medicines must not make the screen too long; compress cards; no side effects on the home page or in the journal | Sujay, 1, 7 and 21 Nov | Check |
| SOS or anytime medicines logged several times a day with time, and a count for the doctor | Sujay, 21 Nov | **Partly.** Code for as-needed medicines exists; check the per-day count for the doctor. |
| Several medicines, each with its own plan timeline (a + button) | Shirin, 21 Nov | Check |
| Medicine and appointment alarms that stay until "taken" is pressed | Shirin, 21 Nov | **Partly.** Dose reminders exist; visit reminder the evening before is **done** (`c97a307`). |

### Patient profile, emergency card and directory

| Request | From | Status |
|---|---|---|
| Profile page with basic details (age, weight); diagnosis visible without opening edit | SVK, 1 Nov; Sujay, 7 Nov | **Partly.** First-run care details were added (`384b3dd`). |
| Sex vs gender: both are kept, on the lawyer's advice; DOB year should be changeable without swiping months | Shirin, 21 Nov | Check the DOB picker (`b3dc5eb` reworked it) |
| Guardian's phone number on the emergency QR | Shirin, 21 Nov | Check. "Guardian" is in the code. |
| Tell the doctor where and when the emergency card was opened | Sujay, 23 Nov | **Done.** App, plus the database change applied 23 Nov. The website isn't deployed yet. |
| Map: centres offering palliative care disappear when morphine is unticked | Sujay, 7 Nov | Check |
| Keep checking the care directory (hospitals, groups) in case they go offline | Shirin, 23 Nov | Open |
| Survey: morphine "yes" → ask which centre; directory updated by its users | Basith, 20 Nov meeting | Open |
| Pregnancy and elderly ambulance entries: are they needed? | Sujay, 1 Nov | Open. Needs a decision. |

### Family and caregivers

| Request | From | Status |
|---|---|---|
| Choose family member or nurse when creating a caregiver | Shirin, 6 Nov | Open |
| Caregiver wellbeing / burden section, with its source | Shirin; Sujay, 7 Nov | **Done.** Section and source are in the code. |
| Caregiver pop-up appears every time a symptom is added | Sujay, 7 Nov | Check |
| **Caregiving changes and bereavement** (SVK's structure, 23 Nov): quiet check-in after 3–5 inactive days; "I'm no longer actively caregiving" → optional reason (passed away, hospice, someone else took over); stop all reminders at once; archive, never delete; grief support; pause for 30, 60 or 90 days; a co-caregiver can update | SVK, 23 Nov; the team agreed. Linked to "how to address the death of a patient" in the 22 Nov minutes. | Open |

### Look and feel

- 7 Nov: the revamp was too red and contrasty, and the icon turned red. The calmer v3 the same night was liked.
- 21 Nov: Sujay again found the colours "too dark and scary".
- Make the app easier for elderly and less tech-savvy users (Shirin, 23 Nov).

Status: Check.

### Planned for later (v2)

- **Lines and tubes** (chemo port, feeding tube, colostomy and so on): date inserted, date due for change, centre, complications as free text. Sujay, 3 Nov. Status: Check, since device terms are in the code.
- Ambient microphones instead of phone-only recording (Sujay, 7 Nov). Put it in the deck as a plan.
- Integrations with hospitals and hospice systems for caregiver status (SVK, 23 Nov).

## 4. Open action items

From the 22 Nov minutes and the messages after them:

- [ ] Pitch deck outline: Dr Sharada and Sujay.
- [ ] Ask the mentor about the deck outline and the upcoming call: Basith.
- [ ] How to handle a patient's death: everyone. See SVK's structure above.
- [ ] Failsafe for older printed QR codes.
- [ ] Design the surprise-question algorithm.
- [ ] Test the new APK with at least 5 conversations. Sujay asked for logins to be allowed. APK 0.8.0 was shared on 23 Nov.
- [ ] Prepare for clinical and ethical questions from the jury.
- [ ] Long screen-by-screen review call, planned for **24 Nov after 12:00**. SVK and Sujay; Shirin may join later.
- [ ] Play Store update after the "thousand edits" (Sujay asked for them by the 22nd).
