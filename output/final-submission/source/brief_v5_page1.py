# ---------------- Page 1, left: About the Team + Problem ----------------
# Problem Statement, Users and setting, AI Solution Description and Key Features are Dr Sujay's
# own text (Problem_Statement_Sujay_2026-11-07.docx, "My edits", 7 Nov), word for word.
P1 = BODY_PT - 0.5
left = slide1.shapes[3]
olds = list(left.text_frame._txBody.findall(qn('a:p')))
tA, tPlain, tProbBody = olds[2], olds[7], olds[19]
plan = [('keep', 0)]
plan.append(('new', make_para(tA, [S('Team Zeros and Ones. ', b=True), S('Shaik Abdul Basith (technical lead; Founder, WeDevIT Private Limited, wedevit.in) and Shaik Muhammad Awaiz (Android engineer, WeDevIT) built Sahara. Dr Sujay Halkur Shankar (pulmonologist, Tata IISc Medical School) defined the problem statement, evidence and solution, distilled the goals-of-care form, designed the hospital survey, tested the whole app (3 Nov), designed the 13-topic checklist and guided conversation (7 Nov) and led the clinical wording review. Dr Sharada Vinod Kutty (pulmonologist, AIIMS Mangalagiri) wrote Saanthvana v1.0, the goals-of-care structure the app grew from, the clinical specification and the comfort-care rule, designed the goals-of-care document and reviewed the symptom list and survey. Shirin Ayub (medical student) designed the symptom log, defined caregiver portal needs, researched caregiver stress for the wellbeing check and gave UI and usability inputs. Legal advice was sought: inputs and refining the goals of care wording and format.')], autonum=True, space_before=4, size=P1)))
plan.append(('new', make_para(tPlain, [S(' ')], size=4)))
plan.append(('keep', 17))
SUJ_PROB = [
    [S('98% of India’s metastatic (advanced) cancer patients had unmet palliative-care needs (Patil, BMJ Support Palliat Care 2024), and timely referral was 30–37% at two centres (Venketeswaran, BMJ Support Palliat Care 2026). We identified the following gaps in palliative care to solve:')],
    [S('Goals of care: ', b=True), S('wishes about CPR, ventilation, ICU or place of death are rarely recorded in clinical documents – and the utilization of advanced directives are negligible. In one hospital, only 41% of cancer patients dying on active treatment had end-of-life care documented (Das, Cancer Med 2026), though 88% of urban adults want this choice (Living Will Survey, IndiaSpend 2019).')],
    [S('Symptoms: ', b=True), S('symptoms in advanced cancer change between visits and are often missed; Patients reporting symptoms online had improved quality of life and fewer emergency visits (Basch, J Clin Oncol 2016).')],
    [S('Pain relief and help: ', b=True), S('India uses 0.11 mg morphine per person vs 6.27 mg globally (Rajagopal, J Glob Oncol 2017); families rarely know where to find morphine, palliative centres, crisis care or support groups.')],
    [S('The result: ', b=True), S('a 62-year-old woman with advanced breast cancer tells her doctor her wishes. Two months later she is unconscious in the emergency department, her children disagree about CPR, and nobody can find what she said.')],
    [S('Sahara helps patients and caregivers align care with their values and needs.', b=True)],
    [S('Users and setting: ', b=True), S('Patients receiving palliative care and their families - at home; their palliative care physician, oncologist or pulmonologist - in the clinic (as an extension plugin to their electronic medical records system); and the emergency physician, who has only minutes to decide and needs the patient’s wishes immediately.')],
]
for segs in SUJ_PROB:
    plan.append(('new', make_para(tProbBody, segs, space_before=3, size=P1)))
rebuild(left, plan)

# ---------------- Page 1, right: AI solution, technology, features ----------------
right = slide1.shapes[5]
olds = list(right.text_frame._txBody.findall(qn('a:p')))
tBody = olds[2]
bullet_src = list(slide2.shapes[2].text_frame._txBody.findall(qn('a:p')))[2]  # template bullet paragraph
plan = [('keep', 0)]
SUJ_AI = [
    [S('Sahara is a palliative-care companion (Android and web) with separate patient and doctor interfaces.')],
    [S('Goals of care: ', b=True), S('After the patient and/or caregivers consent, the doctor leads the conversation and records it on the doctor portal. Gemini AI transcribes the audio, labels who said what, and fills Sahara’s advance care planning form (a clinical, not legal, document). A checklist ticks off topics as covered and flags any missed. The doctor corrects the draft; doctor and patient or family sign on the phone. It then locks; edits require re-signing, and all versions are stored. The signed wishes open from the patient’s emergency QR code.')],
    [S('Symptoms and reports: ', b=True), S('Patients log symptoms, distress and pain at home, shown on the doctor portal as trends. Patients can upload documents that Gemini categorizes (as prescriptions, bills, reports) and answers questions from (“When is my next follow-up?”). Tablet strip photos and prescriptions are converted to an easily usable pill tracker.')],
    [S('Pain relief and help: ', b=True), S('Sahara finds the nearest palliative centre, morphine-dispensing hospital, emergency hospital and support groups.')],
    [S('The AI only records, flags and retrieves; every clinical decision stays with the doctor.')],
]
for segs in SUJ_AI:
    plan.append(('new', make_para(tBody, segs, space_before=2, size=P1)))
plan.append(('new', make_para(tBody, [S(' ')], size=3)))
plan.append(('keep', 6))
plan.append(('new', make_para(tBody, [S('Android (Kotlin, Compose) and web (Next.js, Vercel); '), S('Supabase', b=True), S(' with row-level security (each family sees only its own records); '), S('Google Gemini', b=True), S(' with backup models; '), S('OpenAI Whisper', b=True), S(' offline on the phone; Google ML Kit for 5 languages.')], space_before=2, size=P1)))
plan.append(('new', make_para(tBody, [S(' ')], size=3)))
plan.append(('keep', 14))
SUJ_FEAT = [
    ('Goals-of-care record: ', 'consent, record, transcribe, fill the goals of care form, flag missed topics, sign and lock; every version kept; shared with invited family, each with their own access level.'),
    ('Emergency QR code and SOS: ', 'wishes, contacts and medicines in one scan.'),
    ('How I feel: ', 'symptoms, a 0–10 distress scale and a pain body map; trends on the doctor portal.'),
    ('Medicines and documents: ', 'pill tracker from prescription and tablet-strip photos, dose ticks, reminders; uploaded clinical documents categorized and questions answered.'),
    ('Help nearby: ', 'palliative centres, morphine-dispensing and emergency hospitals, ambulances, support groups.'),
]
EXTRA_FEAT = [
    ('Conversation guide (new): ', 'Dr Sujay’s 13-topic checklist (capacity, existing directives, values, CPR, ICU, ventilatory support, dialysis, nutritional support and more); in “Guide me” or “Free flow” the doctor taps topics while recording.'),
    ('Caregiver wellbeing (new): ', 'a short check for family caregivers, flagged with Tele-MANAS (14416) and support groups when stress rises; about 1 in 5 caregivers has burnout (Cleveland Clinic and other surveys).'),
]
for head, rest in SUJ_FEAT + EXTRA_FEAT:
    plan.append(('new', make_para(tBody, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=1, size=P1)))
rebuild(right, plan)

