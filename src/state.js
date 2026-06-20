import {reactive, inject} from 'vue';

export const stateSymbol = Symbol('state');

export const createState = () => reactive({
    loading: false,
    token: undefined,
    user: {
        mail: undefined
    },
    authenticated: false,
    deferredInstallPrompt: undefined,
    installAvailable: false,
    installed: false
});

export const userState = () => inject(stateSymbol);
