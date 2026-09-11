import {del, get, post, put} from './api';

export default {
    get_all() { return get('/stretching-sets'); },
    save(set) { return set.id ? put(`/stretching-sets/${set.id}`, set) : post('/stretching-sets', set); },
    delete(id) { return del(`/stretching-sets/${id}`); }
};
