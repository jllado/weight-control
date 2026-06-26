import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Calorie from '../model/Calorie';

function toPayload(calorie) {
    return {
        date: dayjs(calorie.date).format('YYYY-MM-DD'),
        calories: calorie.calories
    };
}

function toCalorie(data) {
    return new Calorie({
        id: data.id,
        date: data.date,
        dateFormat: data.dateFormat,
        calories: data.calories
    });
}

export default {
    async get_all() {
        return (await get('/calories')).map(toCalorie);
    },
    async save(calorie) {
        const data = calorie.id
            ? await put(`/calories/${calorie.id}`, toPayload(calorie))
            : await post('/calories', toPayload(calorie));
        return toCalorie(data);
    },
    delete(calorie) {
        return del(`/calories/${calorie.id}`);
    }
}
