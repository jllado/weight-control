import dayjs from 'dayjs';

export const MealType = {
    BREAKFAST: 'BREAKFAST',
    LUNCH: 'LUNCH',
    DINNER: 'DINNER',
    SNACK: 'SNACK'
};

export const MealSource = {
    MANUAL: 'MANUAL',
    GPT_IMAGE_ESTIMATE: 'GPT_IMAGE_ESTIMATE'
};

export const mealTypeOptions = [
    {label: 'Breakfast', value: MealType.BREAKFAST},
    {label: 'Lunch', value: MealType.LUNCH},
    {label: 'Dinner', value: MealType.DINNER},
    {label: 'Snack', value: MealType.SNACK}
];

export function formatMealType(mealType, mealSequence) {
    const label = mealTypeOptions.find(option => option.value === mealType).label;
    return mealType === MealType.SNACK ? `${label} ${mealSequence}` : label;
}

export default class Meal {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.mealType = source.mealType;
        this.mealSequence = source.mealSequence;
        this.mealTime = source.mealTime ? dayjs(`1970-01-01T${source.mealTime}`).toDate() : null;
        this.calories = source.calories;
        this.proteinGrams = source.proteinGrams;
        this.carbohydrateGrams = source.carbohydrateGrams;
        this.fatGrams = source.fatGrams;
        this.notes = source.notes;
        this.source = source.source;
    }

    label() {
        return formatMealType(this.mealType, this.mealSequence);
    }

    macroSummary() {
        return [
            this.proteinGrams === null ? null : `P ${this.proteinGrams} g`,
            this.carbohydrateGrams === null ? null : `C ${this.carbohydrateGrams} g`,
            this.fatGrams === null ? null : `F ${this.fatGrams} g`
        ].filter(value => value).join(' · ');
    }

    mealTimeFormat() {
        return this.mealTime ? dayjs(this.mealTime).format('HH:mm') : '—';
    }

    sourceLabel() {
        return this.source === MealSource.GPT_IMAGE_ESTIMATE ? 'GPT image estimate' : 'Manual';
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            mealType: this.mealType,
            calories: this.calories,
            proteinGrams: this.proteinGrams,
            carbohydrateGrams: this.carbohydrateGrams,
            fatGrams: this.fatGrams,
            mealTime: this.mealTime,
            notes: this.notes
        };
    }
}
