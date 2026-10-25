/* global BigInt */
export const dishUnits = [
    {label: 'g', value: 'GRAM'}, {label: 'ml', value: 'MILLILITRE'},
    {label: 'serving', value: 'SERVING'}, {label: 'unit', value: 'UNIT'}
];
export const legacyNutrientFields = [
    {key: 'vitaminDMicrograms', label: 'Vitamin D', unit: 'µg', coverageKey: 'vitaminDCoverage'},
    {key: 'omega3Milligrams', label: 'Total omega-3 (legacy)', unit: 'mg', coverageKey: 'omega3Coverage'},
    {key: 'magnesiumMilligrams', label: 'Magnesium', unit: 'mg', coverageKey: 'magnesiumCoverage'}
];
export const newNutrientFields = [
    {key: 'epaDhaMilligrams', label: 'Omega-3 (EPA+DHA)', unit: 'mg', sourceKey: 'epaDhaSource', estimatedKey: 'epaDhaEstimated', coverageKey: 'epaDhaCoverage'},
    {key: 'alaMilligrams', label: 'ALA', unit: 'mg', sourceKey: 'alaSource', estimatedKey: 'alaEstimated', coverageKey: 'alaCoverage'},
    {key: 'fiberGrams', label: 'Fiber', unit: 'g', sourceKey: 'fiberSource', estimatedKey: 'fiberEstimated', coverageKey: 'fiberCoverage'}
];
export const nutrientFields = [legacyNutrientFields[0], legacyNutrientFields[2], ...newNutrientFields];
export const totalOmega3Field = {...legacyNutrientFields[1], label: 'Omega-3'};
export const dailyNutrientFields = [legacyNutrientFields[0], legacyNutrientFields[2], totalOmega3Field, newNutrientFields[2]];
export const requiredNutrientFields = [legacyNutrientFields[0], legacyNutrientFields[2]];
export const trendNutrientFields = [legacyNutrientFields[0], totalOmega3Field, legacyNutrientFields[2], newNutrientFields[2]];
export const storedNutrientFields = [...legacyNutrientFields, ...newNutrientFields];
export const nutritionFields = ['calories', 'proteinGrams', 'carbohydrateGrams', 'fatGrams', ...storedNutrientFields.map(field => field.key)];

export function formatNutritionValue(value) {
    return Number(value.toFixed(2)).toString();
}

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
    const result = {...Object.fromEntries(storedNutrientFields.map(field => [field.key, null])), ...dish, nutrientSource: dish.nutrientSource ?? '', nutrientsEstimated: dish.nutrientsEstimated ?? false, quantity: dish.quantity ?? 1, unit: dish.unit ?? 'SERVING', fruit: dish.fruit ?? false};
    newNutrientFields.forEach(field => {
        result[field.sourceKey] = dish[field.sourceKey] ?? '';
        result[field.estimatedKey] = dish[field.estimatedKey] ?? false;
    });
    result.reference = dish.reference ? {...Object.fromEntries(storedNutrientFields.map(field => [field.key, null])), ...dish.reference} : dishReference(result);
    return result;
}

export function quantityLabel(dish) {
    return `${dish.quantity} ${dishUnits.find(unit => unit.value === dish.unit).label}`;
}

export function macroSummary(dish) {
    return [['proteinGrams', 'P'], ['carbohydrateGrams', 'C'], ['fatGrams', 'F']]
        .map(([key, label]) => `${label} ${dish[key] === null ? '—' : `${formatNutritionValue(dish[key])} g`}`).join(' · ');
}

export function foodPayload(food) {
    const payload = {nutrientSchemaVersion: 2, ...Object.fromEntries(['name', ...nutritionFields, 'quantity', 'unit', 'reference', 'fruit', 'nutrientSource', 'nutrientsEstimated',
        ...newNutrientFields.flatMap(field => [field.sourceKey, field.estimatedKey])].map(key => [key, food[key]]))};
    newNutrientFields.forEach(field => {
        if (payload[field.key] == null) {
            payload[field.sourceKey] = null;
            payload[field.estimatedKey] = null;
        }
    });
    return payload;
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

export function nutrientSummary(meals) {
    const foods = meals.flatMap(meal => meal.dishes);
    const recorded = foods.filter(food => legacyNutrientFields.every(field => food[field.key] != null));
    const coverage = field => {
        const known = foods.filter(food => food[field.key] != null);
        return {foodsWithValues: known.length, estimatedFoods: known.filter(food => food[field.estimatedKey || 'nutrientsEstimated']).length};
    };
    return {
        ...Object.fromEntries(storedNutrientFields.map(field => [field.key, foods.some(food => food[field.key] != null)
            ? Math.round(foods.reduce((sum, food) => sum + (food[field.key] ?? 0), 0) * 100) / 100 : null])),
        vitaminDCoverage: coverage(legacyNutrientFields[0]),
        omega3Coverage: coverage(legacyNutrientFields[1]),
        magnesiumCoverage: coverage(legacyNutrientFields[2]),
        ...Object.fromEntries(newNutrientFields.map(field => [field.coverageKey, coverage(field)])),
        foodsWithValues: recorded.length, totalFoods: foods.length,
        estimatedFoods: recorded.filter(food => food.nutrientsEstimated).length,
        mealsWithoutFoods: meals.filter(meal => !meal.dishes.length).length
    };
}
