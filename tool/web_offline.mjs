// Fills in web/sw.js after `flutter build web`: the list of files to keep
// offline and a version that changes whenever any of them does.
//
// USAGE  node tool/web_offline.mjs   (from the repo root, after the build)
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const root = "build/web";
// Engine variants this dart2js + CanvasKit build never loads, debug symbols,
// and Flutter's deprecated worker.
const skip = [
  /\.symbols$/, /^canvaskit\/(skwasm|wimp)/, /^canvaskit\/webparagraph\//,
  /^flutter_service_worker\.js$/, /^sw\.js$/, /^\.last_build_id$/,
  /^index\.html$/, // cached as "./" instead
];

const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path);
    else {
      const rel = relative(root, path).split("\\").join("/");
      if (!skip.some((r) => r.test(rel))) files.push(rel);
    }
  }
})(root);
files.sort();

const hash = createHash("sha256");
for (const f of [...files, "index.html"]) {
  hash.update(f).update(readFileSync(join(root, f)));
}
const version = hash.digest("hex").slice(0, 16);

// firebase_core loads these from Google's CDN, at the version its web
// package pins.
const pkgs = JSON.parse(readFileSync(".dart_tool/package_config.json", "utf8"));
const coreWeb = pkgs.packages.find((p) => p.name === "firebase_core_web");
const versionFile = new URL(
  "lib/src/firebase_sdk_version.dart",
  new URL(coreWeb.rootUri.endsWith("/") ? coreWeb.rootUri : coreWeb.rootUri + "/",
    pathToFileURL(resolve(".dart_tool/package_config.json"))),
);
const sdkVersion = readFileSync(versionFile, "utf8").match(/'(\d+\.\d+\.\d+)'/)?.[1];
if (!sdkVersion) throw new Error(`No Firebase JS SDK version in ${versionFile}`);
const sdk = ["app", "auth", "firestore-pipelines"].map(
  (m) => `https://www.gstatic.com/firebasejs/${sdkVersion}/firebase-${m}.js`);

const sw = join(root, "sw.js");
const out = readFileSync(sw, "utf8")
  .replace('"__VERSION__"', JSON.stringify(version))
  .replace("__FILES__", JSON.stringify(["./", ...files], null, 2))
  .replace("__SDK__", JSON.stringify(sdk, null, 2));
writeFileSync(sw, out);
console.log(`sw.js: ${files.length + 1} files, version ${version}, Firebase JS ${sdkVersion}`);
