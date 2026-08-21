import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import FastingPeriod from '../model/FastingPeriod';

function toPayload(period) {
    return {
        startTime: dayjs(period.startTime).format('YYYY-MM-DDTHH:mm:ssZ'),
        endTime: dayjs(period.endTime).format('YYYY-MM-DDTHH:mm:ssZ'),
        notes: period.notes
    };
}

function toFastingPeriod(data) {
    return new FastingPeriod(data);
}

export default {
    async get_all() {
        return (await get('/fasting-periods')).map(toFastingPeriod);
    },
    async save(period) {
        const data = period.id
            ? await put(`/fasting-periods/${period.id}`, toPayload(period))
            : await post('/fasting-periods', toPayload(period));
        return toFastingPeriod(data);
    },
    delete(period) {
        return del(`/fasting-periods/${period.id}`);
    }
};
