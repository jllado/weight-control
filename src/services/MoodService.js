import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Mood from '../model/Mood';
import {notificationsChanged} from './InAppNotificationService';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(mood) {
    return {
        date: dayjs(mood.date).format('YYYY-MM-DD'),
        period: mood.period,
        value: mood.value,
        note: mood.note
    };
}

function toMood(data) {
    return new Mood({
        id: data.id,
        date: data.date,
        period: data.period,
        value: data.value,
        note: data.note
    });
}

export default {
    async get_all_by() {
        return (await get('/moods')).map(toMood);
    },
    async save(mood) {
        const response = mood.id
            ? await put(`/moods/${mood.id}`, toPayload(mood))
            : await post('/moods', toPayload(mood));
        const data = response.result;
        celebratePersonalRecords(response.recordAchievements);
        notificationsChanged();
        return toMood(data);
    },
    async delete(mood) {
        await del(`/moods/${mood.id}`);
        notificationsChanged();
    }
}
