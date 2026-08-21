import {get} from './api';
import DailyNutritionSummary from '../model/DailyNutritionSummary';

export default {
    async get_daily_summaries() {
        return (await get('/nutrition/daily-summaries')).map(summary => new DailyNutritionSummary(summary));
    }
};
