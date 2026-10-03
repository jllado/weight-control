import {get, post} from './api';

function getConfig() {
    return get('/weekly-summary/config');
}

function send() {
    return post('/weekly-summary/send');
}

function getArchive() {
    return get('/weekly-summary');
}

function getPreview() {
    return get('/weekly-summary/preview');
}

function createLatest() {
    return post('/weekly-summary/create');
}

function getSummary(fridayDate) {
    return get(`/weekly-summary/${fridayDate}`);
}

export default {getConfig, send, getArchive, getPreview, createLatest, getSummary};
