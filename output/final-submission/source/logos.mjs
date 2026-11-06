import { chromium } from 'playwright-core';
import fs from 'fs';
const P='logos-src/package/';
const data=JSON.parse(fs.readFileSync(P+'_data/simple-icons.json','utf8'));
const list=data.icons||data;
const names=process.argv.slice(2);
const b=await chromium.launch({executablePath:'C:/Users/basit/AppData/Local/ms-playwright/chromium-1200/chrome-win64/chrome.exe'});
const pg=await b.newPage({viewport:{width:256,height:256}});
fs.mkdirSync('assets/logos',{recursive:true});
for(const n of names){
  let svg=fs.readFileSync(P+'icons/'+n+'.svg','utf8');
  const slug=s=>s.title.toLowerCase().replace(/\+/g,'plus').replace(/\./g,'dot').replace(/&/g,'and').replace(/[^a-z0-9]/g,'');
  const e=list.find(i=>(i.slug||slug(i))===n); let hex=e?e.hex:'333333';
  if(['000000','FFFFFF'].includes(hex.toUpperCase())) hex='111111';
  svg=svg.replace('<svg ','<svg fill="#'+hex+'" width="256" height="256" ');
  await pg.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
  await pg.locator('svg').screenshot({path:'assets/logos/'+n+'.png',omitBackground:true});
  console.log(n,hex);
}
await b.close();
