import {del, get, post, put} from './api';

export default {
    get_all() { return get('/stretching-sets'); },
    save({id, name, entries}) { return id ? put(`/stretching-sets/${id}`, {name, entries}) : post('/stretching-sets', {name, entries}); },
    delete(id) { return del(`/stretching-sets/${id}`); }
};
