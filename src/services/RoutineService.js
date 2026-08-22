import {del, get, post, put} from './api';
import Routine from '../model/Routine'
import {notificationsChanged} from './InAppNotificationService';
import {celebratePersonalRecords} from './CelebrationService';

export default {
    async get_all_by() {
        return (await get('/routines')).map(item => new Routine({
            id: item.id,
            start_date: item.startDate,
            last_time_date: item.lastTimeDate,
            name: item.name,
            reminders: item.reminders,
            times: item.times,
            current_strike: item.currentStrike,
            best_strike: item.bestStrike,
            types: item.types
        })).sort((r1, r2) => r2.strike() - r1.strike());
    },
    async save(routine) {
        const payload = {name: routine.name, types: routine.typeNames(), reminderTimes: routine.reminders.map(reminder => reminder.time)};
        const data = routine.id
            ? await put(`/routines/${routine.id}`, payload)
            : await post('/routines', payload);
        notificationsChanged();
        return new Routine({
            id: data.id,
            start_date: data.startDate,
            last_time_date: data.lastTimeDate,
            name: data.name,
            reminders: data.reminders,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike,
            types: data.types
        });
    },
    async delete(routine) {
        await del(`/routines/${routine.id}`);
        notificationsChanged();
    },
    async checkin(routineId, date) {
        const response = await post(`/routines/${routineId}/checkins`, {date: date.toISOString()});
        const data = response.result;
        celebratePersonalRecords(response.recordAchievements);
        notificationsChanged();
        return new Routine({
            id: data.id,
            start_date: data.startDate,
            last_time_date: data.lastTimeDate,
            name: data.name,
            reminders: data.reminders,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike,
            types: data.types
        });
    },
    async snoozeReminder(routineId, reminderId, minutes) {
        const result = await post(`/routines/${routineId}/reminders/${reminderId}/snooze`, {minutes});
        notificationsChanged();
        return result;
    },
    async undoCheckin(routineId, date) {
        const data = await del(`/routines/${routineId}/checkins`, {date: date.toISOString()});
        notificationsChanged();
        return new Routine({
            id: data.id,
            start_date: data.startDate,
            last_time_date: data.lastTimeDate,
            name: data.name,
            reminders: data.reminders,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike,
            types: data.types
        });
    }
}
