"""v3 sections: every term explained in plain words; logos on the tech slide; sprint as a calendar;
team photos only on the team slide. make_v3.py swaps these blocks into build_deck_v2.py."""
S = {}

S[1] = r'''
set_bg(cover, BG['cream'])
sh = cover.shapes
sh[2].text_frame.paragraphs[0].runs[0].text = 'FINAL SUBMISSION'; style_runs(sh[2], font='Calibri', size=16, bold=True, color=RUST)
sh[3].text_frame.paragraphs[0].runs[0].text = 'Sahara'
for r in sh[3].text_frame.paragraphs[0].runs[1:]: r.text = ''
style_runs(sh[3], font='Georgia', size=44, bold=True, color=GREEN)
sh[4].text_frame.paragraphs[0].runs[0].text = ('A phone app for people with a serious illness, like advanced cancer, and their families. '
    'Their doctor asks what care they want near the end of life; Sahara writes it on the legal form, '
    'and an emergency doctor can read it in one scan.')
for r in sh[4].text_frame.paragraphs[0].runs[1:]: r.text = ''
sh[4].text_frame.word_wrap = True
style_runs(sh[4], font='Calibri', size=16, color=MUTED)
sh[4].top = Inches(4.15); sh[4].height = Inches(0.95)
sh[3].top = Inches(3.35); sh[3].height = Inches(0.8)
sh[2].top = Inches(2.1); sh[2].height = Inches(0.4)
for s_, val in ((sh[5], 'TEAM NAME  Zeros and Ones'), (sh[6], 'TEAM CODE  ' + TEAM_CODE)):
    ps = s_.text_frame.paragraphs[0]; ps.runs[0].text = val
    for r in ps.runs[1:]: r.text = ''
    style_runs(s_, font='Calibri', size=14, bold=True, color=GREEN)
style_runs(sh[7], color=MUTED)
pic(cover, B / 'assets/sahara-app-icon.png', 6.29, 2.55, 0.75, 0.75)
names = [('basith', 'Shaik Abdul Basith'), ('awaiz', 'Shaik Muhammad Awaiz'), ('sharada', 'Dr Sharada Vinod Kutty'), ('sujay', 'Dr Sujay Halkur Shankar'), ('shirin', 'Shirin Ayub')]
for s_ in (sh[5], sh[6]):
    s_.top = Inches(5.25); s_.height = Inches(0.4); s_.text_frame.vertical_anchor = MSO_ANCHOR.TOP
sh[1]._element.getparent().remove(sh[1]._element)   # the green rule; the app icon takes its place
text(cover, 0.8, 5.8, 11.7, 0.4, [[R('  ·  '.join(n for _, n in names), size=13, bold=True, color=GREEN)]], align=PP_ALIGN.CENTER)
text(cover, 1.2, 6.3, 10.9, 0.35, [[R('Try it: sahara.wedevit.in', size=13, color=LINK, link=MVP), R('   ·   Android app and website   ·   English, Hindi, Telugu, Urdu, Kannada', size=13, color=MUTED)]], align=PP_ALIGN.CENTER)
'''

S[2] = r'''
section(team, 'Team Overview & Background', 'cream')
members = [
    ('basith', 'Shaik Abdul Basith', 'Technical lead', 'Built the website and the AI that writes down the doctor’s conversation'),
    ('awaiz', 'Shaik Muhammad Awaiz', 'Engineer', 'Built the Android app and the server; tests every version'),
    ('sharada', 'Dr Sharada Vinod Kutty', 'Doctor', 'Lung specialist. Wrote the medical rules the app follows'),
    ('sujay', 'Dr Sujay Halkur Shankar', 'Doctor', 'Lung specialist who treats patients near the end of life. Set the order of questions'),
    ('shirin', 'Shirin Ayub', 'Medical student', 'Designed how patients log their symptoms'),
]
for i, (f, n, role, c) in enumerate(members):
    x = 0.55 + i * 2.48
    card(team, x, 1.2, 2.3, 4.15, fill='FFFFFF', line='E6DCC8')
    pic(team, PH / f'{f}.jpg', x + 0.35, 1.38, 1.6, 1.6)
    text(team, x + 0.1, 3.1, 2.1, 2.2, [
        ([R(n, size=14, bold=True)], {'after': 2}),
        ([R(role, size=13, bold=True, color=RUST)], {'after': 5}),
        [R(c, size=12.5, color=MUTED)]], align=PP_ALIGN.CENTER)
card(team, 0.55, 5.55, 12.2, 1.45, fill=BG['mint'])
icon(team, 11, 0.8, 5.72, 0.45); text(team, 1.35, 5.65, 5.3, 0.65, [[R('Our doctors ', size=14, bold=True), R('chose this problem from their own clinics and check every version.', size=14)]], anchor=MSO_ANCHOR.MIDDLE)
icon(team, 28, 6.85, 5.72, 0.45); text(team, 7.4, 5.65, 5.3, 0.65, [[R('Rituparna P, a lawyer, ', size=14, bold=True), R('checked our form against India’s legal living-will format.', size=14)]], anchor=MSO_ANCHOR.MIDDLE)
text(team, 0.8, 6.42, 11.8, 0.45, [[R('An independent team. The app is on Google Play under the name WeDevit.', size=13, color=MUTED)]])
notes(team, 'Doctor background from Dr Sujay (team chat, 25 Aug 2026): pulmonary specialists who have worked extensively with lung cancer; Dr Sujay has palliative-medicine exposure.')
'''

S[3] = r'''
section(prob, 'Problem Statement and Supporting Evidence', 'blue')
pic(prob, B / 'assets/consult-family.jpg', 0.55, 1.2, 5.6, 5.6 * 883 / 1600)
text(prob, 0.55, 4.36, 5.6, 0.3, [[R('Example story (illustration). A 62-year-old woman with advanced breast cancer.', size=11, color=MUTED, italic=True)]])
story = [(4, 'At the clinic, ', 'her doctor asks what treatment she would want if she got very sick. Only her husband is there.'),
         (5, 'Two months later ', 'she is unconscious in the emergency room. Should doctors try to restart her heart (CPR)?'),
         (6, 'Her children disagree, ', 'and nobody can find what she said.')]
for i, (ic, a, b) in enumerate(story):
    y = 4.72 + i * 0.75
    icon(prob, ic, 0.55, y, 0.4)
    text(prob, 1.1, y - 0.04, 5.1, 0.75, [[R(a, size=13.5, bold=True), R(b, size=13.5)]])
stats = [(7, '15.6 lakh', RUST, 'new cancer cases in India in 2024.', 'IARC, GLOBOCAN 2024', 'https://gco.iarc.who.int/media/globocan/factsheets/populations/356-india-fact-sheet.pdf'),
         (8, '98%', TEAL, 'of patients whose cancer had spread did not get the comfort care (palliative care) they needed.', 'Patil et al., 2024', 'https://doi.org/10.1136/spcare-2024-004978'),
         (9, '88%', PURPLE, 'of surveyed city patients wanted to choose their own end-of-life treatment.', 'IndiaSpend, 2019', 'https://scroll.in/article/925875/73-urban-indians-do-not-know-about-their-right-to-make-a-living-will-finds-a-survey'),
         (10, '11.5%', BLUE, 'of doctors (45 of 391) in one hospital were trained in advance directives (recording these wishes).', 'Jacob et al., 2026', 'https://doi.org/10.25259/NMJI_817_2023')]
for i, (ic, big, col, desc, src, url) in enumerate(stats):
    x = 6.55 + (i % 2) * 3.15; y = 1.2 + (i // 2) * 1.85
    icon(prob, ic, x, y + 0.08, 0.45)
    text(prob, x + 0.55, y, 2.5, 0.6, [[R(big, size=28, bold=True, color=col)]])
    text(prob, x, y + 0.62, 2.95, 1.2, [[R(desc, size=12.5)], [R(src, size=10, color=LINK, link=url)]])
card(prob, 6.55, 5.0, 6.2, 1.95, fill='FFFFFF')
text(prob, 6.75, 5.08, 5.85, 1.85, [
    [R('What has to change', size=15, bold=True, color=RUST)],
    [R('Her wishes written on the legal form, signed, and found in seconds in an emergency.', size=16, bold=True)],
    [R('Numbers are from studies of different groups, not from Sahara.', size=10, color=MUTED, italic=True)]])
'''

S[4] = r'''
section(users, 'Target Users and Use Case', 'peach')
phone(users, '01-doctor-signin', 0.65, 1.2, 5.6)
rows = [(6, 'Patient', 'at home', 'logs how they feel; says what care they want'),
        (13, 'Family', 'at home', 'sees the same record; reads what the patient said'),
        (11, 'Their doctor', 'clinic', 'leads the conversation; checks and signs the form'),
        (10, 'Emergency doctor', 'minutes to decide', 'scans the patient’s QR code to see their wishes')]
for i, (ic, who, where, need) in enumerate(rows):
    y = 1.2 + i * 0.98
    card(users, 3.5, y, 9.25, 0.88, fill='FFFFFF')
    icon(users, ic, 3.7, y + 0.17, 0.52)
    text(users, 4.45, y + 0.04, 8.2, 0.8, [
        ([R(who, size=16, bold=True), R('  ·  ' + where, size=13, color=RUST)], {'after': 0}),
        [R(need, size=14, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
card(users, 3.5, 5.18, 9.25, 1.8, fill=BG['cream'], line='E6CDB8')
text(users, 3.7, 5.22, 8.9, 0.35, [[R('Words used in this deck', size=13, bold=True, color=RUST)]])
gl = [('Palliative care: ', 'care that eases pain and distress in a serious illness.'),
      ('Goals of care: ', 'what treatment a patient wants, or refuses, near the end of life (CPR, ventilator, ICU).'),
      ('Living will: ', 'the legal paper in India that records those wishes.'),
      ('QR code: ', 'a square barcode on the patient’s phone or card; a phone camera scan opens their wishes.')]
for i, (a, b) in enumerate(gl):
    x = 3.7 + (i % 2) * 4.5; y = 5.55 + (i // 2) * 0.7
    text(users, x, y, 4.35, 0.7, [[R(a, size=11.5, bold=True), R(b, size=11.5)]])
'''

S[5] = r'''
section(sol1, 'Solution Overview, Key Features & Impact (1/2)', 'cream')
text(sol1, 0.55, 1.0, 12.2, 0.7, [
    ([R('In the clinic: the doctor talks, Sahara listens and writes. ', size=17, bold=True, color=RUST)], {'after': 0}),
    [R('The doctor asks the patient and family what care they want (a “goals-of-care” talk). Today this is often never written down.', size=13.5, color=MUTED)]])
steps = [('08-goc-open', '1. Patient agrees, tap Record', 'Google’s Gemini AI types out who said what'),
         ('09-goc-checklist', '2. A checklist ticks itself', 'Shows any topic the doctor has not asked yet'),
         ('10g-note', '3. Answers fill the legal form', 'India’s living-will form, word for word. Doctor checks it'),
         ('11-qr', '4. Everyone signs on the phone', 'The form locks and opens from a QR code in an emergency')]
for i, (n, h, d) in enumerate(steps):
    x = 0.85 + i * 3.1
    phone(sol1, n, x, 1.85, 3.9)
    text(sol1, x - 0.3, 5.88, 2.55, 1.2, [([R(h, size=14, bold=True)], {'after': 2}), [R(d, size=12.5, color=MUTED)]])
    if i < 3: arrow(sol1, x + 2.1, 3.65)
notes(sol1, 'Screens captured from the live website demo (sahara.wedevit.in, 5 Nov 2026) with a fictional patient (Meera Raghavan) and the fictional mock conversation docs/goc-mock-conversations/01-english.txt. The Android app has the same flow.')
'''

S[6] = r'''
section(sol2, 'Solution Overview, Key Features & Impact (2/2)', 'lilac')
text(sol2, 0.55, 1.05, 12.2, 0.45, [[R('At home, between visits: ', size=18, bold=True, color=RUST), R('patients and families log how things are going; their doctor sees it.', size=15, color=MUTED)]])
for i, n in enumerate(['10-patient-home', '20-feel', '06-doc-care']):
    phone(sol2, n, 0.65 + i * 2.05, 1.7, 3.95)
feats = [(17, 'How I feel: ', 'symptoms, a 0–10 “how upset am I” scale and a body map to mark pain; the doctor sees charts.'),
         (12, 'Medicines: ', 'tick each dose; reminders; photograph a prescription to add it.'),
         (23, 'Reports: ', 'ask a question about a hospital report and get the answer with the line it came from.'),
         (29, 'Emergency: ', 'an SOS button and a QR code with wishes, contacts and medicines.'),
         (14, 'Help nearby: ', 'a map of palliative-care centres, ambulances and support groups.'),
         (9, 'Family: ', 'invited relatives see and add to the same record.')]
for i, (ic, a, b) in enumerate(feats):
    y = 1.7 + i * 0.66
    icon(sol2, ic, 6.95, y, 0.4)
    text(sol2, 7.5, y - 0.03, 5.3, 0.66, [[R(a, size=13, bold=True), R(b, size=13)]])
card(sol2, 0.55, 5.85, 12.2, 1.15, fill='FFFFFF')
text(sol2, 0.75, 5.92, 11.8, 1.05, [
    [R('What we expect to change (to be measured in a pilot): ', size=14, bold=True, color=PURPLE)],
    [R('more of these talks written and signed  ·  wishes found in seconds in an emergency  ·  fewer family arguments at the bedside  ·  doctors see problems before the next visit.', size=14)]])
'''

S[7] = r'''
section(tech, 'Technology, Tools and AI Approach', 'mint')
LG = B / 'assets/logos'
text(tech, 0.55, 0.98, 12.2, 0.4, [[R('How a conversation becomes a signed form', size=16, bold=True, color=RUST)]])
flow = [(LG / 'android.png', 'Doctor’s phone', 'records only after the patient agrees'),
        (LG / 'supabase.png', 'Our server', 'keeps the AI password; the phone never has it'),
        (LG / 'googlegemini.png', 'Google Gemini AI', 'types out the talk and fills in the form'),
        (IC / 'image11.png', 'Doctor', 'reads it, corrects it, signs'),
        (IC / 'image29.png', 'Locked form', 'PDF + QR code; any later change cancels the signatures')]
for i, (lg, h, d) in enumerate(flow):
    x = 0.55 + i * 2.5
    card(tech, x, 1.42, 2.15, 1.7, fill='FFFFFF', line='C9DDD2')
    pic(tech, lg, x + 0.8, 1.55, 0.55, 0.55)
    text(tech, x + 0.08, 2.15, 1.99, 1.2, [([R(h, size=14, bold=True)], {'after': 2}), [R(d, size=11.5, color=MUTED)]], align=PP_ALIGN.CENTER)
    if i < 4: arrow(tech, x + 2.17, 2.25, 0.3, 0.24)
pic(tech, LG / 'openai.png', 0.75, 3.52, 0.34, 0.34)
text(tech, 1.2, 3.47, 11.5, 0.45, [[R('No internet, or the AI is busy?  ', size=13.5, bold=True, color=RUST),
    R('OpenAI Whisper turns speech into text on the phone itself. If that fails too, the doctor types.', size=13.5)]], anchor=MSO_ANCHOR.MIDDLE)
text(tech, 0.55, 4.02, 12.2, 0.38, [[R('Built with', size=16, bold=True, color=RUST)]])
stack = [('android', 'Android', 'the phone app'), ('kotlin', 'Kotlin + Compose', 'language and screens of the app'),
         ('nextdotjs', 'Next.js', 'the website'), ('vercel', 'Vercel', 'puts the website online'),
         ('supabase', 'Supabase', 'server, sign-in, storage'), ('postgresql', 'PostgreSQL', 'database; each family sees only its own'),
         ('googlegemini', 'Google Gemini', 'AI: speech to text, fills the form'), ('openai', 'OpenAI Whisper', 'speech to text with no internet'),
         ('googletranslate', 'Google ML Kit', '5 languages and read-aloud on the phone'), ('openstreetmap', 'OpenStreetMap', 'free map of nearby centres'),
         (None, 'Legal form', 'India’s living will, checked by our lawyer'), (None, 'FHIR', 'standard format hospital software can read')]
for i, (lg, h, d) in enumerate(stack):
    x = 0.55 + (i % 6) * 2.05; y = 4.45 + (i // 6) * 1.27
    card(tech, x, y, 1.93, 1.15, fill='FFFFFF', radius=0.12)
    if lg: pic(tech, LG / f'{lg}.png', x + 0.12, y + 0.14, 0.42, 0.42)
    else: icon(tech, 28 if h == 'Legal form' else 23, x + 0.12, y + 0.14, 0.42)
    text(tech, x + 0.62, y + 0.1, 1.27, 0.5, [[R(h, size=11.5, bold=True)]], anchor=MSO_ANCHOR.MIDDLE)
    text(tech, x + 0.12, y + 0.62, 1.75, 0.5, [[R(d, size=10, color=MUTED)]])
notes(tech, 'Logos from the simple-icons set (CC0), used only to name the tools we use. Gemini models: 3.1 Flash-Lite first, then other Flash models when busy; on-device Whisper; typing.')
'''

S[8] = r'''
section(sprint, 'Build Sprint Journey and Progress', 'sand')
import datetime as _dt
GC_TODAY = '1A73E8'
text(sprint, 0.55, 0.98, 6.0, 0.36, [[R('October – November 2026', size=16, bold=True, color=GREEN)]])
leg = [('Built', TEAL), ('Doctor / lawyer feedback', PURPLE), ('Milestone', RUST)]
lx = 4.6
for lbl, col in leg:
    card(sprint, lx, 1.07, 0.2, 0.2, fill=col, radius=0.5)
    text(sprint, lx + 0.25, 1.0, 2.0, 0.34, [[R(lbl, size=11, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
    lx += 0.45 + len(lbl) * 0.075
gx0, cw, rh, top = 1.2, 1.17, 1.06, 1.68
days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
for j, d in enumerate(days):
    text(sprint, gx0 + j * cw, 1.38, cw, 0.28, [[R(d, size=9.5, bold=True, color=MUTED)]], align=PP_ALIGN.CENTER)
ev = {  # (day, month): [(label, colour)]
    (9, 10): [('Shortlisted!', RUST), ('Android app + map of centres', TEAL)],
    (10, 10): [('AI medicine organiser', TEAL)],
    (11, 10): [('Prescription organiser', TEAL)],
    (13, 10): [('Google Play test version', RUST)],
    (14, 10): [('Testers invited', TEAL)],
    (16, 10): [('Privacy and account safety', TEAL)],
    (19, 10): [('Sent to Google Play', RUST)],
    (20, 10): [('Mentor review', PURPLE)],
    (23, 10): [('Hospital survey feedback', PURPLE)],
    (25, 10): [('Symptom tracker', TEAL)],
    (26, 10): [('Pain body map', TEAL)],
    (27, 10): [('Hospital survey live', RUST)],
    (28, 10): [('Team meeting: 9 changes', PURPLE), ('Ask your reports', TEAL)],
    (29, 10): [('Dr Sharada’s medical rules', PURPLE)],
    (30, 10): [('Living will + QR code', TEAL), ('Telugu, Urdu, Kannada', TEAL)],
    (31, 10): [('Lawyer checks the form', PURPLE)],
    (1, 11): [('Recording → text', TEAL)],
    (2, 11): [('Doctor schedule', TEAL)],
    (3, 11): [('Dr Sujay tests the whole app', PURPLE)],
    (4, 11): [('Legal form word for word', TEAL), ('8 practice talks pass', TEAL)],
    (5, 11): [('Gemini AI + backups', TEAL), ('Final polish', RUST)],
}
start = _dt.date(2026, 10, 5) if _dt.date(2026, 10, 5).weekday() == 0 else _dt.date(2026, 10, 6)
start = _dt.date(2026, 10, 9) - _dt.timedelta(days=_dt.date(2026, 10, 9).weekday())
s0, s1 = _dt.date(2026, 10, 9), _dt.date(2026, 11, 5)
for w in range(5):
    y = top + w * rh
    wk = w + 1 if w < 4 else 4
    text(sprint, 0.5, y + 0.05, 0.68, 0.5, [[R('Week ' + str(min(w + 1, 4)), size=9.5, bold=True, color=MUTED)]], align=PP_ALIGN.CENTER) if w < 4 else None
    for j in range(7):
        d = start + _dt.timedelta(days=7 * w + j)
        x = gx0 + j * cw
        inside = s0 <= d <= s1
        c = card(sprint, x, y, cw, rh, fill='FFFFFF' if inside else 'EFEAE0', line='DDD5C6', radius=0.0)
        if d == s1:
            o = sprint.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x + 0.04), Inches(y + 0.03), Inches(0.26), Inches(0.26))
            o.fill.solid(); o.fill.fore_color.rgb = rgb(GC_TODAY); o.line.fill.background()
            text(sprint, x + 0.04, y + 0.03, 0.26, 0.26, [[R(str(d.day), size=9.5, bold=True, color='FFFFFF')]], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
        else:
            lbl = d.strftime('%d').lstrip('0') + (' ' + d.strftime('%b') if d.day == 1 or (w == 0 and j == 0) else '')
            text(sprint, x + 0.06, y + 0.03, cw - 0.1, 0.24, [[R(lbl, size=9.5, bold=inside, color=GREEN if inside else '9A9486')]])
        for k, (lab, col) in enumerate(ev.get((d.day, d.month), [])):
            cy = y + 0.3 + k * 0.37
            chip = card(sprint, x + 0.04, cy, cw - 0.08, 0.34, fill=col, radius=0.18)
            text(sprint, x + 0.07, cy, cw - 0.14, 0.34, [[R(lab, size=8, bold=True, color='FFFFFF')]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
text(sprint, gx0, top + 5 * rh + 0.02, 7 * cw, 0.28, [[R('Blue circle: 5 Nov, the day of this submission. Grey days are outside the 4-week build sprint.', size=9.5, color=MUTED, italic=True)]])
boxes = [('✓  Working now', TEAL, 'Patient and family app · doctor’s view · recording → signed form · QR code · website live'),
         ('↻  Changed on the way', PURPLE, 'Website first, then a real Android app. The AI used to write the note; now the doctor leads and the AI only writes it down.'),
         ('⚠  Problems we solved', RUST, 'Google’s AI was often busy → automatic backups. Legal wording → copied exactly.'),
         ('…  Still to do', BLUE, 'Test on many real phones · record a real practice talk with Dr Sharada')]
for i, (h, col, body) in enumerate(boxes):
    y = 1.0 + i * 1.48
    card(sprint, 9.55, y, 3.2, 1.38, fill='FFFFFF')
    text(sprint, 9.7, y + 0.06, 2.95, 1.3, [([R(h, size=13, bold=True, color=col)], {'after': 3}), [R(body, size=11, color=GREEN)]])
notes(sprint, 'Dates from the git history and team chat: shortlisted 9 Oct; native Android 9 Oct; Play testing 13-14 Oct; Play submission 19 Oct; survey feedback 23 Oct; survey live 27 Oct; team meeting 28 Oct; clinical spec 29 Oct; legal review 31 Oct; Dr Sujay walkthrough 3 Nov; legal form word for word 4 Nov; Gemini fallback 5 Nov.')
'''

S[9] = r'''
section(evals, 'Evaluation and User Validation', 'blue')
text(evals, 0.55, 0.98, 12.2, 0.38, [[R('1. Does it work? Our own tests', size=16, bold=True, color=RUST)]])
tiles = [('877', 'automatic checks on the Android app: all pass', TEAL), ('594 / 594', 'automatic checks on the website: all pass', BLUE),
         ('8 / 8', 'practice doctor–patient talks in 6 languages: every answer landed in the right place on the form', RUST),
         ('4 / 4', 'questions about a sample hospital report answered correctly', PURPLE)]
for i, (big, lbl, col) in enumerate(tiles):
    x = 0.55 + i * 3.08
    card(evals, x, 1.38, 2.93, 1.75, fill='FFFFFF')
    text(evals, x + 0.15, 1.4, 2.63, 0.7, [[R(big, size=32, bold=True, color=col, font='Georgia')]], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
    text(evals, x + 0.15, 2.1, 2.63, 1.0, [[R(lbl, size=12, color=MUTED)]], align=PP_ALIGN.CENTER)
text(evals, 0.55, 3.15, 12.2, 0.3, [[R('Automatic checks are small test programs that run after every change, so a broken feature is caught before release. The practice talks are made up, not real patients.', size=10.5, color=MUTED, italic=True)]])
text(evals, 0.55, 3.5, 12.2, 0.38, [[R('2. What experts told us, and what we changed', size=16, bold=True, color=RUST)]])
fb = [(11, 'Dr Sujay Halkur Shankar', 'doctor, used the whole app on 3 Nov',
       'Is there a guide telling the doctor what to talk about next? And a medicine taken 4 times a day needs 4 ticks.',
       'Checklist in the doctors’ order with a “not yet discussed” prompt; one tick per dose. Team chat: tubes-and-drains tracker, darker text.'),
      (28, 'Rituparna P', 'lawyer, checked the form on 31 Oct',
       'Use the exact wording of India’s living-will form.',
       'Form copied word for word; signing locks it.'),
      (23, 'Dr Sharada Vinod Kutty', 'doctor, wrote our medical rules on 29 Oct',
       'Comfort care must always continue, whatever treatment a patient refuses.',
       'The app shows this on every signed form.')]
for i, (ic, who, role, said, did) in enumerate(fb):
    x = 0.55 + i * 4.12
    card(evals, x, 3.92, 3.95, 2.0, fill='FFFFFF')
    icon(evals, ic, x + 0.15, 4.02, 0.42)
    text(evals, x + 0.65, 3.98, 3.2, 0.55, [([R(who, size=12.5, bold=True)], {'after': 0}), [R(role, size=10.5, color=MUTED)]])
    text(evals, x + 0.15, 4.55, 3.7, 1.35, [([R('Said: ', size=11.5, bold=True, color=PURPLE), R(said, size=11.5)], {'after': 4}),
                                           [R('We changed: ', size=11.5, bold=True, color=TEAL), R(did, size=11.5)]])
card(evals, 0.55, 6.08, 12.2, 0.92, fill=BG['lilac'])
text(evals, 0.8, 6.1, 11.8, 0.88, [
    ([R('Not tested yet: ', size=14, bold=True, color=PURPLE),
      R('with real patients  ·  on many real phones  ·  speech-to-text on real Hindi, Telugu and Kannada recordings  ·  a doctor checking each translation means the same', size=13)], {'after': 0})], anchor=MSO_ANCHOR.MIDDLE)
notes(evals, 'Unit tests: HANDOFF 5 Nov (Android 877, web 594/594). Mock conversations: docs/goc-mock-conversations, English, Hindi, Hinglish, Telugu, Urdu, Kannada, disagreement, short talk; both matchers pass all 8. Ask documents: 4 questions on a practice discharge PDF (one needed a retry). Feedback: 3 Nov meeting transcript and team chat to 4 Nov (tubes and lines, faded text, checklist order); legal review 31 Oct; clinical spec 29 Oct.')
'''

S[10] = r'''
section(safety, 'Safety and Responsible AI', 'mint')
quads = [(27, 'Privacy and consent', TEAL, ['Recording starts only after the patient ticks “I agree”', 'The AI is used only with that consent', 'Each family sees only their own records']),
         (21, 'Limits on the AI', RUST, ['The AI never suggests treatment', 'If a topic was not discussed, it says so; it does not guess', 'If the AI is down, the phone or the doctor writes it']),
         (30, 'A person decides', PURPLE, ['The doctor reads, corrects and signs first', 'The patient or family signs too', 'After signing, any change cancels the signatures']),
         (13, 'For everyone', BLUE, ['5 Indian languages, and it reads text aloud', 'Sign with a thumbprint; large text', 'Demos use made-up patients only'])]
for i, (ic, h, col, items) in enumerate(quads):
    x = 0.55 + (i % 2) * 6.15; y = 1.15 + (i // 2) * 2.95
    card(safety, x, y, 6.0, 2.8, fill='FFFFFF')
    icon(safety, ic, x + 0.25, y + 0.25, 0.6)
    text(safety, x + 1.05, y + 0.25, 4.8, 0.6, [[R(h, size=20, bold=True, color=col)]], anchor=MSO_ANCHOR.MIDDLE)
    text(safety, x + 0.35, y + 1.0, 5.5, 1.75, [([R('✓  ' + t, size=15)], {'after': 6}) for t in items])
'''

S[11] = r'''
section(deploy, 'Deployment, Cost and Sustainability Plan', 'peach')
steps_ = [(11, 'Pilot', 'Start in 1 clinic with our own doctors. Patients give consent as India’s data-protection law (DPDP Act) requires.'),
          (23, 'Fitting in', 'Works on its own; no hospital software needed. Later it can send records in FHIR, a format hospital software reads.'),
          (18, 'Who pays', 'Clinics or charities pay a monthly fee. Free for patients and families.'),
          (9, 'Growing', 'Grants first, then palliative-care networks across India.')]
for i, (ic, h, d) in enumerate(steps_):
    y = 1.2 + i * 1.45
    card(deploy, 0.55, y, 5.15, 1.3, fill='FFFFFF')
    icon(deploy, ic, 0.75, y + 0.35, 0.55)
    text(deploy, 1.5, y + 0.06, 4.1, 1.2, [([R(h, size=15, bold=True, color=RUST)], {'after': 1}), [R(d, size=12, color=GREEN)]], anchor=MSO_ANCHOR.MIDDLE)
crow = [('What it costs', 'Once', 'Every month'),
        ('Servers and website (Supabase + Vercel)', '–', '₹3,900'),
        ('Google Gemini AI, per 30-minute talk', '–', '≈ ₹2 a talk'),
        ('Setting up and training clinic staff', '₹25,000', '–'),
        ('Consent forms and legal check', '₹25,000', '–'),
        ('Support from our team', '–', '₹10,000'),
        ('Cost per clinic, if 10 clinics share it', '₹5,000', '≈ ₹1,600')]
t2 = deploy.shapes.add_table(len(crow), 3, Inches(5.9), Inches(1.2), Inches(6.85), Inches(3.6)).table
for j, wdt in enumerate([3.85, 1.25, 1.75]): t2.columns[j].width = Inches(wdt)
for i, row in enumerate(crow):
    for j, val in enumerate(row):
        c = t2.cell(i, j); c.text = val; c.fill.solid()
        c.fill.fore_color.rgb = rgb(GREEN if i == 0 else (BG['mint'] if i == len(crow) - 1 else 'FFFFFF'))
        c.margin_left = c.margin_right = Inches(0.1)
        for p in c.text_frame.paragraphs:
            for r in p.runs:
                r.font.name = 'Calibri'; r.font.size = Pt(13.5); r.font.bold = (i == 0 or i == len(crow) - 1)
                r.font.color.rgb = rgb('FFFFFF' if i == 0 else GREEN)
card(deploy, 5.9, 5.1, 6.85, 0.75, fill=GREEN)
text(deploy, 6.05, 5.1, 6.55, 0.75, [[R('We would charge ₹1,500–2,000 per clinic a month', size=17, bold=True, color='FFFFFF')]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
text(deploy, 5.9, 6.0, 6.85, 1.0, [[R('Our estimates from public prices: US$1 ≈ ₹86; Supabase $25 and Vercel $20 a month; Gemini ≈ $0.30 per million audio units (a 30-minute talk ≈ 58,000); 100 talks per clinic a month.', size=11, color=MUTED, italic=True)]])
notes(deploy, 'COST FIGURES ARE TEAM ESTIMATES from public list prices and an assumed Gemini audio price; confirm before submission. Per clinic monthly = (3,900 + 10,000) / 10 + 100 x 2 = ~1,590. One-time per clinic = 50,000 / 10 = 5,000.')
'''

S[12] = r'''
section(nxt, 'What Next? Plans to take initiative forward (if any)', 'lilac')
plan = [('1', 'Prove it works', 'Test on many real phones. Record a real practice talk with Dr Sharada.', TEAL),
        ('2', 'Pilot, 60–90 days', 'One clinic. Count how many talks get signed and how fast wishes are found.', RUST),
        ('3', 'Legal rules', 'What to do when a patient cannot decide, who may sign for them, and how to cancel a living will.', PURPLE),
        ('4', 'Fund and grow', 'Grants, palliative-care networks, then link with hospital software.', BLUE)]
for i, (n, h, d, col) in enumerate(plan):
    x = 0.55 + i * 3.08
    card(nxt, x, 1.3, 2.93, 3.4, fill='FFFFFF')
    circ = nxt.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x + 1.06), Inches(1.5), Inches(0.8), Inches(0.8))
    circ.fill.solid(); circ.fill.fore_color.rgb = rgb(col); circ.line.fill.background()
    circ.text_frame.text = n; style_runs(circ, font='Georgia', size=26, bold=True, color='FFFFFF')
    circ.text_frame.paragraphs[0].alignment = PP_ALIGN.CENTER
    text(nxt, x + 0.15, 2.45, 2.63, 2.2, [([R(h, size=18, bold=True, color=col)], {'after': 6}), [R(d, size=13.5, color=MUTED)]], align=PP_ALIGN.CENTER)
    if i < 3: arrow(nxt, x + 2.95, 2.8, 0.13, 0.22)
card(nxt, 0.55, 5.0, 12.2, 1.9, fill='FFFFFF', line='D9C9E6')
text(nxt, 0.8, 5.1, 11.7, 1.7, [
    ([R('Can the organisers share our solution publicly?   ', size=20, bold=True), R('YES', size=26, bold=True, color=TEAL)], {'after': 6}),
    [R('Slides, brief, video and app description. Not patient records, passwords or private code.', size=15, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
'''

S[13] = r'''
set_bg(thanks, BG['cream'])
th = thanks.shapes
style_runs(th[1], font='Georgia', size=40, bold=True, color=GREEN)
p = th[2].text_frame.paragraphs[0]; p.runs[0].text = 'Zeros and Ones  •  ' + TEAM_CODE
for r in p.runs[1:]: r.text = ''
style_runs(th[2], font='Calibri', size=18, bold=True, color=RUST)
tf = th[3].text_frame; p = tf.paragraphs[0]
for r in list(p.runs): p._p.remove(r._r)
th[3].left = Inches(0.6); th[3].width = Inches(12.1); th[3].height = Inches(0.6); th[3].text_frame.word_wrap = True
items = [('Try it: sahara.wedevit.in', MVP), ('Demo video: ' + VIDEO, None), (EMAIL, 'mailto:' + EMAIL), ('Code: github.com/basithladdu/healthathon (private)', REPO)]
for k, (lbl, url) in enumerate(items):
    r = p.add_run(); r.text = lbl; r.font.name = 'Calibri'; r.font.size = Pt(13)
    r.font.color.rgb = rgb(LINK if url else MUTED)
    if url: r.hyperlink.address = url; r.font.underline = True
    if k < len(items) - 1:
        s_ = p.add_run(); s_.text = '   |   '; s_.font.size = Pt(14); s_.font.color.rgb = rgb(MUTED)
style_runs(th[4], font='Calibri', size=12, bold=True, color=MUTED)
th[4].top = Inches(6.55)
style_runs(th[5], color=MUTED)
text(thanks, 0.8, 5.25, 11.7, 0.4, [[R('  ·  '.join(n for _, n in names), size=13, bold=True, color=GREEN)]], align=PP_ALIGN.CENTER)
'''
