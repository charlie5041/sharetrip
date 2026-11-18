// Fails the build if the web bundle still reaches Skia through JavaScript
// tail calls, which Safari 27.2 breaks (see composeApp/webpack.config.d/
// skiko-no-tail-call.js).
//
// USAGE  node tools/check-tail-calls.mjs <dist dir>
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.argv[2];
if (!root) throw new Error("usage: node tools/check-tail-calls.mjs <dist dir>");
const js = readFileSync(join(root, "tripshare.js"), "utf8");
const tail = js.match(/\(\.\.\.(\w+)\)=>\(\w+=[^()]*\)\(\.\.\.\1\)/g) ?? [];
const wrapped = js.match(/\(\.\.\.(\w+)\)=>\{try\{return\(\w+=[^()]*\)\(\.\.\.\1\)\}/g) ?? [];
console.log(`skiko wrappers: ${wrapped.length} without tail calls, ${tail.length} with`);
if (tail.length > 0 || wrapped.length < 500) {
  console.error("Skia is still reached through tail calls; the page would be white in Safari 27.2.");
  process.exit(1);
}
