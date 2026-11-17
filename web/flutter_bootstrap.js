{{flutter_js}}
{{flutter_build_config}}

// Load the rendering engine from this site instead of Google's CDN, so the
// offline cache in sw.js can serve it with no connection.
_flutter.loader.load({
  config: { canvasKitBaseUrl: "canvaskit/" },
});

// Flutter's own service worker is deprecated and unregisters itself; ours
// keeps the app openable offline (abroad, on a plane).
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch((e) => {
      console.warn("Offline cache unavailable:", e);
    });
  });
}
