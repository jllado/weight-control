import {del, get, post, put} from './api';
import BloodPressure from '../model/BloodPressure'
import {notificationsChanged} from './InAppNotificationService';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(bloodPressure) {
    return {
        date: bloodPressure.date.toISOString(),
        upper: bloodPressure.upper,
        lower: bloodPressure.lower
    };
}

export default {
    async get_all_by() {
        return (await get('/blood-pressures')).map(item => new BloodPressure({
            id: item.id,
            date: item.date,
            upper: item.upper,
            lower: item.lower,
            lost_upper: item.lostUpper,
            lost_lower: item.lostLower
        }));
    },
    async get_last() {
        return (await this.get_all_by())[0];
    },
    async get_previous(date) {
        return (await this.get_all_by()).find(item => item.date < date);
    },
    async save(bloodPressure) {
        const response = bloodPressure.id
            ? await put(`/blood-pressures/${bloodPressure.id}`, toPayload(bloodPressure))
            : await post('/blood-pressures', toPayload(bloodPressure));
        const data = response.result;
        celebratePersonalRecords(response.recordAchievements);
        notificationsChanged();
        return new BloodPressure({
            id: data.id,
            date: data.date,
            upper: data.upper,
            lower: data.lower,
            lost_upper: data.lostUpper,
            lost_lower: data.lostLower
        });
    },
    async delete(bloodPressure) {
        await del(`/blood-pressures/${bloodPressure.id}`);
        notificationsChanged();
    }
}
