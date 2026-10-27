# In-app consent screen: draft text (English and Hindi)

_Draft, 27 Oct 2026, compliance lane. Not in the app yet. Every statement traces to code on `origin/main` (commit `1304b6d`). Sources are listed below the text._

## Open questions

1. **When to show it.** Suggested: before creating an account or signing in, because without an account nothing leaves the phone (`CareModel.kt:22, 98`). Today the app shows no general consent screen. Only invitees get a recorded `consented_at` time when they join (`20261019151322_shared_care_clinician_review.sql:92`). **[owner to decide]**
2. **Who consents.** Family members often enter the patient's data. Should the patient (or a lawful guardian) confirm too? See `PRIVACY_POLICY_DRAFT.md`, open question 4. **[owner to decide]**
3. **Record the consent?** Saving the time and text version on the server needs a server change. **[owner to decide; Awaiz builds]**
4. **Server region** in the text: **[owner to confirm]** (`AI_HANDOFF.md:30` records ap-south-1, Mumbai).
5. **"Delete account" wording.** Today the button opens a web page, and the user has to send an email (`CareAccount.kt:263`). In-app deletion is **in progress, verify before submitting** (`sa4-account`). The text below works for both.
6. **Hindi** was written by an AI. A native speaker on the team must check it before use **[unverified]**.

## English (about 80 words)

**Before you sign in**

Without an account, everything stays on this phone. If you sign in, Sahara saves names, notes, medicines, visits, symptoms and family confirmations on its server in India [owner to confirm]. Recordings and report files stay on this phone.

Only people you invite can see them: family members and a doctor you invite, with the access you choose. Sahara's server staff can also see them.

You can remove anyone, delete a person's records, or delete your account at any time in Your account.

Buttons: **I agree, sign in** · **Not now, keep on this phone**

## Hindi (हिंदी) [unverified — needs a native speaker check]

**साइन इन करने से पहले**

बिना खाते के, सब कुछ इसी फ़ोन पर रहता है। साइन इन करने पर Sahara नाम, नोट, दवाएँ, मुलाक़ातें, लक्षण और परिवार की पुष्टि अपने सर्वर पर भारत में [owner to confirm] रखता है। रिकॉर्डिंग और रिपोर्ट की फ़ाइलें इसी फ़ोन पर रहती हैं।

इन्हें सिर्फ़ वही देख सकते हैं जिन्हें आप बुलाते हैं: परिवार के सदस्य और आपके बुलाए डॉक्टर, उतनी ही पहुँच के साथ जितनी आप चुनें। Sahara के सर्वर कर्मचारी भी इन्हें देख सकते हैं।

आप कभी भी "आपका खाता" में जाकर किसी को हटा सकते हैं, किसी व्यक्ति के रिकॉर्ड मिटा सकते हैं, या अपना खाता मिटा सकते हैं।

बटन: **मैं सहमत हूँ, साइन इन करें** · **अभी नहीं, इसी फ़ोन पर रखें**

## Where each statement comes from

| Statement | Source |
|---|---|
| Without an account, nothing leaves the phone | `CareModel.kt:22, 98` (`cloud == null`); audit "What goes to the server" |
| What is saved on the server | `CareCloud.kt:70-92`; `MedicineHistoryRepo.kt:62, 84`; `DoctorCareData.kt:73` |
| "in India" | `AI_HANDOFF.md:30` only (ap-south-1). **[owner to confirm]**. Remove the phrase if it is not confirmed. |
| Recordings and report files stay on the phone | `CareModel.kt:625, 710`; `build.gradle.kts:37, 46`; `CareReports.kt:18` |
| Only invited family and doctor; access you choose | `CareCloud.kt:93-102`; `CareAccount.kt:240-246`; RLS in `20261009162933_android_care_accounts.sql:47-77`, `20261019151322_shared_care_clinician_review.sql:117-155` |
| Server staff can see it | Audit: "Sahara's own server admins can see everything" |
| Remove anyone | `CareCloud.kt:109` `removeMember`; `CareAccount.kt:255` |
| Delete a person's records | `CareCloud.kt:73` `removePerson` |
| Delete your account | `CareAccount.kt:263` (opens the web request page today); in-app deletion is in progress |
