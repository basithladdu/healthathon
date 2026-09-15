import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Run with the bundled Node runtime. All intermediate files stay in tmp/submission-build.
const ROOT = path.resolve(process.env.SMART_UTILITY_ROOT || process.cwd());
const BUILD = path.join(ROOT, 'tmp/submission-build');
const SKILL = 'C:/Users/basit/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations';
const RUNTIME = 'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies';
process.env.RUNTIME_NODE_MODULES = path.join(RUNTIME, 'node/node_modules');
const { Presentation, PresentationFile, FileBlob } = await import(pathToFileURL(path.join(RUNTIME, 'node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs')).href);
const { resolvePresentationFont, applyPresentationChartFont, finalizePresentation } = await import(pathToFileURL(path.join(SKILL, 'container_tools/artifact_tool_utils.mjs')).href);
const FONT = resolvePresentationFont();
const C = { ink:'#14323C', muted:'#526B72', teal:'#087F83', blue:'#28649B', orange:'#B86B26', pale:'#E7F0F0', line:'#C8D6D9', paper:'#FBFCFA', white:'#FFFFFF' };
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
function txt(s,text,x,y,w,h,size=26,color=C.ink,bold=false){
  const a=s.shapes.add({geometry:'textbox',name:text.slice(0,60),position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
  a.text=text;a.text.style={typeface:FONT,fontSize:size,color,bold,autoFit:'none'};return a;
}
function rect(s,x,y,w,h,fill=C.pale){return s.shapes.add({geometry:'rect',position:{left:x,top:y,width:w,height:h},fill,line:{fill:'none',width:0}});}
function rule(s,x,y,w,color=C.line){rect(s,x,y,w,2,color);}
function notes(s,body,refs=[]){s.speakerNotes.textFrame.setText(body+'\n\nSources\n'+refs.map(k=>sources[k]??k).join('\n\n'));}
function slide(title){const s=p.slides.add();slideCount++;s.background.fill=C.paper;txt(s,title,60,42,1150,130,44,C.ink,true);txt(s,String(slideCount).padStart(2,'0'),1180,678,40,25,16,C.muted);return s;}
async function img(s,file,x,y,w,h,alt){s.images.add({blob:new Uint8Array(await fs.readFile(path.join(ROOT,'docs/presentation/assets',file))),contentType:'image/png',fit:'contain',position:{left:x,top:y,width:w,height:h},alt});}
function arrow(s,x,y,w=45){s.shapes.add({geometry:'rightArrow',position:{left:x,top:y,width:w,height:20},fill:C.teal,line:{fill:'none',width:0}});}
function row(s,y,n,title,body){txt(s,n,62,y,72,65,34,C.teal,true);txt(s,title,145,y,370,65,28,C.ink,true);txt(s,body,550,y,655,80,25,C.muted);rule(s,145,y+95,1055);}

// 01: Minimal cover, original product identity and one real product image.
{
 const s=slide('Smart Utility\nDemand Forecasting');
 txt(s,'Mangalagiri–Tadepalli',65,235,520,60,31,C.teal,true);
 txt(s,'Water supply and sewerage planning',65,310,500,100,30);
 txt(s,'Product: NEERU LEKKA',65,420,500,45,25,C.muted);
 txt(s,'WEDEVIT PRIVATE LIMITED',65,566,530,45,29,C.ink,true);
 txt(s,'AP AI Acceleration Programme\nSubmission review · 5 September 2026',65,617,780,55,20,C.muted);
 await img(s,'masterplan.png',590,200,650,420,'Supplied MTMC town map, planning reference only');
 notes(s,'Submission date is the team preparation date, not an organizer deadline. This deck describes an evidence-review prototype and a proposed demand forecasting system. Smart Utility Demand Forecasting is the official challenge name; NEERU LEKKA is the current product brand in this repository; WEDEVIT PRIVATE LIMITED is the legal company name. Visual: page 1 of raw-context/2026-08-11-mtmc-maud/MTMC MASTER PLAN (1).pdf, rendered from the preserved original. PDF metadata is dated 16 May 2023 and does not establish plan approval, completed development or current occupancy. No usable geospatial reference was supplied.', ['challenge','https://www.wedevit.in/doc and linked https://www.wedevit.in/documents/CIN.pdf, inspected 5 September 2026. Company-published incorporation certificate copy names WEDEVIT PRIVATE LIMITED.']);
}
// 02: Department decision, spatial context, explicit operating limit.
{
 const s=slide('Where will demand outgrow supply?');
 await img(s,'spatial-desktop-20260905.png',55,168,825,440,'Fresh local Smart Utility asset map captured at 1440 by 1000, historical GIS data');
 txt(s,'Planners',930,175,270,40,28,C.teal,true);
 txt(s,'Compare growth scenarios before sizing projects',930,225,270,125,27);
 txt(s,'Engineers',930,385,270,40,28,C.teal,true);
 txt(s,'Check service areas against usable capacity',930,435,270,130,27);
 txt(s,'Local app, 5 September 2026. Historical source inventory does not prove current capacity.',65,622,1100,42,22,C.muted);
 notes(s,'Department problem: uniform arithmetic allocation misses saturated old neighbourhoods and new growth corridors. Forecast water demand and sewage generation at ward/city level, then compare with confirmed service-area capacity. Network hydraulics, water quality and equal distribution require additional operational evidence and engineering analysis; this prototype does not automatically control distribution.', ['orientation','gis','Fresh real-browser screenshot captured 5 September 2026 from http://127.0.0.1:5197/dashboard/spatial in the saved devit-utility checkout. Browser viewport 1440 x 1000; unaltered content crop x260 y120 width1130 height830. Displayed data: real supplied historical GIS records (2017–2019 operational inventory, later boundary metadata), not live telemetry or synthetic data. Local walkthrough sign-in is not production authorization. Basemap credits: OpenFreeMap https://openfreemap.org/ ; OpenMapTiles https://openmaptiles.org/ ; OpenStreetMap contributors https://www.openstreetmap.org/copyright .']);
}
// 03: Evidence chronology, not mixed-vintage precision.
{
 const s=slide('Historical data cannot establish today’s demand');
 row(s,170,'01','2001 / 2011','Demographic history and census base');
 row(s,285,'02','2017–2019','GIS inventory with incomplete spatial coverage');
 row(s,400,'03','2026 / 2041 / 2056','Cached workbook projections, not measured use');
 txt(s,'Observed: dated records     Proxy: built-up index',65,551,1110,45,25,C.teal,true);
 txt(s,'Assumed: use rates          Missing: current readings',65,598,1110,45,25,C.teal,true);
 txt(s,'50 workbook wards and 65 GIS boundaries still need an approved crosswalk.',65,650,1100,36,22,C.muted);
 notes(s,'2001 data are 25 years old in 2026. The orientation specifically says the DPR population base is 2011. The supplied workbook contains both years. Do not characterize every dataset as 2001. The 2026 base-year label does not make a cached projection an observation. Building and parcel coverage is geographically incomplete; new boundary metadata do not refresh the old operational inventory. Cached gross 2026 population multiplied by 135 LPCD yields 44.685 MLD only as a planning calculation. Missing external workbook links prevent a full local recalculation.', ['workbook','gis','orientation']);
}
// 04: Explicitly requested editable demand-driver composition.
{
 const s=slide('Demand drivers and supply constraints');
 txt(s,'PEOPLE & BUILDINGS',65,162,520,32,23,C.teal,true);
 txt(s,'Households, occupancy, building use\nand occupied floor area',65,209,540,82,28);
 txt(s,'ACTIVITY: CANDIDATE PROXIES',700,162,520,32,23,C.teal,true);
 txt(s,'Industry, commerce and tourism\nAirport activity only where service-linked',700,209,525,95,27);
 rule(s,65,323,1140);
 txt(s,'CONSUMER WATER DEMAND',65,350,680,57,36,C.ink,true);
 txt(s,'Weather, seasons and climate\nTariffs, restrictions and behaviour',765,351,440,86,25,C.muted);
 rule(s,65,465,1140);
 txt(s,'SPATIAL CHANGE',65,500,500,32,23,C.teal,true);
 txt(s,'Land use, roads, approved projects\nand development timing',65,546,545,82,27);
 txt(s,'DELIVERY CONSTRAINTS',700,500,520,32,23,C.teal,true);
 txt(s,'Source availability, network losses\nand commissioned infrastructure',700,546,525,82,27);
 notes(s,'Proposed driver framework, not fitted local effects. Domestic demand uses households/population and occupancy. Footprint alone is a proxy, not occupied floor area or water use. Non-domestic demand uses metered categories and activity evidence. Airport passenger counts, hotel nights and economic indicators are candidate proxies only where a physical service-area or catchment link and incremental predictive value can be demonstrated. Do not assign airport demand to MTMC merely because an airport is nearby. Avoid double counting employees, visitors, airport users and household demand. Land-use permission does not prove construction or occupation. Source shortages and restrictions can suppress delivered/billed water below unconstrained demand. Network losses increase required production and do not represent consumer consumption. Every input needs owner, observation date, units, spatial extent and an observed/proxy/assumed/missing classification.', ['orientation','method']);
}
// 05: System view and mass-balance distinctions.
{
 const s=slide('Proposed forecasting system');
 const xs=[65,380,695,1010];
 const ws=[260,260,260,205];
 ['Source records','Aligned history','Forecast engine','Review'].forEach((t,i)=>{txt(s,t,xs[i],177,ws[i],42,28,C.teal,true);if(i<3)arrow(s,xs[i]+265,186,38);});
 ['Meters and connections\nBuildings and weather','Same units and periods\nApproved service areas','Seasonal regression\nLagged use + drivers','Range and gaps\nVersioned decision'].forEach((t,i)=>txt(s,t,xs[i],245,ws[i],115,25));
 rule(s,65,395,1140);
 txt(s,'Consumer demand',65,436,350,50,30,C.ink,true);
 txt(s,'People × use rate\n+ non-domestic use',65,520,350,108,28);
 txt(s,'System input',465,436,350,50,30,C.ink,true);
 txt(s,'Authorised use ÷\n(1 − loss fraction)',465,520,350,108,28);
 txt(s,'Sewage generation',865,436,350,60,30,C.ink,true);
 txt(s,'Return fraction × use\n+ other-source returns',865,520,350,116,27);
 notes(s,'Conceptual proposed architecture. Candidate monthly regression: service-zone effect + month-of-year terms + lagged consumption (e.g. 1 and 12 months) + verified connection/occupancy/weather/policy drivers. Compare against seasonal-naive and per-connection baselines with rolling backtests; coefficients are not fitted here. Daily rates in litres per day convert to MLD by division by 1,000,000. For system input, use total authorised consumption divided by (1 minus water-loss share of system input). NRW also includes authorised unbilled consumption, so NRW and physical leakage are not interchangeable. If using NRW share instead, the numerator is billed authorised consumption. Reconcile the full water balance and never count losses twice. Sewerage generation, collected sewer flow and STP treatment load are separate quantities. Apply the return fraction to relevant consumer use including verified private borewell/tanker contributions; the extra term in the diagram represents their sewer-return contribution, not their entire supplied volume. Account separately for sewer connection coverage, infiltration/inflow and non-returning uses. The prototype defaults to 135 LPCD and an 80% return assumption from the orientation; these are planning assumptions, not measured use or a universal regulatory determination.', ['orientation','code','method','MoHUA/ASCI, Guidance Notes on Preparation of DPR for Drink-from-Tap, supplied attachment Guidance-Notes-on-Preparation-of-DPR.pdf, PDF page 71 (printed page 19). Separates consumer-end LPCD, losses, non-domestic bulk use and floating population. Its example 15% losses is not an MTMC observation. Local source: C:/Users/basit/Downloads/Guidance-Notes-on-Preparation-of-DPR.pdf.']);
}
// 06: Native spatial-method diagram, no invented GIS precision.
{
 const s=slide('Growth needs both location and occupancy');
 txt(s,'Current prototype',65,162,510,47,31,C.teal,true);
 txt(s,'Satellite built-up index trend',65,242,525,50,31);
 txt(s,'Relative headroom + corridor assumption',65,309,525,85,31);
 txt(s,'Allocation of a fixed city total',65,435,525,90,35,C.ink,true);
 txt(s,'Heuristic scenario. No trained coefficients.',65,588,530,62,24,C.muted);
 rect(s,627,168,2,450,C.line);
 txt(s,'Proposed department model',685,162,530,47,31,C.teal,true);
 txt(s,'Verified building use and occupancy',685,242,530,85,31);
 txt(s,'Connection histories + project timing',685,350,530,85,31);
 txt(s,'Validated ward and city estimates',685,467,530,90,35,C.ink,true);
 txt(s,'50-to-65 boundary crosswalk still required.',685,588,530,62,24,C.muted);
 notes(s,'Current src/data/growthModel.js reallocates fixed workbook city totals over 65 GIS wards. It starts from equal base population across the 65 boundaries, weights normalized positive built-up index trend times relative headroom, applies a hard-coded 1.35 corridor multiplier to listed corridors and defaults to 0.7 spatial weighting. This is not a fitted logistic/geometric demand forecast and does not establish ward population. A near-zero headroom value is an algorithmic proxy, not verified land capacity. Proposed replacement: use approved crosswalks and observed building/connection/occupancy data, reconcile bottom-up ward estimates to a compatible city total, represent planned projects by commissioning/occupation scenarios, and validate on withheld periods and areas.', ['code','gis','workbook','method']);
}
// 07: Transparent editable scenario chart, deliberately normalized.
{
 const s=slide('Future demand changes with assumptions');
 txt(s,'Illustrative domestic-demand index. Base = 100.',65,148,1100,45,25,C.muted);
 const horizons=[0,1,2,3,4,5];
 const cases=[{name:'Lower',h:0,u:-.01,color:C.blue},{name:'Central',h:.015,u:0,color:C.teal},{name:'Higher',h:.03,u:.01,color:C.orange}];
 const chart=s.charts.add('line',{position:{left:65,top:218,width:750,height:378},categories:horizons.map(y=>y===0?'Base':`Year ${y}`),series:cases.map(c=>({name:c.name,values:horizons.map(y=>Number((100*((1+c.h)*(1+c.u))**y).toFixed(3))),line:{fill:c.color,width:4},marker:{symbol:'circle',size:6}})),hasLegend:true,legend:{position:'bottom',textStyle:{typeface:FONT,fontSize:20,fill:C.ink}},xAxis:{textStyle:{typeface:FONT,fontSize:18,fill:C.muted}},yAxis:{min:90,max:130,majorUnit:10,numberFormatCode:'0',textStyle:{typeface:FONT,fontSize:18,fill:C.muted},majorGridlines:{fill:C.line,width:1}},chartFill:C.paper,plotAreaFill:C.paper});
 applyPresentationChartFont(chart,{fontFamily:FONT});
 txt(s,'Annual assumptions',872,226,345,42,29,C.ink,true);
 txt(s,'Lower\nHouseholds 0%, use −1%',872,296,345,77,24,C.blue);
 txt(s,'Central\nHouseholds +1.5%, use 0%',872,392,345,77,24,C.teal);
 txt(s,'Higher\nHouseholds +3%, use +1%',872,488,345,77,24,C.orange);
 txt(s,'Scenario spread is assumption uncertainty, not a statistical confidence interval or an MTMC forecast.',65,625,1120,56,23,C.muted);
 notes(s,'Entire chart is a hypothetical sensitivity demonstration, not observed MTMC data. Index(t)=100×[(1+annual household growth)×(1+annual per-household use change)]^t. Household size and non-domestic mix are held constant for this illustration. Lower: 0% household growth and -1% use, year5 95.099. Central: +1.5% and 0%, year5 107.728. Higher: +3% and +1%, year5 121.841. Display rounding does not imply measured precision. No probabilities attach to these paths. For the operational model, separate parameter/project scenarios from empirical prediction intervals calibrated on backtest residuals. Revise the paths when occupancy, tariffs, weather or project dates change. Long-range 2041/2056 planning should use explicit development scenarios, not extrapolate this five-year example mechanically.', ['method']);
}
// 08: Product evidence, concise annotation outside screenshot.
{
 const s=slide('The prototype exposes planning assumptions');
 await img(s,'demand-desktop-20260905.png',55,150,925,485,'Fresh local demand screen with Ward 40 selected for 2041');
 txt(s,'Selected state',1020,190,205,85,28,C.teal,true);
 txt(s,'Ward 40\n2041 horizon\nWhole city\nComparison enabled',1020,305,205,230,26);
 txt(s,'Screenshot shows a planning calculation. Forecast accuracy remains unmeasured.',65,635,1120,42,23,C.muted);
 notes(s,'Fresh local demand screen captured after using actual controls to select Ward 40, year 2041 and comparison mode. Displayed numbers are calculations from cached workbook projections, not measured current consumption or a fitted forecast. Ward 40 displayed 9367 planning population, 1.26 MLD water and 1.01 MLD sewage at the selected horizon. No synthetic readings were inserted. The current app brand is NEERU LEKKA in this same Smart Utility checkout. Screenshot provenance recorded in docs/presentation/SOURCES_AND_READINESS.md; this proves the inspected local state only.', ['code','workbook','Captured 5 September 2026: http://127.0.0.1:5197/dashboard/demand?ward=40&year=2041&horizons=compare . Browser viewport 1440 x 1000; unaltered content crop x260 y120 width1130 height790. Data class: source-derived planning projection. The same state was inspected at 390 x 844 with no page-level horizontal overflow. Related preserved source PDF: output/innovation-for-her-draft-pack.pdf.']);
}
// 09: Department workflow plus real intake screenshot.
{
 const s=slide('The workflow makes missing data explicit');
 await img(s,'requirements-mobile-20260905.png',65,156,340,490,'Fresh 390px mobile requirement R-08 showing current capability and missing usage history');
 txt(s,'Selected requirement',490,179,680,40,29,C.teal,true);
 txt(s,'Historical utility demand and usage',490,229,680,50,29);
 txt(s,'Current capability',490,335,680,40,29,C.teal,true);
 txt(s,'Local department CSV checks',490,385,680,70,29);
 txt(s,'Still required',490,514,680,40,29,C.teal,true);
 txt(s,'Dated usage records with stable area IDs and units',490,564,680,80,29);
 txt(s,'Current local prototype. Production ingestion, authorization and measured demand remain unverified.',65,654,1100,40,20,C.muted);
 notes(s,'Fresh mobile capture of the actual populated requirements detail, selected using the R-08 search and item controls. The next-gate link successfully opened Daily readings, which explicitly showed No daily readings loaded. That empty operational dashboard is omitted from the deck; no demo readings were created to fill it. Department files require timestamps, units, stable area identifiers and correction handling. Production requires configured server-side authorization, scoped data access, audited writes and department acceptance. UI roles or a mock sign-in do not prove production RBAC.', ['code','Captured 5 September 2026: http://127.0.0.1:5197/dashboard/requirements?year=2041&req-q=R-08&req-id=R-08 . Viewport 390 x 844, full viewport screenshot at the selected-detail scroll position, no edits or compositing. Displayed data: real app requirement/source metadata. Historical usage readings: unavailable. Walkthrough sign-in: presentation environment, not production authorization. Linked route inspected: /dashboard/operations?year=2041 .']);
}
// 10: Forecast evaluation, native time split diagram.
{
 const s=slide('Validation precedes operational forecasts');
 txt(s,'Rolling time-based backtests',65,167,1100,48,32,C.teal,true);
 ['History available at forecast date','Held-out future months'].forEach((t,i)=>txt(s,t,65+i*725,235,i?425:650,45,27));
 rect(s,65,303,705,50,C.teal);rect(s,790,303,420,50,C.orange);
 txt(s,'Train and tune on earlier periods',65,379,640,53,27);
 txt(s,'Score every horizon',790,379,420,53,27);
 rule(s,65,464,1145);
 txt(s,'Baseline comparison',65,497,365,44,28,C.ink,true);
 txt(s,'Seasonal naïve and\npopulation × use-rate model',65,554,365,90,26);
 txt(s,'Error and bias',465,497,365,44,28,C.ink,true);
 txt(s,'MAE, WAPE and bias\nby ward, season and horizon',465,554,365,90,26);
 txt(s,'Uncertainty coverage',865,497,345,44,28,C.ink,true);
 txt(s,'Check interval coverage\nand flag out-of-range inputs',865,554,345,90,26);
 notes(s,'Proposed evaluation plan. Prefer at least 36 monthly observations, with 60 desirable, subject to seasonal coverage and data quality; these are requested data windows, not universal sufficiency thresholds. Use expanding/rolling origins with horizon-matched held-out blocks; no random time split. Start with seasonal naïve (same month previous year), trend/seasonal regression and population/use-rate baselines. Introduce lagged consumption (e.g. 1 and 12 months), weather, connection counts and verified drivers only if out-of-sample gains persist. Future weather must use information available at forecast time. Measure MAE in MLD and signed bias; WAPE is sum absolute error divided by sum actual use and needs nonzero aggregate actual use. Validate boundary changes and sparse wards separately. Calibrate empirical intervals and report coverage plus width. Fit and select without looking at final test outcomes. No numeric acceptance threshold or performance result has been agreed or achieved.', ['method']);
}
// 11: No dense raw table: four timed intake bands.
{
 const s=slide('The next data package makes the model testable');
 row(s,170,'01','Monthly consumption','36–60 months requested, with meter and connection counts');
 row(s,285,'02','Spatial and occupancy data','Approved ward/service-area crosswalk, building use and occupancy');
 row(s,400,'03','Supply and network balance','Source yield, bulk flows, losses, outages and usable capacity');
 row(s,515,'04','Changes and external drivers','Weather/climate, policies and dated development plans');
 txt(s,'Proposed refresh: monthly readings and review, quarterly spatial updates, project changes on approval.',65,649,1110,43,22,C.muted);
 notes(s,'Proposed data contract. Consumption: monthly/daily volume, source and billing basis, unit, period, meter identifier or approved aggregate zone, active connections, meter replacement/reset, missingness and corrections. Spatial: current boundary versions and effective dates, approved crosswalk from 50 workbook wards to 65 GIS boundaries, service zones, building footprint/use/floors/occupancy and connection links. Operations: source abstraction/availability, bulk inflow/outflow, authorised billed/unbilled consumption, audited NRW components, supply hours/outages, WTP/storage/STP design and usable capacity, commissioning dates, sewer connections and flow. Exogenous: local weather, industrial/commercial activity and metered categories, hotel occupancy/tourism seasonality, airport passenger counts only with demonstrable catchment linkage, tariffs/restriction dates and project/phasing/occupation evidence. Refresh proposal: monthly consumption/connections and model run; daily operational/weather inputs where available; quarterly building/use review; project and policy updates on approved change. Retain original observations, derived values, provenance and versioned assumptions. Annual-only totals support annual scenarios and cannot establish monthly seasonality.', ['method','orientation']);
}
// 12: Specific pilot decision and clear release boundary.
{
 const s=slide('WEDEVIT PRIVATE LIMITED');
 txt(s,'devit · We Develop It',65,168,555,48,31,C.teal,true);
 txt(s,'Web, mobile and custom software\nKurnool, Andhra Pradesh',65,231,565,85,27);
 txt(s,'Co-founders',65,335,510,40,26,C.ink,true);
 txt(s,'Shaik Muqeeth\nShaik Abdul Basith',65,382,530,78,27);
 txt(s,'wedevit.in\nworkwithdevit@gmail.com\n+91 95533 21211',65,478,545,103,26,C.teal);
 txt(s,'Relevant portfolio',685,168,520,48,31,C.teal,true);
 txt(s,'ITI Dashboard',685,234,530,45,29,C.ink,true);
 txt(s,'Technical-education monitoring',685,283,535,55,26);
 txt(s,'ToFEI',685,350,530,45,29,C.ink,true);
 txt(s,'Geospatial compliance workflows',685,399,535,55,26);
 txt(s,'DPIIT recognition: DIPP266651',685,479,535,45,25,C.muted);
 rule(s,65,594,1140);
 txt(s,'Next: one agreed-area forecasting pilot',65,611,1100,45,30,C.ink,true);
 txt(s,'Recent readings, backtests and engineering review before operational use.',65,661,1100,34,23,C.muted);
 notes(s,'Company identity and contact checked live on 5 September 2026. https://www.wedevit.in/doc lists the legal name WeDevit Private Limited, brand devit (We Develop It), co-founders Shaik Muqeeth and Shaik Abdul Basith, company email and phone. The company-published CIN certificate https://www.wedevit.in/documents/CIN.pdf names WEDEVIT PRIVATE LIMITED, CIN U62020AP2026PTC125497, incorporated 24 April 2026. The linked DPIIT certificate https://www.wedevit.in/documents/DIPP266651_WEDEVIT_PRIVATE_LIMITED_RECOGNITION_1375028494029563733.pdf identifies DIPP266651 issued 10 June 2026; certificate copies were textually and visually inspected, not checked against a live government registry. Homepage https://www.wedevit.in/ describes web/mobile/custom software and lists ITI Dashboard and ToFEI as government-focused portfolio work. Repository cross-checks: ../devit-iti/README.md documents an ITI monitoring PoC; ../devit-tofei/README.md documents ToFEI analytics, UDISE lookup, submission and report tracking. These establish portfolio/capability references, not government procurement, adoption, approved outcomes or production scale. Conflict: homepage describes seven Krishna District ITIs, while the current ITI README describes a four-ITI PoC elsewhere; the deck deliberately makes no institution-count, deployment-scale or client-approval claim. The next step is an agreed-area pilot with a verified crosswalk and recent readings, followed by backtests, engineering review and production access approval. Current organizer deadline, template, slide limit, team registration identifier and filename rule remain unknown. No unsupported identity field is filled.', ['challenge','orientation','method']);
}

await fs.mkdir(BUILD,{recursive:true});
const candidatePath=path.join(BUILD,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidatePath);
await fs.writeFile(path.join(BUILD,'deck-content.ndjson'),(await p.inspect({kind:'slide,textbox,chart,notes',maxChars:200000})).ndjson);
const finalPath=path.join(ROOT,'docs/presentation',process.env.SMART_UTILITY_OUTPUT || '100088_Smart_Utility_Demand_Forecasting_WEDEVIT_PRIVATE_LIMITED.pptx');
const result=await finalizePresentation({workspaceDir:ROOT,candidatePath,finalPath,pythonExecutable:path.join(RUNTIME,'python/python.exe'),integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],explicitTotalSlideCount:12,requiredNativeChartOwnerSlides:[7],requiredNativeTableOwnerSlides:[],materializeLiteralChartWorkbooks:true,fontPolicy:{basis:'design',families:[FONT]},verifyArtifactToolImport:true,receiptPath:path.join(BUILD,'fresh-captures',path.basename(finalPath)+'.validation.json')});
console.log(JSON.stringify({font:FONT,...result},null,2));
const final=await PresentationFile.importPptx(await FileBlob.load(finalPath));
await fs.mkdir(path.join(BUILD,'fresh-renders'),{recursive:true});
for(let i=0;i<slideCount;i++){
 const blob=await final.export({slide:final.slides.getItem(i),format:'png',scale:1});
 await fs.writeFile(path.join(BUILD,'fresh-renders',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await blob.arrayBuffer()));
}
console.log('Rendered all final slides.');
