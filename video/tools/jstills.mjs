// Usage: node tools/jstills.mjs <outdir> <sec> <sec> ...   -> <outdir>/t0012.50.jpg  (Journey composition, seconds)
// Add --sheet to tile them into <outdir>/sheet.jpg (needs ffmpeg).
import {bundle} from '@remotion/bundler';
import {renderStill, selectComposition, openBrowser} from '@remotion/renderer';
import path from 'node:path';
import fs from 'node:fs';
import {execSync} from 'node:child_process';

const args = process.argv.slice(2);
const sheet = args.includes('--sheet');
const rest = args.filter((a) => a !== '--sheet');
const dir = path.resolve(rest[0]);
const secs = rest.slice(1).map(Number);
fs.mkdirSync(dir, {recursive: true});
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts'), publicDir: path.resolve('public')});
const browser = await openBrowser('chrome', {chromiumOptions: {gl: 'angle'}});
const comp = await selectComposition({serveUrl, id: 'Journey', puppeteerInstance: browser});
const files = [];
for (const s of secs) {
  const frame = Math.min(comp.durationInFrames - 1, Math.round(s * 30));
  const out = path.join(dir, `t${s.toFixed(2).padStart(6, '0')}.jpg`);
  await renderStill({composition: comp, serveUrl, frame, output: out, imageFormat: 'jpeg', jpegQuality: 82, scale: 0.5, puppeteerInstance: browser});
  files.push(out);
  console.log('ok', s);
}
await browser.close({silent: true});
if (sheet && files.length) {
  const cols = 4;
  const list = path.join(dir, 'list.txt');
  // label each still with its time using drawtext, then tile
  const tmp = files.map((f, i) => {
    const o = path.join(dir, `_lab${i}.jpg`);
    const lab = path.basename(f).slice(1, -4);
    execSync(`ffmpeg -v error -y -i "${f}" -vf "scale=480:-1,drawtext=text='${lab}s':x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.6" "${o}"`);
    return o;
  });
  const rows = Math.ceil(tmp.length / cols);
  while (tmp.length < rows * cols) tmp.push(tmp[tmp.length - 1]);
  fs.writeFileSync(list, tmp.map((f) => `file '${f.replace(/\\/g, '/')}'`).join('\n'));
  execSync(`ffmpeg -v error -y -f concat -safe 0 -i "${list}" -vf "tile=${cols}x${rows}" -frames:v 1 "${path.join(dir, 'sheet.jpg')}"`);
  tmp.forEach((f) => fs.existsSync(f) && fs.unlinkSync(f));
  console.log('sheet', path.join(dir, 'sheet.jpg'));
}
