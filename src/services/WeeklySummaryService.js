import {get, post, put} from './api';

function getConfig() {
    return get('/weekly-summary/config');
}

function saveConfig({deliveryDay, deliveryTime, reminderEnabled, reminderDay, reminderTime}) {
    return put('/weekly-summary/config', {deliveryDay, deliveryTime, reminderEnabled, reminderDay, reminderTime});
}

function send() {
    return post('/weekly-summary/send');
}

function getArchive({page = 0, size = 10, selectedFridayDate} = {}) {
    const parameters = new URLSearchParams({page, size});
    if (selectedFridayDate) parameters.set('selectedFridayDate', selectedFridayDate);
    return get(`/weekly-summary?${parameters}`);
}

function getPreview(selectedFridayDate) {
    const query = selectedFridayDate ? `?selectedFridayDate=${encodeURIComponent(selectedFridayDate)}` : '';
    return get(`/weekly-summary/preview${query}`);
}

function createLatest(selectedFridayDate) {
    const query = selectedFridayDate ? `?selectedFridayDate=${encodeURIComponent(selectedFridayDate)}` : '';
    return post(`/weekly-summary/create${query}`);
}

function getSummary(fridayDate) {
    return get(`/weekly-summary/${fridayDate}`);
}

export default {getConfig, saveConfig, send, getArchive, getPreview, createLatest, getSummary};
