import {reactive, inject} from 'vue';

export const stateSymbol = Symbol('state');
export const createState = () => reactive({
    loading: true,
    user: {},
    authenticated: document.cookie.indexOf("login") !== -1
});
export const useState = () => inject(stateSymbol);