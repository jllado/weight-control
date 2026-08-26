import {del, get, post, put} from './api';
import Weight from '../model/Weight'
import {notificationsChanged} from './InAppNotificationService';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(weight) {
    return {
        date: weight.date.toISOString(),
        weight: weight.weight,
        fatPercentage: weight.fat_percentage,
        muscle: weight.muscle
    };
}

function toWeight(data) {
    return new Weight({
        id: data.id,
        date: data.date,
        weight: data.weight,
        lost_weight: data.lostWeight,
        fat: data.fat,
        fat_percentage: data.fatPercentage,
        lost_fat: data.lostFat,
        muscle: data.muscle,
        muscle_percentage: data.musclePercentage,
        lost_muscle: data.lostMuscle,
        performance_week: data.performanceWeek,
        photo_front: data.photoFront,
        photo_right: data.photoRight,
        photo_left: data.photoLeft
    });
}

export default {
    async get_all_by() {
        const data = await get('/weights');
        return data.map(toWeight);
    },
    async get_all_photos_by() {
        const weights = await this.get_all_by();
        return weights.map(weight => weight.photo()).filter(photo => photo.photo_front || photo.photo_left || photo.photo_right);
    },
    async get_last() {
        const weights = await this.get_all_by();
        return weights[0];
    },
    async get_previous(date) {
        const weights = await this.get_all_by();
        return weights.find(weight => weight.date < date);
    },
    async save(weight) {
        if (weight.id) {
            const response = await put(`/weights/${weight.id}`, toPayload(weight));
            notificationsChanged();
            celebratePersonalRecords(response.recordAchievements);
            return toWeight(response.result);
        }
        const response = await post('/weights', toPayload(weight));
        notificationsChanged();
        celebratePersonalRecords(response.recordAchievements);
        return toWeight(response.result);
    },
    async delete(weight) {
        await del(`/weights/${weight.id}`);
        notificationsChanged();
    },
    async upload_image(weightId, side, file) {
        const body = new FormData();
        body.append('file', file);
        const data = await post(`/weights/${weightId}/photos/${side}`, body);
        return toWeight(data);
    }
}
