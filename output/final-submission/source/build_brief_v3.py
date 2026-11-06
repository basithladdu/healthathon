"""Fill the organisers' Two-page Solution Brief Template without changing its structure.

Only the italic guidance text is replaced. Headings, boxes, logo, fonts, colours and
slide geometry stay exactly as supplied. Title runs keep their own colours.
"""
import copy, sys
from pathlib import Path
from pptx import Presentation
from pptx.oxml.ns import qn
from lxml import etree

SRC = Path(r"C:\Users\basit\sahara-build\deck\Two-page Solution Brief Template.pptx")
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\basit\sahara-build\deck\Sahara-Two-Page-Solution-Brief.pptx")
BODY_PT = float(sys.argv[2]) if len(sys.argv) > 2 else 10.5
A = 'http://schemas.openxmlformats.org/drawingml/2006/main'

prs = Presentation(SRC)

def set_title(shape):
    runs = shape.text_frame.paragraphs[0].runs
    # runs: "[Team Name]" " " "|" "[Solution Name]" (blue, blue, blue, black) -- keep each run's formatting
    texts = [r.text for r in runs]
    for r in runs:
        if r.text == '[Team Name]': r.text = 'Zeros and Ones'
        elif r.text == '[Solution Name]': r.text = 'Sahara'
        elif r.text.startswith('[Solution'): r.text = 'Sahara'
    assert 'Zeros and Ones' in shape.text_frame.text and 'Sahara' in shape.text_frame.text, texts

def make_para(template_p, segments, bullet=False, bullet_src=None, autonum=False, size=None, space_before=None):
    """New paragraph copying template_p's pPr. segments = [(text, {'b':..,'link':..})]."""
    p = etree.SubElement(etree.Element('dummy'), qn('a:p'))
    pPr = template_p.find(qn('a:pPr'))
    if bullet and bullet_src is not None:
        pPr = bullet_src.find(qn('a:pPr'))
    if pPr is not None:
        newp = copy.deepcopy(pPr)
        if not bullet and not autonum:
            for tag in ('a:buAutoNum', 'a:buChar', 'a:buFont', 'a:buClr', 'a:buSzPct'):
                for el in newp.findall(qn(tag)): newp.remove(el)
            newp.set('indent', '0')
            newp.set('marL', '12700')
        if space_before is not None:
            for el in newp.findall(qn('a:spcBef')): newp.remove(el)
            sb = etree.Element(qn('a:spcBef')); etree.SubElement(sb, qn('a:spcPts')).set('val', str(int(space_before * 100)))
            # spcBef must come after lnSpc
            ln = newp.find(qn('a:lnSpc'))
            if ln is not None: ln.addnext(sb)
            else: newp.insert(0, sb)
        # tighter line spacing for body text
        for el in newp.findall(qn('a:lnSpc')): newp.remove(el)
        ls = etree.Element(qn('a:lnSpc')); etree.SubElement(ls, qn('a:spcPct')).set('val', '100000')
        newp.insert(0, ls)
        p.append(newp)
    for text, opts in segments:
        r = etree.SubElement(p, qn('a:r'))
        rPr = etree.SubElement(r, qn('a:rPr'))
        rPr.set('lang', 'en-IN'); rPr.set('sz', str(int((size or BODY_PT) * 100)))
        rPr.set('b', '1' if opts.get('b') else '0'); rPr.set('i', '0'); rPr.set('dirty', '0')
        fill = etree.SubElement(rPr, qn('a:solidFill'))
        etree.SubElement(fill, qn('a:srgbClr')).set('val', opts.get('color', '000000'))
        rPr.set('u', 'sng' if opts.get('link') else 'none')
        lat = etree.SubElement(rPr, qn('a:latin')); lat.set('typeface', opts.get('font', 'Arial'))
        cs = etree.SubElement(rPr, qn('a:cs')); cs.set('typeface', opts.get('font', 'Arial'))
        if opts.get('link'):
            rel = opts['part'].relate_to(opts['link'], 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink', is_external=True)
            h = etree.SubElement(rPr, qn('a:hlinkClick'))
            h.set('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id', rel)
        etree.SubElement(r, qn('a:t')).text = text
    return p

def rebuild(shape, plan):
    """plan: list of ('keep', index) for template paragraphs kept as-is, or ('new', element)."""
    txBody = shape.text_frame._txBody
    old = list(txBody.findall(qn('a:p')))
    for p in old: txBody.remove(p)
    for kind, val in plan:
        txBody.append(old[val] if kind == 'keep' else val)
    return old

def S(text, **o): return (text, o)

slide1, slide2 = prs.slides[0], prs.slides[1]
set_title(slide1.shapes[0]); set_title(slide2.shapes[4])

# ---------------- Page 1, left: About the Team + Problem ----------------
left = slide1.shapes[3]
olds = list(left.text_frame._txBody.findall(qn('a:p')))
tA, tPlain, tProbBody = olds[2], olds[7], olds[19]
plan = [('keep', 0)]
plan.append(('new', make_para(tA, [S('Team Zeros and Ones. ', b=True), S('Shaik Abdul Basith (technical lead: website and the AI that writes down the conversation) and Shaik Muhammad Awaiz (Android app and server) built Sahara. Shirin Ayub (medical student) designed how patients log symptoms. Rituparna P, a lawyer, checked our form against India\u2019s advance-directive (\u201cliving will\u201d) format.')], autonum=True, space_before=5)))
plan.append(('new', make_para(tA, [S('Doctor partners. ', b=True), S('Dr Sharada Vinod Kutty and Dr Sujay Halkur Shankar are pulmonologists (lung specialists) who treat many lung-cancer patients; Dr Sujay also works in palliative care (care that eases pain and distress in a serious illness). They chose this problem from their own clinics, wrote the clinical specification (the medical rules the app follows), set the order of the conversation checklist, and tested the app with us, most recently in the 3 Nov team meeting.')], autonum=True, space_before=4)))
plan.append(('new', make_para(tPlain, [S(' ')], size=6)))
plan.append(('keep', 17))
plan.append(('new', make_para(tProbBody, [S('Goals-of-care wishes are lost when they matter most. ', b=True), S('A goals-of-care conversation is when the doctor asks a seriously ill patient and family what treatment they want or refuse near the end of life: CPR (restarting the heart), a ventilator, ICU, dialysis, where to die. It is rarely written on the legal advance-directive form. Example: a 62-year-old woman with advanced breast cancer tells her doctor her wishes; two months later she is unconscious in the emergency department, her children disagree about CPR, and nobody can find what she said.')], space_before=4)))
plan.append(('new', make_para(tProbBody, [S('Evidence: ', b=True), S('about 15.6 lakh new cancer cases in India in 2024 (GLOBOCAN); 98% of patients with metastatic (spread) cancer had unmet palliative-care needs (Patil et al., 2024); 88% of surveyed urban patients wanted to choose their own end-of-life treatment (IndiaSpend, 2019); only 11.5% of doctors in one hospital were trained in advance directives (Jacob et al., 2026).')], space_before=4)))
plan.append(('new', make_para(tProbBody, [S('Users and setting: ', b=True), S('patients in palliative care and their families, at home; their palliative, oncology or lung doctor, in the clinic; and the emergency doctor, who has minutes to decide and needs the patient\u2019s wishes at once.')], space_before=4)))
rebuild(left, plan)

# ---------------- Page 1, right: AI solution, technology, features ----------------
right = slide1.shapes[5]
olds = list(right.text_frame._txBody.findall(qn('a:p')))
tBody = olds[2]
bullet_src = list(slide2.shapes[2].text_frame._txBody.findall(qn('a:p')))[2]  # template bullet paragraph
plan = [('keep', 0)]
plan.append(('new', make_para(tBody, [S('Sahara is a palliative-care app (Android and website). In the clinic the doctor leads the goals-of-care conversation; Sahara listens and writes. After the patient ticks \u201cI agree\u201d, the doctor taps Record. Google\u2019s Gemini AI transcribes the talk (turns speech into text), labels who said what, and places each answer on the legal advance-directive form (word for word, as our lawyer checked). A checklist in the doctors\u2019 order ticks itself and, at the end, lists any topic not yet discussed. The doctor corrects it; doctor and patient or family sign on the phone, and the form locks: any later edit cancels the signatures. The signed wishes open from the patient\u2019s emergency QR code (a barcode any phone camera can scan). The AI only writes things down: the doctor makes every clinical decision.')], space_before=4)))
plan.append(('new', make_para(tBody, [S(' ')], size=5)))
plan.append(('keep', 6))
plan.append(('new', make_para(tBody, [S('Android (Kotlin, Jetpack Compose); website (Next.js, Vercel). '), S('Supabase', b=True), S(' is the server: sign-in, a Postgres database with row-level security (each family sees only its own records), and server functions that keep the AI keys off the phone. '), S('Google Gemini', b=True), S(' (backup models when busy) transcribes, fills the form and answers \u201cAsk your documents\u201d with the line it used. With no internet, '), S('OpenAI Whisper', b=True), S(' transcribes on the phone itself; if that fails, the doctor types. Google ML Kit: 5 languages and read-aloud on the phone.')], space_before=4)))
plan.append(('new', make_para(tBody, [S(' ')], size=5)))
plan.append(('keep', 14))
for head, rest in [
    ('Goals-of-care conversation: ', 'consent, record, transcribe, fill the legal form, sign and lock.'),
    ('Emergency QR code and SOS: ', 'wishes, contacts and medicines in one scan.'),
    ('How I feel: ', 'symptoms, a 0\u201310 distress scale (how upset the patient feels) and a pain body map; charts for the doctor.'),
    ('Medicines and reports: ', 'a tick for every dose, reminders, prescription photo; ask a question about a hospital report.'),
    ('Help nearby and family: ', 'palliative centres, ambulances, support groups; invited family share the record.'),
]:
    plan.append(('new', make_para(tBody, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
rebuild(right, plan)

# ---------------- Page 2, left: Build Sprint Progress + Validation ----------------
l2 = slide2.shapes[1]
olds = list(l2.text_frame._txBody.findall(qn('a:p')))
tB = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Weeks 1\u20132 (9\u201322 Oct): ', 'shortlisted 9 Oct; native Android app, on Google Play closed testing from 14 Oct; map of nearby palliative centres; medicine organiser with prescription photo; symptom tracker.'),
    ('Week 3 (23\u201329 Oct): ', 'pain body map; hospital survey live 27 Oct; 28 Oct team meeting (9 decisions); \u201cAsk your documents\u201d; Dr Sharada\u2019s clinical specification on 29 Oct.'),
    ('Week 4 (30 Oct\u20135 Nov): ', 'advance directive (living will) and emergency QR code; Telugu, Urdu, Kannada; legal review 31 Oct; Dr Sujay tested the whole app 3 Nov; goals-of-care recording with Gemini onto the legal form; signed, locked PDF.'),
    ('How it changed: ', 'in Round 1 the AI drafted a \u201cCare Note\u201d; our doctors asked that the doctor lead and the AI only write it down, on the legal form.'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tB, [S(' ')], size=5)))
plan.append(('keep', 5))
for head, rest in [
    ('Automatic tests: ', '877 Android and 594/594 website tests pass (they re-check every feature after each change).'),
    ('Goals of care: ', '8 made-up conversations (6 languages, a change of mind, a family disagreement): every answer on the right line of the form, 8 of 8.'),
    ('Ask your documents: ', '4 of 4 questions on a sample hospital report answered with the right source line.'),
    ('Dr Sujay, 3 Nov meeting: ', 'asked for a guide on what to talk about next \u2192 checklist in the doctors\u2019 order (consent, patient, family, CPR, dialysis\u2026) with a \u201cnot yet discussed\u201d prompt; 4 doses a day \u2192 4 ticks. Team chat: tubes-and-drains tracker, darker text.'),
    ('Rituparna P, lawyer, 31 Oct: ', 'use the form\u2019s exact legal wording \u2192 copied word for word.'),
    ('Not tested yet: ', 'no real-patient pilot; Gemini not yet measured on real Hindi or Telugu recordings; on-phone speech is weak for Telugu and Kannada, so typing is offered.'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
rebuild(l2, plan)

# ---------------- Page 2, right: Safety, Deployment, Links ----------------
r2 = slide2.shapes[2]
olds = list(r2.text_frame._txBody.findall(qn('a:p')))
tS = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Doctor in charge: ', 'the doctor leads, corrects and signs every form; the AI never diagnoses or suggests treatment, and lists missing topics instead of guessing.'),
    ('Consent and privacy: ', 'recording starts only after the patient ticks “I agree”; Gemini is used only with that consent. Family members join by invite and see only their patient.'),
    ('Tamper-evident forms: ', 'signed forms are sealed with a SHA-256 fingerprint, so any change cancels the signatures; earlier versions stay visible.'),
    ('Inclusion: ', 'five languages, read aloud, large text, thumb signature; typing when speech fails. Demos use made-up patients only.'),
]:
    plan.append(('new', make_para(tS, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tS, [S(' ')], size=4)))
plan.append(('keep', 6))
for head, rest in [
    ('Pilot: ', '60\u201390 days in one palliative-care clinic with our doctors; patients consent as India\u2019s data-protection law (DPDP Act 2023) requires. We count talks signed and how fast wishes are found.'),
    ('Integration: ', 'works on its own, no hospital software needed; a FHIR export (the standard format hospital systems read) is ready for later.'),
    ('Cost (team estimate): ', 'servers about \u20b93,900 a month (Supabase, Vercel); Gemini about \u20b92 per 30-minute talk; about \u20b91,600 per clinic a month if 10 clinics share it. Free for patients; clinics or NGOs pay \u20b91,500\u20132,000 a month.'),
]:
    plan.append(('new', make_para(tS, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tS, [S(' ')], size=4)))
plan.append(('keep', 11))
# keep the template's three link lines, filling each with its link
link_ps = [olds[13], olds[14], olds[15]]
fills = [
    ('MVP', ': ', 'sahara.wedevit.in', 'https://sahara.wedevit.in', ' \u00b7 Android app on Google Play', 'https://play.google.com/store/apps/details?id=in.wedevit.sahara'),
    ('Video Demo Walkthrough', ': ', 'link to be added', None, '', None),
    ('Other Supporting Links', ': ', 'code (private, access on request)', 'https://github.com/basithladdu/healthathon', '', None),
]
part = slide2.part
for p, (label, sep, txt, url, extra, url2) in zip(link_ps, fills):
    runs = p.findall(qn('a:r'))
    base = copy.deepcopy(runs[0])
    for r in runs: p.remove(r)
    def add(text, link):
        r = copy.deepcopy(base); r.find(qn('a:t')).text = text
        rPr = r.find(qn('a:rPr')); rPr.set('sz', '1050')
        for h in rPr.findall(qn('a:hlinkClick')): rPr.remove(h)
        if link:
            rel = part.relate_to(link, 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink', is_external=True)
            h = etree.SubElement(rPr, qn('a:hlinkClick')); h.set('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id', rel)
        else:
            rPr.set('u', 'none')
            for f in rPr.findall(qn('a:solidFill')): f.find(qn('a:srgbClr')).set('val', '000000')
        end = p.find(qn('a:endParaRPr'))
        if end is not None: end.addprevious(r)
        else: p.append(r)
    add(label + sep, None)
    add(txt, url)
    if extra: add(extra, url2)
    plan.append(('new', p))
rebuild(r2, plan)

prs.save(OUT)
print('saved', OUT)
