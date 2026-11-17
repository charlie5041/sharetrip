// Offline cache for 同遊 on the web. tool/web_offline.mjs fills in VERSION
// and FILES after `flutter build web`; until then this file does nothing.
const VERSION = "__VERSION__";
const FILES = __FILES__;
// The Firebase SDK that firebase_core loads from Google's CDN at startup.
// Without it Firebase never starts, so it is kept offline too.
const SDK = __SDK__;
const CACHE = `tripshare-${VERSION}`;
// CDN files carry their version in the URL, so one cache serves every build.
const CDN = "tripshare-cdn";
const cdnHosts = ["www.gstatic.com", "fonts.gstatic.com"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      // cache: "reload" skips the HTTP cache, so every file is this build's.
      await cache.addAll(FILES.map((f) => new Request(f, { cache: "reload" })));
      // Best effort: a CDN hiccup must not stop the app's own files from
      // installing. Whatever is missed is cached on its first use.
      const cdn = await caches.open(CDN);
      await Promise.all(
        SDK.map(async (url) => {
          if (await cdn.match(url)) return;
          try {
            const res = await fetch(url, { mode: "cors" });
            if (res.ok) await cdn.put(url, res);
          } catch (_) {}
        }),
      );
      await self.skipWaiting();
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const key of await caches.keys()) {
        if (key !== CACHE && key !== CDN) await caches.delete(key);
      }
      await self.clients.claim();
    })(),
  );
});

// This build's files come from the cache, so the app opens with no
// connection. A new deploy changes sw.js, which installs the new files in
// the background; the next launch uses them. The Firebase SDK and fonts
// from Google's CDN are kept the same way. Everything else (Firestore,
// Google sign-in, exchange rates) goes to the network as usual.
self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (cdnHosts.includes(url.host)) {
    event.respondWith(fromCdnCache(req));
    return;
  }
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/__/")) return; // Firebase Auth handlers.
  const key = req.mode === "navigate" ? new URL("./", self.location).href : req.url;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const hit = await cache.match(key, { ignoreSearch: true });
      if (hit) return hit;
      return fetch(req);
    })(),
  );
});

async function fromCdnCache(req) {
  const cdn = await caches.open(CDN);
  const hit = await cdn.match(req.url);
  if (hit) return hit;
  const res = await fetch(req);
  if (res.ok) await cdn.put(req.url, res.clone());
  return res;
}
