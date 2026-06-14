import {del, get, post, put} from './api';
import Habit from '../model/Habit'

export default {
    async get_all_by() {
        return (await get('/habits')).map(item => new Habit({
            id: item.id,
            start_date: item.startDate,
            duration: item.duration,
            last_time_date: item.lastTimeDate,
            name: item.name,
            times: item.times,
            current_strike: item.currentStrike,
            best_strike: item.bestStrike
        }));
    },
    async save(habit) {
        const payload = {name: habit.name, duration: habit.duration};
        const data = habit.id
            ? await put(`/habits/${habit.id}`, payload)
            : await post('/habits', payload);
        return new Habit({
            id: data.id,
            start_date: data.startDate,
            duration: data.duration,
            last_time_date: data.lastTimeDate,
            name: data.name,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike
        });
    },
    delete(habit) {
        return del(`/habits/${habit.id}`);
    },
    async complete(habitId, date) {
        const data = await post(`/habits/${habitId}/complete?date=${date.toISOString().slice(0, 10)}`, {});
        return new Habit({
            id: data.id,
            start_date: data.startDate,
            duration: data.duration,
            last_time_date: data.lastTimeDate,
            name: data.name,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike
        });
    }
}
