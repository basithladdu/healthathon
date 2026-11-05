# Sahara: demo video script and shot list

**Length: about 6 min 30 s** (limit 6–7 min, organiser email of 3 Nov). About 850 spoken words at a calm pace.
**Order follows the final presentation:** team → problem → users → goals of care (live) → between visits (live) → technology → progress and validation → safety → next steps.
The organisers want a **narrated walkthrough**, not a silent screen recording or a run of screenshots. Show the app working, and show the team's faces at the start and the end.

## Before recording

- **Phone:** the latest Sahara APK on a real Android phone. Record the phone screen (Android screen recorder), with the voice recorded separately on a good microphone, then lay the voice over.
- **If a phone step fails:** use the website demo at `sahara.wedevit.in` on a phone-sized browser. Doctor → Sign in → Plan → "I have read this" → Start a review → tick a reason → "Open the review draft" → step 5 "Values" → "Record the conversation". The deck's screenshots were taken from that path on 5 Nov.
- **People and data:** fictional only (Meera Raghavan and her daughter Kavya on the website; Rahul on Android). No real names, phone numbers or records on screen.
- **Goals-of-care talk:** two team members (or Dr Sharada and a teammate) act out `docs/goc-mock-conversations/01-english.txt`, about 60–90 seconds, in a quiet room. Tick the consent box first. If Gemini says "busy", wait: the app tries the next model, and the phone's own Whisper takes over offline.
- Large system font **off**, notifications **off**, battery icon full, Wi-Fi on.
- Say "Sahara" every time, never an older name.

## Shot list

| Time | On screen | Narration |
|---|---|---|
| **0:00–0:35** Team | Deck slide 1 (cover with photos), then a short clip of the team on a video call, or slide 2. | "We are Team Zeros and Ones, and this is Sahara, a palliative-care companion. Dr Sharada Vinod Kutty and Dr Sujay Halkur Shankar are pulmonary specialists who care for people with lung cancer; Dr Sujay also has palliative-medicine experience. Shirin Ayub, a medical student, shaped our symptom tracker, and Rituparna P reviewed our goals-of-care form for legal wording. Shaik Abdul Basith and Shaik Muhammad Awaiz built the app." |
| **0:35–1:20** Problem | Slide 3: the consultation photo, then the four numbers. | "Here is the problem our doctors see every week. A 62-year-old woman with advanced breast cancer tells her doctor what she wants at the end of life. Only her husband is in the room. Two months later she is unconscious in the emergency department. The doctor asks: did she want CPR? Her husband says yes, her daughter says no, and the note from that conversation cannot be found. India has about 15.6 lakh new cancer cases a year, almost all palliative-care need in metastatic cancer is unmet, and in one large hospital only about one doctor in nine had training in advance directives. Patients want a say. Their wishes just do not reach the bedside." |
| **1:20–1:45** Users | Slide 4, then the phone's sign-in screen with Patient / Family / Doctor. | "Sahara is for four people: the patient at home, the family who often has to decide, the palliative or oncology doctor who leads the conversation, and the emergency doctor who needs the answer in minutes. One app, three sign-ins, five languages." |
| **1:45–3:35** Goals of care, live | **Phone, doctor side.** Open the patient → Goals of care → consent screen. Tick consent. Press Record. Cut to the two people talking (10–15 s of the mock conversation), back to the phone: Stop. The transcript appears with speaker labels. Scroll the checklist as it ticks itself. Show "You haven't spoken about these" → Continue anyway. Show the note in the legal form: Section I, then Section III (CPR, ICU, dialysis). Doctor signs with a finger; patient or family signs; the note locks. Open the PDF. | "This is the heart of Sahara. The doctor starts the goals-of-care conversation. Everyone present agrees to recording; that tick is required. Then the doctor simply talks with the patient and family. When they stop, Google Gemini turns the recording into text and labels who said what. If there is no internet or no consent for the cloud, Whisper does it on the phone itself. The checklist follows our doctors' order: consent, the patient, the family and emergency contact, CPR, dialysis, breathing support, ICU and the rest. It ticks itself as topics come up and tells the doctor what was not discussed. Gemini then places each statement on the legal goals-of-care form, word for word, Sections one to four. If someone changes their mind, the latest statement wins and the earlier one stays visible, struck through. If two people disagree, both are shown. Nothing is decided by the AI. The doctor reads, edits and signs, then the patient or a family member signs on the same phone, with a finger or a thumb. The note is locked: any later edit cancels the signatures." |
| **3:35–3:55** Emergency card | Patient side → SOS → QR card full screen. A second phone scans it and opens the emergency view. | "The signed wishes go on the patient's emergency QR card. In the emergency department, a doctor scans it and sees the wishes, the decision-maker, and the medicines, in seconds." |
| **3:55–4:45** Between visits | Patient Today screen → How are you feeling (tap Pain, the body map, the distress scale) → Medicines (tick a dose) → Labs chart → Documents → "Ask your documents" with a question and the source line → Nearby (map, a centre, Call) → family view "Caring for Rahul". | "Between visits, Sahara is a companion for the patient and family. A daily check-in records symptoms, distress from zero to ten, and where it hurts on a body map; the doctor sees the trend. Each medicine dose is a tick, with reminders. Lab results become charts. Ask a question about your reports and Sahara answers with the exact line it came from. Nearby shows palliative-care centres, ambulances and support groups, and the family sees and adds to the same record." |
| **4:45–5:05** Doctor side | Doctor home → patient list → a patient's tabs: Symptoms & distress (most severe first), Goals of care, Medicines. Schedule. | "On the doctor's side: their patients, the most severe symptoms first, the signed goals of care, medicines and the visit schedule." |
| **5:05–5:35** Technology | Slide 7 (flow diagram). | "Under the hood: an Android app in Kotlin and a Next.js website, on Supabase with row-level security. Recordings go through our server, so AI keys never sit on the phone. Gemini Flash-Lite transcribes and maps; if it is busy we fall back to the next model, then to Whisper on the device, then to typing. Google ML Kit translates and reads aloud." |
| **5:35–6:05** Progress and validation | Slide 8 (timeline), then slide 9. | "In four weeks we went from a web prototype to a native app our doctors walked through and changed. After the 3 November walkthrough we added tubes-and-lines care and clearer wording; after the legal review, the note copies the form word for word. 877 Android tests and 594 website tests pass, and eight fictional conversations, in six languages, all map correctly. We have not yet run a pilot with real patients, and we still need to measure speech accuracy on real Indian-language audio." |
| **6:05–6:20** Safety | Slide 10. | "Safety is built in: consent before recording, the doctor in charge of every word, locked and versioned notes, and only fictional patients in this demo." |
| **6:20–6:35** Next | Slide 12, then slide 13 with the team photos. | "Next, a 60- to 90-day pilot in a palliative-care clinic, measuring how many goals-of-care conversations are written and signed, and how fast wishes are found in an emergency. Sahara: so a patient's wishes reach the bedside. Thank you." |

## Words to avoid

- "AI decides", "AI recommends", "diagnoses": the AI only transcribes and fills the form.
- "Integrated with CARE" or "certified": a FHIR export exists; there is no live hospital integration.
- "Clinically validated" or "tested with patients": no pilot yet.
- Any older product name.

## After recording

- [ ] Total length under 7:00 (aim for 6:30).
- [ ] Upload unlisted (YouTube or Google Drive, "anyone with the link"); open the link in a private window while signed out.
- [ ] Paste the link into the deck (slide 13 and the video placeholder) and the two-page brief (Supporting Links), then re-export both PDFs.
- [ ] Doctors approve the sentences about them.
