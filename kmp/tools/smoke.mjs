// Opens the built web app in headless Chromium at phone and desktop sizes
// and fails if it reports an error or shows the start-up error text.
// Usage: node smoke.mjs <url> <screenshot-dir>
import { chromium } from 'playwright';

const [url, outDir] = process.argv.slice(2);
const sizes = { phone: { width: 390, height: 844 }, desktop: { width: 1440, height: 900 } };
// The runner's own Chrome, so no browser download is needed.
const browser = await chromium.launch({ channel: 'chrome' });
let failed = false;
for (const [name, viewport] of Object.entries(sizes)) {
  const page = await browser.newPage({ viewport });
  const crashes = [];
  const logs = [];
  page.on('pageerror', (e) => crashes.push(`${e.message}\n${e.stack ?? ''}`));
  page.on('console', (m) => { if (m.type() === 'error') logs.push(m.text()); });
  await page.goto(url);
  await page.waitForTimeout(8000);
  const status = await page.evaluate(() => document.getElementById('status')?.textContent ?? '');
  await page.screenshot({ path: `${outDir}/${name}.png` });
  console.log(`== ${name} ${viewport.width}x${viewport.height}`);
  console.log(`status text: ${JSON.stringify(status)}`);
  for (const e of logs) console.log(`console error: ${e}`);
  for (const e of crashes) console.log(`CRASH: ${e}`);
  // Console errors (a missing favicon, library deprecation notices) are
  // only printed; an uncaught exception fails the test.
  if (status.includes('無法啟動') || crashes.length) failed = true;
  await page.close();
}
await browser.close();
process.exit(failed ? 1 : 0);
