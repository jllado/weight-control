import {reactive, inject} from 'vue';

export const stateSymbol = Symbol('state');

export const createState = () => reactive({
    loading: false,
    token: undefined,
    user: {
        mail: undefined
    },
    authenticated: false
});

export const userState = () => inject(stateSymbol);
