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
const palettes=[
 ['#5328A7','#F4BB36','#F1E8FF','#28154E'],['#642AC0','#FFD255','#EEE2FF','#381269'],
 ['#006C7C','#E2467A','#DCF5F5','#06414B'],['#1848B8','#E43973','#E4EDFF','#16367C'],
 ['#9B3B12','#C03268','#FFE7CE','#58270F'],['#006D62','#AA2580','#D9F2EB','#07493F'],
 ['#954315','#B12776','#FFE9C9','#5B2916'],['#6A2EB2','#C32964','#EEE2FC','#382052'],
 ['#1856A9','#A02D9E','#E0F0FF','#153D72'],['#97471C','#8136B4','#FFF0CE','#5D3119'],
 ['#3D3BBD','#EDB93E','#E7E7FF','#242157'],['#6532AC','#C42D77','#EDE4FC','#3D225A'],
 ['#006D80','#BD245F','#DDF3F7','#074B58'],['#A12F48','#7142C4','#FBE3E9','#5C2230'],
 ['#3C3BAB','#BE2C78','#E7E8FF','#232356'],['#00685F','#9D2AA7','#DFF4EC','#064A43'],
 ['#6C35A2','#B72F61','#F0E5F8','#422251'],['#A54017','#7637A8','#FFE9D7','#602D18'],
 ['#185DA4','#B02E7E','#E2F0FC','#123E6B'],['#6C2CAE','#E7B735','#F0E5FA','#341947']];
function slide(title,dark=false){const s=p.slides.add();const q=palettes[(slideCount++)%palettes.length];C.teal=q[0];C.orange=q[1];C.lime='#FFD465';C.label=dark?'#FFD465':q[0];C.pale=q[2];C.dark=q[3];s.background.fill=dark?q[3]:q[2];rect(s,0,0,1280,12,q[0]);if(title)txt(s,title,64,72,1150,100,42,dark?C.white:q[0],true);txt(s,String(slideCount).padStart(2,'0'),1170,679,60,26,14,dark?'#DACEEB':C.muted);return s;}
function rounded(s,x,y,w,h,fill){return s.shapes.add({geometry:'roundRect',position:{left:x,top:y,width:w,height:h},fill,line:{fill:'none',width:0}});}
async function phone(s,file,x,y,w,h){rounded(s,x+7,y+10,w,h,'#B6BBC5');rounded(s,x,y,w,h,'#171B29');rect(s,x+8,y+18,w-16,h-36,C.white);await img(s,file,x+9,y+28,w-18,h-61,'Real NEERU LEKKA mobile screen');rounded(s,x+w*.34,y+9,w*.32,10,'#171B29');rect(s,x+w*.35,y+h-14,w*.3,3,C.white);}
async function screen(s,file,x,y,w,h,alt){rounded(s,x+6,y+8,w,h,'#C1C5CD');rounded(s,x,y,w,h,C.white);rect(s,x+12,y+12,w-24,20,'#E8EAF0');txt(s,'neerulekka.wedevit.in',x+27,y+12,w-50,21,12,C.muted);await img(s,file,x+10,y+41,w-20,h-51,alt);}
function flow(s,rows,x=820,y=185,w=390){rows.forEach((row,i)=>{const yy=y+i*150;rect(s,x,yy,5,119,C.orange);txt(s,['INPUT','NEERU LEKKA','MTMC ACTION'][i],x+20,yy,w-24,29,17,C.teal,true,MONO);txt(s,row,x+20,yy+37,w-25,82,27,C.ink,i===2);});}

function kicker(s,label,dark=false){const t=txt(s,label.toUpperCase(),68,37,1140,31,16,C.label,true,MONO);}
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
 txt(s,'Water and sewage planning\nfor Mangalagiri–Tadepalli',68,255,522,114,36,C.white);
 await img(s,'user-selected-city-map.png',624,256,604,342,'User-selected oblique city map with mapped asset locations');
 qrCode(s,70,427,180);
 txt(s,'OPEN THE PROTOTYPE',267,440,360,34,18,C.orange,true,MONO);
 txt(s,'neerulekka.wedevit.in',267,482,358,43,26,C.white,true);
 txt(s,'Mangalagiri–Tadepalli',267,537,358,42,25,'#CFDFF5');
 rect(s,68,634,529,43,C.white);txt(s,'WEDEVIT PRIVATE LIMITED',70,635,525,36,23,'#CB1723',true);
 txt(s,'DPIIT-recognised startup  DIPP266651',651,638,578,35,23,'#CFDFF5');


 notes(s,'Cover visual supplied directly by the user on 5 September 2026: codex-clipboard-28469776-237e-41da-96f4-1381e79c3006.png, embedded without pixel edits. Capture route, viewport and underlying point classes were not independently provided for this image; it illustrates visible urban form and mapped locations, not current capacity or measured consumption. QR encodes exactly https://neerulekka.wedevit.in/ as requested. Submission date is the team preparation date, not an organizer deadline. This deck describes an evidence-review prototype and a proposed demand forecasting system. Smart Utility Demand Forecasting is the official challenge name; NEERU LEKKA is the current product brand in this repository; WEDEVIT PRIVATE LIMITED is the legal company name. The cover uses the supplied oblique map screenshot; it is not a master-plan PDF rendering.', ['challenge','https://www.wedevit.in/doc and linked https://www.wedevit.in/documents/CIN.pdf, inspected 5 September 2026. Company-published incorporation certificate copy names WEDEVIT PRIVATE LIMITED.']);
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
 const s=slide('The department needs an area-wise capacity plan');kicker(s,'The planning problem');
 await img(s,'selected-map.png',593,180,627,387,'User-selected prototype map and planning chart');
 txt(s,'MTMC plans water supply and sewage treatment.',65,190,487,124,34,C.teal,true);
 txt(s,'New homes and businesses add demand in different places.',65,349,487,136,31,C.ink);
 txt(s,'The decision: which area needs more capacity, and by which year?',65,526,1127,95,37,C.teal,true);
 caption(s,'MLD means million litres per day. Area-to-network links are part of the proposed forecast workflow.');
 notes(s,'Meeting feedback: mapped inventory isuseful,butuniformallocation doesnotcapturelocaloccupiedfloors,nondomesticactivityandfuturedevelopment. This slide introduces theplanningquestion forfirst-time readers.',['orientation']);
}

{

 const s=slide('Sign in and choose your department role');kicker(s,'Prototype login');
 txt(s,'State planner',69,204,480,56,36,C.teal,true);
 txt(s,'Local body planner',69,303,480,56,36,C.teal,true);
 txt(s,'Ward operator',69,402,480,56,36,C.teal,true);
 txt(s,'Department reviewer',69,501,490,65,36,C.teal,true);
 await phone(s,'selected-phone-login.png',647,164,235,498);
 await phone(s,'spatial-mobile-20260905.png',930,198,217,461);
 txt(s,'Login: neerulekka.wedevit.in/login',66,655,560,35,22,C.teal,true);
 notes(s,'User-selected phone login screenshot used unchanged inside phoneframe. Otherphoneusesrealmobilemap. PreviewrolesnotproductionRBACproof. NoAi4everyonebrandingorcontentused; onlydevicecompositioninspiration.', ['code']);

}

{

 const s=slide('The dashboard brings the planning figures together');kicker(s,'Current prototype');
 await screen(s,'selected-dashboard.png',63,173,1154,440,'User-selected MTMC water desk dashboard');
 txt(s,'Choose a year. Read the estimate. Open the map or capacity page.',70,642,1124,54,29,C.teal,true);
 notes(s,'Userprovidedactualdashboard.2026grosspopulation331000,135LPCD water44.685MLD,80percentsewage35.748MLD. Workbook45.22MLDcachedsewage isaseparatecalculation; reportedAPPCB19.2generationand0.4treatment aredatedsnapshot,notcomparabledemanddefinitions.',['workbook','code']);

}

{

 const s=slide('Select the area on the map');kicker(s,'Map in the prototype');
 await screen(s,'selected-map.png',64,186,794,450,'Actual GIS asset map');
 flow(s,['Find the building, ward or water asset.','Keep the area with its demand estimate.','Check which supply and sewer network serves it.'],895,190,320);
 notes(s,'Actual preserved local spatial screenshot, source layers 2017–2019. Future forecast service-area links require a reviewed match; visual map alone does not establish a service connection. Proposed decision chain is introduced without claiming a working integrated task engine.',['gis','code']);

}

{

 const s=slide('Occupied floors change the demand estimate');kicker(s,'Building example');
 await img(s,'building-water-example.png',36,184,744,414,'Illustrative one-floor and five-floor building comparison');
 txt(s,'1 floor: 16 people',820,188,397,50,31,C.teal,true);
 txt(s,'5 floors: 80 people',820,255,397,50,31,C.teal,true);
 txt(s,'Extra water',820,339,397,36,23,C.ink);
 txt(s,'8,640 L/day',820,378,397,68,46,C.teal,true);
 txt(s,'Extra sewage: 6,912 L/day',820,454,397,78,28,C.ink,true);
 rect(s,65,592,1150,48,C.teal);txt(s,'NEERU LEKKA adds this demand to the building’s service area.',83,600,1116,38,26,C.white,true);
 caption(s,'Forecast design example: 4 homes/floor, 4 people/home, 135 L/person/day, 80% return.');
 notes(s,'Illustrative assumptions, not observed local building demand. One floor16 people2160L/day; fivefloors80people10800L/day; difference8640. Sewage increment6912 at80percent. Image generated, genericarchitectural cutaway not realsite. This slide describes proposed building-input extension.',['method','orientation']);

}

{

 const s=slide('Hospital activity feeds a separate site estimate');kicker(s,'AIIMS Mangalagiri');
 await img(s,'aiims-mangalagiri.jpg',64,198,709,289,'Official AIIMS Mangalagiri campus photo');
 txt(s,'Patients, staff, cleaning and laundry',65,517,700,90,34,C.teal,true);
 flow(s,['Record site use, readings and water sources.','Calculate site water use and sewage return.','Review supply and treatment for the site.']);
 caption(s,'Forecast design. Official hospital image; no measured site demand is claimed.');
 notes(s,'Official localinstitution photo. Site-specific water calculation is proposed: no assumedMTMC connection, no measuredAIIMSconsumption. Source retained from prior slide.',['https://pmssy.mohfw.gov.in/WriteReadData/p92g8/38945360461691993957.jpg','https://www.aiimsmangalagiri.edu.in/hospital-services/','method']);

}

{

 const s=slide('Move-in dates set when new demand starts');kicker(s,'Navuluru township plan');
 await img(s,'navuluru-detail.png',64,177,704,460,'Official township layout');
 flow(s,['Count homes and planned move-in dates.','Add occupied homes to each year’s estimate.','Review connections before that demand arrives.']);
 caption(s,'Forecast design. A published layout shows planned homes, not current occupation.');
 notes(s,'APCRDA officialNavuluruMIG layout andtender, notcompleteddevelopment. Occupancy/phasingfeeds futurebuildingpopulation. No arithmeticallocation bylandarea.',['https://crda.ap.gov.in/crda_norifications/NOT01098148/01~Amaravati%20Township%20MIG%20Layout.pdf','method']);

}

{

 const s=slide('Each site uses a matching water calculation');kicker(s,'Shops, schools, industry and data centres');
 await img(s,'user-selected-city-map.png',64,178,704,405,'User-supplied building and road map');
 txt(s,'Attach the use type to the mapped site.',66,594,708,49,29,C.teal,true);
 flow(s,['Enter people, work hours, production or cooling use.','Add each site’s demand to its service area.','Check large users before sizing supply and sewage works.']);
 notes(s,'Proposed nondomesticdriver extension. Map is userprovided, no individualsiteclassification inferredfromdots. Schools useattendance andoperatingdays; industry processmetering; datacentrescoolingsystemandverifiedITload. Do notuseuniversalLPCD orassumea datacentre exists.',['method','orientation']);

}

{

 const s=slide('New road links change where growth is placed');kicker(s,'Road and airport access');
 await img(s,'vijayawada-airport.jpg',64,185,704,435,'Official AAI Vijayawada airport photograph');
 flow(s,['Locate planned homes and workplaces near new links.','Place their demand in the area that serves them.','Review that area’s supply and sewer capacity.']);
 caption(s,'Forecast design. Vijayawada Airport is outside MTMC; use verified service links.');
 notes(s,'Airport is atGannavaram, outsideMTMC. Noairport-to-MTMCwaterconnectionorpassengerlitreconversionclaimed. Regionalaccessmayaffectfuturehomes/businesses.',['https://www.aai.aero/en/airports/vijayawada','https://www.aai.aero/sites/default/files/airport-photo-fallery/City_side_1.jpg','method']);

}

{

 const s=slide('Weather helps test months with less spare supply');kicker(s,'Real weather screen; forecast link design');
 await screen(s,'weather-screen-crop.png',64,175,704,460,'Live prototype weather panel');
 flow(s,['Read heat, rain and available source water.','Test monthly use against monthly supply.','Plan field checks for months with a gap.']);
 notes(s,'Live weather page captured5Sept2026 includesgovernmentpointobservations andOpenMeteogriddeddata. These are distinctdatafeeds, notvalidatedwardrainfallorwater-demandforecast. Proposedlink tofuturemodel requiresbacktests.',['https://neerulekka.wedevit.in/dashboard/climate','method']);

}

{

 const s=slide('The model places growth, then calculates demand');kicker(s,'Growth map in the prototype');
 await screen(s,'selected-growth.png',64,179,722,466,'Real live growth allocation map');
 txt(s,'People × daily use',831,205,390,71,36,C.teal,true);
 txt(s,'Add shops and other site use',831,312,385,90,31,C.ink,true);
 txt(s,'Calculate sewage return',831,443,390,88,31,C.ink,true);
 txt(s,'Compare with capacity',831,565,390,71,31,C.teal,true);
 caption(s,'Current map uses growth assumptions. Local site inputs extend that calculation.');
 notes(s,'Livegrowthmap screenshot5Sept2026. Existinglogistic/geometric/arithmeticallocation isheuristic andpreservesworkbooktotal; nottrainedforecast orapproved50-to65wardcrosswalk. Localdriversareproposedextensions. CandidateAItestedagainstsimplemathusingheldoutreadings,notclaimedtrained.',['code','method','https://neerulekka.wedevit.in/dashboard/growth']);

}

{

 const s=slide('The future model uses local inputs for each area');kicker(s,'Proposed mathematical model');
 const labels=['People','Water use','Sewage','Capacity gap'];
 const forms=['Occupied homes × people per home','People × daily use + other site use','Water use × site return rate','Required supply − usable supply'];
 labels.forEach((v,i)=>{const y=185+i*109;rect(s,65,y,211,76,C.teal);txt(s,v,79,y+18,187,45,25,C.white,true);rect(s,293,y,921,76,C.white);txt(s,forms[i],310,y+15,891,49,31,C.ink,true);});
 caption(s,'Run by area and year. Add supply losses separately. Check sewage collection and treatment too.');
 notes(s,'Futuremodelstructure. Sumacrosssites,andapplyuse-specificreturn fractions ratherthanoneuniversalfraction. Requiredsysteminput=authorisedconsumption/(1-waterlossshare)afterreconciledwaterbalance. Usablecapacity mustmatcharea,date,flowdefinition,timebasis. Negativegapisheadroom. ValuesinL/dayorMLDconsistently.',['method','orientation']);

}

{

 const s=slide('Past readings help choose the forecast model');kicker(s,'Future model testing');
 await screen(s,'inputs-screen-crop.png',64,180,685,450,'Actual prototype planning-input calculator');
 txt(s,'Start with simple maths',797,194,421,78,32,C.teal,true);
 txt(s,'Test local growth and seasonal patterns',797,305,421,97,30,C.ink,true);
 txt(s,'Use AI only when it gives a better forecast',797,441,421,116,30,C.ink,true);
 caption(s,'Compare each method with readings it has not seen. Keep the model and assumptions with the result.');
 notes(s,'Proposedmodelselection. Population/use-ratebaseline, seasonalnaivebaseline, regressionwithlagsandverifieddrivers,thenMLchallenger. Rollingtimebacktests,horizonmatchedholdouts; compareabsoluteerrors,biasandintervalcoverage. Noachievedaccuracyclaim. Actualcalculatorimageisnottrainedmodelproof.',['method','code']);

}

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

 const s=slide('The capacity page shows the treatment gap');kicker(s,'Live prototype');
 await screen(s,'capacity-screen-crop.png',64,177,762,464,'Actual live capacity comparison page');
 txt(s,'19.2 MLD',861,188,353,70,47,C.teal,true);txt(s,'Reported sewage generated',864,259,349,61,25,C.ink);
 txt(s,'0.4 MLD',861,341,353,70,47,C.teal,true);txt(s,'Reported STP use',864,412,349,61,25,C.ink);
 txt(s,'18.8 MLD gap',861,494,360,71,41,C.teal,true);
 caption(s,'APPCB filing: 24 March 2026. A dated reported position, not live plant telemetry.');
 notes(s,'Verifiedpreserved APPCB filingtranscription raw-context/2026-08-26-official-research/appcb-ngt-mtmc-annexures.raw.txt, AnnexureI7.19.2generated minus0.4utilised=18.8reportedgap.26.68MLD tenderstage isnotoperationalcapacity. Screenshotforecastbars compareplanningdemandwithcapacitypositions; planningdemanddiffersfromreportedactualflow.',['https://www.greentribunal.gov.in/sites/default/files/news_updates/OA%2040%20of%202024%20Report%20filed%20by%20R4%20APPCB.pdf','https://neerulekka.wedevit.in/dashboard/capacity']);

}

{

 const s=slide('The capacity result leads to a specific task');kicker(s,'Decision screen design · illustrative area');
 await img(s,'user-selected-city-map.png',64,189,654,372,'Real user-supplied map shown beside an illustrative decision panel');
 rect(s,742,182,473,416,C.white);
 txt(s,'Selected service area',766,202,429,45,26,C.teal,true);
 txt(s,'Current use     48,000 L/day',766,265,429,43,25,C.ink);
 txt(s,'Added use         8,640 L/day',766,318,429,43,25,C.ink);
 txt(s,'New total         56,640 L/day',766,371,429,43,25,C.ink,true);
 txt(s,'Capacity           60,000 L/day',766,424,429,43,25,C.ink);
 rect(s,765,491,426,75,C.teal);txt(s,'Spare supply: 3,360 L/day',780,504,401,55,28,C.white,true);
 txt(s,'Task: confirm flow and connections before the new homes open.',68,590,1140,60,30,C.teal,true);
 notes(s,'Authoreddecision-screen design, notliveUI. Allnumbersexceptderivedbuildingincrementareillustrativeassumptions; map doesnotidentifyanactualmeasuredservicezone.48000+8640=56640;60000-56640=3360headroom,thereforeextra demanddoesnotautomaticallymeanashortage. Sewageassumptions38400+6912=45312;50000capacityleaves4688. Validateconnectionandusablecapacitybeforeadecision.',['method']);

}

{

 const s=slide('The task records the owner, evidence and review');kicker(s,'Task screen design');
 await screen(s,'task-screen-design.png',64,171,910,463,'Generated NEERU LEKKA task screen design');
 txt(s,'Ward operator',1007,212,219,76,29,C.teal,true);
 txt(s,'Records the field check',1007,300,219,100,25,C.ink);
 txt(s,'Department reviewer',1007,433,219,88,28,C.teal,true);
 txt(s,'Accepts or returns it',1007,538,219,82,25,C.ink);
 notes(s,'Generated taskUI mockup prominentlylabelledTASKSCREENDESIGN. Existingappdoesnotpersistthisassignment/reviewworkflow. Suggested taskrecord includesarea,reason,owner,due date,evidence,resultandreviewer. Plannerprioritises anddepartmentrevieweraccepts; noautonomousinfrastructureaction.',['method']);

}

{

 const s=slide('Field readings check whether the estimate holds');kicker(s,'Live intake; proposed review flow');
 await screen(s,'readings-screen-crop.png',64,192,746,429,'Actual daily CSV intake screen');
 flow(s,['Operator records supply, date and service area.','Compare the reading with the estimate.','Reviewer checks the difference and closes or returns the task.'],842,187,374);
 notes(s,'ActualdailyCSVintake islocaltabonlyandemptybydefault. Nooperationalfixtureintroduced. Reviewclosureisproposedtaskworkflow,notimplemented. Modelvalidation usespastreadings/heldoutperiods,avoidrandomtimesplit,compare simplebaseline withAI onlyafterimprovement.',['code','method','https://neerulekka.wedevit.in/dashboard/operations']);

}

{

 const s=slide('Proposed work connects forecasts with field action');kicker(s,'Expected work proposal');
 await screen(s,'selected-growth.png',65,175,550,278,'Actual growth screen');
 await screen(s,'task-screen-design.png',660,175,550,278,'Future task screen design');
 txt(s,'Forecast work',69,481,538,52,33,C.teal,true);
 txt(s,'Building and site inputs, local growth, water and sewage by year.',69,545,538,104,28,C.ink);
 txt(s,'Decision work',664,481,538,52,33,C.teal,true);
 txt(s,'Capacity checks, assigned tasks, field evidence and department review.',664,545,538,104,28,C.ink);
 notes(s,'Explicitfutureworkproposal. Currentprototypehasmaps,planningcalculator,growthallocation,capacityreferenceview. Proposedextensionaddsvalidatedlocalforecastinputsandpersistenttask/reviewflow. Notcompletedproductiondelivery.',['code','method']);

}

{

 const s=slide('Pilot work and review schedule');kicker(s,'Proposed schedule');
 await phone(s,'spatial-mobile-20260905.png',925,181,226,470);
 const rows=[['Weeks 1–2','Office visit and one agreed test area'],['Weeks 3–5','Local demand and growth comparisons'],['Weeks 6–7','Tasks, field evidence and role checks'],['Weeks 8–9','MTMC review and agreed next steps']];
 rows.forEach((r,i)=>{const y=191+i*110;rect(s,66,y,203,75,C.teal);txt(s,r[0],81,y+13,182,53,27,C.white,true);txt(s,r[1],301,y+8,560,70,30,C.ink);});
 caption(s,'Final dates will be agreed during the office visit.');
 notes(s,'Proposedpilotduration,notacceptedappointmentorcompletedwork. Phoneframereusesrealmobilemapasrequested.',['method']);

}

{

 const s=slide('What the department can follow from start to finish');kicker(s,'Review sequence');
 await screen(s,'spatial-desktop-20260905.png',63,184,373,255,'Actual map');
 await screen(s,'capacity-screen-crop.png',455,184,373,255,'Actual capacity screen');
 await screen(s,'task-screen-design.png',847,184,373,255,'Task design');
 txt(s,'Select the area',65,468,373,55,30,C.teal,true);
 txt(s,'Review the gap',457,468,373,55,30,C.teal,true);
 txt(s,'Assign and review',849,468,373,55,30,C.teal,true);
 txt(s,'People, floors, site use\nand growth by year',65,538,373,90,26,C.ink);
 txt(s,'Water, sewage and\nusable capacity',457,538,373,90,26,C.ink);
 txt(s,'Owner, evidence\nand department review',849,538,373,90,26,C.ink);
 caption(s,'Maps and calculations are in the prototype. The task workflow is shown as a screen design.');
 notes(s,'Summaryofexistingprototypeandproposedworkflowwithclearvisibleboundary,notcompletioncheckmarks. Referencecomponentstyleusesthreeproductscreenframeswithoutborrowedbranding.',['code','method']);

}

{

 const s=slide('Prototype and company contacts',true);
 qrCode(s,76,204,300);
 txt(s,'PROTOTYPE LINK',73,530,586,35,19,C.lime,true,MONO);
 txt(s,'neerulekka.wedevit.in',73,574,601,55,35,C.white,true);
 txt(s,'Login: neerulekka.wedevit.in/login',73,640,650,40,25,'#E5D9F4');
 rect(s,708,176,509,61,C.white);txt(s,'WEDEVIT PRIVATE LIMITED',724,188,478,43,27,'#CB1723',true);
 txt(s,'COMPANY WEBSITE',722,273,485,29,18,C.lime,true,MONO);
 txt(s,'wedevit.in',722,313,485,44,30,C.white,true);
 txt(s,'COMPANY EMAIL',722,383,485,29,18,C.lime,true,MONO);
 txt(s,'workwithdevit@gmail.com',722,424,485,45,29,C.white);
 txt(s,'CONTACT NUMBER',722,495,485,29,18,C.lime,true,MONO);
 txt(s,'+91 95533 21211',722,535,485,45,29,C.white);
 txt(s,'DPIIT-recognised startup · DIPP266651',722,611,490,65,23,'#E5D9F4');
 notes(s,'Exactuserprovidedhomepageandlogin. Companywebsite,email,phoneandDPIITrecognitionverifiedincompanypublisheddocumentsduringthissession. Recognitionbelongs tocompanynotcertificationofprototype. Noexternalmessagewassent.',['https://www.wedevit.in/doc']);

}
await fs.mkdir(BUILD,{recursive:true});
const candidatePath=path.join(BUILD,'candidate.pptx');
const unlinkedPath=path.join(BUILD,'candidate-unlinked.pptx');
await (await PresentationFile.exportPptx(p)).save(unlinkedPath);
execFileSync(path.join(RUNTIME,'python/python.exe'),[path.join(ROOT,'docs/presentation/add-slide-links.py'),unlinkedPath,candidatePath],{stdio:'inherit'});
await fs.writeFile(path.join(BUILD,'deck-content.ndjson'),(await p.inspect({kind:'slide,textbox,chart,notes',maxChars:200000})).ndjson);
const finalPath=path.join(ROOT,'docs/presentation',process.env.SMART_UTILITY_OUTPUT || 'NEERU_LEKKA_MTMC_Final_v2.pptx');
const result=await finalizePresentation({workspaceDir:ROOT,candidatePath,finalPath,pythonExecutable:path.join(RUNTIME,'python/python.exe'),integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],explicitTotalSlideCount:25,requiredNativeChartOwnerSlides:[16],requiredNativeTableOwnerSlides:[],materializeLiteralChartWorkbooks:true,fontPolicy:{basis:'design',families:[FONT,MONO,'Nirmala UI']},verifyArtifactToolImport:true,receiptPath:path.join(BUILD,path.basename(finalPath)+'.validation.json')});
console.log(JSON.stringify({font:FONT,...result},null,2));
const final=await PresentationFile.importPptx(await FileBlob.load(finalPath));
await fs.mkdir(path.join(BUILD,'renders'),{recursive:true});
for(let i=0;i<slideCount;i++){
 const blob=await final.export({slide:final.slides.getItem(i),format:'png',scale:1});
 await fs.writeFile(path.join(BUILD,'renders',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await blob.arrayBuffer()));
}
console.log('Rendered all final slides.');


