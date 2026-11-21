import dayjs from 'dayjs';
import {nutritionFields, newNutrientFields} from './Dish';

// Copy snapshots bypass normal meal/catalog normalization and portion recalculation.
export function copyFoodSnapshot(food) {
    return {...Object.fromEntries(['name', ...nutritionFields, 'quantity', 'unit', 'fruit', 'nutrientSource', 'nutrientsEstimated',
        ...newNutrientFields.flatMap(field => [field.sourceKey, field.estimatedKey])].map(key => [key, food[key]])), reference: {...food.reference}};
}

export function copyMealSnapshot(meal) {
    return {...Object.fromEntries(['mealType', 'mealSequence', 'calories', 'proteinGrams', 'carbohydrateGrams', 'fatGrams', 'durationMinutes', 'notes'].map(key => [key, meal[key]])),
        mealTime: meal.mealTime instanceof Date ? dayjs(meal.mealTime).format('HH:mm:ss') : meal.mealTime,
        dishes: meal.dishes.map(copyFoodSnapshot)};
}

export function foodTitle(foods) {
    if (!foods.length) return 'No foods';
    const first = foods.find(food => !/\b(?:salads?|ensaladas?|amanida|amanides)\b/i.test(food.name)) ?? foods[0];
    if (foods.length === 1) return first.name;
    return `${first.name} + ${foods.length - 1} ${foods.length === 2 ? 'food' : 'foods'}`;
}

export function preloadDate(date) {
    return dayjs(date).format('ddd, DD/MM/YYYY');
}

export function previousDays(meals, targetDate) {
    const days = new Map();
    meals.filter(meal => dayjs(meal.date).isBefore(targetDate, 'day')).forEach(meal => {
        const date = dayjs(meal.date).format('YYYY-MM-DD');
        if (!days.has(date)) days.set(date, []);
        days.get(date).push(meal);
    });
    return [...days.entries()].sort(([left], [right]) => right.localeCompare(left)).slice(0, 14).map(([date, entries]) => {
        const title = foodTitle(entries.flatMap(meal => meal.dishes));
        const calories = entries.reduce((sum, meal) => sum + meal.calories, 0);
        const dateFormat = preloadDate(date);
        return {date, dateFormat, title, calories, meals: entries, label: `${title} · ${dateFormat} · ${calories.toLocaleString('en-US')} kcal`};
    });
}
