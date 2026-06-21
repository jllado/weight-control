import {reactive, inject} from 'vue';

export const stateSymbol = Symbol('state');

let state;

export const createState = () => {
    state = state || reactive({
        loading: false,
        token: undefined,
        user: {
            mail: undefined
        },
        authenticated: false,
        deferredInstallPrompt: undefined,
        installAvailable: false,
        installed: false,
        updateAvailable: false,
        updateRegistration: undefined,
        updateRefreshing: false
    });
    return state;
};

export const appState = () => state || createState();

export const userState = () => inject(stateSymbol);
