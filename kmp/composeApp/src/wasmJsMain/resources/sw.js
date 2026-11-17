// Offline cache for 同遊 on the web. kmp/tools/offline.mjs fills in VERSION
// and FILES after the build; until then this file does nothing.
const VERSION = "__VERSION__";
const FILES = __FILES__;
const CACHE = `tripshare-k-${VERSION}`;

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      if (!Array.isArray(FILES)) return;
      const cache = await caches.open(CACHE);
      // cache: "reload" skips the HTTP cache, so every file is this build's.
      await cache.addAll(FILES.map((f) => new Request(f, { cache: "reload" })));
      await self.skipWaiting();
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const key of await caches.keys()) {
        if (key !== CACHE) await caches.delete(key);
      }
      await self.clients.claim();
    })(),
  );
});

// This build's files come from the cache, so the app opens with no
// connection. A new deploy changes sw.js, which installs the new files in
// the background; the next launch uses them. Everything else (Firestore,
// Google sign-in, exchange rates) goes to the network as usual.
self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/__/")) return; // Firebase Auth handlers.
  const key = req.mode === "navigate" ? new URL("./", self.location).href : req.url;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const hit = await cache.match(key, { ignoreSearch: true });
      return hit || fetch(req);
    })(),
  );
});
