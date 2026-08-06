/* eslint-env serviceworker */

import {cleanupOutdatedCaches, precacheAndRoute} from 'workbox-precaching';

cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);

self.addEventListener('message', event => {
    if (event.data?.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
});

self.addEventListener('activate', event => {
    event.waitUntil(self.clients.claim());
});

self.addEventListener('push', event => {
    const payload = event.data.json();
    event.waitUntil(self.registration.showNotification(payload.title, {
        body: payload.body,
        icon: '/android-chrome-192x192.png',
        tag: payload.tag,
        data: {url: payload.url}
    }));
});

self.addEventListener('notificationclick', event => {
    event.notification.close();
    const targetUrl = new URL(event.notification.data.url, self.location.origin).href;
    event.waitUntil(self.clients.matchAll({type: 'window', includeUncontrolled: true}).then(windowClients => {
        const existingClient = windowClients.find(client => new URL(client.url).origin === self.location.origin);
        if (existingClient) {
            return existingClient.navigate(targetUrl).then(client => client.focus());
        }
        return self.clients.openWindow(targetUrl);
    }));
});
