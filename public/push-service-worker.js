/* eslint-env serviceworker */

const routineActions = [
    {action: 'snooze', title: 'Snooze 15 min'},
    {action: 'dismiss', title: 'Dismiss'}
];

self.addEventListener('push', event => {
    const payload = event.data.json();
    event.waitUntil(self.registration.showNotification(payload.title, {
        body: payload.body,
        icon: '/android-chrome-192x192.png',
        tag: payload.tag,
        actions: payload.snoozeUrl ? routineActions : [],
        data: {url: payload.url, snoozeUrl: payload.snoozeUrl}
    }));
});

function openNotificationTarget(targetUrl) {
    return self.clients.matchAll({type: 'window', includeUncontrolled: true}).then(windowClients => {
        const existingClient = windowClients.find(client => new URL(client.url).origin === self.location.origin);
        if (existingClient) {
            return existingClient.navigate(targetUrl).then(client => client.focus());
        }
        return self.clients.openWindow(targetUrl);
    });
}

self.addEventListener('notificationclick', event => {
    event.notification.close();
    if (event.action === 'dismiss') {
        return;
    }

    const targetUrl = new URL(event.notification.data.url, self.location.origin).href;
    if (event.action === 'snooze') {
        const snoozeRequest = fetch(event.notification.data.snoozeUrl, {
            method: 'POST',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({minutes: 15})
        });
        event.waitUntil(snoozeRequest.then(
            response => response.ok ? undefined : openNotificationTarget(targetUrl),
            () => openNotificationTarget(targetUrl)
        ));
        return;
    }

    event.waitUntil(openNotificationTarget(targetUrl));
});
