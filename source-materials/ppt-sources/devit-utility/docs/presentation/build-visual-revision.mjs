import { createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Run with the bundled Node runtime. All intermediate files stay in tmp/submission-build.
const ROOT = path.resolve(process.env.SMART_UTILITY_ROOT || process.cwd());
const BUILD = path.join(ROOT, 'tmp/submission-build/visual-redesign');
const SKILL = 'C:/Users/basit/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations';
const RUNTIME = 'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies';
process.env.RUNTIME_NODE_MODULES = path.join(RUNTIME, 'node/node_modules');
const { Presentation, PresentationFile, FileBlob } = await import(pathToFileURL(path.join(RUNTIME, 'node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs')).href);
const { resolvePresentationFont, applyPresentationChartFont, finalizePresentation } = await import(pathToFileURL(path.join(SKILL, 'container_tools/artifact_tool_utils.mjs')).href);
const FONT = resolvePresentationFont({fontFamily:'Segoe UI',availableFonts:['Segoe UI','Consolas']});
const MONO='Consolas';
const QRCode=(await import(pathToFileURL(path.join(ROOT,'node_modules/qrcode/lib/index.js')).href)).default;
const qr=QRCode.create('https://neerulekka.wedevit.in/',{errorCorrectionLevel:'M'});
const C = { ink:'#0B1744', muted:'#526079', teal:'#003580', blue:'#526F95', orange:'#FF9933', pale:'#EEF2F7', line:'#DCE4EF', paper:'#FFFFFF', white:'#FFFFFF', dark:'#003580', lime:'#FF9933', green:'#138808' };
const p = Presentation.create({slideSize:{width:1280,height:720}});
let slideCount = 0;
const sources = {
  orientation:'raw-context/smart-utility-demand-forecasting-orientation.raw.txt (4 August 2026 orientation transcript, transcription contains errors). Department describes obsolete 2011 census-based DPRs, spatially uneven growth, short/medium/long horizons, CPHEEO planning assumptions, other water sources and distribution concerns.',
  workbook:'raw-context/2026-08-11-mtmc-maud/MTMC Population details.xlsx. Historical 2001/2011 demographic fields; MTMC_Wardwise_pop has 50 wards and cached gross population 331000/428000/554000 for 2026/2041/2056. Linked upstream workbooks are unavailable. These are planning projections, not observed consumption.',
  gis:'raw-context/2026-08-11-mtmc-maud/GIS_Data.zip; source-email.raw.txt. GIS Cell reports 2017–2019 source generation. public/data/mtmc/manifest.json and docs/MTMC_SOURCE_ANALYSIS.md record 65 GIS ward-secretariat boundaries, 16608 building features, incomplete coverage and later boundary metadata. No approved crosswalk to 50 workbook wards.',
  challenge:'https://rtgs.ap.gov.in/hackathons/#/home/case/100088 ; preserved raw-context/smart-utility-demand-forecasting-guidelines.raw.txt. Exact use case: Absence of Smart Demand Forecasting for Civic Utilities Leading to Service Gaps and Urban Flooding Risks. MAUD / Public Health Engineering Department; MTMC PoC. No claim of flood prediction.',
  method:'Proposed method authored for this submission. Not implemented or validated unless explicitly identified as prototype. No measured forecast performance or local fitted driver coefficients are available. Long-range climate scenarios should stress-test temperature/rainfall and dependable source yield separately from near-term weather forecasts, using relevant official projections with stated time horizon and uncertainty. No local climate effect or passenger-to-water coefficient has been estimated.',
  code:'src/data/mtmcData.js; src/data/growthModel.js; src/data/wardBuiltUp.json; src/data/operational.js; docs/OPERATIONAL_EVIDENCE.md. Current working-tree inspection 5 September 2026. Existing application changes were not altered for this deck.',
};
function txt(s,text,x,y,w,h,size=26,color=C.ink,bold=false,font=FONT){
  const a=s.shapes.add({geometry:'textbox',name:text.slice(0,60),position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
  a.text=text;a.text.style={typeface:font,fontSize:size,color,bold,autoFit:'none'};return a;
}
function rect(s,x,y,w,h,fill=C.pale){return s.shapes.add({geometry:'rect',position:{left:x,top:y,width:w,height:h},fill,line:{fill:'none',width:0}});}
function rule(s,x,y,w,color=C.line){rect(s,x,y,w,2,color);}
function notes(s,body,refs=[]){s.speakerNotes.textFrame.setText(body+'\n\nSources\n'+refs.map(k=>sources[k]??k).join('\n\n'));}
function slide(title,dark=false){const s=p.slides.add();slideCount++;s.background.fill=dark?C.dark:C.paper;rect(s,0,0,426,8,C.orange);rect(s,426,0,427,8,'#EEEEEE');rect(s,853,0,427,8,C.green);if(title)txt(s,title,68,77,1140,95,42,dark?C.white:C.teal,true);txt(s,String(slideCount).padStart(2,'0'),1170,677,60,30,14,dark?'#B9CCEA':C.muted);return s;}
function kicker(s,label,dark=false){const t=txt(s,label.toUpperCase(),68,37,1140,31,16,C.orange,true,MONO);}
function qrCode(s,x,y,size){const n=qr.modules.size,quiet=4,u=size/(n+2*quiet);rect(s,x,y,size,size,C.white);for(let r=0;r<n;r++)for(let c=0;c<n;c++)if(qr.modules.data[r*n+c])rect(s,x+(c+quiet)*u,y+(r+quiet)*u,u+.05,u+.05,'#000000');}
async function img(s,file,x,y,w,h,alt,crop){s.images.add({blob:new Uint8Array(await fs.readFile(path.join(ROOT,'docs/presentation/assets',file))),contentType:'image/png',fit:'contain',...(crop?{crop}:{}),position:{left:x,top:y,width:w,height:h},alt});}
function arrow(s,x,y,w=45,color=C.teal){s.shapes.add({geometry:'rightArrow',position:{left:x,top:y,width:w,height:22},fill:color,line:{fill:'none',width:0}});}
function circle(s,x,y,d,fill){return s.shapes.add({geometry:'ellipse',position:{left:x,top:y,width:d,height:d},fill,line:{fill:'none',width:0}});}
function line(s,x,y,w,h,color=C.teal){rect(s,x,y,w,h,color);}
function building(s,x,y,w,h,color=C.teal){rect(s,x,y,w,h,color);for(let a=0;a<Math.floor(w/24);a++)for(let b=0;b<Math.floor((h-16)/25);b++)rect(s,x+9+a*24,y+10+b*25,9,10,C.paper);}
function city(s,x,y,scale=1){building(s,x,y+65*scale,64*scale,95*scale);building(s,x+80*scale,y,75*scale,160*scale);building(s,x+171*scale,y+35*scale,57*scale,125*scale);line(s,x-8,y+166*scale,247*scale,4,C.ink);}
function floors(s,x,bottom,count,occupied){for(let f=0;f<count;f++){const y=bottom-(f+1)*43;rect(s,x,y,150,41,f<occupied?C.teal:'#CDD6E1');for(let w=0;w<5;w++)rect(s,x+14+w*26,y+13,11,14,C.paper);}}
function caption(s,text,dark=false){txt(s,text,56,650,1120,31,19,dark?'#ADBDCC':C.muted);}

// 01: Minimal cover, original product identity and one real product image.
{

 const s=slide('',true);
 txt(s,'SMART UTILITY DEMAND FORECASTING',68,49,1140,36,21,C.orange,true,MONO);
 txt(s,'NEERU LEKKA',68,130,1120,103,77,C.white,true);
 txt(s,'Local growth. Water demand.\nReviewed planning decisions.',68,255,522,114,36,C.white);
 await img(s,'user-selected-city-map.png',624,256,604,342,'User-selected oblique city map with mapped asset locations');
 qrCode(s,70,427,180);
 txt(s,'OPEN THE PROTOTYPE',267,440,360,34,18,C.orange,true,MONO);
 txt(s,'neerulekka.wedevit.in',267,482,358,43,26,C.white,true);
 txt(s,'Mangalagiri–Tadepalli',267,537,358,42,25,'#CFDFF5');
 txt(s,'WEDEVIT PRIVATE LIMITED',70,638,550,35,23,C.white,true);
 txt(s,'DPIIT-recognised startup  DIPP266651',651,638,578,35,23,'#CFDFF5');


 notes(s,'Cover visual supplied directly by the user on 5 September 2026: codex-clipboard-28469776-237e-41da-96f4-1381e79c3006.png, embedded without pixel edits. Capture route, viewport and underlying point classes were not independently provided for this image; it illustrates visible urban form and mapped locations, not current capacity or measured consumption. QR encodes exactly https://neerulekka.wedevit.in/ as requested. Submission date is the team preparation date, not an organizer deadline. This deck describes an evidence-review prototype and a proposed demand forecasting system. Smart Utility Demand Forecasting is the official challenge name; NEERU LEKKA is the current product brand in this repository; WEDEVIT PRIVATE LIMITED is the legal company name. Visual: page 1 of raw-context/2026-08-11-mtmc-maud/MTMC MASTER PLAN (1).pdf, rendered from the preserved original. PDF metadata is dated 16 May 2023 and does not establish plan approval, completed development or current occupancy. No usable geospatial reference was supplied.', ['challenge','https://www.wedevit.in/doc and linked https://www.wedevit.in/documents/CIN.pdf, inspected 5 September 2026. Company-published incorporation certificate copy names WEDEVIT PRIVATE LIMITED.']);
}
// 02: Department decision, spatial context, explicit operating limit.
{

 const s=slide('The map locates assets. The model must locate demand.');
 kicker(s,'Current prototype');
 await img(s,'spatial-desktop-20260905.png',64,182,862,453,'Real local historical GIS asset map detail',{left:.014,top:.238,right:.197,bottom:.19});
 txt(s,'Growth',966,227,264,53,35,C.teal,true);
 txt(s,'Where will\nneed increase?',966,288,264,84,26);
 rect(s,972,405,240,3,C.orange);
 txt(s,'Capacity',966,439,264,53,35,C.teal,true);
 txt(s,'What can\nserve the area?',966,500,264,92,26);
 caption(s,'Real local app. 2017–2019 source-point inventory; mapped assets do not establish current service capacity.');


 notes(s,'Native PowerPoint crop focuses on the real captured map panel, preserving source year and basemap attribution. Full original capture remains embedded and preserved on disk. Department problem: uniform arithmetic allocation misses saturated old neighbourhoods and new growth corridors. Forecast water demand and sewage generation at ward/city level, then compare with confirmed service-area capacity. Network hydraulics, water quality and equal distribution require additional operational evidence and engineering analysis; this prototype does not automatically control distribution.', ['orientation','gis','Fresh real-browser screenshot captured 5 September 2026 from http://127.0.0.1:5197/dashboard/spatial in the saved devit-utility checkout. Browser viewport 1440 x 1000; unaltered content crop x260 y120 width1130 height830. Displayed data: real supplied historical GIS records (2017–2019 operational inventory, later boundary metadata), not live telemetry or synthetic data. Local walkthrough sign-in is not production authorization. Basemap credits: OpenFreeMap https://openfreemap.org/ ; OpenMapTiles https://openmaptiles.org/ ; OpenStreetMap contributors https://www.openstreetmap.org/copyright .']);
}
// 03: Evidence chronology, not mixed-vintage precision.
{

 const s=slide('Three local growth paths to test');kicker(s,'Future development');
 city(s,69,202,.78);
 txt(s,'Infill',56,396,345,56,40,C.ink,true);
 txt(s,'More occupied floors\nwithin existing streets',56,463,345,91,27,C.muted);
 for(let r=0;r<2;r++)for(let c=0;c<3;c++)building(s,478+c*72,236+r*78,48,52,r===1?'#A7BADE':C.teal);
 line(s,460,320,245,7,C.ink);line(s,560,195,7,209,C.ink);
 txt(s,'New colony',451,396,350,56,40,C.ink,true);
 txt(s,'Commissioning dates\nand occupancy ramps',451,463,375,95,27,C.muted);
 building(s,875,257,127,108,C.teal);building(s,1033,190,80,175,C.teal);
 line(s,859,372,303,9,C.ink);
 txt(s,'Economic growth',850,396,376,56,39,C.ink,true);
 txt(s,'Schools, commerce, industry\nand proposed data centres',850,463,380,95,27,C.muted);
 rect(s,56,579,1168,53,C.dark);
 txt(s,'Near term: seasonal use          2041: development paths          2056: long-range scenarios',72,588,1135,38,25,C.white);
 caption(s,'Proposed scenario families. Project dates, occupancy ramps and local coefficients require evidence.');

 notes(s,'User-supplied evaluator transcript, 5 September 2026. Proposed local growth scenario families distinguish existing-area densification, commissioned new colonies and verified non-domestic economic activity. No particular colony, data centre, airport project or road project is asserted as approved or located within the MTMC service catchment. Apply building-use classification, commissioned connection dates and occupancy ramps before assigning activity-specific water demand. 2041 and 2056 are supplied planning horizons, not validated forecasts. Near-term monthly horizons should reflect available history and seasonality. 2001 data are 25 years old in 2026. The orientation specifically says the DPR population base is 2011. The supplied workbook contains both years. Do not characterize every dataset as 2001. The 2026 base-year label does not make a cached projection an observation. Building and parcel coverage is geographically incomplete; new boundary metadata do not refresh the old operational inventory. Cached gross 2026 population multiplied by 135 LPCD yields 44.685 MLD only as a planning calculation. Missing external workbook links prevent a full local recalculation.', ['workbook','gis','orientation']);
}
// 04: Explicitly requested editable demand-driver composition.
{

 const s=slide('Different uses need different demand models');kicker(s,'Local demand drivers');
 const xs=[68,361,654,947];
 city(s,81,210,.65);
 building(s,378,270,182,98,C.teal);rect(s,451,242,36,36,C.orange);
 building(s,674,266,178,102,C.teal);rect(s,688,211,21,73,C.teal);rect(s,744,231,21,48,C.teal);
 for(let c=0;c<3;c++){rect(s,967+c*59,224,46,143,C.teal);for(let r=0;r<5;r++){rect(s,974+c*59,233+r*24,31,14,C.pale);circle(s,994+c*59,237+r*24,5,C.orange);}}
 ['Homes','Institutions','Industry','Data centres'].forEach((t,i)=>txt(s,t,xs[i],409,282,52,32,C.teal,true));
 ['Dwellings + people','Students + operating days','Process + production','IT load + cooling'].forEach((t,i)=>txt(s,t,xs[i],470,280,78,25,C.ink));
 rect(s,68,568,1152,61,C.pale);txt(s,'Local activity × calibrated use intensity = demand by category',83,580,1120,42,30,C.teal,true);
 caption(s,'Proposed calibration. Include commercial uses, occupancy, seasonality and service-linked development.');


 notes(s,'Non-domestic categories use evidence-appropriate activity variables, not one universal population/LPCD multiplier. The listed variables are candidates; no locally fitted water intensity or data-centre coefficient exists. A data-centre model requires technology and cooling-system information as well as verified load, utilization and location; proposed sites are scenario inputs only after evidence of service linkage. Proposed driver framework, not fitted local effects. Domestic demand uses households/population and occupancy. Footprint alone is a proxy, not occupied floor area or water use. Non-domestic demand uses metered categories and activity evidence. Airport passenger counts, hotel nights and economic indicators are candidate proxies only where a physical service-area or catchment link and incremental predictive value can be demonstrated. Do not assign airport demand to MTMC merely because an airport is nearby. Avoid double counting employees, visitors, airport users and household demand. Land-use permission does not prove construction or occupation. Source shortages and restrictions can suppress delivered/billed water below unconstrained demand. Network losses increase required production and do not represent consumer consumption. Every input needs owner, observation date, units, spatial extent and an observed/proxy/assumed/missing classification.', ['orientation','method']);
}
// 05: System view and mass-balance distinctions.
{
 const s=slide('From source records to a reviewed forecast',true);kicker(s,'Proposed forecasting approach');
 const xs=[56,363,670,977], titles=['Records','Alignment','Model','Review'];
 for(let i=0;i<4;i++){
  circle(s,xs[i],183,68,i===3?C.lime:C.teal);txt(s,String(i+1),xs[i]+19,186,40,59,39,i===3?C.dark:C.white,true);
  txt(s,titles[i],xs[i],278,245,55,35,C.white,true);if(i<3)arrow(s,xs[i]+116,207,122,'#566879');
 }
 ['Readings\nConnections','Units\nService areas','Seasonal baseline\nLocal regression\nML if validated','Ranges\nEngineer sign-off'].forEach((t,i)=>txt(s,t,xs[i],350,275,126,26,'#C2CCD6'));
 rule(s,56,504,1168,'#445565');
 txt(s,'DOMESTIC USE',56,543,355,38,23,C.lime,true);txt(s,'People × use rate',56,588,355,52,30,C.white);
 txt(s,'SYSTEM INPUT',463,543,355,38,23,C.lime,true);txt(s,'Use ÷ (1 − loss share)',463,588,390,52,29,C.white);
 txt(s,'SEWAGE',918,543,305,38,23,C.lime,true);txt(s,'Use × return fraction',918,588,310,52,27,C.white);
 caption(s,'Proposed model. Full water balance and sewer contributions require separate reconciliation.',true);

 notes(s,'Conceptual proposed architecture. Candidate monthly regression: service-zone effect + month-of-year terms + lagged consumption (e.g. 1 and 12 months) + verified connection/occupancy/weather/policy drivers. Compare against seasonal-naive and per-connection baselines with rolling backtests; coefficients are not fitted here. Daily rates in litres per day convert to MLD by division by 1,000,000. For system input, use total authorised consumption divided by (1 minus water-loss share of system input). NRW also includes authorised unbilled consumption, so NRW and physical leakage are not interchangeable. If using NRW share instead, the numerator is billed authorised consumption. Reconcile the full water balance and never count losses twice. Sewerage generation, collected sewer flow and STP treatment load are separate quantities. Apply the return fraction to relevant consumer use including verified private borewell/tanker contributions; the extra term in the diagram represents their sewer-return contribution, not their entire supplied volume. Account separately for sewer connection coverage, infiltration/inflow and non-returning uses. The prototype defaults to 135 LPCD and an 80% return assumption from the orientation; these are planning assumptions, not measured use or a universal regulatory determination.', ['orientation','code','method','MoHUA/ASCI, Guidance Notes on Preparation of DPR for Drink-from-Tap, supplied attachment Guidance-Notes-on-Preparation-of-DPR.pdf, PDF page 71 (printed page 19). Separates consumer-end LPCD, losses, non-domestic bulk use and floating population. Its example 15% losses is not an MTMC observation. Local source: C:/Users/basit/Downloads/Guidance-Notes-on-Preparation-of-DPR.pdf.']);
}
// 06: Native spatial-method diagram, no invented GIS precision.
{

 const s=slide('The same road can serve very different demand');kicker(s,'The evaluator’s central point');
 txt(s,'Equal road length',56,160,1140,44,28,C.muted);
 line(s,67,440,1140,17,C.ink);line(s,67,477,1140,3,C.line);
 floors(s,122,431,1,1);
 floors(s,485,431,5,5);
 floors(s,877,431,5,1);
 txt(s,'One occupied floor',75,496,335,51,29,C.ink,true);
 txt(s,'Five occupied floors',430,496,380,51,29,C.ink,true);
 txt(s,'Upper floors vacant',846,496,371,51,29,C.ink,true);
 txt(s,'Footprint × floors × occupied share = occupied floor area',56,583,1160,51,34,C.teal,true);
 caption(s,'Illustrative buildings. Height shows floors; colour shows occupancy. Demand needs verified people or activity.');


 notes(s,'Evaluator feedback supplied by the user on 5 September 2026: equal distribution by road length or ward misses multi-storey development, occupancy and building-use differences. This is an illustrative schematic, not mapped MTMC buildings. Proposed modelling input: occupied floor area = footprint area times applicable floor count times occupied share; mixed uses require per-floor classification. Do not assume demand is five times greater solely because a building has five floors. Estimate residential occupants from verified dwellings and occupancy or locally calibrated area-per-person data, then apply an appropriate consumer-use assumption. Non-domestic demand needs category-specific activity units and metering. Current src/data/growthModel.js reallocates fixed workbook city totals over 65 GIS wards. It starts from equal base population across the 65 boundaries, weights normalized positive built-up index trend times relative headroom, applies a hard-coded 1.35 corridor multiplier to listed corridors and defaults to 0.7 spatial weighting. This is not a fitted logistic/geometric demand forecast and does not establish ward population. A near-zero headroom value is an algorithmic proxy, not verified land capacity. Proposed replacement: use approved crosswalks and observed building/connection/occupancy data, reconcile bottom-up ward estimates to a compatible city total, represent planned projects by commissioning/occupation scenarios, and validate on withheld periods and areas.', ['code','gis','workbook','method']);
}
// 07: Transparent editable scenario chart, deliberately normalized.
{
 const s=slide('How assumptions change the demand path');kicker(s,'Illustrative sensitivity');
 txt(s,'Five-year illustrative sensitivity. Base = 100.',56,145,1100,45,23,C.muted);
 const horizons=[0,1,2,3,4,5];
 const cases=[{name:'Lower',h:0,u:-.01,color:C.blue},{name:'Central',h:.015,u:0,color:C.teal},{name:'Higher',h:.03,u:.01,color:C.orange}];
 const chart=s.charts.add('line',{position:{left:56,top:217,width:818,height:391},categories:horizons.map(y=>y===0?'Base':`Year ${y}`),series:cases.map(c=>({name:c.name,values:horizons.map(y=>Number((100*((1+c.h)*(1+c.u))**y).toFixed(3))),line:{fill:c.color,width:4},marker:{symbol:'circle',size:6}})),hasLegend:true,legend:{position:'bottom',textStyle:{typeface:FONT,fontSize:20,fill:C.ink}},xAxis:{textStyle:{typeface:FONT,fontSize:18,fill:C.muted}},yAxis:{min:90,max:130,majorUnit:10,numberFormatCode:'0',textStyle:{typeface:FONT,fontSize:18,fill:C.muted},majorGridlines:{fill:C.line,width:1}},chartFill:C.paper,plotAreaFill:C.paper});
 applyPresentationChartFont(chart,{fontFamily:FONT});
 txt(s,'Annual inputs',916,226,310,42,29,C.ink,true);
 txt(s,'Lower\nHouseholds 0%, use −1%',916,296,310,77,24,C.blue);
 txt(s,'Central\nHouseholds +1.5%, use 0%',916,392,310,77,24,C.teal);
 txt(s,'Higher\nHouseholds +3%, use +1%',916,488,310,77,24,C.orange);
 txt(s,'Scenario spread is assumption uncertainty, not a statistical confidence interval or an MTMC forecast.',56,650,1120,34,19,C.muted);
 notes(s,'Entire chart is a hypothetical sensitivity demonstration, not observed MTMC data. Index(t)=100×[(1+annual household growth)×(1+annual per-household use change)]^t. Household size and non-domestic mix are held constant for this illustration. Lower: 0% household growth and -1% use, year5 95.099. Central: +1.5% and 0%, year5 107.728. Higher: +3% and +1%, year5 121.841. Display rounding does not imply measured precision. No probabilities attach to these paths. For the operational model, separate parameter/project scenarios from empirical prediction intervals calibrated on backtest residuals. Revise the paths when occupancy, tariffs, weather or project dates change. Long-range 2041/2056 planning should use explicit development scenarios, not extrapolate this five-year example mechanically.', ['method']);
}
// 08: Product evidence, concise annotation outside screenshot.
{
 const s=slide('A ward-level planning calculation');kicker(s,'Current prototype');
 await img(s,'demand-desktop-20260905.png',56,150,794,480,'Actual local Ward 40 demand calculation at the 2041 horizon');
 rect(s,905,163,319,439,C.dark);
 txt(s,'WARD 40 / 2041',927,185,274,39,23,C.lime,true);
 txt(s,'1.26',924,249,275,101,78,C.white,true);
 txt(s,'MLD water',931,349,270,44,28,C.white);
 txt(s,'1.01',924,419,275,91,68,C.white,true);
 txt(s,'MLD sewage',931,516,270,44,28,C.white);
 txt(s,'Gap awaits capacity data',913,608,317,40,22,C.orange,true);
 caption(s,'Source-derived planning projections. Local app capture: 5 September 2026. Accuracy unmeasured.');

 notes(s,'Fresh local demand screen captured after using actual controls to select Ward 40, year 2041 and comparison mode. Displayed numbers are calculations from cached workbook projections, not measured current consumption or a fitted forecast. Ward 40 displayed 9367 planning population, 1.26 MLD water and 1.01 MLD sewage at the selected horizon. No synthetic readings were inserted. The current app brand is NEERU LEKKA in this same Smart Utility checkout. Screenshot provenance recorded in docs/presentation/SOURCES_AND_READINESS.md; this proves the inspected local state only.', ['code','workbook','Captured 5 September 2026: http://127.0.0.1:5197/dashboard/demand?ward=40&year=2041&horizons=compare . Browser viewport 1440 x 1000; unaltered content crop x260 y120 width1130 height790. Data class: source-derived planning projection. The same state was inspected at 390 x 844 with no page-level horizontal overflow. Related preserved source PDF: output/innovation-for-her-draft-pack.pdf.']);
}
// 09: Department workflow plus real intake screenshot.
{
 const s=slide('Missing readings stay visible');kicker(s,'Data readiness');
 await img(s,'requirements-mobile-20260905.png',79,141,228,494,'Actual 390px mobile R-08 requirement detail');
 const yy=[177,290,403,516];
 line(s,448,207,3,341,C.line);
 ['Department CSV','Dates, units, area IDs','Historical usage','Backtesting'].forEach((t,i)=>{
 circle(s,430,yy[i]+10,40,i<2?C.teal:C.line);txt(s,String(i+1),439,yy[i]+8,32,40,25,i<2?C.white:C.ink,true);
 txt(s,t,505,yy[i],690,51,35,C.ink,true);
 txt(s,['Local intake available','Quality checks available','Records still required','Blocked until history exists'][i],505,yy[i]+51,690,42,25,i<2?C.muted:C.orange);
 });
 caption(s,'Population base: 2011. GIS: 2017–2019. Approved 50-to-65 boundary crosswalk and recent readings needed.');

 notes(s,'Fresh mobile capture of the actual populated requirements detail, selected using the R-08 search and item controls. The next-gate link successfully opened Daily readings, which explicitly showed No daily readings loaded. That empty operational dashboard is omitted from the deck; no demo readings were created to fill it. Department files require timestamps, units, stable area identifiers and correction handling. Production requires configured server-side authorization, scoped data access, audited writes and department acceptance. UI roles or a mock sign-in do not prove production RBAC.', ['code','Captured 5 September 2026: http://127.0.0.1:5197/dashboard/requirements?year=2041&req-q=R-08&req-id=R-08 . Viewport 390 x 844, full viewport screenshot at the selected-detail scroll position, no edits or compositing. Displayed data: real app requirement/source metadata. Historical usage readings: unavailable. Walkthrough sign-in: presentation environment, not production authorization. Linked route inspected: /dashboard/operations?year=2041 .']);
}
// 10: Forecast evaluation, native time split diagram.
{
 const s=slide('The model must predict months it has not seen');kicker(s,'Proposed validation');
 txt(s,'Earlier history',56,160,500,43,28,C.teal,true);txt(s,'Future months',867,160,360,43,28,C.orange,true);
 const x=240,cell=70,gap=5;
 for(let r=0;r<3;r++){
  txt(s,['Backtest 1','Backtest 2','Backtest 3'][r],56,248+r*104,177,47,25,C.ink,true);
  for(let c=0;c<13;c++)rect(s,x+c*(cell+gap),235+r*104,cell,60,c<7+r?C.teal:c<10+r?C.orange:C.pale);
 }
 txt(s,'Train on available history',240,555,535,41,27,C.teal,true);
 txt(s,'Score held-out periods',845,555,380,41,27,C.orange,true);
 caption(s,'Compare seasonal baselines. Report MAE, WAPE, bias and interval coverage. No measured result yet.');

 notes(s,'Proposed evaluation plan. Prefer at least 36 monthly observations, with 60 desirable, subject to seasonal coverage and data quality; these are requested data windows, not universal sufficiency thresholds. Use expanding/rolling origins with horizon-matched held-out blocks; no random time split. Start with seasonal naïve (same month previous year), trend/seasonal regression and population/use-rate baselines. Introduce lagged consumption (e.g. 1 and 12 months), weather, connection counts and verified drivers only if out-of-sample gains persist. Future weather must use information available at forecast time. Measure MAE in MLD and signed bias; WAPE is sum absolute error divided by sum actual use and needs nonzero aggregate actual use. Validate boundary changes and sparse wards separately. Calibrate empirical intervals and report coverage plus width. Fit and select without looking at final test outcomes. No numeric acceptance threshold or performance result has been agreed or achieved.', ['method']);
}
// 11: No dense raw table: four timed intake bands.
{

 const s=slide('A verified gap becomes a reviewed action',true);kicker(s,'Decision and accountability');
 txt(s,'Projected requirement',56,156,500,55,37,C.white,true);
 txt(s,'−',555,157,58,60,43,C.lime,true);
 txt(s,'Usable service-area capacity',631,156,599,55,37,C.white,true);
 txt(s,'Same area, horizon, units and operating basis',56,229,1150,45,25,'#ADBDCC');
 rule(s,56,301,1168,'#465563');
 const xs=[56,363,670,977];
 const roles=['State planner','Local body','Ward operator','Dept. reviewer'];
 const acts=['Prioritise','Assign','Verify on site','Accept or return'];
 for(let i=0;i<4;i++){
 circle(s,xs[i],341,56,i===3?C.lime:C.teal);txt(s,String(i+1),xs[i]+13,341,43,51,32,i===3?C.dark:C.white,true);
 if(i<3)arrow(s,xs[i]+100,358,142,'#526777');
 txt(s,roles[i],xs[i],431,257,49,28,C.white,true);
 txt(s,acts[i],xs[i],497,257,80,28,i===3?C.lime:'#C2CCD6');
 }
 txt(s,'Task record: owner, due date, evidence and outcome',56,591,1130,48,31,C.white);
 caption(s,'Proposed workflow. Capacity verification, production permissions and task closure are not yet proven.',true);


 notes(s,'User-provided evaluator discussion and role clarification, 5 September 2026. Four business roles suffice for the proposed pilot: state planner oversees priorities and cross-ULB allocation; local-body planner scopes and assigns interventions; ward operator supplies field evidence and updates; departmental reviewer verifies findings and accepts or returns closure. These are responsibilities, not proof of implemented authorization. Keep the person closing a task distinct from its evidence submitter. A technical administrator can be an operational permission rather than a fifth planning persona. Capacity gap must compare like-for-like service area, horizon, time basis and flow definition; water consumer use must be reconciled to production input, and sewage generation to collected/treatment load. Unknown capacity produces an evidence request, not a fabricated numerical gap. Task statuses can be proposed, assigned, evidence submitted, accepted or returned; no automatic public-infrastructure control is claimed. Proposed data contract. Consumption: monthly/daily volume, source and billing basis, unit, period, meter identifier or approved aggregate zone, active connections, meter replacement/reset, missingness and corrections. Spatial: current boundary versions and effective dates, approved crosswalk from 50 workbook wards to 65 GIS boundaries, service zones, building footprint/use/floors/occupancy and connection links. Operations: source abstraction/availability, bulk inflow/outflow, authorised billed/unbilled consumption, audited NRW components, supply hours/outages, WTP/storage/STP design and usable capacity, commissioning dates, sewer connections and flow. Exogenous: local weather, industrial/commercial activity and metered categories, hotel occupancy/tourism seasonality, airport passenger counts only with demonstrable catchment linkage, tariffs/restriction dates and project/phasing/occupation evidence. Refresh proposal: monthly consumption/connections and model run; daily operational/weather inputs where available; quarterly building/use review; project and policy updates on approved change. Retain original observations, derived values, provenance and versioned assumptions. Annual-only totals support annual scenarios and cannot establish monthly seasonality.', ['method','orientation']);
}
// 12: Specific pilot decision and clear release boundary.
{

 const s=slide('Visit NEERU LEKKA',true);kicker(s,'Prototype and team',true);
 qrCode(s,79,193,306);
 txt(s,'neerulekka.wedevit.in',78,522,580,57,35,C.white,true);
 txt(s,'Login: neerulekka.wedevit.in/login',78,588,640,48,27,'#CFDFF5');
 txt(s,'WEDEVIT PRIVATE LIMITED',712,184,506,52,29,C.white,true);
 txt(s,'DPIIT-recognised startup\nDIPP266651',712,244,506,79,25,'#CFDFF5');
 txt(s,'wedevit.in',712,346,506,43,28,C.white,true);
 txt(s,'workwithdevit@gmail.com',712,399,506,43,27,C.white);
 txt(s,'+91 95533 21211',712,452,506,43,27,C.white);
 txt(s,'Shaik Muqeeth\nShaik Abdul Basith',712,526,506,80,26,C.white);
 txt(s,'Co-founders, Kurnool',712,617,506,38,22,'#CFDFF5');


 notes(s,'User supplied exact prototype homepage https://neerulekka.wedevit.in/ and login https://neerulekka.wedevit.in/login . Both are included as clickable links; QR points to homepage. Company website, email and phone are verified from company documents page. Recognition belongs to company, not certification of the prototype. Company identity and contact checked live on 5 September 2026. https://www.wedevit.in/doc lists the legal name WeDevit Private Limited, brand devit (We Develop It), co-founders Shaik Muqeeth and Shaik Abdul Basith, company email and phone. The company-published CIN certificate https://www.wedevit.in/documents/CIN.pdf names WEDEVIT PRIVATE LIMITED, CIN U62020AP2026PTC125497, incorporated 24 April 2026. The linked DPIIT certificate https://www.wedevit.in/documents/DIPP266651_WEDEVIT_PRIVATE_LIMITED_RECOGNITION_1375028494029563733.pdf identifies DIPP266651 issued 10 June 2026; certificate copies were textually and visually inspected, not checked against a live government registry. Homepage https://www.wedevit.in/ describes web/mobile/custom software and lists ITI Dashboard and ToFEI as government-focused portfolio work. Repository cross-checks: ../devit-iti/README.md documents an ITI monitoring PoC; ../devit-tofei/README.md documents ToFEI analytics, UDISE lookup, submission and report tracking. These establish portfolio/capability references, not government procurement, adoption, approved outcomes or production scale. Conflict: homepage describes seven Krishna District ITIs, while the current ITI README describes a four-ITI PoC elsewhere; the deck deliberately makes no institution-count, deployment-scale or client-approval claim. The next step is an agreed-area pilot with a verified crosswalk and recent readings, followed by backtests, engineering review and production access approval. Current organizer deadline, template, slide limit, team registration identifier and filename rule remain unknown. No unsupported identity field is filled.', ['challenge','orientation','method']);
}

await fs.mkdir(BUILD,{recursive:true});
const candidatePath=path.join(BUILD,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidatePath);
await fs.writeFile(path.join(BUILD,'deck-content.ndjson'),(await p.inspect({kind:'slide,textbox,chart,notes',maxChars:200000})).ndjson);
const finalPath=path.join(ROOT,'docs/presentation',process.env.SMART_UTILITY_OUTPUT || 'NEERU_LEKKA_WEDEVIT_Visual_Deck.pptx');
const result=await finalizePresentation({workspaceDir:ROOT,candidatePath,finalPath,pythonExecutable:path.join(RUNTIME,'python/python.exe'),integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],explicitTotalSlideCount:12,requiredNativeChartOwnerSlides:[7],requiredNativeTableOwnerSlides:[],materializeLiteralChartWorkbooks:true,fontPolicy:{basis:'reference',families:[FONT,MONO],referencePath:'C:/Users/basit/Downloads/CODE/PPT/protohub-deck-visual-final--8466f13c9299e91c.pptx',referenceSha256:createHash('sha256').update(await fs.readFile('C:/Users/basit/Downloads/CODE/PPT/protohub-deck-visual-final--8466f13c9299e91c.pptx')).digest('hex')},verifyArtifactToolImport:true,receiptPath:path.join(BUILD,path.basename(finalPath)+'.validation.json')});
console.log(JSON.stringify({font:FONT,...result},null,2));
const final=await PresentationFile.importPptx(await FileBlob.load(finalPath));
await fs.mkdir(path.join(BUILD,'renders'),{recursive:true});
for(let i=0;i<slideCount;i++){
 const blob=await final.export({slide:final.slides.getItem(i),format:'png',scale:1});
 await fs.writeFile(path.join(BUILD,'renders',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await blob.arrayBuffer()));
}
console.log('Rendered all final slides.');


