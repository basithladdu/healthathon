# Health-a-thon 2026 problem-statement decision matrix

## Direct answer

The authenticated Health-a-thon dashboard was inspected read-only on 24 August 2026. The registered selection is:

**Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care**

There is no visible self-service edit control on the dashboard. Treat this as the working category unless the organizers explicitly change it.

This is not a bad lock. It ranks first in the independent comparison because it is measurable, buildable with low integration, and safely assistive. Do not change it merely because Dr. Sujay's PDF was written before the selection was known.

The original PDF maps most naturally to **Consultation Readiness & Patient Journey Review** because its main failure is documenting and retrieving a goals-of-care record. It can honestly fit the locked follow-up category only if tomorrow's meeting confirms a recurring longitudinal failure: planned conversations or reviews are missed, delayed, incomplete, or not revisited as circumstances change.

Recommended first branch: **Goals-of-Care Continuity Loop** under the locked use case. Clinicians enroll patients and set follow-up dates; coordinators close due work; AI only drafts a source-grounded summary for physician verification. If that recurring workflow does not exist, use the broader **Cancer Follow-up Recovery Worklist**. If rapid retrieval of an already-completed record is the only real problem, ask the organizers to switch to Consultation Readiness rather than forcing the fit.

## Source-quality observations

- The raw text has 651 lines and appears to concatenate the homepage, guide/FAQ and use-case pages from `https://healthathon.reskilll.com/`.
- Character encoding is damaged in places, but the substantive text is readable.
- The six patient/caregiver use cases are listed correctly once, then some detailed sections are duplicated or appear out of numerical order near the end. The canonical list at the start of the section should be used.
- The raw capture states the official guardrail, timeline, team structure, pilot bar and all 13 use cases.
- It does not establish judging weights beyond the published design principles.
- It does not expose the logged-in dashboard's selected problem statement.
- The logged-in dashboard does expose the selection and currently shows no edit control.
- The dashboard shows registration and matchmaking through 25 September, conflicting with earlier dates in the raw capture. This schedule discrepancy should be confirmed with the organizers.

## Scoring method

These are decision-support scores, not empirical measurements. Each problem statement is scored from 0 to 5 on seven factors and converted to a weighted total out of 100.

| Factor | Weight | Meaning |
| --- | ---: | --- |
| Clinical access and ownership | 20 | Whether this team has a clinician who owns the workflow and can validate or pilot it. |
| Assistive-scope safety | 15 | Ability to avoid diagnosis, treatment recommendations, risk scoring and clinical decision support. |
| Five-week buildability | 15 | Ability to produce a credible working prototype without unavailable integrations. |
| 60-90 day measurability | 15 | Ability to move and measure one operational KPI quickly. |
| Problem intensity and frequency | 15 | How often the problem occurs and how consequential the operational failure is. |
| Differentiation | 10 | Distance from mature EMR, CRM, scribe, chatbot and PHR products. |
| Legitimate AI role | 10 | Whether AI adds useful assistance without being decorative or unsafe. |

## Team-adjusted ranking

| Rank | Problem statement and best current angle | Score | Verdict |
| ---: | --- | ---: | --- |
| 1 | Patient Follow-up & Continuity of Care - Goals-of-Care Continuity Loop if validated; otherwise Cancer Follow-up Recovery Worklist | 82 current / 88 if the specific workflow and pilot are confirmed | Locked choice and strongest execution path. The narrow GOC continuity wedge prevents it from becoming generic reminder software. |
| 2 | Consultation Readiness & Patient Journey Review - Goals-of-Care Relay | 80 current / 87 if access is confirmed | Best category for the PDF as written, but worth a switch only if retrieval is the real primary failure and follow-up is not. |
| 3 | Doctor Productivity & Knowledge Assistant - generic scribe/content support | 75 | Easy to build, but mature competitors make a generic entry weak. |
| 4 | Healthcare Navigation & Access - institution-specific cancer journey navigator | 74 | Strong only when narrowed to one real hospital journey; generic chatbot version is weak. |
| 5 | Clinic Operations & Patient Flow - OPD flow and cancellation recovery | 71 | Highly measurable but heavily served by hospital-management products. |
| 6 | Family & Caregiver Support - consented shared task and information layer | 70 | Relevant and less crowded, but governance and outcome measurement are harder. |
| 7 | Financial & Administrative Support - cancer scheme/document navigator | 70 | High need and safe scope; rule maintenance, eligibility accuracy and institutional specificity are burdens. |
| 8 | Patient Education & Digital Engagement - clinician-approved multilingual content | 67 | Buildable but crowded, with significant accuracy and approval burden. |
| 9 | Long-term Care Engagement - adherence and milestone support | 65 | Important but broad; short-pilot impact and differentiation are weak. |
| 10 | Clinic Performance & Practice Growth - practice analytics/CRM | 64 | Easy to demonstrate, but weak clinical-impact narrative and mature competition. |
| 11 | Care Journey Companion - stage-aware assistant | 63 | High user need, but broad guidance easily crosses into clinical advice. |
| 12 | Patient Health Journey Progress - longitudinal patient view | 62 | Useful but overlaps PHR products and depends on fragmented data integration. |
| 13 | Patient Registry & Population Health - consolidated panel management | 53 | Valuable at scale, but integration burden and clinical-prioritization risk are poor for this sprint. |

## Score components

The following 0-5 ratings make the aggregate ranking reproducible. Half-points represent unresolved team-specific evidence. Totals are rounded to the nearest whole number. These remain strategic judgements, not measured programme scores.

| Problem statement | Access 20 | Safety 15 | Build 15 | Measure 15 | Frequency 15 | Differentiation 10 | AI role 10 | Weighted total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Patient Follow-up & Continuity of Care | 4 | 5 | 5 | 5 | 4 | 3 | 1.5 | 82 |
| Consultation Readiness & Patient Journey Review | 3 | 4 | 4 | 4 | 4 | 5 | 5 | 80 |
| Doctor Productivity & Knowledge Assistant | 3 | 4 | 5 | 4 | 4 | 1 | 5 | 75 |
| Healthcare Navigation & Access | 3.5 | 4 | 4 | 3.5 | 4 | 3 | 3.5 | 74 |
| Clinic Operations & Patient Flow | 3 | 5 | 4 | 5 | 4 | 1 | 1.5 | 71 |
| Family & Caregiver Support | 3.5 | 4 | 4 | 3 | 3 | 4 | 3 | 70 |
| Financial & Administrative Support | 3 | 4 | 4 | 4 | 4 | 3 | 2 | 70 |
| Patient Education & Digital Engagement | 3 | 3.5 | 5 | 3 | 3.5 | 1 | 4 | 67 |
| Long-term Care Engagement | 3 | 3.5 | 4 | 2.5 | 4 | 2 | 3.5 | 65 |
| Clinic Performance & Practice Growth | 3 | 5 | 5 | 4 | 2 | 1 | 1 | 64 |
| Care Journey Companion | 3 | 2.5 | 4 | 2.5 | 4 | 2 | 4 | 63 |
| Patient Health Journey Progress | 2.5 | 4 | 3 | 3 | 3.5 | 2 | 3.5 | 62 |
| Patient Registry & Population Health | 2.5 | 2.5 | 2 | 3 | 4 | 2 | 2 | 53 |

Validation changes only team-specific ratings, not the weights. Confirmed GOC follow-up ownership and pilot access would raise Follow-up to about 88. Confirmed documentation/retrieval ownership and pilot access would raise Consultation Readiness to about 87.

## Why the top two are close

### Patient Follow-up & Continuity of Care

Strengths:

- Explicitly encouraged by the programme.
- Clear metrics: no-show rate, recall completion, time to contact or follow-up completion.
- Can begin with Excel, appointment lists and existing communication channels.
- Human-defined operational rules can avoid clinical risk scoring.
- Easy to explain and pilot.
- Already selected in the team dashboard.
- Can retain Dr. Sujay's differentiation if recurring goals-of-care follow-up is a real workflow.

Weaknesses:

- Generic reminders, follow-up boards, WhatsApp automation and clinic CRM are already common.
- A weak version looks like appointment-reminder software with an AI label.
- No clinician has yet supplied a specific recurring follow-up failure, patient cohort, baseline or pilot site.

It is the working winner. It becomes a strong differentiated entry if the clinicians validate a Goals-of-Care Continuity Loop; otherwise it remains a safer but more generic cancer follow-up worklist.

### Consultation Readiness through Goals-of-Care Relay

Strengths:

- A specific problem proposed by a clinician rather than a solution invented before workflow discovery.
- Direct Indian cancer-centre evidence validates standardized documentation and identifies digital portability as an unresolved gap.
- Clear operational metrics around completion and retrieval.
- Strong human-control, provenance, consent and versioning story.
- More memorable and defensible than a generic scribe or reminder engine.

Weaknesses:

- The original PDF wording crosses close to emergency clinical decision support.
- Access, legal meaning, governance, eligible patient volume and pilot setting are unconfirmed.
- AI transcription can alter nuanced values or preferences.
- Emergency access is technically and institutionally sensitive.

It becomes the better category only after the clinicians show that documentation and retrieval, not recurring follow-up, is the central failure and the team confirms workflow ownership and access.

## Sensitivity analysis

The implementation choice changes under five facts:

1. **Confirmed recurring goals-of-care follow-up plus pilot access:** Goals-of-Care Continuity Loop under the locked use case moves to approximately 88 and is the recommended winner.
2. **No recurring GOC follow-up, but a broader high-volume cancer follow-up failure exists:** build Cancer Follow-up Recovery Worklist under the locked use case.
3. **Only documentation and rapid retrieval are real:** ask the organizers to switch to Consultation Readiness; Goals-of-Care Relay moves to approximately 87 if pilot access is also confirmed.
4. **No relevant pilot access or representative workflow:** do not build a GOC product; use a better-evidenced follow-up workflow from the clinicians.
5. **Clinicians expect the record to direct treatment or carry legal authority:** the concept becomes a no-go unless substantially reframed.

## Locked-choice decision rule

The locked use case is **Patient Follow-up & Continuity of Care**. Keep it for the meeting.

- Choose **Goals-of-Care Continuity Loop** if there is a repeatable missed, delayed, incomplete or unrevisited conversation workflow with named owners, adequate volume and pilot access.
- Choose **Cancer Follow-up Recovery Worklist** if there is a stronger general follow-up failure but the GOC continuity hypothesis fails.
- Ask for **Consultation Readiness & Patient Journey Review** only if the doctors prove that retrieving an already-completed record is the primary problem and follow-up is not.
- Abandon the GOC direction if it depends on automated clinical eligibility, treatment recommendations, legal authority or an institution that cannot validate it.

## What would justify asking for a different problem statement

Ask to switch from Follow-up to Consultation Readiness only when all of the following are true:

- The recurring follow-up hypothesis is false or too weak to measure.
- The doctors can demonstrate a frequent, material failure to retrieve the latest completed record.
- They own or can validate the oncology, palliative-care or emergency workflow.
- The product can remain a physician-verified summary rather than a treatment instruction or legal directive.
- The organizers confirm that a category change is permitted.

Do not switch merely because Consultation Readiness is a cleaner semantic label for the PDF. A category change only makes sense when the underlying observed workflow also supports it.

## Current recommendation before the meeting

Do not tell Dr. Sujay that the team selected the wrong problem statement. That conclusion is false.

Tell him the team is registered under Patient Follow-up & Continuity of Care and sees a potentially stronger version of his idea: a closed loop for planned, unfinished and repeated goals-of-care conversations, followed by a physician-verified record. Ask him to prove that this continuity failure occurs in real practice. Leave the call with one of four explicit decisions: pursue that concept, pursue broader cancer follow-up, request Consultation Readiness, or stop the GOC direction.
