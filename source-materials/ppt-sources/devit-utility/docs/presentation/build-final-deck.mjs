import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Run with the bundled Node runtime. All intermediate files stay in tmp/submission-build.
const ROOT = path.resolve(process.env.SMART_UTILITY_ROOT || process.cwd());
const BUILD = path.join(ROOT, 'tmp/submission-build/final-visual');
const SKILL = 'C:/Users/basit/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations';
const RUNTIME = 'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies';
process.env.RUNTIME_NODE_MODULES = path.join(RUNTIME, 'node/node_modules');
const { Presentation, PresentationFile, FileBlob } = await import(pathToFileURL(path.join(RUNTIME, 'node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs')).href);
const { resolvePresentationFont, applyPresentationChartFont, finalizePresentation } = await import(pathToFileURL(path.join(SKILL, 'container_tools/artifact_tool_utils.mjs')).href);
const FONT = resolvePresentationFont({fontFamily:'Segoe UI',availableFonts:['Segoe UI','Consolas']});
const MONO='Consolas';
const QRCode=(await import(pathToFileURL(path.join(ROOT,'node_modules/qrcode/lib/index.js')).href)).default;
const qr=QRCode.create('https://neerulekka.wedevit.in/',{errorCorrectionLevel:'M'});
const C = { ink:'#0B1744', muted:'#526079', teal:'#003580', blue:'#008FCE', orange:'#FF8A24', pale:'#E3F4FA', line:'#DCE4EF', paper:'#FFFFFF', white:'#FFFFFF', dark:'#062B64', lime:'#FF9933', green:'#138808' };
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
function slide(title,dark=false){const s=p.slides.add();slideCount++;s.background.fill=dark?C.dark:({4:'#FFF3E4',6:'#E4F5F3',7:'#FFF3E4',9:'#E5F2FF',10:'#FFF3E4',12:'#E5F2FF',14:'#E4F5F3',18:'#FFF3E4'}[slideCount]||C.paper);rect(s,0,0,426,8,C.orange);rect(s,426,0,427,8,'#EEEEEE');rect(s,853,0,427,8,C.green);if(title)txt(s,title,68,77,1140,95,42,dark?C.white:C.teal,true);txt(s,String(slideCount).padStart(2,'0'),1170,677,60,30,14,dark?'#B9CCEA':C.muted);return s;}
function kicker(s,label,dark=false){const t=txt(s,label.toUpperCase(),68,37,1140,31,16,C.orange,true,MONO);}
function qrCode(s,x,y,size){const n=qr.modules.size,quiet=4,u=size/(n+2*quiet);rect(s,x,y,size,size,C.white);for(let r=0;r<n;r++)for(let c=0;c<n;c++)if(qr.modules.data[r*n+c])rect(s,x+(c+quiet)*u,y+(r+quiet)*u,u+.05,u+.05,'#000000');}
async function img(s,file,x,y,w,h,alt,crop){s.images.add({blob:new Uint8Array(await fs.readFile(path.join(ROOT,'docs/presentation/assets',file))),contentType:file.endsWith('.jpg')?'image/jpeg':'image/png',fit:crop?'cover':'contain',...(crop?{crop}:{}),position:{left:x,top:y,width:w,height:h},alt});}
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
 rect(s,68,634,529,43,C.white);txt(s,'WEDEVIT PRIVATE LIMITED',70,635,525,36,23,'#CB1723',true);
 txt(s,'DPIIT-recognised startup  DIPP266651',651,638,578,35,23,'#CFDFF5');


 notes(s,'Cover visual supplied directly by the user on 5 September 2026: codex-clipboard-28469776-237e-41da-96f4-1381e79c3006.png, embedded without pixel edits. Capture route, viewport and underlying point classes were not independently provided for this image; it illustrates visible urban form and mapped locations, not current capacity or measured consumption. QR encodes exactly https://neerulekka.wedevit.in/ as requested. Submission date is the team preparation date, not an organizer deadline. This deck describes an evidence-review prototype and a proposed demand forecasting system. Smart Utility Demand Forecasting is the official challenge name; NEERU LEKKA is the current product brand in this repository; WEDEVIT PRIVATE LIMITED is the legal company name. Visual: page 1 of raw-context/2026-08-11-mtmc-maud/MTMC MASTER PLAN (1).pdf, rendered from the preserved original. PDF metadata is dated 16 May 2023 and does not establish plan approval, completed development or current occupancy. No usable geospatial reference was supplied.', ['challenge','https://www.wedevit.in/doc and linked https://www.wedevit.in/documents/CIN.pdf, inspected 5 September 2026. Company-published incorporation certificate copy names WEDEVIT PRIVATE LIMITED.']);
}

{

 const s=slide('',true);
 txt(s,'నీరు లెక్క',64,115,1120,210,152,C.white,true,'Nirmala UI');
 txt(s,'NEERU LEKKA',74,332,650,53,37,C.orange,true);
 txt(s,'Open the prototype',74,421,680,60,42,C.white,true);
 txt(s,'neerulekka.wedevit.in',74,511,720,64,42,C.white,true);
 rect(s,75,577,578,3,C.orange);
 qrCode(s,870,350,315);
 txt(s,'Scan to visit',918,303,300,45,27,C.white,true);
 notes(s,'QR and clickable text both open https://neerulekka.wedevit.in/. Current prototype: maps and planning estimates. Local growth forecasts and persistent task tracking are the proposed next release. Telugu product name uses Nirmala UI for Telugu glyph support.',['code']);

}


{

 const s=slide('');
 await img(s,'building-water-example.png',35,20,1210,662,'Generated building example: one storey and five storeys, water and sewage paths');
 txt(s,'More people use more water and make more sewage.',68,668,1140,35,24,C.teal,true);

 notes(s,"Commissioned AI-generated architectural illustration for this presentation. This is a genericbuildingexample, not a realMTMCproperty or a pipe-design drawing. It illustrates the evaluator point aboutoccupiedfloors. Pipe paths are schematic; nohydraulicclaim.",[]);
}


{

 const s=slide('A simple building example');kicker(s,'Same-sized homes, all in use');
 floors(s,95,433,1,1);floors(s,634,433,5,5);
 txt(s,'1 floor',273,180,319,56,41,C.teal,true);txt(s,'5 floors',835,180,335,56,41,C.teal,true);
 txt(s,'16 people',273,252,319,47,30);txt(s,'80 people',835,252,335,47,30);
 txt(s,'2,160 L/day',273,318,331,59,40,C.teal,true);txt(s,'10,800 L/day',835,318,355,59,40,C.teal,true);
 txt(s,'Water needed',273,383,319,45,25,C.muted);txt(s,'Water needed',835,383,335,45,25,C.muted);
 rect(s,68,495,1152,97,C.pale);
 txt(s,'Extra water: 8,640 L/day',87,512,570,55,36,C.teal,true);
 txt(s,'Extra sewage: 6,912 L/day',653,512,551,55,34,'#A35C15',true);
 caption(s,'Example assumptions: 4 homes/floor, 4 people/home, 135 L/person/day, 80% sewage return.');

 notes(s,"Illustrative arithmetic, notmeasuredMTMCbuildingdata. 1floor:4homesx4people=16people,water2160L/day,sewage1728L/day.5floors:80people,water10800,sewage8640. Increment8640waterand6912sewage. Allhomesinuse,sameuseclassandhouseholdsizeheldconstant.135LPCDand80percentareplanningassumptions. Demandisnotinferredfromheightalone.",["orientation"]);
}


{

 const s=slide('Plan water supply and sewage together');kicker(s,'One area, two needs');
 rect(s,81,262,213,144,C.teal);txt(s,'Water\nsupply',102,281,180,105,32,C.white,true);
 arrow(s,331,321,130,C.teal);city(s,489,213,.96);arrow(s,770,321,130,C.orange);
 rect(s,949,262,251,144,C.orange);txt(s,'Sewage\ntreatment',967,281,219,105,31,C.ink,true);
 txt(s,'Homes and businesses',446,447,398,53,30,C.teal,true);
 txt(s,'Allow for water losses',76,550,496,49,28,C.ink,true);
 txt(s,'Check sewer links and treatment',681,550,537,49,28,C.ink,true);
 caption(s,'Keep water use, water supplied, sewage flow and treatment limits as separate values.');

 notes(s,"Conceptual waterbalance andsewageflow. Consumeruse differsfromsourceproductionbecauseofreconciledwaterlosses. NRWincludesunbilledauthorizeduse andisnotpurephysicalleakage. Sewercollectiondepends onconnectionsandreturn fractions; treatmentloaddiffersfromgeneratedsewage. No universalconnectioncoverageassumption.",["orientation", "method"]);
}


{

 const s=slide('Hospital use becomes a separate demand estimate');kicker(s,'Local example: AIIMS Mangalagiri');
 await img(s,'aiims-mangalagiri.jpg',68,193,1152,295,'Official MoHFW PMSSY photo of AIIMS Mangalagiri');
 const labels=['Patients','Staff','Cleaning','Laundry'];
 labels.forEach((t,i)=>{rect(s,68+i*293,521,268,69,C.pale);txt(s,t,87+i*293,536,237,44,31,C.teal,true);});
 caption(s,'Forecast design: site use + water source → local demand → supply check → review task.');

 notes(s,"AIIMSMangalagiri verifiedofficialinstitutionlocation. Photo fromMoHFWPMSSY. Institutionalactivitypatterns motivate use-specificmodel; no waterconsumptionfigureorMTMCutilityconnectionclaim. Source officialhospitalservicesincludeslaundryandclinicalservices.",["https://www.aiimsmangalagiri.edu.in/aboutus/about-us/", "https://www.aiimsmangalagiri.edu.in/hospital-services/", "https://pmssy.mohfw.gov.in/WriteReadData/p92g8/38945360461691993957.jpg"]);
}


{

 const s=slide('New homes change the forecast by move-in year');kicker(s,'Local example: Navuluru township');
 await img(s,'navuluru-layout.png', 68,178,712,441,'Official APCRDA proposed township layout');
 txt(s,'Count occupied homes',838,222,363,54,34,C.teal,true);
 txt(s,'Add people by year',838,345,363,54,34,C.teal,true);
 txt(s,'Check supply for that year',838,468,363,54,34,C.teal,true);
 caption(s,'Forecast design: homes × people × daily use. Flag the year supply falls short.');

 notes(s,"APCRDA officialproposedAmaravatiTownshipMIGLayoutpage1 showsE13/E14/E15andExistingNowuluruRoad.10Feb2022tenderforNavuluruMTMC80.46acres includeswater,sewerage/STP,roads. Plan/tenderdoesnotestablishfinishedoroccupiedhomes. Usephasedoccupationandconnectiondatesforfutureuse.",["https://crda.ap.gov.in/crda_norifications/NOT01098148/01~Amaravati%20Township%20MIG%20Layout.pdf", "https://crda.ap.gov.in/APCRDADOCS/GOSACTSRULES/Other%20Notifications/Tender%20Notice%20for%20Development%20of%20Jagananna%20Smart%20Township.pdf"]);
}


// 04: Explicitly requested editable demand-driver composition.
{

 const s=slide('Each type of use gets its own calculation');kicker(s,'Local demand drivers');
 const xs=[68,361,654,947];
 city(s,81,210,.65);
 building(s,378,270,182,98,C.teal);rect(s,451,242,36,36,C.orange);
 building(s,674,266,178,102,C.teal);rect(s,688,211,21,73,C.teal);rect(s,744,231,21,48,C.teal);
 for(let c=0;c<3;c++){rect(s,967+c*59,224,46,143,C.teal);for(let r=0;r<5;r++){rect(s,974+c*59,233+r*24,31,14,C.pale);circle(s,994+c*59,237+r*24,5,C.orange);}}
 ['Homes','Institutions','Industry','Data centres'].forEach((t,i)=>txt(s,t,xs[i],409,282,52,32,C.teal,true));
 ['Dwellings + people','Students + operating days','Process + production','IT load + cooling'].forEach((t,i)=>txt(s,t,xs[i],470,280,78,25,C.ink));
 rect(s,68,568,1152,61,C.pale);txt(s,'Count each use. Apply a local use rate. Add the results.',83,580,1120,42,30,C.teal,true);
 caption(s,'Forecast design: add each site to its service area, then compare the total with supply.');


 notes(s,'Non-domestic categories use evidence-appropriate activity variables, not one universal population/LPCD multiplier. The listed variables are candidates; no locally fitted water intensity or data-centre coefficient exists. A data-centre model requires technology and cooling-system information as well as verified load, utilization and location; proposed sites are scenario inputs only after evidence of service linkage. Proposed driver framework, not fitted local effects. Domestic demand uses households/population and occupancy. Footprint alone is a proxy, not occupied floor area or water use. Non-domestic demand uses metered categories and activity evidence. Airport passenger counts, hotel nights and economic indicators are candidate proxies only where a physical service-area or catchment link and incremental predictive value can be demonstrated. Do not assign airport demand to MTMC merely because an airport is nearby. Avoid double counting employees, visitors, airport users and household demand. Land-use permission does not prove construction or occupation. Source shortages and restrictions can suppress delivered/billed water below unconstrained demand. Network losses increase required production and do not represent consumer consumption. Every input needs owner, observation date, units, spatial extent and an observed/proxy/assumed/missing classification.', ['orientation','method']);
}

{

 const s=slide('Growth near new links changes the area forecast');kicker(s,'Roads and airport access');
 await img(s,'vijayawada-airport.jpg',68,177,735,465,'Official AAI photograph of Vijayawada Airport at Gannavaram');
 txt(s,'Map new homes\nand workplaces',856,210,365,95,35,C.teal,true);
 txt(s,'Add their expected\nuse by year',856,373,365,98,35,C.teal,true);
 txt(s,'Check the system\nthat serves them',856,530,365,92,29,C.ink);
 caption(s,'Vijayawada Airport is at Gannavaram, outside MTMC. Its local effect must follow an actual service link.');

 notes(s,"AAI officialairportpage listsGannavaram,KrishnaDistrict.NH16providesregionalaccess. AirportisoutsideMTMC: nophysicalMTMCwatersupplyconnectionorpassenger-to-waterconversionclaim. Connectivitymaychangehousing/businesslocation butrequiresdocumentedservicecatchmentandlocalobservation. E15extensiontoOldNationalHighwayMangalagiri hadawardauthorization9May2025GO411; nooperationclaim.",["https://www.aai.aero/en/airports/vijayawada", "https://www.aai.aero/sites/default/files/airport-photo-fallery/City_side_1.jpg", "https://crda.ap.gov.in/APCRDADOCS/GOSACTSRULES/APCRDA/01~15582025MAUD_RT411_E.pdf"]);
}


{

 const s=slide('Seasonal changes affect the supply gap');kicker(s,'Heat, rain and seasons');
 circle(s,121,211,118,C.orange);for(let i=0;i<5;i++)rect(s,101+i*42,348,18,66-i*8,C.orange);
 circle(s,552,224,104,'#B9CCEA');circle(s,614,196,136,'#B9CCEA');circle(s,698,233,94,'#B9CCEA');rect(s,588,275,150,44,'#B9CCEA');for(let i=0;i<5;i++)rect(s,580+i*37,344,7,48,C.teal);
 rect(s,955,290,237,112,'#B9CCEA');for(let i=0;i<3;i++)rect(s,970,313+i*25,208,5,C.teal);
 txt(s,'Hot months',77,455,330,53,36,C.teal,true);txt(s,'Rain',507,455,300,53,36,C.teal,true);txt(s,'Water sources',907,455,319,53,36,C.teal,true);
 txt(s,'Can raise water use',77,522,350,54,27);txt(s,'Can change supply',507,522,350,54,27);txt(s,'Can change with climate',907,522,318,79,26);
 caption(s,'Forecast design: estimate monthly use and available supply. Flag months with a gap.');

 notes(s,"Conceptualweatherdrivers. Donotassumeauniformrainfalldemandorsupplyeffect. Rainmaychangerecharge,runoff,qualityandoperations; sourceyieldhasitsownhydrologicuncertainty. Long-rangeclimatestresscases differfrommonthlyweatherforecast. No localcoefficientsestimated.",["method"]);
}


// 05: System view and mass-balance distinctions.
{
 const s=slide('How we estimate future water needs',true);kicker(s,'Forecast design');
 const xs=[56,363,670,977], titles=['Read','Match','Estimate','Check'];
 for(let i=0;i<4;i++){
  circle(s,xs[i],183,68,i===3?C.lime:C.teal);txt(s,String(i+1),xs[i]+19,186,40,59,39,i===3?C.dark:C.white,true);
  txt(s,titles[i],xs[i],278,245,55,35,C.white,true);if(i<3)arrow(s,xs[i]+116,207,122,'#566879');
 }
 ['Readings\nConnections','Units\nService areas','Simple maths\nLocal patterns\nAI if it improves','Ranges\nEngineer sign-off'].forEach((t,i)=>txt(s,t,xs[i],350,275,126,26,'#C2CCD6'));
 rule(s,56,504,1168,'#445565');
 txt(s,'DOMESTIC USE',56,543,355,38,23,C.lime,true);txt(s,'People × use rate',56,588,355,52,30,C.white);
 txt(s,'SYSTEM INPUT',463,543,355,38,23,C.lime,true);txt(s,'Use ÷ (1 − loss share)',463,588,390,52,29,C.white);
 txt(s,'SEWAGE',918,543,305,38,23,C.lime,true);txt(s,'Use × return fraction',918,588,310,52,27,C.white);
 caption(s,'Use local readings to check the estimate. Add ward results to get the city total.',true);

 notes(s,'Conceptual proposed architecture. Candidate monthly regression: service-zone effect + month-of-year terms + lagged consumption (e.g. 1 and 12 months) + verified connection/occupancy/weather/policy drivers. Compare against seasonal-naive and per-connection baselines with rolling backtests; coefficients are not fitted here. Daily rates in litres per day convert to MLD by division by 1,000,000. For system input, use total authorised consumption divided by (1 minus water-loss share of system input). NRW also includes authorised unbilled consumption, so NRW and physical leakage are not interchangeable. If using NRW share instead, the numerator is billed authorised consumption. Reconcile the full water balance and never count losses twice. Sewerage generation, collected sewer flow and STP treatment load are separate quantities. Apply the return fraction to relevant consumer use including verified private borewell/tanker contributions; the extra term in the diagram represents their sewer-return contribution, not their entire supplied volume. Account separately for sewer connection coverage, infiltration/inflow and non-returning uses. The prototype defaults to 135 LPCD and an 80% return assumption from the orientation; these are planning assumptions, not measured use or a universal regulatory determination.', ['orientation','code','method','MoHUA/ASCI, Guidance Notes on Preparation of DPR for Drink-from-Tap, supplied attachment Guidance-Notes-on-Preparation-of-DPR.pdf, PDF page 71 (printed page 19). Separates consumer-end LPCD, losses, non-domestic bulk use and floating population. Its example 15% losses is not an MTMC observation. Local source: C:/Users/basit/Downloads/Guidance-Notes-on-Preparation-of-DPR.pdf.']);
}

// 07: Transparent editable scenario chart, deliberately normalized.
{
 const s=slide('Compare slower and faster growth');kicker(s,'Illustrative sensitivity');
 txt(s,'Example: water need starts at 100.',56,145,1100,45,23,C.muted);
 const horizons=[0,1,2,3,4,5];
 const cases=[{name:'Lower',h:0,u:-.01,color:C.blue},{name:'Central',h:.015,u:0,color:C.teal},{name:'Higher',h:.03,u:.01,color:C.orange}];
 const chart=s.charts.add('line',{position:{left:56,top:217,width:818,height:391},categories:horizons.map(y=>y===0?'Base':`Year ${y}`),series:cases.map(c=>({name:c.name,values:horizons.map(y=>Number((100*((1+c.h)*(1+c.u))**y).toFixed(3))),line:{fill:c.color,width:4},marker:{symbol:'circle',size:6}})),hasLegend:true,legend:{position:'bottom',textStyle:{typeface:FONT,fontSize:20,fill:C.ink}},xAxis:{textStyle:{typeface:FONT,fontSize:18,fill:C.muted}},yAxis:{min:90,max:130,majorUnit:10,numberFormatCode:'0',textStyle:{typeface:FONT,fontSize:18,fill:C.muted},majorGridlines:{fill:C.line,width:1}},chartFill:C.paper,plotAreaFill:C.paper});
 applyPresentationChartFont(chart,{fontFamily:FONT});
 txt(s,'Annual inputs',916,226,310,42,29,C.ink,true);
 txt(s,'Lower\nHouseholds 0%, use −1%',916,296,310,77,24,C.blue);
 txt(s,'Central\nHouseholds +1.5%, use 0%',916,392,310,77,24,C.teal);
 txt(s,'Higher\nHouseholds +3%, use +1%',916,488,310,77,24,C.orange);
 txt(s,'Illustrative growth choices. Local readings set the final rates.',56,650,1120,34,19,C.muted);
 notes(s,'Entire chart is a hypothetical sensitivity demonstration, not observed MTMC data. Index(t)=100×[(1+annual household growth)×(1+annual per-household use change)]^t. Household size and non-domestic mix are held constant for this illustration. Lower: 0% household growth and -1% use, year5 95.099. Central: +1.5% and 0%, year5 107.728. Higher: +3% and +1%, year5 121.841. Display rounding does not imply measured precision. No probabilities attach to these paths. For the operational model, separate parameter/project scenarios from empirical prediction intervals calibrated on backtest residuals. Revise the paths when occupancy, tariffs, weather or project dates change. Long-range 2041/2056 planning should use explicit development scenarios, not extrapolate this five-year example mechanically.', ['method']);
}

// 08: Product evidence, concise annotation outside screenshot.
{
 const s=slide('See water and sewage for each ward');kicker(s,'Current prototype');
 await img(s,'demand-desktop-20260905.png',56,150,794,480,'Actual local Ward 40 demand calculation at the 2041 horizon');
 rect(s,905,163,319,439,C.dark);
 txt(s,'WARD 40 / 2041',927,185,274,39,23,C.lime,true);
 txt(s,'1.26',924,249,275,101,78,C.white,true);
 txt(s,'MLD water',931,349,270,44,28,C.white);
 txt(s,'1.01',924,419,275,91,68,C.white,true);
 txt(s,'MLD sewage',931,516,270,44,28,C.white);
 txt(s,'Compare with available supply',913,608,317,40,22,C.orange,true);
 caption(s,'Ward 40, 2041. Planning estimate from the supplied population workbook.');

 notes(s,'Fresh local demand screen captured after using actual controls to select Ward 40, year 2041 and comparison mode. Displayed numbers are calculations from cached workbook projections, not measured current consumption or a fitted forecast. Ward 40 displayed 9367 planning population, 1.26 MLD water and 1.01 MLD sewage at the selected horizon. No synthetic readings were inserted. The current app brand is NEERU LEKKA in this same Smart Utility checkout. Screenshot provenance recorded in docs/presentation/SOURCES_AND_READINESS.md; this proves the inspected local state only.', ['code','workbook','Captured 5 September 2026: http://127.0.0.1:5197/dashboard/demand?ward=40&year=2041&horizons=compare . Browser viewport 1440 x 1000; unaltered content crop x260 y120 width1130 height790. Data class: source-derived planning projection. The same state was inspected at 390 x 844 with no page-level horizontal overflow. Related preserved source PDF: output/innovation-for-her-draft-pack.pdf.']);
}

{

 const s=slide('How much more must the area provide?');kicker(s,'Need compared with supply');
 txt(s,'Future need',68,205,420,72,51,C.teal,true);txt(s,'Available supply',687,205,540,72,51,C.teal,true);
 rect(s,80,327,438,123,C.teal);rect(s,714,327,315,123,C.green);
 txt(s,'−',565,335,88,100,67,C.orange,true);
 txt(s,'The gap',68,527,440,69,48,C.teal,true);arrow(s,426,550,138,C.orange);
 txt(s,'Work to add supply, reduce losses\nor improve treatment',609,514,610,104,32,C.ink,true);
 caption(s,'Compare the same area and date. Bars show the idea, not measured MTMC amounts.');

 notes(s,"Conceptualdiagramonly,barlengthsareillustrativeandnottiedtoMTMCvalues. Compareconsumerusewithsupplyonlyafterreconcilingproductionlosses,servicearea,timebasisandsource. SewagetreatmentchecksusecollectedflowandverifiedusableSTPcapacity.StoragevolumecannotbesubtracteddirectlyfromMLD. Unknowncapacityisanevidencecheck,notfictitiousnumericalgap.",["method"]);
}


{

 const s=slide('A clear task for each gap');kicker(s,'Task screen design');
 await img(s,'task-screen-design.png',56,161,1168,488,'Generated design for future task feature, with Ward 40 example');

 notes(s,"AI-generated task-screen design requested by user. Notanactualapplicationcaptureorproof ofimplementedtaskassignmentorclosure. ExacttaskcontentusesWard40asdesignexamplewithduedatetobeset; no realassignmentmade. Imagecreatedwithbuiltinimagegenerationtool5September2026.",[]);
}


// 11: No dense raw table: four timed intake bands.
{

 const s=slide('Four roles keep the work clear',true);kicker(s,'Decision and accountability');
 txt(s,'Water and sewage need',56,156,500,55,37,C.white,true);
 txt(s,'−',555,157,58,60,43,C.lime,true);
 txt(s,'What the area can provide',631,156,599,55,37,C.white,true);
 txt(s,'Compare the same area, date and units',56,229,1150,45,25,'#ADBDCC');
 rule(s,56,301,1168,'#465563');
 const xs=[56,363,670,977];
 const roles=['State planner','Local body','Ward operator','Dept. reviewer'];
 const acts=['Set priorities','Give the task','Check on site','Accept or return'];
 for(let i=0;i<4;i++){
 circle(s,xs[i],341,56,i===3?C.lime:C.teal);txt(s,String(i+1),xs[i]+13,341,43,51,32,i===3?C.dark:C.white,true);
 if(i<3)arrow(s,xs[i]+100,358,142,'#526777');
 txt(s,roles[i],xs[i],431,257,49,28,C.white,true);
 txt(s,acts[i],xs[i],497,257,80,28,i===3?C.lime:'#C2CCD6');
 }
 txt(s,'Each task says who, when, what to check and the result.',56,591,1130,48,31,C.white);
 caption(s,'Task flow design: the person doing the work and the person reviewing it are separate.',true);


 notes(s,'User-provided evaluator discussion and role clarification, 5 September 2026. Four business roles suffice for the proposed pilot: state planner oversees priorities and cross-ULB allocation; local-body planner scopes and assigns interventions; ward operator supplies field evidence and updates; departmental reviewer verifies findings and accepts or returns closure. These are responsibilities, not proof of implemented authorization. Keep the person closing a task distinct from its evidence submitter. A technical administrator can be an operational permission rather than a fifth planning persona. Capacity gap must compare like-for-like service area, horizon, time basis and flow definition; water consumer use must be reconciled to production input, and sewage generation to collected/treatment load. Unknown capacity produces an evidence request, not a fabricated numerical gap. Task statuses can be proposed, assigned, evidence submitted, accepted or returned; no automatic public-infrastructure control is claimed. Proposed data contract. Consumption: monthly/daily volume, source and billing basis, unit, period, meter identifier or approved aggregate zone, active connections, meter replacement/reset, missingness and corrections. Spatial: current boundary versions and effective dates, approved crosswalk from 50 workbook wards to 65 GIS boundaries, service zones, building footprint/use/floors/occupancy and connection links. Operations: source abstraction/availability, bulk inflow/outflow, authorised billed/unbilled consumption, audited NRW components, supply hours/outages, WTP/storage/STP design and usable capacity, commissioning dates, sewer connections and flow. Exogenous: local weather, industrial/commercial activity and metered categories, hotel occupancy/tourism seasonality, airport passenger counts only with demonstrable catchment linkage, tariffs/restriction dates and project/phasing/occupation evidence. Refresh proposal: monthly consumption/connections and model run; daily operational/weather inputs where available; quarterly building/use review; project and policy updates on approved change. Retain original observations, derived values, provenance and versioned assumptions. Annual-only totals support annual scenarios and cannot establish monthly seasonality.', ['method','orientation']);
}

// 10: Forecast evaluation, native time split diagram.
{
 const s=slide('Check the forecast against real readings');kicker(s,'How we test');
 txt(s,'Past readings',56,160,500,43,28,C.teal,true);txt(s,'Months to check',867,160,360,43,28,C.orange,true);
 const x=240,cell=70,gap=5;
 for(let r=0;r<3;r++){
  txt(s,['Test 1','Test 2','Test 3'][r],56,248+r*104,177,47,25,C.ink,true);
  for(let c=0;c<13;c++)rect(s,x+c*(cell+gap),235+r*104,cell,60,c<7+r?C.teal:c<10+r?C.orange:C.pale);
 }
 txt(s,'Learn from past months',240,555,535,41,27,C.teal,true);
 txt(s,'Check against real results',845,555,380,41,27,C.orange,true);
 caption(s,'Check errors by area, by season and during peak use. Keep the model only if it improves the result.');

 notes(s,'Proposed evaluation plan. Prefer at least 36 monthly observations, with 60 desirable, subject to seasonal coverage and data quality; these are requested data windows, not universal sufficiency thresholds. Use expanding/rolling origins with horizon-matched held-out blocks; no random time split. Start with seasonal naïve (same month previous year), trend/seasonal regression and population/use-rate baselines. Introduce lagged consumption (e.g. 1 and 12 months), weather, connection counts and verified drivers only if out-of-sample gains persist. Future weather must use information available at forecast time. Measure MAE in MLD and signed bias; WAPE is sum absolute error divided by sum actual use and needs nonzero aggregate actual use. Validate boundary changes and sparse wards separately. Calibrate empirical intervals and report coverage plus width. Fit and select without looking at final test outcomes. No numeric acceptance threshold or performance result has been agreed or achieved.', ['method']);
}

{

 const s=slide('Pilot delivery plan');kicker(s,'A schedule to agree with MTMC');
 const yy=[184,294,404,514],ph=['Weeks 1–2','Weeks 3–5','Weeks 6–7','Weeks 8–9'],work=['Visit the office. Agree one test area.','Build local estimates. Test growth options.','Connect tasks. Check roles and records.','Review results with MTMC. Set the next steps.'];
 for(let i=0;i<4;i++){rect(s,72,yy[i],246,74,i%2?C.teal:C.orange);txt(s,ph[i],88,yy[i]+13,220,48,29,i%2?C.white:C.ink,true);txt(s,work[i],375,yy[i]+8,826,62,31,C.ink);if(i<3)rect(s,188,yy[i]+80,5,23,C.line);}
 caption(s,'Proposed pilot schedule. Final dates will be agreed during the office visit.');

 notes(s,"Planningproposal,notaschedulealreadyacceptedbyMTMC,norcompletedwork. Durationsareworkingassumptionsandwilldependonscopeandaccess. Noappointment,emailorportalactionperformed. Fourrolesremainstateplanner,localbodyplanner,wardoperator,departmentreviewer.",[]);
}


{

 const s=slide('What MTMC can check in the review');kicker(s,'Meeting checklist');
 const items=['People and floors','Homes, shops and workplaces','Future growth by area','Water and sewage needs','Supply gaps','Tasks and review'];
 items.forEach((t,i)=>{let x=73+(i%2)*599,y=187+Math.floor(i/2)*147;rect(s,x,y,552,106,C.pale);rect(s,x+23,y+30,34,34,C.white);rect(s,x+23,y+30,34,3,C.orange);rect(s,x+23,y+61,34,3,C.orange);rect(s,x+23,y+30,3,34,C.orange);rect(s,x+54,y+30,3,34,C.orange);txt(s,t,x+81,y+24,442,64,31,C.teal,true);});

 notes(s,"Reviewchecklistcoveringevaluatorfeedback. Emptycheckboxesinviteassessmentanddonotclaimallfeaturesimplementedoraccepted. Technicaldetailsandprovenanceareinspeakernotes.",[]);
}


// 12: Specific pilot decision and clear release boundary.
{

 const s=slide('Visit NEERU LEKKA',true);kicker(s,'Prototype and team',true);
 qrCode(s,79,193,306);
 txt(s,'neerulekka.wedevit.in',78,522,580,57,35,C.white,true);
 txt(s,'Login: neerulekka.wedevit.in/login',78,588,640,48,27,'#CFDFF5');
 rect(s,702,179,522,62,C.white);txt(s,'WEDEVIT PRIVATE LIMITED',712,184,506,52,29,'#CB1723',true);
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
const unlinkedPath=path.join(BUILD,'candidate-unlinked.pptx');
await (await PresentationFile.exportPptx(p)).save(unlinkedPath);
execFileSync(path.join(RUNTIME,'python/python.exe'),[path.join(ROOT,'docs/presentation/add-slide-links.py'),unlinkedPath,candidatePath],{stdio:'inherit'});
await fs.writeFile(path.join(BUILD,'deck-content.ndjson'),(await p.inspect({kind:'slide,textbox,chart,notes',maxChars:200000})).ndjson);
const finalPath=path.join(ROOT,'docs/presentation',process.env.SMART_UTILITY_OUTPUT || 'NEERU_LEKKA_MTMC_Presentation.pptx');
const result=await finalizePresentation({workspaceDir:ROOT,candidatePath,finalPath,pythonExecutable:path.join(RUNTIME,'python/python.exe'),integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],explicitTotalSlideCount:20,requiredNativeChartOwnerSlides:[12],requiredNativeTableOwnerSlides:[],materializeLiteralChartWorkbooks:true,fontPolicy:{basis:'reference',families:[FONT,MONO,'Nirmala UI'],referencePath:'C:/Users/basit/Downloads/CODE/PPT/protohub-deck-visual-final--8466f13c9299e91c.pptx',referenceSha256:createHash('sha256').update(await fs.readFile('C:/Users/basit/Downloads/CODE/PPT/protohub-deck-visual-final--8466f13c9299e91c.pptx')).digest('hex')},verifyArtifactToolImport:true,receiptPath:path.join(BUILD,path.basename(finalPath)+'.validation.json')});
console.log(JSON.stringify({font:FONT,...result},null,2));
const final=await PresentationFile.importPptx(await FileBlob.load(finalPath));
await fs.mkdir(path.join(BUILD,'renders'),{recursive:true});
for(let i=0;i<slideCount;i++){
 const blob=await final.export({slide:final.slides.getItem(i),format:'png',scale:1});
 await fs.writeFile(path.join(BUILD,'renders',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await blob.arrayBuffer()));
}
console.log('Rendered all final slides.');


