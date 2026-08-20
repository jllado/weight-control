import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Mood from '../model/Mood';
import {notificationsChanged} from './InAppNotificationService';

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
        const data = mood.id
            ? await put(`/moods/${mood.id}`, toPayload(mood))
            : await post('/moods', toPayload(mood));
        notificationsChanged();
        return toMood(data);
    },
    async delete(mood) {
        await del(`/moods/${mood.id}`);
        notificationsChanged();
    }
}
