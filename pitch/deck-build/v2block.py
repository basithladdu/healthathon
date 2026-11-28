
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
    gw = Inches(2.45) * len(ppl) + Inches(0.15) * (len(ppl) + 1)
    box(tm2, gx, Inches(1.25), gw, Inches(4.55), bgc, line=tone)
    pill(tm2, gx + Inches(0.15), Inches(1.05), gname, tone, w=Inches(1.9))
    for j, (img, name, role, inst) in enumerate(ppl):
        x = gx + Inches(0.15) + j * Inches(2.6)
        tm2.shapes.add_picture(V + rf"\team-{img}.jpg", x + Inches(0.4), Inches(1.55), Inches(1.65), Inches(1.65))
        text(tm2, x, Inches(3.3), Inches(2.45), Inches(2.4), [
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
text(gc, Inches(0.55), Inches(6.42), Inches(12.25), Inches(0.36), [[
    ("Medical credits   ", {"size": 12, "bold": True, "color": TEAL}),
    ("Dr Sujay Halkur Shankar: framework and 13 topics   ·   Dr Sharada Vinod Kutty: clinical specification and emergency care plan   ·   Legal advice taken on wording and format", {"size": 12, "color": SUB})]])

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
    text(te, x + Inches(1.2), Inches(3.5), Inches(2.65), Inches(0.75), name, size=20, bold=True, anchor=MSO_ANCHOR.MIDDLE)
    text(te, x + Inches(0.25), Inches(4.5), Inches(3.55), Inches(2.2),
         [[(big, {"size": 32 if k < 2 else 24, "bold": True, "color": tone})], [(line1, {"size": 14, "color": SUB, "space": 4})]])
text(te, Inches(0.55), Inches(6.82), Inches(12.2), Inches(0.3),
     "The app itself is in 11 languages  ·  Android (Kotlin + Compose), Next.js, Supabase  ·  FHIR-ready for hospital software", size=11, color=SUB)

# ---------- We filled the gaps (native table) ----------
fg = new_slide("We Filled the Gaps")
cols_ = ["PalaSh\nAIIMS Bathinda", "BandhuCare\nCMC Vellore", "Palli Sahayak\nKCDH-A", "Kerala Care\ngrid", "London / Oregon /\nTaiwan", "Sahara"]
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
         [[(big + "  ", {"size": 44, "bold": True, "color": TEAL}), (small, {"size": 20, "color": SUB})]], anchor=MSO_ANCHOR.MIDDLE)
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
