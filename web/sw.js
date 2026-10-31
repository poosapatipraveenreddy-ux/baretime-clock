/* BareTime service worker.
 * To ship an update: change VERSION below and redeploy the files.
 * Installed copies notice the change, download the new files in the
 * background and show an "Update now" message. No server code involved. */
const VERSION = '1.1.0';
const CORE = 'baretime-core-' + VERSION;
const RUNTIME = 'baretime-runtime-v1'; // Google Fonts, kept across updates
const ASSETS = [
  './', 'index.html', 'styles.css', 'app.js', 'manifest.webmanifest',
  'icons/favicon.svg', 'icons/icon-192.png', 'icons/icon-512.png',
  'icons/icon-maskable-512.png', 'icons/apple-touch-icon.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CORE).then(c =>
    Promise.all(ASSETS.map(u => c.add(new Request(u, { cache: 'reload' }))))));
  // No skipWaiting here: the page asks for it when the user taps "Update now".
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k.startsWith('baretime-core-') && k !== CORE).map(k => caches.delete(k))))
      .then(() => self.clients.claim()));
});

self.addEventListener('message', e => {
  const d = e.data || {};
  if (d.type === 'SKIP_WAITING') self.skipWaiting();
  if (d.type === 'GET_VERSION' && e.ports && e.ports[0]) e.ports[0].postMessage({ version: VERSION });
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.origin === location.origin) {
    // App files are versioned together, so serve them from the cache as one set.
    e.respondWith(caches.open(CORE).then(async c => {
      const hit = (await c.match(req, { ignoreSearch: true })) ||
        (req.mode === 'navigate' ? await c.match('index.html') : undefined);
      return hit || fetch(req);
    }));
  } else if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    // Fonts: serve the cached copy instantly, refresh it quietly.
    e.respondWith(caches.open(RUNTIME).then(async c => {
      const hit = await c.match(req);
      const net = fetch(req).then(r => { if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone()); return r; }).catch(() => hit);
      return hit || net;
    }));
  }
});
