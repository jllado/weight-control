import {del, get, post, put} from './api';
import Sickness from '../model/Sickness';

function toPayload(sickness) {
    return {
        date: sickness.date.toISOString().slice(0, 10),
        type: sickness.type,
        severity: sickness.severity,
        note: sickness.note
    };
}

function toSickness(data) {
    return new Sickness({
        id: data.id,
        date: data.date,
        dateFormat: data.dateFormat,
        type: data.type,
        severity: data.severity,
        note: data.note
    });
}

export default {
    async get_all() {
        return (await get('/sicknesses')).map(toSickness);
    },
    async save(sickness) {
        const data = sickness.id
            ? await put(`/sicknesses/${sickness.id}`, toPayload(sickness))
            : await post('/sicknesses', toPayload(sickness));
        return toSickness(data);
    },
    delete(sickness) {
        return del(`/sicknesses/${sickness.id}`);
    }
}
