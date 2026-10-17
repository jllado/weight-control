import dayjs from 'dayjs';
import {get, put} from './api';
import {getTypicalCaloriesForDate} from '../model/WeekMetricThresholds';

export function weekStartForDate(date) {
    return dayjs(date).subtract((dayjs(date).day() + 1) % 7, 'day').format('YYYY-MM-DD');
}

export function resolveEffectiveCalorieTarget(date, profile, savedPlan) {
    if (savedPlan && savedPlan.weekStart === weekStartForDate(date)) {
        return savedPlan.days.find(day => day.date === date)?.calories ?? null;
    }
    return getTypicalCaloriesForDate(profile, date);
}

export default {
    get(weekStart) { return get(`/weekly-calorie-plans/${weekStart}`); },
    save(weekStart, revision, days) {
        return put(`/weekly-calorie-plans/${weekStart}`, {weekStart, revision, days});
    }
};
