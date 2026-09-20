import {del, get, post, put} from './api';
export default {
  list() { return get('/coach-notes'); },
  create(note) { return post('/coach-notes', note); },
  update(id, note) { return put(`/coach-notes/${id}`, note); },
  delete(id) { return del(`/coach-notes/${id}`); }
};
