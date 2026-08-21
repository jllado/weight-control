import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Meal from '../model/Meal';

function toPayload(meal) {
    return {
        date: dayjs(meal.date).format('YYYY-MM-DD'),
        mealType: meal.mealType,
        calories: meal.calories,
        proteinGrams: meal.proteinGrams,
        carbohydrateGrams: meal.carbohydrateGrams,
        fatGrams: meal.fatGrams,
        mealTime: meal.mealTime ? dayjs(meal.mealTime).format('HH:mm:ss') : null,
        notes: meal.notes
    };
}

function toMeal(data) {
    return new Meal(data);
}

export default {
    async get_all() {
        return (await get('/meals')).map(toMeal);
    },
    async save(meal) {
        const data = meal.id
            ? await put(`/meals/${meal.id}`, toPayload(meal))
            : await post('/meals', toPayload(meal));
        return toMeal(data);
    },
    delete(meal) {
        return del(`/meals/${meal.id}`);
    }
}
