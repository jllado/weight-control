import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Sleep from '../model/Sleep';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(sleep) {
    return {
        sleepDate: dayjs(sleep.date).format('YYYY-MM-DD'),
        bedtimeStart: dayjs(sleep.bedtimeStart).format('YYYY-MM-DDTHH:mm:ssZ'),
        bedtimeEnd: dayjs(sleep.bedtimeEnd).format('YYYY-MM-DDTHH:mm:ssZ'),
        totalSleepDuration: sleep.totalSleepDuration,
        deepSleepDuration: sleep.deepSleepDuration,
        remSleepDuration: sleep.remSleepDuration,
        lightSleepDuration: sleep.lightSleepDuration,
        awakeTime: sleep.awakeTime,
        averageHeartRate: sleep.averageHeartRate,
        averageHrv: sleep.averageHrv
    };
}

function toSleep(data) {
    return new Sleep(data);
}

export default {
    async get_all() {
        return (await get('/sleeps')).map(toSleep);
    },
    async save(sleep) {
        const response = sleep.id
            ? await put(`/sleeps/${sleep.id}`, toPayload(sleep))
            : await post('/sleeps', toPayload(sleep));
        celebratePersonalRecords(response.recordAchievements);
        return toSleep(response.result);
    },
    delete(sleep) {
        return del(`/sleeps/${sleep.id}`);
    }
}
