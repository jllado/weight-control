import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import BackStatus from '../model/BackStatus';

function toPayload(status) {
    return {
        date: dayjs(status.date).format('YYYY-MM-DD'),
        lower: {...status.lower},
        middle: {...status.middle},
        upper: {...status.upper},
        note: status.note
    };
}

function toBackStatus(data) {
    return new BackStatus(data);
}

export default {
    async get_all() {
        return (await get('/back-statuses')).map(toBackStatus);
    },
    async save(status) {
        const data = status.id
            ? await put(`/back-statuses/${status.id}`, toPayload(status))
            : await post('/back-statuses', toPayload(status));
        return toBackStatus(data);
    },
    delete(status) {
        return del(`/back-statuses/${status.id}`);
    }
};
