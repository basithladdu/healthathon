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
    ('basith', 'Shaik Abdul Basith', 'Technical lead', 'Web, AI, goals-of-care flow'),
    ('awaiz', 'Shaik Muhammad Awaiz', 'Technical member', 'Android app, server, testing'),
    ('sharada', 'Dr Sharada Vinod Kutty', 'Doctor partner', 'Pulmonologist · wrote the clinical spec'),
    ('sujay', 'Dr Sujay Halkur Shankar', 'Clinician', 'Pulmonologist, palliative care · set the checklist'),
    ('shirin', 'Shirin Ayub', 'Medical student', 'Symptom tracker'),
]
for i, (f, n, role, c) in enumerate(members):
    x = 0.55 + i * 2.48
    card(team, x, 1.25, 2.3, 4.0, fill='FFFFFF', line='E6DCC8')
    pic(team, PH / f'{f}.jpg', x + 0.25, 1.45, 1.8, 1.8)
    text(team, x + 0.1, 3.4, 2.1, 1.8, [
        ([R(n, size=15, bold=True)], {'after': 2}),
        ([R(role, size=13, bold=True, color=RUST)], {'after': 6}),
        [R(c, size=13, color=MUTED)]], align=PP_ALIGN.CENTER)
card(team, 0.55, 5.55, 12.2, 1.4, fill=BG['mint'])
icon(team, 11, 0.8, 5.75, 0.45); text(team, 1.35, 5.72, 5.4, 0.55, [[R('Doctors: ', size=15, bold=True), R('chose the problem, review every build', size=15)]], anchor=MSO_ANCHOR.MIDDLE)
icon(team, 28, 6.85, 5.75, 0.45); text(team, 7.4, 5.72, 5.3, 0.55, [[R('Legal: ', size=15, bold=True), R('Rituparna P reviewed the form', size=15)]], anchor=MSO_ANCHOR.MIDDLE)
text(team, 0.8, 6.35, 11.8, 0.45, [[R('Independent team · apps published under WeDevit', size=13, color=MUTED)]])
notes(team, 'Doctor background from Dr Sujay (team chat, 25 Aug 2026): pulmonary specialists who have worked extensively with lung cancer; Dr Sujay has palliative-medicine exposure.')

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
    [R('Wishes written on the legal form, signed, and found in seconds in an emergency.', size=16, bold=True)],
    [R('Studies of different groups; not results from Sahara.', size=10, color=MUTED, italic=True)]])

# ---------------------------------------------------------------- 4 users
section(users, 'Target Users and Use Case', 'peach')
phone(users, '01-doctor-signin', 0.65, 1.2, 5.6)
rows = [(6, 'Patient', 'at home', 'logs how they feel; wishes written down'),
        (13, 'Family', 'at home', 'same record; reads what the patient said'),
        (11, 'Palliative / oncology doctor', 'clinic', 'leads the talk; only reviews and signs'),
        (10, 'Emergency doctor', 'ED, minutes to decide', 'scans the QR card for the wishes')]
for i, (ic, who, where, need) in enumerate(rows):
    y = 1.2 + i * 1.32
    card(users, 3.5, y, 9.25, 1.2, fill='FFFFFF')
    icon(users, ic, 3.7, y + 0.3, 0.6)
    text(users, 4.55, y + 0.12, 8.05, 1.0, [
        ([R(who, size=18, bold=True), R('  ·  ' + where, size=15, color=RUST)], {'after': 2}),
        [R(need, size=16, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
text(users, 3.5, 6.55, 9.25, 0.45, [[R('1 app  ·  3 sign-ins  ·  5 languages  ·  low-cost Android', size=16, bold=True, color=TEAL)]])

# ---------------------------------------------------------------- 5 solution 1/2
section(sol1, 'Solution Overview, Key Features & Impact (1/2)', 'cream')
text(sol1, 0.55, 1.05, 12.2, 0.45, [[R('Doctor leads. AI writes it down. ', size=18, bold=True, color=RUST), R('The goals-of-care conversation, from consent to a signed note.', size=16, color=MUTED)]])
steps = [('08-goc-open', '1. Consent, then record', 'Gemini transcribes (Whisper offline)'),
         ('09-goc-checklist', '2. Checklist ticks itself', 'Gaps flagged'),
         ('10g-note', '3. Written on the legal form', 'Doctor reviews and edits'),
         ('11-qr', '4. Signed, locked, findable', 'On the emergency QR card')]
for i, (n, h, d) in enumerate(steps):
    x = 0.85 + i * 3.1
    phone(sol1, n, x, 1.65, 4.1)
    text(sol1, x - 0.25, 5.85, 2.4, 1.2, [([R(h, size=16, bold=True)], {'after': 2}), [R(d, size=14, color=MUTED)]])
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
flow = [('Doctor’s phone', 'consent → record', RUST),
        ('Sahara server', 'keys stay here', TEAL),
        ('Google Gemini', 'transcribe · speakers · fill form', BLUE),
        ('Doctor', 'review · edit · sign', PURPLE),
        ('Locked note', 'SHA-256 · PDF · QR', GREEN)]
for i, (h, d, col) in enumerate(flow):
    x = 0.55 + i * 2.5
    card(tech, x, 1.3, 2.15, 1.45, fill=col)
    text(tech, x + 0.08, 1.35, 1.99, 1.35, [([R(h, size=17, bold=True, color='FFFFFF')], {'after': 3}), [R(d, size=13, color='FFFFFF')]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
    if i < 4: arrow(tech, x + 2.17, 1.88, 0.3, 0.24)
text(tech, 0.55, 2.95, 12.2, 0.5, [[R('Backup:  ', size=15, bold=True, color=RUST), R('Gemini busy → next model → Whisper on the phone → typing', size=15)]], align=PP_ALIGN.CENTER)
chips = [('AI', RUST, ['Gemini Flash-Lite / Flash', 'OpenAI Whisper (on phone)', 'ML Kit translate + read aloud', 'On-phone speaker labels']),
         ('App', TEAL, ['Android · Kotlin, Compose', 'Website · Next.js, Vercel', 'Supabase · Postgres + RLS', '5 languages']),
         ('Data', BLUE, ['Legal goals-of-care form', 'Clinical spec (Dr Sharada)', 'Pallium India + OpenStreetMap', 'FHIR R4 export'])]
for i, (h, col, items) in enumerate(chips):
    x = 0.55 + i * 4.12
    text(tech, x, 3.6, 3.95, 0.45, [[R(h, size=18, bold=True, color=col)]])
    for k, t in enumerate(items):
        card(tech, x, 4.15 + k * 0.7, 3.95, 0.58, fill='FFFFFF', radius=0.3)
        text(tech, x + 0.2, 4.15 + k * 0.7, 3.6, 0.58, [[R(t, size=14)]], anchor=MSO_ANCHOR.MIDDLE)

# ---------------------------------------------------------------- 8 build sprint
section(sprint, 'Build Sprint Journey and Progress', 'sand')
weeks = ['Week 1 · 9–15 Oct', 'Week 2 · 16–22 Oct', 'Week 3 · 23–29 Oct', 'Week 4 · 30 Oct–5 Nov']
gx, gw, lab = 4.3, 8.45, 3.7
for i, wk in enumerate(weeks):
    text(sprint, gx + i * gw / 4, 1.05, gw / 4, 0.3, [[R(wk, size=12, bold=True, color=MUTED)]], align=PP_ALIGN.CENTER)
bars = [('Android app, Play testing', 0, 2, TEAL), ('Nearby centres map', 0, 2, BLUE),
        ('Medicines, prescription scan', 0, 1, RUST), ('Symptoms, pain map', 1, 3, PURPLE),
        ('Hospital survey', 2, 3, BLUE), ('Ask your documents', 2, 3, TEAL),
        ('Care plan, living will, QR', 2, 4, RUST), ('Telugu, Urdu, Kannada', 3, 4, PURPLE),
        ('Goals of care + Gemini', 3, 4, GREEN), ('Doctor side', 3, 4, TEAL)]
for i, (name, a, b, col) in enumerate(bars):
    y = 1.4 + i * 0.29
    text(sprint, 0.55, y - 0.03, lab, 0.29, [[R(name, size=12.5)]], align=PP_ALIGN.RIGHT)
    card(sprint, gx + a * gw / 4 + 0.04, y + 0.03, (b - a) * gw / 4 - 0.08, 0.2, fill=col, radius=0.5)
boxes = [('✓  Working', TEAL, ['Patient + family app', 'Doctor side', 'Record → signed note', 'QR card, website live']),
         ('↻  Evolved', PURPLE, ['Web prototype → Android', 'AI note → doctor-led', 'Legal form, word for word']),
         ('⚠  Challenges', RUST, ['Gemini “busy” → fallbacks', 'Legal wording → copied', 'Faded text → fixed']),
         ('…  Still to do', BLUE, ['Real-phone tests', 'Recording with Dr Sharada', 'Back-translation'])]
for i, (h, col, items) in enumerate(boxes):
    x = 0.55 + i * 3.08
    card(sprint, x, 4.45, 2.93, 2.5, fill='FFFFFF')
    text(sprint, x + 0.18, 4.55, 2.65, 2.35, [([R(h, size=17, bold=True, color=col)], {'after': 6})] + [([R(t, size=14)], {'after': 4}) for t in items])
notes(sprint, 'Dates from the git history: shortlisted 9 Oct; native Android 9 Oct; Play testing 10-14 Oct; directory 20 Oct; survey live 27 Oct; Ask your documents 28 Oct; clinical spec 29 Oct; legal comments 1 Nov; legal form word for word 4 Nov; Gemini fallback 5 Nov.')

# ---------------------------------------------------------------- 9 evaluation
section(evals, 'Evaluation and User Validation', 'blue')
tiles = [('877', 'Android tests pass', TEAL), ('594/594', 'website tests pass', BLUE),
         ('8/8', 'mock conversations, 6 languages, mapped right', RUST), ('4/4', '“Ask your documents” answers correct', PURPLE)]
for i, (big, lbl, col) in enumerate(tiles):
    x = 0.55 + i * 3.08
    card(evals, x, 1.2, 2.93, 1.95, fill='FFFFFF')
    text(evals, x + 0.15, 1.25, 2.63, 0.95, [[R(big, size=40, bold=True, color=col, font='Georgia')]], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
    text(evals, x + 0.15, 2.2, 2.63, 0.9, [[R(lbl, size=14, color=MUTED)]], align=PP_ALIGN.CENTER)
text(evals, 0.55, 3.35, 12.2, 0.45, [[R('Feedback → change', size=18, bold=True, color=RUST)]])
fb = [(11, 'Dr Sujay walkthrough (3 Nov)', 'tubes & lines, clearer wording, darker text'),
      (28, 'Legal review (31 Oct)', 'form copied word for word'),
      (23, 'Dr Sharada’s spec', '12 “always continues” items, word for word')]
for i, (ic, a, b) in enumerate(fb):
    x = 0.55 + i * 4.12
    card(evals, x, 3.85, 3.95, 1.45, fill='FFFFFF')
    icon(evals, ic, x + 0.18, 4.05, 0.5)
    text(evals, x + 0.8, 3.92, 3.05, 1.35, [([R(a, size=14, bold=True)], {'after': 3}), [R(b, size=13, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
card(evals, 0.55, 5.5, 12.2, 1.45, fill=BG['lilac'])
text(evals, 0.8, 5.58, 11.8, 1.3, [
    ([R('Not yet', size=17, bold=True, color=PURPLE)], {'after': 4}),
    [R('real-patient pilot  ·  full real-phone test  ·  speech accuracy on real Indian-language audio  ·  clinician back-translation', size=15)]])
notes(evals, 'Unit tests: HANDOFF 5 Nov (Android 877, web 594/594). Mock conversations: docs/goc-mock-conversations, English, Hindi, Hinglish, Telugu, Urdu, Kannada, disagreement, short talk; both matchers pass all 8. Ask documents: 4 questions on a practice discharge PDF (one needed a retry). Speech: English end to end; Telugu and Kannada weak on the device.')

# ---------------------------------------------------------------- 10 safety
section(safety, 'Safety and Responsible AI', 'mint')
quads = [(27, 'Privacy and consent', TEAL, ['Consent tick before recording', 'Gemini only with consent', 'Access by invite · row-level security']),
         (21, 'AI limits', RUST, ['AI never advises treatment', 'Missing topics listed, not guessed', 'Busy model → phone → typing']),
         (30, 'Human oversight', PURPLE, ['Doctor reviews, edits, signs first', 'Patient or family signs too', 'Locked: any edit voids signatures']),
         (13, 'Inclusion', BLUE, ['5 languages + read aloud', 'Thumb signature, large text', 'Fictional patients in demos'])]
for i, (ic, h, col, items) in enumerate(quads):
    x = 0.55 + (i % 2) * 6.15; y = 1.15 + (i // 2) * 2.95
    card(safety, x, y, 6.0, 2.8, fill='FFFFFF')
    icon(safety, ic, x + 0.25, y + 0.25, 0.6)
    text(safety, x + 1.05, y + 0.25, 4.8, 0.6, [[R(h, size=20, bold=True, color=col)]], anchor=MSO_ANCHOR.MIDDLE)
    text(safety, x + 0.35, y + 1.0, 5.5, 1.75, [([R('✓  ' + t, size=16)], {'after': 6}) for t in items])

# ---------------------------------------------------------------- 11 deployment
section(deploy, 'Deployment, Cost and Sustainability Plan', 'peach')
steps_ = [(11, 'Pilot', '1 clinic · our doctors · DPDP consent'),
          (23, 'Integration', 'none needed · FHIR export later'),
          (18, 'Who pays', 'clinics / NGOs · free for patients'),
          (9, 'Scale', 'grants → palliative networks')]
for i, (ic, h, d) in enumerate(steps_):
    y = 1.2 + i * 1.45
    card(deploy, 0.55, y, 5.15, 1.3, fill='FFFFFF')
    icon(deploy, ic, 0.75, y + 0.35, 0.6)
    text(deploy, 1.55, y + 0.1, 4.05, 1.1, [([R(h, size=17, bold=True, color=RUST)], {'after': 2}), [R(d, size=14)]], anchor=MSO_ANCHOR.MIDDLE)
crow = [('Item', 'One-time', 'Ongoing'),
        ('Hosting (Supabase + Vercel)', '–', '₹3,900 / mo'),
        ('Gemini, per conversation', '–', '≈ ₹2'),
        ('Set-up + training', '₹25,000', '–'),
        ('Consent + legal review', '₹25,000', '–'),
        ('Support', '–', '₹10,000 / mo'),
        ('Per clinic (10 clinics)', '₹5,000', '₹1,600 / mo')]
t2 = deploy.shapes.add_table(len(crow), 3, Inches(5.9), Inches(1.2), Inches(6.85), Inches(3.6)).table
for j, wdt in enumerate([3.25, 1.6, 2.0]): t2.columns[j].width = Inches(wdt)
for i, row in enumerate(crow):
    for j, val in enumerate(row):
        c = t2.cell(i, j); c.text = val; c.fill.solid()
        c.fill.fore_color.rgb = rgb(GREEN if i == 0 else (BG['mint'] if i == len(crow) - 1 else 'FFFFFF'))
        c.margin_left = c.margin_right = Inches(0.1)
        for p in c.text_frame.paragraphs:
            for r in p.runs:
                r.font.name = 'Calibri'; r.font.size = Pt(15); r.font.bold = (i == 0 or i == len(crow) - 1)
                r.font.color.rgb = rgb('FFFFFF' if i == 0 else GREEN)
card(deploy, 5.9, 5.1, 6.85, 0.75, fill=GREEN)
text(deploy, 6.05, 5.1, 6.55, 0.75, [[R('Price: ₹1,500–2,000 per clinic a month', size=18, bold=True, color='FFFFFF')]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
text(deploy, 5.9, 6.0, 6.85, 1.0, [[R('Team estimates. US$1 ≈ ₹86; Supabase Pro $25, Vercel Pro $20 a month; Gemini ≈ $0.30 per million audio tokens, 30 min ≈ 58k tokens; 100 talks per clinic a month.', size=11.5, color=MUTED, italic=True)]])
notes(deploy, 'COST FIGURES ARE TEAM ESTIMATES from public list prices and an assumed Gemini audio price; confirm before submission. Per clinic monthly = (3,900 + 10,000) / 10 + 100 x 2 = ~1,590. One-time per clinic = 50,000 / 10 = 5,000.')

# ---------------------------------------------------------------- 12 what next
section(nxt, 'What Next? Plans to take initiative forward (if any)', 'lilac')
plan = [('1', 'Prove it', 'real phones · record with Dr Sharada', TEAL),
        ('2', 'Pilot 60–90 days', 'conversations signed · time to find wishes', RUST),
        ('3', 'Legal', 'capacity · partners · withdrawal', PURPLE),
        ('4', 'Fund + grow', 'grants · palliative networks · FHIR', BLUE)]
for i, (n, h, d, col) in enumerate(plan):
    x = 0.55 + i * 3.08
    card(nxt, x, 1.3, 2.93, 3.3, fill='FFFFFF')
    circ = nxt.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x + 1.06), Inches(1.55), Inches(0.8), Inches(0.8))
    circ.fill.solid(); circ.fill.fore_color.rgb = rgb(col); circ.line.fill.background()
    circ.text_frame.text = n; style_runs(circ, font='Georgia', size=26, bold=True, color='FFFFFF')
    circ.text_frame.paragraphs[0].alignment = PP_ALIGN.CENTER
    text(nxt, x + 0.15, 2.55, 2.63, 2.0, [([R(h, size=19, bold=True, color=col)], {'after': 6}), [R(d, size=15, color=MUTED)]], align=PP_ALIGN.CENTER)
    if i < 3: arrow(nxt, x + 2.95, 2.8, 0.13, 0.22)
card(nxt, 0.55, 5.0, 12.2, 1.9, fill='FFFFFF', line='D9C9E6')
text(nxt, 0.8, 5.1, 11.7, 1.7, [
    ([R('Share our solution publicly?   ', size=22, bold=True), R('YES', size=26, bold=True, color=TEAL)], {'after': 6}),
    [R('Slides, brief, video and app description. Not patient records, credentials or private code.', size=15, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

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
