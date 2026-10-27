# Sahara data flow: what goes to the server (code audit)

_Research agent report, 27 Oct 2026. Unedited except for this header._

The docs and deck no longer say Sahara is offline-only, and the change is pushed to main as `fbcdf451ec5a02c94a111e6e21481a5018bd856a`. Author and committer are both basithladdu <basithladoo@gmail.com>, with no co-author; it was rebased and pushed fast-forward under `flock /tmp/sahara.lock`, without force.

One thing the code contradicts: the "data is saved on the phone first, then syncs" wording isn't accurate for signed-in users. A signed-in save goes to the server first and only then to the phone's local database (`CareModel.kt:552`). With no internet, the save fails with "Couldn't save. Your entry is still here." There is no offline "send later" queue. The docs say this instead.

**What goes to the server.** Without an account, nothing does (`cloud == null`, `CareModel.kt:21,98`). When signed in, this goes to Supabase project `<sahara-supabase-project>` (`CareCloud.kt:41`). WORK_STATUS.md says all 14 server migrations are applied.

| Data | Table | When | Source |
|---|---|---|---|
| Person's name | `healthathon_people` | When the person is added | `CareCloud.kt:70-71`; `CareModel.kt:282` |
| Medicines, visits, tasks, lab values, symptom and diary entries (as JSON) | `healthathon_records`, kind `entry` | Every save | `CareModel.kt:552`; `CareCloud.kt:74-92` |
| Published notes, including `personName`, doctor and participants | `healthathon_records`, kind `note` | Every save (`CareCloud.kt:81`) | |
| Family confirmations (name, relationship, hash) | `healthathon_records`, kind `ack` | When confirmed | `CareCloud.kt:82-91`; `CareModel.kt:464` |
| Conversation drafts: source words and reviewed sections | `healthathon_care_drafts` | Only on explicit save or send to clinician | `CareModel.kt:375-391, 426-449`; `CareSharedCloud.kt:87` |
| Medicine courses and taken/missed events | `healthathon_medicine_courses`, `healthathon_medicine_events` | Each change | `MedicineHistoryRepo.kt:62,84` |
| Prescription groups | `healthathon_prescriptions` and related tables | Each save | `DoctorPrescriptionRepo.kt:55` |
| Care contacts (name, relation, role, phone) | `healthathon_care_contacts` | Each save | `DoctorCareData.kt:73` |
| Doctor's verification request (name, council, registration number) | `healthathon_clinician_requests` | When a doctor applies | `CareSharedCloud.kt:69` |
| Family invites (owner chooses role, edit right, permissions, 1–365 days) | Invites table; members join through a link | When the owner invites | `CareCloud.kt:93-102`; `CareInviteLink.kt:11` |

**Who can read it (row-level security).**
- Only the owner or invited members can read anything (`can_read`/`can_edit`, `20261009162933_android_care_accounts.sql:47-77`).
- Each permission (notes, calendar, reports, costs, care team) is checked per record (`has_permission` and `can_read_record`, `20261019151322_shared_care_clinician_review.sql:117-155`).
- A doctor gets access only after Sahara staff approve the doctor's registration (`is_care_clinician`, same file 123-129).
- Doctors see drafts only once they are submitted (same file 222-224).
- Sahara's own server admins can see everything.

**What stays only on the phone.**
- **Recordings:** saved in the app's private storage (`CareModel.kt:709`). Cloud transcription is off in both debug and release builds (`build.gradle.kts:37,46`).
- **Report files and photos, and their scanned text:** saved in the app's private storage too (`CareReports.kt:18`; `CareModel.kt:568-574`; `CareDocumentReader.kt:114`). They are never uploaded unless someone copies a fact into an entry.
- **Speech-to-text, scanning and translation:** run on the phone. Online AI is switched off (`CareRemoteAssist.kt:28-30`; `MedicineOrganizer.kt:431-436`).
- **Signed-in copy:** the phone keeps a copy of server records, readable offline, and reminders still ring (`CareModel.kt:196-205`).
- **Exception:** a separate doctor test workspace with fictional patients only (`DoctorConnectedStore.kt:28`) uploads a test recording or file to a private storage bucket. It does this only after a tick box (`ConnectedDoctorUi.kt:330-340, 520`).

**Anonymising: planned, not built.** Nothing anonymises, redacts or blurs anything today; searching the Kotlin, SQL and TS found no such code. The server receives the real name and the text exactly as typed. To build it would take a pseudonymous patient ID instead of the name on doctor views, and stripping names, phone numbers and addresses from text before upload. It would also need faces and ID numbers blurred in any photo before upload, though no photos are uploaded today, plus a DPDP review. A treating doctor usually needs to know who the patient is, so anonymising mainly makes sense for outside reviewers or AI processing. I flagged this in the docs; the owner should decide the scope.

**Files changed** (all under `/home/user/healthathon/`):
- `docs/research/CARE_VS_SAHARA_FOR_TEAM.md`: three new terms in the word list, a rewritten "Works offline" row and the decision bullet about it, and a new "Where Sahara's data lives" section with the full table and file:line sources.
- `docs/research/CARE_FAMILY_SIDE_FIT.md`: a clarification on the audio row and a short "where this data lives" note.
- `docs/research/CARE_BRIEF_2026-10-27.md`: the offline-first line corrected.
- `docs/research/CARE_DEEP_DIVE_2026-10-27.md`: not changed, because it makes no offline claim.
- `CLAUDE_TRANSCRIPT.md`: a dated note of the owner's correction.
- `HANDOFF.md`: two new open items. "Anonymise before sharing with the doctor" (owner decides scope, Awaiz builds) and a DPDP note (owner).
- `docs/presentations/src/care-team-briefing.html`: slide 4 row renamed "Works without internet" and slide 6 "Where data lives" row rewritten. There were no other offline claims in the deck.
- `docs/presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf`: rebuilt, still 9 pages. I rendered slides 4 and 6 and looked at them; there is no overflow.
