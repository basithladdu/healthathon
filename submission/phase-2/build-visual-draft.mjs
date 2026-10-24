import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
const runtimeModules=process.env.RUNTIME_NODE_MODULES??'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
process.env.RUNTIME_NODE_MODULES??=runtimeModules;
const {FileBlob,PresentationFile}=await import(pathToFileURL(path.join(runtimeModules,'@oai/artifact-tool/dist/artifact_tool.mjs')).href);

process.on('uncaughtException',e=>{console.error(e.message);console.error(e.stack?.split('\n').slice(1,5).join('\n'));process.exitCode=1;});
const root=process.env.SAHARA_WORKSPACE??path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=process.env.SAHARA_DECK_VERSION??'v6-ready';
const skill=process.env.SAHARA_PRESENTATIONS_SKILL??'C:/Users/basit/.codex/plugins/cache/openai-primary-runtime/presentations/26.1017.22136/skills/presentations';
const src=path.join(root,'source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx');
const out=path.join(root,'submission/phase-2');
const build=path.join(out,'build');
const final=path.join(out,`final/Sahara-Healthathon-Phase-2-visual-draft-${version}.pptx`);
await fs.mkdir(build,{recursive:true});
await fs.mkdir(path.dirname(final),{recursive:true});
const p=await PresentationFile.importPptx(await FileBlob.load(src));
const info=await p.inspect({select:'$.slides[*]',include:['id','index','elements.id','elements.kind','elements.text']});
const slides=info.records;const P=x=>x*4/3;
const C={blue:'#253DAE',ink:'#243047',teal:'#167C6D',muted:'#657388',light:'#EEF3F8',green:'#E7F4EF',cream:'#FFF8E9',coral:'#B66647',amber:'#F2C45D',purple:'#7653AD',line:'#CEDAE5'};
function anchor(i,prefix){const r=slides[i].elements.find(e=>e.text?.startsWith(prefix));if(!r)throw Error(`Missing anchor ${i+1} ${prefix}`);return p.resolve(r.id);}
function clear(i,prefix){anchor(i,prefix).text='';}
function position(o){return Object.fromEntries(Object.entries(o).map(([k,v])=>[k,P(v)]));}
function txt(i,value,x,y,w,h,f=20,color=C.ink,bold=false,align='left'){
 const s=p.slides.items[i].shapes.add({geometry:'textbox',position:position({left:x,top:y,width:w,height:h}),fill:'none',line:{fill:'none',width:0}});
 s.text=value;s.text.style={fontFamily:'Arial',fontSize:P(f),color,bold,alignment:align,autoFit:'none',insets:0};return s;
}
async function img(i,rel,x,y,w,h,alt,fit='contain',crop){return p.slides.items[i].images.add({blob:new Uint8Array(await fs.readFile(path.join(root,rel))),contentType:rel.endsWith('.jpeg')||rel.endsWith('.jpg')?'image/jpeg':'image/png',alt,fit,position:position({left:x,top:y,width:w,height:h}),...(crop?{crop}:{})});}
function node(i,value,x,y,w,h,fill=C.green,border=C.teal,f=20){const s=p.slides.items[i].shapes.add({geometry:'rect',position:position({left:x,top:y,width:w,height:h}),fill,line:{fill:border,width:1}});s.text=value;s.text.style={fontFamily:'Arial',fontSize:P(f),color:C.ink,alignment:'center',verticalAlignment:'middle',autoFit:'none',insets:P(9)};return s;}
function connect(i,a,b,style='solid',color=C.teal,from='right',to='left'){return p.slides.items[i].shapes.connect(a,b,{kind:'elbow',fromSide:from,toSide:to,line:{style,fill:color,width:2},tail:{type:'triangle',width:'med',length:'med'}});}
function table(i,values,x,y,width,height,colWidths,font=19){const t=p.slides.items[i].tables.add({rows:values.length,columns:values[0].length,left:P(x),top:P(y),width:P(width),height:P(height),columnWidths:colWidths.map(P),values});t.borders.assign({fill:C.line,width:.75,style:'solid'});t.cells.block({row:0,column:0,rowCount:values.length,columnCount:values[0].length}).assign({fill:'#FFFFFF',textStyle:{fontFamily:'Arial',fontSize:P(font),color:C.ink},margins:{left:10,right:10,top:9,bottom:9}});t.cells.block({row:0,column:0,rowCount:1,columnCount:values[0].length}).assign({fill:C.light,textStyle:{fontFamily:'Arial',fontSize:P(font),color:C.blue,bold:true}});return t;}
function notes(i,...lines){p.slides.items[i].speakerNotes.text=lines.join('\n');}
function link(i,label,url,x,y,w,h,f=20,color=C.blue){const s=txt(i,'',x,y,w,h,f,color,false,'center');s.text=[{runs:[{run:label,style:{fontSize:`${f}pt`,typeface:'Arial',color},link:{uri:url,isExternal:true}}]}];return s;}
const bodyAnchors=['','Introduce the team members','Clearly define the healthcare problem','Identify the primary users','Describe the solution and how','Include screenshots of the working','Identify the AI models','Show key milestones','Describe how the solution was tested','Explain relevant data privacy','Explain what would be required','Explain what the team plans',''];
for(let i=1;i<12;i++)clear(i,bodyAnchors[i]);
for(const [i,t] of [[6,'Keep this concise.'],[7,'Focus on milestones'],[8,'Teams may use']])clear(i,t);
anchor(4,'Solution Overview').text='Solution Overview, Key Features & Impact';
anchor(6,'Technology, Tools').text='Technology, Tools and AI Approach';

// V6: product-led compositions within the organiser's thirteen sections.
// Original artwork remains. The cover logo is smaller so Sahara leads.
const coverMark=p.slides.items[0].images.items[0];
if(coverMark){const f=coverMark.frame;coverMark.frame={left:P(44),top:P(28),width:P(270),height:P(270*f.height/f.width)};}
const sectionNames=['','Team','The problem','People and care setting','Goals-of-care journey','Working product','Technology and AI','Build progress','Evaluation','Safety and responsible AI','Deployment and cost','Next steps',''];
for(let i=1;i<12;i++){
 const heading=slides[i].elements.find(e=>e.kind==='textbox'&&e.text&&!e.text.startsWith('HEALTH-')&&!e.text.startsWith(bodyAnchors[i])&&!e.text.startsWith('Keep this')&&!e.text.startsWith('Focus on')&&!e.text.startsWith('Teams may'));
 const s=p.resolve(heading.id);s.text=sectionNames[i];s.text.style={fontFamily:'Arial',fontSize:P(22),color:C.blue,bold:true,insets:0};
}
const cap=(i,t,x=44,y=487,w=872)=>txt(i,t,x,Math.min(y,487),w,18,12,C.muted);
const big=(i,t,x,y,w,h=90,f=32)=>txt(i,t,x,y,w,h,f,C.ink,true);
const small=(i,t,x,y,w,h=60,f=21)=>txt(i,t,x,y,w,h,f,C.ink);

// 1. Cover: one name and one scene, not a submission form.
for(const a of ['FINAL SUBMISSION','[Solution Name]','[Short subtitle','TEAM NAME','TEAM CODE'])clear(0,a);
anchor(0,'HEALTH-A-THON 2026').text='HEALTH-A-THON 2026  •  PHASE 2 DRAFT';
await img(0,'submission/phase-2/assets/recording-consultation-concept.png',447,138,466,338,'Round 1 generated consultation illustration','contain');
await img(0,'submission/phase-2/assets/sahara-app-icon.png',44,197,64,64,'Actual Sahara app icon');
txt(0,'Sahara',44,283,410,72,54,C.teal,true);
small(0,'A Palliative Care\nCompanion',44,365,390,78,28);
txt(0,'Zeros and Ones',44,469,320,28,20,C.blue,true);
cap(0,'Illustrative consultation. No real patient use shown.',470,488,440);

// 2. The required team table stays editable and concise.
big(1,'Clinical and product work together',44,91,872,48,32);
table(1,[['Member','Role','Focus'],['Dr Sharada Vinod Kutty','Doctor partner','Clinical workflow'],['Dr Sujay Halkur Shankar','Clinician','Care framework'],['Shaik Abdul Basith','Technical lead','Product and engineering'],['Shaik Muhammad Awaiz','Technical member','Web and Android'],['Shirin Ayub','Medical student','Clinical input']],44,170,872,290,[320,220,332],20);
cap(1,'Doctor specialities, biographies and photos remain to confirm.');

// 3. A patient situation is the main image; external research stays separate.
await img(2,'submission/phase-2/assets/consultation-family.png',44,103,584,300,'Generated family consultation scenario from the original deck');
big(2,'The next team needs\nthe earlier conversation',44,416,584,63,26);
txt(2,'92% documented\nafter QI',662,104,254,69,24,C.teal,true);
p.slides.items[2].charts.add('bar',{position:position({left:655,top:184,width:264,height:219}),categories:['Before','After QI'],series:[{name:'Documented',values:[0,.92],fill:C.teal}],barOptions:{direction:'column',grouping:'clustered',gapWidth:100},hasLegend:false,xAxis:{textStyle:{fontSize:P(17),fill:C.ink},line:{fill:C.line,width:1}},yAxis:{min:0,max:1,majorUnit:.5,numberFormatCode:'0%',textStyle:{fontSize:P(13),fill:C.muted},majorGridlines:{fill:C.line,width:.75}},dataLabels:{showValue:false},chartFill:'#FFFFFF',chartLine:{fill:'none',width:0},plotAreaFill:'#FFFFFF',plotAreaLine:{fill:'none',width:0}});
small(2,'24/26 documented\nChennai QI study',662,414,254,58,20);
cap(2,'Illustrative family scene',44,499,550);
cap(2,'External study, not Sahara results',662,487,254);

// 4. Large family scene with two short role statements.
await img(3,'submission/phase-2/assets/family-report-concept.png',44,103,590,332,'Original generated family care-plan concept');
txt(3,'Patient and family',667,127,249,58,26,C.teal,true);
small(3,'Say what matters.\nCheck the exact note.',667,200,249,79,24);
txt(3,'Clinician',667,319,249,37,26,C.blue,true);
small(3,'Lead, review\nand revisit care.',667,372,249,82,24);
txt(3,'Across clinic, home and the next care encounter',44,449,590,38,25,C.ink,true);
cap(3,'Illustration from Round 1. Clinical pilot not yet validated.');

// 5. The core product fills the body. Captions describe the actual care journey.
big(4,'Source beside the Care Note',44,88,872,49,32);
await img(4,'submission/phase-2/assets/historical-web-source-review-2026-09-24.png',44,151,872,286,'Actual historical care-note review UI with source beside draft','contain');
const journey=['Consent + original','Clinician review','Release version','Exact-note confirmation','Permissioned retrieval'];
for(let j=0;j<5;j++){const x=44+j*177;txt(4,String(j+1),x,448,26,29,22,C.teal,true);txt(4,journey[j],x+31,448,134,49,18,C.ink,true);}
cap(4,'Actual web capture, 24 September. Fictional record and earlier name. Workflow is subject to clinical validation.',44,501,872);

// 6. Four real native screens, each large enough to read the main action.
const phones=[['android-patient-hub-050-2026-10-19.png','Family record'],['android-care-queue-050-2026-10-19.png','Request a discussion'],['android-doctor-workspace-050-2026-10-19.png','Clinician workspace'],['android-care-task-050-2026-10-19.png','Follow-up task']];
for(let j=0;j<4;j++){const x=44+j*224;await img(5,`submission/phase-2/assets/${phones[j][0]}`,x,97,200,357,`Actual 0.5 emulator: ${phones[j][1]}`);txt(5,phones[j][1],x,456,200,28,19,C.teal,true,'center');}
cap(5,'Actual 0.5 emulator screens, 19 October. Fictional fixtures. Current 0.6 handset verification remains pending.',44,501,872);

// 7. AI stays next to the actual input, with a small, explicit sharing boundary.
await img(6,'submission/phase-2/assets/android-local-capture-050-2026-10-19.png',44,102,190,379,'Actual historical Android local-recording screen');
txt(6,'On the phone',275,103,365,42,29,C.teal,true);
small(6,'Hindi / English speech\nQwen3-ASR',275,165,360,67,25);
small(6,'OCR and translation\nEditable corrections',275,260,360,67,25);
txt(6,'Originals stay private',275,367,355,38,25,C.ink,true);
txt(6,'Approved\nnotes only',672,151,244,72,28,C.blue,true);
small(6,'Shared accounts\nAccess rules',672,266,244,67,23);
txt(6,'CARE extension\nProposed',672,391,244,57,22,C.coral,true);
cap(6,'Kotlin, Compose, Room, MapLibre and Supabase. Cloud patient scanning and generative clinical drafting stay off.',44,501,872);

// 8. Visible product evolution instead of a box timeline.
txt(7,'September',44,94,270,31,24,C.teal,true);
await img(7,'submission/phase-2/assets/historical-family-home-2026-09-24.png',44,140,345,226,'Actual historical family home UI','contain');
txt(7,'Web Care Note',44,383,345,34,24,C.ink,true);
txt(7,'19 October',421,94,210,31,24,C.teal,true);
await img(7,'submission/phase-2/assets/android-labs-050-2026-10-19.png',451,136,166,284,'Actual historical Android labs screen');
txt(7,'Native care tools',407,437,234,34,22,C.ink,true,'center');
txt(7,'23 October',671,94,245,31,24,C.teal,true);
await img(7,'submission/phase-2/assets/survey-production-mobile-2026-10-23.png',710,136,166,284,'Actual public hospital survey, empty intake');
txt(7,'Hospital survey',652,437,264,35,22,C.ink,true,'center');
cap(7,'Dated actual screens. Earlier records are fictional. Latest 0.6 debug APK adds local language tools.',44,501,872);

// 9. Real comparable evidence stays a table; no wall of explanatory text.
for(const [x,n,label] of [[44,'147','Native checks'],[345,'284','Web checks'],[646,'65','Database checks']]){txt(8,n,x,98,260,78,56,C.teal,true);txt(8,label,x,181,260,33,24,C.ink,true);}
table(8,[['Speech fixture','Audio / inference','Observed'],['Synthetic English','7.15 s / 5.95 s','Exact on one clip'],['Public Hindi','9.12 s / 22.05 s','Word substitutions'],['Public Hinglish','8.28 s / 22.53 s','Word / script errors']],44,259,872,171,[300,250,322],20);
txt(8,'Next evidence: handset + clinician evaluation',44,447,872,35,25,C.coral,true);
cap(8,'Checks dated 19–20 October. Speech timings are desktop CPU tests, not clinical accuracy or phone performance.',44,501,872);

// 10. An actual note history makes safety concrete, with only three annotations.
await img(9,'submission/phase-2/assets/historical-note-history-2026-09-24.png',44,99,585,379,'Actual historical versioned care-note history, fictional record');
txt(9,'Named review',672,117,244,37,27,C.teal,true);
small(9,'Check the source\nbefore release',672,164,244,69,23);
txt(9,'Exact version',672,269,244,37,27,C.blue,true);
small(9,'Patient and family\nconfirm separately',672,316,244,68,23);
txt(9,'Permissioned access',672,421,244,35,23,C.coral,true);
cap(9,'Historical fictional UI. Current access includes expiry/revocation. Patient use needs clinical oversight and validation.',44,501,872);

// 11. One cost table beside an actual care map, with amounts honestly unresolved.
table(10,[['Cost area','Planning basis'],['One-time','Onboarding, devices, clinical validation'],['Ongoing','Hosting, database, support and governance'],['Per transaction','Local AI: no cloud call fee'],['Price / payer','Facility / NGO / government proposal']],44,115,552,312,[145,407],18);
await img(10,'submission/phase-2/assets/survey-confirmed-map-2026-10-23.png',631,113,285,291,'Actual MapLibre hospital pin test, synthetic survey context');
txt(10,'A supported clinical pilot',44,448,552,38,28,C.teal,true);
cap(10,'Amounts remain to confirm. Map: 23 October QA, OpenStreetMap contributors. No partner adoption claimed.',44,514,872);

// 12. A few large next actions rather than report paragraphs.
big(11,'Before patient use',44,104,872,50,34);
const next=[['1','Validate on phones','Hindi / English accuracy'],['2','Agree a clinical pilot','Consent, review and escalation'],['3','Measure the care flow','Retrieval and understanding']];
for(let j=0;j<3;j++){const y=187+j*82;txt(11,next[j][0],44,y,60,57,43,C.teal,true);txt(11,next[j][1],127,y+2,460,36,27,C.ink,true);txt(11,next[j][2],127,y+42,460,30,21,C.muted);}
txt(11,'CARE clinician\nextension',665,211,251,73,29,C.blue,true);
small(11,'Prototype after\nthe core pilot',665,310,251,72,24);
txt(11,'Public solution information: Yes',44,450,872,33,23,C.teal,true);
cap(11,'Shirin’s clinical symptom-tracker input remains pending. Team code, costs and narrated video still need confirmation.',44,511,872);

// 13. A real product crop closes the story and the product link remains clickable.
clear(12,'[Team name]');clear(12,'[Contact email]');clear(12,'Thank you');clear(12,'Health-a-thon 2026');
const closeMark=p.slides.items[12].images.items[0];
if(closeMark){const f=closeMark.frame;closeMark.frame={left:P(44),top:P(26),width:P(285),height:P(285*f.height/f.width)};}
txt(12,'Sahara',44,200,432,66,49,C.teal,true);
txt(12,'A Palliative Care Companion',44,286,432,78,28,C.ink,true);
link(12,'sahara.wedevit.in','https://sahara.wedevit.in',44,395,432,35,27,C.blue);
link(12,'workwithdevit@gmail.com','mailto:workwithdevit@gmail.com',44,447,432,32,20,C.muted);
await img(12,'submission/phase-2/assets/survey-production-desktop-2026-10-23.png',525,159,391,304,'Actual current public Sahara survey intake','contain',{left:.12,top:.08,right:.25,bottom:.47});
cap(12,'Current public web, 23 October. Empty survey intake.',525,481,391);


// Retained claim sources, updated where the visual evidence changed.
notes(0,'Organiser template artwork/theme is unchanged. Product name and icon: TEAM_HANDOFF.md and output/android/play-listing/icon-512.png.','Generated concept illustration reused unchanged from submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx, ppt/media/image23.png. It is not a patient photograph or deployment evidence.','Team code remains unknown.');
notes(1,'Roster/roles: original submitted 3 October deck, slide 1. Contributions: repository history, MEETING_EVIDENCE_MAP.md and TEAM_HANDOFF.md.','The user explicitly deferred photographs and biographies. Doctor speciality, experience, institutional affiliations and final individual contributions remain to confirm.','Latest symptom-tracker clinical work is assigned to Shirin and remains pending. It is not a completed clinical module.');
notes(2,'The family scenario comes from the old submitted deck and is illustrative.','Primary evidence: Thangasamy DRJ et al., Initiating and Documenting Goals of Care Discussion in Patients with Advanced Pancreatic and Colorectal Cancers, published 4 November 2025. https://journals.sagepub.com/doi/10.1177/26892820251392545 and https://pubmed.ncbi.nlm.nih.gov/41340644/ .','Single-centre quality improvement, Cancer Institute (WIA), Chennai. Reported baseline documentation0%; QI result24/26=92.3%, reported92%. Two discussions had no documentation. No separate baseline denominator is given, so do not label baseline0/26.','The intervention included a standard procedure, referral changes and a paper record. It was not Sahara and did not establish that documentation improved goal-aligned treatment. Native chart uses fractions0,.92 with percentage formatting.');
notes(3,'Primary user framing: form.md and FAMILY_CAREGIVER_DIRECTION.md. Clinician-led discussion with patient/family support.','Image is an unchanged generated concept from the old deck, ppt/media/image16.png. It is not a screenshot of current generative assistance or real patient use.','No actual hospital adoption, pilot enrolment or clinical outcome is established.');
notes(4,'Current implemented workflow: TEAM_HANDOFF.md, android/release/store-draft.json and backend/shared-care source.','This is an editable workflow diagram, not evidence of completed clinical validation. Account roles require independent approval. A patient confirms an exact released version.','Original audio/reports remain private phone copies. Shared copied facts do not imply file sync. Confirmations are not claimed to be legally certified signatures, a living will, an advance directive or a treatment order.','Generative clinical drafting remains off.');
notes(5,'Left image: submission/phase-2/assets/historical-web-source-review-2026-09-24.png. Actual historical browser capture with fictional fixtures. It shows the earlier Saath name and does not establish current authenticated clinician identity.','Right image: submission/phase-2/assets/android-care-queue-050-2026-10-19.png. Actual19 October emulator evidence from0.5sourceebffb7b.','Latest build is0.6.0(7), an installable debug test APK. No current0.6physical-device run or Play release is claimed.','Both captures retain their source labels. No screenshot, clinical output or patient record has been fabricated. An updated narrated end-to-end video remains pending.');
notes(6,'Architecture and model source: TEAM_HANDOFF.md. Local Qwen3-ASR0.6Bint8 and sherpa-onnx1.13.8, Apache2.0. MLKit OCR/translation is not described as open source.','ASR optional download987,015,347bytes,64bitprocess and minimum2.5GiB availableRAM. Optional translation pack must download before offline use.','The source-retention and human-review path is implemented. This diagram does not establish clinical language accuracy or a current0.6handset test.','Cloud records use Supabase. Original audio/report files are not synchronized. CARE integration is a proposed clinician-side extension; no CARE deployment or record exchange exists.','Free-tierGemini/publicphoto assistance stays off and must not receive patient data. SponsorSarvam access remains unconfirmed.');
notes(7,'Dates and completed outputs are sourced from PROJECT_CONTEXT_MAP.md, TEAM_HANDOFF.md and output/android/verification/features/mentor-delivery.json.','The four-week sprint is not presented as fully completed. Historical published Play versions, pending versions and0.6debug artifacts remain distinct.','Latest clinical feedback approved the survey direction but requested wording/search/map-pin refinements. This feedback is not a usability outcome or completed clinical validation.');
notes(8,'Native checks: output/android/verification/features/mentor-delivery.json,147unitchecks/25suites,zero failures/errors/skips. Instrumentation compiled but did not execute on the current APK.','Web/database: TEAM_HANDOFF.md,284webchecks,47workflow+18directory database checks dated19 October.','ASR values from output/android/verification/qwen3-asr-inference.log: English7.15sinput/5.953sinference; Hindi9.12/22.047. Hinglish from output/android/verification/qwen3-asr-low-memory-inference.json:8.275/22.531. Rounded values in table.','These are narrow desktopCPU tests with synthetic/public non-patient fixtures. The English text matched that one fixture including negation. Hindi/Hinglish had errors. No clinicalWER/CER benchmark or handheld performance result exists. Historical memory-allocation failures remain in the logs.','Usability changes from team feedback: direct routine controls, fewer repetitive inputs and visible source review. No formal participant sample or measured patient benefit is claimed.');
notes(9,'Current source retention, version confirmations, role approvals, access expiry/revocation and backend permissions: TEAM_HANDOFF.md and deployed shared-care source.','Personal-original and shared-note boundaries are exact. This does not claim cloud synchronization of audio/report originals.','Record confirmations do not establish legal validity, certified digital signatures, advance directives or treatment orders.','A clinical institution must approve the intended workflow. Where this becomes health research, its ethics committee must determine review before recruitment. ICMR handbook: https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724914217_handbook_on_icmr_ethical_guidelines_2018.pdf .');
notes(10,'This is a planning structure, not an approved quotation. No monetary amount, customer, government reimbursement or payer commitment is invented.','One-time and monthly doctor/facility costs require workload, service-level, integration, training and governance assumptions. Existing free tiers do not make the service free.','Current hosting/backend and potential CARE/EHR extension: TEAM_HANDOFF.md. Local inference avoids a per-call cloudAIfee but uses device memory/storage, optional model download and user data costs.','No fees, subscriptions, contracts, paidAIprovider or billing settings were created.');
notes(11,'Next-step gates from TEAM_HANDOFF.md. No patient enrolment or completed clinical validation is claimed.','New clinical feedback in the current conversation: Sujay approves survey direction and requests palliative wording, mobile-number removal, state-aware hospital search, hospital name/address, compulsory map-pin check and optional palliative-physician text. Implementation is owned by another workstream; this deck does not claim it is done.','Symptom-tracker clinical work is assigned to Shirin and remains incomplete.','CARE extension is a future prototype to test against patient and consent workflows, not a completed integration.','The user explicitly answeredYes to public solution-information sharing. Private data, credentials, conversations, identities and unrestricted private-code access are excluded.');
notes(12,'Current MVP: https://sahara.wedevit.in . Contact: existing Devit project correspondence address.','Code repository https://github.com/basithladdu/healthathon is private; organiser/jury access remains to arrange. No code publication is implied.','Team code and updated narrated end-to-end video remain to confirm.');
notes(5,'Actual 0.5 Android emulator screenshots captured 19 October from output/android/verification/0.5.0/ci-runtime-38882501784/: fictional-patient-hub.png, conversation-request-picked-up.png, doctor-workspace.png and care-task-completed.png. Fictional fixtures and older preview UI remain visible.','These show earlier product actions, not latest 0.6 handset evidence, an authenticated real clinician, patient use or clinical validation. The 0.6.0(7) debug APK needs current physical-phone verification.');
notes(7,'Dated actual product captures: historical family web home,24 September; Android0.5 lab view,19 October; empty current public hospital intake,23 October. Sources and hashes are in assets/source-provenance.json.','Current survey screenshot captured from https://sahara.wedevit.in/hospital-survery after production deployment dpl_CRAGwBsq21vZ9tYTSnwFNKy1uDTa. It contains no submitted personal data. New survey source includes manual hospital name/address, state-aware search, explicit pin confirmation, no mobile-number field and optional physician text. Runtime verification is separately owned by the web workstream.','Current0.6 testAPK includes local language tools and directory data. A screenshot of an earlier version does not establish a current handset test or clinical accuracy.');
notes(9,'The historical24 September note-history capture contains fictional records and the earlier name. Its self-entered reviewer label is not evidence of a verified professional identity or a certified signature. The screenshot still shows patient/family review pending.','Current shared-care source implements independent role approval, released note versions, exact-version confirmations, access expiry and revocation. These capabilities require clinical evaluation. No legally certified advance directive or treatment order is claimed.','Original audio/report files remain phone-private. AI can mishear words, numbers and negation. The intended care team must review original evidence before release. Clinical institution and, where applicable, ethics review are prerequisites to recruitment.');
notes(10,'No monetary amounts or payer commitment are established. Table distinguishes one-time setup, ongoing operations, local inference and a proposed payer model.','Map is an actual23 October MapLibre/OpenStreetMap rendered QA capture, not an enrolled hospital or adoption claim. Attribution: OpenStreetMap contributors, https://www.openstreetmap.org/copyright . Search, if used, is Photon, https://photon.komoot.io .','Clinical owner, consent, supported phones, account recovery, training, clinical validation, support and governance must be costed. Original reports/audio remain local.');
notes(11,'Next gates remain current0.6 handset validation, Hindi/English clinical language evaluation, reliable account recovery, clinical approval and a governed pilot. No patient recruitment or clinical benefit is claimed.','CARE clinician extension remains proposed, not integrated. Shirin owns the pending clinical symptom-tracker input. Doctor specialities, biographies, team code, cost amounts, updated narrated video and organiser/jury access remain to confirm.','The user explicitly answered Yes to publicly sharing solution information. Private data, credentials, identities, conversations and unrestricted private code are excluded.');
notes(12,'Product link: https://sahara.wedevit.in . Contact: workwithdevit@gmail.com . Current public web screenshot,23 October, is an empty hospital survey intake.','Repository remains private, and updated narrated video, organiser code access and team code remain preparation gaps. This local draft has not been externally submitted.');

const candidate=path.join(build,'Sahara-Phase-2-visual-candidate.pptx');
await(await PresentationFile.exportPptx(p)).save(candidate);
const{finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const result=await finalizePresentation({workspaceDir:root,candidatePath:candidate,finalPath:final,explicitTotalSlideCount:13,sourceTemplatePath:src,requiredNativeTableOwnerSlides:[2,9,11],requiredNativeChartOwnerSlides:[3],materializeLiteralChartWorkbooks:true,pythonExecutable:'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit','--require-native-table-slide','2','--require-native-table-slide','9','--require-native-table-slide','11'],fontPolicy:{basis:'reference',families:['Arial','Calibri'],referencePath:src,referenceSha256:crypto.createHash('sha256').update(await fs.readFile(src)).digest('hex')},verifyArtifactToolImport:true,receiptPath:path.join(build,`visual-draft-${version}-validation.json`)});
console.log(JSON.stringify(result));
