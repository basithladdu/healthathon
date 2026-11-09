import fs from 'node:fs';
const tl = JSON.parse(fs.readFileSync('src/timeline.json','utf8'));
let cur=0; const rows=[]; const add=(n,fr)=>{rows.push([n,cur,fr]);cur+=fr;};
tl.opening.forEach(o=>add(o.id,o.frames));
tl.chapters.forEach(c=>{add('CH'+c.n+' '+c.name,tl.chapterFrames);c.features.forEach(f=>add('F'+f,tl.features[f].frames));});
tl.close.forEach(o=>add(o.id,o.frames));
for(const [n,s,fr] of rows) console.log(n.padEnd(34),String(s).padStart(5),String(fr).padStart(4),(s/30).toFixed(1).padStart(6)+'s', (fr/30).toFixed(1)+'s');
console.log('total',cur,(cur/30).toFixed(1));
