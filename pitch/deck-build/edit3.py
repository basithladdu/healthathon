p='finale.py'; s=open(p,encoding='utf-8').read()
def rep(a,b):
    global s
    assert a in s, a[:70]; s=s.replace(a,b,1)
# A) evidence: keep the original 3 stats, add ONLY / BUT labels, bigger
a=s.index('NEW = [("<4%"'); b=s.index('    set_pars(shape(ev, num_), [big]); set_pars(shape(ev, txt_), lines)')+len('    set_pars(shape(ev, num_), [big]); set_pars(shape(ev, txt_), lines)')
s=s[:a]+'''for k_, (o_, ic_, num_, txt_) in enumerate(rows):
    for q in shape(ev, num_).text_frame.paragraphs:
        for r in q.runs: r.font.size = Pt(44); r.font.bold = True
    shape(ev, num_).height = Inches(0.8); shape(ev, num_).width = Inches(4)
    tt = shape(ev, txt_); tt.top = tt.top + Inches(0.2)
    for i_, q in enumerate(tt.text_frame.paragraphs):
        for r in q.runs: r.font.size = Pt(18 if i_ == 0 else 11)
    lab = {0: "ONLY", 2: "BUT"}.get(k_)
    if lab:
        text(ev, Inches(7.55), shape(ev, num_).top - Inches(0.3), Inches(2), Inches(0.35), lab, size=14, bold=True, color=RUST)'''+s[b:]
rep('    y = 1.35 + k * 1.85', '    y = 1.45 + k * 1.85')
# B) problem list slide + backup evidence slide + deployment slide, defined before order
rep('# ---------- order ----------', '''# ---------- extra: what goes wrong today ----------
pl = new_slide("What Goes Wrong Today")
probs = [("Wishes get lost", "The goals-of-care talk happens, but the signed plan never reaches the emergency room."),
         ("Symptoms go unseen", "Pain, breathlessness and distress at home are not reported between visits."),
         ("Pain relief is hard to find", "Families don\u2019t know which centre near them stocks oral morphine."),
         ("Caregivers carry it alone", "Families are out of the loop, and their own distress is never asked about."),
         ("Care in a language they don\u2019t read", "Notes and plans are in English; many families aren\u2019t.")]
for k, (h_, d_) in enumerate(probs):
    y = Inches(1.15) + k * Inches(1.12)
    o = pl.shapes.add_shape(MSO_SHAPE.OVAL, Inches(0.6), y + Inches(0.12), Inches(0.6), Inches(0.6))
    o.fill.solid(); o.fill.fore_color.rgb = RUST if k == 0 else TEAL; o.line.fill.background()
    q = o.text_frame.paragraphs[0]; q.alignment = PP_ALIGN.CENTER
    r = q.add_run(); r.text = str(k + 1); r.font.bold = True; r.font.size = Pt(18); r.font.color.rgb = WHITE
    text(pl, Inches(1.45), y, Inches(11.2), Inches(1.0), [[(h_, {"size": 24, "bold": True})], [(d_, {"size": 17, "color": SUB})]])

bk = new_slide("Backup: More Evidence")
more = [("<4%", "of the 7\u201310 million Indians who need palliative care get it", "Sharma, Thakkar et al., ecancer 2025"),
        ("79%", "of cancer hospitalisations push families into catastrophic spending", "Kastor & Mohanty, PLoS One 2018"),
        ("63%", "of end-stage patients: code status not found within 15 minutes of reaching the ER", "Russell, West J Emerg Med 2021"),
        ("0\u219292%", "goals-of-care documentation with one standard form (Chennai)", "Thangasamy, Palliat Med Rep 2025"),
        ("Low", "India\u2019s medical morphine use is a small fraction of the global average", "INCB; Lancet Commission on palliative care")]
for k, (big, d_, src) in enumerate(more):
    x = Inches(0.55) + (k % 3) * Inches(4.15); y = Inches(1.2) + (k // 3) * Inches(2.85)
    box(bk, x, y, Inches(3.95), Inches(2.65), WHITE, line=RGBColor(0xD5, 0xDE, 0xDB))
    text(bk, x + Inches(0.2), y + Inches(0.15), Inches(3.6), Inches(2.4),
         [[(big, {"size": 40, "bold": True, "color": TEAL})], [(d_, {"size": 15, "space": 4})], [(src, {"size": 10, "color": SUB, "space": 6})]])

dp = new_slide("Deployment, Cost and Sustainability")
qs = [("How is it deployed?", ["One clinic first: our own doctors, 90 days.", "Then the National Cancer Grid\u2019s 370+ centres.", "Runs on ABDM health IDs and plugs into hospital EHRs."]),
      ("Who pays?", ["Patients and families: always free.", "Hospitals and palliative clinics: per enrolled patient.", "States and programmes: a yearly subscription, like Oregon."]),
      ("How does it last?", ["Hospital fees cover servers, AI and support.", "Grants and CSR fund new clinics and languages.", "Taiwan already reimburses advance care planning."])]
for k, (h_, items) in enumerate(qs):
    x = Inches(0.55) + k * Inches(4.15)
    box(dp, x, Inches(1.2), Inches(3.95), Inches(4.4), TEAL_BG)
    text(dp, x + Inches(0.25), Inches(1.4), Inches(3.5), Inches(4.1),
         [[(h_, {"size": 22, "bold": True, "color": TEAL})]] + [[("\u2022  " + it, {"size": 16, "space": 12})] for it in items])
box(dp, Inches(0.55), Inches(5.85), Inches(12.25), Inches(1.0), TEAL)
text(dp, Inches(0.75), Inches(5.85), Inches(11.9), Inches(1.0),
     [[("No family should go into debt for treatment the patient said they didn\u2019t want.", {"size": 20, "bold": True, "color": WHITE})]],
     anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)

# ---------- order ----------''')
rep('order = [S[0], S[1], pr, S[2], S[3], dm, vt, cp, S[6], S[9], S[10], S[12], S[4], S[5]]',
    'order = [S[0], S[1], pr, pl, S[2], S[3], dm, vt, cp, S[6], S[9], S[10], dp, S[12], bk, S[4], S[5]]')
open(p,'w',encoding='utf-8').write(s); print('ok')
