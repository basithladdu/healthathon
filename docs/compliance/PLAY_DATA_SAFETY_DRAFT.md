# Google Play Data safety: draft answers for Sahara

_Draft, 27 Oct 2026, compliance lane. Not entered in Play Console. Nothing here has been submitted._

Based on the code on `origin/main` (commit `1304b6d`), Android `0.6.0 (7)` (`android/app/build.gradle.kts:20-21`), and the code audit in [`docs/research/handoff-2026-10-27/sahara-data-flow-audit.md`](../research/handoff-2026-10-27/sahara-data-flow-audit.md). Each answer below gives its source. The answers already saved in Play Console on 24 Oct are recorded in [`android/release/store-draft.json`](../../android/release/store-draft.json) (`dataSafetyDraft`). This draft says where it differs from them.

## Open questions (answer these before submitting)

1. **Which server does the next Play build use?** Code on main points to the Saanthvana Supabase project (`CareCloud.kt:40-41`, comment dated 26 Oct). `AI_HANDOFF.md:31` says the Play 0.6.0 build still uses an older project that is not in the owner's Supabase account. The answers below hold for both, but the region and who controls the data are different. **[owner to confirm]**
2. **Care-contact phone numbers.** Signed-in users can save care contacts (name, relation, role, phone) to the server (`DoctorCareData.kt:73`; `CareAccount.kt:260` `CareContactManager`). The saved Play form does not declare "Phone number". This draft recommends adding it. **[owner to decide]**
3. **Clinician registration details.** A doctor who applies sends name, medical council and registration number (`CareSharedCloud.kt:69`). The saved form has no "Other info" entry. This draft recommends adding it. **[owner to decide]**
4. **Doctor test workspace upload.** The test workspace with fictional patients uploads a test recording or file to a private storage bucket after a tick box (`DoctorConnectedStore.kt:28`; `ConnectedDoctorUi.kt:330-340, 520`; `CareCloud.kt:161-180`). If a real user can reach this in the release build, Audio and Files must be declared. **[verify in the release build]**
5. **`ACCESS_NETWORK_STATE`.** `store-draft.json` lists it, but the app manifest on main does not (`AndroidManifest.xml:2-8`). It may come from a library in the merged manifest. The send-later queue adds it directly. **[verify in the merged manifest]**
6. **Costs.** Doctor invites include a "Costs" permission (`CareAccount.kt:240`). Expenses are web-only per `HANDOFF.md` (D7, D10). If Android saves cost entries, "Financial info: Other" applies. **[verify]**
7. **Google ML Kit metrics.** Kept as "collected, required" to match the saved form and the website policy. Check them against Google's current ML Kit data-disclosure page before submitting. **[verify]**
8. **Data fiduciary name** in the policy linked from Play. **[owner to confirm: company or new entity]** (see `PRIVACY_POLICY_DRAFT.md`).

## Section 1: Data collection and security

| Play question | Draft answer | Source |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Signed-in records go to Supabase (`CareCloud.kt:41, 70-92`); ML Kit SDK metrics (`build.gradle.kts:81-83`) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | `usesCleartextTraffic="false"` (`AndroidManifest.xml:15`); all endpoints are `https://` (`CareCloud.kt:41`, `CentreMap.kt:75`) |
| Which of the following methods of account creation does your app support? | **Username and password** (email + password) | `CareCloud.kt:45-56` (`/auth/v1/signup`, `/auth/v1/token?grant_type=password`) |
| Account deletion URL | `https://sahara.wedevit.in/delete-account` | `res/values/strings.xml:5`; `prototype/app/delete-account/page.tsx` |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Per-person removal deletes records on the server (`CareCloud.kt:73`, `removePerson`). Whole-account deletion today is an email request from the web page (`delete-account/page.tsx`). The in-app "Delete account" button opens that page (`CareAccount.kt:263`); it does not delete by itself. |
| Specific-data deletion URL | `https://sahara.wedevit.in/delete-account#specific` | `store-draft.json` `specificDataDeletionUrl`; `delete-account/page.tsx` section "specific" |
| Independent security review | **No** | `store-draft.json` `independentSecurityReview: false` |
| Is your app designed for children / Families policy? | **No** [owner to confirm] | No child-specific flows in code |

## Section 2: Data types

Key:
- **Optional** = the app works without it. Every care feature works without signing in, and then nothing goes to the server (`CareModel.kt:22, 98`; audit "What goes to the server").
- **Shared** = sent to a third party that is not a service provider and not by user choice. Supabase acts for Sahara as a service provider, so it is not "sharing". Invites and Android share-sheet exports are user-initiated, which Google excludes from "sharing" (`store-draft.json` `sharingAssessment`).

### Location
| Type | Collected | Shared | Optional? | Purposes | Source and notes |
|---|---|---|---|---|---|
| Approximate location | **Yes** | No | Optional | App functionality; Fraud prevention, security and compliance | The app has no location permission (`AndroidManifest.xml:7-8` removes both). Declared only because Supabase logs can infer city or country from the IP address. Kept to match the saved form (`store-draft.json` `location`). |
| Precise location | No | No | — | — | No GPS permission. The hospital map pin is on the website, not in the app. |

### Personal info
| Type | Collected | Shared | Optional? | Purposes | Source and notes |
|---|---|---|---|---|---|
| Name | **Yes** | No | Optional | App functionality | Person's name (`CareCloud.kt:70-71`); family confirmation name and relationship (`CareCloud.kt:82-91`); doctor and participant names in notes (`CareCloud.kt:81`); clinician applicant name (`CareSharedCloud.kt:69`); care contact names (`DoctorCareData.kt:73`) |
| Email address | **Yes** | No | Optional | Account management; App functionality | Sign-in (`CareCloud.kt:45-56`). Owners see invited members' emails (`CareCloud.kt:103-108`) |
| User IDs | **Yes** | No | Optional | Account management; App functionality | Supabase user ID kept in the encrypted session (`CareCloud.kt` `rememberSession`) |
| Address | No | No | — | — | No address field is uploaded. Free text could contain one; see "Redaction" in Section 4. |
| Phone number | **Yes (new; recommended)** | No | Optional | App functionality | Care contacts' phone numbers saved to `healthathon_care_contacts` (`DoctorCareData.kt:73`). Not in the saved form: open question 2. |
| Race and ethnicity; Political or religious beliefs; Sexual orientation | No | No | — | — | No such fields |
| Other info | **Yes (new; recommended)** | No | Optional | App functionality; Fraud prevention, security and compliance | Clinician's medical council and registration number, used for staff verification (`CareSharedCloud.kt:69`; `20261019151322_shared_care_clinician_review.sql:123-129`). Open question 3. |

### Financial info
| Type | Collected | Notes |
|---|---|---|
| User payment info; Purchase history; Credit score; Other financial info | **No** [verify] | No payments in the app. "Costs" is an invite permission only (`CareAccount.kt:240`): open question 6. |

### Health and fitness
| Type | Collected | Shared | Optional? | Purposes | Source |
|---|---|---|---|---|---|
| Health info | **Yes** | No | Optional | App functionality | Medicines, visits, lab values, symptom and diary entries (`CareCloud.kt:74-76`, `CareModel.kt:552`); goals-of-care notes (`CareCloud.kt:81`); conversation drafts sent for review (`CareSharedCloud.kt:87`); medicine courses and taken/missed events (`MedicineHistoryRepo.kt:62, 84`); prescription groups (`DoctorPrescriptionRepo.kt:55`) |
| Fitness info | No | — | — | — | No fitness data |

### Messages
| Type | Collected | Notes |
|---|---|---|
| Emails; SMS or MMS; Other in-app messages | **No** | No messaging feature. Drafts sent to a doctor are declared as Health info and Other user-generated content. |

### Photos and videos
| Type | Collected | Notes |
|---|---|---|
| Photos; Videos | **No** | Report photos and PDFs are saved in app-private storage and never uploaded (`CareReports.kt:18`; `CareModel.kt:568-574`). Text is read on the phone (ML Kit bundled models, `build.gradle.kts:81-82`). Online photo reading is switched off (`MedicineOrganizer.kt:431-436`; `CareRemoteAssist.kt:28-30`). Exception: the doctor test workspace (open question 4). |

### Audio
| Type | Collected | Notes |
|---|---|---|
| Voice or sound recordings; Music files; Other audio files | **No** | Recordings are saved in app-private storage (`CareModel.kt:625, 710`). Cloud transcription is off in debug and release (`build.gradle.kts:37, 46`; checked at `ConversationTranscriber.kt:82`, `CareModel.kt:752`). Speech-to-text runs on the phone. Exception: the doctor test workspace (open question 4). |

### Files and docs
| Type | Collected | Notes |
|---|---|---|
| Files and docs | **No** | Same as Photos: stays on the phone (`CareReports.kt:18`; `CareDocumentReader.kt:114`). A fact is uploaded only if the user copies it into an entry, and then it is Health info. |

### Calendar
| Type | Collected | Shared | Optional? | Purposes | Source |
|---|---|---|---|---|---|
| Calendar events | **Yes** | No | Optional | App functionality | Visits, tasks and due dates in entries (`entryJson` `due`, `doneOn`: `CareCloud.kt` bottom); medicine courses (`MedicineHistoryRepo.kt:62`). The app does not read the phone's calendar. |

### Contacts
| Type | Collected | Notes |
|---|---|---|
| Contacts | **No** | No contacts permission (`AndroidManifest.xml:2-8`). Contacts typed by hand are declared under Name and Phone number. |

### App activity
| Type | Collected | Shared | Optional? | Purposes | Source |
|---|---|---|---|---|---|
| App interactions | **Yes** | No | Required | Analytics | Google ML Kit usage metrics. ML Kit starts with the app and has no opt-out, so it is marked required (`store-draft.json` `optionalReason`). Open question 7. |
| In-app search history | No | — | — | — | Centre search runs on the bundled list (`CareCentreDirectory.kt`; assets `india.json`) |
| Installed apps | No | — | — | — | — |
| Other user-generated content | **Yes** | No | Optional | App functionality | Note text, conversation words and sections (`CareCloud.kt:81`; `CareSharedCloud.kt:87`) |
| Other actions | No | — | — | — | — |

### Web browsing
| Type | Collected | Notes |
|---|---|---|
| Web browsing history | **No** | Links open in the user's browser (`CareUi.kt:913`; `CareSharedUi.kt:90`). |

### App info and performance
| Type | Collected | Shared | Optional? | Purposes | Source |
|---|---|---|---|---|---|
| Crash logs | **No** | — | — | — | No crash-reporting SDK in `build.gradle.kts:65-94` (no Firebase Crashlytics, Sentry or similar) |
| Diagnostics | **Yes** | No | Required | App functionality; Analytics; Fraud prevention, security and compliance | ML Kit technical diagnostics (`build.gradle.kts:81-83`); matches the saved form |
| Other app performance data | No | — | — | — | — |

### Device or other IDs
| Type | Collected | Shared | Optional? | Purposes | Source |
|---|---|---|---|---|---|
| Device or other IDs | **Yes** | No | Required | App functionality; Fraud prevention, security and compliance; Analytics | ML Kit per-installation identifier; matches the saved form |

### Ephemeral processing
Answer **"No"** to "Is this data processed ephemerally?" for every type above. Records stay on the server until deleted, and the saved form makes no ephemeral claim (`store-draft.json` `optionalReason`).

## Section 3: Network calls that are not data collection (for the reviewer notes)

| Destination | What it receives | Source |
|---|---|---|
| OpenFreeMap (map tiles) | IP address and the map area viewed | `CentreMap.kt:75` |
| Google (ML Kit Hindi language pack download, when the user asks) | IP address; download request. Care text is translated on the phone. | `CareOfflineTranslation.kt:16-24` |
| Hugging Face (optional speech model download) | IP address; model file requested. No audio or text. | `OfflineSpeechRules.kt:17` |
| `sahara.wedevit.in/api/care-assist`, `/api/medicine-organizer` | Nothing. Blocked in code: `externalAiEnabled = false`. | `CareRemoteAssist.kt:28-30, 44-45`; `MedicineOrganizer.kt:431-436` |
| Supabase Edge Function for cloud transcription | Nothing. `CLOUD_AUDIO_ENABLED = false`. | `build.gradle.kts:37, 46` |

Other points:
- No advertising SDK and no analytics SDK other than ML Kit's built-in metrics (`build.gradle.kts:65-94`).
- App data is excluded from cloud backup and device transfer (`AndroidManifest.xml:13`; `res/xml/data_extraction_rules.xml`).
- The sign-in token is encrypted with Android Keystore (`CareCloud.kt` `seal`/`key`).
- The in-app feedback form stays on the phone (`FeedbackStore.kt:7`).
- The offline emergency QR card on main contains plain text and makes no network call (`EmergencyCard.kt:12-15`). Anyone who scans the printed card can read it. Because the user shows or prints it, this is not collection.

## Section 4: Changes in progress (in progress, verify before submitting)

None of these are on `origin/main`. Re-check each against the merged code before you change the Play form.

| Change | Where it is now | What it would change in this form |
|---|---|---|
| **Send-later queue.** Saves on the phone first and uploads when the internet returns. | Uncommitted work in the `sa4-queue` worktree (`CareSyncQueue.kt`; adds `ACCESS_NETWORK_STATE` to the manifest) | No new data types. Update the policy wording: today a signed-in save without internet fails (`CareModel.kt:552`). With the queue, records wait on the phone and then upload. Recheck open question 5. **In progress, verify before submitting.** |
| **Redaction before upload.** Replaces phone numbers, emails, Aadhaar-like 12-digit numbers, PAN and PIN-code addresses with tokens such as `[phone]`. Names and photos are not changed (owner decision). | Branch `sa4-privacy`, commit `086e591` (`CarePrivacy.kt`). Covers symptom check-ins and shared conversation drafts only. | No change to the declared types. Health info, Name and Phone number (care contacts) are still collected, and other entries, notes and contacts are not redacted. Do not claim "anonymised" or "de-identified" in Play or the policy. **In progress, verify before submitting.** |
| **Emergency QR server link.** A revocable, read-only web link to a copy of the emergency card (name, latest confirmed goals, emergency contacts, medicines). It lasts 365 days, and a new link turns the old one off. | Branch `sa3-qr`, commit `b2b2456` plus uncommitted app changes. Migration `20261027190000_emergency_card_links.sql` is **not applied**; the page is **not deployed**. | Card data is stored on the server (Name, Health info, Phone number: already declared if open question 2 is accepted). Anyone holding the link can read it with no sign-in. That is user-initiated sharing, not "shared", but the policy must say it plainly. View counts are stored. **In progress, verify before submitting.** |
| **Account deletion in the app.** `healthathon_delete_my_account` lets the signed-in person delete their Sahara data and sign-in. | Uncommitted in the `sa4-account` worktree (migration `20261027210000_sahara_account_lane.sql`, "OWNER APPLIES") | Once applied and shipped, the deletion answer becomes "in the app and by web request". Keep the web URL anyway, because Play requires it. **In progress, verify before submitting.** |

## Section 5: Differences from the form saved on 24 Oct (`store-draft.json`)

| Item | Saved form | This draft |
|---|---|---|
| Phone number | Not declared | Add (care contacts). Open question 2. |
| Other info | Not declared | Add (clinician registration). Open question 3. |
| Everything else | Name, Email, User IDs, Health info, Calendar, Other UGC, Approximate location, Device IDs, Diagnostics, App interactions | Same |
| Deletion | Web request | Same today. In-app deletion is in progress. |
