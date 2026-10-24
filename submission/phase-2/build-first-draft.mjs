import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { FileBlob, PresentationFile } from 'file:///C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';

// Organiser template remains unchanged. This is an editable first draft, not a submission.
const workspaceDir = 'F:/CODE/healthathon';
const skillDir = 'C:/Users/basit/.codex/plugins/cache/openai-primary-runtime/presentations/26.1017.22136/skills/presentations';
const sourcePath = path.join(workspaceDir, 'source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx');
const buildDir = path.join(workspaceDir, 'submission/phase-2/build');
const finalPath = path.join(workspaceDir, 'submission/phase-2/final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pptx');
const p = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
process.on('uncaughtException',error=>{ console.error(error.message); console.error(error.stack?.split('\n').slice(1,4).join('\n')); process.exitCode=1; });
const inspected = await p.inspect({select:'$.slides[*]',include:['id','index','elements.id','elements.kind','elements.text']});
const slides = inspected.records;
const pt = x => x * 4 / 3;

function find(slideIndex, text) {
  const record = slides[slideIndex].elements.find(e => e.text === text || e.text?.startsWith(text));
  if (!record) throw new Error(`Missing template anchor on slide ${slideIndex+1}: ${text}`);
  return p.resolve(record.id);
}
function replace(slideIndex, before, after, options={}) {
  const record=slides[slideIndex].elements.find(e=>e.text===before||e.text?.startsWith(before));
  const shape=find(slideIndex,before);
  const original=record.text;
  // The public Text facade accepts replacement while retaining template styles.
  shape.text.replace(original,after);
  if(options.frame) shape.position=Object.fromEntries(Object.entries(options.frame).map(([k,v])=>[k,pt(v)]));
  if(options.fontSize) shape.text.style={fontFamily:options.fontFamily??'Arial',fontSizePt:options.fontSize,...options.style};
  return shape;
}
function body(slideIndex, before, text, height=315) {
  return replace(slideIndex,before,text,{frame:{left:39.6,top:155.45984,width:874.4428,height},fontSize:24});
}
function text(slideIndex, value, frame, fontSize=18, color='#243047') {
 const s=p.slides.items[slideIndex].shapes.add({geometry:'textbox',position:Object.fromEntries(Object.entries(frame).map(([k,v])=>[k,pt(v)])),fill:'none',line:{fill:'none',width:0}});
 s.text=value;s.text.style={fontFamily:'Arial',fontSizePt:fontSize,color};return s;
}
function notes(slideIndex, values) { p.slides.items[slideIndex].speakerNotes.text=values.join('\n'); }

replace(0,'[Solution Name]','Sahara');
replace(0,'[Short subtitle','A Palliative Care Companion');
replace(0,'TEAM NAME','TEAM NAME  Zeros and Ones');
replace(0,'TEAM CODE','TEAM CODE  To confirm');
replace(0,'HEALTH-A-THON 2026','HEALTH-A-THON 2026  •  PHASE 2 DRAFT');
notes(0,['Draft identity: current product name from TEAM_HANDOFF.md. Team name from the submitted 3 October deck.','Team code is not present in inspected source records and remains to confirm.','Original organiser template: source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx.']);

replace(1,'Introduce the team members','');
const teamValues=[
 ['Team member','Role in prior submission','Contribution / detail'],
 ['Shaik Abdul Basith','Technical lead','Product and engineering'],
 ['Shaik Muhammad Awaiz','Technical member','Web and Android development'],
 ['Dr Sharada Vinod Kutty','Doctor partner','Clinical workflow input'],
 ['Dr Sujay Halkur Shankar','Clinician','Clinical concept and care framework'],
 ['Shirin Ayub','Medical student','Team member'],
];
const team=p.slides.items[1].tables.add({rows:6,columns:3,left:pt(39.6),top:pt(122),width:pt(874.44),height:pt(235),columnWidths:[pt(300),pt(235),pt(339.44)],values:teamValues});
team.borders.assign({style:'solid',fill:'#D9E0EA',width:0.75});
team.cells.block({row:0,column:0,rowCount:6,columnCount:3}).assign({fill:'#FFFFFF',textStyle:{fontFamily:'Arial',fontSize:pt(20),color:'#243047'},margins:{left:9,right:9,top:8,bottom:8}});
team.cells.block({row:0,column:0,rowCount:1,columnCount:3}).assign({fill:'#EEF3FA',textStyle:{fontFamily:'Arial',fontSize:pt(20),bold:true,color:'#253DAE'}});
text(1,'Team photos and individual write-ups will follow the team discussion.',{left:39.6,top:390,width:874,height:30},18);
text(1,'Doctor specialities, experience and institutional affiliations remain to confirm.',{left:39.6,top:432,width:874,height:40},18);
notes(1,['Roster and roles: original submitted deck, submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx, slide 1.','Engineering contributions are evidenced by repository commits and TEAM_HANDOFF.md. Clinical concept/input: MEETING_EVIDENCE_MAP.md.','The user explicitly deferred photos and teammate write-ups. No doctor speciality or institution is inferred.']);

body(2,'Clearly define the healthcare problem',
 'Families may face an emergency without a clear, current record of the patient’s care preferences.\nOur clinicians identified inconsistent goals-of-care conversations, documentation and later retrieval. These are team observations, not a measured baseline.\nSahara connects the reviewed conversation to family access and follow-up tasks.\nA clinical pilot must establish whether this improves retrieval time, understanding and continuity.');
notes(2,['Problem source: MEETING_EVIDENCE_MAP.md, clinician discussion from 24 August and subsequent September follow-ups.','Previous submitted deck slides 2–3 supplies the scenario and literature context. The illustrative scenario is not a reported patient outcome.','No prevalence estimate or impact claim is carried forward without fresh primary-source verification. Proposed metrics are hypotheses, not measured results.']);

body(3,'Identify the primary users',
 'Primary users: people with advanced cancer and their family caregivers.\nThe clinician leads the goals-of-care discussion and reviews each released record.\nClinic and home use: prepare for a conversation, retain the reviewed note and keep track of agreed care tasks.\nAt a later care encounter, the patient chooses who may retrieve the current version.\nHindi and English support aims to reduce language friction.');
notes(3,['Current adopted Cancer / Patient-Caregiver / Family & Caregiver Support framing: form.md and FAMILY_CAREGIVER_DIRECTION.md.','Implementation and access boundaries: TEAM_HANDOFF.md. A verified clinician role requires independent approval.','No hospital deployment or real-patient usage is claimed.']);

body(4,'Describe the solution and how it addresses',
 'Record with consent or enter the conversation. Keep original words and source links.\nReview the note, release an immutable version, then capture patient and family confirmation against that exact version.\nShare by permission, with expiry and revocation. Assign follow-up tasks to named accounts.\nDaily support includes medicines, appointments, documents and a source-linked palliative-care directory.\nExpected benefit: less repeated explanation and a retrievable care record. Impact remains to be measured.');
notes(4,['Current implementation: TEAM_HANDOFF.md and android/release/store-draft.json.','Original report/audio files remain private phone copies. Shared note facts do not imply original file synchronization.','Care record acknowledgements do not establish a legally valid advance directive, certified digital signature or treatment order.']);

replace(5,'Include screenshots of the working MVP','');
const screenshotBase=path.join(workspaceDir,'output/android/verification/0.5.0/ci-runtime-38882501784');
for(const [i,name] of ['care-task-completed.png','calendar-dated-visit.png','lab-history-series.png'].entries()) {
  p.slides.items[5].images.add({blob:new Uint8Array(await fs.readFile(path.join(screenshotBase,name))),contentType:'image/png',alt:['Care task marked completed with a fictional record','Dated clinic follow-up in the calendar','Lab history with copied fictional values'][i],fit:'contain',position:{left:pt(43+i*193),top:pt(92),width:pt(170),height:pt(377.78)}});
}
text(5,'Actual Android screens\n0.5 emulator, 19 October\nFictional records\n\nCurrent 0.6 test APK:\nphone testing pending\n\nWeb MVP:\nsahara.wedevit.in\n\nUpdated video: pending',{left:642,top:118,width:270,height:340},20);
notes(5,['Actual screenshots: output/android/verification/0.5.0/ci-runtime-38882501784/care-task-completed.png, calendar-dated-visit.png, lab-history-series.png. Source ebffb7b.','Captured and inspected in 19 October emulator verification. Fictional records. These are not screenshots of the latest 0.6 build and do not establish patient use.','Latest build: output/android/verification/features/mentor-delivery.json. 0.6.0 (7) debug APK, not uploaded to Play, no current phone test.','Public web MVP: https://sahara.wedevit.in/. An updated end-to-end narrated demo URL remains pending.']);

body(6,'Identify the AI models',
 'Android: Kotlin, Jetpack Compose, Room and MapLibre Native. Web: React/Next.js with shared Supabase records.\nOptional local speech: Qwen3-ASR-0.6B int8 through sherpa-onnx. Original audio and reviewed corrections remain attributable.\nBundled Latin/Devanagari OCR and optional on-device Hindi/English translation assist document review.\nDirectory: 1,631 public listings from Pallium India and IAPC sources.\nCARE integration is proposed. Generative clinical drafting remains off.',300);
replace(6,'Keep this concise.','AI output requires human review. No diagnosis or treatment recommendation.',{frame:{left:44.64,top:470,width:823.3,height:29.08},fontSize:18,fontFamily:'Calibri'});
notes(6,['Technology and model identities: TEAM_HANDOFF.md, android/release/store-draft.json.','Qwen3-ASR model and sherpa-onnx are Apache-2.0. Google ML Kit is not described as open source.','Speech pack: 987,015,347 bytes, 64-bit process and at least 2.5 GiB available RAM. Translation needs an optional model download.','Public cloud photo scanning and cloud patient audio remain off. No patient inputs are sent to free-tier Gemini. Sponsor Sarvam access is not assumed available.','Directory provenance: android/directory/README.md and android/app/src/main/assets/india-directory-sources.json. Listings are not an exhaustive census, verified partners or live medicine stock.','CARE/OHC: proposed clinician-side integration only. No connection, deployment or patient-record exchange exists.']);

body(7,'Show key milestones',
 'September to 3 October: clinician framework, family workflow and the original Round 1 deck.\n9–14 October: native Android app, shared records and initial Play release work.\n16–19 October: version confirmations, permissions, task transitions, calendar/labs and backend role checks.\n20 October: local Hindi/English tools, national directory and a 0.6 test APK with 147 unit checks.\nNext: current phone tests, clinical language evaluation and a governed pilot.',290);
replace(7,'Focus on milestones achieved,','Scope changes: stronger source attribution and local processing. Clinical drafting and CARE integration remain incomplete.',{frame:{left:50.48,top:461,width:804.52,height:44},fontSize:18,fontFamily:'Calibri'});
notes(7,['Milestone dates are from PROJECT_CONTEXT_MAP.md, the original 3 October submission, TEAM_HANDOFF.md and output/android/verification/features/mentor-delivery.json.','This is progress to date. The four-week sprint is still in progress and later weeks are not presented as completed.','0.6 packaging is a debug test build. Historical Play versions and review outcomes remain separate.']);

body(8,'Describe how the solution was tested',
 '20 October: 147 native unit checks passed. Instrumentation compiled but did not run on the current APK.\n19 October: 284 web checks plus 65 workflow/directory database checks.\nDesktop ASR: 7.15 s English clip transcribed exactly in 5.95 s. A 9.12 s Hindi clip took 22.05 s and contained word errors.\nAn 8.28 s public Hinglish clip took 22.53 s and also contained errors.\nNo clinical accuracy score or patient outcome is established.',285);
replace(8,'Teams may use up to two slides','Still required: current handset tests, clinician-reviewed bilingual cases and patient/family usability validation.',{frame:{left:50.48,top:461,width:869.68,height:44},fontSize:18,fontFamily:'Calibri'});
notes(8,['Native counts/build: output/android/verification/features/mentor-delivery.json, 20 October. Unit checks do not establish speech recognition quality or runtime behavior.','Web/database counts: TEAM_HANDOFF.md dated 19 October. 47 workflow and 18 directory database checks = 65.','ASR source: output/android/verification/qwen3-asr-inference.log. Actual desktop CPU tests, not Android tests. English-synthetic: 7.15 s input, 5.953 s inference, text exactly matched that fixture including negation. Hindi-public: 9.12 s input, 22.047 s inference, substitution error in original output.','Hinglish source: output/android/verification/qwen3-asr-low-memory-inference.json, 8.275 s input and 22.531 s inference. Mixed script/word errors remain. No WER/CER clinical benchmark was completed.','Historical memory-allocation failures are retained in the evidence. App uses pinned models, local guards and mandatory review.','User-feedback changes: fewer visible form choices, retained source originals and direct routine controls, as recorded in TEAM_HANDOFF.md. No formal usability sample size or benefit estimate exists.']);

body(9,'Explain relevant data privacy',
 'Obtain recording consent and bind audio, speaker attribution and review to the correct person.\nKeep source audio/text, corrections and immutable note versions. AI may mishear words, numbers or negation.\nA clinician reviews release. Patient confirmation applies to the exact reviewed version.\nEnforce account roles and permissioned sharing with expiry/revocation. Original files stay on the phone.\nBefore patient use: validate recovery/deletion, language comprehension, access controls and clinical oversight.');
notes(9,['Current safeguards and limitations: TEAM_HANDOFF.md and deployed workflow source. No unsupported claim of legal/compliance certification is made.','The system does not diagnose, choose treatment, produce autonomous clinical advice or create a legal treatment order.','Clinical/ethics review before a health research pilot: ICMR handbook https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724914217_handbook_on_icmr_ethical_guidelines_2018.pdf.','Hindi/English does not establish coverage for other Indian languages, universal accessibility or clinical language accuracy.']);

body(10,'Explain what would be required to deploy',
 'Pilot requirements: clinical owner, agreed consent, approved users, phone checks and a supported recovery/deletion process.\nIntegration: Sahara already shares approved note data across its own web and native apps. CARE/EHR integration remains proposed.\nOne-time budget: device checks, onboarding, clinician validation and integration. Costing remains to confirm.\nOngoing budget: hosting, database, storage, support and governance. Local inference has no per-call cloud AI fee.\nSustainability proposal: facility/NGO/government support. Patient access and final pricing require team agreement.');
notes(10,['This slide is a first planning draft. No approved monetary budget, procurement quote, service price, paid customer or payer commitment is recorded.','Required cost fields remain explicit gaps: one-time deployment estimate, per-transaction cost if applicable and monthly doctor/facility price. Do not infer that existing free tiers make operation free.','Architecture assumptions: existing user phones, optional local model download and server records. Device/data costs, clinician time, hosting and support remain.','No fee, subscription, billing, contract or paid cloud setup is authorized or created by this draft.']);

body(11,'Explain what the team plans',
 'Complete Android phone checks using fictional inputs and clinician-reviewed Hindi/English cases.\nFinish signup/recovery and independent clinician verification before patient use.\nAgree a pilot setting, baseline measures and consent/oversight with clinical partners.\nPrototype a CARE clinician extension and test patient and consent workflows.\nPublic sharing of solution information on Health-a-thon or other websites: Yes.');
notes(11,['The user explicitly answered Yes to the template’s public solution-information question in this task.','Yes applies to the solution summary. It does not authorize publication of credentials, government IDs, patient material, raw private chats, private support cases or unrestricted access to the private repository.','Remaining pilot/account/runtime requirements: TEAM_HANDOFF.md. Proposed work is not presented as achieved.']);

replace(12,'[Team name]','Zeros and Ones  •  Team code to confirm');
const contact=replace(12,'[Contact email]',
 'workwithdevit@gmail.com\nMVP: https://sahara.wedevit.in\nDemo video: updated link pending\nCode: github.com/basithladdu/healthathon (private)',{frame:{left:85,top:300,width:790,height:100},fontSize:17});
contact.text=[
 [{run:'workwithdevit@gmail.com',style:{fontSize:'17pt',typeface:'Arial',color:'#657388'},link:{uri:'mailto:workwithdevit@gmail.com',isExternal:true}}],
 [{run:'MVP: https://sahara.wedevit.in',style:{fontSize:'17pt',typeface:'Arial',color:'#657388'},link:{uri:'https://sahara.wedevit.in',isExternal:true}}],
 [{run:'Demo video: updated link pending',style:{fontSize:'17pt',typeface:'Arial',color:'#657388'}}],
 [{run:'Code: github.com/basithladdu/healthathon (private)',style:{fontSize:'17pt',typeface:'Arial',color:'#657388'},link:{uri:'https://github.com/basithladdu/healthathon',isExternal:true}}],
];
notes(12,['MVP and repo: TEAM_HANDOFF.md. Contact is the existing Devit project/release correspondence address.','Private repository access for jury review remains to arrange. The URL alone does not make code public or accessible without approval.','Updated narrated demonstration link and team code remain pending.']);

await fs.writeFile(path.join(buildDir,'content-and-claims.json'), JSON.stringify({sourcePath,slides:13,teamPhotosPending:true,teamCodePending:true,doctorSpecialityPending:true,costingPending:true,currentDeviceValidationPending:true,careIntegration:'proposed, not implemented',publicSolutionInformation:'Yes - explicit user direction; excludes private material',createdAs:'first filled draft, not submitted'},null,2));
const candidatePath=path.join(buildDir,'Sahara-Phase-2-candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidatePath);
const {finalizePresentation}=await import(pathToFileURL(path.join(skillDir,'container_tools/artifact_tool_utils.mjs')).href);
const result=await finalizePresentation({workspaceDir,candidatePath,finalPath,explicitTotalSlideCount:13,sourceTemplatePath:sourcePath,requiredNativeTableOwnerSlides:[2],requiredNativeChartOwnerSlides:[],expectedSlideSizeEmu:'12192000,6858000',pythonExecutable:'C:/Users/basit/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',integrityValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit','--require-native-table-slide','2'],fontPolicy:{basis:'reference',families:['Arial','Calibri'],referencePath:sourcePath,referenceSha256:crypto.createHash('sha256').update(await fs.readFile(sourcePath)).digest('hex')},verifyArtifactToolImport:true,receiptPath:path.join(buildDir,'first-draft-v2-validation.json')});
console.log(JSON.stringify(result));
