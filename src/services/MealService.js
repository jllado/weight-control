import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Meal from '../model/Meal';
import {foodPayload, nutrientSchemaVersion} from '../model/Dish';
import {copyMealSnapshot} from '../model/MealDayCopy';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(meal) {
    return {
        date: dayjs(meal.date).format('YYYY-MM-DD'),
        mealType: meal.mealType,
        calories: meal.calories,
        proteinGrams: meal.proteinGrams,
        carbohydrateGrams: meal.carbohydrateGrams,
        fatGrams: meal.fatGrams,
        estimatedSaturatedFatGrams: meal.estimatedSaturatedFatGrams,
        saturatedFatEstimateSource: meal.saturatedFatEstimateSource,
        mealTime: meal.mealTime ? dayjs(meal.mealTime).format('HH:mm:ss') : null,
        durationMinutes: meal.durationMinutes,
        notes: meal.notes,
        dishes: meal.dishes.map(foodPayload),
        nutrientSchemaVersion
    };
}

function toMeal(data) {
    return new Meal(data);
}

export default {
    get_rating_preferences() {
        return get('/meals/rating-preferences');
    },
    save_rating_preferences(preferences) {
        return put('/meals/rating-preferences', preferences);
    },
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
    async copy_day(date, meals) {
        const response = await post('/meals/copy-day', {date: dayjs(date).format('YYYY-MM-DD'), meals: meals.map(copyMealSnapshot)});
        celebratePersonalRecords(response.recordAchievements);
        return response.result.map(toMeal);
    },
    delete(meal) {
        return del(`/meals/${meal.id}`);
    }
}
