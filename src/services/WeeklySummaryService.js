import {get, post} from './api';

function getConfig() {
    return get('/weekly-summary/config');
}

function send() {
    return post('/weekly-summary/send');
}

export default {getConfig, send};
