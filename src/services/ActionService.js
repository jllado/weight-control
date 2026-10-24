import {get, post, put, del} from './api';

export function actionToday() {
    return new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'}).format(new Date());
}

export default {
    list: (date, view = 'ACTIVE') => get('/actions?' + new URLSearchParams({date, view})),
    find: (id, date) => get('/actions/' + id + '?' + new URLSearchParams({date})),
    suggestions: () => get('/actions/suggestions'),
    resolve: source => get('/actions/suggestions/' + encodeURIComponent(source)),
    create: request => post('/actions', request),
    update: (id, request) => put('/actions/' + id, request),
    stop: id => post('/actions/' + id + '/stop'),
    restart: (id, request) => post('/actions/' + id + '/restart', request),
    check: (id, date, checked) => checked ? put('/actions/' + id + '/checkins/' + date) : del('/actions/' + id + '/checkins/' + date)
};
