import {del, get, post, put} from './api';
import Routine from '../model/Routine'
import {notificationsChanged} from './InAppNotificationService';
import {celebratePersonalRecords} from './CelebrationService';
import {normalizeDashboard} from './DashboardService';

function toRoutine(data) {
    return new Routine({
        id: data.id,
        start_date: data.startDate,
        last_time_date: data.lastTimeDate,
        name: data.name,
        reminders: data.reminders,
        times: data.times,
        current_strike: data.currentStrike,
        best_strike: data.bestStrike,
        personal_records_enabled: data.personalRecordsEnabled,
        types: data.types
    });
}

export default {
    async get_all_by() {
        return (await get('/routines')).map(toRoutine).sort((r1, r2) => r2.strike() - r1.strike());
    },
    async save(routine) {
        const payload = {name: routine.name, types: routine.typeNames(), reminderTimes: routine.reminders.map(reminder => reminder.time), personalRecordsEnabled: routine.personal_records_enabled};
        const data = routine.id
            ? await put(`/routines/${routine.id}`, payload)
            : await post('/routines', payload);
        notificationsChanged();
        return toRoutine(data);
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
        return {
            routine: toRoutine(data.routine),
            checkedAt: new Date(data.checkedAt),
            changed: data.changed,
            dashboard: normalizeDashboard(data.dashboard)
        };
    },
    async snoozeReminder(routineId, reminderId, minutes) {
        const result = await post(`/routines/${routineId}/reminders/${reminderId}/snooze`, {minutes});
        notificationsChanged();
        return result;
    },
    async updateReminderTime(routineId, reminderId, time) {
        const data = await put(`/routines/${routineId}/reminders/${reminderId}`, {time});
        notificationsChanged();
        return toRoutine(data);
    },
    async undoCheckin(routineId, date) {
        const data = await del(`/routines/${routineId}/checkins`, {date: date.toISOString()});
        notificationsChanged();
        return {
            routine: toRoutine(data.routine),
            checkedAt: new Date(data.checkedAt),
            changed: data.changed,
            dashboard: normalizeDashboard(data.dashboard)
        };
    }
}
