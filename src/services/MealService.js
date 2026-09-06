import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Meal from '../model/Meal';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(meal) {
    return {
        date: dayjs(meal.date).format('YYYY-MM-DD'),
        mealType: meal.mealType,
        calories: meal.calories,
        proteinGrams: meal.proteinGrams,
        carbohydrateGrams: meal.carbohydrateGrams,
        fatGrams: meal.fatGrams,
        mealTime: meal.mealTime ? dayjs(meal.mealTime).format('HH:mm:ss') : null,
        durationMinutes: meal.durationMinutes,
        notes: meal.notes,
        dishes: meal.dishes
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
        const response = meal.id
            ? await put(`/meals/${meal.id}`, toPayload(meal))
            : await post('/meals', toPayload(meal));
        celebratePersonalRecords(response.recordAchievements);
        return toMeal(response.result);
    },
    delete(meal) {
        return del(`/meals/${meal.id}`);
    }
}
