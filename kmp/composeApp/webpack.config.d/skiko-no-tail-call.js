// Safari 27.2 passes NaN instead of 0 or 1 to an optimised WebAssembly
// function that JavaScript reaches through a tail call with a float argument
// (WebKit bug 329421, fixed upstream but not shipped yet; JetBrains CMP-10986).
// skiko.mjs reaches every Skia function that way:
//   X = (...a) => (X = loadedWasm._["X"])(...a)
// so Compose measures and draws with NaN and the page stays white.
// This loader moves each of those calls into a try block, which is never a
// tail position. Remove it once Safari ships the fix and Compose's skiko
// stops generating tail calls.
;(function () {
    const fs = require("fs");
    const path = require("path");
    const loader = path.join(__dirname, "skiko-no-tail-call-loader.cjs");
    fs.writeFileSync(loader, `
const wrapper = /\\(\\.\\.\\.a\\) => \\((\\w+) = loadedWasm\\._\\["(\\w+)"\\]\\)\\(\\.\\.\\.a\\)/g;
module.exports = function (source) {
  let count = 0;
  const out = source.replace(wrapper, (_, name, key) => {
    count++;
    return "(...a) => { try { return (" + name + " = loadedWasm._[\\"" + key + "\\"])(...a); } catch (e) { throw e; } }";
  });
  // A skiko update that changes the wrapper shape must fail the build
  // rather than silently ship the white page again.
  if (count === 0) throw new Error("skiko-no-tail-call: no skiko wrappers found in " + this.resourcePath);
  return out;
};
`);
    config.module = config.module || {};
    config.module.rules = config.module.rules || [];
    config.module.rules.push({ test: /[\\/]skiko\.mjs$/, use: [{ loader }] });
})();
