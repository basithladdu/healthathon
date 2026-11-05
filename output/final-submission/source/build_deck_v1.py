"""Sahara final presentation, built on the organisers' PPT_Final_Teamplate_Health-a-thon 2026.pptx.

Kept from the template: slide order and section titles, Health-a-thon logo, footer, cover and
thank-you structure. Customised (allowed): backgrounds, colours, fonts, visuals, in the theme of our
3 Oct deck (cream/pastel backgrounds, Georgia headings in dark green, Calibri body, coloured line icons,
the same team photos and consultation scenes). The solution section uses its permitted 2 slides.
"""
import copy, sys
from pathlib import Path
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR, MSO_AUTO_SIZE
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn
from lxml import etree

B = Path(r"C:\Users\basit\sahara-build")
TPL = B / "deck" / "PPT_Final_Teamplate_Health-a-thon 2026.pptx"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else B / "deck" / "Sahara-Final-Presentation.pptx"
SC, PH, IC = B / "assets/screens", B / "assets/photos", B / "assets/icons"

GREEN, MUTED, RUST, TEAL, PURPLE, BLUE, LINK = '173F33', '53655C', 'B75E43', '157B68', '7353AC', '4357C7', '2D5F9A'
BG = {'cream': 'FFF6E5', 'blue': 'DCE6F4', 'peach': 'F7E3D5', 'lilac': 'F1E9F6', 'mint': 'E6F2EC', 'sand': 'F6EFE2'}
TEAM_CODE = '[add team code]'
VIDEO = '[add demo video link]'
MVP = 'https://sahara.wedevit.in'
REPO = 'https://github.com/basithladdu/healthathon'
EMAIL = 'workwithdevit@gmail.com'

prs = Presentation(TPL)
S = list(prs.slides)

def rgb(h): return RGBColor.from_string(h)

def set_bg(slide, color):
    f = slide.background.fill; f.solid(); f.fore_color.rgb = rgb(color)

def style_runs(shape, font=None, size=None, bold=None, color=None):
    for p in shape.text_frame.paragraphs:
        for r in p.runs:
            if font: r.font.name = font
            if size: r.font.size = Pt(size)
            if bold is not None: r.font.bold = bold
            if color: r.font.color.rgb = rgb(color)

def text(slide, x, y, w, h, paras, anchor=MSO_ANCHOR.TOP, align=PP_ALIGN.LEFT, inset=0.0):
    """paras: list of paragraphs; a paragraph = list of runs (txt, opts) or a str. opts: size,bold,color,font,link,italic,space"""
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame; tf.word_wrap = True; tf.auto_size = MSO_AUTO_SIZE.NONE; tf.vertical_anchor = anchor
    for m in ('margin_left', 'margin_right', 'margin_top', 'margin_bottom'): setattr(tf, m, Inches(inset))
    for i, para in enumerate(paras):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        if isinstance(para, tuple) and isinstance(para[0], list): para, popts = para
        else: popts = {}
        if isinstance(para, str): para = [(para, {})]
        p.space_after = Pt(popts.get('after', 4)); p.space_before = Pt(popts.get('before', 0))
        for t, o in para:
            r = p.add_run(); r.text = t
            f = r.font; f.name = o.get('font', 'Calibri'); f.size = Pt(o.get('size', 14)); f.bold = o.get('bold', False)
            f.italic = o.get('italic', False); f.color.rgb = rgb(o.get('color', GREEN))
            if o.get('link'):
                r.hyperlink.address = o['link']; f.underline = True
    return tb

def R(t, **o): return (t, o)

def card(slide, x, y, w, h, fill='FFFFFF', line=None, radius=0.08):
    sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.adjustments[0] = radius
    sh.fill.solid(); sh.fill.fore_color.rgb = rgb(fill)
    if line: sh.line.color.rgb = rgb(line); sh.line.width = Pt(0.75)
    else: sh.line.fill.background()
    sh.shadow.inherit = False
    return sh

def pic(slide, path, x, y, w=None, h=None, border=None):
    p = slide.shapes.add_picture(str(path), Inches(x), Inches(y), Inches(w) if w else None, Inches(h) if h else None)
    if border: p.line.color.rgb = rgb(border); p.line.width = Pt(0.75)
    return p

def phone(slide, name, x, y, h):
    """Phone screenshot (824x1784) scaled to height h, with a soft frame."""
    w = h * 824 / 1784
    card(slide, x - 0.05, y - 0.05, w + 0.1, h + 0.1, fill='FFFFFF', line='D9D4C7', radius=0.06)
    return pic(slide, SC / f'{name}.jpg', x, y, w, h)

def icon(slide, n, x, y, s=0.42): return pic(slide, IC / f'image{n}.png', x, y, s, s)

def arrow(slide, x, y, w=0.32, h=0.26, color='9AA79F'):
    a = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x), Inches(y), Inches(w), Inches(h))
    a.fill.solid(); a.fill.fore_color.rgb = rgb(color); a.line.fill.background(); return a

def strip_guidance(slide, keep=3):
    """Remove the template's grey guidance text boxes (everything after logo, title and footer)."""
    for sh in list(slide.shapes)[keep:]:
        sh._element.getparent().remove(sh._element)

def section(slide, title, bg):
    set_bg(slide, BG[bg])
    t = slide.shapes[1]
    t.text_frame.paragraphs[0].runs[0].text = title
    for r in t.text_frame.paragraphs[0].runs[1:]: r.text = ''
    t.text_frame.word_wrap = True
    t.fill.background()
    style_runs(t, font='Georgia', size=26, bold=True, color=GREEN)
    style_runs(slide.shapes[2], color=MUTED)   # footer
    strip_guidance(slide)

def notes(slide, txt): slide.notes_slide.notes_text_frame.text = txt

def duplicate_after(src_idx):
    """Duplicate template slide src_idx (0-based) and place the copy right after it."""
    src = prs.slides[src_idx]
    new = prs.slides.add_slide(src.slide_layout)
    for sh in list(new.shapes): sh._element.getparent().remove(sh._element)
    rid_map = {}
    for rel in src.part.rels.values():
        if 'image' in rel.reltype:
            rid_map[rel.rId] = new.part.relate_to(rel.target_part, rel.reltype)
    for el in src.shapes._spTree.iterchildren():
        if el.tag in (qn('p:nvGrpSpPr'), qn('p:grpSpPr')): continue
        e = copy.deepcopy(el)
        for blip in e.iter(qn('a:blip')):
            old = blip.get(qn('r:embed'))
            if old in rid_map: blip.set(qn('r:embed'), rid_map[old])
        new.shapes._spTree.append(e)
    lst = prs.slides._sldIdLst; ids = list(lst); moved = ids[-1]; lst.remove(moved); lst.insert(src_idx + 1, moved)
    return new

# Solution section: 2 slides (template allows "Max 2 Slides")
duplicate_after(4)
S = list(prs.slides)   # 13 slides now
cover, team, prob, users, sol1, sol2, tech, sprint, evals, safety, deploy, nxt, thanks = S

# ---------------------------------------------------------------- 1 cover
set_bg(cover, BG['cream'])
sh = cover.shapes
sh[2].text_frame.paragraphs[0].runs[0].text = 'FINAL SUBMISSION'; style_runs(sh[2], font='Calibri', size=16, bold=True, color=RUST)
sh[3].text_frame.paragraphs[0].runs[0].text = 'Sahara'
for r in sh[3].text_frame.paragraphs[0].runs[1:]: r.text = ''
style_runs(sh[3], font='Georgia', size=44, bold=True, color=GREEN)
sh[4].text_frame.paragraphs[0].runs[0].text = 'A palliative-care companion. Doctor-led goals-of-care conversations, written onto the legal form and ready in an emergency.'
for r in sh[4].text_frame.paragraphs[0].runs[1:]: r.text = ''
sh[4].text_frame.word_wrap = True
style_runs(sh[4], font='Calibri', size=18, color=MUTED)
sh[4].top = Inches(4.15); sh[4].height = Inches(0.8)
sh[3].top = Inches(3.35); sh[3].height = Inches(0.8)
sh[2].top = Inches(2.1); sh[2].height = Inches(0.4)
for s_, val in ((sh[5], 'TEAM NAME  Zeros and Ones'), (sh[6], 'TEAM CODE  ' + TEAM_CODE)):
    ps = s_.text_frame.paragraphs[0]; ps.runs[0].text = val
    for r in ps.runs[1:]: r.text = ''
    style_runs(s_, font='Calibri', size=14, bold=True, color=GREEN)
style_runs(sh[7], color=MUTED)
pic(cover, B / 'assets/sahara-app-icon.png', 6.29, 2.55, 0.75, 0.75)
# team strip
names = [('basith', 'Shaik Abdul Basith'), ('awaiz', 'Shaik Muhammad Awaiz'), ('sharada', 'Dr Sharada Vinod Kutty'), ('sujay', 'Dr Sujay Halkur Shankar'), ('shirin', 'Shirin Ayub')]
x0 = 1.55
for i, (f, n) in enumerate(names):
    x = x0 + i * 2.1
    pic(cover, PH / f'{f}.jpg', x, 5.7, 0.55, 0.55)
    text(cover, x + 0.62, 5.68, 1.45, 0.6, [[R(n, size=11, bold=True)]], anchor=MSO_ANCHOR.MIDDLE)
for s_ in (sh[5], sh[6]):
    s_.top = Inches(5.1); s_.height = Inches(0.4); s_.text_frame.vertical_anchor = MSO_ANCHOR.TOP
sh[1]._element.getparent().remove(sh[1]._element)   # the green rule; the app icon takes its place
text(cover, 2.2, 6.45, 9, 0.35, [[R('sahara.wedevit.in', size=13, color=LINK, link=MVP), R('   ·   Android app and website   ·   English, Hindi, Telugu, Urdu, Kannada', size=13, color=MUTED)]], align=PP_ALIGN.CENTER)

# ---------------------------------------------------------------- 2 team
section(team, 'Team Overview & Background', 'cream')
members = [
    ('basith', 'Shaik Abdul Basith', 'Technical lead', 'Product design, website, AI (Gemini, Whisper) and the goals-of-care flow.'),
    ('awaiz', 'Shaik Muhammad Awaiz', 'Technical member', 'Android app, Supabase server, deployment and phone testing.'),
    ('sharada', 'Dr Sharada Vinod Kutty', 'Doctor partner', 'Pulmonary specialist, lung-cancer care. Wrote the clinical specification: care plan, living will, emergency card.'),
    ('sujay', 'Dr Sujay Halkur Shankar', 'Clinician', 'Pulmonary specialist with palliative-medicine experience. Chose the problem, set the goals-of-care checklist, led app walkthroughs.'),
    ('shirin', 'Shirin Ayub', 'Medical student', 'Symptom tracker: symptom list, scales and check-in wording.'),
]
for i, (f, n, role, c) in enumerate(members):
    x = 0.55 + i * 2.48
    card(team, x, 1.2, 2.3, 4.15, fill='FFFFFF', line='E6DCC8')
    pic(team, PH / f'{f}.jpg', x + 0.35, 1.4, 1.6, 1.6)
    text(team, x + 0.12, 3.1, 2.06, 2.2, [
        ([R(n, size=14, bold=True)], {'after': 1}),
        ([R(role, size=12, bold=True, color=RUST)], {'after': 6}),
        [R(c, size=12, color=MUTED)]], align=PP_ALIGN.CENTER)
card(team, 0.55, 5.55, 12.2, 1.45, fill=BG['mint'])
text(team, 0.8, 5.65, 11.8, 1.3, [
    [R('Doctor partners. ', size=14, bold=True), R('Both are pulmonary specialists who have worked extensively with lung cancer, and Dr Sujay also has palliative-medicine experience. They chose the problem, wrote the clinical rules, and reviewed every build; the last walkthrough was on 3 Nov.', size=14)],
    [R('Legal review. ', size=14, bold=True), R('Rituparna P reviewed the goals-of-care form; the app copies it word for word.  ', size=14), R('Organisation: ', size=14, bold=True), R('independent team; apps published under WeDevit.', size=14)]])
notes(team, 'Doctor background from Dr Sujay (team chat, 25 Aug 2026): "Dr. Sharada and I are pulmonary specialists who have worked extensively with lung cancer. I additionally have some palliative medicine exposure." Photos from the submitted 3 Oct deck. Team: please confirm roles and the organisation line.')

# ---------------------------------------------------------------- 3 problem
section(prob, 'Problem Statement and Supporting Evidence', 'blue')
pic(prob, B / 'assets/consult-family.jpg', 0.55, 1.2, 5.6, 5.6 * 883 / 1600)
text(prob, 0.55, 4.36, 5.6, 0.3, [[R('Illustrative scene. A 62-year-old woman with advanced breast cancer.', size=11, color=MUTED, italic=True)]])
story = [(4, 'At the clinic, ', 'her doctor discusses her wishes. Only her husband attends.'),
         (5, 'Two months later, ', 'she is unconscious in the Emergency Department. Did she want CPR?'),
         (6, 'The family disagrees ', 'and her earlier note is missing.')]
for i, (ic, a, b) in enumerate(story):
    y = 4.75 + i * 0.6
    icon(prob, ic, 0.55, y, 0.4)
    text(prob, 1.1, y - 0.02, 5.1, 0.6, [[R(a, size=14, bold=True), R(b, size=14)]])
stats = [(7, '15.6 lakh', RUST, 'new cancer cases in India in 2024.', 'IARC, GLOBOCAN 2024', 'https://gco.iarc.who.int/media/globocan/factsheets/populations/356-india-fact-sheet.pdf'),
         (8, '98.39%', TEAL, 'unmet palliative-care need in metastatic cancer.', 'Patil et al., 2024', 'https://doi.org/10.1136/spcare-2024-004978'),
         (9, '88%', PURPLE, 'of surveyed urban patients wanted to choose their end-of-life treatment.', 'IndiaSpend, 2019', 'https://scroll.in/article/925875/73-urban-indians-do-not-know-about-their-right-to-make-a-living-will-finds-a-survey'),
         (10, '11.5%', BLUE, 'of doctors (45/391) in one hospital were trained in advance directives.', 'Jacob et al., 2026', 'https://doi.org/10.25259/NMJI_817_2023')]
for i, (ic, big, col, desc, src, url) in enumerate(stats):
    x = 6.55 + (i % 2) * 3.15; y = 1.2 + (i // 2) * 1.85
    icon(prob, ic, x, y + 0.08, 0.45)
    text(prob, x + 0.55, y, 2.5, 0.6, [[R(big, size=28, bold=True, color=col)]])
    text(prob, x, y + 0.65, 2.95, 1.2, [[R(desc, size=13)], [R(src, size=10, color=LINK, link=url)]])
card(prob, 6.55, 5.0, 6.2, 1.95, fill='FFFFFF')
text(prob, 6.75, 5.08, 5.85, 1.85, [
    [R('What needs to improve', size=15, bold=True, color=RUST)],
    [R('A doctor-led goals-of-care conversation, written down on a legally valid form, signed by doctor and patient or family, and found in seconds in an emergency. Between visits, symptoms and medicines need a simple record the doctor can see.', size=13)],
    [R('Studies of different groups; not results from Sahara.', size=10, color=MUTED, italic=True)]])

# ---------------------------------------------------------------- 4 users
section(users, 'Target Users and Use Case', 'peach')
phone(users, '01-doctor-signin', 0.65, 1.2, 5.6)
rows = [(6, 'Patients receiving palliative care', 'At home, between clinic visits.', 'Record how they feel, keep medicines and reports, and know their wishes are written down.'),
        (13, 'Family caregivers', 'At home, often deciding for the patient.', 'See the same record, log symptoms, and read exactly what the patient said, signed.'),
        (11, 'Palliative, oncology and pulmonary doctors', 'Outpatient clinic and ward.', 'Lead the goals-of-care conversation; the app writes it onto the legal form so they only review and sign.'),
        (10, 'Emergency doctors', 'Emergency Department, minutes to decide.', 'Scan the patient\u2019s QR card to read their wishes, decision-maker and medicines.')]
for i, (ic, who, where, need) in enumerate(rows):
    y = 1.2 + i * 1.32
    card(users, 3.5, y, 9.25, 1.2, fill='FFFFFF')
    icon(users, ic, 3.68, y + 0.32, 0.5)
    text(users, 4.35, y + 0.08, 8.25, 1.1, [
        ([R(who, size=15, bold=True), R('   ' + where, size=13, color=RUST)], {'after': 2}),
        [R(need, size=14)]])
text(users, 3.5, 6.55, 9.25, 0.45, [[R('Fits their workflow: ', size=13, bold=True), R('one app, three sign-ins (patient, family, doctor); five languages; works on low-cost Android phones.', size=13)]])

# ---------------------------------------------------------------- 5 solution 1/2
section(sol1, 'Solution Overview, Key Features & Impact (1/2)', 'cream')
text(sol1, 0.55, 1.05, 12.2, 0.45, [[R('Doctor leads. AI writes it down. ', size=18, bold=True, color=RUST), R('The goals-of-care conversation, from consent to a signed note.', size=16, color=MUTED)]])
steps = [('08-goc-open', '1. Consent, then record', 'Everyone agrees. Gemini transcribes; Whisper on the phone if offline.'),
         ('09-goc-checklist', '2. Checklist ticks itself', 'Topics in the doctors\u2019 order. Gaps are flagged before the note.'),
         ('10g-note', '3. Written on the legal form', 'Sections I\u2013IV, word for word. The doctor reviews and edits.'),
         ('11-qr', '4. Signed, locked, findable', 'Doctor and patient or family sign. Wishes go on the emergency QR card.')]
for i, (n, h, d) in enumerate(steps):
    x = 0.85 + i * 3.1
    phone(sol1, n, x, 1.65, 4.1)
    text(sol1, x - 0.25, 5.85, 2.4, 1.2, [([R(h, size=14, bold=True)], {'after': 2}), [R(d, size=12, color=MUTED)]])
    if i < 3: arrow(sol1, x + 2.15, 3.55)
notes(sol1, 'Screens captured from the live website demo (sahara.wedevit.in, 5 Nov 2026) with a fictional patient (Meera Raghavan) and the fictional mock conversation docs/goc-mock-conversations/01-english.txt. The Android app has the same flow.')

# ---------------------------------------------------------------- 6 solution 2/2
section(sol2, 'Solution Overview, Key Features & Impact (2/2)', 'lilac')
text(sol2, 0.55, 1.05, 12.2, 0.45, [[R('Between visits: ', size=18, bold=True, color=RUST), R('a companion for patients and families, shared with their doctor.', size=16, color=MUTED)]])
for i, n in enumerate(['10-patient-home', '20-feel', '06-doc-care']):
    phone(sol2, n, 0.65 + i * 2.05, 1.7, 3.95)
feats = [(17, 'How I feel: ', 'symptoms, 0\u201310 distress and a pain body map; trend charts for the doctor.'),
         (12, 'Medicines: ', 'each dose is a tick, with reminders and prescription scan.'),
         (23, 'Labs and documents: ', 'trends, and \u201cAsk your documents\u201d answers with the source line.'),
         (29, 'Emergency QR card and SOS: ', 'wishes, contacts and medicines in one scan.'),
         (14, 'Help nearby: ', 'palliative centres, ambulances and support groups.'),
         (9, 'Family: ', 'invited relatives see and add to the same record.')]
for i, (ic, a, b) in enumerate(feats):
    y = 1.7 + i * 0.6
    icon(sol2, ic, 6.95, y, 0.4)
    text(sol2, 7.5, y - 0.03, 5.3, 0.6, [[R(a, size=13, bold=True), R(b, size=13)]])
card(sol2, 0.55, 5.85, 12.2, 1.15, fill='FFFFFF')
text(sol2, 0.75, 5.92, 11.8, 1.05, [
    [R('Expected impact (to be measured in the pilot): ', size=14, bold=True, color=PURPLE)],
    [R('more goals-of-care conversations written and signed  \u00b7  wishes found in seconds in an emergency  \u00b7  fewer family disputes at the bedside  \u00b7  symptoms seen before the next visit.', size=14)]])

# ---------------------------------------------------------------- 7 technology
section(tech, 'Technology, Tools and AI Approach', 'mint')
flow = [('Doctor\u2019s phone', 'Consent tick, then record', RUST),
        ('Sahara server', 'Supabase function or Vercel; AI keys stay here', TEAL),
        ('Google Gemini', 'Transcript, who said what, mapped to the form', BLUE),
        ('Doctor', 'Reviews, edits and signs; patient or family signs', PURPLE),
        ('Locked note', 'SHA-256 seal, PDF, emergency QR card', GREEN)]
for i, (h, d, col) in enumerate(flow):
    x = 0.55 + i * 2.5
    c = card(tech, x, 1.2, 2.15, 1.35, fill=col)
    text(tech, x + 0.08, 1.25, 1.99, 1.25, [([R(h, size=15, bold=True, color='FFFFFF')], {'after': 3}), [R(d, size=12, color='FFFFFF')]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
    if i < 4: arrow(tech, x + 2.17, 1.75, 0.3, 0.24)
card(tech, 0.55, 2.75, 12.2, 0.75, fill='FFFFFF', line='C9DCD2')
text(tech, 0.75, 2.8, 11.8, 0.65, [[R('If Gemini is busy: ', size=13, bold=True, color=RUST), R('next model (Flash-Lite, then Flash) and next key.  ', size=13), R('Offline or no consent: ', size=13, bold=True, color=RUST), R('OpenAI Whisper on the phone or in the browser, then typing. Speakers are separated on the device.', size=13)]], anchor=MSO_ANCHOR.MIDDLE)
cols = [('What AI does', ['Turns speech into text (Indian languages).', 'Labels speakers.', 'Places each statement in the right section of the legal form; flags topics not discussed.', '\u201cAsk your documents\u201d: answers with the source line.', 'It never diagnoses or suggests treatment.']),
        ('Tools and models', ['Google Gemini 3.x Flash-Lite and Flash.', 'OpenAI Whisper (on device).', 'Google ML Kit translation and read aloud.', 'Android: Kotlin, Jetpack Compose.', 'Web: Next.js on Vercel.']),
        ('Data and integrations', ['Supabase: sign-in, Postgres with row-level security, Edge Functions.', 'Legal goals-of-care form (Rituparna P).', 'Clinical spec (Dr Sharada); Vidhi living-will template.', 'Pallium India directory and OpenStreetMap.', 'FHIR R4 export for hospital systems.'])]
for i, (h, items) in enumerate(cols):
    x = 0.55 + i * 4.12
    card(tech, x, 3.7, 3.95, 3.3, fill='FFFFFF')
    text(tech, x + 0.18, 3.78, 3.65, 3.15, [([R(h, size=15, bold=True, color=RUST)], {'after': 5})] + [([R('\u2022  ' + t, size=14)], {'after': 5}) for t in items])

# ---------------------------------------------------------------- 8 build sprint
section(sprint, 'Build Sprint Journey and Progress', 'sand')
weeks = ['Week 1 \u00b7 9\u201315 Oct', 'Week 2 \u00b7 16\u201322 Oct', 'Week 3 \u00b7 23\u201329 Oct', 'Week 4 \u00b7 30 Oct\u20135 Nov']
gx, gw, lab = 4.3, 8.45, 3.7
for i, wk in enumerate(weeks):
    text(sprint, gx + i * gw / 4, 1.05, gw / 4, 0.3, [[R(wk, size=11, bold=True, color=MUTED)]], align=PP_ALIGN.CENTER)
bars = [('Native Android app, Play testing', 0, 2, TEAL), ('Nearby centres map, national directory', 0, 2, BLUE),
        ('Medicines and prescription scan', 0, 1, RUST), ('Symptom tracker, pain map, distress', 1, 3, PURPLE),
        ('Hospital survey (live 27 Oct)', 2, 3, BLUE), ('Documents and \u201cAsk your documents\u201d', 2, 3, TEAL),
        ('Care plan, living will, QR card (clinical spec)', 2, 4, RUST), ('Telugu, Urdu and Kannada', 3, 4, PURPLE),
        ('Goals of care on the legal form, with Gemini', 3, 4, GREEN), ('Doctor side: patients, schedule, history', 3, 4, TEAL)]
for i, (name, a, b, col) in enumerate(bars):
    y = 1.38 + i * 0.27
    text(sprint, 0.55, y - 0.02, lab, 0.27, [[R(name, size=11)]], align=PP_ALIGN.RIGHT)
    card(sprint, gx + a * gw / 4 + 0.04, y + 0.03, (b - a) * gw / 4 - 0.08, 0.19, fill=col, radius=0.5)
boxes = [('Working now', TEAL, 'Patient and family app; doctor side; goals of care from recording to a locked, signed note; QR card; website live; Android app on Google Play testing.'),
         ('How it evolved', PURPLE, 'Round 1: a web prototype where AI drafted a \u201cCare Note\u201d. Now: a native app; the doctor leads; the note is the legal form word for word; daily companion added from doctors\u2019 walkthroughs.'),
         ('Challenges and changes', RUST, 'Gemini free tier said \u201chigh demand\u201d: added model and key fallback. Legal wording: copied the form exactly. Faded text on some phones: fixed.'),
         ('Still incomplete', BLUE, 'Full test on real phones; a recorded conversation with Dr Sharada; clinician back-translation; open legal questions (capacity, partners).')]
for i, (h, col, d) in enumerate(boxes):
    x = 0.55 + i * 3.08
    card(sprint, x, 4.2, 2.93, 2.8, fill='FFFFFF')
    text(sprint, x + 0.15, 4.28, 2.65, 2.65, [([R(h, size=15, bold=True, color=col)], {'after': 5}), [R(d, size=14)]])
notes(sprint, 'Dates from the git history: shortlisted 9 Oct; native Android app 9 Oct; prescription organiser 11 Oct; Play closed testing 10-14 Oct; 0.5 alpha 16 Oct; national directory 20 Oct; survey feedback 23 Oct, live 27 Oct; Ask your documents 28 Oct; SVK clinical spec 29 Oct; care plan translations 30 Oct; legal comments and goals-of-care transcription 1 Nov; doctor schedule 2 Nov; legal form word for word 4 Nov; Gemini model fallback 5 Nov.')

# ---------------------------------------------------------------- 9 evaluation
section(evals, 'Evaluation and User Validation', 'blue')
rows = [('What we tested', 'How', 'Result'),
        ('Code', 'Automated tests on every build', 'Android: 877 unit tests pass. Website: 594/594.'),
        ('Goals-of-care mapping', '8 fictional conversations with expected answers: English, Hindi, Hinglish (change of mind), Telugu, Urdu, Kannada, family disagreement, short talk', '8/8 matched the expected form.'),
        ('Speech to text', 'Gemini in the cloud; Whisper on the device; synthetic audio', 'English works end to end. Telugu and Kannada weak on the device, so typing is offered.'),
        ('Ask your documents', '4 questions on a practice discharge PDF', '4/4 correct, with the source line.'),
        ('Hospital survey', 'Real browser flows; changed after doctors\u2019 feedback', 'Live since 27 Oct.')]
tbl = evals.shapes.add_table(len(rows), 3, Inches(0.55), Inches(1.15), Inches(7.6), Inches(2.4)).table
for rr in tbl.rows: rr.height = Inches(0.3)
for j, wdt in enumerate([1.75, 3.35, 2.5]): tbl.columns[j].width = Inches(wdt)
for i, row in enumerate(rows):
    for j, val in enumerate(row):
        c = tbl.cell(i, j); c.text = val
        c.fill.solid(); c.fill.fore_color.rgb = rgb(GREEN if i == 0 else ('FFFFFF' if i % 2 else 'F3F6FB'))
        for m in ('margin_left', 'margin_right'): setattr(c, m, Inches(0.08))
        c.margin_top = Inches(0.04); c.margin_bottom = Inches(0.04)
        for p in c.text_frame.paragraphs:
            for r in p.runs:
                r.font.name = 'Calibri'; r.font.size = Pt(12 if i else 13); r.font.bold = (i == 0 or j == 0)
                r.font.color.rgb = rgb('FFFFFF' if i == 0 else GREEN)
card(evals, 8.4, 1.15, 4.35, 4.25, fill='FFFFFF')
text(evals, 8.6, 1.22, 4.0, 4.15, [
    ([R('Feedback and what we changed', size=15, bold=True, color=RUST)], {'after': 6}),
    ([R('Dr Sujay\u2019s walkthrough, 3 Nov: ', size=14, bold=True), R('added tubes-and-lines care, \u201cIf\u2026\u201d wording in the care plan, darker text, a shorter side-effect list, fewer ambulance types.', size=14)], {'after': 6}),
    ([R('Legal review, 31 Oct: ', size=14, bold=True), R('the note now copies the legal form word for word; 31 comments tracked.', size=14)], {'after': 6}),
    ([R('Dr Sharada\u2019s clinical spec: ', size=14, bold=True), R('the 12 \u201ccare that always continues\u201d items used word for word.', size=14)])])
card(evals, 0.55, 5.6, 12.2, 1.4, fill=BG['lilac'])
text(evals, 0.75, 5.66, 11.8, 1.3, [
    ([R('Limits and what still needs validation', size=15, bold=True, color=PURPLE)], {'after': 4}),
    [R('No real-patient pilot yet. Most checks ran on an emulator or in a browser; the full real-phone test is pending. Gemini accuracy on real Hindi, Telugu and other Indian-language audio is not yet measured. Translations are machine-drafted and need clinician back-translation.', size=13)]])

# ---------------------------------------------------------------- 10 safety
section(safety, 'Safety and Responsible AI', 'mint')
quads = [(27, 'Privacy, consent and security', TEAL, ['Recording starts only after everyone agrees.', 'Audio goes to Gemini only with that consent; otherwise Whisper runs on the phone.', 'Per-patient access, by invite; row-level security; AI keys only on the server.', 'Patients can download their data.']),
         (21, 'AI limits and errors', RUST, ['AI only transcribes and fills the form; it never advises treatment.', 'Topics not discussed are listed, not guessed.', 'If a person changes their mind, the latest statement wins and the earlier one is shown struck through; disagreements show both.', 'Busy or failing model: next model, then the phone, then typing.']),
         (30, 'Human oversight', PURPLE, ['The doctor starts, reviews, edits and signs first.', 'Patient or family signs on the same phone.', 'The version is locked with a SHA-256 seal; any edit voids the signatures.', 'Every version stays in the history.']),
         (13, 'Bias, accessibility and inclusion', BLUE, ['Five languages, with read aloud.', 'Large text; thumb signature for people who cannot write.', 'Works on low-cost Android phones.', 'Speech weak in Telugu and Kannada: typing offered. Demos use fictional patients only.'])]
for i, (ic, h, col, items) in enumerate(quads):
    x = 0.55 + (i % 2) * 6.15; y = 1.15 + (i // 2) * 2.95
    card(safety, x, y, 6.0, 2.8, fill='FFFFFF')
    icon(safety, ic, x + 0.2, y + 0.18, 0.45)
    text(safety, x + 0.8, y + 0.18, 5.0, 0.45, [[R(h, size=16, bold=True, color=col)]], anchor=MSO_ANCHOR.MIDDLE)
    text(safety, x + 0.25, y + 0.72, 5.55, 2.05, [([R('\u2022  ' + t, size=14)], {'after': 4}) for t in items])

# ---------------------------------------------------------------- 11 deployment
section(deploy, 'Deployment, Cost and Sustainability Plan', 'peach')
card(deploy, 0.55, 1.15, 5.15, 5.85, fill='FFFFFF')
text(deploy, 0.75, 1.22, 4.8, 5.7, [
    ([R('To run a pilot', size=15, bold=True, color=RUST)], {'after': 3}),
    ([R('A palliative or oncology clinic with our doctors; Android phones for doctors; 2 short training sessions; a consent and privacy process under India\u2019s DPDP Act; the legal team\u2019s open questions settled.', size=14)], {'after': 10}),
    ([R('Integration', size=15, bold=True, color=RUST)], {'after': 3}),
    ([R('Not required to start: Sahara works on its own. Later: FHIR R4 export into the hospital record, and a link to CARE (Open Healthcare Network) palliative programmes.', size=14)], {'after': 10}),
    ([R('Scale and sustain', size=15, bold=True, color=RUST)], {'after': 3}),
    ([R('Free for patients and families. Clinics, hospitals or NGO palliative programmes pay a small monthly fee per clinic; grants or CSR fund the first pilots. New languages are added through the translation dictionary.', size=14)])])
crow = [('Item', 'One-time', 'Ongoing'),
        ('Hosting: Supabase Pro + Vercel Pro', '\u2013', '\u2248 \u20b93,900 / month'),
        ('Gemini, per 30-min conversation', '\u2013', '\u2248 \u20b92 (per transaction)'),
        ('Clinic set-up and staff training', '\u2248 \u20b925,000', '\u2013'),
        ('Consent, privacy and legal review', '\u2248 \u20b925,000', '\u2013'),
        ('Support and updates (part-time)', '\u2013', '\u2248 \u20b910,000 / month'),
        ('Per clinic, at 10 clinics', '\u2248 \u20b95,000', '\u2248 \u20b91,600 / month')]
t2 = deploy.shapes.add_table(len(crow), 3, Inches(5.9), Inches(1.15), Inches(6.85), Inches(3.3)).table
for j, wdt in enumerate([3.35, 1.4, 2.1]): t2.columns[j].width = Inches(wdt)
for i, row in enumerate(crow):
    for j, val in enumerate(row):
        c = t2.cell(i, j); c.text = val; c.fill.solid()
        c.fill.fore_color.rgb = rgb(GREEN if i == 0 else (BG['mint'] if i == len(crow) - 1 else 'FFFFFF'))
        c.margin_left = c.margin_right = Inches(0.08)
        for p in c.text_frame.paragraphs:
            for r in p.runs:
                r.font.name = 'Calibri'; r.font.size = Pt(12.5); r.font.bold = (i == 0 or i == len(crow) - 1)
                r.font.color.rgb = rgb('FFFFFF' if i == 0 else GREEN)
text(deploy, 5.9, 4.75, 6.85, 2.25, [
    ([R('Pricing assumptions', size=14, bold=True, color=RUST)], {'after': 2}),
    ([R('US$1 \u2248 \u20b986. Supabase Pro US$25 and Vercel Pro US$20 a month. Gemini Flash-Lite paid tier, about US$0.30 per million audio tokens; 30 minutes of audio \u2248 58,000 tokens. 100 conversations per clinic a month. ', size=12.5), R('Team estimates, not quotes.', size=12.5, italic=True)], {'after': 4}),
    ([R('Suggested price: \u20b91,500\u20132,000 per clinic per month; free for patients.', size=12.5, bold=True)])])
notes(deploy, 'COST FIGURES ARE TEAM ESTIMATES built from public list prices (Supabase Pro $25/month, Vercel Pro $20/seat/month) and an assumed Gemini audio price; please confirm before submission. Per-clinic monthly = (3,900 + 10,000) / 10 + 100 x 2 = ~1,590. One-time per clinic = 50,000 / 10 = 5,000.')

# ---------------------------------------------------------------- 12 what next
section(nxt, 'What Next? Plans to take initiative forward (if any)', 'lilac')
plan = [('1', 'Finish and prove the app', 'Real-phone tests; record a goals-of-care conversation with Dr Sharada; clinician back-translation of Hindi, Telugu, Urdu and Kannada.', TEAL),
        ('2', 'Pilot for 60\u201390 days', 'With ethics approval, in a palliative-care clinic. Compare before and after: goals-of-care conversations documented; signed notes per clinic; time to find the patient\u2019s wishes in an emergency drill.', RUST),
        ('3', 'Settle the legal questions', 'With Rituparna P: capacity checks, partners as decision-makers, withdrawal of treatment (Common Cause judgments).', PURPLE),
        ('4', 'Fund and grow', 'Apply for grants and CSR support; offer Sahara to palliative-care networks; link to hospital records through FHIR.', BLUE)]
for i, (n, h, d, col) in enumerate(plan):
    x = 0.55 + i * 3.08
    card(nxt, x, 1.2, 2.93, 3.9, fill='FFFFFF')
    circ = nxt.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x + 0.2), Inches(1.38), Inches(0.6), Inches(0.6))
    circ.fill.solid(); circ.fill.fore_color.rgb = rgb(col); circ.line.fill.background()
    circ.text_frame.text = n; style_runs(circ, font='Georgia', size=20, bold=True, color='FFFFFF')
    circ.text_frame.paragraphs[0].alignment = PP_ALIGN.CENTER
    text(nxt, x + 0.2, 2.1, 2.6, 3.0, [([R(h, size=16, bold=True, color=col)], {'after': 5}), [R(d, size=14)]])
card(nxt, 0.55, 5.4, 12.2, 1.5, fill='FFFFFF', line='D9C9E6')
text(nxt, 0.8, 5.5, 11.7, 1.35, [
    ([R('Make the solution information public?  ', size=17, bold=True), R('Yes.', size=17, bold=True, color=TEAL)], {'after': 4}),
    [R('Our slides, brief, demo video and app description may be shared on Health-a-thon and other websites. This excludes patient records, credentials and the private code repository.', size=13.5)]])

# ---------------------------------------------------------------- 13 thank you
set_bg(thanks, BG['cream'])
th = thanks.shapes
style_runs(th[1], font='Georgia', size=40, bold=True, color=GREEN)
p = th[2].text_frame.paragraphs[0]; p.runs[0].text = 'Zeros and Ones  \u2022  ' + TEAM_CODE
for r in p.runs[1:]: r.text = ''
style_runs(th[2], font='Calibri', size=18, bold=True, color=RUST)
# links line: rebuild with real hyperlinks
tf = th[3].text_frame; p = tf.paragraphs[0]
for r in list(p.runs): p._p.remove(r._r)
th[3].left = Inches(0.6); th[3].width = Inches(12.1); th[3].height = Inches(0.6); th[3].text_frame.word_wrap = True
items = [(EMAIL, 'mailto:' + EMAIL), ('sahara.wedevit.in', MVP), ('Demo video: ' + VIDEO, None), ('github.com/basithladdu/healthathon (private)', REPO)]
for k, (lbl, url) in enumerate(items):
    r = p.add_run(); r.text = lbl; r.font.name = 'Calibri'; r.font.size = Pt(13)
    r.font.color.rgb = rgb(LINK if url else MUTED)
    if url: r.hyperlink.address = url; r.font.underline = True
    if k < len(items) - 1:
        s_ = p.add_run(); s_.text = '   |   '; s_.font.size = Pt(14); s_.font.color.rgb = rgb(MUTED)
style_runs(th[4], font='Calibri', size=12, bold=True, color=MUTED)
th[4].top = Inches(6.55)
style_runs(th[5], color=MUTED)
for i, (f, n) in enumerate(names):
    x = 1.55 + i * 2.1
    pic(thanks, PH / f'{f}.jpg', x + 0.5, 5.0, 0.8, 0.8)
    text(thanks, x - 0.1, 5.85, 2.0, 0.5, [[R(n, size=11, bold=True)]], align=PP_ALIGN.CENTER)

import re
for part in prs.part.package.iter_parts():
    if '/theme/' in str(part.partname):
        x = part.blob.decode('utf-8')
        for tag in ('hlink', 'folHlink'):
            x = re.sub(rf'<a:{tag}>.*?</a:{tag}>', f'<a:{tag}><a:srgbClr val="{LINK}"/></a:{tag}>', x, flags=re.S)
        part._blob = x.encode('utf-8')
prs.save(OUT)
print('saved', OUT, 'slides', len(prs.slides))
