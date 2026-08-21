import dayjs from 'dayjs';

export default class DailyNutritionSummary {

    constructor(source) {
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.calories = source.calories;
        this.proteinGrams = source.proteinGrams;
        this.carbohydrateGrams = source.carbohydrateGrams;
        this.fatGrams = source.fatGrams;
        this.macrosComplete = source.macrosComplete;
    }
}
