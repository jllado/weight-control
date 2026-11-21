import {get, put} from './api';
import {getTypicalCaloriesForDate} from '../model/WeekMetricThresholds';

export function resolveEffectiveCalorieTarget(date, profile, savedPlan) {
    return savedPlan?.days.find(day => day.date === date)?.calories ?? getTypicalCaloriesForDate(profile, date);
}

export default {
    get(weekStart) { return get(`/weekly-calorie-plans/${weekStart}`); },
    save(weekStart, revision, days) {
        return put(`/weekly-calorie-plans/${weekStart}`, {weekStart, revision, days});
    }
};
