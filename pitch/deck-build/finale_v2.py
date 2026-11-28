import copy
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR

SRC = r"F:\CODE\healthathon\output\final-submission\Sahara-Final-Presentation.pptx"
OUT = r"F:\CODE\healthathon\output\final-submission\Sahara-Grand-Finale-v2.pptx"
D = r"C:\Users\basit\AppData\Local\Temp\claude\47b2f2e7075728d7\25bf6a30-9b33-460f-9705-10c566fe1061\scratchpad\deck"
COMIC = r"C:\Users\basit\sahara-video\out\comic"
LOGO = r"F:\CODE\healthathon\video\public\brand\sahara-logo.png"
DEVIT = D + r"\devit.png"
OPENAI = D + r"\openai.png"
SARVAM = r"C:\Users\basit\sahara-video\public\brand\sarvam.jpeg"

INK = RGBColor(0x17, 0x2B, 0x2A)
SUB = RGBColor(0x4A, 0x5A, 0x58)
TEAL = RGBColor(0x14, 0x6C, 0x60)
TEAL_BG = RGBColor(0xE3, 0xF1, 0xEC)
BLUE = RGBColor(0x2F, 0x4E, 0x9A)
BLUE_BG = RGBColor(0xE6, 0xEC, 0xF8)
RUST = RGBColor(0xC2, 0x4E, 0x2B)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
FONT = "Calibri"

p = Presentation(SRC)
S = list(p.slides)
W, H = p.slide_width, p.slide_height


def shape(s, sid):
    for sh in s.shapes:
        if sh.shape_id == sid:
            return sh
    raise KeyError(sid)


def drop(s, *sids):
    for sid in sids:
        el = shape(s, sid)._element
        el.getparent().remove(el)


def set_par(par, text):
    if not par.runs:
        par.text = text; return
    par.runs[0].text = text
    for r in par.runs[1:]:
        r._r.getparent().remove(r._r)


def set_pars(sh, texts):
    pars = [q for q in sh.text_frame.paragraphs if q.text.strip()] or list(sh.text_frame.paragraphs)
    while len(pars) < len(texts):  # clone the last paragraph
        new = copy.deepcopy(pars[-1]._p); pars[-1]._p.addnext(new)
        pars = [q for q in sh.text_frame.paragraphs if q.text.strip()] + [sh.text_frame.paragraphs[-1]]
        pars = list(dict.fromkeys(pars))
    for q, t in zip(pars, texts):
        set_par(q, t)
    for q in pars[len(texts):]:
        q._p.getparent().remove(q._p)


def text(s, x, y, w, h, runs, size=16, color=INK, bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, font=FONT):
    """runs: str, or list of paragraphs; a paragraph is str or list of (text, {opts})."""
    tb = s.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame; tf.word_wrap = True; tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = Inches(0.05); tf.margin_top = tf.margin_bottom = Inches(0.03)
    paras = runs if isinstance(runs, list) else [runs]
    for i, para in enumerate(paras):
        q = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        q.alignment = align
        parts = para if isinstance(para, list) else [(para, {})]
        for t, o in parts:
            r = q.add_run(); r.text = t
            f = r.font; f.name = o.get("font", font); f.size = Pt(o.get("size", size)); f.bold = o.get("bold", bold)
            f.color.rgb = o.get("color", color)
        if isinstance(para, list) and para and "space" in para[0][1]:
            q.space_before = Pt(para[0][1]["space"])
    return tb


def box(s, x, y, w, h, fill, line=None, radius=0.08):
    b = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, y, w, h)
    b.adjustments[0] = radius
    b.fill.solid(); b.fill.fore_color.rgb = fill
    if line is None: b.line.fill.background()
    else: b.line.color.rgb = line; b.line.width = Pt(1)
    b.shadow.inherit = False
    return b


def pill(s, x, y, label, fill, color=WHITE, w=None):
    w = w or Inches(0.12 + 0.085 * len(label))
    b = box(s, x, y, w, Inches(0.3), fill, radius=0.5)
    tf = b.text_frame; tf.margin_top = tf.margin_bottom = 0
    q = tf.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
    r = q.add_run(); r.text = label; r.font.size = Pt(11); r.font.bold = True; r.font.color.rgb = color; r.font.name = FONT
    return b


def clean(s, title=None):
    """Every slide: no Health-a-thon logo, no devit. mark, Grand Finale footer, Sahara logo top right."""
    for sh in list(s.shapes):
        isl = sh.shape_type == 13 and abs(sh.left - Inches(10.8)) < Inches(0.05) and sh.top < Inches(0.4)
        dev = sh.has_text_frame and sh.text_frame.text.strip() == "devit."
        if isl or dev:
            sh._element.getparent().remove(sh._element)
        elif sh.has_text_frame and "HEALTH-A-THON 2026" in sh.text_frame.text:
            set_pars(sh, ["SAHARA  \u2022  ZEROS AND ONES  \u2022  GRAND FINALE, IIT BOMBAY"])
    s.shapes.add_picture(LOGO, Inches(11.55), Inches(0.2), Inches(0.42), Inches(0.42))
    text(s, Inches(12.0), Inches(0.22), Inches(1.2), Inches(0.4), "Sahara", size=18, bold=True, color=TEAL, anchor=MSO_ANCHOR.MIDDLE)
    if title:
        for sh in s.shapes:
            if sh.has_text_frame and sh.top < Inches(0.6) and sh.left < Inches(1) and sh.text_frame.text.strip():
                set_pars(sh, [title]); break


def new_slide(title):
    s = p.slides.add_slide(S[8].slide_layout)
    for ph in list(s.placeholders):
        ph._element.getparent().remove(ph._element)
    # copy the template's colour strips and title box/footer from a content slide
    src = S[9]
    for sh in src.shapes:
        keep = (sh.shape_type == 1 and sh.top == 0 and sh.height < Inches(0.1)) or \
               (sh.has_text_frame and ("Responsible AI" in sh.text_frame.text or "Safe by Design" in sh.text_frame.text or "HEALTH-A-THON" in sh.text_frame.text or "GRAND FINALE" in sh.text_frame.text))
        if keep:
            s.shapes._spTree.append(copy.deepcopy(sh._element))
    # background like the content slides
    bg = src.background._cSld.bg if hasattr(src.background, "_cSld") else None
    srcbg = src._element.cSld.bg
    if srcbg is not None:
        s._element.cSld.insert(0, copy.deepcopy(srcbg))
    clean(s, title)
    return s


# ---------- 1 Title ----------
t = S[0]
drop(t, 86, 95)                                  # white Health-a-thon box, member names
for sh in list(t.shapes):
    if sh.has_text_frame and sh.text_frame.text.strip() == "devit.":
        sh._element.getparent().remove(sh._element)
set_pars(shape(t, 88), ["GRAND FINALE  \u2022  IIT BOMBAY"])
shape(t, 88).top = Inches(0.75)
icon = shape(t, 94); icon._element.getparent().remove(icon._element)
t.shapes.add_picture(LOGO, Inches(5.42), Inches(1.25), Inches(2.5), Inches(2.5))
shape(t, 89).top = Inches(3.75)
set_pars(shape(t, 90), ["A palliative care companion"])
shape(t, 90).top = Inches(4.55); shape(t, 90).height = Inches(0.6)
for q in shape(t, 90).text_frame.paragraphs:
    for r in q.runs: r.font.size = Pt(26)
set_pars(shape(t, 96), ["sahara.wedevit.in   \u00B7   Android app and website"])
shape(t, 96).top = Inches(6.2)
set_pars(shape(t, 93), ["SAHARA  \u2022  ZEROS AND ONES  \u2022  GRAND FINALE, IIT BOMBAY"])

# ---------- 2 Team ----------
tm = S[1]
clean(tm, "The Team")
drop(tm, 116, 117, 118, 119, 120, 121, 122, 123)
people = {
    103: ("Shaik Abdul Basith", "Technical lead \u00B7 Founder", "WeDevIT Private Limited", "wedevit.in", "tech"),
    106: ("Shaik Muhammad Awaiz", "Android engineer", "WeDevIT Private Limited", "wedevit.in", "tech"),
    109: ("Dr Sharada Vinod Kutty", "Pulmonologist", "AIIMS Mangalagiri", "", "doc"),
    112: ("Dr Sujay Halkur Shankar", "Pulmonologist", "Tata IISc Medical School", "", "doc"),
    115: ("Shirin Ayub", "Medical student", "Bhaskar Medical College, Hyderabad", "", "doc"),
}
cards = {103: 101, 106: 104, 109: 107, 112: 110, 115: 113}
for tid, (name, role, inst, web, kind) in people.items():
    card = shape(tm, cards[tid])
    card.top = Inches(1.35); card.height = Inches(4.45)
    card.fill.solid(); card.fill.fore_color.rgb = BLUE_BG if kind == "tech" else TEAL_BG
    card.line.color.rgb = BLUE if kind == "tech" else TEAL
    tb = shape(tm, tid); tb._element.getparent().remove(tb._element)
    x = card.left
    pic = shape(tm, tid - 1); pic.top = Inches(1.6)
    if tid == 106:  # new photo of Awaiz
        l_, t_, w_, h_ = pic.left, pic.top, pic.width, pic.height
        pic._element.getparent().remove(pic._element)
        tm.shapes.add_picture(D + "/awaiz.jpg", l_, t_, w_, h_)
    tone = BLUE if kind == "tech" else TEAL
    pill(tm, x + Inches(0.3), Inches(1.0), "\U0001F4BB  TECHNOLOGY" if kind == "tech" else "\U0001FA7A  CLINICAL", tone, w=Inches(1.7))
    text(tm, x + Inches(0.08), Inches(3.05), card.width - Inches(0.16), Inches(2.6), [
        [(name, {"size": 17, "bold": True, "color": INK})],
        [(role, {"size": 13, "color": SUB, "space": 4})],
        [(inst, {"size": 15, "bold": True, "color": tone, "space": 10})],
    ] + ([[(web, {"size": 12, "color": tone})]] if web else []), align=PP_ALIGN.CENTER)
text(tm, Inches(0.55), Inches(6.05), Inches(8), Inches(0.5),
     [[("Three clinicians and two engineers met at Health-a-thon.", {"size": 15, "color": SUB})]])
text(tm, Inches(8.9), Inches(6.05), Inches(1.4), Inches(0.5), "Built by", size=15, color=SUB, align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)
tm.shapes.add_picture(DEVIT, Inches(10.4), Inches(6.02), Inches(1.95), Inches(0.5))

# ---------- 3 Problem (comic) ----------
pr = new_slide("The Problem")
pw, ph = Inches(4.35), Inches(2.45)
for i in range(4):
    x = Inches(0.55) + (i % 2) * (pw + Inches(0.15)); y = Inches(1.1) + (i // 2) * (ph + Inches(0.15))
    pic = pr.shapes.add_picture(COMIC + rf"\p{i+1}.png", x, y, pw, ph)
    pic.line.color.rgb = INK; pic.line.width = Pt(2)
    n = pr.shapes.add_shape(MSO_SHAPE.OVAL, x + Inches(0.1), y + Inches(0.1), Inches(0.38), Inches(0.38))
    n.fill.solid(); n.fill.fore_color.rgb = WHITE; n.line.color.rgb = INK
    q = n.text_frame.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
    r = q.add_run(); r.text = str(i + 1); r.font.bold = True; r.font.size = Pt(12); r.font.color.rgb = INK
b = box(pr, Inches(9.65), Inches(1.1), Inches(3.15), Inches(5.05), TEAL)
text(pr, Inches(9.85), Inches(1.35), Inches(2.8), Inches(4.6), [
    [("What has to change", {"size": 15, "bold": True, "color": RGBColor(0xCF, 0xEA, 0xE1)})],
    [("Her goals of care need to be documented, signed, and found in seconds in an emergency.", {"size": 24, "bold": True, "color": WHITE, "space": 10})],
])

# ---------- 4 Evidence (from old slide 3) ----------
ev = S[2]
clean(ev, "Supporting Evidence")
drop(ev, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 136, 137)
# hero 98%
for sid, (x, y) in {120: (0.6, 1.3), 121: (0.69, 1.39)}.items():
    s_ = shape(ev, sid); s_.left = Inches(x); s_.top = Inches(y)
hero = shape(ev, 122); hero.left = Inches(0.55); hero.top = Inches(1.85); hero.width = Inches(6.2); hero.height = Inches(2.6)
for q in hero.text_frame.paragraphs:
    for r in q.runs: r.font.size = Pt(150); r.font.bold = True
cap = shape(ev, 123); cap.left = Inches(0.6); cap.top = Inches(4.6); cap.width = Inches(5.6); cap.height = Inches(1.6)
for i, q in enumerate(cap.text_frame.paragraphs):
    for r in q.runs: r.font.size = Pt(22 if i == 0 else 12)
rows = [(124, 125, 126, 127), (128, 129, 130, 131), (132, 133, 134, 135)]
for k_, (o_, ic_, num_, txt_) in enumerate(rows):
    for q in shape(ev, num_).text_frame.paragraphs:
        for r in q.runs: r.font.size = Pt(44); r.font.bold = True
    shape(ev, num_).height = Inches(0.8); shape(ev, num_).width = Inches(4)
    tt = shape(ev, txt_); tt.top = tt.top + Inches(0.2)
    for i_, q in enumerate(tt.text_frame.paragraphs):
        for r in q.runs: r.font.size = Pt(18 if i_ == 0 else 11)
    lab = {0: "ONLY", 2: "BUT"}.get(k_)
    if lab:
        text(ev, Inches(7.55), Inches(1.45 + k_ * 1.85 - 0.3), Inches(2), Inches(0.35), lab, size=14, bold=True, color=RUST)
for k, (o, ic, num, txt) in enumerate(rows):
    y = 1.45 + k * 1.85
    for sid, dx, dy in ((o, 0, 0.08), (ic, 0.09, 0.17), (num, 0.55, 0)):
        s_ = shape(ev, sid); s_.left = Inches(7.0 + dx); s_.top = Inches(y + dy)
    t_ = shape(ev, txt); t_.left = Inches(7.0); t_.top = Inches(y + 0.62); t_.width = Inches(5.7); t_.height = Inches(1.1)

# ---------- 5 Target users (old slide 4), vertical cards ----------
tu = S[3]
clean(tu, "Who Sahara Is For")
drop(tu, 117, 134, 135, 136, 137, 138)
roles = [
    (118, 119, 120, 121, "Patient", "at home", ["Symptoms, pain and distress", "Multilingual", "Shares the care with a caregiver", "Simple Mode"]),
    (122, 123, 124, 125, "Caregiver", "at home, with the patient", ["Everyone stays in the loop", "Medicine and visit reminders", "Their own wellbeing check"]),
    (126, 127, 128, 129, "Their doctor", "clinic", ["Leads the goals-of-care talk", "Sees symptom trends", "Signs the plan"]),
    (130, 131, 132, 133, "Emergency doctor", "emergency room", ["Scans the QR", "Signed wishes in seconds", "No login needed"]),
]
cw = Inches(2.95)
for k, (card, ov, ic, tb, name, where, items) in enumerate(roles):
    x = Inches(0.55) + k * (cw + Inches(0.13))
    c = shape(tu, card); c.left = x; c.top = Inches(1.15); c.width = cw; c.height = Inches(5.2)
    o = shape(tu, ov); o.left = x + Inches(0.25); o.top = Inches(1.4); o.width = o.height = Inches(0.7)
    i_ = shape(tu, ic); i_.left = x + Inches(0.4); i_.top = Inches(1.55); i_.width = i_.height = Inches(0.4)
    t_ = shape(tu, tb); t_._element.getparent().remove(t_._element)
    text(tu, x + Inches(0.2), Inches(2.25), cw - Inches(0.4), Inches(4.5),
         [[(name, {"size": 24, "bold": True})], [(where.upper(), {"size": 12, "bold": True, "color": SUB, "space": 2})]] +
         [[("\u2022  " + it, {"size": 18, "color": INK, "space": 14})] for it in items])
text(tu, Inches(0.55), Inches(6.38), Inches(12.25), Inches(0.62), [[
    ("Started as a doctor-facing app; then we asked why a family would install it.   ", {"size": 14, "bold": True, "color": TEAL}),
    ("Patient, caregiver and doctor logins, plus the emergency doctor  \u00B7  website first, then the Android app", {"size": 14, "color": SUB})]],
    anchor=MSO_ANCHOR.MIDDLE)

# ---------- 6 Goals of care: demo ----------
dm = new_slide("The Goals-of-Care Document, Made in the Room")
v = box(dm, Inches(0.55), Inches(1.1), Inches(8.0), Inches(4.5), RGBColor(0x1E, 0x2B, 0x3A))
text(dm, Inches(0.55), Inches(1.1), Inches(8.0), Inches(4.5), [
    [("\u25B6", {"size": 54, "color": WHITE})],
    [("Our recorded talk: the audio with captions,", {"size": 18, "color": WHITE, "space": 8})],
    [("then the document typing itself out", {"size": 18, "color": WHITE})],
    [("(video goes here)", {"size": 12, "color": RGBColor(0xA8, 0xB8, 0xC8), "space": 8})],
], align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
steps = [("1", "Agree, then record", "A guided or free-flow talk; the doctor leads, Sahara listens."),
         ("2", "Sarvam AI writes it down", "Who said what, in 23 languages."),
         ("3", "Covers 13 topics", "From the Indian palliative care and critical care societies’ guidelines."),
         ("4", "Doctor and caregiver sign", "Locked; opens from a QR in an emergency.")]
for k, (n, h, d) in enumerate(steps):
    y = Inches(1.1) + k * Inches(1.08)
    o = dm.shapes.add_shape(MSO_SHAPE.OVAL, Inches(8.85), y + Inches(0.05), Inches(0.5), Inches(0.5))
    o.fill.solid(); o.fill.fore_color.rgb = TEAL; o.line.fill.background()
    q = o.text_frame.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
    r = q.add_run(); r.text = n; r.font.bold = True; r.font.size = Pt(14); r.font.color.rgb = WHITE
    text(dm, Inches(9.5), y, Inches(3.3), Inches(1.1), [[(h, {"size": 16, "bold": True})], [(d, {"size": 13, "color": SUB})]])
text(dm, Inches(9.45), Inches(4.95), Inches(3.35), Inches(0.3), "Legal advice taken on wording and format.", size=11, color=SUB)
text(dm, Inches(0.55), Inches(5.62), Inches(12.25), Inches(0.38), [[
    ("Medical credits   ", {"size": 13, "bold": True, "color": TEAL}),
    ("Dr Sujay Halkur Shankar: framework and 13 topics   ·   Dr Sharada Vinod Kutty: clinical specification and emergency care plan", {"size": 13, "color": SUB})]],
    anchor=MSO_ANCHOR.MIDDLE)
q = box(dm, Inches(0.55), Inches(6.02), Inches(12.25), Inches(0.85), TEAL_BG)
text(dm, Inches(0.75), Inches(6.02), Inches(11.9), Inches(0.85),
     [[("India\u2019s first goals-of-care plan that the patient, caregiver and doctor build together, sign, update, and carry to the emergency room.", {"size": 18, "bold": True, "color": TEAL})]],
     anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

# ---------- 7 Clinically validated tools ----------
vt = new_slide("Goals of Care Is Only the Start: Validated Tools")
LINE = RGBColor(0xD5, 0xDE, 0xDB)
tools = [("ESAS-r", "Edmonton Symptom Assessment System", "9 symptoms, each 0–10", D + r"\tool1.png", D + r"\still-f08-symptoms.png", "Patient", TEAL),
         ("NCCN Distress Thermometer", "", "Distress 0–10 + problem list", D + r"\tool2.png", D + r"\still-f14-patient-today.png", "Patient", TEAL),
         ("Zarit Caregiver Burden (ZBI-4)", "", "4 questions, Never to Nearly always (0–4)", None, D + r"\still-f14-carer-burden-ui.png", "Caregiver", RUST)]
cw_, y0 = Inches(3.95), Inches(1.1)
for k, (name, sub, line, paper, phone, who, tone) in enumerate(tools):
    x = Inches(0.55) + k * (cw_ + Inches(0.2))
    box(vt, x, y0, cw_, Inches(4.9), WHITE, line=LINE)
    text(vt, x + Inches(0.15), y0 + Inches(0.08), cw_ - Inches(0.3), Inches(0.32),
         [[("Paper form  →  Sahara screen", {"size": 12, "bold": True, "color": TEAL})]])
    px, pw_, ph_ = x + Inches(0.15), Inches(1.9), Inches(1.59)
    py = y0 + Inches(0.45) + Inches(1.4) - ph_ / 2
    if paper:
        pic = vt.shapes.add_picture(paper, px, py, pw_, ph_); pic.line.color.rgb = LINE; pic.line.width = Pt(0.75)
    else:  # Zarit ZBI-4: schematic 4 x 5 answer grid, not the wording
        box(vt, px, py, pw_, ph_, WHITE, line=LINE, radius=0.03)
        text(vt, px, py + Inches(0.04), pw_, Inches(0.25), "ZBI-4", size=11, bold=True, color=INK, align=PP_ALIGN.CENTER)
        gx0, gy0, cwid, chgt = px + Inches(0.55), py + Inches(0.5), Inches(0.24), Inches(0.21)
        for c_ in range(5):
            text(vt, gx0 + c_ * cwid, py + Inches(0.27), cwid, Inches(0.22), str(c_), size=9, color=SUB, align=PP_ALIGN.CENTER)
        for r_ in range(4):
            text(vt, px + Inches(0.08), gy0 + r_ * chgt, Inches(0.45), chgt, "Q" + str(r_ + 1), size=9, color=SUB, anchor=MSO_ANCHOR.MIDDLE)
            for c_ in range(5):
                cell = vt.shapes.add_shape(MSO_SHAPE.RECTANGLE, gx0 + c_ * cwid, gy0 + r_ * chgt, cwid, chgt)
                cell.fill.solid(); cell.fill.fore_color.rgb = WHITE; cell.line.color.rgb = SUB; cell.line.width = Pt(0.5); cell.shadow.inherit = False
    a = vt.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, px + pw_ + Inches(0.06), py + ph_ / 2 - Inches(0.15), Inches(0.3), Inches(0.3))
    a.fill.solid(); a.fill.fore_color.rgb = TEAL; a.line.fill.background(); a.shadow.inherit = False
    sp = vt.shapes.add_picture(phone, x + cw_ - Inches(0.15) - Inches(1.26), y0 + Inches(0.45), Inches(1.26), Inches(2.8))
    sp.line.color.rgb = LINE; sp.line.width = Pt(0.75)
    text(vt, x + Inches(0.15), y0 + Inches(3.32), cw_ - Inches(0.3), Inches(0.75),
         [[(name, {"size": 20, "bold": True, "color": INK})]] + ([[(sub, {"size": 11, "color": SUB})]] if sub else []), anchor=MSO_ANCHOR.MIDDLE)
    text(vt, x + Inches(0.15), y0 + Inches(4.07), cw_ - Inches(0.3), Inches(0.34), line, size=14, color=SUB)
    pill(vt, x + Inches(0.15), y0 + Inches(4.47), "Answered by: " + who, tone, w=Inches(2.1))
text(vt, Inches(0.55), Inches(6.08), Inches(12.2), Inches(0.36),
     [[("Same items and scoring as the validated originals: ESAS-r, NCCN Distress Thermometer, Zarit Caregiver Burden (ZBI-4).", {"size": 15, "color": SUB})]])
text(vt, Inches(0.55), Inches(6.45), Inches(12.2), Inches(0.36), [[
    ("Medical credit   ", {"size": 13, "bold": True, "color": TEAL}),
    ("Shirin Ayub, Bhaskar Medical College, Hyderabad: symptom log and caregiver needs", {"size": 13, "color": SUB})]])

# ---------- extra: how doctors and engineers built it together ----------
hw = new_slide("How Doctors and Engineers Built It Together")
loop = [("Doctors", "write the clinical rules", TEAL, TEAL_BG, 0, 0),
        ("Engineers", "build", BLUE, BLUE_BG, 1, 0),
        ("Doctors", "test on their phones and review", TEAL, TEAL_BG, 1, 1),
        ("Engineers", "fix", BLUE, BLUE_BG, 0, 1)]
bw, bh, gx, gy = Inches(2.45), Inches(1.75), Inches(0.65), Inches(0.6)
lx, ly = Inches(0.55), Inches(1.25)
for who, what, tone, bgc, cx, cy in loop:
    x = lx + cx * (bw + gx); y = ly + cy * (bh + gy)
    box(hw, x, y, bw, bh, bgc, line=tone)
    text(hw, x + Inches(0.1), y, bw - Inches(0.2), bh,
         [[(who, {"size": 26, "bold": True, "color": tone})], [(what, {"size": 17, "color": INK, "space": 2})]],
         align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
aw = Inches(0.45)
for shp, x, y, w_, h_ in [
        (MSO_SHAPE.RIGHT_ARROW, lx + bw + Inches(0.1), ly + bh / 2 - aw / 2, gx - Inches(0.2), aw),
        (MSO_SHAPE.DOWN_ARROW, lx + bw + gx + bw / 2 - aw / 2, ly + bh + Inches(0.08), aw, gy - Inches(0.16)),
        (MSO_SHAPE.LEFT_ARROW, lx + bw + Inches(0.1), ly + bh + gy + bh / 2 - aw / 2, gx - Inches(0.2), aw),
        (MSO_SHAPE.UP_ARROW, lx + bw / 2 - aw / 2, ly + bh + Inches(0.08), aw, gy - Inches(0.16))]:
    a = hw.shapes.add_shape(shp, x, y, w_, h_)
    a.fill.solid(); a.fill.fore_color.rgb = SUB; a.line.fill.background(); a.shadow.inherit = False
timeline = [("1–3 Aug", "Doctors register"), ("15 Aug", "Tech teams register"),
            ("20 Aug", "Dr Sujay’s goals-of-care idea"), ("22 Aug", "Matchmaking"),
            ("24 Aug", "First team meeting"), ("Sep", "Dr Sharada’s clinical reference, Shirin’s review"),
            ("20 Oct", "Mentor review"), ("29 Oct", "Clinical and tech specs"),
            ("3 Nov", "Team meeting"), ("21–22 Nov", "Device test and video reviews"),
            ("24 Nov", "Screen-by-screen review"), ("28 Nov", "Grand Finale")]
tx0, ty0, rh = Inches(6.55), Inches(1.15), Inches(0.37)
ln = hw.shapes.add_shape(MSO_SHAPE.RECTANGLE, tx0 + Inches(1.38), ty0 + rh / 2, Inches(0.03), rh * (len(timeline) - 1))
ln.fill.solid(); ln.fill.fore_color.rgb = RGBColor(0xC5, 0xD3, 0xCF); ln.line.fill.background(); ln.shadow.inherit = False
for k, (d_, e_) in enumerate(timeline):
    y = ty0 + k * rh; last = k == len(timeline) - 1
    tone = RUST if last else TEAL
    text(hw, tx0, y, Inches(1.25), rh, d_, size=15, bold=True, color=tone, align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)
    dot = hw.shapes.add_shape(MSO_SHAPE.OVAL, tx0 + Inches(1.31), y + rh / 2 - Inches(0.085), Inches(0.17), Inches(0.17))
    dot.fill.solid(); dot.fill.fore_color.rgb = tone; dot.line.fill.background(); dot.shadow.inherit = False
    text(hw, tx0 + Inches(1.6), y, Inches(4.7), rh, e_, size=15, bold=last, color=INK, anchor=MSO_ANCHOR.MIDDLE)
for k, (big, small) in enumerate([("25", "meetings and reviews"), ("650+", "code commits"), ("Live", "on Google Play")]):
    x = Inches(0.55) + k * Inches(4.15)
    box(hw, x, Inches(5.75), Inches(3.95), Inches(1.05), TEAL)
    text(hw, x + Inches(0.2), Inches(5.75), Inches(3.55), Inches(1.05),
         [[(big, {"size": 34, "bold": True, "color": WHITE})], [(small, {"size": 15, "color": WHITE})]],
         align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)

# ---------- 8 Competitors ----------
cp = new_slide("Others Do One Piece. Sahara Joins the Whole Journey")
cp.shapes.add_picture(D + r"\crop_Sahara_Competitor_Slide.png", Inches(0.45), Inches(1.05), Inches(10.0), Inches(4.03))
text(cp, Inches(10.6), Inches(1.05), Inches(2.25), Inches(4.1), [
    [("Also checked (55 apps)", {"size": 14, "bold": True, "color": TEAL})],
    [("London UCP, Oregon POLST, Taiwan NHI: plans reach the ER, but no home layer for families.", {"size": 11, "space": 5})],
    [("Kerala Care grid: home visits and care plans.", {"size": 11, "space": 5})],
    [("PalaSh, AIIMS Bathinda: clinician records; no patient plan or ER card. A likely partner.", {"size": 11, "space": 5})],
    [("BandhuCare, CMC Vellore: 8-language companion.", {"size": 11, "space": 5})],
    [("Palli Sahayak (KCDH-A): 15+ languages.", {"size": 11, "space": 5})],
])
b = box(cp, Inches(0.55), Inches(5.3), Inches(12.25), Inches(1.5), TEAL_BG)
text(cp, Inches(0.75), Inches(5.3), Inches(11.9), Inches(1.5), [
    [("Palli Sahayak answers \u201CWhat do I do now?\u201D  Sahara answers \u201CWhat did this patient decide, and who signed it?\u201D", {"size": 17, "bold": True, "color": TEAL})],
    [("They work together: an escalation in Palli Sahayak can pull up the signed Sahara plan when the patient reaches the ER.", {"size": 14, "color": SUB, "space": 4})],
], anchor=MSO_ANCHOR.MIDDLE)

# ---------- 9 Technology (old slide 7) ----------
te = S[6]
clean(te, "Technology and AI")
for sh in list(te.shapes):  # drop the whole "Built with" grid (y >= 3.4in) except the footer/strips
    if sh.top >= Inches(3.4) and sh.top < Inches(7.0):
        sh._element.getparent().remove(sh._element)
half_y = Inches(3.4)
for k, (img, name, big, line1, tone) in enumerate([
        (OPENAI, "OpenAI", "Works offline", "Whisper turns speech into text on the phone itself, even with no internet.", BLUE),
        (SARVAM, "Sarvam AI", "23 languages", "Speech to text in all 22 scheduled Indian languages + English (Sarvam Saaras v4 on our server).", TEAL),
]):
    x = Inches(0.55) + k * Inches(6.2)
    box(te, x, half_y, Inches(6.05), Inches(3.4), WHITE, line=RGBColor(0xD5, 0xDE, 0xDB))
    try:
        te.shapes.add_picture(img, x + Inches(0.35), half_y + Inches(0.3), height=Inches(0.95))
    except Exception:
        pass
    text(te, x + Inches(1.75), half_y + Inches(0.3), Inches(4.1), Inches(0.95), name, size=34, bold=True, anchor=MSO_ANCHOR.MIDDLE)
    text(te, x + Inches(0.35), half_y + Inches(1.35), Inches(5.5), Inches(2.0),
         [[(big, {"size": 48, "bold": True, "color": tone})], [(line1, {"size": 16, "color": SUB, "space": 2})]])
text(te, Inches(0.55), Inches(6.88), Inches(12.2), Inches(0.3),
     "The app itself is in 11 languages  \u00B7  Android (Kotlin + Compose), Next.js, Supabase  \u00B7  FHIR-ready for hospital software", size=11, color=SUB)

# ---------- 10 Safety (old slide 10) ----------
sf = S[9]
clean(sf, "Backup: Safe by Design")
set_pars(shape(sf, 163), ["Privacy"])
def bullets(sid, items):
    sh = shape(sf, sid); pars = [q for q in sh.text_frame.paragraphs if q.text.strip()]
    for q, t_ in zip(pars, items): set_par(q, "\u2713  " + t_)
bullets(164, ["Recording only with agreement", "Complies with India’s DPDP Act", "Analytics only if the patient opts in"])
bullets(169, ["The AI transcribes and summarises; it never recommends", "Every line links to the sentence it came from", "A signed clinical record, not a living will"])
bullets(174, ["The doctor stays in full control", "Read aloud for those who can\u2019t read; a witness signs", "Any change makes a new version to re-sign"])
bullets(179, ["11 languages; it reads everything aloud", "Signature or thumb impression", "Each family sees only its own records"])

# ---------- 11 Impact and who pays (old slide 11) ----------
c = S[10]
clean(c, "Our Key Performance Indicators (KPIs)")
drop(c, *range(168, 184))
tshape = shape(c, 184); tshape.left = Inches(0.55); tshape.top = Inches(1.2); tshape.width = Inches(12.2); tshape.height = Inches(4.0)
pay = shape(c, 185); pay.left = Inches(0.55); pay.width = Inches(12.2); pay.top = Inches(5.45)
pt = shape(c, 186); pt.left = Inches(0.75); pt.width = Inches(11.8); pt.top = Inches(5.45)
nt = shape(c, 187); nt.left = Inches(0.55); nt.width = Inches(12.2); nt.top = Inches(6.35); nt.height = Inches(0.6)
for sid, tx in [] and {
    171: ["Hospitals and clinics pay", "Per enrolled patient, to plug Sahara into their EHR. Public roll-out through NCG or state programmes."],
    175: ["Free for patients", "Patients and families never pay. No in-app purchases."],
    179: ["Step by step, like Oregon", "A state yearly subscription, private hospitals separately. Taiwan already reimburses advance care planning."],
    183: ["How it scales", "One clinic, then the National Cancer Grid’s 370+ centres, on ABDM’s 90 crore health IDs. London’s care plan grew the same way."],
}.items():
    set_pars(shape(c, sid), tx)
rows = [["What we measure", "Evidence it can move", "Target"],
        ["Eligible patients with a signed plan", "Chennai form: 0% to 92%", "Measured in the pilot"],
        ["Seconds from ER arrival to the plan", "63% not found in 15 min", "Under 60 s"],
        ["Doctor minutes to a signed plan", "Measured, not promised", "By hand vs Sahara"],
        ["Care at the end matched the plan", "86% vs 30% with planning", "Chart review"],
        ["Caregiver distress at 30 days", "Less stress with planning", "Lower or no worse"],
        ["Family out-of-pocket, last 30 days", "₹43,897 for patients who died", "Direction only"]]
tbl = shape(c, 184).table
for k_, w_ in enumerate((5.0, 4.2, 3.0)): tbl.columns[k_].width = Inches(w_)
for r_, vals in enumerate(rows):
    for col, v_ in enumerate(vals):
        cell = tbl.cell(r_, col); pars = cell.text_frame.paragraphs
        set_par(pars[0], v_)
        for q in pars[1:]: q._p.getparent().remove(q._p)
        for r__ in pars[0].runs: r__.font.size = Pt(16 if r_ else 17)
set_pars(shape(c, 186), ["A week in an ICU costs a hospital \u20B942,000\u201375,000 to provide, and families often pay most of it. We make sure what the patient chose is known before that week starts."])
set_pars(shape(c, 187), ["Thangasamy 2025; Russell 2021; Detering, BMJ 2010; Das & Ladusingh 2018 (NSS). Effect evidence is mostly from abroad; our pilot measures it in India."])

# ---------- 12 Thank you (old 13) ----------
ty = S[12]
for sh in list(ty.shapes):
    if sh.has_text_frame and sh.text_frame.text.strip() == "devit.":
        sh._element.getparent().remove(sh._element)
    elif sh.has_text_frame and "Demo video" in sh.text_frame.text:
        set_pars(sh, ["Try it: sahara.wedevit.in   |   Android: Sahara on Google Play   |   workwithdevit@gmail.com"])
    elif sh.has_text_frame and "HEALTH-A-THON" in sh.text_frame.text:
        set_pars(sh, ["SAHARA  \u2022  ZEROS AND ONES  \u2022  GRAND FINALE, IIT BOMBAY"])
for sh in list(ty.shapes):
    if sh.shape_type == 13 and sh.top < Inches(0.8) and sh.left < Inches(3.5):  # Health-a-thon wordmark top left
        sh._element.getparent().remove(sh._element)
ty.shapes.add_picture(LOGO, Inches(0.55), Inches(0.2), Inches(0.7), Inches(0.7))
text(ty, Inches(1.3), Inches(0.25), Inches(2.2), Inches(0.6), "Sahara", size=26, bold=True, color=TEAL, anchor=MSO_ANCHOR.MIDDLE)

# backup slides (old 5, 6): clean too
for s_ in (S[4], S[5]):
    clean(s_)
    for sh in s_.shapes:
        if sh.has_text_frame and sh.text_frame.text.startswith("Solution Overview"):
            set_par(sh.text_frame.paragraphs[0], "Backup: " + sh.text_frame.paragraphs[0].text); break

# ---------- extra: what goes wrong today ----------
pl = new_slide("Backup: What Goes Wrong Today")
probs = [("Wishes get lost", "The goals-of-care talk happens, but the signed plan never reaches the emergency room."),
         ("Symptoms go unseen", "Pain, breathlessness and distress at home are not reported between visits."),
         ("Pain relief is hard to find", "Families don’t know which centre near them stocks oral morphine."),
         ("Caregivers carry it alone", "Families are out of the loop, and their own distress is never asked about."),
         ("Care in a language they don’t read", "Notes and plans are in English; many families aren’t.")]
for k, (h_, d_) in enumerate(probs):
    y = Inches(1.15) + k * Inches(1.12)
    o = pl.shapes.add_shape(MSO_SHAPE.OVAL, Inches(0.6), y + Inches(0.12), Inches(0.6), Inches(0.6))
    o.fill.solid(); o.fill.fore_color.rgb = RUST if k == 0 else TEAL; o.line.fill.background()
    q = o.text_frame.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
    r = q.add_run(); r.text = str(k + 1); r.font.bold = True; r.font.size = Pt(18); r.font.color.rgb = WHITE
    text(pl, Inches(1.45), y, Inches(11.2), Inches(1.0), [[(h_, {"size": 24, "bold": True})], [(d_, {"size": 17, "color": SUB})]])

bk = new_slide("Backup: More Evidence")
more = [("<4%", "of the 7–10 million Indians who need palliative care get it", "Sharma, Thakkar et al., ecancer 2025"),
        ("79%", "of cancer hospitalisations push families into catastrophic spending", "Kastor & Mohanty, PLoS One 2018"),
        ("63%", "of end-stage patients: code status not found within 15 minutes of reaching the ER", "Russell, West J Emerg Med 2021"),
        ("0→92%", "goals-of-care documentation with one standard form (Chennai)", "Thangasamy, Palliat Med Rep 2025"),
        ("~4%", "India meets only about 4% of its need for pain-relief medicines such as morphine", "Lancet Commission on Palliative Care and Pain Relief (Knaul et al., 2017/18)")]
for k, (big, d_, src) in enumerate(more):
    x = Inches(0.55) + (k % 3) * Inches(4.15); y = Inches(1.2) + (k // 3) * Inches(2.85)
    box(bk, x, y, Inches(3.95), Inches(2.65), WHITE, line=RGBColor(0xD5, 0xDE, 0xDB))
    text(bk, x + Inches(0.2), y + Inches(0.15), Inches(3.6), Inches(2.4),
         [[(big, {"size": 40, "bold": True, "color": TEAL})], [(d_, {"size": 15, "space": 4})], [(src, {"size": 10, "color": SUB, "space": 6})]])

dp = new_slide("Deployment, Cost and Sustainability")
qs = [("Deploy", ["Hospitals plug Sahara into their EHR.", "Website and Android app."]),
      ("Who pays?", ["Free for patients and caregivers.", "Hospitals and palliative clinics pay."]),
      ("Cost to run Sahara", ["Servers and website \u2248 \u20B94,200 a month", "Sarvam AI \u2248 \u20B923 per 30-minute talk", "Set-up and staff training \u2248 \u20B925,000 per clinic (once)", "Support \u2248 \u20B910,000 a month"]),
      ("Sustain", ["Hospital and clinic fees.", "Grants and CSR."])]
for k, (h_, items) in enumerate(qs):
    wide = k == 2
    x = Inches([0.55, 3.45, 6.35, 10.5][k])
    w_ = Inches([2.75, 2.75, 4.0, 2.3][k])
    box(dp, x, Inches(1.2), w_, Inches(4.4), BLUE_BG if wide else TEAL_BG)
    text(dp, x + Inches(0.2), Inches(1.4), w_ - Inches(0.4), Inches(4.1),
         [[(h_, {"size": 22, "bold": True, "color": BLUE if wide else TEAL})]] + [[("\u2022  " + it, {"size": 16, "space": 12})] for it in items])
box(dp, Inches(0.55), Inches(5.85), Inches(12.25), Inches(1.0), TEAL)
text(dp, Inches(0.75), Inches(5.85), Inches(11.9), Inches(1.0),
     [[("No family should go into debt for treatment the patient said they didn’t want.", {"size": 20, "bold": True, "color": WHITE})]],
     anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)


# ====================== v2: more visual, final video decisions ======================
from PIL import Image as _Im
V = D + r"\v2"
LN = RGBColor(0xD5, 0xDE, 0xDB)
GREY = RGBColor(0xB8, 0xC4, 0xC1)
GREY_BG = RGBColor(0xEE, 0xF1, 0xF0)


def phone(s, path, cx, top, h):
    w0, h0 = _Im.open(path).size
    w = int(h * w0 / h0)
    pic = s.shapes.add_picture(path, int(cx - w / 2), top, w, h)
    pic.line.color.rgb = LN; pic.line.width = Pt(0.75)
    return pic


def arrow(s, kind, x, y, w, h, col=SUB):
    a = s.shapes.add_shape(kind, x, y, w, h)
    a.fill.solid(); a.fill.fore_color.rgb = col; a.line.fill.background(); a.shadow.inherit = False
    return a


def disc(s, x, y, d, col, label="", size=18, fcol=WHITE):
    o = s.shapes.add_shape(MSO_SHAPE.OVAL, x, y, d, d)
    o.fill.solid(); o.fill.fore_color.rgb = col; o.line.fill.background(); o.shadow.inherit = False
    if label:
        tf = o.text_frame; tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
        q = tf.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
        r = q.add_run(); r.text = label; r.font.bold = True; r.font.size = Pt(size); r.font.color.rgb = fcol; r.font.name = FONT
    return o


def rect(s, x, y, w, h, col):
    b = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, h)
    b.fill.solid(); b.fill.fore_color.rgb = col; b.line.fill.background(); b.shadow.inherit = False
    return b


# ---------- Evidence, drawn ----------
ev2 = new_slide("Supporting Evidence")
text(ev2, Inches(0.55), Inches(1.05), Inches(6.0), Inches(1.6), [[("98%", {"size": 96, "bold": True, "color": RUST})]])
gs, gg = Inches(0.27), Inches(0.06)
for i in range(100):
    r_, c_ = divmod(i, 20)
    col = RUST if i < 98 else GREY
    rect(ev2, Inches(0.6) + c_ * (gs + gg), Inches(2.75) + r_ * (gs + gg), gs, gs, col)
text(ev2, Inches(0.55), Inches(4.5), Inches(6.4), Inches(1.6), [
    [("of India’s metastatic (advanced) cancer patients had unmet palliative-care needs.", {"size": 20, "bold": True})],
    [("Each square is 1 in 100 patients.", {"size": 13, "color": SUB, "space": 4})],
    [("Patil et al., BMJ Supportive & Palliative Care, 2024 (National Cancer Registry Programme data)", {"size": 11, "color": SUB, "space": 6})]])
bars = [("ONLY", "30–37%", 0.30, 0.37, "of patients get timely palliative care referrals (two centres).", "Venketeswaran, BMJ Support Palliat Care 2026", RUST),
        ("", "41%", 0.41, 0.41, "of cancer patients dying on active treatment had end-of-life care documented (one hospital).", "Das, Cancer Med 2026", RUST),
        ("HOWEVER", "88%", 0.88, 0.88, "of urban adults want to choose their own end-of-life care.", "Living will survey of 2,400+ urban adults, HCAH with ELICIT, 2019 (reported by IndiaSpend)", TEAL)]
bx, bw = Inches(7.2), Inches(5.6)
for k, (lab, big, lo, hi, cap, src, col) in enumerate(bars):
    y = Inches(1.15) + k * Inches(1.9)
    if lab:
        text(ev2, bx, y - Inches(0.05), Inches(2), Inches(0.3), lab, size=13, bold=True, color=col)
    text(ev2, bx, y + Inches(0.2), Inches(2.6), Inches(0.7), big, size=36, bold=True, color=col)
    by = y + Inches(0.32)
    track = box(ev2, bx + Inches(2.3), by, bw - Inches(2.3), Inches(0.42), GREY_BG, radius=0.5)
    tw = bw - Inches(2.3)
    box(ev2, bx + Inches(2.3), by, int(tw * lo), Inches(0.42), col, radius=0.5)
    if hi > lo:
        rect(ev2, bx + Inches(2.3) + int(tw * lo) - Inches(0.05), by, int(tw * (hi - lo)) + Inches(0.05), Inches(0.42), RGBColor(0xE8, 0xA8, 0x95))
    text(ev2, bx, y + Inches(0.9), bw, Inches(0.85), [[(cap, {"size": 15})], [(src, {"size": 10, "color": SUB, "space": 2})]])

# ---------- Team, Zeros and Ones ----------
tm2 = new_slide("Zeros and Ones")
groups = [("CLINICIANS", TEAL, TEAL_BG, [("sharada", "Dr Sharada Vinod Kutty", "Pulmonologist", "AIIMS Mangalagiri"),
                                        ("sujay", "Dr Sujay Halkur Shankar", "Pulmonologist", "Tata IISc Medical School"),
                                        ("shirin", "Shirin Ayub", "Medical student", "Bhaskar Medical College, Hyderabad")]),
          ("TECHNOLOGISTS", BLUE, BLUE_BG, [("basith", "Shaik Abdul Basith", "Technical lead · Founder", "WeDevIT Private Limited"),
                                            ("awaiz", "Shaik Muhammad Awaiz", "Android engineer", "WeDevIT Private Limited")])]
gx = Inches(0.55)
for gname, tone, bgc, ppl in groups:
    gw = Inches(2.15) * len(ppl) + Inches(0.12) * (len(ppl) + 1)
    box(tm2, gx, Inches(1.25), gw, Inches(4.55), bgc, line=tone)
    pill(tm2, gx + Inches(0.15), Inches(1.05), gname, tone, w=Inches(1.9))
    for j, (img, name, role, inst) in enumerate(ppl):
        x = gx + Inches(0.12) + j * Inches(2.27)
        tm2.shapes.add_picture(V + rf"\team-{img}.jpg", x + Inches(0.3), Inches(1.55), Inches(1.55), Inches(1.55))
        text(tm2, x, Inches(3.2), Inches(2.15), Inches(2.5), [
            [(name, {"size": 17, "bold": True})], [(role, {"size": 13, "color": SUB, "space": 2})],
            [(inst, {"size": 14, "bold": True, "color": tone, "space": 6})]], align=PP_ALIGN.CENTER)
    gx = gx + gw + Inches(0.3)
text(tm2, Inches(0.55), Inches(6.0), Inches(8.6), Inches(0.6),
     [[("Three clinicians and two technologists came together to build Sahara.", {"size": 20, "bold": True, "color": TEAL})]], anchor=MSO_ANCHOR.MIDDLE)
text(tm2, Inches(9.0), Inches(6.05), Inches(1.3), Inches(0.5), "Built by", size=15, color=SUB, align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)
tm2.shapes.add_picture(DEVIT, Inches(10.4), Inches(6.02), Inches(1.95), Inches(0.5))

# ---------- Goals of care: the doctor's flow ----------
gc = new_slide("Goals of Care: the Doctor’s View")
dia = gc.shapes.add_shape(MSO_SHAPE.DIAMOND, Inches(0.45), Inches(1.1), Inches(3.9), Inches(2.6))
dia.fill.solid(); dia.fill.fore_color.rgb = BLUE_BG; dia.line.color.rgb = BLUE; dia.line.width = Pt(1.5); dia.shadow.inherit = False
text(gc, Inches(1.2), Inches(1.6), Inches(2.4), Inches(1.6),
     [[("Would you be surprised if this patient died in the next 12 months?", {"size": 14, "bold": True, "color": BLUE})]],
     align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
# Yes branch
arrow(gc, MSO_SHAPE.DOWN_ARROW, Inches(2.2), Inches(3.75), Inches(0.4), Inches(0.4), GREY)
text(gc, Inches(2.65), Inches(3.75), Inches(0.8), Inches(0.4), "Yes", size=14, bold=True, color=SUB, anchor=MSO_ANCHOR.MIDDLE)
box(gc, Inches(0.55), Inches(4.2), Inches(3.7), Inches(0.55), GREY_BG, line=GREY)
text(gc, Inches(0.55), Inches(4.2), Inches(3.7), Inches(0.55), "Ask again at the next visit", size=14, color=SUB, align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
# No branch
arrow(gc, MSO_SHAPE.RIGHT_ARROW, Inches(4.4), Inches(2.2), Inches(0.6), Inches(0.42), TEAL)
text(gc, Inches(4.4), Inches(1.8), Inches(0.6), Inches(0.4), "No", size=14, bold=True, color=TEAL, align=PP_ALIGN.CENTER)
box(gc, Inches(5.1), Inches(1.85), Inches(7.7), Inches(0.75), TEAL)
text(gc, Inches(5.25), Inches(1.85), Inches(7.4), Inches(0.75),
     [[("Sahara prompts the doctor to start the goals-of-care talk", {"size": 19, "bold": True, "color": WHITE})]], anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
arrow(gc, MSO_SHAPE.DOWN_ARROW, Inches(8.75), Inches(2.65), Inches(0.4), Inches(0.3), TEAL)
steps2 = [(V + r"\f01-goc-record.png", "Guided or free-flow talk, recorded"),
          (V + r"\f02-goc-review.png", "13 topics, from Indian guidelines"),
          (V + r"\f05-goc-document.png", "The document writes itself"),
          (None, "Doctor and caregiver sign")]
cw2 = Inches(1.82)
for k, (img, lab) in enumerate(steps2):
    x = Inches(5.1) + k * (cw2 + Inches(0.14))
    box(gc, x, Inches(3.0), cw2, Inches(3.3), WHITE, line=LN)
    if img:
        phone(gc, img, x + cw2 / 2, Inches(3.1), Inches(2.3))
    else:
        disc(gc, x + cw2 / 2 - Inches(0.6), Inches(3.55), Inches(1.2), TEAL, "✓", size=48)
        text(gc, x, Inches(4.85), cw2, Inches(0.5), "Locked; a new version to re-sign", size=11, color=SUB, align=PP_ALIGN.CENTER)
    disc(gc, x + Inches(0.06), Inches(3.06), Inches(0.36), BLUE, str(k + 1), size=12)
    text(gc, x + Inches(0.05), Inches(5.45), cw2 - Inches(0.1), Inches(0.8), lab, size=13, bold=True, align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
box(gc, Inches(0.55), Inches(4.95), Inches(3.7), Inches(1.35), TEAL_BG)
text(gc, Inches(0.7), Inches(5.0), Inches(3.5), Inches(1.3), [
    [("Safe by design", {"size": 15, "bold": True, "color": TEAL})],
    [("•  People stay in control", {"size": 12, "space": 2})],
    [("•  India’s DPDP Act", {"size": 12})],
    [("•  Analytics only if the patient opts in", {"size": 12})]])
text(gc, Inches(0.55), Inches(6.36), Inches(12.25), Inches(0.55), [[
    ("Medical credits   ", {"size": 12, "bold": True, "color": TEAL}),
    ("Dr Sujay Halkur Shankar: framework and 13 topics   ·   Dr Sharada Vinod Kutty: clinical specification and emergency care plan", {"size": 12, "color": SUB})],
    [("Legal advice taken on wording and format.", {"size": 11, "color": SUB})]])

# ---------- At home 1: validated tools, paper -> Sahara ----------
h1 = new_slide("At Home: Sahara Stays With You, Every Day")
tools2 = [("ESAS-r", "Symptom log", "9 symptoms, each 0–10", D + r"\tool1.png", V + r"\ss-02-log-symptom.png", "Patient", TEAL),
          ("NCCN Distress Thermometer", "Distress check", "Distress 0–10 + problem list", D + r"\tool2.png", V + r"\f14-patient-today.png", "Patient", TEAL),
          ("Zarit Caregiver Burden (ZBI-4)", "Caregiver wellbeing check", "4 questions, Never to Nearly always (0–4)", None, V + r"\f14-carer-burden-ui.png", "Caregiver", RUST)]
cw_, y0 = Inches(3.95), Inches(1.1)
for k, (name, scr, line, paper, ph_img, who, tone) in enumerate(tools2):
    x = Inches(0.55) + k * (cw_ + Inches(0.2))
    box(h1, x, y0, cw_, Inches(4.95), WHITE, line=LN)
    text(h1, x + Inches(0.15), y0 + Inches(0.08), cw_ - Inches(0.3), Inches(0.32),
         [[("Paper form  →  Sahara screen", {"size": 12, "bold": True, "color": TEAL})]])
    px, pw_, ph_ = x + Inches(0.15), Inches(1.9), Inches(1.59)
    py = y0 + Inches(0.45) + Inches(1.4) - ph_ / 2
    if paper:
        pic = h1.shapes.add_picture(paper, px, py, pw_, ph_); pic.line.color.rgb = LN; pic.line.width = Pt(0.75)
    else:
        box(h1, px, py, pw_, ph_, WHITE, line=LN, radius=0.03)
        text(h1, px, py + Inches(0.04), pw_, Inches(0.25), "ZBI-4", size=11, bold=True, align=PP_ALIGN.CENTER)
        gx0, gy0, cwid, chgt = px + Inches(0.55), py + Inches(0.5), Inches(0.24), Inches(0.21)
        for c_ in range(5):
            text(h1, gx0 + c_ * cwid, py + Inches(0.27), cwid, Inches(0.22), str(c_), size=9, color=SUB, align=PP_ALIGN.CENTER)
        for r_ in range(4):
            text(h1, px + Inches(0.08), gy0 + r_ * chgt, Inches(0.45), chgt, "Q" + str(r_ + 1), size=9, color=SUB, anchor=MSO_ANCHOR.MIDDLE)
            for c_ in range(5):
                cell = h1.shapes.add_shape(MSO_SHAPE.RECTANGLE, gx0 + c_ * cwid, gy0 + r_ * chgt, cwid, chgt)
                cell.fill.solid(); cell.fill.fore_color.rgb = WHITE; cell.line.color.rgb = SUB; cell.line.width = Pt(0.5); cell.shadow.inherit = False
    arrow(h1, MSO_SHAPE.RIGHT_ARROW, px + pw_ + Inches(0.06), py + ph_ / 2 - Inches(0.15), Inches(0.3), Inches(0.3), TEAL)
    phone(h1, ph_img, x + cw_ - Inches(0.15) - Inches(0.63), y0 + Inches(0.45), Inches(2.8))
    text(h1, x + Inches(0.15), y0 + Inches(3.32), cw_ - Inches(0.3), Inches(0.8),
         [[(name, {"size": 18, "bold": True})], [("→ " + scr, {"size": 14, "bold": True, "color": tone})]], anchor=MSO_ANCHOR.MIDDLE)
    text(h1, x + Inches(0.15), y0 + Inches(4.12), cw_ - Inches(0.3), Inches(0.34), line, size=13, color=SUB)
    pill(h1, x + Inches(0.15), y0 + Inches(4.5), "Answered by: " + who, tone, w=Inches(2.1))
text(h1, Inches(0.55), Inches(6.15), Inches(12.2), Inches(0.36),
     [[("Same items and scoring as the validated originals.", {"size": 14, "bold": True, "color": TEAL}),
       ("   Medical credit: Shirin Ayub, Bhaskar Medical College, Hyderabad (symptom log and caregiver needs)", {"size": 12, "color": SUB})]])

# ---------- At home 2: help nearby ----------
h2 = new_slide("At Home: Help When It Is Needed")
helps = [(V + r"\ss-01-home.png", "SOS", "One tap to emergency help", RUST),
         (V + r"\ss-04-find-care.png", "1,600+", "palliative care centres, and where oral morphine is listed", TEAL),
         (V + r"\f15-community.png", "Community", "Caregivers support each other", BLUE)]
for k, (img, big, small, tone) in enumerate(helps):
    x = Inches(0.55) + k * Inches(4.15)
    box(h2, x, Inches(1.1), Inches(3.95), Inches(5.7), WHITE, line=LN)
    phone(h2, img, x + Inches(1.975), Inches(1.25), Inches(3.75))
    text(h2, x + Inches(0.15), Inches(5.1), Inches(3.65), Inches(1.6),
         [[(big, {"size": 32, "bold": True, "color": tone})], [(small, {"size": 15, "color": INK})]], align=PP_ALIGN.CENTER)

# ---------- Emergency, once ----------
em = new_slide("In an Emergency: QR → Scan → Her Wishes")
pic = em.shapes.add_picture(V + r"\card-crop.png", Inches(0.55), Inches(1.6), Inches(5.6), int(Inches(5.6) * 242 / 748))
pic.line.color.rgb = LN
text(em, Inches(0.55), Inches(3.55), Inches(5.6), Inches(1.0),
     [[("1  The wallet card", {"size": 20, "bold": True, "color": RUST})], [("Printed from Sahara; kept with the patient", {"size": 14, "color": SUB})]])
arrow(em, MSO_SHAPE.RIGHT_ARROW, Inches(6.3), Inches(2.3), Inches(0.6), Inches(0.45), RUST)
disc(em, Inches(7.05), Inches(1.75), Inches(1.6), RUST, "QR", size=36)
text(em, Inches(6.8), Inches(3.55), Inches(2.1), Inches(1.3),
     [[("2  Scan", {"size": 20, "bold": True, "color": RUST})], [("Any phone camera; no login needed", {"size": 14, "color": SUB})]], align=PP_ALIGN.CENTER)
arrow(em, MSO_SHAPE.RIGHT_ARROW, Inches(8.85), Inches(2.3), Inches(0.6), Inches(0.45), RUST)
phone(em, V + r"\f07-emergency-qr.png", Inches(11.1), Inches(1.1), Inches(4.3))
text(em, Inches(9.5), Inches(5.45), Inches(3.3), Inches(1.0),
     [[("3  Emergency information", {"size": 18, "bold": True, "color": RUST})], [("Signed wishes in seconds", {"size": 14, "color": SUB})]], align=PP_ALIGN.CENTER)
box(em, Inches(0.55), Inches(5.0), Inches(8.6), Inches(1.3), TEAL_BG)
text(em, Inches(0.75), Inches(5.0), Inches(8.2), Inches(1.3),
     [[("The emergency doctor sees what the patient decided, and who signed it.", {"size": 19, "bold": True, "color": TEAL})],
      [("If in doubt, resuscitate. What the patient says now overrides the card.", {"size": 13, "color": SUB, "space": 4})]], anchor=MSO_ANCHOR.MIDDLE)

# ---------- Technology: three cards ----------
for sh in list(te.shapes):
    if sh.top >= Inches(3.3) and sh.top < Inches(7.0):
        sh._element.getparent().remove(sh._element)
cards3 = [(OPENAI, "OpenAI Whisper", "Works offline", "Speech to text on the phone itself, even with no internet.", BLUE),
          (SARVAM, "Sarvam AI", "23 languages", "All 22 scheduled Indian languages + English (Saaras v4).", TEAL),
          (V + r"\f11-ask-documents.png", "Ask your documents", "With the source line", "Questions on uploaded reports and prescriptions are answered with the line they came from (RAG).", RUST)]
for k, (img, name, big, line1, tone) in enumerate(cards3):
    x = Inches(0.55) + k * Inches(4.15)
    box(te, x, Inches(3.35), Inches(3.95), Inches(3.4), WHITE, line=LN)
    if k < 2:
        te.shapes.add_picture(img, x + Inches(0.25), Inches(3.5), height=Inches(0.75))
    else:
        phone(te, img, x + Inches(0.6), Inches(3.45), Inches(1.0))
    text(te, x + Inches(1.4), Inches(3.5), Inches(2.5), Inches(0.75), name, size=20, bold=True, anchor=MSO_ANCHOR.MIDDLE)
    text(te, x + Inches(0.25), Inches(4.5), Inches(3.55), Inches(2.2),
         [[(big, {"size": 32 if k < 2 else 24, "bold": True, "color": tone})], [(line1, {"size": 14, "color": SUB, "space": 4})]])
text(te, Inches(0.55), Inches(6.82), Inches(12.2), Inches(0.3),
     "The app itself is in 11 languages  ·  Android (Kotlin + Compose), Next.js, Supabase  ·  FHIR-ready for hospital software", size=11, color=SUB)

# ---------- We filled the gaps (native table) ----------
fg = new_slide("We Filled the Gaps")
cols_ = ["PalaSh\nAIIMS Bathinda", "BandhuCare\nCMC Vellore", "Palli Sahayak\nKCDH-A", "Kerala Care\ngrid", "London, Oregon\nand Taiwan plans", "Sahara"]
rows_ = [("Symptom and distress log at home", [0, 0, 0, 0, 0, 1]),
         ("Works in Indian languages", [0, 1, 1, 0, 0, 1]),
         ("Caregivers share the care", [0, 0, 0, 0, 0, 1]),
         ("Recorded talk, AI draft, doctor signs", [0, 0, 0, 0, 0, 1]),
         ("Wishes visible in an emergency", [0, 0, 0, 0, 1, 1]),
         ("Palliative centres and oral morphine near you", [0, 0, 0, 0, 0, 1]),
         ("Built for India", [1, 1, 1, 1, 0, 1])]
lx0, lw, cwx, ty, rh = Inches(0.55), Inches(4.1), Inches(1.36), Inches(1.15), Inches(0.56)
hdr_h = Inches(0.75)
for j, cname in enumerate(cols_):
    x = lx0 + lw + j * cwx
    sah = j == len(cols_) - 1
    if sah:
        rect(fg, x, ty, cwx, hdr_h + rh * len(rows_), TEAL)
    text(fg, x, ty, cwx, hdr_h, [[(ln_, {"size": 13 if i_ == 0 else 10, "bold": i_ == 0, "color": WHITE if sah else (INK if i_ == 0 else SUB)})] for i_, ln_ in enumerate(cname.split("\n"))],
         align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
for i_, (feat, marks) in enumerate(rows_):
    y = ty + hdr_h + i_ * rh
    if i_ % 2 == 0:
        rect(fg, lx0, y, lw + cwx * (len(cols_) - 1), rh, GREY_BG)
    text(fg, lx0 + Inches(0.1), y, lw - Inches(0.1), rh, feat, size=15, anchor=MSO_ANCHOR.MIDDLE)
    for j, m in enumerate(marks):
        cx = lx0 + lw + j * cwx + cwx / 2
        d = Inches(0.24)
        o = fg.shapes.add_shape(MSO_SHAPE.OVAL, int(cx - d / 2), int(y + rh / 2 - d / 2), d, d)
        o.shadow.inherit = False
        if j == len(cols_) - 1:
            o.fill.solid(); o.fill.fore_color.rgb = WHITE; o.line.fill.background()
        elif m:
            o.fill.solid(); o.fill.fore_color.rgb = TEAL; o.line.fill.background()
        else:
            o.fill.background(); o.line.color.rgb = GREY; o.line.width = Pt(1)
text(fg, lx0, Inches(5.95), Inches(5), Inches(0.3), [[("● Does it    ○ Not described", {"size": 12, "color": SUB})]])
text(fg, Inches(5.5), Inches(5.95), Inches(7.3), Inches(0.3), "Sources: product pages and publications; team comparison sheet (checked 20 Nov 2026).", size=10, color=SUB, align=PP_ALIGN.RIGHT)
box(fg, Inches(0.55), Inches(6.3), Inches(12.25), Inches(0.6), TEAL_BG)
text(fg, Inches(0.75), Inches(6.3), Inches(11.9), Inches(0.6),
     [[("Palli Sahayak answers “What do I do now?”  Sahara answers “What did this patient decide, and who signed it?”", {"size": 15, "bold": True, "color": TEAL})]],
     anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

# ---------- Live on Google Play ----------
gp = new_slide("Live on Google Play")
pic = gp.shapes.add_picture(V + r"\play-listing.png", Inches(0.55), Inches(1.4), Inches(12.2), int(Inches(12.2) * 520 / 2150))
pic.line.color.rgb = LN
text(gp, Inches(0.55), Inches(4.6), Inches(12.2), Inches(1.0),
     [[("sahara.wedevit.in", {"size": 40, "bold": True, "color": TEAL})], [("Website first, then the Android app", {"size": 18, "color": SUB})]], align=PP_ALIGN.CENTER)

# ---------- KPIs, visual ----------
kp = new_slide("Our Key Performance Indicators (KPIs)")
kpis = [("1", "Signed plans", "Eligible patients with a signed goals-of-care plan", "Chennai: one standard form took it from 0% to 92%"),
        ("2", "Time to the plan", "From arrival in the emergency room to the plan on screen", "Measured in the pilot"),
        ("3", "Care matched the plan", "Care at the end matched what the patient chose", "86% vs 30% with advance care planning"),
        ("4", "Caregiver distress", "Caregiver distress at 30 days", "Less stress with planning")]
for k, (n, h_, d_, ev_) in enumerate(kpis):
    x = Inches(0.55) + k * Inches(3.1)
    box(kp, x, Inches(1.2), Inches(2.95), Inches(4.6), TEAL_BG)
    disc(kp, x + Inches(0.95), Inches(1.45), Inches(1.05), TEAL, n, size=36)
    text(kp, x + Inches(0.15), Inches(2.7), Inches(2.65), Inches(3.0), [
        [(h_, {"size": 22, "bold": True, "color": TEAL})], [(d_, {"size": 15, "space": 6})],
        [(ev_, {"size": 13, "bold": True, "color": SUB, "space": 12})]], align=PP_ALIGN.CENTER)
text(kp, Inches(0.55), Inches(6.0), Inches(12.2), Inches(0.5),
     [[("Measured in a clinic pilot, before and after Sahara.", {"size": 16, "bold": True, "color": INK})]])
text(kp, Inches(0.55), Inches(6.5), Inches(12.2), Inches(0.3), "Thangasamy, Palliat Med Rep 2025; Detering, BMJ 2010. Effect evidence is mostly from abroad; our pilot measures it in India.", size=10, color=SUB)

# ---------- Rollout and cost ----------
ro = new_slide("Rollout Plan and Cost")
stages = [("1", "Free for patients and caregivers", TEAL), ("2", "Hospitals pay for EHR integration", BLUE), ("3", "National Cancer Grid centres", RUST)]
for k, (n, lab, tone) in enumerate(stages):
    x = Inches(0.55) + k * Inches(4.2)
    disc(ro, x + Inches(1.35), Inches(1.2), Inches(1.2), tone, n, size=40)
    text(ro, x, Inches(2.5), Inches(3.9), Inches(0.9), lab, size=20, bold=True, color=tone, align=PP_ALIGN.CENTER)
    if k < 2:
        arrow(ro, MSO_SHAPE.RIGHT_ARROW, x + Inches(3.2), Inches(1.55), Inches(1.4), Inches(0.5), GREY)
text(ro, Inches(0.55), Inches(3.65), Inches(12.2), Inches(0.4), "Cost per clinic", size=18, bold=True, color=INK)
for k, (big, small) in enumerate([("₹25,000", "one-time set-up"), ("₹15,000", "a month, recurring")]):
    x = Inches(0.55) + k * Inches(6.2)
    box(ro, x, Inches(4.1), Inches(6.05), Inches(1.45), WHITE, line=LN)
    text(ro, x + Inches(0.3), Inches(4.1), Inches(5.5), Inches(1.45),
         [[(big, {"size": 40, "bold": True, "color": TEAL})], [(small, {"size": 18, "color": SUB})]], anchor=MSO_ANCHOR.MIDDLE)
box(ro, Inches(0.55), Inches(5.8), Inches(12.25), Inches(0.95), TEAL)
text(ro, Inches(0.75), Inches(5.8), Inches(11.9), Inches(0.95),
     [[("No family should go into debt for treatment the patient said they didn’t want.", {"size": 20, "bold": True, "color": WHITE})]],
     anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

# ---------- Close ----------
cl = new_slide("Thank You")
text(cl, Inches(0.9), Inches(1.15), Inches(11.5), Inches(1.5),
     [[("India’s first goals-of-care plan that the patient, caregiver and doctor build together, sign, update, and carry to the emergency room.", {"size": 26, "bold": True, "color": TEAL})]],
     align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE)
for k, (img, name) in enumerate([("sharada", "Dr Sharada Vinod Kutty"), ("sujay", "Dr Sujay Halkur Shankar"), ("shirin", "Shirin Ayub"),
                                 ("basith", "Shaik Abdul Basith"), ("awaiz", "Shaik Muhammad Awaiz")]):
    x = Inches(0.75) + k * Inches(2.4)
    cl.shapes.add_picture(V + rf"\team-{img}.jpg", x + Inches(0.4), Inches(2.95), Inches(1.4), Inches(1.4))
    text(cl, x, Inches(4.4), Inches(2.2), Inches(0.7), name, size=14, bold=True, align=PP_ALIGN.CENTER)
cl.shapes.add_picture(LOGO, Inches(3.6), Inches(5.45), Inches(0.9), Inches(0.9))
text(cl, Inches(4.6), Inches(5.4), Inches(6), Inches(1.0),
     [[("sahara.wedevit.in", {"size": 36, "bold": True, "color": TEAL})], [("Zeros and Ones  ·  Sahara on Google Play", {"size": 15, "color": SUB})]], anchor=MSO_ANCHOR.MIDDLE)

# ---------- wording: the role is "caregiver"; no "flagged" ----------
FIX = [("Caregiver Self-Assessment Questionnaire", "Zarit Caregiver Burden (ZBI-4)"),
       ("Caregiver Self-Assessment", "Zarit Caregiver Burden (ZBI-4)"),
       ("Dr Shirin", "Shirin Ayub"),
       ("records only after patient consent", "records only after the patient agrees"),
       ("doctor, patient and family portal", "doctor, patient and caregiver portal"),
       ("Doctor and patient or family sign", "Doctor and patient or caregiver sign"),
       ("Missed topics are flagged", "Missed topics are marked"),
       ("a short check for family; flagged with", "a short check for the caregiver; it points to"),
       ("Family: invited relatives", "Caregiver: invited relatives"),
       ("patients and families log", "patients and caregivers log")]
def fix_tf(tf):
    for q in tf.paragraphs:
        full = "".join(r.text for r in q.runs)
        new = full
        for a, b_ in FIX: new = new.replace(a, b_)
        if new == full: continue
        done = False
        for r in q.runs:  # try within a single run first
            t0 = r.text
            for a, b_ in FIX: t0 = t0.replace(a, b_)
            if t0 != r.text: r.text = t0; done = True
        if not done or "".join(r.text for r in q.runs) != new:
            set_par(q, new)
for s_ in p.slides:
    for sh in s_.shapes:
        if sh.has_text_frame: fix_tf(sh.text_frame)
        elif sh.has_table:
            for row in sh.table.rows:
                for cell in row.cells: fix_tf(cell.text_frame)
for sh in S[3].shapes:  # Caregiver card subtitle
    if sh.has_text_frame and "FAMILY, AT HOME" in sh.text_frame.text:
        for q in sh.text_frame.paragraphs:
            for r in q.runs: r.text = r.text.replace("FAMILY, AT HOME", "AT HOME")

# ---------- order ----------
lst = p.slides._sldIdLst
ids = list(lst)
all_s = list(p.slides)
el = {id(s): e for s, e in zip(all_s, ids)}
order = [S[0], pr, ev2, tm2, S[3], gc, h1, h2, em, hw, S[6], fg, gp, kp, ro, cl, bk, pl, sf, S[4], S[5]]
gone = [s_ for s_ in all_s if all(s_ is not o_ for o_ in order)]
for e in ids: lst.remove(e)
for s_ in order: lst.append(el[id(s_)])
for s_ in gone:
    p.part.drop_rel(el[id(s_)].get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id'))
p.save(OUT)
print("saved", OUT, len(p.slides))
