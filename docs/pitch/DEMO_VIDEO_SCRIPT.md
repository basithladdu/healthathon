# Demo video script: narrated walkthrough (HANDOFF H13)

**Status: DRAFT, 27 Oct 2026.** For the owner and Awaiz to record. Length: about 3 min 45 s (about 520 spoken words at a calm pace).

## What the organiser asks for (27 Oct final-deliverables email)
Source: `source-materials/healthathon-phase-2/2026-10-27-final-deliverables/organiser-email.txt` and its `README.md`.
- [x] Introduce the team. → Part 1
- [x] Explain how we approached the problem and built the solution. → Parts 1–2
- [x] Show the collaboration with our **doctor** and **technology** partners. → Part 2 (technology partner: **[team to fill, if we have one]**)
- [x] Show the working solution, its key features, and how it meets the healthcare problem. → Parts 3–9
- [x] **Narrated**, "not merely a screen recording or a sequence of screenshots". → voice over the live app, face or slide only at start and end
- [ ] The link must open for the evaluators. Test it signed out before submitting.
- The email gives **no length, format or deadline**. 3–4 minutes is our own choice.

## Honesty rules for this video
- Say **"word matching"**, never "AI", for how answers are found in the transcript. AI drafting is **off** (`LocalQuestionMatcher` in `CareDraftAssist.kt`: "Rule-based local matching … no AI, model or transcription").
- Say **"not connected to CARE"**. Never say "integrated with CARE", "FHIR-compliant" or "certified".
- The transcript in the demo is **typed or a labelled sample**, not live speech-to-text. Say so on screen.
- The confirmation is an **"exact-version confirmation"**, not a legal signature.
- Use only the synthetic walkthrough people (Rahul / Arjun) or made-up test names. No real patient data, no real phone numbers.
- Nothing has been tried on a real phone yet. **Record on a real phone** and re-check every step before the final take.

## Step status (checked against code on `main` at 1304b6d, 27 Oct)
| Step | Status | Code / note |
|---|---|---|
| Family adds the patient | **[works]** | Welcome → "Patient & family" → "Add a person" → "Who is this care for?" → Continue (`CareUi.kt`). Invite: Today → "Invite family" card → "Send invite on WhatsApp" (`CareAddFamily.kt`). Invite links open the app only after the owner publishes the app-link file and redeploys the website. |
| Daily check-in | **[works]** | Care → Daily care → + Add → check-in (`CareUi.kt`). Swipe-scroll fix landed (ebe5558). |
| Pain body | **[works]** | Symptom tracker → "How are you feeling?" → "Where does it hurt?" → tap on the 3D body (`CareSymptomUi.kt`, `CarePainBodyMap.kt`). 12 body parts only. |
| Doctor conversation recorded | **[works]** for consent + recording + typed transcript + word matching. Live goals checklist and tap-an-answer-to-jump: **[being finished in lane sa3-convo]** (branch not on main). Doctor patient search by ID: **[being finished in lane sa4-doctor]**. | `DoctorWorkflowUi.kt` (participants, consent), `DoctorRecordingReviewUi.kt` ("Record conversation", "Stop & save", "Transcript · tap to edit"), `DoctorTranscriptGoals.kt`. |
| Note | **[works]** | Review → "Continue to publish" → "Publishing creates an exact version" (`CareUi.kt`). Real doctor accounts need hand approval in the database (owner). |
| Family confirms the exact version | **[works]** | Note reader → "I have read this exact version…" → "Confirm this version". Patient confirms first, then family (`CareUi.kt`). |
| Emergency QR | **[works]** offline card on Today (7a1810a, unit-tested). Read-only web link from the QR: **[being finished in lane sa3-qr]** (branch not on main; needs an owner-applied migration and website deploy). | `EmergencyCardUi.kt`, `EmergencyCard.kt` |
| Nearby | **[works]** with a bundled directory of 1,631 listings, Call and Directions. Emergency numbers and nearest centres by state/district: **[being finished in lane sa3-nearby]** (branch not on main). | `CareCentreDirectory.kt`, `CentreMap.kt`, `CareUi.kt` |
| Name/phone removal before upload | **[being finished in lane sa4-privacy]** (not in the video; do not claim it) | — |

Re-check this table on the morning of recording: any lane merged into `main` by then moves to [works].

## Recording set-up (suggested)
- Phone A: **family** account (signed in). Phone B: **doctor** test account (owner approves it by hand first). Or use "Try the walkthrough" for both sides if accounts are not ready, and say "this is the walkthrough with sample people".
- Screen-record each phone; record the voice separately and lay it over. Large system font off, so the screen is readable.
- Opening and closing: a title card or the team on camera.

---

## Script

### Part 1. Team and problem (0:00–0:35)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 0:00 | Title card: "Sahara · Team Zeros and Ones" (+ team code **[team to fill]**) | — | "We are Team Zeros and Ones. Dr Sharada Vinod Kutty and Dr Sujay Halkur Shankar are our doctors. Shaik Abdul Basith and Shaik Muhammad Awaiz build the app. **[team to fill: specialities and roles]**" |
| 0:12 | Slide: two numbers with sources on screen | — | "In India, the median trip to the nearest palliative care centre is about two hours. Between visits, families carry the care alone. In one Kerala survey, only about one in five government sites routinely wrote down the patient's own pain score." |
| 0:25 | Slide: "What did we decide together?" | — | "And when a family has to decide fast, the talk about what the patient wants is often not written down, or not where they need it." |

Sources for the slide (put in small text): 118-minute median travel, ecancermedicalscience 2025 (`landscape/india-palliative-evidence.md`); pain documented at 21% of government sites, Lijimol, IJPC 2020, PMID 33623312 (`partners/kerala-government-grid.md`).

### Part 2. How we built it with the doctors (0:35–1:05)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 0:35 | Slide: timeline 24 Aug → 21 Sep → 3 Oct → 23 Oct | — | "We built this with our doctors from the first week. On 24 August Dr Sujay asked for a goals-of-care tool that records the talk and keeps every version. He told us it must help the process, not make decisions." |
| 0:48 | Same slide | — | "On 21 September the doctors asked for a 0-to-10 symptom diary, only vetted care centres, and one outcome: agreement between patient and family. In October Dr Sujay tested our hospital survey and Shirin **[role to confirm]** advised on the symptom tracker. **[team to fill: technology partner, if any]**" |

Sources: `docs/research/CHAT_AUDIT_2026-10-27.md` items A3, B3, B13, E1, D22, B10, D14, E2.

### Part 3. Family adds the patient (1:05–1:25)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 1:05 | Welcome screen | "Patient & family" → "Add a person" | "This is Sahara, our Android app. A family member starts here." |
| 1:12 | "Who is this care for?" | type a sample name → "Continue" | "They add the person they care for." |
| 1:17 | Today → "Invite family" card (it reads "Add family" when signed out) | pick "Daughter", "Can view and add" → "Send invite on WhatsApp" | "Then they invite family by WhatsApp. The link works once and ends in three days." |

### Part 4. Daily check-in and pain body (1:25–1:55)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 1:25 | Care → Daily care | "+ Add" → check-in → Save | "Every day the family can add a quick check-in." |
| 1:32 | Symptom tracker | "How are you feeling?" → pain → slide to 6 | "For symptoms, they rate each one from 0 to 10 and say who is answering: the patient, or a carer." |
| 1:42 | "Where does it hurt?" 3D body | turn the body, tap the lower back | "They tap where it hurts on a 3D body. The doctor sees the same map before the visit." |

### Part 5. Doctor conversation is recorded (1:55–2:35)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 1:55 | Doctor phone: patient → Conversation | add participants, tick recording consent → "Continue to document" | "Now the doctor's side. Before recording, each person in the room gives consent." |
| 2:05 | Record panel | "Record conversation" … "Stop & save" | "The doctor records the goals-of-care conversation on the phone. The audio stays on this phone." |
| 2:13 | "Transcript · tap to edit" (label on screen: "typed for this demo") | paste or type the transcript | "Live speech-to-text is not in this version, so for this demo we type the conversation." |
| 2:20 | "Goals of Care review" with "Source:" quotes | scroll the answers | "Sahara uses simple word matching to point each question to the line where it was answered. It is not AI. The doctor checks every answer and fixes it." |

### Part 6. The note (2:35–2:50)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 2:35 | Review screen | "Continue to publish" | "The doctor reads the whole note and publishes it. Each publish makes a fixed, numbered version. Older versions stay." |

### Part 7. Family confirms the exact version (2:50–3:10)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 2:50 | Family phone: Today → care note | open "Version 1" | "Back on the family phone, the new note is here." |
| 2:57 | Bottom of the note | type name, tick "I have read this exact version…" → "Confirm this version" | "The patient confirms first, then the family. They confirm this exact version. It is not a legal signature." |

### Part 8. Emergency QR (3:10–3:25)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 3:10 | Today → "Emergency card" | tick note, contacts, medicines → see the large QR | "For an emergency, the family makes a QR card. They choose what goes on it. It works without internet and is not linked to any hospital system." |

### Part 9. Nearby (3:25–3:35)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 3:25 | Nearby tab | search a city → "Call" | "Nearby lists 1,631 palliative care centres from public directories. Families should call to confirm services." |

### Part 10. Honest limits and close (3:35–3:50)
| Time | Screen | Tap | Narration |
|---|---|---|---|
| 3:35 | Slide: "What is next" | — | "Sahara is not connected to CARE or any hospital system yet. Next, we want to test it with one palliative care team, and measure confirmed notes, pain scores between visits, and caregiver burden. Thank you." |
| 3:47 | End card: MVP link, video link, code access **[owner to fill]** | — | — |

---

## Before the final take (owner + Awaiz)
- [ ] Re-check the step status table against `main`; move merged lanes to [works].
- [ ] Doctor test account approved by hand; family account signed in.
- [ ] Fill team code, roles, Shirin's role and the technology partner.
- [ ] Get the doctors to approve Part 2 wording about them.
- [ ] Check every on-screen label matches the phone (labels above are from code, not from a phone).
- [ ] Upload, set the link to "anyone with the link", open it signed out.
