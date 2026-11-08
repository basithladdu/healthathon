// Maps feature numbers to whatever clip files exist in public/clips (prefix fNN-). Run before still/render.
import fs from 'node:fs';
import path from 'node:path';
import {execSync} from 'node:child_process';
const dir = path.resolve('public/clips');
const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.mp4')) : [];
const out = {};
for (const f of files) {
  const m = /^f(\d{2})-/.exec(f);
  if (m) out[String(parseInt(m[1], 10))] = f;
}
const meta = {};
for (const f of files) {
  try {
    meta[f] = parseFloat(execSync(`ffprobe -v error -show_entries format=duration -of csv=p=0 "${path.join(dir, f)}"`).toString());
  } catch {}
}
fs.writeFileSync(path.resolve('src/clipMeta.json'), JSON.stringify(meta, null, 2));
fs.writeFileSync(path.resolve('src/clipIndex.json'), JSON.stringify(out, null, 2));
const missing = [];
for (let n = 1; n <= 15; n++) if (!out[String(n)]) missing.push(n);
console.log('clips found:', Object.keys(out).length, 'missing features:', missing.join(',') || 'none');
fs.writeFileSync(path.resolve('out/.missing.txt'), missing.join(','));
