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
plan.append(('new', make_para(tA, [S('Team Zeros and Ones. ', b=True), S('Shaik Abdul Basith (technical lead: product, web and AI) and Shaik Muhammad Awaiz (Android app, server and deployment) built Sahara. Shirin Ayub (medical student) shaped the symptom tracker. Rituparna P reviewed the goals-of-care form for legal wording.')], autonum=True, space_before=5)))
plan.append(('new', make_para(tA, [S('Doctor partners. ', b=True), S('Dr Sharada Vinod Kutty and Dr Sujay Halkur Shankar are pulmonary specialists who have worked extensively with lung cancer; Dr Sujay also has palliative-medicine experience. They chose the problem, wrote the clinical specification and the order of the goals-of-care checklist, and reviewed the app in team walkthroughs, most recently on 3 Nov.')], autonum=True, space_before=4)))
plan.append(('new', make_para(tPlain, [S(' ')], size=6)))
plan.append(('keep', 17))
plan.append(('new', make_para(tProbBody, [S('A patient\u2019s wishes are often lost when they matter most. ', b=True), S('People with advanced cancer seldom have their wishes about CPR, ICU care, dialysis or place of death written down. When the patient can no longer speak, family members disagree and the emergency doctor cannot find the earlier conversation. Between visits, symptoms and medicines also go unrecorded.')], space_before=4)))
plan.append(('new', make_para(tProbBody, [S('Evidence: ', b=True), S('about 15.6 lakh new cancer cases in India in 2024 (GLOBOCAN); 98% unmet palliative-care need in metastatic cancer (Patil et al., 2024); 88% of surveyed urban patients wanted to choose their end-of-life treatment (IndiaSpend, 2019); only 11.5% of doctors in one tertiary hospital were trained in advance directives (Jacob et al., 2026).')], space_before=4)))
plan.append(('new', make_para(tProbBody, [S('Users and setting: ', b=True), S('patients receiving palliative care and their families at home; palliative, oncology and pulmonary doctors in clinics and hospitals; emergency doctors who need the patient\u2019s wishes at once.')], space_before=4)))
rebuild(left, plan)

# ---------------- Page 1, right: AI solution, technology, features ----------------
right = slide1.shapes[5]
olds = list(right.text_frame._txBody.findall(qn('a:p')))
tBody = olds[2]
bullet_src = list(slide2.shapes[2].text_frame._txBody.findall(qn('a:p')))[2]  # template bullet paragraph
plan = [('keep', 0)]
plan.append(('new', make_para(tBody, [S('Sahara is a palliative-care companion for patients, families and doctors (Android app and website). The doctor starts a goals-of-care discussion in the app and, with consent, records it. Google Gemini transcribes the talk, labels who said what and places each statement on the legally reviewed goals-of-care form (Sections I\u2013IV). A checklist flags topics not yet discussed. The doctor reviews and edits; doctor and patient or family sign on the phone; the note is locked, and any later edit voids the signatures. The signed wishes appear on the patient\u2019s emergency QR card. Between visits, patients and families log symptoms and medicines that the doctor sees. AI only transcribes and fills the form: the doctor makes every clinical decision.')], space_before=4)))
plan.append(('new', make_para(tBody, [S(' ')], size=5)))
plan.append(('keep', 6))
plan.append(('new', make_para(tBody, [S('Android (Kotlin, Jetpack Compose) and website (Next.js on Vercel); Supabase for sign-in, Postgres with row-level security and server functions. AI: ', ), S('Google Gemini', b=True), S(' (Flash-Lite first, Flash models as backup when busy) for transcription, speaker labels, mapping to the form and \u201cAsk your documents\u201d answers with sources; '), S('OpenAI Whisper', b=True), S(' on the phone or browser as the offline fallback; on-device speaker separation; Google ML Kit for on-phone translation and read aloud. English, Hindi, Telugu, Urdu and Kannada.')], space_before=4)))
plan.append(('new', make_para(tBody, [S(' ')], size=5)))
plan.append(('keep', 14))
for head, rest in [
    ('Goals-of-care discussion: ', 'record, transcribe, fill the legal form, sign and lock.'),
    ('Emergency QR card: ', 'the patient\u2019s wishes, contacts and medicines in one scan.'),
    ('Daily check-ins: ', 'symptoms, 0\u201310 distress, pain body map and trend charts for the doctor.'),
    ('Medicines and records: ', 'per-dose reminders, labs, documents and \u201cAsk your documents\u201d.'),
    ('Help nearby: ', 'palliative centres, ambulances and support groups; family sharing.'),
]:
    plan.append(('new', make_para(tBody, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
rebuild(right, plan)

# ---------------- Page 2, left: Build Sprint Progress + Validation ----------------
l2 = slide2.shapes[1]
olds = list(l2.text_frame._txBody.findall(qn('a:p')))
tB = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Weeks 1\u20132: ', 'Android app with check-ins, pain map, medicines and nearby palliative centres; hospital survey (live 27 Oct); \u201cAsk your documents\u201d with Gemini; emergency QR card.'),
    ('Week 3: ', 'Dr Sharada\u2019s clinical specification became the emergency care plan and living will; Telugu, Urdu and Kannada; legal review of the goals-of-care form.'),
    ('Week 4: ', 'doctor-led goals-of-care recording, Gemini transcription mapped word for word onto the legal form, drawn signatures and a locked PDF; doctor side (patients, schedule, symptom history); design polish.'),
    ('How it evolved: ', 'from Round 1\u2019s AI-drafted \u201cCare Note\u201d to a doctor-led discussion on a legally reviewed form, plus a daily companion for patients and families.'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tB, [S(' ')], size=5)))
plan.append(('keep', 5))
for head, rest in [
    ('Tests: ', '877 Android unit tests and 594 website tests pass.'),
    ('Goals of care: ', '8 fictional doctor\u2013patient conversations (English, Hindi, Hinglish with a change of mind, Telugu, Urdu, Kannada, a family disagreement, a short talk); the form mapping passes all 8.'),
    ('Ask your documents: ', '4 of 4 test questions answered with the correct source line.'),
    ('Doctor and legal feedback: ', 'the 3 Nov walkthrough added a tubes-and-lines tracker, clearer \u201cIf\u2026\u201d wording, darker text and a shorter side-effect list; the legal review made us copy the form word for word.'),
    ('Limits: ', 'no real-patient pilot yet; Gemini accuracy on real Hindi and Telugu audio not yet measured; on-phone speech is weak for Telugu and Kannada, so typing is offered.'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
rebuild(l2, plan)

# ---------------- Page 2, right: Safety, Deployment, Links ----------------
r2 = slide2.shapes[2]
olds = list(r2.text_frame._txBody.findall(qn('a:p')))
tS = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Doctor in charge: ', 'the doctor starts, reviews and edits every note; AI never diagnoses or advises treatment.'),
    ('Consent and privacy: ', 'recording starts only after a consent tick; Gemini is used only with consent, otherwise Whisper runs on the device. Access is per patient, by invite only.'),
    ('Tamper-evident notes: ', 'signed notes are sealed (SHA-256); any edit voids the signatures and earlier versions stay visible.'),
    ('Inclusion: ', 'five languages, read aloud, large text; typing when speech fails. Demos use fictional patients only.'),
]:
    plan.append(('new', make_para(tS, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tS, [S(' ')], size=4)))
plan.append(('keep', 6))
for head, rest in [
    ('Pilot: ', '60\u201390 days in a palliative-care clinic with our doctors, with a consent process under India\u2019s DPDP Act.'),
    ('Integration: ', 'works on its own; FHIR export ready for a later link to hospital records or CARE.'),
    ('Cost (estimate): ', 'about \u20b94,000 a month hosting (Supabase and Vercel) plus a few rupees of Gemini per conversation; free for patients, paid by hospitals or NGOs per clinic.'),
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
