import { register } from 'register-service-worker';
import { appState } from './state';

if (process.env.NODE_ENV === 'production') {
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

    register(`${process.env.BASE_URL}service-worker.js`, {
        updated(registration) {
            state.updateRegistration = registration;
            state.updateAvailable = true;
        }
    });
}
