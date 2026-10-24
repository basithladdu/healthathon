async(page)=>{
 const output='F:/CODE/healthathon/output/playwright/pain-body-2026-10-24/';
 const fixture=[{"symptom": "Pain", "severity": 7, "date": "2026-10-23", "startedOn": "2026-10-23", "note": "QA fictional observation only", "scale": "sahara-personal-nrs-v1", "time": "08:30", "recall": "now", "respondent": "patient", "painLocation": "", "painBodySites": ["left-arm"], "painBodyPoints": [{"site": "left-arm", "x": 0.794, "y": 0.523, "z": -0.188}], "painBodyModel": "sahara-body-v1", "painTriggers": [], "id": "60cd6de8-d49c-4668-bdac-0d8172a18d80", "patientId": "CANCER-20418", "author": "Meera Raghavan", "recordedAt": "2026-10-24T13:47:29.138Z"}, {"symptom": "Pain", "severity": 3, "date": "2026-10-24", "startedOn": "2026-10-23", "note": "", "scale": "sahara-personal-nrs-v1", "time": "10:30", "recall": "now", "respondent": "patient", "painLocation": "", "painBodySites": ["left-arm"], "painBodyPoints": [{"site": "left-arm", "x": 0.794, "y": 0.523, "z": -0.188}], "painBodyModel": "sahara-body-v1", "painTriggers": [], "selfReportedChange": "better", "comparedWithId": "60cd6de8-d49c-4668-bdac-0d8172a18d80", "id": "becb3f46-65cb-43ce-99ee-6e96436f39da", "patientId": "CANCER-20418", "author": "Meera Raghavan", "recordedAt": "2026-10-24T13:59:46.613Z"}];
 const report={stage:'css-regression',scope:'One ephemeral browser, actual local care-tools UI and fictional prior UI-saved pain records only',checks:[],errors:[]};
 const check=(name,pass,detail)=>{report.checks.push({name,pass,detail});if(!pass)throw new Error(name)};
 await page.addInitScript(()=>{
  window.__appQAViewerStates=[];window.__cssInputPicks=[];window.__cssHistoryPicks=[];
  window.addEventListener('message',event=>{
   if(event.origin!==location.origin)return;
   if(event.data?.type==='state')window.__appQAViewerStates.push(event.data);
   if(event.data?.type!=='pick')return;
   const history=document.querySelector('iframe[title="Rotate the saved pain map"]');
   if(history&&event.source===history.contentWindow)window.__cssHistoryPicks.push(event.data);
   else window.__cssInputPicks.push(event.data);
  });
 });
 const records=()=>page.evaluate(()=>new Promise(resolve=>{const req=indexedDB.open('saanthvana-family-care',1);req.onsuccess=()=>{const db=req.result;const get=db.transaction('care','readonly').objectStore('care').get('symptoms');get.onsuccess=()=>{db.close();resolve(get.result.value)}}}));
 const dimensions=(frame)=>frame.locator('#surface').evaluate(surface=>({surfaceWidth:surface.clientWidth,surfaceHeight:surface.clientHeight,viewportWidth:innerWidth,viewportHeight:innerHeight,canvasWidth:surface.querySelector('canvas')?.clientWidth,canvasHeight:surface.querySelector('canvas')?.clientHeight,documentWidth:document.documentElement.scrollWidth,cssHeight:getComputedStyle(surface).height,bodyPosition:getComputedStyle(document.body).position}));
 const state=(frame)=>frame.locator('body').evaluate(()=>window.__appQAViewerStates.at(-1));
 try{
 await page.setViewportSize({width:1440,height:1000});
 await page.goto('https://sahara.wedevit.in/how-i-feel');
 await page.evaluate(async(saved)=>{
  const db=await new Promise((resolve,reject)=>{const req=indexedDB.open('saanthvana-family-care',1);req.onupgradeneeded=()=>{if(!req.result.objectStoreNames.contains('care'))req.result.createObjectStore('care')};req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error)});
  await new Promise((resolve,reject)=>{const tx=db.transaction('care','readwrite');tx.objectStore('care').put({version:1,value:saved},'symptoms');tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)});db.close();
  sessionStorage.setItem('saanthvana-care-viewer',JSON.stringify({sessionType:'patient',selectedId:'CANCER-20418',hindi:false,name:'Meera Raghavan'}));
 },fixture);
 await page.reload();
 await page.getByRole('heading',{name:'Pain history in 3D',exact:true}).waitFor();
 const history=page.locator('.pain-history');
 const historyFrame=page.frameLocator('iframe[title="Rotate the saved pain map"]');
 await historyFrame.locator('#status').filter({hasText:'Saved pain map'}).waitFor();
 const css=await page.request.get('https://sahara.wedevit.in/pain-body-viewer/viewer.css');
 report.cssText=await css.text();
 check('Live corrected CSS has explicit viewport height and positioned body',css.status()===200&&report.cssText.includes('height:100vh')&&report.cssText.includes('height:100dvh')&&report.cssText.includes('body{position:relative}'));
 for(const width of [1440,390]){
  await page.setViewportSize({width,height:width===390?844:1000});
  await historyFrame.locator('canvas').scrollIntoViewIfNeeded();
  const d=await dimensions(historyFrame);
  check(width+'px saved viewer has positive surface and canvas height',d.surfaceHeight>200&&d.canvasHeight>200&&d.canvasHeight===d.viewportHeight&&d.bodyPosition==='relative',d);
  const beforeRecords=JSON.stringify(await records());
  await history.getByRole('button',{name:/7\/10/}).click();
  await page.waitForFunction(()=>document.querySelector('iframe[title="Rotate the saved pain map"]').contentWindow.__appQAViewerStates.at(-1)?.rating===7);
  check(width+'px historical7 restores actual saved point',JSON.stringify((await state(historyFrame)).points)===JSON.stringify(fixture.find(r=>r.severity===7).painBodyPoints)&&(await state(historyFrame)).readOnly===true);
  await historyFrame.locator('canvas').scrollIntoViewIfNeeded();
  const canvas=historyFrame.locator('canvas');const box=await canvas.boundingBox();const before=await canvas.screenshot();
  await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);await page.mouse.down();await page.mouse.move(box.x+box.width*.5+65,box.y+box.height*.5,{steps:8});await page.mouse.up();
  const after=await canvas.screenshot();
  await page.mouse.click(box.x+box.width*.5,box.y+box.height*.35);
  check(width+'px history actually rotates while tap and orbit leave records unchanged',!before.equals(after)&&(await page.evaluate(()=>window.__cssHistoryPicks.length))===0&&beforeRecords===JSON.stringify(await records()));
  await history.getByRole('button',{name:/3\/10/}).click();
  await page.waitForFunction(()=>document.querySelector('iframe[title="Rotate the saved pain map"]').contentWindow.__appQAViewerStates.at(-1)?.rating===3);
  check(width+'px date chooser restores saved3 and exact points',(await state(historyFrame)).rating===3&&JSON.stringify((await state(historyFrame)).points)===JSON.stringify(fixture.find(r=>r.severity===3).painBodyPoints));
  const overflow=await page.evaluate(()=>({viewport:innerWidth,scrollWidth:document.documentElement.scrollWidth}));
  check(width+'px document and iframe have no horizontal overflow',overflow.scrollWidth<=width&&d.documentWidth<=d.viewportWidth,{page:overflow,viewer:d});
  await history.screenshot({path:output+'app-css-history-'+width+'.png'});
 }
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'Update rating: Pain, '+fixture.find(r=>r.severity===3).date,exact:true}).click();
 const form=page.getByRole('form',{name:'Add a feeling note',exact:true});
 await form.getByRole('button',{name:'Pain: 4/10',exact:true}).click();
 const input=page.frameLocator('iframe[title="Rotate and select a pain location"]');
 await input.locator('#status').filter({hasText:'Drag to rotate'}).waitFor();
 await input.locator('canvas').scrollIntoViewIfNeeded();
 const inputDimensions=await dimensions(input);
 check('390px editable viewer retains positive surface/canvas height',inputDimensions.surfaceHeight>200&&inputDimensions.canvasHeight>200,inputDimensions);
 const inputCanvas=input.locator('canvas');const b=await inputCanvas.boundingBox();
 await page.mouse.click(b.x+b.width*.5,b.y+b.height*.35);
 await page.waitForFunction(()=>window.__cssInputPicks.length>0);
 const pick=await page.evaluate(()=>window.__cssInputPicks.at(-1));
 await page.waitForFunction(()=>{const f=document.querySelector('iframe[title="Rotate and select a pain location"]');const p=window.__cssInputPicks.at(-1);return f.contentWindow.__appQAViewerStates.at(-1)?.points?.some(v=>v.site===p.site&&v.x===p.point.x&&v.y===p.point.y&&v.z===p.point.z)});
 check('Actual model tap reaches editable form with normalized point',!!pick?.site&&Number.isFinite(pick.point.x)&&Number.isFinite(pick.point.y)&&Number.isFinite(pick.point.z),pick);
 const n=await page.evaluate(()=>window.__cssInputPicks.length);const beforeOrbit=await inputCanvas.screenshot();
 await page.mouse.move(b.x+b.width*.5,b.y+b.height*.5);await page.mouse.down();await page.mouse.move(b.x+b.width*.5-70,b.y+b.height*.5,{steps:8});await page.mouse.up();
 const afterOrbit=await inputCanvas.screenshot();
 check('Editable model actually rotates without accidental extra selection',!beforeOrbit.equals(afterOrbit)&&(await page.evaluate(()=>window.__cssInputPicks.length))===n);
 check('Unsaved editable tap does not alter previously saved history',JSON.stringify(await records())===JSON.stringify(fixture));
 await form.locator('.pain-body-picker').screenshot({path:output+'app-css-input-390.png'});
 await page.reload();await page.getByRole('heading',{name:'Pain history in 3D',exact:true}).waitFor();
 check('Saved histories remain intact after reload',JSON.stringify(await records())===JSON.stringify(fixture));
 report.stage='complete-css-regression';
 }catch(e){report.failure=String(e);}
 return report;
}
