import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Habit from '../model/Habit'
import {celebratePersonalRecords} from './CelebrationService';

function toHabit(data) {
    return new Habit({
        id: data.id,
        start_date: data.startDate,
        duration: data.duration,
        last_time_date: data.lastTimeDate,
        name: data.name,
        times: data.times,
        current_strike: data.currentStrike,
        best_strike: data.bestStrike,
        checkins: data.checkins,
        legacy_baseline: data.legacyBaseline
    });
}

export default {
    async get_all_by() {
        return (await get('/habits')).map(toHabit);
    },
    async save(habit) {
        const payload = {name: habit.name, duration: habit.duration};
        const data = habit.id
            ? await put(`/habits/${habit.id}`, payload)
            : await post('/habits', payload);
        return toHabit(data);
    },
    delete(habit) {
        return del(`/habits/${habit.id}`);
    },
    async complete(habitId, date) {
        const response = await post(`/habits/${habitId}/complete?date=${dayjs(date).format('YYYY-MM-DD')}`, {});
        celebratePersonalRecords(response.recordAchievements);
        return toHabit(response.result);
    },
    async undo(habitId, date) {
        return toHabit(await del(`/habits/${habitId}/checkins?date=${dayjs(date).format('YYYY-MM-DD')}`));
    }
}
