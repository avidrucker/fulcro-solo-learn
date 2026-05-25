// AutoFocus service worker — Phase 7.19 / S-pwa-offline.
//
// Scope-relative caching strategy adapted from the og JS port
// (avidrucker/pwa-autofocus-app/public/serviceWorker.js). Simpler
// than CRA's Workbox-generated SW: pre-cache the app shell on
// install, network-first for navigations, cache-first for static
// assets, fall back to offline.html when the network is down and
// the request can't be served from cache.
//
// Bump APP_VERSION (semver, mirrored in package.json) when shipping a
// release so clients invalidate the old cache. Without the bump, the
// SW serves the previous JS bundle from cache indefinitely. sw.js is
// the runtime authority; package.json's version mirrors it for human
// reference only.

const APP_VERSION = '0.0.22';
const CACHE_NAME = `autofocus-cache-v${APP_VERSION}`;

// Scope is set by where the SW is registered + its file path. The
// SW URL resolves relatively, so paths below are all scope-relative.
const CORE_URLS = [
  './',
  './index.html',
  './offline.html',
  './manifest.webmanifest',
  './css/app.css',
  './js/main/main.js',
  './icon.svg',
  // External CDN dependencies — same as the og.
  'https://unpkg.com/tachyons@4.12.0/css/tachyons.min.css',
  'https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;800&display=swap'
];

self.addEventListener('install', (event) => {
  console.log('[SW] install', APP_VERSION);
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE_NAME);
      // addAll fails atomically if any URL 404s; cache them one-by-one
      // with individual try/catch so a missing optional asset doesn't
      // block the install.
      await Promise.all(CORE_URLS.map(async (url) => {
        try {
          await cache.add(url);
        } catch (err) {
          console.warn('[SW] skipped (failed to cache):', url, err.message);
        }
      }));
      self.skipWaiting();
    })()
  );
});

self.addEventListener('activate', (event) => {
  console.log('[SW] activate', APP_VERSION);
  event.waitUntil(
    (async () => {
      // Delete caches from previous versions.
      const names = await caches.keys();
      await Promise.all(
        names
          .filter((n) => n.startsWith('autofocus-cache-') && n !== CACHE_NAME)
          .map((n) => {
            console.log('[SW] delete old cache', n);
            return caches.delete(n);
          })
      );
      await self.clients.claim();
    })()
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;

  // SW only handles GETs.
  if (req.method !== 'GET') return;

  // Only intercept same-origin requests + known CDN deps.
  const url = new URL(req.url);
  const sameOrigin = url.origin === self.location.origin;
  const knownCDN = url.host.includes('unpkg.com') ||
                   url.host.includes('fonts.googleapis.com') ||
                   url.host.includes('fonts.gstatic.com');
  if (!sameOrigin && !knownCDN) return;

  // Dev bypass — let shadow-cljs serve hot-reload chunks directly on
  // localhost. Without this, the cache-first branch below pins the
  // browser to whatever JS shadow-cljs wrote the first time we
  // visited; subsequent recompiles ship new files to disk that the
  // browser never sees. Scope is narrow:
  //   - localhost only (prod still gets full PWA caching)
  //   - js/main/* only (so index.html, css, icons, manifest still
  //     route through the SW and the PWA install flow stays testable)
  // To test the cached prod-like behaviour locally, run
  // `npx shadow-cljs release app` and load the release build (or
  // toggle DevTools → Application → Service Workers → Bypass for
  // network OFF while this branch is active).
  if (sameOrigin &&
      self.location.hostname === 'localhost' &&
      url.pathname.startsWith('/js/main/')) {
    return;
  }

  // Strategy:
  //   - Navigations (HTML) → network-first, fall back to cached
  //     index.html, then offline.html.
  //   - Everything else (JS/CSS/fonts/icons) → cache-first, then
  //     network and add to cache.
  if (req.mode === 'navigate' || req.headers.get('accept')?.includes('text/html')) {
    event.respondWith(
      (async () => {
        try {
          const fresh = await fetch(req);
          const cache = await caches.open(CACHE_NAME);
          cache.put(req, fresh.clone());
          return fresh;
        } catch (_) {
          const cached = await caches.match(req) ||
                         await caches.match('./index.html') ||
                         await caches.match('./offline.html');
          return cached || new Response('Offline', { status: 503 });
        }
      })()
    );
    return;
  }

  // Cache-first for static assets.
  event.respondWith(
    (async () => {
      const cached = await caches.match(req);
      if (cached) return cached;
      try {
        const fresh = await fetch(req);
        if (fresh.status === 200) {
          const cache = await caches.open(CACHE_NAME);
          cache.put(req, fresh.clone());
        }
        return fresh;
      } catch (_) {
        return new Response('Offline', { status: 503 });
      }
    })()
  );
});
