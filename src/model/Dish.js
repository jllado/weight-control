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
