// 由 assets/icon/ 的 SVG 產生各平台圖示。執行：node tool/icon/generate_icons.js（需要 playwright）
const {chromium} = require('playwright');
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '../..');
const svg = f => fs.readFileSync(path.join(root, 'assets/icon', f), 'utf8');

// 圓角：用 SVG 外包一層 clipPath
const rounded = (s, r) => s.replace('<svg ', `<svg `).replace(/(<svg[^>]*>)/, `$1<defs><clipPath id="rr"><rect width="1024" height="1024" rx="${r}"/></clipPath></defs><g clip-path="url(#rr)">`).replace('</svg>', '</g></svg>');

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  async function out(svgText, size, file, transparent) {
    const dest = path.join(root, file);
    fs.mkdirSync(path.dirname(dest), {recursive: true});
    await p.setViewportSize({width: size, height: size});
    await p.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:${size}px;height:${size}px}</style>${svgText}`);
    await p.locator('svg').first().screenshot({path: dest, omitBackground: !!transparent});
  }
  const full = svg('app_icon.svg');            // 滿版方形（iOS、apple-touch-icon）
  const round = rounded(full, 230);           // 圓角（舊版 Android、網頁一般圖示）
  const fg = svg('adaptive_foreground.svg'), bg = svg('adaptive_background.svg'), mono = svg('adaptive_monochrome.svg');
  const maskable = svg('maskable.svg');

  // Android 舊版圖示 + adaptive 圖層
  const dens = {mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4};
  for (const [d, k] of Object.entries(dens)) {
    await out(round, 48 * k, `android/app/src/main/res/mipmap-${d}/ic_launcher.png`, true);
    await out(fg, 108 * k, `android/app/src/main/res/mipmap-${d}/ic_launcher_foreground.png`, true);
    await out(bg, 108 * k, `android/app/src/main/res/mipmap-${d}/ic_launcher_background.png`, false);
    await out(mono, 108 * k, `android/app/src/main/res/mipmap-${d}/ic_launcher_monochrome.png`, true);
  }
  // iOS：依檔名的尺寸輸出，不能有透明
  const iosDir = 'ios/Runner/Assets.xcassets/AppIcon.appiconset';
  for (const f of fs.readdirSync(path.join(root, iosDir)).filter(f => f.endsWith('.png'))) {
    const m = f.match(/Icon-App-([\d.]+)x[\d.]+@(\d)x\.png/);
    await out(full, Math.round(parseFloat(m[1]) * +m[2]), `${iosDir}/${f}`, false);
  }
  // 網頁 PWA
  await out(round, 192, 'web/icons/Icon-192.png', true);
  await out(round, 512, 'web/icons/Icon-512.png', true);
  await out(maskable, 192, 'web/icons/Icon-maskable-192.png', false);
  await out(maskable, 512, 'web/icons/Icon-maskable-512.png', false);
  await out(full, 180, 'web/icons/apple-touch-icon.png', false);
  await out(round, 32, 'web/favicon.png', true);
  await b.close();
})();
