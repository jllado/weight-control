import { register } from 'register-service-worker';
import { appState } from './state';

if (import.meta.env.PROD) {
    const state = appState();
    let refreshing = false;

    navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (refreshing) {
            return;
        }
        refreshing = true;
        state.updateRefreshing = true;
        window.location.reload();
    });

    register(`${import.meta.env.BASE_URL}service-worker.js`, {
        updated(registration) {
            state.updateRegistration = registration;
            state.updateAvailable = true;
        }
    });
}
