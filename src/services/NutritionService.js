import {get, put} from './api';
import DailyNutritionSummary from '../model/DailyNutritionSummary';

export default {
    async get_daily_summaries(from, to) {
        const query = from && to ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}` : '';
        return (await get(`/nutrition/daily-summaries${query}`)).map(summary => new DailyNutritionSummary(summary));
    },
    async get_targets(asOf) {
        return get(asOf ? `/nutrition/targets?asOf=${encodeURIComponent(asOf)}` : '/nutrition/targets');
    },
    async save_targets(targets) {
        return put('/nutrition/targets', targets);
    }
};
