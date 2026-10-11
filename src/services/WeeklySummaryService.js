import {get, post, put} from './api';

function getConfig() {
    return get('/weekly-summary/config');
}

function saveConfig({deliveryDay, deliveryTime}) {
    return put('/weekly-summary/config', {deliveryDay, deliveryTime});
}

function send() {
    return post('/weekly-summary/send');
}

function getArchive({page = 0, size = 10, selectedFridayDate} = {}) {
    const parameters = new URLSearchParams({page, size});
    if (selectedFridayDate) parameters.set('selectedFridayDate', selectedFridayDate);
    return get(`/weekly-summary?${parameters}`);
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

export default {getConfig, saveConfig, send, getArchive, getPreview, createLatest, getSummary};
