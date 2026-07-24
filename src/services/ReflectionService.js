import {get} from './api';

export default {
    async getOverview() {
        return get('/reflections');
    },
    async get(date) {
        return get(`/reflections/${date}`);
    }
};
