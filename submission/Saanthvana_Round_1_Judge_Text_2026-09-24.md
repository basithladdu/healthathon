# Saanthvana: eight-slide pitch

This revision uses both meetings from this week: 21 September and 23 September 2026, plus the later Shirin/Sujay messages on 23 September. The visible slide wording below is the source of truth for the deck. Photo credits and the full research citations belong in the relevant slide notes.

## 1. Saanthvana

A shared care record for people with cancer and their families.

Imagine a 62-year-old woman with advanced breast cancer. She starts best supportive care to help manage her symptoms. Her doctor discusses what she would want if her health worsens, but only her husband is there.

Two months later, she is unconscious and rushed to hospital. The doctor asks whether she would want attempts to restart her heart or a machine to help her breathe. Her husband says yes. Her daughter says no. Her son is unsure. No one can find the earlier discussion.

Saanthvana brings that conversation together with the records and next steps the family needs.

Cancer / Patient and Caregiver / Family & Caregiver Support

continuity-loop-healthathon.vercel.app

Notes: This is the team's illustrative story, not a reported patient case. A contextual photograph must not be presented as the woman in this story. Use the later 23 September revision. Do not add a death outcome or imply a particular emergency intervention is right or wrong. Place the working app QR on this slide or slide 3, as well as at the end if space permits.

## 2. The need is large. Better records are one part of the answer.

**15.6 lakh**

India had an estimated 15.6 lakh new cancer cases in 2024.

**98.39%**

A registry analysis estimated that 98.39% of people whose cancer had spread did not receive palliative care.

The analysis covered 10 cancer types in Indian registry data.

**A clearer process helped one hospital**

Documented care conversations: **0% before / 92% after**.

A standard form and process helped a Chennai cancer hospital document these conversations for 24 of 26 eligible patients.

Sources: IARC, GLOBOCAN 2024; Patil et al., 2024; Thangasamy et al., 2025.

Notes: The IARC figure is 1,562,581 new cases in 2024, rounded to 15.6 lakh. Patil et al. define unmet need as the percentage of people with metastatic cancer who did not receive palliative care, using NCRP 2020 data for ten cancer types. It does not measure awareness and is not a national survey of all people with cancer. The Chennai quality-improvement study concerned advanced pancreatic/colorectal cancer at Cancer Institute (WIA). The intervention included a standard process, a colour-coded form and team awareness/referral work. The chart shows published research, not Saanthvana outcomes or an AI result. These studies concern different populations and must not be multiplied together. The app addresses documentation and coordination; it cannot by itself supply missing care services.

## 3. The patient’s wishes stay with the care record

Doctors call this a goals-of-care conversation. It means talking about what matters to the patient and what they want from their care.

The patient or family can ask to talk. The doctor can start the conversation too.

The note keeps what was discussed separate from what was decided. An unanswered question stays unanswered.

The doctor checks the note. In the shared app we are building, the patient will choose which family members can see it. A later conversation gets a new dated version, so earlier discussions stay available.

Notes: Use a genuine capture of the signed-note/history flow. Describe the intended shared workflow, with the current account/sharing boundary explicitly on slide 7. A care note is not a legal advance directive or an instruction for automatic treatment. The current app records typed names, not verified digital signatures. Sources: 21 September transcript lines 337–395 and 903–959; 23 September transcript lines 15–21 and 123–167; later chat decisions.

## 4. AI has two clear jobs

**Help write the care note**

With permission, the doctor records the conversation or adds existing notes. AI will prepare a draft and point back to the source. The doctor corrects it before signing.

**Find information in the patient’s own documents**

AI will organise uploaded reports by date and help answer questions such as “When was my last chemotherapy?” Each answer must show the original record. If the answer is missing, it must say so.

AI will help organise information. It will not diagnose, interpret test results or recommend treatment.

Notes: These are planned connected-AI functions. Today the app supports document upload, source passages, literal retrieval, recording and rule-based note preparation. A model, reliable transcription and image OCR are still to be connected. The source-bound examples come from the 21 September discussion, including lines 251–303 and 1097–1155. The reviewed conversation remains the main AI use case; document retrieval supports continuity. Use one real clinician/document photograph and a genuine document-library capture if space permits.

## 5. The family still needs help between visits

**Today’s plan**

The family can find appointments, medicine times and the doctor’s preparation notes together.

**Documents and personal notes**

Reports stay in one place. The patient can keep symptoms, feelings and questions for the next visit.

**Help from people and places**

A task goes to a named family member who can accept it and mark it done. The map and support-group links help the family find people to contact.

The care conversation stays at the centre of these everyday tools.

Notes: Use actual Home/calendar/support screenshots. The medicine schedule uses reviewed prescription details; no dose advice. Symptom entries are for sharing with clinicians, not automatic clinical interpretation. Named tasks are not transport bookings. Map markers and links do not establish opening hours, beds, dispensing availability or emergency response. External groups are not an in-app community. Across-device delivery is still unfinished, as slide 7 states. Sources: both meeting transcripts and Shirin's 23 September request to bring today's medicines/appointments to Home.

## 6. Doctors need a clear starting point. Families need a say.

**88%**

In a 2019 survey of over 2,400 urban respondents, 88% wanted to choose their treatment near the end of life, according to IndiaSpend.

**11.5%**

Only 45 of 391 doctors in one hospital survey reported prior training in advance directives, which formally record a person’s wishes for future care.

We are building guided questions and a draft from the conversation. The patient can say what matters to them and choose who can read the checked note.

Sources: HCAH survey in seven cities, reported by IndiaSpend, 2019; Jacob et al., one hospital survey, 2026. These are separate surveys, not national estimates.

Notes: Doctor measure is prior training in advance directives, not knowledge of their definition. Table 1 reports 45/391 (11.5%). The hospital is CMC Vellore. The 88% is explicitly attributed to Priya Sharma's original IndiaSpend reporting, 3 June 2019, of HCAH's Living Will Survey. It covered seven cities, 350–400 respondents per city, with equal numbers of men and women who had been hospitalised for more than one day in the preceding year. It is not a cancer-only or nationally representative sample. The original survey's Adobe link was inaccessible, so this is a reported finding, not an independently examined survey dataset or instrument. Related research motivates the design but does not prove app effectiveness. Guided questions aid the discussion; this care note is not a substitute for a legal advance directive. Source: https://www.indiaspend.com/73-urban-indians-ignorant-of-legal-right-to-living-will/

## 7. What works now, and what we need to prove

**You can use the app today**

Upload documents, record a conversation, review a care note, add a typed signature, open earlier versions and download a PDF. The calendar, journal and family tasks are connected within the app.

**The next connections**

Connect AI and reliable transcription, verified accounts and sharing between different phones. Check translations and care-centre details with the people who will use them.

**Our proposed 60–90 day trial**

Measure the time doctors spend writing and correcting notes, how quickly families find the latest plan, and whether agreed tasks get done. Count mistakes and extra work too.

We have not measured these results yet.

Notes: Current functionality is a browser implementation, not evidence of clinical deployment. No connected model, verified ABHA/medical registration, delivered notifications, shared backend or legal digital signing is claimed. The proposed 60–90 day evaluation aligns with the supplied Health-a-thon brief; that time range was not agreed in either meeting. Partners, enrollment and protocol remain to be arranged. No time-saving percentage, outcome or accuracy claim exists. Use a real contextual photo of clinician work or home support.

## 8. The hardest decisions shouldn’t be made without the right information

We are starting with cancer care conversations, then keeping the family’s next steps connected to that record.

**Zeros and Ones**

Dr Sharada Vinod Kutty

Dr Sujay Halkur Shankar

Shaik Abdul Basith

Shaik Muhammad Awaiz

Open Saanthvana

continuity-loop-healthathon.vercel.app

Notes: These are the four confirmed team members. Do not name institutions as pilot partners or add Shirin as a confirmed fifth registered member. No headshots unless actual supplied photographs are available. Keep a large scannable QR and a real contextual photograph with its original credit. Closing line follows the later 23 September messages.
