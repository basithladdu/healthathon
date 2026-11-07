"""Sahara final presentation v3 (plain words, logos, calendar), built on the organisers' PPT_Final_Teamplate_Health-a-thon 2026.pptx.

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
TEAM_CODE = '37MYUP'
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
sh[4].text_frame.paragraphs[0].runs[0].text = ('A phone app for people with a serious illness, like advanced cancer, and their families. '
    'Their doctor discusses their end of life wishes; Sahara creates a clinical document, '
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
for s_ in cover.shapes:
    if s_.has_text_frame and s_.text_frame.text.endswith('TITLE'):
        for r in s_.text_frame.paragraphs[0].runs:
            if 'TITLE' in r.text: r.text = r.text.replace('TITLE', 'FINAL SUBMISSION')
pic(cover, B / 'assets/sahara-app-icon.png', 6.29, 2.55, 0.75, 0.75)
names = [('basith', 'Shaik Abdul Basith'), ('awaiz', 'Shaik Muhammad Awaiz'), ('sharada', 'Dr Sharada Vinod Kutty'), ('sujay', 'Dr Sujay Halkur Shankar'), ('shirin', 'Shirin Ayub')]
for s_ in (sh[5], sh[6]):
    s_.top = Inches(5.25); s_.height = Inches(0.4); s_.text_frame.vertical_anchor = MSO_ANCHOR.TOP
sh[1]._element.getparent().remove(sh[1]._element)   # the green rule; the app icon takes its place
text(cover, 0.8, 5.8, 11.7, 0.4, [[R('  ·  '.join(n for _, n in names), size=13, bold=True, color=GREEN)]], align=PP_ALIGN.CENTER)
text(cover, 1.2, 6.3, 10.9, 0.35, [[R('Try it: sahara.wedevit.in', size=13, color=LINK, link=MVP), R('   ·   Android app and website   ·   English, Hindi, Telugu, Urdu, Kannada', size=13, color=MUTED)]], align=PP_ALIGN.CENTER)

# ---------------------------------------------------------------- 2 team
section(team, 'Team Overview & Background', 'cream')
members = [
    ('basith', 'Shaik Abdul Basith', 'Technical lead · Founder', 'WeDevIT Private Limited · wedevit.in', 'Led the build end to end: the website and the Android app; the Gemini pipeline that transcribes the conversation, fills the goals-of-care document and answers questions from reports; the Supabase server, security and Google Play releases; the hospital survey site; this deck and brief'),
    ('awaiz', 'Shaik Muhammad Awaiz', 'Android engineer', 'WeDevIT', 'Built Android screens and flows: patient summary, side-effect notes, care timeline, doctor schedule, sign-up and family invites by link, the Today, Care and More redesign, welcome panel and screen transitions; AI retry and backup models; tested every version'),
    ('sharada', 'Dr Sharada Vinod Kutty', 'Pulmonologist', 'AIIMS Mangalagiri', 'Wrote Saanthvana v1.0, the goals-of-care structure the app grew from, then the clinical specification and platform spec (29 Oct) and the comfort-care rule. Designed the goals of care document; set how often patients log symptoms; reviewed the symptom list; tested the survey and shared it with colleagues; advised on medicolegal limits of AI'),
    ('sujay', 'Dr Sujay Halkur Shankar', 'Pulmonologist', 'Tata IISc Medical School', 'Defined the problem statement, evidence and solution, and wrote the final problem, solution and features text (7 Nov); distilled the goals-of-care form; designed and circulated the hospital survey; positioned Sahara as the patient-facing extension of CARE; arranged legal advice; tested the whole app (3 Nov); designed the 13-topic checklist and guided conversation (7 Nov); led the clinical wording review'),
    ('shirin', 'Shirin Ayub', 'Medical student', '', 'Designed the symptom log: the symptom list, how often to log, and the trend graph for the doctor. Defined caregiver portal needs and researched caregiver stress for the wellbeing check. Reworked the patient story in our first deck; asked hospitals about morphine access; UI clean up and usability inputs'),
]
for i, (f, n, role, inst, c) in enumerate(members):
    x = 0.55 + i * 2.48
    card(team, x, 1.1, 2.3, 4.6, fill='FFFFFF', line='E6DCC8')
    pic(team, PH / f'{f}.jpg', x + 0.5, 1.22, 1.3, 1.3)
    text(team, x + 0.08, 2.6, 2.14, 3.05, [
        ([R(n, size=13, bold=True)], {'after': 1}),
        ([R(role, size=12, bold=True, color=RUST)], {'after': 0}),
        ([R(inst or ' ', size=10.5, bold=True, color=TEAL)], {'after': 4}),
        [R(c, size=10, color=MUTED)]], align=PP_ALIGN.CENTER)
card(team, 0.55, 5.85, 12.2, 1.15, fill=BG['mint'])
icon(team, 11, 0.8, 5.95, 0.42); text(team, 1.35, 5.88, 5.3, 0.6, [[R('Our doctors ', size=13.5, bold=True), R('chose this problem from their own clinics, wrote the clinical rules and checked every version.', size=13)]], anchor=MSO_ANCHOR.MIDDLE)
icon(team, 28, 6.85, 5.95, 0.42); text(team, 7.4, 5.88, 5.3, 0.6, [[R('Legal advice was sought: ', size=13.5, bold=True), R('inputs and refining the goals of care wording and format.', size=13.5)]], anchor=MSO_ANCHOR.MIDDLE)
text(team, 0.8, 6.5, 11.8, 0.4, [[R('Built by WeDevIT, the team’s company. The app is on Google Play under WeDevIT.', size=12, color=MUTED)]])
notes(team, 'Doctor background from Dr Sujay (team chat, 25 Aug 2026): pulmonary specialists who have worked extensively with lung cancer; Dr Sujay has palliative-medicine exposure.')

# ---------------------------------------------------------------- 3 problem
section(prob, 'Problem Statement and Supporting Evidence', 'blue')
pic(prob, B / 'assets/consult-family.jpg', 0.55, 1.2, 5.6, 5.6 * 883 / 1600)
text(prob, 0.55, 4.36, 5.6, 0.3, [[R('A typical case: a 62-year-old woman with advanced breast cancer.', size=11, color=MUTED, italic=True)]])
story = [(4, 'At the clinic, ', 'her doctor asks what treatment she would want if she got very sick. Only her husband is there.'),
         (5, 'Two months later ', 'she is unconscious in the emergency room. Should doctors try to restart her heart (CPR)?'),
         (6, 'Her children disagree, ', 'and nobody can find what she said.')]
for i, (ic, a, b) in enumerate(story):
    y = 4.72 + i * 0.75
    icon(prob, ic, 0.55, y, 0.4)
    text(prob, 1.1, y - 0.04, 5.1, 0.75, [[R(a, size=13.5, bold=True), R(b, size=13.5)]])
stats = [(8, '98%', TEAL, 'of India’s metastatic (advanced) cancer patients had unmet palliative-care needs.', 'Patil, BMJ Support Palliat Care 2024', 'https://doi.org/10.1136/spcare-2024-004978'),
         (7, '30–37%', RUST, 'of patients get timely palliative care referrals (two centres).', 'Venketeswaran, BMJ Support Palliat Care 2026', 'https://spcare.bmj.com/content/16/2/339'),
         (10, '41%', BLUE, 'of cancer patients dying on active treatment had end-of-life care documented (one hospital).', 'Das, Cancer Med 2026', 'https://onlinelibrary.wiley.com/doi/10.1002/cam4.72004'),
         (9, '88%', PURPLE, 'of urban adults want to choose their own end-of-life care.', 'Living Will Survey, IndiaSpend 2019', 'https://scroll.in/article/925875/73-urban-indians-do-not-know-about-their-right-to-make-a-living-will-finds-a-survey')]
for i, (ic, big, col, desc, src, url) in enumerate(stats):
    x = 6.55 + (i % 2) * 3.15; y = 1.2 + (i // 2) * 1.85
    icon(prob, ic, x, y + 0.08, 0.45)
    text(prob, x + 0.55, y, 2.5, 0.6, [[R(big, size=28, bold=True, color=col)]])
    text(prob, x, y + 0.62, 2.95, 1.2, [[R(desc, size=12.5)], [R(src, size=10, color=LINK, link=url)]])
card(prob, 6.55, 5.0, 6.2, 1.95, fill='FFFFFF')
text(prob, 6.75, 5.08, 5.85, 1.85, [
    [R('What has to change', size=15, bold=True, color=RUST)],
    [R('Her goals of care need to be documented, signed, and found in seconds in an emergency.', size=16, bold=True)]])

# ---------------------------------------------------------------- 4 users
section(users, 'Target Users and Use Case', 'peach')
pic(users, B / 'assets/welcome-conversation.png', 0.35, 2.1, 3.0, 3.0)
rows = [(6, 'Patient', 'at home', 'Logs symptoms, medications, and helps find care; access to clinical documents'),
        (13, 'Family', 'at home', 'Access to their patient’s portal; logs specific caregiver issues'),
        (11, 'Their doctor', 'clinic', 'Guides goals of care discussions; access to patient information: symptom trends, reports, medications'),
        (10, 'Emergency doctor', 'emergency room', 'Quick retrieval of the latest goals of care discussion to guide emergency medical decisions')]
for i, (ic, who, where, need) in enumerate(rows):
    y = 1.2 + i * 0.98
    card(users, 3.5, y, 9.25, 0.88, fill='FFFFFF')
    icon(users, ic, 3.7, y + 0.17, 0.52)
    text(users, 4.45, y + 0.04, 8.2, 0.8, [
        ([R(who, size=16, bold=True), R('  ·  ' + where, size=13, color=RUST)], {'after': 0}),
        [R(need, size=12.5, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
card(users, 3.5, 5.18, 9.25, 1.8, fill=BG['cream'], line='E6CDB8')
text(users, 3.7, 5.22, 8.9, 0.35, [[R('Words used in this deck', size=13, bold=True, color=RUST)]])
gl = [('Palliative care: ', 'care that eases pain and distress in a serious illness.'),
      ('Goals of care: ', 'what treatment a patient wants, or refuses, near the end of life (CPR, ventilator, ICU).'),
      ('QR code: ', 'a square barcode on the patient’s phone or card; a phone camera scan opens their wishes.')]
for i, (a, b) in enumerate(gl):
    x = 3.7 + (i % 2) * 4.5; y = 5.55 + (i // 2) * 0.7
    text(users, x, y, 4.35, 0.7, [[R(a, size=11.5, bold=True), R(b, size=11.5)]])

# ---------------------------------------------------------------- 5 solution 1/2
section(sol1, 'Solution Overview, Key Features & Impact (1/2)', 'cream')
text(sol1, 0.55, 1.0, 12.2, 0.7, [
    ([R('In the clinic: the doctor talks, Sahara listens and writes. ', size=17, bold=True, color=RUST)], {'after': 0}),
    [R('After consent, the doctor leads the goals-of-care conversation and records it on the doctor portal. Today this is rarely documented.', size=13.5, color=MUTED)]])
steps = [('08-goc-open', '1. Patient agrees, tap Record', 'Google’s Gemini AI types out who said what'),
         ('09-goc-checklist', '2. A checklist ticks itself', 'Missed topics are flagged. Android: Dr Sujay’s 13 topics, with “Guide me” or “Free flow” taps'),
         ('10g-note', '3. Answers fill the structured clinical document', 'Doctor checks the transcription and summary'),
         ('11-qr', '4. Everyone signs on the phone', 'Doctor and patient or family sign; it locks and opens from a QR code in an emergency')]
for i, (n, h, d) in enumerate(steps):
    x = 0.85 + i * 3.1
    phone(sol1, n, x, 1.85, 3.9)
    text(sol1, x - 0.3, 5.88, 2.55, 1.2, [([R(h, size=14, bold=True)], {'after': 2}), [R(d, size=12.5, color=MUTED)]])
    if i < 3: arrow(sol1, x + 2.1, 3.65)
notes(sol1, 'Screens captured from the live website demo (sahara.wedevit.in, 5 Nov 2026) with a fictional patient (Meera Raghavan) and the fictional mock conversation docs/goc-mock-conversations/01-english.txt. The Android app has the same flow.')

# ---------------------------------------------------------------- 6 solution 2/2
section(sol2, 'Solution Overview, Key Features & Impact (2/2)', 'lilac')
text(sol2, 0.55, 0.98, 12.2, 0.62, [([R('At home, between visits: ', size=17, bold=True, color=RUST), R('patients and families log how things are going; their doctor sees the trends.', size=14, color=MUTED)], {'after': 0}), [R('Every feature is built on published guideline tools and validated questionnaires (ESAS-r symptoms, NCCN Distress Thermometer, ZBI-4 caregiver burden).', size=11.5, italic=True, color=GREEN)]])
for i, n in enumerate(['21-feel-scale', '23-answer', '22-findcare']):
    phone(sol2, n, 0.65 + i * 2.05, 1.7, 3.95)
feats = [(17, 'How I feel: ', 'symptoms (ESAS-r), a 0–10 distress scale (NCCN thermometer) and a pain body map; trends for the doctor.'),
         (8, 'Caregiver wellbeing (new): ', 'a short check for family; flagged with Tele-MANAS 14416 and support.'),
         (12, 'Medicines: ', 'pill tracker from prescription photos; tag pictures of your medicine strips for easy identification; dose ticks, reminders.'),
         (23, 'Documents: ', 'uploads sorted into prescriptions, bills, reports; questions answered with the line it came from.'),
         (29, 'Emergency: ', 'SOS button and a QR code with goals of care, contacts and medicines.'),
         (14, 'Help nearby: ', 'palliative-care centres and availability of morphine, emergency hospitals, ambulances, support groups.'),
         (9, 'Family: ', 'invited relatives share the record with their own access level; ability to manage caregiver concerns.')]
for i, (ic, a, b) in enumerate(feats):
    y = 1.62 + i * 0.6
    icon(sol2, ic, 6.95, y, 0.38)
    text(sol2, 7.45, y - 0.05, 5.4, 0.6, [[R(a, size=12, bold=True), R(b, size=12)]])
card(sol2, 0.55, 5.85, 12.2, 1.15, fill='FFFFFF')
text(sol2, 0.75, 5.92, 11.8, 1.05, [
    [R('Impact we will measure in the clinic pilot: ', size=14, bold=True, color=PURPLE)],
    [R('more of these talks written and signed  ·  wishes found in seconds in an emergency  ·  fewer family arguments at the bedside  ·  doctors see problems before the next visit.', size=14)]])

# ---------------------------------------------------------------- 7 technology
section(tech, 'Technology, Tools, and AI Approach', 'mint')
LG = B / 'assets/logos'
text(tech, 0.55, 0.98, 12.2, 0.4, [[R('How a conversation becomes a signed clinical document', size=16, bold=True, color=RUST)]])
flow = [(LG / 'android.png', 'Doctor’s phone', 'records only after patient consent'),
        (LG / 'supabase.png', 'Our server', 'keeps the AI password; the phone never has it'),
        (LG / 'googlegemini.png', 'Google Gemini AI', 'types out the talk and fills in the clinical document'),
        (IC / 'image11.png', 'Doctor', 'reads it, corrects it, signs; the patient or caregiver also signs'),
        (IC / 'image29.png', 'Locked document', 'PDF + QR code; any later change creates a new version that requires re-signatures')]
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
         ('googlegemini', 'Google Gemini', 'AI: speech to text, fills the document'), ('openai', 'OpenAI Whisper', 'speech to text with no internet'),
         ('googletranslate', 'Google ML Kit', '5 languages and read-aloud on the phone'), ('openstreetmap', 'OpenStreetMap', 'free map of nearby centres'),
         (None, 'Clinical tools', 'ESAS-r, NCCN Distress Thermometer, ZBI-4'), (None, 'FHIR', 'standard format hospital software can read')]
for i, (lg, h, d) in enumerate(stack):
    x = 0.55 + (i % 6) * 2.05; y = 4.45 + (i // 6) * 1.27
    card(tech, x, y, 1.93, 1.15, fill='FFFFFF', radius=0.12)
    if lg: pic(tech, LG / f'{lg}.png', x + 0.12, y + 0.14, 0.42, 0.42)
    else: icon(tech, 27 if h == 'Clinical tools' else 23, x + 0.12, y + 0.14, 0.42)
    text(tech, x + 0.62, y + 0.1, 1.27, 0.5, [[R(h, size=11.5, bold=True)]], anchor=MSO_ANCHOR.MIDDLE)
    text(tech, x + 0.12, y + 0.62, 1.75, 0.5, [[R(d, size=10, color=MUTED)]])
notes(tech, 'Logos from the simple-icons set (CC0), used only to name the tools we use. Gemini models: 3.1 Flash-Lite first, then other Flash models when busy; on-device Whisper; typing.')

# ---------------------------------------------------------------- 8 build sprint
section(sprint, 'Build Sprint Journey and Progress', 'sand')
import datetime as _dt
GC_TODAY = '1A73E8'
text(sprint, 0.55, 0.98, 6.0, 0.36, [[R('30 days  ·  500 commits  ·  2 apps live', size=16, bold=True, color=GREEN)]])
leg = [('Built', TEAL), ('Doctor / legal feedback', PURPLE), ('Milestone', RUST)]
lx = 4.6
for lbl, col in leg:
    card(sprint, lx, 1.07, 0.2, 0.2, fill=col, radius=0.5)
    text(sprint, lx + 0.25, 1.0, 2.0, 0.34, [[R(lbl, size=11, color=MUTED)]], anchor=MSO_ANCHOR.MIDDLE)
    lx += 0.45 + len(lbl) * 0.075
gx0, cw, rh, top = 1.2, 1.17, 1.06, 1.68
days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
for j, d in enumerate(days):
    text(sprint, gx0 + j * cw, 1.38, cw, 0.28, [[R(d, size=9.5, bold=True, color=MUTED)]], align=PP_ALIGN.CENTER)
ev = {  # (day, month): [(label, colour)] - from the git history and team chat
    (9, 10): [('Shortlisted!', RUST), ('Native Android + map', TEAL)],
    (10, 10): [('Play closed test sent', RUST), ('Safe account deletion', TEAL)],
    (11, 10): [('Prescription organiser', TEAL), ('Web features on Android', TEAL)],
    (13, 10): [('Play test version 0.4', RUST), ('Scanner verified', TEAL)],
    (14, 10): [('Closed testing live', RUST), ('Testers invited', TEAL)],
    (15, 10): [('Production submission', RUST)],
    (16, 10): [('Sahara 0.5 alpha', TEAL), ('Privacy and safety', TEAL)],
    (19, 10): [('Sent to Google Play', RUST), ('On-device processing', TEAL)],
    (20, 10): [('Mentor review', PURPLE), ('National care directory', TEAL)],
    (21, 10): [('Release verified', TEAL), ('Research archive', TEAL)],
    (22, 10): [('Daily health workflow', TEAL)],
    (23, 10): [('Hospital survey feedback', PURPLE), ('Phase 2 deck', TEAL)],
    (24, 10): [('Symptom list: Shirin, SVK', PURPLE), ('Production review', RUST)],
    (25, 10): [('Symptom tracker', TEAL), ('Navigation, feedback', TEAL)],
    (26, 10): [('Pain body map', TEAL), ('Motion and loading', TEAL)],
    (27, 10): [('Hospital survey live', RUST), ('Hindi + offline speech', TEAL)],
    (28, 10): [('Team meeting: 9 changes', PURPLE), ('Ask your documents', TEAL)],
    (29, 10): [('Dr Sharada’s clinical spec', PURPLE), ('Survey to doctors', RUST)],
    (30, 10): [('Goals of care + QR', TEAL), ('Telugu, Urdu, Kannada', TEAL)],
    (31, 10): [('Legal advice on format', PURPLE), ('Patient summary', TEAL)],
    (1, 11): [('Recording → text', TEAL), ('Today / Care redesign', TEAL)],
    (2, 11): [('Doctor schedule', TEAL), ('Patient profile', TEAL)],
    (3, 11): [('Dr Sujay tests whole app', PURPLE)],
    (4, 11): [('Clinical document', TEAL), ('8 practice talks pass', TEAL)],
    (5, 11): [('Gemini AI + backups', TEAL), ('Whisper + Gemini', TEAL)],
    (6, 11): [('Community groups', TEAL), ('910 tests pass', TEAL)],
    (7, 11): [('Dr Sujay’s 13 topics', PURPLE), ('Caregiver wellbeing', TEAL)],
}
start = _dt.date(2026, 10, 5) if _dt.date(2026, 10, 5).weekday() == 0 else _dt.date(2026, 10, 6)
start = _dt.date(2026, 10, 9) - _dt.timedelta(days=_dt.date(2026, 10, 9).weekday())
s0, s1 = _dt.date(2026, 10, 9), _dt.date(2026, 11, 7)
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
text(sprint, gx0, top + 5 * rh + 0.02, 7 * cw, 0.28, [[R('9 Oct – 7 Nov 2026. Blue circle: 7 Nov, the day of this submission. Every chip is a dated commit or meeting.', size=9.5, color=MUTED, italic=True)]])
boxes = [('✓  Live today', TEAL, 'Android app on Google Play · website · doctor portal · recording → signed clinical document · emergency QR · 5 languages · 910 passing tests'),
         ('↻  Changed on the way', PURPLE, 'Website first, then a real Android app. The AI used to write the note; now the doctor leads and the AI only writes it down.'),
         ('⚠  Problems we solved', RUST, 'Google’s AI was often busy → automatic backups. Exact goals-of-care wording → copied.'),
         ('→  Next steps', BLUE, 'Field testing on more phones · recorded practice talk with Dr Sharada · clinic pilot')]
for i, (h, col, body) in enumerate(boxes):
    y = 1.0 + i * 1.48
    card(sprint, 9.55, y, 3.2, 1.38, fill='FFFFFF')
    text(sprint, 9.7, y + 0.06, 2.95, 1.3, [([R(h, size=13, bold=True, color=col)], {'after': 3}), [R(body, size=11, color=GREEN)]])
notes(sprint, 'Dates from the git history and team chat: shortlisted 9 Oct; native Android 9 Oct; Play testing 13-14 Oct; Play submission 19 Oct; survey feedback 23 Oct; survey live 27 Oct; team meeting 28 Oct; clinical spec 29 Oct; legal review 31 Oct; Dr Sujay walkthrough 3 Nov; clinical document word for word 4 Nov; Gemini fallback 5 Nov.')

# ---------------------------------------------------------------- 9 evaluation
section(evals, 'Evaluation and User Validation', 'blue')
text(evals, 0.55, 0.98, 12.2, 0.38, [[R('1. Does it work? Our own tests', size=16, bold=True, color=RUST)]])
tiles = [('877', 'automatic checks on the Android app: all pass', TEAL), ('594 / 594', 'automatic checks on the website: all pass', BLUE),
         ('8 / 8', 'practice doctor–patient talks in 6 languages: every answer landed in the right place on the document', RUST),
         ('4 / 4', 'questions about a sample hospital report answered correctly', PURPLE)]
for i, (big, lbl, col) in enumerate(tiles):
    x = 0.55 + i * 3.08
    card(evals, x, 1.38, 2.93, 1.75, fill='FFFFFF')
    text(evals, x + 0.15, 1.4, 2.63, 0.7, [[R(big, size=32, bold=True, color=col, font='Georgia')]], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
    text(evals, x + 0.15, 2.1, 2.63, 1.0, [[R(lbl, size=12, color=MUTED)]], align=PP_ALIGN.CENTER)
text(evals, 0.55, 3.15, 12.2, 0.3, [[R('Automatic checks are small test programs that run after every change, so a broken feature is caught before release. Practice talks use scripted conversations, so no patient data is exposed.', size=10.5, color=MUTED, italic=True)]])
text(evals, 0.55, 3.5, 12.2, 0.38, [[R('2. What experts told us, and what we changed', size=16, bold=True, color=RUST)]])
fb = [(11, 'Dr Sujay Halkur Shankar', 'doctor, used the whole app on 3 Nov',
       'Is there a guide telling the doctor what to talk about next? And a medicine taken 4 times a day needs 4 ticks.',
       'Checklist in the doctors’ order with a “not yet discussed” prompt; one tick per dose. Team chat: tubes-and-drains tracker, darker text.'),
      (23, 'Dr Sharada Vinod Kutty', 'doctor, wrote our medical rules on 29 Oct',
       'Comfort care must always continue, whatever treatment a patient refuses.',
       'The app shows this on every signed document.')]
for i, (ic, who, role, said, did) in enumerate(fb):
    x = 0.55 + i * 6.17
    card(evals, x, 3.92, 6.03, 2.0, fill='FFFFFF')
    icon(evals, ic, x + 0.15, 4.02, 0.42)
    text(evals, x + 0.65, 3.98, 5.2, 0.55, [([R(who, size=12.5, bold=True)], {'after': 0}), [R(role, size=10.5, color=MUTED)]])
    text(evals, x + 0.15, 4.55, 5.75, 1.35, [([R('Said: ', size=11.5, bold=True, color=PURPLE), R(said, size=11.5)], {'after': 4}),
                                           [R('We changed: ', size=11.5, bold=True, color=TEAL), R(did, size=11.5)]])
card(evals, 0.55, 6.08, 12.2, 0.92, fill=BG['lilac'])
text(evals, 0.8, 6.1, 11.8, 0.88, [
    ([R('Next steps: ', size=14, bold=True, color=PURPLE),
      R('clinic pilot with patients  ·  field testing on more phones  ·  speech-to-text on Hindi, Telugu and Kannada clinic recordings  ·  doctor review of every translation', size=13)], {'after': 0})], anchor=MSO_ANCHOR.MIDDLE)
notes(evals, 'Unit tests: HANDOFF 5 Nov (Android 877, web 594/594). Mock conversations: docs/goc-mock-conversations, English, Hindi, Hinglish, Telugu, Urdu, Kannada, disagreement, short talk; both matchers pass all 8. Ask documents: 4 questions on a practice discharge PDF (one needed a retry). Feedback: 3 Nov meeting transcript and team chat to 4 Nov (tubes and lines, faded text, checklist order); legal review 31 Oct; clinical spec 29 Oct.')

# ---------------------------------------------------------------- 10 safety
section(safety, 'Safety and Responsible AI', 'mint')
quads = [(27, 'Privacy and consent', TEAL, ['Recording starts only after the patient ticks “I agree”', 'The AI is used only with that consent', 'Each family sees only their own records']),
         (21, 'Limits on the AI', RUST, ['The AI never suggests treatment', 'If a topic was not discussed, it says so; it does not guess', 'If the AI is down, the phone or the doctor writes it']),
         (30, 'A person decides', PURPLE, ['The doctor reads, corrects and signs first', 'The patient or family signs too', 'Any later change creates a new version requiring re-signatures']),
         (13, 'For everyone', BLUE, ['5 Indian languages, and it reads text aloud', 'Sign with a thumbprint; large text', 'Demos use sample patients, so no real data is exposed'])]
for i, (ic, h, col, items) in enumerate(quads):
    x = 0.55 + (i % 2) * 6.15; y = 1.15 + (i // 2) * 2.95
    card(safety, x, y, 6.0, 2.8, fill='FFFFFF')
    icon(safety, ic, x + 0.25, y + 0.25, 0.6)
    text(safety, x + 1.05, y + 0.25, 4.8, 0.6, [[R(h, size=20, bold=True, color=col)]], anchor=MSO_ANCHOR.MIDDLE)
    text(safety, x + 0.35, y + 1.0, 5.5, 1.75, [([R('✓  ' + t, size=15)], {'after': 6}) for t in items])

# ---------------------------------------------------------------- 11 deployment
section(deploy, 'Deployment, Cost and Sustainability Plan', 'peach')
steps_ = [(11, 'Pilot', 'Start in 1 clinic with our own doctors. Patients give consent as India’s data-protection law (DPDP Act) requires.'),
          (23, 'Fitting in', 'Today it records on the doctor’s phone. Next: ambient listening, with room or lapel microphones in clinics. Later, FHIR export for hospital software.'),
          (18, 'Who pays', 'Clinics or charities pay a monthly fee. Free for patients and families.'),
          (9, 'Growing', 'Grants first, then palliative-care networks across India.')]
for i, (ic, h, d) in enumerate(steps_):
    y = 1.2 + i * 1.45
    card(deploy, 0.55, y, 5.15, 1.3, fill='FFFFFF')
    icon(deploy, ic, 0.75, y + 0.35, 0.55)
    text(deploy, 1.5, y + 0.06, 4.1, 1.2, [([R(h, size=15, bold=True, color=RUST)], {'after': 1}), [R(d, size=12, color=GREEN)]], anchor=MSO_ANCHOR.MIDDLE)
crow = [('What it costs', 'Once', 'Every month'),
        ('Servers and website (Supabase + Vercel)', '–', '₹4,200'),
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
text(deploy, 5.9, 6.0, 6.85, 1.0, [[R('Based on public prices: US$1 ≈ ₹94; Supabase $25 and Vercel $20 a month; Gemini ≈ $0.30 per million audio units (a 30-minute talk ≈ 58,000); 100 talks per clinic a month.', size=11, color=MUTED, italic=True)]])
notes(deploy, 'COST FIGURES ARE TEAM ESTIMATES from public list prices and an assumed Gemini audio price; confirm before submission. US$1 = Rs 94 (Dr Sujay, 7 Nov). Servers 45 x 94 = ~4,230. Per clinic monthly = (4,230 + 10,000) / 10 + 100 x 2 = ~1,620. One-time per clinic = 50,000 / 10 = 5,000.')

# ---------------------------------------------------------------- 12 what next
section(nxt, 'What Next? Plans to take initiative forward (if any)', 'lilac')
plan = [('1', 'Field testing', 'Test on more phones; try room and lapel microphones (ambient listening). Record a real practice talk with Dr Sharada.', TEAL),
        ('2', 'Pilot, 60–90 days', 'One clinic. Count how many talks get signed and how fast wishes are found.', RUST),
        ('3', 'Legal rules', 'What to do when a patient cannot decide, who may sign for them, and how a patient changes their goals of care.', PURPLE),
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
card(nxt, 0.55, 4.85, 12.2, 0.8, fill=BG['mint'])
text(nxt, 0.8, 4.85, 11.7, 0.8, [[R('Next version of the app: ', size=15, bold=True, color=TEAL), R('more features such as devices like feeding tubes  ·  an improved caregiver portal  ·  updates based on user feedback', size=14)]], anchor=MSO_ANCHOR.MIDDLE)
card(nxt, 0.55, 5.8, 12.2, 1.0, fill='FFFFFF', line='D9C9E6')
text(nxt, 0.8, 5.82, 11.7, 0.96, [
    [R('Can the organisers share our solution publicly?   ', size=18, bold=True), R('YES', size=22, bold=True, color=TEAL)]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

# ---------------------------------------------------------------- 13 thank you
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
style_runs(th[5], color=MUTED)
th[0].left = Inches(0.55); th[0].top = Inches(0.3); th[0].width = Inches(2.9); th[0].height = Inches(2.9 * th[0].height / th[0].width) if False else Inches(0.4)
th[1].left = Inches(3.6); th[1].width = Inches(6.1); th[1].top = Inches(0.2); th[1].height = Inches(0.65)
style_runs(th[1], font='Georgia', size=30, bold=True, color=GREEN)
th[2].left = Inches(3.6); th[2].width = Inches(6.1); th[2].top = Inches(0.82); th[2].height = Inches(0.35)
style_runs(th[2], font='Calibri', size=15, bold=True, color=RUST)
text(thanks, 10.1, 0.28, 2.7, 0.6, [[R('dev', size=30, bold=False, color='E8333A', font='Consolas'), R('it.', size=30, color='111111', font='Consolas')]], align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)
# two link cards, each with the Google Play mark and its own QR code
cards_ = [(0.55, BG['mint'], '1C7A55', 'Website: doctor, patient and family portal', 'sahara.wedevit.in', B / 'assets/qr-web.png', MVP),
          (6.75, 'E8EEFC', '2A55C4', 'Android app on Google Play', 'play.google.com  ·  in.wedevit.sahara', B / 'assets/qr-app.png', 'https://play.google.com/store/apps/details?id=in.wedevit.sahara')]
for x, fill, col, h, sub, qr, url in cards_:
    card(thanks, x, 1.35, 6.03, 1.75, fill=fill, line=fill)
    pic(thanks, B / 'assets/google-play.png', x + 0.25, 1.6, 0.42, 0.46)
    text(thanks, x + 0.82, 1.5, 3.4, 1.4, [([R(h, size=15, bold=True, color=col)], {'after': 3}), [R(sub, size=13, bold=True, color=GREEN, link=url)], [R('Scan the code to open', size=11, color=MUTED)]])
    pic(thanks, qr, x + 6.03 - 1.6, 1.43, 1.5, 1.5)
# Play Store listing, big, with a soft frame and caption
card(thanks, 0.55, 3.25, 12.23, 3.05, fill='FFFFFF', line='D9D2C3', radius=0.05)
pic(thanks, B / 'assets/play-listing-wide.png', 0.7, 3.35, 11.93, 11.93 * 520 / 2150)
text(thanks, 0.55, 6.32, 12.23, 0.3, [[R('Live on Google Play: “Sahara: Palliative Companion” by WEDEVIT PRIVATE LIMITED', size=11, italic=True, color=MUTED)]])
th[3].top = Inches(6.6); th[3].height = Inches(0.35)
for r in th[3].text_frame.paragraphs[0].runs: r.font.size = Pt(11.5)
th[4].text_frame.paragraphs[0].runs[0].text = ''
STRIPE = ['2A6BCF', '1C8A5A', 'D96B2B', '7B4FC4']
for sl in prs.slides:
    w = 13.333 / 4
    for k, col in enumerate(STRIPE):
        bar = sl.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(k * w), 0, Inches(w + 0.01), Inches(0.07))
        bar.fill.solid(); bar.fill.fore_color.rgb = rgb(col); bar.line.fill.background()
    text(sl, 5.67, 7.08, 2.0, 0.3, [[R('dev', size=11, color='E8333A', font='Consolas'), R('it.', size=11, color='111111', font='Consolas')]], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)

import re
for part in prs.part.package.iter_parts():
    if '/theme/' in str(part.partname):
        x = part.blob.decode('utf-8')
        for tag in ('hlink', 'folHlink'):
            x = re.sub(rf'<a:{tag}>.*?</a:{tag}>', f'<a:{tag}><a:srgbClr val="{LINK}"/></a:{tag}>', x, flags=re.S)
        part._blob = x.encode('utf-8')
prs.save(OUT)
print('saved', OUT, 'slides', len(prs.slides))
