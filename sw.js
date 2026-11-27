/* Lumora Film web — offline support. Keeps a copy of the app so it opens without internet.
 * Photos are never sent anywhere; they stay in the browser's own storage. */
'use strict';
const CACHE = 'filmdesk-v14';
const FILES = [
  './', 'index.html', 'manifest.webmanifest', 'icon-lumora.svg', 'icon-lumora-192.png', 'icon-lumora-512.png', 'material-icons.woff',
  'model.js?v=10', 'engine.js?v=10', 'files.js?v=10', 'raw.js?v=10', 'libraw.wasm?v=1', 'worker.js?v=10',
  'app-core.js?v=10', 'app-vm.js?v=10', 'app-ui.js?v=10', 'app-curvecrop.js?v=10', 'app-pages.js?v=10',
];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// Online: use the newest files and refresh the saved copy. Offline: use the saved copy.
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    // 'no-cache': always ask the server for the newest version (no stale browser copy).
    fetch(req, { cache: 'no-cache' }).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req, { ignoreSearch: req.mode === 'navigate' }).then(r => r || caches.match('index.html')))
  );
});
