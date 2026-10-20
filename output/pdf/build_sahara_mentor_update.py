from pathlib import Path
import re
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from pypdf import PdfReader

OUT = Path(__file__).resolve().parent
OUT.mkdir(parents=True, exist_ok=True)
PDF = OUT / 'Sahara_Mentor_Update_2026-10-20.pdf'
pdfmetrics.registerFont(TTFont('Arial', r'C:\Windows\Fonts\arial.ttf'))
pdfmetrics.registerFont(TTFont('ArialBold', r'C:\Windows\Fonts\arialbd.ttf'))
pdfmetrics.registerFont(TTFont('ArialItalic', r'C:\Windows\Fonts\ariali.ttf'))
pdfmetrics.registerFontFamily('Arial', normal='Arial', bold='ArialBold', italic='ArialItalic', boldItalic='ArialBold')
W,H = A4
CREAM,INK,RUST,SAGE,MUTED,LINE = map(HexColor, ['#FAF6EF','#2D302C','#A54D35','#E6EBDF','#5E625A','#D7CDBC'])
c = canvas.Canvas(str(PDF), pagesize=A4)
c.setTitle('Sahara | Mentor briefing | 20 October 2026')
c.setAuthor('Zeros and Ones / WeDevit')
c.setSubject('Palliative care continuity, delivered workflows and mentor decisions')
c.setFillColor(CREAM); c.rect(0,0,W,H,fill=1,stroke=0)
c.setFillColor(RUST); c.rect(0,H-10,W,10,fill=1,stroke=0)

styles = {
 'body': ParagraphStyle('body',fontName='Arial',fontSize=10.5,leading=14.5,textColor=INK,spaceAfter=5),
 'small': ParagraphStyle('small',fontName='Arial',fontSize=10,leading=13.5,textColor=MUTED),
 'section': ParagraphStyle('section',fontName='ArialBold',fontSize=11.5,leading=15,textColor=RUST),
}
def para(text,x,y,w,kind='body'):
 p=Paragraph(text,styles[kind]); _,height=p.wrap(w,H)
 p.drawOn(c,x,y-height)
 return y-height
def section(label,y):
 return para(label,38,y,W-76,'section')-8
def rule(y):
 c.setStrokeColor(LINE); c.setLineWidth(.6); c.line(38,y,W-38,y)
 return y-16

y=H-42
c.setFillColor(RUST); c.setFont('ArialBold',32); c.drawString(38,y-26,'Sahara')
c.setFillColor(MUTED); c.setFont('Arial',10); c.drawRightString(W-38,y-3,'MENTOR BRIEFING')
c.drawRightString(W-38,y-18,'20 October 2026')
y-=44
y=para('A Palliative Care Companion',38,y,W-76,'section')-10
y=para('Help patients, families and clinicians keep one clear, source-linked record of what matters to the patient.',38,y,W-76)-13
y=rule(y)

y=section('The core journey',y)
steps=[('01','Listen','Record a consented goals-of-care conversation.'),('02','Review','Check the transcript, original evidence and copied facts.'),('03','Confirm','A clinician reviews the exact note version; the patient confirms it.'),('04','Share','Share by choice, with recipient permissions, expiry and revocation.')]
for no,title,body in steps:
 c.setFillColor(RUST); c.setFont('ArialBold',10); c.drawString(38,y-10,no)
 y=para(f'<b>{title}.</b> {body}',65,y,W-103)-5
y=rule(y-1)

y=section('What we have built',y)
features=[
 '<b>Connected care records:</b> shared web/native roles, clinician review, patient/family confirmations, protected exact-version QR retrieval and assigned tasks.',
 '<b>Daily care on Android:</b> medicines, visits, tasks, check-ins and reminders. Report originals stay available beside reviewed OCR and page/line-linked facts.',
 '<b>National directory:</b> 1,631 public listings across all 36 states/UTs, with search, state filters, call/directions and source dates. These are directory entries, not verified facilities or live medicine stock.',
 '<b>Latest Android source:</b> visible controls replace dropdowns/expanders; Latin + Devanagari OCR and on-device Hindi/English translation are added. Qwen3-ASR offline speech is included; Hindi/mixed-language phone validation remains pending.'
]
for text in features: y=para(text,38,y,W-76)-5
y=rule(y-1)

y=section('Where we are now',y)
y=para('The shared-care website is deployed. The updated Android test APK is built; 147 unit checks passed. Phone checks remain pending. Previous web/database checks passed: 284 web tests and 65 database checks.',38,y,W-76)-5
y=para('Transcripts and translations require human review. Original report/audio files remain on the phone; shared notes do not synchronize those files. Generative clinical drafting and the promised Sarvam integration are not enabled. A clinician/family pilot is still to be arranged.',38,y,W-76,'small')-14

c.setFillColor(SAGE); c.roundRect(28,y-96,W-56,105,9,fill=1,stroke=0)
y=section('What we need from our mentor',y-2)
y=para('<b>1.</b> Validate minimum goals-of-care content and clinician/patient consent roles.<br/><b>2.</b> Help shape a proposed 3-5 case clinician/family pilot and facility-data validation.<br/><b>3.</b> Decide whether CARE integration is needed, and the first useful exchange.',38,y,W-76)-12

footer_y=42
c.setStrokeColor(LINE); c.line(38,footer_y+24,W-38,footer_y+24)
para('<a href="https://sahara.wedevit.in/shared-care" color="#A54D35"><b>Open shared care</b></a>  |  <a href="https://sahara.wedevit.in/hospital-survery" color="#A54D35"><b>Hospital survey (web only)</b></a>',38,footer_y+13,W-76,'small')
c.setFillColor(MUTED); c.setFont('Arial',9); c.drawRightString(W-38,20,'Zeros and Ones / WeDevit')
if y < footer_y+30:
 raise RuntimeError(f'Footer collision: content ended at {y:.1f}')
c.save()
reader=PdfReader(str(PDF)); text='\n'.join(p.extract_text() or '' for p in reader.pages)
(OUT/'Sahara_Mentor_Update_2026-10-20.txt').write_text(text,encoding='utf-8')
assert len(reader.pages)==1
assert len(re.findall(r'\S+',text))<=420
assert len(reader.pages[0].get('/Annots',[]))==2
print({'pdf':str(PDF),'pages':len(reader.pages),'words':len(re.findall(r'\S+',text)),'content_bottom':round(y,1),'links':2})
