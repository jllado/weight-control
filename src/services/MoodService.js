import {del, get, post, put} from './api';
import Mood from '../model/Mood';

function toPayload(mood) {
    return {
        date: mood.date.toISOString().slice(0, 10),
        value: mood.value,
        note: mood.note
    };
}

function toMood(data) {
    return new Mood({
        id: data.id,
        date: data.date,
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
        return toMood(data);
    },
    delete(mood) {
        return del(`/moods/${mood.id}`);
    }
}
