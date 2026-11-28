import copy, sys
from pptx import Presentation
from pptx.util import Emu

SRC = r"F:\CODE\healthathon\output\final-submission\Sahara-Final-Presentation.pptx"
OUT = r"F:\CODE\healthathon\output\final-submission\Sahara-Finale-Pitch-2026-11-27.pptx"
PITCH = r"F:\CODE\healthathon\pitch"

p = Presentation(SRC)
S = list(p.slides)  # 0-based


def shape(slide, sid):
    for sh in slide.shapes:
        if sh.shape_id == sid:
            return sh
    raise KeyError(sid)


def set_par(par, text):
    """Replace a paragraph's text, keeping the first run's formatting."""
    runs = par.runs
    if not runs:
        par.text = text
        return
    runs[0].text = text
    for r in runs[1:]:
        r._r.getparent().remove(r._r)


def set_pars(sh, texts):
    """Set the non-empty paragraphs of a text frame in order; extra old ones are removed."""
    pars = [q for q in sh.text_frame.paragraphs if q.text.strip()]
    assert len(pars) >= len(texts), (sh.name, len(pars), texts)
    for q, t in zip(pars, texts):
        set_par(q, t)
    for q in pars[len(texts):]:
        q._p.getparent().remove(q._p)


def replace_in(slide, old, new):
    n = 0
    for sh in slide.shapes:
        if not sh.has_text_frame:
            continue
        for par in sh.text_frame.paragraphs:
            for r in par.runs:
                if old in r.text:
                    r.text = r.text.replace(old, new); n += 1
    assert n, (old,)


# 1 Title: languages
replace_in(S[0], "English, Hindi, Telugu, Urdu, Kannada", "11 Indian languages")

# 12 -> Live demo slide
d = S[11]
set_pars(shape(d, 174), ["Live Demo: One Patient, from Clinic to Emergency Room"])
cards = {
    178: ["In the clinic", "The doctor records the goals-of-care talk; Sahara writes the document; doctor and family sign."],
    182: ["At home", "The patient logs how they feel and ticks medicines. Simple Mode gives older patients four big spoken buttons."],
    186: ["The family", "Invited relatives share the care, get reminders and check their own wellbeing."],
    190: ["In an emergency", "Any phone scans the QR and opens the latest signed wishes on the website."],
}
for sid, t in cards.items():
    set_pars(shape(d, sid), t)
set_pars(shape(d, 192), ["\u25B6  Demo video plays here (about 2 minutes). If it does not play, the backup slides are at the end."])
set_pars(shape(d, 194), ["Vision: one palliative-care platform for all of India, working alongside any hospital\u2019s records, with no unified hospital system needed."])

# 10 Safety: DPDP and new features
sf = S[9]
set_pars(shape(sf, 158), ["Safety, Privacy and Responsible AI"])
pv = [q for q in shape(sf, 164).text_frame.paragraphs if q.text.strip()]
set_par(pv[1], "\u2713  Consent as India\u2019s DPDP Act requires; research use only if the patient opts in")
fe = [q for q in shape(sf, 179).text_frame.paragraphs if q.text.strip()]
set_par(fe[0], "\u2713  11 Indian languages, and it reads text aloud")
set_par(fe[2], "\u2713  Simple Mode for older patients: big spoken buttons, no typing")

# 11 Cost + plans merged
c = S[10]
set_pars(shape(c, 166), ["What \u20B95 Lakh Builds, and How Sahara Sustains Itself"])
for sid, t in {
    171: ["With \u20B95 lakh", "A 6-month pilot in 3 clinics: staff training, a legal check of consent forms, servers and our team\u2019s time."],
    175: ["Online consultations", "Goals-of-care talks over video, transcribed with consent and signed from home."],
    179: ["Beyond cancer", "The same platform for heart, lung and kidney failure and dementia."],
    183: ["Self-sustaining", "Clinics or NGOs pay a small monthly fee. Free for patients and families. Servers can stay in India."],
}.items():
    set_pars(shape(c, sid), t)
rows = [
    ["How the \u20B95 lakh is used", "Basis", "\u20B9"],
    ["Set-up and staff training, 3 clinics", "\u20B925,000 a clinic", "75,000"],
    ["Consent forms and legal check (DPDP)", "Once", "25,000"],
    ["Servers, website and Sarvam AI, 12 months", "\u20B94,200 a month + \u20B923 a talk, 300 talks a month", "1,33,200"],
    ["Online consultations: build and test", "New feature", "1,00,000"],
    ["Team time: engineers and clinicians", "6 months", "1,66,800"],
    ["Total", "", "5,00,000"],
]
tbl = shape(c, 184).table
for r, vals in enumerate(rows):
    for col, v in enumerate(vals):
        cell = tbl.cell(r, col)
        pars = cell.text_frame.paragraphs
        set_par(pars[0], v)
        for q in pars[1:]:
            q._p.getparent().remove(q._p)
set_pars(shape(c, 186), ["After the pilot: \u20B91,500\u20132,000 per clinic a month keeps it running"])
note = shape(c, 187)
first = [q for q in note.text_frame.paragraphs if q.text.strip()][0]
set_par(first, "Planning estimate from public prices: US$1 \u2248 \u20B994; Supabase $25 and Vercel $20 a month; Sarvam AI \u20B945 per audio hour; 100 talks per clinic a month.")

# 13 Thank you: no placeholder
replace_in(S[12], "Demo video: [add demo video link]", "Demo video: shown live")

# New image slides (validated tools, competitors)
blank = S[8].slide_layout
def image_slide(png):
    s = p.slides.add_slide(blank)
    for ph in list(s.placeholders):
        ph._element.getparent().remove(ph._element)
    s.shapes.add_picture(png, 0, 0, p.slide_width, p.slide_height)
    return s
vt = image_slide(PITCH + r"\Sahara_Validated_Tools_Slide.png")
cp = image_slide(PITCH + r"\Sahara_Competitor_Slide.png")

# Order: 1,2,3,4, demo(12), validated, competitors, 7, 10, 11, 13, backup 5, 6. Drop 8, 9.
lst = p.slides._sldIdLst
ids = list(lst)
by_slide = {}
for el, s in zip(ids, list(p.slides)):
    by_slide[id(s)] = el
all_slides = list(p.slides)
order = [S[0], S[1], S[2], S[3], S[11], vt, cp, S[6], S[9], S[10], S[12], S[4], S[5]]
drop = [S[7], S[8]]
els = {id(s): el for s, el in zip(all_slides, ids)}
for el in ids:
    lst.remove(el)
for s in order:
    lst.append(els[id(s)])
for s in drop:
    rid = els[id(s)].get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')
    p.part.drop_rel(rid)

# Mark the backup slides in their titles
for s in (S[4], S[5]):
    for sh in s.shapes:
        if sh.has_text_frame and sh.text_frame.text.startswith("Solution Overview"):
            par = sh.text_frame.paragraphs[0]
            set_par(par, "Backup: " + par.text)
            break

p.save(OUT)
print("saved", OUT, len(p.slides), "slides")
