import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
await mkdir('public/pain-body-viewer', { recursive: true });
await build({
  entryPoints: ['scripts/pain-body-viewer-entry.mjs'], bundle: true, format: 'iife', target: ['es2020'],
  minify: true, legalComments: 'linked', outfile: 'public/pain-body-viewer/viewer.js',
});
await copyFile('node_modules/three/LICENSE', 'public/pain-body-viewer/THREE-LICENSE.txt');
