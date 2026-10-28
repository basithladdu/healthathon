from pathlib import Path
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR, MSO_AUTO_SIZE
from pptx.oxml.xmlchemy import OxmlElement
from pptx.dml.color import RGBColor
from pptx.enum.dml import MSO_THEME_COLOR

ROOT=Path(__file__).resolve().parents[3]
SRC=ROOT/'source-materials/healthathon-phase-2/2026-10-27-final-deliverables'
OUT=ROOT/'submission/phase-2/final-deliverables-2026-10-28'
OUT.mkdir(parents=True,exist_ok=True)

# Helpers that retain the template's slide size, theme, masters and title/footer objects.
def clear_bullet(p):
    pPr=p._p.get_or_add_pPr()
    for tag in ('buChar','buAutoNum','buNone'):
        el=pPr.find('{http://schemas.openxmlformats.org/drawingml/2006/main}'+tag)
        if el is not None: pPr.remove(el)
    pPr.append(OxmlElement('a:buNone'))

def set_paragraph(shape, blocks, body_pt=17, margin=0, font_name='Arial'):

    tf=shape.text_frame
    tf.clear()
    tf.word_wrap=True
    tf.auto_size=MSO_AUTO_SIZE.NONE
    tf.margin_left=Inches(margin); tf.margin_right=Inches(margin)
    for i,b in enumerate(blocks):
        # tuple: (text, kind); kinds: heading, body, bullet, small
        txt,kind=b
        p=tf.paragraphs[0] if i==0 else tf.add_paragraph()
        p.text=txt
        p.space_after=Pt(7 if kind in ('heading','bullet') else 4)
        p.space_before=Pt(2 if kind=='heading' else 0)
        p.level=0
        clear_bullet(p)
        p.alignment=PP_ALIGN.LEFT
        for run in p.runs:
            run.font.name=font_name
            run.font.size=Pt({'heading':body_pt,'body':body_pt,'bullet':body_pt,'small':max(body_pt-2,8)}[kind])
            run.font.bold=kind=='heading'
            run.font.underline=False
            run.font.color.rgb=RGBColor(35,46,54)
            if kind=='heading': run.font.color.rgb=RGBColor(31,82,110)
            elif kind=='small': run.font.color.rgb=RGBColor(85,99,108)
    tf.vertical_anchor=MSO_ANCHOR.TOP

def set_links(shape, lines, font_size=10):
    tf=shape.text_frame; tf.clear(); tf.word_wrap=True; tf.auto_size=MSO_AUTO_SIZE.NONE
    for i,parts in enumerate(lines):
        p=tf.paragraphs[0] if i==0 else tf.add_paragraph(); clear_bullet(p); p.space_after=Pt(3)
        for label,url in parts:
            r=p.add_run(); r.text=label; r.font.name='Arial'; r.font.size=Pt(font_size); r.font.color.rgb=RGBColor(34,93,144); r.font.underline=True
            if url: r.hyperlink.address=url

def add_notes(slide, text):
    try:
        ns=slide.notes_slide.notes_text_frame
        ns.text=text
    except Exception:
        pass

def add_step(slide, x, y, w, title, subtitle, color):
    sh=slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(0.96))
    sh.fill.solid(); sh.fill.fore_color.rgb=RGBColor(*color)
    sh.line.color.rgb=RGBColor(*color)
    tf=sh.text_frame; tf.clear(); tf.vertical_anchor=MSO_ANCHOR.MIDDLE
    tf.margin_left=Inches(.12); tf.margin_right=Inches(.12); tf.margin_top=Inches(.04); tf.margin_bottom=Inches(.04)
    p=tf.paragraphs[0]; p.text=title; p.alignment=PP_ALIGN.CENTER
    for r in p.runs: r.font.name='Arial'; r.font.size=Pt(15); r.font.bold=True; r.font.color.rgb=RGBColor(255,255,255)
    p=tf.add_paragraph(); p.text=subtitle; p.alignment=PP_ALIGN.CENTER; p.space_before=Pt(2)
    for r in p.runs: r.font.name='Arial'; r.font.size=Pt(10); r.font.color.rgb=RGBColor(255,255,255)
    return sh

# Two-page brief: write concise text into the existing template content boxes.
brief=Presentation(SRC/'Two-page Solution Brief Template.pptx')
brief.slides[0].shapes[0].text='Zeros and Ones | Sahara'
brief.slides[0].shapes[1].text='SAHARA'
for r in brief.slides[0].shapes[1].text_frame.paragraphs[0].runs:
    r.font.name='Arial'; r.font.size=Pt(28); r.font.bold=True; r.font.color.rgb=RGBColor(21,93,102)
set_paragraph(brief.slides[0].shapes[3],[
 ('About the team / organisation','heading'),
 ('Team Zeros and Ones, building with WeDevit. Dr Sharada Vinod Kutty and Dr Sujay Halur Shankar provide clinical input. Shaik Abdul Basith leads product engineering; Shaik Muhammad Awaiz builds Android and web; Shirin Ayub has contributed to symptom-tracker discussion. Formal roles, doctor specialities and team code need team confirmation.','body'),
 ('Problem','heading'),
 ('Between visits, patients and families need a clear way to record symptoms and find the goals-of-care note a clinician reviewed. A Kerala survey found routine patient pain-score documentation at 21% of government palliative sites.','body'),
],body_pt=10.5,margin=.06)
set_paragraph(brief.slides[0].shapes[5],[
 ('Solution','heading'),
 ('Sahara is a patient-and-family companion for palliative care. It keeps symptom check-ins and care information together, and supports a clinician-reviewed goals-of-care note that the patient or family can confirm by version.','body'),
 ('Technology','heading'),
 ('Android (Kotlin), web (Next.js), Supabase Auth/Postgres/Edge Functions, and Vercel. Goals-of-care prompts currently use local word matching. AI note drafting is off; Sahara is not connected to CARE or a hospital system.','body'),
 ('Core features','heading'),
 ('- Symptom diary with dated 0–10 ratings and a tappable pain-area body map\n• Patient/family care records, medicine list and reminders\n• Clinician-reviewed, versioned goals-of-care notes\n• Patient/family confirmation of the exact note version\n• Nearby palliative-care directory and emergency QR card','body'),
],body_pt=10.5,margin=.06)
brief.slides[1].shapes[4].text='Zeros and Ones | Sahara'
set_paragraph(brief.slides[1].shapes[1],[
 ('Build sprint progress','heading'),
 ('Built the Android patient/family workflows, symptom diary and pain-area map, versioned goals-of-care notes, and the hospital survey. The survey was updated from clinician feedback and deployed on 27 Oct. The product has grown from a diary/directory into a shared care workflow; CARE integration remains a proposal.','body'),
 ('Validation','heading'),
 ('Code-level and synthetic backend checks are recorded in the repository. The hospital survey has been exercised in real browser flows and deployed. There is no prospective patient pilot, AI performance result or measured clinical outcome yet.','body'),
],body_pt=10.5,margin=.06)
set_paragraph(brief.slides[1].shapes[2],[
 ('Safety and responsible AI','heading'),
 ('A clinician reviews a goals-of-care note before publishing; Sahara does not diagnose or recommend treatment. AI drafting is off. Exact-version confirmation is not legal consent. Patient consent, data-fiduciary ownership and grievance contact need decisions before a pilot. Use synthetic people in demos.','body'),
 ('Deployment and sustainability','heading'),
 ('Pilot only with a named care partner, approved consent/privacy process, security review and staff training. No fee to patients is proposed. One-time and ongoing costs are not yet estimated; hosting, storage, support and any future AI use must be costed with the selected partner. Institutional or public funding is an option to explore, not a commitment.','body'),
 ('Supporting links','heading'),
 ('MVP: https://play.google.com/store/apps/details?id=in.wedevit.sahara\nSurvey: https://sahara.wedevit.in/hospital-survey\nCode: https://github.com/basithladdu/healthathon (private access)\nNarrated video: not recorded yet; link to add after recording.','small'),
],body_pt=10.5,margin=.06)
# Use short, genuinely clickable labels instead of unlinked raw URLs.
support_tf=brief.slides[1].shapes[2].text_frame
old=support_tf.paragraphs[-1]
old._element.getparent().remove(old._element)
for label,url in [
    ('Public app listing','https://play.google.com/store/apps/details?id=in.wedevit.sahara'),
    ('Hospital survey','https://sahara.wedevit.in/hospital-survey'),
    ('Code (private repository)','https://github.com/basithladdu/healthathon'),
    ('Narrated demo video: pending',None),
]:
    p=support_tf.add_paragraph(); clear_bullet(p); p.space_after=Pt(2)
    r=p.add_run(); r.text=label; r.font.name='Arial'; r.font.size=Pt(8.5)
    r.font.color.rgb=RGBColor(34,93,144) if url else RGBColor(85,99,108)
    r.font.underline=bool(url)
    if url: r.hyperlink.address=url
add_notes(brief.slides[0], 'Source: Healthathon template and current Sahara handoff. Problem context: Lijimol et al., Indian Journal of Palliative Care (2020), PMID 33623312, as cited in docs/pitch/KPI_EVALUATION_DRAFT.md. The 21% is a published Kerala site-level statistic, not a Sahara baseline or outcome. AI drafting is currently off; local word matching is not an AI model.')
add_notes(brief.slides[1], 'Validation, deployment state and feature list reflect repository handoff dated 27 Oct 2026. No pilot outcome is claimed. Costs, legal ownership, consent and CARE option remain open team decisions. Public app URL and survey URL are included; evaluator sign-in and video link still require private preparation.')
for pr in brief.slides:
    for sh in pr.shapes:
        if getattr(sh,'has_text_frame',False) and '\ufffd' in sh.text: sh.text=sh.text.replace('\ufffd','-')
brief.save(OUT/'Sahara-Two-Page-Solution-Brief-working-draft.pptx')

# Final 12-slide deck: retain original cover, section titles, footers and layout.
ppt=Presentation(SRC/'PPT_Final_Teamplate_Health-a-thon 2026.pptx')
def body(i, blocks, size=17): set_paragraph(ppt.slides[i-1].shapes[3],blocks,body_pt=size,margin=.02,font_name='Calibri')
# cover
ppt.slides[0].shapes[3].text='Sahara: Palliative Companion'
ppt.slides[0].shapes[4].text='A patient-and-family companion for palliative care between visits'
ppt.slides[0].shapes[5].text='TEAM NAME  Zeros and Ones'
ppt.slides[0].shapes[6].text='TEAM CODE  PENDING TEAM INPUT'
for sh,sz,bold in [(ppt.slides[0].shapes[3],34,True),(ppt.slides[0].shapes[4],17,False),(ppt.slides[0].shapes[5],12,False),(ppt.slides[0].shapes[6],12,False),(ppt.slides[0].shapes[7],8,False)]:
    sh.text_frame.auto_size=MSO_AUTO_SIZE.NONE
    for pa in sh.text_frame.paragraphs:
        clear_bullet(pa)
        for r in pa.runs:
            r.font.name='Arial'; r.font.size=Pt(sz); r.font.bold=bold; r.font.color.rgb=RGBColor(26,48,62)
ppt.slides[0].shapes[7].text='HEALTH-A-THON 2026 - FINAL SUBMISSION - WORKING DRAFT'
for r in ppt.slides[0].shapes[7].text_frame.paragraphs[0].runs: r.font.name='Arial'; r.font.size=Pt(8); r.font.color.rgb=RGBColor(36,51,64)

body(2,[
 ('Team Zeros and Ones · WeDevit','heading'),
 ('Dr Sharada Vinod Kutty and Dr Sujay Halur Shankar — clinical partners; specialties and approved biographies to confirm. Shaik Abdul Basith — product/engineering lead. Shaik Muhammad Awaiz — Android and web engineering. Shirin Ayub — symptom-tracker input; title and role to confirm. Team code: pending.','body'),
 ('The team builds Sahara with clinician input. Any public-facing doctor profile or video wording needs each doctor’s approval.','small')],17)
body(3,[
 ('Between visits, families need to keep symptoms and the patient’s care preferences easy to find.','heading'),
 ('A Kerala survey reported routine patient pain-score documentation at 21% of government palliative sites. Families also need the latest clinician-reviewed goals-of-care note when care is discussed across visits.','body'),
 ('Opportunity: make the patient’s own updates visible to their care team without replacing clinical judgement or the hospital record.','body')],18)
body(4,[
 ('People: patients, family caregivers and palliative-care clinicians.','heading'),
 ('Setting: home and clinic between scheduled visits. The family records symptoms; a clinician reviews and publishes a goals-of-care note; the patient or family can confirm that exact version.','body'),
 ('Need: a short, accessible record of what changed and what the patient wants, ready for the next conversation.','body')],18)
ppt.slides[4].shapes[1].text='Solution Overview, Key Features & Impact'
for r in ppt.slides[4].shapes[1].text_frame.paragraphs[0].runs: r.font.name='Arial'; r.font.size=Pt(27); r.font.bold=True; r.font.color.rgb=RGBColor(37,81,180)
body(5,[
 ('Sahara gives patients and families a simple place to record symptoms and care information, while supporting a clinician-reviewed goals-of-care note.','body'),
 ('Core features: symptom diary with dates and 0–10 ratings; tappable pain-area body map; care records and medicine reminders; versioned clinician-reviewed notes; exact-version confirmation; nearby directory and emergency QR.','small')],17)
# Add a restrained 4-step flow using the template's blue/green palette.
for idx,(x,title,sub,c) in enumerate([(0.7,'Record','Patient / family update',(31,105,144)),(3.8,'Review','Clinician checks note',(48,126,123)),(6.9,'Publish','A fixed note version',(68,137,98)),(10.0,'Confirm','Patient / family',(45,94,124))]):
    add_step(ppt.slides[4],x,4.45,2.45,title,sub,c)
for x in (3.28,6.38,9.48):
    arrow=ppt.slides[4].shapes.add_shape(MSO_SHAPE.CHEVRON, Inches(x), Inches(4.76), Inches(.4), Inches(.28))
    arrow.fill.solid(); arrow.fill.fore_color.rgb=RGBColor(116,132,141); arrow.line.color.rgb=RGBColor(116,132,141)
ppt.slides[5].shapes[1].text='Technology, Tools and AI Approach'
for r in ppt.slides[5].shapes[1].text_frame.paragraphs[0].runs: r.font.name='Arial'; r.font.size=Pt(27); r.font.bold=True; r.font.color.rgb=RGBColor(37,81,180)
ppt.slides[5].shapes[4].text=''
body(6,[
 ('Android app (Kotlin) + web app (Next.js)','heading'),
 ('Supabase provides sign-in, database records and server functions. Vercel hosts the web app. The current goals-of-care helper uses local word matching; AI drafting and hosted transcription are not enabled.','body'),
 ('CARE connection: proposed only. No hospital-system integration is live.','small')],17)
# architecture flow diagram, labels show existing vs proposed.
for x,title,sub,c in [(0.9,'Patient / family','Android app',(31,105,144)),(4.0,'Sahara services','Supabase + web',(48,126,123)),(7.1,'Clinician','Review + publish',(68,137,98)),(10.0,'CARE','Possible future link',(114,125,135))]:
    add_step(ppt.slides[5],x,4.35,2.4,title,sub,c)
for x in (3.47,6.57,9.57):
    a=ppt.slides[5].shapes.add_shape(MSO_SHAPE.CHEVRON, Inches(x), Inches(4.68), Inches(.4), Inches(.28))
    a.fill.solid(); a.fill.fore_color.rgb=RGBColor(116,132,141); a.line.color.rgb=RGBColor(116,132,141)
# Label CARE link as proposed, not current integration.
ppt.slides[6].shapes[4].text='Current status: features are in the app source. The pilot, clinical validation, AI drafting and CARE connection remain incomplete.'
for r in ppt.slides[6].shapes[4].text_frame.paragraphs[0].runs: r.font.name='Calibri'; r.font.size=Pt(12); r.font.color.rgb=RGBColor(85,99,108)
body(7,[
 ('Sprint outputs','heading'),
 ('- Patient/family Android workflows, symptom diary and body map\n• Versioned clinician-reviewed care note and family confirmation\n• Nearby directory and emergency QR card\n• Hospital survey refined from clinician feedback; deployed 27 Oct','body'),
 ('Change in direction: from a standalone diary/directory toward a patient-facing companion to clinician-led palliative care. CARE integration, pilot and AI drafting remain incomplete.','small')],16)
ppt.slides[7].shapes[4].text='No pilot or AI benchmark yet. Metrics and baselines need team approval.'
for r in ppt.slides[7].shapes[4].text_frame.paragraphs[0].runs: r.font.name='Calibri'; r.font.size=Pt(12); r.font.color.rgb=RGBColor(85,99,108)
body(8,[
 ('What has been checked','heading'),
 ('- Automated Android and web checks recorded in the repository\n• Hospital survey tested in browser flows, including incomplete answers and retry; production deployment recorded 27 Oct\n• No prospective patient pilot, AI benchmark or measured health outcome yet','body'),
 ('Proposed measures (team to approve): exact-version notes confirmed; patient/family symptom ratings between visits; correct latest-note retrieval time in a supervised drill. Baselines and targets are not yet set.','small')],16)
body(9,[
 ('Human review stays in the loop. A clinician checks the goals-of-care note before publishing. The app does not diagnose or recommend treatment.','body'),
 ('AI drafting is off. Do not send real patient data to an unapproved model. Exact-version confirmation is not patient consent or a legal signature. Consent flow, data owner, grievance contact, retention and pilot ethics still need approval.','body'),
 ('Use synthetic names in demos. CARE/hospital integration is not live.','small')],17)
body(10,[
 ('Pilot requirements','heading'),
 ('Choose a CARE scope and partner; agree consent, privacy, security, clinician training and workflow; then test with a small approved group.','body'),
 ('Costs and sustainability','heading'),
 ('No patient fee is proposed. Hosting, storage, support, staff time and any future AI costs have not been estimated. Institutional or public funding could support access, but no payer is committed. Cost only after partner and deployment scope are chosen.','body'),
 ('Integration','heading'),
 ('CARE integration is a proposal, not a current capability.','small')],16)
body(11,[
 ('Next','heading'),
 ('1. Confirm CARE option (A, A+B or A+C) with the team.\n2. Finish consent, data ownership, pilot site, team code, clinician profiles and costs.\n3. Complete the real-phone end-to-end walkthrough and narrated video.\n4. Confirm deadline and submission portal with the organiser.','body'),
 ('Public sharing of solution information: YES. Exclude private records, test credentials and unapproved clinician information.','small')],16)
ppt.slides[11].shapes[1].text='Thank you'
ppt.slides[11].shapes[2].text='Zeros and Ones - Team code pending'
ppt.slides[11].shapes[3].text='MVP: Google Play | Survey: Sahara website\nContact: workwithdevit@gmail.com | Demo video: pending | Code: private repository'
ppt.slides[11].shapes[4].text='Health-a-thon 2026'
ppt.slides[11].shapes[5].text='HEALTH-A-THON 2026 - WORKING DRAFT - team approvals and video still needed'
set_links(ppt.slides[11].shapes[3], [[('MVP: Google Play','https://play.google.com/store/apps/details?id=in.wedevit.sahara'),('  |  Survey: Sahara website','https://sahara.wedevit.in/hospital-survey')],[('Contact: workwithdevit@gmail.com','mailto:workwithdevit@gmail.com'),('  |  Demo video: pending',None),('  |  Code: private repository','https://github.com/basithladdu/healthathon')]],font_size=10)
for sh,sz,bold in [(ppt.slides[11].shapes[1],38,False),(ppt.slides[11].shapes[2],16,False),(ppt.slides[11].shapes[3],10,False),(ppt.slides[11].shapes[4],12,False),(ppt.slides[11].shapes[5],8,False)]:
    sh.text_frame.auto_size=MSO_AUTO_SIZE.NONE
    if sh==ppt.slides[11].shapes[3]: sh.height=Inches(.7)
    for pa in sh.text_frame.paragraphs:
        clear_bullet(pa)
        for r in pa.runs: r.font.name='Arial'; r.font.size=Pt(sz); r.font.bold=bold; r.font.color.rgb=RGBColor(45,60,70)

notes={
3:'Evidence source: Lijimol et al., Indian Journal of Palliative Care, 2020, PMID 33623312; 21% is reported for routine patient pain-score documentation at surveyed Kerala government palliative sites. It is not a Sahara baseline or outcome. See docs/pitch/KPI_EVALUATION_DRAFT.md.',
5:'Feature status based on current Sahara handoff and Android source. Body map is tappable, but only 12 body regions are currently represented. Exact-version family confirmation is not legal consent.',
6:'Current app stack from repository. CARE integration is not built. Local matching is rules/word matching, not an AI model; hosted AI remains disabled.',
7:'Milestones/features reflect repository checkpoints through 27 Oct 2026; review the current branch and releases before final submission. CARE, live AI and prospective pilot are not complete.',
8:'No prospective patient evaluation or model performance results exist. Proposed metrics only: published/confirmed goals-of-care note, patient/family symptom entry between visits, retrieval accuracy/time. See docs/pitch/KPI_EVALUATION_DRAFT.md; team approval and baseline required.',
9:'Consent, data fiduciary, grievance contact, retention, AI provider terms and pilot ethics remain open. No patient data should be used in demo.',
10:'No dollar/rupee cost figures are available in source records. Do not invent; obtain project-specific hosting, storage, staff and provider quotes after scope is selected.',
11:'Public-sharing answer Yes is recorded in everyday.md. It excludes credentials, private records and unrestricted access to the private repository.'
}
for n,t in notes.items(): add_notes(ppt.slides[n-1],t)
for pr in ppt.slides:
    for sh in pr.shapes:
        if getattr(sh,'has_text_frame',False) and '\ufffd' in sh.text: sh.text=sh.text.replace('\ufffd','-')
ppt.save(OUT/'Sahara-Healthathon-Final-Presentation-working-draft.pptx')

# Machine-readable text review and evidence ledger for QA, kept beside the actual drafts.
with (OUT/'content-review.txt').open('w',encoding='utf-8') as f:
    f.write('Health-a-thon 2026 — Sahara working drafts\nGenerated 2026-10-28 from the two original organiser templates.\n')
    f.write('Unresolved before submission: team code; clinician bios/specialties and approvals; Shirin title; CARE scope; KPI selection/baselines/targets; pilot partner; cost estimate; legal/data owner and consent; private judge access; narrated video; confirmed deadline and portal.\n')
    f.write('Evidence boundary: no pilot outcomes or AI benchmark are claimed; AI drafting is off; CARE integration is not live.\n')
print('Wrote',OUT)
for p in OUT.iterdir(): print(p.name,p.stat().st_size)
