// Usage: node tools/stills.mjs 120 400 ...   (frame numbers)  -> out/stills/f####.jpg
import {bundle} from '@remotion/bundler';
import {renderStill, selectComposition} from '@remotion/renderer';
import path from 'node:path';
import fs from 'node:fs';
import {execSync} from 'node:child_process';

execSync('node tools/scan-clips.mjs', {stdio: 'inherit'});
const frames = process.argv.slice(2).map(Number);
const dir = path.resolve('out/stills');
fs.mkdirSync(dir, {recursive: true});
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts'), publicDir: path.resolve('public')});
const comp = await selectComposition({serveUrl, id: 'Film'});
for (const f of frames) {
  await renderStill({composition: comp, serveUrl, frame: f, output: path.join(dir, `f${String(f).padStart(4, '0')}.jpg`), imageFormat: 'jpeg', jpegQuality: 80, scale: 1});
  console.log('ok', f);
}
