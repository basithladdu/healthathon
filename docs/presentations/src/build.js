// Renders care-team-briefing.html to ../Sahara_CARE_Team_Briefing_2026-10-27.pdf (1280x720 pages).
const path = require('path');
let playwright;
try { playwright = require('playwright'); } catch { playwright = require('/opt/node22/lib/node_modules/playwright'); }
(async () => {
  const browser = await playwright.chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.goto('file://' + path.join(__dirname, 'care-team-briefing.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({ path: path.join(__dirname, '..', 'Sahara_CARE_Team_Briefing_2026-10-27.pdf'),
    width: '1280px', height: '720px', printBackground: true, margin: { top: 0, right: 0, bottom: 0, left: 0 } });
  await browser.close();
})();
