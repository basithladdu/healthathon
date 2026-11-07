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
# Problem Statement, Users and setting, AI Solution Description and Key Features are Dr Sujay's
# own text (Problem_Statement_Sujay_2026-11-07.docx, "My edits", 7 Nov), word for word.
P1 = BODY_PT - 0.5
left = slide1.shapes[3]
olds = list(left.text_frame._txBody.findall(qn('a:p')))
tA, tPlain, tProbBody = olds[2], olds[7], olds[19]
plan = [('keep', 0)]
plan.append(('new', make_para(tA, [S('Team Zeros and Ones. ', b=True), S('Shaik Abdul Basith (technical lead) and Shaik Muhammad Awaiz (Android engineer) of WeDevIT built Sahara. Dr Sujay Halkur Shankar (pulmonologist, Tata IISc Medical School) defined the problem statement and the proposed solution and provided the clinical documents and scores behind the tools. Dr Sharada Vinod Kutty (pulmonologist, AIIMS Mangalagiri) wrote the clinical specifications, designed the goals-of-care document and tested mock clinical conversations. Shirin Ayub (medical student) designed the symptom log and defined caregiver portal needs. Legal advice was sought: inputs and refining the goals of care wording and format.')], autonum=True, space_before=4, size=P1)))
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

# ---------------- Page 2, left: Build Sprint Progress + Validation ----------------
l2 = slide2.shapes[1]
olds = list(l2.text_frame._txBody.findall(qn('a:p')))
tB = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Weeks 1\u20132 (9\u201322 Oct): ', 'shortlisted 9 Oct; native Android app, on Google Play closed testing from 14 Oct; map of nearby palliative centres; medicine organiser with prescription photo; symptom tracker.'),
    ('Week 3 (23\u201329 Oct): ', 'pain body map; hospital survey live 27 Oct; 28 Oct team meeting (9 decisions); \u201cAsk your documents\u201d; Dr Sharada\u2019s clinical specification on 29 Oct.'),
    ('Week 4 (30 Oct–5 Nov): ', 'advance care planning (goals of care) and emergency QR code; Telugu, Urdu, Kannada; legal advice 31 Oct; Dr Sujay tested the whole app 3 Nov; goals-of-care recording with Gemini onto the clinical document; signed, locked PDF.'),
    ('How it changed: ', 'in Round 1 the AI drafted a “Care Note”; our doctors asked that the doctor lead and the AI only write it down, on the clinical document. 6\u20137 Nov: Dr Sujay\u2019s 13-topic checklist, Guide me / Free flow, caregiver wellbeing (ZBI-4).'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tB, [S(' ')], size=5)))
plan.append(('keep', 5))
for head, rest in [
    ('Automatic tests: ', '877 Android and 594/594 website tests pass (they re-check every feature after each change).'),
    ('Goals of care: ', '8 made-up conversations (6 languages, a change of mind, a family disagreement): every answer on the right line of the document, 8 of 8.'),
    ('Ask your documents: ', '4 of 4 questions on a sample hospital report answered with the right source line.'),
    ('Dr Sujay, 3 Nov meeting: ', 'asked for a guide on what to talk about next \u2192 checklist in the doctors\u2019 order (consent, patient, family, CPR, dialysis\u2026) with a \u201cnot yet discussed\u201d prompt; 4 doses a day \u2192 4 ticks.'),
    ('Legal advice, 31 Oct: ', 'inputs and refining the goals-of-care wording and format.'),
    ('Not tested yet: ', 'no real-patient pilot; Gemini not yet measured on real Hindi or Telugu recordings; on-phone speech weak for Telugu, Kannada (typing offered).'),
]:
    plan.append(('new', make_para(tB, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
rebuild(l2, plan)

# ---------------- Page 2, right: Safety, Deployment, Links ----------------
r2 = slide2.shapes[2]
olds = list(r2.text_frame._txBody.findall(qn('a:p')))
tS = olds[2]
plan = [('keep', 0)]
for head, rest in [
    ('Doctor in charge: ', 'the doctor leads, corrects and signs every goals-of-care document; the AI never diagnoses or suggests treatment, and lists missing topics instead of guessing.'),
    ('Consent and privacy: ', 'recording starts only after the patient ticks “I agree”; Gemini is used only with that consent. Family members join by invite and see only their patient.'),
    ('Tamper-evident documents: ', 'signed documents are sealed with a SHA-256 fingerprint, so any change creates a new version that requires re-signatures; earlier versions stay visible.'),
    ('Inclusion: ', 'five languages, read aloud, large text, thumb signature; typing when speech fails. Demos use made-up patients only.'),
]:
    plan.append(('new', make_para(tS, [S(head, b=True), S(rest)], bullet=True, bullet_src=bullet_src, space_before=2)))
plan.append(('new', make_para(tS, [S(' ')], size=4)))
plan.append(('keep', 6))
for head, rest in [
    ('Pilot: ', '60\u201390 days in one palliative-care clinic with our doctors; patients consent as India\u2019s data-protection law (DPDP Act 2023) requires. We count talks signed and how fast wishes are found.'),
    ('Integration: ', 'works on its own, no hospital software needed; can sit beside the clinic’s electronic medical records as a plug-in; a FHIR export (the standard format hospital systems read) is ready.'),
    ('Ambient listening (next): ', 'today Sahara records on the doctor’s phone; next, room or lapel microphones in clinics capture the discussion hands-free, with the same consent.'),
    ('Cost (team estimate): ', 'servers about \u20b94,200 a month (Supabase, Vercel); Gemini about \u20b92 per 30-minute talk; about \u20b91,600 per clinic a month if 10 clinics share it. Free for patients; clinics or NGOs pay \u20b91,500\u20132,000 a month.'),
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
