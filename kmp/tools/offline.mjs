// Fills in sw.js after the web build: the files to keep offline and a
// version that changes whenever any of them does.
//
// USAGE  node tools/offline.mjs <dist dir>
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.argv[2];
if (!root) throw new Error("usage: node tools/offline.mjs <dist dir>");
// Debug maps, the worker itself, and index.html (cached as "./").
const skip = [/\.map$/, /^sw\.js$/, /^index\.html$/, /\.txt$/];

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
for (const f of [...files, "index.html"]) hash.update(f).update(readFileSync(join(root, f)));
const version = hash.digest("hex").slice(0, 16);

const sw = join(root, "sw.js");
writeFileSync(sw, readFileSync(sw, "utf8")
  .replace('"__VERSION__"', JSON.stringify(version))
  .replace("__FILES__", JSON.stringify(["./", ...files], null, 2)));
console.log(`sw.js: ${files.length + 1} files, version ${version}`);
