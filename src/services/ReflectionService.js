import {get} from './api';

export default {
    async getOverview(page = 0) {
        return get(`/reflections?page=${page}`);
    },
    async get(date) {
        return get(`/reflections/${date}`);
    }
};
