import {get} from './api';
import Calorie from '../model/Calorie';

function toCalorie(data) {
    return new Calorie({
        date: data.date,
        dateFormat: data.dateFormat,
        calories: data.calories
    });
}

export default {
    async get_all() {
        return (await get('/calories')).map(toCalorie);
    }
}
