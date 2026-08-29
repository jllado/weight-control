import {del, get, post, put} from './api';

const promptDismissedKey = 'notification-prompt-dismissed';

function isSupported() {
    return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

function applicationServerKey(value) {
    const padding = '='.repeat((4 - value.length % 4) % 4);
    const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
    return Uint8Array.from(window.atob(base64), character => character.charCodeAt(0));
}

async function getStatus() {
    const config = await get('/push/config');
    if (!config.enabled) {
        return {config, supported: false, permission: 'default', subscription: null, enabled: false};
    }
    if (!isSupported()) {
        return {config, supported: false, permission: 'default', subscription: null, enabled: false};
    }
    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.getSubscription();
    return {config, supported: true, permission: Notification.permission, subscription, enabled: subscription !== null};
}

async function enable() {
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    const status = await getStatus();
    if (permission !== 'granted') {
        return {...status, permission, enabled: false};
    }
    const registration = await navigator.serviceWorker.ready;
    const subscription = status.subscription || await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: applicationServerKey(status.config.publicKey)
    });
    const subscriptionJson = subscription.toJSON();
    await put('/push/subscriptions', {endpoint: subscriptionJson.endpoint, keys: subscriptionJson.keys});
    return {...status, permission, subscription, enabled: true};
}

async function disable() {
    const status = await getStatus();
    await del('/push/subscriptions', {endpoint: status.subscription.endpoint});
    await status.subscription.unsubscribe();
    dismissPrompt();
    return {...status, subscription: null, enabled: false};
}

async function sendTest() {
    const status = await getStatus();
    await post('/push/test', {endpoint: status.subscription.endpoint});
}

function getReminderSettings() {
    return get('/push/reminder-settings');
}

function getAgenda() {
    return get('/push/agenda');
}

function saveReminderSettings(settings) {
    return put('/push/reminder-settings', settings);
}

function dismissPrompt() {
    window.localStorage.setItem(promptDismissedKey, 'true');
}

function isPromptDismissed() {
    return window.localStorage.getItem(promptDismissedKey) === 'true';
}

export default {getStatus, enable, disable, sendTest, getReminderSettings, getAgenda, saveReminderSettings, dismissPrompt, isPromptDismissed};
