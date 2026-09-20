import mitt from 'mitt';
import {get, post} from './api';

const events = mitt();

export function notificationsChanged() {
    events.emit('changed');
}

export function onNotificationsChanged(handler) {
    events.on('changed', handler);
    return () => events.off('changed', handler);
}

export function reconcileMobileNotifications(notifications) {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.ready.then(registration => registration.active.postMessage({
            type: 'reconcile-in-app-notifications',
            pendingNotificationIds: notifications.map(notification => notification.id)
        }));
    }
}

async function dismiss(id) {
    await post(`/notifications/${id}/dismiss`, {});
    notificationsChanged();
}

async function dismissAll() {
    await post('/notifications/dismiss-all', {});
    notificationsChanged();
}

export default {
    getPending() {
        return get('/notifications/pending');
    },
    dismiss,
    dismissAll
};
