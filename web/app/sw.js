'use strict';
const ROOT = new URL('./', self.location.href);
const CACHE_PREFIX = 'code-canvas-pwa-' + ROOT.pathname + '-';
const CACHE = CACHE_PREFIX + '20261126-v2';
const SHELL = ['./index.html', './styles.css?v=apple2', './app.js?v=apple2', './studies.js?v=apple2', './manifest.webmanifest', './icon.svg', './icon-192.png', './icon-512.png'].map(path => new URL(path, ROOT).href);
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== ROOT.origin || !url.pathname.startsWith(ROOT.pathname)) return;
  const index = SHELL[0];
  const fromThisCache = key => caches.open(CACHE).then(cache => cache.match(key));
  // Closing the cache surface over a checked-in allowlist keeps private documents out of Cache Storage.
  if (request.mode === 'navigate' && (url.pathname === ROOT.pathname || url.pathname === new URL(index).pathname)) {
    event.respondWith(fetch(request).then(response => response.ok ? response : fromThisCache(index).then(saved => saved || response)).catch(() => fromThisCache(index)));
  } else if (SHELL.includes(url.href)) {
    event.respondWith(fromThisCache(request).then(saved => saved || fetch(request)));
  }
});
