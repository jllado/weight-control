import {get} from './api';
import CoachWarning from '../model/CoachWarning';

function page(data) { return {...data, items: data.items.map(item => new CoachWarning(item))}; }

export default {
    async overview() {
        const data = await get('/coach-warnings');
        return {...data, active: data.active.map(item => new CoachWarning(item))};
    },
    async history(index = 0) { return page(await get(`/coach-warnings/history?page=${index}`)); },
    async revisions(id, index = 0) { return page(await get(`/coach-warnings/${id}/revisions?page=${index}`)); }
};
