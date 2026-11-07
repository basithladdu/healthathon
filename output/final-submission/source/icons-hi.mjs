import { chromium } from 'playwright-core';
import { readFileSync } from 'node:fs';
const map = JSON.parse(readFileSync('icon-map.json', 'utf8'));
const b = await chromium.launch({ executablePath: 'C:/Users/basit/AppData/Local/ms-playwright/chromium-1200/chrome-win64/chrome.exe' });
const pg = await b.newPage({ viewport: { width: 256, height: 256 } });
for (const [n, name, hex] of map) {
  let svg = readFileSync(`tabler-src/package/icons/outline/${name}.svg`, 'utf8');
  svg = svg.replace(/width="24"/, 'width="256"').replace(/height="24"/, 'height="256"').replace(/stroke="currentColor"/, `stroke="#${hex}"`);
  await pg.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
  await pg.locator('svg').screenshot({ path: `assets/icons/image${n}.png`, omitBackground: true });
}
await b.close(); console.log('done', map.length);
