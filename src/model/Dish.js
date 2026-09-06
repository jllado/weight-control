/* global BigInt */
export const dishUnits = [
    {label: 'g', value: 'GRAM'}, {label: 'ml', value: 'MILLILITRE'},
    {label: 'serving', value: 'SERVING'}, {label: 'unit', value: 'UNIT'}
];
export const nutritionFields = ['calories', 'proteinGrams', 'carbohydrateGrams', 'fatGrams'];

export function dishReference(dish) {
    return Object.fromEntries(['quantity', ...nutritionFields].map(key => [key, dish[key]]));
}

// Decimal integer arithmetic matches the backend's HALF_UP rounding.
export function scaleNutrition(value, quantity, referenceQuantity, decimals) {
    if (value === null) return null;
    const numerator = BigInt(Math.round(value * 100)) * BigInt(Math.round(quantity * 1000)) * (10n ** BigInt(decimals));
    const denominator = 100n * BigInt(Math.round(referenceQuantity * 1000));
    return Number((numerator * 2n + denominator) / (denominator * 2n)) / (10 ** decimals);
}

export function normalizeDish(dish) {
    const result = {...dish, quantity: dish.quantity ?? 1, unit: dish.unit ?? 'SERVING'};
    result.reference = dish.reference ? {...dish.reference} : dishReference(result);
    return result;
}

export function quantityLabel(dish) {
    return `${dish.quantity} ${dishUnits.find(unit => unit.value === dish.unit).label}`;
}

export function macroSummary(dish) {
    return [['proteinGrams', 'P'], ['carbohydrateGrams', 'C'], ['fatGrams', 'F']]
        .map(([key, label]) => `${label} ${dish[key] === null ? '—' : `${dish[key]} g`}`).join(' · ');
}

export function foodPayload(food) {
    return Object.fromEntries(['name', ...nutritionFields, 'quantity', 'unit', 'reference'].map(key => [key, food[key]]));
}

export function previousFoods(meals) {
    const foods = new Map();
    [...meals].sort((a, b) => b.date - a.date || b.id - a.id).forEach(meal => [...meal.dishes].reverse().forEach(food => {
        const name = food.name.trim().toLowerCase();
        if (!foods.has(name)) foods.set(name, {...food, label: `${food.name.trim()} · ${quantityLabel(food)} · ${food.calories} kcal`});
    }));
    return [...foods.values()];
}

export function scaleRecipe(recipe, servings) {
    if (!(servings > 0) || servings > 99999999.999) throw new Error('Enter a positive serving count within the supported range.');
    const foods = recipe.ingredients.map(ingredient => {
        const numerator = BigInt(Math.round(ingredient.quantity * 1000)) * BigInt(Math.round(servings * 1000));
        const denominator = BigInt(Math.round(recipe.servings * 1000));
        const quantity = Number((numerator * 2n + denominator) / (denominator * 2n)) / 1000;
        if (quantity < 0.001 || quantity > 99999999.999) throw new Error('This serving count puts an ingredient outside the supported quantity range.');
        const food = {...normalizeDish(ingredient), quantity, key: crypto.randomUUID()};
        nutritionFields.forEach(key => {
            food[key] = scaleNutrition(food.reference[key], quantity, food.reference.quantity, key === 'calories' ? 0 : 2);
            if (food[key] > (key === 'calories' ? 2147483647 : 99999999.99)) throw new Error('This serving count exceeds the supported nutrition range.');
        });
        return food;
    });
    const totals = foodTotals(foods);
    if (nutritionFields.some(key => totals[key] > (key === 'calories' ? 2147483647 : 99999999.99))) throw new Error('This serving count exceeds the supported nutrition range.');
    return foods;
}

export function foodTotals(foods) {
    return Object.fromEntries(nutritionFields.map(key => [key, foods.some(food => food[key] === null) ? null : Math.round(foods.reduce((sum, food) => sum + food[key], 0) * 100) / 100]));
}
