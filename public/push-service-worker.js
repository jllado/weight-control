/* eslint-env serviceworker */

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
