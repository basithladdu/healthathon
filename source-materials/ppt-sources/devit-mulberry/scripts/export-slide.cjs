// ToFEI PoC Pilot Briefing — PPTX export
// Run: node scripts/export-slide.cjs
const PptxGenJS = require('pptxgenjs');
const path = require('path');

const pptx = new PptxGenJS();
pptx.layout = 'LAYOUT_WIDE'; // 13.33" x 7.5"

const slide = pptx.addSlide();

const C = {
  navy:    '0F172A', teal:    '0D9488', tealL:  'CCFBF1',
  amber:   'B45309', amberBg: 'FFF7ED', orange:  'EA580C',
  red:     'DC2626', slate:   '475569', slateL:  'F1F5F9',
  border:  'CBD5E1', devitBg: 'DBEAFE', devitFg: '1D4ED8',
  deptBg:  'DCFCE7', deptFg:  '166534', mileBg:  'FEF9C3',
  mileFg:  '854D0E', white:   'FFFFFF', dark:    '0F172A',
};

// ── Background ─────────────────────────────────────────────────
slide.addShape(pptx.ShapeType.rect, { x:0, y:0, w:'100%', h:'100%', fill:{color:'F8FAFC'}, line:{color:'F8FAFC'} });

// ── Header ─────────────────────────────────────────────────────
slide.addShape(pptx.ShapeType.rect, { x:0, y:0, w:'100%', h:0.72, fill:{color:C.navy}, line:{color:C.navy} });
slide.addText('ToFEI — Tobacco-Free Educational Institutions  |  AI Safety Audit App', {
  x:0.3, y:0.04, w:9, h:0.24, fontSize:7.5, color:'94A3B8' });
slide.addText('Project Scope & Pilot Briefing', {
  x:0.3, y:0.28, w:8, h:0.38, fontSize:17, color:C.white, bold:true });
slide.addText('WEDEVIT PRIVATE LIMITED  |  DEVIT', {
  x:9.8, y:0.24, w:3.2, h:0.24, fontSize:7.5, color:'94A3B8', align:'right' });

// teal accent bar
slide.addShape(pptx.ShapeType.rect, { x:0, y:0.72, w:'100%', h:0.04, fill:{color:C.teal}, line:{color:C.teal} });

// ═══════════════════════════════════════════════════════════════
// LEFT COLUMN  x=0.22, w=5.55
// ═══════════════════════════════════════════════════════════════
const LX=0.22, LW=5.55;

// ── Section header ─────────────────────────────────────────────
slide.addText('DEVIT & DEPARTMENT CORRESPONDENCE', {
  x:LX, y:0.88, w:LW, h:0.2, fontSize:7.5, color:C.teal, bold:true });

const chatItems = [
  { date:'JUNE 18 (EMAIL)', who:'SPO (NHM-AP)', side:'dept',
    text:'Requested DEVIT to conduct a demo and provided access to the 10 schools in the annexure list.' },
  { date:'JUNE 19 (EMAIL)', who:'DEVIT', side:'devit',
    text:'Confirmed receipt of 10 pilot school list; requested RTGS team to release hackathon prize money.' },
  { date:'JUNE 20', who:'DEVIT', side:'devit',
    text:'Asked for a 15-min demo & requested Headmaster names/numbers for the 10 pilot schools.' },
  { date:'', who:'Dept', side:'dept',
    text:'Holiday today. Meeting time will be informed soon as per SPO mam convenience.' },
  { date:'JUNE 22', who:'Dept', side:'dept', text:'Meet at 2.30pm today. Please share link.' },
  { date:'', who:'DEVIT', side:'devit', text:'Shared Google Meet (nsf-hder-foa). Conducted Demo.' },
  { date:'', who:'DEVIT', side:'devit', text:'Revamped the app based on suggestions received from the department.' },
  { date:'JUNE 23', who:'Milestone', side:'mile', text:'Hackathon prize money successfully received by DEVIT.' },
  { date:'JUNE 29 – JULY 7', who:'DEVIT', side:'devit', text:'Nodal officer contacts? Can we sign PoC MoU?' },
  { date:'', who:'Dept', side:'dept', text:'File is in process basith... communication will be given asap.' },
];

let cy = 1.1;
const BH = 0.22;

chatItems.forEach(item => {
  if (item.date) {
    slide.addText(item.date, { x:LX, y:cy, w:LW, h:0.15, fontSize:5.8, color:C.slate, bold:true });
    cy += 0.16;
  }
  const bg   = item.side==='devit' ? C.devitBg : item.side==='mile' ? C.mileBg : C.deptBg;
  const fg   = item.side==='devit' ? C.devitFg : item.side==='mile' ? C.mileFg : C.deptFg;
  const bar  = item.side==='devit' ? '3B82F6'  : item.side==='mile' ? 'EAB308'  : '22C55E';
  slide.addShape(pptx.ShapeType.rect, { x:LX,       y:cy, w:LW,   h:BH, fill:{color:bg}, line:{color:bg} });
  slide.addShape(pptx.ShapeType.rect, { x:LX,       y:cy, w:0.04, h:BH, fill:{color:bar}, line:{color:bar} });
  slide.addText([
    { text:item.who+': ', options:{bold:true, color:fg, fontSize:6.5} },
    { text:item.text,     options:{color:fg,  fontSize:6.5} },
  ], { x:LX+0.07, y:cy, w:LW-0.07, h:BH, valign:'middle' });
  cy += BH + 0.01;
});

// ── KPI boxes ──────────────────────────────────────────────────
cy += 0.1;
slide.addText('STATE-WIDE SCHOOL REGISTRIES', { x:LX, y:cy, w:LW, h:0.2, fontSize:7.5, color:C.teal, bold:true });
cy += 0.22;
const kW=(LW-0.1)/2;
// box 1
slide.addShape(pptx.ShapeType.rect, { x:LX, y:cy, w:kW, h:0.52, fill:{color:C.slateL}, line:{color:C.border} });
slide.addText('45,000',               { x:LX, y:cy+0.02, w:kW, h:0.28, fontSize:22, bold:true, color:C.dark, align:'center' });
slide.addText('GOV. SCHOOLS (STATEWIDE)', { x:LX, y:cy+0.3, w:kW, h:0.18, fontSize:5.8, bold:true, color:C.slate, align:'center' });
// box 2
const k2=LX+kW+0.1;
slide.addShape(pptx.ShapeType.rect, { x:k2, y:cy, w:kW, h:0.52, fill:{color:C.slateL}, line:{color:C.border} });
slide.addText('approx 70,000',          { x:k2, y:cy+0.02, w:kW, h:0.28, fontSize:16, bold:true, color:C.orange, align:'center' });
slide.addText('PRIVATE SCHOOLS (STATEWIDE)', { x:k2, y:cy+0.3, w:kW, h:0.18, fontSize:5.8, bold:true, color:C.slate, align:'center' });
cy += 0.62;

// ── Pending PoC Timeline ───────────────────────────────────────
const boxH = 1.38;
slide.addShape(pptx.ShapeType.rect, { x:LX, y:cy, w:LW, h:boxH, fill:{color:C.amberBg}, line:{color:C.orange, pt:1.5} });

slide.addText('⚠  PoC PENDING — AWAITING DEPARTMENT ACTION', {
  x:LX+0.1, y:cy+0.07, w:LW-0.2, h:0.22, fontSize:8, bold:true, color:C.amber });
slide.addShape(pptx.ShapeType.rect, {
  x:LX+0.08, y:cy+0.29, w:LW-0.16, h:0.015, fill:{color:'FDBA74'}, line:{color:'FDBA74'} });

const steps = [
  { icon:'⏳', text:'Dept releases school contacts, runs file & obtains district authority permissions' },
  { icon:'→',  text:'DEVIT reaches out, distributes logins & conducts PoC (field visit or online)' },
  { icon:'📄', text:'MoU requested between DEVIT & dept  +  demo video shared simultaneously' },
  { icon:'🎯', text:'Expected PoC completion: end of July 2026  →  State-wide rollout (subject to dept approval)', bold:true },
];

let sy = cy+0.32;
steps.forEach((s,i) => {
  const dotColor = i===3 ? 'B45309' : C.red;
  const bdColor  = i===3 ? 'FDE68A' : 'FCA5A5';
  slide.addShape(pptx.ShapeType.ellipse, { x:LX+0.1, y:sy, w:0.22, h:0.22, fill:{color:dotColor}, line:{color:bdColor} });
  slide.addText(s.icon, { x:LX+0.1, y:sy, w:0.22, h:0.22, fontSize:7, align:'center', valign:'middle' });
  // connector (not last)
  if (i<3) slide.addShape(pptx.ShapeType.rect, { x:LX+0.2, y:sy+0.22, w:0.02, h:0.04, fill:{color:'FDBA74'}, line:{color:'FDBA74'} });
  slide.addText(s.text, {
    x:LX+0.38, y:sy, w:LW-0.48, h:0.25,
    fontSize:6.8, color: s.bold ? '78350F' : '7C2D12', bold:!!s.bold, italic:!!s.bold, valign:'middle'
  });
  sy += 0.26;
});

// ═══════════════════════════════════════════════════════════════
// RIGHT COLUMN  x=6.1, w=7.0
// ═══════════════════════════════════════════════════════════════
const RX=6.1, RW=7.0;

// vertical divider
slide.addShape(pptx.ShapeType.rect, { x:RX-0.07, y:0.78, w:0.015, h:6.55, fill:{color:C.border}, line:{color:C.border} });

slide.addText('PILOT SCHOOLS REGISTRY — 10 SELECTED TARGETS', {
  x:RX, y:0.88, w:RW, h:0.2, fontSize:7.5, color:C.teal, bold:true });

// Table
const cols  = [0.38, 1.22, 2.75, 1.22, 1.1];
const hdrs  = ['S.No','UDISE Code','School Name','District','Mandal'];
let tx = RX;
const thY = 1.12;
cols.forEach((w,i) => {
  slide.addShape(pptx.ShapeType.rect, { x:tx, y:thY, w, h:0.3, fill:{color:C.slateL}, line:{color:C.border} });
  slide.addText(hdrs[i], { x:tx+0.04, y:thY, w:w-0.04, h:0.3, fontSize:6.8, bold:true, color:C.navy, valign:'middle' });
  tx += w;
});

const schools = [
  [1,'28222591301','BHARATHI MPL.PRI.SCH','ANANTHAPUR','Anantapur (M)'],
  [2,'28221300609','ZPHS KALLUR R.S','ANANTHAPUR','Kallur'],
  [3,'28222700510','ZPHS GUGUDU','ANANTHAPUR','Gugudu'],
  [4,'28174100881','MPPS PKPALEM','GUNTUR','Kondayapalem'],
  [5,'28174501403','MPPS CC MURIKIPUDI','GUNTUR','Murikipudi'],
  [6,'28204800301','MPPS T SAKIBANDA','KADAPA','Tsakibanda'],
  [7,'28204301106','MPPS NVSG COLONY','KADAPA','Upparapalle'],
  [8,'28161000703','RCM PRI.SCH. AIDED','KRISHNA','Chandragudem'],
  [9,'28161400933','APTWRSGVISSANNAPET','KRISHNA','Vissannapet'],
  [10,'28210700503','MPPS R ULCHALA','KURNOOL','Ulchala'],
];

const ROW_H = 0.55;
schools.forEach((row,ri) => {
  const rowY = thY + 0.3 + ri*ROW_H;
  tx = RX;
  cols.forEach((w,ci) => {
    slide.addShape(pptx.ShapeType.rect, {
      x:tx, y:rowY, w, h:ROW_H,
      fill:{color: ri%2===0 ? C.white : 'F8FAFC'}, line:{color:C.border}
    });
    slide.addText(String(row[ci]), {
      x:tx+0.05, y:rowY, w:w-0.05, h:ROW_H,
      fontSize: ci===0 ? 11 : ci===1 ? 7.2 : 8.5,
      bold: ci===0||ci===2, color: ci===1 ? '334155' : C.navy,
      align: ci===0 ? 'center' : 'left', valign:'middle',
      fontFace: ci===1 ? 'Courier New' : 'Calibri',
    });
    tx += w;
  });
});

// ── Footer ─────────────────────────────────────────────────────
slide.addShape(pptx.ShapeType.rect, { x:0, y:7.3, w:'100%', h:0.2, fill:{color:C.slateL}, line:{color:C.border} });
slide.addText('Prepared by: WEDEVIT PRIVATE LIMITED', { x:0.3, y:7.32, w:6, h:0.16, fontSize:7, color:C.slate });
slide.addText('Confidential  ·  ToFEI AP PoC Pilot Briefing  ·  July 2026', { x:7.2, y:7.32, w:5.8, h:0.16, fontSize:7, color:C.slate, align:'right' });

// ── Save ───────────────────────────────────────────────────────
const outPath = path.join(__dirname, '..', 'ToFEI_PoC_Pilot_Briefing.pptx');
pptx.writeFile({ fileName: outPath })
  .then(() => console.log('✅  Saved: ' + outPath))
  .catch(e => { console.error('❌  Error:', e); process.exit(1); });
