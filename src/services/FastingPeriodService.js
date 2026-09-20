import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import FastingPeriod from '../model/FastingPeriod';
import {celebratePersonalRecords} from './CelebrationService';

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
        const response = period.id
            ? await put(`/fasting-periods/${period.id}`, toPayload(period))
            : await post('/fasting-periods', toPayload(period));
        celebratePersonalRecords(response.recordAchievements);
        return toFastingPeriod(response.result);
    },
    delete(period) {
        return del(`/fasting-periods/${period.id}`);
    }
};
