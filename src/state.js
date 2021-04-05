import {reactive, inject} from 'vue';

const loginCookie = getCookie("wc-login");

export const stateSymbol = Symbol('state');

function getUserMail() {
    return loginCookie.split(":")[1];
}

export const createState = () => reactive({
    loading: true,
    user: {
        mail: loginCookie ? getUserMail() : undefined
    },
    authenticated: loginCookie != undefined
});

export const userState = () => inject(stateSymbol);

function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) {
        return parts.pop().split(';').shift();
    }
    return undefined;
}