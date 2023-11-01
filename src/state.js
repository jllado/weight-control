import {reactive, inject} from 'vue';
import dayjs from "dayjs";

const login_cookie = 'wc-login';

const loginCookie = getCookie(login_cookie);

export const saveCookie = (credential, email) => document.cookie = login_cookie + "=" +  credential + ":" + email + "; expires = " + dayjs().add(7, 'day').toDate().toUTCString();

export const expireCookie = () => document.cookie = login_cookie + "wc-login= ; expires = Thu, 01 Jan 1970 00:00:00 GMT"

export const stateSymbol = Symbol('state');

function getUserMail() {
    return loginCookie.split(":")[1];
}

function getUserToken() {
    return loginCookie.split(":")[0];
}

export const createState = () => reactive({
    loading: true,
    token: loginCookie ? getUserToken() : undefined,
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